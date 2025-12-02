package com.waqiti.lending.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.compliance.ComplianceEngine;
import com.waqiti.common.fraud.FraudDetectionEngine;
import com.waqiti.common.security.AuditLogger;
import com.waqiti.lending.model.LoanApplication;
import com.waqiti.lending.model.CreditDecision;
import com.waqiti.lending.service.LoanOriginationService;
import com.waqiti.lending.service.CreditScoringService;
import com.waqiti.lending.service.UnderwritingService;
import com.waqiti.lending.service.NotificationService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Consumer #156: Loan Origination Event Consumer
 * Handles loan application processing with credit scoring, underwriting, and regulatory compliance
 * Implements zero-tolerance 12-step processing pattern with SERIALIZABLE transaction isolation
 */
@Component
public class LoanOriginationEventConsumer {
    private static final Logger logger = LoggerFactory.getLogger(LoanOriginationEventConsumer.class);
    
    private static final String TOPIC = "loan-origination";
    private static final String DLQ_TOPIC = "loan-origination-dlq";
    private static final String CONSUMER_GROUP = "loan-origination-consumer-group";
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private LoanOriginationService loanOriginationService;
    
    @Autowired
    private CreditScoringService creditScoringService;
    
    @Autowired
    private UnderwritingService underwritingService;
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private ComplianceEngine complianceEngine;
    
    @Autowired
    private FraudDetectionEngine fraudDetectionEngine;
    
    @Autowired
    private AuditLogger auditLogger;
    
    @Value("${lending.loan.max-amount:1000000}")
    private BigDecimal maxLoanAmount;
    
    @Value("${lending.loan.min-credit-score:600}")
    private int minCreditScore;
    
    @Value("${lending.loan.max-dti-ratio:0.43}")
    private double maxDebtToIncomeRatio;
    
    @Value("${lending.loan.processing.timeout:120}")
    private int processingTimeoutSeconds;
    
    private Counter applicationsProcessedCounter;
    private Counter applicationsApprovedCounter;
    private Counter applicationsDeclinedCounter;
    private Counter fraudDetectedCounter;
    private Counter complianceViolationsCounter;
    private Counter errorCounter;
    private Timer processingTimer;
    private Timer underwritingTimer;
    private Timer creditScoringTimer;
    
    private CircuitBreaker circuitBreaker;
    private Retry retry;
    
