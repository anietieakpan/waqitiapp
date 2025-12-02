package com.waqiti.payment.integration.paypal.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * PayPal payout request DTO
 */
@Data
@Builder
public class PayPalPayoutRequest {
    private BigDecimal amount;
    private String currency;
    private String recipientEmail;
    private String reference;
    private String note;
}