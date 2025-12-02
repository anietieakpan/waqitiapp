package com.waqiti.transaction.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.transaction.service.TransactionDeclineService;
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
public class TransactionDeclinedEventsConsumer {

    private final TransactionDeclineService transactionDeclineService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler dlqHandler;
    
    @KafkaListener(
        topics = {"transaction-declined-events", "payment-declined", "authorization-failed"},
        groupId = "transaction-service-transaction-declined-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000)
    )
    @Transactional
    public void handleTransactionDeclinedEvent(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        String eventJson = record.value();
        
        UUID transactionId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            transactionId = UUID.fromString((String) event.get("transactionId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            String declineReason = (String) event.get("declineReason");
            String declineCode = (String) event.get("declineCode");
            BigDecimal amount = new BigDecimal(event.get("amount").toString());
            String currency = (String) event.get("currency");
            LocalDateTime declinedDate = LocalDateTime.parse((String) event.get("declinedDate"));
            String merchantId = (String) event.getOrDefault("merchantId", "");
            String riskScore = (String) event.getOrDefault("riskScore", "MEDIUM");
            
            log.warn("Transaction declined event - TransactionId: {}, CustomerId: {}, Reason: {}, Code: {}, Amount: {} {}", 
                    transactionId, customerId, declineReason, declineCode, amount, currency);
            
            transactionDeclineService.processTransactionDeclined(transactionId, customerId, 
                    declineReason, declineCode, amount, currency, declinedDate, merchantId, riskScore);
            
            auditService.auditFinancialEvent(
                    "TRANSACTION_DECLINED_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Transaction declined - Reason: %s, Code: %s, Amount: %s %s", 
                            declineReason, declineCode, amount, currency),
                    Map.of(
                            "transactionId", transactionId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "declineReason", declineReason,
                            "declineCode", declineCode,
                            "amount", amount.toString(),
                            "currency", currency,
                            "merchantId", merchantId,
                            "riskScore", riskScore
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Transaction declined event processing failed - TransactionId: {}, CustomerId: {}, Error: {}",
                    transactionId, customerId, e.getMessage(), e);

            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(result -> log.info("Transaction declined event sent to DLQ: transactionId={}, destination={}, attemptNumber={}",
                        transactionId, result.getDestinationTopic(), result.getAttemptNumber()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for transaction declined event - MESSAGE MAY BE LOST! " +
                            "transactionId={}, partition={}, offset={}, error={}",
                            transactionId, partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Transaction declined event processing failed", e);
        }
    }

    @DltHandler
    public void handleDlt(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        log.error("Transaction declined event sent to DLT: topic={}, key={}, exception={}",
                  topic, record.key(), exceptionMessage);

        try {
            Map<String, Object> event = objectMapper.readValue(record.value(), Map.class);
            UUID transactionId = UUID.fromString((String) event.get("transactionId"));
            UUID customerId = UUID.fromString((String) event.get("customerId"));

            auditService.auditFinancialEvent(
                    "TRANSACTION_DECLINED_DLT_EVENT",
                    customerId.toString(),
                    String.format("Transaction declined event sent to DLT: %s", exceptionMessage),
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