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
@Table(name = "savings_milestones", indexes = {
        @Index(name = "idx_savings_milestones_goal", columnList = "goal_id"),
        @Index(name = "idx_savings_milestones_status", columnList = "status"),
        @Index(name = "idx_savings_milestones_achieved", columnList = "achieved_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Milestone {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "goal_id", nullable = false)
    private UUID goalId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "target_percentage", precision = 5, scale = 2)
    private BigDecimal targetPercentage;
    
    @Column(name = "target_amount", precision = 19, scale = 4)
    private BigDecimal targetAmount;
    
    @Column(name = "target_date")
    private LocalDateTime targetDate;
    
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private MilestoneStatus status = MilestoneStatus.PENDING;
    
    @Column(name = "achieved_at")
    private LocalDateTime achievedAt;
    
    @Column(name = "achievement_amount", precision = 19, scale = 4)
    private BigDecimal achievementAmount;
    
    // Rewards
    @Column(name = "reward_type", length = 30)
    private String rewardType;
    
    @Column(name = "reward_value", length = 200)
    private String rewardValue;
    
    @Column(name = "reward_claimed")
    private Boolean rewardClaimed = false;
    
    @Column(name = "reward_claimed_at")
    private LocalDateTime rewardClaimedAt;
    
    // Visual customization
    @Column(name = "icon", length = 50)
    private String icon;
    
    @Column(name = "color", length = 7)
    private String color;
    
    @Column(name = "badge_url", length = 500)
    private String badgeUrl;
    
    // Notifications
    @Column(name = "notify_on_approach")
    private Boolean notifyOnApproach = true;
    
    @Column(name = "notify_on_achievement")
    private Boolean notifyOnAchievement = true;
    
    @Column(name = "approach_notification_sent")
    private Boolean approachNotificationSent = false;
    
    @Column(name = "achievement_notification_sent")
    private Boolean achievementNotificationSent = false;
    
    // Metadata
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "is_custom")
    private Boolean isCustom = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Audit: User who created this milestone
     */
    @org.springframework.data.annotation.CreatedBy
    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    /**
     * Audit: User who last modified this milestone
     */
    @org.springframework.data.annotation.LastModifiedBy
    @Column(name = "modified_by", length = 100)
    private String modifiedBy;

    /**
     * Version for optimistic locking
     * CRITICAL: Prevents concurrent updates to milestone status
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
    public boolean isPending() {
        return status == MilestoneStatus.PENDING;
    }

    public boolean isAchieved() {
        return status == MilestoneStatus.ACHIEVED;
    }

    public boolean isMissed() {
        return status == MilestoneStatus.MISSED;
    }
    
    public boolean hasReward() {
        return rewardType != null && rewardValue != null;
    }
    
    public boolean canClaimReward() {
        return isAchieved() && hasReward() && !rewardClaimed;
    }
    
    public boolean shouldNotifyApproach(BigDecimal currentProgress) {
        if (!notifyOnApproach || approachNotificationSent) return false;
        
        // Notify when within 10% of milestone
        BigDecimal threshold = targetPercentage.subtract(BigDecimal.valueOf(10));
        return currentProgress.compareTo(threshold) >= 0 && 
               currentProgress.compareTo(targetPercentage) < 0;
    }
    
    public boolean isOverdue() {
        return targetDate != null && 
               LocalDateTime.now().isAfter(targetDate) && 
               !isAchieved();
    }
    
    // Enums
    public enum MilestoneStatus {
        PENDING,
        APPROACHING,
        ACHIEVED,
        MISSED,
        CANCELLED
    }
}