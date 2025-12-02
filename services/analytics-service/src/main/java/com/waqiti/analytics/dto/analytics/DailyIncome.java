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
public class DailyIncome {
    private LocalDateTime date;
    private BigDecimal amount;
    private Integer transactionCount;
    private String primarySource;
}