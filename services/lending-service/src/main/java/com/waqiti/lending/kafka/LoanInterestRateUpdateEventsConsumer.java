package com.waqiti.lending.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.lending.service.LoanInterestRateService;
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
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoanInterestRateUpdateEventsConsumer {
    
    private final LoanInterestRateService loanInterestRateService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"loan-interest-rate-update-events", "rate-updated", "rate-adjustment"},
        groupId = "lending-service-loan-rate-update-group",
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
    public void handleLoanRateUpdateEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("LOAN RATE UPDATE: Processing loan interest rate update event - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        UUID updateId = null;
        UUID loanId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            updateId = UUID.fromString((String) event.get("updateId"));
            loanId = UUID.fromString((String) event.get("loanId"));
            eventType = (String) event.get("eventType");
            BigDecimal oldRate = new BigDecimal(event.get("oldRate").toString());
            BigDecimal newRate = new BigDecimal(event.get("newRate").toString());
            LocalDate effectiveDate = LocalDate.parse((String) event.get("effectiveDate"));
            String updateReason = (String) event.get("updateReason");
            String rateType = (String) event.get("rateType");
            
            log.info("Loan rate update event - UpdateId: {}, LoanId: {}, OldRate: {}%, NewRate: {}%, Effective: {}", 
                    updateId, loanId, oldRate, newRate, effectiveDate);
            
            switch (eventType) {
                case "RATE_UPDATED" -> loanInterestRateService.processRateUpdate(updateId, loanId, 
                        oldRate, newRate, effectiveDate, updateReason, rateType);
                case "RATE_ADJUSTMENT" -> loanInterestRateService.processRateAdjustment(updateId, loanId, 
                        oldRate, newRate, effectiveDate, updateReason);
                default -> log.warn("Unknown rate update event type: {}", eventType);
            }
            
            auditService.auditFinancialEvent(
                    "LOAN_RATE_UPDATE_EVENT_PROCESSED",
                    loanId.toString(),
                    String.format("Loan rate update %s - Old: %s%%, New: %s%%, Effective: %s", 
                            eventType, oldRate, newRate, effectiveDate),
                    Map.of(
                            "updateId", updateId.toString(),
                            "loanId", loanId.toString(),
                            "eventType", eventType,
                            "oldRate", oldRate.toString(),
                            "newRate", newRate.toString(),
                            "effectiveDate", effectiveDate.toString(),
                            "updateReason", updateReason
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Loan rate update event processing failed - UpdateId: {}, LoanId: {}, EventType: {}, Error: {}", 
                    updateId, loanId, eventType, e.getMessage(), e);
            throw new RuntimeException("Loan rate update event processing failed", e);
        }
    }
}