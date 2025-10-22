package com.supersoft.sparkpay.bulk_dispute_processor.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Slf4j
@Repository
public class BulkDisputeSessionErrorRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Save validation errors for a session
     */
    public void saveErrors(Long sessionId, List<SessionError> errors) {
        if (errors == null || errors.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO bulk_dispute_session_errors (session_id, `row_number`, column_name, error_message) VALUES (?, ?, ?, ?)";
        
        try {
            jdbcTemplate.batchUpdate(sql, errors, errors.size(),
                    (ps, error) -> {
                        ps.setLong(1, sessionId);
                        ps.setInt(2, error.getRowNumber());
                        ps.setString(3, error.getColumnName());
                        ps.setString(4, error.getErrorMessage());
                    });
            log.info("Saved {} validation errors for session {}", errors.size(), sessionId);
        } catch (Exception e) {
            log.error("Error saving validation errors for session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Get all errors for a session
     */
    public List<SessionError> getErrorsBySessionId(Long sessionId) {
        String sql = "SELECT `row_number`, column_name, error_message FROM bulk_dispute_session_errors WHERE session_id = ? ORDER BY `row_number`";
        
        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> 
                new SessionError(
                    rs.getInt("row_number"),
                    rs.getString("column_name"),
                    rs.getString("error_message")
                ), sessionId);
        } catch (Exception e) {
            log.error("Error getting validation errors for session {}: {}", sessionId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Get errors for specific rows (for pagination)
     */
    public List<SessionError> getErrorsForRows(Long sessionId, List<Integer> rowNumbers) {
        if (rowNumbers == null || rowNumbers.isEmpty()) {
            return List.of();
        }

        // Create placeholders for the IN clause
        String placeholders = String.join(",", rowNumbers.stream().map(r -> "?").toList());
        String sql = "SELECT `row_number`, column_name, error_message FROM bulk_dispute_session_errors " +
                    "WHERE session_id = ? AND `row_number` IN (" + placeholders + ") ORDER BY `row_number`";
        
        // Create parameters array: sessionId + rowNumbers
        Object[] params = new Object[rowNumbers.size() + 1];
        params[0] = sessionId;
        for (int i = 0; i < rowNumbers.size(); i++) {
            params[i + 1] = rowNumbers.get(i);
        }
        
        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> 
                new SessionError(
                    rs.getInt("row_number"),
                    rs.getString("column_name"),
                    rs.getString("error_message")
                ), params);
        } catch (Exception e) {
            log.error("Error getting validation errors for session {} rows {}: {}", sessionId, rowNumbers, e.getMessage());
            return List.of();
        }
    }

    /**
     * Get errors grouped by row number
     */
    public Map<Integer, List<SessionError>> getErrorsGroupedByRow(Long sessionId) {
        List<SessionError> allErrors = getErrorsBySessionId(sessionId);
        return allErrors.stream()
                .collect(java.util.stream.Collectors.groupingBy(SessionError::getRowNumber));
    }

    /**
     * Check if a specific row has errors
     */
    public boolean hasRowErrors(Long sessionId, int rowNumber) {
        String sql = "SELECT COUNT(*) FROM bulk_dispute_session_errors WHERE session_id = ? AND `row_number` = ?";
        
        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, sessionId, rowNumber);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("Error checking if row {} has errors for session {}: {}", rowNumber, sessionId, e.getMessage());
            return false;
        }
    }

    /**
     * Check if a session has any errors
     */
    public boolean hasAnyErrors(Long sessionId) {
        String sql = "SELECT COUNT(*) FROM bulk_dispute_session_errors WHERE session_id = ?";
        
        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, sessionId);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("Error checking if session {} has any errors: {}", sessionId, e.getMessage());
            return false;
        }
    }

    /**
     * Delete all errors for a session
     */
    public void deleteErrorsBySessionId(Long sessionId) {
        String sql = "DELETE FROM bulk_dispute_session_errors WHERE session_id = ?";
        
        try {
            int deleted = jdbcTemplate.update(sql, sessionId);
            log.info("Deleted {} validation errors for session {}", deleted, sessionId);
        } catch (Exception e) {
            log.error("Error deleting validation errors for session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Session error data class
     */
    public static class SessionError {
        private final int rowNumber;
        private final String columnName;
        private final String errorMessage;

        public SessionError(int rowNumber, String columnName, String errorMessage) {
            this.rowNumber = rowNumber;
            this.columnName = columnName;
            this.errorMessage = errorMessage;
        }

        public int getRowNumber() { return rowNumber; }
        public String getColumnName() { return columnName; }
        public String getErrorMessage() { return errorMessage; }
    }
}
