package com.waqiti.risk.kafka;

import com.waqiti.common.events.LiquidityRiskEvent;
import com.waqiti.risk.domain.LiquidityRisk;
import com.waqiti.risk.repository.LiquidityRiskRepository;
import com.waqiti.risk.service.LiquidityRiskService;
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
public class LiquidityRiskEventsConsumer {

    private final LiquidityRiskRepository liquidityRiskRepository;
    private final LiquidityRiskService liquidityRiskService;
    private final RiskMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("liquidity_risk_events_processed_total")
            .description("Total number of successfully processed liquidity risk events")
            .register(meterRegistry);
        errorCounter = Counter.builder("liquidity_risk_events_errors_total")
            .description("Total number of liquidity risk event processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("liquidity_risk_events_processing_duration")
            .description("Time taken to process liquidity risk events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"liquidity-risk-events"},
        groupId = "liquidity-risk-events-service-group",
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
    @CircuitBreaker(name = "liquidity-risk-events", fallbackMethod = "handleLiquidityRiskEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleLiquidityRiskEvent(
            @Payload LiquidityRiskEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("liquidity-risk-%s-p%d-o%d", event.getEntityId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getEntityId(), event.getRiskType(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.warn("Processing liquidity risk event: entityId={}, riskType={}, severity={}, liquidityRatio={}",
                event.getEntityId(), event.getRiskType(), event.getSeverity(), event.getLiquidityRatio());

            cleanExpiredEntries();

            switch (event.getRiskType()) {
                case LIQUIDITY_COVERAGE_RATIO_BREACH:
                    processLiquidityCoverageRatioBreach(event, correlationId);
                    break;
                case NET_STABLE_FUNDING_RATIO_BREACH:
                    processNetStableFundingRatioBreach(event, correlationId);
                    break;
                case CASH_FLOW_SHORTFALL:
                    processCashFlowShortfall(event, correlationId);
                    break;
                case FUNDING_GAP_ALERT:
                    processFundingGapAlert(event, correlationId);
                    break;
                case LIQUIDITY_STRESS_TEST_FAILURE:
                    processLiquidityStressTestFailure(event, correlationId);
                    break;
                case CONCENTRATION_RISK_FUNDING:
                    processConcentrationRiskFunding(event, correlationId);
                    break;
                case INTRADAY_LIQUIDITY_SHORTAGE:
                    processIntradayLiquidityShortage(event, correlationId);
                    break;
                case CONTINGENT_LIQUIDITY_ACTIVATION:
                    processContingentLiquidityActivation(event, correlationId);
                    break;
                case MARKET_LIQUIDITY_DETERIORATION:
                    processMarketLiquidityDeterioration(event, correlationId);
                    break;
                case REGULATORY_LIQUIDITY_BREACH:
                    processRegulatoryLiquidityBreach(event, correlationId);
                    break;
                default:
                    log.warn("Unknown liquidity risk type: {}", event.getRiskType());
                    processGenericLiquidityRisk(event, correlationId);
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logRiskEvent("LIQUIDITY_RISK_EVENT_PROCESSED", event.getEntityId(),
                Map.of("riskType", event.getRiskType(), "severity", event.getSeverity(),
                    "liquidityRatio", event.getLiquidityRatio(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process liquidity risk event: {}", e.getMessage(), e);

            kafkaTemplate.send("liquidity-risk-events-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleLiquidityRiskEventFallback(
            LiquidityRiskEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("liquidity-risk-fallback-%s-p%d-o%d", event.getEntityId(), partition, offset);

        log.error("Circuit breaker fallback triggered for liquidity risk event: entityId={}, error={}",
            event.getEntityId(), ex.getMessage());

        kafkaTemplate.send("liquidity-risk-events-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        try {
            notificationService.sendCriticalAlert(
                "Liquidity Risk Event Circuit Breaker Triggered",
                String.format("CRITICAL: Liquidity risk event processing failed for entity %s: %s",
                    event.getEntityId(), ex.getMessage()),
                Map.of("entityId", event.getEntityId(), "riskType", event.getRiskType(), "correlationId", correlationId)
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send critical alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltLiquidityRiskEvent(
            @Payload LiquidityRiskEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-liquidity-risk-%s-%d", event.getEntityId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Liquidity risk event permanently failed: entityId={}, topic={}, error={}",
            event.getEntityId(), topic, exceptionMessage);

        auditService.logRiskEvent("LIQUIDITY_RISK_EVENT_DLT", event.getEntityId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "riskType", event.getRiskType(), "correlationId", correlationId,
                "requiresEmergencyIntervention", true, "timestamp", Instant.now()));

        try {
            notificationService.sendEmergencyAlert(
                "Liquidity Risk Event Dead Letter Event",
                String.format("EMERGENCY: Liquidity risk event for entity %s sent to DLT: %s",
                    event.getEntityId(), exceptionMessage),
                Map.of("entityId", event.getEntityId(), "riskType", event.getRiskType(),
                       "topic", topic, "correlationId", correlationId, "severity", "EMERGENCY")
            );
        } catch (Exception ex) {
            log.error("Failed to send emergency DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) return false;
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

    private void processLiquidityCoverageRatioBreach(LiquidityRiskEvent event, String correlationId) {
        LiquidityRisk risk = createLiquidityRisk(event, correlationId);
        risk.setLiquidityCoverageRatio(event.getLiquidityCoverageRatio());
        risk.setRegulatoryThreshold(event.getRegulatoryThreshold());
        risk.setSeverity("CRITICAL");
        liquidityRiskRepository.save(risk);

        liquidityRiskService.processLiquidityCoverageRatioBreach(event.getEntityId(), event.getLiquidityCoverageRatio());

        // Critical regulatory breach
        kafkaTemplate.send("regulatory-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "LCR_BREACH",
            "severity", "CRITICAL",
            "liquidityCoverageRatio", event.getLiquidityCoverageRatio(),
            "regulatoryThreshold", event.getRegulatoryThreshold(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Immediate treasury alert
        kafkaTemplate.send("treasury-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "LIQUIDITY_COVERAGE_RATIO_BREACH",
            "urgency", "IMMEDIATE",
            "lcrRatio", event.getLiquidityCoverageRatio(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendEmergencyAlert(
            "Liquidity Coverage Ratio Breach",
            String.format("EMERGENCY: LCR breach for entity %s - Ratio: %s, Required: %s",
                event.getEntityId(), event.getLiquidityCoverageRatio(), event.getRegulatoryThreshold()),
            Map.of("entityId", event.getEntityId(), "lcrRatio", event.getLiquidityCoverageRatio(),
                   "correlationId", correlationId, "severity", "EMERGENCY")
        );

        metricsService.recordLiquidityRisk("LCR_BREACH", "CRITICAL");
        log.error("LCR breach processed: entityId={}, ratio={}, threshold={}",
            event.getEntityId(), event.getLiquidityCoverageRatio(), event.getRegulatoryThreshold());
    }

    private void processNetStableFundingRatioBreach(LiquidityRiskEvent event, String correlationId) {
        LiquidityRisk risk = createLiquidityRisk(event, correlationId);
        risk.setNetStableFundingRatio(event.getNetStableFundingRatio());
        risk.setRegulatoryThreshold(event.getRegulatoryThreshold());
        risk.setSeverity("CRITICAL");
        liquidityRiskRepository.save(risk);

        liquidityRiskService.processNetStableFundingRatioBreach(event.getEntityId(), event.getNetStableFundingRatio());

        kafkaTemplate.send("regulatory-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "NSFR_BREACH",
            "severity", "CRITICAL",
            "netStableFundingRatio", event.getNetStableFundingRatio(),
            "regulatoryThreshold", event.getRegulatoryThreshold(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendEmergencyAlert(
            "Net Stable Funding Ratio Breach",
            String.format("EMERGENCY: NSFR breach for entity %s - Ratio: %s, Required: %s",
                event.getEntityId(), event.getNetStableFundingRatio(), event.getRegulatoryThreshold()),
            Map.of("entityId", event.getEntityId(), "nsfrRatio", event.getNetStableFundingRatio(),
                   "correlationId", correlationId, "severity", "EMERGENCY")
        );

        metricsService.recordLiquidityRisk("NSFR_BREACH", "CRITICAL");
        log.error("NSFR breach processed: entityId={}, ratio={}", event.getEntityId(), event.getNetStableFundingRatio());
    }

    private void processCashFlowShortfall(LiquidityRiskEvent event, String correlationId) {
        LiquidityRisk risk = createLiquidityRisk(event, correlationId);
        risk.setCashFlowShortfall(event.getCashFlowShortfall());
        risk.setTimeHorizon(event.getTimeHorizon());
        liquidityRiskRepository.save(risk);

        liquidityRiskService.processCashFlowShortfall(event.getEntityId(), event.getCashFlowShortfall(), event.getTimeHorizon());

        kafkaTemplate.send("treasury-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "CASH_FLOW_SHORTFALL",
            "urgency", "HIGH",
            "shortfallAmount", event.getCashFlowShortfall(),
            "timeHorizon", event.getTimeHorizon(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Trigger liquidity contingency plan
        kafkaTemplate.send("liquidity-contingency-activation", Map.of(
            "entityId", event.getEntityId(),
            "activationReason", "CASH_FLOW_SHORTFALL",
            "shortfallAmount", event.getCashFlowShortfall(),
            "timeHorizon", event.getTimeHorizon(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendCriticalAlert(
            "Cash Flow Shortfall Detected",
            String.format("CRITICAL: Cash flow shortfall for entity %s - Amount: %s, Horizon: %s",
                event.getEntityId(), event.getCashFlowShortfall(), event.getTimeHorizon()),
            Map.of("entityId", event.getEntityId(), "shortfall", event.getCashFlowShortfall(),
                   "correlationId", correlationId)
        );

        metricsService.recordLiquidityRisk("CASH_FLOW_SHORTFALL", event.getSeverity());
        log.error("Cash flow shortfall processed: entityId={}, shortfall={}, horizon={}",
            event.getEntityId(), event.getCashFlowShortfall(), event.getTimeHorizon());
    }

    private void processFundingGapAlert(LiquidityRiskEvent event, String correlationId) {
        LiquidityRisk risk = createLiquidityRisk(event, correlationId);
        risk.setFundingGap(event.getFundingGap());
        risk.setMaturityBucket(event.getMaturityBucket());
        liquidityRiskRepository.save(risk);

        liquidityRiskService.processFundingGapAlert(event.getEntityId(), event.getFundingGap(), event.getMaturityBucket());

        kafkaTemplate.send("funding-management-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "FUNDING_GAP",
            "fundingGap", event.getFundingGap(),
            "maturityBucket", event.getMaturityBucket(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordLiquidityRisk("FUNDING_GAP", event.getSeverity());
        log.warn("Funding gap alert processed: entityId={}, gap={}, bucket={}",
            event.getEntityId(), event.getFundingGap(), event.getMaturityBucket());
    }

    private void processLiquidityStressTestFailure(LiquidityRiskEvent event, String correlationId) {
        LiquidityRisk risk = createLiquidityRisk(event, correlationId);
        risk.setStressTestResults(event.getStressTestResults());
        risk.setStressScenario(event.getStressScenario());
        risk.setSeverity("HIGH");
        liquidityRiskRepository.save(risk);

        liquidityRiskService.processLiquidityStressTestFailure(event.getEntityId(), event.getStressTestResults());

        kafkaTemplate.send("stress-test-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "LIQUIDITY_STRESS_TEST_FAILURE",
            "stressTestResults", event.getStressTestResults(),
            "stressScenario", event.getStressScenario(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendHighPriorityNotification("risk-management-team",
            "Liquidity Stress Test Failure",
            String.format("Liquidity stress test failed for entity %s - Scenario: %s",
                event.getEntityId(), event.getStressScenario()),
            correlationId);

        metricsService.recordLiquidityRisk("STRESS_TEST_FAILURE", "HIGH");
        log.error("Liquidity stress test failure processed: entityId={}, scenario={}",
            event.getEntityId(), event.getStressScenario());
    }

    private void processConcentrationRiskFunding(LiquidityRiskEvent event, String correlationId) {
        LiquidityRisk risk = createLiquidityRisk(event, correlationId);
        risk.setConcentrationMetrics(event.getConcentrationMetrics());
        risk.setFundingSource(event.getFundingSource());
        liquidityRiskRepository.save(risk);

        liquidityRiskService.processConcentrationRiskFunding(event.getEntityId(), event.getConcentrationMetrics());

        kafkaTemplate.send("concentration-risk-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "FUNDING_CONCENTRATION",
            "concentrationMetrics", event.getConcentrationMetrics(),
            "fundingSource", event.getFundingSource(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordLiquidityRisk("CONCENTRATION_RISK_FUNDING", event.getSeverity());
        log.warn("Concentration risk funding processed: entityId={}, source={}",
            event.getEntityId(), event.getFundingSource());
    }

    private void processIntradayLiquidityShortage(LiquidityRiskEvent event, String correlationId) {
        LiquidityRisk risk = createLiquidityRisk(event, correlationId);
        risk.setIntradayShortfall(event.getIntradayShortfall());
        risk.setPaymentPriority(event.getPaymentPriority());
        risk.setSeverity("HIGH");
        liquidityRiskRepository.save(risk);

        liquidityRiskService.processIntradayLiquidityShortage(event.getEntityId(), event.getIntradayShortfall());

        // Immediate intraday funding action
        kafkaTemplate.send("intraday-funding-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "INTRADAY_LIQUIDITY_SHORTAGE",
            "urgency", "IMMEDIATE",
            "shortfallAmount", event.getIntradayShortfall(),
            "paymentPriority", event.getPaymentPriority(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendCriticalAlert(
            "Intraday Liquidity Shortage",
            String.format("CRITICAL: Intraday liquidity shortage for entity %s - Amount: %s",
                event.getEntityId(), event.getIntradayShortfall()),
            Map.of("entityId", event.getEntityId(), "shortfall", event.getIntradayShortfall(),
                   "correlationId", correlationId)
        );

        metricsService.recordLiquidityRisk("INTRADAY_LIQUIDITY_SHORTAGE", "HIGH");
        log.error("Intraday liquidity shortage processed: entityId={}, shortfall={}",
            event.getEntityId(), event.getIntradayShortfall());
    }

    private void processContingentLiquidityActivation(LiquidityRiskEvent event, String correlationId) {
        LiquidityRisk risk = createLiquidityRisk(event, correlationId);
        risk.setContingentLiquidityDetails(event.getContingentLiquidityDetails());
        risk.setActivationTrigger(event.getActivationTrigger());
        risk.setSeverity("CRITICAL");
        liquidityRiskRepository.save(risk);

        liquidityRiskService.processContingentLiquidityActivation(event.getEntityId(), event.getContingentLiquidityDetails());

        kafkaTemplate.send("contingent-liquidity-execution", Map.of(
            "entityId", event.getEntityId(),
            "executionType", "ACTIVATE_CONTINGENT_LIQUIDITY",
            "liquidityDetails", event.getContingentLiquidityDetails(),
            "activationTrigger", event.getActivationTrigger(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendEmergencyAlert(
            "Contingent Liquidity Activation",
            String.format("EMERGENCY: Contingent liquidity activated for entity %s - Trigger: %s",
                event.getEntityId(), event.getActivationTrigger()),
            Map.of("entityId", event.getEntityId(), "trigger", event.getActivationTrigger(),
                   "correlationId", correlationId, "severity", "EMERGENCY")
        );

        metricsService.recordLiquidityRisk("CONTINGENT_LIQUIDITY_ACTIVATION", "CRITICAL");
        log.error("Contingent liquidity activation processed: entityId={}, trigger={}",
            event.getEntityId(), event.getActivationTrigger());
    }

    private void processMarketLiquidityDeterioration(LiquidityRiskEvent event, String correlationId) {
        LiquidityRisk risk = createLiquidityRisk(event, correlationId);
        risk.setMarketLiquidityMetrics(event.getMarketLiquidityMetrics());
        risk.setAssetClass(event.getAssetClass());
        liquidityRiskRepository.save(risk);

        liquidityRiskService.processMarketLiquidityDeterioration(event.getEntityId(), event.getMarketLiquidityMetrics());

        kafkaTemplate.send("market-risk-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "MARKET_LIQUIDITY_DETERIORATION",
            "marketLiquidityMetrics", event.getMarketLiquidityMetrics(),
            "assetClass", event.getAssetClass(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordLiquidityRisk("MARKET_LIQUIDITY_DETERIORATION", event.getSeverity());
        log.warn("Market liquidity deterioration processed: entityId={}, assetClass={}",
            event.getEntityId(), event.getAssetClass());
    }

    private void processRegulatoryLiquidityBreach(LiquidityRiskEvent event, String correlationId) {
        LiquidityRisk risk = createLiquidityRisk(event, correlationId);
        risk.setRegulatoryRequirement(event.getRegulatoryRequirement());
        risk.setActualValue(event.getActualValue());
        risk.setSeverity("CRITICAL");
        liquidityRiskRepository.save(risk);

        liquidityRiskService.processRegulatoryLiquidityBreach(event.getEntityId(), event.getRegulatoryRequirement());

        kafkaTemplate.send("regulatory-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "LIQUIDITY_REGULATORY_BREACH",
            "severity", "CRITICAL",
            "regulatoryRequirement", event.getRegulatoryRequirement(),
            "actualValue", event.getActualValue(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendEmergencyAlert(
            "Regulatory Liquidity Breach",
            String.format("EMERGENCY: Regulatory liquidity breach for entity %s - Requirement: %s, Actual: %s",
                event.getEntityId(), event.getRegulatoryRequirement(), event.getActualValue()),
            Map.of("entityId", event.getEntityId(), "requirement", event.getRegulatoryRequirement(),
                   "correlationId", correlationId, "severity", "EMERGENCY")
        );

        metricsService.recordLiquidityRisk("REGULATORY_LIQUIDITY_BREACH", "CRITICAL");
        log.error("Regulatory liquidity breach processed: entityId={}, requirement={}, actual={}",
            event.getEntityId(), event.getRegulatoryRequirement(), event.getActualValue());
    }

    private void processGenericLiquidityRisk(LiquidityRiskEvent event, String correlationId) {
        LiquidityRisk risk = createLiquidityRisk(event, correlationId);
        liquidityRiskRepository.save(risk);

        liquidityRiskService.processGenericLiquidityRisk(event.getEntityId(), event.getRiskType());

        metricsService.recordLiquidityRisk("GENERIC", event.getSeverity());
        log.info("Generic liquidity risk processed: entityId={}, riskType={}",
            event.getEntityId(), event.getRiskType());
    }

    private LiquidityRisk createLiquidityRisk(LiquidityRiskEvent event, String correlationId) {
        return LiquidityRisk.builder()
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .riskType(event.getRiskType())
            .severity(event.getSeverity())
            .liquidityRatio(event.getLiquidityRatio())
            .detectedAt(LocalDateTime.now())
            .status("ACTIVE")
            .correlationId(correlationId)
            .riskFactors(event.getRiskFactors())
            .impact(determineImpact(event.getSeverity()))
            .build();
    }

    private String determineImpact(String severity) {
        switch (severity.toUpperCase()) {
            case "CRITICAL": return "BUSINESS_CRITICAL";
            case "HIGH": return "HIGH_IMPACT";
            case "MEDIUM": return "MEDIUM_IMPACT";
            default: return "LOW_IMPACT";
        }
    }
}