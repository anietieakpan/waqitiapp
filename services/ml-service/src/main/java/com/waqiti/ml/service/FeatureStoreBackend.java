package com.waqiti.ml.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Enterprise Feature Store Backend Service
 * 
 * Provides a centralized feature store for machine learning pipelines with support for
 * real-time and batch feature computation, versioning, and serving.
 * 
 * Features:
 * - Real-time feature computation and serving
 * - Batch feature processing and storage
 * - Feature versioning and lineage tracking
 * - Low-latency feature retrieval
 * - Feature monitoring and statistics
 * - Point-in-time feature retrieval for training
 * 
 * @author Waqiti ML Team
 * @version 2.0.0
 * @since 2024-01-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureStoreBackend implements com.waqiti.ml.cache.MLCacheService.FeatureStoreBackend {

    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Cache prefixes
    private static final String FEATURE_PREFIX = "feature:store:";
    private static final String FEATURE_SET_PREFIX = "feature:set:";
    private static final String FEATURE_STATS_PREFIX = "feature:stats:";
    
    // Local cache for hot features
    private final ConcurrentHashMap<String, CachedFeature> localFeatureCache = new ConcurrentHashMap<>();
    
    // Feature computation pipelines
    private final Map<String, FeatureComputation> featureComputations = new ConcurrentHashMap<>();

    /**
     * Get features for an entity and feature set
     */
    @Override
    public com.waqiti.ml.cache.MLCacheService.FeatureVector getFeatures(String entityId, String featureSetId) {
        try {
            log.debug("Getting features for entity: {}, featureSet: {}", entityId, featureSetId);
            
            // Check local cache for hot features
            String cacheKey = buildFeatureCacheKey(entityId, featureSetId);
            CachedFeature cached = localFeatureCache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                log.debug("Features found in local cache");
                incrementFeatureAccessCount(featureSetId);
                return cached.getFeatureVector();
            }
            
            // Check Redis for warm features
            Map<Object, Object> redisFeatures = redisTemplate.opsForHash().entries(cacheKey);
            if (!redisFeatures.isEmpty()) {
                com.waqiti.ml.cache.MLCacheService.FeatureVector featureVector = deserializeFeatureVector(entityId, featureSetId, redisFeatures);
                if (featureVector != null) {
                    updateLocalCache(cacheKey, featureVector);
                    incrementFeatureAccessCount(featureSetId);
                    log.debug("Features found in Redis cache");
                    return featureVector;
                }
            }
            
            // Compute features on-demand or retrieve from database
            com.waqiti.ml.cache.MLCacheService.FeatureVector featureVector = computeOrRetrieveFeatures(entityId, featureSetId);
            
            if (featureVector != null) {
                // Cache the computed features
                cacheFeatureVector(cacheKey, featureVector);
                incrementFeatureAccessCount(featureSetId);
            }
            
            return featureVector;
            
        } catch (Exception e) {
            log.error("Error getting features for entity {} and featureSet {}: {}", 
                entityId, featureSetId, e.getMessage());
            return null;
        }
    }

    /**
     * Store computed features
     */
    @Override
    @Transactional
    public void storeFeatures(com.waqiti.ml.cache.MLCacheService.FeatureVector featureVector) {
        try {
            log.info("Storing features for entity: {}, featureSet: {}", 
                featureVector.getEntityId(), featureVector.getFeatureSetId());
            
            // Validate feature vector
            if (!validateFeatureVector(featureVector)) {
                log.error("Invalid feature vector for entity: {}", featureVector.getEntityId());
                throw new IllegalArgumentException("Invalid feature vector");
            }
            
            // Store in database for persistence
            storeFeaturesToDatabase(featureVector);
            
            // Cache in Redis for fast retrieval
            String cacheKey = buildFeatureCacheKey(featureVector.getEntityId(), featureVector.getFeatureSetId());
            cacheFeatureVector(cacheKey, featureVector);
            
            // Update feature statistics
            updateFeatureStatistics(featureVector);
            
            // Publish feature update event
            publishFeatureUpdateEvent(featureVector);
            
            log.info("Successfully stored features for entity: {}", featureVector.getEntityId());
            
        } catch (Exception e) {
            log.error("Error storing features for entity {}: {}", 
                featureVector.getEntityId(), e.getMessage());
            throw new RuntimeException("Failed to store features", e);
        }
    }

    /**
     * Compute real-time features for an entity
     */
    public Map<String, Object> computeRealTimeFeatures(String entityId, String entityType) {
        try {
            log.debug("Computing real-time features for entity: {} of type: {}", entityId, entityType);
            
            Map<String, Object> features = new HashMap<>();
            
            switch (entityType) {
                case "USER":
                    features.putAll(computeUserFeatures(entityId));
                    break;
                case "TRANSACTION":
                    features.putAll(computeTransactionFeatures(entityId));
                    break;
                case "DEVICE":
                    features.putAll(computeDeviceFeatures(entityId));
                    break;
                case "MERCHANT":
                    features.putAll(computeMerchantFeatures(entityId));
                    break;
                default:
                    log.warn("Unknown entity type: {}", entityType);
            }
            
            // Add computed timestamp
            features.put("computed_at", LocalDateTime.now().toString());
            features.put("feature_version", "2.0");
            
            return features;
            
        } catch (Exception e) {
            log.error("Error computing real-time features for entity {}: {}", entityId, e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Get point-in-time features for training
     */
    public List<com.waqiti.ml.cache.MLCacheService.FeatureVector> getPointInTimeFeatures(
            List<String> entityIds, String featureSetId, LocalDateTime timestamp) {
        try {
            log.info("Getting point-in-time features for {} entities at {}", entityIds.size(), timestamp);
            
            List<com.waqiti.ml.cache.MLCacheService.FeatureVector> featureVectors = new ArrayList<>();
            
            // Batch retrieve features from database
            String sql = "SELECT entity_id, features, version FROM feature_store " +
                        "WHERE entity_id = ANY(?) AND feature_set_id = ? AND created_at <= ? " +
                        "AND (expired_at IS NULL OR expired_at > ?) " +
                        "ORDER BY entity_id, version DESC";
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, 
                entityIds.toArray(new String[0]), featureSetId, timestamp, timestamp);
            
            // Group by entity and take the latest version for each
            Map<String, Map<String, Object>> latestFeatures = new HashMap<>();
            for (Map<String, Object> row : results) {
                String entityId = (String) row.get("entity_id");
                if (!latestFeatures.containsKey(entityId)) {
                    latestFeatures.put(entityId, row);
                }
            }
            
            // Convert to feature vectors
            for (Map.Entry<String, Map<String, Object>> entry : latestFeatures.entrySet()) {
                com.waqiti.ml.cache.MLCacheService.FeatureVector vector = createFeatureVectorFromDb(
                    entry.getKey(), featureSetId, entry.getValue());
                if (vector != null) {
                    featureVectors.add(vector);
                }
            }
            
            log.info("Retrieved {} feature vectors for point-in-time query", featureVectors.size());
            return featureVectors;
            
        } catch (Exception e) {
            log.error("Error getting point-in-time features: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Register a feature computation pipeline
     */
    public void registerFeatureComputation(String featureSetId, FeatureComputation computation) {
        try {
            log.info("Registering feature computation for featureSet: {}", featureSetId);
            
            featureComputations.put(featureSetId, computation);
            
            // Store computation metadata in database
            String sql = "INSERT INTO feature_computations (feature_set_id, computation_type, " +
                        "computation_config, is_active, created_at) VALUES (?, ?, ?::jsonb, ?, ?) " +
                        "ON CONFLICT (feature_set_id) DO UPDATE SET " +
                        "computation_type = EXCLUDED.computation_type, " +
                        "computation_config = EXCLUDED.computation_config, " +
                        "is_active = EXCLUDED.is_active, " +
                        "updated_at = ?";
            
            jdbcTemplate.update(sql, 
                featureSetId,
                computation.getType(),
                convertToJson(computation.getConfig()),
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
            );
            
            log.info("Successfully registered feature computation for {}", featureSetId);
            
        } catch (Exception e) {
            log.error("Error registering feature computation for {}: {}", featureSetId, e.getMessage());
            throw new RuntimeException("Failed to register feature computation", e);
        }
    }

    /**
     * Update feature statistics
     */
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    public void updateFeatureStatistics() {
        try {
            log.debug("Updating feature statistics");
            
            // Get all active feature sets
            String sql = "SELECT DISTINCT feature_set_id FROM feature_store " +
                        "WHERE created_at > ? GROUP BY feature_set_id";
            
            List<String> featureSets = jdbcTemplate.queryForList(sql, String.class, 
                LocalDateTime.now().minusDays(7));
            
            for (String featureSetId : featureSets) {
                computeFeatureSetStatistics(featureSetId);
            }
            
            log.debug("Feature statistics update completed");
            
        } catch (Exception e) {
            log.error("Error updating feature statistics: {}", e.getMessage());
        }
    }

    // Helper methods

    private String buildFeatureCacheKey(String entityId, String featureSetId) {
        return FEATURE_PREFIX + entityId + ":" + featureSetId;
    }

    private com.waqiti.ml.cache.MLCacheService.FeatureVector deserializeFeatureVector(
            String entityId, String featureSetId, Map<Object, Object> data) {
        try {
            Map<String, Object> features = new HashMap<>();
            for (Map.Entry<Object, Object> entry : data.entrySet()) {
                if (!"version".equals(entry.getKey()) && !"timestamp".equals(entry.getKey())) {
                    features.put(entry.getKey().toString(), entry.getValue());
                }
            }
            
            return com.waqiti.ml.cache.MLCacheService.FeatureVector.builder()
                .entityId(entityId)
                .featureSetId(featureSetId)
                .features(features)
                .version(data.containsKey("version") ? Integer.parseInt(data.get("version").toString()) : 1)
                .timestamp(data.containsKey("timestamp") ? 
                    LocalDateTime.parse(data.get("timestamp").toString()) : LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Error deserializing feature vector: {}", e.getMessage());
            return null;
        }
    }

    private void updateLocalCache(String cacheKey, com.waqiti.ml.cache.MLCacheService.FeatureVector featureVector) {
        localFeatureCache.put(cacheKey, new CachedFeature(featureVector, 300)); // 5 min TTL
    }

    private void incrementFeatureAccessCount(String featureSetId) {
        try {
            String statsKey = FEATURE_STATS_PREFIX + featureSetId;
            redisTemplate.opsForHash().increment(statsKey, "access_count", 1);
            redisTemplate.expire(statsKey, 24, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("Error incrementing access count for featureSet {}: {}", featureSetId, e.getMessage());
        }
    }

    private com.waqiti.ml.cache.MLCacheService.FeatureVector computeOrRetrieveFeatures(
            String entityId, String featureSetId) {
        try {
            // Check if computation is registered
            FeatureComputation computation = featureComputations.get(featureSetId);
            if (computation != null) {
                // Compute features using registered pipeline
                Map<String, Object> computedFeatures = computation.compute(entityId);
                
                com.waqiti.ml.cache.MLCacheService.FeatureVector featureVector = 
                    com.waqiti.ml.cache.MLCacheService.FeatureVector.builder()
                        .entityId(entityId)
                        .featureSetId(featureSetId)
                        .features(computedFeatures)
                        .version(1)
                        .timestamp(LocalDateTime.now())
                        .build();
                
                // Store computed features
                storeFeatures(featureVector);
                
                return featureVector;
            }
            
            // Retrieve from database
            String sql = "SELECT features, version, created_at FROM feature_store " +
                        "WHERE entity_id = ? AND feature_set_id = ? " +
                        "ORDER BY version DESC LIMIT 1";
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, entityId, featureSetId);
            
            if (!results.isEmpty()) {
                return createFeatureVectorFromDb(entityId, featureSetId, results.get(0));
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("Error computing/retrieving features: {}", e.getMessage());
            return null;
        }
    }

    private com.waqiti.ml.cache.MLCacheService.FeatureVector createFeatureVectorFromDb(
            String entityId, String featureSetId, Map<String, Object> row) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> features = (Map<String, Object>) row.get("features");
            
            return com.waqiti.ml.cache.MLCacheService.FeatureVector.builder()
                .entityId(entityId)
                .featureSetId(featureSetId)
                .features(features != null ? features : new HashMap<>())
                .version(((Number) row.get("version")).intValue())
                .timestamp(row.get("created_at") != null ? 
                    ((java.sql.Timestamp) row.get("created_at")).toLocalDateTime() : LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Error creating feature vector from DB row: {}", e.getMessage());
            return null;
        }
    }

    private void cacheFeatureVector(String cacheKey, com.waqiti.ml.cache.MLCacheService.FeatureVector featureVector) {
        try {
            // Cache in Redis
            Map<String, Object> cacheData = new HashMap<>(featureVector.getFeatures());
            cacheData.put("version", featureVector.getVersion());
            cacheData.put("timestamp", featureVector.getTimestamp().toString());
            
            redisTemplate.opsForHash().putAll(cacheKey, cacheData);
            redisTemplate.expire(cacheKey, 2, TimeUnit.HOURS);
            
            // Update local cache
            updateLocalCache(cacheKey, featureVector);
            
        } catch (Exception e) {
            log.error("Error caching feature vector: {}", e.getMessage());
        }
    }

    private boolean validateFeatureVector(com.waqiti.ml.cache.MLCacheService.FeatureVector featureVector) {
        if (featureVector == null) return false;
        if (featureVector.getEntityId() == null || featureVector.getEntityId().isEmpty()) return false;
        if (featureVector.getFeatureSetId() == null || featureVector.getFeatureSetId().isEmpty()) return false;
        if (featureVector.getFeatures() == null || featureVector.getFeatures().isEmpty()) return false;
        return true;
    }

    @Transactional
    private void storeFeaturesToDatabase(com.waqiti.ml.cache.MLCacheService.FeatureVector featureVector) {
        try {
            // Get next version number
            String versionSql = "SELECT COALESCE(MAX(version), 0) + 1 FROM feature_store " +
                               "WHERE entity_id = ? AND feature_set_id = ?";
            Integer nextVersion = jdbcTemplate.queryForObject(versionSql, Integer.class, 
                featureVector.getEntityId(), featureVector.getFeatureSetId());
            
            // Insert new feature version
            String insertSql = "INSERT INTO feature_store (entity_id, feature_set_id, features, " +
                              "version, created_at, expired_at) VALUES (?, ?, ?::jsonb, ?, ?, ?)";
            
            jdbcTemplate.update(insertSql,
                featureVector.getEntityId(),
                featureVector.getFeatureSetId(),
                convertToJson(featureVector.getFeatures()),
                nextVersion,
                LocalDateTime.now(),
                null // No expiration by default
            );
            
            // Update version in the vector
            featureVector.setVersion(nextVersion);
            
        } catch (Exception e) {
            log.error("Error storing features to database: {}", e.getMessage());
            throw new RuntimeException("Failed to store features", e);
        }
    }

    private void updateFeatureStatistics(com.waqiti.ml.cache.MLCacheService.FeatureVector featureVector) {
        try {
            String statsKey = FEATURE_STATS_PREFIX + featureVector.getFeatureSetId();
            
            Map<String, String> stats = new HashMap<>();
            stats.put("last_updated", LocalDateTime.now().toString());
            stats.put("feature_count", String.valueOf(featureVector.getFeatures().size()));
            stats.put("latest_version", String.valueOf(featureVector.getVersion()));
            
            redisTemplate.opsForHash().putAll(statsKey, stats);
            redisTemplate.expire(statsKey, 24, TimeUnit.HOURS);
            
        } catch (Exception e) {
            log.error("Error updating feature statistics: {}", e.getMessage());
        }
    }

    private void publishFeatureUpdateEvent(com.waqiti.ml.cache.MLCacheService.FeatureVector featureVector) {
        try {
            Map<String, Object> event = Map.of(
                "entity_id", featureVector.getEntityId(),
                "feature_set_id", featureVector.getFeatureSetId(),
                "version", featureVector.getVersion(),
                "timestamp", featureVector.getTimestamp().toString(),
                "event_type", "FEATURE_UPDATE"
            );
            
            kafkaTemplate.send("feature-updates", featureVector.getEntityId(), event);
            
        } catch (Exception e) {
            log.error("Error publishing feature update event: {}", e.getMessage());
        }
    }

    private Map<String, Object> computeUserFeatures(String userId) {
        Map<String, Object> features = new HashMap<>();
        
        try {
            // Transaction velocity features
            String txnSql = "SELECT COUNT(*) as txn_count_24h, AVG(amount) as avg_amount_24h, " +
                           "MAX(amount) as max_amount_24h FROM transactions " +
                           "WHERE user_id = ? AND created_at > ?";
            
            Map<String, Object> txnStats = jdbcTemplate.queryForMap(txnSql, 
                userId, LocalDateTime.now().minusHours(24));
            
            features.put("txn_count_24h", txnStats.get("txn_count_24h"));
            features.put("avg_txn_amount_24h", txnStats.get("avg_amount_24h"));
            features.put("max_txn_amount_24h", txnStats.get("max_amount_24h"));
            
            // Account features
            String accountSql = "SELECT account_age_days, kyc_level, risk_score FROM users WHERE user_id = ?";
            Map<String, Object> accountInfo = jdbcTemplate.queryForMap(accountSql, userId);
            features.putAll(accountInfo);
            
            // Device features
            String deviceSql = "SELECT COUNT(DISTINCT device_id) as device_count FROM device_user_associations " +
                              "WHERE user_id = ?";
            Integer deviceCount = jdbcTemplate.queryForObject(deviceSql, Integer.class, userId);
            features.put("device_count", deviceCount);
            
        } catch (Exception e) {
            log.error("Error computing user features: {}", e.getMessage());
        }
        
        return features;
    }

    private Map<String, Object> computeTransactionFeatures(String transactionId) {
        Map<String, Object> features = new HashMap<>();
        
        try {
            // Transaction details
            String sql = "SELECT amount, currency, merchant_id, user_id, device_id, created_at " +
                        "FROM transactions WHERE transaction_id = ?";
            Map<String, Object> txnDetails = jdbcTemplate.queryForMap(sql, transactionId);
            
            // Basic features
            features.put("amount", txnDetails.get("amount"));
            features.put("currency", txnDetails.get("currency"));
            
            // Time-based features
            LocalDateTime txnTime = ((java.sql.Timestamp) txnDetails.get("created_at")).toLocalDateTime();
            features.put("hour_of_day", txnTime.getHour());
            features.put("day_of_week", txnTime.getDayOfWeek().getValue());
            
            // Velocity features
            String userId = (String) txnDetails.get("user_id");
            String velocitySql = "SELECT COUNT(*) as txn_count_1h FROM transactions " +
                                "WHERE user_id = ? AND created_at > ? AND created_at < ?";
            
            Integer recentTxnCount = jdbcTemplate.queryForObject(velocitySql, Integer.class,
                userId, txnTime.minusHours(1), txnTime);
            features.put("user_txn_count_1h", recentTxnCount);
            
        } catch (Exception e) {
            log.error("Error computing transaction features: {}", e.getMessage());
        }
        
        return features;
    }

    private Map<String, Object> computeDeviceFeatures(String deviceId) {
        Map<String, Object> features = new HashMap<>();
        
        try {
            // Device trust score
            String sql = "SELECT trust_score, first_seen, is_flagged FROM device_tracking WHERE device_id = ?";
            Map<String, Object> deviceInfo = jdbcTemplate.queryForMap(sql, deviceId);
            
            features.put("device_trust_score", deviceInfo.get("trust_score"));
            features.put("device_is_flagged", deviceInfo.get("is_flagged"));
            
            // Device age
            LocalDateTime firstSeen = ((java.sql.Timestamp) deviceInfo.get("first_seen")).toLocalDateTime();
            long deviceAgeDays = java.time.Duration.between(firstSeen, LocalDateTime.now()).toDays();
            features.put("device_age_days", deviceAgeDays);
            
            // User associations
            String userSql = "SELECT COUNT(DISTINCT user_id) as user_count FROM device_user_associations " +
                            "WHERE device_id = ?";
            Integer userCount = jdbcTemplate.queryForObject(userSql, Integer.class, deviceId);
            features.put("device_user_count", userCount);
            
        } catch (Exception e) {
            log.error("Error computing device features: {}", e.getMessage());
        }
        
        return features;
    }

    private Map<String, Object> computeMerchantFeatures(String merchantId) {
        Map<String, Object> features = new HashMap<>();
        
        try {
            // Merchant risk profile
            String sql = "SELECT risk_category, mcc_code, country, chargeback_rate " +
                        "FROM merchants WHERE merchant_id = ?";
            Map<String, Object> merchantInfo = jdbcTemplate.queryForMap(sql, merchantId);
            features.putAll(merchantInfo);
            
            // Transaction statistics
            String statsSql = "SELECT COUNT(*) as total_txns, AVG(amount) as avg_amount, " +
                             "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed_count " +
                             "FROM transactions WHERE merchant_id = ? AND created_at > ?";
            
            Map<String, Object> stats = jdbcTemplate.queryForMap(statsSql, 
                merchantId, LocalDateTime.now().minusDays(30));
            
            features.put("merchant_txn_count_30d", stats.get("total_txns"));
            features.put("merchant_avg_amount_30d", stats.get("avg_amount"));
            features.put("merchant_failure_rate", 
                ((Number) stats.get("failed_count")).doubleValue() / 
                Math.max(1, ((Number) stats.get("total_txns")).doubleValue()));
            
        } catch (Exception e) {
            log.error("Error computing merchant features: {}", e.getMessage());
        }
        
        return features;
    }

    private void computeFeatureSetStatistics(String featureSetId) {
        try {
            String sql = "SELECT COUNT(DISTINCT entity_id) as entity_count, " +
                        "MAX(version) as max_version, COUNT(*) as total_records " +
                        "FROM feature_store WHERE feature_set_id = ?";
            
            Map<String, Object> stats = jdbcTemplate.queryForMap(sql, featureSetId);
            
            String statsKey = FEATURE_STATS_PREFIX + featureSetId;
            Map<String, String> statsMap = new HashMap<>();
            statsMap.put("entity_count", stats.get("entity_count").toString());
            statsMap.put("max_version", stats.get("max_version").toString());
            statsMap.put("total_records", stats.get("total_records").toString());
            statsMap.put("computed_at", LocalDateTime.now().toString());
            
            redisTemplate.opsForHash().putAll(statsKey, statsMap);
            redisTemplate.expire(statsKey, 24, TimeUnit.HOURS);
            
        } catch (Exception e) {
            log.error("Error computing statistics for featureSet {}: {}", featureSetId, e.getMessage());
        }
    }

    private String convertToJson(Object data) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
        } catch (Exception e) {
            log.error("Error converting to JSON: {}", e.getMessage());
            return "{}";
        }
    }

    // Inner classes

    private static class CachedFeature {
        private final com.waqiti.ml.cache.MLCacheService.FeatureVector featureVector;
        private final long cachedAt;
        private final long ttlSeconds;

        public CachedFeature(com.waqiti.ml.cache.MLCacheService.FeatureVector featureVector, long ttlSeconds) {
            this.featureVector = featureVector;
            this.cachedAt = System.currentTimeMillis();
            this.ttlSeconds = ttlSeconds;
        }

        public boolean isExpired() {
            return (System.currentTimeMillis() - cachedAt) > (ttlSeconds * 1000);
        }

        public com.waqiti.ml.cache.MLCacheService.FeatureVector getFeatureVector() {
            return featureVector;
        }
    }

    public interface FeatureComputation {
        Map<String, Object> compute(String entityId);
        String getType();
        Map<String, Object> getConfig();
    }
}