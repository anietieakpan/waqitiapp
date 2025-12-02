package com.waqiti.saga.orchestrator;

import com.waqiti.saga.domain.SagaExecution;
import com.waqiti.common.saga.SagaStatus;
import com.waqiti.saga.domain.SagaType;
import com.waqiti.saga.dto.InternationalTransferRequest;
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
import java.util.Map;
import java.util.UUID;

/**
 * International Transfer Saga Orchestrator
 *
 * CRITICAL: Production-grade distributed saga for cross-border money transfers
 *
 * COMPLEXITY: International transfers are the most complex transaction type:
 * - Multi-currency conversion
 * - Cross-border compliance (SWIFT, OFAC, sanctions screening)
 * - Foreign exchange rate locking
 * - Correspondent banking network
 * - Regulatory reporting (CTR, FBAR, etc.)
 * - Settlement across multiple time zones
 *
 * SAGA ARCHITECTURE:
 * This saga coordinates 12+ steps across 6+ microservices with full compensation.
 * Each step is idempotent and supports retry with exponential backoff.
 *
 * FORWARD STEPS (Happy Path):
 * 1. Validate Transfer Request (limits, KYC, AML, account status)
 * 2. Screen Sanctions (OFAC, EU, UN sanctions lists)
 * 3. Check Compliance (cross-border reporting requirements)
 * 4. Lock Exchange Rate (prevent rate fluctuation during transfer)
 * 5. Calculate Fees (including FX spread, wire fees, intermediary fees)
 * 6. Reserve Funds (place hold on source wallet)
 * 7. Debit Source Wallet (remove funds in source currency)
 * 8. Execute FX Conversion (convert to destination currency)
 * 9. Route via Correspondent Banks (SWIFT network)
 * 10. Credit Destination Account (add funds in destination currency)
 * 11. Record Regulatory Reports (CTR, FBAR, SAR if needed)
 * 12. Send Notifications (notify sender, receiver, regulators)
 * 13. Update Analytics (fraud detection, AML scoring)
 *
 * COMPENSATION STEPS (Rollback on Failure):
 * - Executed in reverse order
 * - Each step has a compensation handler
 * - Idempotent operations prevent duplicate reversals
 * - Audit trail maintained for all compensations
 *
 * FAILURE SCENARIOS:
 * - Sanctions hit: Freeze funds, alert compliance, file SAR
 * - Insufficient funds: Immediate rejection before debit
 * - FX rate expiration: Re-quote or cancel
 * - Correspondent bank failure: Retry or alternative route
 * - Destination account closure: Reverse and refund
 * - Regulatory rejection: Compliance review required
 *
 * PERFORMANCE:
 * - Happy path: 2-5 seconds (real-time)
 * - With correspondent banks: 30 seconds - 5 minutes
 * - International clearing: 1-3 business days (batched)
 *
 * COMPLIANCE:
 * - BSA/AML: Full transaction monitoring
 * - OFAC: Real-time sanctions screening
 * - FATF: Travel rule compliance (sender/receiver info)
 * - FinCEN: Automated CTR/SAR filing
 * - GDPR: Data privacy for EU transfers
 *
 * @author Waqiti Platform Engineering Team
 * @version 3.0.0
 */
@Component
@Slf4j
public class InternationalTransferSaga implements SagaOrchestrator<InternationalTransferRequest> {

    private final SagaExecutionService sagaExecutionService;

    // Forward steps
    private final ValidateInternationalTransferStep validateTransferStep;
    private final SanctionsScreeningStep sanctionsScreeningStep;
    private final CompliancePreCheckStep compliancePreCheckStep;
    private final LockExchangeRateStep lockExchangeRateStep;
    private final CalculateInternationalFeesStep calculateFeesStep;
    private final ReserveFundsStep reserveFundsStep;
    private final DebitSourceWalletStep debitSourceWalletStep;
    private final ExecuteFXConversionStep executeFXConversionStep;
    private final RouteViaCorrespondentBanksStep routeViaCorrespondentBanksStep;
    private final CreditDestinationAccountStep creditDestinationAccountStep;
    private final RecordRegulatoryReportsStep recordRegulatoryReportsStep;
    private final SendInternationalNotificationsStep sendNotificationsStep;
    private final UpdateAMLAnalyticsStep updateAnalyticsStep;

