package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.service.MerchantDisputeService;
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
public class MerchantDisputeResponseEventsConsumer {
    
    private final MerchantDisputeService merchantDisputeService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"merchant-dispute-response-events", "merchant-response-submitted"},
        groupId = "payment-service-merchant-dispute-response-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000)
    )
    @Transactional
    public void handleMerchantDisputeResponseEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID responseId = null;
        UUID disputeId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            responseId = UUID.fromString((String) event.get("responseId"));
            disputeId = UUID.fromString((String) event.get("disputeId"));
            eventType = (String) event.get("eventType");
            String merchantId = (String) event.get("merchantId");
            String responseType = (String) event.get("responseType");
            String responseStatus = (String) event.get("responseStatus");
            BigDecimal disputeAmount = new BigDecimal(event.get("disputeAmount").toString());
            String currency = (String) event.get("currency");
            LocalDateTime responseDate = LocalDateTime.parse((String) event.get("responseDate"));
            String responseText = (String) event.getOrDefault("responseText", "");
            
            log.info("Merchant dispute response event - ResponseId: {}, DisputeId: {}, MerchantId: {}, Type: {}, Status: {}", 
                    responseId, disputeId, merchantId, responseType, responseStatus);
            
            merchantDisputeService.processMerchantResponse(responseId, disputeId, merchantId, 
                    responseType, responseStatus, disputeAmount, currency, responseDate, responseText);
            
            auditService.auditFinancialEvent(
                    "MERCHANT_DISPUTE_RESPONSE_EVENT_PROCESSED",
                    merchantId,
                    String.format("Merchant dispute response %s - Type: %s, Status: %s, Amount: %s %s", 
                            eventType, responseType, responseStatus, disputeAmount, currency),
                    Map.of(
                            "responseId", responseId.toString(),
                            "disputeId", disputeId.toString(),
                            "merchantId", merchantId,
                            "eventType", eventType,
                            "responseType", responseType,
                            "responseStatus", responseStatus,
                            "disputeAmount", disputeAmount.toString(),
                            "currency", currency
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Merchant dispute response event processing failed - ResponseId: {}, DisputeId: {}, Error: {}", 
                    responseId, disputeId, e.getMessage(), e);
            throw new RuntimeException("Merchant dispute response event processing failed", e);
        }
    }
}