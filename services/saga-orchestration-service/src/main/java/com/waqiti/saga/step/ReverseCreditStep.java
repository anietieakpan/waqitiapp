package com.waqiti.saga.step;

import com.waqiti.saga.client.WalletServiceClient;
import com.waqiti.saga.domain.SagaExecution;
import com.waqiti.saga.dto.DebitWalletRequest;
import com.waqiti.saga.dto.WalletTransactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Compensation step: Reverses the credit transaction
 * 
 * This step debits the destination wallet if the saga fails
 * after the credit was successful but before completion.
 */
@Component
public class ReverseCreditStep implements SagaStep {
    
    private static final Logger logger = LoggerFactory.getLogger(ReverseCreditStep.class);
    
    private final WalletServiceClient walletServiceClient;
    
    public ReverseCreditStep(WalletServiceClient walletServiceClient) {
        this.walletServiceClient = walletServiceClient;
    }
    
    @Override
    public String getStepName() {
        return "ReverseCredit";
    }
    
    @Override
    public StepExecutionResult execute(SagaExecution execution) {
        try {
            logger.info("Reversing credit for saga: {}", execution.getSagaId());
            
            // Get transaction details from context
            String toUserId = (String) execution.getContextValue("toUserId");
            BigDecimal amount = (BigDecimal) execution.getContextValue("amount");
            String currency = (String) execution.getContextValue("currency");
            String transactionId = (String) execution.getContextValue("transactionId");
            String destWalletId = (String) execution.getContextValue("destWalletId");
            String creditTransactionId = (String) execution.getContextValue("creditTransactionId");
            
            if (creditTransactionId == null) {
                logger.warn("No credit transaction found for saga: {} - skipping reversal", execution.getSagaId());
                return StepExecutionResult.success();
            }
            
            // Create reversal debit request
            DebitWalletRequest request = DebitWalletRequest.builder()
                .walletId(destWalletId)
                .userId(toUserId)
                .amount(amount)
                .currency(currency)
                .transactionId(transactionId + "-reverse-credit")
                .sagaId(execution.getSagaId())
                .description("P2P Transfer Reversal - Credit Compensation")
                .category("P2P_TRANSFER_REVERSAL")
                .metadata(Map.of(
                    "originalTransactionId", creditTransactionId,
                    "transactionType", "P2P_TRANSFER_REVERSAL",
                    "sagaId", execution.getSagaId(),
                    "reversalType", "CREDIT_REVERSAL",
                    "reason", "Saga compensation"
                ))
                .allowNegativeBalance(false) // Don't allow negative balance
                .build();
            
            // Debit the destination wallet to reverse the credit
            WalletTransactionResponse response = walletServiceClient.debitWallet(request);
            
            if (!response.isSuccess()) {
                // If we can't reverse due to insufficient funds, log but don't fail the compensation
                if ("INSUFFICIENT_FUNDS".equals(response.getErrorCode())) {
                    logger.warn("Cannot reverse credit due to insufficient funds in destination wallet for saga: {} - " +
                              "manual intervention may be required", execution.getSagaId());
                    
                    // Create a pending reversal record for manual processing
                    Map<String, Object> stepData = new HashMap<>();
                    stepData.put("creditReversalPending", true);
                    stepData.put("creditReversalReason", "Insufficient funds");
                    stepData.put("creditReversalAmount", amount);
                    stepData.put("manualInterventionRequired", true);
                    
                    return StepExecutionResult.success(stepData);
                } else {
                    logger.error("Failed to reverse credit for saga: {} - {}", 
                               execution.getSagaId(), response.getReason());
                    return StepExecutionResult.failure("Failed to reverse credit: " + response.getReason(), 
                        response.getErrorCode());
                }
            }
            
            // Store reversal details in context
            Map<String, Object> stepData = new HashMap<>();
            stepData.put("creditReversalTransactionId", response.getWalletTransactionId());
            stepData.put("creditReversalAmount", response.getAmount());
            stepData.put("creditReversalBalance", response.getNewBalance());
            stepData.put("creditReversalTimestamp", response.getTimestamp());
            stepData.put("creditReversalPending", false);
            
            logger.info("Credit reversed successfully for saga: {} with transaction ID: {}", 
                       execution.getSagaId(), response.getWalletTransactionId());
            
            return StepExecutionResult.success(stepData);
            
        } catch (Exception e) {
            logger.error("Failed to reverse credit for saga: {}", execution.getSagaId(), e);
            return StepExecutionResult.failure("Credit reversal error: " + e.getMessage(), 
                "CREDIT_REVERSAL_ERROR");
        }
    }
}