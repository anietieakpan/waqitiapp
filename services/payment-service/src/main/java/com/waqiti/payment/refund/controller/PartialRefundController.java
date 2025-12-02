package com.waqiti.payment.refund.controller;

import com.waqiti.common.audit.service.AuditService;
import com.waqiti.common.client.FraudDetectionServiceClient;
import com.waqiti.common.dto.FraudCheckRequest;
import com.waqiti.common.dto.FraudCheckResult;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.ratelimit.RateLimited;
import com.waqiti.payment.core.model.RefundRequest;
import com.waqiti.payment.refund.model.RefundResult;
import com.waqiti.payment.refund.service.PartialRefundCalculator;
import com.waqiti.payment.refund.service.PartialRefundCalculator.PartialRefundSummary;
import com.waqiti.payment.refund.service.PaymentRefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Duration;
import java.util.UUID;

/**
 * P2 ENHANCEMENT: Partial Refund REST API
 *
 * Provides endpoints for merchants to issue partial refunds with validation
 * and preview capabilities.
 *
 * ENDPOINTS:
 * 1. POST /api/v1/refunds/partial - Issue partial refund
 * 2. POST /api/v1/refunds/partial/preview - Preview partial refund calculation
 * 3. GET /api/v1/refunds/partial/remaining/{paymentId} - Get remaining refundable amount
 *
 * @author Waqiti Payment Team
 * @since 1.0 (P2 Enhancement)
 */
@RestController
@RequestMapping("/api/v1/refunds/partial")
@Tag(name = "Partial Refunds", description = "Partial refund operations for merchants")
@Slf4j
@RequiredArgsConstructor
public class PartialRefundController {

    private final PaymentRefundService refundService;
    private final PartialRefundCalculator partialRefundCalculator;
    private final FraudDetectionServiceClient fraudDetectionClient;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;

    // Security Constants
    private static final BigDecimal MIN_REFUND_AMOUNT = new BigDecimal("0.01");
    private static final BigDecimal MAX_REFUND_AMOUNT = new BigDecimal("50000.00");
    private static final String IDEMPOTENCY_PREFIX = "partial-refund:";

