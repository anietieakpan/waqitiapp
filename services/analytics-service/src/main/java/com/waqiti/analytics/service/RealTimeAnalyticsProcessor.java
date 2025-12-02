package com.waqiti.analytics.service;

import com.waqiti.analytics.model.*;
import com.waqiti.analytics.repository.AnalyticsRepository;
import com.waqiti.common.kafka.KafkaTopics;
import com.waqiti.common.redis.RedisKeyGenerator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Real-time Analytics Processing Service
 * Processes streaming data for real-time insights and metrics
 */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableKafkaStreams
public class RealTimeAnalyticsProcessor {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AnalyticsRepository analyticsRepository;
    private final MeterRegistry meterRegistry;
    private final StreamsBuilderFactoryBean streamsBuilderFactory;
    
    @Value("${analytics.window.size.minutes:5}")
    private int windowSizeMinutes;
    
    @Value("${analytics.aggregation.interval.seconds:10}")
    private int aggregationIntervalSeconds;
    
    @Value("${analytics.retention.days:90}")
    private int retentionDays;
    
    private Counter transactionCounter;
    private Counter paymentCounter;
    private Timer processingTimer;
    
    // In-memory caches for hot data
    private final ConcurrentHashMap<String, TransactionMetrics> realtimeMetrics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UserActivityMetrics> userActivityCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MerchantMetrics> merchantMetricsCache = new ConcurrentHashMap<>();
    
    // Scheduled executor for periodic aggregations
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    
    @PostConstruct
    public void initialize() {
        // Initialize metrics
        this.transactionCounter = Counter.builder("analytics.transactions.processed")
                .description("Number of transactions processed")
                .register(meterRegistry);
                
        this.paymentCounter = Counter.builder("analytics.payments.processed")
                .description("Number of payments processed")
                .register(meterRegistry);
                
        this.processingTimer = Timer.builder("analytics.processing.time")
                .description("Analytics processing time")
                .register(meterRegistry);
        
        // Start scheduled tasks
        startScheduledAggregations();
        
        // Configure Kafka Streams topology
        configureStreamsTopology();
        
        log.info("Real-time analytics processor initialized with window size: {} minutes", windowSizeMinutes);
    }
    
    /**
     * Configure Kafka Streams topology for real-time processing
     */
    private void configureStreamsTopology() {
        // Transaction stream processing
        KStream<String, TransactionEvent> transactionStream = streamsBuilderFactory.getObject()
                .stream(KafkaTopics.TRANSACTION_EVENTS);
        
        // Payment stream processing
        KStream<String, PaymentEvent> paymentStream = streamsBuilderFactory.getObject()
                .stream(KafkaTopics.PAYMENT_EVENTS);
        
        // User activity stream
        KStream<String, UserActivityEvent> activityStream = streamsBuilderFactory.getObject()
                .stream(KafkaTopics.USER_ACTIVITY);
        
        // Process transaction stream
        processTransactionStream(transactionStream);
        
        // Process payment stream
        processPaymentStream(paymentStream);
        
        // Process user activity stream
        processUserActivityStream(activityStream);
        
        // Join streams for cross-analytics
        performStreamJoins(transactionStream, paymentStream, activityStream);
    }
    
    /**
     * Process real-time transaction stream
     */
    private void processTransactionStream(KStream<String, TransactionEvent> stream) {
        // Window for aggregation
        TimeWindows timeWindow = TimeWindows.of(Duration.ofMinutes(windowSizeMinutes))
                .grace(Duration.ofMinutes(1));
        
        // Group by user and aggregate
        stream.groupByKey()
                .windowedBy(timeWindow)
                .aggregate(
                        TransactionAggregate::new,
                        (key, value, aggregate) -> {
                            aggregate.addTransaction(value);
                            return aggregate;
                        },
                        Materialized.with(Serdes.String(), new TransactionAggregateSerde())
                )
                .toStream()
                .foreach((windowedKey, aggregate) -> {
                    String userId = windowedKey.key();
                    Instant windowStart = windowedKey.window().startTime();
                    
                    // Update real-time metrics
                    updateTransactionMetrics(userId, aggregate, windowStart);
                    
                    // Push to Redis for real-time access
                    pushToRedis(userId, aggregate, windowStart);
                    
                    // Increment counter
                    transactionCounter.increment(aggregate.getCount());
                });
        
        // Detect anomalies in real-time
        stream.foreach((key, transaction) -> {
            detectTransactionAnomalies(transaction);
        });
        
        // Calculate moving averages
        stream.groupByKey()
                .windowedBy(SlidingWindows.withTimeDifferenceAndGrace(
                        Duration.ofMinutes(15), 
                        Duration.ofMinutes(1)
                ))
                .aggregate(
                        MovingAverage::new,
                        (key, value, average) -> {
                            average.add(value.getAmount());
                            return average;
                        },
                        Materialized.with(Serdes.String(), new MovingAverageSerde())
                )
                .toStream()
                .foreach((windowedKey, average) -> {
                    updateMovingAverages(windowedKey.key(), average);
                });
    }
    
