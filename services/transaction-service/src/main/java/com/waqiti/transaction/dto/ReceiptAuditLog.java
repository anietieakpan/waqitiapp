package com.waqiti.transaction.dto;

import com.waqiti.transaction.enums.ReceiptAuditAction;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Receipt audit log entry
 */
@Data
@Builder
public class ReceiptAuditLog {

    private UUID id;
    private UUID transactionId;
    private UUID receiptId;
    private String userId;
    private ReceiptAuditAction action;
    private String actionDescription;
    
    private String clientIp;
    private String userAgent;
    private String sessionId;
    
    private boolean success;
    private String errorMessage;
    private String riskLevel;
    private int securityScore;
    
    private Map<String, Object> metadata;
    private LocalDateTime timestamp;
    
    private String complianceCategory;
    private boolean flaggedForReview;
    private String reviewNotes;
}