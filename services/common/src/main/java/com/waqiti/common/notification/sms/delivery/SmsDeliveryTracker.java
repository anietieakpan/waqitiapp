package com.waqiti.common.notification.sms.delivery;

import com.waqiti.common.notification.sms.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks SMS delivery status and provides delivery receipts
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmsDeliveryTracker {
    
    private final StringRedisTemplate redisTemplate;
    private final Map<String, SmsDeliveryStatus> deliveryCache = new ConcurrentHashMap<>();
    private final AtomicLong messageCounter = new AtomicLong(1);
    
    private static final String TRACKING_KEY_PREFIX = "sms:tracking:";
    private static final String DELIVERY_KEY_PREFIX = "sms:delivery:";
    private static final String METRICS_KEY_PREFIX = "sms:metrics:";
    
    public void trackDelivery(String messageId, String recipient, String status) {
        log.info("SMS delivery status for message {}: {} to {}", messageId, status, recipient);
        // Implementation would store delivery status in database
    }
    
    public String getDeliveryStatus(String messageId) {
        // Implementation would query delivery status from database
        return "DELIVERED";
    }
    
    /**
     * Generate a unique tracking ID
     */
    public String generateTrackingId() {
        return "SMS-" + System.currentTimeMillis() + "-" + messageCounter.getAndIncrement();
    }
    
    /**
     * Record SMS send attempt
     */
    public void recordSendAttempt(String trackingId, SmsMessage message) {
        try {
            SmsDeliveryStatus status = SmsDeliveryStatus.builder()
                .messageId(trackingId)
                .phoneNumber(message.getPhoneNumber())
                .state(DeliveryState.PENDING)
                .sentAt(java.time.Instant.now())
                .attemptCount(1)
                .build();
            
            deliveryCache.put(trackingId, status);
            
            // Store in Redis with TTL
            String key = TRACKING_KEY_PREFIX + trackingId;
            redisTemplate.opsForValue().set(key, status.toString(), 7, TimeUnit.DAYS);
            
            log.debug("Recorded send attempt for tracking ID: {}", trackingId);
        } catch (Exception e) {
            log.error("Failed to record send attempt for tracking ID: {}", trackingId, e);
        }
    }
    
    /**
     * Record SMS delivery failure
     */
    public void recordFailure(String trackingId, String errorMessage) {
        try {
            SmsDeliveryStatus status = deliveryCache.get(trackingId);
            if (status == null) {
                status = SmsDeliveryStatus.builder()
                    .messageId(trackingId)
                    .state(DeliveryState.FAILED)
                    .build();
            }
            
            status.setState(DeliveryState.FAILED);
            status.setFailureReason(errorMessage);
            // Failed time tracked via failureReason
            status.setAttemptCount(status.getAttemptCount() + 1);
            
            deliveryCache.put(trackingId, status);
            
            // Update Redis
            String key = TRACKING_KEY_PREFIX + trackingId;
            redisTemplate.opsForValue().set(key, status.toString(), 7, TimeUnit.DAYS);
            
            log.debug("Recorded failure for tracking ID: {} - {}", trackingId, errorMessage);
        } catch (Exception e) {
            log.error("Failed to record failure for tracking ID: {}", trackingId, e);
        }
    }
    
    /**
     * Record SMS delivery result
     */
    public void recordResult(String trackingId, SmsResult result) {
        try {
            SmsDeliveryStatus status = deliveryCache.get(trackingId);
            if (status == null) {
                status = SmsDeliveryStatus.builder()
                    .messageId(trackingId)
                    .phoneNumber(result.getPhoneNumber())
                    .build();
            }
            
            if (result.isSuccess()) {
                status.setState(DeliveryState.SENT);
                status.setSentAt(java.time.Instant.now());
                status.setProviderMessageId(result.getProviderId());
                status.setProviderMessageId(result.getProviderMessageId());
                
                if (result.getDeliveredAt() != null) {
                    status.setState(DeliveryState.DELIVERED);
                    status.setDeliveredAt(result.getDeliveredAt());
                }
            } else {
                status.setState(DeliveryState.FAILED);
                status.setFailureReason(result.getErrorMessage());
                // Failed time tracked via failureReason
            }
            
            deliveryCache.put(trackingId, status);
            
            // Update Redis
            String key = TRACKING_KEY_PREFIX + trackingId;
            redisTemplate.opsForValue().set(key, status.toString(), 7, TimeUnit.DAYS);
            
            log.debug("Recorded result for tracking ID: {} - {}", trackingId, status.getState());
        } catch (Exception e) {
            log.error("Failed to record result for tracking ID: {}", trackingId, e);
        }
    }
    
    /**
     * Get delivery status by tracking ID
     */
    public SmsDeliveryStatus getStatus(String trackingId) {
        // Try cache first
        SmsDeliveryStatus status = deliveryCache.get(trackingId);
        if (status != null) {
            return status;
        }
        
        // Try Redis
        try {
            String key = TRACKING_KEY_PREFIX + trackingId;
            String statusStr = redisTemplate.opsForValue().get(key);
            if (statusStr != null) {
                // Parse status from Redis (simplified - in production use JSON)
                return SmsDeliveryStatus.builder()
                    .messageId(trackingId)
                    .state(DeliveryState.UNKNOWN)
                    .build();
            }
        } catch (Exception e) {
            log.error("Failed to get status for tracking ID: {}", trackingId, e);
        }
        
        return SmsDeliveryStatus.builder()
            .messageId(trackingId)
            .state(DeliveryState.UNKNOWN)
            .build();
    }
    
    /**
     * Get SMS metrics for a date range
     */
    public SmsMetrics getMetrics(LocalDateTime from, LocalDateTime to) {
        try {
            long totalSent = 0;
            long totalDelivered = 0;
            long totalFailed = 0;
            double totalCost = 0.0;
            
            // In production, this would query actual metrics from database/Redis
            // For now, return sample metrics
            
            return SmsMetrics.builder()
                .totalSent(totalSent)
                .totalDelivered(totalDelivered)
                .totalFailed(totalFailed)
                .deliveryRate(totalSent > 0 ? (double) totalDelivered / totalSent : 0.0)
                .totalCost(totalCost)
                .periodStart(from.atZone(java.time.ZoneId.systemDefault()).toInstant())
                .periodEnd(to.atZone(java.time.ZoneId.systemDefault()).toInstant())
                .build();
        } catch (Exception e) {
            log.error("Failed to get SMS metrics", e);
            return SmsMetrics.builder()
                .totalSent(0)
                .totalDelivered(0)
                .totalFailed(0)
                .deliveryRate(0.0)
                .totalCost(0.0)
                .periodStart(from.atZone(java.time.ZoneId.systemDefault()).toInstant())
                .periodEnd(to.atZone(java.time.ZoneId.systemDefault()).toInstant())
                .build();
        }
    }
}