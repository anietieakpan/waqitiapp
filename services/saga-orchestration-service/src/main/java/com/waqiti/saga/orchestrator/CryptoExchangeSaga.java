package com.waqiti.saga.orchestrator;

import com.waqiti.saga.domain.SagaExecution;
import com.waqiti.common.saga.SagaStatus;
import com.waqiti.saga.domain.SagaType;
import com.waqiti.saga.dto.CryptoExchangeRequest;
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
 * Crypto Exchange Saga Orchestrator
 *
 * Orchestrates cryptocurrency buy/sell/swap operations with blockchain integration.
 *
 * Supported Operations:
 * - Buy crypto with fiat (USD, EUR, GBP → BTC, ETH, USDC)
 * - Sell crypto to fiat (BTC, ETH → USD, EUR)
 * - Crypto-to-crypto swap (BTC → ETH, ETH → USDC)
 * - Withdrawal to external wallet
 *
 * Saga Steps (12 steps):
 * 1. Validate Exchange Request (amounts, pairs, wallet addresses)
 * 2. KYC/AML Verification (enhanced due diligence for crypto)
 * 3. Check Trading Limits (daily/monthly volume caps)
 * 4. Lock Exchange Rate (quote valid for 30 seconds)
 * 5. Reserve Source Funds (fiat or crypto)
 * 6. Debit Source Account
 * 7. Execute Exchange (liquidity provider or order book)
 * 8. Confirm Blockchain Transaction (for crypto operations)
 * 9. Credit Destination Account
 * 10. Record Ledger Entries
 * 11. Send Transaction Confirmation
 * 12. Update Analytics (trading volume, P&L)
 *
 * Compensation Steps:
 * - Reverse Analytics
 * - Cancel Notification
 * - Reverse Ledger Entries
 * - Reverse Destination Credit
 * - Request Blockchain Reversal (if possible)
 * - Reverse Exchange
 * - Reverse Source Debit
 * - Release Reserved Funds
 * - Release Rate Lock
 *
 * Compliance:
 * - Travel Rule: Required for transfers > $3,000
 * - FinCEN: MSB registration, SAR filing
 * - FATF: Virtual asset service provider (VASP) requirements
 * - State licenses: BitLicense (NY), MTL (various states)
 *
 * @author Waqiti Platform Engineering Team
 * @version 1.0.0
 */
@Component
@Slf4j
public class CryptoExchangeSaga implements SagaOrchestrator<CryptoExchangeRequest> {

    private final SagaExecutionService sagaExecutionService;

    // Forward steps
    private final ValidateCryptoExchangeStep validateExchangeStep;
    private final CryptoKYCAMLVerificationStep kycAmlStep;
    private final CheckTradingLimitsStep checkLimitsStep;
    private final LockExchangeRateStep lockRateStep;
    private final ReserveFundsStep reserveFundsStep;
    private final DebitSourceAccountStep debitSourceStep;
    private final ExecuteCryptoExchangeStep executeExchangeStep;
    private final ConfirmBlockchainTransactionStep confirmBlockchainStep;
    private final CreditDestinationAccountStep creditDestinationStep;
    private final RecordLedgerEntriesStep recordLedgerStep;
    private final SendCryptoConfirmationStep sendConfirmationStep;
    private final UpdateCryptoAnalyticsStep updateAnalyticsStep;

    // Compensation steps
    private final ReverseCryptoAnalyticsStep reverseAnalyticsStep;
    private final CancelCryptoNotificationStep cancelNotificationStep;
    private final ReverseLedgerEntriesStep reverseLedgerStep;
    private final ReverseDestinationCreditStep reverseDestinationStep;
    private final RequestBlockchainReversalStep requestBlockchainReversalStep;
    private final ReverseExchangeStep reverseExchangeStep;
    private final ReverseSourceDebitStep reverseSourceDebitStep;
    private final ReleaseReservedFundsStep releaseReservedFundsStep;
    private final ReleaseRateLockStep releaseRateLockStep;

