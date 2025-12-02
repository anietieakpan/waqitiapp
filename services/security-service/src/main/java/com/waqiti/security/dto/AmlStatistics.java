package com.waqiti.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * AML Statistics DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmlStatistics {
    private Long totalScreenings;
    private Long flaggedTransactions;
    private Long sarsField;
    private Map<String, Long> alertsByType;
    private Map<String, BigDecimal> volumeByRiskLevel;
    private LocalDate fromDate;
    private LocalDate toDate;
}
