package com.waqiti.common.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Record representing an audit failure event for compliance and monitoring
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditFailureRecord {
    
    private String failureId;
    private String originalEventId;
    private String transactionId;
    private String failureType;
    private String eventType;
    private String userId;
    private String action;
    private String errorMessage;
    private String errorStackTrace;
    private String stackTrace;
    private String originalEventData;
    private java.time.Instant failureTimestamp;
    private LocalDateTime failedAt;
    private LocalDateTime retryAt;
    private int retryCount;
    private String severity;
    private boolean resolved;
    private String resolutionNotes;
    private LocalDateTime resolvedAt;
}