package com.waqiti.saga.orchestrator;

import com.waqiti.saga.domain.SagaExecution;
import com.waqiti.common.saga.SagaStatus;
import com.waqiti.saga.domain.SagaType;
import com.waqiti.saga.dto.MerchantPaymentRequest;
import com.waqiti.saga.dto.SagaResponse;
import com.waqiti.saga.exception.SagaExecutionException;
import com.waqiti.saga.service.SagaExecutionService;
import com.waqiti.saga.step.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Merchant Payment Saga Orchestrator
 *
 * Orchestrates distributed transactions for customer-to-merchant payments.
 * Handles payment processing fees, merchant settlement, and reconciliation.
 *
 * Use Cases:
 * - E-commerce checkout
 * - Point-of-sale payments
 * - Online service purchases
 * - Subscription payments (first charge)
 *
 * Saga Steps (9 steps):
 * 1. Validate Payment Request (merchant exists, amount valid, customer KYC)
 * 2. Verify Merchant Account Status (active, not suspended, fee agreement current)
 * 3. Check Fraud Rules (velocity, amount limits, customer history)
 * 4. Reserve Customer Funds (place hold on customer wallet)
 * 5. Debit Customer Wallet (charge customer)
 * 6. Calculate Merchant Fees (payment processing fee: 2.9% + $0.30)
 * 7. Credit Merchant Account (net amount = gross - fees)
 * 8. Record Ledger Entries (double-entry bookkeeping)
 * 9. Send Notifications (customer receipt + merchant notification)
 * 10. Update Analytics (transaction metrics, fraud signals)
 *
 * Compensation Steps (reverse order):
 * - Reverse Analytics Update
 * - Cancel Notifications
 * - Reverse Ledger Entries
 * - Reverse Merchant Credit
 * - Reverse Customer Debit
 * - Release Reserved Funds
 * - Cancel Payment
 *
 * Business Rules:
 * - Merchant fee: 2.9% + $0.30 per transaction
 * - Minimum transaction: $0.50
 * - Maximum transaction: $25,000 per transaction
 * - Merchant settlement: T+2 days (not handled by saga, async batch process)
 *
 * Compliance:
 * - PCI-DSS: Payment card data handled securely (tokenization)
 * - KYC: Customer identity verified before high-value transactions
 * - AML: Transaction monitoring for suspicious patterns
 *
 * @author Waqiti Platform Engineering Team
 * @version 1.0.0
 */
@Component
@Slf4j
public class MerchantPaymentSaga implements SagaOrchestrator<MerchantPaymentRequest> {

    private final SagaExecutionService sagaExecutionService;

    // Forward steps
    private final ValidateMerchantPaymentStep validatePaymentStep;
    private final VerifyMerchantAccountStep verifyMerchantAccountStep;
    private final CheckFraudRulesStep checkFraudRulesStep;
    private final ReserveFundsStep reserveFundsStep;
    private final DebitCustomerWalletStep debitCustomerWalletStep;
    private final CalculateMerchantFeesStep calculateMerchantFeesStep;
    private final CreditMerchantAccountStep creditMerchantAccountStep;
    private final RecordLedgerEntriesStep recordLedgerEntriesStep;
    private final SendMerchantNotificationsStep sendNotificationsStep;
    private final UpdateAnalyticsStep updateAnalyticsStep;

    // Compensation steps
    private final ReverseAnalyticsStep reverseAnalyticsStep;
    private final CancelMerchantNotificationsStep cancelNotificationsStep;
    private final ReverseLedgerEntriesStep reverseLedgerEntriesStep;
    private final ReverseMerchantCreditStep reverseMerchantCreditStep;
    private final ReverseCustomerDebitStep reverseCustomerDebitStep;
    private final ReleaseReservedFundsStep releaseReservedFundsStep;

    // Metrics
    private final Counter paymentAttempts;
    private final Counter paymentSuccesses;
    private final Counter paymentFailures;
    private final Counter compensations;
    private final Timer paymentDuration;

