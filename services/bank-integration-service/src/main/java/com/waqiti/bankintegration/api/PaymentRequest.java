package com.waqiti.bankintegration.api;

import java.math.BigDecimal;
import java.util.Map;

@lombok.Data
@lombok.Builder
public class PaymentRequest {
    private BigDecimal amount;
    private String currency;
    private String paymentMethodId;
    private Map<String, Object> metadata;
}
