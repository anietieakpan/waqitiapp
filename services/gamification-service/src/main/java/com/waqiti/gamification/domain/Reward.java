package com.waqiti.gamification.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "rewards", indexes = {
    @Index(name = "idx_reward_type", columnList = "reward_type"),
    @Index(name = "idx_reward_status", columnList = "status"),
    @Index(name = "idx_reward_points_cost", columnList = "points_cost"),
    @Index(name = "idx_reward_category", columnList = "category")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reward {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "reward_code", nullable = false, unique = true, length = 50)
    private String rewardCode;
    
    @Column(name = "name", nullable = false, length = 200)
    private String name;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "reward_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private RewardType rewardType;
    
    @Column(name = "category", nullable = false)
    @Enumerated(EnumType.STRING)
    private RewardCategory category;
    
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RewardStatus status = RewardStatus.ACTIVE;
    
    @Column(name = "points_cost", nullable = false)
    private Long pointsCost;
    
    @Column(name = "cash_value", precision = 19, scale = 2)
    private BigDecimal cashValue;
    
    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage;
    
    @Column(name = "brand_name", length = 100)
    private String brandName;
    
    @Column(name = "brand_logo_url", length = 500)
    private String brandLogoUrl;
    
    @Column(name = "image_url", length = 500)
    private String imageUrl;
    
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;
    
    @Column(name = "total_quantity")
    private Integer totalQuantity;
    
    @Column(name = "available_quantity", nullable = false)
    @Builder.Default
    private Integer availableQuantity = 0;
    
    @Column(name = "redeemed_quantity", nullable = false)
    @Builder.Default
    private Integer redeemedQuantity = 0;
    
    @Column(name = "min_level_required")
    @Enumerated(EnumType.STRING)
    private UserPoints.Level minLevelRequired;
    
    @Column(name = "max_redemptions_per_user")
    @Builder.Default
    private Integer maxRedemptionsPerUser = 1;
    
    @Column(name = "is_featured", nullable = false)
    @Builder.Default
    private Boolean isFeatured = false;
    
    @Column(name = "is_limited_time", nullable = false)
    @Builder.Default
    private Boolean isLimitedTime = false;
    
    @Column(name = "valid_from")
    private LocalDateTime validFrom;
    
    @Column(name = "valid_until")
    private LocalDateTime validUntil;
    
    @Column(name = "redemption_instructions", columnDefinition = "TEXT")
    private String redemptionInstructions;
    
    @Column(name = "terms_conditions", columnDefinition = "TEXT")
    private String termsConditions;
    
    @Column(name = "provider_name", length = 100)
    private String providerName;
    
    @Column(name = "provider_contact", length = 200)
    private String providerContact;
    
    @Column(name = "external_id", length = 100)
    private String externalId;
    
    @Column(name = "external_url", length = 500)
    private String externalUrl;
    
    @Column(name = "delivery_method", length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DeliveryMethod deliveryMethod = DeliveryMethod.DIGITAL;
    
    @Column(name = "estimated_delivery_days")
    private Integer estimatedDeliveryDays;
    
    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;
    
    @Column(name = "popularity_score", nullable = false)
    @Builder.Default
    private Long popularityScore = 0L;
    
    @Column(name = "rating", precision = 3, scale = 2)
    private BigDecimal rating;
    
    @Column(name = "review_count", nullable = false)
    @Builder.Default
    private Integer reviewCount = 0;
    
    @OneToMany(mappedBy = "reward", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<RewardRedemption> redemptions = new HashSet<>();
    
    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    public enum RewardType {
        CASHBACK,
        GIFT_CARD,
        MERCHANDISE,
        DISCOUNT_COUPON,
        SERVICE_VOUCHER,
        CHARITY_DONATION,
        EXPERIENCE,
        FEATURE_UNLOCK,
        SUBSCRIPTION,
        PHYSICAL_PRODUCT
    }
    
    public enum RewardCategory {
        SHOPPING,
        DINING,
        ENTERTAINMENT,
        TRAVEL,
        TECHNOLOGY,
        FASHION,
        HEALTH_FITNESS,
        EDUCATION,
        CHARITY,
        FINANCIAL_SERVICES,
        LIFESTYLE,
        AUTOMOTIVE,
        HOME_GARDEN,
        SPORTS,
        GAMING
    }
    
    public enum RewardStatus {
        DRAFT,
        ACTIVE,
        INACTIVE,
        OUT_OF_STOCK,
        EXPIRED,
        DISCONTINUED
    }
    
    public enum DeliveryMethod {
        DIGITAL,
        EMAIL,
        SMS,
        PHYSICAL_MAIL,
        IN_APP,
        PICKUP,
        DIRECT_DEPOSIT
    }
    
    public boolean isAvailable() {
        return status == RewardStatus.ACTIVE && 
               availableQuantity > 0 && 
               (validUntil == null || validUntil.isAfter(LocalDateTime.now()));
    }
    
    public boolean isExpired() {
        return validUntil != null && validUntil.isBefore(LocalDateTime.now());
    }
    
    public boolean hasStock() {
        return totalQuantity == null || availableQuantity > 0;
    }
}