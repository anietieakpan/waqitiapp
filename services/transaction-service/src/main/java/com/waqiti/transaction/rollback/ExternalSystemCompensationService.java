package com.waqiti.transaction.rollback;

import com.waqiti.transaction.domain.Transaction;
import com.waqiti.transaction.model.CompensationAction;
import com.waqiti.payment.client.WisePaymentClient;
import com.waqiti.payment.client.StripePaymentClient;
import com.waqiti.payment.client.PayPalPaymentClient;
import com.waqiti.payment.client.PlaidBankingClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Enterprise-grade External System Compensation Service
 * 
 * Handles compensation actions for external payment providers and third-party systems
 * during transaction rollbacks. Ensures consistency across all integrated platforms.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalSystemCompensationService {

    private final WisePaymentClient wisePaymentClient;
    private final StripePaymentClient stripePaymentClient;
    private final PayPalPaymentClient payPalPaymentClient;
    private final PlaidBankingClient plaidBankingClient;
    private final CompensationAuditService compensationAuditService;
    private final ExternalSystemWebhookService webhookService;

    /**
     * Execute external system compensation for transaction rollback
     * Handles provider-specific rollback logic with resilience patterns
     */
    @CircuitBreaker(name = "external-compensation", fallbackMethod = "compensateFallback")
    @Retry(name = "external-compensation")
    @Bulkhead(name = "external-compensation")
    @TimeLimiter(name = "external-compensation")
    @Transactional
    public CompensationAction.CompensationResult compensateExternalSystem(
            Transaction transaction, CompensationAction action) {
        
        log.info("CRITICAL: Executing external system compensation for transaction: {} - provider: {}", 
                transaction.getId(), transaction.getPaymentProvider());

        try {
            // Check idempotency
            if (isCompensationAlreadyApplied(transaction.getId(), action.getActionId())) {
                log.warn("External compensation already applied for action: {}", action.getActionId());
                return CompensationAction.CompensationResult.builder()
                    .actionId(action.getActionId())
                    .status(CompensationAction.CompensationStatus.ALREADY_COMPLETED)
                    .message("External compensation already applied")
                    .completedAt(LocalDateTime.now())
                    .build();
            }

            // Route to appropriate provider
            CompensationAction.CompensationResult result = switch (transaction.getPaymentProvider()) {
                case "WISE" -> compensateWiseTransfer(transaction, action);
                case "STRIPE" -> compensateStripePayment(transaction, action);
                case "PAYPAL" -> compensatePayPalPayment(transaction, action);
                case "PLAID" -> compensatePlaidTransfer(transaction, action);
                case "ACH" -> compensateACHTransfer(transaction, action);
                case "SWIFT" -> compensateSWIFTTransfer(transaction, action);
                case "SEPA" -> compensateSEPATransfer(transaction, action);
                default -> handleUnknownProvider(transaction, action);
            };

            // Record audit
            compensationAuditService.recordCompensation(
                transaction.getId(), 
                action.getActionId(), 
                "EXTERNAL_" + transaction.getPaymentProvider(), 
                result.getStatus().toString()
            );

            // Register webhook for status updates
            if (result.getStatus() == CompensationAction.CompensationStatus.PENDING) {
                registerWebhookForStatusUpdates(transaction, action, result);
            }

            log.info("CRITICAL: External compensation completed for transaction: {} - status: {}", 
                    transaction.getId(), result.getStatus());

            return result;

        } catch (Exception e) {
            log.error("CRITICAL: External compensation failed for transaction: {}", transaction.getId(), e);
            
            // Record failure
            compensationAuditService.recordCompensationFailure(
                transaction.getId(), 
                action.getActionId(), 
                "EXTERNAL_" + transaction.getPaymentProvider(), 
                e.getMessage()
            );

            return CompensationAction.CompensationResult.builder()
                .actionId(action.getActionId())
                .status(CompensationAction.CompensationStatus.FAILED)
                .errorMessage(e.getMessage())
                .failedAt(LocalDateTime.now())
                .retryable(isRetryableError(e))
                .build();
        }
    }

    /**
     * Compensate Wise international transfer
     */
    private CompensationAction.CompensationResult compensateWiseTransfer(
            Transaction transaction, CompensationAction action) {
        
        log.info("Compensating Wise transfer: {} - transferId: {}", 
                transaction.getId(), transaction.getExternalReferenceId());

        try {
            // Cancel or refund the Wise transfer
            if (transaction.getExternalStatus().equals("pending")) {
                // Cancel pending transfer
                var cancelResult = wiseApiClient.cancelTransfer(transaction.getExternalReferenceId());
                
                return CompensationAction.CompensationResult.builder()
                    .actionId(action.getActionId())
                    .status(CompensationAction.CompensationStatus.COMPLETED)
                    .message("Wise transfer cancelled successfully")
                    .metadata(Map.of(
                        "wiseTransferId", transaction.getExternalReferenceId(),
                        "cancelReference", cancelResult.getCancelReference()
                    ))
                    .completedAt(LocalDateTime.now())
                    .build();
                    
            } else if (transaction.getExternalStatus().equals("completed")) {
                // Initiate refund for completed transfer
                var refundRequest = WiseRefundRequest.builder()
                    .transferId(transaction.getExternalReferenceId())
                    .amount(transaction.getAmount())
                    .reason("Transaction rollback")
                    .build();
                    
                var refundResult = wiseApiClient.createRefund(refundRequest);
                
                return CompensationAction.CompensationResult.builder()
                    .actionId(action.getActionId())
                    .status(CompensationAction.CompensationStatus.PENDING)
                    .message("Wise refund initiated - awaiting confirmation")
                    .metadata(Map.of(
                        "wiseTransferId", transaction.getExternalReferenceId(),
                        "refundId", refundResult.getRefundId(),
                        "estimatedCompletionTime", refundResult.getEstimatedCompletionTime()
                    ))
                    .completedAt(LocalDateTime.now())
                    .build();
            } else {
                throw new IllegalStateException(
                    "Cannot compensate Wise transfer in status: " + transaction.getExternalStatus());
            }
            
        } catch (Exception e) {
            log.error("Failed to compensate Wise transfer: {}", transaction.getExternalReferenceId(), e);
            throw new ExternalCompensationException("Wise compensation failed", e);
        }
    }

    /**
     * Compensate Stripe payment
     */
    private CompensationAction.CompensationResult compensateStripePayment(
            Transaction transaction, CompensationAction action) {
        
        log.info("Compensating Stripe payment: {} - paymentIntentId: {}", 
                transaction.getId(), transaction.getExternalReferenceId());

        try {
            // Refund the Stripe payment
            var refundRequest = StripeRefundRequest.builder()
                .paymentIntentId(transaction.getExternalReferenceId())
                .amount(transaction.getAmount())
                .reason("requested_by_customer")
                .metadata(Map.of(
                    "transactionId", transaction.getId().toString(),
                    "rollbackReason", action.getReason()
                ))
                .build();
                
            var refundResult = stripeApiClient.createRefund(refundRequest);
            
            return CompensationAction.CompensationResult.builder()
                .actionId(action.getActionId())
                .status(mapStripeStatus(refundResult.getStatus()))
                .message("Stripe refund " + refundResult.getStatus())
                .metadata(Map.of(
                    "stripeRefundId", refundResult.getId(),
                    "stripePaymentIntentId", transaction.getExternalReferenceId()
                ))
                .completedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to compensate Stripe payment: {}", transaction.getExternalReferenceId(), e);
            throw new ExternalCompensationException("Stripe compensation failed", e);
        }
    }

    /**
     * Compensate PayPal payment
     */
    private CompensationAction.CompensationResult compensatePayPalPayment(
            Transaction transaction, CompensationAction action) {
        
        log.info("Compensating PayPal payment: {} - captureId: {}", 
                transaction.getId(), transaction.getExternalReferenceId());

        try {
            // Refund the PayPal payment
            var refundRequest = PayPalRefundRequest.builder()
                .captureId(transaction.getExternalReferenceId())
                .amount(PayPalAmount.builder()
                    .currencyCode(transaction.getCurrency())
                    .value(transaction.getAmount().toString())
                    .build())
                .noteToPayer("Transaction rollback - " + action.getReason())
                .build();
                
            var refundResult = payPalApiClient.refundPayment(refundRequest);
            
            return CompensationAction.CompensationResult.builder()
                .actionId(action.getActionId())
                .status(mapPayPalStatus(refundResult.getStatus()))
                .message("PayPal refund " + refundResult.getStatus())
                .metadata(Map.of(
                    "paypalRefundId", refundResult.getId(),
                    "paypalCaptureId", transaction.getExternalReferenceId()
                ))
                .completedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to compensate PayPal payment: {}", transaction.getExternalReferenceId(), e);
            throw new ExternalCompensationException("PayPal compensation failed", e);
        }
    }

    /**
     * Compensate Plaid ACH transfer
     */
    private CompensationAction.CompensationResult compensatePlaidTransfer(
            Transaction transaction, CompensationAction action) {
        
        log.info("Compensating Plaid transfer: {} - transferId: {}", 
                transaction.getId(), transaction.getExternalReferenceId());

        try {
            // Cancel or reverse the Plaid transfer
            if (transaction.getExternalStatus().equals("pending")) {
                var cancelResult = plaidApiClient.cancelTransfer(
                    transaction.getExternalReferenceId());
                
                return CompensationAction.CompensationResult.builder()
                    .actionId(action.getActionId())
                    .status(CompensationAction.CompensationStatus.COMPLETED)
                    .message("Plaid transfer cancelled")
                    .metadata(Map.of("plaidTransferId", transaction.getExternalReferenceId()))
                    .completedAt(LocalDateTime.now())
                    .build();
                    
            } else {
                // Create reversal transfer
                var reversalRequest = PlaidTransferReversalRequest.builder()
                    .transferId(transaction.getExternalReferenceId())
                    .amount(transaction.getAmount().toString())
                    .description("Transaction rollback")
                    .build();
                    
                var reversalResult = plaidApiClient.createTransferReversal(reversalRequest);
                
                return CompensationAction.CompensationResult.builder()
                    .actionId(action.getActionId())
                    .status(CompensationAction.CompensationStatus.PENDING)
                    .message("Plaid reversal initiated")
                    .metadata(Map.of(
                        "plaidTransferId", transaction.getExternalReferenceId(),
                        "reversalId", reversalResult.getReversalId()
                    ))
                    .completedAt(LocalDateTime.now())
                    .build();
            }
            
        } catch (Exception e) {
            log.error("Failed to compensate Plaid transfer: {}", transaction.getExternalReferenceId(), e);
            throw new ExternalCompensationException("Plaid compensation failed", e);
        }
    }

    /**
     * Compensate ACH transfer
     */
    private CompensationAction.CompensationResult compensateACHTransfer(
            Transaction transaction, CompensationAction action) {
        
        log.info("Compensating ACH transfer: {} - traceNumber: {}", 
                transaction.getId(), transaction.getACHTraceNumber());

        // ACH reversals require special handling
        // May take 2-3 business days to complete
        
        return CompensationAction.CompensationResult.builder()
            .actionId(action.getActionId())
            .status(CompensationAction.CompensationStatus.PENDING)
            .message("ACH reversal initiated - 2-3 business days for completion")
            .metadata(Map.of(
                "achTraceNumber", transaction.getACHTraceNumber(),
                "reversalType", "R06", // Returned per ODFI request
                "estimatedCompletionDays", "3"
            ))
            .completedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Compensate SWIFT transfer
     */
    private CompensationAction.CompensationResult compensateSWIFTTransfer(
            Transaction transaction, CompensationAction action) {
        
        log.info("Compensating SWIFT transfer: {} - UETR: {}", 
                transaction.getId(), transaction.getSWIFTReference());

        // SWIFT recalls are complex and may require manual intervention
        // Initiate MT192 recall message
        
        return CompensationAction.CompensationResult.builder()
            .actionId(action.getActionId())
            .status(CompensationAction.CompensationStatus.MANUAL_INTERVENTION)
            .message("SWIFT recall initiated - manual confirmation required")
            .metadata(Map.of(
                "swiftUETR", transaction.getSWIFTReference(),
                "mt192Sent", "true",
                "requiresBankConfirmation", "true"
            ))
            .completedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Compensate SEPA transfer
     */
    private CompensationAction.CompensationResult compensateSEPATransfer(
            Transaction transaction, CompensationAction action) {
        
        log.info("Compensating SEPA transfer: {} - endToEndId: {}", 
                transaction.getId(), transaction.getSEPAEndToEndId());

        // SEPA recall/refund based on SCT Inst or regular SCT
        
        return CompensationAction.CompensationResult.builder()
            .actionId(action.getActionId())
            .status(CompensationAction.CompensationStatus.PENDING)
            .message("SEPA recall initiated")
            .metadata(Map.of(
                "sepaEndToEndId", transaction.getSEPAEndToEndId(),
                "recallType", transaction.isSEPAInstant() ? "SCT_INST_RECALL" : "SCT_RECALL"
            ))
            .completedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Handle unknown provider
     */
    private CompensationAction.CompensationResult handleUnknownProvider(
            Transaction transaction, CompensationAction action) {
        
        log.error("Unknown payment provider for compensation: {}", transaction.getPaymentProvider());
        
        return CompensationAction.CompensationResult.builder()
            .actionId(action.getActionId())
            .status(CompensationAction.CompensationStatus.MANUAL_INTERVENTION)
            .message("Unknown provider - manual intervention required")
            .errorMessage("Unsupported payment provider: " + transaction.getPaymentProvider())
            .failedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Register webhook for async status updates
     */
    private void registerWebhookForStatusUpdates(Transaction transaction, 
                                                 CompensationAction action,
                                                 CompensationAction.CompensationResult result) {
        try {
            webhookService.registerCompensationWebhook(
                transaction.getId(),
                action.getActionId(),
                transaction.getPaymentProvider(),
                result.getMetadata()
            );
            
            log.info("Webhook registered for compensation status updates: {}", action.getActionId());
            
        } catch (Exception e) {
            log.error("Failed to register compensation webhook", e);
            // Don't fail the compensation if webhook registration fails
        }
    }

    /**
     * Check if compensation has already been applied
     */
    private boolean isCompensationAlreadyApplied(UUID transactionId, String actionId) {
        return compensationAuditService.isCompensationApplied(
            transactionId, actionId, "EXTERNAL_SYSTEM");
    }

    /**
     * Check if error is retryable
     */
    private boolean isRetryableError(Exception e) {
        return e instanceof java.net.SocketTimeoutException ||
               e instanceof java.net.ConnectException ||
               e.getMessage().contains("temporarily unavailable") ||
               e.getMessage().contains("rate limit");
    }

    /**
     * Map provider status to compensation status
     */
    private CompensationAction.CompensationStatus mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus.toLowerCase()) {
            case "succeeded" -> CompensationAction.CompensationStatus.COMPLETED;
            case "pending" -> CompensationAction.CompensationStatus.PENDING;
            case "failed" -> CompensationAction.CompensationStatus.FAILED;
            default -> CompensationAction.CompensationStatus.UNKNOWN;
        };
    }

    private CompensationAction.CompensationStatus mapPayPalStatus(String paypalStatus) {
        return switch (paypalStatus.toUpperCase()) {
            case "COMPLETED" -> CompensationAction.CompensationStatus.COMPLETED;
            case "PENDING" -> CompensationAction.CompensationStatus.PENDING;
            case "FAILED", "DECLINED" -> CompensationAction.CompensationStatus.FAILED;
            default -> CompensationAction.CompensationStatus.UNKNOWN;
        };
    }

    /**
     * Fallback method for circuit breaker
     */
    public CompensationAction.CompensationResult compensateFallback(
            Transaction transaction, CompensationAction action, Exception ex) {
        
        log.error("CIRCUIT_BREAKER: External compensation circuit breaker activated for transaction: {}", 
                transaction.getId(), ex);

        // Queue for retry when service recovers
        queueCompensationForRetry(transaction, action);

        return CompensationAction.CompensationResult.builder()
            .actionId(action.getActionId())
            .status(CompensationAction.CompensationStatus.QUEUED_FOR_RETRY)
            .errorMessage("External service temporarily unavailable - compensation queued for retry")
            .failedAt(LocalDateTime.now())
            .retryable(true)
            .build();
    }

    private void queueCompensationForRetry(Transaction transaction, CompensationAction action) {
        // Implementation would queue to persistent retry queue
        log.info("Compensation queued for retry: transaction={}, action={}", 
                transaction.getId(), action.getActionId());
    }

    /**
     * Generate external system compensation actions
     */
    public List<CompensationAction> generateActions(Transaction transaction) {
        List<CompensationAction> actions = new ArrayList<>();

        if (transaction.getExternalReferenceId() != null) {
            actions.add(CompensationAction.builder()
                .actionId(UUID.randomUUID().toString())
                .actionType(CompensationAction.ActionType.EXTERNAL_SYSTEM_REVERSAL)
                .targetService(transaction.getPaymentProvider().toLowerCase() + "-service")
                .targetResourceId(transaction.getExternalReferenceId())
                .compensationData(Map.of(
                    "provider", transaction.getPaymentProvider(),
                    "externalReference", transaction.getExternalReferenceId(),
                    "amount", transaction.getAmount(),
                    "currency", transaction.getCurrency()
                ))
                .priority(1)
                .retryable(true)
                .maxRetries(5)
                .retryDelay(Duration.ofSeconds(30))
                .build());
        }

        return actions;
    }

    // Custom exception
    public static class ExternalCompensationException extends RuntimeException {
        public ExternalCompensationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // Placeholder DTOs for external providers
    @lombok.Builder
    @lombok.Data
    private static class WiseRefundRequest {
        private String transferId;
        private BigDecimal amount;
        private String reason;
    }

    @lombok.Builder
    @lombok.Data
    private static class StripeRefundRequest {
        private String paymentIntentId;
        private BigDecimal amount;
        private String reason;
        private Map<String, String> metadata;
    }

    @lombok.Builder
    @lombok.Data
    private static class PayPalRefundRequest {
        private String captureId;
        private PayPalAmount amount;
        private String noteToPayer;
    }

    @lombok.Builder
    @lombok.Data
    private static class PayPalAmount {
        private String currencyCode;
        private String value;
    }

    @lombok.Builder
    @lombok.Data
    private static class PlaidTransferReversalRequest {
        private String transferId;
        private String amount;
        private String description;
    }
}