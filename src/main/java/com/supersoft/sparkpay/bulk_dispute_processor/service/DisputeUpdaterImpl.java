package com.supersoft.sparkpay.bulk_dispute_processor.service;

import com.supersoft.sparkpay.bulk_dispute_processor.repository.DisputeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class DisputeUpdaterImpl implements DisputeUpdater {

    @Autowired
    DisputeRepository disputeRepository;
    
    @Autowired
    ProofService proofService;

    @Override
    public ProcessingResult processRow(Map<String, String> row) {
        String uniqueKey = row.get("Unique Key");
        String action = row.get("Action");
        String proofUri = row.get("Proof(Optional)");
        String resolvedBy = row.get("uploadedBy"); // From session context
        
        if (uniqueKey == null || uniqueKey.trim().isEmpty()) {
            return ProcessingResult.failure("Unique Key is required");
        }
        
        if (action == null || action.trim().isEmpty()) {
            return ProcessingResult.failure("Action is required");
        }
        
        try {
            int status = mapActionToStatus(action);
            int resolved = (status == 0) ? 0 : 1;
            
            // For REJECT actions, check if proof file exists
            String finalProofUri = proofUri;
            if ("REJECT".equalsIgnoreCase(action)) {
                if (proofService.proofExists(uniqueKey)) {
                    // Use the uploaded proof file path
                    finalProofUri = proofService.getProofFilePath(uniqueKey);
                    log.info("Using uploaded proof file for dispute: {} -> {}", uniqueKey, finalProofUri);
                } else if (proofUri == null || proofUri.trim().isEmpty()) {
                    log.warn("No proof provided for REJECT action on dispute: {}", uniqueKey);
                    return ProcessingResult.failure("Proof is required for REJECT actions. Please upload proof file or provide proof URI.");
                }
            }
            
            log.info("Processing dispute: uniqueKey={}, action={}, status={}, resolved={}, resolvedBy={}, proofUri={}", 
                    uniqueKey, action, status, resolved, resolvedBy, finalProofUri);
            
            int rows = disputeRepository.updateDisputeStatus(uniqueKey, resolvedBy, status, resolved, finalProofUri);
            
            if (rows > 0) {
                log.info("Successfully updated dispute: {} with action: {}", uniqueKey, action);
                return ProcessingResult.success();
            } else {
                log.warn("No matching dispute found or already processed: {}", uniqueKey);
                return ProcessingResult.failure("No matching dispute found or already processed");
            }
            
        } catch (Exception e) {
            log.error("Database error processing dispute: {}", uniqueKey, e);
            return ProcessingResult.failure("Database error: " + e.getMessage());
        }
    }
    
    private int mapActionToStatus(String action) {
        if ("ACCEPT".equalsIgnoreCase(action)) {
            return 0;  // ACCEPTED: status 0, resolved 0
        } else if ("REJECT".equalsIgnoreCase(action)) {
            return 1;  // REJECTED: status 1, resolved 1
        } else {
            throw new IllegalArgumentException("Invalid action: " + action + ". Must be ACCEPT or REJECT");
        }
    }
}
