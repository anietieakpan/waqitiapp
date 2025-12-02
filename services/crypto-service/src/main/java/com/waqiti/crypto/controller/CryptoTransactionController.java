/**
 * Crypto Transaction Controller
 * REST API endpoints for cryptocurrency transactions
 */
package com.waqiti.crypto.controller;

import com.waqiti.crypto.dto.request.BuyCryptocurrencyRequest;
import com.waqiti.crypto.dto.request.SellCryptocurrencyRequest;
import com.waqiti.crypto.dto.request.SendCryptocurrencyRequest;
import com.waqiti.crypto.dto.response.TransactionResponse;
import com.waqiti.crypto.entity.TransactionStatus;
import com.waqiti.crypto.service.CryptoTransactionService;
import com.waqiti.crypto.security.CryptoTransactionMfaService;
import com.waqiti.crypto.security.CryptoTransactionMfaService.*;
import com.waqiti.common.ratelimit.RateLimited;
import com.waqiti.common.security.SecurityContextUtil;
import com.waqiti.common.security.ResourceOwnershipAspect.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Crypto Transactions", description = "Cryptocurrency transaction endpoints with mandatory 2FA")
public class CryptoTransactionController {
    
    private final CryptoTransactionService transactionService;
    private final CryptoTransactionMfaService mfaService;
    
