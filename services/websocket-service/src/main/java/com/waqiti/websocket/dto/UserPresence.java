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
public class UserPresence {
    private String userId;
    private boolean isOnline;
    private Instant lastSeen;
    private String status;
    private String deviceId;
    private String platform;
    private String customStatus;
}