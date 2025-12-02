package com.waqiti.saga.orchestrator;

import com.waqiti.saga.domain.SagaExecution;
import com.waqiti.common.saga.SagaStatus;
import com.waqiti.saga.domain.SagaType;
import com.waqiti.saga.dto.P2PTransferRequest;
import com.waqiti.saga.dto.SagaResponse;
import com.waqiti.saga.exception.SagaExecutionException;
import com.waqiti.saga.service.SagaExecutionService;
import com.waqiti.saga.step.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * P2P Transfer Saga Orchestrator
 * 
 * Orchestrates the distributed transaction for peer-to-peer money transfers.
 * This saga ensures that money is transferred atomically between two wallets
 * with proper validation, notifications, and analytics.
 * 
 * Saga Steps:
 * 1. Validate Transfer Request (user limits, account status, etc.)
 * 2. Reserve Funds (place hold on source wallet)
 * 3. Debit Source Wallet (remove funds from source)
 * 4. Credit Destination Wallet (add funds to destination)
 * 5. Send Notifications (notify both parties)
 * 6. Update Analytics (record transaction metrics)
 * 
 * Compensation Steps (if any step fails):
 * 1. Reverse Analytics Update
 * 2. Cancel Notifications
 * 3. Reverse Credit
 * 4. Reverse Debit
 * 5. Release Reserved Funds
 * 6. Cancel Transfer
 */
@Component
public class P2PTransferSaga implements SagaOrchestrator<P2PTransferRequest> {

    private static final Logger logger = LoggerFactory.getLogger(P2PTransferSaga.class);

    private final SagaExecutionService sagaExecutionService;
    private final ValidateTransferStep validateTransferStep;
    private final ReserveFundsStep reserveFundsStep;
    private final DebitWalletStep debitWalletStep;
    private final CreditWalletStep creditWalletStep;
    private final SendNotificationStep sendNotificationStep;
    private final UpdateAnalyticsStep updateAnalyticsStep;

    // Compensation steps
    private final ReleaseReservedFundsStep releaseReservedFundsStep;
    private final ReverseCreditStep reverseCreditStep;
    private final ReverseDebitStep reverseDebitStep;
    private final CancelNotificationStep cancelNotificationStep;
    private final ReverseAnalyticsStep reverseAnalyticsStep;

    // Metrics
    private final Counter transferAttempts;
    private final Counter transferSuccesses;
    private final Counter transferFailures;
    private final Counter compensations;
    private final Timer transferDuration;

    public P2PTransferSaga(SagaExecutionService sagaExecutionService,
                          ValidateTransferStep validateTransferStep,
                          ReserveFundsStep reserveFundsStep,
                          DebitWalletStep debitWalletStep,
                          CreditWalletStep creditWalletStep,
                          SendNotificationStep sendNotificationStep,
                          UpdateAnalyticsStep updateAnalyticsStep,
                          ReleaseReservedFundsStep releaseReservedFundsStep,
                          ReverseCreditStep reverseCreditStep,
                          ReverseDebitStep reverseDebitStep,
                          CancelNotificationStep cancelNotificationStep,
                          ReverseAnalyticsStep reverseAnalyticsStep,
                          MeterRegistry meterRegistry) {
        this.sagaExecutionService = sagaExecutionService;
        this.validateTransferStep = validateTransferStep;
        this.reserveFundsStep = reserveFundsStep;
        this.debitWalletStep = debitWalletStep;
        this.creditWalletStep = creditWalletStep;
        this.sendNotificationStep = sendNotificationStep;
        this.updateAnalyticsStep = updateAnalyticsStep;
        this.releaseReservedFundsStep = releaseReservedFundsStep;
        this.reverseCreditStep = reverseCreditStep;
        this.reverseDebitStep = reverseDebitStep;
        this.cancelNotificationStep = cancelNotificationStep;
        this.reverseAnalyticsStep = reverseAnalyticsStep;

        // Initialize metrics
        this.transferAttempts = Counter.builder("p2p_transfer.attempts")
            .description("Number of P2P transfer attempts")
            .tag("saga_type", "P2P_TRANSFER")
            .register(meterRegistry);

        this.transferSuccesses = Counter.builder("p2p_transfer.successes")
            .description("Number of successful P2P transfers")
            .tag("saga_type", "P2P_TRANSFER")
            .register(meterRegistry);

        this.transferFailures = Counter.builder("p2p_transfer.failures")
            .description("Number of failed P2P transfers")
            .tag("saga_type", "P2P_TRANSFER")
            .register(meterRegistry);

        this.compensations = Counter.builder("p2p_transfer.compensations")
            .description("Number of compensated P2P transfers")
            .tag("saga_type", "P2P_TRANSFER")
            .register(meterRegistry);

        this.transferDuration = Timer.builder("p2p_transfer.duration")
            .description("P2P transfer saga duration")
            .tag("saga_type", "P2P_TRANSFER")
            .register(meterRegistry);
    }

