package com.waqiti.bnpl.controller;

import com.waqiti.bnpl.dto.request.ProcessPaymentRequest;
import com.waqiti.bnpl.dto.response.BnplTransactionDto;
import com.waqiti.bnpl.service.BnplPlanService;
import com.waqiti.common.audit.service.AuditService;
import com.waqiti.common.client.FraudDetectionServiceClient;
import com.waqiti.common.dto.FraudCheckRequest;
import com.waqiti.common.dto.FraudCheckResult;
import com.waqiti.common.ratelimit.RateLimited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.HashMap;

/**
 * REST controller for BNPL payment operations
 *
 * SECURITY ENHANCEMENTS (Production-Grade):
 * - Ownership validation: Ensures users can only pay their own plans
 * - Rate limiting: 20 payments per hour per user
 * - Fraud detection: Real-time ML-based risk scoring
 * - Amount validation: Min/Max limits per installment
 * - Audit logging: Complete security event trail
 */
@RestController
@RequestMapping("/api/v1/bnpl/payments")
@Tag(name = "BNPL Payments", description = "BNPL payment processing endpoints")
@Slf4j
@RequiredArgsConstructor
public class BnplPaymentController {

    private final BnplPlanService bnplPlanService;
    private final FraudDetectionServiceClient fraudDetectionClient;
    private final AuditService auditService;

    // Security Constants
    private static final BigDecimal MIN_PAYMENT_AMOUNT = new BigDecimal("1.00");
    private static final BigDecimal MAX_PAYMENT_AMOUNT = new BigDecimal("10000.00");

