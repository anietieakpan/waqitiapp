package com.waqiti.saga.compensation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Service for sending alerts when compensation failures occur
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertingService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    public void sendCriticalAlert(String title, Map<String, Object> details) {
        Alert alert = Alert.builder()
            .alertId(UUID.randomUUID().toString())
            .severity("CRITICAL")
            .title(title)
            .details(details)
            .timestamp(Instant.now())
            .requiresAcknowledgement(true)
            .build();
        
        log.error("CRITICAL ALERT: {}", title);
        kafkaTemplate.send("alerts-critical", alert);
        
        // Would also integrate with PagerDuty, Slack, etc.
    }
    
    public void sendEmergencyAlert(String title, Map<String, Object> details) {
        Alert alert = Alert.builder()
            .alertId(UUID.randomUUID().toString())
            .severity("EMERGENCY")
            .title(title)
            .details(details)
            .timestamp(Instant.now())
            .requiresAcknowledgement(true)
            .autoEscalate(true)
            .build();
        
        log.error("EMERGENCY ALERT: {}", title);
        kafkaTemplate.send("alerts-emergency", alert);
    }
    
    @lombok.Data
    @lombok.Builder
    public static class Alert {
        private String alertId;
        private String severity;
        private String title;
        private Map<String, Object> details;
        private Instant timestamp;
        private boolean requiresAcknowledgement;
        private boolean autoEscalate;
    }
}