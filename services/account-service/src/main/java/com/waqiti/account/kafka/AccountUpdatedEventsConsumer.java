package com.waqiti.account.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.account.service.AccountUpdateService;
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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountUpdatedEventsConsumer {
    
    private final AccountUpdateService accountUpdateService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"account-updated-events", "account-profile-updated", "account-settings-changed"},
        groupId = "account-service-account-updated-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000)
    )
    @Transactional
    public void handleAccountUpdatedEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID accountId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            accountId = UUID.fromString((String) event.get("accountId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            String updateType = (String) event.get("updateType");
            String fieldChanged = (String) event.get("fieldChanged");
            String oldValue = (String) event.getOrDefault("oldValue", "");
            String newValue = (String) event.getOrDefault("newValue", "");
            LocalDateTime updatedDate = LocalDateTime.parse((String) event.get("updatedDate"));
            String updatedBy = (String) event.get("updatedBy");
            
            log.info("Account updated event - AccountId: {}, CustomerId: {}, Field: {}, UpdatedBy: {}", 
                    accountId, customerId, fieldChanged, updatedBy);
            
            accountUpdateService.processAccountUpdate(accountId, customerId, updateType, fieldChanged, 
                    oldValue, newValue, updatedDate, updatedBy);
            
            auditService.auditFinancialEvent(
                    "ACCOUNT_UPDATED_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Account updated - Field: %s, OldValue: %s, NewValue: %s", 
                            fieldChanged, oldValue, newValue),
                    Map.of(
                            "accountId", accountId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "updateType", updateType,
                            "fieldChanged", fieldChanged,
                            "updatedBy", updatedBy
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Account updated event processing failed - AccountId: {}, CustomerId: {}, Error: {}", 
                    accountId, customerId, e.getMessage(), e);
            throw new RuntimeException("Account updated event processing failed", e);
        }
    }
}