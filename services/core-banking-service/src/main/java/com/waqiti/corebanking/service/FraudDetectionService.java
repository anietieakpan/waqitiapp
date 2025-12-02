package com.waqiti.corebanking.service;

import com.waqiti.corebanking.domain.Transaction;
import com.waqiti.corebanking.domain.Account;
import com.waqiti.corebanking.dto.*;
import com.waqiti.corebanking.repository.TransactionRepository;
import com.waqiti.corebanking.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Advanced Fraud Detection Service
 * Implements real-time fraud detection using multiple algorithms and patterns
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // Velocity tracking
    private final Map<String, VelocityTracker> velocityTrackers = new ConcurrentHashMap<>();
    
    // Pattern detection
    private final Map<String, List<TransactionPattern>> userPatterns = new ConcurrentHashMap<>();
    
    // Device fingerprinting
    private final Map<String, Set<String>> accountDevices = new ConcurrentHashMap<>();
    
    // Geo-location tracking
    private final Map<String, LocationHistory> locationHistories = new ConcurrentHashMap<>();

    @Value("${fraud.detection.enabled:true}")
    private boolean fraudDetectionEnabled;

    @Value("${fraud.velocity.max-transactions-per-hour:10}")
    private int maxTransactionsPerHour;

    @Value("${fraud.velocity.max-amount-per-day:10000}")
    private BigDecimal maxAmountPerDay;

    @Value("${fraud.velocity.max-unique-recipients-per-day:5}")
    private int maxUniqueRecipientsPerDay;

    @Value("${fraud.risk.high-threshold:0.7}")
    private double highRiskThreshold;

    @Value("${fraud.risk.medium-threshold:0.4}")
    private double mediumRiskThreshold;

    @PostConstruct
    public void initialize() {
        log.info("Fraud Detection Service initialized with settings: " +
            "maxTransactionsPerHour={}, maxAmountPerDay={}, maxUniqueRecipientsPerDay={}",
            maxTransactionsPerHour, maxAmountPerDay, maxUniqueRecipientsPerDay);
    }

    /**
     * Main fraud detection method - analyzes transaction for fraud risk
     */
    @Transactional(readOnly = true)
    public FraudCheckResult checkTransaction(FraudCheckRequest request) {
        if (!fraudDetectionEnabled) {
            return FraudCheckResult.approved("Fraud detection disabled");
        }

        String accountNumber = request.getSourceAccountNumber();
        BigDecimal amount = request.getAmount();
        
        // Calculate risk scores from multiple factors
        List<RiskFactor> riskFactors = new ArrayList<>();
        
        // 1. Velocity checks
        VelocityCheckResult velocityResult = checkVelocity(accountNumber, amount);
        if (velocityResult.isExceeded()) {
            riskFactors.add(new RiskFactor("VELOCITY", velocityResult.getScore(), velocityResult.getReason()));
        }
        
        // 2. Pattern analysis
        PatternAnalysisResult patternResult = analyzeTransactionPattern(request);
        if (patternResult.isAnomalous()) {
            riskFactors.add(new RiskFactor("PATTERN", patternResult.getScore(), patternResult.getReason()));
        }
        
        // 3. Device fingerprinting
        DeviceCheckResult deviceResult = checkDevice(accountNumber, request.getDeviceId());
        if (deviceResult.isSuspicious()) {
            riskFactors.add(new RiskFactor("DEVICE", deviceResult.getScore(), deviceResult.getReason()));
        }
        
        // 4. Geo-location analysis
        LocationCheckResult locationResult = checkLocation(accountNumber, request.getLocation());
        if (locationResult.isSuspicious()) {
            riskFactors.add(new RiskFactor("LOCATION", locationResult.getScore(), locationResult.getReason()));
        }
        
        // 5. Behavioral analysis
        BehavioralCheckResult behavioralResult = analyzeBehavior(request);
        if (behavioralResult.isAnomalous()) {
            riskFactors.add(new RiskFactor("BEHAVIORAL", behavioralResult.getScore(), behavioralResult.getReason()));
        }
        
        // 6. Network analysis (check for money mule patterns)
        NetworkAnalysisResult networkResult = analyzeNetwork(request);
        if (networkResult.isSuspicious()) {
            riskFactors.add(new RiskFactor("NETWORK", networkResult.getScore(), networkResult.getReason()));
        }
        
        // Calculate composite risk score
        double compositeScore = calculateCompositeRiskScore(riskFactors);
        
        // Determine action based on risk score
        FraudCheckResult result = determineAction(compositeScore, riskFactors);
        
        // Log and publish fraud event if detected
        if (result.getAction() != FraudAction.APPROVE) {
            logFraudEvent(request, result);
            publishFraudAlert(request, result);
        }
        
        // Update patterns for machine learning
        updatePatterns(accountNumber, request, result);
        
        return result;
    }

    /**
     * Velocity checking - detects rapid-fire transactions
     */
    private VelocityCheckResult checkVelocity(String accountNumber, BigDecimal amount) {
        VelocityTracker tracker = velocityTrackers.computeIfAbsent(accountNumber, k -> new VelocityTracker());
        
        LocalDateTime now = LocalDateTime.now();
        tracker.addTransaction(now, amount);
        
        // Check hourly transaction count
        int hourlyCount = tracker.getTransactionCount(now.minusHours(1), now);
        if (hourlyCount > maxTransactionsPerHour) {
            return new VelocityCheckResult(true, 0.8, 
                String.format("Exceeded hourly limit: %d transactions in last hour", hourlyCount));
        }
        
        // Check daily amount
        BigDecimal dailyAmount = tracker.getTotalAmount(now.minusDays(1), now);
        if (dailyAmount.compareTo(maxAmountPerDay) > 0) {
            return new VelocityCheckResult(true, 0.9,
                String.format("Exceeded daily amount limit: %s", dailyAmount));
        }
        
        // Check transaction frequency pattern
        double frequencyScore = analyzeFrequencyPattern(tracker, now);
        if (frequencyScore > 0.7) {
            return new VelocityCheckResult(true, frequencyScore,
                "Unusual transaction frequency detected");
        }
        
        return new VelocityCheckResult(false, 0.0, "Velocity check passed");
    }

    /**
     * Pattern analysis - detects unusual transaction patterns
     */
    private PatternAnalysisResult analyzeTransactionPattern(FraudCheckRequest request) {
        String accountNumber = request.getSourceAccountNumber();
        
        // Get historical patterns
        List<TransactionPattern> patterns = userPatterns.computeIfAbsent(accountNumber, 
            k -> loadHistoricalPatterns(k));
        
        // Check for amount anomaly
        BigDecimal amount = request.getAmount();
        double amountScore = calculateAmountAnomalyScore(amount, patterns);
        
        // Check for time anomaly
        LocalDateTime timestamp = request.getTimestamp();
        double timeScore = calculateTimeAnomalyScore(timestamp, patterns);
        
        // Check for recipient anomaly
        String recipient = request.getTargetAccountNumber();
        double recipientScore = calculateRecipientAnomalyScore(recipient, patterns);
        
        // Combine scores
        double totalScore = (amountScore * 0.4) + (timeScore * 0.3) + (recipientScore * 0.3);
        
        if (totalScore > 0.6) {
            return new PatternAnalysisResult(true, totalScore,
                String.format("Anomalous pattern detected (amount:%.2f, time:%.2f, recipient:%.2f)",
                    amountScore, timeScore, recipientScore));
        }
        
        return new PatternAnalysisResult(false, totalScore, "Pattern analysis normal");
    }

    /**
     * Device fingerprinting - detects unauthorized devices
     */
    private DeviceCheckResult checkDevice(String accountNumber, String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) {
            return new DeviceCheckResult(true, 0.5, "No device ID provided");
        }
        
        Set<String> knownDevices = accountDevices.computeIfAbsent(accountNumber, k -> new HashSet<>());
        
        if (knownDevices.isEmpty()) {
            // First device for this account
            knownDevices.add(deviceId);
            return new DeviceCheckResult(false, 0.0, "First device registered");
        }
        
        if (!knownDevices.contains(deviceId)) {
            // New device detected
            int deviceCount = knownDevices.size();
            if (deviceCount >= 3) {
                return new DeviceCheckResult(true, 0.8,
                    String.format("New device detected. Account has %d devices", deviceCount + 1));
            } else {
                knownDevices.add(deviceId);
                return new DeviceCheckResult(true, 0.4,
                    "New device detected but within acceptable limit");
            }
        }
        
        return new DeviceCheckResult(false, 0.0, "Known device");
    }

    /**
     * Location checking - detects impossible travel scenarios
     */
    private LocationCheckResult checkLocation(String accountNumber, Location currentLocation) {
        if (currentLocation == null) {
            return new LocationCheckResult(false, 0.0, "No location data");
        }
        
        LocationHistory history = locationHistories.computeIfAbsent(accountNumber, 
            k -> new LocationHistory());
        
        LocationEntry lastEntry = history.getLastEntry();
        if (lastEntry != null) {
            // Calculate distance and time difference
            double distance = calculateDistance(lastEntry.getLocation(), currentLocation);
            long minutesDiff = ChronoUnit.MINUTES.between(lastEntry.getTimestamp(), LocalDateTime.now());
            
            // Check for impossible travel
            double maxPossibleDistance = calculateMaxPossibleDistance(minutesDiff);
            if (distance > maxPossibleDistance) {
                return new LocationCheckResult(true, 0.9,
                    String.format("Impossible travel detected: %.2f km in %d minutes", distance, minutesDiff));
            }
            
            // Check for unusual location
            if (!history.isLocationNormal(currentLocation)) {
                return new LocationCheckResult(true, 0.6,
                    "Transaction from unusual location");
            }
        }
        
        history.addEntry(currentLocation, LocalDateTime.now());
        return new LocationCheckResult(false, 0.0, "Location check passed");
    }

    /**
     * Behavioral analysis - detects changes in user behavior
     */
    private BehavioralCheckResult analyzeBehavior(FraudCheckRequest request) {
        String accountNumber = request.getSourceAccountNumber();
        
        // Load user profile
        UserBehaviorProfile profile = loadUserProfile(accountNumber);
        
        // Check transaction timing
        double timingScore = analyzeTransactionTiming(request.getTimestamp(), profile);
        
        // Check amount distribution
        double amountScore = analyzeAmountDistribution(request.getAmount(), profile);
        
        // Check merchant category (if applicable)
        double merchantScore = analyzeMerchantCategory(request.getMerchantCategory(), profile);
        
        // Check transaction description/notes
        double descriptionScore = analyzeDescription(request.getDescription(), profile);
        
        // Combine behavioral scores
        double totalScore = (timingScore * 0.25) + (amountScore * 0.35) + 
                           (merchantScore * 0.2) + (descriptionScore * 0.2);
        
        if (totalScore > 0.6) {
            return new BehavioralCheckResult(true, totalScore,
                "Unusual behavior detected compared to user profile");
        }
        
        return new BehavioralCheckResult(false, totalScore, "Behavior consistent with profile");
    }

    /**
     * Network analysis - detects money mule and fraud ring patterns
     */
    private NetworkAnalysisResult analyzeNetwork(FraudCheckRequest request) {
        String sourceAccount = request.getSourceAccountNumber();
        String targetAccount = request.getTargetAccountNumber();
        
        // Check for rapid fund movement (money mule pattern)
        boolean isRapidMovement = checkRapidFundMovement(targetAccount);
        if (isRapidMovement) {
            return new NetworkAnalysisResult(true, 0.8,
                "Rapid fund movement pattern detected (possible money mule)");
        }
        
        // Check for circular transactions
        boolean hasCircularPattern = detectCircularTransactions(sourceAccount, targetAccount);
        if (hasCircularPattern) {
            return new NetworkAnalysisResult(true, 0.7,
                "Circular transaction pattern detected");
        }
        
        // Check for network clustering (fraud rings)
        double clusteringScore = analyzeNetworkClustering(sourceAccount, targetAccount);
        if (clusteringScore > 0.6) {
            return new NetworkAnalysisResult(true, clusteringScore,
                "Suspicious network clustering detected");
        }
        
        return new NetworkAnalysisResult(false, 0.0, "Network analysis normal");
    }

    /**
     * Calculate composite risk score from all factors
     */
    private double calculateCompositeRiskScore(List<RiskFactor> riskFactors) {
        if (riskFactors.isEmpty()) {
            return 0.0;
        }
        
        // Weighted average with exponential emphasis on high scores
        double weightedSum = 0.0;
        double totalWeight = 0.0;
        
        for (RiskFactor factor : riskFactors) {
            double weight = getFactorWeight(factor.getType());
            double score = factor.getScore();
            
            // Apply exponential emphasis for high scores
            if (score > 0.7) {
                score = Math.pow(score, 0.8); // Emphasize high risk scores
            }
            
            weightedSum += score * weight;
            totalWeight += weight;
        }
        
        return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
    }

    /**
     * Determine action based on risk score
     */
    private FraudCheckResult determineAction(double riskScore, List<RiskFactor> riskFactors) {
        FraudCheckResult result = new FraudCheckResult();
        result.setRiskScore(riskScore);
        result.setRiskFactors(riskFactors);
        result.setTimestamp(LocalDateTime.now());
        
        if (riskScore >= highRiskThreshold) {
            result.setAction(FraudAction.BLOCK);
            result.setReason("High fraud risk detected");
            result.setRequiredActions(Arrays.asList(
                "Manual review required",
                "Contact customer for verification",
                "Freeze account if confirmed fraud"
            ));
        } else if (riskScore >= mediumRiskThreshold) {
            result.setAction(FraudAction.CHALLENGE);
            result.setReason("Medium fraud risk - additional verification required");
            result.setRequiredActions(Arrays.asList(
                "Request 2FA verification",
                "Confirm transaction via SMS/Email",
                "Monitor subsequent transactions"
            ));
        } else {
            result.setAction(FraudAction.APPROVE);
            result.setReason("Low fraud risk");
            result.setRequiredActions(Collections.emptyList());
        }
        
        return result;
    }

    /**
     * Async fraud event logging
     */
    @Async
    public void logFraudEvent(FraudCheckRequest request, FraudCheckResult result) {
        FraudEvent event = FraudEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .accountNumber(request.getSourceAccountNumber())
            .transactionAmount(request.getAmount())
            .riskScore(result.getRiskScore())
            .action(result.getAction())
            .riskFactors(result.getRiskFactors())
            .build();
        
        // Log to database
        // fraudEventRepository.save(event);
        
        // Log to audit trail
        log.warn("FRAUD_ALERT: Account={}, Amount={}, RiskScore={}, Action={}",
            request.getSourceAccountNumber(), request.getAmount(), 
            result.getRiskScore(), result.getAction());
    }

    /**
     * Publish fraud alert to monitoring systems
     */
    private void publishFraudAlert(FraudCheckRequest request, FraudCheckResult result) {
        FraudAlert alert = FraudAlert.builder()
            .alertId(UUID.randomUUID().toString())
            .severity(mapRiskToSeverity(result.getRiskScore()))
            .accountNumber(request.getSourceAccountNumber())
            .transactionDetails(request)
            .riskAssessment(result)
            .timestamp(LocalDateTime.now())
            .build();
        
        kafkaTemplate.send("fraud-alerts", alert);
    }

    // Helper methods

    private double getFactorWeight(String factorType) {
        return switch (factorType) {
            case "VELOCITY" -> 0.25;
            case "PATTERN" -> 0.20;
            case "DEVICE" -> 0.15;
            case "LOCATION" -> 0.15;
            case "BEHAVIORAL" -> 0.15;
            case "NETWORK" -> 0.10;
            default -> 0.05;
        };
    }

    private String mapRiskToSeverity(double riskScore) {
        if (riskScore >= highRiskThreshold) return "CRITICAL";
        if (riskScore >= mediumRiskThreshold) return "HIGH";
        if (riskScore >= 0.2) return "MEDIUM";
        return "LOW";
    }

    private double calculateDistance(Location from, Location to) {
        // Haversine formula for geographic distance
        double earthRadius = 6371; // km
        double dLat = Math.toRadians(to.getLatitude() - from.getLatitude());
        double dLon = Math.toRadians(to.getLongitude() - from.getLongitude());
        
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                   Math.cos(Math.toRadians(from.getLatitude())) * 
                   Math.cos(Math.toRadians(to.getLatitude())) *
                   Math.sin(dLon/2) * Math.sin(dLon/2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return earthRadius * c;
    }

    private double calculateMaxPossibleDistance(long minutes) {
        // Assume maximum travel speed of 900 km/h (airplane)
        return (900.0 / 60.0) * minutes;
    }

    // Inner classes for tracking

    private static class VelocityTracker {
        private final List<TransactionRecord> transactions = new ArrayList<>();
        
        public void addTransaction(LocalDateTime timestamp, BigDecimal amount) {
            transactions.add(new TransactionRecord(timestamp, amount));
            // Keep only last 7 days of data
            cleanOldRecords();
        }
        
        public int getTransactionCount(LocalDateTime from, LocalDateTime to) {
            return (int) transactions.stream()
                .filter(t -> t.timestamp.isAfter(from) && t.timestamp.isBefore(to))
                .count();
        }
        
        public BigDecimal getTotalAmount(LocalDateTime from, LocalDateTime to) {
            return transactions.stream()
                .filter(t -> t.timestamp.isAfter(from) && t.timestamp.isBefore(to))
                .map(t -> t.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        
        private void cleanOldRecords() {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
            transactions.removeIf(t -> t.timestamp.isBefore(cutoff));
        }
        
        private record TransactionRecord(LocalDateTime timestamp, BigDecimal amount) {}
    }

    private static class LocationHistory {
        private final List<LocationEntry> entries = Collections.synchronizedList(new ArrayList<>());
        private final Map<String, Integer> locationFrequency = new ConcurrentHashMap<>();
        
        public void addEntry(Location location, LocalDateTime timestamp) {
            entries.add(new LocationEntry(location, timestamp));
            String locationKey = location.getCity() + "," + location.getCountry();
            locationFrequency.merge(locationKey, 1, Integer::sum);
            
            // Keep only last 30 days
            cleanOldEntries();
        }
        
        public LocationEntry getLastEntry() {
            return entries.isEmpty() ? null : entries.get(entries.size() - 1);
        }
        
        public boolean isLocationNormal(Location location) {
            String locationKey = location.getCity() + "," + location.getCountry();
            return locationFrequency.getOrDefault(locationKey, 0) > 2;
        }
        
        private void cleanOldEntries() {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
            entries.removeIf(e -> e.timestamp.isBefore(cutoff));
        }
    }

    private record LocationEntry(Location location, LocalDateTime timestamp) {}

    // DTOs for fraud detection

    @lombok.Data
    @lombok.Builder
    public static class FraudCheckRequest {
        private String sourceAccountNumber;
        private String targetAccountNumber;
        private BigDecimal amount;
        private String currency;
        private LocalDateTime timestamp;
        private String deviceId;
        private Location location;
        private String ipAddress;
        private String merchantCategory;
        private String description;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    public static class FraudCheckResult {
        private FraudAction action;
        private double riskScore;
        private String reason;
        private List<RiskFactor> riskFactors;
        private List<String> requiredActions;
        private LocalDateTime timestamp;
        
        public static FraudCheckResult approved(String reason) {
            return FraudCheckResult.builder()
                .action(FraudAction.APPROVE)
                .riskScore(0.0)
                .reason(reason)
                .riskFactors(Collections.emptyList())
                .requiredActions(Collections.emptyList())
                .timestamp(LocalDateTime.now())
                .build();
        }
    }

    public enum FraudAction {
        APPROVE,
        CHALLENGE,
        BLOCK
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class RiskFactor {
        private String type;
        private double score;
        private String reason;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class Location {
        private double latitude;
        private double longitude;
        private String city;
        private String country;
        private String ipAddress;
    }

    // Result classes
    private record VelocityCheckResult(boolean exceeded, double score, String reason) {
        public boolean isExceeded() { return exceeded; }
        public double getScore() { return score; }
        public String getReason() { return reason; }
    }

    private record PatternAnalysisResult(boolean anomalous, double score, String reason) {
        public boolean isAnomalous() { return anomalous; }
        public double getScore() { return score; }
        public String getReason() { return reason; }
    }

    private record DeviceCheckResult(boolean suspicious, double score, String reason) {
        public boolean isSuspicious() { return suspicious; }
        public double getScore() { return score; }
        public String getReason() { return reason; }
    }

    private record LocationCheckResult(boolean suspicious, double score, String reason) {
        public boolean isSuspicious() { return suspicious; }
        public double getScore() { return score; }
        public String getReason() { return reason; }
    }

    private record BehavioralCheckResult(boolean anomalous, double score, String reason) {
        public boolean isAnomalous() { return anomalous; }
        public double getScore() { return score; }
        public String getReason() { return reason; }
    }

    private record NetworkAnalysisResult(boolean suspicious, double score, String reason) {
        public boolean isSuspicious() { return suspicious; }
        public double getScore() { return score; }
        public String getReason() { return reason; }
    }

    // Stub methods for demonstration - would be fully implemented in production
    private List<TransactionPattern> loadHistoricalPatterns(String accountNumber) {
        // Load historical transaction patterns for fraud analysis
        List<TransactionPattern> patterns = new ArrayList<>();
        
        // Query transaction history from repository
        List<Transaction> transactions = transactionRepository
            .findByAccountNumberAndTimestampAfterOrderByTimestampDesc(
                accountNumber,
                LocalDateTime.now().minusDays(90)
            );
        
        // Build patterns from transaction history
        Map<String, List<Transaction>> merchantGroups = transactions.stream()
            .collect(Collectors.groupingBy(t -> t.getMerchantCategory()));
        
        for (Map.Entry<String, List<Transaction>> entry : merchantGroups.entrySet()) {
            TransactionPattern pattern = new TransactionPattern();
            pattern.setMerchantCategory(entry.getKey());
            pattern.setTransactionCount(entry.getValue().size());
            pattern.setAverageAmount(entry.getValue().stream()
                .mapToDouble(t -> t.getAmount().doubleValue())
                .average()
                .orElse(0.0));
            pattern.setMaxAmount(entry.getValue().stream()
                .map(Transaction::getAmount)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO));
            patterns.add(pattern);
        }
        
        return patterns;
    }

    private double analyzeFrequencyPattern(VelocityTracker tracker, LocalDateTime now) {
        // Analyze transaction frequency patterns
        if (tracker == null || tracker.timestamps.isEmpty()) {
            return 0.0;
        }
        
        double score = 0.0;
        
        // Check for rapid successive transactions
        List<LocalDateTime> recentTimes = tracker.timestamps.stream()
            .filter(t -> t.isAfter(now.minusMinutes(5)))
            .sorted()
            .collect(Collectors.toList());
        
        if (recentTimes.size() > 3) {
            // More than 3 transactions in 5 minutes is suspicious
            score += 30.0 * (recentTimes.size() / 3.0);
        }
        
        // Check for burst patterns
        if (recentTimes.size() >= 2) {
            long minGapSeconds = Long.MAX_VALUE;
            for (int i = 1; i < recentTimes.size(); i++) {
                long gap = ChronoUnit.SECONDS.between(recentTimes.get(i-1), recentTimes.get(i));
                minGapSeconds = Math.min(minGapSeconds, gap);
            }
            
            if (minGapSeconds < 10) {
                // Transactions less than 10 seconds apart
                score += 40.0;
            } else if (minGapSeconds < 30) {
                // Transactions less than 30 seconds apart
                score += 20.0;
            }
        }
        
        // Check for unusual time patterns (e.g., many transactions at odd hours)
        long oddHourTransactions = tracker.timestamps.stream()
            .filter(t -> {
                int hour = t.getHour();
                return hour >= 2 && hour <= 5; // 2 AM to 5 AM
            })
            .count();
        
        if (oddHourTransactions > 2) {
            score += 15.0 * oddHourTransactions;
        }
        
        return Math.min(score, 100.0);
    }

    private double calculateAmountAnomalyScore(BigDecimal amount, List<TransactionPattern> patterns) {
        // Statistical analysis of amount vs historical patterns
        if (patterns.isEmpty() || amount == null) {
            // No history, can't determine anomaly
            return amount.compareTo(new BigDecimal("10000")) > 0 ? 20.0 : 0.0;
        }
        
        // Calculate statistics from patterns
        List<BigDecimal> historicalAmounts = patterns.stream()
            .map(p -> p.amount)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        if (historicalAmounts.isEmpty()) {
            return 0.0;
        }
        
        // Calculate mean and standard deviation
        BigDecimal sum = historicalAmounts.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mean = sum.divide(BigDecimal.valueOf(historicalAmounts.size()), 2, RoundingMode.HALF_UP);
        
        // Calculate standard deviation
        BigDecimal variance = historicalAmounts.stream()
            .map(a -> a.subtract(mean).pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(historicalAmounts.size()), 2, RoundingMode.HALF_UP);
        
        double stdDev = Math.sqrt(variance.doubleValue());
        
        // Calculate z-score
        double zScore = 0.0;
        if (stdDev > 0) {
            zScore = Math.abs(amount.subtract(mean).doubleValue() / stdDev);
        }
        
        // Convert z-score to risk score
        double score = 0.0;
        if (zScore > 3) {
            score = 80.0; // Very unusual (>3 standard deviations)
        } else if (zScore > 2.5) {
            score = 60.0;
        } else if (zScore > 2) {
            score = 40.0;
        } else if (zScore > 1.5) {
            score = 20.0;
        }
        
        // Additional check for round numbers (common in fraud)
        if (amount.remainder(BigDecimal.valueOf(100)).compareTo(BigDecimal.ZERO) == 0) {
            score += 10.0;
        }
        
        // Check for maximum amount exceeded
        BigDecimal maxHistorical = historicalAmounts.stream()
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
        
        if (amount.compareTo(maxHistorical.multiply(BigDecimal.valueOf(2))) > 0) {
            score += 30.0; // More than double the historical max
        }
        
        return Math.min(score, 100.0);
    }

    private double calculateTimeAnomalyScore(LocalDateTime timestamp, List<TransactionPattern> patterns) {
        // Analyze transaction timing patterns
        if (patterns.isEmpty() || timestamp == null) {
            return 0.0;
        }
        
        double score = 0.0;
        int hour = timestamp.getHour();
        DayOfWeek dayOfWeek = timestamp.getDayOfWeek();
        
        // Collect historical timing patterns
        Map<Integer, Long> hourFrequency = patterns.stream()
            .filter(p -> p.timestamp != null)
            .collect(Collectors.groupingBy(
                p -> p.timestamp.getHour(),
                Collectors.counting()
            ));
        
        Map<DayOfWeek, Long> dayFrequency = patterns.stream()
            .filter(p -> p.timestamp != null)
            .collect(Collectors.groupingBy(
                p -> p.timestamp.getDayOfWeek(),
                Collectors.counting()
            ));
        
        // Check if transaction is at an unusual hour
        if (!hourFrequency.containsKey(hour)) {
            score += 25.0; // Never transacted at this hour before
        } else {
            long totalTransactions = hourFrequency.values().stream().mapToLong(Long::longValue).sum();
            double hourPercentage = (hourFrequency.get(hour) * 100.0) / totalTransactions;
            if (hourPercentage < 5) {
                score += 15.0; // Rare hour for transactions
            }
        }
        
        // Check if transaction is on unusual day
        if (!dayFrequency.containsKey(dayOfWeek)) {
            score += 20.0; // Never transacted on this day of week
        }
        
        // High-risk hours (late night/early morning)
        if (hour >= 0 && hour <= 5) {
            score += 30.0;
        } else if (hour >= 23) {
            score += 20.0;
        }
        
        // Weekend transactions might be unusual for business accounts
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            long weekdayTransactions = dayFrequency.entrySet().stream()
                .filter(e -> e.getKey() != DayOfWeek.SATURDAY && e.getKey() != DayOfWeek.SUNDAY)
                .mapToLong(Map.Entry::getValue)
                .sum();
            
            long weekendTransactions = dayFrequency.getOrDefault(DayOfWeek.SATURDAY, 0L) + 
                                      dayFrequency.getOrDefault(DayOfWeek.SUNDAY, 0L);
            
            if (weekdayTransactions > 0 && weekendTransactions == 0) {
                score += 25.0; // First weekend transaction
            }
        }
        
        return Math.min(score, 100.0);
    }

    private double calculateRecipientAnomalyScore(String recipient, List<TransactionPattern> patterns) {
        // Check if recipient is new or unusual
        if (recipient == null || recipient.isEmpty()) {
            return 30.0; // Anonymous recipient
        }
        
        if (patterns.isEmpty()) {
            return 10.0; // No history to compare
        }
        
        double score = 0.0;
        
        // Check if recipient has been seen before
        Set<String> historicalRecipients = patterns.stream()
            .map(p -> p.recipient)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        
        if (!historicalRecipients.contains(recipient)) {
            score += 35.0; // New recipient
            
            // Check for similarity to known recipients (typo-squatting)
            for (String known : historicalRecipients) {
                double similarity = calculateStringSimilarity(recipient, known);
                if (similarity > 0.8 && similarity < 1.0) {
                    score += 40.0; // Similar but not identical - possible typo-squatting
                    break;
                }
            }
        } else {
            // Known recipient - check frequency
            long recipientCount = patterns.stream()
                .filter(p -> recipient.equals(p.recipient))
                .count();
            
            double recipientPercentage = (recipientCount * 100.0) / patterns.size();
            if (recipientPercentage < 1) {
                score += 15.0; // Rare recipient
            }
        }
        
        // Check for suspicious patterns in recipient name
        String lowerRecipient = recipient.toLowerCase();
        if (lowerRecipient.contains("test") || lowerRecipient.contains("demo") || 
            lowerRecipient.contains("temp") || lowerRecipient.contains("xxx")) {
            score += 25.0; // Suspicious recipient name
        }
        
        // Check for numeric-only or random-looking recipients
        if (recipient.matches("[0-9]+") || recipient.matches("[a-f0-9]{32,}")) {
            score += 20.0; // Looks like an ID or hash rather than a name
        }
        
        return Math.min(score, 100.0);
    }
    
    private double calculateStringSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();
        
        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) return 1.0;
        
        int distance = computeLevenshteinDistance(s1, s2);
        return 1.0 - (double) distance / maxLength;
    }
    
    private int computeLevenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                    dp[i - 1][j] + 1,
                    Math.min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                );
            }
        }
        
        return dp[s1.length()][s2.length()];
    }

    private UserBehaviorProfile loadUserProfile(String accountNumber) {
        // Load user behavior profile from database
        return new UserBehaviorProfile();
    }

    private double analyzeTransactionTiming(LocalDateTime timestamp, UserBehaviorProfile profile) {
        // Analyze if transaction time is unusual for user
        if (profile == null || profile.typicalTransactionHours == null) {
            return 0.0;
        }
        
        double score = 0.0;
        int hour = timestamp.getHour();
        
        // Check against user's typical hours
        if (!profile.typicalTransactionHours.contains(hour)) {
            score += 30.0;
            
            // Extra penalty for very unusual hours
            if ((hour >= 0 && hour <= 4) || hour >= 23) {
                score += 20.0;
            }
        }
        
        // Check day of week patterns
        DayOfWeek day = timestamp.getDayOfWeek();
        if (profile.typicalDays != null && !profile.typicalDays.contains(day)) {
            score += 25.0;
        }
        
        // Check for holidays or special dates
        if (isHolidayOrWeekend(timestamp) && !profile.transactsOnHolidays) {
            score += 15.0;
        }
        
        // Check time zone consistency
        if (profile.primaryTimeZone != null) {
            ZonedDateTime userTime = timestamp.atZone(profile.primaryTimeZone);
            int userHour = userTime.getHour();
            
            // If transaction appears to be from different timezone
            if (Math.abs(userHour - hour) > 3) {
                score += 35.0;
            }
        }
        
        return Math.min(score, 100.0);
    }
    
    private boolean isHolidayOrWeekend(LocalDateTime timestamp) {
        DayOfWeek day = timestamp.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private double analyzeAmountDistribution(BigDecimal amount, UserBehaviorProfile profile) {
        // Check if amount fits user's typical distribution
        if (profile == null || profile.averageTransactionAmount == null) {
            return 0.0;
        }
        
        double score = 0.0;
        BigDecimal avg = profile.averageTransactionAmount;
        BigDecimal stdDev = profile.transactionStdDev;
        
        if (stdDev != null && stdDev.compareTo(BigDecimal.ZERO) > 0) {
            // Calculate z-score
            double zScore = Math.abs(amount.subtract(avg).doubleValue() / stdDev.doubleValue());
            
            if (zScore > 3) {
                score += 60.0; // More than 3 standard deviations
            } else if (zScore > 2) {
                score += 40.0;
            } else if (zScore > 1.5) {
                score += 20.0;
            }
        }
        
        // Check against maximum historical amount
        if (profile.maxTransactionAmount != null && 
            amount.compareTo(profile.maxTransactionAmount) > 0) {
            // Exceeds historical maximum
            BigDecimal excess = amount.divide(profile.maxTransactionAmount, 2, RoundingMode.HALF_UP);
            if (excess.compareTo(BigDecimal.valueOf(2)) > 0) {
                score += 40.0; // More than double the max
            } else {
                score += 20.0;
            }
        }
        
        // Check for suspicious round amounts
        if (amount.remainder(BigDecimal.valueOf(1000)).compareTo(BigDecimal.ZERO) == 0) {
            score += 10.0; // Round thousand
        }
        
        // Check spending velocity
        if (profile.monthlySpendingLimit != null && 
            profile.currentMonthSpending != null) {
            BigDecimal projected = profile.currentMonthSpending.add(amount);
            if (projected.compareTo(profile.monthlySpendingLimit) > 0) {
                score += 25.0; // Would exceed monthly limit
            }
        }
        
        return Math.min(score, 100.0);
    }

    private double analyzeMerchantCategory(String category, UserBehaviorProfile profile) {
        // Check if merchant category is typical for user
        return 0.0;
    }

    private double analyzeDescription(String description, UserBehaviorProfile profile) {
        // NLP analysis of transaction description
        return 0.0;
    }

    private boolean checkRapidFundMovement(String accountNumber) {
        // Check if funds are rapidly moved after receipt
        return false;
    }

    private boolean detectCircularTransactions(String source, String target) {
        // Detect circular transaction patterns
        return false;
    }

    private double analyzeNetworkClustering(String source, String target) {
        // Graph analysis for fraud ring detection
        if (source == null || target == null) {
            return 0.0;
        }
        
        double score = 0.0;
        
        // Check if accounts are in known fraud clusters
        Set<String> sourceConnections = getAccountConnections(source);
        Set<String> targetConnections = getAccountConnections(target);
        
        // Calculate Jaccard similarity of connections
        if (!sourceConnections.isEmpty() && !targetConnections.isEmpty()) {
            Set<String> intersection = new HashSet<>(sourceConnections);
            intersection.retainAll(targetConnections);
            
            Set<String> union = new HashSet<>(sourceConnections);
            union.addAll(targetConnections);
            
            double similarity = (double) intersection.size() / union.size();
            
            if (similarity > 0.5) {
                score += 40.0; // High network overlap
            } else if (similarity > 0.3) {
                score += 20.0;
            }
        }
        
        // Check for star pattern (one account connected to many)
        if (sourceConnections.size() > 10 || targetConnections.size() > 10) {
            score += 25.0; // Potential money mule or aggregator
        }
        
        // Check for rapid network growth
        int recentConnections = countRecentConnections(source, 7);
        if (recentConnections > 5) {
            score += 30.0; // Sudden increase in connections
        }
        
        // Check for closed loops (money circulation)
        if (detectClosedLoop(source, target, 3)) {
            score += 45.0; // Money is circulating back
        }
        
        return Math.min(score, 100.0);
    }
    
    private Set<String> getAccountConnections(String account) {
        // Query Redis for account connections graph
        Set<String> connections = new HashSet<>();
        try {
            // Get direct connections (accounts that have transacted with this account)
            String directKey = "account:connections:direct:" + account;
            Set<Object> directConnections = redisTemplate.opsForSet().members(directKey);
            if (directConnections != null) {
                directConnections.forEach(conn -> connections.add(conn.toString()));
            }
            
            // Get secondary connections (2-hop relationships)
            String secondaryKey = "account:connections:secondary:" + account;
            Set<Object> secondaryConnections = redisTemplate.opsForSet().members(secondaryKey);
            if (secondaryConnections != null && connections.size() < 100) {
                secondaryConnections.stream()
                    .limit(50)
                    .forEach(conn -> connections.add(conn.toString()));
            }
            
            // Get device-based connections (accounts using same device)
            String deviceKey = "account:connections:device:" + account;
            Set<Object> deviceConnections = redisTemplate.opsForSet().members(deviceKey);
            if (deviceConnections != null) {
                deviceConnections.forEach(conn -> connections.add(conn.toString()));
            }
            
            log.debug("Found {} connections for account {}", connections.size(), account);
        } catch (Exception e) {
            log.error("Error fetching account connections for {}: {}", account, e.getMessage());
        }
        return connections;
    }
    
    private int countRecentConnections(String account, int days) {
        // Count connections made in last N days
        return 0;
    }
    
    private boolean detectClosedLoop(String source, String target, int maxDepth) {
        // Detect if money flows back to source within maxDepth hops
        // This would use graph traversal in production
        return false;
    }

    private void updatePatterns(String accountNumber, FraudCheckRequest request, FraudCheckResult result) {
        // Update ML patterns based on outcome
    }

    // Helper classes
    private static class TransactionPattern {
        private LocalDateTime timestamp;
        private BigDecimal amount;
        private String recipient;
        private String transactionType;
        private String location;
        private String merchantCategory;
        
        public TransactionPattern() {}
    }

    private static class UserBehaviorProfile {
        private Set<Integer> typicalTransactionHours;
        private Set<DayOfWeek> typicalDays;
        private boolean transactsOnHolidays;
        private ZoneId primaryTimeZone;
        private BigDecimal averageTransactionAmount;
        private BigDecimal transactionStdDev;
        private BigDecimal maxTransactionAmount;
        private BigDecimal monthlySpendingLimit;
        private BigDecimal currentMonthSpending;
        private Set<String> trustedRecipients;
        private Set<String> commonMerchantCategories;
        
        public UserBehaviorProfile() {
            this.typicalTransactionHours = new HashSet<>();
            this.typicalDays = new HashSet<>();
            this.trustedRecipients = new HashSet<>();
            this.commonMerchantCategories = new HashSet<>();
        }
    }

    @lombok.Data
    @lombok.Builder
    private static class FraudEvent {
        private String eventId;
        private LocalDateTime timestamp;
        private String accountNumber;
        private BigDecimal transactionAmount;
        private double riskScore;
        private FraudAction action;
        private List<RiskFactor> riskFactors;
    }

    @lombok.Data
    @lombok.Builder
    private static class FraudAlert {
        private String alertId;
        private String severity;
        private String accountNumber;
        private FraudCheckRequest transactionDetails;
        private FraudCheckResult riskAssessment;
        private LocalDateTime timestamp;
    }
}