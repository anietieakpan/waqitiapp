package com.waqiti.transaction.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.transaction.service.TransactionReversalService;
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
public class TransactionReversedEventsConsumer {

    private final TransactionReversalService transactionReversalService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler dlqHandler;
    
    @KafkaListener(
        topics = {"transaction-reversed-events", "payment-reversed", "refund-processed"},
        groupId = "transaction-service-transaction-reversed-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 30000)
    )
    @Transactional
    public void handleTransactionReversedEvent(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        String eventJson = record.value();
        
        UUID reversalId = null;
        UUID originalTransactionId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            reversalId = UUID.fromString((String) event.get("reversalId"));
            originalTransactionId = UUID.fromString((String) event.get("originalTransactionId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            String reversalReason = (String) event.get("reversalReason");
            BigDecimal reversalAmount = new BigDecimal(event.get("reversalAmount").toString());
            String currency = (String) event.get("currency");
            LocalDateTime reversalDate = LocalDateTime.parse((String) event.get("reversalDate"));
            String reversalType = (String) event.getOrDefault("reversalType", "FULL");
            String initiatedBy = (String) event.get("initiatedBy");
            String reversalReference = (String) event.getOrDefault("reversalReference", "");
            
            log.warn("Transaction reversed event - ReversalId: {}, OriginalTxnId: {}, CustomerId: {}, Amount: {} {}, Reason: {}", 
                    reversalId, originalTransactionId, customerId, reversalAmount, currency, reversalReason);
            
            transactionReversalService.processTransactionReversal(reversalId, originalTransactionId, 
                    customerId, reversalReason, reversalAmount, currency, reversalDate, reversalType, 
                    initiatedBy, reversalReference);
            
            auditService.auditFinancialEvent(
                    "TRANSACTION_REVERSED_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Transaction reversed - Reason: %s, Amount: %s %s, Type: %s", 
                            reversalReason, reversalAmount, currency, reversalType),
                    Map.of(
                            "reversalId", reversalId.toString(),
                            "originalTransactionId", originalTransactionId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "reversalReason", reversalReason,
                            "reversalAmount", reversalAmount.toString(),
                            "currency", currency,
                            "reversalType", reversalType,
                            "initiatedBy", initiatedBy,
                            "reversalReference", reversalReference
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Transaction reversed event processing failed - ReversalId: {}, OriginalTxnId: {}, Error: {}",
                    reversalId, originalTransactionId, e.getMessage(), e);

            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(result -> log.info("Transaction reversed event sent to DLQ: reversalId={}, destination={}, attemptNumber={}",
                        reversalId, result.getDestinationTopic(), result.getAttemptNumber()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for transaction reversed event - MESSAGE MAY BE LOST! " +
                            "reversalId={}, partition={}, offset={}, error={}",
                            reversalId, partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Transaction reversed event processing failed", e);
        }
    }

    @DltHandler
    public void handleDlt(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        log.error("Transaction reversed event sent to DLT: topic={}, key={}, exception={}",
                  topic, record.key(), exceptionMessage);

        try {
            Map<String, Object> event = objectMapper.readValue(record.value(), Map.class);
            UUID reversalId = UUID.fromString((String) event.get("reversalId"));
            UUID customerId = UUID.fromString((String) event.get("customerId"));

            auditService.auditFinancialEvent(
                    "TRANSACTION_REVERSED_DLT_EVENT",
                    customerId.toString(),
                    String.format("Transaction reversed event sent to DLT: %s", exceptionMessage),
                    Map.of(
                            "reversalId", reversalId.toString(),
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