package com.waqiti.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicSubscriptionResponse {
    private String topic;
    private boolean subscribed;
    private LocalDateTime subscribedAt;
    private String message;
}