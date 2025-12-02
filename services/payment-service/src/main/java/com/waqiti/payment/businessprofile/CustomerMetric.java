package com.waqiti.payment.businessprofile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Customer metrics for business analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerMetric {

    private UUID customerId;
    private String customerName;
    private Long transactionCount;
    private BigDecimal totalSpent;
    private BigDecimal averageTransactionValue;
    private LocalDate lastTransactionDate;
    private String segment;
    private Double satisfactionScore;

    // Helper methods
    public boolean isHighValue() {
        return totalSpent != null && totalSpent.compareTo(BigDecimal.valueOf(10000)) > 0;
    }

    public boolean isActive() {
        return lastTransactionDate != null &&
               lastTransactionDate.isAfter(LocalDate.now().minusMonths(3));
    }
}
