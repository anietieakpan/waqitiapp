package com.waqiti.compliance.feign;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.Map;

/**
 * Transaction Service Client
 *
 * FeignClient for transaction-service integration to retrieve
 * transaction data for compliance analysis.
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
@FeignClient(
    name = "transaction-service",
    url = "${services.transaction.url}",
    fallback = TransactionServiceClientFallback.class
)
public interface TransactionServiceClient {

    /**
     * Get transactions by user ID
     *
     * @param userId user ID
     * @param authToken service-to-service auth token
     * @return list of transactions
     */
    @GetMapping("/api/v1/transactions/user/{userId}")
    @CircuitBreaker(name = "transaction-service")
    List<Map<String, Object>> getTransactionsByUser(
        @PathVariable("userId") String userId,
        @RequestHeader("Authorization") String authToken
    );

    /**
     * Get transaction by ID
     *
     * @param transactionId transaction ID
     * @param authToken service-to-service auth token
     * @return transaction details
     */
    @GetMapping("/api/v1/transactions/{transactionId}")
    @CircuitBreaker(name = "transaction-service")
    Map<String, Object> getTransaction(
        @PathVariable("transactionId") String transactionId,
        @RequestHeader("Authorization") String authToken
    );

    /**
     * Get suspicious transactions for review
     *
     * @param authToken service-to-service auth token
     * @return list of suspicious transactions
     */
    @GetMapping("/api/v1/transactions/suspicious")
    @CircuitBreaker(name = "transaction-service")
    List<Map<String, Object>> getSuspiciousTransactions(
        @RequestHeader("Authorization") String authToken
    );
}