    /**
     * Process BNPL installment payment
     *
     * SECURITY LAYERS:
     * 1. Ownership validation - @PreAuthorize with @bnplOwnershipValidator
     * 2. Rate limiting - 20 payments/hour per user
     * 3. Amount validation - Min $1.00, Max $10,000
     * 4. Fraud detection - Real-time risk scoring
     * 5. Audit logging - Security event trail
     */
    @PostMapping
    @Operation(summary = "Process BNPL installment payment",
               description = "Processes a BNPL installment payment with comprehensive security validation")
    @PreAuthorize("hasRole('USER') and @bnplOwnershipValidator.canProcessPayment(authentication.name, #request.planId, #request.userId)")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 20, refillTokens = 20, refillPeriodMinutes = 60)
    public ResponseEntity<BnplTransactionDto> processPayment(
            @Valid @RequestBody ProcessPaymentRequest request,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId,
            @RequestHeader(value = "X-Device-ID", required = false) String deviceId,
            Principal principal) {

        String username = principal.getName();
        long startTime = System.currentTimeMillis();

        log.info("BNPL PAYMENT: Request received - Plan: {}, Amount: ${}, User: {}, RequestID: {}",
                request.getPlanId(), request.getAmount(), username, requestId);

        try {
            // 1. AUDIT LOGGING - Security event tracking
            auditService.logSecurityEvent(
                "BNPL_PAYMENT_REQUEST",
                username,
                "BnplPaymentController.processPayment",
                String.format("Plan: %s, Amount: %s", request.getPlanId(), request.getAmount()),
                request.getPlanId().toString(),
                requestId,
                deviceId
            );

            // 2. AMOUNT VALIDATION - Prevent abuse
            if (request.getAmount() == null) {
                throw new IllegalArgumentException("Payment amount is required");
            }
            if (request.getAmount().compareTo(MIN_PAYMENT_AMOUNT) < 0) {
                throw new IllegalArgumentException(
                    "Payment amount must be at least $" + MIN_PAYMENT_AMOUNT);
            }
            if (request.getAmount().compareTo(MAX_PAYMENT_AMOUNT) > 0) {
                throw new IllegalArgumentException(
                    "Payment amount exceeds maximum allowed: $" + MAX_PAYMENT_AMOUNT);
            }

            // 3. FRAUD DETECTION - Real-time risk scoring
            log.debug("BNPL PAYMENT: Running fraud detection for plan: {}", request.getPlanId());

            FraudCheckRequest fraudCheck = FraudCheckRequest.builder()
                .userId(request.getUserId() != null ? request.getUserId().toString() : username)
                .transactionType("BNPL_INSTALLMENT_PAYMENT")
                .amount(request.getAmount())
                .currency("USD") // Default currency for BNPL
                .paymentId(request.getPlanId().toString())
                .deviceId(deviceId)
                .requestId(requestId)
                .metadata(new HashMap<>())
                .build();

            FraudCheckResult fraudResult;
            try {
                fraudResult = fraudDetectionClient.checkFraud(fraudCheck);
            } catch (Exception e) {
                // FAIL-SECURE: Block payment if fraud service is down
                log.error("BNPL PAYMENT: Fraud detection service unavailable - BLOCKING payment for plan: {}",
                    request.getPlanId(), e);

                auditService.logSecurityEvent(
                    "BNPL_PAYMENT_BLOCKED_FRAUD_SERVICE_DOWN",
                    username,
                    "BnplPaymentController.processPayment",
                    "Fraud service unavailable - payment blocked",
                    request.getPlanId().toString(),
                    requestId,
                    deviceId
                );

                throw new IllegalStateException(
                    "Payment processing temporarily unavailable. Please try again later.");
            }

            // Block high-risk payments
            if ("HIGH".equals(fraudResult.getRiskLevel()) || "BLOCK".equals(fraudResult.getDecision())) {
                log.warn("BNPL PAYMENT: HIGH RISK DETECTED - Plan: {}, Risk Score: {}, Reason: {}",
                    request.getPlanId(), fraudResult.getRiskScore(), fraudResult.getReason());

                auditService.logSecurityEvent(
                    "BNPL_PAYMENT_BLOCKED_HIGH_RISK",
                    username,
                    "BnplPaymentController.processPayment",
                    String.format("Risk Score: %.2f, Reason: %s", fraudResult.getRiskScore(), fraudResult.getReason()),
                    request.getPlanId().toString(),
                    requestId,
                    deviceId
                );

                throw new IllegalStateException(
                    "Payment request flagged for security review. Please contact support.");
            }

            log.info("BNPL PAYMENT: Fraud check passed - Plan: {}, Risk Score: {}, Decision: {}",
                request.getPlanId(), fraudResult.getRiskScore(), fraudResult.getDecision());

            // 4. PROCESS PAYMENT
            BnplTransactionDto transaction = bnplPlanService.processInstallmentPayment(request);

            long processingTime = System.currentTimeMillis() - startTime;

            log.info("BNPL PAYMENT: Successfully processed - Plan: {}, Transaction ID: {}, Amount: ${}, " +
                "Risk Score: {}, Processing Time: {}ms",
                request.getPlanId(), transaction.getTransactionId(), request.getAmount(),
                fraudResult.getRiskScore(), processingTime);

            // 5. AUDIT SUCCESS
            auditService.logSecurityEvent(
                "BNPL_PAYMENT_SUCCESS",
                username,
                "BnplPaymentController.processPayment",
                String.format("Transaction ID: %s, Amount: %s, Processing Time: %dms",
                    transaction.getTransactionId(), request.getAmount(), processingTime),
                request.getPlanId().toString(),
                requestId,
                deviceId
            );

            return ResponseEntity.ok(transaction);

        } catch (IllegalArgumentException e) {
            log.warn("BNPL PAYMENT: Validation failed - Plan: {}, Error: {}",
                request.getPlanId(), e.getMessage());

            auditService.logSecurityEvent(
                "BNPL_PAYMENT_VALIDATION_FAILED",
                username,
                "BnplPaymentController.processPayment",
                e.getMessage(),
                request.getPlanId().toString(),
                requestId,
                deviceId
            );

            throw e;

        } catch (Exception e) {
            log.error("BNPL PAYMENT: Processing failed - Plan: {}, User: {}",
                request.getPlanId(), username, e);

            auditService.logSecurityEvent(
                "BNPL_PAYMENT_ERROR",
                username,
                "BnplPaymentController.processPayment",
                e.getMessage(),
                request.getPlanId().toString(),
                requestId,
                deviceId
            );

            throw e;
        }
    }
}