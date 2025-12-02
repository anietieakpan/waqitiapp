package com.waqiti.card.kafka;

import com.waqiti.common.events.VirtualCardEvent;
import com.waqiti.card.domain.VirtualCard;
import com.waqiti.card.domain.Card;
import com.waqiti.card.repository.VirtualCardRepository;
import com.waqiti.card.repository.CardRepository;
import com.waqiti.card.service.VirtualCardService;
import com.waqiti.card.service.TokenizationService;
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
public class VirtualCardEventsConsumer {
    
    private final VirtualCardRepository virtualCardRepository;
    private final CardRepository cardRepository;
    private final VirtualCardService virtualCardService;
    private final TokenizationService tokenizationService;
    private final SecurityService securityService;
    private final CardMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final int MAX_VIRTUAL_CARDS_PER_USER = 10;
    private static final int VIRTUAL_CARD_VALIDITY_MONTHS = 12;
    
    @KafkaListener(
        topics = {"virtual-card-events", "digital-card-events", "tokenized-card-events"},
        groupId = "virtual-card-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 90)
    public void handleVirtualCardEvent(
            @Payload VirtualCardEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("virtual-card-%s-p%d-o%d", 
            event.getVirtualCardId() != null ? event.getVirtualCardId() : event.getUserId(), 
            partition, offset);
        
        log.info("Processing virtual card event: virtualCardId={}, type={}, userId={}", 
            event.getVirtualCardId(), event.getEventType(), event.getUserId());
        
        try {
            switch (event.getEventType()) {
                case VIRTUAL_CARD_CREATION_REQUESTED:
                    processVirtualCardCreationRequested(event, correlationId);
                    break;
                case VIRTUAL_CARD_NUMBER_GENERATED:
                    processVirtualCardNumberGenerated(event, correlationId);
                    break;
                case VIRTUAL_CARD_TOKENIZED:
                    processVirtualCardTokenized(event, correlationId);
                    break;
                case VIRTUAL_CARD_ACTIVATED:
                    processVirtualCardActivated(event, correlationId);
                    break;
                case WALLET_PROVISIONED:
                    processWalletProvisioned(event, correlationId);
                    break;
                case TRANSACTION_LIMIT_SET:
                    processTransactionLimitSet(event, correlationId);
                    break;
                case MERCHANT_LOCKED:
                    processMerchantLocked(event, correlationId);
                    break;
                case SUBSCRIPTION_LINKED:
                    processSubscriptionLinked(event, correlationId);
                    break;
                case VIRTUAL_CARD_FROZEN:
                    processVirtualCardFrozen(event, correlationId);
                    break;
                case VIRTUAL_CARD_UNFROZEN:
                    processVirtualCardUnfrozen(event, correlationId);
                    break;
                case VIRTUAL_CARD_DELETED:
                    processVirtualCardDeleted(event, correlationId);
                    break;
                case VIRTUAL_CARD_EXPIRED:
                    processVirtualCardExpired(event, correlationId);
                    break;
                default:
                    log.warn("Unknown virtual card event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logCardEvent(
                "VIRTUAL_CARD_EVENT_PROCESSED",
                event.getVirtualCardId() != null ? event.getVirtualCardId() : "N/A",
                Map.of(
                    "eventType", event.getEventType(),
                    "userId", event.getUserId(),
                    "linkedCardId", event.getLinkedCardId() != null ? event.getLinkedCardId() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process virtual card event: {}", e.getMessage(), e);
            kafkaTemplate.send("virtual-card-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processVirtualCardCreationRequested(VirtualCardEvent event, String correlationId) {
        log.info("Virtual card creation requested: userId={}, purpose={}, linkedCardId={}", 
            event.getUserId(), event.getCardPurpose(), event.getLinkedCardId());
        
        long userVirtualCardCount = virtualCardRepository.countByUserIdAndStatus(
            event.getUserId(), "ACTIVE");
        
        if (userVirtualCardCount >= MAX_VIRTUAL_CARDS_PER_USER) {
            log.error("User exceeded maximum virtual cards: userId={}, current={}, max={}", 
                event.getUserId(), userVirtualCardCount, MAX_VIRTUAL_CARDS_PER_USER);
            virtualCardService.rejectCreationRequest(event.getUserId(), "MAX_CARDS_EXCEEDED");
            return;
        }
        
        Card linkedCard = null;
        if (event.getLinkedCardId() != null) {
            linkedCard = cardRepository.findById(event.getLinkedCardId())
                .orElseThrow();
            
            if (!"ACTIVE".equals(linkedCard.getStatus())) {
                log.error("Linked card not active: cardId={}, status={}", 
                    event.getLinkedCardId(), linkedCard.getStatus());
                return;
            }
        }
        
        VirtualCard virtualCard = VirtualCard.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .linkedCardId(event.getLinkedCardId())
            .cardPurpose(event.getCardPurpose())
            .cardNickname(event.getCardNickname())
            .requestedAt(LocalDateTime.now())
            .expiryDate(LocalDateTime.now().plusMonths(VIRTUAL_CARD_VALIDITY_MONTHS))
            .status("REQUESTED")
            .isSingleUse(event.isSingleUse())
            .correlationId(correlationId)
            .build();
        
        virtualCardRepository.save(virtualCard);
        
        virtualCardService.generateVirtualCardNumber(virtualCard.getId());
        
        metricsService.recordVirtualCardCreationRequested(event.getCardPurpose());
    }
    
    private void processVirtualCardNumberGenerated(VirtualCardEvent event, String correlationId) {
        log.info("Virtual card number generated: virtualCardId={}, last4={}", 
            event.getVirtualCardId(), event.getCardNumber().substring(event.getCardNumber().length() - 4));
        
        VirtualCard virtualCard = virtualCardRepository.findById(event.getVirtualCardId())
            .orElseThrow();
        
        String encryptedCardNumber = securityService.encryptCardNumber(event.getCardNumber());
        String encryptedCvv = securityService.encryptCvv(event.getCvv());
        
        virtualCard.setEncryptedCardNumber(encryptedCardNumber);
        virtualCard.setLast4Digits(event.getCardNumber().substring(event.getCardNumber().length() - 4));
        virtualCard.setEncryptedCvv(encryptedCvv);
        virtualCard.setExpiryMonth(event.getExpiryMonth());
        virtualCard.setExpiryYear(event.getExpiryYear());
        virtualCard.setCardNetwork(event.getCardNetwork());
        virtualCard.setNumberGeneratedAt(LocalDateTime.now());
        virtualCard.setStatus("NUMBER_GENERATED");
        virtualCardRepository.save(virtualCard);
        
        tokenizationService.tokenizeVirtualCard(virtualCard.getId());
        
        metricsService.recordVirtualCardNumberGenerated();
    }
    
    private void processVirtualCardTokenized(VirtualCardEvent event, String correlationId) {
        log.info("Virtual card tokenized: virtualCardId={}, tokenProvider={}, token={}", 
            event.getVirtualCardId(), event.getTokenProvider(), 
            event.getToken() != null ? event.getToken().substring(0, 8) + "..." : "N/A");
        
        VirtualCard virtualCard = virtualCardRepository.findById(event.getVirtualCardId())
            .orElseThrow();
        
        virtualCard.setTokenProvider(event.getTokenProvider());
        virtualCard.setToken(event.getToken());
        virtualCard.setTokenReferenceId(event.getTokenReferenceId());
        virtualCard.setTokenizedAt(LocalDateTime.now());
        virtualCard.setStatus("TOKENIZED");
        virtualCardRepository.save(virtualCard);
        
        virtualCardService.activateVirtualCard(virtualCard.getId());
        
        metricsService.recordVirtualCardTokenized(event.getTokenProvider());
    }
    
    private void processVirtualCardActivated(VirtualCardEvent event, String correlationId) {
        log.info("Virtual card activated: virtualCardId={}, userId={}", 
            event.getVirtualCardId(), event.getUserId());
        
        VirtualCard virtualCard = virtualCardRepository.findById(event.getVirtualCardId())
            .orElseThrow();
        
        virtualCard.setStatus("ACTIVE");
        virtualCard.setActivatedAt(LocalDateTime.now());
        virtualCard.setUsageCount(0);
        virtualCard.setTotalSpent(BigDecimal.ZERO);
        virtualCardRepository.save(virtualCard);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Virtual Card Ready",
            String.format("Your virtual card '%s' is ready to use. Card ending in %s. " +
                "You can add it to Apple Pay, Google Pay, or use it for online purchases.",
                virtualCard.getCardNickname(), virtualCard.getLast4Digits()),
            correlationId
        );
        
        metricsService.recordVirtualCardActivated(virtualCard.getCardPurpose());
    }
    
    private void processWalletProvisioned(VirtualCardEvent event, String correlationId) {
        log.info("Virtual card provisioned to wallet: virtualCardId={}, walletType={}, deviceId={}", 
            event.getVirtualCardId(), event.getWalletType(), event.getDeviceId());
        
        VirtualCard virtualCard = virtualCardRepository.findById(event.getVirtualCardId())
            .orElseThrow();
        
        virtualCard.setWalletType(event.getWalletType());
        virtualCard.setDeviceId(event.getDeviceId());
        virtualCard.setDeviceName(event.getDeviceName());
        virtualCard.setProvisionedAt(LocalDateTime.now());
        virtualCard.setWalletProvisioned(true);
        virtualCardRepository.save(virtualCard);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Card Added to Wallet",
            String.format("Your virtual card ending in %s has been added to %s on %s",
                virtualCard.getLast4Digits(), event.getWalletType(), event.getDeviceName()),
            correlationId
        );
        
        metricsService.recordWalletProvisioned(event.getWalletType());
    }
    
    private void processTransactionLimitSet(VirtualCardEvent event, String correlationId) {
        log.info("Transaction limit set: virtualCardId={}, limitType={}, amount={}", 
            event.getVirtualCardId(), event.getLimitType(), event.getLimitAmount());
        
        VirtualCard virtualCard = virtualCardRepository.findById(event.getVirtualCardId())
            .orElseThrow();
        
        switch (event.getLimitType()) {
            case "SINGLE_TRANSACTION":
                virtualCard.setSingleTransactionLimit(event.getLimitAmount());
                break;
            case "DAILY_SPENDING":
                virtualCard.setDailySpendingLimit(event.getLimitAmount());
                break;
            case "TOTAL_LIFETIME":
                virtualCard.setLifetimeSpendingLimit(event.getLimitAmount());
                break;
        }
        
        virtualCard.setLimitSetAt(LocalDateTime.now());
        virtualCardRepository.save(virtualCard);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Virtual Card Limit Set",
            String.format("%s limit set to %s for virtual card ending in %s",
                event.getLimitType().replace("_", " "), 
                event.getLimitAmount(), virtualCard.getLast4Digits()),
            correlationId
        );
        
        metricsService.recordTransactionLimitSet(event.getLimitType(), event.getLimitAmount());
    }
    
    private void processMerchantLocked(VirtualCardEvent event, String correlationId) {
        log.info("Merchant locked for virtual card: virtualCardId={}, merchantId={}, merchantName={}", 
            event.getVirtualCardId(), event.getMerchantId(), event.getMerchantName());
        
        VirtualCard virtualCard = virtualCardRepository.findById(event.getVirtualCardId())
            .orElseThrow();
        
        virtualCard.setMerchantLocked(true);
        virtualCard.setLockedMerchantId(event.getMerchantId());
        virtualCard.setLockedMerchantName(event.getMerchantName());
        virtualCard.setMerchantLockedAt(LocalDateTime.now());
        virtualCardRepository.save(virtualCard);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Virtual Card Merchant-Locked",
            String.format("Your virtual card ending in %s can now only be used at %s. " +
                "This prevents unauthorized use at other merchants.",
                virtualCard.getLast4Digits(), event.getMerchantName()),
            correlationId
        );
        
        metricsService.recordMerchantLocked(event.getMerchantName());
    }
    
    private void processSubscriptionLinked(VirtualCardEvent event, String correlationId) {
        log.info("Subscription linked to virtual card: virtualCardId={}, subscriptionId={}, service={}", 
            event.getVirtualCardId(), event.getSubscriptionId(), event.getSubscriptionService());
        
        VirtualCard virtualCard = virtualCardRepository.findById(event.getVirtualCardId())
            .orElseThrow();
        
        virtualCard.setLinkedSubscriptionId(event.getSubscriptionId());
        virtualCard.setSubscriptionService(event.getSubscriptionService());
        virtualCard.setSubscriptionAmount(event.getSubscriptionAmount());
        virtualCard.setSubscriptionLinkedAt(LocalDateTime.now());
        virtualCardRepository.save(virtualCard);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Subscription Linked",
            String.format("Your %s subscription (%s/month) has been linked to virtual card ending in %s",
                event.getSubscriptionService(), event.getSubscriptionAmount(), 
                virtualCard.getLast4Digits()),
            correlationId
        );
        
        metricsService.recordSubscriptionLinked(event.getSubscriptionService());
    }
    
    private void processVirtualCardFrozen(VirtualCardEvent event, String correlationId) {
        log.info("Virtual card frozen: virtualCardId={}, reason={}", 
            event.getVirtualCardId(), event.getFreezeReason());
        
        VirtualCard virtualCard = virtualCardRepository.findById(event.getVirtualCardId())
            .orElseThrow();
        
        virtualCard.setStatus("FROZEN");
        virtualCard.setFrozenAt(LocalDateTime.now());
        virtualCard.setFreezeReason(event.getFreezeReason());
        virtualCardRepository.save(virtualCard);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Virtual Card Frozen",
            String.format("Your virtual card ending in %s has been frozen. Reason: %s",
                virtualCard.getLast4Digits(), event.getFreezeReason()),
            correlationId
        );
        
        metricsService.recordVirtualCardFrozen(event.getFreezeReason());
    }
    
