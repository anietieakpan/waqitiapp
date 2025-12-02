package com.waqiti.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectedMessage {
    private String userId;
    private String sessionId;
    private Instant timestamp;
    private String message;
    
    public static ConnectedMessage create(String userId, String sessionId) {
        return ConnectedMessage.builder()
            .userId(userId)
            .sessionId(sessionId)
            .timestamp(Instant.now())
            .message("Connected successfully")
            .build();
    }
}