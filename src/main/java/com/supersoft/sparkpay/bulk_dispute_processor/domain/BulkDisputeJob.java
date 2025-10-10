package com.supersoft.sparkpay.bulk_dispute_processor.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkDisputeJob {
    private Long id;
    private Long sessionId;
    private String jobRef;
    private JobStatus status;
    private int totalRows;
    private int processedRows;
    private int successCount;
    private int failureCount;
    private String errorReportPath;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum JobStatus {
        PENDING, RUNNING, COMPLETED, FAILED
    }
}
