package com.supersoft.sparkpay.bulk_dispute_processor.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface CombinedValidationService {
    
    /**
     * Validates CSV file and corresponding proof files in a single request
     * @param csvFile The CSV file containing dispute data
     * @param proofFiles List of proof files uploaded with the request
     * @return CombinedValidationResult with validation results
     */
    CombinedValidationResult validateCsvWithProofs(MultipartFile csvFile, List<MultipartFile> proofFiles);
    
    /**
     * Validates CSV file and corresponding proof files, and saves them to appropriate folders
     * @param csvFile The CSV file containing dispute data
     * @param proofFiles List of proof files uploaded with the request
     * @param uploadedBy User who uploaded the files
     * @param institutionCode Institution code
     * @param merchantId Merchant ID
     * @return CombinedValidationResult with validation results and file paths
     */
    CombinedValidationResult validateAndSaveCsvWithProofs(MultipartFile csvFile, List<MultipartFile> proofFiles, 
                                                         String uploadedBy, String institutionCode, String merchantId);
    
    /**
     * Validates CSV file and corresponding proof files, saves them, and returns response map
     * @param csvFile The CSV file containing dispute data
     * @param proofFiles List of proof files uploaded with the request
     * @param uploadedBy User who uploaded the files
     * @param institutionCode Institution code
     * @param merchantId Merchant ID
     * @return Map containing the response structure with sessionId
     */
    Map<String, Object> validateSessionAndProofs(MultipartFile csvFile, List<MultipartFile> proofFiles, 
                                                String uploadedBy, String institutionCode, String merchantId);
    
    class CombinedValidationResult {
        private boolean valid;
        private List<ValidationError> errors;
        private List<String> headers;
        private int totalRows;
        private int validRows;
        private int invalidRows;
        private int malformedRows;
        private long elapsedMs;
        
        // Accepted disputes breakdown
        private int acceptedSlated;
        private int acceptedSucceeded;
        private int acceptedFailed;
        
        // Rejected disputes breakdown
        private int rejectedSlated;
        private int rejectedSucceeded;
        private int rejectedFailed;
        private int missingReceipt;
        
        // File paths
        private String csvFilePath;
        private List<String> proofFilePaths;
        
        // Session info
        private Long sessionId;
        
        // Constructors
        public CombinedValidationResult() {
            this.valid = true;
            this.errors = new java.util.ArrayList<>();
            this.headers = new java.util.ArrayList<>();
            this.totalRows = 0;
            this.validRows = 0;
            this.invalidRows = 0;
            this.malformedRows = 0;
            this.elapsedMs = 0;
            this.acceptedSlated = 0;
            this.acceptedSucceeded = 0;
            this.acceptedFailed = 0;
            this.rejectedSlated = 0;
            this.rejectedSucceeded = 0;
            this.rejectedFailed = 0;
            this.missingReceipt = 0;
            this.csvFilePath = null;
            this.proofFilePaths = new java.util.ArrayList<>();
            this.sessionId = null;
        }
        
        // Builder methods
        public CombinedValidationResult addError(ValidationError error) {
            this.errors.add(error);
            this.valid = false;
            return this;
        }
        
        public CombinedValidationResult addError(int row, String column, String reason) {
            return addError(new ValidationError(row, column, reason));
        }
        
        public CombinedValidationResult setHeaders(List<String> headers) {
            this.headers = headers;
            return this;
        }
        
        public CombinedValidationResult setTotalRows(int totalRows) {
            this.totalRows = totalRows;
            return this;
        }
        
        public CombinedValidationResult setValidRows(int validRows) {
            this.validRows = validRows;
            return this;
        }
        
        public CombinedValidationResult setInvalidRows(int invalidRows) {
            this.invalidRows = invalidRows;
            return this;
        }
        
        public CombinedValidationResult setMalformedRows(int malformedRows) {
            this.malformedRows = malformedRows;
            return this;
        }
        
        public CombinedValidationResult setElapsedMs(long elapsedMs) {
            this.elapsedMs = elapsedMs;
            return this;
        }
        
        public CombinedValidationResult setAcceptedSlated(int acceptedSlated) {
            this.acceptedSlated = acceptedSlated;
            return this;
        }
        
        public CombinedValidationResult setAcceptedSucceeded(int acceptedSucceeded) {
            this.acceptedSucceeded = acceptedSucceeded;
            return this;
        }
        
        public CombinedValidationResult setAcceptedFailed(int acceptedFailed) {
            this.acceptedFailed = acceptedFailed;
            return this;
        }
        
        public CombinedValidationResult setRejectedSlated(int rejectedSlated) {
            this.rejectedSlated = rejectedSlated;
            return this;
        }
        
        public CombinedValidationResult setRejectedSucceeded(int rejectedSucceeded) {
            this.rejectedSucceeded = rejectedSucceeded;
            return this;
        }
        
        public CombinedValidationResult setRejectedFailed(int rejectedFailed) {
            this.rejectedFailed = rejectedFailed;
            return this;
        }
        
        public CombinedValidationResult setMissingReceipt(int missingReceipt) {
            this.missingReceipt = missingReceipt;
            return this;
        }
        
        public CombinedValidationResult setCsvFilePath(String csvFilePath) {
            this.csvFilePath = csvFilePath;
            return this;
        }
        
        public CombinedValidationResult setProofFilePaths(List<String> proofFilePaths) {
            this.proofFilePaths = proofFilePaths;
            return this;
        }
        
        public CombinedValidationResult setSessionId(Long sessionId) {
            this.sessionId = sessionId;
            return this;
        }
        
        // Getters
        public boolean isValid() { return valid; }
        public List<ValidationError> getErrors() { return errors; }
        public List<String> getHeaders() { return headers; }
        public int getTotalRows() { return totalRows; }
        public int getValidRows() { return validRows; }
        public int getInvalidRows() { return invalidRows; }
        public int getMalformedRows() { return malformedRows; }
        public long getElapsedMs() { return elapsedMs; }
        public int getAcceptedSlated() { return acceptedSlated; }
        public int getAcceptedSucceeded() { return acceptedSucceeded; }
        public int getAcceptedFailed() { return acceptedFailed; }
        public int getRejectedSlated() { return rejectedSlated; }
        public int getRejectedSucceeded() { return rejectedSucceeded; }
        public int getRejectedFailed() { return rejectedFailed; }
        public int getMissingReceipt() { return missingReceipt; }
        public String getCsvFilePath() { return csvFilePath; }
        public List<String> getProofFilePaths() { return proofFilePaths; }
        public Long getSessionId() { return sessionId; }
        
        // Helper methods
        public int getRequested() { return totalRows; }
        public int getSucceeded() { return validRows; }
        public int getFailed() { return invalidRows; }
        
        public Map<String, Object> toResponseMap() {
            return Map.of(
                "totals", Map.of(
                    "requested", getRequested(),
                    "malformedRows", malformedRows,
                    "succeeded", getSucceeded(),
                    "failed", getFailed(),
                    "elapsedMs", elapsedMs
                ),
                "accepted", Map.of(
                    "slated", acceptedSlated,
                    "succeeded", acceptedSucceeded,
                    "failed", acceptedFailed
                ),
                "rejected", Map.of(
                    "slated", rejectedSlated,
                    "succeeded", rejectedSucceeded,
                    "failed", rejectedFailed,
                    "missingReceipt", missingReceipt
                )
            );
        }
    }
    
    class ValidationError {
        private int row;
        private String column;
        private String reason;

        public ValidationError(int row, String column, String reason) {
            this.row = row;
            this.column = column;
            this.reason = reason;
        }

        // Getters
        public int getRow() { return row; }
        public String getColumn() { return column; }
        public String getReason() { return reason; }
    }
}
