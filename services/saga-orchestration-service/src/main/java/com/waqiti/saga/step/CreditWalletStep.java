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
 * Credits the destination wallet
 * 
 * This step adds the funds to the destination wallet.
 */
@Component
public class CreditWalletStep implements SagaStep {
    
    private static final Logger logger = LoggerFactory.getLogger(CreditWalletStep.class);
    
    private final WalletServiceClient walletServiceClient;
    
    public CreditWalletStep(WalletServiceClient walletServiceClient) {
        this.walletServiceClient = walletServiceClient;
    }
    
    @Override
    public String getStepName() {
        return "CreditWallet";
    }
    
    @Override
    public boolean isCompensatable() {
        return true;
    }
    
    @Override
    public StepExecutionResult execute(SagaExecution execution) {
        try {
            logger.info("Crediting wallet for saga: {}", execution.getSagaId());
            
            // Get transfer details from context
            String fromUserId = (String) execution.getContextValue("fromUserId");
            String toUserId = (String) execution.getContextValue("toUserId");
            BigDecimal amount = (BigDecimal) execution.getContextValue("amount");
            String currency = (String) execution.getContextValue("currency");
            String transactionId = (String) execution.getContextValue("transactionId");
            String destWalletId = (String) execution.getContextValue("destWalletId");
            String debitTransactionId = (String) execution.getContextValue("debitTransactionId");
            
            if (destWalletId == null) {
                return StepExecutionResult.failure("Missing destination wallet ID", "INVALID_CONTEXT");
            }
            
            // Create credit request
            CreditWalletRequest request = CreditWalletRequest.builder()
                .walletId(destWalletId)
                .userId(toUserId)
                .amount(amount)
                .currency(currency)
                .transactionId(transactionId)
                .sagaId(execution.getSagaId())
                .relatedTransactionId(debitTransactionId)
                .description("P2P Transfer from " + fromUserId)
                .category("P2P_TRANSFER")
                .metadata(Map.of(
                    "fromUserId", fromUserId,
                    "transactionType", "P2P_TRANSFER",
                    "sagaId", execution.getSagaId(),
                    "debitTransactionId", debitTransactionId
                ))
                .build();
            
            // Credit wallet
            WalletTransactionResponse response = walletServiceClient.creditWallet(request);
            
            if (!response.isSuccess()) {
                return StepExecutionResult.failure("Failed to credit wallet: " + response.getReason(), 
                    response.getErrorCode());
            }
            
            // Store credit details in context
            Map<String, Object> stepData = new HashMap<>();
            stepData.put("creditTransactionId", response.getWalletTransactionId());
            stepData.put("creditAmount", response.getAmount());
            stepData.put("creditBalance", response.getNewBalance());
            stepData.put("creditTimestamp", response.getTimestamp());
            
            logger.info("Wallet credited successfully for saga: {} with transaction ID: {}", 
                       execution.getSagaId(), response.getWalletTransactionId());
            
            return StepExecutionResult.success(stepData);
            
        } catch (Exception e) {
            logger.error("Failed to credit wallet for saga: {}", execution.getSagaId(), e);
            return StepExecutionResult.failure("Wallet credit error: " + e.getMessage(), 
                "CREDIT_ERROR");
        }
    }
}