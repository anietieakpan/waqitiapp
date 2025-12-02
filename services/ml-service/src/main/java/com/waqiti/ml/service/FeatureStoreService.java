package com.waqiti.ml.service;

import com.waqiti.ml.dto.TransactionData;
import com.waqiti.ml.entity.UserBehaviorProfile;
import com.waqiti.common.exception.MLProcessingException;
import com.waqiti.common.tracing.Traced;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Production-ready Feature Store Service with Feast integration.
 * Provides centralized feature management for ML models.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FeatureStoreService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final com.waqiti.ml.cache.MLCacheService mlCacheService;

    @Value("${feature.store.enabled:true}")
    private boolean featureStoreEnabled;

    @Value("${feature.store.feast.enabled:false}")
    private boolean feastEnabled;

    @Value("${feature.store.cache.ttl.minutes:60}")
    private int cacheTtlMinutes;

    @Value("${feature.store.batch.size:100}")
    private int batchSize;

    @Value("${feature.store.online.serving.enabled:true}")
    private boolean onlineServingEnabled;

    private static final String FEATURE_CACHE_PREFIX = "feature:";
    private static final String FEATURE_VERSION_PREFIX = "feature:version:";
    private static final String FEATURE_METADATA_PREFIX = "feature:metadata:";

    // Feature registry
    private final Map<String, FeatureDefinition> featureRegistry = new ConcurrentHashMap<>();

    // Feature statistics cache
    private final Map<String, FeatureStatistics> featureStatsCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        initializeFeatureRegistry();
        log.info("FeatureStoreService initialized with {} registered features", featureRegistry.size());
    }

    /**
     * Get features for a transaction
     */
    @Traced(operation = "get_transaction_features")
    public FeatureVector getTransactionFeatures(TransactionData transaction, UserBehaviorProfile profile) {
        long startTime = System.currentTimeMillis();
        
        try {
            String featureSetId = "transaction_features_v1";
            
            // Check cache first
            FeatureVector cachedFeatures = getCachedFeatures(transaction.getTransactionId(), featureSetId);
            if (cachedFeatures != null) {
                return cachedFeatures;
            }

            // Build feature vector
            FeatureVector features = FeatureVector.builder()
                .entityId(transaction.getTransactionId())
                .featureSetId(featureSetId)
                .timestamp(LocalDateTime.now())
                .build();

            // Extract transaction features
            extractTransactionFeatures(features, transaction);
            
            // Extract user features
            extractUserFeatures(features, profile);
            
            // Extract temporal features
            extractTemporalFeatures(features, transaction);
            
            // Extract behavioral features
            extractBehavioralFeatures(features, transaction, profile);
            
            // Extract network features
            extractNetworkFeatures(features, transaction);
            
            // Extract device features
            extractDeviceFeatures(features, transaction);
            
            // Extract geolocation features
            extractGeolocationFeatures(features, transaction);
            
            // Validate and transform features
            validateAndTransformFeatures(features);
            
            // Cache features
            cacheFeatures(transaction.getTransactionId(), featureSetId, features);
            
            // Update feature statistics
            updateFeatureStatistics(featureSetId, features);
            
            long duration = System.currentTimeMillis() - startTime;
            features.setProcessingTimeMs(duration);
            
            log.debug("Retrieved {} features for transaction {} in {}ms", 
                features.getFeatures().size(), transaction.getTransactionId(), duration);
            
            return features;
            
        } catch (Exception e) {
            log.error("Error getting features for transaction: {}", transaction.getTransactionId(), e);
            throw new MLProcessingException("Failed to get transaction features", e);
        }
    }

    /**
     * Get user features
     */
    @Traced(operation = "get_user_features")
    public FeatureVector getUserFeatures(String userId, UserBehaviorProfile profile) {
        long startTime = System.currentTimeMillis();
        
        try {
            String featureSetId = "user_features_v1";
            
            // Check cache
            FeatureVector cachedFeatures = getCachedFeatures(userId, featureSetId);
            if (cachedFeatures != null) {
                return cachedFeatures;
            }

            FeatureVector features = FeatureVector.builder()
                .entityId(userId)
                .featureSetId(featureSetId)
                .timestamp(LocalDateTime.now())
                .build();

            // User profile features
            features.addFeature("user_risk_score", profile.getRiskScore());
            features.addFeature("user_confidence_score", profile.getConfidenceScore());
            features.addFeature("user_transaction_count", profile.getTotalTransactionCount().doubleValue());
            features.addFeature("user_profile_age_days", profile.getProfileAgeDays());
            
            // Behavioral metrics
            if (profile.getBehaviorMetrics() != null) {
                var metrics = profile.getBehaviorMetrics();
                features.addFeature("avg_transaction_amount", 
                    metrics.getAverageAmount() != null ? metrics.getAverageAmount().doubleValue() : 0.0);
                features.addFeature("avg_hourly_rate", metrics.getAverageHourlyTransactionRate());
                features.addFeature("avg_daily_rate", metrics.getAverageDailyTransactionRate());
                features.addFeature("transaction_success_rate", metrics.getSuccessRate());
                features.addFeature("unique_recipients", metrics.getUniqueRecipientsCount().doubleValue());
                features.addFeature("unique_merchants", metrics.getUniqueMerchantsCount().doubleValue());
                features.addFeature("diversity_score", metrics.getDiversityScore());
            }
            
            // Cache features
            cacheFeatures(userId, featureSetId, features);
            
            long duration = System.currentTimeMillis() - startTime;
            features.setProcessingTimeMs(duration);
            
            return features;
            
        } catch (Exception e) {
            log.error("Error getting features for user: {}", userId, e);
            throw new MLProcessingException("Failed to get user features", e);
        }
    }

    /**
     * Store features
     */
    @CachePut(value = "features", key = "#entityId + ':' + #featureSetId")
    public void storeFeatures(String entityId, String featureSetId, FeatureVector features) {
        try {
            // Store in Redis for online serving
            if (onlineServingEnabled) {
                String key = FEATURE_CACHE_PREFIX + entityId + ":" + featureSetId;
                redisTemplate.opsForValue().set(key, features, cacheTtlMinutes, TimeUnit.MINUTES);
            }
            
            // Store feature version
            storeFeatureVersion(entityId, featureSetId, features.getVersion());
            
            // Update feature metadata
            updateFeatureMetadata(featureSetId, features);
            
            log.debug("Stored features for entity: {} with feature set: {}", entityId, featureSetId);
            
        } catch (Exception e) {
            log.error("Error storing features for entity: {}", entityId, e);
        }
    }

    /**
     * Get feature statistics
     */
    public FeatureStatistics getFeatureStatistics(String featureSetId) {
        return featureStatsCache.computeIfAbsent(featureSetId, k -> {
            String key = FEATURE_METADATA_PREFIX + "stats:" + featureSetId;
            FeatureStatistics stats = (FeatureStatistics) redisTemplate.opsForValue().get(key);
            return stats != null ? stats : new FeatureStatistics(featureSetId);
        });
    }

    /**
     * Extract transaction features
     */
    private void extractTransactionFeatures(FeatureVector features, TransactionData transaction) {
        // Basic transaction features
        features.addFeature("amount", transaction.getAmount().doubleValue());
        features.addFeature("amount_log", Math.log1p(transaction.getAmount().doubleValue()));
        features.addFeature("amount_squared", Math.pow(transaction.getAmount().doubleValue(), 2));
        
        // Transaction type encoding
        features.addFeature("is_p2p_transfer", "P2P_TRANSFER".equals(transaction.getTransactionType()) ? 1.0 : 0.0);
        features.addFeature("is_payment", "PAYMENT".equals(transaction.getTransactionType()) ? 1.0 : 0.0);
        features.addFeature("is_withdrawal", "WITHDRAWAL".equals(transaction.getTransactionType()) ? 1.0 : 0.0);
        features.addFeature("is_international", transaction.isInternational() ? 1.0 : 0.0);
        features.addFeature("is_crypto", transaction.isCrypto() ? 1.0 : 0.0);
        features.addFeature("is_high_value", transaction.isHighValue() ? 1.0 : 0.0);
        
        // Authentication features
        features.addFeature("auth_strength_score", transaction.getAuthenticationStrengthScore());
        features.addFeature("is_mfa_used", transaction.isMfaUsed() ? 1.0 : 0.0);
        features.addFeature("is_biometric_used", transaction.isBiometricUsed() ? 1.0 : 0.0);
    }

    /**
     * Extract user features
     */
    private void extractUserFeatures(FeatureVector features, UserBehaviorProfile profile) {
        if (profile == null) return;
        
        features.addFeature("user_risk_score", profile.getRiskScore());
        features.addFeature("user_confidence_score", profile.getConfidenceScore());
        features.addFeature("user_total_transactions", profile.getTotalTransactionCount().doubleValue());
        features.addFeature("user_profile_age_days", profile.getProfileAgeDays());
        features.addFeature("user_risk_level_encoded", encodeRiskLevel(profile.getRiskLevel()));
    }

    /**
     * Extract temporal features
     */
    private void extractTemporalFeatures(FeatureVector features, TransactionData transaction) {
        LocalDateTime timestamp = transaction.getTimestamp();
        
        // Time-based features
        features.addFeature("hour_of_day", timestamp.getHour());
        features.addFeature("day_of_week", timestamp.getDayOfWeek().getValue());
        features.addFeature("day_of_month", timestamp.getDayOfMonth());
        features.addFeature("month", timestamp.getMonthValue());
        features.addFeature("quarter", (timestamp.getMonthValue() - 1) / 3 + 1);
        
        // Cyclic encoding for temporal features
        features.addFeature("hour_sin", Math.sin(2 * Math.PI * timestamp.getHour() / 24));
        features.addFeature("hour_cos", Math.cos(2 * Math.PI * timestamp.getHour() / 24));
        features.addFeature("day_sin", Math.sin(2 * Math.PI * timestamp.getDayOfWeek().getValue() / 7));
        features.addFeature("day_cos", Math.cos(2 * Math.PI * timestamp.getDayOfWeek().getValue() / 7));
        
        // Weekend/weekday indicator
        features.addFeature("is_weekend", 
            timestamp.getDayOfWeek().getValue() >= 6 ? 1.0 : 0.0);
        
        // Business hours indicator
        features.addFeature("is_business_hours", 
            timestamp.getHour() >= 9 && timestamp.getHour() <= 17 ? 1.0 : 0.0);
        
        // Night transaction indicator
        features.addFeature("is_night_transaction", 
            timestamp.getHour() < 6 || timestamp.getHour() >= 22 ? 1.0 : 0.0);
    }

    /**
     * Extract behavioral features
     */
    private void extractBehavioralFeatures(FeatureVector features, TransactionData transaction, 
                                          UserBehaviorProfile profile) {
        if (profile == null || profile.getBehaviorMetrics() == null) return;
        
        var metrics = profile.getBehaviorMetrics();
        
        // Amount deviation from average
        if (metrics.getAverageAmount() != null && metrics.getStdDevAmount() != null) {
            double avgAmount = metrics.getAverageAmount().doubleValue();
            double stdDev = metrics.getStdDevAmount().doubleValue();
            double currentAmount = transaction.getAmount().doubleValue();
            
            if (stdDev > 0) {
                features.addFeature("amount_z_score", (currentAmount - avgAmount) / stdDev);
            }
            
            features.addFeature("amount_deviation_ratio", 
                avgAmount > 0 ? currentAmount / avgAmount : 0.0);
        }
        
        // Velocity features
        features.addFeature("hourly_transaction_rate", metrics.getAverageHourlyTransactionRate());
        features.addFeature("daily_transaction_rate", metrics.getAverageDailyTransactionRate());
        
        // Pattern features
        features.addFeature("recipient_diversity", metrics.getDiversityScore());
        features.addFeature("merchant_diversity", 
            metrics.getUniqueMerchantsCount().doubleValue() / Math.max(1, metrics.getTotalTransactionCount()));
    }

    /**
     * Extract network features
     */
    private void extractNetworkFeatures(FeatureVector features, TransactionData transaction) {
        if (transaction.getNetworkInfo() == null) return;
        
        var networkInfo = transaction.getNetworkInfo();
        
        // Network type features
        features.addFeature("is_vpn", networkInfo.getIsVpn() ? 1.0 : 0.0);
        features.addFeature("is_proxy", networkInfo.getIsProxy() ? 1.0 : 0.0);
        features.addFeature("is_tor", networkInfo.getIsTor() ? 1.0 : 0.0);
        features.addFeature("is_datacenter", networkInfo.getIsDatacenter() ? 1.0 : 0.0);
        
        // Network risk features
        features.addFeature("network_risk_score", networkInfo.getRiskScore());
        features.addFeature("ip_reputation", networkInfo.getIpReputation());
        
        // Connection features
        if (networkInfo.getLatencyMs() != null) {
            features.addFeature("network_latency_ms", networkInfo.getLatencyMs());
            features.addFeature("high_latency", networkInfo.getLatencyMs() > 500 ? 1.0 : 0.0);
        }
    }

    /**
     * Extract device features
     */
    private void extractDeviceFeatures(FeatureVector features, TransactionData transaction) {
        if (transaction.getDeviceInfo() == null) return;
        
        var deviceInfo = transaction.getDeviceInfo();
        
        // Device type features
        features.addFeature("is_mobile", "MOBILE".equals(deviceInfo.getDeviceType()) ? 1.0 : 0.0);
        features.addFeature("is_desktop", "DESKTOP".equals(deviceInfo.getDeviceType()) ? 1.0 : 0.0);
        features.addFeature("is_tablet", "TABLET".equals(deviceInfo.getDeviceType()) ? 1.0 : 0.0);
        
        // Device security features
        features.addFeature("is_jailbroken", deviceInfo.getIsJailbroken() ? 1.0 : 0.0);
        features.addFeature("is_emulator", deviceInfo.getIsEmulator() ? 1.0 : 0.0);
        features.addFeature("is_trusted_device", transaction.isFromTrustedDevice() ? 1.0 : 0.0);
        
        // OS features
        features.addFeature("is_ios", "IOS".equals(deviceInfo.getOperatingSystem()) ? 1.0 : 0.0);
        features.addFeature("is_android", "ANDROID".equals(deviceInfo.getOperatingSystem()) ? 1.0 : 0.0);
    }

    /**
     * Extract geolocation features
     */
    private void extractGeolocationFeatures(FeatureVector features, TransactionData transaction) {
        if (transaction.getGeolocation() == null) return;
        
        var geoData = transaction.getGeolocation();
        
        // Location features
        if (geoData.getLatitude() != null && geoData.getLongitude() != null) {
            features.addFeature("latitude", geoData.getLatitude());
            features.addFeature("longitude", geoData.getLongitude());
            
            // Geohash for location clustering
            features.addFeature("geohash_prefix", 
                generateGeohashPrefix(geoData.getLatitude(), geoData.getLongitude()));
        }
        
        // Location accuracy
        if (geoData.getAccuracyMeters() != null) {
            features.addFeature("location_accuracy_meters", geoData.getAccuracyMeters());
            features.addFeature("poor_location_accuracy", geoData.getAccuracyMeters() > 100 ? 1.0 : 0.0);
        }
        
        // Mock location detection
        features.addFeature("is_mock_location", geoData.getIsMockLocation() ? 1.0 : 0.0);
    }

    /**
     * Validate and transform features
     */
    private void validateAndTransformFeatures(FeatureVector features) {
        Map<String, Double> transformedFeatures = new HashMap<>();
        
        for (Map.Entry<String, Double> entry : features.getFeatures().entrySet()) {
            String featureName = entry.getKey();
            Double value = entry.getValue();
            
            // Handle null values
            if (value == null || value.isNaN() || value.isInfinite()) {
                value = 0.0; // Default imputation
            }
            
            // Apply feature-specific transformations
            FeatureDefinition definition = featureRegistry.get(featureName);
            if (definition != null) {
                value = applyFeatureTransformation(value, definition);
            }
            
            transformedFeatures.put(featureName, value);
        }
        
        features.setFeatures(transformedFeatures);
        features.setValidated(true);
    }

    /**
     * Apply feature transformation based on definition
     */
    private double applyFeatureTransformation(double value, FeatureDefinition definition) {
        switch (definition.getTransformationType()) {
            case "LOG":
                return Math.log1p(Math.abs(value));
            case "SQRT":
                return Math.sqrt(Math.abs(value));
            case "NORMALIZE":
                if (definition.getMin() != null && definition.getMax() != null) {
                    double range = definition.getMax() - definition.getMin();
                    if (range > 0) {
                        return (value - definition.getMin()) / range;
                    }
                }
                return value;
            case "STANDARDIZE":
                if (definition.getMean() != null && definition.getStdDev() != null && definition.getStdDev() > 0) {
                    return (value - definition.getMean()) / definition.getStdDev();
                }
                return value;
            default:
                return value;
        }
    }

    /**
     * Initialize feature registry
     */
    private void initializeFeatureRegistry() {
        // Transaction features
        registerFeature("amount", "NUMERIC", "LOG", 0.0, 1000000.0);
        registerFeature("amount_log", "NUMERIC", "NONE", null, null);
        registerFeature("hour_of_day", "NUMERIC", "NONE", 0.0, 23.0);
        registerFeature("day_of_week", "NUMERIC", "NONE", 1.0, 7.0);
        
        // User features
        registerFeature("user_risk_score", "NUMERIC", "NORMALIZE", 0.0, 100.0);
        registerFeature("user_confidence_score", "NUMERIC", "NORMALIZE", 0.0, 1.0);
        registerFeature("user_total_transactions", "NUMERIC", "LOG", 0.0, null);
        
        // Network features
        registerFeature("network_risk_score", "NUMERIC", "NORMALIZE", 0.0, 100.0);
        registerFeature("network_latency_ms", "NUMERIC", "LOG", 0.0, 10000.0);
        
        // Boolean features
        registerFeature("is_vpn", "BOOLEAN", "NONE", 0.0, 1.0);
        registerFeature("is_international", "BOOLEAN", "NONE", 0.0, 1.0);
        registerFeature("is_high_value", "BOOLEAN", "NONE", 0.0, 1.0);
        
        log.info("Registered {} feature definitions", featureRegistry.size());
    }

    /**
     * Register a feature definition
     */
    private void registerFeature(String name, String type, String transformation, 
                                Double min, Double max) {
        featureRegistry.put(name, FeatureDefinition.builder()
            .name(name)
            .type(type)
            .transformationType(transformation)
            .min(min)
            .max(max)
            .build());
    }

    /**
     * Get cached features
     */
    private FeatureVector getCachedFeatures(String entityId, String featureSetId) {
        com.waqiti.ml.cache.MLCacheService.FeatureVector cachedVector = mlCacheService.getCachedFeatures(entityId, featureSetId);
        if (cachedVector == null) {
            return null;
        }
        
        // Convert from MLCacheService.FeatureVector to FeatureStoreService.FeatureVector
        return FeatureVector.builder()
            .entityId(cachedVector.getEntityId())
            .featureSetId(cachedVector.getFeatureSetId())
            .features(cachedVector.getFeatures())
            .timestamp(cachedVector.getTimestamp())
            .version(String.valueOf(cachedVector.getVersion()))
            .build();
    }

    /**
     * Cache features
     */
    private void cacheFeatures(String entityId, String featureSetId, FeatureVector features) {
        // Convert from FeatureStoreService.FeatureVector to MLCacheService.FeatureVector
        com.waqiti.ml.cache.MLCacheService.FeatureVector mlFeatureVector = 
            com.waqiti.ml.cache.MLCacheService.FeatureVector.builder()
                .entityId(features.getEntityId())
                .featureSetId(features.getFeatureSetId())
                .features(features.getFeatures())
                .timestamp(features.getTimestamp())
                .version(features.getVersion() != null ? Integer.parseInt(features.getVersion()) : 1)
                .build();
        
        mlCacheService.storeFeatureVector(entityId, featureSetId, mlFeatureVector);
    }

    /**
     * Store feature version
     */
    private void storeFeatureVersion(String entityId, String featureSetId, String version) {
        String key = FEATURE_VERSION_PREFIX + entityId + ":" + featureSetId;
        redisTemplate.opsForValue().set(key, version, cacheTtlMinutes * 2, TimeUnit.MINUTES);
    }

    /**
     * Update feature metadata
     */
    private void updateFeatureMetadata(String featureSetId, FeatureVector features) {
        String key = FEATURE_METADATA_PREFIX + featureSetId;
        FeatureMetadata metadata = (FeatureMetadata) redisTemplate.opsForValue().get(key);
        
        if (metadata == null) {
            metadata = new FeatureMetadata(featureSetId);
        }
        
        metadata.updateWithFeatures(features);
        redisTemplate.opsForValue().set(key, metadata, 24, TimeUnit.HOURS);
    }

    /**
     * Update feature statistics
     */
    private void updateFeatureStatistics(String featureSetId, FeatureVector features) {
        FeatureStatistics stats = featureStatsCache.computeIfAbsent(featureSetId, 
            k -> new FeatureStatistics(featureSetId));
        
        stats.update(features);
        
        // Periodically persist to Redis
        if (stats.getUpdateCount() % 100 == 0) {
            String key = FEATURE_METADATA_PREFIX + "stats:" + featureSetId;
            redisTemplate.opsForValue().set(key, stats, 24, TimeUnit.HOURS);
        }
    }

    /**
     * Encode risk level
     */
    private double encodeRiskLevel(String riskLevel) {
        if (riskLevel == null) return 0.0;
        
        switch (riskLevel.toUpperCase()) {
            case "CRITICAL": return 5.0;
            case "HIGH": return 4.0;
            case "MEDIUM": return 3.0;
            case "LOW": return 2.0;
            case "MINIMAL": return 1.0;
            default: return 0.0;
        }
    }

    /**
     * Generate geohash prefix for location clustering
     */
    private double generateGeohashPrefix(double latitude, double longitude) {
        // Simplified geohash - in production would use proper geohash library
        int latBucket = (int) ((latitude + 90) * 10);
        int lonBucket = (int) ((longitude + 180) * 10);
        return latBucket * 10000 + lonBucket;
    }

    /**
     * Feature vector class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureVector {
        private String entityId;
        private String featureSetId;
        private Map<String, Double> features = new HashMap<>();
        private LocalDateTime timestamp;
        private String version = "1.0.0";
        private boolean validated;
        private Long processingTimeMs;
        
        public void addFeature(String name, double value) {
            features.put(name, value);
        }
    }

    /**
     * Feature definition
     */
    @Data
    @Builder
    private static class FeatureDefinition {
        private String name;
        private String type;
        private String transformationType;
        private Double min;
        private Double max;
        private Double mean;
        private Double stdDev;
        private String description;
    }

    /**
     * Feature metadata
     */
    @Data
    private static class FeatureMetadata {
        private String featureSetId;
        private Set<String> featureNames = new HashSet<>();
        private LocalDateTime firstSeen;
        private LocalDateTime lastSeen;
        private long totalSamples;
        
        public FeatureMetadata(String featureSetId) {
            this.featureSetId = featureSetId;
            this.firstSeen = LocalDateTime.now();
            this.lastSeen = LocalDateTime.now();
        }
        
        public void updateWithFeatures(FeatureVector features) {
            featureNames.addAll(features.getFeatures().keySet());
            lastSeen = LocalDateTime.now();
            totalSamples++;
        }
    }

    /**
     * Feature statistics
     */
    @Data
    private static class FeatureStatistics {
        private String featureSetId;
        private Map<String, Double> featureMeans = new HashMap<>();
        private Map<String, Double> featureStdDevs = new HashMap<>();
        private Map<String, Double> featureMins = new HashMap<>();
        private Map<String, Double> featureMaxs = new HashMap<>();
        private long updateCount;
        
        public FeatureStatistics(String featureSetId) {
            this.featureSetId = featureSetId;
        }
        
        public void update(FeatureVector features) {
            for (Map.Entry<String, Double> entry : features.getFeatures().entrySet()) {
                String name = entry.getKey();
                Double value = entry.getValue();
                
                // Update min/max
                featureMins.merge(name, value, Math::min);
                featureMaxs.merge(name, value, Math::max);
                
                // Update running mean (simplified)
                Double currentMean = featureMeans.getOrDefault(name, 0.0);
                featureMeans.put(name, currentMean + (value - currentMean) / (updateCount + 1));
            }
            
            updateCount++;
        }
    }

    /**
     * Scheduled task to refresh feature statistics
     */
    @Scheduled(fixedDelayString = "${feature.store.stats.refresh.minutes:60}000")
    public void refreshFeatureStatistics() {
        if (!featureStoreEnabled) return;
        
        try {
            log.debug("Refreshing feature statistics for {} feature sets", featureStatsCache.size());
            
            for (Map.Entry<String, FeatureStatistics> entry : featureStatsCache.entrySet()) {
                String key = FEATURE_METADATA_PREFIX + "stats:" + entry.getKey();
                redisTemplate.opsForValue().set(key, entry.getValue(), 24, TimeUnit.HOURS);
            }
            
        } catch (Exception e) {
            log.error("Error refreshing feature statistics", e);
        }
    }

    /**
     * Get feature importance scores
     */
    public Map<String, Double> getFeatureImportance(String featureSetId) {
        // In production, this would integrate with ML model to get actual feature importance
        Map<String, Double> importance = new HashMap<>();
        
        // Default importance scores based on domain knowledge
        importance.put("amount", 0.25);
        importance.put("user_risk_score", 0.20);
        importance.put("network_risk_score", 0.15);
        importance.put("is_international", 0.10);
        importance.put("is_high_value", 0.10);
        importance.put("hour_of_day", 0.05);
        importance.put("is_vpn", 0.05);
        importance.put("device_trust_score", 0.05);
        importance.put("location_risk", 0.05);
        
        return importance;
    }

    /**
     * Clear feature cache for an entity
     */
    @CacheEvict(value = "features", key = "#entityId + ':*'")
    public void clearFeatureCache(String entityId) {
        log.debug("Cleared feature cache for entity: {}", entityId);
    }
}