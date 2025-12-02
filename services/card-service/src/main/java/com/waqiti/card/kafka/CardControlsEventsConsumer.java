package com.waqiti.card.kafka;

import com.waqiti.common.events.CardControlsEvent;
import com.waqiti.card.domain.Card;
import com.waqiti.card.domain.CardControls;
import com.waqiti.card.repository.CardRepository;
import com.waqiti.card.repository.CardControlsRepository;
import com.waqiti.card.service.CardManagementService;
import com.waqiti.card.service.SecurityService;
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
public class CardControlsEventsConsumer {
    
    private final CardRepository cardRepository;
    private final CardControlsRepository controlsRepository;
    private final CardManagementService cardManagementService;
    private final SecurityService securityService;
    private final CardMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"card-controls-events", "card-security-settings-events", "spending-controls-events"},
        groupId = "card-controls-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1500, multiplier = 2.0, maxDelay = 15000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 60)
    public void handleCardControlsEvent(
            @Payload CardControlsEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("card-controls-%s-p%d-o%d", 
            event.getCardId(), partition, offset);
        
        log.info("Processing card controls event: cardId={}, type={}, control={}", 
            event.getCardId(), event.getEventType(), event.getControlType());
        
        try {
            switch (event.getEventType()) {
                case CONTROLS_INITIALIZED:
                    processControlsInitialized(event, correlationId);
                    break;
                case SPENDING_LIMIT_UPDATED:
                    processSpendingLimitUpdated(event, correlationId);
                    break;
                case MERCHANT_CATEGORY_BLOCKED:
                    processMerchantCategoryBlocked(event, correlationId);
                    break;
                case MERCHANT_CATEGORY_UNBLOCKED:
                    processMerchantCategoryUnblocked(event, correlationId);
                    break;
                case ONLINE_TRANSACTIONS_DISABLED:
                    processOnlineTransactionsDisabled(event, correlationId);
                    break;
                case ONLINE_TRANSACTIONS_ENABLED:
                    processOnlineTransactionsEnabled(event, correlationId);
                    break;
                case CONTACTLESS_DISABLED:
                    processContactlessDisabled(event, correlationId);
                    break;
                case CONTACTLESS_ENABLED:
                    processContactlessEnabled(event, correlationId);
                    break;
                case INTERNATIONAL_TRANSACTIONS_DISABLED:
                    processInternationalTransactionsDisabled(event, correlationId);
                    break;
                case INTERNATIONAL_TRANSACTIONS_ENABLED:
                    processInternationalTransactionsEnabled(event, correlationId);
                    break;
                case ATM_WITHDRAWALS_DISABLED:
                    processAtmWithdrawalsDisabled(event, correlationId);
                    break;
                case ATM_WITHDRAWALS_ENABLED:
                    processAtmWithdrawalsEnabled(event, correlationId);
                    break;
                case GEOGRAPHIC_RESTRICTION_ADDED:
                    processGeographicRestrictionAdded(event, correlationId);
                    break;
                case GEOGRAPHIC_RESTRICTION_REMOVED:
                    processGeographicRestrictionRemoved(event, correlationId);
                    break;
                case TRANSACTION_ALERTS_CONFIGURED:
                    processTransactionAlertsConfigured(event, correlationId);
                    break;
                case TEMPORARY_LOCK_APPLIED:
                    processTemporaryLockApplied(event, correlationId);
                    break;
                case TEMPORARY_LOCK_REMOVED:
                    processTemporaryLockRemoved(event, correlationId);
                    break;
                default:
                    log.warn("Unknown card controls event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logCardEvent(
                "CARD_CONTROLS_EVENT_PROCESSED",
                event.getCardId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "userId", event.getUserId(),
                    "controlType", event.getControlType() != null ? event.getControlType() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process card controls event: {}", e.getMessage(), e);
            kafkaTemplate.send("card-controls-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processControlsInitialized(CardControlsEvent event, String correlationId) {
        log.info("Card controls initialized: cardId={}, userId={}", 
            event.getCardId(), event.getUserId());
        
        CardControls controls = CardControls.builder()
            .id(UUID.randomUUID().toString())
            .cardId(event.getCardId())
            .userId(event.getUserId())
            .onlineTransactionsEnabled(true)
            .contactlessEnabled(true)
            .internationalTransactionsEnabled(true)
            .atmWithdrawalsEnabled(true)
            .transactionAlertsEnabled(true)
            .blockedMerchantCategories(new ArrayList<>())
            .allowedCountries(new ArrayList<>())
            .createdAt(LocalDateTime.now())
            .lastUpdated(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        controlsRepository.save(controls);
        
        metricsService.recordControlsInitialized();
    }
    
    private void processSpendingLimitUpdated(CardControlsEvent event, String correlationId) {
        log.info("Spending limit updated: cardId={}, limitType={}, oldLimit={}, newLimit={}", 
            event.getCardId(), event.getLimitType(), event.getOldLimit(), event.getNewLimit());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        switch (event.getLimitType()) {
            case "DAILY_SPENDING":
                card.setDailySpendingLimit(event.getNewLimit());
                break;
            case "MONTHLY_SPENDING":
                card.setMonthlySpendingLimit(event.getNewLimit());
                break;
            case "DAILY_ATM":
                card.setDailyAtmLimit(event.getNewLimit());
                break;
            case "SINGLE_TRANSACTION":
                card.setSingleTransactionLimit(event.getNewLimit());
                break;
        }
        
        cardRepository.save(card);
        
        CardControls controls = controlsRepository.findByCardId(event.getCardId())
            .orElseThrow();
        controls.setLastUpdated(LocalDateTime.now());
        controlsRepository.save(controls);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Spending Limit Updated",
            String.format("Your %s limit has been updated from %s to %s",
                event.getLimitType().replace("_", " "), event.getOldLimit(), event.getNewLimit()),
            correlationId
        );
        
        metricsService.recordSpendingLimitUpdated(event.getLimitType(), event.getOldLimit(), event.getNewLimit());
    }
    
    private void processMerchantCategoryBlocked(CardControlsEvent event, String correlationId) {
        log.info("Merchant category blocked: cardId={}, category={}", 
            event.getCardId(), event.getMerchantCategory());
        
        CardControls controls = controlsRepository.findByCardId(event.getCardId())
            .orElseThrow();
        
        if (!controls.getBlockedMerchantCategories().contains(event.getMerchantCategory())) {
            controls.getBlockedMerchantCategories().add(event.getMerchantCategory());
            controls.setLastUpdated(LocalDateTime.now());
            controlsRepository.save(controls);
        }
        
        notificationService.sendNotification(
            event.getUserId(),
            "Merchant Category Blocked",
            String.format("Transactions at %s merchants have been blocked on your card",
                event.getMerchantCategory()),
            correlationId
        );
        
        metricsService.recordMerchantCategoryBlocked(event.getMerchantCategory());
    }
    
    private void processMerchantCategoryUnblocked(CardControlsEvent event, String correlationId) {
        log.info("Merchant category unblocked: cardId={}, category={}", 
            event.getCardId(), event.getMerchantCategory());
        
        CardControls controls = controlsRepository.findByCardId(event.getCardId())
            .orElseThrow();
        
        controls.getBlockedMerchantCategories().remove(event.getMerchantCategory());
        controls.setLastUpdated(LocalDateTime.now());
        controlsRepository.save(controls);
        
        metricsService.recordMerchantCategoryUnblocked(event.getMerchantCategory());
    }
    
    private void processOnlineTransactionsDisabled(CardControlsEvent event, String correlationId) {
        log.info("Online transactions disabled: cardId={}", event.getCardId());
        
        CardControls controls = controlsRepository.findByCardId(event.getCardId())
            .orElseThrow();
        
        controls.setOnlineTransactionsEnabled(false);
        controls.setOnlineTransactionsDisabledAt(LocalDateTime.now());
        controls.setLastUpdated(LocalDateTime.now());
        controlsRepository.save(controls);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Online Transactions Disabled",
            "Online and e-commerce transactions have been disabled on your card for security",
            correlationId
        );
        
        metricsService.recordOnlineTransactionsDisabled();
    }
    
    private void processOnlineTransactionsEnabled(CardControlsEvent event, String correlationId) {
        log.info("Online transactions enabled: cardId={}", event.getCardId());
        
        CardControls controls = controlsRepository.findByCardId(event.getCardId())
            .orElseThrow();
        
        controls.setOnlineTransactionsEnabled(true);
        controls.setOnlineTransactionsEnabledAt(LocalDateTime.now());
        controls.setLastUpdated(LocalDateTime.now());
        controlsRepository.save(controls);
        
        metricsService.recordOnlineTransactionsEnabled();
    }
    
    private void processContactlessDisabled(CardControlsEvent event, String correlationId) {
        log.info("Contactless disabled: cardId={}", event.getCardId());
        
        CardControls controls = controlsRepository.findByCardId(event.getCardId())
            .orElseThrow();
        
        controls.setContactlessEnabled(false);
        controls.setContactlessDisabledAt(LocalDateTime.now());
        controls.setLastUpdated(LocalDateTime.now());
        controlsRepository.save(controls);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Contactless Payments Disabled",
            "Contactless payments have been disabled. You'll need to insert your card or enter PIN",
            correlationId
        );
        
        metricsService.recordContactlessDisabled();
    }
    
    private void processContactlessEnabled(CardControlsEvent event, String correlationId) {
        log.info("Contactless enabled: cardId={}", event.getCardId());
        
        CardControls controls = controlsRepository.findByCardId(event.getCardId())
            .orElseThrow();
        
        controls.setContactlessEnabled(true);
        controls.setContactlessEnabledAt(LocalDateTime.now());
        controls.setLastUpdated(LocalDateTime.now());
        controlsRepository.save(controls);
        
        metricsService.recordContactlessEnabled();
    }
    
    private void processInternationalTransactionsDisabled(CardControlsEvent event, String correlationId) {
        log.info("International transactions disabled: cardId={}", event.getCardId());
        
        CardControls controls = controlsRepository.findByCardId(event.getCardId())
            .orElseThrow();
        
        controls.setInternationalTransactionsEnabled(false);
        controls.setInternationalDisabledAt(LocalDateTime.now());
        controls.setLastUpdated(LocalDateTime.now());
        controlsRepository.save(controls);
        
        notificationService.sendNotification(
            event.getUserId(),
            "International Transactions Disabled",
            "International transactions have been disabled. Only domestic transactions are allowed",
            correlationId
        );
        
        metricsService.recordInternationalTransactionsDisabled();
    }
    
    private void processInternationalTransactionsEnabled(CardControlsEvent event, String correlationId) {
        log.info("International transactions enabled: cardId={}", event.getCardId());
        
        CardControls controls = controlsRepository.findByCardId(event.getCardId())
            .orElseThrow();
        
        controls.setInternationalTransactionsEnabled(true);
        controls.setInternationalEnabledAt(LocalDateTime.now());
        controls.setLastUpdated(LocalDateTime.now());
        controlsRepository.save(controls);
        
        metricsService.recordInternationalTransactionsEnabled();
    }
    
    private void processAtmWithdrawalsDisabled(CardControlsEvent event, String correlationId) {
        log.info("ATM withdrawals disabled: cardId={}", event.getCardId());
        
        CardControls controls = controlsRepository.findByCardId(event.getCardId())
            .orElseThrow();
        
        controls.setAtmWithdrawalsEnabled(false);
        controls.setAtmDisabledAt(LocalDateTime.now());
        controls.setLastUpdated(LocalDateTime.now());
        controlsRepository.save(controls);
        
        notificationService.sendNotification(
            event.getUserId(),
            "ATM Withdrawals Disabled",
            "ATM withdrawals have been disabled on your card for security",
            correlationId
        );
        
        metricsService.recordAtmWithdrawalsDisabled();
    }
    
    private void processAtmWithdrawalsEnabled(CardControlsEvent event, String correlationId) {
        log.info("ATM withdrawals enabled: cardId={}", event.getCardId());
        
        CardControls controls = controlsRepository.findByCardId(event.getCardId())
            .orElseThrow();
        
        controls.setAtmWithdrawalsEnabled(true);
        controls.setAtmEnabledAt(LocalDateTime.now());
        controls.setLastUpdated(LocalDateTime.now());
        controlsRepository.save(controls);
        
        metricsService.recordAtmWithdrawalsEnabled();
    }
    
    private void processGeographicRestrictionAdded(CardControlsEvent event, String correlationId) {
        log.info("Geographic restriction added: cardId={}, countries={}", 
            event.getCardId(), event.getAllowedCountries());
        
        CardControls controls = controlsRepository.findByCardId(event.getCardId())
            .orElseThrow();
        
        controls.setGeographicRestrictionsEnabled(true);
        controls.setAllowedCountries(event.getAllowedCountries());
        controls.setLastUpdated(LocalDateTime.now());
        controlsRepository.save(controls);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Geographic Restrictions Applied",
            String.format("Your card can now only be used in: %s", 
                String.join(", ", event.getAllowedCountries())),
            correlationId
        );
        
        metricsService.recordGeographicRestrictionAdded(event.getAllowedCountries().size());
    }
    
    private void processGeographicRestrictionRemoved(CardControlsEvent event, String correlationId) {
        log.info("Geographic restriction removed: cardId={}", event.getCardId());
        
        CardControls controls = controlsRepository.findByCardId(event.getCardId())
            .orElseThrow();
        
        controls.setGeographicRestrictionsEnabled(false);
        controls.setAllowedCountries(new ArrayList<>());
        controls.setLastUpdated(LocalDateTime.now());
        controlsRepository.save(controls);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Geographic Restrictions Removed",
            "Your card can now be used worldwide",
            correlationId
        );
        
        metricsService.recordGeographicRestrictionRemoved();
    }
    
    private void processTransactionAlertsConfigured(CardControlsEvent event, String correlationId) {
        log.info("Transaction alerts configured: cardId={}, alertTypes={}, threshold={}", 
            event.getCardId(), event.getAlertTypes(), event.getAlertThreshold());
        
        CardControls controls = controlsRepository.findByCardId(event.getCardId())
            .orElseThrow();
        
        controls.setTransactionAlertsEnabled(true);
        controls.setAlertTypes(event.getAlertTypes());
        controls.setAlertThreshold(event.getAlertThreshold());
        controls.setLastUpdated(LocalDateTime.now());
        controlsRepository.save(controls);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Transaction Alerts Configured",
            String.format("You'll receive alerts for: %s (threshold: %s)",
                String.join(", ", event.getAlertTypes()), event.getAlertThreshold()),
            correlationId
        );
        
        metricsService.recordTransactionAlertsConfigured(event.getAlertTypes().size());
    }
    
    private void processTemporaryLockApplied(CardControlsEvent event, String correlationId) {
        log.info("Temporary lock applied: cardId={}, reason={}, duration={}", 
            event.getCardId(), event.getLockReason(), event.getLockDuration());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        card.setTemporarilyLocked(true);
        card.setLockedAt(LocalDateTime.now());
        card.setLockExpiresAt(LocalDateTime.now().plusMinutes(event.getLockDuration()));
        cardRepository.save(card);
        
        CardControls controls = controlsRepository.findByCardId(event.getCardId())
            .orElseThrow();
        controls.setTemporaryLockActive(true);
        controls.setLastUpdated(LocalDateTime.now());
        controlsRepository.save(controls);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Temporarily Locked",
            String.format("Your card has been temporarily locked for %d minutes. Reason: %s",
                event.getLockDuration(), event.getLockReason()),
            correlationId
        );
        
        metricsService.recordTemporaryLockApplied(event.getLockReason());
    }
    
    private void processTemporaryLockRemoved(CardControlsEvent event, String correlationId) {
        log.info("Temporary lock removed: cardId={}", event.getCardId());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        card.setTemporarilyLocked(false);
        card.setUnlockedAt(LocalDateTime.now());
        cardRepository.save(card);
        
        CardControls controls = controlsRepository.findByCardId(event.getCardId())
            .orElseThrow();
        controls.setTemporaryLockActive(false);
        controls.setLastUpdated(LocalDateTime.now());
        controlsRepository.save(controls);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Unlocked",
            "Your card has been unlocked and is ready to use",
            correlationId
        );
        
        metricsService.recordTemporaryLockRemoved();
    }
}