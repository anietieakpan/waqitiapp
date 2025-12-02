package com.waqiti.lending.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.lending.model.*;
import com.waqiti.lending.service.*;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.service.DlqService;
import com.waqiti.common.kafka.KafkaHealthIndicator;
import com.waqiti.common.utils.MDCUtil;
import com.waqiti.common.security.SecurityContextHolder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class CashAdvanceRequestedEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(CashAdvanceRequestedEventConsumer.class);
    
    private static final String TOPIC = "waqiti.lending.cash-advance-requested";
    private static final String CONSUMER_GROUP = "cash-advance-requested-consumer-group";
    private static final String DLQ_TOPIC = "waqiti.lending.cash-advance-requested.dlq";
    
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;
    private final DlqService dlqService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final KafkaHealthIndicator kafkaHealthIndicator;
    private final CashAdvanceService cashAdvanceService;
    private final CashAdvanceEligibilityService eligibilityService;
    private final CashAdvanceRiskAssessmentService riskAssessmentService;
    private final CashAdvanceUnderwritingService underwritingService;
    private final CashAdvanceFundingService fundingService;
    private final CashAdvanceComplianceService complianceService;
    private final CashAdvanceNotificationService notificationService;
    private final SecurityContextHolder securityContextHolder;
    
    @Value("${lending.cash-advance.max-amount:5000.00}")
    private BigDecimal maxCashAdvanceAmount;
    
    @Value("${lending.cash-advance.min-amount:50.00}")
    private BigDecimal minCashAdvanceAmount;
    
    @Value("${lending.cash-advance.processing-fee-rate:0.05}")
    private BigDecimal processingFeeRate;
    
    @Value("${lending.cash-advance.apr-rate:0.395}")
    private BigDecimal aprRate;
    
    @Value("${lending.cash-advance.max-term-days:30}")
    private int maxTermDays;
    
    @Value("${lending.cash-advance.rate-limit.global:1000}")
    private int globalRateLimit;
    
    @Value("${lending.cash-advance.batch-size:50}")
    private int batchSize;
    
    @Value("${lending.cash-advance.max-retry-attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${lending.cash-advance.circuit-breaker.failure-rate:50}")
    private float circuitBreakerFailureRate;
    
    @Value("${lending.cash-advance.circuit-breaker.wait-duration:30}")
    private long circuitBreakerWaitDuration;
    
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(4);
    private final ExecutorService batchExecutor = Executors.newFixedThreadPool(6);
    private final ExecutorService priorityExecutor = Executors.newFixedThreadPool(2);
    
    private CircuitBreaker circuitBreaker;
    private Retry retryConfig;
    
    private Counter messagesProcessedCounter;
    private Counter messagesFailedCounter;
    private Counter requestsProcessedCounter;
    private Counter requestsApprovedCounter;
    private Counter requestsRejectedCounter;
    private Counter requestsUnderReviewCounter;
    private Counter eligibilityChecksCounter;
    private Counter riskAssessmentsCounter;
    private Counter underwritingDecisionsCounter;
    private Counter complianceChecksCounter;
    private Counter fundingInitiatedCounter;
    private Counter fraudAlertsCounter;
    
    private Timer messageProcessingTimer;
    private Timer eligibilityCheckTimer;
    private Timer riskAssessmentTimer;
    private Timer underwritingDecisionTimer;
    private Timer complianceCheckTimer;
    private Timer fundingProcessingTimer;
    private Timer totalProcessingTimer;
    
    private final AtomicLong totalRequestsCount = new AtomicLong(0);
    private final AtomicLong approvedRequestsCount = new AtomicLong(0);
    private final AtomicLong rejectedRequestsCount = new AtomicLong(0);
    private final AtomicLong underReviewRequestsCount = new AtomicLong(0);
    private final AtomicLong totalAdvanceAmount = new AtomicLong(0);
    private final AtomicInteger currentGlobalRate = new AtomicInteger(0);
    
    private final ConcurrentHashMap<String, CashAdvanceRequest> activeRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CashAdvanceEligibility> eligibilityCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CashAdvanceRisk> riskCache = new ConcurrentHashMap<>();
    private final BlockingQueue<CashAdvanceRequest> requestQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<CashAdvanceBatch> batchQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<CashAdvanceDecision> decisionQueue = new LinkedBlockingQueue<>();
    private final PriorityBlockingQueue<CashAdvanceRequest> priorityQueue = 
        new PriorityBlockingQueue<>(500, Comparator.comparing(CashAdvanceRequest::getPriorityScore).reversed());
    
    public CashAdvanceRequestedEventConsumer(
            ObjectMapper objectMapper,
            MetricsService metricsService,
            DlqService dlqService,
            KafkaTemplate<String, Object> kafkaTemplate,
            MeterRegistry meterRegistry,
            KafkaHealthIndicator kafkaHealthIndicator,
            CashAdvanceService cashAdvanceService,
            CashAdvanceEligibilityService eligibilityService,
            CashAdvanceRiskAssessmentService riskAssessmentService,
            CashAdvanceUnderwritingService underwritingService,
            CashAdvanceFundingService fundingService,
            CashAdvanceComplianceService complianceService,
            CashAdvanceNotificationService notificationService,
            SecurityContextHolder securityContextHolder) {
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
        this.dlqService = dlqService;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        this.kafkaHealthIndicator = kafkaHealthIndicator;
        this.cashAdvanceService = cashAdvanceService;
        this.eligibilityService = eligibilityService;
        this.riskAssessmentService = riskAssessmentService;
        this.underwritingService = underwritingService;
        this.fundingService = fundingService;
        this.complianceService = complianceService;
        this.notificationService = notificationService;
        this.securityContextHolder = securityContextHolder;
    }
    
    @PostConstruct
    public void init() {
        initializeCircuitBreaker();
        initializeRetry();
        initializeMetrics();
        startRequestProcessor();
        startBatchProcessor();
        startDecisionProcessor();
        startPriorityProcessor();
        startRateLimitReset();
        logger.info("CashAdvanceRequestedEventConsumer initialized successfully");
    }
    
    @PreDestroy
    public void cleanup() {
        scheduledExecutor.shutdown();
        batchExecutor.shutdown();
        priorityExecutor.shutdown();
        try {
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            if (!batchExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                batchExecutor.shutdownNow();
            }
            if (!priorityExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                priorityExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduledExecutor.shutdownNow();
            batchExecutor.shutdownNow();
            priorityExecutor.shutdownNow();
        }
        logger.info("CashAdvanceRequestedEventConsumer cleanup completed");
    }
    
    private void initializeCircuitBreaker() {
        circuitBreaker = CircuitBreaker.of("cash-advance-circuit-breaker",
            CircuitBreakerConfig.custom()
                .failureRateThreshold(circuitBreakerFailureRate)
                .waitDurationInOpenState(Duration.ofSeconds(circuitBreakerWaitDuration))
                .slidingWindowSize(100)
                .minimumNumberOfCalls(10)
                .build());
    }
    
    private void initializeRetry() {
        retryConfig = Retry.of("cash-advance-retry",
            RetryConfig.custom()
                .maxAttempts(maxRetryAttempts)
                .waitDuration(Duration.ofMillis(1000))
                .exponentialBackoffMultiplier(2.0)
                .retryOnException(throwable -> !(throwable instanceof SecurityException))
                .build());
    }
    
    private void initializeMetrics() {
        messagesProcessedCounter = Counter.builder("cash_advance_messages_processed_total")
            .description("Total cash advance messages processed")
            .register(meterRegistry);
            
        messagesFailedCounter = Counter.builder("cash_advance_messages_failed_total")
            .description("Total cash advance messages failed")
            .register(meterRegistry);
            
        requestsProcessedCounter = Counter.builder("cash_advance_requests_processed_total")
            .description("Total cash advance requests processed")
            .register(meterRegistry);
            
        requestsApprovedCounter = Counter.builder("cash_advance_requests_approved_total")
            .description("Total cash advance requests approved")
            .register(meterRegistry);
            
        requestsRejectedCounter = Counter.builder("cash_advance_requests_rejected_total")
            .description("Total cash advance requests rejected")
            .register(meterRegistry);
            
        requestsUnderReviewCounter = Counter.builder("cash_advance_requests_under_review_total")
            .description("Total cash advance requests under review")
            .register(meterRegistry);
            
        eligibilityChecksCounter = Counter.builder("cash_advance_eligibility_checks_total")
            .description("Total cash advance eligibility checks")
            .register(meterRegistry);
            
        riskAssessmentsCounter = Counter.builder("cash_advance_risk_assessments_total")
            .description("Total cash advance risk assessments")
            .register(meterRegistry);
            
        underwritingDecisionsCounter = Counter.builder("cash_advance_underwriting_decisions_total")
            .description("Total cash advance underwriting decisions")
            .register(meterRegistry);
            
        complianceChecksCounter = Counter.builder("cash_advance_compliance_checks_total")
            .description("Total cash advance compliance checks")
            .register(meterRegistry);
            
        fundingInitiatedCounter = Counter.builder("cash_advance_funding_initiated_total")
            .description("Total cash advance funding initiated")
            .register(meterRegistry);
            
        fraudAlertsCounter = Counter.builder("cash_advance_fraud_alerts_total")
            .description("Total cash advance fraud alerts")
            .register(meterRegistry);
        
        messageProcessingTimer = Timer.builder("cash_advance_message_processing_duration")
            .description("Cash advance message processing duration")
            .register(meterRegistry);
            
        eligibilityCheckTimer = Timer.builder("cash_advance_eligibility_check_duration")
            .description("Cash advance eligibility check duration")
            .register(meterRegistry);
            
        riskAssessmentTimer = Timer.builder("cash_advance_risk_assessment_duration")
            .description("Cash advance risk assessment duration")
            .register(meterRegistry);
            
        underwritingDecisionTimer = Timer.builder("cash_advance_underwriting_duration")
            .description("Cash advance underwriting duration")
            .register(meterRegistry);
            
        complianceCheckTimer = Timer.builder("cash_advance_compliance_check_duration")
            .description("Cash advance compliance check duration")
            .register(meterRegistry);
            
        fundingProcessingTimer = Timer.builder("cash_advance_funding_processing_duration")
            .description("Cash advance funding processing duration")
            .register(meterRegistry);
            
        totalProcessingTimer = Timer.builder("cash_advance_total_processing_duration")
            .description("Cash advance total processing duration")
            .register(meterRegistry);
        
        Gauge.builder("cash_advance_total_requests")
            .description("Total cash advance requests")
            .register(meterRegistry, this, value -> totalRequestsCount.get());
            
        Gauge.builder("cash_advance_approved_requests")
            .description("Approved cash advance requests")
            .register(meterRegistry, this, value -> approvedRequestsCount.get());
            
        Gauge.builder("cash_advance_rejected_requests")
            .description("Rejected cash advance requests")
            .register(meterRegistry, this, value -> rejectedRequestsCount.get());
            
        Gauge.builder("cash_advance_under_review_requests")
            .description("Under review cash advance requests")
            .register(meterRegistry, this, value -> underReviewRequestsCount.get());
            
        Gauge.builder("cash_advance_total_amount")
            .description("Total cash advance amount")
            .register(meterRegistry, this, value -> totalAdvanceAmount.get());
    }
    
    @KafkaListener(topics = TOPIC, groupId = CONSUMER_GROUP)
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processCashAdvanceRequested(@Payload String message,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                         @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                         @Header(KafkaHeaders.OFFSET) long offset,
                                         @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
                                         Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        Timer.Sample totalSample = Timer.start(meterRegistry);
        String requestId = UUID.randomUUID().toString();
        
        try {
            MDCUtil.setRequestId(requestId);
            MDC.put("topic", topic);
            MDC.put("partition", String.valueOf(partition));
            MDC.put("offset", String.valueOf(offset));
            
            logger.info("Processing cash advance requested message: topic={}, partition={}, offset={}", 
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
                metricsService.recordCustomMetric("cash_advance_processed", 1.0, 
                    Map.of("eventType", eventType, "requestId", requestId));
                acknowledgment.acknowledge();
                logger.info("Successfully processed cash advance message: eventType={}, requestId={}", 
                    eventType, requestId);
            } else {
                throw new RuntimeException("Failed to process cash advance message: " + eventType);
            }
            
        } catch (Exception e) {
            logger.error("Error processing cash advance message", e);
            messagesFailedCounter.increment();
            
            try {
                dlqService.sendToDlq(DLQ_TOPIC, message, e.getMessage(), requestId);
                acknowledgment.acknowledge();
            } catch (Exception dlqException) {
                logger.error("Failed to send message to DLQ", dlqException);
            }
        } finally {
            sample.stop(messageProcessingTimer);
            totalSample.stop(totalProcessingTimer);
            MDC.clear();
        }
    }
    
    private boolean executeProcessingStep(String eventType, JsonNode messageNode, String requestId) {
        switch (eventType) {
            case "CASH_ADVANCE_REQUESTED":
                return processNewCashAdvanceRequest(messageNode, requestId);
            case "ELIGIBILITY_CHECK_REQUESTED":
                return processEligibilityCheck(messageNode, requestId);
            case "RISK_ASSESSMENT_REQUESTED":
                return processRiskAssessment(messageNode, requestId);
            case "UNDERWRITING_DECISION_REQUESTED":
                return processUnderwritingDecision(messageNode, requestId);
            case "COMPLIANCE_VERIFICATION_REQUESTED":
                return processComplianceVerification(messageNode, requestId);
            case "FUNDING_AUTHORIZATION_REQUESTED":
                return processFundingAuthorization(messageNode, requestId);
            case "ADVANCE_APPROVAL_NOTIFICATION":
                return processApprovalNotification(messageNode, requestId);
            case "ADVANCE_REJECTION_NOTIFICATION":
                return processRejectionNotification(messageNode, requestId);
            case "REPAYMENT_SCHEDULE_GENERATION":
                return processRepaymentScheduleGeneration(messageNode, requestId);
            case "FRAUD_ALERT_PROCESSING":
                return processFraudAlert(messageNode, requestId);
            case "PAYMENT_VERIFICATION":
                return processPaymentVerification(messageNode, requestId);
            case "REGULATORY_REPORTING":
                return processRegulatoryReporting(messageNode, requestId);
            default:
                logger.warn("Unknown event type: {}", eventType);
                return false;
        }
    }
    
    private boolean processNewCashAdvanceRequest(JsonNode messageNode, String requestId) {
        try {
            totalRequestsCount.incrementAndGet();
            
            String customerId = messageNode.path("customerId").asText();
            String requestAmount = messageNode.path("requestAmount").asText();
            String purpose = messageNode.path("purpose").asText();
            String urgencyLevel = messageNode.path("urgencyLevel").asText();
            String employmentVerified = messageNode.path("employmentVerified").asText();
            String bankAccountVerified = messageNode.path("bankAccountVerified").asText();
            JsonNode metadata = messageNode.path("metadata");
            
            BigDecimal amount = new BigDecimal(requestAmount);
            
            if (!validateRequestAmount(amount)) {
                logger.warn("Invalid request amount: {}", requestAmount);
                return false;
            }
            
            CashAdvanceRequest request = CashAdvanceRequest.builder()
                .id(UUID.randomUUID().toString())
                .customerId(customerId)
                .requestAmount(amount)
                .purpose(purpose)
                .urgencyLevel(urgencyLevel)
                .employmentVerified(Boolean.parseBoolean(employmentVerified))
                .bankAccountVerified(Boolean.parseBoolean(bankAccountVerified))
                .status("PENDING_ELIGIBILITY")
                .priorityScore(calculatePriorityScore(urgencyLevel, amount))
                .metadata(metadata.toString())
                .requestedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            activeRequests.put(request.getId(), request);
            
            if ("HIGH".equals(urgencyLevel) || "CRITICAL".equals(urgencyLevel)) {
                priorityQueue.offer(request);
            } else {
                requestQueue.offer(request);
            }
            
            requestsProcessedCounter.increment();
            totalAdvanceAmount.addAndGet(amount.longValue());
            
            logger.info("Processed new cash advance request: id={}, customerId={}, amount={}, urgency={}", 
                request.getId(), customerId, amount, urgencyLevel);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing new cash advance request", e);
            return false;
        }
    }
    
    private boolean processEligibilityCheck(JsonNode messageNode, String requestId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            eligibilityChecksCounter.increment();
            
            String requestingId = messageNode.path("requestId").asText();
            String customerId = messageNode.path("customerId").asText();
            
            CashAdvanceRequest request = activeRequests.get(requestingId);
            if (request == null) {
                request = cashAdvanceService.getRequest(requestingId);
            }
            
            if (request == null) {
                logger.warn("Request not found for eligibility check: {}", requestingId);
                return false;
            }
            
            CashAdvanceEligibility eligibility = eligibilityService.checkEligibility(request);
            eligibilityCache.put(customerId, eligibility);
            
            request.setStatus(eligibility.isEligible() ? "PENDING_RISK_ASSESSMENT" : "REJECTED");
            request.setEligibilityCheckedAt(LocalDateTime.now());
            request.setEligibilityScore(eligibility.getEligibilityScore());
            request.setEligibilityReasons(eligibility.getReasons());
            
            cashAdvanceService.updateRequest(request);
            
            if (!eligibility.isEligible()) {
                rejectedRequestsCount.incrementAndGet();
                requestsRejectedCounter.increment();
            }
            
            logger.info("Processed eligibility check: requestId={}, eligible={}, score={}", 
                requestingId, eligibility.isEligible(), eligibility.getEligibilityScore());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing eligibility check", e);
            return false;
        } finally {
            sample.stop(eligibilityCheckTimer);
        }
    }
    
    private boolean processRiskAssessment(JsonNode messageNode, String requestId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            riskAssessmentsCounter.increment();
            
            String requestingId = messageNode.path("requestId").asText();
            String customerId = messageNode.path("customerId").asText();
            JsonNode creditData = messageNode.path("creditData");
            JsonNode behaviorData = messageNode.path("behaviorData");
            
            CashAdvanceRequest request = activeRequests.get(requestingId);
            if (request == null) {
                request = cashAdvanceService.getRequest(requestingId);
            }
            
            if (request == null || !"PENDING_RISK_ASSESSMENT".equals(request.getStatus())) {
                logger.warn("Invalid request state for risk assessment: {}", requestingId);
                return false;
            }
            
            CashAdvanceRisk riskAssessment = riskAssessmentService.assessRisk(request, creditData, behaviorData);
            riskCache.put(customerId, riskAssessment);
            
            request.setStatus(determineNextStatusAfterRisk(riskAssessment));
            request.setRiskAssessedAt(LocalDateTime.now());
            request.setRiskScore(riskAssessment.getRiskScore());
            request.setRiskLevel(riskAssessment.getRiskLevel());
            request.setRiskFactors(riskAssessment.getRiskFactors());
            
            cashAdvanceService.updateRequest(request);
            
            if ("HIGH".equals(riskAssessment.getRiskLevel()) || "CRITICAL".equals(riskAssessment.getRiskLevel())) {
                fraudAlertsCounter.increment();
                logger.warn("High risk cash advance detected: requestId={}, riskLevel={}, score={}", 
                    requestingId, riskAssessment.getRiskLevel(), riskAssessment.getRiskScore());
            }
            
            logger.info("Processed risk assessment: requestId={}, riskLevel={}, score={}", 
                requestingId, riskAssessment.getRiskLevel(), riskAssessment.getRiskScore());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing risk assessment", e);
            return false;
        } finally {
            sample.stop(riskAssessmentTimer);
        }
    }
    
    private boolean processUnderwritingDecision(JsonNode messageNode, String requestId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            underwritingDecisionsCounter.increment();
            
            String requestingId = messageNode.path("requestId").asText();
            String underwriterAction = messageNode.path("underwriterAction").asText();
            String decisionReason = messageNode.path("decisionReason").asText();
            String approvedAmount = messageNode.path("approvedAmount").asText();
            
            CashAdvanceRequest request = activeRequests.get(requestingId);
            if (request == null) {
                request = cashAdvanceService.getRequest(requestingId);
            }
            
            if (request == null) {
                logger.warn("Request not found for underwriting decision: {}", requestingId);
                return false;
            }
            
            CashAdvanceDecision decision = underwritingService.makeUnderwritingDecision(request, underwriterAction);
            
            request.setStatus(decision.getDecision());
            request.setDecisionMadeAt(LocalDateTime.now());
            request.setDecisionReason(decisionReason);
            request.setUnderwriterAction(underwriterAction);
            
            if ("APPROVED".equals(decision.getDecision()) && approvedAmount != null) {
                BigDecimal approved = new BigDecimal(approvedAmount);
                request.setApprovedAmount(approved);
                approvedRequestsCount.incrementAndGet();
                requestsApprovedCounter.increment();
            } else if ("REJECTED".equals(decision.getDecision())) {
                rejectedRequestsCount.incrementAndGet();
                requestsRejectedCounter.increment();
            } else if ("UNDER_REVIEW".equals(decision.getDecision())) {
                underReviewRequestsCount.incrementAndGet();
                requestsUnderReviewCounter.increment();
            }
            
            decisionQueue.offer(decision);
            cashAdvanceService.updateRequest(request);
            
            logger.info("Processed underwriting decision: requestId={}, decision={}, approvedAmount={}", 
                requestingId, decision.getDecision(), approvedAmount);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing underwriting decision", e);
            return false;
        } finally {
            sample.stop(underwritingDecisionTimer);
        }
    }
    
    private boolean processComplianceVerification(JsonNode messageNode, String requestId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            complianceChecksCounter.increment();
            
            String requestingId = messageNode.path("requestId").asText();
            JsonNode complianceData = messageNode.path("complianceData");
            
            CashAdvanceRequest request = activeRequests.get(requestingId);
            if (request == null) {
                request = cashAdvanceService.getRequest(requestingId);
            }
            
            if (request == null) {
                logger.warn("Request not found for compliance verification: {}", requestingId);
                return false;
            }
            
            CashAdvanceCompliance compliance = complianceService.verifyCompliance(request, complianceData);
            
            request.setComplianceVerifiedAt(LocalDateTime.now());
            request.setComplianceStatus(compliance.getStatus());
            request.setComplianceScore(compliance.getScore());
            request.setComplianceFlags(compliance.getFlags());
            
            if (!compliance.isCompliant()) {
                request.setStatus("REJECTED");
                rejectedRequestsCount.incrementAndGet();
                requestsRejectedCounter.increment();
                logger.warn("Compliance violation detected: requestId={}, flags={}", 
                    requestingId, compliance.getFlags());
            }
            
            cashAdvanceService.updateRequest(request);
            
            logger.info("Processed compliance verification: requestId={}, compliant={}, score={}", 
                requestingId, compliance.isCompliant(), compliance.getScore());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing compliance verification", e);
            return false;
        } finally {
            sample.stop(complianceCheckTimer);
        }
    }
    
    private boolean processFundingAuthorization(JsonNode messageNode, String requestId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            String requestingId = messageNode.path("requestId").asText();
            String bankAccountId = messageNode.path("bankAccountId").asText();
            String fundingSource = messageNode.path("fundingSource").asText();
            
            CashAdvanceRequest request = activeRequests.get(requestingId);
            if (request == null) {
                request = cashAdvanceService.getRequest(requestingId);
            }
            
            if (request == null || !"APPROVED".equals(request.getStatus())) {
                logger.warn("Invalid request state for funding authorization: {}", requestingId);
                return false;
            }
            
            CashAdvanceFunding funding = fundingService.initiateFunding(request, bankAccountId, fundingSource);
            
            request.setStatus("FUNDING_IN_PROGRESS");
            request.setFundingInitiatedAt(LocalDateTime.now());
            request.setFundingMethod(fundingSource);
            request.setDestinationAccountId(bankAccountId);
            request.setFundingReference(funding.getReference());
            
            fundingInitiatedCounter.increment();
            cashAdvanceService.updateRequest(request);
            
            logger.info("Processed funding authorization: requestId={}, amount={}, reference={}", 
                requestingId, request.getApprovedAmount(), funding.getReference());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing funding authorization", e);
            return false;
        } finally {
            sample.stop(fundingProcessingTimer);
        }
    }
    
    private boolean processApprovalNotification(JsonNode messageNode, String requestId) {
        try {
            String requestingId = messageNode.path("requestId").asText();
            String customerId = messageNode.path("customerId").asText();
            JsonNode notificationChannels = messageNode.path("notificationChannels");
            
            CashAdvanceRequest request = activeRequests.get(requestingId);
            if (request == null) {
                request = cashAdvanceService.getRequest(requestingId);
            }
            
            if (request == null) {
                logger.warn("Request not found for approval notification: {}", requestingId);
                return false;
            }
            
            CashAdvanceNotification notification = CashAdvanceNotification.builder()
                .requestId(requestingId)
                .customerId(customerId)
                .notificationType("APPROVAL")
                .channels(extractStringList(notificationChannels))
                .template("cash_advance_approval")
                .data(Map.of(
                    "approvedAmount", request.getApprovedAmount().toString(),
                    "processingFee", calculateProcessingFee(request.getApprovedAmount()).toString(),
                    "repaymentDate", calculateRepaymentDate(request).toString()
                ))
                .build();
            
            notificationService.sendNotification(notification);
            
            logger.info("Processed approval notification: requestId={}, customerId={}, channels={}", 
                requestingId, customerId, notification.getChannels());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing approval notification", e);
            return false;
        }
    }
    
    private boolean processRejectionNotification(JsonNode messageNode, String requestId) {
        try {
            String requestingId = messageNode.path("requestId").asText();
            String customerId = messageNode.path("customerId").asText();
            String rejectionReason = messageNode.path("rejectionReason").asText();
            JsonNode notificationChannels = messageNode.path("notificationChannels");
            
            CashAdvanceNotification notification = CashAdvanceNotification.builder()
                .requestId(requestingId)
                .customerId(customerId)
                .notificationType("REJECTION")
                .channels(extractStringList(notificationChannels))
                .template("cash_advance_rejection")
                .data(Map.of(
                    "rejectionReason", rejectionReason,
                    "appealProcess", "Available within 30 days"
                ))
                .build();
            
            notificationService.sendNotification(notification);
            
            logger.info("Processed rejection notification: requestId={}, customerId={}, reason={}", 
                requestingId, customerId, rejectionReason);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing rejection notification", e);
            return false;
        }
    }
    
    private boolean processRepaymentScheduleGeneration(JsonNode messageNode, String requestId) {
        try {
            String requestingId = messageNode.path("requestId").asText();
            String repaymentMethod = messageNode.path("repaymentMethod").asText();
            
            CashAdvanceRequest request = activeRequests.get(requestingId);
            if (request == null) {
                request = cashAdvanceService.getRequest(requestingId);
            }
            
            if (request == null || request.getApprovedAmount() == null) {
                logger.warn("Invalid request for repayment schedule generation: {}", requestingId);
                return false;
            }
            
            CashAdvanceRepaymentSchedule schedule = generateRepaymentSchedule(request, repaymentMethod);
            cashAdvanceService.saveRepaymentSchedule(schedule);
            
            request.setRepaymentScheduleGenerated(true);
            request.setRepaymentMethod(repaymentMethod);
            cashAdvanceService.updateRequest(request);
            
            logger.info("Generated repayment schedule: requestId={}, totalAmount={}, dueDate={}", 
                requestingId, schedule.getTotalAmount(), schedule.getDueDate());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error generating repayment schedule", e);
            return false;
        }
    }
    
    private boolean processFraudAlert(JsonNode messageNode, String requestId) {
        try {
            fraudAlertsCounter.increment();
            
            String requestingId = messageNode.path("requestId").asText();
            String alertType = messageNode.path("alertType").asText();
            String alertSeverity = messageNode.path("alertSeverity").asText();
            JsonNode fraudIndicators = messageNode.path("fraudIndicators");
            
            CashAdvanceRequest request = activeRequests.get(requestingId);
            if (request != null) {
                request.setStatus("FRAUD_REVIEW");
                request.setFraudAlerted(true);
                request.setFraudAlertType(alertType);
                request.setFraudAlertSeverity(alertSeverity);
                request.setFraudIndicators(extractStringList(fraudIndicators));
                
                cashAdvanceService.updateRequest(request);
            }
            
            logger.warn("Processed fraud alert: requestId={}, type={}, severity={}, indicators={}", 
                requestingId, alertType, alertSeverity, fraudIndicators.size());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing fraud alert", e);
            return false;
        }
    }
    
    private boolean processPaymentVerification(JsonNode messageNode, String requestId) {
        try {
            String requestingId = messageNode.path("requestId").asText();
            String paymentReference = messageNode.path("paymentReference").asText();
            String verificationStatus = messageNode.path("verificationStatus").asText();
            
            CashAdvanceRequest request = activeRequests.get(requestingId);
            if (request != null) {
                request.setPaymentVerified("VERIFIED".equals(verificationStatus));
                request.setPaymentReference(paymentReference);
                request.setPaymentVerifiedAt(LocalDateTime.now());
                
                if ("VERIFIED".equals(verificationStatus)) {
                    request.setStatus("FUNDED");
                } else {
                    request.setStatus("PAYMENT_FAILED");
                }
                
                cashAdvanceService.updateRequest(request);
            }
            
            logger.info("Processed payment verification: requestId={}, reference={}, status={}", 
                requestingId, paymentReference, verificationStatus);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing payment verification", e);
            return false;
        }
    }
    
    private boolean processRegulatoryReporting(JsonNode messageNode, String requestId) {
        try {
            String reportType = messageNode.path("reportType").asText();
            String reportingPeriod = messageNode.path("reportingPeriod").asText();
            JsonNode requestIds = messageNode.path("requestIds");
            
            CashAdvanceRegulatoryReport report = complianceService.generateRegulatoryReport(
                reportType, reportingPeriod, extractStringList(requestIds));
            
            logger.info("Generated regulatory report: type={}, period={}, requestCount={}", 
                reportType, reportingPeriod, report.getRequestCount());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing regulatory reporting", e);
            return false;
        }
    }
    
    private void startRequestProcessor() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                List<CashAdvanceRequest> requests = new ArrayList<>();
                requestQueue.drainTo(requests, batchSize);
                
                if (!requests.isEmpty()) {
                    CashAdvanceBatch batch = CashAdvanceBatch.builder()
                        .id(UUID.randomUUID().toString())
                        .requests(requests)
                        .status("PENDING")
                        .createdAt(LocalDateTime.now())
                        .build();
                    
                    batchQueue.offer(batch);
                }
            } catch (Exception e) {
                logger.error("Error in request processor", e);
            }
        }, 0, 5, TimeUnit.SECONDS);
    }
    
    private void startBatchProcessor() {
        batchExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    CashAdvanceBatch batch = batchQueue.take();
                    processBatch(batch);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in batch processor", e);
                }
            }
        });
    }
    
    private void processBatch(CashAdvanceBatch batch) {
        try {
            batch.setStatus("PROCESSING");
            batch.setProcessingStarted(LocalDateTime.now());
            
            for (CashAdvanceRequest request : batch.getRequests()) {
                try {
                    cashAdvanceService.processRequest(request);
                } catch (Exception e) {
                    logger.error("Error processing request in batch: {}", request.getId(), e);
                }
            }
            
            batch.setStatus("COMPLETED");
            batch.setProcessingCompleted(LocalDateTime.now());
            
        } catch (Exception e) {
            logger.error("Error processing batch: {}", batch.getId(), e);
            batch.setStatus("FAILED");
            batch.setProcessingCompleted(LocalDateTime.now());
        }
    }
    
    private void startDecisionProcessor() {
        batchExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    CashAdvanceDecision decision = decisionQueue.take();
                    processDecision(decision);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in decision processor", e);
                }
            }
        });
    }
    
    private void processDecision(CashAdvanceDecision decision) {
        try {
            cashAdvanceService.executeDecision(decision);
            logger.info("Executed decision: requestId={}, decision={}", 
                decision.getRequestId(), decision.getDecision());
        } catch (Exception e) {
            logger.error("Error executing decision: {}", decision.getRequestId(), e);
        }
    }
    
    private void startPriorityProcessor() {
        priorityExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    CashAdvanceRequest priorityRequest = priorityQueue.take();
                    cashAdvanceService.processPriorityRequest(priorityRequest);
                    logger.info("Processed priority request: id={}, urgency={}", 
                        priorityRequest.getId(), priorityRequest.getUrgencyLevel());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in priority processor", e);
                }
            }
        });
    }
    
    private void startRateLimitReset() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            currentGlobalRate.set(0);
            logger.debug("Reset rate limits");
        }, 1, 1, TimeUnit.MINUTES);
    }
    
    private boolean isWithinRateLimit() {
        return currentGlobalRate.incrementAndGet() <= globalRateLimit;
    }
    
    private boolean validateRequestAmount(BigDecimal amount) {
        return amount.compareTo(minCashAdvanceAmount) >= 0 && 
               amount.compareTo(maxCashAdvanceAmount) <= 0;
    }
    
    private int calculatePriorityScore(String urgencyLevel, BigDecimal amount) {
        int baseScore = 0;
        switch (urgencyLevel.toUpperCase()) {
            case "CRITICAL": baseScore = 100; break;
            case "HIGH": baseScore = 75; break;
            case "MEDIUM": baseScore = 50; break;
            case "LOW": baseScore = 25; break;
            default: baseScore = 10; break;
        }
        
        if (amount.compareTo(new BigDecimal("1000")) >= 0) {
            baseScore += 10;
        }
        
        return baseScore;
    }
    
    private String determineNextStatusAfterRisk(CashAdvanceRisk riskAssessment) {
        switch (riskAssessment.getRiskLevel().toUpperCase()) {
            case "LOW":
            case "MEDIUM":
                return "PENDING_UNDERWRITING";
            case "HIGH":
                return "UNDER_REVIEW";
            case "CRITICAL":
                return "REJECTED";
            default:
                return "UNDER_REVIEW";
        }
    }
    
    private BigDecimal calculateProcessingFee(BigDecimal amount) {
        return amount.multiply(processingFeeRate).setScale(2, RoundingMode.HALF_UP);
    }
    
    private LocalDateTime calculateRepaymentDate(CashAdvanceRequest request) {
        return request.getRequestedAt().plusDays(maxTermDays);
    }
    
    private CashAdvanceRepaymentSchedule generateRepaymentSchedule(CashAdvanceRequest request, String repaymentMethod) {
        BigDecimal principal = request.getApprovedAmount();
        BigDecimal processingFee = calculateProcessingFee(principal);
        BigDecimal interest = principal.multiply(aprRate).divide(new BigDecimal("365"), 4, RoundingMode.HALF_UP)
                                    .multiply(new BigDecimal(maxTermDays));
        BigDecimal totalAmount = principal.add(processingFee).add(interest);
        
        return CashAdvanceRepaymentSchedule.builder()
            .requestId(request.getId())
            .customerId(request.getCustomerId())
            .principalAmount(principal)
            .processingFee(processingFee)
            .interestAmount(interest)
            .totalAmount(totalAmount)
            .dueDate(calculateRepaymentDate(request))
            .repaymentMethod(repaymentMethod)
            .status("SCHEDULED")
            .createdAt(LocalDateTime.now())
            .build();
    }
    
    private List<String> extractStringList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            arrayNode.forEach(node -> list.add(node.asText()));
        }
        return list;
    }
}