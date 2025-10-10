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
public class BulkDisputeSession {
    private Long id;
    private String uploadedBy;
    private String filePath;
    private String fileName;
    private SessionStatus status;
    private int totalRows;
    private int validRows;
    private int invalidRows;
    private String errorSummary;
    private int version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum SessionStatus {
        UPLOADED, VALIDATED, PREVIEWED, CONFIRMED,PROCESSING,FAILED
    }
}
