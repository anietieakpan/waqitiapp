/**
 * Rewards Tier Entity
 * Defines the different loyalty tiers and their benefits
 */
package com.waqiti.rewards.entity;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rewards_tiers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RewardsTier {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "tier_name", nullable = false, unique = true)
    private String tierName;
    
    @Column(name = "tier_level", nullable = false, unique = true)
    private Integer tierLevel;
    
    // Requirements
    @Column(name = "points_required", nullable = false)
    private Long pointsRequired;
    
    @Column(name = "transactions_required")
    private Integer transactionsRequired;
    
    // Benefits
    @Column(name = "points_multiplier", nullable = false, precision = 3, scale = 2)
    private BigDecimal pointsMultiplier;
    
    @Column(name = "cashback_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal cashbackRate;
    
    // Perks
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private JsonNode perks;
    
    // Display
    @Column(name = "color_code")
    private String colorCode;
    
    @Column(name = "icon_url", columnDefinition = "TEXT")
    private String iconUrl;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
    
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (isActive == null) {
            isActive = true;
        }
        if (transactionsRequired == null) {
            transactionsRequired = 0;
        }
        if (pointsMultiplier == null) {
            pointsMultiplier = BigDecimal.ONE;
        }
        if (cashbackRate == null) {
            cashbackRate = new BigDecimal("0.0100"); // 1% default
        }
    }
    
    public boolean isBaseTier() {
        return tierLevel == 0;
    }
    
    public boolean isTopTier() {
        // This would be determined dynamically in production
        return tierLevel >= 3;
    }
    
    public BigDecimal calculatePointsEarned(BigDecimal transactionAmount) {
        // Base rate: 1 point per dollar
        BigDecimal basePoints = transactionAmount.setScale(0, RoundingMode.DOWN);
        return basePoints.multiply(pointsMultiplier).setScale(0, RoundingMode.DOWN);
    }

    public BigDecimal calculateCashbackEarned(BigDecimal transactionAmount) {
        return transactionAmount.multiply(cashbackRate).setScale(2, RoundingMode.HALF_UP);
    }
    
    public boolean hasPerk(String perkName) {
        if (perks == null || !perks.has(perkName)) {
            return false;
        }
        JsonNode perkValue = perks.get(perkName);
        if (perkValue.isBoolean()) {
            return perkValue.asBoolean();
        }
        return !perkValue.asText().equals("false") && !perkValue.asText().equals("0");
    }
    
    public String getPerkValue(String perkName) {
        if (perks == null || !perks.has(perkName)) {
            return null;
        }
        return perks.get(perkName).asText();
    }
}