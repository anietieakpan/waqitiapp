package com.waqiti.compliance.kafka;

import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.compliance.domain.ComplianceAlert;
import com.waqiti.compliance.domain.AMLScreening;
import com.waqiti.compliance.repository.ComplianceAlertRepository;
import com.waqiti.compliance.repository.CustomerRiskProfileRepository;
import com.waqiti.compliance.service.AmlService;
import com.waqiti.compliance.service.ComplianceMetricsService;
import com.waqiti.compliance.service.AuditService;
import com.waqiti.compliance.service.ComplianceNotificationService;
import com.waqiti.compliance.service.SanctionsScreeningService;
import com.waqiti.compliance.service.OFACSanctionsScreeningService;
import com.waqiti.compliance.service.ScreeningService;
import com.waqiti.compliance.service.ComprehensiveComplianceScreeningService;
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
public class AMLScreeningEventsConsumer {

    private final ComplianceAlertRepository alertRepository;
    private final CustomerRiskProfileRepository riskProfileRepository;
    private final UniversalDLQHandler universalDLQHandler;
    private final AmlService amlService;
    private final ComplianceMetricsService metricsService;
    private final AuditService auditService;
    private final ComplianceNotificationService notificationService;
    private final SanctionsScreeningService sanctionsScreeningService;
    private final OFACSanctionsScreeningService ofacScreeningService;
    private final ScreeningService screeningService;
    private final ComprehensiveComplianceScreeningService comprehensiveScreeningService;
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
        successCounter = Counter.builder("aml_screening_events_processed_total")
            .description("Total number of successfully processed AML screening events")
            .register(meterRegistry);
        errorCounter = Counter.builder("aml_screening_events_errors_total")
            .description("Total number of AML screening event processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("aml_screening_events_processing_duration")
            .description("Time taken to process AML screening events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"aml-screening-events", "sanctions-screening-results", "pep-screening-events", "aml-customer-screening"},
        groupId = "aml-screening-compliance-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "aml-screening-events", fallbackMethod = "handleAMLScreeningEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAMLScreeningEvent(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String entityId = String.valueOf(event.get("entityId"));
        String screeningType = String.valueOf(event.get("screeningType"));
        String correlationId = String.format("aml-screening-%s-p%d-o%d", entityId, partition, offset);
        String eventKey = String.format("%s-%s-%s", entityId, screeningType, event.get("timestamp"));

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("AML screening event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing AML screening event: entityId={}, screeningType={}, status={}",
                entityId, screeningType, event.get("status"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (screeningType) {
                case "SANCTIONS_SCREENING":
                    processSanctionsScreening(event, correlationId);
                    break;

                case "PEP_SCREENING":
                    processPEPScreening(event, correlationId);
                    break;

                case "ADVERSE_MEDIA_SCREENING":
                    processAdverseMediaScreening(event, correlationId);
                    break;

                case "WATCHLIST_SCREENING":
                    processWatchlistScreening(event, correlationId);
                    break;

                case "OFAC_SCREENING":
                    processOFACScreening(event, correlationId);
                    break;

                case "ONGOING_MONITORING":
                    processOngoingMonitoring(event, correlationId);
                    break;

                case "ENHANCED_DUE_DILIGENCE":
                    processEnhancedDueDiligence(event, correlationId);
                    break;

                case "CUSTOMER_RISK_ASSESSMENT":
                    processCustomerRiskAssessment(event, correlationId);
                    break;

                case "TRANSACTION_SCREENING":
                    processTransactionScreening(event, correlationId);
                    break;

                case "REGULATORY_SCREENING":
                    processRegulatoryScreening(event, correlationId);
                    break;

                default:
                    log.warn("Unknown AML screening type: {}", screeningType);
                    processGenericScreening(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logCriticalComplianceEvent("AML_SCREENING_EVENT_PROCESSED", entityId,
                Map.of("screeningType", screeningType, "status", event.get("status"),
                    "correlationId", correlationId, "timestamp", Instant.now(),
                    "riskLevel", event.get("riskLevel"), "requiresReview", event.get("requiresReview")));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process AML screening event: {}", e.getMessage(), e);

            // Send to DLQ for retry/parking
            try {
                org.apache.kafka.clients.consumer.ConsumerRecord<String, Map<String, Object>> consumerRecord =
                    new org.apache.kafka.clients.consumer.ConsumerRecord<>(
                        "aml-screening-events", partition, offset, entityId, event);
                universalDLQHandler.handleFailedMessage(consumerRecord, e);
            } catch (Exception dlqEx) {
                log.error("CRITICAL: Failed to send AML screening event to DLQ: {}", entityId, dlqEx);
            }

            // Send fallback event
            kafkaTemplate.send("aml-screening-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3, "screeningType", screeningType));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAMLScreeningEventFallback(
            Map<String, Object> event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String entityId = String.valueOf(event.get("entityId"));
        String correlationId = String.format("aml-screening-fallback-%s-p%d-o%d", entityId, partition, offset);

        log.error("Circuit breaker fallback triggered for AML screening: entityId={}, error={}",
            entityId, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("aml-screening-events-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical notification to compliance team
        try {
            notificationService.sendCriticalComplianceAlert(
                "AML Screening Circuit Breaker Triggered",
                String.format("AML screening failed for entity %s: %s", entityId, ex.getMessage()),
                "CRITICAL",
                Map.of("entityId", entityId, "screeningType", event.get("screeningType"), "correlationId", correlationId)
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send AML screening notification: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAMLScreeningEvent(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String entityId = String.valueOf(event.get("entityId"));
        String correlationId = String.format("dlt-aml-screening-%s-%d", entityId, System.currentTimeMillis());

        log.error("Dead letter topic handler - AML screening permanently failed: entityId={}, topic={}, error={}",
            entityId, topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logCriticalComplianceEvent("AML_SCREENING_DLT_EVENT", entityId,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "screeningType", event.get("screeningType"), "correlationId", correlationId,
                "requiresManualIntervention", true, "regulatoryImplication", true,
                "timestamp", Instant.now()));

        // Send emergency alert to compliance leadership
        try {
            notificationService.sendEmergencyComplianceAlert(
                "CRITICAL: AML Screening Dead Letter Event",
                String.format("AML screening for entity %s sent to DLT - Manual intervention required: %s",
                    entityId, exceptionMessage),
                Map.of("entityId", entityId, "topic", topic, "correlationId", correlationId,
                    "regulatoryRisk", "HIGH", "immediateActionRequired", true)
            );
        } catch (Exception ex) {
            log.error("Failed to send emergency AML screening DLT alert: {}", ex.getMessage());
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

    private void processSanctionsScreening(Map<String, Object> event, String correlationId) {
        String entityId = String.valueOf(event.get("entityId"));
        String matchType = String.valueOf(event.get("matchType"));
        Boolean hasMatch = Boolean.valueOf(String.valueOf(event.get("hasMatch")));

        // Perform sanctions screening
        sanctionsScreeningService.processSanctionsScreening(entityId, event);

        if (hasMatch) {
            ComplianceAlert alert = ComplianceAlert.builder()
                .entityId(entityId)
                .alertType("SANCTIONS_MATCH")
                .description(String.format("Sanctions match found: %s", matchType))
                .severity("CRITICAL")
                .status("IMMEDIATE_REVIEW_REQUIRED")
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();
            alertRepository.save(alert);

            // Send immediate notification for sanctions matches
            notificationService.sendEmergencyComplianceAlert(
                "CRITICAL: Sanctions Match Detected",
                String.format("Sanctions match detected for entity %s: %s", entityId, matchType),
                Map.of("entityId", entityId, "matchType", matchType, "correlationId", correlationId,
                    "requiresAccountFreeze", true)
            );
        }

        log.info("Sanctions screening processed: entityId={}, hasMatch={}, matchType={}",
            entityId, hasMatch, matchType);
    }

    private void processPEPScreening(Map<String, Object> event, String correlationId) {
        String entityId = String.valueOf(event.get("entityId"));
        Boolean isPEP = Boolean.valueOf(String.valueOf(event.get("isPEP")));
        String pepCategory = String.valueOf(event.get("pepCategory"));

        if (isPEP) {
            ComplianceAlert alert = ComplianceAlert.builder()
                .entityId(entityId)
                .alertType("PEP_DETECTED")
                .description(String.format("Politically Exposed Person detected: %s", pepCategory))
                .severity("HIGH")
                .status("ENHANCED_DUE_DILIGENCE_REQUIRED")
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();
            alertRepository.save(alert);

            // Initiate enhanced due diligence for PEPs
            comprehensiveScreeningService.initiateEnhancedDueDiligence(entityId, pepCategory, correlationId);

            notificationService.sendComplianceAlert(
                "PEP Detected - Enhanced Due Diligence Required",
                String.format("PEP detected for entity %s: %s", entityId, pepCategory),
                "HIGH",
                correlationId
            );
        }

        log.info("PEP screening processed: entityId={}, isPEP={}, category={}", entityId, isPEP, pepCategory);
    }

    private void processAdverseMediaScreening(Map<String, Object> event, String correlationId) {
        String entityId = String.valueOf(event.get("entityId"));
        Boolean hasAdverseMedia = Boolean.valueOf(String.valueOf(event.get("hasAdverseMedia")));
        String mediaType = String.valueOf(event.get("mediaType"));

        if (hasAdverseMedia) {
            ComplianceAlert alert = ComplianceAlert.builder()
                .entityId(entityId)
                .alertType("ADVERSE_MEDIA")
                .description(String.format("Adverse media found: %s", mediaType))
                .severity("MEDIUM")
                .status("UNDER_REVIEW")
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();
            alertRepository.save(alert);

            notificationService.sendComplianceAlert(
                "Adverse Media Detected",
                String.format("Adverse media found for entity %s: %s", entityId, mediaType),
                "MEDIUM",
                correlationId
            );
        }

        log.info("Adverse media screening processed: entityId={}, hasAdverseMedia={}, type={}",
            entityId, hasAdverseMedia, mediaType);
    }

    private void processWatchlistScreening(Map<String, Object> event, String correlationId) {
        String entityId = String.valueOf(event.get("entityId"));
        Boolean hasWatchlistMatch = Boolean.valueOf(String.valueOf(event.get("hasWatchlistMatch")));
        String watchlistType = String.valueOf(event.get("watchlistType"));

        if (hasWatchlistMatch) {
            ComplianceAlert alert = ComplianceAlert.builder()
                .entityId(entityId)
                .alertType("WATCHLIST_MATCH")
                .description(String.format("Watchlist match found: %s", watchlistType))
                .severity("HIGH")
                .status("IMMEDIATE_REVIEW_REQUIRED")
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();
            alertRepository.save(alert);

            notificationService.sendUrgentComplianceAlert(
                "Watchlist Match Detected",
                String.format("Watchlist match found for entity %s: %s", entityId, watchlistType),
                Map.of("entityId", entityId, "watchlistType", watchlistType, "correlationId", correlationId)
            );
        }

        log.info("Watchlist screening processed: entityId={}, hasMatch={}, type={}",
            entityId, hasWatchlistMatch, watchlistType);
    }

    private void processOFACScreening(Map<String, Object> event, String correlationId) {
        String entityId = String.valueOf(event.get("entityId"));
        Boolean hasOFACMatch = Boolean.valueOf(String.valueOf(event.get("hasOFACMatch")));
        String matchScore = String.valueOf(event.get("matchScore"));

        // Process OFAC screening through dedicated service
        ofacScreeningService.processOFACScreening(entityId, event);

        if (hasOFACMatch) {
            ComplianceAlert alert = ComplianceAlert.builder()
                .entityId(entityId)
                .alertType("OFAC_MATCH")
                .description(String.format("OFAC sanctions match found (score: %s)", matchScore))
                .severity("CRITICAL")
                .status("IMMEDIATE_BLOCK_REQUIRED")
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();
            alertRepository.save(alert);

            // OFAC matches require immediate account blocking
            notificationService.sendEmergencyComplianceAlert(
                "EMERGENCY: OFAC Sanctions Match",
                String.format("OFAC match for entity %s - Immediate blocking required (score: %s)", entityId, matchScore),
                Map.of("entityId", entityId, "matchScore", matchScore, "correlationId", correlationId,
                    "blockAccount", true, "freezeAssets", true)
            );
        }

        log.warn("OFAC screening processed: entityId={}, hasMatch={}, score={}", entityId, hasOFACMatch, matchScore);
    }

    private void processOngoingMonitoring(Map<String, Object> event, String correlationId) {
        String entityId = String.valueOf(event.get("entityId"));
        String monitoringType = String.valueOf(event.get("monitoringType"));
        Boolean alertTriggered = Boolean.valueOf(String.valueOf(event.get("alertTriggered")));

        if (alertTriggered) {
            ComplianceAlert alert = ComplianceAlert.builder()
                .entityId(entityId)
                .alertType("ONGOING_MONITORING_ALERT")
                .description(String.format("Ongoing monitoring alert: %s", monitoringType))
                .severity("MEDIUM")
                .status("UNDER_REVIEW")
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();
            alertRepository.save(alert);
        }

        log.info("Ongoing monitoring processed: entityId={}, type={}, alertTriggered={}",
            entityId, monitoringType, alertTriggered);
    }

    private void processEnhancedDueDiligence(Map<String, Object> event, String correlationId) {
        String entityId = String.valueOf(event.get("entityId"));
        String eddType = String.valueOf(event.get("eddType"));
        String status = String.valueOf(event.get("status"));

        comprehensiveScreeningService.processEnhancedDueDiligence(entityId, eddType, event);

        ComplianceAlert alert = ComplianceAlert.builder()
            .entityId(entityId)
            .alertType("ENHANCED_DUE_DILIGENCE")
            .description(String.format("Enhanced Due Diligence %s: %s", status, eddType))
            .severity("HIGH")
            .status("UNDER_REVIEW")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        alertRepository.save(alert);

        log.info("Enhanced due diligence processed: entityId={}, type={}, status={}", entityId, eddType, status);
    }

    private void processCustomerRiskAssessment(Map<String, Object> event, String correlationId) {
        String entityId = String.valueOf(event.get("entityId"));
        String riskLevel = String.valueOf(event.get("riskLevel"));
        Double riskScore = Double.valueOf(String.valueOf(event.get("riskScore")));

        // Update customer risk profile
        screeningService.updateCustomerRiskProfile(entityId, riskLevel, riskScore, correlationId);

        if ("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel)) {
            ComplianceAlert alert = ComplianceAlert.builder()
                .entityId(entityId)
                .alertType("HIGH_RISK_CUSTOMER")
                .description(String.format("High risk customer assessment: %s (score: %.2f)", riskLevel, riskScore))
                .severity(riskLevel)
                .status("ENHANCED_MONITORING_REQUIRED")
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();
            alertRepository.save(alert);
        }

        log.info("Customer risk assessment processed: entityId={}, riskLevel={}, score={}",
            entityId, riskLevel, riskScore);
    }

    private void processTransactionScreening(Map<String, Object> event, String correlationId) {
        String entityId = String.valueOf(event.get("entityId"));
        String transactionId = String.valueOf(event.get("transactionId"));
        Boolean screeningPassed = Boolean.valueOf(String.valueOf(event.get("screeningPassed")));

        if (!screeningPassed) {
            ComplianceAlert alert = ComplianceAlert.builder()
                .entityId(entityId)
                .alertType("TRANSACTION_SCREENING_FAILED")
                .description(String.format("Transaction screening failed for transaction: %s", transactionId))
                .severity("HIGH")
                .status("TRANSACTION_BLOCKED")
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();
            alertRepository.save(alert);

            notificationService.sendUrgentComplianceAlert(
                "Transaction Screening Failed",
                String.format("Transaction %s for entity %s failed screening", transactionId, entityId),
                Map.of("entityId", entityId, "transactionId", transactionId, "correlationId", correlationId)
            );
        }

        log.info("Transaction screening processed: entityId={}, transactionId={}, passed={}",
            entityId, transactionId, screeningPassed);
    }

    private void processRegulatoryScreening(Map<String, Object> event, String correlationId) {
        String entityId = String.valueOf(event.get("entityId"));
        String regulatoryType = String.valueOf(event.get("regulatoryType"));
        String complianceStatus = String.valueOf(event.get("complianceStatus"));

        if ("NON_COMPLIANT".equals(complianceStatus)) {
            ComplianceAlert alert = ComplianceAlert.builder()
                .entityId(entityId)
                .alertType("REGULATORY_NON_COMPLIANCE")
                .description(String.format("Regulatory non-compliance detected: %s", regulatoryType))
                .severity("CRITICAL")
                .status("IMMEDIATE_ACTION_REQUIRED")
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();
            alertRepository.save(alert);

            notificationService.sendCriticalComplianceAlert(
                "Regulatory Non-Compliance Detected",
                String.format("Entity %s is non-compliant with %s requirements", entityId, regulatoryType),
                "CRITICAL",
                Map.of("entityId", entityId, "regulatoryType", regulatoryType, "correlationId", correlationId)
            );
        }

        log.info("Regulatory screening processed: entityId={}, type={}, status={}",
            entityId, regulatoryType, complianceStatus);
    }

    private void processGenericScreening(Map<String, Object> event, String correlationId) {
        String entityId = String.valueOf(event.get("entityId"));
        String screeningType = String.valueOf(event.get("screeningType"));

        ComplianceAlert alert = ComplianceAlert.builder()
            .entityId(entityId)
            .alertType(screeningType)
            .description(String.valueOf(event.get("description")))
            .severity(String.valueOf(event.getOrDefault("severity", "MEDIUM")))
            .status("UNDER_REVIEW")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        alertRepository.save(alert);

        log.info("Generic screening processed: entityId={}, screeningType={}", entityId, screeningType);
    }
}