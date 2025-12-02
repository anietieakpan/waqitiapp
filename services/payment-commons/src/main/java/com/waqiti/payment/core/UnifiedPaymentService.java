package com.waqiti.payment.core;

import com.waqiti.payment.core.model.*;
import com.waqiti.payment.core.provider.PaymentProvider;
import com.waqiti.payment.core.strategy.PaymentStrategy;
import com.waqiti.payment.core.service.PaymentValidationService;
import com.waqiti.payment.core.service.PaymentEventPublisher;
import com.waqiti.payment.core.service.PaymentAuditService;
import com.waqiti.payment.core.service.PaymentFraudDetectionService;
import com.waqiti.payment.core.exception.PaymentValidationException;
import com.waqiti.payment.core.exception.UnsupportedPaymentTypeException;
import com.waqiti.payment.core.exception.UnsupportedProviderException;
// Using the model PaymentRequest as it's what the specialized classes convert to
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UNIFIED Payment Service - Enterprise Grade Consolidation
 * 
 * Consolidates all 23+ payment service implementations with:
 * - 8 Payment Strategies (Group, Recurring, Crypto, P2P, Split, BNPL, Merchant)
 * - 9 Payment Providers (Stripe, PayPal, Square, Adyen, Plaid, Dwolla, Bitcoin, Ethereum)
 * - Enterprise-grade PaymentRequest integration
 * - Circuit breaker patterns for resilience
 * - Comprehensive metrics and monitoring
 * - Idempotency and caching support
 * 
 * @version 2.0.0
 * @since 2025-01-15
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UnifiedPaymentService {

    private final Map<PaymentType, PaymentStrategy> paymentStrategies;
    private final Map<ProviderType, PaymentProvider> paymentProviders;
    private final PaymentValidationService validationService;
    private final PaymentEventPublisher eventPublisher;
    private final PaymentAuditService auditService;
    private final PaymentFraudDetectionService fraudDetectionService;
    private final com.waqiti.payment.cache.PaymentCacheService paymentCacheService;
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    /**
     * Universal payment processing method
     * Handles all payment types through strategy pattern
     */
    @Transactional
    public PaymentResult processPayment(PaymentRequest request) {
        log.info("Processing payment: type={}, amount={}, provider={}", 
                request.getType(), request.getAmount(), request.getProviderType());
        
        try {
            // 1. Validate payment request
            validatePaymentRequest(request);
            
            // 2. Fraud detection
            FraudAnalysisResult fraudCheck = fraudDetectionService.analyzePayment(request);
            if (fraudCheck.shouldBlock()) {
                return PaymentResult.fraudBlocked(fraudCheck.getRiskFactors());
            }
            
            // 3. Get appropriate strategy
            PaymentStrategy strategy = getPaymentStrategy(request.getType());
            
            // 4. Execute payment
            PaymentResult result = strategy.executePayment(request);
            
            // 5. Audit and events
            auditService.auditPayment(request, result);
            eventPublisher.publishPaymentEvent(request, result);
            
            return result;
            
        } catch (Exception e) {
            log.error("Payment processing failed: ", e);
            PaymentResult errorResult = PaymentResult.error(e.getMessage());
            auditService.auditPayment(request, errorResult);
            return errorResult;
        }
    }

    /**
     * Enterprise-grade payment processing with enhanced PaymentRequest
     * Provides full integration with all consolidation features
     */
    @Transactional
    @CircuitBreaker(name = "payment-processing")
    @Retry(name = "payment-processing")
    @Timed(value = "payment.process.enterprise", description = "Enterprise payment processing")
    public CompletableFuture<PaymentResult> processEnterprisePayment(PaymentRequest enterpriseRequest) {
        log.info("Processing enterprise payment: type={}, amount={}, security={}", 
                enterpriseRequest.getPaymentType(), 
                enterpriseRequest.getAmount(),
                enterpriseRequest.getSecurityLevel());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Enterprise validation
                enterpriseRequest.validate();
                
                // 2. Check idempotency
                if (isDuplicateRequest(enterpriseRequest.getIdempotencyKey())) {
                    return getCachedResult(enterpriseRequest.getIdempotencyKey());
                }
                
                // 3. Enhanced fraud detection with risk score
                if (enterpriseRequest.getFraudCheckRequired()) {
                    FraudAnalysisResult fraudCheck = performEnhancedFraudCheck(enterpriseRequest);
                    if (fraudCheck.shouldBlock()) {
                        return PaymentResult.fraudBlocked(fraudCheck.getRiskFactors());
                    }
                }
                
                // 4. Compliance checks
                if (enterpriseRequest.getRequiresAMLCheck() || 
                    enterpriseRequest.getSanctionsCheckRequired() || 
                    enterpriseRequest.getPepCheckRequired()) {
                    performComplianceChecks(enterpriseRequest);
                }
                
                // 5. Convert to internal PaymentRequest model
                PaymentRequest internalRequest = adaptEnterpriseRequest(enterpriseRequest);
                
                // 6. Route to appropriate strategy
                PaymentType paymentType = mapPaymentType(enterpriseRequest.getPaymentType());
                PaymentStrategy strategy = getPaymentStrategy(paymentType);
                
                // 7. Execute with selected provider
                ProviderType providerType = selectProvider(enterpriseRequest);
                PaymentResult result = executeWithProvider(internalRequest, strategy, providerType);
                
                // 8. Cache result for idempotency
                cacheResult(enterpriseRequest.getIdempotencyKey(), result);
                
                // 9. Audit and events
                auditEnterprisePayment(enterpriseRequest, result);
                publishEnterpriseEvent(enterpriseRequest, result);
                
                return result;
                
            } catch (Exception e) {
                log.error("Enterprise payment processing failed: ", e);
                return handleEnterpriseFailure(enterpriseRequest, e);
            }
        }, executorService);
    }
    
    /**
     * Process enterprise payment synchronously
     */
    @Transactional
    @CircuitBreaker(name = "payment-processing")
    @Retry(name = "payment-processing")
    public PaymentResult processEnterprisePaymentSync(PaymentRequest enterpriseRequest) {
        return processEnterprisePayment(enterpriseRequest).join();
    }
    
    /**
     * Batch processing for enterprise payments
     */
    @Transactional
    @Timed(value = "payment.batch.enterprise", description = "Batch enterprise payment processing")
    public CompletableFuture<BatchPaymentResult> processBatchEnterprisePayments(List<PaymentRequest> requests) {
        log.info("Processing batch of {} enterprise payments", requests.size());
        
        List<CompletableFuture<PaymentResult>> futures = requests.stream()
            .map(this::processEnterprisePayment)
            .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                List<PaymentResult> results = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
                
                return BatchPaymentResult.builder()
                    .totalRequests(requests.size())
                    .successCount(countSuccessful(results))
                    .failureCount(countFailed(results))
                    .results(results)
                    .build();
            });
    }

    /**
     * Process different payment types
     */
    public PaymentResult processSplitPayment(SplitPaymentRequest request) {
        return processPayment(request.toPaymentRequest(PaymentType.SPLIT));
    }

    public PaymentResult processRecurringPayment(RecurringPaymentRequest request) {
        return processPayment(request.toPaymentRequest(PaymentType.RECURRING));
    }

    public PaymentResult processGroupPayment(GroupPaymentRequest request) {
        return processPayment(request.toPaymentRequest(PaymentType.GROUP));
    }

    public PaymentResult processBnplPayment(BnplPaymentRequest request) {
        return processPayment(request.toPaymentRequest(PaymentType.BNPL));
    }

    public PaymentResult processMerchantPayment(MerchantPaymentRequest request) {
        return processPayment(request.toPaymentRequest(PaymentType.MERCHANT));
    }

    public PaymentResult processCryptoPayment(CryptoPaymentRequest request) {
        return processPayment(request.toPaymentRequest(PaymentType.CRYPTO));
    }

    public PaymentResult processP2PPayment(P2PPaymentRequest request) {
        return processPayment(request.toPaymentRequest(PaymentType.P2P));
    }

    /**
     * Payment refund processing
     */
    @Transactional
    public PaymentResult refundPayment(RefundRequest request) {
        log.info("Processing refund for payment: {}", request.getOriginalPaymentId());
        
        try {
            // Validate refund
            validateRefundRequest(request);
            
            // Get provider for refund
            PaymentProvider provider = getPaymentProvider(request.getProviderType());
            
            // Process refund
            PaymentResult result = provider.refundPayment(request);
            
            // Audit
            auditService.auditRefund(request, result);
            eventPublisher.publishRefundEvent(request, result);
            
            return result;
            
        } catch (Exception e) {
            log.error("Refund processing failed: ", e);
            return PaymentResult.error(e.getMessage());
        }
    }

    /**
     * Payment status inquiry
     */
    public PaymentStatus getPaymentStatus(String paymentId, ProviderType providerType) {
        PaymentProvider provider = getPaymentProvider(providerType);
        // In production, would call provider.getPaymentStatus(paymentId)
        // For now, return status from audit service
        PaymentResult result = auditService.getPaymentById(paymentId);
        return result != null ? result.getStatus() : PaymentStatus.NOT_FOUND;
    }

    /**
     * Get payment history
     */
    public List<PaymentResult> getPaymentHistory(String userId, PaymentHistoryFilter filter) {
        return auditService.getPaymentHistory(userId, filter);
    }

    /**
     * Validate payment limits and business rules
     */
    private void validatePaymentRequest(PaymentRequest request) {
        ValidationResult result = validationService.validatePaymentRequest(request);
        if (!result.isValid()) {
            throw new PaymentValidationException(result.getErrorMessage());
        }
    }

    private void validateRefundRequest(RefundRequest request) {
        ValidationResult result = validationService.validateRefundRequest(request);
        if (!result.isValid()) {
            throw new PaymentValidationException(result.getErrorMessage());
        }
        
        // Additional refund validations
        PaymentResult originalPayment = auditService.getPaymentById(request.getOriginalPaymentId());
        if (originalPayment == null) {
            throw new PaymentValidationException("Original payment not found");
        }
        
        if (request.getAmount().compareTo(originalPayment.getAmount()) > 0) {
            throw new PaymentValidationException("Refund amount exceeds original payment amount");
        }
    }

    private PaymentStrategy getPaymentStrategy(PaymentType type) {
        PaymentStrategy strategy = paymentStrategies.get(type);
        if (strategy == null) {
            throw new UnsupportedPaymentTypeException("No strategy found for payment type: " + type);
        }
        return strategy;
    }

    private PaymentProvider getPaymentProvider(ProviderType type) {
        PaymentProvider provider = paymentProviders.get(type);
        if (provider == null) {
            throw new UnsupportedProviderException("No provider found for type: " + type);
        }
        return provider;
    }

    /**
     * Health check for payment service
     */
    public PaymentServiceHealth getHealthStatus() {
        PaymentServiceHealth.Builder builder = PaymentServiceHealth.builder();
        
        // Check providers
        paymentProviders.forEach((type, provider) -> {
            try {
                boolean healthy = provider.isAvailable();
                builder.providerStatus(type, healthy);
            } catch (Exception e) {
                builder.providerStatus(type, false);
            }
        });
        
        // Check strategies
        builder.strategiesCount(paymentStrategies.size());
        builder.validationServiceActive(validationService != null);
        
        return builder.build();
    }

    /**
     * Get payment analytics
     */
    public PaymentAnalytics getAnalytics(String userId, AnalyticsFilter filter) {
        return auditService.getPaymentAnalytics(userId, filter);
    }
    
    // =====================================================
    // ENTERPRISE PAYMENT HELPER METHODS
    // =====================================================
    
    private boolean isDuplicateRequest(String idempotencyKey) {
        // Check if request with this idempotency key already exists
        return auditService.existsByIdempotencyKey(idempotencyKey);
    }
    
    private PaymentResult getCachedResult(String idempotencyKey) {
        return paymentCacheService.getCachedResult(idempotencyKey);
    }
    
    private void cacheResult(String idempotencyKey, PaymentResult result) {
        auditService.cacheResult(idempotencyKey, result);
    }
    
    private FraudAnalysisResult performEnhancedFraudCheck(PaymentRequest request) {
        // Enhanced fraud check using risk score from enterprise request
        Map<String, Object> riskFactors = Map.of(
            "riskScore", request.getRiskScore() != null ? request.getRiskScore() : BigDecimal.ZERO,
            "securityLevel", request.getSecurityLevel(),
            "paymentType", request.getPaymentType(),
            "amount", request.getAmount(),
            "isInternational", request.isInternational(),
            "isHighValue", request.isHighValue()
        );
        
        return fraudDetectionService.analyzeWithRiskFactors(request.toPaymentRequest(), riskFactors);
    }
    
    private void performComplianceChecks(PaymentRequest request) {
        // Perform AML, Sanctions, and PEP checks
        if (request.getRequiresAMLCheck()) {
            validationService.performAMLCheck(request);
        }
        
        if (request.getSanctionsCheckRequired()) {
            validationService.performSanctionsCheck(request);
        }
        
        if (request.getPepCheckRequired()) {
            validationService.performPEPCheck(request);
        }
    }
    
    private PaymentRequest adaptEnterpriseRequest(PaymentRequest enterpriseRequest) {
        // Adapt enterprise PaymentRequest to internal model
        return PaymentRequest.builder()
            .type(mapPaymentType(enterpriseRequest.getPaymentType()))
            .amount(enterpriseRequest.getAmount().getAmount())
            .currency(enterpriseRequest.getAmount().getCurrencyCode())
            .senderId(enterpriseRequest.getSenderId().toString())
            .recipientId(enterpriseRequest.getRecipientId().toString())
            .description(enterpriseRequest.getDescription())
            .metadata(enterpriseRequest.getCustomFields())
            .idempotencyKey(enterpriseRequest.getIdempotencyKey())
            .build();
    }
    
    private PaymentType mapPaymentType(String paymentType) {
        return switch (paymentType) {
            case "P2P" -> PaymentType.P2P;
            case "GROUP" -> PaymentType.GROUP;
            case "MERCHANT" -> PaymentType.MERCHANT;
            case "BILL_PAY" -> PaymentType.BILL;
            case "INTERNATIONAL" -> PaymentType.INTERNATIONAL;
            case "CRYPTO" -> PaymentType.CRYPTO;
            case "RECURRING" -> PaymentType.RECURRING;
            case "INSTANT" -> PaymentType.INSTANT;
            default -> PaymentType.STANDARD;
        };
    }
    
    private ProviderType selectProvider(PaymentRequest request) {
        // Select provider based on payment method and type
        String paymentMethod = request.getPaymentMethodCode();
        
        if ("CRYPTO".equals(request.getPaymentType())) {
            return ProviderType.BITCOIN; // or ETHEREUM based on currency
        }
        
        if (request.isInternational()) {
            return ProviderType.STRIPE; // Stripe for international
        }
        
        // Default provider selection logic
        return switch (paymentMethod) {
            case "CARD" -> ProviderType.STRIPE;
            case "BANK" -> ProviderType.PLAID;
            case "ACH" -> ProviderType.DWOLLA;
            case "PAYPAL" -> ProviderType.PAYPAL;
            default -> ProviderType.STRIPE;
        };
    }
    
    private PaymentResult executeWithProvider(PaymentRequest request, PaymentStrategy strategy, ProviderType providerType) {
        // Execute payment with selected provider
        PaymentProvider provider = getPaymentProvider(providerType);
        
        // Set provider in request context
        request.setProviderType(providerType);
        
        // Execute through strategy
        return strategy.executePayment(request);
    }
    
    private void auditEnterprisePayment(PaymentRequest enterpriseRequest, PaymentResult result) {
        // Enhanced audit with enterprise fields
        Map<String, Object> auditData = Map.of(
            "requestId", enterpriseRequest.getRequestId(),
            "correlationId", enterpriseRequest.getCorrelationId(),
            "traceId", enterpriseRequest.getTraceId(),
            "securityLevel", enterpriseRequest.getSecurityLevel(),
            "complianceLevel", enterpriseRequest.getComplianceLevel(),
            "riskScore", enterpriseRequest.getRiskScore()
        );
        
        auditService.auditPaymentWithMetadata(enterpriseRequest.toPaymentRequest(), result, auditData);
    }
    
    private void publishEnterpriseEvent(PaymentRequest enterpriseRequest, PaymentResult result) {
        // Publish enhanced event with enterprise context
        Map<String, Object> eventData = Map.of(
            "eventType", "ENTERPRISE_PAYMENT_PROCESSED",
            "requestId", enterpriseRequest.getRequestId(),
            "paymentType", enterpriseRequest.getPaymentType(),
            "result", result,
            "timestamp", Instant.now()
        );
        
        eventPublisher.publishPaymentEventWithMetadata(enterpriseRequest.toPaymentRequest(), result, eventData);
    }
    
    private PaymentResult handleEnterpriseFailure(PaymentRequest enterpriseRequest, Exception e) {
        PaymentResult errorResult = PaymentResult.error(e.getMessage());
        
        // Audit failure
        auditEnterprisePayment(enterpriseRequest, errorResult);
        
        // Publish failure event
        publishEnterpriseEvent(enterpriseRequest, errorResult);
        
        return errorResult;
    }
    
    private long countSuccessful(List<PaymentResult> results) {
        return results.stream()
            .filter(r -> r.getStatus() == PaymentStatus.SUCCESS)
            .count();
    }
    
    private long countFailed(List<PaymentResult> results) {
        return results.stream()
            .filter(r -> r.getStatus() == PaymentStatus.FAILED || r.getStatus() == PaymentStatus.ERROR)
            .count();
    }
    
    // =====================================================
    // INNER CLASSES FOR BATCH PROCESSING
    // =====================================================
    
    @lombok.Data
    @lombok.Builder
    public static class BatchPaymentResult {
        private int totalRequests;
        private long successCount;
        private long failureCount;
        private List<PaymentResult> results;
        private Map<String, Object> summary;
    }
}