package com.supersoft.sparkpay.bulk_dispute_processor.repository;

import com.supersoft.sparkpay.bulk_dispute_processor.domain.BulkDisputeJobAudit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class BulkDisputeJobAuditRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public BulkDisputeJobAudit save(BulkDisputeJobAudit audit) {
        String sql = "INSERT INTO bulk_dispute_job_audit (job_id, action, message) VALUES (?, ?, ?)";
        
        KeyHolder keyHolder = new GeneratedKeyHolder();
        
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, audit.getJobId());
            ps.setString(2, audit.getAction());
            ps.setString(3, audit.getMessage());
            return ps;
        }, keyHolder);
        
        audit.setId(keyHolder.getKey().longValue());
        return audit;
    }

    public List<BulkDisputeJobAudit> findByJobId(Long jobId) {
        String sql = "SELECT * FROM bulk_dispute_job_audit WHERE job_id = ? ORDER BY created_at ASC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> BulkDisputeJobAudit.builder()
                .id(rs.getLong("id"))
                .jobId(rs.getLong("job_id"))
                .action(rs.getString("action"))
                .message(rs.getString("message"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .build(), jobId);
    }
}