    @PostMapping("/send/initiate")
    @Operation(summary = "Initiate cryptocurrency send", 
               description = "Initiates a cryptocurrency send transaction with mandatory 2FA for high-value transfers")
    @PreAuthorize("hasRole('USER')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 60)
    public ResponseEntity<CryptoTransactionInitiationResponse> initiateSendCrypto(
            @Valid @RequestBody SendCryptocurrencyRequest request) {
        
        // SECURITY FIX: Get user ID from security context instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();
        
        log.info("Initiating send transaction for user: {} to address: {} amount: {} {}", 
            userId, request.getToAddress(), request.getAmount(), request.getCurrency());
        request.setUserId(userId);
        
        // Check MFA requirement
        MfaRequirement mfaRequirement = mfaService.determineMfaRequirement(
            userId, request.getAmount(), request.getCurrency(), request.getToAddress());
        
        if (mfaRequirement.isRequired()) {
            // Generate transaction ID for tracking
            UUID transactionId = UUID.randomUUID();
            
            // Store pending transaction
            transactionService.storePendingTransaction(transactionId, request);
            
            // Generate MFA challenge
            MfaChallenge challenge = mfaService.generateMfaChallenge(userId, transactionId, mfaRequirement);
            
            return ResponseEntity.ok(CryptoTransactionInitiationResponse.builder()
                .transactionId(transactionId)
                .status("MFA_REQUIRED")
                .mfaRequired(true)
                .mfaChallenge(challenge)
                .message("High-value transaction requires additional verification")
                .build());
        } else {
            // Low value to whitelisted address - process immediately
            TransactionResponse transaction = transactionService.sendCryptocurrency(request);
            return ResponseEntity.ok(CryptoTransactionInitiationResponse.builder()
                .transactionId(transaction.getTransactionId())
                .status("COMPLETED")
                .mfaRequired(false)
                .transaction(transaction)
                .message("Transaction processed successfully")
                .build());
        }
    }
    
    @PostMapping("/send/confirm")
    @Operation(summary = "Confirm cryptocurrency send with 2FA", 
               description = "Confirms a pending cryptocurrency transaction with 2FA verification")
    @PreAuthorize("hasRole('USER')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 5, refillTokens = 5, refillPeriodMinutes = 15)
    public ResponseEntity<TransactionResponse> confirmSendCrypto(
            @RequestParam UUID transactionId,
            @RequestParam String challengeId,
            @Valid @RequestBody Map<MfaMethod, String> mfaResponses) {
        
        // SECURITY FIX: Get user ID from security context instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();
        
        log.info("Confirming send transaction {} with MFA for user: {}", transactionId, userId);
        
        // Verify MFA
        MfaVerificationResult verificationResult = mfaService.verifyMfaResponse(challengeId, mfaResponses);
        
        if (!verificationResult.isSuccess()) {
            if (verificationResult.isAccountLocked()) {
                return ResponseEntity.status(HttpStatus.LOCKED)
                    .body(TransactionResponse.builder()
                        .status(TransactionStatus.BLOCKED)
                        .error("Account temporarily locked due to multiple failed attempts")
                        .build());
            }
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(TransactionResponse.builder()
                    .status(TransactionStatus.FAILED)
                    .error(verificationResult.getErrorMessage())
                    .attemptsRemaining(verificationResult.getAttemptsRemaining())
                    .build());
        }
        
        // MFA verified - process transaction
        SendCryptocurrencyRequest request = transactionService.getPendingTransaction(transactionId);
        if (request == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(TransactionResponse.builder()
                    .status(TransactionStatus.FAILED)
                    .error("Transaction not found or expired")
                    .build());
        }
        
        // Verify user owns this transaction
        if (!request.getUserId().equals(userId)) {
            log.warn("User {} attempted to confirm transaction owned by {}", userId, request.getUserId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(TransactionResponse.builder()
                    .status(TransactionStatus.FAILED)
                    .error("Unauthorized access to transaction")
                    .build());
        }
        
        // Process the verified transaction
        TransactionResponse transaction = transactionService.processVerifiedTransaction(
            transactionId, request, verificationResult.getTransactionToken());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(transaction);
    }
    
    @PostMapping("/buy")
    @Operation(summary = "Buy cryptocurrency", description = "Purchase cryptocurrency with fiat currency")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TransactionResponse> buyCrypto(
            @Valid @RequestBody BuyCryptocurrencyRequest request) {
        
        // SECURITY FIX: Get user ID from security context instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();
        
        log.info("Processing buy transaction for user: {} amount: {} USD", userId, request.getFiatAmount());
        request.setUserId(userId);
        
        TransactionResponse transaction = transactionService.buyCryptocurrency(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(transaction);
    }
    
    @PostMapping("/sell")
    @Operation(summary = "Sell cryptocurrency", description = "Sell cryptocurrency for fiat currency")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TransactionResponse> sellCrypto(
            @Valid @RequestBody SellCryptocurrencyRequest request) {
        
        // SECURITY FIX: Get user ID from security context instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();
        
        log.info("Processing sell transaction for user: {} amount: {} {}", 
                userId, request.getAmount(), request.getCurrency());
        request.setUserId(userId);
        
        TransactionResponse transaction = transactionService.sellCryptocurrency(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(transaction);
    }
    
    @GetMapping("/{transactionId}")
    @Operation(summary = "Get transaction details", description = "Retrieves transaction information by ID")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.CRYPTO_TRANSACTION, resourceIdParam = "transactionId", operation = "VIEW")
    public ResponseEntity<TransactionResponse> getTransaction(
            @PathVariable UUID transactionId) {
        
        // SECURITY FIX: Get user ID from security context instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();
        
        log.info("Fetching transaction: {} for user: {}", transactionId, userId);
        TransactionResponse transaction = transactionService.getTransaction(transactionId, userId);
        return ResponseEntity.ok(transaction);
    }
    
    @GetMapping
    @Operation(summary = "List transactions", description = "Retrieves user transaction history")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<TransactionResponse>> getTransactions(
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Pageable pageable) {
        
        // SECURITY FIX: Get user ID from security context instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();
        
        log.info("Listing transactions for user: {} with filters - status: {}, currency: {}", 
                userId, status, currency);
        
        Page<TransactionResponse> transactions = transactionService.getUserTransactions(
                userId, status, currency, startDate, endDate, pageable);
        return ResponseEntity.ok(transactions);
    }
    
    @GetMapping("/wallet/{walletId}")
    @Operation(summary = "List wallet transactions", description = "Retrieves transaction history for a specific wallet")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.CRYPTO_WALLET, resourceIdParam = "walletId", operation = "VIEW_TRANSACTIONS")
    public ResponseEntity<Page<TransactionResponse>> getWalletTransactions(
            @PathVariable UUID walletId,
            Pageable pageable) {
        
        // SECURITY FIX: Get user ID from security context instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();
        
        log.info("Listing transactions for wallet: {}", walletId);
        Page<TransactionResponse> transactions = transactionService.getWalletTransactions(
                walletId, userId, pageable);
        return ResponseEntity.ok(transactions);
    }
    
    @PostMapping("/{transactionId}/cancel")
    @Operation(summary = "Cancel transaction", description = "Cancels a pending transaction")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.CRYPTO_TRANSACTION, resourceIdParam = "transactionId", operation = "CANCEL")
    public ResponseEntity<TransactionResponse> cancelTransaction(
            @PathVariable UUID transactionId,
            @RequestParam String reason) {
        
        // SECURITY FIX: Get user ID from security context instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();
        
        log.info("Cancelling transaction: {} for user: {}", transactionId, userId);
        TransactionResponse transaction = transactionService.cancelTransaction(transactionId, userId, reason);
        return ResponseEntity.ok(transaction);
    }
    
    @PostMapping("/{transactionId}/retry")
    @Operation(summary = "Retry transaction", description = "Retries a failed transaction")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.CRYPTO_TRANSACTION, resourceIdParam = "transactionId", operation = "RETRY")
    public ResponseEntity<TransactionResponse> retryTransaction(
            @PathVariable UUID transactionId) {
        
        // SECURITY FIX: Get user ID from security context instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();
        
        log.info("Retrying transaction: {} for user: {}", transactionId, userId);
        TransactionResponse transaction = transactionService.retryTransaction(transactionId, userId);
        return ResponseEntity.ok(transaction);
    }
    
    @GetMapping("/estimate-fee")
    @Operation(summary = "Estimate transaction fee", description = "Estimates fee for a cryptocurrency transaction")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<FeeEstimate> estimateFee(
            @RequestParam String currency,
            @RequestParam String toAddress,
            @RequestParam String amount,
            @RequestParam(required = false, defaultValue = "false") boolean expedited) {
        
        log.info("Estimating fee for {} {} to {}", amount, currency, toAddress);
        FeeEstimate estimate = transactionService.estimateFee(currency, toAddress, amount, expedited);
        return ResponseEntity.ok(estimate);
    }
    
    /**
     * Whitelist Management with 2FA
     */
    @PostMapping("/whitelist/add")
    @Operation(summary = "Add address to whitelist", 
               description = "Add a cryptocurrency address to whitelist with 2FA verification")
    @PreAuthorize("hasRole('USER')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 5, refillTokens = 5, refillPeriodMinutes = 60)
    public ResponseEntity<WhitelistResult> addToWhitelist(
            @RequestParam String address,
            @RequestParam String label,
            @RequestParam String verificationCode) {
        
        // SECURITY FIX: Get user ID from security context instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();
        
        log.info("Adding address to whitelist for user {}: {}", userId, address);
        
        WhitelistResult result = mfaService.addToWhitelist(userId, address, label, verificationCode);
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    @PostMapping("/whitelist/request-code")
    @Operation(summary = "Request whitelist verification code", 
               description = "Request a verification code for whitelist operations")
    @PreAuthorize("hasRole('USER')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 3, refillTokens = 3, refillPeriodMinutes = 15)
    public ResponseEntity<Map<String, String>> requestWhitelistCode() {
        
        // SECURITY FIX: Get user ID from security context instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();
        
        log.info("Requesting whitelist verification code for user {}", userId);
        
        // Generate and send verification code
        MfaRequirement requirement = MfaRequirement.builder()
            .required(true)
            .level(MfaLevel.STANDARD)
            .requiredMethods(Arrays.asList(MfaMethod.SMS))
            .build();
        
        MfaChallenge challenge = mfaService.generateMfaChallenge(
            userId, UUID.randomUUID(), requirement);
        
        return ResponseEntity.ok(Map.of(
            "message", "Verification code sent to your registered phone",
            "challengeId", challenge.getChallengeId(),
            "expiresIn", "5 minutes"
        ));
    }
    
    /**
     * Hardware Wallet Integration
     */
    @PostMapping("/hardware-wallet/sign")
    @Operation(summary = "Request hardware wallet signing", 
               description = "Generate signing request for hardware wallet transactions")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.CRYPTO_TRANSACTION, resourceIdParam = "transactionId", operation = "SIGN")
    public ResponseEntity<TransactionSigningRequest> requestHardwareWalletSigning(
            @RequestParam UUID transactionId,
            @Valid @RequestBody SendCryptocurrencyRequest request) {
        
        // SECURITY FIX: Get user ID from security context instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();
        
        log.info("Generating hardware wallet signing request for transaction {}", transactionId);
        
        TransactionSigningRequest signingRequest = mfaService.generateSigningRequest(
            userId, transactionId, request);
        
        return ResponseEntity.ok(signingRequest);
    }
    
    // Response Classes
    
    @Data
    @Builder
    public static class CryptoTransactionInitiationResponse {
        private UUID transactionId;
        private String status;
        private boolean mfaRequired;
        private MfaChallenge mfaChallenge;
        private TransactionResponse transaction;
        private String message;
    }
    
    @Data
    @Builder
    public static class FeeEstimate {
        private BigDecimal estimatedFee;
        private BigDecimal totalAmount;
        private String currency;
        private Integer estimatedConfirmationMinutes;
        private BigDecimal currentGasPrice;
    }
}