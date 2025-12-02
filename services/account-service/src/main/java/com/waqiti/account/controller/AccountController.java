package com.waqiti.account.controller;

import com.waqiti.account.dto.*;
import com.waqiti.account.service.AccountManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.waqiti.common.security.rbac.RequiresPermission;
import com.waqiti.common.security.rbac.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Account Management REST Controller
 * 
 * Provides comprehensive account management endpoints:
 * - Account creation and lifecycle management
 * - Balance inquiries and management
 * - Account status updates and controls
 * - Account hierarchy and relationships
 * - Compliance and security operations
 */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Account Management", description = "Core banking account operations")
public class AccountController {

    private final AccountManagementService accountManagementService;

    @PostMapping
    @RequiresPermission(Permission.ACCOUNT_WRITE)
    @Operation(summary = "Create new account", description = "Creates a new banking account with specified configuration")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request,
            Authentication authentication) {
        
        // SECURITY FIX: Prevent users from creating accounts for others
        UUID authenticatedUserId = extractUserIdFromAuthentication(authentication);
        
        // If user role, force the userId to match authenticated user
        if (authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_USER"))) {
            request.setUserId(authenticatedUserId);
            log.info("Creating account for authenticated user: {}", authenticatedUserId);
        } else {
            // Admin can create for any user, but log it for audit
            log.warn("ADMIN creating account for different user - Admin: {}, Target: {}", 
                authenticatedUserId, request.getUserId());
        }
        
        AccountResponse account = accountManagementService.createAccount(request);
        return ResponseEntity.ok(account);
    }

    @GetMapping("/{accountId}")
    @RequiresPermission(Permission.ACCOUNT_READ)
    @Operation(summary = "Get account details", description = "Retrieves comprehensive account information")
    @PreAuthorize("hasRole('ADMIN') or @accountService.isAccountOwner(#accountId, authentication.name)")
    public ResponseEntity<AccountDetailsResponse> getAccount(@PathVariable UUID accountId) {
        AccountDetailsResponse account = accountManagementService.getAccountDetails(accountId);
        return ResponseEntity.ok(account);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user accounts", description = "Retrieves all accounts for a specific user")
    @PreAuthorize("hasRole('ADMIN') or authentication.name == #userId.toString()")
    public ResponseEntity<List<AccountSummaryResponse>> getUserAccounts(@PathVariable UUID userId) {
        List<AccountSummaryResponse> accounts = accountManagementService.getUserAccounts(userId);
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{accountId}/balance")
    @Operation(summary = "Get account balance", description = "Retrieves current account balance information")
    @PreAuthorize("hasRole('ADMIN') or @accountService.isAccountOwner(#accountId, authentication.name)")
    public ResponseEntity<AccountBalanceResponse> getAccountBalance(@PathVariable UUID accountId) {
        AccountBalanceResponse balance = accountManagementService.getAccountBalance(accountId);
        return ResponseEntity.ok(balance);
    }

    @PutMapping("/{accountId}/status")
    @RequiresPermission(Permission.ACCOUNT_CLOSE)
    @Operation(summary = "Update account status", description = "Updates account status (activate, suspend, freeze, etc.)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateAccountStatus(
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountStatusRequest request) {
        log.info("Updating account status: {} to {}", accountId, request.getStatus());
        accountManagementService.updateAccountStatus(accountId, request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{accountId}/limits")
    @Operation(summary = "Update account limits", description = "Updates transaction limits for the account")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateAccountLimits(
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountLimitsRequest request) {
        accountManagementService.updateAccountLimits(accountId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{accountId}/freeze")
    @Operation(summary = "Freeze account", description = "Freezes account due to security or compliance reasons")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> freezeAccount(
            @PathVariable UUID accountId,
            @Valid @RequestBody FreezeAccountRequest request) {
        log.warn("Freezing account: {} - Reason: {}", accountId, request.getReason());
        accountManagementService.freezeAccount(accountId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{accountId}/unfreeze")
    @Operation(summary = "Unfreeze account", description = "Unfreezes a previously frozen account")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> unfreezeAccount(@PathVariable UUID accountId) {
        log.info("Unfreezing account: {}", accountId);
        accountManagementService.unfreezeAccount(accountId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{accountId}/close")
    @Operation(summary = "Close account", description = "Closes an account permanently")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> closeAccount(
            @PathVariable UUID accountId,
            @Valid @RequestBody CloseAccountRequest request) {
        log.info("Closing account: {} - Reason: {}", accountId, request.getReason());
        accountManagementService.closeAccount(accountId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/search")
    @Operation(summary = "Search accounts", description = "Advanced account search with multiple criteria")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AccountSummaryResponse>> searchAccounts(
            @RequestParam(required = false) String accountNumber,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String accountType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) BigDecimal minBalance,
            @RequestParam(required = false) BigDecimal maxBalance,
            Pageable pageable) {
        
        AccountSearchCriteria criteria = AccountSearchCriteria.builder()
            .accountNumber(accountNumber)
            .userId(userId)
            .accountType(accountType)
            .status(status)
            .currency(currency)
            .minBalance(minBalance)
            .maxBalance(maxBalance)
            .build();
            
        Page<AccountSummaryResponse> accounts = accountManagementService.searchAccounts(criteria, pageable);
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/statistics")
    @Operation(summary = "Get account statistics", description = "Retrieves account statistics and metrics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountStatisticsResponse> getAccountStatistics() {
        AccountStatisticsResponse statistics = accountManagementService.getAccountStatistics();
        return ResponseEntity.ok(statistics);
    }

    @GetMapping("/compliance/review")
    @Operation(summary = "Get accounts for compliance review", description = "Retrieves accounts requiring compliance review")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE')")
    public ResponseEntity<List<AccountSummaryResponse>> getAccountsForComplianceReview() {
        List<AccountSummaryResponse> accounts = accountManagementService.getAccountsForComplianceReview();
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/dormant")
    @Operation(summary = "Get dormant accounts", description = "Retrieves accounts with no recent activity")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AccountSummaryResponse>> getDormantAccounts(
            @RequestParam(defaultValue = "90") int inactiveDays) {
        List<AccountSummaryResponse> accounts = accountManagementService.getDormantAccounts(inactiveDays);
        return ResponseEntity.ok(accounts);
    }

    @PostMapping("/{accountId}/reserve-funds")
    @Operation(summary = "Reserve funds", description = "Reserves funds for pending transactions")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('ADMIN')")
    public ResponseEntity<ReserveFundsResponse> reserveFunds(
            @PathVariable UUID accountId,
            @Valid @RequestBody ReserveFundsRequest request) {
        ReserveFundsResponse response = accountManagementService.reserveFunds(accountId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{accountId}/release-funds")
    @Operation(summary = "Release reserved funds", description = "Releases previously reserved funds")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('ADMIN')")
    public ResponseEntity<Void> releaseReservedFunds(
            @PathVariable UUID accountId,
            @Valid @RequestBody ReleaseReservedFundsRequest request) {
        accountManagementService.releaseReservedFunds(accountId, request);
        return ResponseEntity.ok().build();
    }
    
    /**
     * SECURITY ENDPOINT: Check if user owns account
     * Used by security service for authorization checks
     * Prevents horizontal privilege escalation
     */
    @GetMapping("/{accountId}/owner/{userId}/check")
    @Operation(summary = "Check account ownership", 
               description = "Verifies if the specified user owns the account - used for security authorization")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('SECURITY_SERVICE') or hasRole('ADMIN')")
    public ResponseEntity<Boolean> checkAccountOwnership(
            @PathVariable UUID accountId,
            @PathVariable UUID userId) {
        
        log.debug("SECURITY: Checking account ownership - accountId={}, userId={}", accountId, userId);
        
        try {
            boolean isOwner = accountManagementService.checkAccountOwnership(accountId, userId);
            
            if (!isOwner) {
                log.warn("SECURITY: Account ownership check FAILED - user {} does NOT own account {}", 
                    userId, accountId);
            }
            
            return ResponseEntity.ok(isOwner);
            
        } catch (Exception e) {
            log.error("SECURITY: Error checking account ownership - accountId={}, userId={}", 
                accountId, userId, e);
            return ResponseEntity.ok(false);
        }
    }
    
    /**
     * SECURITY: Extract user ID from authentication context
     * Prevents users from impersonating others in requests
     */
    private UUID extractUserIdFromAuthentication(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            log.error("Failed to parse user ID from authentication: {}", authentication.getName());
            throw new SecurityException("Invalid user ID in authentication context", e);
        }
    }
}