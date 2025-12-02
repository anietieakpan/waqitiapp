package com.waqiti.payment.saga;

@lombok.Data
public class AccountTopUpRequest {
    private String userId;
    private java.math.BigDecimal amount;
    private String currency;
    private String paymentSource;
    private String paymentMethodId;
    private String requestId;
}
