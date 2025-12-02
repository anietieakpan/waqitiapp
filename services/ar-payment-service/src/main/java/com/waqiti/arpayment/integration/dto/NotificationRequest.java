package com.waqiti.arpayment.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Notification request for notification service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {

    private Set<UUID> recipientIds;
    private String title;
    private String message;
    private String notificationType; // PUSH, EMAIL, SMS, IN_APP
    private String priority; // LOW, NORMAL, HIGH, URGENT
    private Map<String, Object> data;
    private String templateId;
    private Map<String, String> templateVariables;
    private boolean requiresDeliveryConfirmation;
}
