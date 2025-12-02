package com.waqiti.common.metrics.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Comprehensive business metrics dashboard
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessDashboard {
    private String dashboardId;
    private LocalDateTime timestamp;
    private TransactionMetrics transactionMetrics;
    private UserMetrics userMetrics;
    private PaymentMetrics paymentMetrics;
    private FraudMetrics fraudMetrics;
    private SystemMetrics systemMetrics;
    private Map<String, Object> customMetrics;
}