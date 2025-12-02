package com.waqiti.saga.orchestrator;

import com.waqiti.saga.domain.SagaExecution;
import com.waqiti.common.saga.SagaStatus;
import com.waqiti.saga.domain.SagaType;
import com.waqiti.saga.dto.BillPaymentRequest;
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
 * Bill Payment Saga Orchestrator
 *
 * Orchestrates distributed transactions for utility and bill payments.
 * Integrates with multiple biller systems and payment processors.
 *
 * Supported Bill Types:
 * - Utilities (electricity, water, gas)
 * - Telecommunications (mobile, internet, TV)
 * - Credit cards
 * - Loans and mortgages
 * - Insurance premiums
 * - Government services (taxes, fees)
 *
 * Biller Integration:
 * - Direct biller APIs (real-time posting)
 * - Biller aggregators (CheckFree, Paymentus, ACI Worldwide)
 * - Legacy systems (batch processing)
 *
 * Saga Steps (10 steps):
 * 1. Validate Bill Payment Request (account number, amount)
 * 2. Lookup Biller Configuration (API endpoint, auth credentials)
 * 3. Verify Bill Account Status (account active, amount matches)
 * 4. Check Payment Limits (daily/monthly limits, velocity)
 * 5. Reserve Customer Funds
 * 6. Debit Customer Wallet
 * 7. Submit Payment to Biller (API call or batch file)
 * 8. Await Biller Confirmation (real-time or async)
 * 9. Record Transaction in Ledger
 * 10. Send Payment Confirmation (customer + save for records)
 * 11. Update Analytics
 *
 * Compensation Steps:
 * - Reverse Analytics
 * - Cancel Confirmation Notification
 * - Reverse Ledger Entry
 * - Request Payment Reversal from Biller (if supported)
 * - Reverse Customer Debit (credit back)
 * - Release Reserved Funds
 *
 * Business Rules:
 * - Bill payment posting: T+0 to T+3 days (depends on biller)
 * - Payment confirmation: Real-time or async (webhook)
 * - Service fee: $0.00 - $2.95 per payment (free for premium)
 * - Minimum payment: $1.00
 * - Maximum payment: $10,000 per transaction
 * - Auto-pay supported (recurring saga)
 *
 * Reconciliation:
 * - Daily reconciliation with billers
 * - Monthly statement generation
 * - Failed payment retry (3 attempts)
 * - Aging report for pending payments
 *
 * @author Waqiti Platform Engineering Team
 * @version 1.0.0
 */
@Component
@Slf4j
public class BillPaymentSaga implements SagaOrchestrator<BillPaymentRequest> {

    private final SagaExecutionService sagaExecutionService;

    // Forward steps
    private final ValidateBillPaymentStep validatePaymentStep;
    private final LookupBillerConfigurationStep lookupBillerStep;
    private final VerifyBillAccountStep verifyBillAccountStep;
    private final CheckPaymentLimitsStep checkLimitsStep;
    private final ReserveFundsStep reserveFundsStep;
    private final DebitCustomerWalletStep debitWalletStep;
    private final SubmitPaymentToBillerStep submitToBillerStep;
    private final AwaitBillerConfirmationStep awaitConfirmationStep;
    private final RecordLedgerEntriesStep recordLedgerStep;
    private final SendBillPaymentConfirmationStep sendConfirmationStep;
    private final UpdateAnalyticsStep updateAnalyticsStep;

    // Compensation steps
    private final ReverseAnalyticsStep reverseAnalyticsStep;
    private final CancelBillNotificationStep cancelNotificationStep;
    private final ReverseLedgerEntriesStep reverseLedgerStep;
    private final RequestPaymentReversalStep requestReversalStep;
    private final ReverseCustomerDebitStep reverseDebitStep;
    private final ReleaseReservedFundsStep releaseReservedFundsStep;

    // Metrics
    private final Counter paymentAttempts;
    private final Counter paymentSuccesses;
    private final Counter paymentFailures;
    private final Counter compensations;
    private final Counter billerTimeouts;
    private final Timer paymentDuration;

