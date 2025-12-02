package com.waqiti.payment.service;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
public class TaxRateInfo {
    private BigDecimal rate;
    private TaxType taxType;
    private String jurisdiction;
    private LocalDate effectiveDate;
    private Map<String, Object> metadata;
}