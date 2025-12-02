package com.waqiti.ledger.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Risk Management Service - Handles platform risk assessment and management
 * 
 * Provides comprehensive risk management capabilities for:
 * - Reserve adequacy assessment and risk coverage analysis
 * - High-value transaction risk evaluation and monitoring
 * - Platform exposure analysis and risk threshold management
 * - Regulatory risk compliance and capital adequacy assessment
 * - Operational risk monitoring and mitigation strategies
 * - Emergency risk response and crisis management
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskManagementService {

    private final RedisTemplate<String, Object> redisTemplate;

    // Risk assessment thresholds
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("50000");
    private static final BigDecimal CRITICAL_VALUE_THRESHOLD = new BigDecimal("100000");
    private static final BigDecimal RESERVE_ADEQUACY_RATIO = new BigDecimal("0.15"); // 15%
    private static final BigDecimal CRITICAL_RISK_THRESHOLD = new BigDecimal("0.85"); // 85%
    private static final BigDecimal HIGH_RISK_THRESHOLD = new BigDecimal("0.70"); // 70%

    @Value("${risk.management.enabled:true}")
    private boolean riskManagementEnabled;

    @Value("${risk.assessment.high.value.threshold:50000}")
    private BigDecimal configuredHighValueThreshold;

    @Value("${risk.assessment.reserve.adequacy.ratio:0.15}")
    private BigDecimal configuredReserveAdequacyRatio;

    /**
     * Assesses reserve adequacy for platform risk coverage
     * 
     * @param reserveRequestId Reserve request identifier
     * @param amount Reserve amount
     * @param currency Currency code
     * @param reserveType Type of reserve
     * @param riskLevel Risk level assessment
     * @param emergencyReserve Whether this is an emergency reserve
     */
    public void assessReserveAdequacy(
            String reserveRequestId,
            BigDecimal amount,
            String currency,
            PlatformReserveService.ReserveType reserveType,
            String riskLevel,
            boolean emergencyReserve) {

        if (!riskManagementEnabled) {
            log.debug("Risk management disabled, skipping reserve adequacy assessment");
            return;
        }

        try {
            log.debug("Assessing reserve adequacy for request: {} - Amount: {} {}", 
                reserveRequestId, amount, currency);

            // Calculate current platform exposure
            BigDecimal currentExposure = calculatePlatformExposure(currency, reserveType);
            
            // Calculate reserve adequacy ratio
            BigDecimal reserveAdequacyRatio = amount.divide(currentExposure, 4, RoundingMode.HALF_UP);
            
            // Assess adequacy against thresholds
            ReserveAdequacyAssessment assessment = performAdequacyAssessment(
                reserveRequestId, amount, currency, reserveType, 
                currentExposure, reserveAdequacyRatio, riskLevel, emergencyReserve);

            // Store assessment results
            storeReserveAdequacyAssessment(assessment);

            // Trigger alerts if needed
            if (assessment.getRiskLevel() == RiskLevel.CRITICAL) {
                triggerCriticalReserveRiskAlert(assessment);
            } else if (assessment.getRiskLevel() == RiskLevel.HIGH) {
                triggerHighReserveRiskAlert(assessment);
            }

            log.info("Reserve adequacy assessment completed for {}: {} (Adequacy: {}%)", 
                reserveRequestId, assessment.getRiskLevel(), 
                reserveAdequacyRatio.multiply(new BigDecimal("100")));

        } catch (Exception e) {
            log.error("Failed to assess reserve adequacy for request: {}", reserveRequestId, e);
        }
    }

    /**
     * Flags high-value reserve for additional monitoring
     * 
     * @param reserveRequestId Reserve request identifier
     * @param amount Reserve amount
     * @param reserveType Type of reserve
     * @param reason Reserve reason
     */
    public void flagHighValueReserve(
            String reserveRequestId,
            BigDecimal amount,
            PlatformReserveService.ReserveType reserveType,
            String reason) {

        try {
            log.info("Flagging high-value reserve: {} - Amount: {} - Type: {}", 
                reserveRequestId, amount, reserveType);

            // Create high-value reserve alert
            HighValueReserveAlert alert = HighValueReserveAlert.builder()
                .reserveRequestId(reserveRequestId)
                .amount(amount)
                .reserveType(reserveType)
                .reason(reason)
                .flaggedAt(LocalDateTime.now())
                .riskLevel(determineRiskLevelForAmount(amount))
                .requiresApproval(amount.compareTo(CRITICAL_VALUE_THRESHOLD) > 0)
                .build();

            // Store alert for monitoring
            storeHighValueReserveAlert(alert);

            // Update high-value reserve metrics
            updateHighValueReserveMetrics(amount, reserveType);

            // Trigger notifications if critical value
            if (amount.compareTo(CRITICAL_VALUE_THRESHOLD) > 0) {
                triggerCriticalValueReserveAlert(alert);
            }

            log.info("High-value reserve flagged: {} requires {} monitoring", 
                reserveRequestId, alert.getRiskLevel());

        } catch (Exception e) {
            log.error("Failed to flag high-value reserve: {}", reserveRequestId, e);
        }
    }

    /**
     * Calculates current platform exposure for currency and reserve type
     */
    private BigDecimal calculatePlatformExposure(String currency, PlatformReserveService.ReserveType reserveType) {
        try {
            // Get current exposure from Redis metrics
            String exposureKey = "platform:exposure:" + reserveType.toString().toLowerCase() + ":" + currency;
            Double exposureValue = (Double) redisTemplate.opsForValue().get(exposureKey);
            
            if (exposureValue != null) {
                return BigDecimal.valueOf(exposureValue);
            }

            // Calculate default exposure based on reserve type
            return calculateDefaultExposure(currency, reserveType);

        } catch (Exception e) {
            log.error("Failed to calculate platform exposure", e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Calculates default exposure when metrics are not available
     */
    private BigDecimal calculateDefaultExposure(String currency, PlatformReserveService.ReserveType reserveType) {
        // Default exposure estimates based on reserve type
        switch (reserveType) {
            case CHARGEBACK:
                return new BigDecimal("500000"); // $500K default chargeback exposure
            case FRAUD:
                return new BigDecimal("250000"); // $250K default fraud exposure
            case OPERATIONAL:
                return new BigDecimal("100000"); // $100K default operational exposure
            case EMERGENCY:
                return new BigDecimal("1000000"); // $1M default emergency exposure
            case REGULATORY:
                return new BigDecimal("750000"); // $750K default regulatory exposure
            default:
                return new BigDecimal("100000");
        }
    }

    /**
     * Performs reserve adequacy assessment
     */
    private ReserveAdequacyAssessment performAdequacyAssessment(
            String reserveRequestId, BigDecimal amount, String currency,
            PlatformReserveService.ReserveType reserveType, BigDecimal currentExposure,
            BigDecimal reserveAdequacyRatio, String riskLevel, boolean emergencyReserve) {

        // Determine overall risk level
        RiskLevel assessedRiskLevel = determineOverallRiskLevel(
            reserveAdequacyRatio, riskLevel, emergencyReserve, amount);

        // Calculate coverage percentage
        BigDecimal coveragePercentage = amount.divide(currentExposure, 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));

        // Determine if additional reserves are needed
        boolean additionalReservesNeeded = reserveAdequacyRatio.compareTo(configuredReserveAdequacyRatio) < 0;

        // Calculate recommended reserve amount
        BigDecimal recommendedReserveAmount = calculateRecommendedReserveAmount(
            currentExposure, reserveType, emergencyReserve);

        return ReserveAdequacyAssessment.builder()
            .reserveRequestId(reserveRequestId)
            .amount(amount)
            .currency(currency)
            .reserveType(reserveType)
            .currentExposure(currentExposure)
            .reserveAdequacyRatio(reserveAdequacyRatio)
            .coveragePercentage(coveragePercentage)
            .riskLevel(assessedRiskLevel)
            .emergencyReserve(emergencyReserve)
            .additionalReservesNeeded(additionalReservesNeeded)
            .recommendedReserveAmount(recommendedReserveAmount)
            .assessedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Determines overall risk level based on multiple factors
     */
    private RiskLevel determineOverallRiskLevel(
            BigDecimal reserveAdequacyRatio, String riskLevel, 
            boolean emergencyReserve, BigDecimal amount) {

        // Emergency reserves are always high risk
        if (emergencyReserve) {
            return RiskLevel.HIGH;
        }

        // Very large amounts are critical risk
        if (amount.compareTo(CRITICAL_VALUE_THRESHOLD) > 0) {
            return RiskLevel.CRITICAL;
        }

        // Check adequacy ratio
        if (reserveAdequacyRatio.compareTo(CRITICAL_RISK_THRESHOLD) > 0) {
            return RiskLevel.CRITICAL;
        } else if (reserveAdequacyRatio.compareTo(HIGH_RISK_THRESHOLD) > 0) {
            return RiskLevel.HIGH;
        }

        // Check external risk level
        if ("CRITICAL".equalsIgnoreCase(riskLevel) || "HIGH".equalsIgnoreCase(riskLevel)) {
            return "CRITICAL".equalsIgnoreCase(riskLevel) ? RiskLevel.CRITICAL : RiskLevel.HIGH;
        }

        return RiskLevel.MEDIUM;
    }

    /**
     * Determines risk level for amount
     */
    private RiskLevel determineRiskLevelForAmount(BigDecimal amount) {
        if (amount.compareTo(CRITICAL_VALUE_THRESHOLD) > 0) {
            return RiskLevel.CRITICAL;
        } else if (amount.compareTo(configuredHighValueThreshold) > 0) {
            return RiskLevel.HIGH;
        } else {
            return RiskLevel.MEDIUM;
        }
    }

    /**
     * Calculates recommended reserve amount
     */
    private BigDecimal calculateRecommendedReserveAmount(
            BigDecimal currentExposure, PlatformReserveService.ReserveType reserveType, 
            boolean emergencyReserve) {

        BigDecimal baseRecommendation = currentExposure.multiply(configuredReserveAdequacyRatio);

        // Adjust for reserve type
        switch (reserveType) {
            case CHARGEBACK:
                baseRecommendation = baseRecommendation.multiply(new BigDecimal("1.2")); // 20% higher
                break;
            case FRAUD:
                baseRecommendation = baseRecommendation.multiply(new BigDecimal("1.1")); // 10% higher
                break;
            case EMERGENCY:
                baseRecommendation = baseRecommendation.multiply(new BigDecimal("2.0")); // 100% higher
                break;
            case REGULATORY:
                baseRecommendation = baseRecommendation.multiply(new BigDecimal("1.5")); // 50% higher
                break;
        }

        if (emergencyReserve) {
            baseRecommendation = baseRecommendation.multiply(new BigDecimal("1.5"));
        }

        return baseRecommendation.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Stores reserve adequacy assessment
     */
    private void storeReserveAdequacyAssessment(ReserveAdequacyAssessment assessment) {
        try {
            String assessmentKey = "risk:assessment:reserve:" + assessment.getReserveRequestId();
            Map<String, String> assessmentData = Map.of(
                "reserve_id", assessment.getReserveRequestId(),
                "amount", assessment.getAmount().toString(),
                "currency", assessment.getCurrency(),
                "reserve_type", assessment.getReserveType().toString(),
                "current_exposure", assessment.getCurrentExposure().toString(),
                "adequacy_ratio", assessment.getReserveAdequacyRatio().toString(),
                "coverage_percentage", assessment.getCoveragePercentage().toString(),
                "risk_level", assessment.getRiskLevel().toString(),
                "emergency_reserve", String.valueOf(assessment.isEmergencyReserve()),
                "additional_reserves_needed", String.valueOf(assessment.isAdditionalReservesNeeded()),
                "recommended_amount", assessment.getRecommendedReserveAmount().toString(),
                "assessed_at", assessment.getAssessedAt().toString()
            );

            redisTemplate.opsForHash().putAll(assessmentKey, assessmentData);
            redisTemplate.expire(assessmentKey, Duration.ofDays(30));

        } catch (Exception e) {
            log.error("Failed to store reserve adequacy assessment", e);
        }
    }

    /**
     * Stores high-value reserve alert
     */
    private void storeHighValueReserveAlert(HighValueReserveAlert alert) {
        try {
            String alertKey = "risk:alert:high_value:" + alert.getReserveRequestId();
            Map<String, String> alertData = Map.of(
                "reserve_id", alert.getReserveRequestId(),
                "amount", alert.getAmount().toString(),
                "reserve_type", alert.getReserveType().toString(),
                "reason", alert.getReason(),
                "risk_level", alert.getRiskLevel().toString(),
                "requires_approval", String.valueOf(alert.isRequiresApproval()),
                "flagged_at", alert.getFlaggedAt().toString()
            );

            redisTemplate.opsForHash().putAll(alertKey, alertData);
            redisTemplate.expire(alertKey, Duration.ofDays(7));

        } catch (Exception e) {
            log.error("Failed to store high-value reserve alert", e);
        }
    }

    /**
     * Updates high-value reserve metrics
     */
    private void updateHighValueReserveMetrics(BigDecimal amount, PlatformReserveService.ReserveType reserveType) {
        try {
            // Update count
            String countKey = "risk:metrics:high_value:count:" + reserveType.toString().toLowerCase();
            redisTemplate.opsForValue().increment(countKey);
            redisTemplate.expire(countKey, Duration.ofDays(30));

            // Update total amount
            String amountKey = "risk:metrics:high_value:amount:" + reserveType.toString().toLowerCase();
            redisTemplate.opsForValue().increment(amountKey, amount.doubleValue());
            redisTemplate.expire(amountKey, Duration.ofDays(30));

        } catch (Exception e) {
            log.error("Failed to update high-value reserve metrics", e);
        }
    }

    /**
     * Triggers critical reserve risk alert
     */
    private void triggerCriticalReserveRiskAlert(ReserveAdequacyAssessment assessment) {
        log.error("CRITICAL: Reserve adequacy risk for {} - Coverage: {}%, Exposure: {} {}", 
            assessment.getReserveRequestId(), assessment.getCoveragePercentage(), 
            assessment.getCurrentExposure(), assessment.getCurrency());

        // Store critical alert
        String alertKey = "risk:alerts:critical:reserve:" + assessment.getReserveRequestId();
        Map<String, String> alertData = Map.of(
            "reserve_id", assessment.getReserveRequestId(),
            "risk_level", "CRITICAL",
            "coverage_percentage", assessment.getCoveragePercentage().toString(),
            "current_exposure", assessment.getCurrentExposure().toString(),
            "recommended_amount", assessment.getRecommendedReserveAmount().toString(),
            "timestamp", LocalDateTime.now().toString()
        );
        redisTemplate.opsForHash().putAll(alertKey, alertData);
        redisTemplate.expire(alertKey, Duration.ofDays(1));
    }

    /**
     * Triggers high reserve risk alert
     */
    private void triggerHighReserveRiskAlert(ReserveAdequacyAssessment assessment) {
        log.warn("HIGH RISK: Reserve adequacy concern for {} - Coverage: {}%, Exposure: {} {}", 
            assessment.getReserveRequestId(), assessment.getCoveragePercentage(), 
            assessment.getCurrentExposure(), assessment.getCurrency());

        // Store high risk alert
        String alertKey = "risk:alerts:high:reserve:" + assessment.getReserveRequestId();
        Map<String, String> alertData = Map.of(
            "reserve_id", assessment.getReserveRequestId(),
            "risk_level", "HIGH",
            "coverage_percentage", assessment.getCoveragePercentage().toString(),
            "current_exposure", assessment.getCurrentExposure().toString(),
            "recommended_amount", assessment.getRecommendedReserveAmount().toString(),
            "timestamp", LocalDateTime.now().toString()
        );
        redisTemplate.opsForHash().putAll(alertKey, alertData);
        redisTemplate.expire(alertKey, Duration.ofHours(12));
    }

    /**
     * Triggers critical value reserve alert
     */
    private void triggerCriticalValueReserveAlert(HighValueReserveAlert alert) {
        log.error("CRITICAL VALUE RESERVE: {} - Amount: {} - Type: {} - Requires approval", 
            alert.getReserveRequestId(), alert.getAmount(), alert.getReserveType());

        // Store critical value alert
        String alertKey = "risk:alerts:critical_value:" + alert.getReserveRequestId();
        Map<String, String> alertData = Map.of(
            "reserve_id", alert.getReserveRequestId(),
            "amount", alert.getAmount().toString(),
            "reserve_type", alert.getReserveType().toString(),
            "reason", alert.getReason(),
            "requires_approval", "true",
            "timestamp", LocalDateTime.now().toString()
        );
        redisTemplate.opsForHash().putAll(alertKey, alertData);
        redisTemplate.expire(alertKey, Duration.ofDays(3));
    }

    /**
     * Reserve adequacy assessment result
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class ReserveAdequacyAssessment {
        private String reserveRequestId;
        private BigDecimal amount;
        private String currency;
        private PlatformReserveService.ReserveType reserveType;
        private BigDecimal currentExposure;
        private BigDecimal reserveAdequacyRatio;
        private BigDecimal coveragePercentage;
        private RiskLevel riskLevel;
        private boolean emergencyReserve;
        private boolean additionalReservesNeeded;
        private BigDecimal recommendedReserveAmount;
        private LocalDateTime assessedAt;
    }

    /**
     * High-value reserve alert
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class HighValueReserveAlert {
        private String reserveRequestId;
        private BigDecimal amount;
        private PlatformReserveService.ReserveType reserveType;
        private String reason;
        private LocalDateTime flaggedAt;
        private RiskLevel riskLevel;
        private boolean requiresApproval;
    }

    /**
     * Risk level enumeration
     */
    public enum RiskLevel {
        LOW,        // Low risk - standard processing
        MEDIUM,     // Medium risk - enhanced monitoring
        HIGH,       // High risk - management attention required
        CRITICAL    // Critical risk - immediate escalation required
    }
}