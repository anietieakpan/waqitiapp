package com.waqiti.payment.service;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class TaxCalculationResult {
    private BigDecimal originalAmount;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String currency;
    private String country;
    private String state;
    private TaxType taxType;
    private Map<String, BigDecimal> taxBreakdown;
    private List<String> applicableRules;
    private Map<String, Object> calculation;
}