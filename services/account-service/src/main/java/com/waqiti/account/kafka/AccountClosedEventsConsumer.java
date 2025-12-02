package com.waqiti.account.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.account.service.AccountClosureService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountClosedEventsConsumer {
    
    private final AccountClosureService accountClosureService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"account-closed-events", "account-closure-requested"},
        groupId = "account-service-account-closed-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000)
    )
    @Transactional
    public void handleAccountClosedEvent(
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
            String closureReason = (String) event.get("closureReason");
            LocalDateTime closureDate = LocalDateTime.parse((String) event.get("closureDate"));
            BigDecimal finalBalance = new BigDecimal(event.get("finalBalance").toString());
            String currency = (String) event.get("currency");
            String closedBy = (String) event.get("closedBy");
            String accountType = (String) event.get("accountType");
            
            log.warn("Account closed event - AccountId: {}, CustomerId: {}, Reason: {}, FinalBalance: {} {}", 
                    accountId, customerId, closureReason, finalBalance, currency);
            
            accountClosureService.processAccountClosure(accountId, customerId, closureReason, 
                    closureDate, finalBalance, currency, closedBy, accountType);
            
            auditService.auditFinancialEvent(
                    "ACCOUNT_CLOSED_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Account closed - Reason: %s, FinalBalance: %s %s", 
                            closureReason, finalBalance, currency),
                    Map.of(
                            "accountId", accountId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "closureReason", closureReason,
                            "finalBalance", finalBalance.toString(),
                            "currency", currency,
                            "closedBy", closedBy,
                            "accountType", accountType
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Account closed event processing failed - AccountId: {}, CustomerId: {}, Error: {}", 
                    accountId, customerId, e.getMessage(), e);
            throw new RuntimeException("Account closed event processing failed", e);
        }
    }
}