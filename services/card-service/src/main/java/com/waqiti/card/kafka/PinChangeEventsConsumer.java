package com.waqiti.card.kafka;

import com.waqiti.common.events.PinChangeEvent;
import com.waqiti.card.domain.Card;
import com.waqiti.card.domain.PinChangeRequest;
import com.waqiti.card.repository.CardRepository;
import com.waqiti.card.repository.PinChangeRequestRepository;
import com.waqiti.card.service.SecurityService;
import com.waqiti.card.service.FraudCheckService;
import com.waqiti.card.service.CardManagementService;
import com.waqiti.card.metrics.CardMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class PinChangeEventsConsumer {
    
    private final CardRepository cardRepository;
    private final PinChangeRequestRepository pinChangeRequestRepository;
    private final SecurityService securityService;
    private final FraudCheckService fraudCheckService;
    private final CardManagementService cardManagementService;
    private final CardMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final int MAX_PIN_CHANGE_ATTEMPTS = 3;
    private static final int PIN_CHANGE_COOLDOWN_HOURS = 24;
    private static final int MAX_PIN_CHANGES_PER_MONTH = 5;
    
    @KafkaListener(
        topics = {"pin-change-events", "card-pin-update-events", "pin-reset-events"},
        groupId = "pin-change-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "5"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 60)
    public void handlePinChangeEvent(
            @Payload PinChangeEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("pin-change-%s-p%d-o%d", 
            event.getCardId(), partition, offset);
        
        log.info("Processing PIN change event: cardId={}, type={}, method={}", 
            event.getCardId(), event.getEventType(), event.getChangeMethod());
        
        try {
            switch (event.getEventType()) {
                case PIN_CHANGE_REQUESTED:
                    processPinChangeRequested(event, correlationId);
                    break;
                case OLD_PIN_VERIFIED:
                    processOldPinVerified(event, correlationId);
                    break;
                case OLD_PIN_VERIFICATION_FAILED:
                    processOldPinVerificationFailed(event, correlationId);
                    break;
                case NEW_PIN_VALIDATED:
                    processNewPinValidated(event, correlationId);
                    break;
                case NEW_PIN_VALIDATION_FAILED:
                    processNewPinValidationFailed(event, correlationId);
                    break;
                case PIN_CHANGED:
                    processPinChanged(event, correlationId);
                    break;
                case PIN_RESET_REQUESTED:
                    processPinResetRequested(event, correlationId);
                    break;
                case PIN_RESET_CODE_SENT:
                    processPinResetCodeSent(event, correlationId);
                    break;
                case PIN_RESET_CODE_VERIFIED:
                    processPinResetCodeVerified(event, correlationId);
                    break;
                case PIN_RESET_COMPLETED:
                    processPinResetCompleted(event, correlationId);
                    break;
                case PIN_CHANGE_CANCELLED:
                    processPinChangeCancelled(event, correlationId);
                    break;
                case PIN_BLOCKED:
                    processPinBlocked(event, correlationId);
                    break;
                default:
                    log.warn("Unknown PIN change event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logCardEvent(
                "PIN_CHANGE_EVENT_PROCESSED",
                event.getCardId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "userId", event.getUserId(),
                    "changeMethod", event.getChangeMethod() != null ? event.getChangeMethod() : "N/A",
                    "requestId", event.getRequestId() != null ? event.getRequestId() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process PIN change event: {}", e.getMessage(), e);
            kafkaTemplate.send("pin-change-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processPinChangeRequested(PinChangeEvent event, String correlationId) {
        log.info("PIN change requested: cardId={}, userId={}, method={}", 
            event.getCardId(), event.getUserId(), event.getChangeMethod());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        if (!"ACTIVE".equals(card.getStatus())) {
            log.error("Card not active: cardId={}, status={}", event.getCardId(), card.getStatus());
            return;
        }
        
        if (card.getLastPinChangeAt() != null) {
            LocalDateTime cooldownExpiry = card.getLastPinChangeAt()
                .plusHours(PIN_CHANGE_COOLDOWN_HOURS);
            
            if (LocalDateTime.now().isBefore(cooldownExpiry)) {
                log.error("PIN change cooldown active: cardId={}, cooldownExpiry={}", 
                    event.getCardId(), cooldownExpiry);
                
                notificationService.sendNotification(
                    event.getUserId(),
                    "PIN Change Cooldown",
                    String.format("You can change your PIN again after %s. " +
                        "This is for your security.", cooldownExpiry),
                    correlationId
                );
                return;
            }
        }
        
        long monthlyChanges = pinChangeRequestRepository
            .countByCardIdAndCreatedAtAfter(event.getCardId(), 
                LocalDateTime.now().minusMonths(1));
        
        if (monthlyChanges >= MAX_PIN_CHANGES_PER_MONTH) {
            log.error("Monthly PIN change limit exceeded: cardId={}, changes={}", 
                event.getCardId(), monthlyChanges);
            cardManagementService.flagForReview(event.getCardId(), "EXCESSIVE_PIN_CHANGES");
            return;
        }
        
        boolean fraudCheck = fraudCheckService.checkPinChangeRequest(
            event.getUserId(), event.getCardId(), event.getChangeMethod());
        
        if (!fraudCheck) {
            log.error("Fraud check failed for PIN change: cardId={}, userId={}", 
                event.getCardId(), event.getUserId());
            cardManagementService.blockCard(event.getCardId(), "FRAUD_SUSPECTED");
            return;
        }
        
        PinChangeRequest request = PinChangeRequest.builder()
            .id(UUID.randomUUID().toString())
            .cardId(event.getCardId())
            .userId(event.getUserId())
            .changeMethod(event.getChangeMethod())
            .requestedAt(LocalDateTime.now())
            .attemptsRemaining(MAX_PIN_CHANGE_ATTEMPTS)
            .status("REQUESTED")
            .correlationId(correlationId)
            .build();
        
        pinChangeRequestRepository.save(request);
        
        if ("APP".equals(event.getChangeMethod())) {
            securityService.requestOldPinVerification(request.getId());
        } else if ("ATM".equals(event.getChangeMethod())) {
            securityService.requestAtmPinChange(request.getId());
        }
        
        metricsService.recordPinChangeRequested(event.getChangeMethod());
    }
    
    private void processOldPinVerified(PinChangeEvent event, String correlationId) {
        log.info("Old PIN verified: requestId={}, cardId={}", 
            event.getRequestId(), event.getCardId());
        
        PinChangeRequest request = pinChangeRequestRepository.findById(event.getRequestId())
            .orElseThrow();
        
        request.setOldPinVerifiedAt(LocalDateTime.now());
        request.setStatus("OLD_PIN_VERIFIED");
        pinChangeRequestRepository.save(request);
        
        metricsService.recordOldPinVerified();
    }
    
    private void processOldPinVerificationFailed(PinChangeEvent event, String correlationId) {
        log.error("Old PIN verification failed: requestId={}, cardId={}, attempt={}", 
            event.getRequestId(), event.getCardId(), event.getFailedAttempt());
        
        PinChangeRequest request = pinChangeRequestRepository.findById(event.getRequestId())
            .orElseThrow();
        
        request.setAttemptsRemaining(request.getAttemptsRemaining() - 1);
        request.setLastFailedAttempt(LocalDateTime.now());
        
        if (request.getAttemptsRemaining() <= 0) {
            request.setStatus("LOCKED");
            pinChangeRequestRepository.save(request);
            
            Card card = cardRepository.findById(event.getCardId())
                .orElseThrow();
            card.setPinBlocked(true);
            card.setPinBlockedAt(LocalDateTime.now());
            cardRepository.save(card);
            
            notificationService.sendNotification(
                event.getUserId(),
                "Card PIN Blocked",
                "Your card PIN has been blocked due to multiple failed verification attempts. " +
                    "Please contact customer support or visit an ATM to reset your PIN.",
                correlationId
            );
            
            log.error("PIN blocked due to failed attempts: cardId={}", event.getCardId());
        } else {
            request.setStatus("OLD_PIN_VERIFICATION_FAILED");
            pinChangeRequestRepository.save(request);
            
            notificationService.sendNotification(
                event.getUserId(),
                "PIN Verification Failed",
                String.format("Incorrect PIN entered. You have %d attempts remaining.",
                    request.getAttemptsRemaining()),
                correlationId
            );
        }
        
        metricsService.recordOldPinVerificationFailed(event.getFailedAttempt());
    }
    
    private void processNewPinValidated(PinChangeEvent event, String correlationId) {
        log.info("New PIN validated: requestId={}, cardId={}", 
            event.getRequestId(), event.getCardId());
        
        PinChangeRequest request = pinChangeRequestRepository.findById(event.getRequestId())
            .orElseThrow();
        
        request.setNewPinValidatedAt(LocalDateTime.now());
        request.setStatus("NEW_PIN_VALIDATED");
        pinChangeRequestRepository.save(request);
        
        securityService.changePinToNew(request.getId(), event.getNewPin());
        
        metricsService.recordNewPinValidated();
    }
    
    private void processNewPinValidationFailed(PinChangeEvent event, String correlationId) {
        log.error("New PIN validation failed: requestId={}, reason={}", 
            event.getRequestId(), event.getValidationFailureReason());
        
        PinChangeRequest request = pinChangeRequestRepository.findById(event.getRequestId())
            .orElseThrow();
        
        request.setStatus("NEW_PIN_VALIDATION_FAILED");
        request.setValidationFailureReason(event.getValidationFailureReason());
        pinChangeRequestRepository.save(request);
        
        notificationService.sendNotification(
            event.getUserId(),
            "PIN Validation Failed",
            String.format("Your new PIN doesn't meet security requirements: %s. Please try again.",
                event.getValidationFailureReason()),
            correlationId
        );
        
        metricsService.recordNewPinValidationFailed(event.getValidationFailureReason());
    }
    
    private void processPinChanged(PinChangeEvent event, String correlationId) {
        log.info("PIN changed successfully: requestId={}, cardId={}", 
            event.getRequestId(), event.getCardId());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        String encryptedPin = securityService.encryptPin(event.getNewPin());
        card.setEncryptedPin(encryptedPin);
        card.setLastPinChangeAt(LocalDateTime.now());
        card.setPinChangeCount(card.getPinChangeCount() + 1);
        cardRepository.save(card);
        
        PinChangeRequest request = pinChangeRequestRepository.findById(event.getRequestId())
            .orElseThrow();
        
        request.setCompletedAt(LocalDateTime.now());
        request.setStatus("COMPLETED");
        pinChangeRequestRepository.save(request);
        
        notificationService.sendNotification(
            event.getUserId(),
            "PIN Changed Successfully",
            String.format("Your card PIN has been changed successfully via %s. " +
                "If you didn't make this change, please contact us immediately.",
                request.getChangeMethod()),
            correlationId
        );
        
        metricsService.recordPinChanged(request.getChangeMethod());
    }
    
    private void processPinResetRequested(PinChangeEvent event, String correlationId) {
        log.info("PIN reset requested: cardId={}, userId={}, reason={}", 
            event.getCardId(), event.getUserId(), event.getResetReason());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        PinChangeRequest resetRequest = PinChangeRequest.builder()
            .id(UUID.randomUUID().toString())
            .cardId(event.getCardId())
            .userId(event.getUserId())
            .changeMethod("RESET")
            .resetReason(event.getResetReason())
            .requestedAt(LocalDateTime.now())
            .attemptsRemaining(MAX_PIN_CHANGE_ATTEMPTS)
            .status("RESET_REQUESTED")
            .correlationId(correlationId)
            .build();
        
        pinChangeRequestRepository.save(resetRequest);
        
        securityService.generatePinResetCode(resetRequest.getId());
        
        metricsService.recordPinResetRequested(event.getResetReason());
    }
    
    private void processPinResetCodeSent(PinChangeEvent event, String correlationId) {
        log.info("PIN reset code sent: requestId={}, deliveryMethod={}", 
            event.getRequestId(), event.getCodeDeliveryMethod());
        
        PinChangeRequest request = pinChangeRequestRepository.findById(event.getRequestId())
            .orElseThrow();
        
        String hashedCode = securityService.hashResetCode(event.getResetCode());
        
        request.setResetCodeHash(hashedCode);
        request.setResetCodeSentAt(LocalDateTime.now());
        request.setResetCodeExpiresAt(LocalDateTime.now().plusMinutes(15));
        request.setCodeDeliveryMethod(event.getCodeDeliveryMethod());
        request.setStatus("RESET_CODE_SENT");
        pinChangeRequestRepository.save(request);
        
        if ("SMS".equals(event.getCodeDeliveryMethod())) {
            notificationService.sendSMS(
                event.getPhoneNumber(),
                String.format("Your PIN reset code is: %s. Valid for 15 minutes. " +
                    "Do not share this code.", event.getResetCode()),
                correlationId
            );
        } else if ("EMAIL".equals(event.getCodeDeliveryMethod())) {
            notificationService.sendEmail(
                event.getEmail(),
                "PIN Reset Code",
                String.format("Your PIN reset code is: %s. Valid for 15 minutes.", 
                    event.getResetCode()),
                correlationId
            );
        }
        
        metricsService.recordPinResetCodeSent(event.getCodeDeliveryMethod());
    }
    
    private void processPinResetCodeVerified(PinChangeEvent event, String correlationId) {
        log.info("PIN reset code verified: requestId={}", event.getRequestId());
        
        PinChangeRequest request = pinChangeRequestRepository.findById(event.getRequestId())
            .orElseThrow();
        
        if (LocalDateTime.now().isAfter(request.getResetCodeExpiresAt())) {
            log.error("Reset code expired: requestId={}", event.getRequestId());
            request.setStatus("RESET_CODE_EXPIRED");
            pinChangeRequestRepository.save(request);
            return;
        }
        
        request.setResetCodeVerifiedAt(LocalDateTime.now());
        request.setStatus("RESET_CODE_VERIFIED");
        pinChangeRequestRepository.save(request);
        
        metricsService.recordPinResetCodeVerified();
    }
    
    private void processPinResetCompleted(PinChangeEvent event, String correlationId) {
        log.info("PIN reset completed: requestId={}, cardId={}", 
            event.getRequestId(), event.getCardId());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        String encryptedPin = securityService.encryptPin(event.getNewPin());
        card.setEncryptedPin(encryptedPin);
        card.setLastPinChangeAt(LocalDateTime.now());
        card.setPinBlocked(false);
        card.setPinChangeCount(card.getPinChangeCount() + 1);
        cardRepository.save(card);
        
        PinChangeRequest request = pinChangeRequestRepository.findById(event.getRequestId())
            .orElseThrow();
        
        request.setCompletedAt(LocalDateTime.now());
        request.setStatus("COMPLETED");
        pinChangeRequestRepository.save(request);
        
        notificationService.sendNotification(
            event.getUserId(),
            "PIN Reset Successful",
            "Your card PIN has been reset successfully. You can now use your card with the new PIN.",
            correlationId
        );
        
        metricsService.recordPinResetCompleted();
    }
    
    private void processPinChangeCancelled(PinChangeEvent event, String correlationId) {
        log.info("PIN change cancelled: requestId={}, cancelledBy={}", 
            event.getRequestId(), event.getCancelledBy());
        
        PinChangeRequest request = pinChangeRequestRepository.findById(event.getRequestId())
            .orElseThrow();
        
        request.setCancelledAt(LocalDateTime.now());
        request.setCancelledBy(event.getCancelledBy());
        request.setStatus("CANCELLED");
        pinChangeRequestRepository.save(request);
        
        metricsService.recordPinChangeCancelled();
    }
    
    private void processPinBlocked(PinChangeEvent event, String correlationId) {
        log.error("PIN blocked: cardId={}, reason={}", event.getCardId(), event.getBlockReason());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        card.setPinBlocked(true);
        card.setPinBlockedAt(LocalDateTime.now());
        card.setPinBlockReason(event.getBlockReason());
        cardRepository.save(card);
        
        notificationService.sendNotification(
            event.getUserId(),
            "URGENT: Card PIN Blocked",
            String.format("Your card PIN has been blocked. Reason: %s. " +
                "Please contact customer support or visit an ATM to reset your PIN.",
                event.getBlockReason()),
            correlationId
        );
        
        metricsService.recordPinBlocked(event.getBlockReason());
    }
}