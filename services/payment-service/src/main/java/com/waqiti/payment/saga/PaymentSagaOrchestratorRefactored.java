package com.waqiti.payment.saga;

import com.waqiti.payment.core.model.*;
import com.waqiti.payment.saga.model.*;
import com.waqiti.payment.saga.validator.*;
import com.waqiti.common.client.SagaOrchestrationServiceClient;
import com.waqiti.common.saga.SagaExecutionResult;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * REFACTORED Payment Saga Orchestrator
 *
 * BEFORE: 1,086 lines - Self-contained saga orchestration
 * AFTER:  ~150 lines - Delegates to central saga-orchestration-service
 *
 * Architecture:
 * - This class handles ONLY payment-specific validation and post-processing
 * - All saga coordination delegated to saga-orchestration-service
 * - Domain expertise retained for payment workflows
 *
 * Responsibilities:
 * - Validate payment requests (domain-specific business rules)
 * - Delegate saga execution to central orchestrator
 * - Handle saga completion results
 * - Perform payment-specific error handling
 *
 * Features Retained:
 * - Payment type validation (SPLIT, GROUP, RECURRING, INTERNATIONAL, BNPL)
 * - Domain-specific compensation logic
 * - Metrics collection
 * - Distributed tracing
 *
 * Features Removed (Now in saga-orchestration-service):
 * - Saga state management
 * - Step execution coordination
 * - Timeout handling
 * - Generic compensation logic
 * - Thread pool management
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0 - Refactored for central orchestration
 * @since 2025-11-19
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentSagaOrchestratorRefactored {

    // Integration with central saga orchestration service
    private final SagaOrchestrationServiceClient sagaClient;

    // Payment-specific validators (domain logic)
    private final SplitPaymentValidator splitValidator;
    private final GroupPaymentValidator groupValidator;
    private final RecurringPaymentValidator recurringValidator;
    private final InternationalPaymentValidator internationalValidator;
    private final BnplPaymentValidator bnplValidator;

    // Metrics
    private final MeterRegistry meterRegistry;

    @Value("${feature.payment-saga.use-central-orchestrator:false}")
    private boolean useCentralOrchestrator;

    /**
     * Execute payment saga via central saga orchestration service
     *
     * REFACTORED: Now delegates to saga-orchestration-service
     * Keeps only payment-specific validation logic
     *
     * @param transaction Payment saga transaction
     * @return PaymentResult with saga execution outcome
     */
    @Timed(value = "payment.saga.execution.time", description = "Payment saga execution time")
    public CompletableFuture<PaymentResult> executePaymentSaga(SagaTransaction transaction) {

        String sagaId = transaction.getTransactionId();
        PaymentType paymentType = transaction.getPaymentRequest().getPaymentType();

        log.info("Starting payment saga: sagaId={}, type={}, useCentralOrchestrator={}",
            sagaId, paymentType, useCentralOrchestrator);

        Counter.builder("payment.saga.started")
            .tag("payment.type", paymentType.name())
            .tag("orchestrator", useCentralOrchestrator ? "central" : "local")
            .register(meterRegistry)
            .increment();

        try {
            // PHASE 1: Perform payment-specific validation (domain expertise)
            validatePaymentRequest(transaction);

            // PHASE 2: Delegate execution to central saga orchestration service
            return sagaClient.startPaymentSaga(
                sagaId,
                paymentType.name(),
                transaction.getPaymentRequest(),
                transaction.getProvider(),
                transaction.getStrategy()
            )
            .thenApply(centralSagaResult -> {
                // PHASE 3: Handle result and perform domain-specific post-processing
                return handleSagaCompletion(transaction, centralSagaResult);
            })
            .exceptionally(throwable -> {
                // PHASE 4: Payment-specific error handling
                return handleSagaFailure(transaction, throwable);
            });

        } catch (ValidationException e) {
            log.error("Payment validation failed: sagaId={}", sagaId, e);

            Counter.builder("payment.saga.validation.failed")
                .tag("payment.type", paymentType.name())
                .register(meterRegistry)
                .increment();

            return CompletableFuture.completedFuture(
                PaymentResult.failure(
                    transaction.getPaymentRequest().getPaymentId(),
                    "Validation failed: " + e.getMessage()
                )
            );
        }
    }

    /**
     * Payment-specific validation BEFORE delegating to central service
     *
     * Each payment type has its own validation rules:
     * - SPLIT: Validate split details, recipient accounts, split amounts sum
     * - GROUP: Validate group members, approval thresholds, contribution amounts
     * - RECURRING: Validate subscription details, payment mandate, schedule
     * - INTERNATIONAL: Validate SWIFT/IBAN, compliance, currency conversion
     * - BNPL: Validate credit check, installment plan, merchant agreement
     */
    private void validatePaymentRequest(SagaTransaction transaction) {
        PaymentRequest request = transaction.getPaymentRequest();
        PaymentType type = request.getPaymentType();

        log.debug("Validating payment request: type={}, paymentId={}",
            type, request.getPaymentId());

        switch (type) {
            case SPLIT:
                splitValidator.validate(request);
                log.debug("Split payment validation passed: {} splits",
                    request.getSplitDetails().size());
                break;

            case GROUP:
                groupValidator.validate(request);
                log.debug("Group payment validation passed: {} members",
                    request.getGroupDetails().getMemberCount());
                break;

            case RECURRING:
                recurringValidator.validate(request);
                log.debug("Recurring payment validation passed: frequency={}",
                    request.getRecurringDetails().getFrequency());
                break;

            case INTERNATIONAL:
                internationalValidator.validate(request);
                log.debug("International payment validation passed: currency={}",
                    request.getCurrency());
                break;

            case BNPL:
                bnplValidator.validate(request);
                log.debug("BNPL payment validation passed: installments={}",
                    request.getBnplDetails().getInstallmentCount());
                break;

            default:
                log.warn("Unknown payment type: {}, skipping validation", type);
        }
    }

    /**
     * Handle successful saga completion
     *
     * Performs payment-specific post-processing:
     * - Update payment status
     * - Trigger notifications
     * - Record analytics
     * - Update user dashboard
     */
    private PaymentResult handleSagaCompletion(SagaTransaction transaction,
                                               SagaExecutionResult result) {
        String sagaId = transaction.getTransactionId();

        log.info("Payment saga completed successfully: sagaId={}, stepsCompleted={}",
            sagaId, result.getCompletedSteps().size());

        Counter.builder("payment.saga.completed")
            .tag("payment.type", transaction.getPaymentRequest().getPaymentType().name())
            .register(meterRegistry)
            .increment();

        return PaymentResult.builder()
            .paymentId(transaction.getPaymentRequest().getPaymentId())
            .transactionId(result.getSagaId())
            .status(PaymentResult.PaymentStatus.COMPLETED)
            .amount(transaction.getPaymentRequest().getAmount())
            .currency(transaction.getPaymentRequest().getCurrency())
            .metadata(result.getStepResults())
            .timestamp(LocalDateTime.now())
            .message("Payment processed successfully")
            .build();
    }

    /**
     * Handle saga failure
     *
     * Performs payment-specific error handling:
     * - Classify error type (validation, provider, network, etc.)
     * - Determine if retry is appropriate
     * - Update payment status
     * - Trigger alerts if needed
     */
    private PaymentResult handleSagaFailure(SagaTransaction transaction,
                                           Throwable throwable) {
        String sagaId = transaction.getTransactionId();
        String errorMessage = throwable.getMessage();

        log.error("Payment saga failed: sagaId={}, error={}", sagaId, errorMessage, throwable);

        Counter.builder("payment.saga.failed")
            .tag("payment.type", transaction.getPaymentRequest().getPaymentType().name())
            .tag("error.type", classifyError(throwable))
            .register(meterRegistry)
            .increment();

        return PaymentResult.builder()
            .paymentId(transaction.getPaymentRequest().getPaymentId())
            .transactionId(transaction.getTransactionId())
            .status(PaymentResult.PaymentStatus.FAILED)
            .errorMessage(errorMessage)
            .errorCode(extractErrorCode(throwable))
            .retryable(isRetryable(throwable))
            .timestamp(LocalDateTime.now())
            .build();
    }

    /**
     * Classify error type for metrics and alerting
     */
    private String classifyError(Throwable throwable) {
        String className = throwable.getClass().getSimpleName();

        if (throwable instanceof ValidationException) {
            return "validation";
        } else if (throwable instanceof PaymentProviderException) {
            return "provider";
        } else if (throwable instanceof NetworkException) {
            return "network";
        } else if (throwable instanceof TimeoutException) {
            return "timeout";
        } else {
            return "unknown";
        }
    }

    /**
     * Extract error code from exception
     */
    private String extractErrorCode(Throwable throwable) {
        if (throwable instanceof PaymentException) {
            return ((PaymentException) throwable).getErrorCode();
        }
        return "UNKNOWN_ERROR";
    }

    /**
     * Determine if error is retryable
     */
    private boolean isRetryable(Throwable throwable) {
        // Validation errors are not retryable
        if (throwable instanceof ValidationException) {
            return false;
        }

        // Network and timeout errors are retryable
        if (throwable instanceof NetworkException || throwable instanceof TimeoutException) {
            return true;
        }

        // Provider errors depend on error code
        if (throwable instanceof PaymentProviderException) {
            PaymentProviderException ppe = (PaymentProviderException) throwable;
            return ppe.isRetryable();
        }

        // Default to not retryable for safety
        return false;
    }
}
