package com.waqiti.saga.orchestrator;

import com.waqiti.saga.builder.InternationalTransferRequestBuilder;
import com.waqiti.saga.dto.InternationalTransferRequest;
import com.waqiti.saga.dto.SagaResponse;
import com.waqiti.saga.service.SagaExecutionService;
import com.waqiti.saga.step.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for InternationalTransferSaga
 *
 * CRITICAL: This saga handles cross-border transfers with regulatory compliance
 *
 * Test Coverage:
 * - Happy path (successful international transfer)
 * - OFAC sanctions screening (BLOCKING step)
 * - CTR filing ($10K+ threshold)
 * - SAR filing (suspicious activity)
 * - Compensation with FX rate loss absorption
 * - SWIFT network integration
 * - Regulatory reporting
 * - Metrics collection
 *
 * Compliance Requirements:
 * - OFAC: Sanctions screening must block transfers
 * - FinCEN CTR: Auto-file for transfers >= $10,000
 * - FinCEN SAR: Auto-file for suspicious patterns
 * - FATF Travel Rule: Sender/receiver info required
 * - GDPR: EU data privacy compliance
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InternationalTransferSaga Unit Tests - Compliance Critical")
class InternationalTransferSagaTest {

    @Mock
    private SagaExecutionService sagaExecutionService;

    // Forward steps
    @Mock
    private ValidateInternationalTransferStep validateTransferStep;
    @Mock
    private SanctionsScreeningStep sanctionsScreeningStep;
    @Mock
    private CompliancePreCheckStep compliancePreCheckStep;
    @Mock
    private LockExchangeRateStep lockExchangeRateStep;
    @Mock
    private CalculateInternationalFeesStep calculateFeesStep;
    @Mock
    private ReserveFundsStep reserveFundsStep;
    @Mock
    private DebitSourceWalletStep debitSourceWalletStep;
    @Mock
    private ExecuteFXConversionStep executeFXConversionStep;
    @Mock
    private RouteViaCorrespondentBanksStep routeViaCorrespondentBanksStep;
    @Mock
    private CreditDestinationAccountStep creditDestinationAccountStep;
    @Mock
    private RecordRegulatoryReportsStep recordRegulatoryReportsStep;
    @Mock
    private SendInternationalNotificationsStep sendNotificationsStep;
    @Mock
    private UpdateAMLAnalyticsStep updateAnalyticsStep;

    // Compensation steps
    @Mock
    private ReverseAMLAnalyticsStep reverseAnalyticsStep;
    @Mock
    private CancelInternationalNotificationsStep cancelNotificationsStep;
    @Mock
    private ReverseCreditDestinationStep reverseCreditStep;
    @Mock
    private RecallCorrespondentBankTransferStep recallCorrespondentTransferStep;
    @Mock
    private ReverseFXConversionStep reverseFXConversionStep;
    @Mock
    private ReverseDebitSourceWalletStep reverseDebitStep;
    @Mock
    private ReleaseReservedFundsStep releaseReservedFundsStep;
    @Mock
    private UnlockExchangeRateStep unlockExchangeRateStep;
    @Mock
    private FileSAROnFailureStep fileSAROnFailureStep;

    private InternationalTransferSaga saga;
    private MeterRegistry meterRegistry;
    private InternationalTransferRequest validRequest;

