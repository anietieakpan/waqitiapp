package com.waqiti.payment.client;

import com.waqiti.payment.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

/**
 * Feign client for Balance Service integration
 * 
 * IMPORTANT: This client has been redirected to wallet-service
 * as balance-service does not exist as a separate microservice.
 * All balance operations are handled by wallet-service.
 * 
 * Migration Note: balance-service was originally designed as a separate
 * service but was consolidated into wallet-service for better cohesion
 * and to avoid unnecessary service fragmentation.
 * 
 * @author Waqiti Infrastructure Team
 * @version 2.0.0 - Redirected to wallet-service
 * @since 2025-09-27
 */
@FeignClient(
    name = "wallet-service",
    url = "${services.wallet-service.url:http://wallet-service:8082}",
    fallback = BalanceServiceClientFallback.class,
    configuration = FeignConfiguration.class
)
public interface BalanceServiceClient {

    /**
     * Get customer balance
     */
    @GetMapping("/api/v1/wallets/{customerId}/balance")
    ResponseEntity<BalanceInfo> getBalance(
            @PathVariable String customerId, 
            @RequestParam(defaultValue = "USD") String currency);

    /**
     * Get all customer balances
     */
    @GetMapping("/api/v1/wallets/{customerId}/balances")
    ResponseEntity<List<BalanceInfo>> getAllBalances(@PathVariable String customerId);

    /**
     * Reserve balance for transaction
     */
    @PostMapping("/api/v1/wallets/reserve")
    ResponseEntity<BalanceReservationResult> reserveBalance(@Valid @RequestBody BalanceReservationRequest request);

    /**
     * Release balance reservation
     */
    @PostMapping("/api/v1/wallets/release/{reservationId}")
    ResponseEntity<Void> releaseReservation(@PathVariable String reservationId);

    /**
     * Confirm balance reservation (commit)
     */
    @PostMapping("/api/v1/wallets/confirm/{reservationId}")
    ResponseEntity<Void> confirmReservation(@PathVariable String reservationId);

    /**
     * Debit customer account
     */
    @PostMapping("/api/v1/wallets/{walletId}/debit")
    ResponseEntity<BalanceTransactionResult> debitAccount(
            @PathVariable String walletId,
            @Valid @RequestBody BalanceDebitRequest request);

    /**
     * Credit customer account
     */
    @PostMapping("/api/v1/wallets/{walletId}/credit")
    ResponseEntity<BalanceTransactionResult> creditAccount(
            @PathVariable String walletId,
            @Valid @RequestBody AccountCreditRequest request);

    /**
     * Transfer between accounts
     */
    @PostMapping("/api/v1/wallets/transfer")
    ResponseEntity<BalanceTransferResult> transferFunds(@Valid @RequestBody BalanceTransferRequest request);

    /**
     * Check if sufficient balance exists
     */
    @PostMapping("/api/v1/wallets/check-sufficient")
    ResponseEntity<BalanceSufficientResult> checkSufficientBalance(@Valid @RequestBody BalanceSufficientRequest request);

    /**
     * Get balance history
     */
    @GetMapping("/api/v1/wallets/{customerId}/history")
    ResponseEntity<List<BalanceTransaction>> getBalanceHistory(
            @PathVariable String customerId,
            @RequestParam(defaultValue = "USD") String currency,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size);

    /**
     * Get pending reservations
     */
    @GetMapping("/api/v1/wallets/{customerId}/reservations")
    ResponseEntity<List<BalanceReservation>> getPendingReservations(@PathVariable String customerId);

    /**
     * Get balance summary
     */
    @GetMapping("/api/v1/wallets/{customerId}/summary")
    ResponseEntity<BalanceSummary> getBalanceSummary(@PathVariable String customerId);

    /**
     * Freeze account balance
     */
    @PostMapping("/api/v1/wallets/{customerId}/freeze")
    ResponseEntity<Void> freezeBalance(@PathVariable String customerId, @Valid @RequestBody BalanceFreezeRequest request);

    /**
     * Unfreeze account balance
     */
    @PostMapping("/api/v1/wallets/{customerId}/unfreeze")
    ResponseEntity<Void> unfreezeBalance(@PathVariable String customerId, @Valid @RequestBody BalanceUnfreezeRequest request);

    /**
     * Get frozen balance amount
     */
    @GetMapping("/api/v1/wallets/{customerId}/frozen")
    ResponseEntity<BigDecimal> getFrozenAmount(
            @PathVariable String customerId, 
            @RequestParam(defaultValue = "USD") String currency);

    /**
     * Validate account for transactions
     */
    @PostMapping("/api/v1/wallets/validate")
    ResponseEntity<BalanceValidationResult> validateAccount(@Valid @RequestBody BalanceValidationRequest request);
}