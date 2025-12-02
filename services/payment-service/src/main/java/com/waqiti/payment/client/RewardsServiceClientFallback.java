package com.waqiti.payment.client;

import com.waqiti.payment.dto.rewards.ProcessPaymentRewardsRequest;
import com.waqiti.payment.dto.rewards.ProcessPaymentRewardsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Fallback implementation for Rewards Service Client
 * 
 * Provides graceful degradation when rewards-service is unavailable:
 * - Returns success response without actually awarding rewards
 * - Logs the failure for manual processing
 * - Allows payment flow to continue
 * - Rewards can be calculated retroactively through batch reconciliation
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-27
 */
@Component
@Slf4j
public class RewardsServiceClientFallback implements RewardsServiceClient {
    
    @Override
    public ProcessPaymentRewardsResponse processPaymentRewards(ProcessPaymentRewardsRequest request) {
        log.warn("Rewards service unavailable - using fallback for payment: {}. " +
                "Rewards will be calculated retroactively.", request.getPaymentId());
        
        // Return default response - rewards processing will be handled by batch job
        return ProcessPaymentRewardsResponse.builder()
            .paymentId(request.getPaymentId())
            .userId(request.getUserId())
            .pointsAwarded(BigDecimal.ZERO)
            .cashbackAwarded(BigDecimal.ZERO)
            .success(true)
            .message("Rewards service temporarily unavailable - will be processed in batch")
            .requiresManualProcessing(true)
            .build();
    }
}