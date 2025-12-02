package com.waqiti.payment.integration.paypal.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PayPal subscription response DTO
 */
@Data
@Builder
public class PayPalSubscription {
    private String subscriptionId;
    private String status;
    private String planId;
    private UUID customerId;
    private String startTime;
    private String approvalUrl;
    private LocalDateTime createdAt;
}