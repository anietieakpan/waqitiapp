package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.compliance.domain.*;
import com.waqiti.compliance.repository.SARRepository;
import com.waqiti.compliance.repository.ComplianceAlertRepository;
import com.waqiti.compliance.service.SARFilingService;
import com.waqiti.compliance.service.FinCENIntegrationService;
import com.waqiti.compliance.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * CRITICAL COMPLIANCE KAFKA CONSUMER - SAR Filing Required
 * 
 * Suspicious Activity Report (SAR) Filing Consumer
 * 
 * Handles events that trigger mandatory SAR filing requirements under the
 * Bank Secrecy Act (BSA) and USA PATRIOT Act. SARs must be filed within
 * 30 days of initial detection of suspicious activity.
 * 
 * REGULATORY REQUIREMENTS:
 * - Bank Secrecy Act (BSA) 31 CFR 1020.320
 * - USA PATRIOT Act Section 356(c)
 * - FinCEN SAR Filing Requirements
 * - 30-day filing deadline from detection
 * - $5,000 threshold for known perpetrators
 * - $25,000 threshold for unknown perpetrators
 * 
 * CRITICAL NATURE:
 * - Failure to file SAR: $25,000-$100,000 civil penalty per violation
 * - Criminal penalties: Up to 5 years imprisonment
 * - Regulatory sanctions: License revocation possible
 * - Reputation damage: Severe
 * 
 * Event Source: fraud-detection-service, transaction-monitoring-service, compliance-service
 * Topic: compliance.sar.filing.required
 * 
 * @author Waqiti Compliance Team
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SARFilingRequiredConsumer {

    private final SARRepository sarRepository;
    private final ComplianceAlertRepository alertRepository;
    private final SARFilingService sarFilingService;
    private final FinCENIntegrationService finCENService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Regulatory thresholds
    private static final BigDecimal KNOWN_PERPETRATOR_THRESHOLD = new BigDecimal("5000.00");
    private static final BigDecimal UNKNOWN_PERPETRATOR_THRESHOLD = new BigDecimal("25000.00");
    private static final int SAR_FILING_DEADLINE_DAYS = 30;

    /**
     * Processes SAR filing required events.
     * 
     * CRITICAL: This consumer MUST NOT lose messages due to regulatory requirements.
     * All SAR filing requirements must be tracked and actioned.
     * 
     * Retry Strategy:
     * - 5 attempts with exponential backoff (regulatory critical)
     * - Failed messages sent to DLQ for immediate manual intervention
     * - Alerts sent to compliance team for every failure
     */
    @KafkaListener(
        topics = "compliance.sar.filing.required",
        groupId = "compliance-sar-filing-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 30000),
        autoCreateTopics = "false",
        dltTopicSuffix = "-dlt",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional(rollbackFor = Exception.class)
    public void handleSARFilingRequired(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            log.error("REGULATORY ALERT: Processing SAR filing requirement: key={}, partition={}, offset={}", 
                key, partition, record.offset());
            
            // Parse the SAR filing event
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            
            String alertId = (String) event.get("alertId");
            String activityType = (String) event.get("activityType");
            String userId = (String) event.get("userId");
            String accountId = (String) event.get("accountId");
            String transactionId = (String) event.getOrDefault("transactionId", "N/A");
            
            BigDecimal suspiciousAmount = new BigDecimal(event.getOrDefault("suspiciousAmount", "0").toString());
            String currency = (String) event.getOrDefault("currency", "USD");
            String suspiciousActivityDescription = (String) event.get("suspiciousActivityDescription");
            String detectedBy = (String) event.get("detectedBy");
            Boolean perpetratorKnown = (Boolean) event.getOrDefault("perpetratorKnown", false);
            
            // Parse detection timestamp
            String detectedAtStr = (String) event.get("detectedAt");
            LocalDateTime detectedAt = detectedAtStr != null ? 
                LocalDateTime.parse(detectedAtStr) : LocalDateTime.now();
            
            // Validate required fields
            if (alertId == null || activityType == null || userId == null) {
                log.error("Invalid SAR filing event - missing required fields: {}", event);
                publishSARFilingFailedEvent(alertId, "VALIDATION_ERROR", 
                    "Missing required fields: alertId, activityType, or userId");
                acknowledgment.acknowledge();
                return;
            }
            
            // Check if SAR already exists for this alert
            Optional<SuspiciousActivityReport> existingSAR = sarRepository.findByAlertId(alertId);
            if (existingSAR.isPresent()) {
                log.warn("SAR already exists for alert: {}, SAR ID: {}", 
                    alertId, existingSAR.get().getId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Validate amount meets threshold
            boolean meetsThreshold = validateThreshold(suspiciousAmount, perpetratorKnown);
            if (!meetsThreshold) {
                log.warn("Amount {} does not meet SAR filing threshold for perpetratorKnown={}", 
                    suspiciousAmount, perpetratorKnown);
                // Still create SAR but mark as below threshold for review
            }
            
            // Calculate filing deadline
            LocalDateTime filingDeadline = detectedAt.plusDays(SAR_FILING_DEADLINE_DAYS);
            
            // Create SAR record
            SuspiciousActivityReport sar = SuspiciousActivityReport.builder()
                    .id(UUID.randomUUID())
                    .alertId(alertId)
                    .activityType(SARActivityType.valueOf(activityType))
                    .userId(userId)
                    .accountId(accountId)
                    .transactionId(!"N/A".equals(transactionId) ? transactionId : null)
                    .suspiciousAmount(suspiciousAmount)
                    .currency(currency)
                    .suspiciousActivityDescription(suspiciousActivityDescription)
                    .detectedAt(detectedAt)
                    .detectedBy(detectedBy)
                    .perpetratorKnown(perpetratorKnown)
                    .meetsThreshold(meetsThreshold)
                    .status(SARStatus.PENDING_REVIEW)
                    .filingDeadline(filingDeadline)
                    .priority(calculatePriority(suspiciousAmount, perpetratorKnown, filingDeadline))
                    .createdAt(LocalDateTime.now())
                    .build();
            
            // Save SAR
            sar = sarRepository.save(sar);
            
            // Create compliance alert record
            createComplianceAlert(sar);
            
            // Initiate SAR preparation workflow
            initiateSARPreparation(sar);
            
            // Send immediate notifications to compliance team
            sendComplianceNotifications(sar);
            
            // Audit the SAR filing requirement
            auditService.logSecurityEvent("SAR_FILING_REQUIRED", Map.of(
                "sarId", sar.getId().toString(),
                "alertId", alertId,
                "activityType", activityType,
                "userId", userId,
                "accountId", accountId,
                "suspiciousAmount", suspiciousAmount.toString(),
                "perpetratorKnown", perpetratorKnown.toString(),
                "filingDeadline", filingDeadline.toString(),
                "priority", sar.getPriority().toString(),
                "processingTimeMs", (System.currentTimeMillis() - startTime)
            ));
            
            long duration = System.currentTimeMillis() - startTime;
            log.error("SAR FILING REQUIRED: Created SAR {} for alert {} - Deadline: {} ({}ms)", 
                sar.getId(), alertId, filingDeadline, duration);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL REGULATORY FAILURE: Error processing SAR filing requirement", e);
            
            // Extract alert ID for error reporting
            String alertId = extractAlertId(message);
            
            // Publish failure event
            publishSARFilingFailedEvent(alertId, "PROCESSING_ERROR", e.getMessage());
            
            // Send CRITICAL alert to compliance team
            try {
                notificationService.sendCriticalComplianceAlert(
                    "SAR Filing Processing Failure",
                    "CRITICAL: Failed to process SAR filing requirement. " +
                    "Alert ID: " + alertId + ". " +
                    "Error: " + e.getMessage() + ". " +
                    "IMMEDIATE MANUAL INTERVENTION REQUIRED."
                );
            } catch (Exception notifEx) {
                log.error("Failed to send critical compliance alert", notifEx);
            }
            
            // Audit the critical failure
            auditService.logSecurityEvent("SAR_FILING_PROCESSING_FAILURE", Map.of(
                "alertId", alertId != null ? alertId : "unknown",
                "errorType", e.getClass().getSimpleName(),
                "errorMessage", e.getMessage(),
                "stackTrace", getStackTraceAsString(e),
                "timestamp", LocalDateTime.now().toString()
            ));
            
            // Rethrow to trigger retry mechanism
            throw new RuntimeException("Failed to process SAR filing requirement", e);
        }
    }
    
    /**
     * Validates if amount meets regulatory threshold for SAR filing.
     */
    private boolean validateThreshold(BigDecimal amount, boolean perpetratorKnown) {
        BigDecimal threshold = perpetratorKnown ? 
            KNOWN_PERPETRATOR_THRESHOLD : UNKNOWN_PERPETRATOR_THRESHOLD;
        
        return amount.compareTo(threshold) >= 0;
    }
    
    /**
     * Calculates SAR priority based on multiple factors.
     */
    private SARPriority calculatePriority(BigDecimal amount, boolean perpetratorKnown, 
                                         LocalDateTime filingDeadline) {
        
        // Calculate days until deadline
        long daysUntilDeadline = java.time.Duration.between(
            LocalDateTime.now(), filingDeadline
        ).toDays();
        
        // High amount = critical
        if (amount.compareTo(new BigDecimal("100000")) >= 0) {
            return SARPriority.CRITICAL;
        }
        
        // Known perpetrator = high priority
        if (perpetratorKnown) {
            return SARPriority.HIGH;
        }
        
        // Approaching deadline = escalate priority
        if (daysUntilDeadline <= 7) {
            return SARPriority.CRITICAL;
        } else if (daysUntilDeadline <= 14) {
            return SARPriority.HIGH;
        }
        
        return SARPriority.MEDIUM;
    }
    
    /**
     * Creates a compliance alert for tracking purposes.
     */
    private void createComplianceAlert(SuspiciousActivityReport sar) {
        try {
            ComplianceAlert alert = ComplianceAlert.builder()
                    .id(UUID.randomUUID())
                    .alertType(ComplianceAlertType.SAR_FILING_REQUIRED)
                    .relatedEntityId(sar.getId().toString())
                    .relatedEntityType("SAR")
                    .userId(sar.getUserId())
                    .accountId(sar.getAccountId())
                    .severity(mapPriorityToSeverity(sar.getPriority()))
                    .description("SAR filing required: " + sar.getActivityType())
                    .status(AlertStatus.OPEN)
                    .deadline(sar.getFilingDeadline())
                    .assignedTo("COMPLIANCE_TEAM")
                    .createdAt(LocalDateTime.now())
                    .build();
            
            alertRepository.save(alert);
            
            log.info("Created compliance alert {} for SAR {}", alert.getId(), sar.getId());
            
        } catch (Exception e) {
            log.error("Failed to create compliance alert for SAR: {}", sar.getId(), e);
            // Non-critical - log but don't fail SAR creation
        }
    }
    
    /**
     * Initiates the SAR preparation and filing workflow.
     */
    private void initiateSARPreparation(SuspiciousActivityReport sar) {
        try {
            log.info("Initiating SAR preparation workflow for SAR: {}", sar.getId());
            
            // Gather supporting documentation
            sarFilingService.gatherSupportingDocumentation(sar.getId());
            
            // Create SAR narrative
            sarFilingService.generateSARNarrative(sar.getId());
            
            // Prepare FinCEN BSA E-Filing format
            sarFilingService.prepareFinCENFormat(sar.getId());
            
            // Create review task for compliance officer
            sarFilingService.createReviewTask(sar.getId());
            
            // Schedule reminder 7 days before deadline
            sarFilingService.scheduleDeadlineReminder(
                sar.getId(), 
                sar.getFilingDeadline().minusDays(7)
            );
            
            log.info("SAR preparation workflow initiated for SAR: {}", sar.getId());
            
        } catch (Exception e) {
            log.error("Error initiating SAR preparation for: {}", sar.getId(), e);
            // Non-critical - log but don't fail SAR creation
        }
    }
    
    /**
     * Sends notifications to compliance team about SAR filing requirement.
     */
    private void sendComplianceNotifications(SuspiciousActivityReport sar) {
        try {
            // Immediate notification based on priority
            switch (sar.getPriority()) {
                case CRITICAL:
                    notificationService.sendCriticalSARNotification(sar);
                    notificationService.sendSMSToComplianceOfficer(
                        formatCriticalSARMessage(sar)
                    );
                    break;
                    
                case HIGH:
                    notificationService.sendHighPrioritySARNotification(sar);
                    break;
                    
                case MEDIUM:
                case LOW:
                    notificationService.sendSARNotificationToComplianceTeam(sar);
                    break;
            }
            
            // Email to compliance officer
            notificationService.sendSARFilingRequiredEmail(
                sar.getId(),
                sar.getActivityType().toString(),
                sar.getFilingDeadline(),
                sar.getPriority()
            );
            
            log.info("Sent compliance notifications for SAR: {}", sar.getId());
            
        } catch (Exception e) {
            log.error("Error sending compliance notifications for SAR: {}", sar.getId(), e);
            // Non-critical - log but don't fail SAR creation
        }
    }
    
    private String formatCriticalSARMessage(SuspiciousActivityReport sar) {
        return String.format(
            "CRITICAL SAR FILING REQUIRED\n" +
            "SAR ID: %s\n" +
            "Activity: %s\n" +
            "Amount: %s %s\n" +
            "User: %s\n" +
            "Deadline: %s\n" +
            "Days Remaining: %d\n" +
            "IMMEDIATE ACTION REQUIRED",
            sar.getId(),
            sar.getActivityType(),
            sar.getSuspiciousAmount(),
            sar.getCurrency(),
            sar.getUserId(),
            sar.getFilingDeadline(),
            java.time.Duration.between(LocalDateTime.now(), sar.getFilingDeadline()).toDays()
        );
    }
    
    private AlertSeverity mapPriorityToSeverity(SARPriority priority) {
        return switch (priority) {
            case CRITICAL -> AlertSeverity.CRITICAL;
            case HIGH -> AlertSeverity.HIGH;
            case MEDIUM -> AlertSeverity.MEDIUM;
            case LOW -> AlertSeverity.LOW;
        };
    }
    
    private void publishSARFilingFailedEvent(String alertId, String errorCode, String errorMessage) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "compliance-sar-filing-failed",
                "alertId", alertId != null ? alertId : "unknown",
                "errorCode", errorCode,
                "errorMessage", errorMessage,
                "timestamp", LocalDateTime.now().toString()
            );
            
            kafkaTemplate.send("compliance.sar.filing.failed", alertId, event);
            
            log.debug("Published SAR filing failed event: alertId={}, error={}", 
                alertId, errorCode);
            
        } catch (Exception e) {
            log.error("Failed to publish SAR filing failed event", e);
            // Last resort - audit the failure
            try {
                auditService.logSecurityEvent("SAR_FILING_EVENT_PUBLISH_FAILURE", Map.of(
                    "alertId", alertId != null ? alertId : "unknown",
                    "errorCode", errorCode,
                    "publishError", e.getMessage()
                ));
            } catch (Exception auditException) {
                log.error("CRITICAL: Unable to audit or publish SAR filing failure - MANUAL ESCALATION REQUIRED", 
                    auditException);
            }
        }
    }
    
    private String extractAlertId(String message) {
        try {
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            return (String) event.get("alertId");
        } catch (Exception e) {
            log.error("Failed to extract alertId from message", e);
            return null;
        }
    }
    
    private String getStackTraceAsString(Exception e) {
        // Use Apache Commons Lang for stack trace string conversion
        // This is safer and more efficient than printStackTrace()
        return org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(e);
    }
}