package com.supersoft.sparkpay.bulk_dispute_processor.service;

public interface JobMessagePublisher {
    void publishJobMessage(JobMessage jobMessage);
    
    class JobMessage {
        private Long jobId;
        private Long sessionId;
        private String filePath;
        private String uploadedBy;

        public JobMessage() {}

        public JobMessage(Long jobId, Long sessionId, String filePath, String uploadedBy) {
            this.jobId = jobId;
            this.sessionId = sessionId;
            this.filePath = filePath;
            this.uploadedBy = uploadedBy;
        }

        // Getters and setters
        public Long getJobId() { return jobId; }
        public void setJobId(Long jobId) { this.jobId = jobId; }
        public Long getSessionId() { return sessionId; }
        public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public String getUploadedBy() { return uploadedBy; }
        public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
    }
}
