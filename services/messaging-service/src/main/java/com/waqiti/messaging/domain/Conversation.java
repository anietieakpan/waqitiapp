package com.waqiti.messaging.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "conversations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private String id;
    
    @Column(name = "conversation_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ConversationType type;
    
    @Column(name = "name")
    private String name;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "avatar_url")
    private String avatarUrl;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;
    
    @Column(name = "last_message_preview")
    private String lastMessagePreview;
    
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<ConversationParticipant> participants = new HashSet<>();
    
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("sentAt DESC")
    private List<Message> messages = new ArrayList<>();
    
    @Column(name = "is_encrypted")
    private Boolean isEncrypted = true;
    
    @Column(name = "encryption_type")
    @Enumerated(EnumType.STRING)
    private EncryptionType encryptionType;
    
    @Column(name = "group_key_version")
    private Integer groupKeyVersion;
    
    @Column(name = "is_archived")
    private Boolean isArchived = false;
    
    @Column(name = "is_muted")
    private Boolean isMuted = false;
    
    @Column(name = "muted_until")
    private LocalDateTime mutedUntil;
    
    @Column(name = "pin_code")
    private String pinCode; // For additional conversation security
    
    @Column(name = "ephemeral_messages_enabled")
    private Boolean ephemeralMessagesEnabled = false;
    
    @Column(name = "default_ephemeral_duration")
    private Integer defaultEphemeralDuration; // in seconds
    
    @Column(name = "admin_approval_required")
    private Boolean adminApprovalRequired = false;
    
    @Column(name = "max_participants")
    private Integer maxParticipants;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        
        if (type == ConversationType.DIRECT) {
            maxParticipants = 2;
        }
        
        if (encryptionType == null) {
            encryptionType = EncryptionType.E2E;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public void addParticipant(ConversationParticipant participant) {
        participants.add(participant);
        participant.setConversation(this);
    }
    
    public void removeParticipant(ConversationParticipant participant) {
        participants.remove(participant);
        participant.setConversation(null);
    }
    
    public void updateLastMessage(Message message) {
        this.lastMessageAt = message.getSentAt();
        this.lastMessagePreview = generateMessagePreview(message);
    }
    
    private String generateMessagePreview(Message message) {
        // Generate a preview without exposing encrypted content
        switch (message.getMessageType()) {
            case IMAGE:
                return "📷 Photo";
            case VIDEO:
                return "🎥 Video";
            case AUDIO:
                return "🎵 Audio";
            case FILE:
                return "📎 File";
            case LOCATION:
                return "📍 Location";
            case CONTACT:
                return "👤 Contact";
            case PAYMENT:
                return "💰 Payment";
            default:
                return "Message";
        }
    }
    
    public boolean canAddParticipant() {
        return maxParticipants == null || participants.size() < maxParticipants;
    }
    
    public boolean isGroupConversation() {
        return type == ConversationType.GROUP || type == ConversationType.CHANNEL;
    }
}

enum ConversationType {
    DIRECT,      // 1-to-1 conversation
    GROUP,       // Group chat
    CHANNEL,     // Broadcast channel
    SUPPORT,     // Support conversation
    PAYMENT      // Payment-related conversation
}

enum EncryptionType {
    E2E,         // End-to-end encryption
    TRANSPORT,   // Transport layer encryption only
    NONE         // No encryption (for public channels)
}