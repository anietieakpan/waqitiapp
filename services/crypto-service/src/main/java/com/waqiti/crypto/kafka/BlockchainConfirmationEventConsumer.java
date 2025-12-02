package com.waqiti.crypto.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.crypto.service.BlockchainMonitoringService;
import com.waqiti.crypto.service.CryptoTransactionService;
import com.waqiti.crypto.entity.BlockchainConfirmation;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Critical Event Consumer #162: Blockchain Confirmation Event Consumer
 * Processes blockchain transaction confirmations with multi-chain support
 * Implements 12-step zero-tolerance processing for cryptographic transaction finality
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlockchainConfirmationEventConsumer extends BaseKafkaConsumer {

    private final BlockchainMonitoringService blockchainMonitoringService;
    private final CryptoTransactionService cryptoTransactionService;
    private final ObjectMapper objectMapper;
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService;

    @KafkaListener(topics = "blockchain-confirmation-events", groupId = "blockchain-confirmation-group")
    @CircuitBreaker(name = "blockchain-confirmation-consumer")
    @Retry(name = "blockchain-confirmation-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleBlockchainConfirmationEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "blockchain-confirmation-event");
        
        try {
            log.info("Step 1: Processing blockchain confirmation event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String transactionId = eventData.path("transactionId").asText();
            String blockchainTxHash = eventData.path("blockchainTxHash").asText();
            String blockchain = eventData.path("blockchain").asText();

            // CRITICAL SECURITY: Idempotency check
            String idempotencyKey = "blockchain-confirmation:" + blockchainTxHash + ":" + blockchain + ":" + eventId;
            UUID operationId = UUID.randomUUID();
            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(7))) {
                log.warn("SECURITY: Duplicate blockchain confirmation ignored: txHash={}, eventId={}", blockchainTxHash, eventId);
                ack.acknowledge();
                return;
            }
            int confirmationCount = eventData.path("confirmationCount").asInt();
            String blockHash = eventData.path("blockHash").asText();
            long blockNumber = eventData.path("blockNumber").asLong();
            BigDecimal gasFee = new BigDecimal(eventData.path("gasFee").asText());
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted confirmation details: txHash={}, blockchain={}, confirmations={}", 
                    blockchainTxHash, blockchain, confirmationCount);
            
            BlockchainConfirmation confirmation = blockchainMonitoringService.recordConfirmation(
                    transactionId, blockchainTxHash, blockchain, confirmationCount, 
                    blockHash, blockNumber, timestamp);
            
            log.info("Step 3: Recorded blockchain confirmation");
            
            boolean reorgDetected = blockchainMonitoringService.detectChainReorganization(
                    blockchain, blockNumber, blockHash, timestamp);
            
            if (reorgDetected) {
                log.error("Step 4: CHAIN REORGANIZATION DETECTED: blockchain={}, block={}", 
                        blockchain, blockNumber);
                blockchainMonitoringService.handleChainReorg(transactionId, timestamp);
                ack.acknowledge();
                return;
            }
            
            log.info("Step 4: No chain reorganization detected");
            
            int requiredConfirmations = blockchainMonitoringService.getRequiredConfirmations(blockchain);
            
            if (confirmationCount >= requiredConfirmations) {
                log.info("Step 5: Transaction finalized: confirmations={}/{}", 
                        confirmationCount, requiredConfirmations);
                cryptoTransactionService.finalizeTransaction(transactionId, timestamp);
            } else {
                log.info("Step 5: Waiting for more confirmations: {}/{}", 
                        confirmationCount, requiredConfirmations);
            }
            
            blockchainMonitoringService.verifyTransactionOnChain(blockchainTxHash, 
                    blockchain, timestamp);
            log.info("Step 6: Verified transaction on blockchain");
            
            cryptoTransactionService.updateTransactionStatus(transactionId, 
                    confirmationCount, requiredConfirmations, timestamp);
            log.info("Step 7: Updated transaction status");
            
            cryptoTransactionService.recordGasFee(transactionId, gasFee, blockchain, timestamp);
            log.info("Step 8: Recorded gas fee: amount={}", gasFee);
            
            if (confirmationCount >= requiredConfirmations) {
                cryptoTransactionService.creditUserAccount(transactionId, timestamp);
                log.info("Step 9: Credited user account");
            }
            
            blockchainMonitoringService.sendConfirmationNotification(transactionId, 
                    confirmationCount, requiredConfirmations, timestamp);
            log.info("Step 10: Sent confirmation notification");
            
            blockchainMonitoringService.updateMetrics(blockchain, confirmationCount, timestamp);
            log.info("Step 11: Updated blockchain monitoring metrics");

            // CRITICAL SECURITY: Mark completed
            idempotencyService.completeOperation(idempotencyKey, operationId,
                Map.of("blockchainTxHash", blockchainTxHash, "blockchain", blockchain,
                       "confirmationCount", String.valueOf(confirmationCount), "status", "COMPLETED"), Duration.ofDays(7));

            ack.acknowledge();
            log.info("Step 12: Successfully processed blockchain confirmation: eventId={}", eventId);

        } catch (Exception e) {
            log.error("SECURITY: Error processing blockchain confirmation event: {}", e.getMessage(), e);
            String idempotencyKey = "blockchain-confirmation:" + record.key();
            idempotencyService.failOperation(idempotencyKey, UUID.randomUUID(), e.getMessage());
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("transactionId") || 
            !eventData.has("blockchainTxHash") || !eventData.has("blockchain")) {
            throw new IllegalArgumentException("Invalid blockchain confirmation event structure");
        }
    }
}