package com.waqiti.messaging.dto;

import com.waqiti.messaging.domain.ConversationType;
import com.waqiti.messaging.domain.EncryptionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDTO {
    
    private String id;
    private ConversationType type;
    private String name;
    private String description;
    private String avatarUrl;
    private List<ParticipantDTO> participants;
    private Integer participantCount;
    private LocalDateTime createdAt;
    private LocalDateTime lastMessageAt;
    private String lastMessagePreview;
    private Boolean isEncrypted;
    private EncryptionType encryptionType;
    private Boolean isArchived;
    private Boolean isMuted;
    private LocalDateTime mutedUntil;
    private Boolean ephemeralMessagesEnabled;
    private Integer defaultEphemeralDuration;
    private Integer unreadCount;
}