    /**
     * Issues a partial refund
     *
     * Allows merchants to refund a portion of the original payment amount.
     * Validates that the partial refund doesn't exceed remaining refundable amount.
     *
     * SECURITY ENHANCEMENTS (Production-Grade):
     * - Rate limiting: 10 refunds per hour per user
     * - Fraud detection: Real-time risk scoring
     * - Amount validation: Min $0.01, Max $50,000
     * - Idempotency: Prevents duplicate refunds
     * - Audit logging: Complete security event trail
     * - Ownership validation: Via @PreAuthorize
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('REFUND_CREATE', 'MERCHANT_ADMIN') and " +
                  "@accountOwnershipValidator.canCreateRefund(authentication.name, #request.paymentId)")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 60)
    @Operation(summary = "Issue partial refund",
               description = "Issues a partial refund for a payment. Validates amount against remaining refundable balance.")
    public ResponseEntity<RefundResult> issuePartialRefund(
            @Valid @RequestBody PartialRefundRequest request,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId,
            @RequestHeader(value = "X-Device-ID", required = false) String deviceId,
            Principal principal) {

        String username = principal.getName();
        long startTime = System.currentTimeMillis();

        log.info("PARTIAL REFUND: Request received - Payment: {}, Amount: ${}, Reason: {}, User: {}, RequestID: {}",
            request.getPaymentId(), request.getAmount(), request.getReason(), username, requestId);

        try {
            // 1. AUDIT LOGGING - Security event tracking
            auditService.logSecurityEvent(
                "PARTIAL_REFUND_REQUEST",
                username,
                "PartialRefundController.issuePartialRefund",
                String.format("Payment: %s, Amount: %s, Reason: %s",
                    request.getPaymentId(), request.getAmount(), request.getReason()),
                request.getPaymentId().toString(),
                requestId,
                deviceId
            );

            // 2. AMOUNT VALIDATION - Prevent abuse
            if (request.getAmount() == null) {
                throw new IllegalArgumentException("Refund amount is required");
            }
            if (request.getAmount().compareTo(MIN_REFUND_AMOUNT) < 0) {
                throw new IllegalArgumentException(
                    "Refund amount must be at least $" + MIN_REFUND_AMOUNT);
            }
            if (request.getAmount().compareTo(MAX_REFUND_AMOUNT) > 0) {
                throw new IllegalArgumentException(
                    "Refund amount exceeds maximum allowed: $" + MAX_REFUND_AMOUNT);
            }

            // 3. FRAUD DETECTION - Real-time risk scoring
            log.debug("PARTIAL REFUND: Running fraud detection for payment: {}", request.getPaymentId());

            FraudCheckRequest fraudCheck = FraudCheckRequest.builder()
                .userId(request.getMerchantId() != null ? request.getMerchantId().toString() : username)
                .transactionType("PARTIAL_REFUND")
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .paymentId(request.getPaymentId().toString())
                .deviceId(deviceId)
                .requestId(requestId)
                .metadata(request.getMetadata())
                .build();

            FraudCheckResult fraudResult;
            try {
                fraudResult = fraudDetectionClient.checkFraud(fraudCheck);
            } catch (Exception e) {
                // FAIL-SECURE: Block refund if fraud service is down
                log.error("PARTIAL REFUND: Fraud detection service unavailable - BLOCKING refund for payment: {}",
                    request.getPaymentId(), e);

                auditService.logSecurityEvent(
                    "PARTIAL_REFUND_BLOCKED_FRAUD_SERVICE_DOWN",
                    username,
                    "PartialRefundController.issuePartialRefund",
                    "Fraud service unavailable - refund blocked",
                    request.getPaymentId().toString(),
                    requestId,
                    deviceId
                );

                throw new IllegalStateException(
                    "Refund processing temporarily unavailable. Please try again later.");
            }

            // Block high-risk refunds
            if ("HIGH".equals(fraudResult.getRiskLevel()) || "BLOCK".equals(fraudResult.getDecision())) {
                log.warn("PARTIAL REFUND: HIGH RISK DETECTED - Payment: {}, Risk Score: {}, Reason: {}",
                    request.getPaymentId(), fraudResult.getRiskScore(), fraudResult.getReason());

                auditService.logSecurityEvent(
                    "PARTIAL_REFUND_BLOCKED_HIGH_RISK",
                    username,
                    "PartialRefundController.issuePartialRefund",
                    String.format("Risk Score: %.2f, Reason: %s", fraudResult.getRiskScore(), fraudResult.getReason()),
                    request.getPaymentId().toString(),
                    requestId,
                    deviceId
                );

                throw new IllegalStateException(
                    "Refund request flagged for security review. Please contact support.");
            }

            log.info("PARTIAL REFUND: Fraud check passed - Payment: {}, Risk Score: {}, Decision: {}",
                request.getPaymentId(), fraudResult.getRiskScore(), fraudResult.getDecision());

            // 4. IDEMPOTENT EXECUTION - Prevent duplicate refunds
            String idempotencyKeyFull = IDEMPOTENCY_PREFIX + idempotencyKey;

            RefundResult result = idempotencyService.executeIdempotent(
                idempotencyKeyFull,
                Duration.ofHours(24),
                () -> {
                    // Convert to standard refund request
                    RefundRequest refundRequest = RefundRequest.builder()
                        .paymentId(request.getPaymentId())
                        .amount(request.getAmount())
                        .currency(request.getCurrency())
                        .reason(request.getReason())
                        .customerId(request.getCustomerId())
                        .merchantId(request.getMerchantId())
                        .requestedBy(request.getRequestedBy())
                        .metadata(request.getMetadata())
                        .build();

                    // Process refund (includes PartialRefundCalculator validation)
                    return refundService.processRefund(refundRequest);
                },
                RefundResult.class
            );

            long processingTime = System.currentTimeMillis() - startTime;

            log.info("PARTIAL REFUND: Successfully processed - Payment: {}, Refund ID: {}, Amount: ${}, " +
                "Risk Score: {}, Processing Time: {}ms",
                request.getPaymentId(), result.getRefundId(), request.getAmount(),
                fraudResult.getRiskScore(), processingTime);

            // 5. AUDIT SUCCESS
            auditService.logSecurityEvent(
                "PARTIAL_REFUND_SUCCESS",
                username,
                "PartialRefundController.issuePartialRefund",
                String.format("Refund ID: %s, Amount: %s, Processing Time: %dms",
                    result.getRefundId(), request.getAmount(), processingTime),
                request.getPaymentId().toString(),
                requestId,
                deviceId
            );

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.warn("PARTIAL REFUND: Validation failed - Payment: {}, Error: {}",
                request.getPaymentId(), e.getMessage());

            auditService.logSecurityEvent(
                "PARTIAL_REFUND_VALIDATION_FAILED",
                username,
                "PartialRefundController.issuePartialRefund",
                e.getMessage(),
                request.getPaymentId().toString(),
                requestId,
                deviceId
            );

            throw e;

        } catch (Exception e) {
            log.error("PARTIAL REFUND: Processing failed - Payment: {}, User: {}",
                request.getPaymentId(), username, e);

            auditService.logSecurityEvent(
                "PARTIAL_REFUND_ERROR",
                username,
                "PartialRefundController.issuePartialRefund",
                e.getMessage(),
                request.getPaymentId().toString(),
                requestId,
                deviceId
            );

            throw e;
        }
    }

    /**
     * Previews a partial refund calculation without executing it
     *
     * Merchants can use this to show customers the exact refund breakdown
     * before confirming the refund.
     *
     * SECURITY: Audit logging for data access tracking
     */
    @PostMapping("/preview")
    @PreAuthorize("hasAnyAuthority('REFUND_CREATE', 'REFUND_VIEW', 'MERCHANT_ADMIN')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 30, refillTokens = 30, refillPeriodMinutes = 60)
    @Operation(summary = "Preview partial refund",
               description = "Calculates and previews partial refund breakdown without executing the refund")
    public ResponseEntity<PartialRefundSummary> previewPartialRefund(
            @Valid @RequestBody PartialRefundPreviewRequest request,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId,
            Principal principal) {

        String username = principal != null ? principal.getName() : "anonymous";

        log.info("PARTIAL REFUND PREVIEW: Payment: {}, Requested amount: ${}, User: {}",
            request.getPaymentId(), request.getAmount(), username);

        try {
            // Audit data access
            auditService.logDataAccess(
                "PARTIAL_REFUND_PREVIEW",
                username,
                "PartialRefundController.previewPartialRefund",
                String.format("Payment: %s, Requested Amount: %s",
                    request.getPaymentId(), request.getAmount()),
                request.getPaymentId().toString(),
                requestId
            );

            // Validate the partial refund
            partialRefundCalculator.validatePartialRefund(
                request.getPaymentId(),
                request.getOriginalAmount(),
                request.getAmount()
            );

            // Generate summary
            PartialRefundSummary summary = partialRefundCalculator.generatePartialRefundSummary(
                request.getPaymentId(),
                request.getOriginalAmount(),
                request.getOriginalFee(),
                request.getAmount()
            );

            log.info("PARTIAL REFUND PREVIEW: {}", summary.toHumanReadableString());

            return ResponseEntity.ok(summary);

        } catch (IllegalArgumentException e) {
            log.warn("PARTIAL REFUND PREVIEW: Validation failed - Payment: {}, Error: {}",
                request.getPaymentId(), e.getMessage());
            throw e;
        }
    }

    /**
     * Gets the remaining refundable amount for a payment
     *
     * SECURITY: Audit logging for data access tracking
     */
    @GetMapping("/remaining/{paymentId}")
    @PreAuthorize("hasAnyAuthority('REFUND_VIEW', 'MERCHANT_ADMIN')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 50, refillTokens = 50, refillPeriodMinutes = 60)
    @Operation(summary = "Get remaining refundable amount",
               description = "Returns the remaining refundable amount for a payment after accounting for previous refunds")
    public ResponseEntity<RemainingRefundableResponse> getRemainingRefundable(
            @PathVariable UUID paymentId,
            @RequestParam BigDecimal originalAmount,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId,
            Principal principal) {

        String username = principal != null ? principal.getName() : "anonymous";

        log.debug("PARTIAL REFUND: Getting remaining refundable for payment: {}, User: {}", paymentId, username);

        // Audit data access
        auditService.logDataAccess(
            "PARTIAL_REFUND_REMAINING_QUERY",
            username,
            "PartialRefundController.getRemainingRefundable",
            String.format("Payment: %s, Original Amount: %s", paymentId, originalAmount),
            paymentId.toString(),
            requestId
        );

        BigDecimal remainingAmount = partialRefundCalculator.calculateRemainingRefundableAmount(
            paymentId, originalAmount
        );

        boolean eligible = partialRefundCalculator.isEligibleForPartialRefund(paymentId, originalAmount);

        RemainingRefundableResponse response = RemainingRefundableResponse.builder()
            .paymentId(paymentId)
            .originalAmount(originalAmount)
            .remainingRefundable(remainingAmount)
            .eligibleForPartialRefund(eligible)
            .build();

        return ResponseEntity.ok(response);
    }

    // DTOs

    @lombok.Data
    @lombok.Builder
    public static class PartialRefundRequest {
        private UUID paymentId;
        private BigDecimal amount;
        private String currency;
        private String reason;
        private UUID customerId;
        private UUID merchantId;
        private String requestedBy;
        private java.util.Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    public static class PartialRefundPreviewRequest {
        private UUID paymentId;
        private BigDecimal originalAmount;
        private BigDecimal originalFee;
        private BigDecimal amount;
    }

    @lombok.Data
    @lombok.Builder
    public static class RemainingRefundableResponse {
        private UUID paymentId;
        private BigDecimal originalAmount;
        private BigDecimal remainingRefundable;
        private boolean eligibleForPartialRefund;
    }
}