    // Metrics
    private final Counter exchangeAttempts;
    private final Counter exchangeSuccesses;
    private final Counter exchangeFailures;
    private final Counter compensations;
    private final Counter blockchainTimeouts;
    private final Counter rateLockExpired;
    private final Timer exchangeDuration;

    public CryptoExchangeSaga(
            SagaExecutionService sagaExecutionService,
            ValidateCryptoExchangeStep validateExchangeStep,
            CryptoKYCAMLVerificationStep kycAmlStep,
            CheckTradingLimitsStep checkLimitsStep,
            LockExchangeRateStep lockRateStep,
            ReserveFundsStep reserveFundsStep,
            DebitSourceAccountStep debitSourceStep,
            ExecuteCryptoExchangeStep executeExchangeStep,
            ConfirmBlockchainTransactionStep confirmBlockchainStep,
            CreditDestinationAccountStep creditDestinationStep,
            RecordLedgerEntriesStep recordLedgerStep,
            SendCryptoConfirmationStep sendConfirmationStep,
            UpdateCryptoAnalyticsStep updateAnalyticsStep,
            ReverseCryptoAnalyticsStep reverseAnalyticsStep,
            CancelCryptoNotificationStep cancelNotificationStep,
            ReverseLedgerEntriesStep reverseLedgerStep,
            ReverseDestinationCreditStep reverseDestinationStep,
            RequestBlockchainReversalStep requestBlockchainReversalStep,
            ReverseExchangeStep reverseExchangeStep,
            ReverseSourceDebitStep reverseSourceDebitStep,
            ReleaseReservedFundsStep releaseReservedFundsStep,
            ReleaseRateLockStep releaseRateLockStep,
            MeterRegistry meterRegistry) {

        this.sagaExecutionService = sagaExecutionService;
        this.validateExchangeStep = validateExchangeStep;
        this.kycAmlStep = kycAmlStep;
        this.checkLimitsStep = checkLimitsStep;
        this.lockRateStep = lockRateStep;
        this.reserveFundsStep = reserveFundsStep;
        this.debitSourceStep = debitSourceStep;
        this.executeExchangeStep = executeExchangeStep;
        this.confirmBlockchainStep = confirmBlockchainStep;
        this.creditDestinationStep = creditDestinationStep;
        this.recordLedgerStep = recordLedgerStep;
        this.sendConfirmationStep = sendConfirmationStep;
        this.updateAnalyticsStep = updateAnalyticsStep;
        this.reverseAnalyticsStep = reverseAnalyticsStep;
        this.cancelNotificationStep = cancelNotificationStep;
        this.reverseLedgerStep = reverseLedgerStep;
        this.reverseDestinationStep = reverseDestinationStep;
        this.requestBlockchainReversalStep = requestBlockchainReversalStep;
        this.reverseExchangeStep = reverseExchangeStep;
        this.reverseSourceDebitStep = reverseSourceDebitStep;
        this.releaseReservedFundsStep = releaseReservedFundsStep;
        this.releaseRateLockStep = releaseRateLockStep;

        // Initialize metrics
        this.exchangeAttempts = Counter.builder("crypto_exchange.attempts")
            .description("Number of crypto exchange attempts")
            .tag("saga_type", "CRYPTO_EXCHANGE")
            .register(meterRegistry);

        this.exchangeSuccesses = Counter.builder("crypto_exchange.successes")
            .description("Number of successful crypto exchanges")
            .tag("saga_type", "CRYPTO_EXCHANGE")
            .register(meterRegistry);

        this.exchangeFailures = Counter.builder("crypto_exchange.failures")
            .description("Number of failed crypto exchanges")
            .tag("saga_type", "CRYPTO_EXCHANGE")
            .register(meterRegistry);

        this.compensations = Counter.builder("crypto_exchange.compensations")
            .description("Number of compensated crypto exchanges")
            .tag("saga_type", "CRYPTO_EXCHANGE")
            .register(meterRegistry);

        this.blockchainTimeouts = Counter.builder("crypto_exchange.blockchain_timeouts")
            .description("Number of blockchain confirmation timeouts")
            .tag("saga_type", "CRYPTO_EXCHANGE")
            .register(meterRegistry);

        this.rateLockExpired = Counter.builder("crypto_exchange.rate_lock_expired")
            .description("Number of expired rate locks")
            .tag("saga_type", "CRYPTO_EXCHANGE")
            .register(meterRegistry);

        this.exchangeDuration = Timer.builder("crypto_exchange.duration")
            .description("Crypto exchange saga duration")
            .tag("saga_type", "CRYPTO_EXCHANGE")
            .register(meterRegistry);
    }

