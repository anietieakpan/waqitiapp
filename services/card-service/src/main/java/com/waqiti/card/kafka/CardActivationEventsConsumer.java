package com.waqiti.card.kafka;

import com.waqiti.common.events.CardActivationEvent;
import com.waqiti.card.domain.Card;
import com.waqiti.card.domain.CardActivation;
import com.waqiti.card.repository.CardRepository;
import com.waqiti.card.repository.CardActivationRepository;
import com.waqiti.card.service.CardManagementService;
import com.waqiti.card.service.SecurityService;
import com.waqiti.card.service.FraudCheckService;
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
public class CardActivationEventsConsumer {
    
    private final CardRepository cardRepository;
    private final CardActivationRepository activationRepository;
    private final CardManagementService cardManagementService;
    private final SecurityService securityService;
    private final FraudCheckService fraudCheckService;
    private final CardMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final int MAX_ACTIVATION_ATTEMPTS = 3;
    private static final int ACTIVATION_CODE_EXPIRY_MINUTES = 15;
    
    @KafkaListener(
        topics = {"card-activation-events", "card-enable-events", "card-onboarding-events"},
        groupId = "card-activation-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "5"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 90)
    public void handleCardActivationEvent(
            @Payload CardActivationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("card-activation-%s-p%d-o%d", 
            event.getCardId(), partition, offset);
        
        log.info("Processing card activation event: cardId={}, type={}, userId={}", 
            event.getCardId(), event.getEventType(), event.getUserId());
        
        try {
            switch (event.getEventType()) {
                case ACTIVATION_REQUESTED:
                    processActivationRequested(event, correlationId);
                    break;
                case ACTIVATION_CODE_GENERATED:
                    processActivationCodeGenerated(event, correlationId);
                    break;
                case ACTIVATION_CODE_VERIFIED:
                    processActivationCodeVerified(event, correlationId);
                    break;
                case IDENTITY_VERIFIED:
                    processIdentityVerified(event, correlationId);
                    break;
                case PIN_SET:
                    processPinSet(event, correlationId);
                    break;
                case CARD_ACTIVATED:
                    processCardActivated(event, correlationId);
                    break;
                case ACTIVATION_FAILED:
                    processActivationFailed(event, correlationId);
                    break;
                case ACTIVATION_CANCELLED:
                    processActivationCancelled(event, correlationId);
                    break;
                case FIRST_TRANSACTION_COMPLETED:
                    processFirstTransactionCompleted(event, correlationId);
                    break;
                default:
                    log.warn("Unknown card activation event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logCardEvent(
                "CARD_ACTIVATION_EVENT_PROCESSED",
                event.getCardId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "userId", event.getUserId(),
                    "activationId", event.getActivationId() != null ? event.getActivationId() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process card activation event: {}", e.getMessage(), e);
            kafkaTemplate.send("card-activation-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processActivationRequested(CardActivationEvent event, String correlationId) {
        log.info("Card activation requested: cardId={}, userId={}, method={}", 
            event.getCardId(), event.getUserId(), event.getActivationMethod());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        if (!"INACTIVE".equals(card.getStatus())) {
            log.error("Card not eligible for activation: cardId={}, status={}", 
                event.getCardId(), card.getStatus());
            return;
        }
        
        boolean fraudCheck = fraudCheckService.checkActivationRequest(
            event.getUserId(), event.getCardId(), event.getActivationMethod());
        
        if (!fraudCheck) {
            log.error("Fraud check failed for activation: cardId={}, userId={}", 
                event.getCardId(), event.getUserId());
            cardManagementService.blockCard(event.getCardId(), "FRAUD_SUSPECTED");
            return;
        }
        
        CardActivation activation = CardActivation.builder()
            .id(UUID.randomUUID().toString())
            .cardId(event.getCardId())
            .userId(event.getUserId())
            .activationMethod(event.getActivationMethod())
            .requestedAt(LocalDateTime.now())
            .attemptsRemaining(MAX_ACTIVATION_ATTEMPTS)
            .status("REQUESTED")
            .correlationId(correlationId)
            .build();
        
        activationRepository.save(activation);
        
        cardManagementService.generateActivationCode(activation.getId());
        
        metricsService.recordActivationRequested(event.getActivationMethod());
    }
    
    private void processActivationCodeGenerated(CardActivationEvent event, String correlationId) {
        log.info("Activation code generated: activationId={}, codeLength={}, deliveryMethod={}", 
            event.getActivationId(), event.getActivationCode().length(), event.getCodeDeliveryMethod());
        
        CardActivation activation = activationRepository.findById(event.getActivationId())
            .orElseThrow();
        
        String hashedCode = securityService.hashActivationCode(event.getActivationCode());
        
        activation.setActivationCodeHash(hashedCode);
        activation.setCodeGeneratedAt(LocalDateTime.now());
        activation.setCodeExpiresAt(LocalDateTime.now().plusMinutes(ACTIVATION_CODE_EXPIRY_MINUTES));
        activation.setCodeDeliveryMethod(event.getCodeDeliveryMethod());
        activation.setStatus("CODE_GENERATED");
        activationRepository.save(activation);
        
        if ("SMS".equals(event.getCodeDeliveryMethod())) {
            notificationService.sendSMS(
                event.getPhoneNumber(),
                String.format("Your card activation code is: %s. Valid for %d minutes. Do not share this code.",
                    event.getActivationCode(), ACTIVATION_CODE_EXPIRY_MINUTES),
                correlationId
            );
        } else if ("EMAIL".equals(event.getCodeDeliveryMethod())) {
            notificationService.sendEmail(
                event.getEmail(),
                "Card Activation Code",
                String.format("Your card activation code is: %s. Valid for %d minutes.",
                    event.getActivationCode(), ACTIVATION_CODE_EXPIRY_MINUTES),
                correlationId
            );
        }
        
        metricsService.recordActivationCodeGenerated(event.getCodeDeliveryMethod());
    }
    
    private void processActivationCodeVerified(CardActivationEvent event, String correlationId) {
        log.info("Activation code verified: activationId={}, userId={}", 
            event.getActivationId(), event.getUserId());
        
        CardActivation activation = activationRepository.findById(event.getActivationId())
            .orElseThrow();
        
        if (LocalDateTime.now().isAfter(activation.getCodeExpiresAt())) {
            log.error("Activation code expired: activationId={}, expiresAt={}", 
                event.getActivationId(), activation.getCodeExpiresAt());
            activation.setStatus("CODE_EXPIRED");
            activationRepository.save(activation);
            return;
        }
        
        activation.setCodeVerifiedAt(LocalDateTime.now());
        activation.setStatus("CODE_VERIFIED");
        activationRepository.save(activation);
        
        cardManagementService.requestIdentityVerification(activation.getId());
        
        metricsService.recordActivationCodeVerified();
    }
    
    private void processIdentityVerified(CardActivationEvent event, String correlationId) {
        log.info("Identity verified: activationId={}, verificationMethod={}", 
            event.getActivationId(), event.getIdentityVerificationMethod());
        
        CardActivation activation = activationRepository.findById(event.getActivationId())
            .orElseThrow();
        
        activation.setIdentityVerifiedAt(LocalDateTime.now());
        activation.setIdentityVerificationMethod(event.getIdentityVerificationMethod());
        activation.setStatus("IDENTITY_VERIFIED");
        activationRepository.save(activation);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Ready for Activation",
            "Your identity has been verified. Please set your card PIN to complete activation.",
            correlationId
        );
        
        metricsService.recordIdentityVerified(event.getIdentityVerificationMethod());
    }
    
    private void processPinSet(CardActivationEvent event, String correlationId) {
        log.info("PIN set for card: activationId={}, cardId={}", 
            event.getActivationId(), event.getCardId());
        
        CardActivation activation = activationRepository.findById(event.getActivationId())
            .orElseThrow();
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        String encryptedPin = securityService.encryptPin(event.getPin());
        card.setEncryptedPin(encryptedPin);
        card.setPinSetAt(LocalDateTime.now());
        cardRepository.save(card);
        
        activation.setPinSetAt(LocalDateTime.now());
        activation.setStatus("PIN_SET");
        activationRepository.save(activation);
        
        cardManagementService.activateCard(event.getCardId());
        
        metricsService.recordPinSet();
    }
    
    private void processCardActivated(CardActivationEvent event, String correlationId) {
        log.info("Card activated: cardId={}, userId={}, activatedAt={}", 
            event.getCardId(), event.getUserId(), event.getActivatedAt());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        card.setStatus("ACTIVE");
        card.setActivatedAt(event.getActivatedAt());
        card.setActivatedBy(event.getUserId());
        cardRepository.save(card);
        
        CardActivation activation = activationRepository
            .findByCardIdAndStatus(event.getCardId(), "PIN_SET")
            .orElseThrow();
        
        activation.setCompletedAt(LocalDateTime.now());
        activation.setStatus("COMPLETED");
        activationRepository.save(activation);
        
        cardManagementService.setInitialLimits(event.getCardId());
        
        cardManagementService.enableCardFeatures(event.getCardId(), 
            List.of("CONTACTLESS", "ONLINE_PURCHASES", "ATM_WITHDRAWAL"));
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Activated Successfully",
            String.format("Your %s card ending in %s is now active and ready to use. " +
                "You can make purchases, withdraw cash, and manage your card in the app.",
                card.getCardType(), card.getLast4Digits()),
            correlationId
        );
        
        metricsService.recordCardActivated(card.getCardType(), card.getCardNetwork());
    }
    
    private void processActivationFailed(CardActivationEvent event, String correlationId) {
        log.error("Card activation failed: activationId={}, failureReason={}", 
            event.getActivationId(), event.getFailureReason());
        
        CardActivation activation = activationRepository.findById(event.getActivationId())
            .orElseThrow();
        
        activation.setFailedAt(LocalDateTime.now());
        activation.setFailureReason(event.getFailureReason());
        activation.setAttemptsRemaining(activation.getAttemptsRemaining() - 1);
        
        if (activation.getAttemptsRemaining() <= 0) {
            activation.setStatus("LOCKED");
            log.error("Activation locked due to too many failed attempts: activationId={}", 
                event.getActivationId());
            
            notificationService.sendNotification(
                event.getUserId(),
                "Card Activation Locked",
                "Your card activation has been locked due to multiple failed attempts. " +
                    "Please contact customer support.",
                correlationId
            );
        } else {
            activation.setStatus("FAILED");
            
            notificationService.sendNotification(
                event.getUserId(),
                "Card Activation Failed",
                String.format("Card activation failed: %s. You have %d attempts remaining.",
                    event.getFailureReason(), activation.getAttemptsRemaining()),
                correlationId
            );
        }
        
        activationRepository.save(activation);
        
        metricsService.recordActivationFailed(event.getFailureReason());
    }
    
    private void processActivationCancelled(CardActivationEvent event, String correlationId) {
        log.info("Card activation cancelled: activationId={}, cancelledBy={}, reason={}", 
            event.getActivationId(), event.getCancelledBy(), event.getCancellationReason());
        
        CardActivation activation = activationRepository.findById(event.getActivationId())
            .orElseThrow();
        
        activation.setCancelledAt(LocalDateTime.now());
        activation.setCancelledBy(event.getCancelledBy());
        activation.setCancellationReason(event.getCancellationReason());
        activation.setStatus("CANCELLED");
        activationRepository.save(activation);
        
        metricsService.recordActivationCancelled(event.getCancellationReason());
    }
    
    private void processFirstTransactionCompleted(CardActivationEvent event, String correlationId) {
        log.info("First transaction completed: cardId={}, transactionId={}, amount={}", 
            event.getCardId(), event.getFirstTransactionId(), event.getFirstTransactionAmount());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        card.setFirstTransactionAt(LocalDateTime.now());
        card.setFirstTransactionId(event.getFirstTransactionId());
        card.setFirstTransactionAmount(event.getFirstTransactionAmount());
        cardRepository.save(card);
        
        notificationService.sendNotification(
            event.getUserId(),
            "First Transaction Successful",
            String.format("Congratulations! Your first card transaction of %s has been completed successfully.",
                event.getFirstTransactionAmount()),
            correlationId
        );
        
        metricsService.recordFirstTransaction(card.getCardType());
    }
}