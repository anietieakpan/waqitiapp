package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing variance trend analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VarianceTrend {
    private String trend; // IMPROVING, DETERIORATING, STABLE
    private String description;
}