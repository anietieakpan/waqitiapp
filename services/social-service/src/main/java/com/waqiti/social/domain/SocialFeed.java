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
@Table(name = "social_feed")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialFeed {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "activity_id", nullable = false)
    private UUID activityId; // References the payment, connection, etc.
    
    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 30)
    private ActivityType activityType;
    
    @Column(name = "title", nullable = false, length = 200)
    private String title;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "currency", length = 3)
    private String currency;
    
    @Column(name = "emoji", length = 10)
    private String emoji;
    
    @Type(type = "jsonb")
    @Column(name = "participants", columnDefinition = "jsonb")
    private List<UUID> participants; // Other users involved
    
    @Type(type = "jsonb")
    @Column(name = "media_urls", columnDefinition = "jsonb")
    private List<String> mediaUrls;
    
    @Type(type = "jsonb")
    @Column(name = "tags", columnDefinition = "jsonb")
    private List<String> tags;
    
    @Column(name = "location", length = 200)
    private String location;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private Visibility visibility = Visibility.FRIENDS;
    
    @Column(name = "is_pinned")
    private Boolean isPinned = false;
    
    @Column(name = "likes_count")
    private Long likesCount = 0L;
    
    @Column(name = "comments_count")
    private Long commentsCount = 0L;
    
    @Column(name = "shares_count")
    private Long sharesCount = 0L;
    
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
        BILL_SPLIT,
        GROUP_PAYMENT,
        NEW_CONNECTION,
        ACHIEVEMENT,
        MILESTONE,
        GOAL_REACHED,
        INVESTMENT_GAIN,
        CRYPTO_TRADE,
        REWARD_EARNED,
        CHARITY_DONATION,
        BIRTHDAY_REMINDER,
        CUSTOM_POST
    }
    
    public enum Visibility {
        PRIVATE,    // Only user can see
        FRIENDS,    // Only friends can see
        PUBLIC,     // Everyone can see
        CUSTOM      // Custom visibility settings
    }
    
    public boolean isPublic() {
        return visibility == Visibility.PUBLIC;
    }
    
    public boolean isFriendsOnly() {
        return visibility == Visibility.FRIENDS;
    }
    
    public long getTotalEngagement() {
        return likesCount + commentsCount + sharesCount;
    }
}