    // Compensation steps
    private final ReverseAMLAnalyticsStep reverseAnalyticsStep;
    private final CancelInternationalNotificationsStep cancelNotificationsStep;
    private final ReverseCreditDestinationStep reverseCreditStep;
    private final RecallCorrespondentBankTransferStep recallCorrespondentTransferStep;
    private final ReverseFXConversionStep reverseFXConversionStep;
    private final ReverseDebitSourceWalletStep reverseDebitStep;
    private final ReleaseReservedFundsStep releaseReservedFundsStep;
    private final UnlockExchangeRateStep unlockExchangeRateStep;
    private final FileSAROnFailureStep fileSAROnFailureStep;

    // Metrics
    private final Counter transferAttempts;
    private final Counter transferSuccesses;
    private final Counter transferFailures;
    private final Counter compensations;
    private final Timer transferDuration;

    public InternationalTransferSaga(
            SagaExecutionService sagaExecutionService,
            ValidateInternationalTransferStep validateTransferStep,
            SanctionsScreeningStep sanctionsScreeningStep,
            CompliancePreCheckStep compliancePreCheckStep,
            LockExchangeRateStep lockExchangeRateStep,
            CalculateInternationalFeesStep calculateFeesStep,
            ReserveFundsStep reserveFundsStep,
            DebitSourceWalletStep debitSourceWalletStep,
            ExecuteFXConversionStep executeFXConversionStep,
            RouteViaCorrespondentBanksStep routeViaCorrespondentBanksStep,
            CreditDestinationAccountStep creditDestinationAccountStep,
            RecordRegulatoryReportsStep recordRegulatoryReportsStep,
            SendInternationalNotificationsStep sendNotificationsStep,
            UpdateAMLAnalyticsStep updateAnalyticsStep,
            ReverseAMLAnalyticsStep reverseAnalyticsStep,
            CancelInternationalNotificationsStep cancelNotificationsStep,
            ReverseCreditDestinationStep reverseCreditStep,
            RecallCorrespondentBankTransferStep recallCorrespondentTransferStep,
            ReverseFXConversionStep reverseFXConversionStep,
            ReverseDebitSourceWalletStep reverseDebitStep,
            ReleaseReservedFundsStep releaseReservedFundsStep,
            UnlockExchangeRateStep unlockExchangeRateStep,
            FileSAROnFailureStep fileSAROnFailureStep,
            MeterRegistry meterRegistry) {

        this.sagaExecutionService = sagaExecutionService;
        this.validateTransferStep = validateTransferStep;
        this.sanctionsScreeningStep = sanctionsScreeningStep;
        this.compliancePreCheckStep = compliancePreCheckStep;
        this.lockExchangeRateStep = lockExchangeRateStep;
        this.calculateFeesStep = calculateFeesStep;
        this.reserveFundsStep = reserveFundsStep;
        this.debitSourceWalletStep = debitSourceWalletStep;
        this.executeFXConversionStep = executeFXConversionStep;
        this.routeViaCorrespondentBanksStep = routeViaCorrespondentBanksStep;
        this.creditDestinationAccountStep = creditDestinationAccountStep;
        this.recordRegulatoryReportsStep = recordRegulatoryReportsStep;
        this.sendNotificationsStep = sendNotificationsStep;
        this.updateAnalyticsStep = updateAnalyticsStep;
        this.reverseAnalyticsStep = reverseAnalyticsStep;
        this.cancelNotificationsStep = cancelNotificationsStep;
        this.reverseCreditStep = reverseCreditStep;
        this.recallCorrespondentTransferStep = recallCorrespondentTransferStep;
        this.reverseFXConversionStep = reverseFXConversionStep;
        this.reverseDebitStep = reverseDebitStep;
        this.releaseReservedFundsStep = releaseReservedFundsStep;
        this.unlockExchangeRateStep = unlockExchangeRateStep;
        this.fileSAROnFailureStep = fileSAROnFailureStep;

        // Initialize metrics
        this.transferAttempts = Counter.builder("international_transfer.attempts")
            .description("Number of international transfer attempts")
            .register(meterRegistry);

        this.transferSuccesses = Counter.builder("international_transfer.successes")
            .description("Number of successful international transfers")
            .register(meterRegistry);

        this.transferFailures = Counter.builder("international_transfer.failures")
            .description("Number of failed international transfers")
            .register(meterRegistry);

        this.compensations = Counter.builder("international_transfer.compensations")
            .description("Number of compensated international transfers")
            .register(meterRegistry);

        this.transferDuration = Timer.builder("international_transfer.duration")
            .description("International transfer saga duration")
            .register(meterRegistry);
    }

