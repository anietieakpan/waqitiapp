package com.waqiti.rewards.domain;

import com.waqiti.rewards.enums.MerchantStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant_rewards",
    indexes = {
        @Index(name = "idx_merchant_rewards_merchant_id", columnList = "merchant_id", unique = true),
        @Index(name = "idx_merchant_rewards_category", columnList = "category"),
        @Index(name = "idx_merchant_rewards_status", columnList = "status"),
        @Index(name = "idx_merchant_rewards_featured", columnList = "is_featured"),
        @Index(name = "idx_merchant_rewards_rate", columnList = "cashback_rate DESC")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class MerchantRewards {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "merchant_id", nullable = false, unique = true, length = 100)
    private String merchantId;
    
    @Column(name = "merchant_name", nullable = false, length = 255)
    private String merchantName;
    
    @Column(name = "category", nullable = false, length = 100)
    private String category;
    
    @Column(name = "mcc", length = 10)
    private String mcc; // Merchant Category Code
    
    @Column(name = "cashback_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal cashbackRate;
    
    @Column(name = "points_multiplier", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal pointsMultiplier = BigDecimal.ONE;
    
    @Column(name = "max_cashback_per_transaction", precision = 15, scale = 2)
    private BigDecimal maxCashbackPerTransaction;
    
    @Column(name = "max_cashback_per_month", precision = 15, scale = 2)
    private BigDecimal maxCashbackPerMonth;
    
    @Column(name = "min_transaction_amount", precision = 15, scale = 2)
    private BigDecimal minTransactionAmount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private MerchantStatus status = MerchantStatus.ACTIVE;
    
    @Column(name = "is_featured", nullable = false)
    @Builder.Default
    private Boolean isFeatured = false;
    
    @Column(name = "has_active_promotion", nullable = false)
    @Builder.Default
    private Boolean hasActivePromotion = false;
    
    @Column(name = "promotion_description")
    private String promotionDescription;
    
    @Column(name = "promotion_start_date")
    private Instant promotionStartDate;
    
    @Column(name = "promotion_end_date")
    private Instant promotionEndDate;
    
    @Column(name = "promotion_cashback_rate", precision = 5, scale = 4)
    private BigDecimal promotionCashbackRate;
    
    @Column(name = "logo_url")
    private String logoUrl;
    
    @Column(name = "website_url")
    private String websiteUrl;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "terms_and_conditions", columnDefinition = "TEXT")
    private String termsAndConditions;
    
    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;
    
    @Column(name = "total_transactions")
    @Builder.Default
    private Long totalTransactions = 0L;
    
    @Column(name = "total_cashback_paid", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalCashbackPaid = BigDecimal.ZERO;
    
    @Column(name = "last_transaction_date")
    private Instant lastTransactionDate;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    // Business methods
    public BigDecimal getEffectiveCashbackRate() {
        if (hasActivePromotion && isPromotionActive() && promotionCashbackRate != null) {
            return promotionCashbackRate;
        }
        return cashbackRate;
    }
    
    public boolean isPromotionActive() {
        if (!hasActivePromotion || promotionEndDate == null) {
            return false;
        }
        
        Instant now = Instant.now();
        return (promotionStartDate == null || promotionStartDate.isBefore(now)) &&
               promotionEndDate.isAfter(now);
    }
    
    public boolean isEligibleTransaction(BigDecimal transactionAmount) {
        if (status != MerchantStatus.ACTIVE) {
            return false;
        }
        
        if (minTransactionAmount != null && 
            transactionAmount.compareTo(minTransactionAmount) < 0) {
            return false;
        }
        
        return true;
    }
    
    public BigDecimal calculateCashback(BigDecimal transactionAmount) {
        if (!isEligibleTransaction(transactionAmount)) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal rate = getEffectiveCashbackRate();
        BigDecimal cashback = transactionAmount.multiply(rate);
        
        // Apply per-transaction limit
        if (maxCashbackPerTransaction != null && 
            cashback.compareTo(maxCashbackPerTransaction) > 0) {
            cashback = maxCashbackPerTransaction;
        }
        
        return cashback;
    }
    
    public void recordTransaction(BigDecimal transactionAmount, BigDecimal cashbackAmount) {
        totalTransactions++;
        totalCashbackPaid = totalCashbackPaid.add(cashbackAmount);
        lastTransactionDate = Instant.now();
    }
    
    public boolean hasMonthlyLimit() {
        return maxCashbackPerMonth != null;
    }
    
    public void activatePromotion(String description, BigDecimal promotionRate,
                                Instant startDate, Instant endDate) {
        this.hasActivePromotion = true;
        this.promotionDescription = description;
        this.promotionCashbackRate = promotionRate;
        this.promotionStartDate = startDate;
        this.promotionEndDate = endDate;
    }
    
    public void deactivatePromotion() {
        this.hasActivePromotion = false;
        this.promotionDescription = null;
        this.promotionCashbackRate = null;
        this.promotionStartDate = null;
        this.promotionEndDate = null;
    }
}