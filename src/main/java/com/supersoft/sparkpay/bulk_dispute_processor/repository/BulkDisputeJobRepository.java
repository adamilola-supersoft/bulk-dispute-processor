package com.supersoft.sparkpay.bulk_dispute_processor.repository;

import com.supersoft.sparkpay.bulk_dispute_processor.domain.BulkDisputeJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class BulkDisputeJobRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final RowMapper<BulkDisputeJob> ROW_MAPPER = new RowMapper<BulkDisputeJob>() {
        @Override
        public BulkDisputeJob mapRow(ResultSet rs, int rowNum) throws SQLException {
            return BulkDisputeJob.builder()
                    .id(rs.getLong("id"))
                    .sessionId(rs.getLong("session_id"))
                    .jobRef(rs.getString("job_ref"))
                    .status(BulkDisputeJob.JobStatus.valueOf(rs.getString("status")))
                    .totalRows(rs.getInt("total_rows"))
                    .processedRows(rs.getInt("processed_rows"))
                    .successCount(rs.getInt("success_count"))
                    .failureCount(rs.getInt("failure_count"))
                    .lastProcessedRow(rs.getInt("last_processed_row"))
                    .errorReportPath(rs.getString("error_report_path"))
                    .retryCount(rs.getInt("retry_count"))
                    .failureReason(rs.getString("failure_reason"))
                    .failureType(rs.getString("failure_type"))
                    .lastRetryAt(rs.getTimestamp("last_retry_at") != null ? rs.getTimestamp("last_retry_at").toLocalDateTime() : null)
                    .nextRetryAt(rs.getTimestamp("next_retry_at") != null ? rs.getTimestamp("next_retry_at").toLocalDateTime() : null)
                    .startedAt(rs.getTimestamp("started_at") != null ? rs.getTimestamp("started_at").toLocalDateTime() : null)
                    .completedAt(rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toLocalDateTime() : null)
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                    .build();
        }
    };

    public BulkDisputeJob save(BulkDisputeJob job) {
        if (job.getId() == null) {
            return insert(job);
        } else {
            return update(job);
        }
    }

    private BulkDisputeJob insert(BulkDisputeJob job) {
        String sql = "INSERT INTO bulk_dispute_job (session_id, job_ref, status, total_rows, processed_rows, success_count, failure_count, error_report_path, started_at, completed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        KeyHolder keyHolder = new GeneratedKeyHolder();
        
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, job.getSessionId());
            ps.setString(2, job.getJobRef());
            ps.setString(3, job.getStatus().name());
            ps.setInt(4, job.getTotalRows());
            ps.setInt(5, job.getProcessedRows());
            ps.setInt(6, job.getSuccessCount());
            ps.setInt(7, job.getFailureCount());
            ps.setString(8, job.getErrorReportPath());
            ps.setTimestamp(9, job.getStartedAt() != null ? java.sql.Timestamp.valueOf(job.getStartedAt()) : null);
            ps.setTimestamp(10, job.getCompletedAt() != null ? java.sql.Timestamp.valueOf(job.getCompletedAt()) : null);
            return ps;
        }, keyHolder);
        
        job.setId(keyHolder.getKey().longValue());
        return job;
    }

    private BulkDisputeJob update(BulkDisputeJob job) {
        String sql = "UPDATE bulk_dispute_job SET session_id=?, job_ref=?, status=?, total_rows=?, processed_rows=?, success_count=?, failure_count=?, last_processed_row=?, error_report_path=?, retry_count=?, failure_reason=?, failure_type=?, last_retry_at=?, next_retry_at=?, started_at=?, completed_at=? WHERE id=?";
        
        jdbcTemplate.update(sql,
                job.getSessionId(),
                job.getJobRef(),
                job.getStatus().name(),
                job.getTotalRows(),
                job.getProcessedRows(),
                job.getSuccessCount(),
                job.getFailureCount(),
                job.getLastProcessedRow(),
                job.getErrorReportPath(),
                job.getRetryCount(),
                job.getFailureReason(),
                job.getFailureType(),
                job.getLastRetryAt() != null ? Timestamp.valueOf(job.getLastRetryAt()) : null,
                job.getNextRetryAt() != null ? Timestamp.valueOf(job.getNextRetryAt()) : null,
                job.getStartedAt() != null ? Timestamp.valueOf(job.getStartedAt()) : null,
                job.getCompletedAt() != null ? Timestamp.valueOf(job.getCompletedAt()) : null,
                job.getId()
        );
        
        return job;
    }

    public Optional<BulkDisputeJob> findById(Long id) {
        String sql = "SELECT * FROM bulk_dispute_job WHERE id = ?";
        List<BulkDisputeJob> jobs = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return jobs.isEmpty() ? Optional.empty() : Optional.of(jobs.get(0));
    }

    public List<BulkDisputeJob> findBySessionId(Long sessionId) {
        String sql = "SELECT * FROM bulk_dispute_job WHERE session_id = ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, sessionId);
    }

    public List<BulkDisputeJob> findJobsWithFilters(int page, int size, String status, Long sessionId, 
            LocalDateTime startDate, LocalDateTime endDate, String sortBy, String sortDir) {
        StringBuilder sql = new StringBuilder("SELECT * FROM bulk_dispute_job WHERE 1=1");
        List<Object> params = new java.util.ArrayList<>();
        
        // Add filters
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        
        if (sessionId != null) {
            sql.append(" AND session_id = ?");
            params.add(sessionId);
        }
        
        if (startDate != null) {
            sql.append(" AND created_at >= ?");
            params.add(java.sql.Timestamp.valueOf(startDate));
        }
        
        if (endDate != null) {
            sql.append(" AND created_at <= ?");
            params.add(java.sql.Timestamp.valueOf(endDate));
        }
        
        // Add sorting
        String validSortBy = isValidSortField(sortBy) ? sortBy : "created_at";
        String validSortDir = "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
        sql.append(" ORDER BY ").append(validSortBy).append(" ").append(validSortDir);
        
        // Add pagination
        sql.append(" LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);
        
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }
    
    public long countJobsWithFilters(String status, Long sessionId, LocalDateTime startDate, LocalDateTime endDate) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM bulk_dispute_job WHERE 1=1");
        List<Object> params = new java.util.ArrayList<>();
        
        // Add filters
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        
        if (sessionId != null) {
            sql.append(" AND session_id = ?");
            params.add(sessionId);
        }
        
        if (startDate != null) {
            sql.append(" AND created_at >= ?");
            params.add(java.sql.Timestamp.valueOf(startDate));
        }
        
        if (endDate != null) {
            sql.append(" AND created_at <= ?");
            params.add(java.sql.Timestamp.valueOf(endDate));
        }
        
        return jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
    }
    
    /**
     * Find jobs that are ready for retry based on next_retry_at time and retry count
     */
    public List<BulkDisputeJob> findJobsReadyForRetry(LocalDateTime now, int maxRetryAttempts) {
        String sql = "SELECT * FROM bulk_dispute_job WHERE next_retry_at IS NOT NULL AND next_retry_at <= ? AND retry_count < ? AND status IN ('FAILED', 'PAUSED') ORDER BY next_retry_at ASC";
        return jdbcTemplate.query(sql, ROW_MAPPER, Timestamp.valueOf(now), maxRetryAttempts);
    }

    /**
     * Find jobs by status
     */
    public List<BulkDisputeJob> findByStatus(BulkDisputeJob.JobStatus status) {
        String sql = "SELECT * FROM bulk_dispute_job WHERE status = ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, status.name());
    }

    private boolean isValidSortField(String sortBy) {
        return sortBy != null && (sortBy.equals("id") || sortBy.equals("status") || 
                sortBy.equals("created_at") || sortBy.equals("completed_at") || 
                sortBy.equals("started_at") || sortBy.equals("session_id"));
    }
}
