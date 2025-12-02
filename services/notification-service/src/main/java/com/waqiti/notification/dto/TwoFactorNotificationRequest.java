// File: services/notification-service/src/main/java/com/waqiti/notification/dto/TwoFactorNotificationRequest.java
package com.waqiti.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request to send a 2FA notification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorNotificationRequest {
    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotBlank(message = "Recipient is required")
    private String recipient;

    @NotBlank(message = "Verification code is required")
    private String verificationCode;

    private String language = "en";
}