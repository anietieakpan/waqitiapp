package com.waqiti.security.rasp.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.rasp.model.SecurityEvent;
import com.waqiti.security.rasp.model.ThreatLevel;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles security responses when threats are detected
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SecurityResponse {

    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${rasp.response.block-duration-minutes:15}")
    private int blockDurationMinutes;

    @Value("${rasp.response.send-alerts:true}")
    private boolean sendAlerts;

    @Value("${rasp.response.kafka-topic:security-alerts}")
    private String alertTopic;

    /**
     * Handle a detected security threat
     */
    public void handleThreat(SecurityEvent event, HttpServletResponse response) throws IOException {
        log.warn("Handling security threat: {} - {}", event.getThreatType(), event.getDescription());
        
        // Block the request if threat level is high enough
        if (event.shouldBlock()) {
            blockRequest(event, response);
        }
        
        // Send security alert
        if (sendAlerts && event.getThreatLevel().shouldAlert()) {
            sendSecurityAlert(event);
        }
        
        // Apply IP-based blocking for critical threats
        if (event.isCritical()) {
            blockIpAddress(event.getClientIp());
        }
        
        // Log for audit trail
        logSecurityEvent(event);
    }

    private void blockRequest(SecurityEvent event, HttpServletResponse response) throws IOException {
        event.setBlocked(true);
        event.setAction("BLOCKED");
        
        // Set security headers
        response.setHeader("X-Security-Event-ID", event.getRequestId());
        response.setHeader("X-Security-Threat-Type", event.getThreatType());
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-XSS-Protection", "1; mode=block");
        
        // Return appropriate error response based on threat type
        if (event.getThreatType().contains("RATE_LIMIT")) {
            response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
            response.setContentType("application/json");
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Rate limit exceeded");
            errorResponse.put("message", "Too many requests. Please try again later.");
            errorResponse.put("retryAfter", blockDurationMinutes * 60);
            errorResponse.put("timestamp", LocalDateTime.now());
            
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        } else {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Security violation detected");
            errorResponse.put("message", "Your request has been blocked due to security policy.");
            errorResponse.put("requestId", event.getRequestId());
            errorResponse.put("timestamp", LocalDateTime.now());
            
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        }
        
        response.getWriter().flush();
    }

    private void blockIpAddress(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return;
        }
        
        try {
            String blockKey = "blocked_ip:" + clientIp;
            redisTemplate.opsForValue().set(blockKey, "BLOCKED", Duration.ofMinutes(blockDurationMinutes));
            
            log.warn("Blocked IP address {} for {} minutes due to critical security threat", 
                    clientIp, blockDurationMinutes);
            
            // Send IP block notification
            Map<String, Object> blockNotification = new HashMap<>();
            blockNotification.put("type", "IP_BLOCKED");
            blockNotification.put("ip", clientIp);
            blockNotification.put("duration", blockDurationMinutes);
            blockNotification.put("timestamp", LocalDateTime.now());
            
            kafkaTemplate.send(alertTopic, blockNotification);
        } catch (Exception e) {
            log.error("Failed to block IP address {}: ", clientIp, e);
        }
    }

    private void sendSecurityAlert(SecurityEvent event) {
        try {
            Map<String, Object> alert = new HashMap<>();
            alert.put("type", "SECURITY_THREAT_DETECTED");
            alert.put("eventId", event.getRequestId());
            alert.put("threatType", event.getThreatType());
            alert.put("threatLevel", event.getThreatLevel().name());
            alert.put("description", event.getDescription());
            alert.put("clientIp", event.getClientIp());
            alert.put("userAgent", event.getUserAgent());
            alert.put("uri", event.getUri());
            alert.put("method", event.getMethod());
            alert.put("detector", event.getDetectorName());
            alert.put("blocked", event.isBlocked());
            alert.put("timestamp", event.getTimestamp());
            
            // Add attack payload (truncated for security)
            if (event.getAttackPayload() != null) {
                String payload = event.getAttackPayload();
                alert.put("attackPayload", payload.length() > 500 ? 
                        payload.substring(0, 500) + "..." : payload);
            }
            
            kafkaTemplate.send(alertTopic, alert);
            log.info("Security alert sent for threat: {} from IP: {}", 
                    event.getThreatType(), event.getClientIp());
        } catch (Exception e) {
            log.error("Failed to send security alert: ", e);
        }
    }

    private void logSecurityEvent(SecurityEvent event) {
        // Enhanced logging for security audit
        Map<String, Object> auditLog = new HashMap<>();
        auditLog.put("event_type", "SECURITY_EVENT");
        auditLog.put("request_id", event.getRequestId());
        auditLog.put("threat_type", event.getThreatType());
        auditLog.put("threat_level", event.getThreatLevel().name());
        auditLog.put("client_ip", event.getClientIp());
        auditLog.put("user_agent", event.getUserAgent());
        auditLog.put("uri", event.getUri());
        auditLog.put("method", event.getMethod());
        auditLog.put("detector", event.getDetectorName());
        auditLog.put("blocked", event.isBlocked());
        auditLog.put("action", event.getAction());
        auditLog.put("timestamp", event.getTimestamp());
        
        try {
            log.warn("SECURITY_AUDIT: {}", objectMapper.writeValueAsString(auditLog));
        } catch (Exception e) {
            log.error("Failed to log security event: ", e);
        }
    }

    /**
     * Check if an IP address is currently blocked
     */
    public boolean isIpBlocked(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return false;
        }
        
        try {
            String blockKey = "blocked_ip:" + clientIp;
            return redisTemplate.hasKey(blockKey);
        } catch (Exception e) {
            log.error("Error checking if IP {} is blocked: ", clientIp, e);
            return false;
        }
    }

    /**
     * Manually unblock an IP address
     */
    public void unblockIpAddress(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return;
        }
        
        try {
            String blockKey = "blocked_ip:" + clientIp;
            redisTemplate.delete(blockKey);
            log.info("Manually unblocked IP address: {}", clientIp);
        } catch (Exception e) {
            log.error("Failed to unblock IP address {}: ", clientIp, e);
        }
    }
}