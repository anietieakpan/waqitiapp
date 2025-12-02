package com.waqiti.saga.step;

import com.waqiti.saga.client.WalletServiceClient;
import com.waqiti.saga.domain.SagaExecution;
import com.waqiti.saga.dto.CreditWalletRequest;
import com.waqiti.saga.dto.WalletTransactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Compensation step: Reverses the debit transaction
 * 
 * This step credits back the source wallet if the saga fails
 * after the debit was successful but before completion.
 */
@Component
public class ReverseDebitStep implements SagaStep {
    
    private static final Logger logger = LoggerFactory.getLogger(ReverseDebitStep.class);
    
    private final WalletServiceClient walletServiceClient;
    
    public ReverseDebitStep(WalletServiceClient walletServiceClient) {
        this.walletServiceClient = walletServiceClient;
    }
    
    @Override
    public String getStepName() {
        return "ReverseDebit";
    }
    
    @Override
    public StepExecutionResult execute(SagaExecution execution) {
        try {
            logger.info("Reversing debit for saga: {}", execution.getSagaId());
            
            // Get transaction details from context
            String fromUserId = (String) execution.getContextValue("fromUserId");
            BigDecimal amount = (BigDecimal) execution.getContextValue("amount");
            String currency = (String) execution.getContextValue("currency");
            String transactionId = (String) execution.getContextValue("transactionId");
            String sourceWalletId = (String) execution.getContextValue("sourceWalletId");
            String debitTransactionId = (String) execution.getContextValue("debitTransactionId");
            
            if (debitTransactionId == null) {
                logger.warn("No debit transaction found for saga: {} - skipping reversal", execution.getSagaId());
                return StepExecutionResult.success();
            }
            
            // Create reversal credit request
            CreditWalletRequest request = CreditWalletRequest.builder()
                .walletId(sourceWalletId)
                .userId(fromUserId)
                .amount(amount)
                .currency(currency)
                .transactionId(transactionId + "-reverse-debit")
                .sagaId(execution.getSagaId())
                .relatedTransactionId(debitTransactionId)
                .description("P2P Transfer Reversal - Debit Compensation")
                .category("P2P_TRANSFER_REVERSAL")
                .metadata(Map.of(
                    "originalTransactionId", debitTransactionId,
                    "transactionType", "P2P_TRANSFER_REVERSAL",
                    "sagaId", execution.getSagaId(),
                    "reversalType", "DEBIT_REVERSAL",
                    "reason", "Saga compensation"
                ))
                .build();
            
            // Credit back the source wallet
            WalletTransactionResponse response = walletServiceClient.creditWallet(request);
            
            if (!response.isSuccess()) {
                logger.error("Failed to reverse debit for saga: {} - {}", 
                           execution.getSagaId(), response.getReason());
                return StepExecutionResult.failure("Failed to reverse debit: " + response.getReason(), 
                    response.getErrorCode());
            }
            
            // Store reversal details in context
            Map<String, Object> stepData = new HashMap<>();
            stepData.put("debitReversalTransactionId", response.getWalletTransactionId());
            stepData.put("debitReversalAmount", response.getAmount());
            stepData.put("debitReversalBalance", response.getNewBalance());
            stepData.put("debitReversalTimestamp", response.getTimestamp());
            
            logger.info("Debit reversed successfully for saga: {} with transaction ID: {}", 
                       execution.getSagaId(), response.getWalletTransactionId());
            
            return StepExecutionResult.success(stepData);
            
        } catch (Exception e) {
            logger.error("Failed to reverse debit for saga: {}", execution.getSagaId(), e);
            return StepExecutionResult.failure("Debit reversal error: " + e.getMessage(), 
                "DEBIT_REVERSAL_ERROR");
        }
    }
}