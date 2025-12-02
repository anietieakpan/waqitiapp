package com.waqiti.wallet.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.wallet.service.WalletService;
import com.waqiti.wallet.service.WalletComplianceService;
import com.waqiti.wallet.service.WalletFreezeService;
import com.waqiti.wallet.entity.Wallet;
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
 * Critical Event Consumer #262: Wallet Freezing Event Consumer
 * Processes suspicious activity freezes with regulatory compliance
 * Implements 12-step zero-tolerance processing for account freezing
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletFreezingEventConsumer extends BaseKafkaConsumer {

    private final WalletService walletService;
    private final WalletComplianceService complianceService;
    private final WalletFreezeService freezeService;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "wallet-freezing-events", groupId = "wallet-freezing-group")
    @CircuitBreaker(name = "wallet-freezing-consumer")
    @Retry(name = "wallet-freezing-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleWalletFreezingEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "wallet-freezing-event");
        
        try {
            log.info("Step 1: Processing wallet freezing event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String walletId = eventData.path("walletId").asText();
            String freezeReason = eventData.path("freezeReason").asText();
            String triggerSource = eventData.path("triggerSource").asText();
            String riskScore = eventData.path("riskScore").asText();
            String suspiciousActivity = eventData.path("suspiciousActivity").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted freeze details: walletId={}, reason={}, source={}", 
                    walletId, freezeReason, triggerSource);
            
            // Step 3: Freeze authorization validation
            boolean authorized = freezeService.validateFreezeAuthorization(walletId, freezeReason, 
                    triggerSource, timestamp);
            if (!authorized) {
                log.error("Step 3: Freeze authorization failed for walletId={}", walletId);
                freezeService.rejectFreezeRequest(eventId, "UNAUTHORIZED_FREEZE", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: Freeze authorization validated");
            
            // Step 4: Pre-freeze wallet status verification
            Wallet wallet = walletService.getWalletById(walletId);
            boolean canFreeze = freezeService.canWalletBeFrezen(wallet, freezeReason, timestamp);
            if (!canFreeze) {
                log.error("Step 4: Wallet cannot be frozen in current state");
                freezeService.rejectFreezeRequest(eventId, "WALLET_STATE_INCOMPATIBLE", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 4: Wallet state verification completed");
            
            // Step 5: SAR filing for suspicious activity
            if ("SUSPICIOUS_ACTIVITY".equals(freezeReason)) {
                boolean sarFiled = complianceService.fileSAR(walletId, suspiciousActivity, 
                        riskScore, timestamp);
                if (sarFiled) {
                    log.info("Step 5: SAR filed for suspicious activity");
                } else {
                    log.warn("Step 5: SAR filing failed, proceeding with freeze");
                }
            } else {
                log.info("Step 5: No SAR filing required for freeze reason: {}", freezeReason);
            }
            
            // Step 6: Asset preservation before freeze
            BigDecimal currentBalance = walletService.getCurrentBalance(walletId);
            freezeService.preserveAssets(walletId, currentBalance, eventId, timestamp);
            log.info("Step 6: Assets preserved: balance={}", currentBalance);
            
            // Step 7: Execute wallet freeze with audit trail
            String freezeId = freezeService.executeWalletFreeze(walletId, freezeReason, 
                    triggerSource, suspiciousActivity, timestamp);
            log.info("Step 7: Wallet frozen successfully: freezeId={}", freezeId);
            
            // Step 8: Transaction blocking implementation
            freezeService.blockAllTransactions(walletId, freezeId, timestamp);
            log.info("Step 8: All transactions blocked for wallet");
            
            // Step 9: Regulatory notification requirements
            if ("OFAC_MATCH".equals(freezeReason) || "SANCTIONS".equals(freezeReason)) {
                complianceService.notifyRegulatoryAuthorities(walletId, freezeReason, timestamp);
                log.info("Step 9: Regulatory authorities notified");
            } else {
                log.info("Step 9: No regulatory notification required");
            }
            
            // Step 10: Customer notification with legal requirements
            freezeService.sendFreezeNotification(walletId, freezeReason, freezeId, timestamp);
            log.info("Step 10: Customer freeze notification sent");
            
            // Step 11: Legal hold implementation
            if ("COURT_ORDER".equals(freezeReason) || "LAW_ENFORCEMENT".equals(freezeReason)) {
                freezeService.implementLegalHold(walletId, freezeId, eventData.path("legalReference").asText(), 
                        timestamp);
                log.info("Step 11: Legal hold implemented");
            } else {
                log.info("Step 11: No legal hold required");
            }
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed wallet freeze: eventId={}, freezeId={}", 
                    eventId, freezeId);
            
        } catch (Exception e) {
            log.error("Error processing wallet freezing event: partition={}, offset={}, error={}",
                    record.partition(), record.offset(), e.getMessage(), e);

            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(result -> log.info("Wallet freezing message sent to DLQ: offset={}, destination={}, category={}",
                        record.offset(), result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for wallet freezing event - MESSAGE MAY BE LOST! " +
                            "partition={}, offset={}, error={}",
                            record.partition(), record.offset(), dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Wallet freezing event processing failed", e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("walletId") || 
            !eventData.has("freezeReason") || !eventData.has("triggerSource")) {
            throw new IllegalArgumentException("Invalid wallet freezing event structure");
        }
    }
}