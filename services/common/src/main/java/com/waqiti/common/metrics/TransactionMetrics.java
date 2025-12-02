package com.waqiti.common.metrics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Transaction metrics data model
 */
@Data
@Builder
public class TransactionMetrics {
    private String transactionId;
    private String transactionType; // PAYMENT, TRANSFER, WITHDRAWAL, DEPOSIT
    private TransactionStatus status;
    private String currency;
    private BigDecimal amount;
    private Duration processingTime;
    private String userId;
    private String merchantId;
    private String paymentMethod;
    private String errorCode;
    private String failureReason;
}

enum TransactionStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
    REFUNDED
}