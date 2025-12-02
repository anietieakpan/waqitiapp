package com.waqiti.payment.client;

import com.waqiti.payment.dto.rewards.ProcessPaymentRewardsRequest;
import com.waqiti.payment.dto.rewards.ProcessPaymentRewardsResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;

/**
 * Feign Client for Rewards Service Integration
 * 
 * Handles communication with the rewards-service for:
 * - Processing payment rewards
 * - Calculating loyalty points
 * - Applying cashback
 * - Managing reward campaigns
 * 
 * RESILIENCE:
 * - Circuit breaker protection
 * - Automatic retries with exponential backoff
 * - Fallback to default behavior
 * - Comprehensive error handling
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-27
 */
@FeignClient(
    name = "rewards-service",
    path = "/api/v1/rewards",
    fallback = RewardsServiceClientFallback.class
)
public interface RewardsServiceClient {
    
    /**
     * Process rewards for a completed payment
     * 
     * Calculates and awards:
     * - Loyalty points based on amount and category
     * - Cashback percentages
     * - Campaign bonuses
     * - Referral rewards
     * 
     * @param request Payment rewards request
     * @return Processed rewards response with points awarded
     */
    @PostMapping("/process-payment")
    @CircuitBreaker(name = "rewards-service")
    @Retry(name = "rewards-service")
    ProcessPaymentRewardsResponse processPaymentRewards(
        @Valid @RequestBody ProcessPaymentRewardsRequest request
    );
}