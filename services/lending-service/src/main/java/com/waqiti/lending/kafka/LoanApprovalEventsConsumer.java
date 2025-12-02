package com.waqiti.lending.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.lending.service.LoanApprovalService;
import com.waqiti.lending.service.LoanUnderwritingService;
import com.waqiti.lending.service.LoanNotificationService;
import com.waqiti.lending.service.LoanContractService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoanApprovalEventsConsumer {
    
    private final LoanApprovalService loanApprovalService;
    private final LoanUnderwritingService loanUnderwritingService;
    private final LoanNotificationService loanNotificationService;
    private final LoanContractService loanContractService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"loan-approval-events", "loan-approved", "loan-rejected", "loan-conditional-approval"},
        groupId = "lending-service-loan-approval-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleLoanApprovalEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("LOAN APPROVAL: Processing loan approval event - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID approvalId = null;
        UUID loanId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            approvalId = UUID.fromString((String) event.get("approvalId"));
            loanId = UUID.fromString((String) event.get("loanId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            String approvalStatus = (String) event.get("approvalStatus");
            String loanType = (String) event.get("loanType");
            BigDecimal approvedAmount = new BigDecimal(event.get("approvedAmount").toString());
            BigDecimal requestedAmount = new BigDecimal(event.get("requestedAmount").toString());
            String currency = (String) event.get("currency");
            BigDecimal approvedInterestRate = new BigDecimal(event.get("approvedInterestRate").toString());
            Integer approvedTermMonths = (Integer) event.get("approvedTermMonths");
            UUID underwriterId = UUID.fromString((String) event.get("underwriterId"));
            LocalDateTime approvalDate = LocalDateTime.parse((String) event.get("approvalDate"));
            String approvalReason = (String) event.getOrDefault("approvalReason", "");
            String rejectionReason = (String) event.getOrDefault("rejectionReason", "");
            Integer creditScore = event.containsKey("creditScore") ? (Integer) event.get("creditScore") : null;
            String riskGrade = (String) event.getOrDefault("riskGrade", "UNKNOWN");
            @SuppressWarnings("unchecked")
            List<String> conditions = (List<String>) event.getOrDefault("conditions", List.of());
            @SuppressWarnings("unchecked")
            List<String> covenants = (List<String>) event.getOrDefault("covenants", List.of());
            Boolean requiresCollateral = (Boolean) event.getOrDefault("requiresCollateral", false);
            String approvalLevel = (String) event.get("approvalLevel");
            UUID managerId = event.containsKey("managerId") ? 
                    UUID.fromString((String) event.get("managerId")) : null;
            
            log.info("Loan approval event - ApprovalId: {}, LoanId: {}, CustomerId: {}, EventType: {}, Status: {}, Amount: {} {}, Rate: {}%", 
                    approvalId, loanId, customerId, eventType, approvalStatus, 
                    approvedAmount, currency, approvedInterestRate);
            
            validateLoanApprovalEvent(approvalId, loanId, customerId, eventType, approvalStatus, 
                    approvedAmount, approvedInterestRate, approvedTermMonths);
            
            processEventByType(approvalId, loanId, customerId, eventType, approvalStatus, loanType, 
                    approvedAmount, requestedAmount, currency, approvedInterestRate, approvedTermMonths, 
                    underwriterId, approvalDate, approvalReason, rejectionReason, creditScore, 
                    riskGrade, conditions, covenants, requiresCollateral, approvalLevel, managerId);
            
            if ("APPROVED".equals(approvalStatus)) {
                handleLoanApproval(approvalId, loanId, customerId, loanType, approvedAmount, 
                        currency, approvedInterestRate, approvedTermMonths, underwriterId, 
                        approvalDate, approvalReason, conditions, covenants, requiresCollateral);
            } else if ("REJECTED".equals(approvalStatus)) {
                handleLoanRejection(approvalId, loanId, customerId, loanType, requestedAmount, 
                        currency, rejectionReason, creditScore, riskGrade);
            } else if ("CONDITIONAL_APPROVAL".equals(approvalStatus)) {
                handleConditionalApproval(approvalId, loanId, customerId, loanType, approvedAmount, 
                        currency, approvedInterestRate, conditions, requiresCollateral);
            }
            
            processUnderwritingDecision(approvalId, loanId, underwriterId, approvalStatus, 
                    approvedAmount, approvedInterestRate, riskGrade, creditScore);
            
            generateLoanContract(loanId, customerId, approvalStatus, approvedAmount, currency, 
                    approvedInterestRate, approvedTermMonths, conditions, covenants);
            
            notifyBorrower(customerId, loanId, eventType, approvalStatus, loanType, 
                    approvedAmount, currency, approvedInterestRate, rejectionReason);
            
            notifyUnderwriter(underwriterId, approvalId, loanId, customerId, approvalStatus, 
                    approvedAmount, currency, approvalLevel);
            
            if (managerId != null) {
                notifyManager(managerId, approvalId, loanId, customerId, approvalStatus, 
                        approvedAmount, currency, riskGrade);
            }
            
            updateApprovalMetrics(eventType, approvalStatus, loanType, approvedAmount, 
                    approvedInterestRate, riskGrade, approvalLevel);
            
            auditLoanApprovalEvent(approvalId, loanId, customerId, eventType, approvalStatus, 
                    loanType, approvedAmount, currency, approvedInterestRate, underwriterId, 
                    processingStartTime);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Loan approval event processed - ApprovalId: {}, EventType: {}, Status: {}, ProcessingTime: {}ms", 
                    approvalId, eventType, approvalStatus, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Loan approval event processing failed - ApprovalId: {}, LoanId: {}, CustomerId: {}, EventType: {}, Error: {}", 
                    approvalId, loanId, customerId, eventType, e.getMessage(), e);
            
            if (approvalId != null && loanId != null && customerId != null) {
                handleEventFailure(approvalId, loanId, customerId, eventType, e);
            }
            
            throw new RuntimeException("Loan approval event processing failed", e);
        }
    }
    
    private void validateLoanApprovalEvent(UUID approvalId, UUID loanId, UUID customerId, 
                                         String eventType, String approvalStatus, 
                                         BigDecimal approvedAmount, BigDecimal approvedInterestRate, 
                                         Integer approvedTermMonths) {
        if (approvalId == null || loanId == null || customerId == null) {
            throw new IllegalArgumentException("Approval ID, Loan ID, and Customer ID are required");
        }
        
        if (eventType == null || eventType.trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }
        
        List<String> validEventTypes = List.of("APPROVED", "REJECTED", "CONDITIONAL_APPROVAL", 
                "APPROVAL_REVISED", "APPROVAL_EXPIRED", "APPROVAL_WITHDRAWN");
        if (!validEventTypes.contains(eventType)) {
            throw new IllegalArgumentException("Invalid event type: " + eventType);
        }
        
        if (approvalStatus == null || approvalStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("Approval status is required");
        }
        
        if ("APPROVED".equals(approvalStatus) || "CONDITIONAL_APPROVAL".equals(approvalStatus)) {
            if (approvedAmount == null || approvedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Invalid approved amount");
            }
            
            if (approvedInterestRate == null || approvedInterestRate.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Invalid interest rate");
            }
            
            if (approvedTermMonths == null || approvedTermMonths <= 0) {
                throw new IllegalArgumentException("Invalid loan term");
            }
        }
        
        log.debug("Loan approval event validation passed - ApprovalId: {}", approvalId);
    }
    
    private void processEventByType(UUID approvalId, UUID loanId, UUID customerId, String eventType,
                                   String approvalStatus, String loanType, BigDecimal approvedAmount,
                                   BigDecimal requestedAmount, String currency, BigDecimal approvedInterestRate,
                                   Integer approvedTermMonths, UUID underwriterId, LocalDateTime approvalDate,
                                   String approvalReason, String rejectionReason, Integer creditScore,
                                   String riskGrade, List<String> conditions, List<String> covenants,
                                   Boolean requiresCollateral, String approvalLevel, UUID managerId) {
        try {
            switch (eventType) {
                case "APPROVED" -> processLoanApproval(approvalId, loanId, customerId, loanType, 
                        approvedAmount, currency, approvedInterestRate, approvedTermMonths, 
                        underwriterId, approvalDate, approvalReason, conditions, covenants, 
                        requiresCollateral);
                
                case "REJECTED" -> processLoanRejection(approvalId, loanId, customerId, loanType, 
                        requestedAmount, currency, rejectionReason, creditScore, riskGrade, 
                        underwriterId);
                
                case "CONDITIONAL_APPROVAL" -> processConditionalApproval(approvalId, loanId, 
                        customerId, loanType, approvedAmount, currency, approvedInterestRate, 
                        approvedTermMonths, conditions, requiresCollateral, underwriterId);
                
                case "APPROVAL_REVISED" -> processApprovalRevision(approvalId, loanId, customerId, 
                        approvedAmount, currency, approvedInterestRate, approvedTermMonths, 
                        conditions, underwriterId);
                
                case "APPROVAL_EXPIRED" -> processApprovalExpiration(approvalId, loanId, customerId, 
                        loanType, approvedAmount);
                
                case "APPROVAL_WITHDRAWN" -> processApprovalWithdrawal(approvalId, loanId, 
                        customerId, approvalReason);
                
                default -> {
                    log.warn("Unknown loan approval event type: {}", eventType);
                    processGenericEvent(approvalId, loanId, customerId, eventType);
                }
            }
            
            log.debug("Event type processing completed - ApprovalId: {}, EventType: {}", 
                    approvalId, eventType);
            
        } catch (Exception e) {
            log.error("Failed to process event by type - ApprovalId: {}, EventType: {}", 
                    approvalId, eventType, e);
            throw new RuntimeException("Event type processing failed", e);
        }
    }
    
    private void processLoanApproval(UUID approvalId, UUID loanId, UUID customerId, String loanType,
                                   BigDecimal approvedAmount, String currency, BigDecimal approvedInterestRate,
                                   Integer approvedTermMonths, UUID underwriterId, LocalDateTime approvalDate,
                                   String approvalReason, List<String> conditions, List<String> covenants,
                                   Boolean requiresCollateral) {
        log.info("Processing LOAN APPROVAL - ApprovalId: {}, LoanId: {}, Amount: {} {}, Rate: {}%, Term: {} months", 
                approvalId, loanId, approvedAmount, currency, approvedInterestRate, approvedTermMonths);
        
        loanApprovalService.processApproval(approvalId, loanId, customerId, loanType, approvedAmount, 
                currency, approvedInterestRate, approvedTermMonths, underwriterId, approvalDate, 
                approvalReason, conditions, covenants, requiresCollateral);
    }
    
    private void processLoanRejection(UUID approvalId, UUID loanId, UUID customerId, String loanType,
                                    BigDecimal requestedAmount, String currency, String rejectionReason,
                                    Integer creditScore, String riskGrade, UUID underwriterId) {
        log.warn("Processing LOAN REJECTION - ApprovalId: {}, LoanId: {}, Reason: {}, Score: {}, Risk: {}", 
                approvalId, loanId, rejectionReason, creditScore, riskGrade);
        
        loanApprovalService.processRejection(approvalId, loanId, customerId, loanType, requestedAmount, 
                currency, rejectionReason, creditScore, riskGrade, underwriterId);
    }
    
    private void processConditionalApproval(UUID approvalId, UUID loanId, UUID customerId, String loanType,
                                          BigDecimal approvedAmount, String currency, BigDecimal approvedInterestRate,
                                          Integer approvedTermMonths, List<String> conditions,
                                          Boolean requiresCollateral, UUID underwriterId) {
        log.info("Processing CONDITIONAL APPROVAL - ApprovalId: {}, LoanId: {}, Amount: {} {}, Conditions: {}", 
                approvalId, loanId, approvedAmount, currency, conditions.size());
        
        loanApprovalService.processConditionalApproval(approvalId, loanId, customerId, loanType, 
                approvedAmount, currency, approvedInterestRate, approvedTermMonths, conditions, 
                requiresCollateral, underwriterId);
    }
    
    private void processApprovalRevision(UUID approvalId, UUID loanId, UUID customerId,
                                       BigDecimal approvedAmount, String currency, BigDecimal approvedInterestRate,
                                       Integer approvedTermMonths, List<String> conditions,
                                       UUID underwriterId) {
        log.info("Processing APPROVAL REVISION - ApprovalId: {}, LoanId: {}, Amount: {} {}, Rate: {}%", 
                approvalId, loanId, approvedAmount, currency, approvedInterestRate);
        
        loanApprovalService.processRevision(approvalId, loanId, customerId, approvedAmount, currency, 
                approvedInterestRate, approvedTermMonths, conditions, underwriterId);
    }
    
    private void processApprovalExpiration(UUID approvalId, UUID loanId, UUID customerId,
                                         String loanType, BigDecimal approvedAmount) {
        log.warn("Processing APPROVAL EXPIRATION - ApprovalId: {}, LoanId: {}, Amount: {}", 
                approvalId, loanId, approvedAmount);
        
        loanApprovalService.processExpiration(approvalId, loanId, customerId, loanType, approvedAmount);
    }
    
    private void processApprovalWithdrawal(UUID approvalId, UUID loanId, UUID customerId,
                                         String approvalReason) {
        log.info("Processing APPROVAL WITHDRAWAL - ApprovalId: {}, LoanId: {}, Reason: {}", 
                approvalId, loanId, approvalReason);
        
        loanApprovalService.processWithdrawal(approvalId, loanId, customerId, approvalReason);
    }
    
    private void processGenericEvent(UUID approvalId, UUID loanId, UUID customerId, String eventType) {
        log.info("Processing generic loan approval event - ApprovalId: {}, EventType: {}", 
                approvalId, eventType);
        
        loanApprovalService.processGenericEvent(approvalId, loanId, customerId, eventType);
    }
    
    private void handleLoanApproval(UUID approvalId, UUID loanId, UUID customerId, String loanType,
                                   BigDecimal approvedAmount, String currency, BigDecimal approvedInterestRate,
                                   Integer approvedTermMonths, UUID underwriterId, LocalDateTime approvalDate,
                                   String approvalReason, List<String> conditions, List<String> covenants,
                                   Boolean requiresCollateral) {
        try {
            loanApprovalService.recordApproval(approvalId, loanId, customerId, loanType, approvedAmount, 
                    currency, approvedInterestRate, approvedTermMonths, underwriterId, approvalDate, 
                    approvalReason, conditions, covenants, requiresCollateral);
            
        } catch (Exception e) {
            log.error("Failed to handle loan approval - ApprovalId: {}", approvalId, e);
        }
    }
    
    private void handleLoanRejection(UUID approvalId, UUID loanId, UUID customerId, String loanType,
                                    BigDecimal requestedAmount, String currency, String rejectionReason,
                                    Integer creditScore, String riskGrade) {
        try {
            loanApprovalService.recordRejection(approvalId, loanId, customerId, loanType, requestedAmount, 
                    currency, rejectionReason, creditScore, riskGrade);
            
        } catch (Exception e) {
            log.error("Failed to handle loan rejection - ApprovalId: {}", approvalId, e);
        }
    }
    
    private void handleConditionalApproval(UUID approvalId, UUID loanId, UUID customerId, String loanType,
                                         BigDecimal approvedAmount, String currency, BigDecimal approvedInterestRate,
                                         List<String> conditions, Boolean requiresCollateral) {
        try {
            log.info("Processing conditional approval - ApprovalId: {}, Conditions: {}, Collateral: {}", 
                    approvalId, conditions.size(), requiresCollateral);
            
            loanApprovalService.recordConditionalApproval(approvalId, loanId, customerId, loanType, 
                    approvedAmount, currency, approvedInterestRate, conditions, requiresCollateral);
            
        } catch (Exception e) {
            log.error("Failed to handle conditional approval - ApprovalId: {}", approvalId, e);
        }
    }
    
    private void processUnderwritingDecision(UUID approvalId, UUID loanId, UUID underwriterId,
                                           String approvalStatus, BigDecimal approvedAmount,
                                           BigDecimal approvedInterestRate, String riskGrade,
                                           Integer creditScore) {
        try {
            loanUnderwritingService.recordDecision(approvalId, loanId, underwriterId, approvalStatus, 
                    approvedAmount, approvedInterestRate, riskGrade, creditScore);
            
            log.debug("Underwriting decision recorded - ApprovalId: {}, Decision: {}", 
                    approvalId, approvalStatus);
            
        } catch (Exception e) {
            log.error("Failed to process underwriting decision - ApprovalId: {}", approvalId, e);
        }
    }
    
    private void generateLoanContract(UUID loanId, UUID customerId, String approvalStatus,
                                    BigDecimal approvedAmount, String currency, BigDecimal approvedInterestRate,
                                    Integer approvedTermMonths, List<String> conditions,
                                    List<String> covenants) {
        try {
            if ("APPROVED".equals(approvalStatus) || "CONDITIONAL_APPROVAL".equals(approvalStatus)) {
                loanContractService.generateContract(loanId, customerId, approvedAmount, currency, 
                        approvedInterestRate, approvedTermMonths, conditions, covenants);
                
                log.info("Loan contract generated - LoanId: {}, Amount: {} {}", 
                        loanId, approvedAmount, currency);
            }
            
        } catch (Exception e) {
            log.error("Failed to generate loan contract - LoanId: {}", loanId, e);
        }
    }
    
    private void notifyBorrower(UUID customerId, UUID loanId, String eventType, String approvalStatus,
                               String loanType, BigDecimal approvedAmount, String currency,
                               BigDecimal approvedInterestRate, String rejectionReason) {
        try {
            loanNotificationService.sendApprovalNotification(customerId, loanId, eventType, approvalStatus, 
                    loanType, approvedAmount, currency, approvedInterestRate, rejectionReason);
            
            log.info("Borrower notified - CustomerId: {}, LoanId: {}, Status: {}", 
                    customerId, loanId, approvalStatus);
            
        } catch (Exception e) {
            log.error("Failed to notify borrower - CustomerId: {}, LoanId: {}", customerId, loanId, e);
        }
    }
    
    private void notifyUnderwriter(UUID underwriterId, UUID approvalId, UUID loanId, UUID customerId,
                                  String approvalStatus, BigDecimal approvedAmount, String currency,
                                  String approvalLevel) {
        try {
            loanNotificationService.sendUnderwriterApprovalNotification(underwriterId, approvalId, loanId, 
                    customerId, approvalStatus, approvedAmount, currency, approvalLevel);
            
            log.info("Underwriter notified - UnderwriterId: {}, ApprovalId: {}", underwriterId, approvalId);
            
        } catch (Exception e) {
            log.error("Failed to notify underwriter - UnderwriterId: {}", underwriterId, e);
        }
    }
    
    private void notifyManager(UUID managerId, UUID approvalId, UUID loanId, UUID customerId,
                              String approvalStatus, BigDecimal approvedAmount, String currency,
                              String riskGrade) {
        try {
            loanNotificationService.sendManagerNotification(managerId, approvalId, loanId, customerId, 
                    approvalStatus, approvedAmount, currency, riskGrade);
            
            log.info("Manager notified - ManagerId: {}, ApprovalId: {}", managerId, approvalId);
            
        } catch (Exception e) {
            log.error("Failed to notify manager - ManagerId: {}", managerId, e);
        }
    }
    
    private void updateApprovalMetrics(String eventType, String approvalStatus, String loanType,
                                     BigDecimal approvedAmount, BigDecimal approvedInterestRate,
                                     String riskGrade, String approvalLevel) {
        try {
            loanApprovalService.updateApprovalMetrics(eventType, approvalStatus, loanType, 
                    approvedAmount, approvedInterestRate, riskGrade, approvalLevel);
        } catch (Exception e) {
            log.error("Failed to update approval metrics - EventType: {}, Status: {}", 
                    eventType, approvalStatus, e);
        }
    }
    
    private void auditLoanApprovalEvent(UUID approvalId, UUID loanId, UUID customerId, String eventType,
                                      String approvalStatus, String loanType, BigDecimal approvedAmount,
                                      String currency, BigDecimal approvedInterestRate, UUID underwriterId,
                                      LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditFinancialEvent(
                    "LOAN_APPROVAL_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Loan approval event %s - Status: %s, LoanType: %s, Amount: %s %s, Rate: %s%%", 
                            eventType, approvalStatus, loanType, approvedAmount, currency, approvedInterestRate),
                    Map.of(
                            "approvalId", approvalId.toString(),
                            "loanId", loanId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "approvalStatus", approvalStatus,
                            "loanType", loanType,
                            "approvedAmount", approvedAmount.toString(),
                            "currency", currency,
                            "approvedInterestRate", approvedInterestRate.toString(),
                            "underwriterId", underwriterId.toString(),
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit loan approval event - ApprovalId: {}", approvalId, e);
        }
    }
    
    private void handleEventFailure(UUID approvalId, UUID loanId, UUID customerId, String eventType,
                                   Exception error) {
        try {
            loanApprovalService.handleEventFailure(approvalId, loanId, customerId, eventType, 
                    error.getMessage());
            
            auditService.auditFinancialEvent(
                    "LOAN_APPROVAL_EVENT_PROCESSING_FAILED",
                    customerId.toString(),
                    "Failed to process loan approval event: " + error.getMessage(),
                    Map.of(
                            "approvalId", approvalId.toString(),
                            "loanId", loanId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType != null ? eventType : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle event failure - ApprovalId: {}", approvalId, e);
        }
    }
    
    @KafkaListener(
        topics = {"loan-approval-events.DLQ", "loan-approved.DLQ", "loan-rejected.DLQ", "loan-conditional-approval.DLQ"},
        groupId = "lending-service-loan-approval-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: Loan approval event sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID approvalId = event.containsKey("approvalId") ? 
                    UUID.fromString((String) event.get("approvalId")) : null;
            UUID loanId = event.containsKey("loanId") ? 
                    UUID.fromString((String) event.get("loanId")) : null;
            UUID customerId = event.containsKey("customerId") ? 
                    UUID.fromString((String) event.get("customerId")) : null;
            String eventType = (String) event.get("eventType");
            
            log.error("DLQ: Loan approval event failed permanently - ApprovalId: {}, LoanId: {}, CustomerId: {}, EventType: {} - MANUAL REVIEW REQUIRED", 
                    approvalId, loanId, customerId, eventType);
            
            if (approvalId != null && customerId != null) {
                loanApprovalService.markForManualReview(approvalId, loanId, customerId, eventType, 
                        "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse loan approval DLQ event: {}", eventJson, e);
        }
    }
}