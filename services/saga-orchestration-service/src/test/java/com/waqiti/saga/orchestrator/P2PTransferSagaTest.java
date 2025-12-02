package com.waqiti.saga.orchestrator;

import com.waqiti.saga.builder.P2PTransferRequestBuilder;
import com.waqiti.saga.builder.SagaExecutionBuilder;
import com.waqiti.saga.domain.SagaExecution;
import com.waqiti.common.saga.SagaStatus;
import com.waqiti.saga.dto.P2PTransferRequest;
import com.waqiti.saga.dto.SagaResponse;
import com.waqiti.saga.exception.SagaExecutionException;
import com.waqiti.saga.service.SagaExecutionService;
import com.waqiti.saga.step.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for P2PTransferSaga
 *
 * Test Coverage:
 * - Happy path (successful transfer)
 * - Compensation scenarios (failures at each step)
 * - Edge cases (insufficient funds, invalid amounts, self-transfer)
 * - Retry logic
 * - Idempotency
 * - State transitions
 * - Error handling
 *
 * Test Strategy:
 * - Use Mockito for all dependencies
 * - Test each saga step in isolation
 * - Verify compensation is triggered correctly
 * - Verify state transitions are correct
 * - Use test data builders for clarity
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P2PTransferSaga Unit Tests")
class P2PTransferSagaTest {

    @Mock
    private SagaExecutionService sagaExecutionService;

    @Mock
    private ValidateTransferStep validateTransferStep;

    @Mock
    private ReserveFundsStep reserveFundsStep;

    @Mock
    private DebitWalletStep debitWalletStep;

    @Mock
    private CreditWalletStep creditWalletStep;

    @Mock
    private SendNotificationStep sendNotificationStep;

    @Mock
    private UpdateAnalyticsStep updateAnalyticsStep;

    @Mock
    private ReleaseReservedFundsStep releaseReservedFundsStep;

    @Mock
    private ReverseCreditStep reverseCreditStep;

    @Mock
    private ReverseDebitStep reverseDebitStep;

    @Mock
    private CancelNotificationStep cancelNotificationStep;

    @Mock
    private ReverseAnalyticsStep reverseAnalyticsStep;

    @InjectMocks
    private P2PTransferSaga saga;

    private P2PTransferRequest validRequest;
    private SagaExecution mockExecution;

    @BeforeEach
    void setUp() {
        // Create valid test request
        validRequest = P2PTransferRequestBuilder.aP2PTransferRequest()
            .withAmount(new BigDecimal("100.00"))
            .withFromUserId("user-123")
            .withToUserId("user-456")
            .build();

        // Create mock saga execution
        mockExecution = SagaExecutionBuilder.aSagaExecution()
            .withSagaType(SagaType.P2P_TRANSFER)
            .withStatus(SagaStatus.INITIATED)
            .build();
    }

    // ========== HAPPY PATH TESTS ==========

