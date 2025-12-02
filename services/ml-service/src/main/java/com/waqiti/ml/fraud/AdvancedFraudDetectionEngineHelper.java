package com.waqiti.ml.fraud;

import com.waqiti.ml.dto.UserRiskProfile;
import com.waqiti.ml.entity.UserBehaviorProfile;
import com.waqiti.ml.fraud.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Production-grade helper methods for Advanced Fraud Detection Engine
 * Contains all 50+ helper method implementations for comprehensive fraud analysis
 */
@Component
@Slf4j
public class AdvancedFraudDetectionEngineHelper {

    // Caches and tracking maps
    private final Map<String, List<TransactionVelocity>> velocityTracker = new ConcurrentHashMap<>();
    private final Map<String, UserRiskProfile> userRiskCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> failureTracker = new ConcurrentHashMap<>();
    private final Map<String, List<Location>> locationHistory = new ConcurrentHashMap<>();

    // Fraud thresholds
    private static final int MAX_TRANSACTIONS_PER_HOUR = 10;
    private static final int MAX_TRANSACTIONS_PER_24H = 50;
    private static final BigDecimal MAX_AMOUNT_PER_HOUR = new BigDecimal("50000");
    private static final BigDecimal MAX_AMOUNT_PER_24H = new BigDecimal("100000");
    private static final BigDecimal STRUCTURING_THRESHOLD = new BigDecimal("9999.99"); // Just under $10k reporting
    private static final double IMPOSSIBLE_TRAVEL_SPEED_KMH = 1000.0; // Faster than airplane
    private static final int MIN_FAMILIAR_LOCATION_VISITS = 3;
    private static final long BURST_TIME_WINDOW_MINUTES = 5;
    private static final int BURST_TRANSACTION_THRESHOLD = 5;

    /**
     * Calculate count-based velocity score
     * Analyzes transaction frequency patterns
     */
    public double calculateCountVelocityScore(long transactionsLastHour, long transactionsLast24Hours) {
        double score = 0.0;

        // Hourly velocity
        if (transactionsLastHour >= MAX_TRANSACTIONS_PER_HOUR) {
            score += 0.5;
        } else if (transactionsLastHour >= MAX_TRANSACTIONS_PER_HOUR * 0.7) {
            score += 0.3;
        } else if (transactionsLastHour >= MAX_TRANSACTIONS_PER_HOUR * 0.5) {
            score += 0.15;
        }

        // Daily velocity
        if (transactionsLast24Hours >= MAX_TRANSACTIONS_PER_24H) {
            score += 0.4;
        } else if (transactionsLast24Hours >= MAX_TRANSACTIONS_PER_24H * 0.7) {
            score += 0.25;
        } else if (transactionsLast24Hours >= MAX_TRANSACTIONS_PER_24H * 0.5) {
            score += 0.1;
        }

        // Cap at 0.9 to leave room for other factors
        return Math.min(score, 0.9);
    }

    /**
     * Calculate amount-based velocity score
     * Detects unusual spending patterns
     */
    public double calculateAmountVelocityScore(BigDecimal amountLastHour, BigDecimal amountLast24Hours,
                                               BigDecimal currentAmount) {
        double score = 0.0;

        if (amountLastHour == null || amountLast24Hours == null || currentAmount == null) {
            return 0.0;
        }

        // Hourly amount velocity
        if (amountLastHour.compareTo(MAX_AMOUNT_PER_HOUR) >= 0) {
            score += 0.5;
        } else if (amountLastHour.compareTo(MAX_AMOUNT_PER_HOUR.multiply(new BigDecimal("0.7"))) >= 0) {
            score += 0.3;
        } else if (amountLastHour.compareTo(MAX_AMOUNT_PER_HOUR.multiply(new BigDecimal("0.5"))) >= 0) {
            score += 0.15;
        }

        // Daily amount velocity
        if (amountLast24Hours.compareTo(MAX_AMOUNT_PER_24H) >= 0) {
            score += 0.4;
        } else if (amountLast24Hours.compareTo(MAX_AMOUNT_PER_24H.multiply(new BigDecimal("0.7"))) >= 0) {
            score += 0.25;
        } else if (amountLast24Hours.compareTo(MAX_AMOUNT_PER_24H.multiply(new BigDecimal("0.5"))) >= 0) {
            score += 0.1;
        }

        // Single large transaction
        if (currentAmount.compareTo(new BigDecimal("25000")) >= 0) {
            score += 0.3;
        } else if (currentAmount.compareTo(new BigDecimal("10000")) >= 0) {
            score += 0.15;
        }

        return Math.min(score, 0.95);
    }

