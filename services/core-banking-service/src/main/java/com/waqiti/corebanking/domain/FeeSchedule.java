package com.waqiti.corebanking.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Fee Schedule Entity
 * 
 * Defines fee structures that can be applied to accounts and transactions.
 * Supports various fee types including flat fees, percentage-based fees,
 * tiered fees, and conditional fees.
 */
@Entity
@Table(name = "fee_schedules", indexes = {
    @Index(name = "idx_fee_schedule_name", columnList = "name"),
    @Index(name = "idx_fee_schedule_status", columnList = "status"),
    @Index(name = "idx_fee_schedule_type", columnList = "feeType")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false)
    private FeeType feeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_method", nullable = false)
    private CalculationMethod calculationMethod;

    @Column(name = "base_amount", precision = 19, scale = 4)
    private BigDecimal baseAmount;

    @Column(name = "percentage_rate", precision = 8, scale = 6)
    private BigDecimal percentageRate;

    @Column(name = "minimum_fee", precision = 19, scale = 4)
    private BigDecimal minimumFee;

    @Column(name = "maximum_fee", precision = 19, scale = 4)
    private BigDecimal maximumFee;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "effective_date", nullable = false)
    private LocalDateTime effectiveDate;

    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FeeScheduleStatus status;

    @Column(name = "applies_to_account_types", columnDefinition = "TEXT")
    private String appliesToAccountTypes; // JSON array of account types

    @Column(name = "applies_to_transaction_types", columnDefinition = "TEXT")
    private String appliesToTransactionTypes; // JSON array of transaction types

    @Column(name = "conditions", columnDefinition = "TEXT")
    private String conditions; // JSON for conditional fee logic

    @Column(name = "free_transactions_per_period")
    private Integer freeTransactionsPerPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type")
    private PeriodType periodType;

    @Column(name = "waiver_conditions", columnDefinition = "TEXT")
    private String waiverConditions; // JSON for fee waiver conditions

    @OneToMany(mappedBy = "feeSchedule", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<FeeTier> feeTiers;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    // Business methods

    /**
     * Checks if fee schedule is currently active
     */
    public boolean isActive() {
        return status == FeeScheduleStatus.ACTIVE &&
               effectiveDate.isBefore(LocalDateTime.now()) &&
               (expiryDate == null || expiryDate.isAfter(LocalDateTime.now()));
    }

    /**
     * Checks if fee schedule applies to specific account type
     */
    public boolean appliesToAccountType(Account.AccountType accountType) {
        if (appliesToAccountTypes == null || appliesToAccountTypes.isEmpty()) {
            return true; // Apply to all if not specified
        }
        return appliesToAccountTypes.contains(accountType.toString());
    }

    /**
     * Checks if fee schedule applies to specific transaction type
     */
    public boolean appliesToTransactionType(Transaction.TransactionType transactionType) {
        if (appliesToTransactionTypes == null || appliesToTransactionTypes.isEmpty()) {
            return true; // Apply to all if not specified
        }
        return appliesToTransactionTypes.contains(transactionType.toString());
    }

    // Enums

    public enum FeeType {
        TRANSACTION_FEE,       // Fee charged per transaction
        MAINTENANCE_FEE,       // Monthly/periodic account maintenance
        OVERDRAFT_FEE,         // Fee for overdraft usage
        ATM_FEE,              // ATM usage fee
        WIRE_TRANSFER_FEE,     // Wire transfer fee
        INTERNATIONAL_FEE,     // International transaction fee
        CARD_FEE,             // Card-related fees
        SERVICE_FEE,          // General service fees
        PENALTY_FEE,          // Penalty fees
        CUSTOM_FEE            // Custom fee types
    }

    public enum CalculationMethod {
        FLAT_FEE,             // Fixed amount
        PERCENTAGE,           // Percentage of transaction amount
        TIERED,               // Different rates for different tiers
        FLAT_PLUS_PERCENTAGE, // Fixed amount plus percentage
        MIN_OF_FLAT_OR_PERCENTAGE, // Minimum of flat or percentage
        MAX_OF_FLAT_OR_PERCENTAGE  // Maximum of flat or percentage
    }

    public enum FeeScheduleStatus {
        DRAFT,                // Under development
        ACTIVE,               // Currently active
        INACTIVE,             // Temporarily disabled
        EXPIRED,              // Past expiry date
        SUPERSEDED            // Replaced by newer version
    }

    public enum PeriodType {
        DAILY,
        WEEKLY, 
        MONTHLY,
        QUARTERLY,
        ANNUALLY
    }
}