package com.waqiti.frauddetection.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Fraud Alert Event DTO for Kafka messages
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAlertEvent {

    private String fraudAlertId;
    private String userId;
    private String fraudType; // ACCOUNT_TAKEOVER, PAYMENT_FRAUD, IDENTITY_THEFT, etc.
    private Double riskScore; // 0-100
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private String transactionId;
    private String ipAddress;
    private String deviceId;
    private String location;
    private Map<String, Object> fraudIndicators;
    private LocalDateTime detectedAt;
    private String eventType;
    private String correlationId;
}