    private void processVirtualCardUnfrozen(VirtualCardEvent event, String correlationId) {
        log.info("Virtual card unfrozen: virtualCardId={}", event.getVirtualCardId());
        
        VirtualCard virtualCard = virtualCardRepository.findById(event.getVirtualCardId())
            .orElseThrow();
        
        virtualCard.setStatus("ACTIVE");
        virtualCard.setUnfrozenAt(LocalDateTime.now());
        virtualCardRepository.save(virtualCard);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Virtual Card Unfrozen",
            String.format("Your virtual card ending in %s is now active and ready to use",
                virtualCard.getLast4Digits()),
            correlationId
        );
        
        metricsService.recordVirtualCardUnfrozen();
    }
    
    private void processVirtualCardDeleted(VirtualCardEvent event, String correlationId) {
        log.info("Virtual card deleted: virtualCardId={}, userId={}, reason={}", 
            event.getVirtualCardId(), event.getUserId(), event.getDeletionReason());
        
        VirtualCard virtualCard = virtualCardRepository.findById(event.getVirtualCardId())
            .orElseThrow();
        
        virtualCard.setStatus("DELETED");
        virtualCard.setDeletedAt(LocalDateTime.now());
        virtualCard.setDeletionReason(event.getDeletionReason());
        virtualCardRepository.save(virtualCard);
        
        if (virtualCard.getWalletProvisioned()) {
            tokenizationService.deprovisionFromWallet(virtualCard.getId(), virtualCard.getWalletType());
        }
        
        notificationService.sendNotification(
            event.getUserId(),
            "Virtual Card Deleted",
            String.format("Your virtual card '%s' ending in %s has been permanently deleted",
                virtualCard.getCardNickname(), virtualCard.getLast4Digits()),
            correlationId
        );
        
        metricsService.recordVirtualCardDeleted(event.getDeletionReason());
    }
    
    private void processVirtualCardExpired(VirtualCardEvent event, String correlationId) {
        log.info("Virtual card expired: virtualCardId={}, expiryDate={}", 
            event.getVirtualCardId(), event.getExpiryDate());
        
        VirtualCard virtualCard = virtualCardRepository.findById(event.getVirtualCardId())
            .orElseThrow();
        
        virtualCard.setStatus("EXPIRED");
        virtualCard.setExpiredAt(LocalDateTime.now());
        virtualCardRepository.save(virtualCard);
        
        if (virtualCard.getWalletProvisioned()) {
            tokenizationService.deprovisionFromWallet(virtualCard.getId(), virtualCard.getWalletType());
        }
        
        notificationService.sendNotification(
            event.getUserId(),
            "Virtual Card Expired",
            String.format("Your virtual card '%s' ending in %s has expired. " +
                "Create a new virtual card if needed.",
                virtualCard.getCardNickname(), virtualCard.getLast4Digits()),
            correlationId
        );
        
        metricsService.recordVirtualCardExpired();
    }
}