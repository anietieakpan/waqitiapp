package com.waqiti.gamification.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "badges", indexes = {
    @Index(name = "idx_badge_category", columnList = "category"),
    @Index(name = "idx_badge_tier", columnList = "tier"),
    @Index(name = "idx_badge_active", columnList = "is_active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Badge {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "badge_code", nullable = false, unique = true, length = 50)
    private String badgeCode;
    
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "category", nullable = false)
    @Enumerated(EnumType.STRING)
    private BadgeCategory category;
    
    @Column(name = "tier", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private BadgeTier tier = BadgeTier.BRONZE;
    
    @Column(name = "icon_url", length = 500)
    private String iconUrl;
    
    @Column(name = "points_reward", nullable = false)
    @Builder.Default
    private Long pointsReward = 0L;
    
    @Column(name = "requirement_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private RequirementType requirementType;
    
    @Column(name = "requirement_value", nullable = false)
    private Long requirementValue;
    
    @Column(name = "requirement_description", columnDefinition = "TEXT")
    private String requirementDescription;
    
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
    
    @Column(name = "is_secret", nullable = false)
    @Builder.Default
    private Boolean isSecret = false;
    
    @Column(name = "display_order")
    private Integer displayOrder;
    
    @Column(name = "valid_from")
    private LocalDateTime validFrom;
    
    @Column(name = "valid_until")
    private LocalDateTime validUntil;
    
    @OneToMany(mappedBy = "badge", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<UserBadge> userBadges = new HashSet<>();
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public enum BadgeCategory {
        TRANSACTION,
        SOCIAL,
        SAVINGS,
        INVESTMENT,
        LOYALTY,
        ACHIEVEMENT,
        SPECIAL,
        SECURITY,
        EDUCATION,
        COMMUNITY
    }
    
    public enum BadgeTier {
        BRONZE(1),
        SILVER(2),
        GOLD(3),
        PLATINUM(4),
        DIAMOND(5),
        LEGENDARY(6);
        
        private final int level;
        
        BadgeTier(int level) {
            this.level = level;
        }
        
        public int getLevel() { return level; }
    }
    
    public enum RequirementType {
        TRANSACTION_COUNT,
        TRANSACTION_VOLUME,
        REFERRAL_COUNT,
        SAVINGS_AMOUNT,
        INVESTMENT_COUNT,
        STREAK_DAYS,
        POINTS_EARNED,
        CHALLENGES_COMPLETED,
        ACCOUNT_AGE_DAYS,
        SPECIFIC_ACTION,
        MANUAL_AWARD
    }
}