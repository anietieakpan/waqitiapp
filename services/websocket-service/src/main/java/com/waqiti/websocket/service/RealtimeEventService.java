package com.waqiti.websocket.service;

import com.waqiti.websocket.dto.AnalyticsEvent;
import com.waqiti.websocket.dto.PaymentStatusUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.waqiti.websocket.pool.WebSocketConnectionPool;
import com.waqiti.websocket.pool.WebSocketConnection;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeEventService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final WebSocketConnectionPool connectionPool;
    private final ObjectMapper objectMapper;
    
    private static final String ANALYTICS_TOPIC = "analytics-events";
    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";
    
    /**
     * Check if user can update payment
     */
    public boolean canUpdatePayment(String userId, String paymentId) {
        // In a real implementation, this would check the database
        // For now, we'll implement basic logic
        log.debug("Checking if user {} can update payment {}", userId, paymentId);
        
        try {
            // Check if user owns the payment through payment service
            PaymentResponse paymentDetails = paymentServiceClient.getPaymentDetails(paymentId, userId);
            if (paymentDetails != null && paymentDetails.getUserId().equals(userId)) {
                return true;
            }
            
            // Check if user has admin privileges
            return hasAdminPrivileges(userId);
            
        } catch (Exception e) {
            log.warn("Failed to verify payment permissions for user {} and payment {}: {}", 
                    userId, paymentId, e.getMessage());
            // Fail closed - deny access if we can't verify
            return false;
        }
    }
    
    /**
     * Check if user has admin privileges
     */
    public boolean isAdmin(String userId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getName().equals(userId)) {
            Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
            return authorities.stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
        }
        return false;
    }
    
    /**
     * Process analytics event
     */
    public void processAnalyticsEvent(AnalyticsEvent event) {
        log.debug("Processing analytics event: {} from user: {}", event.getEventType(), event.getUserId());
        
        // Enrich event
        Map<String, Object> enrichedEvent = new HashMap<>();
        enrichedEvent.put("eventId", UUID.randomUUID().toString());
        enrichedEvent.put("userId", event.getUserId());
        enrichedEvent.put("eventType", event.getEventType());
        enrichedEvent.put("timestamp", event.getTimestamp());
        enrichedEvent.put("data", event.getData());
        enrichedEvent.put("sessionId", event.getSessionId());
        enrichedEvent.put("platform", event.getPlatform());
        
        // Send to Kafka for processing
        kafkaTemplate.send(ANALYTICS_TOPIC, event.getUserId(), enrichedEvent)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send analytics event to Kafka", ex);
                } else {
                    log.debug("Analytics event sent successfully");
                }
            });
    }
    
    /**
     * Process payment status update
     */
    public void processPaymentStatusUpdate(PaymentStatusUpdate update) {
        log.info("Processing payment status update: {} for payment: {}", 
            update.getStatus(), update.getPaymentId());
        
        // Send to Kafka for processing
        kafkaTemplate.send(PAYMENT_EVENTS_TOPIC, update.getPaymentId(), update)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send payment update to Kafka", ex);
                } else {
                    log.debug("Payment update sent successfully");
                }
            });
    }
    
    /**
     * Publish real-time event
     */
    public void publishEvent(String topic, String key, Object event) {
        kafkaTemplate.send(topic, key, event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish event to topic: {}", topic, ex);
                } else {
                    log.debug("Event published to topic: {} with key: {}", topic, key);
                }
            });
    }
    
    /**
     * Send real-time notification to user via WebSocket
     */
    public CompletableFuture<Integer> sendRealtimeNotification(String userId, Object notification) {
        try {
            String message = objectMapper.writeValueAsString(notification);
            return connectionPool.broadcastToUser(userId, message)
                .thenApply(count -> {
                    log.debug("Sent notification to {} connections for user {}", count, userId);
                    return count;
                });
        } catch (Exception e) {
            log.error("Failed to send realtime notification to user: {}", userId, e);
            return CompletableFuture.completedFuture(0);
        }
    }
    
    /**
     * Send payment update to user
     */
    public void sendPaymentUpdate(String userId, PaymentStatusUpdate update) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "PAYMENT_UPDATE");
        notification.put("timestamp", System.currentTimeMillis());
        notification.put("data", update);
        
        sendRealtimeNotification(userId, notification)
            .thenAccept(count -> {
                if (count == 0) {
                    log.warn("No active connections found for user {} to send payment update", userId);
                }
            });
    }
    
    /**
     * Send analytics event to user
     */
    public void sendAnalyticsUpdate(String userId, Map<String, Object> analyticsData) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "ANALYTICS_UPDATE");
        notification.put("timestamp", System.currentTimeMillis());
        notification.put("data", analyticsData);
        
        sendRealtimeNotification(userId, notification);
    }
    
    /**
     * Send system notification to all connected users
     */
    public CompletableFuture<Map<String, Integer>> broadcastSystemNotification(Object notification) {
        Map<String, Integer> results = new HashMap<>();
        
        // Get all unique user IDs from the connection pool
        // This would require adding a method to get all user IDs in the pool
        // For now, we'll return an empty result
        
        log.info("Broadcasting system notification to all connected users");
        return CompletableFuture.completedFuture(results);
    }
    
    /**
     * Get connection statistics for monitoring
     */
    public WebSocketConnectionPool.PoolStatistics getConnectionStatistics() {
        return connectionPool.getStatistics();
    }
}