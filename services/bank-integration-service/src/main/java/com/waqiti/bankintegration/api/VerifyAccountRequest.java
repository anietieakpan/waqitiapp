package com.waqiti.bankintegration.api;

import java.math.BigDecimal;

@lombok.Data
@lombok.Builder
public class VerifyAccountRequest {
    private String accountId;
    private BigDecimal deposit1;
    private BigDecimal deposit2;
}
