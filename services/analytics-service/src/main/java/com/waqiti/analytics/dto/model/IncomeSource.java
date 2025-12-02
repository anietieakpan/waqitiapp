package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Income source model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeSource {
    private String sourceName;
    private String sourceType;
    private BigDecimal amount;
    private Integer frequency; // Times per month
    private LocalDateTime lastReceived;
    private Boolean isRecurring;
}