package com.waqiti.infrastructure.kafka;

import com.waqiti.common.events.DnsUpdateEvent;
import com.waqiti.infrastructure.domain.DnsRecord;
import com.waqiti.infrastructure.repository.DnsRecordRepository;
import com.waqiti.infrastructure.service.DnsManagementService;
import com.waqiti.infrastructure.service.SecurityIncidentService;
import com.waqiti.infrastructure.service.NetworkMonitoringService;
import com.waqiti.infrastructure.metrics.InfrastructureMetricsService;
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
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class DnsUpdatesConsumer {

    private final DnsRecordRepository dnsRecordRepository;
    private final DnsManagementService dnsManagementService;
    private final SecurityIncidentService securityIncidentService;
    private final NetworkMonitoringService networkMonitoringService;
    private final InfrastructureMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("dns_updates_processed_total")
            .description("Total number of successfully processed DNS update events")
            .register(meterRegistry);
        errorCounter = Counter.builder("dns_updates_errors_total")
            .description("Total number of DNS update processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("dns_updates_processing_duration")
            .description("Time taken to process DNS update events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"dns-updates"},
        groupId = "dns-updates-service-group",
        containerFactory = "criticalInfrastructureKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "dns-updates", fallbackMethod = "handleDnsUpdateEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000))
    public void handleDnsUpdateEvent(
            @Payload DnsUpdateEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("dns-update-%s-p%d-o%d", event.getDomainName(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getDomainName(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing DNS update: domain={}, eventType={}, recordType={}",
                event.getDomainName(), event.getEventType(), event.getRecordType());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case DNS_RECORD_CREATED:
                    processDnsRecordCreated(event, correlationId);
                    break;

                case DNS_RECORD_UPDATED:
                    processDnsRecordUpdated(event, correlationId);
                    break;

                case DNS_RECORD_DELETED:
                    processDnsRecordDeleted(event, correlationId);
                    break;

                case DNS_ZONE_CREATED:
                    processDnsZoneCreated(event, correlationId);
                    break;

                case DNS_ZONE_UPDATED:
                    processDnsZoneUpdated(event, correlationId);
                    break;

                case DNS_ZONE_DELETED:
                    processDnsZoneDeleted(event, correlationId);
                    break;

                case DNS_PROPAGATION_STARTED:
                    processDnsPropagationStarted(event, correlationId);
                    break;

                case DNS_PROPAGATION_COMPLETED:
                    processDnsPropagationCompleted(event, correlationId);
                    break;

                case DNS_PROPAGATION_FAILED:
                    processDnsPropagationFailed(event, correlationId);
                    break;

                case DNS_SECURITY_UPDATE:
                    processDnsSecurityUpdate(event, correlationId);
                    break;

                default:
                    log.warn("Unknown DNS update event type: {}", event.getEventType());
                    processUnknownDnsEvent(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logInfrastructureEvent("DNS_UPDATE_EVENT_PROCESSED", event.getDomainName(),
                Map.of("eventType", event.getEventType(), "recordType", event.getRecordType(),
                    "value", event.getValue(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process DNS update event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("dns-updates-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleDnsUpdateEventFallback(
            DnsUpdateEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("dns-update-fallback-%s-p%d-o%d", event.getDomainName(), partition, offset);

        log.error("Circuit breaker fallback triggered for DNS update: domain={}, error={}",
            event.getDomainName(), ex.getMessage());

        // Create incident for circuit breaker
        securityIncidentService.createIncident(
            "DNS_UPDATE_CIRCUIT_BREAKER",
            String.format("DNS update circuit breaker triggered for domain %s", event.getDomainName()),
            "HIGH",
            Map.of("domainName", event.getDomainName(), "eventType", event.getEventType(),
                "error", ex.getMessage(), "correlationId", correlationId)
        );

        // Send to dead letter queue
        kafkaTemplate.send("dns-updates-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "DNS Update Circuit Breaker",
                String.format("DNS update processing failed for domain %s: %s",
                    event.getDomainName(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send critical alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltDnsUpdateEvent(
            @Payload DnsUpdateEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-dns-update-%s-%d", event.getDomainName(), System.currentTimeMillis());

        log.error("Dead letter topic handler - DNS update permanently failed: domain={}, topic={}, error={}",
            event.getDomainName(), topic, exceptionMessage);

        // Create critical incident
        securityIncidentService.createCriticalIncident(
            "DNS_UPDATE_DLT_EVENT",
            String.format("DNS update sent to DLT for domain %s", event.getDomainName()),
            Map.of("domainName", event.getDomainName(), "originalTopic", topic,
                "errorMessage", exceptionMessage, "correlationId", correlationId,
                "requiresImmediateAction", true)
        );

        // Save to dead letter store for manual investigation
        auditService.logCriticalInfrastructureEvent("DNS_UPDATE_DLT_EVENT", event.getDomainName(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send emergency alert
        try {
            notificationService.sendEmergencyAlert(
                "DNS Update Dead Letter Event",
                String.format("DNS update for domain %s sent to DLT: %s",
                    event.getDomainName(), exceptionMessage),
                Map.of("domainName", event.getDomainName(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send emergency DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        // Check if the entry has expired
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
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void processDnsRecordCreated(DnsUpdateEvent event, String correlationId) {
        // Create DNS record
        DnsRecord record = DnsRecord.builder()
            .domainName(event.getDomainName())
            .recordType(event.getRecordType())
            .value(event.getValue())
            .ttl(event.getTtl())
            .priority(event.getPriority())
            .status("ACTIVE")
            .createdAt(LocalDateTime.now())
            .createdBy(event.getUpdatedBy())
            .correlationId(correlationId)
            .build();

        dnsRecordRepository.save(record);

        // Apply DNS record to infrastructure
        dnsManagementService.createDnsRecord(event.getDomainName(), event.getRecordType(),
            event.getValue(), event.getTtl(), event.getPriority());

        // Start propagation monitoring
        networkMonitoringService.startDnsPropagationMonitoring(event.getDomainName(),
            event.getRecordType(), event.getValue());

        // Send propagation event
        kafkaTemplate.send("dns-updates", Map.of(
            "domainName", event.getDomainName(),
            "eventType", "DNS_PROPAGATION_STARTED",
            "recordType", event.getRecordType(),
            "value", event.getValue(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordDnsRecordCreated(event.getRecordType());

        log.info("DNS record created: domain={}, type={}, value={}",
            event.getDomainName(), event.getRecordType(), event.getValue());
    }

    private void processDnsRecordUpdated(DnsUpdateEvent event, String correlationId) {
        // Update DNS record
        DnsRecord record = dnsRecordRepository.findByDomainNameAndRecordType(
            event.getDomainName(), event.getRecordType())
            .orElseThrow(() -> new RuntimeException("DNS record not found"));

        String oldValue = record.getValue();
        record.setValue(event.getValue());
        record.setTtl(event.getTtl());
        record.setPriority(event.getPriority());
        record.setUpdatedAt(LocalDateTime.now());
        record.setUpdatedBy(event.getUpdatedBy());
        record.setCorrelationId(correlationId);

        dnsRecordRepository.save(record);

        // Apply DNS record update to infrastructure
        dnsManagementService.updateDnsRecord(event.getDomainName(), event.getRecordType(),
            event.getValue(), event.getTtl(), event.getPriority());

        // Log change for audit
        auditService.logInfrastructureEvent("DNS_RECORD_UPDATED", event.getDomainName(),
            Map.of("recordType", event.getRecordType(), "oldValue", oldValue,
                "newValue", event.getValue(), "updatedBy", event.getUpdatedBy(),
                "correlationId", correlationId, "timestamp", Instant.now()));

        // Start propagation monitoring
        networkMonitoringService.startDnsPropagationMonitoring(event.getDomainName(),
            event.getRecordType(), event.getValue());

        metricsService.recordDnsRecordUpdated(event.getRecordType());

        log.info("DNS record updated: domain={}, type={}, oldValue={}, newValue={}",
            event.getDomainName(), event.getRecordType(), oldValue, event.getValue());
    }

    private void processDnsRecordDeleted(DnsUpdateEvent event, String correlationId) {
        // Find and mark DNS record as deleted
        DnsRecord record = dnsRecordRepository.findByDomainNameAndRecordType(
            event.getDomainName(), event.getRecordType())
            .orElseThrow(() -> new RuntimeException("DNS record not found"));

        record.setStatus("DELETED");
        record.setDeletedAt(LocalDateTime.now());
        record.setDeletedBy(event.getUpdatedBy());
        record.setCorrelationId(correlationId);

        dnsRecordRepository.save(record);

        // Remove DNS record from infrastructure
        dnsManagementService.deleteDnsRecord(event.getDomainName(), event.getRecordType());

        // Log deletion for audit
        auditService.logInfrastructureEvent("DNS_RECORD_DELETED", event.getDomainName(),
            Map.of("recordType", event.getRecordType(), "value", record.getValue(),
                "deletedBy", event.getUpdatedBy(), "correlationId", correlationId,
                "timestamp", Instant.now()));

        metricsService.recordDnsRecordDeleted(event.getRecordType());

        log.info("DNS record deleted: domain={}, type={}, value={}",
            event.getDomainName(), event.getRecordType(), record.getValue());
    }

    private void processDnsZoneCreated(DnsUpdateEvent event, String correlationId) {
        // Create DNS zone
        dnsManagementService.createDnsZone(event.getDomainName(), event.getZoneConfig());

        // Log zone creation
        auditService.logInfrastructureEvent("DNS_ZONE_CREATED", event.getDomainName(),
            Map.of("zoneConfig", event.getZoneConfig(), "createdBy", event.getUpdatedBy(),
                "correlationId", correlationId, "timestamp", Instant.now()));

        // Start zone monitoring
        networkMonitoringService.startDnsZoneMonitoring(event.getDomainName());

        metricsService.recordDnsZoneCreated();

        log.info("DNS zone created: domain={}, config={}", event.getDomainName(), event.getZoneConfig());
    }

    private void processDnsZoneUpdated(DnsUpdateEvent event, String correlationId) {
        // Update DNS zone
        dnsManagementService.updateDnsZone(event.getDomainName(), event.getZoneConfig());

        // Log zone update
        auditService.logInfrastructureEvent("DNS_ZONE_UPDATED", event.getDomainName(),
            Map.of("zoneConfig", event.getZoneConfig(), "updatedBy", event.getUpdatedBy(),
                "correlationId", correlationId, "timestamp", Instant.now()));

        metricsService.recordDnsZoneUpdated();

        log.info("DNS zone updated: domain={}, config={}", event.getDomainName(), event.getZoneConfig());
    }

    private void processDnsZoneDeleted(DnsUpdateEvent event, String correlationId) {
        // Delete DNS zone
        dnsManagementService.deleteDnsZone(event.getDomainName());

        // Log zone deletion
        auditService.logInfrastructureEvent("DNS_ZONE_DELETED", event.getDomainName(),
            Map.of("deletedBy", event.getUpdatedBy(), "correlationId", correlationId,
                "timestamp", Instant.now()));

        // Stop zone monitoring
        networkMonitoringService.stopDnsZoneMonitoring(event.getDomainName());

        metricsService.recordDnsZoneDeleted();

        log.info("DNS zone deleted: domain={}", event.getDomainName());
    }

    private void processDnsPropagationStarted(DnsUpdateEvent event, String correlationId) {
        // Start propagation tracking
        networkMonitoringService.trackDnsPropagation(event.getDomainName(), event.getRecordType(),
            event.getValue(), correlationId);

        metricsService.recordDnsPropagationStarted();

        log.info("DNS propagation started: domain={}, type={}, value={}",
            event.getDomainName(), event.getRecordType(), event.getValue());
    }

    private void processDnsPropagationCompleted(DnsUpdateEvent event, String correlationId) {
        // Mark propagation as completed
        networkMonitoringService.markPropagationCompleted(event.getDomainName(), event.getRecordType(),
            event.getPropagationTimeMs());

        // Update DNS record status
        DnsRecord record = dnsRecordRepository.findByDomainNameAndRecordType(
            event.getDomainName(), event.getRecordType()).orElse(null);

        if (record != null) {
            record.setStatus("PROPAGATED");
            record.setPropagatedAt(LocalDateTime.now());
            record.setPropagationTimeMs(event.getPropagationTimeMs());
            dnsRecordRepository.save(record);
        }

        // Send notification
        kafkaTemplate.send("dns-status-updates", Map.of(
            "domainName", event.getDomainName(),
            "recordType", event.getRecordType(),
            "status", "PROPAGATED",
            "propagationTimeMs", event.getPropagationTimeMs(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordDnsPropagationCompleted(event.getPropagationTimeMs());

        log.info("DNS propagation completed: domain={}, type={}, time={}ms",
            event.getDomainName(), event.getRecordType(), event.getPropagationTimeMs());
    }

    private void processDnsPropagationFailed(DnsUpdateEvent event, String correlationId) {
        // Create incident for propagation failure
        securityIncidentService.createIncident(
            "DNS_PROPAGATION_FAILED",
            String.format("DNS propagation failed for domain %s", event.getDomainName()),
            "HIGH",
            Map.of("domainName", event.getDomainName(), "recordType", event.getRecordType(),
                "errorMessage", event.getErrorMessage(), "correlationId", correlationId)
        );

        // Update DNS record status
        DnsRecord record = dnsRecordRepository.findByDomainNameAndRecordType(
            event.getDomainName(), event.getRecordType()).orElse(null);

        if (record != null) {
            record.setStatus("PROPAGATION_FAILED");
            record.setErrorMessage(event.getErrorMessage());
            dnsRecordRepository.save(record);
        }

        // Send alert
        notificationService.sendOperationalAlert(
            "DNS Propagation Failed",
            String.format("DNS propagation failed for %s (%s): %s",
                event.getDomainName(), event.getRecordType(), event.getErrorMessage()),
            "HIGH"
        );

        metricsService.recordDnsPropagationFailed(event.getRecordType());

        log.error("DNS propagation failed: domain={}, type={}, error={}",
            event.getDomainName(), event.getRecordType(), event.getErrorMessage());
    }

    private void processDnsSecurityUpdate(DnsUpdateEvent event, String correlationId) {
        // Apply DNS security update
        dnsManagementService.applySecurityUpdate(event.getDomainName(), event.getSecurityConfig());

        // Log security update
        auditService.logSecurityEvent("DNS_SECURITY_UPDATE", event.getDomainName(),
            Map.of("securityConfig", event.getSecurityConfig(), "updatedBy", event.getUpdatedBy(),
                "correlationId", correlationId, "timestamp", Instant.now()));

        // Send security notification
        notificationService.sendSecurityAlert(
            "DNS Security Update Applied",
            String.format("DNS security update applied for domain %s", event.getDomainName()),
            "MEDIUM"
        );

        metricsService.recordDnsSecurityUpdate();

        log.info("DNS security update applied: domain={}, config={}",
            event.getDomainName(), event.getSecurityConfig());
    }

    private void processUnknownDnsEvent(DnsUpdateEvent event, String correlationId) {
        // Create incident for unknown event type
        securityIncidentService.createIncident(
            "UNKNOWN_DNS_UPDATE_EVENT",
            String.format("Unknown DNS update event type %s for domain %s",
                event.getEventType(), event.getDomainName()),
            "MEDIUM",
            Map.of("domainName", event.getDomainName(), "unknownEventType", event.getEventType(),
                "correlationId", correlationId)
        );

        log.warn("Unknown DNS update event: domain={}, eventType={}",
            event.getDomainName(), event.getEventType());
    }
}