package com.waqiti.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Customer Metrics DTO
 *
 * Contains comprehensive customer analytics and metrics
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-11-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerMetrics {

    private String customerId;
    private Double engagementScore;
    private Double healthScore;
    private BigDecimal lifetimeValue;
    private Integer totalInteractions;
    private LocalDateTime lastActivityDate;
    private Long daysSinceLastActivity;
    private Double averageSatisfactionScore;
    private Integer feedbackCount;
}
