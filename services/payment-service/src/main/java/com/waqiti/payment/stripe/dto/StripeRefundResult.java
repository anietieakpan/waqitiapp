package com.waqiti.payment.stripe.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class StripeRefundResult {
    private String id;
    private String status; // pending, succeeded, failed, canceled
    private BigDecimal amount;
    private String currency;
    private String paymentIntentId;
    private String reason;
    private LocalDateTime created;
    private Map<String, String> metadata;
}