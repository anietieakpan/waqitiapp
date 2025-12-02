package com.waqiti.rewards.events.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.events.wallet.WalletCreatedEvent;
import com.waqiti.common.exceptions.ServiceIntegrationException;
import com.waqiti.rewards.dto.*;
import com.waqiti.rewards.entity.RewardsRecord;
import com.waqiti.rewards.repository.RewardsRecordRepository;
import com.waqiti.rewards.service.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Production-ready Wallet Created Events Consumer for Rewards Service
 * 
 * Consumes wallet-created events to award welcome bonuses and initial rewards
 * to new users when they create their first wallet.
 * 
 * Key Responsibilities:
 * - Award welcome bonuses to new users
 * - Track wallet creation for referral rewards
 * - Initialize reward tiers based on wallet type
 * - Send welcome bonus notifications
 * - Record analytics for welcome bonus campaigns
 * 
 * Welcome Bonus Rules:
 * - First wallet: $10 welcome bonus
 * - Referral wallet: Additional $5 referral bonus
 * - Premium wallets: 2x welcome bonus
 * - Verified users: Additional verification bonus
 * 
 * Integration Points:
 * - notification-service: Welcome bonus notifications
 * - analytics-service: Campaign tracking
 * - wallet-service: Bonus credit application
 * 
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletCreatedEventsConsumer {

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;

    // Repository
    private final RewardsRecordRepository rewardsRecordRepository;

    // Services
    private final WelcomeBonusService welcomeBonusService;
    private final ReferralRewardsService referralRewardsService;
    private final RewardsTierService rewardsTierService;
    private final NotificationServiceClient notificationServiceClient;
    private final AnalyticsServiceClient analyticsServiceClient;
    private final EventProcessingTrackingService eventProcessingTrackingService;

    // Metrics
    private final Counter successCounter = Counter.builder("wallet_created_events_processed_total")
            .description("Total number of wallet created events successfully processed")
            .register(meterRegistry);

    private final Counter failureCounter = Counter.builder("wallet_created_events_failed_total")
            .description("Total number of wallet created events that failed processing")
            .register(meterRegistry);

    private final Timer processingTimer = Timer.builder("wallet_created_event_processing_duration")
            .description("Time taken to process wallet created events")
            .register(meterRegistry);

    private final Counter bonusesAwardedCounter = Counter.builder("welcome_bonuses_awarded_total")
            .description("Total number of welcome bonuses awarded")
            .register(meterRegistry);

    /**
     * Main Kafka listener for wallet created events
     */
    @KafkaListener(
        topics = "${kafka.topics.wallet-created:wallet-created}",
        groupId = "${kafka.consumer.group-id:rewards-service-consumer-group}",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "${kafka.consumer.concurrency:3}"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        include = {ServiceIntegrationException.class, Exception.class},
        dltTopicSuffix = "-dlt",
        autoCreateTopics = "true",
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED, timeout = 30)
    public void handleWalletCreatedEvent(
            @Payload WalletCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = "correlationId", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        LocalDateTime processingStartTime = LocalDateTime.now();
        
        // Generate correlation ID if not provided
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = event.getCorrelationId() != null ? 
                event.getCorrelationId() : UUID.randomUUID().toString();
        }

        log.info("Processing wallet created event: eventId={}, walletId={}, userId={}, " +
                "correlationId={}, topic={}, partition={}, offset={}", 
                event.getEventId(), event.getWalletId(), event.getUserId(), 
                correlationId, topic, partition, offset);

        try {
            // 1. Validate event
            validateWalletCreatedEvent(event);

            // 2. Check for duplicate processing
            if (eventProcessingTrackingService.isDuplicateEvent(event.getEventId(), "WALLET_CREATED_EVENT")) {
                log.warn("Duplicate wallet created event detected, skipping: eventId={}, walletId={}", 
                        event.getEventId(), event.getWalletId());
                acknowledgment.acknowledge();
                return;
            }

            // 3. Track event processing start
            eventProcessingTrackingService.trackEventProcessingStart(
                event.getEventId(), 
                "WALLET_CREATED_EVENT", 
                correlationId,
                Map.of(
                    "walletId", event.getWalletId().toString(),
                    "userId", event.getUserId().toString(),
                    "walletType", event.getWalletType() != null ? event.getWalletType() : "UNKNOWN",
                    "currency", event.getCurrency()
                )
            );

            // 4. Process welcome bonuses
            processWelcomeBonuses(event, correlationId);

            // 5. Track successful processing
            eventProcessingTrackingService.trackEventProcessingSuccess(
                event.getEventId(),
                Map.of(
                    "processingTimeMs", sample.stop(processingTimer).longValue(),
                    "bonusesAwarded", "true",
                    "processingStartTime", processingStartTime.toString()
                )
            );

            successCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed wallet created event: eventId={}, walletId={}, " +
                    "userId={}, processingTimeMs={}", 
                    event.getEventId(), event.getWalletId(), event.getUserId(),
                    sample.stop(processingTimer).longValue());

        } catch (Exception e) {
            sample.stop(processingTimer);
            failureCounter.increment();

            log.error("Failed to process wallet created event: eventId={}, walletId={}, " +
                     "userId={}, attempt={}, error={}", 
                     event.getEventId(), event.getWalletId(), event.getUserId(),
                     RetrySynchronizationManager.getContext() != null ? 
                         RetrySynchronizationManager.getContext().getRetryCount() + 1 : 1,
                     e.getMessage(), e);

            // Track processing failure
            eventProcessingTrackingService.trackEventProcessingFailure(
                event.getEventId(),
                e.getClass().getSimpleName(),
                e.getMessage(),
                Map.of(
                    "processingTimeMs", sample.stop(processingTimer).longValue(),
                    "attempt", String.valueOf(RetrySynchronizationManager.getContext() != null ? 
                        RetrySynchronizationManager.getContext().getRetryCount() + 1 : 1)
                )
            );

            // Audit critical failure
            auditService.logWalletCreatedEventProcessingFailure(
                event.getEventId(),
                event.getWalletId().toString(),
                event.getUserId().toString(),
                correlationId,
                e.getClass().getSimpleName(),
                e.getMessage(),
                Map.of(
                    "topic", topic,
                    "partition", String.valueOf(partition),
                    "offset", String.valueOf(offset),
                    "walletType", event.getWalletType() != null ? event.getWalletType() : "UNKNOWN",
                    "currency", event.getCurrency()
                )
            );

            throw new ServiceIntegrationException("Wallet created event processing failed", e);
        }
    }

    /**
     * Process welcome bonuses for newly created wallet
     */
    private void processWelcomeBonuses(WalletCreatedEvent event, String correlationId) {
        log.info("Processing welcome bonuses for wallet: walletId={}, userId={}, walletType={}", 
                event.getWalletId(), event.getUserId(), event.getWalletType());

        // Check if this is the user's first wallet
        boolean isFirstWallet = isFirstWalletForUser(event.getUserId());
        
        if (!isFirstWallet) {
            log.info("Not first wallet for user, skipping welcome bonus: userId={}, walletId={}", 
                    event.getUserId(), event.getWalletId());
            return;
        }

        // Calculate welcome bonus amount based on wallet type and user status
        BigDecimal welcomeBonusAmount = calculateWelcomeBonusAmount(event);
        
        if (welcomeBonusAmount.compareTo(BigDecimal.ZERO) == 0) {
            log.info("No welcome bonus applicable for wallet: walletId={}, walletType={}", 
                    event.getWalletId(), event.getWalletType());
            return;
        }

        // Award welcome bonus
        try {
            WelcomeBonusRequest bonusRequest = WelcomeBonusRequest.builder()
                    .userId(event.getUserId())
                    .walletId(event.getWalletId())
                    .bonusAmount(welcomeBonusAmount)
                    .currency(event.getCurrency())
                    .bonusType("WELCOME_BONUS")
                    .walletType(event.getWalletType())
                    .correlationId(correlationId)
                    .build();

            WelcomeBonusResponse bonusResponse = welcomeBonusService.awardWelcomeBonus(bonusRequest);
            
            if (bonusResponse.isSuccess()) {
                bonusesAwardedCounter.increment();
                
                // Record rewards record
                recordWelcomeBonusReward(event, welcomeBonusAmount, bonusResponse.getRewardsId(), correlationId);
                
                // Send notification
                sendWelcomeBonusNotification(event, welcomeBonusAmount, correlationId);
                
                // Record analytics
                recordWelcomeBonusAnalytics(event, welcomeBonusAmount, correlationId);
                
                log.info("Welcome bonus awarded successfully: userId={}, walletId={}, amount={} {}, rewardsId={}", 
                        event.getUserId(), event.getWalletId(), welcomeBonusAmount, event.getCurrency(),
                        bonusResponse.getRewardsId());
            } else {
                log.warn("Welcome bonus award failed: userId={}, walletId={}, reason={}", 
                        event.getUserId(), event.getWalletId(), bonusResponse.getFailureReason());
            }
            
        } catch (Exception e) {
            log.error("Failed to award welcome bonus: userId={}, walletId={}, error={}", 
                     event.getUserId(), event.getWalletId(), e.getMessage(), e);
            // Don't fail the entire processing for bonus award failures
        }

        // Check for referral bonuses
        processReferralBonuses(event, correlationId);

        // Initialize rewards tier
        initializeRewardsTier(event, correlationId);
    }

    /**
     * Check if this is the first wallet for the user
     */
    private boolean isFirstWalletForUser(UUID userId) {
        // Check if user has received welcome bonus before
        return !rewardsRecordRepository.existsByUserIdAndRewardType(userId, "WELCOME_BONUS");
    }

    /**
     * Calculate welcome bonus amount based on wallet type and user status
     */
    private BigDecimal calculateWelcomeBonusAmount(WalletCreatedEvent event) {
        BigDecimal baseBonus = new BigDecimal("10.00"); // $10 base welcome bonus
        
        // Premium wallets get 2x bonus
        if ("PREMIUM".equals(event.getWalletType()) || "BUSINESS".equals(event.getWalletType())) {
            baseBonus = baseBonus.multiply(new BigDecimal("2"));
        }
        
        // Verified users get additional bonus
        if (Boolean.TRUE.equals(event.getIsVerified())) {
            baseBonus = baseBonus.add(new BigDecimal("5.00")); // +$5 verification bonus
        }
        
        // Check for promotional periods (could be loaded from configuration)
        // For now, return base bonus
        return baseBonus;
    }

    /**
     * Process referral bonuses if applicable
     */
    private void processReferralBonuses(WalletCreatedEvent event, String correlationId) {
        try {
            // Check if wallet was created via referral
            Map<String, String> metadata = event.getMetadata();
            if (metadata != null && metadata.containsKey("referralCode")) {
                String referralCode = metadata.get("referralCode");
                
                ReferralBonusRequest referralRequest = ReferralBonusRequest.builder()
                        .userId(event.getUserId())
                        .walletId(event.getWalletId())
                        .referralCode(referralCode)
                        .currency(event.getCurrency())
                        .correlationId(correlationId)
                        .build();

                referralRewardsService.processReferralBonus(referralRequest);
                
                log.info("Referral bonus processed for wallet: walletId={}, referralCode={}", 
                        event.getWalletId(), referralCode);
            }
        } catch (Exception e) {
            log.error("Failed to process referral bonus: walletId={}, error={}", 
                     event.getWalletId(), e.getMessage(), e);
        }
    }

    /**
     * Initialize rewards tier for new wallet
     */
    private void initializeRewardsTier(WalletCreatedEvent event, String correlationId) {
        try {
            RewardsTierRequest tierRequest = RewardsTierRequest.builder()
                    .userId(event.getUserId())
                    .walletId(event.getWalletId())
                    .walletType(event.getWalletType())
                    .currency(event.getCurrency())
                    .correlationId(correlationId)
                    .build();

            rewardsTierService.initializeRewardsTier(tierRequest);
            
            log.info("Rewards tier initialized for wallet: walletId={}, userId={}", 
                    event.getWalletId(), event.getUserId());
        } catch (Exception e) {
            log.error("Failed to initialize rewards tier: walletId={}, error={}", 
                     event.getWalletId(), e.getMessage(), e);
        }
    }

    /**
     * Record welcome bonus reward in database
     */
    private void recordWelcomeBonusReward(WalletCreatedEvent event, BigDecimal amount, 
                                         UUID rewardsId, String correlationId) {
        try {
            RewardsRecord record = RewardsRecord.builder()
                    .rewardsId(rewardsId)
                    .userId(event.getUserId())
                    .walletId(event.getWalletId())
                    .rewardType("WELCOME_BONUS")
                    .amount(amount)
                    .currency(event.getCurrency())
                    .status("AWARDED")
                    .awardedAt(LocalDateTime.now())
                    .correlationId(correlationId)
                    .metadata(Map.of(
                        "walletType", event.getWalletType(),
                        "creationSource", event.getCreationSource()
                    ))
                    .build();

            rewardsRecordRepository.save(record);
            
            auditService.logWelcomeBonusAwarded(
                event.getEventId(),
                event.getWalletId().toString(),
                event.getUserId().toString(),
                amount,
                event.getCurrency(),
                rewardsId.toString(),
                correlationId,
                Map.of("walletType", event.getWalletType())
            );
            
        } catch (Exception e) {
            log.error("Failed to record welcome bonus reward: walletId={}, error={}", 
                     event.getWalletId(), e.getMessage(), e);
        }
    }

    /**
     * Send welcome bonus notification
     */
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendWelcomeBonusNotificationFallback")
    @Retry(name = "notification-service")
    private void sendWelcomeBonusNotification(WalletCreatedEvent event, BigDecimal amount, 
                                             String correlationId) {
        try {
            WelcomeBonusNotificationRequest notificationRequest = WelcomeBonusNotificationRequest.builder()
                    .userId(event.getUserId())
                    .walletId(event.getWalletId())
                    .bonusAmount(amount)
                    .currency(event.getCurrency())
                    .correlationId(correlationId)
                    .build();

            notificationServiceClient.sendWelcomeBonusNotification(notificationRequest);
            
            log.info("Welcome bonus notification sent: userId={}, amount={} {}", 
                    event.getUserId(), amount, event.getCurrency());
                    
        } catch (Exception e) {
            log.error("Failed to send welcome bonus notification: userId={}, error={}", 
                     event.getUserId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Fallback for welcome bonus notification
     */
    private void sendWelcomeBonusNotificationFallback(WalletCreatedEvent event, BigDecimal amount, 
                                                     String correlationId, Exception ex) {
        log.warn("Welcome bonus notification fallback triggered: userId={}, error={}", 
                event.getUserId(), ex.getMessage());
    }

    /**
     * Record welcome bonus analytics
     */
    @CircuitBreaker(name = "analytics-service")
    @Retry(name = "analytics-service")
    private void recordWelcomeBonusAnalytics(WalletCreatedEvent event, BigDecimal amount, 
                                           String correlationId) {
        try {
            WelcomeBonusAnalyticsRequest analyticsRequest = WelcomeBonusAnalyticsRequest.builder()
                    .userId(event.getUserId())
                    .walletId(event.getWalletId())
                    .bonusAmount(amount)
                    .currency(event.getCurrency())
                    .walletType(event.getWalletType())
                    .creationSource(event.getCreationSource())
                    .awardedAt(LocalDateTime.now())
                    .correlationId(correlationId)
                    .build();

            analyticsServiceClient.recordWelcomeBonusAnalytics(analyticsRequest);
            
            log.debug("Welcome bonus analytics recorded: userId={}, walletId={}", 
                     event.getUserId(), event.getWalletId());
                     
        } catch (Exception e) {
            log.warn("Failed to record welcome bonus analytics: userId={}, error={}", 
                    event.getUserId(), e.getMessage());
            // Don't fail for analytics issues
        }
    }

    /**
     * Validate wallet created event
     */
    private void validateWalletCreatedEvent(WalletCreatedEvent event) {
        Set<ConstraintViolation<WalletCreatedEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder("Wallet created event validation failed: ");
            for (ConstraintViolation<WalletCreatedEvent> violation : violations) {
                sb.append(violation.getPropertyPath()).append(" ").append(violation.getMessage()).append("; ");
            }
            throw new IllegalArgumentException(sb.toString());
        }

        // Additional business validation
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID is required");
        }
        if (event.getWalletId() == null) {
            throw new IllegalArgumentException("Wallet ID is required");
        }
        if (event.getUserId() == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (event.getCurrency() == null || event.getCurrency().trim().isEmpty()) {
            throw new IllegalArgumentException("Currency is required");
        }
    }

    /**
     * Dead Letter Topic handler for failed wallet created events
     */
    @DltHandler
    public void handleDltWalletCreatedEvent(
            @Payload WalletCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
            @Header(value = "correlationId", required = false) String correlationId) {
        
        log.error("Wallet created event sent to DLT: eventId={}, walletId={}, userId={}, " +
                 "topic={}, error={}", 
                 event.getEventId(), event.getWalletId(), event.getUserId(),
                 topic, exceptionMessage);

        // Generate correlation ID if not provided
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = event.getCorrelationId() != null ? 
                event.getCorrelationId() : UUID.randomUUID().toString();
        }

        // Track DLT event
        eventProcessingTrackingService.trackEventDLT(
            event.getEventId(),
            "WALLET_CREATED_EVENT",
            exceptionMessage,
            Map.of(
                "topic", topic,
                "walletId", event.getWalletId().toString(),
                "userId", event.getUserId().toString(),
                "walletType", event.getWalletType() != null ? event.getWalletType() : "UNKNOWN",
                "currency", event.getCurrency()
            )
        );

        // Critical audit for DLT events
        auditService.logWalletCreatedEventDLT(
            event.getEventId(),
            event.getWalletId().toString(),
            event.getUserId().toString(),
            correlationId,
            topic,
            exceptionMessage,
            Map.of(
                "walletType", event.getWalletType() != null ? event.getWalletType() : "UNKNOWN",
                "currency", event.getCurrency(),
                "creationSource", event.getCreationSource() != null ? event.getCreationSource() : "UNKNOWN",
                "requiresManualIntervention", "true"
            )
        );
    }
}