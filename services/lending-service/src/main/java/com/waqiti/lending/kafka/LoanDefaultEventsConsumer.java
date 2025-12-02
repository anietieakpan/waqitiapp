package com.waqiti.lending.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.lending.service.LoanDefaultService;
import com.waqiti.lending.service.LoanCollectionService;
import com.waqiti.lending.service.LoanNotificationService;
import com.waqiti.lending.service.LoanRiskService;
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
public class LoanDefaultEventsConsumer {
    
    private final LoanDefaultService loanDefaultService;
    private final LoanCollectionService loanCollectionService;
    private final LoanNotificationService loanNotificationService;
    private final LoanRiskService loanRiskService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"loan-default-events", "loan-delinquent", "loan-charge-off", "loan-recovery"},
        groupId = "lending-service-loan-default-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleLoanDefaultEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("LOAN DEFAULT: Processing loan default event - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID defaultId = null;
        UUID loanId = null;
        UUID borrowerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            defaultId = UUID.fromString((String) event.get("defaultId"));
            loanId = UUID.fromString((String) event.get("loanId"));
            borrowerId = UUID.fromString((String) event.get("borrowerId"));
            eventType = (String) event.get("eventType");
            String defaultStatus = (String) event.get("defaultStatus");
            String loanType = (String) event.get("loanType");
            BigDecimal originalLoanAmount = new BigDecimal(event.get("originalLoanAmount").toString());
            BigDecimal outstandingBalance = new BigDecimal(event.get("outstandingBalance").toString());
            BigDecimal defaultAmount = new BigDecimal(event.get("defaultAmount").toString());
            String currency = (String) event.get("currency");
            LocalDate defaultDate = LocalDate.parse((String) event.get("defaultDate"));
            LocalDate lastPaymentDate = event.containsKey("lastPaymentDate") ? 
                    LocalDate.parse((String) event.get("lastPaymentDate")) : null;
            Integer daysPastDue = (Integer) event.get("daysPastDue");
            String defaultReason = (String) event.get("defaultReason");
            String riskCategory = (String) event.getOrDefault("riskCategory", "HIGH");
            Integer creditScore = event.containsKey("creditScore") ? (Integer) event.get("creditScore") : null;
            BigDecimal collateralValue = event.containsKey("collateralValue") ? 
                    new BigDecimal(event.get("collateralValue").toString()) : BigDecimal.ZERO;
            String collateralType = (String) event.getOrDefault("collateralType", "NONE");
            @SuppressWarnings("unchecked")
            List<String> collectionActions = (List<String>) event.getOrDefault("collectionActions", List.of());
            UUID collectorId = event.containsKey("collectorId") ? 
                    UUID.fromString((String) event.get("collectorId")) : null;
            String legalStatus = (String) event.getOrDefault("legalStatus", "NONE");
            BigDecimal recoveryAmount = event.containsKey("recoveryAmount") ? 
                    new BigDecimal(event.get("recoveryAmount").toString()) : BigDecimal.ZERO;
            String recoveryMethod = (String) event.getOrDefault("recoveryMethod", "");
            
            log.warn("Loan default event - DefaultId: {}, LoanId: {}, BorrowerId: {}, EventType: {}, Status: {}, Outstanding: {} {}, DaysPastDue: {}", 
                    defaultId, loanId, borrowerId, eventType, defaultStatus, outstandingBalance, currency, daysPastDue);
            
            validateLoanDefaultEvent(defaultId, loanId, borrowerId, eventType, defaultStatus, 
                    outstandingBalance, defaultAmount, daysPastDue);
            
            processEventByType(defaultId, loanId, borrowerId, eventType, defaultStatus, loanType, 
                    originalLoanAmount, outstandingBalance, defaultAmount, currency, defaultDate, 
                    lastPaymentDate, daysPastDue, defaultReason, riskCategory, creditScore, 
                    collateralValue, collateralType, collectionActions, collectorId, legalStatus, 
                    recoveryAmount, recoveryMethod);
            
            if ("DELINQUENT".equals(defaultStatus)) {
                handleLoanDelinquency(defaultId, loanId, borrowerId, loanType, outstandingBalance, 
                        currency, defaultDate, daysPastDue, defaultReason, creditScore);
            } else if ("DEFAULT".equals(defaultStatus)) {
                handleLoanDefault(defaultId, loanId, borrowerId, loanType, outstandingBalance, 
                        defaultAmount, currency, defaultDate, daysPastDue, collateralValue, 
                        collateralType);
            } else if ("CHARGE_OFF".equals(defaultStatus)) {
                handleLoanChargeOff(defaultId, loanId, borrowerId, outstandingBalance, currency, 
                        defaultDate, collateralValue);
            } else if ("RECOVERY".equals(defaultStatus)) {
                handleLoanRecovery(defaultId, loanId, borrowerId, recoveryAmount, currency, 
                        recoveryMethod, defaultDate);
            }
            
            assessDefaultRisk(loanId, borrowerId, defaultStatus, outstandingBalance, daysPastDue, 
                    creditScore, collateralValue, riskCategory);
            
            initiateCollectionActions(defaultId, loanId, borrowerId, defaultStatus, outstandingBalance, 
                    currency, daysPastDue, collectionActions, collectorId);
            
            updateCreditBureauReporting(borrowerId, loanId, defaultStatus, outstandingBalance, 
                    daysPastDue, defaultDate);
            
            notifyBorrower(borrowerId, loanId, defaultId, eventType, defaultStatus, outstandingBalance, 
                    currency, daysPastDue, defaultReason);
            
            if (collectorId != null) {
                notifyCollector(collectorId, defaultId, loanId, borrowerId, defaultStatus, 
                        outstandingBalance, currency, daysPastDue);
            }
            
            updateDefaultMetrics(eventType, defaultStatus, loanType, defaultAmount, daysPastDue, 
                    riskCategory, collateralValue);
            
            auditLoanDefaultEvent(defaultId, loanId, borrowerId, eventType, defaultStatus, 
                    outstandingBalance, defaultAmount, currency, daysPastDue, processingStartTime);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.warn("Loan default event processed - DefaultId: {}, EventType: {}, Status: {}, ProcessingTime: {}ms", 
                    defaultId, eventType, defaultStatus, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Loan default event processing failed - DefaultId: {}, LoanId: {}, BorrowerId: {}, EventType: {}, Error: {}", 
                    defaultId, loanId, borrowerId, eventType, e.getMessage(), e);
            
            if (defaultId != null && loanId != null && borrowerId != null) {
                handleEventFailure(defaultId, loanId, borrowerId, eventType, e);
            }
            
            throw new RuntimeException("Loan default event processing failed", e);
        }
    }
    
    private void validateLoanDefaultEvent(UUID defaultId, UUID loanId, UUID borrowerId,
                                        String eventType, String defaultStatus,
                                        BigDecimal outstandingBalance, BigDecimal defaultAmount,
                                        Integer daysPastDue) {
        if (defaultId == null || loanId == null || borrowerId == null) {
            throw new IllegalArgumentException("Default ID, Loan ID, and Borrower ID are required");
        }
        
        if (eventType == null || eventType.trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }
        
        if (defaultStatus == null || defaultStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("Default status is required");
        }
        
        if (outstandingBalance == null || outstandingBalance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid outstanding balance");
        }
        
        if (defaultAmount == null || defaultAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid default amount");
        }
        
        if (daysPastDue == null || daysPastDue < 0) {
            throw new IllegalArgumentException("Invalid days past due");
        }
        
        log.debug("Loan default event validation passed - DefaultId: {}", defaultId);
    }
    
    private void processEventByType(UUID defaultId, UUID loanId, UUID borrowerId, String eventType,
                                   String defaultStatus, String loanType, BigDecimal originalLoanAmount,
                                   BigDecimal outstandingBalance, BigDecimal defaultAmount, String currency,
                                   LocalDate defaultDate, LocalDate lastPaymentDate, Integer daysPastDue,
                                   String defaultReason, String riskCategory, Integer creditScore,
                                   BigDecimal collateralValue, String collateralType, 
                                   List<String> collectionActions, UUID collectorId, String legalStatus,
                                   BigDecimal recoveryAmount, String recoveryMethod) {
        try {
            switch (eventType) {
                case "DELINQUENT" -> processLoanDelinquency(defaultId, loanId, borrowerId, loanType, 
                        outstandingBalance, currency, defaultDate, daysPastDue, defaultReason, 
                        creditScore, collateralValue);
                
                case "DEFAULT" -> processLoanDefault(defaultId, loanId, borrowerId, loanType, 
                        originalLoanAmount, outstandingBalance, defaultAmount, currency, defaultDate, 
                        lastPaymentDate, daysPastDue, defaultReason, riskCategory, creditScore, 
                        collateralValue, collateralType);
                
                case "CHARGE_OFF" -> processLoanChargeOff(defaultId, loanId, borrowerId, 
                        outstandingBalance, currency, defaultDate, collateralValue, legalStatus);
                
                case "RECOVERY" -> processLoanRecovery(defaultId, loanId, borrowerId, recoveryAmount, 
                        currency, recoveryMethod, defaultDate, outstandingBalance);
                
                case "COLLECTION_STARTED" -> processCollectionStart(defaultId, loanId, borrowerId, 
                        outstandingBalance, currency, collectorId, collectionActions);
                
                case "LEGAL_ACTION" -> processLegalAction(defaultId, loanId, borrowerId, 
                        outstandingBalance, currency, legalStatus, collateralValue);
                
                default -> {
                    log.warn("Unknown loan default event type: {}", eventType);
                    processGenericEvent(defaultId, loanId, borrowerId, eventType);
                }
            }
            
            log.debug("Event type processing completed - DefaultId: {}, EventType: {}", 
                    defaultId, eventType);
            
        } catch (Exception e) {
            log.error("Failed to process event by type - DefaultId: {}, EventType: {}", 
                    defaultId, eventType, e);
            throw new RuntimeException("Event type processing failed", e);
        }
    }
    
    private void processLoanDelinquency(UUID defaultId, UUID loanId, UUID borrowerId, String loanType,
                                      BigDecimal outstandingBalance, String currency, LocalDate defaultDate,
                                      Integer daysPastDue, String defaultReason, Integer creditScore,
                                      BigDecimal collateralValue) {
        log.warn("Processing LOAN DELINQUENCY - DefaultId: {}, LoanId: {}, Outstanding: {} {}, DaysPastDue: {}", 
                defaultId, loanId, outstandingBalance, currency, daysPastDue);
        
        loanDefaultService.processDelinquency(defaultId, loanId, borrowerId, loanType, outstandingBalance, 
                currency, defaultDate, daysPastDue, defaultReason, creditScore, collateralValue);
    }
    
    private void processLoanDefault(UUID defaultId, UUID loanId, UUID borrowerId, String loanType,
                                  BigDecimal originalLoanAmount, BigDecimal outstandingBalance,
                                  BigDecimal defaultAmount, String currency, LocalDate defaultDate,
                                  LocalDate lastPaymentDate, Integer daysPastDue, String defaultReason,
                                  String riskCategory, Integer creditScore, BigDecimal collateralValue,
                                  String collateralType) {
        log.error("Processing LOAN DEFAULT - DefaultId: {}, LoanId: {}, Outstanding: {} {}, DefaultAmount: {} {}, DaysPastDue: {}", 
                defaultId, loanId, outstandingBalance, currency, defaultAmount, currency, daysPastDue);
        
        loanDefaultService.processDefault(defaultId, loanId, borrowerId, loanType, originalLoanAmount, 
                outstandingBalance, defaultAmount, currency, defaultDate, lastPaymentDate, daysPastDue, 
                defaultReason, riskCategory, creditScore, collateralValue, collateralType);
    }
    
    private void processLoanChargeOff(UUID defaultId, UUID loanId, UUID borrowerId,
                                    BigDecimal outstandingBalance, String currency, LocalDate defaultDate,
                                    BigDecimal collateralValue, String legalStatus) {
        log.error("Processing LOAN CHARGE OFF - DefaultId: {}, LoanId: {}, Outstanding: {} {}, Collateral: {} {}", 
                defaultId, loanId, outstandingBalance, currency, collateralValue, currency);
        
        loanDefaultService.processChargeOff(defaultId, loanId, borrowerId, outstandingBalance, currency, 
                defaultDate, collateralValue, legalStatus);
    }
    
    private void processLoanRecovery(UUID defaultId, UUID loanId, UUID borrowerId, BigDecimal recoveryAmount,
                                   String currency, String recoveryMethod, LocalDate defaultDate,
                                   BigDecimal outstandingBalance) {
        log.info("Processing LOAN RECOVERY - DefaultId: {}, LoanId: {}, RecoveryAmount: {} {}, Method: {}", 
                defaultId, loanId, recoveryAmount, currency, recoveryMethod);
        
        loanDefaultService.processRecovery(defaultId, loanId, borrowerId, recoveryAmount, currency, 
                recoveryMethod, defaultDate, outstandingBalance);
    }
    
    private void processCollectionStart(UUID defaultId, UUID loanId, UUID borrowerId,
                                      BigDecimal outstandingBalance, String currency, UUID collectorId,
                                      List<String> collectionActions) {
        log.warn("Processing COLLECTION START - DefaultId: {}, LoanId: {}, Outstanding: {} {}, Actions: {}", 
                defaultId, loanId, outstandingBalance, currency, collectionActions.size());
        
        loanDefaultService.processCollectionStart(defaultId, loanId, borrowerId, outstandingBalance, 
                currency, collectorId, collectionActions);
    }
    
    private void processLegalAction(UUID defaultId, UUID loanId, UUID borrowerId,
                                  BigDecimal outstandingBalance, String currency, String legalStatus,
                                  BigDecimal collateralValue) {
        log.error("Processing LEGAL ACTION - DefaultId: {}, LoanId: {}, Outstanding: {} {}, Status: {}", 
                defaultId, loanId, outstandingBalance, currency, legalStatus);
        
        loanDefaultService.processLegalAction(defaultId, loanId, borrowerId, outstandingBalance, 
                currency, legalStatus, collateralValue);
    }
    
    private void processGenericEvent(UUID defaultId, UUID loanId, UUID borrowerId, String eventType) {
        log.info("Processing generic loan default event - DefaultId: {}, EventType: {}", 
                defaultId, eventType);
        
        loanDefaultService.processGenericEvent(defaultId, loanId, borrowerId, eventType);
    }
    
    private void handleLoanDelinquency(UUID defaultId, UUID loanId, UUID borrowerId, String loanType,
                                     BigDecimal outstandingBalance, String currency, LocalDate defaultDate,
                                     Integer daysPastDue, String defaultReason, Integer creditScore) {
        try {
            loanDefaultService.recordDelinquency(defaultId, loanId, borrowerId, loanType, outstandingBalance, 
                    currency, defaultDate, daysPastDue, defaultReason, creditScore);
            
        } catch (Exception e) {
            log.error("Failed to handle loan delinquency - DefaultId: {}", defaultId, e);
        }
    }
    
    private void handleLoanDefault(UUID defaultId, UUID loanId, UUID borrowerId, String loanType,
                                 BigDecimal outstandingBalance, BigDecimal defaultAmount, String currency,
                                 LocalDate defaultDate, Integer daysPastDue, BigDecimal collateralValue,
                                 String collateralType) {
        try {
            loanDefaultService.recordDefault(defaultId, loanId, borrowerId, loanType, outstandingBalance, 
                    defaultAmount, currency, defaultDate, daysPastDue, collateralValue, collateralType);
            
        } catch (Exception e) {
            log.error("Failed to handle loan default - DefaultId: {}", defaultId, e);
        }
    }
    
    private void handleLoanChargeOff(UUID defaultId, UUID loanId, UUID borrowerId,
                                   BigDecimal outstandingBalance, String currency, LocalDate defaultDate,
                                   BigDecimal collateralValue) {
        try {
            log.error("Processing loan charge off - DefaultId: {}, Outstanding: {} {}", 
                    defaultId, outstandingBalance, currency);
            
            loanDefaultService.recordChargeOff(defaultId, loanId, borrowerId, outstandingBalance, 
                    currency, defaultDate, collateralValue);
            
        } catch (Exception e) {
            log.error("Failed to handle loan charge off - DefaultId: {}", defaultId, e);
        }
    }
    
    private void handleLoanRecovery(UUID defaultId, UUID loanId, UUID borrowerId, BigDecimal recoveryAmount,
                                  String currency, String recoveryMethod, LocalDate defaultDate) {
        try {
            log.info("Processing loan recovery - DefaultId: {}, RecoveryAmount: {} {}", 
                    defaultId, recoveryAmount, currency);
            
            loanDefaultService.recordRecovery(defaultId, loanId, borrowerId, recoveryAmount, currency, 
                    recoveryMethod, defaultDate);
            
        } catch (Exception e) {
            log.error("Failed to handle loan recovery - DefaultId: {}", defaultId, e);
        }
    }
    
    private void assessDefaultRisk(UUID loanId, UUID borrowerId, String defaultStatus,
                                 BigDecimal outstandingBalance, Integer daysPastDue, Integer creditScore,
                                 BigDecimal collateralValue, String riskCategory) {
        try {
            loanRiskService.assessDefaultRisk(loanId, borrowerId, defaultStatus, outstandingBalance, 
                    daysPastDue, creditScore, collateralValue, riskCategory);
            
            log.debug("Default risk assessed - LoanId: {}, Risk: {}", loanId, riskCategory);
            
        } catch (Exception e) {
            log.error("Failed to assess default risk - LoanId: {}", loanId, e);
        }
    }
    
    private void initiateCollectionActions(UUID defaultId, UUID loanId, UUID borrowerId,
                                         String defaultStatus, BigDecimal outstandingBalance,
                                         String currency, Integer daysPastDue, List<String> collectionActions,
                                         UUID collectorId) {
        try {
            if (daysPastDue >= 30) {
                loanCollectionService.initiateCollection(defaultId, loanId, borrowerId, defaultStatus, 
                        outstandingBalance, currency, daysPastDue, collectionActions, collectorId);
                
                log.warn("Collection actions initiated - DefaultId: {}, DaysPastDue: {}", 
                        defaultId, daysPastDue);
            }
            
        } catch (Exception e) {
            log.error("Failed to initiate collection actions - DefaultId: {}", defaultId, e);
        }
    }
    
    private void updateCreditBureauReporting(UUID borrowerId, UUID loanId, String defaultStatus,
                                           BigDecimal outstandingBalance, Integer daysPastDue,
                                           LocalDate defaultDate) {
        try {
            loanDefaultService.updateCreditBureauReporting(borrowerId, loanId, defaultStatus, 
                    outstandingBalance, daysPastDue, defaultDate);
            
            log.debug("Credit bureau reporting updated - BorrowerId: {}, Status: {}", 
                    borrowerId, defaultStatus);
            
        } catch (Exception e) {
            log.error("Failed to update credit bureau reporting - BorrowerId: {}", borrowerId, e);
        }
    }
    
    private void notifyBorrower(UUID borrowerId, UUID loanId, UUID defaultId, String eventType,
                               String defaultStatus, BigDecimal outstandingBalance, String currency,
                               Integer daysPastDue, String defaultReason) {
        try {
            loanNotificationService.sendDefaultNotification(borrowerId, loanId, defaultId, eventType, 
                    defaultStatus, outstandingBalance, currency, daysPastDue, defaultReason);
            
            log.warn("Borrower notified of default - BorrowerId: {}, DefaultId: {}, Status: {}", 
                    borrowerId, defaultId, defaultStatus);
            
        } catch (Exception e) {
            log.error("Failed to notify borrower - BorrowerId: {}, DefaultId: {}", borrowerId, defaultId, e);
        }
    }
    
    private void notifyCollector(UUID collectorId, UUID defaultId, UUID loanId, UUID borrowerId,
                                String defaultStatus, BigDecimal outstandingBalance, String currency,
                                Integer daysPastDue) {
        try {
            loanNotificationService.sendCollectorNotification(collectorId, defaultId, loanId, borrowerId, 
                    defaultStatus, outstandingBalance, currency, daysPastDue);
            
            log.info("Collector notified - CollectorId: {}, DefaultId: {}", collectorId, defaultId);
            
        } catch (Exception e) {
            log.error("Failed to notify collector - CollectorId: {}", collectorId, e);
        }
    }
    
    private void updateDefaultMetrics(String eventType, String defaultStatus, String loanType,
                                    BigDecimal defaultAmount, Integer daysPastDue, String riskCategory,
                                    BigDecimal collateralValue) {
        try {
            loanDefaultService.updateDefaultMetrics(eventType, defaultStatus, loanType, defaultAmount, 
                    daysPastDue, riskCategory, collateralValue);
        } catch (Exception e) {
            log.error("Failed to update default metrics - EventType: {}, Status: {}", 
                    eventType, defaultStatus, e);
        }
    }
    
    private void auditLoanDefaultEvent(UUID defaultId, UUID loanId, UUID borrowerId, String eventType,
                                     String defaultStatus, BigDecimal outstandingBalance,
                                     BigDecimal defaultAmount, String currency, Integer daysPastDue,
                                     LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditFinancialEvent(
                    "LOAN_DEFAULT_EVENT_PROCESSED",
                    borrowerId.toString(),
                    String.format("Loan default event %s - Status: %s, Outstanding: %s %s, DefaultAmount: %s %s, DaysPastDue: %d", 
                            eventType, defaultStatus, outstandingBalance, currency, defaultAmount, currency, daysPastDue),
                    Map.of(
                            "defaultId", defaultId.toString(),
                            "loanId", loanId.toString(),
                            "borrowerId", borrowerId.toString(),
                            "eventType", eventType,
                            "defaultStatus", defaultStatus,
                            "outstandingBalance", outstandingBalance.toString(),
                            "defaultAmount", defaultAmount.toString(),
                            "currency", currency,
                            "daysPastDue", daysPastDue,
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit loan default event - DefaultId: {}", defaultId, e);
        }
    }
    
    private void handleEventFailure(UUID defaultId, UUID loanId, UUID borrowerId, String eventType,
                                   Exception error) {
        try {
            loanDefaultService.handleEventFailure(defaultId, loanId, borrowerId, eventType, 
                    error.getMessage());
            
            auditService.auditFinancialEvent(
                    "LOAN_DEFAULT_EVENT_PROCESSING_FAILED",
                    borrowerId.toString(),
                    "Failed to process loan default event: " + error.getMessage(),
                    Map.of(
                            "defaultId", defaultId.toString(),
                            "loanId", loanId.toString(),
                            "borrowerId", borrowerId.toString(),
                            "eventType", eventType != null ? eventType : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle event failure - DefaultId: {}", defaultId, e);
        }
    }
    
    @KafkaListener(
        topics = {"loan-default-events.DLQ", "loan-delinquent.DLQ", "loan-charge-off.DLQ", "loan-recovery.DLQ"},
        groupId = "lending-service-loan-default-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: Loan default event sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID defaultId = event.containsKey("defaultId") ? 
                    UUID.fromString((String) event.get("defaultId")) : null;
            UUID loanId = event.containsKey("loanId") ? 
                    UUID.fromString((String) event.get("loanId")) : null;
            UUID borrowerId = event.containsKey("borrowerId") ? 
                    UUID.fromString((String) event.get("borrowerId")) : null;
            String eventType = (String) event.get("eventType");
            
            log.error("DLQ: Loan default event failed permanently - DefaultId: {}, LoanId: {}, BorrowerId: {}, EventType: {} - MANUAL REVIEW REQUIRED", 
                    defaultId, loanId, borrowerId, eventType);
            
            if (defaultId != null && borrowerId != null) {
                loanDefaultService.markForManualReview(defaultId, loanId, borrowerId, eventType, 
                        "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse loan default DLQ event: {}", eventJson, e);
        }
    }
}