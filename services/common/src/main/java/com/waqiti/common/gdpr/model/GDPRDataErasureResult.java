package com.waqiti.common.gdpr.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GDPR Data Erasure Result
 * PRODUCTION FIX: Created to support GDPRAuditService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GDPRDataErasureResult {
    private String requestId;
    private String userId;
    private ErasureStatus status;
    private Integer totalRecordsDeleted;
    private Integer totalTablesProcessed;
    private List<String> pendingTables;
    private List<String> failedTables;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long processingTimeMs;
    private String errorMessage;

    public enum ErasureStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        PARTIALLY_COMPLETED,
        FAILED
    }
}
