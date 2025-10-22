package com.supersoft.sparkpay.bulk_dispute_processor.worker;

import com.supersoft.sparkpay.bulk_dispute_processor.domain.BulkDisputeJob;
import com.supersoft.sparkpay.bulk_dispute_processor.domain.BulkDisputeJobAudit;
import com.supersoft.sparkpay.bulk_dispute_processor.repository.BulkDisputeJobAuditRepository;
import com.supersoft.sparkpay.bulk_dispute_processor.repository.BulkDisputeJobRepository;
import com.supersoft.sparkpay.bulk_dispute_processor.repository.BulkDisputeSessionErrorRepository;
import com.supersoft.sparkpay.bulk_dispute_processor.service.CsvValidationService;
import com.supersoft.sparkpay.bulk_dispute_processor.service.DisputeUpdater;
import com.supersoft.sparkpay.bulk_dispute_processor.service.JobMessagePublisher;
import com.supersoft.sparkpay.bulk_dispute_processor.service.EnhancedJobProcessor;
import com.supersoft.sparkpay.bulk_dispute_processor.service.FailureClassifier;
import com.supersoft.sparkpay.bulk_dispute_processor.service.AtomicJobUpdater;
import com.supersoft.sparkpay.bulk_dispute_processor.service.JobRetryService;
import com.supersoft.sparkpay.bulk_dispute_processor.service.JobResumeService;
import com.supersoft.sparkpay.bulk_dispute_processor.util.CsvParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
public class BulkJobWorker {

    @Autowired
    private BulkDisputeJobRepository jobRepository;
    
    @Autowired
    private BulkDisputeJobAuditRepository auditRepository;
    
    @Autowired
    private DisputeUpdater disputeUpdater;
    
    @Autowired
    private EnhancedJobProcessor enhancedJobProcessor;
    
    @Autowired
    private FailureClassifier failureClassifier;
    
    @Autowired
    private BulkDisputeSessionErrorRepository errorRepository;
    
    @Autowired
    private CsvValidationService csvValidationService;
    
    @Autowired
    private AtomicJobUpdater atomicJobUpdater;
    
    @Autowired
    private JobRetryService jobRetryService;
    
    @Autowired
    private JobResumeService jobResumeService;

