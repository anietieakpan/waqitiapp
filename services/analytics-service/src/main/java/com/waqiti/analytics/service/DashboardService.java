package com.waqiti.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing real-time dashboard updates and widget data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final WebClient.Builder webClientBuilder;
    
    private static final String WIDGET_KEY_PREFIX = "dashboard:widget:";
    private static final String DASHBOARD_UPDATE_TOPIC = "dashboard-updates";
    
    /**
     * Update a real-time widget with new data
     */
    public void updateRealTimeWidget(String widgetId, Object value) {
        try {
            String key = WIDGET_KEY_PREFIX + widgetId;
            
            // Store in Redis for immediate access
            Map<String, Object> widgetData = new HashMap<>();
            widgetData.put("value", value);
            widgetData.put("timestamp", LocalDateTime.now());
            widgetData.put("widgetId", widgetId);
            
            redisTemplate.opsForHash().putAll(key, widgetData);
            redisTemplate.expire(key, 5, TimeUnit.MINUTES);
            
            // Publish update event for real-time subscribers
            publishWidgetUpdate(widgetId, value);
            
            log.debug("Updated widget {} with value: {}", widgetId, value);
            
        } catch (Exception e) {
            log.error("Failed to update widget {}: {}", widgetId, e.getMessage());
        }
    }
    
    /**
     * Update multiple widgets in a batch
     */
    public void updateWidgetBatch(Map<String, Object> widgetUpdates) {
        widgetUpdates.forEach(this::updateRealTimeWidget);
    }
    
    /**
     * Get current value of a widget
     */
    public Object getWidgetValue(String widgetId) {
        String key = WIDGET_KEY_PREFIX + widgetId;
        Map<Object, Object> widgetData = redisTemplate.opsForHash().entries(key);
        return widgetData.get("value");
    }
    
    /**
     * Update dashboard metrics
     */
    public void updateDashboardMetrics(Map<String, Object> metrics) {
        String key = "dashboard:metrics:current";
        redisTemplate.opsForHash().putAll(key, metrics);
        redisTemplate.expire(key, 10, TimeUnit.MINUTES);
        
        // Publish metrics update
        kafkaTemplate.send(DASHBOARD_UPDATE_TOPIC, "metrics", metrics);
    }
    
    /**
     * Send alert to dashboard for visual notification
     */
    public void sendDashboardAlert(String alertType, String message, String severity) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("type", alertType);
        alert.put("message", message);
        alert.put("severity", severity);
        alert.put("timestamp", LocalDateTime.now());
        
        // Store in Redis for dashboard to pick up
        String key = "dashboard:alerts:" + System.currentTimeMillis();
        redisTemplate.opsForValue().set(key, alert, 1, TimeUnit.HOURS);
        
        // Publish alert for real-time notification
        kafkaTemplate.send(DASHBOARD_UPDATE_TOPIC, "alert", alert);
    }
    
    /**
     * Update chart data for dashboard
     */
    public void updateChartData(String chartId, Object data) {
        Map<String, Object> chartUpdate = new HashMap<>();
        chartUpdate.put("chartId", chartId);
        chartUpdate.put("data", data);
        chartUpdate.put("timestamp", LocalDateTime.now());
        
        String key = "dashboard:chart:" + chartId;
        redisTemplate.opsForValue().set(key, chartUpdate, 30, TimeUnit.MINUTES);
        
        publishChartUpdate(chartId, data);
    }
    
    private void publishWidgetUpdate(String widgetId, Object value) {
        Map<String, Object> update = new HashMap<>();
        update.put("widgetId", widgetId);
        update.put("value", value);
        update.put("timestamp", LocalDateTime.now());
        
        kafkaTemplate.send(DASHBOARD_UPDATE_TOPIC, "widget", update);
    }
    
    private void publishChartUpdate(String chartId, Object data) {
        Map<String, Object> update = new HashMap<>();
        update.put("chartId", chartId);
        update.put("data", data);
        update.put("timestamp", LocalDateTime.now());
        
        kafkaTemplate.send(DASHBOARD_UPDATE_TOPIC, "chart", update);
    }
}