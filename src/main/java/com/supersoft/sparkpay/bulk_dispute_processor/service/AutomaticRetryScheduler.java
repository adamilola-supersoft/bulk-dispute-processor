package com.supersoft.sparkpay.bulk_dispute_processor.service;

import com.supersoft.sparkpay.bulk_dispute_processor.domain.BulkDisputeJob;
import com.supersoft.sparkpay.bulk_dispute_processor.repository.BulkDisputeJobRepository;
import com.supersoft.sparkpay.bulk_dispute_processor.repository.BulkDisputeJobAuditRepository;
import com.supersoft.sparkpay.bulk_dispute_processor.domain.BulkDisputeJobAudit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Automatic retry scheduler that handles failed jobs and paused jobs
 * Runs on a configurable schedule to retry failed jobs and resume paused jobs
 */
@Slf4j
@Service
public class AutomaticRetryScheduler {

    @Autowired
    private BulkDisputeJobRepository jobRepository;
    
    @Autowired
    private BulkDisputeJobAuditRepository auditRepository;
    
    @Autowired
    private JobRetryService jobRetryService;
    
    @Autowired
    private JobResumeService jobResumeService;
    
    @Autowired
    private FailureClassifier failureClassifier;

    // Retry configuration
    @Value("${bulk.retry.enabled:true}")
    private boolean retryEnabled;
    
    @Value("${bulk.retry.max-attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${bulk.retry.initial-delay-ms:30000}")
    private long initialDelayMs;
    
    @Value("${bulk.retry.max-delay-ms:300000}")
    private long maxDelayMs;
    
    @Value("${bulk.retry.multiplier:2.0}")
    private double retryMultiplier;

    // Resume configuration
    @Value("${bulk.resume.enabled:true}")
    private boolean resumeEnabled;

    /**
     * Scheduled method to process failed jobs for retry
     * Runs every minute by default (configurable via properties)
     */
    @Scheduled(fixedDelayString = "${bulk.retry.schedule-interval-ms:60000}")
    public void processFailedJobsForRetry() {
        if (!retryEnabled) {
            log.debug("Automatic retry is disabled");
            return;
        }

        try {
            log.debug("Starting automatic retry processing");
            
            // Find jobs that are ready for retry
            List<BulkDisputeJob> jobsToRetry = jobRepository.findJobsReadyForRetry(
                LocalDateTime.now(), maxRetryAttempts);
            
            log.info("Found {} jobs ready for retry", jobsToRetry.size());
            
            for (BulkDisputeJob job : jobsToRetry) {
                processJobForRetry(job);
            }
            
        } catch (Exception e) {
            log.error("Error in automatic retry processing", e);
        }
    }

    /**
     * Scheduled method to process paused jobs for resume
     * Runs every 30 seconds by default (configurable via properties)
     */
    @Scheduled(fixedDelayString = "${bulk.resume.schedule-interval-ms:30000}")
    public void processPausedJobsForResume() {
        if (!resumeEnabled) {
            log.debug("Automatic resume is disabled");
            return;
        }

        try {
            log.debug("Starting automatic resume processing");
            
            // Find paused jobs that can be resumed
            List<BulkDisputeJob> pausedJobs = jobRepository.findByStatus(BulkDisputeJob.JobStatus.PAUSED);
            
            log.info("Found {} paused jobs for resume", pausedJobs.size());
            
            for (BulkDisputeJob job : pausedJobs) {
                processJobForResume(job);
            }
            
        } catch (Exception e) {
            log.error("Error in automatic resume processing", e);
        }
    }

    /**
     * Process a specific job for retry
     */
    private void processJobForRetry(BulkDisputeJob job) {
        try {
            log.info("Processing job {} for retry (attempt {}/{})", 
                job.getId(), job.getRetryCount() + 1, maxRetryAttempts);
            
            // Check if job can be retried based on failure type
            if (!canRetryJob(job)) {
                log.warn("Job {} cannot be retried - failure type: {}", 
                    job.getId(), job.getFailureType());
                return;
            }
            
            // Calculate next retry delay
            long retryDelay = calculateRetryDelay(job.getRetryCount());
            LocalDateTime nextRetryAt = LocalDateTime.now().plusNanos(retryDelay * 1_000_000);
            
            // Update job with retry information
            job.setRetryCount(job.getRetryCount() + 1);
            job.setLastRetryAt(LocalDateTime.now());
            job.setNextRetryAt(nextRetryAt);
            job.setStatus(BulkDisputeJob.JobStatus.PENDING);
            jobRepository.save(job);
            
            // Add audit entry
            addAuditEntry(job.getId(), "AUTO_RETRY_SCHEDULED", 
                String.format("Automatic retry scheduled for attempt %d/%d (delay: %dms)", 
                    job.getRetryCount(), maxRetryAttempts, retryDelay));
            
            log.info("Job {} scheduled for retry in {}ms", job.getId(), retryDelay);
            
        } catch (Exception e) {
            log.error("Error processing job {} for retry", job.getId(), e);
        }
    }

    /**
     * Process a specific job for resume
     */
    private void processJobForResume(BulkDisputeJob job) {
        try {
            log.info("Processing job {} for resume", job.getId());
            
            // Attempt to resume the job
            boolean resumed = jobResumeService.resumeJob(job.getId());
            
            if (resumed) {
                log.info("Job {} automatically resumed", job.getId());
            } else {
                log.warn("Failed to automatically resume job {}", job.getId());
            }
            
        } catch (Exception e) {
            log.error("Error processing job {} for resume", job.getId(), e);
        }
    }

    /**
     * Check if a job can be retried based on failure type and retry count
     */
    private boolean canRetryJob(BulkDisputeJob job) {
        // Check retry count limit
        if (job.getRetryCount() >= maxRetryAttempts) {
            log.debug("Job {} has exceeded max retry attempts ({})", job.getId(), maxRetryAttempts);
            return false;
        }
        
        // Check failure type
        if (job.getFailureType() != null) {
            FailureClassifier.FailureType failureType = 
                FailureClassifier.FailureType.valueOf(job.getFailureType());
            
            return failureClassifier.shouldRetry(failureType, job.getRetryCount(), maxRetryAttempts);
        }
        
        // Default to true if no failure type is set
        return true;
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
