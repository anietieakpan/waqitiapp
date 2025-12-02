package com.waqiti.reconciliation.domain;

import com.waqiti.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "settlement_instructions", indexes = {
    @Index(name = "idx_settlement_date", columnList = "settlementDate"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_settlement_type", columnList = "settlementType"),
    @Index(name = "idx_counterparty", columnList = "counterparty"),
    @Index(name = "idx_currency", columnList = "currency"),
    @Index(name = "idx_reference_number", columnList = "referenceNumber")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SettlementInstruction extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "reference_number", nullable = false, unique = true, length = 50)
    private String referenceNumber;
    
    @Column(name = "settlement_date", nullable = false)
    private LocalDateTime settlementDate;
    
    @Column(name = "value_date", nullable = false)
    private LocalDateTime valueDate;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_type", nullable = false)
    private SettlementType settlementType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private SettlementDirection direction;
    
    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;
    
    @Column(name = "currency", length = 3, nullable = false)
    private String currency;
    
    @Column(name = "counterparty", nullable = false, length = 255)
    private String counterparty;
    
    @Column(name = "counterparty_account", length = 100)
    private String counterpartyAccount;
    
    @Column(name = "our_account", nullable = false, length = 100)
    private String ourAccount;
    
    @Column(name = "correspondent_bank", length = 255)
    private String correspondentBank;
    
    @Column(name = "correspondent_account", length = 100)
    private String correspondentAccount;
    
    @Column(name = "swift_code", length = 11)
    private String swiftCode;
    
    @Column(name = "routing_number", length = 20)
    private String routingNumber;
    
    @Column(name = "iban", length = 34)
    private String iban;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "purpose_code", length = 10)
    private String purposeCode;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SettlementStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private SettlementPriority priority = SettlementPriority.NORMAL;
    
    @Column(name = "initiated_by", length = 100)
    private String initiatedBy;
    
    @Column(name = "initiated_at")
    private LocalDateTime initiatedAt;
    
    @Column(name = "approved_by", length = 100)
    private String approvedBy;
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    
    @Column(name = "sent_to_network_at")
    private LocalDateTime sentToNetworkAt;
    
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;
    
    @Column(name = "failed_at")
    private LocalDateTime failedAt;
    
    @Column(name = "settled_at")
    private LocalDateTime settledAt;
    
    @Column(name = "network_reference", length = 100)
    private String networkReference;
    
    @Column(name = "confirmation_number", length = 100)
    private String confirmationNumber;
    
    @Column(name = "error_code", length = 20)
    private String errorCode;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "retry_count")
    private Integer retryCount = 0;
    
    @Column(name = "max_retries")
    private Integer maxRetries = 3;
    
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;
    
    @Column(name = "fee_amount", precision = 19, scale = 4)
    private BigDecimal feeAmount;
    
    @Column(name = "fee_currency", length = 3)
    private String feeCurrency;
    
    @Column(name = "exchange_rate", precision = 19, scale = 8)
    private BigDecimal exchangeRate;
    
    @Column(name = "settled_amount", precision = 19, scale = 4)
    private BigDecimal settledAmount;
    
    @Column(name = "settled_currency", length = 3)
    private String settledCurrency;
    
    @Column(name = "reconciliation_run_id")
    private UUID reconciliationRunId;
    
    @Column(name = "is_recurring", nullable = false)
    private Boolean isRecurring = false;
    
    @Column(name = "parent_instruction_id")
    private UUID parentInstructionId;
    
    @ElementCollection
    @CollectionTable(name = "settlement_instruction_metadata", 
                     joinColumns = @JoinColumn(name = "instruction_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value", columnDefinition = "TEXT")
    private Map<String, String> metadata;
    
    @ElementCollection
    @CollectionTable(name = "settlement_instruction_regulatory", 
                     joinColumns = @JoinColumn(name = "instruction_id"))
    @MapKeyColumn(name = "regulatory_key")
    @Column(name = "regulatory_value", columnDefinition = "TEXT")
    private Map<String, String> regulatoryInfo;
    
    public enum SettlementType {
        NOSTRO_FUNDING,
        NOSTRO_DEFUNDING,
        INTERBANK_TRANSFER,
        CUSTOMER_PAYMENT,
        LIQUIDITY_TRANSFER,
        FX_SETTLEMENT,
        CLEARING_SETTLEMENT,
        REGULATORY_REPORTING,
        CORRESPONDENT_PAYMENT,
        INTERNAL_TRANSFER,
        EMERGENCY_FUNDING,
        END_OF_DAY_SWEEP
    }
    
    public enum SettlementDirection {
        INCOMING,
        OUTGOING,
        INTERNAL
    }
    
    public enum SettlementStatus {
        PENDING,
        APPROVED,
        REJECTED,
        SENT,
        ACKNOWLEDGED,
        CONFIRMED,
        SETTLED,
        FAILED,
        CANCELLED,
        TIMEOUT,
        INVESTIGATING,
        REVERSED
    }
    
    public enum SettlementPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT,
        EMERGENCY
    }
    
    public boolean isCompleted() {
        return status == SettlementStatus.SETTLED || 
               status == SettlementStatus.CONFIRMED;
    }
    
    public boolean isFailed() {
        return status == SettlementStatus.FAILED || 
               status == SettlementStatus.REJECTED ||
               status == SettlementStatus.TIMEOUT;
    }
    
    public boolean canRetry() {
        return isFailed() && 
               retryCount < maxRetries &&
               (nextRetryAt == null || LocalDateTime.now().isAfter(nextRetryAt));
    }
    
    public boolean requiresApproval() {
        return amount.compareTo(getApprovalThreshold()) > 0 ||
               priority == SettlementPriority.URGENT ||
               priority == SettlementPriority.EMERGENCY;
    }
    
    private BigDecimal getApprovalThreshold() {
        // This would typically come from configuration
        return new BigDecimal("100000.00");
    }
    
    public void incrementRetryCount() {
        this.retryCount = (this.retryCount != null ? this.retryCount : 0) + 1;
        
        // Exponential backoff: 2^retry_count minutes
        int backoffMinutes = (int) Math.pow(2, this.retryCount);
        this.nextRetryAt = LocalDateTime.now().plusMinutes(backoffMinutes);
    }
    
    public boolean isOverdue() {
        if (settlementDate == null) return false;
        
        return LocalDateTime.now().isAfter(settlementDate.plusHours(1)) && 
               !isCompleted() && !isFailed();
    }
    
    public long getProcessingTimeMinutes() {
        if (initiatedAt == null) return 0;
        
        LocalDateTime endTime = settledAt != null ? settledAt : LocalDateTime.now();
        return java.time.Duration.between(initiatedAt, endTime).toMinutes();
    }
    
    public BigDecimal getTotalAmount() {
        BigDecimal total = amount;
        if (feeAmount != null && 
            (feeCurrency == null || feeCurrency.equals(currency))) {
            total = total.add(feeAmount);
        }
        return total;
    }
}