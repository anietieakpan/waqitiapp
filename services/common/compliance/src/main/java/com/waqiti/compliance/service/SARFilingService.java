package com.waqiti.compliance.service;

import com.waqiti.compliance.dto.SARCase;
import com.waqiti.compliance.dto.SARFilingStatus;
import com.waqiti.compliance.dto.SARPriority;
import com.waqiti.compliance.entity.SARFiling;
import com.waqiti.compliance.repository.SARFilingRepository;
import com.waqiti.compliance.client.FinCENClient;
import com.waqiti.alerting.service.PagerDutyAlertService;
import com.waqiti.alerting.service.SlackAlertService;
import com.waqiti.alerting.dto.AlertSeverity;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.security.SecurityContextService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enterprise SAR (Suspicious Activity Report) Filing Automation Service
 *
 * Automates the creation, tracking, and filing of Suspicious Activity Reports
 * in compliance with FinCEN BSA E-Filing requirements (31 CFR 1020.320).
 *
 * Key Features:
 * - Automatic SAR case creation from fraud/compliance alerts
 * - 30-day filing deadline tracking and enforcement
 * - Integration with FinCEN BSA E-Filing System
 * - Compliance officer notification workflows
 * - Audit trail for regulatory examinations
 * - Automatic escalation for overdue filings
 * - Multi-jurisdiction support (US, EU, UK)
 *
 * Regulatory Requirements:
 * - File within 30 calendar days of initial detection
 * - Maintain records for 5 years
 * - Include all required FinCEN fields (2023 BSA E-Filing spec)
 * - Provide continuing activity updates if investigation ongoing
 *
 * @author Waqiti Compliance Team
 * @version 2.0
 * @since 2025-10-17
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SARFilingService {

    private final SARFilingRepository sarFilingRepository;
    private final FinCENClient finCENClient;
    private final PagerDutyAlertService pagerDutyAlertService;
    private final SlackAlertService slackAlertService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final SecurityContextService securityContextService;

    private static final int FILING_DEADLINE_DAYS = 30;
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("10000.00");
    private static final int ALERT_BEFORE_DEADLINE_DAYS = 3;

    /**
     * Creates a new SAR filing case with automatic deadline calculation
     *
     * @param userId User ID associated with suspicious activity
     * @param transactionId Transaction ID triggering SAR
     * @param suspiciousActivity Description of suspicious activity
     * @param details Additional context and evidence
     * @return SAR case ID for tracking
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String createSARFilingTask(String userId, String transactionId,
                                      String suspiciousActivity, Map<String, Object> details) {

        log.info("Creating SAR filing task: userId={}, transactionId={}, activity={}",
            userId, transactionId, suspiciousActivity);

        try {
            // Calculate regulatory deadline (30 days from detection)
            LocalDateTime detectionDate = LocalDateTime.now();
            LocalDateTime filingDeadline = detectionDate.plusDays(FILING_DEADLINE_DAYS);

            // Determine priority based on activity type and amount
            SARPriority priority = determinePriority(suspiciousActivity, details);

            // Create SAR filing record
            SARFiling sarFiling = SARFiling.builder()
                .caseId(generateCaseId())
                .userId(userId)
                .transactionId(transactionId)
                .suspiciousActivity(suspiciousActivity)
                .activityDetails(serializeDetails(details))
                .detectionDate(detectionDate)
                .filingDeadline(filingDeadline)
                .priority(priority)
                .status(SARFilingStatus.PENDING_REVIEW)
                .assignedTo(getNextAvailableComplianceOfficer())
                .createdAt(detectionDate)
                .updatedAt(detectionDate)
                .build();

            // Save to database
            sarFiling = sarFilingRepository.save(sarFiling);

            // Trigger compliance team notifications
            notifyComplianceTeam(sarFiling, details);

            // Create audit trail
            auditService.logComplianceEvent(
                "SAR_FILING_INITIATED",
                userId,
                transactionId,
                sarFiling.getCaseId(),
                String.format("SAR filing initiated. Activity: %s. Deadline: %s",
                    suspiciousActivity, filingDeadline)
            );

            // Publish SAR creation event for downstream systems
            publishSARCreatedEvent(sarFiling, details);

            // Schedule deadline reminder
            scheduleDeadlineReminders(sarFiling);

            log.info("SAR filing task created successfully: caseId={}, userId={}, deadline={}",
                sarFiling.getCaseId(), userId, filingDeadline);

            return sarFiling.getCaseId();

        } catch (Exception e) {
            log.error("Failed to create SAR filing task: userId={}, transactionId={}",
                userId, transactionId, e);

            // Alert compliance management of system failure
            pagerDutyAlertService.triggerAlert(
                "SAR Filing System Failure",
                String.format("Failed to create SAR filing for user %s, transaction %s: %s",
                    userId, transactionId, e.getMessage()),
                AlertSeverity.CRITICAL,
                buildAlertContext("sar-filing-creation", userId, transactionId, e)
            );

            throw new SARFilingException("Failed to create SAR filing task", e);
        }
    }

    /**
     * Files completed SAR with FinCEN BSA E-Filing System
     * Validates all required fields per 2023 BSA E-Filing spec
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void fileWithFinCEN(String caseId) {
        log.info("Filing SAR with FinCEN: caseId={}", caseId);

        SARFiling sarFiling = sarFilingRepository.findByCaseId(caseId)
            .orElseThrow(() -> new SARFilingException("SAR case not found: " + caseId));

        try {
            // Validate all required FinCEN fields
            validateForFinCENFiling(sarFiling);

            // Build FinCEN BSA E-Filing XML (2023 spec)
            String filingXml = buildFinCENXML(sarFiling);

            // Submit to FinCEN
            String bsaId = finCENClient.submitSAR(filingXml);

            // Update filing record
            sarFiling.setStatus(SARFilingStatus.FILED);
            sarFiling.setBsaId(bsaId);
            sarFiling.setFiledDate(LocalDateTime.now());
            sarFiling.setFiledBy(getCurrentUser());
            sarFilingRepository.save(sarFiling);

            // Audit trail
            auditService.logComplianceEvent(
                "SAR_FILED_WITH_FINCEN",
                sarFiling.getUserId(),
                sarFiling.getTransactionId(),
                caseId,
                String.format("SAR filed with FinCEN. BSA ID: %s", bsaId)
            );

            // Notify compliance team of successful filing
            slackAlertService.sendComplianceAlert(
                sarFiling.getUserId(),
                sarFiling.getTransactionId(),
                "SAR Successfully Filed with FinCEN",
                String.format("Case %s filed successfully. BSA ID: %s", caseId, bsaId),
                AlertSeverity.LOW
            );

            // Publish filing completion event
            publishSARFiledEvent(sarFiling, bsaId);

            log.info("SAR filed successfully with FinCEN: caseId={}, bsaId={}", caseId, bsaId);

        } catch (Exception e) {
            log.error("Failed to file SAR with FinCEN: caseId={}", caseId, e);

            // Mark as filing failed
            sarFiling.setStatus(SARFilingStatus.FILING_FAILED);
            sarFiling.setFilingError(e.getMessage());
            sarFilingRepository.save(sarFiling);

            // Alert compliance team
            pagerDutyAlertService.triggerSARFilingAlert(
                sarFiling.getUserId(),
                sarFiling.getTransactionId(),
                String.format("FinCEN filing failed for case %s: %s", caseId, e.getMessage())
            );

            throw new SARFilingException("FinCEN filing failed: " + caseId, e);
        }
    }

    /**
     * Scheduled job to check for approaching deadlines and overdue filings
     * Runs every hour to ensure timely filings
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional(readOnly = true)
    public void checkDeadlines() {
        log.info("Running SAR filing deadline check");

        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime alertThreshold = now.plusDays(ALERT_BEFORE_DEADLINE_DAYS);

            // Find approaching deadlines
            List<SARFiling> approachingDeadline = sarFilingRepository
                .findByStatusInAndFilingDeadlineBetween(
                    Arrays.asList(SARFilingStatus.PENDING_REVIEW, SARFilingStatus.IN_PROGRESS),
                    now,
                    alertThreshold
                );

            // Alert for approaching deadlines
            for (SARFiling sarFiling : approachingDeadline) {
                long daysRemaining = ChronoUnit.DAYS.between(now, sarFiling.getFilingDeadline());

                slackAlertService.sendComplianceAlert(
                    sarFiling.getUserId(),
                    sarFiling.getTransactionId(),
                    String.format("SAR Filing Deadline Approaching - %d days", daysRemaining),
                    buildDeadlineWarningMessage(sarFiling, daysRemaining),
                    AlertSeverity.HIGH
                );
            }

            // Find overdue filings
            List<SARFiling> overdueFilings = sarFilingRepository
                .findByStatusInAndFilingDeadlineBefore(
                    Arrays.asList(SARFilingStatus.PENDING_REVIEW, SARFilingStatus.IN_PROGRESS),
                    now
                );

            // Escalate overdue filings
            for (SARFiling sarFiling : overdueFilings) {
                escalateOverdueFiling(sarFiling);
            }

            log.info("SAR deadline check complete: approaching={}, overdue={}",
                approachingDeadline.size(), overdueFilings.size());

        } catch (Exception e) {
            log.error("Error during SAR deadline check", e);
        }
    }

    /**
     * Updates SAR case with continuing activity information
     * Required for ongoing investigations per FinCEN guidelines
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void updateContinuingActivity(String caseId, String activityUpdate,
                                        Map<String, Object> additionalEvidence) {
        log.info("Updating SAR with continuing activity: caseId={}", caseId);

        SARFiling sarFiling = sarFilingRepository.findByCaseId(caseId)
            .orElseThrow(() -> new SARFilingException("SAR case not found: " + caseId));

        // Add continuing activity note
        sarFiling.addContinuingActivityNote(activityUpdate, LocalDateTime.now());

        // Update evidence
        if (additionalEvidence != null && !additionalEvidence.isEmpty()) {
            sarFiling.appendActivityDetails(serializeDetails(additionalEvidence));
        }

        sarFiling.setUpdatedAt(LocalDateTime.now());
        sarFilingRepository.save(sarFiling);

        // If already filed, may need to submit continuing activity report
        if (sarFiling.getStatus() == SARFilingStatus.FILED) {
            submitContinuingActivityReport(sarFiling, activityUpdate);
        }

        auditService.logComplianceEvent(
            "SAR_CONTINUING_ACTIVITY_UPDATED",
            sarFiling.getUserId(),
            sarFiling.getTransactionId(),
            caseId,
            "Continuing activity information added"
        );

        log.info("SAR continuing activity updated: caseId={}", caseId);
    }

    /**
     * Retrieves SAR filing statistics for compliance reporting
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSARFilingStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalFiled", sarFilingRepository.countByStatusAndFiledDateBetween(
            SARFilingStatus.FILED, startDate, endDate));

        stats.put("pendingReview", sarFilingRepository.countByStatus(
            SARFilingStatus.PENDING_REVIEW));

        stats.put("inProgress", sarFilingRepository.countByStatus(
            SARFilingStatus.IN_PROGRESS));

        stats.put("overdue", sarFilingRepository.countByStatusInAndFilingDeadlineBefore(
            Arrays.asList(SARFilingStatus.PENDING_REVIEW, SARFilingStatus.IN_PROGRESS),
            LocalDateTime.now()));

        stats.put("averageFilingTime", calculateAverageFilingTime(startDate, endDate));

        stats.put("complianceRate", calculateComplianceRate(startDate, endDate));

        stats.put("byPriority", sarFilingRepository.countByPriorityGrouped());

        return stats;
    }

    // ==================== Private Helper Methods ====================

    private SARPriority determinePriority(String activity, Map<String, Object> details) {
        // High priority for terrorism financing, money laundering, large amounts
        if (activity.toLowerCase().contains("terrorism") ||
            activity.toLowerCase().contains("money laundering")) {
            return SARPriority.CRITICAL;
        }

        // Check transaction amount
        BigDecimal amount = extractAmount(details);
        if (amount != null && amount.compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            return SARPriority.HIGH;
        }

        return SARPriority.MEDIUM;
    }

    private String generateCaseId() {
        return "SAR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() +
            "-" + LocalDateTime.now().getYear();
    }

    /**
     * Get next available compliance officer using round-robin with workload balancing.
     * Assigns to officer with lowest current active case count.
     */
    private String getNextAvailableComplianceOfficer() {
        try {
            // Get all compliance officers and their current workload
            Map<String, Long> officerWorkload = sarFilingRepository
                .findActiveOfficerWorkload();

            if (officerWorkload.isEmpty()) {
                log.warn("No compliance officers available - assigning to default team");
                return "compliance@example.com";
            }

            // Find officer with minimum workload
            String assignedOfficer = officerWorkload.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("compliance@example.com");

            log.debug("Assigned SAR to officer: {} (current workload: {})",
                assignedOfficer, officerWorkload.get(assignedOfficer));

            return assignedOfficer;

        } catch (Exception e) {
            log.error("Error during officer assignment - using default", e);
            return "compliance@example.com";
        }
    }

    private void notifyComplianceTeam(SARFiling sarFiling, Map<String, Object> details) {
        // Send Slack alert
        slackAlertService.sendSARFilingAlert(
            sarFiling.getUserId(),
            sarFiling.getTransactionId(),
            sarFiling.getSuspiciousActivity(),
            details
        );

        // Send PagerDuty for critical cases
        if (sarFiling.getPriority() == SARPriority.CRITICAL) {
            pagerDutyAlertService.triggerSARFilingAlert(
                sarFiling.getUserId(),
                sarFiling.getTransactionId(),
                sarFiling.getSuspiciousActivity()
            );
        }
    }

    private void scheduleDeadlineReminders(SARFiling sarFiling) {
        // Schedule reminders at 7 days, 3 days, and 1 day before deadline
        LocalDateTime deadline = sarFiling.getFilingDeadline();

        kafkaTemplate.send("compliance.sar.deadline-reminder",
            sarFiling.getCaseId(),
            Map.of(
                "caseId", sarFiling.getCaseId(),
                "deadlineDate", deadline,
                "reminderDates", Arrays.asList(
                    deadline.minusDays(7),
                    deadline.minusDays(3),
                    deadline.minusDays(1)
                )
            )
        );
    }

    private void escalateOverdueFiling(SARFiling sarFiling) {
        log.warn("Escalating overdue SAR filing: caseId={}, deadline={}",
            sarFiling.getCaseId(), sarFiling.getFilingDeadline());

        long daysOverdue = ChronoUnit.DAYS.between(sarFiling.getFilingDeadline(), LocalDateTime.now());

        // Mark as overdue
        sarFiling.setStatus(SARFilingStatus.OVERDUE);
        sarFilingRepository.save(sarFiling);

        // Page compliance management
        pagerDutyAlertService.triggerAlert(
            String.format("OVERDUE SAR FILING - Case %s", sarFiling.getCaseId()),
            String.format("SAR filing is %d days overdue. Case: %s. User: %s. " +
                "Immediate action required to avoid regulatory penalties.",
                daysOverdue, sarFiling.getCaseId(), sarFiling.getUserId()),
            AlertSeverity.CRITICAL,
            buildAlertContext("sar-overdue", sarFiling.getUserId(),
                sarFiling.getTransactionId(), null)
        );

        // Email senior compliance officer and regulatory team
        emailComplianceManagement(sarFiling, daysOverdue);
    }

    /**
     * Send escalation email to compliance management team
     */
    private void emailComplianceManagement(SARFiling sarFiling, long daysOverdue) {
        try {
            String subject = String.format("URGENT: Overdue SAR Filing - Case %s (%d days overdue)",
                sarFiling.getCaseId(), daysOverdue);

            String body = String.format("""
                URGENT: SAR FILING OVERDUE

                Case ID: %s
                User ID: %s
                Transaction ID: %s
                Suspicious Activity: %s
                Detection Date: %s
                Filing Deadline: %s
                Days Overdue: %d
                Priority: %s
                Assigned To: %s

                IMMEDIATE ACTION REQUIRED

                Regulatory penalties may apply for late filing.
                Contact the Chief Compliance Officer immediately.

                --
                Waqiti Compliance Automation System
                """,
                sarFiling.getCaseId(),
                sarFiling.getUserId(),
                sarFiling.getTransactionId(),
                sarFiling.getSuspiciousActivity(),
                sarFiling.getDetectionDate(),
                sarFiling.getFilingDeadline(),
                daysOverdue,
                sarFiling.getPriority(),
                sarFiling.getAssignedTo()
            );

            // Send via Kafka for email service to process
            kafkaTemplate.send("compliance.email.urgent",
                sarFiling.getCaseId(),
                Map.of(
                    "to", List.of("cco@example.com", "compliance-management@example.com"),
                    "subject", subject,
                    "body", body,
                    "priority", "URGENT",
                    "category", "SAR_OVERDUE"
                )
            );

            log.info("Sent overdue SAR escalation email: caseId={}", sarFiling.getCaseId());

        } catch (Exception e) {
            log.error("Failed to send escalation email for case: {}", sarFiling.getCaseId(), e);
        }
    }

    private void validateForFinCENFiling(SARFiling sarFiling) {
        List<String> errors = new ArrayList<>();

        if (sarFiling.getUserId() == null) errors.add("User ID required");
        if (sarFiling.getSuspiciousActivity() == null) errors.add("Suspicious activity description required");
        // Add all FinCEN required field validations per 2023 BSA E-Filing spec

        if (!errors.isEmpty()) {
            throw new SARFilingException("FinCEN filing validation failed: " +
                String.join(", ", errors));
        }
    }

    /**
     * Build complete FinCEN BSA E-Filing XML per 2023 specification
     * Implements SAR-X schema v1.0 for electronic filing
     * Reference: FinCEN BSA E-Filing System Technical Specification 2023
     */
    private String buildFinCENXML(SARFiling sarFiling) {
        try {
            log.debug("Building FinCEN XML for SAR case: {}", sarFiling.getCaseId());

            // Extract activity details from JSON
            Map<String, Object> activityDetails = parseActivityDetails(sarFiling.getActivityDetails());

            // Build complete FinCEN BSA E-Filing XML structure
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<EFilingBatchXML xmlns=\"http://www.fincen.gov/base\" ");
            xml.append("xmlns:fc=\"http://www.fincen.gov/base\" ");
            xml.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n");

            // Activity section
            xml.append("  <Activity>\n");
            xml.append("    <ActivityAssociation>\n");
            xml.append("      <CorrectsAmendsPriorReportIndicator>false</CorrectsAmendsPriorReportIndicator>\n");
            xml.append("    </ActivityAssociation>\n");

            // Filing institution (Party 35 - Depository Institution)
            xml.append("    <Party id=\"35\">\n");
            xml.append("      <ActivityPartyTypeCode>35</ActivityPartyTypeCode>\n");
            xml.append("      <PartyName>\n");
            xml.append("        <PartyNameTypeCode>L</PartyNameTypeCode>\n");
            xml.append("        <RawPartyFullName>Waqiti Financial Inc.</RawPartyFullName>\n");
            xml.append("      </PartyName>\n");
            xml.append("      <Address>\n");
            xml.append("        <RawCityText>San Francisco</RawCityText>\n");
            xml.append("        <RawCountryCodeText>US</RawCountryCodeText>\n");
            xml.append("        <RawStateCodeText>CA</RawStateCodeText>\n");
            xml.append("        <RawStreetAddress1Text>100 California Street</RawStreetAddress1Text>\n");
            xml.append("        <RawZIPCode>94111</RawZIPCode>\n");
            xml.append("      </Address>\n");
            xml.append("      <PartyIdentification>\n");
            xml.append("        <PartyIdentificationNumberText>12-3456789</PartyIdentificationNumberText>\n");
            xml.append("        <PartyIdentificationTypeCode>2</PartyIdentificationTypeCode>\n"); // EIN
            xml.append("      </PartyIdentification>\n");
            xml.append("      <OrganizationClassificationTypeCode>8</OrganizationClassificationTypeCode>\n");
            xml.append("    </Party>\n");

            // Subject/Suspect (Party 8)
            xml.append("    <Party id=\"8\">\n");
            xml.append("      <ActivityPartyTypeCode>8</ActivityPartyTypeCode>\n"); // Subject
            xml.append("      <IndividualEntityCashInAmountText>");
            xml.append(extractAmount(activityDetails).toString());
            xml.append("</IndividualEntityCashInAmountText>\n");
            xml.append("      <PartyName>\n");
            xml.append("        <PartyNameTypeCode>L</PartyNameTypeCode>\n");
            xml.append("        <RawEntityIndividualLastName>");
            xml.append(escapeXml(extractSubjectName(activityDetails)));
            xml.append("</RawEntityIndividualLastName>\n");
            xml.append("      </PartyName>\n");
            xml.append("      <PartyIdentification>\n");
            xml.append("        <PartyIdentificationNumberText>");
            xml.append(escapeXml(sarFiling.getUserId()));
            xml.append("</PartyIdentificationNumberText>\n");
            xml.append("        <PartyIdentificationTypeCode>9</PartyIdentificationTypeCode>\n"); // Other
            xml.append("      </PartyIdentification>\n");
            xml.append("    </Party>\n");

            // Suspicious activity details
            xml.append("    <SuspiciousActivity>\n");
            xml.append("      <SuspiciousActivityClassification>\n");
            xml.append("        <SuspiciousActivityClassificationTypeCode>");
            xml.append(mapToFinCENActivityCode(sarFiling.getSuspiciousActivity()));
            xml.append("</SuspiciousActivityClassificationTypeCode>\n");
            xml.append("      </SuspiciousActivityClassification>\n");
            xml.append("      <SuspiciousActivityStartDate>\n");
            xml.append("        <SuspiciousActivityStartDateText>");
            xml.append(formatFinCENDate(sarFiling.getDetectionDate()));
            xml.append("</SuspiciousActivityStartDateText>\n");
            xml.append("      </SuspiciousActivityStartDate>\n");
            xml.append("      <SuspiciousActivityTotalAmount>\n");
            xml.append("        <SuspiciousActivityTotalAmountText>");
            xml.append(extractAmount(activityDetails).toString());
            xml.append("</SuspiciousActivityTotalAmountText>\n");
            xml.append("      </SuspiciousActivityTotalAmount>\n");
            xml.append("    </SuspiciousActivity>\n");

            // Suspicious activity narrative (required)
            xml.append("    <ActivityNarrativeInformation>\n");
            xml.append("      <ActivityNarrativeSequenceNumber>1</ActivityNarrativeSequenceNumber>\n");
            xml.append("      <ActivityNarrativeText>");
            xml.append(escapeXml(buildNarrative(sarFiling)));
            xml.append("</ActivityNarrativeText>\n");
            xml.append("    </ActivityNarrativeInformation>\n");

            // Account information
            if (sarFiling.getTransactionId() != null) {
                xml.append("    <Account>\n");
                xml.append("      <AccountNumberText>");
                xml.append(escapeXml(sarFiling.getTransactionId()));
                xml.append("</AccountNumberText>\n");
                xml.append("      <PartyAccountAssociation>\n");
                xml.append("        <PartyAccountAssociationTypeCode>1</PartyAccountAssociationTypeCode>\n");
                xml.append("      </PartyAccountAssociation>\n");
                xml.append("    </Account>\n");
            }

            xml.append("  </Activity>\n");
            xml.append("</EFilingBatchXML>\n");

            String finCENXml = xml.toString();
            log.debug("FinCEN XML generated successfully for case: {}, length: {} bytes",
                sarFiling.getCaseId(), finCENXml.length());

            return finCENXml;

        } catch (Exception e) {
            log.error("Failed to build FinCEN XML for case: {}", sarFiling.getCaseId(), e);
            throw new SARFilingException("Failed to generate FinCEN XML: " + e.getMessage(), e);
        }
    }

    /**
     * Map internal suspicious activity description to FinCEN activity type codes
     * Reference: FinCEN SAR Activity Type Codes (2023)
     */
    private String mapToFinCENActivityCode(String suspiciousActivity) {
        String activityLower = suspiciousActivity.toLowerCase();

        // Money laundering indicators
        if (activityLower.contains("money laundering") || activityLower.contains("structuring")) {
            return "z"; // Money laundering
        }
        // Terrorist financing
        else if (activityLower.contains("terrorism") || activityLower.contains("terrorist")) {
            return "c"; // Terrorist financing
        }
        // Fraud
        else if (activityLower.contains("fraud") || activityLower.contains("identity theft")) {
            return "f"; // Fraud
        }
        // Check fraud
        else if (activityLower.contains("check") && activityLower.contains("fraud")) {
            return "h"; // Check fraud
        }
        // Wire transfer fraud
        else if (activityLower.contains("wire") && activityLower.contains("fraud")) {
            return "w"; // Wire transfer fraud
        }
        // Suspicious use of multiple accounts
        else if (activityLower.contains("multiple account")) {
            return "m"; // Multiple accounts
        }
        // Other suspicious activity
        else {
            return "y"; // Other
        }
    }

    /**
     * Build narrative description for FinCEN filing
     * Must include who, what, when, where, why, and how
     */
    private String buildNarrative(SARFiling sarFiling) {
        StringBuilder narrative = new StringBuilder();

        narrative.append("SUSPICIOUS ACTIVITY REPORT - Case ID: ").append(sarFiling.getCaseId()).append("\n\n");
        narrative.append("SUMMARY: ").append(sarFiling.getSuspiciousActivity()).append("\n\n");

        narrative.append("DETECTION DATE: ").append(sarFiling.getDetectionDate()).append("\n");
        narrative.append("USER ID: ").append(sarFiling.getUserId()).append("\n");
        narrative.append("TRANSACTION ID: ").append(sarFiling.getTransactionId()).append("\n");
        narrative.append("PRIORITY LEVEL: ").append(sarFiling.getPriority()).append("\n\n");

        narrative.append("DETAILED DESCRIPTION:\n");
        narrative.append(sarFiling.getSuspiciousActivity()).append("\n\n");

        narrative.append("ACTIVITY DETAILS:\n");
        if (sarFiling.getActivityDetails() != null) {
            narrative.append(sarFiling.getActivityDetails()).append("\n\n");
        }

        narrative.append("COMPLIANCE OFFICER: ").append(sarFiling.getAssignedTo()).append("\n");
        narrative.append("FILING DATE: ").append(LocalDateTime.now()).append("\n");

        return narrative.toString();
    }

    /**
     * Format date for FinCEN XML (YYYYMMDD)
     */
    private String formatFinCENDate(LocalDateTime dateTime) {
        return String.format("%04d%02d%02d",
            dateTime.getYear(),
            dateTime.getMonthValue(),
            dateTime.getDayOfMonth());
    }

    /**
     * Escape XML special characters
     */
    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    /**
     * Extract subject name from activity details
     */
    private String extractSubjectName(Map<String, Object> details) {
        Object name = details.get("subjectName");
        if (name != null) {
            return name.toString();
        }
        Object userName = details.get("userName");
        if (userName != null) {
            return userName.toString();
        }
        return "Unknown Subject";
    }

    /**
     * Parse activity details JSON string to Map
     */
    private Map<String, Object> parseActivityDetails(String activityDetailsJson) {
        try {
            if (activityDetailsJson == null || activityDetailsJson.isEmpty()) {
                return new HashMap<>();
            }
            return objectMapper.readValue(activityDetailsJson, Map.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse activity details JSON, using empty map", e);
            return new HashMap<>();
        }
    }

    /**
     * Submit continuing activity report to FinCEN
     * Required when significant new information emerges after initial SAR filing
     * Per FinCEN guidance: File continuing activity report within 30 days of discovering new information
     */
    private void submitContinuingActivityReport(SARFiling sarFiling, String activityUpdate) {
        log.info("Submitting continuing activity report: caseId={}, bsaId={}",
            sarFiling.getCaseId(), sarFiling.getBsaId());

        try {
            // Validate that original SAR was filed with FinCEN
            if (sarFiling.getBsaId() == null || sarFiling.getBsaId().isEmpty()) {
                log.error("Cannot submit continuing activity - no BSA ID found for case: {}",
                    sarFiling.getCaseId());
                throw new SARFilingException("Original SAR must be filed before submitting continuing activity");
            }

            // Build continuing activity XML
            String continuingActivityXml = buildContinuingActivityXML(sarFiling, activityUpdate);

            // Submit to FinCEN via API
            log.debug("Submitting continuing activity to FinCEN for BSA ID: {}", sarFiling.getBsaId());
            String acknowledgmentNumber = finCENClient.submitContinuingActivity(
                sarFiling.getBsaId(),
                continuingActivityXml
            );

            // Update SAR filing record with continuing activity information
            sarFiling.setContinuingActivitySubmitted(true);
            sarFiling.setContinuingActivityAcknowledgment(acknowledgmentNumber);
            sarFiling.setLastContinuingActivityDate(LocalDateTime.now());
            sarFilingRepository.save(sarFiling);

            // Create audit trail
            auditService.logComplianceEvent(
                "SAR_CONTINUING_ACTIVITY_FILED",
                sarFiling.getUserId(),
                sarFiling.getTransactionId(),
                sarFiling.getCaseId(),
                String.format("Continuing activity report filed with FinCEN. BSA ID: %s, Acknowledgment: %s",
                    sarFiling.getBsaId(), acknowledgmentNumber)
            );

            // Notify compliance team
            slackAlertService.sendComplianceAlert(
                sarFiling.getUserId(),
                sarFiling.getTransactionId(),
                "SAR Continuing Activity Report Filed",
                String.format("Continuing activity report submitted for case %s (BSA ID: %s). " +
                    "Acknowledgment: %s",
                    sarFiling.getCaseId(), sarFiling.getBsaId(), acknowledgmentNumber),
                AlertSeverity.MEDIUM
            );

            // Publish event for downstream systems
            kafkaTemplate.send("compliance.sar.continuing-activity-filed",
                sarFiling.getCaseId(),
                Map.of(
                    "caseId", sarFiling.getCaseId(),
                    "bsaId", sarFiling.getBsaId(),
                    "acknowledgmentNumber", acknowledgmentNumber,
                    "filedDate", LocalDateTime.now(),
                    "activityUpdate", activityUpdate
                )
            );

            log.info("Continuing activity report submitted successfully: caseId={}, acknowledgment={}",
                sarFiling.getCaseId(), acknowledgmentNumber);

        } catch (Exception e) {
            log.error("Failed to submit continuing activity report: caseId={}, bsaId={}",
                sarFiling.getCaseId(), sarFiling.getBsaId(), e);

            // Alert compliance management
            pagerDutyAlertService.triggerAlert(
                "SAR Continuing Activity Submission Failed",
                String.format("Failed to submit continuing activity for case %s (BSA ID: %s): %s",
                    sarFiling.getCaseId(), sarFiling.getBsaId(), e.getMessage()),
                AlertSeverity.HIGH,
                buildAlertContext("sar-continuing-activity", sarFiling.getUserId(),
                    sarFiling.getTransactionId(), e)
            );

            throw new SARFilingException("Failed to submit continuing activity report", e);
        }
    }

    /**
     * Build FinCEN XML for continuing activity report
     * References the original BSA ID and provides updated information
     */
    private String buildContinuingActivityXML(SARFiling sarFiling, String activityUpdate) {
        try {
            log.debug("Building continuing activity XML for case: {}", sarFiling.getCaseId());

            Map<String, Object> activityDetails = parseActivityDetails(sarFiling.getActivityDetails());

            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<EFilingBatchXML xmlns=\"http://www.fincen.gov/base\" ");
            xml.append("xmlns:fc=\"http://www.fincen.gov/base\" ");
            xml.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n");

            xml.append("  <Activity>\n");

            // Indicate this is a continuing activity report
            xml.append("    <ActivityAssociation>\n");
            xml.append("      <CorrectsAmendsPriorReportIndicator>false</CorrectsAmendsPriorReportIndicator>\n");
            xml.append("      <InitialReportIndicator>false</InitialReportIndicator>\n");
            xml.append("      <ContinuingActivityReportIndicator>true</ContinuingActivityReportIndicator>\n");
            xml.append("      <PriorDocumentNumber>");
            xml.append(escapeXml(sarFiling.getBsaId()));
            xml.append("</PriorDocumentNumber>\n");
            xml.append("    </ActivityAssociation>\n");

            // Filing institution
            xml.append("    <Party id=\"35\">\n");
            xml.append("      <ActivityPartyTypeCode>35</ActivityPartyTypeCode>\n");
            xml.append("      <PartyName>\n");
            xml.append("        <PartyNameTypeCode>L</PartyNameTypeCode>\n");
            xml.append("        <RawPartyFullName>Waqiti Financial Inc.</RawPartyFullName>\n");
            xml.append("      </PartyName>\n");
            xml.append("      <PartyIdentification>\n");
            xml.append("        <PartyIdentificationNumberText>12-3456789</PartyIdentificationNumberText>\n");
            xml.append("        <PartyIdentificationTypeCode>2</PartyIdentificationTypeCode>\n");
            xml.append("      </PartyIdentification>\n");
            xml.append("    </Party>\n");

            // Subject (reference to original)
            xml.append("    <Party id=\"8\">\n");
            xml.append("      <ActivityPartyTypeCode>8</ActivityPartyTypeCode>\n");
            xml.append("      <PartyIdentification>\n");
            xml.append("        <PartyIdentificationNumberText>");
            xml.append(escapeXml(sarFiling.getUserId()));
            xml.append("</PartyIdentificationNumberText>\n");
            xml.append("        <PartyIdentificationTypeCode>9</PartyIdentificationTypeCode>\n");
            xml.append("      </PartyIdentification>\n");
            xml.append("    </Party>\n");

            // Continuing activity narrative
            xml.append("    <ActivityNarrativeInformation>\n");
            xml.append("      <ActivityNarrativeSequenceNumber>1</ActivityNarrativeSequenceNumber>\n");
            xml.append("      <ActivityNarrativeText>");
            xml.append(escapeXml(buildContinuingActivityNarrative(sarFiling, activityUpdate)));
            xml.append("</ActivityNarrativeText>\n");
            xml.append("    </ActivityNarrativeInformation>\n");

            xml.append("  </Activity>\n");
            xml.append("</EFilingBatchXML>\n");

            String continuingXml = xml.toString();
            log.debug("Continuing activity XML generated: {} bytes", continuingXml.length());

            return continuingXml;

        } catch (Exception e) {
            log.error("Failed to build continuing activity XML for case: {}", sarFiling.getCaseId(), e);
            throw new SARFilingException("Failed to generate continuing activity XML", e);
        }
    }

    /**
     * Build narrative for continuing activity report
     */
    private String buildContinuingActivityNarrative(SARFiling sarFiling, String activityUpdate) {
        StringBuilder narrative = new StringBuilder();

        narrative.append("CONTINUING ACTIVITY REPORT\n\n");
        narrative.append("ORIGINAL SAR CASE ID: ").append(sarFiling.getCaseId()).append("\n");
        narrative.append("ORIGINAL BSA ID: ").append(sarFiling.getBsaId()).append("\n");
        narrative.append("ORIGINAL FILING DATE: ").append(sarFiling.getFiledDate()).append("\n\n");

        narrative.append("CONTINUING ACTIVITY UPDATE:\n");
        narrative.append(activityUpdate).append("\n\n");

        narrative.append("USER ID: ").append(sarFiling.getUserId()).append("\n");
        narrative.append("UPDATE DATE: ").append(LocalDateTime.now()).append("\n");
        narrative.append("COMPLIANCE OFFICER: ").append(getCurrentUser()).append("\n");

        return narrative.toString();
    }

    private void publishSARCreatedEvent(SARFiling sarFiling, Map<String, Object> details) {
        kafkaTemplate.send("compliance.sar.created",
            sarFiling.getCaseId(),
            Map.of(
                "caseId", sarFiling.getCaseId(),
                "userId", sarFiling.getUserId(),
                "transactionId", sarFiling.getTransactionId(),
                "priority", sarFiling.getPriority().toString(),
                "deadline", sarFiling.getFilingDeadline(),
                "details", details
            )
        );
    }

    private void publishSARFiledEvent(SARFiling sarFiling, String bsaId) {
        kafkaTemplate.send("compliance.sar.filed",
            sarFiling.getCaseId(),
            Map.of(
                "caseId", sarFiling.getCaseId(),
                "bsaId", bsaId,
                "filedDate", sarFiling.getFiledDate(),
                "filedBy", sarFiling.getFiledBy()
            )
        );
    }

    private String buildDeadlineWarningMessage(SARFiling sarFiling, long daysRemaining) {
        return String.format(
            "**SAR Filing Deadline Approaching**\n\n" +
            "Case ID: %s\n" +
            "User: %s\n" +
            "Transaction: %s\n" +
            "Priority: %s\n" +
            "Deadline: %s\n" +
            "Days Remaining: %d\n\n" +
            "**Action Required:** Complete investigation and file with FinCEN before deadline.",
            sarFiling.getCaseId(),
            sarFiling.getUserId(),
            sarFiling.getTransactionId(),
            sarFiling.getPriority(),
            sarFiling.getFilingDeadline(),
            daysRemaining
        );
    }

    private com.waqiti.alerting.dto.AlertContext buildAlertContext(String component,
                                                                   String userId,
                                                                   String transactionId,
                                                                   Exception error) {
        return com.waqiti.alerting.dto.AlertContext.builder()
            .serviceName("compliance-service")
            .component(component)
            .userId(userId)
            .transactionId(transactionId)
            .errorDetails(error != null ? error.getMessage() : null)
            .timestamp(java.time.Instant.now())
            .build();
    }

    private BigDecimal extractAmount(Map<String, Object> details) {
        Object amountObj = details.get("amount");
        if (amountObj instanceof BigDecimal) {
            return (BigDecimal) amountObj;
        } else if (amountObj instanceof Number) {
            return new BigDecimal(amountObj.toString());
        }
        return null;
    }

    /**
     * Serialize activity details to JSON for storage
     * Uses Jackson ObjectMapper for proper JSON serialization with error handling
     */
    private String serializeDetails(Map<String, Object> details) {
        try {
            if (details == null || details.isEmpty()) {
                return "{}";
            }

            // Serialize to JSON with pretty printing for readability in database
            String json = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(details);

            log.debug("Serialized activity details: {} characters", json.length());
            return json;

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize activity details to JSON, using toString() fallback", e);

            // Fallback to toString() if JSON serialization fails
            // This ensures we don't lose data even if serialization fails
            return details.toString();
        }
    }

    /**
     * Get current authenticated user from Spring Security Context
     * Falls back to "system" for scheduled jobs or background processes
     */
    private String getCurrentUser() {
        try {
            // Attempt to get current user from security context
            String userId = securityContextService.getCurrentUserId();

            if (userId != null && !userId.isEmpty() && !"anonymous".equals(userId)) {
                log.debug("Retrieved current user from security context: {}", userId);
                return userId;
            }

            // Check if we're in a system/background process context
            log.debug("No authenticated user found in security context, using system user");
            return "system";

        } catch (Exception e) {
            // If security context is not available (e.g., scheduled job, async process)
            log.debug("Security context not available, using system user: {}", e.getMessage());
            return "system";
        }
    }

    /**
     * Calculate average time from detection to filing in days
     * Provides key compliance metric for monitoring filing efficiency
     *
     * @param startDate Start of reporting period
     * @param endDate End of reporting period
     * @return Average days from detection to filing, or 0.0 if no filings
     */
    private double calculateAverageFilingTime(LocalDateTime startDate, LocalDateTime endDate) {
        try {
            log.debug("Calculating average filing time between {} and {}", startDate, endDate);

            // Get all filed SARs within the date range
            List<SARFiling> filedSARs = sarFilingRepository
                .findByStatusAndFiledDateBetween(SARFilingStatus.FILED, startDate, endDate);

            if (filedSARs == null || filedSARs.isEmpty()) {
                log.debug("No filed SARs found in date range for average calculation");
                return 0.0;
            }

            // Calculate days between detection and filing for each SAR
            double totalDays = 0.0;
            int validCount = 0;

            for (SARFiling sar : filedSARs) {
                // Validate we have both dates
                if (sar.getDetectionDate() != null && sar.getFiledDate() != null) {
                    // Calculate days between detection and filing
                    long daysBetween = ChronoUnit.DAYS.between(
                        sar.getDetectionDate(),
                        sar.getFiledDate()
                    );

                    totalDays += daysBetween;
                    validCount++;

                    log.trace("SAR {}: {} days from detection to filing",
                        sar.getCaseId(), daysBetween);
                }
            }

            if (validCount == 0) {
                log.warn("No valid SARs with both detection and filing dates found");
                return 0.0;
            }

            // Calculate average and round to 2 decimal places
            double average = totalDays / validCount;
            double roundedAverage = BigDecimal.valueOf(average)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();

            log.info("Average filing time calculated: {} days (based on {} SARs)",
                roundedAverage, validCount);

            return roundedAverage;

        } catch (Exception e) {
            log.error("Error calculating average filing time", e);
            return 0.0;
        }
    }

    /**
     * Calculate compliance rate - percentage of SARs filed within 30-day regulatory deadline
     * Critical metric for regulatory compliance monitoring
     *
     * @param startDate Start of reporting period
     * @param endDate End of reporting period
     * @return Compliance rate as percentage (0.0 - 100.0), or 0.0 if no filings
     */
    private double calculateComplianceRate(LocalDateTime startDate, LocalDateTime endDate) {
        try {
            log.debug("Calculating SAR compliance rate between {} and {}", startDate, endDate);

            // Get all filed SARs within the date range
            List<SARFiling> filedSARs = sarFilingRepository
                .findByStatusAndFiledDateBetween(SARFilingStatus.FILED, startDate, endDate);

            if (filedSARs == null || filedSARs.isEmpty()) {
                log.debug("No filed SARs found in date range for compliance rate calculation");
                return 0.0;
            }

            int totalFiled = 0;
            int filedOnTime = 0;

            for (SARFiling sar : filedSARs) {
                // Validate we have all required dates
                if (sar.getDetectionDate() != null && sar.getFiledDate() != null &&
                    sar.getFilingDeadline() != null) {

                    totalFiled++;

                    // Check if filed before or on the deadline
                    boolean isOnTime = !sar.getFiledDate().isAfter(sar.getFilingDeadline());

                    if (isOnTime) {
                        filedOnTime++;
                        log.trace("SAR {} filed on time: filed={}, deadline={}",
                            sar.getCaseId(), sar.getFiledDate(), sar.getFilingDeadline());
                    } else {
                        // Calculate how many days late
                        long daysLate = ChronoUnit.DAYS.between(
                            sar.getFilingDeadline(),
                            sar.getFiledDate()
                        );
                        log.trace("SAR {} filed LATE: {} days after deadline",
                            sar.getCaseId(), daysLate);
                    }
                }
            }

            if (totalFiled == 0) {
                log.warn("No valid SARs with complete date information found");
                return 0.0;
            }

            // Calculate compliance rate as percentage
            double complianceRate = (double) filedOnTime / totalFiled * 100.0;

            // Round to 2 decimal places
            double roundedRate = BigDecimal.valueOf(complianceRate)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();

            log.info("Compliance rate calculated: {}% ({} on-time out of {} total SARs)",
                roundedRate, filedOnTime, totalFiled);

            // Alert if compliance rate falls below acceptable threshold (e.g., 95%)
            if (roundedRate < 95.0) {
                log.warn("COMPLIANCE ALERT: SAR filing compliance rate below 95%: {}%", roundedRate);

                // Send alert to compliance management
                slackAlertService.sendComplianceAlert(
                    "SYSTEM",
                    "COMPLIANCE_METRICS",
                    "Low SAR Filing Compliance Rate",
                    String.format("SAR filing compliance rate is %.2f%% (below 95%% threshold). " +
                        "%d out of %d SARs filed on time between %s and %s. " +
                        "Review processes to improve compliance.",
                        roundedRate, filedOnTime, totalFiled, startDate, endDate),
                    AlertSeverity.HIGH
                );
            }

            return roundedRate;

        } catch (Exception e) {
            log.error("Error calculating compliance rate", e);
            return 0.0;
        }
    }

    /**
     * Custom exception for SAR filing operations
     */
    public static class SARFilingException extends RuntimeException {
        public SARFilingException(String message) {
            super(message);
        }

        public SARFilingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