    private final Map<String, LoanOriginationState> originationStates = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        initializeMetrics();
        initializeResilience();
        logger.info("LoanOriginationEventConsumer initialized with comprehensive underwriting capabilities");
    }
    
    private void initializeMetrics() {
        applicationsProcessedCounter = Counter.builder("loan.applications.processed")
                .description("Total number of loan applications processed")
                .register(meterRegistry);
                
        applicationsApprovedCounter = Counter.builder("loan.applications.approved")
                .description("Total number of loan applications approved")
                .register(meterRegistry);
                
        applicationsDeclinedCounter = Counter.builder("loan.applications.declined")
                .description("Total number of loan applications declined")
                .register(meterRegistry);
                
        fraudDetectedCounter = Counter.builder("loan.applications.fraud.detected")
                .description("Number of fraudulent loan applications detected")
                .register(meterRegistry);
                
        complianceViolationsCounter = Counter.builder("loan.applications.compliance.violations")
                .description("Number of compliance violations in loan applications")
                .register(meterRegistry);
                
        errorCounter = Counter.builder("loan.applications.errors")
                .description("Number of loan application processing errors")
                .register(meterRegistry);
                
        processingTimer = Timer.builder("loan.applications.processing.time")
                .description("Time taken to process loan applications")
                .register(meterRegistry);
                
        underwritingTimer = Timer.builder("loan.underwriting.time")
                .description("Time taken for loan underwriting")
                .register(meterRegistry);
                
        creditScoringTimer = Timer.builder("loan.credit.scoring.time")
                .description("Time taken for credit scoring")
                .register(meterRegistry);
    }
    
    private void initializeResilience() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(100)
                .permittedNumberOfCallsInHalfOpenState(10)
                .build();
                
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("loan-origination-processor");
        
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(2))
                .retryExceptions(Exception.class)
                .build();
                
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        retry = retryRegistry.retry("loan-origination-retry");
    }
    
    @KafkaListener(topics = TOPIC, groupId = CONSUMER_GROUP)
    @Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRED, timeout = 120)
    public void processLoanOriginationEvent(@Payload String message,
                                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                          @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                          @Header(KafkaHeaders.OFFSET) long offset,
                                          @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
                                          Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = UUID.randomUUID().toString();
        
        // Step 1: Initialize MDC and logging context
        MDC.put("correlation.id", correlationId);
        MDC.put("topic", topic);
        MDC.put("partition", String.valueOf(partition));
        MDC.put("offset", String.valueOf(offset));
        MDC.put("processor", "LoanOriginationEventConsumer");
        
        try {
            logger.info("Step 1: Processing loan origination event - correlation: {}", correlationId);
            
            // Step 2: Parse and validate message structure
            Map<String, Object> eventData = parseAndValidateMessage(message);
            if (eventData == null) {
                sendToDlq(message, "Invalid message structure", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            String applicationId = (String) eventData.get("applicationId");
            String customerId = (String) eventData.get("customerId");
            String loanType = (String) eventData.get("loanType");
            
            MDC.put("application.id", applicationId);
            MDC.put("customer.id", customerId);
            MDC.put("loan.type", loanType);
            
            logger.info("Step 2: Message validated - Application ID: {}, Loan Type: {}", applicationId, loanType);
            
            // Step 3: Fraud detection and identity verification
            if (!performFraudDetectionAndVerification(eventData, correlationId)) {
                logger.warn("Step 3: Fraud detected in loan application: {}", applicationId);
                fraudDetectedCounter.increment();
                sendToDlq(message, "Fraud detected", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            logger.info("Step 3: Fraud detection and verification passed");
            
            // Step 4: Regulatory compliance validation
            if (!performRegulatoryComplianceValidation(eventData, correlationId)) {
                logger.warn("Step 4: Regulatory compliance violation in loan application: {}", applicationId);
                complianceViolationsCounter.increment();
                sendToDlq(message, "Regulatory compliance violation", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            logger.info("Step 4: Regulatory compliance validation passed");
            
            // Step 5: Credit scoring and risk assessment
            if (!performCreditScoringAndRiskAssessment(eventData, correlationId)) {
                logger.warn("Step 5: Credit scoring failed for application: {}", applicationId);
                sendToDlq(message, "Credit scoring failed", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            logger.info("Step 5: Credit scoring and risk assessment completed");
            
            // Step 6: Income and employment verification
            if (!performIncomeAndEmploymentVerification(eventData, correlationId)) {
                logger.warn("Step 6: Income verification failed for application: {}", applicationId);
                sendToDlq(message, "Income verification failed", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            logger.info("Step 6: Income and employment verification completed");
            
            // Step 7: Automated underwriting decision
            Supplier<Boolean> processor = () -> performAutomatedUnderwriting(eventData, correlationId);
            Boolean result = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(circuitBreaker, processor)).get();
            
            if (!result) {
                logger.error("Step 7: Automated underwriting failed for application: {}", applicationId);
                sendToDlq(message, "Underwriting processing failed", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            logger.info("Step 7: Automated underwriting completed");
            
            // Step 8: Loan terms and pricing determination
            determineLoanTermsAndPricing(eventData, correlationId);
            logger.info("Step 8: Loan terms and pricing determined");
            
            // Step 9: Final credit decision
            makeFinalCreditDecision(eventData, correlationId);
            logger.info("Step 9: Final credit decision made");
            
            // Step 10: Regulatory reporting and documentation
            generateRegulatoryReports(eventData, correlationId);
            logger.info("Step 10: Regulatory reports generated");
            
            // Step 11: Customer notification and disclosure
            sendCustomerNotifications(eventData, correlationId);
            logger.info("Step 11: Customer notifications sent");
            
            // Step 12: Audit trail and final acknowledgment
            createAuditTrail(eventData, correlationId);
            applicationsProcessedCounter.increment();
            
            LoanOriginationState state = originationStates.get(applicationId);
            if (state != null && "APPROVED".equals(state.getDecision())) {
                applicationsApprovedCounter.increment();
            } else {
                applicationsDeclinedCounter.increment();
            }
            
            acknowledgment.acknowledge();
            logger.info("Step 12: Loan origination processing completed for: {}", applicationId);
            
        } catch (Exception e) {
            logger.error("Error processing loan origination event", e);
            errorCounter.increment();
            sendToDlq(message, e.getMessage(), correlationId);
            acknowledgment.acknowledge();
        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }
    
    private Map<String, Object> parseAndValidateMessage(String message) {
        try {
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            // Validate required fields
            List<String> requiredFields = Arrays.asList(
                "applicationId", "customerId", "loanType", "requestedAmount", "purpose",
                "applicantIncome", "employmentStatus", "creditConsent", "applicationDate"
            );
            
            for (String field : requiredFields) {
                if (!eventData.containsKey(field) || eventData.get(field) == null) {
                    logger.error("Missing required field: {}", field);
                    return null;
                }
            }
            
            // Validate loan amount
            Object amountObj = eventData.get("requestedAmount");
            if (!(amountObj instanceof Number)) {
                logger.error("Invalid requested amount: {}", amountObj);
                return null;
            }
            
            BigDecimal amount = new BigDecimal(amountObj.toString());
            if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(maxLoanAmount) > 0) {
                logger.error("Loan amount out of range: {}", amount);
                return null;
            }
            
            // Validate credit consent
            Boolean creditConsent = (Boolean) eventData.get("creditConsent");
            if (!creditConsent) {
                logger.error("Credit consent not provided");
                return null;
            }
            
            // Validate loan type
            String loanType = (String) eventData.get("loanType");
            if (!isValidLoanType(loanType)) {
                logger.error("Invalid loan type: {}", loanType);
                return null;
            }
            
            return eventData;
        } catch (Exception e) {
            logger.error("Failed to parse message", e);
            return null;
        }
    }
    
    private boolean performFraudDetectionAndVerification(Map<String, Object> eventData, String correlationId) {
        try {
            String applicationId = (String) eventData.get("applicationId");
            String customerId = (String) eventData.get("customerId");
            BigDecimal requestedAmount = new BigDecimal(eventData.get("requestedAmount").toString());
            
            // Check for synthetic identity fraud
            if (fraudDetectionEngine.detectSyntheticIdentity(customerId, eventData, correlationId)) {
                logger.warn("Synthetic identity detected for application: {}", applicationId);
                auditLogger.logSecurityEvent("SYNTHETIC_IDENTITY_DETECTED", correlationId,
                    Map.of("applicationId", applicationId, "customerId", customerId));
                return false;
            }
            
            // Check for identity theft indicators
            if (fraudDetectionEngine.detectIdentityTheft(customerId, eventData, correlationId)) {
                logger.warn("Identity theft indicators detected for application: {}", applicationId);
                auditLogger.logSecurityEvent("IDENTITY_THEFT_INDICATORS", correlationId,
                    Map.of("applicationId", applicationId, "customerId", customerId));
                return false;
            }
            
            // Verify identity documents
            if (eventData.containsKey("identityDocuments")) {
                if (!fraudDetectionEngine.verifyIdentityDocuments(eventData.get("identityDocuments"), correlationId)) {
                    logger.warn("Identity document verification failed for application: {}", applicationId);
                    auditLogger.logSecurityEvent("IDENTITY_DOCUMENT_VERIFICATION_FAILED", correlationId,
                        Map.of("applicationId", applicationId, "customerId", customerId));
                    return false;
                }
            }
            
            // Check for income fraud
            if (fraudDetectionEngine.detectIncomeFraud(customerId, eventData, correlationId)) {
                logger.warn("Income fraud detected for application: {}", applicationId);
                auditLogger.logSecurityEvent("INCOME_FRAUD_DETECTED", correlationId,
                    Map.of("applicationId", applicationId, "customerId", customerId));
                return false;
            }
            
            // Check for application velocity fraud
            if (fraudDetectionEngine.detectApplicationVelocityFraud(customerId, correlationId)) {
                logger.warn("Application velocity fraud detected for customer: {}", customerId);
                auditLogger.logSecurityEvent("APPLICATION_VELOCITY_FRAUD", correlationId,
                    Map.of("applicationId", applicationId, "customerId", customerId));
                return false;
            }
            
            return true;
        } catch (Exception e) {
            logger.error("Fraud detection and verification failed", e);
            return false;
        }
    }
    
    private boolean performRegulatoryComplianceValidation(Map<String, Object> eventData, String correlationId) {
        try {
            String customerId = (String) eventData.get("customerId");
            String applicationId = (String) eventData.get("applicationId");
            BigDecimal requestedAmount = new BigDecimal(eventData.get("requestedAmount").toString());
            String loanType = (String) eventData.get("loanType");
            
            // Truth in Lending Act (TILA) compliance
            if (!complianceEngine.validateTILACompliance(customerId, requestedAmount, loanType, correlationId)) {
                logger.warn("TILA compliance violation for application: {}", applicationId);
                auditLogger.logComplianceViolation("TILA_VIOLATION", correlationId,
                    Map.of("customerId", customerId, "applicationId", applicationId, "loanType", loanType));
                return false;
            }
            
            // Equal Credit Opportunity Act (ECOA) compliance
            if (!complianceEngine.validateECOACompliance(customerId, eventData, correlationId)) {
                logger.warn("ECOA compliance violation for application: {}", applicationId);
                auditLogger.logComplianceViolation("ECOA_VIOLATION", correlationId,
                    Map.of("customerId", customerId, "applicationId", applicationId));
                return false;
            }
            
            // Fair Credit Reporting Act (FCRA) compliance
            if (!complianceEngine.validateFCRACompliance(customerId, correlationId)) {
                logger.warn("FCRA compliance violation for application: {}", applicationId);
                auditLogger.logComplianceViolation("FCRA_VIOLATION", correlationId,
                    Map.of("customerId", customerId, "applicationId", applicationId));
                return false;
            }
            
            // OFAC sanctions screening
            if (!complianceEngine.performOFACScreening(customerId, correlationId)) {
                logger.warn("OFAC sanctions match for customer: {}", customerId);
                auditLogger.logComplianceViolation("OFAC_SANCTIONS_MATCH", correlationId,
                    Map.of("customerId", customerId, "screeningType", "LOAN_APPLICATION"));
                return false;
            }
            
            // State licensing compliance
            if (eventData.containsKey("customerState")) {
                String customerState = (String) eventData.get("customerState");
                if (!complianceEngine.validateStateLicensingCompliance(customerState, loanType, correlationId)) {
                    logger.warn("State licensing compliance violation for application: {}", applicationId);
                    auditLogger.logComplianceViolation("STATE_LICENSING_VIOLATION", correlationId,
                        Map.of("customerId", customerId, "applicationId", applicationId, "state", customerState));
                    return false;
                }
            }
            
            return true;
        } catch (Exception e) {
            logger.error("Regulatory compliance validation failed", e);
            return false;
        }
    }
    
    private boolean performCreditScoringAndRiskAssessment(Map<String, Object> eventData, String correlationId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String applicationId = (String) eventData.get("applicationId");
            String customerId = (String) eventData.get("customerId");
            BigDecimal requestedAmount = new BigDecimal(eventData.get("requestedAmount").toString());
            
            // Pull credit report
            Map<String, Object> creditReport = creditScoringService.pullCreditReport(customerId, correlationId);
            if (creditReport == null) {
                logger.warn("Failed to pull credit report for customer: {}", customerId);
                return false;
            }
            
            // Calculate credit score
            int creditScore = creditScoringService.calculateCreditScore(creditReport, correlationId);
            if (creditScore < minCreditScore) {
                logger.warn("Credit score below minimum for application: {} - score: {}", applicationId, creditScore);
                auditLogger.logBusinessEvent("CREDIT_SCORE_BELOW_MINIMUM", correlationId,
                    Map.of("applicationId", applicationId, "creditScore", creditScore, "minimum", minCreditScore));
                return false;
            }
            
            // Calculate debt-to-income ratio
            BigDecimal monthlyIncome = new BigDecimal(eventData.get("applicantIncome").toString()).divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
            BigDecimal monthlyDebt = creditScoringService.calculateMonthlyDebtPayments(creditReport, correlationId);
            double dtiRatio = monthlyDebt.divide(monthlyIncome, 4, RoundingMode.HALF_UP).doubleValue();
            
            if (dtiRatio > maxDebtToIncomeRatio) {
                logger.warn("DTI ratio exceeds maximum for application: {} - ratio: {}", applicationId, dtiRatio);
                auditLogger.logBusinessEvent("DTI_RATIO_EXCEEDED", correlationId,
                    Map.of("applicationId", applicationId, "dtiRatio", dtiRatio, "maximum", maxDebtToIncomeRatio));
                return false;
            }
            
            // Assess credit risk
            Map<String, Object> riskAssessment = creditScoringService.assessCreditRisk(
                customerId, requestedAmount, creditReport, eventData, correlationId);
            
            String riskRating = (String) riskAssessment.get("riskRating");
            if ("HIGH".equals(riskRating)) {
                logger.warn("High credit risk for application: {}", applicationId);
                auditLogger.logBusinessEvent("HIGH_CREDIT_RISK", correlationId,
                    Map.of("applicationId", applicationId, "riskRating", riskRating));
                return false;
            }
            
            // Store scoring results
            eventData.put("creditScore", creditScore);
            eventData.put("dtiRatio", dtiRatio);
            eventData.put("riskAssessment", riskAssessment);
            eventData.put("creditReport", creditReport);
            
            logger.info("Credit scoring completed for application: {} - score: {}, DTI: {}, risk: {}", 
                applicationId, creditScore, dtiRatio, riskRating);
            return true;
            
        } catch (Exception e) {
            logger.error("Credit scoring and risk assessment failed", e);
            return false;
        } finally {
            sample.stop(creditScoringTimer);
        }
    }
    
    private boolean performIncomeAndEmploymentVerification(Map<String, Object> eventData, String correlationId) {
        try {
            String applicationId = (String) eventData.get("applicationId");
            String customerId = (String) eventData.get("customerId");
            BigDecimal reportedIncome = new BigDecimal(eventData.get("applicantIncome").toString());
            String employmentStatus = (String) eventData.get("employmentStatus");
            
            // Verify employment status
            if (!loanOriginationService.verifyEmploymentStatus(customerId, employmentStatus, correlationId)) {
                logger.warn("Employment verification failed for application: {}", applicationId);
                auditLogger.logBusinessEvent("EMPLOYMENT_VERIFICATION_FAILED", correlationId,
                    Map.of("applicationId", applicationId, "customerId", customerId));
                return false;
            }
            
            // Verify income through third-party services
            BigDecimal verifiedIncome = loanOriginationService.verifyIncome(customerId, correlationId);
            if (verifiedIncome == null) {
                logger.warn("Income verification failed for application: {}", applicationId);
                auditLogger.logBusinessEvent("INCOME_VERIFICATION_FAILED", correlationId,
                    Map.of("applicationId", applicationId, "customerId", customerId));
                return false;
            }
            
            // Check income discrepancy
            BigDecimal discrepancy = reportedIncome.subtract(verifiedIncome).abs();
            BigDecimal discrepancyPercentage = discrepancy.divide(verifiedIncome, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            
            if (discrepancyPercentage.compareTo(new BigDecimal("20")) > 0) {
                logger.warn("Income discrepancy exceeds threshold for application: {} - discrepancy: {}%", 
                    applicationId, discrepancyPercentage);
                auditLogger.logBusinessEvent("INCOME_DISCREPANCY_EXCEEDED", correlationId,
                    Map.of("applicationId", applicationId, "reportedIncome", reportedIncome.toString(),
                           "verifiedIncome", verifiedIncome.toString(), "discrepancyPercentage", discrepancyPercentage.toString()));
                return false;
            }
            
            // Validate employment history
            if (eventData.containsKey("employmentHistory")) {
                if (!loanOriginationService.validateEmploymentHistory(eventData.get("employmentHistory"), correlationId)) {
                    logger.warn("Employment history validation failed for application: {}", applicationId);
                    auditLogger.logBusinessEvent("EMPLOYMENT_HISTORY_VALIDATION_FAILED", correlationId,
                        Map.of("applicationId", applicationId, "customerId", customerId));
                    return false;
                }
            }
            
            // Store verification results
            eventData.put("verifiedIncome", verifiedIncome);
            eventData.put("incomeVerified", true);
            eventData.put("employmentVerified", true);
            
            logger.info("Income and employment verification completed for application: {}", applicationId);
            return true;
            
        } catch (Exception e) {
            logger.error("Income and employment verification failed", e);
            return false;
        }
    }
    
    private boolean performAutomatedUnderwriting(Map<String, Object> eventData, String correlationId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String applicationId = (String) eventData.get("applicationId");
            String customerId = (String) eventData.get("customerId");
            
            logger.info("Performing automated underwriting for application: {}", applicationId);
            
            // Create loan application record
            LoanApplication application = new LoanApplication();
            application.setApplicationId(applicationId);
            application.setCustomerId(customerId);
            application.setLoanType((String) eventData.get("loanType"));
            application.setRequestedAmount(new BigDecimal(eventData.get("requestedAmount").toString()));
            application.setPurpose((String) eventData.get("purpose"));
            application.setApplicantIncome(new BigDecimal(eventData.get("applicantIncome").toString()));
            application.setEmploymentStatus((String) eventData.get("employmentStatus"));
            application.setApplicationDate(LocalDateTime.now());
            application.setStatus("UNDERWRITING");
            application.setCorrelationId(correlationId);
            
            // Add verification results
            if (eventData.containsKey("creditScore")) {
                application.setCreditScore((Integer) eventData.get("creditScore"));
            }
            if (eventData.containsKey("dtiRatio")) {
                application.setDebtToIncomeRatio((Double) eventData.get("dtiRatio"));
            }
            if (eventData.containsKey("verifiedIncome")) {
                application.setVerifiedIncome((BigDecimal) eventData.get("verifiedIncome"));
            }
            
            // Save application
            loanOriginationService.saveLoanApplication(application);
            
            // Perform automated underwriting
            Map<String, Object> underwritingResult = underwritingService.performAutomatedUnderwriting(
                application, eventData, correlationId);
            
            String decision = (String) underwritingResult.get("decision");
            String decisionReason = (String) underwritingResult.get("reason");
            
            // Create underwriting state
            LoanOriginationState state = new LoanOriginationState();
            state.setApplicationId(applicationId);
            state.setStatus("UNDERWRITTEN");
            state.setDecision(decision);
            state.setDecisionReason(decisionReason);
            state.setProcessedAt(Instant.now());
            state.setCorrelationId(correlationId);
            originationStates.put(applicationId, state);
            
            // Store underwriting results
            eventData.put("underwritingResult", underwritingResult);
            
            logger.info("Automated underwriting completed for application: {} - decision: {}", 
                applicationId, decision);
            return true;
            
        } catch (Exception e) {
            logger.error("Automated underwriting failed", e);
            return false;
        } finally {
            sample.stop(underwritingTimer);
        }
    }
    
    private void determineLoanTermsAndPricing(Map<String, Object> eventData, String correlationId) {
        try {
            String applicationId = (String) eventData.get("applicationId");
            LoanOriginationState state = originationStates.get(applicationId);
            
            if (state != null && "APPROVED".equals(state.getDecision())) {
                BigDecimal loanAmount = new BigDecimal(eventData.get("requestedAmount").toString());
                String loanType = (String) eventData.get("loanType");
                Integer creditScore = (Integer) eventData.get("creditScore");
                Map<String, Object> riskAssessment = (Map<String, Object>) eventData.get("riskAssessment");
                
                // Determine loan terms
                Map<String, Object> loanTerms = loanOriginationService.determineLoanTerms(
                    loanAmount, loanType, creditScore, riskAssessment, correlationId);
                
                // Calculate pricing
                Map<String, Object> pricing = loanOriginationService.calculateLoanPricing(
                    loanAmount, loanTerms, creditScore, riskAssessment, correlationId);
                
                // Store terms and pricing
                state.setLoanTerms(loanTerms);
                state.setPricing(pricing);
                eventData.put("loanTerms", loanTerms);
                eventData.put("pricing", pricing);
                
                logger.info("Loan terms and pricing determined for application: {}", applicationId);
            }
            
        } catch (Exception e) {
            logger.error("Failed to determine loan terms and pricing", e);
            throw new RuntimeException("Loan terms determination failed", e);
        }
    }
    
    private void makeFinalCreditDecision(Map<String, Object> eventData, String correlationId) {
        try {
            String applicationId = (String) eventData.get("applicationId");
            String customerId = (String) eventData.get("customerId");
            
            LoanOriginationState state = originationStates.get(applicationId);
            if (state != null) {
                // Create credit decision record
                CreditDecision decision = new CreditDecision();
                decision.setApplicationId(applicationId);
                decision.setCustomerId(customerId);
                decision.setDecision(state.getDecision());
                decision.setDecisionReason(state.getDecisionReason());
                decision.setDecisionDate(LocalDateTime.now());
                decision.setCorrelationId(correlationId);
                
                if ("APPROVED".equals(state.getDecision())) {
                    decision.setApprovedAmount(new BigDecimal(eventData.get("requestedAmount").toString()));
                    decision.setLoanTerms(state.getLoanTerms());
                    decision.setPricing(state.getPricing());
                }
                
                // Save decision
                loanOriginationService.saveCreditDecision(decision);
                
                // Update application status
                loanOriginationService.updateApplicationStatus(applicationId, state.getDecision(), correlationId);
                
                logger.info("Final credit decision made for application: {} - decision: {}", 
                    applicationId, state.getDecision());
            }
            
        } catch (Exception e) {
            logger.error("Failed to make final credit decision", e);
            throw new RuntimeException("Credit decision failed", e);
        }
    }
    
    private void generateRegulatoryReports(Map<String, Object> eventData, String correlationId) {
        try {
            String applicationId = (String) eventData.get("applicationId");
            String customerId = (String) eventData.get("customerId");
            String loanType = (String) eventData.get("loanType");
            BigDecimal requestedAmount = new BigDecimal(eventData.get("requestedAmount").toString());
            
            LoanOriginationState state = originationStates.get(applicationId);
            
            // Generate HMDA report
            complianceEngine.generateHMDAReport(customerId, applicationId, loanType, 
                requestedAmount, state.getDecision(), correlationId);
            
            // Generate CRA report
            complianceEngine.generateCRAReport(customerId, applicationId, loanType, 
                requestedAmount, correlationId);
            
            // Generate adverse action notice if declined
            if ("DECLINED".equals(state.getDecision())) {
                complianceEngine.generateAdverseActionNotice(customerId, applicationId, 
                    state.getDecisionReason(), correlationId);
            }
            
            logger.info("Regulatory reports generated for application: {}", applicationId);
        } catch (Exception e) {
            logger.error("Failed to generate regulatory reports", e);
            throw new RuntimeException("Regulatory reporting failed", e);
        }
    }
    
    private void sendCustomerNotifications(Map<String, Object> eventData, String correlationId) {
        try {
            String customerId = (String) eventData.get("customerId");
            String applicationId = (String) eventData.get("applicationId");
            
            LoanOriginationState state = originationStates.get(applicationId);
            
            if ("APPROVED".equals(state.getDecision())) {
                // Send approval notification
                notificationService.sendLoanApprovalNotification(customerId, applicationId, 
                    state.getLoanTerms(), state.getPricing(), correlationId);
                
                // Send loan documents
                notificationService.sendLoanDocuments(customerId, applicationId, correlationId);
            } else {
                // Send decline notification
                notificationService.sendLoanDeclineNotification(customerId, applicationId, 
                    state.getDecisionReason(), correlationId);
            }
            
            logger.info("Customer notifications sent for application: {}", applicationId);
        } catch (Exception e) {
            logger.error("Failed to send customer notifications", e);
            throw new RuntimeException("Customer notification failed", e);
        }
    }
    
    private void createAuditTrail(Map<String, Object> eventData, String correlationId) {
        try {
            String applicationId = (String) eventData.get("applicationId");
            String customerId = (String) eventData.get("customerId");
            
            LoanOriginationState state = originationStates.get(applicationId);
            
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("eventType", "LOAN_APPLICATION_PROCESSED");
            auditData.put("applicationId", applicationId);
            auditData.put("customerId", customerId);
            auditData.put("decision", state != null ? state.getDecision() : "UNKNOWN");
            auditData.put("processingTimestamp", Instant.now().toString());
            auditData.put("correlationId", correlationId);
            
            // Add key decision factors
            if (eventData.containsKey("creditScore")) {
                auditData.put("creditScore", eventData.get("creditScore"));
            }
            if (eventData.containsKey("dtiRatio")) {
                auditData.put("dtiRatio", eventData.get("dtiRatio"));
            }
            
            auditLogger.logBusinessEvent("LOAN_APPLICATION_PROCESSED", correlationId, auditData);
            
            logger.info("Audit trail created for application: {}", applicationId);
        } catch (Exception e) {
            logger.error("Failed to create audit trail", e);
            throw new RuntimeException("Audit trail creation failed", e);
        }
    }
    
    private boolean isValidLoanType(String loanType) {
        return Arrays.asList("PERSONAL", "AUTO", "MORTGAGE", "BUSINESS", "STUDENT").contains(loanType);
    }
    
    private void sendToDlq(String message, String reason, String correlationId) {
        try {
            ProducerRecord<String, String> dlqRecord = new ProducerRecord<>(DLQ_TOPIC, message);
            dlqRecord.headers().add("failure_reason", reason.getBytes(StandardCharsets.UTF_8));
            dlqRecord.headers().add("correlation_id", correlationId.getBytes(StandardCharsets.UTF_8));
            dlqRecord.headers().add("original_topic", TOPIC.getBytes(StandardCharsets.UTF_8));
            dlqRecord.headers().add("failed_at", Instant.now().toString().getBytes(StandardCharsets.UTF_8));
            
            kafkaTemplate.send(dlqRecord);
            logger.warn("Message sent to DLQ with reason: {} - correlation: {}", reason, correlationId);
        } catch (Exception e) {
            logger.error("Failed to send message to DLQ", e);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down LoanOriginationEventConsumer...");
        originationStates.clear();
        logger.info("LoanOriginationEventConsumer shutdown complete");
    }
    
    private static class LoanOriginationState {
        private String applicationId;
        private String status;
        private String decision;
        private String decisionReason;
        private Instant processedAt;
        private String correlationId;
        private Map<String, Object> loanTerms;
        private Map<String, Object> pricing;
        
        // Getters and setters
        public String getApplicationId() { return applicationId; }
        public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }
        public String getDecisionReason() { return decisionReason; }
        public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
        public Instant getProcessedAt() { return processedAt; }
        public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
        public Map<String, Object> getLoanTerms() { return loanTerms; }
        public void setLoanTerms(Map<String, Object> loanTerms) { this.loanTerms = loanTerms; }
        public Map<String, Object> getPricing() { return pricing; }
        public void setPricing(Map<String, Object> pricing) { this.pricing = pricing; }
    }
}