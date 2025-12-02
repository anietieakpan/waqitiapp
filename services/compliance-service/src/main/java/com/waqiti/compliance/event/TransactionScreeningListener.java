package com.waqiti.compliance.event;

import com.waqiti.common.validation.NullSafetyUtils;
import com.waqiti.compliance.model.AMLTransaction;
import com.waqiti.compliance.service.DroolsAMLRuleEngine;
import com.waqiti.compliance.service.DroolsAMLRuleEngine.ComplianceScreeningResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka listener for real-time transaction screening
 * 
 * Automatically screens all transactions published to the transaction events topic
 * and takes appropriate actions based on compliance rules
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TransactionScreeningListener {
    
    private final DroolsAMLRuleEngine ruleEngine;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    /**
     * Listen for transaction events and screen them for compliance
     */
    @KafkaListener(
        topics = "${kafka.topics.transaction-events:transaction-events}",
        groupId = "compliance-screening-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleTransactionEvent(
            @Payload Map<String, Object> transactionEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String transactionId = (String) transactionEvent.get("transactionId");
        log.debug("Received transaction event for screening: {}", transactionId);
        
        try {
            // Convert event to AMLTransaction
            AMLTransaction transaction = mapEventToTransaction(transactionEvent);
            
            // Screen the transaction
            ComplianceScreeningResult result = ruleEngine.screenTransaction(transaction);
            
            log.info("Transaction {} screening complete. Risk Level: {}, Should Block: {}", 
                    transactionId, result.getRiskLevel(), result.shouldBlock());
            
            // Take action based on screening result
            handleScreeningResult(transaction, result);
            
            // Acknowledge message processing
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
            
        } catch (Exception e) {
            log.error("Error screening transaction {}: {}", transactionId, e.getMessage(), e);
            
            // Send to error topic for manual review
            sendToErrorQueue(transactionEvent, e);
            
            // Still acknowledge to prevent infinite retries
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        }
    }
    
    /**
     * Listen for high-value transactions requiring immediate screening
     */
    @KafkaListener(
        topics = "${kafka.topics.high-value-transactions:high-value-transactions}",
        groupId = "compliance-priority-screening-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleHighValueTransaction(
            @Payload Map<String, Object> transactionEvent,
            Acknowledgment acknowledgment) {
        
        String transactionId = (String) transactionEvent.get("transactionId");
        log.info("Received HIGH VALUE transaction for priority screening: {}", transactionId);
        
        try {
            AMLTransaction transaction = mapEventToTransaction(transactionEvent);
            
            // Priority screening for high-value transactions
            ComplianceScreeningResult result = ruleEngine.screenTransaction(transaction);
            
            // High-value transactions always require review
            if (!result.requiresReview()) {
                result.setRequiresReview(true);
                result.getAlerts().add(new AMLTransaction.ComplianceAlert(
                    "HIGH_VALUE", "HIGH", "High-value transaction requires manual review", LocalDateTime.now()
                ));
            }
            
            handleScreeningResult(transaction, result);
            
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
            
        } catch (Exception e) {
            log.error("Error screening high-value transaction {}: {}", transactionId, e.getMessage(), e);
            sendToErrorQueue(transactionEvent, e);
            
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        }
    }
    
    /**
     * Map Kafka event to AMLTransaction model
     */
    private AMLTransaction mapEventToTransaction(Map<String, Object> event) {
        return AMLTransaction.builder()
            .transactionId((String) event.get("transactionId"))
            .accountId((String) event.get("accountId"))
            .customerId((String) event.get("customerId"))
            .counterpartyId((String) event.get("counterpartyId"))
            .counterpartyAccountId((String) event.get("counterpartyAccountId"))
            // SAFETY FIX: Safe parsing with null check to prevent NumberFormatException
            .amount(NullSafetyUtils.safeParseBigDecimal(
                event.get("amount") != null ? event.get("amount").toString() : null,
                BigDecimal.ZERO
            ))
            .currency((String) event.get("currency"))
            .type((String) event.get("type"))
            .channel((String) event.get("channel"))
            .timestamp(LocalDateTime.parse(event.get("timestamp").toString()))
            .originCountry((String) event.get("originCountry"))
            .destinationCountry((String) event.get("destinationCountry"))
            .ipAddress((String) event.get("ipAddress"))
            .deviceId((String) event.get("deviceId"))
            .customerType((String) event.get("customerType"))
            .customerRiskRating((String) event.get("customerRiskRating"))
            .accountAgeInDays((Integer) event.get("accountAgeInDays"))
            .isPEP((Boolean) event.get("isPEP"))
            .isHighRiskJurisdiction((Boolean) event.get("isHighRiskJurisdiction"))
            .occupation((String) event.get("occupation"))
            // SAFETY FIX: Safe parsing with Optional for nullable field
            .declaredIncome(NullSafetyUtils.safeParseBigDecimal(
                event.get("declaredIncome") != null ? event.get("declaredIncome").toString() : null
            ).orElse(null))
            .counterpartyType((String) event.get("counterpartyType"))
            .counterpartyCountry((String) event.get("counterpartyCountry"))
            .counterpartyIsHighRisk((Boolean) event.get("counterpartyIsHighRisk"))
            .description((String) event.get("description"))
            .reference((String) event.get("reference"))
            .purposeCode((String) event.get("purposeCode"))
            .isInternational((Boolean) event.get("isInternational"))
            .isCashTransaction((Boolean) event.get("isCashTransaction"))
            .dailyTransactionCount((Integer) event.get("dailyTransactionCount"))
            // SAFETY FIX: Safe parsing with Optional for nullable field
            .dailyTransactionVolume(NullSafetyUtils.safeParseBigDecimal(
                event.get("dailyTransactionVolume") != null ? event.get("dailyTransactionVolume").toString() : null
            ).orElse(null))
            .monthlyTransactionCount((Integer) event.get("monthlyTransactionCount"))
            // SAFETY FIX: Safe parsing with Optional for nullable field
            .monthlyTransactionVolume(NullSafetyUtils.safeParseBigDecimal(
                event.get("monthlyTransactionVolume") != null ? event.get("monthlyTransactionVolume").toString() : null
            ).orElse(null))
            // SAFETY FIX: Safe parsing with Optional for nullable field
            .averageTransactionAmount(NullSafetyUtils.safeParseBigDecimal(
                event.get("averageTransactionAmount") != null ? event.get("averageTransactionAmount").toString() : null
            ).orElse(null))
            // SAFETY FIX: Safe parsing with Optional for nullable field
            .largestPreviousTransaction(NullSafetyUtils.safeParseBigDecimal(
                event.get("largestPreviousTransaction") != null ? event.get("largestPreviousTransaction").toString() : null
            ).orElse(null))
            .build();
    }
    
    /**
     * Handle screening result and take appropriate actions
     */
    private void handleScreeningResult(AMLTransaction transaction, ComplianceScreeningResult result) {
        // If transaction should be blocked
        if (result.shouldBlock()) {
            blockTransaction(transaction, result);
        }
        
        // If transaction requires review
        if (result.requiresReview()) {
            sendForManualReview(transaction, result);
        }
        
        // If SAR is required
        if (result.requiresSAR()) {
            initiateSARFiling(transaction, result);
        }
        
        // Always send screening result to audit trail
        auditScreeningResult(transaction, result);
        
        // Publish screening completed event
        publishScreeningCompletedEvent(transaction, result);
    }
    
    /**
     * Block a transaction
     */
    private void blockTransaction(AMLTransaction transaction, ComplianceScreeningResult result) {
        log.warn("BLOCKING transaction {} due to compliance risk: {}", 
                transaction.getTransactionId(), result.getRiskLevel());
        
        Map<String, Object> blockEvent = new HashMap<>();
        blockEvent.put("eventId", UUID.randomUUID().toString());
        blockEvent.put("transactionId", transaction.getTransactionId());
        blockEvent.put("accountId", transaction.getAccountId());
        blockEvent.put("customerId", transaction.getCustomerId());
        blockEvent.put("amount", transaction.getAmount());
        blockEvent.put("currency", transaction.getCurrency());
        blockEvent.put("blockReason", "AML_COMPLIANCE");
        blockEvent.put("riskScore", result.getRiskScore());
        blockEvent.put("riskLevel", result.getRiskLevel());
        blockEvent.put("alerts", result.getAlerts());
        blockEvent.put("timestamp", LocalDateTime.now());
        
        kafkaTemplate.send("transaction-blocks", blockEvent);
    }
    
    /**
     * Send transaction for manual review
     */
    private void sendForManualReview(AMLTransaction transaction, ComplianceScreeningResult result) {
        log.info("Sending transaction {} for manual review", transaction.getTransactionId());
        
        Map<String, Object> reviewEvent = new HashMap<>();
        reviewEvent.put("reviewId", UUID.randomUUID().toString());
        reviewEvent.put("transactionId", transaction.getTransactionId());
        reviewEvent.put("customerId", transaction.getCustomerId());
        reviewEvent.put("amount", transaction.getAmount());
        reviewEvent.put("currency", transaction.getCurrency());
        reviewEvent.put("riskScore", result.getRiskScore());
        reviewEvent.put("riskLevel", result.getRiskLevel());
        reviewEvent.put("riskIndicators", result.getRiskIndicators());
        reviewEvent.put("alerts", result.getAlerts());
        reviewEvent.put("priority", calculateReviewPriority(result));
        reviewEvent.put("timestamp", LocalDateTime.now());
        
        kafkaTemplate.send("compliance-review-queue", reviewEvent);
    }
    
    /**
     * Initiate SAR filing
     */
    private void initiateSARFiling(AMLTransaction transaction, ComplianceScreeningResult result) {
        log.warn("Initiating SAR filing for transaction {}", transaction.getTransactionId());
        
        Map<String, Object> sarEvent = new HashMap<>();
        sarEvent.put("sarId", UUID.randomUUID().toString());
        sarEvent.put("transactionId", transaction.getTransactionId());
        sarEvent.put("accountId", transaction.getAccountId());
        sarEvent.put("customerId", transaction.getCustomerId());
        sarEvent.put("amount", transaction.getAmount());
        sarEvent.put("currency", transaction.getCurrency());
        sarEvent.put("transactionDate", transaction.getTimestamp());
        sarEvent.put("suspiciousActivity", result.getRiskReasons());
        sarEvent.put("riskScore", result.getRiskScore());
        sarEvent.put("riskIndicators", result.getRiskIndicators());
        sarEvent.put("filingInitiated", LocalDateTime.now());
        sarEvent.put("status", "PENDING_FILING");
        
        kafkaTemplate.send("sar-filing-queue", sarEvent);
    }
    
    /**
     * Audit screening result
     */
    private void auditScreeningResult(AMLTransaction transaction, ComplianceScreeningResult result) {
        Map<String, Object> auditEvent = new HashMap<>();
        auditEvent.put("auditId", UUID.randomUUID().toString());
        auditEvent.put("transactionId", transaction.getTransactionId());
        auditEvent.put("customerId", transaction.getCustomerId());
        auditEvent.put("screeningResult", result);
        auditEvent.put("timestamp", LocalDateTime.now());
        
        kafkaTemplate.send("compliance-audit-trail", auditEvent);
    }
    
    /**
     * Publish screening completed event
     */
    private void publishScreeningCompletedEvent(AMLTransaction transaction, ComplianceScreeningResult result) {
        Map<String, Object> completedEvent = new HashMap<>();
        completedEvent.put("eventId", UUID.randomUUID().toString());
        completedEvent.put("transactionId", transaction.getTransactionId());
        completedEvent.put("riskLevel", result.getRiskLevel());
        completedEvent.put("decision", result.getRecommendation());
        completedEvent.put("blocked", result.shouldBlock());
        completedEvent.put("requiresReview", result.requiresReview());
        completedEvent.put("timestamp", LocalDateTime.now());
        
        kafkaTemplate.send("compliance-screening-completed", completedEvent);
    }
    
    /**
     * Send failed screening to error queue
     */
    private void sendToErrorQueue(Map<String, Object> transactionEvent, Exception error) {
        Map<String, Object> errorEvent = new HashMap<>();
        errorEvent.put("errorId", UUID.randomUUID().toString());
        errorEvent.put("originalEvent", transactionEvent);
        errorEvent.put("errorMessage", error.getMessage());
        errorEvent.put("errorType", error.getClass().getSimpleName());
        errorEvent.put("timestamp", LocalDateTime.now());
        
        kafkaTemplate.send("compliance-screening-errors", errorEvent);
    }
    
    /**
     * Calculate review priority based on risk factors
     */
    private String calculateReviewPriority(ComplianceScreeningResult result) {
        if (result.getRiskScore() >= 75 || "VERY_HIGH".equals(result.getRiskLevel())) {
            return "CRITICAL";
        } else if (result.getRiskScore() >= 50 || "HIGH".equals(result.getRiskLevel())) {
            return "HIGH";
        } else if (result.getRiskScore() >= 25 || "MEDIUM".equals(result.getRiskLevel())) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
}