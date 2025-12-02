package com.waqiti.payment.client;

import com.waqiti.payment.client.dto.crypto.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Feign Client for Crypto Service Integration
 *
 * BLOCKER #3 FIX: Proper Integration with crypto-service
 *
 * This client enables payment-service to delegate crypto operations to the dedicated
 * crypto-service microservice, replacing the mock implementations that caused 100% failure.
 *
 * Architecture:
 * - payment-service (orchestration) → crypto-service (crypto operations) → Blockchain APIs
 *
 * Features:
 * - Circuit breaker pattern for resilience
 * - Automatic retry with exponential backoff
 * - Service discovery via Eureka (or direct URL)
 * - Comprehensive error handling
 *
 * Endpoints:
 * - POST /wallets - Create crypto wallet
 * - GET /wallets/{id} - Get wallet details
 * - GET /wallets - List user wallets
 * - POST /transactions/send/initiate - Initiate crypto send
 * - POST /transactions/send/confirm - Confirm with MFA
 * - GET /transactions/{id} - Get transaction details
 * - GET /transactions - List transactions
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 */
@FeignClient(
    name = "crypto-service",
    url = "${crypto-service.url:http://localhost:8087/api/v1/crypto}",
    fallback = CryptoServiceClientFallback.class
)
public interface CryptoServiceClient {

    /**
     * Create new cryptocurrency wallet for user.
     *
     * @param request Wallet creation request
     * @return Wallet response with address and details
     */
    @PostMapping("/wallets")
    @CircuitBreaker(name = "crypto-service", fallbackMethod = "createWalletFallback")
    @Retry(name = "crypto-service")
    ResponseEntity<CryptoWalletResponse> createWallet(@RequestBody CreateCryptoWalletRequest request);

    /**
     * Get wallet details by ID.
     *
     * @param walletId Wallet UUID
     * @return Wallet response
     */
    @GetMapping("/wallets/{walletId}")
    @CircuitBreaker(name = "crypto-service")
    @Retry(name = "crypto-service")
    ResponseEntity<CryptoWalletResponse> getWallet(@PathVariable("walletId") UUID walletId);

    /**
     * List all wallets for authenticated user.
     *
     * @param pageable Pagination parameters
     * @return Page of wallets
     */
    @GetMapping("/wallets")
    @CircuitBreaker(name = "crypto-service")
    @Retry(name = "crypto-service")
    ResponseEntity<Page<CryptoWalletResponse>> getUserWallets(Pageable pageable);

    /**
     * Get total balance across all user wallets.
     *
     * @return Aggregated balance response
     */
    @GetMapping("/wallets/balance")
    @CircuitBreaker(name = "crypto-service")
    @Retry(name = "crypto-service")
    ResponseEntity<CryptoBalanceResponse> getTotalBalance();

    /**
     * Generate new receiving address for wallet.
     *
     * @param walletId Wallet UUID
     * @return New address response
     */
    @PostMapping("/wallets/{walletId}/addresses")
    @CircuitBreaker(name = "crypto-service")
    @Retry(name = "crypto-service")
    ResponseEntity<CryptoAddressResponse> generateNewAddress(@PathVariable("walletId") UUID walletId);

    /**
     * Initiate cryptocurrency send transaction.
     * Returns MFA challenge if required, or immediate transaction response.
     *
     * @param request Send cryptocurrency request
     * @return Transaction initiation response (may require MFA)
     */
    @PostMapping("/transactions/send/initiate")
    @CircuitBreaker(name = "crypto-service", fallbackMethod = "initiateSendCryptoFallback")
    @Retry(name = "crypto-service")
    ResponseEntity<CryptoTransactionInitiationResponse> initiateSendCrypto(
            @RequestBody SendCryptocurrencyRequest request);

    /**
     * Confirm pending cryptocurrency transaction with MFA.
     *
     * @param transactionId Pending transaction ID
     * @param request MFA confirmation request
     * @return Completed transaction response
     */
    @PostMapping("/transactions/send/confirm")
    @CircuitBreaker(name = "crypto-service")
    @Retry(name = "crypto-service")
    ResponseEntity<CryptoTransactionResponse> confirmSendCrypto(
            @RequestParam("transactionId") UUID transactionId,
            @RequestBody MfaConfirmationRequest request);

    /**
     * Get transaction details by ID.
     *
     * @param transactionId Transaction UUID
     * @return Transaction response
     */
    @GetMapping("/transactions/{transactionId}")
    @CircuitBreaker(name = "crypto-service")
    @Retry(name = "crypto-service")
    ResponseEntity<CryptoTransactionResponse> getTransaction(
            @PathVariable("transactionId") UUID transactionId);

    /**
     * List transactions for authenticated user.
     *
     * @param pageable Pagination parameters
     * @param status Optional status filter
     * @param currency Optional currency filter
     * @return Page of transactions
     */
    @GetMapping("/transactions")
    @CircuitBreaker(name = "crypto-service")
    @Retry(name = "crypto-service")
    ResponseEntity<Page<CryptoTransactionResponse>> getTransactions(
            Pageable pageable,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String currency);

    /**
     * Buy cryptocurrency (fiat to crypto).
     *
     * @param request Buy cryptocurrency request
     * @return Transaction response
     */
    @PostMapping("/transactions/buy")
    @CircuitBreaker(name = "crypto-service")
    @Retry(name = "crypto-service")
    ResponseEntity<CryptoTransactionResponse> buyCryptocurrency(
            @RequestBody BuyCryptocurrencyRequest request);

    /**
     * Sell cryptocurrency (crypto to fiat).
     *
     * @param request Sell cryptocurrency request
     * @return Transaction response
     */
    @PostMapping("/transactions/sell")
    @CircuitBreaker(name = "crypto-service")
    @Retry(name = "crypto-service")
    ResponseEntity<CryptoTransactionResponse> sellCryptocurrency(
            @RequestBody SellCryptocurrencyRequest request);

    /**
     * Get current cryptocurrency price.
     *
     * @param currency Crypto currency code (BTC, ETH, etc.)
     * @param fiatCurrency Fiat currency code (USD, EUR, etc.)
     * @return Price response
     */
    @GetMapping("/prices/{currency}")
    @CircuitBreaker(name = "crypto-service")
    @Retry(name = "crypto-service")
    ResponseEntity<CryptoPriceResponse> getCurrentPrice(
            @PathVariable("currency") String currency,
            @RequestParam(defaultValue = "USD") String fiatCurrency);

    /**
     * Get wallet balance by currency.
     *
     * @param walletId Wallet UUID
     * @return Balance response
     */
    @GetMapping("/wallets/{walletId}/balance")
    @CircuitBreaker(name = "crypto-service")
    @Retry(name = "crypto-service")
    ResponseEntity<CryptoBalanceResponse> getWalletBalance(
            @PathVariable("walletId") UUID walletId);
}
