package com.waqiti.compliance.events;

import com.waqiti.common.events.SarFilingEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SAR (Suspicious Activity Report) Filing Event Producer
 * 
 * CRITICAL IMPLEMENTATION: Publishes SAR filing events for AML compliance
 * Connects to SarFilingRequestEventConsumer
 * 
 * This producer is essential for:
 * - AML (Anti-Money Laundering) compliance
 * - FinCEN SAR reporting requirements
 * - Regulatory compliance and auditing
 * - Suspicious transaction reporting
 * - Law enforcement cooperation
 * 
 * REGULATORY IMPACT: Required by BSA/AML regulations
 * LEGAL IMPACT: Failure to file SARs = $25k-$100k+ fines per violation
 * 
 * SAR Triggers:
 * - Transactions over $5,000 with known/suspected criminal activity
 * - Structuring (attempts to evade BSA reporting)
 * - Money laundering red flags
 * - Terrorist financing indicators
 * - Identity theft patterns
 * - Elder financial abuse
 * 
 * Filing Deadlines:
 * - 30 calendar days from initial detection
 * - If identifier needed: 60 calendar days
 * - No continuing activity required after initial SAR
 * 
 * @author Waqiti Compliance Team
 * @version 2.0 - Production Implementation
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SarFilingEventProducer {

    private final KafkaTemplate<String, SarFilingEvent> kafkaTemplate;
    
    private static final String TOPIC = "sar-filing-requests";
    private static final String URGENT_TOPIC = "sar-filing-requests-urgent";
    
    private static final BigDecimal SAR_THRESHOLD = new BigDecimal("5000.00");

    /**
     * Publish SAR filing request for suspicious activity
     */
    public CompletableFuture<SendResult<String, SarFilingEvent>> publishSarFilingRequest(
            String userId,
            String transactionId,
            String activityType,
            BigDecimal amount,
            String currency,
            String suspicionNarrative,
            List<String> suspiciousIndicators,
            LocalDate activityStartDate,
            LocalDate activityEndDate,
            String filingReason,
            Map<String, Object> supportingDocuments,
            String correlationId) {
        
        log.warn("Publishing SAR filing request: userId={}, transaction={}, amount={}, type={}",
            userId, transactionId, amount, activityType);
        
        String sarId = generateSarId();
        LocalDate filingDeadline = calculateFilingDeadline(activityStartDate);
        
        SarFilingEvent event = SarFilingEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .sarId(sarId)
            .userId(userId)
            .transactionId(transactionId)
            .filingStatus("PENDING")
            .activityType(activityType)
            .amount(amount)
            .currency(currency)
            .suspicionNarrative(suspicionNarrative)
            .suspiciousIndicators(suspiciousIndicators)
            .activityStartDate(activityStartDate)
            .activityEndDate(activityEndDate)
            .filingReason(filingReason)
            .filingDeadline(filingDeadline)
            .priority(determinePriority(amount, activityType, suspiciousIndicators))
            .requiresImmediateReview(isUrgent(activityType, suspiciousIndicators))
            .supportingDocuments(supportingDocuments)
            .filingInstitution("WAQITI_FINANCIAL_SERVICES")
            .complianceOfficer("COMPLIANCE_TEAM")
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        String topic = event.isRequiresImmediateReview() ? URGENT_TOPIC : TOPIC;
        return sendEvent(event, topic);
    }

    /**
     * Publish SAR for money laundering suspicion
     */
    public CompletableFuture<SendResult<String, SarFilingEvent>> publishMoneyLaunderingSar(
            String userId,
            List<String> transactionIds,
            BigDecimal totalAmount,
            String launderingScheme,
            List<String> mlIndicators,
            Map<String, Object> transactionPattern,
            String correlationId) {
        
        log.error("Publishing money laundering SAR: userId={}, transactions={}, amount={}",
            userId, transactionIds.size(), totalAmount);
        
        String sarId = generateSarId();
        LocalDate activityDate = LocalDate.now();
        
        String narrative = String.format(
            "Suspected money laundering activity detected for user %s. " +
            "Pattern: %s. Total amount: %s USD across %d transactions. " +
            "Indicators: %s. Transaction analysis shows: %s",
            userId, launderingScheme, totalAmount, transactionIds.size(),
            String.join(", ", mlIndicators),
            transactionPattern.toString()
        );
        
        SarFilingEvent event = SarFilingEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .sarId(sarId)
            .userId(userId)
            .transactionId(transactionIds.get(0))
            .relatedTransactions(transactionIds)
            .filingStatus("PENDING")
            .activityType("MONEY_LAUNDERING")
            .amount(totalAmount)
            .currency("USD")
            .suspicionNarrative(narrative)
            .suspiciousIndicators(mlIndicators)
            .activityStartDate(activityDate.minusDays(30))
            .activityEndDate(activityDate)
            .filingReason("Suspicious money laundering pattern detected")
            .filingDeadline(activityDate.plusDays(30))
            .priority("CRITICAL")
            .requiresImmediateReview(true)
            .supportingDocuments(transactionPattern)
            .filingInstitution("WAQITI_FINANCIAL_SERVICES")
            .complianceOfficer("COMPLIANCE_TEAM")
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event, URGENT_TOPIC);
    }

    /**
     * Publish SAR for structuring (smurfing)
     */
    public CompletableFuture<SendResult<String, SarFilingEvent>> publishStructuringSar(
            String userId,
            List<String> transactionIds,
            BigDecimal totalAmount,
            Integer transactionCount,
            String structuringPattern,
            Map<String, Object> patternDetails,
            String correlationId) {
        
        log.warn("Publishing structuring SAR: userId={}, transactions={}, total={}",
            userId, transactionCount, totalAmount);
        
        String sarId = generateSarId();
        LocalDate activityDate = LocalDate.now();
        
        String narrative = String.format(
            "Suspected structuring activity detected for user %s. " +
            "%d transactions totaling %s USD, all below reporting thresholds. " +
            "Pattern: %s. This appears to be an attempt to evade CTR reporting requirements.",
            userId, transactionCount, totalAmount, structuringPattern
        );
        
        SarFilingEvent event = SarFilingEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .sarId(sarId)
            .userId(userId)
            .transactionId(transactionIds.get(0))
            .relatedTransactions(transactionIds)
            .filingStatus("PENDING")
            .activityType("STRUCTURING")
            .amount(totalAmount)
            .currency("USD")
            .suspicionNarrative(narrative)
            .suspiciousIndicators(List.of("STRUCTURING", "CTR_EVASION", "SMURFING"))
            .activityStartDate(activityDate.minusDays(7))
            .activityEndDate(activityDate)
            .filingReason("Suspected structuring to evade reporting requirements")
            .filingDeadline(activityDate.plusDays(30))
            .priority("HIGH")
            .requiresImmediateReview(true)
            .supportingDocuments(patternDetails)
            .filingInstitution("WAQITI_FINANCIAL_SERVICES")
            .complianceOfficer("COMPLIANCE_TEAM")
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event, URGENT_TOPIC);
    }

    /**
     * Publish SAR for terrorist financing
     */
    public CompletableFuture<SendResult<String, SarFilingEvent>> publishTerroristFinancingSar(
            String userId,
            String transactionId,
            BigDecimal amount,
            List<String> tfIndicators,
            String sanctionMatch,
            Map<String, Object> evidenceDetails,
            String correlationId) {
        
        log.error("CRITICAL: Publishing terrorist financing SAR: userId={}, transaction={}, amount={}",
            userId, transactionId, amount);
        
        String sarId = generateSarId();
        LocalDate activityDate = LocalDate.now();
        
        String narrative = String.format(
            "TERRORIST FINANCING ALERT: Suspected terrorist financing activity for user %s. " +
            "Transaction %s for %s USD. Sanctions match: %s. Indicators: %s. " +
            "IMMEDIATE LAW ENFORCEMENT NOTIFICATION REQUIRED.",
            userId, transactionId, amount, sanctionMatch, String.join(", ", tfIndicators)
        );
        
        SarFilingEvent event = SarFilingEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .sarId(sarId)
            .userId(userId)
            .transactionId(transactionId)
            .filingStatus("PENDING")
            .activityType("TERRORIST_FINANCING")
            .amount(amount)
            .currency("USD")
            .suspicionNarrative(narrative)
            .suspiciousIndicators(tfIndicators)
            .activityStartDate(activityDate)
            .activityEndDate(activityDate)
            .filingReason("Suspected terrorist financing - sanctions match detected")
            .filingDeadline(activityDate.plusDays(1))
            .priority("CRITICAL")
            .requiresImmediateReview(true)
            .requiresLawEnforcementNotification(true)
            .supportingDocuments(evidenceDetails)
            .filingInstitution("WAQITI_FINANCIAL_SERVICES")
            .complianceOfficer("COMPLIANCE_TEAM")
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event, URGENT_TOPIC);
    }

    /**
     * Publish SAR for identity theft
     */
    public CompletableFuture<SendResult<String, SarFilingEvent>> publishIdentityTheftSar(
            String victimUserId,
            String suspectedThiefUserId,
            String transactionId,
            BigDecimal amount,
            List<String> theftIndicators,
            Map<String, Object> evidenceDetails,
            String correlationId) {
        
        log.warn("Publishing identity theft SAR: victim={}, suspect={}, transaction={}",
            victimUserId, suspectedThiefUserId, transactionId);
        
        String sarId = generateSarId();
        LocalDate activityDate = LocalDate.now();
        
        String narrative = String.format(
            "Suspected identity theft activity. Victim: %s. Suspected thief: %s. " +
            "Unauthorized transaction %s for %s USD. Indicators: %s.",
            victimUserId, suspectedThiefUserId, transactionId, amount, 
            String.join(", ", theftIndicators)
        );
        
        SarFilingEvent event = SarFilingEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .sarId(sarId)
            .userId(victimUserId)
            .suspectedCriminal(suspectedThiefUserId)
            .transactionId(transactionId)
            .filingStatus("PENDING")
            .activityType("IDENTITY_THEFT")
            .amount(amount)
            .currency("USD")
            .suspicionNarrative(narrative)
            .suspiciousIndicators(theftIndicators)
            .activityStartDate(activityDate)
            .activityEndDate(activityDate)
            .filingReason("Suspected identity theft - unauthorized account access")
            .filingDeadline(activityDate.plusDays(30))
            .priority("HIGH")
            .requiresImmediateReview(true)
            .supportingDocuments(evidenceDetails)
            .filingInstitution("WAQITI_FINANCIAL_SERVICES")
            .complianceOfficer("COMPLIANCE_TEAM")
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event, URGENT_TOPIC);
    }

    /**
     * Publish SAR for elder financial abuse
     */
    public CompletableFuture<SendResult<String, SarFilingEvent>> publishElderAbuseSar(
            String elderUserId,
            String suspectedAbuserId,
            List<String> transactionIds,
            BigDecimal totalAmount,
            List<String> abuseIndicators,
            Map<String, Object> patternDetails,
            String correlationId) {
        
        log.warn("Publishing elder abuse SAR: elder={}, suspect={}, amount={}",
            elderUserId, suspectedAbuserId, totalAmount);
        
        String sarId = generateSarId();
        LocalDate activityDate = LocalDate.now();
        
        String narrative = String.format(
            "Suspected elder financial abuse. Elder: %s. Suspected abuser: %s. " +
            "%d suspicious transactions totaling %s USD. Indicators: %s.",
            elderUserId, suspectedAbuserId, transactionIds.size(), totalAmount,
            String.join(", ", abuseIndicators)
        );
        
        SarFilingEvent event = SarFilingEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .sarId(sarId)
            .userId(elderUserId)
            .suspectedCriminal(suspectedAbuserId)
            .transactionId(transactionIds.get(0))
            .relatedTransactions(transactionIds)
            .filingStatus("PENDING")
            .activityType("ELDER_FINANCIAL_ABUSE")
            .amount(totalAmount)
            .currency("USD")
            .suspicionNarrative(narrative)
            .suspiciousIndicators(abuseIndicators)
            .activityStartDate(activityDate.minusDays(30))
            .activityEndDate(activityDate)
            .filingReason("Suspected elder financial exploitation")
            .filingDeadline(activityDate.plusDays(30))
            .priority("HIGH")
            .requiresImmediateReview(true)
            .supportingDocuments(patternDetails)
            .filingInstitution("WAQITI_FINANCIAL_SERVICES")
            .complianceOfficer("COMPLIANCE_TEAM")
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event, URGENT_TOPIC);
    }

    /**
     * Generate unique SAR ID
     */
    private String generateSarId() {
        return String.format("SAR-%d-%s", 
            System.currentTimeMillis(),
            UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    /**
     * Calculate SAR filing deadline (30 days from detection)
     */
    private LocalDate calculateFilingDeadline(LocalDate activityDate) {
        return LocalDate.now().plusDays(30);
    }

    /**
     * Determine SAR priority
     */
    private String determinePriority(BigDecimal amount, String activityType, List<String> indicators) {
        if ("TERRORIST_FINANCING".equals(activityType)) return "CRITICAL";
        if ("MONEY_LAUNDERING".equals(activityType)) return "CRITICAL";
        if (amount.compareTo(new BigDecimal("100000")) > 0) return "HIGH";
        if (indicators.contains("SANCTIONS_MATCH")) return "CRITICAL";
        return "MEDIUM";
    }

    /**
     * Determine if SAR requires immediate review
     */
    private boolean isUrgent(String activityType, List<String> indicators) {
        if ("TERRORIST_FINANCING".equals(activityType)) return true;
        if ("MONEY_LAUNDERING".equals(activityType)) return true;
        if (indicators.contains("SANCTIONS_MATCH")) return true;
        if (indicators.contains("HIGH_RISK_COUNTRY")) return true;
        return false;
    }

    /**
     * Send event to Kafka with error handling
     */
    private CompletableFuture<SendResult<String, SarFilingEvent>> sendEvent(SarFilingEvent event, String topic) {
        try {
            CompletableFuture<SendResult<String, SarFilingEvent>> future = 
                kafkaTemplate.send(topic, event.getSarId(), event);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("SAR filing event published: sarId={}, userId={}, type={}, priority={}",
                        event.getSarId(), event.getUserId(), event.getActivityType(), event.getPriority());
                } else {
                    log.error("CRITICAL: Failed to publish SAR filing event: sarId={}, error={}",
                        event.getSarId(), ex.getMessage(), ex);
                }
            });
            
            return future;
            
        } catch (Exception e) {
            log.error("CRITICAL: Error sending SAR filing event: sarId={}, error={}", 
                event.getSarId(), e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Get event statistics
     */
    public Map<String, Object> getEventStatistics() {
        return Map.of(
            "topic", TOPIC,
            "urgentTopic", URGENT_TOPIC,
            "sarThreshold", SAR_THRESHOLD,
            "activityTypes", List.of(
                "MONEY_LAUNDERING",
                "STRUCTURING",
                "TERRORIST_FINANCING",
                "IDENTITY_THEFT",
                "ELDER_FINANCIAL_ABUSE",
                "FRAUD",
                "CYBERCRIMES"
            ),
            "producerActive", true
        );
    }
}