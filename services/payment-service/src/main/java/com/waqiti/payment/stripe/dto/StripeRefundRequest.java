package com.waqiti.payment.stripe.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class StripeRefundRequest {
    private String paymentIntentId;
    private BigDecimal amount; // null for full refund
    private String reason; // duplicate, fraudulent, requested_by_customer
    private Map<String, String> metadata;
}