    @RabbitListener(queues = "bulk.jobs")
    public void processJob(JobMessagePublisher.JobMessage jobMessage) {
        log.info("Processing job: jobId={}, sessionId={}, filePath={}", 
                jobMessage.getJobId(), jobMessage.getSessionId(), jobMessage.getFilePath());

        Optional<BulkDisputeJob> jobOpt = jobRepository.findById(jobMessage.getJobId());
        if (jobOpt.isEmpty()) {
            log.error("Job not found: {}", jobMessage.getJobId());
            return;
        }

        BulkDisputeJob job = jobOpt.get();
        
        // Atomically claim the job for processing to prevent race conditions
        if (!atomicJobUpdater.claimJobForProcessing(job.getId())) {
            log.warn("Job {} is already being processed by another worker", job.getId());
            return;
        }
        
        try {
            job.setStartedAt(LocalDateTime.now());
            jobRepository.save(job);
            
            addAuditEntry(job.getId(), "JOB_STARTED", "Job processing started");
            processCsvFile(job, jobMessage.getFilePath(), jobMessage);

            job.setStatus(BulkDisputeJob.JobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
            
            // Update session status based on job completion
            enhancedJobProcessor.updateSessionStatus(jobMessage.getSessionId(), job);
            
            addAuditEntry(job.getId(), "JOB_COMPLETED", 
                    String.format("Job completed successfully. Processed: %d, Success: %d, Failed: %d", 
                            job.getProcessedRows(), job.getSuccessCount(), job.getFailureCount()));

            log.info("Job completed successfully: jobId={}", job.getId());

        } catch (Exception e) {
            log.error("Job processing failed: jobId={}", job.getId(), e);
            
            // Classify the failure
            FailureClassifier.FailureType failureType = failureClassifier.classifyFailure(e, e.getMessage());
            String failureReason = e.getMessage();
            String failureTypeStr = failureType.name();
            
            if (failureType == FailureClassifier.FailureType.INFRASTRUCTURE) {
                // Pause job for infrastructure issues using the resume service
                boolean paused = jobResumeService.pauseJob(job.getId(), "Infrastructure failure: " + failureReason);
                if (paused) {
                    log.warn("Job paused due to infrastructure issue: jobId={}", job.getId());
                    // Store failure information for potential retry
                    job.setFailureReason(failureReason);
                    job.setFailureType(failureTypeStr);
                    jobRepository.save(job);
                } else {
                    log.error("Failed to pause job {} for infrastructure issue", job.getId());
                    // Fallback to failed status with automatic retry
                    handleJobFailureWithRetry(job, failureReason, failureTypeStr);
                }
            } else if (failureType == FailureClassifier.FailureType.TRANSIENT) {
                // Handle transient failures with automatic retry
                handleJobFailureWithRetry(job, failureReason, failureTypeStr);
            } else {
                // Mark as permanently failed for business logic errors
                job.setStatus(BulkDisputeJob.JobStatus.FAILED);
                job.setCompletedAt(LocalDateTime.now());
                job.setFailureReason(failureReason);
                job.setFailureType(failureTypeStr);
                jobRepository.save(job);
                addAuditEntry(job.getId(), "JOB_FAILED_PERMANENT", "Job processing failed permanently: " + failureReason);
            }
            
            // Update session status
            enhancedJobProcessor.updateSessionStatus(jobMessage.getSessionId(), job);
        }
    }

    private void processCsvFile(BulkDisputeJob job, String filePath, JobMessagePublisher.JobMessage jobMessage) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IOException("File not found: " + filePath);
        }

        List<String> failedRows = new ArrayList<>();
        int processedRows = 0;
        int successCount = 0;
        int failureCount = 0;
        
        // Resume from last processed row if job was paused
        int startRow = job.getLastProcessedRow();
        if (startRow > 0) {
            log.info("Resuming job from row: {}", startRow + 1);
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("File is empty");
            }

            List<String> headers = CsvParser.parseCsvLine(headerLine);
            String line;
            int currentRow = 0;
            
            while ((line = reader.readLine()) != null) {
                currentRow++;
                
                // Skip rows that were already processed
                if (currentRow <= startRow) {
                    continue;
                }
                
                processedRows++;
                
                // Check for stored validation errors from upload process FIRST
                // Convert 0-based currentRow to 1-based row number for validation errors
                int validationRowNumber = currentRow + 1;
                boolean hasStoredValidationErrors = errorRepository.hasRowErrors(jobMessage.getSessionId(), validationRowNumber);
                log.debug("Row {} (validation row {}) stored validation check: hasErrors={}", currentRow, validationRowNumber, hasStoredValidationErrors);
                
                if (hasStoredValidationErrors) {
                    log.info("Skipping row {} due to stored validation errors", currentRow);
                    addAuditEntry(job.getId(), "ROW_SKIPPED", 
                            String.format("Row %d skipped due to stored validation errors", currentRow));
                    
                    // Get stored validation errors for this row
                    List<BulkDisputeSessionErrorRepository.SessionError> storedErrors = 
                            errorRepository.getErrorsForRows(jobMessage.getSessionId(), List.of(validationRowNumber));
                    
                    // Format validation errors in a structured way
                    StringBuilder errorBuilder = new StringBuilder();
                    errorBuilder.append("VALIDATION_ERRORS: ");
                    
                    for (int i = 0; i < storedErrors.size(); i++) {
                        BulkDisputeSessionErrorRepository.SessionError error = storedErrors.get(i);
                        if (i > 0) {
                            errorBuilder.append("; ");
                        }
                        errorBuilder.append("[").append(error.getColumnName()).append("] ").append(error.getErrorMessage());
                    }
                    
                    failedRows.add(line + " // " + errorBuilder.toString());
                    failureCount++;
                    continue;
                }
                
                List<String> row = CsvParser.parseCsvLine(line);
                
                if (row.size() != headers.size()) {
                    log.warn("Row {} has incorrect column count. Expected: {}, Got: {}", 
                            processedRows, headers.size(), row.size());
                    failedRows.add(line);
                    failureCount++;
                    continue;
                }
                
                // Re-validate basic fields in real-time
                List<String> realTimeValidationErrors = validateRow(row, headers, currentRow);
                log.debug("Row {} real-time validation errors: {}", currentRow, realTimeValidationErrors);
                
                if (!realTimeValidationErrors.isEmpty()) {
                    log.info("Skipping row {} due to real-time validation errors", currentRow);
                    addAuditEntry(job.getId(), "ROW_SKIPPED", 
                            String.format("Row %d skipped due to real-time validation errors", currentRow));
                    
                    String errorMessage = "VALIDATION_ERRORS: " + String.join("; ", realTimeValidationErrors);
                    failedRows.add(line + " // " + errorMessage);
                    failureCount++;
                    continue;
                }

                Map<String, String> rowMap = new HashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    rowMap.put(headers.get(i), row.get(i));
                }
                
