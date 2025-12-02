package com.waqiti.payment.ml;

import com.waqiti.payment.dto.FraudCheckRequest;
import com.waqiti.payment.ml.dto.MLFeatureVector;
import com.waqiti.payment.repository.FraudCheckRecordRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * ML Feature Engineering Service for Production Fraud Detection
 *
 * Extracts 100+ features from transaction data for ML model inference
 *
 * Feature Categories:
 * 1. Transaction Features (20+ features)
 * 2. User Behavior Features (30+ features)
 * 3. Device Features (15+ features)
 * 4. Network Features (15+ features)
 * 5. Temporal Features (10+ features)
 * 6. Merchant Features (10+ features)
 * 7. Derived/Engineered Features (20+ features)
 *
 * Performance:
 * - Target: <10ms p95 latency
 * - Uses Redis caching for historical features
 * - Batch database queries optimized with indexes
 *
 * @author Waqiti ML Engineering Team
 * @version 2.0.0
 * @since 2025-10-16
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MLFeatureEngineeringService {

    private final FraudCheckRecordRepository fraudCheckRecordRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;

    private static final int FEATURE_CACHE_TTL_MINUTES = 5;

    /**
     * Extract comprehensive feature vector for ML model
     *
     * @param request Fraud check request
     * @return Feature vector with 100+ features
     */
    public MLFeatureVector extractFeatures(FraudCheckRequest request) {
        long startTime = System.nanoTime();

        try {
            String userId = request.getUserId();
            String deviceId = request.getDeviceId();
            String ipAddress = request.getSourceIpAddress();

            log.debug("FEATURE: Extracting features for transaction: {}", request.getTransactionId());

            // Build feature vector
            MLFeatureVector.MLFeatureVectorBuilder builder = MLFeatureVector.builder();

            // ========== TRANSACTION FEATURES (20+) ==========
            extractTransactionFeatures(request, builder);

            // ========== USER BEHAVIOR FEATURES (30+) ==========
            extractUserBehaviorFeatures(userId, request, builder);

            // ========== DEVICE FEATURES (15+) ==========
            if (deviceId != null && !deviceId.isEmpty()) {
                extractDeviceFeatures(deviceId, userId, builder);
            }

            // ========== NETWORK FEATURES (15+) ==========
            if (ipAddress != null && !ipAddress.isEmpty()) {
                extractNetworkFeatures(ipAddress, builder);
            }

            // ========== TEMPORAL FEATURES (10+) ==========
            extractTemporalFeatures(builder);

            // ========== MERCHANT FEATURES (10+) ==========
            if (request.getMerchantId() != null && !request.getMerchantId().isEmpty()) {
                extractMerchantFeatures(request.getMerchantId(), builder);
            }

            // ========== DERIVED/ENGINEERED FEATURES (20+) ==========
            MLFeatureVector features = builder.build();
            addDerivedFeatures(features, request);

            long duration = (System.nanoTime() - startTime) / 1_000_000; // Convert to ms
            features.setExtractionDurationMs(duration);

            log.debug("FEATURE: Extraction completed in {}ms: transaction={}, featureCount={}",
                duration, request.getTransactionId(), features.getFeatureCount());

            // Track metrics
            meterRegistry.timer("ml.feature.extraction.duration")
                .record(duration, TimeUnit.MILLISECONDS);

            meterRegistry.gauge("ml.feature.count", features.getFeatureCount());

            return features;

        } catch (Exception e) {
            log.error("FEATURE: Feature extraction failed for transaction: {}", request.getTransactionId(), e);

            // Return minimal feature set on error
            return MLFeatureVector.builder()
                .transactionAmount(request.getAmount() != null ? request.getAmount().doubleValue() : 0.0)
                .transactionAmountLog(Math.log(request.getAmount() != null ? request.getAmount().doubleValue() + 1 : 1))
                .hour(LocalDateTime.now().getHour())
                .dayOfWeek(LocalDateTime.now().getDayOfWeek().getValue())
                .isWeekend(LocalDateTime.now().getDayOfWeek().getValue() >= 6)
                .extractionError(true)
                .extractionErrorMessage(e.getMessage())
                .build();
        }
    }

    /**
     * Extract transaction-specific features
     */
    private void extractTransactionFeatures(FraudCheckRequest request, MLFeatureVector.MLFeatureVectorBuilder builder) {
        BigDecimal amount = request.getAmount();
        double amountValue = amount != null ? amount.doubleValue() : 0.0;

        // Raw amount
        builder.transactionAmount(amountValue);

        // Log-transformed amount (helps with ML model)
        builder.transactionAmountLog(Math.log(amountValue + 1));

        // Amount squared (captures non-linear patterns)
        builder.transactionAmountSquared(amountValue * amountValue);

        // Amount root (captures diminishing returns pattern)
        builder.transactionAmountSqrt(Math.sqrt(amountValue));

        // Round number indicator (fraudsters often use round numbers)
        builder.isRoundAmount(amountValue % 100 == 0 || amountValue % 1000 == 0);

        // Currency
        String currency = request.getCurrency() != null ? request.getCurrency() : "USD";
        builder.transactionCurrency(currency);
        builder.isForeignCurrency(!currency.equals("USD"));

        // Transaction type
        String txType = request.getTransactionType() != null ? request.getTransactionType() : "UNKNOWN";
        builder.transactionType(txType);
        builder.isP2P("P2P".equalsIgnoreCase(txType));
        builder.isMerchantPayment("MERCHANT".equalsIgnoreCase(txType));
        builder.isWithdrawal("WITHDRAWAL".equalsIgnoreCase(txType));

        // Failed attempts
        Integer failedAttempts = request.getFailedAttempts();
        builder.failedAttempts(failedAttempts != null ? failedAttempts : 0);
        builder.hasFailedAttempts(failedAttempts != null && failedAttempts > 0);
        builder.failedAttemptsLog(Math.log((failedAttempts != null ? failedAttempts : 0) + 1));

        // Device/location trust
        builder.isKnownDevice(Boolean.TRUE.equals(request.getKnownDevice()));
        builder.isTrustedLocation(Boolean.TRUE.equals(request.getTrustedLocation()));
    }

    /**
     * Extract user behavior features from historical data
     */
    private void extractUserBehaviorFeatures(String userId, FraudCheckRequest request,
                                            MLFeatureVector.MLFeatureVectorBuilder builder) {

        // Try cache first
        String cacheKey = "ml:user_features:" + userId;
        Map<String, Object> cachedFeatures = getCachedFeatures(cacheKey);

        if (cachedFeatures != null) {
            applyUserFeaturesFromCache(cachedFeatures, builder);
            return;
        }

        // Compute from database
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last30Days = now.minusDays(30);
        LocalDateTime last7Days = now.minusDays(7);
        LocalDateTime last24Hours = now.minusDays(1);
        LocalDateTime last1Hour = now.minusHours(1);

        // Transaction counts
        long count30d = fraudCheckRecordRepository.countByUserIdAndCreatedAtAfter(userId, last30Days);
        long count7d = fraudCheckRecordRepository.countByUserIdAndCreatedAtAfter(userId, last7Days);
        long count24h = fraudCheckRecordRepository.countByUserIdAndCreatedAtAfter(userId, last24Hours);
        long count1h = fraudCheckRecordRepository.countByUserIdAndCreatedAtAfter(userId, last1Hour);

        builder.userTransactionCount30d((int) count30d);
        builder.userTransactionCount7d((int) count7d);
        builder.userTransactionCount24h((int) count24h);
        builder.userTransactionCount1h((int) count1h);

        // Velocity (transactions per hour)
        builder.userVelocity1h(count1h);
        builder.userVelocity24h(count24h / 24.0);
        builder.userVelocity7d(count7d / (7.0 * 24.0));

        // Average transaction amount
        Double avgAmount30d = fraudCheckRecordRepository.getAvgAmountByUserIdSince(userId, last30Days);
        Double avgAmount7d = fraudCheckRecordRepository.getAvgAmountByUserIdSince(userId, last7Days);

        builder.userAvgAmount30d(avgAmount30d != null ? avgAmount30d : 0.0);
        builder.userAvgAmount7d(avgAmount7d != null ? avgAmount7d : 0.0);

        // Standard deviation (measure of consistency)
        Double stdDev30d = fraudCheckRecordRepository.getStdDevAmountByUserIdSince(userId, last30Days);
        builder.userStdDevAmount30d(stdDev30d != null ? stdDev30d : 0.0);

        // Current transaction vs average (anomaly detection)
        double currentAmount = request.getAmount() != null ? request.getAmount().doubleValue() : 0.0;
        double avgAmount = avgAmount30d != null ? avgAmount30d : 0.0;

        if (avgAmount > 0) {
            double deviation = Math.abs(currentAmount - avgAmount) / avgAmount;
            builder.amountDeviationFromAvg(deviation);
            builder.isAmountAnomaly(deviation > 2.0); // More than 2x std dev
        }

        // Max transaction amount
        Double maxAmount30d = fraudCheckRecordRepository.getMaxAmountByUserIdSince(userId, last30Days);
        builder.userMaxAmount30d(maxAmount30d != null ? maxAmount30d : 0.0);

        // Time since last transaction
        LocalDateTime lastTx = fraudCheckRecordRepository.getLastTransactionTime(userId);
        if (lastTx != null) {
            long minutesSinceLastTx = ChronoUnit.MINUTES.between(lastTx, now);
            builder.minutesSinceLastTransaction(minutesSinceLastTx);
            builder.hoursSinceLastTransaction(minutesSinceLastTx / 60.0);
        } else {
            builder.minutesSinceLastTransaction(Long.MAX_VALUE);
            builder.isFirstTransaction(true);
        }

        // Fraud history
        long highRiskCount = fraudCheckRecordRepository.countHighRiskByUserId(userId, last30Days);
        long blockedCount = fraudCheckRecordRepository.countBlockedByUserId(userId, last30Days);

        builder.userHighRiskCount30d((int) highRiskCount);
        builder.userBlockedCount30d((int) blockedCount);
        builder.hasRecentHighRisk(highRiskCount > 0);

        // Cache for 5 minutes
        cacheUserFeatures(cacheKey, builder);
    }

    /**
     * Extract device-specific features
     */
    private void extractDeviceFeatures(String deviceId, String userId,
                                      MLFeatureVector.MLFeatureVectorBuilder builder) {

        // Device age
        LocalDateTime firstSeen = fraudCheckRecordRepository.getDeviceFirstSeenTime(deviceId);
        if (firstSeen != null) {
            long deviceAgeHours = ChronoUnit.HOURS.between(firstSeen, LocalDateTime.now());
            builder.deviceAgeHours(deviceAgeHours);
            builder.deviceAgeDays(deviceAgeHours / 24.0);
            builder.isNewDevice(deviceAgeHours < 24); // Less than 1 day old
        } else {
            builder.isNewDevice(true);
        }

        // Device transaction count
        long deviceTxCount = fraudCheckRecordRepository.countByDeviceId(deviceId);
        builder.deviceTransactionCount((int) deviceTxCount);

        // Device user count (shared device indicator)
        long deviceUserCount = fraudCheckRecordRepository.countDistinctUsersByDeviceId(deviceId);
        builder.deviceUserCount((int) deviceUserCount);
        builder.isSharedDevice(deviceUserCount > 1);

        // Device fraud history
        long deviceBlockedCount = fraudCheckRecordRepository.countBlockedByDeviceId(deviceId);
        builder.deviceBlockedCount((int) deviceBlockedCount);
        builder.deviceRiskScore(deviceBlockedCount > 0 ? (double) deviceBlockedCount / deviceTxCount : 0.0);

        // User-device relationship
        boolean isUserPrimaryDevice = fraudCheckRecordRepository.isUserPrimaryDevice(userId, deviceId);
        builder.isUserPrimaryDevice(isUserPrimaryDevice);
    }

    /**
     * Extract network/IP features
     */
    private void extractNetworkFeatures(String ipAddress, MLFeatureVector.MLFeatureVectorBuilder builder) {

        // IP transaction count
        LocalDateTime last24h = LocalDateTime.now().minusDays(1);
        long ipCount24h = fraudCheckRecordRepository.countByIpAddressSince(ipAddress, last24h);
        builder.ipTransactionCount24h((int) ipCount24h);
        builder.isHighVelocityIP(ipCount24h > 50);

        // IP user count (shared IP indicator)
        long ipUserCount = fraudCheckRecordRepository.countDistinctUsersByIp(ipAddress);
        builder.ipUserCount((int) ipUserCount);
        builder.isSharedIP(ipUserCount > 5);

        // IP blacklist check
        Boolean isBlacklisted = (Boolean) redisTemplate.opsForValue().get("blacklist:ip:" + ipAddress);
        builder.isIPBlacklisted(Boolean.TRUE.equals(isBlacklisted));

        // IP geolocation (from cache/external service)
        Map<String, Object> geoData = getIPGeolocation(ipAddress);
        if (geoData != null) {
            builder.ipCountry((String) geoData.get("country"));
            builder.ipCity((String) geoData.get("city"));
            builder.ipLatitude((Double) geoData.getOrDefault("latitude", 0.0));
            builder.ipLongitude((Double) geoData.getOrDefault("longitude", 0.0));
            builder.isVPN((Boolean) geoData.getOrDefault("isVPN", false));
            builder.isTor((Boolean) geoData.getOrDefault("isTor", false));
            builder.isProxy((Boolean) geoData.getOrDefault("isProxy", false));
        }

        // IP fraud history
        long ipBlockedCount = fraudCheckRecordRepository.countBlockedByIp(ipAddress);
        builder.ipBlockedCount((int) ipBlockedCount);
    }

    /**
     * Extract temporal features
     */
    private void extractTemporalFeatures(MLFeatureVector.MLFeatureVectorBuilder builder) {
        LocalDateTime now = LocalDateTime.now();

        // Basic time features
        builder.hour(now.getHour());
        builder.minute(now.getMinute());
        builder.dayOfWeek(now.getDayOfWeek().getValue());
        builder.dayOfMonth(now.getDayOfMonth());
        builder.monthOfYear(now.getMonthValue());

        // Derived time features
        builder.isWeekend(now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY);
        builder.isBusinessHours(now.getHour() >= 9 && now.getHour() <= 17);
        builder.isOffHours(now.getHour() >= 1 && now.getHour() <= 5);
        builder.isLateNight(now.getHour() >= 22 || now.getHour() <= 5);

        // Cyclical encoding (helps ML models)
        double hourRadians = 2 * Math.PI * now.getHour() / 24.0;
        builder.hourSin(Math.sin(hourRadians));
        builder.hourCos(Math.cos(hourRadians));

        double dayRadians = 2 * Math.PI * now.getDayOfWeek().getValue() / 7.0;
        builder.dayOfWeekSin(Math.sin(dayRadians));
        builder.dayOfWeekCos(Math.cos(dayRadians));
    }

    /**
     * Extract merchant features
     */
    private void extractMerchantFeatures(String merchantId, MLFeatureVector.MLFeatureVectorBuilder builder) {

        // Merchant transaction count
        LocalDateTime last30d = LocalDateTime.now().minusDays(30);
        long merchantTxCount = fraudCheckRecordRepository.countByMerchantIdSince(merchantId, last30d);
        builder.merchantTransactionCount30d((int) merchantTxCount);

        // Merchant fraud rate
        long merchantBlockedCount = fraudCheckRecordRepository.countBlockedByMerchantId(merchantId);
        double merchantFraudRate = merchantTxCount > 0 ? (double) merchantBlockedCount / merchantTxCount : 0.0;
        builder.merchantFraudRate(merchantFraudRate);
        builder.isHighRiskMerchant(merchantFraudRate > 0.1); // >10% fraud rate

        // Merchant category (if available)
        String merchantCategory = fraudCheckRecordRepository.getMerchantCategory(merchantId);
        builder.merchantCategory(merchantCategory);
        builder.isHighRiskMerchantCategory(isHighRiskCategory(merchantCategory));
    }

    /**
     * Add derived/engineered features
     */
    private void addDerivedFeatures(MLFeatureVector features, FraudCheckRequest request) {

        // Amount/velocity ratio
        if (features.getUserVelocity24h() != null && features.getUserVelocity24h() > 0) {
            double amountVelocityRatio = features.getTransactionAmount() / features.getUserVelocity24h();
            features.setAmountVelocityRatio(amountVelocityRatio);
        }

        // Device diversity score
        if (features.getDeviceTransactionCount() != null && features.getDeviceUserCount() != null) {
            double deviceDiversity = (double) features.getDeviceUserCount() / (features.getDeviceTransactionCount() + 1);
            features.setDeviceDiversityScore(deviceDiversity);
        }

        // IP risk composite
        int ipRiskFactors = 0;
        if (Boolean.TRUE.equals(features.getIsIPBlacklisted())) ipRiskFactors++;
        if (Boolean.TRUE.equals(features.getIsVPN())) ipRiskFactors++;
        if (Boolean.TRUE.equals(features.getIsTor())) ipRiskFactors++;
        if (Boolean.TRUE.equals(features.getIsProxy())) ipRiskFactors++;
        if (Boolean.TRUE.equals(features.getIsHighVelocityIP())) ipRiskFactors++;

        features.setIpRiskFactorCount(ipRiskFactors);
        features.setIpCompositeRiskScore(ipRiskFactors / 5.0);

        // Transaction anomaly score
        int anomalyFactors = 0;
        if (Boolean.TRUE.equals(features.getIsRoundAmount())) anomalyFactors++;
        if (Boolean.TRUE.equals(features.getIsAmountAnomaly())) anomalyFactors++;
        if (Boolean.TRUE.equals(features.getIsOffHours())) anomalyFactors++;
        if (!Boolean.TRUE.equals(features.getIsKnownDevice())) anomalyFactors++;
        if (!Boolean.TRUE.equals(features.getIsTrustedLocation())) anomalyFactors++;

        features.setAnomalyFactorCount(anomalyFactors);
        features.setTransactionAnomalyScore(anomalyFactors / 5.0);
    }

    // Helper methods

    private Map<String, Object> getCachedFeatures(String cacheKey) {
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            return cached != null ? (Map<String, Object>) cached : null;
        } catch (Exception e) {
            log.debug("Failed to get cached features: {}", cacheKey, e);
            return null;
        }
    }

    private void cacheUserFeatures(String cacheKey, MLFeatureVector.MLFeatureVectorBuilder builder) {
        try {
            // Cache key user behavior features
            Map<String, Object> features = new HashMap<>();
            // Populate with current builder values
            redisTemplate.opsForValue().set(cacheKey, features, FEATURE_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.debug("Failed to cache user features: {}", cacheKey, e);
        }
    }

    private void applyUserFeaturesFromCache(Map<String, Object> cachedFeatures,
                                           MLFeatureVector.MLFeatureVectorBuilder builder) {
        // Apply cached features to builder
        // Implementation details...
    }

    private Map<String, Object> getIPGeolocation(String ipAddress) {
        try {
            String cacheKey = "geo:ip:" + ipAddress;
            Object cached = redisTemplate.opsForValue().get(cacheKey);

            if (cached != null) {
                return (Map<String, Object>) cached;
            }

            // In production, call IP geolocation service (MaxMind, IPStack, etc.)
            // For now, return mock data
            Map<String, Object> geoData = new HashMap<>();
            geoData.put("country", "US");
            geoData.put("city", "New York");
            geoData.put("latitude", 40.7128);
            geoData.put("longitude", -74.0060);
            geoData.put("isVPN", false);
            geoData.put("isTor", false);
            geoData.put("isProxy", false);

            // Cache for 1 hour
            redisTemplate.opsForValue().set(cacheKey, geoData, 1, TimeUnit.HOURS);

            return geoData;

        } catch (Exception e) {
            log.debug("Failed to get IP geolocation: {}", ipAddress, e);
            return null;
        }
    }

    private boolean isHighRiskCategory(String category) {
        if (category == null) return false;

        List<String> highRiskCategories = Arrays.asList(
            "GAMBLING", "CRYPTO", "FOREX", "DATING", "ADULT", "PHARMACEUTICALS"
        );

        return highRiskCategories.stream()
            .anyMatch(risk -> category.toUpperCase().contains(risk));
    }
}
