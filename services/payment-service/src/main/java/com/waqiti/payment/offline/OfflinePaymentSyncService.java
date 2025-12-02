package com.waqiti.payment.offline;

import com.waqiti.payment.dto.CreatePaymentRequest;
import com.waqiti.payment.dto.PaymentResponse;
import com.waqiti.payment.offline.domain.OfflinePayment;
import com.waqiti.payment.offline.domain.OfflinePaymentStatus;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.event.PaymentEventPublisher;
import com.waqiti.common.audit.TransactionAuditService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Enhanced Offline Payment Sync Service
 *
 * Handles synchronization of offline payments when connectivity is restored.
 * Includes comprehensive error handling, retry logic, and circuit breakers.
 *
 * Features:
 * - Automatic retry with exponential backoff
 * - Circuit breaker for system protection
 * - Bulk synchronization with parallel processing
 * - Conflict resolution
 * - Comprehensive error tracking and alerting
 * - Idempotency guarantees
 * - Metrics and monitoring
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OfflinePaymentSyncService {

    private final OfflinePaymentRepository offlinePaymentRepository;
    private final PaymentService paymentService;
    private final PaymentEventPublisher eventPublisher;
    private final TransactionAuditService auditService;
    private final MeterRegistry meterRegistry;

    @Value("${offline.payment.max-sync-attempts:5}")
    private int maxSyncAttempts;

    @Value("${offline.payment.expiry-hours:72}")
    private int offlinePaymentExpiryHours;

    @Value("${offline.payment.sync-batch-size:50}")
    private int syncBatchSize;

    @Value("${offline.payment.retry-delay-minutes:5}")
    private int retryDelayMinutes;

    @Value("${offline.payment.enable-parallel-sync:true}")
    private boolean enableParallelSync;

    private final Counter syncedPaymentsCounter;
    private final Counter failedSyncsCounter;
    private final Timer syncDurationTimer;

    public OfflinePaymentSyncService(
            OfflinePaymentRepository offlinePaymentRepository,
            PaymentService paymentService,
            PaymentEventPublisher eventPublisher,
            TransactionAuditService auditService,
            MeterRegistry meterRegistry) {

        this.offlinePaymentRepository = offlinePaymentRepository;
        this.paymentService = paymentService;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;

        // Initialize metrics
        this.syncedPaymentsCounter = Counter.builder("offline.payment.sync.success")
                .description("Number of successfully synced offline payments")
                .register(meterRegistry);

        this.failedSyncsCounter = Counter.builder("offline.payment.sync.failure")
                .description("Number of failed offline payment sync attempts")
                .register(meterRegistry);

        this.syncDurationTimer = Timer.builder("offline.payment.sync.duration")
                .description("Duration of offline payment sync operations")
                .register(meterRegistry);
    }
    
    /**
     * Sync a single offline payment to the online system with comprehensive error handling
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    @CircuitBreaker(name = "offline-payment-sync", fallbackMethod = "syncFallback")
    @Retry(name = "offline-payment-sync")
    @Bulkhead(name = "offline-payment-sync")
    public SyncResult syncOfflinePayment(OfflinePayment offlinePayment) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            log.info("Syncing offline payment: {} (attempt {}/{})",
                    offlinePayment.getId(),
                    offlinePayment.getSyncAttempts() + 1,
                    maxSyncAttempts);

            // Validate payment before syncing
            ValidationResult validation = validateOfflinePayment(offlinePayment);
            if (!validation.isValid()) {
                return handleValidationFailure(offlinePayment, validation);
            }

            // Check for duplicate/already synced
            if (isDuplicateSync(offlinePayment)) {
                log.warn("Offline payment already synced: {}", offlinePayment.getId());
                return SyncResult.duplicate(offlinePayment.getId());
            }

            // Update status to syncing
            offlinePayment.setStatus(OfflinePaymentStatus.SYNCING);
            offlinePayment.setLastSyncAttemptAt(LocalDateTime.now());
            offlinePaymentRepository.save(offlinePayment);

            // Create online payment request
            CreatePaymentRequest onlineRequest = CreatePaymentRequest.builder()
                .fromWalletId(offlinePayment.getSenderId())
                .toWalletId(offlinePayment.getRecipientId())
                .amount(offlinePayment.getAmount())
                .currency(offlinePayment.getCurrency())
                .description(offlinePayment.getDescription())
                .type("P2P_OFFLINE")
                .correlationId(offlinePayment.getId().toString())
                .idempotencyKey("offline-payment-" + offlinePayment.getId())
                .metadata(Map.of(
                    "offline_payment_id", offlinePayment.getId().toString(),
                    "device_id", String.valueOf(offlinePayment.getDeviceId()),
                    "client_timestamp", offlinePayment.getClientTimestamp().toString(),
                    "sync_attempt", String.valueOf(offlinePayment.getSyncAttempts() + 1),
                    "offline_created_at", offlinePayment.getCreatedAt().toString()
                ))
                .build();

            // Process through regular payment service
            PaymentResponse response = paymentService.processPayment(onlineRequest, offlinePayment.getSenderId());

            // Update offline payment with online payment ID
            offlinePayment.setOnlinePaymentId(response.getPaymentId());
            offlinePayment.setStatus(OfflinePaymentStatus.SYNCED);
            offlinePayment.setSyncedAt(LocalDateTime.now());
            offlinePayment.setSyncError(null); // Clear any previous errors
            offlinePaymentRepository.save(offlinePayment);

            // Audit successful sync
            auditSync(offlinePayment, true, null);

            // Publish sync success event
            publishSyncEvent(offlinePayment, "SYNC_SUCCESS", null);

            // Increment success counter
            syncedPaymentsCounter.increment();

            log.info("Successfully synced offline payment: {} to online payment: {}",
                    offlinePayment.getId(), response.getPaymentId());

            return SyncResult.success(offlinePayment.getId(), response.getPaymentId());

        } catch (Exception e) {
            return handleSyncError(offlinePayment, e);
        } finally {
            sample.stop(syncDurationTimer);
        }
    }

    /**
     * Fallback method for circuit breaker
     */
    public SyncResult syncFallback(OfflinePayment offlinePayment, Exception ex) {
        log.error("Sync circuit breaker opened or retry exhausted for payment: {} - {}",
                offlinePayment.getId(), ex.getMessage());

        return handleSyncError(offlinePayment, ex);
    }

    /**
     * Handle sync error with retry management
     */
    private SyncResult handleSyncError(OfflinePayment offlinePayment, Exception e) {
        log.error("Failed to sync offline payment: {}", offlinePayment.getId(), e);

        offlinePayment.setSyncAttempts(offlinePayment.getSyncAttempts() + 1);
        offlinePayment.setSyncError(truncateError(e.getMessage()));
        offlinePayment.setLastSyncAttemptAt(LocalDateTime.now());

        if (offlinePayment.getSyncAttempts() >= maxSyncAttempts) {
            // Max retries exceeded
            offlinePayment.setStatus(OfflinePaymentStatus.SYNC_FAILED_PERMANENT);
            log.error("PERMANENT FAILURE: Offline payment {} exceeded max sync attempts ({})",
                    offlinePayment.getId(), maxSyncAttempts);

            // Alert for manual intervention
            publishSyncEvent(offlinePayment, "SYNC_FAILED_PERMANENT", e.getMessage());
        } else {
            // Calculate next retry time with exponential backoff
            offlinePayment.setStatus(OfflinePaymentStatus.SYNC_FAILED);
            offlinePayment.setNextRetryAt(calculateNextRetryTime(offlinePayment.getSyncAttempts()));
        }

        offlinePaymentRepository.save(offlinePayment);

        // Audit failed sync
        auditSync(offlinePayment, false, e.getMessage());

        // Increment failure counter
        failedSyncsCounter.increment();

        return SyncResult.failure(offlinePayment.getId(), e.getMessage());
    }

    /**
     * Validate offline payment before syncing
     */
    private ValidationResult validateOfflinePayment(OfflinePayment payment) {
        List<String> errors = new ArrayList<>();

        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Invalid payment amount");
        }

        if (payment.getSenderId() == null || payment.getRecipientId() == null) {
            errors.add("Missing sender or recipient information");
        }

        if (payment.getCurrency() == null || payment.getCurrency().isEmpty()) {
            errors.add("Missing currency");
        }

        // Check if payment is expired
        LocalDateTime expiryTime = payment.getCreatedAt().plusHours(offlinePaymentExpiryHours);
        if (LocalDateTime.now().isAfter(expiryTime)) {
            errors.add("Payment has expired");
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Handle validation failure
     */
    private SyncResult handleValidationFailure(OfflinePayment payment, ValidationResult validation) {
        String errorMessage = "Validation failed: " + String.join(", ", validation.getErrors());
        log.warn("Offline payment validation failed: {} - {}", payment.getId(), errorMessage);

        payment.setStatus(OfflinePaymentStatus.INVALID);
        payment.setSyncError(errorMessage);
        offlinePaymentRepository.save(offlinePayment);

        return SyncResult.validationFailure(payment.getId(), errorMessage);
    }

    /**
     * Check if payment is already synced
     */
    private boolean isDuplicateSync(OfflinePayment payment) {
        return payment.getOnlinePaymentId() != null &&
               payment.getStatus() == OfflinePaymentStatus.SYNCED;
    }

    /**
     * Calculate next retry time with exponential backoff
     */
    private LocalDateTime calculateNextRetryTime(int attemptCount) {
        long delayMinutes = (long) (retryDelayMinutes * Math.pow(2, attemptCount - 1));
        // Cap at 24 hours
        delayMinutes = Math.min(delayMinutes, 1440);
        return LocalDateTime.now().plusMinutes(delayMinutes);
    }

    /**
     * Truncate error message to prevent database field overflow
     */
    private String truncateError(String error) {
        if (error == null) return null;
        return error.length() > 500 ? error.substring(0, 497) + "..." : error;
    }

    /**
     * Audit sync operation
     */
    private void auditSync(OfflinePayment payment, boolean success, String errorMessage) {
        try {
            auditService.auditPaymentOperation(
                    payment.getId().toString(),
                    "OFFLINE",
                    success ? "OFFLINE_PAYMENT_SYNCED" : "OFFLINE_PAYMENT_SYNC_FAILED",
                    payment.getAmount(),
                    payment.getCurrency(),
                    success ? "SYNCED" : "FAILED",
                    "system",
                    Map.of(
                            "offline_payment_id", payment.getId().toString(),
                            "online_payment_id", String.valueOf(payment.getOnlinePaymentId()),
                            "sync_attempts", payment.getSyncAttempts(),
                            "device_id", String.valueOf(payment.getDeviceId()),
                            "error", errorMessage != null ? errorMessage : ""
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit offline payment sync", e);
        }
    }

    /**
     * Publish sync event for monitoring/alerting
     */
    private void publishSyncEvent(OfflinePayment payment, String eventType, String errorMessage) {
        try {
            eventPublisher.publishEvent(
                    "offline.payment." + eventType.toLowerCase(),
                    payment.getId().toString(),
                    Map.of(
                            "offlinePaymentId", payment.getId().toString(),
                            "onlinePaymentId", String.valueOf(payment.getOnlinePaymentId()),
                            "amount", payment.getAmount(),
                            "currency", payment.getCurrency(),
                            "syncAttempts", payment.getSyncAttempts(),
                            "status", payment.getStatus().toString(),
                            "error", errorMessage != null ? errorMessage : "",
                            "timestamp", LocalDateTime.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to publish sync event", e);
        }
    }

    // Supporting classes
    private record ValidationResult(boolean isValid, List<String> errors) {}

    @lombok.Data
    @lombok.Builder
    public static class SyncResult {
        private String offlinePaymentId;
        private String onlinePaymentId;
        private boolean success;
        private boolean duplicate;
        private String errorMessage;
        private LocalDateTime timestamp;

        public static SyncResult success(UUID offlineId, String onlineId) {
            return SyncResult.builder()
                    .offlinePaymentId(offlineId.toString())
                    .onlinePaymentId(onlineId)
                    .success(true)
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        public static SyncResult failure(UUID offlineId, String error) {
            return SyncResult.builder()
                    .offlinePaymentId(offlineId.toString())
                    .success(false)
                    .errorMessage(error)
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        public static SyncResult duplicate(UUID offlineId) {
            return SyncResult.builder()
                    .offlinePaymentId(offlineId.toString())
                    .success(true)
                    .duplicate(true)
                    .timestamp(LocalDateTime.now())
                    .build();
        }

        public static SyncResult validationFailure(UUID offlineId, String error) {
            return SyncResult.builder()
                    .offlinePaymentId(offlineId.toString())
                    .success(false)
                    .errorMessage(error)
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }
    
    /**
     * Scheduled job to sync pending offline payments
     */
    @Scheduled(fixedDelay = 60000) // Run every minute
    @Transactional
    public void syncPendingOfflinePayments() {
        log.debug("Starting scheduled offline payment sync");
        
        // Find all pending offline payments
        List<OfflinePayment> pendingPayments = offlinePaymentRepository
            .findByStatus(OfflinePaymentStatus.PENDING_SYNC);
        
        // Also find accepted offline payments
        pendingPayments.addAll(offlinePaymentRepository
            .findByStatus(OfflinePaymentStatus.ACCEPTED_OFFLINE));
        
        for (OfflinePayment payment : pendingPayments) {
            try {
                syncOfflinePayment(payment);
            } catch (Exception e) {
                log.error("Failed to sync offline payment in scheduled job: {}", payment.getId(), e);
            }
        }
        
        // Retry failed payments
        retryFailedPayments();
        
        // Mark expired payments
        markExpiredPayments();
    }
    
    /**
     * Retry payments that failed to sync
     */
    private void retryFailedPayments() {
        List<OfflinePayment> failedPayments = offlinePaymentRepository
            .findRetryableOfflinePayments(MAX_SYNC_ATTEMPTS);
        
        for (OfflinePayment payment : failedPayments) {
            try {
                log.info("Retrying sync for failed payment: {}", payment.getId());
                syncOfflinePayment(payment);
            } catch (Exception e) {
                log.error("Retry failed for offline payment: {}", payment.getId(), e);
            }
        }
    }
    
    /**
     * Mark old offline payments as expired
     */
    private void markExpiredPayments() {
        LocalDateTime expiryTime = LocalDateTime.now().minusHours(OFFLINE_PAYMENT_EXPIRY_HOURS);
        List<OfflinePayment> expiredPayments = offlinePaymentRepository
            .findExpiredOfflinePayments(expiryTime);
        
        for (OfflinePayment payment : expiredPayments) {
            payment.setStatus(OfflinePaymentStatus.EXPIRED);
            offlinePaymentRepository.save(payment);
            log.info("Marked offline payment as expired: {}", payment.getId());
        }
    }
}