    public MerchantPaymentSaga(
            SagaExecutionService sagaExecutionService,
            ValidateMerchantPaymentStep validatePaymentStep,
            VerifyMerchantAccountStep verifyMerchantAccountStep,
            CheckFraudRulesStep checkFraudRulesStep,
            ReserveFundsStep reserveFundsStep,
            DebitCustomerWalletStep debitCustomerWalletStep,
            CalculateMerchantFeesStep calculateMerchantFeesStep,
            CreditMerchantAccountStep creditMerchantAccountStep,
            RecordLedgerEntriesStep recordLedgerEntriesStep,
            SendMerchantNotificationsStep sendNotificationsStep,
            UpdateAnalyticsStep updateAnalyticsStep,
            ReverseAnalyticsStep reverseAnalyticsStep,
            CancelMerchantNotificationsStep cancelNotificationsStep,
            ReverseLedgerEntriesStep reverseLedgerEntriesStep,
            ReverseMerchantCreditStep reverseMerchantCreditStep,
            ReverseCustomerDebitStep reverseCustomerDebitStep,
            ReleaseReservedFundsStep releaseReservedFundsStep,
            MeterRegistry meterRegistry) {

        this.sagaExecutionService = sagaExecutionService;
        this.validatePaymentStep = validatePaymentStep;
        this.verifyMerchantAccountStep = verifyMerchantAccountStep;
        this.checkFraudRulesStep = checkFraudRulesStep;
        this.reserveFundsStep = reserveFundsStep;
        this.debitCustomerWalletStep = debitCustomerWalletStep;
        this.calculateMerchantFeesStep = calculateMerchantFeesStep;
        this.creditMerchantAccountStep = creditMerchantAccountStep;
        this.recordLedgerEntriesStep = recordLedgerEntriesStep;
        this.sendNotificationsStep = sendNotificationsStep;
        this.updateAnalyticsStep = updateAnalyticsStep;
        this.reverseAnalyticsStep = reverseAnalyticsStep;
        this.cancelNotificationsStep = cancelNotificationsStep;
        this.reverseLedgerEntriesStep = reverseLedgerEntriesStep;
        this.reverseMerchantCreditStep = reverseMerchantCreditStep;
        this.reverseCustomerDebitStep = reverseCustomerDebitStep;
        this.releaseReservedFundsStep = releaseReservedFundsStep;

        // Initialize metrics
        this.paymentAttempts = Counter.builder("merchant_payment.attempts")
            .description("Number of merchant payment attempts")
            .tag("saga_type", "MERCHANT_PAYMENT")
            .register(meterRegistry);

        this.paymentSuccesses = Counter.builder("merchant_payment.successes")
            .description("Number of successful merchant payments")
            .tag("saga_type", "MERCHANT_PAYMENT")
            .register(meterRegistry);

        this.paymentFailures = Counter.builder("merchant_payment.failures")
            .description("Number of failed merchant payments")
            .tag("saga_type", "MERCHANT_PAYMENT")
            .register(meterRegistry);

        this.compensations = Counter.builder("merchant_payment.compensations")
            .description("Number of compensated merchant payments")
            .tag("saga_type", "MERCHANT_PAYMENT")
            .register(meterRegistry);

        this.paymentDuration = Timer.builder("merchant_payment.duration")
            .description("Merchant payment saga duration")
            .tag("saga_type", "MERCHANT_PAYMENT")
            .register(meterRegistry);
    }

    @Override
    public SagaType getSagaType() {
        return SagaType.MERCHANT_PAYMENT;
    }

