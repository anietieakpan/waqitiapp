package com.waqiti.crypto.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.crypto.service.NFTTransactionService;
import com.waqiti.crypto.service.CryptoComplianceService;
import com.waqiti.crypto.service.CryptoNotificationService;
import com.waqiti.common.exception.CryptoProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka Consumer for NFT Transaction Events
 * Handles NFT minting, transfers, sales, and marketplace activities
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NFTTransactionEventsConsumer {
    
    private final NFTTransactionService nftService;
    private final CryptoComplianceService complianceService;
    private final CryptoNotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService;
    
    @KafkaListener(
        topics = {"nft-transaction-events", "nft-minted", "nft-transferred", "nft-sold"},
        groupId = "crypto-service-nft-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000)
    )
    @Transactional
    public void handleNFTTransactionEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID nftTransactionId = null;
        UUID customerId = null;
        String eventType = null;
        String idempotencyKey = null;
        UUID operationId = UUID.randomUUID();

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            nftTransactionId = UUID.fromString((String) event.get("nftTransactionId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            String tokenId = (String) event.get("tokenId");
            String contractAddress = (String) event.get("contractAddress");
            String blockchain = (String) event.get("blockchain");
            String fromAddress = (String) event.get("fromAddress");
            String toAddress = (String) event.get("toAddress");
            BigDecimal price = event.containsKey("price") ? new BigDecimal((String) event.get("price")) : BigDecimal.ZERO;
            String currency = (String) event.get("currency");
            LocalDateTime timestamp = LocalDateTime.parse((String) event.get("timestamp"));

            // CRITICAL SECURITY: Idempotency check
            idempotencyKey = String.format("nft-transaction:%s:%s:%s",
                nftTransactionId, tokenId, contractAddress);
            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(7))) {
                log.warn("SECURITY: Duplicate NFT transaction ignored: nftTransactionId={}, tokenId={}",
                        nftTransactionId, tokenId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing NFT transaction event - TransactionId: {}, Type: {}, TokenId: {}, Price: {} {}",
                    nftTransactionId, eventType, tokenId, price, currency);
            
            // Compliance screening
            Map<String, Object> complianceResult = complianceService.screenNFTTransaction(
                    contractAddress, fromAddress, toAddress, price, currency, timestamp);
            
            if ("BLOCKED".equals(complianceResult.get("status"))) {
                nftService.blockNFTTransaction(nftTransactionId, 
                        (String) complianceResult.get("reason"), timestamp);
                acknowledgment.acknowledge();
                return;
            }
            
            switch (eventType) {
                case "NFT_MINTED":
                    nftService.processNFTMinting(nftTransactionId, customerId, tokenId,
                            contractAddress, blockchain, toAddress, timestamp);
                    break;
                case "NFT_TRANSFERRED":
                    nftService.processNFTTransfer(nftTransactionId, customerId, tokenId,
                            contractAddress, fromAddress, toAddress, timestamp);
                    break;
                case "NFT_SOLD":
                    nftService.processNFTSale(nftTransactionId, customerId, tokenId,
                            contractAddress, fromAddress, toAddress, price, currency, timestamp);
                    break;
                default:
                    nftService.processGenericNFTEvent(nftTransactionId, eventType, event, timestamp);
            }
            
            notificationService.sendNFTNotification(nftTransactionId, customerId, eventType,
                    tokenId, price, currency, timestamp);
            
            auditService.auditFinancialEvent(
                    "NFT_TRANSACTION_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("NFT transaction event processed - Type: %s, Token: %s, Price: %s %s", 
                            eventType, tokenId, price, currency),
                    Map.of(
                            "nftTransactionId", nftTransactionId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "tokenId", tokenId,
                            "contractAddress", contractAddress,
                            "blockchain", blockchain,
                            "price", price.toString(),
                            "currency", currency != null ? currency : "N/A"
                    )
            );

            // CRITICAL SECURITY: Mark completed
            idempotencyService.completeOperation(idempotencyKey, operationId,
                Map.of("nftTransactionId", nftTransactionId.toString(), "eventType", eventType,
                       "tokenId", tokenId, "contractAddress", contractAddress,
                       "status", "COMPLETED"), Duration.ofDays(7));

            acknowledgment.acknowledge();
            log.info("Successfully processed NFT transaction event - TransactionId: {}, EventType: {}",
                    nftTransactionId, eventType);

        } catch (Exception e) {
            log.error("SECURITY: NFT transaction event processing failed - TransactionId: {}, CustomerId: {}, Error: {}",
                    nftTransactionId, customerId, e.getMessage(), e);
            if (idempotencyKey != null) {
                idempotencyService.failOperation(idempotencyKey, operationId, e.getMessage());
            }
            throw new CryptoProcessingException("NFT transaction event processing failed", e);
        }
    }
}