package com.supersoft.sparkpay.bulk_dispute_processor.service;

import com.supersoft.sparkpay.bulk_dispute_processor.domain.BulkDisputeJob;
import com.supersoft.sparkpay.bulk_dispute_processor.domain.BulkDisputeSession;
import com.supersoft.sparkpay.bulk_dispute_processor.repository.BulkDisputeJobRepository;
import com.supersoft.sparkpay.bulk_dispute_processor.repository.BulkDisputeSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class EnhancedJobProcessor {

    @Autowired
    private BulkDisputeJobRepository jobRepository;
    
    @Autowired
    private BulkDisputeSessionRepository sessionRepository;

    /**
     * Update session status based on job completion
     */
    public void updateSessionStatus(Long sessionId, BulkDisputeJob job) {
        try {
            Optional<BulkDisputeSession> sessionOpt = sessionRepository.findById(sessionId);
            if (sessionOpt.isEmpty()) {
                log.warn("Session not found for job completion: sessionId={}", sessionId);
                return;
            }

            BulkDisputeSession session = sessionOpt.get();
            BulkDisputeSession.SessionStatus newStatus = determineSessionStatus(job);
            
            if (session.getStatus() != newStatus) {
                session.setStatus(newStatus);
                sessionRepository.save(session);
                log.info("Updated session status: sessionId={}, oldStatus={}, newStatus={}", 
                        sessionId, session.getStatus(), newStatus);
            }

        } catch (Exception e) {
            log.error("Error updating session status for sessionId={}", sessionId, e);
        }
    }

    /**
     * Determine session status based on job result
     */
    private BulkDisputeSession.SessionStatus determineSessionStatus(BulkDisputeJob job) {
        switch (job.getStatus()) {
            case RUNNING:
                return BulkDisputeSession.SessionStatus.PROCESSING;
                
            case COMPLETED:
                if (job.getFailureCount() == 0) {
                    return BulkDisputeSession.SessionStatus.PROCESSED;
                } else {
                    return BulkDisputeSession.SessionStatus.PARTIALLY_PROCESSED;
                }
                
            case FAILED:
                return BulkDisputeSession.SessionStatus.FAILED;
                
            case PAUSED:
                return BulkDisputeSession.SessionStatus.PROCESSING; // Still processing, just paused
                
            default:
                return BulkDisputeSession.SessionStatus.CONFIRMED;
        }
    }

    /**
     * Resume a paused job from the last processed row
     */
    public boolean resumeJob(Long jobId) {
        try {
            Optional<BulkDisputeJob> jobOpt = jobRepository.findById(jobId);
            if (jobOpt.isEmpty()) {
                log.error("Job not found for resume: jobId={}", jobId);
                return false;
            }

            BulkDisputeJob job = jobOpt.get();
            if (job.getStatus() != BulkDisputeJob.JobStatus.PAUSED) {
                log.warn("Job is not paused, cannot resume: jobId={}, status={}", jobId, job.getStatus());
                return false;
            }

            // Reset job status to RUNNING for resume
            job.setStatus(BulkDisputeJob.JobStatus.RUNNING);
            jobRepository.save(job);
            
            log.info("Job resumed: jobId={}, resuming from row={}", jobId, job.getLastProcessedRow() + 1);
            return true;

        } catch (Exception e) {
            log.error("Error resuming job: jobId={}", jobId, e);
            return false;
        }
    }

    /**
     * Pause a running job (for infrastructure issues)
     */
    public boolean pauseJob(Long jobId, String reason) {
        try {
            Optional<BulkDisputeJob> jobOpt = jobRepository.findById(jobId);
            if (jobOpt.isEmpty()) {
                log.error("Job not found for pause: jobId={}", jobId);
                return false;
            }

            BulkDisputeJob job = jobOpt.get();
            if (job.getStatus() != BulkDisputeJob.JobStatus.RUNNING) {
                log.warn("Job is not running, cannot pause: jobId={}, status={}", jobId, job.getStatus());
                return false;
            }

            job.setStatus(BulkDisputeJob.JobStatus.PAUSED);
            jobRepository.save(job);
            
            log.info("Job paused: jobId={}, reason={}", jobId, reason);
            return true;

        } catch (Exception e) {
            log.error("Error pausing job: jobId={}", jobId, e);
            return false;
        }
    }
}
