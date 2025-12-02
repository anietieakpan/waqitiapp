package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO providing explanations for budget variances
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VarianceExplanation {
    private String category;
    private String explanation;
    private String impact;
    private String recommendation;
}