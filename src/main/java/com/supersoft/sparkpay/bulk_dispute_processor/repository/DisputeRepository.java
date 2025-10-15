package com.supersoft.sparkpay.bulk_dispute_processor.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
}
