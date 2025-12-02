package com.waqiti.frauddetection.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.frauddetection.model.*;
import com.waqiti.frauddetection.repository.RiskAssessmentRepository;
import com.waqiti.frauddetection.service.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Production-grade Kafka consumer for risk assessment events
 * Handles comprehensive risk evaluation, customer profiling, and dynamic risk scoring
 * 
 * Critical for: Risk management, fraud prevention, regulatory compliance, credit decisions
 * SLA: Must complete risk assessment within 2 seconds for real-time decision making
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RiskAssessmentConsumer {

    private final RiskAssessmentRepository riskRepository;
    private final RiskScoringService riskScoringService;
    private final CustomerRiskService customerRiskService;
    private final MerchantRiskService merchantRiskService;
    private final TransactionRiskService transactionRiskService;
    private final BehavioralRiskService behavioralRiskService;
    private final CreditRiskService creditRiskService;
    private final RiskModelService riskModelService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final MetricsService metricsService;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long SLA_THRESHOLD_MS = 2000; // 2 seconds
    private static final int HIGH_RISK_THRESHOLD = 70;
    private static final int CRITICAL_RISK_THRESHOLD = 85;
    
    @KafkaListener(
        topics = {"risk-assessment"},
        groupId = "risk-assessment-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "risk-assessment-processor", fallbackMethod = "handleRiskAssessmentFailure")
    @Retry(name = "risk-assessment-processor")
    public void processRiskAssessmentEvent(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        log.info("Processing risk assessment event: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, Object> payload = event.getPayload();
            RiskAssessmentRequest riskRequest = extractRiskRequest(payload);
            
            // Validate risk request
            validateRiskRequest(riskRequest);
            
            // Check for duplicate assessment
            if (isDuplicateRiskRequest(riskRequest)) {
                log.warn("Duplicate risk request detected: {}, skipping", riskRequest.getAssessmentId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Perform comprehensive risk assessment
            ComprehensiveRiskAssessment assessment = performComprehensiveRiskAssessment(riskRequest);
            
            // Apply risk policies and rules
            RiskPolicyResult policyResult = applyRiskPolicies(riskRequest, assessment);
            
            // Determine risk-based actions
            RiskActionPlan actionPlan = determineRiskActions(assessment, policyResult);
            
            // Execute risk mitigation actions
            executeRiskActions(riskRequest, assessment, actionPlan);
            
            // Update risk profiles
            updateRiskProfiles(riskRequest, assessment);
            
            // Send risk notifications
            sendRiskNotifications(riskRequest, assessment, actionPlan);
            
            // Update monitoring systems
            updateMonitoringSystems(riskRequest, assessment);
            
            // Create audit trail
            auditRiskAssessment(riskRequest, assessment, event);
            
            // Record metrics
            recordRiskMetrics(riskRequest, assessment, startTime);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed risk assessment: {} score: {} level: {} in {}ms", 
                    riskRequest.getAssessmentId(), assessment.getOverallRiskScore(), 
                    assessment.getRiskLevel(), System.currentTimeMillis() - startTime);
            
        } catch (ValidationException e) {
            log.error("Validation failed for risk assessment event: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();
            
        } catch (CriticalRiskException e) {
            log.error("Critical risk assessment failed: {}", eventId, e);
            handleCriticalRiskError(event, e);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process risk assessment event: {}", eventId, e);
            handleProcessingError(event, e, acknowledgment);
        }
    }

    private RiskAssessmentRequest extractRiskRequest(Map<String, Object> payload) {
        return RiskAssessmentRequest.builder()
            .assessmentId(extractString(payload, "assessmentId", UUID.randomUUID().toString()))
            .assessmentType(RiskAssessmentType.fromString(extractString(payload, "assessmentType", null)))
            .entityType(RiskEntityType.fromString(extractString(payload, "entityType", null)))
            .entityId(extractString(payload, "entityId", null))
            .customerId(extractString(payload, "customerId", null))
            .merchantId(extractString(payload, "merchantId", null))
            .transactionId(extractString(payload, "transactionId", null))
            .accountId(extractString(payload, "accountId", null))
            .amount(extractBigDecimal(payload, "amount"))
            .currency(extractString(payload, "currency", "USD"))
            .transactionType(extractString(payload, "transactionType", null))
            .purpose(extractString(payload, "purpose", null))
            .requestedBy(extractString(payload, "requestedBy", null))
            .urgency(RiskUrgency.fromString(extractString(payload, "urgency", "NORMAL")))
            .contextData(extractMap(payload, "contextData"))
            .existingRiskFactors(extractStringList(payload, "existingRiskFactors"))
            .complianceRequirements(extractStringList(payload, "complianceRequirements"))
            .businessContext(extractMap(payload, "businessContext"))
            .geographicContext(extractMap(payload, "geographicContext"))
            .temporalContext(extractMap(payload, "temporalContext"))
            .metadata(extractMap(payload, "metadata"))
            .timestamp(Instant.now())
            .build();
    }

    private void validateRiskRequest(RiskAssessmentRequest request) {
        if (request.getAssessmentType() == null) {
            throw new ValidationException("Assessment type is required");
        }
        
        if (request.getEntityType() == null) {
            throw new ValidationException("Entity type is required");
        }
        
        if (request.getEntityId() == null || request.getEntityId().isEmpty()) {
            throw new ValidationException("Entity ID is required");
        }
        
        // Validate entity type specific requirements
        switch (request.getEntityType()) {
            case CUSTOMER:
                if (request.getCustomerId() == null) {
                    throw new ValidationException("Customer ID required for customer risk assessment");
                }
                break;
                
            case MERCHANT:
                if (request.getMerchantId() == null) {
                    throw new ValidationException("Merchant ID required for merchant risk assessment");
                }
                break;
                
            case TRANSACTION:
                if (request.getTransactionId() == null) {
                    throw new ValidationException("Transaction ID required for transaction risk assessment");
                }
                if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new ValidationException("Valid amount required for transaction risk assessment");
                }
                break;
                
            case ACCOUNT:
                if (request.getAccountId() == null) {
                    throw new ValidationException("Account ID required for account risk assessment");
                }
                break;
        }
        
        // Validate assessment type specific requirements
        if (request.getAssessmentType() == RiskAssessmentType.CREDIT_RISK && 
            request.getAmount() == null) {
            throw new ValidationException("Amount required for credit risk assessment");
        }
    }

    private boolean isDuplicateRiskRequest(RiskAssessmentRequest request) {
        return riskRepository.existsByAssessmentIdAndTimestampAfter(
            request.getAssessmentId(),
            Instant.now().minus(10, ChronoUnit.MINUTES)
        );
    }

    private ComprehensiveRiskAssessment performComprehensiveRiskAssessment(RiskAssessmentRequest request) {
        // Initialize assessment components
        CustomerRiskAssessment customerRisk = null;
        MerchantRiskAssessment merchantRisk = null;
        TransactionRiskAssessment transactionRisk = null;
        BehavioralRiskAssessment behavioralRisk = null;
        CreditRiskAssessment creditRisk = null;
        
        // Perform entity-specific risk assessments
        switch (request.getEntityType()) {
            case CUSTOMER:
                customerRisk = performCustomerRiskAssessment(request);
                behavioralRisk = performBehavioralRiskAssessment(request);
                if (request.getAssessmentType() == RiskAssessmentType.CREDIT_RISK) {
                    creditRisk = performCreditRiskAssessment(request);
                }
                break;
                
            case MERCHANT:
                merchantRisk = performMerchantRiskAssessment(request);
                break;
                
            case TRANSACTION:
                transactionRisk = performTransactionRiskAssessment(request);
                if (request.getCustomerId() != null) {
                    customerRisk = performCustomerRiskAssessment(request);
                }
                if (request.getMerchantId() != null) {
                    merchantRisk = performMerchantRiskAssessment(request);
                }
                break;
                
            case ACCOUNT:
                customerRisk = performCustomerRiskAssessment(request);
                behavioralRisk = performBehavioralRiskAssessment(request);
                break;
        }
        
        // Additional assessments based on assessment type
        switch (request.getAssessmentType()) {
            case COMPREHENSIVE:
                // Perform all applicable assessments
                if (customerRisk == null && request.getCustomerId() != null) {
                    customerRisk = performCustomerRiskAssessment(request);
                }
                if (merchantRisk == null && request.getMerchantId() != null) {
                    merchantRisk = performMerchantRiskAssessment(request);
                }
                if (transactionRisk == null && request.getTransactionId() != null) {
                    transactionRisk = performTransactionRiskAssessment(request);
                }
                if (behavioralRisk == null) {
                    behavioralRisk = performBehavioralRiskAssessment(request);
                }
                break;
                
            case FRAUD_RISK:
                // Focus on fraud-related risk factors
                if (transactionRisk == null) {
                    transactionRisk = performTransactionRiskAssessment(request);
                }
                break;
                
            case CREDIT_RISK:
                // Focus on credit-related risk factors
                if (creditRisk == null) {
                    creditRisk = performCreditRiskAssessment(request);
                }
                break;
        }
        
        // Aggregate overall risk assessment
        OverallRiskScore overallScore = calculateOverallRiskScore(
            customerRisk, merchantRisk, transactionRisk, behavioralRisk, creditRisk);
        
        // Determine risk level and factors
        String riskLevel = determineRiskLevel(overallScore.getScore());
        List<RiskFactor> riskFactors = extractRiskFactors(
            customerRisk, merchantRisk, transactionRisk, behavioralRisk, creditRisk);
        
        // Generate risk recommendations
        List<RiskRecommendation> recommendations = generateRiskRecommendations(
            request, overallScore, riskFactors);
        
        return ComprehensiveRiskAssessment.builder()
            .assessmentId(request.getAssessmentId())
            .entityType(request.getEntityType())
            .entityId(request.getEntityId())
            .assessmentType(request.getAssessmentType())
            .customerRisk(customerRisk)
            .merchantRisk(merchantRisk)
            .transactionRisk(transactionRisk)
            .behavioralRisk(behavioralRisk)
            .creditRisk(creditRisk)
            .overallRiskScore(overallScore.getScore())
            .riskLevel(riskLevel)
            .riskFactors(riskFactors)
            .recommendations(recommendations)
            .confidence(overallScore.getConfidence())
            .assessmentTimestamp(Instant.now())
            .build();
    }

    private CustomerRiskAssessment performCustomerRiskAssessment(RiskAssessmentRequest request) {
        if (request.getCustomerId() == null) {
            // PRODUCTION FIX: Return proper error instead of null
            throw new IllegalArgumentException("Customer ID is required for risk assessment");
        }
        
        // Historical risk analysis
        CustomerHistoryAnalysis historyAnalysis = customerRiskService.analyzeCustomerHistory(
            request.getCustomerId());
        
        // Identity verification risk
        IdentityVerificationRisk identityRisk = customerRiskService.assessIdentityRisk(
            request.getCustomerId());
        
        // Financial profile risk
        FinancialProfileRisk financialRisk = customerRiskService.assessFinancialRisk(
            request.getCustomerId(), request.getAmount());
        
        // Geographic risk
        GeographicRisk geoRisk = customerRiskService.assessGeographicRisk(
            request.getCustomerId(), request.getGeographicContext());
        
        // Compliance risk
        ComplianceRisk complianceRisk = customerRiskService.assessComplianceRisk(
            request.getCustomerId(), request.getComplianceRequirements());
        
        // KYC/AML risk
        KycAmlRisk kycAmlRisk = customerRiskService.assessKycAmlRisk(request.getCustomerId());
        
        // Calculate customer risk score
        int customerRiskScore = customerRiskService.calculateCustomerRiskScore(
            historyAnalysis, identityRisk, financialRisk, geoRisk, complianceRisk, kycAmlRisk);
        
        return CustomerRiskAssessment.builder()
            .customerId(request.getCustomerId())
            .historyAnalysis(historyAnalysis)
            .identityRisk(identityRisk)
            .financialRisk(financialRisk)
            .geographicRisk(geoRisk)
            .complianceRisk(complianceRisk)
            .kycAmlRisk(kycAmlRisk)
            .riskScore(customerRiskScore)
            .riskLevel(determineRiskLevel(customerRiskScore))
            .assessmentDate(Instant.now())
            .build();
    }

    private MerchantRiskAssessment performMerchantRiskAssessment(RiskAssessmentRequest request) {
        if (request.getMerchantId() == null) {
            // PRODUCTION FIX: Return proper error instead of null
            throw new IllegalArgumentException("Merchant ID is required for risk assessment");
        }
        
        // Business risk analysis
        BusinessRisk businessRisk = merchantRiskService.assessBusinessRisk(request.getMerchantId());
        
        // Financial stability risk
        FinancialStabilityRisk financialStability = merchantRiskService.assessFinancialStability(
            request.getMerchantId());
        
        // Operational risk
        OperationalRisk operationalRisk = merchantRiskService.assessOperationalRisk(
            request.getMerchantId());
        
        // Reputation risk
        ReputationRisk reputationRisk = merchantRiskService.assessReputationRisk(
            request.getMerchantId());
        
        // Compliance risk
        MerchantComplianceRisk complianceRisk = merchantRiskService.assessComplianceRisk(
            request.getMerchantId());
        
        // Transaction pattern risk
        TransactionPatternRisk patternRisk = merchantRiskService.assessTransactionPatternRisk(
            request.getMerchantId());
        
        // Calculate merchant risk score
        int merchantRiskScore = merchantRiskService.calculateMerchantRiskScore(
            businessRisk, financialStability, operationalRisk, reputationRisk, 
            complianceRisk, patternRisk);
        
        return MerchantRiskAssessment.builder()
            .merchantId(request.getMerchantId())
            .businessRisk(businessRisk)
            .financialStabilityRisk(financialStability)
            .operationalRisk(operationalRisk)
            .reputationRisk(reputationRisk)
            .complianceRisk(complianceRisk)
            .transactionPatternRisk(patternRisk)
            .riskScore(merchantRiskScore)
            .riskLevel(determineRiskLevel(merchantRiskScore))
            .assessmentDate(Instant.now())
            .build();
    }

    private TransactionRiskAssessment performTransactionRiskAssessment(RiskAssessmentRequest request) {
        // Amount-based risk
        AmountRisk amountRisk = transactionRiskService.assessAmountRisk(
            request.getAmount(), request.getCustomerId(), request.getTransactionType());
        
        // Velocity risk
        VelocityRisk velocityRisk = transactionRiskService.assessVelocityRisk(
            request.getCustomerId(), request.getAmount(), request.getTimestamp());
        
        // Pattern risk
        TransactionPatternRisk patternRisk = transactionRiskService.assessPatternRisk(
            request.getCustomerId(), request.getTransactionType(), request.getContextData());
        
        // Channel risk
        ChannelRisk channelRisk = transactionRiskService.assessChannelRisk(
            request.getContextData());
        
        // Timing risk
        TimingRisk timingRisk = transactionRiskService.assessTimingRisk(
            request.getTimestamp(), request.getTemporalContext());
        
        // Counterparty risk
        CounterpartyRisk counterpartyRisk = transactionRiskService.assessCounterpartyRisk(
            request.getMerchantId(), request.getAmount());
        
        // Calculate transaction risk score
        int transactionRiskScore = transactionRiskService.calculateTransactionRiskScore(
            amountRisk, velocityRisk, patternRisk, channelRisk, timingRisk, counterpartyRisk);
        
        return TransactionRiskAssessment.builder()
            .transactionId(request.getTransactionId())
            .amountRisk(amountRisk)
            .velocityRisk(velocityRisk)
            .patternRisk(patternRisk)
            .channelRisk(channelRisk)
            .timingRisk(timingRisk)
            .counterpartyRisk(counterpartyRisk)
            .riskScore(transactionRiskScore)
            .riskLevel(determineRiskLevel(transactionRiskScore))
            .assessmentDate(Instant.now())
            .build();
    }

    private BehavioralRiskAssessment performBehavioralRiskAssessment(RiskAssessmentRequest request) {
        if (request.getCustomerId() == null) {
            // PRODUCTION FIX: Return proper error instead of null
            throw new IllegalArgumentException("Customer ID is required for behavioral analysis");
        }
        
        // Spending behavior analysis
        SpendingBehaviorRisk spendingRisk = behavioralRiskService.assessSpendingBehavior(
            request.getCustomerId(), request.getAmount(), request.getTransactionType());
        
        // Login behavior analysis
        LoginBehaviorRisk loginRisk = behavioralRiskService.assessLoginBehavior(
            request.getCustomerId(), request.getContextData());
        
        // Device behavior analysis
        DeviceBehaviorRisk deviceRisk = behavioralRiskService.assessDeviceBehavior(
            request.getCustomerId(), request.getContextData());
        
        // Navigation behavior analysis
        NavigationBehaviorRisk navigationRisk = behavioralRiskService.assessNavigationBehavior(
            request.getCustomerId(), request.getContextData());
        
        // Temporal behavior analysis
        TemporalBehaviorRisk temporalRisk = behavioralRiskService.assessTemporalBehavior(
            request.getCustomerId(), request.getTimestamp());
        
        // Calculate behavioral risk score
        int behavioralRiskScore = behavioralRiskService.calculateBehavioralRiskScore(
            spendingRisk, loginRisk, deviceRisk, navigationRisk, temporalRisk);
        
        return BehavioralRiskAssessment.builder()
            .customerId(request.getCustomerId())
            .spendingBehaviorRisk(spendingRisk)
            .loginBehaviorRisk(loginRisk)
            .deviceBehaviorRisk(deviceRisk)
            .navigationBehaviorRisk(navigationRisk)
            .temporalBehaviorRisk(temporalRisk)
            .riskScore(behavioralRiskScore)
            .riskLevel(determineRiskLevel(behavioralRiskScore))
            .assessmentDate(Instant.now())
            .build();
    }

    private CreditRiskAssessment performCreditRiskAssessment(RiskAssessmentRequest request) {
        if (request.getCustomerId() == null || request.getAmount() == null) {
            // PRODUCTION FIX: Return proper error instead of null
            throw new IllegalArgumentException("Customer ID and amount are required for transaction risk assessment");
        }
        
        // Credit score analysis
        CreditScoreAnalysis creditScore = creditRiskService.analyzeCreditScore(
            request.getCustomerId());
        
        // Income verification
        IncomeVerification incomeVerification = creditRiskService.verifyIncome(
            request.getCustomerId());
        
        // Debt-to-income ratio
        DebtToIncomeRatio dtiRatio = creditRiskService.calculateDTI(
            request.getCustomerId(), request.getAmount());
        
        // Payment history analysis
        PaymentHistoryAnalysis paymentHistory = creditRiskService.analyzePaymentHistory(
            request.getCustomerId());
        
        // Credit utilization
        CreditUtilization creditUtilization = creditRiskService.analyzeCreditUtilization(
            request.getCustomerId());
        
        // Default probability
        DefaultProbability defaultProbability = creditRiskService.calculateDefaultProbability(
            creditScore, incomeVerification, dtiRatio, paymentHistory, creditUtilization);
        
        // Calculate credit risk score
        int creditRiskScore = creditRiskService.calculateCreditRiskScore(
            creditScore, incomeVerification, dtiRatio, paymentHistory, 
            creditUtilization, defaultProbability);
        
        return CreditRiskAssessment.builder()
            .customerId(request.getCustomerId())
            .requestedAmount(request.getAmount())
            .creditScoreAnalysis(creditScore)
            .incomeVerification(incomeVerification)
            .debtToIncomeRatio(dtiRatio)
            .paymentHistoryAnalysis(paymentHistory)
            .creditUtilization(creditUtilization)
            .defaultProbability(defaultProbability)
            .riskScore(creditRiskScore)
            .riskLevel(determineRiskLevel(creditRiskScore))
            .assessmentDate(Instant.now())
            .build();
    }

    private OverallRiskScore calculateOverallRiskScore(CustomerRiskAssessment customerRisk,
                                                     MerchantRiskAssessment merchantRisk,
                                                     TransactionRiskAssessment transactionRisk,
                                                     BehavioralRiskAssessment behavioralRisk,
                                                     CreditRiskAssessment creditRisk) {
        
        List<ComponentScore> componentScores = new ArrayList<>();
        
        if (customerRisk != null) {
            componentScores.add(new ComponentScore("CUSTOMER", customerRisk.getRiskScore(), 0.3));
        }
        
        if (merchantRisk != null) {
            componentScores.add(new ComponentScore("MERCHANT", merchantRisk.getRiskScore(), 0.2));
        }
        
        if (transactionRisk != null) {
            componentScores.add(new ComponentScore("TRANSACTION", transactionRisk.getRiskScore(), 0.25));
        }
        
        if (behavioralRisk != null) {
            componentScores.add(new ComponentScore("BEHAVIORAL", behavioralRisk.getRiskScore(), 0.15));
        }
        
        if (creditRisk != null) {
            componentScores.add(new ComponentScore("CREDIT", creditRisk.getRiskScore(), 0.1));
        }
        
        // Normalize weights
        double totalWeight = componentScores.stream().mapToDouble(ComponentScore::getWeight).sum();
        componentScores.forEach(cs -> cs.setWeight(cs.getWeight() / totalWeight));
        
        // Calculate weighted score
        double weightedScore = componentScores.stream()
            .mapToDouble(cs -> cs.getScore() * cs.getWeight())
            .sum();
        
        // Calculate confidence based on data availability and consistency
        double confidence = calculateConfidence(componentScores);
        
        return OverallRiskScore.builder()
            .score((int) Math.round(weightedScore))
            .componentScores(componentScores)
            .confidence(confidence)
            .calculatedAt(Instant.now())
            .build();
    }

    private double calculateConfidence(List<ComponentScore> componentScores) {
        if (componentScores.isEmpty()) return 0.0;
        
        // Base confidence from data availability
        double dataAvailability = Math.min(componentScores.size() / 5.0, 1.0);
        
        // Score consistency
        double mean = componentScores.stream().mapToDouble(ComponentScore::getScore).average().orElse(0.0);
        double variance = componentScores.stream()
            .mapToDouble(cs -> Math.pow(cs.getScore() - mean, 2))
            .average().orElse(0.0);
        double consistency = Math.max(0.0, 1.0 - (Math.sqrt(variance) / 100.0));
        
        return (dataAvailability * 0.6) + (consistency * 0.4);
    }

    private String determineRiskLevel(int score) {
        if (score >= CRITICAL_RISK_THRESHOLD) return "CRITICAL";
        if (score >= HIGH_RISK_THRESHOLD) return "HIGH";
        if (score >= 50) return "MEDIUM";
        if (score >= 25) return "LOW";
        return "MINIMAL";
    }

    private List<RiskFactor> extractRiskFactors(CustomerRiskAssessment customerRisk,
                                               MerchantRiskAssessment merchantRisk,
                                               TransactionRiskAssessment transactionRisk,
                                               BehavioralRiskAssessment behavioralRisk,
                                               CreditRiskAssessment creditRisk) {
        List<RiskFactor> factors = new ArrayList<>();
        
        // Extract customer risk factors
        if (customerRisk != null) {
            factors.addAll(extractCustomerRiskFactors(customerRisk));
        }
        
        // Extract merchant risk factors
        if (merchantRisk != null) {
            factors.addAll(extractMerchantRiskFactors(merchantRisk));
        }
        
        // Extract transaction risk factors
        if (transactionRisk != null) {
            factors.addAll(extractTransactionRiskFactors(transactionRisk));
        }
        
        // Extract behavioral risk factors
        if (behavioralRisk != null) {
            factors.addAll(extractBehavioralRiskFactors(behavioralRisk));
        }
        
        // Extract credit risk factors
        if (creditRisk != null) {
            factors.addAll(extractCreditRiskFactors(creditRisk));
        }
        
        return factors;
    }

    private List<RiskFactor> extractCustomerRiskFactors(CustomerRiskAssessment customerRisk) {
        List<RiskFactor> factors = new ArrayList<>();
        
        if (customerRisk.getIdentityRisk().getRiskScore() > HIGH_RISK_THRESHOLD) {
            factors.add(new RiskFactor("IDENTITY_RISK", "High identity verification risk", "HIGH"));
        }
        
        if (customerRisk.getComplianceRisk().getRiskScore() > HIGH_RISK_THRESHOLD) {
            factors.add(new RiskFactor("COMPLIANCE_RISK", "Compliance-related risk factors", "HIGH"));
        }
        
        if (customerRisk.getKycAmlRisk().getRiskScore() > HIGH_RISK_THRESHOLD) {
            factors.add(new RiskFactor("KYC_AML_RISK", "KYC/AML compliance concerns", "HIGH"));
        }
        
        return factors;
    }

    private List<RiskFactor> extractMerchantRiskFactors(MerchantRiskAssessment merchantRisk) {
        List<RiskFactor> factors = new ArrayList<>();
        
        if (merchantRisk.getBusinessRisk().getRiskScore() > HIGH_RISK_THRESHOLD) {
            factors.add(new RiskFactor("BUSINESS_RISK", "High business risk profile", "HIGH"));
        }
        
        if (merchantRisk.getReputationRisk().getRiskScore() > HIGH_RISK_THRESHOLD) {
            factors.add(new RiskFactor("REPUTATION_RISK", "Negative reputation indicators", "HIGH"));
        }
        
        return factors;
    }

    private List<RiskFactor> extractTransactionRiskFactors(TransactionRiskAssessment transactionRisk) {
        List<RiskFactor> factors = new ArrayList<>();
        
        if (transactionRisk.getVelocityRisk().getRiskScore() > HIGH_RISK_THRESHOLD) {
            factors.add(new RiskFactor("VELOCITY_RISK", "Unusual transaction velocity", "HIGH"));
        }
        
        if (transactionRisk.getAmountRisk().getRiskScore() > HIGH_RISK_THRESHOLD) {
            factors.add(new RiskFactor("AMOUNT_RISK", "Suspicious transaction amount", "HIGH"));
        }
        
        return factors;
    }

    private List<RiskFactor> extractBehavioralRiskFactors(BehavioralRiskAssessment behavioralRisk) {
        List<RiskFactor> factors = new ArrayList<>();
        
        if (behavioralRisk.getSpendingBehaviorRisk().getRiskScore() > HIGH_RISK_THRESHOLD) {
            factors.add(new RiskFactor("SPENDING_BEHAVIOR", "Abnormal spending patterns", "HIGH"));
        }
        
        if (behavioralRisk.getDeviceBehaviorRisk().getRiskScore() > HIGH_RISK_THRESHOLD) {
            factors.add(new RiskFactor("DEVICE_BEHAVIOR", "Suspicious device usage", "HIGH"));
        }
        
        return factors;
    }

    private List<RiskFactor> extractCreditRiskFactors(CreditRiskAssessment creditRisk) {
        List<RiskFactor> factors = new ArrayList<>();
        
        if (creditRisk.getDefaultProbability().getProbability() > 0.2) {
            factors.add(new RiskFactor("DEFAULT_RISK", "High probability of default", "HIGH"));
        }
        
        if (creditRisk.getDebtToIncomeRatio().getRatio() > 0.4) {
            factors.add(new RiskFactor("DTI_RISK", "High debt-to-income ratio", "MEDIUM"));
        }
        
        return factors;
    }

    private List<RiskRecommendation> generateRiskRecommendations(RiskAssessmentRequest request,
                                                               OverallRiskScore overallScore,
                                                               List<RiskFactor> riskFactors) {
        return riskModelService.generateRecommendations(request, overallScore, riskFactors);
    }

    private RiskPolicyResult applyRiskPolicies(RiskAssessmentRequest request, 
                                             ComprehensiveRiskAssessment assessment) {
        return riskModelService.applyRiskPolicies(request, assessment);
    }

    private RiskActionPlan determineRiskActions(ComprehensiveRiskAssessment assessment,
                                              RiskPolicyResult policyResult) {
        return riskModelService.determineRiskActions(assessment, policyResult);
    }

    private void executeRiskActions(RiskAssessmentRequest request,
                                  ComprehensiveRiskAssessment assessment,
                                  RiskActionPlan actionPlan) {
        
        CompletableFuture.runAsync(() -> {
            try {
                riskModelService.executeRiskActions(request, assessment, actionPlan);
            } catch (Exception e) {
                log.error("Failed to execute risk actions for assessment: {}", 
                         request.getAssessmentId(), e);
            }
        });
    }

    private void updateRiskProfiles(RiskAssessmentRequest request, 
                                  ComprehensiveRiskAssessment assessment) {
        
        CompletableFuture.runAsync(() -> {
            try {
                if (assessment.getCustomerRisk() != null) {
                    customerRiskService.updateRiskProfile(request.getCustomerId(), assessment);
                }
                
                if (assessment.getMerchantRisk() != null) {
                    merchantRiskService.updateRiskProfile(request.getMerchantId(), assessment);
                }
            } catch (Exception e) {
                log.error("Failed to update risk profiles for assessment: {}", 
                         request.getAssessmentId(), e);
            }
        });
    }

    private void sendRiskNotifications(RiskAssessmentRequest request,
                                     ComprehensiveRiskAssessment assessment,
                                     RiskActionPlan actionPlan) {
        
        Map<String, Object> notificationData = Map.of(
            "assessmentId", request.getAssessmentId(),
            "entityType", request.getEntityType().toString(),
            "entityId", request.getEntityId(),
            "riskScore", assessment.getOverallRiskScore(),
            "riskLevel", assessment.getRiskLevel(),
            "riskFactors", assessment.getRiskFactors().size(),
            "actions", actionPlan.getActions().size()
        );
        
        // Critical risk alerts
        if (assessment.getOverallRiskScore() >= CRITICAL_RISK_THRESHOLD) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendCriticalRiskAlert(notificationData);
                notificationService.sendExecutiveAlert("CRITICAL_RISK", notificationData);
            });
        }
        
        // High risk alerts
        if (assessment.getOverallRiskScore() >= HIGH_RISK_THRESHOLD) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendHighRiskAlert(notificationData);
            });
        }
        
        // Risk team notifications
        CompletableFuture.runAsync(() -> {
            notificationService.sendRiskTeamNotification(
                request.getAssessmentType().toString(), notificationData);
        });
    }

    private void updateMonitoringSystems(RiskAssessmentRequest request, 
                                       ComprehensiveRiskAssessment assessment) {
        // Update risk monitoring dashboards
        monitoringService.updateRiskDashboard(request, assessment);
        
        // Update risk metrics
        riskMonitoringService.updateRiskMetrics(request, assessment);
    }

    private void auditRiskAssessment(RiskAssessmentRequest request,
                                   ComprehensiveRiskAssessment assessment,
                                   GenericKafkaEvent event) {
        auditService.auditRiskAssessment(
            request.getAssessmentId(),
            request.getEntityType().toString(),
            request.getEntityId(),
            assessment.getOverallRiskScore(),
            assessment.getRiskLevel(),
            assessment.getConfidence(),
            event.getEventId()
        );
    }

    private void recordRiskMetrics(RiskAssessmentRequest request,
                                 ComprehensiveRiskAssessment assessment,
                                 long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        metricsService.recordRiskAssessmentMetrics(
            request.getAssessmentType().toString(),
            request.getEntityType().toString(),
            assessment.getOverallRiskScore(),
            assessment.getRiskLevel(),
            processingTime,
            processingTime <= SLA_THRESHOLD_MS,
            assessment.getConfidence()
        );
        
        // Record risk factors
        metricsService.recordRiskFactors(
            assessment.getRiskFactors()
        );
    }

    // Error handling methods
    private void handleValidationError(GenericKafkaEvent event, ValidationException e) {
        auditService.logValidationError(event.getEventId(), e.getMessage());
        kafkaTemplate.send("risk-assessment-validation-errors", event);
    }

    private void handleCriticalRiskError(GenericKafkaEvent event, CriticalRiskException e) {
        // Create emergency alert for critical risk processing failures
        emergencyAlertService.createEmergencyAlert(
            "CRITICAL_RISK_PROCESSING_FAILED",
            event.getPayload(),
            e.getMessage()
        );
        
        kafkaTemplate.send("risk-assessment-critical-failures", event);
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;
            
            log.warn("Retrying risk assessment event {} after {}ms (attempt {})", 
                    eventId, retryDelay, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            scheduledExecutor.schedule(() -> {
                kafkaTemplate.send("risk-assessment-retry", event);
            }, retryDelay, TimeUnit.MILLISECONDS);
            
            acknowledgment.acknowledge();
        } else {
            log.error("Max retries exceeded for risk assessment event {}, sending to DLQ", eventId);
            sendToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "risk-assessment");
        
        kafkaTemplate.send("risk-assessment.DLQ", event);
        
        alertingService.createDLQAlert(
            "risk-assessment",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handleRiskAssessmentFailure(GenericKafkaEvent event, String topic, int partition,
                                          long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for risk assessment processing: {}", e.getMessage());
        
        failedEventRepository.save(
            FailedEvent.builder()
                .eventId(event.getEventId())
                .topic(topic)
                .payload(event)
                .errorMessage(e.getMessage())
                .createdAt(Instant.now())
                .build()
        );
        
        alertingService.sendCriticalAlert(
            "Risk Assessment Circuit Breaker Open",
            "Risk assessment processing is failing. Risk management capability compromised."
        );
        
        acknowledgment.acknowledge();
    }

    // Helper extraction methods
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * Extract BigDecimal with secure null handling for risk calculations
     * 
     * CRITICAL FIX: Never return null in risk assessment - could bypass risk controls
     * Use BigDecimal.ZERO as safe fallback for all risk calculations
     */
    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            log.warn("RISK_ASSESSMENT_WARNING: Missing value for key '{}' in risk calculation - using zero", key);
            return BigDecimal.ZERO; // Safe default for risk calculations
        }
        
        try {
            if (value instanceof BigDecimal) return (BigDecimal) value;
            if (value instanceof Number) return new BigDecimal(value.toString());
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            log.error("CRITICAL: Failed to parse BigDecimal for key '{}' in risk assessment - using zero fallback", key, e);
            return BigDecimal.ZERO; // Always return safe value for risk calculations
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.stream()
                .map(Object::toString)
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    // Custom exceptions
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static class CriticalRiskException extends RuntimeException {
        public CriticalRiskException(String message) {
            super(message);
        }
    }
}