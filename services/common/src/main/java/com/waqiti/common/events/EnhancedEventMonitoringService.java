package com.waqiti.common.events;

import io.micrometer.core.instrument.*;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Enhanced Event Monitoring Service
 * Provides comprehensive monitoring, alerting, and analytics
 * for the Waqiti event-driven architecture
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnhancedEventMonitoringService {

    private final MeterRegistry meterRegistry;
    private final EventRegistryService eventRegistry;
    
    private final Map<String, EventMetrics> eventMetrics = new ConcurrentHashMap<>();
    private final Map<String, TopicMetrics> topicMetrics = new ConcurrentHashMap<>();
    private final Map<String, ConsumerMetrics> consumerMetrics = new ConcurrentHashMap<>();
    private final Map<String, List<EventTrace>> eventTraces = new ConcurrentHashMap<>();
    private final Map<String, Alert> activeAlerts = new ConcurrentHashMap<>();
    
    private final AtomicLong totalEventsProcessed = new AtomicLong(0);
    private final AtomicLong totalEventsFailed = new AtomicLong(0);
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Enhanced Event Monitoring Service");
        
        // Register custom metrics
        registerCustomMetrics();
        
        // Initialize built-in event metrics
        initializeBuiltInMetrics();
        
        log.info("Enhanced Event Monitoring Service initialized");
    }

    /**
     * Record event processing metrics
     */
    public void recordEventProcessing(EventProcessingMetrics metrics) {
        String eventType = metrics.getEventType();
        
        // Update event-specific metrics
        EventMetrics eventMetric = eventMetrics.computeIfAbsent(eventType, k -> new EventMetrics());
        eventMetric.recordProcessing(metrics);
        
        // Update global counters
        totalEventsProcessed.incrementAndGet();
        if (!metrics.isSuccess()) {
            totalEventsFailed.incrementAndGet();
        }
        
        // Record to Micrometer
        recordMicrometerMetrics(metrics);
        
        // Store event trace
        storeEventTrace(metrics);
        
        // Check for alerts
        checkAlerts(metrics);
        
        log.debug("Recorded event processing: eventType={}, success={}, duration={}ms", 
                eventType, metrics.isSuccess(), metrics.getProcessingDuration().toMillis());
    }

    /**
     * Record topic-level metrics
     */
    public void recordTopicMetrics(String topic, int partition, long offset, int messageSize) {
        TopicMetrics topicMetric = topicMetrics.computeIfAbsent(topic, k -> new TopicMetrics(topic));
        topicMetric.recordMessage(partition, offset, messageSize);
        
        // Record to Micrometer
        meterRegistry.counter("events.topic.messages",
                Tags.of("topic", topic, "partition", String.valueOf(partition)))
                .increment();
        
        meterRegistry.gauge("events.topic.offset",
                Tags.of("topic", topic, "partition", String.valueOf(partition)),
                offset);
    }

    /**
     * Record consumer-level metrics
     */
    public void recordConsumerMetrics(String consumerGroup, String topic, Duration lagTime, long lagCount) {
        String key = consumerGroup + ":" + topic;
        ConsumerMetrics consumerMetric = consumerMetrics.computeIfAbsent(key, k -> new ConsumerMetrics(consumerGroup, topic));
        consumerMetric.recordLag(lagTime, lagCount);
        
        // Record to Micrometer
        meterRegistry.gauge("events.consumer.lag.time",
                Tags.of("consumer_group", consumerGroup, "topic", topic),
                lagTime.toMillis());
        
        meterRegistry.gauge("events.consumer.lag.count",
                Tags.of("consumer_group", consumerGroup, "topic", topic),
                lagCount);
    }

    /**
     * Get real-time monitoring dashboard data
     */
    public MonitoringDashboard getDashboard() {
        return MonitoringDashboard.builder()
                .timestamp(Instant.now())
                .totalEventsProcessed(totalEventsProcessed.get())
                .totalEventsFailed(totalEventsFailed.get())
                .successRate(calculateOverallSuccessRate())
                .averageProcessingTime(calculateAverageProcessingTime())
                .eventTypeMetrics(getEventTypeMetrics())
                .topicMetrics(getTopicMetricsSnapshot())
                .consumerMetrics(getConsumerMetricsSnapshot())
                .activeAlerts(new ArrayList<>(activeAlerts.values()))
                .topEventsByVolume(getTopEventsByVolume(10))
                .slowestEvents(getSlowestEvents(10))
                .build();
    }

    /**
     * Get event flow analysis
     */
    public EventFlowAnalysis getEventFlowAnalysis(String eventType, Duration timeWindow) {
        List<EventTrace> traces = eventTraces.getOrDefault(eventType, new ArrayList<>())
                .stream()
                .filter(trace -> trace.getTimestamp().isAfter(Instant.now().minus(timeWindow)))
                .collect(Collectors.toList());
        
        return EventFlowAnalysis.builder()
                .eventType(eventType)
                .timeWindow(timeWindow)
                .totalEvents(traces.size())
                .successfulEvents(traces.stream().mapToInt(t -> t.isSuccess() ? 1 : 0).sum())
                .averageProcessingTime(traces.stream()
                        .mapToLong(t -> t.getProcessingDuration().toMillis())
                        .average()
                        .orElse(0.0))
                .processingTimePercentiles(calculateProcessingTimePercentiles(traces))
                .errorDistribution(getErrorDistribution(traces))
                .throughputOverTime(getThroughputOverTime(traces, timeWindow))
                .build();
    }

    /**
     * Get system health status
     */
    public SystemHealthStatus getSystemHealth() {
        double successRate = calculateOverallSuccessRate();
        double avgProcessingTime = calculateAverageProcessingTime();
        int activeAlertsCount = activeAlerts.size();
        
        HealthStatus status;
        if (successRate >= 99.0 && avgProcessingTime <= 100 && activeAlertsCount == 0) {
            status = HealthStatus.HEALTHY;
        } else if (successRate >= 95.0 && avgProcessingTime <= 500 && activeAlertsCount <= 5) {
            status = HealthStatus.DEGRADED;
        } else {
            status = HealthStatus.UNHEALTHY;
        }
        
        return SystemHealthStatus.builder()
                .status(status)
                .successRate(successRate)
                .averageProcessingTime(avgProcessingTime)
                .activeAlertsCount(activeAlertsCount)
                .totalEventsProcessed(totalEventsProcessed.get())
                .checkedAt(Instant.now())
                .details(generateHealthDetails())
                .build();
    }

    /**
     * Create custom alert
     */
    public void createAlert(AlertDefinition alertDefinition) {
        log.info("Creating alert: {}", alertDefinition.getName());
        
        Alert alert = Alert.builder()
                .id(UUID.randomUUID().toString())
                .name(alertDefinition.getName())
                .description(alertDefinition.getDescription())
                .condition(alertDefinition.getCondition())
                .severity(alertDefinition.getSeverity())
                .enabled(true)
                .createdAt(Instant.now())
                .build();
        
        // Store alert (in production, this would be persisted)
        activeAlerts.put(alert.getId(), alert);
    }

    /**
     * Monitor Kafka events (example listener)
     */
    @KafkaListener(topics = {"payment-events", "user-events", "security-events", "system-events"})
    public void monitorKafkaEvent(@Payload Object eventData,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                 @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                 @Header(KafkaHeaders.OFFSET) long offset,
                                 @Header(value = "eventType", required = false) String eventType) {
        
        Instant processingStart = Instant.now();
        
        try {
            // Extract event type from data if not in header
            if (eventType == null && eventData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> eventMap = (Map<String, Object>) eventData;
                eventType = (String) eventMap.get("eventType");
            }
            
            if (eventType == null) {
                eventType = "unknown";
            }
            
            // Calculate message size (approximate)
            int messageSize = eventData.toString().length();
            
            // Record metrics
            recordTopicMetrics(topic, partition, offset, messageSize);
            
            Duration processingTime = Duration.between(processingStart, Instant.now());
            
            recordEventProcessing(EventProcessingMetrics.builder()
                    .eventId(extractEventId(eventData))
                    .eventType(eventType)
                    .topic(topic)
                    .partition(partition)
                    .offset(offset)
                    .processingDuration(processingTime)
                    .success(true)
                    .timestamp(Instant.now())
                    .build());
                    
        } catch (Exception e) {
            Duration processingTime = Duration.between(processingStart, Instant.now());
            
            recordEventProcessing(EventProcessingMetrics.builder()
                    .eventId(extractEventId(eventData))
                    .eventType(eventType != null ? eventType : "unknown")
                    .topic(topic)
                    .partition(partition)
                    .offset(offset)
                    .processingDuration(processingTime)
                    .success(false)
                    .error(e.getMessage())
                    .timestamp(Instant.now())
                    .build());
            
            log.error("Error monitoring Kafka event: topic={}, partition={}, offset={}", 
                    topic, partition, offset, e);
        }
    }

    /**
     * Periodic health checks and alerting
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void performHealthChecks() {
        try {
            // Check event processing rates
            checkEventProcessingRates();
            
            // Check consumer lag
            checkConsumerLag();
            
            // Check error rates
            checkErrorRates();
            
            // Clean up old traces and metrics
            cleanupOldData();
            
        } catch (Exception e) {
            log.error("Error during health checks", e);
        }
    }

    // Private helper methods

    private void registerCustomMetrics() {
        // Register gauges for real-time monitoring
        meterRegistry.gauge("events.monitoring.total.processed", totalEventsProcessed, AtomicLong::get);
        meterRegistry.gauge("events.monitoring.total.failed", totalEventsFailed, AtomicLong::get);
        meterRegistry.gauge("events.monitoring.success.rate", this, service -> service.calculateOverallSuccessRate());
        meterRegistry.gauge("events.monitoring.avg.processing.time", this, service -> service.calculateAverageProcessingTime());
        meterRegistry.gauge("events.monitoring.active.alerts", activeAlerts, Map::size);
    }

    private void initializeBuiltInMetrics() {
        // Initialize metrics for known event types
        for (String eventType : eventRegistry.getAllEventTypes()) {
            eventMetrics.put(eventType, new EventMetrics());
        }
    }

    private void recordMicrometerMetrics(EventProcessingMetrics metrics) {
        // Record event processing metrics
        meterRegistry.counter("events.processed",
                Tags.of("event_type", metrics.getEventType(),
                        "topic", metrics.getTopic(),
                        "status", metrics.isSuccess() ? "success" : "failure"))
                .increment();
        
        // Record processing duration
        meterRegistry.timer("events.processing.duration",
                Tags.of("event_type", metrics.getEventType(),
                        "topic", metrics.getTopic()))
                .record(metrics.getProcessingDuration());
        
        // Record throughput
        meterRegistry.counter("events.throughput",
                Tags.of("event_type", metrics.getEventType()))
                .increment();
    }

    private void storeEventTrace(EventProcessingMetrics metrics) {
        EventTrace trace = EventTrace.builder()
                .eventId(metrics.getEventId())
                .eventType(metrics.getEventType())
                .processingDuration(metrics.getProcessingDuration())
                .success(metrics.isSuccess())
                .error(metrics.getError())
                .timestamp(metrics.getTimestamp())
                .build();
        
        eventTraces.computeIfAbsent(metrics.getEventType(), k -> new ArrayList<>()).add(trace);
        
        // Keep only recent traces (last 1000 per event type)
        List<EventTrace> traces = eventTraces.get(metrics.getEventType());
        if (traces.size() > 1000) {
            traces.subList(0, traces.size() - 1000).clear();
        }
    }

    private void checkAlerts(EventProcessingMetrics metrics) {
        // Check for high processing time
        if (metrics.getProcessingDuration().toMillis() > 5000) {
            createAlert(AlertDefinition.builder()
                    .name("High Processing Time")
                    .description(String.format("Event %s took %dms to process", 
                            metrics.getEventType(), metrics.getProcessingDuration().toMillis()))
                    .condition("processing_time > 5000ms")
                    .severity(AlertSeverity.WARNING)
                    .build());
        }
        
        // Check for failures
        if (!metrics.isSuccess()) {
            createAlert(AlertDefinition.builder()
                    .name("Event Processing Failure")
                    .description(String.format("Event %s processing failed: %s", 
                            metrics.getEventType(), metrics.getError()))
                    .condition("success = false")
                    .severity(AlertSeverity.ERROR)
                    .build());
        }
    }

    private double calculateOverallSuccessRate() {
        long total = totalEventsProcessed.get();
        if (total == 0) return 100.0;
        
        long failed = totalEventsFailed.get();
        return ((double) (total - failed) / total) * 100.0;
    }

    private double calculateAverageProcessingTime() {
        return eventMetrics.values().stream()
                .mapToDouble(EventMetrics::getAverageProcessingTime)
                .average()
                .orElse(0.0);
    }

    private List<EventTypeMetric> getEventTypeMetrics() {
        return eventMetrics.entrySet().stream()
                .map(entry -> EventTypeMetric.builder()
                        .eventType(entry.getKey())
                        .totalCount(entry.getValue().getTotalCount())
                        .successCount(entry.getValue().getSuccessCount())
                        .failureCount(entry.getValue().getFailureCount())
                        .averageProcessingTime(entry.getValue().getAverageProcessingTime())
                        .build())
                .collect(Collectors.toList());
    }

    private List<TopicMetricSnapshot> getTopicMetricsSnapshot() {
        return topicMetrics.values().stream()
                .map(tm -> TopicMetricSnapshot.builder()
                        .topic(tm.getTopic())
                        .totalMessages(tm.getTotalMessages())
                        .totalSizeBytes(tm.getTotalSizeBytes())
                        .partitionOffsets(tm.getPartitionOffsets())
                        .build())
                .collect(Collectors.toList());
    }

    private List<ConsumerMetricSnapshot> getConsumerMetricsSnapshot() {
        return consumerMetrics.values().stream()
                .map(cm -> ConsumerMetricSnapshot.builder()
                        .consumerGroup(cm.getConsumerGroup())
                        .topic(cm.getTopic())
                        .currentLagTime(cm.getCurrentLagTime())
                        .currentLagCount(cm.getCurrentLagCount())
                        .averageLagTime(cm.getAverageLagTime())
                        .build())
                .collect(Collectors.toList());
    }

    private List<EventVolumeMetric> getTopEventsByVolume(int limit) {
        return eventMetrics.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().getTotalCount(), a.getValue().getTotalCount()))
                .limit(limit)
                .map(entry -> EventVolumeMetric.builder()
                        .eventType(entry.getKey())
                        .count(entry.getValue().getTotalCount())
                        .build())
                .collect(Collectors.toList());
    }

    private List<EventLatencyMetric> getSlowestEvents(int limit) {
        return eventMetrics.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue().getAverageProcessingTime(), a.getValue().getAverageProcessingTime()))
                .limit(limit)
                .map(entry -> EventLatencyMetric.builder()
                        .eventType(entry.getKey())
                        .averageLatency(entry.getValue().getAverageProcessingTime())
                        .build())
                .collect(Collectors.toList());
    }

    private Map<String, Double> calculateProcessingTimePercentiles(List<EventTrace> traces) {
        if (traces.isEmpty()) {
            return Map.of();
        }
        
        List<Double> times = traces.stream()
                .map(trace -> (double) trace.getProcessingDuration().toMillis())
                .sorted()
                .collect(Collectors.toList());
        
        int size = times.size();
        return Map.of(
                "p50", times.get(size * 50 / 100),
                "p95", times.get(size * 95 / 100),
                "p99", times.get(size * 99 / 100)
        );
    }

    private Map<String, Integer> getErrorDistribution(List<EventTrace> traces) {
        return traces.stream()
                .filter(trace -> !trace.isSuccess())
                .collect(Collectors.groupingBy(
                        trace -> trace.getError() != null ? trace.getError() : "Unknown Error",
                        Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                ));
    }

    private List<ThroughputDataPoint> getThroughputOverTime(List<EventTrace> traces, Duration timeWindow) {
        Instant now = Instant.now();
        Instant start = now.minus(timeWindow);
        
        // Group by minute
        Map<Instant, Long> throughputByMinute = traces.stream()
                .filter(trace -> trace.getTimestamp().isAfter(start))
                .collect(Collectors.groupingBy(
                        trace -> trace.getTimestamp().truncatedTo(ChronoUnit.MINUTES),
                        Collectors.counting()
                ));
        
        return throughputByMinute.entrySet().stream()
                .map(entry -> ThroughputDataPoint.builder()
                        .timestamp(entry.getKey())
                        .count(entry.getValue())
                        .build())
                .sorted(Comparator.comparing(ThroughputDataPoint::getTimestamp))
                .collect(Collectors.toList());
    }

    private void checkEventProcessingRates() {
        // Implementation for checking processing rate anomalies
    }

    private void checkConsumerLag() {
        // Implementation for checking consumer lag thresholds
    }

    private void checkErrorRates() {
        // Implementation for checking error rate thresholds
    }

    private void cleanupOldData() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(24));
        
        // Clean up event traces older than 24 hours
        eventTraces.values().forEach(traces -> 
                traces.removeIf(trace -> trace.getTimestamp().isBefore(cutoff)));
        
        // Clean up resolved alerts older than 24 hours
        activeAlerts.entrySet().removeIf(entry -> 
                entry.getValue().getResolvedAt() != null && 
                entry.getValue().getResolvedAt().isBefore(cutoff));
    }

    private String extractEventId(Object eventData) {
        if (eventData instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> eventMap = (Map<String, Object>) eventData;
            return (String) eventMap.get("eventId");
        }
        return null;
    }

    private Map<String, String> generateHealthDetails() {
        return Map.of(
                "totalEventTypes", String.valueOf(eventMetrics.size()),
                "activeTopics", String.valueOf(topicMetrics.size()),
                "activeConsumerGroups", String.valueOf(consumerMetrics.size())
        );
    }

    // Inner classes and enums

    @Data
    public static class EventMetrics {
        private long totalCount = 0;
        private long successCount = 0;
        private long failureCount = 0;
        private double totalProcessingTime = 0;
        private double averageProcessingTime = 0;
        
        public void recordProcessing(EventProcessingMetrics metrics) {
            totalCount++;
            if (metrics.isSuccess()) {
                successCount++;
            } else {
                failureCount++;
            }
            
            double processingTime = metrics.getProcessingDuration().toMillis();
            totalProcessingTime += processingTime;
            averageProcessingTime = totalProcessingTime / totalCount;
        }
    }

    @Data
    public static class TopicMetrics {
        private final String topic;
        private long totalMessages = 0;
        private long totalSizeBytes = 0;
        private final Map<Integer, Long> partitionOffsets = new HashMap<>();
        
        public TopicMetrics(String topic) {
            this.topic = topic;
        }
        
        public void recordMessage(int partition, long offset, int messageSize) {
            totalMessages++;
            totalSizeBytes += messageSize;
            partitionOffsets.put(partition, Math.max(partitionOffsets.getOrDefault(partition, 0L), offset));
        }
    }

    @Data
    public static class ConsumerMetrics {
        private final String consumerGroup;
        private final String topic;
        private Duration currentLagTime = Duration.ZERO;
        private long currentLagCount = 0;
        private double averageLagTime = 0;
        private long lagMeasurements = 0;
        
        public ConsumerMetrics(String consumerGroup, String topic) {
            this.consumerGroup = consumerGroup;
            this.topic = topic;
        }
        
        public void recordLag(Duration lagTime, long lagCount) {
            this.currentLagTime = lagTime;
            this.currentLagCount = lagCount;
            
            lagMeasurements++;
            averageLagTime = ((averageLagTime * (lagMeasurements - 1)) + lagTime.toMillis()) / lagMeasurements;
        }
    }

    // Additional data classes and enums would be defined here...
    // For brevity, including key ones:

    public enum HealthStatus {
        HEALTHY, DEGRADED, UNHEALTHY
    }

    public enum AlertSeverity {
        INFO, WARNING, ERROR, CRITICAL
    }

    @Data
    @Builder
    public static class EventProcessingMetrics {
        private String eventId;
        private String eventType;
        private String topic;
        private int partition;
        private long offset;
        private Duration processingDuration;
        private boolean success;
        private String error;
        private Instant timestamp;
    }

    @Data
    @Builder
    public static class MonitoringDashboard {
        private Instant timestamp;
        private long totalEventsProcessed;
        private long totalEventsFailed;
        private double successRate;
        private double averageProcessingTime;
        private List<EventTypeMetric> eventTypeMetrics;
        private List<TopicMetricSnapshot> topicMetrics;
        private List<ConsumerMetricSnapshot> consumerMetrics;
        private List<Alert> activeAlerts;
        private List<EventVolumeMetric> topEventsByVolume;
        private List<EventLatencyMetric> slowestEvents;
    }

    @Data
    @Builder
    public static class SystemHealthStatus {
        private HealthStatus status;
        private double successRate;
        private double averageProcessingTime;
        private int activeAlertsCount;
        private long totalEventsProcessed;
        private Instant checkedAt;
        private Map<String, String> details;
    }

    @Data
    @Builder
    public static class EventFlowAnalysis {
        private String eventType;
        private Duration timeWindow;
        private int totalEvents;
        private int successfulEvents;
        private double averageProcessingTime;
        private Map<String, Double> processingTimePercentiles;
        private Map<String, Integer> errorDistribution;
        private List<ThroughputDataPoint> throughputOverTime;
    }

    @Data
    @Builder
    public static class AlertDefinition {
        private String name;
        private String description;
        private String condition;
        private AlertSeverity severity;
    }

    @Data
    @Builder
    public static class Alert {
        private String id;
        private String name;
        private String description;
        private String condition;
        private AlertSeverity severity;
        private boolean enabled;
        private Instant createdAt;
        private Instant resolvedAt;
    }

    @Data
    @Builder
    public static class EventTrace {
        private String eventId;
        private String eventType;
        private Duration processingDuration;
        private boolean success;
        private String error;
        private Instant timestamp;
    }

    @Data
    @Builder
    public static class EventTypeMetric {
        private String eventType;
        private long totalCount;
        private long successCount;
        private long failureCount;
        private double averageProcessingTime;
    }

    @Data
    @Builder
    public static class TopicMetricSnapshot {
        private String topic;
        private long totalMessages;
        private long totalSizeBytes;
        private Map<Integer, Long> partitionOffsets;
    }

    @Data
    @Builder
    public static class ConsumerMetricSnapshot {
        private String consumerGroup;
        private String topic;
        private Duration currentLagTime;
        private long currentLagCount;
        private double averageLagTime;
    }

    @Data
    @Builder
    public static class EventVolumeMetric {
        private String eventType;
        private long count;
    }

    @Data
    @Builder
    public static class EventLatencyMetric {
        private String eventType;
        private double averageLatency;
    }

    @Data
    @Builder
    public static class ThroughputDataPoint {
        private Instant timestamp;
        private long count;
    }
}