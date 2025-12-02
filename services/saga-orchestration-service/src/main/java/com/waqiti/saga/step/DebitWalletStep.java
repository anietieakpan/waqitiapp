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
 * Debits the source wallet
 * 
 * This step removes the funds from the source wallet using the reservation
 * created in the previous step.
 */
@Component
public class DebitWalletStep implements SagaStep {
    
    private static final Logger logger = LoggerFactory.getLogger(DebitWalletStep.class);
    
    private final WalletServiceClient walletServiceClient;
    
    public DebitWalletStep(WalletServiceClient walletServiceClient) {
        this.walletServiceClient = walletServiceClient;
    }
    
    @Override
    public String getStepName() {
        return "DebitWallet";
    }
    
    @Override
    public boolean isCompensatable() {
        return true;
    }
    
    @Override
    public StepExecutionResult execute(SagaExecution execution) {
        try {
            logger.info("Debiting wallet for saga: {}", execution.getSagaId());
            
            // Get transfer details from context
            String fromUserId = (String) execution.getContextValue("fromUserId");
            String toUserId = (String) execution.getContextValue("toUserId");
            BigDecimal amount = (BigDecimal) execution.getContextValue("amount");
            String currency = (String) execution.getContextValue("currency");
            String transactionId = (String) execution.getContextValue("transactionId");
            String sourceWalletId = (String) execution.getContextValue("sourceWalletId");
            String reservationId = (String) execution.getContextValue("reservationId");
            
            if (sourceWalletId == null || reservationId == null) {
                return StepExecutionResult.failure("Missing wallet or reservation ID", "INVALID_CONTEXT");
            }
            
            // Create debit request
            DebitWalletRequest request = DebitWalletRequest.builder()
                .walletId(sourceWalletId)
                .userId(fromUserId)
                .amount(amount)
                .currency(currency)
                .transactionId(transactionId)
                .sagaId(execution.getSagaId())
                .reservationId(reservationId)
                .description("P2P Transfer to " + toUserId)
                .category("P2P_TRANSFER")
                .metadata(Map.of(
                    "toUserId", toUserId,
                    "transactionType", "P2P_TRANSFER",
                    "sagaId", execution.getSagaId()
                ))
                .build();
            
            // Debit wallet
            WalletTransactionResponse response = walletServiceClient.debitWallet(request);
            
            if (!response.isSuccess()) {
                return StepExecutionResult.failure("Failed to debit wallet: " + response.getReason(), 
                    response.getErrorCode());
            }
            
            // Store debit details in context
            Map<String, Object> stepData = new HashMap<>();
            stepData.put("debitTransactionId", response.getWalletTransactionId());
            stepData.put("debitAmount", response.getAmount());
            stepData.put("debitBalance", response.getNewBalance());
            stepData.put("debitTimestamp", response.getTimestamp());
            
            logger.info("Wallet debited successfully for saga: {} with transaction ID: {}", 
                       execution.getSagaId(), response.getWalletTransactionId());
            
            return StepExecutionResult.success(stepData);
            
        } catch (Exception e) {
            logger.error("Failed to debit wallet for saga: {}", execution.getSagaId(), e);
            return StepExecutionResult.failure("Wallet debit error: " + e.getMessage(), 
                "DEBIT_ERROR");
        }
    }
}