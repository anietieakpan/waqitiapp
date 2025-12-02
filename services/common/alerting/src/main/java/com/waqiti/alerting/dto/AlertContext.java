package com.waqiti.alerting.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Rich context information for alerts
 * Provides comprehensive metadata for on-call engineers to diagnose and resolve incidents
 */
@Data
@Builder
public class AlertContext {
    private String serviceName;
    private String component;
    private String userId;
    private String transactionId;
    private String walletId;
    private String kafkaTopic;
    private String messageId;
    private String operation;
    private String errorDetails;
    private String complianceIssue;
    private String riskFactors;
    private Double riskScore;
    private Instant timestamp;
    private Map<String, Object> additionalData;
}
