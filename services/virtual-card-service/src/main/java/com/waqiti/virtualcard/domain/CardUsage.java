package com.waqiti.virtualcard.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Card Usage Entity
 * Tracks daily usage statistics for virtual cards
 */
@Entity
@Table(name = "card_usage", 
    indexes = {
        @Index(name = "idx_card_usage_date", columnList = "card_id, usage_date", unique = true),
        @Index(name = "idx_usage_date", columnList = "usage_date"),
        @Index(name = "idx_card_id", columnList = "card_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CardUsage {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @EqualsAndHashCode.Include
    private String id;
    
    @Column(name = "card_id", nullable = false)
    private String cardId;
    
    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;
    
    @Column(name = "transaction_count", nullable = false)
    private Integer transactionCount;
    
    @Column(name = "total_spent", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalSpent;
    
    @Column(name = "online_transaction_count", nullable = false)
    private Integer onlineTransactionCount;
    
    @Column(name = "international_transaction_count", nullable = false)
    private Integer internationalTransactionCount;
    
    @Column(name = "contactless_transaction_count", nullable = false)
    private Integer contactlessTransactionCount;
    
    @Column(name = "atm_transaction_count", nullable = false)
    private Integer atmTransactionCount;
    
    @Column(name = "chip_transaction_count", nullable = false)
    private Integer chipTransactionCount;
    
    @Column(name = "magnetic_stripe_count", nullable = false)
    private Integer magneticStripeCount;
    
    @Column(name = "manual_entry_count", nullable = false)
    private Integer manualEntryCount;
    
    @Column(name = "recurring_transaction_count", nullable = false)
    private Integer recurringTransactionCount;
    
    @Column(name = "declined_transaction_count", nullable = false)
    private Integer declinedTransactionCount;
    
    @Column(name = "fraud_attempts", nullable = false)
    private Integer fraudAttempts;
    
    @Column(name = "unique_merchant_count", nullable = false)
    private Integer uniqueMerchantCount;
    
    @Column(name = "unique_country_count", nullable = false)
    private Integer uniqueCountryCount;
    
    @Column(name = "average_transaction_amount", precision = 19, scale = 2)
    private BigDecimal averageTransactionAmount;
    
    @Column(name = "largest_transaction_amount", precision = 19, scale = 2)
    private BigDecimal largestTransactionAmount;
    
    @Column(name = "smallest_transaction_amount", precision = 19, scale = 2)
    private BigDecimal smallestTransactionAmount;
    
    @Column(name = "first_transaction_time")
    private LocalDateTime firstTransactionTime;
    
    @Column(name = "last_transaction_time")
    private LocalDateTime lastTransactionTime;
    
    @Column(name = "peak_hour")
    private Integer peakHour;
    
    @Column(name = "velocity_score")
    private Integer velocityScore;
    
    @Column(name = "risk_score")
    private Integer riskScore;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (transactionCount == null) {
            transactionCount = 0;
        }
        if (totalSpent == null) {
            totalSpent = BigDecimal.ZERO;
        }
        if (onlineTransactionCount == null) {
            onlineTransactionCount = 0;
        }
        if (internationalTransactionCount == null) {
            internationalTransactionCount = 0;
        }
        if (contactlessTransactionCount == null) {
            contactlessTransactionCount = 0;
        }
        if (atmTransactionCount == null) {
            atmTransactionCount = 0;
        }
        if (chipTransactionCount == null) {
            chipTransactionCount = 0;
        }
        if (magneticStripeCount == null) {
            magneticStripeCount = 0;
        }
        if (manualEntryCount == null) {
            manualEntryCount = 0;
        }
        if (recurringTransactionCount == null) {
            recurringTransactionCount = 0;
        }
        if (declinedTransactionCount == null) {
            declinedTransactionCount = 0;
        }
        if (fraudAttempts == null) {
            fraudAttempts = 0;
        }
        if (uniqueMerchantCount == null) {
            uniqueMerchantCount = 0;
        }
        if (uniqueCountryCount == null) {
            uniqueCountryCount = 0;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        
        // Calculate average transaction amount
        if (transactionCount > 0 && totalSpent != null) {
            averageTransactionAmount = totalSpent.divide(
                BigDecimal.valueOf(transactionCount), 
                2, 
                java.math.RoundingMode.HALF_UP
            );
        }
    }
    
    /**
     * Check if this is a high-usage day
     */
    public boolean isHighUsageDay(int threshold) {
        return transactionCount >= threshold;
    }
    
    /**
     * Check if this is a high-spending day
     */
    public boolean isHighSpendingDay(BigDecimal threshold) {
        return totalSpent.compareTo(threshold) >= 0;
    }
    
    /**
     * Get usage efficiency (spending per transaction)
     */
    public BigDecimal getUsageEfficiency() {
        if (transactionCount == 0) {
            return BigDecimal.ZERO;
        }
        return totalSpent.divide(BigDecimal.valueOf(transactionCount), 2, java.math.RoundingMode.HALF_UP);
    }
    
    /**
     * Get online transaction percentage
     */
    public double getOnlineTransactionPercentage() {
        if (transactionCount == 0) {
            return 0.0;
        }
        return (onlineTransactionCount.doubleValue() / transactionCount.doubleValue()) * 100.0;
    }
    
    /**
     * Get international transaction percentage
     */
    public double getInternationalTransactionPercentage() {
        if (transactionCount == 0) {
            return 0.0;
        }
        return (internationalTransactionCount.doubleValue() / transactionCount.doubleValue()) * 100.0;
    }
    
    /**
     * Get contactless transaction percentage
     */
    public double getContactlessTransactionPercentage() {
        if (transactionCount == 0) {
            return 0.0;
        }
        return (contactlessTransactionCount.doubleValue() / transactionCount.doubleValue()) * 100.0;
    }
    
    /**
     * Get fraud attempt percentage
     */
    public double getFraudAttemptPercentage() {
        if (transactionCount == 0) {
            return 0.0;
        }
        return (fraudAttempts.doubleValue() / transactionCount.doubleValue()) * 100.0;
    }
    
    /**
     * Get decline rate
     */
    public double getDeclineRate() {
        int totalAttempts = transactionCount + declinedTransactionCount;
        if (totalAttempts == 0) {
            return 0.0;
        }
        return (declinedTransactionCount.doubleValue() / totalAttempts) * 100.0;
    }
}