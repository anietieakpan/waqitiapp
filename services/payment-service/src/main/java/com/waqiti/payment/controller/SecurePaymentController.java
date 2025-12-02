package com.waqiti.payment.controller;

import com.waqiti.common.security.SecurityContextUtil;
import com.waqiti.common.validation.FinancialValidator;
import com.waqiti.payment.dto.*;
import com.waqiti.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Secure Payment Controller with comprehensive authorization.
 *
 * SECURITY FEATURES:
 * - @PreAuthorize on all endpoints
 * - User ID validation (prevents IDOR)
 * - Role-based access control (RBAC)
 * - Input validation
 * - Audit logging
 * - Rate limiting (via API Gateway)
 *
 * AUTHORIZATION LEVELS:
 * - USER: Can create/view own payments
 * - MERCHANT: Can view received payments
 * - ADMIN: Full access to all operations
 * - COMPLIANCE: Read-only access for audits
 *
 * IDOR PREVENTION:
 * All endpoints validate that authenticated user can only access their own data.
 * Cross-user access attempts are logged and blocked.
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payments", description = "Secure payment operations")
@SecurityRequirement(name = "bearer-jwt")
public class SecurePaymentController {

    private final PaymentService paymentService;
    private final FinancialValidator financialValidator;

    /**
     * Create a new payment.
     *
     * SECURITY:
     * - Requires USER or MERCHANT role
     * - Validates user can only create payments for themselves
     * - Validates amount, currency, merchant
     * - Fraud detection applied automatically
     *
     * @param request Payment creation request
     * @return Created payment details
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
    @Operation(summary = "Create payment", description = "Create a new payment transaction")
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody PaymentRequest request) {

        log.info("Payment creation request: customerId={}, merchantId={}, amount={}",
                request.getCustomerId(), request.getMerchantId(), request.getAmount());

        // CRITICAL: Validate authenticated user matches request
        UUID authenticatedUserId = SecurityContextUtil.getCurrentUserId();
        if (!request.getCustomerId().equals(authenticatedUserId)) {
            log.warn("🚨 SECURITY: IDOR attempt detected - User {} tried to create payment for user {}",
                    authenticatedUserId, request.getCustomerId());
            throw new AccessDeniedException(
                "Cannot create payment for another user. Authenticated: " + authenticatedUserId +
                ", Requested: " + request.getCustomerId()
            );
        }

        // Additional validation
        financialValidator.validateAmount(request.getAmount(), "amount");
        financialValidator.validateCurrency(request.getCurrency());
        financialValidator.validateUUID(request.getMerchantId(), "merchantId");

        // Create payment
        PaymentResponse response = paymentService.createPayment(request);

        log.info("✅ Payment created: paymentId={}, customerId={}", response.getId(), authenticatedUserId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get payment by ID.
     *
     * SECURITY:
     * - Requires USER, MERCHANT, or ADMIN role
     * - Users can only view their own payments
     * - Merchants can view payments they received
     * - Admins can view any payment
     *
     * @param paymentId Payment ID
     * @return Payment details
     */
    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @Operation(summary = "Get payment", description = "Retrieve payment details by ID")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID paymentId) {

        log.debug("Get payment request: paymentId={}", paymentId);

        PaymentResponse payment = paymentService.getPayment(paymentId);

        // CRITICAL: Verify user owns this payment (unless ADMIN)
        UUID authenticatedUserId = SecurityContextUtil.getCurrentUserId();
        boolean isAdmin = SecurityContextUtil.hasRole("ADMIN");

        if (!isAdmin) {
            // Check if user is customer or merchant in this payment
            boolean isCustomer = payment.getCustomerId().equals(authenticatedUserId);
            boolean isMerchant = payment.getMerchantId().equals(authenticatedUserId);

            if (!isCustomer && !isMerchant) {
                log.warn("🚨 SECURITY: IDOR attempt - User {} tried to view payment {} " +
                        "(customer={}, merchant={})",
                        authenticatedUserId, paymentId, payment.getCustomerId(), payment.getMerchantId());
                throw new AccessDeniedException("Cannot view payment for another user");
            }
        }

        return ResponseEntity.ok(payment);
    }

    /**
     * Get user's payment history.
     *
     * SECURITY:
     * - Requires USER or MERCHANT role
     * - Can only view own payment history
     *
     * @param userId User ID
     * @param pageable Pagination parameters
     * @return Page of payments
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @Operation(summary = "Get user payments", description = "Get payment history for a user")
    public ResponseEntity<Page<PaymentResponse>> getUserPayments(
            @PathVariable UUID userId,
            Pageable pageable) {

        log.debug("Get user payments: userId={}", userId);

        // CRITICAL: Validate user can only access their own data (unless ADMIN)
        UUID authenticatedUserId = SecurityContextUtil.getCurrentUserId();
        boolean isAdmin = SecurityContextUtil.hasRole("ADMIN");

        if (!isAdmin && !userId.equals(authenticatedUserId)) {
            log.warn("🚨 SECURITY: IDOR attempt - User {} tried to view payments for user {}",
                    authenticatedUserId, userId);
            throw new AccessDeniedException("Cannot view payments for another user");
        }

        Page<PaymentResponse> payments = paymentService.getUserPayments(userId, pageable);

        return ResponseEntity.ok(payments);
    }

    /**
     * Cancel a payment.
     *
     * SECURITY:
     * - Requires USER or MERCHANT role
     * - Can only cancel own payments
     * - Only PENDING payments can be cancelled
     *
     * @param paymentId Payment ID
     * @return Cancelled payment details
     */
    @PostMapping("/{paymentId}/cancel")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
    @Operation(summary = "Cancel payment", description = "Cancel a pending payment")
    public ResponseEntity<PaymentResponse> cancelPayment(@PathVariable UUID paymentId) {

        log.info("Cancel payment request: paymentId={}", paymentId);

        // Get payment to verify ownership
        PaymentResponse payment = paymentService.getPayment(paymentId);

        // CRITICAL: Verify user owns this payment
        UUID authenticatedUserId = SecurityContextUtil.getCurrentUserId();
        if (!payment.getCustomerId().equals(authenticatedUserId)) {
            log.warn("🚨 SECURITY: User {} tried to cancel payment {} owned by {}",
                    authenticatedUserId, paymentId, payment.getCustomerId());
            throw new AccessDeniedException("Cannot cancel payment for another user");
        }

        // Cancel payment
        PaymentResponse cancelled = paymentService.cancelPayment(paymentId);

        log.info("✅ Payment cancelled: paymentId={}", paymentId);

        return ResponseEntity.ok(cancelled);
    }

    /**
     * Refund a payment (ADMIN only).
     *
     * SECURITY:
     * - Requires ADMIN role
     * - Creates audit trail
     * - Notifies all parties
     *
     * @param paymentId Payment ID
     * @param request Refund request
     * @return Refund details
     */
    @PostMapping("/{paymentId}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Refund payment", description = "Issue refund for a payment (ADMIN only)")
    public ResponseEntity<RefundResponse> refundPayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody RefundRequest request) {

        log.info("Refund request: paymentId={}, amount={}, reason={}, admin={}",
                paymentId, request.getAmount(), request.getReason(),
                SecurityContextUtil.getCurrentUserId());

        // Validate refund amount
        financialValidator.validateAmount(request.getAmount(), "refundAmount");

        // Process refund
        RefundResponse refund = paymentService.refundPayment(paymentId, request);

        log.info("✅ Refund processed: paymentId={}, refundId={}", paymentId, refund.getId());

        return ResponseEntity.ok(refund);
    }

    /**
     * Get all payments (ADMIN only).
     *
     * SECURITY:
     * - Requires ADMIN role
     * - Full access to all payments
     * - Audit logged
     *
     * @param pageable Pagination parameters
     * @return Page of all payments
     */
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all payments", description = "Get all payments in system (ADMIN only)")
    public ResponseEntity<Page<PaymentResponse>> getAllPayments(Pageable pageable) {

        log.info("Admin payment query: admin={}", SecurityContextUtil.getCurrentUserId());

        Page<PaymentResponse> payments = paymentService.getAllPayments(pageable);

        return ResponseEntity.ok(payments);
    }

    /**
     * Get payments by status (ADMIN/COMPLIANCE).
     *
     * SECURITY:
     * - Requires ADMIN or COMPLIANCE role
     * - Used for monitoring and audits
     *
     * @param status Payment status
     * @param pageable Pagination parameters
     * @return Page of payments with status
     */
    @GetMapping("/admin/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")
    @Operation(summary = "Get payments by status", description = "Get payments filtered by status")
    public ResponseEntity<Page<PaymentResponse>> getPaymentsByStatus(
            @PathVariable String status,
            Pageable pageable) {

        log.info("Status query: status={}, user={}", status, SecurityContextUtil.getCurrentUserId());

        Page<PaymentResponse> payments = paymentService.getPaymentsByStatus(status, pageable);

        return ResponseEntity.ok(payments);
    }

    /**
     * Get merchant payments.
     *
     * SECURITY:
     * - Requires MERCHANT or ADMIN role
     * - Merchants can only view their own received payments
     *
     * @param merchantId Merchant ID
     * @param pageable Pagination parameters
     * @return Page of merchant payments
     */
    @GetMapping("/merchant/{merchantId}")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    @Operation(summary = "Get merchant payments", description = "Get payments received by merchant")
    public ResponseEntity<Page<PaymentResponse>> getMerchantPayments(
            @PathVariable UUID merchantId,
            Pageable pageable) {

        log.debug("Merchant payment query: merchantId={}", merchantId);

        // CRITICAL: Validate merchant can only access their own data (unless ADMIN)
        UUID authenticatedUserId = SecurityContextUtil.getCurrentUserId();
        boolean isAdmin = SecurityContextUtil.hasRole("ADMIN");

        if (!isAdmin && !merchantId.equals(authenticatedUserId)) {
            log.warn("🚨 SECURITY: Merchant {} tried to view payments for merchant {}",
                    authenticatedUserId, merchantId);
            throw new AccessDeniedException("Cannot view payments for another merchant");
        }

        Page<PaymentResponse> payments = paymentService.getMerchantPayments(merchantId, pageable);

        return ResponseEntity.ok(payments);
    }

    /**
     * Retry failed payment (ADMIN only).
     *
     * SECURITY:
     * - Requires ADMIN role
     * - Creates audit trail
     *
     * @param paymentId Payment ID
     * @return Retry result
     */
    @PostMapping("/{paymentId}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Retry payment", description = "Retry a failed payment (ADMIN only)")
    public ResponseEntity<PaymentResponse> retryPayment(@PathVariable UUID paymentId) {

        log.info("Retry payment: paymentId={}, admin={}",
                paymentId, SecurityContextUtil.getCurrentUserId());

        PaymentResponse payment = paymentService.retryPayment(paymentId);

        log.info("✅ Payment retry initiated: paymentId={}", paymentId);

        return ResponseEntity.ok(payment);
    }

    /**
     * Get payment analytics (ADMIN/COMPLIANCE).
     *
     * SECURITY:
     * - Requires ADMIN or COMPLIANCE role
     * - Read-only analytics data
     *
     * @param request Analytics request parameters
     * @return Payment analytics
     */
    @PostMapping("/admin/analytics")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")
    @Operation(summary = "Get payment analytics", description = "Get payment analytics and statistics")
    public ResponseEntity<PaymentAnalyticsResponse> getPaymentAnalytics(
            @Valid @RequestBody PaymentAnalyticsRequest request) {

        log.info("Analytics query: dateRange={}, user={}",
                request.getDateRange(), SecurityContextUtil.getCurrentUserId());

        PaymentAnalyticsResponse analytics = paymentService.getPaymentAnalytics(request);

        return ResponseEntity.ok(analytics);
    }
}
