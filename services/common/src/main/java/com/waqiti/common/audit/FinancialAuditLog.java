package com.waqiti.common.audit;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Financial audit log for tracking transaction-related events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialAuditLog {
    
    private UUID id;
    private String transactionId;
    private String userId;
    private String fromUserId;
    private String toUserId;
    private String recipientId;  // Alias for toUserId for compatibility
    private String fromAccount;
    private String toAccount;
    private BigDecimal amount;
    private String currency;
    private TransactionType transactionType;
    private TransactionStatus status;
    private String eventType;
    private String description;
    private LocalDateTime timestamp;
    private String sessionId;
    private String ipAddress;
    private String userAgent;
    private String riskScore;
    private Map<String, Object> metadata;
    private String complianceFlags;
    private String regulatoryData;
    private String sourceService;
    private String correlationId;
    private String severity;
    
    /**
     * Transaction types for audit logging
     */
    public enum TransactionType {
        TRANSFER,
        DEPOSIT,
        WITHDRAWAL,
        PAYMENT,
        REFUND,
        FEE,
        EXCHANGE,
        LOAN_DISBURSEMENT,
        LOAN_REPAYMENT,
        CRYPTO_EXCHANGE,
        SECURITY_EVENT,
        FRAUD_CHECK,
        ACCOUNTING_EVENT,
        JOURNAL_ENTRY,
        LEDGER_ADJUSTMENT
    }
    
    /**
     * Transaction status for audit logging
     */
    public enum TransactionStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED,
        REFUNDED,
        REVERSED,
        BLOCKED
    }
    
    /**
     * Create a financial audit log for a transaction
     */
    public static FinancialAuditLog create(String transactionId, String userId, 
                                         BigDecimal amount, String currency,
                                         TransactionType type, TransactionStatus status,
                                         String description) {
        return FinancialAuditLog.builder()
                .id(UUID.randomUUID())
                .transactionId(transactionId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .transactionType(type)
                .status(status)
                .description(description)
                .timestamp(LocalDateTime.now())
                .severity("INFO")
                .build();
    }
    
    /**
     * Add metadata to the audit log
     */
    public FinancialAuditLog withMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        return this;
    }
    
    /**
     * Set session information
     */
    public FinancialAuditLog withSession(String sessionId, String ipAddress, String userAgent) {
        this.sessionId = sessionId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        return this;
    }
    
    /**
     * Set compliance information
     */
    public FinancialAuditLog withCompliance(String complianceFlags, String regulatoryData) {
        this.complianceFlags = complianceFlags;
        this.regulatoryData = regulatoryData;
        return this;
    }
    
    /**
     * Set risk score
     */
    public FinancialAuditLog withRiskScore(String riskScore) {
        this.riskScore = riskScore;
        return this;
    }
    
    /**
     * Set severity level
     */
    public FinancialAuditLog withSeverity(String severity) {
        this.severity = severity;
        return this;
    }
}