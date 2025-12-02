package com.waqiti.user.service;

import java.math.BigDecimal;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class TaxDocumentData {
    private String taxId;
    private String taxpayerName;
    private int taxYear;
    private String documentType;
    private BigDecimal grossIncome;
    private BigDecimal taxPaid;
    private java.time.LocalDate filingDate;
}
