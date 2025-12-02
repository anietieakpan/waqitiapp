package com.waqiti.messaging.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "conversation_participants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationParticipant {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private ParticipantRole role;
    
    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;
    
    @Column(name = "left_at")
    private LocalDateTime leftAt;
    
    @Column(name = "last_read_message_id")
    private String lastReadMessageId;
    
    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;
    
    @Column(name = "notification_preferences")
    @Enumerated(EnumType.STRING)
    private NotificationPreference notificationPreference;
    
    @Column(name = "is_admin")
    private Boolean isAdmin = false;
    
    @Column(name = "can_send_messages")
    private Boolean canSendMessages = true;
    
    @Column(name = "can_add_participants")
    private Boolean canAddParticipants = false;
    
    @Column(name = "nickname")
    private String nickname;
    
    @Column(name = "unread_count")
    private Integer unreadCount = 0;
    
    @Column(name = "is_typing")
    private Boolean isTyping = false;
    
    @Column(name = "typing_started_at")
    private LocalDateTime typingStartedAt;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        joinedAt = LocalDateTime.now();
        if (notificationPreference == null) {
            notificationPreference = NotificationPreference.ALL;
        }
    }
    
    public void updateLastRead(String messageId) {
        this.lastReadMessageId = messageId;
        this.lastReadAt = LocalDateTime.now();
        this.unreadCount = 0;
    }
    
    public void incrementUnreadCount() {
        this.unreadCount++;
    }
    
    public void startTyping() {
        this.isTyping = true;
        this.typingStartedAt = LocalDateTime.now();
    }
    
    public void stopTyping() {
        this.isTyping = false;
        this.typingStartedAt = null;
    }
    
    public boolean isActive() {
        return leftAt == null;
    }
}

enum ParticipantRole {
    OWNER,
    ADMIN,
    MODERATOR,
    MEMBER,
    VIEWER
}

enum NotificationPreference {
    ALL,
    MENTIONS_ONLY,
    NONE
}