                // Pass session context to the dispute processor
                rowMap.put("uploadedBy", jobMessage.getUploadedBy());
                rowMap.put("sessionId", jobMessage.getSessionId().toString());
                try {
                    DisputeUpdater.ProcessingResult result = disputeUpdater.processRow(rowMap);
                    if (result.isSuccess()) {
                        successCount++;
                    } else {
                        // Classify the failure
                        FailureClassifier.FailureType failureType = failureClassifier.classifyFailure(
                            new RuntimeException(result.getErrorMessage()), result.getErrorMessage());
                        
                        log.warn("Row {} processing failed: {} (Type: {})", currentRow, result.getErrorMessage(), failureType);
                        failedRows.add(line + " // PROCESSING_ERROR: " + result.getErrorMessage() + " // Type: " + failureType);
                        failureCount++;
                    }
                } catch (Exception e) {
                    // Classify the failure
                    FailureClassifier.FailureType failureType = failureClassifier.classifyFailure(e, e.getMessage());
                    
                    log.error("Error processing row {}: {} (Type: {})", currentRow, e.getMessage(), failureType, e);
                    failedRows.add(line + " // PROCESSING_ERROR: " + e.getMessage() + " // Type: " + failureType);
                    failureCount++;
                }
                
                // Atomically update last processed row to prevent race conditions
                if (!atomicJobUpdater.updateLastProcessedRow(job.getId(), currentRow)) {
                    log.warn("Failed to update lastProcessedRow for job {} at row {} - another worker may have processed this row", 
                            job.getId(), currentRow);
                    // Continue processing but be aware of potential race condition
                }
            }
        }

        job.setProcessedRows(processedRows);
        job.setSuccessCount(successCount);
        job.setFailureCount(failureCount);

        // Generate error report for failed rows
        if (!failedRows.isEmpty()) {
            String errorReportPath = createErrorReport(job.getSessionId(), failedRows);
            job.setErrorReportPath(errorReportPath);
        }

        jobRepository.save(job);
    }

    private String createErrorReport(Long sessionId, List<String> failedRows) throws IOException {
        String errorReportPath = "uploads/" + sessionId + "_errors_" + 
                LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";
        
        Path path = Paths.get(errorReportPath);
        Files.createDirectories(path.getParent());
        
        try (var writer = Files.newBufferedWriter(path)) {
            writer.write("dispute_id,action,reason,notes,error_message");
            writer.newLine();
            for (String row : failedRows) {
                writer.write(row);
                writer.newLine();
            }
        }
        
        return path.toString();
    }


    /**
     * Handle job failure with automatic retry logic
     */
    private void handleJobFailureWithRetry(BulkDisputeJob job, String failureReason, String failureType) {
        try {
            // Check if job can be retried
            if (job.getRetryCount() < 3) { // Max 3 retries by default
                // Schedule job for automatic retry
                boolean scheduled = jobRetryService.scheduleJobForRetry(job.getId(), failureReason, failureType);
                if (scheduled) {
                    log.info("Job {} scheduled for automatic retry (attempt {})", job.getId(), job.getRetryCount() + 1);
                    addAuditEntry(job.getId(), "AUTO_RETRY_SCHEDULED", 
                        String.format("Job scheduled for automatic retry: %s", failureReason));
                } else {
                    log.warn("Failed to schedule job {} for automatic retry", job.getId());
                    // Mark as failed if retry scheduling fails
                    job.setStatus(BulkDisputeJob.JobStatus.FAILED);
                    job.setCompletedAt(LocalDateTime.now());
                    job.setFailureReason(failureReason);
                    job.setFailureType(failureType);
                    jobRepository.save(job);
                    addAuditEntry(job.getId(), "JOB_FAILED_NO_RETRY", "Job failed and retry scheduling failed: " + failureReason);
                }
            } else {
                // Max retries exceeded, mark as permanently failed
                job.setStatus(BulkDisputeJob.JobStatus.FAILED);
                job.setCompletedAt(LocalDateTime.now());
                job.setFailureReason(failureReason);
                job.setFailureType(failureType);
                jobRepository.save(job);
                addAuditEntry(job.getId(), "JOB_FAILED_MAX_RETRIES", 
                    String.format("Job failed after %d retry attempts: %s", job.getRetryCount(), failureReason));
                log.warn("Job {} failed after maximum retry attempts", job.getId());
            }
        } catch (Exception e) {
            log.error("Error handling job failure with retry for job {}", job.getId(), e);
            // Fallback to failed status
            job.setStatus(BulkDisputeJob.JobStatus.FAILED);
            job.setCompletedAt(LocalDateTime.now());
            job.setFailureReason(failureReason);
            job.setFailureType(failureType);
            jobRepository.save(job);
        }
    }

    private void addAuditEntry(Long jobId, String action, String message) {
        BulkDisputeJobAudit audit = BulkDisputeJobAudit.builder()
                .jobId(jobId)
                .action(action)
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();
        auditRepository.save(audit);
    }
    
    /**
     * Validate a single row using the same validation logic as the upload process
     */
    private List<String> validateRow(List<String> row, List<String> headers, int rowNumber) {
        List<String> errors = new ArrayList<>();
        
        try {
            // Create a map of the row data
            Map<String, String> rowMap = new HashMap<>();
            for (int i = 0; i < headers.size() && i < row.size(); i++) {
                rowMap.put(headers.get(i), row.get(i));
            }
            
            // Get required fields
            String action = rowMap.get("Action");
            String uniqueKey = rowMap.get("Unique Key");
            
            // Validate required fields
            if (action == null || action.trim().isEmpty()) {
                errors.add("Action is required");
            } else if (!action.equalsIgnoreCase("Accept") && !action.equalsIgnoreCase("Reject")) {
                errors.add("Action must be 'Accept' or 'Reject'");
            }
            
            if (uniqueKey == null || uniqueKey.trim().isEmpty()) {
                errors.add("Unique Key is required");
            }
            
            // For REJECT actions, validate proof requirement (business rule)
            if ("REJECT".equalsIgnoreCase(action) && uniqueKey != null && !uniqueKey.trim().isEmpty()) {
                // Note: In job processing, we don't have access to proof files
                // This validation should have been done during upload
                // We'll rely on the stored validation errors for this
            }
            
        } catch (Exception e) {
            log.error("Error validating row {}: {}", rowNumber, e.getMessage());
            errors.add("Validation error: " + e.getMessage());
        }
        
        return errors;
    }
}
