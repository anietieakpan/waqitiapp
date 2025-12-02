package com.waqiti.analytics.service;

import com.waqiti.analytics.model.*;
import com.waqiti.common.metrics.MetricsPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * Service for aggregating real-time metrics across different dimensions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealTimeMetricsAggregator {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;
    private final MetricsPublisher metricsPublisher;
    
    // Real-time counters
    private final Map<String, LongAdder> metricCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> gaugeValues = new ConcurrentHashMap<>();
    
    // Sliding window aggregations
    private final Map<String, SlidingWindowAggregator> slidingWindows = new ConcurrentHashMap<>();
    
    // Rate calculators
    private final Map<String, RateCalculator> rateCalculators = new ConcurrentHashMap<>();
    
    /**
     * Record a transaction metric
     */
    @Async
    public CompletableFuture<Void> recordTransaction(TransactionEvent event) {
        try {
            // Update counters
            incrementCounter("transactions.total");
            incrementCounter("transactions." + event.getType().toLowerCase());
            incrementCounter("transactions." + event.getStatus().toLowerCase());
            
            // Update amount aggregations
            String amountKey = "transactions.amount." + event.getCurrency();
            updateSlidingWindow(amountKey, event.getAmount());
            
            // Update user-specific metrics
            String userKey = "user." + event.getUserId() + ".transactions";
            incrementCounter(userKey);
            updateRate(userKey + ".rate", 1);
            
            // Update merchant metrics if applicable
            if (event.getMerchantId() != null) {
                String merchantKey = "merchant." + event.getMerchantId() + ".transactions";
                incrementCounter(merchantKey);
                updateSlidingWindow(merchantKey + ".amount", event.getAmount());
            }
            
            // Publish to time-series database
            publishTimeSeries("transaction", event);
            
            // Update dashboards
            updateDashboardMetrics(event);
            
            // Check for alerts
            checkThresholds(event);
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Error recording transaction metric", e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Record a payment metric
     */
    @Async
    public CompletableFuture<Void> recordPayment(PaymentEvent event) {
        try {
            // Update counters
            incrementCounter("payments.total");
            incrementCounter("payments." + event.getPaymentMethod().toLowerCase());
            incrementCounter("payments." + event.getStatus().toLowerCase());
            
            // Update payment method distribution
            updateDistribution("payments.methods", event.getPaymentMethod());
            
            // Update provider metrics
            String providerKey = "provider." + event.getProvider() + ".payments";
            incrementCounter(providerKey);
            
            if (event.getStatus() == PaymentStatus.FAILED) {
                incrementCounter(providerKey + ".failures");
                updateFailureRate(event.getProvider());
            }
            
            // Update processing time metrics
            if (event.getProcessingTime() != null) {
                updateSlidingWindow("payments.processing_time", 
                    BigDecimal.valueOf(event.getProcessingTime()));
            }
            
            // Merchant-specific metrics
            if (event.getMerchantId() != null) {
                updateMerchantPaymentMetrics(event);
            }
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Error recording payment metric", e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Record user activity metric
     */
    @Async
    public CompletableFuture<Void> recordUserActivity(UserActivityEvent event) {
        try {
            // Update active users set
            updateActiveUsers(event.getUserId());
            
            // Update activity counters
            incrementCounter("activity.total");
            incrementCounter("activity." + event.getActivityType().toLowerCase());
            
            // Update session metrics
            updateSessionMetrics(event);
            
            // Update device/platform distribution
            if (event.getDevice() != null) {
                updateDistribution("activity.devices", event.getDevice());
            }
            
            if (event.getPlatform() != null) {
                updateDistribution("activity.platforms", event.getPlatform());
            }
            
            // Update location metrics if available
            if (event.getLocation() != null) {
                updateLocationMetrics(event.getLocation());
            }
            
            // Track user journey
            trackUserJourney(event);
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Error recording user activity metric", e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Record system performance metric
     */
    public void recordPerformanceMetric(String service, String operation, long duration, boolean success) {
        try {
            // Update latency metrics
            String latencyKey = "performance." + service + "." + operation + ".latency";
            updateSlidingWindow(latencyKey, BigDecimal.valueOf(duration));
            
            // Update success/failure counts
            if (success) {
                incrementCounter("performance." + service + ".success");
            } else {
                incrementCounter("performance." + service + ".failure");
            }
            
            // Calculate and update SLA metrics
            updateSlaMetrics(service, operation, duration, success);
            
            // Update percentiles
            updatePercentiles(latencyKey, duration);
            
            // Publish to monitoring system
            meterRegistry.timer("service.latency", 
                    Tags.of("service", service, "operation", operation))
                    .record(duration, TimeUnit.MILLISECONDS);
            
        } catch (Exception e) {
            log.error("Error recording performance metric", e);
        }
    }
    
    /**
     * Get aggregated metrics for a time range
     */
    public AggregatedMetrics getAggregatedMetrics(String metricType, Duration period) {
        AggregatedMetrics metrics = new AggregatedMetrics();
        metrics.setMetricType(metricType);
        metrics.setPeriod(period);
        
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(period);
        
        // Get data from Redis time-series
        String key = "metrics:timeseries:" + metricType;
        Set<Object> dataPoints = redisTemplate.opsForZSet()
                .rangeByScore(key, startTime.toEpochMilli(), endTime.toEpochMilli());
        
        if (dataPoints != null && !dataPoints.isEmpty()) {
            List<MetricDataPoint> points = dataPoints.stream()
                    .map(obj -> parseDataPoint(obj))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            
            metrics.setDataPoints(points);
            metrics.calculateStatistics();
        }
        
        // Add current values
        metrics.setCurrentValue(getCurrentValue(metricType));
        metrics.setCurrentRate(getCurrentRate(metricType));
        
        return metrics;
    }
    
    /**
     * Get real-time dashboard data
     */
    public DashboardData getRealTimeDashboard() {
        DashboardData dashboard = new DashboardData();
        
        // Transaction metrics
        dashboard.setTransactionRate(getCurrentRate("transactions.total"));
        dashboard.setTransactionVolume(getCurrentVolume("transactions"));
        dashboard.setTransactionSuccessRate(calculateSuccessRate("transactions"));
        
        // Payment metrics
        dashboard.setPaymentRate(getCurrentRate("payments.total"));
        dashboard.setPaymentVolume(getCurrentVolume("payments"));
        dashboard.setPaymentSuccessRate(calculateSuccessRate("payments"));
        
        // User metrics
        dashboard.setActiveUsers(getActiveUserCount());
        dashboard.setNewUsers(getCounter("users.new.today"));
        dashboard.setSessionCount(getCounter("sessions.active"));
        
        // System metrics
        dashboard.setSystemHealth(calculateSystemHealth());
        dashboard.setAverageLatency(getAverageLatency());
        dashboard.setErrorRate(calculateErrorRate());
        
        // Top lists
        dashboard.setTopMerchants(getTopMerchants(5));
        dashboard.setTopTransactionTypes(getTopTransactionTypes(5));
        dashboard.setTopPaymentMethods(getTopPaymentMethods(5));
        
        // Alerts
        dashboard.setActiveAlerts(getActiveAlerts());
        
        // Trends
        dashboard.setHourlyTrend(calculateTrend("hourly"));
        dashboard.setDailyTrend(calculateTrend("daily"));
        
        return dashboard;
    }
    
    /**
     * Get metric distribution
     */
    public Map<String, BigDecimal> getMetricDistribution(String metric, int topN) {
        String key = "metrics:distribution:" + metric;
        
        Set<ZSetOperations.TypedTuple<Object>> topItems = redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, 0, topN - 1);
        
        if (topItems == null) {
            return Collections.emptyMap();
        }
        
        Map<String, BigDecimal> distribution = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        
        // Calculate total for percentage
        for (ZSetOperations.TypedTuple<Object> item : topItems) {
            if (item.getScore() != null) {
                total = total.add(BigDecimal.valueOf(item.getScore()));
            }
        }
        
        // Calculate percentages
        for (ZSetOperations.TypedTuple<Object> item : topItems) {
            if (item.getValue() != null && item.getScore() != null) {
                BigDecimal percentage = BigDecimal.valueOf(item.getScore())
                        .divide(total, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                distribution.put(item.getValue().toString(), percentage);
            }
        }
        
        return distribution;
    }
    
    /**
     * Get percentile values for a metric
     */
    public PercentileMetrics getPercentiles(String metric) {
        SlidingWindowAggregator window = slidingWindows.get(metric);
        
        if (window == null) {
            return new PercentileMetrics();
        }
        
        return window.getPercentiles();
    }
    
    // Private helper methods
    
    private void incrementCounter(String key) {
        metricCounters.computeIfAbsent(key, k -> new LongAdder()).increment();
        
        // Also update Redis
        redisTemplate.opsForValue().increment("metrics:counter:" + key);
    }
    
    private void updateSlidingWindow(String key, BigDecimal value) {
        slidingWindows.computeIfAbsent(key, k -> 
                new SlidingWindowAggregator(Duration.ofMinutes(5)))
                .add(value);
    }
    
    private void updateRate(String key, long value) {
        rateCalculators.computeIfAbsent(key, k -> 
                new RateCalculator(Duration.ofMinutes(1)))
                .record(value);
    }
    
    private void updateDistribution(String key, String value) {
        String redisKey = "metrics:distribution:" + key;
        redisTemplate.opsForZSet().incrementScore(redisKey, value, 1);
        redisTemplate.expire(redisKey, Duration.ofHours(24));
    }
    
    private void updateActiveUsers(String userId) {
        String key = "metrics:active_users";
        long now = System.currentTimeMillis();
        
        // Add user with current timestamp as score
        redisTemplate.opsForZSet().add(key, userId, now);
        
        // Remove users inactive for more than 5 minutes
        long cutoff = now - TimeUnit.MINUTES.toMillis(5);
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoff);
    }
    
    private void updateSessionMetrics(UserActivityEvent event) {
        String sessionKey = "metrics:session:" + event.getSessionId();
        
        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("user_id", event.getUserId());
        sessionData.put("last_activity", Instant.now().toString());
        sessionData.put("activity_count", 
                redisTemplate.opsForValue().increment(sessionKey + ":count"));
        
        redisTemplate.opsForHash().putAll(sessionKey, sessionData);
        redisTemplate.expire(sessionKey, Duration.ofMinutes(30));
    }
    
    private void updateLocationMetrics(String location) {
        // Parse location (country, city, etc.)
        String[] parts = location.split(",");
        if (parts.length > 0) {
            String country = parts[0].trim();
            updateDistribution("metrics.locations.countries", country);
            
            if (parts.length > 1) {
                String city = parts[1].trim();
                updateDistribution("metrics.locations.cities", city);
            }
        }
    }
    
    private void trackUserJourney(UserActivityEvent event) {
        String journeyKey = "metrics:journey:" + event.getUserId();
        
        // Add activity to journey
        Map<String, Object> activity = new HashMap<>();
        activity.put("type", event.getActivityType());
        activity.put("timestamp", event.getTimestamp());
        activity.put("page", event.getPage());
        
        redisTemplate.opsForList().rightPush(journeyKey, activity);
        
        // Keep only last 100 activities
        redisTemplate.opsForList().trim(journeyKey, -100, -1);
        redisTemplate.expire(journeyKey, Duration.ofDays(7));
    }
    
    private void updateMerchantPaymentMetrics(PaymentEvent event) {
        String merchantKey = "metrics:merchant:" + event.getMerchantId();
        
        // Update payment count
        incrementCounter(merchantKey + ".payments");
        
        // Update revenue
        String revenueKey = merchantKey + ".revenue." + event.getCurrency();
        BigDecimal currentRevenue = (BigDecimal) redisTemplate.opsForValue().get(revenueKey);
        
        if (currentRevenue == null) {
            currentRevenue = BigDecimal.ZERO;
        }
        
        currentRevenue = currentRevenue.add(event.getAmount());
        redisTemplate.opsForValue().set(revenueKey, currentRevenue, Duration.ofDays(1));
        
        // Update conversion rate
        updateConversionRate(event.getMerchantId());
    }
    
    private void updateConversionRate(String merchantId) {
        String key = "metrics:merchant:" + merchantId;
        
        Long attempts = getCounter(key + ".attempts");
        Long successes = getCounter(key + ".payments");
        
        if (attempts > 0) {
            BigDecimal rate = BigDecimal.valueOf(successes)
                    .divide(BigDecimal.valueOf(attempts), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            
            redisTemplate.opsForValue().set(key + ".conversion_rate", rate, Duration.ofHours(1));
        }
    }
    
    private void updateFailureRate(String provider) {
        String key = "metrics:provider:" + provider;
        
        Long total = getCounter(key + ".payments");
        Long failures = getCounter(key + ".failures");
        
        if (total > 0) {
            BigDecimal rate = BigDecimal.valueOf(failures)
                    .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            
            // Alert if failure rate is too high
            if (rate.compareTo(new BigDecimal("5")) > 0) {
                publishAlert("High failure rate for provider: " + provider + " (" + rate + "%)");
            }
        }
    }
    
    private void updateSlaMetrics(String service, String operation, long duration, boolean success) {
        String key = "metrics:sla:" + service + ":" + operation;
        
        // Update success rate
        incrementCounter(key + ".total");
        if (success) {
            incrementCounter(key + ".success");
        }
        
        // Check SLA thresholds
        if (duration > 1000) { // Over 1 second
            incrementCounter(key + ".sla_breach");
        }
        
        // Calculate availability
        Long total = getCounter(key + ".total");
        Long successful = getCounter(key + ".success");
        
        if (total > 0) {
            BigDecimal availability = BigDecimal.valueOf(successful)
                    .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            
            redisTemplate.opsForValue().set(key + ".availability", availability, Duration.ofHours(1));
        }
    }
    
    private void updatePercentiles(String key, long value) {
        String percentilesKey = "metrics:percentiles:" + key;
        
        // Add value to sorted set
        redisTemplate.opsForZSet().add(percentilesKey, 
                UUID.randomUUID().toString(), value);
        
        // Keep only recent values
        Long size = redisTemplate.opsForZSet().size(percentilesKey);
        if (size != null && size > 10000) {
            redisTemplate.opsForZSet().removeRange(percentilesKey, 0, size - 10000);
        }
        
        // Calculate percentiles
        if (size != null && size > 100) {
            calculateAndStorePercentiles(percentilesKey, size);
        }
    }
    
    private void calculateAndStorePercentiles(String key, long size) {
        // P50
        long p50Index = size / 2;
        Set<Object> p50 = redisTemplate.opsForZSet().range(key, p50Index, p50Index);
        
        // P95
        long p95Index = (long) (size * 0.95);
        Set<Object> p95 = redisTemplate.opsForZSet().range(key, p95Index, p95Index);
        
        // P99
        long p99Index = (long) (size * 0.99);
        Set<Object> p99 = redisTemplate.opsForZSet().range(key, p99Index, p99Index);
        
        // Store percentiles
        Map<String, Object> percentiles = new HashMap<>();
        if (p50 != null && !p50.isEmpty()) {
            percentiles.put("p50", p50.iterator().next());
        }
        if (p95 != null && !p95.isEmpty()) {
            percentiles.put("p95", p95.iterator().next());
        }
        if (p99 != null && !p99.isEmpty()) {
            percentiles.put("p99", p99.iterator().next());
        }
        
        redisTemplate.opsForHash().putAll(key + ":values", percentiles);
    }
    
    private void publishTimeSeries(String type, Object event) {
        String key = "metrics:timeseries:" + type;
        
        Map<String, Object> dataPoint = new HashMap<>();
        dataPoint.put("timestamp", Instant.now().toEpochMilli());
        dataPoint.put("data", event);
        
        redisTemplate.opsForZSet().add(key, dataPoint, System.currentTimeMillis());
        
        // Expire old data
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoff);
    }
    
    private void updateDashboardMetrics(TransactionEvent event) {
        // Update real-time dashboard metrics
        String dashboardKey = "metrics:dashboard:realtime";
        
        redisTemplate.opsForHash().increment(dashboardKey, "transaction_count", 1);
        
        BigDecimal currentVolume = (BigDecimal) redisTemplate.opsForHash()
                .get(dashboardKey, "transaction_volume");
        
        if (currentVolume == null) {
            currentVolume = BigDecimal.ZERO;
        }
        
        currentVolume = currentVolume.add(event.getAmount());
        redisTemplate.opsForHash().put(dashboardKey, "transaction_volume", currentVolume);
        
        redisTemplate.expire(dashboardKey, Duration.ofMinutes(5));
    }
    
    private void checkThresholds(TransactionEvent event) {
        // Check various thresholds
        if (event.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            publishAlert("High-value transaction detected: " + event.getTransactionId());
        }
        
        // Check velocity
        String velocityKey = "metrics:velocity:" + event.getUserId();
        Long count = redisTemplate.opsForValue().increment(velocityKey);
        redisTemplate.expire(velocityKey, Duration.ofMinutes(1));
        
        if (count != null && count > 10) {
            publishAlert("High transaction velocity for user: " + event.getUserId());
        }
    }
    
    private void publishAlert(String message) {
        log.warn("Alert: {}", message);
        
        // Publish to alert channel
        Map<String, Object> alert = new HashMap<>();
        alert.put("message", message);
        alert.put("timestamp", Instant.now());
        alert.put("severity", "MEDIUM");
        
        redisTemplate.convertAndSend("analytics:alerts", alert);
    }
    
    private Long getCounter(String key) {
        LongAdder adder = metricCounters.get(key);
        if (adder != null) {
            return adder.sum();
        }
        
        // Fall back to Redis
        Object value = redisTemplate.opsForValue().get("metrics:counter:" + key);
        return value != null ? Long.parseLong(value.toString()) : 0L;
    }
    
    private BigDecimal getCurrentValue(String metricType) {
        String key = "metrics:current:" + metricType;
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? new BigDecimal(value.toString()) : BigDecimal.ZERO;
    }
    
    private BigDecimal getCurrentRate(String metricType) {
        RateCalculator calculator = rateCalculators.get(metricType);
        return calculator != null ? calculator.getRate() : BigDecimal.ZERO;
    }
    
    private BigDecimal getCurrentVolume(String type) {
        String key = "metrics:volume:" + type;
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? new BigDecimal(value.toString()) : BigDecimal.ZERO;
    }
    
    private BigDecimal calculateSuccessRate(String type) {
        Long total = getCounter(type + ".total");
        Long successful = getCounter(type + ".success") + getCounter(type + ".completed");
        
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        
        return BigDecimal.valueOf(successful)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }
    
    private int getActiveUserCount() {
        String key = "metrics:active_users";
        Long count = redisTemplate.opsForZSet().size(key);
        return count != null ? count.intValue() : 0;
    }
    
    private BigDecimal calculateSystemHealth() {
        // Combine various metrics for system health score
        BigDecimal successRate = calculateSuccessRate("transactions");
        BigDecimal errorRate = calculateErrorRate();
        BigDecimal latency = getAverageLatency();
        
        // Health score calculation (0-100)
        BigDecimal health = new BigDecimal("100");
        
        // Deduct for low success rate
        if (successRate.compareTo(new BigDecimal("95")) < 0) {
            health = health.subtract(new BigDecimal("20"));
        }
        
        // Deduct for high error rate
        if (errorRate.compareTo(new BigDecimal("5")) > 0) {
            health = health.subtract(new BigDecimal("30"));
        }
        
        // Deduct for high latency
        if (latency.compareTo(new BigDecimal("1000")) > 0) {
            health = health.subtract(new BigDecimal("10"));
        }
        
        return health.max(BigDecimal.ZERO);
    }
    
    private BigDecimal getAverageLatency() {
        SlidingWindowAggregator window = slidingWindows.get("performance.latency");
        return window != null ? window.getAverage() : BigDecimal.ZERO;
    }
    
    private BigDecimal calculateErrorRate() {
        Long total = getCounter("performance.total");
        Long errors = getCounter("performance.errors");
        
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        
        return BigDecimal.valueOf(errors)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }
    
    private MetricDataPoint parseDataPoint(Object obj) {
        // Parse data point from Redis storage
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            MetricDataPoint point = new MetricDataPoint();
            point.setTimestamp(Instant.ofEpochMilli((Long) map.get("timestamp")));
            point.setValue(new BigDecimal(map.get("value").toString()));
            return point;
        }
        return null;
    }
}