package com.waqiti.compliance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Alert Service for Compliance
 * Handles alerting and notifications for compliance events
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AlertService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ComplianceNotificationService notificationService;
    
    public void sendAlert(String alertType, String userId, String message, Map<String, Object> metadata) {
        log.info("Sending compliance alert: type={}, userId={}", alertType, userId);
        
        try {
            Map<String, Object> alert = Map.of(
                "alertType", alertType,
                "userId", userId,
                "message", message,
                "metadata", metadata,
                "timestamp", System.currentTimeMillis()
            );
            
            kafkaTemplate.send("compliance-alerts", userId, alert);
            
            if (notificationService != null) {
                notificationService.sendComplianceAlert(userId, alertType, message);
            }
            
        } catch (Exception e) {
            log.error("Failed to send alert", e);
        }
    }
    
    public void sendFraudAlert(String userId, String transactionId, String reason) {
        sendAlert("FRAUD_ALERT", userId, "Fraud detected: " + reason, 
            Map.of("transactionId", transactionId, "reason", reason));
    }
    
    public void sendComplianceAlert(String userId, String complianceType, String details) {
        sendAlert("COMPLIANCE_ALERT", userId, details, 
            Map.of("complianceType", complianceType));
    }
}