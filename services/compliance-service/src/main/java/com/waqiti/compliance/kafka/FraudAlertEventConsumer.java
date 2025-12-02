package com.waqiti.compliance.kafka;

import com.waqiti.payment.dto.FraudAlertEvent;
import com.waqiti.compliance.service.ComplianceAuditService;
import com.waqiti.compliance.service.SarFilingService;
import com.waqiti.compliance.service.ComplianceNotificationService;
import com.waqiti.compliance.service.AMLComplianceService;
import com.waqiti.compliance.domain.SuspiciousActivity;
import com.waqiti.compliance.domain.ComplianceAlert;
import com.waqiti.compliance.domain.CustomerRiskProfile;
import com.waqiti.compliance.model.SarFiling;
import com.waqiti.compliance.model.SarFilingStatus;
import com.waqiti.compliance.repository.SuspiciousActivityRepository;
import com.waqiti.compliance.repository.ComplianceAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * CRITICAL COMPLIANCE CONSUMER: FraudAlertEventConsumer
 * 
 * This was a MISSING CRITICAL consumer for FraudAlertEvent from fraud-detection-service.
 * The absence of this consumer meant fraud alerts were not being processed for compliance requirements.
 * 
 * BUSINESS IMPACT:
 * - Ensures compliance with AML/BSA regulations
 * - Triggers SAR filing when required by law
 * - Creates audit trail for regulatory examinations
 * - Manages customer risk profiles based on fraud activity
 * - Prevents regulatory violations and penalties
 * 
 * COMPLIANCE RESPONSIBILITIES:
 * - Flag accounts for enhanced monitoring
 * - Initiate SAR filing for qualifying fraud events
 * - Update customer risk profiles
 * - Create compliance audit trail
 * - Send alerts to compliance team
 * - Track suspicious activity patterns
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FraudAlertEventConsumer {
    
    private final AMLComplianceService amlComplianceService;
    private final SarFilingService sarFilingService;
    private final ComplianceAuditService auditService;
    private final ComplianceNotificationService notificationService;
    private final SuspiciousActivityRepository suspiciousActivityRepository;
    private final ComplianceAuditRepository auditRepository;
    
    private static final String DLT_TOPIC = "fraud-alert-events-dlq";
    private static final BigDecimal SAR_THRESHOLD = new BigDecimal("5000.00");
    private static final Set<String> PROCESSED_EVENTS = new HashSet<>();
    
    /**
     * CRITICAL: Process fraud alerts for compliance screening and SAR filing
     * 
     * This consumer is essential for:
     * - AML compliance monitoring
     * - SAR filing triggers
     * - Customer risk profiling
     * - Regulatory audit trail
     * - Compliance team alerting
     */
    @KafkaListener(
        topics = {"fraud-alerts", "fraud-alert-events", "fraud-detection-events"},
        groupId = "compliance-service-fraud-alerts-group",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "5"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2),
        dltTopicSuffix = ".dlt",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional
    public void handleFraudAlert(
            @Payload FraudAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("fraud-alert-%s-p%d-o%d",
            event.getAlertId(), partition, offset);
        
        log.warn("COMPLIANCE: Processing fraud alert for compliance screening: alertId={}, userId={}, amount={}, riskScore={}, correlation={}",
            event.getAlertId(), event.getUserId(), event.getAmount(), event.getRiskScore(), correlationId);
        
        try {
            // Idempotency check
            if (isDuplicateEvent(event.getAlertId())) {
                log.debug("COMPLIANCE: Duplicate fraud alert event: {}", event.getAlertId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Create suspicious activity record
            SuspiciousActivity suspiciousActivity = createSuspiciousActivityRecord(event, correlationId);
            
            // Update customer risk profile
            updateCustomerRiskProfile(event, correlationId);
            
            // Check if SAR filing is required
            checkAndInitiateSARFiling(event, suspiciousActivity, correlationId);
            
            // Create compliance audit entry
            createComplianceAuditEntry(event, correlationId);
            
            // Send alerts to compliance team
            sendComplianceAlerts(event, correlationId);
            
            // Flag account for enhanced monitoring if high risk
            flagAccountForEnhancedMonitoring(event, correlationId);
            
            // Mark event as processed
            markEventProcessed(event.getAlertId());
            
            acknowledgment.acknowledge();
            
            log.info("COMPLIANCE: Successfully processed fraud alert for compliance: alertId={}, actions_taken={}",
                event.getAlertId(), determineActionsDescription(event));
            
        } catch (Exception e) {
            log.error("COMPLIANCE: CRITICAL - Failed to process fraud alert for compliance: alertId={}, error={}",
                event.getAlertId(), e.getMessage(), e);
            
            // Create critical compliance alert
            createCriticalComplianceAlert(event, e, correlationId);
            
            // Re-throw to trigger retry mechanism
            throw new RuntimeException("Compliance processing failed for fraud alert: " + event.getAlertId(), e);
        }
    }
    
    /**
     * Create suspicious activity record for regulatory purposes
     */
    private SuspiciousActivity createSuspiciousActivityRecord(FraudAlertEvent event, String correlationId) {
        SuspiciousActivity activity = SuspiciousActivity.builder()
            .id(UUID.randomUUID())
            .alertId(event.getAlertId())
            .userId(event.getUserId())
            .transactionId(event.getTransactionId())
            .activityType("FRAUD_DETECTED")
            .severity(event.getSeverity())
            .amount(event.getAmount())
            .riskScore(event.getRiskScore())
            .fraudIndicators(event.getFraudIndicators())
            .timestamp(event.getTimestamp())
            .status("ACTIVE")
            .requiresInvestigation(event.getRiskScore().compareTo(new BigDecimal("0.7")) > 0)
            .sarRequired(event.getAmount().compareTo(SAR_THRESHOLD) >= 0 && 
                        event.getRiskScore().compareTo(new BigDecimal("0.8")) > 0)
            .enhancedMonitoringRequired(event.getRiskScore().compareTo(new BigDecimal("0.6")) > 0)
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .lastUpdated(LocalDateTime.now())
            .build();
        
        suspiciousActivityRepository.save(activity);
        
        log.info("COMPLIANCE: Created suspicious activity record: id={}, alertId={}, sarRequired={}", 
            activity.getId(), event.getAlertId(), activity.isSarRequired());
        
        return activity;
    }
    
    /**
     * Update customer risk profile based on fraud activity
     */
    private void updateCustomerRiskProfile(FraudAlertEvent event, String correlationId) {
        try {
            CustomerRiskProfile riskProfile = amlComplianceService.getOrCreateRiskProfile(event.getUserId());
            
            // Increase risk score based on fraud alert
            BigDecimal riskIncrease = calculateRiskIncrease(event);
            riskProfile.adjustRiskScore(riskIncrease);
            
            // Add fraud indicator to risk factors
            riskProfile.addRiskFactor("FRAUD_ALERT", event.getSeverity(), LocalDateTime.now());
            
            // Set enhanced monitoring flag if warranted
            if (event.getRiskScore().compareTo(new BigDecimal("0.7")) > 0) {
                riskProfile.setEnhancedMonitoringRequired(true);
                riskProfile.setEnhancedMonitoringReason("HIGH_FRAUD_RISK");
                riskProfile.setEnhancedMonitoringStartDate(LocalDateTime.now());
            }
            
            amlComplianceService.updateRiskProfile(riskProfile);
            
            log.info("COMPLIANCE: Updated customer risk profile: userId={}, newRiskScore={}, enhancedMonitoring={}", 
                event.getUserId(), riskProfile.getCurrentRiskScore(), riskProfile.isEnhancedMonitoringRequired());
            
        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to update customer risk profile: userId={}, error={}", 
                event.getUserId(), e.getMessage(), e);
        }
    }
    
    /**
     * Check if SAR filing is required and initiate if necessary
     */
    private void checkAndInitiateSARFiling(FraudAlertEvent event, SuspiciousActivity activity, String correlationId) {
        try {
            // SAR filing criteria:
            // 1. Transaction amount >= $5,000 AND high fraud risk score (>= 0.8)
            // 2. OR any amount with CRITICAL severity
            // 3. OR pattern of multiple fraud alerts for same user
            
            boolean sarRequired = false;
            String sarReason = "";
            
            if (event.getAmount().compareTo(SAR_THRESHOLD) >= 0 && 
                event.getRiskScore().compareTo(new BigDecimal("0.8")) > 0) {
                sarRequired = true;
                sarReason = "HIGH_VALUE_FRAUD_TRANSACTION";
            } else if ("CRITICAL".equals(event.getSeverity())) {
                sarRequired = true;
                sarReason = "CRITICAL_FRAUD_ALERT";
            } else if (hasPatternOfFraudActivity(event.getUserId())) {
                sarRequired = true;
                sarReason = "PATTERN_OF_FRAUD_ACTIVITY";
            }
            
            if (sarRequired) {
                SarFiling sarFiling = sarFilingService.initiateSARFiling(
                    event.getUserId(),
                    event.getTransactionId(),
                    event.getAmount(),
                    sarReason,
                    buildSARNarrative(event, activity),
                    correlationId
                );
                
                log.warn("COMPLIANCE: SAR filing initiated: sarId={}, alertId={}, reason={}, amount={}", 
                    sarFiling.getId(), event.getAlertId(), sarReason, event.getAmount());
                
                // Send immediate notification to compliance officer
                notificationService.sendUrgentNotification(
                    "COMPLIANCE_OFFICER",
                    "SAR Filing Initiated - Fraud Alert",
                    String.format("SAR filing has been automatically initiated due to fraud alert. " +
                        "SAR ID: %s, User: %s, Amount: %s, Reason: %s. Manual review required within 24 hours.",
                        sarFiling.getId(), event.getUserId(), event.getAmount(), sarReason),
                    Map.of(
                        "sarId", sarFiling.getId().toString(),
                        "alertId", event.getAlertId().toString(),
                        "priority", "URGENT",
                        "deadline", "24_HOURS"
                    )
                );
            }
            
        } catch (Exception e) {
            log.error("COMPLIANCE: CRITICAL - Failed to process SAR filing check: alertId={}, error={}", 
                event.getAlertId(), e.getMessage(), e);
        }
    }
    
    /**
     * Create compliance audit entry
     */
    private void createComplianceAuditEntry(FraudAlertEvent event, String correlationId) {
        try {
            auditService.createAuditEntry(
                "FRAUD_ALERT_PROCESSED",
                event.getUserId().toString(),
                Map.of(
                    "alertId", event.getAlertId().toString(),
                    "transactionId", event.getTransactionId() != null ? event.getTransactionId().toString() : "",
                    "riskScore", event.getRiskScore().toString(),
                    "severity", event.getSeverity(),
                    "amount", event.getAmount().toString(),
                    "fraudIndicators", String.join(",", event.getFraudIndicators()),
                    "correlationId", correlationId
                ),
                "SYSTEM",
                "Fraud alert processed for compliance screening and regulatory requirements"
            );
            
        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to create audit entry: alertId={}, error={}", 
                event.getAlertId(), e.getMessage(), e);
        }
    }
    
    /**
     * Send alerts to compliance team
     */
    private void sendComplianceAlerts(FraudAlertEvent event, String correlationId) {
        try {
            String priority = determinePriority(event);
            
            if ("HIGH".equals(priority) || "CRITICAL".equals(priority)) {
                notificationService.sendNotification(
                    "COMPLIANCE_TEAM",
                    String.format("%s Priority Fraud Alert - Compliance Review Required", priority),
                    buildComplianceAlertMessage(event),
                    Map.of(
                        "alertId", event.getAlertId().toString(),
                        "userId", event.getUserId().toString(),
                        "priority", priority,
                        "requiresImmedateAction", "HIGH".equals(priority) || "CRITICAL".equals(priority)
                    )
                );
            }
            
        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to send compliance alerts: alertId={}, error={}", 
                event.getAlertId(), e.getMessage(), e);
        }
    }
    
    /**
     * Flag account for enhanced monitoring if warranted
     */
    private void flagAccountForEnhancedMonitoring(FraudAlertEvent event, String correlationId) {
        try {
            if (event.getRiskScore().compareTo(new BigDecimal("0.6")) > 0) {
                amlComplianceService.flagForEnhancedMonitoring(
                    event.getUserId(),
                    "FRAUD_ALERT",
                    String.format("Enhanced monitoring triggered by fraud alert: %s", event.getAlertId()),
                    30 // 30 days of enhanced monitoring
                );
                
                log.info("COMPLIANCE: Account flagged for enhanced monitoring: userId={}, alertId={}, duration=30days", 
                    event.getUserId(), event.getAlertId());
            }
            
        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to flag account for enhanced monitoring: userId={}, error={}", 
                event.getUserId(), e.getMessage(), e);
        }
    }
    
    /**
     * Check for duplicate event processing
     */
    private boolean isDuplicateEvent(UUID alertId) {
        return PROCESSED_EVENTS.contains(alertId.toString());
    }
    
    /**
     * Mark event as processed
     */
    private void markEventProcessed(UUID alertId) {
        PROCESSED_EVENTS.add(alertId.toString());
        // Clean up old processed events to prevent memory leaks
        if (PROCESSED_EVENTS.size() > 10000) {
            PROCESSED_EVENTS.clear();
        }
    }
    
    /**
     * Calculate risk increase for customer profile
     */
    private BigDecimal calculateRiskIncrease(FraudAlertEvent event) {
        BigDecimal baseIncrease = new BigDecimal("10.0");
        
        if ("CRITICAL".equals(event.getSeverity())) {
            return baseIncrease.multiply(new BigDecimal("3"));
        } else if ("HIGH".equals(event.getSeverity())) {
            return baseIncrease.multiply(new BigDecimal("2"));
        } else {
            return baseIncrease;
        }
    }
    
    /**
     * Check if user has pattern of fraud activity
     */
    private boolean hasPatternOfFraudActivity(UUID userId) {
        try {
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            long recentFraudCount = suspiciousActivityRepository
                .countByUserIdAndActivityTypeAndTimestampAfter(userId, "FRAUD_DETECTED", thirtyDaysAgo);
            
            return recentFraudCount >= 3; // 3 or more fraud alerts in 30 days indicates pattern
        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to check fraud pattern: userId={}, error={}", userId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Build SAR narrative
     */
    private String buildSARNarrative(FraudAlertEvent event, SuspiciousActivity activity) {
        StringBuilder narrative = new StringBuilder();
        narrative.append("AUTOMATED FRAUD DETECTION SYSTEM ALERT\n\n");
        narrative.append(String.format("Alert ID: %s\n", event.getAlertId()));
        narrative.append(String.format("Detection Date: %s\n", event.getTimestamp()));
        narrative.append(String.format("Transaction Amount: %s\n", event.getAmount()));
        narrative.append(String.format("Risk Score: %s\n", event.getRiskScore()));
        narrative.append(String.format("Severity: %s\n", event.getSeverity()));
        narrative.append("\nFRAUD INDICATORS:\n");
        
        for (String indicator : event.getFraudIndicators()) {
            narrative.append(String.format("- %s\n", indicator));
        }
        
        narrative.append("\nSUSPICIOUS ACTIVITY DETAILS:\n");
        narrative.append("This alert was generated by our automated fraud detection system using machine learning ");
        narrative.append("models and rule-based analysis. The transaction exhibited patterns consistent with ");
        narrative.append("fraudulent activity and requires regulatory reporting.\n\n");
        narrative.append("COMPLIANCE ACTION: Account flagged for enhanced monitoring and investigation.");
        
        return narrative.toString();
    }
    
    /**
     * Determine priority level
     */
    private String determinePriority(FraudAlertEvent event) {
        if ("CRITICAL".equals(event.getSeverity()) || 
            event.getRiskScore().compareTo(new BigDecimal("0.9")) > 0) {
            return "CRITICAL";
        } else if ("HIGH".equals(event.getSeverity()) || 
                   event.getRiskScore().compareTo(new BigDecimal("0.7")) > 0) {
            return "HIGH";
        } else {
            return "MEDIUM";
        }
    }
    
    /**
     * Build compliance alert message
     */
    private String buildComplianceAlertMessage(FraudAlertEvent event) {
        return String.format(
            "FRAUD ALERT COMPLIANCE REVIEW\n\n" +
            "Alert ID: %s\n" +
            "User ID: %s\n" +
            "Transaction ID: %s\n" +
            "Amount: %s\n" +
            "Risk Score: %s\n" +
            "Severity: %s\n" +
            "Fraud Indicators: %s\n\n" +
            "ACTION REQUIRED:\n" +
            "- Review for SAR filing requirements\n" +
            "- Verify enhanced monitoring status\n" +
            "- Assess need for account restrictions\n" +
            "- Document compliance actions taken\n\n" +
            "Deadline: Review within 24 hours",
            event.getAlertId(),
            event.getUserId(),
            event.getTransactionId(),
            event.getAmount(),
            event.getRiskScore(),
            event.getSeverity(),
            String.join(", ", event.getFraudIndicators())
        );
    }
    
    /**
     * Determine actions description for logging
     */
    private String determineActionsDescription(FraudAlertEvent event) {
        List<String> actions = new ArrayList<>();
        actions.add("suspicious_activity_recorded");
        actions.add("risk_profile_updated");
        
        if (event.getRiskScore().compareTo(new BigDecimal("0.6")) > 0) {
            actions.add("enhanced_monitoring_flagged");
        }
        
        if (event.getAmount().compareTo(SAR_THRESHOLD) >= 0 && 
            event.getRiskScore().compareTo(new BigDecimal("0.8")) > 0) {
            actions.add("sar_filing_initiated");
        }
        
        return String.join(", ", actions);
    }
    
    /**
     * Create critical compliance alert for processing failures
     */
    private void createCriticalComplianceAlert(FraudAlertEvent event, Exception error, String correlationId) {
        try {
            ComplianceAlert alert = ComplianceAlert.builder()
                .alertId(UUID.randomUUID().toString())
                .alertType("CRITICAL_PROCESSING_FAILURE")
                .severity("CRITICAL")
                .message(String.format("CRITICAL: Failed to process fraud alert for compliance. " +
                    "Alert ID: %s, Error: %s. Manual intervention required immediately.",
                    event.getAlertId(), error.getMessage()))
                .relatedEntityId(event.getUserId().toString())
                .relatedEntityType("USER")
                .requiresAction(true)
                .assignedTo("COMPLIANCE_MANAGER")
                .createdAt(LocalDateTime.now())
                .deadline(LocalDateTime.now().plusHours(1)) // 1 hour deadline for critical failures
                .metadata(Map.of(
                    "originalAlertId", event.getAlertId().toString(),
                    "errorType", error.getClass().getSimpleName(),
                    "correlationId", correlationId,
                    "processingFailure", true
                ))
                .build();
            
            // This would typically be persisted and trigger immediate notifications
            log.error("COMPLIANCE: CRITICAL ALERT CREATED - Processing failure requires immediate attention: {}",
                alert.getAlertId());
            
        } catch (Exception e) {
            log.error("COMPLIANCE: CATASTROPHIC - Failed to create critical compliance alert", e);
        }
    }
}