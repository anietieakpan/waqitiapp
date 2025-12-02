package com.waqiti.wallet.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.wallet.service.WalletService;
import com.waqiti.wallet.service.WalletComplianceService;
import com.waqiti.wallet.entity.Wallet;
import com.waqiti.wallet.entity.WalletType;
import com.waqiti.wallet.entity.WalletStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Critical Event Consumer #258: Wallet Creation Event Consumer
 * Processes digital wallet provisioning with BSA/AML, OFAC, GDPR compliance
 * Implements 12-step zero-tolerance processing for wallet creation
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletCreationEventConsumer extends BaseKafkaConsumer {

    private final WalletService walletService;
    private final WalletComplianceService complianceService;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "wallet-creation-events", groupId = "wallet-creation-group")
    @CircuitBreaker(name = "wallet-creation-consumer")
    @Retry(name = "wallet-creation-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleWalletCreationEvent(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "wallet-creation-event");

        try {
            log.info("Step 1: Processing wallet creation event: partition={}, offset={}",
                    partition, offset);
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String userId = eventData.path("userId").asText();
            String walletType = eventData.path("walletType").asText();
            String currency = eventData.path("currency").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted wallet creation details: userId={}, walletType={}, currency={}", 
                    userId, walletType, currency);
            
            // Step 3: KYC validation with early return
            boolean kycValidated = complianceService.validateKYCLevel(userId, WalletType.valueOf(walletType.toUpperCase()));
            if (!kycValidated) {
                log.error("Step 3: KYC validation failed for userId={}", userId);
                walletService.rejectWalletCreation(eventId, "KYC_INSUFFICIENT", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: KYC validation successful");
            
            // Step 4: Wallet creation with tokenization
            Wallet wallet = walletService.createWallet(userId, WalletType.valueOf(walletType.toUpperCase()), 
                    currency, eventId, timestamp);
            log.info("Step 4: Wallet created: walletId={}, tokenizedAccount={}", 
                    wallet.getId(), wallet.getTokenizedAccountNumber());
            
            // Step 5: OFAC screening
            boolean ofacCleared = complianceService.performOFACScreening(userId, wallet.getId(), timestamp);
            if (!ofacCleared) {
                log.error("Step 5: OFAC screening failed");
                walletService.freezeWallet(wallet.getId(), "OFAC_MATCH", timestamp);
            } else {
                log.info("Step 5: OFAC screening passed");
            }
            
            // Step 6: AML risk assessment
            int amlRiskScore = complianceService.calculateAMLRiskScore(userId, walletType, timestamp);
            if (amlRiskScore > 750) {
                walletService.flagForEnhancedDueDiligence(wallet.getId(), amlRiskScore, timestamp);
            }
            log.info("Step 6: AML risk score calculated: {}", amlRiskScore);
            
            // Step 7: Digital wallet provisioning
            String provisioningResult = walletService.provisionDigitalWallet(wallet.getId(), timestamp);
            log.info("Step 7: Digital wallet provisioned: status={}", provisioningResult);
            
            // Step 8: PCI DSS compliant card linking
            if (WalletType.valueOf(walletType.toUpperCase()) == WalletType.PREMIUM) {
                walletService.enableCardLinking(wallet.getId(), timestamp);
                log.info("Step 8: Card linking enabled for premium wallet");
            } else {
                log.info("Step 8: Card linking not required for wallet type: {}", walletType);
            }
            
            // Step 9: GDPR consent tracking
            complianceService.recordGDPRConsent(userId, wallet.getId(), "WALLET_CREATION", timestamp);
            log.info("Step 9: GDPR consent recorded");
            
            // Step 10: Transaction limits setup
            walletService.setupTransactionLimits(wallet.getId(), WalletType.valueOf(walletType.toUpperCase()), timestamp);
            log.info("Step 10: Transaction limits configured");
            
            // Step 11: Audit trail creation
            complianceService.createAuditTrail(eventId, "WALLET_CREATED", wallet.getId(), userId, timestamp);
            log.info("Step 11: Audit trail created");
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed wallet creation: eventId={}, walletId={}", 
                    eventId, wallet.getId());
            
        } catch (Exception e) {
            log.error("Error processing wallet event: topic={}, partition={}, offset={}, error={}",
                    topic, partition, offset, e.getMessage(), e);

            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(result -> log.info("Wallet message sent to DLQ: topic={}, offset={}, destination={}, category={}",
                        topic, offset, result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for wallet event - MESSAGE MAY BE LOST! " +
                            "topic={}, partition={}, offset={}, error={}",
                            topic, partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Wallet event processing failed", e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("userId") || !eventData.has("walletType")) {
            throw new IllegalArgumentException("Invalid wallet creation event structure");
        }
    }
}