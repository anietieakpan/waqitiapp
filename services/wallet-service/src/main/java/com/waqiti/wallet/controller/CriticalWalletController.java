package com.waqiti.wallet.controller;

import com.waqiti.common.api.ApiResponse;
import com.waqiti.common.ratelimit.RateLimit;
import com.waqiti.common.ratelimit.RateLimit.KeyType;
import com.waqiti.common.ratelimit.RateLimit.Priority;
import com.waqiti.wallet.dto.*;
import com.waqiti.wallet.service.WalletService;
import com.waqiti.wallet.security.SessionSecurityService;
import com.waqiti.common.security.SecurityContextUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * CRITICAL WALLET OPERATIONS CONTROLLER
 * 
 * Handles high-risk wallet operations with strict rate limiting for security:
 * - Wallet transfers: 20 requests/minute per user
 * - Withdrawals: 10 requests/minute per user  
 * - Balance checks: 60 requests/minute per user
 * - Wallet freeze (admin): 5 requests/minute per admin
 * 
 * All limits are enforced to prevent abuse and ensure system stability.
 */
@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Critical Wallet Operations", description = "High-security wallet operations with strict rate limiting")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('USER')")
public class CriticalWalletController {

    private final WalletService walletService;
    private final SessionSecurityService sessionSecurityService;

