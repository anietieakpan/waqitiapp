package com.waqiti.rewards.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.rewards.service.CashbackService;
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
public class CashbackEarnedEventsConsumer {
    
    private final CashbackService cashbackService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"cashback-earned-events", "cashback-calculated", "cashback-posted"},
        groupId = "rewards-service-cashback-earned-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000)
    )
    @Transactional
    public void handleCashbackEarnedEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID cashbackId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            cashbackId = UUID.fromString((String) event.get("cashbackId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            UUID transactionId = UUID.fromString((String) event.get("transactionId"));
            eventType = (String) event.get("eventType");
            BigDecimal cashbackAmount = new BigDecimal(event.get("cashbackAmount").toString());
            String currency = (String) event.get("currency");
            BigDecimal transactionAmount = new BigDecimal(event.get("transactionAmount").toString());
            BigDecimal cashbackRate = new BigDecimal(event.get("cashbackRate").toString());
            LocalDateTime earnedDate = LocalDateTime.parse((String) event.get("earnedDate"));
            String category = (String) event.get("category");
            String merchantId = (String) event.getOrDefault("merchantId", "");
            String campaignId = (String) event.getOrDefault("campaignId", "");
            
            log.info("Cashback earned event - CashbackId: {}, CustomerId: {}, Amount: {} {}, Rate: {}%, Category: {}", 
                    cashbackId, customerId, cashbackAmount, currency, cashbackRate, category);
            
            cashbackService.processCashbackEarned(cashbackId, customerId, transactionId, 
                    cashbackAmount, currency, transactionAmount, cashbackRate, earnedDate, 
                    category, merchantId, campaignId);
            
            auditService.auditFinancialEvent(
                    "CASHBACK_EARNED_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Cashback earned - Amount: %s %s, Rate: %s%%, Category: %s", 
                            cashbackAmount, currency, cashbackRate, category),
                    Map.of(
                            "cashbackId", cashbackId.toString(),
                            "customerId", customerId.toString(),
                            "transactionId", transactionId.toString(),
                            "eventType", eventType,
                            "cashbackAmount", cashbackAmount.toString(),
                            "currency", currency,
                            "cashbackRate", cashbackRate.toString(),
                            "category", category,
                            "merchantId", merchantId,
                            "campaignId", campaignId
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Cashback earned event processing failed - CashbackId: {}, CustomerId: {}, Error: {}", 
                    cashbackId, customerId, e.getMessage(), e);
            throw new RuntimeException("Cashback earned event processing failed", e);
        }
    }
}