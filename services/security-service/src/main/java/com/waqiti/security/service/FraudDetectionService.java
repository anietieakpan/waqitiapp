package com.waqiti.security.service;

import com.waqiti.security.domain.SecurityEvent;
import com.waqiti.security.domain.SecurityEventType;
import com.waqiti.security.domain.SecuritySeverity;
import com.waqiti.security.domain.SecurityAction;
import com.waqiti.security.repository.SecurityEventRepository;
import com.waqiti.security.dto.*;
import com.waqiti.security.ml.FraudMLEngine;
import com.waqiti.security.geolocation.GeolocationService;
import com.waqiti.security.behavioral.BehavioralAnalysisEngine;
import com.waqiti.security.velocity.VelocityCheckEngine;
import com.waqiti.security.patterns.PatternDetectionEngine;
import com.waqiti.security.device.DeviceFingerprintService;
import com.waqiti.common.events.FraudAlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Advanced Fraud Detection Service
 * Implements real-time ML-based fraud detection with comprehensive risk analysis
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FraudDetectionService {

    private final SecurityEventRepository securityEventRepository;
    private final RiskScoringService riskScoringService;
    private final NotificationService notificationService;
    
    // Advanced ML and Analytics Components
    private final FraudMLEngine fraudMLEngine;
    private final GeolocationService geolocationService;
    private final BehavioralAnalysisEngine behavioralAnalysisEngine;
    private final VelocityCheckEngine velocityCheckEngine;
    private final PatternDetectionEngine patternDetectionEngine;
    private final DeviceFingerprintService deviceFingerprintService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // High-value transaction threshold
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("10000.00");
    
    // Velocity limits
    private static final BigDecimal DAILY_TRANSACTION_LIMIT = new BigDecimal("50000.00");
    private static final int DAILY_TRANSACTION_COUNT_LIMIT = 20;

    /**
     * Advanced ML-based transaction fraud analysis
     */
    public FraudAnalysisResponse analyzeTransactionAdvanced(FraudAnalysisRequest request) {
        log.info("Starting advanced fraud analysis for transaction: {}", request.getTransactionId());
        
        try {
            // Perform comprehensive fraud analysis using ML and multiple risk factors
            FraudScore fraudScore = performComprehensiveFraudAnalysis(request);
            
            // Create detailed fraud analysis response
            FraudAnalysisResponse response = FraudAnalysisResponse.builder()
                    .transactionId(request.getTransactionId())
                    .userId(request.getUserId())
                    .riskScore(fraudScore.getOverallScore())
                    .riskLevel(determineAdvancedRiskLevel(fraudScore.getOverallScore()))
                    .mlScore(fraudScore.getMlScore())
                    .velocityScore(fraudScore.getVelocityScore())
                    .behavioralScore(fraudScore.getBehavioralScore())
                    .geolocationScore(fraudScore.getGeolocationScore())
                    .deviceScore(fraudScore.getDeviceScore())
                    .patternScore(fraudScore.getPatternScore())
                    .recommendedAction(determineAdvancedRecommendedAction(fraudScore))
                    .confidenceLevel(fraudScore.getConfidenceLevel())
                    .riskFactors(fraudScore.getRiskFactors())
                    .recommendations(fraudScore.getRecommendations())
                    .analysisDetails(fraudScore.getAnalysisDetails())
                    .timestamp(LocalDateTime.now())
                    .build();
            
            // Create fraud alert if high risk
            if (response.getRiskScore() >= 75) {
                createAdvancedFraudAlert(response, fraudScore);
            }
            
            // Publish event for downstream processing
            publishFraudAnalysisEvent(response);
            
            log.info("Advanced fraud analysis completed for transaction: {} with risk score: {}", 
                    request.getTransactionId(), response.getRiskScore());
            
            return response;
            
        } catch (Exception e) {
            log.error("Error in advanced fraud analysis for transaction: {}", request.getTransactionId(), e);
            throw new RuntimeException("Advanced fraud analysis failed", e);
        }
    }

    /**
     * Legacy analyze transaction method for backward compatibility
     */
    public SecurityEvent analyzeTransaction(UUID userId, UUID transactionId, 
                                           BigDecimal amount, String ipAddress, String userAgent) {
        
        log.info("Analyzing transaction {} for user {} amount {}", transactionId, userId, amount);
        
        // Create request for advanced analysis
        FraudAnalysisRequest request = FraudAnalysisRequest.builder()
                .transactionId(transactionId)
                .userId(userId)
                .amount(amount)
                .ipAddress(ipAddress)
                .deviceFingerprint(userAgent) // Use userAgent as basic device fingerprint
                .transactionType("P2P_TRANSFER")
                .channelType("MOBILE_APP")
                .build();
        
        // Perform advanced analysis
        FraudAnalysisResponse analysisResponse = analyzeTransactionAdvanced(request);
        
        // Convert to legacy SecurityEvent format
        int riskScore = (int) analysisResponse.getRiskScore();
        SecurityEventType eventType = determineEventType(amount, riskScore);
        SecuritySeverity severity = determineSeverity(riskScore);
        SecurityAction action = determineAction(riskScore, severity);
        
        SecurityEvent event = SecurityEvent.builder()
            .userId(userId)
            .eventType(eventType)
            .severity(severity)
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .transactionId(transactionId)
            .amount(amount)
            .riskScore(riskScore)
            .description(generateDescription(eventType, amount, riskScore))
            .actionTaken(action)
            .resolved(action == SecurityAction.NO_ACTION || action == SecurityAction.LOG_ONLY)
            .build();
            
        SecurityEvent savedEvent = securityEventRepository.save(event);
        
        // Execute action if required
        executeSecurityAction(savedEvent);
        
        return savedEvent;
    }

    /**
     * Analyze login attempt for suspicious patterns
     */
    public SecurityEvent analyzeLoginAttempt(UUID userId, String ipAddress, 
                                           String userAgent, boolean successful) {
        
        SecurityEventType eventType = successful ? 
            SecurityEventType.SUCCESSFUL_LOGIN : SecurityEventType.FAILED_LOGIN_ATTEMPT;
            
        int riskScore = riskScoringService.calculateLoginRiskScore(userId, ipAddress, userAgent, successful);
        SecuritySeverity severity = determineSeverity(riskScore);
        SecurityAction action = determineAction(riskScore, severity);
        
        // Check for account lockout conditions
        if (!successful && shouldLockAccount(userId)) {
            eventType = SecurityEventType.ACCOUNT_LOCKED;
            action = SecurityAction.ACCOUNT_LOCKED;
            severity = SecuritySeverity.HIGH;
        }
        
        SecurityEvent event = SecurityEvent.builder()
            .userId(userId)
            .eventType(eventType)
            .severity(severity)
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .riskScore(riskScore)
            .description(generateDescription(eventType, null, riskScore))
            .actionTaken(action)
            .resolved(action == SecurityAction.NO_ACTION || action == SecurityAction.LOG_ONLY)
            .build();
            
        SecurityEvent savedEvent = securityEventRepository.save(event);
        executeSecurityAction(savedEvent);
        
        return savedEvent;
    }

    /**
     * Check daily transaction velocity rules
     */
    public boolean checkVelocityRules(UUID userId, BigDecimal transactionAmount) {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        
        // Check daily amount limit
        BigDecimal dailyTotal = securityEventRepository.getDailyTransactionTotal(userId, startOfDay);
        if (dailyTotal.add(transactionAmount).compareTo(DAILY_TRANSACTION_LIMIT) > 0) {
            createVelocityViolationEvent(userId, "Daily amount limit exceeded");
            return false;
        }
        
        // Check daily count limit
        Long dailyCount = securityEventRepository.getDailyTransactionCount(userId, startOfDay);
        if (dailyCount >= DAILY_TRANSACTION_COUNT_LIMIT) {
            createVelocityViolationEvent(userId, "Daily transaction count limit exceeded");
            return false;
        }
        
        return true;
    }

    private SecurityEventType determineEventType(BigDecimal amount, int riskScore) {
        if (amount.compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
            return SecurityEventType.HIGH_VALUE_TRANSACTION;
        }
        if (riskScore >= 70) {
            return SecurityEventType.SUSPICIOUS_TRANSACTION;
        }
        return SecurityEventType.SUSPICIOUS_TRANSACTION; // Default for analysis
    }

    private SecuritySeverity determineSeverity(int riskScore) {
        if (riskScore >= 90) return SecuritySeverity.CRITICAL;
        if (riskScore >= 70) return SecuritySeverity.HIGH;
        if (riskScore >= 40) return SecuritySeverity.MEDIUM;
        return SecuritySeverity.LOW;
    }

    private SecurityAction determineAction(int riskScore, SecuritySeverity severity) {
        if (riskScore >= 90) return SecurityAction.TRANSACTION_BLOCKED;
        if (riskScore >= 70) return SecurityAction.MANUAL_REVIEW_REQUIRED;
        if (riskScore >= 40) return SecurityAction.ADDITIONAL_VERIFICATION_REQUIRED;
        return SecurityAction.LOG_ONLY;
    }

    private boolean shouldLockAccount(UUID userId) {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        Long failedAttempts = securityEventRepository.countFailedLoginAttempts(userId, oneHourAgo);
        return failedAttempts >= 5; // Lock after 5 failed attempts in 1 hour
    }

    private void createVelocityViolationEvent(UUID userId, String description) {
        SecurityEvent event = SecurityEvent.builder()
            .userId(userId)
            .eventType(SecurityEventType.VELOCITY_RULE_VIOLATION)
            .severity(SecuritySeverity.HIGH)
            .description(description)
            .actionTaken(SecurityAction.TRANSACTION_BLOCKED)
            .resolved(false)
            .build();
            
        securityEventRepository.save(event);
        executeSecurityAction(event);
    }

    private String generateDescription(SecurityEventType eventType, BigDecimal amount, int riskScore) {
        StringBuilder desc = new StringBuilder();
        desc.append("Event: ").append(eventType.name().replace("_", " "));
        if (amount != null) {
            desc.append(", Amount: $").append(amount);
        }
        desc.append(", Risk Score: ").append(riskScore);
        return desc.toString();
    }

    private void executeSecurityAction(SecurityEvent event) {
        switch (event.getActionTaken()) {
            case ACCOUNT_LOCKED:
                notificationService.sendAccountLockedNotification(event.getUserId());
                break;
            case TRANSACTION_BLOCKED:
                notificationService.sendTransactionBlockedNotification(event.getUserId(), event.getTransactionId());
                break;
            case MANUAL_REVIEW_REQUIRED:
                notificationService.sendManualReviewNotification(event);
                break;
            case ADDITIONAL_VERIFICATION_REQUIRED:
                notificationService.sendAdditionalVerificationNotification(event.getUserId());
                break;
            default:
                // No action required
                break;
        }
    }

    // Advanced ML-based fraud analysis methods

    private FraudScore performComprehensiveFraudAnalysis(FraudAnalysisRequest request) {
        log.debug("Performing comprehensive fraud analysis");
        
        FraudScore.FraudScoreBuilder scoreBuilder = FraudScore.builder();
        
        // 1. Machine Learning Score (35% weight)
        double mlScore = fraudMLEngine.calculateMlScore(request);
        scoreBuilder.mlScore(mlScore);
        
        // 2. Velocity Check Score (20% weight)
        double velocityScore = velocityCheckEngine.calculateVelocityScore(request.getUserId(), request.getAmount());
        scoreBuilder.velocityScore(velocityScore);
        
        // 3. Behavioral Analysis Score (15% weight)
        double behavioralScore = behavioralAnalysisEngine.calculateBehavioralScore(request.getUserId(), request);
        scoreBuilder.behavioralScore(behavioralScore);
        
        // 4. Geolocation Risk Score (10% weight)
        double geolocationScore = geolocationService.calculateGeolocationScore(
                request.getUserId(), request.getIpAddress(), request.getGeolocation());
        scoreBuilder.geolocationScore(geolocationScore);
        
        // 5. Device Fingerprint Score (10% weight)
        double deviceScore = deviceFingerprintService.calculateDeviceScore(
                request.getUserId(), request.getDeviceFingerprint());
        scoreBuilder.deviceScore(deviceScore);
        
        // 6. Pattern Detection Score (10% weight)
        double patternScore = patternDetectionEngine.calculatePatternScore(request);
        scoreBuilder.patternScore(patternScore);
        
        // Calculate weighted overall score
        double overallScore = calculateWeightedScore(mlScore, velocityScore, behavioralScore, 
                geolocationScore, deviceScore, patternScore);
        scoreBuilder.overallScore(overallScore);
        
        // Calculate confidence level based on score consistency
        double confidenceLevel = calculateConfidenceLevel(mlScore, velocityScore, behavioralScore, 
                geolocationScore, deviceScore, patternScore);
        scoreBuilder.confidenceLevel(confidenceLevel);
        
        // Generate detailed analysis and recommendations
        scoreBuilder.analysisDetails(generateAnalysisDetails(mlScore, velocityScore, behavioralScore, 
                geolocationScore, deviceScore, patternScore));
        scoreBuilder.riskFactors(identifyRiskFactors(request, mlScore, velocityScore, behavioralScore, 
                geolocationScore, deviceScore, patternScore));
        scoreBuilder.recommendations(generateRecommendations(overallScore, confidenceLevel));
        
        return scoreBuilder.build();
    }

    private double calculateWeightedScore(double mlScore, double velocityScore, double behavioralScore,
                                        double geolocationScore, double deviceScore, double patternScore) {
        return (mlScore * 0.35) + 
               (velocityScore * 0.20) + 
               (behavioralScore * 0.15) + 
               (geolocationScore * 0.10) + 
               (deviceScore * 0.10) + 
               (patternScore * 0.10);
    }

    private double calculateConfidenceLevel(double mlScore, double velocityScore, double behavioralScore,
                                          double geolocationScore, double deviceScore, double patternScore) {
        double[] scores = {mlScore, velocityScore, behavioralScore, geolocationScore, deviceScore, patternScore};
        double mean = java.util.Arrays.stream(scores).average().orElse(0.0);
        double variance = java.util.Arrays.stream(scores)
                .map(score -> Math.pow(score - mean, 2))
                .average().orElse(0.0);
        
        return Math.max(0.1, 1.0 - (variance / 100.0));
    }

    private String determineAdvancedRiskLevel(double riskScore) {
        if (riskScore >= 90) return "CRITICAL";
        if (riskScore >= 75) return "HIGH";
        if (riskScore >= 50) return "MEDIUM";
        if (riskScore >= 25) return "LOW";
        return "MINIMAL";
    }

    private String determineAdvancedRecommendedAction(FraudScore fraudScore) {
        double score = fraudScore.getOverallScore();
        double confidence = fraudScore.getConfidenceLevel();
        
        if (score >= 90 && confidence >= 0.8) return "BLOCK";
        if (score >= 75) return "MANUAL_REVIEW";
        if (score >= 50) return "ADDITIONAL_VERIFICATION";
        if (score >= 25) return "MONITOR";
        return "ALLOW";
    }

    private void createAdvancedFraudAlert(FraudAnalysisResponse response, FraudScore fraudScore) {
        FraudAlertEvent alert = FraudAlertEvent.builder()
                .alertId(UUID.randomUUID())
                .transactionId(response.getTransactionId())
                .userId(response.getUserId())
                .severity(mapScoreToSeverity(response.getRiskScore()))
                .riskScore(response.getRiskScore())
                .description(generateAdvancedAlertDescription(response, fraudScore))
                .timestamp(LocalDateTime.now())
                .build();
        
        kafkaTemplate.send("fraud-alert", alert);
    }

    private void publishFraudAnalysisEvent(FraudAnalysisResponse response) {
        kafkaTemplate.send("fraud-analysis-completed", response);
    }

    private String mapScoreToSeverity(double score) {
        if (score >= 90) return "CRITICAL";
        if (score >= 75) return "HIGH";
        if (score >= 50) return "MEDIUM";
        return "LOW";
    }

    private String generateAdvancedAlertDescription(FraudAnalysisResponse response, FraudScore fraudScore) {
        return String.format("High-risk transaction detected (Score: %.1f) for user %s, amount: %s. " +
                "ML: %.1f, Velocity: %.1f, Behavioral: %.1f", 
                response.getRiskScore(), response.getUserId(), "N/A",
                fraudScore.getMlScore(), fraudScore.getVelocityScore(), fraudScore.getBehavioralScore());
    }

    private String generateAnalysisDetails(double mlScore, double velocityScore, double behavioralScore,
                                         double geolocationScore, double deviceScore, double patternScore) {
        return String.format("ML: %.1f, Velocity: %.1f, Behavioral: %.1f, Geo: %.1f, Device: %.1f, Pattern: %.1f",
                mlScore, velocityScore, behavioralScore, geolocationScore, deviceScore, patternScore);
    }

    private List<String> identifyRiskFactors(FraudAnalysisRequest request, double mlScore, double velocityScore,
                                           double behavioralScore, double geolocationScore, double deviceScore, 
                                           double patternScore) {
        List<String> riskFactors = new java.util.ArrayList<>();
        
        if (mlScore > 70) riskFactors.add("High ML fraud probability");
        if (velocityScore > 70) riskFactors.add("Velocity limit exceeded");
        if (behavioralScore > 70) riskFactors.add("Unusual behavioral pattern");
        if (geolocationScore > 70) riskFactors.add("High-risk geographic location");
        if (deviceScore > 70) riskFactors.add("Suspicious device fingerprint");
        if (patternScore > 70) riskFactors.add("Suspicious transaction pattern");
        
        return riskFactors;
    }

    private List<String> generateRecommendations(double overallScore, double confidenceLevel) {
        List<String> recommendations = new java.util.ArrayList<>();
        
        if (overallScore >= 90) {
            recommendations.add("Block transaction immediately");
            recommendations.add("Investigate user account for fraud");
        } else if (overallScore >= 75) {
            recommendations.add("Hold transaction for manual review");
            recommendations.add("Contact user for verification");
        } else if (overallScore >= 50) {
            recommendations.add("Require additional authentication");
            recommendations.add("Monitor subsequent transactions closely");
        } else if (overallScore >= 25) {
            recommendations.add("Increase monitoring for this user");
            recommendations.add("Log transaction for analysis");
        }
        
        if (confidenceLevel < 0.6) {
            recommendations.add("Low confidence - consider collecting more data");
        }
        
        return recommendations;
    }
}