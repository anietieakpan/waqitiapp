package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * DTO for notification response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationResponse {
    
    private boolean success;
    private String notificationId;
    private String message;
    private String status;
    private String errorCode;
    private String errorMessage;
    private Instant sentAt;
    private String deliveryStatus;
    private Map<String, Object> metadata;
}