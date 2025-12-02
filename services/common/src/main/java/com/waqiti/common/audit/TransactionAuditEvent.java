package com.waqiti.common.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL SECURITY: Transaction Audit Event for immutable financial record keeping
 * Contains all necessary information for regulatory compliance and fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionAuditEvent {
    
    // Event identification
    private UUID eventId;
    private String transactionId;
    private String transactionType;
    
    // Entity information
    private String sourceEntityId;
    private String sourceEntityType;
    private String targetEntityId;
    private String targetEntityType;
    
    // Financial details
    private BigDecimal amount;
    private String currency;
    private BigDecimal previousBalance;
    private BigDecimal newBalance;
    private String status;
    
    // Payment specific
    private String paymentMethod;
    private String providerId;
    
    // Risk and compliance
    private Double riskScore;
    private String riskLevel;
    
    // User and session context
    private String userId;
    private String sessionId;
    private String correlationId;
    private String clientIpAddress;
    private String userAgent;
    private String deviceFingerprint;
    
    // Additional data
    private Map<String, Object> metadata;
    
    // Audit integrity
    private String integrityHash;
    private Instant timestamp;
}