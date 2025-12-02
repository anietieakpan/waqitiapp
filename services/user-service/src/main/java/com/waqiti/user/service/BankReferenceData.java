package com.waqiti.user.service;

import java.math.BigDecimal;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class BankReferenceData {
    private String accountHolderName;
    private String accountNumber;
    private String bankName;
    private String branchName;
    private java.time.LocalDate accountOpenDate;
    private java.time.LocalDate referenceDate;
    private String accountStatus;
    private BigDecimal averageBalance;
}
