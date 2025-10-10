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
public class BulkDisputeJobAudit {
    private Long id;
    private Long jobId;
    private String action;
    private String message;
    private LocalDateTime createdAt;
}
