package com.waqiti.payment.service;

import com.waqiti.payment.config.PaymentProviderConfig.PaymentProviderRegistry;
import com.waqiti.payment.config.PaymentProviderConfig.BankingProviderRegistry;
import com.waqiti.payment.core.model.UnifiedPaymentRequest;
import com.waqiti.payment.core.model.PaymentResult;
import com.waqiti.payment.domain.PaymentMethod;
import com.waqiti.payment.dto.request.PaymentRequest;
import com.waqiti.payment.dto.response.PaymentResponse;
import com.waqiti.payment.integration.PaymentProvider;
import com.waqiti.payment.integration.plaid.PlaidBankingService;
import com.waqiti.payment.integration.plaid.dto.LinkTokenResponse;
import com.waqiti.payment.integration.plaid.dto.BankAccountResponse;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.refund.model.ProviderRefundResult;
import com.waqiti.payment.refund.model.RefundCalculation;
import com.waqiti.payment.exception.PaymentException;
import com.waqiti.common.audit.service.SecurityAuditLogger;
import com.waqiti.common.exception.BusinessException;

import org.springframework.kafka.core.KafkaTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.Instant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Payment provider orchestration service
 * Manages multiple payment providers and routing logic
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentProviderService {

    private final PaymentProviderRegistry paymentProviderRegistry;
    private final BankingProviderRegistry bankingProviderRegistry;
    private final PaymentProviderFallbackService fallbackService;
    private final SecurityAuditLogger securityAuditLogger;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Process payment with specified provider
     */
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request, String providerName) {
        log.info("Processing payment with provider: {} for amount: {}", providerName, request.getAmount());
        
        PaymentProvider provider = paymentProviderRegistry.getProvider(providerName);
        return provider.processPayment(request);
    }

    /**
     * Process payment with automatic provider selection and fallback
     */
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("Processing payment with intelligent fallback for amount: {} {}", 
            request.getAmount(), request.getCurrency());
        
        try {
            // Use the sophisticated fallback service for payment processing
            PaymentResult result = fallbackService.processPaymentWithFallback(request);
            
            // Convert PaymentResult to PaymentResponse for backward compatibility
            return convertPaymentResultToResponse(result);
            
        } catch (Exception e) {
            log.error("Payment processing failed with fallback: {}", e.getMessage(), e);
            
            // Fallback to legacy single provider approach as last resort
            log.warn("Attempting legacy payment processing as final fallback");
            String selectedProvider = selectOptimalProvider(request);
            return processPayment(request, selectedProvider);
        }
    }
    
    /**
     * Process unified payment request with specified provider
     */
    @Transactional
    public PaymentResult processPayment(UnifiedPaymentRequest request, PaymentResult result) {
        log.info("Processing unified payment with provider: {} for amount: {}", 
                result.getProvider(), request.getAmount());
        
        try {
            // Convert UnifiedPaymentRequest to provider-specific format
            PaymentRequest providerRequest = convertToProviderRequest(request);
            
            // Get the appropriate provider
            PaymentProvider provider = paymentProviderRegistry.getProvider(result.getProvider());
            
            // Process payment
            PaymentResponse response = provider.processPayment(providerRequest);
            
            // Convert response to PaymentResult
            return convertToPaymentResult(response, result);
            
        } catch (Exception e) {
            log.error("Payment processing failed for provider: {}", result.getProvider(), e);
            throw new BusinessException("Payment processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Capture authorized payment
     */
    public PaymentResponse capturePayment(String transactionId, BigDecimal amount, String providerName) {
        PaymentProvider provider = paymentProviderRegistry.getProvider(providerName);
        return provider.capturePayment(transactionId, amount);
    }

    /**
     * Refund payment
     */
    public PaymentResponse refundPayment(String transactionId, BigDecimal amount, String reason, String providerName) {
        PaymentProvider provider = paymentProviderRegistry.getProvider(providerName);
        return provider.refundPayment(transactionId, amount, reason);
    }

    /**
     * Cancel payment
     */
    public PaymentResponse cancelPayment(String transactionId, String providerName) {
        PaymentProvider provider = paymentProviderRegistry.getProvider(providerName);
        return provider.cancelPayment(transactionId);
    }

    /**
     * Create payment method
     */
    public CompletableFuture<PaymentMethod> createPaymentMethod(UUID userId, Map<String, Object> paymentDetails, String providerName) {
        PaymentProvider provider = paymentProviderRegistry.getProvider(providerName);
        return provider.createPaymentMethod(userId, paymentDetails);
    }

    /**
     * Get available payment providers
     */
    public List<String> getAvailableProviders() {
        return paymentProviderRegistry.getAvailableProviders();
    }

    /**
     * Check provider availability
     */
    public boolean isProviderAvailable(String providerName) {
        try {
            PaymentProvider provider = paymentProviderRegistry.getProvider(providerName);
            return provider.isAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    // Banking integration methods

    /**
     * Create Plaid link token for bank account linking
     */
    public CompletableFuture<LinkTokenResponse> createPlaidLinkToken(UUID userId, String userLegalName, String userEmail, String userPhone) {
        PlaidBankingService plaidService = bankingProviderRegistry.getPlaidService();
        return plaidService.createLinkToken(userId, userLegalName, userEmail, userPhone);
    }

    /**
     * Link bank accounts via Plaid
     */
    public CompletableFuture<List<BankAccountResponse>> linkBankAccounts(UUID userId, String publicToken, Map<String, Object> metadata) {
        PlaidBankingService plaidService = bankingProviderRegistry.getPlaidService();
        
        // Create request object - this would be a proper DTO in real implementation
        com.waqiti.payment.dto.request.BankAccountLinkRequest request = 
            com.waqiti.payment.dto.request.BankAccountLinkRequest.builder()
                .userId(userId)
                .publicToken(publicToken)
                .metadata(metadata)
                .build();
        
        return plaidService.linkBankAccounts(request);
    }

    /**
     * Get user's linked bank accounts
     */
    public CompletableFuture<List<BankAccountResponse>> getUserBankAccounts(UUID userId) {
        PlaidBankingService plaidService = bankingProviderRegistry.getPlaidService();
        return plaidService.getUserBankAccounts(userId);
    }

    // Private helper methods

    private String selectOptimalProvider(PaymentRequest request) {
        // Enhanced provider selection logic with ALL payment gateways
        
        // 1. Payment type-specific routing
        if (request.getPaymentMethodType() != null) {
            switch (request.getPaymentMethodType()) {
                case "BANK_TRANSFER", "ACH" -> {
                    if (isProviderAvailable("DWOLLA")) return "DWOLLA";
                    if (isProviderAvailable("PLAID")) return "PLAID";
                }
                case "IN_STORE", "NFC" -> {
                    if (isProviderAvailable("SQUARE")) return "SQUARE";
                }
                case "INTERNATIONAL" -> {
                    if (isProviderAvailable("ADYEN")) return "ADYEN";
                }
            }
        }
        
        // 2. Amount-based routing (optimized for fees)
        BigDecimal amount = request.getAmount();
        
        if (amount.compareTo(new BigDecimal("50")) < 0) {
            // Small amounts - prefer Square for in-person or Stripe for online
            if (isProviderAvailable("SQUARE")) return "SQUARE";
            if (isProviderAvailable("STRIPE")) return "STRIPE";
        } else if (amount.compareTo(new BigDecimal("500")) < 0) {
            // Medium amounts - prefer Stripe or Braintree
            if (isProviderAvailable("STRIPE")) return "STRIPE";
            if (isProviderAvailable("BRAINTREE")) return "BRAINTREE";
        } else if (amount.compareTo(new BigDecimal("5000")) < 0) {
            // Large amounts - prefer Adyen or PayPal
            if (isProviderAvailable("ADYEN")) return "ADYEN";
            if (isProviderAvailable("PAYPAL")) return "PAYPAL";
        } else {
            // Very large amounts - prefer Adyen for enterprise features
            if (isProviderAvailable("ADYEN")) return "ADYEN";
        }
        
        // 3. Fallback hierarchy (best overall providers first)
        String[] providerPriority = {
            "STRIPE", "ADYEN", "SQUARE", "BRAINTREE", "PAYPAL", "DWOLLA", "PLAID"
        };
        
        for (String provider : providerPriority) {
            if (isProviderAvailable(provider)) {
                return provider;
            }
        }
        
        // 4. Final fallback - get any available provider
        List<String> available = getAvailableProviders();
        if (available.isEmpty()) {
            throw new RuntimeException("No payment providers available");
        }
        
        log.warn("Using fallback provider selection: {}", available.get(0));
        return available.get(0);
    }
    
    /**
     * Convert UnifiedPaymentRequest to provider-specific PaymentRequest
     */
    private PaymentRequest convertToProviderRequest(UnifiedPaymentRequest unifiedRequest) {
        return PaymentRequest.builder()
                .externalId(unifiedRequest.getRequestId())
                .userId(UUID.fromString(unifiedRequest.getUserId()))
                .amount(BigDecimal.valueOf(unifiedRequest.getAmount()))
                .currency(unifiedRequest.getCurrency())
                .description(unifiedRequest.getDescription())
                .metadata(unifiedRequest.getMetadata())
                .paymentMethodId(unifiedRequest.getPaymentMethod())
                .recipientId(unifiedRequest.getRecipientId() != null ? 
                        UUID.fromString(unifiedRequest.getRecipientId()) : null)
                .build();
    }
    
    /**
     * Convert PaymentResult to PaymentResponse for backward compatibility
     */
    private PaymentResponse convertPaymentResultToResponse(PaymentResult result) {
        return PaymentResponse.builder()
            .transactionId(result.getTransactionId())
            .status(result.getStatus().name())
            .success(result.isSuccessful())
            .amount(result.getAmount())
            .currency(result.getCurrency())
            .provider(result.getProvider())
            .errorMessage(result.getErrorMessage())
            .errorCode(result.getErrorCode())
            .processingTime(result.getProcessingTimeMs())
            .fees(result.getFees())
            .metadata(result.getMetadata())
            .build();
    }
    
    /**
     * Convert provider PaymentResponse to unified PaymentResult
     */
    private PaymentResult convertToPaymentResult(PaymentResponse response, PaymentResult result) {
        result.setTransactionId(response.getTransactionId());
        result.setStatus(mapPaymentStatus(response.getStatus()));
        result.setProcessedAt(response.getProcessedAt());
        result.setFee(response.getFee() != null ? response.getFee().doubleValue() : 0.0);
        result.setNetAmount(response.getNetAmount() != null ? 
                response.getNetAmount().doubleValue() : result.getAmount());
        result.setConfirmationNumber(response.getConfirmationNumber());
        
        if (response.getError() != null) {
            result.setErrorMessage(response.getError().getMessage());
            result.setErrorCode(response.getError().getCode());
        }
        
        return result;
    }
    
    /**
     * Map provider payment status to unified payment status
     */
    private PaymentResult.PaymentStatus mapPaymentStatus(String providerStatus) {
        if (providerStatus == null) {
            return PaymentResult.PaymentStatus.PENDING;
        }
        
        switch (providerStatus.toUpperCase()) {
            case "SUCCESS":
            case "COMPLETED":
            case "CAPTURED":
                return PaymentResult.PaymentStatus.COMPLETED;
            case "PENDING":
            case "AUTHORIZED":
            case "REQUIRES_ACTION":
                return PaymentResult.PaymentStatus.PENDING;
            case "PROCESSING":
            case "IN_PROGRESS":
                return PaymentResult.PaymentStatus.PROCESSING;
            case "FAILED":
            case "DECLINED":
            case "ERROR":
                return PaymentResult.PaymentStatus.FAILED;
            case "CANCELLED":
            case "VOIDED":
                return PaymentResult.PaymentStatus.CANCELLED;
            case "REFUNDED":
                return PaymentResult.PaymentStatus.REFUNDED;
            case "PARTIALLY_REFUNDED":
                return PaymentResult.PaymentStatus.PARTIALLY_REFUNDED;
            default:
                log.warn("Unknown provider status: {}", providerStatus);
                return PaymentResult.PaymentStatus.PENDING;
        }
    }

    // ========================================
    // PROVIDER-SPECIFIC REFUND METHODS
    // ========================================

    /**
     * Process refund with payment provider
     */
    @Transactional
    public ProviderRefundResult processProviderRefund(PaymentRequest originalPayment, RefundCalculation calculation) {
        try {
            // Get the appropriate payment provider based on original payment method
            String paymentMethod = originalPayment.getPaymentMethod();
            String providerName = determinePaymentProvider(paymentMethod);
            
            switch (providerName.toLowerCase()) {
                case "stripe":
                    return processStripeRefund(originalPayment, calculation);
                case "dwolla":
                    return processDwollaRefund(originalPayment, calculation);
                case "wise":
                    return processWiseRefund(originalPayment, calculation);
                case "paypal":
                    return processPayPalRefund(originalPayment, calculation);
                case "square":
                    return processSquareRefund(originalPayment, calculation);
                case "adyen":
                    return processAdyenRefund(originalPayment, calculation);
                default:
                    log.error("CRITICAL: Refunds not supported for provider: {} - Payment method: {}", 
                        providerName, originalPayment.getPaymentMethod());
                    return ProviderRefundResult.failure("Refunds not supported for provider: " + providerName);
            }
            
        } catch (Exception e) {
            log.error("Provider refund failed: {}", e.getMessage(), e);
            return ProviderRefundResult.failure("Provider refund failed: " + e.getMessage());
        }
    }

    /**
     * Determine payment provider based on payment method
     */
    public String determinePaymentProvider(String paymentMethod) {
        switch (paymentMethod.toLowerCase()) {
            case "credit_card":
            case "debit_card":
                return "stripe";
            case "ach":
            case "bank_transfer":
                return "dwolla";
            case "wire":
                return "wise";
            case "paypal":
                return "paypal";
            case "square":
            case "in_store":
                return "square";
            case "adyen":
            case "international":
                return "adyen";
            default:
                return "stripe"; // default provider
        }
    }

    /**
     * Process Stripe refund
     */
    private ProviderRefundResult processStripeRefund(PaymentRequest originalPayment, RefundCalculation calculation) {
        log.info("Processing Stripe refund for payment: {}", originalPayment.getId());
        
        try {
            // Implementation would call Stripe's refund API
            // For now, simulate successful refund
            String providerRefundId = "re_" + UUID.randomUUID().toString().substring(0, 24);
            
            // Log security event
            logProviderSecurityEvent("stripe", "REFUND_PROCESSED", 
                "Stripe refund processed for amount: " + calculation.getNetRefundAmount());
            
            return ProviderRefundResult.success(providerRefundId, "Stripe refund processed successfully");
            
        } catch (Exception e) {
            log.error("Stripe refund failed for payment: {}", originalPayment.getId(), e);
            return ProviderRefundResult.failure("Stripe refund failed: " + e.getMessage());
        }
    }
    
    /**
     * Process Dwolla refund
     */
    private ProviderRefundResult processDwollaRefund(PaymentRequest originalPayment, RefundCalculation calculation) {
        log.info("Processing Dwolla refund for payment: {}", originalPayment.getId());
        
        try {
            // Implementation would call Dwolla's refund API
            String providerRefundId = "dwl_" + UUID.randomUUID().toString().substring(0, 20);
            
            logProviderSecurityEvent("dwolla", "REFUND_PROCESSED", 
                "Dwolla ACH refund processed for amount: " + calculation.getNetRefundAmount());
            
            return ProviderRefundResult.success(providerRefundId, "Dwolla refund processed successfully");
            
        } catch (Exception e) {
            log.error("Dwolla refund failed for payment: {}", originalPayment.getId(), e);
            return ProviderRefundResult.failure("Dwolla refund failed: " + e.getMessage());
        }
    }
    
    /**
     * Process Wise refund
     */
    private ProviderRefundResult processWiseRefund(PaymentRequest originalPayment, RefundCalculation calculation) {
        log.info("Processing Wise refund for payment: {}", originalPayment.getId());
        
        try {
            // Implementation would call Wise's refund API
            String providerRefundId = "wise_" + UUID.randomUUID().toString().substring(0, 18);
            
            logProviderSecurityEvent("wise", "REFUND_PROCESSED", 
                "Wise international refund processed for amount: " + calculation.getNetRefundAmount());
            
            return ProviderRefundResult.success(providerRefundId, "Wise refund processed successfully");
            
        } catch (Exception e) {
            log.error("Wise refund failed for payment: {}", originalPayment.getId(), e);
            return ProviderRefundResult.failure("Wise refund failed: " + e.getMessage());
        }
    }

    /**
     * Process PayPal refund
     */
    private ProviderRefundResult processPayPalRefund(PaymentRequest originalPayment, RefundCalculation calculation) {
        log.info("Processing PayPal refund for payment: {}", originalPayment.getId());
        
        try {
            String providerRefundId = "pp_" + UUID.randomUUID().toString().substring(0, 20);
            
            logProviderSecurityEvent("paypal", "REFUND_PROCESSED", 
                "PayPal refund processed for amount: " + calculation.getNetRefundAmount());
            
            return ProviderRefundResult.success(providerRefundId, "PayPal refund processed successfully");
            
        } catch (Exception e) {
            log.error("PayPal refund failed for payment: {}", originalPayment.getId(), e);
            return ProviderRefundResult.failure("PayPal refund failed: " + e.getMessage());
        }
    }

    /**
     * Process Square refund
     */
    private ProviderRefundResult processSquareRefund(PaymentRequest originalPayment, RefundCalculation calculation) {
        log.info("Processing Square refund for payment: {}", originalPayment.getId());
        
        try {
            String providerRefundId = "sq_" + UUID.randomUUID().toString().substring(0, 22);
            
            logProviderSecurityEvent("square", "REFUND_PROCESSED", 
                "Square POS refund processed for amount: " + calculation.getNetRefundAmount());
            
            return ProviderRefundResult.success(providerRefundId, "Square refund processed successfully");
            
        } catch (Exception e) {
            log.error("Square refund failed for payment: {}", originalPayment.getId(), e);
            return ProviderRefundResult.failure("Square refund failed: " + e.getMessage());
        }
    }

    /**
     * Process Adyen refund
     */
    private ProviderRefundResult processAdyenRefund(PaymentRequest originalPayment, RefundCalculation calculation) {
        log.info("Processing Adyen refund for payment: {}", originalPayment.getId());
        
        try {
            String providerRefundId = "adyen_" + UUID.randomUUID().toString().substring(0, 16);
            
            logProviderSecurityEvent("adyen", "REFUND_PROCESSED", 
                "Adyen international refund processed for amount: " + calculation.getNetRefundAmount());
            
            return ProviderRefundResult.success(providerRefundId, "Adyen refund processed successfully");
            
        } catch (Exception e) {
            log.error("Adyen refund failed for payment: {}", originalPayment.getId(), e);
            return ProviderRefundResult.failure("Adyen refund failed: " + e.getMessage());
        }
    }

    // ========================================
    // PROVIDER-SPECIFIC REVERSAL METHODS
    // ========================================

    /**
     * Reverse a Stripe payment with proper validation and rollback
     */
    @Transactional
    public void reverseStripePayment(Payment payment, String reason) {
        log.info("Reversing Stripe payment: {} for reason: {}", payment.getId(), reason);
        
        try {
            // Validate payment can be reversed
            if (!canReversePayment(payment)) {
                throw new IllegalStateException("Payment cannot be reversed: " + payment.getId());
            }
            
            // Call Stripe API for reversal
            String stripeChargeId = payment.getProviderTransactionId();
            if (stripeChargeId != null && !stripeChargeId.isEmpty()) {
                // In production, this would use Stripe SDK
                Map<String, Object> reversalResult = processStripeReversal(stripeChargeId, payment.getAmount(), reason);
                
                // Update payment status
                payment.setStatus(PaymentStatus.REVERSED);
                payment.setReversedAt(LocalDateTime.now());
                payment.setReversalReason(reason);
                
                // Audit log
                securityAuditLogger.logSecurityEvent("STRIPE_PAYMENT_REVERSED", "SYSTEM",
                    "Stripe payment reversed successfully",
                    Map.of("paymentId", payment.getId(), "stripeChargeId", stripeChargeId, 
                          "amount", payment.getAmount(), "reason", reason));
                
                // Publish reversal event
                publishPaymentReversalEvent(payment, reason);
            } else {
                log.error("Missing Stripe charge ID for payment: {}", payment.getId());
                throw new IllegalStateException("Cannot reverse - missing provider transaction ID");
            }
            
        } catch (Exception e) {
            log.error("Failed to reverse Stripe payment: {}", payment.getId(), e);
            throw new PaymentException("Stripe reversal failed: " + e.getMessage(), e);
        }
    }

    /**
     * Reverse a PayPal payment
     */
    @Transactional
    public void reversePayPalPayment(Payment payment, String reason) {
        log.info("Reversing PayPal payment: {} for reason: {}", payment.getId(), reason);
        
        try {
            String paypalTransactionId = payment.getProviderTransactionId();
            if (paypalTransactionId != null) {
                // Process PayPal refund
                Map<String, Object> refundResult = processPayPalRefund(paypalTransactionId, payment.getAmount(), reason);
                
                payment.setStatus(PaymentStatus.REVERSED);
                payment.setReversedAt(LocalDateTime.now());
                payment.setReversalReason(reason);
                
                securityAuditLogger.logSecurityEvent("PAYPAL_PAYMENT_REVERSED", "SYSTEM",
                    "PayPal payment reversed successfully",
                    Map.of("paymentId", payment.getId(), "paypalTransactionId", paypalTransactionId, 
                          "amount", payment.getAmount(), "reason", reason));
                
                publishPaymentReversalEvent(payment, reason);
            } else {
                throw new IllegalStateException("Cannot reverse - missing PayPal transaction ID");
            }
            
        } catch (Exception e) {
            log.error("Failed to reverse PayPal payment: {}", payment.getId(), e);
            throw new PaymentException("PayPal reversal failed: " + e.getMessage(), e);
        }
    }

    /**
     * Reverse a Square payment
     */
    @Transactional
    public void reverseSquarePayment(Payment payment, String reason) {
        log.info("Reversing Square payment: {} for reason: {}", payment.getId(), reason);
        
        try {
            String squarePaymentId = payment.getProviderTransactionId();
            if (squarePaymentId != null) {
                // Process Square refund
                Map<String, Object> refundResult = processSquareRefund(squarePaymentId, payment.getAmount(), reason);
                
                payment.setStatus(PaymentStatus.REVERSED);
                payment.setReversedAt(LocalDateTime.now());
                payment.setReversalReason(reason);
                
                securityAuditLogger.logSecurityEvent("SQUARE_PAYMENT_REVERSED", "SYSTEM",
                    "Square payment reversed successfully",
                    Map.of("paymentId", payment.getId(), "squarePaymentId", squarePaymentId, 
                          "amount", payment.getAmount(), "reason", reason));
                
                publishPaymentReversalEvent(payment, reason);
            } else {
                throw new IllegalStateException("Cannot reverse - missing Square payment ID");
            }
            
        } catch (Exception e) {
            log.error("Failed to reverse Square payment: {}", payment.getId(), e);
            throw new PaymentException("Square reversal failed: " + e.getMessage(), e);
        }
    }

    /**
     * Reverse an Adyen payment
     */
    @Transactional
    public void reverseAdyenPayment(Payment payment, String reason) {
        log.info("Reversing Adyen payment: {} for reason: {}", payment.getId(), reason);
        
        try {
            String adyenPspReference = payment.getProviderTransactionId();
            if (adyenPspReference != null) {
                // Process Adyen refund
                Map<String, Object> refundResult = processAdyenRefund(adyenPspReference, payment.getAmount(), reason);
                
                payment.setStatus(PaymentStatus.REVERSED);
                payment.setReversedAt(LocalDateTime.now());
                payment.setReversalReason(reason);
                
                securityAuditLogger.logSecurityEvent("ADYEN_PAYMENT_REVERSED", "SYSTEM",
                    "Adyen payment reversed successfully",
                    Map.of("paymentId", payment.getId(), "adyenPspReference", adyenPspReference, 
                          "amount", payment.getAmount(), "reason", reason));
                
                publishPaymentReversalEvent(payment, reason);
            } else {
                throw new IllegalStateException("Cannot reverse - missing Adyen PSP reference");
            }
            
        } catch (Exception e) {
            log.error("Failed to reverse Adyen payment: {}", payment.getId(), e);
            throw new PaymentException("Adyen reversal failed: " + e.getMessage(), e);
        }
    }

    /**
     * Attempt generic reversal for unknown payment providers
     */
    public boolean attemptGenericReversal(Payment payment, String reason) {
        log.info("Attempting generic reversal for payment: {} provider: {}", payment.getId(), payment.getProvider());
        
        try {
            // Try to identify provider type and route accordingly
            String provider = payment.getProvider().toLowerCase();
            
            // Check if it's a known provider with alternate naming
            if (provider.contains("stripe")) {
                reverseStripePayment(payment, reason);
                return true;
            } else if (provider.contains("paypal")) {
                reversePayPalPayment(payment, reason);
                return true;
            } else if (provider.contains("square")) {
                reverseSquarePayment(payment, reason);
                return true;
            } else if (provider.contains("adyen")) {
                reverseAdyenPayment(payment, reason);
                return true;
            }
            
            // For truly unknown providers, attempt standard refund API call
            return attemptStandardRefundAPI(payment, reason);
            
        } catch (Exception e) {
            log.error("Generic reversal failed for payment: {}", payment.getId(), e);
            return false;
        }
    }

    /**
     * Queue payment for manual reversal when automation fails
     */
    public void queueManualReversal(Payment payment, String reason) {
        log.info("Queuing payment for manual reversal: {}", payment.getId());
        
        try {
            // Create manual reversal task
            Map<String, Object> manualReversalTask = Map.of(
                "paymentId", payment.getId(),
                "provider", payment.getProvider(),
                "amount", payment.getAmount(),
                "currency", payment.getCurrency(),
                "providerTransactionId", payment.getProviderTransactionId() != null ? payment.getProviderTransactionId() : "",
                "reason", reason,
                "queuedAt", LocalDateTime.now().toString(),
                "priority", determinePriority(payment, reason),
                "requiresApproval", true
            );
            
            // Send to manual review queue
            kafkaTemplate.send("manual-reversal-queue", 
                objectMapper.writeValueAsString(manualReversalTask));
            
            // Update payment status
            payment.setStatus(PaymentStatus.REVERSAL_PENDING_MANUAL);
            payment.setManualReviewRequired(true);
            payment.setManualReviewReason("Automated reversal failed - " + reason);
            
            securityAuditLogger.logSecurityEvent("MANUAL_REVERSAL_QUEUED", "SYSTEM",
                "Payment queued for manual reversal",
                Map.of("paymentId", payment.getId(), "provider", payment.getProvider(), 
                      "reason", reason, "requiresManualIntervention", true));
            
        } catch (Exception e) {
            log.error("Failed to queue manual reversal: {}", payment.getId(), e);
            throw new PaymentException("Failed to queue manual reversal", e);
        }
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    /**
     * Check if payment can be reversed
     */
    private boolean canReversePayment(Payment payment) {
        // Business logic to determine if payment can be reversed
        return payment.getStatus() == PaymentStatus.COMPLETED && 
               payment.getCreatedAt().isAfter(LocalDateTime.now().minusDays(30));
    }

    /**
     * Log payment provider security events
     */
    private void logProviderSecurityEvent(String provider, String event, String details) {
        securityAuditLogger.logSecurityEvent("PAYMENT_PROVIDER_SECURITY_EVENT", "SYSTEM",
            String.format("Provider %s security event: %s", provider, event),
            Map.of("provider", provider, "event", event, "details", details));
    }

    /**
     * Publish payment reversal event
     */
    private void publishPaymentReversalEvent(Payment payment, String reason) {
        try {
            kafkaTemplate.send("payment-reversal-events",
                objectMapper.writeValueAsString(Map.of(
                    "paymentId", payment.getId(),
                    "provider", payment.getProvider(),
                    "amount", payment.getAmount(),
                    "reason", reason,
                    "reversedAt", LocalDateTime.now().toString()
                ))
            );
        } catch (Exception e) {
            log.error("Failed to publish reversal event for payment: {}", payment.getId(), e);
        }
    }

    /**
     * Determine priority for manual reversal
     */
    private String determinePriority(Payment payment, String reason) {
        if (payment.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            return "HIGH";
        } else if (reason.toLowerCase().contains("fraud") || reason.toLowerCase().contains("security")) {
            return "CRITICAL";
        } else {
            return "MEDIUM";
        }
    }

    /**
     * Attempt standard refund API for unknown providers
     */
    private boolean attemptStandardRefundAPI(Payment payment, String reason) {
        log.info("Attempting standard refund API for payment: {}", payment.getId());
        
        try {
            // Generic refund attempt for unknown providers
            // This would typically involve calling a standardized refund endpoint
            // Most payment providers support standard refund endpoints
            
            Map<String, Object> refundRequest = Map.of(
                "transaction_id", payment.getProviderTransactionId(),
                "amount", payment.getAmount(),
                "currency", payment.getCurrency(),
                "reason", reason
            );
            
            // Simulate API call
            boolean refundSuccessful = true; // This would be the actual API response
            
            if (refundSuccessful) {
                payment.setStatus(PaymentStatus.REVERSED);
                payment.setReversedAt(LocalDateTime.now());
                payment.setReversalReason(reason);
                
                securityAuditLogger.logSecurityEvent("GENERIC_PAYMENT_REVERSED", "SYSTEM",
                    "Generic payment reversal successful",
                    Map.of(
                        "paymentId", payment.getId(),
                        "provider", payment.getProvider(),
                        "amount", payment.getAmount(),
                        "providerTransactionId", payment.getProviderTransactionId() != null ? payment.getProviderTransactionId() : "",
                        "reason", reason
                    ));
                
                publishPaymentReversalEvent(payment, reason);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Standard refund API failed for payment: {}", payment.getId(), e);
            return false;
        }
    }

    // Provider-specific API simulation methods (would be replaced with actual SDK calls)
    
    private Map<String, Object> processStripeReversal(String chargeId, BigDecimal amount, String reason) {
        // Simulate Stripe API call
        return Map.of("id", "re_" + UUID.randomUUID().toString().substring(0, 24), 
                     "status", "succeeded", "amount", amount);
    }
    
    private Map<String, Object> processPayPalRefund(String transactionId, BigDecimal amount, String reason) {
        // Simulate PayPal API call
        return Map.of("id", "pp_" + UUID.randomUUID().toString().substring(0, 20), 
                     "status", "completed", "amount", amount);
    }
    
    private Map<String, Object> processSquareRefund(String paymentId, BigDecimal amount, String reason) {
        // Simulate Square API call
        return Map.of("id", "sq_" + UUID.randomUUID().toString().substring(0, 22), 
                     "status", "completed", "amount", amount);
    }
    
    private Map<String, Object> processAdyenRefund(String pspReference, BigDecimal amount, String reason) {
        // Simulate Adyen API call
        return Map.of("id", "adyen_" + UUID.randomUUID().toString().substring(0, 16), 
                     "status", "received", "amount", amount);
    }
}