    @Override
    public SagaType getSagaType() {
        return SagaType.P2P_TRANSFER;
    }

    @Override
    public SagaResponse execute(P2PTransferRequest request) {
        String sagaId = UUID.randomUUID().toString();
        logger.info("Starting P2P Transfer Saga: {} for transfer from {} to {} amount {}",
                   sagaId, request.getFromUserId(), request.getToUserId(), request.getAmount());

        // Increment attempts counter
        transferAttempts.increment();
        Timer.Sample sample = Timer.start();

        // Create saga execution
        SagaExecution execution = new SagaExecution(sagaId, SagaType.P2P_TRANSFER, request.getTransactionId());
        execution.setInitiatedBy(request.getFromUserId());
        execution.setTotalSteps(6);
        execution.setTimeoutAt(LocalDateTime.now().plusMinutes(30)); // 30 minutes timeout

        // Store request in context
        execution.setContextValue("request", request);
        execution.setContextValue("transactionId", request.getTransactionId());
        execution.setContextValue("fromUserId", request.getFromUserId());
        execution.setContextValue("toUserId", request.getToUserId());
        execution.setContextValue("amount", request.getAmount());
        execution.setContextValue("currency", request.getCurrency());

        try {
            // Save initial execution state
            execution = sagaExecutionService.save(execution);
            execution.start();

            // Execute saga steps
            executeForwardSteps(execution);

            // Mark as completed
            execution.complete();
            sagaExecutionService.save(execution);

            // Record success metrics
            sample.stop(transferDuration);
            transferSuccesses.increment();

            logger.info("P2P Transfer Saga completed successfully: {} in {}ms",
                       sagaId, sample.stop(transferDuration));
            return SagaResponse.success(sagaId, "P2P transfer completed successfully");

        } catch (Exception e) {
            logger.error("P2P Transfer Saga failed: {}", sagaId, e);

            // Increment failure counter
            transferFailures.increment();

            // Execute compensation
            try {
                executeCompensation(execution, e);
                execution.compensated();
                sagaExecutionService.save(execution);

                // Record compensation metrics
                compensations.increment();
                sample.stop(transferDuration);

                logger.info("P2P Transfer Saga compensated: {}", sagaId);
                return SagaResponse.compensated(sagaId, "P2P transfer failed and compensated: " + e.getMessage());

            } catch (Exception compensationError) {
                logger.error("Compensation failed for saga: {}", sagaId, compensationError);
                execution.fail(e.getMessage(), "COMPENSATION_FAILED", execution.getCurrentStep());
                sagaExecutionService.save(execution);

                sample.stop(transferDuration);

                return SagaResponse.failed(sagaId, "P2P transfer failed and compensation failed: " + e.getMessage());
            }
        }
    }

