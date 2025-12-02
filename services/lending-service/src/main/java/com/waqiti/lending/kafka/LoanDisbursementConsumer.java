package com.waqiti.lending.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.lending.service.LoanDisbursementService;
import com.waqiti.lending.service.LoanAccountingService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoanDisbursementConsumer {
    
    private final LoanDisbursementService loanDisbursementService;
    private final LoanAccountingService loanAccountingService;
    private final LoanNotificationService loanNotificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"loan-disbursement-events", "loan-disbursement"},
        groupId = "lending-service-disbursement-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleLoanDisbursement(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("LOAN DISBURSEMENT: Processing disbursement event - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID disbursementId = null;
        UUID loanId = null;
        String disbursementStatus = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            disbursementId = UUID.fromString((String) event.get("disbursementId"));
            loanId = UUID.fromString((String) event.get("loanId"));
            UUID borrowerId = UUID.fromString((String) event.get("borrowerId"));
            String loanType = (String) event.get("loanType");
            disbursementStatus = (String) event.get("disbursementStatus");
            BigDecimal disbursementAmount = new BigDecimal(event.get("disbursementAmount").toString());
            String currency = (String) event.get("currency");
            String disbursementMethod = (String) event.get("disbursementMethod");
            String destinationAccountId = (String) event.get("destinationAccountId");
            LocalDateTime disbursementTimestamp = LocalDateTime.parse((String) event.get("timestamp"));
            BigDecimal interestRate = new BigDecimal(event.get("interestRate").toString());
            Integer loanTerm = (Integer) event.get("loanTerm");
            String loanTermUnit = (String) event.get("loanTermUnit");
            LocalDate firstPaymentDate = LocalDate.parse((String) event.get("firstPaymentDate"));
            BigDecimal monthlyPayment = new BigDecimal(event.get("monthlyPayment").toString());
            @SuppressWarnings("unchecked")
            Map<String, Object> loanTerms = (Map<String, Object>) event.getOrDefault("loanTerms", Map.of());
            String failureReason = (String) event.get("failureReason");
            
            log.info("Loan disbursement - DisbursementId: {}, LoanId: {}, Status: {}, Amount: {} {}, Type: {}, Method: {}", 
                    disbursementId, loanId, disbursementStatus, disbursementAmount, currency, 
                    loanType, disbursementMethod);
            
            validateLoanDisbursement(disbursementId, loanId, borrowerId, disbursementStatus, 
                    disbursementAmount);
            
            processDisbursementByStatus(disbursementId, loanId, borrowerId, loanType, 
                    disbursementStatus, disbursementAmount, currency, disbursementMethod, 
                    destinationAccountId, interestRate, loanTerm, loanTermUnit, firstPaymentDate, 
                    monthlyPayment, loanTerms, failureReason, disbursementTimestamp);
            
            if ("DISBURSED".equals(disbursementStatus)) {
                handleSuccessfulDisbursement(disbursementId, loanId, borrowerId, disbursementAmount, 
                        currency, destinationAccountId, interestRate, loanTerm, firstPaymentDate, 
                        monthlyPayment, disbursementTimestamp);
            } else if ("FAILED".equals(disbursementStatus)) {
                handleFailedDisbursement(disbursementId, loanId, borrowerId, disbursementAmount, 
                        currency, failureReason);
            } else if ("PENDING".equals(disbursementStatus)) {
                handlePendingDisbursement(disbursementId, loanId, borrowerId, disbursementAmount, 
                        destinationAccountId);
            }
            
            recordDisbursementAccounting(disbursementId, loanId, disbursementStatus, 
                    disbursementAmount, currency, interestRate, loanTerm);
            
            notifyBorrower(borrowerId, loanId, disbursementId, disbursementStatus, 
                    disbursementAmount, currency, destinationAccountId, firstPaymentDate);
            
            updateDisbursementMetrics(loanType, disbursementStatus, disbursementAmount, 
                    disbursementMethod);
            
            auditLoanDisbursement(disbursementId, loanId, borrowerId, loanType, disbursementStatus, 
                    disbursementAmount, currency, processingStartTime);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Loan disbursement processed - DisbursementId: {}, Status: {}, ProcessingTime: {}ms", 
                    disbursementId, disbursementStatus, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Loan disbursement processing failed - DisbursementId: {}, LoanId: {}, Status: {}, Error: {}", 
                    disbursementId, loanId, disbursementStatus, e.getMessage(), e);
            
            if (disbursementId != null && loanId != null) {
                handleDisbursementFailure(disbursementId, loanId, disbursementStatus, e);
            }
            
            throw new RuntimeException("Loan disbursement processing failed", e);
        }
    }
    
    private void validateLoanDisbursement(UUID disbursementId, UUID loanId, UUID borrowerId,
                                         String disbursementStatus, BigDecimal disbursementAmount) {
        if (disbursementId == null || loanId == null || borrowerId == null) {
            throw new IllegalArgumentException("Disbursement ID, Loan ID, and Borrower ID are required");
        }
        
        if (disbursementStatus == null || disbursementStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("Disbursement status is required");
        }
        
        List<String> validStatuses = List.of("PENDING", "DISBURSED", "FAILED", "REVERSED");
        if (!validStatuses.contains(disbursementStatus)) {
            throw new IllegalArgumentException("Invalid disbursement status: " + disbursementStatus);
        }
        
        if (disbursementAmount == null || disbursementAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid disbursement amount");
        }
        
        log.debug("Loan disbursement validation passed - DisbursementId: {}", disbursementId);
    }
    
    private void processDisbursementByStatus(UUID disbursementId, UUID loanId, UUID borrowerId,
                                            String loanType, String disbursementStatus,
                                            BigDecimal disbursementAmount, String currency,
                                            String disbursementMethod, String destinationAccountId,
                                            BigDecimal interestRate, Integer loanTerm,
                                            String loanTermUnit, LocalDate firstPaymentDate,
                                            BigDecimal monthlyPayment, Map<String, Object> loanTerms,
                                            String failureReason, LocalDateTime disbursementTimestamp) {
        try {
            loanDisbursementService.processDisbursement(
                    disbursementId, loanId, borrowerId, loanType, disbursementStatus,
                    disbursementAmount, currency, disbursementMethod, destinationAccountId,
                    interestRate, loanTerm, loanTermUnit, firstPaymentDate, monthlyPayment,
                    loanTerms, failureReason, disbursementTimestamp);
            
            log.debug("Disbursement status processing completed - DisbursementId: {}, Status: {}", 
                    disbursementId, disbursementStatus);
            
        } catch (Exception e) {
            log.error("Failed to process disbursement by status - DisbursementId: {}, Status: {}", 
                    disbursementId, disbursementStatus, e);
            throw new RuntimeException("Disbursement status processing failed", e);
        }
    }
    
    private void handleSuccessfulDisbursement(UUID disbursementId, UUID loanId, UUID borrowerId,
                                             BigDecimal disbursementAmount, String currency,
                                             String destinationAccountId, BigDecimal interestRate,
                                             Integer loanTerm, LocalDate firstPaymentDate,
                                             BigDecimal monthlyPayment, LocalDateTime disbursementTimestamp) {
        try {
            log.info("Processing successful disbursement - DisbursementId: {}, Amount: {} {}, Destination: {}", 
                    disbursementId, disbursementAmount, currency, destinationAccountId);
            
            loanDisbursementService.recordSuccessfulDisbursement(disbursementId, loanId, borrowerId, 
                    disbursementAmount, currency, destinationAccountId, disbursementTimestamp);
            
            loanDisbursementService.activateLoan(loanId, disbursementAmount, interestRate, 
                    loanTerm, firstPaymentDate);
            
            loanDisbursementService.createRepaymentSchedule(loanId, disbursementAmount, interestRate, 
                    loanTerm, firstPaymentDate, monthlyPayment);
            
            loanDisbursementService.updateBorrowerLoanBalance(borrowerId, disbursementAmount);
            
        } catch (Exception e) {
            log.error("Failed to handle successful disbursement - DisbursementId: {}", disbursementId, e);
        }
    }
    
    private void handleFailedDisbursement(UUID disbursementId, UUID loanId, UUID borrowerId,
                                         BigDecimal disbursementAmount, String currency,
                                         String failureReason) {
        try {
            log.error("Processing failed disbursement - DisbursementId: {}, Reason: {}", 
                    disbursementId, failureReason);
            
            loanDisbursementService.recordFailedDisbursement(disbursementId, loanId, borrowerId, 
                    disbursementAmount, currency, failureReason);
            
            loanDisbursementService.cancelLoan(loanId, "DISBURSEMENT_FAILED: " + failureReason);
            
            loanDisbursementService.createManualReviewTask(disbursementId, loanId, borrowerId, 
                    failureReason);
            
        } catch (Exception e) {
            log.error("Failed to handle failed disbursement - DisbursementId: {}", disbursementId, e);
        }
    }
    
    private void handlePendingDisbursement(UUID disbursementId, UUID loanId, UUID borrowerId,
                                          BigDecimal disbursementAmount, String destinationAccountId) {
        try {
            log.info("Processing pending disbursement - DisbursementId: {}, Amount: {}, Destination: {}", 
                    disbursementId, disbursementAmount, destinationAccountId);
            
            loanDisbursementService.trackPendingDisbursement(disbursementId, loanId, borrowerId, 
                    disbursementAmount, destinationAccountId);
            
            loanDisbursementService.monitorDisbursementProgress(disbursementId, loanId);
            
        } catch (Exception e) {
            log.error("Failed to handle pending disbursement - DisbursementId: {}", disbursementId, e);
        }
    }
    
    private void recordDisbursementAccounting(UUID disbursementId, UUID loanId, String disbursementStatus,
                                             BigDecimal disbursementAmount, String currency,
                                             BigDecimal interestRate, Integer loanTerm) {
        try {
            loanAccountingService.recordDisbursementAccounting(disbursementId, loanId, 
                    disbursementStatus, disbursementAmount, currency, interestRate, loanTerm);
            
            if ("DISBURSED".equals(disbursementStatus)) {
                loanAccountingService.createLoanReceivableEntry(loanId, disbursementAmount, currency);
                
                loanAccountingService.recordInterestIncome(loanId, disbursementAmount, interestRate, 
                        loanTerm);
            }
            
            log.debug("Disbursement accounting recorded - DisbursementId: {}", disbursementId);
            
        } catch (Exception e) {
            log.error("Failed to record disbursement accounting - DisbursementId: {}", disbursementId, e);
        }
    }
    
    private void notifyBorrower(UUID borrowerId, UUID loanId, UUID disbursementId, String disbursementStatus,
                               BigDecimal disbursementAmount, String currency, String destinationAccountId,
                               LocalDate firstPaymentDate) {
        try {
            loanNotificationService.sendDisbursementNotification(borrowerId, loanId, disbursementId, 
                    disbursementStatus, disbursementAmount, currency, destinationAccountId, 
                    firstPaymentDate);
            
            log.info("Borrower notified of disbursement - BorrowerId: {}, DisbursementId: {}, Status: {}", 
                    borrowerId, disbursementId, disbursementStatus);
            
        } catch (Exception e) {
            log.error("Failed to notify borrower - BorrowerId: {}, DisbursementId: {}", 
                    borrowerId, disbursementId, e);
        }
    }
    
    private void updateDisbursementMetrics(String loanType, String disbursementStatus,
                                          BigDecimal disbursementAmount, String disbursementMethod) {
        try {
            loanDisbursementService.updateDisbursementMetrics(loanType, disbursementStatus, 
                    disbursementAmount, disbursementMethod);
        } catch (Exception e) {
            log.error("Failed to update disbursement metrics - Type: {}, Status: {}", 
                    loanType, disbursementStatus, e);
        }
    }
    
    private void auditLoanDisbursement(UUID disbursementId, UUID loanId, UUID borrowerId,
                                      String loanType, String disbursementStatus,
                                      BigDecimal disbursementAmount, String currency,
                                      LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditFinancialEvent(
                    "LOAN_DISBURSEMENT_PROCESSED",
                    borrowerId.toString(),
                    String.format("Loan disbursement %s - Type: %s, Amount: %s %s", 
                            disbursementStatus, loanType, disbursementAmount, currency),
                    Map.of(
                            "disbursementId", disbursementId.toString(),
                            "loanId", loanId.toString(),
                            "borrowerId", borrowerId.toString(),
                            "loanType", loanType,
                            "disbursementStatus", disbursementStatus,
                            "disbursementAmount", disbursementAmount.toString(),
                            "currency", currency,
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit loan disbursement - DisbursementId: {}", disbursementId, e);
        }
    }
    
    private void handleDisbursementFailure(UUID disbursementId, UUID loanId, String disbursementStatus,
                                          Exception error) {
        try {
            loanDisbursementService.handleDisbursementFailure(disbursementId, loanId, 
                    disbursementStatus, error.getMessage());
            
            auditService.auditFinancialEvent(
                    "LOAN_DISBURSEMENT_PROCESSING_FAILED",
                    "SYSTEM",
                    "Failed to process loan disbursement: " + error.getMessage(),
                    Map.of(
                            "disbursementId", disbursementId.toString(),
                            "loanId", loanId.toString(),
                            "disbursementStatus", disbursementStatus != null ? disbursementStatus : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle disbursement failure - DisbursementId: {}", disbursementId, e);
        }
    }
    
    @KafkaListener(
        topics = {"loan-disbursement-events.DLQ", "loan-disbursement.DLQ"},
        groupId = "lending-service-disbursement-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: Loan disbursement event sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID disbursementId = event.containsKey("disbursementId") ? 
                    UUID.fromString((String) event.get("disbursementId")) : null;
            UUID loanId = event.containsKey("loanId") ? 
                    UUID.fromString((String) event.get("loanId")) : null;
            UUID borrowerId = event.containsKey("borrowerId") ? 
                    UUID.fromString((String) event.get("borrowerId")) : null;
            String disbursementStatus = (String) event.get("disbursementStatus");
            
            log.error("DLQ: Loan disbursement failed permanently - DisbursementId: {}, LoanId: {}, BorrowerId: {}, Status: {} - MANUAL INTERVENTION REQUIRED", 
                    disbursementId, loanId, borrowerId, disbursementStatus);
            
            if (disbursementId != null && loanId != null) {
                loanDisbursementService.markForManualReview(disbursementId, loanId, borrowerId, 
                        disbursementStatus, "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse loan disbursement DLQ event: {}", eventJson, e);
        }
    }
}