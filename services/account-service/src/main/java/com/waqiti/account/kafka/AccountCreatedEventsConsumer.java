package com.waqiti.account.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.account.service.AccountCreationService;
import com.waqiti.account.service.AccountNotificationService;
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
public class AccountCreatedEventsConsumer {
    
    private final AccountCreationService accountCreationService;
    private final AccountNotificationService accountNotificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"account-created-events", "account-opened", "account-activated"},
        groupId = "account-service-account-created-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleAccountCreatedEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("ACCOUNT CREATED: Processing account created event - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        UUID accountId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            accountId = UUID.fromString((String) event.get("accountId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            String accountType = (String) event.get("accountType");
            String accountStatus = (String) event.get("accountStatus");
            String accountNumber = (String) event.get("accountNumber");
            String routingNumber = (String) event.get("routingNumber");
            LocalDateTime createdDate = LocalDateTime.parse((String) event.get("createdDate"));
            String createdBy = (String) event.get("createdBy");
            String branchCode = (String) event.getOrDefault("branchCode", "");
            String productCode = (String) event.get("productCode");
            String currency = (String) event.getOrDefault("currency", "USD");
            Boolean isJointAccount = (Boolean) event.getOrDefault("isJointAccount", false);
            String riskProfile = (String) event.getOrDefault("riskProfile", "MEDIUM");
            
            log.info("Account created event - AccountId: {}, CustomerId: {}, EventType: {}, Type: {}, Status: {}, Number: {}", 
                    accountId, customerId, eventType, accountType, accountStatus, accountNumber);
            
            switch (eventType) {
                case "ACCOUNT_CREATED" -> accountCreationService.processAccountCreated(accountId, 
                        customerId, accountType, accountStatus, accountNumber, routingNumber, 
                        createdDate, createdBy, branchCode, productCode, currency, isJointAccount, 
                        riskProfile);
                
                case "ACCOUNT_OPENED" -> accountCreationService.processAccountOpened(accountId, 
                        customerId, accountType, accountNumber, createdDate, productCode, currency);
                
                case "ACCOUNT_ACTIVATED" -> accountCreationService.processAccountActivated(accountId, 
                        customerId, accountType, accountStatus, createdDate);
                
                default -> log.warn("Unknown account created event type: {}", eventType);
            }
            
            accountNotificationService.sendAccountCreationNotification(customerId, accountId, eventType, 
                    accountType, accountNumber, accountStatus);
            
            accountCreationService.updateAccountMetrics(eventType, accountType, accountStatus, 
                    productCode, isJointAccount, riskProfile);
            
            auditService.auditFinancialEvent(
                    "ACCOUNT_CREATED_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Account created event %s - Type: %s, Number: %s, Status: %s", 
                            eventType, accountType, accountNumber, accountStatus),
                    Map.of(
                            "accountId", accountId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "accountType", accountType,
                            "accountStatus", accountStatus,
                            "accountNumber", accountNumber,
                            "productCode", productCode,
                            "currency", currency,
                            "isJointAccount", isJointAccount.toString(),
                            "riskProfile", riskProfile
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Account created event processing failed - AccountId: {}, CustomerId: {}, EventType: {}, Error: {}", 
                    accountId, customerId, eventType, e.getMessage(), e);
            throw new RuntimeException("Account created event processing failed", e);
        }
    }
}