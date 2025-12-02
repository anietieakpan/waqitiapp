package com.waqiti.user.service;

import java.math.BigDecimal;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class BankStatementData {
    private String accountNumber;
    private String accountHolderName;
    private String bankName;
    private java.time.LocalDate statementPeriodStart;
    private java.time.LocalDate statementPeriodEnd;
    private String address;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
}
