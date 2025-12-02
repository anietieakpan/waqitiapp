package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO representing top variance items (positive or negative)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopVarianceItem {
    private String category;
    private String accountName;
    private BigDecimal variance;
    private BigDecimal variancePercentage;
    private String impact;
}