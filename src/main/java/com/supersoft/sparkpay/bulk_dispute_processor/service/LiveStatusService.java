package com.supersoft.sparkpay.bulk_dispute_processor.service;

import com.supersoft.sparkpay.bulk_dispute_processor.repository.DisputeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service for retrieving live dispute statuses
 */
@Slf4j
@Service
public class LiveStatusService {

    @Autowired
    private DisputeRepository disputeRepository;

    /**
     * Get live status information for a list of unique keys
     * @param uniqueKeys List of unique keys to check
     * @return Map of unique key to live status information
     */
    public Map<String, DisputeRepository.DisputeStatusInfo> getLiveStatuses(List<String> uniqueKeys) {
        if (uniqueKeys == null || uniqueKeys.isEmpty()) {
            log.debug("No unique keys provided for live status check");
            return Map.of();
        }

        log.info("LiveStatusService: Getting live statuses for {} unique keys: {}", uniqueKeys.size(), uniqueKeys);
        Map<String, DisputeRepository.DisputeStatusInfo> statuses = disputeRepository.getDisputeStatuses(uniqueKeys);
        
        log.info("LiveStatusService: Retrieved live statuses for {} out of {} requested keys", statuses.size(), uniqueKeys.size());
        return statuses;
    }

    /**
     * Get live status for a single unique key
     * @param uniqueKey The unique key to check
     * @return Live status information or null if not found
     */
    public DisputeRepository.DisputeStatusInfo getLiveStatus(String uniqueKey) {
        if (uniqueKey == null || uniqueKey.trim().isEmpty()) {
            log.warn("Empty unique key provided for live status check");
            return null;
        }

        Map<String, DisputeRepository.DisputeStatusInfo> statuses = getLiveStatuses(List.of(uniqueKey));
        return statuses.get(uniqueKey);
    }

    /**
     * Check if a dispute is processed (has been resolved)
     * @param uniqueKey The unique key to check
     * @return true if processed, false otherwise
     */
    public boolean isDisputeProcessed(String uniqueKey) {
        DisputeRepository.DisputeStatusInfo status = getLiveStatus(uniqueKey);
        return status != null && status.isProcessed();
    }

    /**
     * Get human-readable status description for a dispute
     * @param uniqueKey The unique key to check
     * @return Status description (PENDING, ACCEPTED, REJECTED, UNKNOWN)
     */
    public String getStatusDescription(String uniqueKey) {
        DisputeRepository.DisputeStatusInfo status = getLiveStatus(uniqueKey);
        if (status == null) {
            return "NOT_FOUND";
        }
        return status.getStatusDescription();
    }
}
