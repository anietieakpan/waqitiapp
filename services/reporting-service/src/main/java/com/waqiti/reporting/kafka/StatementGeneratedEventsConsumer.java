package com.waqiti.reporting.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.reporting.service.StatementGenerationService;
import com.waqiti.common.audit.AuditService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StatementGeneratedEventsConsumer {
    
    private final StatementGenerationService statementGenerationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"statement-generated-events", "monthly-statement-generated", "quarterly-statement-generated"},
        groupId = "reporting-service-statement-generated-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000)
    )
    @Transactional
    public void handleStatementGeneratedEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID statementId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            statementId = UUID.fromString((String) event.get("statementId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            UUID accountId = UUID.fromString((String) event.get("accountId"));
            eventType = (String) event.get("eventType");
            String statementType = (String) event.get("statementType");
            String statementPeriod = (String) event.get("statementPeriod");
            LocalDate statementStartDate = LocalDate.parse((String) event.get("statementStartDate"));
            LocalDate statementEndDate = LocalDate.parse((String) event.get("statementEndDate"));
            LocalDateTime generatedDate = LocalDateTime.parse((String) event.get("generatedDate"));
            String statementFormat = (String) event.getOrDefault("statementFormat", "PDF");
            String documentPath = (String) event.get("documentPath");
            String deliveryMethod = (String) event.getOrDefault("deliveryMethod", "EMAIL");
            Boolean isAutomated = (Boolean) event.getOrDefault("isAutomated", true);
            
            log.info("Statement generated event - StatementId: {}, CustomerId: {}, Type: {}, Period: {}, Format: {}", 
                    statementId, customerId, statementType, statementPeriod, statementFormat);
            
            statementGenerationService.processStatementGenerated(statementId, customerId, accountId, 
                    statementType, statementPeriod, statementStartDate, statementEndDate, 
                    generatedDate, statementFormat, documentPath, deliveryMethod, isAutomated);
            
            auditService.auditFinancialEvent(
                    "STATEMENT_GENERATED_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Statement generated - Type: %s, Period: %s, Format: %s, Delivery: %s", 
                            statementType, statementPeriod, statementFormat, deliveryMethod),
                    Map.of(
                            "statementId", statementId.toString(),
                            "customerId", customerId.toString(),
                            "accountId", accountId.toString(),
                            "eventType", eventType,
                            "statementType", statementType,
                            "statementPeriod", statementPeriod,
                            "statementStartDate", statementStartDate.toString(),
                            "statementEndDate", statementEndDate.toString(),
                            "statementFormat", statementFormat,
                            "documentPath", documentPath,
                            "deliveryMethod", deliveryMethod,
                            "isAutomated", isAutomated.toString()
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Statement generated event processing failed - StatementId: {}, CustomerId: {}, Error: {}", 
                    statementId, customerId, e.getMessage(), e);
            throw new RuntimeException("Statement generated event processing failed", e);
        }
    }
}