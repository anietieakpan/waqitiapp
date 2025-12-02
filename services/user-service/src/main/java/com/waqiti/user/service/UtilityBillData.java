package com.waqiti.user.service;

import java.math.BigDecimal;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class UtilityBillData {
    private String accountNumber;
    private String accountHolderName;
    private String serviceAddress;
    private java.time.LocalDate billDate;
    private java.time.LocalDate dueDate;
    private String utilityProvider;
    private BigDecimal billAmount;
}
