package com.waqiti.wallet.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.wallet.service.WalletService;
import com.waqiti.wallet.service.WalletComplianceService;
import com.waqiti.wallet.domain.Transaction;
import com.waqiti.wallet.domain.TransferResult;
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
 * Critical Event Consumer #260: Wallet Transfer Event Consumer
 * Processes wallet-to-wallet transfers with AML, OFAC compliance
 * Implements 12-step zero-tolerance processing for P2P transfers
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletTransferEventConsumer extends BaseKafkaConsumer {

    private final WalletService walletService;
    private final WalletComplianceService complianceService;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "wallet-transfer-events", groupId = "wallet-transfer-group")
    @CircuitBreaker(name = "wallet-transfer-consumer")
    @Retry(name = "wallet-transfer-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleWalletTransferEvent(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "wallet-transfer-event");

        try {
            log.info("Step 1: Processing wallet transfer event: partition={}, offset={}",
                    partition, offset);
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String fromWalletId = eventData.path("fromWalletId").asText();
            String toWalletId = eventData.path("toWalletId").asText();
            BigDecimal amount = new BigDecimal(eventData.path("amount").asText());
            String transferPurpose = eventData.path("transferPurpose").asText();
            String memo = eventData.path("memo").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted transfer details: from={}, to={}, amount={}", 
                    fromWalletId, toWalletId, amount);
            
            // Step 3: Transfer validation with early return
            boolean validated = walletService.validateTransferRequest(fromWalletId, toWalletId, 
                    amount, transferPurpose, timestamp);
            if (!validated) {
                log.error("Step 3: Transfer validation failed");
                walletService.rejectTransfer(eventId, "VALIDATION_FAILED", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: Transfer validation successful");
            
            // Step 4: Balance and limit checks
            boolean sufficientFunds = walletService.checkSufficientBalance(fromWalletId, amount, timestamp);
            boolean withinLimits = complianceService.checkTransferLimits(fromWalletId, amount, timestamp);
            if (!sufficientFunds || !withinLimits) {
                log.error("Step 4: Insufficient funds or limits exceeded");
                walletService.rejectTransfer(eventId, 
                        !sufficientFunds ? "INSUFFICIENT_FUNDS" : "LIMIT_EXCEEDED", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 4: Balance and limits verified");
            
            // Step 5: AML screening for both parties
            boolean fromAMLClear = complianceService.performAMLScreening(fromWalletId, amount, "SENDER", timestamp);
            boolean toAMLClear = complianceService.performAMLScreening(toWalletId, amount, "RECIPIENT", timestamp);
            if (!fromAMLClear || !toAMLClear) {
                log.error("Step 5: AML screening failed");
                walletService.holdTransfer(eventId, "AML_REVIEW_REQUIRED", timestamp);
            } else {
                log.info("Step 5: AML screening passed");
            }
            
            // Step 6: OFAC sanctions screening
            boolean sanctionsCleared = complianceService.performSanctionsScreening(fromWalletId, 
                    toWalletId, timestamp);
            if (!sanctionsCleared) {
                log.error("Step 6: Sanctions screening failed");
                walletService.blockTransfer(eventId, "SANCTIONS_MATCH", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 6: Sanctions screening passed");
            
            // Step 7: Atomic transfer execution
            TransferResult transferResult = walletService.executeAtomicTransfer(eventId, fromWalletId, 
                    toWalletId, amount, transferPurpose, memo, timestamp);
            log.info("Step 7: Transfer executed: resultStatus={}, transactionId={}", 
                    transferResult.getStatus(), transferResult.getTransactionId());
            
            // Step 8: Real-time fraud monitoring
            int fraudScore = complianceService.calculateTransferFraudScore(fromWalletId, toWalletId, 
                    amount, transferPurpose, timestamp);
            if (fraudScore > 750) {
                walletService.flagSuspiciousTransfer(transferResult.getTransactionId(), fraudScore, timestamp);
                log.warn("Step 8: High fraud score detected: {}", fraudScore);
            } else {
                log.info("Step 8: Fraud score acceptable: {}", fraudScore);
            }
            
            // Step 9: Velocity pattern analysis
            complianceService.analyzeVelocityPatterns(fromWalletId, toWalletId, amount, timestamp);
            log.info("Step 9: Velocity pattern analysis completed");
            
            // Step 10: Notifications to both parties
            walletService.sendTransferNotifications(fromWalletId, toWalletId, amount, 
                    transferResult.getTransactionId(), timestamp);
            log.info("Step 10: Transfer notifications sent");
            
            // Step 11: Compliance reporting for large transfers
            if (amount.compareTo(new BigDecimal("3000")) >= 0) {
                complianceService.generateTransferReport(eventId, fromWalletId, toWalletId, 
                        amount, transferPurpose, timestamp);
                log.info("Step 11: Large transfer report generated");
            } else {
                log.info("Step 11: No reporting required for amount: {}", amount);
            }
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed wallet transfer: eventId={}, transactionId={}", 
                    eventId, transferResult.getTransactionId());
            
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
        if (!eventData.has("eventId") || !eventData.has("fromWalletId") || 
            !eventData.has("toWalletId") || !eventData.has("amount")) {
            throw new IllegalArgumentException("Invalid wallet transfer event structure");
        }
    }
}