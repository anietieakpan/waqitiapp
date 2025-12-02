package com.waqiti.bankintegration.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.waqiti.common.kyc.service.KYCClientService;
import com.waqiti.common.kyc.annotation.RequireKYCVerification;
import com.waqiti.common.kyc.annotation.RequireKYCVerification.VerificationLevel;
import com.waqiti.common.security.SecurityContextUtil;
import com.waqiti.common.security.ResourceOwnershipAspect.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/banking/accounts")
@RequiredArgsConstructor
@Slf4j
public class BankAccountController {

    private final KYCClientService kycClientService;

    @PostMapping("/link")
    @PreAuthorize("hasRole('USER')")
    @RequireKYCVerification(level = VerificationLevel.INTERMEDIATE, action = "BANK_ACCOUNT_LINK")
    public ResponseEntity<Map<String, Object>> linkBankAccount(@RequestBody @Valid LinkAccountRequest request) {
        
        // SECURITY FIX: Override user ID with authenticated user
        String authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId().toString();
        request.setUserId(authenticatedUserId);
        log.info("Linking bank account for user: {}", request.getUserId());
        
        try {
            // Simulate account linking process
            String accountId = java.util.UUID.randomUUID().toString();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "accountId", accountId,
                "accountNumber", maskAccountNumber(request.getAccountNumber()),
                "bankName", request.getBankName(),
                "accountType", request.getAccountType(),
                "status", "pending_verification",
                "message", "Bank account linked successfully. Verification required."
            ));
        } catch (Exception e) {
            log.error("Failed to link bank account", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/verify")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.BANK_ACCOUNT, resourceIdParam = "accountId", operation = "VERIFY")
    public ResponseEntity<Map<String, Object>> verifyBankAccount(@RequestBody @Valid VerifyAccountRequest request) {
        log.info("Verifying bank account: {}", request.getAccountId());
        
        try {
            // Simulate micro-deposit verification
            boolean verified = request.getDeposit1().compareTo(BigDecimal.valueOf(0.01)) == 0 &&
                             request.getDeposit2().compareTo(BigDecimal.valueOf(0.02)) == 0;
            
            if (verified) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "accountId", request.getAccountId(),
                    "status", "verified",
                    "verifiedAt", LocalDateTime.now(),
                    "message", "Bank account verified successfully"
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Invalid verification amounts. Please check and try again."
                ));
            }
        } catch (Exception e) {
            log.error("Failed to verify bank account", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/{userId}")
    @PreAuthorize("@userOwnershipValidator.isCurrentUser(authentication.name, #userId) or hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getUserBankAccounts(@PathVariable String userId) {
        
        // SECURITY FIX: Override user ID with authenticated user for non-admin
        String authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId().toString();
        if (!SecurityContextUtil.hasRole("ADMIN")) {
            userId = authenticatedUserId;
        }
        log.info("Getting bank accounts for user: {}", userId);
        
        try {
            // Simulate fetching user's bank accounts
            List<Map<String, Object>> accounts = List.of(
                Map.of(
                    "accountId", "acc_123456",
                    "accountNumber", "****1234",
                    "bankName", "Chase Bank",
                    "accountType", "checking",
                    "status", "verified",
                    "isPrimary", true,
                    "linkedAt", LocalDateTime.now().minusDays(30)
                ),
                Map.of(
                    "accountId", "acc_789012",
                    "accountNumber", "****5678",
                    "bankName", "Bank of America",
                    "accountType", "savings",
                    "status", "verified",
                    "isPrimary", false,
                    "linkedAt", LocalDateTime.now().minusDays(15)
                )
            );
            
            return ResponseEntity.ok(accounts);
        } catch (Exception e) {
            log.error("Failed to get bank accounts", e);
            return ResponseEntity.badRequest().body(List.of());
        }
    }

    @GetMapping("/{accountId}/balance")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @ValidateOwnership(resourceType = ResourceType.BANK_ACCOUNT, resourceIdParam = "accountId", operation = "VIEW_BALANCE")
    public ResponseEntity<Map<String, Object>> getAccountBalance(@PathVariable String accountId) {
        log.info("Getting balance for account: {}", accountId);
        
        try {
            // Simulate balance check
            return ResponseEntity.ok(Map.of(
                "accountId", accountId,
                "availableBalance", BigDecimal.valueOf(2500.75),
                "currentBalance", BigDecimal.valueOf(2750.75),
                "currency", "USD",
                "lastUpdated", LocalDateTime.now(),
                "accountType", "checking"
            ));
        } catch (Exception e) {
            log.error("Failed to get account balance", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/{accountId}/transactions")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @ValidateOwnership(resourceType = ResourceType.BANK_ACCOUNT, resourceIdParam = "accountId", operation = "VIEW_TRANSACTIONS")
    public ResponseEntity<List<Map<String, Object>>> getAccountTransactions(
            @PathVariable String accountId,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        log.info("Getting transactions for account: {} for last {} days", accountId, days);
        
        try {
            // Simulate transaction history
            List<Map<String, Object>> transactions = List.of(
                Map.of(
                    "transactionId", "txn_123",
                    "date", LocalDateTime.now().minusDays(1),
                    "description", "ACH Credit from WAQITI",
                    "amount", BigDecimal.valueOf(100.00),
                    "type", "credit",
                    "category", "transfer",
                    "balance", BigDecimal.valueOf(2750.75)
                ),
                Map.of(
                    "transactionId", "txn_124",
                    "date", LocalDateTime.now().minusDays(2),
                    "description", "Grocery Store Purchase",
                    "amount", BigDecimal.valueOf(-45.67),
                    "type", "debit",
                    "category", "purchase",
                    "balance", BigDecimal.valueOf(2650.75)
                )
            );
            
            return ResponseEntity.ok(transactions);
        } catch (Exception e) {
            log.error("Failed to get account transactions", e);
            return ResponseEntity.badRequest().body(List.of());
        }
    }

    @PostMapping("/{accountId}/transfer")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.BANK_ACCOUNT, resourceIdParam = "accountId", operation = "TRANSFER")
    @RequireKYCVerification(level = VerificationLevel.BASIC, action = "BANK_TRANSFER")
    @RequiresMFA(threshold = 500) // CRITICAL P0 FIX: Lowered from $1000 to $500 for enhanced security
    @FraudCheck(level = FraudCheckLevel.CRITICAL)
    public ResponseEntity<Map<String, Object>> initiateTransfer(@PathVariable String accountId,
                                                               @RequestBody @Valid TransferRequest request) {
        
        // SECURITY FIX: Override user ID with authenticated user
        String authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId().toString();
        request.setUserId(authenticatedUserId);
        log.info("Initiating transfer from account: {} amount: {}", accountId, request.getAmount());
        
        // Enhanced KYC check for high-value bank transfers
        if (request.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            if (!kycClientService.canUserMakeHighValueTransfer(request.getUserId())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Enhanced KYC verification required for bank transfers over $10,000",
                    "errorCode", "KYC_VERIFICATION_REQUIRED"
                ));
            }
        }
        
        try {
            String transferId = java.util.UUID.randomUUID().toString();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "transferId", transferId,
                "fromAccount", accountId,
                "toAccount", request.getToAccount(),
                "amount", request.getAmount(),
                "currency", request.getCurrency(),
                "status", "pending",
                "estimatedCompletion", LocalDateTime.now().plusDays(1),
                "feeAmount", BigDecimal.valueOf(0.25),
                "message", "Transfer initiated successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to initiate transfer", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/{accountId}/set-primary")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.BANK_ACCOUNT, resourceIdParam = "accountId", operation = "SET_PRIMARY")
    public ResponseEntity<Map<String, Object>> setPrimaryAccount(@PathVariable String accountId,
                                                                @RequestBody @Valid SetPrimaryRequest request) {
        
        // SECURITY FIX: Override user ID with authenticated user
        String authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId().toString();
        request.setUserId(authenticatedUserId);
        log.info("Setting primary account: {} for user: {}", accountId, request.getUserId());
        
        try {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "accountId", accountId,
                "userId", request.getUserId(),
                "isPrimary", true,
                "updatedAt", LocalDateTime.now(),
                "message", "Primary account updated successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to set primary account", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/{accountId}")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.BANK_ACCOUNT, resourceIdParam = "accountId", operation = "UNLINK")
    public ResponseEntity<Map<String, Object>> unlinkBankAccount(@PathVariable String accountId,
                                                                @RequestBody @Valid UnlinkAccountRequest request) {
        
        // SECURITY FIX: Override user ID with authenticated user
        String authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId().toString();
        request.setUserId(authenticatedUserId);
        log.info("Unlinking bank account: {} for user: {}", accountId, request.getUserId());
        
        try {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "accountId", accountId,
                "userId", request.getUserId(),
                "status", "unlinked",
                "unlinkedAt", LocalDateTime.now(),
                "message", "Bank account unlinked successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to unlink bank account", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/supported-banks")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<Map<String, Object>>> getSupportedBanks(@RequestParam(required = false) String country) {
        log.info("Getting supported banks for country: {}", country);
        
        List<Map<String, Object>> banks = List.of(
            Map.of(
                "bankId", "chase",
                "bankName", "JPMorgan Chase Bank",
                "country", "US",
                "supportedAccountTypes", List.of("checking", "savings"),
                "features", List.of("instant_verification", "ach_transfers")
            ),
            Map.of(
                "bankId", "bofa",
                "bankName", "Bank of America",
                "country", "US",
                "supportedAccountTypes", List.of("checking", "savings"),
                "features", List.of("instant_verification", "ach_transfers")
            ),
            Map.of(
                "bankId", "wells_fargo",
                "bankName", "Wells Fargo Bank",
                "country", "US",
                "supportedAccountTypes", List.of("checking", "savings"),
                "features", List.of("instant_verification", "ach_transfers")
            )
        );
        
        return ResponseEntity.ok(banks);
    }

    @PostMapping("/instant-verification")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> instantVerification(@RequestBody @Valid InstantVerificationRequest request) {
        
        // SECURITY FIX: Override user ID with authenticated user
        String authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId().toString();
        request.setUserId(authenticatedUserId);
        log.info("Starting instant verification for user: {}", request.getUserId());
        
        try {
            String verificationId = java.util.UUID.randomUUID().toString();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "verificationId", verificationId,
                "status", "in_progress",
                "redirectUrl", "https://verification.provider.com/verify?id=" + verificationId,
                "expiresAt", LocalDateTime.now().plusMinutes(15),
                "message", "Instant verification initiated"
            ));
        } catch (Exception e) {
            log.error("Failed to start instant verification", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/verification/{verificationId}/status")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getVerificationStatus(@PathVariable String verificationId) {
        log.info("Checking verification status: {}", verificationId);
        
        try {
            return ResponseEntity.ok(Map.of(
                "verificationId", verificationId,
                "status", "completed",
                "accountsLinked", 1,
                "completedAt", LocalDateTime.now(),
                "accounts", List.of(
                    Map.of(
                        "accountId", "acc_" + java.util.UUID.randomUUID().toString(),
                        "accountNumber", "****1234",
                        "bankName", "Chase Bank",
                        "accountType", "checking"
                    )
                )
            ));
        } catch (Exception e) {
            log.error("Failed to get verification status", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }
}

