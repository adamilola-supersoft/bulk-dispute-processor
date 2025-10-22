package com.supersoft.sparkpay.bulk_dispute_processor.service;

import com.supersoft.sparkpay.bulk_dispute_processor.domain.BulkDisputeSession;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface BulkDisputeSessionService {
    
    /**
     * Upload a CSV file and create a new session
     */
    SessionUploadResult uploadSession(MultipartFile file, String uploadedBy, String institutionCode, String merchantId);
    
    /**
     * Get preview data for a session
     */
    SessionPreviewResult getSessionPreview(Long sessionId, int rows, int offset);
    SessionPreviewResult getSessionPreview(Long sessionId, int rows, int offset, boolean includeErrors);
    
    /**
     * Overwrite session file with optimistic locking
     */
    SessionOverwriteResult overwriteSessionFile(Long sessionId, MultipartFile file, String ifMatch);
    
    /**
     * Confirm session and create processing job
     */
    SessionConfirmResult confirmSession(Long sessionId);
    
    /**
     * Get session file as blob
     */
    byte[] getSessionFileBlob(Long sessionId) throws IOException;
    
    /**
     * Get session file path (for download)
     */
    String getSessionFilePath(Long sessionId);
    
    /**
     * Get paginated list of sessions
     */
    SessionListResult getSessions(int page, int size, String status, String uploadedBy, String institutionCode, String merchantId);
    
    class SessionUploadResult {
        private final boolean success;
        private final Long sessionId;
        private final List<Map<String, String>> preview;
        private final int totalRows;
        private final int version;
        private final String error;
        private final List<CsvValidationService.ValidationError> validationErrors;
        
        public SessionUploadResult(boolean success, Long sessionId, List<Map<String, String>> preview, 
                                  int totalRows, int version, String error, List<CsvValidationService.ValidationError> validationErrors) {
            this.success = success;
            this.sessionId = sessionId;
            this.preview = preview;
            this.totalRows = totalRows;
            this.version = version;
            this.error = error;
            this.validationErrors = validationErrors;
        }
        
        public static SessionUploadResult success(Long sessionId, List<Map<String, String>> preview, 
                                                int totalRows, int version) {
            return new SessionUploadResult(true, sessionId, preview, totalRows, version, null, null);
        }
        
        public static SessionUploadResult failure(String error) {
            return new SessionUploadResult(false, null, null, 0, 0, error, null);
        }
        
        public static SessionUploadResult validationFailure(List<CsvValidationService.ValidationError> validationErrors) {
            return new SessionUploadResult(false, null, null, 0, 0, "Validation failed", validationErrors);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public Long getSessionId() { return sessionId; }
        public List<Map<String, String>> getPreview() { return preview; }
        public int getTotalRows() { return totalRows; }
        public int getVersion() { return version; }
        public String getError() { return error; }
        public List<CsvValidationService.ValidationError> getValidationErrors() { return validationErrors; }
    }
    
    class SessionPreviewResult {
        private final boolean success;
        private final Long sessionId;
        private final List<Map<String, String>> preview;
        private final int totalRows;
        private final int version;
        private final String error;
        private final boolean hasErrors;
        private final List<Integer> pagesWithErrors;
        
        public SessionPreviewResult(boolean success, Long sessionId, List<Map<String, String>> preview, 
                                   int totalRows, int version, String error, boolean hasErrors, List<Integer> pagesWithErrors) {
            this.success = success;
            this.sessionId = sessionId;
            this.preview = preview;
            this.totalRows = totalRows;
            this.version = version;
            this.error = error;
            this.hasErrors = hasErrors;
            this.pagesWithErrors = pagesWithErrors;
        }
        
        public static SessionPreviewResult success(Long sessionId, List<Map<String, String>> preview, 
                                                 int totalRows, int version, boolean hasErrors, List<Integer> pagesWithErrors) {
            return new SessionPreviewResult(true, sessionId, preview, totalRows, version, null, hasErrors, pagesWithErrors);
        }
        
        public static SessionPreviewResult failure(String error) {
            return new SessionPreviewResult(false, null, null, 0, 0, error, false, List.of());
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public Long getSessionId() { return sessionId; }
        public List<Map<String, String>> getPreview() { return preview; }
        public int getTotalRows() { return totalRows; }
        public int getVersion() { return version; }
        public String getError() { return error; }
        public boolean isHasErrors() { return hasErrors; }
        public List<Integer> getPagesWithErrors() { return pagesWithErrors; }
    }
    
    class SessionOverwriteResult {
        private final boolean success;
        private final Long sessionId;
        private final int version;
        private final int totalRows;
        private final String error;
        
        public SessionOverwriteResult(boolean success, Long sessionId, int version, int totalRows, String error) {
            this.success = success;
            this.sessionId = sessionId;
            this.version = version;
            this.totalRows = totalRows;
            this.error = error;
        }
        
        public static SessionOverwriteResult success(Long sessionId, int version, int totalRows) {
            return new SessionOverwriteResult(true, sessionId, version, totalRows, null);
        }
        
        public static SessionOverwriteResult failure(String error) {
            return new SessionOverwriteResult(false, null, 0, 0, error);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public Long getSessionId() { return sessionId; }
        public int getVersion() { return version; }
        public int getTotalRows() { return totalRows; }
        public String getError() { return error; }
    }
    
    class SessionConfirmResult {
        private final boolean success;
        private final Long jobId;
        private final String error;
        
        public SessionConfirmResult(boolean success, Long jobId, String error) {
            this.success = success;
            this.jobId = jobId;
            this.error = error;
        }
        
        public static SessionConfirmResult success(Long jobId) {
            return new SessionConfirmResult(true, jobId, null);
        }
        
        public static SessionConfirmResult failure(String error) {
            return new SessionConfirmResult(false, null, error);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public Long getJobId() { return jobId; }
        public String getError() { return error; }
    }
    
    class SessionListResult {
        private final boolean success;
        private final List<SessionSummary> sessions;
        private final int totalPages;
        private final long totalElements;
        private final int currentPage;
        private final int pageSize;
        private final String error;
        
        public SessionListResult(boolean success, List<SessionSummary> sessions, int totalPages, 
                               long totalElements, int currentPage, int pageSize, String error) {
            this.success = success;
            this.sessions = sessions;
            this.totalPages = totalPages;
            this.totalElements = totalElements;
            this.currentPage = currentPage;
            this.pageSize = pageSize;
            this.error = error;
        }
        
        public static SessionListResult success(List<SessionSummary> sessions, int totalPages, 
                                              long totalElements, int currentPage, int pageSize) {
            return new SessionListResult(true, sessions, totalPages, totalElements, currentPage, pageSize, null);
        }
        
        public static SessionListResult failure(String error) {
            return new SessionListResult(false, null, 0, 0, 0, 0, error);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public List<SessionSummary> getSessions() { return sessions; }
        public int getTotalPages() { return totalPages; }
        public long getTotalElements() { return totalElements; }
        public int getCurrentPage() { return currentPage; }
        public int getPageSize() { return pageSize; }
        public String getError() { return error; }
    }
    
    class SessionSummary {
        private final Long id;
        private final String institutionCode;
        private final String merchantId;
        private final String uploadedBy;
        private final String fileName;
        private final String status;
        private final int totalRows;
        private final int validRows;
        private final int invalidRows;
        private final String createdAt;
        private final String updatedAt;
        private final JobSummary job;
        private final boolean hasErrors;
        
        public SessionSummary(Long id, String institutionCode, String merchantId, String uploadedBy, String fileName, String status, 
                            int totalRows, int validRows, int invalidRows, String createdAt, String updatedAt,
                            Long jobId, String jobStatus, int processedRows, int successCount, int failureCount, boolean hasErrors) {
            this.id = id;
            this.institutionCode = institutionCode;
            this.merchantId = merchantId;
            this.uploadedBy = uploadedBy;
            this.fileName = fileName;
            this.status = status;
            this.totalRows = totalRows;
            this.validRows = validRows;
            this.invalidRows = invalidRows;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.job = jobId != null ? new JobSummary(jobId, jobStatus, processedRows, successCount, failureCount) : null;
            this.hasErrors = hasErrors;
        }
        
        // Getters
        public Long getId() { return id; }
        public String getInstitutionCode() { return institutionCode; }
        public String getMerchantId() { return merchantId; }
        public String getUploadedBy() { return uploadedBy; }
        public String getFileName() { return fileName; }
        public String getStatus() { return status; }
        public int getTotalRows() { return totalRows; }
        public int getValidRows() { return validRows; }
        public int getInvalidRows() { return invalidRows; }
        public String getCreatedAt() { return createdAt; }
        public String getUpdatedAt() { return updatedAt; }
        public JobSummary getJob() { return job; }
        public boolean isHasErrors() { return hasErrors; }
    }
    
    class JobSummary {
        private final Long id;
        private final String status;
        private final int processedRows;
        private final int successCount;
        private final int failureCount;
        
        public JobSummary(Long id, String status, int processedRows, int successCount, int failureCount) {
            this.id = id;
            this.status = status;
            this.processedRows = processedRows;
            this.successCount = successCount;
            this.failureCount = failureCount;
        }
        
        // Getters
        public Long getId() { return id; }
        public String getStatus() { return status; }
        public int getProcessedRows() { return processedRows; }
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
    }
}
