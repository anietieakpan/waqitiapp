package com.waqiti.card.kafka;

import com.waqiti.common.events.CardLifecycleEvent;
import com.waqiti.card.domain.Card;
import com.waqiti.card.domain.CardStatusHistory;
import com.waqiti.card.repository.CardRepository;
import com.waqiti.card.repository.CardStatusHistoryRepository;
import com.waqiti.card.service.CardActivationService;
import com.waqiti.card.service.CardReplacementService;
import com.waqiti.card.service.CardSecurityService;
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
public class CardLifecycleEventsConsumer {
    
    private final CardRepository cardRepository;
    private final CardStatusHistoryRepository statusHistoryRepository;
    private final CardActivationService activationService;
    private final CardReplacementService replacementService;
    private final CardSecurityService securityService;
    private final CardMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"card-lifecycle-events", "card-status-changes", "card-management-events"},
        groupId = "card-lifecycle-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCardLifecycleEvent(
            @Payload CardLifecycleEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("card-%s-p%d-o%d", 
            event.getCardId(), partition, offset);
        
        log.info("Processing card lifecycle event: cardId={}, type={}, status={}", 
            event.getCardId(), event.getEventType(), event.getCardStatus());
        
        try {
            switch (event.getEventType()) {
                case CARD_ISSUED:
                    processCardIssued(event, correlationId);
                    break;
                case CARD_SHIPPED:
                    processCardShipped(event, correlationId);
                    break;
                case CARD_DELIVERED:
                    processCardDelivered(event, correlationId);
                    break;
                case CARD_ACTIVATION_REQUESTED:
                    processCardActivationRequested(event, correlationId);
                    break;
                case CARD_ACTIVATED:
                    processCardActivated(event, correlationId);
                    break;
                case CARD_BLOCKED:
                    processCardBlocked(event, correlationId);
                    break;
                case CARD_UNBLOCKED:
                    processCardUnblocked(event, correlationId);
                    break;
                case CARD_SUSPENDED:
                    processCardSuspended(event, correlationId);
                    break;
                case CARD_LOST_REPORTED:
                    processCardLostReported(event, correlationId);
                    break;
                case CARD_STOLEN_REPORTED:
                    processCardStolenReported(event, correlationId);
                    break;
                case CARD_DAMAGED_REPORTED:
                    processCardDamagedReported(event, correlationId);
                    break;
                case CARD_REPLACEMENT_REQUESTED:
                    processCardReplacementRequested(event, correlationId);
                    break;
                case CARD_EXPIRED:
                    processCardExpired(event, correlationId);
                    break;
                case CARD_RENEWAL_INITIATED:
                    processCardRenewalInitiated(event, correlationId);
                    break;
                case CARD_CLOSED:
                    processCardClosed(event, correlationId);
                    break;
                default:
                    log.warn("Unknown card lifecycle event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logCardEvent(
                "CARD_LIFECYCLE_EVENT_PROCESSED",
                event.getCardId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "status", event.getCardStatus(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process card lifecycle event: {}", e.getMessage(), e);
            kafkaTemplate.send("card-lifecycle-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processCardIssued(CardLifecycleEvent event, String correlationId) {
        log.info("Card issued: cardId={}, userId={}, cardType={}", 
            event.getCardId(), event.getUserId(), event.getCardType());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        card.setStatus("ISSUED");
        card.setIssuedAt(LocalDateTime.now());
        cardRepository.save(card);
        
        recordStatusChange(card, "PENDING", "ISSUED", "Card issued", correlationId);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Issued",
            "Your new card has been issued and will be shipped shortly.",
            correlationId
        );
        
        metricsService.recordCardIssued(event.getCardType());
    }
    
    private void processCardShipped(CardLifecycleEvent event, String correlationId) {
        log.info("Card shipped: cardId={}, trackingNumber={}, carrier={}", 
            event.getCardId(), event.getTrackingNumber(), event.getCarrier());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        card.setStatus("SHIPPED");
        card.setShippedAt(LocalDateTime.now());
        card.setTrackingNumber(event.getTrackingNumber());
        card.setCarrier(event.getCarrier());
        card.setEstimatedDeliveryDate(event.getEstimatedDeliveryDate());
        cardRepository.save(card);
        
        recordStatusChange(card, "ISSUED", "SHIPPED", "Card shipped", correlationId);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Shipped",
            String.format("Your card has been shipped! Track it here: %s", event.getTrackingNumber()),
            correlationId
        );
        
        metricsService.recordCardShipped(event.getCarrier());
    }
    
    private void processCardDelivered(CardLifecycleEvent event, String correlationId) {
        log.info("Card delivered: cardId={}", event.getCardId());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        card.setStatus("DELIVERED");
        card.setDeliveredAt(LocalDateTime.now());
        cardRepository.save(card);
        
        recordStatusChange(card, "SHIPPED", "DELIVERED", "Card delivered", correlationId);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Delivered",
            "Your card has been delivered! Activate it to start using it.",
            correlationId
        );
        
        metricsService.recordCardDelivered();
    }
    
    private void processCardActivationRequested(CardLifecycleEvent event, String correlationId) {
        log.info("Card activation requested: cardId={}, method={}", 
            event.getCardId(), event.getActivationMethod());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        card.setActivationRequested(true);
        card.setActivationRequestedAt(LocalDateTime.now());
        card.setActivationMethod(event.getActivationMethod());
        cardRepository.save(card);
        
        activationService.initiateActivation(event.getCardId(), event.getActivationMethod());
        metricsService.recordActivationRequested(event.getActivationMethod());
    }
    
    private void processCardActivated(CardLifecycleEvent event, String correlationId) {
        log.info("Card activated: cardId={}, userId={}", event.getCardId(), event.getUserId());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        card.setStatus("ACTIVE");
        card.setActivatedAt(LocalDateTime.now());
        cardRepository.save(card);
        
        recordStatusChange(card, card.getPreviousStatus(), "ACTIVE", "Card activated", correlationId);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Activated",
            "Your card is now active and ready to use!",
            correlationId
        );
        
        metricsService.recordCardActivated(event.getCardType());
    }
    
    private void processCardBlocked(CardLifecycleEvent event, String correlationId) {
        log.warn("Card blocked: cardId={}, reason={}", event.getCardId(), event.getBlockReason());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        String previousStatus = card.getStatus();
        card.setStatus("BLOCKED");
        card.setBlockedAt(LocalDateTime.now());
        card.setBlockReason(event.getBlockReason());
        cardRepository.save(card);
        
        recordStatusChange(card, previousStatus, "BLOCKED", event.getBlockReason(), correlationId);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Blocked",
            String.format("Your card has been blocked: %s. Contact support if you didn't request this.", 
                event.getBlockReason()),
            correlationId
        );
        
        metricsService.recordCardBlocked(event.getBlockReason());
    }
    
    private void processCardUnblocked(CardLifecycleEvent event, String correlationId) {
        log.info("Card unblocked: cardId={}", event.getCardId());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        card.setStatus("ACTIVE");
        card.setUnblockedAt(LocalDateTime.now());
        card.setBlockReason(null);
        cardRepository.save(card);
        
        recordStatusChange(card, "BLOCKED", "ACTIVE", "Card unblocked", correlationId);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Unblocked",
            "Your card has been unblocked and is ready to use.",
            correlationId
        );
        
        metricsService.recordCardUnblocked();
    }
    
    private void processCardSuspended(CardLifecycleEvent event, String correlationId) {
        log.warn("Card suspended: cardId={}, reason={}", event.getCardId(), event.getSuspensionReason());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        String previousStatus = card.getStatus();
        card.setStatus("SUSPENDED");
        card.setSuspendedAt(LocalDateTime.now());
        card.setSuspensionReason(event.getSuspensionReason());
        cardRepository.save(card);
        
        recordStatusChange(card, previousStatus, "SUSPENDED", event.getSuspensionReason(), correlationId);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Suspended",
            String.format("Your card has been suspended: %s", event.getSuspensionReason()),
            correlationId
        );
        
        metricsService.recordCardSuspended(event.getSuspensionReason());
    }
    
    private void processCardLostReported(CardLifecycleEvent event, String correlationId) {
        log.warn("Card reported lost: cardId={}, userId={}", event.getCardId(), event.getUserId());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        String previousStatus = card.getStatus();
        card.setStatus("LOST");
        card.setLostReportedAt(LocalDateTime.now());
        cardRepository.save(card);
        
        recordStatusChange(card, previousStatus, "LOST", "Card reported lost", correlationId);
        securityService.blockCard(event.getCardId(), "LOST");
        replacementService.initiateReplacement(event.getCardId(), "LOST");
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Reported Lost",
            "Your card has been blocked and a replacement is being processed.",
            correlationId
        );
        
        metricsService.recordCardLost();
    }
    
    private void processCardStolenReported(CardLifecycleEvent event, String correlationId) {
        log.error("Card reported stolen: cardId={}, userId={}", event.getCardId(), event.getUserId());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        String previousStatus = card.getStatus();
        card.setStatus("STOLEN");
        card.setStolenReportedAt(LocalDateTime.now());
        cardRepository.save(card);
        
        recordStatusChange(card, previousStatus, "STOLEN", "Card reported stolen", correlationId);
        securityService.blockCard(event.getCardId(), "STOLEN");
        securityService.flagFraudulentTransactions(event.getCardId());
        replacementService.initiateReplacement(event.getCardId(), "STOLEN");
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Reported Stolen",
            "Your card has been blocked immediately. We're reviewing recent transactions for fraud.",
            correlationId
        );
        
        metricsService.recordCardStolen();
    }
    
    private void processCardDamagedReported(CardLifecycleEvent event, String correlationId) {
        log.info("Card reported damaged: cardId={}", event.getCardId());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        card.setDamageReported(true);
        card.setDamageReportedAt(LocalDateTime.now());
        cardRepository.save(card);
        
        replacementService.initiateReplacement(event.getCardId(), "DAMAGED");
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Replacement",
            "We're processing a replacement for your damaged card.",
            correlationId
        );
        
        metricsService.recordCardDamaged();
    }
    
    private void processCardReplacementRequested(CardLifecycleEvent event, String correlationId) {
        log.info("Card replacement requested: cardId={}, reason={}", 
            event.getCardId(), event.getReplacementReason());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        card.setReplacementRequested(true);
        card.setReplacementRequestedAt(LocalDateTime.now());
        card.setReplacementReason(event.getReplacementReason());
        cardRepository.save(card);
        
        replacementService.processReplacement(event.getCardId(), event.getReplacementReason());
        metricsService.recordReplacementRequested(event.getReplacementReason());
    }
    
    private void processCardExpired(CardLifecycleEvent event, String correlationId) {
        log.info("Card expired: cardId={}, expiryDate={}", 
            event.getCardId(), event.getExpiryDate());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        String previousStatus = card.getStatus();
        card.setStatus("EXPIRED");
        card.setExpiredAt(LocalDateTime.now());
        cardRepository.save(card);
        
        recordStatusChange(card, previousStatus, "EXPIRED", "Card expired", correlationId);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Expired",
            "Your card has expired. Please check for your renewal card.",
            correlationId
        );
        
        metricsService.recordCardExpired();
    }
    
    private void processCardRenewalInitiated(CardLifecycleEvent event, String correlationId) {
        log.info("Card renewal initiated: cardId={}, newCardId={}", 
            event.getCardId(), event.getNewCardId());
        
        Card oldCard = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        oldCard.setRenewalCardId(event.getNewCardId());
        oldCard.setRenewalInitiatedAt(LocalDateTime.now());
        cardRepository.save(oldCard);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Renewal",
            "Your replacement card is on the way!",
            correlationId
        );
        
        metricsService.recordCardRenewal();
    }
    
    private void processCardClosed(CardLifecycleEvent event, String correlationId) {
        log.info("Card closed: cardId={}, reason={}", event.getCardId(), event.getClosureReason());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        String previousStatus = card.getStatus();
        card.setStatus("CLOSED");
        card.setClosedAt(LocalDateTime.now());
        card.setClosureReason(event.getClosureReason());
        cardRepository.save(card);
        
        recordStatusChange(card, previousStatus, "CLOSED", event.getClosureReason(), correlationId);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Closed",
            "Your card has been permanently closed.",
            correlationId
        );
        
        metricsService.recordCardClosed(event.getClosureReason());
    }
    
    private void recordStatusChange(Card card, String fromStatus, String toStatus, String reason, String correlationId) {
        CardStatusHistory history = CardStatusHistory.builder()
            .id(UUID.randomUUID().toString())
            .cardId(card.getId())
            .fromStatus(fromStatus)
            .toStatus(toStatus)
            .reason(reason)
            .changedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        statusHistoryRepository.save(history);
    }
}