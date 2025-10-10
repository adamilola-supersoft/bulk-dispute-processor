package com.supersoft.sparkpay.bulk_dispute_processor.service;

import java.util.Map;

public interface DisputeUpdater {
    
    /**
     * Process a single dispute row
     * @param row The dispute data as a map of column names to values
     * @return ProcessingResult indicating success or failure
     */
    ProcessingResult processRow(Map<String, String> row);
    
    class ProcessingResult {
        private final boolean success;
        private final String errorMessage;
        
        public ProcessingResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public static ProcessingResult success() {
            return new ProcessingResult(true, null);
        }
        
        public static ProcessingResult failure(String errorMessage) {
            return new ProcessingResult(false, errorMessage);
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
