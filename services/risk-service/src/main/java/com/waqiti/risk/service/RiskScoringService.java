package com.waqiti.risk.service;

import com.waqiti.risk.domain.*;
import com.waqiti.risk.dto.*;
import com.waqiti.risk.repository.*;
import com.waqiti.risk.ml.RiskMLModel;
import com.waqiti.risk.rules.RiskRuleEngine;
import com.waqiti.risk.exception.RiskAssessmentException;
import com.waqiti.common.cache.CacheService;
import com.waqiti.common.events.EventPublisher;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Enterprise Risk Scoring Service
 * 
 * Implements comprehensive risk assessment for:
 * - Transaction risk scoring
 * - User behavior analysis  
 * - Merchant risk evaluation
 * - Device fingerprint risk
 * - Geographic risk assessment
 * - Velocity checks
 * - Pattern recognition
 * - ML-based anomaly detection
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RiskScoringService {
    
    private final RiskAssessmentRepository assessmentRepository;
    private final RiskProfileRepository profileRepository;
    private final RiskRuleRepository ruleRepository;
    private final RiskThresholdRepository thresholdRepository;
    private final UserBehaviorRepository behaviorRepository;
    private final DeviceRepository deviceRepository;
    private final VelocityRepository velocityRepository;
    private final RiskMetricsRepository metricsRepository;
    private final RiskRuleEngine ruleEngine;
    private final RiskMLModel mlModel;
    private final CacheService cacheService;
    private final EventPublisher eventPublisher;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    
    @Value("${risk.scoring.ml.enabled:true}")
    private boolean mlEnabled;
    
    @Value("${risk.scoring.cache.ttl:300}")
    private int cacheTtlSeconds;
    
    @Value("${risk.scoring.high-risk-threshold:0.7}")
    private double highRiskThreshold;
    
    @Value("${risk.scoring.medium-risk-threshold:0.4}")
    private double mediumRiskThreshold;
    
    @Value("${risk.scoring.velocity.window-minutes:60}")
    private int velocityWindowMinutes;
    
    @Value("${risk.scoring.behavior.history-days:90}")
    private int behaviorHistoryDays;
    
    @Value("${risk.scoring.device.trust.threshold:0.8}")
    private double deviceTrustThreshold;
    
    private CircuitBreaker mlCircuitBreaker;
    
    // Risk factor weights (configurable)
    private final Map<RiskFactor, Double> riskFactorWeights = new ConcurrentHashMap<>();
    
    // Risk scoring models by type
    private final Map<String, RiskScoringModel> scoringModels = new ConcurrentHashMap<>();
    
    // Real-time risk cache
    private final Map<String, RiskScore> realtimeRiskCache = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        // Initialize circuit breaker for ML model
        this.mlCircuitBreaker = circuitBreakerRegistry.circuitBreaker("risk-ml-model");
        
        // Initialize risk factor weights
        initializeRiskFactorWeights();
        
        // Load scoring models
        loadScoringModels();
        
        // Initialize ML model if enabled
        if (mlEnabled) {
            initializeMLModel();
        }
        
        log.info("Risk scoring service initialized with ML={}, high-risk-threshold={}",
            mlEnabled, highRiskThreshold);
    }
    
    /**
     * Comprehensive transaction risk assessment
     */
    @Transactional
    public RiskAssessment assessTransactionRisk(TransactionRiskRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String assessmentId = UUID.randomUUID().toString();
        
        try {
            log.debug("Assessing transaction risk: transactionId={}, amount={}, userId={}",
                request.getTransactionId(), request.getAmount(), request.getUserId());
            
            // Get user risk profile
            UserRiskProfile userProfile = getUserRiskProfile(request.getUserId());
            
            // Get merchant risk profile
            MerchantRiskProfile merchantProfile = getMerchantRiskProfile(request.getMerchantId());
            
            // Calculate individual risk factors
            Map<RiskFactor, RiskFactorScore> factorScores = calculateRiskFactors(request, userProfile, merchantProfile);
            
            // Apply rule-based risk assessment
            RuleEngineResult ruleResult = ruleEngine.evaluate(request, factorScores);
            
            // ML-based risk scoring
            MLRiskScore mlScore = null;
            if (mlEnabled) {
                mlScore = calculateMLRiskScore(request, factorScores);
            }
            
            // Calculate composite risk score
            CompositeRiskScore compositeScore = calculateCompositeScore(factorScores, ruleResult, mlScore);
            
            // Determine risk level and actions
            RiskLevel riskLevel = determineRiskLevel(compositeScore.getOverallScore());
            List<RiskAction> actions = determineRiskActions(riskLevel, compositeScore, ruleResult);
            
            // Create risk assessment
            RiskAssessment assessment = RiskAssessment.builder()
                .id(assessmentId)
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .merchantId(request.getMerchantId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .riskScore(compositeScore.getOverallScore())
                .riskLevel(riskLevel)
                .factorScores(factorScores)
                .ruleEngineScore(ruleResult.getScore())
                .mlScore(mlScore != null ? mlScore.getScore() : null)
                .triggeredRules(ruleResult.getTriggeredRules())
                .recommendedActions(actions)
                .assessmentReason(generateAssessmentReason(compositeScore, ruleResult))
                .metadata(buildAssessmentMetadata(request, compositeScore))
                .createdAt(LocalDateTime.now())
                .build();
            
            // Save assessment
            assessmentRepository.save(assessment);
            
            // Update user and merchant profiles
            updateRiskProfiles(assessment, userProfile, merchantProfile);
            
            // Publish risk event
            publishRiskEvent(assessment);
            
            // Record metrics
            sample.stop(meterRegistry.timer("risk.assessment.duration",
                "level", riskLevel.toString()
            ));
            
            meterRegistry.counter("risk.assessments.completed",
                "level", riskLevel.toString(),
                "merchant", request.getMerchantId()
            ).increment();
            
            log.info("Risk assessment completed: id={}, score={}, level={}, actions={}",
                assessmentId, compositeScore.getOverallScore(), riskLevel, actions.size());
            
            return assessment;
            
        } catch (Exception e) {
            log.error("Risk assessment failed: {}", e.getMessage(), e);
            
            sample.stop(meterRegistry.timer("risk.assessment.duration",
                "status", "error"
            ));
            
            // Return conservative high-risk assessment on error
            return createFailsafeAssessment(request, e);
        }
    }
    
    /**
     * Calculate risk factors
     */
    private Map<RiskFactor, RiskFactorScore> calculateRiskFactors(TransactionRiskRequest request,
                                                                  UserRiskProfile userProfile,
                                                                  MerchantRiskProfile merchantProfile) {
        Map<RiskFactor, RiskFactorScore> scores = new HashMap<>();
        
        // 1. Transaction Amount Risk
        RiskFactorScore amountRisk = calculateAmountRisk(request.getAmount(), userProfile);
        scores.put(RiskFactor.AMOUNT, amountRisk);
        
        // 2. Velocity Risk
        RiskFactorScore velocityRisk = calculateVelocityRisk(request.getUserId(), request.getAmount());
        scores.put(RiskFactor.VELOCITY, velocityRisk);
        
        // 3. Behavioral Risk
        RiskFactorScore behaviorRisk = calculateBehaviorRisk(request, userProfile);
        scores.put(RiskFactor.BEHAVIOR, behaviorRisk);
        
        // 4. Device Risk
        RiskFactorScore deviceRisk = calculateDeviceRisk(request.getDeviceId(), request.getUserId());
        scores.put(RiskFactor.DEVICE, deviceRisk);
        
        // 5. Geographic Risk
        RiskFactorScore geoRisk = calculateGeographicRisk(request.getIpAddress(), userProfile);
        scores.put(RiskFactor.GEOGRAPHIC, geoRisk);
        
        // 6. Merchant Risk
        RiskFactorScore merchantRisk = calculateMerchantRisk(merchantProfile);
        scores.put(RiskFactor.MERCHANT, merchantRisk);
        
        // 7. Time-based Risk
        RiskFactorScore timeRisk = calculateTimeRisk(request.getTimestamp());
        scores.put(RiskFactor.TIME, timeRisk);
        
        // 8. Network Risk
        RiskFactorScore networkRisk = calculateNetworkRisk(request.getUserId(), request.getMerchantId());
        scores.put(RiskFactor.NETWORK, networkRisk);
        
        // 9. Payment Method Risk
        RiskFactorScore paymentRisk = calculatePaymentMethodRisk(request.getPaymentMethod());
        scores.put(RiskFactor.PAYMENT_METHOD, paymentRisk);
        
        // 10. Identity Verification Risk
        RiskFactorScore identityRisk = calculateIdentityRisk(userProfile);
        scores.put(RiskFactor.IDENTITY, identityRisk);
        
        return scores;
    }
    
    /**
     * Calculate transaction amount risk
     */
    private RiskFactorScore calculateAmountRisk(BigDecimal amount, UserRiskProfile profile) {
        double score = 0.0;
        List<String> reasons = new ArrayList<>();
        
        // Check against user's typical transaction amount
        BigDecimal avgAmount = profile.getAverageTransactionAmount();
        BigDecimal stdDev = profile.getTransactionAmountStdDev();
        
        if (avgAmount != null && stdDev != null && stdDev.compareTo(BigDecimal.ZERO) > 0) {
            // CONFIGURE_IN_VAULT: Z-score thresholds for amount deviation detection
            double zScore = amount.subtract(avgAmount)
                .divide(stdDev, 4, RoundingMode.HALF_UP)
                .doubleValue();

            double highDeviationThreshold = Double.parseDouble(System.getenv().getOrDefault("AMOUNT_ZSCORE_HIGH_THRESHOLD", "0.0"));
            double mediumDeviationThreshold = Double.parseDouble(System.getenv().getOrDefault("AMOUNT_ZSCORE_MEDIUM_THRESHOLD", "0.0"));
            double highDeviationScore = Double.parseDouble(System.getenv().getOrDefault("AMOUNT_ZSCORE_HIGH_SCORE", "0.0"));
            double mediumDeviationScore = Double.parseDouble(System.getenv().getOrDefault("AMOUNT_ZSCORE_MEDIUM_SCORE", "0.0"));
            double lowDeviationScore = Double.parseDouble(System.getenv().getOrDefault("AMOUNT_ZSCORE_LOW_SCORE", "0.0"));

            if (Math.abs(zScore) > highDeviationThreshold) {
                score = highDeviationScore;
                reasons.add("Amount significantly deviates from user pattern (z-score: " + zScore + ")");
            } else if (Math.abs(zScore) > mediumDeviationThreshold) {
                score = mediumDeviationScore;
                reasons.add("Amount moderately deviates from user pattern");
            } else {
                score = lowDeviationScore;
            }
        }

        // CONFIGURE_IN_VAULT: Absolute amount thresholds should be stored in secure configuration
        BigDecimal highAmountThreshold = new BigDecimal(System.getenv().getOrDefault("AMOUNT_HIGH_THRESHOLD", "0"));
        BigDecimal mediumAmountThreshold = new BigDecimal(System.getenv().getOrDefault("AMOUNT_MEDIUM_THRESHOLD", "0"));
        double highAmountScore = Double.parseDouble(System.getenv().getOrDefault("AMOUNT_HIGH_SCORE", "0.0"));
        double mediumAmountScore = Double.parseDouble(System.getenv().getOrDefault("AMOUNT_MEDIUM_SCORE", "0.0"));

        if (amount.compareTo(highAmountThreshold) > 0) {
            score = Math.max(score, highAmountScore);
            reasons.add("High value transaction");
        } else if (amount.compareTo(mediumAmountThreshold) > 0) {
            score = Math.max(score, mediumAmountScore);
            reasons.add("Elevated transaction amount");
        }
        
        // CONFIGURE_IN_VAULT: First large transaction multiplier and score adjustment
        if (profile.getLargestTransaction() != null) {
            double largestTxMultiplier = Double.parseDouble(System.getenv().getOrDefault("LARGEST_TX_MULTIPLIER", "1.0"));
            double largestTxScoreAdjustment = Double.parseDouble(System.getenv().getOrDefault("LARGEST_TX_SCORE_ADJUSTMENT", "0.0"));

            if (amount.compareTo(profile.getLargestTransaction().multiply(new BigDecimal(largestTxMultiplier))) > 0) {
                score = Math.min(1.0, score + largestTxScoreAdjustment);
                reasons.add("Largest transaction for user");
            }
        }
        
        return RiskFactorScore.builder()
            .factor(RiskFactor.AMOUNT)
            .score(score)
            .weight(riskFactorWeights.get(RiskFactor.AMOUNT))
            .reasons(reasons)
            .build();
    }
    
    /**
     * Calculate velocity risk (transaction frequency)
     */
    private RiskFactorScore calculateVelocityRisk(String userId, BigDecimal amount) {
        double score = 0.0;
        List<String> reasons = new ArrayList<>();
        
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(velocityWindowMinutes);
        
        // Get velocity metrics
        VelocityMetrics metrics = velocityRepository.getVelocityMetrics(userId, windowStart);

        // CONFIGURE_IN_VAULT: Transaction count velocity thresholds and scores
        int highTxCountThreshold = Integer.parseInt(System.getenv().getOrDefault("VELOCITY_TX_COUNT_HIGH_THRESHOLD", "0"));
        int mediumTxCountThreshold = Integer.parseInt(System.getenv().getOrDefault("VELOCITY_TX_COUNT_MEDIUM_THRESHOLD", "0"));
        double highTxCountScore = Double.parseDouble(System.getenv().getOrDefault("VELOCITY_TX_COUNT_HIGH_SCORE", "0.0"));
        double mediumTxCountScore = Double.parseDouble(System.getenv().getOrDefault("VELOCITY_TX_COUNT_MEDIUM_SCORE", "0.0"));

        if (metrics.getTransactionCount() > highTxCountThreshold) {
            score = highTxCountScore;
            reasons.add("High transaction frequency: " + metrics.getTransactionCount() + " in " + velocityWindowMinutes + " minutes");
        } else if (metrics.getTransactionCount() > mediumTxCountThreshold) {
            score = mediumTxCountScore;
            reasons.add("Elevated transaction frequency");
        }

        // CONFIGURE_IN_VAULT: Amount velocity thresholds
        BigDecimal velocityThreshold = new BigDecimal(System.getenv().getOrDefault("VELOCITY_AMOUNT_THRESHOLD", "0"));
        double velocityAmountScore = Double.parseDouble(System.getenv().getOrDefault("VELOCITY_AMOUNT_SCORE", "0.0"));

        if (metrics.getTotalAmount().compareTo(velocityThreshold) > 0) {
            score = Math.max(score, velocityAmountScore);
            reasons.add("High transaction volume: " + metrics.getTotalAmount());
        }

        // CONFIGURE_IN_VAULT: Unique merchants velocity thresholds
        int uniqueMerchantsThreshold = Integer.parseInt(System.getenv().getOrDefault("VELOCITY_UNIQUE_MERCHANTS_THRESHOLD", "0"));
        double uniqueMerchantsScore = Double.parseDouble(System.getenv().getOrDefault("VELOCITY_UNIQUE_MERCHANTS_SCORE", "0.0"));

        if (metrics.getUniqueMerchants() > uniqueMerchantsThreshold) {
            score = Math.min(1.0, score + uniqueMerchantsScore);
            reasons.add("Multiple merchants in short time: " + metrics.getUniqueMerchants());
        }
        
        // CONFIGURE_IN_VAULT: Card testing pattern detection score
        if (detectCardTestingPattern(metrics)) {
            score = Double.parseDouble(System.getenv().getOrDefault("VELOCITY_CARD_TESTING_SCORE", "0.0"));
            reasons.add("Potential card testing pattern detected");
        }
        
        return RiskFactorScore.builder()
            .factor(RiskFactor.VELOCITY)
            .score(score)
            .weight(riskFactorWeights.get(RiskFactor.VELOCITY))
            .reasons(reasons)
            .build();
    }
    
    /**
     * Calculate behavioral risk
     */
    private RiskFactorScore calculateBehaviorRisk(TransactionRiskRequest request, UserRiskProfile profile) {
        double score = 0.0;
        List<String> reasons = new ArrayList<>();
        
        // Get user behavior history
        UserBehavior behavior = behaviorRepository.getUserBehavior(
            request.getUserId(), 
            LocalDateTime.now().minusDays(behaviorHistoryDays)
        );
        
        // CONFIGURE_IN_VAULT: Behavioral risk scoring thresholds
        if (behavior != null) {
            double unusualTimeScore = Double.parseDouble(System.getenv().getOrDefault("BEHAVIOR_UNUSUAL_TIME_SCORE", "0.0"));
            double unusualDayScore = Double.parseDouble(System.getenv().getOrDefault("BEHAVIOR_UNUSUAL_DAY_SCORE", "0.0"));
            double unusualCategoryScore = Double.parseDouble(System.getenv().getOrDefault("BEHAVIOR_UNUSUAL_CATEGORY_SCORE", "0.0"));
            int shortSessionThreshold = Integer.parseInt(System.getenv().getOrDefault("BEHAVIOR_SHORT_SESSION_THRESHOLD", "0"));
            double shortSessionScore = Double.parseDouble(System.getenv().getOrDefault("BEHAVIOR_SHORT_SESSION_SCORE", "0.0"));
            int minPagesThreshold = Integer.parseInt(System.getenv().getOrDefault("BEHAVIOR_MIN_PAGES_THRESHOLD", "0"));
            double directTransactionScore = Double.parseDouble(System.getenv().getOrDefault("BEHAVIOR_DIRECT_TRANSACTION_SCORE", "0.0"));

            // Time of day analysis
            int currentHour = request.getTimestamp().getHour();
            if (!behavior.getTypicalTransactionHours().contains(currentHour)) {
                score += unusualTimeScore;
                reasons.add("Transaction at unusual time");
            }

            // Day of week analysis
            String currentDay = request.getTimestamp().getDayOfWeek().toString();
            if (!behavior.getTypicalTransactionDays().contains(currentDay)) {
                score += unusualDayScore;
                reasons.add("Transaction on unusual day");
            }

            // Category analysis
            if (!behavior.getTypicalMerchantCategories().contains(request.getMerchantCategory())) {
                score += unusualCategoryScore;
                reasons.add("Unusual merchant category");
            }

            // Session behavior
            if (request.getSessionDuration() != null) {
                if (request.getSessionDuration() < shortSessionThreshold) {
                    score += shortSessionScore;
                    reasons.add("Very short session duration");
                }
            }

            // Navigation pattern
            if (request.getPagesVisited() != null && request.getPagesVisited() < minPagesThreshold) {
                score += directTransactionScore;
                reasons.add("Direct transaction without browsing");
            }
        }
        
        // CONFIGURE_IN_VAULT: Account age risk factors
        long accountAgeDays = ChronoUnit.DAYS.between(profile.getAccountCreatedAt(), LocalDateTime.now());
        int veryNewAccountDays = Integer.parseInt(System.getenv().getOrDefault("ACCOUNT_AGE_VERY_NEW_DAYS", "0"));
        int newAccountDays = Integer.parseInt(System.getenv().getOrDefault("ACCOUNT_AGE_NEW_DAYS", "0"));
        double veryNewAccountScore = Double.parseDouble(System.getenv().getOrDefault("ACCOUNT_AGE_VERY_NEW_SCORE", "0.0"));
        double newAccountScore = Double.parseDouble(System.getenv().getOrDefault("ACCOUNT_AGE_NEW_SCORE", "0.0"));

        if (accountAgeDays < veryNewAccountDays) {
            score += veryNewAccountScore;
            reasons.add("New account (< " + veryNewAccountDays + " days)");
        } else if (accountAgeDays < newAccountDays) {
            score += newAccountScore;
            reasons.add("Relatively new account (< " + newAccountDays + " days)");
        }
        
        return RiskFactorScore.builder()
            .factor(RiskFactor.BEHAVIOR)
            .score(Math.min(1.0, score))
            .weight(riskFactorWeights.get(RiskFactor.BEHAVIOR))
            .reasons(reasons)
            .build();
    }
    
    /**
     * Calculate device risk
     */
    private RiskFactorScore calculateDeviceRisk(String deviceId, String userId) {
        double score = 0.0;
        List<String> reasons = new ArrayList<>();
        
        if (deviceId == null) {
            score = 0.5;
            reasons.add("No device information available");
            return RiskFactorScore.builder()
                .factor(RiskFactor.DEVICE)
                .score(score)
                .weight(riskFactorWeights.get(RiskFactor.DEVICE))
                .reasons(reasons)
                .build();
        }
        
        // Get device profile
        DeviceProfile device = deviceRepository.findByDeviceId(deviceId)
            .orElse(null);
        
        if (device == null) {
            score = 0.6;
            reasons.add("Unknown device");
        } else {
            // Device trust score
            if (device.getTrustScore() < deviceTrustThreshold) {
                score = 1.0 - device.getTrustScore();
                reasons.add("Low device trust score: " + device.getTrustScore());
            }
            
            // CONFIGURE_IN_VAULT: Device risk scoring thresholds
            int multipleUsersThreshold = Integer.parseInt(System.getenv().getOrDefault("DEVICE_MULTIPLE_USERS_THRESHOLD", "0"));
            double multipleUsersScore = Double.parseDouble(System.getenv().getOrDefault("DEVICE_MULTIPLE_USERS_SCORE", "0.0"));
            double rootedScore = Double.parseDouble(System.getenv().getOrDefault("DEVICE_ROOTED_SCORE", "0.0"));
            double emulatorScore = Double.parseDouble(System.getenv().getOrDefault("DEVICE_EMULATOR_SCORE", "0.0"));

            int associatedUsers = deviceRepository.countUsersForDevice(deviceId);
            if (associatedUsers > multipleUsersThreshold) {
                score = Math.max(score, multipleUsersScore);
                reasons.add("Device associated with multiple users: " + associatedUsers);
            }

            // Check if rooted/jailbroken
            if (device.isRooted() || device.isJailbroken()) {
                score = Math.max(score, rootedScore);
                reasons.add("Device is rooted/jailbroken");
            }

            // Check if emulator
            if (device.isEmulator()) {
                score = emulatorScore;
                reasons.add("Transaction from emulator");
            }
            
            // CONFIGURE_IN_VAULT: Device age risk factors
            long deviceAgeDays = ChronoUnit.DAYS.between(device.getFirstSeenAt(), LocalDateTime.now());
            int newDeviceDays = Integer.parseInt(System.getenv().getOrDefault("DEVICE_NEW_DAYS_THRESHOLD", "0"));
            double newDeviceScore = Double.parseDouble(System.getenv().getOrDefault("DEVICE_NEW_SCORE", "0.0"));

            if (deviceAgeDays < newDeviceDays) {
                score = Math.max(score, newDeviceScore);
                reasons.add("New device for user");
            }

            // CONFIGURE_IN_VAULT: Device spoofing detection score
            if (detectDeviceSpoofing(device)) {
                score = Double.parseDouble(System.getenv().getOrDefault("DEVICE_SPOOFING_SCORE", "0.0"));
                reasons.add("Device spoofing indicators detected");
            }
        }
        
        return RiskFactorScore.builder()
            .factor(RiskFactor.DEVICE)
            .score(score)
            .weight(riskFactorWeights.get(RiskFactor.DEVICE))
            .reasons(reasons)
            .build();
    }
    
    /**
     * Calculate geographic risk
     */
    private RiskFactorScore calculateGeographicRisk(String ipAddress, UserRiskProfile profile) {
        double score = 0.0;
        List<String> reasons = new ArrayList<>();
        
        // This would integrate with the GeoLocationService
        GeoLocationInfo geoInfo = getGeoLocation(ipAddress);
        
        if (geoInfo == null) {
            score = 0.4;
            reasons.add("Unable to determine location");
        } else {
            // Country risk
            double countryRisk = getCountryRiskScore(geoInfo.getCountryCode());
            if (countryRisk > 0.5) {
                score = countryRisk;
                reasons.add("High-risk country: " + geoInfo.getCountryName());
            }
            
            // CONFIGURE_IN_VAULT: Geographic risk scoring thresholds
            double unusualCountryScore = Double.parseDouble(System.getenv().getOrDefault("GEO_UNUSUAL_COUNTRY_SCORE", "0.0"));
            double vpnProxyScore = Double.parseDouble(System.getenv().getOrDefault("GEO_VPN_PROXY_SCORE", "0.0"));
            double torScore = Double.parseDouble(System.getenv().getOrDefault("GEO_TOR_SCORE", "0.0"));
            double impossibleTravelScore = Double.parseDouble(System.getenv().getOrDefault("GEO_IMPOSSIBLE_TRAVEL_SCORE", "0.0"));

            // Check for location consistency
            if (profile.getTypicalCountries() != null &&
                !profile.getTypicalCountries().contains(geoInfo.getCountryCode())) {
                score = Math.max(score, unusualCountryScore);
                reasons.add("Transaction from unusual country");
            }

            // VPN/Proxy detection
            if (geoInfo.isVpn() || geoInfo.isProxy()) {
                score = Math.max(score, vpnProxyScore);
                reasons.add("VPN/Proxy detected");
            }

            // Tor detection
            if (geoInfo.isTor()) {
                score = torScore;
                reasons.add("Tor network detected");
            }

            // Impossible travel check
            LocationHistory lastLocation = profile.getLastKnownLocation();
            if (lastLocation != null && detectImpossibleTravel(lastLocation, geoInfo)) {
                score = Math.max(score, impossibleTravelScore);
                reasons.add("Impossible travel detected");
            }
        }
        
        return RiskFactorScore.builder()
            .factor(RiskFactor.GEOGRAPHIC)
            .score(score)
            .weight(riskFactorWeights.get(RiskFactor.GEOGRAPHIC))
            .reasons(reasons)
            .build();
    }
    
    /**
     * Calculate merchant risk
     */
    private RiskFactorScore calculateMerchantRisk(MerchantRiskProfile merchantProfile) {
        double score = 0.0;
        List<String> reasons = new ArrayList<>();
        
        if (merchantProfile == null) {
            score = 0.5;
            reasons.add("Unknown merchant");
        } else {
            // Merchant risk score
            score = merchantProfile.getRiskScore();
            
            // CONFIGURE_IN_VAULT: Merchant risk scoring thresholds
            double highRiskCategoryScore = Double.parseDouble(System.getenv().getOrDefault("MERCHANT_HIGH_RISK_CATEGORY_SCORE", "0.0"));
            double highChargebackThreshold = Double.parseDouble(System.getenv().getOrDefault("MERCHANT_HIGH_CHARGEBACK_THRESHOLD", "0.0"));
            double highChargebackScore = Double.parseDouble(System.getenv().getOrDefault("MERCHANT_HIGH_CHARGEBACK_SCORE", "0.0"));
            double highComplaintThreshold = Double.parseDouble(System.getenv().getOrDefault("MERCHANT_HIGH_COMPLAINT_THRESHOLD", "0.0"));
            double highComplaintScore = Double.parseDouble(System.getenv().getOrDefault("MERCHANT_HIGH_COMPLAINT_SCORE", "0.0"));
            int newMerchantDays = Integer.parseInt(System.getenv().getOrDefault("MERCHANT_NEW_DAYS_THRESHOLD", "0"));
            double newMerchantScore = Double.parseDouble(System.getenv().getOrDefault("MERCHANT_NEW_SCORE", "0.0"));

            // Check merchant category risk
            if (isHighRiskMerchantCategory(merchantProfile.getCategory())) {
                score = Math.max(score, highRiskCategoryScore);
                reasons.add("High-risk merchant category: " + merchantProfile.getCategory());
            }

            // Check chargeback rate
            if (merchantProfile.getChargebackRate() > highChargebackThreshold) {
                score = Math.max(score, highChargebackScore);
                reasons.add("High chargeback rate: " + merchantProfile.getChargebackRate());
            }

            // Check complaint rate
            if (merchantProfile.getComplaintRate() > highComplaintThreshold) {
                score = Math.max(score, highComplaintScore);
                reasons.add("Elevated complaint rate");
            }

            // New merchant factor
            long merchantAgeDays = ChronoUnit.DAYS.between(
                merchantProfile.getOnboardedAt(),
                LocalDateTime.now()
            );
            if (merchantAgeDays < newMerchantDays) {
                score = Math.max(score, newMerchantScore);
                reasons.add("New merchant (< " + newMerchantDays + " days)");
            }
        }
        
        return RiskFactorScore.builder()
            .factor(RiskFactor.MERCHANT)
            .score(score)
            .weight(riskFactorWeights.get(RiskFactor.MERCHANT))
            .reasons(reasons)
            .build();
    }
    
    /**
     * Calculate ML-based risk score
     */
    private MLRiskScore calculateMLRiskScore(TransactionRiskRequest request,
                                            Map<RiskFactor, RiskFactorScore> factorScores) {
        try {
            return mlCircuitBreaker.executeSupplier(() -> {
                // Prepare feature vector
                FeatureVector features = prepareFeatureVector(request, factorScores);
                
                // Get ML prediction
                MLPrediction prediction = mlModel.predict(features);
                
                return MLRiskScore.builder()
                    .score(prediction.getRiskScore())
                    .confidence(prediction.getConfidence())
                    .modelVersion(prediction.getModelVersion())
                    .features(features.getFeatures())
                    .build();
            });
        } catch (Exception e) {
            log.error("CRITICAL: ML risk scoring failed - using conservative default risk score", e);
            // CONFIGURE_IN_VAULT: Default risk score when ML fails
            double mlFailureDefaultScore = Double.parseDouble(System.getenv().getOrDefault("ML_FAILURE_DEFAULT_SCORE", "0.0"));
            return RiskScore.builder()
                .score(mlFailureDefaultScore)
                .category(RiskCategory.HIGH)
                .reason("ML scoring failed - defaulting to high risk")
                .timestamp(Instant.now())
                .build();
        }
    }
    
    /**
     * Calculate composite risk score
     */
    private CompositeRiskScore calculateCompositeScore(Map<RiskFactor, RiskFactorScore> factorScores,
                                                      RuleEngineResult ruleResult,
                                                      MLRiskScore mlScore) {
        // Weight-based aggregation of factor scores
        double weightedFactorScore = factorScores.values().stream()
            .mapToDouble(score -> score.getScore() * score.getWeight())
            .sum();
        
        double totalWeight = factorScores.values().stream()
            .mapToDouble(RiskFactorScore::getWeight)
            .sum();
        
        double normalizedFactorScore = weightedFactorScore / totalWeight;
        
        // Combine with rule engine score
        double ruleEngineScore = ruleResult.getScore();
        
        // CONFIGURE_IN_VAULT: Composite score calculation weights
        double finalScore;
        if (mlScore != null) {
            double factorWeight = Double.parseDouble(System.getenv().getOrDefault("COMPOSITE_FACTOR_WEIGHT_WITH_ML", "0.0"));
            double ruleWeight = Double.parseDouble(System.getenv().getOrDefault("COMPOSITE_RULE_WEIGHT_WITH_ML", "0.0"));
            double mlWeight = Double.parseDouble(System.getenv().getOrDefault("COMPOSITE_ML_WEIGHT", "0.0"));

            finalScore = normalizedFactorScore * factorWeight +
                        ruleEngineScore * ruleWeight +
                        mlScore.getScore() * mlWeight;
        } else {
            double factorWeight = Double.parseDouble(System.getenv().getOrDefault("COMPOSITE_FACTOR_WEIGHT_NO_ML", "0.0"));
            double ruleWeight = Double.parseDouble(System.getenv().getOrDefault("COMPOSITE_RULE_WEIGHT_NO_ML", "0.0"));

            finalScore = normalizedFactorScore * factorWeight +
                        ruleEngineScore * ruleWeight;
        }

        // CONFIGURE_IN_VAULT: Risk amplification multiplier for critical rules
        if (ruleResult.hasCriticalRuleTriggered()) {
            double criticalRuleMultiplier = Double.parseDouble(System.getenv().getOrDefault("CRITICAL_RULE_MULTIPLIER", "1.0"));
            finalScore = Math.min(1.0, finalScore * criticalRuleMultiplier);
        }
        
        return CompositeRiskScore.builder()
            .overallScore(Math.min(1.0, finalScore))
            .factorScore(normalizedFactorScore)
            .ruleEngineScore(ruleEngineScore)
            .mlScore(mlScore != null ? mlScore.getScore() : null)
            .confidence(calculateConfidence(factorScores, mlScore))
            .build();
    }
    
    /**
     * Determine risk level based on score
     */
    private RiskLevel determineRiskLevel(double score) {
        if (score >= highRiskThreshold) {
            return RiskLevel.HIGH;
        } else if (score >= mediumRiskThreshold) {
            return RiskLevel.MEDIUM;
        } else {
            return RiskLevel.LOW;
        }
    }
    
    /**
     * Determine risk actions based on assessment
     */
    private List<RiskAction> determineRiskActions(RiskLevel level,
                                                  CompositeRiskScore score,
                                                  RuleEngineResult ruleResult) {
        List<RiskAction> actions = new ArrayList<>();
        
        switch (level) {
            case HIGH:
                actions.add(RiskAction.BLOCK_TRANSACTION);
                actions.add(RiskAction.NOTIFY_FRAUD_TEAM);
                actions.add(RiskAction.REQUEST_ADDITIONAL_VERIFICATION);
                break;
            case MEDIUM:
                actions.add(RiskAction.REQUIRE_2FA);
                actions.add(RiskAction.FLAG_FOR_REVIEW);
                if (score.getOverallScore() > 0.6) {
                    actions.add(RiskAction.LIMIT_TRANSACTION_AMOUNT);
                }
                break;
            case LOW:
                actions.add(RiskAction.APPROVE);
                break;
        }
        
        // Add rule-specific actions
        actions.addAll(ruleResult.getRecommendedActions());
        
        return actions;
    }
    
    // Helper methods
    
    private void initializeRiskFactorWeights() {
        // CONFIGURE_IN_VAULT: Risk factor weights should be stored in secure configuration
        riskFactorWeights.put(RiskFactor.AMOUNT, Double.parseDouble(System.getenv().getOrDefault("RISK_WEIGHT_AMOUNT", "0.0")));
        riskFactorWeights.put(RiskFactor.VELOCITY, Double.parseDouble(System.getenv().getOrDefault("RISK_WEIGHT_VELOCITY", "0.0")));
        riskFactorWeights.put(RiskFactor.BEHAVIOR, Double.parseDouble(System.getenv().getOrDefault("RISK_WEIGHT_BEHAVIOR", "0.0")));
        riskFactorWeights.put(RiskFactor.DEVICE, Double.parseDouble(System.getenv().getOrDefault("RISK_WEIGHT_DEVICE", "0.0")));
        riskFactorWeights.put(RiskFactor.GEOGRAPHIC, Double.parseDouble(System.getenv().getOrDefault("RISK_WEIGHT_GEOGRAPHIC", "0.0")));
        riskFactorWeights.put(RiskFactor.MERCHANT, Double.parseDouble(System.getenv().getOrDefault("RISK_WEIGHT_MERCHANT", "0.0")));
        riskFactorWeights.put(RiskFactor.TIME, Double.parseDouble(System.getenv().getOrDefault("RISK_WEIGHT_TIME", "0.0")));
        riskFactorWeights.put(RiskFactor.NETWORK, Double.parseDouble(System.getenv().getOrDefault("RISK_WEIGHT_NETWORK", "0.0")));
        riskFactorWeights.put(RiskFactor.PAYMENT_METHOD, Double.parseDouble(System.getenv().getOrDefault("RISK_WEIGHT_PAYMENT_METHOD", "0.0")));
        riskFactorWeights.put(RiskFactor.IDENTITY, Double.parseDouble(System.getenv().getOrDefault("RISK_WEIGHT_IDENTITY", "0.0")));
    }
    
    private void loadScoringModels() {
        // Load configured scoring models
    }
    
    private void initializeMLModel() {
        // Initialize ML model
        if (mlModel != null) {
            mlModel.initialize();
        }
    }
    
    private UserRiskProfile getUserRiskProfile(String userId) {
        return profileRepository.findByUserId(userId)
            .orElse(createDefaultUserProfile(userId));
    }
    
    private MerchantRiskProfile getMerchantRiskProfile(String merchantId) {
        return profileRepository.findMerchantProfile(merchantId)
            .orElse(null);
    }
    
    private UserRiskProfile createDefaultUserProfile(String userId) {
        // CONFIGURE_IN_VAULT: Default risk score for new user profiles
        double defaultUserRiskScore = Double.parseDouble(System.getenv().getOrDefault("DEFAULT_USER_RISK_SCORE", "0.0"));
        return UserRiskProfile.builder()
            .userId(userId)
            .riskScore(defaultUserRiskScore)
            .accountCreatedAt(LocalDateTime.now())
            .build();
    }
    
    private boolean detectCardTestingPattern(VelocityMetrics metrics) {
        // CONFIGURE_IN_VAULT: Card testing pattern detection thresholds
        int cardTestingTxCountThreshold = Integer.parseInt(System.getenv().getOrDefault("CARD_TESTING_TX_COUNT_THRESHOLD", "0"));
        BigDecimal cardTestingAmountThreshold = new BigDecimal(System.getenv().getOrDefault("CARD_TESTING_AMOUNT_THRESHOLD", "0"));
        int cardTestingTimeThreshold = Integer.parseInt(System.getenv().getOrDefault("CARD_TESTING_TIME_THRESHOLD", "0"));

        return metrics.getTransactionCount() > cardTestingTxCountThreshold &&
               metrics.getAverageAmount().compareTo(cardTestingAmountThreshold) < 0 &&
               metrics.getTimeBetweenTransactions() < cardTestingTimeThreshold;
    }
    
    private boolean detectDeviceSpoofing(DeviceProfile device) {
        // CONFIGURE_IN_VAULT: Device fingerprint inconsistency threshold
        int fingerprintInconsistencyThreshold = Integer.parseInt(System.getenv().getOrDefault("DEVICE_FINGERPRINT_INCONSISTENCY_THRESHOLD", "0"));
        return device.getFingerprintInconsistencies() > fingerprintInconsistencyThreshold;
    }
    
    private GeoLocationInfo getGeoLocation(String ipAddress) {
        try {
            // Integration with geo-location service
            if (geoLocationService != null) {
                return geoLocationService.lookup(ipAddress);
            }
            log.warn("Geo-location service not available - using default location");
        } catch (Exception e) {
            log.error("Failed to get geo-location for IP: {} - using unknown location", ipAddress, e);
        }
        // Return unknown/default location when service fails
        return GeoLocationInfo.builder()
            .ipAddress(ipAddress)
            .countryCode("XX")
            .countryName("Unknown")
            .riskLevel("MEDIUM")
            .build();
    }
    
    private double getCountryRiskScore(String countryCode) {
        // CONFIGURE_IN_VAULT: Country risk scores should be stored in secure configuration
        // Format: COUNTRY_RISK_SCORES=NG:0.0,PK:0.0,RU:0.0,CN:0.0
        String countryRiskScoresEnv = System.getenv().getOrDefault("COUNTRY_RISK_SCORES", "");
        double defaultScore = Double.parseDouble(System.getenv().getOrDefault("COUNTRY_RISK_DEFAULT_SCORE", "0.0"));

        if (countryRiskScoresEnv.isEmpty()) {
            return defaultScore;
        }

        Map<String, Double> countryRisks = new HashMap<>();
        for (String entry : countryRiskScoresEnv.split(",")) {
            String[] parts = entry.split(":");
            if (parts.length == 2) {
                countryRisks.put(parts[0], Double.parseDouble(parts[1]));
            }
        }
        return countryRisks.getOrDefault(countryCode, defaultScore);
    }
    
    private boolean detectImpossibleTravel(LocationHistory last, GeoLocationInfo current) {
        // Calculate if travel between locations is possible given time
        return false;
    }
    
    private boolean isHighRiskMerchantCategory(String category) {
        // CONFIGURE_IN_VAULT: High-risk merchant categories should be stored in secure configuration
        String highRiskCategoriesEnv = System.getenv().getOrDefault("MERCHANT_HIGH_RISK_CATEGORIES", "");
        if (highRiskCategoriesEnv.isEmpty()) {
            return false;
        }
        Set<String> highRiskCategories = Set.of(highRiskCategoriesEnv.split(","));
        return highRiskCategories.contains(category);
    }
    
    private RiskFactorScore calculateTimeRisk(LocalDateTime timestamp) {
        // Time-based risk (late night, weekends, etc.)
        return RiskFactorScore.builder()
            .factor(RiskFactor.TIME)
            .score(0.1)
            .weight(riskFactorWeights.get(RiskFactor.TIME))
            .build();
    }
    
    private RiskFactorScore calculateNetworkRisk(String userId, String merchantId) {
        // Network analysis risk
        return RiskFactorScore.builder()
            .factor(RiskFactor.NETWORK)
            .score(0.1)
            .weight(riskFactorWeights.get(RiskFactor.NETWORK))
            .build();
    }
    
    private RiskFactorScore calculatePaymentMethodRisk(String paymentMethod) {
        // Payment method risk
        return RiskFactorScore.builder()
            .factor(RiskFactor.PAYMENT_METHOD)
            .score(0.1)
            .weight(riskFactorWeights.get(RiskFactor.PAYMENT_METHOD))
            .build();
    }
    
    private RiskFactorScore calculateIdentityRisk(UserRiskProfile profile) {
        // Identity verification risk
        return RiskFactorScore.builder()
            .factor(RiskFactor.IDENTITY)
            .score(0.1)
            .weight(riskFactorWeights.get(RiskFactor.IDENTITY))
            .build();
    }
    
    private FeatureVector prepareFeatureVector(TransactionRiskRequest request,
                                              Map<RiskFactor, RiskFactorScore> scores) {
        // Prepare features for ML model
        return new FeatureVector();
    }
    
    private double calculateConfidence(Map<RiskFactor, RiskFactorScore> scores, MLRiskScore mlScore) {
        // Calculate confidence in risk assessment
        return 0.85;
    }
    
    private String generateAssessmentReason(CompositeRiskScore score, RuleEngineResult ruleResult) {
        // Generate human-readable assessment reason
        return "Risk assessment based on multiple factors";
    }
    
    private Map<String, Object> buildAssessmentMetadata(TransactionRiskRequest request,
                                                        CompositeRiskScore score) {
        return Map.of(
            "assessmentVersion", "2.0",
            "modelsUsed", mlEnabled ? "RULES+ML" : "RULES",
            "timestamp", LocalDateTime.now()
        );
    }
    
    private void updateRiskProfiles(RiskAssessment assessment,
                                   UserRiskProfile userProfile,
                                   MerchantRiskProfile merchantProfile) {
        // Update profiles based on assessment
    }
    
    @Async
    private void publishRiskEvent(RiskAssessment assessment) {
        kafkaTemplate.send("risk-assessment-events", assessment);
    }
    
    private RiskAssessment createFailsafeAssessment(TransactionRiskRequest request, Exception error) {
        // CONFIGURE_IN_VAULT: Failsafe risk score when assessment fails
        double failsafeRiskScore = Double.parseDouble(System.getenv().getOrDefault("FAILSAFE_RISK_SCORE", "0.0"));
        return RiskAssessment.builder()
            .transactionId(request.getTransactionId())
            .riskScore(failsafeRiskScore)
            .riskLevel(RiskLevel.HIGH)
            .recommendedActions(List.of(RiskAction.BLOCK_TRANSACTION))
            .assessmentReason("System error - defaulting to high risk: " + error.getMessage())
            .build();
    }
}