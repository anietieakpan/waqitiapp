package com.waqiti.ml.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Enterprise Behavior Analytics Service
 * 
 * Analyzes user and entity behavior patterns for fraud detection, anomaly identification,
 * and risk assessment using advanced behavioral analytics and machine learning.
 * 
 * Features:
 * - User behavior profiling and baseline establishment
 * - Real-time anomaly detection
 * - Peer group analysis
 * - Temporal pattern recognition
 * - Behavioral biometrics analysis
 * - Risk scoring based on behavior deviation
 * - Adaptive learning from feedback
 * 
 * @author Waqiti ML Team
 * @version 2.0.0
 * @since 2024-01-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorAnalyticsService implements com.waqiti.ml.cache.MLCacheService.BehaviorAnalyticsService {

    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Cache prefixes
    private static final String BEHAVIOR_PREFIX = "behavior:analytics:";
    private static final String PROFILE_PREFIX = "behavior:profile:";
    private static final String ANOMALY_PREFIX = "behavior:anomaly:";
    
    // Local cache for behavior profiles
    private final ConcurrentHashMap<String, BehaviorProfile> profileCache = new ConcurrentHashMap<>();
    
    // Thresholds and configurations
    private static final double ANOMALY_THRESHOLD = 3.0; // Standard deviations
    private static final int MIN_BASELINE_TRANSACTIONS = 10;
    private static final int PROFILE_HISTORY_DAYS = 90;

    /**
     * Analyze user behavior
     */
    @Override
    public com.waqiti.ml.cache.MLCacheService.BehaviorAnalysisResult analyzeBehavior(
            String userId, String timeWindow, Map<String, Object> behaviorData) {
        try {
            log.debug("Analyzing behavior for user: {} in window: {}", userId, timeWindow);
            
            // Get or create behavior profile
            BehaviorProfile profile = getBehaviorProfile(userId);
            
            // Parse time window
            TimeWindow window = parseTimeWindow(timeWindow);
            
            // Extract current behavior metrics
            BehaviorMetrics currentMetrics = extractBehaviorMetrics(behaviorData);
            
            // Get baseline behavior for comparison
            BehaviorMetrics baseline = getBaselineBehavior(userId, window);
            
            // Detect anomalies
            List<String> anomalies = detectAnomalies(currentMetrics, baseline, profile);
            
            // Calculate risk score
            double riskScore = calculateBehaviorRiskScore(currentMetrics, baseline, anomalies);
            
            // Perform peer group analysis
            PeerGroupAnalysis peerAnalysis = performPeerGroupAnalysis(userId, currentMetrics);
            
            // Update behavior profile
            updateBehaviorProfile(userId, currentMetrics);
            
            // Build result
            com.waqiti.ml.cache.MLCacheService.BehaviorAnalysisResult result = 
                com.waqiti.ml.cache.MLCacheService.BehaviorAnalysisResult.builder()
                    .userId(userId)
                    .timeWindow(timeWindow)
                    .riskScore(riskScore)
                    .anomalies(anomalies)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            // Audit behavior analysis
            auditBehaviorAnalysis(userId, result);
            
            // Trigger alerts if needed
            if (riskScore > 0.7 || !anomalies.isEmpty()) {
                triggerBehaviorAlert(userId, result);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Error analyzing behavior for user {}: {}", userId, e.getMessage());
            
            // Return default result on error
            return com.waqiti.ml.cache.MLCacheService.BehaviorAnalysisResult.builder()
                .userId(userId)
                .timeWindow(timeWindow)
                .riskScore(0.5)
                .anomalies(new ArrayList<>())
                .timestamp(LocalDateTime.now())
                .build();
        }
    }

    /**
     * Build comprehensive behavior profile for a user
     */
    public BehaviorProfile buildBehaviorProfile(String userId) {
        try {
            log.info("Building behavior profile for user: {}", userId);
            
            BehaviorProfile profile = new BehaviorProfile();
            profile.setUserId(userId);
            profile.setCreatedAt(LocalDateTime.now());
            
            // Transaction patterns
            TransactionPattern txnPattern = analyzeTransactionPatterns(userId);
            profile.setTransactionPattern(txnPattern);
            
            // Login patterns
            LoginPattern loginPattern = analyzeLoginPatterns(userId);
            profile.setLoginPattern(loginPattern);
            
            // Device usage patterns
            DeviceUsagePattern devicePattern = analyzeDeviceUsagePatterns(userId);
            profile.setDeviceUsagePattern(devicePattern);
            
            // Geographic patterns
            GeographicPattern geoPattern = analyzeGeographicPatterns(userId);
            profile.setGeographicPattern(geoPattern);
            
            // Merchant preferences
            MerchantPreference merchantPref = analyzeMerchantPreferences(userId);
            profile.setMerchantPreference(merchantPref);
            
            // Temporal patterns
            TemporalPattern temporalPattern = analyzeTemporalPatterns(userId);
            profile.setTemporalPattern(temporalPattern);
            
            // Store profile
            storeBehaviorProfile(profile);
            
            // Cache profile
            profileCache.put(userId, profile);
            
            log.info("Successfully built behavior profile for user: {}", userId);
            return profile;
            
        } catch (Exception e) {
            log.error("Error building behavior profile for user {}: {}", userId, e.getMessage());
            return new BehaviorProfile();
        }
    }

    /**
     * Detect behavioral anomalies in real-time
     */
    public List<BehaviorAnomaly> detectRealTimeAnomalies(String userId, Map<String, Object> currentActivity) {
        try {
            log.debug("Detecting real-time anomalies for user: {}", userId);
            
            List<BehaviorAnomaly> anomalies = new ArrayList<>();
            
            // Get user profile
            BehaviorProfile profile = getBehaviorProfile(userId);
            if (profile == null || !profile.hasBaseline()) {
                log.debug("No baseline profile for user: {}", userId);
                return anomalies;
            }
            
            // Check various anomaly types
            
            // 1. Transaction amount anomaly
            if (currentActivity.containsKey("amount")) {
                double amount = ((Number) currentActivity.get("amount")).doubleValue();
                BehaviorAnomaly amountAnomaly = checkAmountAnomaly(profile, amount);
                if (amountAnomaly != null) anomalies.add(amountAnomaly);
            }
            
            // 2. Time-based anomaly
            LocalDateTime activityTime = LocalDateTime.now();
            BehaviorAnomaly timeAnomaly = checkTimeAnomaly(profile, activityTime);
            if (timeAnomaly != null) anomalies.add(timeAnomaly);
            
            // 3. Location anomaly
            if (currentActivity.containsKey("location")) {
                Map<String, Object> location = (Map<String, Object>) currentActivity.get("location");
                BehaviorAnomaly locationAnomaly = checkLocationAnomaly(profile, location);
                if (locationAnomaly != null) anomalies.add(locationAnomaly);
            }
            
            // 4. Device anomaly
            if (currentActivity.containsKey("device_id")) {
                String deviceId = (String) currentActivity.get("device_id");
                BehaviorAnomaly deviceAnomaly = checkDeviceAnomaly(profile, deviceId);
                if (deviceAnomaly != null) anomalies.add(deviceAnomaly);
            }
            
            // 5. Velocity anomaly
            BehaviorAnomaly velocityAnomaly = checkVelocityAnomaly(userId, currentActivity);
            if (velocityAnomaly != null) anomalies.add(velocityAnomaly);
            
            // 6. Merchant category anomaly
            if (currentActivity.containsKey("merchant_category")) {
                String category = (String) currentActivity.get("merchant_category");
                BehaviorAnomaly merchantAnomaly = checkMerchantCategoryAnomaly(profile, category);
                if (merchantAnomaly != null) anomalies.add(merchantAnomaly);
            }
            
            // Store detected anomalies
            if (!anomalies.isEmpty()) {
                storeDetectedAnomalies(userId, anomalies);
            }
            
            return anomalies;
            
        } catch (Exception e) {
            log.error("Error detecting anomalies for user {}: {}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Perform peer group analysis
     */
    public PeerGroupAnalysis performPeerGroupAnalysis(String userId, BehaviorMetrics userMetrics) {
        try {
            log.debug("Performing peer group analysis for user: {}", userId);
            
            // Get user's peer group
            String peerGroup = getUserPeerGroup(userId);
            
            // Get peer group statistics
            PeerGroupStats peerStats = getPeerGroupStatistics(peerGroup);
            
            // Compare user metrics with peer group
            PeerGroupAnalysis analysis = new PeerGroupAnalysis();
            analysis.setUserId(userId);
            analysis.setPeerGroup(peerGroup);
            analysis.setPeerGroupSize(peerStats.getGroupSize());
            
            // Calculate deviations
            Map<String, Double> deviations = new HashMap<>();
            
            // Transaction amount deviation
            double amountDeviation = calculateDeviation(
                userMetrics.getAverageTransactionAmount(),
                peerStats.getAverageTransactionAmount(),
                peerStats.getTransactionAmountStdDev()
            );
            deviations.put("transaction_amount", amountDeviation);
            
            // Transaction frequency deviation
            double frequencyDeviation = calculateDeviation(
                userMetrics.getTransactionFrequency(),
                peerStats.getAverageTransactionFrequency(),
                peerStats.getTransactionFrequencyStdDev()
            );
            deviations.put("transaction_frequency", frequencyDeviation);
            
            // Login frequency deviation
            double loginDeviation = calculateDeviation(
                userMetrics.getLoginFrequency(),
                peerStats.getAverageLoginFrequency(),
                peerStats.getLoginFrequencyStdDev()
            );
            deviations.put("login_frequency", loginDeviation);
            
            analysis.setDeviations(deviations);
            
            // Determine if user is outlier
            boolean isOutlier = deviations.values().stream()
                .anyMatch(deviation -> Math.abs(deviation) > ANOMALY_THRESHOLD);
            analysis.setIsOutlier(isOutlier);
            
            // Calculate peer group risk score
            double peerRiskScore = calculatePeerGroupRiskScore(deviations);
            analysis.setRiskScore(peerRiskScore);
            
            return analysis;
            
        } catch (Exception e) {
            log.error("Error performing peer group analysis for user {}: {}", userId, e.getMessage());
            return new PeerGroupAnalysis();
        }
    }

    /**
     * Update behavior model with feedback
     */
    @Transactional
    public void updateWithFeedback(String userId, String transactionId, boolean isFraud) {
        try {
            log.info("Updating behavior model with feedback for user: {}, transaction: {}, fraud: {}", 
                userId, transactionId, isFraud);
            
            // Store feedback
            String sql = "INSERT INTO behavior_feedback (user_id, transaction_id, is_fraud, " +
                        "feedback_timestamp) VALUES (?, ?, ?, ?)";
            jdbcTemplate.update(sql, userId, transactionId, isFraud, LocalDateTime.now());
            
            // Update user risk profile based on feedback
            if (isFraud) {
                increaseBehaviorRiskScore(userId);
            } else {
                decreaseBehaviorRiskScore(userId);
            }
            
            // Retrain behavior model if needed
            int feedbackCount = getFeedbackCount(userId);
            if (feedbackCount % 100 == 0) {
                scheduleModelRetraining(userId);
            }
            
            // Clear cached profile to force refresh
            profileCache.remove(userId);
            
            log.info("Successfully updated behavior model with feedback");
            
        } catch (Exception e) {
            log.error("Error updating behavior model with feedback: {}", e.getMessage());
        }
    }

    /**
     * Calculate behavioral biometrics score
     */
    public double calculateBehavioralBiometrics(String userId, Map<String, Object> biometricData) {
        try {
            log.debug("Calculating behavioral biometrics for user: {}", userId);
            
            double score = 1.0; // Start with perfect score
            
            // Get user's biometric baseline
            BiometricBaseline baseline = getUserBiometricBaseline(userId);
            
            // Typing cadence analysis
            if (biometricData.containsKey("typing_cadence")) {
                double typingScore = analyzeTypingCadence(
                    baseline.getTypingPattern(),
                    (Map<String, Object>) biometricData.get("typing_cadence")
                );
                score *= typingScore;
            }
            
            // Mouse movement patterns
            if (biometricData.containsKey("mouse_patterns")) {
                double mouseScore = analyzeMousePatterns(
                    baseline.getMousePattern(),
                    (List<Map<String, Object>>) biometricData.get("mouse_patterns")
                );
                score *= mouseScore;
            }
            
            // Touch patterns (for mobile)
            if (biometricData.containsKey("touch_patterns")) {
                double touchScore = analyzeTouchPatterns(
                    baseline.getTouchPattern(),
                    (Map<String, Object>) biometricData.get("touch_patterns")
                );
                score *= touchScore;
            }
            
            // Navigation patterns
            if (biometricData.containsKey("navigation_sequence")) {
                double navScore = analyzeNavigationPatterns(
                    baseline.getNavigationPattern(),
                    (List<String>) biometricData.get("navigation_sequence")
                );
                score *= navScore;
            }
            
            // Session duration patterns
            if (biometricData.containsKey("session_duration")) {
                double sessionScore = analyzeSessionDuration(
                    baseline.getAverageSessionDuration(),
                    ((Number) biometricData.get("session_duration")).longValue()
                );
                score *= sessionScore;
            }
            
            log.debug("Behavioral biometrics score for user {}: {}", userId, score);
            return score;
            
        } catch (Exception e) {
            log.error("Error calculating behavioral biometrics for user {}: {}", userId, e.getMessage());
            return 0.5; // Return neutral score on error
        }
    }

    /**
     * Scheduled task to update behavior baselines
     */
    @Scheduled(cron = "0 0 2 * * *") // Run at 2 AM daily
    public void updateBehaviorBaselines() {
        try {
            log.info("Starting daily behavior baseline update");
            
            // Get active users
            String sql = "SELECT DISTINCT user_id FROM user_activity " +
                        "WHERE activity_timestamp > ? AND activity_timestamp <= ?";
            
            LocalDateTime endDate = LocalDateTime.now();
            LocalDateTime startDate = endDate.minusDays(1);
            
            List<String> activeUsers = jdbcTemplate.queryForList(sql, String.class, startDate, endDate);
            
            log.info("Updating behavior baselines for {} active users", activeUsers.size());
            
            // Update baselines in batches
            int batchSize = 100;
            for (int i = 0; i < activeUsers.size(); i += batchSize) {
                int end = Math.min(i + batchSize, activeUsers.size());
                List<String> batch = activeUsers.subList(i, end);
                
                CompletableFuture.runAsync(() -> updateBatchBaselines(batch));
            }
            
            log.info("Behavior baseline update scheduled for all active users");
            
        } catch (Exception e) {
            log.error("Error updating behavior baselines: {}", e.getMessage());
        }
    }

    // Helper methods

    private BehaviorProfile getBehaviorProfile(String userId) {
        // Check cache
        BehaviorProfile cached = profileCache.get(userId);
        if (cached != null && !cached.isStale()) {
            return cached;
        }
        
        // Load from database
        try {
            String sql = "SELECT profile_data FROM behavior_profiles WHERE user_id = ? " +
                        "AND created_at > ?";
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, 
                userId, LocalDateTime.now().minusDays(PROFILE_HISTORY_DAYS));
            
            if (!results.isEmpty()) {
                BehaviorProfile profile = deserializeBehaviorProfile(results.get(0));
                profileCache.put(userId, profile);
                return profile;
            }
            
            // Build new profile if not exists
            return buildBehaviorProfile(userId);
            
        } catch (Exception e) {
            log.error("Error loading behavior profile for user {}: {}", userId, e.getMessage());
            return new BehaviorProfile();
        }
    }

    private TimeWindow parseTimeWindow(String timeWindow) {
        // Parse time window string (e.g., "1h", "24h", "7d")
        TimeWindow window = new TimeWindow();
        
        if (timeWindow.endsWith("h")) {
            int hours = Integer.parseInt(timeWindow.substring(0, timeWindow.length() - 1));
            window.setStart(LocalDateTime.now().minusHours(hours));
            window.setEnd(LocalDateTime.now());
        } else if (timeWindow.endsWith("d")) {
            int days = Integer.parseInt(timeWindow.substring(0, timeWindow.length() - 1));
            window.setStart(LocalDateTime.now().minusDays(days));
            window.setEnd(LocalDateTime.now());
        } else {
            // Default to 24 hours
            window.setStart(LocalDateTime.now().minusHours(24));
            window.setEnd(LocalDateTime.now());
        }
        
        return window;
    }

    private BehaviorMetrics extractBehaviorMetrics(Map<String, Object> behaviorData) {
        BehaviorMetrics metrics = new BehaviorMetrics();
        
        // Extract relevant metrics from behavior data
        if (behaviorData.containsKey("transaction_count")) {
            metrics.setTransactionCount(((Number) behaviorData.get("transaction_count")).intValue());
        }
        if (behaviorData.containsKey("total_amount")) {
            metrics.setTotalAmount(((Number) behaviorData.get("total_amount")).doubleValue());
        }
        if (behaviorData.containsKey("login_count")) {
            metrics.setLoginCount(((Number) behaviorData.get("login_count")).intValue());
        }
        if (behaviorData.containsKey("unique_merchants")) {
            metrics.setUniqueMerchants(((Number) behaviorData.get("unique_merchants")).intValue());
        }
        if (behaviorData.containsKey("unique_locations")) {
            metrics.setUniqueLocations(((Number) behaviorData.get("unique_locations")).intValue());
        }
        
        // Calculate derived metrics
        if (metrics.getTransactionCount() > 0) {
            metrics.setAverageTransactionAmount(metrics.getTotalAmount() / metrics.getTransactionCount());
        }
        
        return metrics;
    }

    private BehaviorMetrics getBaselineBehavior(String userId, TimeWindow window) {
        try {
            // Query historical behavior for baseline
            String sql = "SELECT AVG(amount) as avg_amount, COUNT(*) as txn_count, " +
                        "COUNT(DISTINCT merchant_id) as unique_merchants, " +
                        "COUNT(DISTINCT DATE(created_at)) as active_days " +
                        "FROM transactions WHERE user_id = ? AND created_at BETWEEN ? AND ?";
            
            Map<String, Object> baseline = jdbcTemplate.queryForMap(sql, 
                userId, window.getStart().minusDays(30), window.getStart());
            
            BehaviorMetrics metrics = new BehaviorMetrics();
            metrics.setAverageTransactionAmount(((Number) baseline.get("avg_amount")).doubleValue());
            metrics.setTransactionCount(((Number) baseline.get("txn_count")).intValue());
            metrics.setUniqueMerchants(((Number) baseline.get("unique_merchants")).intValue());
            
            int activeDays = ((Number) baseline.get("active_days")).intValue();
            if (activeDays > 0) {
                metrics.setTransactionFrequency(metrics.getTransactionCount() / (double) activeDays);
            }
            
            return metrics;
            
        } catch (Exception e) {
            log.error("Error getting baseline behavior for user {}: {}", userId, e.getMessage());
            return new BehaviorMetrics();
        }
    }

    private List<String> detectAnomalies(BehaviorMetrics current, BehaviorMetrics baseline, BehaviorProfile profile) {
        List<String> anomalies = new ArrayList<>();
        
        // Amount anomaly
        if (baseline.getAverageTransactionAmount() > 0) {
            double amountRatio = current.getAverageTransactionAmount() / baseline.getAverageTransactionAmount();
            if (amountRatio > 3.0) {
                anomalies.add("EXCESSIVE_TRANSACTION_AMOUNT");
            }
        }
        
        // Frequency anomaly
        if (baseline.getTransactionFrequency() > 0) {
            double freqRatio = current.getTransactionFrequency() / baseline.getTransactionFrequency();
            if (freqRatio > 5.0) {
                anomalies.add("UNUSUAL_TRANSACTION_FREQUENCY");
            }
        }
        
        // New merchant anomaly
        if (current.getUniqueMerchants() > baseline.getUniqueMerchants() * 2) {
            anomalies.add("MULTIPLE_NEW_MERCHANTS");
        }
        
        // Location anomaly
        if (current.getUniqueLocations() > 3 && current.getUniqueLocations() > baseline.getUniqueLocations() * 2) {
            anomalies.add("MULTIPLE_LOCATION_CHANGES");
        }
        
        return anomalies;
    }

    private double calculateBehaviorRiskScore(BehaviorMetrics current, BehaviorMetrics baseline, List<String> anomalies) {
        double riskScore = 0.0;
        
        // Base risk from anomalies
        riskScore += anomalies.size() * 0.2;
        
        // Risk from transaction amount deviation
        if (baseline.getAverageTransactionAmount() > 0) {
            double amountDeviation = Math.abs(current.getAverageTransactionAmount() - baseline.getAverageTransactionAmount()) 
                                    / baseline.getAverageTransactionAmount();
            riskScore += Math.min(amountDeviation * 0.3, 0.3);
        }
        
        // Risk from frequency deviation
        if (baseline.getTransactionFrequency() > 0) {
            double freqDeviation = Math.abs(current.getTransactionFrequency() - baseline.getTransactionFrequency()) 
                                 / baseline.getTransactionFrequency();
            riskScore += Math.min(freqDeviation * 0.2, 0.2);
        }
        
        // Normalize to 0-1 range
        return Math.min(riskScore, 1.0);
    }

    private TransactionPattern analyzeTransactionPatterns(String userId) {
        TransactionPattern pattern = new TransactionPattern();
        
        try {
            // Analyze transaction amounts
            String amountSql = "SELECT AVG(amount) as avg_amount, STDDEV(amount) as std_amount, " +
                              "MAX(amount) as max_amount, MIN(amount) as min_amount " +
                              "FROM transactions WHERE user_id = ? AND created_at > ?";
            
            Map<String, Object> amountStats = jdbcTemplate.queryForMap(amountSql, 
                userId, LocalDateTime.now().minusDays(PROFILE_HISTORY_DAYS));
            
            pattern.setAverageAmount(((Number) amountStats.get("avg_amount")).doubleValue());
            pattern.setStdDevAmount(((Number) amountStats.get("std_amount")).doubleValue());
            pattern.setMaxAmount(((Number) amountStats.get("max_amount")).doubleValue());
            pattern.setMinAmount(((Number) amountStats.get("min_amount")).doubleValue());
            
            // Analyze transaction frequency
            String freqSql = "SELECT COUNT(*) / COUNT(DISTINCT DATE(created_at)) as daily_freq " +
                            "FROM transactions WHERE user_id = ? AND created_at > ?";
            
            Double dailyFreq = jdbcTemplate.queryForObject(freqSql, Double.class, 
                userId, LocalDateTime.now().minusDays(PROFILE_HISTORY_DAYS));
            pattern.setDailyFrequency(dailyFreq != null ? dailyFreq : 0.0);
            
        } catch (Exception e) {
            log.error("Error analyzing transaction patterns: {}", e.getMessage());
        }
        
        return pattern;
    }

    private LoginPattern analyzeLoginPatterns(String userId) {
        LoginPattern pattern = new LoginPattern();
        
        try {
            // Analyze login times
            String sql = "SELECT EXTRACT(HOUR FROM login_time) as hour, COUNT(*) as count " +
                        "FROM user_logins WHERE user_id = ? AND login_time > ? " +
                        "GROUP BY EXTRACT(HOUR FROM login_time)";
            
            List<Map<String, Object>> hourlyLogins = jdbcTemplate.queryForList(sql, 
                userId, LocalDateTime.now().minusDays(PROFILE_HISTORY_DAYS));
            
            Map<Integer, Integer> hourlyDistribution = new HashMap<>();
            for (Map<String, Object> row : hourlyLogins) {
                int hour = ((Number) row.get("hour")).intValue();
                int count = ((Number) row.get("count")).intValue();
                hourlyDistribution.put(hour, count);
            }
            
            pattern.setHourlyDistribution(hourlyDistribution);
            pattern.setPreferredLoginHours(findPreferredHours(hourlyDistribution));
            
        } catch (Exception e) {
            log.error("Error analyzing login patterns: {}", e.getMessage());
        }
        
        return pattern;
    }

    private DeviceUsagePattern analyzeDeviceUsagePatterns(String userId) {
        DeviceUsagePattern pattern = new DeviceUsagePattern();
        
        try {
            String sql = "SELECT device_id, COUNT(*) as usage_count " +
                        "FROM device_user_associations WHERE user_id = ? " +
                        "GROUP BY device_id ORDER BY usage_count DESC";
            
            List<Map<String, Object>> devices = jdbcTemplate.queryForList(sql, userId);
            
            List<String> primaryDevices = new ArrayList<>();
            for (Map<String, Object> device : devices) {
                primaryDevices.add((String) device.get("device_id"));
            }
            
            pattern.setPrimaryDevices(primaryDevices);
            pattern.setTotalDeviceCount(devices.size());
            
        } catch (Exception e) {
            log.error("Error analyzing device usage patterns: {}", e.getMessage());
        }
        
        return pattern;
    }

    private GeographicPattern analyzeGeographicPatterns(String userId) {
        GeographicPattern pattern = new GeographicPattern();
        
        try {
            String sql = "SELECT country, city, COUNT(*) as count " +
                        "FROM transaction_locations WHERE user_id = ? " +
                        "GROUP BY country, city ORDER BY count DESC LIMIT 10";
            
            List<Map<String, Object>> locations = jdbcTemplate.queryForList(sql, userId);
            
            List<String> frequentLocations = new ArrayList<>();
            for (Map<String, Object> loc : locations) {
                frequentLocations.add(loc.get("city") + ", " + loc.get("country"));
            }
            
            pattern.setFrequentLocations(frequentLocations);
            pattern.setPrimaryCountry(locations.isEmpty() ? null : (String) locations.get(0).get("country"));
            
        } catch (Exception e) {
            log.error("Error analyzing geographic patterns: {}", e.getMessage());
        }
        
        return pattern;
    }

    private MerchantPreference analyzeMerchantPreferences(String userId) {
        MerchantPreference preference = new MerchantPreference();
        
        try {
            String sql = "SELECT merchant_category, COUNT(*) as count " +
                        "FROM transactions WHERE user_id = ? " +
                        "GROUP BY merchant_category ORDER BY count DESC LIMIT 5";
            
            List<Map<String, Object>> categories = jdbcTemplate.queryForList(sql, userId);
            
            List<String> preferredCategories = new ArrayList<>();
            for (Map<String, Object> cat : categories) {
                preferredCategories.add((String) cat.get("merchant_category"));
            }
            
            preference.setPreferredCategories(preferredCategories);
            
        } catch (Exception e) {
            log.error("Error analyzing merchant preferences: {}", e.getMessage());
        }
        
        return preference;
    }

    private TemporalPattern analyzeTemporalPatterns(String userId) {
        TemporalPattern pattern = new TemporalPattern();
        
        try {
            // Analyze day of week patterns
            String dowSql = "SELECT EXTRACT(DOW FROM created_at) as day_of_week, COUNT(*) as count " +
                           "FROM transactions WHERE user_id = ? " +
                           "GROUP BY EXTRACT(DOW FROM created_at)";
            
            List<Map<String, Object>> dowData = jdbcTemplate.queryForList(dowSql, userId);
            
            Map<DayOfWeek, Integer> weeklyDistribution = new HashMap<>();
            for (Map<String, Object> row : dowData) {
                int dow = ((Number) row.get("day_of_week")).intValue();
                int count = ((Number) row.get("count")).intValue();
                weeklyDistribution.put(DayOfWeek.of(dow == 0 ? 7 : dow), count);
            }
            
            pattern.setWeeklyDistribution(weeklyDistribution);
            
        } catch (Exception e) {
            log.error("Error analyzing temporal patterns: {}", e.getMessage());
        }
        
        return pattern;
    }

    private void storeBehaviorProfile(BehaviorProfile profile) {
        try {
            String sql = "INSERT INTO behavior_profiles (user_id, profile_data, created_at) " +
                        "VALUES (?, ?::jsonb, ?)";
            
            jdbcTemplate.update(sql, 
                profile.getUserId(), 
                convertToJson(profile), 
                LocalDateTime.now()
            );
            
        } catch (Exception e) {
            log.error("Error storing behavior profile: {}", e.getMessage());
        }
    }

    private void updateBehaviorProfile(String userId, BehaviorMetrics currentMetrics) {
        // Update profile with new metrics (incremental learning)
        try {
            BehaviorProfile profile = getBehaviorProfile(userId);
            profile.updateWithNewMetrics(currentMetrics);
            storeBehaviorProfile(profile);
            profileCache.put(userId, profile);
        } catch (Exception e) {
            log.error("Error updating behavior profile: {}", e.getMessage());
        }
    }

    private void auditBehaviorAnalysis(String userId, com.waqiti.ml.cache.MLCacheService.BehaviorAnalysisResult result) {
        try {
            Map<String, Object> auditEntry = Map.of(
                "user_id", userId,
                "risk_score", result.getRiskScore(),
                "anomalies", result.getAnomalies(),
                "timestamp", result.getTimestamp().toString()
            );
            
            kafkaTemplate.send("behavior-analysis-audit", userId, auditEntry);
            
        } catch (Exception e) {
            log.error("Error auditing behavior analysis: {}", e.getMessage());
        }
    }

    private void triggerBehaviorAlert(String userId, com.waqiti.ml.cache.MLCacheService.BehaviorAnalysisResult result) {
        try {
            Map<String, Object> alert = Map.of(
                "alert_type", "BEHAVIOR_ANOMALY",
                "user_id", userId,
                "risk_score", result.getRiskScore(),
                "anomalies", result.getAnomalies(),
                "timestamp", LocalDateTime.now().toString()
            );
            
            kafkaTemplate.send("fraud-alerts", userId, alert);
            
        } catch (Exception e) {
            log.error("Error triggering behavior alert: {}", e.getMessage());
        }
    }

    private List<Integer> findPreferredHours(Map<Integer, Integer> hourlyDistribution) {
        return hourlyDistribution.entrySet().stream()
            .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
            .limit(3)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    private double calculateDeviation(double value, double mean, double stdDev) {
        if (stdDev == 0) return 0;
        return (value - mean) / stdDev;
    }

    private String getUserPeerGroup(String userId) {
        try {
            String sql = "SELECT peer_group FROM user_segments WHERE user_id = ?";
            return jdbcTemplate.queryForObject(sql, String.class, userId);
        } catch (Exception e) {
            return "DEFAULT";
        }
    }

    private PeerGroupStats getPeerGroupStatistics(String peerGroup) {
        // Would retrieve actual statistics from database
        PeerGroupStats stats = new PeerGroupStats();
        stats.setPeerGroup(peerGroup);
        stats.setGroupSize(1000);
        stats.setAverageTransactionAmount(150.0);
        stats.setTransactionAmountStdDev(50.0);
        stats.setAverageTransactionFrequency(5.0);
        stats.setTransactionFrequencyStdDev(2.0);
        stats.setAverageLoginFrequency(10.0);
        stats.setLoginFrequencyStdDev(3.0);
        return stats;
    }

    private double calculatePeerGroupRiskScore(Map<String, Double> deviations) {
        double totalDeviation = deviations.values().stream()
            .mapToDouble(Math::abs)
            .sum();
        
        return Math.min(totalDeviation / 10.0, 1.0);
    }

    private void storeDetectedAnomalies(String userId, List<BehaviorAnomaly> anomalies) {
        // Store anomalies in database for future reference
    }

    private BehaviorAnomaly checkAmountAnomaly(BehaviorProfile profile, double amount) {
        TransactionPattern pattern = profile.getTransactionPattern();
        if (pattern == null) return null;
        
        double zscore = (amount - pattern.getAverageAmount()) / pattern.getStdDevAmount();
        if (Math.abs(zscore) > ANOMALY_THRESHOLD) {
            return new BehaviorAnomaly("AMOUNT_ANOMALY", zscore, "Unusual transaction amount");
        }
        return null;
    }

    private BehaviorAnomaly checkTimeAnomaly(BehaviorProfile profile, LocalDateTime activityTime) {
        LoginPattern pattern = profile.getLoginPattern();
        if (pattern == null) return null;
        
        int hour = activityTime.getHour();
        List<Integer> preferredHours = pattern.getPreferredLoginHours();
        
        if (!preferredHours.contains(hour) && !isAdjacentHour(hour, preferredHours)) {
            return new BehaviorAnomaly("TIME_ANOMALY", 1.0, "Activity at unusual time");
        }
        return null;
    }

    private boolean isAdjacentHour(int hour, List<Integer> preferredHours) {
        for (int preferred : preferredHours) {
            if (Math.abs(hour - preferred) <= 1 || Math.abs(hour - preferred) == 23) {
                return true;
            }
        }
        return false;
    }

    private BehaviorAnomaly checkLocationAnomaly(BehaviorProfile profile, Map<String, Object> location) {
        GeographicPattern pattern = profile.getGeographicPattern();
        if (pattern == null) return null;
        
        String country = (String) location.get("country");
        if (!country.equals(pattern.getPrimaryCountry())) {
            return new BehaviorAnomaly("LOCATION_ANOMALY", 1.0, "Activity from unusual location");
        }
        return null;
    }

    private BehaviorAnomaly checkDeviceAnomaly(BehaviorProfile profile, String deviceId) {
        DeviceUsagePattern pattern = profile.getDeviceUsagePattern();
        if (pattern == null) return null;
        
        if (!pattern.getPrimaryDevices().contains(deviceId)) {
            return new BehaviorAnomaly("DEVICE_ANOMALY", 1.0, "Activity from unknown device");
        }
        return null;
    }

    private BehaviorAnomaly checkVelocityAnomaly(String userId, Map<String, Object> currentActivity) {
        // Check recent transaction velocity
        try {
            String sql = "SELECT COUNT(*) FROM transactions WHERE user_id = ? AND created_at > ?";
            Integer recentCount = jdbcTemplate.queryForObject(sql, Integer.class, 
                userId, LocalDateTime.now().minusHours(1));
            
            if (recentCount != null && recentCount > 10) {
                return new BehaviorAnomaly("VELOCITY_ANOMALY", recentCount / 10.0, "High transaction velocity");
            }
        } catch (Exception e) {
            log.error("Error checking velocity anomaly: {}", e.getMessage());
        }
        return null;
    }

    private BehaviorAnomaly checkMerchantCategoryAnomaly(BehaviorProfile profile, String category) {
        MerchantPreference preference = profile.getMerchantPreference();
        if (preference == null) return null;
        
        if (!preference.getPreferredCategories().contains(category)) {
            return new BehaviorAnomaly("MERCHANT_ANOMALY", 1.0, "Transaction with unusual merchant category");
        }
        return null;
    }

    private BiometricBaseline getUserBiometricBaseline(String userId) {
        // Would retrieve from database
        return new BiometricBaseline();
    }

    private double analyzeTypingCadence(Object baseline, Map<String, Object> current) {
        // Implement typing pattern analysis
        return 0.9;
    }

    private double analyzeMousePatterns(Object baseline, List<Map<String, Object>> current) {
        // Implement mouse movement analysis
        return 0.85;
    }

    private double analyzeTouchPatterns(Object baseline, Map<String, Object> current) {
        // Implement touch pattern analysis
        return 0.88;
    }

    private double analyzeNavigationPatterns(Object baseline, List<String> current) {
        // Implement navigation sequence analysis
        return 0.92;
    }

    private double analyzeSessionDuration(long baselineDuration, long currentDuration) {
        double ratio = (double) currentDuration / baselineDuration;
        if (ratio > 0.5 && ratio < 2.0) {
            return 1.0;
        }
        return Math.max(0.5, 1.0 - Math.abs(1.0 - ratio) * 0.2);
    }

    private void increaseBehaviorRiskScore(String userId) {
        // Increase user's risk score based on fraud feedback
    }

    private void decreaseBehaviorRiskScore(String userId) {
        // Decrease user's risk score based on legitimate feedback
    }

    private int getFeedbackCount(String userId) {
        try {
            String sql = "SELECT COUNT(*) FROM behavior_feedback WHERE user_id = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId);
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private void scheduleModelRetraining(String userId) {
        // Schedule ML model retraining for this user
    }

    @Async
    public void updateBatchBaselines(List<String> userIds) {
        for (String userId : userIds) {
            try {
                buildBehaviorProfile(userId);
            } catch (Exception e) {
                log.error("Error updating baseline for user {}: {}", userId, e.getMessage());
            }
        }
    }

    private BehaviorProfile deserializeBehaviorProfile(Map<String, Object> data) {
        // Deserialize profile from JSON
        return new BehaviorProfile();
    }

    private String convertToJson(Object data) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
        } catch (Exception e) {
            log.error("Error converting to JSON: {}", e.getMessage());
            return "{}";
        }
    }

    // Inner classes and data structures

    @lombok.Data
    private static class BehaviorProfile {
        private String userId;
        private LocalDateTime createdAt;
        private TransactionPattern transactionPattern;
        private LoginPattern loginPattern;
        private DeviceUsagePattern deviceUsagePattern;
        private GeographicPattern geographicPattern;
        private MerchantPreference merchantPreference;
        private TemporalPattern temporalPattern;

        public boolean hasBaseline() {
            return transactionPattern != null && createdAt != null;
        }

        public boolean isStale() {
            return createdAt == null || createdAt.isBefore(LocalDateTime.now().minusDays(7));
        }

        public void updateWithNewMetrics(BehaviorMetrics metrics) {
            // Update profile with incremental learning
        }
    }

    @lombok.Data
    private static class TransactionPattern {
        private double averageAmount;
        private double stdDevAmount;
        private double maxAmount;
        private double minAmount;
        private double dailyFrequency;
    }

    @lombok.Data
    private static class LoginPattern {
        private Map<Integer, Integer> hourlyDistribution;
        private List<Integer> preferredLoginHours;
    }

    @lombok.Data
    private static class DeviceUsagePattern {
        private List<String> primaryDevices;
        private int totalDeviceCount;
    }

    @lombok.Data
    private static class GeographicPattern {
        private List<String> frequentLocations;
        private String primaryCountry;
    }

    @lombok.Data
    private static class MerchantPreference {
        private List<String> preferredCategories;
    }

    @lombok.Data
    private static class TemporalPattern {
        private Map<DayOfWeek, Integer> weeklyDistribution;
    }

    @lombok.Data
    private static class TimeWindow {
        private LocalDateTime start;
        private LocalDateTime end;
    }

    @lombok.Data
    private static class BehaviorMetrics {
        private int transactionCount;
        private double totalAmount;
        private double averageTransactionAmount;
        private double transactionFrequency;
        private int loginCount;
        private double loginFrequency;
        private int uniqueMerchants;
        private int uniqueLocations;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class BehaviorAnomaly {
        private String type;
        private double severity;
        private String description;
    }

    @lombok.Data
    private static class PeerGroupAnalysis {
        private String userId;
        private String peerGroup;
        private int peerGroupSize;
        private Map<String, Double> deviations;
        private boolean isOutlier;
        private double riskScore;
    }

    @lombok.Data
    private static class PeerGroupStats {
        private String peerGroup;
        private int groupSize;
        private double averageTransactionAmount;
        private double transactionAmountStdDev;
        private double averageTransactionFrequency;
        private double transactionFrequencyStdDev;
        private double averageLoginFrequency;
        private double loginFrequencyStdDev;
    }

    @lombok.Data
    private static class BiometricBaseline {
        private Object typingPattern;
        private Object mousePattern;
        private Object touchPattern;
        private Object navigationPattern;
        private long averageSessionDuration;
    }
}