package com.waqiti.bankintegration.api;

import java.math.BigDecimal;
import java.util.Map;

@lombok.Data
@lombok.Builder
public class PayPalPaymentRequest {
    private BigDecimal amount;
    private String currency;
    private String clientId;
    private String clientSecret;
    private String returnUrl;
    private String cancelUrl;
    private Map<String, Object> metadata;
}
