package com.waqiti.payment.controller;

import com.waqiti.security.rbac.annotation.RequiresPermission;
import com.waqiti.security.rbac.annotation.RequiresPermission.AuditLevel;
import com.waqiti.payment.dto.*;
import com.waqiti.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

/**
 * SECURE Payment Controller Example - Demonstrates proper authorization implementation
 * that addresses the critical authorization bypass vulnerabilities.
 * 
 * This example shows how to properly secure payment endpoints with:
 * 1. Resource ownership validation (users can only access their own payments)
 * 2. Enhanced authentication for high-risk operations
 * 3. Proper audit logging for compliance
 * 4. Role-based access control with resource-level validation
 */
@RestController
@RequestMapping("/api/v1/secure/payments")
@Tag(name = "Secure Payment Operations", description = "Example of properly secured payment endpoints")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Validated
@Slf4j
public class SecurePaymentControllerExample {

    private final PaymentService paymentService;

    /**
     * SECURE: Get payment details - validates user owns or is participant in the payment
     */
    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment details", description = "Retrieves payment details with proper ownership validation")
    @RequiresPermission(
        resource = "PAYMENT",
        resourceId = "paymentId",
        allowOwner = true,  // Validates user is payment participant
        auditLevel = AuditLevel.NORMAL
    )
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID paymentId) {
        log.info("Fetching payment details: paymentId={}", paymentId);
        PaymentResponse payment = paymentService.getPayment(paymentId);
        return ResponseEntity.ok(payment);
    }

    /**
     * SECURE: Process a payment - validates wallet ownership and enhanced auth for high amounts
     */
    @PostMapping("/process")
    @Operation(summary = "Process payment", description = "Processes a payment with proper authorization and fraud checks")
    @RequiresPermission(
        value = {"PAYMENT:CREATE"},
        resource = "WALLET",
        resourceId = "request.sourceWalletId",  // SpEL expression to extract wallet ID
        allowOwner = true,  // Must own source wallet
        requiresEnhancedAuth = true,  // Requires MFA/biometric
        auditLevel = AuditLevel.COMPLIANCE  // Full compliance audit
    )
    public ResponseEntity<PaymentResponse> processPayment(@Valid @RequestBody PaymentRequest request) {
        log.info("Processing secure payment from wallet: {}", request.getSourceWalletId());
        
        // Additional security validations could be added here
        if (request.getAmount().doubleValue() > 10000) {
            log.warn("High-value payment detected, additional fraud checks applied");
            // Could trigger additional fraud detection logic
        }
        
        PaymentResponse response = paymentService.processPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * SECURE: Cancel payment - only payment initiator or admin can cancel
     */
    @DeleteMapping("/{paymentId}")
    @Operation(summary = "Cancel payment", description = "Cancels a pending payment with proper authorization")
    @RequiresPermission(
        value = {"PAYMENT:CANCEL", "ADMIN"},  // Either permission works
        resource = "PAYMENT",
        resourceId = "paymentId",
        allowOwner = true,  // Payment initiator can cancel
        auditLevel = AuditLevel.DETAILED
    )
    public ResponseEntity<Void> cancelPayment(@PathVariable UUID paymentId) {
        log.info("Cancelling payment: paymentId={}", paymentId);
        paymentService.cancelPayment(paymentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * SECURE: Get user's payment history - validates user can only see their own history
     */
    @GetMapping("/history")
    @Operation(summary = "Get payment history", description = "Retrieves authenticated user's payment history")
    @RequiresPermission(
        value = {"PAYMENT:READ_OWN"},
        auditLevel = AuditLevel.NORMAL
    )
    // No resource ownership check needed - service filters by authenticated user
    public ResponseEntity<List<PaymentResponse>> getMyPaymentHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        // Service should get userId from security context
        List<PaymentResponse> history = paymentService.getUserPaymentHistory(page, size);
        return ResponseEntity.ok(history);
    }

    /**
     * SECURE: Approve high-value payment - requires special permission and enhanced auth
     */
    @PostMapping("/{paymentId}/approve")
    @Operation(summary = "Approve high-value payment", description = "Approves a high-value payment requiring manual review")
    @RequiresPermission(
        allOf = {"PAYMENT:APPROVE", "HIGH_VALUE:APPROVE"},  // Must have both permissions
        resource = "PAYMENT",
        resourceId = "paymentId",
        requiresEnhancedAuth = true,
        auditLevel = AuditLevel.COMPLIANCE
    )
    public ResponseEntity<PaymentResponse> approveHighValuePayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody PaymentApprovalRequest approval) {
        
        log.info("Approving high-value payment: paymentId={}, approvalReason={}", 
                paymentId, approval.getReason());
        
        PaymentResponse response = paymentService.approvePayment(paymentId, approval);
        return ResponseEntity.ok(response);
    }

    /**
     * SECURE: Bulk payment processing - requires special permission and validates all wallets
     */
    @PostMapping("/bulk")
    @Operation(summary = "Process bulk payments", description = "Processes multiple payments with batch authorization")
    @RequiresPermission(
        value = {"PAYMENT:BULK_CREATE"},
        requiresEnhancedAuth = true,
        auditLevel = AuditLevel.COMPLIANCE
    )
    // Note: Bulk operations should validate each wallet in service layer
    public ResponseEntity<BulkPaymentResponse> processBulkPayments(
            @Valid @RequestBody BulkPaymentRequest request) {
        
        log.info("Processing bulk payment: count={}, totalAmount={}", 
                request.getPayments().size(), request.getTotalAmount());
        
        // Service should validate ownership of all source wallets
        BulkPaymentResponse response = paymentService.processBulkPayments(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * SECURE: Admin-only endpoint to view any payment
     */
    @GetMapping("/admin/{paymentId}")
    @Operation(summary = "Admin view payment", description = "Administrative access to view any payment")
    @RequiresPermission(
        value = {"ADMIN", "PAYMENT:ADMIN_VIEW"},
        auditLevel = AuditLevel.COMPLIANCE  // All admin actions are compliance-audited
    )
    public ResponseEntity<PaymentResponse> adminViewPayment(@PathVariable UUID paymentId) {
        log.info("Admin viewing payment: paymentId={}", paymentId);
        PaymentResponse payment = paymentService.getPaymentForAdmin(paymentId);
        return ResponseEntity.ok(payment);
    }

    /**
     * SECURE: Merchant payment acceptance - validates merchant account
     */
    @PostMapping("/merchant/accept")
    @Operation(summary = "Accept merchant payment", description = "Merchant accepts a payment with proper merchant validation")
    @RequiresPermission(
        value = {"MERCHANT:ACCEPT_PAYMENT"},
        resource = "BUSINESS",
        resourceId = "request.merchantId",
        allowOwner = true,  // Must be owner/authorized user of merchant account
        auditLevel = AuditLevel.DETAILED
    )
    public ResponseEntity<MerchantPaymentResponse> acceptMerchantPayment(
            @Valid @RequestBody MerchantPaymentRequest request) {
        
        log.info("Merchant accepting payment: merchantId={}, amount={}", 
                request.getMerchantId(), request.getAmount());
        
        MerchantPaymentResponse response = paymentService.acceptMerchantPayment(request);
        return ResponseEntity.ok(response);
    }
}

