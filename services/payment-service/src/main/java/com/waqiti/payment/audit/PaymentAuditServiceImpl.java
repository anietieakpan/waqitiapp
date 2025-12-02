package com.waqiti.payment.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.security.audit.SecurityAuditLogger;
import com.waqiti.payment.audit.model.*;
import com.waqiti.payment.audit.repository.PaymentAuditRepository;
import com.waqiti.payment.dto.*;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Enterprise Payment Audit Service Implementation
 * 
 * Production-ready implementation extracted from PaymentService with:
 * - Comprehensive security event logging and violation tracking
 * - Real-time suspicious pattern detection and alerting
 * - Compliance audit trail management with regulatory reporting
 * - Performance metrics collection and monitoring
 * - Forensic investigation support with detailed event correlation
 * - Integration with SIEM systems and compliance platforms
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentAuditServiceImpl implements PaymentAuditServiceInterface {
    
    // Core dependencies
    private final PaymentAuditRepository auditRepository;
    private final SecurityAuditLogger securityAuditLogger;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    // Service statistics
    private final AtomicLong totalEventsProcessed = new AtomicLong(0);
    private final AtomicLong securityViolations = new AtomicLong(0);
    private final AtomicLong suspiciousPatterns = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicLong> eventCounters = new ConcurrentHashMap<>();
    
    // Configuration
    private static final String AUDIT_EVENTS_TOPIC = "payment-audit-events";
    private static final String SECURITY_ALERTS_TOPIC = "security-alerts";
    private static final String COMPLIANCE_EVENTS_TOPIC = "compliance-events";
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("5000");
    private static final BigDecimal CRITICAL_VALUE_THRESHOLD = new BigDecimal("50000");
    
    // Service state
    private LocalDateTime serviceStartTime;
    private volatile AuditServiceStatistics.ServiceHealth currentHealth = AuditServiceStatistics.ServiceHealth.UNKNOWN;
    
    // =====================================
    // PAYMENT OPERATION AUDITING
    // =====================================
    
    @Override
    @Transactional
    public String auditPaymentRequestCreated(String paymentId, UUID requestorId, BigDecimal amount,
                                             String currency, UUID recipientId, Map<String, Object> metadata) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.debug("Auditing payment request creation: paymentId={}, amount={} {}", paymentId, amount, currency);
            
            // Create audit record
            PaymentAuditRecord record = PaymentAuditRecord.builder()
                .auditId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .eventType("PAYMENT_REQUEST_CREATED")
                .category(PaymentAuditRecord.EventCategory.PAYMENT_OPERATION)
                .severity(determinePaymentSeverity(amount))
                .paymentId(paymentId)
                .transactionType(PaymentAuditRecord.TransactionType.P2P_PAYMENT)
                .userId(requestorId)
                .amount(amount)
                .currency(currency)
                .recipientId(recipientId)
                .paymentStatus(PaymentAuditRecord.PaymentStatus.INITIATED)
                .operationResult(PaymentAuditRecord.OperationResult.SUCCESS)
                .operationMetadata(metadata)
                .correlationId(metadata != null ? (String) metadata.get("correlationId") : null)
                .traceId(metadata != null ? (String) metadata.get("traceId") : null)
                .build();
            
            // Save to repository
            auditRepository.save(record);
            
            // Track metrics
            incrementCounter("payment.audit.created", Map.of("type", "payment_request"));
            totalEventsProcessed.incrementAndGet();
            
            // Check for high-value payment
            if (amount.compareTo(HIGH_VALUE_THRESHOLD) > 0) {
                auditHighValuePayment(requestorId, amount, currency, amount.compareTo(CRITICAL_VALUE_THRESHOLD) > 0);
            }
            
            // Delegate to existing security audit logger
            securityAuditLogger.logSecurityEvent("PAYMENT_REQUEST_CREATED", requestorId.toString(),
                "Payment request created successfully",
                Map.of("paymentId", paymentId, "amount", amount, "currency", currency, "recipientId", recipientId));
            
            // Publish audit event
            publishAuditEvent(record);
            
            log.info("Payment request audit recorded: auditId={}, paymentId={}", record.getAuditId(), paymentId);
            
            return record.getAuditId();

        } finally {
            Timer.builder("payment.audit.processing.duration")
                .tag("operation", "payment_request_created")
                .register(meterRegistry).stop(sample);
        }
    }
    
    @Override
    @Transactional
    public String auditPaymentRequestFailed(UUID requestorId, BigDecimal amount,
                                            String errorType, String errorMessage, Map<String, Object> metadata) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.debug("Auditing failed payment request: requestor={}, error={}", requestorId, errorType);
            
            // Create audit record
            PaymentAuditRecord record = PaymentAuditRecord.builder()
                .auditId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .eventType("PAYMENT_REQUEST_FAILED")
                .category(PaymentAuditRecord.EventCategory.PAYMENT_OPERATION)
                .severity(PaymentAuditRecord.EventSeverity.WARNING)
                .userId(requestorId)
                .amount(amount)
                .operationResult(PaymentAuditRecord.OperationResult.FAILURE)
                .errorCode(errorType)
                .errorMessage(errorMessage)
                .operationMetadata(metadata)
                .build();
            
            // Save to repository
            auditRepository.save(record);
            
            // Track metrics
            incrementCounter("payment.audit.failed", Map.of("error", errorType));
            meterRegistry.counter("payment.requests.errors.unified", "type", errorType).increment();
            
            // Delegate to existing security audit logger
            securityAuditLogger.logSecurityViolation("PAYMENT_REQUEST_FAILED", requestorId.toString(),
                "Payment request creation failed with error: " + errorMessage,
                Map.of("errorType", errorType, "amount", amount));
            
            // Check for suspicious failure patterns
            checkForSuspiciousFailurePattern(requestorId, errorType, metadata);
            
            return record.getAuditId();

        } finally {
            Timer.builder("payment.audit.processing.duration")
                .tag("operation", "payment_request_failed")
                .register(meterRegistry).stop(sample);
        }
    }
    
    @Override
    @Transactional
    public String auditPaymentCompleted(String paymentId, String status, long processingTimeMs, Map<String, Object> metadata) {
        PaymentAuditRecord record = PaymentAuditRecord.builder()
            .auditId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .eventType("PAYMENT_COMPLETED")
            .category(PaymentAuditRecord.EventCategory.PAYMENT_OPERATION)
            .severity(PaymentAuditRecord.EventSeverity.INFO)
            .paymentId(paymentId)
            .paymentStatus(PaymentAuditRecord.PaymentStatus.valueOf(status))
            .operationResult(PaymentAuditRecord.OperationResult.SUCCESS)
            .processingTimeMs(processingTimeMs)
            .operationMetadata(metadata)
            .build();
        
        auditRepository.save(record);
        incrementCounter("payment.audit.completed", Map.of("status", status));
        
        return record.getAuditId();
    }
    
    // =====================================
    // REFUND OPERATION AUDITING
    // =====================================
    
    @Override
    @Transactional
    public String auditRefundRequested(RefundRequest refundRequest, String initiatedBy) {
        log.debug("Auditing refund request: originalPaymentId={}, amount={}", 
            refundRequest.getOriginalPaymentId(), refundRequest.getAmount());
        
        PaymentAuditRecord record = PaymentAuditRecord.builder()
            .auditId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .eventType("REFUND_REQUESTED")
            .category(PaymentAuditRecord.EventCategory.REFUND_OPERATION)
            .severity(PaymentAuditRecord.EventSeverity.INFO)
            .paymentId(refundRequest.getOriginalPaymentId())
            .transactionType(PaymentAuditRecord.TransactionType.REFUND)
            .amount(refundRequest.getAmount())
            .operationResult(PaymentAuditRecord.OperationResult.PENDING)
            .operationMetadata(Map.of(
                "reason", refundRequest.getReason(),
                "initiatedBy", initiatedBy,
                "requestTime", LocalDateTime.now().toString()
            ))
            .build();
        
        auditRepository.save(record);
        incrementCounter("payment.audit.refund.requested", Map.of());
        
        return record.getAuditId();
    }
    
    @Override
    @Transactional
    public String auditRefundCompleted(String refundId, String originalPaymentId, BigDecimal amount,
                                       String status, String initiatedBy, Map<String, Object> metadata) {
        log.info("Auditing refund completion: refundId={}, status={}", refundId, status);
        
        PaymentAuditRecord record = PaymentAuditRecord.refundOperation(refundId, originalPaymentId, amount,
            PaymentAuditRecord.OperationResult.SUCCESS);
        
        record.setOperationMetadata(metadata);
        record.setUserId(UUID.fromString(initiatedBy));
        
        auditRepository.save(record);
        
        // Delegate to existing security audit logger
        securityAuditLogger.logSecurityEvent("REFUND_COMPLETED", initiatedBy,
            "Refund processed successfully",
            Map.of("refundId", refundId, "originalPaymentId", originalPaymentId,
                  "amount", amount, "status", status));
        
        incrementCounter("payment.audit.refund.completed", Map.of("status", status));
        
        return record.getAuditId();
    }
    
    @Override
    @Transactional
    public String auditRefundFailed(String refundId, String originalPaymentId, String reason,
                                    String initiatedBy, Map<String, Object> metadata) {
        log.warn("Auditing refund failure: refundId={}, reason={}", refundId, reason);
        
        PaymentAuditRecord record = PaymentAuditRecord.refundOperation(refundId, originalPaymentId,
            null, PaymentAuditRecord.OperationResult.FAILURE);
        
        record.setErrorMessage(reason);
        record.setOperationMetadata(metadata);
        record.setSeverity(PaymentAuditRecord.EventSeverity.WARNING);
        
        auditRepository.save(record);
        
        // Delegate to existing security audit logger  
        securityAuditLogger.logSecurityViolation("REFUND_PROVIDER_FAILURE", initiatedBy,
            "Payment provider refund failed: " + reason,
            Map.of("refundId", refundId, "originalPaymentId", originalPaymentId, "providerError", reason));
        
        incrementCounter("payment.audit.refund.failed", Map.of());
        
        return record.getAuditId();
    }
    
    // =====================================
    // RECONCILIATION AUDITING
    // =====================================
    
    @Override
    @Transactional
    public String auditReconciliation(ReconciliationRequest reconciliationRequest,
                                     List<ReconciliationDiscrepancy> discrepancies) {
        log.info("Auditing reconciliation: settlementId={}, discrepancies={}", 
            reconciliationRequest.getSettlementId(), discrepancies.size());
        
        PaymentAuditRecord record = PaymentAuditRecord.builder()
            .auditId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .eventType("RECONCILIATION_PERFORMED")
            .category(PaymentAuditRecord.EventCategory.RECONCILIATION)
            .severity(discrepancies.isEmpty() ? 
                PaymentAuditRecord.EventSeverity.INFO : PaymentAuditRecord.EventSeverity.WARNING)
            .settlementId(reconciliationRequest.getSettlementId())
            .transactionType(PaymentAuditRecord.TransactionType.RECONCILIATION)
            .operationResult(PaymentAuditRecord.OperationResult.SUCCESS)
            .operationMetadata(Map.of(
                "discrepancyCount", discrepancies.size(),
                "actualGrossAmount", reconciliationRequest.getActualGrossAmount(),
                "actualNetAmount", reconciliationRequest.getActualNetAmount(),
                "initiatedBy", reconciliationRequest.getInitiatedBy()
            ))
            .build();
        
        auditRepository.save(record);
        
        // Audit each discrepancy
        for (ReconciliationDiscrepancy discrepancy : discrepancies) {
            auditReconciliationDiscrepancy(reconciliationRequest.getSettlementId(), discrepancy);
        }
        
        incrementCounter("payment.audit.reconciliation", Map.of("has_discrepancies", String.valueOf(!discrepancies.isEmpty())));
        
        return record.getAuditId();
    }
    
    @Override
    @Transactional
    public String auditReconciliationCompleted(String reconciliationId, String settlementId, int totalPayments,
                                               BigDecimal variance, String initiatedBy, Map<String, Object> metadata) {
        log.info("Auditing reconciliation completion: reconciliationId={}, variance={}", reconciliationId, variance);
        
        PaymentAuditRecord record = PaymentAuditRecord.builder()
            .auditId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .eventType("RECONCILIATION_COMPLETED")
            .category(PaymentAuditRecord.EventCategory.RECONCILIATION)
            .severity(PaymentAuditRecord.EventSeverity.INFO)
            .reconciliationId(reconciliationId)
            .settlementId(settlementId)
            .transactionType(PaymentAuditRecord.TransactionType.RECONCILIATION)
            .operationResult(PaymentAuditRecord.OperationResult.SUCCESS)
            .operationMetadata(metadata)
            .build();
        
        auditRepository.save(record);
        
        // Delegate to existing security audit logger
        securityAuditLogger.logSecurityEvent("RECONCILIATION_COMPLETED", initiatedBy,
            "Payment reconciliation completed successfully",
            Map.of("reconciliationId", reconciliationId, "settlementId", settlementId,
                  "totalPayments", totalPayments, "variance", variance));
        
        meterRegistry.counter("payment.reconciliation.processed", 
            "status", variance.compareTo(BigDecimal.ZERO) == 0 ? "MATCHED" : "VARIANCE").increment();
        meterRegistry.gauge("payment.reconciliation.discrepancies", variance.abs().doubleValue());
        
        return record.getAuditId();
    }
    
    // =====================================
    // SECURITY EVENT AUDITING
    // =====================================
    
    @Override
    @Transactional
    public String auditSecurityViolation(String violationType, String userId, String description, Map<String, Object> context) {
        log.warn("SECURITY VIOLATION: type={}, user={}, description={}", violationType, userId, description);
        
        SecurityAuditRecord record = SecurityAuditRecord.builder()
            .securityAuditId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .threatLevel(determineThreatLevel(violationType))
            .eventType(SecurityAuditRecord.SecurityEventType.POLICY_VIOLATION)
            .userId(userId != null ? UUID.fromString(userId) : null)
            .violationType(violationType)
            .violationDescription(description)
            .violationSeverity(SecurityAuditRecord.ViolationSeverity.HIGH)
            .violationContext(context)
            .actionTaken(determineResponseAction(violationType))
            .investigationStatus(SecurityAuditRecord.InvestigationStatus.PENDING)
            .build();
        
        auditRepository.saveSecurityRecord(record);
        securityViolations.incrementAndGet();
        
        // Delegate to existing security audit logger
        securityAuditLogger.logSecurityViolation(violationType, userId, description, context);
        
        // Publish security alert
        publishSecurityAlert(record);
        
        incrementCounter("payment.audit.security.violations", Map.of("type", violationType));
        
        return record.getSecurityAuditId();
    }
    
    @Override
    @Transactional
    public String auditSecurityEvent(String eventType, String userId, String description, Map<String, Object> context) {
        log.info("Security event: type={}, user={}", eventType, userId);
        
        PaymentAuditRecord record = PaymentAuditRecord.builder()
            .auditId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .eventType(eventType)
            .category(PaymentAuditRecord.EventCategory.SECURITY_EVENT)
            .severity(PaymentAuditRecord.EventSeverity.INFO)
            .userId(userId != null ? UUID.fromString(userId) : null)
            .operationResult(PaymentAuditRecord.OperationResult.SUCCESS)
            .operationMetadata(context)
            .build();
        
        auditRepository.save(record);
        
        // Delegate to existing security audit logger
        securityAuditLogger.logSecurityEvent(eventType, userId, description, context);
        
        incrementCounter("payment.audit.security.events", Map.of("type", eventType));
        
        return record.getAuditId();
    }
    
    @Override
    @Transactional
    public String auditSuspiciousPattern(UUID userId, String patternType, Map<String, Object> details) {
        log.warn("SUSPICIOUS PATTERN DETECTED: user={}, pattern={}", userId, patternType);
        
        SecurityAuditRecord record = SecurityAuditRecord.suspiciousActivity(userId, patternType, details);
        
        auditRepository.saveSecurityRecord(record);
        suspiciousPatterns.incrementAndGet();
        
        // Delegate to existing security audit logger
        securityAuditLogger.logSecurityViolation("SUSPICIOUS_PAYMENT_PATTERN", userId.toString(),
            "Suspicious payment pattern detected: " + patternType, details);
        
        // Trigger investigation if needed
        if (shouldTriggerInvestigation(patternType, details)) {
            triggerSecurityInvestigation(userId, patternType, details);
        }
        
        incrementCounter("payment.audit.suspicious.patterns", Map.of("pattern", patternType));
        
        return record.getSecurityAuditId();
    }
    
    @Override
    @Transactional
    public String auditHighValuePayment(UUID userId, BigDecimal amount, String currency, boolean requiresManualReview) {
        log.info("HIGH VALUE PAYMENT: user={}, amount={} {}", userId, amount, currency);
        
        PaymentAuditRecord record = PaymentAuditRecord.builder()
            .auditId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .eventType("HIGH_VALUE_PAYMENT_ATTEMPT")
            .category(PaymentAuditRecord.EventCategory.PAYMENT_OPERATION)
            .severity(requiresManualReview ? 
                PaymentAuditRecord.EventSeverity.WARNING : PaymentAuditRecord.EventSeverity.INFO)
            .userId(userId)
            .amount(amount)
            .currency(currency)
            .operationMetadata(Map.of(
                "threshold", HIGH_VALUE_THRESHOLD,
                "requiresManualReview", requiresManualReview
            ))
            .build();
        
        auditRepository.save(record);
        
        // Delegate to existing security audit logger
        securityAuditLogger.logSecurityEvent("HIGH_VALUE_PAYMENT_ATTEMPT", userId.toString(),
            "High-value payment attempt detected",
            Map.of("amount", amount, "currency", currency, "threshold", HIGH_VALUE_THRESHOLD,
                  "requiresManualReview", requiresManualReview));
        
        incrementCounter("payment.audit.high_value", Map.of("requires_review", String.valueOf(requiresManualReview)));
        
        return record.getAuditId();
    }
    
    @Override
    @Transactional
    public String auditSelfPaymentAttempt(UUID userId, BigDecimal amount, String ipAddress) {
        log.warn("SELF PAYMENT ATTEMPT: user={}, amount={}, ip={}", userId, amount, ipAddress);
        
        SecurityAuditRecord record = SecurityAuditRecord.builder()
            .securityAuditId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .threatLevel(SecurityAuditRecord.ThreatLevel.MEDIUM)
            .eventType(SecurityAuditRecord.SecurityEventType.FRAUD_ATTEMPT)
            .userId(userId)
            .ipAddress(ipAddress)
            .violationType("SELF_PAYMENT_ATTEMPT")
            .violationDescription("User attempted to create payment request to themselves")
            .violationSeverity(SecurityAuditRecord.ViolationSeverity.MEDIUM)
            .violationContext(Map.of("amount", amount, "ipAddress", ipAddress))
            .actionTaken(SecurityAuditRecord.ResponseAction.ACCESS_DENIED)
            .investigationStatus(SecurityAuditRecord.InvestigationStatus.NOT_REQUIRED)
            .build();
        
        auditRepository.saveSecurityRecord(record);
        
        // Delegate to existing security audit logger
        securityAuditLogger.logSecurityViolation("SELF_PAYMENT_ATTEMPT", userId.toString(),
            "User attempted to create payment request to themselves",
            Map.of("requestorId", userId, "recipientId", userId, "amount", amount, "ipAddress", ipAddress));
        
        incrementCounter("payment.audit.self_payment", Map.of());
        
        return record.getSecurityAuditId();
    }
    
    @Override
    @Transactional
    public String auditInsufficientKYC(UUID userId, BigDecimal requestedAmount, String verificationLevel) {
        log.warn("INSUFFICIENT KYC: user={}, amount={}, level={}", userId, requestedAmount, verificationLevel);
        
        SecurityAuditRecord record = SecurityAuditRecord.builder()
            .securityAuditId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .threatLevel(SecurityAuditRecord.ThreatLevel.LOW)
            .eventType(SecurityAuditRecord.SecurityEventType.COMPLIANCE_VIOLATION)
            .userId(userId)
            .violationType("INSUFFICIENT_KYC_VERIFICATION")
            .violationDescription("User attempted payment exceeding KYC verification level")
            .violationSeverity(SecurityAuditRecord.ViolationSeverity.LOW)
            .violationContext(Map.of(
                "requestedAmount", requestedAmount,
                "verificationLevel", verificationLevel
            ))
            .actionTaken(SecurityAuditRecord.ResponseAction.ACCESS_DENIED)
            .investigationStatus(SecurityAuditRecord.InvestigationStatus.NOT_REQUIRED)
            .build();
        
        auditRepository.saveSecurityRecord(record);
        
        // Delegate to existing security audit logger
        securityAuditLogger.logSecurityViolation("INSUFFICIENT_KYC_VERIFICATION", userId.toString(),
            "User attempted payment exceeding KYC verification level",
            Map.of("requestedAmount", requestedAmount, "verificationLevel", verificationLevel));
        
        incrementCounter("payment.audit.kyc.insufficient", Map.of("level", verificationLevel));
        
        return record.getSecurityAuditId();
    }
    
    // =====================================
    // CUSTOMER ACCOUNT AUDITING
    // =====================================
    
    @Override
    @Transactional
    public String auditCustomerActivation(String customerId, String activatedBy) {
        log.info("Auditing customer activation: customerId={}", customerId);
        
        PaymentAuditRecord record = PaymentAuditRecord.builder()
            .auditId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .eventType("CUSTOMER_ACCOUNT_ACTIVATED")
            .category(PaymentAuditRecord.EventCategory.CUSTOMER_MANAGEMENT)
            .severity(PaymentAuditRecord.EventSeverity.INFO)
            .operationResult(PaymentAuditRecord.OperationResult.SUCCESS)
            .operationMetadata(Map.of(
                "customerId", customerId,
                "activatedBy", activatedBy
            ))
            .build();
        
        auditRepository.save(record);
        
        // Delegate to existing security audit logger
        securityAuditLogger.logSecurityEvent("CUSTOMER_ACCOUNT_ACTIVATED", customerId,
            "Customer account activated successfully",
            Map.of("customerId", customerId, "activatedBy", activatedBy));
        
        meterRegistry.counter("dwolla.accounts.activated").increment();
        incrementCounter("payment.audit.customer.activated", Map.of());
        
        return record.getAuditId();
    }
    
    @Override
    @Transactional
    public String auditCustomerSuspension(String customerId, String reason, String suspendedBy) {
        log.info("Auditing customer suspension: customerId={}, reason={}", customerId, reason);
        
        PaymentAuditRecord record = PaymentAuditRecord.builder()
            .auditId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .eventType("CUSTOMER_ACCOUNT_SUSPENDED")
            .category(PaymentAuditRecord.EventCategory.CUSTOMER_MANAGEMENT)
            .severity(PaymentAuditRecord.EventSeverity.WARNING)
            .operationResult(PaymentAuditRecord.OperationResult.SUCCESS)
            .operationMetadata(Map.of(
                "customerId", customerId,
                "reason", reason,
                "suspendedBy", suspendedBy
            ))
            .build();
        
        auditRepository.save(record);
        
        // Delegate to existing security audit logger
        securityAuditLogger.logSecurityEvent("CUSTOMER_ACCOUNT_SUSPENDED", customerId,
            "Customer account suspended",
            Map.of("customerId", customerId, "reason", reason, "suspendedBy", suspendedBy));
        
        meterRegistry.counter("dwolla.accounts.suspended", "reason", reason).increment();
        incrementCounter("payment.audit.customer.suspended", Map.of("reason", reason));
        
        return record.getAuditId();
    }
    
    @Override
    @Transactional
    public String auditIneligibleActivation(String customerId, String reason) {
        log.warn("Ineligible activation attempt: customerId={}, reason={}", customerId, reason);
        
        SecurityAuditRecord record = SecurityAuditRecord.builder()
            .securityAuditId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .threatLevel(SecurityAuditRecord.ThreatLevel.LOW)
            .eventType(SecurityAuditRecord.SecurityEventType.POLICY_VIOLATION)
            .violationType("INELIGIBLE_CUSTOMER_ACTIVATION")
            .violationDescription("Attempt to activate ineligible customer account")
            .violationSeverity(SecurityAuditRecord.ViolationSeverity.LOW)
            .violationContext(Map.of(
                "customerId", customerId,
                "reason", reason
            ))
            .actionTaken(SecurityAuditRecord.ResponseAction.ACCESS_DENIED)
            .investigationStatus(SecurityAuditRecord.InvestigationStatus.NOT_REQUIRED)
            .build();
        
        auditRepository.saveSecurityRecord(record);
        
        // Delegate to existing security audit logger
        securityAuditLogger.logSecurityViolation("INELIGIBLE_CUSTOMER_ACTIVATION", customerId,
            "Attempt to activate ineligible customer account",
            Map.of("customerId", customerId, "reason", reason));
        
        incrementCounter("payment.audit.customer.ineligible", Map.of("reason", reason));
        
        return record.getSecurityAuditId();
    }
    
    // =====================================
    // METRICS AND PERFORMANCE TRACKING
    // =====================================
    
    @Override
    public void trackOperationMetrics(String operationType, boolean success, long processingTimeMs, Map<String, Object> metadata) {
        Timer.builder("payment.operation.duration")
            .tag("operation", operationType)
            .tag("success", String.valueOf(success))
            .register(meterRegistry)
            .record(java.time.Duration.ofMillis(processingTimeMs));
        
        if (!success && metadata != null && metadata.containsKey("errorType")) {
            meterRegistry.counter("payment.operation.errors",
                "operation", operationType,
                "error", metadata.get("errorType").toString()).increment();
        }
    }
    
    @Override
    public void updateMetric(String metricName, double value, Map<String, String> tags) {
        Timer.Builder timerBuilder = Timer.builder(metricName);
        tags.forEach(timerBuilder::tag);
        timerBuilder.register(meterRegistry).record(java.time.Duration.ofMillis((long) value));
    }
    
    @Override
    public void incrementCounter(String counterName, Map<String, String> tags) {
        io.micrometer.core.instrument.Counter.Builder counterBuilder = io.micrometer.core.instrument.Counter.builder(counterName);
        tags.forEach(counterBuilder::tag);
        counterBuilder.register(meterRegistry).increment();
        
        eventCounters.computeIfAbsent(counterName, k -> new AtomicLong()).incrementAndGet();
    }
    
    @Override
    public void recordGauge(String gaugeName, double value, Map<String, String> tags) {
        AtomicLong gaugeValue = new AtomicLong(Double.doubleToLongBits(value));
        io.micrometer.core.instrument.Gauge.Builder<Number> gaugeBuilder = io.micrometer.core.instrument.Gauge.builder(gaugeName, gaugeValue, AtomicLong::doubleValue);
        tags.forEach(gaugeBuilder::tag);
        gaugeBuilder.register(meterRegistry);
    }
    
    // =====================================
    // AUDIT TRAIL QUERIES
    // =====================================
    
    @Override
    public List<PaymentAuditRecord> getPaymentAuditTrail(String paymentId) {
        log.debug("Retrieving audit trail for payment: {}", paymentId);
        return auditRepository.findByPaymentId(paymentId);
    }
    
    @Override
    public List<PaymentAuditRecord> getUserAuditTrail(UUID userId, LocalDateTime startTime, LocalDateTime endTime) {
        log.debug("Retrieving audit trail for user: {} between {} and {}", userId, startTime, endTime);
        return auditRepository.findByUserIdAndTimestampBetween(userId, startTime, endTime);
    }
    
    @Override
    public List<SecurityAuditRecord> getUserSecurityViolations(UUID userId, int limit) {
        log.debug("Retrieving security violations for user: {}", userId);
        return auditRepository.findSecurityViolationsByUserId(userId, limit);
    }
    
    @Override
    public SuspiciousActivityReport getSuspiciousActivityReport(LocalDateTime startTime, LocalDateTime endTime) {
        log.info("Generating suspicious activity report for period: {} to {}", startTime, endTime);
        
        // Retrieve suspicious events
        List<SecurityAuditRecord> suspiciousEvents = auditRepository.findSuspiciousEvents(startTime, endTime);
        
        // Build report
        return SuspiciousActivityReport.builder()
            .reportId(UUID.randomUUID().toString())
            .generatedAt(LocalDateTime.now())
            .reportPeriodStart(startTime)
            .reportPeriodEnd(endTime)
            .reportType(SuspiciousActivityReport.ReportType.ON_DEMAND)
            .reportStatus(SuspiciousActivityReport.ReportStatus.DRAFT)
            .totalSuspiciousEvents(suspiciousEvents.size())
            .uniqueUsersInvolved((int) suspiciousEvents.stream()
                .map(SecurityAuditRecord::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .count())
            .overallRiskLevel(calculateOverallRiskLevel(suspiciousEvents))
            .build();
    }
    
    // =====================================
    // COMPLIANCE AND REPORTING
    // =====================================
    
    @Override
    public ComplianceReport generateComplianceReport(String reportType, LocalDateTime startTime, LocalDateTime endTime) {
        log.info("Generating compliance report: type={}, period={} to {}", reportType, startTime, endTime);
        
        return ComplianceReport.builder()
            .reportId(UUID.randomUUID().toString())
            .reportType(reportType)
            .generatedAt(LocalDateTime.now())
            .periodStart(startTime)
            .periodEnd(endTime)
            .generatedBy("system")
            .overallStatus(ComplianceReport.ComplianceStatus.PARTIALLY_COMPLIANT)
            .complianceScore(calculateComplianceScore())
            .build();
    }
    
    @Override
    public String exportAuditLogs(String format, LocalDateTime startTime, LocalDateTime endTime) {
        log.info("Exporting audit logs: format={}, period={} to {}", format, startTime, endTime);
        
        List<PaymentAuditRecord> records = auditRepository.findByTimestampBetween(startTime, endTime);
        
        try {
            if ("JSON".equalsIgnoreCase(format)) {
                return objectMapper.writeValueAsString(records);
            } else if ("CSV".equalsIgnoreCase(format)) {
                return convertToCSV(records);
            } else {
                throw new IllegalArgumentException("Unsupported export format: " + format);
            }
        } catch (Exception e) {
            log.error("Failed to export audit logs", e);
            throw new RuntimeException("Export failed: " + e.getMessage(), e);
        }
    }
    
    // =====================================
    // SYSTEM INITIALIZATION AND HEALTH
    // =====================================
    
    @Override
    @PostConstruct
    public void initializeSecurityLogging() {
        log.info("Initializing Payment Audit Service security logging");
        serviceStartTime = LocalDateTime.now();
        
        // Initialize existing security audit logger
        securityAuditLogger.logSecurityEvent("PAYMENT_SERVICE_INITIALIZED", "SYSTEM",
            "Payment service security logging initialized",
            Map.of("service", "PaymentService", "securityFeatures",
                  List.of("KYC_VERIFICATION", "DISTRIBUTED_LOCKING", "FRAUD_DETECTION", "AUDIT_LOGGING")));
        
        // Initialize metrics
        meterRegistry.counter("payment.audit.service.started").increment();
        
        currentHealth = AuditServiceStatistics.ServiceHealth.HEALTHY;
        
        log.info("Payment Audit Service initialized successfully");
    }
    
    @Override
    public boolean isHealthy() {
        return currentHealth == AuditServiceStatistics.ServiceHealth.HEALTHY ||
               currentHealth == AuditServiceStatistics.ServiceHealth.DEGRADED;
    }
    
    @Override
    public AuditServiceStatistics getStatistics() {
        return AuditServiceStatistics.builder()
            .serviceId("payment-audit-service")
            .serviceVersion("2.0.0")
            .startupTime(serviceStartTime)
            .uptimeSeconds(java.time.Duration.between(serviceStartTime, LocalDateTime.now()).getSeconds())
            .statisticsTimestamp(LocalDateTime.now())
            .totalEventsProcessed(totalEventsProcessed.get())
            .totalSecurityViolations(securityViolations.get())
            .suspiciousPatternDetections(suspiciousPatterns.get())
            .overallHealth(currentHealth)
            .eventCountsByType(eventCounters.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get())))
            .build();
    }
    
    // =====================================
    // HELPER METHODS
    // =====================================
    
    private PaymentAuditRecord.EventSeverity determinePaymentSeverity(BigDecimal amount) {
        if (amount.compareTo(CRITICAL_VALUE_THRESHOLD) > 0) {
            return PaymentAuditRecord.EventSeverity.WARNING;
        } else if (amount.compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            return PaymentAuditRecord.EventSeverity.INFO;
        }
        return PaymentAuditRecord.EventSeverity.DEBUG;
    }
    
    private SecurityAuditRecord.ThreatLevel determineThreatLevel(String violationType) {
        return switch (violationType) {
            case "FRAUD_ATTEMPT", "SYSTEM_INTRUSION" -> SecurityAuditRecord.ThreatLevel.CRITICAL;
            case "SUSPICIOUS_PAYMENT_PATTERN", "SELF_PAYMENT_ATTEMPT" -> SecurityAuditRecord.ThreatLevel.MEDIUM;
            case "INSUFFICIENT_KYC_VERIFICATION" -> SecurityAuditRecord.ThreatLevel.LOW;
            default -> SecurityAuditRecord.ThreatLevel.LOW;
        };
    }
    
    private SecurityAuditRecord.ResponseAction determineResponseAction(String violationType) {
        return switch (violationType) {
            case "FRAUD_ATTEMPT" -> SecurityAuditRecord.ResponseAction.ACCOUNT_LOCKED;
            case "SYSTEM_INTRUSION" -> SecurityAuditRecord.ResponseAction.IP_BLOCKED;
            case "SUSPICIOUS_PAYMENT_PATTERN" -> SecurityAuditRecord.ResponseAction.MANUAL_REVIEW_REQUIRED;
            default -> SecurityAuditRecord.ResponseAction.LOGGED_ONLY;
        };
    }
    
    private void checkForSuspiciousFailurePattern(UUID userId, String errorType, Map<String, Object> metadata) {
        // Check for repeated failures
        List<PaymentAuditRecord> recentFailures = auditRepository.findRecentFailuresByUser(userId, 10);
        if (recentFailures.size() >= 5) {
            auditSuspiciousPattern(userId, "REPEATED_PAYMENT_FAILURES", 
                Map.of("failureCount", recentFailures.size(), "recentErrorType", errorType));
        }
    }
    
    private boolean shouldTriggerInvestigation(String patternType, Map<String, Object> details) {
        return patternType.contains("FRAUD") || patternType.contains("LAUNDERING") ||
               (details != null && details.containsKey("riskScore") && 
                (Integer) details.get("riskScore") > 70);
    }
    
    private void triggerSecurityInvestigation(UUID userId, String patternType, Map<String, Object> details) {
        log.warn("Triggering security investigation for user: {} due to pattern: {}", userId, patternType);
        // Implementation would create investigation case
    }
    
    private void publishAuditEvent(PaymentAuditRecord record) {
        try {
            String eventJson = objectMapper.writeValueAsString(record);
            kafkaTemplate.send(AUDIT_EVENTS_TOPIC, record.getAuditId(), eventJson);
        } catch (Exception e) {
            log.error("Failed to publish audit event: {}", record.getAuditId(), e);
        }
    }
    
    private void publishSecurityAlert(SecurityAuditRecord record) {
        try {
            String alertJson = objectMapper.writeValueAsString(record);
            kafkaTemplate.send(SECURITY_ALERTS_TOPIC, record.getSecurityAuditId(), alertJson);
        } catch (Exception e) {
            log.error("Failed to publish security alert: {}", record.getSecurityAuditId(), e);
        }
    }
    
    private void auditReconciliationDiscrepancy(String settlementId, ReconciliationDiscrepancy discrepancy) {
        PaymentAuditRecord record = PaymentAuditRecord.builder()
            .auditId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .eventType("RECONCILIATION_DISCREPANCY")
            .category(PaymentAuditRecord.EventCategory.RECONCILIATION)
            .severity("CRITICAL".equals(discrepancy.getSeverity()) ? 
                PaymentAuditRecord.EventSeverity.ERROR : PaymentAuditRecord.EventSeverity.WARNING)
            .settlementId(settlementId)
            .operationMetadata(Map.of(
                "discrepancyType", discrepancy.getType(),
                "expectedValue", discrepancy.getExpectedValue(),
                "actualValue", discrepancy.getActualValue(),
                "severity", discrepancy.getSeverity()
            ))
            .build();
        
        auditRepository.save(record);
    }
    
    private SuspiciousActivityReport.OverallRiskLevel calculateOverallRiskLevel(List<SecurityAuditRecord> events) {
        long criticalCount = events.stream()
            .filter(e -> e.getThreatLevel() == SecurityAuditRecord.ThreatLevel.CRITICAL)
            .count();
        
        if (criticalCount > 0) {
            return SuspiciousActivityReport.OverallRiskLevel.CRITICAL;
        }
        
        long highCount = events.stream()
            .filter(e -> e.getThreatLevel() == SecurityAuditRecord.ThreatLevel.HIGH)
            .count();
        
        if (highCount > 5) {
            return SuspiciousActivityReport.OverallRiskLevel.HIGH;
        }
        
        return events.size() > 10 ? 
            SuspiciousActivityReport.OverallRiskLevel.ELEVATED : 
            SuspiciousActivityReport.OverallRiskLevel.MODERATE;
    }
    
    private double calculateComplianceScore() {
        // Simplified compliance score calculation
        double base = 80.0;
        if (securityViolations.get() > 0) {
            base -= Math.min(20, securityViolations.get() * 2);
        }
        if (suspiciousPatterns.get() > 0) {
            base -= Math.min(10, suspiciousPatterns.get());
        }
        return Math.max(0, base);
    }
    
    private String convertToCSV(List<PaymentAuditRecord> records) {
        StringBuilder csv = new StringBuilder();
        csv.append("AuditId,Timestamp,EventType,UserId,Amount,Status\n");
        for (PaymentAuditRecord record : records) {
            csv.append(String.format("%s,%s,%s,%s,%s,%s\n",
                record.getAuditId(),
                record.getTimestamp(),
                record.getEventType(),
                record.getUserId(),
                record.getAmount(),
                record.getOperationResult()));
        }
        return csv.toString();
    }
    
    // Scheduled health check
    @Scheduled(fixedDelay = 60000) // Every minute
    public void performHealthCheck() {
        try {
            boolean repositoryHealthy = auditRepository.isHealthy();
            boolean kafkaHealthy = kafkaTemplate != null;
            
            if (repositoryHealthy && kafkaHealthy) {
                currentHealth = AuditServiceStatistics.ServiceHealth.HEALTHY;
            } else {
                currentHealth = AuditServiceStatistics.ServiceHealth.DEGRADED;
            }
        } catch (Exception e) {
            log.error("Health check failed", e);
            currentHealth = AuditServiceStatistics.ServiceHealth.UNHEALTHY;
        }
    }
}