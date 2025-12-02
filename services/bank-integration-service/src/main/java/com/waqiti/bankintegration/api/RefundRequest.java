package com.waqiti.bankintegration.api;

import java.math.BigDecimal;

@lombok.Data
@lombok.Builder
public class RefundRequest {
    private String transactionId;
    private BigDecimal amount;
    private String reason;
    private String apiKey;
    private String clientId;
    private String clientSecret;
}
