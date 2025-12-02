package com.waqiti.payment.events;

import com.waqiti.common.events.Payment3DSAuthenticationEvent;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.ThreeDSAuthentication;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Payment 3DS Authentication Event Producer
 * 
 * CRITICAL IMPLEMENTATION: Publishes 3D Secure authentication events
 * Connects to Payment3DSAuthenticationEventsConsumer
 * 
 * This producer is essential for:
 * - PCI DSS compliance
 * - PSD2 Strong Customer Authentication (SCA)
 * - Fraud prevention and liability shift
 * - Payment security workflows
 * 
 * Event Types Published:
 * - AUTHENTICATION_REQUIRED: When 3DS check is needed
 * - CHALLENGE_INITIATED: Challenge flow started
 * - CHALLENGE_COMPLETED: User completed challenge
 * - FRICTIONLESS_COMPLETED: Frictionless auth completed
 * - AUTHENTICATION_SUCCESS: Auth successful
 * - AUTHENTICATION_FAILED: Auth failed
 * - AUTHENTICATION_ABANDONED: User abandoned auth
 * - EXEMPTION_APPLIED: SCA exemption granted
 * - STEP_UP_REQUIRED: Additional auth needed
 * - AUTHENTICATION_TIMEOUT: Auth timed out
 * 
 * @author Waqiti Engineering Team
 * @version 2.0 - Production Implementation
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class Payment3DSAuthenticationEventProducer {

    private final KafkaTemplate<String, Payment3DSAuthenticationEvent> kafkaTemplate;
    
    private static final String TOPIC = "payment-3ds-authentication-events";

    /**
     * Publish event when 3DS authentication is required
     */
    public CompletableFuture<SendResult<String, Payment3DSAuthenticationEvent>> publishAuthenticationRequired(
            Payment payment, 
            String threeDSVersion,
            boolean challengeRequired,
            String correlationId) {
        
        log.info("Publishing 3DS authentication required: paymentId={}, version={}, challenge={}",
            payment.getId(), threeDSVersion, challengeRequired);
        
        Payment3DSAuthenticationEvent event = Payment3DSAuthenticationEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .eventType("AUTHENTICATION_REQUIRED")
            .threeDSVersion(threeDSVersion)
            .challengeRequired(challengeRequired)
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .merchantId(payment.getMerchantId())
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish event when challenge flow is initiated
     */
    public CompletableFuture<SendResult<String, Payment3DSAuthenticationEvent>> publishChallengeInitiated(
            Payment payment,
            ThreeDSAuthentication auth,
            String challengeUrl,
            String correlationId) {
        
        log.info("Publishing 3DS challenge initiated: paymentId={}, authId={}",
            payment.getId(), auth.getId());
        
        Payment3DSAuthenticationEvent event = Payment3DSAuthenticationEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .eventType("CHALLENGE_INITIATED")
            .threeDSVersion(auth.getThreeDSVersion())
            .challengeRequired(true)
            .challengeUrl(challengeUrl)
            .transactionId(auth.getTransactionId())
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish event when challenge is completed
     */
    public CompletableFuture<SendResult<String, Payment3DSAuthenticationEvent>> publishChallengeCompleted(
            Payment payment,
            ThreeDSAuthentication auth,
            String authenticationStatus,
            String authenticationValue,
            String eci,
            String correlationId) {
        
        log.info("Publishing 3DS challenge completed: paymentId={}, status={}",
            payment.getId(), authenticationStatus);
        
        Payment3DSAuthenticationEvent event = Payment3DSAuthenticationEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .eventType("CHALLENGE_COMPLETED")
            .threeDSVersion(auth.getThreeDSVersion())
            .authenticationStatus(authenticationStatus)
            .authenticationValue(authenticationValue)
            .eci(eci)
            .transactionId(auth.getTransactionId())
            .challengeRequired(true)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish event when frictionless authentication completes
     */
    public CompletableFuture<SendResult<String, Payment3DSAuthenticationEvent>> publishFrictionlessCompleted(
            Payment payment,
            ThreeDSAuthentication auth,
            String authenticationStatus,
            String authenticationValue,
            String eci,
            String correlationId) {
        
        log.info("Publishing 3DS frictionless completed: paymentId={}, status={}",
            payment.getId(), authenticationStatus);
        
        Payment3DSAuthenticationEvent event = Payment3DSAuthenticationEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .eventType("FRICTIONLESS_COMPLETED")
            .threeDSVersion(auth.getThreeDSVersion())
            .authenticationStatus(authenticationStatus)
            .authenticationValue(authenticationValue)
            .eci(eci)
            .transactionId(auth.getTransactionId())
            .challengeRequired(false)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish event when authentication succeeds
     */
    public CompletableFuture<SendResult<String, Payment3DSAuthenticationEvent>> publishAuthenticationSuccess(
            Payment payment,
            ThreeDSAuthentication auth,
            String authenticationValue,
            String eci,
            String correlationId) {
        
        log.info("Publishing 3DS authentication success: paymentId={}", payment.getId());
        
        Payment3DSAuthenticationEvent event = Payment3DSAuthenticationEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .eventType("AUTHENTICATION_SUCCESS")
            .threeDSVersion(auth.getThreeDSVersion())
            .authenticationStatus("SUCCESS")
            .authenticationValue(authenticationValue)
            .eci(eci)
            .transactionId(auth.getTransactionId())
            .liabilityShift(true)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish event when authentication fails
     */
    public CompletableFuture<SendResult<String, Payment3DSAuthenticationEvent>> publishAuthenticationFailed(
            Payment payment,
            ThreeDSAuthentication auth,
            String failureReason,
            String correlationId) {
        
        log.warn("Publishing 3DS authentication failed: paymentId={}, reason={}",
            payment.getId(), failureReason);
        
        Payment3DSAuthenticationEvent event = Payment3DSAuthenticationEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .eventType("AUTHENTICATION_FAILED")
            .threeDSVersion(auth.getThreeDSVersion())
            .authenticationStatus("FAILED")
            .failureReason(failureReason)
            .transactionId(auth.getTransactionId())
            .liabilityShift(false)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish event when authentication is abandoned
     */
    public CompletableFuture<SendResult<String, Payment3DSAuthenticationEvent>> publishAuthenticationAbandoned(
            Payment payment,
            ThreeDSAuthentication auth,
            String correlationId) {
        
        log.info("Publishing 3DS authentication abandoned: paymentId={}", payment.getId());
        
        Payment3DSAuthenticationEvent event = Payment3DSAuthenticationEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .eventType("AUTHENTICATION_ABANDONED")
            .threeDSVersion(auth.getThreeDSVersion())
            .authenticationStatus("ABANDONED")
            .transactionId(auth.getTransactionId())
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish event when SCA exemption is applied
     */
    public CompletableFuture<SendResult<String, Payment3DSAuthenticationEvent>> publishExemptionApplied(
            Payment payment,
            String exemptionType,
            String exemptionReason,
            boolean liabilityShift,
            String correlationId) {
        
        log.info("Publishing 3DS exemption applied: paymentId={}, type={}",
            payment.getId(), exemptionType);
        
        Payment3DSAuthenticationEvent event = Payment3DSAuthenticationEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .eventType("EXEMPTION_APPLIED")
            .exemptionType(exemptionType)
            .exemptionReason(exemptionReason)
            .liabilityShift(liabilityShift)
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish event when step-up authentication is required
     */
    public CompletableFuture<SendResult<String, Payment3DSAuthenticationEvent>> publishStepUpRequired(
            Payment payment,
            String stepUpReason,
            String correlationId) {
        
        log.info("Publishing 3DS step-up required: paymentId={}, reason={}",
            payment.getId(), stepUpReason);
        
        Payment3DSAuthenticationEvent event = Payment3DSAuthenticationEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .eventType("STEP_UP_REQUIRED")
            .stepUpReason(stepUpReason)
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish event when authentication times out
     */
    public CompletableFuture<SendResult<String, Payment3DSAuthenticationEvent>> publishAuthenticationTimeout(
            Payment payment,
            ThreeDSAuthentication auth,
            String timeoutType,
            String correlationId) {
        
        log.warn("Publishing 3DS authentication timeout: paymentId={}, type={}",
            payment.getId(), timeoutType);
        
        Payment3DSAuthenticationEvent event = Payment3DSAuthenticationEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .eventType("AUTHENTICATION_TIMEOUT")
            .threeDSVersion(auth.getThreeDSVersion())
            .authenticationStatus("TIMEOUT")
            .timeoutType(timeoutType)
            .transactionId(auth.getTransactionId())
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Send event to Kafka with error handling
     */
    private CompletableFuture<SendResult<String, Payment3DSAuthenticationEvent>> sendEvent(
            Payment3DSAuthenticationEvent event) {
        
        try {
            CompletableFuture<SendResult<String, Payment3DSAuthenticationEvent>> future = 
                kafkaTemplate.send(TOPIC, event.getPaymentId(), event);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug("3DS auth event published successfully: eventId={}, paymentId={}, type={}",
                        event.getEventId(), event.getPaymentId(), event.getEventType());
                } else {
                    log.error("Failed to publish 3DS auth event: eventId={}, paymentId={}, error={}",
                        event.getEventId(), event.getPaymentId(), ex.getMessage(), ex);
                }
            });
            
            return future;
            
        } catch (Exception e) {
            log.error("Error sending 3DS auth event: eventId={}, error={}",
                event.getEventId(), e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Get event statistics
     */
    public Map<String, Object> getEventStatistics() {
        return Map.of(
            "topic", TOPIC,
            "eventTypes", java.util.Arrays.asList(
                "AUTHENTICATION_REQUIRED",
                "CHALLENGE_INITIATED",
                "CHALLENGE_COMPLETED",
                "FRICTIONLESS_COMPLETED",
                "AUTHENTICATION_SUCCESS",
                "AUTHENTICATION_FAILED",
                "AUTHENTICATION_ABANDONED",
                "EXEMPTION_APPLIED",
                "STEP_UP_REQUIRED",
                "AUTHENTICATION_TIMEOUT"
            ),
            "producerActive", true
        );
    }
}