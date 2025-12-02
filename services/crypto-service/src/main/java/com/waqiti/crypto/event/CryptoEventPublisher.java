/**
 * Crypto Event Publisher
 * Publishes cryptocurrency domain events to Kafka
 */
package com.waqiti.crypto.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.crypto.entity.CryptoTransaction;
import com.waqiti.crypto.entity.CryptoWallet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class CryptoEventPublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String TOPIC_PREFIX = "crypto.";
    
    /**
     * Publishes wallet created event
     */
    public void publishWalletCreated(CryptoWallet wallet) {
        WalletEvent event = WalletEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("WALLET_CREATED")
                .timestamp(LocalDateTime.now())
                .walletId(wallet.getId())
                .userId(wallet.getUserId())
                .currency(wallet.getCurrency().name())
                .walletType(wallet.getWalletType().name())
                .primaryAddress(wallet.getPrimaryAddress())
                .build();
        
        publishEvent(TOPIC_PREFIX + "wallet.created", event);
    }
    
    /**
     * Publishes transaction initiated event
     */
    public void publishTransactionInitiated(CryptoTransaction transaction) {
        TransactionEvent event = TransactionEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("TRANSACTION_INITIATED")
                .timestamp(LocalDateTime.now())
                .transactionId(transaction.getId())
                .userId(transaction.getUserId())
                .walletId(transaction.getWalletId())
                .currency(transaction.getCurrency().name())
                .transactionType(transaction.getTransactionType().name())
                .amount(transaction.getAmount())
                .toAddress(transaction.getToAddress())
                .fromAddress(transaction.getFromAddress())
                .build();
        
        publishEvent(TOPIC_PREFIX + "transaction.initiated", event);
    }
    
    /**
     * Publishes transaction broadcast event
     */
    public void publishTransactionBroadcast(CryptoTransaction transaction) {
        TransactionEvent event = TransactionEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("TRANSACTION_BROADCAST")
                .timestamp(LocalDateTime.now())
                .transactionId(transaction.getId())
                .userId(transaction.getUserId())
                .walletId(transaction.getWalletId())
                .currency(transaction.getCurrency().name())
                .transactionType(transaction.getTransactionType().name())
                .amount(transaction.getAmount())
                .txHash(transaction.getTxHash())
                .status(transaction.getStatus().name())
                .build();
        
        publishEvent(TOPIC_PREFIX + "transaction.broadcast", event);
    }
    
    /**
     * Publishes transaction confirmed event
     */
    public void publishTransactionConfirmed(CryptoTransaction transaction) {
        TransactionEvent event = TransactionEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("TRANSACTION_CONFIRMED")
                .timestamp(LocalDateTime.now())
                .transactionId(transaction.getId())
                .userId(transaction.getUserId())
                .walletId(transaction.getWalletId())
                .currency(transaction.getCurrency().name())
                .transactionType(transaction.getTransactionType().name())
                .amount(transaction.getAmount())
                .txHash(transaction.getTxHash())
                .confirmations(transaction.getConfirmations())
                .status("CONFIRMED")
                .build();
        
        publishEvent(TOPIC_PREFIX + "transaction.confirmed", event);
    }
    
    /**
     * Publishes transaction failed event
     */
    public void publishTransactionFailed(CryptoTransaction transaction, String reason) {
        TransactionEvent event = TransactionEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("TRANSACTION_FAILED")
                .timestamp(LocalDateTime.now())
                .transactionId(transaction.getId())
                .userId(transaction.getUserId())
                .walletId(transaction.getWalletId())
                .currency(transaction.getCurrency().name())
                .transactionType(transaction.getTransactionType().name())
                .amount(transaction.getAmount())
                .status("FAILED")
                .failureReason(reason)
                .build();
        
        publishEvent(TOPIC_PREFIX + "transaction.failed", event);
    }
    
    /**
     * Publishes fraud alert event
     */
    public void publishFraudAlert(UUID userId, UUID transactionId, Integer riskScore, String reason) {
        FraudAlertEvent event = FraudAlertEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("FRAUD_ALERT")
                .timestamp(LocalDateTime.now())
                .userId(userId)
                .transactionId(transactionId)
                .riskScore(riskScore)
                .alertReason(reason)
                .build();
        
        publishEvent(TOPIC_PREFIX + "fraud.alert", event);
    }
    
    /**
     * Publishes compliance alert event
     */
    public void publishComplianceAlert(UUID userId, UUID transactionId, String violationType, String details) {
        ComplianceEvent event = ComplianceEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("COMPLIANCE_ALERT")
                .timestamp(LocalDateTime.now())
                .userId(userId)
                .transactionId(transactionId)
                .violationType(violationType)
                .details(details)
                .requiresReporting(true)
                .build();
        
        publishEvent(TOPIC_PREFIX + "compliance.alert", event);
    }
    
    /**
     * Publishes price alert triggered event
     */
    public void publishPriceAlert(UUID userId, String currency, BigDecimal currentPrice, BigDecimal targetPrice) {
        PriceAlertEvent event = PriceAlertEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("PRICE_ALERT_TRIGGERED")
                .timestamp(LocalDateTime.now())
                .userId(userId)
                .currency(currency)
                .currentPrice(currentPrice)
                .targetPrice(targetPrice)
                .build();
        
        publishEvent(TOPIC_PREFIX + "price.alert", event);
    }
    
    /**
     * Generic event publisher
     */
    private void publishEvent(String topic, Object event) {
        try {
            String eventKey = event.getClass().getSimpleName() + "_" + UUID.randomUUID();
            
            CompletableFuture<SendResult<String, Object>> future = 
                    kafkaTemplate.send(topic, eventKey, event);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug("Published event to topic {} with key {}", topic, eventKey);
                } else {
                    log.error("Failed to publish event to topic {}", topic, ex);
                }
            });
            
        } catch (Exception e) {
            log.error("Error publishing event to topic {}", topic, e);
        }
    }
    
    @Data
    @Builder
    public static class WalletEvent {
        private UUID eventId;
        private String eventType;
        private LocalDateTime timestamp;
        private UUID walletId;
        private UUID userId;
        private String currency;
        private String walletType;
        private String primaryAddress;
    }
    
    @Data
    @Builder
    public static class TransactionEvent {
        private UUID eventId;
        private String eventType;
        private LocalDateTime timestamp;
        private UUID transactionId;
        private UUID userId;
        private UUID walletId;
        private String currency;
        private String transactionType;
        private BigDecimal amount;
        private String toAddress;
        private String fromAddress;
        private String txHash;
        private Integer confirmations;
        private String status;
        private String failureReason;
    }
    
    @Data
    @Builder
    public static class FraudAlertEvent {
        private UUID eventId;
        private String eventType;
        private LocalDateTime timestamp;
        private UUID userId;
        private UUID transactionId;
        private Integer riskScore;
        private String alertReason;
    }
    
    @Data
    @Builder
    public static class ComplianceEvent {
        private UUID eventId;
        private String eventType;
        private LocalDateTime timestamp;
        private UUID userId;
        private UUID transactionId;
        private String violationType;
        private String details;
        private boolean requiresReporting;
    }
    
    @Data
    @Builder
    public static class PriceAlertEvent {
        private UUID eventId;
        private String eventType;
        private LocalDateTime timestamp;
        private UUID userId;
        private String currency;
        private BigDecimal currentPrice;
        private BigDecimal targetPrice;
    }
}