package com.waqiti.merchant.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.merchant.service.ChargebackPreventionService;
import com.waqiti.merchant.service.RiskScoringService;
import com.waqiti.merchant.service.MerchantNotificationService;
import com.waqiti.merchant.service.DisputePreventionService;
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

/**
 * CRITICAL MERCHANT PROTECTION CONSUMER: Chargeback Prevention
 * 
 * Processes chargeback prevention events to protect merchants from
 * fraudulent chargebacks and reduce financial losses.
 * 
 * BUSINESS IMPACT:
 * - Chargebacks cost merchants 2-3x the transaction amount
 * - High chargeback ratios lead to account termination
 * - Prevention saves millions in losses annually
 * 
 * KEY FEATURES:
 * - Early warning detection
 * - Automated dispute responses
 * - Risk pattern analysis
 * - Merchant coaching
 * - Compliance monitoring
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChargebackPreventionEventsConsumer {
    
    private final ChargebackPreventionService chargebackPreventionService;
    private final RiskScoringService riskScoringService;
    private final MerchantNotificationService merchantNotificationService;
    private final DisputePreventionService disputePreventionService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    // Industry threshold: >1% chargeback ratio = high risk, >1.5% = account closure risk
    private static final BigDecimal CHARGEBACK_WARNING_THRESHOLD = new BigDecimal("0.01"); // 1%
    private static final BigDecimal CHARGEBACK_CRITICAL_THRESHOLD = new BigDecimal("0.015"); // 1.5%
    
    @KafkaListener(
        topics = "chargeback-prevention-events",
        groupId = "merchant-service-chargeback-prevention-group",
        containerFactory = "criticalMerchantKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleChargebackPreventionEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.warn("CHARGEBACK PREVENTION: Processing prevention event - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID eventId = null;
        UUID merchantId = null;
        String eventType = null;
        
        try {
            // Parse chargeback prevention event
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            eventId = UUID.fromString((String) event.get("eventId"));
            merchantId = UUID.fromString((String) event.get("merchantId"));
            eventType = (String) event.get("eventType");
            UUID transactionId = event.containsKey("transactionId") ? 
                    UUID.fromString((String) event.get("transactionId")) : null;
            UUID customerId = event.containsKey("customerId") ? 
                    UUID.fromString((String) event.get("customerId")) : null;
            BigDecimal transactionAmount = event.containsKey("transactionAmount") ? 
                    new BigDecimal(event.get("transactionAmount").toString()) : null;
            String currency = (String) event.getOrDefault("currency", "USD");
            String chargebackReason = (String) event.get("chargebackReason");
            Integer riskScore = event.containsKey("riskScore") ? (Integer) event.get("riskScore") : 0;
            @SuppressWarnings("unchecked")
            List<String> riskIndicators = (List<String>) event.getOrDefault("riskIndicators", List.of());
            LocalDateTime eventTimestamp = LocalDateTime.parse((String) event.get("timestamp"));
            String productCategory = (String) event.get("productCategory");
            String paymentMethod = (String) event.get("paymentMethod");
            Boolean isFirstPurchase = (Boolean) event.getOrDefault("isFirstPurchase", false);
            Integer daysSincePurchase = event.containsKey("daysSincePurchase") ? 
                    (Integer) event.get("daysSincePurchase") : null;
            
            log.warn("CHARGEBACK PREVENTION - EventId: {}, Type: {}, MerchantId: {}, TransactionId: {}, Amount: {} {}, Reason: {}, RiskScore: {}", 
                    eventId, eventType, merchantId, transactionId, transactionAmount, currency, 
                    chargebackReason, riskScore);
            
            // Validate event data
            validateChargebackPreventionEvent(eventId, merchantId, eventType);
            
            // Process based on event type
            processChargebackPreventionByType(eventId, eventType, merchantId, transactionId, 
                    customerId, transactionAmount, currency, chargebackReason, riskScore, 
                    riskIndicators, productCategory, paymentMethod, isFirstPurchase, daysSincePurchase);
            
            // Calculate merchant chargeback ratio
            ChargebackRatio merchantRatio = calculateMerchantChargebackRatio(merchantId, transactionAmount);
            
            // Check if merchant is at risk
            if (merchantRatio.getRatio().compareTo(CHARGEBACK_WARNING_THRESHOLD) >= 0) {
                handleHighChargebackRatio(merchantId, merchantRatio, eventId, transactionId);
            }
            
            // Update merchant risk profile
            updateMerchantRiskProfile(merchantId, eventType, chargebackReason, riskScore, 
                    riskIndicators, merchantRatio);
            
            // Create prevention case if needed
            if (shouldCreatePreventionCase(eventType, riskScore, merchantRatio)) {
                String caseId = createPreventionCase(eventId, merchantId, transactionId, 
                        customerId, eventType, chargebackReason, riskScore, transactionAmount, 
                        currency, riskIndicators);
                
                log.info("Chargeback prevention case created - EventId: {}, CaseId: {}", eventId, caseId);
            }
            
            // Send automated response if applicable
            if (canAutoRespond(eventType, riskScore, riskIndicators)) {
                sendAutomatedResponse(eventId, merchantId, transactionId, eventType, 
                        chargebackReason, transactionAmount);
            }
            
            // Notify merchant
            notifyMerchant(merchantId, eventId, eventType, transactionId, transactionAmount, 
                    currency, chargebackReason, riskScore, merchantRatio);
            
            // Update prevention metrics
            updatePreventionMetrics(merchantId, eventType, chargebackReason, riskScore, 
                    transactionAmount, merchantRatio);
            
            // Comprehensive audit trail
            auditChargebackPreventionEvent(eventId, merchantId, eventType, transactionId, 
                    chargebackReason, riskScore, transactionAmount, merchantRatio, processingStartTime);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Chargeback prevention event processed - EventId: {}, Type: {}, MerchantId: {}, ProcessingTime: {}ms", 
                    eventId, eventType, merchantId, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Chargeback prevention processing failed - EventId: {}, Type: {}, MerchantId: {}, Error: {}", 
                    eventId, eventType, merchantId, e.getMessage(), e);
            
            if (eventId != null && merchantId != null) {
                handlePreventionFailure(eventId, merchantId, eventType, e);
            }
            
            throw new RuntimeException("Chargeback prevention processing failed", e);
        }
    }
    
    private void validateChargebackPreventionEvent(UUID eventId, UUID merchantId, String eventType) {
        if (eventId == null || merchantId == null) {
            throw new IllegalArgumentException("Event ID and Merchant ID are required");
        }
        
        if (eventType == null || eventType.trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }
        
        List<String> validEventTypes = List.of(
            "EARLY_WARNING", "DISPUTE_FILED", "FRAUD_NOTIFICATION", "INQUIRY_RECEIVED",
            "RETRIEVAL_REQUEST", "PRE_ARBITRATION", "HIGH_RISK_TRANSACTION", 
            "PATTERN_DETECTED", "CUSTOMER_COMPLAINT"
        );
        
        if (!validEventTypes.contains(eventType)) {
            log.warn("Unknown chargeback prevention event type: {}", eventType);
        }
        
        log.debug("Chargeback prevention event validation passed - EventId: {}", eventId);
    }
    
    private void processChargebackPreventionByType(UUID eventId, String eventType, UUID merchantId,
                                                   UUID transactionId, UUID customerId,
                                                   BigDecimal transactionAmount, String currency,
                                                   String chargebackReason, Integer riskScore,
                                                   List<String> riskIndicators, String productCategory,
                                                   String paymentMethod, Boolean isFirstPurchase,
                                                   Integer daysSincePurchase) {
        try {
            switch (eventType) {
                case "EARLY_WARNING" -> processEarlyWarning(eventId, merchantId, transactionId, 
                        customerId, transactionAmount, currency, chargebackReason, riskScore);
                
                case "DISPUTE_FILED" -> processDisputeFiled(eventId, merchantId, transactionId, 
                        customerId, transactionAmount, currency, chargebackReason, riskScore);
                
                case "FRAUD_NOTIFICATION" -> processFraudNotification(eventId, merchantId, 
                        transactionId, customerId, transactionAmount, chargebackReason);
                
                case "INQUIRY_RECEIVED" -> processInquiryReceived(eventId, merchantId, 
                        transactionId, customerId, chargebackReason);
                
                case "RETRIEVAL_REQUEST" -> processRetrievalRequest(eventId, merchantId, 
                        transactionId, customerId, chargebackReason);
                
                case "PRE_ARBITRATION" -> processPreArbitration(eventId, merchantId, 
                        transactionId, transactionAmount, chargebackReason);
                
                case "HIGH_RISK_TRANSACTION" -> processHighRiskTransaction(eventId, merchantId, 
                        transactionId, customerId, transactionAmount, riskScore, riskIndicators);
                
                case "PATTERN_DETECTED" -> processPatternDetected(eventId, merchantId, 
                        chargebackReason, riskIndicators, productCategory);
                
                case "CUSTOMER_COMPLAINT" -> processCustomerComplaint(eventId, merchantId, 
                        transactionId, customerId, chargebackReason);
                
                default -> processGenericPreventionEvent(eventId, merchantId, eventType, 
                        transactionId, chargebackReason);
            }
            
            log.debug("Event type processing completed - EventId: {}, Type: {}", eventId, eventType);
            
        } catch (Exception e) {
            log.error("Failed to process chargeback prevention by type - EventId: {}, Type: {}", 
                    eventId, eventType, e);
            throw new RuntimeException("Prevention event type processing failed", e);
        }
    }
    
    private void processEarlyWarning(UUID eventId, UUID merchantId, UUID transactionId, 
                                    UUID customerId, BigDecimal transactionAmount, String currency,
                                    String chargebackReason, Integer riskScore) {
        log.warn("EARLY WARNING: Potential chargeback detected - EventId: {}, MerchantId: {}, TransactionId: {}, Reason: {}", 
                eventId, merchantId, transactionId, chargebackReason);
        
        chargebackPreventionService.handleEarlyWarning(eventId, merchantId, transactionId, 
                customerId, transactionAmount, currency, chargebackReason, riskScore);
        
        // Immediate merchant notification - they have 72 hours to respond
        merchantNotificationService.sendUrgentChargebackWarning(merchantId, transactionId, 
                transactionAmount, currency, chargebackReason, "72 hours");
    }
    
    private void processDisputeFiled(UUID eventId, UUID merchantId, UUID transactionId, 
                                    UUID customerId, BigDecimal transactionAmount, String currency,
                                    String chargebackReason, Integer riskScore) {
        log.error("DISPUTE FILED: Chargeback officially filed - EventId: {}, MerchantId: {}, TransactionId: {}, Amount: {} {}", 
                eventId, merchantId, transactionId, transactionAmount, currency);
        
        disputePreventionService.handleDisputeFiling(eventId, merchantId, transactionId, 
                customerId, transactionAmount, currency, chargebackReason, riskScore);
        
        // Create dispute response case with deadline
        disputePreventionService.createDisputeResponseCase(eventId, merchantId, transactionId, 
                chargebackReason, transactionAmount, currency);
    }
    
    private void processFraudNotification(UUID eventId, UUID merchantId, UUID transactionId, 
                                         UUID customerId, BigDecimal transactionAmount, String chargebackReason) {
        log.error("FRAUD NOTIFICATION: Fraudulent transaction reported - EventId: {}, MerchantId: {}, TransactionId: {}", 
                eventId, merchantId, transactionId);
        
        chargebackPreventionService.handleFraudNotification(eventId, merchantId, transactionId, 
                customerId, transactionAmount, chargebackReason);
        
        // Investigate merchant's fraud prevention measures
        riskScoringService.auditMerchantFraudPrevention(merchantId, transactionId);
    }
    
    private void processInquiryReceived(UUID eventId, UUID merchantId, UUID transactionId, 
                                       UUID customerId, String chargebackReason) {
        log.info("INQUIRY RECEIVED: Customer inquiry about transaction - EventId: {}, MerchantId: {}, TransactionId: {}", 
                eventId, merchantId, transactionId);
        
        chargebackPreventionService.handleCustomerInquiry(eventId, merchantId, transactionId, 
                customerId, chargebackReason);
        
        // Prompt merchant response - can prevent chargeback if resolved quickly
        merchantNotificationService.sendInquiryAlert(merchantId, transactionId, chargebackReason);
    }
    
    private void processRetrievalRequest(UUID eventId, UUID merchantId, UUID transactionId, 
                                        UUID customerId, String chargebackReason) {
        log.warn("RETRIEVAL REQUEST: Transaction documentation requested - EventId: {}, MerchantId: {}, TransactionId: {}", 
                eventId, merchantId, transactionId);
        
        chargebackPreventionService.handleRetrievalRequest(eventId, merchantId, transactionId, 
                customerId, chargebackReason);
    }
    
    private void processPreArbitration(UUID eventId, UUID merchantId, UUID transactionId, 
                                      BigDecimal transactionAmount, String chargebackReason) {
        log.error("PRE-ARBITRATION: Dispute escalated - EventId: {}, MerchantId: {}, TransactionId: {}, Amount: {}", 
                eventId, merchantId, transactionId, transactionAmount);
        
        disputePreventionService.handlePreArbitration(eventId, merchantId, transactionId, 
                transactionAmount, chargebackReason);
        
        // High-stakes - merchant needs legal review
        merchantNotificationService.sendPreArbitrationAlert(merchantId, transactionId, 
                transactionAmount, chargebackReason);
    }
    
    private void processHighRiskTransaction(UUID eventId, UUID merchantId, UUID transactionId, 
                                           UUID customerId, BigDecimal transactionAmount,
                                           Integer riskScore, List<String> riskIndicators) {
        log.warn("HIGH RISK TRANSACTION: Elevated chargeback risk - EventId: {}, MerchantId: {}, TransactionId: {}, RiskScore: {}", 
                eventId, merchantId, transactionId, riskScore);
        
        chargebackPreventionService.handleHighRiskTransaction(eventId, merchantId, transactionId, 
                customerId, transactionAmount, riskScore, riskIndicators);
        
        // Enhanced monitoring for this transaction
        chargebackPreventionService.enableTransactionMonitoring(merchantId, transactionId, riskScore);
    }
    
    private void processPatternDetected(UUID eventId, UUID merchantId, String chargebackReason, 
                                       List<String> riskIndicators, String productCategory) {
        log.warn("PATTERN DETECTED: Chargeback pattern identified - EventId: {}, MerchantId: {}, Reason: {}, Category: {}", 
                eventId, merchantId, chargebackReason, productCategory);
        
        chargebackPreventionService.handlePatternDetection(eventId, merchantId, chargebackReason, 
                riskIndicators, productCategory);
        
        // Merchant coaching opportunity
        merchantNotificationService.sendPatternAlert(merchantId, chargebackReason, riskIndicators);
    }
    
    private void processCustomerComplaint(UUID eventId, UUID merchantId, UUID transactionId, 
                                         UUID customerId, String chargebackReason) {
        log.info("CUSTOMER COMPLAINT: Pre-chargeback complaint received - EventId: {}, MerchantId: {}, TransactionId: {}", 
                eventId, merchantId, transactionId);
        
        chargebackPreventionService.handleCustomerComplaint(eventId, merchantId, transactionId, 
                customerId, chargebackReason);
        
        // Opportunity to resolve before chargeback
        merchantNotificationService.sendComplaintResolutionOpportunity(merchantId, transactionId, 
                customerId, chargebackReason);
    }
    
    private void processGenericPreventionEvent(UUID eventId, UUID merchantId, String eventType, 
                                              UUID transactionId, String chargebackReason) {
        log.info("Processing generic prevention event - EventId: {}, Type: {}", eventId, eventType);
        
        chargebackPreventionService.handleGenericEvent(eventId, merchantId, eventType, 
                transactionId, chargebackReason);
    }
    
    private ChargebackRatio calculateMerchantChargebackRatio(UUID merchantId, BigDecimal currentAmount) {
        try {
            return chargebackPreventionService.calculateChargebackRatio(merchantId, currentAmount);
        } catch (Exception e) {
            log.error("Failed to calculate chargeback ratio - MerchantId: {}", merchantId, e);
            return ChargebackRatio.builder()
                    .ratio(BigDecimal.ZERO)
                    .totalChargebacks(0)
                    .totalTransactions(0)
                    .build();
        }
    }
    
    private void handleHighChargebackRatio(UUID merchantId, ChargebackRatio ratio, UUID eventId, 
                                          UUID transactionId) {
        try {
            if (ratio.getRatio().compareTo(CHARGEBACK_CRITICAL_THRESHOLD) >= 0) {
                log.error("CRITICAL CHARGEBACK RATIO: Merchant at risk of account closure - MerchantId: {}, Ratio: {}%", 
                        merchantId, ratio.getRatio().multiply(new BigDecimal("100")));
                
                chargebackPreventionService.flagForAccountReview(merchantId, ratio, eventId);
                merchantNotificationService.sendCriticalChargebackAlert(merchantId, ratio);
                
            } else if (ratio.getRatio().compareTo(CHARGEBACK_WARNING_THRESHOLD) >= 0) {
                log.warn("WARNING CHARGEBACK RATIO: Merchant needs improvement - MerchantId: {}, Ratio: {}%", 
                        merchantId, ratio.getRatio().multiply(new BigDecimal("100")));
                
                chargebackPreventionService.initiateCoaching(merchantId, ratio, eventId);
                merchantNotificationService.sendChargebackWarning(merchantId, ratio);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle high chargeback ratio - MerchantId: {}", merchantId, e);
        }
    }
    
    private void updateMerchantRiskProfile(UUID merchantId, String eventType, String chargebackReason,
                                          Integer riskScore, List<String> riskIndicators,
                                          ChargebackRatio ratio) {
        try {
            riskScoringService.updateMerchantRiskProfile(merchantId, eventType, chargebackReason, 
                    riskScore, riskIndicators, ratio);
            
            log.debug("Merchant risk profile updated - MerchantId: {}", merchantId);
            
        } catch (Exception e) {
            log.error("Failed to update merchant risk profile - MerchantId: {}", merchantId, e);
        }
    }
    
    private boolean shouldCreatePreventionCase(String eventType, Integer riskScore, ChargebackRatio ratio) {
        if (List.of("DISPUTE_FILED", "PRE_ARBITRATION", "FRAUD_NOTIFICATION").contains(eventType)) {
            return true;
        }
        
        if (riskScore >= 70) {
            return true;
        }
        
        if (ratio.getRatio().compareTo(CHARGEBACK_WARNING_THRESHOLD) >= 0) {
            return true;
        }
        
        return false;
    }
    
    private String createPreventionCase(UUID eventId, UUID merchantId, UUID transactionId, 
                                       UUID customerId, String eventType, String chargebackReason,
                                       Integer riskScore, BigDecimal transactionAmount, String currency,
                                       List<String> riskIndicators) {
        try {
            return chargebackPreventionService.createPreventionCase(eventId, merchantId, 
                    transactionId, customerId, eventType, chargebackReason, riskScore, 
                    transactionAmount, currency, riskIndicators);
        } catch (Exception e) {
            log.error("Failed to create prevention case - EventId: {}, MerchantId: {}", eventId, merchantId, e);
            return null;
        }
    }
    
    private boolean canAutoRespond(String eventType, Integer riskScore, List<String> riskIndicators) {
        // Auto-respond to low-risk inquiries and retrieval requests
        if (List.of("INQUIRY_RECEIVED", "RETRIEVAL_REQUEST").contains(eventType) && riskScore < 50) {
            return true;
        }
        
        // Don't auto-respond to high-risk or fraud cases
        return false;
    }
    
    private void sendAutomatedResponse(UUID eventId, UUID merchantId, UUID transactionId, 
                                      String eventType, String chargebackReason, BigDecimal transactionAmount) {
        try {
            disputePreventionService.sendAutomatedResponse(eventId, merchantId, transactionId, 
                    eventType, chargebackReason, transactionAmount);
            
            log.info("Automated response sent - EventId: {}, MerchantId: {}, Type: {}", 
                    eventId, merchantId, eventType);
            
        } catch (Exception e) {
            log.error("Failed to send automated response - EventId: {}", eventId, e);
        }
    }
    
    private void notifyMerchant(UUID merchantId, UUID eventId, String eventType, UUID transactionId,
                               BigDecimal transactionAmount, String currency, String chargebackReason,
                               Integer riskScore, ChargebackRatio ratio) {
        try {
            merchantNotificationService.sendChargebackPreventionNotification(merchantId, eventId, 
                    eventType, transactionId, transactionAmount, currency, chargebackReason, 
                    riskScore, ratio);
            
            log.info("Merchant notified - MerchantId: {}, EventType: {}", merchantId, eventType);
            
        } catch (Exception e) {
            log.error("Failed to notify merchant - MerchantId: {}, EventId: {}", merchantId, eventId, e);
        }
    }
    
    private void updatePreventionMetrics(UUID merchantId, String eventType, String chargebackReason,
                                        Integer riskScore, BigDecimal transactionAmount, ChargebackRatio ratio) {
        try {
            chargebackPreventionService.updatePreventionMetrics(merchantId, eventType, 
                    chargebackReason, riskScore, transactionAmount, ratio);
        } catch (Exception e) {
            log.error("Failed to update prevention metrics - MerchantId: {}", merchantId, e);
        }
    }
    
    private void auditChargebackPreventionEvent(UUID eventId, UUID merchantId, String eventType,
                                               UUID transactionId, String chargebackReason,
                                               Integer riskScore, BigDecimal transactionAmount,
                                               ChargebackRatio ratio, LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditMerchantEvent(
                    "CHARGEBACK_PREVENTION_EVENT",
                    merchantId.toString(),
                    String.format("Chargeback prevention event processed - Type: %s, Reason: %s, Ratio: %.2f%%", 
                            eventType, chargebackReason, ratio.getRatio().multiply(new BigDecimal("100"))),
                    Map.of(
                            "eventId", eventId.toString(),
                            "merchantId", merchantId.toString(),
                            "eventType", eventType,
                            "transactionId", transactionId != null ? transactionId.toString() : "N/A",
                            "chargebackReason", chargebackReason != null ? chargebackReason : "N/A",
                            "riskScore", riskScore,
                            "transactionAmount", transactionAmount != null ? transactionAmount.toString() : "N/A",
                            "chargebackRatio", ratio.getRatio().multiply(new BigDecimal("100")).toString() + "%",
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit chargeback prevention event - EventId: {}", eventId, e);
        }
    }
    
    private void handlePreventionFailure(UUID eventId, UUID merchantId, String eventType, Exception error) {
        try {
            chargebackPreventionService.handlePreventionFailure(eventId, merchantId, eventType, error.getMessage());
            
            auditService.auditMerchantEvent(
                    "CHARGEBACK_PREVENTION_FAILED",
                    merchantId.toString(),
                    "Failed to process chargeback prevention event: " + error.getMessage(),
                    Map.of(
                            "eventId", eventId.toString(),
                            "merchantId", merchantId.toString(),
                            "eventType", eventType != null ? eventType : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle prevention failure - EventId: {}, MerchantId: {}", eventId, merchantId, e);
        }
    }
    
    @KafkaListener(
        topics = "chargeback-prevention-events.DLQ",
        groupId = "merchant-service-chargeback-prevention-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: Chargeback prevention event sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID eventId = event.containsKey("eventId") ? UUID.fromString((String) event.get("eventId")) : null;
            UUID merchantId = event.containsKey("merchantId") ? UUID.fromString((String) event.get("merchantId")) : null;
            String eventType = (String) event.get("eventType");
            
            log.error("DLQ: Chargeback prevention failed permanently - EventId: {}, MerchantId: {}, Type: {} - MANUAL REVIEW REQUIRED", 
                    eventId, merchantId, eventType);
            
            if (eventId != null && merchantId != null) {
                chargebackPreventionService.markForManualReview(eventId, merchantId, eventType, 
                        "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse chargeback prevention DLQ event: {}", eventJson, e);
        }
    }
    
    @lombok.Data
    @lombok.Builder
    private static class ChargebackRatio {
        private BigDecimal ratio;
        private Integer totalChargebacks;
        private Integer totalTransactions;
        private BigDecimal totalChargebackAmount;
        private BigDecimal totalTransactionVolume;
    }
}