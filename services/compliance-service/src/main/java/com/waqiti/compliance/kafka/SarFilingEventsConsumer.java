package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.validation.NullSafetyUtils;
import com.waqiti.compliance.service.SarFilingService;
import com.waqiti.compliance.service.ComplianceNotificationService;
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
public class SarFilingEventsConsumer {
    
    private final SarFilingService sarFilingService;
    private final ComplianceNotificationService complianceNotificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"sar-filing-events", "sar-required", "sar-filed"},
        groupId = "compliance-service-sar-filing-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleSarFilingEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.warn("SAR FILING: Processing SAR filing event - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        UUID sarId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            sarId = UUID.fromString((String) event.get("sarId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            UUID transactionId = event.containsKey("transactionId") ? 
                    UUID.fromString((String) event.get("transactionId")) : null;
            eventType = (String) event.get("eventType");
            String sarType = (String) event.get("sarType");
            String suspiciousActivity = (String) event.get("suspiciousActivity");
            // SAFETY FIX: Safe parsing with null check to prevent NumberFormatException
            BigDecimal suspiciousAmount = NullSafetyUtils.safeParseBigDecimal(
                event.get("suspiciousAmount") != null ? event.get("suspiciousAmount").toString() : null,
                BigDecimal.ZERO
            );
            String currency = (String) event.get("currency");
            LocalDateTime activityDate = LocalDateTime.parse((String) event.get("activityDate"));
            LocalDateTime filingDate = event.containsKey("filingDate") ? 
                    LocalDateTime.parse((String) event.get("filingDate")) : null;
            String filingStatus = (String) event.get("filingStatus");
            String regulatoryReference = (String) event.getOrDefault("regulatoryReference", "");
            String filedBy = (String) event.getOrDefault("filedBy", "");
            String priority = (String) event.getOrDefault("priority", "HIGH");
            
            log.error("SAR filing event - SarId: {}, CustomerId: {}, EventType: {}, Type: {}, Amount: {} {}, Status: {}", 
                    sarId, customerId, eventType, sarType, suspiciousAmount, currency, filingStatus);
            
            switch (eventType) {
                case "SAR_REQUIRED" -> sarFilingService.processSarRequired(sarId, customerId, 
                        transactionId, sarType, suspiciousActivity, suspiciousAmount, currency, 
                        activityDate, priority);
                
                case "SAR_FILED" -> sarFilingService.processSarFiled(sarId, customerId, transactionId, 
                        sarType, suspiciousActivity, suspiciousAmount, currency, activityDate, 
                        filingDate, regulatoryReference, filedBy);
                
                case "SAR_REJECTED" -> sarFilingService.processSarRejected(sarId, customerId, 
                        sarType, suspiciousActivity, suspiciousAmount, currency);
                
                default -> log.warn("Unknown SAR filing event type: {}", eventType);
            }
            
            complianceNotificationService.sendSarFilingNotification(customerId, sarId, eventType, 
                    sarType, suspiciousAmount, currency, filingStatus, priority);
            
            sarFilingService.updateSarMetrics(eventType, sarType, suspiciousAmount, filingStatus, 
                    priority);
            
            auditService.auditFinancialEvent(
                    "SAR_FILING_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("SAR filing event %s - Type: %s, Amount: %s %s, Status: %s", 
                            eventType, sarType, suspiciousAmount, currency, filingStatus),
                    Map.of(
                            "sarId", sarId.toString(),
                            "customerId", customerId.toString(),
                            "transactionId", transactionId != null ? transactionId.toString() : "NONE",
                            "eventType", eventType,
                            "sarType", sarType,
                            "suspiciousActivity", suspiciousActivity,
                            "suspiciousAmount", suspiciousAmount.toString(),
                            "currency", currency,
                            "filingStatus", filingStatus,
                            "regulatoryReference", regulatoryReference,
                            "priority", priority
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: SAR filing event processing failed - SarId: {}, CustomerId: {}, EventType: {}, Error: {}", 
                    sarId, customerId, eventType, e.getMessage(), e);
            throw new RuntimeException("SAR filing event processing failed", e);
        }
    }
}