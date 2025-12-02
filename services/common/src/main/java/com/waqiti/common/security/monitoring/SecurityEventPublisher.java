package com.waqiti.common.security.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Publisher for security-related events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityEventPublisher {
    
    private final ApplicationEventPublisher applicationEventPublisher;
    
    /**
     * Publish a general security event
     */
    public void publishSecurityEvent(String eventType, String clientIp, Object data, String resource) {
        try {
            SecurityEvent event = new SecurityEvent();
            event.setEventType(eventType);
            event.setClientIp(clientIp);
            event.setResource(resource);
            event.setTimestamp(LocalDateTime.now());
            event.setData(data);
            
            applicationEventPublisher.publishEvent(event);
            log.debug("Published security event: {} for IP: {}", eventType, clientIp);
        } catch (Exception e) {
            log.error("Failed to publish security event", e);
        }
    }
    
    /**
     * Publish high threat alert
     */
    public void publishHighThreatAlert(String clientIp, int threatScore) {
        try {
            HighThreatAlert alert = new HighThreatAlert();
            alert.setClientIp(clientIp);
            alert.setThreatScore(threatScore);
            alert.setTimestamp(LocalDateTime.now());
            
            applicationEventPublisher.publishEvent(alert);
            log.warn("Published high threat alert for IP: {} with score: {}", clientIp, threatScore);
        } catch (Exception e) {
            log.error("Failed to publish high threat alert", e);
        }
    }
    
    /**
     * Security event data class
     */
    public static class SecurityEvent {
        private String eventType;
        private String clientIp;
        private String resource;
        private LocalDateTime timestamp;
        private Object data;
        
        // Getters and setters
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        
        public String getClientIp() { return clientIp; }
        public void setClientIp(String clientIp) { this.clientIp = clientIp; }
        
        public String getResource() { return resource; }
        public void setResource(String resource) { this.resource = resource; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public Object getData() { return data; }
        public void setData(Object data) { this.data = data; }
    }
    
    /**
     * High threat alert data class
     */
    public static class HighThreatAlert {
        private String clientIp;
        private int threatScore;
        private LocalDateTime timestamp;
        
        // Getters and setters
        public String getClientIp() { return clientIp; }
        public void setClientIp(String clientIp) { this.clientIp = clientIp; }
        
        public int getThreatScore() { return threatScore; }
        public void setThreatScore(int threatScore) { this.threatScore = threatScore; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
}