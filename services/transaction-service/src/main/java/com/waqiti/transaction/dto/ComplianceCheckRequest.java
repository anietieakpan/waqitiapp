package com.waqiti.transaction.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ComplianceCheckRequest {
    private String transactionId;
    private String fromAccountId;
    private String toAccountId;
    private BigDecimal amount;
    private String transactionType;
    private String currency;
    private String beneficiaryName;
    private String beneficiaryCountry;
}