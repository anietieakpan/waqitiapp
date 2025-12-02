package com.waqiti.lending.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.lending.service.LoanRefinanceService;
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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoanRefinanceEventsConsumer {
    
    private final LoanRefinanceService loanRefinanceService;
    private final LoanNotificationService loanNotificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"loan-refinance-events", "loan-refinance-requested", "loan-refinance-approved"},
        groupId = "lending-service-loan-refinance-group",
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
    public void handleLoanRefinanceEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("LOAN REFINANCE: Processing loan refinance event - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID refinanceId = null;
        UUID originalLoanId = null;
        UUID borrowerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            refinanceId = UUID.fromString((String) event.get("refinanceId"));
            originalLoanId = UUID.fromString((String) event.get("originalLoanId"));
            borrowerId = UUID.fromString((String) event.get("borrowerId"));
            eventType = (String) event.get("eventType");
            String refinanceStatus = (String) event.get("refinanceStatus");
            BigDecimal originalBalance = new BigDecimal(event.get("originalBalance").toString());
            BigDecimal newLoanAmount = new BigDecimal(event.get("newLoanAmount").toString());
            BigDecimal originalRate = new BigDecimal(event.get("originalRate").toString());
            BigDecimal newRate = new BigDecimal(event.get("newRate").toString());
            String currency = (String) event.get("currency");
            Integer newTermMonths = (Integer) event.get("newTermMonths");
            BigDecimal cashOut = event.containsKey("cashOut") ? 
                    new BigDecimal(event.get("cashOut").toString()) : BigDecimal.ZERO;
            BigDecimal monthlySavings = event.containsKey("monthlySavings") ? 
                    new BigDecimal(event.get("monthlySavings").toString()) : BigDecimal.ZERO;
            String refinanceReason = (String) event.get("refinanceReason");
            UUID newLoanId = event.containsKey("newLoanId") ? 
                    UUID.fromString((String) event.get("newLoanId")) : null;
            
            log.info("Loan refinance event - RefinanceId: {}, OriginalLoanId: {}, BorrowerId: {}, EventType: {}, Status: {}, OriginalRate: {}%, NewRate: {}%", 
                    refinanceId, originalLoanId, borrowerId, eventType, refinanceStatus, originalRate, newRate);
            
            validateLoanRefinanceEvent(refinanceId, originalLoanId, borrowerId, eventType, 
                    refinanceStatus, originalBalance, newLoanAmount);
            
            switch (eventType) {
                case "REFINANCE_REQUESTED" -> processRefinanceRequest(refinanceId, originalLoanId, 
                        borrowerId, originalBalance, newLoanAmount, originalRate, newRate, currency, 
                        newTermMonths, cashOut, refinanceReason);
                
                case "REFINANCE_APPROVED" -> processRefinanceApproval(refinanceId, originalLoanId, 
                        borrowerId, newLoanAmount, newRate, currency, newTermMonths, monthlySavings, 
                        newLoanId);
                
                case "REFINANCE_REJECTED" -> processRefinanceRejection(refinanceId, originalLoanId, 
                        borrowerId, refinanceReason);
                
                case "REFINANCE_COMPLETED" -> processRefinanceCompletion(refinanceId, originalLoanId, 
                        newLoanId, borrowerId, newLoanAmount, currency);
                
                default -> log.warn("Unknown refinance event type: {}", eventType);
            }
            
            notifyBorrower(borrowerId, refinanceId, eventType, refinanceStatus, originalRate, 
                    newRate, monthlySavings, currency);
            
            loanRefinanceService.updateRefinanceMetrics(eventType, refinanceStatus, originalRate, 
                    newRate, monthlySavings);
            
            auditService.auditFinancialEvent(
                    "LOAN_REFINANCE_EVENT_PROCESSED",
                    borrowerId.toString(),
                    String.format("Loan refinance event %s - Status: %s, OriginalRate: %s%%, NewRate: %s%%", 
                            eventType, refinanceStatus, originalRate, newRate),
                    Map.of(
                            "refinanceId", refinanceId.toString(),
                            "originalLoanId", originalLoanId.toString(),
                            "borrowerId", borrowerId.toString(),
                            "eventType", eventType,
                            "refinanceStatus", refinanceStatus,
                            "originalRate", originalRate.toString(),
                            "newRate", newRate.toString(),
                            "monthlySavings", monthlySavings.toString(),
                            "currency", currency
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Loan refinance event processing failed - RefinanceId: {}, OriginalLoanId: {}, BorrowerId: {}, EventType: {}, Error: {}", 
                    refinanceId, originalLoanId, borrowerId, eventType, e.getMessage(), e);
            throw new RuntimeException("Loan refinance event processing failed", e);
        }
    }
    
    private void validateLoanRefinanceEvent(UUID refinanceId, UUID originalLoanId, UUID borrowerId,
                                          String eventType, String refinanceStatus,
                                          BigDecimal originalBalance, BigDecimal newLoanAmount) {
        if (refinanceId == null || originalLoanId == null || borrowerId == null) {
            throw new IllegalArgumentException("Refinance ID, Original Loan ID, and Borrower ID are required");
        }
        
        if (eventType == null || eventType.trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }
        
        if (refinanceStatus == null || refinanceStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("Refinance status is required");
        }
        
        if (originalBalance == null || originalBalance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid original balance");
        }
        
        if (newLoanAmount == null || newLoanAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid new loan amount");
        }
    }
    
    private void processRefinanceRequest(UUID refinanceId, UUID originalLoanId, UUID borrowerId,
                                       BigDecimal originalBalance, BigDecimal newLoanAmount,
                                       BigDecimal originalRate, BigDecimal newRate, String currency,
                                       Integer newTermMonths, BigDecimal cashOut, String refinanceReason) {
        log.info("Processing REFINANCE REQUEST - RefinanceId: {}, OriginalLoanId: {}, Amount: {} {}", 
                refinanceId, originalLoanId, newLoanAmount, currency);
        
        loanRefinanceService.processRefinanceRequest(refinanceId, originalLoanId, borrowerId, 
                originalBalance, newLoanAmount, originalRate, newRate, currency, newTermMonths, 
                cashOut, refinanceReason);
    }
    
    private void processRefinanceApproval(UUID refinanceId, UUID originalLoanId, UUID borrowerId,
                                        BigDecimal newLoanAmount, BigDecimal newRate, String currency,
                                        Integer newTermMonths, BigDecimal monthlySavings, UUID newLoanId) {
        log.info("Processing REFINANCE APPROVAL - RefinanceId: {}, NewLoanId: {}, Amount: {} {}, Rate: {}%", 
                refinanceId, newLoanId, newLoanAmount, currency, newRate);
        
        loanRefinanceService.processRefinanceApproval(refinanceId, originalLoanId, borrowerId, 
                newLoanAmount, newRate, currency, newTermMonths, monthlySavings, newLoanId);
    }
    
    private void processRefinanceRejection(UUID refinanceId, UUID originalLoanId, UUID borrowerId,
                                         String refinanceReason) {
        log.warn("Processing REFINANCE REJECTION - RefinanceId: {}, Reason: {}", 
                refinanceId, refinanceReason);
        
        loanRefinanceService.processRefinanceRejection(refinanceId, originalLoanId, borrowerId, 
                refinanceReason);
    }
    
    private void processRefinanceCompletion(UUID refinanceId, UUID originalLoanId, UUID newLoanId,
                                          UUID borrowerId, BigDecimal newLoanAmount, String currency) {
        log.info("Processing REFINANCE COMPLETION - RefinanceId: {}, OriginalLoanId: {}, NewLoanId: {}", 
                refinanceId, originalLoanId, newLoanId);
        
        loanRefinanceService.processRefinanceCompletion(refinanceId, originalLoanId, newLoanId, 
                borrowerId, newLoanAmount, currency);
    }
    
    private void notifyBorrower(UUID borrowerId, UUID refinanceId, String eventType, String refinanceStatus,
                               BigDecimal originalRate, BigDecimal newRate, BigDecimal monthlySavings,
                               String currency) {
        try {
            loanNotificationService.sendRefinanceNotification(borrowerId, refinanceId, eventType, 
                    refinanceStatus, originalRate, newRate, monthlySavings, currency);
            
            log.info("Borrower notified - BorrowerId: {}, RefinanceId: {}, Status: {}", 
                    borrowerId, refinanceId, refinanceStatus);
            
        } catch (Exception e) {
            log.error("Failed to notify borrower - BorrowerId: {}, RefinanceId: {}", borrowerId, refinanceId, e);
        }
    }
    
    @KafkaListener(
        topics = {"loan-refinance-events.DLQ", "loan-refinance-requested.DLQ", "loan-refinance-approved.DLQ"},
        groupId = "lending-service-loan-refinance-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: Loan refinance event sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID refinanceId = event.containsKey("refinanceId") ? 
                    UUID.fromString((String) event.get("refinanceId")) : null;
            UUID borrowerId = event.containsKey("borrowerId") ? 
                    UUID.fromString((String) event.get("borrowerId")) : null;
            String eventType = (String) event.get("eventType");
            
            log.error("DLQ: Loan refinance event failed permanently - RefinanceId: {}, BorrowerId: {}, EventType: {} - MANUAL REVIEW REQUIRED", 
                    refinanceId, borrowerId, eventType);
            
            if (refinanceId != null && borrowerId != null) {
                loanRefinanceService.markForManualReview(refinanceId, borrowerId, eventType, 
                        "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse loan refinance DLQ event: {}", eventJson, e);
        }
    }
}