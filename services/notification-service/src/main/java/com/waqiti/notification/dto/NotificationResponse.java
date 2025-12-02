package com.waqiti.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID; /**
 * Response for notification operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationResponse {
    private UUID id;
    private UUID userId;
    private String title;
    private String message;
    private String type;
    private String category;
    private String referenceId;
    private boolean read;
    private String actionUrl;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime readAt;
    private String deliveryStatus;
}
