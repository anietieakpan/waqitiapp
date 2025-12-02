package com.waqiti.common.monitoring;

import com.waqiti.common.kafka.ConsumerHealth;
import com.waqiti.common.kafka.KafkaEventTrackingService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive monitoring service for Kafka consumers
 * Provides real-time metrics, health checks, and alerting
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaConsumerMonitoringService {

    private final MeterRegistry meterRegistry;
    private final KafkaEventTrackingService eventTrackingService;
    private final AlertingService alertingService;

    // Metrics
    private Counter messagesProcessedCounter;
    private Counter messagesFailedCounter;
    private Timer messageProcessingTimer;
    private Gauge consumerLagGauge;
    private Gauge activeConsumersGauge;
    private Gauge dlqMessagesGauge;

    // Runtime monitoring
    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private final OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();

    // Consumer health tracking
    private final Map<String, ConsumerHealth> consumerHealthMap = new ConcurrentHashMap<>();
    private final AtomicLong totalMessagesProcessed = new AtomicLong(0);
    private final AtomicLong totalMessagesFailed = new AtomicLong(0);
    private final AtomicLong totalDlqMessages = new AtomicLong(0);

    @PostConstruct
    public void initializeMetrics() {
        log.info("Initializing Kafka consumer monitoring metrics");

        // Create core metrics
        messagesProcessedCounter = Counter.builder("kafka.consumer.messages.processed")
            .description("Total number of messages successfully processed")
            .tag("application", "waqiti")
            .register(meterRegistry);

        messagesFailedCounter = Counter.builder("kafka.consumer.messages.failed")
            .description("Total number of messages that failed processing")
            .tag("application", "waqiti")
            .register(meterRegistry);

        messageProcessingTimer = Timer.builder("kafka.consumer.processing.time")
            .description("Time taken to process messages")
            .tag("application", "waqiti")
            .register(meterRegistry);

        consumerLagGauge = Gauge.builder("kafka.consumer.lag.milliseconds", this, KafkaConsumerMonitoringService::getTotalConsumerLag)
            .description("Consumer lag in milliseconds")
            .tag("application", "waqiti")
            .register(meterRegistry);

        activeConsumersGauge = Gauge.builder("kafka.consumer.active.count", this, KafkaConsumerMonitoringService::getActiveConsumerCount)
            .description("Number of active consumers")
            .tag("application", "waqiti")
            .register(meterRegistry);

        dlqMessagesGauge = Gauge.builder("kafka.consumer.dlq.messages", this, KafkaConsumerMonitoringService::getDlqMessageCount)
            .description("Number of messages in DLQ")
            .tag("application", "waqiti")
            .register(meterRegistry);

        log.info("Kafka consumer monitoring metrics initialized successfully");
    }

    /**
     * Record message processing success
     */
    public void recordMessageProcessed(String consumerName, String topic, long processingTimeMs) {
        try {
            messagesProcessedCounter.increment();
            totalMessagesProcessed.incrementAndGet();
            
            messageProcessingTimer.record(processingTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            
            // Update consumer-specific metrics
            updateConsumerMetrics(consumerName, topic, processingTimeMs, true);
            
        } catch (Exception e) {
            log.error("Failed to record message processing metrics", e);
        }
    }

    /**
     * Record message processing failure
     */
    public void recordMessageFailed(String consumerName, String topic, Exception exception) {
        try {
            messagesFailedCounter.increment();
            totalMessagesFailed.incrementAndGet();
            
            // Update consumer-specific metrics
            updateConsumerMetrics(consumerName, topic, 0, false);
            
            // Send alert for high failure rates
            checkFailureRateAlert(consumerName, topic);
            
        } catch (Exception e) {
            log.error("Failed to record message failure metrics", e);
        }
    }

    /**
     * Record DLQ message
     */
    public void recordDlqMessage(String consumerName, String topic) {
        try {
            totalDlqMessages.incrementAndGet();
            
            Counter.builder("kafka.consumer.dlq.messages.topic")
                .tag("consumer", consumerName)
                .tag("topic", topic)
                .register(meterRegistry)
                .increment();
                
            // Send immediate alert for DLQ messages
            alertingService.sendDlqAlert(consumerName, topic);
            
        } catch (Exception e) {
            log.error("Failed to record DLQ message metrics", e);
        }
    }

    /**
     * Update consumer health information
     */
    public void updateConsumerHealth(String consumerName, ConsumerHealth health) {
        try {
            consumerHealthMap.put(consumerName, health);
            
            // Create health-specific metrics
            Gauge.builder("kafka.consumer.health.lag", health, h -> h.getLagMilliseconds() != null ? h.getLagMilliseconds() : 0)
                .tag("consumer", consumerName)
                .description("Consumer lag for specific consumer")
                .register(meterRegistry);
                
            Gauge.builder("kafka.consumer.health.processing_rate", health, h -> h.getProcessingRate() != null ? h.getProcessingRate() : 0)
                .tag("consumer", consumerName)
                .description("Processing rate for specific consumer")
                .register(meterRegistry);
                
            Gauge.builder("kafka.consumer.health.error_rate", health, h -> h.getErrorRate() != null ? h.getErrorRate() : 0)
                .tag("consumer", consumerName)
                .description("Error rate for specific consumer")
                .register(meterRegistry);
            
            // Check for health alerts
            checkHealthAlerts(consumerName, health);
            
        } catch (Exception e) {
            log.error("Failed to update consumer health for {}", consumerName, e);
        }
    }

    /**
     * Get total consumer lag across all consumers
     */
    private double getTotalConsumerLag() {
        return consumerHealthMap.values().stream()
            .mapToLong(health -> health.getLagMilliseconds() != null ? health.getLagMilliseconds() : 0)
            .sum();
    }

    /**
     * Get active consumer count
     */
    private double getActiveConsumerCount() {
        return consumerHealthMap.values().stream()
            .filter(ConsumerHealth::isOperational)
            .count();
    }

    /**
     * Get DLQ message count
     */
    private double getDlqMessageCount() {
        return totalDlqMessages.get();
    }

    /**
     * Update consumer-specific metrics
     */
    private void updateConsumerMetrics(String consumerName, String topic, long processingTimeMs, boolean success) {
        try {
            String metricName = success ? "kafka.consumer.messages.processed.consumer" : "kafka.consumer.messages.failed.consumer";
            
            Counter.builder(metricName)
                .tag("consumer", consumerName)
                .tag("topic", topic)
                .register(meterRegistry)
                .increment();
            
            if (success && processingTimeMs > 0) {
                Timer.builder("kafka.consumer.processing.time.consumer")
                    .tag("consumer", consumerName)
                    .tag("topic", topic)
                    .register(meterRegistry)
                    .record(processingTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            
        } catch (Exception e) {
            log.debug("Failed to update consumer metrics", e);
        }
    }

    /**
     * Check for failure rate alerts
     */
    private void checkFailureRateAlert(String consumerName, String topic) {
        try {
            ConsumerHealth health = consumerHealthMap.get(consumerName);
            if (health != null && health.getErrorRate() != null && health.getErrorRate() > 5.0) {
                alertingService.sendHighErrorRateAlert(consumerName, topic, health.getErrorRate());
            }
        } catch (Exception e) {
            log.debug("Failed to check failure rate alert", e);
        }
    }

    /**
     * Check for health-based alerts
     */
    private void checkHealthAlerts(String consumerName, ConsumerHealth health) {
        try {
            ConsumerHealth.HealthThresholds thresholds = ConsumerHealth.HealthThresholds.builder().build();
            
            if (health.hasCriticalIssues(thresholds)) {
                alertingService.sendConsumerHealthAlert(consumerName, health);
            }
            
            // Check for lag alerts
            if (health.getLagMilliseconds() != null && health.getLagMilliseconds() > 300000) { // 5 minutes
                alertingService.sendConsumerLagAlert(consumerName, health.getLagMilliseconds());
            }
            
        } catch (Exception e) {
            log.debug("Failed to check health alerts", e);
        }
    }

    /**
     * Scheduled health check for all consumers
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void performHealthCheck() {
        try {
            log.debug("Performing scheduled health check for {} consumers", consumerHealthMap.size());
            
            Instant now = Instant.now();
            ConsumerHealth.HealthThresholds thresholds = ConsumerHealth.HealthThresholds.builder().build();
            
            for (Map.Entry<String, ConsumerHealth> entry : consumerHealthMap.entrySet()) {
                String consumerName = entry.getKey();
                ConsumerHealth health = entry.getValue();
                
                // Check for stale heartbeats
                if (!health.hasRecentHeartbeat(thresholds.getHeartbeatTimeoutMs())) {
                    log.warn("Consumer {} has stale heartbeat (last: {})", consumerName, health.getLastHeartbeat());
                    alertingService.sendConsumerStaleHeartbeatAlert(consumerName, health.getLastHeartbeat());
                }
                
                // Update system metrics
                updateSystemMetrics(consumerName, health);
            }
            
        } catch (Exception e) {
            log.error("Error during scheduled health check", e);
        }
    }

    /**
     * Update system-level metrics
     */
    private void updateSystemMetrics(String consumerName, ConsumerHealth health) {
        try {
            // Memory metrics
            long usedMemory = memoryMXBean.getHeapMemoryUsage().getUsed() / (1024 * 1024); // MB
            long maxMemory = memoryMXBean.getHeapMemoryUsage().getMax() / (1024 * 1024); // MB
            
            Gauge.builder("system.memory.used.mb", this, m -> usedMemory)
                .tag("consumer", consumerName)
                .register(meterRegistry);
                
            Gauge.builder("system.memory.usage.percent", this, m -> maxMemory > 0 ? (usedMemory * 100.0 / maxMemory) : 0)
                .tag("consumer", consumerName)
                .register(meterRegistry);
            
            // CPU metrics (if available)
            if (osMXBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOsMXBean = 
                    (com.sun.management.OperatingSystemMXBean) osMXBean;
                
                double processCpuLoad = sunOsMXBean.getProcessCpuLoad() * 100;
                
                Gauge.builder("system.cpu.usage.percent", this, m -> processCpuLoad)
                    .tag("consumer", consumerName)
                    .register(meterRegistry);
            }
            
        } catch (Exception e) {
            log.debug("Failed to update system metrics", e);
        }
    }

    /**
     * Generate monitoring report
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void generateMonitoringReport() {
        try {
            Map<String, Object> summary = eventTrackingService.getConsumerPerformanceSummary();
            
            log.info("=== Kafka Consumer Monitoring Report ===");
            log.info("Active Consumers: {}", getActiveConsumerCount());
            log.info("Total Messages Processed: {}", totalMessagesProcessed.get());
            log.info("Total Messages Failed: {}", totalMessagesFailed.get());
            log.info("Total DLQ Messages: {}", totalDlqMessages.get());
            log.info("Total Consumer Lag: {}ms", getTotalConsumerLag());
            
            double successRate = totalMessagesProcessed.get() + totalMessagesFailed.get() > 0 ?
                (totalMessagesProcessed.get() * 100.0) / (totalMessagesProcessed.get() + totalMessagesFailed.get()) : 100.0;
            log.info("Success Rate: {:.2f}%", successRate);
            
            // Log individual consumer health
            consumerHealthMap.forEach((name, health) -> {
                log.info("Consumer {}: {}", name, health.getHealthSummary());
            });
            
            log.info("=== End Report ===");
            
        } catch (Exception e) {
            log.error("Failed to generate monitoring report", e);
        }
    }

    /**
     * Get current consumer health status
     */
    public Map<String, ConsumerHealth> getConsumerHealthStatus() {
        return new ConcurrentHashMap<>(consumerHealthMap);
    }

    /**
     * Get monitoring statistics
     */
    public MonitoringStatistics getMonitoringStatistics() {
        return MonitoringStatistics.builder()
            .totalMessagesProcessed(totalMessagesProcessed.get())
            .totalMessagesFailed(totalMessagesFailed.get())
            .totalDlqMessages(totalDlqMessages.get())
            .activeConsumerCount((int) getActiveConsumerCount())
            .totalConsumerLag((long) getTotalConsumerLag())
            .consumerHealthMap(new ConcurrentHashMap<>(consumerHealthMap))
            .generatedAt(Instant.now())
            .build();
    }

    /**
     * Monitoring statistics data structure
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MonitoringStatistics {
        private Long totalMessagesProcessed;
        private Long totalMessagesFailed;
        private Long totalDlqMessages;
        private Integer activeConsumerCount;
        private Long totalConsumerLag;
        private Map<String, ConsumerHealth> consumerHealthMap;
        private Instant generatedAt;
        
        public double getSuccessRate() {
            long total = totalMessagesProcessed + totalMessagesFailed;
            return total > 0 ? (totalMessagesProcessed * 100.0) / total : 100.0;
        }
        
        public double getFailureRate() {
            long total = totalMessagesProcessed + totalMessagesFailed;
            return total > 0 ? (totalMessagesFailed * 100.0) / total : 0.0;
        }
    }
}