package com.waqiti.risk.kafka;

import com.waqiti.common.events.HighRiskReviewQueueEvent;
import com.waqiti.common.security.SecureRandomService;
import com.waqiti.risk.domain.HighRiskReview;
import com.waqiti.risk.repository.HighRiskReviewRepository;
import com.waqiti.risk.service.HighRiskReviewService;
import com.waqiti.risk.service.RiskAnalysisService;
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
public class HighRiskReviewQueueConsumer {

    private final HighRiskReviewRepository highRiskReviewRepository;
    private final HighRiskReviewService highRiskReviewService;
    private final RiskAnalysisService riskAnalysisService;
    private final RiskMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final SecureRandomService secureRandomService;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("high_risk_review_queue_processed_total")
            .description("Total number of successfully processed high risk review queue events")
            .register(meterRegistry);
        errorCounter = Counter.builder("high_risk_review_queue_errors_total")
            .description("Total number of high risk review queue processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("high_risk_review_queue_processing_duration")
            .description("Time taken to process high risk review queue events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"high-risk-review-queue"},
        groupId = "high-risk-review-queue-service-group",
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
    @CircuitBreaker(name = "high-risk-review-queue", fallbackMethod = "handleHighRiskReviewQueueEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleHighRiskReviewQueueEvent(
            @Payload HighRiskReviewQueueEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("high-risk-review-%s-p%d-o%d", event.getEntityId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getEntityId(), event.getReviewType(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.warn("Processing high risk review queue: entityId={}, reviewType={}, priority={}, riskScore={}",
                event.getEntityId(), event.getReviewType(), event.getPriority(), event.getRiskScore());

            cleanExpiredEntries();

            switch (event.getReviewType()) {
                case SUSPICIOUS_ACTIVITY_REVIEW:
                    processSuspiciousActivityReview(event, correlationId);
                    break;
                case FRAUD_INVESTIGATION:
                    processFraudInvestigation(event, correlationId);
                    break;
                case AML_COMPLIANCE_REVIEW:
                    processAmlComplianceReview(event, correlationId);
                    break;
                case REGULATORY_BREACH_REVIEW:
                    processRegulatoryBreachReview(event, correlationId);
                    break;
                case CREDIT_RISK_ESCALATION:
                    processCreditRiskEscalation(event, correlationId);
                    break;
                case OPERATIONAL_RISK_REVIEW:
                    processOperationalRiskReview(event, correlationId);
                    break;
                case CONCENTRATION_RISK_REVIEW:
                    processConcentrationRiskReview(event, correlationId);
                    break;
                case LIQUIDITY_RISK_REVIEW:
                    processLiquidityRiskReview(event, correlationId);
                    break;
                case CYBER_SECURITY_INCIDENT:
                    processCyberSecurityIncident(event, correlationId);
                    break;
                case MODEL_RISK_REVIEW:
                    processModelRiskReview(event, correlationId);
                    break;
                default:
                    log.warn("Unknown high risk review type: {}", event.getReviewType());
                    processGenericHighRiskReview(event, correlationId);
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logRiskEvent("HIGH_RISK_REVIEW_QUEUED", event.getEntityId(),
                Map.of("reviewType", event.getReviewType(), "priority", event.getPriority(),
                    "riskScore", event.getRiskScore(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process high risk review queue event: {}", e.getMessage(), e);

            kafkaTemplate.send("high-risk-review-queue-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleHighRiskReviewQueueEventFallback(
            HighRiskReviewQueueEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("high-risk-review-fallback-%s-p%d-o%d", event.getEntityId(), partition, offset);

        log.error("Circuit breaker fallback triggered for high risk review queue: entityId={}, error={}",
            event.getEntityId(), ex.getMessage());

        kafkaTemplate.send("high-risk-review-queue-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        try {
            notificationService.sendCriticalAlert(
                "High Risk Review Queue Circuit Breaker Triggered",
                String.format("CRITICAL: High risk review for entity %s failed: %s", event.getEntityId(), ex.getMessage()),
                Map.of("entityId", event.getEntityId(), "reviewType", event.getReviewType(), "correlationId", correlationId)
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send critical alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltHighRiskReviewQueueEvent(
            @Payload HighRiskReviewQueueEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-high-risk-review-%s-%d", event.getEntityId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - High risk review queue permanently failed: entityId={}, topic={}, error={}",
            event.getEntityId(), topic, exceptionMessage);

        auditService.logRiskEvent("HIGH_RISK_REVIEW_DLT_EVENT", event.getEntityId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "reviewType", event.getReviewType(), "correlationId", correlationId,
                "requiresEmergencyIntervention", true, "timestamp", Instant.now()));

        try {
            notificationService.sendEmergencyAlert(
                "High Risk Review Queue Dead Letter Event",
                String.format("EMERGENCY: High risk review for entity %s sent to DLT: %s", event.getEntityId(), exceptionMessage),
                Map.of("entityId", event.getEntityId(), "reviewType", event.getReviewType(),
                       "topic", topic, "correlationId", correlationId, "severity", "EMERGENCY")
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

    private void processSuspiciousActivityReview(HighRiskReviewQueueEvent event, String correlationId) {
        HighRiskReview review = createHighRiskReview(event, correlationId);
        review.setSuspiciousActivities(event.getSuspiciousActivities());
        highRiskReviewRepository.save(review);

        String reviewer = assignUrgentReviewer();
        highRiskReviewService.initiateSuspiciousActivityReview(event.getEntityId(), event.getSuspiciousActivities());

        notificationService.sendUrgentNotification(reviewer, "Suspicious Activity Review Required",
            String.format("URGENT: Suspicious activity review for entity %s", event.getEntityId()),
            correlationId);

        metricsService.recordHighRiskReview("SUSPICIOUS_ACTIVITY", "URGENT");
        log.warn("Suspicious activity review queued: entityId={}, assignedTo={}", event.getEntityId(), reviewer);
    }

    private void processFraudInvestigation(HighRiskReviewQueueEvent event, String correlationId) {
        HighRiskReview review = createHighRiskReview(event, correlationId);
        review.setFraudIndicators(event.getFraudIndicators());
        review.setPriority("CRITICAL");
        highRiskReviewRepository.save(review);

        String investigator = assignFraudInvestigator();
        highRiskReviewService.initiateFraudInvestigation(event.getEntityId(), event.getFraudIndicators());

        kafkaTemplate.send("fraud-investigation-queue", Map.of(
            "entityId", event.getEntityId(),
            "investigationType", "HIGH_RISK_FRAUD",
            "fraudIndicators", event.getFraudIndicators(),
            "assignedTo", investigator,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendCriticalAlert(
            "Fraud Investigation Required",
            String.format("CRITICAL: Fraud investigation required for entity %s", event.getEntityId()),
            Map.of("entityId", event.getEntityId(), "assignedTo", investigator, "correlationId", correlationId)
        );

        metricsService.recordHighRiskReview("FRAUD_INVESTIGATION", "CRITICAL");
        log.error("Fraud investigation queued: entityId={}, assignedTo={}", event.getEntityId(), investigator);
    }

    private void processAmlComplianceReview(HighRiskReviewQueueEvent event, String correlationId) {
        HighRiskReview review = createHighRiskReview(event, correlationId);
        review.setAmlViolations(event.getAmlViolations());
        review.setPriority("CRITICAL");
        highRiskReviewRepository.save(review);

        String complianceOfficer = assignComplianceOfficer();
        highRiskReviewService.initiateAmlComplianceReview(event.getEntityId(), event.getAmlViolations());

        kafkaTemplate.send("compliance-investigation-queue", Map.of(
            "entityId", event.getEntityId(),
            "investigationType", "AML_VIOLATION",
            "violations", event.getAmlViolations(),
            "assignedTo", complianceOfficer,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendCriticalAlert(
            "AML Compliance Review Required",
            String.format("CRITICAL: AML compliance review required for entity %s", event.getEntityId()),
            Map.of("entityId", event.getEntityId(), "assignedTo", complianceOfficer, "correlationId", correlationId)
        );

        metricsService.recordHighRiskReview("AML_COMPLIANCE", "CRITICAL");
        log.error("AML compliance review queued: entityId={}, assignedTo={}", event.getEntityId(), complianceOfficer);
    }

    private void processRegulatoryBreachReview(HighRiskReviewQueueEvent event, String correlationId) {
        HighRiskReview review = createHighRiskReview(event, correlationId);
        review.setRegulatoryBreaches(event.getRegulatoryBreaches());
        review.setPriority("CRITICAL");
        highRiskReviewRepository.save(review);

        String legalOfficer = assignLegalOfficer();
        highRiskReviewService.initiateRegulatoryBreachReview(event.getEntityId(), event.getRegulatoryBreaches());

        kafkaTemplate.send("legal-review-queue", Map.of(
            "entityId", event.getEntityId(),
            "reviewType", "REGULATORY_BREACH",
            "breaches", event.getRegulatoryBreaches(),
            "assignedTo", legalOfficer,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendEmergencyAlert(
            "Regulatory Breach Review Required",
            String.format("EMERGENCY: Regulatory breach review required for entity %s", event.getEntityId()),
            Map.of("entityId", event.getEntityId(), "assignedTo", legalOfficer, "correlationId", correlationId, "severity", "EMERGENCY")
        );

        metricsService.recordHighRiskReview("REGULATORY_BREACH", "CRITICAL");
        log.error("Regulatory breach review queued: entityId={}, assignedTo={}", event.getEntityId(), legalOfficer);
    }

    private void processCreditRiskEscalation(HighRiskReviewQueueEvent event, String correlationId) {
        HighRiskReview review = createHighRiskReview(event, correlationId);
        review.setCreditMetrics(event.getCreditMetrics());
        highRiskReviewRepository.save(review);

        String creditAnalyst = assignCreditRiskAnalyst();
        highRiskReviewService.initiateCreditRiskEscalation(event.getEntityId(), event.getCreditMetrics());

        notificationService.sendHighPriorityNotification(creditAnalyst, "Credit Risk Escalation",
            String.format("High priority: Credit risk escalation for entity %s", event.getEntityId()),
            correlationId);

        metricsService.recordHighRiskReview("CREDIT_RISK_ESCALATION", event.getPriority());
        log.warn("Credit risk escalation queued: entityId={}, assignedTo={}", event.getEntityId(), creditAnalyst);
    }

    private void processOperationalRiskReview(HighRiskReviewQueueEvent event, String correlationId) {
        HighRiskReview review = createHighRiskReview(event, correlationId);
        review.setOperationalRisks(event.getOperationalRisks());
        highRiskReviewRepository.save(review);

        String opsRiskAnalyst = assignOperationalRiskAnalyst();
        highRiskReviewService.initiateOperationalRiskReview(event.getEntityId(), event.getOperationalRisks());

        notificationService.sendHighPriorityNotification(opsRiskAnalyst, "Operational Risk Review",
            String.format("High priority: Operational risk review for entity %s", event.getEntityId()),
            correlationId);

        metricsService.recordHighRiskReview("OPERATIONAL_RISK", event.getPriority());
        log.warn("Operational risk review queued: entityId={}, assignedTo={}", event.getEntityId(), opsRiskAnalyst);
    }

    private void processConcentrationRiskReview(HighRiskReviewQueueEvent event, String correlationId) {
        HighRiskReview review = createHighRiskReview(event, correlationId);
        review.setConcentrationMetrics(event.getConcentrationMetrics());
        highRiskReviewRepository.save(review);

        String riskManager = assignRiskManager();
        highRiskReviewService.initiateConcentrationRiskReview(event.getEntityId(), event.getConcentrationMetrics());

        notificationService.sendHighPriorityNotification(riskManager, "Concentration Risk Review",
            String.format("High priority: Concentration risk review for entity %s", event.getEntityId()),
            correlationId);

        metricsService.recordHighRiskReview("CONCENTRATION_RISK", event.getPriority());
        log.warn("Concentration risk review queued: entityId={}, assignedTo={}", event.getEntityId(), riskManager);
    }

    private void processLiquidityRiskReview(HighRiskReviewQueueEvent event, String correlationId) {
        HighRiskReview review = createHighRiskReview(event, correlationId);
        review.setLiquidityMetrics(event.getLiquidityMetrics());
        highRiskReviewRepository.save(review);

        String treasuryAnalyst = assignTreasuryAnalyst();
        highRiskReviewService.initiateLiquidityRiskReview(event.getEntityId(), event.getLiquidityMetrics());

        notificationService.sendHighPriorityNotification(treasuryAnalyst, "Liquidity Risk Review",
            String.format("High priority: Liquidity risk review for entity %s", event.getEntityId()),
            correlationId);

        metricsService.recordHighRiskReview("LIQUIDITY_RISK", event.getPriority());
        log.warn("Liquidity risk review queued: entityId={}, assignedTo={}", event.getEntityId(), treasuryAnalyst);
    }

    private void processCyberSecurityIncident(HighRiskReviewQueueEvent event, String correlationId) {
        HighRiskReview review = createHighRiskReview(event, correlationId);
        review.setSecurityThreats(event.getSecurityThreats());
        review.setPriority("CRITICAL");
        highRiskReviewRepository.save(review);

        String securityAnalyst = assignSecurityAnalyst();
        highRiskReviewService.initiateCyberSecurityIncident(event.getEntityId(), event.getSecurityThreats());

        kafkaTemplate.send("security-incident-response", Map.of(
            "entityId", event.getEntityId(),
            "incidentType", "HIGH_RISK_CYBER_THREAT",
            "threats", event.getSecurityThreats(),
            "assignedTo", securityAnalyst,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendEmergencyAlert(
            "Cyber Security Incident",
            String.format("EMERGENCY: Cyber security incident for entity %s", event.getEntityId()),
            Map.of("entityId", event.getEntityId(), "assignedTo", securityAnalyst, "correlationId", correlationId, "severity", "EMERGENCY")
        );

        metricsService.recordHighRiskReview("CYBER_SECURITY_INCIDENT", "CRITICAL");
        log.error("Cyber security incident queued: entityId={}, assignedTo={}", event.getEntityId(), securityAnalyst);
    }

    private void processModelRiskReview(HighRiskReviewQueueEvent event, String correlationId) {
        HighRiskReview review = createHighRiskReview(event, correlationId);
        review.setModelMetrics(event.getModelMetrics());
        highRiskReviewRepository.save(review);

        String modelRiskAnalyst = assignModelRiskAnalyst();
        highRiskReviewService.initiateModelRiskReview(event.getEntityId(), event.getModelMetrics());

        notificationService.sendHighPriorityNotification(modelRiskAnalyst, "Model Risk Review",
            String.format("High priority: Model risk review for entity %s", event.getEntityId()),
            correlationId);

        metricsService.recordHighRiskReview("MODEL_RISK", event.getPriority());
        log.warn("Model risk review queued: entityId={}, assignedTo={}", event.getEntityId(), modelRiskAnalyst);
    }

    private void processGenericHighRiskReview(HighRiskReviewQueueEvent event, String correlationId) {
        HighRiskReview review = createHighRiskReview(event, correlationId);
        highRiskReviewRepository.save(review);

        String reviewer = assignGenericReviewer(event.getPriority());
        highRiskReviewService.initiateGenericHighRiskReview(event.getEntityId(), event.getReviewType());

        notificationService.sendHighPriorityNotification(reviewer, "High Risk Review Required",
            String.format("High risk review required for entity %s: %s", event.getEntityId(), event.getReviewType()),
            correlationId);

        metricsService.recordHighRiskReview("GENERIC", event.getPriority());
        log.warn("Generic high risk review queued: entityId={}, reviewType={}, assignedTo={}",
            event.getEntityId(), event.getReviewType(), reviewer);
    }

    private HighRiskReview createHighRiskReview(HighRiskReviewQueueEvent event, String correlationId) {
        return HighRiskReview.builder()
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .reviewType(event.getReviewType())
            .priority(event.getPriority())
            .riskScore(event.getRiskScore())
            .status("PENDING_ASSIGNMENT")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .riskFactors(event.getRiskFactors())
            .build();
    }

    // Assignment methods
    // SECURITY FIX: Using SecureRandomService instead of Math.random()
    private String assignUrgentReviewer() { return "urgent-risk-analyst-" + secureRandomService.nextInt(1, 4); }
    private String assignFraudInvestigator() { return "fraud-investigator-" + secureRandomService.nextInt(1, 3); }
    private String assignComplianceOfficer() { return "compliance-officer-" + secureRandomService.nextInt(1, 4); }
    private String assignLegalOfficer() { return "legal-officer-" + secureRandomService.nextInt(1, 3); }
    private String assignCreditRiskAnalyst() { return "credit-risk-analyst-" + secureRandomService.nextInt(1, 5); }
    private String assignOperationalRiskAnalyst() { return "ops-risk-analyst-" + secureRandomService.nextInt(1, 4); }
    private String assignRiskManager() { return "risk-manager-" + secureRandomService.nextInt(1, 3); }
    private String assignTreasuryAnalyst() { return "treasury-analyst-" + secureRandomService.nextInt(1, 4); }
    private String assignSecurityAnalyst() { return "security-analyst-" + secureRandomService.nextInt(1, 5); }
    private String assignModelRiskAnalyst() { return "model-risk-analyst-" + secureRandomService.nextInt(1, 3); }
    private String assignGenericReviewer(String priority) {
        return "HIGH".equals(priority) || "CRITICAL".equals(priority) ?
            "senior-risk-analyst-" + secureRandomService.nextInt(1, 4) :
            "risk-analyst-" + secureRandomService.nextInt(1, 6);
    }
}