package com.waqiti.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Recurring Pattern DTO
 *
 * Identifies and tracks recurring transaction patterns.
 *
 * @author Waqiti Analytics Team
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringPattern {

    private String merchantName;
    private BigDecimal averageAmount;
    private String frequency; // DAILY, WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY, ANNUALLY
    private Integer occurrences;
    private LocalDate nextExpectedDate;
    private BigDecimal confidenceScore; // 0.0 - 1.0
    private String category;
    private Boolean isSubscription;
}