    @Override
    public SagaResponse execute(MerchantPaymentRequest request) {
        String sagaId = UUID.randomUUID().toString();

        log.info("========================================");
        log.info("Starting Merchant Payment Saga: {}", sagaId);
        log.info("Customer: {} -> Merchant: {}", request.getCustomerId(), request.getMerchantId());
        log.info("Amount: {} {}", request.getAmount(), request.getCurrency());
        log.info("========================================");

        paymentAttempts.increment();
        Timer.Sample sample = Timer.start();

        // Create saga execution
        SagaExecution execution = new SagaExecution(sagaId, SagaType.MERCHANT_PAYMENT, request.getPaymentId());
        execution.setInitiatedBy(request.getCustomerId());
        execution.setTotalSteps(10);
        execution.setTimeoutAt(LocalDateTime.now().plusMinutes(10)); // 10 minutes timeout

        // Store request in context
        execution.setContextValue("request", request);
        execution.setContextValue("paymentId", request.getPaymentId());
        execution.setContextValue("customerId", request.getCustomerId());
        execution.setContextValue("merchantId", request.getMerchantId());
        execution.setContextValue("amount", request.getAmount());
        execution.setContextValue("currency", request.getCurrency());
        execution.setContextValue("description", request.getDescription());

        try {
            // Save initial execution state
            execution = sagaExecutionService.save(execution);
            execution.start();

            // Execute saga steps
            executeForwardSteps(execution);

            // Mark as completed
            execution.complete();
            sagaExecutionService.save(execution);

            sample.stop(paymentDuration);
            paymentSuccesses.increment();

            log.info("========================================");
            log.info("Merchant Payment Saga COMPLETED: {}", sagaId);
            log.info("Duration: {}ms", sample.stop(paymentDuration));
            log.info("========================================");

            return SagaResponse.success(sagaId, "Merchant payment completed successfully");

        } catch (Exception e) {
            log.error("========================================");
            log.error("Merchant Payment Saga FAILED: {}", sagaId, e);
            log.error("========================================");

            paymentFailures.increment();

            // Execute compensation
            try {
                executeCompensation(execution, e);
                execution.compensated();
                sagaExecutionService.save(execution);

                compensations.increment();
                sample.stop(paymentDuration);

                log.info("Merchant Payment Saga COMPENSATED: {}", sagaId);

                return SagaResponse.compensated(sagaId,
                    "Merchant payment failed and fully compensated: " + e.getMessage());

            } catch (Exception compensationError) {
                log.error("CRITICAL: Compensation FAILED for merchant payment saga: {}", sagaId, compensationError);
                execution.fail(e.getMessage(), "COMPENSATION_FAILED", execution.getCurrentStep());
                sagaExecutionService.save(execution);

                sample.stop(paymentDuration);

                return SagaResponse.failed(sagaId,
                    "Merchant payment failed and compensation failed - manual intervention required");
            }
        }
    }

    /**
     * Execute forward steps in order
     */
    private void executeForwardSteps(SagaExecution execution) throws SagaExecutionException {
        List<SagaStep> steps = getForwardSteps();

        for (int i = 0; i < steps.size(); i++) {
            SagaStep step = steps.get(i);
            execution.moveToNextStep(step.getStepName());
            sagaExecutionService.save(execution);

            log.info("[Saga {}] Step {}/{}: Executing {}",
                execution.getSagaId(), i + 1, steps.size(), step.getStepName());

            StepExecutionResult result = step.execute(execution);

            if (!result.isSuccess()) {
                throw new SagaExecutionException(
                    "Step failed: " + step.getStepName() + " - " + result.getErrorMessage(),
                    result.getErrorCode()
                );
            }

            // Update execution with step result
            if (result.getStepData() != null) {
                result.getStepData().forEach(execution::setContextValue);
            }

            sagaExecutionService.save(execution);
        }

        log.info("[Saga {}] All forward steps completed successfully", execution.getSagaId());
    }

    /**
     * Execute compensation steps in reverse order
     */
    private void executeCompensation(SagaExecution execution, Exception originalError) {
        log.warn("========================================");
        log.warn("Starting compensation for Merchant Payment Saga: {}", execution.getSagaId());
        log.warn("Original error: {}", originalError.getMessage());
        log.warn("Compensating {} completed steps", execution.getCurrentStepIndex());
        log.warn("========================================");

        execution.compensate();
        List<SagaStep> compensationSteps = getCompensationSteps();

        // Execute compensation steps in reverse order
        for (int i = compensationSteps.size() - 1; i >= 0; i--) {
            SagaStep step = compensationSteps.get(i);

            // Only compensate if the corresponding forward step was executed
            if (shouldCompensateStep(execution, i)) {
                log.info("[Compensation] Executing: {} for saga: {}",
                    step.getStepName(), execution.getSagaId());

                try {
                    StepExecutionResult result = step.execute(execution);

                    if (!result.isSuccess()) {
                        log.warn("Compensation step failed: {} for saga: {} - {}",
                            step.getStepName(), execution.getSagaId(), result.getErrorMessage());
                        // Continue with other compensation steps (best-effort)
                    }

                } catch (Exception e) {
                    log.error("Compensation step error: {} for saga: {}",
                        step.getStepName(), execution.getSagaId(), e);
                    // Continue with other compensation steps (best-effort)
                }
            }
        }

        log.info("========================================");
        log.info("Compensation completed for saga: {}", execution.getSagaId());
        log.info("========================================");
    }

