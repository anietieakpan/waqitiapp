package com.waqiti.social.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "social_interactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialInteraction {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "target_activity_id", nullable = false)
    private UUID targetActivityId;
    
    @Column(name = "target_user_id", nullable = false)
    private UUID targetUserId;
    
    @Column(name = "interaction_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private InteractionType interactionType;
    
    @Column(name = "comment_text", columnDefinition = "TEXT")
    private String commentText;
    
    @Column(name = "emoji", length = 10)
    private String emoji;
    
    @Column(name = "gif_url", length = 500)
    private String gifUrl;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sticker_data", columnDefinition = "jsonb")
    private Map<String, Object> stickerData;
    
    @Column(name = "reply_to_interaction_id")
    private UUID replyToInteractionId;
    
    @Column(name = "is_edited")
    private Boolean isEdited = false;
    
    @Column(name = "is_deleted")
    private Boolean isDeleted = false;
    
    @Column(name = "like_count")
    private Integer likeCount = 0;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "edited_at")
    private LocalDateTime editedAt;
    
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
        if (isEdited != null && isEdited) {
            editedAt = LocalDateTime.now();
        }
    }
    
    public enum InteractionType {
        LIKE,
        LOVE,
        LAUGH,
        WOW,
        COMMENT,
        SHARE,
        MENTION,
        TAG,
        REACT_EMOJI,
        REACT_GIF,
        REACT_STICKER
    }
    
    public boolean isReaction() {
        return interactionType == InteractionType.LIKE ||
               interactionType == InteractionType.LOVE ||
               interactionType == InteractionType.LAUGH ||
               interactionType == InteractionType.WOW ||
               interactionType == InteractionType.REACT_EMOJI;
    }
    
    public boolean isComment() {
        return interactionType == InteractionType.COMMENT;
    }
    
    public boolean hasContent() {
        return commentText != null && !commentText.trim().isEmpty();
    }
    
    public void incrementLikes() {
        this.likeCount = (this.likeCount == null ? 0 : this.likeCount) + 1;
    }
}