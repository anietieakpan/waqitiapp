package com.waqiti.account.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.account.service.AccountVerificationService;
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
public class AccountVerificationEventsConsumer {
    
    private final AccountVerificationService accountVerificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"account-verification-events", "account-verified", "verification-failed"},
        groupId = "account-service-account-verification-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000)
    )
    @Transactional
    public void handleAccountVerificationEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID verificationId = null;
        UUID accountId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            verificationId = UUID.fromString((String) event.get("verificationId"));
            accountId = UUID.fromString((String) event.get("accountId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            String verificationType = (String) event.get("verificationType");
            String verificationStatus = (String) event.get("verificationStatus");
            LocalDateTime verificationDate = LocalDateTime.parse((String) event.get("verificationDate"));
            String verificationMethod = (String) event.get("verificationMethod");
            String verifiedBy = (String) event.getOrDefault("verifiedBy", "SYSTEM");
            String failureReason = (String) event.getOrDefault("failureReason", "");
            
            log.info("Account verification event - VerificationId: {}, AccountId: {}, Type: {}, Status: {}, Method: {}", 
                    verificationId, accountId, verificationType, verificationStatus, verificationMethod);
            
            accountVerificationService.processAccountVerification(verificationId, accountId, 
                    customerId, verificationType, verificationStatus, verificationDate, 
                    verificationMethod, verifiedBy, failureReason);
            
            auditService.auditFinancialEvent(
                    "ACCOUNT_VERIFICATION_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Account verification %s - Type: %s, Status: %s, Method: %s", 
                            eventType, verificationType, verificationStatus, verificationMethod),
                    Map.of(
                            "verificationId", verificationId.toString(),
                            "accountId", accountId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "verificationType", verificationType,
                            "verificationStatus", verificationStatus,
                            "verificationMethod", verificationMethod,
                            "verifiedBy", verifiedBy,
                            "failureReason", failureReason
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Account verification event processing failed - VerificationId: {}, AccountId: {}, Error: {}", 
                    verificationId, accountId, e.getMessage(), e);
            throw new RuntimeException("Account verification event processing failed", e);
        }
    }
}