    /**
     * Determine if a compensation step should be executed
     * based on which forward steps completed
     */
    private boolean shouldCompensateStep(SagaExecution execution, int compensationStepIndex) {
        // Compensation step i corresponds to forward step (9 - i)
        // Example: compensationSteps[9] compensates forwardSteps[0]
        int correspondingForwardStep = 9 - compensationStepIndex;
        return execution.getCurrentStepIndex() > correspondingForwardStep;
    }

    private List<SagaStep> getForwardSteps() {
        return Arrays.asList(
            validatePaymentStep,
            verifyMerchantAccountStep,
            checkFraudRulesStep,
            reserveFundsStep,
            debitCustomerWalletStep,
            calculateMerchantFeesStep,
            creditMerchantAccountStep,
            recordLedgerEntriesStep,
            sendNotificationsStep,
            updateAnalyticsStep
        );
    }

    private List<SagaStep> getCompensationSteps() {
        return Arrays.asList(
            reverseAnalyticsStep,
            cancelNotificationsStep,
            reverseLedgerEntriesStep,
            reverseMerchantCreditStep,
            reverseCustomerDebitStep,
            releaseReservedFundsStep
        );
    }

    @Override
    public SagaResponse retry(String sagaId) {
        log.info("Retrying Merchant Payment Saga: {}", sagaId);

        SagaExecution execution = sagaExecutionService.findBySagaId(sagaId)
            .orElseThrow(() -> new SagaExecutionException("Saga not found: " + sagaId));

        if (!execution.canRetry()) {
            throw new SagaExecutionException("Saga cannot be retried: " + sagaId);
        }

        execution.incrementRetryCount();
        execution.setStatus(SagaStatus.RUNNING);
        execution.setErrorMessage(null);
        execution.setErrorCode(null);

        MerchantPaymentRequest request = (MerchantPaymentRequest) execution.getContextValue("request");
        return execute(request);
    }

    @Override
    public SagaResponse cancel(String sagaId, String reason) {
        log.info("Cancelling Merchant Payment Saga: {} - Reason: {}", sagaId, reason);

        SagaExecution execution = sagaExecutionService.findBySagaId(sagaId)
            .orElseThrow(() -> new SagaExecutionException("Saga not found: " + sagaId));

        if (execution.isTerminal()) {
            throw new SagaExecutionException("Saga is already in terminal state: " + sagaId);
        }

        try {
            executeCompensation(execution, new Exception("Saga cancelled by user"));
            execution.setStatus(SagaStatus.COMPENSATED);
            execution.setErrorMessage("Cancelled by user: " + reason);
            sagaExecutionService.save(execution);

            return SagaResponse.cancelled(sagaId, "Merchant payment saga cancelled successfully");

        } catch (Exception e) {
            log.error("Failed to cancel saga: {}", sagaId, e);
            execution.fail("Cancellation failed: " + e.getMessage(), "CANCELLATION_FAILED", execution.getCurrentStep());
            sagaExecutionService.save(execution);

            return SagaResponse.failed(sagaId, "Failed to cancel saga: " + e.getMessage());
        }
    }

    @Override
    public SagaExecution getExecution(String sagaId) {
        return sagaExecutionService.findBySagaId(sagaId)
            .orElseThrow(() -> new SagaExecutionException("Saga not found: " + sagaId));
    }
}
