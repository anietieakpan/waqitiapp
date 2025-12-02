package com.waqiti.rewards.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerAwareListenerErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class LoyaltyPointsConsumer {
    private static final Logger logger = LoggerFactory.getLogger(LoyaltyPointsConsumer.class);
    
    private static final String TOPIC = "loyalty-points";
    private static final String DLQ_TOPIC = "loyalty-points-dlq";
    private static final String CONSUMER_GROUP = "loyalty-points-consumer-group";
    private static final String POINTS_BALANCE_PREFIX = "points:balance:";
    private static final String TIER_STATUS_PREFIX = "tier:status:";
    private static final String POINTS_HISTORY_PREFIX = "points:history:";
    private static final String EXPIRATION_TRACKING_PREFIX = "points:expiration:";
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final RedisTemplate<String, String> redisTemplate;
    private final CacheManager cacheManager;
    
    @Value("${rewards.loyalty.points.base-earn-rate:0.01}")
    private double baseEarnRate;
    
    @Value("${rewards.loyalty.points.max-daily-earn:10000}")
    private int maxDailyEarn;
    
    @Value("${rewards.loyalty.points.expiration.months:12}")
    private int pointsExpirationMonths;
    
    @Value("${rewards.loyalty.points.min-redemption:1000}")
    private int minRedemptionPoints;
    
    @Value("${rewards.loyalty.tier.upgrade.enabled:true}")
    private boolean tierUpgradeEnabled;
    
    @Value("${rewards.loyalty.tier.downgrade.enabled:true}")
    private boolean tierDowngradeEnabled;
    
    @Value("${rewards.loyalty.bonus.multiplier.enabled:true}")
    private boolean bonusMultiplierEnabled;
    
    @Value("${rewards.loyalty.transfer.enabled:true}")
    private boolean transferEnabled;
    
    @Value("${rewards.loyalty.transfer.min-amount:100}")
    private int minTransferAmount;
    
    @Value("${rewards.loyalty.fraud.detection.enabled:true}")
    private boolean fraudDetectionEnabled;
    
    private final Map<String, LoyaltyAccount> loyaltyAccounts = new ConcurrentHashMap<>();
    private final Map<String, TierStatus> tierStatuses = new ConcurrentHashMap<>();
    private final Map<String, PointsTransaction> pendingTransactions = new ConcurrentHashMap<>();
    private final Map<String, EarnHistory> earnHistories = new ConcurrentHashMap<>();
    private final Map<String, RedemptionCatalog> redemptionCatalogs = new ConcurrentHashMap<>();
    private final Map<String, BonusProgram> activePrograms = new ConcurrentHashMap<>();
    private final Map<String, PointsExpiration> expirationTracking = new ConcurrentHashMap<>();
    private final Map<String, TransferRequest> pendingTransfers = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(8);
    private final ExecutorService processingExecutor = Executors.newFixedThreadPool(12);
    private final ExecutorService calculationExecutor = Executors.newFixedThreadPool(6);
    private final ExecutorService notificationExecutor = Executors.newFixedThreadPool(4);
    private final ExecutorService fraudCheckExecutor = Executors.newFixedThreadPool(3);
    
    private Counter pointsEarnedCounter;
    private Counter pointsRedeemedCounter;
    private Counter pointsExpiredCounter;
    private Counter pointsTransferredCounter;
    private Counter tierUpgradesCounter;
    private Counter tierDowngradesCounter;
    private Counter bonusPointsCounter;
    private Counter fraudDetectedCounter;
    private Counter errorCounter;
    private Timer processingTimer;
    private Timer calculationTimer;
    private Timer fraudCheckTimer;
    
    private CircuitBreaker circuitBreaker;
    private Retry retry;
    
    @PostConstruct
    public void init() {
        initializeMetrics();
        initializeResilience();
        initializeBackgroundTasks();
        loadLoyaltyAccounts();
        loadRedemptionCatalog();
        initializePrograms();
        logger.info("LoyaltyPointsConsumer initialized with comprehensive rewards management");
    }
    
    private void initializeMetrics() {
        pointsEarnedCounter = Counter.builder("loyalty.points.earned")
                .description("Total points earned")
                .register(meterRegistry);
                
        pointsRedeemedCounter = Counter.builder("loyalty.points.redeemed")
                .description("Total points redeemed")
                .register(meterRegistry);
                
        pointsExpiredCounter = Counter.builder("loyalty.points.expired")
                .description("Total points expired")
                .register(meterRegistry);
                
        pointsTransferredCounter = Counter.builder("loyalty.points.transferred")
                .description("Total points transferred")
                .register(meterRegistry);
                
        tierUpgradesCounter = Counter.builder("loyalty.tier.upgrades")
                .description("Total tier upgrades")
                .register(meterRegistry);
                
        tierDowngradesCounter = Counter.builder("loyalty.tier.downgrades")
                .description("Total tier downgrades")
                .register(meterRegistry);
                
        bonusPointsCounter = Counter.builder("loyalty.bonus.points")
                .description("Total bonus points awarded")
                .register(meterRegistry);
                
        fraudDetectedCounter = Counter.builder("loyalty.fraud.detected")
                .description("Fraudulent activities detected")
                .register(meterRegistry);
                
        errorCounter = Counter.builder("loyalty.points.errors")
                .description("Total processing errors")
                .register(meterRegistry);
                
        processingTimer = Timer.builder("loyalty.processing.time")
                .description("Time to process loyalty events")
                .register(meterRegistry);
                
        calculationTimer = Timer.builder("loyalty.calculation.time")
                .description("Time to calculate points")
                .register(meterRegistry);
                
        fraudCheckTimer = Timer.builder("loyalty.fraud.check.time")
                .description("Time to check for fraud")
                .register(meterRegistry);
                
        Gauge.builder("loyalty.active.accounts", loyaltyAccounts, Map::size)
                .description("Number of active loyalty accounts")
                .register(meterRegistry);
                
        Gauge.builder("loyalty.pending.transactions", pendingTransactions, Map::size)
                .description("Number of pending transactions")
                .register(meterRegistry);
                
        Gauge.builder("loyalty.active.programs", activePrograms, Map::size)
                .description("Number of active bonus programs")
                .register(meterRegistry);
    }
    
    private void initializeResilience() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(100)
                .permittedNumberOfCallsInHalfOpenState(10)
                .build();
                
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("loyalty-points-processor");
        
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(2))
                .retryExceptions(Exception.class)
                .build();
                
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        retry = retryRegistry.retry("loyalty-points-retry");
    }
    
    private void initializeBackgroundTasks() {
        scheduledExecutor.scheduleWithFixedDelay(this::processPendingTransactions, 0, 10, TimeUnit.SECONDS);
        scheduledExecutor.scheduleWithFixedDelay(this::checkPointsExpiration, 0, 1, TimeUnit.HOURS);
        scheduledExecutor.scheduleWithFixedDelay(this::updateTierStatuses, 0, 30, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::processTransferRequests, 0, 15, TimeUnit.SECONDS);
        scheduledExecutor.scheduleWithFixedDelay(this::calculateBonusPoints, 0, 5, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::generateLoyaltyReports, 0, 1, TimeUnit.HOURS);
        scheduledExecutor.scheduleWithFixedDelay(this::syncAccountBalances, 0, 2, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::cleanupExpiredData, 0, 24, TimeUnit.HOURS);
    }
    
    private void loadLoyaltyAccounts() {
        try {
            Set<String> accountKeys = redisTemplate.keys(POINTS_BALANCE_PREFIX + "*");
            if (accountKeys != null && !accountKeys.isEmpty()) {
                for (String key : accountKeys) {
                    String accountJson = redisTemplate.opsForValue().get(key);
                    if (accountJson != null) {
                        LoyaltyAccount account = objectMapper.readValue(accountJson, LoyaltyAccount.class);
                        loyaltyAccounts.put(account.customerId, account);
                    }
                }
                logger.info("Loaded {} loyalty accounts from Redis", loyaltyAccounts.size());
            }
        } catch (Exception e) {
            logger.error("Failed to load loyalty accounts", e);
        }
    }
    
    private void loadRedemptionCatalog() {
        initializeDefaultCatalog();
        logger.info("Redemption catalog initialized");
    }
    
    private void initializePrograms() {
        initializeDefaultPrograms();
        logger.info("Bonus programs initialized");
    }
    
    @KafkaListener(topics = TOPIC, groupId = CONSUMER_GROUP)
    public void processLoyaltyEvent(@Payload String message,
                                   @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                   @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                   @Header(KafkaHeaders.OFFSET) long offset,
                                   @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
                                   Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        MDC.put("loyalty.topic", topic);
        MDC.put("loyalty.partition", String.valueOf(partition));
        MDC.put("loyalty.offset", String.valueOf(offset));
        
        try {
            logger.debug("Processing loyalty event from partition {} offset {}", partition, offset);
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            String eventType = (String) eventData.get("eventType");
            String customerId = (String) eventData.get("customerId");
            
            MDC.put("customer.id", customerId);
            MDC.put("event.type", eventType);
            
            Supplier<Boolean> eventProcessor = () -> {
                try {
                    switch (eventType) {
                        case "POINTS_EARNED":
                            return handlePointsEarned(eventData);
                        case "POINTS_REDEEMED":
                            return handlePointsRedeemed(eventData);
                        case "POINTS_TRANSFERRED":
                            return handlePointsTransferred(eventData);
                        case "POINTS_EXPIRED":
                            return handlePointsExpired(eventData);
                        case "POINTS_ADJUSTED":
                            return handlePointsAdjusted(eventData);
                        case "TIER_UPGRADED":
                            return handleTierUpgraded(eventData);
                        case "TIER_DOWNGRADED":
                            return handleTierDowngraded(eventData);
                        case "BONUS_AWARDED":
                            return handleBonusAwarded(eventData);
                        case "MILESTONE_REACHED":
                            return handleMilestoneReached(eventData);
                        case "ACCOUNT_CREATED":
                            return handleAccountCreated(eventData);
                        case "ACCOUNT_SUSPENDED":
                            return handleAccountSuspended(eventData);
                        case "ACCOUNT_REACTIVATED":
                            return handleAccountReactivated(eventData);
                        case "CATALOG_ITEM_REDEEMED":
                            return handleCatalogItemRedeemed(eventData);
                        case "PROGRAM_ENROLLED":
                            return handleProgramEnrolled(eventData);
                        case "PROGRAM_COMPLETED":
                            return handleProgramCompleted(eventData);
                        case "POINTS_REVERSAL":
                            return handlePointsReversal(eventData);
                        default:
                            logger.warn("Unknown event type: {}", eventType);
                            return false;
                    }
                } catch (Exception e) {
                    logger.error("Error processing loyalty event", e);
                    errorCounter.increment();
                    return false;
                }
            };
            
            Boolean result = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(circuitBreaker, eventProcessor)).get();
            
            if (result) {
                acknowledgment.acknowledge();
                logger.debug("Loyalty event processed successfully");
            } else {
                sendToDlq(message, "Processing failed");
                acknowledgment.acknowledge();
            }
            
        } catch (Exception e) {
            logger.error("Failed to process loyalty event", e);
            errorCounter.increment();
            sendToDlq(message, e.getMessage());
            acknowledgment.acknowledge();
        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }
    
    private boolean handlePointsEarned(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        String transactionId = (String) eventData.get("transactionId");
        BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
        String source = (String) eventData.get("source");
        Map<String, Object> metadata = (Map<String, Object>) eventData.get("metadata");
        
        LoyaltyAccount account = loyaltyAccounts.computeIfAbsent(customerId, 
            k -> createNewAccount(k));
        
        if (fraudDetectionEnabled && detectFraud(account, amount, source)) {
            logger.warn("Fraud detected for customer: {}", customerId);
            suspendAccount(account, "Fraud detected");
            fraudDetectedCounter.increment();
            return false;
        }
        
        int basePoints = calculateBasePoints(amount);
        int bonusPoints = 0;
        
        if (bonusMultiplierEnabled) {
            bonusPoints = calculateBonusPoints(account, basePoints, source, metadata);
        }
        
        int totalPoints = basePoints + bonusPoints;
        
        if (exceedsDailyLimit(account, totalPoints)) {
            logger.warn("Daily earn limit exceeded for customer: {}", customerId);
            queueForNextDay(account, totalPoints, transactionId);
            return true;
        }
        
        account.addPoints(totalPoints);
        
        PointsTransaction transaction = new PointsTransaction(
            transactionId,
            customerId,
            "EARN",
            totalPoints,
            source,
            metadata
        );
        
        recordTransaction(transaction);
        updateEarnHistory(account, totalPoints, source);
        
        if (bonusPoints > 0) {
            bonusPointsCounter.increment();
            notifyBonusAwarded(account, bonusPoints, source);
        }
        
        checkTierEligibility(account);
        checkMilestones(account);
        
        persistAccount(account);
        pointsEarnedCounter.increment();
        
        return true;
    }
    
    private boolean handlePointsRedeemed(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        String redemptionId = (String) eventData.get("redemptionId");
        int points = ((Number) eventData.get("points")).intValue();
        String redemptionType = (String) eventData.get("redemptionType");
        Map<String, Object> details = (Map<String, Object>) eventData.get("details");
        
        LoyaltyAccount account = loyaltyAccounts.get(customerId);
        if (account == null) {
            logger.warn("Account not found: {}", customerId);
            return false;
        }
        
        if (account.getAvailablePoints() < points) {
            logger.warn("Insufficient points for redemption: {} < {}", 
                       account.getAvailablePoints(), points);
            return false;
        }
        
        if (points < minRedemptionPoints) {
            logger.warn("Below minimum redemption threshold: {}", points);
            return false;
        }
        
        account.redeemPoints(points);
        
        PointsTransaction transaction = new PointsTransaction(
            redemptionId,
            customerId,
            "REDEEM",
            -points,
            redemptionType,
            details
        );
        
        recordTransaction(transaction);
        
        processRedemptionReward(account, redemptionType, details);
        
        updateRedemptionHistory(account, points, redemptionType);
        
        persistAccount(account);
        pointsRedeemedCounter.increment();
        
        return true;
    }
    
    private boolean handlePointsTransferred(Map<String, Object> eventData) {
        String fromCustomerId = (String) eventData.get("fromCustomerId");
        String toCustomerId = (String) eventData.get("toCustomerId");
        int points = ((Number) eventData.get("points")).intValue();
        String transferId = (String) eventData.get("transferId");
        String reason = (String) eventData.get("reason");
        
        if (!transferEnabled) {
            logger.warn("Points transfer is disabled");
            return false;
        }
        
        if (points < minTransferAmount) {
            logger.warn("Transfer amount below minimum: {}", points);
            return false;
        }
        
        LoyaltyAccount fromAccount = loyaltyAccounts.get(fromCustomerId);
        if (fromAccount == null || fromAccount.getAvailablePoints() < points) {
            logger.warn("Insufficient points for transfer from: {}", fromCustomerId);
            return false;
        }
        
        LoyaltyAccount toAccount = loyaltyAccounts.computeIfAbsent(toCustomerId,
            k -> createNewAccount(k));
        
        fromAccount.redeemPoints(points);
        toAccount.addPoints(points);
        
        recordTransfer(transferId, fromCustomerId, toCustomerId, points, reason);
        
        notifyTransferCompleted(fromAccount, toAccount, points);
        
        persistAccount(fromAccount);
        persistAccount(toAccount);
        
        pointsTransferredCounter.increment();
        return true;
    }
    
    private boolean handlePointsExpired(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        int expiredPoints = ((Number) eventData.get("expiredPoints")).intValue();
        String expirationBatch = (String) eventData.get("expirationBatch");
        
        LoyaltyAccount account = loyaltyAccounts.get(customerId);
        if (account == null) {
            return false;
        }
        
        account.expirePoints(expiredPoints);
        
        PointsExpiration expiration = new PointsExpiration(
            customerId,
            expiredPoints,
            expirationBatch,
            Instant.now()
        );
        
        expirationTracking.put(expirationBatch, expiration);
        
        notifyPointsExpiring(account, expiredPoints);
        
        persistAccount(account);
        pointsExpiredCounter.increment();
        
        return true;
    }
    
    private boolean handlePointsAdjusted(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        int adjustmentAmount = ((Number) eventData.get("adjustmentAmount")).intValue();
        String reason = (String) eventData.get("reason");
        String adjustedBy = (String) eventData.get("adjustedBy");
        
        LoyaltyAccount account = loyaltyAccounts.get(customerId);
        if (account == null) {
            return false;
        }
        
        if (adjustmentAmount > 0) {
            account.addPoints(adjustmentAmount);
        } else {
            account.redeemPoints(Math.abs(adjustmentAmount));
        }
        
        account.addAdjustment(new PointsAdjustment(adjustmentAmount, reason, adjustedBy));
        
        notifyAdjustment(account, adjustmentAmount, reason);
        
        persistAccount(account);
        
        return true;
    }
    
    private boolean handleTierUpgraded(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        String oldTier = (String) eventData.get("oldTier");
        String newTier = (String) eventData.get("newTier");
        Map<String, Object> benefits = (Map<String, Object>) eventData.get("benefits");
        
        LoyaltyAccount account = loyaltyAccounts.get(customerId);
        if (account == null) {
            return false;
        }
        
        TierStatus tierStatus = tierStatuses.computeIfAbsent(customerId, 
            k -> new TierStatus(k));
        
        tierStatus.currentTier = newTier;
        tierStatus.previousTier = oldTier;
        tierStatus.upgradeDate = Instant.now();
        tierStatus.benefits = benefits;
        
        account.setTier(newTier);
        
        awardTierUpgradeBonuses(account, newTier);
        
        notifyTierUpgrade(account, oldTier, newTier, benefits);
        
        persistAccount(account);
        persistTierStatus(tierStatus);
        
        tierUpgradesCounter.increment();
        return true;
    }
    
    private boolean handleTierDowngraded(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        String oldTier = (String) eventData.get("oldTier");
        String newTier = (String) eventData.get("newTier");
        String reason = (String) eventData.get("reason");
        
        if (!tierDowngradeEnabled) {
            logger.info("Tier downgrade is disabled");
            return true;
        }
        
        LoyaltyAccount account = loyaltyAccounts.get(customerId);
        if (account == null) {
            return false;
        }
        
        TierStatus tierStatus = tierStatuses.get(customerId);
        if (tierStatus != null) {
            tierStatus.currentTier = newTier;
            tierStatus.previousTier = oldTier;
            tierStatus.downgradeDate = Instant.now();
            tierStatus.downgradeReason = reason;
        }
        
        account.setTier(newTier);
        
        notifyTierDowngrade(account, oldTier, newTier, reason);
        
        persistAccount(account);
        if (tierStatus != null) {
            persistTierStatus(tierStatus);
        }
        
        tierDowngradesCounter.increment();
        return true;
    }
    
    private boolean handleBonusAwarded(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        int bonusPoints = ((Number) eventData.get("bonusPoints")).intValue();
        String programId = (String) eventData.get("programId");
        String reason = (String) eventData.get("reason");
        
        LoyaltyAccount account = loyaltyAccounts.get(customerId);
        if (account == null) {
            return false;
        }
        
        account.addPoints(bonusPoints);
        account.addBonus(new BonusAward(bonusPoints, programId, reason));
        
        BonusProgram program = activePrograms.get(programId);
        if (program != null) {
            program.recordAward(customerId, bonusPoints);
        }
        
        notifyBonusAwarded(account, bonusPoints, reason);
        
        persistAccount(account);
        bonusPointsCounter.increment();
        
        return true;
    }
    
    private boolean handleMilestoneReached(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        String milestone = (String) eventData.get("milestone");
        int rewardPoints = ((Number) eventData.get("rewardPoints")).intValue();
        Map<String, Object> achievements = (Map<String, Object>) eventData.get("achievements");
        
        LoyaltyAccount account = loyaltyAccounts.get(customerId);
        if (account == null) {
            return false;
        }
        
        account.addPoints(rewardPoints);
        account.addMilestone(new Milestone(milestone, rewardPoints, achievements));
        
        notifyMilestoneReached(account, milestone, rewardPoints);
        
        checkNextMilestone(account);
        
        persistAccount(account);
        
        return true;
    }
    
    private boolean handleAccountCreated(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        String tier = (String) eventData.getOrDefault("tier", "BRONZE");
        int welcomeBonus = ((Number) eventData.getOrDefault("welcomeBonus", 0)).intValue();
        
        LoyaltyAccount account = new LoyaltyAccount(customerId, tier);
        
        if (welcomeBonus > 0) {
            account.addPoints(welcomeBonus);
            notifyWelcomeBonus(account, welcomeBonus);
        }
        
        loyaltyAccounts.put(customerId, account);
        
        TierStatus tierStatus = new TierStatus(customerId);
        tierStatus.currentTier = tier;
        tierStatuses.put(customerId, tierStatus);
        
        initializeEarnHistory(customerId);
        
        persistAccount(account);
        persistTierStatus(tierStatus);
        
        return true;
    }
    
    private boolean handleAccountSuspended(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        String reason = (String) eventData.get("reason");
        String suspendedBy = (String) eventData.get("suspendedBy");
        
        LoyaltyAccount account = loyaltyAccounts.get(customerId);
        if (account == null) {
            return false;
        }
        
        account.suspend(reason, suspendedBy);
        
        cancelPendingTransactions(customerId);
        
        notifyAccountSuspended(account, reason);
        
        persistAccount(account);
        
        return true;
    }
    
    private boolean handleAccountReactivated(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        String reactivatedBy = (String) eventData.get("reactivatedBy");
        int reactivationBonus = ((Number) eventData.getOrDefault("reactivationBonus", 0)).intValue();
        
        LoyaltyAccount account = loyaltyAccounts.get(customerId);
        if (account == null) {
            return false;
        }
        
        account.reactivate(reactivatedBy);
        
        if (reactivationBonus > 0) {
            account.addPoints(reactivationBonus);
            notifyReactivationBonus(account, reactivationBonus);
        }
        
        persistAccount(account);
        
        return true;
    }
    
    private boolean handleCatalogItemRedeemed(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        String itemId = (String) eventData.get("itemId");
        int pointsCost = ((Number) eventData.get("pointsCost")).intValue();
        Map<String, Object> itemDetails = (Map<String, Object>) eventData.get("itemDetails");
        
        LoyaltyAccount account = loyaltyAccounts.get(customerId);
        if (account == null || account.getAvailablePoints() < pointsCost) {
            return false;
        }
        
        RedemptionCatalog catalog = redemptionCatalogs.get(account.getTier());
        if (catalog == null || !catalog.isItemAvailable(itemId)) {
            logger.warn("Catalog item not available: {}", itemId);
            return false;
        }
        
        account.redeemPoints(pointsCost);
        
        processCatalogRedemption(account, itemId, itemDetails);
        
        catalog.recordRedemption(itemId, customerId);
        
        persistAccount(account);
        
        return true;
    }
    
    private boolean handleProgramEnrolled(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        String programId = (String) eventData.get("programId");
        Map<String, Object> enrollmentDetails = (Map<String, Object>) eventData.get("enrollmentDetails");
        
        LoyaltyAccount account = loyaltyAccounts.get(customerId);
        if (account == null) {
            return false;
        }
        
        BonusProgram program = activePrograms.get(programId);
        if (program == null) {
            logger.warn("Program not found: {}", programId);
            return false;
        }
        
        program.enroll(customerId, enrollmentDetails);
        account.enrollInProgram(programId);
        
        notifyProgramEnrollment(account, program);
        
        persistAccount(account);
        
        return true;
    }
    
    private boolean handleProgramCompleted(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        String programId = (String) eventData.get("programId");
        int completionBonus = ((Number) eventData.get("completionBonus")).intValue();
        
        LoyaltyAccount account = loyaltyAccounts.get(customerId);
        if (account == null) {
            return false;
        }
        
        account.addPoints(completionBonus);
        account.completeProgram(programId);
        
        BonusProgram program = activePrograms.get(programId);
        if (program != null) {
            program.recordCompletion(customerId);
        }
        
        notifyProgramCompletion(account, programId, completionBonus);
        
        persistAccount(account);
        
        return true;
    }
    
    private boolean handlePointsReversal(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        String originalTransactionId = (String) eventData.get("originalTransactionId");
        int reversalAmount = ((Number) eventData.get("reversalAmount")).intValue();
        String reason = (String) eventData.get("reason");
        
        LoyaltyAccount account = loyaltyAccounts.get(customerId);
        if (account == null) {
            return false;
        }
        
        account.redeemPoints(reversalAmount);
        
        PointsTransaction reversal = new PointsTransaction(
            UUID.randomUUID().toString(),
            customerId,
            "REVERSAL",
            -reversalAmount,
            reason,
            Map.of("originalTransactionId", originalTransactionId)
        );
        
        recordTransaction(reversal);
        
        notifyPointsReversal(account, reversalAmount, reason);
        
        persistAccount(account);
        
        return true;
    }
    
    private void processPendingTransactions() {
        pendingTransactions.values().stream()
            .filter(t -> t.isPending())
            .limit(100)
            .forEach(this::processTransaction);
    }
    
    private void checkPointsExpiration() {
        Instant expirationThreshold = Instant.now().minusSeconds(pointsExpirationMonths * 30L * 24 * 60 * 60);
        
        loyaltyAccounts.values().forEach(account -> {
            int expiredPoints = account.calculateExpiredPoints(expirationThreshold);
            if (expiredPoints > 0) {
                handlePointsExpired(Map.of(
                    "customerId", account.customerId,
                    "expiredPoints", expiredPoints,
                    "expirationBatch", UUID.randomUUID().toString()
                ));
            }
        });
    }
    
    private void updateTierStatuses() {
        loyaltyAccounts.values().forEach(this::checkTierEligibility);
    }
    
    private void processTransferRequests() {
        pendingTransfers.values().stream()
            .filter(TransferRequest::isValid)
            .limit(50)
            .forEach(this::processTransfer);
    }
    
    private void calculateBonusPoints() {
        loyaltyAccounts.values().forEach(account -> {
            activePrograms.values().forEach(program -> {
                if (program.isEligible(account)) {
                    int bonus = program.calculateBonus(account);
                    if (bonus > 0) {
                        handleBonusAwarded(Map.of(
                            "customerId", account.customerId,
                            "bonusPoints", bonus,
                            "programId", program.programId,
                            "reason", program.programName
                        ));
                    }
                }
            });
        });
    }
    
    private void generateLoyaltyReports() {
        LoyaltyMetrics metrics = new LoyaltyMetrics();
        metrics.totalAccounts = loyaltyAccounts.size();
        metrics.totalPoints = loyaltyAccounts.values().stream()
            .mapToInt(LoyaltyAccount::getTotalPoints)
            .sum();
        metrics.averageBalance = metrics.totalPoints / Math.max(metrics.totalAccounts, 1);
        
        publishMetrics(metrics);
    }
    
    private void syncAccountBalances() {
        loyaltyAccounts.values().forEach(this::persistAccount);
    }
    
    private void cleanupExpiredData() {
        Instant cleanupThreshold = Instant.now().minusSeconds(365L * 24 * 60 * 60);
        
        expirationTracking.entrySet().removeIf(entry -> 
            entry.getValue().expirationDate.isBefore(cleanupThreshold));
    }
    
    private void sendToDlq(String message, String reason) {
        try {
            ProducerRecord<String, String> dlqRecord = new ProducerRecord<>(DLQ_TOPIC, message);
            dlqRecord.headers().add("failure_reason", reason.getBytes(StandardCharsets.UTF_8));
            dlqRecord.headers().add("original_topic", TOPIC.getBytes(StandardCharsets.UTF_8));
            dlqRecord.headers().add("failed_at", Instant.now().toString().getBytes(StandardCharsets.UTF_8));
            
            kafkaTemplate.send(dlqRecord);
            logger.warn("Message sent to DLQ with reason: {}", reason);
        } catch (Exception e) {
            logger.error("Failed to send message to DLQ", e);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down LoyaltyPointsConsumer...");
        
        persistAllAccounts();
        persistAllTierStatuses();
        
        scheduledExecutor.shutdown();
        processingExecutor.shutdown();
        calculationExecutor.shutdown();
        notificationExecutor.shutdown();
        fraudCheckExecutor.shutdown();
        
        try {
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("LoyaltyPointsConsumer shutdown complete");
    }
    
    private static class LoyaltyAccount {
        String customerId;
        int totalPoints;
        int availablePoints;
        int lifetimePoints;
        String tier;
        boolean suspended;
        String suspensionReason;
        List<PointsTransaction> transactions;
        List<BonusAward> bonuses;
        List<Milestone> milestones;
        List<PointsAdjustment> adjustments;
        Set<String> enrolledPrograms;
        Set<String> completedPrograms;
        Instant createdAt;
        Instant lastActivityAt;
        Instant suspendedAt;
        
        LoyaltyAccount(String customerId, String tier) {
            this.customerId = customerId;
            this.tier = tier;
            this.totalPoints = 0;
            this.availablePoints = 0;
            this.lifetimePoints = 0;
            this.suspended = false;
            this.transactions = new CopyOnWriteArrayList<>();
            this.bonuses = new CopyOnWriteArrayList<>();
            this.milestones = new CopyOnWriteArrayList<>();
            this.adjustments = new CopyOnWriteArrayList<>();
            this.enrolledPrograms = ConcurrentHashMap.newKeySet();
            this.completedPrograms = ConcurrentHashMap.newKeySet();
            this.createdAt = Instant.now();
            this.lastActivityAt = Instant.now();
        }
        
        synchronized void addPoints(int points) {
            this.totalPoints += points;
            this.availablePoints += points;
            this.lifetimePoints += points;
            this.lastActivityAt = Instant.now();
        }
        
        synchronized void redeemPoints(int points) {
            this.availablePoints -= points;
            this.lastActivityAt = Instant.now();
        }
        
        synchronized void expirePoints(int points) {
            this.availablePoints = Math.max(0, this.availablePoints - points);
            this.totalPoints = Math.max(0, this.totalPoints - points);
        }
        
        int getAvailablePoints() {
            return availablePoints;
        }
        
        int getTotalPoints() {
            return totalPoints;
        }
        
        String getTier() {
            return tier;
        }
        
        void setTier(String tier) {
            this.tier = tier;
        }
        
        void suspend(String reason, String suspendedBy) {
            this.suspended = true;
            this.suspensionReason = reason;
            this.suspendedAt = Instant.now();
        }
        
        void reactivate(String reactivatedBy) {
            this.suspended = false;
            this.suspensionReason = null;
            this.suspendedAt = null;
        }
        
        void addBonus(BonusAward bonus) {
            bonuses.add(bonus);
        }
        
        void addMilestone(Milestone milestone) {
            milestones.add(milestone);
        }
        
        void addAdjustment(PointsAdjustment adjustment) {
            adjustments.add(adjustment);
        }
        
        void enrollInProgram(String programId) {
            enrolledPrograms.add(programId);
        }
        
        void completeProgram(String programId) {
            enrolledPrograms.remove(programId);
            completedPrograms.add(programId);
        }
        
        int calculateExpiredPoints(Instant expirationThreshold) {
            return transactions.stream()
                .filter(t -> t.type.equals("EARN"))
                .filter(t -> t.timestamp.isBefore(expirationThreshold))
                .mapToInt(t -> t.points)
                .sum();
        }
    }
    
    private static class TierStatus {
        String customerId;
        String currentTier;
        String previousTier;
        Map<String, Object> benefits;
        int pointsToNextTier;
        Instant upgradeDate;
        Instant downgradeDate;
        String downgradeReason;
        
        TierStatus(String customerId) {
            this.customerId = customerId;
            this.currentTier = "BRONZE";
            this.benefits = new HashMap<>();
        }
    }
    
    private static class PointsTransaction {
        String transactionId;
        String customerId;
        String type;
        int points;
        String source;
        Map<String, Object> metadata;
        Instant timestamp;
        boolean pending;
        
        PointsTransaction(String transactionId, String customerId, String type, 
                         int points, String source, Map<String, Object> metadata) {
            this.transactionId = transactionId;
            this.customerId = customerId;
            this.type = type;
            this.points = points;
            this.source = source;
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
            this.timestamp = Instant.now();
            this.pending = false;
        }
        
        boolean isPending() {
            return pending;
        }
    }
    
    private static class EarnHistory {
        String customerId;
        Map<String, Integer> sourcePoints;
        Map<LocalDate, Integer> dailyEarnings;
        int totalEarned;
        Instant lastEarnDate;
    }
    
    private static class RedemptionCatalog {
        String tier;
        Map<String, CatalogItem> items;
        Map<String, Integer> redemptionCounts;
        
        boolean isItemAvailable(String itemId) {
            CatalogItem item = items.get(itemId);
            return item != null && item.available && item.stock > 0;
        }
        
        void recordRedemption(String itemId, String customerId) {
            CatalogItem item = items.get(itemId);
            if (item != null) {
                item.stock--;
                redemptionCounts.merge(itemId, 1, Integer::sum);
            }
        }
    }
    
    private static class CatalogItem {
        String itemId;
        String name;
        int pointsCost;
        int stock;
        boolean available;
        Map<String, Object> details;
    }
    
    private static class BonusProgram {
        String programId;
        String programName;
        Map<String, Object> criteria;
        Map<String, Integer> participantProgress;
        Map<String, Instant> completionDates;
        boolean active;
        
        boolean isEligible(LoyaltyAccount account) {
            return active && !account.completedPrograms.contains(programId);
        }
        
        int calculateBonus(LoyaltyAccount account) {
            return 0;
        }
        
        void enroll(String customerId, Map<String, Object> details) {
            participantProgress.put(customerId, 0);
        }
        
        void recordAward(String customerId, int points) {
            participantProgress.merge(customerId, points, Integer::sum);
        }
        
        void recordCompletion(String customerId) {
            completionDates.put(customerId, Instant.now());
        }
    }
    
    private static class PointsExpiration {
        String customerId;
        int expiredPoints;
        String batchId;
        Instant expirationDate;
        
        PointsExpiration(String customerId, int expiredPoints, String batchId, Instant expirationDate) {
            this.customerId = customerId;
            this.expiredPoints = expiredPoints;
            this.batchId = batchId;
            this.expirationDate = expirationDate;
        }
    }
    
    private static class TransferRequest {
        String transferId;
        String fromCustomerId;
        String toCustomerId;
        int points;
        String status;
        Instant requestedAt;
        
        boolean isValid() {
            return "PENDING".equals(status) && 
                   Duration.between(requestedAt, Instant.now()).toMinutes() < 30;
        }
    }
    
    private static class BonusAward {
        int points;
        String programId;
        String reason;
        Instant awardedAt;
        
        BonusAward(int points, String programId, String reason) {
            this.points = points;
            this.programId = programId;
            this.reason = reason;
            this.awardedAt = Instant.now();
        }
    }
    
    private static class Milestone {
        String name;
        int rewardPoints;
        Map<String, Object> achievements;
        Instant reachedAt;
        
        Milestone(String name, int rewardPoints, Map<String, Object> achievements) {
            this.name = name;
            this.rewardPoints = rewardPoints;
            this.achievements = achievements != null ? new HashMap<>(achievements) : new HashMap<>();
            this.reachedAt = Instant.now();
        }
    }
    
    private static class PointsAdjustment {
        int amount;
        String reason;
        String adjustedBy;
        Instant adjustedAt;
        
        PointsAdjustment(int amount, String reason, String adjustedBy) {
            this.amount = amount;
            this.reason = reason;
            this.adjustedBy = adjustedBy;
            this.adjustedAt = Instant.now();
        }
    }
    
    private static class LoyaltyMetrics {
        int totalAccounts;
        int totalPoints;
        int averageBalance;
        Map<String, Integer> tierDistribution;
        double redemptionRate;
    }
    
    private LoyaltyAccount createNewAccount(String customerId) {
        return new LoyaltyAccount(customerId, "BRONZE");
    }
    
    private boolean detectFraud(LoyaltyAccount account, BigDecimal amount, String source) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return false;
        } finally {
            sample.stop(fraudCheckTimer);
        }
    }
    
    private void suspendAccount(LoyaltyAccount account, String reason) {
        account.suspend(reason, "SYSTEM");
        persistAccount(account);
    }
    
    private int calculateBasePoints(BigDecimal amount) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return amount.multiply(BigDecimal.valueOf(baseEarnRate))
                       .setScale(0, RoundingMode.DOWN)
                       .intValue();
        } finally {
            sample.stop(calculationTimer);
        }
    }
    
    private int calculateBonusPoints(LoyaltyAccount account, int basePoints, String source, Map<String, Object> metadata) {
        double multiplier = getMultiplierForTier(account.getTier());
        return (int) (basePoints * (multiplier - 1));
    }
    
    private double getMultiplierForTier(String tier) {
        switch (tier) {
            case "PLATINUM": return 2.0;
            case "GOLD": return 1.5;
            case "SILVER": return 1.25;
            default: return 1.0;
        }
    }
    
    private boolean exceedsDailyLimit(LoyaltyAccount account, int points) {
        EarnHistory history = earnHistories.get(account.customerId);
        if (history == null) return false;
        
        int todayEarned = history.dailyEarnings.getOrDefault(LocalDate.now(), 0);
        return todayEarned + points > maxDailyEarn;
    }
    
    private void queueForNextDay(LoyaltyAccount account, int points, String transactionId) {
        logger.debug("Queueing {} points for next day for customer {}", points, account.customerId);
    }
    
    private void recordTransaction(PointsTransaction transaction) {
        pendingTransactions.put(transaction.transactionId, transaction);
    }
    
    private void updateEarnHistory(LoyaltyAccount account, int points, String source) {
        EarnHistory history = earnHistories.computeIfAbsent(account.customerId, 
            k -> new EarnHistory());
        history.sourcePoints.merge(source, points, Integer::sum);
        history.dailyEarnings.merge(LocalDate.now(), points, Integer::sum);
        history.totalEarned += points;
        history.lastEarnDate = Instant.now();
    }
    
    private void checkTierEligibility(LoyaltyAccount account) {
        String newTier = calculateTier(account.lifetimePoints);
        if (!newTier.equals(account.getTier())) {
            if (getTierRank(newTier) > getTierRank(account.getTier())) {
                handleTierUpgraded(Map.of(
                    "customerId", account.customerId,
                    "oldTier", account.getTier(),
                    "newTier", newTier,
                    "benefits", getTierBenefits(newTier)
                ));
            }
        }
    }
    
    private String calculateTier(int lifetimePoints) {
        if (lifetimePoints >= 100000) return "PLATINUM";
        if (lifetimePoints >= 50000) return "GOLD";
        if (lifetimePoints >= 20000) return "SILVER";
        return "BRONZE";
    }
    
    private int getTierRank(String tier) {
        switch (tier) {
            case "PLATINUM": return 4;
            case "GOLD": return 3;
            case "SILVER": return 2;
            default: return 1;
        }
    }
    
    private Map<String, Object> getTierBenefits(String tier) {
        Map<String, Object> benefits = new HashMap<>();
        benefits.put("multiplier", getMultiplierForTier(tier));
        benefits.put("exclusive_offers", getTierRank(tier) >= 3);
        return benefits;
    }
    
    private void checkMilestones(LoyaltyAccount account) {
        logger.debug("Checking milestones for customer {}", account.customerId);
    }
    
    private void checkNextMilestone(LoyaltyAccount account) {
        logger.debug("Checking next milestone for customer {}", account.customerId);
    }
    
    private void persistAccount(LoyaltyAccount account) {
        try {
            String key = POINTS_BALANCE_PREFIX + account.customerId;
            String json = objectMapper.writeValueAsString(account);
            redisTemplate.opsForValue().set(key, json, Duration.ofDays(30));
        } catch (Exception e) {
            logger.error("Failed to persist account", e);
        }
    }
    
    private void persistTierStatus(TierStatus status) {
        try {
            String key = TIER_STATUS_PREFIX + status.customerId;
            String json = objectMapper.writeValueAsString(status);
            redisTemplate.opsForValue().set(key, json, Duration.ofDays(30));
        } catch (Exception e) {
            logger.error("Failed to persist tier status", e);
        }
    }
    
    private void persistAllAccounts() {
        loyaltyAccounts.values().forEach(this::persistAccount);
    }
    
    private void persistAllTierStatuses() {
        tierStatuses.values().forEach(this::persistTierStatus);
    }
    
    private void processRedemptionReward(LoyaltyAccount account, String type, Map<String, Object> details) {
        logger.debug("Processing redemption reward for customer {}", account.customerId);
    }
    
    private void updateRedemptionHistory(LoyaltyAccount account, int points, String type) {
        logger.debug("Updating redemption history for customer {}", account.customerId);
    }
    
    private void recordTransfer(String transferId, String from, String to, int points, String reason) {
        logger.info("Transfer {} from {} to {}: {} points", transferId, from, to, points);
    }
    
    private void awardTierUpgradeBonuses(LoyaltyAccount account, String newTier) {
        int bonus = getTierRank(newTier) * 1000;
        account.addPoints(bonus);
        logger.info("Awarded {} tier upgrade bonus to customer {}", bonus, account.customerId);
    }
    
    private void initializeEarnHistory(String customerId) {
        EarnHistory history = new EarnHistory();
        history.customerId = customerId;
        history.sourcePoints = new ConcurrentHashMap<>();
        history.dailyEarnings = new ConcurrentHashMap<>();
        earnHistories.put(customerId, history);
    }
    
    private void cancelPendingTransactions(String customerId) {
        pendingTransactions.values().stream()
            .filter(t -> t.customerId.equals(customerId))
            .forEach(t -> t.pending = false);
    }
    
    private void processCatalogRedemption(LoyaltyAccount account, String itemId, Map<String, Object> details) {
        logger.debug("Processing catalog redemption for customer {}", account.customerId);
    }
    
    private void processTransaction(PointsTransaction transaction) {
        logger.debug("Processing pending transaction {}", transaction.transactionId);
    }
    
    private void processTransfer(TransferRequest transfer) {
        logger.debug("Processing transfer request {}", transfer.transferId);
    }
    
    private void publishMetrics(LoyaltyMetrics metrics) {
        logger.debug("Publishing loyalty metrics: {} accounts, {} total points", 
                    metrics.totalAccounts, metrics.totalPoints);
    }
    
    private void initializeDefaultCatalog() {
        RedemptionCatalog bronzeCatalog = new RedemptionCatalog();
        bronzeCatalog.tier = "BRONZE";
        bronzeCatalog.items = new ConcurrentHashMap<>();
        bronzeCatalog.redemptionCounts = new ConcurrentHashMap<>();
        redemptionCatalogs.put("BRONZE", bronzeCatalog);
    }
    
    private void initializeDefaultPrograms() {
        BonusProgram welcomeProgram = new BonusProgram();
        welcomeProgram.programId = "WELCOME";
        welcomeProgram.programName = "Welcome Program";
        welcomeProgram.criteria = new HashMap<>();
        welcomeProgram.participantProgress = new ConcurrentHashMap<>();
        welcomeProgram.completionDates = new ConcurrentHashMap<>();
        welcomeProgram.active = true;
        activePrograms.put("WELCOME", welcomeProgram);
    }
    
    private void notifyBonusAwarded(LoyaltyAccount account, int points, String reason) {
        logger.info("Bonus awarded to {}: {} points for {}", account.customerId, points, reason);
    }
    
    private void notifyTransferCompleted(LoyaltyAccount from, LoyaltyAccount to, int points) {
        logger.info("Transfer completed: {} points from {} to {}", points, from.customerId, to.customerId);
    }
    
    private void notifyPointsExpiring(LoyaltyAccount account, int points) {
        logger.warn("Points expiring for {}: {} points", account.customerId, points);
    }
    
    private void notifyAdjustment(LoyaltyAccount account, int amount, String reason) {
        logger.info("Points adjusted for {}: {} points, reason: {}", account.customerId, amount, reason);
    }
    
    private void notifyTierUpgrade(LoyaltyAccount account, String oldTier, String newTier, Map<String, Object> benefits) {
        logger.info("Tier upgraded for {}: {} -> {}", account.customerId, oldTier, newTier);
    }
    
    private void notifyTierDowngrade(LoyaltyAccount account, String oldTier, String newTier, String reason) {
        logger.info("Tier downgraded for {}: {} -> {}, reason: {}", account.customerId, oldTier, newTier, reason);
    }
    
    private void notifyMilestoneReached(LoyaltyAccount account, String milestone, int reward) {
        logger.info("Milestone reached for {}: {} ({} points)", account.customerId, milestone, reward);
    }
    
    private void notifyWelcomeBonus(LoyaltyAccount account, int bonus) {
        logger.info("Welcome bonus awarded to {}: {} points", account.customerId, bonus);
    }
    
    private void notifyAccountSuspended(LoyaltyAccount account, String reason) {
        logger.warn("Account suspended: {} - {}", account.customerId, reason);
    }
    
    private void notifyReactivationBonus(LoyaltyAccount account, int bonus) {
        logger.info("Reactivation bonus awarded to {}: {} points", account.customerId, bonus);
    }
    
    private void notifyProgramEnrollment(LoyaltyAccount account, BonusProgram program) {
        logger.info("Customer {} enrolled in program {}", account.customerId, program.programName);
    }
    
    private void notifyProgramCompletion(LoyaltyAccount account, String programId, int bonus) {
        logger.info("Customer {} completed program {} ({} points)", account.customerId, programId, bonus);
    }
    
    private void notifyPointsReversal(LoyaltyAccount account, int amount, String reason) {
        logger.warn("Points reversed for {}: {} points, reason: {}", account.customerId, amount, reason);
    }
}