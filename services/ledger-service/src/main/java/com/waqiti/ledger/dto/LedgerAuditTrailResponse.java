package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerAuditTrailResponse {
    private UUID auditId;
    private String entityType;
    private String entityId;
    private String action;
    private String performedBy;
    private LocalDateTime performedAt;
    private String ipAddress;
    private String userAgent;
    private Map<String, Object> previousState;
    private Map<String, Object> newState;
    private Map<String, Object> changes;
    private String description;
    private String source;
    private boolean successful;
    private String errorMessage;
}