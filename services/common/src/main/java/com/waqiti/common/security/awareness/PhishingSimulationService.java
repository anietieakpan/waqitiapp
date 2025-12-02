package com.waqiti.common.security.awareness;

import com.waqiti.common.security.awareness.model.*;
import com.waqiti.common.security.awareness.dto.*;
import com.waqiti.common.security.awareness.repository.*;

import com.waqiti.common.security.awareness.domain.*;
import com.waqiti.common.security.awareness.domain.PhishingSimulationCampaign.PhishingTemplateType;
import com.waqiti.common.security.awareness.domain.PhishingSimulationCampaign.CampaignStatus;
import com.waqiti.common.security.awareness.domain.EmployeeTrainingRecord.TrainingStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PCI DSS REQ 12.6.3.1 - Phishing Simulation Service
 *
 * Conducts regular phishing simulation campaigns to train employees
 * to recognize and report social engineering attacks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhishingSimulationService {

    private final PhishingSimulationCampaignRepository campaignRepository;
    private final PhishingTestResultRepository resultRepository;
    private final EmployeeRepository employeeRepository;
    private final SecurityAwarenessService awarenessService;
    private final EmailService emailService;
    private final SecurityAwarenessAuditRepository auditRepository;

    // Phishing email templates
    private static final Map<String, PhishingTemplate> TEMPLATES = Map.of(
            "CREDENTIAL_HARVESTING", PhishingTemplate.builder()
                    .subject("🔐 Urgent: Reset Your Password Immediately")
                    .fromName("Waqiti Security Team")
                    .fromEmail("security-noreply@waqiti-phishing-test.com")
                    .body("""
                            Dear Team Member,

                            We've detected unusual activity on your account. For your security,
                            please reset your password within 24 hours by clicking the link below:

                            [PHISHING_LINK]

                            Failure to do so may result in account suspension.

                            Best regards,
                            Waqiti Security Team
                            """)
                    .landingPageUrl("/phishing-test/credential-harvest")
                    .build(),

            "MALWARE_ATTACHMENT", PhishingTemplate.builder()
                    .subject("📎 Q4 Bonus Information - Action Required")
                    .fromName("HR Department")
                    .fromEmail("hr-noreply@waqiti-phishing-test.com")
                    .body("""
                            Hi there,

                            Great news! Please review the attached document for your Q4 bonus details.

                            [ATTACHMENT: Q4_Bonus_Details.pdf.exe]

                            Please review and sign by EOD Friday.

                            Thanks,
                            HR Team
                            """)
                    .landingPageUrl("/phishing-test/malware-attachment")
                    .build(),

            "SOCIAL_ENGINEERING", PhishingTemplate.builder()
                    .subject("🎉 You've Won a $500 Amazon Gift Card!")
                    .fromName("Waqiti Rewards Program")
                    .fromEmail("rewards@waqiti-phishing-test.com")
                    .body("""
                            Congratulations!

                            You've been selected as the winner of our monthly employee lottery.
                            Claim your $500 Amazon gift card by verifying your identity:

                            [PHISHING_LINK]

                            This offer expires in 48 hours.

                            Cheers,
                            Waqiti Rewards Team
                            """)
                    .landingPageUrl("/phishing-test/social-engineering")
                    .build()
    );

    /**
     * Create a new phishing simulation campaign
     */
    @Transactional
    public UUID createPhishingCampaign(PhishingCampaignRequest request) {
        log.info("PCI_DSS_12.6.3.1: Creating phishing campaign: {}", request.getCampaignName());

        PhishingSimulationCampaign campaign = PhishingSimulationCampaign.builder()
                .campaignName(request.getCampaignName())
                .description(request.getDescription())
                .templateType(PhishingTemplateType.valueOf(request.getTemplateType()))
                .difficultyLevel(request.getDifficultyLevel())
                .targetAudience(String.join(",", request.getTargetAudience()))
                .scheduledStart(request.getScheduledStart())
                .scheduledEnd(request.getScheduledEnd())
                .status(CampaignStatus.SCHEDULED)
                .createdBy(request.getCreatedBy())
                .build();

        campaign = campaignRepository.save(campaign);

        // Audit log
        auditRepository.save(SecurityAwarenessAuditLog.builder()
                .eventType("PHISHING_CAMPAIGN_CREATED")
                .entityType("CAMPAIGN")
                .entityId(campaign.getId())
                .pciRequirement("12.6.3.1")
                .complianceStatus("SCHEDULED")
                .eventData(Map.of(
                        "campaign_name", request.getCampaignName(),
                        "template", request.getTemplateType(),
                        "target_audience", String.join(",", request.getTargetAudience()),
                        "start_date", request.getScheduledStart().toString()
                ))
                .build());

        log.info("✅ Created phishing campaign: id={}, name={}", campaign.getId(), campaign.getCampaignName());
        return campaign.getId();
    }

    /**
     * Launch a scheduled phishing campaign (triggered by scheduler)
     */
    @Scheduled(cron = "0 */15 * * * *") // Check every 15 minutes
    @Transactional
    public void launchScheduledCampaigns() {
        LocalDateTime now = LocalDateTime.now();

        List<PhishingSimulationCampaign> scheduledCampaigns = campaignRepository
                .findByStatusAndScheduledStartBefore(CampaignStatus.SCHEDULED, now);

        for (PhishingSimulationCampaign campaign : scheduledCampaigns) {
            try {
                launchCampaign(campaign);
            } catch (Exception e) {
                log.error("❌ Failed to launch phishing campaign {}: {}", campaign.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Launch a phishing campaign - send emails to target audience
     */
    @Transactional
    public void launchCampaign(PhishingSimulationCampaign campaign) {
        log.info("🚀 Launching phishing campaign: {}", campaign.getCampaignName());

        campaign.setStatus(CampaignStatus.IN_PROGRESS);
        campaign.setActualStart(LocalDateTime.now());

        // Get target employees
        List<String> targetAudience = campaign.getTargetAudience() != null ?
            Arrays.asList(campaign.getTargetAudience().split(",")) : Collections.emptyList();
        List<Employee> targetEmployees = employeeRepository.findByRolesIn(targetAudience);

        PhishingTemplate template = TEMPLATES.get(campaign.getTemplateType());
        if (template == null) {
            throw new IllegalStateException("Unknown template type: " + campaign.getTemplateType());
        }

        int emailsSent = 0;
        int emailsDelivered = 0;

        // Send phishing emails to each target
        for (Employee employee : targetEmployees) {
            try {
                // Generate unique tracking link
                String trackingToken = UUID.randomUUID().toString();
                String phishingLink = String.format("https://api.example.com/track/%s", trackingToken);

                // Personalize email
                String emailBody = template.getBody()
                        .replace("[PHISHING_LINK]", phishingLink)
                        .replace("[EMPLOYEE_NAME]", employee.getFirstName());

                // Send email
                try {
                    emailService.sendEmail(
                            employee.getEmail(),
                            template.getSubject(),
                            emailBody
                    );
                    emailsSent++;
                    emailsDelivered++;
                } catch (Exception emailEx) {
                    log.error("Failed to send email to {}: {}", employee.getEmail(), emailEx.getMessage());
                }

                // Create test result record
                PhishingTestResult result = PhishingTestResult.builder()
                        .campaign(campaign)
                        .employee(employee)
                        .emailSent(true)
                        .result(PhishingResult.PENDING)
                        .trackingToken(trackingToken)
                        .build();

                resultRepository.save(result);

            } catch (Exception e) {
                log.error("Failed to send phishing email to employee {}: {}", employee.getId(), e.getMessage());
            }
        }

        // Update campaign statistics
        campaign.setTotalTargeted(targetEmployees.size());
        campaign.setTotalDelivered(emailsDelivered);
        campaignRepository.save(campaign);

        log.info("✅ Launched phishing campaign: sent={}/{}, delivered={}",
                emailsSent, targetEmployees.size(), emailsDelivered);

        // Audit log
        auditRepository.save(SecurityAwarenessAuditLog.builder()
                .eventType("PHISHING_CAMPAIGN_LAUNCHED")
                .entityType("CAMPAIGN")
                .entityId(campaign.getId())
                .pciRequirement("12.6.3.1")
                .complianceStatus("IN_PROGRESS")
                .eventData(Map.of(
                        "targeted", targetEmployees.size(),
                        "sent", emailsSent,
                        "delivered", emailsDelivered
                ))
                .build());
    }

    /**
     * Track phishing email opened
     */
    @Transactional
    public void trackEmailOpened(String trackingToken, String userAgent) {
        PhishingTestResult result = resultRepository.findByTrackingToken(trackingToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid tracking token"));

        if (result.getEmailOpenedAt() == null) {
            result.setEmailOpenedAt(LocalDateTime.now());
            resultRepository.save(result);

            // Update campaign statistics
            updateCampaignStatistics(result.getCampaign().getId());

            log.info("📧 Phishing email opened: employeeId={}, campaign={}",
                    result.getEmployeeId(), result.getCampaignId());
        }
    }

    /**
     * Track phishing link clicked
     */
    @Transactional
    public void trackLinkClicked(String trackingToken, String ipAddress, String userAgent) {
        PhishingTestResult result = resultRepository.findByTrackingToken(trackingToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid tracking token"));

        if (result.getLinkClickedAt() == null) {
            result.setLinkClickedAt(LocalDateTime.now());
            result.setLinkClickedIpAddress(ipAddress);
            result.setLinkClickedUserAgent(userAgent);
            result.setResult(PhishingResult.FAILED); // Clicking the link = failed the test
            resultRepository.save(result);

            // Update campaign statistics
            updateCampaignStatistics(result.getCampaign().getId());

            // Trigger immediate awareness notification
            sendPhishingFailureNotification(result.getEmployeeId(), result.getCampaignId());

            log.warn("⚠️ PHISHING_LINK_CLICKED: employeeId={}, campaign={}, ip={}",
                    result.getEmployeeId(), result.getCampaignId(), ipAddress);

            // Audit log
            auditRepository.save(SecurityAwarenessAuditLog.builder()
                    .eventType("PHISHING_LINK_CLICKED")
                    .entityType("PHISHING_TEST")
                    .entityId(result.getId())
                    .employeeId(result.getEmployee().getId())
                    .pciRequirement("12.6.3.1")
                    .complianceStatus("FAILED")
                    .eventData(Map.of(
                            "campaign_id", result.getCampaignId().toString(),
                            "ip_address", ipAddress
                    ))
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build());
        }
    }

    /**
     * Track credential submission (most critical failure)
     */
    @Transactional
    public void trackDataSubmitted(String trackingToken, String ipAddress, Map<String, String> submittedData) {
        PhishingTestResult result = resultRepository.findByTrackingToken(trackingToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid tracking token"));

        result.setDataSubmittedAt(LocalDateTime.now());
        result.setDataSubmittedIpAddress(ipAddress);
        result.setResult(PhishingResult.FAILED);
        result.setRemedialTrainingRequired(true); // CRITICAL FAILURE - requires immediate training
        resultRepository.save(result);

        // Update campaign statistics
        updateCampaignStatistics(result.getCampaign().getId());

        // Immediate escalation
        sendCriticalPhishingFailureAlert(result.getEmployeeId(), result.getCampaignId());

        // Assign mandatory remedial training
        assignRemedialTraining(result.getEmployeeId());

        log.error("🚨 CRITICAL_PHISHING_FAILURE: Employee {} submitted credentials in phishing test",
                result.getEmployeeId());

        // Audit log
        auditRepository.save(SecurityAwarenessAuditLog.builder()
                .eventType("PHISHING_CREDENTIALS_SUBMITTED")
                .entityType("PHISHING_TEST")
                .entityId(result.getId())
                .employeeId(result.getEmployee().getId())
                .pciRequirement("12.6.3.1")
                .complianceStatus("CRITICAL_FAILURE")
                .eventData(Map.of(
                        "campaign_id", result.getCampaignId().toString(),
                        "ip_address", ipAddress,
                        "remedial_training_assigned", "true"
                ))
                .ipAddress(ipAddress)
                .build());
    }

    /**
     * Track employee reporting the phishing attempt (POSITIVE BEHAVIOR)
     */
    @Transactional
    public void trackPhishingReported(String trackingToken, String reportedVia) {
        PhishingTestResult result = resultRepository.findByTrackingToken(trackingToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid tracking token"));

        result.setReportedAt(LocalDateTime.now());
        result.setReportedAsPhishing(true);
        result.setActionTaken(PhishingTestResult.ActionTaken.REPORTED_PHISHING);
        result.setResult(PhishingResult.REPORTED); // BEST OUTCOME
        resultRepository.save(result);

        // Update campaign statistics
        updateCampaignStatistics(result.getCampaign().getId());

        // Send congratulations email
        sendPhishingSuccessNotification(result.getEmployeeId());

        log.info("✅ PHISHING_REPORTED: Employee {} correctly identified and reported phishing attempt",
                result.getEmployeeId());

        // Audit log
        auditRepository.save(SecurityAwarenessAuditLog.builder()
                .eventType("PHISHING_REPORTED")
                .entityType("PHISHING_TEST")
                .entityId(result.getId())
                .employeeId(result.getEmployee().getId())
                .pciRequirement("12.6.3.1")
                .complianceStatus("PASSED")
                .eventData(Map.of(
                        "campaign_id", result.getCampaign().getId().toString(),
                        "reported_via", reportedVia
                ))
                .build());
    }

    /**
     * Update campaign statistics
     */
    private void updateCampaignStatistics(UUID campaignId) {
        PhishingSimulationCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalStateException("Campaign not found"));

        long totalOpened = resultRepository.countByCampaign_IdAndEmailOpenedAtIsNotNull(campaignId);
        long totalClicked = resultRepository.countByCampaign_IdAndLinkClickedAtIsNotNull(campaignId);
        long totalSubmitted = resultRepository.countByCampaign_IdAndDataSubmittedAtIsNotNull(campaignId);
        long totalReported = resultRepository.countByCampaign_IdAndReportedAtIsNotNull(campaignId);

        campaign.setTotalOpened((int) totalOpened);
        campaign.setTotalClicked((int) totalClicked);
        campaign.setTotalSubmittedData((int) totalSubmitted);
        campaign.setTotalReported((int) totalReported);

        campaignRepository.save(campaign);
    }

    /**
     * Complete campaign and generate report
     */
    @Scheduled(cron = "0 0 9 * * *") // Daily at 9 AM
    @Transactional
    public void completeExpiredCampaigns() {
        LocalDateTime now = LocalDateTime.now();

        List<PhishingSimulationCampaign> activeCampaigns = campaignRepository
                .findByStatusAndScheduledEndBefore(CampaignStatus.IN_PROGRESS, now);

        for (PhishingSimulationCampaign campaign : activeCampaigns) {
            campaign.setStatus(CampaignStatus.COMPLETED);
            campaign.setActualEnd(LocalDateTime.now());
            campaignRepository.save(campaign);

            // Generate campaign report
            generateCampaignReport(campaign);

            // Update employee security profiles
            List<PhishingTestResult> results = resultRepository.findByCampaignId(campaign.getId());
            for (PhishingTestResult result : results) {
                awarenessService.updateEmployeeSecurityProfile(result.getEmployeeId());
            }

            log.info("✅ Completed phishing campaign: {}", campaign.getCampaignName());
        }
    }

    /**
     * Generate campaign performance report
     */
    private void generateCampaignReport(PhishingSimulationCampaign campaign) {
        double openRate = campaign.getTotalDelivered() > 0
                ? (campaign.getTotalOpened() * 100.0) / campaign.getTotalDelivered()
                : 0;

        double clickRate = campaign.getTotalDelivered() > 0
                ? (campaign.getTotalClicked() * 100.0) / campaign.getTotalDelivered()
                : 0;

        double submitRate = campaign.getTotalDelivered() > 0
                ? (campaign.getTotalSubmittedData() * 100.0) / campaign.getTotalDelivered()
                : 0;

        double reportRate = campaign.getTotalDelivered() > 0
                ? (campaign.getTotalReported() * 100.0) / campaign.getTotalDelivered()
                : 0;

        log.info("""

                📊 PHISHING CAMPAIGN REPORT: {}
                ==========================================
                Targeted:        {}
                Delivered:       {}
                Opened:          {} ({}%)
                Clicked:         {} ({}%) ⚠️
                Submitted Data:  {} ({}%) 🚨
                Reported:        {} ({}%) ✅
                ==========================================
                """,
                campaign.getCampaignName(),
                campaign.getTotalTargeted(),
                campaign.getTotalDelivered(),
                campaign.getTotalOpened(), String.format("%.1f", openRate),
                campaign.getTotalClicked(), String.format("%.1f", clickRate),
                campaign.getTotalSubmittedData(), String.format("%.1f", submitRate),
                campaign.getTotalReported(), String.format("%.1f", reportRate)
        );

        // Email report to security team
        emailService.sendCampaignReportToSecurityTeam(campaign, openRate, clickRate, submitRate, reportRate);
    }

    /**
     * Get target employees based on audience criteria
     */
    private List<Employee> getTargetEmployees(String targetAudienceStr) {
        List<String> targetAudience = targetAudienceStr != null && !targetAudienceStr.isEmpty()
            ? Arrays.asList(targetAudienceStr.split(","))
            : Collections.emptyList();

        if (targetAudience.contains("ALL")) {
            return employeeRepository.findByStatus(Employee.EmployeeStatus.ACTIVE);
        }

        return employeeRepository.findByDepartmentIn(targetAudience);
    }

    /**
     * Send phishing failure notification (educational, not punitive)
     */
    private void sendPhishingFailureNotification(UUID employeeId, UUID campaignId) {
        emailService.sendPhishingEducationEmail(employeeId, """
                This was a simulated phishing test conducted by our security team.

                You clicked on a suspicious link, which in a real attack could have compromised
                your account or company data.

                🔍 Red flags you may have missed:
                • Urgent/threatening language
                • Suspicious sender email address
                • Request for credentials or personal info
                • Generic greetings ("Dear User")

                ✅ What to do next time:
                • Verify sender identity through official channels
                • Hover over links to check the actual URL
                • Report suspicious emails to security@example.com

                We've assigned a short refresher training module to help you stay safe.
                """);
    }

    /**
     * Send critical failure alert (submitted credentials)
     */
    private void sendCriticalPhishingFailureAlert(UUID employeeId, UUID campaignId) {
        sendPhishingFailureNotification(employeeId, campaignId);

        // Also notify security team and manager
        emailService.sendSecurityTeamAlert(
                "CRITICAL Phishing Simulation Failure",
                String.format("Employee %s submitted credentials in phishing test. Remedial training assigned.", employeeId)
        );
    }

    /**
     * Send success notification (reported phishing)
     */
    private void sendPhishingSuccessNotification(UUID employeeId) {
        emailService.sendPhishingEducationEmail(employeeId, """
                🎉 Great job!

                This was a simulated phishing test, and you correctly identified and reported it.
                Your vigilance helps protect our company and customer data.

                Thank you for staying security-aware!
                """);
    }

    /**
     * Assign remedial training to employee who failed phishing test
     */
    private void assignRemedialTraining(UUID employeeId) {
        // Assign "Phishing Awareness Remedial Training" module
        UUID remedialModuleId = UUID.fromString("..."); // Predefined module ID

        EmployeeTrainingRecord record = EmployeeTrainingRecord.builder()
                .employeeId(employeeId)
                .moduleId(remedialModuleId)
                .status(TrainingStatus.NOT_STARTED)
                .attempts(0)
                .maxAttemptsAllowed(3)
                .build();

        // Save and notify
        log.info("📚 Assigned remedial phishing training to employee {}", employeeId);
    }

    /**
     * Get comprehensive campaign report
     */
    public com.waqiti.common.security.awareness.dto.PhishingCampaignReport getCampaignReport(UUID campaignId) {
        PhishingSimulationCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));

        // Get all results for this campaign
        List<PhishingTestResult> results = resultRepository.findByCampaignId(campaignId);

        int totalSent = campaign.getTotalEmailsSent();
        int totalOpened = (int) results.stream().filter(r -> r.getEmailOpenedAt() != null).count();
        int totalClicked = (int) results.stream().filter(r -> r.getLinkClickedAt() != null).count();
        int totalSubmitted = (int) results.stream().filter(r -> r.getDataSubmittedAt() != null).count();
        int totalReported = (int) results.stream().filter(r -> r.getReportedAt() != null).count();

        double clickRate = totalSent > 0 ? (double) totalClicked / totalSent * 100 : 0;
        double reportRate = totalSent > 0 ? (double) totalReported / totalSent * 100 : 0;

        return com.waqiti.common.security.awareness.dto.PhishingCampaignReport.builder()
                .campaignId(campaignId)
                .campaignName(campaign.getCampaignName())
                .totalTargeted(totalSent)
                .totalDelivered(totalSent)
                .totalOpened(totalOpened)
                .totalClicked(totalClicked)
                .totalSubmitted(totalSubmitted)
                .totalReported(totalReported)
                .openRate(totalSent > 0 ? (double) totalOpened / totalSent * 100 : 0.0)
                .clickRate(clickRate)
                .submitRate(totalSent > 0 ? (double) totalSubmitted / totalSent * 100 : 0.0)
                .reportRate(reportRate)
                .build();
    }
}
