package com.waqiti.security.rasp.service;

import com.waqiti.security.rasp.model.SecurityEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for handling security events
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SecurityEventService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final String SECURITY_EVENTS_TOPIC = "security-events";

    /**
     * Log a security event
     */
    public void logSecurityEvent(SecurityEvent event) {
        try {
            // Ensure timestamp is set
            if (event.getTimestamp() == null) {
                event.setTimestamp(LocalDateTime.now());
            }
            
            // Create event payload for Kafka
            Map<String, Object> eventPayload = createEventPayload(event);
            
            // Send to Kafka for processing and storage
            kafkaTemplate.send(SECURITY_EVENTS_TOPIC, eventPayload);
            
            // Log locally for immediate visibility
            log.warn("Security event logged: {} - {} from IP: {}", 
                    event.getThreatType(), 
                    event.getDescription(), 
                    event.getClientIp());
            
        } catch (Exception e) {
            log.error("Failed to log security event: ", e);
            // Fallback to local logging only
            log.error("SECURITY_EVENT_FALLBACK: {} - {} from IP: {}", 
                    event.getThreatType(), 
                    event.getDescription(), 
                    event.getClientIp());
        }
    }

    private Map<String, Object> createEventPayload(SecurityEvent event) {
        Map<String, Object> payload = new HashMap<>();
        
        // Basic event information
        payload.put("requestId", event.getRequestId());
        payload.put("timestamp", event.getTimestamp());
        payload.put("threatType", event.getThreatType());
        payload.put("threatLevel", event.getThreatLevel() != null ? event.getThreatLevel().name() : null);
        payload.put("description", event.getDescription());
        payload.put("detectorName", event.getDetectorName());
        payload.put("blocked", event.isBlocked());
        payload.put("action", event.getAction());
        
        // Request information
        payload.put("clientIp", event.getClientIp());
        payload.put("userAgent", event.getUserAgent());
        payload.put("uri", event.getUri());
        payload.put("method", event.getMethod());
        payload.put("requestSize", event.getRequestSize());
        payload.put("contentType", event.getContentType());
        
        // User and session info
        payload.put("userId", event.getUserId());
        payload.put("sessionId", event.getSessionId());
        
        // Geolocation data
        payload.put("country", event.getCountry());
        payload.put("city", event.getCity());
        payload.put("organization", event.getOrganization());
        
        // Client characteristics
        payload.put("isBot", event.isBot());
        payload.put("isTor", event.isTor());
        payload.put("isVpn", event.isVpn());
        
        // Attack vectors (truncated for security)
        if (event.getAttackPayload() != null) {
            String payload_truncated = event.getAttackPayload().length() > 1000 ? 
                    event.getAttackPayload().substring(0, 1000) + "..." : 
                    event.getAttackPayload();
            payload.put("attackPayload", payload_truncated);
        }
        
        payload.put("sqlInjectionVector", truncateString(event.getSqlInjectionVector(), 500));
        payload.put("xssVector", truncateString(event.getXssVector(), 500));
        payload.put("commandInjectionVector", truncateString(event.getCommandInjectionVector(), 500));
        payload.put("pathTraversalVector", truncateString(event.getPathTraversalVector(), 500));
        
        // Rate limiting info
        payload.put("requestCount", event.getRequestCount());
        payload.put("rateLimitWindow", event.getRateLimitWindow());
        
        // Response time
        payload.put("responseTime", event.getResponseTime());
        
        // Additional metadata
        payload.put("metadata", event.getMetadata());
        
        return payload;
    }

    private String truncateString(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }

    /**
     * Log a security metric for monitoring
     */
    public void logSecurityMetric(String metricName, Object value, Map<String, String> tags) {
        try {
            Map<String, Object> metric = new HashMap<>();
            metric.put("type", "SECURITY_METRIC");
            metric.put("name", metricName);
            metric.put("value", value);
            metric.put("tags", tags);
            metric.put("timestamp", LocalDateTime.now());
            
            kafkaTemplate.send("security-metrics", metric);
        } catch (Exception e) {
            log.error("Failed to log security metric {}: ", metricName, e);
        }
    }
}