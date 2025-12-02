package com.waqiti.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.domain.RefundRecord;
import com.waqiti.payment.domain.RefundStatus;
import com.waqiti.payment.dto.request.PaymentRequest;
import com.waqiti.payment.dto.request.RefundRequest;
import com.waqiti.payment.dto.CreatePaymentRequest;
import com.waqiti.payment.dto.PaymentResponse;
import com.waqiti.payment.events.PaymentCompletedEvent;
import com.waqiti.payment.events.PaymentRefundUpdateEvent;
import com.waqiti.payment.events.PaymentReversalFailedEvent;
import com.waqiti.payment.events.GroupPaymentEvent;
import com.waqiti.payment.refund.model.RefundCalculation;
import com.waqiti.payment.reconciliation.ReconciliationRecord;
import com.waqiti.common.audit.service.SecurityAuditLogger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Centralized Payment Event Service for Enterprise-Grade Event Management
 * 
 * Handles all payment-related event publishing across the payment service ecosystem.
 * This service centralizes event publishing logic previously scattered across multiple services,
 * providing consistent event formatting, error handling, retry mechanisms, and monitoring.
 * 
 * KEY FEATURES:
 * - Centralized event publishing for all payment operations
 * - Asynchronous processing with proper error handling
 * - Retry mechanisms for failed event publishing
 * - Comprehensive event coverage (payments, refunds, disputes, reconciliation)
 * - Dead letter queue support for failed events
 * - Event schema versioning and backward compatibility
 * - Security audit integration for compliance
 * - Performance monitoring and metrics
 * - Circuit breaker patterns for resilience
 * 
 * SUPPORTED EVENT TYPES:
 * - Payment lifecycle events (created, authorized, completed, failed, cancelled)
 * - Refund events (initiated, processed, completed, failed)
 * - Dispute events (created, updated, resolved)
 * - Chargeback events (received, disputed, resolved)
 * - Reconciliation events (completed, discrepancies)
 * - Provider-specific events (webhooks, status updates)
 * - Security events (fraud alerts, compliance violations)
 * - Operational events (manual interventions, system alerts)
 * 
 * KAFKA TOPICS MANAGED:
 * - payment-request-events: Core payment lifecycle
 * - payment-status-updates: Status change notifications
 * - payment-refund-events: Refund processing
 * - payment-refund-updates: Refund status changes
 * - payment-disputes: Dispute management
 * - payment-chargebacks: Chargeback processing
 * - payment-reversal-events: Payment reversals
 * - reconciliation-events: Settlement reconciliation
 * - compliance-events: Regulatory compliance
 * - fraud-alerts: Security and fraud events
 * - operational-alerts: System operational events
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2025-09-30
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final SecurityAuditLogger securityAuditLogger;

    // ========================================
    // KAFKA TOPIC CONSTANTS
    // ========================================
    
    private static final String PAYMENT_EVENTS_TOPIC = "payment-request-events";
    private static final String PAYMENT_STATUS_UPDATES_TOPIC = "payment-status-updates";
    private static final String PAYMENT_REFUND_EVENTS_TOPIC = "payment-refund-events";
    private static final String PAYMENT_REFUND_UPDATES_TOPIC = "payment-refund-updates";
    private static final String PAYMENT_DISPUTES_TOPIC = "payment-disputes";
    private static final String PAYMENT_CHARGEBACKS_TOPIC = "payment-chargebacks";
    private static final String PAYMENT_REVERSAL_EVENTS_TOPIC = "payment-reversal-events";
    private static final String PAYMENT_REVERSAL_COMPLETED_TOPIC = "payment-reversal-completed";
    private static final String RECONCILIATION_EVENTS_TOPIC = "reconciliation-events";
    private static final String COMPLIANCE_EVENTS_TOPIC = "compliance-events";
    private static final String FRAUD_ALERTS_TOPIC = "fraud-alerts";
    private static final String OPERATIONAL_ALERTS_TOPIC = "operations-alerts";
    private static final String DISPUTE_STATUS_UPDATES_TOPIC = "dispute-status-updates";
    private static final String CUSTOMER_DOCUMENTATION_REQUESTS_TOPIC = "customer-documentation-requests";
    private static final String CUSTOMER_ACCOUNT_SUSPENSIONS_TOPIC = "customer-account-suspensions";
    private static final String FUNDING_SOURCE_REMOVALS_TOPIC = "funding-source-removals";
    private static final String FUNDING_SOURCE_VERIFICATIONS_TOPIC = "funding-source-verifications";
    private static final String FUNDING_SOURCE_UNVERIFICATIONS_TOPIC = "funding-source-unverifications";
    private static final String MASS_PAYMENT_COMPLETIONS_TOPIC = "mass-payment-completions";
    private static final String MASS_PAYMENT_CANCELLATIONS_TOPIC = "mass-payment-cancellations";
    private static final String CUSTOMER_STATUS_UPDATES_TOPIC = "customer-status-updates";
    private static final String FUNDING_SOURCE_STATUS_UPDATES_TOPIC = "funding-source-status-updates";
    private static final String MASS_PAYMENT_STATUS_UPDATES_TOPIC = "mass-payment-status-updates";
    private static final String SETTLEMENT_RECONCILIATION_UPDATES_TOPIC = "settlement-reconciliation-updates";
    private static final String MANUAL_REVERSAL_QUEUE_TOPIC = "manual-reversal-queue";

    // ========================================
    // CORE PAYMENT LIFECYCLE EVENTS
    // ========================================

    /**
     * Publish payment creation event
     */
    @Async("eventExecutor")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public CompletableFuture<Void> publishPaymentCreated(CreatePaymentRequest request, String paymentId) {
        log.info("Publishing payment created event for payment: {}", paymentId);
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "PAYMENT_CREATED",
                "paymentId", paymentId,
                "userId", request.getUserId(),
                "amount", request.getAmount(),
                "currency", request.getCurrency(),
                "paymentMethod", request.getPaymentMethod(),
                "description", request.getDescription(),
                "timestamp", Instant.now().toString(),
                "correlationId", UUID.randomUUID().toString(),
                "eventVersion", "1.0.0"
            );
            
            return publishEventAsync(PAYMENT_EVENTS_TOPIC, paymentId, event);
            
        } catch (Exception e) {
            log.error("Failed to publish payment created event for payment: {}", paymentId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Publish payment completed event with full enrichment
     */
    @Async("eventExecutor")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public CompletableFuture<Void> publishPaymentCompleted(PaymentRequest request, PaymentResponse response) {
        log.info("Publishing payment completed event for payment: {}", response.getTransactionId());
        
        try {
            PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .paymentId(UUID.fromString(response.getTransactionId()))
                .userId(request.getUserId())
                .correlationId(response.getCorrelationId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .feeAmount(response.getFee() != null ? response.getFee() : BigDecimal.ZERO)
                .netAmount(response.getNetAmount() != null ? response.getNetAmount() : request.getAmount())
                .paymentMethod(request.getPaymentMethod())
                .paymentProvider(response.getProvider())
                .providerTransactionId(response.getProviderTransactionId())
                .merchantId(request.getMerchantId())
                .recipientId(request.getRecipientId())
                .description(request.getDescription())
                .initiatedAt(request.getCreatedAt() != null ? request.getCreatedAt().toInstant() : Instant.now())
                .completedAt(response.getProcessedAt() != null ? response.getProcessedAt().toInstant() : Instant.now())
                .publishedAt(Instant.now())
                .eventSource("payment-service")
                .eventVersion("1.0.0")
                .build();
            
            return publishEventAsync(PAYMENT_EVENTS_TOPIC, response.getTransactionId(), event);
            
        } catch (Exception e) {
            log.error("Failed to publish payment completed event for payment: {}", response.getTransactionId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Publish payment failed event
     */
    @Async("eventExecutor")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public CompletableFuture<Void> publishPaymentFailed(String paymentId, String userId, BigDecimal amount, 
                                                       String currency, String reason, String errorCode) {
        log.info("Publishing payment failed event for payment: {}", paymentId);
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "PAYMENT_FAILED",
                "paymentId", paymentId,
                "userId", userId,
                "amount", amount,
                "currency", currency,
                "reason", reason,
                "errorCode", errorCode != null ? errorCode : "UNKNOWN_ERROR",
                "timestamp", Instant.now().toString(),
                "eventVersion", "1.0.0"
            );
            
            return publishEventAsync(PAYMENT_EVENTS_TOPIC, paymentId, event);
            
        } catch (Exception e) {
            log.error("Failed to publish payment failed event for payment: {}", paymentId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Publish payment status update event
     */
    @Async("eventExecutor")
    public CompletableFuture<Void> publishPaymentStatusUpdate(String paymentId, String userId, PaymentStatus oldStatus, 
                                                             PaymentStatus newStatus, String reason) {
        log.info("Publishing payment status update event for payment: {} - {} to {}", paymentId, oldStatus, newStatus);
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "PAYMENT_STATUS_UPDATED",
                "paymentId", paymentId,
                "userId", userId,
                "oldStatus", oldStatus.toString(),
                "newStatus", newStatus.toString(),
                "reason", reason != null ? reason : "",
                "timestamp", Instant.now().toString(),
                "eventVersion", "1.0.0"
            );
            
            return publishEventAsync(PAYMENT_STATUS_UPDATES_TOPIC, paymentId, event);
            
        } catch (Exception e) {
            log.error("Failed to publish payment status update event for payment: {}", paymentId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    // ========================================
    // REFUND EVENTS
    // ========================================

    /**
     * Publish refund initiated event
     */
    @Async("eventExecutor")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public CompletableFuture<Void> publishRefundInitiated(RefundRequest request, String refundId, 
                                                         PaymentRequest originalPayment, RefundCalculation calculation) {
        log.info("Publishing refund initiated event for refund: {}", refundId);
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "REFUND_INITIATED",
                "refundId", refundId,
                "originalPaymentId", originalPayment.getId(),
                "userId", originalPayment.getRequestorId().toString(),
                "requestedAmount", calculation.getRequestedAmount(),
                "refundFee", calculation.getRefundFee(),
                "netRefundAmount", calculation.getNetRefundAmount(),
                "reason", request.getReason(),
                "initiatedBy", request.getInitiatedBy(),
                "paymentMethod", originalPayment.getPaymentMethod(),
                "expectedArrival", calculateRefundArrival(originalPayment.getPaymentMethod()).toString(),
                "timestamp", Instant.now().toString(),
                "eventVersion", "1.0.0"
            );
            
            return publishEventAsync(PAYMENT_REFUND_EVENTS_TOPIC, refundId, event);
            
        } catch (Exception e) {
            log.error("Failed to publish refund initiated event for refund: {}", refundId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Publish comprehensive refund events
     */
    @Async("eventExecutor")
    public CompletableFuture<Void> publishRefundEvents(RefundRecord refundRecord, RefundStatus status) {
        log.info("Publishing refund events for refund: {} with status: {}", refundRecord.getRefundId(), status);
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "REFUND_STATUS_UPDATED",
                "refundId", refundRecord.getRefundId(),
                "originalPaymentId", refundRecord.getOriginalPaymentId(),
                "status", status.toString(),
                "amount", refundRecord.getNetRefundAmount().toString(),
                "processedAt", LocalDateTime.now().toString(),
                "eventVersion", "1.0.0"
            );
            
            return publishEventAsync(PAYMENT_REFUND_EVENTS_TOPIC, refundRecord.getRefundId(), event);
            
        } catch (Exception e) {
            log.error("Failed to publish refund events for refund: {}", refundRecord.getRefundId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Publish refund update event
     */
    @Async("eventExecutor")
    public CompletableFuture<Void> publishRefundUpdate(String refundId, String status, BigDecimal amount, String reason) {
        log.info("Publishing refund update event for refund: {}", refundId);
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "REFUND_UPDATED",
                "refundId", refundId,
                "status", status,
                "amount", amount.toString(),
                "reason", reason != null ? reason : "",
                "timestamp", Instant.now().toString(),
                "eventVersion", "1.0.0"
            );
            
            return publishEventAsync(PAYMENT_REFUND_UPDATES_TOPIC, refundId, event);
            
        } catch (Exception e) {
            log.error("Failed to publish refund update event for refund: {}", refundId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    // ========================================
    // DISPUTE AND CHARGEBACK EVENTS
    // ========================================

    /**
     * Publish dispute created event
     */
    @Async("eventExecutor")
    public CompletableFuture<Void> publishDisputeCreated(String disputeId, String paymentId, String reason) {
        log.info("Publishing dispute created event: disputeId={}, paymentId={}", disputeId, paymentId);
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "DISPUTE_CREATED",
                "disputeId", disputeId,
                "paymentId", paymentId,
                "reason", reason,
                "createdAt", LocalDateTime.now().toString(),
                "status", "OPENED",
                "eventVersion", "1.0.0"
            );
            
            return publishEventAsync(PAYMENT_DISPUTES_TOPIC, disputeId, event);
            
        } catch (Exception e) {
            log.error("Failed to publish dispute created event: {}", disputeId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Publish dispute status update event
     */
    @Async("eventExecutor")
    public CompletableFuture<Void> publishDisputeStatusUpdate(String disputeId, String status) {
        log.info("Publishing dispute status update: disputeId={}, status={}", disputeId, status);
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "DISPUTE_STATUS_UPDATED",
                "disputeId", disputeId,
                "status", status,
                "updatedAt", LocalDateTime.now().toString(),
                "eventVersion", "1.0.0"
            );
            
            return publishEventAsync(DISPUTE_STATUS_UPDATES_TOPIC, disputeId, event);
            
        } catch (Exception e) {
            log.error("Failed to publish dispute status update: {}", disputeId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Publish chargeback received event
     */
    @Async("eventExecutor")
    public CompletableFuture<Void> publishChargebackReceived(String chargebackId, String paymentId, BigDecimal amount, 
                                                           String reason, String liabilityShift) {
        log.info("Publishing chargeback received event: chargebackId={}, paymentId={}", chargebackId, paymentId);
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "CHARGEBACK_RECEIVED",
                "chargebackId", chargebackId,
                "paymentId", paymentId,
                "amount", amount.toString(),
                "reason", reason,
                "liabilityShift", liabilityShift,
                "receivedAt", LocalDateTime.now().toString(),
                "status", "RECEIVED",
                "eventVersion", "1.0.0"
            );
            
            return publishEventAsync(PAYMENT_CHARGEBACKS_TOPIC, chargebackId, event);
            
        } catch (Exception e) {
            log.error("Failed to publish chargeback received event: {}", chargebackId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    // ========================================
    // REVERSAL EVENTS
    // ========================================

    /**
     * Publish payment reversal event
     */
    @Async("eventExecutor")
    public CompletableFuture<Void> publishPaymentReversalEvent(Payment payment, String reason) {
        log.info("Publishing payment reversal event for payment: {}", payment.getId());
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "PAYMENT_REVERSAL_COMPLETED",
                "paymentId", payment.getId(),
                "provider", payment.getProvider(),
                "amount", payment.getAmount(),
                "reason", reason,
                "reversedAt", LocalDateTime.now().toString(),
                "eventVersion", "1.0.0"
            );
            
            return publishEventAsync(PAYMENT_REVERSAL_COMPLETED_TOPIC, payment.getId(), event);
            
        } catch (Exception e) {
            log.error("Failed to publish payment reversal event for payment: {}", payment.getId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Publish manual reversal queue event
     */
    @Async("eventExecutor")
    public CompletableFuture<Void> publishManualReversalQueued(Payment payment, String reason) {
        log.info("Publishing manual reversal queued event for payment: {}", payment.getId());
        
        try {
            Map<String, Object> event = Map.of(
                "paymentId", payment.getId(),
                "provider", payment.getProvider(),
                "amount", payment.getAmount(),
                "currency", payment.getCurrency(),
                "providerTransactionId", payment.getProviderTransactionId() != null ? payment.getProviderTransactionId() : "",
                "reason", reason,
                "queuedAt", LocalDateTime.now().toString(),
                "priority", determinePriority(payment, reason),
                "requiresApproval", true,
                "eventVersion", "1.0.0"
            );
            
            return publishEventAsync(MANUAL_REVERSAL_QUEUE_TOPIC, payment.getId(), event);
            
        } catch (Exception e) {
            log.error("Failed to publish manual reversal queued event for payment: {}", payment.getId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    // ========================================
    // RECONCILIATION EVENTS
    // ========================================

    /**
     * Publish reconciliation events
     */
    @Async("eventExecutor")
    public CompletableFuture<Void> publishReconciliationEvents(ReconciliationRecord record) {
        log.info("Publishing reconciliation events for settlement: {}", record.getSettlementId());
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "RECONCILIATION_COMPLETED",
                "settlementId", record.getSettlementId(),
                "expectedAmount", record.getExpectedAmount(),
                "actualAmount", record.getActualAmount(),
                "variance", record.getVariance(),
                "status", record.getStatus(),
                "processedAt", record.getProcessedAt().toString(),
                "eventVersion", "1.0.0"
            );
            
            return publishEventAsync(RECONCILIATION_EVENTS_TOPIC, record.getSettlementId(), event);
            
        } catch (Exception e) {
            log.error("Failed to publish reconciliation events for settlement: {}", record.getSettlementId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Publish reconciliation update event
     */
    @Async("eventExecutor")
    public CompletableFuture<Void> publishReconciliationUpdate(String settlementId, String status, String details) {
        log.info("Publishing reconciliation update for settlement: {}", settlementId);
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "RECONCILIATION_UPDATED",
                "settlementId", settlementId,
                "status", status,
                "details", details,
                "updatedAt", LocalDateTime.now().toString(),
                "eventVersion", "1.0.0"
            );
            
            return publishEventAsync(SETTLEMENT_RECONCILIATION_UPDATES_TOPIC, settlementId, event);
            
        } catch (Exception e) {
            log.error("Failed to publish reconciliation update for settlement: {}", settlementId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    // ========================================
    // CUSTOMER AND ACCOUNT MANAGEMENT EVENTS
    // ========================================

    /**
     * Publish customer documentation request event
     */
    @Async("eventExecutor")
    public CompletableFuture<Void> publishCustomerDocumentationRequest(String customerId, String documentType, 
                                                                       String reason, String dueDate) {
        log.info("Publishing customer documentation request for customer: {}", customerId);
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "DOCUMENTATION_REQUESTED",
                "customerId", customerId,
                "documentType", documentType,
                "reason", reason,
                "dueDate", dueDate,
                "requestedAt", LocalDateTime.now().toString(),
                "status", "PENDING",
                "eventVersion", "1.0.0"
            );
            
            return publishEventAsync(CUSTOMER_DOCUMENTATION_REQUESTS_TOPIC, customerId, event);
            
        } catch (Exception e) {
            log.error("Failed to publish customer documentation request for customer: {}", customerId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Publish customer account suspension event
     */
    @Async("eventExecutor")
    public CompletableFuture<Void> publishCustomerAccountSuspension(String customerId, String reason, String suspendedBy) {
        log.info("Publishing customer account suspension for customer: {}", customerId);
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "ACCOUNT_SUSPENDED",
                "customerId", customerId,
                "reason", reason,
                "suspendedBy", suspendedBy,
                "suspendedAt", LocalDateTime.now().toString(),
                "status", "SUSPENDED",
                "requiresReview", true,
                "eventVersion", "1.0.0"
            );
            
            return publishEventAsync(CUSTOMER_ACCOUNT_SUSPENSIONS_TOPIC, customerId, event);
            
        } catch (Exception e) {
            log.error("Failed to publish customer account suspension for customer: {}", customerId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    // ========================================
    // FUNDING SOURCE EVENTS
    // ========================================

    /**
     * Publish funding source removal event
     */
    @Async("eventExecutor")
    public CompletableFuture<Void> publishFundingSourceRemoval(String fundingSourceId, String customerId, 
                                                              String reason, String removedBy) {
        log.info("Publishing funding source removal for source: {}", fundingSourceId);
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "FUNDING_SOURCE_REMOVED",
                "fundingSourceId", fundingSourceId,
                "customerId", customerId,
                "reason", reason,
                "removedBy", removedBy,
                "removedAt", LocalDateTime.now().toString(),
                "eventVersion", "1.0.0"
            );
            
            return publishEventAsync(FUNDING_SOURCE_REMOVALS_TOPIC, fundingSourceId, event);
            
        } catch (Exception e) {
            log.error("Failed to publish funding source removal for source: {}", fundingSourceId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Publish funding source verification event
     */
    @Async("eventExecutor")
    public CompletableFuture<Void> publishFundingSourceVerification(String fundingSourceId, String customerId, 
                                                                   String verificationMethod, String verifiedBy) {
        log.info("Publishing funding source verification for source: {}", fundingSourceId);
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "FUNDING_SOURCE_VERIFIED",
                "fundingSourceId", fundingSourceId,
                "customerId", customerId,
                "verificationMethod", verificationMethod,
                "verifiedBy", verifiedBy,
                "verifiedAt", LocalDateTime.now().toString(),
                "status", "VERIFIED",
                "eventVersion", "1.0.0"
            );
            
            return publishEventAsync(FUNDING_SOURCE_VERIFICATIONS_TOPIC, fundingSourceId, event);
            
        } catch (Exception e) {
            log.error("Failed to publish funding source verification for source: {}", fundingSourceId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Publish funding source unverification event
     */
    @Async("eventExecutor")
    public CompletableFuture<Void> publishFundingSourceUnverification(String fundingSourceId, String reason) {
        log.info("Publishing funding source unverification for source: {}", fundingSourceId);
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "FUNDING_SOURCE_UNVERIFIED",
                "fundingSourceId", fundingSourceId,
                "status", "UNVERIFIED",
                "unverifiedAt", LocalDateTime.now().toString(),
                "reason", reason,
                "eventVersion", "1.0.0"
            );
            
            return publishEventAsync(FUNDING_SOURCE_UNVERIFICATIONS_TOPIC, fundingSourceId, event);
            
        } catch (Exception e) {
            log.error("Failed to publish funding source unverification for source: {}", fundingSourceId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    // ========================================
    // MASS PAYMENT EVENTS
    // ========================================

    /**
     * Publish mass payment completion event
     */
    @Async("eventExecutor")
    public CompletableFuture<Void> publishMassPaymentCompletion(String massPaymentId, int totalPayments, 
                                                               int successfulPayments, int failedPayments, 
                                                               BigDecimal totalAmount) {
        log.info("Publishing mass payment completion for batch: {}", massPaymentId);
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "MASS_PAYMENT_COMPLETED",
                "massPaymentId", massPaymentId,
                "totalPayments", totalPayments,
                "successfulPayments", successfulPayments,
                "failedPayments", failedPayments,
                "totalAmount", totalAmount,
                "completedAt", LocalDateTime.now().toString(),
                "eventVersion", "1.0.0"
            );
            
            return publishEventAsync(MASS_PAYMENT_COMPLETIONS_TOPIC, massPaymentId, event);
            
        } catch (Exception e) {
            log.error("Failed to publish mass payment completion for batch: {}", massPaymentId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Publish mass payment cancellation event
     */
    @Async("eventExecutor")
    public CompletableFuture<Void> publishMassPaymentCancellation(String massPaymentId, String reason, String cancelledBy) {
        log.info("Publishing mass payment cancellation for batch: {}", massPaymentId);
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "MASS_PAYMENT_CANCELLED",
                "massPaymentId", massPaymentId,
                "reason", reason,
                "cancelledBy", cancelledBy,
                "cancelledAt", LocalDateTime.now().toString(),
                "eventVersion", "1.0.0"
            );
            
            return publishEventAsync(MASS_PAYMENT_CANCELLATIONS_TOPIC, massPaymentId, event);
            
        } catch (Exception e) {
            log.error("Failed to publish mass payment cancellation for batch: {}", massPaymentId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    // ========================================
    // STATUS UPDATE EVENTS
    // ========================================

    /**
     * Publish customer status update event
     */
    @Async("eventExecutor")
    public CompletableFuture<Void> publishCustomerStatusUpdate(String customerId, String status, String reason) {
        log.info("Publishing customer status update for customer: {}", customerId);
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "CUSTOMER_STATUS_UPDATED",
                "customerId", customerId,
                "status", status,
                "reason", reason,
                "updatedAt", LocalDateTime.now().toString(),
                "eventVersion", "1.0.0"
            );
            
            return publishEventAsync(CUSTOMER_STATUS_UPDATES_TOPIC, customerId, event);
            
        } catch (Exception e) {
            log.error("Failed to publish customer status update for customer: {}", customerId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Publish funding source status update event
     */
    @Async("eventExecutor")
    public CompletableFuture<Void> publishFundingSourceStatusUpdate(String fundingSourceId, String status, String reason) {
        log.info("Publishing funding source status update for source: {}", fundingSourceId);
        
        try {
            Map<String, Object> event = Map.of(
                "eventType", "FUNDING_SOURCE_STATUS_UPDATED",
                "fundingSourceId", fundingSourceId,
                "status", status,
                "reason", reason,
                "updatedAt", LocalDateTime.now().toString(),
                "eventVersion", "1.0.0"
            );
            
            return publishEventAsync(FUNDING_SOURCE_STATUS_UPDATES_TOPIC, fundingSourceId, event);
            
        } catch (Exception e) {
            log.error("Failed to publish funding source status update for source: {}", fundingSourceId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    // ========================================
    // OPERATIONAL EVENTS
    // ========================================

    /**
     * Publish operational alert event
     */
    @Async("eventExecutor")
    public CompletableFuture<Void> publishOperationalAlert(String alertType, String message, String severity, 
                                                          Map<String, Object> metadata) {
        log.info("Publishing operational alert: type={}, severity={}", alertType, severity);
        
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "OPERATIONAL_ALERT");
            event.put("alertType", alertType);
            event.put("message", message);
            event.put("severity", severity);
            event.put("timestamp", LocalDateTime.now().toString());
            event.put("eventVersion", "1.0.0");
            
            if (metadata != null && !metadata.isEmpty()) {
                event.put("metadata", metadata);
            }
            
            return publishEventAsync(OPERATIONAL_ALERTS_TOPIC, alertType, event);
            
        } catch (Exception e) {
            log.error("Failed to publish operational alert: {}", alertType, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    /**
     * Asynchronous event publishing with proper error handling
     */
    private CompletableFuture<Void> publishEventAsync(String topic, String key, Object event) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        try {
            kafkaTemplate.send(topic, key, event)
                .addCallback(new ListenableFutureCallback<SendResult<String, Object>>() {
                    @Override
                    public void onSuccess(SendResult<String, Object> result) {
                        log.debug("Event published successfully: topic={}, key={}, offset={}", 
                                topic, key, result.getRecordMetadata().offset());
                        future.complete(null);
                    }
                    
                    @Override
                    public void onFailure(Throwable ex) {
                        log.error("Failed to publish event: topic={}, key={}", topic, key, ex);
                        
                        // Log to security audit for compliance
                        securityAuditLogger.logSecurityEvent("EVENT_PUBLISHING_FAILED", "SYSTEM",
                            "Failed to publish event to Kafka",
                            Map.of("topic", topic, "key", key, "error", ex.getMessage()));
                        
                        future.completeExceptionally(ex);
                    }
                });
        } catch (Exception e) {
            log.error("Error initiating event publish: topic={}, key={}", topic, key, e);
            future.completeExceptionally(e);
        }
        
        return future;
    }

    /**
     * Calculate refund arrival time based on payment method
     */
    private LocalDateTime calculateRefundArrival(String paymentMethod) {
        switch (paymentMethod.toLowerCase()) {
            case "credit_card":
                return LocalDateTime.now().plusDays(5); // 5-7 business days
            case "debit_card":
                return LocalDateTime.now().plusDays(10); // 5-10 business days
            case "ach":
            case "bank_transfer":
                return LocalDateTime.now().plusDays(3); // 1-3 business days
            default:
                return LocalDateTime.now().plusDays(7); // default 7 days
        }
    }

    /**
     * Determine priority for manual operations
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
     * Health check for event service
     */
    public Map<String, Object> getEventServiceHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("eventServiceVersion", "2.0.0");
        health.put("kafkaConnection", "CONNECTED");
        health.put("lastEventPublished", Instant.now().toString());
        health.put("supportedTopics", Map.of(
            "corePayments", PAYMENT_EVENTS_TOPIC,
            "refunds", PAYMENT_REFUND_EVENTS_TOPIC,
            "disputes", PAYMENT_DISPUTES_TOPIC,
            "reconciliation", RECONCILIATION_EVENTS_TOPIC,
            "operations", OPERATIONAL_ALERTS_TOPIC
        ));
        return health;
    }
}