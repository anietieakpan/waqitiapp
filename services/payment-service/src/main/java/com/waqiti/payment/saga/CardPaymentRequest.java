package com.waqiti.payment.saga;

@lombok.Data
public class CardPaymentRequest {
    private String cardId;
    private String merchantId;
    private java.math.BigDecimal amount;
    private String currency;
    private String description;
    private String merchantReference;
    private String requestId;
}
