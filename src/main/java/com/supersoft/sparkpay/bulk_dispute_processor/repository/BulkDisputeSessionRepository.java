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
                    .uploadedBy(rs.getString("uploaded_by"))
                    .filePath(rs.getString("file_path"))
                    .fileName(rs.getString("file_name"))
                    .status(BulkDisputeSession.SessionStatus.valueOf(rs.getString("status")))
                    .totalRows(rs.getInt("total_rows"))
                    .validRows(rs.getInt("valid_rows"))
                    .invalidRows(rs.getInt("invalid_rows"))
                    .errorSummary(rs.getString("error_summary"))
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
        String sql = "INSERT INTO bulk_dispute_session (uploaded_by, file_path, file_name, status, total_rows, valid_rows, invalid_rows, error_summary, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        KeyHolder keyHolder = new GeneratedKeyHolder();
        
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, session.getUploadedBy());
            ps.setString(2, session.getFilePath());
            ps.setString(3, session.getFileName());
            ps.setString(4, session.getStatus().name());
            ps.setInt(5, session.getTotalRows());
            ps.setInt(6, session.getValidRows());
            ps.setInt(7, session.getInvalidRows());
            ps.setString(8, session.getErrorSummary());
            ps.setInt(9, session.getVersion());
            return ps;
        }, keyHolder);
        
        session.setId(keyHolder.getKey().longValue());
        return session;
    }

    private BulkDisputeSession update(BulkDisputeSession session) {
        String sql = "UPDATE bulk_dispute_session SET uploaded_by=?, file_path=?, file_name=?, status=?, total_rows=?, valid_rows=?, invalid_rows=?, error_summary=?, version=? WHERE id=? AND version=?";
        
        int rowsUpdated = jdbcTemplate.update(sql,
                session.getUploadedBy(),
                session.getFilePath(),
                session.getFileName(),
                session.getStatus().name(),
                session.getTotalRows(),
                session.getValidRows(),
                session.getInvalidRows(),
                session.getErrorSummary(),
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

    public List<BulkDisputeSessionService.SessionSummary> findSessionsWithPagination(int page, int size, String status, String uploadedBy) {
        StringBuilder sql = new StringBuilder("SELECT id, uploaded_by, file_name, status, total_rows, valid_rows, invalid_rows, created_at, updated_at FROM bulk_dispute_session WHERE 1=1");
        List<Object> params = new ArrayList<>();
        
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        
        if (uploadedBy != null && !uploadedBy.trim().isEmpty()) {
            sql.append(" AND uploaded_by = ?");
            params.add(uploadedBy);
        }
        
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);
        
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> 
            new BulkDisputeSessionService.SessionSummary(
                rs.getLong("id"),
                rs.getString("uploaded_by"),
                rs.getString("file_name"),
                rs.getString("status"),
                rs.getInt("total_rows"),
                rs.getInt("valid_rows"),
                rs.getInt("invalid_rows"),
                rs.getTimestamp("created_at").toLocalDateTime().toString(),
                rs.getTimestamp("updated_at").toLocalDateTime().toString()
            ), params.toArray());
    }

    public long countSessions(String status, String uploadedBy) {
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
        
        return jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
    }
}
