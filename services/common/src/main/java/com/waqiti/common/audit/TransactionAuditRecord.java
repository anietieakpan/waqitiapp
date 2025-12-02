package com.waqiti.common.audit;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * CRITICAL SECURITY: Immutable transaction audit record for regulatory compliance
 * Stores all financial operations with cryptographic integrity verification
 */
@Entity
@Table(name = "transaction_audit_records", indexes = {
    @Index(name = "idx_transaction_id", columnList = "transactionId"),
    @Index(name = "idx_transaction_type", columnList = "transactionType"),
    @Index(name = "idx_user_id", columnList = "userId"),
    @Index(name = "idx_timestamp", columnList = "timestamp"),
    @Index(name = "idx_correlation_id", columnList = "correlationId"),
    @Index(name = "idx_amount", columnList = "amount"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_risk_score", columnList = "riskScore")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TransactionAuditRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private UUID eventId;
    
    @Column(nullable = false, length = 128)
    private String transactionId;
    
    @Column(nullable = false, length = 64)
    private String transactionType;
    
    // Entity information
    @Column(length = 128)
    private String sourceEntityId;
    
    @Column(length = 32)
    private String sourceEntityType;
    
    @Column(length = 128)
    private String targetEntityId;
    
    @Column(length = 32)
    private String targetEntityType;
    
    // Financial details
    @Column(precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(length = 3)
    private String currency;
    
    @Column(precision = 19, scale = 4)
    private BigDecimal previousBalance;
    
    @Column(precision = 19, scale = 4)
    private BigDecimal newBalance;
    
    @Column(nullable = false, length = 32)
    private String status;
    
    // Payment specific
    @Column(length = 64)
    private String paymentMethod;
    
    @Column(length = 64)
    private String providerId;
    
    // Risk and compliance
    @Column(precision = 5, scale = 4)
    private Double riskScore;
    
    @Column(length = 16)
    private String riskLevel;
    
    // User and session context
    @Column(length = 64)
    private String userId;
    
    @Column(length = 128)
    private String sessionId;
    
    @Column(length = 128)
    private String correlationId;
    
    @Column(length = 45)
    private String clientIpAddress;
    
    @Column(length = 512)
    private String userAgent;
    
    @Column(length = 128)
    private String deviceFingerprint;
    
    // Additional data as JSON
    @Lob
    @Column(columnDefinition = "TEXT")
    private String metadataJson;
    
    // Audit integrity
    @Column(nullable = false, length = 128)
    private String integrityHash;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant timestamp;
    
    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (eventId == null) {
            eventId = UUID.randomUUID();
        }
    }
}