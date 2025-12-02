package com.waqiti.monitoring.kafka;

import com.waqiti.common.events.NetworkConnectivityEvent;
import com.waqiti.monitoring.domain.NetworkConnectivityRecord;
import com.waqiti.monitoring.repository.NetworkConnectivityRecordRepository;
import com.waqiti.monitoring.service.NetworkMonitoringService;
import com.waqiti.monitoring.service.NetworkRecoveryService;
import com.waqiti.monitoring.service.NetworkDiagnosticsService;
import com.waqiti.monitoring.metrics.NetworkMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicDouble;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class NetworkConnectivityEventsConsumer {

    private final NetworkConnectivityRecordRepository connectivityRepository;
    private final NetworkMonitoringService networkMonitoringService;
    private final NetworkRecoveryService recoveryService;
    private final NetworkDiagnosticsService diagnosticsService;
    private final NetworkMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Network tracking
    private final AtomicLong networkIssueCount = new AtomicLong(0);
    private final AtomicDouble averageLatency = new AtomicDouble(0.0);

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter networkIssueCounter;
    private Timer processingTimer;
    private Gauge networkIssueGauge;
    private Gauge avgLatencyGauge;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("network_connectivity_events_processed_total")
            .description("Total number of successfully processed network connectivity events")
            .register(meterRegistry);
        errorCounter = Counter.builder("network_connectivity_events_errors_total")
            .description("Total number of network connectivity event processing errors")
            .register(meterRegistry);
        networkIssueCounter = Counter.builder("network_issues_total")
            .description("Total number of network connectivity issues")
            .register(meterRegistry);
        processingTimer = Timer.builder("network_connectivity_events_processing_duration")
            .description("Time taken to process network connectivity events")
            .register(meterRegistry);
        networkIssueGauge = Gauge.builder("network_issues_active")
            .description("Number of active network connectivity issues")
            .register(meterRegistry, networkIssueCount, AtomicLong::get);
        avgLatencyGauge = Gauge.builder("average_network_latency_ms")
            .description("Average network latency across all monitored connections")
            .register(meterRegistry, averageLatency, AtomicDouble::get);
    }

    @KafkaListener(
        topics = {"network-connectivity-events", "network-outage-alerts", "network-latency-events"},
        groupId = "network-connectivity-events-service-group",
        containerFactory = "criticalInfrastructureKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "network-connectivity-events", fallbackMethod = "handleNetworkEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleNetworkConnectivityEvent(
            @Payload NetworkConnectivityEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("network-%s-p%d-o%d", event.getConnectionId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getConnectionId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing network connectivity event: connectionId={}, eventType={}, status={}, latency={}ms",
                event.getConnectionId(), event.getEventType(), event.getConnectionStatus(), event.getLatencyMs());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Network recovery impact assessment
            assessNetworkRecoveryImpact(event, correlationId);

            switch (event.getEventType()) {
                case CONNECTION_ESTABLISHED:
                    handleConnectionEstablished(event, correlationId);
                    break;

                case CONNECTION_LOST:
                    handleConnectionLost(event, correlationId);
                    break;

                case CONNECTION_DEGRADED:
                    handleConnectionDegraded(event, correlationId);
                    break;

                case LATENCY_SPIKE:
                    handleLatencySpike(event, correlationId);
                    break;

                case PACKET_LOSS_DETECTED:
                    handlePacketLossDetected(event, correlationId);
                    break;

                case BANDWIDTH_SATURATION:
                    handleBandwidthSaturation(event, correlationId);
                    break;

                case DNS_RESOLUTION_FAILURE:
                    handleDnsResolutionFailure(event, correlationId);
                    break;

                case TIMEOUT_EXCEEDED:
                    handleTimeoutExceeded(event, correlationId);
                    break;

                case ROUTE_FLAPPING:
                    handleRouteFlapping(event, correlationId);
                    break;

                case CONNECTION_RESTORED:
                    handleConnectionRestored(event, correlationId);
                    break;

                default:
                    log.warn("Unknown network connectivity event type: {}", event.getEventType());
                    handleGenericNetworkEvent(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logNetworkEvent("NETWORK_CONNECTIVITY_EVENT_PROCESSED", event.getConnectionId(),
                Map.of("eventType", event.getEventType(), "connectionStatus", event.getConnectionStatus(),
                    "latencyMs", event.getLatencyMs(), "sourceEndpoint", event.getSourceEndpoint(),
                    "targetEndpoint", event.getTargetEndpoint(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process network connectivity event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("network-connectivity-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleNetworkEventFallback(
            NetworkConnectivityEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("network-fallback-%s-p%d-o%d", event.getConnectionId(), partition, offset);

        log.error("Circuit breaker fallback triggered for network event: connectionId={}, error={}",
            event.getConnectionId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("network-connectivity-events-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert for critical network connections
        if ("CRITICAL".equals(event.getSeverity())) {
            try {
                notificationService.sendExecutiveAlert(
                    "Critical Network Connectivity Event - Circuit Breaker Triggered",
                    String.format("Critical network monitoring for %s failed: %s",
                        event.getConnectionId(), ex.getMessage()),
                    "CRITICAL"
                );
            } catch (Exception notificationEx) {
                log.error("Failed to send executive alert: {}", notificationEx.getMessage());
            }
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltNetworkEvent(
            @Payload NetworkConnectivityEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-network-%s-%d", event.getConnectionId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Network event permanently failed: connectionId={}, topic={}, error={}",
            event.getConnectionId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logNetworkEvent("NETWORK_CONNECTIVITY_DLT_EVENT", event.getConnectionId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "connectionStatus", event.getConnectionStatus(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Network Connectivity Event Dead Letter Event",
                String.format("Network monitoring for %s sent to DLT: %s",
                    event.getConnectionId(), exceptionMessage),
                Map.of("connectionId", event.getConnectionId(), "topic", topic,
                    "correlationId", correlationId, "eventType", event.getEventType())
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private void assessNetworkRecoveryImpact(NetworkConnectivityEvent event, String correlationId) {
        if ("CONNECTION_LOST".equals(event.getEventType()) ||
            "CONNECTION_DEGRADED".equals(event.getEventType()) ||
            "TIMEOUT_EXCEEDED".equals(event.getEventType())) {
            networkIssueCount.incrementAndGet();
            networkIssueCounter.increment();

            // Alert if too many network issues
            if (networkIssueCount.get() > 3) {
                try {
                    notificationService.sendExecutiveAlert(
                        "CRITICAL: Multiple Network Connectivity Issues",
                        String.format("Network issues count: %d. Network infrastructure review required.",
                            networkIssueCount.get()),
                        "CRITICAL"
                    );
                } catch (Exception ex) {
                    log.error("Failed to send network recovery impact alert: {}", ex.getMessage());
                }
            }
        }

        if ("CONNECTION_ESTABLISHED".equals(event.getEventType()) ||
            "CONNECTION_RESTORED".equals(event.getEventType())) {
            long currentIssues = networkIssueCount.get();
            if (currentIssues > 0) {
                networkIssueCount.decrementAndGet();
            }
        }

        // Update average latency
        if (event.getLatencyMs() != null && event.getLatencyMs() > 0) {
            double currentAvg = averageLatency.get();
            double newAvg = (currentAvg + event.getLatencyMs()) / 2.0;
            averageLatency.set(newAvg);
        }
    }

    private void handleConnectionEstablished(NetworkConnectivityEvent event, String correlationId) {
        createConnectivityRecord(event, "ESTABLISHED", correlationId);

        // Clear any existing alerts for this connection
        kafkaTemplate.send("network-alert-resolution", Map.of(
            "connectionId", event.getConnectionId(),
            "resolutionType", "CONNECTION_ESTABLISHED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Update connection health status
        networkMonitoringService.updateConnectionHealth(event.getConnectionId(), "HEALTHY");

        // Resume network traffic if it was redirected
        kafkaTemplate.send("network-traffic-resumption", Map.of(
            "connectionId", event.getConnectionId(),
            "resumptionType", "CONNECTION_ESTABLISHED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Network connection {} established", event.getConnectionId());
        metricsService.recordNetworkEvent("CONNECTION_ESTABLISHED", event.getConnectionId());
    }

    private void handleConnectionLost(NetworkConnectivityEvent event, String correlationId) {
        createConnectivityRecord(event, "LOST", correlationId);

        // Immediate failover to backup connection
        kafkaTemplate.send("network-failover-requests", Map.of(
            "primaryConnectionId", event.getConnectionId(),
            "failoverType", "CONNECTION_LOST",
            "urgency", "IMMEDIATE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Start connection recovery
        kafkaTemplate.send("connection-recovery-requests", Map.of(
            "connectionId", event.getConnectionId(),
            "recoveryType", "CONNECTION_LOST_RECOVERY",
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Network diagnostics
        kafkaTemplate.send("network-diagnostics-requests", Map.of(
            "connectionId", event.getConnectionId(),
            "diagnosticType", "CONNECTION_LOST_ANALYSIS",
            "sourceEndpoint", event.getSourceEndpoint(),
            "targetEndpoint", event.getTargetEndpoint(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Network Connection Lost",
            String.format("Connection %s lost between %s and %s",
                event.getConnectionId(), event.getSourceEndpoint(), event.getTargetEndpoint()),
            "HIGH");

        metricsService.recordNetworkEvent("CONNECTION_LOST", event.getConnectionId());
    }

    private void handleConnectionDegraded(NetworkConnectivityEvent event, String correlationId) {
        createConnectivityRecord(event, "DEGRADED", correlationId);

        // Network optimization
        kafkaTemplate.send("network-optimization-requests", Map.of(
            "connectionId", event.getConnectionId(),
            "optimizationType", "DEGRADED_CONNECTION_OPTIMIZATION",
            "currentLatency", event.getLatencyMs(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Load balancing adjustment
        kafkaTemplate.send("load-balancing-adjustments", Map.of(
            "connectionId", event.getConnectionId(),
            "adjustmentType", "DEGRADED_CONNECTION",
            "trafficReduction", 25.0,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Monitor for escalation
        kafkaTemplate.send("connection-degradation-monitoring", Map.of(
            "connectionId", event.getConnectionId(),
            "monitoringType", "DEGRADATION_TRACKING",
            "escalationThreshold", "10_MINUTES",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordNetworkEvent("CONNECTION_DEGRADED", event.getConnectionId());
    }

    private void handleLatencySpike(NetworkConnectivityEvent event, String correlationId) {
        createConnectivityRecord(event, "LATENCY_SPIKE", correlationId);

        // Latency analysis
        kafkaTemplate.send("latency-analysis-requests", Map.of(
            "connectionId", event.getConnectionId(),
            "analysisType", "LATENCY_SPIKE_INVESTIGATION",
            "currentLatency", event.getLatencyMs(),
            "baselineLatency", event.getBaselineLatencyMs(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Route optimization
        kafkaTemplate.send("route-optimization-requests", Map.of(
            "connectionId", event.getConnectionId(),
            "optimizationType", "LATENCY_REDUCTION",
            "targetLatency", event.getBaselineLatencyMs(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if (event.getLatencyMs() > 1000) { // > 1 second
            notificationService.sendOperationalAlert("High Network Latency",
                String.format("Connection %s latency spike: %dms (baseline: %dms)",
                    event.getConnectionId(), event.getLatencyMs(), event.getBaselineLatencyMs()),
                "MEDIUM");
        }

        metricsService.recordNetworkEvent("LATENCY_SPIKE", event.getConnectionId());
    }

    private void handlePacketLossDetected(NetworkConnectivityEvent event, String correlationId) {
        createConnectivityRecord(event, "PACKET_LOSS", correlationId);

        // Packet loss analysis
        kafkaTemplate.send("packet-loss-analysis", Map.of(
            "connectionId", event.getConnectionId(),
            "analysisType", "PACKET_LOSS_INVESTIGATION",
            "packetLossPercent", event.getPacketLossPercent(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Network path analysis
        kafkaTemplate.send("network-path-analysis", Map.of(
            "connectionId", event.getConnectionId(),
            "pathAnalysisType", "PACKET_LOSS_TROUBLESHOOTING",
            "sourceEndpoint", event.getSourceEndpoint(),
            "targetEndpoint", event.getTargetEndpoint(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if (event.getPacketLossPercent() > 5.0) {
            notificationService.sendOperationalAlert("Significant Packet Loss",
                String.format("Connection %s packet loss: %.1f%%",
                    event.getConnectionId(), event.getPacketLossPercent()),
                "HIGH");
        }

        metricsService.recordNetworkEvent("PACKET_LOSS", event.getConnectionId());
    }

    private void handleBandwidthSaturation(NetworkConnectivityEvent event, String correlationId) {
        createConnectivityRecord(event, "BANDWIDTH_SATURATION", correlationId);

        // Bandwidth optimization
        kafkaTemplate.send("bandwidth-optimization-requests", Map.of(
            "connectionId", event.getConnectionId(),
            "optimizationType", "BANDWIDTH_SATURATION_RELIEF",
            "currentUtilization", event.getBandwidthUtilizationPercent(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Traffic shaping
        kafkaTemplate.send("traffic-shaping-requests", Map.of(
            "connectionId", event.getConnectionId(),
            "shapingType", "BANDWIDTH_SATURATION",
            "targetUtilization", 80.0,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Scale bandwidth if possible
        kafkaTemplate.send("bandwidth-scaling-requests", Map.of(
            "connectionId", event.getConnectionId(),
            "scalingType", "BANDWIDTH_INCREASE",
            "scalingFactor", 1.5,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordNetworkEvent("BANDWIDTH_SATURATION", event.getConnectionId());
    }

    private void handleDnsResolutionFailure(NetworkConnectivityEvent event, String correlationId) {
        createConnectivityRecord(event, "DNS_FAILURE", correlationId);

        // DNS diagnostics
        kafkaTemplate.send("dns-diagnostics-requests", Map.of(
            "connectionId", event.getConnectionId(),
            "diagnosticType", "DNS_RESOLUTION_FAILURE",
            "targetHostname", event.getTargetHostname(),
            "dnsServer", event.getDnsServer(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Fallback DNS servers
        kafkaTemplate.send("dns-fallback-activation", Map.of(
            "connectionId", event.getConnectionId(),
            "fallbackType", "DNS_RESOLUTION_FAILURE",
            "primaryDnsServer", event.getDnsServer(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("DNS Resolution Failure",
            String.format("DNS resolution failed for connection %s, hostname: %s",
                event.getConnectionId(), event.getTargetHostname()),
            "HIGH");

        metricsService.recordNetworkEvent("DNS_FAILURE", event.getConnectionId());
    }

    private void handleTimeoutExceeded(NetworkConnectivityEvent event, String correlationId) {
        createConnectivityRecord(event, "TIMEOUT", correlationId);

        // Timeout analysis
        kafkaTemplate.send("timeout-analysis-requests", Map.of(
            "connectionId", event.getConnectionId(),
            "analysisType", "TIMEOUT_INVESTIGATION",
            "timeoutValue", event.getTimeoutMs(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Timeout optimization
        kafkaTemplate.send("timeout-optimization-requests", Map.of(
            "connectionId", event.getConnectionId(),
            "optimizationType", "TIMEOUT_ADJUSTMENT",
            "currentTimeout", event.getTimeoutMs(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordNetworkEvent("TIMEOUT", event.getConnectionId());
    }

    private void handleRouteFlapping(NetworkConnectivityEvent event, String correlationId) {
        createConnectivityRecord(event, "ROUTE_FLAPPING", correlationId);

        // Route stability analysis
        kafkaTemplate.send("route-stability-analysis", Map.of(
            "connectionId", event.getConnectionId(),
            "analysisType", "ROUTE_FLAPPING_INVESTIGATION",
            "flappingFrequency", event.getFlappingFrequency(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Route dampening
        kafkaTemplate.send("route-dampening-requests", Map.of(
            "connectionId", event.getConnectionId(),
            "dampeningType", "FLAPPING_PREVENTION",
            "dampeningDuration", 300000, // 5 minutes
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Route Flapping Detected",
            String.format("Route flapping detected for connection %s, frequency: %d/min",
                event.getConnectionId(), event.getFlappingFrequency()),
            "HIGH");

        metricsService.recordNetworkEvent("ROUTE_FLAPPING", event.getConnectionId());
    }

    private void handleConnectionRestored(NetworkConnectivityEvent event, String correlationId) {
        createConnectivityRecord(event, "RESTORED", correlationId);

        // Clear connection alerts
        kafkaTemplate.send("connection-alert-resolution", Map.of(
            "connectionId", event.getConnectionId(),
            "resolutionType", "CONNECTION_RESTORED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Resume normal traffic flow
        kafkaTemplate.send("traffic-restoration-requests", Map.of(
            "connectionId", event.getConnectionId(),
            "restorationType", "CONNECTION_RESTORED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Network connection {} restored", event.getConnectionId());
        metricsService.recordNetworkEvent("CONNECTION_RESTORED", event.getConnectionId());
    }

    private void handleGenericNetworkEvent(NetworkConnectivityEvent event, String correlationId) {
        createConnectivityRecord(event, "GENERIC", correlationId);

        // Log for investigation
        auditService.logNetworkEvent("UNKNOWN_NETWORK_EVENT", event.getConnectionId(),
            Map.of("eventType", event.getEventType(), "connectionStatus", event.getConnectionStatus(),
                "latencyMs", event.getLatencyMs(), "correlationId", correlationId,
                "requiresInvestigation", true, "timestamp", Instant.now()));

        notificationService.sendOperationalAlert("Unknown Network Event",
            String.format("Unknown network event for connection %s: %s",
                event.getConnectionId(), event.getEventType()),
            "MEDIUM");

        metricsService.recordNetworkEvent("GENERIC", event.getConnectionId());
    }

    private void createConnectivityRecord(NetworkConnectivityEvent event, String status, String correlationId) {
        try {
            NetworkConnectivityRecord record = NetworkConnectivityRecord.builder()
                .connectionId(event.getConnectionId())
                .sourceEndpoint(event.getSourceEndpoint())
                .targetEndpoint(event.getTargetEndpoint())
                .connectionStatus(status)
                .latencyMs(event.getLatencyMs())
                .packetLossPercent(event.getPacketLossPercent())
                .bandwidthUtilizationPercent(event.getBandwidthUtilizationPercent())
                .eventTime(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

            connectivityRepository.save(record);
        } catch (Exception e) {
            log.error("Failed to create network connectivity record: {}", e.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) {
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }
}