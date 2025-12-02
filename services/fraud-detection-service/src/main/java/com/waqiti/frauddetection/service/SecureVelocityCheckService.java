package com.waqiti.frauddetection.service;

import com.waqiti.frauddetection.dto.FraudCheckRequest;
import com.waqiti.frauddetection.entity.TransactionVelocity;
import com.waqiti.frauddetection.repository.TransactionVelocityRepository;
import com.waqiti.common.math.MoneyMath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * CRITICAL SECURITY: Thread-Safe Velocity Check Service
 * Prevents race conditions in velocity checking with distributed locking
 * 
 * Security features:
 * - Distributed locking with Redisson
 * - Atomic operations for counters
 * - Sliding window rate limiting
 * - Transaction isolation
 * - Optimistic locking with retry
 * - Circuit breaker pattern
 * - Memory-safe caching
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecureVelocityCheckService {
    
    private final TransactionVelocityRepository velocityRepository;
    private final RedissonClient redissonClient;
    
    @Value("${velocity.check.enabled:true}")
    private boolean velocityCheckEnabled;
    
    @Value("${velocity.short.window.seconds:300}")  // 5 minutes
    private int shortWindowSeconds;
    
    @Value("${velocity.medium.window.seconds:3600}")  // 1 hour
    private int mediumWindowSeconds;
    
    @Value("${velocity.long.window.seconds:86400}")  // 24 hours
    private int longWindowSeconds;
    
    // Thresholds
    @Value("${velocity.short.max.count:5}")
    private int shortMaxCount;
    
    @Value("${velocity.short.max.amount:1000}")
    private BigDecimal shortMaxAmount;
    
    @Value("${velocity.medium.max.count:20}")
    private int mediumMaxCount;
    
    @Value("${velocity.medium.max.amount:5000}")
    private BigDecimal mediumMaxAmount;
    
    @Value("${velocity.long.max.count:100}")
    private int longMaxCount;
    
    @Value("${velocity.long.max.amount:20000}")
    private BigDecimal longMaxAmount;
    
    // Circuit breaker
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    
    // Local cache with TTL
    private final Map<String, CachedVelocityData> localCache = new ConcurrentHashMap<>();
    
    // Read-write lock for cache operations
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();
    
    // Thread pool for async operations
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    
    private static final String VELOCITY_LOCK_PREFIX = "velocity:lock:";
    private static final String VELOCITY_COUNTER_PREFIX = "velocity:counter:";
    private static final String VELOCITY_AMOUNT_PREFIX = "velocity:amount:";
    private static final String VELOCITY_WINDOW_PREFIX = "velocity:window:";
    private static final String VELOCITY_SPIKE_PREFIX = "velocity:spike:";
    
    @PostConstruct
    public void initialize() {
        log.info("Secure Velocity Check Service initialized with distributed locking");
        
        // Schedule cleanup of expired cache entries
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::cleanupExpiredCache, 0, 60, TimeUnit.SECONDS);
    }
    
    /**
     * CRITICAL: Thread-safe velocity check with distributed locking
     */
    @Retryable(value = {ConcurrentModificationException.class}, 
               maxAttempts = 3, 
               backoff = @Backoff(delay = 100, multiplier = 2))
    public VelocityCheckResult checkVelocity(FraudCheckRequest request) {
        if (!velocityCheckEnabled) {
            return VelocityCheckResult.allowed();
        }
        
        String userId = request.getUserId();
        String lockKey = VELOCITY_LOCK_PREFIX + userId;
        
        // Acquire distributed lock
        RLock lock = redissonClient.getFairLock(lockKey);
        
        try {
            // Try to acquire lock with timeout
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                log.warn("Failed to acquire velocity lock for user: {}", userId);
                // Fail-open: allow transaction if can't acquire lock
                return VelocityCheckResult.allowed();
            }
            
            try {
                // Check circuit breaker
                if (isCircuitOpen(userId)) {
                    log.warn("Circuit breaker open for user: {}", userId);
                    return VelocityCheckResult.blocked("Too many recent violations");
                }
                
                // Perform atomic velocity checks
                VelocityData velocityData = getAtomicVelocityData(userId);
                
                // Check all time windows
                VelocityCheckResult shortResult = checkShortWindow(velocityData, request);
                if (!shortResult.isAllowed()) {
                    tripCircuitBreaker(userId);
                    return shortResult;
                }
                
                VelocityCheckResult mediumResult = checkMediumWindow(velocityData, request);
                if (!mediumResult.isAllowed()) {
                    tripCircuitBreaker(userId);
                    return mediumResult;
                }
                
                VelocityCheckResult longResult = checkLongWindow(velocityData, request);
                if (!longResult.isAllowed()) {
                    tripCircuitBreaker(userId);
                    return longResult;
                }
                
                // Check for velocity spikes
                VelocityCheckResult spikeResult = checkVelocitySpike(userId, velocityData);
                if (!spikeResult.isAllowed()) {
                    tripCircuitBreaker(userId);
                    return spikeResult;
                }
                
                // Update velocity counters atomically
                updateVelocityCounters(userId, request);
                
                // Calculate risk score
                double riskScore = calculateVelocityRiskScore(velocityData);
                
                return VelocityCheckResult.allowed(riskScore);
                
            } finally {
                // Always release the lock
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while checking velocity for user: {}", userId);
            return VelocityCheckResult.allowed(); // Fail-open
            
        } catch (Exception e) {
            log.error("Error checking velocity for user: {}", userId, e);
            return VelocityCheckResult.allowed(); // Fail-open
        }
    }
    
    /**
     * Get atomic velocity data using distributed atomic operations
     */
    private VelocityData getAtomicVelocityData(String userId) {
        Instant now = Instant.now();
        
        // Use sliding window for accurate counting
        VelocityData data = new VelocityData();
        
        // Short window
        data.shortWindowCount = getAtomicWindowCount(userId, shortWindowSeconds);
        data.shortWindowAmount = getAtomicWindowAmount(userId, shortWindowSeconds);
        
        // Medium window
        data.mediumWindowCount = getAtomicWindowCount(userId, mediumWindowSeconds);
        data.mediumWindowAmount = getAtomicWindowAmount(userId, mediumWindowSeconds);
        
        // Long window
        data.longWindowCount = getAtomicWindowCount(userId, longWindowSeconds);
        data.longWindowAmount = getAtomicWindowAmount(userId, longWindowSeconds);
        
        return data;
    }
    
    /**
     * Get atomic count for sliding window
     */
    private int getAtomicWindowCount(String userId, int windowSeconds) {
        String windowKey = VELOCITY_WINDOW_PREFIX + userId + ":" + windowSeconds;
        RMap<Long, Integer> windowMap = redissonClient.getMap(windowKey);
        
        long now = System.currentTimeMillis();
        long windowStart = now - (windowSeconds * 1000L);
        
        // Clean old entries and count current window
        int count = 0;
        Iterator<Map.Entry<Long, Integer>> iterator = windowMap.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<Long, Integer> entry = iterator.next();
            if (entry.getKey() < windowStart) {
                iterator.remove(); // Remove expired entries
            } else {
                count += entry.getValue();
            }
        }
        
        return count;
    }
    
    /**
     * Get atomic amount for sliding window
     */
    private BigDecimal getAtomicWindowAmount(String userId, int windowSeconds) {
        String amountKey = VELOCITY_AMOUNT_PREFIX + userId + ":" + windowSeconds;
        RMap<Long, BigDecimal> amountMap = redissonClient.getMap(amountKey);
        
        long now = System.currentTimeMillis();
        long windowStart = now - (windowSeconds * 1000L);
        
        // Clean old entries and sum current window
        BigDecimal total = BigDecimal.ZERO;
        Iterator<Map.Entry<Long, BigDecimal>> iterator = amountMap.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<Long, BigDecimal> entry = iterator.next();
            if (entry.getKey() < windowStart) {
                iterator.remove(); // Remove expired entries
            } else {
                total = total.add(entry.getValue());
            }
        }
        
        return total;
    }
    
    /**
     * Update velocity counters atomically
     */
    private void updateVelocityCounters(String userId, FraudCheckRequest request) {
        long timestamp = System.currentTimeMillis();
        
        // Update count windows
        updateWindowCount(userId, shortWindowSeconds, timestamp);
        updateWindowCount(userId, mediumWindowSeconds, timestamp);
        updateWindowCount(userId, longWindowSeconds, timestamp);
        
        // Update amount windows
        updateWindowAmount(userId, shortWindowSeconds, timestamp, request.getAmount());
        updateWindowAmount(userId, mediumWindowSeconds, timestamp, request.getAmount());
        updateWindowAmount(userId, longWindowSeconds, timestamp, request.getAmount());
        
        // Persist to database asynchronously
        persistVelocityDataAsync(userId, request);
    }
    
    /**
     * Update window count atomically
     */
    private void updateWindowCount(String userId, int windowSeconds, long timestamp) {
        String windowKey = VELOCITY_WINDOW_PREFIX + userId + ":" + windowSeconds;
        RMap<Long, Integer> windowMap = redissonClient.getMap(windowKey);
        
        // Use minute buckets for aggregation
        long bucket = timestamp / 60000 * 60000; // Round to minute
        
        windowMap.merge(bucket, 1, Integer::sum);
        
        // Set TTL on the map
        windowMap.expire(Duration.ofSeconds(windowSeconds + 3600)); // Extra hour for safety
    }
    
    /**
     * Update window amount atomically
     */
    private void updateWindowAmount(String userId, int windowSeconds, long timestamp, BigDecimal amount) {
        String amountKey = VELOCITY_AMOUNT_PREFIX + userId + ":" + windowSeconds;
        RMap<Long, BigDecimal> amountMap = redissonClient.getMap(amountKey);
        
        // Use minute buckets for aggregation
        long bucket = timestamp / 60000 * 60000; // Round to minute
        
        amountMap.merge(bucket, amount, BigDecimal::add);
        
        // Set TTL on the map
        amountMap.expire(Duration.ofSeconds(windowSeconds + 3600)); // Extra hour for safety
    }
    
    /**
     * Check short window velocity
     */
    private VelocityCheckResult checkShortWindow(VelocityData data, FraudCheckRequest request) {
        // Check count
        if (data.shortWindowCount >= shortMaxCount) {
            log.warn("Short window count exceeded for user {}: {} >= {}", 
                request.getUserId(), data.shortWindowCount, shortMaxCount);
            return VelocityCheckResult.blocked("Too many transactions in short window");
        }
        
        // Check amount
        BigDecimal projectedAmount = data.shortWindowAmount.add(request.getAmount());
        if (projectedAmount.compareTo(shortMaxAmount) > 0) {
            log.warn("Short window amount exceeded for user {}: {} > {}", 
                request.getUserId(), projectedAmount, shortMaxAmount);
            return VelocityCheckResult.blocked("Transaction amount too high for short window");
        }
        
        return VelocityCheckResult.allowed();
    }
    
    /**
     * Check medium window velocity
     */
    private VelocityCheckResult checkMediumWindow(VelocityData data, FraudCheckRequest request) {
        // Check count
        if (data.mediumWindowCount >= mediumMaxCount) {
            log.warn("Medium window count exceeded for user {}: {} >= {}", 
                request.getUserId(), data.mediumWindowCount, mediumMaxCount);
            return VelocityCheckResult.blocked("Too many transactions in medium window");
        }
        
        // Check amount
        BigDecimal projectedAmount = data.mediumWindowAmount.add(request.getAmount());
        if (projectedAmount.compareTo(mediumMaxAmount) > 0) {
            log.warn("Medium window amount exceeded for user {}: {} > {}", 
                request.getUserId(), projectedAmount, mediumMaxAmount);
            return VelocityCheckResult.blocked("Transaction amount too high for medium window");
        }
        
        return VelocityCheckResult.allowed();
    }
    
    /**
     * Check long window velocity
     */
    private VelocityCheckResult checkLongWindow(VelocityData data, FraudCheckRequest request) {
        // Check count
        if (data.longWindowCount >= longMaxCount) {
            log.warn("Long window count exceeded for user {}: {} >= {}", 
                request.getUserId(), data.longWindowCount, longMaxCount);
            return VelocityCheckResult.blocked("Daily transaction limit exceeded");
        }
        
        // Check amount
        BigDecimal projectedAmount = data.longWindowAmount.add(request.getAmount());
        if (projectedAmount.compareTo(longMaxAmount) > 0) {
            log.warn("Long window amount exceeded for user {}: {} > {}", 
                request.getUserId(), projectedAmount, longMaxAmount);
            return VelocityCheckResult.blocked("Daily amount limit exceeded");
        }
        
        return VelocityCheckResult.allowed();
    }
    
    /**
     * Check for velocity spikes
     */
    private VelocityCheckResult checkVelocitySpike(String userId, VelocityData currentData) {
        String spikeKey = VELOCITY_SPIKE_PREFIX + userId;
        RMap<String, Double> spikeMap = redissonClient.getMap(spikeKey);
        
        // Get historical average
        Double historicalAvg = spikeMap.get("average");
        if (historicalAvg == null) {
            // Calculate from database and cache
            historicalAvg = calculateHistoricalAverage(userId);
            spikeMap.put("average", historicalAvg);
            spikeMap.expire(Duration.ofHours(1));
        }
        
        if (historicalAvg > 0) {
            double currentRate = currentData.mediumWindowCount;
            double spikeRatio = currentRate / historicalAvg;
            
            if (spikeRatio > 10) {
                log.warn("Extreme velocity spike detected for user {}: {}x normal", userId, spikeRatio);
                return VelocityCheckResult.blocked("Abnormal transaction spike detected");
            }
            
            if (spikeRatio > 5) {
                log.warn("High velocity spike detected for user {}: {}x normal", userId, spikeRatio);
                // Allow but with high risk score
                return VelocityCheckResult.allowed(0.8);
            }
        }
        
        return VelocityCheckResult.allowed();
    }
    
    /**
     * Calculate historical average from database
     */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    private double calculateHistoricalAverage(String userId) {
        try {
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            List<TransactionVelocity> historicalData = 
                velocityRepository.findByUserIdAndTimestampAfter(userId, thirtyDaysAgo);
            
            if (historicalData.isEmpty()) {
                return 0.0;
            }
            
            // Calculate hourly average
            long hours = Duration.between(thirtyDaysAgo, LocalDateTime.now()).toHours();
            return (double) historicalData.size() / Math.max(hours, 1);
            
        } catch (Exception e) {
            log.error("Error calculating historical average for user: {}", userId, e);
            return 0.0;
        }
    }
    
    /**
     * Calculate velocity risk score
     */
    private double calculateVelocityRiskScore(VelocityData data) {
        double score = 0.0;

        // Short window contribution
        double shortCountRatio = (double) data.shortWindowCount / shortMaxCount;
        double shortAmountRatio = (double) MoneyMath.toMLFeature(
            MoneyMath.divide(data.shortWindowAmount, shortMaxAmount));
        score += (shortCountRatio * 0.2 + shortAmountRatio * 0.2);

        // Medium window contribution
        double mediumCountRatio = (double) data.mediumWindowCount / mediumMaxCount;
        double mediumAmountRatio = (double) MoneyMath.toMLFeature(
            MoneyMath.divide(data.mediumWindowAmount, mediumMaxAmount));
        score += (mediumCountRatio * 0.15 + mediumAmountRatio * 0.15);

        // Long window contribution
        double longCountRatio = (double) data.longWindowCount / longMaxCount;
        double longAmountRatio = (double) MoneyMath.toMLFeature(
            MoneyMath.divide(data.longWindowAmount, longMaxAmount));
        score += (longCountRatio * 0.1 + longAmountRatio * 0.1);

        return Math.min(score, 1.0);
    }
    
    /**
     * Persist velocity data asynchronously
     */
    private void persistVelocityDataAsync(String userId, FraudCheckRequest request) {
        CompletableFuture.runAsync(() -> {
            try {
                TransactionVelocity velocity = TransactionVelocity.builder()
                    .userId(userId)
                    .transactionId(request.getTransactionId())
                    .amount(request.getAmount())
                    .timestamp(LocalDateTime.now())
                    .deviceId(request.getDeviceId())
                    .ipAddress(request.getIpAddress())
                    .build();
                
                velocityRepository.save(velocity);
                
            } catch (Exception e) {
                log.error("Error persisting velocity data for user: {}", userId, e);
            }
        }, executorService);
    }
    
    /**
     * Circuit breaker implementation
     */
    private boolean isCircuitOpen(String userId) {
        CircuitBreaker breaker = circuitBreakers.computeIfAbsent(userId, 
            k -> new CircuitBreaker(5, 60000)); // 5 failures in 1 minute
        
        return breaker.isOpen();
    }
    
    private void tripCircuitBreaker(String userId) {
        CircuitBreaker breaker = circuitBreakers.computeIfAbsent(userId, 
            k -> new CircuitBreaker(5, 60000));
        
        breaker.recordFailure();
    }
    
    /**
     * Clean up expired cache entries
     */
    @Scheduled(fixedDelay = 60000) // Every minute
    private void cleanupExpiredCache() {
        cacheLock.writeLock().lock();
        try {
            long now = System.currentTimeMillis();
            localCache.entrySet().removeIf(entry -> 
                entry.getValue().expiryTime < now);
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * Reset user velocity (admin function)
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void resetUserVelocity(String userId) {
        String lockKey = VELOCITY_LOCK_PREFIX + userId;
        RLock lock = redissonClient.getFairLock(lockKey);
        
        try {
            if (lock.tryLock(10, TimeUnit.SECONDS)) {
                try {
                    // Clear all Redis data for user
                    String[] patterns = {
                        VELOCITY_WINDOW_PREFIX + userId + ":*",
                        VELOCITY_AMOUNT_PREFIX + userId + ":*",
                        VELOCITY_SPIKE_PREFIX + userId
                    };
                    
                    for (String pattern : patterns) {
                        redissonClient.getKeys().deleteByPattern(pattern);
                    }
                    
                    // Clear circuit breaker
                    circuitBreakers.remove(userId);
                    
                    // Clear local cache
                    cacheLock.writeLock().lock();
                    try {
                        localCache.entrySet().removeIf(entry -> 
                            entry.getKey().contains(userId));
                    } finally {
                        cacheLock.writeLock().unlock();
                    }
                    
                    log.info("Reset velocity tracking for user: {}", userId);
                    
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error resetting velocity for user: {}", userId, e);
        }
    }
    
    /**
     * Velocity data holder
     */
    private static class VelocityData {
        int shortWindowCount = 0;
        BigDecimal shortWindowAmount = BigDecimal.ZERO;
        int mediumWindowCount = 0;
        BigDecimal mediumWindowAmount = BigDecimal.ZERO;
        int longWindowCount = 0;
        BigDecimal longWindowAmount = BigDecimal.ZERO;
    }
    
    /**
     * Cached velocity data with TTL
     */
    private static class CachedVelocityData {
        final VelocityData data;
        final long expiryTime;
        
        CachedVelocityData(VelocityData data, long ttlMillis) {
            this.data = data;
            this.expiryTime = System.currentTimeMillis() + ttlMillis;
        }
    }
    
    /**
     * Circuit breaker implementation
     */
    private static class CircuitBreaker {
        private final int threshold;
        private final long windowMillis;
        private final Queue<Long> failures = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean open = new AtomicBoolean(false);
        private final AtomicReference<Long> openTime = new AtomicReference<>(0L);
        private final long cooldownMillis = 30000; // 30 seconds
        
        CircuitBreaker(int threshold, long windowMillis) {
            this.threshold = threshold;
            this.windowMillis = windowMillis;
        }
        
        boolean isOpen() {
            if (!open.get()) {
                return false;
            }
            
            // Check if cooldown period has passed
            long now = System.currentTimeMillis();
            if (now - openTime.get() > cooldownMillis) {
                // Try to close the circuit
                open.set(false);
                failures.clear();
                return false;
            }
            
            return true;
        }
        
        void recordFailure() {
            long now = System.currentTimeMillis();
            
            // Remove old failures outside window
            failures.removeIf(time -> now - time > windowMillis);
            
            // Add new failure
            failures.offer(now);
            
            // Check if threshold exceeded
            if (failures.size() >= threshold) {
                open.set(true);
                openTime.set(now);
                log.warn("Circuit breaker opened due to {} failures", failures.size());
            }
        }
    }
    
    /**
     * Velocity check result
     */
    public static class VelocityCheckResult {
        private final boolean allowed;
        private final String reason;
        private final double riskScore;
        
        private VelocityCheckResult(boolean allowed, String reason, double riskScore) {
            this.allowed = allowed;
            this.reason = reason;
            this.riskScore = riskScore;
        }
        
        public static VelocityCheckResult allowed() {
            return new VelocityCheckResult(true, null, 0.0);
        }
        
        public static VelocityCheckResult allowed(double riskScore) {
            return new VelocityCheckResult(true, null, riskScore);
        }
        
        public static VelocityCheckResult blocked(String reason) {
            return new VelocityCheckResult(false, reason, 1.0);
        }
        
        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }
        public double getRiskScore() { return riskScore; }
    }
}