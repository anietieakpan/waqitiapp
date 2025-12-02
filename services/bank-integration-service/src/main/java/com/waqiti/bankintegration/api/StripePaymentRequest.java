package com.waqiti.bankintegration.api;

import java.math.BigDecimal;
import java.util.Map;

// Request DTOs
@lombok.Data
@lombok.Builder
public class StripePaymentRequest {
    private BigDecimal amount;
    private String currency;
    private String apiKey;
    private String paymentMethodId;
    private String customerId;
    private Map<String, Object> metadata;
}
