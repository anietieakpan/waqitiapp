package com.waqiti.bankintegration.api;

import java.math.BigDecimal;

@lombok.Data
@lombok.Builder
public class PlaidPaymentRequest {
    private BigDecimal amount;
    private String currency;
    private String clientId;
    private String secret;
    private String accountId;
    private String recipientId;
}
