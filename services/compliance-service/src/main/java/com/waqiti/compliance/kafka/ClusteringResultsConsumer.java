package com.waqiti.compliance.kafka;

import com.waqiti.common.events.ClusteringResultsEvent;
import com.waqiti.compliance.domain.ClusteringResult;
import com.waqiti.compliance.repository.ClusteringResultRepository;
import com.waqiti.compliance.service.ClusteringAnalysisService;
import com.waqiti.compliance.service.ComplianceReviewService;
import com.waqiti.compliance.metrics.ComplianceMetricsService;
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
public class ClusteringResultsConsumer {

    private final ClusteringResultRepository clusteringResultRepository;
    private final ClusteringAnalysisService clusteringAnalysisService;
    private final ComplianceReviewService complianceReviewService;
    private final ComplianceMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("clustering_results_processed_total")
            .description("Total number of successfully processed clustering results events")
            .register(meterRegistry);
        errorCounter = Counter.builder("clustering_results_errors_total")
            .description("Total number of clustering results processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("clustering_results_processing_duration")
            .description("Time taken to process clustering results events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"clustering-results"},
        groupId = "compliance-clustering-results-group",
        containerFactory = "criticalComplianceKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "clustering-results", fallbackMethod = "handleClusteringResultsEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleClusteringResultsEvent(
            @Payload ClusteringResultsEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("clustering-%s-p%d-o%d", event.getClusteringId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getClusteringId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing clustering results: clusteringId={}, analysisType={}, riskLevel={}",
                event.getClusteringId(), event.getAnalysisType(), event.getRiskLevel());

            cleanExpiredEntries();

            switch (event.getEventType()) {
                case CLUSTERING_COMPLETED:
                    processClusteringCompleted(event, correlationId);
                    break;

                case HIGH_RISK_CLUSTER_IDENTIFIED:
                    processHighRiskCluster(event, correlationId);
                    break;

                case CLUSTER_PATTERN_DETECTED:
                    processClusterPattern(event, correlationId);
                    break;

                case CLUSTER_ANOMALY_FOUND:
                    processClusterAnomaly(event, correlationId);
                    break;

                case CLUSTERING_VALIDATION_REQUIRED:
                    processValidationRequired(event, correlationId);
                    break;

                case CLUSTERING_REVIEW_ESCALATED:
                    processReviewEscalation(event, correlationId);
                    break;

                default:
                    log.warn("Unknown clustering results event type: {}", event.getEventType());
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logComplianceEvent("CLUSTERING_RESULTS_EVENT_PROCESSED", event.getClusteringId(),
                Map.of("eventType", event.getEventType(), "analysisType", event.getAnalysisType(),
                    "riskLevel", event.getRiskLevel(), "correlationId", correlationId,
                    "clusterCount", event.getClusterCount(), "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process clustering results event: {}", e.getMessage(), e);

            kafkaTemplate.send("compliance-clustering-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleClusteringResultsEventFallback(
            ClusteringResultsEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("clustering-fallback-%s-p%d-o%d", event.getClusteringId(), partition, offset);

        log.error("Circuit breaker fallback triggered for clustering results: clusteringId={}, error={}",
            event.getClusteringId(), ex.getMessage());

        kafkaTemplate.send("clustering-results-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        try {
            notificationService.sendOperationalAlert(
                "Clustering Results Circuit Breaker Triggered",
                String.format("Clustering analysis %s failed: %s", event.getClusteringId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltClusteringResultsEvent(
            @Payload ClusteringResultsEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-clustering-%s-%d", event.getClusteringId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Clustering results permanently failed: clusteringId={}, topic={}, error={}",
            event.getClusteringId(), topic, exceptionMessage);

        auditService.logComplianceEvent("CLUSTERING_RESULTS_DLT_EVENT", event.getClusteringId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        try {
            notificationService.sendCriticalAlert(
                "Clustering Results Dead Letter Event",
                String.format("Clustering analysis %s sent to DLT: %s", event.getClusteringId(), exceptionMessage),
                Map.of("clusteringId", event.getClusteringId(), "topic", topic, "correlationId", correlationId)
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

    private void processClusteringCompleted(ClusteringResultsEvent event, String correlationId) {
        ClusteringResult result = ClusteringResult.builder()
            .clusteringId(event.getClusteringId())
            .analysisType(event.getAnalysisType())
            .riskLevel(event.getRiskLevel())
            .clusterCount(event.getClusterCount())
            .analysisResults(event.getAnalysisResults())
            .completedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        clusteringResultRepository.save(result);

        clusteringAnalysisService.processClusters(event.getClusteringId(), event.getAnalysisResults());

        if ("HIGH".equals(event.getRiskLevel()) || "CRITICAL".equals(event.getRiskLevel())) {
            complianceReviewService.initiateReview(event.getClusteringId(), "CLUSTERING_HIGH_RISK",
                "High-risk clusters identified in analysis", correlationId);
        }

        kafkaTemplate.send("compliance-dashboard", Map.of(
            "eventType", "CLUSTERING_ANALYSIS_COMPLETED",
            "clusteringId", event.getClusteringId(),
            "analysisType", event.getAnalysisType(),
            "riskLevel", event.getRiskLevel(),
            "clusterCount", event.getClusterCount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordClusteringAnalysisCompleted(event.getAnalysisType(), event.getRiskLevel());

        log.info("Clustering analysis completed: clusteringId={}, clusters={}, riskLevel={}",
            event.getClusteringId(), event.getClusterCount(), event.getRiskLevel());
    }

    private void processHighRiskCluster(ClusteringResultsEvent event, String correlationId) {
        clusteringAnalysisService.flagHighRiskCluster(event.getClusteringId(), event.getHighRiskClusters());

        complianceReviewService.initiateUrgentReview(event.getClusteringId(), "HIGH_RISK_CLUSTER",
            "High-risk cluster pattern detected", correlationId);

        kafkaTemplate.send("compliance-alerts", Map.of(
            "alertType", "HIGH_RISK_CLUSTER_IDENTIFIED",
            "clusteringId", event.getClusteringId(),
            "riskLevel", "HIGH",
            "clusterDetails", event.getHighRiskClusters(),
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendComplianceAlert("High Risk Cluster Identified",
            String.format("High-risk cluster detected in analysis %s", event.getClusteringId()),
            "HIGH", correlationId);

        metricsService.recordHighRiskClusterIdentified();

        log.warn("High-risk cluster identified: clusteringId={}, clusters={}",
            event.getClusteringId(), event.getHighRiskClusters().size());
    }

    private void processClusterPattern(ClusteringResultsEvent event, String correlationId) {
        clusteringAnalysisService.analyzePattern(event.getClusteringId(), event.getPatternDetails());

        if ("SUSPICIOUS".equals(event.getPatternType()) || "HIGH_RISK".equals(event.getPatternType())) {
            kafkaTemplate.send("compliance-review-queue", Map.of(
                "reviewType", "CLUSTER_PATTERN_ANALYSIS",
                "clusteringId", event.getClusteringId(),
                "patternType", event.getPatternType(),
                "priority", "MEDIUM",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordClusterPatternDetected(event.getPatternType());

        log.info("Cluster pattern detected: clusteringId={}, patternType={}",
            event.getClusteringId(), event.getPatternType());
    }

    private void processClusterAnomaly(ClusteringResultsEvent event, String correlationId) {
        clusteringAnalysisService.processAnomaly(event.getClusteringId(), event.getAnomalyDetails());

        complianceReviewService.queueAnomalyReview(event.getClusteringId(), event.getAnomalyDetails(), correlationId);

        kafkaTemplate.send("compliance-incidents", Map.of(
            "incidentType", "CLUSTERING_ANOMALY",
            "clusteringId", event.getClusteringId(),
            "anomalyDetails", event.getAnomalyDetails(),
            "severity", event.getAnomalySeverity(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if ("HIGH".equals(event.getAnomalySeverity()) || "CRITICAL".equals(event.getAnomalySeverity())) {
            notificationService.sendComplianceAlert("Clustering Anomaly Detected",
                String.format("Anomaly detected in clustering analysis %s", event.getClusteringId()),
                "MEDIUM", correlationId);
        }

        metricsService.recordClusterAnomalyDetected(event.getAnomalySeverity());

        log.info("Cluster anomaly detected: clusteringId={}, severity={}",
            event.getClusteringId(), event.getAnomalySeverity());
    }

    private void processValidationRequired(ClusteringResultsEvent event, String correlationId) {
        complianceReviewService.queueValidation(event.getClusteringId(), "CLUSTERING_VALIDATION",
            event.getValidationRequirements(), correlationId);

        kafkaTemplate.send("compliance-review-tasks", Map.of(
            "taskType", "CLUSTERING_VALIDATION",
            "clusteringId", event.getClusteringId(),
            "validationRequirements", event.getValidationRequirements(),
            "priority", "MEDIUM",
            "dueDate", LocalDateTime.now().plusDays(3),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordClusteringValidationQueued();

        log.info("Clustering validation queued: clusteringId={}", event.getClusteringId());
    }

    private void processReviewEscalation(ClusteringResultsEvent event, String correlationId) {
        complianceReviewService.escalateReview(event.getClusteringId(), event.getEscalationReason(),
            "HIGH", correlationId);

        kafkaTemplate.send("compliance-alerts", Map.of(
            "alertType", "CLUSTERING_REVIEW_ESCALATED",
            "clusteringId", event.getClusteringId(),
            "escalationReason", event.getEscalationReason(),
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Clustering Review Escalated",
            String.format("Clustering review escalated for analysis %s: %s",
                event.getClusteringId(), event.getEscalationReason()),
            "HIGH");

        metricsService.recordClusteringReviewEscalated();

        log.warn("Clustering review escalated: clusteringId={}, reason={}",
            event.getClusteringId(), event.getEscalationReason());
    }
}