package com.waqiti.wallet.controller;

import com.waqiti.common.security.SecurityContextUtil;
import com.waqiti.common.validation.FinancialValidator;
import com.waqiti.wallet.dto.*;
import com.waqiti.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Secure Wallet Controller with comprehensive authorization.
 *
 * SECURITY FEATURES:
 * - @PreAuthorize on all endpoints
 * - User ID validation (IDOR prevention)
 * - Role-based access control
 * - Balance access restrictions
 * - Audit logging
 *
 * CRITICAL OPERATIONS:
 * - Transfer: User can only transfer from own wallet
 * - Balance: User can only view own balance
 * - History: User can only view own transaction history
 * - Freeze: ADMIN only
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Wallets", description = "Secure wallet operations")
@SecurityRequirement(name = "bearer-jwt")
public class SecureWalletController {

    private final WalletService walletService;
    private final FinancialValidator financialValidator;

    /**
     * Get wallet balance.
     *
     * SECURITY:
     * - User can only view own wallet balance
     * - ADMIN can view any balance
     */
    @GetMapping("/{walletId}/balance")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Get balance", description = "Get wallet balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable UUID walletId) {

        log.debug("Get balance request: walletId={}", walletId);

        // Get wallet to verify ownership
        WalletResponse wallet = walletService.getWallet(walletId);

        // CRITICAL: Verify user owns this wallet
        UUID authenticatedUserId = SecurityContextUtil.getCurrentUserId();
        boolean isAdmin = SecurityContextUtil.hasRole("ADMIN");

        if (!isAdmin && !wallet.getCustomerId().equals(authenticatedUserId)) {
            log.warn("🚨 SECURITY: User {} tried to view balance for wallet {} owned by {}",
                    authenticatedUserId, walletId, wallet.getCustomerId());
            throw new AccessDeniedException("Cannot view balance for another user's wallet");
        }

        BalanceResponse balance = walletService.getBalance(walletId);

        return ResponseEntity.ok(balance);
    }

    /**
     * Transfer funds between wallets.
     *
     * SECURITY:
     * - User can only transfer from own wallet
     * - Validates sufficient funds
     * - Fraud detection applied
     */
    @PostMapping("/transfer")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Transfer funds", description = "Transfer funds between wallets")
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {

        log.info("Transfer request: from={}, to={}, amount={}",
                request.getFromWalletId(), request.getToWalletId(), request.getAmount());

        // Get source wallet
        WalletResponse fromWallet = walletService.getWallet(request.getFromWalletId());

        // CRITICAL: Verify user owns source wallet
        UUID authenticatedUserId = SecurityContextUtil.getCurrentUserId();
        if (!fromWallet.getCustomerId().equals(authenticatedUserId)) {
            log.warn("🚨 SECURITY: User {} tried to transfer from wallet {} owned by {}",
                    authenticatedUserId, request.getFromWalletId(), fromWallet.getCustomerId());
            throw new AccessDeniedException("Cannot transfer from another user's wallet");
        }

        // Validate amount
        financialValidator.validateAmount(request.getAmount(), "transferAmount");
        financialValidator.validateCurrency(request.getCurrency());

        // Process transfer
        TransferResponse response = walletService.transfer(request);

        log.info("✅ Transfer completed: transferId={}", response.getTransferId());

        return ResponseEntity.ok(response);
    }

    /**
     * Get wallet transaction history.
     *
     * SECURITY:
     * - User can only view own wallet transactions
     */
    @GetMapping("/{walletId}/transactions")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Get transactions", description = "Get wallet transaction history")
    public ResponseEntity<Page<WalletTransactionResponse>> getTransactions(
            @PathVariable UUID walletId,
            Pageable pageable) {

        log.debug("Get transactions: walletId={}", walletId);

        // Get wallet to verify ownership
        WalletResponse wallet = walletService.getWallet(walletId);

        // CRITICAL: Verify ownership
        UUID authenticatedUserId = SecurityContextUtil.getCurrentUserId();
        boolean isAdmin = SecurityContextUtil.hasRole("ADMIN");

        if (!isAdmin && !wallet.getCustomerId().equals(authenticatedUserId)) {
            log.warn("🚨 SECURITY: User {} tried to view transactions for wallet {} owned by {}",
                    authenticatedUserId, walletId, wallet.getCustomerId());
            throw new AccessDeniedException("Cannot view transactions for another user's wallet");
        }

        Page<WalletTransactionResponse> transactions =
            walletService.getTransactions(walletId, pageable);

        return ResponseEntity.ok(transactions);
    }

    /**
     * Freeze wallet (ADMIN only).
     *
     * SECURITY:
     * - ADMIN only
     * - Creates audit trail
     * - Notifies user
     */
    @PostMapping("/{walletId}/freeze")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Freeze wallet", description = "Freeze wallet (ADMIN only)")
    public ResponseEntity<WalletResponse> freezeWallet(
            @PathVariable UUID walletId,
            @Valid @RequestBody FreezeRequest request) {

        log.warn("Freeze wallet request: walletId={}, reason={}, admin={}",
                walletId, request.getReason(), SecurityContextUtil.getCurrentUserId());

        WalletResponse wallet = walletService.freezeWallet(walletId, request);

        log.warn("⚠️ Wallet frozen: walletId={}, reason={}", walletId, request.getReason());

        return ResponseEntity.ok(wallet);
    }

    /**
     * Unfreeze wallet (ADMIN only).
     */
    @PostMapping("/{walletId}/unfreeze")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Unfreeze wallet", description = "Unfreeze wallet (ADMIN only)")
    public ResponseEntity<WalletResponse> unfreezeWallet(@PathVariable UUID walletId) {

        log.info("Unfreeze wallet: walletId={}, admin={}",
                walletId, SecurityContextUtil.getCurrentUserId());

        WalletResponse wallet = walletService.unfreezeWallet(walletId);

        log.info("✅ Wallet unfrozen: walletId={}", walletId);

        return ResponseEntity.ok(wallet);
    }

    /**
     * Get user's wallets.
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Get user wallets", description = "Get all wallets for a user")
    public ResponseEntity<Page<WalletResponse>> getUserWallets(
            @PathVariable UUID userId,
            Pageable pageable) {

        log.debug("Get user wallets: userId={}", userId);

        // CRITICAL: Verify user can only access their own wallets
        UUID authenticatedUserId = SecurityContextUtil.getCurrentUserId();
        boolean isAdmin = SecurityContextUtil.hasRole("ADMIN");

        if (!isAdmin && !userId.equals(authenticatedUserId)) {
            log.warn("🚨 SECURITY: User {} tried to view wallets for user {}",
                    authenticatedUserId, userId);
            throw new AccessDeniedException("Cannot view wallets for another user");
        }

        Page<WalletResponse> wallets = walletService.getUserWallets(userId, pageable);

        return ResponseEntity.ok(wallets);
    }
}
