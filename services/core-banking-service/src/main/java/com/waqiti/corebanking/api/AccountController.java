package com.waqiti.corebanking.api;

import com.waqiti.corebanking.dto.*;
import com.waqiti.corebanking.service.AccountManagementService;
import com.waqiti.common.tracing.Traced;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Account Management", description = "Core banking account operations")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AccountManagementService accountManagementService;

    @PostMapping
    @Operation(summary = "Create new account", description = "Creates a new banking account for a user")
    @ApiResponse(responseCode = "201", description = "Account created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid account creation request")
    @ApiResponse(responseCode = "409", description = "Account already exists")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Traced(operationName = "create-account", businessOperation = "account-creation", priority = Traced.TracingPriority.HIGH)
    public ResponseEntity<AccountResponseDto> createAccount(
            @Valid @RequestBody AccountCreationRequestDto request) {
        
        log.info("Creating account for user: {} (type: {}, currency: {})", 
                request.getUserId(), request.getAccountType(), request.getCurrency());
        
        try {
            AccountResponseDto response = accountManagementService.createAccount(request);
            
            log.info("Successfully created account: {} for user: {}", 
                    response.getAccountId(), request.getUserId());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid account creation request: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to create account for user: {}", request.getUserId(), e);
            throw new RuntimeException("Account creation failed", e);
        }
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Get account details", description = "Retrieves detailed account information")
    @ApiResponse(responseCode = "200", description = "Account found")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Traced(operationName = "get-account", businessOperation = "account-inquiry", priority = Traced.TracingPriority.MEDIUM)
    public ResponseEntity<AccountResponseDto> getAccount(
            @Parameter(description = "Account identifier", required = true)
            @PathVariable @NotBlank String accountId) {
        
        log.debug("Retrieving account details: {}", accountId);
        
        try {
            AccountResponseDto account = accountManagementService.getAccount(accountId);
            return ResponseEntity.ok(account);
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                log.warn("Account not found: {}", accountId);
                return ResponseEntity.notFound().build();
            }
            log.error("Failed to retrieve account: {}", accountId, e);
            throw e;
        }
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user accounts", description = "Retrieves all accounts for a specific user")
    @ApiResponse(responseCode = "200", description = "Accounts retrieved successfully")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Traced(operationName = "get-user-accounts", businessOperation = "account-inquiry", priority = Traced.TracingPriority.MEDIUM)
    public ResponseEntity<List<AccountResponseDto>> getUserAccounts(
            @Parameter(description = "User identifier", required = true)
            @PathVariable @NotBlank String userId) {
        
        log.debug("Retrieving accounts for user: {}", userId);
        
        try {
            List<AccountResponseDto> accounts = accountManagementService.getUserAccounts(userId);
            return ResponseEntity.ok(accounts);
            
        } catch (Exception e) {
            log.error("Failed to retrieve accounts for user: {}", userId, e);
            throw new RuntimeException("Failed to retrieve user accounts", e);
        }
    }

    @GetMapping("/{accountId}/balance")
    @Operation(summary = "Get account balance", description = "Retrieves current account balance information")
    @ApiResponse(responseCode = "200", description = "Balance retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Traced(operationName = "get-balance", businessOperation = "balance-inquiry", priority = Traced.TracingPriority.HIGH)
    public ResponseEntity<AccountBalanceDto> getAccountBalance(
            @Parameter(description = "Account identifier", required = true)
            @PathVariable @NotBlank String accountId) {
        
        log.debug("Retrieving balance for account: {}", accountId);
        
        try {
            AccountBalanceDto balance = accountManagementService.getAccountBalance(accountId);
            return ResponseEntity.ok(balance);
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                log.warn("Account not found for balance inquiry: {}", accountId);
                return ResponseEntity.notFound().build();
            }
            log.error("Failed to retrieve balance for account: {}", accountId, e);
            throw e;
        }
    }

    @PostMapping("/{accountId}/reserve")
    @Operation(summary = "Reserve funds", description = "Reserves a specific amount from account balance")
    @ApiResponse(responseCode = "200", description = "Funds reserved successfully")
    @ApiResponse(responseCode = "400", description = "Insufficient funds or invalid request")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Traced(operationName = "reserve-funds", businessOperation = "fund-reservation", priority = Traced.TracingPriority.HIGH)
    public ResponseEntity<ReservationResponseDto> reserveFunds(
            @Parameter(description = "Account identifier", required = true)
            @PathVariable @NotBlank String accountId,
            @Valid @RequestBody FundReservationRequestDto request) {
        
        log.info("Reserving funds: {} {} from account: {} (reservation: {})", 
                request.getAmount(), request.getCurrency(), accountId, request.getReservationId());
        
        try {
            ReservationResponseDto response = accountManagementService.reserveFunds(accountId, request);
            
            log.info("Successfully reserved funds: {} (reservation: {})", 
                    accountId, response.getReservationId());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Fund reservation failed for account {}: {}", accountId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to reserve funds for account: {}", accountId, e);
            throw new RuntimeException("Fund reservation failed", e);
        }
    }

    @DeleteMapping("/{accountId}/reserve/{reservationId}")
    @Operation(summary = "Release reserved funds", description = "Releases previously reserved funds back to available balance")
    @ApiResponse(responseCode = "204", description = "Funds released successfully")
    @ApiResponse(responseCode = "404", description = "Reservation not found")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Traced(operationName = "release-funds", businessOperation = "fund-release", priority = Traced.TracingPriority.HIGH)
    public ResponseEntity<Void> releaseFunds(
            @Parameter(description = "Account identifier", required = true)
            @PathVariable @NotBlank String accountId,
            @Parameter(description = "Reservation identifier", required = true)
            @PathVariable @NotBlank String reservationId) {
        
        log.info("Releasing reserved funds: account={}, reservation={}", accountId, reservationId);
        
        try {
            accountManagementService.releaseFunds(accountId, reservationId);
            
            log.info("Successfully released reserved funds: account={}, reservation={}", 
                    accountId, reservationId);
            
            return ResponseEntity.noContent().build();
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                log.warn("Reservation not found: account={}, reservation={}", accountId, reservationId);
                return ResponseEntity.notFound().build();
            }
            log.error("Failed to release funds: account={}, reservation={}", accountId, reservationId, e);
            throw e;
        }
    }

    @PutMapping("/{accountId}/status")
    @Operation(summary = "Update account status", description = "Updates account status (active, frozen, closed, etc.)")
    @ApiResponse(responseCode = "200", description = "Account status updated successfully")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @PreAuthorize("hasRole('ADMIN')")
    @Traced(operationName = "update-account-status", businessOperation = "account-management", priority = Traced.TracingPriority.HIGH)
    public ResponseEntity<AccountResponseDto> updateAccountStatus(
            @Parameter(description = "Account identifier", required = true)
            @PathVariable @NotBlank String accountId,
            @Valid @RequestBody AccountStatusUpdateDto request) {
        
        log.info("Updating account status: {} -> {}", accountId, request.getStatus());
        
        try {
            AccountResponseDto response = accountManagementService.updateAccountStatus(accountId, request);
            
            log.info("Successfully updated account status: {} -> {}", accountId, request.getStatus());
            
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                log.warn("Account not found for status update: {}", accountId);
                return ResponseEntity.notFound().build();
            }
            log.error("Failed to update account status: {}", accountId, e);
            throw e;
        }
    }

    @GetMapping
    @Operation(summary = "Search accounts", description = "Search accounts with filtering and pagination")
    @ApiResponse(responseCode = "200", description = "Accounts retrieved successfully")
    @PreAuthorize("hasRole('ADMIN')")
    @Traced(operationName = "search-accounts", businessOperation = "account-search", priority = Traced.TracingPriority.LOW)
    public ResponseEntity<Page<AccountResponseDto>> searchAccounts(
            @Parameter(description = "Account status filter")
            @RequestParam(required = false) String status,
            @Parameter(description = "Account type filter")
            @RequestParam(required = false) String accountType,
            @Parameter(description = "Currency filter")
            @RequestParam(required = false) String currency,
            @Parameter(description = "User ID filter")
            @RequestParam(required = false) String userId,
            Pageable pageable) {
        
        log.debug("Searching accounts with filters: status={}, type={}, currency={}, userId={}", 
                 status, accountType, currency, userId);
        
        try {
            AccountSearchCriteria criteria = AccountSearchCriteria.builder()
                .status(status)
                .accountType(accountType)
                .currency(currency)
                .userId(userId)
                .build();
            
            Page<AccountResponseDto> accounts = accountManagementService.searchAccounts(criteria, pageable);
            return ResponseEntity.ok(accounts);
            
        } catch (Exception e) {
            log.error("Failed to search accounts", e);
            throw new RuntimeException("Account search failed", e);
        }
    }

    @PostMapping("/{accountId}/debit")
    @Operation(summary = "Debit account", description = "Debits specified amount from account")
    @ApiResponse(responseCode = "200", description = "Account debited successfully")
    @ApiResponse(responseCode = "400", description = "Insufficient funds or invalid request")
    @PreAuthorize("hasRole('SYSTEM')")
    @Traced(operationName = "debit-account", businessOperation = "account-debit", priority = Traced.TracingPriority.CRITICAL)
    public ResponseEntity<AccountBalanceDto> debitAccount(
            @Parameter(description = "Account identifier", required = true)
            @PathVariable @NotBlank String accountId,
            @Valid @RequestBody AccountDebitRequestDto request) {
        
        log.info("Debiting account: {} - {} {} (transaction: {})", 
                accountId, request.getAmount(), request.getCurrency(), request.getTransactionId());
        
        try {
            AccountBalanceDto balance = accountManagementService.debitAccount(accountId, request);
            
            log.info("Successfully debited account: {} - {} {} (new balance: {})", 
                    accountId, request.getAmount(), request.getCurrency(), balance.getCurrentBalance());
            
            return ResponseEntity.ok(balance);
            
        } catch (IllegalArgumentException e) {
            log.warn("Account debit failed for {}: {}", accountId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to debit account: {}", accountId, e);
            throw new RuntimeException("Account debit failed", e);
        }
    }

    @PostMapping("/{accountId}/credit")
    @Operation(summary = "Credit account", description = "Credits specified amount to account")
    @ApiResponse(responseCode = "200", description = "Account credited successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @PreAuthorize("hasRole('SYSTEM')")
    @Traced(operationName = "credit-account", businessOperation = "account-credit", priority = Traced.TracingPriority.CRITICAL)
    public ResponseEntity<AccountBalanceDto> creditAccount(
            @Parameter(description = "Account identifier", required = true)
            @PathVariable @NotBlank String accountId,
            @Valid @RequestBody AccountCreditRequestDto request) {
        
        log.info("Crediting account: {} + {} {} (transaction: {})", 
                accountId, request.getAmount(), request.getCurrency(), request.getTransactionId());
        
        try {
            AccountBalanceDto balance = accountManagementService.creditAccount(accountId, request);
            
            log.info("Successfully credited account: {} + {} {} (new balance: {})", 
                    accountId, request.getAmount(), request.getCurrency(), balance.getCurrentBalance());
            
            return ResponseEntity.ok(balance);
            
        } catch (Exception e) {
            log.error("Failed to credit account: {}", accountId, e);
            throw new RuntimeException("Account credit failed", e);
        }
    }

    /**
     * GDPR Article 17: Right to Erasure (Right to be Forgotten)
     * Anonymizes account data while preserving financial audit trail
     */
    @DeleteMapping("/{accountId}/gdpr-erasure")
    @Operation(summary = "GDPR data erasure",
               description = "Anonymizes user data for GDPR compliance while preserving audit trail")
    @ApiResponse(responseCode = "200", description = "Data anonymized successfully")
    @ApiResponse(responseCode = "400", description = "Account has active balance or pending transactions")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('USER') and @accountService.isAccountOwner(#accountId, authentication.principal.id))")
    @Traced(operationName = "gdpr-erasure", businessOperation = "data-privacy", priority = Traced.TracingPriority.HIGH)
    public ResponseEntity<GdprErasureResponseDto> anonymizeAccountData(
            @Parameter(description = "Account identifier", required = true)
            @PathVariable @NotBlank String accountId,
            @Valid @RequestBody GdprErasureRequestDto request) {

        log.info("GDPR erasure requested for account: {} (reason: {})", accountId, request.getReason());

        try {
            // Validate account exists
            AccountResponseDto account = accountManagementService.getAccount(accountId);

            // GDPR validation: Cannot erase if account has active balance or pending transactions
            if (account.getCurrentBalance().compareTo(BigDecimal.ZERO) != 0) {
                log.warn("GDPR erasure blocked: account {} has non-zero balance: {}",
                    accountId, account.getCurrentBalance());
                return ResponseEntity.badRequest()
                    .body(GdprErasureResponseDto.builder()
                        .success(false)
                        .accountId(accountId)
                        .message("Cannot erase account with non-zero balance. Please withdraw all funds first.")
                        .currentBalance(account.getCurrentBalance())
                        .build());
            }

            // Perform anonymization (preserves audit trail, removes PII)
            accountManagementService.anonymizeAccountForGdpr(accountId, request);

            log.info("GDPR erasure completed successfully for account: {}", accountId);

            return ResponseEntity.ok(GdprErasureResponseDto.builder()
                .success(true)
                .accountId(accountId)
                .message("Account data anonymized successfully. Audit trail preserved for regulatory compliance.")
                .anonymizedAt(java.time.Instant.now())
                .build());

        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                log.warn("GDPR erasure failed: account not found: {}", accountId);
                return ResponseEntity.notFound().build();
            }
            log.error("Failed to anonymize account data: {}", accountId, e);
            throw new RuntimeException("GDPR erasure failed", e);
        }
    }

    /**
     * GDPR Article 20: Right to Data Portability
     * Exports all user data in machine-readable format
     */
    @GetMapping("/{accountId}/gdpr-export")
    @Operation(summary = "GDPR data export",
               description = "Exports all account data in machine-readable format")
    @ApiResponse(responseCode = "200", description = "Data exported successfully")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('USER') and @accountService.isAccountOwner(#accountId, authentication.principal.id))")
    @Traced(operationName = "gdpr-export", businessOperation = "data-privacy", priority = Traced.TracingPriority.MEDIUM)
    public ResponseEntity<GdprDataExportDto> exportAccountData(
            @Parameter(description = "Account identifier", required = true)
            @PathVariable @NotBlank String accountId) {

        log.info("GDPR data export requested for account: {}", accountId);

        try {
            GdprDataExportDto exportData = accountManagementService.exportAccountDataForGdpr(accountId);

            log.info("GDPR data export completed for account: {}", accountId);

            return ResponseEntity.ok(exportData);

        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                log.warn("GDPR export failed: account not found: {}", accountId);
                return ResponseEntity.notFound().build();
            }
            log.error("Failed to export account data: {}", accountId, e);
            throw new RuntimeException("GDPR export failed", e);
        }
    }
}