package com.waqiti.lending.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.lending.service.LoanClosureService;
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
public class LoanClosureEventsConsumer {
    
    private final LoanClosureService loanClosureService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"loan-closure-events", "loan-paid-off", "loan-closed"},
        groupId = "lending-service-loan-closure-group",
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
    public void handleLoanClosureEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("LOAN CLOSURE: Processing loan closure event - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        UUID closureId = null;
        UUID loanId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            closureId = UUID.fromString((String) event.get("closureId"));
            loanId = UUID.fromString((String) event.get("loanId"));
            UUID borrowerId = UUID.fromString((String) event.get("borrowerId"));
            eventType = (String) event.get("eventType");
            String closureReason = (String) event.get("closureReason");
            LocalDate closureDate = LocalDate.parse((String) event.get("closureDate"));
            BigDecimal finalPaymentAmount = new BigDecimal(event.get("finalPaymentAmount").toString());
            String currency = (String) event.get("currency");
            BigDecimal totalInterestPaid = new BigDecimal(event.get("totalInterestPaid").toString());
            
            log.info("Loan closure event - ClosureId: {}, LoanId: {}, BorrowerId: {}, Reason: {}, FinalPayment: {} {}", 
                    closureId, loanId, borrowerId, closureReason, finalPaymentAmount, currency);
            
            switch (eventType) {
                case "LOAN_PAID_OFF" -> loanClosureService.processLoanPaidOff(closureId, loanId, 
                        borrowerId, closureDate, finalPaymentAmount, currency, totalInterestPaid);
                case "LOAN_CLOSED" -> loanClosureService.processLoanClosed(closureId, loanId, 
                        borrowerId, closureReason, closureDate, totalInterestPaid, currency);
                default -> log.warn("Unknown closure event type: {}", eventType);
            }
            
            auditService.auditFinancialEvent(
                    "LOAN_CLOSURE_EVENT_PROCESSED",
                    borrowerId.toString(),
                    String.format("Loan closure event %s - Reason: %s, FinalPayment: %s %s", 
                            eventType, closureReason, finalPaymentAmount, currency),
                    Map.of(
                            "closureId", closureId.toString(),
                            "loanId", loanId.toString(),
                            "borrowerId", borrowerId.toString(),
                            "eventType", eventType,
                            "closureReason", closureReason,
                            "finalPaymentAmount", finalPaymentAmount.toString(),
                            "totalInterestPaid", totalInterestPaid.toString(),
                            "currency", currency
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Loan closure event processing failed - ClosureId: {}, LoanId: {}, EventType: {}, Error: {}", 
                    closureId, loanId, eventType, e.getMessage(), e);
            throw new RuntimeException("Loan closure event processing failed", e);
        }
    }
}