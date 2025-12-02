package com.waqiti.bankintegration.api;

import java.math.BigDecimal;

@lombok.Data
@lombok.Builder
public class TransferRequest {
    private String userId;
    private String toAccount;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String transferType; // ach, wire, instant
}
