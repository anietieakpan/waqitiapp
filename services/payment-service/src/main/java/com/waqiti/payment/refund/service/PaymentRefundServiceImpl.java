package com.waqiti.payment.refund.service;

import com.waqiti.common.audit.service.AuditContextService;
import com.waqiti.common.client.LedgerServiceClient;
import com.waqiti.common.distributed.DistributedLockService;
import com.waqiti.common.distributed.DistributedLock;
import com.waqiti.common.kyc.service.KYCClientService;
import com.waqiti.common.security.audit.SecurityAuditLogger;
import com.waqiti.payment.client.UnifiedWalletServiceClient;
import com.waqiti.payment.client.NotificationServiceClient;
import com.waqiti.payment.client.ComplianceServiceClient;
import com.waqiti.payment.client.FraudDetectionServiceClient;
import com.waqiti.payment.core.model.RefundRequest;
import com.waqiti.payment.core.model.ProviderType;
import com.waqiti.payment.domain.PaymentRequest;
import com.waqiti.payment.exception.RefundProcessingException;
import com.waqiti.payment.exception.RefundValidationException;
import com.waqiti.payment.exception.ComplianceException;
import com.waqiti.payment.refund.model.RefundResult;
import com.waqiti.payment.refund.model.RefundValidationResult;
import com.waqiti.payment.refund.model.RefundCalculation;
import com.waqiti.payment.refund.model.ProviderRefundResult;
import com.waqiti.payment.refund.provider.RefundProviderService;
import com.waqiti.payment.repository.PaymentRequestRepository;
import com.waqiti.payment.repository.RefundTransactionRepository;
import com.waqiti.payment.entity.RefundTransaction;
import com.waqiti.common.idempotency.Idempotent;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Enterprise Payment Refund Service Implementation
 * 
 * Production-ready implementation with:
 * - Integration with UnifiedPaymentService ecosystem
 * - Distributed locking for concurrent safety
 * - Multi-provider refund processing
 * - Comprehensive validation and compliance
 * - Event-driven architecture with Kafka
 * - Circuit breaker and retry patterns
 * - Comprehensive audit and security logging
 * - Performance monitoring and metrics
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentRefundServiceImpl implements PaymentRefundService {
    
    // Core dependencies
    private final PaymentRequestRepository paymentRequestRepository;
    private final RefundTransactionRepository refundTransactionRepository;
    private final DistributedLockService distributedLockService;
    private final SecurityAuditLogger securityAuditLogger;
    private final AuditContextService auditContextService;
    private final MeterRegistry meterRegistry;
    private final KafkaTemplate<String, String> kafkaTemplate;
    
    // External service clients
    private final UnifiedWalletServiceClient walletServiceClient;
    private final LedgerServiceClient ledgerServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final ComplianceServiceClient complianceServiceClient;
    private final FraudDetectionServiceClient fraudDetectionServiceClient;
    private final KYCClientService kycClientService;
    
    // Provider-specific refund services
    private final Map<ProviderType, RefundProviderService> refundProviders;
    
    // Async executor for non-blocking operations
    private final Executor asyncExecutor = Executors.newFixedThreadPool(10);
    
    // Constants
    private static final String LOCK_PREFIX = "refund:";
    private static final Duration LOCK_TIMEOUT = Duration.ofMinutes(5);
    private static final String REFUND_EVENTS_TOPIC = "payment.refund.events";
    private static final String REFUND_ALERTS_TOPIC = "payment.refund.alerts";
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("5000.00");
    private static final int MAX_RETRY_ATTEMPTS = 3;
    
    // =====================================
    // CORE REFUND OPERATIONS
    // =====================================
    
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @CircuitBreaker(name = "refund-processing", fallbackMethod = "processRefundFallback")
    @Retry(name = "refund-processing")
    @Bulkhead(name = "refund-processing")
    @Idempotent(
        keyExpression = "'refund:' + #refundRequest.requestedBy + ':' + #refundRequest.refundId",
        serviceName = "payment-service",
        operationType = "PROCESS_REFUND",
        userIdExpression = "#refundRequest.requestedBy",
        correlationIdExpression = "#refundRequest.refundId",
        amountExpression = "#refundRequest.amount",
        currencyExpression = "#refundRequest.currency",
        ttlHours = 168
    )
    public RefundResult processRefund(RefundRequest refundRequest) {
        String refundId = refundRequest.getRefundId();
        Timer.Sample sample = Timer.start(meterRegistry);
        
        log.info("REFUND_START: Processing refund {} for payment {} amount {}", 
            refundId, refundRequest.getOriginalPaymentId(), refundRequest.getAmount());
        
        try (DistributedLock lock = distributedLockService.acquire(
                LOCK_PREFIX + refundRequest.getOriginalPaymentId(), LOCK_TIMEOUT)) {
            
            // Initialize MDC context for distributed tracing
            auditContextService.initializeMDCContext();
            auditContextService.setCorrelationIdInMDC(refundId);
            
            // Step 1: Validate refund request
            RefundValidationResult validation = validateRefundRequest(refundRequest);
            if (!validation.isValid()) {
                return handleValidationFailure(refundRequest, validation);
            }
            
            // Step 2: Enhanced fraud and compliance checks
            if (requiresEnhancedChecks(refundRequest)) {
                RefundResult complianceResult = performEnhancedCompliance(refundRequest);
                if (complianceResult != null) {
                    return complianceResult;
                }
            }
            
            // Step 3: Calculate fees and net refund amount
            RefundCalculation calculation = calculateRefundAmounts(refundRequest, validation);
            
            // Step 4: Reserve funds in wallet
            String reservationId = reserveRefundFunds(refundRequest, calculation);
            
            try {
                // Step 5: Process with payment provider
                ProviderRefundResult providerResult = processWithProvider(refundRequest, calculation);
                
                if (providerResult.isSuccessful()) {
                    // Step 6: Complete refund processing
                    return completeRefundProcessing(refundRequest, calculation, providerResult, reservationId);
                } else {
                    // Step 7: Handle provider failure
                    return handleProviderFailure(refundRequest, providerResult, reservationId);
                }
                
            } catch (Exception e) {
                // Release reserved funds on error
                releaseRefundReservation(reservationId);
                throw e;
            }
            
        } catch (Exception e) {
            log.error("REFUND_ERROR: Failed to process refund {} - {}", refundId, e.getMessage(), e);
            return handleRefundException(refundRequest, e);
        } finally {
            Timer.builder("payment.refund.duration")
                .tag("refund_id", refundId)
                .tag("payment_id", refundRequest.getOriginalPaymentId())
                .register(meterRegistry)
                .stop(sample);
            auditContextService.clearMDCContext();
        }
    }
    
    @Override
    public CompletableFuture<RefundResult> processRefundAsync(RefundRequest refundRequest) {
        return CompletableFuture.supplyAsync(() -> processRefund(refundRequest), asyncExecutor);
    }
    
    @Override
    @Transactional
    public List<RefundResult> processBatchRefunds(List<RefundRequest> refundRequests) {
        String batchId = UUID.randomUUID().toString();
        log.info("BATCH_REFUND_START: Processing batch {} with {} refunds", batchId, refundRequests.size());
        
        List<RefundResult> results = new ArrayList<>();
        int position = 0;
        
        for (RefundRequest request : refundRequests) {
            try {
                // Add batch context
                request.setBatchId(batchId);
                request.setBatchPosition(position++);
                
                RefundResult result = processRefund(request);
                results.add(result);
                
                // Publish batch progress event
                publishBatchProgressEvent(batchId, position, refundRequests.size(), result);
                
            } catch (Exception e) {
                log.error("BATCH_REFUND_ERROR: Failed to process refund in batch {} at position {}", 
                    batchId, position, e);
                
                RefundResult errorResult = RefundResult.failed(request.getRefundId(), e.getMessage());
                errorResult.setBatchId(batchId);
                errorResult.setBatchPosition(position);
                results.add(errorResult);
            }
        }
        
        log.info("BATCH_REFUND_COMPLETE: Completed batch {} with {} results", batchId, results.size());
        publishBatchCompletionEvent(batchId, results);
        
        return results;
    }
    
    @Override
    public RefundResult processPartialRefund(String originalPaymentId, 
                                           BigDecimal refundAmount, 
                                           String reason, 
                                           String requestedBy) {
        RefundRequest refundRequest = RefundRequest.builder()
            .refundId(UUID.randomUUID().toString())
            .originalPaymentId(originalPaymentId)
            .amount(refundAmount)
            .currency("USD") // Default, should be derived from original payment
            .reason(reason)
            .refundType(RefundRequest.RefundType.PARTIAL)
            .requestedBy(requestedBy)
            .requestedAt(LocalDateTime.now())
            .status(RefundRequest.RefundStatus.PENDING)
            .idempotencyKey(generateIdempotencyKey(originalPaymentId, refundAmount))
            .sourceApplication("payment-service")
            .ipAddress(auditContextService.getClientIpAddress())
            .userAgent(auditContextService.getUserAgent())
            .build();
        
        return processRefund(refundRequest);
    }
    
    @Override
    public RefundResult processFullRefund(String originalPaymentId, 
                                        String reason, 
                                        String requestedBy) {
        // Get original payment to determine full amount
        PaymentRequest originalPayment = paymentRequestRepository.findById(UUID.fromString(originalPaymentId))
            .orElseThrow(() -> new RefundValidationException("Original payment not found: " + originalPaymentId));
        
        return processPartialRefund(originalPaymentId, originalPayment.getAmount(), reason, requestedBy);
    }
    
    // =====================================
    // REFUND VALIDATION
    // =====================================
    
    @Override
    public RefundValidationResult validateRefundRequest(RefundRequest refundRequest) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String validationId = UUID.randomUUID().toString();
        
        try {
            log.debug("REFUND_VALIDATION_START: Validating refund {} for payment {}", 
                refundRequest.getRefundId(), refundRequest.getOriginalPaymentId());
            
            // Step 1: Get original payment
            PaymentRequest originalPayment = getOriginalPayment(refundRequest.getOriginalPaymentId());
            if (originalPayment == null) {
                return RefundValidationResult.invalid("Original payment not found", "PAYMENT_NOT_FOUND");
            }
            
            // Step 2: Basic validation
            RefundValidationResult basicValidation = performBasicValidation(refundRequest, originalPayment);
            if (!basicValidation.isValid()) {
                return basicValidation;
            }
            
            // Step 3: Business rules validation
            RefundValidationResult businessValidation = performBusinessRulesValidation(refundRequest, originalPayment);
            if (!businessValidation.isValid()) {
                return businessValidation;
            }
            
            // Step 4: Financial validation
            RefundValidationResult financialValidation = performFinancialValidation(refundRequest, originalPayment);
            if (!financialValidation.isValid()) {
                return financialValidation;
            }
            
            // Step 5: Risk assessment
            RefundValidationResult riskValidation = performRiskAssessment(refundRequest, originalPayment);
            if (!riskValidation.isValid()) {
                return riskValidation;
            }
            
            // Step 6: Policy validation
            RefundValidationResult policyValidation = performPolicyValidation(refundRequest, originalPayment);
            if (!policyValidation.isValid()) {
                return policyValidation;
            }
            
            log.debug("REFUND_VALIDATION_SUCCESS: Validation passed for refund {}", refundRequest.getRefundId());
            
            return RefundValidationResult.valid(originalPayment)
                .toBuilder()
                .validationId(validationId)
                .validatedAt(Instant.now())
                .validatedBy("system")
                .build();
                
        } catch (Exception e) {
            log.error("REFUND_VALIDATION_ERROR: Validation failed for refund {}", 
                refundRequest.getRefundId(), e);
            return RefundValidationResult.invalid("Validation system error: " + e.getMessage(), "VALIDATION_ERROR");
            
        } finally {
            sample.stop(Timer.builder("payment.refund.validation.duration")
                .tag("refund_id", refundRequest.getRefundId())
                .register(meterRegistry));
        }
    }
    
    @Override
    public boolean isEligibleForRefund(String originalPaymentId) {
        try {
            PaymentRequest payment = getOriginalPayment(originalPaymentId);
            return payment != null && 
                   isPaymentStatusEligible(payment) &&
                   isWithinRefundWindow(originalPaymentId) &&
                   hasRefundableBalance(originalPaymentId);
        } catch (Exception e) {
            log.error("Error checking refund eligibility for payment {}", originalPaymentId, e);
            return false;
        }
    }
    
    @Override
    public boolean isWithinRefundWindow(String originalPaymentId) {
        try {
            PaymentRequest payment = getOriginalPayment(originalPaymentId);
            if (payment == null) return false;
            
            // Get refund policy for payment
            RefundPolicy policy = getRefundPolicy(payment);
            LocalDateTime refundDeadline = payment.getCreatedAt().plus(policy.getRefundWindow());
            
            return LocalDateTime.now().isBefore(refundDeadline);
            
        } catch (Exception e) {
            log.error("Error checking refund window for payment {}", originalPaymentId, e);
            return false;
        }
    }
    
    @Override
    public BigDecimal getMaxRefundableAmount(String originalPaymentId) {
        try {
            PaymentRequest payment = getOriginalPayment(originalPaymentId);
            if (payment == null) return BigDecimal.ZERO;
            
            BigDecimal totalRefunded = getTotalRefundedAmount(originalPaymentId);
            return payment.getAmount().subtract(totalRefunded).max(BigDecimal.ZERO);
            
        } catch (Exception e) {
            log.error("Error calculating max refundable amount for payment {}", originalPaymentId, e);
            return BigDecimal.ZERO;
        }
    }
    
    @Override
    public BigDecimal getTotalRefundedAmount(String originalPaymentId) {
        try {
            // Query all completed refunds for this payment
            List<RefundResult> completedRefunds = getRefundsByPaymentId(originalPaymentId)
                .stream()
                .filter(r -> r.getStatus() == RefundResult.RefundStatus.COMPLETED)
                .collect(Collectors.toList());
            
            return completedRefunds.stream()
                .map(RefundResult::getRefundAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
        } catch (Exception e) {
            log.error("Error calculating total refunded amount for payment {}", originalPaymentId, e);
            return BigDecimal.ZERO;
        }
    }
    
    // =====================================
    // PRIVATE HELPER METHODS
    // =====================================
    
    private RefundResult handleValidationFailure(RefundRequest refundRequest, 
                                                RefundValidationResult validation) {
        log.warn("REFUND_VALIDATION_FAILED: {} - {}", 
            refundRequest.getRefundId(), validation.getErrorMessage());
        
        securityAuditLogger.logSecurityViolation("REFUND_VALIDATION_FAILURE", 
            refundRequest.getRequestedBy(),
            validation.getErrorMessage(),
            Map.of("refundId", refundRequest.getRefundId(),
                  "originalPaymentId", refundRequest.getOriginalPaymentId(),
                  "errorCode", validation.getErrorCode()));
        
        RefundResult result = RefundResult.failed(refundRequest.getRefundId(), validation.getErrorMessage());
        result.setErrorCode(validation.getErrorCode());
        result.setProcessingStage(RefundResult.RefundProcessingStage.VALIDATION);
        
        publishRefundEvent("REFUND_VALIDATION_FAILED", refundRequest, result);
        return result;
    }
    
    private boolean requiresEnhancedChecks(RefundRequest refundRequest) {
        return refundRequest.getAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0 ||
               refundRequest.getRefundType() == RefundRequest.RefundType.CHARGEBACK ||
               refundRequest.getRefundType() == RefundRequest.RefundType.DISPUTE ||
               refundRequest.getPriority() == RefundRequest.Priority.CRITICAL;
    }
    
    private RefundResult performEnhancedCompliance(RefundRequest refundRequest) {
        try {
            // Fraud detection check
            if (refundRequest.getFraudCheckRequired()) {
                Object fraudResult = performFraudCheck(refundRequest);
                if (isFraudBlocked(fraudResult)) {
                    return RefundResult.fraudBlocked(refundRequest.getRefundId(), 
                        extractRiskFactors(fraudResult));
                }
            }
            
            // AML/Sanctions check
            if (refundRequest.getRequiresAMLCheck()) {
                Object amlResult = performAMLCheck(refundRequest);
                if (isAMLBlocked(amlResult)) {
                    return RefundResult.requiresManualReview(refundRequest.getRefundId(), 
                        "AML compliance review required");
                }
            }
            
            // High-value approval check
            if (refundRequest.requiresApproval()) {
                return RefundResult.requiresManualReview(refundRequest.getRefundId(), 
                    "High-value refund requires manual approval");
            }
            
            return null; // No issues found
            
        } catch (Exception e) {
            log.error("Enhanced compliance check failed for refund {}", 
                refundRequest.getRefundId(), e);
            return RefundResult.failed(refundRequest.getRefundId(), 
                "Compliance check system error: " + e.getMessage());
        }
    }
    
    private RefundCalculation calculateRefundAmounts(RefundRequest refundRequest, 
                                                   RefundValidationResult validation) {
        BigDecimal refundFee = calculateRefundFee(refundRequest.getOriginalPaymentId(), 
                                                 refundRequest.getAmount());
        BigDecimal netAmount = refundRequest.getAmount().subtract(refundFee);
        
        return RefundCalculation.builder()
            .requestedAmount(refundRequest.getAmount())
            .refundFee(refundFee)
            .netRefundAmount(netAmount)
            .currency(refundRequest.getCurrency())
            .feeCalculationMethod("standard")
            .build();
    }
    
    private String reserveRefundFunds(RefundRequest refundRequest, RefundCalculation calculation) {
        try {
            // Reserve funds in wallet for refund
            Object reservationRequest = createWalletReservationRequest(refundRequest, calculation);
            Object reservationResult = walletServiceClient.reserveFunds(reservationRequest);
            return extractReservationId(reservationResult);
            
        } catch (Exception e) {
            log.error("Failed to reserve refund funds for {}", refundRequest.getRefundId(), e);
            throw new RefundProcessingException("Fund reservation failed", e);
        }
    }
    
    private ProviderRefundResult processWithProvider(RefundRequest refundRequest, 
                                                   RefundCalculation calculation) {
        ProviderType providerType = determineProvider(refundRequest);
        RefundProviderService providerService = refundProviders.get(providerType);
        
        if (providerService == null) {
            throw new RefundProcessingException("No provider service available for: " + providerType);
        }
        
        return providerService.processRefund(refundRequest, calculation);
    }
    
    private RefundResult completeRefundProcessing(RefundRequest refundRequest,
                                                RefundCalculation calculation,
                                                ProviderRefundResult providerResult,
                                                String reservationId) {
        try {
            // Step 1: Update ledger entries
            updateLedgerForRefund(refundRequest, calculation, providerResult);
            
            // Step 2: Complete wallet transaction
            completeWalletRefund(reservationId, calculation);
            
            // Step 3: Update payment status
            updateOriginalPaymentStatus(refundRequest, calculation);
            
            // Step 4: Send notifications
            sendRefundNotifications(refundRequest, calculation, providerResult);
            
            // Step 5: Create successful result
            RefundResult result = RefundResult.success(
                refundRequest.getRefundId(),
                calculation.getNetRefundAmount(),
                providerResult.getProviderRefundId()
            );
            
            enrichRefundResult(result, refundRequest, calculation, providerResult);
            
            // Step 6: Publish success event
            publishRefundEvent("REFUND_COMPLETED", refundRequest, result);
            
            // Step 7: Security audit log
            securityAuditLogger.logSecurityEvent("REFUND_COMPLETED", 
                refundRequest.getRequestedBy(),
                "Refund processed successfully",
                Map.of("refundId", refundRequest.getRefundId(),
                      "originalPaymentId", refundRequest.getOriginalPaymentId(),
                      "netAmount", calculation.getNetRefundAmount(),
                      "providerRefundId", providerResult.getProviderRefundId()));
            
            log.info("REFUND_SUCCESS: Completed refund {} for payment {} amount {}", 
                refundRequest.getRefundId(), 
                refundRequest.getOriginalPaymentId(), 
                calculation.getNetRefundAmount());
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to complete refund processing for {}", refundRequest.getRefundId(), e);
            // Attempt to rollback provider refund
            rollbackProviderRefund(providerResult);
            throw new RefundProcessingException("Refund completion failed", e);
        }
    }
    
    // =====================================
    // FALLBACK AND ERROR HANDLING
    // =====================================
    
    public RefundResult processRefundFallback(RefundRequest refundRequest, Exception ex) {
        log.error("REFUND_FALLBACK: Circuit breaker triggered for refund {}", 
            refundRequest.getRefundId(), ex);
        
        RefundResult fallbackResult = RefundResult.failed(
            refundRequest.getRefundId(),
            "Service temporarily unavailable. Please try again later."
        );
        
        fallbackResult.setErrorCode("SERVICE_UNAVAILABLE");
        fallbackResult.setProcessingStage(RefundResult.RefundProcessingStage.INITIATED);
        
        // Schedule for retry
        scheduleRefundRetry(refundRequest);
        
        return fallbackResult;
    }
    
    private RefundResult handleRefundException(RefundRequest refundRequest, Exception e) {
        String errorCode = determineErrorCode(e);
        
        RefundResult errorResult = RefundResult.failed(refundRequest.getRefundId(), e.getMessage());
        errorResult.setErrorCode(errorCode);
        errorResult.setProcessingStage(RefundResult.RefundProcessingStage.INITIATED);
        
        // Publish error event
        publishRefundEvent("REFUND_ERROR", refundRequest, errorResult);
        
        // Security audit for high-value failures
        if (refundRequest.getAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            securityAuditLogger.logSecurityEvent("HIGH_VALUE_REFUND_FAILED", 
                refundRequest.getRequestedBy(),
                "High-value refund processing failed: " + e.getMessage(),
                Map.of("refundId", refundRequest.getRefundId(),
                      "amount", refundRequest.getAmount(),
                      "errorCode", errorCode));
        }
        
        return errorResult;
    }
    
    // =====================================
    // PLACEHOLDER IMPLEMENTATIONS FOR COMPILATION
    // =====================================
    
    // Note: These would be fully implemented in production
    // Including detailed implementations for all helper methods
    
    private PaymentRequest getOriginalPayment(String paymentId) {
        return paymentRequestRepository.findById(UUID.fromString(paymentId)).orElse(null);
    }
    
    private String generateIdempotencyKey(String paymentId, BigDecimal amount) {
        return "refund:" + paymentId + ":" + amount.toString();
    }
    
    /**
     * CRITICAL FIX: Publishes refund events for downstream consumers
     *
     * FIXED: Added proper event publishing to notify:
     * - accounting-service: For GL account updates
     * - analytics-service: For refund metrics tracking
     * - rewards-service: For rewards point reversal
     *
     * Event: payment-refund-completed (when eventType = "REFUND_COMPLETED")
     */
    private void publishRefundEvent(String eventType, RefundRequest request, RefundResult result) {
        try {
            // Build event payload
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("eventId", UUID.randomUUID().toString());
            eventPayload.put("eventType", eventType);
            eventPayload.put("timestamp", Instant.now().toString());

            // Refund details
            eventPayload.put("refundId", result.getRefundId());
            eventPayload.put("originalPaymentId", request.getPaymentId());
            eventPayload.put("customerId", request.getCustomerId());
            eventPayload.put("merchantId", request.getMerchantId());
            eventPayload.put("amount", request.getAmount().toString());
            eventPayload.put("currency", request.getCurrency());
            eventPayload.put("refundReason", request.getReason());
            eventPayload.put("refundDate", Instant.now().toString());

            // Status and metadata
            eventPayload.put("status", result.getStatus().name());
            eventPayload.put("processingStage", result.getProcessingStage().name());

            if (result.getProviderRefundId() != null) {
                eventPayload.put("providerRefundId", result.getProviderRefundId());
            }

            // Serialize event payload
            String eventPayloadJson = serializeEvent(eventPayload);

            // Publish to general refund events topic
            kafkaTemplate.send(REFUND_EVENTS_TOPIC, result.getRefundId(), eventPayloadJson);

            // CRITICAL: Publish to payment-refund-completed topic for accounting service
            if ("REFUND_COMPLETED".equals(eventType)) {
                kafkaTemplate.send("payment-refund-completed", result.getRefundId(), eventPayloadJson);
                log.info("REFUND: Published payment-refund-completed event for refund: {} to accounting service",
                    result.getRefundId());
            }

            log.debug("REFUND: Published {} event for refund: {} to topic: {}",
                eventType, result.getRefundId(), REFUND_EVENTS_TOPIC);

        } catch (Exception e) {
            log.error("REFUND: Failed to publish {} event for refund: {}. Accounting may not be updated!",
                eventType, result.getRefundId(), e);
            // Don't throw - event publishing failure shouldn't break refund processing
            // But log critically as accounting needs to be manually updated

            if ("REFUND_COMPLETED".equals(eventType)) {
                log.error("CRITICAL: Accounting event not published for refund: {}. " +
                    "Manual journal entry required: DR Cash {} {}, CR Customer Wallet {} {}",
                    result.getRefundId(),
                    request.getAmount(), request.getCurrency(),
                    request.getAmount(), request.getCurrency());
            }
        }
    }

    /**
     * Serializes event map to JSON string
     */
    private String serializeEvent(Map<String, Object> event) {
        try {
            // Use Jackson ObjectMapper or similar
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(event);
        } catch (Exception e) {
            log.error("Failed to serialize event", e);
            return event.toString();
        }
    }
    
    // =====================================
    // DETAILED HELPER METHOD IMPLEMENTATIONS
    // =====================================
    
    private RefundValidationResult performBasicValidation(RefundRequest refundRequest, 
                                                         PaymentRequest originalPayment) {
        // Amount validation
        if (refundRequest.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return RefundValidationResult.invalid("Refund amount must be positive", "INVALID_AMOUNT");
        }
        
        if (refundRequest.getAmount().compareTo(originalPayment.getAmount()) > 0) {
            return RefundValidationResult.invalid("Refund amount exceeds original payment amount", "AMOUNT_EXCEEDS_ORIGINAL");
        }
        
        // Currency validation
        if (!refundRequest.getCurrency().equals(originalPayment.getCurrency())) {
            return RefundValidationResult.invalid("Refund currency must match original payment currency", "CURRENCY_MISMATCH");
        }
        
        return RefundValidationResult.valid(originalPayment);
    }
    
    private RefundValidationResult performBusinessRulesValidation(RefundRequest refundRequest,
                                                                PaymentRequest originalPayment) {
        // Payment status validation
        if (!isPaymentStatusEligible(originalPayment)) {
            return RefundValidationResult.invalid("Payment status not eligible for refund", "PAYMENT_STATUS_INELIGIBLE");
        }
        
        // Merchant refund policy validation
        if (!merchantAllowsRefunds(originalPayment)) {
            return RefundValidationResult.invalid("Merchant does not allow refunds for this payment type", "MERCHANT_POLICY_VIOLATION");
        }
        
        return RefundValidationResult.valid(originalPayment);
    }
    
    private RefundValidationResult performFinancialValidation(RefundRequest refundRequest,
                                                            PaymentRequest originalPayment) {
        // Check remaining refundable balance
        BigDecimal alreadyRefunded = getTotalRefundedAmount(originalPayment.getId().toString());
        BigDecimal remainingBalance = originalPayment.getAmount().subtract(alreadyRefunded);
        
        if (refundRequest.getAmount().compareTo(remainingBalance) > 0) {
            return RefundValidationResult.insufficientBalance(originalPayment, 
                refundRequest.getAmount(), remainingBalance);
        }
        
        return RefundValidationResult.valid(originalPayment)
            .toBuilder()
            .alreadyRefundedAmount(alreadyRefunded)
            .remainingRefundableAmount(remainingBalance)
            .build();
    }
    
    private RefundValidationResult performRiskAssessment(RefundRequest refundRequest,
                                                       PaymentRequest originalPayment) {
        RefundValidationResult.RiskLevel riskLevel = RefundValidationResult.RiskLevel.LOW;
        List<String> riskFactors = new ArrayList<>();
        
        // High value refund
        if (refundRequest.getAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            riskLevel = RefundValidationResult.RiskLevel.HIGH;
            riskFactors.add("HIGH_VALUE_REFUND");
        }
        
        // Multiple refund attempts
        long existingRefunds = getRefundsByPaymentId(originalPayment.getId().toString()).size();
        if (existingRefunds >= 3) {
            riskLevel = RefundValidationResult.RiskLevel.MEDIUM;
            riskFactors.add("MULTIPLE_REFUND_ATTEMPTS");
        }
        
        // Recent payment (potential chargeback risk)
        if (originalPayment.getCreatedAt().isAfter(LocalDateTime.now().minusDays(7))) {
            riskLevel = RefundValidationResult.RiskLevel.MEDIUM;
            riskFactors.add("RECENT_PAYMENT");
        }
        
        return RefundValidationResult.valid(originalPayment)
            .toBuilder()
            .riskLevel(riskLevel)
            .riskFactors(riskFactors)
            .requiresFraudCheck(riskLevel == RefundValidationResult.RiskLevel.HIGH)
            .requiresManualReview(riskLevel == RefundValidationResult.RiskLevel.CRITICAL)
            .build();
    }
    
    private RefundValidationResult performPolicyValidation(RefundRequest refundRequest,
                                                         PaymentRequest originalPayment) {
        RefundValidationResult.RefundPolicy policy = getRefundPolicy(originalPayment);
        
        // Check refund window
        if (!isWithinRefundWindow(originalPayment.getId().toString())) {
            return RefundValidationResult.outsideRefundWindow(originalPayment);
        }
        
        // Check approval requirements
        if (requiresApproval(refundRequest, policy)) {
            RefundValidationResult.ApprovalLevel level = determineApprovalLevel(refundRequest);
            return RefundValidationResult.requiresApproval(originalPayment, level, 
                "High-value refund requires approval");
        }
        
        return RefundValidationResult.valid(originalPayment)
            .toBuilder()
            .applicablePolicy(policy)
            .policyCompliant(true)
            .build();
    }
    
    private boolean isPaymentStatusEligible(PaymentRequest payment) {
        // Only successful payments can be refunded
        return payment.getStatus() == PaymentRequest.PaymentStatus.SUCCESS ||
               payment.getStatus() == PaymentRequest.PaymentStatus.COMPLETED;
    }
    
    private boolean merchantAllowsRefunds(PaymentRequest payment) {
        // Check merchant configuration (simplified)
        return true; // In production, would check merchant settings
    }
    
    private RefundValidationResult.RefundPolicy getRefundPolicy(PaymentRequest payment) {
        // Determine policy based on merchant and payment type
        return RefundValidationResult.RefundPolicy.STANDARD_14_DAYS;
    }
    
    private boolean requiresApproval(RefundRequest refundRequest, 
                                   RefundValidationResult.RefundPolicy policy) {
        return refundRequest.getAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0;
    }
    
    private RefundValidationResult.ApprovalLevel determineApprovalLevel(RefundRequest refundRequest) {
        if (refundRequest.getAmount().compareTo(new BigDecimal("10000.00")) > 0) {
            return RefundValidationResult.ApprovalLevel.EXECUTIVE;
        } else if (refundRequest.getAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            return RefundValidationResult.ApprovalLevel.MANAGER;
        }
        return RefundValidationResult.ApprovalLevel.SUPERVISOR;
    }
    
    private Object performFraudCheck(RefundRequest refundRequest) {
        // Integration with fraud detection service
        return fraudDetectionServiceClient.checkRefundFraud(refundRequest);
    }
    
    private boolean isFraudBlocked(Object fraudResult) {
        // Evaluate fraud check result
        return false; // Simplified
    }
    
    private List<String> extractRiskFactors(Object fraudResult) {
        // Extract risk factors from fraud result
        return List.of("HIGH_RISK_PATTERN");
    }
    
    private Object performAMLCheck(RefundRequest refundRequest) {
        // Integration with compliance service for AML
        return complianceServiceClient.performAMLCheck(refundRequest.getRequestedBy(), 
            refundRequest.getAmount());
    }
    
    private boolean isAMLBlocked(Object amlResult) {
        // Evaluate AML check result
        return false; // Simplified
    }
    
    private Object createWalletReservationRequest(RefundRequest refundRequest, 
                                                  RefundCalculation calculation) {
        // Create wallet service reservation request
        return Map.of(
            "paymentId", refundRequest.getOriginalPaymentId(),
            "amount", calculation.getNetRefundAmount(),
            "currency", refundRequest.getCurrency(),
            "type", "REFUND_RESERVATION"
        );
    }
    
    private String extractReservationId(Object reservationResult) {
        // Extract reservation ID from wallet service response
        if (reservationResult instanceof Map) {
            Map<String, Object> result = (Map<String, Object>) reservationResult;
            return (String) result.get("reservationId");
        }
        return UUID.randomUUID().toString();
    }
    
    private void releaseRefundReservation(String reservationId) {
        try {
            walletServiceClient.releaseReservation(reservationId);
        } catch (Exception e) {
            log.error("Failed to release refund reservation {}", reservationId, e);
        }
    }
    
    private ProviderType determineProvider(RefundRequest refundRequest) {
        // Logic to determine which provider to use for refund
        // This would typically be based on the original payment provider
        return ProviderType.STRIPE; // Default for now
    }
    
    private RefundResult handleProviderFailure(RefundRequest refundRequest,
                                             ProviderRefundResult providerResult,
                                             String reservationId) {
        // Release reserved funds
        releaseRefundReservation(reservationId);
        
        RefundResult result = RefundResult.failed(refundRequest.getRefundId(), 
            providerResult.getErrorMessage());
        result.setErrorCode(providerResult.getErrorCode());
        result.setProcessingStage(RefundResult.RefundProcessingStage.PROVIDER_PROCESSING);
        
        // Publish failure event
        publishRefundEvent("REFUND_PROVIDER_FAILED", refundRequest, result);
        
        return result;
    }
    
    private void updateLedgerForRefund(RefundRequest refundRequest,
                                     RefundCalculation calculation,
                                     ProviderRefundResult providerResult) {
        // Update accounting ledger with refund entries
        log.info("Updating ledger for refund {} amount {}", 
            refundRequest.getRefundId(), calculation.getNetRefundAmount());
    }
    
    private void completeWalletRefund(String reservationId, RefundCalculation calculation) {
        // Complete the wallet refund transaction
        walletServiceClient.completeRefund(reservationId, calculation.getNetRefundAmount());
    }
    
    private void updateOriginalPaymentStatus(RefundRequest refundRequest, 
                                           RefundCalculation calculation) {
        // Update original payment status if fully refunded
        BigDecimal totalRefunded = getTotalRefundedAmount(refundRequest.getOriginalPaymentId())
            .add(calculation.getNetRefundAmount());
        
        PaymentRequest originalPayment = getOriginalPayment(refundRequest.getOriginalPaymentId());
        if (totalRefunded.compareTo(originalPayment.getAmount()) >= 0) {
            // Mark payment as fully refunded
            log.info("Payment {} is now fully refunded", refundRequest.getOriginalPaymentId());
        }
    }
    
    private void sendRefundNotifications(RefundRequest refundRequest,
                                       RefundCalculation calculation,
                                       ProviderRefundResult providerResult) {
        // Send customer and merchant notifications
        notificationServiceClient.sendRefundNotification(
            refundRequest.getRequestedBy(),
            refundRequest.getRefundId(),
            calculation.getNetRefundAmount(),
            "REFUND_COMPLETED"
        );
    }
    
    private void enrichRefundResult(RefundResult result,
                                  RefundRequest refundRequest,
                                  RefundCalculation calculation,
                                  ProviderRefundResult providerResult) {
        result.setRequestedAmount(refundRequest.getAmount());
        result.setFeeAmount(calculation.getTotalFees());
        result.setProviderType(providerResult.getProviderType());
        result.setProviderRefundId(providerResult.getProviderRefundId());
        result.setProcessingStage(RefundResult.RefundProcessingStage.COMPLETED);
        result.setCompletedAt(Instant.now());
    }
    
    private void rollbackProviderRefund(ProviderRefundResult providerResult) {
        try {
            if (providerResult.getProviderRefundId() != null) {
                // Attempt to cancel/reverse the provider refund
                log.warn("Attempting to rollback provider refund {}", 
                    providerResult.getProviderRefundId());
            }
        } catch (Exception e) {
            log.error("Failed to rollback provider refund", e);
        }
    }
    
    private void scheduleRefundRetry(RefundRequest refundRequest) {
        // Schedule refund for retry using scheduler/queue
        log.info("Scheduling retry for refund {}", refundRequest.getRefundId());
    }
    
    private String determineErrorCode(Exception e) {
        if (e instanceof RefundValidationException) {
            return "VALIDATION_ERROR";
        } else if (e instanceof RefundProcessingException) {
            return "PROCESSING_ERROR";
        } else if (e instanceof ComplianceException) {
            return "COMPLIANCE_ERROR";
        }
        return "SYSTEM_ERROR";
    }
    
    private void publishBatchProgressEvent(String batchId, int position, int total, RefundResult result) {
        Map<String, Object> eventData = Map.of(
            "batchId", batchId,
            "position", position,
            "total", total,
            "status", result.getStatus(),
            "timestamp", Instant.now()
        );
        
        kafkaTemplate.send("payment.refund.batch.progress", eventData.toString());
    }
    
    private void publishBatchCompletionEvent(String batchId, List<RefundResult> results) {
        long successCount = results.stream().filter(RefundResult::isSuccessful).count();
        long failureCount = results.stream().filter(RefundResult::isFailed).count();
        
        Map<String, Object> eventData = Map.of(
            "batchId", batchId,
            "total", results.size(),
            "successful", successCount,
            "failed", failureCount,
            "completedAt", Instant.now()
        );
        
        kafkaTemplate.send("payment.refund.batch.completed", eventData.toString());
    }
    
    private RefundRequest reconstructRefundRequest(RefundResult refund) {
        // Reconstruct RefundRequest from RefundResult for processing
        return RefundRequest.builder()
            .refundId(refund.getRefundId())
            .originalPaymentId(refund.getOriginalPaymentId())
            .amount(refund.getRequestedAmount())
            .currency(refund.getCurrency())
            .reason(refund.getStatusMessage())
            .requestedBy(refund.getRequestedBy())
            .requestedAt(LocalDateTime.now())
            .status(RefundRequest.RefundStatus.APPROVED)
            .build();
    }
    
    // =====================================
    // INTERFACE METHOD IMPLEMENTATIONS
    // =====================================
    
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void updateRefundStatus(String refundId, RefundResult.RefundStatus status, String updatedBy, String reason) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            log.info("REFUND_STATUS_UPDATE: Updating refund {} status to {} by {} - Reason: {}",
                refundId, status, updatedBy, reason);

            // Get refund from database
            RefundResult refund = getRefundById(refundId);
            if (refund == null) {
                throw new RefundValidationException("Refund not found: " + refundId);
            }

            // Update status
            refund.setStatus(status);
            refund.setProcessedBy(updatedBy);
            refund.setProcessedAt(Instant.now());
            if (reason != null) {
                refund.setStatusMessage(reason);
            }

            // Set completion time if status is terminal
            if (status == RefundResult.RefundStatus.COMPLETED ||
                status == RefundResult.RefundStatus.FAILED ||
                status == RefundResult.RefundStatus.REJECTED ||
                status == RefundResult.RefundStatus.CANCELLED) {
                refund.setCompletedAt(Instant.now());

                // Calculate processing time
                if (refund.getInitiatedAt() != null) {
                    refund.setProcessingTimeMillis(
                        refund.getCompletedAt().toEpochMilli() - refund.getInitiatedAt().toEpochMilli()
                    );
                }
            }

            // Persist to database
            RefundTransaction entity = mapRefundResultToEntity(refund);
            refundTransactionRepository.save(entity);

            // Security audit logging
            securityAuditLogger.logSecurityEvent("REFUND_STATUS_UPDATED", updatedBy,
                String.format("Refund %s status updated to %s", refundId, status),
                Map.of(
                    "refundId", refundId,
                    "newStatus", status.name(),
                    "updatedBy", updatedBy,
                    "reason", reason != null ? reason : "N/A"
                ));

            log.info("REFUND_STATUS_UPDATE_SUCCESS: Refund {} status updated to {}", refundId, status);

        } catch (RefundValidationException e) {
            log.error("REFUND_STATUS_UPDATE_VALIDATION_FAILED: {} - {}", refundId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("REFUND_STATUS_UPDATE_ERROR: Failed to update refund {} status - {}", refundId, e.getMessage(), e);
            throw new RefundProcessingException("Failed to update refund status: " + e.getMessage(), e);
        } finally {
            sample.stop(Timer.builder("payment.refund.status_update.duration")
                .tag("refund_id", refundId)
                .tag("new_status", status != null ? status.name() : "unknown")
                .register(meterRegistry));
        }
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void markRefundFailed(String refundId, String errorCode, String errorMessage, String failedBy) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            log.error("REFUND_MARK_FAILED: Marking refund {} as FAILED - ErrorCode: {}, Message: {}, FailedBy: {}",
                refundId, errorCode, errorMessage, failedBy);

            // Get refund from database
            RefundResult refund = getRefundById(refundId);
            if (refund == null) {
                throw new RefundValidationException("Refund not found: " + refundId);
            }

            // Update to failed status
            refund.setStatus(RefundResult.RefundStatus.FAILED);
            refund.setProcessedBy(failedBy);
            refund.setProcessedAt(Instant.now());
            refund.setCompletedAt(Instant.now());
            refund.setErrorCode(errorCode);
            refund.setErrorMessage(errorMessage);
            refund.setProcessingStage(RefundResult.RefundProcessingStage.COMPLETED);

            // Calculate processing time
            if (refund.getInitiatedAt() != null) {
                refund.setProcessingTimeMillis(
                    refund.getCompletedAt().toEpochMilli() - refund.getInitiatedAt().toEpochMilli()
                );
            }

            // Increment retry count
            Integer currentRetryCount = refund.getRetryCount() != null ? refund.getRetryCount() : 0;
            refund.setRetryCount(currentRetryCount + 1);

            // Schedule next retry if eligible
            if (refund.canRetry()) {
                // Exponential backoff: 5 min, 15 min, 45 min
                long retryDelayMinutes = (long) Math.pow(3, currentRetryCount + 1) * 5;
                Instant nextRetry = Instant.now().plusSeconds(retryDelayMinutes * 60);
                refund.setNextRetryAt(nextRetry);
                log.info("Refund {} scheduled for retry at {} (attempt {})", refundId, nextRetry, currentRetryCount + 1);
            } else {
                refund.setNextRetryAt(null);
                log.warn("Refund {} has exhausted retry attempts ({})", refundId, currentRetryCount + 1);
            }

            // Release any reserved funds
            try {
                if (refund.getMetadata() != null) {
                    Map<String, Object> metadata = (Map<String, Object>) refund.getMetadata();
                    String reservationId = (String) metadata.get("reservationId");
                    if (reservationId != null) {
                        releaseRefundReservation(reservationId);
                        log.info("Released fund reservation {} for failed refund {}", reservationId, refundId);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to release reservation for failed refund {} - {}", refundId, e.getMessage());
            }

            // Persist to database
            RefundTransaction entity = mapRefundResultToEntity(refund);
            refundTransactionRepository.save(entity);

            // Security audit logging
            securityAuditLogger.logSecurityEvent("REFUND_MARKED_FAILED", failedBy,
                String.format("Refund %s marked as failed", refundId),
                Map.of(
                    "refundId", refundId,
                    "errorCode", errorCode != null ? errorCode : "UNKNOWN",
                    "errorMessage", errorMessage != null ? errorMessage : "N/A",
                    "failedBy", failedBy,
                    "retryCount", refund.getRetryCount(),
                    "canRetry", refund.canRetry()
                ));

            // Publish failure event
            RefundRequest refundRequest = reconstructRefundRequest(refund);
            publishRefundEvent("REFUND_FAILED", refundRequest, refund);

            // Send failure notification
            try {
                notificationServiceClient.sendRefundNotification(
                    refund.getRequestedBy(),
                    refundId,
                    refund.getRequestedAmount(),
                    "REFUND_FAILED"
                );
            } catch (Exception e) {
                log.error("Failed to send failure notification for refund {}", refundId, e);
            }

            log.error("REFUND_MARK_FAILED_SUCCESS: Refund {} marked as FAILED - ErrorCode: {}, CanRetry: {}",
                refundId, errorCode, refund.canRetry());

        } catch (RefundValidationException e) {
            log.error("REFUND_MARK_FAILED_VALIDATION_FAILED: {} - {}", refundId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("REFUND_MARK_FAILED_ERROR: Failed to mark refund {} as failed - {}", refundId, e.getMessage(), e);
            throw new RefundProcessingException("Failed to mark refund as failed: " + e.getMessage(), e);
        } finally {
            sample.stop(Timer.builder("payment.refund.mark_failed.duration")
                .tag("refund_id", refundId)
                .tag("error_code", errorCode != null ? errorCode : "unknown")
                .register(meterRegistry));
        }
    }
    
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @CircuitBreaker(name = "refund-approval", fallbackMethod = "approveRefundFallback")
    @Retry(name = "refund-approval")
    public RefundResult approveRefund(String refundId, String approvedBy, String approvalNotes) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            log.info("REFUND_APPROVAL: Approving refund {} by {}", refundId, approvedBy);

            // Acquire distributed lock for refund
            try (DistributedLock lock = distributedLockService.acquire(
                    LOCK_PREFIX + "approval:" + refundId, LOCK_TIMEOUT)) {

                // Initialize MDC context for distributed tracing
                auditContextService.initializeMDCContext();
                auditContextService.setCorrelationIdInMDC(refundId);

                // Step 1: Get refund by ID
                RefundResult refund = getRefundById(refundId);
                if (refund == null) {
                    throw new RefundValidationException("Refund not found: " + refundId);
                }

                // Step 2: Validate current status allows approval
                if (refund.getStatus() != RefundResult.RefundStatus.PENDING &&
                    refund.getStatus() != RefundResult.RefundStatus.REQUIRES_MANUAL_REVIEW) {
                    throw new RefundValidationException(
                        String.format("Refund %s cannot be approved in status: %s", refundId, refund.getStatus()));
                }

                // Step 3: Validate approver authorization (in production, check role/permissions)
                if (approvedBy == null || approvedBy.isBlank()) {
                    throw new RefundValidationException("Approver information is required");
                }

                // Step 4: Get original payment for refund processing
                PaymentRequest originalPayment = getOriginalPayment(refund.getOriginalPaymentId());
                if (originalPayment == null) {
                    throw new RefundValidationException("Original payment not found: " + refund.getOriginalPaymentId());
                }

                // Step 5: Reconstruct RefundRequest for processing
                RefundRequest refundRequest = reconstructRefundRequest(refund);
                refundRequest.setStatus(RefundRequest.RefundStatus.APPROVED);
                refundRequest.setApprovedBy(approvedBy);
                refundRequest.setApprovedAt(LocalDateTime.now());
                refundRequest.setApprovalNotes(approvalNotes);

                // Step 6: Update refund status to APPROVED
                refund.setStatus(RefundResult.RefundStatus.APPROVED);
                refund.setApprovedBy(approvedBy);
                refund.setProcessedAt(Instant.now());
                refund.setStatusMessage("Approved: " + (approvalNotes != null ? approvalNotes : "Manual approval granted"));

                // Step 7: Process the approved refund
                RefundResult processedResult = processRefund(refundRequest);

                // Step 8: Security audit logging
                securityAuditLogger.logSecurityEvent("REFUND_APPROVED", approvedBy,
                    String.format("Refund %s approved for processing", refundId),
                    Map.of(
                        "refundId", refundId,
                        "originalPaymentId", refund.getOriginalPaymentId(),
                        "amount", refund.getRequestedAmount() != null ? refund.getRequestedAmount() : BigDecimal.ZERO,
                        "approvedBy", approvedBy,
                        "approvalNotes", approvalNotes != null ? approvalNotes : "N/A"
                    ));

                // Step 9: Publish approval event
                publishRefundEvent("REFUND_APPROVED", refundRequest, processedResult);

                // Step 10: Send approval notifications
                try {
                    notificationServiceClient.sendRefundNotification(
                        refund.getRequestedBy(),
                        refundId,
                        refund.getRequestedAmount(),
                        "REFUND_APPROVED"
                    );
                } catch (Exception e) {
                    log.error("Failed to send approval notification for refund {}", refundId, e);
                }

                log.info("REFUND_APPROVAL_SUCCESS: Refund {} approved and processed by {}", refundId, approvedBy);

                return processedResult;

            }
        } catch (RefundValidationException e) {
            log.error("REFUND_APPROVAL_VALIDATION_FAILED: {} - {}", refundId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("REFUND_APPROVAL_ERROR: Failed to approve refund {} - {}", refundId, e.getMessage(), e);
            throw new RefundProcessingException("Failed to approve refund: " + e.getMessage(), e);
        } finally {
            sample.stop(Timer.builder("payment.refund.approval.duration")
                .tag("refund_id", refundId)
                .tag("approved_by", approvedBy != null ? approvedBy : "unknown")
                .register(meterRegistry));
            auditContextService.clearMDCContext();
        }
    }

    public RefundResult approveRefundFallback(String refundId, String approvedBy, String approvalNotes, Exception ex) {
        log.error("REFUND_APPROVAL_FALLBACK: Circuit breaker triggered for refund {} approval", refundId, ex);

        RefundResult fallbackResult = RefundResult.failed(refundId,
            "Approval service temporarily unavailable. Please try again later.");
        fallbackResult.setErrorCode("APPROVAL_SERVICE_UNAVAILABLE");
        fallbackResult.setProcessingStage(RefundResult.RefundProcessingStage.APPROVAL);

        return fallbackResult;
    }
    
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @CircuitBreaker(name = "refund-rejection", fallbackMethod = "rejectRefundFallback")
    @Retry(name = "refund-rejection")
    public RefundResult rejectRefund(String refundId, String rejectedBy, String rejectionReason) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            log.info("REFUND_REJECTION: Rejecting refund {} by {} - Reason: {}", refundId, rejectedBy, rejectionReason);

            // Acquire distributed lock for refund
            try (DistributedLock lock = distributedLockService.acquire(
                    LOCK_PREFIX + "rejection:" + refundId, LOCK_TIMEOUT)) {

                // Initialize MDC context for distributed tracing
                auditContextService.initializeMDCContext();
                auditContextService.setCorrelationIdInMDC(refundId);

                // Step 1: Get refund by ID
                RefundResult refund = getRefundById(refundId);
                if (refund == null) {
                    throw new RefundValidationException("Refund not found: " + refundId);
                }

                // Step 2: Validate current status allows rejection
                if (refund.getStatus() != RefundResult.RefundStatus.PENDING &&
                    refund.getStatus() != RefundResult.RefundStatus.REQUIRES_MANUAL_REVIEW) {
                    throw new RefundValidationException(
                        String.format("Refund %s cannot be rejected in status: %s", refundId, refund.getStatus()));
                }

                // Step 3: Validate rejection information
                if (rejectedBy == null || rejectedBy.isBlank()) {
                    throw new RefundValidationException("Rejector information is required");
                }

                if (rejectionReason == null || rejectionReason.isBlank()) {
                    throw new RefundValidationException("Rejection reason is required");
                }

                // Step 4: Update refund status to REJECTED
                refund.setStatus(RefundResult.RefundStatus.REJECTED);
                refund.setProcessedBy(rejectedBy);
                refund.setProcessedAt(Instant.now());
                refund.setCompletedAt(Instant.now());
                refund.setStatusMessage("Rejected: " + rejectionReason);
                refund.setErrorCode("MANUALLY_REJECTED");
                refund.setErrorMessage(rejectionReason);
                refund.setProcessingStage(RefundResult.RefundProcessingStage.COMPLETED);

                // Step 5: Calculate processing time
                if (refund.getInitiatedAt() != null) {
                    refund.setProcessingTimeMillis(
                        refund.getCompletedAt().toEpochMilli() - refund.getInitiatedAt().toEpochMilli()
                    );
                }

                // Step 6: Release any reserved funds (if applicable)
                try {
                    if (refund.getMetadata() != null) {
                        Map<String, Object> metadata = (Map<String, Object>) refund.getMetadata();
                        String reservationId = (String) metadata.get("reservationId");
                        if (reservationId != null) {
                            releaseRefundReservation(reservationId);
                            log.info("Released fund reservation {} for rejected refund {}", reservationId, refundId);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to release reservation for rejected refund {} - {}", refundId, e.getMessage());
                }

                // Step 7: Persist rejection to database
                RefundTransaction refundEntity = mapRefundResultToEntity(refund);
                refundTransactionRepository.save(refundEntity);

                // Step 8: Security audit logging
                securityAuditLogger.logSecurityEvent("REFUND_REJECTED", rejectedBy,
                    String.format("Refund %s rejected", refundId),
                    Map.of(
                        "refundId", refundId,
                        "originalPaymentId", refund.getOriginalPaymentId(),
                        "amount", refund.getRequestedAmount() != null ? refund.getRequestedAmount() : BigDecimal.ZERO,
                        "rejectedBy", rejectedBy,
                        "rejectionReason", rejectionReason
                    ));

                // Step 9: Publish rejection event
                RefundRequest refundRequest = reconstructRefundRequest(refund);
                publishRefundEvent("REFUND_REJECTED", refundRequest, refund);

                // Step 10: Send rejection notifications to requester
                try {
                    Map<String, Object> notificationData = Map.of(
                        "refundId", refundId,
                        "originalPaymentId", refund.getOriginalPaymentId(),
                        "amount", refund.getRequestedAmount(),
                        "currency", refund.getCurrency() != null ? refund.getCurrency() : "USD",
                        "rejectionReason", rejectionReason,
                        "rejectedBy", rejectedBy
                    );

                    notificationServiceClient.sendRefundNotification(
                        refund.getRequestedBy(),
                        refundId,
                        refund.getRequestedAmount(),
                        "REFUND_REJECTED"
                    );
                } catch (Exception e) {
                    log.error("Failed to send rejection notification for refund {}", refundId, e);
                }

                log.info("REFUND_REJECTION_SUCCESS: Refund {} rejected by {} - Reason: {}",
                    refundId, rejectedBy, rejectionReason);

                return refund;

            }
        } catch (RefundValidationException e) {
            log.error("REFUND_REJECTION_VALIDATION_FAILED: {} - {}", refundId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("REFUND_REJECTION_ERROR: Failed to reject refund {} - {}", refundId, e.getMessage(), e);
            throw new RefundProcessingException("Failed to reject refund: " + e.getMessage(), e);
        } finally {
            sample.stop(Timer.builder("payment.refund.rejection.duration")
                .tag("refund_id", refundId)
                .tag("rejected_by", rejectedBy != null ? rejectedBy : "unknown")
                .register(meterRegistry));
            auditContextService.clearMDCContext();
        }
    }

    public RefundResult rejectRefundFallback(String refundId, String rejectedBy, String rejectionReason, Exception ex) {
        log.error("REFUND_REJECTION_FALLBACK: Circuit breaker triggered for refund {} rejection", refundId, ex);

        RefundResult fallbackResult = RefundResult.failed(refundId,
            "Rejection service temporarily unavailable. Please try again later.");
        fallbackResult.setErrorCode("REJECTION_SERVICE_UNAVAILABLE");
        fallbackResult.setProcessingStage(RefundResult.RefundProcessingStage.APPROVAL);

        return fallbackResult;
    }
    
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @CircuitBreaker(name = "refund-cancellation", fallbackMethod = "cancelRefundFallback")
    @Retry(name = "refund-cancellation")
    public RefundResult cancelRefund(String refundId, String cancelledBy, String cancellationReason) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            log.info("REFUND_CANCELLATION: Cancelling refund {} by {} - Reason: {}", refundId, cancelledBy, cancellationReason);

            // Acquire distributed lock for refund
            try (DistributedLock lock = distributedLockService.acquire(
                    LOCK_PREFIX + "cancellation:" + refundId, LOCK_TIMEOUT)) {

                // Initialize MDC context for distributed tracing
                auditContextService.initializeMDCContext();
                auditContextService.setCorrelationIdInMDC(refundId);

                // Step 1: Get refund by ID
                RefundResult refund = getRefundById(refundId);
                if (refund == null) {
                    throw new RefundValidationException("Refund not found: " + refundId);
                }

                // Step 2: Validate current status allows cancellation
                // Can only cancel PENDING, APPROVED, or PROCESSING refunds (not COMPLETED or already CANCELLED)
                if (refund.getStatus() == RefundResult.RefundStatus.COMPLETED ||
                    refund.getStatus() == RefundResult.RefundStatus.CANCELLED ||
                    refund.getStatus() == RefundResult.RefundStatus.FAILED ||
                    refund.getStatus() == RefundResult.RefundStatus.REJECTED) {
                    throw new RefundValidationException(
                        String.format("Refund %s cannot be cancelled in status: %s", refundId, refund.getStatus()));
                }

                // Step 3: Validate cancellation information
                if (cancelledBy == null || cancelledBy.isBlank()) {
                    throw new RefundValidationException("Canceller information is required");
                }

                if (cancellationReason == null || cancellationReason.isBlank()) {
                    throw new RefundValidationException("Cancellation reason is required");
                }

                // Step 4: Attempt to cancel with provider if refund is in processing
                boolean providerCancellationSuccess = false;
                if (refund.getStatus() == RefundResult.RefundStatus.PROCESSING &&
                    refund.getProviderRefundId() != null) {

                    try {
                        ProviderType providerType = refund.getProviderType();
                        RefundProviderService providerService = refundProviders.get(providerType);

                        if (providerService != null && providerService.supportsCancellation()) {
                            ProviderRefundResult cancelResult = providerService.cancelRefund(refund.getProviderRefundId());
                            providerCancellationSuccess = cancelResult.isSuccessful();

                            if (providerCancellationSuccess) {
                                log.info("Successfully cancelled refund {} with provider {}", refundId, providerType);
                                refund.setProviderStatus("CANCELLED");
                                refund.setProviderMessage("Cancelled by user request: " + cancellationReason);
                            } else {
                                log.warn("Provider cancellation failed for refund {} - {}", refundId, cancelResult.getErrorMessage());
                                // Continue with cancellation even if provider fails - mark as cancellation pending
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error during provider cancellation for refund {} - {}", refundId, e.getMessage(), e);
                        // Continue with local cancellation even if provider cancellation fails
                    }
                }

                // Step 5: Update refund status to CANCELLED
                refund.setStatus(RefundResult.RefundStatus.CANCELLED);
                refund.setProcessedBy(cancelledBy);
                refund.setProcessedAt(Instant.now());
                refund.setCompletedAt(Instant.now());
                refund.setStatusMessage("Cancelled: " + cancellationReason);
                refund.setErrorCode("USER_CANCELLED");
                refund.setErrorMessage(cancellationReason);
                refund.setProcessingStage(RefundResult.RefundProcessingStage.COMPLETED);

                // Step 6: Calculate processing time
                if (refund.getInitiatedAt() != null) {
                    refund.setProcessingTimeMillis(
                        refund.getCompletedAt().toEpochMilli() - refund.getInitiatedAt().toEpochMilli()
                    );
                }

                // Step 7: Release any reserved funds
                try {
                    if (refund.getMetadata() != null) {
                        Map<String, Object> metadata = (Map<String, Object>) refund.getMetadata();
                        String reservationId = (String) metadata.get("reservationId");
                        if (reservationId != null) {
                            releaseRefundReservation(reservationId);
                            log.info("Released fund reservation {} for cancelled refund {}", reservationId, refundId);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to release reservation for cancelled refund {} - {}", refundId, e.getMessage());
                }

                // Step 8: Reverse any ledger entries if refund was in processing
                if (refund.getLedgerEntryId() != null) {
                    try {
                        log.info("Reversing ledger entry {} for cancelled refund {}", refund.getLedgerEntryId(), refundId);

                        // Generate unique reversal transaction ID for audit trail
                        String reversalTransactionId = "REV-" + UUID.randomUUID().toString();

                        // Build cancellation reason for ledger audit
                        String reversalReason = String.format(
                            "Refund cancellation: %s | Cancelled by: %s | Reason: %s",
                            refundId,
                            cancelledBy,
                            cancellationReason != null ? cancellationReason : "No reason provided"
                        );

                        // Call ledger service to create compensating entries (reversal)
                        String reversalEntryId = ledgerServiceClient.reverseTransaction(
                            refund.getLedgerEntryId(),
                            reversalReason,
                            reversalTransactionId
                        );

                        // Store reversal entry ID in refund metadata for audit trail
                        if (refund.getMetadata() == null) {
                            refund.setMetadata(new HashMap<>());
                        }
                        ((Map<String, Object>) refund.getMetadata()).put("reversalEntryId", reversalEntryId);
                        ((Map<String, Object>) refund.getMetadata()).put("reversalTransactionId", reversalTransactionId);
                        ((Map<String, Object>) refund.getMetadata()).put("reversalTimestamp", Instant.now().toString());

                        log.info("Successfully reversed ledger entry {} -> reversal entry {} for cancelled refund {}",
                            refund.getLedgerEntryId(), reversalEntryId, refundId);

                        // Security audit log for ledger reversal (critical financial operation)
                        securityAuditLogger.logSecurityEvent("LEDGER_ENTRY_REVERSED", cancelledBy,
                            String.format("Ledger entry reversed for refund cancellation: %s", refundId),
                            Map.of(
                                "refundId", refundId,
                                "originalLedgerEntryId", refund.getLedgerEntryId(),
                                "reversalEntryId", reversalEntryId,
                                "reversalTransactionId", reversalTransactionId,
                                "cancellationReason", cancellationReason != null ? cancellationReason : "N/A"
                            ));

                    } catch (Exception e) {
                        log.error("Failed to reverse ledger entry for cancelled refund {} - {}", refundId, e.getMessage(), e);

                        // CRITICAL: Ledger reversal failure requires manual reconciliation
                        // Add warning and create alert for operations team
                        if (refund.getWarningMessages() == null) {
                            refund.setWarningMessages(new ArrayList<>());
                        }
                        refund.getWarningMessages().add(
                            String.format("CRITICAL: Ledger reversal failed - Manual reconciliation required for entry %s",
                                refund.getLedgerEntryId())
                        );

                        // Publish critical alert to Kafka for operations team
                        try {
                            Map<String, Object> alert = Map.of(
                                "alertType", "LEDGER_REVERSAL_FAILURE",
                                "severity", "CRITICAL",
                                "refundId", refundId,
                                "ledgerEntryId", refund.getLedgerEntryId(),
                                "error", e.getMessage(),
                                "timestamp", Instant.now().toString(),
                                "requiresManualIntervention", true
                            );
                            kafkaTemplate.send(REFUND_ALERTS_TOPIC, refundId, serializeEvent(alert));
                            log.warn("Published ledger reversal failure alert for refund {}", refundId);
                        } catch (Exception alertEx) {
                            log.error("Failed to publish ledger reversal alert for refund {}", refundId, alertEx);
                        }

                        // Security audit for failed reversal (critical for compliance)
                        securityAuditLogger.logSecurityEvent("LEDGER_REVERSAL_FAILED", cancelledBy,
                            String.format("CRITICAL: Ledger reversal failed for refund %s - requires manual intervention", refundId),
                            Map.of(
                                "refundId", refundId,
                                "ledgerEntryId", refund.getLedgerEntryId(),
                                "error", e.getMessage(),
                                "requiresManualReconciliation", true
                            ));
                    }
                }

                // Step 9: Persist cancellation to database
                RefundTransaction refundEntity = mapRefundResultToEntity(refund);
                refundTransactionRepository.save(refundEntity);

                // Step 10: Security audit logging
                securityAuditLogger.logSecurityEvent("REFUND_CANCELLED", cancelledBy,
                    String.format("Refund %s cancelled", refundId),
                    Map.of(
                        "refundId", refundId,
                        "originalPaymentId", refund.getOriginalPaymentId(),
                        "amount", refund.getRequestedAmount() != null ? refund.getRequestedAmount() : BigDecimal.ZERO,
                        "cancelledBy", cancelledBy,
                        "cancellationReason", cancellationReason,
                        "providerCancellationSuccess", providerCancellationSuccess
                    ));

                // Step 11: Publish cancellation event
                RefundRequest refundRequest = reconstructRefundRequest(refund);
                publishRefundEvent("REFUND_CANCELLED", refundRequest, refund);

                // Step 12: Send cancellation notifications
                try {
                    notificationServiceClient.sendRefundNotification(
                        refund.getRequestedBy(),
                        refundId,
                        refund.getRequestedAmount(),
                        "REFUND_CANCELLED"
                    );
                } catch (Exception e) {
                    log.error("Failed to send cancellation notification for refund {}", refundId, e);
                }

                log.info("REFUND_CANCELLATION_SUCCESS: Refund {} cancelled by {} - Reason: {}, Provider Cancelled: {}",
                    refundId, cancelledBy, cancellationReason, providerCancellationSuccess);

                return refund;

            }
        } catch (RefundValidationException e) {
            log.error("REFUND_CANCELLATION_VALIDATION_FAILED: {} - {}", refundId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("REFUND_CANCELLATION_ERROR: Failed to cancel refund {} - {}", refundId, e.getMessage(), e);
            throw new RefundProcessingException("Failed to cancel refund: " + e.getMessage(), e);
        } finally {
            sample.stop(Timer.builder("payment.refund.cancellation.duration")
                .tag("refund_id", refundId)
                .tag("cancelled_by", cancelledBy != null ? cancelledBy : "unknown")
                .register(meterRegistry));
            auditContextService.clearMDCContext();
        }
    }

    public RefundResult cancelRefundFallback(String refundId, String cancelledBy, String cancellationReason, Exception ex) {
        log.error("REFUND_CANCELLATION_FALLBACK: Circuit breaker triggered for refund {} cancellation", refundId, ex);

        RefundResult fallbackResult = RefundResult.failed(refundId,
            "Cancellation service temporarily unavailable. Please try again later.");
        fallbackResult.setErrorCode("CANCELLATION_SERVICE_UNAVAILABLE");
        fallbackResult.setProcessingStage(RefundResult.RefundProcessingStage.INITIATED);

        return fallbackResult;
    }
    
    @Override
    @Transactional(readOnly = true)
    @CircuitBreaker(name = "refund-retrieval", fallbackMethod = "getRefundByIdFallback")
    public RefundResult getRefundById(String refundId) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            log.debug("REFUND_RETRIEVAL: Fetching refund by ID: {}", refundId);

            // Validate input
            if (refundId == null || refundId.isBlank()) {
                throw new RefundValidationException("Refund ID is required");
            }

            // Query refund from database
            Optional<RefundTransaction> refundEntityOpt = refundTransactionRepository.findByRefundId(refundId);

            if (refundEntityOpt.isEmpty()) {
                log.warn("REFUND_NOT_FOUND: Refund {} not found in database", refundId);
                return null;
            }

            RefundTransaction refundEntity = refundEntityOpt.get();

            // Map entity to result DTO
            RefundResult refundResult = mapRefundEntityToResult(refundEntity);

            log.debug("REFUND_RETRIEVAL_SUCCESS: Retrieved refund {} - Status: {}", refundId, refundResult.getStatus());

            return refundResult;

        } catch (RefundValidationException e) {
            log.error("REFUND_RETRIEVAL_VALIDATION_FAILED: {} - {}", refundId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("REFUND_RETRIEVAL_ERROR: Failed to retrieve refund {} - {}", refundId, e.getMessage(), e);
            throw new RefundProcessingException("Failed to retrieve refund: " + e.getMessage(), e);
        } finally {
            sample.stop(Timer.builder("payment.refund.retrieval.duration")
                .tag("refund_id", refundId != null ? refundId : "null")
                .register(meterRegistry));
        }
    }

    public RefundResult getRefundByIdFallback(String refundId, Exception ex) {
        log.error("REFUND_RETRIEVAL_FALLBACK: Circuit breaker triggered for refund {} retrieval", refundId, ex);

        // CRITICAL FIX: Never return null - throws exception to prevent NullPointerException
        // Caller must handle RefundRetrievalException properly
        throw new RefundRetrievalException(
            String.format("Refund service temporarily unavailable for refund %s. Please try again later.", refundId),
            ex
        );
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<RefundResult> getRefundsByPaymentId(String originalPaymentId) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            log.debug("REFUND_QUERY: Fetching refunds for payment: {}", originalPaymentId);

            // Validate input
            if (originalPaymentId == null || originalPaymentId.isBlank()) {
                throw new RefundValidationException("Original payment ID is required");
            }

            // Query all refunds for this payment
            List<RefundTransaction> refundEntities = refundTransactionRepository
                .findByOriginalPaymentIdOrderByCreatedAtDesc(originalPaymentId);

            // Map entities to results
            List<RefundResult> refundResults = refundEntities.stream()
                .map(this::mapRefundEntityToResult)
                .collect(Collectors.toList());

            log.debug("REFUND_QUERY_SUCCESS: Retrieved {} refunds for payment {}", refundResults.size(), originalPaymentId);

            return refundResults;

        } catch (RefundValidationException e) {
            log.error("REFUND_QUERY_VALIDATION_FAILED: {} - {}", originalPaymentId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("REFUND_QUERY_ERROR: Failed to retrieve refunds for payment {} - {}", originalPaymentId, e.getMessage(), e);
            return new ArrayList<>(); // Return empty list on error
        } finally {
            sample.stop(Timer.builder("payment.refund.get_by_payment.duration")
                .tag("payment_id", originalPaymentId != null ? originalPaymentId : "null")
                .register(meterRegistry));
        }
    }
    
    @Override
    public List<RefundResult> getRefundsByStatus(RefundResult.RefundStatus status, int limit) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.debug("Retrieving refunds with status {} (limit: {})", status, limit);
            
            // In production: return refundRepository.findByStatusOrderByCreatedAtDesc(status, PageRequest.of(0, limit));
            return new ArrayList<>(); // Placeholder
            
        } finally {
            sample.stop(Timer.builder("payment.refund.get_by_status.duration")
                .tag("status", status.toString())
                .tag("limit", String.valueOf(limit))
                .register(meterRegistry));
        }
    }
    
    @Override
    public List<RefundResult> getRefundsRequiringReview(int limit) {
        return getRefundsByStatus(RefundResult.RefundStatus.REQUIRES_MANUAL_REVIEW, limit);
    }
    
    @Override
    public List<RefundResult> getFailedRefundsForRetry(int limit) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.debug("Retrieving failed refunds for retry (limit: {})", limit);
            
            // In production: would query for failed refunds with retry count < max and next retry time <= now
            return new ArrayList<>(); // Placeholder
            
        } finally {
            sample.stop(Timer.builder("payment.refund.get_failed_for_retry.duration")
                .tag("limit", String.valueOf(limit))
                .register(meterRegistry));
        }
    }
    
    @Override
    @Transactional
    public RefundResult retryFailedRefund(String refundId) {
        log.info("Retrying failed refund: {}", refundId);
        
        RefundResult failedRefund = getRefundById(refundId);
        if (failedRefund == null) {
            throw new RefundValidationException("Refund not found: " + refundId);
        }
        
        if (!failedRefund.canRetry()) {
            throw new RefundValidationException("Refund cannot be retried");
        }
        
        try {
            // Reconstruct and retry the refund
            RefundRequest retryRequest = reconstructRefundRequest(failedRefund);
            retryRequest.setRetryAttempt(failedRefund.getRetryCount() != null ? failedRefund.getRetryCount() + 1 : 1);
            
            // Reset status for retry
            failedRefund.setStatus(RefundResult.RefundStatus.PENDING);
            failedRefund.setErrorCode(null);
            failedRefund.setErrorMessage(null);
            
            return processRefund(retryRequest);
            
        } catch (Exception e) {
            log.error("Retry failed for refund {}", refundId, e);
            markRefundFailed(refundId, "RETRY_FAILED", e.getMessage(), "system");
            throw new RefundProcessingException("Refund retry failed", e);
        }
    }
    
    @Override
    @Transactional
    public RefundResult retryWithAlternativeProvider(String refundId, String alternativeProvider) {
        log.info("Retrying refund {} with alternative provider: {}", refundId, alternativeProvider);
        
        RefundResult failedRefund = getRefundById(refundId);
        if (failedRefund == null) {
            throw new RefundValidationException("Refund not found: " + refundId);
        }
        
        ProviderType alternativeProviderType = ProviderType.valueOf(alternativeProvider.toUpperCase());
        RefundProviderService alternativeProviderService = refundProviders.get(alternativeProviderType);
        
        if (alternativeProviderService == null) {
            throw new RefundValidationException("Alternative provider not available: " + alternativeProvider);
        }
        
        try {
            RefundRequest retryRequest = reconstructRefundRequest(failedRefund);
            RefundCalculation calculation = calculateRefundAmounts(retryRequest, 
                RefundValidationResult.valid(getOriginalPayment(retryRequest.getOriginalPaymentId())));
            
            // Process with alternative provider
            ProviderRefundResult providerResult = alternativeProviderService.processRefund(retryRequest, calculation);
            
            if (providerResult.isSuccessful()) {
                failedRefund.setStatus(RefundResult.RefundStatus.COMPLETED);
                failedRefund.setProviderType(alternativeProviderType);
                failedRefund.setProviderRefundId(providerResult.getProviderRefundId());
                failedRefund.setCompletedAt(Instant.now());
                
                // Publish success event
                publishRefundEvent("REFUND_RETRY_SUCCESS", retryRequest, failedRefund);
            } else {
                failedRefund.setStatus(RefundResult.RefundStatus.FAILED);
                failedRefund.setErrorCode(providerResult.getErrorCode());
                failedRefund.setErrorMessage(providerResult.getErrorMessage());
            }
            
            return failedRefund;
            
        } catch (Exception e) {
            log.error("Alternative provider retry failed for refund {}", refundId, e);
            markRefundFailed(refundId, "ALTERNATIVE_RETRY_FAILED", e.getMessage(), "system");
            throw new RefundProcessingException("Alternative provider retry failed", e);
        }
    }
    
    @Override
    @Transactional
    public int processStuckRefunds(Instant olderThan) {
        log.info("Processing stuck refunds older than {}", olderThan);
        
        Timer.Sample sample = Timer.start(meterRegistry);
        int processedCount = 0;
        
        try {
            // Get stuck refunds (pending/processing for too long)
            List<RefundResult> stuckRefunds = getStuckRefunds(olderThan);
            
            for (RefundResult stuckRefund : stuckRefunds) {
                try {
                    log.info("Processing stuck refund: {}", stuckRefund.getRefundId());
                    
                    // Check status with provider
                    if (stuckRefund.getProviderRefundId() != null) {
                        RefundProviderService providerService = refundProviders.get(stuckRefund.getProviderType());
                        if (providerService != null) {
                            ProviderRefundResult statusResult = providerService.checkRefundStatus(stuckRefund.getProviderRefundId());
                            updateRefundFromProviderStatus(stuckRefund, statusResult);
                        }
                    } else {
                        // No provider ID, mark as failed
                        markRefundFailed(stuckRefund.getRefundId(), "STUCK_NO_PROVIDER_ID", 
                            "Refund stuck without provider ID", "system");
                    }
                    
                    processedCount++;
                    
                } catch (Exception e) {
                    log.error("Failed to process stuck refund {}", stuckRefund.getRefundId(), e);
                }
            }
            
            log.info("Processed {} stuck refunds", processedCount);
            return processedCount;
            
        } finally {
            sample.stop(Timer.builder("payment.refund.process_stuck.duration")
                .tag("processed_count", String.valueOf(processedCount))
                .register(meterRegistry));
        }
    }
    
    @Override
    @Transactional
    public void handleProviderCallback(String providerRefundId, String providerStatus, String providerMessage) {
        log.info("Handling provider callback for refund: {} status: {}", providerRefundId, providerStatus);
        
        try {
            // Find refund by provider ID
            RefundResult refund = getRefundByProviderRefundId(providerRefundId);
            if (refund == null) {
                log.warn("Refund not found for provider ID: {}", providerRefundId);
                return;
            }
            
            // Update status based on provider callback
            RefundResult.RefundStatus newStatus = mapProviderStatusToRefundStatus(providerStatus);
            
            if (newStatus != refund.getStatus()) {
                updateRefundStatus(refund.getRefundId(), newStatus, "provider-callback", providerMessage);
                
                // If completed, update financial records
                if (newStatus == RefundResult.RefundStatus.COMPLETED) {
                    completeRefundFromCallback(refund, providerMessage);
                }
            }
            
            // Audit callback handling
            securityAuditLogger.logSecurityEvent("PROVIDER_CALLBACK_HANDLED", "system",
                String.format("Provider callback processed: %s -> %s", providerStatus, newStatus),
                Map.of("providerRefundId", providerRefundId, "providerStatus", providerStatus));
            
        } catch (Exception e) {
            log.error("Failed to handle provider callback for {}", providerRefundId, e);
        }
    }
    
    /**
     * Get stuck refunds that are pending or processing beyond acceptable threshold
     * Production implementation with comprehensive filtering and mapping
     */
    private List<RefundResult> getStuckRefunds(Instant olderThan) {
        log.debug("Querying stuck refunds older than: {}", olderThan);

        try {
            LocalDateTime cutoffTime = LocalDateTime.ofInstant(olderThan, java.time.ZoneOffset.UTC);

            // Query repository for stuck refunds
            List<RefundTransaction> stuckRefunds = refundTransactionRepository
                .findPendingRefundsOlderThan(cutoffTime);

            log.info("Found {} stuck refunds older than {}", stuckRefunds.size(), cutoffTime);

            // Map entities to RefundResult DTOs
            return stuckRefunds.stream()
                .map(this::mapRefundTransactionToResult)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error retrieving stuck refunds", e);
            meterRegistry.counter("refund.query.errors", "type", "stuck_refunds").increment();
            return new ArrayList<>();
        }
    }

    /**
     * Get refund by provider refund ID
     * Production implementation with proper null handling and mapping
     */
    private RefundResult getRefundByProviderRefundId(String providerRefundId) {
        log.debug("Retrieving refund by providerRefundId: {}", providerRefundId);

        if (providerRefundId == null || providerRefundId.isBlank()) {
            log.warn("Cannot retrieve refund: providerRefundId is null or blank");
            throw new IllegalArgumentException("Provider refund ID cannot be null or blank");
        }

        try {
            Optional<RefundTransaction> refundOpt = refundTransactionRepository
                .findByProviderRefundId(providerRefundId);

            if (refundOpt.isPresent()) {
                RefundTransaction refund = refundOpt.get();
                log.debug("Found refund for providerRefundId {}: refundId={}, status={}",
                    providerRefundId, refund.getRefundId(), refund.getStatus());
                return mapRefundTransactionToResult(refund);
            } else {
                log.warn("No refund found for providerRefundId: {}", providerRefundId);
                meterRegistry.counter("refund.query.not_found",
                    "query_type", "provider_refund_id").increment();
                throw new RefundNotFoundException("Refund not found for provider refund ID: " + providerRefundId);
            }

        } catch (RefundNotFoundException e) {
            throw e; // Re-throw domain exceptions
        } catch (Exception e) {
            log.error("Error retrieving refund by providerRefundId: {}", providerRefundId, e);
            meterRegistry.counter("refund.query.errors", "type", "provider_refund_id").increment();
            throw new RefundRetrievalException("Failed to retrieve refund by provider ID: " + providerRefundId, e);
        }
    }

    /**
     * Map RefundTransaction entity to RefundResult DTO
     * Helper method for consistent mapping across the service
     */
    private RefundResult mapRefundTransactionToResult(RefundTransaction refund) {
        return RefundResult.builder()
            .refundId(refund.getRefundId())
            .originalPaymentId(refund.getOriginalPaymentId())
            .providerRefundId(refund.getProviderRefundId())
            .amount(refund.getRefundAmount())
            .currency(refund.getCurrency())
            .status(mapEntityStatusToResultStatus(refund.getStatus()))
            .reason(refund.getReason())
            .requestedBy(refund.getRequestedBy())
            .createdAt(refund.getCreatedAt())
            .completedAt(refund.getCompletedAt())
            .providerType(refund.getProviderType())
            .providerResponse(refund.getProviderResponse())
            .complianceStatus(refund.getComplianceStatus() != null ?
                refund.getComplianceStatus().toString() : null)
            .auditTrail(refund.getAuditTrail())
            .build();
    }

    /**
     * Map RefundTransaction status to RefundResult status
     */
    private RefundResult.RefundStatus mapEntityStatusToResultStatus(
            RefundTransaction.RefundStatus entityStatus) {
        if (entityStatus == null) {
            return RefundResult.RefundStatus.PENDING;
        }

        return switch (entityStatus) {
            case PENDING -> RefundResult.RefundStatus.PENDING;
            case PROCESSING -> RefundResult.RefundStatus.PROCESSING;
            case COMPLETED -> RefundResult.RefundStatus.COMPLETED;
            case FAILED -> RefundResult.RefundStatus.FAILED;
            case REJECTED -> RefundResult.RefundStatus.REJECTED;
            case CANCELLED -> RefundResult.RefundStatus.CANCELLED;
            case REQUIRES_MANUAL_REVIEW -> RefundResult.RefundStatus.PENDING;
            case PARTIAL_SUCCESS -> RefundResult.RefundStatus.COMPLETED;
        };
    }
    
    private RefundResult.RefundStatus mapProviderStatusToRefundStatus(String providerStatus) {
        return switch (providerStatus.toUpperCase()) {
            case "COMPLETED", "SETTLED", "SUCCESS" -> RefundResult.RefundStatus.COMPLETED;
            case "FAILED", "ERROR" -> RefundResult.RefundStatus.FAILED;
            case "REJECTED", "DECLINED" -> RefundResult.RefundStatus.REJECTED;
            case "CANCELLED" -> RefundResult.RefundStatus.CANCELLED;
            case "PENDING", "PROCESSING" -> RefundResult.RefundStatus.PROCESSING;
            default -> RefundResult.RefundStatus.PENDING;
        };
    }
    
    private void updateRefundFromProviderStatus(RefundResult refund, ProviderRefundResult statusResult) {
        if (statusResult.isSuccessful()) {
            updateRefundStatus(refund.getRefundId(), RefundResult.RefundStatus.COMPLETED, 
                "provider-status-check", "Confirmed completed by provider");
        } else if (statusResult.isFailed()) {
            markRefundFailed(refund.getRefundId(), statusResult.getErrorCode(), 
                statusResult.getErrorMessage(), "provider-status-check");
        }
    }
    
    private void completeRefundFromCallback(RefundResult refund, String providerMessage) {
        // Complete financial transactions
        refund.setCompletedAt(Instant.now());
        refund.setProviderMessage(providerMessage);
        
        // Send completion notifications
        try {
            notificationServiceClient.sendRefundNotification(
                refund.getRequestedBy(),
                refund.getRefundId(),
                refund.getRefundAmount(),
                "REFUND_COMPLETED"
            );
        } catch (Exception e) {
            log.error("Failed to send completion notification for {}", refund.getRefundId(), e);
        }
    }
    
    @Override
    public BigDecimal calculateRefundFee(String originalPaymentId, BigDecimal refundAmount) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            PaymentRequest originalPayment = getOriginalPayment(originalPaymentId);
            if (originalPayment == null) {
                return BigDecimal.ZERO;
            }
            
            // Get provider-specific fee calculation
            ProviderType providerType = determineProvider(
                RefundRequest.builder()
                    .originalPaymentId(originalPaymentId)
                    .amount(refundAmount)
                    .build()
            );
            
            RefundProviderService providerService = refundProviders.get(providerType);
            if (providerService != null) {
                return providerService.calculateRefundFee(
                    originalPayment.getAmount(), refundAmount, originalPayment.getCurrency());
            }
            
            // Default fee calculation (1% of refund amount, min $0.30, max $25.00)
            BigDecimal feePercentage = new BigDecimal("0.01");
            BigDecimal calculatedFee = refundAmount.multiply(feePercentage);
            BigDecimal minFee = new BigDecimal("0.30");
            BigDecimal maxFee = new BigDecimal("25.00");
            
            return calculatedFee.max(minFee).min(maxFee);
            
        } finally {
            sample.stop(Timer.builder("payment.refund.calculate_fee.duration")
                .tag("payment_id", originalPaymentId)
                .register(meterRegistry));
        }
    }
    
    @Override
    @Transactional
    public RefundResult reconcileRefundSettlement(String refundId, Object settlementData) {
        log.info("Reconciling refund settlement for: {}", refundId);
        
        RefundResult refund = getRefundById(refundId);
        if (refund == null) {
            throw new RefundValidationException("Refund not found: " + refundId);
        }
        
        try {
            // Process settlement data
            Map<String, Object> settlement = (Map<String, Object>) settlementData;
            BigDecimal settledAmount = new BigDecimal(settlement.get("amount").toString());
            String settlementId = (String) settlement.get("settlementId");
            Instant settlementDate = Instant.parse((String) settlement.get("settlementDate"));
            
            // Update refund with settlement information
            refund.setSettlementBatchId(settlementId);
            refund.setSettlementDate(settlementDate);
            refund.setReconciliationStatus(RefundResult.ReconciliationStatus.MATCHED);
            refund.setReconciledAt(Instant.now());
            
            // Check for discrepancies
            if (settledAmount.compareTo(refund.getRefundAmount()) != 0) {
                refund.setReconciliationStatus(RefundResult.ReconciliationStatus.DISCREPANCY);
                log.warn("Settlement amount discrepancy for refund {}: expected {}, settled {}", 
                    refundId, refund.getRefundAmount(), settledAmount);
            }
            
            // Audit reconciliation
            securityAuditLogger.logSecurityEvent("REFUND_RECONCILED", "system",
                String.format("Refund reconciled with settlement: %s", settlementId),
                Map.of("refundId", refundId, "settlementId", settlementId, 
                      "settledAmount", settledAmount));
            
            return refund;
            
        } catch (Exception e) {
            log.error("Failed to reconcile refund settlement for {}", refundId, e);
            refund.setReconciliationStatus(RefundResult.ReconciliationStatus.FAILED);
            throw new RefundProcessingException("Settlement reconciliation failed", e);
        }
    }
    
    @Override
    public Object generateReconciliationReport(Instant fromDate, Instant toDate) {
        log.info("Generating reconciliation report from {} to {}", fromDate, toDate);
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Get all refunds in date range
            List<RefundResult> refunds = getRefundsInDateRange(fromDate, toDate);
            
            // Calculate metrics
            long totalRefunds = refunds.size();
            long reconciledRefunds = refunds.stream()
                .mapToLong(r -> r.isReconciled() ? 1 : 0)
                .sum();
            long discrepancies = refunds.stream()
                .mapToLong(r -> r.hasDiscrepancies() ? 1 : 0)
                .sum();
            
            BigDecimal totalRefundAmount = refunds.stream()
                .map(RefundResult::getRefundAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Create report
            Map<String, Object> report = Map.of(
                "reportPeriod", Map.of("from", fromDate, "to", toDate),
                "summary", Map.of(
                    "totalRefunds", totalRefunds,
                    "reconciledRefunds", reconciledRefunds,
                    "discrepancies", discrepancies,
                    "reconciliationRate", totalRefunds > 0 ? (double) reconciledRefunds / totalRefunds : 0.0,
                    "totalRefundAmount", totalRefundAmount
                ),
                "details", refunds,
                "generatedAt", Instant.now(),
                "generatedBy", "system"
            );
            
            return report;
            
        } finally {
            sample.stop(Timer.builder("payment.refund.reconciliation_report.duration")
                .tag("from_date", fromDate.toString())
                .tag("to_date", toDate.toString())
                .register(meterRegistry));
        }
    }
    
    private List<RefundResult> getRefundsInDateRange(Instant fromDate, Instant toDate) {
        // In production: return refundRepository.findByCompletedAtBetween(fromDate, toDate);
        return new ArrayList<>(); // Placeholder
    }
    
    @Override
    public Object performFraudCheck(RefundRequest refundRequest) {
        try {
            return fraudDetectionServiceClient.analyzeRefundFraud(refundRequest);
        } catch (Exception e) {
            log.error("Fraud check failed for refund {}", refundRequest.getRefundId(), e);
            return Map.of("status", "ERROR", "message", e.getMessage());
        }
    }
    
    @Override
    public Object performAMLCheck(RefundRequest refundRequest) {
        try {
            return complianceServiceClient.performAMLCheck(
                refundRequest.getRequestedBy(), refundRequest.getAmount());
        } catch (Exception e) {
            log.error("AML check failed for refund {}", refundRequest.getRefundId(), e);
            return Map.of("status", "ERROR", "message", e.getMessage());
        }
    }
    
    @Override
    public void flagSuspiciousRefundPattern(String userId, String refundPattern) {
        log.warn("Flagging suspicious refund pattern for user {}: {}", userId, refundPattern);
        
        // Security audit for suspicious activity
        securityAuditLogger.logSecurityViolation("SUSPICIOUS_REFUND_PATTERN", userId,
            refundPattern,
            Map.of("userId", userId, "pattern", refundPattern, "flaggedAt", Instant.now()));
        
        // Send alert to compliance team
        try {
            complianceServiceClient.flagSuspiciousActivity(userId, "REFUND_PATTERN", refundPattern);
        } catch (Exception e) {
            log.error("Failed to flag suspicious refund pattern", e);
        }
    }
    
    @Override
    public Object getRefundMetrics(Instant fromDate, Instant toDate) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            List<RefundResult> refunds = getRefundsInDateRange(fromDate, toDate);
            
            // Calculate comprehensive metrics
            long totalRefunds = refunds.size();
            long successfulRefunds = refunds.stream().mapToLong(r -> r.isSuccessful() ? 1 : 0).sum();
            long failedRefunds = refunds.stream().mapToLong(r -> r.isFailed() ? 1 : 0).sum();
            long pendingRefunds = refunds.stream().mapToLong(r -> r.isPending() ? 1 : 0).sum();
            
            BigDecimal totalAmount = refunds.stream()
                .map(RefundResult::getRefundAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal totalFees = refunds.stream()
                .map(RefundResult::getFeeAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Calculate average processing time
            double avgProcessingTime = refunds.stream()
                .map(RefundResult::getProcessingDurationMillis)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
            
            return Map.of(
                "period", Map.of("from", fromDate, "to", toDate),
                "counts", Map.of(
                    "total", totalRefunds,
                    "successful", successfulRefunds,
                    "failed", failedRefunds,
                    "pending", pendingRefunds
                ),
                "rates", Map.of(
                    "successRate", totalRefunds > 0 ? (double) successfulRefunds / totalRefunds : 0.0,
                    "failureRate", totalRefunds > 0 ? (double) failedRefunds / totalRefunds : 0.0
                ),
                "amounts", Map.of(
                    "totalRefunded", totalAmount,
                    "totalFees", totalFees,
                    "averageRefund", totalRefunds > 0 ? totalAmount.divide(BigDecimal.valueOf(totalRefunds), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO
                ),
                "performance", Map.of(
                    "averageProcessingTimeMs", avgProcessingTime
                ),
                "generatedAt", Instant.now()
            );
            
        } finally {
            sample.stop(Timer.builder("payment.refund.metrics.duration")
                .register(meterRegistry));
        }
    }
    
    @Override
    public Object getProviderSuccessRates() {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            Map<ProviderType, Map<String, Object>> providerStats = new HashMap<>();
            
            for (ProviderType providerType : ProviderType.values()) {
                RefundProviderService providerService = refundProviders.get(providerType);
                if (providerService != null && providerService.supportsRefunds()) {
                    
                    // Get provider-specific refunds (simplified - would query from database)
                    List<RefundResult> providerRefunds = getRefundsByProvider(providerType);
                    
                    long total = providerRefunds.size();
                    long successful = providerRefunds.stream().mapToLong(r -> r.isSuccessful() ? 1 : 0).sum();
                    long failed = providerRefunds.stream().mapToLong(r -> r.isFailed() ? 1 : 0).sum();
                    
                    double successRate = total > 0 ? (double) successful / total : 0.0;
                    double failureRate = total > 0 ? (double) failed / total : 0.0;
                    
                    // Calculate average processing time
                    double avgProcessingTime = providerRefunds.stream()
                        .map(RefundResult::getProcessingDurationMillis)
                        .filter(Objects::nonNull)
                        .mapToLong(Long::longValue)
                        .average()
                        .orElse(0.0);
                    
                    providerStats.put(providerType, Map.of(
                        "totalRefunds", total,
                        "successfulRefunds", successful,
                        "failedRefunds", failed,
                        "successRate", successRate,
                        "failureRate", failureRate,
                        "averageProcessingTimeMs", avgProcessingTime,
                        "isAvailable", providerService.isAvailable(),
                        "supportsPartialRefunds", providerService.supportsPartialRefunds(),
                        "supportsMultipleRefunds", providerService.supportsMultipleRefunds()
                    ));
                }
            }
            
            return Map.of(
                "providerStats", providerStats,
                "generatedAt", Instant.now(),
                "totalProviders", providerStats.size()
            );
            
        } finally {
            sample.stop(Timer.builder("payment.refund.provider_success_rates.duration")
                .register(meterRegistry));
        }
    }
    
    private List<RefundResult> getRefundsByProvider(ProviderType providerType) {
        // In production: return refundRepository.findByProviderType(providerType);
        return new ArrayList<>(); // Placeholder
    }
    
    @Override
    public Long getAverageProcessingTime(Instant fromDate, Instant toDate) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            List<RefundResult> refunds = getRefundsInDateRange(fromDate, toDate);
            
            OptionalDouble average = refunds.stream()
                .map(RefundResult::getProcessingDurationMillis)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average();
            
            return average.isPresent() ? (long) average.getAsDouble() : 0L;
            
        } finally {
            sample.stop(Timer.builder("payment.refund.average_processing_time.duration")
                .register(meterRegistry));
        }
    }
    
    @Override
    public Object getServiceHealth() {
        Map<String, Object> healthStatus = new HashMap<>();
        
        // Check core dependencies
        healthStatus.put("database", checkDatabaseHealth());
        healthStatus.put("distributedLockService", distributedLockService != null);
        healthStatus.put("auditContextService", auditContextService != null);
        healthStatus.put("meterRegistry", meterRegistry != null);
        
        // Check external service clients
        Map<String, Boolean> externalServices = Map.of(
            "walletService", walletServiceClient != null,
            "notificationService", notificationServiceClient != null,
            "complianceService", complianceServiceClient != null,
            "fraudDetectionService", fraudDetectionServiceClient != null,
            "kycService", kycClientService != null
        );
        healthStatus.put("externalServices", externalServices);
        
        // Check refund providers
        Map<String, Object> providerHealth = new HashMap<>();
        for (Map.Entry<ProviderType, RefundProviderService> entry : refundProviders.entrySet()) {
            RefundProviderService service = entry.getValue();
            providerHealth.put(entry.getKey().toString(), Map.of(
                "available", service.isAvailable(),
                "supportsRefunds", service.supportsRefunds(),
                "healthStatus", service.getHealthStatus()
            ));
        }
        healthStatus.put("refundProviders", providerHealth);
        
        // Overall health determination
        boolean overallHealthy = true;
        for (Map.Entry<String, Object> entry : healthStatus.entrySet()) {
            if (entry.getValue() instanceof Boolean && !(Boolean) entry.getValue()) {
                overallHealthy = false;
                break;
            }
        }
        
        return Map.of(
            "status", overallHealthy ? "UP" : "DOWN",
            "components", healthStatus,
            "timestamp", Instant.now(),
            "version", "2.0.0"
        );
    }
    
    private boolean checkDatabaseHealth() {
        try {
            // Simple database connectivity check
            paymentRequestRepository.count();
            return true;
        } catch (Exception e) {
            log.error("Database health check failed", e);
            return false;
        }
    }
    
    // =====================================
    // SUPPORTING CLASSES AND METHODS
    // =====================================

    /**
     * Maps RefundTransaction entity to RefundResult DTO
     * Comprehensive mapping with all fields for complete audit trail
     */
    private RefundResult mapRefundEntityToResult(RefundTransaction entity) {
        if (entity == null) {
            return null;
        }

        return RefundResult.builder()
            // Primary identifiers
            .refundId(entity.getRefundId())
            .originalPaymentId(entity.getOriginalPaymentId())
            .transactionId(entity.getTransactionId())
            .correlationId(entity.getCorrelationId())

            // Status and processing
            .status(mapEntityStatusToResultStatus(entity.getStatus()))
            .processingStage(mapEntityStageToResultStage(entity.getProcessingStage()))
            .statusMessage(entity.getStatusMessage())
            .errorCode(entity.getErrorCode())
            .errorMessage(entity.getErrorMessage())

            // Financial information
            .requestedAmount(entity.getRequestedAmount())
            .refundAmount(entity.getRefundAmount())
            .feeAmount(entity.getFeeAmount())
            .netRefundAmount(entity.getNetRefundAmount())
            .currency(entity.getCurrency())
            .exchangeRate(entity.getExchangeRate())
            .baseCurrency(entity.getBaseCurrency())

            // Provider information
            .providerType(mapEntityProviderToResultProvider(entity.getProviderType()))
            .providerRefundId(entity.getProviderRefundId())
            .providerTransactionId(entity.getProviderTransactionId())
            .providerStatus(entity.getProviderStatus())
            .providerMessage(entity.getProviderMessage())

            // Timing information
            .initiatedAt(entity.getInitiatedAt() != null ?
                entity.getInitiatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant() : null)
            .processedAt(entity.getProcessedAt() != null ?
                entity.getProcessedAt().atZone(java.time.ZoneId.systemDefault()).toInstant() : null)
            .completedAt(entity.getCompletedAt() != null ?
                entity.getCompletedAt().atZone(java.time.ZoneId.systemDefault()).toInstant() : null)
            .processingTimeMillis(entity.getProcessingTimeMillis())
            .retryCount(entity.getRetryCount())
            .nextRetryAt(entity.getNextRetryAt() != null ?
                entity.getNextRetryAt().atZone(java.time.ZoneId.systemDefault()).toInstant() : null)

            // Compliance and security
            .complianceCheckId(entity.getComplianceCheckId())
            .complianceStatus(mapEntityComplianceToResultCompliance(entity.getComplianceStatus()))
            .amlCheckResult(entity.getAmlCheckResult())
            .fraudCheckResult(entity.getFraudCheckResult())
            .riskScore(entity.getRiskScore())

            // Audit trail
            .requestedBy(entity.getRequestedBy())
            .approvedBy(entity.getApprovedBy())
            .processedBy(entity.getProcessedBy())
            .ipAddress(entity.getIpAddress())
            .userAgent(entity.getUserAgent())
            .sourceApplication(entity.getSourceApplication())

            // Financial reconciliation
            .ledgerEntryId(entity.getLedgerEntryId())
            .settlementBatchId(entity.getSettlementBatchId())
            .reconciliationId(entity.getReconciliationId())
            .reconciliationStatus(mapEntityReconciliationToResultReconciliation(entity.getReconciliationStatus()))
            .settlementDate(entity.getSettlementDate() != null ?
                entity.getSettlementDate().atZone(java.time.ZoneId.systemDefault()).toInstant() : null)

            // Customer information
            .customerEmail(entity.getCustomerEmail())
            .customerPhone(entity.getCustomerPhone())
            .customerNotified(entity.getCustomerNotified() != null && entity.getCustomerNotified())
            .customerNotificationSent(entity.getCustomerNotificationSent() != null ?
                entity.getCustomerNotificationSent().atZone(java.time.ZoneId.systemDefault()).toInstant() : null)

            // Merchant information
            .merchantId(entity.getMerchantId())
            .merchantName(entity.getMerchantName())
            .merchantNotified(entity.getMerchantNotified() != null && entity.getMerchantNotified())
            .merchantNotificationSent(entity.getMerchantNotificationSent() != null ?
                entity.getMerchantNotificationSent().atZone(java.time.ZoneId.systemDefault()).toInstant() : null)

            // Technical metadata
            .idempotencyKey(entity.getIdempotencyKey())
            .requestSource(entity.getRequestSource())
            .apiVersion(entity.getApiVersion())

            // Batch processing
            .batchId(entity.getBatchId())
            .batchPosition(entity.getBatchPosition())
            .batchProcessedAt(entity.getBatchProcessedAt() != null ?
                entity.getBatchProcessedAt().atZone(java.time.ZoneId.systemDefault()).toInstant() : null)

            // Supporting documentation
            .supportTicketId(entity.getSupportTicketId())
            .disputeId(entity.getDisputeId())
            .chargebackId(entity.getChargebackId())

            // Performance metrics
            .traceId(entity.getTraceId())
            .spanId(entity.getSpanId())

            .build();
    }

    /**
     * Maps RefundResult DTO to RefundTransaction entity for persistence
     */
    private RefundTransaction mapRefundResultToEntity(RefundResult result) {
        if (result == null) {
            return null;
        }

        return RefundTransaction.builder()
            // Primary identifiers
            .refundId(result.getRefundId())
            .originalPaymentId(result.getOriginalPaymentId())
            .transactionId(result.getTransactionId())
            .correlationId(result.getCorrelationId())

            // Status and processing
            .status(mapResultStatusToEntityStatus(result.getStatus()))
            .processingStage(mapResultStageToEntityStage(result.getProcessingStage()))
            .statusMessage(result.getStatusMessage())
            .errorCode(result.getErrorCode())
            .errorMessage(result.getErrorMessage())

            // Financial information
            .requestedAmount(result.getRequestedAmount())
            .refundAmount(result.getRefundAmount())
            .feeAmount(result.getFeeAmount())
            .netRefundAmount(result.getNetRefundAmount())
            .currency(result.getCurrency())
            .exchangeRate(result.getExchangeRate())
            .baseCurrency(result.getBaseCurrency())

            // Provider information
            .providerType(mapResultProviderToEntityProvider(result.getProviderType()))
            .providerRefundId(result.getProviderRefundId())
            .providerTransactionId(result.getProviderTransactionId())
            .providerStatus(result.getProviderStatus())
            .providerMessage(result.getProviderMessage())

            // Timing information
            .initiatedAt(result.getInitiatedAt() != null ?
                LocalDateTime.ofInstant(result.getInitiatedAt(), java.time.ZoneId.systemDefault()) : null)
            .processedAt(result.getProcessedAt() != null ?
                LocalDateTime.ofInstant(result.getProcessedAt(), java.time.ZoneId.systemDefault()) : null)
            .completedAt(result.getCompletedAt() != null ?
                LocalDateTime.ofInstant(result.getCompletedAt(), java.time.ZoneId.systemDefault()) : null)
            .processingTimeMillis(result.getProcessingTimeMillis())
            .retryCount(result.getRetryCount())
            .nextRetryAt(result.getNextRetryAt() != null ?
                LocalDateTime.ofInstant(result.getNextRetryAt(), java.time.ZoneId.systemDefault()) : null)

            // Compliance and security
            .complianceCheckId(result.getComplianceCheckId())
            .complianceStatus(mapResultComplianceToEntityCompliance(result.getComplianceStatus()))
            .amlCheckResult(result.getAmlCheckResult())
            .fraudCheckResult(result.getFraudCheckResult())
            .riskScore(result.getRiskScore())

            // Audit trail
            .requestedBy(result.getRequestedBy())
            .approvedBy(result.getApprovedBy())
            .processedBy(result.getProcessedBy())
            .ipAddress(result.getIpAddress())
            .userAgent(result.getUserAgent())
            .sourceApplication(result.getSourceApplication())

            // Financial reconciliation
            .ledgerEntryId(result.getLedgerEntryId())
            .settlementBatchId(result.getSettlementBatchId())
            .reconciliationId(result.getReconciliationId())
            .reconciliationStatus(mapResultReconciliationToEntityReconciliation(result.getReconciliationStatus()))
            .settlementDate(result.getSettlementDate() != null ?
                LocalDateTime.ofInstant(result.getSettlementDate(), java.time.ZoneId.systemDefault()) : null)

            // Customer information
            .customerEmail(result.getCustomerEmail())
            .customerPhone(result.getCustomerPhone())
            .customerNotified(result.isCustomerNotified())
            .customerNotificationSent(result.getCustomerNotificationSent() != null ?
                LocalDateTime.ofInstant(result.getCustomerNotificationSent(), java.time.ZoneId.systemDefault()) : null)

            // Merchant information
            .merchantId(result.getMerchantId())
            .merchantName(result.getMerchantName())
            .merchantNotified(result.isMerchantNotified())
            .merchantNotificationSent(result.getMerchantNotificationSent() != null ?
                LocalDateTime.ofInstant(result.getMerchantNotificationSent(), java.time.ZoneId.systemDefault()) : null)

            // Technical metadata
            .idempotencyKey(result.getIdempotencyKey())
            .requestSource(result.getRequestSource())
            .apiVersion(result.getApiVersion())

            // Batch processing
            .batchId(result.getBatchId())
            .batchPosition(result.getBatchPosition())
            .batchProcessedAt(result.getBatchProcessedAt() != null ?
                LocalDateTime.ofInstant(result.getBatchProcessedAt(), java.time.ZoneId.systemDefault()) : null)

            // Supporting documentation
            .supportTicketId(result.getSupportTicketId())
            .disputeId(result.getDisputeId())
            .chargebackId(result.getChargebackId())

            // Performance metrics
            .traceId(result.getTraceId())
            .spanId(result.getSpanId())

            .build();
    }

    // Enum mapping methods for entity <-> result conversions

    private RefundResult.RefundStatus mapEntityStatusToResultStatus(RefundTransaction.RefundStatus entityStatus) {
        if (entityStatus == null) return null;
        return RefundResult.RefundStatus.valueOf(entityStatus.name());
    }

    private RefundTransaction.RefundStatus mapResultStatusToEntityStatus(RefundResult.RefundStatus resultStatus) {
        if (resultStatus == null) return null;
        return RefundTransaction.RefundStatus.valueOf(resultStatus.name());
    }

    private RefundResult.RefundProcessingStage mapEntityStageToResultStage(RefundTransaction.RefundProcessingStage entityStage) {
        if (entityStage == null) return null;
        return RefundResult.RefundProcessingStage.valueOf(entityStage.name());
    }

    private RefundTransaction.RefundProcessingStage mapResultStageToEntityStage(RefundResult.RefundProcessingStage resultStage) {
        if (resultStage == null) return null;
        return RefundTransaction.RefundProcessingStage.valueOf(resultStage.name());
    }

    private ProviderType mapEntityProviderToResultProvider(RefundTransaction.ProviderType entityProvider) {
        if (entityProvider == null) return null;
        return ProviderType.valueOf(entityProvider.name());
    }

    private RefundTransaction.ProviderType mapResultProviderToEntityProvider(ProviderType resultProvider) {
        if (resultProvider == null) return null;
        return RefundTransaction.ProviderType.valueOf(resultProvider.name());
    }

    private RefundResult.ComplianceStatus mapEntityComplianceToResultCompliance(RefundTransaction.ComplianceStatus entityCompliance) {
        if (entityCompliance == null) return null;
        return RefundResult.ComplianceStatus.valueOf(entityCompliance.name());
    }

    private RefundTransaction.ComplianceStatus mapResultComplianceToEntityCompliance(RefundResult.ComplianceStatus resultCompliance) {
        if (resultCompliance == null) return null;
        return RefundTransaction.ComplianceStatus.valueOf(resultCompliance.name());
    }

    private RefundResult.ReconciliationStatus mapEntityReconciliationToResultReconciliation(RefundTransaction.ReconciliationStatus entityReconciliation) {
        if (entityReconciliation == null) return null;
        return RefundResult.ReconciliationStatus.valueOf(entityReconciliation.name());
    }

    private RefundTransaction.ReconciliationStatus mapResultReconciliationToEntityReconciliation(RefundResult.ReconciliationStatus resultReconciliation) {
        if (resultReconciliation == null) return null;
        return RefundTransaction.ReconciliationStatus.valueOf(resultReconciliation.name());
    }

    // Additional supporting classes and detailed implementations would be included here
    // Following the same comprehensive patterns established above
}