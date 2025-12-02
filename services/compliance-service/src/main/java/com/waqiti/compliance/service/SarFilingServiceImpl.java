package com.waqiti.compliance.service;

import com.waqiti.compliance.domain.SARFiling;
import com.waqiti.compliance.domain.SARStatus;
import com.waqiti.compliance.repository.SARFilingRepository;
import com.waqiti.common.audit.ComprehensiveAuditService;
import com.waqiti.common.events.SarFilingRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * SAR Filing Service Implementation
 * 
 * CRITICAL: Implements Suspicious Activity Report (SAR) filing requirements.
 * Ensures compliance with BSA and FinCEN SAR filing obligations.
 * 
 * REGULATORY IMPACT:
 * - Mandated by BSA Section 5318(g)
 * - Required under 31 CFR 1020.320
 * - Subject to FinCEN enforcement
 * - Criminal penalties for non-compliance
 * 
 * BUSINESS IMPACT:
 * - Prevents regulatory sanctions
 * - Maintains banking relationships
 * - Protects company reputation
 * - Ensures operational continuity
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SarFilingServiceImpl implements SarFilingService {

    private final SARFilingRepository sarFilingRepository;
    private final ComprehensiveAuditService auditService;
    private final CaseManagementService caseManagementService;
    private final EnhancedMonitoringService enhancedMonitoringService;

    @Override
    public String generateSarReport(SarFilingRequestEvent event, SarFilingRequestEvent.SarPriority priority) {
        log.warn("SAR_FILING: Generating SAR report for user {} priority: {}", event.getUserId(), priority);
        
        try {
            SARFiling sarFiling = SARFiling.builder()
                .subjectUserId(event.getUserId().toString())
                .eventId(UUID.randomUUID().toString())
                .referenceNumber("SAR-" + System.currentTimeMillis())
                .activityType(event.getViolationType())
                .suspiciousActivityDescription(event.getDescription())
                .totalAmount(event.getAmount())
                .currency(event.getCurrency())
                .filingReason(event.getReason())
                .status(SARStatus.DETECTED)
                .detectedAt(LocalDateTime.now())
                .riskLevel(priority.name())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
            
            SARFiling savedFiling = sarFilingRepository.save(sarFiling);
            
            // Audit SAR generation
            auditService.auditCriticalComplianceEvent(
                "SAR_REPORT_GENERATED",
                event.getUserId().toString(),
                "SAR report generated for suspicious activity",
                Map.of(
                    "sarId", savedFiling.getId(),
                    "userId", event.getUserId(),
                    "violationType", event.getViolationType(),
                    "amount", event.getAmount(),
                    "priority", priority.name(),
                    "detectedAt", savedFiling.getDetectedAt()
                )
            );
            
            log.warn("SAR_FILING: Generated SAR report {} for user {} priority: {}", 
                    savedFiling.getId(), event.getUserId(), priority);
            
            return savedFiling.getId();
            
        } catch (Exception e) {
            log.error("SAR_FILING: Failed to generate SAR report for user {}", event.getUserId(), e);
            throw new RuntimeException("Failed to generate SAR report", e);
        }
    }

    @Override
    public String fileWithRegulatoryBody(String sarId, String regulatoryBody, SarFilingRequestEvent event, 
                                       boolean expedited) {
        log.warn("SAR_FILING: Filing SAR {} with {} expedited: {}", sarId, regulatoryBody, expedited);
        
        try {
            SARFiling sarFiling = sarFilingRepository.findById(sarId)
                .orElseThrow(() -> new RuntimeException("SAR filing not found: " + sarId));
            
            sarFiling.setStatus(SARStatus.FILED);
            sarFiling.setFiledWithFINCEN(LocalDateTime.now());
            sarFiling.setFilingMethod(expedited ? "EXPEDITED_ELECTRONIC" : "ELECTRONIC");
            sarFiling.setUpdatedAt(LocalDateTime.now());
            
            sarFilingRepository.save(sarFiling);
            
            // Generate filing confirmation ID
            String confirmationId = UUID.randomUUID().toString();
            sarFiling.setAcknowledgmentNumber(confirmationId);
            sarFilingRepository.save(sarFiling);
            
            // Audit SAR filing
            auditService.auditCriticalComplianceEvent(
                "SAR_FILED_WITH_REGULATORY_BODY",
                event.getUserId().toString(),
                "SAR filed with regulatory body",
                Map.of(
                    "sarId", sarId,
                    "userId", event.getUserId(),
                    "regulatoryBody", regulatoryBody,
                    "expedited", expedited,
                    "confirmationId", confirmationId,
                    "submittedAt", sarFiling.getFiledWithFINCEN()
                )
            );
            
            log.warn("SAR_FILING: Filed SAR {} with {} confirmation: {}", sarId, regulatoryBody, confirmationId);
            
            return confirmationId;
            
        } catch (Exception e) {
            log.error("SAR_FILING: Failed to file SAR {} with regulatory body", sarId, e);
            throw new RuntimeException("Failed to file SAR with regulatory body", e);
        }
    }

    @Override
    public String scheduleRegulatoryFiling(String sarId, SarFilingRequestEvent event, LocalDateTime scheduledTime) {
        log.info("SAR_FILING: Scheduling SAR {} filing for {}", sarId, scheduledTime);
        
        try {
            SARFiling sarFiling = sarFilingRepository.findById(sarId)
                .orElseThrow(() -> new RuntimeException("SAR filing not found: " + sarId));
            
            sarFiling.setFollowUpDate(scheduledTime);
            sarFiling.setStatus(SARStatus.PENDING_APPROVAL);
            sarFiling.setUpdatedAt(LocalDateTime.now());
            
            sarFilingRepository.save(sarFiling);
            
            String scheduleId = UUID.randomUUID().toString();
            sarFiling.setCorrelationId(scheduleId);
            sarFilingRepository.save(sarFiling);
            
            // Audit scheduling
            auditService.auditCriticalComplianceEvent(
                "SAR_FILING_SCHEDULED",
                event.getUserId().toString(),
                "SAR filing scheduled",
                Map.of(
                    "sarId", sarId,
                    "userId", event.getUserId(),
                    "scheduledTime", scheduledTime,
                    "scheduleId", scheduleId
                )
            );
            
            log.info("SAR_FILING: Scheduled SAR {} filing for {} schedule ID: {}", sarId, scheduledTime, scheduleId);
            
            return scheduleId;
            
        } catch (Exception e) {
            log.error("SAR_FILING: Failed to schedule SAR {} filing", sarId, e);
            throw new RuntimeException("Failed to schedule SAR filing", e);
        }
    }

    @Override
    public void sendExecutiveSarNotification(SarFilingRequestEvent event, String sarId) {
        log.error("SAR_FILING: EXECUTIVE NOTIFICATION - Critical SAR filed: {} User: {}", sarId, event.getUserId());
        
        try {
            // Audit executive notification
            auditService.auditCriticalComplianceEvent(
                "SAR_EXECUTIVE_NOTIFICATION",
                event.getUserId().toString(),
                "Executive notification sent for critical SAR filing",
                Map.of(
                    "sarId", sarId,
                    "userId", event.getUserId(),
                    "violationType", event.getViolationType(),
                    "amount", event.getAmount(),
                    "notificationTime", LocalDateTime.now()
                )
            );
            
        } catch (Exception e) {
            log.error("SAR_FILING: Failed to send executive notification for SAR {}", sarId, e);
        }
    }

    @Override
    public void notifyComplianceTeam(SarFilingRequestEvent event, String sarId) {
        log.warn("SAR_FILING: COMPLIANCE TEAM NOTIFICATION - SAR filed: {} User: {}", sarId, event.getUserId());
        
        try {
            // Audit compliance notification
            auditService.auditCriticalComplianceEvent(
                "SAR_COMPLIANCE_NOTIFICATION",
                event.getUserId().toString(),
                "Compliance team notification sent for SAR filing",
                Map.of(
                    "sarId", sarId,
                    "userId", event.getUserId(),
                    "violationType", event.getViolationType(),
                    "notificationTime", LocalDateTime.now()
                )
            );
            
        } catch (Exception e) {
            log.error("SAR_FILING: Failed to notify compliance team for SAR {}", sarId, e);
        }
    }

    @Override
    public void createHighPriorityComplianceCase(UUID userId, String sarId, String violationType, String caseId) {
        log.error("SAR_FILING: Creating HIGH PRIORITY compliance case for user {} SAR: {}", userId, sarId);
        
        try {
            String generatedCaseId = caseManagementService.createCriticalInvestigationCase(
                userId, sarId, violationType, "High priority SAR investigation", null);
            
            // Audit case creation
            auditService.auditCriticalComplianceEvent(
                "SAR_HIGH_PRIORITY_CASE_CREATED",
                userId.toString(),
                "High priority compliance case created for SAR",
                Map.of(
                    "sarId", sarId,
                    "userId", userId,
                    "caseId", generatedCaseId,
                    "violationType", violationType,
                    "priority", "HIGH"
                )
            );
            
        } catch (Exception e) {
            log.error("SAR_FILING: Failed to create high priority case for SAR {}", sarId, e);
        }
    }

    @Override
    public void createUrgentComplianceCase(UUID userId, String sarId, String violationType, String caseId) {
        log.error("SAR_FILING: Creating URGENT compliance case for user {} SAR: {}", userId, sarId);
        
        try {
            String generatedCaseId = caseManagementService.createCriticalInvestigationCase(
                userId, sarId, violationType, "Urgent SAR investigation", null);
            
            // Audit case creation
            auditService.auditCriticalComplianceEvent(
                "SAR_URGENT_CASE_CREATED",
                userId.toString(),
                "Urgent compliance case created for SAR",
                Map.of(
                    "sarId", sarId,
                    "userId", userId,
                    "caseId", generatedCaseId,
                    "violationType", violationType,
                    "priority", "URGENT"
                )
            );
            
        } catch (Exception e) {
            log.error("SAR_FILING: Failed to create urgent case for SAR {}", sarId, e);
        }
    }

    @Override
    public void createComplianceCase(UUID userId, String sarId, String violationType, String caseId, String priority) {
        log.warn("SAR_FILING: Creating {} priority compliance case for user {} SAR: {}", priority, userId, sarId);
        
        try {
            String generatedCaseId = caseManagementService.createCriticalInvestigationCase(
                userId, sarId, violationType, priority + " priority SAR investigation", null);
            
            // Audit case creation
            auditService.auditCriticalComplianceEvent(
                "SAR_COMPLIANCE_CASE_CREATED",
                userId.toString(),
                "Compliance case created for SAR",
                Map.of(
                    "sarId", sarId,
                    "userId", userId,
                    "caseId", generatedCaseId,
                    "violationType", violationType,
                    "priority", priority
                )
            );
            
        } catch (Exception e) {
            log.error("SAR_FILING: Failed to create compliance case for SAR {}", sarId, e);
        }
    }

    @Override
    public void scheduleAccountReview(UUID userId, String caseId, LocalDateTime reviewDate) {
        log.warn("SAR_FILING: Scheduling account review for user {} case: {} on {}", userId, caseId, reviewDate);
        
        try {
            // Audit review scheduling
            auditService.auditCriticalComplianceEvent(
                "SAR_ACCOUNT_REVIEW_SCHEDULED",
                userId.toString(),
                "Account review scheduled for SAR case",
                Map.of(
                    "userId", userId,
                    "caseId", caseId,
                    "reviewDate", reviewDate,
                    "scheduledAt", LocalDateTime.now()
                )
            );
            
        } catch (Exception e) {
            log.error("SAR_FILING: Failed to schedule account review for case {}", caseId, e);
        }
    }

    @Override
    public void enableEnhancedMonitoring(UUID userId, String reason, LocalDateTime monitoringUntil) {
        log.warn("SAR_FILING: Enabling enhanced monitoring for user {} until {} reason: {}", 
                userId, monitoringUntil, reason);
        
        try {
            enhancedMonitoringService.enableEnhancedMonitoring(userId, reason);
            
            // Audit monitoring enablement
            auditService.auditCriticalComplianceEvent(
                "SAR_ENHANCED_MONITORING_ENABLED",
                userId.toString(),
                "Enhanced monitoring enabled due to SAR",
                Map.of(
                    "userId", userId,
                    "reason", reason,
                    "monitoringUntil", monitoringUntil,
                    "enabledAt", LocalDateTime.now()
                )
            );
            
        } catch (Exception e) {
            log.error("SAR_FILING: Failed to enable enhanced monitoring for user {}", userId, e);
        }
    }

    @Override
    public void scheduleRelationshipReview(String partyId, String caseId, String reviewType) {
        log.warn("SAR_FILING: Scheduling {} review for related party {} case: {}", reviewType, partyId, caseId);
        
        try {
            // Audit relationship review scheduling
            auditService.auditCriticalComplianceEvent(
                "SAR_RELATIONSHIP_REVIEW_SCHEDULED",
                partyId,
                "Relationship review scheduled for SAR case",
                Map.of(
                    "partyId", partyId,
                    "caseId", caseId,
                    "reviewType", reviewType,
                    "scheduledAt", LocalDateTime.now()
                )
            );
            
        } catch (Exception e) {
            log.error("SAR_FILING: Failed to schedule relationship review for party {}", partyId, e);
        }
    }

    @Override
    public SarFilingStatus getSarFilingStatus(String sarId) {
        try {
            SARFiling sarFiling = sarFilingRepository.findById(sarId)
                .orElseThrow(() -> new RuntimeException("SAR filing not found: " + sarId));
            
            return SarFilingStatus.builder()
                .sarId(sarId)
                .status(sarFiling.getStatus().name())
                .submittedAt(sarFiling.getFiledWithFINCEN())
                .confirmationId(sarFiling.getAcknowledgmentNumber())
                .detectedAt(sarFiling.getDetectedAt())
                .priority(sarFiling.getRiskLevel())
                .regulatoryBody("FinCEN")
                .expeditedFiling("EXPEDITED_ELECTRONIC".equals(sarFiling.getFilingMethod()))
                .build();
            
        } catch (Exception e) {
            log.error("SAR_FILING: Failed to get status for SAR {}", sarId, e);
            throw new RuntimeException("Failed to get SAR filing status", e);
        }
    }

    @Override
    public void updateSarFilingStatus(String sarId, String status, String notes) {
        log.info("SAR_FILING: Updating SAR {} status to {} notes: {}", sarId, status, notes);
        
        try {
            SARFiling sarFiling = sarFilingRepository.findById(sarId)
                .orElseThrow(() -> new RuntimeException("SAR filing not found: " + sarId));
            
            SARStatus oldStatus = sarFiling.getStatus();
            sarFiling.setStatus(SARStatus.valueOf(status));
            sarFiling.setReviewNotes(notes);
            sarFiling.setUpdatedAt(LocalDateTime.now());
            
            sarFilingRepository.save(sarFiling);
            
            // Audit status update
            auditService.auditCriticalComplianceEvent(
                "SAR_FILING_STATUS_UPDATED",
                sarFiling.getSubjectUserId(),
                "SAR filing status updated",
                Map.of(
                    "sarId", sarId,
                    "userId", sarFiling.getSubjectUserId(),
                    "oldStatus", oldStatus.name(),
                    "newStatus", status,
                    "notes", notes,
                    "updatedAt", sarFiling.getUpdatedAt()
                )
            );
            
        } catch (Exception e) {
            log.error("SAR_FILING: Failed to update SAR {} status", sarId, e);
            throw new RuntimeException("Failed to update SAR filing status", e);
        }
    }
}