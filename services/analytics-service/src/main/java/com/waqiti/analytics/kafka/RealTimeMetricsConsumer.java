package com.waqiti.analytics.kafka;

import com.waqiti.analytics.service.RealTimeAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Real-Time Metrics Consumer
 *
 * Migrated from real-time-analytics-service
 * Consumes real-time metric events for live dashboards and monitoring
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0 (Consolidated)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RealTimeMetricsConsumer {

    private final RealTimeAnalyticsService realTimeAnalyticsService;

    /**
     * Process real-time metric events
     *
     * Metrics include:
     * - Transaction volume and throughput
     * - System performance indicators
     * - User activity metrics
     * - Error rates and anomalies
     */
    @KafkaListener(
        topics = "${kafka.topics.real-time-metrics:real-time-metrics}",
        groupId = "${kafka.consumer.group-id:analytics-service}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void processRealTimeMetric(
            @Payload Map<String, Object> metricData,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        try {
            log.debug("Processing real-time metric from topic: {}, partition: {}, offset: {}",
                topic, partition, offset);

            // Extract metric details
            String metricName = (String) metricData.get("name");
            Object metricValue = metricData.get("value");
            Long timestamp = (Long) metricData.getOrDefault("timestamp", System.currentTimeMillis());
            String source = (String) metricData.getOrDefault("source", "unknown");

            if (metricName == null || metricValue == null) {
                log.warn("Invalid metric data received: missing name or value");
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing metric: {} = {} from source: {}", metricName, metricValue, source);

            // Route metric to appropriate handler based on type
            processMetricByType(metricName, metricValue, timestamp, source, metricData);

            // Acknowledge successful processing
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing real-time metric from topic: {}, offset: {}",
                topic, offset, e);
            // Don't acknowledge on error - message will be retried or sent to DLQ
        }
    }

    /**
     * Route metric processing based on metric type
     */
    private void processMetricByType(String metricName, Object metricValue,
                                     Long timestamp, String source, Map<String, Object> fullData) {

        switch (metricName.toLowerCase()) {
            case "transaction_volume":
            case "tps":
            case "transactions_per_second":
                processTransactionMetric(metricName, metricValue, timestamp);
                break;

            case "error_rate":
            case "failure_rate":
                processErrorRateMetric(metricName, metricValue, timestamp);
                break;

            case "response_time":
            case "latency":
            case "p95_latency":
            case "p99_latency":
                processLatencyMetric(metricName, metricValue, timestamp);
                break;

            case "active_users":
            case "concurrent_users":
            case "session_count":
                processUserActivityMetric(metricName, metricValue, timestamp);
                break;

            case "cpu_usage":
            case "memory_usage":
            case "disk_usage":
                processSystemMetric(metricName, metricValue, timestamp, source);
                break;

            case "queue_depth":
            case "consumer_lag":
            case "kafka_lag":
                processQueueMetric(metricName, metricValue, timestamp);
                break;

            default:
                processGenericMetric(metricName, metricValue, timestamp, fullData);
        }
    }

    private void processTransactionMetric(String metricName, Object value, Long timestamp) {
        log.debug("Processing transaction metric: {} = {}", metricName, value);
        // Update in-memory transaction counters
        // This integrates with existing RealTimeAnalyticsService
        realTimeAnalyticsService.ingestMetrics(java.util.List.of(
            java.util.Map.of(
                "name", metricName,
                "value", value,
                "timestamp", timestamp,
                "category", "transaction"
            )
        ));
    }

    private void processErrorRateMetric(String metricName, Object value, Long timestamp) {
        log.debug("Processing error rate metric: {} = {}", metricName, value);

        double errorRate = convertToDouble(value);

        // Check if error rate exceeds threshold
        if (errorRate > 0.05) { // 5% threshold
            log.warn("High error rate detected: {}%", errorRate * 100);
            // Trigger alert via RealTimeAnalyticsService
        }

        realTimeAnalyticsService.ingestMetrics(java.util.List.of(
            java.util.Map.of(
                "name", metricName,
                "value", value,
                "timestamp", timestamp,
                "category", "error"
            )
        ));
    }

    private void processLatencyMetric(String metricName, Object value, Long timestamp) {
        log.debug("Processing latency metric: {} = {}", metricName, value);

        long latencyMs = convertToLong(value);

        // Check if latency exceeds threshold
        if (latencyMs > 5000) { // 5 seconds threshold
            log.warn("High latency detected: {}ms for metric: {}", latencyMs, metricName);
        }

        realTimeAnalyticsService.ingestMetrics(java.util.List.of(
            java.util.Map.of(
                "name", metricName,
                "value", value,
                "timestamp", timestamp,
                "category", "performance"
            )
        ));
    }

    private void processUserActivityMetric(String metricName, Object value, Long timestamp) {
        log.debug("Processing user activity metric: {} = {}", metricName, value);

        realTimeAnalyticsService.ingestMetrics(java.util.List.of(
            java.util.Map.of(
                "name", metricName,
                "value", value,
                "timestamp", timestamp,
                "category", "user_activity"
            )
        ));
    }

    private void processSystemMetric(String metricName, Object value, Long timestamp, String source) {
        log.debug("Processing system metric: {} = {} from {}", metricName, value, source);

        double usage = convertToDouble(value);

        // Check if system resource usage is critical
        if (usage > 0.90) { // 90% threshold
            log.warn("Critical resource usage detected: {} at {}% for source: {}",
                metricName, usage * 100, source);
        }

        realTimeAnalyticsService.ingestMetrics(java.util.List.of(
            java.util.Map.of(
                "name", metricName,
                "value", value,
                "timestamp", timestamp,
                "category", "system",
                "source", source
            )
        ));
    }

    private void processQueueMetric(String metricName, Object value, Long timestamp) {
        log.debug("Processing queue metric: {} = {}", metricName, value);

        long queueDepth = convertToLong(value);

        // Check if queue depth is concerning
        if (queueDepth > 10000) {
            log.warn("High queue depth detected: {} messages for metric: {}",
                queueDepth, metricName);
        }

        realTimeAnalyticsService.ingestMetrics(java.util.List.of(
            java.util.Map.of(
                "name", metricName,
                "value", value,
                "timestamp", timestamp,
                "category", "queue"
            )
        ));
    }

    private void processGenericMetric(String metricName, Object value,
                                      Long timestamp, Map<String, Object> fullData) {
        log.debug("Processing generic metric: {} = {}", metricName, value);

        realTimeAnalyticsService.ingestMetrics(java.util.List.of(
            java.util.Map.of(
                "name", metricName,
                "value", value,
                "timestamp", timestamp,
                "category", "general",
                "metadata", fullData
            )
        ));
    }

    // Helper methods

    private double convertToDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Cannot convert value to double: {}", value);
            return 0.0;
        }
    }

    private long convertToLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Cannot convert value to long: {}", value);
            return 0L;
        }
    }
}