    public BillPaymentSaga(
            SagaExecutionService sagaExecutionService,
            ValidateBillPaymentStep validatePaymentStep,
            LookupBillerConfigurationStep lookupBillerStep,
            VerifyBillAccountStep verifyBillAccountStep,
            CheckPaymentLimitsStep checkLimitsStep,
            ReserveFundsStep reserveFundsStep,
            DebitCustomerWalletStep debitWalletStep,
            SubmitPaymentToBillerStep submitToBillerStep,
            AwaitBillerConfirmationStep awaitConfirmationStep,
            RecordLedgerEntriesStep recordLedgerStep,
            SendBillPaymentConfirmationStep sendConfirmationStep,
            UpdateAnalyticsStep updateAnalyticsStep,
            ReverseAnalyticsStep reverseAnalyticsStep,
            CancelBillNotificationStep cancelNotificationStep,
            ReverseLedgerEntriesStep reverseLedgerStep,
            RequestPaymentReversalStep requestReversalStep,
            ReverseCustomerDebitStep reverseDebitStep,
            ReleaseReservedFundsStep releaseReservedFundsStep,
            MeterRegistry meterRegistry) {

        this.sagaExecutionService = sagaExecutionService;
        this.validatePaymentStep = validatePaymentStep;
        this.lookupBillerStep = lookupBillerStep;
        this.verifyBillAccountStep = verifyBillAccountStep;
        this.checkLimitsStep = checkLimitsStep;
        this.reserveFundsStep = reserveFundsStep;
        this.debitWalletStep = debitWalletStep;
        this.submitToBillerStep = submitToBillerStep;
        this.awaitConfirmationStep = awaitConfirmationStep;
        this.recordLedgerStep = recordLedgerStep;
        this.sendConfirmationStep = sendConfirmationStep;
        this.updateAnalyticsStep = updateAnalyticsStep;
        this.reverseAnalyticsStep = reverseAnalyticsStep;
        this.cancelNotificationStep = cancelNotificationStep;
        this.reverseLedgerStep = reverseLedgerStep;
        this.requestReversalStep = requestReversalStep;
        this.reverseDebitStep = reverseDebitStep;
        this.releaseReservedFundsStep = releaseReservedFundsStep;

        // Initialize metrics
        this.paymentAttempts = Counter.builder("bill_payment.attempts")
            .description("Number of bill payment attempts")
            .tag("saga_type", "BILL_PAYMENT")
            .register(meterRegistry);

        this.paymentSuccesses = Counter.builder("bill_payment.successes")
            .description("Number of successful bill payments")
            .tag("saga_type", "BILL_PAYMENT")
            .register(meterRegistry);

        this.paymentFailures = Counter.builder("bill_payment.failures")
            .description("Number of failed bill payments")
            .tag("saga_type", "BILL_PAYMENT")
            .register(meterRegistry);

        this.compensations = Counter.builder("bill_payment.compensations")
            .description("Number of compensated bill payments")
            .tag("saga_type", "BILL_PAYMENT")
            .register(meterRegistry);

        this.billerTimeouts = Counter.builder("bill_payment.biller_timeouts")
            .description("Number of biller API timeouts")
            .tag("saga_type", "BILL_PAYMENT")
            .register(meterRegistry);

        this.paymentDuration = Timer.builder("bill_payment.duration")
            .description("Bill payment saga duration")
            .tag("saga_type", "BILL_PAYMENT")
            .register(meterRegistry);
    }

    @Override
    public SagaType getSagaType() {
        return SagaType.BILL_PAYMENT;
    }

