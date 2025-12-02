package com.waqiti.analytics.streaming;

import com.waqiti.analytics.model.UserEvent;
import com.waqiti.analytics.model.UserMetrics;
import com.waqiti.analytics.repository.UserEventRepository;
import com.waqiti.analytics.repository.UserMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Real-time event processor for user analytics
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventProcessor {
    
    private final UserEventRepository eventRepository;
    private final UserMetricsRepository metricsRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // In-memory cache for real-time metrics
    private final ConcurrentMap<String, UserMetrics> metricsCache = new ConcurrentHashMap<>();
    
    /**
     * Process incoming user event
     */
    public void processEvent(UserEvent event) {
        log.debug("Processing event: {} for user: {}", event.getEventName(), event.getUserId());
        
        try {
            // Update real-time metrics
            updateRealtimeMetrics(event);
            
            // Trigger real-time analytics
            triggerRealtimeAnalytics(event);
            
            // Check for anomalies
            checkForAnomalies(event);
            
            // Forward to downstream processors
            forwardToDownstreamProcessors(event);
            
        } catch (Exception e) {
            log.error("Error processing event for user {}: {}", event.getUserId(), e.getMessage(), e);
        }
    }
    
    /**
     * Process event asynchronously
     */
    public CompletableFuture<Void> processEventAsync(UserEvent event) {
        return CompletableFuture.runAsync(() -> processEvent(event));
    }
    
    /**
     * Listen to Kafka events
     */
    @KafkaListener(topics = "user-events", groupId = "analytics-event-processor")
    public void handleKafkaEvent(UserEvent event) {
        log.debug("Received Kafka event: {} for user: {}", event.getEventName(), event.getUserId());
        processEvent(event);
    }
    
    /**
     * Listen to session events
     */
    @KafkaListener(topics = "session-events", groupId = "analytics-session-processor")
    public void handleSessionEvent(Object sessionEvent) {
        log.debug("Received session event: {}", sessionEvent);
        // Process session events
        processSessionEvent(sessionEvent);
    }
    
    /**
     * Update real-time metrics for the user
     */
    private void updateRealtimeMetrics(UserEvent event) {
        String userId = event.getUserId();
        
        UserMetrics metrics = metricsCache.computeIfAbsent(userId, k -> {
            return metricsRepository.findByUserId(userId)
                    .orElse(new UserMetrics(userId));
        });
        
        // Update metrics based on event type
        metrics.incrementEventCount();
        metrics.setLastActivity(event.getTimestamp());
        
        switch (event.getEventName()) {
            case "transaction_completed":
                handleTransactionEvent(metrics, event);
                break;
            case "login":
                handleLoginEvent(metrics, event);
                break;
            case "logout":
                handleLogoutEvent(metrics, event);
                break;
            case "screen_view":
                handleScreenViewEvent(metrics, event);
                break;
            case "button_click":
                handleButtonClickEvent(metrics, event);
                break;
            case "page_view":
                handlePageViewEvent(metrics, event);
                break;
            case "feature_used":
                handleFeatureUsedEvent(metrics, event);
                break;
            case "error_occurred":
                handleErrorEvent(metrics, event);
                break;
            default:
                handleGenericEvent(metrics, event);
        }
        
        // Update cache
        metricsCache.put(userId, metrics);
        
        // Persist metrics periodically (every 100 events or 5 minutes)
        if (metrics.getEventCount() % 100 == 0) {
            persistMetricsAsync(metrics);
        }
    }
    
    /**
     * Handle transaction completion event
     */
    private void handleTransactionEvent(UserMetrics metrics, UserEvent event) {
        metrics.incrementTransactionCount();
        
        if (event.getEventProperties().containsKey("amount")) {
            try {
                BigDecimal amount = new BigDecimal(event.getEventProperties().get("amount").toString());
                metrics.addTransactionVolume(amount);
                
                // Update transaction statistics
                metrics.updateTransactionStats(amount);
            } catch (NumberFormatException e) {
                log.warn("Invalid transaction amount for user {}: {}", 
                        event.getUserId(), event.getEventProperties().get("amount"));
            }
        }
    }
    
    /**
     * Handle login event
     */
    private void handleLoginEvent(UserMetrics metrics, UserEvent event) {
        metrics.incrementLoginCount();
        metrics.setLastLoginTime(event.getTimestamp());
        
        // Track login platform
        if (event.getPlatform() != null) {
            metrics.addPlatformUsage(event.getPlatform());
        }
        
        // Update engagement score
        metrics.incrementEngagementScore(2.0); // Login adds 2 points
    }
    
    /**
     * Handle logout event
     */
    private void handleLogoutEvent(UserMetrics metrics, UserEvent event) {
        metrics.setLastLogoutTime(event.getTimestamp());
    }
    
    /**
     * Handle screen view event
     */
    private void handleScreenViewEvent(UserMetrics metrics, UserEvent event) {
        metrics.incrementScreenViews();
        
        // Track screen name
        if (event.getEventProperties().containsKey("screen_name")) {
            String screenName = event.getEventProperties().get("screen_name").toString();
            metrics.addScreenView(screenName);
        }
        
        // Update engagement score
        metrics.incrementEngagementScore(0.5); // Screen view adds 0.5 points
    }
    
    /**
     * Handle button click event
     */
    private void handleButtonClickEvent(UserMetrics metrics, UserEvent event) {
        metrics.incrementInteractionCount();
        
        // Track button name
        if (event.getEventProperties().containsKey("button_name")) {
            String buttonName = event.getEventProperties().get("button_name").toString();
            metrics.addButtonClick(buttonName);
        }
        
        // Update engagement score
        metrics.incrementEngagementScore(1.0); // Button click adds 1 point
    }
    
    /**
     * Handle page view event
     */
    private void handlePageViewEvent(UserMetrics metrics, UserEvent event) {
        metrics.incrementPageViews();
        
        // Track page URL
        if (event.getEventProperties().containsKey("page_url")) {
            String pageUrl = event.getEventProperties().get("page_url").toString();
            metrics.addPageView(pageUrl);
        }
    }
    
    /**
     * Handle feature usage event
     */
    private void handleFeatureUsedEvent(UserMetrics metrics, UserEvent event) {
        metrics.incrementFeatureUsageCount();
        
        // Track feature name
        if (event.getEventProperties().containsKey("feature_name")) {
            String featureName = event.getEventProperties().get("feature_name").toString();
            metrics.addFeatureUsage(featureName);
        }
        
        // Update engagement score
        metrics.incrementEngagementScore(1.5); // Feature usage adds 1.5 points
    }
    
    /**
     * Handle error event
     */
    private void handleErrorEvent(UserMetrics metrics, UserEvent event) {
        metrics.incrementErrorCount();
        
        // Track error type
        if (event.getEventProperties().containsKey("error_type")) {
            String errorType = event.getEventProperties().get("error_type").toString();
            metrics.addError(errorType);
        }
        
        // Decrease engagement score for errors
        metrics.decrementEngagementScore(0.5);
    }
    
    /**
     * Handle generic events
     */
    private void handleGenericEvent(UserMetrics metrics, UserEvent event) {
        // Add to custom event counts
        metrics.addCustomEvent(event.getEventName());
        
        // Small engagement boost for any activity
        metrics.incrementEngagementScore(0.1);
    }
    
    /**
     * Process session events
     */
    private void processSessionEvent(Object sessionEvent) {
        // Implementation for session event processing
        log.debug("Processing session event: {}", sessionEvent);
    }
    
    /**
     * Trigger real-time analytics processing
     */
    private void triggerRealtimeAnalytics(UserEvent event) {
        // Send event to real-time analytics pipeline
        kafkaTemplate.send("realtime-analytics", event);
    }
    
    /**
     * Check for anomalies in user behavior
     */
    private void checkForAnomalies(UserEvent event) {
        UserMetrics metrics = metricsCache.get(event.getUserId());
        if (metrics == null) return;
        
        // Check for unusual patterns
        if (isAnomalousEvent(event, metrics)) {
            log.warn("Anomalous event detected for user {}: {}", 
                    event.getUserId(), event.getEventName());
            
            // Send anomaly alert
            kafkaTemplate.send("anomaly-alerts", event);
        }
    }
    
    /**
     * Check if event is anomalous
     */
    private boolean isAnomalousEvent(UserEvent event, UserMetrics metrics) {
        // Simple anomaly detection rules
        
        // Check for rapid events
        if (metrics.getEventRate() > 50) { // More than 50 events per minute
            return true;
        }
        
        // Check for large transaction amounts
        if ("transaction_completed".equals(event.getEventName())) {
            if (event.getEventProperties().containsKey("amount")) {
                try {
                    BigDecimal amount = new BigDecimal(event.getEventProperties().get("amount").toString());
                    BigDecimal avgAmount = metrics.getAvgTransactionAmount();
                    
                    // If amount is 5x larger than average, it's anomalous
                    if (avgAmount.compareTo(BigDecimal.ZERO) > 0 && 
                        amount.compareTo(avgAmount.multiply(BigDecimal.valueOf(5))) > 0) {
                        return true;
                    }
                } catch (NumberFormatException e) {
                    // Invalid amount format is also anomalous
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Forward events to downstream processors
     */
    private void forwardToDownstreamProcessors(UserEvent event) {
        // Forward to different topics based on event type
        switch (event.getEventName()) {
            case "transaction_completed":
                kafkaTemplate.send("transaction-analytics", event);
                break;
            case "login":
            case "logout":
                kafkaTemplate.send("security-analytics", event);
                break;
            case "error_occurred":
                kafkaTemplate.send("error-analytics", event);
                break;
            default:
                kafkaTemplate.send("general-analytics", event);
        }
    }
    
    /**
     * Persist metrics asynchronously
     */
    @Transactional
    public void persistMetricsAsync(UserMetrics metrics) {
        CompletableFuture.runAsync(() -> {
            try {
                metricsRepository.save(metrics);
                log.debug("Persisted metrics for user: {}", metrics.getUserId());
            } catch (Exception e) {
                log.error("Error persisting metrics for user {}: {}", 
                        metrics.getUserId(), e.getMessage(), e);
            }
        });
    }
    
    /**
     * Flush all cached metrics to database
     */
    public void flushMetrics() {
        log.info("Flushing {} cached metrics to database", metricsCache.size());
        
        metricsCache.values().forEach(this::persistMetricsAsync);
        metricsCache.clear();
    }
    
    /**
     * Get real-time metrics for user
     */
    public UserMetrics getRealtimeMetrics(String userId) {
        return metricsCache.get(userId);
    }
    
    /**
     * Get cache size
     */
    public int getCacheSize() {
        return metricsCache.size();
    }
}