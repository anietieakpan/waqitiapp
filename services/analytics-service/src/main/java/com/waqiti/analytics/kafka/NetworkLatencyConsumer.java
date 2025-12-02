package com.waqiti.analytics.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.NetworkLatencyEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.analytics.service.NetworkMonitoringService;
import com.waqiti.analytics.service.AlertService;
import com.waqiti.analytics.model.NetworkLatencyMetric;
import com.waqiti.analytics.repository.NetworkLatencyRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Consumer for processing network latency events.
 * Monitors network performance, detects latency spikes, and analyzes connectivity issues.
 */
@Slf4j
@Component
public class NetworkLatencyConsumer extends BaseKafkaConsumer<NetworkLatencyEvent> {

    private static final String TOPIC = "network-latency-events";
    private static final double HIGH_LATENCY_THRESHOLD = 100.0; // milliseconds
    private static final double CRITICAL_LATENCY_THRESHOLD = 500.0; // milliseconds

    private final NetworkMonitoringService networkMonitoringService;
    private final AlertService alertService;
    private final NetworkLatencyRepository networkLatencyRepository;

    // Metrics
    private final Counter processedCounter;
    private final Counter highLatencyCounter;
    private final Counter timeoutCounter;
    private final Timer processingTimer;

    @Autowired
    public NetworkLatencyConsumer(
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            NetworkMonitoringService networkMonitoringService,
            AlertService alertService,
            NetworkLatencyRepository networkLatencyRepository) {
        super(objectMapper, TOPIC);
        this.networkMonitoringService = networkMonitoringService;
        this.alertService = alertService;
        this.networkLatencyRepository = networkLatencyRepository;

        this.processedCounter = Counter.builder("network_latency_processed_total")
                .description("Total network latency events processed")
                .register(meterRegistry);
        this.highLatencyCounter = Counter.builder("high_latency_detected_total")
                .description("Total high latency incidents detected")
                .register(meterRegistry);
        this.timeoutCounter = Counter.builder("network_timeout_total")
                .description("Total network timeouts detected")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("network_latency_processing_duration")
                .description("Time taken to process network latency events")
                .register(meterRegistry);
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(topics = TOPIC, groupId = "analytics-service-network-latency-group")
    @Transactional
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment ack) {

        Timer.Sample sample = Timer.start();

        try {
            log.info("Processing network latency event - Key: {}, Partition: {}, Offset: {}",
                    key, partition, record.offset());

            NetworkLatencyEvent event = deserializeEvent(record.value(), NetworkLatencyEvent.class);

            // Validate required fields
            validateEvent(event);

            // Check for duplicate processing
            if (isAlreadyProcessed(event.getSourceHost(), event.getTargetHost(), event.getTimestamp())) {
                log.info("Network latency metric already processed: {} -> {}",
                        event.getSourceHost(), event.getTargetHost());
                ack.acknowledge();
                return;
            }

            // Process the network latency event
            processNetworkLatencyEvent(event);

            processedCounter.increment();
            log.info("Successfully processed network latency event: {} -> {} ({}ms)",
                    event.getSourceHost(), event.getTargetHost(), event.getLatencyMs());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing network latency event: {}", record.value(), e);
            throw new RuntimeException("Failed to process network latency event", e);
        } finally {
            sample.stop(processingTimer);
        }
    }

    private void processNetworkLatencyEvent(NetworkLatencyEvent event) {
        try {
            // Create network latency metric record
            NetworkLatencyMetric metric = createNetworkLatencyMetric(event);

            // Check for high latency
            if (isHighLatency(event)) {
                handleHighLatency(event, metric);
                highLatencyCounter.increment();
            }

            // Check for timeouts
            if (hasTimeout(event)) {
                handleTimeout(event, metric);
                timeoutCounter.increment();
            }

            // Analyze network path performance
            analyzeNetworkPath(event, metric);

            // Update network topology health
            updateNetworkHealth(event, metric);

            // Save the metric
            networkLatencyRepository.save(metric);

            // Generate network insights
            generateNetworkInsights(event, metric);

            log.info("Processed network latency metric: {} -> {} = {}ms (Status: {})",
                    event.getSourceHost(), event.getTargetHost(),
                    event.getLatencyMs(), metric.getStatus());

        } catch (Exception e) {
            log.error("Error processing network latency event: {} -> {}",
                    event.getSourceHost(), event.getTargetHost(), e);
            throw new RuntimeException("Failed to process network latency event", e);
        }
    }

    private NetworkLatencyMetric createNetworkLatencyMetric(NetworkLatencyEvent event) {
        return NetworkLatencyMetric.builder()
                .sourceHost(event.getSourceHost())
                .targetHost(event.getTargetHost())
                .sourceRegion(event.getSourceRegion())
                .targetRegion(event.getTargetRegion())
                .latencyMs(event.getLatencyMs())
                .packetLoss(event.getPacketLoss())
                .jitter(event.getJitter())
                .bandwidth(event.getBandwidth())
                .protocol(event.getProtocol())
                .port(event.getPort())
                .networkPath(event.getNetworkPath())
                .timestamp(event.getTimestamp())
                .createdAt(LocalDateTime.now())
                .status("NORMAL")
                .build();
    }

    private boolean isHighLatency(NetworkLatencyEvent event) {
        return event.getLatencyMs() > HIGH_LATENCY_THRESHOLD;
    }

