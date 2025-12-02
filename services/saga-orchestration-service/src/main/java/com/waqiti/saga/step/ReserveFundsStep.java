package com.waqiti.saga.step;

import com.waqiti.saga.client.WalletServiceClient;
import com.waqiti.saga.domain.SagaExecution;
import com.waqiti.saga.dto.ReserveFundsRequest;
import com.waqiti.saga.dto.ReserveFundsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Reserves funds in the source wallet
 * 
 * This step places a hold on the transfer amount in the source wallet
 * to ensure the funds are available throughout the transaction.
 */
@Component
public class ReserveFundsStep implements SagaStep {
    
    private static final Logger logger = LoggerFactory.getLogger(ReserveFundsStep.class);
    
    private final WalletServiceClient walletServiceClient;
    
    public ReserveFundsStep(WalletServiceClient walletServiceClient) {
        this.walletServiceClient = walletServiceClient;
    }
    
    @Override
    public String getStepName() {
        return "ReserveFunds";
    }
    
    @Override
    public boolean isCompensatable() {
        return true;
    }
    
    @Override
    public StepExecutionResult execute(SagaExecution execution) {
        try {
            logger.info("Reserving funds for saga: {}", execution.getSagaId());
            
            // Get transfer details from context
            String fromUserId = (String) execution.getContextValue("fromUserId");
            BigDecimal amount = (BigDecimal) execution.getContextValue("amount");
            String currency = (String) execution.getContextValue("currency");
            String transactionId = (String) execution.getContextValue("transactionId");
            String sourceWalletId = (String) execution.getContextValue("sourceWalletId");
            
            if (fromUserId == null || amount == null || currency == null || sourceWalletId == null) {
                return StepExecutionResult.failure("Missing required context data", "INVALID_CONTEXT");
            }
            
            // Create reservation request
            ReserveFundsRequest request = ReserveFundsRequest.builder()
                .walletId(sourceWalletId)
                .userId(fromUserId)
                .amount(amount)
                .currency(currency)
                .transactionId(transactionId)
                .sagaId(execution.getSagaId())
                .reason("P2P Transfer")
                .expiresInMinutes(30) // Reserve for 30 minutes
                .build();
            
            // Reserve funds
            ReserveFundsResponse response = walletServiceClient.reserveFunds(request);
            
            if (!response.isSuccess()) {
                return StepExecutionResult.failure("Failed to reserve funds: " + response.getReason(), 
                    response.getErrorCode());
            }
            
            // Store reservation details in context
            Map<String, Object> stepData = new HashMap<>();
            stepData.put("reservationId", response.getReservationId());
            stepData.put("reservedAmount", response.getReservedAmount());
            stepData.put("reservationExpiresAt", response.getExpiresAt());
            
            logger.info("Funds reserved successfully for saga: {} with reservation ID: {}", 
                       execution.getSagaId(), response.getReservationId());
            
            return StepExecutionResult.success(stepData);
            
        } catch (Exception e) {
            logger.error("Failed to reserve funds for saga: {}", execution.getSagaId(), e);
            return StepExecutionResult.failure("Fund reservation error: " + e.getMessage(), 
                "RESERVATION_ERROR");
        }
    }
}