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
import org.springframework.beans.factory.annotation.Autowired;
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
import java.security.MessageDigest;
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
public class ReferralTrackingConsumer {
    private static final Logger logger = LoggerFactory.getLogger(ReferralTrackingConsumer.class);
    
    private static final String TOPIC = "referral-tracking";
    private static final String DLQ_TOPIC = "referral-tracking-dlq";
    private static final String CONSUMER_GROUP = "referral-tracking-consumer-group";
    private static final String REFERRAL_CODE_PREFIX = "referral:code:";
    private static final String REFERRAL_CHAIN_PREFIX = "referral:chain:";
    private static final String REFERRAL_STATS_PREFIX = "referral:stats:";
    private static final String CAMPAIGN_PREFIX = "campaign:referral:";
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final RedisTemplate<String, String> redisTemplate;
    private final CacheManager cacheManager;

    public ReferralTrackingConsumer(KafkaTemplate<String, String> kafkaTemplate,
                                   ObjectMapper objectMapper,
                                   MeterRegistry meterRegistry,
                                   ApplicationEventPublisher eventPublisher,
                                   RedisTemplate<String, String> redisTemplate,
                                   CacheManager cacheManager) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.eventPublisher = eventPublisher;
        this.redisTemplate = redisTemplate;
        this.cacheManager = cacheManager;
    }
    
    @Value("${rewards.referral.code.length:8}")
    private int referralCodeLength;
    
    @Value("${rewards.referral.max-depth:3}")
    private int maxReferralDepth;
    
    @Value("${rewards.referral.referee.bonus:10.00}")
    private BigDecimal refereeBonus;
    
    @Value("${rewards.referral.referrer.bonus:20.00}")
    private BigDecimal referrerBonus;
    
    @Value("${rewards.referral.multi-tier.enabled:true}")
    private boolean multiTierEnabled;
    
    @Value("${rewards.referral.tier2.rate:0.10}")
    private double tier2Rate;
    
    @Value("${rewards.referral.tier3.rate:0.05}")
    private double tier3Rate;
    
    @Value("${rewards.referral.expiration.days:90}")
    private int expirationDays;
    
    @Value("${rewards.referral.max-uses:0}")
    private int maxUsesPerCode;
    
    @Value("${rewards.referral.qualification.days:30}")
    private int qualificationDays;
    
    @Value("${rewards.referral.min-transaction:50.00}")
    private BigDecimal minTransactionAmount;
    
    @Value("${rewards.referral.fraud-check.enabled:true}")
    private boolean fraudCheckEnabled;
    
    private final Map<String, ReferralAccount> referralAccounts = new ConcurrentHashMap<>();
    private final Map<String, ReferralCode> referralCodes = new ConcurrentHashMap<>();
    private final Map<String, ReferralChain> referralChains = new ConcurrentHashMap<>();
    private final Map<String, ReferralCampaign> campaigns = new ConcurrentHashMap<>();
    private final Map<String, ReferralStats> referralStats = new ConcurrentHashMap<>();
    private final Map<String, PendingReferral> pendingReferrals = new ConcurrentHashMap<>();
    private final Map<String, ReferralNetwork> networks = new ConcurrentHashMap<>();
    private final Map<String, FraudIndicator> fraudIndicators = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(8);
    private final ExecutorService processingExecutor = Executors.newFixedThreadPool(12);
    private final ExecutorService validationExecutor = Executors.newFixedThreadPool(6);
    private final ExecutorService rewardExecutor = Executors.newFixedThreadPool(6);
    private final ExecutorService analyticsExecutor = Executors.newFixedThreadPool(4);
    
    private Counter referralsCreatedCounter;
    private Counter referralsCompletedCounter;
    private Counter referralsExpiredCounter;
    private Counter referralRewardsCounter;
    private Counter multiTierRewardsCounter;
    private Counter fraudDetectedCounter;
    private Counter campaignReferralsCounter;
    private Counter errorCounter;
    private Timer processingTimer;
    private Timer validationTimer;
    private Timer rewardCalculationTimer;
    
    private CircuitBreaker circuitBreaker;
    private Retry retry;
    
    @PostConstruct
    public void init() {
        initializeMetrics();
        initializeResilience();
        initializeBackgroundTasks();
        loadReferralAccounts();
        loadActiveCampaigns();
        initializeNetworks();
        logger.info("ReferralTrackingConsumer initialized with multi-tier referral system");
    }
    
    private void initializeMetrics() {
        referralsCreatedCounter = Counter.builder("referral.created")
                .description("Total referrals created")
                .register(meterRegistry);
                
        referralsCompletedCounter = Counter.builder("referral.completed")
                .description("Total referrals completed")
                .register(meterRegistry);
                
        referralsExpiredCounter = Counter.builder("referral.expired")
                .description("Total referrals expired")
                .register(meterRegistry);
                
        referralRewardsCounter = Counter.builder("referral.rewards")
                .description("Total referral rewards issued")
                .register(meterRegistry);
                
        multiTierRewardsCounter = Counter.builder("referral.multitier.rewards")
                .description("Multi-tier rewards issued")
                .register(meterRegistry);
                
        fraudDetectedCounter = Counter.builder("referral.fraud.detected")
                .description("Fraudulent referrals detected")
                .register(meterRegistry);
                
        campaignReferralsCounter = Counter.builder("referral.campaign")
                .description("Campaign-specific referrals")
                .register(meterRegistry);
                
        errorCounter = Counter.builder("referral.errors")
                .description("Total processing errors")
                .register(meterRegistry);
                
        processingTimer = Timer.builder("referral.processing.time")
                .description("Time to process referral events")
                .register(meterRegistry);
                
        validationTimer = Timer.builder("referral.validation.time")
                .description("Time to validate referrals")
                .register(meterRegistry);
                
        rewardCalculationTimer = Timer.builder("referral.reward.calculation.time")
                .description("Time to calculate rewards")
                .register(meterRegistry);
                
        Gauge.builder("referral.accounts.active", referralAccounts, Map::size)
                .description("Number of active referral accounts")
                .register(meterRegistry);
                
        Gauge.builder("referral.codes.active", referralCodes, map -> 
                (int) map.values().stream().filter(c -> c.isActive()).count())
                .description("Number of active referral codes")
                .register(meterRegistry);
                
        Gauge.builder("referral.pending", pendingReferrals, Map::size)
                .description("Number of pending referrals")
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
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("referral-processor");
        
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(2))
                .retryExceptions(Exception.class)
                .build();
                
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        retry = retryRegistry.retry("referral-retry");
    }
    
    private void initializeBackgroundTasks() {
        scheduledExecutor.scheduleWithFixedDelay(this::processPendingReferrals, 0, 5, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::checkQualifications, 0, 1, TimeUnit.HOURS);
        scheduledExecutor.scheduleWithFixedDelay(this::expireOldReferrals, 0, 24, TimeUnit.HOURS);
        scheduledExecutor.scheduleWithFixedDelay(this::updateReferralStats, 0, 15, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::detectFraudPatterns, 0, 30, TimeUnit.MINUTES);
        scheduledExecutor.scheduleWithFixedDelay(this::calculateNetworkMetrics, 0, 1, TimeUnit.HOURS);
        scheduledExecutor.scheduleWithFixedDelay(this::generateReferralReports, 0, 6, TimeUnit.HOURS);
        scheduledExecutor.scheduleWithFixedDelay(this::cleanupExpiredCodes, 0, 24, TimeUnit.HOURS);
    }
    
    private void loadReferralAccounts() {
        try {
            Set<String> accountKeys = redisTemplate.keys(REFERRAL_CODE_PREFIX + "*");
            if (accountKeys != null && !accountKeys.isEmpty()) {
                for (String key : accountKeys) {
                    String codeJson = redisTemplate.opsForValue().get(key);
                    if (codeJson != null) {
                        ReferralCode code = objectMapper.readValue(codeJson, ReferralCode.class);
                        referralCodes.put(code.code, code);
                    }
                }
                logger.info("Loaded {} referral codes from Redis", referralCodes.size());
            }
        } catch (Exception e) {
            logger.error("Failed to load referral codes", e);
        }
    }
    
    private void loadActiveCampaigns() {
        initializeDefaultCampaigns();
        logger.info("Referral campaigns initialized");
    }
    
    private void initializeNetworks() {
        logger.info("Referral networks initialized");
    }
    
    @KafkaListener(topics = TOPIC, groupId = CONSUMER_GROUP)
    public void processReferralEvent(@Payload String message,
                                    @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                    @Header(KafkaHeaders.OFFSET) long offset,
                                    @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
                                    Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        MDC.put("referral.topic", topic);
        MDC.put("referral.partition", String.valueOf(partition));
        MDC.put("referral.offset", String.valueOf(offset));
        
        try {
            logger.debug("Processing referral event from partition {} offset {}", partition, offset);
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            String eventType = (String) eventData.get("eventType");
            
            MDC.put("event.type", eventType);
            
            Supplier<Boolean> eventProcessor = () -> {
                try {
                    switch (eventType) {
                        case "REFERRAL_CODE_CREATED":
                            return handleReferralCodeCreated(eventData);
                        case "REFERRAL_CODE_USED":
                            return handleReferralCodeUsed(eventData);
                        case "REFERRAL_COMPLETED":
                            return handleReferralCompleted(eventData);
                        case "REFERRAL_QUALIFIED":
                            return handleReferralQualified(eventData);
                        case "REFERRAL_EXPIRED":
                            return handleReferralExpired(eventData);
                        case "REFERRAL_REWARD_ISSUED":
                            return handleReferralRewardIssued(eventData);
                        case "MULTI_TIER_REWARD":
                            return handleMultiTierReward(eventData);
                        case "REFERRAL_LINK_CLICKED":
                            return handleReferralLinkClicked(eventData);
                        case "REFERRAL_CONVERSION":
                            return handleReferralConversion(eventData);
                        case "CAMPAIGN_ENROLLED":
                            return handleCampaignEnrolled(eventData);
                        case "CAMPAIGN_MILESTONE":
                            return handleCampaignMilestone(eventData);
                        case "NETWORK_FORMED":
                            return handleNetworkFormed(eventData);
                        case "FRAUD_DETECTED":
                            return handleFraudDetected(eventData);
                        case "CODE_SHARED":
                            return handleCodeShared(eventData);
                        case "REFERRAL_INVALIDATED":
                            return handleReferralInvalidated(eventData);
                        case "BONUS_MULTIPLIER_APPLIED":
                            return handleBonusMultiplierApplied(eventData);
                        default:
                            logger.warn("Unknown event type: {}", eventType);
                            return false;
                    }
                } catch (Exception e) {
                    logger.error("Error processing referral event", e);
                    errorCounter.increment();
                    return false;
                }
            };
            
            Boolean result = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(circuitBreaker, eventProcessor)).get();
            
            if (result) {
                acknowledgment.acknowledge();
                logger.debug("Referral event processed successfully");
            } else {
                sendToDlq(message, "Processing failed");
                acknowledgment.acknowledge();
            }
            
        } catch (Exception e) {
            logger.error("Failed to process referral event", e);
            errorCounter.increment();
            sendToDlq(message, e.getMessage());
            acknowledgment.acknowledge();
        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }
    
    private boolean handleReferralCodeCreated(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        String campaignId = (String) eventData.get("campaignId");
        Map<String, Object> customization = (Map<String, Object>) eventData.get("customization");
        
        ReferralAccount account = referralAccounts.computeIfAbsent(customerId,
            k -> createNewAccount(k));
        
        String code = generateUniqueCode(customerId);
        
        ReferralCode referralCode = new ReferralCode(
            code,
            customerId,
            campaignId,
            maxUsesPerCode,
            expirationDays
        );
        
        if (customization != null) {
            referralCode.applyCustomization(customization);
        }
        
        referralCodes.put(code, referralCode);
        account.setPrimaryCode(code);
        
        if (campaignId != null) {
            ReferralCampaign campaign = campaigns.get(campaignId);
            if (campaign != null) {
                campaign.addParticipant(customerId);
                referralCode.campaignBonus = campaign.getBonusMultiplier();
            }
        }
        
        persistReferralCode(referralCode);
        persistAccount(account);
        
        notifyCodeCreated(account, referralCode);
        
        referralsCreatedCounter.increment();
        return true;
    }
    
    private boolean handleReferralCodeUsed(Map<String, Object> eventData) {
        String code = (String) eventData.get("code");
        String refereeId = (String) eventData.get("refereeId");
        String channel = (String) eventData.get("channel");
        Map<String, Object> context = (Map<String, Object>) eventData.get("context");
        
        ReferralCode referralCode = referralCodes.get(code);
        if (referralCode == null) {
            logger.warn("Referral code not found: {}", code);
            return false;
        }
        
        if (!referralCode.isActive()) {
            logger.warn("Referral code inactive or expired: {}", code);
            return false;
        }
        
        if (referralCode.hasReachedLimit()) {
            logger.warn("Referral code usage limit reached: {}", code);
            return false;
        }
        
        if (fraudCheckEnabled && detectFraud(referralCode.ownerId, refereeId, context)) {
            logger.warn("Fraud detected for referral: {} -> {}", referralCode.ownerId, refereeId);
            handleFraudDetected(Map.of(
                "referrerId", referralCode.ownerId,
                "refereeId", refereeId,
                "code", code,
                "reason", "Automated fraud detection"
            ));
            return false;
        }
        
        PendingReferral pending = new PendingReferral(
            UUID.randomUUID().toString(),
            referralCode.ownerId,
            refereeId,
            code,
            channel
        );
        
        pendingReferrals.put(pending.referralId, pending);
        referralCode.incrementUsage();
        
        createReferralChain(referralCode.ownerId, refereeId);
        
        ReferralAccount referrerAccount = referralAccounts.get(referralCode.ownerId);
        if (referrerAccount != null) {
            referrerAccount.addPendingReferral(pending.referralId);
        }
        
        notifyReferralStarted(referralCode.ownerId, refereeId);
        
        persistPendingReferral(pending);
        persistReferralCode(referralCode);
        
        return true;
    }
    
    private boolean handleReferralCompleted(Map<String, Object> eventData) {
        String referralId = (String) eventData.get("referralId");
        String transactionId = (String) eventData.get("transactionId");
        BigDecimal transactionAmount = new BigDecimal(eventData.get("transactionAmount").toString());
        
        PendingReferral pending = pendingReferrals.get(referralId);
        if (pending == null) {
            logger.warn("Pending referral not found: {}", referralId);
            return false;
        }
        
        if (transactionAmount.compareTo(minTransactionAmount) < 0) {
            logger.info("Transaction below minimum for referral qualification: {}", transactionAmount);
            pending.status = "PENDING_QUALIFICATION";
            return true;
        }
        
        pending.status = "COMPLETED";
        pending.completedAt = Instant.now();
        pending.qualifyingTransactionId = transactionId;
        pending.transactionAmount = transactionAmount;
        
        if (Duration.between(pending.createdAt, pending.completedAt).toDays() <= qualificationDays) {
            handleReferralQualified(Map.of(
                "referralId", referralId,
                "referrerId", pending.referrerId,
                "refereeId", pending.refereeId,
                "transactionAmount", transactionAmount
            ));
        }
        
        updateReferralStats(pending.referrerId, pending.refereeId, transactionAmount);
        
        referralsCompletedCounter.increment();
        return true;
    }
    
    private boolean handleReferralQualified(Map<String, Object> eventData) {
        String referralId = (String) eventData.get("referralId");
        String referrerId = (String) eventData.get("referrerId");
        String refereeId = (String) eventData.get("refereeId");
        BigDecimal transactionAmount = new BigDecimal(eventData.get("transactionAmount").toString());
        
        ReferralAccount referrerAccount = referralAccounts.get(referrerId);
        if (referrerAccount == null) {
            logger.warn("Referrer account not found: {}", referrerId);
            return false;
        }
        
        ReferralAccount refereeAccount = referralAccounts.computeIfAbsent(refereeId,
            k -> createNewAccount(k));
        
        BigDecimal referrerReward = calculateReferrerReward(transactionAmount, referrerAccount);
        BigDecimal refereeReward = calculateRefereeReward(transactionAmount, refereeAccount);
        
        issueReward(referrerAccount, referrerReward, "REFERRAL_BONUS");
        issueReward(refereeAccount, refereeReward, "WELCOME_BONUS");
        
        referrerAccount.incrementSuccessfulReferrals();
        refereeAccount.setReferredBy(referrerId);
        
        if (multiTierEnabled) {
            processMultiTierRewards(referrerId, transactionAmount);
        }
        
        PendingReferral pending = pendingReferrals.get(referralId);
        if (pending != null) {
            pending.status = "QUALIFIED";
            pending.referrerReward = referrerReward;
            pending.refereeReward = refereeReward;
        }
        
        updateNetworkStrength(referrerId, refereeId);
        
        persistAccount(referrerAccount);
        persistAccount(refereeAccount);
        
        referralRewardsCounter.increment();
        return true;
    }
    
    private boolean handleReferralExpired(Map<String, Object> eventData) {
        String referralId = (String) eventData.get("referralId");
        String reason = (String) eventData.get("reason");
        
        PendingReferral pending = pendingReferrals.remove(referralId);
        if (pending == null) {
            return false;
        }
        
        pending.status = "EXPIRED";
        pending.expiredAt = Instant.now();
        
        ReferralAccount referrerAccount = referralAccounts.get(pending.referrerId);
        if (referrerAccount != null) {
            referrerAccount.removePendingReferral(referralId);
            referrerAccount.incrementExpiredReferrals();
        }
        
        notifyReferralExpired(pending.referrerId, pending.refereeId, reason);
        
        referralsExpiredCounter.increment();
        return true;
    }
    
    private boolean handleReferralRewardIssued(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
        String type = (String) eventData.get("type");
        String referralId = (String) eventData.get("referralId");
        
        ReferralAccount account = referralAccounts.get(customerId);
        if (account == null) {
            return false;
        }
        
        account.addReward(new ReferralReward(referralId, amount, type));
        account.totalEarned = account.totalEarned.add(amount);
        
        notifyRewardIssued(account, amount, type);
        
        persistAccount(account);
        
        return true;
    }
    
    private boolean handleMultiTierReward(Map<String, Object> eventData) {
        String beneficiaryId = (String) eventData.get("beneficiaryId");
        BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
        int tier = ((Number) eventData.get("tier")).intValue();
        String originalReferralId = (String) eventData.get("originalReferralId");
        
        ReferralAccount account = referralAccounts.get(beneficiaryId);
        if (account == null) {
            return false;
        }
        
        account.addMultiTierReward(tier, amount);
        
        notifyMultiTierReward(account, amount, tier);
        
        persistAccount(account);
        
        multiTierRewardsCounter.increment();
        return true;
    }
    
    private boolean handleReferralLinkClicked(Map<String, Object> eventData) {
        String code = (String) eventData.get("code");
        String ipAddress = (String) eventData.get("ipAddress");
        String userAgent = (String) eventData.get("userAgent");
        Map<String, Object> metadata = (Map<String, Object>) eventData.get("metadata");
        
        ReferralCode referralCode = referralCodes.get(code);
        if (referralCode == null) {
            return false;
        }
        
        referralCode.incrementClicks();
        
        ReferralStats stats = referralStats.computeIfAbsent(referralCode.ownerId,
            k -> new ReferralStats(k));
        stats.addClick(ipAddress, userAgent);
        
        persistReferralCode(referralCode);
        
        return true;
    }
    
    private boolean handleReferralConversion(Map<String, Object> eventData) {
        String code = (String) eventData.get("code");
        String refereeId = (String) eventData.get("refereeId");
        BigDecimal conversionValue = new BigDecimal(eventData.get("conversionValue").toString());
        
        ReferralCode referralCode = referralCodes.get(code);
        if (referralCode == null) {
            return false;
        }
        
        referralCode.addConversion(refereeId, conversionValue);
        
        ReferralStats stats = referralStats.get(referralCode.ownerId);
        if (stats != null) {
            stats.addConversion(conversionValue);
        }
        
        persistReferralCode(referralCode);
        
        return true;
    }
    
    private boolean handleCampaignEnrolled(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        String campaignId = (String) eventData.get("campaignId");
        Map<String, Object> enrollmentData = (Map<String, Object>) eventData.get("enrollmentData");
        
        ReferralCampaign campaign = campaigns.get(campaignId);
        if (campaign == null) {
            logger.warn("Campaign not found: {}", campaignId);
            return false;
        }
        
        ReferralAccount account = referralAccounts.computeIfAbsent(customerId,
            k -> createNewAccount(k));
        
        campaign.addParticipant(customerId);
        account.enrollInCampaign(campaignId);
        
        if (campaign.hasWelcomeBonus()) {
            issueReward(account, campaign.getWelcomeBonus(), "CAMPAIGN_WELCOME");
        }
        
        persistAccount(account);
        
        campaignReferralsCounter.increment();
        return true;
    }
    
    private boolean handleCampaignMilestone(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        String campaignId = (String) eventData.get("campaignId");
        String milestone = (String) eventData.get("milestone");
        BigDecimal reward = new BigDecimal(eventData.get("reward").toString());
        
        ReferralAccount account = referralAccounts.get(customerId);
        if (account == null) {
            return false;
        }
        
        account.addCampaignMilestone(campaignId, milestone, reward);
        issueReward(account, reward, "CAMPAIGN_MILESTONE");
        
        notifyMilestoneReached(account, milestone, reward);
        
        persistAccount(account);
        
        return true;
    }
    
    private boolean handleNetworkFormed(Map<String, Object> eventData) {
        String networkId = (String) eventData.get("networkId");
        String founderId = (String) eventData.get("founderId");
        List<String> memberIds = (List<String>) eventData.get("memberIds");
        
        ReferralNetwork network = new ReferralNetwork(networkId, founderId);
        memberIds.forEach(network::addMember);
        
        networks.put(networkId, network);
        
        calculateNetworkBonus(network);
        
        return true;
    }
    
    private boolean handleFraudDetected(Map<String, Object> eventData) {
        String referrerId = (String) eventData.get("referrerId");
        String refereeId = (String) eventData.get("refereeId");
        String code = (String) eventData.get("code");
        String reason = (String) eventData.get("reason");
        
        FraudIndicator indicator = new FraudIndicator(referrerId, refereeId, reason);
        fraudIndicators.put(code, indicator);
        
        ReferralCode referralCode = referralCodes.get(code);
        if (referralCode != null) {
            referralCode.flagAsFraudulent();
            persistReferralCode(referralCode);
        }
        
        ReferralAccount referrerAccount = referralAccounts.get(referrerId);
        if (referrerAccount != null) {
            referrerAccount.incrementFraudulentAttempts();
            if (referrerAccount.fraudulentAttempts > 3) {
                referrerAccount.suspend("Multiple fraudulent referral attempts");
            }
            persistAccount(referrerAccount);
        }
        
        notifyFraudDetected(referrerId, refereeId, reason);
        
        fraudDetectedCounter.increment();
        return true;
    }
    
    private boolean handleCodeShared(Map<String, Object> eventData) {
        String code = (String) eventData.get("code");
        String platform = (String) eventData.get("platform");
        int reach = ((Number) eventData.getOrDefault("reach", 0)).intValue();
        
        ReferralCode referralCode = referralCodes.get(code);
        if (referralCode == null) {
            return false;
        }
        
        referralCode.addShare(platform, reach);
        
        ReferralStats stats = referralStats.get(referralCode.ownerId);
        if (stats != null) {
            stats.addShare(platform);
        }
        
        persistReferralCode(referralCode);
        
        return true;
    }
    
    private boolean handleReferralInvalidated(Map<String, Object> eventData) {
        String referralId = (String) eventData.get("referralId");
        String reason = (String) eventData.get("reason");
        
        PendingReferral pending = pendingReferrals.remove(referralId);
        if (pending == null) {
            return false;
        }
        
        pending.status = "INVALIDATED";
        
        if (pending.referrerReward != null && pending.referrerReward.compareTo(BigDecimal.ZERO) > 0) {
            reverseReward(pending.referrerId, pending.referrerReward, reason);
        }
        
        if (pending.refereeReward != null && pending.refereeReward.compareTo(BigDecimal.ZERO) > 0) {
            reverseReward(pending.refereeId, pending.refereeReward, reason);
        }
        
        return true;
    }
    
    private boolean handleBonusMultiplierApplied(Map<String, Object> eventData) {
        String customerId = (String) eventData.get("customerId");
        double multiplier = ((Number) eventData.get("multiplier")).doubleValue();
        String reason = (String) eventData.get("reason");
        Instant expiresAt = Instant.parse((String) eventData.get("expiresAt"));
        
        ReferralAccount account = referralAccounts.get(customerId);
        if (account == null) {
            return false;
        }
        
        account.setBonusMultiplier(multiplier, expiresAt);
        
        notifyBonusMultiplierApplied(account, multiplier, reason);
        
        persistAccount(account);
        
        return true;
    }
    
    private void processPendingReferrals() {
        Instant qualificationDeadline = Instant.now().minusSeconds(qualificationDays * 24L * 60 * 60);
        
        pendingReferrals.values().stream()
            .filter(p -> "PENDING".equals(p.status))
            .filter(p -> p.createdAt.isBefore(qualificationDeadline))
            .limit(100)
            .forEach(pending -> {
                handleReferralExpired(Map.of(
                    "referralId", pending.referralId,
                    "reason", "Qualification period expired"
                ));
            });
    }
    
    private void checkQualifications() {
        pendingReferrals.values().stream()
            .filter(p -> "PENDING_QUALIFICATION".equals(p.status))
            .limit(200)
            .forEach(this::checkReferralQualification);
    }
    
    private void expireOldReferrals() {
        Instant expirationThreshold = Instant.now().minusSeconds(90L * 24 * 60 * 60);
        
        referralCodes.values().stream()
            .filter(c -> c.createdAt.isBefore(expirationThreshold))
            .filter(ReferralCode::isActive)
            .forEach(code -> {
                code.expire();
                persistReferralCode(code);
            });
    }
    
    private void updateReferralStats() {
        referralStats.values().forEach(stats -> {
            stats.calculateConversionRate();
            stats.calculateAverageValue();
            persistReferralStats(stats);
        });
    }
    
    private void detectFraudPatterns() {
        Map<String, List<PendingReferral>> referrerGroups = pendingReferrals.values().stream()
            .collect(Collectors.groupingBy(p -> p.referrerId));
        
        referrerGroups.forEach((referrerId, referrals) -> {
            if (detectSuspiciousPattern(referrals)) {
                referrals.forEach(r -> handleFraudDetected(Map.of(
                    "referrerId", r.referrerId,
                    "refereeId", r.refereeId,
                    "code", r.code,
                    "reason", "Suspicious pattern detected"
                )));
            }
        });
    }
    
    private void calculateNetworkMetrics() {
        networks.values().forEach(network -> {
            network.calculateDepth();
            network.calculateValue();
            network.identifyInfluencers();
        });
    }
    
    private void generateReferralReports() {
        ReferralMetrics metrics = new ReferralMetrics();
        
        metrics.totalAccounts = referralAccounts.size();
        metrics.activeCodes = (int) referralCodes.values().stream()
            .filter(ReferralCode::isActive).count();
        metrics.pendingReferrals = pendingReferrals.size();
        metrics.totalRewardsIssued = referralAccounts.values().stream()
            .map(a -> a.totalEarned)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        metrics.conversionRate = calculateOverallConversionRate();
        
        publishMetrics(metrics);
    }
    
    private void cleanupExpiredCodes() {
        referralCodes.entrySet().removeIf(entry -> {
            ReferralCode code = entry.getValue();
            if (!code.isActive() && code.isExpiredForCleanup()) {
                archiveReferralCode(code);
                return true;
            }
            return false;
        });
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
        logger.info("Shutting down ReferralTrackingConsumer...");
        
        persistAllAccounts();
        persistAllReferralCodes();
        
        scheduledExecutor.shutdown();
        processingExecutor.shutdown();
        validationExecutor.shutdown();
        rewardExecutor.shutdown();
        analyticsExecutor.shutdown();
        
        try {
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("ReferralTrackingConsumer shutdown complete");
    }
    
    private static class ReferralAccount {
        String customerId;
        String primaryCode;
        String referredBy;
        int successfulReferrals;
        int pendingReferrals;
        int expiredReferrals;
        int fraudulentAttempts;
        BigDecimal totalEarned;
        BigDecimal pendingRewards;
        double bonusMultiplier;
        Instant bonusExpiresAt;
        List<ReferralReward> rewards;
        Set<String> pendingReferralIds;
        Set<String> campaignIds;
        Map<Integer, BigDecimal> multiTierEarnings;
        Map<String, CampaignMilestone> milestones;
        boolean suspended;
        String suspensionReason;
        Instant createdAt;
        Instant lastActivityAt;
        
        ReferralAccount(String customerId) {
            this.customerId = customerId;
            this.successfulReferrals = 0;
            this.pendingReferrals = 0;
            this.expiredReferrals = 0;
            this.fraudulentAttempts = 0;
            this.totalEarned = BigDecimal.ZERO;
            this.pendingRewards = BigDecimal.ZERO;
            this.bonusMultiplier = 1.0;
            this.rewards = new CopyOnWriteArrayList<>();
            this.pendingReferralIds = ConcurrentHashMap.newKeySet();
            this.campaignIds = ConcurrentHashMap.newKeySet();
            this.multiTierEarnings = new ConcurrentHashMap<>();
            this.milestones = new ConcurrentHashMap<>();
            this.createdAt = Instant.now();
            this.lastActivityAt = Instant.now();
        }
        
        void setPrimaryCode(String code) {
            this.primaryCode = code;
        }
        
        void setReferredBy(String referrerId) {
            this.referredBy = referrerId;
        }
        
        void addPendingReferral(String referralId) {
            pendingReferralIds.add(referralId);
            pendingReferrals++;
        }
        
        void removePendingReferral(String referralId) {
            pendingReferralIds.remove(referralId);
            pendingReferrals = Math.max(0, pendingReferrals - 1);
        }
        
        void incrementSuccessfulReferrals() {
            successfulReferrals++;
            lastActivityAt = Instant.now();
        }
        
        void incrementExpiredReferrals() {
            expiredReferrals++;
        }
        
        void incrementFraudulentAttempts() {
            fraudulentAttempts++;
        }
        
        void addReward(ReferralReward reward) {
            rewards.add(reward);
            lastActivityAt = Instant.now();
        }
        
        void addMultiTierReward(int tier, BigDecimal amount) {
            multiTierEarnings.merge(tier, amount, BigDecimal::add);
            totalEarned = totalEarned.add(amount);
        }
        
        void enrollInCampaign(String campaignId) {
            campaignIds.add(campaignId);
        }
        
        void addCampaignMilestone(String campaignId, String milestone, BigDecimal reward) {
            milestones.put(campaignId + ":" + milestone, 
                          new CampaignMilestone(milestone, reward));
            totalEarned = totalEarned.add(reward);
        }
        
        void setBonusMultiplier(double multiplier, Instant expiresAt) {
            this.bonusMultiplier = multiplier;
            this.bonusExpiresAt = expiresAt;
        }
        
        void suspend(String reason) {
            this.suspended = true;
            this.suspensionReason = reason;
        }
    }
    
    private static class ReferralCode {
        String code;
        String ownerId;
        String campaignId;
        int maxUses;
        int currentUses;
        int clicks;
        List<Conversion> conversions;
        Map<String, Integer> shares;
        boolean active;
        boolean fraudulent;
        double campaignBonus;
        Map<String, Object> customization;
        Instant createdAt;
        Instant expiresAt;
        
        ReferralCode(String code, String ownerId, String campaignId, int maxUses, int expirationDays) {
            this.code = code;
            this.ownerId = ownerId;
            this.campaignId = campaignId;
            this.maxUses = maxUses;
            this.currentUses = 0;
            this.clicks = 0;
            this.conversions = new CopyOnWriteArrayList<>();
            this.shares = new ConcurrentHashMap<>();
            this.active = true;
            this.fraudulent = false;
            this.campaignBonus = 1.0;
            this.customization = new ConcurrentHashMap<>();
            this.createdAt = Instant.now();
            this.expiresAt = Instant.now().plusSeconds(expirationDays * 24L * 60 * 60);
        }
        
        boolean isActive() {
            return active && !fraudulent && Instant.now().isBefore(expiresAt);
        }
        
        boolean hasReachedLimit() {
            return maxUses > 0 && currentUses >= maxUses;
        }
        
        void incrementUsage() {
            currentUses++;
        }
        
        void incrementClicks() {
            clicks++;
        }
        
        void addConversion(String refereeId, BigDecimal value) {
            conversions.add(new Conversion(refereeId, value));
        }
        
        void addShare(String platform, int reach) {
            shares.merge(platform, reach, Integer::sum);
        }
        
        void applyCustomization(Map<String, Object> customization) {
            this.customization.putAll(customization);
        }
        
        void flagAsFraudulent() {
            this.fraudulent = true;
            this.active = false;
        }
        
        void expire() {
            this.active = false;
        }
        
        boolean isExpiredForCleanup() {
            return !active && Instant.now().isAfter(expiresAt.plusSeconds(90L * 24 * 60 * 60));
        }
    }
    
    private static class PendingReferral {
        String referralId;
        String referrerId;
        String refereeId;
        String code;
        String channel;
        String status;
        String qualifyingTransactionId;
        BigDecimal transactionAmount;
        BigDecimal referrerReward;
        BigDecimal refereeReward;
        Instant createdAt;
        Instant completedAt;
        Instant expiredAt;
        
        PendingReferral(String referralId, String referrerId, String refereeId, 
                       String code, String channel) {
            this.referralId = referralId;
            this.referrerId = referrerId;
            this.refereeId = refereeId;
            this.code = code;
            this.channel = channel;
            this.status = "PENDING";
            this.createdAt = Instant.now();
        }
    }
    
    private static class ReferralChain {
        String rootReferrerId;
        Map<String, Set<String>> referralTree;
        Map<String, Integer> depths;
        int maxDepth;
        
        void addLink(String referrer, String referee) {
            referralTree.computeIfAbsent(referrer, k -> new HashSet<>()).add(referee);
            calculateDepths();
        }
        
        void calculateDepths() {
            depths.clear();
            calculateDepthRecursive(rootReferrerId, 0);
        }
        
        private void calculateDepthRecursive(String node, int depth) {
            depths.put(node, depth);
            maxDepth = Math.max(maxDepth, depth);
            Set<String> children = referralTree.get(node);
            if (children != null) {
                for (String child : children) {
                    calculateDepthRecursive(child, depth + 1);
                }
            }
        }
    }
    
    private static class ReferralCampaign {
        String campaignId;
        String name;
        Map<String, Object> rules;
        Set<String> participants;
        BigDecimal welcomeBonus;
        double bonusMultiplier;
        Map<String, BigDecimal> milestoneRewards;
        Instant startDate;
        Instant endDate;
        
        void addParticipant(String customerId) {
            participants.add(customerId);
        }
        
        boolean hasWelcomeBonus() {
            return welcomeBonus != null && welcomeBonus.compareTo(BigDecimal.ZERO) > 0;
        }
        
        BigDecimal getWelcomeBonus() {
            return welcomeBonus;
        }
        
        double getBonusMultiplier() {
            return bonusMultiplier;
        }
    }
    
    private static class ReferralStats {
        String customerId;
        int totalClicks;
        int totalConversions;
        BigDecimal totalValue;
        Map<String, Integer> clicksByIp;
        Map<String, Integer> sharesByPlatform;
        double conversionRate;
        BigDecimal averageValue;
        
        ReferralStats(String customerId) {
            this.customerId = customerId;
            this.totalClicks = 0;
            this.totalConversions = 0;
            this.totalValue = BigDecimal.ZERO;
            this.clicksByIp = new ConcurrentHashMap<>();
            this.sharesByPlatform = new ConcurrentHashMap<>();
        }
        
        void addClick(String ip, String userAgent) {
            totalClicks++;
            clicksByIp.merge(ip, 1, Integer::sum);
        }
        
        void addConversion(BigDecimal value) {
            totalConversions++;
            totalValue = totalValue.add(value);
        }
        
        void addShare(String platform) {
            sharesByPlatform.merge(platform, 1, Integer::sum);
        }
        
        void calculateConversionRate() {
            if (totalClicks > 0) {
                conversionRate = (double) totalConversions / totalClicks;
            }
        }
        
        void calculateAverageValue() {
            if (totalConversions > 0) {
                averageValue = totalValue.divide(BigDecimal.valueOf(totalConversions), 2, RoundingMode.HALF_UP);
            }
        }
    }
    
    private static class ReferralNetwork {
        String networkId;
        String founderId;
        Set<String> members;
        Map<String, Integer> memberLevels;
        Map<String, BigDecimal> memberValues;
        int depth;
        BigDecimal totalValue;
        
        ReferralNetwork(String networkId, String founderId) {
            this.networkId = networkId;
            this.founderId = founderId;
            this.members = ConcurrentHashMap.newKeySet();
            this.memberLevels = new ConcurrentHashMap<>();
            this.memberValues = new ConcurrentHashMap<>();
            this.members.add(founderId);
            this.memberLevels.put(founderId, 0);
        }
        
        void addMember(String memberId) {
            members.add(memberId);
        }
        
        void calculateDepth() {
            depth = memberLevels.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        }
        
        void calculateValue() {
            totalValue = memberValues.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        
        void identifyInfluencers() {
            memberValues.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(10)
                .forEach(entry -> {
                    logger.debug("Top influencer in network {}: {} with value {}", 
                               networkId, entry.getKey(), entry.getValue());
                });
        }
    }
    
    private static class FraudIndicator {
        String referrerId;
        String refereeId;
        String reason;
        Instant detectedAt;
        Map<String, Object> evidence;
        
        FraudIndicator(String referrerId, String refereeId, String reason) {
            this.referrerId = referrerId;
            this.refereeId = refereeId;
            this.reason = reason;
            this.detectedAt = Instant.now();
            this.evidence = new HashMap<>();
        }
    }
    
    private static class ReferralReward {
        String referralId;
        BigDecimal amount;
        String type;
        Instant issuedAt;
        
        ReferralReward(String referralId, BigDecimal amount, String type) {
            this.referralId = referralId;
            this.amount = amount;
            this.type = type;
            this.issuedAt = Instant.now();
        }
    }
    
    private static class CampaignMilestone {
        String name;
        BigDecimal reward;
        Instant achievedAt;
        
        CampaignMilestone(String name, BigDecimal reward) {
            this.name = name;
            this.reward = reward;
            this.achievedAt = Instant.now();
        }
    }
    
    private static class Conversion {
        String refereeId;
        BigDecimal value;
        Instant timestamp;
        
        Conversion(String refereeId, BigDecimal value) {
            this.refereeId = refereeId;
            this.value = value;
            this.timestamp = Instant.now();
        }
    }
    
    private static class ReferralMetrics {
        int totalAccounts;
        int activeCodes;
        int pendingReferrals;
        BigDecimal totalRewardsIssued;
        double conversionRate;
    }
    
    private ReferralAccount createNewAccount(String customerId) {
        return new ReferralAccount(customerId);
    }
    
    private String generateUniqueCode(String customerId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String input = customerId + System.currentTimeMillis() + UUID.randomUUID();
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < referralCodeLength / 2; i++) {
                code.append(String.format("%02x", hash[i]));
            }
            
            return code.toString().toUpperCase().substring(0, referralCodeLength);
        } catch (Exception e) {
            return UUID.randomUUID().toString().substring(0, referralCodeLength).toUpperCase();
        }
    }
    
    private boolean detectFraud(String referrerId, String refereeId, Map<String, Object> context) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            if (referrerId.equals(refereeId)) {
                return true;
            }
            
            ReferralAccount referrerAccount = referralAccounts.get(referrerId);
            if (referrerAccount != null && referrerAccount.suspended) {
                return true;
            }
            
            return false;
        } finally {
            sample.stop(validationTimer);
        }
    }
    
    private void createReferralChain(String referrerId, String refereeId) {
        ReferralChain chain = referralChains.computeIfAbsent(referrerId,
            k -> {
                ReferralChain newChain = new ReferralChain();
                newChain.rootReferrerId = referrerId;
                newChain.referralTree = new ConcurrentHashMap<>();
                newChain.depths = new ConcurrentHashMap<>();
                return newChain;
            });
        
        chain.addLink(referrerId, refereeId);
    }
    
    private BigDecimal calculateReferrerReward(BigDecimal transactionAmount, ReferralAccount account) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            BigDecimal baseReward = referrerBonus;
            
            if (account.bonusMultiplier > 1.0 && 
                (account.bonusExpiresAt == null || Instant.now().isBefore(account.bonusExpiresAt))) {
                baseReward = baseReward.multiply(BigDecimal.valueOf(account.bonusMultiplier));
            }
            
            return baseReward.setScale(2, RoundingMode.DOWN);
        } finally {
            sample.stop(rewardCalculationTimer);
        }
    }
    
    private BigDecimal calculateRefereeReward(BigDecimal transactionAmount, ReferralAccount account) {
        return refereeBonus.setScale(2, RoundingMode.DOWN);
    }
    
    private void issueReward(ReferralAccount account, BigDecimal amount, String type) {
        account.totalEarned = account.totalEarned.add(amount);
        account.addReward(new ReferralReward(UUID.randomUUID().toString(), amount, type));
    }
    
    private void processMultiTierRewards(String referrerId, BigDecimal transactionAmount) {
        ReferralAccount tier1Account = referralAccounts.get(referrerId);
        if (tier1Account == null || tier1Account.referredBy == null) {
            return;
        }
        
        BigDecimal tier2Reward = transactionAmount.multiply(BigDecimal.valueOf(tier2Rate))
                                                  .setScale(2, RoundingMode.DOWN);
        
        ReferralAccount tier2Account = referralAccounts.get(tier1Account.referredBy);
        if (tier2Account != null) {
            issueReward(tier2Account, tier2Reward, "TIER2_BONUS");
            
            if (tier2Account.referredBy != null) {
                BigDecimal tier3Reward = transactionAmount.multiply(BigDecimal.valueOf(tier3Rate))
                                                          .setScale(2, RoundingMode.DOWN);
                
                ReferralAccount tier3Account = referralAccounts.get(tier2Account.referredBy);
                if (tier3Account != null) {
                    issueReward(tier3Account, tier3Reward, "TIER3_BONUS");
                }
            }
        }
    }
    
    private void updateNetworkStrength(String referrerId, String refereeId) {
        logger.debug("Updating network strength for {} -> {}", referrerId, refereeId);
    }
    
    private void updateReferralStats(String referrerId, String refereeId, BigDecimal amount) {
        ReferralStats stats = referralStats.computeIfAbsent(referrerId, k -> new ReferralStats(k));
        stats.addConversion(amount);
    }
    
    private void checkReferralQualification(PendingReferral pending) {
        logger.debug("Checking qualification for referral {}", pending.referralId);
    }
    
    private boolean detectSuspiciousPattern(List<PendingReferral> referrals) {
        if (referrals.size() > 10) {
            long uniqueIps = referrals.stream()
                .map(r -> r.channel)
                .distinct()
                .count();
            
            return uniqueIps < 3;
        }
        return false;
    }
    
    private void calculateNetworkBonus(ReferralNetwork network) {
        if (network.members.size() >= 10) {
            BigDecimal bonus = BigDecimal.valueOf(network.members.size() * 5);
            ReferralAccount founder = referralAccounts.get(network.founderId);
            if (founder != null) {
                issueReward(founder, bonus, "NETWORK_BONUS");
            }
        }
    }
    
    private void reverseReward(String customerId, BigDecimal amount, String reason) {
        ReferralAccount account = referralAccounts.get(customerId);
        if (account != null) {
            account.totalEarned = account.totalEarned.subtract(amount);
            logger.warn("Reversed reward {} for customer {} - {}", amount, customerId, reason);
        }
    }
    
    private double calculateOverallConversionRate() {
        long totalClicks = referralStats.values().stream()
            .mapToLong(s -> s.totalClicks)
            .sum();
        
        long totalConversions = referralStats.values().stream()
            .mapToLong(s -> s.totalConversions)
            .sum();
        
        return totalClicks > 0 ? (double) totalConversions / totalClicks : 0.0;
    }
    
    private void archiveReferralCode(ReferralCode code) {
        logger.debug("Archiving referral code {}", code.code);
    }
    
    private void persistAccount(ReferralAccount account) {
        try {
            String key = REFERRAL_CODE_PREFIX + account.customerId;
            String json = objectMapper.writeValueAsString(account);
            redisTemplate.opsForValue().set(key, json, Duration.ofDays(90));
        } catch (Exception e) {
            logger.error("Failed to persist account", e);
        }
    }
    
    private void persistReferralCode(ReferralCode code) {
        try {
            String key = REFERRAL_CODE_PREFIX + code.code;
            String json = objectMapper.writeValueAsString(code);
            redisTemplate.opsForValue().set(key, json, Duration.ofDays(180));
        } catch (Exception e) {
            logger.error("Failed to persist referral code", e);
        }
    }
    
    private void persistPendingReferral(PendingReferral pending) {
        logger.debug("Persisting pending referral {}", pending.referralId);
    }
    
    private void persistReferralStats(ReferralStats stats) {
        try {
            String key = REFERRAL_STATS_PREFIX + stats.customerId;
            String json = objectMapper.writeValueAsString(stats);
            redisTemplate.opsForValue().set(key, json, Duration.ofDays(30));
        } catch (Exception e) {
            logger.error("Failed to persist referral stats", e);
        }
    }
    
    private void persistAllAccounts() {
        referralAccounts.values().forEach(this::persistAccount);
    }
    
    private void persistAllReferralCodes() {
        referralCodes.values().forEach(this::persistReferralCode);
    }
    
    private void publishMetrics(ReferralMetrics metrics) {
        logger.debug("Publishing referral metrics: {} accounts, {} active codes",
                    metrics.totalAccounts, metrics.activeCodes);
    }
    
    private void initializeDefaultCampaigns() {
        ReferralCampaign summerCampaign = new ReferralCampaign();
        summerCampaign.campaignId = "SUMMER2024";
        summerCampaign.name = "Summer Referral Boost";
        summerCampaign.welcomeBonus = BigDecimal.valueOf(5);
        summerCampaign.bonusMultiplier = 1.5;
        summerCampaign.participants = ConcurrentHashMap.newKeySet();
        summerCampaign.milestoneRewards = new HashMap<>();
        campaigns.put("SUMMER2024", summerCampaign);
    }
    
    private void notifyCodeCreated(ReferralAccount account, ReferralCode code) {
        logger.info("Referral code created for {}: {}", account.customerId, code.code);
    }
    
    private void notifyReferralStarted(String referrerId, String refereeId) {
        logger.info("Referral started: {} -> {}", referrerId, refereeId);
    }
    
    private void notifyReferralExpired(String referrerId, String refereeId, String reason) {
        logger.warn("Referral expired: {} -> {} - {}", referrerId, refereeId, reason);
    }
    
    private void notifyRewardIssued(ReferralAccount account, BigDecimal amount, String type) {
        logger.info("Reward issued to {}: {} ({})", account.customerId, amount, type);
    }
    
    private void notifyMultiTierReward(ReferralAccount account, BigDecimal amount, int tier) {
        logger.info("Tier {} reward issued to {}: {}", tier, account.customerId, amount);
    }
    
    private void notifyMilestoneReached(ReferralAccount account, String milestone, BigDecimal reward) {
        logger.info("Milestone reached by {}: {} ({} reward)", account.customerId, milestone, reward);
    }
    
    private void notifyFraudDetected(String referrerId, String refereeId, String reason) {
        logger.error("Fraud detected: {} -> {} - {}", referrerId, refereeId, reason);
    }
    
    private void notifyBonusMultiplierApplied(ReferralAccount account, double multiplier, String reason) {
        logger.info("Bonus multiplier {}x applied to {} - {}", multiplier, account.customerId, reason);
    }
}