package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentPreAuthorizationEvent;
import com.waqiti.common.events.AuthCaptureEvent;
import com.waqiti.common.events.PaymentStatusUpdatedEvent;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.domain.PaymentAuthorization;
import com.waqiti.payment.domain.AuthorizationStatus;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.repository.PaymentAuthorizationRepository;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.AuthorizationService;
import com.waqiti.payment.service.CaptureService;
import com.waqiti.payment.service.PaymentGatewayService;
import com.waqiti.payment.exception.PaymentNotFoundException;
import com.waqiti.payment.exception.AuthorizationNotFoundException;
import com.waqiti.payment.metrics.AuthorizationMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.security.SecurityContext;
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
import java.math.BigDecimal;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentPreAuthorizationEventsConsumer {
    
    private final PaymentRepository paymentRepository;
    private final PaymentAuthorizationRepository authorizationRepository;
    private final PaymentService paymentService;
    private final AuthorizationService authorizationService;
    private final CaptureService captureService;
    private final PaymentGatewayService gatewayService;
    private final AuthorizationMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SecurityContext securityContext;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final int DEFAULT_AUTH_EXPIRY_DAYS = 7;
    private static final int HOTEL_AUTH_EXPIRY_DAYS = 14;
    private static final int RENTAL_AUTH_EXPIRY_DAYS = 30;
    
    @KafkaListener(
        topics = {"payment-pre-authorization-events", "auth-capture-events", "payment-hold-events"},
        groupId = "payment-authorization-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handlePreAuthorizationEvent(
            @Payload PaymentPreAuthorizationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("auth-%s-p%d-o%d", 
            event.getPaymentId(), partition, offset);
        
        log.info("Processing pre-authorization event: paymentId={}, eventType={}, correlation={}",
            event.getPaymentId(), event.getEventType(), correlationId);
        
        try {
            securityContext.validateFinancialOperation(event.getPaymentId(), "PAYMENT_AUTHORIZATION");
            validateAuthorizationEvent(event);
            
            switch (event.getEventType()) {
                case AUTH_REQUESTED:
                    processAuthRequested(event, correlationId);
                    break;
                case AUTH_APPROVED:
                    processAuthApproved(event, correlationId);
                    break;
                case AUTH_DECLINED:
                    processAuthDeclined(event, correlationId);
                    break;
                case CAPTURE_REQUESTED:
                    processCaptureRequested(event, correlationId);
                    break;
                case CAPTURE_COMPLETED:
                    processCaptureCompleted(event, correlationId);
                    break;
                case VOID_REQUESTED:
                    processVoidRequested(event, correlationId);
                    break;
                case AUTH_EXPIRED:
                    processAuthExpired(event, correlationId);
                    break;
                case PARTIAL_CAPTURE:
                    processPartialCapture(event, correlationId);
                    break;
                case AUTH_INCREMENTED:
                    processAuthIncremented(event, correlationId);
                    break;
                default:
                    log.warn("Unknown authorization event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logFinancialEvent(
                "AUTHORIZATION_EVENT_PROCESSED",
                event.getPaymentId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "authorizationId", event.getAuthorizationId(),
                    "amount", event.getAmount(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process authorization event: paymentId={}, error={}",
                event.getPaymentId(), e.getMessage(), e);
            
            handleAuthorizationEventError(event, e, correlationId);
            acknowledgment.acknowledge();
        }
    }
    
    private void processAuthRequested(PaymentPreAuthorizationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        
        log.info("Processing authorization request: paymentId={}, amount={}", 
            payment.getId(), event.getAmount());
        
        PaymentAuthorization authorization = PaymentAuthorization.builder()
            .id(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .userId(payment.getUserId())
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .gatewayId(payment.getGatewayId())
            .status(AuthorizationStatus.PENDING)
            .authorizationType(event.getAuthorizationType())
            .expiryDays(calculateExpiryDays(event.getAuthorizationType()))
            .requestedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        authorizationRepository.save(authorization);
        
        payment.setStatus(PaymentStatus.AUTHORIZATION_PENDING);
        payment.setAuthorizationId(authorization.getId());
        paymentRepository.save(payment);
        
        try {
            String gatewayAuthId = gatewayService.requestAuthorization(
                payment,
                event.getAmount(),
                event.getCurrency()
            );
            
            authorization.setGatewayAuthorizationId(gatewayAuthId);
            authorizationRepository.save(authorization);
            
        } catch (Exception e) {
            log.error("Failed to request authorization from gateway: paymentId={}", 
                payment.getId(), e);
            
            authorization.setStatus(AuthorizationStatus.FAILED);
            authorization.setFailureReason(e.getMessage());
            authorizationRepository.save(authorization);
            
            payment.setStatus(PaymentStatus.AUTHORIZATION_FAILED);
            paymentRepository.save(payment);
        }
        
        metricsService.recordAuthorizationRequested(payment.getGatewayId(), event.getAuthorizationType());
    }
    
    private void processAuthApproved(PaymentPreAuthorizationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        PaymentAuthorization authorization = getAuthorization(event.getAuthorizationId());
        
        log.info("Processing authorization approval: paymentId={}, authId={}, amount={}", 
            payment.getId(), authorization.getId(), event.getAmount());
        
        authorization.setStatus(AuthorizationStatus.APPROVED);
        authorization.setApprovedAt(LocalDateTime.now());
        authorization.setApprovedAmount(event.getAmount());
        authorization.setGatewayAuthorizationId(event.getGatewayAuthorizationId());
        authorization.setAuthorizationCode(event.getAuthorizationCode());
        authorization.setExpiresAt(LocalDateTime.now().plusDays(authorization.getExpiryDays()));
        authorizationRepository.save(authorization);
        
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setAuthorizationCode(event.getAuthorizationCode());
        payment.setAuthorizedAmount(event.getAmount());
        payment.setAuthorizedAt(LocalDateTime.now());
        payment.setAuthorizationExpiresAt(authorization.getExpiresAt());
        paymentRepository.save(payment);
        
        authorizationService.scheduleFundsReservation(
            payment.getId(),
            authorization.getId(),
            event.getAmount()
        );
        
        authorizationService.scheduleAutoCapture(
            payment.getId(),
            authorization.getId(),
            event.getAutoCaptureDelay()
        );
        
        authorizationService.scheduleExpirationCheck(
            authorization.getId(),
            authorization.getExpiresAt()
        );
        
        publishPaymentStatusUpdate(payment, "AUTHORIZATION_APPROVED", correlationId);
        
        notificationService.sendNotification(
            payment.getUserId(),
            "Payment Authorized",
            String.format("Payment of %s %s has been authorized. Expires: %s",
                event.getAmount(), event.getCurrency(), authorization.getExpiresAt()),
            payment.getId()
        );
        
        metricsService.recordAuthorizationApproved(
            payment.getGatewayId(), 
            event.getAuthorizationType(),
            event.getAmount()
        );
        
        log.info("Authorization approved: paymentId={}, authId={}, expiresAt={}", 
            payment.getId(), authorization.getId(), authorization.getExpiresAt());
    }
    
    private void processAuthDeclined(PaymentPreAuthorizationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        PaymentAuthorization authorization = getAuthorization(event.getAuthorizationId());
        
        log.warn("Processing authorization decline: paymentId={}, reason={}", 
            payment.getId(), event.getDeclineReason());
        
        authorization.setStatus(AuthorizationStatus.DECLINED);
        authorization.setDeclinedAt(LocalDateTime.now());
        authorization.setDeclineReason(event.getDeclineReason());
        authorization.setDeclineCode(event.getDeclineCode());
        authorizationRepository.save(authorization);
        
        payment.setStatus(PaymentStatus.AUTHORIZATION_DECLINED);
        payment.setFailureReason(event.getDeclineReason());
        payment.setFailureCode(event.getDeclineCode());
        paymentRepository.save(payment);
        
        publishPaymentStatusUpdate(payment, "AUTHORIZATION_DECLINED", correlationId);
        
        notificationService.sendNotification(
            payment.getUserId(),
            "Payment Authorization Declined",
            String.format("Authorization for payment of %s %s was declined. Reason: %s",
                payment.getAmount(), payment.getCurrency(), event.getDeclineReason()),
            payment.getId()
        );
        
        metricsService.recordAuthorizationDeclined(
            payment.getGatewayId(),
            event.getDeclineCode()
        );
    }
    
    private void processCaptureRequested(PaymentPreAuthorizationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        PaymentAuthorization authorization = getAuthorization(event.getAuthorizationId());
        
        log.info("Processing capture request: paymentId={}, authId={}, captureAmount={}", 
            payment.getId(), authorization.getId(), event.getCaptureAmount());
        
        if (authorization.getStatus() != AuthorizationStatus.APPROVED) {
            log.error("Cannot capture: authorization not approved. Status: {}", 
                authorization.getStatus());
            throw new IllegalStateException("Authorization not in approved state");
        }
        
        if (authorization.isExpired()) {
            log.error("Cannot capture: authorization expired at {}", 
                authorization.getExpiresAt());
            throw new IllegalStateException("Authorization has expired");
        }
        
        BigDecimal captureAmount = event.getCaptureAmount() != null ? 
            event.getCaptureAmount() : authorization.getApprovedAmount();
        
        if (captureAmount.compareTo(authorization.getApprovedAmount()) > 0) {
            log.error("Capture amount {} exceeds authorized amount {}", 
                captureAmount, authorization.getApprovedAmount());
            throw new IllegalArgumentException("Capture amount exceeds authorized amount");
        }
        
        authorization.setStatus(AuthorizationStatus.CAPTURE_PENDING);
        authorization.setCaptureRequestedAt(LocalDateTime.now());
        authorization.setCaptureAmount(captureAmount);
        authorizationRepository.save(authorization);
        
        payment.setStatus(PaymentStatus.CAPTURE_PENDING);
        paymentRepository.save(payment);
        
        try {
            String gatewayCaptureId = gatewayService.captureAuthorization(
                payment,
                authorization.getGatewayAuthorizationId(),
                captureAmount
            );
            
            authorization.setGatewayCaptureId(gatewayCaptureId);
            authorizationRepository.save(authorization);
            
        } catch (Exception e) {
            log.error("Failed to capture authorization: paymentId={}, authId={}, error={}", 
                payment.getId(), authorization.getId(), e.getMessage(), e);
            
            authorization.setStatus(AuthorizationStatus.CAPTURE_FAILED);
            authorization.setCaptureFailureReason(e.getMessage());
            authorizationRepository.save(authorization);
            
            payment.setStatus(PaymentStatus.CAPTURE_FAILED);
            paymentRepository.save(payment);
        }
        
        metricsService.recordCaptureRequested(payment.getGatewayId(), captureAmount);
    }
    
    private void processCaptureCompleted(PaymentPreAuthorizationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        PaymentAuthorization authorization = getAuthorization(event.getAuthorizationId());
        
        log.info("Processing capture completion: paymentId={}, authId={}, capturedAmount={}", 
            payment.getId(), authorization.getId(), event.getCapturedAmount());
        
        authorization.setStatus(AuthorizationStatus.CAPTURED);
        authorization.setCapturedAt(LocalDateTime.now());
        authorization.setCapturedAmount(event.getCapturedAmount());
        authorization.setGatewayCaptureId(event.getGatewayCaptureId());
        authorizationRepository.save(authorization);
        
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setCapturedAmount(event.getCapturedAmount());
        payment.setCapturedAt(LocalDateTime.now());
        payment.setCompletedAt(LocalDateTime.now());
        paymentRepository.save(payment);
        
        authorizationService.releaseFundsReservation(
            payment.getId(),
            authorization.getId()
        );
        
        publishPaymentStatusUpdate(payment, "CAPTURE_COMPLETED", correlationId);
        
        notificationService.sendNotification(
            payment.getUserId(),
            "Payment Completed",
            String.format("Payment of %s %s has been completed successfully.",
                event.getCapturedAmount(), payment.getCurrency()),
            payment.getId()
        );
        
        metricsService.recordCaptureCompleted(
            payment.getGatewayId(),
            event.getCapturedAmount()
        );
        
        log.info("Capture completed: paymentId={}, authId={}, capturedAmount={}", 
            payment.getId(), authorization.getId(), event.getCapturedAmount());
    }
    
    private void processVoidRequested(PaymentPreAuthorizationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        PaymentAuthorization authorization = getAuthorization(event.getAuthorizationId());
        
        log.info("Processing void request: paymentId={}, authId={}, reason={}", 
            payment.getId(), authorization.getId(), event.getVoidReason());
        
        try {
            gatewayService.voidAuthorization(
                payment,
                authorization.getGatewayAuthorizationId()
            );
            
            authorization.setStatus(AuthorizationStatus.VOIDED);
            authorization.setVoidedAt(LocalDateTime.now());
            authorization.setVoidReason(event.getVoidReason());
            authorizationRepository.save(authorization);
            
            payment.setStatus(PaymentStatus.VOIDED);
            payment.setVoidReason(event.getVoidReason());
            payment.setVoidedAt(LocalDateTime.now());
            paymentRepository.save(payment);
            
            authorizationService.releaseFundsReservation(
                payment.getId(),
                authorization.getId()
            );
            
            publishPaymentStatusUpdate(payment, "AUTHORIZATION_VOIDED", correlationId);
            
            notificationService.sendNotification(
                payment.getUserId(),
                "Payment Authorization Voided",
                String.format("Authorization for payment of %s %s has been cancelled.",
                    payment.getAmount(), payment.getCurrency()),
                payment.getId()
            );
            
            metricsService.recordAuthorizationVoided(payment.getGatewayId());
            
        } catch (Exception e) {
            log.error("Failed to void authorization: paymentId={}, authId={}, error={}", 
                payment.getId(), authorization.getId(), e.getMessage(), e);
            
            authorization.setStatus(AuthorizationStatus.VOID_FAILED);
            authorization.setVoidFailureReason(e.getMessage());
            authorizationRepository.save(authorization);
        }
    }
    
    private void processAuthExpired(PaymentPreAuthorizationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        PaymentAuthorization authorization = getAuthorization(event.getAuthorizationId());
        
        log.warn("Processing authorization expiry: paymentId={}, authId={}", 
            payment.getId(), authorization.getId());
        
        authorization.setStatus(AuthorizationStatus.EXPIRED);
        authorization.setExpiredAt(LocalDateTime.now());
        authorizationRepository.save(authorization);
        
        payment.setStatus(PaymentStatus.AUTHORIZATION_EXPIRED);
        paymentRepository.save(payment);
        
        authorizationService.releaseFundsReservation(
            payment.getId(),
            authorization.getId()
        );
        
        publishPaymentStatusUpdate(payment, "AUTHORIZATION_EXPIRED", correlationId);
        
        notificationService.sendOperationalAlert(
            "Authorization Expired",
            String.format("Authorization for payment %s has expired without capture.",
                payment.getId()),
            NotificationService.Priority.MEDIUM
        );
        
        metricsService.recordAuthorizationExpired(payment.getGatewayId());
    }
    
    private void processPartialCapture(PaymentPreAuthorizationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        PaymentAuthorization authorization = getAuthorization(event.getAuthorizationId());
        
        log.info("Processing partial capture: paymentId={}, authId={}, capturedAmount={}, remainingAmount={}", 
            payment.getId(), authorization.getId(), 
            event.getCapturedAmount(), event.getRemainingAmount());
        
        authorization.setCapturedAmount(
            authorization.getCapturedAmount().add(event.getCapturedAmount())
        );
        authorization.setRemainingAmount(event.getRemainingAmount());
        authorization.setPartialCaptureCount(authorization.getPartialCaptureCount() + 1);
        
        if (event.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0) {
            authorization.setStatus(AuthorizationStatus.CAPTURED);
        } else {
            authorization.setStatus(AuthorizationStatus.PARTIALLY_CAPTURED);
        }
        
        authorizationRepository.save(authorization);
        
        payment.setCapturedAmount(authorization.getCapturedAmount());
        payment.setStatus(authorization.getStatus() == AuthorizationStatus.CAPTURED ? 
            PaymentStatus.COMPLETED : PaymentStatus.PARTIALLY_CAPTURED);
        paymentRepository.save(payment);
        
        metricsService.recordPartialCapture(
            payment.getGatewayId(),
            event.getCapturedAmount(),
            event.getRemainingAmount()
        );
    }
    
    private void processAuthIncremented(PaymentPreAuthorizationEvent event, String correlationId) {
        Payment payment = getPaymentById(event.getPaymentId());
        PaymentAuthorization authorization = getAuthorization(event.getAuthorizationId());
        
        log.info("Processing authorization increment: paymentId={}, authId={}, incrementAmount={}", 
            payment.getId(), authorization.getId(), event.getIncrementAmount());
        
        authorization.setApprovedAmount(
            authorization.getApprovedAmount().add(event.getIncrementAmount())
        );
        authorization.setIncrementCount(authorization.getIncrementCount() + 1);
        authorizationRepository.save(authorization);
        
        payment.setAuthorizedAmount(authorization.getApprovedAmount());
        paymentRepository.save(payment);
        
        metricsService.recordAuthorizationIncremented(
            payment.getGatewayId(),
            event.getIncrementAmount()
        );
    }
    
    private int calculateExpiryDays(String authorizationType) {
        return switch (authorizationType) {
            case "HOTEL" -> HOTEL_AUTH_EXPIRY_DAYS;
            case "RENTAL" -> RENTAL_AUTH_EXPIRY_DAYS;
            default -> DEFAULT_AUTH_EXPIRY_DAYS;
        };
    }
    
    private void validateAuthorizationEvent(PaymentPreAuthorizationEvent event) {
        if (event.getPaymentId() == null || event.getPaymentId().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment ID is required");
        }
        
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
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
    
    private PaymentAuthorization getAuthorization(String authorizationId) {
        return authorizationRepository.findById(authorizationId)
            .orElseThrow(() -> new AuthorizationNotFoundException(
                "Authorization not found: " + authorizationId));
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
    
    private void handleAuthorizationEventError(PaymentPreAuthorizationEvent event, Exception error, 
            String correlationId) {
        
        Map<String, Object> dlqPayload = Map.of(
            "originalEvent", event,
            "error", error.getMessage(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        );
        
        kafkaTemplate.send("payment-pre-authorization-events-dlq", dlqPayload);
        
        notificationService.sendOperationalAlert(
            "Authorization Event Processing Failed",
            String.format("Failed to process authorization event for payment %s: %s",
                event.getPaymentId(), error.getMessage()),
            NotificationService.Priority.HIGH
        );
        
        metricsService.incrementAuthorizationEventError(event.getEventType());
    }
}