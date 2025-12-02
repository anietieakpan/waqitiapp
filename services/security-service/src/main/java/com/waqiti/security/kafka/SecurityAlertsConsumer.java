package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.service.SecurityAlertService;
import com.waqiti.security.service.SecurityNotificationService;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityAlertsConsumer {
    
    private final SecurityAlertService securityAlertService;
    private final SecurityNotificationService securityNotificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"security-alerts", "fraud-alert", "suspicious-activity"},
        groupId = "security-service-security-alerts-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleSecurityAlert(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.warn("SECURITY ALERT: Processing security alert - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        UUID alertId = null;
        UUID customerId = null;
        String alertType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            alertId = UUID.fromString((String) event.get("alertId"));
            customerId = event.containsKey("customerId") ? 
                    UUID.fromString((String) event.get("customerId")) : null;
            alertType = (String) event.get("alertType");
            String severity = (String) event.get("severity");
            String description = (String) event.get("description");
            LocalDateTime alertTime = LocalDateTime.parse((String) event.get("alertTime"));
            String sourceIp = (String) event.getOrDefault("sourceIp", "");
            String userAgent = (String) event.getOrDefault("userAgent", "");
            String location = (String) event.getOrDefault("location", "");
            String riskScore = (String) event.getOrDefault("riskScore", "MEDIUM");
            Boolean isBlocked = (Boolean) event.getOrDefault("isBlocked", false);
            String alertSource = (String) event.get("alertSource");
            
            log.error("Security alert - AlertId: {}, CustomerId: {}, Type: {}, Severity: {}, Blocked: {}, IP: {}", 
                    alertId, customerId, alertType, severity, isBlocked, sourceIp);
            
            switch (alertType) {
                case "FRAUD_ALERT" -> securityAlertService.processFraudAlert(alertId, customerId, 
                        severity, description, alertTime, sourceIp, userAgent, location, riskScore, 
                        isBlocked, alertSource);
                
                case "SUSPICIOUS_LOGIN" -> securityAlertService.processSuspiciousLogin(alertId, 
                        customerId, description, alertTime, sourceIp, userAgent, location, riskScore, 
                        isBlocked);
                
                case "ACCOUNT_TAKEOVER" -> securityAlertService.processAccountTakeover(alertId, 
                        customerId, severity, description, alertTime, sourceIp, riskScore, isBlocked);
                
                case "VELOCITY_EXCEEDED" -> securityAlertService.processVelocityExceeded(alertId, 
                        customerId, description, alertTime, riskScore, isBlocked, alertSource);
                
                default -> log.warn("Unknown security alert type: {}", alertType);
            }
            
            securityNotificationService.sendSecurityAlert(customerId, alertId, alertType, severity, 
                    description, alertTime, isBlocked);
            
            securityAlertService.updateSecurityMetrics(alertType, severity, riskScore, isBlocked, 
                    alertSource);
            
            auditService.auditFinancialEvent(
                    "SECURITY_ALERT_PROCESSED",
                    customerId != null ? customerId.toString() : "SYSTEM",
                    String.format("Security alert %s - Severity: %s, Blocked: %s, IP: %s", 
                            alertType, severity, isBlocked, sourceIp),
                    Map.of(
                            "alertId", alertId.toString(),
                            "customerId", customerId != null ? customerId.toString() : "NONE",
                            "alertType", alertType,
                            "severity", severity,
                            "description", description,
                            "sourceIp", sourceIp,
                            "location", location,
                            "riskScore", riskScore,
                            "isBlocked", isBlocked.toString(),
                            "alertSource", alertSource
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Security alert processing failed - AlertId: {}, CustomerId: {}, Type: {}, Error: {}", 
                    alertId, customerId, alertType, e.getMessage(), e);
            throw new RuntimeException("Security alert processing failed", e);
        }
    }
}