package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.compliance.model.*;
import com.waqiti.compliance.service.*;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.service.DlqService;
import com.waqiti.common.kafka.KafkaHealthIndicator;
import com.waqiti.common.utils.MDCUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.math.BigDecimal;

@Component
public class ESGComplianceEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(ESGComplianceEventConsumer.class);
    
    private static final String TOPIC = "waqiti.compliance.esg-compliance";
    private static final String CONSUMER_GROUP = "esg-compliance-consumer-group";
    private static final String DLQ_TOPIC = "waqiti.compliance.esg-compliance.dlq";
    
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;
    private final DlqService dlqService;
    private final MeterRegistry meterRegistry;
    private final KafkaHealthIndicator kafkaHealthIndicator;
    private final ESGComplianceService esgComplianceService;
    private final ESGReportingService reportingService;
    private final ESGRiskAssessmentService riskAssessmentService;
    private final ESGDataCollectionService dataCollectionService;
    private final ESGNotificationService notificationService;
    
    @Value("${compliance.esg.rate-limit.global:1000}")
    private int globalRateLimit;
    
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(3);
    private final ExecutorService batchExecutor = Executors.newFixedThreadPool(4);
    
    private CircuitBreaker circuitBreaker;
    private Retry retryConfig;
    
    private Counter messagesProcessedCounter;
    private Counter messagesFailedCounter;
    private Counter esgAssessmentsCounter;
    private Counter esgReportsGeneratedCounter;
    private Counter esgViolationsCounter;
    private Timer messageProcessingTimer;
    
    private final AtomicLong totalAssessments = new AtomicLong(0);
    private final AtomicInteger currentGlobalRate = new AtomicInteger(0);
    
    private final ConcurrentHashMap<String, ESGAssessment> assessments = new ConcurrentHashMap<>();
    private final BlockingQueue<ESGComplianceTask> taskQueue = new LinkedBlockingQueue<>();
    
    public ESGComplianceEventConsumer(
            ObjectMapper objectMapper,
            MetricsService metricsService,
            DlqService dlqService,
            MeterRegistry meterRegistry,
            KafkaHealthIndicator kafkaHealthIndicator,
            ESGComplianceService esgComplianceService,
            ESGReportingService reportingService,
            ESGRiskAssessmentService riskAssessmentService,
            ESGDataCollectionService dataCollectionService,
            ESGNotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
        this.dlqService = dlqService;
        this.meterRegistry = meterRegistry;
        this.kafkaHealthIndicator = kafkaHealthIndicator;
        this.esgComplianceService = esgComplianceService;
        this.reportingService = reportingService;
        this.riskAssessmentService = riskAssessmentService;
        this.dataCollectionService = dataCollectionService;
        this.notificationService = notificationService;
    }
    
    @PostConstruct
    public void init() {
        initializeCircuitBreaker();
        initializeRetry();
        initializeMetrics();
        startTaskProcessor();
        startRateLimitReset();
        logger.info("ESGComplianceEventConsumer initialized successfully");
    }
    
    @PreDestroy
    public void cleanup() {
        scheduledExecutor.shutdown();
        batchExecutor.shutdown();
        logger.info("ESGComplianceEventConsumer cleanup completed");
    }
    
    private void initializeCircuitBreaker() {
        circuitBreaker = CircuitBreaker.of("esg-compliance-circuit-breaker",
            CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(100)
                .minimumNumberOfCalls(10)
                .build());
    }
    
    private void initializeRetry() {
        retryConfig = Retry.of("esg-compliance-retry",
            RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(1000))
                .exponentialBackoffMultiplier(2.0)
                .build());
    }
    
    private void initializeMetrics() {
        messagesProcessedCounter = Counter.builder("esg_compliance_messages_processed_total")
            .description("Total ESG compliance messages processed")
            .register(meterRegistry);
            
        messagesFailedCounter = Counter.builder("esg_compliance_messages_failed_total")
            .description("Total ESG compliance messages failed")
            .register(meterRegistry);
            
        esgAssessmentsCounter = Counter.builder("esg_assessments_total")
            .description("Total ESG assessments")
            .register(meterRegistry);
            
        esgReportsGeneratedCounter = Counter.builder("esg_reports_generated_total")
            .description("Total ESG reports generated")
            .register(meterRegistry);
            
        esgViolationsCounter = Counter.builder("esg_violations_total")
            .description("Total ESG violations detected")
            .register(meterRegistry);
        
        messageProcessingTimer = Timer.builder("esg_compliance_message_processing_duration")
            .description("ESG compliance message processing duration")
            .register(meterRegistry);
        
        Gauge.builder("esg_total_assessments")
            .description("Total ESG assessments")
            .register(meterRegistry, this, value -> totalAssessments.get());
    }
    
    @KafkaListener(topics = TOPIC, groupId = CONSUMER_GROUP)
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processESGCompliance(@Payload String message,
                                   @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                   @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                   @Header(KafkaHeaders.OFFSET) long offset,
                                   @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
                                   Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String requestId = UUID.randomUUID().toString();
        
        try {
            MDCUtil.setRequestId(requestId);
            MDC.put("topic", topic);
            MDC.put("partition", String.valueOf(partition));
            MDC.put("offset", String.valueOf(offset));
            
            logger.info("Processing ESG compliance message: topic={}, partition={}, offset={}", 
                topic, partition, offset);
            
            if (!isWithinRateLimit()) {
                logger.warn("Rate limit exceeded, requeueing message");
                dlqService.sendToDlq(DLQ_TOPIC, message, "Rate limit exceeded", requestId);
                acknowledgment.acknowledge();
                return;
            }
            
            JsonNode messageNode = objectMapper.readTree(message);
            String eventType = messageNode.path("eventType").asText();
            
            boolean processed = circuitBreaker.executeSupplier(() ->
                retryConfig.executeSupplier(() -> {
                    return executeProcessingStep(eventType, messageNode, requestId);
                })
            );
            
            if (processed) {
                messagesProcessedCounter.increment();
                metricsService.recordCustomMetric("esg_compliance_processed", 1.0, 
                    Map.of("eventType", eventType, "requestId", requestId));
                acknowledgment.acknowledge();
                logger.info("Successfully processed ESG compliance message: eventType={}, requestId={}", 
                    eventType, requestId);
            } else {
                throw new RuntimeException("Failed to process ESG compliance message: " + eventType);
            }
            
        } catch (Exception e) {
            logger.error("Error processing ESG compliance message", e);
            messagesFailedCounter.increment();
            
            try {
                dlqService.sendToDlq(DLQ_TOPIC, message, e.getMessage(), requestId);
                acknowledgment.acknowledge();
            } catch (Exception dlqException) {
                logger.error("Failed to send message to DLQ", dlqException);
            }
        } finally {
            sample.stop(messageProcessingTimer);
            MDC.clear();
        }
    }
    
    private boolean executeProcessingStep(String eventType, JsonNode messageNode, String requestId) {
        switch (eventType) {
            case "ESG_ASSESSMENT_REQUEST":
                return processEsgAssessmentRequest(messageNode, requestId);
            case "ESG_DATA_COLLECTION":
                return processEsgDataCollection(messageNode, requestId);
            case "ESG_RISK_EVALUATION":
                return processEsgRiskEvaluation(messageNode, requestId);
            case "ESG_REPORT_GENERATION":
                return processEsgReportGeneration(messageNode, requestId);
            case "ESG_VIOLATION_ALERT":
                return processEsgViolationAlert(messageNode, requestId);
            case "ESG_POLICY_UPDATE":
                return processEsgPolicyUpdate(messageNode, requestId);
            case "ESG_AUDIT_REQUEST":
                return processEsgAuditRequest(messageNode, requestId);
            case "ESG_CERTIFICATION_REVIEW":
                return processEsgCertificationReview(messageNode, requestId);
            case "ESG_STAKEHOLDER_ENGAGEMENT":
                return processEsgStakeholderEngagement(messageNode, requestId);
            case "ESG_REGULATORY_FILING":
                return processEsgRegulatoryFiling(messageNode, requestId);
            default:
                logger.warn("Unknown event type: {}", eventType);
                return false;
        }
    }
    
    private boolean processEsgAssessmentRequest(JsonNode messageNode, String requestId) {
        try {
            totalAssessments.incrementAndGet();
            
            String entityId = messageNode.path("entityId").asText();
            String assessmentType = messageNode.path("assessmentType").asText();
            String assessmentScope = messageNode.path("assessmentScope").asText();
            JsonNode esgCriteria = messageNode.path("esgCriteria");
            String reportingPeriod = messageNode.path("reportingPeriod").asText();
            
            ESGAssessment assessment = ESGAssessment.builder()
                .id(UUID.randomUUID().toString())
                .entityId(entityId)
                .assessmentType(assessmentType)
                .assessmentScope(assessmentScope)
                .esgCriteria(extractStringList(esgCriteria))
                .reportingPeriod(reportingPeriod)
                .status("INITIATED")
                .initiatedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            assessments.put(assessment.getId(), assessment);
            
            ESGComplianceTask task = ESGComplianceTask.builder()
                .id(UUID.randomUUID().toString())
                .assessmentId(assessment.getId())
                .taskType("ASSESSMENT")
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();
            
            taskQueue.offer(task);
            esgAssessmentsCounter.increment();
            
            logger.info("Processed ESG assessment request: id={}, entityId={}, type={}, scope={}", 
                assessment.getId(), entityId, assessmentType, assessmentScope);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing ESG assessment request", e);
            return false;
        }
    }
    
    private boolean processEsgDataCollection(JsonNode messageNode, String requestId) {
        try {
            String entityId = messageNode.path("entityId").asText();
            String dataType = messageNode.path("dataType").asText();
            JsonNode dataPoints = messageNode.path("dataPoints");
            String collectionPeriod = messageNode.path("collectionPeriod").asText();
            
            ESGDataCollection dataCollection = ESGDataCollection.builder()
                .id(UUID.randomUUID().toString())
                .entityId(entityId)
                .dataType(dataType)
                .dataPoints(dataPoints.toString())
                .collectionPeriod(collectionPeriod)
                .status("COLLECTED")
                .collectedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            dataCollectionService.processDataCollection(dataCollection);
            
            logger.info("Processed ESG data collection: id={}, entityId={}, type={}, pointCount={}", 
                dataCollection.getId(), entityId, dataType, dataPoints.size());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing ESG data collection", e);
            return false;
        }
    }
    
    private boolean processEsgRiskEvaluation(JsonNode messageNode, String requestId) {
        try {
            String entityId = messageNode.path("entityId").asText();
            JsonNode riskFactors = messageNode.path("riskFactors");
            String evaluationMethod = messageNode.path("evaluationMethod").asText();
            
            ESGRiskEvaluation riskEvaluation = riskAssessmentService.evaluateESGRisk(
                entityId, riskFactors, evaluationMethod);
            
            riskEvaluation.setRequestId(requestId);
            riskAssessmentService.saveRiskEvaluation(riskEvaluation);
            
            logger.info("Processed ESG risk evaluation: entityId={}, riskLevel={}, score={}", 
                entityId, riskEvaluation.getRiskLevel(), riskEvaluation.getRiskScore());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing ESG risk evaluation", e);
            return false;
        }
    }
    
    private boolean processEsgReportGeneration(JsonNode messageNode, String requestId) {
        try {
            String entityId = messageNode.path("entityId").asText();
            String reportType = messageNode.path("reportType").asText();
            String reportingStandard = messageNode.path("reportingStandard").asText();
            String reportingPeriod = messageNode.path("reportingPeriod").asText();
            JsonNode reportParameters = messageNode.path("reportParameters");
            
            ESGReport report = reportingService.generateESGReport(
                entityId, reportType, reportingStandard, reportingPeriod, reportParameters);
            
            report.setRequestId(requestId);
            reportingService.saveReport(report);
            
            esgReportsGeneratedCounter.increment();
            
            logger.info("Generated ESG report: id={}, entityId={}, type={}, standard={}, period={}", 
                report.getId(), entityId, reportType, reportingStandard, reportingPeriod);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error generating ESG report", e);
            return false;
        }
    }
    
    private boolean processEsgViolationAlert(JsonNode messageNode, String requestId) {
        try {
            String entityId = messageNode.path("entityId").asText();
            String violationType = messageNode.path("violationType").asText();
            String severity = messageNode.path("severity").asText();
            String description = messageNode.path("description").asText();
            JsonNode impactedMetrics = messageNode.path("impactedMetrics");
            
            ESGViolation violation = ESGViolation.builder()
                .id(UUID.randomUUID().toString())
                .entityId(entityId)
                .violationType(violationType)
                .severity(severity)
                .description(description)
                .impactedMetrics(extractStringList(impactedMetrics))
                .status("OPEN")
                .detectedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            esgComplianceService.processViolation(violation);
            esgViolationsCounter.increment();
            
            notificationService.sendViolationAlert(violation);
            
            logger.warn("Processed ESG violation alert: id={}, entityId={}, type={}, severity={}", 
                violation.getId(), entityId, violationType, severity);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing ESG violation alert", e);
            return false;
        }
    }
    
    private boolean processEsgPolicyUpdate(JsonNode messageNode, String requestId) {
        try {
            String policyId = messageNode.path("policyId").asText();
            String policyType = messageNode.path("policyType").asText();
            JsonNode policyChanges = messageNode.path("policyChanges");
            String effectiveDate = messageNode.path("effectiveDate").asText();
            
            ESGPolicyUpdate policyUpdate = ESGPolicyUpdate.builder()
                .id(UUID.randomUUID().toString())
                .policyId(policyId)
                .policyType(policyType)
                .policyChanges(policyChanges.toString())
                .effectiveDate(LocalDateTime.parse(effectiveDate))
                .status("IMPLEMENTED")
                .updatedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            esgComplianceService.updatePolicy(policyUpdate);
            
            logger.info("Processed ESG policy update: id={}, policyId={}, type={}, effectiveDate={}", 
                policyUpdate.getId(), policyId, policyType, effectiveDate);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing ESG policy update", e);
            return false;
        }
    }
    
    private boolean processEsgAuditRequest(JsonNode messageNode, String requestId) {
        try {
            String entityId = messageNode.path("entityId").asText();
            String auditType = messageNode.path("auditType").asText();
            String auditScope = messageNode.path("auditScope").asText();
            String auditorId = messageNode.path("auditorId").asText();
            JsonNode auditCriteria = messageNode.path("auditCriteria");
            
            ESGAudit audit = ESGAudit.builder()
                .id(UUID.randomUUID().toString())
                .entityId(entityId)
                .auditType(auditType)
                .auditScope(auditScope)
                .auditorId(auditorId)
                .auditCriteria(extractStringList(auditCriteria))
                .status("SCHEDULED")
                .scheduledAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            esgComplianceService.scheduleAudit(audit);
            
            logger.info("Processed ESG audit request: id={}, entityId={}, type={}, scope={}, auditor={}", 
                audit.getId(), entityId, auditType, auditScope, auditorId);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing ESG audit request", e);
            return false;
        }
    }
    
    private boolean processEsgCertificationReview(JsonNode messageNode, String requestId) {
        try {
            String entityId = messageNode.path("entityId").asText();
            String certificationType = messageNode.path("certificationType").asText();
            String certificationBody = messageNode.path("certificationBody").asText();
            String currentStatus = messageNode.path("currentStatus").asText();
            String expiryDate = messageNode.path("expiryDate").asText();
            
            ESGCertificationReview review = ESGCertificationReview.builder()
                .id(UUID.randomUUID().toString())
                .entityId(entityId)
                .certificationType(certificationType)
                .certificationBody(certificationBody)
                .currentStatus(currentStatus)
                .expiryDate(LocalDateTime.parse(expiryDate))
                .reviewStatus("IN_PROGRESS")
                .reviewStarted(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            esgComplianceService.processCertificationReview(review);
            
            logger.info("Processed ESG certification review: id={}, entityId={}, type={}, body={}, status={}", 
                review.getId(), entityId, certificationType, certificationBody, currentStatus);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing ESG certification review", e);
            return false;
        }
    }
    
    private boolean processEsgStakeholderEngagement(JsonNode messageNode, String requestId) {
        try {
            String entityId = messageNode.path("entityId").asText();
            String engagementType = messageNode.path("engagementType").asText();
            JsonNode stakeholderGroups = messageNode.path("stakeholderGroups");
            JsonNode engagementTopics = messageNode.path("engagementTopics");
            String engagementMethod = messageNode.path("engagementMethod").asText();
            
            ESGStakeholderEngagement engagement = ESGStakeholderEngagement.builder()
                .id(UUID.randomUUID().toString())
                .entityId(entityId)
                .engagementType(engagementType)
                .stakeholderGroups(extractStringList(stakeholderGroups))
                .engagementTopics(extractStringList(engagementTopics))
                .engagementMethod(engagementMethod)
                .status("PLANNED")
                .plannedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            esgComplianceService.planStakeholderEngagement(engagement);
            
            logger.info("Processed ESG stakeholder engagement: id={}, entityId={}, type={}, method={}, groups={}", 
                engagement.getId(), entityId, engagementType, engagementMethod, stakeholderGroups.size());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing ESG stakeholder engagement", e);
            return false;
        }
    }
    
    private boolean processEsgRegulatoryFiling(JsonNode messageNode, String requestId) {
        try {
            String entityId = messageNode.path("entityId").asText();
            String filingType = messageNode.path("filingType").asText();
            String regulatoryBody = messageNode.path("regulatoryBody").asText();
            String filingPeriod = messageNode.path("filingPeriod").asText();
            String dueDate = messageNode.path("dueDate").asText();
            JsonNode filingData = messageNode.path("filingData");
            
            ESGRegulatoryFiling filing = ESGRegulatoryFiling.builder()
                .id(UUID.randomUUID().toString())
                .entityId(entityId)
                .filingType(filingType)
                .regulatoryBody(regulatoryBody)
                .filingPeriod(filingPeriod)
                .dueDate(LocalDateTime.parse(dueDate))
                .filingData(filingData.toString())
                .status("PREPARED")
                .preparedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            esgComplianceService.prepareRegulatoryFiling(filing);
            
            logger.info("Processed ESG regulatory filing: id={}, entityId={}, type={}, body={}, dueDate={}", 
                filing.getId(), entityId, filingType, regulatoryBody, dueDate);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing ESG regulatory filing", e);
            return false;
        }
    }
    
    private void startTaskProcessor() {
        batchExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    ESGComplianceTask task = taskQueue.take();
                    esgComplianceService.processTask(task);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in task processor", e);
                }
            }
        });
    }
    
    private void startRateLimitReset() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            currentGlobalRate.set(0);
        }, 1, 1, TimeUnit.MINUTES);
    }
    
    private boolean isWithinRateLimit() {
        return currentGlobalRate.incrementAndGet() <= globalRateLimit;
    }
    
    private List<String> extractStringList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            arrayNode.forEach(node -> list.add(node.asText()));
        }
        return list;
    }
}