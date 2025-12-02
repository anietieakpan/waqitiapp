package com.waqiti.frauddetection.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.frauddetection.model.*;
import com.waqiti.frauddetection.repository.MerchantRiskRepository;
import com.waqiti.frauddetection.service.*;
import com.waqiti.common.math.MoneyMath;
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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade Kafka consumer for merchant risk scoring
 * Handles dynamic merchant risk assessment, scoring updates, and risk-based controls
 * 
 * Critical for: Merchant onboarding, payment processing, risk management
 * SLA: Must process risk updates within 15 seconds for real-time decision making
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MerchantRiskScoringConsumer {

    private final MerchantRiskRepository riskRepository;
    private final MerchantRiskScoringService scoringService;
    private final MerchantProfileService profileService;
    private final TransactionAnalysisService transactionAnalysisService;
    private final IndustryBenchmarkService benchmarkService;
    private final RiskControlService riskControlService;
    private final NotificationService notificationService;
    private final WorkflowService workflowService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ScheduledExecutorService scheduledExecutor;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long SLA_THRESHOLD_MS = 15000; // 15 seconds
    private static final Set<String> HIGH_RISK_INDUSTRIES = Set.of(
        "ADULT_ENTERTAINMENT", "GAMBLING", "CRYPTOCURRENCY", "FOREX", "DEBT_COLLECTION", "TELEMARKETING"
    );
    
    @KafkaListener(
        topics = {"merchant-risk-scoring"},
        groupId = "merchant-risk-scoring-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "merchant-risk-scoring-processor", fallbackMethod = "handleMerchantRiskScoringFailure")
    @Retry(name = "merchant-risk-scoring-processor")
    public void processMerchantRiskScoring(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        log.info("Processing merchant risk scoring: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, Object> payload = event.getPayload();
            MerchantRiskScoringRequest request = extractRiskScoringRequest(payload);
            
            // Validate request
            validateRequest(request);
            
            // Check for duplicate request
            if (isDuplicateRequest(request)) {
                log.warn("Duplicate merchant risk scoring request: {}, skipping", request.getRequestId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Enrich request with additional merchant data
            MerchantRiskScoringRequest enrichedRequest = enrichRequest(request);
            
            // Determine scoring trigger and priority
            ScoringTrigger trigger = determineScoringTrigger(enrichedRequest);
            
            // Perform comprehensive risk scoring
            MerchantRiskScore riskScore = performRiskScoring(enrichedRequest, trigger);
            
            // Apply risk-based controls
            if (trigger.requiresControlUpdates()) {
                applyRiskControls(enrichedRequest, riskScore);
            }
            
            // Update merchant profile
            updateMerchantProfile(enrichedRequest, riskScore);
            
            // Compare against industry benchmarks
            if (trigger.enablesBenchmarking()) {
                performBenchmarkAnalysis(enrichedRequest, riskScore);
            }
            
            // Trigger automated actions
            if (trigger.hasAutomatedActions()) {
                triggerAutomatedActions(enrichedRequest, riskScore, trigger);
            }
            
            // Send notifications
            sendRiskScoringNotifications(enrichedRequest, riskScore, trigger);
            
            // Update monitoring systems
            updateMonitoringSystems(enrichedRequest, riskScore);
            
            // Audit scoring operation
            auditRiskScoring(enrichedRequest, riskScore, event);
            
            // Record metrics
            recordScoringMetrics(enrichedRequest, riskScore, startTime);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed merchant risk scoring: {} merchant: {} score: {} in {}ms", 
                    enrichedRequest.getRequestId(), enrichedRequest.getMerchantId(), 
                    riskScore.getOverallScore(), System.currentTimeMillis() - startTime);
            
        } catch (ValidationException e) {
            log.error("Validation failed for merchant risk scoring: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();
            
        } catch (CriticalRiskException e) {
            log.error("Critical risk scoring failed: {}", eventId, e);
            handleCriticalRiskError(event, e);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process merchant risk scoring: {}", eventId, e);
            handleProcessingError(event, e, acknowledgment);
        }
    }

    private MerchantRiskScoringRequest extractRiskScoringRequest(Map<String, Object> payload) {
        return MerchantRiskScoringRequest.builder()
            .requestId(extractString(payload, "requestId", UUID.randomUUID().toString()))
            .merchantId(extractString(payload, "merchantId", null))
            .triggerType(extractString(payload, "triggerType", "SCHEDULED"))
            .triggerReason(extractString(payload, "triggerReason", null))
            .urgency(RiskScoringUrgency.fromString(extractString(payload, "urgency", "NORMAL")))
            .includedFactors(extractStringList(payload, "includedFactors"))
            .excludedFactors(extractStringList(payload, "excludedFactors"))
            .scoringPeriod(extractString(payload, "scoringPeriod", "30_DAYS"))
            .requestedBy(extractString(payload, "requestedBy", "SYSTEM"))
            .sourceSystem(extractString(payload, "sourceSystem", "UNKNOWN"))
            .metadata(extractMap(payload, "metadata"))
            .createdAt(Instant.now())
            .build();
    }

    private void validateRequest(MerchantRiskScoringRequest request) {
        if (request.getMerchantId() == null || request.getMerchantId().isEmpty()) {
            throw new ValidationException("Merchant ID is required");
        }
        
        if (request.getTriggerType() == null || request.getTriggerType().isEmpty()) {
            throw new ValidationException("Trigger type is required");
        }
        
        if (request.getUrgency() == null) {
            throw new ValidationException("Urgency level is required");
        }
        
        // Validate merchant exists
        if (!merchantService.merchantExists(request.getMerchantId())) {
            throw new ValidationException("Merchant not found: " + request.getMerchantId());
        }
        
        // Validate scoring period
        if (!isValidScoringPeriod(request.getScoringPeriod())) {
            throw new ValidationException("Invalid scoring period: " + request.getScoringPeriod());
        }
    }

    private boolean isValidScoringPeriod(String period) {
        return Arrays.asList("7_DAYS", "30_DAYS", "90_DAYS", "180_DAYS", "365_DAYS").contains(period);
    }

    private boolean isDuplicateRequest(MerchantRiskScoringRequest request) {
        // Check for recent duplicate requests
        return riskRepository.existsSimilarRequest(
            request.getMerchantId(),
            request.getTriggerType(),
            Instant.now().minus(30, ChronoUnit.MINUTES)
        );
    }

    private MerchantRiskScoringRequest enrichRequest(MerchantRiskScoringRequest request) {
        // Enrich with merchant profile data
        MerchantProfile profile = profileService.getMerchantProfile(request.getMerchantId());
        if (profile != null) {
            request.setMerchantName(profile.getMerchantName());
            request.setIndustry(profile.getIndustry());
            request.setCountry(profile.getCountry());
            request.setOnboardingDate(profile.getOnboardingDate());
            request.setBusinessType(profile.getBusinessType());
            request.setProcessingVolume(profile.getMonthlyProcessingVolume());
        }
        
        // Enrich with current risk data
        MerchantRiskProfile currentRisk = riskRepository.getCurrentRiskProfile(request.getMerchantId());
        if (currentRisk != null) {
            request.setCurrentRiskScore(currentRisk.getOverallScore());
            request.setLastScoringDate(currentRisk.getLastUpdated());
            request.setRiskTrend(currentRisk.getRiskTrend());
        }
        
        // Enrich with recent activity summary
        MerchantActivitySummary activity = transactionAnalysisService.getActivitySummary(
            request.getMerchantId(),
            parseScoringPeriod(request.getScoringPeriod())
        );
        request.setActivitySummary(activity);
        
        return request;
    }

    private int parseScoringPeriod(String period) {
        switch (period) {
            case "7_DAYS": return 7;
            case "30_DAYS": return 30;
            case "90_DAYS": return 90;
            case "180_DAYS": return 180;
            case "365_DAYS": return 365;
            default: return 30;
        }
    }

    private ScoringTrigger determineScoringTrigger(MerchantRiskScoringRequest request) {
        ScoringTrigger trigger = new ScoringTrigger();
        trigger.setTriggerType(request.getTriggerType());
        trigger.setUrgency(request.getUrgency());
        
        // Determine required capabilities based on trigger
        switch (request.getTriggerType()) {
            case "ONBOARDING":
                trigger.setRequiresControlUpdates(true);
                trigger.setEnablesBenchmarking(true);
                trigger.setHasAutomatedActions(true);
                break;
                
            case "TRANSACTION_ANOMALY":
                trigger.setRequiresControlUpdates(true);
                trigger.setHasAutomatedActions(true);
                break;
                
            case "CHARGEBACK_SPIKE":
                trigger.setRequiresControlUpdates(true);
                trigger.setHasAutomatedActions(true);
                break;
                
            case "VELOCITY_VIOLATION":
                trigger.setRequiresControlUpdates(true);
                break;
                
            case "SCHEDULED":
                trigger.setEnablesBenchmarking(true);
                break;
                
            case "REGULATORY_REVIEW":
                trigger.setEnablesBenchmarking(true);
                trigger.setHasAutomatedActions(true);
                break;
                
            default:
                trigger.setRequiresControlUpdates(false);
                trigger.setEnablesBenchmarking(false);
                trigger.setHasAutomatedActions(false);
        }
        
        return trigger;
    }

    private MerchantRiskScore performRiskScoring(MerchantRiskScoringRequest request, ScoringTrigger trigger) {
        MerchantRiskScore riskScore = new MerchantRiskScore();
        riskScore.setMerchantId(request.getMerchantId());
        riskScore.setScoringDate(Instant.now());
        riskScore.setScoringPeriod(request.getScoringPeriod());
        riskScore.setTriggerType(request.getTriggerType());
        
        // Calculate individual risk factors
        BusinessRiskScore businessRisk = calculateBusinessRisk(request);
        TransactionRiskScore transactionRisk = calculateTransactionRisk(request);
        FinancialRiskScore financialRisk = calculateFinancialRisk(request);
        ComplianceRiskScore complianceRisk = calculateComplianceRisk(request);
        OperationalRiskScore operationalRisk = calculateOperationalRisk(request);
        ReputationalRiskScore reputationalRisk = calculateReputationalRisk(request);
        
        riskScore.setBusinessRisk(businessRisk);
        riskScore.setTransactionRisk(transactionRisk);
        riskScore.setFinancialRisk(financialRisk);
        riskScore.setComplianceRisk(complianceRisk);
        riskScore.setOperationalRisk(operationalRisk);
        riskScore.setReputationalRisk(reputationalRisk);
        
        // Calculate weighted overall score
        int overallScore = calculateOverallScore(riskScore, request.getIndustry());
        riskScore.setOverallScore(overallScore);
        
        // Determine risk level and category
        riskScore.setRiskLevel(determineRiskLevel(overallScore));
        riskScore.setRiskCategory(determineRiskCategory(riskScore, request.getIndustry()));
        
        // Calculate risk trend
        riskScore.setRiskTrend(calculateRiskTrend(request.getMerchantId(), overallScore));
        
        // Generate risk insights and recommendations
        riskScore.setRiskInsights(generateRiskInsights(riskScore, request));
        riskScore.setRecommendations(generateRecommendations(riskScore, request));
        
        return riskScore;
    }

    private BusinessRiskScore calculateBusinessRisk(MerchantRiskScoringRequest request) {
        BusinessRiskScore businessRisk = new BusinessRiskScore();
        int score = 50; // Base score
        
        // Industry risk factor
        if (HIGH_RISK_INDUSTRIES.contains(request.getIndustry())) {
            score += 25;
            businessRisk.addFactor("HIGH_RISK_INDUSTRY", 25);
        } else if (isModerateRiskIndustry(request.getIndustry())) {
            score += 15;
            businessRisk.addFactor("MODERATE_RISK_INDUSTRY", 15);
        }
        
        // Business age factor
        if (request.getOnboardingDate() != null) {
            long monthsInBusiness = ChronoUnit.DAYS.between(
                request.getOnboardingDate(), Instant.now()) / 30;
            
            if (monthsInBusiness < 6) {
                score += 20;
                businessRisk.addFactor("NEW_BUSINESS", 20);
            } else if (monthsInBusiness < 12) {
                score += 10;
                businessRisk.addFactor("YOUNG_BUSINESS", 10);
            } else if (monthsInBusiness > 60) {
                score -= 10;
                businessRisk.addFactor("ESTABLISHED_BUSINESS", -10);
            }
        }
        
        // Business type factor
        if ("SOLE_PROPRIETORSHIP".equals(request.getBusinessType())) {
            score += 15;
            businessRisk.addFactor("SOLE_PROPRIETORSHIP", 15);
        } else if ("CORPORATION".equals(request.getBusinessType())) {
            score -= 5;
            businessRisk.addFactor("CORPORATION", -5);
        }
        
        // Geographic risk factor
        CountryRiskData countryRisk = riskDataService.getCountryRiskData(request.getCountry());
        if (countryRisk.isHighRisk()) {
            score += 20;
            businessRisk.addFactor("HIGH_RISK_COUNTRY", 20);
        } else if (countryRisk.getRiskLevel().equals("MEDIUM")) {
            score += 10;
            businessRisk.addFactor("MEDIUM_RISK_COUNTRY", 10);
        }
        
        businessRisk.setScore(Math.max(0, Math.min(score, 100)));
        return businessRisk;
    }

    private TransactionRiskScore calculateTransactionRisk(MerchantRiskScoringRequest request) {
        TransactionRiskScore transactionRisk = new TransactionRiskScore();
        int score = 50; // Base score
        
        MerchantActivitySummary activity = request.getActivitySummary();
        if (activity == null) {
            transactionRisk.setScore(50);
            return transactionRisk;
        }
        
        // Transaction volume volatility
        if (activity.getVolumeVolatility() > 0.8) {
            score += 20;
            transactionRisk.addFactor("HIGH_VOLUME_VOLATILITY", 20);
        } else if (activity.getVolumeVolatility() > 0.5) {
            score += 10;
            transactionRisk.addFactor("MODERATE_VOLUME_VOLATILITY", 10);
        }
        
        // Average transaction size vs industry
        BigDecimal industryAverage = benchmarkService.getIndustryAverageTransactionSize(request.getIndustry());
        if (industryAverage != null && activity.getAverageTransactionSize() != null) {
            double ratio = (double) MoneyMath.toMLFeature(
                activity.getAverageTransactionSize()
                    .divide(industryAverage, 2, RoundingMode.HALF_UP)
            );

            if (ratio > 5.0) {
                score += 25;
                transactionRisk.addFactor("VERY_HIGH_TRANSACTION_SIZE", 25);
            } else if (ratio > 2.0) {
                score += 15;
                transactionRisk.addFactor("HIGH_TRANSACTION_SIZE", 15);
            } else if (ratio < 0.2) {
                score += 10;
                transactionRisk.addFactor("VERY_LOW_TRANSACTION_SIZE", 10);
            }
        }
        
        // Transaction frequency patterns
        if (activity.hasUnusualTimePatterns()) {
            score += 15;
            transactionRisk.addFactor("UNUSUAL_TIME_PATTERNS", 15);
        }
        
        // Geographic spread
        if (activity.getCountryCount() > 10) {
            score += 15;
            transactionRisk.addFactor("HIGH_GEOGRAPHIC_SPREAD", 15);
        } else if (activity.getCountryCount() > 5) {
            score += 10;
            transactionRisk.addFactor("MODERATE_GEOGRAPHIC_SPREAD", 10);
        }
        
        // Payment method diversity
        if (activity.getPaymentMethodCount() > 5) {
            score += 10;
            transactionRisk.addFactor("HIGH_PAYMENT_METHOD_DIVERSITY", 10);
        }
        
        transactionRisk.setScore(Math.max(0, Math.min(score, 100)));
        return transactionRisk;
    }

    private FinancialRiskScore calculateFinancialRisk(MerchantRiskScoringRequest request) {
        FinancialRiskScore financialRisk = new FinancialRiskScore();
        int score = 50; // Base score
        
        MerchantActivitySummary activity = request.getActivitySummary();
        if (activity == null) {
            financialRisk.setScore(50);
            return financialRisk;
        }
        
        // Chargeback rate
        if (activity.getChargebackRate() > 0.02) { // 2%
            score += 30;
            financialRisk.addFactor("VERY_HIGH_CHARGEBACK_RATE", 30);
        } else if (activity.getChargebackRate() > 0.01) { // 1%
            score += 20;
            financialRisk.addFactor("HIGH_CHARGEBACK_RATE", 20);
        } else if (activity.getChargebackRate() > 0.005) { // 0.5%
            score += 10;
            financialRisk.addFactor("MODERATE_CHARGEBACK_RATE", 10);
        }
        
        // Refund rate
        if (activity.getRefundRate() > 0.10) { // 10%
            score += 20;
            financialRisk.addFactor("HIGH_REFUND_RATE", 20);
        } else if (activity.getRefundRate() > 0.05) { // 5%
            score += 10;
            financialRisk.addFactor("MODERATE_REFUND_RATE", 10);
        }
        
        // Failed transaction rate
        if (activity.getFailureRate() > 0.15) { // 15%
            score += 15;
            financialRisk.addFactor("HIGH_FAILURE_RATE", 15);
        } else if (activity.getFailureRate() > 0.10) { // 10%
            score += 10;
            financialRisk.addFactor("MODERATE_FAILURE_RATE", 10);
        }
        
        // Processing volume growth
        if (activity.getVolumeGrowthRate() > 5.0) { // 500% growth
            score += 20;
            financialRisk.addFactor("EXPLOSIVE_VOLUME_GROWTH", 20);
        } else if (activity.getVolumeGrowthRate() > 2.0) { // 200% growth
            score += 10;
            financialRisk.addFactor("HIGH_VOLUME_GROWTH", 10);
        } else if (activity.getVolumeGrowthRate() < -0.5) { // 50% decline
            score += 15;
            financialRisk.addFactor("VOLUME_DECLINE", 15);
        }
        
        // Reserve requirements
        BigDecimal reserveAmount = merchantService.getReserveAmount(request.getMerchantId());
        if (reserveAmount != null && activity.getMonthlyVolume() != null) {
            double reserveRatio = (double) MoneyMath.toMLFeature(
                reserveAmount.divide(activity.getMonthlyVolume(), 4, RoundingMode.HALF_UP)
            );

            if (reserveRatio > 0.20) { // 20% reserve
                score += 25;
                financialRisk.addFactor("HIGH_RESERVE_RATIO", 25);
            } else if (reserveRatio > 0.10) { // 10% reserve
                score += 15;
                financialRisk.addFactor("MODERATE_RESERVE_RATIO", 15);
            }
        }
        
        financialRisk.setScore(Math.max(0, Math.min(score, 100)));
        return financialRisk;
    }

    private ComplianceRiskScore calculateComplianceRisk(MerchantRiskScoringRequest request) {
        ComplianceRiskScore complianceRisk = new ComplianceRiskScore();
        int score = 50; // Base score
        
        // Compliance violation history
        List<ComplianceViolation> violations = complianceService.getViolationHistory(
            request.getMerchantId(),
            Instant.now().minus(365, ChronoUnit.DAYS)
        );
        
        if (violations.size() > 5) {
            score += 30;
            complianceRisk.addFactor("MULTIPLE_VIOLATIONS", 30);
        } else if (violations.size() > 2) {
            score += 20;
            complianceRisk.addFactor("SOME_VIOLATIONS", 20);
        } else if (violations.size() > 0) {
            score += 10;
            complianceRisk.addFactor("FEW_VIOLATIONS", 10);
        }
        
        // KYC completeness
        KYCStatus kycStatus = complianceService.getKYCStatus(request.getMerchantId());
        if (kycStatus.getCompleteness() < 0.8) {
            score += 25;
            complianceRisk.addFactor("INCOMPLETE_KYC", 25);
        } else if (kycStatus.getCompleteness() < 0.9) {
            score += 15;
            complianceRisk.addFactor("PARTIAL_KYC", 15);
        }
        
        // Sanctions screening
        SanctionsScreeningResult sanctionsResult = complianceService.getLatestSanctionsScreening(
            request.getMerchantId()
        );
        if (sanctionsResult.hasMatches()) {
            score += 40;
            complianceRisk.addFactor("SANCTIONS_MATCHES", 40);
        } else if (sanctionsResult.hasPotentialMatches()) {
            score += 20;
            complianceRisk.addFactor("POTENTIAL_SANCTIONS_MATCHES", 20);
        }
        
        // PCI compliance
        PCIComplianceStatus pciStatus = complianceService.getPCIStatus(request.getMerchantId());
        if (!pciStatus.isCompliant()) {
            score += 20;
            complianceRisk.addFactor("PCI_NON_COMPLIANCE", 20);
        } else if (pciStatus.hasWarnings()) {
            score += 10;
            complianceRisk.addFactor("PCI_WARNINGS", 10);
        }
        
        complianceRisk.setScore(Math.max(0, Math.min(score, 100)));
        return complianceRisk;
    }

    private OperationalRiskScore calculateOperationalRisk(MerchantRiskScoringRequest request) {
        OperationalRiskScore operationalRisk = new OperationalRiskScore();
        int score = 50; // Base score
        
        // System integration quality
        IntegrationHealth integrationHealth = systemService.getIntegrationHealth(request.getMerchantId());
        if (integrationHealth.getErrorRate() > 0.05) { // 5% error rate
            score += 20;
            operationalRisk.addFactor("HIGH_INTEGRATION_ERROR_RATE", 20);
        } else if (integrationHealth.getErrorRate() > 0.02) { // 2% error rate
            score += 10;
            operationalRisk.addFactor("MODERATE_INTEGRATION_ERROR_RATE", 10);
        }
        
        // API usage patterns
        APIUsageMetrics apiMetrics = systemService.getAPIUsageMetrics(
            request.getMerchantId(),
            Instant.now().minus(30, ChronoUnit.DAYS)
        );
        if (apiMetrics.hasUnusualPatterns()) {
            score += 15;
            operationalRisk.addFactor("UNUSUAL_API_PATTERNS", 15);
        }
        
        // Support ticket volume
        SupportMetrics supportMetrics = supportService.getSupportMetrics(
            request.getMerchantId(),
            Instant.now().minus(90, ChronoUnit.DAYS)
        );
        if (supportMetrics.getTicketCount() > 20) {
            score += 15;
            operationalRisk.addFactor("HIGH_SUPPORT_VOLUME", 15);
        } else if (supportMetrics.getTicketCount() > 10) {
            score += 10;
            operationalRisk.addFactor("MODERATE_SUPPORT_VOLUME", 10);
        }
        
        // Settlement timing
        SettlementMetrics settlementMetrics = settlementService.getSettlementMetrics(
            request.getMerchantId(),
            Instant.now().minus(30, ChronoUnit.DAYS)
        );
        if (settlementMetrics.getAverageSettlementTime() > 5) { // 5 days
            score += 15;
            operationalRisk.addFactor("SLOW_SETTLEMENT", 15);
        }
        
        operationalRisk.setScore(Math.max(0, Math.min(score, 100)));
        return operationalRisk;
    }

    private ReputationalRiskScore calculateReputationalRisk(MerchantRiskScoringRequest request) {
        ReputationalRiskScore reputationalRisk = new ReputationalRiskScore();
        int score = 50; // Base score
        
        // Online reputation analysis
        OnlineReputationData reputation = reputationService.getOnlineReputation(request.getMerchantId());
        if (reputation.getNegativeReviewPercentage() > 0.4) { // 40% negative
            score += 25;
            reputationalRisk.addFactor("HIGH_NEGATIVE_REVIEWS", 25);
        } else if (reputation.getNegativeReviewPercentage() > 0.2) { // 20% negative
            score += 15;
            reputationalRisk.addFactor("MODERATE_NEGATIVE_REVIEWS", 15);
        }
        
        // Social media sentiment
        if (reputation.getSocialMediaSentiment() < -0.5) {
            score += 20;
            reputationalRisk.addFactor("NEGATIVE_SOCIAL_SENTIMENT", 20);
        } else if (reputation.getSocialMediaSentiment() < -0.2) {
            score += 10;
            reputationalRisk.addFactor("MIXED_SOCIAL_SENTIMENT", 10);
        }
        
        // Industry watchlist status
        if (reputation.isOnIndustryWatchlist()) {
            score += 30;
            reputationalRisk.addFactor("INDUSTRY_WATCHLIST", 30);
        }
        
        // News coverage analysis
        if (reputation.hasNegativeNewsCorrelation()) {
            score += 20;
            reputationalRisk.addFactor("NEGATIVE_NEWS_COVERAGE", 20);
        }
        
        reputationalRisk.setScore(Math.max(0, Math.min(score, 100)));
        return reputationalRisk;
    }

    private int calculateOverallScore(MerchantRiskScore riskScore, String industry) {
        // Industry-specific weight adjustments
        Map<String, Double> weights = getIndustryWeights(industry);
        
        double weightedScore = 
            riskScore.getBusinessRisk().getScore() * weights.get("BUSINESS") +
            riskScore.getTransactionRisk().getScore() * weights.get("TRANSACTION") +
            riskScore.getFinancialRisk().getScore() * weights.get("FINANCIAL") +
            riskScore.getComplianceRisk().getScore() * weights.get("COMPLIANCE") +
            riskScore.getOperationalRisk().getScore() * weights.get("OPERATIONAL") +
            riskScore.getReputationalRisk().getScore() * weights.get("REPUTATIONAL");
        
        return (int) Math.round(weightedScore);
    }

    private Map<String, Double> getIndustryWeights(String industry) {
        Map<String, Double> defaultWeights = Map.of(
            "BUSINESS", 0.15,
            "TRANSACTION", 0.20,
            "FINANCIAL", 0.25,
            "COMPLIANCE", 0.20,
            "OPERATIONAL", 0.10,
            "REPUTATIONAL", 0.10
        );
        
        // Industry-specific adjustments
        if (HIGH_RISK_INDUSTRIES.contains(industry)) {
            return Map.of(
                "BUSINESS", 0.20,
                "TRANSACTION", 0.15,
                "FINANCIAL", 0.20,
                "COMPLIANCE", 0.30,
                "OPERATIONAL", 0.10,
                "REPUTATIONAL", 0.05
            );
        }
        
        return defaultWeights;
    }

    private String determineRiskLevel(int overallScore) {
        if (overallScore >= 80) return "CRITICAL";
        if (overallScore >= 65) return "HIGH";
        if (overallScore >= 45) return "MEDIUM";
        if (overallScore >= 25) return "LOW";
        return "MINIMAL";
    }

    private String determineRiskCategory(MerchantRiskScore riskScore, String industry) {
        // Determine primary risk driver
        Map<String, Integer> riskFactors = Map.of(
            "BUSINESS", riskScore.getBusinessRisk().getScore(),
            "TRANSACTION", riskScore.getTransactionRisk().getScore(),
            "FINANCIAL", riskScore.getFinancialRisk().getScore(),
            "COMPLIANCE", riskScore.getComplianceRisk().getScore(),
            "OPERATIONAL", riskScore.getOperationalRisk().getScore(),
            "REPUTATIONAL", riskScore.getReputationalRisk().getScore()
        );
        
        String primaryDriver = riskFactors.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("MIXED");
        
        return primaryDriver + "_DRIVEN";
    }

    private String calculateRiskTrend(String merchantId, int currentScore) {
        List<MerchantRiskScore> historicalScores = riskRepository.getHistoricalScores(
            merchantId,
            Instant.now().minus(90, ChronoUnit.DAYS),
            5 // Last 5 scores
        );
        
        if (historicalScores.size() < 2) {
            return "STABLE";
        }
        
        int previousScore = historicalScores.get(0).getOverallScore();
        int scoreDifference = currentScore - previousScore;
        
        if (scoreDifference > 10) return "INCREASING";
        if (scoreDifference < -10) return "DECREASING";
        return "STABLE";
    }

    private List<String> generateRiskInsights(MerchantRiskScore riskScore, MerchantRiskScoringRequest request) {
        List<String> insights = new ArrayList<>();
        
        // High-impact factors
        if (riskScore.getFinancialRisk().getScore() > 75) {
            insights.add("High financial risk driven by chargebacks or refunds");
        }
        
        if (riskScore.getComplianceRisk().getScore() > 70) {
            insights.add("Compliance concerns require immediate attention");
        }
        
        if (riskScore.getBusinessRisk().getScore() > 70 && 
            HIGH_RISK_INDUSTRIES.contains(request.getIndustry())) {
            insights.add("High-risk industry requires enhanced monitoring");
        }
        
        // Trend analysis
        if ("INCREASING".equals(riskScore.getRiskTrend())) {
            insights.add("Risk trend is increasing - review controls");
        }
        
        return insights;
    }

    private List<String> generateRecommendations(MerchantRiskScore riskScore, MerchantRiskScoringRequest request) {
        List<String> recommendations = new ArrayList<>();
        
        if (riskScore.getOverallScore() > 80) {
            recommendations.add("IMMEDIATE_REVIEW_REQUIRED");
            recommendations.add("ENHANCED_MONITORING");
            recommendations.add("LIMIT_TRANSACTION_VOLUMES");
        } else if (riskScore.getOverallScore() > 65) {
            recommendations.add("INCREASE_MONITORING_FREQUENCY");
            recommendations.add("REVIEW_TRANSACTION_LIMITS");
        }
        
        // Specific recommendations based on risk factors
        if (riskScore.getFinancialRisk().getScore() > 70) {
            recommendations.add("CHARGEBACK_PREVENTION_PROGRAM");
            recommendations.add("INCREASE_RESERVES");
        }
        
        if (riskScore.getComplianceRisk().getScore() > 70) {
            recommendations.add("COMPLIANCE_REMEDIATION");
            recommendations.add("ENHANCED_KYC_REVIEW");
        }
        
        return recommendations;
    }

    private void applyRiskControls(MerchantRiskScoringRequest request, MerchantRiskScore riskScore) {
        List<String> controlsApplied = new ArrayList<>();
        
        // High risk controls
        if (riskScore.getOverallScore() > 80) {
            riskControlService.applyHighRiskControls(request.getMerchantId());
            controlsApplied.add("HIGH_RISK_CONTROLS");
            
            // Transaction limits
            riskControlService.updateTransactionLimits(
                request.getMerchantId(),
                calculateRiskAdjustedLimits(riskScore)
            );
            controlsApplied.add("TRANSACTION_LIMITS_REDUCED");
        }
        
        // Medium risk controls
        else if (riskScore.getOverallScore() > 65) {
            riskControlService.applyMediumRiskControls(request.getMerchantId());
            controlsApplied.add("MEDIUM_RISK_CONTROLS");
        }
        
        // Specific risk-based controls
        if (riskScore.getFinancialRisk().getScore() > 75) {
            riskControlService.increaseReserveRequirements(request.getMerchantId());
            controlsApplied.add("INCREASED_RESERVES");
        }
        
        if (riskScore.getComplianceRisk().getScore() > 70) {
            riskControlService.enableEnhancedCompliance(request.getMerchantId());
            controlsApplied.add("ENHANCED_COMPLIANCE");
        }
        
        // Log applied controls
        auditService.logRiskControlsApplied(
            request.getMerchantId(),
            riskScore.getOverallScore(),
            controlsApplied
        );
    }

    private Map<String, BigDecimal> calculateRiskAdjustedLimits(MerchantRiskScore riskScore) {
        Map<String, BigDecimal> limits = new HashMap<>();
        
        // Base limits
        BigDecimal baseDailyLimit = new BigDecimal("100000");
        BigDecimal baseTransactionLimit = new BigDecimal("10000");
        
        // Risk adjustment factor
        double adjustmentFactor = 1.0 - (riskScore.getOverallScore() / 200.0); // Max 50% reduction
        
        limits.put("DAILY_LIMIT", baseDailyLimit.multiply(new BigDecimal(adjustmentFactor)));
        limits.put("TRANSACTION_LIMIT", baseTransactionLimit.multiply(new BigDecimal(adjustmentFactor)));
        
        return limits;
    }

    private void updateMerchantProfile(MerchantRiskScoringRequest request, MerchantRiskScore riskScore) {
        // Update risk profile
        MerchantRiskProfile profile = MerchantRiskProfile.builder()
            .merchantId(request.getMerchantId())
            .overallScore(riskScore.getOverallScore())
            .riskLevel(riskScore.getRiskLevel())
            .riskCategory(riskScore.getRiskCategory())
            .riskTrend(riskScore.getRiskTrend())
            .lastUpdated(Instant.now())
            .scoringTrigger(request.getTriggerType())
            .nextScheduledScoring(calculateNextScoringDate(riskScore.getOverallScore()))
            .build();
        
        riskRepository.saveRiskProfile(profile);
        
        // Update merchant risk flags
        profileService.updateRiskFlags(request.getMerchantId(), riskScore);
    }

    private Instant calculateNextScoringDate(int riskScore) {
        // More frequent scoring for higher risk merchants
        int daysUntilNext;
        if (riskScore > 80) {
            daysUntilNext = 7; // Weekly
        } else if (riskScore > 65) {
            daysUntilNext = 14; // Bi-weekly
        } else if (riskScore > 45) {
            daysUntilNext = 30; // Monthly
        } else {
            daysUntilNext = 90; // Quarterly
        }
        
        return Instant.now().plus(daysUntilNext, ChronoUnit.DAYS);
    }

    private void performBenchmarkAnalysis(MerchantRiskScoringRequest request, MerchantRiskScore riskScore) {
        // Compare against industry benchmarks
        IndustryBenchmark benchmark = benchmarkService.getIndustryBenchmark(request.getIndustry());
        
        BenchmarkComparison comparison = BenchmarkComparison.builder()
            .merchantId(request.getMerchantId())
            .industry(request.getIndustry())
            .merchantScore(riskScore.getOverallScore())
            .industryAverage(benchmark.getAverageRiskScore())
            .industryPercentile(benchmark.calculatePercentile(riskScore.getOverallScore()))
            .comparisonDate(Instant.now())
            .build();
        
        benchmarkService.saveBenchmarkComparison(comparison);
        
        // Update industry statistics
        benchmarkService.updateIndustryStatistics(request.getIndustry(), riskScore);
    }

    private void triggerAutomatedActions(MerchantRiskScoringRequest request, MerchantRiskScore riskScore, 
                                       ScoringTrigger trigger) {
        List<String> workflows = getAutomatedWorkflows(riskScore.getRiskLevel(), request.getTriggerType());
        
        for (String workflowType : workflows) {
            CompletableFuture.runAsync(() -> {
                try {
                    workflowService.triggerWorkflow(workflowType, request, riskScore);
                } catch (Exception e) {
                    log.error("Failed to trigger workflow {} for merchant risk scoring {}", 
                             workflowType, request.getRequestId(), e);
                }
            });
        }
    }

    private List<String> getAutomatedWorkflows(String riskLevel, String triggerType) {
        Map<String, List<String>> workflowMapping = Map.of(
            "CRITICAL", Arrays.asList("CRITICAL_RISK_INVESTIGATION", "EXECUTIVE_NOTIFICATION", "IMMEDIATE_REVIEW"),
            "HIGH", Arrays.asList("HIGH_RISK_REVIEW", "ENHANCED_MONITORING", "RISK_MITIGATION"),
            "MEDIUM", Arrays.asList("STANDARD_REVIEW", "MONITORING_UPDATE"),
            "LOW", Arrays.asList("PROFILE_UPDATE", "SCHEDULED_REVIEW")
        );
        
        return workflowMapping.getOrDefault(riskLevel, Arrays.asList("STANDARD_REVIEW"));
    }

    private void sendRiskScoringNotifications(MerchantRiskScoringRequest request, MerchantRiskScore riskScore, 
                                            ScoringTrigger trigger) {
        
        Map<String, Object> notificationData = Map.of(
            "merchantId", request.getMerchantId(),
            "merchantName", request.getMerchantName(),
            "overallScore", riskScore.getOverallScore(),
            "riskLevel", riskScore.getRiskLevel(),
            "riskCategory", riskScore.getRiskCategory(),
            "triggerType", request.getTriggerType(),
            "scoringDate", riskScore.getScoringDate()
        );
        
        // Critical risk notifications
        if ("CRITICAL".equals(riskScore.getRiskLevel())) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendCriticalRiskAlert(notificationData);
                notificationService.sendExecutiveAlert("CRITICAL_MERCHANT_RISK", notificationData);
            });
        }
        
        // High risk notifications
        if ("HIGH".equals(riskScore.getRiskLevel())) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendHighRiskAlert(notificationData);
            });
        }
        
        // Team notifications
        CompletableFuture.runAsync(() -> {
            notificationService.sendTeamNotification(
                "RISK_TEAM",
                "MERCHANT_RISK_UPDATE",
                notificationData
            );
        });
        
        // Merchant notifications for significant changes
        if (isSignificantRiskChange(request.getCurrentRiskScore(), riskScore.getOverallScore())) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendMerchantRiskNotification(
                    request.getMerchantId(),
                    notificationData
                );
            });
        }
    }

    private boolean isSignificantRiskChange(Integer previousScore, int currentScore) {
        if (previousScore == null) return false;
        return Math.abs(currentScore - previousScore) >= 15; // 15 point change
    }

    private void updateMonitoringSystems(MerchantRiskScoringRequest request, MerchantRiskScore riskScore) {
        // Update risk monitoring dashboard
        dashboardService.updateMerchantRiskDashboard(request.getMerchantId(), riskScore);
        
        // Update fraud monitoring systems
        fraudMonitoringService.updateMerchantRiskMetrics(request.getMerchantId(), riskScore);
        
        // Update compliance monitoring
        complianceMonitoringService.updateMerchantRiskProfile(request.getMerchantId(), riskScore);
    }

    private void auditRiskScoring(MerchantRiskScoringRequest request, MerchantRiskScore riskScore, 
                                GenericKafkaEvent originalEvent) {
        auditService.auditMerchantRiskScoring(
            request.getRequestId(),
            request.getMerchantId(),
            request.getTriggerType(),
            riskScore.getOverallScore(),
            riskScore.getRiskLevel(),
            originalEvent.getEventId()
        );
    }

    private void recordScoringMetrics(MerchantRiskScoringRequest request, MerchantRiskScore riskScore, 
                                    long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        metricsService.recordRiskScoringMetrics(
            request.getTriggerType(),
            request.getIndustry(),
            riskScore.getOverallScore(),
            riskScore.getRiskLevel(),
            processingTime,
            processingTime <= SLA_THRESHOLD_MS
        );
        
        // Record scoring volume metrics
        metricsService.recordScoringVolumeMetrics(
            request.getTriggerType(),
            request.getIndustry()
        );
    }

    // Helper methods
    private boolean isModerateRiskIndustry(String industry) {
        Set<String> moderateRiskIndustries = Set.of(
            "TRAVEL", "ELECTRONICS", "DIGITAL_GOODS", "SUBSCRIPTION_SERVICES"
        );
        return moderateRiskIndustries.contains(industry);
    }

    // Error handling methods
    private void handleValidationError(GenericKafkaEvent event, ValidationException e) {
        auditService.logValidationError(event.getEventId(), e.getMessage());
        kafkaTemplate.send("merchant-risk-scoring-validation-errors", event);
    }

    private void handleCriticalRiskError(GenericKafkaEvent event, CriticalRiskException e) {
        emergencyAlertService.createEmergencyAlert(
            "CRITICAL_RISK_SCORING_FAILED",
            event.getPayload(),
            e.getMessage()
        );
        
        kafkaTemplate.send("merchant-risk-scoring-critical-failures", event);
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;
            
            log.warn("Retrying merchant risk scoring {} after {}ms (attempt {})", 
                    eventId, retryDelay, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            scheduledExecutor.schedule(() -> {
                kafkaTemplate.send("merchant-risk-scoring-retry", event);
            }, retryDelay, TimeUnit.MILLISECONDS);
            
            acknowledgment.acknowledge();
        } else {
            log.error("Max retries exceeded for merchant risk scoring {}, sending to DLQ", eventId);
            sendToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "merchant-risk-scoring");
        
        kafkaTemplate.send("merchant-risk-scoring.DLQ", event);
        
        alertingService.createDLQAlert(
            "merchant-risk-scoring",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handleMerchantRiskScoringFailure(GenericKafkaEvent event, String topic, int partition,
                                                long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for merchant risk scoring: {}", e.getMessage());
        
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
            "Merchant Risk Scoring Circuit Breaker Open",
            "Merchant risk scoring is failing. Risk management compromised."
        );
        
        acknowledgment.acknowledge();
    }

    // Helper extraction methods
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
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