package com.waqiti.corebanking.api;

import com.waqiti.corebanking.dto.*;
import com.waqiti.corebanking.service.TransactionProcessingService;
import com.waqiti.common.tracing.Traced;
import com.waqiti.common.security.SecurityContextUtil;
import com.waqiti.common.security.ResourceOwnershipAspect.*;
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
import java.util.concurrent.CompletableFuture;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Transaction Processing", description = "Core banking transaction operations")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionProcessingService transactionProcessingService;

    @PostMapping("/transfer")
    @Operation(summary = "Process money transfer", description = "Processes a money transfer between accounts")
    @ApiResponse(responseCode = "202", description = "Transfer accepted for processing")
    @ApiResponse(responseCode = "400", description = "Invalid transfer request")
    @ApiResponse(responseCode = "409", description = "Insufficient funds or account issues")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @ValidateOwnership(resourceType = ResourceType.ACCOUNT, resourceIdParam = "fromAccountId", operation = "TRANSFER")
    @Traced(operationName = "process-transfer", businessOperation = "money-transfer", priority = Traced.TracingPriority.CRITICAL)
    public ResponseEntity<TransferResponseDto> processTransfer(
            @Valid @RequestBody TransferRequestDto request) {
        
        // SECURITY FIX: Override user ID with authenticated user
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();
        request.setUserId(authenticatedUserId);
        
        log.info("Processing transfer: {} {} from {} to {} (txn: {})", 
                request.getAmount(), request.getCurrency(), 
                request.getFromAccountId(), request.getToAccountId(), request.getTransactionId());
        
        try {
            // ASYNC PROCESSING - Non-blocking transfer initiation
            String transferId = UUID.randomUUID().toString();
            
            // Initiate async processing without blocking
            CompletableFuture<TransferResponseDto> future = transactionProcessingService.processTransferAsync(
                request.toBuilder().transferId(transferId).build()
            );
            
            // Handle async completion without blocking the request
            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Transfer failed asynchronously: {}", transferId, throwable);
                    // Publish failure event for compensation and notification
                    eventPublisher.publishTransferFailedEvent(transferId, request.getFromAccount(), 
                        request.getAmount(), throwable.getMessage());
                } else {
                    log.info("Transfer completed asynchronously: {} with status: {}", 
                        transferId, result.getStatus());
                    // Publish success event for downstream processing
                    eventPublisher.publishTransferCompletedEvent(transferId, result);
                }
            });
            
            // Return immediate response with tracking information
            TransferResponseDto response = TransferResponseDto.builder()
                .transferId(transferId)
                .status("INITIATED")
                .message("Transfer initiated successfully. You will be notified when completed.")
                .timestamp(LocalDateTime.now())
                .estimatedCompletionTime(LocalDateTime.now().plusMinutes(5))
                .trackingUrl("/api/v1/transfers/" + transferId + "/status")
                .build();
            
            log.info("Transfer initiated successfully: {} for request: {}", 
                transferId, request.getTransactionId());
            
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("Location", "/api/v1/transfers/" + transferId)
                .body(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid transfer request: {}", e.getMessage());
            
            // Create audit record for invalid request
            auditService.recordInvalidTransferAttempt(
                request.getFromAccount(),
                request.getToAccount(), 
                request.getAmount(),
                e.getMessage()
            );
            
            throw new TransferValidationException("Invalid transfer request: " + e.getMessage(), e);
            
        } catch (Exception e) {
            log.error("Failed to initiate transfer: {}", request.getTransactionId(), e);
            
            // Create exception record for investigation
            exceptionService.recordTransferInitiationFailure(
                request.getTransactionId(),
                request.getFromAccount(),
                e.getMessage()
            );
            
            throw new TransferInitiationException("Transfer initiation failed: " + e.getMessage(), e);
        }
    }

    @PostMapping("/payment")
    @Operation(summary = "Process payment", description = "Processes a payment transaction")
    @ApiResponse(responseCode = "202", description = "Payment accepted for processing")
    @ApiResponse(responseCode = "400", description = "Invalid payment request")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @ValidateOwnership(resourceType = ResourceType.ACCOUNT, resourceIdParam = "fromAccountId", operation = "PAYMENT")
    @Traced(operationName = "process-payment", businessOperation = "payment-processing", priority = Traced.TracingPriority.CRITICAL)
    public ResponseEntity<PaymentResponseDto> processPayment(
            @Valid @RequestBody PaymentRequestDto request) {
        
        // SECURITY FIX: Override user ID with authenticated user
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();
        request.setUserId(authenticatedUserId);
        
        log.info("Processing payment: {} {} from user {} (txn: {})", 
                request.getAmount(), request.getCurrency(), 
                request.getFromUserId(), request.getTransactionId());
        
        try {
            CompletableFuture<PaymentResponseDto> future = transactionProcessingService.processPaymentAsync(request);
            PaymentResponseDto response = future.get();
            
            log.info("Payment processed successfully: {} (status: {})", 
                    request.getTransactionId(), response.getStatus());
            
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            
        } catch (Exception e) {
            log.error("Failed to process payment: {}", request.getTransactionId(), e);
            throw new RuntimeException("Payment processing failed", e);
        }
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get transaction details", description = "Retrieves transaction information by ID")
    @ApiResponse(responseCode = "200", description = "Transaction found")
    @ApiResponse(responseCode = "404", description = "Transaction not found")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @ValidateOwnership(resourceType = ResourceType.TRANSACTION, resourceIdParam = "transactionId", operation = "VIEW")
    @Traced(operationName = "get-transaction", businessOperation = "transaction-inquiry", priority = Traced.TracingPriority.MEDIUM)
    public ResponseEntity<TransactionResponseDto> getTransaction(
            @Parameter(description = "Transaction identifier", required = true)
            @PathVariable @NotBlank String transactionId) {
        
        log.debug("Retrieving transaction: {}", transactionId);
        
        try {
            TransactionResponseDto transaction = transactionProcessingService.getTransaction(transactionId);
            return ResponseEntity.ok(transaction);
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                log.warn("Transaction not found: {}", transactionId);
                return ResponseEntity.notFound().build();
            }
            log.error("Failed to retrieve transaction: {}", transactionId, e);
            throw e;
        }
    }

    @GetMapping("/account/{accountId}")
    @Operation(summary = "Get account transactions", description = "Retrieves transaction history for an account")
    @ApiResponse(responseCode = "200", description = "Transactions retrieved successfully")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @ValidateOwnership(resourceType = ResourceType.ACCOUNT, resourceIdParam = "accountId", operation = "VIEW_TRANSACTIONS")
    @Traced(operationName = "get-account-transactions", businessOperation = "transaction-history", priority = Traced.TracingPriority.MEDIUM)
    public ResponseEntity<Page<TransactionResponseDto>> getAccountTransactions(
            @Parameter(description = "Account identifier", required = true)
            @PathVariable @NotBlank String accountId,
            @Parameter(description = "Transaction type filter")
            @RequestParam(required = false) String type,
            @Parameter(description = "Transaction status filter")
            @RequestParam(required = false) String status,
            @Parameter(description = "Start date filter (ISO format)")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "End date filter (ISO format)")
            @RequestParam(required = false) String endDate,
            Pageable pageable) {
        
        log.debug("Retrieving transactions for account: {} (type: {}, status: {})", 
                 accountId, type, status);
        
        try {
            TransactionSearchCriteria criteria = TransactionSearchCriteria.builder()
                .accountId(accountId)
                .type(type)
                .status(status)
                .startDate(startDate)
                .endDate(endDate)
                .build();
            
            Page<TransactionResponseDto> transactions = transactionProcessingService
                .searchTransactions(criteria, pageable);
            
            return ResponseEntity.ok(transactions);
            
        } catch (Exception e) {
            log.error("Failed to retrieve transactions for account: {}", accountId, e);
            throw new RuntimeException("Transaction retrieval failed", e);
        }
    }

    @PostMapping("/{transactionId}/reverse")
    @Operation(summary = "Reverse transaction", description = "Reverses a completed transaction")
    @ApiResponse(responseCode = "202", description = "Reversal accepted for processing")
    @ApiResponse(responseCode = "400", description = "Transaction cannot be reversed")
    @ApiResponse(responseCode = "404", description = "Transaction not found")
    @PreAuthorize("hasRole('ADMIN')")
    @Traced(operationName = "reverse-transaction", businessOperation = "transaction-reversal", priority = Traced.TracingPriority.HIGH)
    public ResponseEntity<TransactionResponseDto> reverseTransaction(
            @Parameter(description = "Transaction identifier", required = true)
            @PathVariable @NotBlank String transactionId,
            @Valid @RequestBody TransactionReversalRequestDto request) {
        
        log.info("Reversing transaction: {} (reason: {})", transactionId, request.getReason());
        
        try {
            TransactionResponseDto response = transactionProcessingService
                .reverseTransaction(transactionId, request);
            
            log.info("Transaction reversal processed: {} -> {}", 
                    transactionId, response.getTransactionId());
            
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            
        } catch (IllegalStateException e) {
            log.warn("Transaction cannot be reversed: {} - {}", transactionId, e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                log.warn("Transaction not found for reversal: {}", transactionId);
                return ResponseEntity.notFound().build();
            }
            log.error("Failed to reverse transaction: {}", transactionId, e);
            throw e;
        }
    }

    @PutMapping("/{transactionId}/status")
    @Operation(summary = "Update transaction status", description = "Updates the status of a transaction")
    @ApiResponse(responseCode = "200", description = "Transaction status updated")
    @ApiResponse(responseCode = "404", description = "Transaction not found")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM')")
    @Traced(operationName = "update-transaction-status", businessOperation = "transaction-management", priority = Traced.TracingPriority.HIGH)
    public ResponseEntity<TransactionResponseDto> updateTransactionStatus(
            @Parameter(description = "Transaction identifier", required = true)
            @PathVariable @NotBlank String transactionId,
            @Valid @RequestBody TransactionStatusUpdateDto request) {
        
        log.info("Updating transaction status: {} -> {}", transactionId, request.getStatus());
        
        try {
            TransactionResponseDto response = transactionProcessingService
                .updateTransactionStatus(transactionId, request);
            
            log.info("Transaction status updated: {} -> {}", transactionId, request.getStatus());
            
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                log.warn("Transaction not found for status update: {}", transactionId);
                return ResponseEntity.notFound().build();
            }
            log.error("Failed to update transaction status: {}", transactionId, e);
            throw e;
        }
    }

    @PostMapping("/bulk")
    @Operation(summary = "Process bulk transactions", description = "Processes multiple transactions in a batch")
    @ApiResponse(responseCode = "202", description = "Bulk processing accepted")
    @ApiResponse(responseCode = "400", description = "Invalid bulk request")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM')")
    @Traced(operationName = "process-bulk-transactions", businessOperation = "bulk-processing", priority = Traced.TracingPriority.HIGH)
    public ResponseEntity<BulkTransactionResponseDto> processBulkTransactions(
            @Valid @RequestBody BulkTransactionRequestDto request) {
        
        log.info("Processing bulk transactions: {} items (batch: {})", 
                request.getTransactions().size(), request.getBatchId());
        
        try {
            CompletableFuture<BulkTransactionResponseDto> future = transactionProcessingService
                .processBulkTransactionsAsync(request);
            BulkTransactionResponseDto response = future.get();
            
            log.info("Bulk transaction processing completed: {} (processed: {}/{})", 
                    request.getBatchId(), response.getProcessedCount(), response.getTotalCount());
            
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            
        } catch (Exception e) {
            log.error("Failed to process bulk transactions: {}", request.getBatchId(), e);
            throw new RuntimeException("Bulk processing failed", e);
        }
    }
}