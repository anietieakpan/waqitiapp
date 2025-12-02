package com.waqiti.lending.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.lending.service.LoanScheduleService;
import com.waqiti.lending.service.LoanNotificationService;
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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoanPaymentScheduleEventsConsumer {
    
    private final LoanScheduleService loanScheduleService;
    private final LoanNotificationService loanNotificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"loan-payment-schedule-events", "loan-schedule-updated", "loan-payment-due"},
        groupId = "lending-service-loan-schedule-group",
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
    public void handleLoanScheduleEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("LOAN SCHEDULE: Processing loan schedule event - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID scheduleId = null;
        UUID loanId = null;
        UUID borrowerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            scheduleId = UUID.fromString((String) event.get("scheduleId"));
            loanId = UUID.fromString((String) event.get("loanId"));
            borrowerId = UUID.fromString((String) event.get("borrowerId"));
            eventType = (String) event.get("eventType");
            String scheduleStatus = (String) event.get("scheduleStatus");
            Integer paymentNumber = (Integer) event.get("paymentNumber");
            BigDecimal paymentAmount = new BigDecimal(event.get("paymentAmount").toString());
            BigDecimal principalAmount = new BigDecimal(event.get("principalAmount").toString());
            BigDecimal interestAmount = new BigDecimal(event.get("interestAmount").toString());
            LocalDate dueDate = LocalDate.parse((String) event.get("dueDate"));
            String currency = (String) event.get("currency");
            BigDecimal remainingBalance = new BigDecimal(event.get("remainingBalance").toString());
            
            log.info("Loan schedule event - ScheduleId: {}, LoanId: {}, BorrowerId: {}, EventType: {}, PaymentNum: {}, Amount: {} {}, DueDate: {}", 
                    scheduleId, loanId, borrowerId, eventType, paymentNumber, paymentAmount, currency, dueDate);
            
            validateLoanScheduleEvent(scheduleId, loanId, borrowerId, eventType, paymentAmount);
            
            switch (eventType) {
                case "SCHEDULE_CREATED" -> loanScheduleService.processScheduleCreated(scheduleId, loanId, 
                        borrowerId, paymentNumber, paymentAmount, principalAmount, interestAmount, 
                        dueDate, currency, remainingBalance);
                
                case "SCHEDULE_UPDATED" -> loanScheduleService.processScheduleUpdated(scheduleId, loanId, 
                        borrowerId, paymentNumber, paymentAmount, principalAmount, interestAmount, 
                        dueDate, currency, remainingBalance);
                
                case "PAYMENT_DUE" -> loanScheduleService.processPaymentDue(scheduleId, loanId, 
                        borrowerId, paymentNumber, paymentAmount, dueDate, currency);
                
                case "PAYMENT_OVERDUE" -> loanScheduleService.processPaymentOverdue(scheduleId, loanId, 
                        borrowerId, paymentNumber, paymentAmount, dueDate, currency);
                
                default -> log.warn("Unknown schedule event type: {}", eventType);
            }
            
            notifyBorrower(borrowerId, loanId, scheduleId, eventType, paymentAmount, dueDate, currency);
            
            loanScheduleService.updateScheduleMetrics(eventType, scheduleStatus, paymentAmount);
            
            auditService.auditFinancialEvent(
                    "LOAN_SCHEDULE_EVENT_PROCESSED",
                    borrowerId.toString(),
                    String.format("Loan schedule event %s - Payment %d: %s %s due %s", 
                            eventType, paymentNumber, paymentAmount, currency, dueDate),
                    Map.of(
                            "scheduleId", scheduleId.toString(),
                            "loanId", loanId.toString(),
                            "borrowerId", borrowerId.toString(),
                            "eventType", eventType,
                            "paymentNumber", paymentNumber,
                            "paymentAmount", paymentAmount.toString(),
                            "dueDate", dueDate.toString(),
                            "currency", currency
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Loan schedule event processing failed - ScheduleId: {}, LoanId: {}, BorrowerId: {}, EventType: {}, Error: {}", 
                    scheduleId, loanId, borrowerId, eventType, e.getMessage(), e);
            throw new RuntimeException("Loan schedule event processing failed", e);
        }
    }
    
    private void validateLoanScheduleEvent(UUID scheduleId, UUID loanId, UUID borrowerId,
                                         String eventType, BigDecimal paymentAmount) {
        if (scheduleId == null || loanId == null || borrowerId == null) {
            throw new IllegalArgumentException("Schedule ID, Loan ID, and Borrower ID are required");
        }
        
        if (eventType == null || eventType.trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }
        
        if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid payment amount");
        }
    }
    
    private void notifyBorrower(UUID borrowerId, UUID loanId, UUID scheduleId, String eventType,
                               BigDecimal paymentAmount, LocalDate dueDate, String currency) {
        try {
            if ("PAYMENT_DUE".equals(eventType) || "PAYMENT_OVERDUE".equals(eventType)) {
                loanNotificationService.sendScheduleNotification(borrowerId, loanId, scheduleId, 
                        eventType, paymentAmount, dueDate, currency);
                
                log.info("Borrower notified - BorrowerId: {}, ScheduleId: {}, Event: {}", 
                        borrowerId, scheduleId, eventType);
            }
        } catch (Exception e) {
            log.error("Failed to notify borrower - BorrowerId: {}, ScheduleId: {}", borrowerId, scheduleId, e);
        }
    }
    
    @KafkaListener(
        topics = {"loan-payment-schedule-events.DLQ", "loan-schedule-updated.DLQ", "loan-payment-due.DLQ"},
        groupId = "lending-service-loan-schedule-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: Loan schedule event sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID scheduleId = event.containsKey("scheduleId") ? 
                    UUID.fromString((String) event.get("scheduleId")) : null;
            UUID borrowerId = event.containsKey("borrowerId") ? 
                    UUID.fromString((String) event.get("borrowerId")) : null;
            String eventType = (String) event.get("eventType");
            
            log.error("DLQ: Loan schedule event failed permanently - ScheduleId: {}, BorrowerId: {}, EventType: {} - MANUAL REVIEW REQUIRED", 
                    scheduleId, borrowerId, eventType);
            
            if (scheduleId != null && borrowerId != null) {
                loanScheduleService.markForManualReview(scheduleId, borrowerId, eventType, 
                        "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse loan schedule DLQ event: {}", eventJson, e);
        }
    }
}