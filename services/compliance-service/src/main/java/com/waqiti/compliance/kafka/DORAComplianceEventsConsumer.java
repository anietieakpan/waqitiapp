package com.waqiti.compliance.kafka;

import com.waqiti.common.events.DORAComplianceEvent;
import com.waqiti.compliance.domain.DORAIncident;
import com.waqiti.compliance.domain.ICTRiskAssessment;
import com.waqiti.compliance.domain.ThirdPartyRiskProfile;
import com.waqiti.compliance.repository.DORAIncidentRepository;
import com.waqiti.compliance.repository.ICTRiskAssessmentRepository;
import com.waqiti.compliance.repository.ThirdPartyRiskRepository;
import com.waqiti.compliance.service.DORAComplianceService;
import com.waqiti.compliance.service.IncidentReportingService;
import com.waqiti.compliance.metrics.ComplianceMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.Duration;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class DORAComplianceEventsConsumer {
    
    private final DORAIncidentRepository incidentRepository;
    private final ICTRiskAssessmentRepository riskAssessmentRepository;
    private final ThirdPartyRiskRepository thirdPartyRiskRepository;
    private final DORAComplianceService doraService;
    private final IncidentReportingService incidentReportingService;
    private final ComplianceMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final int MAJOR_INCIDENT_NOTIFICATION_HOURS = 4;
    private static final int SIGNIFICANT_INCIDENT_NOTIFICATION_HOURS = 24;
    private static final int INTERMEDIATE_UPDATE_HOURS = 72;
    
    @KafkaListener(
        topics = {"dora-compliance-events", "digital-operational-resilience-events", "ict-incident-events"},
        groupId = "compliance-dora-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleDORAComplianceEvent(
            @Payload DORAComplianceEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("dora-%s-p%d-o%d", 
            event.getEventId(), partition, offset);
        
        log.info("Processing DORA compliance event: type={}, severity={}", 
            event.getEventType(), event.getSeverity());
        
        try {
            switch (event.getEventType()) {
                case ICT_INCIDENT_DETECTED:
                    processICTIncidentDetected(event, correlationId);
                    break;
                case MAJOR_INCIDENT_REPORTED:
                    processMajorIncidentReported(event, correlationId);
                    break;
                case INCIDENT_NOTIFICATION_SUBMITTED:
                    processIncidentNotificationSubmitted(event, correlationId);
                    break;
                case INTERMEDIATE_UPDATE_REQUIRED:
                    processIntermediateUpdateRequired(event, correlationId);
                    break;
                case INCIDENT_RESOLVED:
                    processIncidentResolved(event, correlationId);
                    break;
                case FINAL_REPORT_SUBMITTED:
                    processFinalReportSubmitted(event, correlationId);
                    break;
                case THIRD_PARTY_RISK_ASSESSMENT:
                    processThirdPartyRiskAssessment(event, correlationId);
                    break;
                case ICT_RISK_TESTING_REQUIRED:
                    processICTRiskTestingRequired(event, correlationId);
                    break;
                case THREAT_LED_PENETRATION_TEST:
                    processThreatLedPenetrationTest(event, correlationId);
                    break;
                case BUSINESS_CONTINUITY_TEST:
                    processBusinessContinuityTest(event, correlationId);
                    break;
                default:
                    log.warn("Unknown DORA event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logComplianceEvent(
                "DORA_EVENT_PROCESSED",
                event.getEventId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "severity", event.getSeverity(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process DORA event: eventId={}, error={}", 
                event.getEventId(), e.getMessage(), e);
            kafkaTemplate.send("dora-compliance-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processICTIncidentDetected(DORAComplianceEvent event, String correlationId) {
        log.warn("ICT incident detected: severity={}, system={}", 
            event.getSeverity(), event.getAffectedSystem());
        
        DORAIncident incident = DORAIncident.builder()
            .id(UUID.randomUUID().toString())
            .incidentType(event.getIncidentType())
            .severity(event.getSeverity())
            .affectedSystem(event.getAffectedSystem())
            .detectedAt(LocalDateTime.now())
            .status("DETECTED")
            .impactedServices(event.getImpactedServices())
            .estimatedCustomersAffected(event.getEstimatedCustomersAffected())
            .financialImpact(event.getFinancialImpact())
            .correlationId(correlationId)
            .build();
        
        incidentRepository.save(incident);
        
        if (isMajorIncident(event)) {
            incident.setClassification("MAJOR");
            incident.setNotificationDeadline(LocalDateTime.now().plusHours(MAJOR_INCIDENT_NOTIFICATION_HOURS));
            incidentRepository.save(incident);
            
            doraService.initiateMajorIncidentProtocol(incident.getId());
            
            notificationService.sendComplianceAlert(
                "DORA Major Incident Detected",
                String.format("Major ICT incident detected in %s. Notification required within %d hours.",
                    event.getAffectedSystem(), MAJOR_INCIDENT_NOTIFICATION_HOURS),
                NotificationService.Priority.CRITICAL
            );
        } else if (isSignificantIncident(event)) {
            incident.setClassification("SIGNIFICANT");
            incident.setNotificationDeadline(LocalDateTime.now().plusHours(SIGNIFICANT_INCIDENT_NOTIFICATION_HOURS));
            incidentRepository.save(incident);
        }
        
        metricsService.recordDORAIncident(event.getSeverity(), event.getIncidentType());
    }
    
    private void processMajorIncidentReported(DORAComplianceEvent event, String correlationId) {
        log.info("Major incident reported to authorities: incidentId={}", event.getIncidentId());
        
        DORAIncident incident = incidentRepository.findById(event.getIncidentId())
            .orElseThrow(() -> new IllegalStateException("Incident not found: " + event.getIncidentId()));
        
        incident.setStatus("REPORTED_TO_AUTHORITIES");
        incident.setInitialNotificationSentAt(LocalDateTime.now());
        incident.setRegulatoryReferenceNumber(event.getRegulatoryReferenceNumber());
        incident.setIntermediateUpdateDeadline(LocalDateTime.now().plusHours(INTERMEDIATE_UPDATE_HOURS));
        incidentRepository.save(incident);
        
        metricsService.recordDORAMajorIncidentReported();
    }
    
    private void processIncidentNotificationSubmitted(DORAComplianceEvent event, String correlationId) {
        log.info("Incident notification submitted: incidentId={}, reference={}", 
            event.getIncidentId(), event.getRegulatoryReferenceNumber());
        
        DORAIncident incident = incidentRepository.findById(event.getIncidentId())
            .orElseThrow();
        
        incident.setNotificationSubmittedAt(LocalDateTime.now());
        incident.setRegulatoryReferenceNumber(event.getRegulatoryReferenceNumber());
        incidentRepository.save(incident);
        
        metricsService.recordDORANotificationSubmitted();
    }
    
    private void processIntermediateUpdateRequired(DORAComplianceEvent event, String correlationId) {
        log.info("Intermediate update required: incidentId={}", event.getIncidentId());
        
        DORAIncident incident = incidentRepository.findById(event.getIncidentId())
            .orElseThrow();
        
        incident.setNextUpdateDeadline(LocalDateTime.now().plusHours(INTERMEDIATE_UPDATE_HOURS));
        incidentRepository.save(incident);
        
        incidentReportingService.scheduleIntermediateUpdate(incident.getId());
        
        notificationService.sendComplianceAlert(
            "DORA Intermediate Update Required",
            String.format("Intermediate update required for incident %s within %d hours",
                incident.getId(), INTERMEDIATE_UPDATE_HOURS),
            NotificationService.Priority.HIGH
        );
    }
    
    private void processIncidentResolved(DORAComplianceEvent event, String correlationId) {
        log.info("Incident resolved: incidentId={}, duration={}", 
            event.getIncidentId(), event.getResolutionDuration());
        
        DORAIncident incident = incidentRepository.findById(event.getIncidentId())
            .orElseThrow();
        
        incident.setStatus("RESOLVED");
        incident.setResolvedAt(LocalDateTime.now());
        incident.setResolutionDuration(event.getResolutionDuration());
        incident.setRootCause(event.getRootCause());
        incident.setFinalReportDeadline(LocalDateTime.now().plusDays(14));
        incidentRepository.save(incident);
        
        incidentReportingService.scheduleFinalReport(incident.getId());
        metricsService.recordDORAIncidentResolved(event.getResolutionDuration());
    }
    
    private void processFinalReportSubmitted(DORAComplianceEvent event, String correlationId) {
        log.info("Final report submitted: incidentId={}", event.getIncidentId());
        
        DORAIncident incident = incidentRepository.findById(event.getIncidentId())
            .orElseThrow();
        
        incident.setStatus("CLOSED");
        incident.setFinalReportSubmittedAt(LocalDateTime.now());
        incident.setLessonsLearned(event.getLessonsLearned());
        incident.setPreventiveMeasures(event.getPreventiveMeasures());
        incidentRepository.save(incident);
        
        metricsService.recordDORAFinalReportSubmitted();
    }
    
    private void processThirdPartyRiskAssessment(DORAComplianceEvent event, String correlationId) {
        log.info("Third party risk assessment: provider={}, criticality={}", 
            event.getThirdPartyProvider(), event.getCriticalityLevel());
        
        ThirdPartyRiskProfile riskProfile = ThirdPartyRiskProfile.builder()
            .id(UUID.randomUUID().toString())
            .providerName(event.getThirdPartyProvider())
            .serviceType(event.getServiceType())
            .criticalityLevel(event.getCriticalityLevel())
            .riskScore(event.getRiskScore())
            .assessmentDate(LocalDateTime.now())
            .nextAssessmentDue(calculateNextAssessmentDate(event.getCriticalityLevel()))
            .contractualArrangements(event.getContractualArrangements())
            .exitStrategy(event.getExitStrategy())
            .correlationId(correlationId)
            .build();
        
        thirdPartyRiskRepository.save(riskProfile);
        
        if ("CRITICAL".equals(event.getCriticalityLevel()) && event.getRiskScore() > 70.0) {
            notificationService.sendComplianceAlert(
                "High Risk Third Party Identified",
                String.format("Critical third party %s has high risk score: %.1f",
                    event.getThirdPartyProvider(), event.getRiskScore()),
                NotificationService.Priority.HIGH
            );
        }
        
        metricsService.recordThirdPartyRiskAssessment(event.getCriticalityLevel(), event.getRiskScore());
    }
    
    private void processICTRiskTestingRequired(DORAComplianceEvent event, String correlationId) {
        log.info("ICT risk testing required: testType={}, dueDate={}", 
            event.getTestType(), event.getTestingDeadline());
        
        ICTRiskAssessment assessment = ICTRiskAssessment.builder()
            .id(UUID.randomUUID().toString())
            .testType(event.getTestType())
            .scope(event.getTestScope())
            .scheduledDate(event.getTestingDeadline())
            .status("SCHEDULED")
            .riskAreas(event.getRiskAreas())
            .correlationId(correlationId)
            .build();
        
        riskAssessmentRepository.save(assessment);
        doraService.scheduleICTRiskTest(assessment.getId());
        
        metricsService.recordICTRiskTestScheduled(event.getTestType());
    }
    
    private void processThreatLedPenetrationTest(DORAComplianceEvent event, String correlationId) {
        log.info("Threat-led penetration test: scope={}, findings={}", 
            event.getTestScope(), event.getFindingsCount());
        
        ICTRiskAssessment assessment = riskAssessmentRepository.findById(event.getAssessmentId())
            .orElseThrow();
        
        assessment.setStatus("COMPLETED");
        assessment.setCompletedAt(LocalDateTime.now());
        assessment.setFindingsCount(event.getFindingsCount());
        assessment.setCriticalFindings(event.getCriticalFindings());
        assessment.setRemediationDeadline(LocalDateTime.now().plusDays(30));
        riskAssessmentRepository.save(assessment);
        
        if (event.getCriticalFindings() > 0) {
            notificationService.sendComplianceAlert(
                "Critical Findings in Penetration Test",
                String.format("Penetration test found %d critical vulnerabilities. Remediation required within 30 days.",
                    event.getCriticalFindings()),
                NotificationService.Priority.CRITICAL
            );
        }
        
        metricsService.recordThreatLedPenetrationTest(event.getFindingsCount(), event.getCriticalFindings());
    }
    
    private void processBusinessContinuityTest(DORAComplianceEvent event, String correlationId) {
        log.info("Business continuity test: result={}, rto={}, rpo={}", 
            event.getTestResult(), event.getRecoveryTimeObjective(), event.getRecoveryPointObjective());
        
        ICTRiskAssessment assessment = ICTRiskAssessment.builder()
            .id(UUID.randomUUID().toString())
            .testType("BUSINESS_CONTINUITY")
            .status("COMPLETED")
            .completedAt(LocalDateTime.now())
            .testResult(event.getTestResult())
            .actualRTO(event.getActualRTO())
            .actualRPO(event.getActualRPO())
            .targetRTO(event.getRecoveryTimeObjective())
            .targetRPO(event.getRecoveryPointObjective())
            .objectivesMet(event.isObjectivesMet())
            .correlationId(correlationId)
            .build();
        
        riskAssessmentRepository.save(assessment);
        
        if (!event.isObjectivesMet()) {
            notificationService.sendComplianceAlert(
                "Business Continuity Test Failed",
                "Business continuity objectives not met. Remediation plan required.",
                NotificationService.Priority.HIGH
            );
        }
        
        metricsService.recordBusinessContinuityTest(event.isObjectivesMet());
    }
    
    private boolean isMajorIncident(DORAComplianceEvent event) {
        return "CRITICAL".equals(event.getSeverity()) && 
               (event.getEstimatedCustomersAffected() > 10000 ||
                (event.getFinancialImpact() != null && event.getFinancialImpact().doubleValue() > 100000));
    }
    
    private boolean isSignificantIncident(DORAComplianceEvent event) {
        return "HIGH".equals(event.getSeverity()) &&
               (event.getEstimatedCustomersAffected() > 1000 ||
                (event.getFinancialImpact() != null && event.getFinancialImpact().doubleValue() > 10000));
    }
    
    private LocalDateTime calculateNextAssessmentDate(String criticalityLevel) {
        return switch (criticalityLevel) {
            case "CRITICAL" -> LocalDateTime.now().plusMonths(6);
            case "HIGH" -> LocalDateTime.now().plusYears(1);
            case "MEDIUM" -> LocalDateTime.now().plusYears(2);
            default -> LocalDateTime.now().plusYears(3);
        };
    }
}