    @Override
    public SagaType getSagaType() {
        return SagaType.INTERNATIONAL_TRANSFER;
    }

    @Override
    public SagaResponse execute(InternationalTransferRequest request) {
        String sagaId = UUID.randomUUID().toString();

        log.info("========================================");
        log.info("Starting International Transfer Saga: {}", sagaId);
        log.info("From: {} ({}) -> To: {} ({})",
            request.getSenderName(), request.getSourceCountry(),
            request.getRecipientName(), request.getDestinationCountry());
        log.info("Amount: {} {} -> {} {}",
            request.getSourceAmount(), request.getSourceCurrency(),
            request.getDestinationAmount(), request.getDestinationCurrency());
        log.info("========================================");

        transferAttempts.increment();
        Timer.Sample sample = Timer.start();

        // Create saga execution
        SagaExecution execution = new SagaExecution(sagaId, SagaType.INTERNATIONAL_TRANSFER, request.getTransferId());
        execution.setInitiatedBy(request.getSenderId());
        execution.setTotalSteps(13);
        execution.setTimeoutAt(LocalDateTime.now().plusHours(2)); // 2 hour timeout for international transfers

        // Store comprehensive context
        execution.setContextValue("request", request);
        execution.setContextValue("transferId", request.getTransferId());
        execution.setContextValue("senderId", request.getSenderId());
        execution.setContextValue("recipientId", request.getRecipientId());
        execution.setContextValue("sourceAmount", request.getSourceAmount());
        execution.setContextValue("sourceCurrency", request.getSourceCurrency());
        execution.setContextValue("destinationAmount", request.getDestinationAmount());
        execution.setContextValue("destinationCurrency", request.getDestinationCurrency());
        execution.setContextValue("sourceCountry", request.getSourceCountry());
        execution.setContextValue("destinationCountry", request.getDestinationCountry());
        execution.setContextValue("purpose", request.getPurpose());
        execution.setContextValue("swiftCode", request.getSwiftCode());
        execution.setContextValue("iban", request.getIban());

        try {
            // Save initial execution state
            execution = sagaExecutionService.save(execution);
            execution.start();

            // Execute saga steps
            executeForwardSteps(execution);

            // Mark as completed
            execution.complete();
            sagaExecutionService.save(execution);

            sample.stop(transferDuration);
            transferSuccesses.increment();

            log.info("========================================");
            log.info("International Transfer Saga COMPLETED: {}", sagaId);
            log.info("Duration: {}ms", sample.stop(transferDuration));
            log.info("========================================");

            return SagaResponse.success(sagaId, "International transfer completed successfully");

        } catch (Exception e) {
            log.error("========================================");
            log.error("International Transfer Saga FAILED: {}", sagaId, e);
            log.error("========================================");

            transferFailures.increment();

            // Execute compensation
            try {
                executeCompensation(execution, e);
                execution.compensated();
                sagaExecutionService.save(execution);

                compensations.increment();
                sample.stop(transferDuration);

                log.info("International Transfer Saga COMPENSATED: {}", sagaId);

                return SagaResponse.compensated(sagaId,
                    "International transfer failed and fully compensated: " + e.getMessage());

            } catch (Exception compensationError) {
                log.error("CRITICAL: Compensation FAILED for international transfer saga: {}", sagaId, compensationError);
                execution.fail(e.getMessage(), "COMPENSATION_FAILED", execution.getCurrentStep());
                sagaExecutionService.save(execution);

                // File SAR for manual investigation
                try {
                    fileSAROnFailureStep.execute(execution);
                } catch (Exception sarError) {
                    log.error("CRITICAL: Failed to file SAR for compensation failure: {}", sagaId, sarError);
                }

                sample.stop(transferDuration);

                return SagaResponse.failed(sagaId,
                    "International transfer failed and compensation failed - manual intervention required");
            }
        }
    }

