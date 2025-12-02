package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Webhook request DTO for check status updates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckWebhookRequest {
    private UUID depositId;
    private String referenceId;
    private String status;
    private String returnCode;
    private String returnReason;
    private String holdReason;
    private String processorMessage;
}