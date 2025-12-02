package com.waqiti.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Request to send a notification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendNotificationRequest {
    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotBlank(message = "Template code is required")
    private String templateCode;

    // Parameters for the template
    private Map<String, Object> parameters;

    // Types of notification to send (if not specified, sends based on user preferences)
    private String[] types;

    // Optional reference ID for tracking
    private String referenceId;

    // Optional action URL
    private String actionUrl;

    // Optional expiry date
    private LocalDateTime expiresAt;
}
