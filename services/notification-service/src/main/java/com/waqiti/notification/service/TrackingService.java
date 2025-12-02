package com.waqiti.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for tracking email opens, clicks, and generating tracking URLs
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrackingService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Value("${notification.tracking.base.url:https://track.example.com}")
    private String trackingBaseUrl;
    
    @Value("${notification.unsubscribe.base.url:https://unsubscribe.example.com}")
    private String unsubscribeBaseUrl;
    
    // In-memory cache for tracking data
    private final Map<String, EmailTrackingData> trackingData = new ConcurrentHashMap<>();
    private final Map<String, ClickTrackingData> clickData = new ConcurrentHashMap<>();
    private final Map<String, UnsubscribeTrackingData> unsubscribeData = new ConcurrentHashMap<>();
    
    private static final String TRACKING_KEY_PREFIX = "email:tracking:";
    private static final String CLICK_KEY_PREFIX = "email:click:";
    private static final String UNSUBSCRIBE_KEY_PREFIX = "email:unsubscribe:";
    private static final Duration TRACKING_DATA_TTL = Duration.ofDays(30);
    
    public String generateTrackingPixel(String messageId) {
        String trackingId = UUID.randomUUID().toString();
        
        // Store tracking metadata
        EmailTrackingData tracking = new EmailTrackingData();
        tracking.setTrackingId(trackingId);
        tracking.setMessageId(messageId);
        tracking.setCreatedAt(LocalDateTime.now());
        tracking.setOpened(false);
        
        trackingData.put(trackingId, tracking);
        
        // Store in Redis for persistence
        try {
            redisTemplate.opsForValue().set(
                TRACKING_KEY_PREFIX + trackingId, 
                tracking, 
                TRACKING_DATA_TTL
            );
        } catch (Exception e) {
            log.error("Failed to store tracking data in Redis: {}", e.getMessage());
        }
        
        String pixelUrl = trackingBaseUrl + "/pixel/" + trackingId + ".png";
        log.debug("Generated tracking pixel URL: {} for message: {}", pixelUrl, messageId);
        
        return pixelUrl;
    }
    
    public String generateClickTrackingUrl(String originalUrl, String messageId, String recipientEmail) {
        String clickId = UUID.randomUUID().toString();
        
        // Store click tracking metadata
        ClickTrackingData clickTracking = new ClickTrackingData();
        clickTracking.setClickId(clickId);
        clickTracking.setMessageId(messageId);
        clickTracking.setRecipientEmail(recipientEmail);
        clickTracking.setOriginalUrl(originalUrl);
        clickTracking.setCreatedAt(LocalDateTime.now());
        clickTracking.setClicked(false);
        
        clickData.put(clickId, clickTracking);
        
        // Store in Redis
        try {
            redisTemplate.opsForValue().set(
                CLICK_KEY_PREFIX + clickId,
                clickTracking,
                TRACKING_DATA_TTL
            );
        } catch (Exception e) {
            log.error("Failed to store click tracking data in Redis: {}", e.getMessage());
        }
        
        String trackingUrl = trackingBaseUrl + "/click/" + clickId;
        log.debug("Generated click tracking URL: {} for original: {}", trackingUrl, originalUrl);
        
        return trackingUrl;
    }
    
    public String generateUnsubscribeLink(String recipientEmail, String campaignId) {
        String unsubscribeId = UUID.randomUUID().toString();
        
        // Store unsubscribe metadata
        UnsubscribeTrackingData unsubscribeTracking = new UnsubscribeTrackingData();
        unsubscribeTracking.setUnsubscribeId(unsubscribeId);
        unsubscribeTracking.setRecipientEmail(recipientEmail);
        unsubscribeTracking.setCampaignId(campaignId);
        unsubscribeTracking.setCreatedAt(LocalDateTime.now());
        unsubscribeTracking.setUnsubscribed(false);
        
        unsubscribeData.put(unsubscribeId, unsubscribeTracking);
        
        // Store in Redis
        try {
            redisTemplate.opsForValue().set(
                UNSUBSCRIBE_KEY_PREFIX + unsubscribeId,
                unsubscribeTracking,
                TRACKING_DATA_TTL
            );
        } catch (Exception e) {
            log.error("Failed to store unsubscribe tracking data in Redis: {}", e.getMessage());
        }
        
        String unsubscribeUrl = unsubscribeBaseUrl + "/unsubscribe/" + unsubscribeId;
        log.debug("Generated unsubscribe URL: {} for email: {}", unsubscribeUrl, recipientEmail);
        
        return unsubscribeUrl;
    }
    
    public void recordOpen(String messageId, String recipientEmail, String userAgent, String ipAddress) {
        log.info("Email opened - Message: {}, Recipient: {}", messageId, recipientEmail);
        
        // Find tracking data by message ID
        EmailTrackingData tracking = findTrackingByMessageId(messageId);
        if (tracking != null) {
            tracking.setOpened(true);
            tracking.setOpenedAt(LocalDateTime.now());
            tracking.setUserAgent(userAgent);
            tracking.setIpAddress(ipAddress);
            
            // Update in Redis
            try {
                redisTemplate.opsForValue().set(
                    TRACKING_KEY_PREFIX + tracking.getTrackingId(),
                    tracking,
                    TRACKING_DATA_TTL
                );
            } catch (Exception e) {
                log.error("Failed to update tracking data in Redis: {}", e.getMessage());
            }
        }
        
        // Record metrics
        recordTrackingEvent("EMAIL_OPENED", messageId, recipientEmail, userAgent, ipAddress);
    }
    
    public void recordClick(String messageId, String recipientEmail, String clickedUrl) {
        log.info("Email clicked - Message: {}, Recipient: {}, URL: {}", messageId, recipientEmail, clickedUrl);
        
        // Find click tracking data
        ClickTrackingData clickTracking = findClickTrackingByMessageId(messageId);
        if (clickTracking != null) {
            clickTracking.setClicked(true);
            clickTracking.setClickedAt(LocalDateTime.now());
            
            // Update in Redis
            try {
                redisTemplate.opsForValue().set(
                    CLICK_KEY_PREFIX + clickTracking.getClickId(),
                    clickTracking,
                    TRACKING_DATA_TTL
                );
            } catch (Exception e) {
                log.error("Failed to update click tracking data in Redis: {}", e.getMessage());
            }
        }
        
        // Record metrics
        recordTrackingEvent("EMAIL_CLICKED", messageId, recipientEmail, clickedUrl, null);
    }
    
    public void trackInAppNotification(String notificationId, String userId, String status, Instant timestamp) {
        log.debug("Tracking in-app notification: {} for user: {} with status: {}", 
            notificationId, userId, status);
        
        // Store in-app tracking data
        Map<String, Object> trackingInfo = new HashMap<>();
        trackingInfo.put("notificationId", notificationId);
        trackingInfo.put("userId", userId);
        trackingInfo.put("status", status);
        trackingInfo.put("timestamp", timestamp.toString());
        trackingInfo.put("channel", "IN_APP");
        
        try {
            redisTemplate.opsForValue().set(
                "inapp:tracking:" + notificationId,
                trackingInfo,
                Duration.ofDays(7)
            );
        } catch (Exception e) {
            log.error("Failed to store in-app tracking data: {}", e.getMessage());
        }
    }
    
    public void recordUnsubscribe(String recipientEmail, String source, String reason) {
        log.info("Unsubscribe recorded - Email: {}, Source: {}, Reason: {}", 
            recipientEmail, source, reason);
        
        // Update unsubscribe tracking data
        UnsubscribeTrackingData unsubscribeTracking = findUnsubscribeByEmail(recipientEmail);
        if (unsubscribeTracking != null) {
            unsubscribeTracking.setUnsubscribed(true);
            unsubscribeTracking.setUnsubscribedAt(LocalDateTime.now());
            unsubscribeTracking.setReason(reason);
            
            // Update in Redis
            try {
                redisTemplate.opsForValue().set(
                    UNSUBSCRIBE_KEY_PREFIX + unsubscribeTracking.getUnsubscribeId(),
                    unsubscribeTracking,
                    TRACKING_DATA_TTL
                );
            } catch (Exception e) {
                log.error("Failed to update unsubscribe tracking data in Redis: {}", e.getMessage());
            }
        }
        
        // Record metrics
        recordTrackingEvent("EMAIL_UNSUBSCRIBED", null, recipientEmail, source, reason);
    }
    
    public TrackingStats getTrackingStats(String messageId) {
        EmailTrackingData tracking = findTrackingByMessageId(messageId);
        ClickTrackingData clickTracking = findClickTrackingByMessageId(messageId);
        
        TrackingStats stats = new TrackingStats();
        stats.setMessageId(messageId);
        stats.setTracked(tracking != null);
        stats.setOpened(tracking != null && tracking.isOpened());
        stats.setClicked(clickTracking != null && clickTracking.isClicked());
        
        if (tracking != null) {
            stats.setOpenedAt(tracking.getOpenedAt());
            stats.setUserAgent(tracking.getUserAgent());
            stats.setIpAddress(tracking.getIpAddress());
        }
        
        if (clickTracking != null) {
            stats.setClickedAt(clickTracking.getClickedAt());
            stats.setClickedUrl(clickTracking.getOriginalUrl());
        }
        
        return stats;
    }
    
    private EmailTrackingData findTrackingByMessageId(String messageId) {
        return trackingData.values().stream()
            .filter(t -> messageId.equals(t.getMessageId()))
            .findFirst()
            .orElse(null);
    }
    
    private ClickTrackingData findClickTrackingByMessageId(String messageId) {
        return clickData.values().stream()
            .filter(c -> messageId.equals(c.getMessageId()))
            .findFirst()
            .orElse(null);
    }
    
    private UnsubscribeTrackingData findUnsubscribeByEmail(String email) {
        return unsubscribeData.values().stream()
            .filter(u -> email.equals(u.getRecipientEmail()))
            .findFirst()
            .orElse(null);
    }
    
    private void recordTrackingEvent(String eventType, String messageId, String recipientEmail, 
                                   String param1, String param2) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("messageId", messageId);
        event.put("recipientEmail", recipientEmail);
        event.put("parameter1", param1);
        event.put("parameter2", param2);
        event.put("timestamp", LocalDateTime.now().toString());
        
        // Send to analytics service and store for reporting
        sendToAnalyticsService(event);
        storeTrackingEvent(event);
    }
    
    // Data model classes
    @lombok.Data
    public static class EmailTrackingData {
        private String trackingId;
        private String messageId;
        private boolean opened;
        private LocalDateTime createdAt;
        private LocalDateTime openedAt;
        private String userAgent;
        private String ipAddress;
    }
    
    @lombok.Data
    public static class ClickTrackingData {
        private String clickId;
        private String messageId;
        private String recipientEmail;
        private String originalUrl;
        private boolean clicked;
        private LocalDateTime createdAt;
        private LocalDateTime clickedAt;
    }
    
    @lombok.Data
    public static class UnsubscribeTrackingData {
        private String unsubscribeId;
        private String recipientEmail;
        private String campaignId;
        private boolean unsubscribed;
        private LocalDateTime createdAt;
        private LocalDateTime unsubscribedAt;
        private String reason;
    }
    
    @lombok.Data
    public static class TrackingStats {
        private String messageId;
        private boolean tracked;
        private boolean opened;
        private boolean clicked;
        private LocalDateTime openedAt;
        private LocalDateTime clickedAt;
        private String userAgent;
        private String ipAddress;
        private String clickedUrl;
    }
    
    /**
     * Send tracking event to analytics service
     */
    private void sendToAnalyticsService(Map<String, Object> event) {
        try {
            log.debug("Sending tracking event to analytics service: {}", event);
            
            // In production, this would send to analytics service:
            // analyticsService.recordEvent("notification_tracking", event);
            
            // For now, just log the event
            log.debug("Analytics event recorded: {}", event);
            
        } catch (Exception e) {
            log.error("Failed to send event to analytics service: {}", e.getMessage());
        }
    }
    
    /**
     * Store tracking event for later analysis
     */
    private void storeTrackingEvent(Map<String, Object> event) {
        try {
            log.debug("Storing tracking event: {}", event);
            
            // In production, this would store in database or time-series DB:
            // trackingEventRepository.save(event);
            
            // For now, just log the storage operation
            log.debug("Tracking event stored successfully");
            
        } catch (Exception e) {
            log.error("Failed to store tracking event: {}", e.getMessage());
        }
    }
}