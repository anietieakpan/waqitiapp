package com.waqiti.rewards.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reward_campaigns",
    indexes = {
        @Index(name = "idx_campaign_active", columnList = "is_active, start_date, end_date"),
        @Index(name = "idx_campaign_merchant", columnList = "merchant_id"),
        @Index(name = "idx_campaign_category", columnList = "merchant_category"),
        @Index(name = "idx_campaign_featured", columnList = "is_featured"),
        @Index(name = "idx_campaign_dates", columnList = "start_date, end_date")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class RewardCampaign {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "name", nullable = false, length = 200)
    private String name;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "target_type", nullable = false, length = 50)
    @Builder.Default
    private String targetType = "ALL"; // ALL, MERCHANT, CATEGORY, USER_SEGMENT
    
    @Column(name = "merchant_id", length = 100)
    private String merchantId;
    
    @Column(name = "merchant_category", length = 100)
    private String merchantCategory;
    
    @Column(name = "user_segment", length = 50)
    private String userSegment; // NEW_USERS, HIGH_VALUE, etc.
    
    @Column(name = "cashback_rate", precision = 5, scale = 4)
    private BigDecimal cashbackRate;
    
    @Column(name = "points_multiplier", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal pointsMultiplier = BigDecimal.ONE;
    
    @Column(name = "bonus_cashback", precision = 15, scale = 2)
    private BigDecimal bonusCashback;
    
    @Column(name = "bonus_points")
    private Long bonusPoints;
    
    @Column(name = "min_transaction_amount", precision = 15, scale = 2)
    private BigDecimal minTransactionAmount;
    
    @Column(name = "max_cashback_per_transaction", precision = 15, scale = 2)
    private BigDecimal maxCashbackPerTransaction;
    
    @Column(name = "max_cashback_total", precision = 15, scale = 2)
    private BigDecimal maxCashbackTotal;
    
    @Column(name = "max_participants")
    private Integer maxParticipants;
    
    @Column(name = "current_participants")
    @Builder.Default
    private Integer currentParticipants = 0;
    
    @Column(name = "min_tier_level")
    @Builder.Default
    private Integer minTierLevel = 0;
    
    @Column(name = "start_date", nullable = false)
    private Instant startDate;
    
    @Column(name = "end_date", nullable = false)
    private Instant endDate;
    
    @Column(name = "terms_and_conditions", columnDefinition = "TEXT")
    private String termsAndConditions;
    
    @Column(name = "image_url")
    private String imageUrl;
    
    @Column(name = "banner_url")
    private String bannerUrl;
    
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;
    
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
    
    @Column(name = "is_featured", nullable = false)
    @Builder.Default
    private Boolean isFeatured = false;
    
    @Column(name = "is_auto_apply", nullable = false)
    @Builder.Default
    private Boolean isAutoApply = true;
    
    @Column(name = "requires_opt_in", nullable = false)
    @Builder.Default
    private Boolean requiresOptIn = false;
    
    @Column(name = "campaign_code", length = 50)
    private String campaignCode;
    
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
    public boolean isActiveNow() {
        Instant now = Instant.now();
        return isActive && startDate.isBefore(now) && endDate.isAfter(now);
    }
    
    public boolean canParticipate() {
        return maxParticipants == null || currentParticipants < maxParticipants;
    }
    
    public boolean isEligibleForTier(int tierLevel) {
        return tierLevel >= minTierLevel;
    }
    
    public boolean isApplicableToMerchant(String merchantId) {
        return "ALL".equals(targetType) || 
               ("MERCHANT".equals(targetType) && merchantId != null && merchantId.equals(this.merchantId));
    }
    
    public boolean isApplicableToCategory(String category) {
        return "ALL".equals(targetType) || 
               ("CATEGORY".equals(targetType) && category != null && category.equals(this.merchantCategory));
    }
    
    public void incrementParticipants() {
        if (currentParticipants == null) {
            currentParticipants = 0;
        }
        currentParticipants++;
    }
}