package com.waqiti.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypingIndicator {
    
    private String conversationId;
    private String userId;
    private String userName;
    private boolean isTyping;
    private Long timestamp;
}