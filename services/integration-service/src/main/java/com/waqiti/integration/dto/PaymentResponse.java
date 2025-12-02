package com.waqiti.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private String transactionId;
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED
    private BigDecimal amount;
    private String currency;
    private BigDecimal fee;
    private String sourceAccountId;
    private String destinationAccountId;
    private String reference;
    private String description;
    private Instant createdAt;
    private Instant completedAt;
    private String failureReason;
}