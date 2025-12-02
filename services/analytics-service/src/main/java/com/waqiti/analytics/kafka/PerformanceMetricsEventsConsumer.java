package com.waqiti.analytics.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.analytics.service.PerformanceMetricsService;
import com.waqiti.analytics.service.SystemMonitoringService;
import com.waqiti.common.exception.AnalyticsProcessingException;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka Consumer for Performance Metrics Events
 * Handles system performance monitoring, analytics, and alerting
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PerformanceMetricsEventsConsumer {
    
    private final PerformanceMetricsService metricsService;
    private final SystemMonitoringService monitoringService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler universalDLQHandler;
    
    @KafkaListener(
        topics = {"performance-metrics-events", "system-performance-measured", "threshold-exceeded", "performance-degraded"},
        groupId = "analytics-service-performance-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1500, multiplier = 2.0, maxDelay = 15000)
    )
    @Transactional
    public void handlePerformanceMetricsEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID metricsId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            metricsId = UUID.fromString((String) event.get("metricsId"));
            eventType = (String) event.get("eventType");
            String serviceName = (String) event.get("serviceName");
            String metricType = (String) event.get("metricType");
            BigDecimal metricValue = new BigDecimal((String) event.get("metricValue"));
            String unit = (String) event.get("unit");
            LocalDateTime timestamp = LocalDateTime.parse((String) event.get("timestamp"));
            
            log.info("Processing performance metrics event - MetricsId: {}, Type: {}, Service: {}, Metric: {}", 
                    metricsId, eventType, serviceName, metricType);
            
            switch (eventType) {
                case "SYSTEM_PERFORMANCE_MEASURED":
                    metricsService.recordPerformanceMetric(metricsId, serviceName, metricType,
                            metricValue, unit, timestamp);
                    break;
                case "THRESHOLD_EXCEEDED":
                    monitoringService.handleThresholdExceeded(metricsId, serviceName, metricType,
                            metricValue, timestamp);
                    break;
                case "PERFORMANCE_DEGRADED":
                    monitoringService.handlePerformanceDegradation(metricsId, serviceName,
                            metricType, metricValue, timestamp);
                    break;
                default:
                    metricsService.processGenericMetricsEvent(metricsId, eventType, event, timestamp);
            }
            
            metricsService.updateSystemDashboard(serviceName, metricType, metricValue, timestamp);
            
            auditService.auditFinancialEvent(
                    "PERFORMANCE_METRICS_EVENT_PROCESSED",
                    serviceName,
                    String.format("Performance metrics event processed - Type: %s, Metric: %s, Value: %s", 
                            eventType, metricType, metricValue),
                    Map.of(
                            "metricsId", metricsId.toString(),
                            "eventType", eventType,
                            "serviceName", serviceName,
                            "metricType", metricType,
                            "metricValue", metricValue.toString(),
                            "unit", unit
                    )
            );
            
            acknowledgment.acknowledge();
            log.info("Successfully processed performance metrics event - MetricsId: {}, EventType: {}", 
                    metricsId, eventType);
            
        } catch (Exception e) {
            log.error("Performance metrics event processing failed - MetricsId: {}, Error: {}",
                    metricsId, e.getMessage(), e);

            // Send to DLQ via UniversalDLQHandler
            try {
                org.apache.kafka.clients.consumer.ConsumerRecord<String, String> consumerRecord =
                    new org.apache.kafka.clients.consumer.ConsumerRecord<>(
                        topic, partition, offset, null, eventJson
                    );
                universalDLQHandler.handleFailedMessage(consumerRecord, e);
            } catch (Exception dlqException) {
                log.error("Failed to send message to DLQ: {}", dlqException.getMessage());
            }

            throw new AnalyticsProcessingException("Performance metrics event processing failed", e);
        }
    }
}