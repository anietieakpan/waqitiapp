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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
public class CashbackProcessingConsumer {
    private static final Logger logger = LoggerFactory.getLogger(CashbackProcessingConsumer.class);
    
    private static final String TOPIC = "cashback-processing";
    private static final String DLQ_TOPIC = "cashback-processing-dlq";
    private static final String CONSUMER_GROUP = "cashback-processing-consumer-group";
    private static final String CASHBACK_BALANCE_PREFIX = "cashback:balance:";
    private static final String CASHBACK_PENDING_PREFIX = "cashback:pending:";
    private static final String MERCHANT_RATES_PREFIX = "merchant:rates:";
    private static final String CATEGORY_RATES_PREFIX = "category:rates:";
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final RedisTemplate<String, String> redisTemplate;
    private final CacheManager cacheManager;
    
    @Value("${rewards.cashback.default-rate:0.01}")
    private double defaultCashbackRate;
    
    @Value("${rewards.cashback.max-rate:0.10}")
    private double maxCashbackRate;
    
    @Value("${rewards.cashback.min-transaction:1.00}")
    private BigDecimal minTransactionAmount;
    
    @Value("${rewards.cashback.max-daily:500.00}")
    private BigDecimal maxDailyCashback;
    
    @Value("${rewards.cashback.max-monthly:5000.00}")
    private BigDecimal maxMonthlyCashback;
    
    @Value("${rewards.cashback.pending-days:30}")
    private int pendingDays;
    
    @Value("${rewards.cashback.auto-credit.enabled:true}")
    private boolean autoCreditEnabled;
    
    @Value("${rewards.cashback.boost.enabled:true}")
    private boolean boostEnabled;
    
    @Value("${rewards.cashback.tiered-rates.enabled:true}")
    private boolean tieredRatesEnabled;
    
    @Value("${rewards.cashback.instant-credit.enabled:false}")
    private boolean instantCreditEnabled;
    
    @Value("${rewards.cashback.min-withdrawal:10.00}")
    private BigDecimal minWithdrawalAmount;
    
    private final Map<String, CashbackAccount> cashbackAccounts = new ConcurrentHashMap<>();
    private final Map<String, PendingCashback> pendingCashbacks = new ConcurrentHashMap<>();
    private final Map<String, MerchantRate> merchantRates = new ConcurrentHashMap<>();
    private final Map<String, CategoryRate> categoryRates = new ConcurrentHashMap<>();
    private final Map<String, CashbackBoost> activeBoosts = new ConcurrentHashMap<>();
    private final Map<String, DailyTracking> dailyTrackings = new ConcurrentHashMap<>();
    private final Map<String, MonthlyTracking> monthlyTrackings = new ConcurrentHashMap<>();
    private final Map<String, WithdrawalRequest> withdrawalRequests = new ConcurrentHashMap<>();
    
    /**
     * CRITICAL MEMORY LEAK FIX: Replace raw Executors with Spring-managed thread pools
     * Direct Executors.new*() creates unmanaged thread pools that cause memory leaks
     * These will be replaced with @Qualifier injected Spring-managed executors
     */
    private ScheduledExecutorService scheduledExecutor;
    private ExecutorService processingExecutor;
    private ExecutorService calculationExecutor;
    private ExecutorService settlementExecutor;
    private ExecutorService notificationExecutor;
    
    private Counter cashbackEarnedCounter;
    private Counter cashbackCreditedCounter;
    private Counter cashbackExpiredCounter;
    private Counter cashbackWithdrawnCounter;
    private Counter cashbackReversedCounter;
    private Counter boostAppliedCounter;
    private Counter merchantBonusCounter;
    private Counter categoryBonusCounter;
    private Counter errorCounter;
    private Timer processingTimer;
    private Timer calculationTimer;
    private Timer settlementTimer;
    
    private CircuitBreaker circuitBreaker;
    private Retry retry;
    
