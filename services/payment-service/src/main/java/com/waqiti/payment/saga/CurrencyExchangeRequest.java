package com.waqiti.payment.saga;

@lombok.Data
public class CurrencyExchangeRequest {
    private String userId;
    private String fromCurrency;
    private String toCurrency;
    private java.math.BigDecimal amount;
    private String fromWalletId;
    private String toWalletId;
    private String requestId;
}
