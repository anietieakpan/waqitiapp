package com.waqiti.compliance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Compliance Review Service - Handles comprehensive compliance review processing
 * 
 * Provides comprehensive compliance capabilities for:
 * - Risk assessment and scoring for customers and transactions
 * - Enhanced due diligence (EDD) triggers and processing
 * - Transaction pattern analysis for unusual activity detection
 * - OFAC sanctions screening and match processing
 * - BSA/AML compliance analysis and reporting
 * - Compliance action determination and execution
 * - Case management and status tracking
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceReviewService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${compliance.risk.threshold.high:80.0}")
    private double highRiskThreshold;

    @Value("${compliance.risk.threshold.medium:50.0}")
    private double mediumRiskThreshold;

    @Value("${compliance.pattern.analysis.days:30}")
    private int patternAnalysisDays;

    @Value("${compliance.ofac.enabled:true}")
    private boolean ofacScreeningEnabled;

    // Cache for tracking risk assessments
    private final Map<String, RiskAssessment> riskAssessments = new ConcurrentHashMap<>();

    /**
     * Performs comprehensive risk assessment
     */
    public void performRiskAssessment(
            String reviewId,
            String customerId,
            String accountId,
            String transactionId,
            BigDecimal amount,
            String currency,
            Double riskScore,
            Map<String, String> alertTriggers,
            LocalDateTime timestamp) {

        try {
            log.debug("Performing risk assessment for review: {}", reviewId);

            // Create risk assessment
            RiskAssessment assessment = RiskAssessment.builder()
                .reviewId(reviewId)
                .customerId(customerId)
                .accountId(accountId)
                .transactionId(transactionId)
                .amount(amount)
                .currency(currency)
                .initialRiskScore(riskScore)
                .alertTriggers(alertTriggers)
                .timestamp(timestamp)
                .build();

            // Calculate enhanced risk score
            double enhancedRiskScore = calculateEnhancedRiskScore(assessment);
            assessment.setEnhancedRiskScore(enhancedRiskScore);

            // Store risk assessment
            storeRiskAssessment(assessment);

            // Update customer risk profile
            updateCustomerRiskProfile(customerId, enhancedRiskScore, timestamp);

            // Cache assessment for later use
            riskAssessments.put(reviewId, assessment);

            log.info("Risk assessment completed for review: {} - Risk Score: {}", 
                reviewId, enhancedRiskScore);

        } catch (Exception e) {
            log.error("Failed to perform risk assessment for review: {}", reviewId, e);
        }
    }

    /**
     * Triggers enhanced due diligence
     */
    public void triggerEnhancedDueDiligence(
            String customerId,
            String reviewId,
            Double riskScore,
            String reason) {

        try {
            log.info("Triggering enhanced due diligence for customer: {} - Reason: {}", 
                customerId, reason);

            // Create EDD record
            String eddId = UUID.randomUUID().toString();
            Map<String, String> eddData = Map.of(
                "edd_id", eddId,
                "customer_id", customerId,
                "review_id", reviewId,
                "risk_score", riskScore.toString(),
                "reason", reason,
                "status", "PENDING",
                "triggered_at", LocalDateTime.now().toString()
            );

            String eddKey = "compliance:edd:" + eddId;
            redisTemplate.opsForHash().putAll(eddKey, eddData);
            redisTemplate.expire(eddKey, Duration.ofDays(365));

            // Add to EDD queue
            String queueKey = "compliance:edd:queue";
            redisTemplate.opsForList().rightPush(queueKey, eddId);
            redisTemplate.expire(queueKey, Duration.ofDays(30));

            // Update customer status
            String customerStatusKey = "compliance:customer:status:" + customerId;
            redisTemplate.opsForValue().set(customerStatusKey, "EDD_REQUIRED");
            redisTemplate.expire(customerStatusKey, Duration.ofDays(90));

        } catch (Exception e) {
            log.error("Failed to trigger enhanced due diligence", e);
        }
    }

    /**
     * Analyzes transaction patterns for unusual activity
     */
    public void analyzeTransactionPatterns(
            String customerId,
            String accountId,
            BigDecimal amount,
            String currency,
            LocalDateTime timestamp) {

        try {
            log.debug("Analyzing transaction patterns for customer: {}", customerId);

            // Get historical transaction data
            String patternKey = "compliance:patterns:" + customerId;
            Map<Object, Object> patternData = redisTemplate.opsForHash().entries(patternKey);

            // Analyze velocity patterns
            boolean velocityAnomaly = analyzeVelocityPatterns(customerId, amount, currency, timestamp);

            // Analyze amount patterns
            boolean amountAnomaly = analyzeAmountPatterns(customerId, amount, currency, timestamp);

            // Analyze frequency patterns
            boolean frequencyAnomaly = analyzeFrequencyPatterns(customerId, timestamp);

            // Store pattern analysis results
            if (velocityAnomaly || amountAnomaly || frequencyAnomaly) {
                String anomalyKey = "compliance:anomalies:" + customerId + ":" + System.currentTimeMillis();
                Map<String, String> anomalyData = Map.of(
                    "customer_id", customerId,
                    "account_id", accountId != null ? accountId : "",
                    "amount", amount != null ? amount.toString() : "0",
                    "currency", currency,
                    "velocity_anomaly", String.valueOf(velocityAnomaly),
                    "amount_anomaly", String.valueOf(amountAnomaly),
                    "frequency_anomaly", String.valueOf(frequencyAnomaly),
                    "detected_at", timestamp.toString()
                );

                redisTemplate.opsForHash().putAll(anomalyKey, anomalyData);
                redisTemplate.expire(anomalyKey, Duration.ofDays(90));

                log.warn("Transaction pattern anomaly detected for customer: {}", customerId);
            }

            // Update pattern tracking data
            updatePatternTrackingData(customerId, amount, currency, timestamp);

        } catch (Exception e) {
            log.error("Failed to analyze transaction patterns", e);
        }
    }

    /**
     * Performs OFAC sanctions screening
     */
    public boolean performOFACScreening(
            String customerId,
            String reviewId,
            Map<String, String> alertTriggers) {

        if (!ofacScreeningEnabled) {
            return false;
        }

        try {
            log.debug("Performing OFAC screening for customer: {}", customerId);

            // Check if customer is on OFAC watchlist (simplified implementation)
            String ofacKey = "compliance:ofac:watchlist:" + customerId;
            Boolean isWatchlisted = redisTemplate.hasKey(ofacKey);

            // Check alert triggers for OFAC matches
            boolean hasOfacAlert = alertTriggers != null && 
                (alertTriggers.containsKey("OFAC") || alertTriggers.containsKey("SANCTIONS"));

            boolean ofacMatch = Boolean.TRUE.equals(isWatchlisted) || hasOfacAlert;

            if (ofacMatch) {
                // Create OFAC match record
                String matchId = UUID.randomUUID().toString();
                String matchKey = "compliance:ofac:matches:" + matchId;
                Map<String, String> matchData = Map.of(
                    "match_id", matchId,
                    "customer_id", customerId,
                    "review_id", reviewId,
                    "match_type", hasOfacAlert ? "ALERT_TRIGGERED" : "WATCHLIST_MATCH",
                    "status", "PENDING_REVIEW",
                    "detected_at", LocalDateTime.now().toString()
                );

                redisTemplate.opsForHash().putAll(matchKey, matchData);
                redisTemplate.expire(matchKey, Duration.ofDays(365));

                // Add to high-priority review queue
                String priorityQueueKey = "compliance:ofac:priority_queue";
                redisTemplate.opsForList().leftPush(priorityQueueKey, matchId);
                redisTemplate.expire(priorityQueueKey, Duration.ofDays(7));

                log.error("OFAC sanctions match detected for customer: {}", customerId);
            }

            return ofacMatch;

        } catch (Exception e) {
            log.error("Failed to perform OFAC screening", e);
            return false;
        }
    }

    /**
     * Performs BSA/AML compliance analysis
     */
    public void performBSAAnalysis(
            String reviewId,
            String customerId,
            String transactionId,
            BigDecimal amount,
            String currency,
            String description) {

        try {
            log.debug("Performing BSA/AML analysis for review: {}", reviewId);

            // Check for structured transactions
            boolean structuringRisk = detectStructuring(customerId, amount, currency);

            // Check for money laundering patterns
            boolean mlRisk = detectMoneyLaunderingPatterns(customerId, amount, description);

            // Check for unusual geographic patterns
            boolean geoRisk = detectUnusualGeographicActivity(customerId, transactionId);

            // Calculate BSA risk score
            double bsaRiskScore = calculateBSARiskScore(structuringRisk, mlRisk, geoRisk, amount);

            // Store BSA analysis results
            String bsaKey = "compliance:bsa:analysis:" + reviewId;
            Map<String, String> bsaData = Map.of(
                "review_id", reviewId,
                "customer_id", customerId,
                "transaction_id", transactionId != null ? transactionId : "",
                "structuring_risk", String.valueOf(structuringRisk),
                "ml_risk", String.valueOf(mlRisk),
                "geo_risk", String.valueOf(geoRisk),
                "bsa_risk_score", String.valueOf(bsaRiskScore),
                "analyzed_at", LocalDateTime.now().toString()
            );

            redisTemplate.opsForHash().putAll(bsaKey, bsaData);
            redisTemplate.expire(bsaKey, Duration.ofDays(90));

            // Trigger SAR if high risk
            if (bsaRiskScore > 75.0) {
                triggerSARReview(reviewId, customerId, transactionId, bsaRiskScore);
            }

        } catch (Exception e) {
            log.error("Failed to perform BSA/AML analysis", e);
        }
    }

    /**
     * Determines appropriate compliance action
     */
    public String determineComplianceAction(
            String reviewType,
            Double riskScore,
            BigDecimal amount,
            Map<String, String> alertTriggers,
            Map<String, String> previousCases) {

        try {
            log.debug("Determining compliance action for review type: {}", reviewType);

            // Critical actions for OFAC matches
            if (reviewType.contains("OFAC") || reviewType.contains("SANCTIONS")) {
                return "BLOCK_ACCOUNT_ESCALATE";
            }

            // High-risk score actions
            if (riskScore != null && riskScore >= highRiskThreshold) {
                return "ENHANCED_DUE_DILIGENCE_REQUIRED";
            }

            // Large transaction actions
            if (amount != null && amount.compareTo(new BigDecimal("10000")) > 0) {
                if (reviewType.contains("CTR")) {
                    return "CTR_FILING_REQUIRED";
                } else if (reviewType.contains("SAR")) {
                    return "SAR_FILING_REQUIRED";
                }
            }

            // Medium risk actions
            if (riskScore != null && riskScore >= mediumRiskThreshold) {
                return "ADDITIONAL_MONITORING_REQUIRED";
            }

            // Check for repeated violations
            if (previousCases != null && previousCases.size() > 2) {
                return "ESCALATE_TO_COMPLIANCE_OFFICER";
            }

            // Default action
            return "CONTINUE_MONITORING";

        } catch (Exception e) {
            log.error("Failed to determine compliance action", e);
            return "MANUAL_REVIEW_REQUIRED";
        }
    }

    /**
     * Executes determined compliance action
     */
    public void executeComplianceAction(
            String reviewId,
            String action,
            String customerId,
            String accountId,
            LocalDateTime timestamp) {

        try {
            log.info("Executing compliance action: {} for review: {}", action, reviewId);

            // Store action record
            String actionKey = "compliance:actions:" + reviewId;
            Map<String, String> actionData = Map.of(
                "review_id", reviewId,
                "action", action,
                "customer_id", customerId,
                "account_id", accountId != null ? accountId : "",
                "executed_at", timestamp.toString(),
                "status", "EXECUTED"
            );

            redisTemplate.opsForHash().putAll(actionKey, actionData);
            redisTemplate.expire(actionKey, Duration.ofDays(365));

            // Execute specific actions
            switch (action) {
                case "BLOCK_ACCOUNT_ESCALATE":
                    executeAccountBlock(customerId, accountId, reviewId);
                    break;
                case "ENHANCED_DUE_DILIGENCE_REQUIRED":
                    triggerEnhancedDueDiligence(customerId, reviewId, null, "Compliance action triggered");
                    break;
                case "CTR_FILING_REQUIRED":
                case "SAR_FILING_REQUIRED":
                    scheduleRegulatoryFiling(reviewId, action, customerId, timestamp);
                    break;
                case "ESCALATE_TO_COMPLIANCE_OFFICER":
                    escalateToComplianceOfficer(reviewId, customerId, action);
                    break;
            }

        } catch (Exception e) {
            log.error("Failed to execute compliance action", e);
        }
    }

    /**
     * Updates compliance case status and notes
     */
    public void updateComplianceCase(
            String reviewId,
            String reviewType,
            String status,
            String notes,
            LocalDateTime timestamp) {

        try {
            String caseKey = "compliance:cases:" + reviewId;
            Map<String, String> caseData = Map.of(
                "review_id", reviewId,
                "review_type", reviewType,
                "status", status,
                "notes", notes,
                "last_updated", timestamp.toString()
            );

            redisTemplate.opsForHash().putAll(caseKey, caseData);
            redisTemplate.expire(caseKey, Duration.ofDays(365));

        } catch (Exception e) {
            log.error("Failed to update compliance case", e);
        }
    }

    /**
     * Links related compliance cases
     */
    public void linkRelatedCases(String reviewId, Map<String, String> relatedCases) {
        try {
            String linkKey = "compliance:case_links:" + reviewId;
            redisTemplate.opsForHash().putAll(linkKey, relatedCases);
            redisTemplate.expire(linkKey, Duration.ofDays(365));

        } catch (Exception e) {
            log.error("Failed to link related cases", e);
        }
    }

    // Private helper methods

    private double calculateEnhancedRiskScore(RiskAssessment assessment) {
        double baseScore = assessment.getInitialRiskScore() != null ? 
            assessment.getInitialRiskScore() : 50.0;

        // Adjust for alert triggers
        if (assessment.getAlertTriggers() != null) {
            baseScore += assessment.getAlertTriggers().size() * 10.0;
        }

        // Adjust for transaction amount
        if (assessment.getAmount() != null) {
            if (assessment.getAmount().compareTo(new BigDecimal("100000")) > 0) {
                baseScore += 15.0;
            } else if (assessment.getAmount().compareTo(new BigDecimal("10000")) > 0) {
                baseScore += 5.0;
            }
        }

        return Math.min(baseScore, 100.0);
    }

    private void storeRiskAssessment(RiskAssessment assessment) {
        try {
            String assessmentKey = "compliance:risk_assessments:" + assessment.getReviewId();
            Map<String, String> assessmentData = Map.of(
                "review_id", assessment.getReviewId(),
                "customer_id", assessment.getCustomerId(),
                "initial_risk_score", assessment.getInitialRiskScore() != null ? 
                    assessment.getInitialRiskScore().toString() : "0",
                "enhanced_risk_score", String.valueOf(assessment.getEnhancedRiskScore()),
                "amount", assessment.getAmount() != null ? assessment.getAmount().toString() : "0",
                "currency", assessment.getCurrency(),
                "assessed_at", assessment.getTimestamp().toString()
            );

            redisTemplate.opsForHash().putAll(assessmentKey, assessmentData);
            redisTemplate.expire(assessmentKey, Duration.ofDays(90));

        } catch (Exception e) {
            log.error("Failed to store risk assessment", e);
        }
    }

    private void updateCustomerRiskProfile(String customerId, double riskScore, LocalDateTime timestamp) {
        try {
            String profileKey = "compliance:customer_risk:" + customerId;
            Map<String, String> profileData = Map.of(
                "customer_id", customerId,
                "current_risk_score", String.valueOf(riskScore),
                "risk_level", getRiskLevel(riskScore),
                "last_updated", timestamp.toString()
            );

            redisTemplate.opsForHash().putAll(profileKey, profileData);
            redisTemplate.expire(profileKey, Duration.ofDays(365));

        } catch (Exception e) {
            log.error("Failed to update customer risk profile", e);
        }
    }

    private boolean analyzeVelocityPatterns(String customerId, BigDecimal amount, String currency, LocalDateTime timestamp) {
        // Simplified velocity analysis - check transactions in last 24 hours
        String velocityKey = "compliance:velocity:" + customerId + ":" + timestamp.toLocalDate();
        Double dailyTotal = (Double) redisTemplate.opsForValue().get(velocityKey);
        
        if (dailyTotal != null && amount != null) {
            BigDecimal newTotal = BigDecimal.valueOf(dailyTotal).add(amount);
            // Flag if daily total exceeds $50,000
            return newTotal.compareTo(new BigDecimal("50000")) > 0;
        }
        
        return false;
    }

    private boolean analyzeAmountPatterns(String customerId, BigDecimal amount, String currency, LocalDateTime timestamp) {
        // Simplified amount analysis - check for round amounts which might indicate structuring
        if (amount != null) {
            BigDecimal remainder = amount.remainder(new BigDecimal("1000"));
            // Flag round thousands as potential structuring
            return remainder.compareTo(BigDecimal.ZERO) == 0 && 
                   amount.compareTo(new BigDecimal("9000")) < 0;
        }
        return false;
    }

    private boolean analyzeFrequencyPatterns(String customerId, LocalDateTime timestamp) {
        // Simplified frequency analysis - check transaction count in last hour
        String frequencyKey = "compliance:frequency:" + customerId + ":" + timestamp.getHour();
        Long hourlyCount = redisTemplate.opsForValue().increment(frequencyKey);
        redisTemplate.expire(frequencyKey, Duration.ofHours(1));
        
        // Flag if more than 10 transactions per hour
        return hourlyCount != null && hourlyCount > 10;
    }

    private void updatePatternTrackingData(String customerId, BigDecimal amount, String currency, LocalDateTime timestamp) {
        try {
            // Update daily transaction totals
            String velocityKey = "compliance:velocity:" + customerId + ":" + timestamp.toLocalDate();
            if (amount != null) {
                redisTemplate.opsForValue().increment(velocityKey, amount.doubleValue());
                redisTemplate.expire(velocityKey, Duration.ofDays(1));
            }
            
        } catch (Exception e) {
            log.error("Failed to update pattern tracking data", e);
        }
    }

    private boolean detectStructuring(String customerId, BigDecimal amount, String currency) {
        // Simplified structuring detection
        return amount != null && 
               amount.compareTo(new BigDecimal("9000")) < 0 && 
               amount.compareTo(new BigDecimal("7000")) > 0;
    }

    private boolean detectMoneyLaunderingPatterns(String customerId, BigDecimal amount, String description) {
        // Simplified ML detection based on description keywords
        if (description != null) {
            String upperDesc = description.toUpperCase();
            return upperDesc.contains("CASH") && upperDesc.contains("LOAN") ||
                   upperDesc.contains("GIFT") && amount != null && amount.compareTo(new BigDecimal("5000")) > 0;
        }
        return false;
    }

    private boolean detectUnusualGeographicActivity(String customerId, String transactionId) {
        try {
            String geoKey = "compliance:geographic:activity:" + customerId;
            Map<Object, Object> geoData = redisTemplate.opsForHash().entries(geoKey);
            
            if (geoData.isEmpty()) {
                return false;
            }
            
            Set<String> recentCountries = geoData.values().stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
            
            if (recentCountries.size() > 5) {
                return true;
            }
            
            Set<String> highRiskCountries = Set.of("AF", "IR", "KP", "SY", "YE", "IQ");
            long highRiskCount = recentCountries.stream()
                .filter(highRiskCountries::contains)
                .count();
            
            return highRiskCount > 0;
        } catch (Exception e) {
            log.error("Geographic risk detection failed for customer: {}", customerId, e);
            return false;
        }
    }

    private double calculateBSARiskScore(boolean structuringRisk, boolean mlRisk, boolean geoRisk, BigDecimal amount) {
        double score = 0.0;
        
        if (structuringRisk) score += 30.0;
        if (mlRisk) score += 25.0;
        if (geoRisk) score += 20.0;
        
        if (amount != null && amount.compareTo(new BigDecimal("10000")) > 0) {
            score += 15.0;
        }
        
        return Math.min(score, 100.0);
    }

    private void triggerSARReview(String reviewId, String customerId, String transactionId, double bsaRiskScore) {
        try {
            String sarKey = "compliance:sar:pending:" + UUID.randomUUID().toString();
            Map<String, String> sarData = Map.of(
                "review_id", reviewId,
                "customer_id", customerId,
                "transaction_id", transactionId != null ? transactionId : "",
                "bsa_risk_score", String.valueOf(bsaRiskScore),
                "status", "PENDING_REVIEW",
                "triggered_at", LocalDateTime.now().toString()
            );

            redisTemplate.opsForHash().putAll(sarKey, sarData);
            redisTemplate.expire(sarKey, Duration.ofDays(30));

            log.warn("SAR review triggered for customer: {} - Risk Score: {}", customerId, bsaRiskScore);

        } catch (Exception e) {
            log.error("Failed to trigger SAR review", e);
        }
    }

    private void executeAccountBlock(String customerId, String accountId, String reviewId) {
        try {
            String blockKey = "compliance:blocked_accounts:" + (accountId != null ? accountId : customerId);
            Map<String, String> blockData = Map.of(
                "customer_id", customerId,
                "account_id", accountId != null ? accountId : "",
                "review_id", reviewId,
                "reason", "OFAC_SANCTIONS_MATCH",
                "blocked_at", LocalDateTime.now().toString()
            );

            redisTemplate.opsForHash().putAll(blockKey, blockData);
            redisTemplate.expire(blockKey, Duration.ofDays(365));

            log.error("Account blocked for customer: {} - Review: {}", customerId, reviewId);

        } catch (Exception e) {
            log.error("Failed to execute account block", e);
        }
    }

    private void scheduleRegulatoryFiling(String reviewId, String filingType, String customerId, LocalDateTime timestamp) {
        try {
            String filingKey = "compliance:regulatory_filings:" + UUID.randomUUID().toString();
            Map<String, String> filingData = Map.of(
                "review_id", reviewId,
                "filing_type", filingType,
                "customer_id", customerId,
                "due_date", timestamp.plusDays(14).toString(), // 14-day filing requirement
                "status", "SCHEDULED",
                "scheduled_at", timestamp.toString()
            );

            redisTemplate.opsForHash().putAll(filingKey, filingData);
            redisTemplate.expire(filingKey, Duration.ofDays(30));

        } catch (Exception e) {
            log.error("Failed to schedule regulatory filing", e);
        }
    }

    private void escalateToComplianceOfficer(String reviewId, String customerId, String reason) {
        try {
            String escalationKey = "compliance:escalations:" + UUID.randomUUID().toString();
            Map<String, String> escalationData = Map.of(
                "review_id", reviewId,
                "customer_id", customerId,
                "reason", reason,
                "priority", "HIGH",
                "escalated_at", LocalDateTime.now().toString(),
                "status", "PENDING_REVIEW"
            );

            redisTemplate.opsForHash().putAll(escalationKey, escalationData);
            redisTemplate.expire(escalationKey, Duration.ofDays(7));

        } catch (Exception e) {
            log.error("Failed to escalate to compliance officer", e);
        }
    }

    private String getRiskLevel(double riskScore) {
        if (riskScore >= highRiskThreshold) return "HIGH";
        if (riskScore >= mediumRiskThreshold) return "MEDIUM";
        return "LOW";
    }

    /**
     * Risk assessment data structure
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class RiskAssessment {
        private String reviewId;
        private String customerId;
        private String accountId;
        private String transactionId;
        private BigDecimal amount;
        private String currency;
        private Double initialRiskScore;
        private double enhancedRiskScore;
        private Map<String, String> alertTriggers;
        private LocalDateTime timestamp;
    }
}