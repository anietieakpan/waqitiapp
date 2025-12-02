package com.waqiti.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageEvent {
    
    private MessageEventType type;
    private String messageId;
    private String conversationId;
    private String senderId;
    private LocalDateTime timestamp;
    private Object data;
}

enum MessageEventType {
    NEW_MESSAGE,
    MESSAGE_EDITED,
    MESSAGE_DELETED,
    MESSAGE_READ,
    MESSAGE_DELIVERED,
    REACTION_ADDED,
    REACTION_REMOVED,
    TYPING_START,
    TYPING_STOP,
    PARTICIPANT_JOINED,
    PARTICIPANT_LEFT,
    CONVERSATION_UPDATED
}