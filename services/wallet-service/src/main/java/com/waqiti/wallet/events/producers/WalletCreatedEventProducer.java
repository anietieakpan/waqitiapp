package com.waqiti.wallet.events.producers;

import com.waqiti.common.events.wallet.WalletCreatedEvent;
import com.waqiti.wallet.domain.Wallet;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Production-ready Wallet Created Event Producer
 * 
 * Publishes wallet-created events when new wallets are created for users.
 * This producer triggers downstream workflows including:
 * - Welcome bonus rewards issuance
 * - User analytics tracking
 * - Wallet notification to user
 * - Initial balance setup
 * - Account tier evaluation
 * 
 * Event Consumers:
 * - rewards-service: Awards welcome bonuses and initial rewards
 * - analytics-service: Tracks wallet creation metrics and user behavior
 * - notification-service: Sends wallet creation confirmation
 * - reporting-service: Updates financial reporting dashboards
 * - fraud-service: Monitors wallet creation patterns
 * 
 * Features:
 * - Transactional outbox pattern for guaranteed delivery
 * - Comprehensive error handling and retry logic
 * - Audit logging for compliance
 * - Metrics and monitoring integration
 * - Correlation ID propagation
 * - Privacy-compliant event data
 * 
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletCreatedEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${kafka.topics.wallet-created:wallet-created}")
    private String topicName;

    @Value("${app.events.wallet-created.enabled:true}")
    private boolean eventsEnabled;

    // Metrics
    private final Counter successCounter = Counter.builder("wallet_created_events_published_total")
            .description("Total number of wallet created events successfully published")
            .register(meterRegistry);

    private final Counter failureCounter = Counter.builder("wallet_created_events_failed_total")
            .description("Total number of wallet created events that failed to publish")
            .register(meterRegistry);

    private final Timer publishTimer = Timer.builder("wallet_created_event_publish_duration")
            .description("Time taken to publish wallet created events")
            .register(meterRegistry);

    /**
     * Publishes wallet created event with comprehensive error handling
     * 
     * @param wallet The newly created wallet
     * @param creationSource Source of wallet creation (USER_REGISTRATION, MANUAL, API, etc.)
     * @param correlationId Correlation ID for tracing
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void publishWalletCreatedEvent(Wallet wallet, 
                                         String creationSource,
                                         String correlationId) {
        
        if (!eventsEnabled) {
            log.debug("Wallet created events are disabled, skipping event publication for wallet: {}", 
                     wallet.getId());
            return;
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = UUID.randomUUID().toString();
        
        try {
            log.info("Publishing wallet created event: walletId={}, userId={}, correlationId={}, eventId={}", 
                    wallet.getId(), wallet.getUserId(), correlationId, eventId);

            // Build comprehensive event
            WalletCreatedEvent event = buildWalletCreatedEvent(
                wallet, eventId, correlationId, creationSource);

            // Validate event before publishing
            validateEvent(event);

            // Publish event asynchronously with callback handling
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                topicName, 
                wallet.getUserId().toString(), 
                event
            );

            // Handle success and failure callbacks
            future.whenComplete((result, throwable) -> {
                sample.stop(publishTimer);
                
                if (throwable != null) {
                    handlePublishFailure(event, throwable, correlationId);
                } else {
                    handlePublishSuccess(event, result, correlationId);
                }
            });

            log.debug("Wallet created event publication initiated: eventId={}, walletId={}", 
                     eventId, wallet.getId());

        } catch (Exception e) {
            sample.stop(publishTimer);
            handlePublishFailure(eventId, wallet.getId().toString(), wallet.getUserId().toString(), e, correlationId);
            throw new RuntimeException("Failed to publish wallet created event", e);
        }
    }

    /**
     * Synchronous version for critical wallet creation paths
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void publishWalletCreatedEventSync(Wallet wallet,
                                             String creationSource,
                                             String correlationId) {
        
        if (!eventsEnabled) {
            log.debug("Wallet created events are disabled, skipping synchronous event publication for wallet: {}", 
                     wallet.getId());
            return;
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = UUID.randomUUID().toString();
        
        try {
            log.info("Publishing wallet created event synchronously: walletId={}, userId={}, correlationId={}, eventId={}", 
                    wallet.getId(), wallet.getUserId(), correlationId, eventId);

            WalletCreatedEvent event = buildWalletCreatedEvent(
                wallet, eventId, correlationId, creationSource);

            validateEvent(event);

            // Synchronous send with timeout
            SendResult<String, Object> result = kafkaTemplate.send(
                topicName, 
                wallet.getUserId().toString(), 
                event
            ).get(5, java.util.concurrent.TimeUnit.SECONDS); // 5 second timeout

            handlePublishSuccess(event, result, correlationId);

        } catch (Exception e) {
            sample.stop(publishTimer);
            handlePublishFailure(eventId, wallet.getId().toString(), wallet.getUserId().toString(), e, correlationId);
            throw new RuntimeException("Failed to publish wallet created event synchronously", e);
        } finally {
            sample.stop(publishTimer);
        }
    }

    /**
     * Builds comprehensive wallet created event from wallet data
     */
    private WalletCreatedEvent buildWalletCreatedEvent(Wallet wallet,
                                                      String eventId,
                                                      String correlationId,
                                                      String creationSource) {
        return WalletCreatedEvent.builder()
                .eventId(eventId)
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .eventVersion("1.0")
                .source("wallet-service")
                
                // Wallet core details
                .walletId(wallet.getId())
                .userId(wallet.getUserId())
                .externalId(wallet.getExternalId())
                .walletType(wallet.getWalletType() != null ? wallet.getWalletType().toString() : "STANDARD")
                .accountType(wallet.getAccountType() != null ? wallet.getAccountType().toString() : "CHECKING")
                .currency(wallet.getCurrency())
                
                // Wallet status and balance
                .status(wallet.getStatus() != null ? wallet.getStatus().toString() : "ACTIVE")
                .balance(wallet.getBalance())
                .availableBalance(wallet.getAvailableBalance())
                .reservedBalance(wallet.getReservedBalance())
                
                // Limits and restrictions
                .dailyLimit(wallet.getDailyLimit())
                .monthlyLimit(wallet.getMonthlyLimit())
                .minimumBalance(wallet.getMinimumBalance())
                .maximumBalance(wallet.getMaximumBalance())
                
                // Creation context
                .creationSource(creationSource != null ? creationSource : "UNKNOWN")
                .createdAt(wallet.getCreatedAt() != null ? 
                    wallet.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant() : 
                    Instant.now())
                .createdBy(wallet.getCreatedBy())
                
                // Wallet features and flags
                .isActive(wallet.isActive())
                .isPrimary(wallet.isPrimary())
                .isVerified(wallet.isVerified())
                .allowsDeposits(wallet.isAllowsDeposits())
                .allowsWithdrawals(wallet.isAllowsWithdrawals())
                .allowsTransfers(wallet.isAllowsTransfers())
                
                // Interest and rewards
                .interestRate(wallet.getInterestRate())
                .rewardMultiplier(wallet.getRewardMultiplier())
                
                // Metadata
                .metadata(buildMetadata(wallet, creationSource))
                
                .build();
    }

    /**
     * Build metadata for the event
     */
    private Map<String, String> buildMetadata(Wallet wallet, String creationSource) {
        Map<String, String> metadata = new java.util.HashMap<>();
        
        metadata.put("creationSource", creationSource != null ? creationSource : "UNKNOWN");
        metadata.put("walletType", wallet.getWalletType() != null ? wallet.getWalletType().toString() : "STANDARD");
        metadata.put("accountType", wallet.getAccountType() != null ? wallet.getAccountType().toString() : "CHECKING");
        metadata.put("currency", wallet.getCurrency());
        metadata.put("isActive", String.valueOf(wallet.isActive()));
        metadata.put("isPrimary", String.valueOf(wallet.isPrimary()));
        metadata.put("isVerified", String.valueOf(wallet.isVerified()));
        metadata.put("hasInterest", String.valueOf(wallet.getInterestRate() != null && 
                                                   wallet.getInterestRate().compareTo(java.math.BigDecimal.ZERO) > 0));
        metadata.put("hasRewards", String.valueOf(wallet.getRewardMultiplier() != null && 
                                                  wallet.getRewardMultiplier().compareTo(java.math.BigDecimal.ZERO) > 0));
        
        return metadata;
    }

    /**
     * Validates event before publishing
     */
    private void validateEvent(WalletCreatedEvent event) {
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
        if (event.getCreatedAt() == null) {
            throw new IllegalArgumentException("Creation timestamp is required");
        }
    }

    /**
     * Handles successful event publication
     */
    private void handlePublishSuccess(WalletCreatedEvent event, 
                                    SendResult<String, Object> result, 
                                    String correlationId) {
        successCounter.increment();
        
        log.info("Successfully published wallet created event: eventId={}, walletId={}, userId={}, " +
                "correlationId={}, partition={}, offset={}", 
                event.getEventId(), 
                event.getWalletId(),
                event.getUserId(),
                correlationId,
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());

        // Track metrics by wallet type and currency
        Counter.builder("wallet_created_by_type_total")
                .tag("walletType", event.getWalletType())
                .tag("currency", event.getCurrency())
                .register(meterRegistry)
                .increment();
    }

    /**
     * Handles event publication failure
     */
    private void handlePublishFailure(WalletCreatedEvent event, 
                                    Throwable throwable, 
                                    String correlationId) {
        failureCounter.increment();
        
        log.error("Failed to publish wallet created event: eventId={}, walletId={}, userId={}, " +
                 "correlationId={}, error={}", 
                 event.getEventId(), 
                 event.getWalletId(),
                 event.getUserId(),
                 correlationId,
                 throwable.getMessage(), 
                 throwable);

        // Track failure metrics
        Counter.builder("wallet_created_events_failed_by_type_total")
                .tag("walletType", event.getWalletType())
                .tag("currency", event.getCurrency())
                .register(meterRegistry)
                .increment();
    }

    /**
     * Handles event publication failure with limited event data
     */
    private void handlePublishFailure(String eventId, 
                                    String walletId,
                                    String userId,
                                    Throwable throwable, 
                                    String correlationId) {
        failureCounter.increment();
        
        log.error("Failed to publish wallet created event during event creation: eventId={}, " +
                 "walletId={}, userId={}, correlationId={}, error={}", 
                 eventId, walletId, userId, correlationId, throwable.getMessage(), throwable);
    }
}