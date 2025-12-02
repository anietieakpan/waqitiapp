package com.waqiti.payment.service;

import com.waqiti.common.telemetry.WaqitiCustomPropagator;
import com.waqiti.payment.dto.PaymentRequest;
import com.waqiti.payment.dto.PaymentResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Example Payment Service with comprehensive OpenTelemetry tracing
 * 
 * This service demonstrates best practices for manual instrumentation:
 * - Creating spans with appropriate attributes
 * - Propagating context across async operations
 * - Adding business-specific metadata
 * - Error handling and span status
 * - Performance monitoring
 * 
 * @author Waqiti Platform Team
 * @since Phase 3 - OpenTelemetry Implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TracedPaymentService {
    
    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    private final PaymentGatewayService paymentGatewayService;
    private final FraudDetectionService fraudDetectionService;
    private final ComplianceService complianceService;
    
    // Attribute keys
    private static final AttributeKey<String> PAYMENT_ID = AttributeKey.stringKey("payment.id");
    private static final AttributeKey<String> PAYMENT_TYPE = AttributeKey.stringKey("payment.type");
    private static final AttributeKey<String> PAYMENT_STATUS = AttributeKey.stringKey("payment.status");
    private static final AttributeKey<String> CURRENCY = AttributeKey.stringKey("payment.currency");
    private static final AttributeKey<Long> AMOUNT_CENTS = AttributeKey.longKey("payment.amount.cents");
    private static final AttributeKey<String> GATEWAY = AttributeKey.stringKey("payment.gateway");
    private static final AttributeKey<String> USER_ID = AttributeKey.stringKey("user.id");
    private static final AttributeKey<Boolean> HIGH_VALUE = AttributeKey.booleanKey("payment.high_value");
    private static final AttributeKey<String> COUNTRY = AttributeKey.stringKey("payment.country");
    
    /**
     * Process payment with comprehensive tracing
     */
    public CompletableFuture<PaymentResponse> processPayment(PaymentRequest request) {
        // Create root span for payment processing
        Span paymentSpan = tracer.spanBuilder("payment.process")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute(PAYMENT_ID, request.getPaymentId())
                .setAttribute(PAYMENT_TYPE, request.getPaymentType())
                .setAttribute(CURRENCY, request.getCurrency())
                .setAttribute(AMOUNT_CENTS, request.getAmount().multiply(BigDecimal.valueOf(100)).longValue())
                .setAttribute(USER_ID, request.getUserId())
                .setAttribute(HIGH_VALUE, isHighValue(request.getAmount()))
                .setAttribute(COUNTRY, request.getCountry())
                .startSpan();
        
        // Set context with business attributes
        Context context = Context.current()
                .with(paymentSpan)
                .with(WaqitiCustomPropagator.withUser(Context.current(), request.getUserId()))
                .with(WaqitiCustomPropagator.withTransaction(Context.current(), request.getPaymentId()))
                .with(WaqitiCustomPropagator.withPriority(Context.current(), 
                    isHighValue(request.getAmount()) ? "HIGH" : "MEDIUM"));
        
        return CompletableFuture.supplyAsync(() -> {
            try (Scope scope = context.makeCurrent()) {
                
                log.info("Processing payment {} for user {} amount {} {}", 
                    request.getPaymentId(), request.getUserId(), 
                    request.getAmount(), request.getCurrency());
                
                // Step 1: Validate payment
                validatePayment(request);
                
                // Step 2: Fraud detection
                performFraudDetection(request);
                
                // Step 3: Compliance check
                performComplianceCheck(request);
                
                // Step 4: Process with gateway
                PaymentResponse response = processWithGateway(request);
                
                // Update span with final status
                paymentSpan.setAttribute(PAYMENT_STATUS, response.getStatus());
                paymentSpan.setAttribute(GATEWAY, response.getGateway());
                
                if ("SUCCESS".equals(response.getStatus())) {
                    paymentSpan.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                } else {
                    paymentSpan.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, 
                        "Payment failed: " + response.getMessage());
                }
                
                log.info("Payment {} completed with status: {}", 
                    request.getPaymentId(), response.getStatus());
                
                return response;
                
            } catch (Exception e) {
                log.error("Payment {} failed: {}", request.getPaymentId(), e.getMessage(), e);
                
                // Record exception and set error status
                paymentSpan.recordException(e);
                paymentSpan.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
                paymentSpan.setAttribute("error", true);
                paymentSpan.setAttribute("error.type", e.getClass().getSimpleName());
                
                throw new RuntimeException("Payment processing failed", e);
            } finally {
                paymentSpan.end();
            }
        });
    }
    
    /**
     * Validate payment with tracing
     */
    private void validatePayment(PaymentRequest request) {
        Span span = tracer.spanBuilder("payment.validate")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(PAYMENT_ID, request.getPaymentId())
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            Instant startTime = Instant.now();
            
            // Validation logic
            if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Invalid payment amount");
            }
            
            if (request.getCurrency() == null || request.getCurrency().isEmpty()) {
                throw new IllegalArgumentException("Currency is required");
            }
            
            Duration validationTime = Duration.between(startTime, Instant.now());
            span.setAttribute("validation.duration_ms", validationTime.toMillis());
            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
            
            log.debug("Payment {} validation completed in {}ms", 
                request.getPaymentId(), validationTime.toMillis());
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Perform fraud detection with tracing
     */
    private void performFraudDetection(PaymentRequest request) {
        Span span = tracer.spanBuilder("payment.fraud_detection")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(PAYMENT_ID, request.getPaymentId())
                .setAttribute(USER_ID, request.getUserId())
                .setAttribute("fraud_detection.enabled", true)
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            Instant startTime = Instant.now();
            
            // Call fraud detection service
            FraudDetectionResult result = fraudDetectionService.checkFraud(request);
            
            Duration detectionTime = Duration.between(startTime, Instant.now());
            
            // Add fraud detection attributes
            span.setAttribute("fraud_detection.risk_score", result.getRiskScore());
            span.setAttribute("fraud_detection.status", result.getStatus());
            span.setAttribute("fraud_detection.duration_ms", detectionTime.toMillis());
            span.setAttribute("fraud_detection.rules_triggered", result.getRulesTriggered().size());
            
            if (result.isBlocked()) {
                span.setAttribute("fraud_detection.blocked", true);
                span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, "Transaction blocked by fraud detection");
                throw new FraudDetectedException("Transaction blocked due to fraud risk");
            }
            
            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
            
            log.debug("Fraud detection for payment {} completed. Risk score: {}, Duration: {}ms",
                request.getPaymentId(), result.getRiskScore(), detectionTime.toMillis());
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Perform compliance check with tracing
     */
    private void performComplianceCheck(PaymentRequest request) {
        Span span = tracer.spanBuilder("payment.compliance_check")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(PAYMENT_ID, request.getPaymentId())
                .setAttribute(USER_ID, request.getUserId())
                .setAttribute("compliance.check_type", "PAYMENT")
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            Instant startTime = Instant.now();
            
            // Call compliance service
            ComplianceResult result = complianceService.checkCompliance(request);
            
            Duration checkTime = Duration.between(startTime, Instant.now());
            
            // Add compliance attributes
            span.setAttribute("compliance.status", result.getStatus());
            span.setAttribute("compliance.duration_ms", checkTime.toMillis());
            span.setAttribute("compliance.rules_evaluated", result.getRulesEvaluated());
            span.setAttribute("compliance.requires_manual_review", result.requiresManualReview());
            
            if (!result.isApproved()) {
                span.setAttribute("compliance.blocked", true);
                span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, "Transaction blocked by compliance");
                throw new ComplianceException("Transaction blocked by compliance check");
            }
            
            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
            
            log.debug("Compliance check for payment {} completed. Status: {}, Duration: {}ms",
                request.getPaymentId(), result.getStatus(), checkTime.toMillis());
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Process with payment gateway with tracing
     */
    private PaymentResponse processWithGateway(PaymentRequest request) {
        String gateway = selectGateway(request);
        
        Span span = tracer.spanBuilder("payment.gateway_processing")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(PAYMENT_ID, request.getPaymentId())
                .setAttribute(GATEWAY, gateway)
                .setAttribute("gateway.timeout_ms", 30000)
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            Instant startTime = Instant.now();
            
            // Process with gateway
            PaymentResponse response = paymentGatewayService.processPayment(request, gateway);
            
            Duration processingTime = Duration.between(startTime, Instant.now());
            
            // Add gateway processing attributes
            span.setAttribute("gateway.response_time_ms", processingTime.toMillis());
            span.setAttribute("gateway.transaction_id", response.getTransactionId());
            span.setAttribute("gateway.response_code", response.getResponseCode());
            
            if ("SUCCESS".equals(response.getStatus())) {
                span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
            } else {
                span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, response.getMessage());
            }
            
            log.debug("Gateway processing for payment {} completed. Gateway: {}, Status: {}, Duration: {}ms",
                request.getPaymentId(), gateway, response.getStatus(), processingTime.toMillis());
            
            return response;
            
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
    
    /**
     * Helper methods
     */
    private boolean isHighValue(BigDecimal amount) {
        return amount.compareTo(BigDecimal.valueOf(10000)) > 0;
    }
    
    private String selectGateway(PaymentRequest request) {
        // Gateway selection logic
        return "stripe"; // Simplified
    }
    
    // Exception classes
    public static class FraudDetectedException extends RuntimeException {
        public FraudDetectedException(String message) {
            super(message);
        }
    }
    
    public static class ComplianceException extends RuntimeException {
        public ComplianceException(String message) {
            super(message);
        }
    }
    
    // DTOs (simplified for example)
    @lombok.Data
    public static class FraudDetectionResult {
        private int riskScore;
        private String status;
        private boolean blocked;
        private java.util.List<String> rulesTriggered;
    }
    
    @lombok.Data
    public static class ComplianceResult {
        private String status;
        private boolean approved;
        private boolean requiresManualReview;
        private int rulesEvaluated;
    }
}