    @Override
    public SagaResponse execute(BillPaymentRequest request) {
        String sagaId = UUID.randomUUID().toString();

        log.info("========================================");
        log.info("Starting Bill Payment Saga: {}", sagaId);
        log.info("Customer: {} | Biller: {} | Account: {}",
            request.getCustomerId(), request.getBillerName(), request.getAccountNumber());
        log.info("Amount: {} {}", request.getAmount(), request.getCurrency());
        log.info("========================================");

        paymentAttempts.increment();
        Timer.Sample sample = Timer.start();

        // Create saga execution
        SagaExecution execution = new SagaExecution(sagaId, SagaType.BILL_PAYMENT, request.getPaymentId());
        execution.setInitiatedBy(request.getCustomerId());
        execution.setTotalSteps(11);
        execution.setTimeoutAt(LocalDateTime.now().plusMinutes(20)); // 20 minutes (includes biller API time)

        // Store request in context
        execution.setContextValue("paymentId", request.getPaymentId());
        execution.setContextValue("customerId", request.getCustomerId());
        execution.setContextValue("billerId", request.getBillerId());
        execution.setContextValue("billerName", request.getBillerName());
        execution.setContextValue("accountNumber", request.getAccountNumber());
        execution.setContextValue("amount", request.getAmount());
        execution.setContextValue("currency", request.getCurrency());
        execution.setContextValue("dueDate", request.getDueDate());

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
            log.info("Bill Payment Saga COMPLETED: {}", sagaId);
            log.info("Biller: {} | Confirmation: {}",
                execution.getContextValue("billerName"),
                execution.getContextValue("billerConfirmationNumber"));
            log.info("Duration: {}ms", sample.stop(paymentDuration));
            log.info("========================================");

            return SagaResponse.success(sagaId, "Bill payment completed successfully");

        } catch (Exception e) {
            log.error("========================================");
            log.error("Bill Payment Saga FAILED: {}", sagaId, e);
            log.error("========================================");

            paymentFailures.increment();

            // Check if biller timeout
            if (isBillerTimeout(e)) {
                billerTimeouts.increment();
                log.warn("BILLER TIMEOUT: Bill payment failed due to biller API timeout - sagaId={}", sagaId);
            }

            // Execute compensation
            try {
                executeCompensation(execution, e);
                execution.compensated();
                sagaExecutionService.save(execution);

                compensations.increment();
                sample.stop(paymentDuration);

                log.info("Bill Payment Saga COMPENSATED: {}", sagaId);

                return SagaResponse.compensated(sagaId,
                    "Bill payment failed and fully compensated: " + e.getMessage());

            } catch (Exception compensationError) {
                log.error("CRITICAL: Compensation FAILED for bill payment saga: {}", sagaId, compensationError);
                execution.fail(e.getMessage(), "COMPENSATION_FAILED", execution.getCurrentStep());
                sagaExecutionService.save(execution);

                sample.stop(paymentDuration);

                return SagaResponse.failed(sagaId,
                    "Bill payment failed and compensation failed - manual intervention required");
            }
        }
    }

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

    private void executeCompensation(SagaExecution execution, Exception originalError) {
        log.warn("========================================");
        log.warn("Starting compensation for Bill Payment Saga: {}", execution.getSagaId());
        log.warn("Original error: {}", originalError.getMessage());
        log.warn("Compensating {} completed steps", execution.getCurrentStepIndex());
        log.warn("========================================");

        execution.compensate();
        List<SagaStep> compensationSteps = getCompensationSteps();

        for (int i = compensationSteps.size() - 1; i >= 0; i--) {
            SagaStep step = compensationSteps.get(i);

            if (shouldCompensateStep(execution, i)) {
                log.info("[Compensation] Executing: {} for saga: {}",
                    step.getStepName(), execution.getSagaId());

                try {
                    StepExecutionResult result = step.execute(execution);

                    if (!result.isSuccess()) {
                        log.warn("Compensation step failed: {} for saga: {} - {}",
                            step.getStepName(), execution.getSagaId(), result.getErrorMessage());
                    }

                } catch (Exception e) {
                    log.error("Compensation step error: {} for saga: {}",
                        step.getStepName(), execution.getSagaId(), e);
                }
            }
        }

        log.info("========================================");
        log.info("Compensation completed for saga: {}", execution.getSagaId());
        log.info("========================================");
    }

    private boolean shouldCompensateStep(SagaExecution execution, int compensationStepIndex) {
        int correspondingForwardStep = 10 - compensationStepIndex;
        return execution.getCurrentStepIndex() > correspondingForwardStep;
    }

    private List<SagaStep> getForwardSteps() {
        return Arrays.asList(
            validatePaymentStep,
            lookupBillerStep,
            verifyBillAccountStep,
            checkLimitsStep,
            reserveFundsStep,
            debitWalletStep,
            submitToBillerStep,
            awaitConfirmationStep,
            recordLedgerStep,
            sendConfirmationStep,
            updateAnalyticsStep
        );
    }

    private List<SagaStep> getCompensationSteps() {
        return Arrays.asList(
            reverseAnalyticsStep,
            cancelNotificationStep,
            reverseLedgerStep,
            requestReversalStep,
            reverseDebitStep,
            releaseReservedFundsStep
        );
    }

    private boolean isBillerTimeout(Exception e) {
        String message = e.getMessage();
        return message != null && (
            message.contains("TIMEOUT") ||
            message.contains("BILLER_UNAVAILABLE") ||
            message.contains("CONNECTION_TIMEOUT")
        );
    }

    @Override
    public SagaResponse retry(String sagaId) {
        log.info("Retrying Bill Payment Saga: {}", sagaId);

        SagaExecution execution = sagaExecutionService.findBySagaId(sagaId)
            .orElseThrow(() -> new SagaExecutionException("Saga not found: " + sagaId));

        if (!execution.canRetry()) {
            throw new SagaExecutionException("Saga cannot be retried: " + sagaId);
        }

        execution.incrementRetryCount();
        execution.setStatus(SagaStatus.RUNNING);
        execution.setErrorMessage(null);
        execution.setErrorCode(null);

        BillPaymentRequest request = (BillPaymentRequest) execution.getContextValue("request");
        return execute(request);
    }

    @Override
    public SagaResponse cancel(String sagaId, String reason) {
        log.info("Cancelling Bill Payment Saga: {} - Reason: {}", sagaId, reason);

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

            return SagaResponse.cancelled(sagaId, "Bill payment saga cancelled successfully");

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
