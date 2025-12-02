package com.waqiti.transaction.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.transaction.service.TransactionSettlementService;
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
public class TransactionSettledEventsConsumer {

    private final TransactionSettlementService transactionSettlementService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler dlqHandler;
    
    @KafkaListener(
        topics = {"transaction-settled-events", "payment-settled", "settlement-completed"},
        groupId = "transaction-service-transaction-settled-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 30000)
    )
    @Transactional
    public void handleTransactionSettledEvent(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        String eventJson = record.value();
        
        UUID settlementId = null;
        UUID transactionId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            settlementId = UUID.fromString((String) event.get("settlementId"));
            transactionId = UUID.fromString((String) event.get("transactionId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            BigDecimal settlementAmount = new BigDecimal(event.get("settlementAmount").toString());
            String currency = (String) event.get("currency");
            LocalDateTime settlementDate = LocalDateTime.parse((String) event.get("settlementDate"));
            String settlementType = (String) event.get("settlementType");
            String settlementStatus = (String) event.get("settlementStatus");
            String settlementReference = (String) event.get("settlementReference");
            String merchantId = (String) event.getOrDefault("merchantId", "");
            BigDecimal feeAmount = event.containsKey("feeAmount") ? 
                    new BigDecimal(event.get("feeAmount").toString()) : BigDecimal.ZERO;
            
            log.info("Transaction settled event - SettlementId: {}, TransactionId: {}, Amount: {} {}, Type: {}, Status: {}", 
                    settlementId, transactionId, settlementAmount, currency, settlementType, settlementStatus);
            
            transactionSettlementService.processTransactionSettlement(settlementId, transactionId, 
                    customerId, settlementAmount, currency, settlementDate, settlementType, 
                    settlementStatus, settlementReference, merchantId, feeAmount);
            
            auditService.auditFinancialEvent(
                    "TRANSACTION_SETTLED_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Transaction settled - Amount: %s %s, Type: %s, Status: %s, Ref: %s", 
                            settlementAmount, currency, settlementType, settlementStatus, settlementReference),
                    Map.of(
                            "settlementId", settlementId.toString(),
                            "transactionId", transactionId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "settlementAmount", settlementAmount.toString(),
                            "currency", currency,
                            "settlementType", settlementType,
                            "settlementStatus", settlementStatus,
                            "settlementReference", settlementReference,
                            "merchantId", merchantId,
                            "feeAmount", feeAmount.toString()
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Transaction settled event processing failed - SettlementId: {}, TransactionId: {}, Error: {}",
                    settlementId, transactionId, e.getMessage(), e);

            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(result -> log.info("Transaction settled event sent to DLQ: settlementId={}, destination={}, attemptNumber={}",
                        settlementId, result.getDestinationTopic(), result.getAttemptNumber()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for transaction settled event - MESSAGE MAY BE LOST! " +
                            "settlementId={}, partition={}, offset={}, error={}",
                            settlementId, partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Transaction settled event processing failed", e);
        }
    }

    @DltHandler
    public void handleDlt(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        log.error("Transaction settled event sent to DLT: topic={}, key={}, exception={}",
                  topic, record.key(), exceptionMessage);

        try {
            Map<String, Object> event = objectMapper.readValue(record.value(), Map.class);
            UUID settlementId = UUID.fromString((String) event.get("settlementId"));
            UUID customerId = UUID.fromString((String) event.get("customerId"));

            auditService.auditFinancialEvent(
                    "TRANSACTION_SETTLED_DLT_EVENT",
                    customerId.toString(),
                    String.format("Transaction settled event sent to DLT: %s", exceptionMessage),
                    Map.of(
                            "settlementId", settlementId.toString(),
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