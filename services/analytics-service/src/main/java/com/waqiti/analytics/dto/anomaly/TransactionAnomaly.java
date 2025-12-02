package com.waqiti.analytics.dto.anomaly;

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
public class TransactionAnomaly {
    private String anomalyId;
    private String userId;
    private String transactionId;
    private String anomalyType; // AMOUNT, TIME, LOCATION, FREQUENCY, MERCHANT
    private BigDecimal severity; // 0-1 score
    private String description;
    private BigDecimal expectedValue;
    private BigDecimal actualValue;
    private LocalDateTime detectedAt;
    private String status; // ACTIVE, RESOLVED, FALSE_POSITIVE
}