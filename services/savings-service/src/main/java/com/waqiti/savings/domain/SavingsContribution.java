package com.waqiti.savings.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "savings_contributions", indexes = {
        @Index(name = "idx_savings_contributions_goal", columnList = "goal_id"),
        @Index(name = "idx_savings_contributions_user", columnList = "user_id"),
        @Index(name = "idx_savings_contributions_type", columnList = "type"),
        @Index(name = "idx_savings_contributions_status", columnList = "status"),
        @Index(name = "idx_savings_contributions_created", columnList = "created_at"),
        @Index(name = "idx_savings_contributions_auto_save", columnList = "is_auto_save")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavingsContribution {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "goal_id", nullable = false)
    private UUID goalId;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(name = "type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ContributionType type;
    
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ContributionStatus status = ContributionStatus.PENDING;
    
    @Column(name = "payment_method", length = 30)
    @Enumerated(EnumType.STRING)
    private ContributionPaymentMethod paymentMethod;
    
    @Column(name = "transaction_id", length = 100)
    private String transactionId;
    
    @Column(name = "source", length = 50)
    private String source;
    
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
    
    @Column(name = "is_auto_save")
    private Boolean isAutoSave = false;
    
    @Column(name = "auto_save_rule_id")
    private UUID autoSaveRuleId;
    
    // For withdrawals
    @Column(name = "withdrawal_reason", length = 100)
    private String withdrawalReason;
    
    @Column(name = "destination_account", length = 100)
    private String destinationAccount;
    
    // Processing details
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    
    @Column(name = "failed_at")
    private LocalDateTime failedAt;
    
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;
    
    @Column(name = "retry_count")
    private Integer retryCount = 0;
    
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;
    
    // Metadata
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Audit: User who created this contribution
     */
    @org.springframework.data.annotation.CreatedBy
    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    /**
     * Audit: User who last modified this contribution
     */
    @org.springframework.data.annotation.LastModifiedBy
    @Column(name = "modified_by", length = 100)
    private String modifiedBy;

    /**
     * Version for optimistic locking
     * CRITICAL: Prevents concurrent updates to contribution status
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Business logic methods
    public boolean isDeposit() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }
    
    public boolean isWithdrawal() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }
    
    public boolean isPending() {
        return status == ContributionStatus.PENDING;
    }

    public boolean isCompleted() {
        return status == ContributionStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == ContributionStatus.FAILED;
    }

    public boolean canRetry() {
        return status == ContributionStatus.FAILED && retryCount < 3;
    }
    
    public BigDecimal getAbsoluteAmount() {
        return amount.abs();
    }
    
    // Enums
    public enum ContributionType {
        MANUAL,
        AUTO_SAVE,
        ROUND_UP,
        SPARE_CHANGE,
        SCHEDULED,
        BONUS,
        INTEREST,
        WITHDRAWAL,
        TRANSFER_IN,
        TRANSFER_OUT
    }

    public enum ContributionStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED,
        REVERSED
    }

    public enum ContributionSource {
        WALLET,
        BANK_TRANSFER,
        CARD,
        CASH,
        AUTO_SAVE,
        INTEREST,
        BONUS,
        OTHER
    }

    public enum ContributionPaymentMethod {
        BANK_ACCOUNT,
        DEBIT_CARD,
        CREDIT_CARD,
        DIGITAL_WALLET,
        BALANCE,
        CASH
    }
}