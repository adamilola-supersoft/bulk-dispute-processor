package com.supersoft.sparkpay.bulk_dispute_processor.service;

import com.supersoft.sparkpay.bulk_dispute_processor.domain.BulkDisputeJob;
import com.supersoft.sparkpay.bulk_dispute_processor.domain.BulkDisputeSession;
import com.supersoft.sparkpay.bulk_dispute_processor.repository.BulkDisputeJobRepository;
import com.supersoft.sparkpay.bulk_dispute_processor.repository.BulkDisputeSessionRepository;
import com.supersoft.sparkpay.bulk_dispute_processor.repository.BulkDisputeJobAuditRepository;
import com.supersoft.sparkpay.bulk_dispute_processor.domain.BulkDisputeJobAudit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
public class JobResumeService {

    @Autowired
    private BulkDisputeJobRepository jobRepository;
    
    @Autowired
    private BulkDisputeSessionRepository sessionRepository;
    
    @Autowired
    private BulkDisputeJobAuditRepository auditRepository;
    
    @Autowired
    private AtomicJobUpdater atomicJobUpdater;

    /**
     * Resume a paused job from where it left off
     * This is different from retry - it continues from the last processed row
     */
    public boolean resumeJob(Long jobId) {
        try {
            Optional<BulkDisputeJob> jobOpt = jobRepository.findById(jobId);
            if (jobOpt.isEmpty()) {
                log.error("Job not found for resume: jobId={}", jobId);
                return false;
            }

            BulkDisputeJob job = jobOpt.get();
            
            // Check if job is in a state that allows resume
            if (job.getStatus() != BulkDisputeJob.JobStatus.PAUSED) {
                log.warn("Job {} is not paused, cannot resume. Current status: {}", jobId, job.getStatus());
                return false;
            }

            // Atomically update job status to RUNNING
            boolean statusUpdated = atomicJobUpdater.updateJobStatus(jobId, 
                BulkDisputeJob.JobStatus.RUNNING, BulkDisputeJob.JobStatus.PAUSED);
            
            if (!statusUpdated) {
                log.warn("Failed to update job status for resume: jobId={}", jobId);
                return false;
            }

            // Update session status to PROCESSING
            updateSessionStatus(job.getSessionId(), BulkDisputeSession.SessionStatus.PROCESSING);

            // Add audit entry
            addAuditEntry(jobId, "JOB_RESUMED", 
                String.format("Job resumed from row %d", job.getLastProcessedRow() + 1));

            log.info("Job {} resumed successfully from row {}", jobId, job.getLastProcessedRow() + 1);
            return true;

        } catch (Exception e) {
            log.error("Error resuming job {}: {}", jobId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Pause a running job due to infrastructure issues
     */
    public boolean pauseJob(Long jobId, String reason) {
        try {
            Optional<BulkDisputeJob> jobOpt = jobRepository.findById(jobId);
            if (jobOpt.isEmpty()) {
                log.error("Job not found for pause: jobId={}", jobId);
                return false;
            }

            BulkDisputeJob job = jobOpt.get();
            
            // Check if job is in a state that allows pause
            if (job.getStatus() != BulkDisputeJob.JobStatus.RUNNING) {
                log.warn("Job {} is not running, cannot pause. Current status: {}", jobId, job.getStatus());
                return false;
            }

            // Atomically update job status to PAUSED
            boolean statusUpdated = atomicJobUpdater.updateJobStatus(jobId, 
                BulkDisputeJob.JobStatus.PAUSED, BulkDisputeJob.JobStatus.RUNNING);
            
            if (!statusUpdated) {
                log.warn("Failed to update job status for pause: jobId={}", jobId);
                return false;
            }

            // Update session status to PROCESSING (still processing, just paused)
            updateSessionStatus(job.getSessionId(), BulkDisputeSession.SessionStatus.PROCESSING);

            // Add audit entry
            addAuditEntry(jobId, "JOB_PAUSED", String.format("Job paused: %s", reason));

            log.info("Job {} paused successfully. Reason: {}", jobId, reason);
            return true;

        } catch (Exception e) {
            log.error("Error pausing job {}: {}", jobId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if a job can be resumed
     */
    public boolean canResumeJob(Long jobId) {
        try {
            Optional<BulkDisputeJob> jobOpt = jobRepository.findById(jobId);
            if (jobOpt.isEmpty()) {
                return false;
            }

            BulkDisputeJob job = jobOpt.get();
            return job.getStatus() == BulkDisputeJob.JobStatus.PAUSED;
        } catch (Exception e) {
            log.error("Error checking if job can be resumed: {}", jobId, e);
            return false;
        }
    }

    /**
     * Get the resume point for a job
     */
    public int getResumePoint(Long jobId) {
        try {
            Optional<BulkDisputeJob> jobOpt = jobRepository.findById(jobId);
            if (jobOpt.isEmpty()) {
                return 0;
            }

            BulkDisputeJob job = jobOpt.get();
            return job.getLastProcessedRow();
        } catch (Exception e) {
            log.error("Error getting resume point for job: {}", jobId, e);
            return 0;
        }
    }

    /**
     * Update session status
     */
    private void updateSessionStatus(Long sessionId, BulkDisputeSession.SessionStatus newStatus) {
        try {
            Optional<BulkDisputeSession> sessionOpt = sessionRepository.findById(sessionId);
            if (sessionOpt.isPresent()) {
                BulkDisputeSession session = sessionOpt.get();
                session.setStatus(newStatus);
                sessionRepository.save(session);
                log.debug("Updated session {} status to {}", sessionId, newStatus);
            }
        } catch (Exception e) {
            log.error("Error updating session status for session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * Add audit entry for resume operations
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
