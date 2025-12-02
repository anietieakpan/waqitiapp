package com.waqiti.risk.kafka;

import com.waqiti.common.events.GeographicRiskResultsEvent;
import com.waqiti.risk.domain.GeographicRiskResult;
import com.waqiti.risk.repository.GeographicRiskResultRepository;
import com.waqiti.risk.service.GeographicRiskService;
import com.waqiti.risk.service.RiskScoringService;
import com.waqiti.risk.service.RiskMetricsService;
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
public class GeographicRiskResultsConsumer {

    private final GeographicRiskResultRepository riskResultRepository;
    private final GeographicRiskService geographicRiskService;
    private final RiskScoringService riskScoringService;
    private final RiskMetricsService metricsService;
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
        successCounter = Counter.builder("geographic_risk_results_processed_total")
            .description("Total number of successfully processed geographic risk results events")
            .register(meterRegistry);
        errorCounter = Counter.builder("geographic_risk_results_errors_total")
            .description("Total number of geographic risk results processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("geographic_risk_results_processing_duration")
            .description("Time taken to process geographic risk results events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"geographic-risk-results"},
        groupId = "geographic-risk-results-service-group",
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
    @CircuitBreaker(name = "geographic-risk-results", fallbackMethod = "handleGeographicRiskResultsEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleGeographicRiskResultsEvent(
            @Payload GeographicRiskResultsEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("geo-risk-result-%s-p%d-o%d", event.getEntityId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getEntityId(), event.getAssessmentType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing geographic risk results: entityId={}, assessmentType={}, riskScore={}, location={}",
                event.getEntityId(), event.getAssessmentType(), event.getRiskScore(), event.getLocation());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getAssessmentType()) {
                case LOCATION_RISK_ASSESSMENT:
                    processLocationRiskAssessment(event, correlationId);
                    break;

                case GEOPOLITICAL_RISK_ASSESSMENT:
                    processGeopoliticalRiskAssessment(event, correlationId);
                    break;

                case TRAVEL_RISK_ASSESSMENT:
                    processTravelRiskAssessment(event, correlationId);
                    break;

                case SANCTIONS_SCREENING_RESULT:
                    processSanctionsScreeningResult(event, correlationId);
                    break;

                case CROSS_BORDER_RISK_ASSESSMENT:
                    processCrossBorderRiskAssessment(event, correlationId);
                    break;

                case GEOGRAPHIC_CLUSTERING_RESULT:
                    processGeographicClusteringResult(event, correlationId);
                    break;

                case COUNTRY_RISK_UPDATE:
                    processCountryRiskUpdate(event, correlationId);
                    break;

                default:
                    log.warn("Unknown geographic risk assessment type: {}", event.getAssessmentType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logRiskEvent("GEOGRAPHIC_RISK_RESULTS_PROCESSED", event.getEntityId(),
                Map.of("assessmentType", event.getAssessmentType(), "riskScore", event.getRiskScore(),
                    "location", event.getLocation(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process geographic risk results event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("geographic-risk-results-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleGeographicRiskResultsEventFallback(
            GeographicRiskResultsEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("geo-risk-result-fallback-%s-p%d-o%d", event.getEntityId(), partition, offset);

        log.error("Circuit breaker fallback triggered for geographic risk results: entityId={}, error={}",
            event.getEntityId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("geographic-risk-results-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Geographic Risk Results Circuit Breaker Triggered",
                String.format("Geographic risk results for entity %s failed: %s", event.getEntityId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltGeographicRiskResultsEvent(
            @Payload GeographicRiskResultsEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-geo-risk-result-%s-%d", event.getEntityId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Geographic risk results permanently failed: entityId={}, topic={}, error={}",
            event.getEntityId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logRiskEvent("GEOGRAPHIC_RISK_RESULTS_DLT_EVENT", event.getEntityId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "assessmentType", event.getAssessmentType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Geographic Risk Results Dead Letter Event",
                String.format("Geographic risk results for entity %s sent to DLT: %s", event.getEntityId(), exceptionMessage),
                Map.of("entityId", event.getEntityId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
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

    private void processLocationRiskAssessment(GeographicRiskResultsEvent event, String correlationId) {
        GeographicRiskResult result = GeographicRiskResult.builder()
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .assessmentType(event.getAssessmentType())
            .location(event.getLocation())
            .country(event.getCountry())
            .region(event.getRegion())
            .riskScore(event.getRiskScore())
            .riskLevel(determineRiskLevel(event.getRiskScore()))
            .assessmentDate(LocalDateTime.now())
            .correlationId(correlationId)
            .riskFactors(event.getRiskFactors())
            .confidenceScore(event.getConfidenceScore())
            .build();
        riskResultRepository.save(result);

        geographicRiskService.updateLocationRisk(event.getLocation(), event.getRiskScore());
        riskScoringService.updateEntityGeographicScore(event.getEntityId(), event.getRiskScore());

        // Trigger actions based on risk level
        String riskLevel = determineRiskLevel(event.getRiskScore());
        if ("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel)) {
            kafkaTemplate.send("geographic-alerts", Map.of(
                "entityId", event.getEntityId(),
                "alertType", "HIGH_RISK_LOCATION",
                "location", event.getLocation(),
                "riskScore", event.getRiskScore(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordRiskAssessment("LOCATION", riskLevel, event.getRiskScore());

        log.info("Location risk assessment processed: entityId={}, location={}, riskScore={}, level={}",
            event.getEntityId(), event.getLocation(), event.getRiskScore(), riskLevel);
    }

    private void processGeopoliticalRiskAssessment(GeographicRiskResultsEvent event, String correlationId) {
        GeographicRiskResult result = GeographicRiskResult.builder()
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .assessmentType(event.getAssessmentType())
            .location(event.getLocation())
            .country(event.getCountry())
            .region(event.getRegion())
            .riskScore(event.getRiskScore())
            .riskLevel(determineRiskLevel(event.getRiskScore()))
            .assessmentDate(LocalDateTime.now())
            .correlationId(correlationId)
            .geopoliticalFactors(event.getGeopoliticalFactors())
            .stabilityIndex(event.getStabilityIndex())
            .build();
        riskResultRepository.save(result);

        geographicRiskService.updateGeopoliticalRisk(event.getCountry(), event.getRiskScore());

        String riskLevel = determineRiskLevel(event.getRiskScore());
        if ("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel)) {
            // Send to risk management team
            notificationService.sendHighPriorityNotification("risk-management-team",
                "High Geopolitical Risk Detected",
                String.format("High geopolitical risk detected for entity %s in %s (Score: %d)",
                    event.getEntityId(), event.getCountry(), event.getRiskScore()),
                correlationId);

            // Send to country risk monitoring
            kafkaTemplate.send("country-risk-monitoring", Map.of(
                "country", event.getCountry(),
                "riskScore", event.getRiskScore(),
                "stabilityIndex", event.getStabilityIndex(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordRiskAssessment("GEOPOLITICAL", riskLevel, event.getRiskScore());

        log.info("Geopolitical risk assessment processed: entityId={}, country={}, riskScore={}, level={}",
            event.getEntityId(), event.getCountry(), event.getRiskScore(), riskLevel);
    }

    private void processTravelRiskAssessment(GeographicRiskResultsEvent event, String correlationId) {
        GeographicRiskResult result = GeographicRiskResult.builder()
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .assessmentType(event.getAssessmentType())
            .location(event.getLocation())
            .country(event.getCountry())
            .region(event.getRegion())
            .riskScore(event.getRiskScore())
            .riskLevel(determineRiskLevel(event.getRiskScore()))
            .assessmentDate(LocalDateTime.now())
            .correlationId(correlationId)
            .travelPattern(event.getTravelPattern())
            .velocityScore(event.getVelocityScore())
            .build();
        riskResultRepository.save(result);

        geographicRiskService.updateTravelRisk(event.getEntityId(), event.getTravelPattern());

        String riskLevel = determineRiskLevel(event.getRiskScore());
        if ("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel)) {
            kafkaTemplate.send("geographic-alerts", Map.of(
                "entityId", event.getEntityId(),
                "alertType", "TRAVEL_PATTERN_ANOMALY",
                "travelPattern", event.getTravelPattern(),
                "riskScore", event.getRiskScore(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordRiskAssessment("TRAVEL", riskLevel, event.getRiskScore());

        log.info("Travel risk assessment processed: entityId={}, pattern={}, riskScore={}, level={}",
            event.getEntityId(), event.getTravelPattern(), event.getRiskScore(), riskLevel);
    }

    private void processSanctionsScreeningResult(GeographicRiskResultsEvent event, String correlationId) {
        GeographicRiskResult result = GeographicRiskResult.builder()
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .assessmentType(event.getAssessmentType())
            .location(event.getLocation())
            .country(event.getCountry())
            .region(event.getRegion())
            .riskScore(event.getRiskScore())
            .riskLevel("CRITICAL") // All sanctions are critical
            .assessmentDate(LocalDateTime.now())
            .correlationId(correlationId)
            .sanctionsMatch(event.getSanctionsMatch())
            .sanctionsList(event.getSanctionsList())
            .matchConfidence(event.getMatchConfidence())
            .build();
        riskResultRepository.save(result);

        geographicRiskService.processSanctionsScreeningResult(event.getEntityId(), event.getSanctionsMatch());

        // Critical alert for any sanctions match
        if (event.getSanctionsMatch() != null && !event.getSanctionsMatch().isEmpty()) {
            notificationService.sendCriticalAlert(
                "Sanctions Screening Match Detected",
                String.format("CRITICAL: Sanctions match detected for entity %s - %s",
                    event.getEntityId(), event.getSanctionsMatch()),
                Map.of("entityId", event.getEntityId(), "sanctionsMatch", event.getSanctionsMatch(),
                       "correlationId", correlationId, "matchConfidence", event.getMatchConfidence())
            );

            // Immediate escalation to compliance
            kafkaTemplate.send("compliance-alerts", Map.of(
                "entityId", event.getEntityId(),
                "alertType", "SANCTIONS_MATCH",
                "sanctionsMatch", event.getSanctionsMatch(),
                "matchConfidence", event.getMatchConfidence(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            // Block entity immediately
            kafkaTemplate.send("entity-blocking-events", Map.of(
                "entityId", event.getEntityId(),
                "blockReason", "SANCTIONS_MATCH",
                "sanctionsDetails", event.getSanctionsMatch(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordRiskAssessment("SANCTIONS_SCREENING", "CRITICAL", event.getRiskScore());

        log.error("Sanctions screening result processed: entityId={}, match={}, confidence={}",
            event.getEntityId(), event.getSanctionsMatch(), event.getMatchConfidence());
    }

    private void processCrossBorderRiskAssessment(GeographicRiskResultsEvent event, String correlationId) {
        GeographicRiskResult result = GeographicRiskResult.builder()
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .assessmentType(event.getAssessmentType())
            .location(event.getLocation())
            .country(event.getCountry())
            .region(event.getRegion())
            .riskScore(event.getRiskScore())
            .riskLevel(determineRiskLevel(event.getRiskScore()))
            .assessmentDate(LocalDateTime.now())
            .correlationId(correlationId)
            .crossBorderFactors(event.getCrossBorderFactors())
            .jurisdictionalRisk(event.getJurisdictionalRisk())
            .build();
        riskResultRepository.save(result);

        geographicRiskService.updateCrossBorderRisk(event.getEntityId(), event.getCrossBorderFactors());

        String riskLevel = determineRiskLevel(event.getRiskScore());
        if ("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel)) {
            // Send to AML team for enhanced monitoring
            kafkaTemplate.send("aml-enhanced-monitoring", Map.of(
                "entityId", event.getEntityId(),
                "monitoringReason", "HIGH_CROSS_BORDER_RISK",
                "riskScore", event.getRiskScore(),
                "crossBorderFactors", event.getCrossBorderFactors(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordRiskAssessment("CROSS_BORDER", riskLevel, event.getRiskScore());

        log.info("Cross-border risk assessment processed: entityId={}, riskScore={}, level={}",
            event.getEntityId(), event.getRiskScore(), riskLevel);
    }

    private void processGeographicClusteringResult(GeographicRiskResultsEvent event, String correlationId) {
        GeographicRiskResult result = GeographicRiskResult.builder()
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .assessmentType(event.getAssessmentType())
            .location(event.getLocation())
            .country(event.getCountry())
            .region(event.getRegion())
            .riskScore(event.getRiskScore())
            .riskLevel(determineRiskLevel(event.getRiskScore()))
            .assessmentDate(LocalDateTime.now())
            .correlationId(correlationId)
            .clusterDetails(event.getClusterDetails())
            .clusterRiskScore(event.getClusterRiskScore())
            .build();
        riskResultRepository.save(result);

        geographicRiskService.processGeographicClustering(event.getEntityId(), event.getClusterDetails());

        String riskLevel = determineRiskLevel(event.getRiskScore());
        if ("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel)) {
            // Send to behavioral analysis for further investigation
            kafkaTemplate.send("behavioral-analysis-events", Map.of(
                "entityId", event.getEntityId(),
                "analysisType", "GEOGRAPHIC_CLUSTERING",
                "clusterDetails", event.getClusterDetails(),
                "riskScore", event.getRiskScore(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordRiskAssessment("GEOGRAPHIC_CLUSTERING", riskLevel, event.getRiskScore());

        log.info("Geographic clustering result processed: entityId={}, cluster={}, riskScore={}",
            event.getEntityId(), event.getClusterDetails(), event.getRiskScore());
    }

    private void processCountryRiskUpdate(GeographicRiskResultsEvent event, String correlationId) {
        // Update country risk profile
        geographicRiskService.updateCountryRiskProfile(event.getCountry(), event.getRiskScore());

        // Store the country risk update
        GeographicRiskResult result = GeographicRiskResult.builder()
            .entityId(event.getCountry()) // Use country as entityId for country-level assessments
            .entityType("COUNTRY")
            .assessmentType(event.getAssessmentType())
            .location(event.getLocation())
            .country(event.getCountry())
            .region(event.getRegion())
            .riskScore(event.getRiskScore())
            .riskLevel(determineRiskLevel(event.getRiskScore()))
            .assessmentDate(LocalDateTime.now())
            .correlationId(correlationId)
            .countryRiskFactors(event.getCountryRiskFactors())
            .economicIndicators(event.getEconomicIndicators())
            .build();
        riskResultRepository.save(result);

        String riskLevel = determineRiskLevel(event.getRiskScore());

        // Notify all relevant entities in this country about risk change
        kafkaTemplate.send("country-risk-updates", Map.of(
            "country", event.getCountry(),
            "newRiskScore", event.getRiskScore(),
            "riskLevel", riskLevel,
            "countryRiskFactors", event.getCountryRiskFactors(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if ("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel)) {
            // Send alert to risk management team
            notificationService.sendHighPriorityNotification("country-risk-team",
                "Country Risk Level Updated",
                String.format("Country %s risk level updated to %s (Score: %d)",
                    event.getCountry(), riskLevel, event.getRiskScore()),
                correlationId);
        }

        metricsService.recordRiskAssessment("COUNTRY_RISK", riskLevel, event.getRiskScore());

        log.info("Country risk update processed: country={}, riskScore={}, level={}",
            event.getCountry(), event.getRiskScore(), riskLevel);
    }

    private String determineRiskLevel(Integer riskScore) {
        if (riskScore >= 90) return "CRITICAL";
        if (riskScore >= 75) return "HIGH";
        if (riskScore >= 50) return "MEDIUM";
        if (riskScore >= 25) return "LOW";
        return "VERY_LOW";
    }
}