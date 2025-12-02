package com.waqiti.saga.step;

import com.waqiti.saga.client.UserServiceClient;
import com.waqiti.saga.client.WalletServiceClient;
import com.waqiti.saga.domain.SagaExecution;
import com.waqiti.saga.dto.P2PTransferRequest;
import com.waqiti.saga.dto.UserValidationResponse;
import com.waqiti.saga.dto.WalletBalanceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Validates the transfer request
 * 
 * Checks:
 * - User accounts exist and are active
 * - Source wallet has sufficient funds
 * - Transfer limits are not exceeded
 * - Users are not blocked or suspended
 */
@Component
public class ValidateTransferStep implements SagaStep {
    
    private static final Logger logger = LoggerFactory.getLogger(ValidateTransferStep.class);
    
    private final UserServiceClient userServiceClient;
    private final WalletServiceClient walletServiceClient;
    
    public ValidateTransferStep(UserServiceClient userServiceClient, 
                              WalletServiceClient walletServiceClient) {
        this.userServiceClient = userServiceClient;
        this.walletServiceClient = walletServiceClient;
    }
    
    @Override
    public String getStepName() {
        return "ValidateTransfer";
    }
    
    @Override
    public StepExecutionResult execute(SagaExecution execution) {
        try {
            logger.info("Validating transfer for saga: {}", execution.getSagaId());
            
            // Extract request data
            P2PTransferRequest request = (P2PTransferRequest) execution.getContextValue("request");
            if (request == null) {
                return StepExecutionResult.failure("Transfer request not found in context", "INVALID_REQUEST");
            }
            
            // Validate source user
            UserValidationResponse sourceUser = userServiceClient.validateUser(request.getFromUserId());
            if (!sourceUser.isValid()) {
                return StepExecutionResult.failure("Source user validation failed: " + sourceUser.getReason(), 
                    "SOURCE_USER_INVALID");
            }
            
            // Validate destination user
            UserValidationResponse destUser = userServiceClient.validateUser(request.getToUserId());
            if (!destUser.isValid()) {
                return StepExecutionResult.failure("Destination user validation failed: " + destUser.getReason(), 
                    "DEST_USER_INVALID");
            }
            
            // Check wallet balance
            WalletBalanceResponse balanceResponse = walletServiceClient.getBalance(
                request.getFromUserId(), request.getCurrency());
            
            if (balanceResponse.getAvailableBalance().compareTo(request.getAmount()) < 0) {
                return StepExecutionResult.failure("Insufficient funds. Available: " + 
                    balanceResponse.getAvailableBalance() + ", Required: " + request.getAmount(), 
                    "INSUFFICIENT_FUNDS");
            }
            
            // Check transfer limits
            if (!checkTransferLimits(sourceUser, request.getAmount())) {
                return StepExecutionResult.failure("Transfer amount exceeds user limits", 
                    "LIMIT_EXCEEDED");
            }
            
            // Store validation data in context
            Map<String, Object> stepData = new HashMap<>();
            stepData.put("sourceUserStatus", sourceUser.getStatus());
            stepData.put("destUserStatus", destUser.getStatus());
            stepData.put("availableBalance", balanceResponse.getAvailableBalance());
            stepData.put("sourceWalletId", balanceResponse.getWalletId());
            
            // Get destination wallet ID
            WalletBalanceResponse destWallet = walletServiceClient.getBalance(
                request.getToUserId(), request.getCurrency());
            stepData.put("destWalletId", destWallet.getWalletId());
            
            logger.info("Transfer validation successful for saga: {}", execution.getSagaId());
            return StepExecutionResult.success(stepData);
            
        } catch (Exception e) {
            logger.error("Transfer validation failed for saga: {}", execution.getSagaId(), e);
            return StepExecutionResult.failure("Validation error: " + e.getMessage(), "VALIDATION_ERROR");
        }
    }
    
    private boolean checkTransferLimits(UserValidationResponse user, BigDecimal amount) {
        // Check daily limit
        BigDecimal dailyLimit = user.getDailyTransferLimit();
        if (dailyLimit != null && amount.compareTo(dailyLimit) > 0) {
            return false;
        }
        
        // Check single transaction limit
        BigDecimal singleLimit = user.getSingleTransactionLimit();
        if (singleLimit != null && amount.compareTo(singleLimit) > 0) {
            return false;
        }
        
        return true;
    }
}