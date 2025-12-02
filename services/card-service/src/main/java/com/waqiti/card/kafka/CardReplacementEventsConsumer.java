package com.waqiti.card.kafka;

import com.waqiti.common.events.CardReplacementEvent;
import com.waqiti.card.domain.Card;
import com.waqiti.card.domain.CardReplacement;
import com.waqiti.card.repository.CardRepository;
import com.waqiti.card.repository.CardReplacementRepository;
import com.waqiti.card.service.CardIssuanceService;
import com.waqiti.card.service.CardManagementService;
import com.waqiti.card.service.ShippingService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class CardReplacementEventsConsumer {
    
    private final CardRepository cardRepository;
    private final CardReplacementRepository replacementRepository;
    private final CardIssuanceService issuanceService;
    private final CardManagementService cardManagementService;
    private final ShippingService shippingService;
    private final CardMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal REPLACEMENT_FEE_LOST = new BigDecimal("15.00");
    private static final BigDecimal REPLACEMENT_FEE_STOLEN = new BigDecimal("10.00");
    private static final BigDecimal REPLACEMENT_FEE_DAMAGED = new BigDecimal("5.00");
    private static final BigDecimal REPLACEMENT_FEE_EXPIRED = BigDecimal.ZERO;
    
    @KafkaListener(
        topics = {"card-replacement-events", "card-reissue-events", "damaged-card-events"},
        groupId = "card-replacement-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 120)
    public void handleCardReplacementEvent(
            @Payload CardReplacementEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("card-replacement-%s-p%d-o%d", 
            event.getOldCardId(), partition, offset);
        
        log.info("Processing card replacement event: oldCardId={}, type={}, reason={}", 
            event.getOldCardId(), event.getEventType(), event.getReplacementReason());
        
        try {
            switch (event.getEventType()) {
                case REPLACEMENT_REQUESTED:
                    processReplacementRequested(event, correlationId);
                    break;
                case OLD_CARD_BLOCKED:
                    processOldCardBlocked(event, correlationId);
                    break;
                case REPLACEMENT_FEE_CALCULATED:
                    processReplacementFeeCalculated(event, correlationId);
                    break;
                case REPLACEMENT_FEE_CHARGED:
                    processReplacementFeeCharged(event, correlationId);
                    break;
                case NEW_CARD_ISSUED:
                    processNewCardIssued(event, correlationId);
                    break;
                case NEW_CARD_SHIPPED:
                    processNewCardShipped(event, correlationId);
                    break;
                case NEW_CARD_DELIVERED:
                    processNewCardDelivered(event, correlationId);
                    break;
                case NEW_CARD_ACTIVATED:
                    processNewCardActivated(event, correlationId);
                    break;
                case OLD_CARD_DESTROYED:
                    processOldCardDestroyed(event, correlationId);
                    break;
                case REPLACEMENT_COMPLETED:
                    processReplacementCompleted(event, correlationId);
                    break;
                default:
                    log.warn("Unknown card replacement event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logCardEvent(
                "CARD_REPLACEMENT_EVENT_PROCESSED",
                event.getOldCardId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "userId", event.getUserId(),
                    "replacementReason", event.getReplacementReason(),
                    "newCardId", event.getNewCardId() != null ? event.getNewCardId() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process card replacement event: {}", e.getMessage(), e);
            kafkaTemplate.send("card-replacement-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processReplacementRequested(CardReplacementEvent event, String correlationId) {
        log.info("Card replacement requested: cardId={}, userId={}, reason={}", 
            event.getOldCardId(), event.getUserId(), event.getReplacementReason());
        
        Card oldCard = cardRepository.findById(event.getOldCardId())
            .orElseThrow();
        
        CardReplacement replacement = CardReplacement.builder()
            .id(UUID.randomUUID().toString())
            .oldCardId(event.getOldCardId())
            .userId(event.getUserId())
            .replacementReason(event.getReplacementReason())
            .urgency(event.getUrgency())
            .requestedAt(LocalDateTime.now())
            .status("REQUESTED")
            .oldCardLast4(oldCard.getLast4Digits())
            .oldCardNetwork(oldCard.getCardNetwork())
            .correlationId(correlationId)
            .build();
        
        replacementRepository.save(replacement);
        
        if ("LOST".equals(event.getReplacementReason()) || "STOLEN".equals(event.getReplacementReason())) {
            log.info("Immediately blocking card due to lost/stolen: cardId={}", event.getOldCardId());
            cardManagementService.blockCard(event.getOldCardId(), event.getReplacementReason());
        }
        
        cardManagementService.calculateReplacementFee(replacement.getId(), event.getReplacementReason());
        
        metricsService.recordReplacementRequested(event.getReplacementReason(), event.getUrgency());
    }
    
    private void processOldCardBlocked(CardReplacementEvent event, String correlationId) {
        log.info("Old card blocked: cardId={}, reason={}, blockedAt={}", 
            event.getOldCardId(), event.getReplacementReason(), event.getBlockedAt());
        
        Card oldCard = cardRepository.findById(event.getOldCardId())
            .orElseThrow();
        
        oldCard.setStatus("BLOCKED");
        oldCard.setBlockedAt(event.getBlockedAt());
        oldCard.setBlockReason(event.getReplacementReason());
        cardRepository.save(oldCard);
        
        CardReplacement replacement = replacementRepository
            .findByOldCardIdAndStatus(event.getOldCardId(), "REQUESTED")
            .orElseThrow();
        
        replacement.setOldCardBlockedAt(LocalDateTime.now());
        replacementRepository.save(replacement);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Blocked",
            String.format("Your card ending in %s has been blocked due to: %s. " +
                "A replacement card is being processed.",
                oldCard.getLast4Digits(), event.getReplacementReason()),
            correlationId
        );
        
        metricsService.recordCardBlocked(event.getReplacementReason());
    }
    
    private void processReplacementFeeCalculated(CardReplacementEvent event, String correlationId) {
        log.info("Replacement fee calculated: replacementId={}, reason={}, fee={}", 
            event.getReplacementId(), event.getReplacementReason(), event.getReplacementFee());
        
        CardReplacement replacement = replacementRepository.findById(event.getReplacementId())
            .orElseThrow();
        
        BigDecimal fee = calculateFee(event.getReplacementReason());
        
        replacement.setReplacementFee(fee);
        replacement.setFeeCalculatedAt(LocalDateTime.now());
        replacementRepository.save(replacement);
        
        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            notificationService.sendNotification(
                event.getUserId(),
                "Card Replacement Fee",
                String.format("A replacement fee of %s will be charged for your new card. " +
                    "The fee will be deducted from your account.",
                    fee),
                correlationId
            );
            
            cardManagementService.chargeReplacementFee(replacement.getId(), fee);
        } else {
            issuanceService.issueReplacementCard(replacement.getId());
        }
        
        metricsService.recordReplacementFeeCalculated(event.getReplacementReason(), fee);
    }
    
    private void processReplacementFeeCharged(CardReplacementEvent event, String correlationId) {
        log.info("Replacement fee charged: replacementId={}, fee={}, transactionId={}", 
            event.getReplacementId(), event.getReplacementFee(), event.getFeeTransactionId());
        
        CardReplacement replacement = replacementRepository.findById(event.getReplacementId())
            .orElseThrow();
        
        replacement.setFeeCharged(true);
        replacement.setFeeChargedAt(LocalDateTime.now());
        replacement.setFeeTransactionId(event.getFeeTransactionId());
        replacementRepository.save(replacement);
        
        issuanceService.issueReplacementCard(replacement.getId());
        
        metricsService.recordReplacementFeeCharged(event.getReplacementFee());
    }
    
    private void processNewCardIssued(CardReplacementEvent event, String correlationId) {
        log.info("New card issued: replacementId={}, newCardId={}, cardNumber={}", 
            event.getReplacementId(), event.getNewCardId(), event.getNewCardNumber());
        
        Card oldCard = cardRepository.findById(event.getOldCardId())
            .orElseThrow();
        
        Card newCard = Card.builder()
            .id(event.getNewCardId())
            .userId(event.getUserId())
            .cardNumber(event.getNewCardNumber())
            .last4Digits(event.getNewCardNumber().substring(event.getNewCardNumber().length() - 4))
            .cardType(oldCard.getCardType())
            .cardNetwork(oldCard.getCardNetwork())
            .expiryDate(LocalDateTime.now().plusYears(3))
            .cvv(event.getNewCvv())
            .status("INACTIVE")
            .issuedAt(LocalDateTime.now())
            .replacementForCardId(event.getOldCardId())
            .build();
        
        cardRepository.save(newCard);
        
        CardReplacement replacement = replacementRepository.findById(event.getReplacementId())
            .orElseThrow();
        
        replacement.setNewCardId(event.getNewCardId());
        replacement.setNewCardIssuedAt(LocalDateTime.now());
        replacement.setNewCardLast4(newCard.getLast4Digits());
        replacement.setStatus("NEW_CARD_ISSUED");
        replacementRepository.save(replacement);
        
        if ("EXPRESS".equals(replacement.getUrgency())) {
            shippingService.shipCard(event.getNewCardId(), "EXPRESS");
        } else {
            shippingService.shipCard(event.getNewCardId(), "STANDARD");
        }
        
        metricsService.recordNewCardIssued(newCard.getCardType());
    }
    
    private void processNewCardShipped(CardReplacementEvent event, String correlationId) {
        log.info("New card shipped: newCardId={}, trackingNumber={}, shippingMethod={}", 
            event.getNewCardId(), event.getTrackingNumber(), event.getShippingMethod());
        
        CardReplacement replacement = replacementRepository.findByNewCardId(event.getNewCardId())
            .orElseThrow();
        
        replacement.setNewCardShippedAt(LocalDateTime.now());
        replacement.setTrackingNumber(event.getTrackingNumber());
        replacement.setShippingMethod(event.getShippingMethod());
        replacement.setEstimatedDeliveryDate(event.getEstimatedDeliveryDate());
        replacement.setStatus("SHIPPED");
        replacementRepository.save(replacement);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Replacement Card Shipped",
            String.format("Your replacement card has been shipped via %s. " +
                "Tracking number: %s. Estimated delivery: %s",
                event.getShippingMethod(), event.getTrackingNumber(), event.getEstimatedDeliveryDate()),
            correlationId
        );
        
        metricsService.recordCardShipped(event.getShippingMethod());
    }
    
    private void processNewCardDelivered(CardReplacementEvent event, String correlationId) {
        log.info("New card delivered: newCardId={}, deliveredAt={}", 
            event.getNewCardId(), event.getDeliveredAt());
        
        CardReplacement replacement = replacementRepository.findByNewCardId(event.getNewCardId())
            .orElseThrow();
        
        replacement.setNewCardDeliveredAt(event.getDeliveredAt());
        replacement.setStatus("DELIVERED");
        replacementRepository.save(replacement);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Replacement Card Delivered",
            String.format("Your replacement card ending in %s has been delivered. " +
                "Please activate it in the app to start using it.",
                replacement.getNewCardLast4()),
            correlationId
        );
        
        metricsService.recordCardDelivered();
    }
    
    private void processNewCardActivated(CardReplacementEvent event, String correlationId) {
        log.info("New card activated: newCardId={}, activatedAt={}", 
            event.getNewCardId(), event.getActivatedAt());
        
        Card newCard = cardRepository.findById(event.getNewCardId())
            .orElseThrow();
        
        newCard.setStatus("ACTIVE");
        newCard.setActivatedAt(event.getActivatedAt());
        cardRepository.save(newCard);
        
        CardReplacement replacement = replacementRepository.findByNewCardId(event.getNewCardId())
            .orElseThrow();
        
        replacement.setNewCardActivatedAt(LocalDateTime.now());
        replacementRepository.save(replacement);
        
        cardManagementService.migrateCardSettings(event.getOldCardId(), event.getNewCardId());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Replacement Card Activated",
            String.format("Your replacement card ending in %s is now active. " +
                "Your old card ending in %s has been permanently disabled.",
                replacement.getNewCardLast4(), replacement.getOldCardLast4()),
            correlationId
        );
        
        metricsService.recordCardActivated(newCard.getCardType(), newCard.getCardNetwork());
    }
    
    private void processOldCardDestroyed(CardReplacementEvent event, String correlationId) {
        log.info("Old card destroyed: oldCardId={}, destroyedAt={}", 
            event.getOldCardId(), event.getDestroyedAt());
        
        Card oldCard = cardRepository.findById(event.getOldCardId())
            .orElseThrow();
        
        oldCard.setStatus("DESTROYED");
        oldCard.setDestroyedAt(event.getDestroyedAt());
        cardRepository.save(oldCard);
        
        metricsService.recordCardDestroyed();
    }
    
    private void processReplacementCompleted(CardReplacementEvent event, String correlationId) {
        log.info("Card replacement completed: replacementId={}, oldCardId={}, newCardId={}", 
            event.getReplacementId(), event.getOldCardId(), event.getNewCardId());
        
        CardReplacement replacement = replacementRepository.findById(event.getReplacementId())
            .orElseThrow();
        
        replacement.setCompletedAt(LocalDateTime.now());
        replacement.setStatus("COMPLETED");
        replacementRepository.save(replacement);
        
        long durationDays = java.time.Duration.between(
            replacement.getRequestedAt(), replacement.getCompletedAt()).toDays();
        
        log.info("Card replacement completed in {} days", durationDays);
        
        metricsService.recordReplacementCompleted(
            replacement.getReplacementReason(), 
            replacement.getUrgency(), 
            durationDays
        );
    }
    
    private BigDecimal calculateFee(String reason) {
        return switch (reason) {
            case "LOST" -> REPLACEMENT_FEE_LOST;
            case "STOLEN" -> REPLACEMENT_FEE_STOLEN;
            case "DAMAGED" -> REPLACEMENT_FEE_DAMAGED;
            case "EXPIRED", "EXPIRING_SOON" -> REPLACEMENT_FEE_EXPIRED;
            case "COMPROMISED" -> BigDecimal.ZERO;
            default -> REPLACEMENT_FEE_DAMAGED;
        };
    }
}