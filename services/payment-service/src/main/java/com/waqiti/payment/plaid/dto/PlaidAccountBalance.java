package com.waqiti.payment.plaid.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PlaidAccountBalance {
    private String accountId;
    private String accountName;
    private String accountType; // depository, credit, loan, investment, other
    private String accountSubtype; // checking, savings, cd, money market, etc.
    private BigDecimal available;
    private BigDecimal current;
    private String isoCurrencyCode;
}