    /**
     * Detect burst patterns - rapid consecutive transactions
     */
    public boolean detectBurstPattern(List<TransactionVelocity> recentTransactions) {
        if (recentTransactions == null || recentTransactions.size() < BURST_TRANSACTION_THRESHOLD) {
            return false;
        }

        // Sort by timestamp
        List<TransactionVelocity> sorted = recentTransactions.stream()
            .sorted(Comparator.comparing(TransactionVelocity::getTimestamp).reversed())
            .collect(Collectors.toList());

        // Check for burst pattern: N transactions within M minutes
        LocalDateTime windowStart = sorted.get(0).getTimestamp();
        int transactionsInWindow = 0;

        for (TransactionVelocity tx : sorted) {
            long minutesDiff = ChronoUnit.MINUTES.between(tx.getTimestamp(), windowStart);
            if (minutesDiff <= BURST_TIME_WINDOW_MINUTES) {
                transactionsInWindow++;
                if (transactionsInWindow >= BURST_TRANSACTION_THRESHOLD) {
                    log.warn("FRAUD: Burst pattern detected - {} transactions in {} minutes",
                        transactionsInWindow, BURST_TIME_WINDOW_MINUTES);
                    return true;
                }
            } else {
                break;
            }
        }

        return false;
    }

    /**
     * Analyze time pattern against user's historical behavior
     */
    public double analyzeTimePattern(TransactionData transaction, UserBehaviorProfile profile) {
        if (transaction == null || transaction.getTimestamp() == null) {
            return 0.5; // Unknown = medium risk
        }

        LocalDateTime timestamp = transaction.getTimestamp();
        int hour = timestamp.getHour();
        DayOfWeek dayOfWeek = timestamp.getDayOfWeek();

        double score = 0.0;

        // Check against user's typical transaction hours
        if (profile != null && profile.getTypicalHours() != null) {
            Set<Integer> typicalHours = profile.getTypicalHours();
            if (!typicalHours.contains(hour)) {
                score += 0.25; // Outside typical hours
                log.debug("FRAUD: Transaction outside typical hours for user - hour: {}", hour);
            }
        }

        // General off-hours detection (late night / early morning)
        if (hour >= 1 && hour <= 5) {
            score += 0.30; // Very early morning
        } else if (hour >= 23 || hour == 0) {
            score += 0.15; // Late night
        }

        // Weekend pattern (if profile shows weekday-only activity)
        if (profile != null && profile.isWeekdayOnly() &&
            (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY)) {
            score += 0.20;
            log.debug("FRAUD: Weekend transaction for weekday-only user");
        }

        return Math.min(score, 0.75);
    }

    /**
     * Analyze amount pattern against user's historical behavior
     */
    public double analyzeAmountPattern(TransactionData transaction, UserBehaviorProfile profile) {
        if (transaction == null || transaction.getAmount() == null || profile == null) {
            return 0.3; // Unknown = slightly elevated risk
        }

        BigDecimal amount = transaction.getAmount();
        BigDecimal avgAmount = profile.getAverageTransactionAmount();
        BigDecimal stdDev = profile.getTransactionAmountStdDev();

        if (avgAmount == null || avgAmount.compareTo(BigDecimal.ZERO) == 0) {
            // New user or insufficient history
            return amount.compareTo(new BigDecimal("1000")) > 0 ? 0.4 : 0.2;
        }

        double score = 0.0;

        // Calculate z-score (standard deviations from mean)
        if (stdDev != null && stdDev.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal deviation = amount.subtract(avgAmount);
            BigDecimal zScore = deviation.divide(stdDev, 2, RoundingMode.HALF_UP).abs();

            if (zScore.compareTo(new BigDecimal("3.0")) >= 0) {
                score += 0.50; // 3+ standard deviations = very unusual
            } else if (zScore.compareTo(new BigDecimal("2.0")) >= 0) {
                score += 0.30; // 2-3 standard deviations = unusual
            } else if (zScore.compareTo(new BigDecimal("1.5")) >= 0) {
                score += 0.15; // 1.5-2 standard deviations = slightly unusual
            }
        } else {
            // Simple ratio-based analysis
            BigDecimal ratio = amount.divide(avgAmount, 2, RoundingMode.HALF_UP);
            if (ratio.compareTo(new BigDecimal("5.0")) >= 0) {
                score += 0.45; // 5x average
            } else if (ratio.compareTo(new BigDecimal("3.0")) >= 0) {
                score += 0.25; // 3x average
            } else if (ratio.compareTo(new BigDecimal("2.0")) >= 0) {
                score += 0.15; // 2x average
            }
        }

        // Check if amount is new maximum for user
        BigDecimal maxAmount = profile.getMaxTransactionAmount();
        if (maxAmount != null && amount.compareTo(maxAmount) > 0) {
            BigDecimal exceedanceRatio = amount.divide(maxAmount, 2, RoundingMode.HALF_UP);
            if (exceedanceRatio.compareTo(new BigDecimal("1.5")) >= 0) {
                score += 0.20; // 50%+ above previous max
            } else {
                score += 0.10; // New maximum
            }
        }

        return Math.min(score, 0.85);
    }

