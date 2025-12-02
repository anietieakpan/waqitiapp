package com.waqiti.messaging.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private String id;
    
    @Column(name = "conversation_id", nullable = false)
    private String conversationId;
    
    @Column(name = "sender_id", nullable = false)
    private String senderId;
    
    @Column(name = "message_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private MessageType messageType;
    
    @Column(name = "encrypted_content", columnDefinition = "TEXT")
    private String encryptedContent;
    
    @Column(name = "encrypted_key", columnDefinition = "TEXT")
    private String encryptedKey;
    
    @Column(name = "ephemeral_public_key")
    private String ephemeralPublicKey;
    
    @Column(name = "signature")
    private String signature;
    
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
    
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
    
    @Column(name = "read_at")
    private LocalDateTime readAt;
    
    @Column(name = "edited_at")
    private LocalDateTime editedAt;
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "is_ephemeral")
    private Boolean isEphemeral;
    
    @Column(name = "ephemeral_duration")
    private Integer ephemeralDuration; // in seconds
    
    @Column(name = "reply_to_message_id")
    private String replyToMessageId;
    
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MessageReceipt> receipts = new ArrayList<>();
    
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MessageAttachment> attachments = new ArrayList<>();
    
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MessageReaction> reactions = new ArrayList<>();
    
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private MessageStatus status;
    
    @Column(name = "forward_count")
    private Integer forwardCount = 0;
    
    @Column(name = "is_pinned")
    private Boolean isPinned = false;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        sentAt = LocalDateTime.now();
        status = MessageStatus.SENT;
        
        if (isEphemeral != null && isEphemeral && ephemeralDuration != null) {
            expiresAt = sentAt.plusSeconds(ephemeralDuration);
        }
    }
    
    public void markAsDelivered() {
        this.deliveredAt = LocalDateTime.now();
        this.status = MessageStatus.DELIVERED;
    }
    
    public void markAsRead() {
        this.readAt = LocalDateTime.now();
        this.status = MessageStatus.READ;
    }
    
    public void markAsDeleted() {
        this.deletedAt = LocalDateTime.now();
        this.status = MessageStatus.DELETED;
        // Clear encrypted content for security
        this.encryptedContent = null;
        this.encryptedKey = null;
    }
    
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}

enum MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    FILE,
    LOCATION,
    CONTACT,
    PAYMENT,
    SYSTEM,
    TYPING_INDICATOR
}

enum MessageStatus {
    SENT,
    DELIVERED,
    READ,
    EDITED,
    DELETED,
    FAILED,
    EXPIRED
}