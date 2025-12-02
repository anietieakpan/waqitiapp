package com.waqiti.account.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.account.service.AccountStatusService;
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
public class AccountStatusChangeEventsConsumer {
    
    private final AccountStatusService accountStatusService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"account-status-change-events", "account-suspended", "account-reactivated"},
        groupId = "account-service-account-status-change-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000)
    )
    @Transactional
    public void handleAccountStatusChangeEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID statusChangeId = null;
        UUID accountId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            statusChangeId = UUID.fromString((String) event.get("statusChangeId"));
            accountId = UUID.fromString((String) event.get("accountId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            String oldStatus = (String) event.get("oldStatus");
            String newStatus = (String) event.get("newStatus");
            String changeReason = (String) event.get("changeReason");
            LocalDateTime changeDate = LocalDateTime.parse((String) event.get("changeDate"));
            String changedBy = (String) event.get("changedBy");
            Boolean isTemporary = (Boolean) event.getOrDefault("isTemporary", false);
            LocalDateTime effectiveUntil = event.containsKey("effectiveUntil") ? 
                    LocalDateTime.parse((String) event.get("effectiveUntil")) : null;
            
            log.warn("Account status change event - StatusChangeId: {}, AccountId: {}, CustomerId: {}, OldStatus: {}, NewStatus: {}, Reason: {}", 
                    statusChangeId, accountId, customerId, oldStatus, newStatus, changeReason);
            
            accountStatusService.processAccountStatusChange(statusChangeId, accountId, customerId, 
                    oldStatus, newStatus, changeReason, changeDate, changedBy, isTemporary, 
                    effectiveUntil);
            
            auditService.auditFinancialEvent(
                    "ACCOUNT_STATUS_CHANGE_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Account status changed from %s to %s - Reason: %s, Temporary: %s", 
                            oldStatus, newStatus, changeReason, isTemporary),
                    Map.of(
                            "statusChangeId", statusChangeId.toString(),
                            "accountId", accountId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "oldStatus", oldStatus,
                            "newStatus", newStatus,
                            "changeReason", changeReason,
                            "changedBy", changedBy,
                            "isTemporary", isTemporary.toString(),
                            "effectiveUntil", effectiveUntil != null ? effectiveUntil.toString() : "PERMANENT"
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Account status change event processing failed - StatusChangeId: {}, AccountId: {}, Error: {}", 
                    statusChangeId, accountId, e.getMessage(), e);
            throw new RuntimeException("Account status change event processing failed", e);
        }
    }
}