    @Override
    public SagaType getSagaType() {
        return SagaType.CRYPTO_EXCHANGE;
    }

    @Override
    public SagaResponse execute(CryptoExchangeRequest request) {
        String sagaId = UUID.randomUUID().toString();

        log.info("========================================");
        log.info("Starting Crypto Exchange Saga: {}", sagaId);
        log.info("User: {} | Type: {}", request.getUserId(), request.getExchangeType());
        log.info("From: {} {} -> To: {} (estimated)",
            request.getSourceAmount(), request.getSourceCurrency(),
            request.getDestinationCurrency());
        log.info("========================================");

        exchangeAttempts.increment();
        Timer.Sample sample = Timer.start();

        // Create saga execution
        SagaExecution execution = new SagaExecution(sagaId, SagaType.CRYPTO_EXCHANGE, request.getExchangeId());
        execution.setInitiatedBy(request.getUserId());
        execution.setTotalSteps(12);
        execution.setTimeoutAt(LocalDateTime.now().plusMinutes(15)); // 15 min (blockchain confirmations)

        // Store request in context
        execution.setContextValue("exchangeId", request.getExchangeId());
        execution.setContextValue("userId", request.getUserId());
        execution.setContextValue("exchangeType", request.getExchangeType());
        execution.setContextValue("sourceCurrency", request.getSourceCurrency());
        execution.setContextValue("sourceAmount", request.getSourceAmount());
        execution.setContextValue("destinationCurrency", request.getDestinationCurrency());
        execution.setContextValue("destinationWalletAddress", request.getDestinationWalletAddress());

        try {
            execution = sagaExecutionService.save(execution);
            execution.start();

            executeForwardSteps(execution);

            execution.complete();
            sagaExecutionService.save(execution);

            sample.stop(exchangeDuration);
            exchangeSuccesses.increment();

            log.info("========================================");
            log.info("Crypto Exchange Saga COMPLETED: {}", sagaId);
            log.info("TxHash: {}", execution.getContextValue("blockchainTxHash"));
            log.info("========================================");

            return SagaResponse.success(sagaId, "Crypto exchange completed successfully");

        } catch (Exception e) {
            log.error("========================================");
            log.error("Crypto Exchange Saga FAILED: {}", sagaId, e);
            log.error("========================================");

            exchangeFailures.increment();

            if (isBlockchainTimeout(e)) {
                blockchainTimeouts.increment();
            }
            if (isRateLockExpired(e)) {
                rateLockExpired.increment();
            }

            try {
                executeCompensation(execution, e);
                execution.compensated();
                sagaExecutionService.save(execution);

                compensations.increment();
                sample.stop(exchangeDuration);

                return SagaResponse.compensated(sagaId,
                    "Crypto exchange failed and compensated: " + e.getMessage());

            } catch (Exception compensationError) {
                log.error("CRITICAL: Compensation FAILED for crypto saga: {}", sagaId, compensationError);
                execution.fail(e.getMessage(), "COMPENSATION_FAILED", execution.getCurrentStep());
                sagaExecutionService.save(execution);

                sample.stop(exchangeDuration);

                return SagaResponse.failed(sagaId,
                    "Crypto exchange failed and compensation failed - manual intervention required");
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

            if (result.getStepData() != null) {
                result.getStepData().forEach(execution::setContextValue);
            }

            sagaExecutionService.save(execution);
        }
    }

    private void executeCompensation(SagaExecution execution, Exception originalError) {
        log.warn("Starting compensation for Crypto Exchange Saga: {}", execution.getSagaId());
        execution.compensate();
        List<SagaStep> compensationSteps = getCompensationSteps();

        for (int i = compensationSteps.size() - 1; i >= 0; i--) {
            SagaStep step = compensationSteps.get(i);

            if (shouldCompensateStep(execution, i)) {
                log.info("[Compensation] Executing: {}", step.getStepName());

                try {
                    step.execute(execution);
                } catch (Exception e) {
                    log.error("Compensation step error: {}", step.getStepName(), e);
                }
            }
        }
    }

    private boolean shouldCompensateStep(SagaExecution execution, int compensationStepIndex) {
        int correspondingForwardStep = 11 - compensationStepIndex;
        return execution.getCurrentStepIndex() > correspondingForwardStep;
    }

    private List<SagaStep> getForwardSteps() {
        return Arrays.asList(
            validateExchangeStep,
            kycAmlStep,
            checkLimitsStep,
            lockRateStep,
            reserveFundsStep,
            debitSourceStep,
            executeExchangeStep,
            confirmBlockchainStep,
            creditDestinationStep,
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
            reverseDestinationStep,
            requestBlockchainReversalStep,
            reverseExchangeStep,
            reverseSourceDebitStep,
            releaseReservedFundsStep,
            releaseRateLockStep
        );
    }

    private boolean isBlockchainTimeout(Exception e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("BLOCKCHAIN_TIMEOUT") || msg.contains("CONFIRMATION_TIMEOUT"));
    }

