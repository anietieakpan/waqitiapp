package com.waqiti.payment.integration.paypal.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * PayPal subscription creation request DTO
 */
@Data
@Builder
public class PayPalSubscriptionRequest {
    private String planId;
    private UUID customerId;
    private String customerName;
    private String customerEmail;
}