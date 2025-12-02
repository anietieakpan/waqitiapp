package com.waqiti.wallet.api;

import com.waqiti.wallet.dto.*;
import com.waqiti.wallet.service.WalletService;
import com.waqiti.wallet.security.SessionSecurityService;
import com.waqiti.common.ratelimit.RateLimited;
import com.waqiti.common.security.ResourceOwnershipAspect.*;
import com.waqiti.common.security.SecurityContextUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
@Slf4j
@Validated
@PreAuthorize("hasRole('USER')")
public class WalletController {
    private final WalletService walletService;
    private final SessionSecurityService sessionSecurityService;

    @PostMapping
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 5, refillTokens = 5, refillPeriodMinutes = 60)
    public ResponseEntity<WalletResponse> createWallet(@Valid @RequestBody CreateWalletRequest request) {
        log.info("Creating wallet: {}", request);
        return ResponseEntity.ok(walletService.createWallet(request));
    }

    @GetMapping("/{walletId}")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 100, refillTokens = 100, refillPeriodMinutes = 1)
    @ValidateOwnership(resourceType = ResourceType.WALLET, resourceIdParam = "walletId", operation = "VIEW")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable @NotNull UUID walletId) {
        log.info("Getting wallet: {}", walletId);
        return ResponseEntity.ok(walletService.getWallet(walletId));
    }

    @GetMapping("/user/{userId}")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 50, refillTokens = 50, refillPeriodMinutes = 1)
    @PreAuthorize("@userOwnershipValidator.isCurrentUser(authentication.name, #userId) or hasRole('ADMIN')")
    public ResponseEntity<List<WalletResponse>> getUserWallets(@PathVariable @NotNull UUID userId) {
        log.info("Getting wallets for user: {}", userId);
        return ResponseEntity.ok(walletService.getUserWallets(userId));
    }

    @PostMapping("/transfer")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 5)
    @PreAuthorize("@walletOwnershipValidator.isWalletOwner(authentication.name, #request.fromWalletId) " +
                  "and isWithinTransactionLimit(#request.amount, 'SINGLE_TRANSFER')")
    public ResponseEntity<TransactionResponse> transfer(@Valid @RequestBody TransferRequest request) {
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();
        
        log.info("SECURITY: User {} initiating transfer of {} from wallet {}", 
                SecurityContextHolder.getContext().getAuthentication().getName(), 
                request.getAmount(), 
                request.getFromWalletId());
        
        // SECURITY FIX: Validate session security for sensitive transfer operation
        sessionSecurityService.validateSessionSecurity("TRANSFER", userId);
        
        // Check if 2FA is required for this amount
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            // This would typically check if 2FA has been completed for high-value transactions
            // Implementation would depend on your 2FA system
        }
        
        TransactionResponse response = walletService.transfer(request);
        
        log.info("SECURITY: Transfer completed successfully for user {} - Transaction ID: {}", 
                userId, response.getTransactionId());
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/deposit")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 20, refillTokens = 20, refillPeriodMinutes = 5)
    @PreAuthorize("@walletOwnershipValidator.isWalletOwner(authentication.name, #request.walletId)")
    public ResponseEntity<TransactionResponse> deposit(@Valid @RequestBody DepositRequest request) {
        log.info("User {} initiating deposit to wallet {}", SecurityContextHolder.getContext().getAuthentication().getName(), request.getWalletId());
        return ResponseEntity.ok(walletService.deposit(request));
    }

    @PostMapping("/withdraw")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 15, refillTokens = 15, refillPeriodMinutes = 10)
    @PreAuthorize("@walletOwnershipValidator.isWalletOwner(authentication.name, #request.walletId) " +
                  "and isWithinTransactionLimit(#request.amount, 'SINGLE_WITHDRAWAL')")
    public ResponseEntity<TransactionResponse> withdraw(@Valid @RequestBody WithdrawalRequest request) {
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();
        
        log.info("SECURITY: User {} initiating withdrawal of {} from wallet {}", 
                SecurityContextHolder.getContext().getAuthentication().getName(), 
                request.getAmount(),
                request.getWalletId());
        
        // SECURITY FIX: Validate session security for sensitive withdrawal operation
        sessionSecurityService.validateSessionSecurity("WITHDRAW", userId);
        
        TransactionResponse response = walletService.withdraw(request);
        
        log.info("SECURITY: Withdrawal completed successfully for user {} - Transaction ID: {}", 
                userId, response.getTransactionId());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{walletId}/transactions")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 50, refillTokens = 50, refillPeriodMinutes = 1)
    @ValidateOwnership(resourceType = ResourceType.WALLET, resourceIdParam = "walletId", operation = "VIEW_TRANSACTIONS")
    public ResponseEntity<Page<TransactionResponse>> getWalletTransactions(
            @PathVariable @NotNull UUID walletId,
            Pageable pageable) {
        log.info("Getting transactions for wallet: {}", walletId);
        return ResponseEntity.ok(walletService.getWalletTransactions(walletId, pageable));
    }

    @GetMapping("/transactions/user/{userId}")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 50, refillTokens = 50, refillPeriodMinutes = 1)
    @PreAuthorize("@userOwnershipValidator.isCurrentUser(authentication.name, #userId) or hasRole('ADMIN')")
    public ResponseEntity<Page<TransactionResponse>> getUserTransactions(
            @PathVariable @NotNull UUID userId,
            Pageable pageable) {
        log.info("Getting transactions for user: {}", userId);
        return ResponseEntity.ok(walletService.getUserTransactions(userId, pageable));
    }
    
    // SECURITY FIX: Admin endpoints for wallet freezing and limit management
    
    @PostMapping("/{walletId}/freeze")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 60)
    @PreAuthorize("hasRole('WALLET_ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<WalletResponse> freezeWallet(
            @PathVariable @NotNull UUID walletId,
            @RequestBody(required = false) Map<String, String> request) {
        UUID adminUserId = SecurityContextUtil.getAuthenticatedUserId();
        
        log.info("SECURITY: Admin {} initiating wallet freeze for wallet {}", adminUserId, walletId);
        
        // SECURITY FIX: Validate session security for sensitive freeze operation
        sessionSecurityService.validateSessionSecurity("FREEZE", adminUserId);
        
        String reason = (request != null && request.containsKey("reason")) ? 
                request.get("reason") : "Administrative action by " + adminUserId;
        
        WalletResponse response = walletService.freezeWallet(walletId, reason);
        
        log.info("SECURITY: Wallet freeze completed successfully - Admin: {}, Wallet: {}", 
                adminUserId, walletId);
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{walletId}/unfreeze")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 60)
    @PreAuthorize("hasRole('WALLET_ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<WalletResponse> unfreezeWallet(
            @PathVariable @NotNull UUID walletId,
            @RequestBody(required = false) Map<String, String> request) {
        UUID adminUserId = SecurityContextUtil.getAuthenticatedUserId();
        
        log.info("SECURITY: Admin {} initiating wallet unfreeze for wallet {}", adminUserId, walletId);
        
        // SECURITY FIX: Validate session security for sensitive unfreeze operation
        sessionSecurityService.validateSessionSecurity("UNFREEZE", adminUserId);
        
        String reason = (request != null && request.containsKey("reason")) ? 
                request.get("reason") : "Administrative action by " + adminUserId;
        
        WalletResponse response = walletService.unfreezeWallet(walletId, reason);
        
        log.info("SECURITY: Wallet unfreeze completed successfully - Admin: {}, Wallet: {}", 
                adminUserId, walletId);
        
        return ResponseEntity.ok(response);
    }
    
    // SECURITY FIX: Session security monitoring endpoint
    @GetMapping("/admin/session-security/stats")
    @PreAuthorize("hasRole('WALLET_ADMIN') or hasRole('SECURITY_ANALYST')")
    public ResponseEntity<Map<String, Object>> getSessionSecurityStats() {
        UUID adminUserId = SecurityContextUtil.getAuthenticatedUserId();
        
        log.info("SECURITY: Admin {} requesting session security statistics", adminUserId);
        
        Map<String, Object> stats = sessionSecurityService.getSessionSecurityStats();
        
        return ResponseEntity.ok(stats);
    }
    
    // CRITICAL SECURITY ENDPOINT: Wallet ownership verification for validators
    @GetMapping("/{walletId}/owner/{username}")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('WALLET_ADMIN') or hasRole('VALIDATION_SERVICE')")
    @RateLimited(keyType = RateLimited.KeyType.SYSTEM, capacity = 1000, refillTokens = 1000, refillPeriodMinutes = 1)
    public ResponseEntity<Map<String, Object>> verifyWalletOwnership(
            @PathVariable @NotNull UUID walletId,
            @PathVariable @NotNull String username) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("SECURITY: Verifying wallet ownership - Wallet: {}, User: {}", walletId, username);
            
            boolean isOwner = walletService.isWalletOwnedByUser(walletId, username);
            long duration = System.currentTimeMillis() - startTime;
            
            if (isOwner) {
                log.debug("SECURITY: Wallet ownership confirmed - Wallet: {}, User: {}, Duration: {}ms", 
                         walletId, username, duration);
            } else {
                log.warn("SECURITY: Wallet ownership denied - Wallet: {}, User: {}, Duration: {}ms", 
                        walletId, username, duration);
            }
            
            Map<String, Object> response = Map.of(
                "isOwner", isOwner,
                "walletId", walletId.toString(),
                "username", username,
                "verificationTimestamp", System.currentTimeMillis(),
                "durationMs", duration
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("SECURITY ERROR: Wallet ownership verification failed - Wallet: {}, User: {}, Duration: {}ms, Error: {}", 
                     walletId, username, duration, e.getMessage(), e);
            
            // Return error response but don't expose internal details
            Map<String, Object> response = Map.of(
                "isOwner", false,
                "walletId", walletId.toString(),
                "username", username,
                "error", "Verification failed",
                "verificationTimestamp", System.currentTimeMillis(),
                "durationMs", duration
            );
            
            return ResponseEntity.ok(response);
        }
    }
}