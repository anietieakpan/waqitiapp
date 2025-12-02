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

@Entity
@Table(name = "user_challenges", 
    indexes = {
        @Index(name = "idx_user_challenge_user_id", columnList = "user_id"),
        @Index(name = "idx_user_challenge_status", columnList = "status"),
        @Index(name = "idx_user_challenge_joined_at", columnList = "joined_at"),
        @Index(name = "idx_user_challenge_completed_at", columnList = "completed_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_challenge", columnNames = {"user_id", "challenge_id"})
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserChallenge {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_id", nullable = false)
    private Challenge challenge;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_points_id")
    private UserPoints userPoints;
    
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserChallengeStatus status = UserChallengeStatus.JOINED;
    
    @Column(name = "progress_value", nullable = false)
    @Builder.Default
    private Long progressValue = 0L;
    
    @Column(name = "progress_percentage", nullable = false)
    @Builder.Default
    private Integer progressPercentage = 0;
    
    @Column(name = "target_value", nullable = false)
    private Long targetValue;
    
    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "failed_at")
    private LocalDateTime failedAt;
    
    @Column(name = "points_earned")
    @Builder.Default
    private Long pointsEarned = 0L;
    
    @Column(name = "bonus_points_earned")
    @Builder.Default
    private Long bonusPointsEarned = 0L;
    
    @Column(name = "cash_earned", precision = 19, scale = 2)
    private BigDecimal cashEarned;
    
    @Column(name = "completion_time_seconds")
    private Long completionTimeSeconds;
    
    @Column(name = "difficulty_multiplier", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal difficultyMultiplier = BigDecimal.ONE;
    
    @Column(name = "rank_position")
    private Integer rankPosition;
    
    @Column(name = "is_featured", nullable = false)
    @Builder.Default
    private Boolean isFeatured = false;
    
    @Column(name = "team_id")
    private String teamId;
    
    @Column(name = "notification_sent", nullable = false)
    @Builder.Default
    private Boolean notificationSent = false;
    
    @Column(name = "shared_on_social", nullable = false)
    @Builder.Default
    private Boolean sharedOnSocial = false;
    
    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;
    
    @Column(name = "failure_reason", length = 500)
    private String failureReason;
    
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
    
    public enum UserChallengeStatus {
        JOINED,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        ABANDONED,
        EXPIRED,
        DISQUALIFIED
    }
    
    public void updateProgress(Long newProgressValue) {
        this.progressValue = newProgressValue;
        this.progressPercentage = Math.min(100, (int) ((newProgressValue * 100) / targetValue));
        this.lastActivityAt = LocalDateTime.now();
        
        if (this.progressPercentage >= 100) {
            this.status = UserChallengeStatus.COMPLETED;
            this.completedAt = LocalDateTime.now();
            this.completionTimeSeconds = java.time.Duration.between(startedAt, completedAt).toSeconds();
        } else if (this.status == UserChallengeStatus.JOINED) {
            this.status = UserChallengeStatus.IN_PROGRESS;
            this.startedAt = LocalDateTime.now();
        }
    }
}