    /**
     * Execute forward steps in order
     *
     * CRITICAL: Each step must be idempotent and support retry
     */
    private void executeForwardSteps(SagaExecution execution) throws SagaExecutionException {
        InternationalTransferRequest request = execution.getContextValue("request", InternationalTransferRequest.class);

        // Step 1: Validate transfer request
        log.info("[Saga {}] Step 1/13: Validating transfer request", execution.getSagaId());
        execution.setCurrentStep(1);
        execution.setCurrentStepName("VALIDATE_TRANSFER");
        sagaExecutionService.save(execution);
        Map<String, Object> validationResult = validateTransferStep.execute(execution);
        execution.setContextValue("validationResult", validationResult);

        // Step 2: Sanctions screening (CRITICAL - must not proceed if sanctions hit)
        log.info("[Saga {}] Step 2/13: Screening for sanctions (OFAC, EU, UN)", execution.getSagaId());
        execution.setCurrentStep(2);
        execution.setCurrentStepName("SANCTIONS_SCREENING");
        sagaExecutionService.save(execution);
        Map<String, Object> sanctionsResult = sanctionsScreeningStep.execute(execution);
        execution.setContextValue("sanctionsResult", sanctionsResult);

        // If sanctions hit, stop immediately
        if (Boolean.TRUE.equals(sanctionsResult.get("sanctionsHit"))) {
            throw new SagaExecutionException("SANCTIONS_HIT: Transfer blocked due to sanctions screening failure");
        }

        // Step 3: Compliance pre-check (cross-border regulations)
        log.info("[Saga {}] Step 3/13: Checking cross-border compliance", execution.getSagaId());
        execution.setCurrentStep(3);
        execution.setCurrentStepName("COMPLIANCE_PRE_CHECK");
        sagaExecutionService.save(execution);
        Map<String, Object> complianceResult = compliancePreCheckStep.execute(execution);
        execution.setContextValue("complianceResult", complianceResult);

        // Step 4: Lock exchange rate (prevent rate fluctuation)
        log.info("[Saga {}] Step 4/13: Locking exchange rate ({} -> {})",
            execution.getSagaId(), request.getSourceCurrency(), request.getDestinationCurrency());
        execution.setCurrentStep(4);
        execution.setCurrentStepName("LOCK_EXCHANGE_RATE");
        sagaExecutionService.save(execution);
        Map<String, Object> rateResult = lockExchangeRateStep.execute(execution);
        execution.setContextValue("lockedRate", rateResult.get("exchangeRate"));
        execution.setContextValue("rateLockId", rateResult.get("lockId"));

        // Step 5: Calculate fees (FX spread + wire fees + intermediary fees)
        log.info("[Saga {}] Step 5/13: Calculating international transfer fees", execution.getSagaId());
        execution.setCurrentStep(5);
        execution.setCurrentStepName("CALCULATE_FEES");
        sagaExecutionService.save(execution);
        Map<String, Object> feeResult = calculateFeesStep.execute(execution);
        execution.setContextValue("fees", feeResult);

        // Step 6: Reserve funds (place hold on source wallet)
        log.info("[Saga {}] Step 6/13: Reserving funds in source wallet", execution.getSagaId());
        execution.setCurrentStep(6);
        execution.setCurrentStepName("RESERVE_FUNDS");
        sagaExecutionService.save(execution);
        Map<String, Object> reservationResult = reserveFundsStep.execute(execution);
        execution.setContextValue("reservationId", reservationResult.get("reservationId"));

        // Step 7: Debit source wallet
        log.info("[Saga {}] Step 7/13: Debiting source wallet", execution.getSagaId());
        execution.setCurrentStep(7);
        execution.setCurrentStepName("DEBIT_SOURCE_WALLET");
        sagaExecutionService.save(execution);
        Map<String, Object> debitResult = debitSourceWalletStep.execute(execution);
        execution.setContextValue("debitTransactionId", debitResult.get("transactionId"));

        // Step 8: Execute FX conversion
        log.info("[Saga {}] Step 8/13: Executing foreign exchange conversion", execution.getSagaId());
        execution.setCurrentStep(8);
        execution.setCurrentStepName("EXECUTE_FX_CONVERSION");
        sagaExecutionService.save(execution);
        Map<String, Object> fxResult = executeFXConversionStep.execute(execution);
        execution.setContextValue("fxConversionId", fxResult.get("conversionId"));

        // Step 9: Route via correspondent banks (SWIFT network)
        log.info("[Saga {}] Step 9/13: Routing via correspondent banks (SWIFT)", execution.getSagaId());
        execution.setCurrentStep(9);
        execution.setCurrentStepName("ROUTE_VIA_CORRESPONDENT_BANKS");
        sagaExecutionService.save(execution);
        Map<String, Object> routingResult = routeViaCorrespondentBanksStep.execute(execution);
        execution.setContextValue("swiftMessageId", routingResult.get("swiftMessageId"));
        execution.setContextValue("correspondentBanks", routingResult.get("correspondentBanks"));

        // Step 10: Credit destination account
        log.info("[Saga {}] Step 10/13: Crediting destination account", execution.getSagaId());
        execution.setCurrentStep(10);
        execution.setCurrentStepName("CREDIT_DESTINATION_ACCOUNT");
        sagaExecutionService.save(execution);
        Map<String, Object> creditResult = creditDestinationAccountStep.execute(execution);
        execution.setContextValue("creditTransactionId", creditResult.get("transactionId"));

        // Step 11: Record regulatory reports (CTR, FBAR, etc.)
        log.info("[Saga {}] Step 11/13: Recording regulatory reports", execution.getSagaId());
        execution.setCurrentStep(11);
        execution.setCurrentStepName("RECORD_REGULATORY_REPORTS");
        sagaExecutionService.save(execution);
        Map<String, Object> reportResult = recordRegulatoryReportsStep.execute(execution);
        execution.setContextValue("regulatoryReports", reportResult.get("reports"));

        // Step 12: Send notifications
        log.info("[Saga {}] Step 12/13: Sending notifications to sender and recipient", execution.getSagaId());
        execution.setCurrentStep(12);
        execution.setCurrentStepName("SEND_NOTIFICATIONS");
        sagaExecutionService.save(execution);
        Map<String, Object> notificationResult = sendNotificationsStep.execute(execution);
        execution.setContextValue("notificationIds", notificationResult.get("notificationIds"));

        // Step 13: Update AML analytics
        log.info("[Saga {}] Step 13/13: Updating AML analytics and fraud detection", execution.getSagaId());
        execution.setCurrentStep(13);
        execution.setCurrentStepName("UPDATE_AML_ANALYTICS");
        sagaExecutionService.save(execution);
        Map<String, Object> analyticsResult = updateAnalyticsStep.execute(execution);
        execution.setContextValue("analyticsResult", analyticsResult);

        log.info("[Saga {}] All forward steps completed successfully", execution.getSagaId());
    }

