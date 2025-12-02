package com.waqiti.rewards.domain;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Referral Milestone Achievement Entity
 *
 * Records when a user achieves a milestone
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Entity
@Table(name = "referral_milestone_achievements", indexes = {
    @Index(name = "idx_milestone_achievements_milestone", columnList = "milestone_id"),
    @Index(name = "idx_milestone_achievements_user", columnList = "userId"),
    @Index(name = "idx_milestone_achievements_achieved", columnList = "achievedAt"),
    @Index(name = "idx_milestone_achievements_reward", columnList = "rewardId")
})
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = "milestone")
public class ReferralMilestoneAchievement {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Achievement ID is required")
    @Column(unique = true, nullable = false, length = 100)
    private String achievementId;

    @NotNull(message = "Milestone is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "milestone_id", referencedColumnName = "milestoneId", nullable = false)
    private ReferralMilestone milestone;

    @NotNull(message = "User ID is required")
    @Column(nullable = false)
    private UUID userId;

    // ============================================================================
    // ACHIEVEMENT DETAILS
    // ============================================================================

    @NotNull(message = "Achievement timestamp is required")
    @Column(nullable = false)
    private LocalDateTime achievedAt;

    /**
     * Snapshot of the criteria that was met at the time of achievement
     * Example: {"referrals": 50, "conversions": 25, "revenue": "5000.00"}
     */
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> achievementData = new HashMap<>();

    // ============================================================================
    // REWARD STATUS
    // ============================================================================

    @Builder.Default
    @Column(nullable = false)
    private Boolean rewardIssued = false;

    @Size(max = 100, message = "Reward ID must not exceed 100 characters")
    @Column(length = 100)
    private String rewardId;

    @Column
    private LocalDateTime rewardIssuedAt;

    // ============================================================================
    // NOTIFICATION STATUS
    // ============================================================================

    @Builder.Default
    @Column(nullable = false)
    private Boolean userNotified = false;

    @Column
    private LocalDateTime notifiedAt;

    // ============================================================================
    // AUDIT FIELDS
    // ============================================================================

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ============================================================================
    // LIFECYCLE CALLBACKS
    // ============================================================================

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (achievedAt == null) {
            achievedAt = LocalDateTime.now();
        }
    }

    // ============================================================================
    // BUSINESS LOGIC METHODS
    // ============================================================================

    /**
     * Marks the reward as issued
     */
    public void markRewardIssued(String rewardId) {
        this.rewardIssued = true;
        this.rewardId = rewardId;
        this.rewardIssuedAt = LocalDateTime.now();
    }

    /**
     * Marks the user as notified
     */
    public void markUserNotified() {
        this.userNotified = true;
        this.notifiedAt = LocalDateTime.now();
    }

    /**
     * Adds achievement data
     */
    public void addAchievementData(String key, Object value) {
        if (this.achievementData == null) {
            this.achievementData = new HashMap<>();
        }
        this.achievementData.put(key, value);
    }

    /**
     * Gets achievement data value
     */
    public Object getAchievementDataValue(String key) {
        if (this.achievementData == null) {
            return null;
        }
        return this.achievementData.get(key);
    }

    /**
     * Checks if the reward is pending issuance
     */
    public boolean isRewardPending() {
        return !rewardIssued;
    }

    /**
     * Checks if the user needs to be notified
     */
    public boolean needsNotification() {
        return !userNotified;
    }

    /**
     * Gets the time elapsed since achievement
     */
    public long getHoursSinceAchievement() {
        return java.time.Duration.between(achievedAt, LocalDateTime.now()).toHours();
    }

    /**
     * Gets the time elapsed since reward was issued
     */
    public Long getHoursSinceRewardIssued() {
        if (rewardIssuedAt == null) {
            return null;
        }
        return java.time.Duration.between(rewardIssuedAt, LocalDateTime.now()).toHours();
    }
}
