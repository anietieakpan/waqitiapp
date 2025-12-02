package com.waqiti.card.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.card.service.CardActivationService;
import com.waqiti.card.service.CardSecurityService;
import com.waqiti.card.entity.Card;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Critical Event Consumer #100: Card Activation Event Consumer
 * Processes card activation requests with full PCI DSS compliance and fraud prevention
 * Implements 12-step zero-tolerance processing for secure card activation workflows
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardActivationEventConsumer extends BaseKafkaConsumer {

    private final CardActivationService cardActivationService;
    private final CardSecurityService cardSecurityService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "card-activation-events", groupId = "card-activation-group")
    @CircuitBreaker(name = "card-activation-consumer")
    @Retry(name = "card-activation-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCardActivationEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "card-activation-event");
        
        try {
            log.info("Step 1: Processing card activation event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String cardId = eventData.path("cardId").asText();
            String userId = eventData.path("userId").asText();
            String cardToken = eventData.path("cardToken").asText();
            String activationCode = eventData.path("activationCode").asText();
            String ipAddress = eventData.path("ipAddress").asText();
            String deviceFingerprint = eventData.path("deviceFingerprint").asText();
            String channel = eventData.path("channel").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted card activation details: cardId={}, userId={}, channel={}", 
                    cardId, userId, channel);
            
            // Step 3: Verify card exists and is in ISSUED status
            Card card = cardActivationService.getCard(cardId);
            
            if (!"ISSUED".equals(card.getStatus())) {
                log.error("Step 3: Card not in ISSUED status: {}, current status: {}", 
                        cardId, card.getStatus());
                throw new IllegalStateException("Card cannot be activated in current status");
            }
            
            // Step 4: Verify user owns the card
            if (!userId.equals(card.getUserId())) {
                log.error("Step 4: SECURITY VIOLATION - User {} attempted to activate card owned by {}", 
                        userId, card.getUserId());
                cardSecurityService.reportSecurityIncident(cardId, userId, "UNAUTHORIZED_ACTIVATION", timestamp);
                throw new SecurityException("Unauthorized card activation attempt");
            }
            
            // Step 5: Validate activation code
            boolean codeValid = cardActivationService.validateActivationCode(cardId, activationCode, timestamp);
            
            if (!codeValid) {
                log.warn("Step 5: Invalid activation code for card: {}", cardId);
                cardActivationService.incrementFailedActivationAttempts(cardId, timestamp);
                throw new IllegalArgumentException("Invalid activation code");
            }
            
            // Step 6: Perform device risk assessment
            String deviceRisk = cardSecurityService.assessDeviceRisk(deviceFingerprint, ipAddress, timestamp);
            
            if ("HIGH".equals(deviceRisk)) {
                log.warn("Step 6: High-risk device detected for activation: cardId={}", cardId);
                cardSecurityService.flagSuspiciousActivation(cardId, deviceFingerprint, ipAddress, timestamp);
            }
            
            // Step 7: Verify geolocation consistency
            cardSecurityService.verifyGeolocationConsistency(userId, ipAddress, timestamp);
            
            // Step 8: Activate card with PCI DSS compliance
            cardActivationService.activateCard(cardId, timestamp);
            
            log.info("Step 8: Card activated successfully: cardId={}", cardId);
            
            // Step 9: Generate and store CVV2 securely (encrypted)
            cardSecurityService.generateAndStoreCVV2(cardId, timestamp);
            
            log.info("Step 9: Generated secure CVV2 for card");
            
            // Step 10: Set initial spending limits
            cardActivationService.setInitialSpendingLimits(cardId, card.getCardType(), timestamp);
            
            // Step 11: Send activation confirmation notifications
            cardActivationService.sendActivationNotifications(userId, cardId, channel, timestamp);
            
            log.info("Step 11: Sent activation confirmations");
            
            // Step 12: Log activation for audit trail
            cardActivationService.logActivationEvent(cardId, userId, ipAddress, deviceFingerprint, 
                    channel, timestamp);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed card activation event: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing card activation event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("cardId") || 
            !eventData.has("userId") || !eventData.has("activationCode")) {
            throw new IllegalArgumentException("Invalid card activation event structure");
        }
    }
}