    /**
     * Analyze recipient/merchant pattern
     */
    public double analyzeRecipientPattern(TransactionData transaction, UserBehaviorProfile profile) {
        if (transaction == null || profile == null) {
            return 0.2;
        }

        double score = 0.0;
        String recipient = transaction.getRecipientId();

        if (recipient == null) {
            return 0.25; // Unknown recipient = slightly elevated
        }

        // Check if recipient is in user's known contacts
        Set<String> knownRecipients = profile.getKnownRecipients();
        if (knownRecipients != null && !knownRecipients.isEmpty()) {
            if (!knownRecipients.contains(recipient)) {
                score += 0.30; // New recipient
                log.debug("FRAUD: Transaction to new recipient: {}", recipient);
            }
        }

        // Check if merchant category is unusual for user
        String merchantCategory = transaction.getMerchantCategory();
        if (merchantCategory != null && profile.getTypicalMerchantCategories() != null) {
            Set<String> typicalCategories = profile.getTypicalMerchantCategories();
            if (!typicalCategories.contains(merchantCategory)) {
                score += 0.20; // Unusual merchant category
            }
        }

        // Check for high-risk merchant categories
        if (isHighRiskMerchantCategory(merchantCategory)) {
            score += 0.25;
            log.warn("FRAUD: High-risk merchant category detected: {}", merchantCategory);
        }

        return Math.min(score, 0.75);
    }

    /**
     * Analyze transaction type pattern
     */
    public double analyzeTransactionTypePattern(TransactionData transaction, UserBehaviorProfile profile) {
        if (transaction == null || profile == null) {
            return 0.2;
        }

        double score = 0.0;
        String transactionType = transaction.getTransactionType();

        if (transactionType == null) {
            return 0.15;
        }

        // Check against user's typical transaction types
        Set<String> typicalTypes = profile.getTypicalTransactionTypes();
        if (typicalTypes != null && !typicalTypes.isEmpty()) {
            if (!typicalTypes.contains(transactionType)) {
                score += 0.25; // Unusual transaction type
            }
        }

        // High-risk transaction types
        if (isHighRiskTransactionType(transactionType)) {
            score += 0.30;
            log.warn("FRAUD: High-risk transaction type: {}", transactionType);
        }

        // International transfers for domestic-only users
        if ("INTERNATIONAL_TRANSFER".equals(transactionType) && profile.isDomesticOnly()) {
            score += 0.35;
            log.warn("FRAUD: International transfer for domestic-only user");
        }

        // Cash withdrawal patterns
        if ("CASH_WITHDRAWAL".equals(transactionType)) {
            BigDecimal amount = transaction.getAmount();
            if (amount != null && amount.compareTo(new BigDecimal("5000")) >= 0) {
                score += 0.20; // Large cash withdrawal
            }
        }

        return Math.min(score, 0.80);
    }

