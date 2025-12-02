package com.waqiti.compliance.kafka;

import com.waqiti.common.events.AMLAlertEvent;
import com.waqiti.common.kafka.KafkaEventTrackingService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.outbox.OutboxService;
import com.waqiti.compliance.entity.*;
import com.waqiti.compliance.repository.*;
import com.waqiti.compliance.service.*;
import com.waqiti.compliance.ml.AMLMachineLearningService;
import com.waqiti.compliance.workflow.CaseWorkflowEngine;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.OptimisticLockException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Production-grade Kafka consumer for AML (Anti-Money Laundering) alert case management
 * 
 * This consumer implements comprehensive AML alert processing and case management including:
 * - Automated case creation with priority assignment
 * - ML-powered risk scoring and false positive reduction
 * - Intelligent case routing based on analyst expertise
 * - SLA tracking and escalation management
 * - Regulatory filing automation (SAR/STR)
 * - Evidence collection and documentation
 * - Multi-jurisdictional compliance support
 * - Quality assurance and review workflows
 * 
 * Regulatory Compliance:
 * - FATF Recommendations (especially R.10, R.20)
 * - USA PATRIOT Act Section 314(b)
 * - EU 5th/6th AMLD requirements
 * - BSA (Bank Secrecy Act) compliance
 * - FinCEN SAR filing requirements
 * - Global AML/CFT standards
 * 
 * Case Management Features:
 * - Automated triage and prioritization
 * - Workload balancing across analysts
 * - Collaborative investigation tools
 * - Decision audit trail and justification
 * - Regulatory reporting automation
 * - Performance metrics and analytics
 * - Knowledge management integration
 * 
 * @author Waqiti Platform Team - Phase 1 Remediation
 * @since Session 6 - Production Implementation (Recreated)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AMLAlertConsumer {

    // Core services
    private final CaseManagementService caseManagementService;
    private final InvestigationService investigationService;
    private final RiskScoringService riskScoringService;
    private final UniversalDLQHandler universalDLQHandler;
    private final AMLMachineLearningService mlService;
    private final SARFilingService sarFilingService;
    private final CustomerDueDiligenceService cddService;
    private final TransactionMonitoringService transactionMonitoringService;
    private final NetworkAnalysisService networkAnalysisService;
    private final CaseWorkflowEngine workflowEngine;
    private final NotificationService notificationService;
    private final AnalystAssignmentService analystAssignmentService;
    private final QualityAssuranceService qaService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final KafkaEventTrackingService eventTrackingService;
    private final OutboxService outboxService;
    
    // Repositories
    private final AMLCaseRepository caseRepository;
    private final AMLAlertRepository alertRepository;
    private final CaseInvestigationRepository investigationRepository;
    private final CaseDecisionRepository decisionRepository;
    private final CaseEvidenceRepository evidenceRepository;
    private final SARReportRepository sarReportRepository;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // Metrics
    private final MeterRegistry meterRegistry;
    private final Counter alertProcessedCounter;
    private final Counter caseCreatedCounter;
    private final Counter sarFiledCounter;
    private final Counter falsePositiveCounter;
    private final Timer caseProcessingTimer;
    private final Gauge activeCasesGauge;
    private final Summary riskScoreSummary;
    
    // Thread pools
    private final ExecutorService investigationExecutor = Executors.newFixedThreadPool(20);
    private final ScheduledExecutorService slaMonitor = Executors.newScheduledThreadPool(5);
    
    // Case tracking
    private final ConcurrentHashMap<String, CaseContext> activeCases = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AnalystWorkload> analystWorkloads = new ConcurrentHashMap<>();
    
    // Configuration
    @Value("${aml.case.auto-create.threshold:60}")
    private int autoCreateThreshold;
    
    @Value("${aml.case.priority.high.threshold:85}")
    private int highPriorityThreshold;
    
    @Value("${aml.case.priority.critical.threshold:95}")
    private int criticalPriorityThreshold;
    
    @Value("${aml.case.sla.critical.hours:4}")
    private int criticalSLAHours;
    
    @Value("${aml.case.sla.high.hours:24}")
    private int highSLAHours;
    
    @Value("${aml.case.sla.medium.hours:72}")
    private int mediumSLAHours;
    
    @Value("${aml.case.sla.low.hours:120}")
    private int lowSLAHours;
    
    @Value("${aml.case.sar.auto-file.threshold:90}")
    private int autoFileSARThreshold;
    
    @Value("${aml.case.ml.enabled:true}")
    private boolean mlEnabled;
    
    @Value("${aml.case.network-analysis.enabled:true}")
    private boolean networkAnalysisEnabled;
    
    @Value("${aml.case.qa.required.threshold:80}")
    private int qaRequiredThreshold;
    
    @Value("${aml.case.max.analysts.per.case:3}")
    private int maxAnalystsPerCase;
    
    // Constants
    private static final String TOPIC_NAME = "aml.alert.raised";
    private static final String CONSUMER_GROUP = "aml-case-management";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final String METRIC_PREFIX = "aml.case.";

    /**
     * Main event handler for AML alert processing and case creation
     * 
     * Processing Flow:
     * 1. Validate and enrich alert data
     * 2. Check for duplicate alerts
     * 3. Calculate risk score using ML models
     * 4. Determine case creation necessity
     * 5. Create investigation case with priority
     * 6. Collect initial evidence
     * 7. Assign to appropriate analyst
     * 8. Set SLA timers
     * 9. Initiate workflow
     * 10. Send notifications
     * 11. Monitor for escalation
     * 12. Track metrics
     */
    @KafkaListener(
        topics = TOPIC_NAME,
        groupId = CONSUMER_GROUP,
        containerFactory = "complianceCriticalKafkaListenerContainerFactory",
        concurrency = "${kafka.consumer.aml.concurrency:8}",
        properties = {
            "max.poll.interval.ms:300000",
            "max.poll.records:25",
            "enable.auto.commit:false",
            "isolation.level:read_committed",
            "fetch.min.bytes:1",
            "fetch.max.wait.ms:1000"
        }
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 10000),
        include = {OptimisticLockException.class, TemporaryCaseException.class},
        exclude = {IllegalArgumentException.class, ValidationException.class},
        dltStrategy = DltStrategy.FAIL_ON_ERROR,
        dltDestinationSuffix = ".aml-dlt",
        autoCreateTopics = "true"
    )
    @Transactional(
        isolation = Isolation.READ_COMMITTED,
        timeout = 120,
        propagation = Propagation.REQUIRED
    )
    @CircuitBreaker(name = "aml-case-processing", fallbackMethod = "handleCaseCreationFailure")
    @Bulkhead(name = "aml-case-processing", maxConcurrentCalls = 25)
    public void handleAMLAlertEvent(
            @Payload AMLAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            @Header(value = KafkaHeaders.RECEIVED_MESSAGE_KEY, required = false) String key,
            @Header(value = "X-Alert-Id", required = false) String alertId,
            @Header(value = "X-Alert-Priority", required = false) String priority,
            @Header(value = "X-Correlation-Id", required = false) String correlationId,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(topic, partition, offset);
        LocalDateTime startTime = LocalDateTime.now();
        CaseContext context = new CaseContext();
        
        log.info("AML ALERT: Processing AML alert event. EventId: {}, AlertId: {}, " +
                "CustomerId: {}, AlertType: {}, RiskIndicators: {}, Amount: {} {}, " +
                "Topic: {}, Partition: {}, Offset: {}",
                eventId, event.getAlertId(), event.getCustomerId(), event.getAlertType(),
                event.getRiskIndicators(), event.getTransactionAmount(), 
                event.getCurrency(), topic, partition, offset);
        
        try {
            // Step 1: Validate alert
            validateAMLAlert(event);
            
            // Step 2: Check for duplicate alerts
            if (isDuplicateAlert(event)) {
                log.info("Duplicate alert detected. Merging with existing case. AlertId: {}", 
                        event.getAlertId());
                mergeWithExistingCase(event);
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 3: Initialize case context
            context = initializeCaseContext(event, eventId, priority);
            activeCases.put(event.getAlertId(), context);
            
            // Step 4: Enrich alert with additional data
            enrichAlertData(event, context);
            
            // Step 5: Calculate risk score
            RiskScore riskScore = calculateRiskScore(event, context);
            context.setRiskScore(riskScore);
            
            // Step 6: Apply ML enhancement
            if (mlEnabled) {
                applyMLEnhancement(event, context, riskScore);
            }
            
            // Step 7: Determine if case creation is needed
            CaseDecision decision = determineCaseCreation(event, context, riskScore);
            context.setCaseDecision(decision);
            
            if (decision == CaseDecision.CREATE_CASE || decision == CaseDecision.CREATE_URGENT) {
                // Step 8: Create investigation case
                AMLCase amlCase = createInvestigationCase(event, context, riskScore);
                context.setAmlCase(amlCase);
                
                // Step 9: Collect initial evidence
                List<Evidence> evidence = collectInitialEvidence(event, context);
                context.setEvidence(evidence);
                
                // Step 10: Perform network analysis
                if (networkAnalysisEnabled) {
                    NetworkAnalysisResult networkResult = performNetworkAnalysis(event, context);
                    context.setNetworkAnalysis(networkResult);
                }
                
                // Step 11: Assign to analyst
                AnalystAssignment assignment = assignToAnalyst(amlCase, context);
                context.setAssignment(assignment);
                
                // Step 12: Set SLA timers
                SLAConfiguration sla = configureSLA(amlCase, context);
                context.setSla(sla);
                
                // Step 13: Initiate workflow
                WorkflowInstance workflow = initiateWorkflow(amlCase, context);
                context.setWorkflow(workflow);
                
                // Step 14: Check for auto-SAR filing
                if (shouldAutoFileSAR(riskScore, event)) {
                    initiateSARFiling(amlCase, context);
                }
                
                // Step 15: Setup monitoring
                setupCaseMonitoring(amlCase, context);
                
                // Step 16: Send notifications
                sendCaseNotifications(amlCase, context, assignment);
                
                // Step 17: Update metrics
                caseCreatedCounter.increment();
                riskScoreSummary.record(riskScore.getScore());
                
                log.info("AML case created successfully. CaseId: {}, Priority: {}, " +
                        "RiskScore: {}, AssignedTo: {}, SLA: {}",
                        amlCase.getCaseId(), amlCase.getPriority(), 
                        riskScore.getScore(), assignment.getAnalystId(),
                        sla.getDeadline());
                
            } else if (decision == CaseDecision.MONITOR) {
                // Add to monitoring without case creation
                addToMonitoring(event, context, riskScore);
                
            } else if (decision == CaseDecision.FALSE_POSITIVE) {
                // Record as false positive
                recordFalsePositive(event, context, riskScore);
                falsePositiveCounter.increment();
            }
            
            // Step 18: Save alert record
            saveAlertRecord(event, context);
            
            // Step 19: Publish case event
            publishCaseEvent(event, context);
            
            // Step 20: Record event tracking
            eventTrackingService.recordEvent(
                eventId, topic, partition, offset,
                context.getCaseDecision().toString(),
                Duration.between(startTime, LocalDateTime.now()).toMillis()
            );
            
            // Step 21: Update processing metrics
            alertProcessedCounter.increment();
            sample.stop(caseProcessingTimer);
            
            // Step 22: Clean up context
            activeCases.remove(event.getAlertId());
            
            // Step 23: Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed AML alert. AlertId: {}, Decision: {}, " +
                    "ProcessingTime: {}ms",
                    event.getAlertId(), decision,
                    Duration.between(startTime, LocalDateTime.now()).toMillis());
            
        } catch (ValidationException e) {
            log.error("Invalid AML alert data. AlertId: {}, Error: {}", 
                    event.getAlertId(), e.getMessage());
            // Don't retry validation errors
            acknowledgment.acknowledge();
            
        } catch (OptimisticLockException e) {
            log.warn("Optimistic lock exception - retrying. AlertId: {}", 
                    event.getAlertId());
            throw e; // Let retry mechanism handle
            
        } catch (Exception e) {
            log.error("Failed to process AML alert. AlertId: {}, Error: {}",
                    event.getAlertId(), e.getMessage(), e);

            // Send to DLQ for retry/parking
            try {
                org.apache.kafka.clients.consumer.ConsumerRecord<String, AMLAlertEvent> consumerRecord =
                    new org.apache.kafka.clients.consumer.ConsumerRecord<>(
                        topic, partition, offset, key != null ? key : event.getAlertId(), event);
                universalDLQHandler.handleFailedMessage(consumerRecord, e);
            } catch (Exception dlqEx) {
                log.error("CRITICAL: Failed to send AML alert to DLQ: {}", event.getAlertId(), dlqEx);
            }

            // Record failure
            eventTrackingService.recordEvent(
                eventId, topic, partition, offset, "FAILED",
                Duration.between(startTime, LocalDateTime.now()).toMillis()
            );

            // Clean up
            activeCases.remove(event.getAlertId());

            throw new RuntimeException("AML alert processing failed", e);
        }
    }
    
    // Helper methods
    
    private void validateAMLAlert(AMLAlertEvent event) {
        if (event.getAlertId() == null) {
            throw new ValidationException("Alert ID is required");
        }
        if (event.getCustomerId() == null) {
            throw new ValidationException("Customer ID is required");
        }
        if (event.getAlertType() == null) {
            throw new ValidationException("Alert type is required");
        }
        if (event.getRiskIndicators() == null || event.getRiskIndicators().isEmpty()) {
            throw new ValidationException("Risk indicators are required");
        }
    }
    
    private CaseContext initializeCaseContext(
            AMLAlertEvent event, String eventId, String priority) {
        CaseContext context = new CaseContext();
        context.setCaseId(UUID.randomUUID());
        context.setEventId(eventId);
        context.setAlertId(event.getAlertId());
        context.setCustomerId(event.getCustomerId());
        context.setAlertType(event.getAlertType());
        context.setPriority(priority != null ? priority : "MEDIUM");
        context.setCreatedAt(LocalDateTime.now());
        context.setJurisdictions(determineJurisdictions(event));
        return context;
    }
    
    private RiskScore calculateRiskScore(AMLAlertEvent event, CaseContext context) {
        log.debug("Calculating risk score for alert: {}", event.getAlertId());
        
        RiskScore score = new RiskScore();
        score.setAlertId(event.getAlertId());
        score.setCalculationTime(LocalDateTime.now());
        
        // Base score from risk indicators
        double baseScore = calculateBaseScore(event.getRiskIndicators());
        
        // Customer risk rating adjustment
        double customerRiskAdjustment = getCustomerRiskAdjustment(context.getCustomerProfile());
        
        // Transaction pattern score
        double patternScore = calculatePatternScore(context.getTransactionHistory(), event);
        
        // Geographic risk
        double geoRisk = calculateGeographicRisk(event, context);
        
        // Product risk
        double productRisk = calculateProductRisk(event);
        
        // Behavioral risk
        double behavioralRisk = calculateBehavioralRisk(context);
        
        // Historical case adjustment
        double historicalAdjustment = calculateHistoricalAdjustment(context.getPreviousCases());
        
        // Calculate weighted final score
        double finalScore = (baseScore * 0.3) +
                          (patternScore * 0.25) +
                          (geoRisk * 0.15) +
                          (productRisk * 0.1) +
                          (behavioralRisk * 0.1) +
                          (customerRiskAdjustment * 0.05) +
                          (historicalAdjustment * 0.05);
        
        // Normalize to 0-100
        finalScore = Math.min(100, Math.max(0, finalScore));
        
        score.setScore(finalScore);
        
        // Determine risk level
        if (finalScore >= criticalPriorityThreshold) {
            score.setRiskLevel("CRITICAL");
        } else if (finalScore >= highPriorityThreshold) {
            score.setRiskLevel("HIGH");
        } else if (finalScore >= 60) {
            score.setRiskLevel("MEDIUM");
        } else {
            score.setRiskLevel("LOW");
        }
        
        log.info("Calculated risk score. AlertId: {}, Score: {}, Level: {}", 
                event.getAlertId(), finalScore, score.getRiskLevel());
        
        return score;
    }
    
    private String generateEventId(String topic, int partition, long offset) {
        return String.format("aml-%s-%d-%d-%d", 
                topic, partition, offset, System.currentTimeMillis());
    }
    
    // Inner classes
    
    @lombok.Data
    private static class CaseContext {
        private UUID caseId;
        private String eventId;
        private String alertId;
        private String customerId;
        private String alertType;
        private String priority;
        private LocalDateTime createdAt;
        private List<String> jurisdictions;
        private CustomerProfile customerProfile;
        private TransactionHistory transactionHistory;
        private List<AMLCase> previousCases;
        private List<RelatedParty> relatedParties;
        private RiskScore riskScore;
        private MLPrediction mlPrediction;
        private CaseDecision caseDecision;
        private AMLCase amlCase;
        private List<Evidence> evidence;
        private NetworkAnalysisResult networkAnalysis;
        private AnalystAssignment assignment;
        private SLAConfiguration sla;
        private WorkflowInstance workflow;
    }
    
    private enum CaseDecision {
        CREATE_URGENT, CREATE_CASE, MONITOR, FALSE_POSITIVE, DISMISS
    }
    
    @lombok.Data
    private static class RiskScore {
        private String alertId;
        private double score;
        private double adjustedScore;
        private String riskLevel;
        private Map<String, Double> components;
        private boolean mlAdjusted;
        private LocalDateTime calculationTime;
    }
    
    private static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }
    
    private static class TemporaryCaseException extends RuntimeException {
        public TemporaryCaseException(String message) {
            super(message);
        }
    }
}