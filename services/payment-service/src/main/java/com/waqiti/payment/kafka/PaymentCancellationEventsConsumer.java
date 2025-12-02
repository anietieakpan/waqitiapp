package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentCancellationEvent;
import com.waqiti.common.events.PaymentStatusUpdatedEvent;
import com.waqiti.common.events.RefundInitiatedEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.domain.CancellationReason;
import com.waqiti.payment.domain.RefundType;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.service.PaymentCancellationService;
import com.waqiti.payment.service.RefundService;
import com.waqiti.payment.service.PaymentHoldService;
import com.waqiti.payment.service.PaymentReversalService;
import com.waqiti.payment.exception.PaymentNotFoundException;
import com.waqiti.payment.exception.CancellationNotAllowedException;
import com.waqiti.payment.metrics.CancellationMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.fraud.FraudService;
import com.waqiti.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.*;

/**
 * CRITICAL Consumer for Payment Cancellation Events
 * 
 * Handles all payment cancellation scenarios including:
 * - Customer-initiated cancellations
 * - Merchant-initiated cancellations
 * - System-initiated cancellations (fraud, compliance)
 * - Automatic refund processing
 * - Hold release and reversal management
 * - Partial cancellations and split refunds
 * - Cross-border cancellation compliance
 * - Dispute prevention through proactive cancellation
 * 
 * This is CRITICAL for customer satisfaction and dispute prevention.
 * Efficient cancellation processing reduces chargebacks by 20-30%
 * and improves customer retention.
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentCancellationEventsConsumer {
    
    private final PaymentRepository paymentRepository;
    private final PaymentCancellationService cancellationService;
    private final RefundService refundService;
    private final PaymentHoldService holdService;
    private final PaymentReversalService reversalService;
    private final CancellationMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SecurityContext securityContext;
    private final FraudService fraudService;
    private final LedgerService ledgerService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UniversalDLQHandler dlqHandler;
    
    // Cancellation time limits
    private static final Duration INSTANT_CANCELLATION_WINDOW = Duration.ofMinutes(15);
    private static final Duration SAME_DAY_CANCELLATION_WINDOW = Duration.ofHours(24);
    private static final Duration STANDARD_CANCELLATION_WINDOW = Duration.ofDays(7);
    
    // Cancellation fee thresholds
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("5000");
    private static final BigDecimal INSTANT_CANCELLATION_FEE = new BigDecimal("0.00");
    private static final BigDecimal STANDARD_CANCELLATION_FEE = new BigDecimal("2.50");
    
    // Refund processing SLAs
    private static final Duration INSTANT_REFUND_SLA = Duration.ofMinutes(5);
    private static final Duration SAME_DAY_REFUND_SLA = Duration.ofHours(2);
    private static final Duration STANDARD_REFUND_SLA = Duration.ofDays(3);
    
    /**
     * Primary handler for payment cancellation events
     * Processes all types of payment cancellations with proper validation
     */
    @KafkaListener(
        topics = "payment-cancellation-events",
        groupId = "payment-cancellation-service-group", 
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 24000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handlePaymentCancellationEvent(
            @Payload PaymentCancellationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("cancel-%s-p%d-o%d", 
            event.getPaymentId(), partition, offset);
        
        log.info("Processing payment cancellation event: paymentId={}, reason={}, initiator={}, correlation={}",
            event.getPaymentId(), event.getCancellationReason(), event.getInitiatedBy(), correlationId);
        
        try {
            // Security and validation
            securityContext.validateFinancialOperation(event.getPaymentId(), "PAYMENT_CANCELLATION");
            validateCancellationEvent(event);
            
            // Check for fraudulent cancellation patterns
            if (fraudService.isSuspiciousCancellationPattern(event)) {
                log.warn("Suspicious cancellation pattern detected: paymentId={}", event.getPaymentId());
                handleSuspiciousCancellation(event, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            // Process based on event type
            switch (event.getEventType()) {
                case CANCELLATION_REQUEST:
                    processCancellationRequest(event, correlationId);
                    break;
                case PARTIAL_CANCELLATION:
                    processPartialCancellation(event, correlationId);
                    break;
                case AUTOMATIC_CANCELLATION:
                    processAutomaticCancellation(event, correlationId);
                    break;
                case CANCELLATION_APPROVED:
                    processCancellationApproved(event, correlationId);
                    break;
                case CANCELLATION_REJECTED:
                    processCancellationRejected(event, correlationId);
                    break;
                case REFUND_PROCESSING:
                    processRefundProcessing(event, correlationId);
                    break;
                case HOLD_RELEASE:
                    processHoldRelease(event, correlationId);
                    break;
                case REVERSAL_PROCESSING:
                    processReversalProcessing(event, correlationId);
                    break;
                default:
                    log.warn("Unknown cancellation event type: {}", event.getEventType());
                    break;
            }
            
            // Audit the cancellation operation
            auditService.logFinancialEvent(
                "CANCELLATION_EVENT_PROCESSED",
                event.getPaymentId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "cancellationReason", event.getCancellationReason(),
                    "initiatedBy", event.getInitiatedBy(),
                    "refundAmount", event.getRefundAmount() != null ? event.getRefundAmount() : BigDecimal.ZERO,
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing cancellation event: topic={}, partition={}, offset={}, error={}",
                topic, partition, offset, e.getMessage(), e);

            handleCancellationEventError(event, e, correlationId);

            dlqHandler.handleFailedMessage(org.apache.kafka.clients.consumer.ConsumerRecord.class.cast(event), e)
                .thenAccept(result -> log.info("Message sent to DLQ: topic={}, offset={}, destination={}, category={}",
                        topic, offset, result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed - MESSAGE MAY BE LOST! " +
                            "topic={}, partition={}, offset={}, error={}",
                            topic, partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Cancellation event processing failed", e);
        }
    }
    
    /**
     * Processes cancellation requests with eligibility validation
     */
    private void processCancellationRequest(PaymentCancellationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        
        log.info("Processing cancellation request: paymentId={}, status={}, amount={}, reason={}",
            payment.getId(), payment.getStatus(), payment.getAmount(), event.getCancellationReason());
        
        // Check if payment is eligible for cancellation
        CancellationEligibility eligibility = checkCancellationEligibility(payment, event);
        
        if (!eligibility.isEligible()) {
            log.warn("Payment not eligible for cancellation: paymentId={}, reason={}",
                payment.getId(), eligibility.getReason());
            
            processCancellationRejected(event.toBuilder()
                .eventType("CANCELLATION_REJECTED")
                .rejectionReason(eligibility.getReason())
                .build(), correlationId);
            return;
        }
        
        // Calculate cancellation fees and refund amount
        CancellationCalculation calculation = calculateCancellationAmounts(payment, event);
        
        // Update payment status to cancellation pending
        payment.setStatus(PaymentStatus.CANCELLATION_PENDING);
        payment.setCancellationRequestedAt(LocalDateTime.now());
        payment.setCancellationReason(event.getCancellationReason());
        payment.setCancellationInitiatedBy(event.getInitiatedBy());
        payment.setEstimatedRefundAmount(calculation.getRefundAmount());
        payment.setCancellationFee(calculation.getCancellationFee());
        paymentRepository.save(payment);
        
        // For instant cancellations (within 15 minutes), auto-approve
        if (eligibility.isInstantCancellation()) {
            log.info("Auto-approving instant cancellation: paymentId={}", payment.getId());
            
            PaymentCancellationEvent approvedEvent = event.toBuilder()
                .eventType("CANCELLATION_APPROVED")
                .refundAmount(calculation.getRefundAmount())
                .cancellationFee(calculation.getCancellationFee())
                .autoApproved(true)
                .build();
            
            kafkaTemplate.send("payment-cancellation-events", approvedEvent);
            
        } else if (requiresManualApproval(payment, event)) {
            // Send for manual approval
            log.info("Cancellation requires manual approval: paymentId={}", payment.getId());
            sendForManualApproval(payment, event, calculation, correlationId);
            
        } else {
            // Auto-approve standard cancellations
            log.info("Auto-approving standard cancellation: paymentId={}", payment.getId());
            
            PaymentCancellationEvent approvedEvent = event.toBuilder()
                .eventType("CANCELLATION_APPROVED")
                .refundAmount(calculation.getRefundAmount())
                .cancellationFee(calculation.getCancellationFee())
                .autoApproved(true)
                .build();
            
            kafkaTemplate.send("payment-cancellation-events", approvedEvent);
        }
        
        // Update metrics
        metricsService.recordCancellationRequest(
            event.getCancellationReason(),
            eligibility.getCancellationType(),
            calculation.getRefundAmount()
        );
        
        // Send customer notification
        notificationService.sendCustomerNotification(
            payment.getCustomerId(),
            "Payment Cancellation Requested",
            String.format("Your payment cancellation request for $%s has been received and is being processed.",
                payment.getAmount()),
            NotificationService.Priority.MEDIUM
        );
    }
    
    /**
     * Checks cancellation eligibility and determines type
     */
    private CancellationEligibility checkCancellationEligibility(Payment payment, 
            PaymentCancellationEvent event) {
        
        // Check payment status
        if (!isCancellableStatus(payment.getStatus())) {
            return CancellationEligibility.notEligible("Payment status does not allow cancellation: " + 
                payment.getStatus());
        }
        
        // Check time windows
        Duration timeSincePayment = Duration.between(payment.getCreatedAt(), LocalDateTime.now());
        
        if (timeSincePayment.compareTo(INSTANT_CANCELLATION_WINDOW) <= 0) {
            return CancellationEligibility.instantCancellation();
        } else if (timeSincePayment.compareTo(SAME_DAY_CANCELLATION_WINDOW) <= 0) {
            return CancellationEligibility.sameDayCancellation();
        } else if (timeSincePayment.compareTo(STANDARD_CANCELLATION_WINDOW) <= 0) {
            return CancellationEligibility.standardCancellation();
        } else {
            return CancellationEligibility.notEligible("Cancellation window expired");
        }
    }
    
    /**
     * Calculates cancellation fees and refund amounts
     */
    private CancellationCalculation calculateCancellationAmounts(Payment payment, 
            PaymentCancellationEvent event) {
        
        BigDecimal originalAmount = payment.getAmount();
        BigDecimal refundAmount = originalAmount;
        BigDecimal cancellationFee = BigDecimal.ZERO;
        
        // Determine cancellation fee based on timing and amount
        Duration timeSincePayment = Duration.between(payment.getCreatedAt(), LocalDateTime.now());
        
        if (timeSincePayment.compareTo(INSTANT_CANCELLATION_WINDOW) <= 0) {
            // No fee for instant cancellations
            cancellationFee = INSTANT_CANCELLATION_FEE;
        } else if (payment.getAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            // Reduced fee for high-value payments
            cancellationFee = STANDARD_CANCELLATION_FEE.multiply(new BigDecimal("0.5"));
        } else {
            // Standard cancellation fee
            cancellationFee = STANDARD_CANCELLATION_FEE;
        }
        
        // Calculate net refund amount
        refundAmount = originalAmount.subtract(cancellationFee);
        
        // Handle partial cancellations
        if (event.getRefundAmount() != null && 
            event.getRefundAmount().compareTo(BigDecimal.ZERO) > 0 &&
            event.getRefundAmount().compareTo(originalAmount) < 0) {
            
            refundAmount = event.getRefundAmount();
            // Adjust cancellation fee proportionally for partial cancellations
            BigDecimal feeRatio = refundAmount.divide(originalAmount, 4, RoundingMode.HALF_UP);
            cancellationFee = cancellationFee.multiply(feeRatio);
        }
        
        return CancellationCalculation.builder()
            .originalAmount(originalAmount)
            .refundAmount(refundAmount)
            .cancellationFee(cancellationFee)
            .netRefundAmount(refundAmount.subtract(cancellationFee))
            .build();
    }
    
    /**
     * Processes approved cancellations
     */
    private void processCancellationApproved(PaymentCancellationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        
        log.info("Processing approved cancellation: paymentId={}, refundAmount={}, fee={}",
            payment.getId(), event.getRefundAmount(), event.getCancellationFee());
        
        // Update payment status
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setCancelledAt(LocalDateTime.now());
        payment.setApprovedBy(event.getApprovedBy());
        payment.setAutoApproved(event.isAutoApproved());
        
        if (event.getRefundAmount() != null) {
            payment.setActualRefundAmount(event.getRefundAmount());
        }
        if (event.getCancellationFee() != null) {
            payment.setActualCancellationFee(event.getCancellationFee());
        }
        
        paymentRepository.save(payment);
        
        // Process refund based on payment status
        if (payment.getStatus() == PaymentStatus.COMPLETED || 
            payment.getStatus() == PaymentStatus.SETTLED) {
            
            // Payment already completed - initiate refund
            initiateRefund(payment, event, correlationId);
            
        } else if (payment.getStatus() == PaymentStatus.AUTHORIZED || 
                   payment.getStatus() == PaymentStatus.PROCESSING) {
            
            // Payment not yet completed - reverse/void authorization
            initiateReversal(payment, event, correlationId);
            
        } else if (payment.getStatus() == PaymentStatus.HELD || 
                   payment.getStatus() == PaymentStatus.ON_HOLD) {
            
            // Release held funds
            releaseHold(payment, event, correlationId);
        }
        
        // Update ledger entries
        ledgerService.recordCancellation(
            payment.getId(),
            payment.getActualRefundAmount(),
            payment.getActualCancellationFee(),
            correlationId
        );
        
        // Send notifications
        sendCancellationNotifications(payment, event, correlationId);
        
        // Update metrics
        metricsService.recordCancellationApproved(
            event.getCancellationReason(),
            payment.getActualRefundAmount(),
            event.isAutoApproved()
        );
        
        // Publish status update
        publishPaymentStatusUpdate(payment, "CANCELLATION_APPROVED", correlationId);
    }
    
    /**
     * Initiates refund processing for completed payments
     */
    private void initiateRefund(Payment payment, PaymentCancellationEvent event, String correlationId) {
        log.info("Initiating refund for cancelled payment: paymentId={}, amount={}",
            payment.getId(), payment.getActualRefundAmount());
        
        try {
            String refundId = refundService.initiateRefund(
                payment.getId(),
                payment.getActualRefundAmount(),
                "Payment cancellation",
                correlationId
            );
            
            payment.setRefundId(refundId);
            payment.setRefundInitiatedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            
            // Publish refund initiated event
            RefundInitiatedEvent refundEvent = RefundInitiatedEvent.builder()
                .paymentId(payment.getId())
                .refundId(refundId)
                .refundAmount(payment.getActualRefundAmount())
                .refundType(RefundType.CANCELLATION)
                .gatewayId(payment.getGatewayId())
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .build();
            
            kafkaTemplate.send("refund-initiated-events", refundEvent);
            
            log.info("Refund initiated: paymentId={}, refundId={}, amount={}",
                payment.getId(), refundId, payment.getActualRefundAmount());
            
        } catch (Exception e) {
            log.error("Failed to initiate refund: paymentId={}, error={}",
                payment.getId(), e.getMessage(), e);
            
            // Update payment with refund failure
            payment.setRefundStatus("FAILED");
            payment.setRefundFailureReason(e.getMessage());
            paymentRepository.save(payment);
            
            // Send alert
            notificationService.sendOperationalAlert(
                "Refund Initiation Failed",
                String.format("Failed to initiate refund for cancelled payment %s: %s",
                    payment.getId(), e.getMessage()),
                NotificationService.Priority.HIGH
            );
        }
    }
    
    /**
     * Initiates reversal for authorized but not completed payments
     */
    private void initiateReversal(Payment payment, PaymentCancellationEvent event, String correlationId) {
        log.info("Initiating reversal for cancelled payment: paymentId={}, amount={}",
            payment.getId(), payment.getAmount());
        
        try {
            String reversalId = reversalService.initiateReversal(
                payment.getId(),
                payment.getAmount(),
                "Payment cancellation",
                correlationId
            );
            
            payment.setReversalId(reversalId);
            payment.setReversalInitiatedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            
            log.info("Reversal initiated: paymentId={}, reversalId={}",
                payment.getId(), reversalId);
            
        } catch (Exception e) {
            log.error("Failed to initiate reversal: paymentId={}, error={}",
                payment.getId(), e.getMessage(), e);
            
            payment.setReversalStatus("FAILED");
            payment.setReversalFailureReason(e.getMessage());
            paymentRepository.save(payment);
        }
    }
    
    /**
     * Releases held funds for cancelled payments
     */
    private void releaseHold(Payment payment, PaymentCancellationEvent event, String correlationId) {
        log.info("Releasing hold for cancelled payment: paymentId={}, amount={}",
            payment.getId(), payment.getAmount());
        
        try {
            holdService.releaseHold(payment.getId(), "Payment cancellation", correlationId);
            
            payment.setHoldReleasedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            
            log.info("Hold released: paymentId={}", payment.getId());
            
        } catch (Exception e) {
            log.error("Failed to release hold: paymentId={}, error={}",
                payment.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Processes partial cancellations
     */
    private void processPartialCancellation(PaymentCancellationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        
        log.info("Processing partial cancellation: paymentId={}, originalAmount={}, refundAmount={}",
            payment.getId(), payment.getAmount(), event.getRefundAmount());
        
        // Validate partial cancellation amount
        if (event.getRefundAmount() == null || 
            event.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0 ||
            event.getRefundAmount().compareTo(payment.getAmount()) >= 0) {
            
            log.error("Invalid partial cancellation amount: paymentId={}, amount={}",
                payment.getId(), event.getRefundAmount());
            return;
        }
        
        // Calculate remaining amount
        BigDecimal remainingAmount = payment.getAmount().subtract(event.getRefundAmount());
        
        // Update payment with partial cancellation
        payment.setPartialCancellation(true);
        payment.setPartialRefundAmount(event.getRefundAmount());
        payment.setRemainingAmount(remainingAmount);
        payment.setPartialCancellationAt(LocalDateTime.now());
        paymentRepository.save(payment);
        
        // Process refund for cancelled portion
        initiateRefund(payment, event, correlationId);
        
        log.info("Partial cancellation processed: paymentId={}, refunded={}, remaining={}",
            payment.getId(), event.getRefundAmount(), remainingAmount);
    }
    
    /**
     * Additional utility methods
     */
    private void processAutomaticCancellation(PaymentCancellationEvent event, String correlationId) {
        log.info("Processing automatic cancellation: paymentId={}, reason={}",
            event.getPaymentId(), event.getCancellationReason());
        
        // Auto-approve system-initiated cancellations
        PaymentCancellationEvent approvedEvent = event.toBuilder()
            .eventType("CANCELLATION_APPROVED")
            .autoApproved(true)
            .approvedBy("SYSTEM")
            .build();
        
        kafkaTemplate.send("payment-cancellation-events", approvedEvent);
    }
    
    private void processCancellationRejected(PaymentCancellationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        
        log.info("Processing cancellation rejection: paymentId={}, reason={}",
            payment.getId(), event.getRejectionReason());
        
        payment.setStatus(PaymentStatus.PROCESSING); // Restore previous status
        payment.setCancellationRejectedAt(LocalDateTime.now());
        payment.setCancellationRejectionReason(event.getRejectionReason());
        paymentRepository.save(payment);
        
        // Send customer notification
        notificationService.sendCustomerNotification(
            payment.getCustomerId(),
            "Payment Cancellation Rejected",
            String.format("Your cancellation request for payment %s has been rejected: %s",
                payment.getId(), event.getRejectionReason()),
            NotificationService.Priority.MEDIUM
        );
        
        metricsService.recordCancellationRejected(event.getCancellationReason());
    }
    
    private void processRefundProcessing(PaymentCancellationEvent event, String correlationId) {
        // Handle refund processing status updates
        Payment payment = getPaymentById(event.getPaymentId());
        payment.setRefundStatus(event.getRefundStatus());
        paymentRepository.save(payment);
    }
    
    private void processHoldRelease(PaymentCancellationEvent event, String correlationId) {
        // Handle hold release events
        releaseHold(getPaymentById(event.getPaymentId()), event, correlationId);
    }
    
    private void processReversalProcessing(PaymentCancellationEvent event, String correlationId) {
        // Handle reversal processing status updates
        Payment payment = getPaymentById(event.getPaymentId());
        payment.setReversalStatus(event.getReversalStatus());
        paymentRepository.save(payment);
    }
    
    /**
     * Validation and utility methods
     */
    private void validateCancellationEvent(PaymentCancellationEvent event) {
        if (event.getPaymentId() == null || event.getPaymentId().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment ID is required");
        }
        
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }
        
        if (event.getCancellationReason() == null || event.getCancellationReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Cancellation reason is required");
        }
        
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required");
        }
    }
    
    private Payment getPaymentById(String paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(
                "Payment not found: " + paymentId));
    }
    
    private boolean isCancellableStatus(PaymentStatus status) {
        return status == PaymentStatus.PENDING ||
               status == PaymentStatus.AUTHORIZED ||
               status == PaymentStatus.PROCESSING ||
               status == PaymentStatus.HELD ||
               status == PaymentStatus.ON_HOLD ||
               status == PaymentStatus.COMPLETED ||
               status == PaymentStatus.SETTLED;
    }
    
    private boolean requiresManualApproval(Payment payment, PaymentCancellationEvent event) {
        // High-value payments require manual approval
        if (payment.getAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            return true;
        }
        
        // Merchant-initiated cancellations may require approval
        if ("MERCHANT".equals(event.getInitiatedBy())) {
            return true;
        }
        
        // Suspicious patterns require approval
        if (fraudService.isSuspiciousCancellationPattern(event)) {
            return true;
        }
        
        return false;
    }
    
    private void sendForManualApproval(Payment payment, PaymentCancellationEvent event,
            CancellationCalculation calculation, String correlationId) {
        
        // Send to approval queue
        Map<String, Object> approvalRequest = Map.of(
            "paymentId", payment.getId(),
            "cancellationReason", event.getCancellationReason(),
            "refundAmount", calculation.getRefundAmount(),
            "cancellationFee", calculation.getCancellationFee(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        );
        
        kafkaTemplate.send("payment-cancellation-approval-queue", approvalRequest);
        
        // Send notification to operations team
        notificationService.sendOperationalAlert(
            "Payment Cancellation Requires Approval",
            String.format("Payment %s cancellation requires manual approval. Amount: $%s, Reason: %s",
                payment.getId(), payment.getAmount(), event.getCancellationReason()),
            NotificationService.Priority.MEDIUM
        );
    }
    
    private void sendCancellationNotifications(Payment payment, PaymentCancellationEvent event,
            String correlationId) {
        
        // Customer notification
        notificationService.sendCustomerNotification(
            payment.getCustomerId(),
            "Payment Cancellation Processed",
            String.format("Your payment of $%s has been cancelled. Refund of $%s will be processed within %s.",
                payment.getAmount(), payment.getActualRefundAmount(), getRefundTimeframe(payment)),
            NotificationService.Priority.MEDIUM
        );
        
        // Merchant notification if applicable
        if (payment.getMerchantId() != null) {
            notificationService.sendMerchantNotification(
                payment.getMerchantId(),
                "Payment Cancelled",
                String.format("Payment %s has been cancelled by customer. Amount: $%s",
                    payment.getId(), payment.getAmount()),
                NotificationService.Priority.MEDIUM
            );
        }
    }
    
    private String getRefundTimeframe(Payment payment) {
        Duration timeSincePayment = Duration.between(payment.getCreatedAt(), LocalDateTime.now());
        
        if (timeSincePayment.compareTo(INSTANT_CANCELLATION_WINDOW) <= 0) {
            return "5 minutes";
        } else if (timeSincePayment.compareTo(SAME_DAY_CANCELLATION_WINDOW) <= 0) {
            return "2 hours";
        } else {
            return "3 business days";
        }
    }
    
    private void handleSuspiciousCancellation(PaymentCancellationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        payment.setStatus(PaymentStatus.FRAUD_REVIEW);
        payment.setFraudReason("Suspicious cancellation pattern detected");
        paymentRepository.save(payment);
        
        notificationService.sendSecurityAlert(
            "Suspicious Cancellation Pattern",
            String.format("Unusual cancellation pattern detected for payment %s", event.getPaymentId()),
            NotificationService.Priority.HIGH
        );
    }
    
    private void publishPaymentStatusUpdate(Payment payment, String reason, String correlationId) {
        PaymentStatusUpdatedEvent statusEvent = PaymentStatusUpdatedEvent.builder()
            .paymentId(payment.getId())
            .status(payment.getStatus().toString())
            .reason(reason)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send("payment-status-updated-events", statusEvent);
    }
    
    private void handleCancellationEventError(PaymentCancellationEvent event, Exception error,
            String correlationId) {
        
        Map<String, Object> dlqPayload = Map.of(
            "originalEvent", event,
            "error", error.getMessage(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        );
        
        kafkaTemplate.send("payment-cancellation-events-dlq", dlqPayload);
        
        notificationService.sendOperationalAlert(
            "Cancellation Event Processing Failed",
            String.format("Failed to process cancellation event for payment %s: %s",
                event.getPaymentId(), error.getMessage()),
            NotificationService.Priority.HIGH
        );
        
        metricsService.incrementCancellationEventError(event.getEventType());
    }
    
    /**
     * Inner classes for cancellation logic
     */
    public static class CancellationEligibility {
        private final boolean eligible;
        private final String reason;
        private final String cancellationType;
        private final boolean instantCancellation;
        
        private CancellationEligibility(boolean eligible, String reason, String type, boolean instant) {
            this.eligible = eligible;
            this.reason = reason;
            this.cancellationType = type;
            this.instantCancellation = instant;
        }
        
        public static CancellationEligibility notEligible(String reason) {
            return new CancellationEligibility(false, reason, null, false);
        }
        
        public static CancellationEligibility instantCancellation() {
            return new CancellationEligibility(true, null, "INSTANT", true);
        }
        
        public static CancellationEligibility sameDayCancellation() {
            return new CancellationEligibility(true, null, "SAME_DAY", false);
        }
        
        public static CancellationEligibility standardCancellation() {
            return new CancellationEligibility(true, null, "STANDARD", false);
        }
        
        // Getters
        public boolean isEligible() { return eligible; }
        public String getReason() { return reason; }
        public String getCancellationType() { return cancellationType; }
        public boolean isInstantCancellation() { return instantCancellation; }
    }
    
    @lombok.Builder
    @lombok.Data
    public static class CancellationCalculation {
        private BigDecimal originalAmount;
        private BigDecimal refundAmount;
        private BigDecimal cancellationFee;
        private BigDecimal netRefundAmount;
    }
}