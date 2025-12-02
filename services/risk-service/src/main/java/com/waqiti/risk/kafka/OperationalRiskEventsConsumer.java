package com.waqiti.risk.kafka;

import com.waqiti.common.events.OperationalRiskEvent;
import com.waqiti.risk.domain.OperationalRisk;
import com.waqiti.risk.repository.OperationalRiskRepository;
import com.waqiti.risk.service.OperationalRiskService;
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
public class OperationalRiskEventsConsumer {

    private final OperationalRiskRepository operationalRiskRepository;
    private final OperationalRiskService operationalRiskService;
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
        successCounter = Counter.builder("operational_risk_events_processed_total")
            .description("Total number of successfully processed operational risk events")
            .register(meterRegistry);
        errorCounter = Counter.builder("operational_risk_events_errors_total")
            .description("Total number of operational risk event processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("operational_risk_events_processing_duration")
            .description("Time taken to process operational risk events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"operational-risk-events"},
        groupId = "operational-risk-events-service-group",
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
    @CircuitBreaker(name = "operational-risk-events", fallbackMethod = "handleOperationalRiskEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleOperationalRiskEvent(
            @Payload OperationalRiskEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("ops-risk-%s-p%d-o%d", event.getEntityId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getEntityId(), event.getRiskType(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.warn("Processing operational risk event: entityId={}, riskType={}, severity={}, lossAmount={}",
                event.getEntityId(), event.getRiskType(), event.getSeverity(), event.getLossAmount());

            cleanExpiredEntries();

            switch (event.getRiskType()) {
                case PROCESS_FAILURE:
                    processProcessFailure(event, correlationId);
                    break;
                case PEOPLE_RISK:
                    processPeopleRisk(event, correlationId);
                    break;
                case TECHNOLOGY_FAILURE:
                    processTechnologyFailure(event, correlationId);
                    break;
                case EXTERNAL_FRAUD:
                    processExternalFraud(event, correlationId);
                    break;
                case INTERNAL_FRAUD:
                    processInternalFraud(event, correlationId);
                    break;
                case BUSINESS_DISRUPTION:
                    processBusinessDisruption(event, correlationId);
                    break;
                case EXECUTION_DELIVERY_PROCESS_MANAGEMENT:
                    processExecutionDeliveryProcessManagement(event, correlationId);
                    break;
                case CLIENTS_PRODUCTS_BUSINESS_PRACTICES:
                    processClientsProductsBusinessPractices(event, correlationId);
                    break;
                case DAMAGE_PHYSICAL_ASSETS:
                    processDamagePhysicalAssets(event, correlationId);
                    break;
                case EMPLOYMENT_PRACTICES_WORKPLACE_SAFETY:
                    processEmploymentPracticesWorkplaceSafety(event, correlationId);
                    break;
                case MODEL_RISK:
                    processModelRisk(event, correlationId);
                    break;
                case VENDOR_THIRD_PARTY_RISK:
                    processVendorThirdPartyRisk(event, correlationId);
                    break;
                case REGULATORY_COMPLIANCE_RISK:
                    processRegulatoryComplianceRisk(event, correlationId);
                    break;
                case REPUTATIONAL_RISK:
                    processReputationalRisk(event, correlationId);
                    break;
                case STRATEGIC_RISK:
                    processStrategicRisk(event, correlationId);
                    break;
                default:
                    log.warn("Unknown operational risk type: {}", event.getRiskType());
                    processGenericOperationalRisk(event, correlationId);
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logRiskEvent("OPERATIONAL_RISK_EVENT_PROCESSED", event.getEntityId(),
                Map.of("riskType", event.getRiskType(), "severity", event.getSeverity(),
                    "lossAmount", event.getLossAmount(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process operational risk event: {}", e.getMessage(), e);

            kafkaTemplate.send("operational-risk-events-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleOperationalRiskEventFallback(
            OperationalRiskEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("ops-risk-fallback-%s-p%d-o%d", event.getEntityId(), partition, offset);

        log.error("Circuit breaker fallback triggered for operational risk event: entityId={}, error={}",
            event.getEntityId(), ex.getMessage());

        kafkaTemplate.send("operational-risk-events-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        try {
            notificationService.sendCriticalAlert(
                "Operational Risk Event Circuit Breaker Triggered",
                String.format("CRITICAL: Operational risk event processing failed for entity %s: %s",
                    event.getEntityId(), ex.getMessage()),
                Map.of("entityId", event.getEntityId(), "riskType", event.getRiskType(), "correlationId", correlationId)
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send critical alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltOperationalRiskEvent(
            @Payload OperationalRiskEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-ops-risk-%s-%d", event.getEntityId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Operational risk event permanently failed: entityId={}, topic={}, error={}",
            event.getEntityId(), topic, exceptionMessage);

        auditService.logRiskEvent("OPERATIONAL_RISK_EVENT_DLT", event.getEntityId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "riskType", event.getRiskType(), "correlationId", correlationId,
                "requiresEmergencyIntervention", true, "timestamp", Instant.now()));

        try {
            notificationService.sendEmergencyAlert(
                "Operational Risk Event Dead Letter Event",
                String.format("EMERGENCY: Operational risk event for entity %s sent to DLT: %s",
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

    private void processProcessFailure(OperationalRiskEvent event, String correlationId) {
        OperationalRisk risk = createOperationalRisk(event, correlationId);
        risk.setProcessDetails(event.getProcessDetails());
        risk.setFailureRoot(event.getFailureRoot());
        operationalRiskRepository.save(risk);

        operationalRiskService.processProcessFailure(event.getEntityId(), event.getProcessDetails());

        kafkaTemplate.send("process-improvement-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "PROCESS_FAILURE",
            "processDetails", event.getProcessDetails(),
            "failureRoot", event.getFailureRoot(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordOperationalRisk("PROCESS_FAILURE", event.getSeverity());
        log.warn("Process failure processed: entityId={}, process={}", event.getEntityId(), event.getProcessDetails());
    }

    private void processPeopleRisk(OperationalRiskEvent event, String correlationId) {
        OperationalRisk risk = createOperationalRisk(event, correlationId);
        risk.setPeopleRiskDetails(event.getPeopleRiskDetails());
        risk.setStaffingIssues(event.getStaffingIssues());
        operationalRiskRepository.save(risk);

        operationalRiskService.processPeopleRisk(event.getEntityId(), event.getPeopleRiskDetails());

        kafkaTemplate.send("human-resources-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "PEOPLE_RISK",
            "peopleRiskDetails", event.getPeopleRiskDetails(),
            "staffingIssues", event.getStaffingIssues(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordOperationalRisk("PEOPLE_RISK", event.getSeverity());
        log.warn("People risk processed: entityId={}, details={}", event.getEntityId(), event.getPeopleRiskDetails());
    }

    private void processTechnologyFailure(OperationalRiskEvent event, String correlationId) {
        OperationalRisk risk = createOperationalRisk(event, correlationId);
        risk.setTechnologyDetails(event.getTechnologyDetails());
        risk.setSystemsAffected(event.getSystemsAffected());
        operationalRiskRepository.save(risk);

        operationalRiskService.processTechnologyFailure(event.getEntityId(), event.getTechnologyDetails());

        kafkaTemplate.send("technology-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "TECHNOLOGY_FAILURE",
            "technologyDetails", event.getTechnologyDetails(),
            "systemsAffected", event.getSystemsAffected(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if ("CRITICAL".equals(event.getSeverity())) {
            kafkaTemplate.send("incident-response", Map.of(
                "incidentType", "TECHNOLOGY_FAILURE",
                "entityId", event.getEntityId(),
                "severity", "CRITICAL",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordOperationalRisk("TECHNOLOGY_FAILURE", event.getSeverity());
        log.error("Technology failure processed: entityId={}, systems={}", event.getEntityId(), event.getSystemsAffected());
    }

    private void processExternalFraud(OperationalRiskEvent event, String correlationId) {
        OperationalRisk risk = createOperationalRisk(event, correlationId);
        risk.setFraudDetails(event.getFraudDetails());
        risk.setFraudType(event.getFraudType());
        risk.setSeverity("CRITICAL");
        operationalRiskRepository.save(risk);

        operationalRiskService.processExternalFraud(event.getEntityId(), event.getFraudDetails());

        kafkaTemplate.send("fraud-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "EXTERNAL_FRAUD",
            "severity", "CRITICAL",
            "fraudDetails", event.getFraudDetails(),
            "fraudType", event.getFraudType(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        kafkaTemplate.send("law-enforcement-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "EXTERNAL_FRAUD_REPORT",
            "fraudDetails", event.getFraudDetails(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendCriticalAlert(
            "External Fraud Detected",
            String.format("CRITICAL: External fraud detected for entity %s - Type: %s",
                event.getEntityId(), event.getFraudType()),
            Map.of("entityId", event.getEntityId(), "fraudType", event.getFraudType(), "correlationId", correlationId)
        );

        metricsService.recordOperationalRisk("EXTERNAL_FRAUD", "CRITICAL");
        log.error("External fraud processed: entityId={}, type={}", event.getEntityId(), event.getFraudType());
    }

    private void processInternalFraud(OperationalRiskEvent event, String correlationId) {
        OperationalRisk risk = createOperationalRisk(event, correlationId);
        risk.setFraudDetails(event.getFraudDetails());
        risk.setFraudType(event.getFraudType());
        risk.setInternalParties(event.getInternalParties());
        risk.setSeverity("CRITICAL");
        operationalRiskRepository.save(risk);

        operationalRiskService.processInternalFraud(event.getEntityId(), event.getFraudDetails());

        kafkaTemplate.send("internal-investigation-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "INTERNAL_FRAUD",
            "severity", "CRITICAL",
            "fraudDetails", event.getFraudDetails(),
            "internalParties", event.getInternalParties(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        kafkaTemplate.send("legal-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "INTERNAL_FRAUD_LEGAL",
            "fraudDetails", event.getFraudDetails(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendEmergencyAlert(
            "Internal Fraud Detected",
            String.format("EMERGENCY: Internal fraud detected for entity %s - Parties: %s",
                event.getEntityId(), event.getInternalParties()),
            Map.of("entityId", event.getEntityId(), "internalParties", event.getInternalParties(),
                   "correlationId", correlationId, "severity", "EMERGENCY")
        );

        metricsService.recordOperationalRisk("INTERNAL_FRAUD", "CRITICAL");
        log.error("Internal fraud processed: entityId={}, parties={}", event.getEntityId(), event.getInternalParties());
    }

    private void processBusinessDisruption(OperationalRiskEvent event, String correlationId) {
        OperationalRisk risk = createOperationalRisk(event, correlationId);
        risk.setDisruptionDetails(event.getDisruptionDetails());
        risk.setBusinessImpact(event.getBusinessImpact());
        operationalRiskRepository.save(risk);

        operationalRiskService.processBusinessDisruption(event.getEntityId(), event.getDisruptionDetails());

        kafkaTemplate.send("business-continuity-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "BUSINESS_DISRUPTION",
            "disruptionDetails", event.getDisruptionDetails(),
            "businessImpact", event.getBusinessImpact(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordOperationalRisk("BUSINESS_DISRUPTION", event.getSeverity());
        log.error("Business disruption processed: entityId={}, impact={}", event.getEntityId(), event.getBusinessImpact());
    }

    private void processExecutionDeliveryProcessManagement(OperationalRiskEvent event, String correlationId) {
        OperationalRisk risk = createOperationalRisk(event, correlationId);
        risk.setExecutionDetails(event.getExecutionDetails());
        risk.setDeliveryIssues(event.getDeliveryIssues());
        operationalRiskRepository.save(risk);

        operationalRiskService.processExecutionDeliveryProcessManagement(event.getEntityId(), event.getExecutionDetails());

        kafkaTemplate.send("operations-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "EXECUTION_DELIVERY_PROCESS_MANAGEMENT",
            "executionDetails", event.getExecutionDetails(),
            "deliveryIssues", event.getDeliveryIssues(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordOperationalRisk("EXECUTION_DELIVERY_PROCESS_MANAGEMENT", event.getSeverity());
        log.warn("Execution delivery process management risk processed: entityId={}", event.getEntityId());
    }

    private void processClientsProductsBusinessPractices(OperationalRiskEvent event, String correlationId) {
        OperationalRisk risk = createOperationalRisk(event, correlationId);
        risk.setClientIssues(event.getClientIssues());
        risk.setProductIssues(event.getProductIssues());
        operationalRiskRepository.save(risk);

        operationalRiskService.processClientsProductsBusinessPractices(event.getEntityId(), event.getClientIssues());

        kafkaTemplate.send("client-relations-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "CLIENT_PRODUCT_BUSINESS_PRACTICES",
            "clientIssues", event.getClientIssues(),
            "productIssues", event.getProductIssues(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordOperationalRisk("CLIENTS_PRODUCTS_BUSINESS_PRACTICES", event.getSeverity());
        log.warn("Client products business practices risk processed: entityId={}", event.getEntityId());
    }

    private void processDamagePhysicalAssets(OperationalRiskEvent event, String correlationId) {
        OperationalRisk risk = createOperationalRisk(event, correlationId);
        risk.setAssetDamage(event.getAssetDamage());
        risk.setDamageDetails(event.getDamageDetails());
        operationalRiskRepository.save(risk);

        operationalRiskService.processDamagePhysicalAssets(event.getEntityId(), event.getAssetDamage());

        kafkaTemplate.send("facilities-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "PHYSICAL_ASSET_DAMAGE",
            "assetDamage", event.getAssetDamage(),
            "damageDetails", event.getDamageDetails(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordOperationalRisk("DAMAGE_PHYSICAL_ASSETS", event.getSeverity());
        log.warn("Physical asset damage processed: entityId={}, damage={}", event.getEntityId(), event.getAssetDamage());
    }

    private void processEmploymentPracticesWorkplaceSafety(OperationalRiskEvent event, String correlationId) {
        OperationalRisk risk = createOperationalRisk(event, correlationId);
        risk.setEmploymentIssues(event.getEmploymentIssues());
        risk.setSafetyIncidents(event.getSafetyIncidents());
        operationalRiskRepository.save(risk);

        operationalRiskService.processEmploymentPracticesWorkplaceSafety(event.getEntityId(), event.getEmploymentIssues());

        kafkaTemplate.send("human-resources-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "EMPLOYMENT_PRACTICES_WORKPLACE_SAFETY",
            "employmentIssues", event.getEmploymentIssues(),
            "safetyIncidents", event.getSafetyIncidents(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordOperationalRisk("EMPLOYMENT_PRACTICES_WORKPLACE_SAFETY", event.getSeverity());
        log.warn("Employment practices workplace safety risk processed: entityId={}", event.getEntityId());
    }

    private void processModelRisk(OperationalRiskEvent event, String correlationId) {
        OperationalRisk risk = createOperationalRisk(event, correlationId);
        risk.setModelDetails(event.getModelDetails());
        risk.setModelPerformanceIssues(event.getModelPerformanceIssues());
        operationalRiskRepository.save(risk);

        operationalRiskService.processModelRisk(event.getEntityId(), event.getModelDetails());

        kafkaTemplate.send("model-risk-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "MODEL_RISK",
            "modelDetails", event.getModelDetails(),
            "performanceIssues", event.getModelPerformanceIssues(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordOperationalRisk("MODEL_RISK", event.getSeverity());
        log.warn("Model risk processed: entityId={}, model={}", event.getEntityId(), event.getModelDetails());
    }

    private void processVendorThirdPartyRisk(OperationalRiskEvent event, String correlationId) {
        OperationalRisk risk = createOperationalRisk(event, correlationId);
        risk.setVendorDetails(event.getVendorDetails());
        risk.setThirdPartyIssues(event.getThirdPartyIssues());
        operationalRiskRepository.save(risk);

        operationalRiskService.processVendorThirdPartyRisk(event.getEntityId(), event.getVendorDetails());

        kafkaTemplate.send("vendor-management-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "VENDOR_THIRD_PARTY_RISK",
            "vendorDetails", event.getVendorDetails(),
            "thirdPartyIssues", event.getThirdPartyIssues(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordOperationalRisk("VENDOR_THIRD_PARTY_RISK", event.getSeverity());
        log.warn("Vendor third party risk processed: entityId={}, vendor={}", event.getEntityId(), event.getVendorDetails());
    }

    private void processRegulatoryComplianceRisk(OperationalRiskEvent event, String correlationId) {
        OperationalRisk risk = createOperationalRisk(event, correlationId);
        risk.setComplianceDetails(event.getComplianceDetails());
        risk.setRegulatoryIssues(event.getRegulatoryIssues());
        risk.setSeverity("HIGH");
        operationalRiskRepository.save(risk);

        operationalRiskService.processRegulatoryComplianceRisk(event.getEntityId(), event.getComplianceDetails());

        kafkaTemplate.send("compliance-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "OPERATIONAL_REGULATORY_COMPLIANCE_RISK",
            "complianceDetails", event.getComplianceDetails(),
            "regulatoryIssues", event.getRegulatoryIssues(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordOperationalRisk("REGULATORY_COMPLIANCE_RISK", "HIGH");
        log.error("Regulatory compliance risk processed: entityId={}, issues={}", event.getEntityId(), event.getRegulatoryIssues());
    }

    private void processReputationalRisk(OperationalRiskEvent event, String correlationId) {
        OperationalRisk risk = createOperationalRisk(event, correlationId);
        risk.setReputationalDetails(event.getReputationalDetails());
        risk.setMediaCoverage(event.getMediaCoverage());
        operationalRiskRepository.save(risk);

        operationalRiskService.processReputationalRisk(event.getEntityId(), event.getReputationalDetails());

        kafkaTemplate.send("public-relations-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "REPUTATIONAL_RISK",
            "reputationalDetails", event.getReputationalDetails(),
            "mediaCoverage", event.getMediaCoverage(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordOperationalRisk("REPUTATIONAL_RISK", event.getSeverity());
        log.warn("Reputational risk processed: entityId={}, media={}", event.getEntityId(), event.getMediaCoverage());
    }

    private void processStrategicRisk(OperationalRiskEvent event, String correlationId) {
        OperationalRisk risk = createOperationalRisk(event, correlationId);
        risk.setStrategicDetails(event.getStrategicDetails());
        risk.setBusinessStrategy(event.getBusinessStrategy());
        operationalRiskRepository.save(risk);

        operationalRiskService.processStrategicRisk(event.getEntityId(), event.getStrategicDetails());

        kafkaTemplate.send("strategic-management-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "STRATEGIC_RISK",
            "strategicDetails", event.getStrategicDetails(),
            "businessStrategy", event.getBusinessStrategy(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordOperationalRisk("STRATEGIC_RISK", event.getSeverity());
        log.warn("Strategic risk processed: entityId={}, strategy={}", event.getEntityId(), event.getBusinessStrategy());
    }

    private void processGenericOperationalRisk(OperationalRiskEvent event, String correlationId) {
        OperationalRisk risk = createOperationalRisk(event, correlationId);
        operationalRiskRepository.save(risk);

        operationalRiskService.processGenericOperationalRisk(event.getEntityId(), event.getRiskType());

        metricsService.recordOperationalRisk("GENERIC", event.getSeverity());
        log.info("Generic operational risk processed: entityId={}, riskType={}",
            event.getEntityId(), event.getRiskType());
    }

    private OperationalRisk createOperationalRisk(OperationalRiskEvent event, String correlationId) {
        return OperationalRisk.builder()
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .riskType(event.getRiskType())
            .severity(event.getSeverity())
            .lossAmount(event.getLossAmount())
            .detectedAt(LocalDateTime.now())
            .status("ACTIVE")
            .correlationId(correlationId)
            .description(event.getDescription())
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