    /**
     * Execute compensation steps in reverse order
     *
     * CRITICAL: Must undo all state changes from forward steps
     */
    private void executeCompensation(SagaExecution execution, Exception originalError) throws Exception {
        log.warn("========================================");
        log.warn("Starting compensation for International Transfer Saga: {}", execution.getSagaId());
        log.warn("Original error: {}", originalError.getMessage());
        log.warn("Compensation will execute for {} completed steps", execution.getCurrentStep());
        log.warn("========================================");

        int currentStep = execution.getCurrentStep();

        try {
            // Reverse in opposite order
            if (currentStep >= 13) {
                log.info("[Compensation] Reversing AML analytics");
                reverseAnalyticsStep.compensate(execution);
            }

            if (currentStep >= 12) {
                log.info("[Compensation] Cancelling notifications");
                cancelNotificationsStep.compensate(execution);
            }

            // Note: We don't reverse regulatory reports (they're immutable)

            if (currentStep >= 10) {
                log.info("[Compensation] Reversing destination account credit");
                reverseCreditStep.compensate(execution);
            }

            if (currentStep >= 9) {
                log.info("[Compensation] Recalling correspondent bank transfer");
                recallCorrespondentTransferStep.compensate(execution);
            }

            if (currentStep >= 8) {
                log.info("[Compensation] Reversing FX conversion");
                reverseFXConversionStep.compensate(execution);
            }

            if (currentStep >= 7) {
                log.info("[Compensation] Reversing source wallet debit");
                reverseDebitStep.compensate(execution);
            }

            if (currentStep >= 6) {
                log.info("[Compensation] Releasing reserved funds");
                releaseReservedFundsStep.compensate(execution);
            }

            if (currentStep >= 4) {
                log.info("[Compensation] Unlocking exchange rate");
                unlockExchangeRateStep.compensate(execution);
            }

            log.info("========================================");
            log.info("Compensation completed successfully for saga: {}", execution.getSagaId());
            log.info("========================================");

        } catch (Exception compensationError) {
            log.error("========================================");
            log.error("CRITICAL: Compensation failed for saga: {}", execution.getSagaId(), compensationError);
            log.error("Manual intervention required - escalating to operations team");
            log.error("========================================");

            throw compensationError;
        }
    }
}