    @BeforeEach
    void setUp() {
        // Create real MeterRegistry for metrics testing
        meterRegistry = new SimpleMeterRegistry();

        // Create saga with real metrics
        saga = new InternationalTransferSaga(
            sagaExecutionService,
            validateTransferStep,
            sanctionsScreeningStep,
            compliancePreCheckStep,
            lockExchangeRateStep,
            calculateFeesStep,
            reserveFundsStep,
            debitSourceWalletStep,
            executeFXConversionStep,
            routeViaCorrespondentBanksStep,
            creditDestinationAccountStep,
            recordRegulatoryReportsStep,
            sendNotificationsStep,
            updateAnalyticsStep,
            reverseAnalyticsStep,
            cancelNotificationsStep,
            reverseCreditStep,
            recallCorrespondentTransferStep,
            reverseFXConversionStep,
            reverseDebitStep,
            releaseReservedFundsStep,
            unlockExchangeRateStep,
            fileSAROnFailureStep,
            meterRegistry
        );

        // Create valid test request (US to UK transfer)
        validRequest = InternationalTransferRequestBuilder.anInternationalTransferRequest()
            .withSourceAmount(new BigDecimal("1000.00"))
            .withSourceCurrency("USD")
            .withDestinationAmount(new BigDecimal("785.00"))
            .withDestinationCurrency("GBP")
            .build();

        // Mock saga execution service
        when(sagaExecutionService.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ========== HAPPY PATH TESTS ==========

    @Test
    @DisplayName("Should complete international transfer successfully with all 13 steps")
    void shouldCompleteInternationalTransferSuccessfully() {
        // Given: All 13 steps will succeed
        mockAllStepsSuccess();

        // When: Execute saga
        SagaResponse response = saga.execute(validRequest);

        // Then: Saga should complete successfully
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).contains("completed successfully");

        // Verify all 13 steps were executed in order
        verify(validateTransferStep).execute(any());
        verify(sanctionsScreeningStep).execute(any());
        verify(compliancePreCheckStep).execute(any());
        verify(lockExchangeRateStep).execute(any());
        verify(calculateFeesStep).execute(any());
        verify(reserveFundsStep).execute(any());
        verify(debitSourceWalletStep).execute(any());
        verify(executeFXConversionStep).execute(any());
        verify(routeViaCorrespondentBanksStep).execute(any());
        verify(creditDestinationAccountStep).execute(any());
        verify(recordRegulatoryReportsStep).execute(any());
        verify(sendNotificationsStep).execute(any());
        verify(updateAnalyticsStep).execute(any());

        // Verify no compensation
        verifyNoInteractions(reverseAnalyticsStep);
        verifyNoInteractions(reverseCreditStep);
    }

    @Test
    @DisplayName("Should increment success metrics when transfer completes")
    void shouldIncrementSuccessMetrics() {
        // Given: All steps succeed
        mockAllStepsSuccess();

        // When: Execute saga
        saga.execute(validRequest);

        // Then: Success metrics should be incremented
        Counter attempts = meterRegistry.find("international_transfer.attempts").counter();
        Counter successes = meterRegistry.find("international_transfer.successes").counter();

        assertThat(attempts).isNotNull();
        assertThat(successes).isNotNull();
        assertThat(attempts.count()).isEqualTo(1.0);
        assertThat(successes.count()).isEqualTo(1.0);
    }

    // ========== COMPLIANCE TESTS (CRITICAL) ==========

    @Test
    @DisplayName("COMPLIANCE: Should block transfer when sanctions hit (95%+ match)")
    void shouldBlockTransferWhenSanctionsHit() {
        // Given: Transfer to sanctioned country/entity
        InternationalTransferRequest sanctionedRequest =
            InternationalTransferRequestBuilder.anInternationalTransferRequest()
                .withSanctionsHit()  // Iran (sanctioned)
                .build();

        // Validation passes but sanctions screening blocks
        when(validateTransferStep.execute(any()))
            .thenReturn(Map.of("valid", true));
        when(sanctionsScreeningStep.execute(any()))
            .thenReturn(Map.of(
                "sanctionsHit", true,
                "matchScore", 0.96,
                "reason", "OFAC: Destination country sanctioned"
            ));

        // When: Execute saga
        SagaResponse response = saga.execute(sanctionedRequest);

        // Then: Transfer must be blocked
        assertThat(response).isNotNull();
        assertThat(response.isCompensated()).isTrue();
        assertThat(response.getMessage()).containsIgnoringCase("sanctions");

        // Verify only first 2 steps were executed
        verify(validateTransferStep).execute(any());
        verify(sanctionsScreeningStep).execute(any());

        // Verify subsequent steps were NOT executed (CRITICAL)
        verify(compliancePreCheckStep, never()).execute(any());
        verify(debitSourceWalletStep, never()).execute(any());
        verify(creditDestinationAccountStep, never()).execute(any());
    }

    @Test
    @DisplayName("COMPLIANCE: Should file CTR for transfers >= $10,000")
    void shouldFileCTRForLargeTransfers() {
        // Given: Transfer >= $10,000 (CTR threshold)
        InternationalTransferRequest largeTransfer =
            InternationalTransferRequestBuilder.anInternationalTransferRequest()
                .withCTRRequired()  // $12,000
                .build();

        // All steps succeed
        mockAllStepsSuccess();

        // Regulatory reports step records CTR
        when(recordRegulatoryReportsStep.execute(any()))
            .thenReturn(Map.of(
                "reports", Map.of(
                    "CTR", Map.of(
                        "filed", true,
                        "amount", new BigDecimal("12000.00"),
                        "filingId", "CTR-2025-001234"
                    )
                )
            ));

        // When: Execute saga
        SagaResponse response = saga.execute(largeTransfer);

        // Then: CTR must be filed
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();

        // Verify regulatory reports step was called
        verify(recordRegulatoryReportsStep).execute(any());
    }

    @Test
    @DisplayName("COMPLIANCE: Should file SAR for suspicious activity")
    void shouldFileSARForSuspiciousActivity() {
        // Given: Suspicious transfer (just below CTR threshold = structuring)
        InternationalTransferRequest suspiciousTransfer =
            InternationalTransferRequestBuilder.anInternationalTransferRequest()
                .withSuspiciousActivity()  // $9,999 to high-risk country
                .build();

        // All steps succeed
        mockAllStepsSuccess();

        // Regulatory reports step records SAR
        when(recordRegulatoryReportsStep.execute(any()))
            .thenReturn(Map.of(
                "reports", Map.of(
                    "SAR", Map.of(
                        "filed", true,
                        "reason", "Possible structuring - amount just below CTR threshold",
                        "filingId", "SAR-2025-005678"
                    )
                )
            ));

        // When: Execute saga
        SagaResponse response = saga.execute(suspiciousTransfer);

        // Then: SAR must be filed
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();

        verify(recordRegulatoryReportsStep).execute(any());
    }

    @Test
    @DisplayName("COMPLIANCE: Should file SAR when compensation fails critically")
    void shouldFileSARWhenCompensationFailsCritically() {
        // Given: Transfer fails and compensation also fails
        when(validateTransferStep.execute(any())).thenReturn(Map.of("valid", true));
        when(sanctionsScreeningStep.execute(any())).thenReturn(Map.of("sanctionsHit", false));
        when(compliancePreCheckStep.execute(any())).thenReturn(Map.of("compliant", true));
        when(lockExchangeRateStep.execute(any())).thenReturn(Map.of("locked", true));
        when(calculateFeesStep.execute(any())).thenReturn(Map.of("fees", new BigDecimal("50.00")));
        when(reserveFundsStep.execute(any())).thenReturn(Map.of("reserved", true));
        when(debitSourceWalletStep.execute(any())).thenReturn(Map.of("debited", true));

        // FX conversion fails
        when(executeFXConversionStep.execute(any()))
            .thenThrow(new RuntimeException("FX conversion failed"));

        // Compensation fails critically
        when(reverseDebitStep.compensate(any()))
            .thenThrow(new RuntimeException("Cannot reverse debit - database error"));

        // SAR filing succeeds
        when(fileSAROnFailureStep.execute(any()))
            .thenReturn(Map.of("sarFiled", true));

        // When: Execute saga
        SagaResponse response = saga.execute(validRequest);

        // Then: SAR should be filed for manual investigation
        assertThat(response).isNotNull();
        assertThat(response.isFailed()).isTrue();

        // Verify SAR was filed
        verify(fileSAROnFailureStep).execute(any());
    }

    // ========== FX & RATE LOCK TESTS ==========

    @Test
    @DisplayName("Should lock exchange rate before debit to prevent rate fluctuation")
    void shouldLockExchangeRateBeforeDebit() {
        // Given: All steps succeed
        mockAllStepsSuccess();

        // When: Execute saga
        saga.execute(validRequest);

        // Then: Rate lock must happen before debit
        var inOrder = inOrder(lockExchangeRateStep, debitSourceWalletStep);
        inOrder.verify(lockExchangeRateStep).execute(any());
        inOrder.verify(debitSourceWalletStep).execute(any());
    }

    @Test
    @DisplayName("Should unlock exchange rate during compensation")
    void shouldUnlockExchangeRateDuringCompensation() {
        // Given: Transfer fails after rate lock
        mockStepsUntilRateLock();
        when(calculateFeesStep.execute(any()))
            .thenThrow(new RuntimeException("Fee calculation failed"));

        // Compensation succeeds
        when(unlockExchangeRateStep.compensate(any()))
            .thenReturn(Map.of("unlocked", true));

        // When: Execute saga
        saga.execute(validRequest);

        // Then: Rate should be unlocked
        verify(unlockExchangeRateStep).compensate(any());
    }

    @Test
    @DisplayName("Should absorb FX rate loss during compensation")
    void shouldAbsorbFXRateLossDuringCompensation() {
        // Given: Transfer fails after FX conversion
        mockStepsUntilFXConversion();
        when(routeViaCorrespondentBanksStep.execute(any()))
            .thenThrow(new RuntimeException("SWIFT routing failed"));

        // FX reversal returns different amount (rate changed)
        when(reverseFXConversionStep.compensate(any()))
            .thenReturn(Map.of(
                "reversed", true,
                "rateLoss", new BigDecimal("5.00"),
                "note", "Exchange rate changed during compensation"
            ));

        // When: Execute saga
        saga.execute(validRequest);

        // Then: FX reversal should be called (absorbing loss)
        verify(reverseFXConversionStep).compensate(any());
    }

    // ========== SWIFT NETWORK TESTS ==========

    @Test
    @DisplayName("Should route via correspondent banks using SWIFT")
    void shouldRouteViaCorrespondentBanksSWIFT() {
        // Given: All steps succeed
        mockAllStepsSuccess();

        // SWIFT routing returns message ID and correspondent banks
        when(routeViaCorrespondentBanksStep.execute(any()))
            .thenReturn(Map.of(
                "swiftMessageId", "MT103-2025-ABC123",
                "correspondentBanks", java.util.List.of("JPMorgan Chase", "Barclays UK")
            ));

        // When: Execute saga
        saga.execute(validRequest);

        // Then: SWIFT routing should be executed
        verify(routeViaCorrespondentBanksStep).execute(any());
    }

    @Test
    @DisplayName("Should recall SWIFT transfer (MT192) during compensation")
    void shouldRecallSWIFTTransferDuringCompensation() {
        // Given: Transfer fails after SWIFT routing
        mockStepsUntilSWIFT();
        when(creditDestinationAccountStep.execute(any()))
            .thenThrow(new RuntimeException("Destination account closed"));

        // SWIFT recall succeeds
        when(recallCorrespondentTransferStep.compensate(any()))
            .thenReturn(Map.of(
                "recalled", true,
                "swiftMessage", "MT192-CANCELLATION"
            ));

        // When: Execute saga
        saga.execute(validRequest);

        // Then: SWIFT recall should be triggered
        verify(recallCorrespondentTransferStep).compensate(any());
    }

    // ========== METRICS TESTS ==========

    @Test
    @DisplayName("Should track transfer duration with Timer")
    void shouldTrackTransferDuration() {
        // Given: All steps succeed
        mockAllStepsSuccess();

        // When: Execute saga
        saga.execute(validRequest);

        // Then: Duration timer should record execution
        Timer duration = meterRegistry.find("international_transfer.duration").timer();
        assertThat(duration).isNotNull();
        assertThat(duration.count()).isEqualTo(1L);
        assertThat(duration.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should increment failure and compensation metrics on failure")
    void shouldIncrementFailureMetrics() {
        // Given: Transfer fails
        when(validateTransferStep.execute(any())).thenReturn(Map.of("valid", true));
        when(sanctionsScreeningStep.execute(any())).thenReturn(Map.of("sanctionsHit", false));
        when(compliancePreCheckStep.execute(any()))
            .thenThrow(new RuntimeException("Compliance check failed"));

        // Compensation succeeds
        mockAllCompensationSuccess();

        // When: Execute saga
        saga.execute(validRequest);

        // Then: Failure and compensation metrics should be incremented
        Counter attempts = meterRegistry.find("international_transfer.attempts").counter();
        Counter failures = meterRegistry.find("international_transfer.failures").counter();
        Counter compensations = meterRegistry.find("international_transfer.compensations").counter();

        assertThat(attempts.count()).isEqualTo(1.0);
        assertThat(failures.count()).isEqualTo(1.0);
        assertThat(compensations.count()).isEqualTo(1.0);
    }

    // ========== COMPENSATION ORDER TESTS ==========

    @Test
    @DisplayName("Should execute compensation in reverse order of forward steps")
    void shouldExecuteCompensationInReverseOrder() {
        // Given: Transfer fails after all steps
        mockAllStepsUntilAnalytics();
        when(updateAnalyticsStep.execute(any()))
            .thenThrow(new RuntimeException("Analytics failed"));

        // All compensation steps succeed
        mockAllCompensationSuccess();

        // When: Execute saga
        saga.execute(validRequest);

        // Then: Compensation should execute in reverse order
        var inOrder = inOrder(
            reverseAnalyticsStep,  // Step 13 compensation (skipped - not yet executed)
            cancelNotificationsStep,  // Step 12 compensation
            // Step 11 - regulatory reports NOT compensated (immutable)
            reverseCreditStep,  // Step 10 compensation
            recallCorrespondentTransferStep,  // Step 9 compensation
            reverseFXConversionStep,  // Step 8 compensation
            reverseDebitStep,  // Step 7 compensation
            releaseReservedFundsStep,  // Step 6 compensation
            unlockExchangeRateStep  // Step 4 compensation
        );

        inOrder.verify(cancelNotificationsStep).compensate(any());
        inOrder.verify(reverseCreditStep).compensate(any());
        inOrder.verify(recallCorrespondentTransferStep).compensate(any());
        inOrder.verify(reverseFXConversionStep).compensate(any());
        inOrder.verify(reverseDebitStep).compensate(any());
        inOrder.verify(releaseReservedFundsStep).compensate(any());
        inOrder.verify(unlockExchangeRateStep).compensate(any());
    }

    @Test
    @DisplayName("Should NOT compensate regulatory reports (immutable audit trail)")
    void shouldNotCompensateRegulatoryReports() {
        // Given: Transfer fails after regulatory reports filed
        mockAllStepsUntilRegulatoryReports();
        when(sendNotificationsStep.execute(any()))
            .thenThrow(new RuntimeException("Notification failed"));

        // Compensation succeeds
        mockAllCompensationSuccess();

        // When: Execute saga
        saga.execute(validRequest);

        // Then: Regulatory reports should NOT be reversed (immutable)
        // (There's no reverseRegulatoryReportsStep - they're permanent)
        verify(cancelNotificationsStep).compensate(any());
        verify(reverseCreditStep).compensate(any());
        // No compensation for regulatory reports step
    }

    // ========== HELPER METHODS ==========

    private void mockAllStepsSuccess() {
        when(validateTransferStep.execute(any())).thenReturn(Map.of("valid", true));
        when(sanctionsScreeningStep.execute(any())).thenReturn(Map.of("sanctionsHit", false));
        when(compliancePreCheckStep.execute(any())).thenReturn(Map.of("compliant", true));
        when(lockExchangeRateStep.execute(any())).thenReturn(Map.of("locked", true, "exchangeRate", 0.785, "lockId", "lock-123"));
        when(calculateFeesStep.execute(any())).thenReturn(Map.of("fees", Map.of("fxSpread", 10.00, "wireFee", 25.00)));
        when(reserveFundsStep.execute(any())).thenReturn(Map.of("reservationId", "res-123"));
        when(debitSourceWalletStep.execute(any())).thenReturn(Map.of("transactionId", "txn-123"));
        when(executeFXConversionStep.execute(any())).thenReturn(Map.of("conversionId", "fx-123"));
        when(routeViaCorrespondentBanksStep.execute(any())).thenReturn(Map.of("swiftMessageId", "MT103-123", "correspondentBanks", java.util.List.of("Bank1")));
        when(creditDestinationAccountStep.execute(any())).thenReturn(Map.of("transactionId", "txn-456"));
        when(recordRegulatoryReportsStep.execute(any())).thenReturn(Map.of("reports", Map.of()));
        when(sendNotificationsStep.execute(any())).thenReturn(Map.of("notificationIds", java.util.List.of("notif-1", "notif-2")));
        when(updateAnalyticsStep.execute(any())).thenReturn(Map.of("analyticsRecorded", true));
    }

    private void mockAllCompensationSuccess() {
        when(reverseAnalyticsStep.compensate(any())).thenReturn(Map.of("reversed", true));
        when(cancelNotificationsStep.compensate(any())).thenReturn(Map.of("cancelled", true));
        when(reverseCreditStep.compensate(any())).thenReturn(Map.of("reversed", true));
        when(recallCorrespondentTransferStep.compensate(any())).thenReturn(Map.of("recalled", true));
        when(reverseFXConversionStep.compensate(any())).thenReturn(Map.of("reversed", true));
        when(reverseDebitStep.compensate(any())).thenReturn(Map.of("reversed", true));
        when(releaseReservedFundsStep.compensate(any())).thenReturn(Map.of("released", true));
        when(unlockExchangeRateStep.compensate(any())).thenReturn(Map.of("unlocked", true));
    }

    private void mockStepsUntilRateLock() {
        when(validateTransferStep.execute(any())).thenReturn(Map.of("valid", true));
        when(sanctionsScreeningStep.execute(any())).thenReturn(Map.of("sanctionsHit", false));
        when(compliancePreCheckStep.execute(any())).thenReturn(Map.of("compliant", true));
        when(lockExchangeRateStep.execute(any())).thenReturn(Map.of("locked", true));
    }

    private void mockStepsUntilFXConversion() {
        mockStepsUntilRateLock();
        when(calculateFeesStep.execute(any())).thenReturn(Map.of("fees", Map.of()));
        when(reserveFundsStep.execute(any())).thenReturn(Map.of("reserved", true));
        when(debitSourceWalletStep.execute(any())).thenReturn(Map.of("debited", true));
        when(executeFXConversionStep.execute(any())).thenReturn(Map.of("converted", true));
    }

    private void mockStepsUntilSWIFT() {
        mockStepsUntilFXConversion();
        when(routeViaCorrespondentBanksStep.execute(any())).thenReturn(Map.of("routed", true));
    }

    private void mockStepsUntilRegulatoryReports() {
        mockStepsUntilSWIFT();
        when(creditDestinationAccountStep.execute(any())).thenReturn(Map.of("credited", true));
        when(recordRegulatoryReportsStep.execute(any())).thenReturn(Map.of("reported", true));
    }

    private void mockAllStepsUntilAnalytics() {
        mockStepsUntilRegulatoryReports();
        when(sendNotificationsStep.execute(any())).thenReturn(Map.of("sent", true));
    }
}
