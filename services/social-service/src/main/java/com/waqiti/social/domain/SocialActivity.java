package com.waqiti.social.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "social_activities")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialActivity {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "activity_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ActivityType activityType;
    
    @Column(name = "payment_id")
    private UUID paymentId;
    
    @Column(name = "target_user_id")
    private UUID targetUserId;
    
    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "currency", length = 3)
    private String currency = "USD";
    
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;
    
    @Column(name = "emoji", length = 10)
    private String emoji;
    
    @Column(name = "visibility", length = 20)
    private String visibility = "FRIENDS"; // PRIVATE, FRIENDS, PUBLIC
    
    @Type(type = "jsonb")
    @Column(name = "media_attachments", columnDefinition = "jsonb")
    private List<String> mediaAttachments;
    
    @Column(name = "location", length = 200)
    private String location;
    
    @Type(type = "jsonb")
    @Column(name = "tags", columnDefinition = "jsonb")
    private List<String> tags;
    
    @Type(type = "jsonb")
    @Column(name = "mentioned_users", columnDefinition = "jsonb")
    private List<UUID> mentionedUsers;
    
    @Column(name = "like_count")
    private Integer likeCount = 0;
    
    @Column(name = "comment_count")
    private Integer commentCount = 0;
    
    @Column(name = "share_count")
    private Integer shareCount = 0;
    
    @Column(name = "is_pinned")
    private Boolean isPinned = false;
    
    @Column(name = "is_trending")
    private Boolean isTrending = false;
    
    @Type(type = "jsonb")
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum ActivityType {
        PAYMENT_SENT,
        PAYMENT_RECEIVED,
        PAYMENT_REQUESTED,
        PAYMENT_SPLIT,
        FRIEND_ADDED,
        PROFILE_UPDATED,
        ACHIEVEMENT_EARNED,
        MILESTONE_REACHED,
        GROUP_JOINED,
        CHARITY_DONATION,
        BIRTHDAY_REMINDER,
        LOCATION_CHECKIN,
        SOCIAL_CHALLENGE,
        PAYMENT_STREAK,
        CASHBACK_EARNED,
        REWARD_CLAIMED
    }
    
    public boolean isEngageable() {
        return activityType == ActivityType.PAYMENT_SENT || 
               activityType == ActivityType.PAYMENT_RECEIVED ||
               activityType == ActivityType.ACHIEVEMENT_EARNED ||
               activityType == ActivityType.MILESTONE_REACHED;
    }
    
    public boolean isPubliclyVisible() {
        return "PUBLIC".equals(visibility);
    }
    
    public void incrementLikes() {
        this.likeCount = (this.likeCount == null ? 0 : this.likeCount) + 1;
    }
    
    public void incrementComments() {
        this.commentCount = (this.commentCount == null ? 0 : this.commentCount) + 1;
    }
    
    public void incrementShares() {
        this.shareCount = (this.shareCount == null ? 0 : this.shareCount) + 1;
    }
}