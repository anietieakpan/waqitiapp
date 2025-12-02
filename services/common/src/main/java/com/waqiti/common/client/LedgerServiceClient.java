package com.waqiti.common.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

@Component
@Slf4j
public class LedgerServiceClient {

    private final WebClient webClient;

    public LedgerServiceClient(WebClient.Builder webClientBuilder,
                              @Value("${ledger-service.url:http://localhost:8086}") String baseUrl) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    @CircuitBreaker(name = "ledger-service", fallbackMethod = "postTransactionFallback")
    @Retry(name = "ledger-service")
    public void postTransaction(String transactionId, String fromAccountId, String toAccountId, 
                               BigDecimal amount, String currency, String description) {
        log.debug("Posting transaction to ledger: {} - {} {} from {} to {}", 
                 transactionId, amount, currency, fromAccountId, toAccountId);

        try {
            Map<String, Object> request = Map.of(
                "transactionId", transactionId,
                "fromAccountId", fromAccountId,
                "toAccountId", toAccountId,
                "amount", amount.toString(),
                "currency", currency,
                "description", description
            );

            webClient.post()
                    .uri("/api/v1/ledger/transactions")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(statusCode -> statusCode.isError(), response -> {
                        return response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Ledger service error: {} - {}", response.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException("Ledger posting failed: " + errorBody));
                                });
                    })
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            log.debug("Successfully posted transaction to ledger: {}", transactionId);

        } catch (Exception e) {
            log.error("Failed to post transaction to ledger: {}", transactionId, e);
            throw new RuntimeException("Ledger service call failed", e);
        }
    }

    @CircuitBreaker(name = "ledger-service", fallbackMethod = "getAccountBalanceFallback")
    @Retry(name = "ledger-service")
    public BigDecimal getAccountBalance(String accountId, String currency) {
        log.debug("Getting account balance from ledger: {} ({})", accountId, currency);

        try {
            String response = webClient.get()
                    .uri("/api/v1/ledger/accounts/{accountId}/balance?currency={currency}",
                         accountId, currency)
                    .retrieve()
                    .onStatus(statusCode -> statusCode.isError(), clientResponse -> {
                        if (clientResponse.statusCode().equals(HttpStatus.NOT_FOUND)) {
                            return Mono.error(new RuntimeException("Account not found: " + accountId));
                        }
                        return clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException(
                                        "Balance retrieval failed: " + errorBody)));
                    })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            // Parse balance from response
            return new BigDecimal(response);

        } catch (Exception e) {
            log.error("Failed to get account balance from ledger: {} ({})", accountId, currency, e);
            throw new RuntimeException("Ledger balance retrieval failed", e);
        }
    }

    /**
     * P1-2 CRITICAL FIX: Get wallet balance from ledger for reconciliation
     *
     * Retrieves the calculated wallet balance from ledger-service by summing all
     * ledger entries for the wallet's liability account. This is the "source of truth"
     * balance used for reconciliation against wallet-service's cached balance.
     *
     * RECONCILIATION FLOW:
     * 1. Wallet-service maintains cached balance for performance
     * 2. Ledger-service maintains authoritative double-entry ledger
     * 3. This method retrieves ledger balance for comparison
     * 4. Discrepancies trigger alerts or auto-correction
     *
     * @param walletId Wallet ID (UUID string)
     * @return Calculated wallet balance from ledger entries
     * @throws RuntimeException if ledger service is unavailable
     */
    @CircuitBreaker(name = "ledger-service", fallbackMethod = "getWalletBalanceFallback")
    @Retry(name = "ledger-service")
    public BigDecimal getWalletBalance(String walletId) {
        log.debug("Getting wallet balance from ledger for reconciliation: walletId={}", walletId);

        try {
            Map<String, Object> response = webClient.get()
                    .uri("/api/v1/ledger/wallets/{walletId}/balance", walletId)
                    .retrieve()
                    .onStatus(statusCode -> statusCode.isError(), clientResponse -> {
                        if (clientResponse.statusCode().equals(HttpStatus.NOT_FOUND)) {
                            log.warn("Wallet not found in ledger: {} - returning zero balance", walletId);
                            return Mono.error(new RuntimeException("Wallet not found in ledger: " + walletId));
                        }
                        return clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException(
                                        "Wallet balance retrieval failed: " + errorBody)));
                    })
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            // Parse balance from response
            String balanceStr = (String) response.get("balance");
            BigDecimal balance = new BigDecimal(balanceStr);

            log.debug("Retrieved wallet balance from ledger: walletId={}, balance={}", walletId, balance);
            return balance;

        } catch (WebClientResponseException.NotFound e) {
            log.warn("Wallet not found in ledger (returning zero): walletId={}", walletId);
            return BigDecimal.ZERO;

        } catch (Exception e) {
            log.error("Failed to get wallet balance from ledger: walletId={}", walletId, e);
            throw new RuntimeException("Ledger wallet balance retrieval failed: " + walletId, e);
        }
    }

    @CircuitBreaker(name = "ledger-service", fallbackMethod = "reserveFundsFallback")
    @Retry(name = "ledger-service")
    public void reserveFunds(String accountId, String currency, BigDecimal amount, String reservationId) {
        log.debug("Reserving funds in ledger: {} {} for account {} (reservation: {})", 
                 amount, currency, accountId, reservationId);

        try {
            Map<String, Object> request = Map.of(
                "accountId", accountId,
                "currency", currency,
                "amount", amount.toString(),
                "reservationId", reservationId
            );

            webClient.post()
                    .uri("/api/v1/ledger/accounts/{accountId}/reserve", accountId)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(statusCode -> statusCode.isError(), response -> {
                        return response.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException(
                                        "Fund reservation failed: " + errorBody)));
                    })
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            log.debug("Successfully reserved funds: {} {} for account {}", amount, currency, accountId);

        } catch (Exception e) {
            log.error("Failed to reserve funds: {} {} for account {}", amount, currency, accountId, e);
            throw new RuntimeException("Fund reservation failed", e);
        }
    }

    @CircuitBreaker(name = "ledger-service", fallbackMethod = "releaseFundsFallback")
    @Retry(name = "ledger-service")
    public void releaseFunds(String accountId, String reservationId) {
        log.debug("Releasing funds in ledger: account {} (reservation: {})", accountId, reservationId);

        try {
            webClient.delete()
                    .uri("/api/v1/ledger/accounts/{accountId}/reserve/{reservationId}",
                         accountId, reservationId)
                    .retrieve()
                    .onStatus(statusCode -> statusCode.isError(), response -> {
                        return response.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException(
                                        "Fund release failed: " + errorBody)));
                    })
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            log.debug("Successfully released funds for account: {} (reservation: {})", accountId, reservationId);

        } catch (Exception e) {
            log.error("Failed to release funds for account: {} (reservation: {})", accountId, reservationId, e);
            throw new RuntimeException("Fund release failed", e);
        }
    }

    /**
     * Reverse/rollback a ledger transaction
     *
     * CRITICAL OPERATION:
     * - Creates compensating ledger entries to reverse original transaction
     * - Maintains double-entry bookkeeping integrity
     * - Updates account balances with reversal
     * - Generates audit trail for reversal
     * - Used for refund cancellations, failed transactions, and rollbacks
     *
     * FAILURE IMPACT:
     * - Accounting inconsistencies between systems
     * - Balance discrepancies requiring manual reconciliation
     * - Audit trail gaps for regulatory compliance
     * - Financial reporting inaccuracies
     *
     * @param ledgerEntryId The original ledger entry ID to reverse
     * @param reason Business reason for reversal (audit trail)
     * @param reversalTransactionId Unique ID for the reversal transaction
     * @return Reversal ledger entry ID
     * @throws RuntimeException if reversal fails
     */
    @CircuitBreaker(name = "ledger-service", fallbackMethod = "reverseTransactionFallback")
    @Retry(name = "ledger-service")
    public String reverseTransaction(String ledgerEntryId, String reason, String reversalTransactionId) {
        log.info("Reversing ledger transaction: entry={}, reversalId={}, reason={}",
                ledgerEntryId, reversalTransactionId, reason);

        try {
            Map<String, Object> request = Map.of(
                "ledgerEntryId", ledgerEntryId,
                "reversalTransactionId", reversalTransactionId,
                "reason", reason,
                "reversalTimestamp", System.currentTimeMillis()
            );

            Map<String, Object> response = webClient.post()
                    .uri("/api/v1/ledger/transactions/{ledgerEntryId}/reverse", ledgerEntryId)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(statusCode -> statusCode.isError(), clientResponse -> {
                        return clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Ledger reversal error: {} - {}", clientResponse.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException("Ledger reversal failed: " + errorBody));
                                });
                    })
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(45))
                    .block();

            String reversalEntryId = (String) response.get("reversalEntryId");

            log.info("Successfully reversed ledger transaction: originalEntry={}, reversalEntry={}",
                    ledgerEntryId, reversalEntryId);

            return reversalEntryId;

        } catch (Exception e) {
            log.error("Failed to reverse ledger transaction: entry={}, reversalId={}",
                    ledgerEntryId, reversalTransactionId, e);
            throw new RuntimeException("Ledger reversal failed for entry: " + ledgerEntryId, e);
        }
    }

    /**
     * Batch reverse multiple ledger transactions
     *
     * Used for complex rollback scenarios involving multiple related transactions
     * Ensures atomicity - either all reversals succeed or all fail
     *
     * @param ledgerEntryIds List of ledger entry IDs to reverse
     * @param reason Business reason for batch reversal
     * @param batchReversalId Unique ID for the batch reversal operation
     * @return Map of original entry ID to reversal entry ID
     * @throws RuntimeException if batch reversal fails
     */
    @CircuitBreaker(name = "ledger-service", fallbackMethod = "batchReverseTransactionsFallback")
    @Retry(name = "ledger-service")
    public Map<String, String> batchReverseTransactions(
            java.util.List<String> ledgerEntryIds,
            String reason,
            String batchReversalId) {

        log.info("Batch reversing {} ledger transactions: batchId={}, reason={}",
                ledgerEntryIds.size(), batchReversalId, reason);

        try {
            Map<String, Object> request = Map.of(
                "ledgerEntryIds", ledgerEntryIds,
                "batchReversalId", batchReversalId,
                "reason", reason,
                "reversalTimestamp", System.currentTimeMillis()
            );

            Map<String, String> response = webClient.post()
                    .uri("/api/v1/ledger/transactions/batch-reverse")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(statusCode -> statusCode.isError(), clientResponse -> {
                        return clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Batch ledger reversal error: {} - {}",
                                            clientResponse.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException(
                                            "Batch ledger reversal failed: " + errorBody));
                                });
                    })
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(90))
                    .block();

            log.info("Successfully batch reversed {} ledger transactions", ledgerEntryIds.size());

            return response;

        } catch (Exception e) {
            log.error("Failed to batch reverse ledger transactions: batchId={}", batchReversalId, e);
            throw new RuntimeException("Batch ledger reversal failed: " + batchReversalId, e);
        }
    }

    // Fallback methods

    private void postTransactionFallback(String transactionId, String fromAccountId, String toAccountId,
                                        BigDecimal amount, String currency, String description, Exception ex) {
        log.error("Ledger service unavailable - transaction posting failed: {} (fallback executed)", 
                 transactionId, ex);
        throw new RuntimeException("Ledger service unavailable", ex);
    }

    private BigDecimal getAccountBalanceFallback(String accountId, String currency, Exception ex) {
        log.error("Ledger service unavailable - balance retrieval failed: {} ({}) (fallback executed)",
                 accountId, currency, ex);
        throw new RuntimeException("Ledger service unavailable", ex);
    }

    /**
     * P1-2 CRITICAL FIX: Fallback for wallet balance retrieval during reconciliation
     *
     * When ledger-service is unavailable, reconciliation cannot proceed safely.
     * This fallback throws an exception to prevent incorrect reconciliation decisions.
     *
     * IMPORTANT: Do NOT return cached/default values as this could mask real discrepancies.
     *
     * @param walletId Wallet ID
     * @param ex Original exception from circuit breaker
     * @return Never returns - always throws
     * @throws RuntimeException Always thrown to signal reconciliation failure
     */
    private BigDecimal getWalletBalanceFallback(String walletId, Exception ex) {
        log.error("Ledger service unavailable - wallet balance retrieval failed: walletId={} (fallback executed)",
                 walletId, ex);
        // Do NOT return zero or cached value - this would cause incorrect reconciliation
        throw new RuntimeException("Ledger service unavailable - cannot perform reconciliation for wallet: " + walletId, ex);
    }

    private void reserveFundsFallback(String accountId, String currency, BigDecimal amount, 
                                     String reservationId, Exception ex) {
        log.error("Ledger service unavailable - fund reservation failed: {} {} for {} (fallback executed)", 
                 amount, currency, accountId, ex);
        throw new RuntimeException("Ledger service unavailable", ex);
    }

    private void releaseFundsFallback(String accountId, String reservationId, Exception ex) {
        log.error("Ledger service unavailable - fund release failed: {} (reservation: {}) (fallback executed)",
                 accountId, reservationId, ex);
        throw new RuntimeException("Ledger service unavailable", ex);
    }

    private String reverseTransactionFallback(String ledgerEntryId, String reason,
                                             String reversalTransactionId, Exception ex) {
        log.error("Ledger service unavailable - transaction reversal failed: entry={}, reversalId={} (fallback executed)",
                 ledgerEntryId, reversalTransactionId, ex);
        // CRITICAL: This needs manual reconciliation
        // Log to DLQ or alert for manual intervention
        throw new RuntimeException("Ledger service unavailable - transaction reversal requires manual intervention: "
                + ledgerEntryId, ex);
    }

    private Map<String, String> batchReverseTransactionsFallback(
            java.util.List<String> ledgerEntryIds,
            String reason,
            String batchReversalId,
            Exception ex) {
        log.error("Ledger service unavailable - batch reversal failed: batchId={}, entryCount={} (fallback executed)",
                 batchReversalId, ledgerEntryIds.size(), ex);
        // CRITICAL: This needs manual reconciliation for all entries
        // Log to DLQ or alert for manual intervention
        throw new RuntimeException("Ledger service unavailable - batch reversal requires manual intervention: "
                + batchReversalId, ex);
    }
}