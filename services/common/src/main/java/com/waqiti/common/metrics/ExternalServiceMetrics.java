package com.waqiti.common.metrics;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

/**
 * External service call metrics data model
 */
@Data
@Builder
public class ExternalServiceMetrics {
    private String serviceName; // CORE_BANKING, PAYMENT_PROCESSOR, KYC_PROVIDER
    private String operation; // PAYMENT, VERIFICATION, BALANCE_CHECK
    private String status; // SUCCESS, FAILURE, TIMEOUT
    private int responseCode;
    private Duration responseTime;
    private boolean circuitBreakerTriggered;
    private int retryCount;
    private String errorMessage;
    private String endpoint;
    private String region;
}