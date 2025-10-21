package com.supersoft.sparkpay.bulk_dispute_processor.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Slf4j
@Repository
public class DisputeRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public int updateDisputeStatus(String uniqueKey, String resolvedBy, int status, int resolved, String proofUri) {
        String sql = "UPDATE tbl_disputes " +
                "SET resolved_by = ?, status = ?, resolved = ?, " +
                "date_modified = now(), proof_of_reject_uri = ? " +
                "WHERE unique_log_code = ? AND status = -1 AND resolved = 0";

        log.info("Updating dispute: uniqueKey={}, resolvedBy={}, status={}, resolved={}, proofUri={}",
                uniqueKey, resolvedBy, status, resolved, proofUri);
        try {
            int result = jdbcTemplate.update(sql,
                    resolvedBy, status, resolved, proofUri, uniqueKey);

            log.info("Update result: {} rows affected", result);
            return result;
        } catch (Exception e) {
            log.error("Error executing update query", e);
            throw e;
        }
    }

    /**
     * Get live dispute statuses for multiple unique keys
     * @param uniqueKeys List of unique keys to check
     * @return Map of unique key to dispute status information
     */
    public Map<String, DisputeStatusInfo> getDisputeStatuses(List<String> uniqueKeys) {
        if (uniqueKeys == null || uniqueKeys.isEmpty()) {
            return Map.of();
        }

        String placeholders = String.join(",", uniqueKeys.stream().map(key -> "?").toList());
        String sql = "SELECT unique_log_code, status, resolved, resolved_by, date_modified, proof_of_reject_uri " +
                "FROM tbl_disputes " +
                "WHERE unique_log_code IN (" + placeholders + ")";

        log.info("DisputeRepository: Getting dispute statuses for {} unique keys: {}", uniqueKeys.size(), uniqueKeys);
        log.info("DisputeRepository: Executing SQL: {}", sql);
        
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, uniqueKeys.toArray());
            log.info("DisputeRepository: SQL query returned {} rows", results.size());
            
            Map<String, DisputeStatusInfo> statusMap = new java.util.HashMap<>();
            for (Map<String, Object> row : results) {
                String uniqueKey = (String) row.get("unique_log_code");
                Integer status = (Integer) row.get("status");
                Integer resolved = (Integer) row.get("resolved");
                String resolvedBy = (String) row.get("resolved_by");
                
                // Handle both LocalDateTime and Timestamp
                java.sql.Timestamp dateModified = null;
                Object dateModifiedObj = row.get("date_modified");
                if (dateModifiedObj instanceof java.sql.Timestamp) {
                    dateModified = (java.sql.Timestamp) dateModifiedObj;
                } else if (dateModifiedObj instanceof java.time.LocalDateTime) {
                    dateModified = java.sql.Timestamp.valueOf((java.time.LocalDateTime) dateModifiedObj);
                }
                
                String proofUri = (String) row.get("proof_of_reject_uri");
                
                log.info("DisputeRepository: Found dispute {} - status: {}, resolved: {}, resolvedBy: {}", 
                        uniqueKey, status, resolved, resolvedBy);
                
                statusMap.put(uniqueKey, new DisputeStatusInfo(
                    uniqueKey, status, resolved, resolvedBy, dateModified, proofUri
                ));
            }
            
            log.info("DisputeRepository: Found {} dispute statuses out of {} requested", statusMap.size(), uniqueKeys.size());
            return statusMap;
        } catch (Exception e) {
            log.error("Error getting dispute statuses", e);
            return Map.of();
        }
    }

    /**
     * Dispute status information
     */
    public static class DisputeStatusInfo {
        private final String uniqueKey;
        private final Integer status;
        private final Integer resolved;
        private final String resolvedBy;
        private final java.sql.Timestamp dateModified;
        private final String proofUri;

        public DisputeStatusInfo(String uniqueKey, Integer status, Integer resolved, String resolvedBy, 
                               java.sql.Timestamp dateModified, String proofUri) {
            this.uniqueKey = uniqueKey;
            this.status = status;
            this.resolved = resolved;
            this.resolvedBy = resolvedBy;
            this.dateModified = dateModified;
            this.proofUri = proofUri;
        }

        public String getUniqueKey() { return uniqueKey; }
        public Integer getStatus() { return status; }
        public Integer getResolved() { return resolved; }
        public String getResolvedBy() { return resolvedBy; }
        public java.sql.Timestamp getDateModified() { return dateModified; }
        public String getProofUri() { return proofUri; }
        
        /**
         * Get human-readable status based on business logic:
         * PENDING: status -1, resolved 0 OR status -2, resolved 1
         * ACCEPTED: status 0, resolved 0 OR status -3, resolved 1
         * REJECTED: status -4, resolved 1 OR status 1, resolved 1
         */
        public String getStatusDescription() {
            if (status == null) {
                return "Unknown";
            }
            
            // PENDING: status -1, resolved 0 OR status -2, resolved 1
            if ((status == -1 && (resolved == null || resolved == 0)) || 
                (status == -2 && resolved != null && resolved == 1)) {
                return "Pending";
            }
            
            // ACCEPTED: status 0, resolved 0 OR status -3, resolved 1
            if ((status == 0 && (resolved == null || resolved == 0)) || 
                (status == -3 && resolved != null && resolved == 1)) {
                return "Accepted";
            }
            
            // REJECTED: status -4, resolved 1 OR status 1, resolved 1
            if ((status == -4 && resolved != null && resolved == 1) || 
                (status == 1 && resolved != null && resolved == 1)) {
                return "Rejected";
            }
            
            return "Unknown";
        }
        
    }
}
