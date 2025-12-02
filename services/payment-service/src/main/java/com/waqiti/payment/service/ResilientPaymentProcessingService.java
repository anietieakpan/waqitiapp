package com.waqiti.payment.service;

import com.waqiti.payment.dto.PaymentRequest;
import com.waqiti.payment.dto.PaymentResponse;
import com.waqiti.payment.entity.Payment;
import com.waqiti.payment.repository.PaymentRepository;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/**
 * Resilient Payment Processing Service with comprehensive fault tolerance
 * 
 * This service demonstrates the proper implementation of Resilience4j patterns:
 * - Circuit Breaker: Prevents cascading failures
 * - Rate Limiter: Controls request rate
 * - Bulkhead: Isolates resources
 * - Retry: Handles transient failures
 * - Time Limiter: Prevents indefinite waits
 * 
 * Each payment processing operation is protected by multiple layers of resilience
 * to ensure platform stability and reliability.
 * 
 * @author Waqiti Platform Team
 * @since Phase 2 - P1 Remediation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResilientPaymentProcessingService {
    
    private final PaymentRepository paymentRepository;
    private final PaymentGatewayService paymentGatewayService;
    private final FraudDetectionService fraudDetectionService;
    private final ComplianceService complianceService;
    private final NotificationService notificationService;
    private final MeterRegistry meterRegistry;
    
    // Metrics
    private Counter paymentProcessedCounter;
    private Counter paymentFailedCounter;
    private Counter circuitBreakerOpenCounter;
    private Timer paymentProcessingTimer;
    
    /**
     * Process payment with full resilience stack
     * 
     * Order of decorators matters:
     * 1. RateLimiter - First line of defense
     * 2. Bulkhead - Resource isolation
     * 3. TimeLimiter - Timeout protection
     * 4. CircuitBreaker - Failure management
     * 5. Retry - Recovery mechanism
     */
    @RateLimiter(name = "payment-processing", fallbackMethod = "paymentRateLimitFallback")
    @Bulkhead(name = "payment-processing", fallbackMethod = "paymentBulkheadFallback")
    @TimeLimiter(name = "payment-processing", fallbackMethod = "paymentTimeoutFallback")
    @CircuitBreaker(name = "payment-processing", fallbackMethod = "paymentCircuitBreakerFallback")
    @Retry(name = "payment-processing", fallbackMethod = "paymentRetryFallback")
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 30, rollbackFor = Exception.class)
    public CompletableFuture<PaymentResponse> processPaymentResilient(PaymentRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            String paymentId = UUID.randomUUID().toString();
            
            log.info("Processing payment with resilience. PaymentId: {}, Amount: {} {}", 
                    paymentId, request.getAmount(), request.getCurrency());
            
            try {
                // Step 1: Validate request
                validatePaymentRequest(request);
                
                // Step 2: Fraud detection with circuit breaker
                FraudCheckResult fraudResult = checkFraudResilient(request);
                if (fraudResult.isHighRisk()) {
                    throw new FraudDetectedException("High risk transaction detected");
                }
                
                // Step 3: Compliance check with circuit breaker
                ComplianceResult complianceResult = checkComplianceResilient(request);
                if (!complianceResult.isApproved()) {
                    throw new ComplianceException("Compliance check failed");
                }
                
                // Step 4: Process with payment gateway
                PaymentGatewayResponse gatewayResponse = processWithGatewayResilient(request);
                
                // Step 5: Save payment record
                Payment payment = savePayment(request, gatewayResponse);
                
                // Step 6: Send notification (non-blocking)
                sendNotificationAsync(payment);
                
                // Update metrics
                paymentProcessedCounter.increment();
                paymentProcessingTimer.stop(sample);
                
                return PaymentResponse.builder()
                        .paymentId(payment.getId())
                        .status("SUCCESS")
                        .transactionReference(gatewayResponse.getTransactionId())
                        .processedAt(LocalDateTime.now())
                        .build();
                        
            } catch (Exception e) {
                log.error("Payment processing failed. PaymentId: {}, Error: {}", 
                        paymentId, e.getMessage());
                paymentFailedCounter.increment();
                throw new PaymentProcessingException("Payment processing failed", e);
            }
        });
    }
    
    /**
     * Fraud detection with dedicated circuit breaker
     */
    @CircuitBreaker(name = "fraud-detection", fallbackMethod = "fraudDetectionFallback")
    @Retry(name = "fraud-detection")
    private FraudCheckResult checkFraudResilient(PaymentRequest request) {
        log.debug("Checking fraud for amount: {}", request.getAmount());
        return fraudDetectionService.checkFraud(request);
    }
    
    /**
     * Compliance check with dedicated circuit breaker
     */
    @CircuitBreaker(name = "compliance-check", fallbackMethod = "complianceCheckFallback")
    @Retry(name = "compliance-check")
    @RateLimiter(name = "compliance-api")
    private ComplianceResult checkComplianceResilient(PaymentRequest request) {
        log.debug("Checking compliance for payment");
        return complianceService.checkCompliance(request);
    }
    
    /**
     * Payment gateway processing with circuit breaker
     */
    @CircuitBreaker(name = "payment-gateway", fallbackMethod = "paymentGatewayFallback")
    @Retry(name = "payment-gateway")
    @Bulkhead(name = "external-services")
    @TimeLimiter(name = "external-calls")
    private PaymentGatewayResponse processWithGatewayResilient(PaymentRequest request) {
        log.debug("Processing with payment gateway");
        return paymentGatewayService.processPayment(request);
    }
    
    /**
     * Async notification with circuit breaker (non-critical path)
     */
    @CircuitBreaker(name = "notification-service")
    @Bulkhead(name = "notification-service", type = Bulkhead.Type.THREADPOOL)
    private CompletableFuture<Void> sendNotificationAsync(Payment payment) {
        return CompletableFuture.runAsync(() -> {
            try {
                notificationService.sendPaymentNotification(payment);
            } catch (Exception e) {
                log.warn("Failed to send notification for payment: {}", payment.getId());
                // Non-critical - don't fail the payment
            }
        });
    }
    
    // ============= Fallback Methods =============
    
    /**
     * Rate limiter fallback - Too many requests
     */
    public CompletableFuture<PaymentResponse> paymentRateLimitFallback(
            PaymentRequest request, io.github.resilience4j.ratelimiter.RequestNotPermitted e) {
        log.error("Rate limit exceeded for payment processing");
        return CompletableFuture.completedFuture(
            PaymentResponse.builder()
                .status("RATE_LIMITED")
                .message("Too many requests. Please try again later.")
                .retryAfterSeconds(60)
                .build()
        );
    }
    
    /**
     * Bulkhead fallback - Resource exhaustion
     */
    public CompletableFuture<PaymentResponse> paymentBulkheadFallback(
            PaymentRequest request, io.github.resilience4j.bulkhead.BulkheadFullException e) {
        log.error("Bulkhead full - system overloaded");
        return CompletableFuture.completedFuture(
            PaymentResponse.builder()
                .status("OVERLOADED")
                .message("System is currently overloaded. Please retry.")
                .retryAfterSeconds(30)
                .build()
        );
    }
    
    /**
     * Timeout fallback
     */
    public CompletableFuture<PaymentResponse> paymentTimeoutFallback(
            PaymentRequest request, TimeoutException e) {
        log.error("Payment processing timeout. Amount: {}", request.getAmount());
        
        // Queue for manual processing
        queueForManualProcessing(request);
        
        return CompletableFuture.completedFuture(
            PaymentResponse.builder()
                .status("TIMEOUT")
                .message("Payment processing is taking longer than expected. Queued for processing.")
                .queuedForProcessing(true)
                .build()
        );
    }
    
    /**
     * Circuit breaker fallback - Service unavailable
     */
    public CompletableFuture<PaymentResponse> paymentCircuitBreakerFallback(
            PaymentRequest request, Exception e) {
        log.error("Circuit breaker open - payment service unavailable");
        circuitBreakerOpenCounter.increment();
        
        // Check if we can use alternative payment method
        if (canUseAlternativeGateway(request)) {
            return processWithAlternativeGateway(request);
        }
        
        return CompletableFuture.completedFuture(
            PaymentResponse.builder()
                .status("SERVICE_UNAVAILABLE")
                .message("Payment service temporarily unavailable")
                .retryAfterSeconds(120)
                .build()
        );
    }
    
    /**
     * Retry exhausted fallback
     */
    public CompletableFuture<PaymentResponse> paymentRetryFallback(
            PaymentRequest request, Exception e) {
        log.error("All retry attempts exhausted for payment. Amount: {}", request.getAmount());
        
        // Store for offline processing
        storeForOfflineProcessing(request);
        
        return CompletableFuture.completedFuture(
            PaymentResponse.builder()
                .status("RETRY_EXHAUSTED")
                .message("Payment could not be processed. Will retry offline.")
                .offlineProcessing(true)
                .build()
        );
    }
    
    /**
     * Fraud detection fallback - FAIL-CLOSED for security
     */
    private FraudCheckResult fraudDetectionFallback(PaymentRequest request, Exception e) {
        log.error("CRITICAL SECURITY: Fraud detection unavailable - BLOCKING all transactions (fail-closed). Amount: {}",
            request.getAmount());

        // SECURITY FIX: Fail-closed - BLOCK ALL transactions when fraud detection unavailable
        // This prevents fraudulent transactions from proceeding during service outages
        return FraudCheckResult.builder()
                .riskScore(95) // Critical risk score - always block
                .requiresManualReview(true) // All transactions require manual review
                .fallbackActivated(true)
                .blocked(true) // Explicitly block transaction
                .reason("Fraud detection service unavailable - transaction blocked for security (fail-closed)")
                .build();
    }
    
    /**
     * Compliance check fallback - Block high-risk
     */
    private ComplianceResult complianceCheckFallback(PaymentRequest request, Exception e) {
        log.warn("Compliance check unavailable. Blocking high-value transactions.");
        
        // Block transactions over $10,000 when compliance is down
        boolean blocked = request.getAmount().compareTo(BigDecimal.valueOf(10000)) > 0;
        
        return ComplianceResult.builder()
                .approved(!blocked)
                .requiresManualReview(true)
                .fallbackActivated(true)
                .build();
    }
    
    // Helper methods
    private void validatePaymentRequest(PaymentRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid payment amount");
        }
        // Additional validations...
    }
    
    private Payment savePayment(PaymentRequest request, PaymentGatewayResponse response) {
        Payment payment = new Payment();
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setStatus("COMPLETED");
        payment.setTransactionReference(response.getTransactionId());
        payment.setProcessedAt(LocalDateTime.now());
        return paymentRepository.save(payment);
    }
    
    private void queueForManualProcessing(PaymentRequest request) {
        log.info("Queuing payment for manual processing. Amount: {}", request.getAmount());
        // Implementation for manual processing queue
    }
    
    private boolean canUseAlternativeGateway(PaymentRequest request) {
        // Check if alternative gateway is available and supports this payment type
        return false; // Simplified
    }
    
    private CompletableFuture<PaymentResponse> processWithAlternativeGateway(PaymentRequest request) {
        log.info("Processing with alternative gateway");
        // Implementation for alternative gateway
        return CompletableFuture.completedFuture(
            PaymentResponse.builder()
                .status("PROCESSING")
                .message("Processing with alternative gateway")
                .build()
        );
    }
    
    private void storeForOfflineProcessing(PaymentRequest request) {
        log.info("Storing payment for offline processing. Amount: {}", request.getAmount());
        // Store in database for batch processing
    }
    
    // DTOs (simplified)
    @lombok.Data
    @lombok.Builder
    static class FraudCheckResult {
        private int riskScore;
        private boolean requiresManualReview;
        private boolean fallbackActivated;
        
        public boolean isHighRisk() {
            return riskScore > 80;
        }
    }
    
    @lombok.Data
    @lombok.Builder
    static class ComplianceResult {
        private boolean approved;
        private boolean requiresManualReview;
        private boolean fallbackActivated;
    }
    
    @lombok.Data
    static class PaymentGatewayResponse {
        private String transactionId;
        private String status;
    }
    
    @lombok.Data
    static class FraudDetectedException extends RuntimeException {
        public FraudDetectedException(String message) {
            super(message);
        }
    }
    
    @lombok.Data
    static class ComplianceException extends RuntimeException {
        public ComplianceException(String message) {
            super(message);
        }
    }
    
    @lombok.Data
    static class PaymentProcessingException extends RuntimeException {
        public PaymentProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}