package com.supersoft.sparkpay.bulk_dispute_processor.service;

import com.supersoft.sparkpay.bulk_dispute_processor.domain.BulkDisputeJob;
import com.supersoft.sparkpay.bulk_dispute_processor.repository.BulkDisputeJobRepository;
import com.supersoft.sparkpay.bulk_dispute_processor.repository.BulkDisputeJobAuditRepository;
import com.supersoft.sparkpay.bulk_dispute_processor.domain.BulkDisputeJobAudit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
public class JobRetryService {

    @Autowired
    private BulkDisputeJobRepository jobRepository;
    
    @Autowired
    private BulkDisputeJobAuditRepository auditRepository;
    
    @Autowired
    private FailureClassifier failureClassifier;

    // Configuration properties
    @Value("${bulk.retry.max-attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${bulk.retry.initial-delay-ms:30000}")
    private long initialDelayMs;
    
    @Value("${bulk.retry.max-delay-ms:300000}")
    private long maxDelayMs;
    
    @Value("${bulk.retry.multiplier:2.0}")
    private double retryMultiplier;

    /**
     * Retry a specific failed row for transient failures
     * This is different from resume - it retries the SAME row that failed
     */
    public boolean retryFailedRow(Long jobId, int rowNumber, String rowData, int maxRetries) {
        try {
            Optional<BulkDisputeJob> jobOpt = jobRepository.findById(jobId);
            if (jobOpt.isEmpty()) {
                log.error("Job not found for retry: jobId={}", jobId);
                return false;
            }

            BulkDisputeJob job = jobOpt.get();
            
            // Check if job is in a state that allows retry
            if (job.getStatus() != BulkDisputeJob.JobStatus.COMPLETED && 
                job.getStatus() != BulkDisputeJob.JobStatus.FAILED) {
                log.warn("Job {} is not in a state that allows retry. Current status: {}", jobId, job.getStatus());
                return false;
            }

            // Add audit entry for retry attempt
            addAuditEntry(jobId, "RETRY_ATTEMPT", 
                String.format("Retrying row %d (attempt %d/%d)", rowNumber, getRetryCount(jobId) + 1, maxRetries));

            log.info("Retrying failed row {} for job {}", rowNumber, jobId);
            return true;

        } catch (Exception e) {
            log.error("Error retrying row {} for job {}: {}", rowNumber, jobId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if a failure should be retried based on classification and retry count
     */
    public boolean shouldRetryFailure(Exception exception, String errorMessage, int currentRetryCount, int maxRetries) {
        FailureClassifier.FailureType failureType = failureClassifier.classifyFailure(exception, errorMessage);
        return failureClassifier.shouldRetry(failureType, currentRetryCount, maxRetries);
    }

    /**
     * Get retry delay based on failure type and retry count
     */
    public long getRetryDelay(Exception exception, String errorMessage, int retryCount) {
        FailureClassifier.FailureType failureType = failureClassifier.classifyFailure(exception, errorMessage);
        return failureClassifier.getRetryDelay(failureType, retryCount);
    }

    /**
     * Get current retry count for a job from database
     */
    private int getRetryCount(Long jobId) {
        try {
            Optional<BulkDisputeJob> jobOpt = jobRepository.findById(jobId);
            return jobOpt.map(BulkDisputeJob::getRetryCount).orElse(0);
        } catch (Exception e) {
            log.error("Error getting retry count for job {}: {}", jobId, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Schedule a job for automatic retry with exponential backoff
     */
    public boolean scheduleJobForRetry(Long jobId, String failureReason, String failureType) {
        try {
            Optional<BulkDisputeJob> jobOpt = jobRepository.findById(jobId);
            if (jobOpt.isEmpty()) {
                log.error("Job not found for retry scheduling: jobId={}", jobId);
                return false;
            }

            BulkDisputeJob job = jobOpt.get();
            
            // Check if job can be retried
            if (job.getRetryCount() >= maxRetryAttempts) {
                log.warn("Job {} has exceeded max retry attempts ({})", jobId, maxRetryAttempts);
                return false;
            }

            // Calculate retry delay using exponential backoff
            long retryDelay = calculateRetryDelay(job.getRetryCount());
            LocalDateTime nextRetryAt = LocalDateTime.now().plusNanos(retryDelay * 1_000_000);

            // Update job with retry information
            job.setRetryCount(job.getRetryCount() + 1);
            job.setFailureReason(failureReason);
            job.setFailureType(failureType);
            job.setLastRetryAt(LocalDateTime.now());
            job.setNextRetryAt(nextRetryAt);
            job.setStatus(BulkDisputeJob.JobStatus.PENDING);
            jobRepository.save(job);

            // Add audit entry
            addAuditEntry(jobId, "AUTO_RETRY_SCHEDULED", 
                String.format("Automatic retry scheduled for attempt %d/%d (delay: %dms, reason: %s)", 
                    job.getRetryCount(), maxRetryAttempts, retryDelay, failureReason));

            log.info("Job {} scheduled for retry in {}ms (attempt {}/{})", 
                jobId, retryDelay, job.getRetryCount(), maxRetryAttempts);
            return true;

        } catch (Exception e) {
            log.error("Error scheduling job {} for retry", jobId, e);
            return false;
        }
    }

    /**
     * Calculate retry delay using exponential backoff
     */
    private long calculateRetryDelay(int retryCount) {
        if (retryCount <= 0) {
            return initialDelayMs;
        }
        
        // Exponential backoff: delay = initial * (multiplier ^ retryCount)
        long delay = (long) (initialDelayMs * Math.pow(retryMultiplier, retryCount - 1));
        
        // Cap at maximum delay
        return Math.min(delay, maxDelayMs);
    }

    /**
     * Check if a job is ready for retry based on next retry time
     */
    public boolean isJobReadyForRetry(Long jobId) {
        try {
            Optional<BulkDisputeJob> jobOpt = jobRepository.findById(jobId);
            if (jobOpt.isEmpty()) {
                return false;
            }

            BulkDisputeJob job = jobOpt.get();
            return job.getNextRetryAt() != null && 
                   job.getNextRetryAt().isBefore(LocalDateTime.now()) &&
                   job.getRetryCount() < maxRetryAttempts;
        } catch (Exception e) {
            log.error("Error checking if job {} is ready for retry", jobId, e);
            return false;
        }
    }

    /**
     * Add audit entry for retry operations
     */
    private void addAuditEntry(Long jobId, String action, String message) {
        try {
            BulkDisputeJobAudit audit = BulkDisputeJobAudit.builder()
                    .jobId(jobId)
                    .action(action)
                    .message(message)
                    .createdAt(LocalDateTime.now())
                    .build();
            auditRepository.save(audit);
        } catch (Exception e) {
            log.error("Error adding audit entry for job {}: {}", jobId, e.getMessage(), e);
        }
    }
}
