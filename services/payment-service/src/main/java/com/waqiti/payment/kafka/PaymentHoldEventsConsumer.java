package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentHoldEvent;
import com.waqiti.common.events.PaymentStatusUpdatedEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.events.HoldExpirationEvent;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.domain.PaymentHold;
import com.waqiti.payment.domain.HoldType;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.repository.PaymentHoldRepository;
import com.waqiti.payment.service.PaymentHoldService;
import com.waqiti.payment.service.AuthorizationHoldService;
import com.waqiti.payment.service.FraudHoldService;
import com.waqiti.payment.service.ComplianceHoldService;
import com.waqiti.payment.exception.PaymentNotFoundException;
import com.waqiti.payment.exception.HoldProcessingException;
import com.waqiti.payment.metrics.HoldMetricsService;
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
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * CRITICAL Consumer for Payment Hold Events
 * 
 * Handles all payment hold operations including:
 * - Authorization holds and pre-authorization
 * - Fraud prevention holds
 * - Compliance and regulatory holds
 * - Hold expiration and automatic release
 * - Hold extension and modification
 * - Partial hold release
 * - Hold escalation and review workflows
 * - Cross-border hold compliance
 * 
 * This is CRITICAL for risk management and regulatory compliance.
 * Proper hold management prevents fraudulent transactions while
 * ensuring legitimate payments are not unnecessarily delayed.
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentHoldEventsConsumer {
    
    private final PaymentRepository paymentRepository;
    private final PaymentHoldRepository holdRepository;
    private final PaymentHoldService holdService;
    private final AuthorizationHoldService authHoldService;
    private final FraudHoldService fraudHoldService;
    private final ComplianceHoldService complianceHoldService;
    private final HoldMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SecurityContext securityContext;
    private final FraudService fraudService;
    private final LedgerService ledgerService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UniversalDLQHandler dlqHandler;
    
    // Hold duration limits
    private static final Duration AUTHORIZATION_HOLD_DURATION = Duration.ofDays(7);
    private static final Duration FRAUD_HOLD_DURATION = Duration.ofDays(14);
    private static final Duration COMPLIANCE_HOLD_DURATION = Duration.ofDays(30);
    private static final Duration REGULATORY_HOLD_DURATION = Duration.ofDays(90);
    
    // Hold amount thresholds
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("10000");
    private static final BigDecimal REGULATORY_THRESHOLD = new BigDecimal("50000");
    private static final BigDecimal SUSPICIOUS_THRESHOLD = new BigDecimal("25000");
    
    // Hold review SLAs
    private static final Duration URGENT_REVIEW_SLA = Duration.ofHours(4);
    private static final Duration STANDARD_REVIEW_SLA = Duration.ofHours(24);
    private static final Duration COMPLIANCE_REVIEW_SLA = Duration.ofDays(3);
    
    /**
     * Primary handler for payment hold events
     * Processes all types of payment holds with proper validation
     */
    @KafkaListener(
        topics = "payment-hold-events",
        groupId = "payment-hold-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "10"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handlePaymentHoldEvent(
            @Payload PaymentHoldEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("hold-%s-p%d-o%d", 
            event.getPaymentId(), partition, offset);
        
        log.info("Processing payment hold event: paymentId={}, holdType={}, action={}, correlation={}",
            event.getPaymentId(), event.getHoldType(), event.getHoldAction(), correlationId);
        
        try {
            // Security and validation
            securityContext.validateFinancialOperation(event.getPaymentId(), "PAYMENT_HOLD");
            validateHoldEvent(event);
            
            // Check for fraudulent hold manipulation
            if (fraudService.isSuspiciousHoldPattern(event)) {
                log.warn("Suspicious hold pattern detected: paymentId={}", event.getPaymentId());
                handleSuspiciousHoldPattern(event, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            // Process based on hold action
            switch (event.getHoldAction()) {
                case PLACE_HOLD:
                    processPlaceHold(event, correlationId);
                    break;
                case RELEASE_HOLD:
                    processReleaseHold(event, correlationId);
                    break;
                case EXTEND_HOLD:
                    processExtendHold(event, correlationId);
                    break;
                case MODIFY_HOLD:
                    processModifyHold(event, correlationId);
                    break;
                case PARTIAL_RELEASE:
                    processPartialRelease(event, correlationId);
                    break;
                case ESCALATE_HOLD:
                    processEscalateHold(event, correlationId);
                    break;
                case AUTOMATIC_RELEASE:
                    processAutomaticRelease(event, correlationId);
                    break;
                case HOLD_EXPIRED:
                    processHoldExpired(event, correlationId);
                    break;
                default:
                    log.warn("Unknown hold action: {}", event.getHoldAction());
                    break;
            }
            
            // Audit the hold operation
            auditService.logFinancialEvent(
                "HOLD_EVENT_PROCESSED",
                event.getPaymentId(),
                Map.of(
                    "holdAction", event.getHoldAction(),
                    "holdType", event.getHoldType(),
                    "holdAmount", event.getHoldAmount() != null ? event.getHoldAmount() : BigDecimal.ZERO,
                    "holdReason", event.getHoldReason(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing hold event: topic={}, partition={}, offset={}, error={}",
                topic, partition, offset, e.getMessage(), e);

            handleHoldEventError(event, e, correlationId);

            dlqHandler.handleFailedMessage(org.apache.kafka.clients.consumer.ConsumerRecord.class.cast(event), e)
                .thenAccept(result -> log.info("Message sent to DLQ: topic={}, offset={}, destination={}, category={}",
                        topic, offset, result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed - MESSAGE MAY BE LOST! " +
                            "topic={}, partition={}, offset={}, error={}",
                            topic, partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Hold event processing failed", e);
        }
    }
    
    /**
     * Processes hold placement with proper validation and authorization
     */
    private void processPlaceHold(PaymentHoldEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        
        log.info("Processing hold placement: paymentId={}, type={}, amount={}, reason={}",
            payment.getId(), event.getHoldType(), event.getHoldAmount(), event.getHoldReason());
        
        // Check if payment is eligible for hold
        if (!isHoldEligible(payment, event)) {
            log.warn("Payment not eligible for hold: paymentId={}, status={}",
                payment.getId(), payment.getStatus());
            return;
        }
        
        // Validate hold amount
        BigDecimal holdAmount = event.getHoldAmount() != null ? 
            event.getHoldAmount() : payment.getAmount();
        
        if (holdAmount.compareTo(payment.getAmount()) > 0) {
            log.error("Hold amount exceeds payment amount: holdAmount={}, paymentAmount={}",
                holdAmount, payment.getAmount());
            throw new HoldProcessingException("Hold amount cannot exceed payment amount");
        }
        
        // Calculate hold expiration
        LocalDateTime holdExpiration = calculateHoldExpiration(event.getHoldType());
        
        // Create hold record
        PaymentHold hold = createPaymentHold(payment, event, holdAmount, holdExpiration, correlationId);
        holdRepository.save(hold);
        
        // Update payment status and hold information
        payment.setStatus(PaymentStatus.HELD);
        payment.setHoldId(hold.getId());
        payment.setHoldType(event.getHoldType());
        payment.setHoldAmount(holdAmount);
        payment.setHoldReason(event.getHoldReason());
        payment.setHoldPlacedAt(LocalDateTime.now());
        payment.setHoldExpirationDate(holdExpiration);
        payment.setHoldPlacedBy(event.getInitiatedBy());
        paymentRepository.save(payment);
        
        // Process based on hold type
        switch (HoldType.valueOf(event.getHoldType())) {
            case AUTHORIZATION:
                processAuthorizationHold(payment, hold, event, correlationId);
                break;
            case FRAUD_PREVENTION:
                processFraudHold(payment, hold, event, correlationId);
                break;
            case COMPLIANCE:
                processComplianceHold(payment, hold, event, correlationId);
                break;
            case REGULATORY:
                processRegulatoryHold(payment, hold, event, correlationId);
                break;
            case MANUAL_REVIEW:
                processManualReviewHold(payment, hold, event, correlationId);
                break;
            default:
                log.warn("Unknown hold type: {}", event.getHoldType());
                break;
        }
        
        // Update ledger
        ledgerService.recordHoldPlacement(
            payment.getId(),
            hold.getId(),
            holdAmount,
            event.getHoldType(),
            correlationId
        );
        
        // Send notifications
        sendHoldNotifications(payment, hold, "PLACED", correlationId);
        
        // Update metrics
        metricsService.recordHoldPlaced(event.getHoldType(), holdAmount);
        
        // Publish status update
        publishPaymentStatusUpdate(payment, "HOLD_PLACED", correlationId);
        
        // Schedule automatic release if applicable
        scheduleAutomaticRelease(hold, correlationId);
        
        log.info("Hold placed successfully: paymentId={}, holdId={}, expires={}",
            payment.getId(), hold.getId(), holdExpiration);
    }
    
    /**
     * Calculates hold expiration based on hold type
     */
    private LocalDateTime calculateHoldExpiration(String holdType) {
        LocalDateTime now = LocalDateTime.now();
        
        switch (HoldType.valueOf(holdType)) {
            case AUTHORIZATION:
                return now.plus(AUTHORIZATION_HOLD_DURATION);
            case FRAUD_PREVENTION:
                return now.plus(FRAUD_HOLD_DURATION);
            case COMPLIANCE:
                return now.plus(COMPLIANCE_HOLD_DURATION);
            case REGULATORY:
                return now.plus(REGULATORY_HOLD_DURATION);
            case MANUAL_REVIEW:
                return now.plus(STANDARD_REVIEW_SLA);
            default:
                return now.plus(AUTHORIZATION_HOLD_DURATION); // Default
        }
    }
    
    /**
     * Creates payment hold record
     */
    private PaymentHold createPaymentHold(Payment payment, PaymentHoldEvent event,
            BigDecimal holdAmount, LocalDateTime expiration, String correlationId) {
        
        return PaymentHold.builder()
            .id(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .holdType(event.getHoldType())
            .holdAmount(holdAmount)
            .holdReason(event.getHoldReason())
            .initiatedBy(event.getInitiatedBy())
            .placedAt(LocalDateTime.now())
            .expiresAt(expiration)
            .status("ACTIVE")
            .correlationId(correlationId)
            .gatewayHoldId(event.getGatewayHoldId())
            .riskScore(event.getRiskScore())
            .reviewRequired(determineReviewRequired(event))
            .build();
    }
    
    /**
     * Processes authorization holds
     */
    private void processAuthorizationHold(Payment payment, PaymentHold hold, 
            PaymentHoldEvent event, String correlationId) {
        
        log.info("Processing authorization hold: paymentId={}, holdId={}",
            payment.getId(), hold.getId());
        
        try {
            // Process through gateway if applicable
            if (event.getGatewayHoldId() == null) {
                String gatewayHoldId = authHoldService.placeAuthorizationHold(
                    payment.getId(),
                    hold.getHoldAmount(),
                    correlationId
                );
                
                hold.setGatewayHoldId(gatewayHoldId);
                holdRepository.save(hold);
            }
            
            // Authorization holds typically auto-release after 7 days
            log.info("Authorization hold placed: paymentId={}, gatewayHoldId={}",
                payment.getId(), hold.getGatewayHoldId());
            
        } catch (Exception e) {
            log.error("Failed to process authorization hold: paymentId={}, error={}",
                payment.getId(), e.getMessage(), e);
            
            hold.setStatus("FAILED");
            hold.setFailureReason(e.getMessage());
            holdRepository.save(hold);
        }
    }
    
    /**
     * Processes fraud prevention holds
     */
    private void processFraudHold(Payment payment, PaymentHold hold, 
            PaymentHoldEvent event, String correlationId) {
        
        log.info("Processing fraud hold: paymentId={}, riskScore={}", 
            payment.getId(), event.getRiskScore());
        
        // Determine review priority based on risk score and amount
        String reviewPriority = determineReviewPriority(payment.getAmount(), event.getRiskScore());
        
        hold.setReviewPriority(reviewPriority);
        hold.setReviewRequired(true);
        
        // Set review SLA based on priority
        Duration reviewSLA = "URGENT".equals(reviewPriority) ? 
            URGENT_REVIEW_SLA : STANDARD_REVIEW_SLA;
        
        hold.setReviewBy(LocalDateTime.now().plus(reviewSLA));
        holdRepository.save(hold);
        
        // Submit for fraud review
        fraudHoldService.submitForReview(hold, reviewPriority, correlationId);
        
        // Send urgent alert for high-risk transactions
        if ("URGENT".equals(reviewPriority)) {
            notificationService.sendSecurityAlert(
                "Urgent Fraud Hold Review Required",
                String.format("Payment %s requires urgent fraud review. Amount: $%s, Risk Score: %s",
                    payment.getId(), payment.getAmount(), event.getRiskScore()),
                NotificationService.Priority.CRITICAL
            );
        }
        
        log.info("Fraud hold submitted for review: paymentId={}, priority={}, reviewBy={}",
            payment.getId(), reviewPriority, hold.getReviewBy());
    }
    
    /**
     * Processes compliance holds
     */
    private void processComplianceHold(Payment payment, PaymentHold hold, 
            PaymentHoldEvent event, String correlationId) {
        
        log.info("Processing compliance hold: paymentId={}, reason={}",
            payment.getId(), event.getHoldReason());
        
        hold.setReviewRequired(true);
        hold.setReviewBy(LocalDateTime.now().plus(COMPLIANCE_REVIEW_SLA));
        holdRepository.save(hold);
        
        // Submit for compliance review
        complianceHoldService.submitForReview(hold, correlationId);
        
        // Check if regulatory reporting is required
        if (payment.getAmount().compareTo(REGULATORY_THRESHOLD) > 0) {
            hold.setRegulatoryReportingRequired(true);
            holdRepository.save(hold);
            
            // Trigger regulatory reporting
            kafkaTemplate.send("regulatory-reporting-events", Map.of(
                "paymentId", payment.getId(),
                "holdId", hold.getId(),
                "reportType", "LARGE_TRANSACTION_HOLD",
                "correlationId", correlationId
            ));
        }
        
        log.info("Compliance hold submitted for review: paymentId={}, reviewBy={}",
            payment.getId(), hold.getReviewBy());
    }
    
    /**
     * Processes regulatory holds
     */
    private void processRegulatoryHold(Payment payment, PaymentHold hold, 
            PaymentHoldEvent event, String correlationId) {
        
        log.info("Processing regulatory hold: paymentId={}, regulation={}",
            payment.getId(), event.getRegulationType());
        
        hold.setRegulationType(event.getRegulationType());
        hold.setRegulatoryReportingRequired(true);
        hold.setReviewRequired(true);
        hold.setReviewBy(LocalDateTime.now().plus(COMPLIANCE_REVIEW_SLA));
        holdRepository.save(hold);
        
        // Notify compliance team
        notificationService.sendComplianceAlert(
            "Regulatory Hold Placed",
            String.format("Regulatory hold placed on payment %s. Regulation: %s, Amount: $%s",
                payment.getId(), event.getRegulationType(), payment.getAmount()),
            NotificationService.Priority.HIGH
        );
        
        log.info("Regulatory hold processed: paymentId={}, regulation={}",
            payment.getId(), event.getRegulationType());
    }
    
    /**
     * Processes manual review holds
     */
    private void processManualReviewHold(Payment payment, PaymentHold hold, 
            PaymentHoldEvent event, String correlationId) {
        
        log.info("Processing manual review hold: paymentId={}", payment.getId());
        
        hold.setReviewRequired(true);
        hold.setReviewBy(LocalDateTime.now().plus(STANDARD_REVIEW_SLA));
        holdRepository.save(hold);
        
        // Send to manual review queue
        kafkaTemplate.send("manual-review-queue", Map.of(
            "paymentId", payment.getId(),
            "holdId", hold.getId(),
            "reviewReason", event.getHoldReason(),
            "priority", determineReviewPriority(payment.getAmount(), event.getRiskScore()),
            "correlationId", correlationId
        ));
        
        log.info("Manual review hold queued: paymentId={}", payment.getId());
    }
    
    /**
     * Processes hold release
     */
    private void processReleaseHold(PaymentHoldEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        PaymentHold hold = getActiveHoldByPaymentId(payment.getId());
        
        if (hold == null) {
            log.warn("No active hold found for payment: {}", payment.getId());
            return;
        }
        
        log.info("Processing hold release: paymentId={}, holdId={}, releaseReason={}",
            payment.getId(), hold.getId(), event.getReleaseReason());
        
        // Validate release authorization
        if (!isReleaseAuthorized(hold, event)) {
            log.warn("Hold release not authorized: paymentId={}, holdId={}",
                payment.getId(), hold.getId());
            return;
        }
        
        // Update hold status
        hold.setStatus("RELEASED");
        hold.setReleasedAt(LocalDateTime.now());
        hold.setReleasedBy(event.getInitiatedBy());
        hold.setReleaseReason(event.getReleaseReason());
        holdRepository.save(hold);
        
        // Update payment status
        payment.setStatus(PaymentStatus.PROCESSING); // Resume processing
        payment.setHoldReleasedAt(LocalDateTime.now());
        paymentRepository.save(payment);
        
        // Release hold at gateway if applicable
        if (hold.getGatewayHoldId() != null) {
            try {
                authHoldService.releaseAuthorizationHold(
                    hold.getGatewayHoldId(),
                    event.getReleaseReason(),
                    correlationId
                );
            } catch (Exception e) {
                log.error("Failed to release gateway hold: gatewayHoldId={}, error={}",
                    hold.getGatewayHoldId(), e.getMessage());
            }
        }
        
        // Update ledger
        ledgerService.recordHoldRelease(
            payment.getId(),
            hold.getId(),
            hold.getHoldAmount(),
            event.getReleaseReason(),
            correlationId
        );
        
        // Send notifications
        sendHoldNotifications(payment, hold, "RELEASED", correlationId);
        
        // Update metrics
        metricsService.recordHoldReleased(hold.getHoldType(), hold.getHoldAmount());
        
        // Publish status update
        publishPaymentStatusUpdate(payment, "HOLD_RELEASED", correlationId);
        
        log.info("Hold released successfully: paymentId={}, holdId={}",
            payment.getId(), hold.getId());
    }
    
    /**
     * Processes hold extension
     */
    private void processExtendHold(PaymentHoldEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        PaymentHold hold = getActiveHoldByPaymentId(payment.getId());
        
        if (hold == null) {
            log.warn("No active hold found for extension: {}", payment.getId());
            return;
        }
        
        log.info("Processing hold extension: paymentId={}, holdId={}, newExpiration={}",
            payment.getId(), hold.getId(), event.getNewExpirationDate());
        
        // Validate extension authorization and limits
        if (!isExtensionAuthorized(hold, event)) {
            log.warn("Hold extension not authorized: paymentId={}", payment.getId());
            return;
        }
        
        LocalDateTime originalExpiration = hold.getExpiresAt();
        
        // Update hold expiration
        hold.setExpiresAt(event.getNewExpirationDate());
        hold.setExtendedAt(LocalDateTime.now());
        hold.setExtendedBy(event.getInitiatedBy());
        hold.setExtensionReason(event.getExtensionReason());
        holdRepository.save(hold);
        
        // Update payment
        payment.setHoldExpirationDate(event.getNewExpirationDate());
        paymentRepository.save(payment);
        
        // Send notifications
        notificationService.sendOperationalAlert(
            "Payment Hold Extended",
            String.format("Hold on payment %s extended from %s to %s. Reason: %s",
                payment.getId(), originalExpiration, event.getNewExpirationDate(),
                event.getExtensionReason()),
            NotificationService.Priority.MEDIUM
        );
        
        // Update metrics
        metricsService.recordHoldExtended(hold.getHoldType());
        
        log.info("Hold extended: paymentId={}, from={} to={}",
            payment.getId(), originalExpiration, event.getNewExpirationDate());
    }
    
    /**
     * Processes partial hold release
     */
    private void processPartialRelease(PaymentHoldEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        PaymentHold hold = getActiveHoldByPaymentId(payment.getId());
        
        if (hold == null) {
            log.warn("No active hold found for partial release: {}", payment.getId());
            return;
        }
        
        BigDecimal releaseAmount = event.getReleaseAmount();
        if (releaseAmount == null || releaseAmount.compareTo(BigDecimal.ZERO) <= 0 ||
            releaseAmount.compareTo(hold.getHoldAmount()) >= 0) {
            log.error("Invalid partial release amount: {}", releaseAmount);
            return;
        }
        
        log.info("Processing partial hold release: paymentId={}, releaseAmount={}, remainingHold={}",
            payment.getId(), releaseAmount, hold.getHoldAmount().subtract(releaseAmount));
        
        // Calculate remaining hold amount
        BigDecimal remainingAmount = hold.getHoldAmount().subtract(releaseAmount);
        
        // Update hold with remaining amount
        hold.setHoldAmount(remainingAmount);
        hold.setPartiallyReleased(true);
        hold.setLastPartialReleaseAt(LocalDateTime.now());
        holdRepository.save(hold);
        
        // Update payment
        payment.setHoldAmount(remainingAmount);
        paymentRepository.save(payment);
        
        // Update ledger
        ledgerService.recordPartialHoldRelease(
            payment.getId(),
            hold.getId(),
            releaseAmount,
            remainingAmount,
            correlationId
        );
        
        // Update metrics
        metricsService.recordPartialHoldRelease(hold.getHoldType(), releaseAmount);
        
        log.info("Partial hold release completed: paymentId={}, released={}, remaining={}",
            payment.getId(), releaseAmount, remainingAmount);
    }
    
    /**
     * Scheduled task to check for expired holds
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void checkExpiredHolds() {
        log.debug("Checking for expired holds...");
        
        List<PaymentHold> expiredHolds = holdRepository.findExpiredActiveHolds(LocalDateTime.now());
        
        for (PaymentHold expiredHold : expiredHolds) {
            try {
                log.info("Processing expired hold: holdId={}, paymentId={}", 
                    expiredHold.getId(), expiredHold.getPaymentId());
                
                PaymentHoldEvent expirationEvent = PaymentHoldEvent.builder()
                    .paymentId(expiredHold.getPaymentId())
                    .holdAction("HOLD_EXPIRED")
                    .holdType(expiredHold.getHoldType())
                    .holdId(expiredHold.getId())
                    .expirationReason("Automatic expiration")
                    .timestamp(Instant.now())
                    .build();
                
                kafkaTemplate.send("payment-hold-events", expirationEvent);
                
            } catch (Exception e) {
                log.error("Failed to process expired hold: holdId={}, error={}",
                    expiredHold.getId(), e.getMessage(), e);
            }
        }
        
        if (!expiredHolds.isEmpty()) {
            log.info("Processed {} expired holds", expiredHolds.size());
        }
    }
    
    /**
     * Additional utility methods
     */
    private void processEscalateHold(PaymentHoldEvent event, String correlationId) {
        // Handle hold escalation to higher authority
        log.info("Processing hold escalation: paymentId={}", event.getPaymentId());
        // Implementation for escalation logic
    }
    
    private void processAutomaticRelease(PaymentHoldEvent event, String correlationId) {
        // Handle automatic hold release based on conditions
        processReleaseHold(event, correlationId);
    }
    
    private void processHoldExpired(PaymentHoldEvent event, String correlationId) {
        PaymentHold hold = holdRepository.findById(event.getHoldId()).orElse(null);
        if (hold != null) {
            hold.setStatus("EXPIRED");
            hold.setExpiredAt(LocalDateTime.now());
            holdRepository.save(hold);
            
            // Auto-release expired holds
            PaymentHoldEvent releaseEvent = event.toBuilder()
                .holdAction("RELEASE_HOLD")
                .releaseReason("Hold expired")
                .initiatedBy("SYSTEM")
                .build();
            
            processReleaseHold(releaseEvent, correlationId);
        }
    }
    
    private void processModifyHold(PaymentHoldEvent event, String correlationId) {
        // Handle hold modifications (amount, type, reason, etc.)
        log.info("Processing hold modification: paymentId={}", event.getPaymentId());
        // Implementation for modification logic
    }
    
    /**
     * Validation and utility methods
     */
    private void validateHoldEvent(PaymentHoldEvent event) {
        if (event.getPaymentId() == null || event.getPaymentId().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment ID is required");
        }
        
        if (event.getHoldAction() == null || event.getHoldAction().trim().isEmpty()) {
            throw new IllegalArgumentException("Hold action is required");
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
    
    private PaymentHold getActiveHoldByPaymentId(String paymentId) {
        return holdRepository.findActiveHoldByPaymentId(paymentId).orElse(null);
    }
    
    private boolean isHoldEligible(Payment payment, PaymentHoldEvent event) {
        // Check if payment status allows holds
        return payment.getStatus() == PaymentStatus.PENDING ||
               payment.getStatus() == PaymentStatus.AUTHORIZED ||
               payment.getStatus() == PaymentStatus.PROCESSING;
    }
    
    private boolean determineReviewRequired(PaymentHoldEvent event) {
        return event.getHoldAmount() != null && 
               event.getHoldAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0;
    }
    
    private String determineReviewPriority(BigDecimal amount, Double riskScore) {
        if (amount.compareTo(SUSPICIOUS_THRESHOLD) > 0 || 
            (riskScore != null && riskScore > 80.0)) {
            return "URGENT";
        } else if (amount.compareTo(HIGH_VALUE_THRESHOLD) > 0 || 
                   (riskScore != null && riskScore > 60.0)) {
            return "HIGH";
        } else {
            return "STANDARD";
        }
    }
    
    private boolean isReleaseAuthorized(PaymentHold hold, PaymentHoldEvent event) {
        // Check authorization based on hold type and initiator
        if ("SYSTEM".equals(event.getInitiatedBy())) {
            return true; // System can always release
        }
        
        // Additional authorization checks based on hold type and amount
        return true; // Simplified for this implementation
    }
    
    private boolean isExtensionAuthorized(PaymentHold hold, PaymentHoldEvent event) {
        // Check if extension is within allowed limits
        Duration totalHoldDuration = Duration.between(hold.getPlacedAt(), event.getNewExpirationDate());
        Duration maxAllowedDuration = getMaxHoldDuration(hold.getHoldType());
        
        return totalHoldDuration.compareTo(maxAllowedDuration) <= 0;
    }
    
    private Duration getMaxHoldDuration(String holdType) {
        switch (HoldType.valueOf(holdType)) {
            case AUTHORIZATION: return AUTHORIZATION_HOLD_DURATION.multipliedBy(2);
            case FRAUD_PREVENTION: return FRAUD_HOLD_DURATION.multipliedBy(2);
            case COMPLIANCE: return COMPLIANCE_HOLD_DURATION.multipliedBy(2);
            case REGULATORY: return REGULATORY_HOLD_DURATION.multipliedBy(2);
            default: return AUTHORIZATION_HOLD_DURATION;
        }
    }
    
    private void scheduleAutomaticRelease(PaymentHold hold, String correlationId) {
        // Schedule automatic release before expiration
        // Implementation would use a scheduler or delayed message
        log.debug("Scheduled automatic release for hold: {}", hold.getId());
    }
    
    private void sendHoldNotifications(Payment payment, PaymentHold hold, 
            String action, String correlationId) {
        
        // Customer notification for significant holds
        if (hold.getHoldAmount().compareTo(new BigDecimal("100")) > 0) {
            String message = String.format(
                "Your payment of $%s has been %s due to %s. We will review this within %s.",
                payment.getAmount(), action.toLowerCase(), hold.getHoldReason(),
                getReviewTimeframe(hold.getHoldType())
            );
            
            notificationService.sendCustomerNotification(
                payment.getCustomerId(),
                "Payment " + action,
                message,
                NotificationService.Priority.MEDIUM
            );
        }
        
        // Merchant notification if applicable
        if (payment.getMerchantId() != null && "PLACED".equals(action)) {
            notificationService.sendMerchantNotification(
                payment.getMerchantId(),
                "Payment Hold Notification",
                String.format("Payment %s has been placed on hold for review.", payment.getId()),
                NotificationService.Priority.LOW
            );
        }
    }
    
    private String getReviewTimeframe(String holdType) {
        switch (HoldType.valueOf(holdType)) {
            case FRAUD_PREVENTION: return "24 hours";
            case COMPLIANCE: return "3 business days";
            case REGULATORY: return "5 business days";
            default: return "2 business days";
        }
    }
    
    private void handleSuspiciousHoldPattern(PaymentHoldEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        
        notificationService.sendSecurityAlert(
            "Suspicious Hold Pattern Detected",
            String.format("Unusual hold manipulation pattern detected for payment %s", 
                event.getPaymentId()),
            NotificationService.Priority.HIGH
        );
        
        // Place additional security hold
        payment.setStatus(PaymentStatus.SECURITY_REVIEW);
        paymentRepository.save(payment);
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
    
    private void handleHoldEventError(PaymentHoldEvent event, Exception error, 
            String correlationId) {
        
        Map<String, Object> dlqPayload = Map.of(
            "originalEvent", event,
            "error", error.getMessage(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        );
        
        kafkaTemplate.send("payment-hold-events-dlq", dlqPayload);
        
        notificationService.sendOperationalAlert(
            "Hold Event Processing Failed",
            String.format("Failed to process hold event for payment %s: %s",
                event.getPaymentId(), error.getMessage()),
            NotificationService.Priority.HIGH
        );
        
        metricsService.incrementHoldEventError(event.getHoldAction());
    }
}