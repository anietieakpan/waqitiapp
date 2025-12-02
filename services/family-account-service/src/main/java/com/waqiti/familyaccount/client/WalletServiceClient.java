package com.waqiti.familyaccount.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Wallet Service Feign Client
 *
 * Feign client for interacting with the wallet-service microservice.
 * Provides methods for wallet creation, balance inquiries, fund transfers,
 * and wallet management operations critical for family account functionality.
 *
 * Circuit Breaker Configuration:
 * - Name: wallet-service
 * - Failure Rate Threshold: 60%
 * - Sliding Window Size: 10 requests
 * - Wait Duration in Open State: 15 seconds
 *
 * Retry Configuration:
 * - Max Attempts: 3
 * - Wait Duration: 2 seconds
 * - Exponential Backoff Multiplier: 2
 *
 * Timeout Configuration:
 * - Connect Timeout: 5 seconds
 * - Read Timeout: 5 seconds
 *
 * @author Waqiti Family Account Team
 * @version 1.0.0
 * @since 2025-11-19
 */
@FeignClient(
    name = "wallet-service",
    url = "${feign.client.config.wallet-service.url}",
    configuration = WalletServiceClientConfig.class
)
public interface WalletServiceClient {

    /**
     * Create a family wallet
     *
     * Creates a shared wallet for the family account. This wallet is owned by
     * the primary parent and used for family-wide transactions, allowance payments,
     * and shared expenses.
     *
     * @param familyId The unique identifier of the family account
     * @param ownerId The user ID of the primary parent (wallet owner)
     * @return The unique wallet ID of the created family wallet
     * @throws feign.FeignException if the service call fails
     */
    @PostMapping("/api/v1/wallets/family")
    String createFamilyWallet(
        @RequestParam("familyId") String familyId,
        @RequestParam("ownerId") String ownerId
    );

    /**
     * Create an individual member wallet
     *
     * Creates a personal wallet for a family member (child, teen, or young adult).
     * This wallet receives allowance payments and is used for the member's
     * personal transactions under parental supervision.
     *
     * @param familyId The unique identifier of the family account
     * @param userId The user ID of the family member
     * @return The unique wallet ID of the created individual wallet
     * @throws feign.FeignException if the service call fails
     */
    @PostMapping("/api/v1/wallets/individual")
    String createIndividualWallet(
        @RequestParam("familyId") String familyId,
        @RequestParam("userId") String userId
    );

    /**
     * Get wallet balance
     *
     * Retrieves the current available balance of a wallet.
     * Used for:
     * - Transaction authorization validation
     * - Allowance payment verification
     * - Balance display in family dashboard
     * - Spending limit calculations
     *
     * @param walletId The unique identifier of the wallet
     * @return The current wallet balance with full precision (2 decimal places)
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/wallets/{walletId}/balance")
    BigDecimal getWalletBalance(@PathVariable("walletId") String walletId);

    /**
     * Transfer funds between wallets
     *
     * Performs a fund transfer between two wallets with full transaction tracking.
     * Used for:
     * - Allowance payments (family wallet → member wallet)
     * - Savings transfers
     * - Chore reward payments
     * - Family member reimbursements
     *
     * The transfer is atomic and idempotent with proper rollback support.
     *
     * @param fromWalletId Source wallet ID (funds debited from here)
     * @param toWalletId Destination wallet ID (funds credited here)
     * @param amount Transfer amount (must be positive, precision 2 decimal places)
     * @param description Transaction description for audit trail
     * @return true if transfer succeeded, false otherwise
     * @throws feign.FeignException if the service call fails
     */
    @PostMapping("/api/v1/wallets/transfer")
    Boolean transferFunds(
        @RequestParam("fromWalletId") String fromWalletId,
        @RequestParam("toWalletId") String toWalletId,
        @RequestParam("amount") BigDecimal amount,
        @RequestParam("description") String description
    );

    /**
     * Freeze a wallet
     *
     * Temporarily freezes a wallet, preventing all outgoing transactions.
     * Used when:
     * - A family member is suspended by parents
     * - Suspicious activity is detected
     * - Account security measures are triggered
     * - Parental controls require temporary spending restriction
     *
     * The wallet can still receive funds but cannot initiate transactions.
     *
     * @param walletId The unique identifier of the wallet to freeze
     * @param reason The reason for freezing (logged for audit purposes)
     * @return true if wallet was frozen successfully, false otherwise
     * @throws feign.FeignException if the service call fails
     */
    @PostMapping("/api/v1/wallets/{walletId}/freeze")
    Boolean freezeWallet(
        @PathVariable("walletId") String walletId,
        @RequestParam("reason") String reason
    );