    /**
     * CRITICAL ENDPOINT: Wallet Transfer
     * Rate Limited: 20 requests/minute per user
     * Priority: HIGH
     */
    @PostMapping("/transfer")
    @RateLimit(
        requests = 20,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.HIGH,
        burstCapacity = 5,
        blockDuration = 5,
        blockUnit = TimeUnit.MINUTES,
        alertThreshold = 0.75,
        description = "Wallet transfer endpoint - high priority security",
        errorMessage = "Transfer rate limit exceeded. Maximum 20 transfers per minute allowed for security."
    )
    @Operation(
        summary = "Transfer funds between wallets",
        description = "Transfers funds from one wallet to another. Limited to 20 transfers per minute per user for security."
    )
    @PreAuthorize("@walletOwnershipValidator.isWalletOwner(authentication.name, #request.fromWalletId) " +
                  "and @transactionLimitValidator.isWithinTransferLimits(#request.amount)")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader(value = "X-Device-ID", required = false) String deviceId) {
        
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        
        log.info("CRITICAL_WALLET_TRANSFER: User {} transferring {} from wallet {} to wallet {}", 
                username, request.getAmount(), request.getFromWalletId(), request.getToWalletId());
        
        // Validate session security for sensitive transfer operation
        sessionSecurityService.validateSessionSecurity("TRANSFER", userId);
        
        // Additional fraud detection for high-value transfers
        if (request.getAmount().compareTo(new java.math.BigDecimal("5000")) >= 0) {
            log.warn("HIGH_VALUE_TRANSFER: User {} attempting transfer of {}", username, request.getAmount());
            // Additional security checks would go here
        }
        
        TransactionResponse response = walletService.transfer(request);
        
        log.info("CRITICAL_WALLET_TRANSFER_COMPLETED: Transfer {} completed for user {}", 
                response.getTransactionId(), username);
        
        return ResponseEntity.ok(ApiResponse.success(response, "Transfer completed successfully"));
    }

    /**
     * CRITICAL ENDPOINT: Wallet Withdrawal
     * Rate Limited: 10 requests/minute per user
     * Priority: HIGH
     */
    @PostMapping("/withdraw")
    @RateLimit(
        requests = 10,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.HIGH,
        burstAllowed = false,
        blockDuration = 10,
        blockUnit = TimeUnit.MINUTES,
        alertThreshold = 0.7,
        description = "Wallet withdrawal endpoint - maximum security",
        errorMessage = "Withdrawal rate limit exceeded. Maximum 10 withdrawals per minute allowed."
    )
    @Operation(
        summary = "Withdraw funds from wallet",
        description = "Withdraws funds from a wallet to external account. Strictly limited to 10 withdrawals per minute per user."
    )
    @PreAuthorize("@walletOwnershipValidator.isWalletOwner(authentication.name, #request.walletId) " +
                  "and @transactionLimitValidator.isWithinWithdrawalLimits(#request.amount)")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
            @Valid @RequestBody WithdrawalRequest request,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader(value = "X-Device-ID", required = false) String deviceId) {
        
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        
        log.warn("CRITICAL_WALLET_WITHDRAWAL: User {} withdrawing {} from wallet {}", 
                username, request.getAmount(), request.getWalletId());
        
        // Enhanced security validation for withdrawals
        sessionSecurityService.validateSessionSecurity("WITHDRAWAL", userId);
        
        // Alert for large withdrawals
        if (request.getAmount().compareTo(new java.math.BigDecimal("2000")) >= 0) {
            log.error("HIGH_VALUE_WITHDRAWAL: User {} withdrawing {} - REQUIRES REVIEW", 
                    username, request.getAmount());
        }
        
        TransactionResponse response = walletService.withdraw(request);
        
        log.warn("CRITICAL_WALLET_WITHDRAWAL_COMPLETED: Withdrawal {} completed for user {}", 
                response.getTransactionId(), username);
        
        return ResponseEntity.ok(ApiResponse.success(response, "Withdrawal completed successfully"));
    }

    /**
     * CRITICAL ENDPOINT: Wallet Balance Check
     * Rate Limited: 60 requests/minute per user
     * Priority: MEDIUM
     */
    @GetMapping("/{walletId}/balance")
    @RateLimit(
        requests = 60,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.MEDIUM,
        burstCapacity = 10,
        alertThreshold = 0.9,
        description = "Wallet balance check endpoint",
        errorMessage = "Balance check rate limit exceeded. Maximum 60 balance checks per minute allowed."
    )
    @Operation(
        summary = "Get wallet balance",
        description = "Retrieves current wallet balance. Limited to 60 requests per minute per user."
    )
    @PreAuthorize("@walletOwnershipValidator.isWalletOwner(authentication.name, #walletId)")
    public ResponseEntity<ApiResponse<WalletBalanceResponse>> getWalletBalance(
            @PathVariable @NotNull UUID walletId,
            @RequestHeader("X-Request-ID") String requestId) {
        
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        
        log.debug("WALLET_BALANCE_CHECK: User {} checking balance for wallet {}", username, walletId);
        
        WalletBalanceResponse response = walletService.getWalletBalance(walletId);
        
        return ResponseEntity.ok(ApiResponse.success(response, "Balance retrieved successfully"));
    }

    /**
     * ADMIN CRITICAL ENDPOINT: Freeze Wallet
     * Rate Limited: 5 requests/minute per admin
     * Priority: EMERGENCY
     */
    @PostMapping("/{walletId}/freeze")
    @RateLimit(
        requests = 5,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.EMERGENCY,
        burstAllowed = false,
        blockDuration = 30,
        blockUnit = TimeUnit.MINUTES,
        userTypes = {"ADMIN", "COMPLIANCE_OFFICER"},
        alertThreshold = 0.6,
        description = "Admin wallet freeze endpoint - maximum security",
        errorMessage = "Wallet freeze rate limit exceeded. Maximum 5 freeze operations per minute allowed."
    )
    @Operation(
        summary = "Freeze a wallet (Admin only)",
        description = "Freezes a wallet to prevent all transactions. Restricted to 5 operations per minute per admin."
    )
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<ApiResponse<WalletFreezeResponse>> freezeWallet(
            @PathVariable @NotNull UUID walletId,
            @Valid @RequestBody WalletFreezeRequest request,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader("X-Admin-Reason") String adminReason) {
        
        String adminUser = SecurityContextHolder.getContext().getAuthentication().getName();
        
        log.error("CRITICAL_WALLET_FREEZE: Admin {} freezing wallet {} - Reason: {}", 
                adminUser, walletId, adminReason);
        
        WalletFreezeResponse response = walletService.freezeWallet(walletId, request, adminUser, adminReason);
        
        log.error("CRITICAL_WALLET_FROZEN: Wallet {} frozen by admin {} - Freeze ID: {}", 
                walletId, adminUser, response.getFreezeId());
        
        // Alert compliance and security teams
        // securityAlertService.alertWalletFrozen(walletId, adminUser, adminReason);
        
        return ResponseEntity.ok(ApiResponse.success(response, "Wallet frozen successfully"));
    }

    /**
     * ADMIN CRITICAL ENDPOINT: Unfreeze Wallet  
     * Rate Limited: 5 requests/minute per admin
     * Priority: EMERGENCY
     */
    @PostMapping("/{walletId}/unfreeze")
    @RateLimit(
        requests = 5,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.EMERGENCY,
        burstAllowed = false,
        blockDuration = 30,
        blockUnit = TimeUnit.MINUTES,
        userTypes = {"ADMIN", "COMPLIANCE_OFFICER"},
        alertThreshold = 0.6,
        description = "Admin wallet unfreeze endpoint - maximum security"
    )
    @Operation(
        summary = "Unfreeze a wallet (Admin only)",
        description = "Unfreezes a previously frozen wallet. Restricted to 5 operations per minute per admin."
    )
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<ApiResponse<WalletUnfreezeResponse>> unfreezeWallet(
            @PathVariable @NotNull UUID walletId,
            @Valid @RequestBody WalletUnfreezeRequest request,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader("X-Admin-Reason") String adminReason) {
        
        String adminUser = SecurityContextHolder.getContext().getAuthentication().getName();
        
        log.warn("CRITICAL_WALLET_UNFREEZE: Admin {} unfreezing wallet {} - Reason: {}", 
                adminUser, walletId, adminReason);
        
        WalletUnfreezeResponse response = walletService.unfreezeWallet(walletId, request, adminUser, adminReason);
        
        log.warn("CRITICAL_WALLET_UNFROZEN: Wallet {} unfrozen by admin {}", walletId, adminUser);
        
        return ResponseEntity.ok(ApiResponse.success(response, "Wallet unfrozen successfully"));
    }

    /**
     * Emergency endpoint for rate limit monitoring
     */
    @GetMapping("/rate-limit/status")
    @RateLimit(
        requests = 30,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.LOW,
        description = "Rate limit status monitoring"
    )
    @Operation(summary = "Monitor rate limit status for wallet operations")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWalletRateLimitStatus() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        
        Map<String, Object> status = Map.of(
            "userId", userId,
            "service", "wallet-service",
            "timestamp", System.currentTimeMillis(),
            "message", "Wallet rate limit status check completed"
        );
        
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}