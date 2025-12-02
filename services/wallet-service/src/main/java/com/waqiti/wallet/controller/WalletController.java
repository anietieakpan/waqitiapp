package com.waqiti.wallet.controller;

import com.waqiti.common.audit.AuditService;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.security.idor.ValidateOwnership;
import com.waqiti.wallet.dto.*;
import com.waqiti.wallet.service.WalletService;
import com.waqiti.wallet.service.TransactionService;
import com.waqiti.wallet.service.BalanceService;
import com.waqiti.common.security.rbac.RequiresPermission;
import com.waqiti.common.security.rbac.Permission;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * SECURED: Wallet Controller with comprehensive financial security controls
 * AUTHENTICATION: All endpoints require valid JWT token with wallet permissions
 * AUTHORIZATION: Strict ownership validation and role-based access control
 * AUDIT: All wallet operations logged for financial compliance
 */
@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
@Validated
@Slf4j
@SecurityRequirement(name = "bearerAuth")
public class WalletController {

    private final WalletService walletService;
    private final TransactionService transactionService;
    private final BalanceService balanceService;
    private final AuditService auditService;
    private final SecurityContext securityContext;

    /**
     * CRITICAL: Get wallet balance
     * SECURITY: Requires WALLET_READ permission and ownership validation
     * RATE LIMIT: 60 requests per minute per user
     * AUDIT: All balance inquiries logged for compliance
     */
    @GetMapping("/{walletId}/balance")
    @RequiresPermission(Permission.WALLET_READ)
    @ValidateOwnership(resourceType = "WALLET", resourceIdParam = "walletId")
    @PreAuthorize("hasAuthority('WALLET_READ') and @walletOwnershipValidator.canAccessWallet(authentication.name, #walletId)")
    @Operation(summary = "Get wallet balance", description = "Retrieve current wallet balance with available and pending amounts")
    @Timed(name = "wallet.balance.duration", description = "Time taken to fetch wallet balance")
    @Counted(name = "wallet.balance.total", description = "Total number of balance inquiries")
    public ResponseEntity<BalanceResponseDto> getWalletBalance(
            @PathVariable UUID walletId,
            @RequestParam(defaultValue = "USD") String currency,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader(value = "X-Device-ID", required = false) String deviceId) {

        String userId = securityContext.getCurrentUserId();
        
        log.info("Balance inquiry - UserId: {}, WalletId: {}, Currency: {}, RequestId: {}",
                userId, walletId, currency, requestId);

        // AUDIT: Log balance access
        auditService.logFinancialQuery("BALANCE_INQUIRY", userId, walletId, currency, 
                requestId, LocalDateTime.now());

        // SECURITY: Device validation
        if (deviceId != null) {
            walletService.validateDeviceAccess(userId, deviceId);
        }

        var balance = balanceService.getWalletBalance(walletId, currency, userId);

        if (balance.isPresent()) {
            return ResponseEntity.ok(balance.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * CRITICAL: Add funds to wallet
     * SECURITY: Requires WALLET_CREDIT permission and ownership validation
     * RATE LIMIT: 10 deposits per hour per user
     * FRAUD: Real-time fraud detection on deposit attempts
     */
    @PostMapping("/{walletId}/credit")
    @ValidateOwnership(resourceType = "WALLET", resourceIdParam = "walletId", requiredPermission = "WRITE")
    @PreAuthorize("hasAuthority('WALLET_CREDIT') and @walletOwnershipValidator.canCreditWallet(authentication.name, #walletId)")
    @Operation(summary = "Add funds to wallet")
    @Timed(name = "wallet.credit.duration", description = "Time taken to process wallet credit")
    @Counted(name = "wallet.credit.total", description = "Total number of wallet credits")
    public ResponseEntity<TransactionResponseDto> creditWallet(
            @PathVariable UUID walletId,
            @Valid @RequestBody CreditWalletRequestDto request,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader(value = "X-Device-ID", required = false) String deviceId,
            @RequestHeader(value = "X-Source-Account", required = true) String sourceAccount) {

        String userId = securityContext.getCurrentUserId();
        
        log.info("Wallet credit request - UserId: {}, WalletId: {}, Amount: {} {}, RequestId: {}",
                userId, walletId, request.getAmount(), request.getCurrency(), requestId);

        // AUDIT: Log credit attempt
        auditService.logFinancialOperation("WALLET_CREDIT_INITIATED", userId, walletId,
                sourceAccount, request.getAmount(), request.getCurrency(), requestId, LocalDateTime.now());

        // VALIDATION: Amount and source validation
        validateCreditRequest(request, userId, walletId, sourceAccount, deviceId);

        // FRAUD CHECK: Screen for suspicious deposit patterns
        var fraudResult = walletService.screenDepositForFraud(userId, walletId, request.getAmount(),
                request.getCurrency(), sourceAccount, deviceId);

        if (fraudResult.isBlocked()) {
            auditService.logSecurityEvent("WALLET_CREDIT_BLOCKED_FRAUD", userId, 
                    fraudResult.getReason(), LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(TransactionResponseDto.blocked(fraudResult.getReason()));
        }

        // EXECUTION: Process wallet credit
        var result = walletService.creditWallet(walletId, request, userId, requestId, sourceAccount, deviceId);

        if (result.isSuccess()) {
            auditService.logFinancialOperation("WALLET_CREDIT_SUCCESS", userId, walletId,
                    sourceAccount, request.getAmount(), request.getCurrency(), 
                    requestId, LocalDateTime.now());

            return ResponseEntity.ok(result.toResponseDto());
        } else {
            auditService.logFinancialOperation("WALLET_CREDIT_FAILED", userId, walletId,
                    sourceAccount, request.getAmount(), request.getCurrency(),
                    requestId, LocalDateTime.now(), result.getErrorMessage());

            return ResponseEntity.badRequest()
                    .body(TransactionResponseDto.failure(result.getErrorMessage()));
        }
    }

    /**
     * CRITICAL: Withdraw funds from wallet
     * SECURITY: Requires WALLET_DEBIT permission and enhanced validation
     * RATE LIMIT: 5 withdrawals per hour per user
     * FRAUD: Multi-factor authentication for large withdrawals
     */
    @PostMapping("/{walletId}/debit")
    @ValidateOwnership(resourceType = "WALLET", resourceIdParam = "walletId", requiredPermission = "WRITE")
    @PreAuthorize("hasAuthority('WALLET_DEBIT') and @walletOwnershipValidator.canDebitWallet(authentication.name, #walletId)")
    @Operation(summary = "Withdraw funds from wallet")
    @Timed(name = "wallet.debit.duration", description = "Time taken to process wallet debit")
    @Counted(name = "wallet.debit.total", description = "Total number of wallet debits")
    public ResponseEntity<TransactionResponseDto> debitWallet(
            @PathVariable UUID walletId,
            @Valid @RequestBody DebitWalletRequestDto request,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader(value = "X-Device-ID", required = false) String deviceId,
            @RequestHeader(value = "X-Destination-Account", required = true) String destinationAccount,
            @RequestHeader(value = "X-MFA-Token", required = false) String mfaToken) {

        String userId = securityContext.getCurrentUserId();
        
        log.info("Wallet debit request - UserId: {}, WalletId: {}, Amount: {} {}, RequestId: {}",
                userId, walletId, request.getAmount(), request.getCurrency(), requestId);

        // AUDIT: Log debit attempt
        auditService.logFinancialOperation("WALLET_DEBIT_INITIATED", userId, walletId,
                destinationAccount, request.getAmount(), request.getCurrency(), requestId, LocalDateTime.now());

        // VALIDATION: Enhanced validation for withdrawals
        validateDebitRequest(request, userId, walletId, destinationAccount, deviceId, mfaToken);

        // BALANCE CHECK: Verify sufficient funds
        if (!balanceService.hasSufficientBalance(walletId, request.getAmount(), request.getCurrency())) {
            auditService.logFinancialOperation("WALLET_DEBIT_INSUFFICIENT_FUNDS", userId, walletId,
                    destinationAccount, request.getAmount(), request.getCurrency(),
                    requestId, LocalDateTime.now());

            return ResponseEntity.badRequest()
                    .body(TransactionResponseDto.failure("Insufficient funds"));
        }

        // MFA CHECK: Require MFA for large withdrawals
        if (request.getAmount().compareTo(new BigDecimal("1000.00")) > 0 && mfaToken == null) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(TransactionResponseDto.mfaRequired("MFA token required for large withdrawals"));
        }

        if (mfaToken != null && !walletService.validateMfaToken(userId, mfaToken)) {
            auditService.logSecurityEvent("WALLET_DEBIT_INVALID_MFA", userId, 
                    "Invalid MFA token for withdrawal", LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(TransactionResponseDto.failure("Invalid MFA token"));
        }

        // EXECUTION: Process wallet debit
        var result = walletService.debitWallet(walletId, request, userId, requestId, destinationAccount, deviceId);

        if (result.isSuccess()) {
            auditService.logFinancialOperation("WALLET_DEBIT_SUCCESS", userId, walletId,
                    destinationAccount, request.getAmount(), request.getCurrency(),
                    requestId, LocalDateTime.now());

            return ResponseEntity.ok(result.toResponseDto());
        } else {
            auditService.logFinancialOperation("WALLET_DEBIT_FAILED", userId, walletId,
                    destinationAccount, request.getAmount(), request.getCurrency(),
                    requestId, LocalDateTime.now(), result.getErrorMessage());

            return ResponseEntity.badRequest()
                    .body(TransactionResponseDto.failure(result.getErrorMessage()));
        }
    }

    /**
     * SECURED: Get wallet transaction history
     * SECURITY: Requires READ permission and ownership validation
     * PAGINATION: Limited to prevent data exposure
     */
    @GetMapping("/{walletId}/transactions")
    @ValidateOwnership(resourceType = "WALLET", resourceIdParam = "walletId")
    @PreAuthorize("hasAuthority('WALLET_READ') and @walletOwnershipValidator.canAccessWallet(authentication.name, #walletId)")
    @Operation(summary = "Get wallet transaction history")
    @Timed(name = "wallet.transactions.duration", description = "Time taken to fetch transactions")
    public ResponseEntity<Page<TransactionResponseDto>> getWalletTransactions(
            @PathVariable UUID walletId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String status,
            @RequestHeader("X-Request-ID") String requestId,
            Pageable pageable) {

        String userId = securityContext.getCurrentUserId();
        
        // SECURITY: Limit page size to prevent data exposure
        if (size > 100) {
            size = 100;
        }
        
        log.info("Wallet transactions requested - UserId: {}, WalletId: {}, Page: {}, Size: {}, RequestId: {}",
                userId, walletId, page, size, requestId);

        // AUDIT: Log transaction history access
        auditService.logDataAccess("WALLET_TRANSACTIONS_ACCESSED", userId, walletId.toString(),
                String.format("page=%d,size=%d", page, size), requestId, LocalDateTime.now());

        var transactions = transactionService.getWalletTransactions(walletId, userId, 
                PageRequest.of(page, size), transactionType, currency, status);

        return ResponseEntity.ok(transactions);
    }

    /**
     * CRITICAL: Freeze wallet for security reasons
     * SECURITY: Requires WALLET_FREEZE permission
     * AUDIT: All freeze actions logged with reason
     */
    @PostMapping("/{walletId}/freeze")
    @PreAuthorize("hasAuthority('WALLET_FREEZE') and (@walletOwnershipValidator.canFreezeWallet(authentication.name, #walletId) or hasRole('COMPLIANCE_OFFICER'))")
    @Operation(summary = "Freeze wallet")
    @Timed(name = "wallet.freeze.duration", description = "Time taken to freeze wallet")
    public ResponseEntity<WalletStatusResponseDto> freezeWallet(
            @PathVariable UUID walletId,
            @Valid @RequestBody FreezeWalletRequestDto request,
            @RequestHeader("X-Request-ID") String requestId) {

        String userId = securityContext.getCurrentUserId();
        
        log.warn("Wallet freeze request - UserId: {}, WalletId: {}, Reason: {}, RequestId: {}",
                userId, walletId, request.getReason(), requestId);

        // AUDIT: Log freeze attempt
        auditService.logSecurityEvent("WALLET_FREEZE_INITIATED", userId, 
                String.format("WalletId: %s, Reason: %s", walletId, request.getReason()), 
                LocalDateTime.now());

        var result = walletService.freezeWallet(walletId, request.getReason(), userId, requestId);

        if (result.isSuccess()) {
            auditService.logSecurityEvent("WALLET_FROZEN", userId,
                    String.format("WalletId: %s, Reason: %s", walletId, request.getReason()),
                    LocalDateTime.now());

            // NOTIFICATION: Notify wallet owner of freeze
            walletService.notifyWalletOwner(walletId, "WALLET_FROZEN", request.getReason());

            return ResponseEntity.ok(result.toResponseDto());
        } else {
            return ResponseEntity.badRequest()
                    .body(WalletStatusResponseDto.failure(result.getErrorMessage()));
        }
    }

    /**
     * CRITICAL: Unfreeze wallet
     * SECURITY: Requires WALLET_UNFREEZE permission and manager approval
     */
    @PostMapping("/{walletId}/unfreeze")
    @PreAuthorize("hasAuthority('WALLET_UNFREEZE') and (hasRole('MANAGER') or hasRole('COMPLIANCE_OFFICER'))")
    @Operation(summary = "Unfreeze wallet")
    @Timed(name = "wallet.unfreeze.duration", description = "Time taken to unfreeze wallet")
    public ResponseEntity<WalletStatusResponseDto> unfreezeWallet(
            @PathVariable UUID walletId,
            @Valid @RequestBody UnfreezeWalletRequestDto request,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader("X-Manager-Approval") String managerApproval) {

        String userId = securityContext.getCurrentUserId();
        
        log.info("Wallet unfreeze request - UserId: {}, WalletId: {}, ManagerApproval: {}, RequestId: {}",
                userId, walletId, managerApproval, requestId);

        // VALIDATION: Verify manager approval
        if (!walletService.validateManagerApproval(managerApproval, walletId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(WalletStatusResponseDto.failure("Invalid manager approval"));
        }

        // AUDIT: Log unfreeze attempt
        auditService.logSecurityEvent("WALLET_UNFREEZE_INITIATED", userId,
                String.format("WalletId: %s, ManagerApproval: %s, Reason: %s", 
                        walletId, managerApproval, request.getReason()),
                LocalDateTime.now());

        var result = walletService.unfreezeWallet(walletId, request.getReason(), userId, 
                managerApproval, requestId);

        if (result.isSuccess()) {
            auditService.logSecurityEvent("WALLET_UNFROZEN", userId,
                    String.format("WalletId: %s, ManagerApproval: %s", walletId, managerApproval),
                    LocalDateTime.now());

            // NOTIFICATION: Notify wallet owner of unfreeze
            walletService.notifyWalletOwner(walletId, "WALLET_UNFROZEN", request.getReason());

            return ResponseEntity.ok(result.toResponseDto());
        } else {
            return ResponseEntity.badRequest()
                    .body(WalletStatusResponseDto.failure(result.getErrorMessage()));
        }
    }

    /**
     * SECURED: Get wallet details
     * SECURITY: Requires READ permission and ownership validation
     */
    @GetMapping("/{walletId}")
    @ValidateOwnership(resourceType = "WALLET", resourceIdParam = "walletId")
    @PreAuthorize("hasAuthority('WALLET_READ') and @walletOwnershipValidator.canAccessWallet(authentication.name, #walletId)")
    @Operation(summary = "Get wallet details")
    public ResponseEntity<WalletDetailsDto> getWalletDetails(
            @PathVariable UUID walletId,
            @RequestHeader("X-Request-ID") String requestId) {

        String userId = securityContext.getCurrentUserId();
        
        // AUDIT: Log wallet details access
        auditService.logDataAccess("WALLET_DETAILS_ACCESSED", userId, walletId.toString(),
                requestId, LocalDateTime.now());

        var wallet = walletService.getWalletDetails(walletId, userId);

        if (wallet.isPresent()) {
            return ResponseEntity.ok(wallet.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * SECURED: List user's wallets
     * SECURITY: Only returns wallets owned by authenticated user
     */
    @GetMapping
    @PreAuthorize("hasAuthority('WALLET_READ')")
    @Operation(summary = "List user wallets")
    public ResponseEntity<List<WalletSummaryDto>> getUserWallets(
            @RequestHeader("X-Request-ID") String requestId) {

        String userId = securityContext.getCurrentUserId();
        
        // AUDIT: Log wallet list access
        auditService.logDataAccess("USER_WALLETS_ACCESSED", userId, userId, 
                requestId, LocalDateTime.now());

        var wallets = walletService.getUserWallets(userId);
        
        return ResponseEntity.ok(wallets);
    }

    /**
     * VALIDATION: Validate credit request with security checks
     */
    private void validateCreditRequest(CreditWalletRequestDto request, String userId, 
                                     UUID walletId, String sourceAccount, String deviceId) {
        // AMOUNT VALIDATION
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }

        if (request.getAmount().compareTo(new BigDecimal("50000.00")) > 0) {
            throw new IllegalArgumentException("Credit amount exceeds daily limit");
        }

        // SOURCE VALIDATION
        if (!walletService.isValidSourceAccount(userId, sourceAccount)) {
            throw new SecurityException("Invalid or unauthorized source account");
        }

        // VELOCITY CHECK
        if (walletService.exceedsCreditVelocityLimits(userId, walletId, request.getAmount())) {
            throw new SecurityException("Credit velocity limits exceeded");
        }
    }

    /**
     * VALIDATION: Validate debit request with enhanced security
     */
    private void validateDebitRequest(DebitWalletRequestDto request, String userId, 
                                    UUID walletId, String destinationAccount, 
                                    String deviceId, String mfaToken) {
        // AMOUNT VALIDATION
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }

        // DESTINATION VALIDATION
        if (!walletService.isValidDestinationAccount(userId, destinationAccount)) {
            throw new SecurityException("Invalid or unauthorized destination account");
        }

        // DEVICE VALIDATION
        if (deviceId != null && !walletService.isDeviceAuthorized(userId, deviceId)) {
            throw new SecurityException("Unauthorized device for withdrawal");
        }

        // DAILY LIMITS
        if (walletService.exceedsDailyWithdrawalLimit(userId, walletId, request.getAmount())) {
            throw new SecurityException("Daily withdrawal limit exceeded");
        }
    }

    /**
     * EXCEPTION HANDLERS: Security and validation exception handling
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponseDto> handleSecurityException(SecurityException e) {
        log.error("Security exception in wallet controller: {}", e.getMessage());
        
        String userId = securityContext.getCurrentUserId();
        auditService.logSecurityEvent("WALLET_SECURITY_VIOLATION", userId, 
                e.getMessage(), LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponseDto("SECURITY_VIOLATION", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationException(IllegalArgumentException e) {
        log.error("Validation exception in wallet controller: {}", e.getMessage());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponseDto("VALIDATION_ERROR", e.getMessage()));
    }
}