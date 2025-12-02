package com.waqiti.recurringpayment.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Payment Result DTO for Payment Service integration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResult {
    private boolean successful;
    private String paymentId;
    private String transactionId;
    private String status;
    private BigDecimal amount;
    private BigDecimal fee;
    private String currency;
    private Instant processedAt;
    private String errorCode;
    private String errorMessage;
    private Map<String, Object> details;
    private String processorReference;
    private Integer retryAfterSeconds;
}