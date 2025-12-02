package com.waqiti.payment.kafka;

import com.waqiti.common.kafka.RetryableKafkaListener;
import com.waqiti.payment.dto.WalletBalanceReservedEvent;
import com.waqiti.payment.service.PaymentAuthorizationService;
import com.waqiti.common.exception.KafkaRetryException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Wallet Balance Reserved Event Consumer
 *
 * PURPOSE: Processes wallet balance reservation events to authorize payments
 *
 * BUSINESS FLOW:
 * 1. Wallet service reserves funds for pending payment
 * 2. This consumer receives the reservation event
 * 3. Payment authorization workflow is triggered
 * 4. Timeout mechanism releases funds if payment not completed
 *
 * ERROR HANDLING:
 * - Automatic retry with exponential backoff (5 attempts)
 * - DLQ routing for permanent failures
 * - Idempotency protection via reservation ID
 * - Compensation for stuck reservations
 *
 * CRITICAL: Prevents funds from being stuck in reserved state
 *
 * @author Waqiti Payment Team
 * @version 1.0.0
 * @since 2025-10-12
 */
@Service
@Slf4j
public class WalletBalanceReservedConsumer {

    private final PaymentAuthorizationService authorizationService;
    private final ReservationTimeoutService timeoutService;
    private final Counter eventsProcessedCounter;
    private final Counter eventsFailedCounter;
    private final Timer processingTimer;

    @Autowired
    public WalletBalanceReservedConsumer(
            PaymentAuthorizationService authorizationService,
            ReservationTimeoutService timeoutService,
            MeterRegistry meterRegistry) {

        this.authorizationService = authorizationService;
        this.timeoutService = timeoutService;

        // Initialize metrics
        this.eventsProcessedCounter = Counter.builder("wallet.balance.reserved.processed")
                .description("Number of wallet balance reserved events processed")
                .tag("consumer", "payment-service")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("wallet.balance.reserved.failed")
                .description("Number of wallet balance reserved events that failed")
                .tag("consumer", "payment-service")
                .register(meterRegistry);

        this.processingTimer = Timer.builder("wallet.balance.reserved.processing.time")
                .description("Time taken to process wallet balance reserved events")
                .register(meterRegistry);
    }

    /**
     * Process wallet balance reservation event
     *
     * DLQ Configuration:
     * - Topic: wallet-events
     * - Group: payment-service-wallet-events
     * - Retries: 5 with exponential backoff
     * - DLQ Topic: wallet-events-payment-service-dlq
     */
    @RetryableKafkaListener(
        topics = "wallet-events",
        groupId = "payment-service-wallet-events",
        containerFactory = "kafkaListenerContainerFactory",
        retries = 5,
        backoffMultiplier = 2.0,
        initialBackoff = 1000L
    )
    @Transactional
    public void handleBalanceReserved(
            @Payload WalletBalanceReservedEvent event,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Instant startTime = Instant.now();

        log.info("Processing wallet balance reservation: reservationId={}, walletId={}, amount={}, partition={}, offset={}",
                event.getReservationId(),
                event.getWalletId(),
                event.getAmount(),
                partition,
                offset);

        try {
            // Step 1: Validate event
            validateEvent(event);

            // Step 2: Check idempotency - prevent duplicate processing
            if (authorizationService.isReservationAlreadyProcessed(event.getReservationId())) {
                log.info("Reservation already processed (idempotent): reservationId={}",
                        event.getReservationId());
                acknowledgment.acknowledge();
                return;
            }

            // Step 3: Process the reservation
            authorizationService.processReservation(
                    event.getWalletId(),
                    event.getAmount(),
                    event.getReservationId(),
                    event.getPaymentId(),
                    event.getReservedAt()
            );

            // Step 4: Schedule timeout cleanup (release funds if payment not completed)
            timeoutService.scheduleReservationTimeout(
                    event.getReservationId(),
                    event.getWalletId(),
                    Duration.ofMinutes(30) // 30-minute timeout
            );

            // Step 5: Record successful processing
            authorizationService.markReservationProcessed(event.getReservationId());

            // Step 6: Acknowledge message
            acknowledgment.acknowledge();

            // Metrics
            eventsProcessedCounter.increment();
            processingTimer.record(Duration.between(startTime, Instant.now()));

            log.info("Successfully processed wallet balance reservation: reservationId={}, processingTime={}ms",
                    event.getReservationId(),
                    Duration.between(startTime, Instant.now()).toMillis());

        } catch (DuplicateReservationException e) {
            // Idempotency violation - already processed
            log.warn("Duplicate reservation detected: reservationId={}", event.getReservationId());
            acknowledgment.acknowledge(); // Acknowledge to prevent reprocessing

        } catch (InsufficientBalanceException e) {
            // Business rule violation - don't retry
            log.error("Insufficient balance for reservation: reservationId={}, walletId={}",
                    event.getReservationId(), event.getWalletId());

            // Notify wallet service to release the reservation
            authorizationService.notifyReservationFailed(event.getReservationId(), e.getMessage());

            acknowledgment.acknowledge(); // Acknowledge to prevent infinite retries
            eventsFailedCounter.increment();

        } catch (WalletNotFoundException e) {
            // Wallet doesn't exist - don't retry
            log.error("Wallet not found: walletId={}", event.getWalletId());

            authorizationService.notifyReservationFailed(event.getReservationId(),
                    "Wallet not found");

            acknowledgment.acknowledge();
            eventsFailedCounter.increment();

        } catch (Exception e) {
            // Transient error - retry
            log.error("Failed to process wallet balance reservation: reservationId={}, attempt will retry",
                    event.getReservationId(), e);

            eventsFailedCounter.increment();

            // Throw exception to trigger retry mechanism
            throw new KafkaRetryException(
                    "Failed to process wallet balance reservation",
                    e,
                    event.getReservationId().toString()
            );
        }
    }

