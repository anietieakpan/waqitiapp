package com.waqiti.payment.events.consumers;

import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.events.payment.RefundRequestEvent;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.payment.domain.Refund;
import com.waqiti.payment.domain.RefundStatus;
import com.waqiti.payment.domain.RefundType;
import com.waqiti.payment.domain.RefundReason;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.repository.RefundRepository;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.service.RefundService;
import com.waqiti.payment.service.PaymentProviderService;
import com.waqiti.payment.service.WalletService;
import com.waqiti.payment.service.RefundValidationService;
import com.waqiti.payment.service.RefundNotificationService;
import com.waqiti.common.exceptions.RefundProcessingException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade consumer for refund processing events.
 * Handles comprehensive refund operations including:
 * - Full and partial refunds
 * - Dispute-triggered refunds
 * - Automatic refunds for failed services
 * - Provider-specific refund processing
 * - Wallet balance restoration
 * - Fee reversal calculations
 *
 * IDEMPOTENCY PROTECTION:
 * - Uses eventId as primary idempotency key via IdempotencyService
 * - 14-day TTL for refund idempotency tracking
 * - CRITICAL: Prevents duplicate refunds to customers
 * - Falls back to database check for legacy events
 *
 * Critical for customer satisfaction and financial accuracy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundProcessingConsumer {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final RefundService refundService;
    private final PaymentProviderService providerService;
    private final WalletService walletService;
    private final RefundValidationService validationService;
    private final RefundNotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final AuditLogger auditLogger;
    private final MetricsService metricsService;

    private static final BigDecimal MAX_REFUND_PERCENTAGE = new BigDecimal("100");
    private static final int REFUND_EXPIRY_DAYS = 180;

    @KafkaListener(
        topics = "refund-requests",
        groupId = "payment-service-refund-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 2000, multiplier = 2.0),
        include = {RefundProcessingException.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void handleRefundRequest(
            @Payload RefundRequestEvent refundEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "correlation-id", required = false) String correlationId,
            @Header(value = "refund-priority", required = false) String priority,
            Acknowledgment acknowledgment) {

        String eventId = refundEvent.getEventId() != null ?
            refundEvent.getEventId() : UUID.randomUUID().toString();

        try {
            log.info("Processing refund request: {} for payment: {} amount: {} {}",
                    eventId, refundEvent.getPaymentId(), refundEvent.getRefundAmount(), refundEvent.getCurrency());

            // Metrics tracking
            metricsService.incrementCounter("refund.processing.started",
                Map.of(
                    "refund_type", refundEvent.getRefundType(),
                    "refund_reason", refundEvent.getRefundReason()
                ));

            // CRITICAL IDEMPOTENCY CHECK - Prevent duplicate refunds using distributed IdempotencyService
            String idempotencyKey = "refund:" + eventId;
            UUID operationId = UUID.randomUUID();
            Duration ttl = Duration.ofDays(14); // 14-day retention for refund tracking

            if (!idempotencyService.startOperation(idempotencyKey, operationId, ttl)) {
                log.warn("⚠️ DUPLICATE REFUND PREVENTED - Refund already processed: eventId={}, paymentId={}, amount={}",
                        eventId, refundEvent.getPaymentId(), refundEvent.getRefundAmount());
                metricsService.incrementCounter("refund.duplicate.prevented");
                acknowledgment.acknowledge();
                return;
            }

            // Fallback: Database idempotency check for legacy events
            if (isRefundAlreadyProcessed(refundEvent.getPaymentId(), eventId)) {
                log.info("Refund {} already processed for payment {} (database check)", eventId, refundEvent.getPaymentId());
                idempotencyService.completeOperation(idempotencyKey, operationId);
                acknowledgment.acknowledge();
                return;
            }

            // Retrieve original payment
            Payment payment = getOriginalPayment(refundEvent.getPaymentId());
            
            // Create refund record
            Refund refund = createRefundRecord(refundEvent, payment, eventId, correlationId);

            // Validate refund eligibility
            validateRefundEligibility(refund, payment, refundEvent);

            // Calculate refund amounts
            calculateRefundAmounts(refund, payment, refundEvent);

            // Process refund through provider
            processProviderRefund(refund, payment, refundEvent);

            // Update wallet balances
            updateWalletBalances(refund, payment, refundEvent);

            // Handle fee reversals
            if (refundEvent.isReverseFees()) {
                reverseFees(refund, payment);
            }

            // Update refund status
            updateRefundStatus(refund);

            // Save refund record
            Refund savedRefund = refundRepository.save(refund);

            // Update original payment status
            updatePaymentStatus(payment, savedRefund);

            // Send notifications
            sendRefundNotifications(savedRefund, payment, refundEvent);

            // Update metrics
            updateRefundMetrics(savedRefund, refundEvent);

            // Create comprehensive audit trail
            createRefundAuditLog(savedRefund, payment, refundEvent, correlationId);

            // Success metrics
            metricsService.incrementCounter("refund.processing.success",
                Map.of(
                    "refund_type", savedRefund.getRefundType().toString(),
                    "status", savedRefund.getStatus().toString(),
                    "provider", payment.getPaymentProvider()
                ));

            log.info("Successfully processed refund: {} for payment: {} amount: {} status: {}",
                    savedRefund.getId(), payment.getId(), savedRefund.getRefundAmount(), savedRefund.getStatus());

            // Mark idempotency operation as complete
            idempotencyService.completeOperation(idempotencyKey, operationId);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing refund event {}: {}", eventId, e.getMessage(), e);
            metricsService.incrementCounter("refund.processing.error");
            
            // Critical audit log for refund failures
            auditLogger.logCriticalAlert("REFUND_PROCESSING_ERROR",
                "Critical refund processing failure",
                Map.of(
                    "paymentId", refundEvent.getPaymentId(),
                    "refundAmount", refundEvent.getRefundAmount().toString(),
                    "eventId", eventId,
                    "error", e.getMessage(),
                    "correlationId", correlationId != null ? correlationId : "N/A"
                ));
            
            throw new RefundProcessingException("Failed to process refund: " + e.getMessage(), e);
        }
    }

    @KafkaListener(
        topics = "refund-requests-instant",
        groupId = "payment-service-instant-refund-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleInstantRefund(
            @Payload RefundRequestEvent refundEvent,
            @Header(value = "correlation-id", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        try {
            log.info("INSTANT REFUND: Processing immediate refund for payment: {}", 
                    refundEvent.getPaymentId());

            // Fast-track refund processing
            Refund refund = performInstantRefund(refundEvent, correlationId);

            // Immediate wallet credit
            walletService.creditInstantRefund(
                refundEvent.getUserId(),
                refund.getRefundAmount(),
                refund.getId()
            );

            // Send instant notification
            notificationService.sendInstantRefundNotification(refund);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process instant refund: {}", e.getMessage(), e);
            acknowledgment.acknowledge(); // Acknowledge to prevent blocking instant queue
        }
    }

    private boolean isRefundAlreadyProcessed(String paymentId, String eventId) {
        return refundRepository.existsByPaymentIdAndEventId(paymentId, eventId);
    }

    private Payment getOriginalPayment(String paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new RefundProcessingException("Payment not found: " + paymentId));
    }

    private Refund createRefundRecord(RefundRequestEvent event, Payment payment, String eventId, String correlationId) {
        return Refund.builder()
            .id(UUID.randomUUID().toString())
            .eventId(eventId)
            .paymentId(payment.getId())
            .userId(payment.getUserId())
            .merchantId(payment.getMerchantId())
            .refundType(RefundType.valueOf(event.getRefundType().toUpperCase()))
            .refundReason(RefundReason.valueOf(event.getRefundReason().toUpperCase()))
            .requestedAmount(event.getRefundAmount())
            .originalAmount(payment.getAmount())
            .currency(payment.getCurrency())
            .description(event.getDescription())
            .initiatedBy(event.getInitiatedBy())
            .customerNote(event.getCustomerNote())
            .status(RefundStatus.INITIATED)
            .correlationId(correlationId)
            .requestedAt(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private void validateRefundEligibility(Refund refund, Payment payment, RefundRequestEvent event) {
        // Check if payment is refundable
        if (!isPaymentRefundable(payment)) {
            throw new RefundProcessingException("Payment is not eligible for refund: " + payment.getStatus());
        }

        // Check refund window
        long daysSincePayment = ChronoUnit.DAYS.between(payment.getCreatedAt(), LocalDateTime.now());
        if (daysSincePayment > REFUND_EXPIRY_DAYS) {
            refund.setEligibilityError("Refund window expired");
            throw new RefundProcessingException("Refund window expired. Payment is " + daysSincePayment + " days old");
        }

        // Check existing refunds
        BigDecimal totalRefunded = refundRepository.getTotalRefundedAmount(payment.getId());
        BigDecimal availableForRefund = payment.getAmount().subtract(totalRefunded);

        if (event.getRefundAmount().compareTo(availableForRefund) > 0) {
            refund.setEligibilityError("Refund amount exceeds available balance");
            throw new RefundProcessingException(
                String.format("Refund amount %s exceeds available %s", 
                    event.getRefundAmount(), availableForRefund)
            );
        }

        // Validate based on refund reason
        boolean isValid = validationService.validateRefundReason(
            refund.getRefundReason(),
            payment,
            event.getSupportingDocuments()
        );

        if (!isValid) {
            refund.setEligibilityError("Refund reason validation failed");
            throw new RefundProcessingException("Refund reason validation failed");
        }

        refund.setEligibilityVerified(true);
    }

    private void calculateRefundAmounts(Refund refund, Payment payment, RefundRequestEvent event) {
        // Calculate refund percentage
        BigDecimal refundPercentage = refund.getRequestedAmount()
            .divide(payment.getAmount(), 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));

        refund.setRefundPercentage(refundPercentage);

        // Determine actual refund amount
        if (refund.getRefundType() == RefundType.FULL) {
            refund.setRefundAmount(payment.getAmount());
            refund.setRefundPercentage(MAX_REFUND_PERCENTAGE);
        } else {
            refund.setRefundAmount(event.getRefundAmount());
        }

        // Calculate fee reversal
        if (event.isReverseFees() && payment.getProcessingFee() != null) {
            BigDecimal feeReversal = payment.getProcessingFee()
                .multiply(refundPercentage)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            
            refund.setFeeReversal(feeReversal);
            refund.setNetRefundAmount(refund.getRefundAmount().add(feeReversal));
        } else {
            refund.setNetRefundAmount(refund.getRefundAmount());
        }

        // Calculate merchant debit amount
        BigDecimal merchantDebit = refund.getRefundAmount();
        if (payment.getMerchantFee() != null) {
            BigDecimal merchantFeeReversal = payment.getMerchantFee()
                .multiply(refundPercentage)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            
            merchantDebit = merchantDebit.subtract(merchantFeeReversal);
            refund.setMerchantFeeReversal(merchantFeeReversal);
        }
        
        refund.setMerchantDebitAmount(merchantDebit);
    }

    private void processProviderRefund(Refund refund, Payment payment, RefundRequestEvent event) {
        try {
            log.info("Processing refund through provider: {} for payment: {}", 
                    payment.getPaymentProvider(), payment.getId());

            // Get provider-specific refund processor
            var providerRefund = providerService.processRefund(
                payment.getPaymentProvider(),
                payment.getProviderTransactionId(),
                refund.getRefundAmount(),
                refund.getRefundReason().toString()
            );

            refund.setProviderRefundId(providerRefund.getRefundId());
            refund.setProviderStatus(providerRefund.getStatus());
            refund.setProviderProcessedAt(LocalDateTime.now());

            // Handle provider-specific responses
            if (providerRefund.isInstant()) {
                refund.setInstantRefund(true);
                refund.setStatus(RefundStatus.COMPLETED);
            } else {
                refund.setEstimatedCompletionDate(providerRefund.getEstimatedCompletionDate());
                refund.setStatus(RefundStatus.PROCESSING);
            }

            // Store provider response
            refund.setProviderResponse(providerRefund.getResponseData());

            log.info("Provider refund processed: {} with status: {}", 
                    providerRefund.getRefundId(), providerRefund.getStatus());

        } catch (Exception e) {
            log.error("Provider refund failed: {}", e.getMessage());
            refund.setProviderError(e.getMessage());
            refund.setStatus(RefundStatus.PROVIDER_FAILED);
            
            // Attempt alternative refund method
            attemptAlternativeRefund(refund, payment);
        }
    }

    private void updateWalletBalances(Refund refund, Payment payment, RefundRequestEvent event) {
        try {
            log.info("Updating wallet balances for refund: {}", refund.getId());

            // Credit customer wallet
            CompletableFuture<Boolean> customerCreditFuture = CompletableFuture.supplyAsync(() -> {
                boolean credited = walletService.creditRefund(
                    payment.getUserId(),
                    refund.getNetRefundAmount(),
                    refund.getId(),
                    refund.getRefundReason().toString()
                );
                refund.setCustomerWalletCredited(credited);
                return credited;
            });

            // Debit merchant wallet
            CompletableFuture<Boolean> merchantDebitFuture = CompletableFuture.supplyAsync(() -> {
                boolean debited = walletService.debitMerchantRefund(
                    payment.getMerchantId(),
                    refund.getMerchantDebitAmount(),
                    refund.getId()
                );
                refund.setMerchantWalletDebited(debited);
                return debited;
            });

            // Wait for both operations
            try {
                CompletableFuture.allOf(customerCreditFuture, merchantDebitFuture)
                    .get(30, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("Refund wallet update timed out after 30 seconds. Refund ID: {}", refund.getId(), e);
                List.of(customerCreditFuture, merchantDebitFuture).forEach(f -> f.cancel(true));
                throw new RefundProcessingException("Refund wallet update timed out", e);
            } catch (java.util.concurrent.ExecutionException e) {
                log.error("Refund wallet update execution failed. Refund ID: {}", refund.getId(), e.getCause());
                throw new RefundProcessingException("Refund wallet update failed: " + e.getCause().getMessage(), e.getCause());
            } catch (java.util.concurrent.InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Refund wallet update interrupted. Refund ID: {}", refund.getId(), e);
                throw new RefundProcessingException("Refund wallet update interrupted", e);
            }

            // Verify balance updates
            if (!refund.isCustomerWalletCredited() || !refund.isMerchantWalletDebited()) {
                throw new RefundProcessingException("Wallet balance update failed");
            }

            refund.setWalletUpdatedAt(LocalDateTime.now());

        } catch (Exception e) {
            log.error("Error updating wallet balances: {}", e.getMessage());
            refund.setWalletUpdateError(e.getMessage());
            throw new RefundProcessingException("Failed to update wallet balances", e);
        }
    }

    private void reverseFees(Refund refund, Payment payment) {
        try {
            log.info("Reversing fees for refund: {}", refund.getId());

            // Reverse processing fee
            if (refund.getFeeReversal() != null && refund.getFeeReversal().compareTo(BigDecimal.ZERO) > 0) {
                boolean reversed = providerService.reverseFee(
                    payment.getProviderTransactionId(),
                    refund.getFeeReversal()
                );
                refund.setFeeReversed(reversed);
            }

            // Reverse platform fee
            if (payment.getPlatformFee() != null) {
                BigDecimal platformFeeReversal = payment.getPlatformFee()
                    .multiply(refund.getRefundPercentage())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                
                refund.setPlatformFeeReversal(platformFeeReversal);
                refundService.reversePlatformFee(refund.getId(), platformFeeReversal);
            }

        } catch (Exception e) {
            log.error("Error reversing fees: {}", e.getMessage());
            refund.setFeeReversalError(e.getMessage());
            // Continue processing even if fee reversal fails
        }
    }

    private void updateRefundStatus(Refund refund) {
        if (refund.getProviderError() != null) {
            refund.setStatus(RefundStatus.FAILED);
            refund.setFailureReason(refund.getProviderError());
        } else if (refund.isInstantRefund()) {
            refund.setStatus(RefundStatus.COMPLETED);
            refund.setCompletedAt(LocalDateTime.now());
        } else if (refund.isCustomerWalletCredited() && refund.isMerchantWalletDebited()) {
            refund.setStatus(RefundStatus.PROCESSING);
        } else {
            refund.setStatus(RefundStatus.PENDING);
        }

        refund.setProcessingTimeMs(
            ChronoUnit.MILLIS.between(refund.getCreatedAt(), LocalDateTime.now())
        );
    }

    private void updatePaymentStatus(Payment payment, Refund refund) {
        try {
            // Calculate total refunded
            BigDecimal totalRefunded = refundRepository.getTotalRefundedAmount(payment.getId());
            payment.setTotalRefunded(totalRefunded);

            // Update payment status
            if (totalRefunded.compareTo(payment.getAmount()) >= 0) {
                payment.setStatus("FULLY_REFUNDED");
                payment.setFullyRefundedAt(LocalDateTime.now());
            } else if (totalRefunded.compareTo(BigDecimal.ZERO) > 0) {
                payment.setStatus("PARTIALLY_REFUNDED");
                payment.setLastRefundedAt(LocalDateTime.now());
            }

            payment.setRefundCount(payment.getRefundCount() + 1);
            paymentRepository.save(payment);

        } catch (Exception e) {
            log.error("Error updating payment status: {}", e.getMessage());
        }
    }

    private void sendRefundNotifications(Refund refund, Payment payment, RefundRequestEvent event) {
        try {
            // Customer notification
            notificationService.sendRefundNotification(refund, payment);

            // Merchant notification
            notificationService.sendMerchantRefundNotification(refund, payment);

            // Instant refund notification
            if (refund.isInstantRefund()) {
                notificationService.sendInstantRefundConfirmation(refund);
            }

            // Failed refund notification
            if (refund.getStatus() == RefundStatus.FAILED) {
                notificationService.sendRefundFailureNotification(refund);
            }

        } catch (Exception e) {
            log.error("Failed to send refund notifications: {}", e.getMessage());
        }
    }

    private void updateRefundMetrics(Refund refund, RefundRequestEvent event) {
        try {
            // Record refund metrics
            metricsService.incrementCounter("refund.processed",
                Map.of(
                    "type", refund.getRefundType().toString(),
                    "reason", refund.getRefundReason().toString(),
                    "status", refund.getStatus().toString(),
                    "instant", String.valueOf(refund.isInstantRefund())
                ));

            // Record refund amount
            metricsService.recordTimer("refund.amount", refund.getRefundAmount().doubleValue(),
                Map.of(
                    "currency", refund.getCurrency(),
                    "type", refund.getRefundType().toString()
                ));

            // Record processing time
            metricsService.recordTimer("refund.processing_time_ms", refund.getProcessingTimeMs(),
                Map.of("status", refund.getStatus().toString()));

            // Record fee reversals
            if (refund.getFeeReversal() != null) {
                metricsService.recordTimer("refund.fee_reversal", refund.getFeeReversal().doubleValue(),
                    Map.of("provider", event.getPaymentProvider()));
            }

        } catch (Exception e) {
            log.error("Failed to update refund metrics: {}", e.getMessage());
        }
    }

    private void createRefundAuditLog(Refund refund, Payment payment, RefundRequestEvent event, String correlationId) {
        auditLogger.logFinancialEvent(
            "REFUND_PROCESSED",
            refund.getUserId(),
            refund.getId(),
            refund.getRefundType().toString(),
            refund.getRefundAmount().doubleValue(),
            "refund_processor",
            refund.getStatus() != RefundStatus.FAILED,
            Map.of(
                "refundId", refund.getId(),
                "paymentId", payment.getId(),
                "refundType", refund.getRefundType().toString(),
                "refundReason", refund.getRefundReason().toString(),
                "status", refund.getStatus().toString(),
                "refundAmount", refund.getRefundAmount().toString(),
                "netRefundAmount", refund.getNetRefundAmount().toString(),
                "feeReversal", refund.getFeeReversal() != null ? refund.getFeeReversal().toString() : "0",
                "instantRefund", String.valueOf(refund.isInstantRefund()),
                "providerRefundId", refund.getProviderRefundId() != null ? refund.getProviderRefundId() : "N/A",
                "processingTimeMs", String.valueOf(refund.getProcessingTimeMs()),
                "correlationId", correlationId != null ? correlationId : "N/A",
                "eventId", event.getEventId()
            )
        );
    }

    private Refund performInstantRefund(RefundRequestEvent event, String correlationId) {
        Payment payment = getOriginalPayment(event.getPaymentId());
        Refund refund = createRefundRecord(event, payment, UUID.randomUUID().toString(), correlationId);
        
        // Quick validation
        if (!isPaymentRefundable(payment)) {
            refund.setStatus(RefundStatus.REJECTED);
            refund.setFailureReason("Payment not eligible for instant refund");
            return refundRepository.save(refund);
        }
        
        // Set instant refund properties
        refund.setInstantRefund(true);
        refund.setRefundAmount(event.getRefundAmount());
        refund.setNetRefundAmount(event.getRefundAmount());
        refund.setStatus(RefundStatus.COMPLETED);
        refund.setCompletedAt(LocalDateTime.now());
        refund.setCustomerWalletCredited(true);
        
        return refundRepository.save(refund);
    }

    private boolean isPaymentRefundable(Payment payment) {
        return "COMPLETED".equals(payment.getStatus()) ||
               "SETTLED".equals(payment.getStatus()) ||
               "PARTIALLY_REFUNDED".equals(payment.getStatus());
    }

    private void attemptAlternativeRefund(Refund refund, Payment payment) {
        try {
            log.info("Attempting alternative refund method for: {}", refund.getId());
            
            // Try direct bank refund
            boolean bankRefund = refundService.processBankRefund(
                payment.getUserId(),
                refund.getRefundAmount(),
                refund.getId()
            );
            
            if (bankRefund) {
                refund.setAlternativeRefundMethod("BANK_TRANSFER");
                refund.setStatus(RefundStatus.PROCESSING);
            }
            
        } catch (Exception e) {
            log.error("Alternative refund failed: {}", e.getMessage());
        }
    }
}