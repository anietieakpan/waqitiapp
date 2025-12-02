package com.waqiti.transaction.controller;

import com.waqiti.common.api.ApiResponse;
import com.waqiti.transaction.dto.*;
import com.waqiti.transaction.service.TransactionProcessingService;
import com.waqiti.transaction.service.TransactionAnalyticsService;
import com.waqiti.common.ratelimit.RateLimited;
import com.waqiti.common.security.ResourceOwnershipAspect.*;
import com.waqiti.common.validation.ValidatedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Transaction Management", description = "Transaction processing and management operations")
@Validated
public class TransactionController {

    private final TransactionProcessingService transactionProcessingService;
    private final TransactionAnalyticsService analyticsService;
    private final WalletOwnershipValidator walletOwnershipValidator;
    private final AuditService auditService;

    @PostMapping("/transfer")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 5)
    @Operation(summary = "Initiate P2P transfer")
    @PreAuthorize("hasAuthority('PAYMENT_CREATE') and @walletOwnershipValidator.canAccessWallet(#request.fromWalletId, authentication.name)")
    @AuditLog(action = "TRANSFER_INITIATED", level = AuditLevel.HIGH)
    @ValidateTransaction
    public ResponseEntity<ApiResponse<TransactionResponse>> initiateTransfer(
            @Valid @RequestBody TransferRequest request) {
        log.info("Initiating transfer: {} -> {}, amount: {}", 
                request.getFromWalletId(), request.getToWalletId(), request.getAmount());
        
        TransactionResponse response = transactionProcessingService.initiateTransfer(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/deposit")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 20, refillTokens = 20, refillPeriodMinutes = 5)
    @Operation(summary = "Initiate deposit")
    @PreAuthorize("hasAuthority('DEPOSIT_CREATE') and @walletOwnershipValidator.canAccessWallet(#request.walletId, authentication.name)")
    @AuditLog(action = "DEPOSIT_INITIATED", level = AuditLevel.HIGH)
    @RequiresMFA(threshold = 10000)
    public ResponseEntity<ApiResponse<TransactionResponse>> initiateDeposit(
            @Valid @RequestBody DepositRequest request) {
        log.info("Initiating deposit to wallet: {}, amount: {}", 
                request.getWalletId(), request.getAmount());
        
        TransactionResponse response = transactionProcessingService.initiateDeposit(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/withdrawal")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 15, refillTokens = 15, refillPeriodMinutes = 10)
    @Operation(summary = "Initiate withdrawal")
    @PreAuthorize("hasAuthority('WITHDRAWAL_CREATE') and @walletOwnershipValidator.canAccessWallet(#request.walletId, authentication.name) and @dailyLimitValidator.canWithdraw(#request.walletId, #request.amount)")
    @AuditLog(action = "WITHDRAWAL_INITIATED", level = AuditLevel.CRITICAL)
    @RequiresMFA(threshold = 5000)
    @FraudCheck(level = FraudCheckLevel.HIGH)
    public ResponseEntity<ApiResponse<TransactionResponse>> initiateWithdrawal(
            @Valid @RequestBody WithdrawalRequest request) {
        log.info("Initiating withdrawal from wallet: {}, amount: {}", 
                request.getWalletId(), request.getAmount());
        
        TransactionResponse response = transactionProcessingService.initiateWithdrawal(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 100, refillTokens = 100, refillPeriodMinutes = 1)
    @Operation(summary = "Get transactions with filters")
    @PreAuthorize("hasRole('USER') and (T(org.springframework.util.StringUtils).isEmpty(#walletId) or @walletOwnershipValidator.canAccessWallet(authentication.name, #walletId))")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getTransactions(
            @RequestParam(required = false) String walletId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable,
            Authentication authentication) {

        // Additional runtime ownership validation for defense in depth
        if (walletId != null && !walletOwnershipValidator.canAccessWallet(authentication.getName(), walletId)) {
            auditService.logSecurityViolation(
                "IDOR_ATTEMPT",
                authentication.getName(),
                Map.of("attemptedWalletId", walletId, "endpoint", "GET /transactions")
            );
            throw new AccessDeniedException("Access denied to wallet: " + walletId);
        }

        TransactionFilter filter = TransactionFilter.builder()
                .walletId(walletId)
                .type(type)
                .status(status)
                .startDate(startDate)
                .endDate(endDate)
                .userId(authentication.getName()) // Filter by authenticated user
                .build();

        Page<TransactionResponse> transactions = transactionProcessingService.getTransactions(filter, pageable);
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }

    @GetMapping("/{transactionId}")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 200, refillTokens = 200, refillPeriodMinutes = 1)
    @Operation(summary = "Get transaction details")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.TRANSACTION, resourceIdParam = "transactionId", operation = "VIEW")
    public ResponseEntity<ApiResponse<TransactionDetailResponse>> getTransactionDetails(
            @PathVariable UUID transactionId) {
        
        TransactionDetailResponse transaction = transactionProcessingService.getTransactionDetails(transactionId);
        return ResponseEntity.ok(ApiResponse.success(transaction));
    }

    @PostMapping("/{transactionId}/retry")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 5, refillTokens = 5, refillPeriodMinutes = 15)
    @Operation(summary = "Retry failed transaction")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.TRANSACTION, resourceIdParam = "transactionId", operation = "RETRY")
    public ResponseEntity<ApiResponse<TransactionResponse>> retryTransaction(
            @PathVariable UUID transactionId) {
        log.info("Retrying transaction: {}", transactionId);
        
        TransactionResponse response = transactionProcessingService.retryTransaction(transactionId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{transactionId}/cancel")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 20, refillTokens = 20, refillPeriodMinutes = 1)
    @Operation(summary = "Cancel pending transaction")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.TRANSACTION, resourceIdParam = "transactionId", operation = "CANCEL")
    public ResponseEntity<ApiResponse<Void>> cancelTransaction(
            @PathVariable UUID transactionId,
            @RequestBody(required = false) @jakarta.validation.Valid CancelTransactionRequest request) {  // ✅ ADDED @Valid
        log.info("Cancelling transaction: {}", transactionId);

        String reason = request != null ? request.getReason() : null;
        transactionProcessingService.cancelTransaction(transactionId, reason);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/summary")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 50, refillTokens = 50, refillPeriodMinutes = 1)
    @Operation(summary = "Get transaction summary")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<TransactionSummaryResponse>> getTransactionSummary(
            @RequestParam(required = false) String walletId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        TransactionSummaryResponse summary = analyticsService.getTransactionSummary(walletId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @GetMapping("/analytics/spending")
    @Operation(summary = "Get spending analytics")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<SpendingAnalyticsResponse>> getSpendingAnalytics(
            @RequestParam String period,
            @RequestParam(required = false) String walletId) {
        
        SpendingAnalyticsResponse analytics = analyticsService.getSpendingAnalytics(period, walletId);
        return ResponseEntity.ok(ApiResponse.success(analytics));
    }

    @PostMapping("/bulk/export")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 5, refillTokens = 5, refillPeriodMinutes = 60, tokens = 3)
    @Operation(summary = "Export transactions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<byte[]> exportTransactions(
            @Valid @RequestBody ExportTransactionsRequest request) {
        
        byte[] exportData = transactionProcessingService.exportTransactions(request);
        
        String contentType = "csv".equals(request.getFormat()) ? 
                "text/csv" : "application/pdf";
        String filename = "transactions." + request.getFormat();
        
        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .header("Content-Disposition", "attachment; filename=" + filename)
                .body(exportData);
    }

    @GetMapping("/{transactionId}/receipt")
    @Operation(summary = "Download transaction receipt")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<byte[]> downloadReceipt(
            @PathVariable UUID transactionId,
            @RequestParam(required = false, defaultValue = "STANDARD") String format,
            @RequestParam(required = false, defaultValue = "true") boolean includeDetailedFees,
            @RequestParam(required = false, defaultValue = "false") boolean includeTimeline,
            @RequestParam(required = false, defaultValue = "true") boolean includeQrCode,
            @RequestParam(required = false, defaultValue = "true") boolean includeWatermark,
            @RequestParam(required = false, defaultValue = "false") boolean includeComplianceInfo) {
        
        ReceiptGenerationOptions options = ReceiptGenerationOptions.builder()
                .format(ReceiptGenerationOptions.ReceiptFormat.valueOf(format))
                .includeDetailedFees(includeDetailedFees)
                .includeTimeline(includeTimeline)
                .includeQrCode(includeQrCode)
                .includeWatermark(includeWatermark)
                .includeComplianceInfo(includeComplianceInfo)
                .build();
        
        byte[] receiptData = transactionProcessingService.generateReceipt(transactionId, options);
        
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=receipt-" + transactionId + ".pdf")
                .body(receiptData);
    }

    @PostMapping("/{transactionId}/receipt/generate")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 20, refillTokens = 20, refillPeriodMinutes = 5)
    @Operation(summary = "Generate and store receipt with metadata")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ReceiptMetadata>> generateAndStoreReceipt(
            @PathVariable UUID transactionId,
            @RequestBody(required = false) @jakarta.validation.Valid ReceiptGenerationOptions options) {  // ✅ ADDED @Valid

        ReceiptMetadata metadata = transactionProcessingService.generateAndStoreReceipt(
                transactionId,
                options != null ? options : ReceiptGenerationOptions.builder().build());

        return ResponseEntity.ok(ApiResponse.success(metadata));
    }

    @PostMapping("/{transactionId}/receipt/email")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 15)
    @Operation(summary = "Email receipt to specified address")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Boolean>> emailReceipt(
            @PathVariable UUID transactionId,
            @Valid @RequestBody EmailReceiptRequest request) {
        
        boolean success = transactionProcessingService.emailReceipt(transactionId, request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(success));
    }

    @PostMapping("/{transactionId}/receipt/access-token")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 5, refillTokens = 5, refillPeriodMinutes = 60)
    @Operation(summary = "Generate secure access token for receipt sharing")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<String>> generateReceiptAccessToken(
            @PathVariable UUID transactionId,
            @Valid @RequestBody ReceiptAccessTokenRequest request) {
        
        String token = transactionProcessingService.generateReceiptAccessToken(
                transactionId, 
                request.getEmail(), 
                request.getValidityHours());
        
        return ResponseEntity.ok(ApiResponse.success(token));
    }

    @GetMapping("/{transactionId}/receipts")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 50, refillTokens = 50, refillPeriodMinutes = 1)
    @Operation(summary = "Get receipt history for transaction")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<ReceiptMetadata>>> getReceiptHistory(@PathVariable UUID transactionId) {
        
        List<ReceiptMetadata> history = transactionProcessingService.getReceiptHistory(transactionId);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @PostMapping("/receipts/verify")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 20, refillTokens = 20, refillPeriodMinutes = 5)
    @Operation(summary = "Verify receipt integrity")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ReceiptSecurityValidation>> verifyReceipt(
            @RequestParam("receiptFile") MultipartFile receiptFile,
            @RequestParam UUID transactionId,
            @RequestParam(required = false) String expectedHash) {
        
        try {
            byte[] receiptData = receiptFile.getBytes();
            ReceiptSecurityValidation validation = transactionProcessingService.verifyReceiptIntegrity(
                    receiptData, transactionId, expectedHash);
            
            return ResponseEntity.ok(ApiResponse.success(validation));
        } catch (IOException e) {
            log.error("Error reading receipt file", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to read receipt file"));
        }
    }

    @PostMapping("/receipts/bulk-download")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 5, refillTokens = 5, refillPeriodMinutes = 15)
    @Operation(summary = "Download multiple receipts as ZIP file")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<byte[]> bulkDownloadReceipts(@Valid @RequestBody BulkReceiptRequest request) {
        
        byte[] zipData = transactionProcessingService.bulkDownloadReceipts(
                request.getTransactionIds(), 
                request.getOptions());
        
        return ResponseEntity.ok()
                .header("Content-Type", "application/zip")
                .header("Content-Disposition", "attachment; filename=receipts-bulk-" + System.currentTimeMillis() + ".zip")
                .body(zipData);
    }

    @GetMapping("/receipts/analytics")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 20, refillTokens = 20, refillPeriodMinutes = 5)
    @Operation(summary = "Get receipt generation analytics")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ReceiptAnalytics>> getReceiptAnalytics(
            @RequestParam(required = false, defaultValue = "month") String timeframe) {
        
        ReceiptAnalytics analytics = transactionProcessingService.getReceiptAnalytics(timeframe);
        return ResponseEntity.ok(ApiResponse.success(analytics));
    }

    @PostMapping("/{transactionId}/dispute")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 3, refillTokens = 3, refillPeriodMinutes = 60)
    @Operation(summary = "Dispute transaction")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<DisputeResponse>> disputeTransaction(
            @PathVariable UUID transactionId,
            @Valid @RequestBody DisputeRequest request) {
        log.info("Creating dispute for transaction: {}", transactionId);
        
        DisputeResponse dispute = transactionProcessingService.createDispute(transactionId, request);
        return ResponseEntity.ok(ApiResponse.success(dispute));
    }

    @GetMapping("/recurring")
    @Operation(summary = "Get recurring transactions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<RecurringTransactionResponse>>> getRecurringTransactions() {
        
        List<RecurringTransactionResponse> recurring = transactionProcessingService.getRecurringTransactions();
        return ResponseEntity.ok(ApiResponse.success(recurring));
    }

    @PostMapping("/scheduled")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 5)
    @Operation(summary = "Schedule transaction")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ScheduledTransactionResponse>> scheduleTransaction(
            @Valid @RequestBody ScheduleTransactionRequest request) {
        log.info("Scheduling transaction: {}", request);
        
        ScheduledTransactionResponse scheduled = transactionProcessingService.scheduleTransaction(request);
        return ResponseEntity.ok(ApiResponse.success(scheduled));
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.success("Transaction service is healthy"));
    }
}