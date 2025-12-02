package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.compliance.service.*;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.metrics.MetricsService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class EnhancedMonitoringEventsConsumer {

    private final EnhancedMonitoringService enhancedMonitoringService;
    private final ComplianceWorkflowService complianceWorkflowService;
    private final AuditService auditService;
    private final MetricsService metricsService;

    private final Map<String, Instant> processedEventIds = new ConcurrentHashMap<>();

    @KafkaListener(
        topics = {"enhanced-monitoring-events"},
        groupId = "compliance-service-enhanced-monitoring-processor"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    @Transactional
    @CircuitBreaker(name = "enhanced-monitoring-processor")
    @Retry(name = "enhanced-monitoring-processor")
    public void processEnhancedMonitoringEvent(
            @Payload @Valid GenericKafkaEvent event,
            Acknowledgment acknowledgment) {

        String eventId = event.getEventId();
        log.info("Processing enhanced monitoring event: {}", eventId);

        try {
            if (processedEventIds.containsKey(eventId)) {
                acknowledgment.acknowledge();
                return;
            }

            processedEventIds.put(eventId, Instant.now());
            
            Map<String, Object> payload = event.getPayload();
            String customerId = (String) payload.get("customerId");
            String monitoringType = (String) payload.get("monitoringType");
            String riskLevel = (String) payload.get("riskLevel");

            enhancedMonitoringService.processMonitoringEvent(customerId, monitoringType, riskLevel);
            
            if ("HIGH".equals(riskLevel)) {
                complianceWorkflowService.escalateHighRiskMonitoring(customerId, monitoringType);
            }

            auditService.auditSecurityEvent(
                "ENHANCED_MONITORING_PROCESSED",
                customerId,
                "Enhanced monitoring event processed",
                Map.of("eventId", eventId, "monitoringType", monitoringType, "riskLevel", riskLevel)
            );

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process enhanced monitoring event: {}", eventId, e);
            throw new RuntimeException("Enhanced monitoring processing failed", e);
        }
    }
}