package com.waqiti.payment.offline.controller;

import com.waqiti.common.api.ApiResponse;
import com.waqiti.payment.offline.OfflinePaymentService;
import com.waqiti.payment.offline.dto.OfflinePaymentRequest;
import com.waqiti.payment.offline.dto.OfflinePaymentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for offline P2P payment functionality
 */
@RestController
@RequestMapping("/api/v1/payments/offline")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Offline Payments", description = "Offline P2P payment operations")
public class OfflinePaymentController {
    
    private final OfflinePaymentService offlinePaymentService;
    
    @PostMapping
    @Operation(
        summary = "Create offline payment",
        description = "Creates a payment while offline that will sync when connectivity is restored"
    )
    @PreAuthorize("hasAuthority('CREATE_PAYMENT')")
    public ResponseEntity<ApiResponse<OfflinePaymentResponse>> createOfflinePayment(
            @Valid @RequestBody OfflinePaymentRequest request,
            Authentication authentication) {
        
        String userId = authentication.getName();
        log.info("Creating offline payment for user: {} to recipient: {}", userId, request.getRecipientId());
        
        OfflinePaymentResponse response = offlinePaymentService.createOfflinePayment(request, userId);
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "Offline payment created successfully"));
    }
    
    @PostMapping("/{paymentId}/accept")
    @Operation(
        summary = "Accept offline payment",
        description = "Accept an offline payment using QR code or proximity transfer"
    )
    @PreAuthorize("hasAuthority('ACCEPT_PAYMENT')")
    public ResponseEntity<ApiResponse<OfflinePaymentResponse>> acceptOfflinePayment(
            @PathVariable String paymentId,
            @RequestParam String verificationData,
            Authentication authentication) {
        
        String recipientId = authentication.getName();
        log.info("Accepting offline payment: {} by recipient: {}", paymentId, recipientId);
        
        OfflinePaymentResponse response = offlinePaymentService.acceptOfflinePayment(paymentId, recipientId, verificationData);
        
        return ResponseEntity.ok(ApiResponse.success(response, "Offline payment accepted"));
    }
    
    @GetMapping("/pending")
    @Operation(
        summary = "Get pending offline payments",
        description = "Retrieves all pending offline payments for the authenticated user"
    )
    @PreAuthorize("hasAuthority('READ_PAYMENT')")
    public ResponseEntity<ApiResponse<List<OfflinePaymentResponse>>> getPendingOfflinePayments(
            Authentication authentication) {
        
        String userId = authentication.getName();
        log.debug("Retrieving pending offline payments for user: {}", userId);
        
        List<OfflinePaymentResponse> payments = offlinePaymentService.getPendingOfflinePayments(userId);
        
        return ResponseEntity.ok(ApiResponse.success(payments));
    }
    
    @PostMapping("/sync")
    @Operation(
        summary = "Sync offline payments",
        description = "Manually trigger sync of pending offline payments"
    )
    @PreAuthorize("hasAuthority('CREATE_PAYMENT')")
    public ResponseEntity<ApiResponse<String>> syncOfflinePayments(Authentication authentication) {
        
        String userId = authentication.getName();
        log.info("Manual sync triggered for user: {}", userId);
        
        offlinePaymentService.processPendingOfflinePayments(userId);
        
        return ResponseEntity.ok(ApiResponse.success("Offline payments sync initiated"));
    }
    
    @DeleteMapping("/{paymentId}")
    @Operation(
        summary = "Cancel offline payment",
        description = "Cancel a pending offline payment before it's synced"
    )
    @PreAuthorize("hasAuthority('CANCEL_PAYMENT')")
    public ResponseEntity<ApiResponse<String>> cancelOfflinePayment(
            @PathVariable String paymentId,
            Authentication authentication) {
        
        String userId = authentication.getName();
        log.info("Cancelling offline payment: {} for user: {}", paymentId, userId);
        
        offlinePaymentService.cancelOfflinePayment(paymentId, userId);
        
        return ResponseEntity.ok(ApiResponse.success("Offline payment cancelled"));
    }
}