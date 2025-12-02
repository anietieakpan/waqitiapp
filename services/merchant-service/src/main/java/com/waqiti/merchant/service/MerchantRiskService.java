package com.waqiti.merchant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Merchant Risk Service - Handles comprehensive merchant risk assessment and management
 * 
 * Provides comprehensive merchant risk capabilities for:
 * - Real-time risk scoring and assessment
 * - Chargeback pattern analysis and prevention
 * - Fraud pattern detection and mitigation
 * - Risk profile management and tracking
 * - Risk mitigation action determination and execution
 * - Enhanced monitoring and surveillance
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantRiskService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${merchant.risk.enabled:true}")
    private boolean riskManagementEnabled;

    @Value("${merchant.risk.chargeback.threshold:1.0}")
    private double chargebackThreshold;

    @Value("${merchant.risk.score.threshold.high:75.0}")
    private double highRiskThreshold;

    @Value("${merchant.risk.volume.threshold:100000}")
    private BigDecimal volumeThreshold;

    @Value("${merchant.risk.retention.days:2555}")
    private int riskRetentionDays; // 7 years

    // Cache for tracking merchant risk profiles
    private final Map<String, MerchantRiskProfile> riskProfiles = new ConcurrentHashMap<>();

    /**
     * Updates merchant risk score
     */
    public void updateMerchantRiskScore(
            String merchantId,
            Double riskScore,
            String riskType,
            Map<String, String> riskIndicators,
            LocalDateTime timestamp) {

        if (!riskManagementEnabled) {
            log.debug("Risk management disabled, skipping risk score update");
            return;
        }

        try {
            log.debug("Updating risk score for merchant: {} - Score: {}", merchantId, riskScore);

            // Get or create risk profile
            MerchantRiskProfile profile = riskProfiles.computeIfAbsent(
                merchantId, k -> new MerchantRiskProfile(k));

            // Calculate enhanced risk score
            double enhancedScore = calculateEnhancedRiskScore(
                riskScore, riskType, riskIndicators, profile);

            // Update profile
            profile.updateRiskScore(enhancedScore, riskType, timestamp);

            // Store risk score update
            storeRiskScoreUpdate(merchantId, enhancedScore, riskType, riskIndicators, timestamp);

            // Check for risk threshold violations
            checkRiskThresholdViolations(merchantId, enhancedScore, riskType);

            log.info("Risk score updated for merchant: {} - New Score: {}", merchantId, enhancedScore);

        } catch (Exception e) {
            log.error("Failed to update merchant risk score: {}", merchantId, e);
        }
    }

    /**
     * Analyzes chargeback patterns
     */
    public void analyzeChargebackPatterns(
            String merchantId,
            Double chargebackRatio,
            BigDecimal processingVolume,
            LocalDateTime timestamp) {

        try {
            log.debug("Analyzing chargeback patterns for merchant: {} - Ratio: {}%", 
                merchantId, chargebackRatio);

            // Store chargeback metrics
            storeChargebackMetrics(merchantId, chargebackRatio, processingVolume, timestamp);

            // Analyze historical chargeback trends
            ChargebackTrend trend = analyzeChargebackTrends(merchantId, chargebackRatio, timestamp);

            // Check chargeback ratio violations
            if (chargebackRatio != null && chargebackRatio > chargebackThreshold) {
                handleChargebackViolation(merchantId, chargebackRatio, processingVolume, trend);
            }

            // Update chargeback risk factors
            updateChargebackRiskFactors(merchantId, chargebackRatio, trend, timestamp);

            // Generate chargeback prevention recommendations
            generateChargebackPreventionRecommendations(merchantId, trend);

            log.info("Chargeback analysis completed for merchant: {} - Trend: {}", 
                merchantId, trend.getDirection());

        } catch (Exception e) {
            log.error("Failed to analyze chargeback patterns for merchant: {}", merchantId, e);
        }
    }

    /**
     * Assesses fraud risk patterns
     */
    public void assessFraudRiskPatterns(
            String merchantId,
            Map<String, String> riskIndicators,
            String alertReason,
            LocalDateTime timestamp) {

        try {
            log.debug("Assessing fraud risk patterns for merchant: {}", merchantId);

            // Analyze fraud indicators
            FraudRiskAssessment assessment = analyzeFraudIndicators(merchantId, riskIndicators);

            // Check for fraud patterns
            boolean fraudPatternDetected = detectFraudPatterns(merchantId, riskIndicators, timestamp);

            // Update fraud risk score
            double fraudRiskScore = calculateFraudRiskScore(assessment, fraudPatternDetected);
            
            // Store fraud risk assessment
            storeFraudRiskAssessment(merchantId, assessment, fraudRiskScore, timestamp);

            // Take immediate action if fraud detected
            if (fraudPatternDetected) {
                handleFraudDetection(merchantId, alertReason, fraudRiskScore);
            }

            // Update merchant fraud monitoring
            updateFraudMonitoring(merchantId, fraudRiskScore, timestamp);

            log.info("Fraud risk assessment completed for merchant: {} - Risk Score: {}", 
                merchantId, fraudRiskScore);

        } catch (Exception e) {
            log.error("Failed to assess fraud risk patterns for merchant: {}", merchantId, e);
        }
    }

    /**
     * Updates merchant risk profile
     */
    public void updateMerchantRiskProfile(
            String merchantId,
            String riskType,
            String severity,
            LocalDateTime timestamp) {

        try {
            log.debug("Updating risk profile for merchant: {}", merchantId);

            // Get or create risk profile
            MerchantRiskProfile profile = riskProfiles.computeIfAbsent(
                merchantId, k -> new MerchantRiskProfile(k));

            // Update profile based on risk type
            switch (riskType.toUpperCase()) {
                case "HIGH_CHARGEBACK_RATIO":
                    profile.updateChargebackRisk(severity, timestamp);
                    break;
                case "FRAUD_PATTERN_DETECTED":
                    profile.updateFraudRisk(severity, timestamp);
                    break;
                case "COMPLIANCE_VIOLATION":
                    profile.updateComplianceRisk(severity, timestamp);
                    break;
                case "HIGH_RISK_MERCHANT":
                    profile.updateOverallRisk(severity, timestamp);
                    break;
            }

            // Calculate composite risk score
            double compositeScore = calculateCompositeRiskScore(profile);
            profile.setCompositeRiskScore(compositeScore);

            // Store updated profile
            storeRiskProfile(profile);

            // Cache profile
            riskProfiles.put(merchantId, profile);

            log.info("Risk profile updated for merchant: {} - Composite Score: {}", 
                merchantId, compositeScore);

        } catch (Exception e) {
            log.error("Failed to update merchant risk profile: {}", merchantId, e);
        }
    }

    /**
     * Determines risk mitigation action
     */
    public String determineRiskMitigationAction(
            String riskType,
            String severity,
            Double riskScore,
            Double chargebackRatio,
            Map<String, String> previousAlerts) {

        try {
            log.debug("Determining risk mitigation action - Type: {}, Severity: {}", riskType, severity);

            // Emergency actions
            if ("EMERGENCY".equals(severity)) {
                return "IMMEDIATE_BLOCK";
            }

            // Critical actions
            if ("CRITICAL".equals(severity)) {
                if (riskType.contains("FRAUD")) {
                    return "SUSPEND_PROCESSING_IMMEDIATE";
                } else if (riskType.contains("CHARGEBACK") && chargebackRatio != null && chargebackRatio > 5.0) {
                    return "TERMINATE_RELATIONSHIP";
                } else {
                    return "SUSPEND_PROCESSING_24H";
                }
            }

            // High severity actions
            if ("HIGH".equals(severity)) {
                if (riskScore != null && riskScore > 85.0) {
                    return "IMPOSE_ROLLING_RESERVE";
                } else if (chargebackRatio != null && chargebackRatio > 3.0) {
                    return "LIMIT_PROCESSING_VOLUME";
                } else {
                    return "ENHANCED_MONITORING";
                }
            }

            // Check previous alerts for escalation
            if (previousAlerts != null && previousAlerts.size() > 3) {
                return "ESCALATE_TO_UNDERWRITING";
            }

            // Default actions
            if ("MEDIUM".equals(severity)) {
                return "ENHANCED_MONITORING";
            }

            return "CONTINUE_MONITORING";

        } catch (Exception e) {
            log.error("Failed to determine risk mitigation action", e);
            return "MANUAL_REVIEW";
        }
    }

    /**
     * Executes risk mitigation action
     */
    public void executeRiskMitigationAction(
            String merchantId,
            String action,
            String reason,
            LocalDateTime timestamp) {

        try {
            log.info("Executing risk mitigation action for merchant: {} - Action: {}", 
                merchantId, action);

            // Store action record
            String actionKey = "merchant:risk:actions:" + UUID.randomUUID().toString();
            Map<String, String> actionData = Map.of(
                "merchant_id", merchantId,
                "action", action,
                "reason", reason != null ? reason : "",
                "executed_at", timestamp.toString(),
                "status", "EXECUTED"
            );

            redisTemplate.opsForHash().putAll(actionKey, actionData);
            redisTemplate.expire(actionKey, Duration.ofDays(riskRetentionDays));

            // Update merchant action history
            String historyKey = "merchant:risk:action_history:" + merchantId;
            redisTemplate.opsForList().rightPush(historyKey, actionKey);
            redisTemplate.expire(historyKey, Duration.ofDays(riskRetentionDays));

            // Create action timeline entry
            createActionTimelineEntry(merchantId, action, reason, timestamp);

        } catch (Exception e) {
            log.error("Failed to execute risk mitigation action for merchant: {}", merchantId, e);
        }
    }

    /**
     * Escalates to underwriting team
     */
    public void escalateToUnderwriting(
            String merchantId,
            String riskType,
            String severity,
            Double riskScore) {

        try {
            log.warn("Escalating merchant to underwriting: {} - Risk Type: {}, Score: {}", 
                merchantId, riskType, riskScore);

            String escalationKey = "merchant:risk:underwriting_escalation:" + UUID.randomUUID().toString();
            Map<String, String> escalationData = Map.of(
                "merchant_id", merchantId,
                "risk_type", riskType,
                "severity", severity,
                "risk_score", riskScore != null ? riskScore.toString() : "0",
                "escalated_at", LocalDateTime.now().toString(),
                "status", "PENDING_REVIEW"
            );

            redisTemplate.opsForHash().putAll(escalationKey, escalationData);
            redisTemplate.expire(escalationKey, Duration.ofDays(30));

            // Add to underwriting queue
            String queueKey = "merchant:risk:underwriting_queue";
            redisTemplate.opsForList().rightPush(queueKey, escalationKey);
            redisTemplate.expire(queueKey, Duration.ofDays(30));

        } catch (Exception e) {
            log.error("Failed to escalate to underwriting for merchant: {}", merchantId, e);
        }
    }

    /**
     * Updates alert history
     */
    public void updateAlertHistory(
            String merchantId,
            String alertId,
            String riskType,
            String severity,
            LocalDateTime timestamp) {

        try {
            String historyKey = "merchant:risk:alert_history:" + merchantId;
            String alertEntry = String.format("%s:%s:%s:%s", 
                alertId, riskType, severity, timestamp.toString());
            
            redisTemplate.opsForList().rightPush(historyKey, alertEntry);
            redisTemplate.expire(historyKey, Duration.ofDays(riskRetentionDays));

            // Limit history size
            Long historySize = redisTemplate.opsForList().size(historyKey);
            if (historySize != null && historySize > 100) {
                redisTemplate.opsForList().trim(historyKey, -100, -1);
            }

        } catch (Exception e) {
            log.error("Failed to update alert history for merchant: {}", merchantId, e);
        }
    }

    /**
     * Updates monitoring configuration
     */
    public void updateMonitoringConfiguration(
            String merchantId,
            String riskType,
            String severity) {

        try {
            String configKey = "merchant:risk:monitoring_config:" + merchantId;
            Map<String, String> config = Map.of(
                "monitoring_level", determineMonitoringLevel(severity),
                "check_frequency", determineCheckFrequency(riskType, severity),
                "alert_threshold", determineAlertThreshold(severity),
                "updated_at", LocalDateTime.now().toString()
            );

            redisTemplate.opsForHash().putAll(configKey, config);
            redisTemplate.expire(configKey, Duration.ofDays(90));

        } catch (Exception e) {
            log.error("Failed to update monitoring configuration for merchant: {}", merchantId, e);
        }
    }

    /**
     * Updates risk profile in storage
     */
    public void updateRiskProfile(
            String merchantId,
            String riskType,
            String severity,
            Double riskScore,
            Double chargebackRatio,
            LocalDateTime timestamp) {

        try {
            String profileKey = "merchant:risk:profile:" + merchantId;
            Map<String, String> profileData = Map.of(
                "merchant_id", merchantId,
                "current_risk_score", riskScore != null ? riskScore.toString() : "0",
                "chargeback_ratio", chargebackRatio != null ? chargebackRatio.toString() : "0",
                "last_risk_type", riskType,
                "last_severity", severity,
                "risk_level", getRiskLevel(riskScore),
                "last_updated", timestamp.toString()
            );

            redisTemplate.opsForHash().putAll(profileKey, profileData);
            redisTemplate.expire(profileKey, Duration.ofDays(riskRetentionDays));

        } catch (Exception e) {
            log.error("Failed to update risk profile for merchant: {}", merchantId, e);
        }
    }

    // Helper methods

    private double calculateEnhancedRiskScore(
            Double baseScore,
            String riskType,
            Map<String, String> riskIndicators,
            MerchantRiskProfile profile) {

        double score = baseScore != null ? baseScore : 50.0;

        // Adjust for risk type
        switch (riskType.toUpperCase()) {
            case "FRAUD_PATTERN_DETECTED":
                score += 20.0;
                break;
            case "HIGH_CHARGEBACK_RATIO":
                score += 15.0;
                break;
            case "COMPLIANCE_VIOLATION":
                score += 10.0;
                break;
        }

        // Adjust for risk indicators
        if (riskIndicators != null) {
            score += riskIndicators.size() * 5.0;
        }

        // Adjust for historical pattern
        if (profile.getAlertCount() > 5) {
            score += 10.0;
        }

        return Math.min(score, 100.0);
    }

    private void storeRiskScoreUpdate(
            String merchantId,
            double riskScore,
            String riskType,
            Map<String, String> riskIndicators,
            LocalDateTime timestamp) {

        try {
            String updateKey = "merchant:risk:score_updates:" + merchantId + ":" + System.currentTimeMillis();
            Map<String, String> updateData = Map.of(
                "merchant_id", merchantId,
                "risk_score", String.valueOf(riskScore),
                "risk_type", riskType,
                "indicators", riskIndicators != null ? riskIndicators.toString() : "",
                "updated_at", timestamp.toString()
            );

            redisTemplate.opsForHash().putAll(updateKey, updateData);
            redisTemplate.expire(updateKey, Duration.ofDays(90));

        } catch (Exception e) {
            log.error("Failed to store risk score update", e);
        }
    }

    private void checkRiskThresholdViolations(String merchantId, double riskScore, String riskType) {
        if (riskScore > highRiskThreshold) {
            log.warn("High risk threshold violated for merchant: {} - Score: {} - Type: {}", 
                merchantId, riskScore, riskType);
            
            // Create threshold violation alert
            String violationKey = "merchant:risk:threshold_violations:" + UUID.randomUUID().toString();
            Map<String, String> violationData = Map.of(
                "merchant_id", merchantId,
                "risk_score", String.valueOf(riskScore),
                "threshold", String.valueOf(highRiskThreshold),
                "risk_type", riskType,
                "detected_at", LocalDateTime.now().toString()
            );

            redisTemplate.opsForHash().putAll(violationKey, violationData);
            redisTemplate.expire(violationKey, Duration.ofDays(30));
        }
    }

    private void storeChargebackMetrics(
            String merchantId,
            Double chargebackRatio,
            BigDecimal processingVolume,
            LocalDateTime timestamp) {

        try {
            String metricsKey = "merchant:risk:chargeback_metrics:" + merchantId + ":" + 
                timestamp.toLocalDate();
            
            Map<String, String> metricsData = Map.of(
                "merchant_id", merchantId,
                "chargeback_ratio", chargebackRatio != null ? chargebackRatio.toString() : "0",
                "processing_volume", processingVolume != null ? processingVolume.toString() : "0",
                "date", timestamp.toLocalDate().toString()
            );

            redisTemplate.opsForHash().putAll(metricsKey, metricsData);
            redisTemplate.expire(metricsKey, Duration.ofDays(90));

        } catch (Exception e) {
            log.error("Failed to store chargeback metrics", e);
        }
    }

    private ChargebackTrend analyzeChargebackTrends(
            String merchantId,
            Double currentRatio,
            LocalDateTime timestamp) {

        // Simplified trend analysis - would use historical data in production
        return ChargebackTrend.builder()
            .merchantId(merchantId)
            .currentRatio(currentRatio)
            .direction(currentRatio != null && currentRatio > chargebackThreshold ? "INCREASING" : "STABLE")
            .severity(currentRatio != null && currentRatio > 3.0 ? "HIGH" : "MEDIUM")
            .analysisDate(timestamp)
            .build();
    }

    private void handleChargebackViolation(
            String merchantId,
            Double chargebackRatio,
            BigDecimal processingVolume,
            ChargebackTrend trend) {

        log.warn("Chargeback violation detected for merchant: {} - Ratio: {}%", 
            merchantId, chargebackRatio);

        String violationKey = "merchant:risk:chargeback_violations:" + UUID.randomUUID().toString();
        Map<String, String> violationData = Map.of(
            "merchant_id", merchantId,
            "chargeback_ratio", chargebackRatio.toString(),
            "threshold", String.valueOf(chargebackThreshold),
            "processing_volume", processingVolume != null ? processingVolume.toString() : "0",
            "trend", trend.getDirection(),
            "detected_at", LocalDateTime.now().toString()
        );

        redisTemplate.opsForHash().putAll(violationKey, violationData);
        redisTemplate.expire(violationKey, Duration.ofDays(90));
    }

    private void updateChargebackRiskFactors(
            String merchantId,
            Double chargebackRatio,
            ChargebackTrend trend,
            LocalDateTime timestamp) {

        try {
            String factorsKey = "merchant:risk:chargeback_factors:" + merchantId;
            Map<String, String> factors = Map.of(
                "current_ratio", chargebackRatio != null ? chargebackRatio.toString() : "0",
                "trend_direction", trend.getDirection(),
                "risk_level", determineChargebackRiskLevel(chargebackRatio),
                "updated_at", timestamp.toString()
            );

            redisTemplate.opsForHash().putAll(factorsKey, factors);
            redisTemplate.expire(factorsKey, Duration.ofDays(riskRetentionDays));

        } catch (Exception e) {
            log.error("Failed to update chargeback risk factors", e);
        }
    }

    private void generateChargebackPreventionRecommendations(String merchantId, ChargebackTrend trend) {
        // Generate recommendations based on trend analysis
        String recommendationsKey = "merchant:risk:chargeback_recommendations:" + merchantId;
        Map<String, String> recommendations = Map.of(
            "primary_action", "IMPLEMENT_3DS",
            "secondary_action", "ENHANCE_FRAUD_SCREENING",
            "tertiary_action", "IMPROVE_CUSTOMER_SERVICE",
            "generated_at", LocalDateTime.now().toString()
        );

        redisTemplate.opsForHash().putAll(recommendationsKey, recommendations);
        redisTemplate.expire(recommendationsKey, Duration.ofDays(30));
    }

    private FraudRiskAssessment analyzeFraudIndicators(String merchantId, Map<String, String> riskIndicators) {
        return FraudRiskAssessment.builder()
            .merchantId(merchantId)
            .indicatorCount(riskIndicators != null ? riskIndicators.size() : 0)
            .highRiskIndicators(countHighRiskIndicators(riskIndicators))
            .assessmentScore(calculateIndicatorScore(riskIndicators))
            .build();
    }

    private boolean detectFraudPatterns(String merchantId, Map<String, String> riskIndicators, LocalDateTime timestamp) {
        // Simplified fraud pattern detection
        return riskIndicators != null && 
               (riskIndicators.containsKey("VELOCITY_FRAUD") || 
                riskIndicators.containsKey("CARD_TESTING") ||
                riskIndicators.containsKey("STOLEN_CARD"));
    }

    private double calculateFraudRiskScore(FraudRiskAssessment assessment, boolean fraudPatternDetected) {
        double score = assessment.getAssessmentScore();
        if (fraudPatternDetected) {
            score += 30.0;
        }
        return Math.min(score, 100.0);
    }

    private void storeFraudRiskAssessment(
            String merchantId,
            FraudRiskAssessment assessment,
            double fraudRiskScore,
            LocalDateTime timestamp) {

        try {
            String assessmentKey = "merchant:risk:fraud_assessment:" + merchantId;
            Map<String, String> assessmentData = Map.of(
                "merchant_id", merchantId,
                "fraud_risk_score", String.valueOf(fraudRiskScore),
                "indicator_count", String.valueOf(assessment.getIndicatorCount()),
                "high_risk_indicators", String.valueOf(assessment.getHighRiskIndicators()),
                "assessed_at", timestamp.toString()
            );

            redisTemplate.opsForHash().putAll(assessmentKey, assessmentData);
            redisTemplate.expire(assessmentKey, Duration.ofDays(riskRetentionDays));

        } catch (Exception e) {
            log.error("Failed to store fraud risk assessment", e);
        }
    }

    private void handleFraudDetection(String merchantId, String alertReason, double fraudRiskScore) {
        log.error("FRAUD DETECTED for merchant: {} - Reason: {} - Score: {}", 
            merchantId, alertReason, fraudRiskScore);

        String detectionKey = "merchant:risk:fraud_detection:" + UUID.randomUUID().toString();
        Map<String, String> detectionData = Map.of(
            "merchant_id", merchantId,
            "alert_reason", alertReason,
            "fraud_risk_score", String.valueOf(fraudRiskScore),
            "detected_at", LocalDateTime.now().toString(),
            "status", "ACTIVE"
        );

        redisTemplate.opsForHash().putAll(detectionKey, detectionData);
        redisTemplate.expire(detectionKey, Duration.ofDays(riskRetentionDays));
    }

    private void updateFraudMonitoring(String merchantId, double fraudRiskScore, LocalDateTime timestamp) {
        String monitoringKey = "merchant:risk:fraud_monitoring:" + merchantId;
        Map<String, String> monitoringData = Map.of(
            "fraud_risk_score", String.valueOf(fraudRiskScore),
            "monitoring_level", fraudRiskScore > 70.0 ? "HIGH" : "MEDIUM",
            "last_updated", timestamp.toString()
        );

        redisTemplate.opsForHash().putAll(monitoringKey, monitoringData);
        redisTemplate.expire(monitoringKey, Duration.ofDays(riskRetentionDays));
    }

    private double calculateCompositeRiskScore(MerchantRiskProfile profile) {
        return (profile.getFraudRiskScore() * 0.4) +
               (profile.getChargebackRiskScore() * 0.3) +
               (profile.getComplianceRiskScore() * 0.2) +
               (profile.getOverallRiskScore() * 0.1);
    }

    private void storeRiskProfile(MerchantRiskProfile profile) {
        try {
            String profileKey = "merchant:risk:profile:" + profile.getMerchantId();
            Map<String, String> profileData = Map.of(
                "merchant_id", profile.getMerchantId(),
                "composite_risk_score", String.valueOf(profile.getCompositeRiskScore()),
                "fraud_risk_score", String.valueOf(profile.getFraudRiskScore()),
                "chargeback_risk_score", String.valueOf(profile.getChargebackRiskScore()),
                "compliance_risk_score", String.valueOf(profile.getComplianceRiskScore()),
                "overall_risk_score", String.valueOf(profile.getOverallRiskScore()),
                "alert_count", String.valueOf(profile.getAlertCount()),
                "last_updated", profile.getLastUpdated().toString()
            );

            redisTemplate.opsForHash().putAll(profileKey, profileData);
            redisTemplate.expire(profileKey, Duration.ofDays(riskRetentionDays));

        } catch (Exception e) {
            log.error("Failed to store risk profile", e);
        }
    }

    private void createActionTimelineEntry(String merchantId, String action, String reason, LocalDateTime timestamp) {
        try {
            String timelineKey = "merchant:risk:timeline:" + merchantId + ":" + System.currentTimeMillis();
            Map<String, String> timelineData = Map.of(
                "merchant_id", merchantId,
                "action", action,
                "reason", reason != null ? reason : "",
                "timestamp", timestamp.toString()
            );

            redisTemplate.opsForHash().putAll(timelineKey, timelineData);
            redisTemplate.expire(timelineKey, Duration.ofDays(riskRetentionDays));

        } catch (Exception e) {
            log.error("Failed to create action timeline entry", e);
        }
    }

    // Additional helper methods

    private String getRiskLevel(Double riskScore) {
        if (riskScore == null) return "UNKNOWN";
        if (riskScore >= 80.0) return "VERY_HIGH";
        if (riskScore >= 60.0) return "HIGH";
        if (riskScore >= 40.0) return "MEDIUM";
        return "LOW";
    }

    private String determineMonitoringLevel(String severity) {
        switch (severity.toUpperCase()) {
            case "CRITICAL":
            case "EMERGENCY":
                return "CONTINUOUS";
            case "HIGH":
                return "ENHANCED";
            case "MEDIUM":
                return "STANDARD";
            default:
                return "BASIC";
        }
    }

    private String determineCheckFrequency(String riskType, String severity) {
        if ("EMERGENCY".equals(severity) || "CRITICAL".equals(severity)) {
            return "HOURLY";
        } else if ("HIGH".equals(severity)) {
            return "DAILY";
        } else {
            return "WEEKLY";
        }
    }

    private String determineAlertThreshold(String severity) {
        switch (severity.toUpperCase()) {
            case "CRITICAL":
            case "EMERGENCY":
                return "0.5";
            case "HIGH":
                return "1.0";
            case "MEDIUM":
                return "2.0";
            default:
                return "5.0";
        }
    }

    private String determineChargebackRiskLevel(Double chargebackRatio) {
        if (chargebackRatio == null) return "UNKNOWN";
        if (chargebackRatio >= 5.0) return "CRITICAL";
        if (chargebackRatio >= 3.0) return "HIGH";
        if (chargebackRatio >= 1.0) return "MEDIUM";
        return "LOW";
    }

    private int countHighRiskIndicators(Map<String, String> riskIndicators) {
        if (riskIndicators == null) return 0;
        
        int count = 0;
        String[] highRiskKeys = {"VELOCITY_FRAUD", "CARD_TESTING", "STOLEN_CARD", "CHARGEBACK_FRAUD"};
        
        for (String key : highRiskKeys) {
            if (riskIndicators.containsKey(key)) {
                count++;
            }
        }
        
        return count;
    }

    private double calculateIndicatorScore(Map<String, String> riskIndicators) {
        if (riskIndicators == null) return 0.0;
        
        double score = riskIndicators.size() * 5.0; // Base score
        score += countHighRiskIndicators(riskIndicators) * 10.0; // High risk bonus
        
        return Math.min(score, 100.0);
    }

    // Data structures

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class ChargebackTrend {
        private String merchantId;
        private Double currentRatio;
        private String direction;
        private String severity;
        private LocalDateTime analysisDate;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class FraudRiskAssessment {
        private String merchantId;
        private int indicatorCount;
        private int highRiskIndicators;
        private double assessmentScore;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class MerchantRiskProfile {
        private String merchantId;
        private double compositeRiskScore = 0.0;
        private double fraudRiskScore = 0.0;
        private double chargebackRiskScore = 0.0;
        private double complianceRiskScore = 0.0;
        private double overallRiskScore = 0.0;
        private int alertCount = 0;
        private LocalDateTime lastUpdated;

        public MerchantRiskProfile(String merchantId) {
            this.merchantId = merchantId;
            this.lastUpdated = LocalDateTime.now();
        }

        public void updateRiskScore(double score, String riskType, LocalDateTime timestamp) {
            this.overallRiskScore = score;
            this.lastUpdated = timestamp;
            this.alertCount++;
        }

        public void updateChargebackRisk(String severity, LocalDateTime timestamp) {
            this.chargebackRiskScore = mapSeverityToScore(severity);
            this.lastUpdated = timestamp;
        }

        public void updateFraudRisk(String severity, LocalDateTime timestamp) {
            this.fraudRiskScore = mapSeverityToScore(severity);
            this.lastUpdated = timestamp;
        }

        public void updateComplianceRisk(String severity, LocalDateTime timestamp) {
            this.complianceRiskScore = mapSeverityToScore(severity);
            this.lastUpdated = timestamp;
        }

        public void updateOverallRisk(String severity, LocalDateTime timestamp) {
            this.overallRiskScore = mapSeverityToScore(severity);
            this.lastUpdated = timestamp;
        }

        private double mapSeverityToScore(String severity) {
            switch (severity.toUpperCase()) {
                case "EMERGENCY": return 95.0;
                case "CRITICAL": return 85.0;
                case "HIGH": return 70.0;
                case "MEDIUM": return 50.0;
                case "LOW": return 25.0;
                default: return 50.0;
            }
        }
    }
}