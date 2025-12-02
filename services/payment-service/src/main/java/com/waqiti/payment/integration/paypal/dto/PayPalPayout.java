package com.waqiti.payment.integration.paypal.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PayPal payout response DTO
 */
@Data
@Builder
public class PayPalPayout {
    private String payoutBatchId;
    private String batchStatus;
    private BigDecimal amount;
    private String currency;
    private String recipientEmail;
    private String reference;
    private LocalDateTime createdAt;
}