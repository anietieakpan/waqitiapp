package com.waqiti.saga.step;

import com.waqiti.saga.client.WalletServiceClient;
import com.waqiti.saga.domain.SagaExecution;
import com.waqiti.saga.dto.ReleaseReservationRequest;
import com.waqiti.saga.dto.WalletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Compensation step: Releases reserved funds
 * 
 * This step releases the fund reservation if the saga fails
 * after funds were reserved but before successful completion.
 */
@Component
public class ReleaseReservedFundsStep implements SagaStep {
    
    private static final Logger logger = LoggerFactory.getLogger(ReleaseReservedFundsStep.class);
    
    private final WalletServiceClient walletServiceClient;
    
    public ReleaseReservedFundsStep(WalletServiceClient walletServiceClient) {
        this.walletServiceClient = walletServiceClient;
    }
    
    @Override
    public String getStepName() {
        return "ReleaseReservedFunds";
    }
    
    @Override
    public StepExecutionResult execute(SagaExecution execution) {
        try {
            logger.info("Releasing reserved funds for saga: {}", execution.getSagaId());
            
            // Get reservation details from context
            String reservationId = (String) execution.getContextValue("reservationId");
            String sourceWalletId = (String) execution.getContextValue("sourceWalletId");
            String fromUserId = (String) execution.getContextValue("fromUserId");
            
            if (reservationId == null) {
                logger.warn("No reservation ID found for saga: {} - skipping release", execution.getSagaId());
                return StepExecutionResult.success();
            }
            
            // Create release request
            ReleaseReservationRequest request = ReleaseReservationRequest.builder()
                .reservationId(reservationId)
                .walletId(sourceWalletId)
                .userId(fromUserId)
                .sagaId(execution.getSagaId())
                .reason("Saga compensation - releasing reserved funds")
                .build();
            
            // Release reservation
            WalletResponse response = walletServiceClient.releaseReservation(request);
            
            if (!response.isSuccess()) {
                logger.error("Failed to release reservation for saga: {} - {}", 
                           execution.getSagaId(), response.getReason());
                return StepExecutionResult.failure("Failed to release reservation: " + response.getReason(), 
                    response.getErrorCode());
            }
            
            // Store release details in context
            Map<String, Object> stepData = new HashMap<>();
            stepData.put("reservationReleased", true);
            stepData.put("releaseTimestamp", java.time.LocalDateTime.now());
            
            logger.info("Reserved funds released successfully for saga: {}", execution.getSagaId());
            return StepExecutionResult.success(stepData);
            
        } catch (Exception e) {
            logger.error("Failed to release reserved funds for saga: {}", execution.getSagaId(), e);
            return StepExecutionResult.failure("Reservation release error: " + e.getMessage(), 
                "RELEASE_ERROR");
        }
    }
}