    private boolean isRateLockExpired(Exception e) {
        String msg = e.getMessage();
        return msg != null && msg.contains("RATE_LOCK_EXPIRED");
    }

    @Override
    public SagaResponse retry(String sagaId) {
        SagaExecution execution = sagaExecutionService.findBySagaId(sagaId)
            .orElseThrow(() -> new SagaExecutionException("Saga not found: " + sagaId));

        if (!execution.canRetry()) {
            throw new SagaExecutionException("Saga cannot be retried: " + sagaId);
        }

        execution.incrementRetryCount();
        execution.setStatus(SagaStatus.RUNNING);

        CryptoExchangeRequest request = (CryptoExchangeRequest) execution.getContextValue("request");
        return execute(request);
    }

    @Override
    public SagaResponse cancel(String sagaId, String reason) {
        SagaExecution execution = sagaExecutionService.findBySagaId(sagaId)
            .orElseThrow(() -> new SagaExecutionException("Saga not found: " + sagaId));

        if (execution.isTerminal()) {
            throw new SagaExecutionException("Saga is already in terminal state: " + sagaId);
        }

        try {
            executeCompensation(execution, new Exception("Cancelled: " + reason));
            execution.setStatus(SagaStatus.COMPENSATED);
            sagaExecutionService.save(execution);

            return SagaResponse.cancelled(sagaId, "Crypto exchange cancelled");
        } catch (Exception e) {
            execution.fail("Cancellation failed: " + e.getMessage(), "CANCELLATION_FAILED", execution.getCurrentStep());
            sagaExecutionService.save(execution);
            return SagaResponse.failed(sagaId, "Failed to cancel: " + e.getMessage());
        }
    }

    @Override
    public SagaExecution getExecution(String sagaId) {
        return sagaExecutionService.findBySagaId(sagaId)
            .orElseThrow(() -> new SagaExecutionException("Saga not found: " + sagaId));
    }
}
