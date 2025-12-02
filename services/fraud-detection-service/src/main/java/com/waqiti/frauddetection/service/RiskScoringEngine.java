package com.waqiti.frauddetection.service;

import com.waqiti.frauddetection.dto.FraudCheckRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Risk scoring engine for fraud detection
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RiskScoringEngine {

    private final VelocityCheckService velocityCheckService;
    private final GeoLocationService geoLocationService;
    private final DeviceFingerprintService deviceFingerprintService;
    private final BehavioralAnalysisService behavioralAnalysisService;
    private final BlacklistService blacklistService;

    /**
     * Calculate overall risk score
     */
    public double calculateRiskScore(String userId, String ipAddress, String deviceFingerprint, 
                                   BigDecimal amount, String transactionType) {
        log.debug("Calculating risk score for user: {}", userId);
        
        double riskScore = 0.0;
        
        // Velocity risk (30% weight)
        // Note: This would need the proper velocity check implementation
        double velocityRisk = 0.0; // velocityCheckService.getVelocityRisk(userId, amount);
        riskScore += velocityRisk * 0.30;
        
        // Location risk (20% weight)
        double locationRisk = geoLocationService.getLocationRiskScore(ipAddress, userId);
        riskScore += locationRisk * 0.20;
        
        // Device risk (15% weight)
        double deviceRisk = deviceFingerprintService.getDeviceRiskScore(deviceFingerprint, userId);
        riskScore += deviceRisk * 0.15;
        
        // Behavioral risk (25% weight)
        double behaviorRisk = behavioralAnalysisService.analyzeBehavior(userId, transactionType);
        riskScore += behaviorRisk * 0.25;
        
        // Blacklist risk (10% weight)
        double blacklistRisk = 0.0;
        if (blacklistService.isUserBlacklisted(userId) || blacklistService.isIpBlacklisted(ipAddress)) {
            blacklistRisk = 1.0;
        }
        riskScore += blacklistRisk * 0.10;
        
        // Ensure score is between 0 and 1
        riskScore = Math.max(0.0, Math.min(1.0, riskScore));
        
        log.debug("Calculated risk score for user {}: {}", userId, riskScore);
        return riskScore;
    }

    /**
     * Get risk level based on score
     */
    public String getRiskLevel(double riskScore) {
        if (riskScore >= 0.8) {
            return "CRITICAL";
        } else if (riskScore >= 0.6) {
            return "HIGH";
        } else if (riskScore >= 0.4) {
            return "MEDIUM";
        } else if (riskScore >= 0.2) {
            return "LOW";
        } else {
            return "MINIMAL";
        }
    }

    /**
     * Get detailed risk analysis
     */
    public Map<String, Object> getDetailedRiskAnalysis(String userId, String ipAddress, 
                                                     String deviceFingerprint, BigDecimal amount, 
                                                     String transactionType) {
        double overallScore = calculateRiskScore(userId, ipAddress, deviceFingerprint, amount, transactionType);
        
        return Map.of(
                "overallScore", overallScore,
                "riskLevel", getRiskLevel(overallScore),
                "userId", userId,
                "ipAddress", ipAddress,
                "amount", amount,
                "transactionType", transactionType,
                "isBlacklisted", blacklistService.isUserBlacklisted(userId) || blacklistService.isIpBlacklisted(ipAddress),
                "deviceTrusted", deviceFingerprintService.isDeviceTrusted(deviceFingerprint, userId),
                "locationSuspicious", geoLocationService.isSuspiciousLocation(ipAddress, userId)
        );
    }

    /**
     * Calculate final risk score from individual component scores
     */
    public double calculateFinalScore(Map<String, Double> scores, FraudCheckRequest request) {
        log.debug("Calculating final risk score for transaction: {}", request.getTransactionId());
        
        // Weighted combination of all risk scores
        double finalScore = 0.0;
        
        // Apply weights to each component score
        finalScore += getScoreOrDefault(scores, "velocity") * 0.25;  // 25% weight
        finalScore += getScoreOrDefault(scores, "geo") * 0.20;       // 20% weight
        finalScore += getScoreOrDefault(scores, "device") * 0.15;    // 15% weight
        finalScore += getScoreOrDefault(scores, "behavior") * 0.25;  // 25% weight
        finalScore += getScoreOrDefault(scores, "ml") * 0.15;        // 15% weight
        
        // Apply risk amplification for high-value transactions
        if (request.getAmount() != null && request.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            finalScore *= 1.1; // 10% amplification for large amounts
        }
        
        // Apply risk adjustment for transaction patterns
        finalScore = applyTransactionPatternAdjustment(finalScore, request);
        
        // Ensure score is between 0 and 1
        finalScore = Math.max(0.0, Math.min(1.0, finalScore));
        
        log.debug("Final risk score calculated for transaction {}: {}", 
            request.getTransactionId(), finalScore);
        
        return finalScore;
    }
    
    /**
     * Get score from map or return default value
     */
    private double getScoreOrDefault(Map<String, Double> scores, String key) {
        return scores.getOrDefault(key, 0.0);
    }
    
    /**
     * Apply transaction pattern adjustments to risk score
     */
    private double applyTransactionPatternAdjustment(double baseScore, FraudCheckRequest request) {
        double adjustedScore = baseScore;
        
        // Increase risk for off-hours transactions
        int hour = java.time.LocalDateTime.now().getHour();
        if (hour >= 0 && hour <= 5) { // Midnight to 5 AM
            adjustedScore *= 1.15; // 15% increase
        }
        
        // Increase risk for weekend transactions (if business account)
        java.time.DayOfWeek dayOfWeek = java.time.LocalDateTime.now().getDayOfWeek();
        if (dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY) {
            if (request.getAccountType() != null && request.getAccountType().contains("BUSINESS")) {
                adjustedScore *= 1.1; // 10% increase for business accounts on weekends
            }
        }
        
        return adjustedScore;
    }
}