package com.waqiti.business.consumer;

import com.waqiti.business.event.BusinessCardEvent;
import com.waqiti.business.service.BusinessCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Production-grade Kafka consumer for business card events
 * Handles card lifecycle, transactions, and limit management
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessCardEventConsumer {

    private final BusinessCardService businessCardService;

    @KafkaListener(topics = "business-card-events", groupId = "business-card-processor")
    public void processBusinessCardEvent(@Payload BusinessCardEvent event,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                       @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                       @Header(KafkaHeaders.OFFSET) long offset,
                                       Acknowledgment acknowledgment) {
        try {
            log.info("Processing business card event: {} for business: {} action: {}", 
                    event.getEventId(), event.getBusinessId(), event.getAction());
            
            // Validate event
            validateBusinessCardEvent(event);
            
            // Process based on action type
            switch (event.getAction()) {
                case "CREATED" -> handleCardCreated(event);
                case "ACTIVATED" -> handleCardActivated(event);
                case "DEACTIVATED" -> handleCardDeactivated(event);
                case "BLOCKED" -> handleCardBlocked(event);
                case "LIMIT_UPDATED" -> handleLimitUpdated(event);
                case "TRANSACTION" -> handleCardTransaction(event);
                default -> {
                    log.warn("Unknown business card action: {} for event: {}", event.getAction(), event.getEventId());
                    // Don't throw exception for unknown actions to avoid DLQ
                }
            }
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.info("Successfully processed business card event: {} action: {}", 
                    event.getEventId(), event.getAction());
            
        } catch (Exception e) {
            log.error("Failed to process business card event: {} error: {}", 
                    event.getEventId(), e.getMessage(), e);
            
            // Don't acknowledge - let retry mechanism handle it
            throw new RuntimeException("Business card event processing failed", e);
        }
    }

    private void validateBusinessCardEvent(BusinessCardEvent event) {
        if (event.getBusinessId() == null || event.getBusinessId().trim().isEmpty()) {
            throw new IllegalArgumentException("Business ID is required for business card event");
        }
        
        if (event.getCardId() == null || event.getCardId().trim().isEmpty()) {
            throw new IllegalArgumentException("Card ID is required for business card event");
        }
        
        if (event.getAction() == null || event.getAction().trim().isEmpty()) {
            throw new IllegalArgumentException("Action is required for business card event");
        }
    }

    private void handleCardCreated(BusinessCardEvent event) {
        try {
            log.info("Handling card creation: {} for business: {} holder: {}", 
                    event.getCardId(), event.getBusinessId(), event.getHolderName());
            
            businessCardService.processCardCreation(
                event.getBusinessId(),
                event.getCardId(),
                event.getHolderName(),
                event.getCurrentLimit(),
                event.getExpiryDate()
            );
            
            // Send welcome notification for new card
            businessCardService.sendCardWelcomeNotification(
                event.getBusinessId(),
                event.getCardId(),
                event.getHolderName()
            );
            
            log.info("Card creation processed successfully: {}", event.getCardId());
            
        } catch (Exception e) {
            log.error("Failed to handle card creation for {}: {}", event.getCardId(), e.getMessage(), e);
            throw new RuntimeException("Card creation processing failed", e);
        }
    }

    private void handleCardActivated(BusinessCardEvent event) {
        try {
            log.info("Handling card activation: {} for business: {}", 
                    event.getCardId(), event.getBusinessId());
            
            businessCardService.activateCard(event.getBusinessId(), event.getCardId());
            
            // Send activation confirmation
            businessCardService.sendCardActivationNotification(
                event.getBusinessId(),
                event.getCardId()
            );
            
            log.info("Card activation processed successfully: {}", event.getCardId());
            
        } catch (Exception e) {
            log.error("Failed to handle card activation for {}: {}", event.getCardId(), e.getMessage(), e);
            throw new RuntimeException("Card activation processing failed", e);
        }
    }

    private void handleCardDeactivated(BusinessCardEvent event) {
        try {
            log.info("Handling card deactivation: {} for business: {} reason: {}", 
                    event.getCardId(), event.getBusinessId(), event.getReason());
            
            businessCardService.deactivateCard(
                event.getBusinessId(),
                event.getCardId(),
                event.getReason()
            );
            
            // Send deactivation notification
            businessCardService.sendCardDeactivationNotification(
                event.getBusinessId(),
                event.getCardId(),
                event.getReason()
            );
            
            log.info("Card deactivation processed successfully: {}", event.getCardId());
            
        } catch (Exception e) {
            log.error("Failed to handle card deactivation for {}: {}", event.getCardId(), e.getMessage(), e);
            throw new RuntimeException("Card deactivation processing failed", e);
        }
    }

    private void handleCardBlocked(BusinessCardEvent event) {
        try {
            log.info("Handling card blocking: {} for business: {} reason: {}", 
                    event.getCardId(), event.getBusinessId(), event.getReason());
            
            businessCardService.blockCard(
                event.getBusinessId(),
                event.getCardId(),
                event.getReason()
            );
            
            // Send urgent security notification
            businessCardService.sendCardBlockNotification(
                event.getBusinessId(),
                event.getCardId(),
                event.getReason()
            );
            
            // Check if additional security measures needed
            businessCardService.assessSecurityIncident(
                event.getBusinessId(),
                event.getCardId(),
                event.getReason()
            );
            
            log.info("Card blocking processed successfully: {}", event.getCardId());
            
        } catch (Exception e) {
            log.error("Failed to handle card blocking for {}: {}", event.getCardId(), e.getMessage(), e);
            throw new RuntimeException("Card blocking processing failed", e);
        }
    }

    private void handleLimitUpdated(BusinessCardEvent event) {
        try {
            log.info("Handling limit update: {} for business: {} old: {} new: {}", 
                    event.getCardId(), event.getBusinessId(), event.getCurrentLimit(), event.getNewLimit());
            
            businessCardService.updateCardLimit(
                event.getBusinessId(),
                event.getCardId(),
                event.getCurrentLimit(),
                event.getNewLimit()
            );
            
            // Send limit update confirmation
            businessCardService.sendLimitUpdateNotification(
                event.getBusinessId(),
                event.getCardId(),
                event.getCurrentLimit(),
                event.getNewLimit()
            );
            
            // Check if limit increase requires additional approval tracking
            if (event.getNewLimit().compareTo(event.getCurrentLimit()) > 0) {
                businessCardService.trackLimitIncrease(
                    event.getBusinessId(),
                    event.getCardId(),
                    event.getNewLimit()
                );
            }
            
            log.info("Card limit update processed successfully: {}", event.getCardId());
            
        } catch (Exception e) {
            log.error("Failed to handle limit update for {}: {}", event.getCardId(), e.getMessage(), e);
            throw new RuntimeException("Card limit update processing failed", e);
        }
    }

    private void handleCardTransaction(BusinessCardEvent event) {
        try {
            log.info("Handling card transaction: {} card: {} amount: {} {} merchant: {}", 
                    event.getTransactionId(), event.getCardId(), event.getAmount(), 
                    event.getCurrency(), event.getMerchantName());
            
            // Process transaction for spend tracking
            businessCardService.processCardTransaction(
                event.getBusinessId(),
                event.getCardId(),
                event.getTransactionId(),
                event.getAmount(),
                event.getCurrency(),
                event.getMerchantName()
            );
            
            // Update spending analytics
            businessCardService.updateSpendingAnalytics(
                event.getBusinessId(),
                event.getCardId(),
                event.getAmount(),
                event.getCurrency(),
                event.getMerchantName()
            );
            
            // Check spending limits and patterns
            businessCardService.checkSpendingLimits(
                event.getBusinessId(),
                event.getCardId(),
                event.getAmount()
            );
            
            // Generate receipt if needed
            businessCardService.generateTransactionReceipt(
                event.getBusinessId(),
                event.getTransactionId(),
                event.getCardId()
            );
            
            log.info("Card transaction processed successfully: {}", event.getTransactionId());
            
        } catch (Exception e) {
            log.error("Failed to handle card transaction {}: {}", event.getTransactionId(), e.getMessage(), e);
            throw new RuntimeException("Card transaction processing failed", e);
        }
    }
}