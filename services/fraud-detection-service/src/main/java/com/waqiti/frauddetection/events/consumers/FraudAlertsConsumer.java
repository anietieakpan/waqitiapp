package com.waqiti.frauddetection.events.consumers;

import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.events.fraud.FraudAlertEvent;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.frauddetection.domain.FraudAlert;
import com.waqiti.frauddetection.domain.FraudCase;
import com.waqiti.frauddetection.domain.AlertSeverity;
import com.waqiti.frauddetection.domain.AlertStatus;
import com.waqiti.frauddetection.repository.FraudAlertRepository;
import com.waqiti.frauddetection.repository.FraudCaseRepository;
import com.waqiti.frauddetection.service.FraudInvestigationService;
import com.waqiti.frauddetection.service.FraudNotificationService;
import com.waqiti.frauddetection.service.MachineLearningFraudService;
import com.waqiti.frauddetection.service.AccountSecurityService;
import com.waqiti.common.math.MoneyMath;
import com.waqiti.common.exceptions.FraudProcessingException;

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
import java.util.Map;
import java.util.UUID;
import java.util.List;

/**
 * Production-grade consumer for fraud alert events.
 * Handles real-time fraud detection and response with:
 * - Immediate threat assessment and risk scoring
 * - Automated account protection measures
 * - Real-time fraud investigation workflow
 * - Machine learning fraud pattern analysis
 * - Regulatory compliance reporting
 * - Multi-channel alert notifications
 * 
 * Critical for financial security and regulatory compliance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FraudAlertsConsumer {

    private final FraudAlertRepository alertRepository;
    private final FraudCaseRepository caseRepository;
    private final FraudInvestigationService investigationService;
    private final FraudNotificationService notificationService;
    private final MachineLearningFraudService mlService;
    private final AccountSecurityService securityService;
    private final AuditLogger auditLogger;
    private final MetricsService metricsService;

    @KafkaListener(
        topics = "fraud-alerts",
        groupId = "fraud-detection-service-alert-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 500, multiplier = 2.0),
        include = {FraudProcessingException.class}
    )
    @Transactional(rollbackFor = Exception.class)
    public void handleFraudAlert(
            @Payload FraudAlertEvent alertEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "correlation-id", required = false) String correlationId,
            @Header(value = "alert-priority", required = false) String priority,
            Acknowledgment acknowledgment) {

        String eventId = alertEvent.getEventId() != null ? 
            alertEvent.getEventId() : UUID.randomUUID().toString();

        try {
            log.info("Processing fraud alert event: {} for entity: {} with severity: {}", 
                    eventId, alertEvent.getEntityId(), alertEvent.getSeverity());

            // Metrics tracking
            metricsService.incrementCounter("fraud.alert.processing.started",
                Map.of(
                    "severity", alertEvent.getSeverity().toString(),
                    "alert_type", alertEvent.getAlertType()
                ));

            // Idempotency check
            if (isFraudAlertProcessed(alertEvent.getEntityId(), eventId)) {
                log.info("Fraud alert {} already processed for entity {}", eventId, alertEvent.getEntityId());
                acknowledgment.acknowledge();
                return;
            }

            // Create fraud alert record
            FraudAlert fraudAlert = createFraudAlert(alertEvent, eventId, correlationId);

            // Enhanced risk assessment with ML
            enhanceAlertWithMLAnalysis(fraudAlert, alertEvent);

            // Save alert with risk assessment
            FraudAlert savedAlert = alertRepository.save(fraudAlert);

            // Take immediate protective actions
            executeImmediateProtectiveActions(savedAlert, alertEvent);

            // Create or update fraud case
            FraudCase fraudCase = createOrUpdateFraudCase(savedAlert, alertEvent);

            // Send real-time notifications
            sendFraudNotifications(savedAlert, fraudCase, alertEvent);

            // Update fraud analytics and patterns
            updateFraudAnalytics(savedAlert, alertEvent);

            // Regulatory compliance reporting
            performComplianceReporting(savedAlert, fraudCase);

            // Create comprehensive audit trail
            createFraudAuditLog(savedAlert, fraudCase, alertEvent, correlationId);

            // Success metrics
            metricsService.incrementCounter("fraud.alert.processing.success",
                Map.of(
                    "severity", alertEvent.getSeverity().toString(),
                    "action_taken", savedAlert.getActionTaken()
                ));

            log.info("Successfully processed fraud alert: {} for entity: {} with risk score: {}", 
                    savedAlert.getId(), alertEvent.getEntityId(), savedAlert.getRiskScore());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing fraud alert event {}: {}", eventId, e.getMessage(), e);
            metricsService.incrementCounter("fraud.alert.processing.error",
                Map.of("error_type", e.getClass().getSimpleName()));
            
            // Create error audit log
            auditLogger.logError("FRAUD_ALERT_PROCESSING_ERROR", 
                "system", eventId, e.getMessage(),
                Map.of(
                    "entityId", alertEvent.getEntityId(),
                    "alertType", alertEvent.getAlertType(),
                    "correlationId", correlationId
                ));
            
            throw new FraudProcessingException("Failed to process fraud alert: " + e.getMessage(), e);
        }
    }

    /**
     * High-priority fraud alerts processor for critical threats
     */
    @KafkaListener(
        topics = "fraud-alerts-critical",
        groupId = "fraud-detection-service-critical-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleCriticalFraudAlert(
            @Payload FraudAlertEvent alertEvent,
            @Header(value = "correlation-id", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        try {
            log.warn("CRITICAL FRAUD ALERT: Processing high-priority fraud event: {} for entity: {}", 
                    alertEvent.getEventId(), alertEvent.getEntityId());

            // Immediate account protection for critical alerts
            securityService.executeEmergencyProtection(alertEvent.getEntityId(), alertEvent.getAlertType());

            // Process with highest priority
            handleFraudAlert(alertEvent, "fraud-alerts-critical", correlationId, "CRITICAL", acknowledgment);

            // Send immediate escalation
            notificationService.sendCriticalFraudEscalation(alertEvent);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process critical fraud alert: {}", e.getMessage(), e);
            acknowledgment.acknowledge(); // Acknowledge to prevent blocking critical queue
        }
    }

    private boolean isFraudAlertProcessed(String entityId, String eventId) {
        return alertRepository.existsByEntityIdAndEventId(entityId, eventId);
    }

    private FraudAlert createFraudAlert(FraudAlertEvent event, String eventId, String correlationId) {
        return FraudAlert.builder()
            .id(UUID.randomUUID().toString())
            .eventId(eventId)
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .alertType(event.getAlertType())
            .severity(AlertSeverity.valueOf(event.getSeverity().toUpperCase()))
            .status(AlertStatus.NEW)
            .riskScore(event.getRiskScore())
            .description(event.getDescription())
            .fraudIndicators(event.getFraudIndicators())
            .transactionId(event.getTransactionId())
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .sourceIp(event.getSourceIp())
            .userAgent(event.getUserAgent())
            .deviceFingerprint(event.getDeviceFingerprint())
            .geolocation(event.getGeolocation())
            .timestamp(event.getTimestamp() != null ? event.getTimestamp() : LocalDateTime.now())
            .correlationId(correlationId)
            .metadata(event.getMetadata())
            .requiresImmediateAction(shouldTakeImmediateAction(event))
            .escalationLevel(calculateEscalationLevel(event))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private void enhanceAlertWithMLAnalysis(FraudAlert alert, FraudAlertEvent event) {
        try {
            // Run ML analysis for enhanced risk assessment
            var mlAnalysis = mlService.analyzeFraudAlert(alert, event);
            
            // Update alert with ML insights
            alert.setMlRiskScore(mlAnalysis.getRiskScore());
            alert.setMlConfidence(mlAnalysis.getConfidence());
            alert.setMlFeatures(mlAnalysis.getSignificantFeatures());
            alert.setSimilarPatterns(mlAnalysis.getSimilarPatterns());
            
            // Adjust severity based on ML analysis
            if (mlAnalysis.getRiskScore() > 0.9 && alert.getSeverity() != AlertSeverity.CRITICAL) {
                alert.setSeverity(AlertSeverity.CRITICAL);
                alert.setRequiresImmediateAction(true);
                
                log.warn("ML analysis upgraded alert {} to CRITICAL severity with score: {}", 
                        alert.getId(), mlAnalysis.getRiskScore());
            }
            
        } catch (Exception e) {
            log.error("ML analysis failed for fraud alert {}: {}", alert.getId(), e.getMessage());
            // Don't fail the entire process, but flag for manual review
            alert.setRequiresManualReview(true);
        }
    }

    private void executeImmediateProtectiveActions(FraudAlert alert, FraudAlertEvent event) {
        if (!alert.getRequiresImmediateAction()) {
            return;
        }

        try {
            String actionTaken = "NONE";
            
            // Critical risk - freeze account
            if (alert.getRiskScore() > 0.9 || alert.getSeverity() == AlertSeverity.CRITICAL) {
                securityService.freezeAccount(alert.getEntityId(), "High fraud risk detected");
                actionTaken = "ACCOUNT_FROZEN";
                
                log.warn("Account {} frozen due to critical fraud risk: {}", 
                        alert.getEntityId(), alert.getRiskScore());
            }
            // High risk - restrict transactions
            else if (alert.getRiskScore() > 0.7) {
                securityService.restrictTransactions(alert.getEntityId(), "Elevated fraud risk");
                actionTaken = "TRANSACTIONS_RESTRICTED";
                
                log.info("Transactions restricted for account {} due to elevated fraud risk: {}", 
                        alert.getEntityId(), alert.getRiskScore());
            }
            // Medium risk - require additional verification
            else if (alert.getRiskScore() > 0.5) {
                securityService.requireAdditionalVerification(alert.getEntityId());
                actionTaken = "ADDITIONAL_VERIFICATION_REQUIRED";
            }
            
            alert.setActionTaken(actionTaken);
            alert.setActionTimestamp(LocalDateTime.now());
            
            // Update metrics
            metricsService.incrementCounter("fraud.protective_action.taken",
                Map.of("action", actionTaken, "risk_score", categorizeRiskScore(alert.getRiskScore())));
            
        } catch (Exception e) {
            log.error("Failed to execute protective actions for alert {}: {}", alert.getId(), e.getMessage());
            alert.setActionTaken("FAILED");
            alert.setErrorMessage(e.getMessage());
        }
    }

    private FraudCase createOrUpdateFraudCase(FraudAlert alert, FraudAlertEvent event) {
        // Check if there's an existing case for this entity
        FraudCase existingCase = caseRepository.findActiveByEntityId(alert.getEntityId());
        
        if (existingCase != null) {
            // Update existing case
            existingCase.addAlert(alert.getId());
            existingCase.updateRiskScore(alert.getRiskScore());
            existingCase.setUpdatedAt(LocalDateTime.now());
            
            log.info("Updated existing fraud case {} with new alert {}", existingCase.getId(), alert.getId());
            return caseRepository.save(existingCase);
        } else {
            // Create new case
            FraudCase newCase = FraudCase.builder()
                .id(UUID.randomUUID().toString())
                .entityId(alert.getEntityId())
                .entityType(alert.getEntityType())
                .caseType("FRAUD_INVESTIGATION")
                .status("OPEN")
                .priority(mapSeverityToPriority(alert.getSeverity()))
                .totalRiskScore(alert.getRiskScore())
                .alertIds(List.of(alert.getId()))
                .assignedInvestigator(null) // Will be assigned by workflow
                .description(String.format("Fraud case opened for %s alerts", alert.getAlertType()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
            
            log.info("Created new fraud case {} for alert {}", newCase.getId(), alert.getId());
            return caseRepository.save(newCase);
        }
    }

    private void sendFraudNotifications(FraudAlert alert, FraudCase fraudCase, FraudAlertEvent event) {
        try {
            // Send to fraud investigation team
            notificationService.sendFraudTeamAlert(alert, fraudCase);
            
            // Critical alerts require immediate escalation
            if (alert.getSeverity() == AlertSeverity.CRITICAL) {
                notificationService.sendFraudManagerEscalation(alert, fraudCase);
                notificationService.sendSMSAlert(alert);
            }
            
            // High-value alerts require special attention
            BigDecimal highValueThreshold = new BigDecimal("10000.0");
            if (alert.getAmount() != null && alert.getAmount().compareTo(highValueThreshold) > 0) {
                notificationService.sendHighValueFraudAlert(alert, fraudCase);
            }
            
            // Send customer notification if account action was taken
            if (alert.getActionTaken() != null && !alert.getActionTaken().equals("NONE")) {
                notificationService.sendCustomerSecurityAlert(alert);
            }
            
        } catch (Exception e) {
            log.error("Failed to send fraud notifications for alert {}: {}", alert.getId(), e.getMessage());
            // Don't fail processing for notification issues
        }
    }

    private void updateFraudAnalytics(FraudAlert alert, FraudAlertEvent event) {
        try {
            // Update fraud pattern analytics
            mlService.updateFraudPatterns(alert, event);
            
            // Record fraud metrics
            metricsService.recordGauge("fraud.risk_score", alert.getRiskScore(),
                Map.of(
                    "alert_type", alert.getAlertType(),
                    "entity_type", alert.getEntityType()
                ));
            
            // Update fraud counters
            metricsService.incrementCounter("fraud.alerts.by_type",
                Map.of(
                    "type", alert.getAlertType(),
                    "severity", alert.getSeverity().toString()
                ));
            
            // Geographic fraud tracking
            if (alert.getGeolocation() != null) {
                metricsService.incrementCounter("fraud.geographic",
                    Map.of("location", extractCountryFromGeolocation(alert.getGeolocation())));
            }
            
        } catch (Exception e) {
            log.error("Failed to update fraud analytics for alert {}: {}", alert.getId(), e.getMessage());
        }
    }

    private void performComplianceReporting(FraudAlert alert, FraudCase fraudCase) {
        try {
            // High-risk alerts require regulatory reporting
            if (alert.getRiskScore() > 0.8 || alert.getSeverity() == AlertSeverity.CRITICAL) {
                investigationService.generateRegulatoryReport(alert, fraudCase);
            }
            
            // Large amount alerts require SAR consideration
            BigDecimal sarThreshold = new BigDecimal("10000.0");
            if (alert.getAmount() != null && alert.getAmount().compareTo(sarThreshold) > 0) {
                investigationService.evaluateForSAR(alert, fraudCase);
            }
            
        } catch (Exception e) {
            log.error("Compliance reporting failed for alert {}: {}", alert.getId(), e.getMessage());
            // Flag for manual compliance review
            alert.setRequiresManualReview(true);
        }
    }

    private void createFraudAuditLog(FraudAlert alert, FraudCase fraudCase, 
                                   FraudAlertEvent event, String correlationId) {
        auditLogger.logSecurityEvent(
            "FRAUD_ALERT_PROCESSED",
            "system",
            alert.getId(),
            alert.getRiskScore(),
            "fraud_detector",
            true,
            Map.of(
                "entityId", alert.getEntityId(),
                "alertType", alert.getAlertType(),
                "severity", alert.getSeverity().toString(),
                "actionTaken", alert.getActionTaken() != null ? alert.getActionTaken() : "NONE",
                "caseId", fraudCase.getId(),
                "riskScore", alert.getRiskScore().toString(),
                "mlRiskScore", alert.getMlRiskScore() != null ? alert.getMlRiskScore().toString() : "N/A",
                "correlationId", correlationId != null ? correlationId : "N/A",
                "eventId", event.getEventId()
            )
        );
    }

    private boolean shouldTakeImmediateAction(FraudAlertEvent event) {
        BigDecimal immediateActionThreshold = new BigDecimal("5000.0");
        return event.getRiskScore() > 0.7 ||
               event.getSeverity().equals("CRITICAL") ||
               (event.getAmount() != null && event.getAmount().compareTo(immediateActionThreshold) > 0);
    }

    private Integer calculateEscalationLevel(FraudAlertEvent event) {
        if (event.getRiskScore() > 0.9 || event.getSeverity().equals("CRITICAL")) {
            return 3; // Immediate escalation
        } else if (event.getRiskScore() > 0.7 || event.getSeverity().equals("HIGH")) {
            return 2; // Standard escalation
        } else {
            return 1; // Normal processing
        }
    }

    private String mapSeverityToPriority(AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "CRITICAL";
            case HIGH -> "HIGH";
            case MEDIUM -> "MEDIUM";
            case LOW -> "LOW";
        };
    }

    private String categorizeRiskScore(Double riskScore) {
        if (riskScore > 0.8) return "high";
        else if (riskScore > 0.5) return "medium";
        else return "low";
    }

    private String extractCountryFromGeolocation(String geolocation) {
        // Simple extraction - in production this would be more sophisticated
        try {
            String[] parts = geolocation.split(",");
            return parts.length > 2 ? parts[2].trim() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}