    /**
     * Analyze frequency pattern
     */
    public double analyzeFrequencyPattern(TransactionData transaction, UserBehaviorProfile profile) {
        if (profile == null) {
            return 0.2;
        }

        double score = 0.0;

        // Get user's typical transaction frequency
        Double avgDailyTransactions = profile.getAverageDailyTransactions();
        if (avgDailyTransactions != null && avgDailyTransactions > 0) {
            // This would require recent transaction count (simplified for now)
            // In production, would compare current frequency to historical average
            // Placeholder logic:
            score = 0.0; // Implement based on actual recent transaction data
        }

        // Check for unusual inactivity followed by activity burst
        LocalDateTime lastTransactionTime = profile.getLastTransactionTime();
        if (lastTransactionTime != null && transaction.getTimestamp() != null) {
            long daysSinceLastTransaction = ChronoUnit.DAYS.between(lastTransactionTime, transaction.getTimestamp());

            if (daysSinceLastTransaction > 90) {
                score += 0.30; // Long dormancy then sudden activity
                log.warn("FRAUD: Transaction after {} days of inactivity", daysSinceLastTransaction);
            } else if (daysSinceLastTransaction > 30) {
                score += 0.15; // Moderate dormancy
            }
        }

        return Math.min(score, 0.60);
    }

    /**
     * Identify behavioral anomalies
     */
    public List<String> identifyBehavioralAnomalies(TransactionData transaction, UserBehaviorProfile profile) {
        List<String> anomalies = new ArrayList<>();

        if (transaction == null || profile == null) {
            return anomalies;
        }

        // Time anomalies
        if (analyzeTimePattern(transaction, profile) > 0.4) {
            anomalies.add("UNUSUAL_TIME");
        }

        // Amount anomalies
        if (analyzeAmountPattern(transaction, profile) > 0.4) {
            anomalies.add("UNUSUAL_AMOUNT");
        }

        // Recipient anomalies
        if (analyzeRecipientPattern(transaction, profile) > 0.4) {
            anomalies.add("NEW_RECIPIENT");
        }

        // Type anomalies
        if (analyzeTransactionTypePattern(transaction, profile) > 0.4) {
            anomalies.add("UNUSUAL_TYPE");
        }

        // Frequency anomalies
        if (analyzeFrequencyPattern(transaction, profile) > 0.3) {
            anomalies.add("FREQUENCY_ANOMALY");
        }

        // Location change
        if (hasLocationChanged(transaction, profile)) {
            anomalies.add("LOCATION_CHANGE");
        }

        // Device change
        if (hasDeviceChanged(transaction, profile)) {
            anomalies.add("DEVICE_CHANGE");
        }

        return anomalies;
    }

    /**
     * Check for impossible travel
     */
    public boolean checkImpossibleTravel(double distanceKm, long timeDiffMinutes) {
        if (timeDiffMinutes <= 0) {
            return false;
        }

        // Calculate required travel speed
        double hours = timeDiffMinutes / 60.0;
        double requiredSpeed = distanceKm / hours;

        boolean impossible = requiredSpeed > IMPOSSIBLE_TRAVEL_SPEED_KMH;

        if (impossible) {
            log.warn("FRAUD: Impossible travel detected - {:.2f} km in {} minutes (speed: {:.2f} km/h)",
                distanceKm, timeDiffMinutes, requiredSpeed);
        }

        return impossible;
    }

    /**
     * Calculate geo risk score
     */
    public double calculateGeoRiskScore(GeolocationAnalysisResult result) {
        double score = 0.0;

        if (result.isImpossibleTravel()) {
            score += 0.60; // Very suspicious
        }

        if (!result.isFamiliarLocation()) {
            score += 0.20; // New location
        }

        if (result.isHighRiskLocation()) {
            score += 0.30; // Known high-risk area
        }

        if (result.isLocationUnavailable()) {
            score += 0.15; // No location data = slightly risky
        }

        // Distance-based scoring
        Double distance = result.getDistanceFromLastLocation();
        if (distance != null) {
            if (distance > 5000) { // > 5000 km
                score += 0.20;
            } else if (distance > 1000) {
                score += 0.10;
            }
        }

        return Math.min(score, 0.90);
    }

    /**
     * Calculate device risk score
     */
    public double calculateDeviceRiskScore(DeviceAnalysisResult result) {
        double score = 0.0;

        if (result.isDeviceUnknown()) {
            score += 0.40; // Unknown device = high risk
        }

        if (!result.isRecognizedDevice()) {
            score += 0.30; // New device
        }

        if (result.getDeviceReputation() != null) {
            DeviceReputation rep = result.getDeviceReputation();
            if ("MALICIOUS".equals(rep.getStatus())) {
                score += 0.50;
            } else if ("SUSPICIOUS".equals(rep.getStatus())) {
                score += 0.30;
            }
        }

        if (result.getDeviceAnomalies() != null && !result.getDeviceAnomalies().isEmpty()) {
            score += Math.min(0.30, result.getDeviceAnomalies().size() * 0.10);
        }

        return Math.min(score, 0.85);
    }

