package com.waqiti.payment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchReport {
    private String batchId;
    private Integer totalPayments;
    private Integer successfulPayments;
    private Integer failedPayments;
    private Double successRate;
    private BigDecimal totalAmount;
    private BigDecimal processedAmount;
    private Long processingTime;
    private List<PaymentProcessingError> errors;
    private Instant generatedAt;
}
