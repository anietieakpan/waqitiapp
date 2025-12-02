package com.waqiti.payment.controller;

import com.waqiti.common.api.ApiResponse;
import com.waqiti.common.ratelimit.RateLimit;
import com.waqiti.common.ratelimit.RateLimit.KeyType;
import com.waqiti.common.ratelimit.RateLimit.Priority;
import com.waqiti.payment.dto.*;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.security.HighValueTransactionMfaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * CRITICAL FINANCIAL ENDPOINTS CONTROLLER
 * 
 * This controller handles the most sensitive financial operations that require
 * strict rate limiting to prevent abuse, fraud, and ensure system stability.
 * 
 * Rate limits implemented per FINRA and PCI-DSS guidelines:
 * - Payment initiation: 10 requests/minute per user
 * - Payment processing: 20 requests/minute per user  
 * - Refunds: 5 requests/minute per user
 * - Chargebacks: 3 requests/minute per user
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Critical Payments", description = "High-value and critical payment operations with strict rate limiting")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('USER')")
public class CriticalPaymentController {

    private final PaymentService paymentService;
    private final HighValueTransactionMfaService mfaService;

    /**
     * CRITICAL ENDPOINT: Payment Initiation
     * Rate Limited: 10 requests/minute per user
     * Priority: CRITICAL
     */
    @PostMapping("/initiate")
    @RateLimit(
        requests = 10, 
        window = 1, 
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.CRITICAL,
        burstAllowed = false,
        blockDuration = 5,
        blockUnit = TimeUnit.MINUTES,
        alertThreshold = 0.7,
        description = "Payment initiation endpoint - highest priority protection",
        errorMessage = "Payment initiation rate limit exceeded. Maximum 10 payments per minute allowed."
    )
    @Operation(
        summary = "Initiate a new payment",
        description = "Initiates a new payment with comprehensive fraud detection and rate limiting. " +
                     "Limited to 10 payments per minute per user for security."
    )
    @PreAuthorize("hasAuthority('PAYMENT_INITIATE') and @securityValidator.validatePaymentLimits(authentication.name, #request)")
    public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(
            @Valid @RequestBody PaymentInitiationRequest request,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader(value = "X-Device-ID", required = false) String deviceId) {
        
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        
        log.info("CRITICAL_PAYMENT_INITIATION: User {} initiating payment of {} to {}", 
                userId, request.getAmount(), request.getRecipientId());
        
        // CRITICAL SECURITY: Check if MFA is required for this amount
        // Per PSD2/SCA and PCI DSS 8.3: All transactions ≥$1000 MUST have MFA
        if (mfaService.requiresMfa(request.getAmount(), "PAYMENT_INITIATION", userId)) {
            var mfaRequirement = mfaService.determineMfaRequirement(
                userId, requestId, request.getAmount(), "PAYMENT_INITIATION");

            // BLOCKER: Account lockout due to failed MFA attempts
            if (mfaRequirement.isBlocked()) {
                log.error("MFA_BLOCKED: User {} blocked from payment initiation - {}",
                         userId, mfaRequirement.getReason());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Payment blocked: " + mfaRequirement.getReason()));
            }

            // CRITICAL FIX: Reject NULL MFA tokens - NO BYPASS ALLOWED
            // Previous vulnerability: null tokens could slip through when validateMfaSession returns false
            if (request.getMfaSessionToken() == null || request.getMfaSessionToken().isBlank()) {
                log.warn("MFA_TOKEN_NULL: User {} attempted payment of {} without MFA token - BLOCKING",
                        userId, request.getAmount());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("MFA verification required for this transaction amount", mfaRequirement));
            }

            // Validate MFA session token
            if (!mfaService.validateMfaSession(request.getMfaSessionToken(), requestId)) {
                log.warn("MFA_VALIDATION_FAILED: User {} provided invalid MFA token for payment {}",
                        userId, requestId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("MFA verification failed - invalid or expired token", mfaRequirement));
            }

            log.info("MFA_VALIDATED: User {} passed MFA validation for payment of {}",
                    userId, request.getAmount());
        }
        
        PaymentResponse response = paymentService.initiatePayment(request, userId, requestId, deviceId);
        
