package com.waqiti.payment.stripe.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class StripePaymentIntentResult {
    private String id;
    private String status; // requires_payment_method, requires_confirmation, requires_action, processing, requires_capture, canceled, succeeded
    private BigDecimal amount;
    private String currency;
    private String clientSecret;
    private LocalDateTime created;
    private Map<String, String> metadata;
}