    /**
     * Unfreeze a wallet
     *
     * Removes the freeze status from a wallet, restoring normal transaction capabilities.
     * Used when:
     * - Parent removes suspension from family member
     * - Security hold is cleared
     * - Temporary restriction period expires
     *
     * @param walletId The unique identifier of the wallet to unfreeze
     * @return true if wallet was unfrozen successfully, false otherwise
     * @throws feign.FeignException if the service call fails
     */
    @PostMapping("/api/v1/wallets/{walletId}/unfreeze")
    Boolean unfreezeWallet(@PathVariable("walletId") String walletId);

    /**
     * Get wallet details
     *
     * Retrieves comprehensive wallet information including:
     * - Wallet ID and type (FAMILY, INDIVIDUAL, SAVINGS)
     * - Owner user ID
     * - Current balance
     * - Wallet status (ACTIVE, FROZEN, CLOSED)
     * - Currency code
     * - Creation date
     * - Last transaction date
     *
     * @param walletId The unique identifier of the wallet
     * @return Map containing wallet details
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/wallets/{walletId}/details")
    Map<String, Object> getWalletDetails(@PathVariable("walletId") String walletId);

    /**
     * Check if wallet is frozen
     *
     * Quick check to determine if a wallet is currently frozen.
     * Used for transaction authorization pre-checks.
     *
     * @param walletId The unique identifier of the wallet
     * @return true if wallet is frozen, false if active
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/wallets/{walletId}/is-frozen")
    Boolean isWalletFrozen(@PathVariable("walletId") String walletId);

    /**
     * Reserve funds for pending transaction
     *
     * Places a hold on funds for a transaction that requires parent approval.
     * The funds are reserved but not yet transferred, ensuring they remain
     * available when the approval is granted.
     *
     * @param walletId The unique identifier of the wallet
     * @param amount The amount to reserve
     * @param transactionId The transaction ID for the reservation
     * @return Reservation ID if successful
     * @throws feign.FeignException if the service call fails
     */
    @PostMapping("/api/v1/wallets/{walletId}/reserve")
    String reserveFunds(
        @PathVariable("walletId") String walletId,
        @RequestParam("amount") BigDecimal amount,
        @RequestParam("transactionId") String transactionId
    );

    /**
     * Release reserved funds
     *
     * Releases a fund reservation when a transaction is cancelled or declined.
     *
     * @param walletId The unique identifier of the wallet
     * @param reservationId The reservation ID to release
     * @return true if reservation was released successfully
     * @throws feign.FeignException if the service call fails
     */
    @PostMapping("/api/v1/wallets/{walletId}/release-reservation")
    Boolean releaseReservation(
        @PathVariable("walletId") String walletId,
        @RequestParam("reservationId") String reservationId
    );

    /**
     * Capture reserved funds
     *
     * Captures (executes) a previously reserved fund amount when a transaction
     * is approved by the parent.
     *
     * @param walletId The unique identifier of the wallet
     * @param reservationId The reservation ID to capture
     * @return true if funds were captured successfully
     * @throws feign.FeignException if the service call fails
     */
    @PostMapping("/api/v1/wallets/{walletId}/capture-reservation")
    Boolean captureReservation(
        @PathVariable("walletId") String walletId,
        @RequestParam("reservationId") String reservationId
    );

    /**
     * Get wallet transaction history
     *
     * Retrieves paginated transaction history for a wallet.
     *
     * @param walletId The unique identifier of the wallet
     * @param page Page number (0-indexed)
     * @param size Number of transactions per page
     * @return Map containing transaction list and pagination info
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/wallets/{walletId}/transactions")
    Map<String, Object> getWalletTransactions(
        @PathVariable("walletId") String walletId,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size
    );

    /**
     * Check wallet exists
     *
     * Verifies if a wallet exists in the system.
     *
     * @param walletId The unique identifier of the wallet
     * @return true if wallet exists, false otherwise
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/wallets/{walletId}/exists")
    Boolean walletExists(@PathVariable("walletId") String walletId);

    /**
     * Set wallet spending limit
     *
     * Sets a spending limit on a wallet (used for additional family-level controls).
     *
     * @param walletId The unique identifier of the wallet
     * @param limitType The type of limit (DAILY, WEEKLY, MONTHLY)
     * @param amount The limit amount
     * @return true if limit was set successfully
     * @throws feign.FeignException if the service call fails
     */
    @PostMapping("/api/v1/wallets/{walletId}/set-limit")
    Boolean setWalletLimit(
        @PathVariable("walletId") String walletId,
        @RequestParam("limitType") String limitType,
        @RequestParam("amount") BigDecimal amount
    );
}
