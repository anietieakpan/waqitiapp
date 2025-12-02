package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cash flow data model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowData {
    private LocalDateTime date;
    private BigDecimal inflow;
    private BigDecimal outflow;
    private BigDecimal netFlow;
    private BigDecimal runningBalance;
}