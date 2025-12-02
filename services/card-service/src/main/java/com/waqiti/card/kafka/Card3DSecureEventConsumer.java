package com.waqiti.card.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.card.service.ThreeDSecureService;
import com.waqiti.card.service.CardFraudService;
import com.waqiti.card.entity.ThreeDSecureAuthentication;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Critical Event Consumer #152: Card 3D Secure Event Consumer
 * Processes 3DS authentication challenges with EMVCo and PSD2 compliance
 * Implements 12-step zero-tolerance processing for strong customer authentication (SCA)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Card3DSecureEventConsumer extends BaseKafkaConsumer {

    private final ThreeDSecureService threeDSecureService;
    private final CardFraudService cardFraudService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "card-3ds-events", groupId = "card-3ds-group")
    @CircuitBreaker(name = "card-3ds-consumer")
    @Retry(name = "card-3ds-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCard3DSecureEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "card-3ds-event");
        
        try {
            log.info("Step 1: Processing 3DS authentication event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String authenticationId = eventData.path("authenticationId").asText();
            String cardId = eventData.path("cardId").asText();
            String merchantId = eventData.path("merchantId").asText();
            BigDecimal amount = new BigDecimal(eventData.path("amount").asText());
            String currency = eventData.path("currency").asText();
            String threeDSVersion = eventData.path("threeDSVersion").asText();
            String deviceChannel = eventData.path("deviceChannel").asText();
            String ipAddress = eventData.path("ipAddress").asText();
            String browserInfo = eventData.path("browserInfo").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted 3DS details: authId={}, cardId={}, version={}", 
                    authenticationId, cardId, threeDSVersion);
            
            ThreeDSecureAuthentication auth = threeDSecureService.createAuthenticationSession(
                    authenticationId, cardId, merchantId, amount, currency, 
                    threeDSVersion, timestamp);
            
            log.info("Step 3: Created 3DS authentication session");
            
            int riskScore = cardFraudService.calculateTransactionRisk(
                    cardId, merchantId, amount, ipAddress, timestamp);
            
            log.info("Step 4: Calculated fraud risk score: {}", riskScore);
            
            boolean scaRequired = threeDSecureService.determineSCARequirement(
                    amount, currency, riskScore, merchantId, timestamp);
            
            if (!scaRequired) {
                log.info("Step 5: SCA exemption applied, frictionless flow");
                threeDSecureService.completeFrictionlessAuthentication(authenticationId, timestamp);
                ack.acknowledge();
                return;
            }
            
            log.info("Step 5: SCA required, challenge flow initiated");
            
            String challengeMethod = threeDSecureService.selectChallengeMethod(
                    cardId, deviceChannel, riskScore, timestamp);
            
            log.info("Step 6: Selected challenge method: {}", challengeMethod);
            
            threeDSecureService.sendAuthenticationChallenge(
                    authenticationId, cardId, challengeMethod, timestamp);
            
            log.info("Step 7: Sent authentication challenge");
            
            boolean challengeResponse = threeDSecureService.waitForChallengeResponse(
                    authenticationId, 300000);
            
            if (challengeResponse) {
                threeDSecureService.verifyChallenge(authenticationId, timestamp);
                log.info("Step 8: Challenge verified successfully");
            } else {
                threeDSecureService.handleChallengeTimeout(authenticationId, timestamp);
                log.warn("Step 8: Challenge timeout, authentication failed");
                ack.acknowledge();
                return;
            }
            
            threeDSecureService.generateAuthenticationValue(authenticationId, timestamp);
            log.info("Step 9: Generated CAVV/AAV authentication value");
            
            threeDSecureService.recordPSD2Compliance(authenticationId, scaRequired, timestamp);
            log.info("Step 10: Recorded PSD2 compliance data");
            
            threeDSecureService.updateIssuerACS(cardId, authenticationId, "SUCCESS", timestamp);
            log.info("Step 11: Updated issuer ACS");
            
            threeDSecureService.archiveAuthentication(authenticationId, eventData.toString(), timestamp);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed 3DS authentication: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing 3DS authentication event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("authenticationId") || 
            !eventData.has("cardId") || !eventData.has("threeDSVersion")) {
            throw new IllegalArgumentException("Invalid 3DS authentication event structure");
        }
    }
}