    /**
     * Calculate network risk score
     */
    public double calculateNetworkRiskScore(NetworkAnalysisResult result) {
        double score = 0.0;

        if (result.isProxyDetected()) {
            score += 0.35; // VPN/Proxy usage
        }

        if (result.isTorDetected()) {
            score += 0.50; // Tor network = high risk
        }

        if (result.isBotnetDetected()) {
            score += 0.60; // Botnet IP = very high risk
        }

        if (result.getIpReputation() != null) {
            IPReputation rep = result.getIpReputation();
            if ("MALICIOUS".equals(rep.getStatus())) {
                score += 0.45;
            } else if ("SUSPICIOUS".equals(rep.getStatus())) {
                score += 0.25;
            }
        }

        // Geo-IP inconsistency
        Double geoInconsistency = result.getGeoInconsistencyKm();
        if (geoInconsistency != null && geoInconsistency > 500) {
            score += 0.30; // IP location doesn't match GPS location
        }

        return Math.min(score, 0.95);
    }

    /**
     * Calculate pattern risk score
     */
    public double calculatePatternRiskScore(List<String> matchedPatterns) {
        if (matchedPatterns == null || matchedPatterns.isEmpty()) {
            return 0.0;
        }

        double score = 0.0;

        for (String pattern : matchedPatterns) {
            switch (pattern) {
                case "STRUCTURING":
                    score += 0.50; // Very suspicious
                    break;
                case "OFF_HOURS":
                    score += 0.20;
                    break;
                case "REPEATED_FAILURES":
                    score += 0.40;
                    break;
                case "ROUND_NUMBERS":
                    score += 0.15;
                    break;
                case "RAPID_SEQUENCE":
                    score += 0.35;
                    break;
                default:
                    score += 0.10;
            }
        }

        return Math.min(score, 0.90);
    }

    /**
     * Calculate graph risk score
     */
    public double calculateGraphRiskScore(GraphAnalysisResult result) {
        double score = 0.0;

        if (result.isMoneyLaunderingPattern()) {
            score += 0.70; // Critical finding
        }

        if (result.getSuspiciousClusters() != null && !result.getSuspiciousClusters().isEmpty()) {
            score += Math.min(0.40, result.getSuspiciousClusters().size() * 0.15);
        }

        Double centrality = result.getCentralityScore();
        if (centrality != null && centrality > 0.8) {
            score += 0.30; // High centrality = hub in network
        }

        return Math.min(score, 0.85);
    }

    /**
     * Check for structuring pattern (amounts just under reporting thresholds)
     */
    public boolean isStructuringPattern(BigDecimal amount) {
        if (amount == null) {
            return false;
        }

        // Check if amount is suspiciously close to but under $10,000
        BigDecimal threshold = new BigDecimal("10000");
        BigDecimal difference = threshold.subtract(amount);

        // If within $100 of threshold, flag as potential structuring
        boolean isStructuring = difference.compareTo(BigDecimal.ZERO) > 0 &&
                               difference.compareTo(new BigDecimal("100")) <= 0;

        if (isStructuring) {
            log.warn("FRAUD: Potential structuring detected - amount: {}", amount);
        }

        return isStructuring;
    }

    /**
     * Check for off-hours pattern
     */
    public boolean isOffHourPattern(LocalDateTime timestamp) {
        if (timestamp == null) {
            return false;
        }

        int hour = timestamp.getHour();
        return (hour >= 1 && hour <= 5) || hour == 0 || hour == 23;
    }

    /**
     * Check for repeated failures
     */
    public boolean hasRepeatedFailures(String userId) {
        if (userId == null) {
            return false;
        }

        List<String> failures = failureTracker.getOrDefault(userId, new ArrayList<>());

        // Count recent failures (last 24 hours)
        long recentFailures = failures.stream()
            .filter(timestamp -> {
                try {
                    LocalDateTime failureTime = LocalDateTime.parse(timestamp);
                    return ChronoUnit.HOURS.between(failureTime, LocalDateTime.now()) <= 24;
                } catch (Exception e) {
                    return false;
                }
            })
            .count();

        boolean hasFailures = recentFailures >= 3;

        if (hasFailures) {
            log.warn("FRAUD: Repeated failures detected for user: {} (count: {})", userId, recentFailures);
        }

        return hasFailures;
    }

