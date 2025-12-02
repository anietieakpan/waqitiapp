package com.waqiti.messaging.dto;

import com.waqiti.messaging.domain.MessageStatus;
import com.waqiti.messaging.domain.MessageType;
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
public class MessageDTO {
    
    private String id;
    private String conversationId;
    private String senderId;
    private String senderName;
    private String senderAvatar;
    private MessageType messageType;
    private String content;
    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime readAt;
    private LocalDateTime editedAt;
    private Boolean isEphemeral;
    private Integer ephemeralDuration;
    private String replyToMessageId;
    private MessageStatus status;
    private List<AttachmentDTO> attachments;
    private List<ReactionDTO> reactions;
    private Integer forwardCount;
    private Boolean isPinned;
}