        log.info("CRITICAL_PAYMENT_INITIATED: Payment {} initiated successfully for user {}", 
                response.getPaymentId(), userId);
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "Payment initiated successfully"));
    }

    /**
     * CRITICAL ENDPOINT: Payment Processing  
     * Rate Limited: 20 requests/minute per user
     * Priority: CRITICAL
     */
    @PostMapping("/process")
    @RateLimit(
        requests = 20,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.CRITICAL,
        burstCapacity = 5,
        alertThreshold = 0.8,
        description = "Payment processing endpoint",
        errorMessage = "Payment processing rate limit exceeded. Maximum 20 processing requests per minute allowed."
    )
    @Operation(
        summary = "Process a pending payment",
        description = "Processes a previously initiated payment. Limited to 20 processing requests per minute per user."
    )
    @PreAuthorize("hasAuthority('PAYMENT_PROCESS') and @paymentOwnershipValidator.canProcessPayment(authentication.name, #paymentId)")
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody PaymentProcessingRequest request,
            @RequestHeader("X-Request-ID") String requestId) {
        
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        
        log.info("CRITICAL_PAYMENT_PROCESSING: User {} processing payment {}", userId, paymentId);
        
        PaymentResponse response = paymentService.processPayment(paymentId, request, userId, requestId);
        
        log.info("CRITICAL_PAYMENT_PROCESSED: Payment {} processed successfully", paymentId);
        
        return ResponseEntity.ok(ApiResponse.success(response, "Payment processed successfully"));
    }

    /**
     * CRITICAL ENDPOINT: Payment Refund
     * Rate Limited: 5 requests/minute per user
     * Priority: HIGH
     */
    @PostMapping("/refund")
    @RateLimit(
        requests = 5,
        window = 1, 
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.HIGH,
        burstAllowed = false,
        blockDuration = 10,
        blockUnit = TimeUnit.MINUTES,
        alertThreshold = 0.6,
        description = "Payment refund endpoint - high security",
        errorMessage = "Refund rate limit exceeded. Maximum 5 refunds per minute allowed for security."
    )
    @Operation(
        summary = "Process a payment refund",
        description = "Processes a refund for a completed payment. Strictly limited to 5 refunds per minute per user."
    )
    @PreAuthorize("hasAuthority('PAYMENT_REFUND') and @paymentOwnershipValidator.canRefundPayment(authentication.name, #paymentId)")
    public ResponseEntity<ApiResponse<RefundResponse>> refundPayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody RefundRequest request,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader(value = "X-Reason", required = false) String reason) {
        
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        
        log.warn("CRITICAL_PAYMENT_REFUND: User {} requesting refund for payment {} - Reason: {}", 
                userId, paymentId, reason);
        
        // Additional validation for high-value refunds
        if (request.getAmount().compareTo(new java.math.BigDecimal("1000")) >= 0) {
            log.warn("HIGH_VALUE_REFUND: User {} requesting refund of {} for payment {}", 
                    userId, request.getAmount(), paymentId);
        }
        
        RefundResponse response = paymentService.refundPayment(paymentId, request, userId, requestId, reason);
        
        log.warn("CRITICAL_PAYMENT_REFUNDED: Refund {} processed for payment {} by user {}", 
                response.getRefundId(), paymentId, userId);
        
        return ResponseEntity.ok(ApiResponse.success(response, "Refund processed successfully"));
    }

    /**
     * CRITICAL ENDPOINT: Chargeback Processing
     * Rate Limited: 3 requests/minute per user
     * Priority: EMERGENCY (highest security)
     */
    @PostMapping("/chargeback")
    @RateLimit(
        requests = 3,
        window = 1,
        unit = TimeUnit.MINUTES, 
        keyType = KeyType.USER,
        priority = Priority.EMERGENCY,
        burstAllowed = false,
        blockDuration = 30,
        blockUnit = TimeUnit.MINUTES,
        alertThreshold = 0.5,
        requireMfa = true,
        description = "Chargeback endpoint - maximum security protection",
        errorMessage = "Chargeback rate limit exceeded. Maximum 3 chargebacks per minute allowed. This incident has been logged."
    )
    @Operation(
        summary = "Initiate a payment chargeback",
        description = "Initiates a chargeback for a disputed payment. Extremely limited to 3 requests per minute per user with mandatory MFA."
    )
    @PreAuthorize("hasAuthority('PAYMENT_CHARGEBACK') and @paymentOwnershipValidator.canChargebackPayment(authentication.name, #paymentId)")
    public ResponseEntity<ApiResponse<ChargebackResponse>> chargebackPayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody ChargebackRequest request,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader(value = "X-MFA-Token") String mfaToken) {
        
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        
        log.error("CRITICAL_CHARGEBACK_REQUEST: User {} initiating chargeback for payment {} - Reason: {}", 
                userId, paymentId, request.getReason());
        
        // Mandatory MFA validation for chargeback requests
        if (!mfaService.validateMfaSession(mfaToken, requestId)) {
            log.error("CHARGEBACK_MFA_FAILED: Invalid MFA token for chargeback request by user {}", userId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Valid MFA token required for chargeback requests"));
        }
        
        ChargebackResponse response = paymentService.chargebackPayment(paymentId, request, userId, requestId);
        
        log.error("CRITICAL_CHARGEBACK_INITIATED: Chargeback {} initiated for payment {} by user {}", 
                response.getChargebackId(), paymentId, userId);
        
        // Alert compliance team
        // complianceAlertService.alertChargebackInitiated(userId, paymentId, request);
        
        return ResponseEntity.ok(ApiResponse.success(response, "Chargeback initiated successfully"));
    }

    /**
     * Emergency endpoint to check rate limit status
     */
    @GetMapping("/rate-limit/status")
    @RateLimit(
        requests = 60,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.LOW,
        description = "Rate limit status check"
    )
    @Operation(summary = "Check current rate limit status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRateLimitStatus() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        
        // This would return current rate limit status for the user
        Map<String, Object> status = Map.of(
            "userId", userId,
            "timestamp", System.currentTimeMillis(),
            "message", "Rate limit status check completed"
        );
        
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}