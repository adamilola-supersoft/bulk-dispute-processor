package com.supersoft.sparkpay.bulk_dispute_processor.worker;

import com.supersoft.sparkpay.bulk_dispute_processor.domain.BulkDisputeJob;
import com.supersoft.sparkpay.bulk_dispute_processor.domain.BulkDisputeJobAudit;
import com.supersoft.sparkpay.bulk_dispute_processor.repository.BulkDisputeJobAuditRepository;
import com.supersoft.sparkpay.bulk_dispute_processor.repository.BulkDisputeJobRepository;
import com.supersoft.sparkpay.bulk_dispute_processor.service.DisputeUpdater;
import com.supersoft.sparkpay.bulk_dispute_processor.service.JobMessagePublisher;
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
        
        try {
            job.setStatus(BulkDisputeJob.JobStatus.RUNNING);
            job.setStartedAt(LocalDateTime.now());
            jobRepository.save(job);
            
            addAuditEntry(job.getId(), "JOB_STARTED", "Job processing started");
            processCsvFile(job, jobMessage.getFilePath(), jobMessage);

            job.setStatus(BulkDisputeJob.JobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
            
            addAuditEntry(job.getId(), "JOB_COMPLETED", 
                    String.format("Job completed successfully. Processed: %d, Success: %d, Failed: %d", 
                            job.getProcessedRows(), job.getSuccessCount(), job.getFailureCount()));

            log.info("Job completed successfully: jobId={}", job.getId());

        } catch (Exception e) {
            log.error("Job processing failed: jobId={}", job.getId(), e);
            
            job.setStatus(BulkDisputeJob.JobStatus.FAILED);
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
            
            addAuditEntry(job.getId(), "JOB_FAILED", "Job processing failed: " + e.getMessage());
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

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("File is empty");
            }

            List<String> headers = CsvParser.parseCsvLine(headerLine);
            String line;
            
            while ((line = reader.readLine()) != null) {
                processedRows++;
                List<String> row = CsvParser.parseCsvLine(line);
                
                if (row.size() != headers.size()) {
                    log.warn("Row {} has incorrect column count. Expected: {}, Got: {}", 
                            processedRows, headers.size(), row.size());
                    failedRows.add(line);
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
                        log.warn("Row {} processing failed: {}", processedRows, result.getErrorMessage());
                        failedRows.add(line + " // Error: " + result.getErrorMessage());
                        failureCount++;
                    }
                } catch (Exception e) {
                    log.error("Error processing row {}: {}", processedRows, e.getMessage(), e);
                    failedRows.add(line + " // Error: " + e.getMessage());
                    failureCount++;
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


    private void addAuditEntry(Long jobId, String action, String message) {
        BulkDisputeJobAudit audit = BulkDisputeJobAudit.builder()
                .jobId(jobId)
                .action(action)
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();
        auditRepository.save(audit);
    }
}
