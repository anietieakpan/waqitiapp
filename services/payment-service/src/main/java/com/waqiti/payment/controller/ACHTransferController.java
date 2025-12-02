package com.waqiti.payment.controller;

import com.waqiti.common.ratelimit.RateLimit;
import com.waqiti.common.ratelimit.RateLimit.KeyType;
import com.waqiti.common.ratelimit.RateLimit.Priority;
import com.waqiti.common.security.SecurityContextUtil;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.payment.dto.*;
import com.waqiti.payment.entity.ACHTransfer;
import com.waqiti.payment.ach.ACHTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * REST controller for ACH transfer operations
 */
@RestController
@RequestMapping("/api/v1/ach")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "ACH Transfers", description = "ACH transfer operations for bank account deposits and withdrawals")
@SecurityRequirement(name = "bearer-jwt")
public class ACHTransferController {
    
    private final ACHTransferService achTransferService;
    private final IdempotencyService idempotencyService;
    
    /**
     * Initiates an ACH deposit from bank account to wallet
     */
    @PostMapping("/deposit")
    @PreAuthorize("hasRole('USER')")
    @RateLimit(
        requests = 20,
        window = 1,
        unit = TimeUnit.HOURS,
        keyType = KeyType.USER,
        priority = Priority.HIGH,
        description = "ACH deposit initiation",
        errorMessage = "ACH deposit limit exceeded. Maximum 20 deposits per hour."
    )
    @Operation(summary = "Initiate ACH deposit",
               description = "Transfer money from a bank account to wallet via ACH")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "ACH deposit initiated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "403", description = "User not authorized for this operation"),
        @ApiResponse(responseCode = "409", description = "Duplicate request (idempotency check)"),
        @ApiResponse(responseCode = "422", description = "Compliance check failed"),
        @ApiResponse(responseCode = "429", description = "Daily limit exceeded")
    })
    public CompletableFuture<ResponseEntity<ACHTransferResponse>> initiateDeposit(
            @Valid @RequestBody ACHDepositRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        
        // SECURITY FIX: Override user ID with authenticated user
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();
        request.setUserId(authenticatedUserId);
        
        log.info("ACH deposit request received for authenticated user: {}", authenticatedUserId);
        
        // Generate idempotency key if not provided
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            idempotencyKey = IdempotencyService.FinancialIdempotencyKeys.deposit(
                request.getWalletId(), 
                request.getAmount().toString(), 
                request.getExternalTransactionId()
            );
        }
        
        final String finalIdempotencyKey = idempotencyKey;
        
        return idempotencyService.executeIdempotent(idempotencyKey, () -> {
            try {
                // SECURITY FIX: Added 10-second timeout to prevent HTTP thread exhaustion
                // If ACH service hangs, this prevents Tomcat threads from blocking indefinitely
                return achTransferService.initiateACHDeposit(request)
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("ACH deposit timed out after 10 seconds for idempotency key: {}", finalIdempotencyKey, e);
                throw new RuntimeException("ACH deposit service timed out - please retry", e);
            } catch (java.util.concurrent.ExecutionException e) {
                log.error("ACH deposit execution failed for idempotency key: {}", finalIdempotencyKey, e.getCause());
                throw new RuntimeException("ACH deposit failed: " + e.getCause().getMessage(), e.getCause());
            } catch (java.util.concurrent.InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("ACH deposit interrupted for idempotency key: {}", finalIdempotencyKey, e);
                throw new RuntimeException("ACH deposit interrupted", e);
            } catch (Exception e) {
                throw new RuntimeException("ACH deposit failed", e);
            }
        })
        .thenApply(response -> ResponseEntity.status(HttpStatus.ACCEPTED).body(response))
        .exceptionally(ex -> {
            log.error("ACH deposit failed for idempotency key: {}", finalIdempotencyKey, ex);
            throw new RuntimeException(ex);
        });
    }
    
    /**
     * Initiates an ACH withdrawal from wallet to bank account
     */
    @PostMapping("/withdrawal")
    @PreAuthorize("hasRole('USER')")
    @RateLimit(
        requests = 10,
        window = 1,
        unit = TimeUnit.HOURS,
        keyType = KeyType.USER,
        priority = Priority.HIGH,
        description = "ACH withdrawal initiation",
        errorMessage = "ACH withdrawal limit exceeded. Maximum 10 withdrawals per hour."
    )
    @Operation(summary = "Initiate ACH withdrawal",
               description = "Transfer money from wallet to a bank account via ACH")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "ACH withdrawal initiated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "402", description = "Insufficient funds in wallet"),
        @ApiResponse(responseCode = "403", description = "User not authorized for this operation"),
        @ApiResponse(responseCode = "409", description = "Duplicate request (idempotency check)"),
        @ApiResponse(responseCode = "422", description = "Compliance check failed"),
        @ApiResponse(responseCode = "429", description = "Daily limit exceeded")
    })
    public CompletableFuture<ResponseEntity<ACHTransferResponse>> initiateWithdrawal(
            @Valid @RequestBody ACHWithdrawalRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        
        // SECURITY FIX: Override user ID with authenticated user
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();
        request.setUserId(authenticatedUserId);
        
        log.info("ACH withdrawal request received for authenticated user: {}", authenticatedUserId);
        
        // Generate idempotency key if not provided
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            idempotencyKey = IdempotencyService.FinancialIdempotencyKeys.withdrawal(
                request.getWalletId(), 
                request.getAmount().toString(), 
                request.getExternalTransactionId()
            );
        }
        
        final String finalIdempotencyKey = idempotencyKey;
        
        return idempotencyService.executeIdempotent(idempotencyKey, () -> {
            try {
                // SECURITY FIX: Added 10-second timeout to prevent HTTP thread exhaustion
                // If ACH service hangs, this prevents Tomcat threads from blocking indefinitely
                return achTransferService.initiateACHWithdrawal(request)
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("ACH withdrawal timed out after 10 seconds for idempotency key: {}", finalIdempotencyKey, e);
                throw new RuntimeException("ACH withdrawal service timed out - please retry", e);
            } catch (java.util.concurrent.ExecutionException e) {
                log.error("ACH withdrawal execution failed for idempotency key: {}", finalIdempotencyKey, e.getCause());
                throw new RuntimeException("ACH withdrawal failed: " + e.getCause().getMessage(), e.getCause());
            } catch (java.util.concurrent.InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("ACH withdrawal interrupted for idempotency key: {}", finalIdempotencyKey, e);
                throw new RuntimeException("ACH withdrawal interrupted", e);
            } catch (Exception e) {
                throw new RuntimeException("ACH withdrawal failed", e);
            }
        })
        .thenApply(response -> ResponseEntity.status(HttpStatus.ACCEPTED).body(response))
        .exceptionally(ex -> {
            log.error("ACH withdrawal failed for idempotency key: {}", finalIdempotencyKey, ex);
            throw new RuntimeException(ex);
        });
    }
    
    /**
     * Gets ACH transfer history for the authenticated user
     */
    @GetMapping("/history")
    @PreAuthorize("hasRole('USER')")
    @RateLimit(
        requests = 100,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.MEDIUM,
        description = "ACH transfer history query",
        errorMessage = "History query limit exceeded. Maximum 100 requests per minute."
    )
    @Operation(summary = "Get ACH transfer history",
               description = "Retrieve paginated ACH transfer history for the authenticated user")
    public ResponseEntity<Page<ACHTransferDTO>> getTransferHistory(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) 
            Pageable pageable) {
        
        // SECURITY FIX: Use authenticated user ID instead of request parameter
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();
        
        Page<ACHTransfer> transfers = achTransferService.getUserTransferHistory(authenticatedUserId, pageable);
        Page<ACHTransferDTO> dtos = transfers.map(this::mapToDTO);
        
        return ResponseEntity.ok(dtos);
    }
    
    /**
     * Gets a specific ACH transfer by ID
     */
    @GetMapping("/{transferId}")
    @PreAuthorize("hasRole('USER')")
    @RateLimit(
        requests = 120,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.MEDIUM,
        description = "ACH transfer details lookup",
        errorMessage = "Transfer lookup limit exceeded. Maximum 120 requests per minute."
    )
    @Operation(summary = "Get ACH transfer details",
               description = "Retrieve details of a specific ACH transfer")
    public ResponseEntity<ACHTransferDTO> getTransfer(
            @Parameter(description = "Transfer ID", required = true)
            @PathVariable UUID transferId) {
        
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();
        
        ACHTransfer transfer = achTransferService.getTransfer(transferId);
        
        // SECURITY FIX: Verify user owns this transfer
        if (!transfer.getUserId().equals(authenticatedUserId)) {
            throw new SecurityException("Unauthorized access to ACH transfer");
        }
        
        return ResponseEntity.ok(mapToDTO(transfer));
    }
    
    /**
     * Gets daily ACH transfer limit status for the authenticated user
     */
    @GetMapping("/limits/daily")
    @PreAuthorize("hasRole('USER')")
    @RateLimit(
        requests = 60,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.LOW,
        description = "ACH daily limits check",
        errorMessage = "Limits check exceeded. Maximum 60 requests per minute."
    )
    @Operation(summary = "Get daily ACH limit status", 
               description = "Check remaining daily ACH transfer limit")
    public ResponseEntity<DailyLimitResponse> getDailyLimitStatus() {
        
        // SECURITY FIX: Use authenticated user ID instead of request parameter
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();
        
        BigDecimal dailyTotal = achTransferService.getDailyACHTotal(authenticatedUserId);
        BigDecimal dailyLimit = achTransferService.getDailyLimit();
        BigDecimal remaining = dailyLimit.subtract(dailyTotal).max(BigDecimal.ZERO);
        
        DailyLimitResponse response = DailyLimitResponse.builder()
            .dailyLimit(dailyLimit)
            .usedToday(dailyTotal)
            .remainingToday(remaining)
            .build();
            
        return ResponseEntity.ok(response);
    }
    
    /**
     * Webhook endpoint for ACH status updates from banking partner
     */
    @PostMapping("/webhook/status")
    @RateLimit(
        requests = 1000,
        window = 1,
        unit = TimeUnit.HOURS,
        keyType = KeyType.IP,
        priority = Priority.MEDIUM,
        description = "ACH webhook from banking partner",
        errorMessage = "Webhook rate limit exceeded. Maximum 1000 requests per hour per IP."
    )
    @Operation(summary = "ACH status webhook",
               description = "Webhook endpoint for receiving ACH transfer status updates")
    public ResponseEntity<Void> handleStatusWebhook(
            @RequestHeader("X-Webhook-Signature") String signature,
            @Valid @RequestBody ACHWebhookRequest webhook) {
        
        log.info("ACH webhook received for transfer: {} with status: {}", 
            webhook.getTransferId(), webhook.getStatus());
        
        // SECURITY FIX: Verify webhook signature before processing
        if (!achTransferService.verifyWebhookSignature(webhook, signature)) {
            log.error("Invalid webhook signature for transfer: {}", webhook.getTransferId());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        achTransferService.handleACHStatusWebhook(webhook);
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Maps ACH transfer entity to DTO
     */
    private ACHTransferDTO mapToDTO(ACHTransfer transfer) {
        return ACHTransferDTO.builder()
            .id(transfer.getId())
            .amount(transfer.getAmount())
            .direction(transfer.getDirection())
            .status(transfer.getStatus())
            .accountLastFour(AccountNumberValidator.getLastFour(
                transfer.getAccountNumber()))
            .accountType(transfer.getAccountType())
            .description(transfer.getDescription())
            .expectedCompletionDate(transfer.getExpectedCompletionDate())
            .createdAt(transfer.getCreatedAt())
            .completedAt(transfer.getCompletedAt())
            .failureReason(transfer.getFailureReason())
            .build();
    }
    
    /**
     * DTO for ACH transfer display
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ACHTransferDTO {
        private UUID id;
        private BigDecimal amount;
        private com.waqiti.payment.entity.TransferDirection direction;
        private com.waqiti.payment.entity.ACHTransferStatus status;
        private String accountLastFour;
        private com.waqiti.payment.entity.BankAccountType accountType;
        private String description;
        private java.time.LocalDate expectedCompletionDate;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime completedAt;
        private String failureReason;
    }
    
    /**
     * DTO for daily limit response
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DailyLimitResponse {
        private BigDecimal dailyLimit;
        private BigDecimal usedToday;
        private BigDecimal remainingToday;
    }
}