    private void executeForwardSteps(SagaExecution execution) throws SagaExecutionException {
        List<SagaStep> steps = getForwardSteps();
        
        for (SagaStep step : steps) {
            execution.moveToNextStep(step.getStepName());
            sagaExecutionService.save(execution);
            
            logger.info("Executing step: {} for saga: {}", step.getStepName(), execution.getSagaId());
            
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
    }

    private void executeCompensation(SagaExecution execution, Exception originalError) throws SagaExecutionException {
        logger.info("Starting compensation for saga: {}", execution.getSagaId());
        execution.compensate();
        
        List<SagaStep> compensationSteps = getCompensationSteps();
        
        // Execute compensation steps in reverse order
        for (int i = compensationSteps.size() - 1; i >= 0; i--) {
            SagaStep step = compensationSteps.get(i);
            
            // Only compensate if the corresponding forward step was executed
            if (shouldCompensateStep(execution, step)) {
                logger.info("Executing compensation step: {} for saga: {}", 
                           step.getStepName(), execution.getSagaId());
                
                try {
                    StepExecutionResult result = step.execute(execution);
                    
                    if (!result.isSuccess()) {
                        logger.warn("Compensation step failed: {} for saga: {} - {}", 
                                  step.getStepName(), execution.getSagaId(), result.getErrorMessage());
                        // Continue with other compensation steps
                    }
                    
                } catch (Exception e) {
                    logger.error("Compensation step error: {} for saga: {}", 
                               step.getStepName(), execution.getSagaId(), e);
                    // Continue with other compensation steps
                }
            }
        }
    }

    private boolean shouldCompensateStep(SagaExecution execution, SagaStep compensationStep) {
        // Determine if the corresponding forward step was executed based on current step index
        String stepName = compensationStep.getStepName();
        
        return switch (stepName) {
            case "ReleaseReservedFunds" -> execution.getCurrentStepIndex() > 1; // If ReserveFunds was executed
            case "ReverseDebit" -> execution.getCurrentStepIndex() > 2; // If DebitWallet was executed
            case "ReverseCredit" -> execution.getCurrentStepIndex() > 3; // If CreditWallet was executed
            case "CancelNotification" -> execution.getCurrentStepIndex() > 4; // If SendNotification was executed
            case "ReverseAnalytics" -> execution.getCurrentStepIndex() > 5; // If UpdateAnalytics was executed
            default -> false;
        };
    }

    private List<SagaStep> getForwardSteps() {
        return Arrays.asList(
            validateTransferStep,
            reserveFundsStep,
            debitWalletStep,
            creditWalletStep,
            sendNotificationStep,
            updateAnalyticsStep
        );
    }

    private List<SagaStep> getCompensationSteps() {
        return Arrays.asList(
            reverseAnalyticsStep,
            cancelNotificationStep,
            reverseCreditStep,
            reverseDebitStep,
            releaseReservedFundsStep
        );
    }

    @Override
    public SagaResponse retry(String sagaId) {
        logger.info("Retrying P2P Transfer Saga: {}", sagaId);
        
        SagaExecution execution = sagaExecutionService.findBySagaId(sagaId)
            .orElseThrow(() -> new SagaExecutionException("Saga not found: " + sagaId));
        
        if (!execution.canRetry()) {
            throw new SagaExecutionException("Saga cannot be retried: " + sagaId);
        }
        
        execution.incrementRetryCount();
        execution.setStatus(SagaStatus.RUNNING);
        execution.setErrorMessage(null);
        execution.setErrorCode(null);
        
        // Extract original request from context
        P2PTransferRequest request = (P2PTransferRequest) execution.getContextValue("request");
        
        return execute(request);
    }

    @Override
    public SagaResponse cancel(String sagaId, String reason) {
        logger.info("Cancelling P2P Transfer Saga: {} - Reason: {}", sagaId, reason);
        
        SagaExecution execution = sagaExecutionService.findBySagaId(sagaId)
            .orElseThrow(() -> new SagaExecutionException("Saga not found: " + sagaId));
        
        if (execution.isTerminal()) {
            throw new SagaExecutionException("Saga is already in terminal state: " + sagaId);
        }
        
        try {
            // Execute compensation for any completed steps
            executeCompensation(execution, new Exception("Saga cancelled by user"));
            
            execution.setStatus(SagaStatus.COMPENSATED);
            execution.setErrorMessage("Cancelled by user: " + reason);
            sagaExecutionService.save(execution);
            
            return SagaResponse.cancelled(sagaId, "P2P transfer saga cancelled successfully");
            
        } catch (Exception e) {
            logger.error("Failed to cancel saga: {}", sagaId, e);
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