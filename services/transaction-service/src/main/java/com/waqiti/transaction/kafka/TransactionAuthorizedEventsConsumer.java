package com.waqiti.transaction.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.transaction.service.TransactionAuthorizationService;
import com.waqiti.transaction.service.TransactionNotificationService;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionAuthorizedEventsConsumer {

    private final TransactionAuthorizationService transactionAuthorizationService;
    private final TransactionNotificationService transactionNotificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler dlqHandler;
    
    @KafkaListener(
        topics = {"transaction-authorized-events", "payment-authorized", "withdrawal-authorized"},
        groupId = "transaction-service-transaction-authorized-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleTransactionAuthorizedEvent(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        String eventJson = record.value();
        
        log.info("TRANSACTION AUTHORIZED: Processing transaction authorized event - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        UUID transactionId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            transactionId = UUID.fromString((String) event.get("transactionId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            UUID accountId = UUID.fromString((String) event.get("accountId"));
            eventType = (String) event.get("eventType");
            String transactionType = (String) event.get("transactionType");
            BigDecimal amount = new BigDecimal(event.get("amount").toString());
            String currency = (String) event.get("currency");
            LocalDateTime authorizedDate = LocalDateTime.parse((String) event.get("authorizedDate"));
            String authorizationCode = (String) event.get("authorizationCode");
            String merchantId = (String) event.getOrDefault("merchantId", "");
            String merchantName = (String) event.getOrDefault("merchantName", "");
            String paymentMethod = (String) event.get("paymentMethod");
            String cardNumber = (String) event.getOrDefault("cardNumber", "");
            String riskScore = (String) event.getOrDefault("riskScore", "LOW");
            String authSource = (String) event.get("authSource");
            
            log.info("Transaction authorized event - TransactionId: {}, CustomerId: {}, EventType: {}, Type: {}, Amount: {} {}, AuthCode: {}", 
                    transactionId, customerId, eventType, transactionType, amount, currency, authorizationCode);
            
            switch (eventType) {
                case "PAYMENT_AUTHORIZED" -> transactionAuthorizationService.processPaymentAuthorized(
                        transactionId, customerId, accountId, amount, currency, authorizedDate, 
                        authorizationCode, merchantId, merchantName, paymentMethod, cardNumber, 
                        riskScore, authSource);
                
                case "WITHDRAWAL_AUTHORIZED" -> transactionAuthorizationService.processWithdrawalAuthorized(
                        transactionId, customerId, accountId, amount, currency, authorizedDate, 
                        authorizationCode, paymentMethod, riskScore, authSource);
                
                case "TRANSFER_AUTHORIZED" -> transactionAuthorizationService.processTransferAuthorized(
                        transactionId, customerId, accountId, amount, currency, authorizedDate, 
                        authorizationCode, riskScore, authSource);
                
                default -> log.warn("Unknown transaction authorized event type: {}", eventType);
            }
            
            transactionNotificationService.sendAuthorizationNotification(customerId, transactionId, 
                    eventType, transactionType, amount, currency, merchantName, authorizedDate);
            
            transactionAuthorizationService.updateAuthorizationMetrics(eventType, transactionType, 
                    amount, currency, riskScore, authSource);
            
            auditService.auditFinancialEvent(
                    "TRANSACTION_AUTHORIZED_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Transaction authorized event %s - Type: %s, Amount: %s %s, AuthCode: %s", 
                            eventType, transactionType, amount, currency, authorizationCode),
                    Map.of(
                            "transactionId", transactionId.toString(),
                            "customerId", customerId.toString(),
                            "accountId", accountId.toString(),
                            "eventType", eventType,
                            "transactionType", transactionType,
                            "amount", amount.toString(),
                            "currency", currency,
                            "authorizationCode", authorizationCode,
                            "merchantId", merchantId,
                            "paymentMethod", paymentMethod,
                            "riskScore", riskScore,
                            "authSource", authSource
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Transaction authorized event processing failed - TransactionId: {}, CustomerId: {}, EventType: {}, Error: {}",
                    transactionId, customerId, eventType, e.getMessage(), e);

            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(result -> log.info("Transaction authorized event sent to DLQ: transactionId={}, destination={}, attemptNumber={}",
                        transactionId, result.getDestinationTopic(), result.getAttemptNumber()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for transaction authorized event - MESSAGE MAY BE LOST! " +
                            "transactionId={}, partition={}, offset={}, error={}",
                            transactionId, partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Transaction authorized event processing failed", e);
        }
    }

    @DltHandler
    public void handleDlt(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        log.error("Transaction authorized event sent to DLT: topic={}, key={}, exception={}",
                  topic, record.key(), exceptionMessage);

        try {
            Map<String, Object> event = objectMapper.readValue(record.value(), Map.class);
            UUID transactionId = UUID.fromString((String) event.get("transactionId"));
            UUID customerId = UUID.fromString((String) event.get("customerId"));

            auditService.auditFinancialEvent(
                    "TRANSACTION_AUTHORIZED_DLT_EVENT",
                    customerId.toString(),
                    String.format("Transaction authorized event sent to DLT: %s", exceptionMessage),
                    Map.of(
                            "transactionId", transactionId.toString(),
                            "customerId", customerId.toString(),
                            "topic", topic,
                            "errorMessage", exceptionMessage,
                            "requiresManualIntervention", true
                    )
            );
        } catch (Exception e) {
            log.error("Failed to process DLT event audit: {}", e.getMessage(), e);
        }
    }
}