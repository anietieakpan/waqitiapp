package com.waqiti.gamification.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
    private String userId;
    private String type;
    private String title;
    private String message;
    private String channel;
    private Map<String, Object> data;
    private String iconUrl;
    private String actionUrl;
    private LocalDateTime timestamp;
}