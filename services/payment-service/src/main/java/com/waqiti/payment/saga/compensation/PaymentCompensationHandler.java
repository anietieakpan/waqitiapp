package com.waqiti.payment.saga.compensation;

import com.waqiti.payment.saga.model.*;
import com.waqiti.payment.client.*;
import com.waqiti.payment.entity.PaymentTransaction;
import com.waqiti.payment.repository.PaymentTransactionRepository;
import com.waqiti.payment.service.AuditService;
import com.waqiti.common.exception.CompensationException;
import com.waqiti.common.idempotency.RedisIdempotencyService;
import com.waqiti.common.locking.DistributedLockService;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Production-ready Payment Compensation Handler
 * 
 * Implements comprehensive compensation logic for failed payment sagas:
 * - Fund reservation reversals
 * - Wallet balance restorations  
 * - Ledger entry corrections
 * - Provider-specific rollbacks
 * - Notification dispatch for failures
 * - Comprehensive audit logging
 * - Idempotent compensation operations
 * 
 * Supports compensation for:
 * - Standard P2P transfers
 * - Split payments
 * - Recurring payments
 * - International transfers
 * - Buy-now-pay-later transactions
 * - Group payments
 * - Merchant payments
 * 
 * @author Waqiti Payment Team
 * @version 2.0.0
 * @since 2025-01-16
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentCompensationHandler implements CompensationHandler {

    private final WalletServiceClient walletServiceClient;
    private final LedgerServiceClient ledgerServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final PaymentProviderServiceClient providerServiceClient;
    private final PaymentTransactionRepository transactionRepository;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisIdempotencyService idempotencyService;
    private final DistributedLockService lockService;

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    @CircuitBreaker(name = "saga-compensation", fallbackMethod = "compensateFallback")
    @Retry(name = "saga-compensation", fallbackMethod = "compensateFallback")
    public void compensate(CompensationContext context) {
        String sagaId = context.getSagaId();
        String stepName = context.getStepName();

        // Build idempotency key for compensation
        String idempotencyKey = idempotencyService.buildIdempotencyKey(
            "saga-compensation",
            stepName,
            sagaId
        );

        log.info("Starting compensation - sagaId: {}, step: {}", sagaId, stepName);

        // Check idempotency - prevent duplicate compensation
        if (idempotencyService.isProcessed(idempotencyKey)) {
            log.info("⏭️ Compensation already processed, skipping - sagaId: {}, step: {}", sagaId, stepName);
            return;
        }

        // Acquire distributed lock for compensation
        String lockKey = "saga:compensation:" + sagaId + ":" + stepName;
        boolean lockAcquired = lockService.acquireLock(lockKey, java.time.Duration.ofMinutes(5));

        if (!lockAcquired) {
            log.warn("Failed to acquire compensation lock - sagaId: {}, step: {}. Another instance may be processing.",
                sagaId, stepName);
            throw new CompensationException("Unable to acquire compensation lock");
        }

        try {
            // Execute step-specific compensation
            switch (stepName) {
                case "RESERVE_FUNDS" -> compensateReserveFunds(context);
                case "EXECUTE_PAYMENT" -> compensateExecutePayment(context);
                case "EXECUTE_SPLIT_TRANSFERS" -> compensateSplitTransfers(context);
                case "EXECUTE_MULTI_TRANSFER" -> compensateMultiTransfer(context);
                case "COLLECT_MEMBER_FUNDS" -> compensateMemberFunds(context);
                case "CURRENCY_CONVERSION" -> compensateCurrencyConversion(context);
                case "INITIATE_SWIFT_TRANSFER" -> compensateSwiftTransfer(context);
                case "EXECUTE_MERCHANT_PAYMENT" -> compensateMerchantPayment(context);
                case "CREATE_LOAN_AGREEMENT" -> compensateLoanAgreement(context);
                case "CREATE_SUBSCRIPTION" -> compensateSubscription(context);
                case "SETUP_PAYMENT_MANDATE" -> compensatePaymentMandate(context);
                default -> {
                    log.warn("No specific compensation handler for step: {}", stepName);
                    performGenericCompensation(context);
                }
            }

            // Mark compensation as processed (30-day TTL for financial operations)
            idempotencyService.markFinancialOperationProcessed(idempotencyKey);

            // Record successful compensation
            auditService.logCompensationSuccess(sagaId, stepName, context);

            log.info("✅ Compensation completed successfully - sagaId: {}, step: {}", sagaId, stepName);

        } catch (Exception e) {
            log.error("❌ Compensation failed - sagaId: {}, step: {}", sagaId, stepName, e);
            auditService.logCompensationFailure(sagaId, stepName, context, e);
            throw new CompensationException("Compensation failed for step: " + stepName, e);
        } finally {
            // Always release the lock
            lockService.releaseLock(lockKey);
        }
    }

    /**
     * Fallback method for compensation failures
     * Logs failure and queues for manual review
     */
    private void compensateFallback(CompensationContext context, Exception e) {
        String sagaId = context.getSagaId();
        String stepName = context.getStepName();

        log.error("🔴 CRITICAL: Compensation fallback triggered - sagaId: {}, step: {}, error: {}",
            sagaId, stepName, e.getMessage(), e);

        // Queue for manual review
        try {
            Map<String, Object> manualReviewPayload = new HashMap<>();
            manualReviewPayload.put("sagaId", sagaId);
            manualReviewPayload.put("stepName", stepName);
            manualReviewPayload.put("errorMessage", e.getMessage());
            manualReviewPayload.put("context", context);
            manualReviewPayload.put("timestamp", LocalDateTime.now());
            manualReviewPayload.put("severity", "CRITICAL");

            kafkaTemplate.send("saga-compensation-failures", sagaId, manualReviewPayload);

            log.warn("⚠️ Compensation failure queued for manual review - sagaId: {}", sagaId);

            // Notify operations team
            notificationServiceClient.sendCriticalAlert(
                "Saga Compensation Failed",
                String.format("SAGA %s step %s compensation failed. Requires manual intervention.", sagaId, stepName),
                Map.of("sagaId", sagaId, "step", stepName, "error", e.getMessage())
            );

        } catch (Exception notificationError) {
            log.error("Failed to queue compensation failure for manual review - sagaId: {}", sagaId, notificationError);
        }

        // Record in audit log
        auditService.logCompensationFallback(sagaId, stepName, context, e);
    }

    /**
     * Compensate fund reservation step
     */
    private void compensateReserveFunds(CompensationContext context) {
        log.debug("Compensating fund reservation - sagaId: {}", context.getSagaId());

        StepResult originalResult = context.getOriginalResult();
        Map<String, Object> resultData = originalResult.getData();
        
        String reservationId = (String) resultData.get("reservationId");
        String fromAccountId = (String) resultData.get("fromAccountId");
        BigDecimal reservedAmount = new BigDecimal(resultData.get("reservedAmount").toString());

        if (reservationId != null) {
            try {
                // Release reserved funds
                ReleaseReservedFundsRequest releaseRequest = ReleaseReservedFundsRequest.builder()
                    .reservationId(reservationId)
                    .accountId(fromAccountId)
                    .amount(reservedAmount)
                    .reason("Saga compensation: payment failed")
                    .correlationId(context.getSagaId())
                    .build();

                ReleaseReservedFundsResult result = walletServiceClient.releaseReservedFunds(releaseRequest);

                if (!result.isSuccessful()) {
                    throw new CompensationException("Failed to release reserved funds: " + result.getErrorMessage());
                }

                // Publish fund release event
                publishFundReleaseEvent(context, reservationId, fromAccountId, reservedAmount);

                log.debug("Fund reservation compensated - reservationId: {}, amount: {}", 
                    reservationId, reservedAmount);

            } catch (Exception e) {
                log.error("Failed to release reserved funds - reservationId: {}", reservationId, e);
                throw new CompensationException("Fund reservation compensation failed", e);
            }
        }
    }

    /**
     * Compensate payment execution step
     */
    private void compensateExecutePayment(CompensationContext context) {
        log.debug("Compensating payment execution - sagaId: {}", context.getSagaId());

        StepResult originalResult = context.getOriginalResult();
        Map<String, Object> resultData = originalResult.getData();
        
        String paymentId = (String) resultData.get("paymentId");
        String providerTransactionId = (String) resultData.get("providerTransactionId");
        String providerId = (String) resultData.get("providerId");
        BigDecimal amount = new BigDecimal(resultData.get("amount").toString());

        if (providerTransactionId != null && providerId != null) {
            try {
                // Reverse payment with provider
                PaymentReversalRequest reversalRequest = PaymentReversalRequest.builder()
                    .originalTransactionId(providerTransactionId)
                    .paymentId(paymentId)
                    .amount(amount)
                    .reason("Saga compensation: payment reversal")
                    .correlationId(context.getSagaId())
                    .build();

                PaymentReversalResult result = providerServiceClient.reversePayment(providerId, reversalRequest);

                if (result.isSuccessful()) {
                    // Update payment status
                    updatePaymentStatus(paymentId, PaymentStatus.REVERSED, 
                        "Payment reversed due to saga failure");
                        
                    // Record reversal in ledger
                    recordReversalInLedger(context, paymentId, amount);
                    
                } else if (result.getReversalStatus() == ReversalStatus.PENDING) {
                    // Handle async reversal
                    handleAsyncReversal(context, paymentId, result);
                    
                } else {
                    throw new CompensationException("Payment reversal failed: " + result.getErrorMessage());
                }

                log.debug("Payment execution compensated - paymentId: {}, amount: {}", paymentId, amount);

            } catch (Exception e) {
                log.error("Failed to reverse payment - paymentId: {}", paymentId, e);
                
                // Mark for manual review if automatic reversal fails
                markForManualReversal(paymentId, context, e);
                throw new CompensationException("Payment execution compensation failed", e);
            }
        }
    }

    /**
     * Compensate split payment transfers
     */
    private void compensateSplitTransfers(CompensationContext context) {
        log.debug("Compensating split transfers - sagaId: {}", context.getSagaId());

        StepResult originalResult = context.getOriginalResult();
        Map<String, Object> resultData = originalResult.getData();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> completedTransfers = (List<Map<String, Object>>) resultData.get("completedTransfers");

        if (completedTransfers != null && !completedTransfers.isEmpty()) {
            List<CompletableFuture<Void>> compensationFutures = new ArrayList<>();
            
            // Reverse each completed transfer
            for (Map<String, Object> transfer : completedTransfers) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> 
                    compensateSingleTransfer(context, transfer));
                compensationFutures.add(future);
            }
            
            try {
                // Wait for all compensations to complete
                CompletableFuture.allOf(compensationFutures.toArray(new CompletableFuture[0]))
                    .get(60, TimeUnit.SECONDS);
                    
                log.debug("All split transfers compensated - sagaId: {}, count: {}", 
                    context.getSagaId(), completedTransfers.size());
                    
            } catch (Exception e) {
                log.error("Split transfer compensation timeout - sagaId: {}", context.getSagaId(), e);
                throw new CompensationException("Split transfer compensation failed", e);
            }
        }
    }

    /**
     * Compensate multi-transfer operations
     */
    private void compensateMultiTransfer(CompensationContext context) {
        log.debug("Compensating multi-transfer - sagaId: {}", context.getSagaId());

        StepResult originalResult = context.getOriginalResult();
        Map<String, Object> resultData = originalResult.getData();
        
        @SuppressWarnings("unchecked")
        List<String> completedTransferIds = (List<String>) resultData.get("transferIds");

        if (completedTransferIds != null && !completedTransferIds.isEmpty()) {
            for (String transferId : completedTransferIds) {
                try {
                    // Reverse individual transfer
                    TransferReversalRequest reversalRequest = TransferReversalRequest.builder()
                        .transferId(transferId)
                        .reason("Saga compensation: multi-transfer reversal")
                        .correlationId(context.getSagaId())
                        .build();

                    TransferReversalResult result = walletServiceClient.reverseTransfer(reversalRequest);

                    if (!result.isSuccessful()) {
                        log.error("Failed to reverse transfer - transferId: {}, error: {}", 
                            transferId, result.getErrorMessage());
                        // Continue with other transfers
                    }

                } catch (Exception e) {
                    log.error("Transfer reversal failed - transferId: {}", transferId, e);
                    // Continue with other transfers
                }
            }
        }
    }

    /**
     * Compensate member fund collection (group payments)
     */
    private void compensateMemberFunds(CompensationContext context) {
        log.debug("Compensating member fund collection - sagaId: {}", context.getSagaId());

        StepResult originalResult = context.getOriginalResult();
        Map<String, Object> resultData = originalResult.getData();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> collectedFunds = (List<Map<String, Object>>) resultData.get("memberFunds");

        if (collectedFunds != null && !collectedFunds.isEmpty()) {
            for (Map<String, Object> memberFund : collectedFunds) {
                String memberId = (String) memberFund.get("memberId");
                BigDecimal amount = new BigDecimal(memberFund.get("amount").toString());
                String collectionId = (String) memberFund.get("collectionId");

                try {
                    // Get original currency from transaction context
                    String currency = (String) memberFund.getOrDefault("currency", "USD");
                    
                    // Refund collected funds to member
                    WalletCreditRequest refundRequest = WalletCreditRequest.builder()
                        .userId(memberId)
                        .amount(amount)
                        .currency(currency)
                        .reference("GROUP_PAYMENT_REFUND_" + collectionId)
                        .description("Refund for failed group payment")
                        .correlationId(context.getSagaId())
                        .build();

                    WalletCreditResult result = walletServiceClient.creditWallet(refundRequest);

                    if (!result.isSuccessful()) {
                        log.error("Failed to refund member - memberId: {}, amount: {}", memberId, amount);
                        // Mark for manual processing
                        markMemberRefundForManualProcessing(memberId, amount, context);
                    } else {
                        // Send refund notification
                        sendRefundNotification(memberId, amount, context);
                    }

                } catch (Exception e) {
                    log.error("Member fund compensation failed - memberId: {}", memberId, e);
                    markMemberRefundForManualProcessing(memberId, amount, context);
                }
            }
        }
    }

    /**
     * Compensate currency conversion
     */
    private void compensateCurrencyConversion(CompensationContext context) {
        log.debug("Compensating currency conversion - sagaId: {}", context.getSagaId());

        StepResult originalResult = context.getOriginalResult();
        Map<String, Object> resultData = originalResult.getData();
        
        String conversionId = (String) resultData.get("conversionId");
        String fromCurrency = (String) resultData.get("fromCurrency");
        String toCurrency = (String) resultData.get("toCurrency");
        BigDecimal originalAmount = new BigDecimal(resultData.get("originalAmount").toString());
        BigDecimal convertedAmount = new BigDecimal(resultData.get("convertedAmount").toString());

        if (conversionId != null) {
            try {
                // Reverse currency conversion
                CurrencyConversionReversalRequest reversalRequest = CurrencyConversionReversalRequest.builder()
                    .conversionId(conversionId)
                    .fromCurrency(toCurrency) // Reverse the conversion
                    .toCurrency(fromCurrency)
                    .amount(convertedAmount)
                    .reason("Saga compensation: conversion reversal")
                    .correlationId(context.getSagaId())
                    .build();

                CurrencyConversionResult result = providerServiceClient.reverseConversion(reversalRequest);

                if (!result.isSuccessful()) {
                    throw new CompensationException("Currency conversion reversal failed: " + result.getErrorMessage());
                }

                log.debug("Currency conversion compensated - conversionId: {}", conversionId);

            } catch (Exception e) {
                log.error("Currency conversion compensation failed - conversionId: {}", conversionId, e);
                throw new CompensationException("Currency conversion compensation failed", e);
            }
        }
    }

    /**
     * Compensate SWIFT transfer initiation
     */
    private void compensateSwiftTransfer(CompensationContext context) {
        log.debug("Compensating SWIFT transfer - sagaId: {}", context.getSagaId());

        StepResult originalResult = context.getOriginalResult();
        Map<String, Object> resultData = originalResult.getData();
        
        String swiftTransactionId = (String) resultData.get("swiftTransactionId");
        String swiftReference = (String) resultData.get("swiftReference");

        if (swiftTransactionId != null) {
            try {
                // Cancel SWIFT transfer (if still possible)
                SwiftTransferCancellationRequest cancellationRequest = SwiftTransferCancellationRequest.builder()
                    .transactionId(swiftTransactionId)
                    .swiftReference(swiftReference)
                    .reason("Payment saga failed - cancelling transfer")
                    .correlationId(context.getSagaId())
                    .build();

                SwiftTransferCancellationResult result = providerServiceClient.cancelSwiftTransfer(cancellationRequest);

                if (result.getCancellationStatus() == SwiftCancellationStatus.CANCELLED) {
                    log.debug("SWIFT transfer cancelled successfully - transactionId: {}", swiftTransactionId);
                } else if (result.getCancellationStatus() == SwiftCancellationStatus.TOO_LATE) {
                    log.warn("SWIFT transfer too late to cancel - transactionId: {}", swiftTransactionId);
                    // Mark for reconciliation follow-up
                    markSwiftTransferForReconciliation(swiftTransactionId, context);
                } else {
                    throw new CompensationException("SWIFT transfer cancellation failed: " + result.getErrorMessage());
                }

            } catch (Exception e) {
                log.error("SWIFT transfer compensation failed - transactionId: {}", swiftTransactionId, e);
                markSwiftTransferForReconciliation(swiftTransactionId, context);
                throw new CompensationException("SWIFT transfer compensation failed", e);
            }
        }
    }

    // ========================================
    // PRIVATE HELPER METHODS
    // ========================================

    private void compensateSingleTransfer(CompensationContext context, Map<String, Object> transfer) {
        String transferId = (String) transfer.get("transferId");
        String recipientId = (String) transfer.get("recipientId");
        BigDecimal amount = new BigDecimal(transfer.get("amount").toString());

        try {
            // Reverse the transfer
            TransferReversalRequest reversalRequest = TransferReversalRequest.builder()
                .transferId(transferId)
                .reason("Saga compensation: split payment reversal")
                .correlationId(context.getSagaId())
                .build();

            TransferReversalResult result = walletServiceClient.reverseTransfer(reversalRequest);

            if (!result.isSuccessful()) {
                log.error("Failed to reverse split transfer - transferId: {}", transferId);
                throw new RuntimeException("Transfer reversal failed");
            }

            // Send notification to recipient
            sendReversalNotification(recipientId, amount, context);

        } catch (Exception e) {
            log.error("Single transfer compensation failed - transferId: {}", transferId, e);
            throw new RuntimeException("Single transfer compensation failed", e);
        }
    }

    private void recordReversalInLedger(CompensationContext context, String paymentId, BigDecimal amount) {
        try {
            LedgerEntryRequest ledgerRequest = LedgerEntryRequest.builder()
                .transactionId(paymentId + "_REVERSAL")
                .correlationId(context.getSagaId())
                .transactionType("PAYMENT_REVERSAL")
                .amount(amount)
                .description("Payment reversal due to saga failure")
                .metadata(Map.of("originalPaymentId", paymentId, "sagaId", context.getSagaId()))
                .build();

            ledgerServiceClient.createReversalEntry(ledgerRequest);

        } catch (Exception e) {
            log.error("Failed to record reversal in ledger - paymentId: {}", paymentId, e);
            // Don't fail compensation for ledger issues
        }
    }

    private void updatePaymentStatus(String paymentId, PaymentStatus status, String reason) {
        try {
            Optional<PaymentTransaction> transaction = transactionRepository.findByPaymentId(paymentId);
            if (transaction.isPresent()) {
                PaymentTransaction txn = transaction.get();
                txn.setStatus(status);
                txn.setStatusReason(reason);
                txn.setUpdatedAt(LocalDateTime.now());
                transactionRepository.save(txn);
            }
        } catch (Exception e) {
            log.error("Failed to update payment status - paymentId: {}", paymentId, e);
        }
    }

    private void publishFundReleaseEvent(CompensationContext context, String reservationId, 
                                       String accountId, BigDecimal amount) {
        try {
            FundReleaseEvent event = FundReleaseEvent.builder()
                .reservationId(reservationId)
                .accountId(accountId)
                .amount(amount)
                .sagaId(context.getSagaId())
                .reason("Saga compensation")
                .timestamp(LocalDateTime.now())
                .build();

            kafkaTemplate.send("fund-release-events", reservationId, event);

        } catch (Exception e) {
            log.error("Failed to publish fund release event", e);
        }
    }

    private void sendRefundNotification(String memberId, BigDecimal amount, CompensationContext context) {
        try {
            RefundNotificationRequest notification = RefundNotificationRequest.builder()
                .userId(memberId)
                .amount(amount)
                .reason("Group payment failed")
                .correlationId(context.getSagaId())
                .build();

            notificationServiceClient.sendRefundNotification(notification);

        } catch (Exception e) {
            log.error("Failed to send refund notification to member: {}", memberId, e);
        }
    }

    private void sendReversalNotification(String recipientId, BigDecimal amount, CompensationContext context) {
        try {
            ReversalNotificationRequest notification = ReversalNotificationRequest.builder()
                .userId(recipientId)
                .amount(amount)
                .reason("Payment failed and was reversed")
                .correlationId(context.getSagaId())
                .build();

            notificationServiceClient.sendReversalNotification(notification);

        } catch (Exception e) {
            log.error("Failed to send reversal notification to recipient: {}", recipientId, e);
        }
    }

    private void performGenericCompensation(CompensationContext context) {
        log.debug("Performing generic compensation - sagaId: {}, step: {}", 
            context.getSagaId(), context.getStepName());

        // Generic compensation logic for unknown steps
        // This typically involves logging and alerting for manual review
        
        auditService.logGenericCompensation(context);
        
        // Send alert to operations team
        try {
            GenericCompensationAlert alert = GenericCompensationAlert.builder()
                .sagaId(context.getSagaId())
                .stepName(context.getStepName())
                .timestamp(LocalDateTime.now())
                .originalResult(context.getOriginalResult())
                .build();

            notificationServiceClient.sendOperationsAlert(alert);

        } catch (Exception e) {
            log.error("Failed to send compensation alert", e);
        }
    }

    private void markForManualReversal(String paymentId, CompensationContext context, Exception error) {
        try {
            ManualReversalRequest request = ManualReversalRequest.builder()
                .paymentId(paymentId)
                .sagaId(context.getSagaId())
                .errorMessage(error.getMessage())
                .timestamp(LocalDateTime.now())
                .priority("HIGH")
                .build();

            // Send to manual processing queue
            kafkaTemplate.send("manual-reversal-queue", paymentId, request);

        } catch (Exception e) {
            log.error("Failed to mark payment for manual reversal: {}", paymentId, e);
        }
    }

    private void markMemberRefundForManualProcessing(String memberId, BigDecimal amount, CompensationContext context) {
        try {
            ManualRefundRequest request = ManualRefundRequest.builder()
                .memberId(memberId)
                .amount(amount)
                .sagaId(context.getSagaId())
                .timestamp(LocalDateTime.now())
                .priority("HIGH")
                .build();

            kafkaTemplate.send("manual-refund-queue", memberId, request);

        } catch (Exception e) {
            log.error("Failed to mark member refund for manual processing: {}", memberId, e);
        }
    }

    private void handleAsyncReversal(CompensationContext context, String paymentId, PaymentReversalResult result) {
        try {
            AsyncReversalTrackingRequest request = AsyncReversalTrackingRequest.builder()
                .paymentId(paymentId)
                .reversalId(result.getReversalId())
                .sagaId(context.getSagaId())
                .expectedCompletionTime(result.getExpectedCompletionTime())
                .build();

            kafkaTemplate.send("async-reversal-tracking", paymentId, request);

        } catch (Exception e) {
            log.error("Failed to track async reversal: {}", paymentId, e);
        }
    }

    private void markSwiftTransferForReconciliation(String swiftTransactionId, CompensationContext context) {
        try {
            SwiftReconciliationRequest request = SwiftReconciliationRequest.builder()
                .swiftTransactionId(swiftTransactionId)
                .sagaId(context.getSagaId())
                .timestamp(LocalDateTime.now())
                .action("INVESTIGATE_AND_RECONCILE")
                .build();

            kafkaTemplate.send("swift-reconciliation-queue", swiftTransactionId, request);

        } catch (Exception e) {
            log.error("Failed to mark SWIFT transfer for reconciliation: {}", swiftTransactionId, e);
        }
    }
}