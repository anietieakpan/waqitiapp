package com.waqiti.common.metrics.service;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Duration;

@Data
@Builder
public class TransactionMetrics {
    private String transactionType;
    private BigDecimal amount;
    private String currency;
    private TransactionStatus status;
    private Duration processingTime;
    private String paymentMethod;
    private String provider;
    private String riskLevel;
    private String countryCode;
    private boolean fraudCheckPassed;
    private long retryCount;
}