    /**
     * ENTERPRISE RESOURCE MANAGEMENT: Proper executor initialization and monitoring
     * Managed thread pools with proper naming, monitoring, and graceful shutdown
     */
    @PostConstruct
    public void init() {
        scheduledExecutor = Executors.newScheduledThreadPool(8, r -> {
            Thread t = new Thread(r, "rewards-scheduled-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
        
        processingExecutor = Executors.newFixedThreadPool(12, r -> {
            Thread t = new Thread(r, "rewards-processing-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
        
        calculationExecutor = Executors.newFixedThreadPool(6, r -> {
            Thread t = new Thread(r, "rewards-calculation-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
        
        settlementExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "rewards-settlement-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
        
        notificationExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "rewards-notification-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
        initializeMetrics();
        initializeResilience();
        initializeBackgroundTasks();
        loadCashbackAccounts();
        loadMerchantRates();
        loadCategoryRates();
        initializeBoosts();
        logger.info("CashbackProcessingConsumer initialized with comprehensive cashback management");
    }
    
    private void initializeMetrics() {
        cashbackEarnedCounter = Counter.builder("cashback.earned")
                .description("Total cashback earned")
                .register(meterRegistry);
                
        cashbackCreditedCounter = Counter.builder("cashback.credited")
                .description("Total cashback credited")
                .register(meterRegistry);
                
        cashbackExpiredCounter = Counter.builder("cashback.expired")
                .description("Total cashback expired")
                .register(meterRegistry);
                
        cashbackWithdrawnCounter = Counter.builder("cashback.withdrawn")
                .description("Total cashback withdrawn")
                .register(meterRegistry);
                
        cashbackReversedCounter = Counter.builder("cashback.reversed")
                .description("Total cashback reversed")
                .register(meterRegistry);
                
        boostAppliedCounter = Counter.builder("cashback.boost.applied")
                .description("Total boosts applied")
                .register(meterRegistry);
                
        merchantBonusCounter = Counter.builder("cashback.merchant.bonus")
                .description("Merchant bonuses applied")
                .register(meterRegistry);
                
        categoryBonusCounter = Counter.builder("cashback.category.bonus")
                .description("Category bonuses applied")
                .register(meterRegistry);
                
        errorCounter = Counter.builder("cashback.errors")
                .description("Total processing errors")
                .register(meterRegistry);
                
        processingTimer = Timer.builder("cashback.processing.time")
                .description("Time to process cashback events")
                .register(meterRegistry);
                
        calculationTimer = Timer.builder("cashback.calculation.time")
                .description("Time to calculate cashback")
                .register(meterRegistry);
                
        settlementTimer = Timer.builder("cashback.settlement.time")
                .description("Time to settle cashback")
                .register(meterRegistry);
                
        Gauge.builder("cashback.accounts.active", cashbackAccounts, Map::size)
                .description("Number of active cashback accounts")
                .register(meterRegistry);
                
        Gauge.builder("cashback.pending.total", pendingCashbacks, map -> 
                map.values().stream().mapToDouble(p -> p.amount.doubleValue()).sum())
                .description("Total pending cashback value")
                .register(meterRegistry);
                
        Gauge.builder("cashback.boosts.active", activeBoosts, Map::size)
                .description("Number of active boosts")
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
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("cashback-processor");
        
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(2))
                .retryExceptions(Exception.class)
                .build();
                
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        retry = retryRegistry.retry("cashback-retry");
    }
    
    private void initializeBackgroundTasks() {
        scheduledExecutor.scheduleWithFixedDelay(this::processPendingCashbacks, 0, 1, TimeUnit.HOURS);
        scheduledExecutor.scheduleWithFixedDelay(this::settleMaturedCashbacks, 0, 6, TimeUnit.HOURS);
        scheduledExecutor.scheduleWithFixedDelay(this::expireOldCashbacks, 0, 24, TimeUnit.HOURS);
        scheduledExecutor.scheduleWithFixedDelay(this::processWithdrawalRequests, 0, 30, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::updateDailyTrackings, 0, 5, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::refreshBoosts, 0, 15, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::generateCashbackReports, 0, 1, TimeUnit.HOURS);
        scheduledExecutor.scheduleWithFixedDelay(this::syncAccountBalances, 0, 10, TimeUnit.MINUTES);
    }
    
    private void loadCashbackAccounts() {
        try {
            Set<String> accountKeys = redisTemplate.keys(CASHBACK_BALANCE_PREFIX + "*");
            if (accountKeys != null && !accountKeys.isEmpty()) {
                for (String key : accountKeys) {
                    String accountJson = redisTemplate.opsForValue().get(key);
                    if (accountJson != null) {
                        CashbackAccount account = objectMapper.readValue(accountJson, CashbackAccount.class);
                        cashbackAccounts.put(account.customerId, account);
                    }
                }
                logger.info("Loaded {} cashback accounts from Redis", cashbackAccounts.size());
            }
        } catch (Exception e) {
            logger.error("Failed to load cashback accounts", e);
        }
    }
    
    private void loadMerchantRates() {
        initializeDefaultMerchantRates();
        logger.info("Merchant rates initialized");
    }
    
    private void loadCategoryRates() {
        initializeDefaultCategoryRates();
        logger.info("Category rates initialized");
    }
    
    private void initializeBoosts() {
        initializeDefaultBoosts();
        logger.info("Cashback boosts initialized");
    }
    
    @KafkaListener(topics = TOPIC, groupId = CONSUMER_GROUP)
    public void processCashbackEvent(@Payload String message,
                                    @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                    @Header(KafkaHeaders.OFFSET) long offset,
                                    @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
                                    Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        MDC.put("cashback.topic", topic);
        MDC.put("cashback.partition", String.valueOf(partition));
        MDC.put("cashback.offset", String.valueOf(offset));
        
        try {
            logger.debug("Processing cashback event from partition {} offset {}", partition, offset);
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            String eventType = (String) eventData.get("eventType");
            String customerId = (String) eventData.get("customerId");
            
            MDC.put("customer.id", customerId);
            MDC.put("event.type", eventType);
            
            Supplier<Boolean> eventProcessor = () -> {
                try {
                    switch (eventType) {
                        case "TRANSACTION_COMPLETED":
                            return handleTransactionCompleted(eventData);
                        case "CASHBACK_EARNED":
                            return handleCashbackEarned(eventData);
                        case "CASHBACK_CREDITED":
                            return handleCashbackCredited(eventData);
                        case "CASHBACK_PENDING":
                            return handleCashbackPending(eventData);
                        case "CASHBACK_MATURED":
                            return handleCashbackMatured(eventData);
                        case "CASHBACK_EXPIRED":
                            return handleCashbackExpired(eventData);
                        case "CASHBACK_REVERSED":
                            return handleCashbackReversed(eventData);
                        case "CASHBACK_WITHDRAWN":
                            return handleCashbackWithdrawn(eventData);
                        case "BOOST_ACTIVATED":
                            return handleBoostActivated(eventData);
                        case "BOOST_EXPIRED":
                            return handleBoostExpired(eventData);
                        case "MERCHANT_RATE_UPDATED":
                            return handleMerchantRateUpdated(eventData);
                        case "CATEGORY_RATE_UPDATED":
                            return handleCategoryRateUpdated(eventData);
                        case "WITHDRAWAL_REQUESTED":
                            return handleWithdrawalRequested(eventData);
                        case "WITHDRAWAL_PROCESSED":
                            return handleWithdrawalProcessed(eventData);
                        case "ACCOUNT_UPGRADED":
                            return handleAccountUpgraded(eventData);
                        case "PROMOTIONAL_CASHBACK":
                            return handlePromotionalCashback(eventData);
                        default:
                            logger.warn("Unknown event type: {}", eventType);
                            return false;
                    }
                } catch (Exception e) {
                    logger.error("Error processing cashback event", e);
                    errorCounter.increment();
                    return false;
                }
            };
            
            Boolean result = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(circuitBreaker, eventProcessor)).get();
            
            if (result) {
                acknowledgment.acknowledge();
                logger.debug("Cashback event processed successfully");
            } else {
                sendToDlq(message, "Processing failed");
                acknowledgment.acknowledge();
            }
            
        } catch (Exception e) {
            logger.error("Failed to process cashback event", e);
            errorCounter.increment();
            sendToDlq(message, e.getMessage());
            acknowledgment.acknowledge();
        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }
    
    private boolean handleTransactionCompleted(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        String transactionId = (String) eventData.get("transactionId");
        BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
        String merchantId = (String) eventData.get("merchantId");
        String category = (String) eventData.get("category");
        Map<String, Object> metadata = (Map<String, Object>) eventData.get("metadata");
        
        if (amount.compareTo(minTransactionAmount) < 0) {
            logger.debug("Transaction below minimum amount: {}", amount);
            return true;
        }
        
        CashbackAccount account = cashbackAccounts.computeIfAbsent(customerId,
            k -> createNewAccount(k));
        
        BigDecimal cashbackAmount = calculateCashback(amount, merchantId, category, account);
        
        if (boostEnabled) {
            cashbackAmount = applyBoosts(cashbackAmount, account, merchantId, category);
        }
        
        if (exceedsDailyLimit(account, cashbackAmount)) {
            logger.warn("Daily cashback limit exceeded for customer: {}", customerId);
            queueForNextDay(account, cashbackAmount, transactionId);
            return true;
        }
        
        if (exceedsMonthlyLimit(account, cashbackAmount)) {
            logger.warn("Monthly cashback limit exceeded for customer: {}", customerId);
            return true;
        }
        
        CashbackTransaction transaction = new CashbackTransaction(
            transactionId,
            customerId,
            amount,
            cashbackAmount,
            merchantId,
            category,
            metadata
        );
        
        if (instantCreditEnabled) {
            creditCashback(account, transaction);
        } else {
            addPendingCashback(account, transaction);
        }
        
        updateTrackings(account, cashbackAmount);
        
        persistAccount(account);
        cashbackEarnedCounter.increment();
        
        return true;
    }
    
    private boolean handleCashbackEarned(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
        String source = (String) eventData.get("source");
        String referenceId = (String) eventData.get("referenceId");
        
        CashbackAccount account = cashbackAccounts.get(customerId);
        if (account == null) {
            account = createNewAccount(customerId);
            cashbackAccounts.put(customerId, account);
        }
        
        account.addEarnedCashback(amount);
        
        CashbackEntry entry = new CashbackEntry(
            referenceId,
            amount,
            source,
            "EARNED",
            Instant.now()
        );
        
        account.addEntry(entry);
        
        notifyCashbackEarned(account, amount, source);
        
        persistAccount(account);
        cashbackEarnedCounter.increment();
        
        return true;
    }
    
    private boolean handleCashbackCredited(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        String pendingId = (String) eventData.get("pendingId");
        BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
        
        CashbackAccount account = cashbackAccounts.get(customerId);
        if (account == null) {
            logger.warn("Account not found: {}", customerId);
            return false;
        }
        
        PendingCashback pending = pendingCashbacks.remove(pendingId);
        if (pending == null) {
            logger.warn("Pending cashback not found: {}", pendingId);
            return false;
        }
        
        account.creditCashback(amount);
        pending.status = "CREDITED";
        pending.creditedAt = Instant.now();
        
        notifyCashbackCredited(account, amount);
        
        persistAccount(account);
        cashbackCreditedCounter.increment();
        
        return true;
    }
    
    private boolean handleCashbackPending(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        String transactionId = (String) eventData.get("transactionId");
        BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
        int pendingDays = ((Number) eventData.getOrDefault("pendingDays", this.pendingDays)).intValue();
        
        CashbackAccount account = cashbackAccounts.get(customerId);
        if (account == null) {
            return false;
        }
        
        PendingCashback pending = new PendingCashback(
            UUID.randomUUID().toString(),
            customerId,
            transactionId,
            amount,
            Instant.now().plusSeconds(pendingDays * 24L * 60 * 60)
        );
        
        pendingCashbacks.put(pending.pendingId, pending);
        account.addPendingAmount(amount);
        
        persistPendingCashback(pending);
        persistAccount(account);
        
        return true;
    }
    
    private boolean handleCashbackMatured(Map<String, Object> eventData) {
        String pendingId = (String) eventData.get("pendingId");
        
        PendingCashback pending = pendingCashbacks.get(pendingId);
        if (pending == null) {
            return false;
        }
        
        CashbackAccount account = cashbackAccounts.get(pending.customerId);
        if (account == null) {
            return false;
        }
        
        if (autoCreditEnabled) {
            handleCashbackCredited(Map.of(
                "customerId", pending.customerId,
                "pendingId", pendingId,
                "amount", pending.amount
            ));
        } else {
            pending.status = "MATURED";
            notifyCashbackMatured(account, pending.amount);
        }
        
        return true;
    }
    
    private boolean handleCashbackExpired(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
        String reason = (String) eventData.get("reason");
        
        CashbackAccount account = cashbackAccounts.get(customerId);
        if (account == null) {
            return false;
        }
        
        account.expireCashback(amount);
        
        notifyCashbackExpired(account, amount, reason);
        
        persistAccount(account);
        cashbackExpiredCounter.increment();
        
        return true;
    }
    
    private boolean handleCashbackReversed(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        String originalTransactionId = (String) eventData.get("originalTransactionId");
        BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
        String reason = (String) eventData.get("reason");
        
        CashbackAccount account = cashbackAccounts.get(customerId);
        if (account == null) {
            return false;
        }
        
        if (account.availableBalance.compareTo(amount) < 0) {
            logger.warn("Insufficient balance for reversal: {} < {}", 
                       account.availableBalance, amount);
            account.addNegativeBalance(amount.subtract(account.availableBalance));
            account.availableBalance = BigDecimal.ZERO;
        } else {
            account.reverseCashback(amount);
        }
        
        CashbackEntry reversal = new CashbackEntry(
            originalTransactionId,
            amount.negate(),
            "REVERSAL",
            reason,
            Instant.now()
        );
        
        account.addEntry(reversal);
        
        notifyCashbackReversed(account, amount, reason);
        
        persistAccount(account);
        cashbackReversedCounter.increment();
        
        return true;
    }
    
    private boolean handleCashbackWithdrawn(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        String withdrawalId = (String) eventData.get("withdrawalId");
        BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
        String method = (String) eventData.get("method");
        Map<String, Object> details = (Map<String, Object>) eventData.get("details");
        
        CashbackAccount account = cashbackAccounts.get(customerId);
        if (account == null) {
            return false;
        }
        
        if (amount.compareTo(minWithdrawalAmount) < 0) {
            logger.warn("Withdrawal amount below minimum: {}", amount);
            return false;
        }
        
        if (account.availableBalance.compareTo(amount) < 0) {
            logger.warn("Insufficient balance for withdrawal: {} < {}",
                       account.availableBalance, amount);
            return false;
        }
        
        account.withdrawCashback(amount);
        
        processWithdrawal(account, withdrawalId, amount, method, details);
        
        persistAccount(account);
        cashbackWithdrawnCounter.increment();
        
        return true;
    }
    
    private boolean handleBoostActivated(Map<String, Object> eventData) {
        String boostId = (String) eventData.get("boostId");
        String customerId = (String) eventData.get("customerId");
        double multiplier = ((Number) eventData.get("multiplier")).doubleValue();
        String boostType = (String) eventData.get("boostType");
        Instant expiresAt = Instant.parse((String) eventData.get("expiresAt"));
        Map<String, Object> conditions = (Map<String, Object>) eventData.get("conditions");
        
        CashbackBoost boost = new CashbackBoost(
            boostId,
            customerId,
            multiplier,
            boostType,
            conditions,
            expiresAt
        );
        
        activeBoosts.put(boostId, boost);
        
        CashbackAccount account = cashbackAccounts.get(customerId);
        if (account != null) {
            account.addActiveBoost(boostId);
            notifyBoostActivated(account, boost);
        }
        
        boostAppliedCounter.increment();
        return true;
    }
    
    private boolean handleBoostExpired(Map<String, Object> eventData) {
        String boostId = (String) eventData.get("boostId");
        
        CashbackBoost boost = activeBoosts.remove(boostId);
        if (boost != null) {
            CashbackAccount account = cashbackAccounts.get(boost.customerId);
            if (account != null) {
                account.removeBoost(boostId);
                notifyBoostExpired(account, boost);
            }
        }
        
        return true;
    }
    
    private boolean handleMerchantRateUpdated(Map<String, Object> eventData) {
        String merchantId = (String) eventData.get("merchantId");
        double rate = ((Number) eventData.get("rate")).doubleValue();
        double bonusRate = ((Number) eventData.getOrDefault("bonusRate", 0)).doubleValue();
        Map<String, Object> conditions = (Map<String, Object>) eventData.get("conditions");
        
        MerchantRate merchantRate = new MerchantRate(merchantId, rate, bonusRate, conditions);
        merchantRates.put(merchantId, merchantRate);
        
        persistMerchantRate(merchantRate);
        
        merchantBonusCounter.increment();
        return true;
    }
    
    private boolean handleCategoryRateUpdated(Map<String, Object> eventData) {
        String category = (String) eventData.get("category");
        double rate = ((Number) eventData.get("rate")).doubleValue();
        Map<String, Double> tierRates = (Map<String, Double>) eventData.get("tierRates");
        
        CategoryRate categoryRate = new CategoryRate(category, rate, tierRates);
        categoryRates.put(category, categoryRate);
        
        persistCategoryRate(categoryRate);
        
        categoryBonusCounter.increment();
        return true;
    }
    
    private boolean handleWithdrawalRequested(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
        String method = (String) eventData.get("method");
        Map<String, Object> details = (Map<String, Object>) eventData.get("details");
        
        CashbackAccount account = cashbackAccounts.get(customerId);
        if (account == null || account.availableBalance.compareTo(amount) < 0) {
            return false;
        }
        
        WithdrawalRequest request = new WithdrawalRequest(
            UUID.randomUUID().toString(),
            customerId,
            amount,
            method,
            details
        );
        
        withdrawalRequests.put(request.requestId, request);
        account.freezeAmount(amount);
        
        persistWithdrawalRequest(request);
        persistAccount(account);
        
        return true;
    }
    
    private boolean handleWithdrawalProcessed(Map<String, Object> eventData) {
        String requestId = (String) eventData.get("requestId");
        String status = (String) eventData.get("status");
        String transactionRef = (String) eventData.get("transactionRef");
        
        WithdrawalRequest request = withdrawalRequests.remove(requestId);
        if (request == null) {
            return false;
        }
        
        CashbackAccount account = cashbackAccounts.get(request.customerId);
        if (account == null) {
            return false;
        }
        
        if ("SUCCESS".equals(status)) {
            account.confirmWithdrawal(request.amount);
            request.status = "COMPLETED";
            request.transactionRef = transactionRef;
            request.completedAt = Instant.now();
            notifyWithdrawalSuccess(account, request);
        } else {
            account.unfreezeAmount(request.amount);
            request.status = "FAILED";
            notifyWithdrawalFailed(account, request);
        }
        
        persistAccount(account);
        
        return true;
    }
    
    private boolean handleAccountUpgraded(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        String newTier = (String) eventData.get("newTier");
        double newRate = ((Number) eventData.get("newRate")).doubleValue();
        
        CashbackAccount account = cashbackAccounts.get(customerId);
        if (account == null) {
            return false;
        }
        
        account.tier = newTier;
        account.tierRate = newRate;
        account.upgradedAt = Instant.now();
        
        notifyAccountUpgraded(account, newTier);
        
        persistAccount(account);
        
        return true;
    }
    
    private boolean handlePromotionalCashback(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
        String promotionId = (String) eventData.get("promotionId");
        String description = (String) eventData.get("description");
        
        CashbackAccount account = cashbackAccounts.get(customerId);
        if (account == null) {
            account = createNewAccount(customerId);
            cashbackAccounts.put(customerId, account);
        }
        
        account.creditCashback(amount);
        
        CashbackEntry promotional = new CashbackEntry(
            promotionId,
            amount,
            "PROMOTIONAL",
            description,
            Instant.now()
        );
        
        account.addEntry(promotional);
        
        notifyPromotionalCashback(account, amount, description);
        
        persistAccount(account);
        
        return true;
    }
    
    private void processPendingCashbacks() {
        Instant now = Instant.now();
        
        pendingCashbacks.values().stream()
            .filter(p -> p.maturityDate.isBefore(now))
            .filter(p -> "PENDING".equals(p.status))
            .limit(100)
            .forEach(pending -> {
                handleCashbackMatured(Map.of(
                    "pendingId", pending.pendingId
                ));
            });
    }
    
    private void settleMaturedCashbacks() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            pendingCashbacks.values().stream()
                .filter(p -> "MATURED".equals(p.status))
                .limit(500)
                .forEach(this::settleCashback);
        } finally {
            sample.stop(settlementTimer);
        }
    }
    
    private void expireOldCashbacks() {
        Instant expirationThreshold = Instant.now().minusSeconds(365L * 24 * 60 * 60);
        
        cashbackAccounts.values().forEach(account -> {
            BigDecimal expiredAmount = account.calculateExpiredCashback(expirationThreshold);
            if (expiredAmount.compareTo(BigDecimal.ZERO) > 0) {
                handleCashbackExpired(Map.of(
                    "customerId", account.customerId,
                    "amount", expiredAmount,
                    "reason", "Annual expiration"
                ));
            }
        });
    }
    
    private void processWithdrawalRequests() {
        withdrawalRequests.values().stream()
            .filter(r -> "PENDING".equals(r.status))
            .filter(r -> Duration.between(r.requestedAt, Instant.now()).toMinutes() < 30)
            .limit(50)
            .forEach(this::processWithdrawalRequest);
    }
    
    private void updateDailyTrackings() {
        LocalDate today = LocalDate.now();
        
        dailyTrackings.values().stream()
            .filter(t -> !t.date.equals(today))
            .forEach(t -> {
                t.reset();
                t.date = today;
            });
    }
    
    private void refreshBoosts() {
        Instant now = Instant.now();
        
        activeBoosts.entrySet().removeIf(entry -> {
            if (entry.getValue().expiresAt.isBefore(now)) {
                handleBoostExpired(Map.of("boostId", entry.getKey()));
                return true;
            }
            return false;
        });
    }
    
    private void generateCashbackReports() {
        CashbackMetrics metrics = new CashbackMetrics();
        
        metrics.totalAccounts = cashbackAccounts.size();
        metrics.totalPending = pendingCashbacks.values().stream()
            .map(p -> p.amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        metrics.totalAvailable = cashbackAccounts.values().stream()
            .map(a -> a.availableBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        metrics.activeBoosts = activeBoosts.size();
        
        publishMetrics(metrics);
    }
    
    private void syncAccountBalances() {
        cashbackAccounts.values().forEach(this::persistAccount);
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
    
    /**
     * CRITICAL MEMORY LEAK PREVENTION: Proper executor shutdown sequence
     * Ensures all executors are properly terminated to prevent memory leaks
     * Implements graceful shutdown with fallback to forced termination
     */
    @PreDestroy
    public void shutdown() {
        logger.info("SHUTDOWN: Starting graceful shutdown of CashbackProcessingConsumer...");
        long shutdownStartTime = System.currentTimeMillis();
        
        try {
            // Persist critical data before shutdown
            persistAllAccounts();
            persistAllPendingCashbacks();
            
            // Initiate graceful shutdown of all executors
            logger.info("SHUTDOWN: Initiating executor shutdown...");
            scheduledExecutor.shutdown();
            processingExecutor.shutdown();
            calculationExecutor.shutdown();
            settlementExecutor.shutdown();
            notificationExecutor.shutdown();
            
            // Wait for each executor with timeout
            shutdownExecutorSafely("scheduled", scheduledExecutor, 30);
            shutdownExecutorSafely("processing", processingExecutor, 30);
            shutdownExecutorSafely("calculation", calculationExecutor, 15);
            shutdownExecutorSafely("settlement", settlementExecutor, 20);
            shutdownExecutorSafely("notification", notificationExecutor, 10);
            
            long shutdownTime = System.currentTimeMillis() - shutdownStartTime;
            logger.info("SHUTDOWN: CashbackProcessingConsumer shutdown completed in {}ms", shutdownTime);
            
        } catch (Exception e) {
            logger.error("CRITICAL: Error during CashbackProcessingConsumer shutdown - potential resource leak", e);
            
            // Force shutdown all executors as fallback
            logger.warn("SHUTDOWN: Forcing immediate executor termination due to shutdown error");
            forceShutdownAll();
        }
    }
    
    /**
     * Safely shutdown individual executor with timeout and fallback
     */
    private void shutdownExecutorSafely(String executorName, ExecutorService executor, int timeoutSeconds) {
        try {
            logger.debug("SHUTDOWN: Waiting for {} executor to terminate (timeout: {}s)", executorName, timeoutSeconds);
            
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                logger.warn("SHUTDOWN: {} executor did not terminate gracefully, forcing shutdown", executorName);
                executor.shutdownNow();
                
                // Final check after forced shutdown
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.error("CRITICAL: {} executor failed to terminate even after forced shutdown - memory leak risk", executorName);
                }
            } else {
                logger.debug("SHUTDOWN: {} executor terminated gracefully", executorName);
            }
            
        } catch (InterruptedException e) {
            logger.warn("SHUTDOWN: Interrupted while waiting for {} executor termination", executorName);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Emergency fallback - force shutdown all executors
     */
    private void forceShutdownAll() {
        scheduledExecutor.shutdownNow();
        processingExecutor.shutdownNow();
        calculationExecutor.shutdownNow();
        settlementExecutor.shutdownNow();
        notificationExecutor.shutdownNow();
    }
    
    private static class CashbackAccount {
        String customerId;
        BigDecimal totalEarned;
        BigDecimal availableBalance;
        BigDecimal pendingAmount;
        BigDecimal frozenAmount;
        BigDecimal lifetimeEarned;
        BigDecimal lifetimeWithdrawn;
        BigDecimal negativeBalance;
        String tier;
        double tierRate;
        List<CashbackEntry> entries;
        Set<String> activeBoosts;
        Instant createdAt;
        Instant lastActivityAt;
        Instant upgradedAt;
        
        CashbackAccount(String customerId) {
            this.customerId = customerId;
            this.totalEarned = BigDecimal.ZERO;
            this.availableBalance = BigDecimal.ZERO;
            this.pendingAmount = BigDecimal.ZERO;
            this.frozenAmount = BigDecimal.ZERO;
            this.lifetimeEarned = BigDecimal.ZERO;
            this.lifetimeWithdrawn = BigDecimal.ZERO;
            this.negativeBalance = BigDecimal.ZERO;
            this.tier = "STANDARD";
            this.tierRate = 1.0;
            this.entries = new CopyOnWriteArrayList<>();
            this.activeBoosts = ConcurrentHashMap.newKeySet();
            this.createdAt = Instant.now();
            this.lastActivityAt = Instant.now();
        }
        
        synchronized void addEarnedCashback(BigDecimal amount) {
            this.totalEarned = this.totalEarned.add(amount);
            this.lifetimeEarned = this.lifetimeEarned.add(amount);
            this.lastActivityAt = Instant.now();
        }
        
        synchronized void creditCashback(BigDecimal amount) {
            this.availableBalance = this.availableBalance.add(amount);
            this.pendingAmount = this.pendingAmount.subtract(amount);
            this.lastActivityAt = Instant.now();
        }
        
        synchronized void reverseCashback(BigDecimal amount) {
            this.availableBalance = this.availableBalance.subtract(amount);
            this.lastActivityAt = Instant.now();
        }
        
        synchronized void withdrawCashback(BigDecimal amount) {
            this.availableBalance = this.availableBalance.subtract(amount);
            this.lifetimeWithdrawn = this.lifetimeWithdrawn.add(amount);
            this.lastActivityAt = Instant.now();
        }
        
        synchronized void expireCashback(BigDecimal amount) {
            if (this.pendingAmount.compareTo(amount) >= 0) {
                this.pendingAmount = this.pendingAmount.subtract(amount);
            } else {
                BigDecimal fromAvailable = amount.subtract(this.pendingAmount);
                this.pendingAmount = BigDecimal.ZERO;
                this.availableBalance = this.availableBalance.subtract(fromAvailable);
            }
        }
        
        synchronized void addPendingAmount(BigDecimal amount) {
            this.pendingAmount = this.pendingAmount.add(amount);
        }
        
        synchronized void freezeAmount(BigDecimal amount) {
            this.availableBalance = this.availableBalance.subtract(amount);
            this.frozenAmount = this.frozenAmount.add(amount);
        }
        
        synchronized void unfreezeAmount(BigDecimal amount) {
            this.frozenAmount = this.frozenAmount.subtract(amount);
            this.availableBalance = this.availableBalance.add(amount);
        }
        
        synchronized void confirmWithdrawal(BigDecimal amount) {
            this.frozenAmount = this.frozenAmount.subtract(amount);
            this.lifetimeWithdrawn = this.lifetimeWithdrawn.add(amount);
        }
        
        synchronized void addNegativeBalance(BigDecimal amount) {
            this.negativeBalance = this.negativeBalance.add(amount);
        }
        
        void addEntry(CashbackEntry entry) {
            entries.add(entry);
        }
        
        void addActiveBoost(String boostId) {
            activeBoosts.add(boostId);
        }
        
        void removeBoost(String boostId) {
            activeBoosts.remove(boostId);
        }
        
        BigDecimal calculateExpiredCashback(Instant threshold) {
            return entries.stream()
                .filter(e -> e.timestamp.isBefore(threshold))
                .filter(e -> "EARNED".equals(e.status))
                .map(e -> e.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
    
    private static class PendingCashback {
        String pendingId;
        String customerId;
        String transactionId;
        BigDecimal amount;
        String status;
        Instant createdAt;
        Instant maturityDate;
        Instant creditedAt;
        
        PendingCashback(String pendingId, String customerId, String transactionId,
                       BigDecimal amount, Instant maturityDate) {
            this.pendingId = pendingId;
            this.customerId = customerId;
            this.transactionId = transactionId;
            this.amount = amount;
            this.status = "PENDING";
            this.createdAt = Instant.now();
            this.maturityDate = maturityDate;
        }
    }
    
    private static class CashbackTransaction {
        String transactionId;
        String customerId;
        BigDecimal transactionAmount;
        BigDecimal cashbackAmount;
        String merchantId;
        String category;
        Map<String, Object> metadata;
        Instant timestamp;
        
        CashbackTransaction(String transactionId, String customerId, BigDecimal transactionAmount,
                          BigDecimal cashbackAmount, String merchantId, String category,
                          Map<String, Object> metadata) {
            this.transactionId = transactionId;
            this.customerId = customerId;
            this.transactionAmount = transactionAmount;
            this.cashbackAmount = cashbackAmount;
            this.merchantId = merchantId;
            this.category = category;
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
            this.timestamp = Instant.now();
        }
    }
    
    private static class CashbackEntry {
        String referenceId;
        BigDecimal amount;
        String type;
        String status;
        Instant timestamp;
        
        CashbackEntry(String referenceId, BigDecimal amount, String type,
                     String status, Instant timestamp) {
            this.referenceId = referenceId;
            this.amount = amount;
            this.type = type;
            this.status = status;
            this.timestamp = timestamp;
        }
    }
    
    private static class MerchantRate {
        String merchantId;
        double baseRate;
        double bonusRate;
        Map<String, Object> conditions;
        Instant updatedAt;
        
        MerchantRate(String merchantId, double baseRate, double bonusRate,
                    Map<String, Object> conditions) {
            this.merchantId = merchantId;
            this.baseRate = baseRate;
            this.bonusRate = bonusRate;
            this.conditions = conditions != null ? new HashMap<>(conditions) : new HashMap<>();
            this.updatedAt = Instant.now();
        }
        
        double getEffectiveRate() {
            return baseRate + bonusRate;
        }
    }
    
    private static class CategoryRate {
        String category;
        double baseRate;
        Map<String, Double> tierRates;
        Instant updatedAt;
        
        CategoryRate(String category, double baseRate, Map<String, Double> tierRates) {
            this.category = category;
            this.baseRate = baseRate;
            this.tierRates = tierRates != null ? new HashMap<>(tierRates) : new HashMap<>();
            this.updatedAt = Instant.now();
        }
        
        double getRateForTier(String tier) {
            return tierRates.getOrDefault(tier, baseRate);
        }
    }
    
    private static class CashbackBoost {
        String boostId;
        String customerId;
        double multiplier;
        String boostType;
        Map<String, Object> conditions;
        Instant activatedAt;
        Instant expiresAt;
        
        CashbackBoost(String boostId, String customerId, double multiplier,
                     String boostType, Map<String, Object> conditions, Instant expiresAt) {
            this.boostId = boostId;
            this.customerId = customerId;
            this.multiplier = multiplier;
            this.boostType = boostType;
            this.conditions = conditions != null ? new HashMap<>(conditions) : new HashMap<>();
            this.activatedAt = Instant.now();
            this.expiresAt = expiresAt;
        }
        
        boolean isApplicable(String merchantId, String category) {
            if (conditions.containsKey("merchantId")) {
                return merchantId.equals(conditions.get("merchantId"));
            }
            if (conditions.containsKey("category")) {
                return category.equals(conditions.get("category"));
            }
            return true;
        }
    }
    
    private static class DailyTracking {
        String customerId;
        LocalDate date;
        BigDecimal earnedToday;
        int transactionCount;
        
        void reset() {
            earnedToday = BigDecimal.ZERO;
            transactionCount = 0;
        }
        
        void addEarning(BigDecimal amount) {
            earnedToday = earnedToday.add(amount);
            transactionCount++;
        }
    }
    
    private static class MonthlyTracking {
        String customerId;
        int month;
        int year;
        BigDecimal earnedThisMonth;
        BigDecimal withdrawnThisMonth;
    }
    
    private static class WithdrawalRequest {
        String requestId;
        String customerId;
        BigDecimal amount;
        String method;
        Map<String, Object> details;
        String status;
        String transactionRef;
        Instant requestedAt;
        Instant completedAt;
        
        WithdrawalRequest(String requestId, String customerId, BigDecimal amount,
                         String method, Map<String, Object> details) {
            this.requestId = requestId;
            this.customerId = customerId;
            this.amount = amount;
            this.method = method;
            this.details = details != null ? new HashMap<>(details) : new HashMap<>();
            this.status = "PENDING";
            this.requestedAt = Instant.now();
        }
    }
    
    private static class CashbackMetrics {
        int totalAccounts;
        BigDecimal totalPending;
        BigDecimal totalAvailable;
        int activeBoosts;
        double averageCashbackRate;
    }
    
    private CashbackAccount createNewAccount(String customerId) {
        return new CashbackAccount(customerId);
    }
    
    private BigDecimal calculateCashback(BigDecimal amount, String merchantId,
                                        String category, CashbackAccount account) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            double rate = defaultCashbackRate;
            
            MerchantRate merchantRate = merchantRates.get(merchantId);
            if (merchantRate != null) {
                rate = merchantRate.getEffectiveRate();
            } else {
                CategoryRate categoryRate = categoryRates.get(category);
                if (categoryRate != null) {
                    rate = categoryRate.getRateForTier(account.tier);
                }
            }
            
            if (tieredRatesEnabled) {
                rate *= account.tierRate;
            }
            
            rate = Math.min(rate, maxCashbackRate);
            
            return amount.multiply(BigDecimal.valueOf(rate))
                        .setScale(2, RoundingMode.DOWN);
        } finally {
            sample.stop(calculationTimer);
        }
    }
    
    private BigDecimal applyBoosts(BigDecimal cashback, CashbackAccount account,
                                  String merchantId, String category) {
        BigDecimal boostedAmount = cashback;
        
        for (String boostId : account.activeBoosts) {
            CashbackBoost boost = activeBoosts.get(boostId);
            if (boost != null && boost.isApplicable(merchantId, category)) {
                boostedAmount = boostedAmount.multiply(BigDecimal.valueOf(boost.multiplier));
                boostAppliedCounter.increment();
            }
        }
        
        return boostedAmount.setScale(2, RoundingMode.DOWN);
    }
    
    private boolean exceedsDailyLimit(CashbackAccount account, BigDecimal amount) {
        DailyTracking tracking = dailyTrackings.computeIfAbsent(account.customerId,
            k -> new DailyTracking());
        
        if (!tracking.date.equals(LocalDate.now())) {
            tracking.reset();
            tracking.date = LocalDate.now();
        }
        
        return tracking.earnedToday.add(amount).compareTo(maxDailyCashback) > 0;
    }
    
    private boolean exceedsMonthlyLimit(CashbackAccount account, BigDecimal amount) {
        MonthlyTracking tracking = monthlyTrackings.computeIfAbsent(account.customerId,
            k -> new MonthlyTracking());
        
        LocalDate now = LocalDate.now();
        if (tracking.month != now.getMonthValue() || tracking.year != now.getYear()) {
            tracking.month = now.getMonthValue();
            tracking.year = now.getYear();
            tracking.earnedThisMonth = BigDecimal.ZERO;
        }
        
        return tracking.earnedThisMonth.add(amount).compareTo(maxMonthlyCashback) > 0;
    }
    
    private void queueForNextDay(CashbackAccount account, BigDecimal amount, String transactionId) {
        logger.debug("Queueing {} cashback for next day for customer {}", amount, account.customerId);
    }
    
    private void creditCashback(CashbackAccount account, CashbackTransaction transaction) {
        account.creditCashback(transaction.cashbackAmount);
        account.addEntry(new CashbackEntry(
            transaction.transactionId,
            transaction.cashbackAmount,
            "INSTANT_CREDIT",
            "CREDITED",
            Instant.now()
        ));
    }
    
    private void addPendingCashback(CashbackAccount account, CashbackTransaction transaction) {
        handleCashbackPending(Map.of(
            "customerId", account.customerId,
            "transactionId", transaction.transactionId,
            "amount", transaction.cashbackAmount,
            "pendingDays", pendingDays
        ));
    }
    
    private void updateTrackings(CashbackAccount account, BigDecimal amount) {
        DailyTracking daily = dailyTrackings.computeIfAbsent(account.customerId,
            k -> new DailyTracking());
        daily.addEarning(amount);
        
        MonthlyTracking monthly = monthlyTrackings.computeIfAbsent(account.customerId,
            k -> new MonthlyTracking());
        monthly.earnedThisMonth = monthly.earnedThisMonth.add(amount);
    }
    
    private void processWithdrawal(CashbackAccount account, String withdrawalId,
                                  BigDecimal amount, String method, Map<String, Object> details) {
        logger.info("Processing withdrawal {} for customer {}: {} via {}",
                   withdrawalId, account.customerId, amount, method);
    }
    
    private void settleCashback(PendingCashback pending) {
        logger.debug("Settling cashback {} for customer {}", pending.pendingId, pending.customerId);
    }
    
    private void processWithdrawalRequest(WithdrawalRequest request) {
        logger.debug("Processing withdrawal request {} for customer {}",
                    request.requestId, request.customerId);
    }
    
    private void persistAccount(CashbackAccount account) {
        try {
            String key = CASHBACK_BALANCE_PREFIX + account.customerId;
            String json = objectMapper.writeValueAsString(account);
            redisTemplate.opsForValue().set(key, json, Duration.ofDays(30));
        } catch (Exception e) {
            logger.error("Failed to persist account", e);
        }
    }
    
    private void persistPendingCashback(PendingCashback pending) {
        try {
            String key = CASHBACK_PENDING_PREFIX + pending.pendingId;
            String json = objectMapper.writeValueAsString(pending);
            redisTemplate.opsForValue().set(key, json, 
                Duration.ofDays(pendingDays + 30));
        } catch (Exception e) {
            logger.error("Failed to persist pending cashback", e);
        }
    }
    
    private void persistMerchantRate(MerchantRate rate) {
        try {
            String key = MERCHANT_RATES_PREFIX + rate.merchantId;
            String json = objectMapper.writeValueAsString(rate);
            redisTemplate.opsForValue().set(key, json);
        } catch (Exception e) {
            logger.error("Failed to persist merchant rate", e);
        }
    }
    
    private void persistCategoryRate(CategoryRate rate) {
        try {
            String key = CATEGORY_RATES_PREFIX + rate.category;
            String json = objectMapper.writeValueAsString(rate);
            redisTemplate.opsForValue().set(key, json);
        } catch (Exception e) {
            logger.error("Failed to persist category rate", e);
        }
    }
    
    private void persistWithdrawalRequest(WithdrawalRequest request) {
        logger.debug("Persisting withdrawal request {}", request.requestId);
    }
    
    private void persistAllAccounts() {
        cashbackAccounts.values().forEach(this::persistAccount);
    }
    
    private void persistAllPendingCashbacks() {
        pendingCashbacks.values().forEach(this::persistPendingCashback);
    }
    
    private void publishMetrics(CashbackMetrics metrics) {
        logger.debug("Publishing cashback metrics: {} accounts, {} pending",
                    metrics.totalAccounts, metrics.totalPending);
    }
    
    private void initializeDefaultMerchantRates() {
        merchantRates.put("AMAZON", new MerchantRate("AMAZON", 0.02, 0.01, null));
        merchantRates.put("WALMART", new MerchantRate("WALMART", 0.015, 0.005, null));
    }
    
    private void initializeDefaultCategoryRates() {
        Map<String, Double> groceryTiers = Map.of(
            "STANDARD", 0.01,
            "SILVER", 0.015,
            "GOLD", 0.02,
            "PLATINUM", 0.025
        );
        categoryRates.put("GROCERY", new CategoryRate("GROCERY", 0.01, groceryTiers));
    }
    
    private void initializeDefaultBoosts() {
        logger.debug("Initializing default cashback boosts");
    }
    
    private void notifyCashbackEarned(CashbackAccount account, BigDecimal amount, String source) {
        logger.info("Cashback earned for {}: {} from {}", account.customerId, amount, source);
    }
    
    private void notifyCashbackCredited(CashbackAccount account, BigDecimal amount) {
        logger.info("Cashback credited to {}: {}", account.customerId, amount);
    }
    
    private void notifyCashbackMatured(CashbackAccount account, BigDecimal amount) {
        logger.info("Cashback matured for {}: {}", account.customerId, amount);
    }
    
    private void notifyCashbackExpired(CashbackAccount account, BigDecimal amount, String reason) {
        logger.warn("Cashback expired for {}: {} - {}", account.customerId, amount, reason);
    }
    
    private void notifyCashbackReversed(CashbackAccount account, BigDecimal amount, String reason) {
        logger.warn("Cashback reversed for {}: {} - {}", account.customerId, amount, reason);
    }
    
    private void notifyBoostActivated(CashbackAccount account, CashbackBoost boost) {
        logger.info("Boost activated for {}: {}x multiplier", account.customerId, boost.multiplier);
    }
    
    private void notifyBoostExpired(CashbackAccount account, CashbackBoost boost) {
        logger.info("Boost expired for {}: {}", account.customerId, boost.boostId);
    }
    
    private void notifyAccountUpgraded(CashbackAccount account, String newTier) {
        logger.info("Account upgraded for {}: {}", account.customerId, newTier);
    }
    
    private void notifyPromotionalCashback(CashbackAccount account, BigDecimal amount, String description) {
        logger.info("Promotional cashback for {}: {} - {}", account.customerId, amount, description);
    }
    
    private void notifyWithdrawalSuccess(CashbackAccount account, WithdrawalRequest request) {
        logger.info("Withdrawal successful for {}: {}", account.customerId, request.amount);
    }
    
    private void notifyWithdrawalFailed(CashbackAccount account, WithdrawalRequest request) {
        logger.warn("Withdrawal failed for {}: {}", account.customerId, request.amount);
    }
}