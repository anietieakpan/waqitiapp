package com.waqiti.saga.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class TransferSaga extends Saga {
    
    private static final List<String> TRANSFER_STEPS = Arrays.asList(
        "VALIDATE_ACCOUNTS",
        "FRAUD_CHECK",
        "COMPLIANCE_CHECK",
        "RESERVE_FUNDS",
        "DEBIT_SOURCE",
        "CREDIT_DESTINATION",
        "FINALIZE_TRANSACTION",
        "SEND_NOTIFICATIONS"
    );
    
    // Transfer-specific data
    private String transactionId;
    private String fromAccountId;
    private String toAccountId;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String userId;
    
    // Step completion tracking
    private Map<String, Boolean> stepCompletions;
    private Map<String, String> stepResults;
    
    public TransferSaga(String transactionId, String fromAccountId, String toAccountId, 
                       BigDecimal amount, String currency, String userId) {
        super("TRANSFER", transactionId);
        this.transactionId = transactionId;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.currency = currency;
        this.userId = userId;
        this.stepCompletions = new HashMap<>();
        this.stepResults = new HashMap<>();
        
        // Set expiration to 30 minutes from now
        setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
        
        // Initialize saga data
        Map<String, Object> sagaData = new HashMap<>();
        sagaData.put("transactionId", transactionId);
        sagaData.put("fromAccountId", fromAccountId);
        sagaData.put("toAccountId", toAccountId);
        sagaData.put("amount", amount);
        sagaData.put("currency", currency);
        sagaData.put("userId", userId);
        setSagaData(sagaData);
        
        // Initialize all steps as incomplete
        TRANSFER_STEPS.forEach(step -> stepCompletions.put(step, false));
    }
    
    @Override
    public void start() {
        log.info("Starting transfer saga: {} for transaction: {}", getSagaId(), transactionId);
        setStatus(SagaStatus.IN_PROGRESS.name());
        setCurrentStep(TRANSFER_STEPS.get(0));
        setStepIndex(0);
    }
    
    @Override
    public void handleEvent(Object event) {
        log.debug("Handling event in transfer saga: {} for step: {}", getSagaId(), getCurrentStep());
        
        try {
            String currentStep = getCurrentStep();
            
            switch (currentStep) {
                case "VALIDATE_ACCOUNTS":
                    handleAccountValidation(event);
                    break;
                case "FRAUD_CHECK":
                    handleFraudCheck(event);
                    break;
                case "COMPLIANCE_CHECK":
                    handleComplianceCheck(event);
                    break;
                case "RESERVE_FUNDS":
                    handleFundsReservation(event);
                    break;
                case "DEBIT_SOURCE":
                    handleSourceDebit(event);
                    break;
                case "CREDIT_DESTINATION":
                    handleDestinationCredit(event);
                    break;
                case "FINALIZE_TRANSACTION":
                    handleTransactionFinalization(event);
                    break;
                case "SEND_NOTIFICATIONS":
                    handleNotifications(event);
                    break;
                default:
                    log.warn("Unknown step in transfer saga: {}", currentStep);
            }
            
            // Check if we can proceed to next step
            if (stepCompletions.get(currentStep) && canProceed()) {
                proceedToNextStep();
            }
            
        } catch (Exception e) {
            log.error("Error handling event in transfer saga: {}", getSagaId(), e);
            markAsFailed("Error processing step: " + getCurrentStep() + " - " + e.getMessage());
        }
    }
    
    @Override
    public void compensate() {
        log.info("Starting compensation for transfer saga: {}", getSagaId());
        setStatus(SagaStatus.COMPENSATING.name());
        
        // Compensate in reverse order
        for (int i = getStepIndex(); i >= 0; i--) {
            String step = TRANSFER_STEPS.get(i);
            if (stepCompletions.get(step)) {
                compensateStep(step);
            }
        }
        
        markAsCompensated();
    }
    
    @Override
    public boolean canProceed() {
        String currentStep = getCurrentStep();
        return stepCompletions.get(currentStep) && !SagaStatus.FAILED.name().equals(getStatus());
    }
    
    @Override
    public String getNextStep() {
        int nextIndex = getStepIndex() + 1;
        if (nextIndex < TRANSFER_STEPS.size()) {
            return TRANSFER_STEPS.get(nextIndex);
        }
        return null;
    }
    
    private void handleAccountValidation(Object event) {
        log.debug("Handling account validation for transaction: {}", transactionId);
        
        // Simulate account validation logic
        if (fromAccountId != null && toAccountId != null && !fromAccountId.equals(toAccountId)) {
            stepCompletions.put("VALIDATE_ACCOUNTS", true);
            stepResults.put("VALIDATE_ACCOUNTS", "PASSED");
            log.info("Account validation passed for transaction: {}", transactionId);
        } else {
            markAsFailed("Account validation failed: Invalid account IDs");
        }
    }
    
    private void handleFraudCheck(Object event) {
        log.debug("Handling fraud check for transaction: {}", transactionId);
        
        // This would integrate with the ML fraud detection service
        // For now, simulate based on amount
        boolean fraudCheckPassed = amount.compareTo(BigDecimal.valueOf(50000)) <= 0;
        
        if (fraudCheckPassed) {
            stepCompletions.put("FRAUD_CHECK", true);
            stepResults.put("FRAUD_CHECK", "PASSED");
            log.info("Fraud check passed for transaction: {}", transactionId);
        } else {
            markAsFailed("Fraud check failed: High risk transaction detected");
        }
    }
    
    private void handleComplianceCheck(Object event) {
        log.debug("Handling compliance check for transaction: {}", transactionId);
        
        // Simulate compliance checks (AML, sanctions, etc.)
        boolean complianceCheckPassed = true; // Simplified for demo
        
        if (complianceCheckPassed) {
            stepCompletions.put("COMPLIANCE_CHECK", true);
            stepResults.put("COMPLIANCE_CHECK", "PASSED");
            log.info("Compliance check passed for transaction: {}", transactionId);
        } else {
            markAsFailed("Compliance check failed: AML/Sanctions violation detected");
        }
    }
    
    private void handleFundsReservation(Object event) {
        log.debug("Handling funds reservation for transaction: {}", transactionId);
        
        // This would reserve funds in the source account
        stepCompletions.put("RESERVE_FUNDS", true);
        stepResults.put("RESERVE_FUNDS", "RESERVED");
        log.info("Funds reserved for transaction: {}", transactionId);
    }
    
    private void handleSourceDebit(Object event) {
        log.debug("Handling source account debit for transaction: {}", transactionId);
        
        // This would debit the source account
        stepCompletions.put("DEBIT_SOURCE", true);
        stepResults.put("DEBIT_SOURCE", "DEBITED");
        log.info("Source account debited for transaction: {}", transactionId);
    }
    
    private void handleDestinationCredit(Object event) {
        log.debug("Handling destination account credit for transaction: {}", transactionId);
        
        // This would credit the destination account
        stepCompletions.put("CREDIT_DESTINATION", true);
        stepResults.put("CREDIT_DESTINATION", "CREDITED");
        log.info("Destination account credited for transaction: {}", transactionId);
    }
    
    private void handleTransactionFinalization(Object event) {
        log.debug("Handling transaction finalization for transaction: {}", transactionId);
        
        // Update transaction status, create ledger entries, etc.
        stepCompletions.put("FINALIZE_TRANSACTION", true);
        stepResults.put("FINALIZE_TRANSACTION", "FINALIZED");
        log.info("Transaction finalized: {}", transactionId);
    }
    
    private void handleNotifications(Object event) {
        log.debug("Handling notifications for transaction: {}", transactionId);
        
        // Send notifications to users
        stepCompletions.put("SEND_NOTIFICATIONS", true);
        stepResults.put("SEND_NOTIFICATIONS", "SENT");
        log.info("Notifications sent for transaction: {}", transactionId);
    }
    
    private void proceedToNextStep() {
        String nextStep = getNextStep();
        if (nextStep != null) {
            moveToNextStep();
            setCurrentStep(nextStep);
            log.info("Transfer saga {} proceeding to step: {}", getSagaId(), nextStep);
        } else {
            // All steps completed
            markAsCompleted();
            log.info("Transfer saga {} completed successfully", getSagaId());
        }
    }
    
    private void compensateStep(String step) {
        log.info("Compensating step: {} in saga: {}", step, getSagaId());
        
        switch (step) {
            case "CREDIT_DESTINATION":
                // Reverse the credit (debit destination account)
                log.info("Reversing destination credit for transaction: {}", transactionId);
                break;
            case "DEBIT_SOURCE":
                // Reverse the debit (credit source account)
                log.info("Reversing source debit for transaction: {}", transactionId);
                break;
            case "RESERVE_FUNDS":
                // Release reserved funds
                log.info("Releasing reserved funds for transaction: {}", transactionId);
                break;
            default:
                log.debug("No compensation needed for step: {}", step);
        }
    }
    
    @Override
    protected int getMaxRetries() {
        return 5; // Allow more retries for transfer sagas
    }
}