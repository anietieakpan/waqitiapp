package com.waqiti.lending.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.lending.service.LoanCollateralService;
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
public class LoanCollateralEventsConsumer {
    
    private final LoanCollateralService loanCollateralService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"loan-collateral-events", "collateral-valued", "collateral-seized"},
        groupId = "lending-service-loan-collateral-group",
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
    public void handleLoanCollateralEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("LOAN COLLATERAL: Processing loan collateral event - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        UUID collateralId = null;
        UUID loanId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            collateralId = UUID.fromString((String) event.get("collateralId"));
            loanId = UUID.fromString((String) event.get("loanId"));
            eventType = (String) event.get("eventType");
            String collateralType = (String) event.get("collateralType");
            BigDecimal collateralValue = new BigDecimal(event.get("collateralValue").toString());
            String currency = (String) event.get("currency");
            String collateralStatus = (String) event.get("collateralStatus");
            
            log.info("Loan collateral event - CollateralId: {}, LoanId: {}, Type: {}, Value: {} {}, Status: {}", 
                    collateralId, loanId, collateralType, collateralValue, currency, collateralStatus);
            
            switch (eventType) {
                case "COLLATERAL_ADDED" -> loanCollateralService.processCollateralAdded(collateralId, 
                        loanId, collateralType, collateralValue, currency);
                case "COLLATERAL_VALUED" -> loanCollateralService.processCollateralValued(collateralId, 
                        loanId, collateralValue, currency);
                case "COLLATERAL_SEIZED" -> loanCollateralService.processCollateralSeized(collateralId, 
                        loanId, collateralType, collateralValue, currency);
                default -> log.warn("Unknown collateral event type: {}", eventType);
            }
            
            auditService.auditFinancialEvent(
                    "LOAN_COLLATERAL_EVENT_PROCESSED",
                    loanId.toString(),
                    String.format("Loan collateral event %s - Type: %s, Value: %s %s", 
                            eventType, collateralType, collateralValue, currency),
                    Map.of(
                            "collateralId", collateralId.toString(),
                            "loanId", loanId.toString(),
                            "eventType", eventType,
                            "collateralType", collateralType,
                            "collateralValue", collateralValue.toString(),
                            "currency", currency
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Loan collateral event processing failed - CollateralId: {}, LoanId: {}, EventType: {}, Error: {}", 
                    collateralId, loanId, eventType, e.getMessage(), e);
            throw new RuntimeException("Loan collateral event processing failed", e);
        }
    }
}