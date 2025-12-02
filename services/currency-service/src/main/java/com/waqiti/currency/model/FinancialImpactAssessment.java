package com.waqiti.currency.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Financial Impact Assessment
 */
@Data
@Builder
public class FinancialImpactAssessment {
    private String conversionId;
    private String fromCurrency;
    private String toCurrency;
    private BigDecimal amount;
    private ImpactType impactType;
    private String correlationId;
    private Instant assessmentDate;
    private String status;
    private Priority priority;
    private String assignedTo;
    private String resolution;
    private BigDecimal financialImpact;
}