    /**
     * Process real-time payment stream
     */
    private void processPaymentStream(KStream<String, PaymentEvent> stream) {
        // Group by merchant for merchant analytics
        stream.selectKey((key, value) -> value.getMerchantId())
                .groupByKey()
                .windowedBy(TimeWindows.of(Duration.ofMinutes(windowSizeMinutes)))
                .aggregate(
                        MerchantAggregate::new,
                        (merchantId, payment, aggregate) -> {
                            aggregate.addPayment(payment);
                            return aggregate;
                        },
                        Materialized.with(Serdes.String(), new MerchantAggregateSerde())
                )
                .toStream()
                .foreach((windowedKey, aggregate) -> {
                    updateMerchantMetrics(windowedKey.key(), aggregate);
                    paymentCounter.increment(aggregate.getPaymentCount());
                });
        
        // Payment method distribution
        stream.groupBy((key, value) -> value.getPaymentMethod())
                .windowedBy(SessionWindows.with(Duration.ofMinutes(30)))
                .count()
                .toStream()
                .foreach((windowedKey, count) -> {
                    updatePaymentMethodDistribution(windowedKey.key(), count);
                });
        
        // Failed payment analysis
        stream.filter((key, payment) -> payment.getStatus() == PaymentStatus.FAILED)
                .groupByKey()
                .windowedBy(TimeWindows.of(Duration.ofHours(1)))
                .count()
                .toStream()
                .foreach((windowedKey, count) -> {
                    if (count > 5) {
                        triggerFailedPaymentAlert(windowedKey.key(), count);
                    }
                });
    }
    
    /**
     * Process user activity stream
     */
    private void processUserActivityStream(KStream<String, UserActivityEvent> stream) {
        // Session detection
        stream.groupByKey()
                .windowedBy(SessionWindows.with(Duration.ofMinutes(30)))
                .aggregate(
                        UserSession::new,
                        (userId, activity, session) -> {
                            session.addActivity(activity);
                            return session;
                        },
                        (key, session1, session2) -> session1.merge(session2),
                        Materialized.with(Serdes.String(), new UserSessionSerde())
                )
                .toStream()
                .foreach((windowedKey, session) -> {
                    updateUserSessionMetrics(windowedKey.key(), session);
                });
        
        // Real-time active user count
        stream.groupBy((key, value) -> "global")
                .windowedBy(TimeWindows.of(Duration.ofMinutes(1)))
                .aggregate(
                        HashSet::new,
                        (key, activity, userSet) -> {
                            userSet.add(activity.getUserId());
                            return userSet;
                        },
                        Materialized.with(Serdes.String(), new HashSetSerde())
                )
                .toStream()
                .foreach((windowedKey, userSet) -> {
                    updateActiveUserCount(userSet.size());
                });
    }
    
    /**
     * Perform stream joins for cross-analytics
     */
    private void performStreamJoins(
            KStream<String, TransactionEvent> transactionStream,
            KStream<String, PaymentEvent> paymentStream,
            KStream<String, UserActivityEvent> activityStream) {
        
        // Join transactions with user activity for behavior analysis
        JoinWindows joinWindow = JoinWindows.of(Duration.ofMinutes(5));
        
        transactionStream.join(
                activityStream,
                (transaction, activity) -> new UserTransactionBehavior(transaction, activity),
                joinWindow,
                StreamJoined.with(Serdes.String(), new TransactionEventSerde(), new UserActivityEventSerde())
        )
        .foreach((key, behavior) -> {
            analyzeBehaviorPatterns(behavior);
        });
        
        // Join payments with transactions for conversion analysis
        paymentStream.join(
                transactionStream,
                (payment, transaction) -> new PaymentConversion(payment, transaction),
                joinWindow,
                StreamJoined.with(Serdes.String(), new PaymentEventSerde(), new TransactionEventSerde())
        )
        .foreach((key, conversion) -> {
            updateConversionMetrics(conversion);
        });
    }
    
