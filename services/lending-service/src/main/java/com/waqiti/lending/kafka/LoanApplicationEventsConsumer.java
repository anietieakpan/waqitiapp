package com.waqiti.lending.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.lending.service.LoanApplicationService;
import com.waqiti.lending.service.CreditCheckService;
import com.waqiti.lending.service.LoanNotificationService;
import com.waqiti.lending.service.LoanDocumentService;
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
public class LoanApplicationEventsConsumer {
    
    private final LoanApplicationService loanApplicationService;
    private final CreditCheckService creditCheckService;
    private final LoanNotificationService loanNotificationService;
    private final LoanDocumentService loanDocumentService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"loan-application-events", "loan-application-submitted", "loan-application-updated"},
        groupId = "lending-service-loan-application-group",
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
    public void handleLoanApplicationEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("LOAN APPLICATION: Processing loan application event - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID applicationId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            applicationId = UUID.fromString((String) event.get("applicationId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            String applicationStatus = (String) event.get("applicationStatus");
            String loanType = (String) event.get("loanType");
            BigDecimal requestedAmount = new BigDecimal(event.get("requestedAmount").toString());
            String currency = (String) event.get("currency");
            Integer termMonths = (Integer) event.get("termMonths");
            BigDecimal proposedInterestRate = event.containsKey("proposedInterestRate") ? 
                    new BigDecimal(event.get("proposedInterestRate").toString()) : null;
            String applicantEmail = (String) event.get("applicantEmail");
            String applicantPhone = (String) event.get("applicantPhone");
            String purpose = (String) event.get("purpose");
            BigDecimal monthlyIncome = new BigDecimal(event.get("monthlyIncome").toString());
            Integer creditScore = event.containsKey("creditScore") ? (Integer) event.get("creditScore") : null;
            String employmentStatus = (String) event.get("employmentStatus");
            LocalDateTime submissionDate = LocalDateTime.parse((String) event.get("submissionDate"));
            @SuppressWarnings("unchecked")
            List<String> requiredDocuments = (List<String>) event.getOrDefault("requiredDocuments", List.of());
            @SuppressWarnings("unchecked")
            List<String> submittedDocuments = (List<String>) event.getOrDefault("submittedDocuments", List.of());
            String riskCategory = (String) event.getOrDefault("riskCategory", "UNKNOWN");
            UUID underwriterId = event.containsKey("underwriterId") ? 
                    UUID.fromString((String) event.get("underwriterId")) : null;
            
            log.info("Loan application event - AppId: {}, CustomerId: {}, EventType: {}, Status: {}, LoanType: {}, Amount: {} {}, Term: {} months", 
                    applicationId, customerId, eventType, applicationStatus, loanType, 
                    requestedAmount, currency, termMonths);
            
            validateLoanApplicationEvent(applicationId, customerId, eventType, applicationStatus, 
                    loanType, requestedAmount, termMonths);
            
            processEventByType(applicationId, customerId, eventType, applicationStatus, loanType, 
                    requestedAmount, currency, termMonths, proposedInterestRate, applicantEmail, 
                    applicantPhone, purpose, monthlyIncome, creditScore, employmentStatus, 
                    submissionDate, requiredDocuments, submittedDocuments, riskCategory, 
                    underwriterId);
            
            if ("SUBMITTED".equals(eventType)) {
                handleApplicationSubmission(applicationId, customerId, loanType, requestedAmount, 
                        currency, termMonths, applicantEmail, applicantPhone, purpose, monthlyIncome, 
                        employmentStatus, submissionDate, requiredDocuments);
            } else if ("UPDATED".equals(eventType)) {
                handleApplicationUpdate(applicationId, customerId, applicationStatus, 
                        submittedDocuments, creditScore, riskCategory);
            } else if ("WITHDRAWN".equals(eventType)) {
                handleApplicationWithdrawal(applicationId, customerId, applicationStatus);
            }
            
            performCreditCheck(applicationId, customerId, loanType, requestedAmount, 
                    monthlyIncome, employmentStatus, creditScore);
            
            validateDocuments(applicationId, requiredDocuments, submittedDocuments);
            
            assessRisk(applicationId, customerId, loanType, requestedAmount, monthlyIncome, 
                    creditScore, employmentStatus, riskCategory);
            
            notifyApplicant(customerId, applicationId, eventType, applicationStatus, 
                    loanType, requestedAmount, currency, applicantEmail);
            
            if (underwriterId != null) {
                notifyUnderwriter(underwriterId, applicationId, customerId, loanType, 
                        requestedAmount, currency, riskCategory);
            }
            
            updateApplicationMetrics(eventType, applicationStatus, loanType, requestedAmount, 
                    creditScore, riskCategory);
            
            auditLoanApplicationEvent(applicationId, customerId, eventType, applicationStatus, 
                    loanType, requestedAmount, currency, riskCategory, processingStartTime);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Loan application event processed - AppId: {}, EventType: {}, Status: {}, ProcessingTime: {}ms", 
                    applicationId, eventType, applicationStatus, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Loan application event processing failed - AppId: {}, CustomerId: {}, EventType: {}, Error: {}", 
                    applicationId, customerId, eventType, e.getMessage(), e);
            
            if (applicationId != null && customerId != null) {
                handleEventFailure(applicationId, customerId, eventType, e);
            }
            
            throw new RuntimeException("Loan application event processing failed", e);
        }
    }
    
    private void validateLoanApplicationEvent(UUID applicationId, UUID customerId, String eventType,
                                            String applicationStatus, String loanType, 
                                            BigDecimal requestedAmount, Integer termMonths) {
        if (applicationId == null || customerId == null) {
            throw new IllegalArgumentException("Application ID and Customer ID are required");
        }
        
        if (eventType == null || eventType.trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }
        
        List<String> validEventTypes = List.of("SUBMITTED", "UPDATED", "REVIEWED", "APPROVED", 
                "REJECTED", "WITHDRAWN", "EXPIRED");
        if (!validEventTypes.contains(eventType)) {
            throw new IllegalArgumentException("Invalid event type: " + eventType);
        }
        
        if (applicationStatus == null || applicationStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("Application status is required");
        }
        
        if (loanType == null || loanType.trim().isEmpty()) {
            throw new IllegalArgumentException("Loan type is required");
        }
        
        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid requested amount");
        }
        
        if (termMonths == null || termMonths <= 0) {
            throw new IllegalArgumentException("Invalid loan term");
        }
        
        log.debug("Loan application event validation passed - AppId: {}", applicationId);
    }
    
    private void processEventByType(UUID applicationId, UUID customerId, String eventType,
                                   String applicationStatus, String loanType, BigDecimal requestedAmount,
                                   String currency, Integer termMonths, BigDecimal proposedInterestRate,
                                   String applicantEmail, String applicantPhone, String purpose,
                                   BigDecimal monthlyIncome, Integer creditScore, String employmentStatus,
                                   LocalDateTime submissionDate, List<String> requiredDocuments,
                                   List<String> submittedDocuments, String riskCategory,
                                   UUID underwriterId) {
        try {
            switch (eventType) {
                case "SUBMITTED" -> processApplicationSubmission(applicationId, customerId, loanType, 
                        requestedAmount, currency, termMonths, purpose, monthlyIncome, employmentStatus, 
                        submissionDate, applicantEmail, applicantPhone);
                
                case "UPDATED" -> processApplicationUpdate(applicationId, customerId, applicationStatus, 
                        submittedDocuments, creditScore, riskCategory);
                
                case "REVIEWED" -> processApplicationReview(applicationId, customerId, loanType, 
                        requestedAmount, creditScore, riskCategory, underwriterId);
                
                case "APPROVED" -> processApplicationApproval(applicationId, customerId, loanType, 
                        requestedAmount, currency, termMonths, proposedInterestRate);
                
                case "REJECTED" -> processApplicationRejection(applicationId, customerId, loanType, 
                        requestedAmount, creditScore, riskCategory);
                
                case "WITHDRAWN" -> processApplicationWithdrawal(applicationId, customerId, 
                        applicationStatus);
                
                case "EXPIRED" -> processApplicationExpiration(applicationId, customerId, loanType);
                
                default -> {
                    log.warn("Unknown loan application event type: {}", eventType);
                    processGenericEvent(applicationId, customerId, eventType);
                }
            }
            
            log.debug("Event type processing completed - AppId: {}, EventType: {}", 
                    applicationId, eventType);
            
        } catch (Exception e) {
            log.error("Failed to process event by type - AppId: {}, EventType: {}", 
                    applicationId, eventType, e);
            throw new RuntimeException("Event type processing failed", e);
        }
    }
    
    private void processApplicationSubmission(UUID applicationId, UUID customerId, String loanType,
                                            BigDecimal requestedAmount, String currency, Integer termMonths,
                                            String purpose, BigDecimal monthlyIncome, String employmentStatus,
                                            LocalDateTime submissionDate, String applicantEmail,
                                            String applicantPhone) {
        log.info("Processing APPLICATION SUBMISSION - AppId: {}, LoanType: {}, Amount: {} {}, Term: {} months", 
                applicationId, loanType, requestedAmount, currency, termMonths);
        
        loanApplicationService.processSubmission(applicationId, customerId, loanType, requestedAmount, 
                currency, termMonths, purpose, monthlyIncome, employmentStatus, submissionDate, 
                applicantEmail, applicantPhone);
    }
    
    private void processApplicationUpdate(UUID applicationId, UUID customerId, String applicationStatus,
                                        List<String> submittedDocuments, Integer creditScore,
                                        String riskCategory) {
        log.info("Processing APPLICATION UPDATE - AppId: {}, Status: {}, Docs: {}, Score: {}", 
                applicationId, applicationStatus, submittedDocuments.size(), creditScore);
        
        loanApplicationService.processUpdate(applicationId, customerId, applicationStatus, 
                submittedDocuments, creditScore, riskCategory);
    }
    
    private void processApplicationReview(UUID applicationId, UUID customerId, String loanType,
                                        BigDecimal requestedAmount, Integer creditScore, 
                                        String riskCategory, UUID underwriterId) {
        log.info("Processing APPLICATION REVIEW - AppId: {}, LoanType: {}, Amount: {}, Score: {}, Risk: {}", 
                applicationId, loanType, requestedAmount, creditScore, riskCategory);
        
        loanApplicationService.processReview(applicationId, customerId, loanType, requestedAmount, 
                creditScore, riskCategory, underwriterId);
    }
    
    private void processApplicationApproval(UUID applicationId, UUID customerId, String loanType,
                                          BigDecimal requestedAmount, String currency, Integer termMonths,
                                          BigDecimal proposedInterestRate) {
        log.info("Processing APPLICATION APPROVAL - AppId: {}, LoanType: {}, Amount: {} {}, Rate: {}%", 
                applicationId, loanType, requestedAmount, currency, proposedInterestRate);
        
        loanApplicationService.processApproval(applicationId, customerId, loanType, requestedAmount, 
                currency, termMonths, proposedInterestRate);
    }
    
    private void processApplicationRejection(UUID applicationId, UUID customerId, String loanType,
                                           BigDecimal requestedAmount, Integer creditScore,
                                           String riskCategory) {
        log.warn("Processing APPLICATION REJECTION - AppId: {}, LoanType: {}, Amount: {}, Score: {}, Risk: {}", 
                applicationId, loanType, requestedAmount, creditScore, riskCategory);
        
        loanApplicationService.processRejection(applicationId, customerId, loanType, requestedAmount, 
                creditScore, riskCategory);
    }
    
    private void processApplicationWithdrawal(UUID applicationId, UUID customerId, String applicationStatus) {
        log.info("Processing APPLICATION WITHDRAWAL - AppId: {}, Status: {}", applicationId, applicationStatus);
        
        loanApplicationService.processWithdrawal(applicationId, customerId, applicationStatus);
    }
    
    private void processApplicationExpiration(UUID applicationId, UUID customerId, String loanType) {
        log.warn("Processing APPLICATION EXPIRATION - AppId: {}, LoanType: {}", applicationId, loanType);
        
        loanApplicationService.processExpiration(applicationId, customerId, loanType);
    }
    
    private void processGenericEvent(UUID applicationId, UUID customerId, String eventType) {
        log.info("Processing generic loan application event - AppId: {}, EventType: {}", 
                applicationId, eventType);
        
        loanApplicationService.processGenericEvent(applicationId, customerId, eventType);
    }
    
    private void handleApplicationSubmission(UUID applicationId, UUID customerId, String loanType,
                                           BigDecimal requestedAmount, String currency, Integer termMonths,
                                           String applicantEmail, String applicantPhone, String purpose,
                                           BigDecimal monthlyIncome, String employmentStatus,
                                           LocalDateTime submissionDate, List<String> requiredDocuments) {
        try {
            loanApplicationService.recordSubmission(applicationId, customerId, loanType, requestedAmount, 
                    currency, termMonths, applicantEmail, applicantPhone, purpose, monthlyIncome, 
                    employmentStatus, submissionDate, requiredDocuments);
            
        } catch (Exception e) {
            log.error("Failed to handle application submission - AppId: {}", applicationId, e);
        }
    }
    
    private void handleApplicationUpdate(UUID applicationId, UUID customerId, String applicationStatus,
                                       List<String> submittedDocuments, Integer creditScore,
                                       String riskCategory) {
        try {
            loanApplicationService.recordUpdate(applicationId, customerId, applicationStatus, 
                    submittedDocuments, creditScore, riskCategory);
            
        } catch (Exception e) {
            log.error("Failed to handle application update - AppId: {}", applicationId, e);
        }
    }
    
    private void handleApplicationWithdrawal(UUID applicationId, UUID customerId, String applicationStatus) {
        try {
            log.info("Processing application withdrawal - AppId: {}, Status: {}", 
                    applicationId, applicationStatus);
            
            loanApplicationService.recordWithdrawal(applicationId, customerId, applicationStatus);
            
        } catch (Exception e) {
            log.error("Failed to handle application withdrawal - AppId: {}", applicationId, e);
        }
    }
    
    private void performCreditCheck(UUID applicationId, UUID customerId, String loanType,
                                   BigDecimal requestedAmount, BigDecimal monthlyIncome,
                                   String employmentStatus, Integer creditScore) {
        try {
            creditCheckService.performCreditCheck(applicationId, customerId, loanType, requestedAmount, 
                    monthlyIncome, employmentStatus, creditScore);
            
            log.debug("Credit check completed - AppId: {}", applicationId);
            
        } catch (Exception e) {
            log.error("Failed to perform credit check - AppId: {}", applicationId, e);
        }
    }
    
    private void validateDocuments(UUID applicationId, List<String> requiredDocuments,
                                  List<String> submittedDocuments) {
        try {
            loanDocumentService.validateDocuments(applicationId, requiredDocuments, submittedDocuments);
            
            log.debug("Document validation completed - AppId: {}, Required: {}, Submitted: {}", 
                    applicationId, requiredDocuments.size(), submittedDocuments.size());
            
        } catch (Exception e) {
            log.error("Failed to validate documents - AppId: {}", applicationId, e);
        }
    }
    
    private void assessRisk(UUID applicationId, UUID customerId, String loanType,
                           BigDecimal requestedAmount, BigDecimal monthlyIncome, Integer creditScore,
                           String employmentStatus, String riskCategory) {
        try {
            loanApplicationService.assessRisk(applicationId, customerId, loanType, requestedAmount, 
                    monthlyIncome, creditScore, employmentStatus, riskCategory);
            
            log.debug("Risk assessment completed - AppId: {}, Category: {}", applicationId, riskCategory);
            
        } catch (Exception e) {
            log.error("Failed to assess risk - AppId: {}", applicationId, e);
        }
    }
    
    private void notifyApplicant(UUID customerId, UUID applicationId, String eventType,
                                String applicationStatus, String loanType, BigDecimal requestedAmount,
                                String currency, String applicantEmail) {
        try {
            loanNotificationService.sendApplicationNotification(customerId, applicationId, eventType, 
                    applicationStatus, loanType, requestedAmount, currency, applicantEmail);
            
            log.info("Applicant notified - CustomerId: {}, AppId: {}, EventType: {}", 
                    customerId, applicationId, eventType);
            
        } catch (Exception e) {
            log.error("Failed to notify applicant - CustomerId: {}, AppId: {}", 
                    customerId, applicationId, e);
        }
    }
    
    private void notifyUnderwriter(UUID underwriterId, UUID applicationId, UUID customerId,
                                  String loanType, BigDecimal requestedAmount, String currency,
                                  String riskCategory) {
        try {
            loanNotificationService.sendUnderwriterNotification(underwriterId, applicationId, customerId, 
                    loanType, requestedAmount, currency, riskCategory);
            
            log.info("Underwriter notified - UnderwriterId: {}, AppId: {}", underwriterId, applicationId);
            
        } catch (Exception e) {
            log.error("Failed to notify underwriter - UnderwriterId: {}", underwriterId, e);
        }
    }
    
    private void updateApplicationMetrics(String eventType, String applicationStatus, String loanType,
                                        BigDecimal requestedAmount, Integer creditScore, String riskCategory) {
        try {
            loanApplicationService.updateApplicationMetrics(eventType, applicationStatus, loanType, 
                    requestedAmount, creditScore, riskCategory);
        } catch (Exception e) {
            log.error("Failed to update application metrics - EventType: {}, Status: {}", 
                    eventType, applicationStatus, e);
        }
    }
    
    private void auditLoanApplicationEvent(UUID applicationId, UUID customerId, String eventType,
                                         String applicationStatus, String loanType, BigDecimal requestedAmount,
                                         String currency, String riskCategory, LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditFinancialEvent(
                    "LOAN_APPLICATION_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Loan application event %s - Type: %s, Status: %s, LoanType: %s, Amount: %s %s, Risk: %s", 
                            eventType, eventType, applicationStatus, loanType, requestedAmount, currency, riskCategory),
                    Map.of(
                            "applicationId", applicationId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "applicationStatus", applicationStatus,
                            "loanType", loanType,
                            "requestedAmount", requestedAmount.toString(),
                            "currency", currency,
                            "riskCategory", riskCategory,
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit loan application event - AppId: {}", applicationId, e);
        }
    }
    
    private void handleEventFailure(UUID applicationId, UUID customerId, String eventType, Exception error) {
        try {
            loanApplicationService.handleEventFailure(applicationId, customerId, eventType, 
                    error.getMessage());
            
            auditService.auditFinancialEvent(
                    "LOAN_APPLICATION_EVENT_PROCESSING_FAILED",
                    customerId.toString(),
                    "Failed to process loan application event: " + error.getMessage(),
                    Map.of(
                            "applicationId", applicationId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType != null ? eventType : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle event failure - AppId: {}", applicationId, e);
        }
    }
    
    @KafkaListener(
        topics = {"loan-application-events.DLQ", "loan-application-submitted.DLQ", "loan-application-updated.DLQ"},
        groupId = "lending-service-loan-application-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: Loan application event sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID applicationId = event.containsKey("applicationId") ? 
                    UUID.fromString((String) event.get("applicationId")) : null;
            UUID customerId = event.containsKey("customerId") ? 
                    UUID.fromString((String) event.get("customerId")) : null;
            String eventType = (String) event.get("eventType");
            
            log.error("DLQ: Loan application event failed permanently - AppId: {}, CustomerId: {}, EventType: {} - MANUAL REVIEW REQUIRED", 
                    applicationId, customerId, eventType);
            
            if (applicationId != null && customerId != null) {
                loanApplicationService.markForManualReview(applicationId, customerId, eventType, 
                        "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse loan application DLQ event: {}", eventJson, e);
        }
    }
}