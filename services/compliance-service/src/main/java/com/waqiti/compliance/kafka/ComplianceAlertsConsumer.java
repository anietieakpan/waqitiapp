package com.waqiti.compliance.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.compliance.model.*;
import com.waqiti.compliance.repository.ComplianceAlertRepository;
import com.waqiti.compliance.service.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade Kafka consumer for compliance alerts
 * Handles real-time compliance violations, regulatory alerts, and escalation management
 * 
 * Critical for: Regulatory compliance, risk management, operational security
 * SLA: Must process alerts within 15 seconds to enable rapid response
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ComplianceAlertsConsumer {

    private final ComplianceAlertRepository alertRepository;
    private final AlertManagementService alertManagementService;
    private final EscalationService escalationService;
    private final NotificationService notificationService;
    private final WorkflowService workflowService;
    private final CaseManagementService caseManagementService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final AlertEnrichmentService enrichmentService;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long SLA_THRESHOLD_MS = 15000; // 15 seconds
    private static final Set<String> CRITICAL_ALERT_TYPES = Set.of(
        "SANCTIONS_HIT", "TERRORIST_FINANCING", "MONEY_LAUNDERING", 
        "FRAUD_CONFIRMED", "REGULATORY_BREACH", "OFAC_VIOLATION"
    );
    
    @KafkaListener(
        topics = {"compliance-alerts"},
        groupId = "compliance-alerts-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "compliance-alerts-processor", fallbackMethod = "handleComplianceAlertFailure")
    @Retry(name = "compliance-alerts-processor")
    public void processComplianceAlert(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        log.info("Processing compliance alert: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, Object> payload = event.getPayload();
            ComplianceAlert alert = extractComplianceAlert(payload);
            
            // Validate alert
            validateAlert(alert);
            
            // Check for duplicate alert
            if (isDuplicateAlert(alert)) {
                log.warn("Duplicate compliance alert detected: {}, skipping", alert.getAlertId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Enrich alert with additional context
            ComplianceAlert enrichedAlert = enrichAlert(alert);
            
            // Classify and prioritize alert
            AlertClassification classification = classifyAlert(enrichedAlert);
            
            // Process alert based on classification
            AlertProcessingResult result = processAlert(enrichedAlert, classification);
            
            // Handle escalation if required
            if (classification.requiresEscalation()) {
                handleEscalation(enrichedAlert, classification, result);
            }
            
            // Create cases for investigation
            if (classification.requiresInvestigation()) {
                createInvestigationCase(enrichedAlert, result);
            }
            
            // Trigger automated workflows
            if (classification.hasAutomatedActions()) {
                triggerAutomatedWorkflows(enrichedAlert, classification);
            }
            
            // Send notifications
            sendAlertNotifications(enrichedAlert, classification, result);
            
            // Update alert tracking
            updateAlertTracking(enrichedAlert, result);
            
            // Audit alert processing
            auditAlertProcessing(enrichedAlert, result, event);
            
            // Record metrics
            recordAlertMetrics(enrichedAlert, result, startTime);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed compliance alert: {} type: {} priority: {} in {}ms", 
                    enrichedAlert.getAlertId(), enrichedAlert.getAlertType(), 
                    classification.getPriority(), System.currentTimeMillis() - startTime);
            
        } catch (ValidationException e) {
            log.error("Validation failed for compliance alert: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();
            
        } catch (CriticalAlertException e) {
            log.error("Critical alert processing failed: {}", eventId, e);
            handleCriticalAlertError(event, e);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process compliance alert: {}", eventId, e);
            handleProcessingError(event, e, acknowledgment);
        }
    }

    private ComplianceAlert extractComplianceAlert(Map<String, Object> payload) {
        return ComplianceAlert.builder()
            .alertId(extractString(payload, "alertId", UUID.randomUUID().toString()))
            .alertType(extractString(payload, "alertType", null))
            .severity(AlertSeverity.fromString(extractString(payload, "severity", "MEDIUM")))
            .status(AlertStatus.OPEN)
            .entityId(extractString(payload, "entityId", null))
            .entityType(extractString(payload, "entityType", null))
            .customerId(extractString(payload, "customerId", null))
            .merchantId(extractString(payload, "merchantId", null))
            .transactionId(extractString(payload, "transactionId", null))
            .amount(extractBigDecimal(payload, "amount"))
            .currency(extractString(payload, "currency", "USD"))
            .country(extractString(payload, "country", null))
            .description(extractString(payload, "description", null))
            .riskScore(extractInteger(payload, "riskScore", 0))
            .sourceSystem(extractString(payload, "sourceSystem", "UNKNOWN"))
            .detectionRule(extractString(payload, "detectionRule", null))
            .triggerData(extractMap(payload, "triggerData"))
            .metadata(extractMap(payload, "metadata"))
            .createdAt(Instant.now())
            .build();
    }

    private void validateAlert(ComplianceAlert alert) {
        if (alert.getAlertType() == null || alert.getAlertType().isEmpty()) {
            throw new ValidationException("Alert type is required");
        }
        
        if (alert.getEntityId() == null || alert.getEntityId().isEmpty()) {
            throw new ValidationException("Entity ID is required");
        }
        
        if (alert.getEntityType() == null || alert.getEntityType().isEmpty()) {
            throw new ValidationException("Entity type is required");
        }
        
        if (alert.getSeverity() == null) {
            throw new ValidationException("Alert severity is required");
        }
        
        // Validate critical alert requirements
        if (CRITICAL_ALERT_TYPES.contains(alert.getAlertType())) {
            if (alert.getDescription() == null || alert.getDescription().trim().isEmpty()) {
                throw new ValidationException("Description required for critical alert type");
            }
            
            if (alert.getRiskScore() < 70) {
                log.warn("Critical alert type {} has low risk score: {}", 
                        alert.getAlertType(), alert.getRiskScore());
            }
        }
        
        // Validate amount for financial alerts
        if (isFinancialAlert(alert.getAlertType()) && alert.getAmount() == null) {
            throw new ValidationException("Amount required for financial compliance alert");
        }
    }

    private boolean isFinancialAlert(String alertType) {
        return Arrays.asList("MONEY_LAUNDERING", "SUSPICIOUS_TRANSACTION", "CTR_THRESHOLD", 
                            "STRUCTURING", "SMURFING", "LAYERING").contains(alertType);
    }

    private boolean isDuplicateAlert(ComplianceAlert alert) {
        // Check for exact duplicate
        if (alertRepository.existsByAlertIdAndCreatedAtAfter(
                alert.getAlertId(), 
                Instant.now().minus(10, ChronoUnit.MINUTES))) {
            return true;
        }
        
        // Check for similar alert (same entity, type within time window)
        return alertRepository.existsSimilarAlert(
            alert.getEntityId(),
            alert.getAlertType(),
            Instant.now().minus(30, ChronoUnit.MINUTES)
        );
    }

    private ComplianceAlert enrichAlert(ComplianceAlert alert) {
        // Enrich with entity information
        EntityEnrichmentResult enrichment = enrichmentService.enrichEntity(
            alert.getEntityId(),
            alert.getEntityType()
        );
        
        alert.setEntityName(enrichment.getEntityName());
        alert.setEntityRiskProfile(enrichment.getRiskProfile());
        alert.setHistoricalAlertCount(enrichment.getHistoricalAlertCount());
        
        // Enrich with geographical data
        if (alert.getCountry() != null) {
            CountryRiskData countryRisk = enrichmentService.getCountryRiskData(alert.getCountry());
            alert.setCountryRiskLevel(countryRisk.getRiskLevel());
            alert.setSanctionedCountry(countryRisk.isSanctioned());
        }
        
        // Enrich with related alerts
        List<String> relatedAlerts = enrichmentService.findRelatedAlerts(
            alert.getEntityId(),
            alert.getAlertType(),
            ChronoUnit.DAYS.between(Instant.now().minus(30, ChronoUnit.DAYS), Instant.now())
        );
        alert.setRelatedAlerts(relatedAlerts);
        
        // Calculate enhanced risk score
        int enhancedRiskScore = calculateEnhancedRiskScore(alert, enrichment);
        alert.setEnhancedRiskScore(enhancedRiskScore);
        
        return alert;
    }

    private int calculateEnhancedRiskScore(ComplianceAlert alert, EntityEnrichmentResult enrichment) {
        int baseScore = alert.getRiskScore();
        int enhancedScore = baseScore;
        
        // Historical alert factor
        if (enrichment.getHistoricalAlertCount() > 5) {
            enhancedScore += 15;
        } else if (enrichment.getHistoricalAlertCount() > 2) {
            enhancedScore += 10;
        }
        
        // Entity risk profile factor
        switch (enrichment.getRiskProfile()) {
            case "HIGH":
                enhancedScore += 20;
                break;
            case "MEDIUM":
                enhancedScore += 10;
                break;
            case "LOW":
                enhancedScore += 0;
                break;
        }
        
        // Country risk factor
        if (alert.isSanctionedCountry()) {
            enhancedScore += 25;
        } else if ("HIGH".equals(alert.getCountryRiskLevel())) {
            enhancedScore += 15;
        }
        
        // Amount factor for financial alerts
        if (alert.getAmount() != null) {
            if (alert.getAmount().compareTo(new BigDecimal("100000")) > 0) {
                enhancedScore += 15;
            } else if (alert.getAmount().compareTo(new BigDecimal("10000")) > 0) {
                enhancedScore += 10;
            }
        }
        
        // Critical alert type factor
        if (CRITICAL_ALERT_TYPES.contains(alert.getAlertType())) {
            enhancedScore += 20;
        }
        
        // Related alerts factor
        if (alert.getRelatedAlerts() != null && alert.getRelatedAlerts().size() > 3) {
            enhancedScore += 10;
        }
        
        return Math.min(enhancedScore, 100); // Cap at 100
    }

    private AlertClassification classifyAlert(ComplianceAlert alert) {
        AlertClassification classification = new AlertClassification();
        
        // Determine priority based on multiple factors
        String priority = determinePriority(alert);
        classification.setPriority(priority);
        
        // Determine response time SLA
        long responseTimeSLA = determineResponseTimeSLA(priority, alert.getAlertType());
        classification.setResponseTimeSLA(responseTimeSLA);
        
        // Determine required actions
        classification.setRequiresEscalation(shouldEscalate(alert, priority));
        classification.setRequiresInvestigation(shouldInvestigate(alert));
        classification.setRequiresImmediateAction(shouldTakeImmediateAction(alert));
        classification.setHasAutomatedActions(hasAutomatedActions(alert.getAlertType()));
        
        // Determine notification channels
        classification.setNotificationChannels(determineNotificationChannels(alert, priority));
        
        // Determine assignee
        classification.setAssignedTeam(determineAssignedTeam(alert.getAlertType()));
        classification.setAssignedUser(determineAssignedUser(alert, priority));
        
        return classification;
    }

    private String determinePriority(ComplianceAlert alert) {
        // Critical priority conditions
        if (CRITICAL_ALERT_TYPES.contains(alert.getAlertType()) ||
            alert.getEnhancedRiskScore() >= 90 ||
            alert.getSeverity() == AlertSeverity.CRITICAL ||
            alert.isSanctionedCountry()) {
            return "CRITICAL";
        }
        
        // High priority conditions
        if (alert.getEnhancedRiskScore() >= 70 ||
            alert.getSeverity() == AlertSeverity.HIGH ||
            (alert.getAmount() != null && alert.getAmount().compareTo(new BigDecimal("50000")) > 0) ||
            (alert.getRelatedAlerts() != null && alert.getRelatedAlerts().size() > 2)) {
            return "HIGH";
        }
        
        // Medium priority conditions
        if (alert.getEnhancedRiskScore() >= 50 ||
            alert.getSeverity() == AlertSeverity.MEDIUM ||
            (alert.getAmount() != null && alert.getAmount().compareTo(new BigDecimal("10000")) > 0)) {
            return "MEDIUM";
        }
        
        return "LOW";
    }

    private long determineResponseTimeSLA(String priority, String alertType) {
        // SLA in minutes
        switch (priority) {
            case "CRITICAL":
                return CRITICAL_ALERT_TYPES.contains(alertType) ? 5 : 15; // 5-15 minutes
            case "HIGH":
                return 60; // 1 hour
            case "MEDIUM":
                return 240; // 4 hours
            case "LOW":
            default:
                return 1440; // 24 hours
        }
    }

    private boolean shouldEscalate(ComplianceAlert alert, String priority) {
        return "CRITICAL".equals(priority) ||
               CRITICAL_ALERT_TYPES.contains(alert.getAlertType()) ||
               alert.getEnhancedRiskScore() >= 85;
    }

    private boolean shouldInvestigate(ComplianceAlert alert) {
        return alert.getEnhancedRiskScore() >= 60 ||
               alert.getSeverity().ordinal() >= AlertSeverity.MEDIUM.ordinal() ||
               (alert.getRelatedAlerts() != null && alert.getRelatedAlerts().size() > 1);
    }

    private boolean shouldTakeImmediateAction(ComplianceAlert alert) {
        return CRITICAL_ALERT_TYPES.contains(alert.getAlertType()) ||
               alert.isSanctionedCountry() ||
               alert.getEnhancedRiskScore() >= 90;
    }

    private boolean hasAutomatedActions(String alertType) {
        return Arrays.asList("SANCTIONS_HIT", "FRAUD_CONFIRMED", "VELOCITY_LIMIT_EXCEEDED",
                           "ACCOUNT_TAKEOVER", "SUSPICIOUS_LOGIN").contains(alertType);
    }

    private Set<String> determineNotificationChannels(ComplianceAlert alert, String priority) {
        Set<String> channels = new HashSet<>();
        
        channels.add("EMAIL"); // Always send email
        
        if ("CRITICAL".equals(priority)) {
            channels.add("SMS");
            channels.add("SLACK");
            channels.add("PAGERDUTY");
        } else if ("HIGH".equals(priority)) {
            channels.add("SLACK");
        }
        
        // Special channels for specific alert types
        if (CRITICAL_ALERT_TYPES.contains(alert.getAlertType())) {
            channels.add("EXECUTIVE_DASHBOARD");
            channels.add("REGULATORY_PORTAL");
        }
        
        return channels;
    }

    private String determineAssignedTeam(String alertType) {
        Map<String, String> teamMapping = Map.of(
            "SANCTIONS_HIT", "SANCTIONS_TEAM",
            "MONEY_LAUNDERING", "AML_TEAM",
            "TERRORIST_FINANCING", "AML_TEAM",
            "FRAUD_CONFIRMED", "FRAUD_TEAM",
            "KYC_VIOLATION", "KYC_TEAM",
            "REGULATORY_BREACH", "COMPLIANCE_TEAM"
        );
        
        return teamMapping.getOrDefault(alertType, "COMPLIANCE_TEAM");
    }

    private String determineAssignedUser(ComplianceAlert alert, String priority) {
        // For critical alerts, assign to senior analysts
        if ("CRITICAL".equals(priority)) {
            return assignmentService.getSeniorAnalyst(determineAssignedTeam(alert.getAlertType()));
        }
        
        // Round-robin assignment for other priorities
        return assignmentService.getNextAvailableAnalyst(determineAssignedTeam(alert.getAlertType()));
    }

    private AlertProcessingResult processAlert(ComplianceAlert alert, AlertClassification classification) {
        AlertProcessingResult result = new AlertProcessingResult();
        result.setAlertId(alert.getAlertId());
        result.setProcessingStartTime(Instant.now());
        
        try {
            // Save alert to database
            ComplianceAlert savedAlert = alertRepository.save(alert);
            result.setSavedAlert(savedAlert);
            
            // Perform immediate actions if required
            if (classification.isRequiresImmediateAction()) {
                ImmediateActionResult actionResult = performImmediateActions(alert);
                result.setImmediateActionResult(actionResult);
            }
            
            // Update alert management system
            alertManagementService.registerAlert(alert, classification);
            
            // Start SLA tracking
            startSLATracking(alert, classification);
            
            result.setStatus(ProcessingStatus.COMPLETED);
            
        } catch (Exception e) {
            log.error("Failed to process alert: {}", alert.getAlertId(), e);
            result.setStatus(ProcessingStatus.FAILED);
            result.setErrorMessage(e.getMessage());
            throw new AlertProcessingException("Alert processing failed", e);
        }
        
        result.setProcessingEndTime(Instant.now());
        result.setProcessingTimeMs(
            ChronoUnit.MILLIS.between(result.getProcessingStartTime(), result.getProcessingEndTime())
        );
        
        return result;
    }

    private ImmediateActionResult performImmediateActions(ComplianceAlert alert) {
        ImmediateActionResult result = new ImmediateActionResult();
        List<String> actionsPerformed = new ArrayList<>();
        
        switch (alert.getAlertType()) {
            case "SANCTIONS_HIT":
                // Freeze accounts and block transactions
                if (alert.getCustomerId() != null) {
                    accountService.freezeCustomerAccounts(alert.getCustomerId(), "SANCTIONS_HIT");
                    actionsPerformed.add("CUSTOMER_ACCOUNTS_FROZEN");
                }
                if (alert.getTransactionId() != null) {
                    transactionService.blockTransaction(alert.getTransactionId(), "SANCTIONS_HIT");
                    actionsPerformed.add("TRANSACTION_BLOCKED");
                }
                break;
                
            case "FRAUD_CONFIRMED":
                // Block transactions and flag account
                if (alert.getTransactionId() != null) {
                    transactionService.blockTransaction(alert.getTransactionId(), "FRAUD");
                    actionsPerformed.add("TRANSACTION_BLOCKED");
                }
                if (alert.getCustomerId() != null) {
                    accountService.flagAccount(alert.getCustomerId(), "FRAUD_CONFIRMED");
                    actionsPerformed.add("ACCOUNT_FLAGGED");
                }
                break;
                
            case "TERRORIST_FINANCING":
                // Immediate freeze and report
                if (alert.getCustomerId() != null) {
                    accountService.freezeCustomerAccounts(alert.getCustomerId(), "TERRORIST_FINANCING");
                    actionsPerformed.add("CUSTOMER_ACCOUNTS_FROZEN");
                }
                regulatoryService.fileUrgentSAR(alert);
                actionsPerformed.add("URGENT_SAR_FILED");
                break;
                
            case "VELOCITY_LIMIT_EXCEEDED":
                // Temporary transaction suspension
                if (alert.getCustomerId() != null) {
                    accountService.suspendTransactions(alert.getCustomerId(), "VELOCITY_LIMIT", 24);
                    actionsPerformed.add("TRANSACTIONS_SUSPENDED_24H");
                }
                break;
        }
        
        result.setActionsPerformed(actionsPerformed);
        result.setActionTimestamp(Instant.now());
        
        return result;
    }

    private void startSLATracking(ComplianceAlert alert, AlertClassification classification) {
        SLATracker tracker = SLATracker.builder()
            .alertId(alert.getAlertId())
            .startTime(Instant.now())
            .slaDeadline(Instant.now().plus(classification.getResponseTimeSLA(), ChronoUnit.MINUTES))
            .priority(classification.getPriority())
            .assignedUser(classification.getAssignedUser())
            .status("ACTIVE")
            .build();
        
        slaTrackingService.startTracking(tracker);
        
        // Schedule SLA breach notification
        scheduleService.scheduleNotification(
            tracker.getSlaDeadline(),
            "SLA_BREACH_WARNING",
            alert.getAlertId()
        );
    }

    private void handleEscalation(ComplianceAlert alert, AlertClassification classification, 
                                 AlertProcessingResult result) {
        
        EscalationRequest escalationRequest = EscalationRequest.builder()
            .alertId(alert.getAlertId())
            .escalationReason("AUTOMATIC_ESCALATION")
            .currentPriority(classification.getPriority())
            .escalatedToPriority("CRITICAL")
            .escalatedTo(getEscalationTarget(alert.getAlertType()))
            .escalationTime(Instant.now())
            .triggerData(result.toMap())
            .build();
        
        escalationService.escalateAlert(escalationRequest);
        
        // Update alert priority
        alert.setPriority(AlertPriority.CRITICAL);
        alert.setEscalated(true);
        alert.setEscalationTime(Instant.now());
        alertRepository.save(alert);
    }

    private String getEscalationTarget(String alertType) {
        Map<String, String> escalationTargets = Map.of(
            "SANCTIONS_HIT", "CHIEF_COMPLIANCE_OFFICER",
            "TERRORIST_FINANCING", "CHIEF_COMPLIANCE_OFFICER",
            "MONEY_LAUNDERING", "AML_DIRECTOR",
            "FRAUD_CONFIRMED", "FRAUD_DIRECTOR",
            "REGULATORY_BREACH", "CHIEF_COMPLIANCE_OFFICER"
        );
        
        return escalationTargets.getOrDefault(alertType, "COMPLIANCE_MANAGER");
    }

    private void createInvestigationCase(ComplianceAlert alert, AlertProcessingResult result) {
        ComplianceCase investigationCase = ComplianceCase.builder()
            .caseId(UUID.randomUUID().toString())
            .alertId(alert.getAlertId())
            .caseType("INVESTIGATION")
            .priority(alert.getPriority())
            .status("OPEN")
            .entityId(alert.getEntityId())
            .entityType(alert.getEntityType())
            .assignedTo(alert.getAssignedUser())
            .createdAt(Instant.now())
            .description("Investigation case for compliance alert: " + alert.getAlertType())
            .metadata(alert.getMetadata())
            .build();
        
        caseManagementService.createCase(investigationCase);
        
        // Link case to alert
        alert.setInvestigationCaseId(investigationCase.getCaseId());
        alertRepository.save(alert);
    }

    private void triggerAutomatedWorkflows(ComplianceAlert alert, AlertClassification classification) {
        List<String> workflows = getAutomatedWorkflows(alert.getAlertType());
        
        for (String workflowType : workflows) {
            CompletableFuture.runAsync(() -> {
                try {
                    workflowService.triggerWorkflow(workflowType, alert, classification);
                } catch (Exception e) {
                    log.error("Failed to trigger workflow {} for alert {}", 
                             workflowType, alert.getAlertId(), e);
                }
            });
        }
    }

    private List<String> getAutomatedWorkflows(String alertType) {
        Map<String, List<String>> workflowMapping = Map.of(
            "SANCTIONS_HIT", Arrays.asList("SANCTIONS_INVESTIGATION", "OFAC_REPORTING"),
            "FRAUD_CONFIRMED", Arrays.asList("FRAUD_INVESTIGATION", "CHARGEBACK_PREVENTION"),
            "MONEY_LAUNDERING", Arrays.asList("AML_INVESTIGATION", "SAR_PREPARATION"),
            "KYC_VIOLATION", Arrays.asList("KYC_REMEDIATION", "CUSTOMER_OUTREACH")
        );
        
        return workflowMapping.getOrDefault(alertType, Arrays.asList("STANDARD_INVESTIGATION"));
    }

    private void sendAlertNotifications(ComplianceAlert alert, AlertClassification classification, 
                                       AlertProcessingResult result) {
        
        Map<String, Object> notificationData = Map.of(
            "alertId", alert.getAlertId(),
            "alertType", alert.getAlertType(),
            "severity", alert.getSeverity().toString(),
            "priority", classification.getPriority(),
            "entityId", alert.getEntityId(),
            "riskScore", alert.getEnhancedRiskScore(),
            "assignedTo", classification.getAssignedUser(),
            "slaDeadline", Instant.now().plus(classification.getResponseTimeSLA(), ChronoUnit.MINUTES)
        );
        
        // Send notifications via configured channels
        for (String channel : classification.getNotificationChannels()) {
            CompletableFuture.runAsync(() -> {
                try {
                    switch (channel) {
                        case "EMAIL":
                            notificationService.sendEmailAlert(classification.getAssignedUser(), notificationData);
                            break;
                        case "SMS":
                            notificationService.sendSMSAlert(classification.getAssignedUser(), notificationData);
                            break;
                        case "SLACK":
                            notificationService.sendSlackAlert(classification.getAssignedTeam(), notificationData);
                            break;
                        case "PAGERDUTY":
                            notificationService.sendPagerDutyAlert(notificationData);
                            break;
                        case "EXECUTIVE_DASHBOARD":
                            dashboardService.updateExecutiveDashboard(notificationData);
                            break;
                        case "REGULATORY_PORTAL":
                            regulatoryService.updateRegulatoryPortal(notificationData);
                            break;
                    }
                } catch (Exception e) {
                    log.error("Failed to send {} notification for alert {}", 
                             channel, alert.getAlertId(), e);
                }
            });
        }
    }

    private void updateAlertTracking(ComplianceAlert alert, AlertProcessingResult result) {
        // Update alert status
        alert.setStatus(AlertStatus.IN_PROGRESS);
        alert.setProcessedAt(Instant.now());
        alert.setProcessingResult(result.toJson());
        
        alertRepository.save(alert);
        
        // Update metrics and dashboards
        metricsService.updateAlertMetrics(alert);
        dashboardService.updateAlertDashboard(alert);
    }

    private void auditAlertProcessing(ComplianceAlert alert, AlertProcessingResult result, 
                                     GenericKafkaEvent originalEvent) {
        auditService.auditComplianceAlert(
            alert.getAlertId(),
            alert.getAlertType(),
            alert.getEntityId(),
            result.getStatus().toString(),
            alert.getAssignedUser(),
            originalEvent.getEventId()
        );
    }

    private void recordAlertMetrics(ComplianceAlert alert, AlertProcessingResult result, long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        metricsService.recordAlertProcessingMetrics(
            alert.getAlertType(),
            alert.getSeverity().toString(),
            alert.getPriority().toString(),
            processingTime,
            processingTime <= SLA_THRESHOLD_MS
        );
        
        // Record alert volume metrics
        metricsService.recordAlertVolumeMetrics(
            alert.getAlertType(),
            alert.getSourceSystem()
        );
    }

    // Error handling methods
    private void handleValidationError(GenericKafkaEvent event, ValidationException e) {
        auditService.logValidationError(event.getEventId(), e.getMessage());
        kafkaTemplate.send("compliance-alert-validation-errors", event);
    }

    private void handleCriticalAlertError(GenericKafkaEvent event, CriticalAlertException e) {
        // Create emergency alert for failed critical alert processing
        emergencyAlertService.createEmergencyAlert(
            "CRITICAL_ALERT_PROCESSING_FAILED",
            event.getPayload(),
            e.getMessage()
        );
        
        kafkaTemplate.send("compliance-alert-critical-failures", event);
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;
            
            log.warn("Retrying compliance alert {} after {}ms (attempt {})", 
                    eventId, retryDelay, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            scheduledExecutor.schedule(() -> {
                kafkaTemplate.send("compliance-alerts-retry", event);
            }, retryDelay, TimeUnit.MILLISECONDS);
            
            acknowledgment.acknowledge();
        } else {
            log.error("Max retries exceeded for compliance alert {}, sending to DLQ", eventId);
            sendToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "compliance-alerts");
        
        kafkaTemplate.send("compliance-alerts.DLQ", event);
        
        alertingService.createDLQAlert(
            "compliance-alerts",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handleComplianceAlertFailure(GenericKafkaEvent event, String topic, int partition,
                                            long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for compliance alert processing: {}", e.getMessage());
        
        failedEventRepository.save(
            FailedEvent.builder()
                .eventId(event.getEventId())
                .topic(topic)
                .payload(event)
                .errorMessage(e.getMessage())
                .createdAt(Instant.now())
                .build()
        );
        
        alertingService.sendCriticalAlert(
            "Compliance Alerts Circuit Breaker Open",
            "Compliance alert processing is failing. Regulatory response capability compromised."
        );
        
        acknowledgment.acknowledge();
    }

    // Helper extraction methods
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return new BigDecimal(value.toString());
    }

    private Integer extractInteger(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    // Custom exceptions
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static class CriticalAlertException extends RuntimeException {
        public CriticalAlertException(String message) {
            super(message);
        }
    }

    public static class AlertProcessingException extends RuntimeException {
        public AlertProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}