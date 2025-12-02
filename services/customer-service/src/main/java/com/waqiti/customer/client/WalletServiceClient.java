package com.waqiti.customer.client;

import com.waqiti.customer.client.dto.WalletBalanceResponse;
import com.waqiti.customer.client.dto.WalletResponse;
import com.waqiti.customer.client.fallback.WalletServiceClientFallback;
import com.waqiti.customer.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

/**
 * Feign client for inter-service communication with wallet-service.
 * Provides methods to retrieve wallet information and manage wallet status.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@FeignClient(
    name = "wallet-service",
    configuration = FeignClientConfig.class,
    fallback = WalletServiceClientFallback.class
)
public interface WalletServiceClient {

    /**
     * Retrieves wallet information by wallet ID.
     *
     * @param walletId The unique wallet identifier
     * @return Wallet details
     */
    @GetMapping("/api/v1/wallets/{walletId}")
    WalletResponse getWallet(@PathVariable("walletId") String walletId);

    /**
     * Retrieves all wallets associated with a customer.
     *
     * @param customerId The unique customer identifier
     * @return List of wallets owned by the customer
     */
    @GetMapping("/api/v1/wallets/customer/{customerId}")
    List<WalletResponse> getWalletsByCustomerId(@PathVariable("customerId") String customerId);

    /**
     * Retrieves wallet balance by wallet ID.
     *
     * @param walletId The unique wallet identifier
     * @return Balance details
     */
    @GetMapping("/api/v1/wallets/{walletId}/balance")
    WalletBalanceResponse getBalance(@PathVariable("walletId") String walletId);

    /**
     * Freezes a wallet, preventing transactions.
     *
     * @param walletId The unique wallet identifier
     */
    @PostMapping("/api/v1/wallets/{walletId}/freeze")
    void freezeWallet(@PathVariable("walletId") String walletId);
}
