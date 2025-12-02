package com.waqiti.compliance.events.consumers;

import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.events.compliance.SARFilingEvent;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.compliance.domain.SuspiciousActivityReport;
import com.waqiti.compliance.domain.SARStatus;
import com.waqiti.compliance.domain.SARType;
import com.waqiti.compliance.repository.SARRepository;
import com.waqiti.compliance.service.RegulatoryReportingService;
import com.waqiti.compliance.service.FINCENIntegrationService;
import com.waqiti.compliance.service.ComplianceNotificationService;
import com.waqiti.common.exceptions.SARProcessingException;

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
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Production-grade consumer for Suspicious Activity Report (SAR) filing events.
 * Handles critical regulatory compliance requirements including:
 * - Automated SAR generation and filing
 * - FINCEN integration and submission
 * - Regulatory deadline management
 * - Compliance team notifications
 * - Audit trail for regulatory examination
 * 
 * Critical for AML compliance and regulatory protection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SARFilingConsumer {

    private final SARRepository sarRepository;
    private final RegulatoryReportingService reportingService;
    private final FINCENIntegrationService fincenService;
    private final ComplianceNotificationService notificationService;
    private final AuditLogger auditLogger;
    private final MetricsService metricsService;

    @KafkaListener(
        topics = "sar-filing-requests",
        groupId = "compliance-service-sar-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0),
        include = {SARProcessingException.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void handleSARFilingRequest(
            @Payload SARFilingEvent sarEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "correlation-id", required = false) String correlationId,
            @Header(value = "priority", required = false) String priority,
            Acknowledgment acknowledgment) {

        String eventId = sarEvent.getEventId() != null ? 
            sarEvent.getEventId() : UUID.randomUUID().toString();

        try {
            log.info("Processing SAR filing request: {} for entity: {} with type: {}", 
                    eventId, sarEvent.getEntityId(), sarEvent.getSarType());

            // Metrics tracking
            metricsService.incrementCounter("sar.filing.processing.started",
                Map.of(
                    "sar_type", sarEvent.getSarType(),
                    "urgency", priority != null ? priority : "normal"
                ));

            // Idempotency check
            if (isSARAlreadyProcessed(sarEvent.getEntityId(), eventId)) {
                log.info("SAR filing {} already processed for entity {}", eventId, sarEvent.getEntityId());
                acknowledgment.acknowledge();
                return;
            }

            // Create SAR record
            SuspiciousActivityReport sar = createSARRecord(sarEvent, eventId, correlationId);

            // Validate SAR requirements
            validateSARRequirements(sar, sarEvent);

            // Generate comprehensive SAR report
            generateSARReport(sar, sarEvent);

            // Submit to FINCEN if required
            submitToFINCEN(sar, sarEvent);

            // Save SAR with all data
            SuspiciousActivityReport savedSAR = sarRepository.save(sar);

            // Send compliance notifications
            sendComplianceNotifications(savedSAR, sarEvent);

            // Update regulatory analytics
            updateRegulatoryAnalytics(savedSAR, sarEvent);

            // Create comprehensive audit trail
            createSARAuditLog(savedSAR, sarEvent, correlationId);

            // Success metrics
            metricsService.incrementCounter("sar.filing.processing.success",
                Map.of(
                    "sar_type", sarEvent.getSarType(),
                    "filed_to_fincen", String.valueOf(sar.isFiledToFINCEN())
                ));

            log.info("Successfully processed SAR filing: {} for entity: {} with BSA ID: {}", 
                    savedSAR.getId(), sarEvent.getEntityId(), savedSAR.getBsaId());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing SAR filing event {}: {}", eventId, e.getMessage(), e);
            metricsService.incrementCounter("sar.filing.processing.error");
            
            // Create critical audit log for SAR failures
            auditLogger.logCriticalAlert("SAR_FILING_PROCESSING_ERROR",
                "Critical SAR filing failure - regulatory compliance at risk",
                Map.of(
                    "entityId", sarEvent.getEntityId(),
                    "sarType", sarEvent.getSarType(),
                    "eventId", eventId,
                    "error", e.getMessage(),
                    "correlationId", correlationId
                ));
            
            throw new SARProcessingException("Failed to process SAR filing: " + e.getMessage(), e);
        }
    }

    @KafkaListener(
        topics = "sar-filing-emergency",
        groupId = "compliance-service-sar-emergency-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleEmergencySARFiling(
            @Payload SARFilingEvent sarEvent,
            @Header(value = "correlation-id", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        try {
            log.warn("EMERGENCY SAR FILING: Processing urgent SAR: {} for entity: {}", 
                    sarEvent.getEventId(), sarEvent.getEntityId());

            // Emergency SAR processing with expedited timeline
            handleSARFilingRequest(sarEvent, "sar-filing-emergency", correlationId, "EMERGENCY", acknowledgment);

            // Send immediate escalation to compliance leadership
            notificationService.sendEmergencySARAlert(sarEvent);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process emergency SAR filing: {}", e.getMessage(), e);
            acknowledgment.acknowledge(); // Acknowledge to prevent blocking emergency queue
        }
    }

    private boolean isSARAlreadyProcessed(String entityId, String eventId) {
        return sarRepository.existsByEntityIdAndEventId(entityId, eventId);
    }

    private SuspiciousActivityReport createSARRecord(SARFilingEvent event, String eventId, String correlationId) {
        return SuspiciousActivityReport.builder()
            .id(UUID.randomUUID().toString())
            .eventId(eventId)
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .sarType(SARType.valueOf(event.getSarType().toUpperCase()))
            .status(SARStatus.INITIATED)
            .suspiciousActivity(event.getSuspiciousActivity())
            .activityDescription(event.getActivityDescription())
            .transactionIds(event.getTransactionIds())
            .totalAmount(event.getTotalAmount())
            .currency(event.getCurrency())
            .activityStartDate(event.getActivityStartDate())
            .activityEndDate(event.getActivityEndDate())
            .detectionMethod(event.getDetectionMethod())
            .riskScore(event.getRiskScore())
            .evidenceDocuments(event.getEvidenceDocuments())
            .correlationId(correlationId)
            .filingDeadline(calculateFilingDeadline(event))
            .requiredFINCENFiling(requiresFINCENFiling(event))
            .priority(calculateSARPriority(event))
            .complianceOfficer(determineComplianceOfficer(event))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private void validateSARRequirements(SuspiciousActivityReport sar, SARFilingEvent event) {
        // Validate minimum SAR requirements
        if (sar.getTotalAmount() == null || sar.getTotalAmount().doubleValue() < 5000.0) {
            if (!isExemptFromAmountThreshold(event)) {
                throw new SARProcessingException("SAR amount below regulatory threshold: " + sar.getTotalAmount());
            }
        }

        // Validate required fields
        if (sar.getSuspiciousActivity() == null || sar.getSuspiciousActivity().trim().isEmpty()) {
            throw new SARProcessingException("Suspicious activity description is required for SAR filing");
        }

        // Validate timeline requirements
        if (sar.getActivityStartDate() == null) {
            throw new SARProcessingException("Activity start date is required for SAR filing");
        }

        // Check if filing deadline is within regulatory requirements (30 days)
        if (sar.getFilingDeadline().isBefore(LocalDateTime.now().plusDays(5))) {
            log.warn("SAR filing deadline is approaching: {} for SAR {}", sar.getFilingDeadline(), sar.getId());
            sar.setPriority("HIGH");
        }
    }

    private void generateSARReport(SuspiciousActivityReport sar, SARFilingEvent event) {
        try {
            // Generate comprehensive SAR narrative
            String narrative = reportingService.generateSARNarrative(sar, event);
            sar.setNarrative(narrative);

            // Generate supporting documentation
            var supportingDocs = reportingService.generateSupportingDocuments(sar, event);
            sar.setSupportingDocuments(supportingDocs);

            // Generate regulatory forms
            var regulatoryForms = reportingService.generateRegulatoryForms(sar, event);
            sar.setRegulatoryForms(regulatoryForms);

            // Calculate final risk assessment
            Double finalRiskScore = reportingService.calculateFinalRiskScore(sar, event);
            sar.setFinalRiskScore(finalRiskScore);

            sar.setStatus(SARStatus.REPORT_GENERATED);
            sar.setReportGeneratedAt(LocalDateTime.now());

            log.info("SAR report generated successfully for: {}", sar.getId());

        } catch (Exception e) {
            log.error("Failed to generate SAR report for {}: {}", sar.getId(), e.getMessage());
            sar.setStatus(SARStatus.REPORT_FAILED);
            sar.setErrorMessage(e.getMessage());
            throw new SARProcessingException("SAR report generation failed", e);
        }
    }

    private void submitToFINCEN(SuspiciousActivityReport sar, SARFilingEvent event) {
        if (!sar.isRequiredFINCENFiling()) {
            log.info("SAR {} does not require FINCEN filing", sar.getId());
            return;
        }

        try {
            // Submit to FINCEN BSA E-Filing system
            var fincenResponse = fincenService.submitSAR(sar);
            
            sar.setBsaId(fincenResponse.getBsaId());
            sar.setFincenSubmissionId(fincenResponse.getSubmissionId());
            sar.setFiledToFINCEN(true);
            sar.setFincenFilingDate(LocalDateTime.now());
            sar.setStatus(SARStatus.FILED_TO_FINCEN);

            log.info("SAR {} successfully filed to FINCEN with BSA ID: {}", sar.getId(), sar.getBsaId());

        } catch (Exception e) {
            log.error("Failed to submit SAR {} to FINCEN: {}", sar.getId(), e.getMessage());
            sar.setStatus(SARStatus.FINCEN_FILING_FAILED);
            sar.setFincenError(e.getMessage());
            
            // Notify compliance team of filing failure
            notificationService.sendFINCENFilingFailureAlert(sar, e);
            
            // Don't fail the entire process - manual filing may be required
        }
    }

    private void sendComplianceNotifications(SuspiciousActivityReport sar, SARFilingEvent event) {
        try {
            // Notify compliance team
            notificationService.sendSARFilingNotification(sar);

            // Notify compliance officer
            if (sar.getComplianceOfficer() != null) {
                notificationService.sendComplianceOfficerAlert(sar);
            }

            // High-risk SARs require immediate escalation
            if (sar.getFinalRiskScore() != null && sar.getFinalRiskScore() > 0.8) {
                notificationService.sendHighRiskSARAlert(sar);
            }

            // FINCEN filing failures require immediate attention
            if (sar.getStatus() == SARStatus.FINCEN_FILING_FAILED) {
                notificationService.sendCriticalComplianceAlert(
                    "SAR FINCEN FILING FAILED",
                    String.format("SAR %s failed to file to FINCEN - manual intervention required", sar.getId()),
                    sar
                );
            }

        } catch (Exception e) {
            log.error("Failed to send SAR notifications for {}: {}", sar.getId(), e.getMessage());
            // Don't fail processing for notification issues
        }
    }

    private void updateRegulatoryAnalytics(SuspiciousActivityReport sar, SARFilingEvent event) {
        try {
            // Record SAR metrics
            metricsService.incrementCounter("sar.filed",
                Map.of(
                    "sar_type", sar.getSarType().toString(),
                    "filed_to_fincen", String.valueOf(sar.isFiledToFINCEN()),
                    "priority", sar.getPriority()
                ));

            // Record financial metrics
            if (sar.getTotalAmount() != null) {
                metricsService.recordTimer("sar.amount", sar.getTotalAmount().doubleValue(),
                    Map.of(
                        "currency", sar.getCurrency(),
                        "sar_type", sar.getSarType().toString()
                    ));
            }

            // Record compliance metrics
            if (sar.getFinalRiskScore() != null) {
                metricsService.recordGauge("sar.risk_score", sar.getFinalRiskScore(),
                    Map.of("sar_type", sar.getSarType().toString()));
            }

            // Update regulatory dashboard metrics
            metricsService.incrementCounter("regulatory.compliance.sar_filings",
                Map.of("month", LocalDateTime.now().getMonth().toString()));

        } catch (Exception e) {
            log.error("Failed to update SAR analytics for {}: {}", sar.getId(), e.getMessage());
        }
    }

    private void createSARAuditLog(SuspiciousActivityReport sar, SARFilingEvent event, String correlationId) {
        auditLogger.logRegulatoryEvent(
            "SAR_FILED",
            sar.getComplianceOfficer() != null ? sar.getComplianceOfficer() : "system",
            sar.getId(),
            sar.getSarType().toString(),
            sar.getTotalAmount() != null ? sar.getTotalAmount().doubleValue() : 0.0,
            "sar_processor",
            sar.getStatus() == SARStatus.FILED_TO_FINCEN,
            Map.of(
                "entityId", sar.getEntityId(),
                "sarType", sar.getSarType().toString(),
                "bsaId", sar.getBsaId() != null ? sar.getBsaId() : "N/A",
                "filedToFINCEN", String.valueOf(sar.isFiledToFINCEN()),
                "priority", sar.getPriority(),
                "riskScore", sar.getFinalRiskScore() != null ? sar.getFinalRiskScore().toString() : "N/A",
                "filingDeadline", sar.getFilingDeadline().toString(),
                "correlationId", correlationId != null ? correlationId : "N/A",
                "eventId", event.getEventId()
            )
        );
    }

    private LocalDateTime calculateFilingDeadline(SARFilingEvent event) {
        // Regulatory requirement: SARs must be filed within 30 calendar days
        LocalDateTime deadline = LocalDateTime.now().plusDays(30);
        
        // Emergency SARs have shorter deadlines
        if (event.getPriority() != null && event.getPriority().equals("EMERGENCY")) {
            deadline = LocalDateTime.now().plusDays(14);
        }
        
        // High-risk activities have expedited deadlines
        if (event.getRiskScore() != null && event.getRiskScore() > 0.9) {
            deadline = LocalDateTime.now().plusDays(14);
        }
        
        return deadline;
    }

    private boolean requiresFINCENFiling(SARFilingEvent event) {
        // Amount threshold check
        if (event.getTotalAmount() != null && event.getTotalAmount().doubleValue() >= 5000.0) {
            return true;
        }
        
        // Specific activity types always require filing
        String[] alwaysFileTypes = {"MONEY_LAUNDERING", "TERRORIST_FINANCING", "FRAUD", "CYBER_CRIME"};
        for (String type : alwaysFileTypes) {
            if (event.getSarType().equals(type)) {
                return true;
            }
        }
        
        // High-risk activities require filing regardless of amount
        return event.getRiskScore() != null && event.getRiskScore() > 0.8;
    }

    private String calculateSARPriority(SARFilingEvent event) {
        if (event.getPriority() != null && event.getPriority().equals("EMERGENCY")) {
            return "CRITICAL";
        }
        
        if (event.getRiskScore() != null && event.getRiskScore() > 0.9) {
            return "HIGH";
        }
        
        if (event.getTotalAmount() != null && event.getTotalAmount().doubleValue() > 50000.0) {
            return "HIGH";
        }
        
        return "MEDIUM";
    }

    private String determineComplianceOfficer(SARFilingEvent event) {
        // Route to appropriate compliance officer based on SAR type and amount
        if (event.getTotalAmount() != null && event.getTotalAmount().doubleValue() > 100000.0) {
            return "senior-compliance-officer";
        }
        
        return switch (event.getSarType().toUpperCase()) {
            case "MONEY_LAUNDERING", "TERRORIST_FINANCING" -> "aml-specialist";
            case "FRAUD", "CYBER_CRIME" -> "fraud-specialist";
            case "SANCTIONS_VIOLATION" -> "sanctions-specialist";
            default -> "compliance-officer";
        };
    }

    private boolean isExemptFromAmountThreshold(SARFilingEvent event) {
        // Certain activity types are exempt from amount thresholds
        String[] exemptTypes = {"TERRORIST_FINANCING", "SANCTIONS_VIOLATION", "STRUCTURING"};
        for (String type : exemptTypes) {
            if (event.getSarType().equals(type)) {
                return true;
            }
        }
        return false;
    }
}