    /**
     * Update real-time transaction metrics
     */
    private void updateTransactionMetrics(String userId, TransactionAggregate aggregate, Instant windowStart) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            TransactionMetrics metrics = realtimeMetrics.computeIfAbsent(userId, 
                    k -> new TransactionMetrics(userId));
            
            metrics.updateFromAggregate(aggregate, windowStart);
            
            // Calculate percentiles
            metrics.calculatePercentiles();
            
            // Update Redis with latest metrics
            String key = RedisKeyGenerator.generateKey("analytics:transaction:metrics", userId);
            redisTemplate.opsForValue().set(key, metrics, Duration.ofMinutes(windowSizeMinutes * 2));
            
            // Update time-series data
            updateTimeSeries(userId, metrics, windowStart);
            
            // Check for threshold alerts
            checkMetricThresholds(userId, metrics);
            
        } finally {
            sample.stop(processingTimer);
        }
    }
    
    /**
     * Update merchant metrics
     */
    private void updateMerchantMetrics(String merchantId, MerchantAggregate aggregate) {
        MerchantMetrics metrics = merchantMetricsCache.computeIfAbsent(merchantId,
                k -> new MerchantMetrics(merchantId));
        
        metrics.updateFromAggregate(aggregate);
        
        // Calculate conversion rates
        BigDecimal conversionRate = calculateConversionRate(aggregate);
        metrics.setConversionRate(conversionRate);
        
        // Calculate average transaction value
        BigDecimal atv = aggregate.getTotalAmount()
                .divide(BigDecimal.valueOf(aggregate.getPaymentCount()), 2, RoundingMode.HALF_UP);
        metrics.setAverageTransactionValue(atv);
        
        // Push to Redis
        String key = RedisKeyGenerator.generateKey("analytics:merchant:metrics", merchantId);
        redisTemplate.opsForValue().set(key, metrics, Duration.ofHours(1));
        
        // Update leaderboard
        updateMerchantLeaderboard(merchantId, aggregate.getTotalAmount());
    }
    
    /**
     * Detect transaction anomalies in real-time
     */
    private void detectTransactionAnomalies(TransactionEvent transaction) {
        String userId = transaction.getUserId();
        
        // Get user's historical pattern
        UserTransactionPattern pattern = getUserTransactionPattern(userId);
        
        // Check for anomalies
        List<AnomalyType> anomalies = new ArrayList<>();
        
        // Amount anomaly
        if (isAmountAnomaly(transaction.getAmount(), pattern)) {
            anomalies.add(AnomalyType.UNUSUAL_AMOUNT);
        }
        
        // Time anomaly
        if (isTimeAnomaly(transaction.getTimestamp(), pattern)) {
            anomalies.add(AnomalyType.UNUSUAL_TIME);
        }
        
        // Frequency anomaly
        if (isFrequencyAnomaly(userId, pattern)) {
            anomalies.add(AnomalyType.HIGH_FREQUENCY);
        }
        
        // Location anomaly (if available)
        if (transaction.getLocation() != null && isLocationAnomaly(transaction.getLocation(), pattern)) {
            anomalies.add(AnomalyType.UNUSUAL_LOCATION);
        }
        
        if (!anomalies.isEmpty()) {
            publishAnomalyAlert(userId, transaction, anomalies);
        }
    }
    
    /**
     * Update moving averages
     */
    private void updateMovingAverages(String userId, MovingAverage average) {
        String key = RedisKeyGenerator.generateKey("analytics:moving:average", userId);
        
        // Store multiple time windows
        redisTemplate.opsForHash().put(key, "15min", average.getAverage());
        redisTemplate.opsForHash().put(key, "15min_std", average.getStandardDeviation());
        redisTemplate.expire(key, Duration.ofHours(1));
        
        // Update trend detection
        detectTrends(userId, average);
    }
    
    /**
     * Update payment method distribution
     */
    private void updatePaymentMethodDistribution(String paymentMethod, Long count) {
        String key = "analytics:payment:distribution:" + LocalDate.now();
        redisTemplate.opsForHash().increment(key, paymentMethod, count);
        redisTemplate.expire(key, Duration.ofDays(7));
    }
    
    /**
     * Update user session metrics
     */
    private void updateUserSessionMetrics(String userId, UserSession session) {
        UserActivityMetrics metrics = userActivityCache.computeIfAbsent(userId,
                k -> new UserActivityMetrics(userId));
        
        metrics.addSession(session);
        
        // Calculate engagement score
        BigDecimal engagementScore = calculateEngagementScore(session);
        metrics.setEngagementScore(engagementScore);
        
        // Store in Redis
        String key = RedisKeyGenerator.generateKey("analytics:user:activity", userId);
        redisTemplate.opsForValue().set(key, metrics, Duration.ofHours(24));
        
        // Update cohort analysis
        updateCohortAnalysis(userId, metrics);
    }
    
    /**
     * Update active user count
     */
    private void updateActiveUserCount(int count) {
        String timestamp = Instant.now().toString();
        String key = "analytics:active:users";
        
        // Add to sorted set with timestamp as score
        redisTemplate.opsForZSet().add(key, count, System.currentTimeMillis());
        
        // Trim old entries
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoff);
        
        // Publish metric
        meterRegistry.gauge("analytics.users.active", count);
    }
    
    /**
     * Analyze behavior patterns
     */
    private void analyzeBehaviorPatterns(UserTransactionBehavior behavior) {
        // Detect patterns like:
        // - Transaction immediately after login
        // - Multiple transactions in short time
        // - Transaction after long inactivity
        
        String pattern = detectPattern(behavior);
        if (pattern != null) {
            String key = "analytics:behavior:patterns:" + LocalDate.now();
            redisTemplate.opsForHash().increment(key, pattern, 1);
            redisTemplate.expire(key, Duration.ofDays(30));
        }
    }
    
    /**
     * Update conversion metrics
     */
    private void updateConversionMetrics(PaymentConversion conversion) {
        String key = "analytics:conversion:metrics:" + LocalDate.now();
        
        // Calculate conversion time
        long conversionTime = Duration.between(
                conversion.getTransaction().getTimestamp(),
                conversion.getPayment().getTimestamp()
        ).toSeconds();
        
        // Update metrics
        redisTemplate.opsForHash().increment(key, "total_conversions", 1);
        redisTemplate.opsForHash().increment(key, "total_time", conversionTime);
        
        // Calculate average
        Long totalConversions = (Long) redisTemplate.opsForHash().get(key, "total_conversions");
        Long totalTime = (Long) redisTemplate.opsForHash().get(key, "total_time");
        
        if (totalConversions != null && totalTime != null && totalConversions > 0) {
            long avgTime = totalTime / totalConversions;
            redisTemplate.opsForHash().put(key, "avg_conversion_time", avgTime);
        }
        
        redisTemplate.expire(key, Duration.ofDays(7));
    }
    
    /**
     * Start scheduled aggregation tasks
     */
    private void startScheduledAggregations() {
        // Minute-level aggregations
        scheduler.scheduleAtFixedRate(
                this::performMinuteAggregations,
                60,
                60,
                TimeUnit.SECONDS
        );
        
        // Hourly aggregations
        scheduler.scheduleAtFixedRate(
                this::performHourlyAggregations,
                3600,
                3600,
                TimeUnit.SECONDS
        );
        
        // Daily aggregations
        scheduler.scheduleAtFixedRate(
                this::performDailyAggregations,
                calculateInitialDelay(),
                TimeUnit.DAYS.toSeconds(1),
                TimeUnit.SECONDS
        );
        
        // Cleanup old data
        scheduler.scheduleAtFixedRate(
                this::cleanupOldData,
                TimeUnit.HOURS.toSeconds(1),
                TimeUnit.HOURS.toSeconds(6),
                TimeUnit.SECONDS
        );
    }
    
    /**
     * Perform minute-level aggregations
     */
    private void performMinuteAggregations() {
        try {
            Instant now = Instant.now();
            Instant minuteAgo = now.minus(Duration.ofMinutes(1));
            
            // Aggregate transaction volume
            BigDecimal transactionVolume = calculateTransactionVolume(minuteAgo, now);
            storeMetric("transaction_volume_1m", transactionVolume, now);
            
            // Calculate average response time
            Double avgResponseTime = calculateAverageResponseTime(minuteAgo, now);
            storeMetric("avg_response_time_1m", avgResponseTime, now);
            
            // Count unique users
            Long uniqueUsers = countUniqueUsers(minuteAgo, now);
            storeMetric("unique_users_1m", uniqueUsers, now);
            
            // Calculate success rate
            BigDecimal successRate = calculateSuccessRate(minuteAgo, now);
            storeMetric("success_rate_1m", successRate, now);
            
            log.debug("Completed minute aggregations at {}", now);
            
        } catch (Exception e) {
            log.error("Error performing minute aggregations", e);
        }
    }
    
    /**
     * Perform hourly aggregations
     */
    private void performHourlyAggregations() {
        try {
            Instant now = Instant.now();
            Instant hourAgo = now.minus(Duration.ofHours(1));
            
            // Generate hourly reports
            HourlyReport report = generateHourlyReport(hourAgo, now);
            
            // Store in database
            analyticsRepository.saveHourlyReport(report);
            
            // Update dashboards
            updateDashboards(report);
            
            // Check for alerts
            checkHourlyAlerts(report);
            
            log.info("Completed hourly aggregations: {}", report.getSummary());
            
        } catch (Exception e) {
            log.error("Error performing hourly aggregations", e);
        }
    }
    
    /**
     * Perform daily aggregations
     */
    private void performDailyAggregations() {
        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            
            // Generate daily report
            DailyReport report = generateDailyReport(yesterday);
            
            // Store in database
            analyticsRepository.saveDailyReport(report);
            
            // Generate insights
            List<Insight> insights = generateDailyInsights(report);
            analyticsRepository.saveInsights(insights);
            
            // Update long-term trends
            updateLongTermTrends(report);
            
            // Send daily summary
            sendDailySummary(report, insights);
            
            log.info("Completed daily aggregations for {}", yesterday);
            
        } catch (Exception e) {
            log.error("Error performing daily aggregations", e);
        }
    }
    
    /**
     * Push metrics to Redis for real-time access
     */
    private void pushToRedis(String userId, TransactionAggregate aggregate, Instant windowStart) {
        String key = RedisKeyGenerator.generateKey("analytics:realtime", userId);
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("count", aggregate.getCount());
        metrics.put("total", aggregate.getTotalAmount());
        metrics.put("average", aggregate.getAverageAmount());
        metrics.put("max", aggregate.getMaxAmount());
        metrics.put("min", aggregate.getMinAmount());
        metrics.put("window_start", windowStart.toString());
        metrics.put("updated_at", Instant.now().toString());
        
        redisTemplate.opsForHash().putAll(key, metrics);
        redisTemplate.expire(key, Duration.ofMinutes(windowSizeMinutes * 3));
    }
    
    /**
     * Update time-series data
     */
    private void updateTimeSeries(String userId, TransactionMetrics metrics, Instant timestamp) {
        String key = RedisKeyGenerator.generateKey("analytics:timeseries", userId, 
                LocalDate.now().toString());
        
        // Store as sorted set with timestamp as score
        String value = serializeMetrics(metrics);
        redisTemplate.opsForZSet().add(key, value, timestamp.toEpochMilli());
        
        // Expire after retention period
        redisTemplate.expire(key, Duration.ofDays(retentionDays));
    }
    
    /**
     * Update merchant leaderboard
     */
    private void updateMerchantLeaderboard(String merchantId, BigDecimal amount) {
        String key = "analytics:leaderboard:merchants:" + LocalDate.now();
        redisTemplate.opsForZSet().incrementScore(key, merchantId, amount.doubleValue());
        redisTemplate.expire(key, Duration.ofDays(7));
    }
    
    /**
     * Clean up old data
     */
    private void cleanupOldData() {
        try {
            Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
            
            // Clean up old analytics data
            analyticsRepository.deleteOldData(cutoff);
            
            // Clean up Redis keys
            Set<String> oldKeys = redisTemplate.keys("analytics:*");
            if (oldKeys != null) {
                for (String key : oldKeys) {
                    Long ttl = redisTemplate.getExpire(key);
                    if (ttl == null || ttl == -1) {
                        // Key doesn't have TTL, check if it's old
                        if (isOldKey(key, cutoff)) {
                            redisTemplate.delete(key);
                        }
                    }
                }
            }
            
            // Clean up in-memory caches
            realtimeMetrics.entrySet().removeIf(entry -> 
                    entry.getValue().getLastUpdated().isBefore(cutoff));
            userActivityCache.entrySet().removeIf(entry -> 
                    entry.getValue().getLastActivity().isBefore(cutoff));
            merchantMetricsCache.entrySet().removeIf(entry -> 
                    entry.getValue().getLastUpdated().isBefore(cutoff));
            
            log.info("Cleaned up analytics data older than {}", cutoff);
            
        } catch (Exception e) {
            log.error("Error cleaning up old data", e);
        }
    }
    
    /**
     * Get real-time metrics for a user
     */
    public TransactionMetrics getRealTimeMetrics(String userId) {
        // Try cache first
        TransactionMetrics cached = realtimeMetrics.get(userId);
        if (cached != null && !cached.isStale()) {
            return cached;
        }
        
        // Try Redis
        String key = RedisKeyGenerator.generateKey("analytics:transaction:metrics", userId);
        TransactionMetrics metrics = (TransactionMetrics) redisTemplate.opsForValue().get(key);
        
        if (metrics != null) {
            // Update cache
            realtimeMetrics.put(userId, metrics);
            return metrics;
        }
        
        // Return empty metrics
        return new TransactionMetrics(userId);
    }
    
    /**
     * Get merchant analytics
     */
    public MerchantAnalytics getMerchantAnalytics(String merchantId, Duration period) {
        MerchantAnalytics analytics = new MerchantAnalytics(merchantId);
        
        // Get real-time metrics
        MerchantMetrics metrics = merchantMetricsCache.get(merchantId);
        if (metrics != null) {
            analytics.setCurrentMetrics(metrics);
        }
        
        // Get historical data
        Instant startTime = Instant.now().minus(period);
        List<MerchantAggregate> historical = analyticsRepository.getMerchantHistory(merchantId, startTime);
        analytics.setHistoricalData(historical);
        
        // Calculate trends
        analytics.calculateTrends();
        
        // Get position in leaderboard
        String leaderboardKey = "analytics:leaderboard:merchants:" + LocalDate.now();
        Long rank = redisTemplate.opsForZSet().reverseRank(leaderboardKey, merchantId);
        analytics.setLeaderboardPosition(rank != null ? rank.intValue() + 1 : null);
        
        return analytics;
    }
    
    /**
     * Get system-wide analytics dashboard
     */
    public SystemDashboard getSystemDashboard() {
        SystemDashboard dashboard = new SystemDashboard();
        
        // Real-time metrics
        dashboard.setActiveUsers(getActiveUserCount());
        dashboard.setTransactionsPerSecond(getTransactionRate());
        dashboard.setCurrentVolume(getCurrentVolume());
        
        // Aggregated metrics
        dashboard.setDailyVolume(getDailyVolume());
        dashboard.setWeeklyVolume(getWeeklyVolume());
        dashboard.setMonthlyVolume(getMonthlyVolume());
        
        // Success rates
        dashboard.setTransactionSuccessRate(getSuccessRate("transaction"));
        dashboard.setPaymentSuccessRate(getSuccessRate("payment"));
        
        // Top metrics
        dashboard.setTopMerchants(getTopMerchants(10));
        dashboard.setTopUsers(getTopUsers(10));
        dashboard.setTopPaymentMethods(getTopPaymentMethods());
        
        // Trends
        dashboard.setHourlyTrend(getHourlyTrend());
        dashboard.setDailyTrend(getDailyTrend());
        
        // Alerts
        dashboard.setActiveAlerts(getActiveAlerts());
        
        return dashboard;
    }
    
    // Helper methods
    
    private boolean isAmountAnomaly(BigDecimal amount, UserTransactionPattern pattern) {
        if (pattern.getAverageAmount() == null) return false;
        
        BigDecimal deviation = amount.subtract(pattern.getAverageAmount())
                .abs()
                .divide(pattern.getStandardDeviation(), 2, RoundingMode.HALF_UP);
        
        return deviation.compareTo(new BigDecimal("3")) > 0; // 3 standard deviations
    }
    
    private boolean isTimeAnomaly(Instant timestamp, UserTransactionPattern pattern) {
        int hour = LocalDateTime.ofInstant(timestamp, ZoneOffset.UTC).getHour();
        return !pattern.getTypicalHours().contains(hour);
    }
    
    private boolean isFrequencyAnomaly(String userId, UserTransactionPattern pattern) {
        String key = RedisKeyGenerator.generateKey("analytics:frequency", userId);
        Long recentCount = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofMinutes(5));
        
        return recentCount != null && recentCount > pattern.getMaxFrequencyPer5Min();
    }
    
    private boolean isLocationAnomaly(String location, UserTransactionPattern pattern) {
        return !pattern.getKnownLocations().contains(location);
    }
    
    private void publishAnomalyAlert(String userId, TransactionEvent transaction, List<AnomalyType> anomalies) {
        AnomalyAlert alert = new AnomalyAlert();
        alert.setUserId(userId);
        alert.setTransactionId(transaction.getTransactionId());
        alert.setAnomalyTypes(anomalies);
        alert.setTimestamp(Instant.now());
        alert.setSeverity(calculateSeverity(anomalies));
        
        // Publish to fraud detection service
        redisTemplate.convertAndSend("fraud:anomaly:alerts", alert);
        
        // Store for analysis
        String key = "analytics:anomalies:" + LocalDate.now();
        redisTemplate.opsForList().rightPush(key, alert);
        redisTemplate.expire(key, Duration.ofDays(30));
    }
    
    private BigDecimal calculateConversionRate(MerchantAggregate aggregate) {
        if (aggregate.getTotalAttempts() == 0) return BigDecimal.ZERO;
        
        return BigDecimal.valueOf(aggregate.getSuccessfulPayments())
                .divide(BigDecimal.valueOf(aggregate.getTotalAttempts()), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }
    
    private BigDecimal calculateEngagementScore(UserSession session) {
        // Factors: session duration, number of actions, variety of actions
        BigDecimal durationScore = BigDecimal.valueOf(session.getDurationMinutes())
                .divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP); // Normalize to 30 min
        
        BigDecimal actionScore = BigDecimal.valueOf(session.getActionCount())
                .divide(new BigDecimal("20"), 2, RoundingMode.HALF_UP); // Normalize to 20 actions
        
        BigDecimal varietyScore = BigDecimal.valueOf(session.getUniqueActionTypes())
                .divide(new BigDecimal("10"), 2, RoundingMode.HALF_UP); // Normalize to 10 types
        
        // Weighted average
        return durationScore.multiply(new BigDecimal("0.3"))
                .add(actionScore.multiply(new BigDecimal("0.4")))
                .add(varietyScore.multiply(new BigDecimal("0.3")))
                .min(BigDecimal.ONE); // Cap at 1.0
    }
    
    private long calculateInitialDelay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.plusDays(1).withHour(2).withMinute(0).withSecond(0);
        return Duration.between(now, nextRun).getSeconds();
    }
    
    private String serializeMetrics(TransactionMetrics metrics) {
        // Simple JSON serialization for Redis storage
        return String.format("{\"count\":%d,\"total\":%s,\"avg\":%s,\"max\":%s,\"min\":%s}",
                metrics.getTransactionCount(),
                metrics.getTotalAmount(),
                metrics.getAverageAmount(),
                metrics.getMaxAmount(),
                metrics.getMinAmount());
    }
    
    private boolean isOldKey(String key, Instant cutoff) {
        // Parse date from key if it contains one
        String[] parts = key.split(":");
        for (String part : parts) {
            try {
                LocalDate date = LocalDate.parse(part);
                return date.isBefore(LocalDate.from(cutoff));
            } catch (Exception ignored) {
                // Not a date, continue
            }
        }
        return false;
    }
}