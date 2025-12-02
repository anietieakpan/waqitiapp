package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Daily income model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyIncome {
    private LocalDateTime date;
    private BigDecimal amount;
    private Integer transactionCount;
    private String primarySource;
}