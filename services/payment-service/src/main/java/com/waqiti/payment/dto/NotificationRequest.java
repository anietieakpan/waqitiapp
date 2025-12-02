package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for sending notifications
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {
    private UUID userId;
    private String type;
    private String title;
    private String message;
    private Map<String, String> data;
    private String channel; // EMAIL, SMS, PUSH
    private String priority; // HIGH, MEDIUM, LOW
}