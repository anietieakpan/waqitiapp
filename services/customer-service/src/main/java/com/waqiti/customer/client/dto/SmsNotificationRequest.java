package com.waqiti.customer.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

/**
 * Request DTO for sending SMS notifications to notification-service.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsNotificationRequest {

    /**
     * Recipient phone number (E.164 format)
     */
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "Phone number must be in E.164 format")
    private String phoneNumber;

    /**
     * SMS message content
     */
    @NotBlank(message = "Message is required")
    private String message;

    /**
     * Sender ID or phone number
     */
    private String senderId;

    /**
     * SMS template ID (if using template)
     */
    private String templateId;

    /**
     * Template parameters
     */
    private Map<String, Object> templateParameters;

    /**
     * Whether this is a transactional SMS
     */
    private Boolean transactional;

    /**
     * Priority level
     */
    private String priority;

    /**
     * Country code
     */
    private String countryCode;

    /**
     * Additional metadata
     */
    private Map<String, String> metadata;
}