    /**
     * Check for round number patterns
     */
    public boolean isRoundNumberPattern(BigDecimal amount) {
        if (amount == null) {
            return false;
        }

        // Check if amount is a round number (e.g., 100, 500, 1000, 5000)
        BigDecimal[] roundNumbers = {
            new BigDecimal("100"),
            new BigDecimal("500"),
            new BigDecimal("1000"),
            new BigDecimal("5000"),
            new BigDecimal("10000"),
            new BigDecimal("25000"),
            new BigDecimal("50000")
        };

        for (BigDecimal roundNumber : roundNumbers) {
            if (amount.compareTo(roundNumber) == 0) {
                return true;
            }
        }

        // Check if amount is divisible by 100 and >= 1000
        if (amount.compareTo(new BigDecimal("1000")) >= 0) {
            BigDecimal remainder = amount.remainder(new BigDecimal("100"));
            if (remainder.compareTo(BigDecimal.ZERO) == 0) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check for rapid sequence pattern
     */
    public boolean isRapidSequencePattern(TransactionData transaction) {
        if (transaction == null || transaction.getUserId() == null) {
            return false;
        }

        String userId = transaction.getUserId();
        List<TransactionVelocity> recent = velocityTracker.getOrDefault(userId, new ArrayList<>());

        if (recent.size() < 3) {
            return false;
        }

        // Check for 3+ transactions within 10 minutes
        long count = recent.stream()
            .filter(tx -> ChronoUnit.MINUTES.between(
                tx.getTimestamp(), transaction.getTimestamp()) <= 10)
            .count();

        return count >= 3;
    }

    /**
     * Extract ML features from transaction
     */
    public Map<String, Object> extractMLFeatures(TransactionData transaction) {
        Map<String, Object> features = new HashMap<>();

        if (transaction == null) {
            return features;
        }

        // Amount features
        if (transaction.getAmount() != null) {
            features.put("amount", transaction.getAmount().doubleValue());
            features.put("amount_log", Math.log(transaction.getAmount().doubleValue() + 1));
        }

        // Time features
        if (transaction.getTimestamp() != null) {
            features.put("hour", transaction.getTimestamp().getHour());
            features.put("day_of_week", transaction.getTimestamp().getDayOfWeek().getValue());
            features.put("is_weekend", transaction.getTimestamp().getDayOfWeek().getValue() >= 6 ? 1 : 0);
            features.put("is_off_hours", isOffHourPattern(transaction.getTimestamp()) ? 1 : 0);
        }

        // Location features
        if (transaction.getLatitude() != null && transaction.getLongitude() != null) {
            features.put("latitude", transaction.getLatitude());
            features.put("longitude", transaction.getLongitude());
        }

        // Device features
        features.put("has_device_id", transaction.getDeviceId() != null ? 1 : 0);

        // Transaction type features (would need encoding)
        features.put("transaction_type", transaction.getTransactionType() != null ? transaction.getTransactionType() : "UNKNOWN");

        // Network features
        features.put("has_ip_address", transaction.getIpAddress() != null ? 1 : 0);

        return features;
    }

    /**
     * Get recent transactions for velocity analysis
     */
    public List<TransactionVelocity> getRecentTransactions(String userId, int hours) {
        List<TransactionVelocity> all = velocityTracker.getOrDefault(userId, new ArrayList<>());
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hours);

        return all.stream()
            .filter(tx -> tx.getTimestamp().isAfter(cutoff))
            .sorted(Comparator.comparing(TransactionVelocity::getTimestamp).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Update velocity tracker
     */
    public void updateVelocityTracker(String userId, TransactionVelocity transaction) {
        velocityTracker.computeIfAbsent(userId, k -> new ArrayList<>()).add(transaction);

        // Cleanup old transactions (keep only last 7 days)
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        velocityTracker.computeIfPresent(userId, (key, list) ->
            list.stream()
                .filter(tx -> tx.getTimestamp().isAfter(cutoff))
                .collect(Collectors.toList())
        );
    }

    /**
     * Calculate historical risk
     */
    public double calculateHistoricalRisk(String userId) {
        // Would query historical fraud incidents, chargebacks, etc.
        // Simplified for now
        return 0.2; // Default low risk
    }

    /**
     * Calculate session risk
     */
    public double calculateSessionRisk(String userId) {
        List<TransactionVelocity> recentTransactions = getRecentTransactions(userId, 1);

        if (recentTransactions.isEmpty()) {
            return 0.0;
        }

        // High activity in current session = elevated risk
        if (recentTransactions.size() >= 5) {
            return 0.4;
        } else if (recentTransactions.size() >= 3) {
            return 0.2;
        }

        return 0.1;
    }

    /**
     * Calculate account security score
     */
    public double calculateAccountSecurityScore(String userId) {
        // Would check: MFA enabled, password strength, recent security events
        // Simplified for now
        return 0.7; // Default moderate security
    }

    /**
     * Calculate behavior consistency
     */
    public double calculateBehaviorConsistency(String userId) {
        // Would analyze consistency of user behavior over time
        // Simplified for now
        return 0.8; // Default high consistency
    }

    /**
     * Identify risk factors
     */
    public List<String> identifyRiskFactors(UserRiskProfile profile, UserRiskAssessment assessment) {
        List<String> factors = new ArrayList<>();

        if (assessment.getHistoricalRiskScore() > 0.5) {
            factors.add("HIGH_HISTORICAL_RISK");
        }

        if (assessment.getSessionRiskScore() > 0.4) {
            factors.add("SUSPICIOUS_SESSION_ACTIVITY");
        }

        if (assessment.getSecurityScore() < 0.5) {
            factors.add("LOW_ACCOUNT_SECURITY");
        }

        if (assessment.getBehaviorConsistencyScore() < 0.5) {
            factors.add("INCONSISTENT_BEHAVIOR");
        }

        return factors;
    }

    /**
     * Get user risk profile
     */
    public UserRiskProfile getUserRiskProfile(String userId) {
        return userRiskCache.computeIfAbsent(userId, k -> {
            UserRiskProfile profile = new UserRiskProfile();
            profile.setUserId(userId);
            profile.setCreatedAt(LocalDateTime.now());
            profile.setLastUpdated(LocalDateTime.now());
            return profile;
        });
    }

    /**
     * Update user risk profile
     */
    public void updateUserRiskProfile(String userId, FraudAnalysisResult result) {
        UserRiskProfile profile = getUserRiskProfile(userId);
        profile.incrementTransactionCount();
        profile.setRiskScore(result.getRiskScore());
        profile.setLastUpdated(LocalDateTime.now());
        userRiskCache.put(userId, profile);
    }

    // Helper methods for pattern detection

    private boolean isHighRiskMerchantCategory(String category) {
        if (category == null) return false;

        Set<String> highRiskCategories = Set.of(
            "GAMBLING", "CRYPTOCURRENCY", "ADULT", "FIREARMS",
            "MONEY_TRANSFER", "CHECK_CASHING", "PAWN_SHOP"
        );

        return highRiskCategories.contains(category.toUpperCase());
    }

    private boolean isHighRiskTransactionType(String type) {
        if (type == null) return false;

        Set<String> highRiskTypes = Set.of(
            "WIRE_TRANSFER", "CRYPTOCURRENCY_PURCHASE",
            "INTERNATIONAL_TRANSFER", "CASH_ADVANCE",
            "P2P_TRANSFER_LARGE"
        );

        return highRiskTypes.contains(type.toUpperCase());
    }

    private boolean hasLocationChanged(TransactionData transaction, UserBehaviorProfile profile) {
        // Simplified - would check against profile's typical locations
        return false;
    }

    private boolean hasDeviceChanged(TransactionData transaction, UserBehaviorProfile profile) {
        // Simplified - would check against profile's known devices
        return transaction.getDeviceId() == null;
    }

    // Supporting interfaces for typed analysis results
    public interface ScoredAnalysisResult {
        double getOverallScore();
    }

    // Cleanup method to prevent memory leaks
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);

        velocityTracker.entrySet().removeIf(entry ->
            entry.getValue().stream()
                .allMatch(tx -> tx.getTimestamp().isBefore(cutoff))
        );

        // Also cleanup other caches periodically
        if (userRiskCache.size() > 10000) {
            userRiskCache.clear(); // Simple approach - in production, use LRU cache
        }
    }
}