    /**
     * Validate the incoming event
     */
    private void validateEvent(WalletBalanceReservedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        if (event.getReservationId() == null) {
            throw new IllegalArgumentException("Reservation ID cannot be null");
        }

        if (event.getWalletId() == null) {
            throw new IllegalArgumentException("Wallet ID cannot be null");
        }

        if (event.getAmount() == null || event.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        if (event.getPaymentId() == null) {
            throw new IllegalArgumentException("Payment ID cannot be null");
        }

        log.debug("Event validation passed: reservationId={}", event.getReservationId());
    }

    /**
     * Handle messages sent to DLQ (Dead Letter Queue)
     *
     * This method processes messages that failed all retry attempts
     */
    @KafkaListener(topics = "wallet-events-payment-service-dlq")
    public void handleDLQMessage(@Payload WalletBalanceReservedEvent event) {
        log.error("Processing DLQ message for wallet balance reservation: reservationId={}",
                event.getReservationId());

        try {
            // Log to persistent storage for manual intervention
            authorizationService.logDLQEvent(
                    event.getReservationId(),
                    event,
                    "Permanent failure after all retry attempts"
            );

            // Notify wallet service to release the stuck reservation
            authorizationService.releaseStuckReservation(
                    event.getReservationId(),
                    event.getWalletId(),
                    "Moved to DLQ - manual intervention required"
            );

            // Alert operations team
            authorizationService.alertOperations(
                    "CRITICAL",
                    "Wallet reservation stuck in DLQ",
                    Map.of(
                            "reservationId", event.getReservationId().toString(),
                            "walletId", event.getWalletId().toString(),
                            "amount", event.getAmount().toString()
                    )
            );

        } catch (Exception e) {
            log.error("Failed to process DLQ message: reservationId={}",
                    event.getReservationId(), e);

            // Last resort - write to file for manual recovery
            writeToDLQRecoveryFile(event, e);
        }
    }

    /**
     * Write failed DLQ message to recovery file for manual processing
     */
    private void writeToDLQRecoveryFile(WalletBalanceReservedEvent event, Exception error) {
        try {
            String recoveryEntry = String.format(
                    "%s|%s|%s|%s|%s|%s%n",
                    Instant.now(),
                    event.getReservationId(),
                    event.getWalletId(),
                    event.getAmount(),
                    event.getPaymentId(),
                    error.getMessage()
            );

            // Write to recovery file (would use proper file handling in production)
            java.nio.file.Files.write(
                    java.nio.file.Paths.get("/var/log/waqiti/dlq-recovery.log"),
                    recoveryEntry.getBytes(),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND
            );

            log.info("Written DLQ message to recovery file: reservationId={}",
                    event.getReservationId());

        } catch (Exception e) {
            log.error("CRITICAL: Failed to write DLQ message to recovery file", e);
        }
    }

    // Exception classes
    public static class DuplicateReservationException extends RuntimeException {
        public DuplicateReservationException(String message) {
            super(message);
        }
    }

    public static class InsufficientBalanceException extends RuntimeException {
        public InsufficientBalanceException(String message) {
            super(message);
        }
    }

    public static class WalletNotFoundException extends RuntimeException {
        public WalletNotFoundException(String message) {
            super(message);
        }
    }
}
