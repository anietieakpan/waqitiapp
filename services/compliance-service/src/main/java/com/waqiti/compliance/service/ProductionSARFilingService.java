package com.waqiti.compliance.service;

import com.waqiti.compliance.domain.SuspiciousActivityReport;
import com.waqiti.compliance.dto.FINCENSubmissionResponse;
import com.waqiti.compliance.model.SarFiling;
import com.waqiti.compliance.model.SarFilingStatus;
import com.waqiti.compliance.repository.SuspiciousActivityRepository;
import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.audit.ComprehensiveAuditService;
import com.waqiti.common.exceptions.RegulatorySubmissionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Production-Ready SAR Filing Service with FinCEN Integration
 *
 * CRITICAL COMPLIANCE SERVICE:
 * - Files Suspicious Activity Reports (SARs) with FinCEN within regulatory deadlines
 * - Manages complete SAR lifecycle from detection to submission
 * - Ensures BSA and AML compliance requirements are met
 *
 * REGULATORY FRAMEWORK:
 * - Bank Secrecy Act (BSA) Section 5318(g)
 * - 31 CFR 1020.320 (SAR Filing Requirements)
 * - FinCEN SAR Filing Deadline: 30 days from detection
 * - Continuing Activity Reports: Every 90 days
 *
 * PENALTIES FOR NON-COMPLIANCE:
 * - Civil penalties: Up to $100,000 per violation
 * - Criminal penalties: Up to $250,000 and/or 5 years imprisonment
 * - Regulatory sanctions: License revocation, consent orders
 *
 * @author Waqiti Compliance Team
 * @version 3.0.0
 * @since 2025-01-01
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductionSARFilingService {

    private final SuspiciousActivityRepository sarRepository;
    private final FINCENIntegrationService fincenIntegrationService;
    private final ComprehensiveAuditService auditService;
    private final AuditLogger auditLogger;
    private final ComplianceNotificationService notificationService;
    private final CaseManagementService caseManagementService;

    /**
     * Create SAR from detected suspicious activity
     * CRITICAL: This is the entry point for all SAR filings
     */
    @Transactional
    public SuspiciousActivityReport createSAR(
            UUID userId,
            String suspiciousActivityType,
            BigDecimal transactionAmount,
            String currency,
            String narrative,
            Map<String, Object> metadata) {

        log.warn("REGULATORY: Creating SAR for user {} - Activity: {} Amount: {} {}",
            userId, suspiciousActivityType, transactionAmount, currency);

        try {
            // Validate narrative meets FinCEN requirements (5 W's)
            validateNarrative(narrative);

            // Create SAR entity
            SuspiciousActivityReport sar = SuspiciousActivityReport.builder()
                .id(UUID.randomUUID().toString())
                .entityId(userId.toString())
                .entityType("CUSTOMER")
                .suspiciousActivity(suspiciousActivityType)
                .totalAmount(transactionAmount)
                .currency(currency)
                .narrative(enrichNarrative(narrative, metadata))
                .activityStartDate(extractActivityStartDate(metadata))
                .activityEndDate(LocalDateTime.now())
                .detectedAt(LocalDateTime.now())
                .status("PENDING_REVIEW")
                .riskScore(calculateRiskScore(suspiciousActivityType, transactionAmount))
                .priority(determinePriority(transactionAmount, suspiciousActivityType))
                .filingDeadline(LocalDateTime.now().plusDays(30))
                .requiresExecutiveReview(requiresExecutiveApproval(transactionAmount, suspiciousActivityType))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

            SuspiciousActivityReport savedSAR = sarRepository.save(sar);

            // Create compliance case for investigation
            String caseId = caseManagementService.createCriticalInvestigationCase(
                userId,
                savedSAR.getId(),
                suspiciousActivityType,
                "SAR Investigation - " + suspiciousActivityType,
                metadata
            );

            // Audit SAR creation
            auditService.auditCriticalComplianceEvent(
                "SAR_CREATED",
                userId.toString(),
                "Suspicious Activity Report created",
                Map.of(
                    "sarId", savedSAR.getId(),
                    "activityType", suspiciousActivityType,
                    "amount", transactionAmount,
                    "currency", currency,
                    "caseId", caseId,
                    "deadline", savedSAR.getFilingDeadline()
                )
            );

            // Send notifications based on priority
            if ("CRITICAL".equals(savedSAR.getPriority())) {
                notificationService.sendCriticalSARAlert(savedSAR);
                notificationService.notifyExecutiveTeam(savedSAR);
            } else if ("HIGH".equals(savedSAR.getPriority())) {
                notificationService.notifyComplianceOfficer(savedSAR);
            }

            log.warn("REGULATORY: Created SAR {} for user {} - Deadline: {}",
                savedSAR.getId(), userId, savedSAR.getFilingDeadline());

            return savedSAR;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to create SAR for user {}", userId, e);

            // Create critical alert
            auditLogger.logCriticalAlert(
                "SAR_CREATION_FAILURE",
                "Failed to create SAR - COMPLIANCE RISK",
                Map.of(
                    "userId", userId,
                    "activityType", suspiciousActivityType,
                    "amount", transactionAmount,
                    "error", e.getMessage()
                )
            );

            throw new RuntimeException("Failed to create SAR", e);
        }
    }

    /**
     * Submit SAR to FinCEN
     * CRITICAL: Must be called before filing deadline
     */
    @Transactional
    public FINCENSubmissionResponse submitSARToFinCEN(String sarId) {
        log.warn("REGULATORY: Submitting SAR {} to FinCEN", sarId);

        try {
            // Load SAR
            SuspiciousActivityReport sar = sarRepository.findById(sarId)
                .orElseThrow(() -> new IllegalArgumentException("SAR not found: " + sarId));

            // Verify SAR is ready for submission
            validateSARForSubmission(sar);

            // Update status to filing in progress
            sar.setStatus("FILING_IN_PROGRESS");
            sar.setFilingAttemptedAt(LocalDateTime.now());
            sarRepository.save(sar);

            // Submit to FinCEN via integration service
            FINCENSubmissionResponse response = fincenIntegrationService.submitSAR(sar);

            // Update SAR with FinCEN response
            sar.setStatus("FILED");
            sar.setFiledAt(LocalDateTime.now());
            sar.setBsaId(response.getBsaId());
            sar.setFincenSubmissionId(response.getSubmissionId());
            sar.setFincenAcknowledgment(response.getAcknowledgment());
            sar.setFincenAcknowledgedAt(response.getAcknowledgedAt());
            sarRepository.save(sar);

            // Audit successful filing
            auditService.auditCriticalComplianceEvent(
                "SAR_FILED_WITH_FINCEN",
                sar.getEntityId(),
                "SAR successfully filed with FinCEN",
                Map.of(
                    "sarId", sarId,
                    "bsaId", response.getBsaId(),
                    "submissionId", response.getSubmissionId(),
                    "filedAt", sar.getFiledAt()
                )
            );

            // Notify compliance team of successful filing
            notificationService.notifySuccessfulFiling(sar, response);

            log.warn("REGULATORY: SAR {} successfully filed with FinCEN - BSA ID: {}",
                sarId, response.getBsaId());

            return response;

        } catch (RegulatorySubmissionException e) {
            log.error("REGULATORY: FinCEN submission failed for SAR {}: {}", sarId, e.getMessage());

            // Update SAR with failure details
            SuspiciousActivityReport sar = sarRepository.findById(sarId).orElse(null);
            if (sar != null) {
                sar.setStatus("FILING_FAILED");
                sar.setFilingFailureReason(e.getMessage());
                sar.setFilingFailedAt(LocalDateTime.now());
                sarRepository.save(sar);
            }

            // Create critical alert for operations
            auditLogger.logCriticalAlert(
                "SAR_FILING_FAILURE",
                "SAR filing with FinCEN failed - IMMEDIATE ACTION REQUIRED",
                Map.of(
                    "sarId", sarId,
                    "error", e.getMessage(),
                    "timestamp", LocalDateTime.now()
                )
            );

            throw e;

        } catch (Exception e) {
            log.error("CRITICAL: Unexpected error filing SAR {} with FinCEN", sarId, e);
            throw new RuntimeException("SAR filing failed", e);
        }
    }

    /**
     * Automatically process and file overdue SARs
     * Scheduled task runs daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    @Transactional
    public void processOverdueSARs() {
        log.info("REGULATORY: Starting daily overdue SAR processing");

        try {
            LocalDateTime now = LocalDateTime.now();

            // Find SARs approaching deadline (within 5 days)
            List<SuspiciousActivityReport> approachingDeadline =
                sarRepository.findSARsApproachingDeadline(now, now.plusDays(5));

            for (SuspiciousActivityReport sar : approachingDeadline) {
                long daysUntilDeadline = java.time.temporal.ChronoUnit.DAYS.between(now, sar.getFilingDeadline());

                log.warn("REGULATORY: SAR {} approaching deadline in {} days",
                    sar.getId(), daysUntilDeadline);

                // Send escalation notifications
                if (daysUntilDeadline <= 2) {
                    notificationService.sendUrgentDeadlineAlert(sar, daysUntilDeadline);
                    notificationService.notifyExecutiveTeam(sar);
                } else {
                    notificationService.sendDeadlineReminder(sar, daysUntilDeadline);
                }
            }

            // Find overdue SARs
            List<SuspiciousActivityReport> overdue = sarRepository.findOverdueSARs(now);

            for (SuspiciousActivityReport sar : overdue) {
                log.error("REGULATORY VIOLATION: SAR {} is OVERDUE - Deadline: {} Current: {}",
                    sar.getId(), sar.getFilingDeadline(), now);

                // Update status
                sar.setStatus("OVERDUE");
                sar.setOverdueNotifiedAt(LocalDateTime.now());
                sarRepository.save(sar);

                // Create critical incident
                auditLogger.logCriticalAlert(
                    "SAR_FILING_OVERDUE",
                    "SAR FILING OVERDUE - REGULATORY VIOLATION",
                    Map.of(
                        "sarId", sar.getId(),
                        "deadline", sar.getFilingDeadline(),
                        "daysOverdue", java.time.temporal.ChronoUnit.DAYS.between(sar.getFilingDeadline(), now),
                        "entityId", sar.getEntityId()
                    )
                );

                // Escalate to executive team
                notificationService.sendCriticalOverdueAlert(sar);
                notificationService.notifyExecutiveTeam(sar);
                notificationService.notifyRegulatoryCompliance(sar);
            }

            log.info("REGULATORY: Overdue SAR processing complete - Approaching: {}, Overdue: {}",
                approachingDeadline.size(), overdue.size());

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process overdue SARs", e);
        }
    }

    /**
     * Check status of filed SARs with FinCEN
     * Scheduled task runs every 6 hours
     */
    @Scheduled(fixedRate = 21600000) // Every 6 hours
    @Async
    public CompletableFuture<Void> checkFiledSARStatus() {
        log.info("REGULATORY: Checking status of filed SARs with FinCEN");

        try {
            List<SuspiciousActivityReport> filedSARs = sarRepository.findByStatus("FILED");

            for (SuspiciousActivityReport sar : filedSARs) {
                try {
                    if (sar.getBsaId() != null) {
                        FINCENSubmissionResponse status =
                            fincenIntegrationService.checkSubmissionStatus(sar.getBsaId());

                        // Update SAR with latest status
                        if (status != null && status.getAcknowledgment() != null) {
                            sar.setFincenAcknowledgment(status.getAcknowledgment());
                            sar.setFincenAcknowledgedAt(status.getAcknowledgedAt());
                            sar.setStatus("ACKNOWLEDGED");
                            sarRepository.save(sar);

                            log.info("REGULATORY: SAR {} acknowledged by FinCEN - BSA ID: {}",
                                sar.getId(), sar.getBsaId());
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to check status for SAR {}", sar.getId(), e);
                }
            }

        } catch (Exception e) {
            log.error("CRITICAL: Failed to check filed SAR status", e);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Validate narrative meets FinCEN 5 W's requirement
     */
    private void validateNarrative(String narrative) {
        if (narrative == null || narrative.length() < 100) {
            throw new IllegalArgumentException(
                "SAR narrative must be at least 100 characters");
        }

        if (narrative.length() > 17000) {
            throw new IllegalArgumentException(
                "SAR narrative exceeds FinCEN limit of 17,000 characters");
        }

        // Check for 5 W's (Who, What, When, Where, Why)
        String lower = narrative.toLowerCase();
        if (!lower.contains("who") || !lower.contains("what") ||
            !lower.contains("when") || !lower.contains("where") ||
            !lower.contains("why")) {
            log.warn("SAR narrative may be missing required 5 W's");
        }
    }

    /**
     * Enrich narrative with structured data
     */
    private String enrichNarrative(String baseNarrative, Map<String, Object> metadata) {
        StringBuilder enriched = new StringBuilder(baseNarrative);

        enriched.append("\n\n--- System Generated Details ---\n");
        enriched.append("Detection Timestamp: ").append(LocalDateTime.now()).append("\n");

        if (metadata != null && !metadata.isEmpty()) {
            enriched.append("Additional Context:\n");
            metadata.forEach((key, value) ->
                enriched.append("  ").append(key).append(": ").append(value).append("\n"));
        }

        return enriched.toString();
    }

    /**
     * Calculate risk score based on activity and amount
     */
    private Integer calculateRiskScore(String activityType, BigDecimal amount) {
        int score = 50; // Base score

        // Activity type scoring
        if (activityType.contains("TERRORISM") || activityType.contains("SANCTIONS")) {
            score += 50;
        } else if (activityType.contains("MONEY_LAUNDERING")) {
            score += 40;
        } else if (activityType.contains("STRUCTURING")) {
            score += 30;
        }

        // Amount scoring
        if (amount.compareTo(BigDecimal.valueOf(1000000)) > 0) {
            score += 30;
        } else if (amount.compareTo(BigDecimal.valueOf(100000)) > 0) {
            score += 20;
        } else if (amount.compareTo(BigDecimal.valueOf(10000)) > 0) {
            score += 10;
        }

        return Math.min(score, 100);
    }

    /**
     * Determine priority level
     */
    private String determinePriority(BigDecimal amount, String activityType) {
        if (activityType.contains("TERRORISM") || activityType.contains("SANCTIONS") ||
            amount.compareTo(BigDecimal.valueOf(1000000)) > 0) {
            return "CRITICAL";
        } else if (amount.compareTo(BigDecimal.valueOf(100000)) > 0) {
            return "HIGH";
        } else if (amount.compareTo(BigDecimal.valueOf(10000)) > 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    /**
     * Check if executive review is required
     */
    private boolean requiresExecutiveApproval(BigDecimal amount, String activityType) {
        return amount.compareTo(BigDecimal.valueOf(500000)) > 0 ||
               activityType.contains("TERRORISM") ||
               activityType.contains("SANCTIONS");
    }

    /**
     * Extract activity start date from metadata
     */
    private LocalDateTime extractActivityStartDate(Map<String, Object> metadata) {
        if (metadata != null && metadata.containsKey("activityStartDate")) {
            return (LocalDateTime) metadata.get("activityStartDate");
        }
        return LocalDateTime.now().minusDays(30); // Default to 30 days ago
    }

    /**
     * Validate SAR is ready for FinCEN submission
     */
    private void validateSARForSubmission(SuspiciousActivityReport sar) {
        if (sar.getNarrative() == null || sar.getNarrative().length() < 100) {
            throw new IllegalStateException("SAR narrative is incomplete");
        }

        if (sar.getStatus().equals("FILED") || sar.getStatus().equals("ACKNOWLEDGED")) {
            throw new IllegalStateException("SAR already filed");
        }

        if (LocalDateTime.now().isAfter(sar.getFilingDeadline())) {
            log.error("REGULATORY VIOLATION: Attempting to file overdue SAR {}", sar.getId());
        }
    }
}
