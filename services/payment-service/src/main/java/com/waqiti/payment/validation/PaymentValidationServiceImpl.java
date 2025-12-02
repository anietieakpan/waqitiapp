package com.waqiti.payment.validation;

import com.waqiti.common.security.audit.SecurityAuditLogger;
import com.waqiti.payment.client.UserServiceClient;
import com.waqiti.payment.core.model.RefundRequest;
import com.waqiti.payment.core.model.ReconciliationRequest;
import com.waqiti.payment.domain.PaymentRequest;
import com.waqiti.payment.dto.UserResponse;
import com.waqiti.payment.repository.PaymentRequestRepository;
import com.waqiti.payment.repository.RefundTransactionRepository;
import com.waqiti.payment.refund.model.RefundValidationResult;
import com.waqiti.payment.validation.model.PaymentValidationResult;
import com.waqiti.payment.validation.model.ReconciliationValidationResult;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Production Payment Validation Service Implementation
 * 
 * Extracted from PaymentService.java during Phase 2 refactoring.
 * Provides comprehensive validation for payment operations including
 * refund validation with proper database integration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentValidationServiceImpl implements PaymentValidationService {

    private final PaymentRequestRepository paymentRequestRepository;
    private final RefundTransactionRepository refundTransactionRepository;
    private final UserServiceClient userServiceClient;
    private final SecurityAuditLogger securityAuditLogger;
    private final MeterRegistry meterRegistry;
    
    // Validation constants
    private static final BigDecimal MIN_PAYMENT_AMOUNT = new BigDecimal("0.01");
    private static final BigDecimal MAX_PAYMENT_AMOUNT = new BigDecimal("100000.00");
    
    // =====================================
    // PAYMENT VALIDATION
    // =====================================
    
    @Override
    public PaymentValidationResult validatePaymentAmount(BigDecimal amount, String currency) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.debug("Validating payment amount: {} {}", amount, currency);
            
            // Null checks
            if (amount == null) {
                return PaymentValidationResult.invalid("Payment amount is required", "AMOUNT_NULL");
            }
            
            if (currency == null || currency.trim().isEmpty()) {
                return PaymentValidationResult.invalid("Currency is required", "CURRENCY_NULL");
            }
            
            // Amount validation
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return PaymentValidationResult.invalid("Payment amount must be positive", "AMOUNT_NOT_POSITIVE");
            }
            
            if (amount.compareTo(MIN_PAYMENT_AMOUNT) < 0) {
                return PaymentValidationResult.invalid(
                    "Amount below minimum: " + MIN_PAYMENT_AMOUNT, "AMOUNT_BELOW_MINIMUM");
            }
            
            if (amount.compareTo(MAX_PAYMENT_AMOUNT) > 0) {
                return PaymentValidationResult.invalid(
                    "Amount exceeds maximum: " + MAX_PAYMENT_AMOUNT, "AMOUNT_EXCEEDS_MAXIMUM");
            }
            
            // Currency validation
            if (!isValidCurrency(currency)) {
                return PaymentValidationResult.invalid("Invalid currency code: " + currency, "INVALID_CURRENCY");
            }
            
            log.debug("Payment amount validation passed for {} {}", amount, currency);
            return PaymentValidationResult.valid();
            
        } finally {
            sample.stop(Timer.builder("payment.validation.amount.duration")
                .tag("currency", currency != null ? currency : "unknown")
                .register(meterRegistry));
        }
    }
    
    @Override
    public UserResponse validateRecipientExists(UUID recipientId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.debug("Validating recipient exists: {}", recipientId);
            
            if (recipientId == null) {
                throw new IllegalArgumentException("Recipient ID is required");
            }
            
            UserResponse recipient = userServiceClient.getUser(recipientId);
            if (recipient == null) {
                securityAuditLogger.logSecurityEvent("INVALID_RECIPIENT_VALIDATION", "system",
                    "Recipient not found during validation",
                    Map.of("recipientId", recipientId));
                throw new IllegalArgumentException("Invalid recipient: " + recipientId);
            }
            
            log.debug("Recipient validation passed for: {}", recipientId);
            return recipient;
            
        } catch (Exception e) {
            log.error("Error validating recipient: {}", e.getMessage());
            securityAuditLogger.logSecurityViolation("RECIPIENT_VALIDATION_ERROR", "system",
                "Recipient validation failed: " + e.getMessage(),
                Map.of("recipientId", recipientId, "error", e.getMessage()));
            throw new IllegalArgumentException("Invalid recipient: " + recipientId, e);
        } finally {
            sample.stop(Timer.builder("payment.validation.recipient.duration")
                .tag("recipient_id", recipientId != null ? recipientId.toString() : "unknown")
                .register(meterRegistry));
        }
    }
    
    // =====================================
    // REFUND VALIDATION (EXTRACTED FROM PaymentService)
    // =====================================
    
    @Override
    @Transactional(readOnly = true)
    public RefundValidationResult validateRefundRequest(RefundRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String validationId = UUID.randomUUID().toString();
        
        try {
            log.debug("Validating refund request for payment: {}", request.getOriginalPaymentId());
            
            // Step 1: Get original payment
            Optional<PaymentRequest> paymentOpt = paymentRequestRepository.findById(request.getOriginalPaymentId());
            if (!paymentOpt.isPresent()) {
                return RefundValidationResult.invalid("Payment not found: " + request.getOriginalPaymentId(), "PAYMENT_NOT_FOUND");
            }
            
            PaymentRequest originalPayment = paymentOpt.get();
            
            // Step 2: Check payment status
            if (!originalPayment.getStatus().equals("COMPLETED") && !originalPayment.getStatus().equals("SETTLED")) {
                return RefundValidationResult.invalid("Payment not in refundable status: " + originalPayment.getStatus(), "PAYMENT_NOT_REFUNDABLE");
            }
            
            // Step 3: Check refund amount
            if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                return RefundValidationResult.invalid("Refund amount must be positive", "INVALID_REFUND_AMOUNT");
            }
            
            if (request.getAmount().compareTo(originalPayment.getAmount()) > 0) {
                return RefundValidationResult.invalid("Refund amount cannot exceed original payment amount", "REFUND_EXCEEDS_ORIGINAL");
            }
            
            // Step 4: Check for existing refunds
            BigDecimal totalRefunded = getTotalRefundedAmount(request.getOriginalPaymentId());
            BigDecimal remainingRefundable = originalPayment.getAmount().subtract(totalRefunded);
            
            if (request.getAmount().compareTo(remainingRefundable) > 0) {
                return RefundValidationResult.invalid("Refund amount exceeds remaining refundable amount: " + remainingRefundable, "EXCEEDS_REMAINING_REFUNDABLE");
            }
            
            // Step 5: Validate refund reason
            if (request.getReason() == null || request.getReason().trim().isEmpty()) {
                return RefundValidationResult.invalid("Refund reason is required", "REFUND_REASON_REQUIRED");
            }
            
            log.debug("Refund validation passed for payment: {}", request.getOriginalPaymentId());
            
            return RefundValidationResult.valid(originalPayment)
                .toBuilder()
                .validationId(validationId)
                .validatedAt(Instant.now())
                .validatedBy("payment-validation-service")
                .build();
                
        } catch (Exception e) {
            log.error("Error validating refund request: {}", e.getMessage(), e);
            securityAuditLogger.logSecurityEvent("REFUND_VALIDATION_ERROR", "system",
                "Refund validation failed: " + e.getMessage(),
                Map.of("originalPaymentId", request.getOriginalPaymentId(), "error", e.getMessage()));
            return RefundValidationResult.invalid("Validation failed: " + e.getMessage(), "VALIDATION_ERROR");
            
        } finally {
            sample.stop(Timer.builder("payment.validation.refund.duration")
                .tag("payment_id", request.getOriginalPaymentId())
                .register(meterRegistry));
        }
    }
    
    @Override
    public boolean isWithinRefundWindow(String originalPaymentId, String paymentMethod) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            Optional<PaymentRequest> paymentOpt = paymentRequestRepository.findById(originalPaymentId);
            if (!paymentOpt.isPresent()) {
                return false;
            }
            
            PaymentRequest payment = paymentOpt.get();
            
            // Different refund windows for different payment methods
            int refundWindowDays = switch (paymentMethod.toLowerCase()) {
                case "credit_card" -> 120; // Credit card chargeback protection
                case "debit_card" -> 60;   // Shorter window for debit
                case "ach", "bank_transfer" -> 30; // ACH has shorter reversal window
                case "crypto" -> 7;        // Crypto transactions are harder to reverse
                default -> 180;            // Standard refund window
            };
            
            LocalDateTime refundDeadline = payment.getCreatedAt().plusDays(refundWindowDays);
            boolean withinWindow = LocalDateTime.now().isBefore(refundDeadline);
            
            log.debug("Refund window check for payment {}: {} (window: {} days)", 
                originalPaymentId, withinWindow, refundWindowDays);
            
            return withinWindow;
            
        } catch (Exception e) {
            log.error("Error checking refund window for payment {}", originalPaymentId, e);
            return false;
        } finally {
            sample.stop(Timer.builder("payment.validation.refund_window.duration")
                .tag("payment_method", paymentMethod)
                .register(meterRegistry));
        }
    }
    
    // =====================================
    // RECONCILIATION VALIDATION (EXTRACTED FROM PaymentService)
    // =====================================
    
    @Override
    public ReconciliationValidationResult validateReconciliationRequest(ReconciliationRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String validationId = UUID.randomUUID().toString();
        
        try {
            log.debug("Validating reconciliation request for settlement: {}", request.getSettlementId());
            
            // Step 1: Validate settlement exists
            if (request.getSettlementId() == null || request.getSettlementId().trim().isEmpty()) {
                return ReconciliationValidationResult.invalid("Settlement ID is required", "SETTLEMENT_ID_REQUIRED");
            }
            
            // Step 2: Validate amounts are provided
            if (request.getActualGrossAmount() == null || request.getActualNetAmount() == null) {
                return ReconciliationValidationResult.invalid("Actual gross and net amounts are required", "AMOUNTS_REQUIRED");
            }
            
            // Step 3: Validate amounts are positive
            if (request.getActualGrossAmount().compareTo(BigDecimal.ZERO) < 0 ||
                request.getActualNetAmount().compareTo(BigDecimal.ZERO) < 0) {
                return ReconciliationValidationResult.invalid("Settlement amounts must be non-negative", "NEGATIVE_AMOUNTS");
            }
            
            // Step 4: Validate net amount is not greater than gross
            if (request.getActualNetAmount().compareTo(request.getActualGrossAmount()) > 0) {
                return ReconciliationValidationResult.invalid("Net amount cannot exceed gross amount", "NET_EXCEEDS_GROSS");
            }
            
            // Step 5: Validate reconciliation period
            if (request.getReconciliationPeriodStart() != null && request.getReconciliationPeriodEnd() != null) {
                if (request.getReconciliationPeriodStart().isAfter(request.getReconciliationPeriodEnd())) {
                    return ReconciliationValidationResult.invalid("Start date must be before end date", "INVALID_DATE_RANGE");
                }
            }
            
            log.debug("Reconciliation validation passed for settlement: {}", request.getSettlementId());
            
            return ReconciliationValidationResult.valid()
                .toBuilder()
                .validationId(validationId)
                .settlementId(request.getSettlementId())
                .actualGrossAmount(request.getActualGrossAmount())
                .actualNetAmount(request.getActualNetAmount())
                .validatedAt(Instant.now())
                .validatedBy("payment-validation-service")
                .build();
                
        } catch (Exception e) {
            log.error("Error validating reconciliation request: {}", e.getMessage(), e);
            securityAuditLogger.logSecurityEvent("RECONCILIATION_VALIDATION_ERROR", "system",
                "Reconciliation validation failed: " + e.getMessage(),
                Map.of("settlementId", request.getSettlementId(), "error", e.getMessage()));
            return ReconciliationValidationResult.invalid("Validation failed: " + e.getMessage(), "VALIDATION_ERROR");
            
        } finally {
            sample.stop(Timer.builder("payment.validation.reconciliation.duration")
                .tag("settlement_id", request.getSettlementId())
                .register(meterRegistry));
        }
    }
    
    // =====================================
    // SECURITY VALIDATION (EXTRACTED FROM PaymentService)
    // =====================================
    
    @Override
    public boolean isValidIPAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return false;
        }
        
        try {
            // Use InetAddress to validate the IP
            java.net.InetAddress.getByName(ipAddress);
            
            // Additional check: reject localhost and private IPs in production for security
            if (ipAddress.equals("127.0.0.1") || ipAddress.equals("::1") || ipAddress.equals("localhost")) {
                // These might be valid in development but indicate proxy misconfiguration in production
                log.debug("Detected localhost IP: {} - might indicate proxy configuration issue", ipAddress);
                return false; // In production, reject localhost IPs as they indicate proxy issues
            }
            
            // Check for private IP ranges that shouldn't be client IPs
            if (isPrivateIPAddress(ipAddress)) {
                log.debug("Detected private IP: {} - might indicate proxy configuration issue", ipAddress);
                return false; // Private IPs shouldn't be client IPs in production
            }
            
            return true;
            
        } catch (java.net.UnknownHostException e) {
            log.debug("Invalid IP address format: {}", ipAddress);
            return false;
        }
    }
    
    @Override
    public boolean isPrivateIPAddress(String ip) {
        if (ip == null) {
            return false;
        }
        
        try {
            // Check common private IP ranges
            if (ip.startsWith("192.168.") || ip.startsWith("10.")) {
                return true;
            }
            
            // Check 172.16.0.0/12 range
            if (ip.startsWith("172.")) {
                String[] parts = ip.split("\\.");
                if (parts.length >= 2) {
                    int secondOctet = Integer.parseInt(parts[1]);
                    return secondOctet >= 16 && secondOctet <= 31;
                }
            }
        } catch (NumberFormatException e) {
            // Invalid format
        }
        return false;
    }
    
    // =====================================
    // HELPER METHODS
    // =====================================
    
    private boolean isValidCurrency(String currency) {
        // Simplified currency validation - in production would use a comprehensive list
        return currency != null && currency.length() == 3 && 
               currency.matches("[A-Z]{3}") &&
               (currency.equals("USD") || currency.equals("EUR") || 
                currency.equals("GBP") || currency.equals("CAD"));
    }
    
    /**
     * Calculate total refunded amount for a payment using the RefundTransactionRepository
     * This method implements the TODO that was previously returning BigDecimal.ZERO
     */
    private BigDecimal getTotalRefundedAmount(String paymentId) {
        try {
            BigDecimal totalRefunded = refundTransactionRepository.calculateTotalRefundedAmount(paymentId);
            log.debug("Total refunded amount for payment {}: {}", paymentId, totalRefunded);
            return totalRefunded;
        } catch (Exception e) {
            log.error("Error calculating total refunded amount for payment {}: {}", paymentId, e.getMessage());
            securityAuditLogger.logSecurityEvent("REFUND_CALCULATION_ERROR", "system",
                "Failed to calculate refunded amount for payment: " + paymentId,
                Map.of("paymentId", paymentId, "error", e.getMessage()));
            throw new RuntimeException("Failed to calculate refunded amount", e);
        }
    }
}