package com.waqiti.gamification.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_badges", 
    indexes = {
        @Index(name = "idx_user_badge_user_id", columnList = "user_id"),
        @Index(name = "idx_user_badge_earned_at", columnList = "earned_at"),
        @Index(name = "idx_user_badge_displayed", columnList = "is_displayed")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_badge", columnNames = {"user_id", "badge_id"})
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBadge {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "badge_id", nullable = false)
    private Badge badge;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_points_id")
    private UserPoints userPoints;
    
    @Column(name = "earned_at", nullable = false)
    private LocalDateTime earnedAt;
    
    @Column(name = "progress_percentage", nullable = false)
    @Builder.Default
    private Integer progressPercentage = 100;
    
    @Column(name = "is_displayed", nullable = false)
    @Builder.Default
    private Boolean isDisplayed = false;
    
    @Column(name = "display_position")
    private Integer displayPosition;
    
    @Column(name = "notification_sent", nullable = false)
    @Builder.Default
    private Boolean notificationSent = false;
    
    @Column(name = "shared_on_social", nullable = false)
    @Builder.Default
    private Boolean sharedOnSocial = false;
    
    @Column(name = "trigger_event", length = 100)
    private String triggerEvent;
    
    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}