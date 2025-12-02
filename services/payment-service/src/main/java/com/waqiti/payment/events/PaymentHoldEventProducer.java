package com.waqiti.payment.events;

import com.waqiti.common.events.PaymentHoldEvent;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentHold;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Payment Hold Event Producer
 * 
 * CRITICAL IMPLEMENTATION: Publishes payment hold events
 * Connects to PaymentHoldEventsConsumer
 * 
 * This producer is essential for:
 * - Fund reservation and authorization holds
 * - Fraud prevention holds
 * - Regulatory and compliance holds
 * - Risk management workflows
 * - Hold expiration management
 * 
 * Hold Types:
 * - AUTHORIZATION: Pre-authorization holds
 * - FRAUD_PREVENTION: Suspicious activity holds
 * - COMPLIANCE: Regulatory review holds
 * - REGULATORY: Government/legal holds
 * - CHARGEBACK: Dispute holds
 * 
 * Hold Actions:
 * - PLACE_HOLD: Create new hold
 * - RELEASE_HOLD: Release hold completely
 * - EXTEND_HOLD: Extend hold duration
 * - MODIFY_HOLD: Modify hold details
 * - PARTIAL_RELEASE: Partial hold release
 * - ESCALATE_HOLD: Escalate for review
 * - AUTOMATIC_RELEASE: Auto-release on expiry
 * - HOLD_EXPIRED: Hold expired notification
 * 
 * @author Waqiti Engineering Team
 * @version 2.0 - Production Implementation
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentHoldEventProducer {

    private final KafkaTemplate<String, PaymentHoldEvent> kafkaTemplate;
    
    private static final String TOPIC = "payment-hold-events";

    /**
     * Publish event to place a hold on payment
     */
    public CompletableFuture<SendResult<String, PaymentHoldEvent>> publishPlaceHold(
            Payment payment,
            String holdType,
            BigDecimal holdAmount,
            String holdReason,
            Duration holdDuration,
            String placedBy,
            String correlationId) {
        
        log.info("Publishing place hold: paymentId={}, type={}, amount={}",
            payment.getId(), holdType, holdAmount);
        
        PaymentHoldEvent event = PaymentHoldEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .holdAction("PLACE_HOLD")
            .holdType(holdType)
            .holdAmount(holdAmount)
            .holdReason(holdReason)
            .holdDuration(holdDuration != null ? holdDuration.toMillis() : null)
            .placedBy(placedBy)
            .requiresReview(isHighValueOrHighRisk(holdAmount, holdType))
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish event to release a hold
     */
    public CompletableFuture<SendResult<String, PaymentHoldEvent>> publishReleaseHold(
            Payment payment,
            PaymentHold hold,
            String releaseReason,
            String releasedBy,
            String correlationId) {
        
        log.info("Publishing release hold: paymentId={}, holdId={}", payment.getId(), hold.getId());
        
        PaymentHoldEvent event = PaymentHoldEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .holdId(hold.getId())
            .holdAction("RELEASE_HOLD")
            .holdType(hold.getHoldType())
            .holdAmount(hold.getHoldAmount())
            .releaseReason(releaseReason)
            .releasedBy(releasedBy)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish event to extend a hold
     */
    public CompletableFuture<SendResult<String, PaymentHoldEvent>> publishExtendHold(
            Payment payment,
            PaymentHold hold,
            Duration extensionDuration,
            String extensionReason,
            String extendedBy,
            String correlationId) {
        
        log.info("Publishing extend hold: paymentId={}, holdId={}, extension={}",
            payment.getId(), hold.getId(), extensionDuration);
        
        PaymentHoldEvent event = PaymentHoldEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .holdId(hold.getId())
            .holdAction("EXTEND_HOLD")
            .holdType(hold.getHoldType())
            .holdAmount(hold.getHoldAmount())
            .holdDuration(extensionDuration.toMillis())
            .holdReason(extensionReason)
            .modifiedBy(extendedBy)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish event to modify a hold
     */
    public CompletableFuture<SendResult<String, PaymentHoldEvent>> publishModifyHold(
            Payment payment,
            PaymentHold hold,
            BigDecimal newAmount,
            String modificationReason,
            String modifiedBy,
            String correlationId) {
        
        log.info("Publishing modify hold: paymentId={}, holdId={}, newAmount={}",
            payment.getId(), hold.getId(), newAmount);
        
        PaymentHoldEvent event = PaymentHoldEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .holdId(hold.getId())
            .holdAction("MODIFY_HOLD")
            .holdType(hold.getHoldType())
            .holdAmount(newAmount)
            .previousAmount(hold.getHoldAmount())
            .holdReason(modificationReason)
            .modifiedBy(modifiedBy)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish event for partial hold release
     */
    public CompletableFuture<SendResult<String, PaymentHoldEvent>> publishPartialRelease(
            Payment payment,
            PaymentHold hold,
            BigDecimal releaseAmount,
            String releaseReason,
            String releasedBy,
            String correlationId) {
        
        log.info("Publishing partial hold release: paymentId={}, holdId={}, releaseAmount={}",
            payment.getId(), hold.getId(), releaseAmount);
        
        PaymentHoldEvent event = PaymentHoldEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .holdId(hold.getId())
            .holdAction("PARTIAL_RELEASE")
            .holdType(hold.getHoldType())
            .holdAmount(releaseAmount)
            .remainingAmount(hold.getHoldAmount().subtract(releaseAmount))
            .releaseReason(releaseReason)
            .releasedBy(releasedBy)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish event to escalate a hold for review
     */
    public CompletableFuture<SendResult<String, PaymentHoldEvent>> publishEscalateHold(
            Payment payment,
            PaymentHold hold,
            String escalationReason,
            String escalatedBy,
            String escalationLevel,
            String correlationId) {
        
        log.info("Publishing escalate hold: paymentId={}, holdId={}, level={}",
            payment.getId(), hold.getId(), escalationLevel);
        
        PaymentHoldEvent event = PaymentHoldEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .holdId(hold.getId())
            .holdAction("ESCALATE_HOLD")
            .holdType(hold.getHoldType())
            .holdAmount(hold.getHoldAmount())
            .holdReason(escalationReason)
            .escalatedBy(escalatedBy)
            .escalationLevel(escalationLevel)
            .requiresReview(true)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish event for automatic hold release
     */
    public CompletableFuture<SendResult<String, PaymentHoldEvent>> publishAutomaticRelease(
            Payment payment,
            PaymentHold hold,
            String correlationId) {
        
        log.info("Publishing automatic hold release: paymentId={}, holdId={}",
            payment.getId(), hold.getId());
        
        PaymentHoldEvent event = PaymentHoldEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .holdId(hold.getId())
            .holdAction("AUTOMATIC_RELEASE")
            .holdType(hold.getHoldType())
            .holdAmount(hold.getHoldAmount())
            .releaseReason("Hold duration expired")
            .releasedBy("SYSTEM")
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish event when hold expires
     */
    public CompletableFuture<SendResult<String, PaymentHoldEvent>> publishHoldExpired(
            Payment payment,
            PaymentHold hold,
            String correlationId) {
        
        log.info("Publishing hold expired: paymentId={}, holdId={}", payment.getId(), hold.getId());
        
        PaymentHoldEvent event = PaymentHoldEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .holdId(hold.getId())
            .holdAction("HOLD_EXPIRED")
            .holdType(hold.getHoldType())
            .holdAmount(hold.getHoldAmount())
            .holdReason("Hold expired without explicit release")
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish event for fraud prevention hold
     */
    public CompletableFuture<SendResult<String, PaymentHoldEvent>> publishFraudPreventionHold(
            Payment payment,
            String fraudReason,
            Double riskScore,
            String correlationId) {
        
        log.warn("Publishing fraud prevention hold: paymentId={}, riskScore={}",
            payment.getId(), riskScore);
        
        PaymentHoldEvent event = PaymentHoldEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .holdAction("PLACE_HOLD")
            .holdType("FRAUD_PREVENTION")
            .holdAmount(payment.getAmount())
            .holdReason(fraudReason)
            .holdDuration(Duration.ofDays(14).toMillis())
            .riskScore(riskScore)
            .placedBy("FRAUD_SYSTEM")
            .requiresReview(true)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish event for compliance hold
     */
    public CompletableFuture<SendResult<String, PaymentHoldEvent>> publishComplianceHold(
            Payment payment,
            String complianceReason,
            String regulatoryReference,
            String correlationId) {
        
        log.info("Publishing compliance hold: paymentId={}, reason={}",
            payment.getId(), complianceReason);
        
        PaymentHoldEvent event = PaymentHoldEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .holdAction("PLACE_HOLD")
            .holdType("COMPLIANCE")
            .holdAmount(payment.getAmount())
            .holdReason(complianceReason)
            .holdDuration(Duration.ofDays(30).toMillis())
            .regulatoryReference(regulatoryReference)
            .placedBy("COMPLIANCE_SYSTEM")
            .requiresReview(true)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish event for authorization hold (pre-auth)
     */
    public CompletableFuture<SendResult<String, PaymentHoldEvent>> publishAuthorizationHold(
            Payment payment,
            BigDecimal authorizedAmount,
            String merchantId,
            String correlationId) {
        
        log.info("Publishing authorization hold: paymentId={}, amount={}",
            payment.getId(), authorizedAmount);
        
        PaymentHoldEvent event = PaymentHoldEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .holdAction("PLACE_HOLD")
            .holdType("AUTHORIZATION")
            .holdAmount(authorizedAmount)
            .holdReason("Pre-authorization hold")
            .holdDuration(Duration.ofDays(7).toMillis())
            .merchantId(merchantId)
            .placedBy("PAYMENT_GATEWAY")
            .requiresReview(false)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Determine if hold requires manual review
     */
    private boolean isHighValueOrHighRisk(BigDecimal amount, String holdType) {
        if (amount != null && amount.compareTo(new BigDecimal("50000")) > 0) {
            return true;
        }
        
        return "FRAUD_PREVENTION".equals(holdType) || 
               "REGULATORY".equals(holdType) ||
               "COMPLIANCE".equals(holdType);
    }

    /**
     * Send event to Kafka with error handling
     */
    private CompletableFuture<SendResult<String, PaymentHoldEvent>> sendEvent(PaymentHoldEvent event) {
        try {
            CompletableFuture<SendResult<String, PaymentHoldEvent>> future = 
                kafkaTemplate.send(TOPIC, event.getPaymentId(), event);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug("Hold event published successfully: eventId={}, paymentId={}, action={}",
                        event.getEventId(), event.getPaymentId(), event.getHoldAction());
                } else {
                    log.error("Failed to publish hold event: eventId={}, paymentId={}, error={}",
                        event.getEventId(), event.getPaymentId(), ex.getMessage(), ex);
                }
            });
            
            return future;
            
        } catch (Exception e) {
            log.error("Error sending hold event: eventId={}, error={}", event.getEventId(), e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Get event statistics
     */
    public Map<String, Object> getEventStatistics() {
        return Map.of(
            "topic", TOPIC,
            "holdTypes", java.util.Arrays.asList(
                "AUTHORIZATION",
                "FRAUD_PREVENTION",
                "COMPLIANCE",
                "REGULATORY",
                "CHARGEBACK"
            ),
            "holdActions", java.util.Arrays.asList(
                "PLACE_HOLD",
                "RELEASE_HOLD",
                "EXTEND_HOLD",
                "MODIFY_HOLD",
                "PARTIAL_RELEASE",
                "ESCALATE_HOLD",
                "AUTOMATIC_RELEASE",
                "HOLD_EXPIRED"
            ),
            "producerActive", true
        );
    }
}