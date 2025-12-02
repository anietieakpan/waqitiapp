package com.waqiti.frauddetection.device;

import com.waqiti.frauddetection.entity.DeviceFingerprint;
import com.waqiti.frauddetection.repository.DeviceFingerprintRepository;
import com.waqiti.common.math.MoneyMath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Device Risk Scoring Service - PRODUCTION READY
 *
 * P1 ENHANCEMENT - Real device fingerprinting with multi-factor risk scoring
 *
 * Calculates device risk based on:
 * - Device age and history (20% weight)
 * - Transaction success/failure rate (30% weight)
 * - Velocity anomalies (25% weight)
 * - Reputation score (15% weight)
 * - Recent fraud incidents (10% weight)
 *
 * Risk Score Range: 0.0 (trusted) to 1.0 (high risk)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceRiskScoringService {

    private final DeviceFingerprintRepository deviceFingerprintRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String VELOCITY_KEY_PREFIX = "device:velocity:";
    private static final int VELOCITY_WINDOW_SECONDS = 3600; // 1 hour

    /**
     * Calculate comprehensive device risk score
     *
     * @param deviceData Device metadata (fingerprint, user agent, IP, etc.)
     * @return Risk score from 0.0 (trusted) to 1.0 (high risk)
     */
    public BigDecimal calculateDeviceRiskScore(Map<String, Object> deviceData) {
        if (deviceData == null || deviceData.isEmpty()) {
            log.warn("DEVICE: Missing device data - returning high risk score");
            return BigDecimal.valueOf(0.8).setScale(4, RoundingMode.HALF_UP);
        }

        String deviceFingerprint = (String) deviceData.get("fingerprint");
        if (deviceFingerprint == null || deviceFingerprint.isEmpty()) {
            log.warn("DEVICE: Missing device fingerprint - returning high risk score");
            return BigDecimal.valueOf(0.8).setScale(4, RoundingMode.HALF_UP);
        }

        try {
            // Retrieve device history from database
            DeviceFingerprint device = deviceFingerprintRepository
                .findByFingerprint(deviceFingerprint)
                .orElse(null);

            if (device == null) {
                // New device - moderate risk
                log.info("DEVICE: New device detected - fingerprint: {}", deviceFingerprint);
                return registerNewDevice(deviceFingerprint, deviceData);
            }

            // Calculate multi-factor risk score
            double riskScore = 0.0;

            // Factor 1: Device age (20% weight) - older devices are more trusted
            double ageScore = calculateAgeScore(device);
            riskScore += ageScore * 0.2;
            log.debug("DEVICE: Age score: {} (age: {} days)", ageScore,
                ChronoUnit.DAYS.between(device.getFirstSeen(), LocalDateTime.now()));

            // Factor 2: Transaction history (30% weight) - success rate matters
            double historyScore = calculateHistoryScore(device);
            riskScore += historyScore * 0.3;
            log.debug("DEVICE: History score: {} (success: {}, failed: {})",
                historyScore, device.getSuccessfulTransactions(), device.getFailedTransactions());

            // Factor 3: Velocity anomalies (25% weight) - detect sudden spikes
            double velocityScore = calculateVelocityScore(device);
            riskScore += velocityScore * 0.25;
            log.debug("DEVICE: Velocity score: {}", velocityScore);

            // Factor 4: Reputation (15% weight) - accumulated trust/distrust
            double reputationScore = device.getReputationScore() != null
                ? (double) MoneyMath.toMLFeature(device.getReputationScore())
                : 0.5; // Default to neutral
            riskScore += reputationScore * 0.15;
            log.debug("DEVICE: Reputation score: {}", reputationScore);

            // Factor 5: Recent fraud flags (10% weight) - recent incidents matter most
            boolean recentFraud = device.getLastFraudIncident() != null &&
                device.getLastFraudIncident().isAfter(LocalDateTime.now().minusDays(30));
            double fraudScore = recentFraud ? 0.9 : 0.1;
            riskScore += fraudScore * 0.1;
            log.debug("DEVICE: Fraud score: {} (recent incident: {})", fraudScore, recentFraud);

            // Update device last seen timestamp
            device.setLastSeen(LocalDateTime.now());
            deviceFingerprintRepository.save(device);

            BigDecimal finalScore = BigDecimal.valueOf(Math.min(1.0, Math.max(0.0, riskScore)))
                .setScale(4, RoundingMode.HALF_UP);

            log.info("DEVICE: Final risk score: {} for fingerprint: {}", finalScore, deviceFingerprint);
            return finalScore;

        } catch (Exception e) {
            log.error("DEVICE: Error calculating risk score for fingerprint: {}", deviceFingerprint, e);
            // Return moderate risk on error (fail-safe)
            return BigDecimal.valueOf(0.6).setScale(4, RoundingMode.HALF_UP);
        }
    }

    /**
     * Register new device with moderate risk score
     */
    private BigDecimal registerNewDevice(String fingerprint, Map<String, Object> deviceData) {
        DeviceFingerprint newDevice = DeviceFingerprint.builder()
            .fingerprint(fingerprint)
            .userAgent((String) deviceData.get("userAgent"))
            .ipAddress((String) deviceData.get("ipAddress"))
            .firstSeen(LocalDateTime.now())
            .lastSeen(LocalDateTime.now())
            .successfulTransactions(0L)
            .failedTransactions(0L)
            .reputationScore(BigDecimal.valueOf(0.5)) // Neutral reputation
            .isActive(true)
            .build();

        deviceFingerprintRepository.save(newDevice);
        log.info("DEVICE: Registered new device - fingerprint: {}", fingerprint);

        // New devices get moderate risk (0.5)
        return BigDecimal.valueOf(0.5).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Calculate age-based risk score
     * Logic: Older devices are more trusted
     */
    private double calculateAgeScore(DeviceFingerprint device) {
        long deviceAgeDays = ChronoUnit.DAYS.between(device.getFirstSeen(), LocalDateTime.now());

        if (deviceAgeDays < 1) return 0.9;  // Very new - high risk
        if (deviceAgeDays < 7) return 0.7;  // New - moderate-high risk
        if (deviceAgeDays < 30) return 0.5; // Moderate risk
        if (deviceAgeDays < 90) return 0.3; // Established - low-moderate risk
        if (deviceAgeDays < 180) return 0.2; // Well established - low risk
        return 0.1; // Highly trusted (180+ days)
    }

    /**
     * Calculate history-based risk score
     * Logic: High success rate = low risk
     */
    private double calculateHistoryScore(DeviceFingerprint device) {
        long successful = device.getSuccessfulTransactions();
        long failed = device.getFailedTransactions();
        long total = successful + failed;

        if (total == 0) return 0.5; // No history - neutral risk
        if (total < 5) return 0.6;   // Limited history - slight concern

        double successRate = (double) successful / total;

        if (successRate > 0.95) return 0.1;  // Excellent (>95%)
        if (successRate > 0.85) return 0.2;  // Very good (85-95%)
        if (successRate > 0.70) return 0.3;  // Good (70-85%)
        if (successRate > 0.50) return 0.5;  // Average (50-70%)
        if (successRate > 0.30) return 0.7;  // Poor (30-50%)
        return 0.9; // Very poor (<30%)
    }

    /**
     * Calculate velocity-based risk score
     * Logic: Unusual transaction velocity indicates potential fraud
     */
    private double calculateVelocityScore(DeviceFingerprint device) {
        try {
            String cacheKey = VELOCITY_KEY_PREFIX + device.getFingerprint();
            Long recentTxCount = (Long) redisTemplate.opsForValue().get(cacheKey);

            if (recentTxCount == null) {
                // First transaction in window - low risk
                redisTemplate.opsForValue().set(cacheKey, 1L, VELOCITY_WINDOW_SECONDS, TimeUnit.SECONDS);
                return 0.1;
            }

            // Increment transaction count
            redisTemplate.opsForValue().increment(cacheKey);

            // Calculate velocity score based on transaction count in window
            if (recentTxCount < 3) return 0.1;   // Normal (1-2 tx/hour)
            if (recentTxCount < 5) return 0.2;   // Slightly elevated (3-4 tx/hour)
            if (recentTxCount < 10) return 0.5;  // Moderate concern (5-9 tx/hour)
            if (recentTxCount < 20) return 0.8;  // High concern (10-19 tx/hour)
            return 1.0; // Critical - potential card testing attack (20+ tx/hour)

        } catch (Exception e) {
            log.warn("DEVICE: Error calculating velocity score: {}", e.getMessage());
            return 0.2; // Default to low risk on error
        }
    }

    /**
     * Update device reputation after transaction
     */
    public void updateDeviceReputation(String fingerprint, boolean transactionSuccessful,
                                      boolean fraudDetected) {
        try {
            DeviceFingerprint device = deviceFingerprintRepository
                .findByFingerprint(fingerprint)
                .orElse(null);

            if (device == null) {
                log.warn("DEVICE: Cannot update reputation - device not found: {}", fingerprint);
                return;
            }

            // Update transaction counters
            if (transactionSuccessful) {
                device.setSuccessfulTransactions(device.getSuccessfulTransactions() + 1);
            } else {
                device.setFailedTransactions(device.getFailedTransactions() + 1);
            }

            // Update reputation score
            BigDecimal currentReputation = device.getReputationScore();
            BigDecimal newReputation;

            if (fraudDetected) {
                // Significant reputation penalty for fraud
                newReputation = currentReputation.add(BigDecimal.valueOf(0.2));
                device.setLastFraudIncident(LocalDateTime.now());
                log.warn("DEVICE: Fraud detected - increasing risk score for: {}", fingerprint);
            } else if (transactionSuccessful) {
                // Small reputation improvement for successful transactions
                newReputation = currentReputation.subtract(BigDecimal.valueOf(0.01));
            } else {
                // Small reputation penalty for failed transactions
                newReputation = currentReputation.add(BigDecimal.valueOf(0.02));
            }

            // Clamp reputation between 0.0 and 1.0
            newReputation = newReputation.max(BigDecimal.ZERO).min(BigDecimal.ONE);
            device.setReputationScore(newReputation);

            device.setLastSeen(LocalDateTime.now());
            deviceFingerprintRepository.save(device);

            log.info("DEVICE: Updated reputation for {}: {} -> {}",
                fingerprint, currentReputation, newReputation);

        } catch (Exception e) {
            log.error("DEVICE: Error updating reputation for: {}", fingerprint, e);
        }
    }

    /**
     * Check if device is blacklisted
     */
    public boolean isBlacklisted(String fingerprint) {
        return deviceFingerprintRepository
            .findByFingerprint(fingerprint)
            .map(device -> device.getIsBlacklisted() != null && device.getIsBlacklisted())
            .orElse(false);
    }

    /**
     * Get device trust level
     */
    public String getDeviceTrustLevel(BigDecimal riskScore) {
        // Use BigDecimal comparisons instead of double conversion
        BigDecimal threshold02 = new BigDecimal("0.2");
        BigDecimal threshold04 = new BigDecimal("0.4");
        BigDecimal threshold06 = new BigDecimal("0.6");
        BigDecimal threshold08 = new BigDecimal("0.8");

        if (riskScore.compareTo(threshold02) < 0) return "HIGHLY_TRUSTED";
        if (riskScore.compareTo(threshold04) < 0) return "TRUSTED";
        if (riskScore.compareTo(threshold06) < 0) return "NEUTRAL";
        if (riskScore.compareTo(threshold08) < 0) return "SUSPICIOUS";
        return "HIGH_RISK";
    }
}
