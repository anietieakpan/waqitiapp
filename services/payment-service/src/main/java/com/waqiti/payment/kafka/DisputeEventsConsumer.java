package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.payment.service.DisputeService;
import com.waqiti.payment.service.DisputeInvestigationService;
import com.waqiti.payment.service.PaymentNotificationService;
import com.waqiti.payment.service.DisputeDocumentationService;
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
public class DisputeEventsConsumer {
    
    private final DisputeService disputeService;
    private final DisputeInvestigationService disputeInvestigationService;
    private final PaymentNotificationService paymentNotificationService;
    private final DisputeDocumentationService disputeDocumentationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler dlqHandler;
    
    @KafkaListener(
        topics = {"dispute-events", "dispute-created", "dispute-updated", "dispute-escalated"},
        groupId = "payment-service-dispute-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleDisputeEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("DISPUTE: Processing dispute event - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID disputeId = null;
        UUID transactionId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            disputeId = UUID.fromString((String) event.get("disputeId"));
            transactionId = UUID.fromString((String) event.get("transactionId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            String disputeStatus = (String) event.get("disputeStatus");
            String disputeType = (String) event.get("disputeType");
            String disputeReason = (String) event.get("disputeReason");
            BigDecimal disputeAmount = new BigDecimal(event.get("disputeAmount").toString());
            String currency = (String) event.get("currency");
            LocalDateTime disputeDate = LocalDateTime.parse((String) event.get("disputeDate"));
            String merchantId = (String) event.getOrDefault("merchantId", "");
            String merchantName = (String) event.getOrDefault("merchantName", "");
            String paymentMethod = (String) event.get("paymentMethod");
            String customerDescription = (String) event.getOrDefault("customerDescription", "");
            @SuppressWarnings("unchecked")
            List<String> evidenceDocuments = (List<String>) event.getOrDefault("evidenceDocuments", List.of());
            String priority = (String) event.getOrDefault("priority", "MEDIUM");
            UUID investigatorId = event.containsKey("investigatorId") ? 
                    UUID.fromString((String) event.get("investigatorId")) : null;
            LocalDateTime responseDeadline = event.containsKey("responseDeadline") ?
                    LocalDateTime.parse((String) event.get("responseDeadline")) : null;
            String cardNetwork = (String) event.getOrDefault("cardNetwork", "");
            String authorizationCode = (String) event.getOrDefault("authorizationCode", "");
            Boolean isFirstPartyFraud = (Boolean) event.getOrDefault("isFirstPartyFraud", false);
            String disputeCategory = (String) event.getOrDefault("disputeCategory", "GENERAL");
            
            log.info("Dispute event - DisputeId: {}, TransactionId: {}, CustomerId: {}, EventType: {}, Status: {}, Type: {}, Amount: {} {}", 
                    disputeId, transactionId, customerId, eventType, disputeStatus, disputeType, 
                    disputeAmount, currency);
            
            validateDisputeEvent(disputeId, transactionId, customerId, eventType, disputeStatus, 
                    disputeAmount);
            
            processEventByType(disputeId, transactionId, customerId, eventType, disputeStatus, 
                    disputeType, disputeReason, disputeAmount, currency, disputeDate, merchantId, 
                    merchantName, paymentMethod, customerDescription, evidenceDocuments, priority, 
                    investigatorId, responseDeadline, cardNetwork, authorizationCode, isFirstPartyFraud, 
                    disputeCategory);
            
            if ("CREATED".equals(eventType)) {
                handleDisputeCreation(disputeId, transactionId, customerId, disputeType, disputeReason, 
                        disputeAmount, currency, disputeDate, merchantId, customerDescription, 
                        evidenceDocuments, priority);
            } else if ("UPDATED".equals(eventType)) {
                handleDisputeUpdate(disputeId, transactionId, customerId, disputeStatus, 
                        evidenceDocuments, investigatorId);
            } else if ("ESCALATED".equals(eventType)) {
                handleDisputeEscalation(disputeId, transactionId, customerId, disputeAmount, 
                        currency, priority, investigatorId, responseDeadline);
            }
            
            initiateInvestigation(disputeId, transactionId, customerId, disputeType, disputeAmount, 
                    merchantId, paymentMethod, evidenceDocuments, isFirstPartyFraud, disputeCategory);
            
            processDocumentation(disputeId, transactionId, customerDescription, evidenceDocuments, 
                    authorizationCode, cardNetwork);
            
            calculateDisputeLiability(disputeId, transactionId, disputeAmount, disputeType, 
                    merchantId, cardNetwork, isFirstPartyFraud);
            
            notifyStakeholders(customerId, disputeId, transactionId, eventType, disputeStatus, 
                    disputeAmount, currency, merchantId, investigatorId);
            
            updateDisputeMetrics(eventType, disputeStatus, disputeType, disputeAmount, priority, 
                    isFirstPartyFraud, disputeCategory);
            
            auditDisputeEvent(disputeId, transactionId, customerId, eventType, disputeStatus, 
                    disputeType, disputeAmount, currency, processingStartTime);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Dispute event processed - DisputeId: {}, EventType: {}, Status: {}, ProcessingTime: {}ms", 
                    disputeId, eventType, disputeStatus, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing dispute event: topic={}, partition={}, offset={}, error={}",
                topic, partition, offset, e.getMessage(), e);

            if (disputeId != null && customerId != null) {
                handleEventFailure(disputeId, transactionId, customerId, eventType, e);
            }

            dlqHandler.handleFailedMessage(org.apache.kafka.clients.consumer.ConsumerRecord.class.cast(eventJson), e)
                .thenAccept(result -> log.info("Message sent to DLQ: topic={}, offset={}, destination={}, category={}",
                        topic, offset, result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed - MESSAGE MAY BE LOST! " +
                            "topic={}, partition={}, offset={}, error={}",
                            topic, partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Dispute event processing failed", e);
        }
    }
    
    private void validateDisputeEvent(UUID disputeId, UUID transactionId, UUID customerId,
                                    String eventType, String disputeStatus, BigDecimal disputeAmount) {
        if (disputeId == null || transactionId == null || customerId == null) {
            throw new IllegalArgumentException("Dispute ID, Transaction ID, and Customer ID are required");
        }
        
        if (eventType == null || eventType.trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }
        
        if (disputeStatus == null || disputeStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("Dispute status is required");
        }
        
        if (disputeAmount == null || disputeAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid dispute amount");
        }
        
        log.debug("Dispute event validation passed - DisputeId: {}", disputeId);
    }
    
    private void processEventByType(UUID disputeId, UUID transactionId, UUID customerId, String eventType,
                                   String disputeStatus, String disputeType, String disputeReason,
                                   BigDecimal disputeAmount, String currency, LocalDateTime disputeDate,
                                   String merchantId, String merchantName, String paymentMethod,
                                   String customerDescription, List<String> evidenceDocuments,
                                   String priority, UUID investigatorId, LocalDateTime responseDeadline,
                                   String cardNetwork, String authorizationCode, Boolean isFirstPartyFraud,
                                   String disputeCategory) {
        try {
            switch (eventType) {
                case "CREATED" -> processDisputeCreated(disputeId, transactionId, customerId, 
                        disputeType, disputeReason, disputeAmount, currency, disputeDate, merchantId, 
                        merchantName, paymentMethod, customerDescription, evidenceDocuments, priority);
                
                case "UPDATED" -> processDisputeUpdated(disputeId, transactionId, customerId, 
                        disputeStatus, evidenceDocuments, investigatorId, responseDeadline);
                
                case "ESCALATED" -> processDisputeEscalated(disputeId, transactionId, customerId, 
                        disputeAmount, currency, priority, investigatorId, responseDeadline, 
                        disputeCategory);
                
                case "INVESTIGATED" -> processDisputeInvestigated(disputeId, transactionId, 
                        customerId, disputeStatus, investigatorId, evidenceDocuments);
                
                case "RESOLVED" -> processDisputeResolved(disputeId, transactionId, customerId, 
                        disputeStatus, disputeAmount, currency, responseDeadline);
                
                case "CLOSED" -> processDisputeClosed(disputeId, transactionId, customerId, 
                        disputeStatus, disputeAmount, currency);
                
                default -> {
                    log.warn("Unknown dispute event type: {}", eventType);
                    processGenericEvent(disputeId, transactionId, customerId, eventType);
                }
            }
            
            log.debug("Event type processing completed - DisputeId: {}, EventType: {}", 
                    disputeId, eventType);
            
        } catch (Exception e) {
            log.error("Failed to process event by type - DisputeId: {}, EventType: {}", 
                    disputeId, eventType, e);
            throw new RuntimeException("Event type processing failed", e);
        }
    }
    
    private void processDisputeCreated(UUID disputeId, UUID transactionId, UUID customerId,
                                     String disputeType, String disputeReason, BigDecimal disputeAmount,
                                     String currency, LocalDateTime disputeDate, String merchantId,
                                     String merchantName, String paymentMethod, String customerDescription,
                                     List<String> evidenceDocuments, String priority) {
        log.info("Processing DISPUTE CREATED - DisputeId: {}, TransactionId: {}, Type: {}, Amount: {} {}", 
                disputeId, transactionId, disputeType, disputeAmount, currency);
        
        disputeService.processDisputeCreated(disputeId, transactionId, customerId, disputeType, 
                disputeReason, disputeAmount, currency, disputeDate, merchantId, merchantName, 
                paymentMethod, customerDescription, evidenceDocuments, priority);
    }
    
    private void processDisputeUpdated(UUID disputeId, UUID transactionId, UUID customerId,
                                     String disputeStatus, List<String> evidenceDocuments,
                                     UUID investigatorId, LocalDateTime responseDeadline) {
        log.info("Processing DISPUTE UPDATED - DisputeId: {}, Status: {}, Evidence: {}", 
                disputeId, disputeStatus, evidenceDocuments.size());
        
        disputeService.processDisputeUpdated(disputeId, transactionId, customerId, disputeStatus, 
                evidenceDocuments, investigatorId, responseDeadline);
    }
    
    private void processDisputeEscalated(UUID disputeId, UUID transactionId, UUID customerId,
                                       BigDecimal disputeAmount, String currency, String priority,
                                       UUID investigatorId, LocalDateTime responseDeadline,
                                       String disputeCategory) {
        log.warn("Processing DISPUTE ESCALATED - DisputeId: {}, Amount: {} {}, Priority: {}", 
                disputeId, disputeAmount, currency, priority);
        
        disputeService.processDisputeEscalated(disputeId, transactionId, customerId, disputeAmount, 
                currency, priority, investigatorId, responseDeadline, disputeCategory);
    }
    
    private void processDisputeInvestigated(UUID disputeId, UUID transactionId, UUID customerId,
                                          String disputeStatus, UUID investigatorId,
                                          List<String> evidenceDocuments) {
        log.info("Processing DISPUTE INVESTIGATED - DisputeId: {}, Status: {}, Investigator: {}", 
                disputeId, disputeStatus, investigatorId);
        
        disputeService.processDisputeInvestigated(disputeId, transactionId, customerId, disputeStatus, 
                investigatorId, evidenceDocuments);
    }
    
    private void processDisputeResolved(UUID disputeId, UUID transactionId, UUID customerId,
                                      String disputeStatus, BigDecimal disputeAmount, String currency,
                                      LocalDateTime responseDeadline) {
        log.info("Processing DISPUTE RESOLVED - DisputeId: {}, Status: {}, Amount: {} {}", 
                disputeId, disputeStatus, disputeAmount, currency);
        
        disputeService.processDisputeResolved(disputeId, transactionId, customerId, disputeStatus, 
                disputeAmount, currency, responseDeadline);
    }
    
    private void processDisputeClosed(UUID disputeId, UUID transactionId, UUID customerId,
                                    String disputeStatus, BigDecimal disputeAmount, String currency) {
        log.info("Processing DISPUTE CLOSED - DisputeId: {}, Status: {}, Amount: {} {}", 
                disputeId, disputeStatus, disputeAmount, currency);
        
        disputeService.processDisputeClosed(disputeId, transactionId, customerId, disputeStatus, 
                disputeAmount, currency);
    }
    
    private void processGenericEvent(UUID disputeId, UUID transactionId, UUID customerId, String eventType) {
        log.info("Processing generic dispute event - DisputeId: {}, EventType: {}", disputeId, eventType);
        
        disputeService.processGenericEvent(disputeId, transactionId, customerId, eventType);
    }
    
    private void handleDisputeCreation(UUID disputeId, UUID transactionId, UUID customerId,
                                     String disputeType, String disputeReason, BigDecimal disputeAmount,
                                     String currency, LocalDateTime disputeDate, String merchantId,
                                     String customerDescription, List<String> evidenceDocuments,
                                     String priority) {
        try {
            disputeService.recordDisputeCreation(disputeId, transactionId, customerId, disputeType, 
                    disputeReason, disputeAmount, currency, disputeDate, merchantId, customerDescription, 
                    evidenceDocuments, priority);
            
        } catch (Exception e) {
            log.error("Failed to handle dispute creation - DisputeId: {}", disputeId, e);
        }
    }
    
    private void handleDisputeUpdate(UUID disputeId, UUID transactionId, UUID customerId,
                                   String disputeStatus, List<String> evidenceDocuments,
                                   UUID investigatorId) {
        try {
            disputeService.recordDisputeUpdate(disputeId, transactionId, customerId, disputeStatus, 
                    evidenceDocuments, investigatorId);
            
        } catch (Exception e) {
            log.error("Failed to handle dispute update - DisputeId: {}", disputeId, e);
        }
    }
    
    private void handleDisputeEscalation(UUID disputeId, UUID transactionId, UUID customerId,
                                       BigDecimal disputeAmount, String currency, String priority,
                                       UUID investigatorId, LocalDateTime responseDeadline) {
        try {
            log.warn("Processing dispute escalation - DisputeId: {}, Amount: {} {}, Priority: {}", 
                    disputeId, disputeAmount, currency, priority);
            
            disputeService.recordDisputeEscalation(disputeId, transactionId, customerId, disputeAmount, 
                    currency, priority, investigatorId, responseDeadline);
            
        } catch (Exception e) {
            log.error("Failed to handle dispute escalation - DisputeId: {}", disputeId, e);
        }
    }
    
    private void initiateInvestigation(UUID disputeId, UUID transactionId, UUID customerId,
                                     String disputeType, BigDecimal disputeAmount, String merchantId,
                                     String paymentMethod, List<String> evidenceDocuments,
                                     Boolean isFirstPartyFraud, String disputeCategory) {
        try {
            disputeInvestigationService.initiateInvestigation(disputeId, transactionId, customerId, 
                    disputeType, disputeAmount, merchantId, paymentMethod, evidenceDocuments, 
                    isFirstPartyFraud, disputeCategory);
            
            log.debug("Dispute investigation initiated - DisputeId: {}", disputeId);
            
        } catch (Exception e) {
            log.error("Failed to initiate investigation - DisputeId: {}", disputeId, e);
        }
    }
    
    private void processDocumentation(UUID disputeId, UUID transactionId, String customerDescription,
                                    List<String> evidenceDocuments, String authorizationCode,
                                    String cardNetwork) {
        try {
            disputeDocumentationService.processDocumentation(disputeId, transactionId, 
                    customerDescription, evidenceDocuments, authorizationCode, cardNetwork);
            
            log.debug("Dispute documentation processed - DisputeId: {}, Documents: {}", 
                    disputeId, evidenceDocuments.size());
            
        } catch (Exception e) {
            log.error("Failed to process documentation - DisputeId: {}", disputeId, e);
        }
    }
    
    private void calculateDisputeLiability(UUID disputeId, UUID transactionId, BigDecimal disputeAmount,
                                         String disputeType, String merchantId, String cardNetwork,
                                         Boolean isFirstPartyFraud) {
        try {
            disputeService.calculateLiability(disputeId, transactionId, disputeAmount, disputeType, 
                    merchantId, cardNetwork, isFirstPartyFraud);
            
            log.debug("Dispute liability calculated - DisputeId: {}", disputeId);
            
        } catch (Exception e) {
            log.error("Failed to calculate dispute liability - DisputeId: {}", disputeId, e);
        }
    }
    
    private void notifyStakeholders(UUID customerId, UUID disputeId, UUID transactionId,
                                  String eventType, String disputeStatus, BigDecimal disputeAmount,
                                  String currency, String merchantId, UUID investigatorId) {
        try {
            paymentNotificationService.sendDisputeNotification(customerId, disputeId, transactionId, 
                    eventType, disputeStatus, disputeAmount, currency, merchantId, investigatorId);
            
            log.info("Dispute stakeholders notified - DisputeId: {}, EventType: {}", disputeId, eventType);
            
        } catch (Exception e) {
            log.error("Failed to notify stakeholders - DisputeId: {}", disputeId, e);
        }
    }
    
    private void updateDisputeMetrics(String eventType, String disputeStatus, String disputeType,
                                    BigDecimal disputeAmount, String priority, Boolean isFirstPartyFraud,
                                    String disputeCategory) {
        try {
            disputeService.updateDisputeMetrics(eventType, disputeStatus, disputeType, disputeAmount, 
                    priority, isFirstPartyFraud, disputeCategory);
        } catch (Exception e) {
            log.error("Failed to update dispute metrics - EventType: {}, Status: {}", 
                    eventType, disputeStatus, e);
        }
    }
    
    private void auditDisputeEvent(UUID disputeId, UUID transactionId, UUID customerId, String eventType,
                                 String disputeStatus, String disputeType, BigDecimal disputeAmount,
                                 String currency, LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditFinancialEvent(
                    "DISPUTE_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Dispute event %s - Status: %s, Type: %s, Amount: %s %s", 
                            eventType, disputeStatus, disputeType, disputeAmount, currency),
                    Map.of(
                            "disputeId", disputeId.toString(),
                            "transactionId", transactionId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "disputeStatus", disputeStatus,
                            "disputeType", disputeType,
                            "disputeAmount", disputeAmount.toString(),
                            "currency", currency,
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit dispute event - DisputeId: {}", disputeId, e);
        }
    }
    
    private void handleEventFailure(UUID disputeId, UUID transactionId, UUID customerId,
                                   String eventType, Exception error) {
        try {
            disputeService.handleEventFailure(disputeId, transactionId, customerId, eventType, 
                    error.getMessage());
            
            auditService.auditFinancialEvent(
                    "DISPUTE_EVENT_PROCESSING_FAILED",
                    customerId.toString(),
                    "Failed to process dispute event: " + error.getMessage(),
                    Map.of(
                            "disputeId", disputeId.toString(),
                            "transactionId", transactionId != null ? transactionId.toString() : "UNKNOWN",
                            "customerId", customerId.toString(),
                            "eventType", eventType != null ? eventType : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle event failure - DisputeId: {}", disputeId, e);
        }
    }
    
    @KafkaListener(
        topics = {"dispute-events.DLQ", "dispute-created.DLQ", "dispute-updated.DLQ", "dispute-escalated.DLQ"},
        groupId = "payment-service-dispute-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: Dispute event sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID disputeId = event.containsKey("disputeId") ? 
                    UUID.fromString((String) event.get("disputeId")) : null;
            UUID transactionId = event.containsKey("transactionId") ? 
                    UUID.fromString((String) event.get("transactionId")) : null;
            UUID customerId = event.containsKey("customerId") ? 
                    UUID.fromString((String) event.get("customerId")) : null;
            String eventType = (String) event.get("eventType");
            
            log.error("DLQ: Dispute event failed permanently - DisputeId: {}, TransactionId: {}, CustomerId: {}, EventType: {} - MANUAL REVIEW REQUIRED", 
                    disputeId, transactionId, customerId, eventType);
            
            if (disputeId != null && customerId != null) {
                disputeService.markForManualReview(disputeId, transactionId, customerId, eventType, 
                        "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse dispute DLQ event: {}", eventJson, e);
        }
    }
}