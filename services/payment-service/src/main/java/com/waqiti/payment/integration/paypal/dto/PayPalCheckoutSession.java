package com.waqiti.payment.integration.paypal.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * PayPal checkout session response DTO
 */
@Data
@Builder
public class PayPalCheckoutSession {
    private String orderId;
    private String approvalUrl;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}