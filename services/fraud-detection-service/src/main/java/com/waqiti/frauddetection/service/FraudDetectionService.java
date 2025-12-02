package com.waqiti.frauddetection.service;

import com.waqiti.frauddetection.dto.FraudCheckRequest;
import com.waqiti.frauddetection.dto.FraudCheckResponse;
import com.waqiti.frauddetection.dto.CountryRiskAssessment;
import com.waqiti.frauddetection.dto.RiskLevel;
import com.waqiti.frauddetection.dto.UserRiskProfile;
import com.waqiti.frauddetection.config.CountryRiskConfiguration;
import com.waqiti.frauddetection.entity.FraudRule;
import com.waqiti.frauddetection.entity.FraudIncident;
import com.waqiti.frauddetection.ml.FraudMLModel;
import com.waqiti.frauddetection.ml.SecureFraudMLModelService;
import com.waqiti.frauddetection.repository.FraudIncidentRepository;
import com.waqiti.frauddetection.repository.FraudRuleRepository;
import com.waqiti.common.math.MoneyMath;
import com.waqiti.common.locking.DistributedLockService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionService {

    private final FraudRuleRepository fraudRuleRepository;
    private final FraudIncidentRepository fraudIncidentRepository;
    private final VelocityCheckService velocityCheckService;
    private final GeoLocationService geoLocationService;
    private final DeviceFingerprintService deviceFingerprintService;
    private final BehavioralAnalysisService behavioralAnalysisService;
    private final FraudMLModel fraudMLModel;
    private final BlacklistService blacklistService;
    private final RiskScoringEngine riskScoringEngine;
    
    // PRODUCTION-GRADE ENHANCEMENT: Country risk configuration
    private final CountryRiskConfiguration countryRiskConfiguration;

    @Transactional
    public FraudCheckResponse checkTransaction(FraudCheckRequest request) {
        log.info("Starting fraud check for transaction: {}", request.getTransactionId());
        
        // Run multiple fraud checks in parallel
        CompletableFuture<Double> velocityScore = CompletableFuture.supplyAsync(() -> 
            velocityCheckService.checkVelocity(request));
        
        CompletableFuture<Double> geoScore = CompletableFuture.supplyAsync(() -> 
            geoLocationService.checkGeoAnomaly(request));
        
        CompletableFuture<Double> deviceScore = CompletableFuture.supplyAsync(() -> 
            deviceFingerprintService.checkDevice(request));
        
        CompletableFuture<Double> behaviorScore = CompletableFuture.supplyAsync(() -> 
            behavioralAnalysisService.analyzeBehavior(request));
        
        CompletableFuture<Double> mlScore = CompletableFuture.supplyAsync(() -> 
            fraudMLModel.predict(request));
        
        CompletableFuture<Boolean> isBlacklisted = CompletableFuture.supplyAsync(() -> 
            blacklistService.isBlacklisted(request));

        // Wait for all checks to complete
        try {
            CompletableFuture.allOf(velocityScore, geoScore, deviceScore, behaviorScore, mlScore, isBlacklisted)
                .get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Fraud detection checks timed out after 10 seconds for transaction: {}", request.getTransactionId(), e);
            List.of(velocityScore, geoScore, deviceScore, behaviorScore, mlScore, isBlacklisted).forEach(f -> f.cancel(true));
            // Return high risk score on timeout
            return createBlockedResponse(request, "Fraud detection timed out - blocked for safety", new HashMap<>());
        } catch (java.util.concurrent.ExecutionException e) {
            log.error("Fraud detection execution failed for transaction: {}", request.getTransactionId(), e.getCause());
            return createBlockedResponse(request, "Fraud detection failed - blocked for safety", new HashMap<>());
        } catch (java.util.concurrent.InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Fraud detection interrupted for transaction: {}", request.getTransactionId(), e);
            return createBlockedResponse(request, "Fraud detection interrupted - blocked for safety", new HashMap<>());
        }

        try {
            // Calculate composite risk score (safe to get with short timeout since allOf completed)
            Map<String, Double> scores = new HashMap<>();
            scores.put("velocity", velocityScore.get(1, java.util.concurrent.TimeUnit.SECONDS));
            scores.put("geo", geoScore.get(1, java.util.concurrent.TimeUnit.SECONDS));
            scores.put("device", deviceScore.get(1, java.util.concurrent.TimeUnit.SECONDS));
            scores.put("behavior", behaviorScore.get(1, java.util.concurrent.TimeUnit.SECONDS));
            scores.put("ml", mlScore.get(1, java.util.concurrent.TimeUnit.SECONDS));

            // Check if blacklisted
            if (isBlacklisted.get(1, java.util.concurrent.TimeUnit.SECONDS)) {
                return createBlockedResponse(request, "User/Device is blacklisted", scores);
            }

            // Apply rule-based checks
            List<FraudRule> triggeredRules = applyRules(request);
            if (!triggeredRules.isEmpty()) {
                return createRuleBasedResponse(request, triggeredRules, scores);
            }

            // Calculate final risk score
            double finalScore = riskScoringEngine.calculateFinalScore(scores, request);
            RiskLevel riskLevel = determineRiskLevel(finalScore);

            // Log the fraud check
            logFraudCheck(request, finalScore, riskLevel, scores);

            return FraudCheckResponse.builder()
                .transactionId(request.getTransactionId())
                .riskLevel(riskLevel)
                .riskScore(finalScore)
                .approved(riskLevel != RiskLevel.HIGH)
                .requiresAdditionalVerification(riskLevel == RiskLevel.MEDIUM)
                .scores(scores)
                .timestamp(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Error during fraud check", e);
            // In case of error, default to medium risk requiring manual review
            return FraudCheckResponse.builder()
                .transactionId(request.getTransactionId())
                .riskLevel(RiskLevel.MEDIUM)
                .approved(false)
                .requiresAdditionalVerification(true)
                .errorMessage("Fraud check encountered an error, manual review required")
                .timestamp(LocalDateTime.now())
                .build();
        }
    }

    private List<FraudRule> applyRules(FraudCheckRequest request) {
        List<FraudRule> activeRules = fraudRuleRepository.findByActiveTrue();
        List<FraudRule> triggeredRules = new ArrayList<>();

        for (FraudRule rule : activeRules) {
            if (evaluateRule(rule, request)) {
                triggeredRules.add(rule);
                log.warn("Fraud rule triggered: {} for transaction: {}", 
                    rule.getName(), request.getTransactionId());
            }
        }

        return triggeredRules;
    }

    private boolean evaluateRule(FraudRule rule, FraudCheckRequest request) {
        switch (rule.getRuleType()) {
            case "AMOUNT_THRESHOLD":
                return request.getAmount() != null && 
                       request.getAmount().compareTo(new BigDecimal(rule.getThreshold())) > 0;
            
            case "VELOCITY_CHECK":
                return velocityCheckService.exceedsVelocityLimit(
                    request.getUserId(), 
                    rule.getTimeWindow(), 
                    Integer.parseInt(rule.getThreshold())
                );
            
            case "CROSS_BORDER":
                return !request.getSenderCountry().equals(request.getRecipientCountry());
            
            case "NEW_RECIPIENT":
                return isNewRecipient(request.getUserId(), request.getRecipientId());
            
            case "UNUSUAL_TIME":
                int hour = LocalDateTime.now().getHour();
                return hour <= 5; // Transactions between midnight and 5 AM
            
            case "HIGH_RISK_COUNTRY":
                return isHighRiskCountry(request.getRecipientCountry());
            
            default:
                return false;
        }
    }

    private boolean isNewRecipient(String userId, String recipientId) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minus(30, ChronoUnit.DAYS);
        return !fraudIncidentRepository.existsSuccessfulTransactionBetween(
            userId, recipientId, thirtyDaysAgo);
    }

    /**
     * PRODUCTION-GRADE: Check if country is high risk using comprehensive risk database
     * Replaces placeholder implementation with real-world regulatory data
     */
    private boolean isHighRiskCountry(String country) {
        if (country == null || country.trim().isEmpty()) {
            log.warn("Country code is null or empty, treating as high risk");
            return true; // Treat unknown countries as high risk for security
        }
        
        // Normalize country code to uppercase
        String normalizedCountry = country.trim().toUpperCase();
        
        try {
            // Use the comprehensive country risk configuration
            return countryRiskConfiguration.isHighRiskCountry(normalizedCountry);
        } catch (Exception e) {
            log.error("Error checking country risk for {}, defaulting to high risk", normalizedCountry, e);
            return true; // Fail secure - treat as high risk if unable to determine
        }
    }
    
    /**
     * ENHANCED: Get detailed country risk assessment
     */
    private CountryRiskAssessment getCountryRiskAssessment(String country) {
        if (country == null || country.trim().isEmpty()) {
            return CountryRiskAssessment.builder()
                .countryCode("UNKNOWN")
                .riskLevel(RiskLevel.HIGH)
                .riskScore(0.8)
                .isSanctioned(false)
                .requiresEDD(true)
                .reason("Unknown or invalid country code")
                .build();
        }
        
        String normalizedCountry = country.trim().toUpperCase();
        CountryRiskConfiguration.CountryRiskProfile profile = 
            countryRiskConfiguration.getCountryRiskProfile(normalizedCountry);
            
        return CountryRiskAssessment.builder()
            .countryCode(normalizedCountry)
            .riskLevel(mapToRiskLevel(profile.getRiskLevel()))
            .riskScore((double) MoneyMath.toMLFeature(profile.getRiskScore()))
            .isSanctioned(profile.getSanctionStatus() != CountryRiskConfiguration.SanctionStatus.NONE)
            .requiresEDD(profile.isRequiresEDD())
            .reason(profile.getReason())
            .sanctionStatus(profile.getSanctionStatus().name())
            .build();
    }
    
    private RiskLevel mapToRiskLevel(CountryRiskConfiguration.CountryRiskLevel configRiskLevel) {
        return switch (configRiskLevel) {
            case LOW -> RiskLevel.LOW;
            case MEDIUM -> RiskLevel.MEDIUM;
            case MEDIUM_HIGH, HIGH, EXTREMELY_HIGH -> RiskLevel.HIGH;
        };
    }

    private RiskLevel determineRiskLevel(double riskScore) {
        if (riskScore >= 0.8) {
            return RiskLevel.HIGH;
        } else if (riskScore >= 0.5) {
            return RiskLevel.MEDIUM;
        } else {
            return RiskLevel.LOW;
        }
    }

    private FraudCheckResponse createBlockedResponse(FraudCheckRequest request, String reason, Map<String, Double> scores) {
        logFraudIncident(request, reason, RiskLevel.HIGH);
        
        return FraudCheckResponse.builder()
            .transactionId(request.getTransactionId())
            .riskLevel(RiskLevel.HIGH)
            .riskScore(1.0)
            .approved(false)
            .blockReason(reason)
            .scores(scores)
            .timestamp(LocalDateTime.now())
            .build();
    }

    private FraudCheckResponse createRuleBasedResponse(FraudCheckRequest request, 
                                                       List<FraudRule> triggeredRules, 
                                                       Map<String, Double> scores) {
        String reasons = triggeredRules.stream()
            .map(FraudRule::getName)
            .collect(Collectors.joining(", "));
        
        RiskLevel riskLevel = triggeredRules.stream()
            .anyMatch(rule -> rule.getSeverity().equals("HIGH")) 
            ? RiskLevel.HIGH 
            : RiskLevel.MEDIUM;
        
        logFraudIncident(request, "Rules triggered: " + reasons, riskLevel);
        
        return FraudCheckResponse.builder()
            .transactionId(request.getTransactionId())
            .riskLevel(riskLevel)
            .riskScore(riskLevel == RiskLevel.HIGH ? 0.9 : 0.6)
            .approved(riskLevel != RiskLevel.HIGH)
            .requiresAdditionalVerification(true)
            .triggeredRules(reasons)
            .scores(scores)
            .timestamp(LocalDateTime.now())
            .build();
    }

    private void logFraudCheck(FraudCheckRequest request, double riskScore, 
                               RiskLevel riskLevel, Map<String, Double> scores) {
        // Log to database for audit and ML training
        FraudIncident incident = FraudIncident.builder()
            .transactionId(request.getTransactionId())
            .userId(request.getUserId())
            .amount(request.getAmount())
            .riskScore(riskScore)
            .riskLevel(riskLevel)
            .velocityScore(scores.get("velocity"))
            .geoScore(scores.get("geo"))
            .deviceScore(scores.get("device"))
            .behaviorScore(scores.get("behavior"))
            .mlScore(scores.get("ml"))
            .timestamp(LocalDateTime.now())
            .build();
        
        fraudIncidentRepository.save(incident);
    }

    private void logFraudIncident(FraudCheckRequest request, String reason, RiskLevel riskLevel) {
        FraudIncident incident = FraudIncident.builder()
            .transactionId(request.getTransactionId())
            .userId(request.getUserId())
            .amount(request.getAmount())
            .riskLevel(riskLevel)
            .incidentReason(reason)
            .blocked(true)
            .timestamp(LocalDateTime.now())
            .build();
        
        fraudIncidentRepository.save(incident);
    }

    @Cacheable(value = "userRiskProfile", key = "#userId")
    public UserRiskProfile getUserRiskProfile(String userId) {
        List<FraudIncident> recentIncidents = fraudIncidentRepository
            .findByUserIdAndTimestampAfter(userId, LocalDateTime.now().minus(90, ChronoUnit.DAYS));
        
        long highRiskCount = recentIncidents.stream()
            .filter(i -> i.getRiskLevel() == RiskLevel.HIGH)
            .count();
        
        long mediumRiskCount = recentIncidents.stream()
            .filter(i -> i.getRiskLevel() == RiskLevel.MEDIUM)
            .count();
        
        double averageRiskScore = recentIncidents.stream()
            .mapToDouble(FraudIncident::getRiskScore)
            .average()
            .orElse(0.0);
        
        return UserRiskProfile.builder()
            .userId(userId)
            .totalTransactions(recentIncidents.size())
            .highRiskTransactions(highRiskCount)
            .mediumRiskTransactions(mediumRiskCount)
            .averageRiskScore(averageRiskScore)
            .lastUpdated(LocalDateTime.now())
            .build();
    }

    public void updateMLModel(List<FraudIncident> labeledIncidents) {
        log.info("Updating ML model with {} labeled incidents", labeledIncidents.size());
        fraudMLModel.retrain(labeledIncidents);
    }
}