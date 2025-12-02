package com.waqiti.compliance.feign;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/**
 * Wallet Service Client
 *
 * FeignClient for wallet-service integration to freeze accounts
 * and block transactions as part of financial crime response.
 *
 * Features:
 * - Circuit breaker for resilience
 * - Fallback for graceful degradation
 * - Automatic retry logic
 *
 * Compliance: BSA/AML account freeze requirements
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
@FeignClient(
    name = "wallet-service",
    url = "${services.wallet.url}",
    fallback = WalletServiceClientFallback.class
)
public interface WalletServiceClient {

    /**
     * Freeze user account
     *
     * Endpoint: POST /api/v1/wallets/{userId}/freeze
     *
     * Freezes all wallets and blocks all transactions for user.
     * Used for:
     * - Suspected money laundering
     * - Fraud investigation
     * - Regulatory compliance
     *
     * @param userId user ID to freeze
     * @param freezeRequest freeze request with reason and case ID
     * @param authToken service-to-service auth token
     * @return freeze response
     */
    @PostMapping("/api/v1/wallets/{userId}/freeze")
    @CircuitBreaker(name = "wallet-service", fallbackMethod = "freezeAccountFallback")
    Map<String, Object> freezeAccount(
        @PathVariable("userId") String userId,
        @RequestBody Map<String, Object> freezeRequest,
        @RequestHeader("Authorization") String authToken
    );

    /**
     * Unfreeze user account
     *
     * Endpoint: POST /api/v1/wallets/{userId}/unfreeze
     *
     * Unfreezes account after investigation completion.
     *
     * @param userId user ID to unfreeze
     * @param unfreezeRequest unfreeze request with reason and case ID
     * @param authToken service-to-service auth token
     * @return unfreeze response
     */
    @PostMapping("/api/v1/wallets/{userId}/unfreeze")
    @CircuitBreaker(name = "wallet-service", fallbackMethod = "unfreezeAccountFallback")
    Map<String, Object> unfreezeAccount(
        @PathVariable("userId") String userId,
        @RequestBody Map<String, Object> unfreezeRequest,
        @RequestHeader("Authorization") String authToken
    );

    /**
     * Block specific transaction
     *
     * Endpoint: POST /api/v1/transactions/{transactionId}/block
     *
     * Blocks a pending transaction from completing.
     *
     * @param transactionId transaction ID to block
     * @param blockRequest block request with reason
     * @param authToken service-to-service auth token
     * @return block response
     */
    @PostMapping("/api/v1/transactions/{transactionId}/block")
    @CircuitBreaker(name = "wallet-service", fallbackMethod = "blockTransactionFallback")
    Map<String, Object> blockTransaction(
        @PathVariable("transactionId") String transactionId,
        @RequestBody Map<String, Object> blockRequest,
        @RequestHeader("Authorization") String authToken
    );

    /**
     * Get account freeze status
     *
     * Endpoint: POST /api/v1/wallets/{userId}/freeze-status
     *
     * @param userId user ID
     * @param authToken service-to-service auth token
     * @return freeze status
     */
    @PostMapping("/api/v1/wallets/{userId}/freeze-status")
    @CircuitBreaker(name = "wallet-service", fallbackMethod = "getFreezeStatusFallback")
    Map<String, Object> getFreezeStatus(
        @PathVariable("userId") String userId,
        @RequestHeader("Authorization") String authToken
    );
}
