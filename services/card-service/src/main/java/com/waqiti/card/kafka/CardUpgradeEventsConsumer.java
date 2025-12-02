package com.waqiti.card.kafka;

import com.waqiti.common.events.CardUpgradeEvent;
import com.waqiti.card.domain.Card;
import com.waqiti.card.domain.CardUpgrade;
import com.waqiti.card.repository.CardRepository;
import com.waqiti.card.repository.CardUpgradeRepository;
import com.waqiti.card.service.CardIssuanceService;
import com.waqiti.card.service.CardManagementService;
import com.waqiti.card.service.EligibilityService;
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
public class CardUpgradeEventsConsumer {
    
    private final CardRepository cardRepository;
    private final CardUpgradeRepository upgradeRepository;
    private final CardIssuanceService issuanceService;
    private final CardManagementService cardManagementService;
    private final EligibilityService eligibilityService;
    private final CardMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal PLATINUM_ANNUAL_FEE = new BigDecimal("95.00");
    private static final BigDecimal GOLD_ANNUAL_FEE = new BigDecimal("45.00");
    private static final BigDecimal SILVER_ANNUAL_FEE = BigDecimal.ZERO;
    
    @KafkaListener(
        topics = {"card-upgrade-events", "card-tier-change-events", "premium-card-events"},
        groupId = "card-upgrade-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 120)
    public void handleCardUpgradeEvent(
            @Payload CardUpgradeEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("card-upgrade-%s-p%d-o%d", 
            event.getCurrentCardId(), partition, offset);
        
        log.info("Processing card upgrade event: currentCardId={}, type={}, fromTier={}, toTier={}", 
            event.getCurrentCardId(), event.getEventType(), event.getFromTier(), event.getToTier());
        
        try {
            switch (event.getEventType()) {
                case UPGRADE_OFFERED:
                    processUpgradeOffered(event, correlationId);
                    break;
                case UPGRADE_REQUESTED:
                    processUpgradeRequested(event, correlationId);
                    break;
                case ELIGIBILITY_CHECKED:
                    processEligibilityChecked(event, correlationId);
                    break;
                case UPGRADE_APPROVED:
                    processUpgradeApproved(event, correlationId);
                    break;
                case UPGRADE_DECLINED:
                    processUpgradeDeclined(event, correlationId);
                    break;
                case ANNUAL_FEE_CHARGED:
                    processAnnualFeeCharged(event, correlationId);
                    break;
                case NEW_CARD_ISSUED:
                    processNewCardIssued(event, correlationId);
                    break;
                case BENEFITS_ACTIVATED:
                    processBenefitsActivated(event, correlationId);
                    break;
                case LIMITS_INCREASED:
                    processLimitsIncreased(event, correlationId);
                    break;
                case UPGRADE_COMPLETED:
                    processUpgradeCompleted(event, correlationId);
                    break;
                default:
                    log.warn("Unknown card upgrade event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logCardEvent(
                "CARD_UPGRADE_EVENT_PROCESSED",
                event.getCurrentCardId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "userId", event.getUserId(),
                    "fromTier", event.getFromTier(),
                    "toTier", event.getToTier() != null ? event.getToTier() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process card upgrade event: {}", e.getMessage(), e);
            kafkaTemplate.send("card-upgrade-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processUpgradeOffered(CardUpgradeEvent event, String correlationId) {
        log.info("Card upgrade offered: userId={}, currentTier={}, offeredTier={}", 
            event.getUserId(), event.getFromTier(), event.getToTier());
        
        CardUpgrade upgrade = CardUpgrade.builder()
            .id(UUID.randomUUID().toString())
            .currentCardId(event.getCurrentCardId())
            .userId(event.getUserId())
            .fromTier(event.getFromTier())
            .toTier(event.getToTier())
            .upgradeType("OFFERED")
            .offeredAt(LocalDateTime.now())
            .offerExpiresAt(LocalDateTime.now().plusDays(30))
            .status("OFFERED")
            .correlationId(correlationId)
            .build();
        
        upgradeRepository.save(upgrade);
        
        Map<String, Object> benefits = getBenefitsForTier(event.getToTier());
        BigDecimal annualFee = getAnnualFee(event.getToTier());
        
        notificationService.sendNotification(
            event.getUserId(),
            String.format("Exclusive %s Card Upgrade Offer", event.getToTier()),
            String.format("Congratulations! You're eligible to upgrade to our %s card. " +
                "Benefits include: %s. Annual fee: %s. Offer valid for 30 days.",
                event.getToTier(), benefits.get("summary"), annualFee),
            correlationId
        );
        
        metricsService.recordUpgradeOffered(event.getFromTier(), event.getToTier());
    }
    
    private void processUpgradeRequested(CardUpgradeEvent event, String correlationId) {
        log.info("Card upgrade requested: userId={}, currentTier={}, requestedTier={}", 
            event.getUserId(), event.getFromTier(), event.getToTier());
        
        CardUpgrade upgrade = CardUpgrade.builder()
            .id(UUID.randomUUID().toString())
            .currentCardId(event.getCurrentCardId())
            .userId(event.getUserId())
            .fromTier(event.getFromTier())
            .toTier(event.getToTier())
            .upgradeType("REQUESTED")
            .requestedAt(LocalDateTime.now())
            .status("REQUESTED")
            .correlationId(correlationId)
            .build();
        
        upgradeRepository.save(upgrade);
        
        eligibilityService.checkUpgradeEligibility(upgrade.getId());
        
        metricsService.recordUpgradeRequested(event.getFromTier(), event.getToTier());
    }
    
    private void processEligibilityChecked(CardUpgradeEvent event, String correlationId) {
        log.info("Eligibility checked: upgradeId={}, eligible={}, criteria={}", 
            event.getUpgradeId(), event.isEligible(), event.getEligibilityCriteria());
        
        CardUpgrade upgrade = upgradeRepository.findById(event.getUpgradeId())
            .orElseThrow();
        
        upgrade.setEligibilityCheckedAt(LocalDateTime.now());
        upgrade.setEligible(event.isEligible());
        upgrade.setEligibilityCriteria(event.getEligibilityCriteria());
        upgrade.setEligibilityNotes(event.getEligibilityNotes());
        
        if (event.isEligible()) {
            upgrade.setStatus("ELIGIBLE");
            upgradeRepository.save(upgrade);
            
            BigDecimal annualFee = getAnnualFee(event.getToTier());
            
            notificationService.sendNotification(
                event.getUserId(),
                "Card Upgrade Approved",
                String.format("Great news! You're eligible to upgrade to %s tier. " +
                    "Annual fee: %s. Would you like to proceed?",
                    event.getToTier(), annualFee),
                correlationId
            );
        } else {
            upgrade.setStatus("NOT_ELIGIBLE");
            upgradeRepository.save(upgrade);
            
            notificationService.sendNotification(
                event.getUserId(),
                "Card Upgrade Status",
                String.format("We're unable to approve your upgrade to %s at this time. %s",
                    event.getToTier(), event.getEligibilityNotes()),
                correlationId
            );
        }
        
        metricsService.recordEligibilityChecked(event.isEligible());
    }
    
    private void processUpgradeApproved(CardUpgradeEvent event, String correlationId) {
        log.info("Card upgrade approved: upgradeId={}, toTier={}, approvedAt={}", 
            event.getUpgradeId(), event.getToTier(), event.getApprovedAt());
        
        CardUpgrade upgrade = upgradeRepository.findById(event.getUpgradeId())
            .orElseThrow();
        
        upgrade.setApprovedAt(event.getApprovedAt());
        upgrade.setStatus("APPROVED");
        upgradeRepository.save(upgrade);
        
        BigDecimal annualFee = getAnnualFee(event.getToTier());
        
        if (annualFee.compareTo(BigDecimal.ZERO) > 0) {
            cardManagementService.chargeAnnualFee(upgrade.getId(), annualFee);
        } else {
            issuanceService.issueUpgradedCard(upgrade.getId());
        }
        
        metricsService.recordUpgradeApproved(event.getFromTier(), event.getToTier());
    }
    
    private void processUpgradeDeclined(CardUpgradeEvent event, String correlationId) {
        log.info("Card upgrade declined: upgradeId={}, declinedBy={}, reason={}", 
            event.getUpgradeId(), event.getDeclinedBy(), event.getDeclineReason());
        
        CardUpgrade upgrade = upgradeRepository.findById(event.getUpgradeId())
            .orElseThrow();
        
        upgrade.setDeclinedAt(LocalDateTime.now());
        upgrade.setDeclinedBy(event.getDeclinedBy());
        upgrade.setDeclineReason(event.getDeclineReason());
        upgrade.setStatus("DECLINED");
        upgradeRepository.save(upgrade);
        
        metricsService.recordUpgradeDeclined(event.getDeclineReason());
    }
    
    private void processAnnualFeeCharged(CardUpgradeEvent event, String correlationId) {
        log.info("Annual fee charged: upgradeId={}, fee={}, transactionId={}", 
            event.getUpgradeId(), event.getAnnualFee(), event.getFeeTransactionId());
        
        CardUpgrade upgrade = upgradeRepository.findById(event.getUpgradeId())
            .orElseThrow();
        
        upgrade.setAnnualFee(event.getAnnualFee());
        upgrade.setAnnualFeeCharged(true);
        upgrade.setAnnualFeeChargedAt(LocalDateTime.now());
        upgrade.setFeeTransactionId(event.getFeeTransactionId());
        upgradeRepository.save(upgrade);
        
        issuanceService.issueUpgradedCard(upgrade.getId());
        
        metricsService.recordAnnualFeeCharged(event.getAnnualFee());
    }
    
    private void processNewCardIssued(CardUpgradeEvent event, String correlationId) {
        log.info("New upgraded card issued: upgradeId={}, newCardId={}, newTier={}", 
            event.getUpgradeId(), event.getNewCardId(), event.getToTier());
        
        Card currentCard = cardRepository.findById(event.getCurrentCardId())
            .orElseThrow();
        
        Card newCard = Card.builder()
            .id(event.getNewCardId())
            .userId(event.getUserId())
            .cardNumber(event.getNewCardNumber())
            .last4Digits(event.getNewCardNumber().substring(event.getNewCardNumber().length() - 4))
            .cardType(event.getToTier())
            .cardNetwork(currentCard.getCardNetwork())
            .expiryDate(LocalDateTime.now().plusYears(3))
            .cvv(event.getNewCvv())
            .status("INACTIVE")
            .issuedAt(LocalDateTime.now())
            .upgradedFromCardId(event.getCurrentCardId())
            .isPremium(isPremiumTier(event.getToTier()))
            .build();
        
        cardRepository.save(newCard);
        
        CardUpgrade upgrade = upgradeRepository.findById(event.getUpgradeId())
            .orElseThrow();
        
        upgrade.setNewCardId(event.getNewCardId());
        upgrade.setNewCardIssuedAt(LocalDateTime.now());
        upgrade.setStatus("NEW_CARD_ISSUED");
        upgradeRepository.save(upgrade);
        
        cardManagementService.shipCard(event.getNewCardId());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Upgraded Card Issued",
            String.format("Your new %s card has been issued and will be shipped soon. " +
                "You'll receive tracking information shortly.",
                event.getToTier()),
            correlationId
        );
        
        metricsService.recordNewCardIssued(event.getToTier());
    }
    
    private void processBenefitsActivated(CardUpgradeEvent event, String correlationId) {
        log.info("Benefits activated: upgradeId={}, newCardId={}, benefits={}", 
            event.getUpgradeId(), event.getNewCardId(), event.getActivatedBenefits());
        
        CardUpgrade upgrade = upgradeRepository.findById(event.getUpgradeId())
            .orElseThrow();
        
        upgrade.setBenefitsActivatedAt(LocalDateTime.now());
        upgrade.setActivatedBenefits(event.getActivatedBenefits());
        upgradeRepository.save(upgrade);
        
        cardManagementService.enablePremiumBenefits(event.getNewCardId(), event.getActivatedBenefits());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Premium Benefits Activated",
            String.format("Your %s benefits are now active: %s",
                event.getToTier(), String.join(", ", event.getActivatedBenefits())),
            correlationId
        );
        
        metricsService.recordBenefitsActivated(event.getToTier());
    }
    
    private void processLimitsIncreased(CardUpgradeEvent event, String correlationId) {
        log.info("Card limits increased: newCardId={}, oldLimit={}, newLimit={}", 
            event.getNewCardId(), event.getOldSpendingLimit(), event.getNewSpendingLimit());
        
        Card newCard = cardRepository.findById(event.getNewCardId())
            .orElseThrow();
        
        newCard.setDailySpendingLimit(event.getNewSpendingLimit());
        newCard.setMonthlySpendingLimit(event.getNewSpendingLimit().multiply(new BigDecimal("30")));
        newCard.setDailyAtmLimit(event.getNewAtmLimit());
        cardRepository.save(newCard);
        
        CardUpgrade upgrade = upgradeRepository.findByNewCardId(event.getNewCardId())
            .orElseThrow();
        
        upgrade.setOldSpendingLimit(event.getOldSpendingLimit());
        upgrade.setNewSpendingLimit(event.getNewSpendingLimit());
        upgrade.setLimitsIncreasedAt(LocalDateTime.now());
        upgradeRepository.save(upgrade);
        
        metricsService.recordLimitsIncreased(event.getOldSpendingLimit(), event.getNewSpendingLimit());
    }
    
    private void processUpgradeCompleted(CardUpgradeEvent event, String correlationId) {
        log.info("Card upgrade completed: upgradeId={}, fromTier={}, toTier={}", 
            event.getUpgradeId(), event.getFromTier(), event.getToTier());
        
        CardUpgrade upgrade = upgradeRepository.findById(event.getUpgradeId())
            .orElseThrow();
        
        upgrade.setCompletedAt(LocalDateTime.now());
        upgrade.setStatus("COMPLETED");
        upgradeRepository.save(upgrade);
        
        Card currentCard = cardRepository.findById(event.getCurrentCardId())
            .orElseThrow();
        currentCard.setStatus("REPLACED");
        currentCard.setReplacedAt(LocalDateTime.now());
        currentCard.setReplacedByCardId(event.getNewCardId());
        cardRepository.save(currentCard);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Upgrade Complete",
            String.format("Congratulations! Your upgrade from %s to %s is complete. " +
                "Enjoy your enhanced benefits and higher limits.",
                event.getFromTier(), event.getToTier()),
            correlationId
        );
        
        long durationDays = java.time.Duration.between(
            upgrade.getRequestedAt(), upgrade.getCompletedAt()).toDays();
        
        metricsService.recordUpgradeCompleted(event.getFromTier(), event.getToTier(), durationDays);
    }
    
    private Map<String, Object> getBenefitsForTier(String tier) {
        return switch (tier) {
            case "PLATINUM" -> Map.of(
                "summary", "Airport lounge access, travel insurance, concierge service, 3% cashback",
                "cashbackRate", 0.03,
                "loungeAccess", true,
                "travelInsurance", true
            );
            case "GOLD" -> Map.of(
                "summary", "Travel insurance, 2% cashback, extended warranty",
                "cashbackRate", 0.02,
                "travelInsurance", true,
                "extendedWarranty", true
            );
            case "SILVER" -> Map.of(
                "summary", "1% cashback, fraud protection",
                "cashbackRate", 0.01,
                "fraudProtection", true
            );
            default -> Map.of("summary", "Basic benefits");
        };
    }
    
    private BigDecimal getAnnualFee(String tier) {
        return switch (tier) {
            case "PLATINUM" -> PLATINUM_ANNUAL_FEE;
            case "GOLD" -> GOLD_ANNUAL_FEE;
            case "SILVER" -> SILVER_ANNUAL_FEE;
            default -> BigDecimal.ZERO;
        };
    }
    
    private boolean isPremiumTier(String tier) {
        return "PLATINUM".equals(tier) || "GOLD".equals(tier);
    }
}