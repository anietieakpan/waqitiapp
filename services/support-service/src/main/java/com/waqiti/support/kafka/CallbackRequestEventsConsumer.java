package com.waqiti.support.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.support.service.CallbackRequestService;
import com.waqiti.support.service.SupportNotificationService;
import com.waqiti.common.exception.SupportProcessingException;
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
public class CallbackRequestEventsConsumer {
    
    private final CallbackRequestService callbackService;
    private final SupportNotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"callback-request-events", "callback-requested", "callback-scheduled", "callback-completed"},
        groupId = "support-service-callback-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000)
    )
    @Transactional
    public void handleCallbackRequestEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID callbackId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            callbackId = UUID.fromString((String) event.get("callbackId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            String phoneNumber = (String) event.get("phoneNumber");
            String preferredTime = (String) event.get("preferredTime");
            String reason = (String) event.get("reason");
            String priority = (String) event.get("priority");
            LocalDateTime timestamp = LocalDateTime.parse((String) event.get("timestamp"));
            
            log.info("Processing callback request event - CallbackId: {}, CustomerId: {}, Type: {}", 
                    callbackId, customerId, eventType);
            
            switch (eventType) {
                case "CALLBACK_REQUESTED":
                    callbackService.processCallbackRequest(callbackId, customerId, phoneNumber,
                            preferredTime, reason, priority, timestamp);
                    break;
                case "CALLBACK_SCHEDULED":
                    callbackService.scheduleCallback(callbackId, customerId,
                            LocalDateTime.parse((String) event.get("scheduledTime")), timestamp);
                    break;
                case "CALLBACK_COMPLETED":
                    callbackService.completeCallback(callbackId, customerId,
                            (String) event.get("outcome"), timestamp);
                    break;
                default:
                    callbackService.processGenericCallbackEvent(callbackId, eventType, event, timestamp);
            }
            
            notificationService.sendCallbackNotification(callbackId, customerId, eventType,
                    phoneNumber, preferredTime, timestamp);
            
            auditService.auditFinancialEvent(
                    "CALLBACK_REQUEST_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Callback request event processed - Type: %s, Priority: %s", eventType, priority),
                    Map.of(
                            "callbackId", callbackId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "phoneNumber", phoneNumber.replaceAll("(\\d{3})\\d{3}(\\d{4})", "$1***$2"), // Masked
                            "preferredTime", preferredTime,
                            "reason", reason,
                            "priority", priority
                    )
            );
            
            acknowledgment.acknowledge();
            log.info("Successfully processed callback request event - CallbackId: {}, EventType: {}", 
                    callbackId, eventType);
            
        } catch (Exception e) {
            log.error("Callback request event processing failed - CallbackId: {}, CustomerId: {}, Error: {}", 
                    callbackId, customerId, e.getMessage(), e);
            throw new SupportProcessingException("Callback request event processing failed", e);
        }
    }
}