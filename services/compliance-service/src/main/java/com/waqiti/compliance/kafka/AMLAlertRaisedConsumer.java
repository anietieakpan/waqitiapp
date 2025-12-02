package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.validation.NullSafetyUtils;
import com.waqiti.compliance.domain.*;
import com.waqiti.compliance.repository.AMLAlertRepository;
import com.waqiti.compliance.repository.ComplianceAlertRepository;
import com.waqiti.compliance.service.AMLInvestigationService;
import com.waqiti.compliance.service.FraudDetectionService;
import com.waqiti.compliance.service.UserAccountService;
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
 * CRITICAL COMPLIANCE KAFKA CONSUMER - AML Alert Raised
 * 
 * Anti-Money Laundering (AML) Alert Consumer
 * 
 * Handles events when suspicious transaction patterns or activities are detected
 * that may indicate money laundering, terrorist financing, or other financial crimes.
 * AML compliance is critical for maintaining regulatory standing and preventing
 * financial system abuse.
 * 
 * REGULATORY REQUIREMENTS:
 * - Bank Secrecy Act (BSA) - 31 CFR 1020.210
 * - USA PATRIOT Act - Section 352
 * - Anti-Money Laundering Act of 2020
 * - FinCEN Customer Due Diligence Requirements
 * - FATF (Financial Action Task Force) Recommendations
 * - OFAC (Office of Foreign Assets Control) Compliance
 * 
 * CRITICAL ACTIONS:
 * - Immediate investigation initiation for high-risk alerts
 * - Transaction monitoring and pattern analysis
 * - Account restrictions based on risk assessment
 * - Enhanced due diligence for high-risk customers
 * - SAR filing consideration for suspicious activity
 * - OFAC sanctions screening
 * 
 * AML ALERT TYPES:
 * - Structuring/Smurfing: Breaking large transactions into smaller amounts
 * - Rapid Movement: Quick transfer of funds through multiple accounts
 * - High-Risk Geography: Transactions involving high-risk countries
 * - Unusual Activity: Patterns inconsistent with customer profile
 * - Large Cash Transactions: Significant cash deposits/withdrawals
 * - Shell Company Activity: Transactions with suspected shell companies
 * - Round Amount Transactions: Suspicious round-number patterns
 * 
 * REGULATORY RISKS:
 * - Failure to investigate: $100,000 - $500,000 civil penalty per violation
 * - Inadequate AML program: Up to $10 million fines
 * - Criminal penalties: Up to 10 years imprisonment
 * - License revocation: Possible for severe violations
 * - Reputation damage: Severe brand impact
 * 
 * Event Source: transaction-monitoring-service, fraud-detection-service
 * Topic: compliance.aml.alert.raised
 * 
 * @author Waqiti Compliance Team
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AMLAlertRaisedConsumer {

    private final AMLAlertRepository amlAlertRepository;
    private final ComplianceAlertRepository complianceAlertRepository;
    private final AMLInvestigationService investigationService;
    private final FraudDetectionService fraudDetectionService;
    private final UserAccountService userAccountService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // AML thresholds and constants
    private static final BigDecimal LARGE_TRANSACTION_THRESHOLD = new BigDecimal("10000.00");
    private static final BigDecimal CRITICAL_TRANSACTION_THRESHOLD = new BigDecimal("50000.00");
    private static final int STRUCTURING_TRANSACTION_COUNT = 5; // Multiple transactions in short period
    private static final int INVESTIGATION_DEADLINE_DAYS = 7; // Time to complete initial investigation
    private static final int HIGH_RISK_INVESTIGATION_DEADLINE_DAYS = 3; // Urgent cases

    /**
     * Processes AML alert raised events.
     * 
     * CRITICAL: Must not lose messages - AML alerts are regulatory requirements.
     * All alerts must be tracked, investigated, and documented.
     * 
     * Retry Strategy:
     * - 5 attempts with exponential backoff (regulatory critical)
     * - Failed messages sent to DLQ for immediate manual intervention
     * - Alerts sent to AML compliance team for every failure
     */
    @KafkaListener(
        topics = "compliance.aml.alert.raised",
        groupId = "compliance-aml-alert-group",
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
    public void handleAMLAlertRaised(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            log.error("AML ALERT: Processing AML alert: key={}, partition={}, offset={}", 
                key, partition, record.offset());
            
            // Parse the AML alert event
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            
            String alertType = (String) event.get("alertType");
            String userId = (String) event.get("userId");
            String accountId = (String) event.get("accountId");
            String transactionId = (String) event.getOrDefault("transactionId", "N/A");
            
            // SAFETY FIX: Safe parsing with null check and default to prevent NumberFormatException
            BigDecimal transactionAmount = NullSafetyUtils.safeParseBigDecimal(
                event.getOrDefault("transactionAmount", "0").toString(),
                BigDecimal.ZERO
            );
            String currency = (String) event.getOrDefault("currency", "USD");
            String description = (String) event.get("description");
            String detectedBy = (String) event.get("detectedBy");
            String riskScore = (String) event.getOrDefault("riskScore", "MEDIUM");
            
            // Parse related transaction IDs for pattern analysis
            @SuppressWarnings("unchecked")
            List<String> relatedTransactionIds = (List<String>) event.getOrDefault("relatedTransactionIds", new ArrayList<>());
            
            // Parse geographic information
            String originCountry = (String) event.getOrDefault("originCountry", "UNKNOWN");
            String destinationCountry = (String) event.getOrDefault("destinationCountry", "UNKNOWN");
            
            // Parse detection timestamp
            String detectedAtStr = (String) event.get("detectedAt");
            LocalDateTime detectedAt = detectedAtStr != null ? 
                LocalDateTime.parse(detectedAtStr) : LocalDateTime.now();
            
            // Validate required fields
            if (alertType == null || userId == null) {
                log.error("Invalid AML alert event - missing required fields: {}", event);
                publishAMLAlertProcessingFailed(userId, alertType, "VALIDATION_ERROR", 
                    "Missing required fields: alertType or userId");
                acknowledgment.acknowledge();
                return;
            }
            
            // Check for duplicate alerts
            Optional<AMLAlert> existingAlert = amlAlertRepository.findByUserIdAndTransactionIdAndAlertType(
                userId, transactionId, AMLAlertType.valueOf(alertType));
            
            if (existingAlert.isPresent() && 
                existingAlert.get().getCreatedAt().isAfter(LocalDateTime.now().minusHours(24))) {
                log.warn("Duplicate AML alert detected within 24 hours - skipping: userId={}, alertType={}", 
                    userId, alertType);
                acknowledgment.acknowledge();
                return;
            }
            
            // Calculate investigation priority and deadline
            AMLRiskLevel riskLevel = determineRiskLevel(
                alertType,
                transactionAmount,
                riskScore,
                originCountry,
                destinationCountry,
                relatedTransactionIds.size()
            );
            
            int investigationDeadlineDays = riskLevel == AMLRiskLevel.CRITICAL || 
                                            riskLevel == AMLRiskLevel.HIGH ? 
                HIGH_RISK_INVESTIGATION_DEADLINE_DAYS : INVESTIGATION_DEADLINE_DAYS;
            
            LocalDateTime investigationDeadline = detectedAt.plusDays(investigationDeadlineDays);
            
            // Create AML alert record
            AMLAlert amlAlert = AMLAlert.builder()
                    .id(UUID.randomUUID())
                    .alertType(AMLAlertType.valueOf(alertType))
                    .userId(userId)
                    .accountId(accountId)
                    .transactionId(!"N/A".equals(transactionId) ? transactionId : null)
                    .relatedTransactionIds(relatedTransactionIds)
                    .transactionAmount(transactionAmount)
                    .currency(currency)
                    .description(description)
                    .detectedAt(detectedAt)
                    .detectedBy(detectedBy)
                    .riskLevel(riskLevel)
                    .originCountry(originCountry)
                    .destinationCountry(destinationCountry)
                    .status(AMLAlertStatus.PENDING_INVESTIGATION)
                    .investigationDeadline(investigationDeadline)
                    .requiresSARFiling(shouldRequireSARFiling(riskLevel, transactionAmount, alertType))
                    .createdAt(LocalDateTime.now())
                    .build();
            
            // Save AML alert
            amlAlert = amlAlertRepository.save(amlAlert);
            
            // Apply immediate account restrictions if necessary
            applyAccountRestrictionsIfNeeded(amlAlert);
            
            // Create compliance alert for tracking
            createComplianceAlert(amlAlert);
            
            // Initiate AML investigation workflow
            initiateAMLInvestigation(amlAlert);
            
            // Check for OFAC sanctions
            performSanctionsScreening(amlAlert);
            
            // Send notifications to AML compliance team
            sendAMLNotifications(amlAlert);
            
            // Audit the AML alert
            auditService.logSecurityEvent("AML_ALERT_RAISED", Map.of(
                "amlAlertId", amlAlert.getId().toString(),
                "alertType", alertType,
                "userId", userId,
                "accountId", accountId,
                "transactionId", transactionId,
                "transactionAmount", transactionAmount.toString(),
                "riskLevel", riskLevel.toString(),
                "originCountry", originCountry,
                "destinationCountry", destinationCountry,
                "requiresSARFiling", amlAlert.isRequiresSARFiling(),
                "investigationDeadline", investigationDeadline.toString(),
                "processingTimeMs", (System.currentTimeMillis() - startTime)
            ));
            
            long duration = System.currentTimeMillis() - startTime;
            log.error("AML ALERT PROCESSED: Created AML alert {} - Risk: {} - Deadline: {} ({}ms)", 
                amlAlert.getId(), riskLevel, investigationDeadline, duration);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL AML COMPLIANCE FAILURE: Error processing AML alert", e);
            
            // Extract identifiers for error reporting
            String userId = null;
            String alertType = null;
            try {
                Map<String, Object> event = objectMapper.readValue(message, Map.class);
                userId = (String) event.get("userId");
                alertType = (String) event.get("alertType");
            } catch (Exception parseException) {
                log.error("Failed to parse event for error reporting", parseException);
            }
            
            // Publish failure event
            publishAMLAlertProcessingFailed(userId, alertType, "PROCESSING_ERROR", e.getMessage());
            
            // Send CRITICAL alert to AML compliance team
            try {
                notificationService.sendCriticalComplianceAlert(
                    "AML Alert Processing Failure",
                    "CRITICAL: Failed to process AML alert. " +
                    "User ID: " + userId + ", " +
                    "Alert Type: " + alertType + ". " +
                    "Error: " + e.getMessage() + ". " +
                    "IMMEDIATE MANUAL INTERVENTION REQUIRED."
                );
            } catch (Exception notifEx) {
                log.error("Failed to send critical AML compliance alert", notifEx);
            }
            
            // Audit the critical failure
            auditService.logSecurityEvent("AML_ALERT_PROCESSING_FAILURE", Map.of(
                "userId", userId != null ? userId : "unknown",
                "alertType", alertType != null ? alertType : "unknown",
                "errorType", e.getClass().getSimpleName(),
                "errorMessage", e.getMessage(),
                "stackTrace", getStackTraceAsString(e),
                "timestamp", LocalDateTime.now().toString()
            ));
            
            // Rethrow to trigger retry mechanism
            throw new RuntimeException("Failed to process AML alert", e);
        }
    }
    
    /**
     * Determines the risk level of an AML alert based on multiple factors.
     */
    private AMLRiskLevel determineRiskLevel(
            String alertType,
            BigDecimal transactionAmount,
            String riskScore,
            String originCountry,
            String destinationCountry,
            int relatedTransactionCount) {
        
        // Critical amount = critical risk
        if (transactionAmount.compareTo(CRITICAL_TRANSACTION_THRESHOLD) >= 0) {
            return AMLRiskLevel.CRITICAL;
        }
        
        // High-risk alert types
        if ("STRUCTURING".equals(alertType) || 
            "TERRORIST_FINANCING".equals(alertType) ||
            "SANCTIONS_VIOLATION".equals(alertType)) {
            return AMLRiskLevel.CRITICAL;
        }
        
        // High-risk geography
        List<String> highRiskCountries = Arrays.asList("KP", "IR", "SY", "CU", "VE"); // North Korea, Iran, Syria, Cuba, Venezuela
        if (highRiskCountries.contains(originCountry) || highRiskCountries.contains(destinationCountry)) {
            return AMLRiskLevel.CRITICAL;
        }
        
        // Multiple related transactions = potential structuring
        if (relatedTransactionCount >= STRUCTURING_TRANSACTION_COUNT) {
            return AMLRiskLevel.HIGH;
        }
        
        // Large transactions
        if (transactionAmount.compareTo(LARGE_TRANSACTION_THRESHOLD) >= 0) {
            return AMLRiskLevel.HIGH;
        }
        
        // Risk score assessment
        if ("HIGH".equals(riskScore) || "VERY_HIGH".equals(riskScore)) {
            return AMLRiskLevel.HIGH;
        } else if ("MEDIUM".equals(riskScore)) {
            return AMLRiskLevel.MEDIUM;
        }
        
        return AMLRiskLevel.LOW;
    }
    
    /**
     * Determines if the alert requires SAR filing.
     */
    private boolean shouldRequireSARFiling(AMLRiskLevel riskLevel, BigDecimal amount, String alertType) {
        // Critical risk always requires SAR filing
        if (riskLevel == AMLRiskLevel.CRITICAL) {
            return true;
        }
        
        // High-risk alert types require SAR
        if ("STRUCTURING".equals(alertType) || 
            "TERRORIST_FINANCING".equals(alertType) ||
            "TRADE_BASED_LAUNDERING".equals(alertType)) {
            return true;
        }
        
        // Large amounts require SAR
        if (amount.compareTo(new BigDecimal("5000.00")) >= 0 && 
            (riskLevel == AMLRiskLevel.HIGH || riskLevel == AMLRiskLevel.CRITICAL)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Applies account restrictions based on AML alert risk level.
     */
    private void applyAccountRestrictionsIfNeeded(AMLAlert amlAlert) {
        try {
            log.info("Evaluating account restrictions for AML alert: {} - Risk: {}", 
                amlAlert.getId(), amlAlert.getRiskLevel());
            
            switch (amlAlert.getRiskLevel()) {
                case CRITICAL:
                    // Immediate account freeze for critical AML alerts
                    userAccountService.freezeAccount(
                        amlAlert.getUserId(),
                        amlAlert.getAccountId(),
                        "AML CRITICAL ALERT: " + amlAlert.getAlertType(),
                        "AML_CRITICAL_FREEZE"
                    );
                    
                    // Block all transactions
                    userAccountService.disableTransactions(
                        amlAlert.getUserId(),
                        "AML_CRITICAL_INVESTIGATION"
                    );
                    
                    // Freeze related wallets
                    userAccountService.freezeAllUserWallets(
                        amlAlert.getUserId(),
                        "AML_CRITICAL_INVESTIGATION"
                    );
                    
                    log.error("ACCOUNT FROZEN: User {} - Critical AML alert: {}", 
                        amlAlert.getUserId(), amlAlert.getAlertType());
                    break;
                    
                case HIGH:
                    // Restrict high-value transactions
                    userAccountService.applyTransactionLimits(
                        amlAlert.getUserId(),
                        new BigDecimal("1000.00"), // Daily limit during investigation
                        "AML_HIGH_RISK_INVESTIGATION"
                    );
                    
                    // Block international transfers
                    userAccountService.disableInternationalTransfers(
                        amlAlert.getUserId(),
                        "AML_HIGH_RISK_INVESTIGATION"
                    );
                    
                    log.warn("RESTRICTED: User {} - High-risk AML investigation", amlAlert.getUserId());
                    break;
                    
                case MEDIUM:
                    // Enhanced monitoring without hard restrictions
                    userAccountService.enableEnhancedMonitoring(
                        amlAlert.getUserId(),
                        "AML_MEDIUM_RISK_MONITORING"
                    );
                    
                    log.info("MONITORING: User {} - Enhanced AML monitoring enabled", amlAlert.getUserId());
                    break;
                    
                case LOW:
                    // Standard monitoring - no immediate restrictions
                    log.info("STANDARD: User {} - Standard AML monitoring", amlAlert.getUserId());
                    break;
            }
            
        } catch (Exception e) {
            log.error("Error applying account restrictions for AML alert: {}", amlAlert.getId(), e);
            // Critical - rethrow to trigger retry
            throw new RuntimeException("Failed to apply AML account restrictions", e);
        }
    }
    
    /**
     * Creates a compliance alert for tracking and workflow purposes.
     */
    private void createComplianceAlert(AMLAlert amlAlert) {
        try {
            ComplianceAlert alert = ComplianceAlert.builder()
                    .id(UUID.randomUUID())
                    .alertType(ComplianceAlertType.AML_ALERT_RAISED)
                    .relatedEntityId(amlAlert.getId().toString())
                    .relatedEntityType("AML_ALERT")
                    .userId(amlAlert.getUserId())
                    .accountId(amlAlert.getAccountId())
                    .severity(mapRiskLevelToSeverity(amlAlert.getRiskLevel()))
                    .description("AML Alert: " + amlAlert.getAlertType())
                    .status(AlertStatus.OPEN)
                    .deadline(amlAlert.getInvestigationDeadline())
                    .assignedTo("AML_COMPLIANCE_TEAM")
                    .requiresAction(amlAlert.getRiskLevel() == AMLRiskLevel.CRITICAL)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            complianceAlertRepository.save(alert);
            
            log.info("Created compliance alert {} for AML alert {}", alert.getId(), amlAlert.getId());
            
        } catch (Exception e) {
            log.error("Failed to create compliance alert for AML alert: {}", amlAlert.getId(), e);
            // Non-critical - log but don't fail
        }
    }
    
    /**
     * Initiates the AML investigation workflow.
     */
    private void initiateAMLInvestigation(AMLAlert amlAlert) {
        try {
            log.info("Initiating AML investigation for alert: {}", amlAlert.getId());
            
            // Create investigation case
            investigationService.createAMLInvestigationCase(amlAlert);
            
            // Gather transaction history
            investigationService.gatherTransactionHistory(
                amlAlert.getUserId(),
                amlAlert.getTransactionId(),
                amlAlert.getRelatedTransactionIds()
            );
            
            // Perform customer due diligence review
            investigationService.performCDDReview(amlAlert.getUserId());
            
            // Check historical alerts
            investigationService.checkHistoricalAlerts(amlAlert.getUserId());
            
            // Generate investigation report template
            investigationService.generateInvestigationTemplate(amlAlert.getId());
            
            // Assign to investigator based on severity
            investigationService.assignToInvestigator(
                amlAlert.getId(),
                amlAlert.getRiskLevel()
            );
            
            // Schedule deadline reminder
            investigationService.scheduleInvestigationReminder(
                amlAlert.getId(),
                amlAlert.getInvestigationDeadline().minusDays(1)
            );
            
            log.info("AML investigation initiated for alert: {}", amlAlert.getId());
            
        } catch (Exception e) {
            log.error("Error initiating AML investigation: {}", amlAlert.getId(), e);
            // Non-critical - log but don't fail alert creation
        }
    }
    
    /**
     * Performs OFAC sanctions screening.
     */
    private void performSanctionsScreening(AMLAlert amlAlert) {
        try {
            log.info("Performing OFAC sanctions screening for user: {}", amlAlert.getUserId());
            
            boolean sanctionsMatch = investigationService.checkOFACSanctions(
                amlAlert.getUserId(),
                amlAlert.getOriginCountry(),
                amlAlert.getDestinationCountry()
            );
            
            if (sanctionsMatch) {
                log.error("SANCTIONS MATCH DETECTED: User {} - IMMEDIATE ACTION REQUIRED", 
                    amlAlert.getUserId());
                
                // Update alert with sanctions flag
                amlAlert.setSanctionsMatch(true);
                amlAlert.setRiskLevel(AMLRiskLevel.CRITICAL);
                amlAlertRepository.save(amlAlert);
                
                // Immediate account freeze
                userAccountService.freezeAccount(
                    amlAlert.getUserId(),
                    amlAlert.getAccountId(),
                    "OFAC SANCTIONS MATCH DETECTED",
                    "OFAC_SANCTIONS_FREEZE"
                );
                
                // Send critical alert to compliance
                notificationService.sendCriticalSanctionsMatchAlert(amlAlert);
                
                // Audit the sanctions match
                auditService.logSecurityEvent("OFAC_SANCTIONS_MATCH", Map.of(
                    "amlAlertId", amlAlert.getId().toString(),
                    "userId", amlAlert.getUserId(),
                    "accountId", amlAlert.getAccountId(),
                    "originCountry", amlAlert.getOriginCountry(),
                    "destinationCountry", amlAlert.getDestinationCountry(),
                    "timestamp", LocalDateTime.now().toString()
                ));
            }
            
        } catch (Exception e) {
            log.error("Error performing sanctions screening: {}", amlAlert.getId(), e);
            // Critical - treat as potential sanctions match to be safe
            try {
                notificationService.sendCriticalComplianceAlert(
                    "Sanctions Screening Failure",
                    "CRITICAL: Failed to perform OFAC sanctions screening for user " + 
                    amlAlert.getUserId() + ". Manual screening required immediately."
                );
            } catch (Exception notifEx) {
                log.error("Failed to send sanctions screening failure alert", notifEx);
            }
        }
    }
    
    /**
     * Sends notifications to AML compliance team.
     */
    private void sendAMLNotifications(AMLAlert amlAlert) {
        try {
            switch (amlAlert.getRiskLevel()) {
                case CRITICAL:
                    notificationService.sendCriticalAMLAlert(amlAlert);
                    notificationService.sendSMSToAMLOfficer(
                        formatCriticalAMLMessage(amlAlert)
                    );
                    notificationService.sendAMLAlertToRegulator(amlAlert); // For critical cases
                    break;
                    
                case HIGH:
                    notificationService.sendHighPriorityAMLAlert(amlAlert);
                    break;
                    
                case MEDIUM:
                case LOW:
                    notificationService.sendAMLAlertToComplianceTeam(amlAlert);
                    break;
            }
            
            // Email to assigned investigator
            notificationService.sendAMLInvestigationAssignmentEmail(
                amlAlert.getId(),
                amlAlert.getAlertType().toString(),
                amlAlert.getInvestigationDeadline(),
                amlAlert.getRiskLevel()
            );
            
            log.info("Sent AML notifications for alert: {}", amlAlert.getId());
            
        } catch (Exception e) {
            log.error("Error sending AML notifications: {}", amlAlert.getId(), e);
            // Non-critical - log but don't fail
        }
    }
    
    private String formatCriticalAMLMessage(AMLAlert amlAlert) {
        return String.format(
            "CRITICAL AML ALERT\n" +
            "Alert ID: %s\n" +
            "Type: %s\n" +
            "User: %s\n" +
            "Amount: %s %s\n" +
            "Risk Level: %s\n" +
            "Origin: %s\n" +
            "Destination: %s\n" +
            "Investigation Deadline: %s\n" +
            "SAR Filing Required: %s\n" +
            "IMMEDIATE INVESTIGATION REQUIRED",
            amlAlert.getId(),
            amlAlert.getAlertType(),
            amlAlert.getUserId(),
            amlAlert.getTransactionAmount(),
            amlAlert.getCurrency(),
            amlAlert.getRiskLevel(),
            amlAlert.getOriginCountry(),
            amlAlert.getDestinationCountry(),
            amlAlert.getInvestigationDeadline(),
            amlAlert.isRequiresSARFiling() ? "YES" : "NO"
        );
    }
    
    private AlertSeverity mapRiskLevelToSeverity(AMLRiskLevel riskLevel) {
        return switch (riskLevel) {
            case CRITICAL -> AlertSeverity.CRITICAL;
            case HIGH -> AlertSeverity.HIGH;
            case MEDIUM -> AlertSeverity.MEDIUM;
            case LOW -> AlertSeverity.LOW;
        };
    }
    
    private void publishAMLAlertProcessingFailed(String userId, String alertType, 
                                                  String errorCode, String errorMessage) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "compliance-aml-alert-processing-failed",
                "userId", userId != null ? userId : "unknown",
                "alertType", alertType != null ? alertType : "unknown",
                "errorCode", errorCode,
                "errorMessage", errorMessage,
                "timestamp", LocalDateTime.now().toString()
            );
            
            kafkaTemplate.send("compliance.aml.alert.failed", userId, event);
            
            log.debug("Published AML alert processing failed event: userId={}, alertType={}, error={}", 
                userId, alertType, errorCode);
            
        } catch (Exception e) {
            log.error("Failed to publish AML alert failed event", e);
            // Last resort - audit
            try {
                auditService.logSecurityEvent("AML_ALERT_EVENT_PUBLISH_FAILURE", Map.of(
                    "userId", userId != null ? userId : "unknown",
                    "alertType", alertType != null ? alertType : "unknown",
                    "errorCode", errorCode,
                    "publishError", e.getMessage()
                ));
            } catch (Exception auditException) {
                log.error("CRITICAL: Unable to audit or publish AML alert failure", auditException);
            }
        }
    }
    
    private String getStackTraceAsString(Exception e) {
        return org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(e);
    }
}