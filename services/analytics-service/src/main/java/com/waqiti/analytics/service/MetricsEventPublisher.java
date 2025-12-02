package com.waqiti.analytics.service;

import com.waqiti.analytics.dto.MetricEvent;
import com.waqiti.analytics.dto.PerformanceMetric;
import com.waqiti.analytics.dto.BusinessMetric;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Metrics Event Publisher
 * 
 * Publishes various metrics events for real-time alerting and analytics
 * This service provides the missing producer for metric-events topic
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MetricsEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.metric-events:metric-events}")
    private String METRIC_EVENTS_TOPIC;

    /**
     * Publishes a performance metric event
     */
    @Async
    public CompletableFuture<Void> publishPerformanceMetric(
            String serviceName, String metricName, double value, Map<String, String> tags) {
        
        try {
            PerformanceMetric metric = PerformanceMetric.builder()
                .metricId(UUID.randomUUID().toString())
                .serviceName(serviceName)
                .metricName(metricName)
                .value(value)
                .tags(tags)
                .timestamp(Instant.now())
                .build();

            MetricEvent event = MetricEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("PERFORMANCE_METRIC")
                .metric(metric)
                .timestamp(Instant.now())
                .build();

            kafkaTemplate.send(METRIC_EVENTS_TOPIC, event.getEventId(), event);
            
            log.debug("Published performance metric: {} for service: {}", metricName, serviceName);
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Failed to publish performance metric: {}", metricName, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Publishes a business metric event
     */
    @Async
    public CompletableFuture<Void> publishBusinessMetric(
            String domain, String metricName, long value, Map<String, Object> metadata) {
        
        try {
            BusinessMetric metric = BusinessMetric.builder()
                .metricId(UUID.randomUUID().toString())
                .domain(domain)
                .metricName(metricName)
                .value(value)
                .metadata(metadata)
                .timestamp(Instant.now())
                .build();

            MetricEvent event = MetricEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("BUSINESS_METRIC")
                .businessMetric(metric)
                .timestamp(Instant.now())
                .build();

            kafkaTemplate.send(METRIC_EVENTS_TOPIC, event.getEventId(), event);
            
            log.debug("Published business metric: {} for domain: {}", metricName, domain);
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Failed to publish business metric: {}", metricName, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Publishes an alert metric event
     */
    @Async
    public CompletableFuture<Void> publishAlertMetric(
            String serviceName, String alertType, String severity, String message) {
        
        try {
            MetricEvent event = MetricEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("ALERT_METRIC")
                .serviceName(serviceName)
                .alertType(alertType)
                .severity(severity)
                .message(message)
                .timestamp(Instant.now())
                .build();

            kafkaTemplate.send(METRIC_EVENTS_TOPIC, event.getEventId(), event);
            
            log.warn("Published alert metric: {} for service: {} (severity: {})", 
                alertType, serviceName, severity);
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Failed to publish alert metric: {}", alertType, e);
            return CompletableFuture.failedFuture(e);
        }
    }
}