package com.waqiti.analytics.dto.analytics;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    private String category;
}