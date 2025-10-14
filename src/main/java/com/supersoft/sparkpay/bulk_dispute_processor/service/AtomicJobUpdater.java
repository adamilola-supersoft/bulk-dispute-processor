package com.supersoft.sparkpay.bulk_dispute_processor.service;

import com.supersoft.sparkpay.bulk_dispute_processor.domain.BulkDisputeJob;
import com.supersoft.sparkpay.bulk_dispute_processor.repository.BulkDisputeJobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
public class AtomicJobUpdater {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private BulkDisputeJobRepository jobRepository;

    /**
     * Atomically update the last processed row to prevent race conditions
     * Only updates if the new row number is greater than the current one
     */
    @Transactional
    public boolean updateLastProcessedRow(Long jobId, int newRow) {
        try {
            String sql = "UPDATE bulk_dispute_job SET last_processed_row = ? WHERE id = ? AND last_processed_row < ?";
            int updated = jdbcTemplate.update(sql, newRow, jobId, newRow);
            
            if (updated > 0) {
                log.debug("Updated lastProcessedRow for job {} to {}", jobId, newRow);
                return true;
            } else {
                log.debug("Failed to update lastProcessedRow for job {} to {} - another worker may have processed this row", jobId, newRow);
                return false;
            }
        } catch (Exception e) {
            log.error("Error updating lastProcessedRow for job {}: {}", jobId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Atomically update job progress metrics
     */
    @Transactional
    public boolean updateJobProgress(Long jobId, int processedRows, int successCount, int failureCount) {
        try {
            String sql = "UPDATE bulk_dispute_job SET processed_rows = ?, success_count = ?, failure_count = ? WHERE id = ?";
            int updated = jdbcTemplate.update(sql, processedRows, successCount, failureCount, jobId);
            
            if (updated > 0) {
                log.debug("Updated job progress for job {}: processed={}, success={}, failed={}", 
                        jobId, processedRows, successCount, failureCount);
                return true;
            } else {
                log.warn("Failed to update job progress for job {} - job may not exist", jobId);
                return false;
            }
        } catch (Exception e) {
            log.error("Error updating job progress for job {}: {}", jobId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Atomically update job status with optimistic locking
     */
    @Transactional
    public boolean updateJobStatus(Long jobId, BulkDisputeJob.JobStatus newStatus, BulkDisputeJob.JobStatus expectedStatus) {
        try {
            String sql = "UPDATE bulk_dispute_job SET status = ? WHERE id = ? AND status = ?";
            int updated = jdbcTemplate.update(sql, newStatus.name(), jobId, expectedStatus.name());
            
            if (updated > 0) {
                log.info("Updated job status for job {} from {} to {}", jobId, expectedStatus, newStatus);
                return true;
            } else {
                log.warn("Failed to update job status for job {} from {} to {} - status may have changed", 
                        jobId, expectedStatus, newStatus);
                return false;
            }
        } catch (Exception e) {
            log.error("Error updating job status for job {}: {}", jobId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if a job can be processed (not already being processed by another worker)
     */
    @Transactional
    public boolean claimJobForProcessing(Long jobId) {
        try {
            String sql = "UPDATE bulk_dispute_job SET status = 'RUNNING' WHERE id = ? AND status = 'PENDING'";
            int updated = jdbcTemplate.update(sql, jobId);
            
            if (updated > 0) {
                log.info("Successfully claimed job {} for processing", jobId);
                return true;
            } else {
                log.debug("Failed to claim job {} - may already be running or completed", jobId);
                return false;
            }
        } catch (Exception e) {
            log.error("Error claiming job {}: {}", jobId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get current job state atomically
     */
    public Optional<BulkDisputeJob> getJobState(Long jobId) {
        return jobRepository.findById(jobId);
    }
}
