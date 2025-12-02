/**
 * Crypto Wallet Controller
 * REST API endpoints for cryptocurrency wallet management
 *
 * CRITICAL P0 SECURITY FIX: Removed IDOR vulnerability
 * Previous implementation accepted userId from X-User-Id header (attacker-controlled)
 * Now uses SecurityContextHolder to get authenticated user ID (secure)
 *
 * @author Waqiti Security Team - P0 Production Fix
 * @since 1.0.0 (Critical Security Enhancement)
 */
package com.waqiti.crypto.controller;

import com.waqiti.common.security.SecurityContextUtil;
import com.waqiti.crypto.dto.request.CreateWalletRequest;
import com.waqiti.crypto.dto.response.AddressResponse;
import com.waqiti.crypto.dto.response.BalanceResponse;
import com.waqiti.crypto.dto.response.WalletResponse;
import com.waqiti.crypto.service.CryptoWalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Crypto Wallets", description = "Cryptocurrency wallet management endpoints")
public class CryptoWalletController {
    
    private final CryptoWalletService walletService;
    
    @PostMapping
    @Operation(summary = "Create new wallet", description = "Creates a new cryptocurrency wallet for the user")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<WalletResponse> createWallet(@Valid @RequestBody CreateWalletRequest request) {

        // SECURITY FIX: Get user ID from SecurityContext instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();

        log.info("Creating wallet for authenticated user: {} with currency: {}", userId, request.getCurrency());
        request.setUserId(userId);

        WalletResponse wallet = walletService.createWallet(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(wallet);
    }
    
    @GetMapping("/{walletId}")
    @Operation(summary = "Get wallet details", description = "Retrieves wallet information by ID")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable UUID walletId) {

        // SECURITY FIX: Get user ID from SecurityContext instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();

        log.info("Fetching wallet: {} for authenticated user: {}", walletId, userId);
        WalletResponse wallet = walletService.getWallet(walletId, userId);
        return ResponseEntity.ok(wallet);
    }
    
    @GetMapping
    @Operation(summary = "List user wallets", description = "Retrieves all wallets for the authenticated user")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<WalletResponse>> getUserWallets(
            @Parameter(description = "Pagination parameters") Pageable pageable) {

        // SECURITY FIX: Get user ID from SecurityContext instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();

        log.info("Listing wallets for authenticated user: {}", userId);
        Page<WalletResponse> wallets = walletService.getUserWallets(userId, pageable);
        return ResponseEntity.ok(wallets);
    }
    
    @GetMapping("/balance")
    @Operation(summary = "Get total balance", description = "Retrieves aggregated balance across all user wallets")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BalanceResponse> getTotalBalance() {

        // SECURITY FIX: Get user ID from SecurityContext instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();

        log.info("Fetching total balance for authenticated user: {}", userId);
        BalanceResponse balance = walletService.getTotalBalance(userId);
        return ResponseEntity.ok(balance);
    }
    
    @PostMapping("/{walletId}/addresses")
    @Operation(summary = "Generate new address", description = "Generates a new receiving address for the wallet")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AddressResponse> generateAddress(
            @PathVariable UUID walletId,
            @RequestParam(required = false) String label) {

        // SECURITY FIX: Get user ID from SecurityContext instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();

        log.info("Generating new address for wallet: {} (authenticated user: {})", walletId, userId);
        AddressResponse address = walletService.generateNewAddress(walletId, userId, label);
        return ResponseEntity.status(HttpStatus.CREATED).body(address);
    }
    
    @GetMapping("/{walletId}/addresses")
    @Operation(summary = "List wallet addresses", description = "Retrieves all addresses for a wallet")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<AddressResponse>> getWalletAddresses(
            @PathVariable UUID walletId,
            Pageable pageable) {

        // SECURITY FIX: Get user ID from SecurityContext instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();

        log.info("Listing addresses for wallet: {} (authenticated user: {})", walletId, userId);
        Page<AddressResponse> addresses = walletService.getWalletAddresses(walletId, userId, pageable);
        return ResponseEntity.ok(addresses);
    }
    
    @PutMapping("/{walletId}/whitelist")
    @Operation(summary = "Enable/disable whitelist", description = "Toggles address whitelist for enhanced security")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<WalletResponse> toggleWhitelist(
            @PathVariable UUID walletId,
            @RequestParam boolean enabled) {

        // SECURITY FIX: Get user ID from SecurityContext instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();

        log.info("Toggling whitelist for wallet: {} to: {} (authenticated user: {})", walletId, enabled, userId);
        WalletResponse wallet = walletService.toggleWhitelist(walletId, userId, enabled);
        return ResponseEntity.ok(wallet);
    }
    
    @DeleteMapping("/{walletId}")
    @Operation(summary = "Disable wallet", description = "Disables a wallet (wallets cannot be deleted for audit purposes)")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> disableWallet(
            @PathVariable UUID walletId,
            @RequestParam String reason) {

        // SECURITY FIX: Get user ID from SecurityContext instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();

        log.info("Disabling wallet: {} for authenticated user: {}", walletId, userId);
        walletService.disableWallet(walletId, userId, reason);
        return ResponseEntity.noContent().build();
    }
}