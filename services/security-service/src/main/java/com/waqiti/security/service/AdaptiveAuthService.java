package com.waqiti.security.service;

import com.waqiti.security.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Adaptive Authentication Service
 * Determines authentication requirements based on risk assessment
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdaptiveAuthService {

    private final UserProfileService userProfileService;
    private final BehavioralAuthService behavioralAuthService;
    private final GeolocationService geolocationService;
    private final DeviceAnalysisService deviceAnalysisService;

    // Risk thresholds
    private static final int LOW_RISK_THRESHOLD = 30;
    private static final int MEDIUM_RISK_THRESHOLD = 60;
    private static final int HIGH_RISK_THRESHOLD = 80;

    /**
     * Determine authentication requirements based on risk
     */
    public AdaptiveAuthRequirement determineAuthRequirement(AuthenticationEvent event) {
        try {
            log.debug("Determining adaptive auth requirements for user: {}", event.getUserId());

            // Perform comprehensive risk assessment
            RiskAssessment risk = assessRisk(event);

            // Determine required authentication factors
            AuthMethod requiredMethod = determineRequiredMethod(risk);
            List<String> requiredFactors = determineRequiredFactors(risk);
            boolean requiresStepUp = determineStepUpRequirement(risk);
            boolean shouldChallenge = determineChallengeRequirement(risk);
            boolean shouldBlock = determineBlockRequirement(risk);

            // Calculate session duration based on risk
            int sessionDuration = calculateSessionDuration(risk);

            // Build adaptive requirement
            return AdaptiveAuthRequirement.builder()
                .userId(event.getUserId())
                .riskScore(risk.getTotalRiskScore())
                .riskLevel(risk.getRiskLevel())
                .requiredMethod(requiredMethod)
                .requiredFactors(requiredFactors)
                .requiresStepUp(requiresStepUp)
                .shouldChallenge(shouldChallenge)
                .shouldBlock(shouldBlock)
                .sessionDuration(sessionDuration)
                .riskFactors(risk.getRiskFactors())
                .recommendation(generateRecommendation(risk))
                .timestamp(Instant.now())
                .build();

        } catch (Exception e) {
            log.error("Error determining auth requirements for user {}: {}",
                event.getUserId(), e.getMessage(), e);

            // Default to secure settings on error
            return createDefaultRequirement(event.getUserId());
        }
    }

    /**
     * Assess overall risk for authentication event
     */
    private RiskAssessment assessRisk(AuthenticationEvent event) {
        Map<String, Integer> riskFactors = new HashMap<>();
        int totalRiskScore = 0;

        // 1. User profile risk
        UserSecurityProfile profile = userProfileService.getUserProfile(event.getUserId());
        int userRisk = userProfileService.calculateUserRiskScore(profile);
        riskFactors.put("user_profile", userRisk);
        totalRiskScore += userRisk;

        // 2. Behavioral risk
        AnomalyDetectionResult behaviorResult = behavioralAuthService.analyzeBehavior(event);
        int behaviorRisk = behaviorResult.getOverallRiskScore();
        riskFactors.put("behavior", behaviorRisk);
        totalRiskScore += behaviorRisk;

        // 3. Location risk
        if (event.getIpAddress() != null) {
            GeoLocationData location = geolocationService.lookupLocation(event.getIpAddress());
            int locationRisk = geolocationService.calculateLocationRiskScore(location);
            riskFactors.put("location", locationRisk);
            totalRiskScore += locationRisk;
        }

        // 4. Device risk
        DeviceAnalysisResult deviceResult = deviceAnalysisService.analyzeDevice(event);
        int deviceRisk = deviceResult.getRiskScore();
        riskFactors.put("device", deviceRisk);
        totalRiskScore += deviceRisk;

        // 5. Time-based risk
        int timeRisk = calculateTimeBasedRisk(event);
        riskFactors.put("time", timeRisk);
        totalRiskScore += timeRisk;

        // 6. Velocity risk (rapid successive attempts)
        int velocityRisk = calculateVelocityRisk(event);
        riskFactors.put("velocity", velocityRisk);
        totalRiskScore += velocityRisk;

        // Normalize total risk (average of factors)
        int avgRiskScore = riskFactors.isEmpty() ? 0 :
            totalRiskScore / riskFactors.size();

        String riskLevel = determineRiskLevel(avgRiskScore);

        return RiskAssessment.builder()
            .totalRiskScore(avgRiskScore)
            .riskLevel(riskLevel)
            .riskFactors(riskFactors)
            .hasAnomalies(behaviorResult.isOverallAnomalous())
            .anomalyDetails(behaviorResult.getAnomalies())
            .build();
    }

    /**
     * Determine required authentication method
     */
    private AuthMethod determineRequiredMethod(RiskAssessment risk) {
        int riskScore = risk.getTotalRiskScore();

        if (riskScore >= HIGH_RISK_THRESHOLD) {
            return AuthMethod.MFA; // Require multi-factor
        } else if (riskScore >= MEDIUM_RISK_THRESHOLD) {
            return AuthMethod.TWO_FACTOR; // Require 2FA
        } else {
            return AuthMethod.PASSWORD; // Standard password
        }
    }

    /**
     * Determine required authentication factors
     */
    private List<String> determineRequiredFactors(RiskAssessment risk) {
        List<String> factors = new ArrayList<>();
        int riskScore = risk.getTotalRiskScore();

        // Always require password
        factors.add("PASSWORD");

        if (riskScore >= MEDIUM_RISK_THRESHOLD) {
            // Medium risk: require OTP
            factors.add("OTP");
        }

        if (riskScore >= HIGH_RISK_THRESHOLD) {
            // High risk: require additional factor
            factors.add("BIOMETRIC");
        }

        return factors;
    }

    /**
     * Determine if step-up authentication is required
     */
    private boolean determineStepUpRequirement(RiskAssessment risk) {
        // Require step-up for medium to high risk
        return risk.getTotalRiskScore() >= MEDIUM_RISK_THRESHOLD;
    }

    /**
     * Determine if CAPTCHA challenge is required
     */
    private boolean determineChallengeRequirement(RiskAssessment risk) {
        // Challenge for low-medium risk or higher
        return risk.getTotalRiskScore() >= LOW_RISK_THRESHOLD;
    }

    /**
     * Determine if authentication should be blocked
     */
    private boolean determineBlockRequirement(RiskAssessment risk) {
        // Block for critical risk
        return risk.getTotalRiskScore() >= 90;
    }

    /**
     * Calculate session duration based on risk (in minutes)
     */
    private int calculateSessionDuration(RiskAssessment risk) {
        int riskScore = risk.getTotalRiskScore();

        if (riskScore >= HIGH_RISK_THRESHOLD) {
            return 15; // 15 minutes for high risk
        } else if (riskScore >= MEDIUM_RISK_THRESHOLD) {
            return 60; // 1 hour for medium risk
        } else {
            return 480; // 8 hours for low risk
        }
    }

    /**
     * Calculate time-based risk (unusual hours)
     */
    private int calculateTimeBasedRisk(AuthenticationEvent event) {
        // Check if login time is unusual (e.g., middle of night)
        int hour = event.getTimestamp()
            .atZone(java.time.ZoneId.systemDefault())
            .getHour();

        // Higher risk for 2 AM - 6 AM
        if (hour >= 2 && hour <= 6) {
            return 20;
        }

        return 0;
    }

    /**
     * Calculate velocity risk (rapid attempts)
     */
    private int calculateVelocityRisk(AuthenticationEvent event) {
        // Check auth attempt count
        if (event.getAuthAttempts() != null && event.getAuthAttempts() > 3) {
            return Math.min(event.getAuthAttempts() * 10, 50);
        }

        return 0;
    }

    /**
     * Determine risk level from score
     */
    private String determineRiskLevel(int riskScore) {
        if (riskScore >= HIGH_RISK_THRESHOLD) return "HIGH";
        if (riskScore >= MEDIUM_RISK_THRESHOLD) return "MEDIUM";
        if (riskScore >= LOW_RISK_THRESHOLD) return "LOW";
        return "MINIMAL";
    }

    /**
     * Generate recommendation based on risk
     */
    private String generateRecommendation(RiskAssessment risk) {
        int riskScore = risk.getTotalRiskScore();

        if (riskScore >= 90) {
            return "BLOCK - Critical risk detected. Block authentication attempt.";
        } else if (riskScore >= HIGH_RISK_THRESHOLD) {
            return "CHALLENGE - High risk. Require MFA and additional verification.";
        } else if (riskScore >= MEDIUM_RISK_THRESHOLD) {
            return "STEP_UP - Medium risk. Require 2FA and CAPTCHA challenge.";
        } else if (riskScore >= LOW_RISK_THRESHOLD) {
            return "CHALLENGE - Low risk. Require CAPTCHA challenge.";
        } else {
            return "ALLOW - Minimal risk. Allow standard authentication.";
        }
    }

    /**
     * Create default (secure) requirement on error
     */
    private AdaptiveAuthRequirement createDefaultRequirement(String userId) {
        return AdaptiveAuthRequirement.builder()
            .userId(userId)
            .riskScore(50)
            .riskLevel("MEDIUM")
            .requiredMethod(AuthMethod.TWO_FACTOR)
            .requiredFactors(Arrays.asList("PASSWORD", "OTP"))
            .requiresStepUp(true)
            .shouldChallenge(true)
            .shouldBlock(false)
            .sessionDuration(60)
            .riskFactors(new HashMap<>())
            .recommendation("DEFAULT - Error during assessment. Require 2FA.")
            .timestamp(Instant.now())
            .build();
    }

    /**
     * Risk Assessment result
     */
    @lombok.Data
    @lombok.Builder
    private static class RiskAssessment {
        private int totalRiskScore;
        private String riskLevel;
        private Map<String, Integer> riskFactors;
        private boolean hasAnomalies;
        private List<DetectedAnomaly> anomalyDetails;
    }

    /**
     * Adaptive Authentication Requirement
     */
    @lombok.Data
    @lombok.Builder
    public static class AdaptiveAuthRequirement {
        private String userId;
        private int riskScore;
        private String riskLevel;
        private AuthMethod requiredMethod;
        private List<String> requiredFactors;
        private boolean requiresStepUp;
        private boolean shouldChallenge;
        private boolean shouldBlock;
        private int sessionDuration;
        private Map<String, Integer> riskFactors;
        private String recommendation;
        private Instant timestamp;
    }
}
