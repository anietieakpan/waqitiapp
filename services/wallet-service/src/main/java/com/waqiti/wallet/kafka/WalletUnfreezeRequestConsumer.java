package com.waqiti.wallet.kafka;

import com.waqiti.wallet.dto.WalletUnfreezeRequest;
import com.waqiti.wallet.entity.Wallet;
import com.waqiti.wallet.entity.WalletFreezeHistory;
import com.waqiti.wallet.enums.WalletStatus;
import com.waqiti.wallet.events.WalletUnfrozenEvent;
import com.waqiti.wallet.exception.WalletNotFoundException;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.repository.WalletFreezeHistoryRepository;
import com.waqiti.wallet.service.AuditService;
import com.waqiti.wallet.service.NotificationService;
import com.waqiti.wallet.service.WalletEventPublisher;
import com.waqiti.common.exception.RecoverableException;
import com.waqiti.common.exception.NonRecoverableException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import java.time.Instant;
import java.util.UUID;

/**
 * Production-grade Kafka consumer for wallet unfreeze requests.
 *
 * Features:
 * - Idempotent processing with deduplication
 * - Retry mechanism with exponential backoff
 * - Dead letter queue handling
 * - Comprehensive audit logging
 * - Metrics and monitoring
 * - Distributed tracing
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-10-18
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Validated
public class WalletUnfreezeRequestConsumer {

    private final WalletRepository walletRepository;
    private final WalletFreezeHistoryRepository freezeHistoryRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final WalletEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    // Metrics
    private static final String METRIC_PREFIX = "wallet.unfreeze.request";
    private Counter successCounter;
    private Counter failureCounter;
    private Counter duplicateCounter;
    private Counter notFrozenCounter;
    private Timer processingTimer;

    @javax.annotation.PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder(METRIC_PREFIX + ".success")
            .description("Number of successful wallet unfreeze operations")
            .tag("service", "wallet-service")
            .register(meterRegistry);

        failureCounter = Counter.builder(METRIC_PREFIX + ".failure")
            .description("Number of failed wallet unfreeze operations")
            .tag("service", "wallet-service")
            .register(meterRegistry);

        duplicateCounter = Counter.builder(METRIC_PREFIX + ".duplicate")
            .description("Number of duplicate unfreeze requests detected")
            .tag("service", "wallet-service")
            .register(meterRegistry);

        notFrozenCounter = Counter.builder(METRIC_PREFIX + ".not_frozen")
            .description("Number of unfreeze requests for wallets not frozen")
            .tag("service", "wallet-service")
            .register(meterRegistry);

        processingTimer = Timer.builder(METRIC_PREFIX + ".processing.time")
            .description("Time taken to process unfreeze requests")
            .tag("service", "wallet-service")
            .register(meterRegistry);
    }

    /**
     * Process wallet unfreeze requests with retry and DLQ handling.
     */
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(
            delay = 1000,
            multiplier = 2.0,
            maxDelay = 10000
        ),
        autoCreateTopics = "false",
        dltTopicSuffix = "-dlq",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {RecoverableException.class, org.springframework.dao.OptimisticLockingFailureException.class},
        exclude = {NonRecoverableException.class, IllegalArgumentException.class}
    )
    @KafkaListener(
        topics = "wallet.unfreeze.requested",
        groupId = "wallet-service-unfreeze-group",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "3",
        properties = {
            "max.poll.interval.ms=300000",
            "session.timeout.ms=30000",
            "heartbeat.interval.ms=10000"
        }
    )
    @Transactional(rollbackFor = Exception.class)
    public void handleWalletUnfreezeRequest(
            @Payload @Valid WalletUnfreezeRequest event,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            ConsumerRecord<String, WalletUnfreezeRequest> record
    ) {
        Timer.Sample sample = Timer.start(meterRegistry);

        log.info("Processing wallet unfreeze request - walletId: {}, reason: {}, partition: {}, offset: {}",
            event.getWalletId(), event.getReason(), partition, offset);

        try {
            // 1. Validate event
            validateUnfreezeRequest(event);

            // 2. Check for duplicate processing (idempotency)
            if (isDuplicateUnfreezeRequest(event)) {
                log.warn("Duplicate unfreeze request detected - walletId: {}, ignoring", event.getWalletId());
                duplicateCounter.increment();
                acknowledgment.acknowledge();
                return;
            }

            // 3. Fetch wallet with pessimistic lock
            Wallet wallet = walletRepository.findByIdWithLock(event.getWalletId())
                .orElseThrow(() -> new WalletNotFoundException(
                    String.format("Wallet not found: %s", event.getWalletId())
                ));

            // 4. Check if wallet is actually frozen
            if (wallet.getStatus() != WalletStatus.FROZEN) {
                log.info("Wallet is not frozen - walletId: {}, currentStatus: {}, skipping",
                    event.getWalletId(), wallet.getStatus());
                notFrozenCounter.increment();
                acknowledgment.acknowledge();
                return;
            }

            // 5. Get the active freeze history record
            WalletFreezeHistory freezeHistory = freezeHistoryRepository
                .findUnresolvedFreezeByWalletId(event.getWalletId())
                .orElse(null);

            // 6. Unfreeze the wallet
            WalletStatus previousStatus = wallet.getStatus();
            wallet.setStatus(WalletStatus.ACTIVE);
            wallet.setFreezeReason(null);
            wallet.setFrozenAt(null);
            wallet.setFrozenBy(null);
            wallet.setUpdatedAt(Instant.now());

            Wallet unfrozenWallet = walletRepository.save(wallet);

            // 7. Update freeze history record
            if (freezeHistory != null) {
                freezeHistory.setUnfrozenBy(event.getUnfrozenBy() != null ?
                    event.getUnfrozenBy() : "FRAUD_DETECTION_SYSTEM");
                freezeHistory.setUnfrozenAt(Instant.now());
                freezeHistory.calculateFreezeDuration();
                freezeHistory.setNotes(event.getReason());

                freezeHistoryRepository.save(freezeHistory);
            }

            // 8. Publish wallet unfrozen event
            WalletUnfrozenEvent unfrozenEvent = WalletUnfrozenEvent.builder()
                .walletId(event.getWalletId())
                .userId(wallet.getUserId())
                .unfreezeReason(event.getReason())
                .unfrozenAt(Instant.now())
                .correlationId(event.getCorrelationId())
                .build();

            eventPublisher.publishWalletUnfrozenEvent(unfrozenEvent);

            // 9. Send notification to user
            notificationService.sendWalletUnfrozenNotification(
                wallet.getUserId(),
                event.getWalletId(),
                event.getReason()
            );

            // 10. Audit logging
            auditService.logWalletUnfreezeAction(
                event.getWalletId(),
                wallet.getUserId(),
                event.getUnfrozenBy() != null ? event.getUnfrozenBy() : "SYSTEM",
                event.getReason(),
                previousStatus,
                WalletStatus.ACTIVE
            );

            // 11. Acknowledge successful processing
            acknowledgment.acknowledge();
            successCounter.increment();

            log.info("Successfully unfroze wallet - walletId: {}, previousStatus: {}, reason: {}",
                event.getWalletId(), previousStatus, event.getReason());

        } catch (WalletNotFoundException e) {
            log.error("Wallet not found - walletId: {}, will retry", event.getWalletId(), e);
            failureCounter.increment();
            throw new RecoverableException("Wallet not found, may be temporary", e);

        } catch (IllegalArgumentException e) {
            log.error("Invalid unfreeze request - walletId: {}, error: {}", event.getWalletId(), e.getMessage());
            failureCounter.increment();
            throw new NonRecoverableException("Invalid request data", e);

        } catch (Exception e) {
            log.error("Unexpected error unfreezing wallet - walletId: {}", event.getWalletId(), e);
            failureCounter.increment();
            throw new RecoverableException("Unexpected error, will retry", e);

        } finally {
            sample.stop(processingTimer);
        }
    }

    /**
     * Dead Letter Queue handler for failed unfreeze requests.
     */
    @DltHandler
    public void handleDlt(
            @Payload WalletUnfreezeRequest event,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
            @Header(KafkaHeaders.EXCEPTION_STACKTRACE) String stackTrace,
            @Header(KafkaHeaders.ORIGINAL_PARTITION_ID) int partition,
            @Header(KafkaHeaders.ORIGINAL_OFFSET) long offset
    ) {
        log.error("Wallet unfreeze request moved to DLQ - walletId: {}, partition: {}, offset: {}, error: {}",
            event.getWalletId(), partition, offset, exceptionMessage);

        // Create manual review ticket
        auditService.createManualReviewTicket(
            "WALLET_UNFREEZE_FAILURE",
            event.getWalletId(),
            exceptionMessage,
            stackTrace,
            "HIGH"
        );

        // Alert operations team
        notificationService.sendOperationsAlert(
            "HIGH",
            "Wallet Unfreeze Failure",
            String.format("Failed to unfreeze wallet %s after all retries. Wallet may remain frozen. Manual intervention required.",
                event.getWalletId()),
            Map.of(
                "walletId", event.getWalletId().toString(),
                "reason", event.getReason(),
                "error", exceptionMessage
            )
        );

        // Metrics
        meterRegistry.counter(METRIC_PREFIX + ".dlq",
            "reason", "max_retries_exceeded").increment();
    }

    /**
     * Validate unfreeze request data
     */
    private void validateUnfreezeRequest(WalletUnfreezeRequest event) {
        if (event.getWalletId() == null) {
            throw new IllegalArgumentException("Wallet ID cannot be null");
        }
        if (event.getReason() == null || event.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Unfreeze reason cannot be empty");
        }
        if (event.getEventId() == null) {
            throw new IllegalArgumentException("Event ID cannot be null for idempotency");
        }
    }

    /**
     * Check for duplicate processing using event ID
     */
    private boolean isDuplicateUnfreezeRequest(WalletUnfreezeRequest event) {
        // Check if event has already been processed by looking for resolved freeze with this event ID
        return freezeHistoryRepository.findByEventId(event.getEventId())
            .map(WalletFreezeHistory::isResolved)
            .orElse(false);
    }
}