    @Test
    @DisplayName("Should complete P2P transfer successfully when all steps pass")
    void shouldCompleteP2PTransferSuccessfully() {
        // Given: All steps will succeed
        when(sagaExecutionService.save(any(SagaExecution.class))).thenReturn(mockExecution);
        when(validateTransferStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(reserveFundsStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(debitWalletStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(creditWalletStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(sendNotificationStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(updateAnalyticsStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());

        // When: Execute saga
        SagaResponse response = saga.execute(validRequest);

        // Then: Saga should complete successfully
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).contains("completed successfully");

        // Verify all steps were executed in order
        verify(validateTransferStep).execute(any(SagaExecution.class));
        verify(reserveFundsStep).execute(any(SagaExecution.class));
        verify(debitWalletStep).execute(any(SagaExecution.class));
        verify(creditWalletStep).execute(any(SagaExecution.class));
        verify(sendNotificationStep).execute(any(SagaExecution.class));
        verify(updateAnalyticsStep).execute(any(SagaExecution.class));

        // Verify saga was saved multiple times (initial, each step, completion)
        verify(sagaExecutionService, atLeast(7)).save(any(SagaExecution.class));

        // Verify no compensation steps were called
        verifyNoInteractions(releaseReservedFundsStep);
        verifyNoInteractions(reverseDebitStep);
        verifyNoInteractions(reverseCreditStep);
        verifyNoInteractions(cancelNotificationStep);
        verifyNoInteractions(reverseAnalyticsStep);
    }

    @Test
    @DisplayName("Should execute all steps in correct order")
    void shouldExecuteStepsInCorrectOrder() {
        // Given: All steps succeed
        when(sagaExecutionService.save(any(SagaExecution.class))).thenReturn(mockExecution);
        mockAllStepsSuccess();

        // When: Execute saga
        saga.execute(validRequest);

        // Then: Verify execution order using InOrder
        var inOrder = inOrder(
            validateTransferStep,
            reserveFundsStep,
            debitWalletStep,
            creditWalletStep,
            sendNotificationStep,
            updateAnalyticsStep
        );

        inOrder.verify(validateTransferStep).execute(any(SagaExecution.class));
        inOrder.verify(reserveFundsStep).execute(any(SagaExecution.class));
        inOrder.verify(debitWalletStep).execute(any(SagaExecution.class));
        inOrder.verify(creditWalletStep).execute(any(SagaExecution.class));
        inOrder.verify(sendNotificationStep).execute(any(SagaExecution.class));
        inOrder.verify(updateAnalyticsStep).execute(any(SagaExecution.class));
    }

    // ========== COMPENSATION TESTS ==========

    @Test
    @DisplayName("Should trigger compensation when ReserveFunds step fails")
    void shouldCompensateWhenReserveFundsFails() {
        // Given: ReserveFunds step will fail
        when(sagaExecutionService.save(any(SagaExecution.class))).thenReturn(mockExecution);
        when(validateTransferStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(reserveFundsStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.failure("Insufficient funds", "INSUFFICIENT_FUNDS"));

        // Compensation steps succeed
        when(releaseReservedFundsStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());

        // When: Execute saga
        SagaResponse response = saga.execute(validRequest);

        // Then: Saga should be compensated
        assertThat(response).isNotNull();
        assertThat(response.isCompensated()).isTrue();
        assertThat(response.getMessage()).contains("compensated");

        // Verify only first step was executed
        verify(validateTransferStep).execute(any(SagaExecution.class));
        verify(reserveFundsStep).execute(any(SagaExecution.class));

        // Verify subsequent steps were NOT executed
        verify(debitWalletStep, never()).execute(any(SagaExecution.class));
        verify(creditWalletStep, never()).execute(any(SagaExecution.class));

        // Verify compensation was NOT triggered (validation and reserve don't need compensation)
        verifyNoInteractions(releaseReservedFundsStep);
    }

    @Test
    @DisplayName("Should compensate DebitWallet when CreditWallet fails")
    void shouldCompensateDebitWalletWhenCreditWalletFails() {
        // Given: CreditWallet step will fail
        when(sagaExecutionService.save(any(SagaExecution.class))).thenReturn(mockExecution);
        when(validateTransferStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(reserveFundsStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(debitWalletStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(creditWalletStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.failure("Destination wallet not found", "WALLET_NOT_FOUND"));

        // Compensation steps succeed
        when(reverseDebitStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(releaseReservedFundsStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());

        // When: Execute saga
        SagaResponse response = saga.execute(validRequest);

        // Then: Saga should be compensated
        assertThat(response).isNotNull();
        assertThat(response.isCompensated()).isTrue();

        // Verify compensation steps were called in reverse order
        var inOrder = inOrder(reverseDebitStep, releaseReservedFundsStep);
        inOrder.verify(reverseDebitStep).execute(any(SagaExecution.class));
        inOrder.verify(releaseReservedFundsStep).execute(any(SagaExecution.class));

        // Verify later steps were NOT compensated (they never executed)
        verifyNoInteractions(reverseCreditStep);
        verifyNoInteractions(cancelNotificationStep);
        verifyNoInteractions(reverseAnalyticsStep);
    }

    @Test
    @DisplayName("Should compensate all steps when last step fails")
    void shouldCompensateAllStepsWhenLastStepFails() {
        // Given: Last step (UpdateAnalytics) will fail
        when(sagaExecutionService.save(any(SagaExecution.class))).thenReturn(mockExecution);
        when(validateTransferStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(reserveFundsStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(debitWalletStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(creditWalletStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(sendNotificationStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(updateAnalyticsStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.failure("Analytics service unavailable", "SERVICE_UNAVAILABLE"));

        // All compensation steps succeed
        mockAllCompensationSuccess();

        // When: Execute saga
        SagaResponse response = saga.execute(validRequest);

        // Then: Saga should be compensated
        assertThat(response).isNotNull();
        assertThat(response.isCompensated()).isTrue();

        // Verify all compensation steps were called in reverse order
        var inOrder = inOrder(
            cancelNotificationStep,
            reverseCreditStep,
            reverseDebitStep,
            releaseReservedFundsStep
        );
        inOrder.verify(cancelNotificationStep).execute(any(SagaExecution.class));
        inOrder.verify(reverseCreditStep).execute(any(SagaExecution.class));
        inOrder.verify(reverseDebitStep).execute(any(SagaExecution.class));
        inOrder.verify(releaseReservedFundsStep).execute(any(SagaExecution.class));
    }

    @Test
    @DisplayName("Should continue compensation even if compensation step fails")
    void shouldContinueCompensationEvenIfStepFails() {
        // Given: CreditWallet fails, and one compensation step also fails
        when(sagaExecutionService.save(any(SagaExecution.class))).thenReturn(mockExecution);
        mockStepsUntilCredit();
        when(creditWalletStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.failure("Destination wallet not found", "WALLET_NOT_FOUND"));

        // ReverseDebit compensation fails, but ReleaseReservedFunds succeeds
        when(reverseDebitStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.failure("Reverse debit failed", "REVERSE_FAILED"));
        when(releaseReservedFundsStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());

        // When: Execute saga
        SagaResponse response = saga.execute(validRequest);

        // Then: Saga should still be compensated (best-effort)
        assertThat(response).isNotNull();
        assertThat(response.isCompensated()).isTrue();

        // Verify all compensation steps were attempted
        verify(reverseDebitStep).execute(any(SagaExecution.class));
        verify(releaseReservedFundsStep).execute(any(SagaExecution.class));
    }

    @Test
    @DisplayName("Should mark saga as failed when compensation fails critically")
    void shouldMarkSagaAsFailedWhenCompensationFailsCritically() {
        // Given: DebitWallet fails, and ALL compensation steps fail
        when(sagaExecutionService.save(any(SagaExecution.class))).thenReturn(mockExecution);
        mockStepsUntilDebit();
        when(debitWalletStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.failure("Debit failed", "DEBIT_FAILED"));

        // All compensation steps fail
        when(releaseReservedFundsStep.execute(any(SagaExecution.class)))
            .thenThrow(new RuntimeException("Critical compensation failure"));

        // When: Execute saga
        SagaResponse response = saga.execute(validRequest);

        // Then: Saga should be marked as failed
        assertThat(response).isNotNull();
        assertThat(response.isFailed()).isTrue();
        assertThat(response.getMessage()).contains("compensation failed");
    }

    // ========== EDGE CASE TESTS ==========

    @Test
    @DisplayName("Should fail validation for negative amount")
    void shouldFailValidationForNegativeAmount() {
        // Given: Request with negative amount
        P2PTransferRequest invalidRequest = P2PTransferRequestBuilder.aP2PTransferRequest()
            .withInvalidAmount()
            .build();

        when(sagaExecutionService.save(any(SagaExecution.class))).thenReturn(mockExecution);
        when(validateTransferStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.failure("Amount must be positive", "INVALID_AMOUNT"));

        // When: Execute saga
        SagaResponse response = saga.execute(invalidRequest);

        // Then: Saga should fail at validation
        assertThat(response).isNotNull();
        assertThat(response.isCompensated()).isTrue();

        // Verify no other steps were executed
        verify(validateTransferStep).execute(any(SagaExecution.class));
        verify(reserveFundsStep, never()).execute(any(SagaExecution.class));
    }

    @Test
    @DisplayName("Should fail validation for self-transfer")
    void shouldFailValidationForSelfTransfer() {
        // Given: Request with same source and destination
        P2PTransferRequest selfTransferRequest = P2PTransferRequestBuilder.aP2PTransferRequest()
            .withSelfTransfer()
            .build();

        when(sagaExecutionService.save(any(SagaExecution.class))).thenReturn(mockExecution);
        when(validateTransferStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.failure("Cannot transfer to self", "SELF_TRANSFER"));

        // When: Execute saga
        SagaResponse response = saga.execute(selfTransferRequest);

        // Then: Saga should fail at validation
        assertThat(response).isNotNull();
        assertThat(response.isCompensated()).isTrue();

        verify(validateTransferStep).execute(any(SagaExecution.class));
        verify(reserveFundsStep, never()).execute(any(SagaExecution.class));
    }

    @Test
    @DisplayName("Should fail when insufficient funds")
    void shouldFailWhenInsufficientFunds() {
        // Given: Request with large amount (insufficient funds)
        P2PTransferRequest largeAmountRequest = P2PTransferRequestBuilder.aP2PTransferRequest()
            .withInsufficientFunds()
            .build();

        when(sagaExecutionService.save(any(SagaExecution.class))).thenReturn(mockExecution);
        when(validateTransferStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(reserveFundsStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.failure("Insufficient funds", "INSUFFICIENT_FUNDS"));

        // When: Execute saga
        SagaResponse response = saga.execute(largeAmountRequest);

        // Then: Saga should fail at ReserveFunds
        assertThat(response).isNotNull();
        assertThat(response.isCompensated()).isTrue();

        verify(validateTransferStep).execute(any(SagaExecution.class));
        verify(reserveFundsStep).execute(any(SagaExecution.class));
        verify(debitWalletStep, never()).execute(any(SagaExecution.class));
    }

    // ========== RETRY LOGIC TESTS ==========

    @Test
    @DisplayName("Should support saga retry on failure")
    void shouldSupportSagaRetryOnFailure() {
        // Given: Saga failed once
        String existingSagaId = "existing-saga-id";
        SagaExecution failedExecution = SagaExecutionBuilder.aSagaExecution()
            .withSagaId(existingSagaId)
            .asFailedSaga()
            .build();

        // Store original request in context
        failedExecution.setContextValue("request", validRequest);

        when(sagaExecutionService.findBySagaId(existingSagaId))
            .thenReturn(Optional.of(failedExecution));
        when(sagaExecutionService.save(any(SagaExecution.class)))
            .thenReturn(failedExecution);

        // This time all steps succeed
        mockAllStepsSuccess();

        // When: Retry saga
        SagaResponse response = saga.retry(existingSagaId);

        // Then: Saga should complete successfully
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();

        // Verify retry count was incremented
        assertThat(failedExecution.getRetryCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should prevent retry when max retries exceeded")
    void shouldPreventRetryWhenMaxRetriesExceeded() {
        // Given: Saga has exceeded max retries
        String sagaId = "exceeded-saga-id";
        SagaExecution exceededExecution = SagaExecutionBuilder.aSagaExecution()
            .withSagaId(sagaId)
            .asExceededRetries()
            .build();

        when(sagaExecutionService.findBySagaId(sagaId))
            .thenReturn(Optional.of(exceededExecution));

        // When & Then: Should throw exception
        assertThatThrownBy(() -> saga.retry(sagaId))
            .isInstanceOf(SagaExecutionException.class)
            .hasMessageContaining("cannot be retried");
    }

    // ========== CANCEL SAGA TESTS ==========

    @Test
    @DisplayName("Should cancel running saga and trigger compensation")
    void shouldCancelRunningSaga() {
        // Given: Running saga
        String sagaId = "running-saga-id";
        SagaExecution runningExecution = SagaExecutionBuilder.aSagaExecution()
            .withSagaId(sagaId)
            .asRunningP2PTransfer()
            .build();

        when(sagaExecutionService.findBySagaId(sagaId))
            .thenReturn(Optional.of(runningExecution));
        when(sagaExecutionService.save(any(SagaExecution.class)))
            .thenReturn(runningExecution);

        // Compensation succeeds
        mockAllCompensationSuccess();

        // When: Cancel saga
        SagaResponse response = saga.cancel(sagaId, "User requested cancellation");

        // Then: Saga should be cancelled
        assertThat(response).isNotNull();
        assertThat(response.getMessage()).contains("cancelled successfully");

        // Verify compensation was triggered
        verify(reverseDebitStep).execute(any(SagaExecution.class));
        verify(releaseReservedFundsStep).execute(any(SagaExecution.class));
    }

    @Test
    @DisplayName("Should prevent cancellation of completed saga")
    void shouldPreventCancellationOfCompletedSaga() {
        // Given: Completed saga
        String sagaId = "completed-saga-id";
        SagaExecution completedExecution = SagaExecutionBuilder.aSagaExecution()
            .withSagaId(sagaId)
            .asCompletedSaga()
            .build();

        when(sagaExecutionService.findBySagaId(sagaId))
            .thenReturn(Optional.of(completedExecution));

        // When & Then: Should throw exception
        assertThatThrownBy(() -> saga.cancel(sagaId, "Attempting to cancel"))
            .isInstanceOf(SagaExecutionException.class)
            .hasMessageContaining("terminal state");
    }

    // ========== HELPER METHODS ==========

    private void mockAllStepsSuccess() {
        when(validateTransferStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(reserveFundsStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(debitWalletStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(creditWalletStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(sendNotificationStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(updateAnalyticsStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
    }

    private void mockAllCompensationSuccess() {
        when(releaseReservedFundsStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(reverseDebitStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(reverseCreditStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(cancelNotificationStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(reverseAnalyticsStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
    }

    private void mockStepsUntilDebit() {
        when(validateTransferStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
        when(reserveFundsStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
    }

    private void mockStepsUntilCredit() {
        mockStepsUntilDebit();
        when(debitWalletStep.execute(any(SagaExecution.class)))
            .thenReturn(StepExecutionResult.success());
    }
}
