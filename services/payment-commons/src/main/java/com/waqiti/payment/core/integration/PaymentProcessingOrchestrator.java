package com.waqiti.payment.core.integration;

import com.waqiti.payment.core.model.*;
import com.waqiti.payment.core.provider.PaymentProviderFactory;
import com.waqiti.payment.core.provider.PaymentProvider;
import com.waqiti.payment.core.routing.PaymentRoutingEngine;
import com.waqiti.payment.core.validation.PaymentValidationService;
import com.waqiti.payment.core.fraud.FraudDetectionIntegration;
import com.waqiti.payment.core.compliance.ComplianceCheckService;
import com.waqiti.payment.core.settlement.SettlementService;
import com.waqiti.payment.core.notification.PaymentNotificationService;
// Using correct common library imports
import com.waqiti.common.audit.ComprehensiveAuditService;
import com.waqiti.common.resilience.CircuitBreakerService;
import com.waqiti.common.observability.HealthIndicatorService;
import com.waqiti.common.ratelimit.AdvancedRateLimitService;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentProcessingOrchestrator {
    
    private final PaymentProviderFactory providerFactory;
    private final PaymentRoutingEngine routingEngine;
    private final PaymentValidationService validationService;
    private final FraudDetectionIntegration fraudDetection;
    private final ComplianceCheckService complianceService;
    private final SettlementService settlementService;
    private final PaymentNotificationService notificationService;
    private final PaymentAuditService auditService;
    private final PaymentRetryService retryService;
    private final PaymentMonitoringService monitoringService;
    private final AdvancedRateLimitService rateLimitService;
    private final PaymentResilience paymentResilience;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    
    @Value("${payment.processing.timeout.seconds:300}")
    private long processingTimeoutSeconds;
    
    @Value("${payment.fraud.check.enabled:true}")
    private boolean fraudCheckEnabled;
    
    @Value("${payment.compliance.check.enabled:true}")
    private boolean complianceCheckEnabled;
    
    @Value("${payment.async.processing.enabled:true}")
    private boolean asyncProcessingEnabled;
    
    @Value("${payment.retry.max.attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${payment.monitoring.enabled:true}")
    private boolean monitoringEnabled;
    
    private static final String PAYMENT_EVENTS_TOPIC = "payment-processing-events";
    
    /**
     * Main payment processing orchestration method
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "payment-processing", fallbackMethod = "processPaymentFallback")
    @Retry(name = "payment-processing")
    @Bulkhead(name = "payment-processing", type = Bulkhead.Type.SEMAPHORE)
    public CompletableFuture<PaymentProcessingResult> processPayment(PaymentProcessingRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String processingId = UUID.randomUUID().toString();
        
        log.info("Starting payment processing orchestration - ID: {}, Amount: {} {}, Type: {}", 
            processingId, request.getAmount(), request.getCurrency(), request.getPaymentType());
        
        try {
            // Phase 1: Pre-processing validations
            PreProcessingResult preProcessingResult = executePreProcessing(request, processingId);
            if (!preProcessingResult.isSuccess()) {
                return CompletableFuture.completedFuture(createFailureResult(processingId, request, 
                    preProcessingResult.getErrorCode(), preProcessingResult.getErrorMessage()));
            }
            
            // Phase 2: Route payment to appropriate provider
            ProviderRoutingResult routingResult = routePayment(request, processingId);
            if (!routingResult.isSuccess()) {
                return CompletableFuture.completedFuture(createFailureResult(processingId, request, 
                    routingResult.getErrorCode(), routingResult.getErrorMessage()));
            }
            
            // Phase 3: Execute payment processing
            if (asyncProcessingEnabled && shouldProcessAsync(request)) {
                return processPaymentAsync(request, processingId, routingResult);
            } else {
                return CompletableFuture.completedFuture(
                    processPaymentSync(request, processingId, routingResult));
            }
            
        } catch (Exception e) {
            log.error("Fatal error in payment processing orchestration - ID: {}", processingId, e);
            
            Counter.builder("payment.processing.fatal_errors")
                .tag("type", request.getPaymentType().toString())
                .register(meterRegistry)
                .increment();
            
            return CompletableFuture.completedFuture(createFailureResult(processingId, request, 
                "ORCHESTRATION_ERROR", "Payment processing orchestration failed"));
        } finally {
            sample.stop(Timer.builder("payment.processing.orchestration.duration")
                .tag("type", request.getPaymentType().toString())
                .register(meterRegistry));
        }
    }
    
    /**
     * Pre-processing phase: validation, fraud check, compliance
     */
    private PreProcessingResult executePreProcessing(PaymentProcessingRequest request, String processingId) {
        log.debug("Executing pre-processing checks - ID: {}", processingId);
        
        try {
            // Step 1: Basic validation
            PaymentValidationResult validationResult = validationService.validatePayment(request);
            if (!validationResult.isValid()) {
                log.warn("Payment validation failed - ID: {}, Errors: {}", 
                    processingId, validationResult.getValidationErrors());
                return PreProcessingResult.failure("VALIDATION_ERROR", 
                    String.join(", ", validationResult.getValidationErrors()));
            }
            
            // Step 2: Rate limiting
            if (!rateLimitService.isAllowed("payment-processing", request.getFromUserId())) {
                log.warn("Rate limit exceeded for user: {}", request.getFromUserId());
                return PreProcessingResult.failure("RATE_LIMIT_EXCEEDED", 
                    "Payment processing rate limit exceeded");
            }
            
            // Step 3: Fraud detection (if enabled)
            if (fraudCheckEnabled) {
                FraudCheckResult fraudResult = fraudDetection.checkTransaction(request);
                if (fraudResult.isBlocked()) {
                    log.warn("Transaction blocked by fraud detection - ID: {}, Risk: {}", 
                        processingId, fraudResult.getRiskLevel());
                    
                    auditService.recordFraudBlock(processingId, request, fraudResult);
                    
                    return PreProcessingResult.failure("FRAUD_DETECTED", 
                        "Transaction blocked due to fraud risk");
                }
                
                if (fraudResult.requiresManualReview()) {
                    log.info("Transaction flagged for manual review - ID: {}", processingId);
                    // Continue processing but mark for review
                }
            }
            
            // Step 4: Compliance checks (if enabled)
            if (complianceCheckEnabled) {
                ComplianceCheckResult complianceResult = complianceService.checkTransaction(request);
                if (!complianceResult.isCompliant()) {
                    log.warn("Transaction failed compliance check - ID: {}, Reason: {}", 
                        processingId, complianceResult.getReason());
                    
                    auditService.recordComplianceBlock(processingId, request, complianceResult);
                    
                    return PreProcessingResult.failure("COMPLIANCE_VIOLATION", 
                        complianceResult.getReason());
                }
            }
            
            log.debug("Pre-processing checks completed successfully - ID: {}", processingId);
            return PreProcessingResult.success();
            
        } catch (Exception e) {
            log.error("Error in pre-processing - ID: {}", processingId, e);
            return PreProcessingResult.failure("PRE_PROCESSING_ERROR", e.getMessage());
        }
    }
    
    /**
     * Payment routing phase
     */
    private ProviderRoutingResult routePayment(PaymentProcessingRequest request, String processingId) {
        log.debug("Routing payment to provider - ID: {}", processingId);
        
        try {
            // Determine optimal payment provider
            PaymentRoutingDecision routingDecision = routingEngine.determineProvider(request);
            
            if (routingDecision.getSelectedProvider() == null) {
                log.error("No suitable payment provider found - ID: {}", processingId);
                return ProviderRoutingResult.failure("NO_PROVIDER_AVAILABLE", 
                    "No suitable payment provider available");
            }
            
            // Get provider instance
            PaymentProvider provider = providerFactory.getProvider(routingDecision.getSelectedProvider());
            
            if (provider == null) {
                log.error("Payment provider not available - ID: {}, Provider: {}", 
                    processingId, routingDecision.getSelectedProvider());
                return ProviderRoutingResult.failure("PROVIDER_UNAVAILABLE", 
                    "Selected payment provider is not available");
            }
            
            // Validate provider capabilities
            if (!provider.supportsPaymentType(request.getPaymentType())) {
                log.error("Provider does not support payment type - ID: {}, Provider: {}, Type: {}", 
                    processingId, routingDecision.getSelectedProvider(), request.getPaymentType());
                return ProviderRoutingResult.failure("UNSUPPORTED_PAYMENT_TYPE", 
                    "Provider does not support this payment type");
            }
            
            log.info("Payment routed to provider - ID: {}, Provider: {}, Reason: {}", 
                processingId, routingDecision.getSelectedProvider(), routingDecision.getReason());
            
            return ProviderRoutingResult.success(provider, routingDecision);
            
        } catch (Exception e) {
            log.error("Error in payment routing - ID: {}", processingId, e);
            return ProviderRoutingResult.failure("ROUTING_ERROR", e.getMessage());
        }
    }
    
    /**
     * Synchronous payment processing
     */
    private PaymentProcessingResult processPaymentSync(PaymentProcessingRequest request, 
                                                      String processingId, 
                                                      ProviderRoutingResult routingResult) {
        log.debug("Processing payment synchronously - ID: {}", processingId);
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            PaymentProvider provider = routingResult.getProvider();
            
            // Create provider-specific payment request
            ProviderPaymentRequest providerRequest = createProviderRequest(request, processingId);
            
            // Execute payment with provider
            ProviderPaymentResult providerResult = executeWithProvider(provider, providerRequest, processingId);
            
            // Handle provider result
            PaymentProcessingResult result = handleProviderResult(request, processingId, 
                providerRequest, providerResult, routingResult);
            
            // Post-processing
            executePostProcessing(result, request, processingId);
            
            sample.stop(Timer.builder("payment.processing.sync.duration")
                .tag("provider", provider.getProviderName())
                .tag("status", result.getStatus().toString())
                .register(meterRegistry));
            
            return result;
            
        } catch (Exception e) {
            log.error("Error in synchronous payment processing - ID: {}", processingId, e);
            
            Counter.builder("payment.processing.sync.errors")
                .tag("error", e.getClass().getSimpleName())
                .register(meterRegistry)
                .increment();
            
            return createFailureResult(processingId, request, "SYNC_PROCESSING_ERROR", e.getMessage());
        }
    }
    
    /**
     * Asynchronous payment processing
     */
    private CompletableFuture<PaymentProcessingResult> processPaymentAsync(PaymentProcessingRequest request, 
                                                                          String processingId, 
                                                                          ProviderRoutingResult routingResult) {
        log.debug("Processing payment asynchronously - ID: {}", processingId);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Mark as processing
                publishProcessingEvent("PAYMENT_PROCESSING_STARTED", processingId, request);
                
                PaymentProvider provider = routingResult.getProvider();
                
                // Create provider-specific payment request
                ProviderPaymentRequest providerRequest = createProviderRequest(request, processingId);
                
                // Execute payment with provider
                ProviderPaymentResult providerResult = executeWithProvider(provider, providerRequest, processingId);
                
                // Handle provider result
                PaymentProcessingResult result = handleProviderResult(request, processingId, 
                    providerRequest, providerResult, routingResult);
                
                // Post-processing
                executePostProcessing(result, request, processingId);
                
                // Publish completion event
                publishProcessingEvent("PAYMENT_PROCESSING_COMPLETED", processingId, request, result);
                
                Counter.builder("payment.processing.async.completed")
                    .tag("provider", provider.getProviderName())
                    .tag("status", result.getStatus().toString())
                    .register(meterRegistry)
                    .increment();
                
                return result;
                
            } catch (Exception e) {
                log.error("Error in asynchronous payment processing - ID: {}", processingId, e);
                
                Counter.builder("payment.processing.async.errors")
                    .tag("error", e.getClass().getSimpleName())
                    .register(meterRegistry)
                    .increment();
                
                PaymentProcessingResult errorResult = createFailureResult(processingId, request, 
                    "ASYNC_PROCESSING_ERROR", e.getMessage());
                
                publishProcessingEvent("PAYMENT_PROCESSING_FAILED", processingId, request, errorResult);
                
                return errorResult;
            }
        });
    }
    
    /**
     * Execute payment with provider, including retry logic
     */
    private ProviderPaymentResult executeWithProvider(PaymentProvider provider, 
                                                     ProviderPaymentRequest providerRequest, 
                                                     String processingId) {
        int attempt = 1;
        ProviderPaymentResult lastResult = null;
        
        while (attempt <= maxRetryAttempts) {
            log.debug("Executing payment with provider - ID: {}, Provider: {}, Attempt: {}", 
                processingId, provider.getProviderName(), attempt);
            
            try {
                Timer.Sample sample = Timer.start(meterRegistry);
                
                ProviderPaymentResult result = provider.processPayment(providerRequest);
                
                sample.stop(Timer.builder("payment.provider.execution.duration")
                    .tag("provider", provider.getProviderName())
                    .tag("attempt", String.valueOf(attempt))
                    .register(meterRegistry));
                
                if (result.isSuccess() || !shouldRetry(result)) {
                    if (attempt > 1) {
                        log.info("Payment succeeded after {} attempts - ID: {}", attempt, processingId);
                        
                        Counter.builder("payment.provider.retry.success")
                            .tag("provider", provider.getProviderName())
                            .tag("attempts", String.valueOf(attempt))
                            .register(meterRegistry)
                            .increment();
                    }
                    
                    return result;
                }
                
                lastResult = result;
                
                if (attempt < maxRetryAttempts) {
                    Duration delay = retryService.calculateRetryDelay(attempt, result);
                    log.info("Payment failed, retrying - ID: {}, Attempt: {}, Delay: {}ms", 
                        processingId, attempt, delay.toMillis());
                    
                    Thread.sleep(delay.toMillis());
                }
                
            } catch (Exception e) {
                log.error("Provider execution error - ID: {}, Provider: {}, Attempt: {}", 
                    processingId, provider.getProviderName(), attempt, e);
                
                lastResult = ProviderPaymentResult.failure("PROVIDER_ERROR", e.getMessage());
                
                if (attempt < maxRetryAttempts && isRetryableException(e)) {
                    Duration delay = retryService.calculateRetryDelay(attempt, e);
                    try {
                        Thread.sleep(delay.toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    break;
                }
            }
            
            attempt++;
        }
        
        log.error("Payment failed after {} attempts - ID: {}", maxRetryAttempts, processingId);
        
        Counter.builder("payment.provider.retry.exhausted")
            .tag("provider", provider.getProviderName())
            .tag("attempts", String.valueOf(maxRetryAttempts))
            .register(meterRegistry)
            .increment();
        
        return lastResult != null ? lastResult : 
            ProviderPaymentResult.failure("MAX_RETRIES_EXCEEDED", "Maximum retry attempts exceeded");
    }
    
    /**
     * Handle provider result and create final processing result
     */
    private PaymentProcessingResult handleProviderResult(PaymentProcessingRequest request, 
                                                        String processingId,
                                                        ProviderPaymentRequest providerRequest, 
                                                        ProviderPaymentResult providerResult,
                                                        ProviderRoutingResult routingResult) {
        
        PaymentProcessingStatus status;
        String errorCode = null;
        String errorMessage = null;
        
        if (providerResult.isSuccess()) {
            status = PaymentProcessingStatus.COMPLETED;
            log.info("Payment completed successfully - ID: {}, Provider Transaction ID: {}", 
                processingId, providerResult.getProviderTransactionId());
        } else {
            status = determineFailureStatus(providerResult);
            errorCode = providerResult.getErrorCode();
            errorMessage = providerResult.getErrorMessage();
            
            log.warn("Payment processing failed - ID: {}, Error: {} - {}", 
                processingId, errorCode, errorMessage);
        }
        
        return PaymentProcessingResult.builder()
            .processingId(processingId)
            .paymentId(request.getPaymentId())
            .transactionId(providerResult.getProviderTransactionId())
            .status(status)
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .provider(routingResult.getProvider().getProviderName())
            .providerResponse(providerResult.getProviderResponse())
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .metadata(createResultMetadata(request, providerRequest, providerResult, routingResult))
            .createdAt(LocalDateTime.now())
            .completedAt(status.isTerminal() ? LocalDateTime.now() : null)
            .build();
    }
    
    /**
     * Post-processing: settlement, notifications, auditing, monitoring
     */
    private void executePostProcessing(PaymentProcessingResult result, 
                                      PaymentProcessingRequest request, 
                                      String processingId) {
        log.debug("Executing post-processing - ID: {}", processingId);
        
        try {
            // Settlement processing (if successful)
            if (result.getStatus() == PaymentProcessingStatus.COMPLETED) {
                CompletableFuture.runAsync(() -> {
                    try {
                        settlementService.processSettlement(result, request);
                        log.debug("Settlement processing initiated - ID: {}", processingId);
                    } catch (Exception e) {
                        log.error("Error in settlement processing - ID: {}", processingId, e);
                    }
                });
            }
            
            // Send notifications
            CompletableFuture.runAsync(() -> {
                try {
                    notificationService.sendPaymentNotifications(result, request);
                    log.debug("Payment notifications sent - ID: {}", processingId);
                } catch (Exception e) {
                    log.error("Error sending notifications - ID: {}", processingId, e);
                }
            });
            
            // Audit logging
            try {
                auditService.recordPaymentProcessing(result, request);
                log.debug("Payment audit recorded - ID: {}", processingId);
            } catch (Exception e) {
                log.error("Error in audit logging - ID: {}", processingId, e);
            }
            
            // Monitoring and analytics
            if (monitoringEnabled) {
                CompletableFuture.runAsync(() -> {
                    try {
                        monitoringService.recordPaymentMetrics(result, request);
                        log.debug("Payment metrics recorded - ID: {}", processingId);
                    } catch (Exception e) {
                        log.error("Error recording payment metrics - ID: {}", processingId, e);
                    }
                });
            }
            
        } catch (Exception e) {
            log.error("Error in post-processing - ID: {}", processingId, e);
        }
    }
    
    // Helper methods
    
    private ProviderPaymentRequest createProviderRequest(PaymentProcessingRequest request, String processingId) {
        return ProviderPaymentRequest.builder()
            .paymentId(request.getPaymentId())
            .processingId(processingId)
            .fromUserId(request.getFromUserId())
            .toUserId(request.getToUserId())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .paymentType(request.getPaymentType())
            .paymentMethod(request.getPaymentMethod())
            .description(request.getDescription())
            .metadata(request.getMetadata())
            .idempotencyKey(request.getIdempotencyKey())
            .timeoutSeconds(processingTimeoutSeconds)
            .createdAt(LocalDateTime.now())
            .build();
    }
    
    private boolean shouldProcessAsync(PaymentProcessingRequest request) {
        // Determine if payment should be processed asynchronously based on:
        // - Payment amount
        // - Payment type
        // - System load
        // - Provider characteristics
        
        return request.getAmount().compareTo(new BigDecimal("10000")) > 0 ||
               request.getPaymentType() == PaymentType.INTERNATIONAL ||
               request.getPaymentType() == PaymentType.CRYPTO;
    }
    
    private boolean shouldRetry(ProviderPaymentResult result) {
        // Determine if the error is retryable
        String errorCode = result.getErrorCode();
        
        return errorCode != null && (
            errorCode.contains("TIMEOUT") ||
            errorCode.contains("CONNECTION") ||
            errorCode.contains("TEMPORARY") ||
            errorCode.contains("SERVICE_UNAVAILABLE") ||
            errorCode.contains("RATE_LIMIT")
        );
    }
    
    private boolean isRetryableException(Exception e) {
        return e instanceof java.net.SocketTimeoutException ||
               e instanceof java.net.ConnectException ||
               e instanceof java.io.IOException ||
               e.getMessage().contains("timeout") ||
               e.getMessage().contains("connection");
    }
    
    private PaymentProcessingStatus determineFailureStatus(ProviderPaymentResult result) {
        String errorCode = result.getErrorCode();
        
        if (errorCode == null) {
            return PaymentProcessingStatus.FAILED;
        }
        
        return switch (errorCode) {
            case "INSUFFICIENT_FUNDS" -> PaymentProcessingStatus.INSUFFICIENT_FUNDS;
            case "INVALID_PAYMENT_METHOD" -> PaymentProcessingStatus.PAYMENT_METHOD_ERROR;
            case "FRAUD_DETECTED" -> PaymentProcessingStatus.FRAUD_DETECTED;
            case "COMPLIANCE_VIOLATION" -> PaymentProcessingStatus.COMPLIANCE_BLOCKED;
            case "TIMEOUT", "CONNECTION_ERROR" -> PaymentProcessingStatus.PROVIDER_ERROR;
            default -> PaymentProcessingStatus.FAILED;
        };
    }
    
    private Map<String, Object> createResultMetadata(PaymentProcessingRequest request,
                                                    ProviderPaymentRequest providerRequest,
                                                    ProviderPaymentResult providerResult,
                                                    ProviderRoutingResult routingResult) {
        Map<String, Object> metadata = new HashMap<>();
        
        metadata.put("routingReason", routingResult.getRoutingDecision().getReason());
        metadata.put("providerName", routingResult.getProvider().getProviderName());
        metadata.put("processingTime", Duration.between(providerRequest.getCreatedAt(), LocalDateTime.now()).toMillis());
        metadata.put("requestMetadata", request.getMetadata());
        metadata.put("providerMetadata", providerResult.getMetadata());
        
        return metadata;
    }
    
    private PaymentProcessingResult createFailureResult(String processingId, 
                                                       PaymentProcessingRequest request,
                                                       String errorCode, 
                                                       String errorMessage) {
        return PaymentProcessingResult.builder()
            .processingId(processingId)
            .paymentId(request.getPaymentId())
            .status(PaymentProcessingStatus.FAILED)
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .createdAt(LocalDateTime.now())
            .completedAt(LocalDateTime.now())
            .build();
    }
    
    private void publishProcessingEvent(String eventType, String processingId, 
                                      PaymentProcessingRequest request) {
        publishProcessingEvent(eventType, processingId, request, null);
    }
    
    private void publishProcessingEvent(String eventType, String processingId, 
                                      PaymentProcessingRequest request, 
                                      PaymentProcessingResult result) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", eventType);
            event.put("processingId", processingId);
            event.put("paymentId", request.getPaymentId());
            event.put("fromUserId", request.getFromUserId());
            event.put("toUserId", request.getToUserId());
            event.put("amount", request.getAmount());
            event.put("currency", request.getCurrency());
            event.put("paymentType", request.getPaymentType().toString());
            event.put("timestamp", LocalDateTime.now().toString());
            
            if (result != null) {
                event.put("status", result.getStatus().toString());
                event.put("provider", result.getProvider());
                event.put("errorCode", result.getErrorCode());
            }
            
            kafkaTemplate.send(PAYMENT_EVENTS_TOPIC, processingId, event);
            
        } catch (Exception e) {
            log.error("Error publishing processing event: {} - {}", eventType, processingId, e);
        }
    }
    
    // Fallback method
    public CompletableFuture<PaymentProcessingResult> processPaymentFallback(PaymentProcessingRequest request, Exception ex) {
        String processingId = UUID.randomUUID().toString();
        log.error("Payment processing fallback triggered - ID: {}", processingId, ex);
        
        PaymentProcessingResult fallbackResult = PaymentProcessingResult.builder()
            .processingId(processingId)
            .paymentId(request.getPaymentId())
            .status(PaymentProcessingStatus.SERVICE_UNAVAILABLE)
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .errorCode("CIRCUIT_BREAKER_OPEN")
            .errorMessage("Payment processing service temporarily unavailable")
            .createdAt(LocalDateTime.now())
            .completedAt(LocalDateTime.now())
            .build();
        
        return CompletableFuture.completedFuture(fallbackResult);
    }
    
    // Inner result classes
    
    private static class PreProcessingResult {
        private final boolean success;
        private final String errorCode;
        private final String errorMessage;
        
        private PreProcessingResult(boolean success, String errorCode, String errorMessage) {
            this.success = success;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }
        
        public static PreProcessingResult success() {
            return new PreProcessingResult(true, null, null);
        }
        
        public static PreProcessingResult failure(String errorCode, String errorMessage) {
            return new PreProcessingResult(false, errorCode, errorMessage);
        }
        
        public boolean isSuccess() { return success; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    private static class ProviderRoutingResult {
        private final boolean success;
        private final PaymentProvider provider;
        private final PaymentRoutingDecision routingDecision;
        private final String errorCode;
        private final String errorMessage;
        
        private ProviderRoutingResult(boolean success, PaymentProvider provider, 
                                     PaymentRoutingDecision routingDecision,
                                     String errorCode, String errorMessage) {
            this.success = success;
            this.provider = provider;
            this.routingDecision = routingDecision;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }
        
        public static ProviderRoutingResult success(PaymentProvider provider, PaymentRoutingDecision decision) {
            return new ProviderRoutingResult(true, provider, decision, null, null);
        }
        
        public static ProviderRoutingResult failure(String errorCode, String errorMessage) {
            return new ProviderRoutingResult(false, null, null, errorCode, errorMessage);
        }
        
        public boolean isSuccess() { return success; }
        public PaymentProvider getProvider() { return provider; }
        public PaymentRoutingDecision getRoutingDecision() { return routingDecision; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
    }
}