    private void handleHighLatency(NetworkLatencyEvent event, NetworkLatencyMetric metric) {
        try {
            String severity = event.getLatencyMs() > CRITICAL_LATENCY_THRESHOLD ? "CRITICAL" : "WARNING";
            metric.setStatus("HIGH_LATENCY");
            metric.setSeverity(severity);

            // Create latency alert
            alertService.createNetworkLatencyAlert(
                event.getSourceHost(),
                event.getTargetHost(),
                event.getLatencyMs(),
                severity
            );

            // Analyze latency spike
            var spikeAnalysis = networkMonitoringService.analyzeLatencySpike(
                event.getSourceHost(), event.getTargetHost(), event.getLatencyMs()
            );

            metric.setSpikeAnalysis(spikeAnalysis);

            // Check for network congestion
            if (networkMonitoringService.isNetworkCongestion(event.getNetworkPath())) {
                alertService.createNetworkCongestionAlert(event.getNetworkPath(), event.getLatencyMs());
                metric.setCongestionDetected(true);
            }

            log.warn("High network latency detected: {} -> {} ({}ms - {})",
                    event.getSourceHost(), event.getTargetHost(),
                    event.getLatencyMs(), severity);

        } catch (Exception e) {
            log.error("Error handling high latency: {} -> {}",
                    event.getSourceHost(), event.getTargetHost(), e);
            throw new RuntimeException("Failed to handle high latency", e);
        }
    }

    private boolean hasTimeout(NetworkLatencyEvent event) {
        return event.getLatencyMs() == -1 || event.getPacketLoss() > 50; // 50% packet loss indicates severe issues
    }

    private void handleTimeout(NetworkLatencyEvent event, NetworkLatencyMetric metric) {
        try {
            metric.setStatus("TIMEOUT");
            metric.setSeverity("CRITICAL");

            // Create timeout alert
            alertService.createNetworkTimeoutAlert(
                event.getSourceHost(),
                event.getTargetHost(),
                event.getPacketLoss()
            );

            // Check for network partition
            boolean isPartition = networkMonitoringService.checkNetworkPartition(
                event.getSourceHost(), event.getTargetHost()
            );

            if (isPartition) {
                metric.setNetworkPartition(true);
                alertService.createCriticalAlert("NETWORK_PARTITION",
                    "Network partition detected between " + event.getSourceHost() + " and " + event.getTargetHost());
            }

            log.error("Network timeout detected: {} -> {} (Packet Loss: {}%)",
                    event.getSourceHost(), event.getTargetHost(), event.getPacketLoss());

        } catch (Exception e) {
            log.error("Error handling timeout: {} -> {}",
                    event.getSourceHost(), event.getTargetHost(), e);
            throw new RuntimeException("Failed to handle timeout", e);
        }
    }

    private void analyzeNetworkPath(NetworkLatencyEvent event, NetworkLatencyMetric metric) {
        try {
            if (event.getNetworkPath() != null && !event.getNetworkPath().isEmpty()) {
                // Analyze network path for bottlenecks
                var pathAnalysis = networkMonitoringService.analyzeNetworkPath(
                    event.getNetworkPath(), event.getLatencyMs()
                );

                metric.setPathAnalysis(pathAnalysis);

                // Identify bottleneck points
                if (pathAnalysis.hasBottlenecks()) {
                    alertService.createNetworkBottleneckAlert(
                        event.getNetworkPath(), pathAnalysis.getBottlenecks()
                    );
                }
            }

        } catch (Exception e) {
            log.error("Error analyzing network path: {} -> {}",
                    event.getSourceHost(), event.getTargetHost(), e);
            // Don't fail the processing for path analysis errors
        }
    }

    private void updateNetworkHealth(NetworkLatencyEvent event, NetworkLatencyMetric metric) {
        try {
            // Update network segment health
            networkMonitoringService.updateNetworkSegmentHealth(
                event.getSourceHost(), event.getTargetHost(), metric
            );

            // Update overall network topology health
            String networkHealth = networkMonitoringService.calculateNetworkHealth(
                event.getSourceRegion(), event.getTargetRegion()
            );

            networkMonitoringService.updateNetworkTopologyHealth(networkHealth);

        } catch (Exception e) {
            log.error("Error updating network health: {} -> {}",
                    event.getSourceHost(), event.getTargetHost(), e);
            // Don't fail the processing for health update errors
        }
    }

    private void generateNetworkInsights(NetworkLatencyEvent event, NetworkLatencyMetric metric) {
        try {
            // Generate network optimization insights
            var insights = networkMonitoringService.generateNetworkInsights(
                event.getSourceHost(), event.getTargetHost(), metric
            );

            if (!insights.isEmpty()) {
                // Store insights for network engineers
                networkMonitoringService.storeNetworkInsights(insights);

                // Send actionable insights
                alertService.sendNetworkInsights(insights);
            }

        } catch (Exception e) {
            log.error("Error generating network insights: {} -> {}",
                    event.getSourceHost(), event.getTargetHost(), e);
            // Don't fail the processing for insight generation errors
        }
    }

    private boolean isAlreadyProcessed(String sourceHost, String targetHost, LocalDateTime timestamp) {
        return networkLatencyRepository.existsBySourceHostAndTargetHostAndTimestamp(
            sourceHost, targetHost, timestamp
        );
    }

    private void validateEvent(NetworkLatencyEvent event) {
        if (event.getSourceHost() == null || event.getSourceHost().trim().isEmpty()) {
            throw new IllegalArgumentException("Source host cannot be null or empty");
        }
        if (event.getTargetHost() == null || event.getTargetHost().trim().isEmpty()) {
            throw new IllegalArgumentException("Target host cannot be null or empty");
        }
        if (event.getLatencyMs() == null) {
            throw new IllegalArgumentException("Latency cannot be null");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
    }

    @Override
    protected void handleProcessingError(String message, Exception error) {
        log.error("Network latency processing error: {}", message, error);
        // Additional error handling logic
    }

    @Override
    protected void logProcessingMetrics(String key, long processingTime) {
        log.debug("Processed network latency event - Key: {}, Time: {}ms", key, processingTime);
    }
}