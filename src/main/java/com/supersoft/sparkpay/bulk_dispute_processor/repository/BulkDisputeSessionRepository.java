package com.supersoft.sparkpay.bulk_dispute_processor.repository;

import com.supersoft.sparkpay.bulk_dispute_processor.domain.BulkDisputeSession;
import com.supersoft.sparkpay.bulk_dispute_processor.service.BulkDisputeSessionService;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class BulkDisputeSessionRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final RowMapper<BulkDisputeSession> ROW_MAPPER = new RowMapper<BulkDisputeSession>() {
        @Override
        public BulkDisputeSession mapRow(ResultSet rs, int rowNum) throws SQLException {
            return BulkDisputeSession.builder()
                    .id(rs.getLong("id"))
                    .institutionCode(rs.getString("institution_code"))
                    .merchantId(rs.getString("merchant_id"))
                    .uploadedBy(rs.getString("uploaded_by"))
                    .filePath(rs.getString("file_path"))
                    .fileName(rs.getString("file_name"))
                    .fileSize(rs.getLong("file_size"))
                    .status(BulkDisputeSession.SessionStatus.valueOf(rs.getString("status")))
                    .totalRows(rs.getInt("total_rows"))
                    .validRows(rs.getInt("valid_rows"))
                    .invalidRows(rs.getInt("invalid_rows"))
                    .version(rs.getInt("version"))
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                    .build();
        }
    };

    public BulkDisputeSession save(BulkDisputeSession session) {
        if (session.getId() == null) {
            return insert(session);
        } else {
            return update(session);
        }
    }

    private BulkDisputeSession insert(BulkDisputeSession session) {
        String sql = "INSERT INTO bulk_dispute_session (institution_code, merchant_id, uploaded_by, file_path, file_name, file_size, status, total_rows, valid_rows, invalid_rows, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        KeyHolder keyHolder = new GeneratedKeyHolder();
        
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, session.getInstitutionCode());
            ps.setString(2, session.getMerchantId());
            ps.setString(3, session.getUploadedBy());
            ps.setString(4, session.getFilePath());
            ps.setString(5, session.getFileName());
            ps.setLong(6, session.getFileSize());
            ps.setString(7, session.getStatus().name());
            ps.setInt(8, session.getTotalRows());
            ps.setInt(9, session.getValidRows());
            ps.setInt(10, session.getInvalidRows());
            ps.setInt(11, session.getVersion());
            return ps;
        }, keyHolder);
        
        session.setId(keyHolder.getKey().longValue());
        return session;
    }

    private BulkDisputeSession update(BulkDisputeSession session) {
        String sql = "UPDATE bulk_dispute_session SET institution_code=?, merchant_id=?, uploaded_by=?, file_path=?, file_name=?, file_size=?, status=?, total_rows=?, valid_rows=?, invalid_rows=?, version=? WHERE id=? AND version=?";
        
        int rowsUpdated = jdbcTemplate.update(sql,
                session.getInstitutionCode(),
                session.getMerchantId(),
                session.getUploadedBy(),
                session.getFilePath(),
                session.getFileName(),
                session.getFileSize(),
                session.getStatus().name(),
                session.getTotalRows(),
                session.getValidRows(),
                session.getInvalidRows(),
                session.getVersion() + 1, // increment version
                session.getId(),
                session.getVersion() // check current version
        );
        
        if (rowsUpdated == 0) {
            throw new RuntimeException("Optimistic locking failed - session was modified by another process");
        }
        
        session.setVersion(session.getVersion() + 1);
        return session;
    }

    public Optional<BulkDisputeSession> findById(Long id) {
        String sql = "SELECT * FROM bulk_dispute_session WHERE id = ?";
        List<BulkDisputeSession> sessions = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return sessions.isEmpty() ? Optional.empty() : Optional.of(sessions.get(0));
    }

    public List<BulkDisputeSession> findByStatus(BulkDisputeSession.SessionStatus status) {
        String sql = "SELECT * FROM bulk_dispute_session WHERE status = ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, status.name());
    }

    public List<BulkDisputeSessionService.SessionSummary> findSessionsWithPagination(int page, int size, String status, String uploadedBy, String institutionCode, String merchantId) {
        StringBuilder sql = new StringBuilder("SELECT s.id, s.institution_code, s.merchant_id, s.uploaded_by, s.file_name, s.status, s.total_rows, s.valid_rows, s.invalid_rows, s.created_at, s.updated_at, j.id as job_id, j.status as job_status, j.processed_rows, j.success_count, j.failure_count FROM bulk_dispute_session s LEFT JOIN bulk_dispute_job j ON s.id = j.session_id WHERE 1=1");
        List<Object> params = new ArrayList<>();
        
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND s.status = ?");
            params.add(status);
        }
        
        if (uploadedBy != null && !uploadedBy.trim().isEmpty()) {
            sql.append(" AND s.uploaded_by = ?");
            params.add(uploadedBy);
        }
        
        if (institutionCode != null && !institutionCode.trim().isEmpty()) {
            sql.append(" AND s.institution_code = ?");
            params.add(institutionCode);
        }
        
        if (merchantId != null && !merchantId.trim().isEmpty()) {
            sql.append(" AND s.merchant_id = ?");
            params.add(merchantId);
        }
        
        sql.append(" ORDER BY s.created_at DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);
        
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            Long jobId = rs.getLong("job_id");
            if (rs.wasNull()) jobId = null;
            
            return new BulkDisputeSessionService.SessionSummary(
                rs.getLong("id"),
                rs.getString("institution_code"),
                rs.getString("merchant_id"),
                rs.getString("uploaded_by"),
                rs.getString("file_name"),
                rs.getString("status"),
                rs.getInt("total_rows"),
                rs.getInt("valid_rows"),
                rs.getInt("invalid_rows"),
                rs.getTimestamp("created_at").toLocalDateTime().toString(),
                rs.getTimestamp("updated_at").toLocalDateTime().toString(),
                jobId,
                rs.getString("job_status"),
                rs.getInt("processed_rows"),
                rs.getInt("success_count"),
                rs.getInt("failure_count")
            );
        }, params.toArray());
    }

    public long countSessions(String status, String uploadedBy, String institutionCode, String merchantId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM bulk_dispute_session WHERE 1=1");
        List<Object> params = new ArrayList<>();
        
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        
        if (uploadedBy != null && !uploadedBy.trim().isEmpty()) {
            sql.append(" AND uploaded_by = ?");
            params.add(uploadedBy);
        }
        
        if (institutionCode != null && !institutionCode.trim().isEmpty()) {
            sql.append(" AND institution_code = ?");
            params.add(institutionCode);
        }
        
        if (merchantId != null && !merchantId.trim().isEmpty()) {
            sql.append(" AND merchant_id = ?");
            params.add(merchantId);
        }
        
        return jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
    }
}
