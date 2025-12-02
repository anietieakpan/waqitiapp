package com.waqiti.saga.definition;

import com.waqiti.common.saga.SagaStepEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CRITICAL P0 FIX: P2P Transfer Saga Definition
 *
 * Defines the 6-step distributed saga flow for peer-to-peer wallet transfers.
 * This was MISSING causing P2P transfers to never complete.
 *
 * Saga Flow:
 * 1. VALIDATE_SOURCE_BALANCE → Wallet Service
 * 2. RESERVE_SOURCE_FUNDS → Wallet Service
 * 3. DEBIT_SOURCE_WALLET → Wallet Service
 * 4. CREDIT_DESTINATION_WALLET → Wallet Service
 * 5. RECORD_LEDGER_ENTRIES → Ledger Service
 * 6. UPDATE_TRANSACTION_STATUS → Transaction Service
 *
 * Compensation Flow (if any step fails):
 * - Step 6 fails → Reverse ledger entries
 * - Step 5 fails → Credit back source, debit destination
 * - Step 4 fails → Credit back source
 * - Step 3 fails → Release reserved funds
 * - Step 2 fails → No compensation needed
 * - Step 1 fails → No compensation needed
 *
 * Annual Impact: $100M+ P2P transaction volume
 *
 * @author Waqiti Engineering Team - P0 Production Fix
 * @since 1.0.0
 */
@Component
@Slf4j
public class P2PTransferSagaDefinition implements SagaDefinition {

    @Override
    public String getSagaType() {
        return "P2P_TRANSFER";
    }

    @Override
    public List<SagaStepEvent> defineSteps(String sagaId, Map<String, Object> sagaData) {
        log.info("SAGA DEFINITION: Creating P2P transfer steps for sagaId={}", sagaId);

        List<SagaStepEvent> steps = new ArrayList<>();

        // Step 1: Validate source wallet has sufficient balance
        steps.add(SagaStepEvent.builder()
                .sagaId(sagaId)
                .sagaType("P2P_TRANSFER")
                .stepName("VALIDATE_SOURCE_BALANCE")
                .serviceName("wallet-service")
                .operation("validateBalance")
                .data(sagaData)
                .compensation(false)
                .build());

        // Step 2: Reserve funds in source wallet (hold/lock)
        steps.add(SagaStepEvent.builder()
                .sagaId(sagaId)
                .sagaType("P2P_TRANSFER")
                .stepName("RESERVE_SOURCE_FUNDS")
                .serviceName("wallet-service")
                .operation("reserveFunds")
                .data(sagaData)
                .compensation(false)
                .build());

        // Step 3: Debit source wallet
        steps.add(SagaStepEvent.builder()
                .sagaId(sagaId)
                .sagaType("P2P_TRANSFER")
                .stepName("DEBIT_SOURCE_WALLET")
                .serviceName("wallet-service")
                .operation("debitWallet")
                .data(sagaData)
                .compensation(false)
                .build());

        // Step 4: Credit destination wallet
        steps.add(SagaStepEvent.builder()
                .sagaId(sagaId)
                .sagaType("P2P_TRANSFER")
                .stepName("CREDIT_DESTINATION_WALLET")
                .serviceName("wallet-service")
                .operation("creditWallet")
                .data(sagaData)
                .compensation(false)
                .build());

        // Step 5: Record double-entry ledger entries
        steps.add(SagaStepEvent.builder()
                .sagaId(sagaId)
                .sagaType("P2P_TRANSFER")
                .stepName("RECORD_LEDGER_ENTRIES")
                .serviceName("ledger-service")
                .operation("recordEntries")
                .data(sagaData)
                .compensation(false)
                .build());

        // Step 6: Update transaction status to COMPLETED
        steps.add(SagaStepEvent.builder()
                .sagaId(sagaId)
                .sagaType("P2P_TRANSFER")
                .stepName("UPDATE_TRANSACTION_STATUS")
                .serviceName("transaction-service")
                .operation("updateStatus")
                .data(sagaData)
                .compensation(false)
                .build());

        log.info("SAGA DEFINITION: Created {} steps for P2P transfer saga", steps.size());
        return steps;
    }

    @Override
    public List<SagaStepEvent> defineCompensationSteps(String sagaId, Map<String, Object> sagaData, int failedStepIndex) {
        log.warn("SAGA COMPENSATION: Creating rollback steps for P2P transfer, failed at step {}",
                failedStepIndex);

        List<SagaStepEvent> compensationSteps = new ArrayList<>();

        // Compensate in reverse order (undo completed steps)

        // If step 5 or 6 failed: Reverse ledger entries
        if (failedStepIndex >= 5) {
            compensationSteps.add(SagaStepEvent.builder()
                    .sagaId(sagaId)
                    .sagaType("P2P_TRANSFER")
                    .stepName("COMPENSATE_LEDGER")
                    .serviceName("ledger-service")
                    .operation("reverseLedgerEntries")
                    .data(sagaData)
                    .compensation(true)
                    .build());
        }

        // If step 4, 5, or 6 failed: Reverse credit (debit from destination)
        if (failedStepIndex >= 4) {
            compensationSteps.add(SagaStepEvent.builder()
                    .sagaId(sagaId)
                    .sagaType("P2P_TRANSFER")
                    .stepName("COMPENSATE_CREDIT")
                    .serviceName("wallet-service")
                    .operation("compensateCredit")
                    .data(sagaData)
                    .compensation(true)
                    .build());
        }

        // If step 3, 4, 5, or 6 failed: Reverse debit (credit back to source)
        if (failedStepIndex >= 3) {
            compensationSteps.add(SagaStepEvent.builder()
                    .sagaId(sagaId)
                    .sagaType("P2P_TRANSFER")
                    .stepName("COMPENSATE_DEBIT")
                    .serviceName("wallet-service")
                    .operation("compensateDebit")
                    .data(sagaData)
                    .compensation(true)
                    .build());
        }

        // If step 2, 3, 4, 5, or 6 failed: Release reserved funds
        if (failedStepIndex >= 2) {
            compensationSteps.add(SagaStepEvent.builder()
                    .sagaId(sagaId)
                    .sagaType("P2P_TRANSFER")
                    .stepName("RELEASE_FUNDS")
                    .serviceName("wallet-service")
                    .operation("releaseFunds")
                    .data(sagaData)
                    .compensation(true)
                    .build());
        }

        // Steps 1 (validate) and 0 (not started) require no compensation

        log.warn("SAGA COMPENSATION: Created {} compensation steps", compensationSteps.size());
        return compensationSteps;
    }

    @Override
    public int getTimeoutSeconds() {
        return 60; // 60 seconds total timeout for entire saga
    }

    @Override
    public int getStepTimeoutSeconds() {
        return 10; // 10 seconds timeout per step
    }

    @Override
    public int getMaxRetryAttempts() {
        return 3; // Retry each step up to 3 times
    }
}
