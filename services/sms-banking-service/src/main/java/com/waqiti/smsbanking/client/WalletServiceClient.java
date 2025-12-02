package com.waqiti.smsbanking.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Feign Client for Wallet Service
 *
 * Handles communication with the wallet-service microservice for
 * balance inquiries, transactions, and account operations.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 */
@FeignClient(
    name = "wallet-service",
    url = "${services.wallet-service.url:http://wallet-service:8080}"
)
public interface WalletServiceClient {

    /**
     * Get wallet balance
     *
     * @param userId User ID
     * @return Balance information
     */
    @GetMapping("/api/v1/wallets/{userId}/balance")
    BalanceDTO getBalance(@PathVariable("userId") String userId);

    /**
     * Get mini statement (last 5 transactions)
     *
     * @param userId User ID
     * @return List of recent transactions
     */
    @GetMapping("/api/v1/wallets/{userId}/mini-statement")
    List<TransactionDTO> getMiniStatement(@PathVariable("userId") String userId);

    /**
     * Perform fund transfer
     *
     * @param request Transfer request
     * @return Transfer result
     */
    @PostMapping("/api/v1/wallets/transfer")
    TransferResult transfer(@RequestBody TransferRequest request);

    /**
     * Balance Data Transfer Object
     */
    record BalanceDTO(
        String userId,
        BigDecimal availableBalance,
        BigDecimal ledgerBalance,
        String currency,
        LocalDateTime asOf
    ) {}

    /**
     * Transaction Data Transfer Object
     */
    record TransactionDTO(
        String transactionId,
        String type,
        BigDecimal amount,
        String description,
        LocalDateTime timestamp,
        BigDecimal balanceAfter
    ) {}

    /**
     * Transfer Request
     */
    record TransferRequest(
        String fromUserId,
        String toAccountNumber,
        BigDecimal amount,
        String description,
        String channel,
        String correlationId
    ) {}

    /**
     * Transfer Result
     */
    record TransferResult(
        boolean success,
        String transactionId,
        String message,
        BigDecimal newBalance
    ) {}
}
