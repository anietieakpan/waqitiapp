package com.waqiti.payment.controller;

import com.waqiti.common.ratelimit.RateLimit;
import com.waqiti.common.ratelimit.RateLimit.KeyType;
import com.waqiti.common.ratelimit.RateLimit.Priority;
import com.waqiti.payment.dto.*;
import com.waqiti.payment.service.CheckDepositService;
import com.waqiti.payment.service.WebhookSignatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * REST controller for check deposit operations
 */
@RestController
@RequestMapping("/api/v1/check-deposits")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Check Deposits", description = "Mobile check deposit operations")
public class CheckDepositController {
    
    private final CheckDepositService checkDepositService;
    private final WebhookSignatureService webhookSignatureService;
    
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    @RateLimit(
        requests = 5,
        window = 1,
        unit = TimeUnit.HOURS,
        keyType = KeyType.USER,
        priority = Priority.HIGH,
        description = "Check deposit submission - fraud prevention",
        errorMessage = "Check deposit limit exceeded. Maximum 5 deposits per hour allowed."
    )
    @Operation(summary = "Submit a check deposit",
               description = "Submit a mobile check deposit with front and back images")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Check deposit accepted for processing"),
        @ApiResponse(responseCode = "400", description = "Invalid request or validation error"),
        @ApiResponse(responseCode = "409", description = "Duplicate check detected"),
        @ApiResponse(responseCode = "429", description = "Deposit limit exceeded"),
        @ApiResponse(responseCode = "503", description = "Service temporarily unavailable")
    })
    public CompletableFuture<ResponseEntity<CheckDepositResponse>> submitCheckDeposit(
            @Valid @RequestBody CheckDepositRequest request) {
        
        log.info("Received check deposit request for user: {} amount: {}", 
            request.getUserId(), request.getAmount());
        
        return checkDepositService.initiateCheckDeposit(request)
            .thenApply(response -> ResponseEntity.status(HttpStatus.ACCEPTED).body(response))
            .exceptionally(ex -> {
                log.error("Check deposit failed", ex);
                return handleCheckDepositError(ex);
            });
    }
    
    @GetMapping("/{depositId}/status")
    @PreAuthorize("hasRole('USER')")
    @RateLimit(
        requests = 60,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.MEDIUM,
        description = "Check deposit status check",
        errorMessage = "Status check limit exceeded. Maximum 60 requests per minute."
    )
    @Operation(summary = "Get check deposit status",
               description = "Get the current status and details of a check deposit")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Check deposit not found")
    })
    public ResponseEntity<CheckDepositStatusResponse> getCheckDepositStatus(
            @Parameter(description = "Check deposit ID", required = true)
            @PathVariable @NotNull UUID depositId) {
        
        log.info("Getting check deposit status for ID: {}", depositId);
        
        try {
            CheckDepositStatusResponse status = checkDepositService.getCheckDepositStatus(depositId);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Failed to get check deposit status", e);
            return ResponseEntity.notFound().build();
        }
    }
    
    @PostMapping("/webhook")
    @Operation(summary = "Handle check processor webhook", 
               description = "Endpoint for receiving status updates from check processor")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Webhook processed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid webhook data")
    })
    public ResponseEntity<Void> handleCheckWebhook(
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @Valid @RequestBody CheckWebhookRequest webhook) {
        
        log.info("Received check webhook for reference: {} status: {}", 
            webhook.getReferenceId(), webhook.getStatus());
        
        try {
            // Verify webhook signature for security
            if (signature == null || !webhookSignatureService.verifySignature(signature, webhook)) {
                log.warn("Invalid webhook signature for reference: {}", webhook.getReferenceId());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            checkDepositService.handleCheckStatusWebhook(webhook);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to process check webhook", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PostMapping("/{depositId}/approve")
    @PreAuthorize("hasRole('ADMIN') or hasRole('REVIEWER')")
    @Operation(summary = "Manually approve check deposit", 
               description = "Manually approve a check deposit that's in review")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Check approved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid state for approval"),
        @ApiResponse(responseCode = "404", description = "Check deposit not found")
    })
    public ResponseEntity<Void> approveCheckDeposit(
            @Parameter(description = "Check deposit ID", required = true)
            @PathVariable @NotNull UUID depositId,
            @Parameter(description = "Approval notes", required = true)
            @RequestParam String notes) {
        
        log.info("Manually approving check deposit: {}", depositId);
        
        try {
            checkDepositService.manuallyApproveDeposit(depositId, notes);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to approve check deposit", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PostMapping("/{depositId}/reject")
    @PreAuthorize("hasRole('ADMIN') or hasRole('REVIEWER')")
    @Operation(summary = "Manually reject check deposit", 
               description = "Manually reject a check deposit")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Check rejected successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid state for rejection"),
        @ApiResponse(responseCode = "404", description = "Check deposit not found")
    })
    public ResponseEntity<Void> rejectCheckDeposit(
            @Parameter(description = "Check deposit ID", required = true)
            @PathVariable @NotNull UUID depositId,
            @Parameter(description = "Rejection reason", required = true)
            @RequestParam String reason) {
        
        log.info("Manually rejecting check deposit: {}", depositId);
        
        try {
            checkDepositService.manuallyRejectDeposit(depositId, reason);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to reject check deposit", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Handles check deposit errors
     */
    private ResponseEntity<CheckDepositResponse> handleCheckDepositError(Throwable ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        
        CheckDepositResponse errorResponse = CheckDepositResponse.builder()
            .status(null)
            .message("Check deposit failed")
            .errorMessage(cause.getMessage())
            .build();
        
        if (cause instanceof DuplicateCheckException) {
            errorResponse.setErrorCode("DUPLICATE_CHECK");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
        } else if (cause instanceof LimitExceededException) {
            errorResponse.setErrorCode("LIMIT_EXCEEDED");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(errorResponse);
        } else if (cause instanceof InvalidCheckImageException) {
            errorResponse.setErrorCode("INVALID_IMAGE");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } else if (cause instanceof ServiceUnavailableException) {
            errorResponse.setErrorCode("SERVICE_UNAVAILABLE");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
        } else if (cause instanceof ValidationException) {
            errorResponse.setErrorCode("VALIDATION_ERROR");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } else {
            errorResponse.setErrorCode("INTERNAL_ERROR");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}