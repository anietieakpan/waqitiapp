package com.waqiti.common.security.awareness;

import com.waqiti.common.security.awareness.model.*;
import com.waqiti.common.security.awareness.dto.*;
import com.waqiti.common.security.awareness.model.SecurityTrainingModule;
import com.waqiti.common.security.awareness.repository.*;

import com.waqiti.common.security.awareness.domain.*;
import com.waqiti.common.security.awareness.domain.EmployeeSecurityProfile.RiskLevel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PCI DSS REQ 12.6 - Security Awareness Program Service
 *
 * Manages security awareness training, phishing simulations, and compliance tracking
 * to ensure all personnel are educated about security best practices.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAwarenessService {

    private final SecurityTrainingModuleRepository moduleRepository;
    private final EmployeeTrainingRecordRepository trainingRecordRepository;
    private final EmployeeSecurityProfileRepository securityProfileRepository;
    private final PhishingSimulationCampaignRepository campaignRepository;
    private final PhishingTestResultRepository phishingResultRepository;
    private final QuarterlySecurityAssessmentRepository assessmentRepository;
    private final SecurityAwarenessAuditRepository auditRepository;
    private final EmailNotificationService emailNotificationService;
    private final CertificateGenerationService certificateService;

    /**
     * PCI DSS REQ 12.6.1 - Assign mandatory training to new employee upon hire
     */
    @Transactional
    public void onboardNewEmployee(UUID employeeId, String role, String email) {
        log.info("PCI_DSS_12.6.1: Onboarding employee {} with role {}", employeeId, role);

        // Get all mandatory training modules for this role
        List<SecurityTrainingModule> mandatoryModules = moduleRepository
                .findByIsMandatoryTrueAndTargetRolesContaining(role);

        if (mandatoryModules.isEmpty()) {
            mandatoryModules = moduleRepository.findByIsMandatoryTrueAndTargetRolesContaining("ALL");
        }

        // Create training records for each mandatory module
        List<EmployeeTrainingRecord> records = mandatoryModules.stream()
                .map(module -> EmployeeTrainingRecord.builder()
                        .employeeId(employeeId)
                        .moduleId(module.getId())
                        .status(EmployeeTrainingRecord.TrainingStatus.NOT_STARTED)
                        .attempts(0)
                        .maxAttemptsAllowed(3)
                        .build())
                .collect(Collectors.toList());

        trainingRecordRepository.saveAll(records);

        // Initialize security profile
        EmployeeSecurityProfile profile = EmployeeSecurityProfile.builder()
                .employeeId(employeeId)
                .totalModulesAssigned(mandatoryModules.size())
                .totalModulesCompleted(0)
                .compliancePercentage(BigDecimal.ZERO)
                .nextTrainingDueAt(LocalDateTime.now().plusDays(30)) // 30 days to complete initial training
                .riskScore(BigDecimal.valueOf(50)) // Medium risk until training completed
                .riskLevel(RiskLevel.MEDIUM)
                .build();

        securityProfileRepository.save(profile);

        // Send welcome email with training assignments
        emailNotificationService.sendNewEmployeeTrainingEmail(email, mandatoryModules);

        // Audit log
        auditRepository.save(SecurityAwarenessAuditLog.builder()
                .eventType("NEW_EMPLOYEE_ONBOARDING")
                .entityType("EMPLOYEE_PROFILE")
                .entityId(employeeId)
                .employeeId(employeeId)
                .pciRequirement("12.6.1")
                .complianceStatus("INITIATED")
                .eventData(Map.of(
                        "role", role,
                        "modules_assigned", mandatoryModules.size(),
                        "due_date", profile.getNextTrainingDueAt().toString()
                ))
                .build());

        log.info("✅ PCI_DSS_12.6.1: Assigned {} mandatory training modules to employee {}",
                mandatoryModules.size(), employeeId);
    }

    /**
     * PCI DSS REQ 12.6.1 - Annual training reminder (triggered 30 days before due date)
     */
    @Scheduled(cron = "0 0 8 * * *") // Daily at 8 AM
    @Transactional(readOnly = true)
    public void sendAnnualTrainingReminders() {
        log.info("PCI_DSS_12.6.1: Checking for upcoming annual training deadlines");

        LocalDateTime reminderThreshold = LocalDateTime.now().plusDays(30);

        List<EmployeeSecurityProfile> upcomingDue = securityProfileRepository
                .findByNextTrainingDueAtBetween(LocalDateTime.now(), reminderThreshold);

        for (EmployeeSecurityProfile profile : upcomingDue) {
            long daysUntilDue = ChronoUnit.DAYS.between(LocalDateTime.now(), profile.getNextTrainingDueAt());

            emailNotificationService.sendAnnualTrainingReminderEmail(
                    profile.getEmployeeId(),
                    daysUntilDue,
                    profile.getCompliancePercentage()
            );

            log.info("📧 Sent annual training reminder to employee {} ({} days until due)",
                    profile.getEmployeeId(), daysUntilDue);
        }
    }

    /**
     * PCI DSS REQ 12.6.2 - Record employee acknowledgment of training
     */
    @Transactional
    public void acknowledgeTraining(UUID employeeId, UUID moduleId, String signature, String ipAddress) {
        EmployeeTrainingRecord record = trainingRecordRepository
                .findByEmployeeIdAndModuleIdAndStatus(employeeId, moduleId, TrainingStatus.COMPLETED)
                .orElseThrow(() -> new IllegalStateException("Training must be completed before acknowledgment"));

        if (record.getAcknowledgedAt() != null) {
            log.warn("Training already acknowledged: employeeId={}, moduleId={}", employeeId, moduleId);
            return;
        }

        record.setAcknowledgedAt(LocalDateTime.now());
        record.setAcknowledgmentSignature(signature);
        record.setAcknowledgmentIpAddress(ipAddress);

        trainingRecordRepository.save(record);

        // Audit log
        auditRepository.save(SecurityAwarenessAuditLog.builder()
                .eventType("TRAINING_ACKNOWLEDGED")
                .entityType("TRAINING_RECORD")
                .entityId(record.getId())
                .employeeId(employeeId)
                .pciRequirement("12.6.2")
                .complianceStatus("ACKNOWLEDGED")
                .eventData(Map.of(
                        "module_id", moduleId.toString(),
                        "signature", signature != null ? "PROVIDED" : "NOT_PROVIDED",
                        "ip_address", ipAddress
                ))
                .ipAddress(ipAddress)
                .build());

        log.info("✅ PCI_DSS_12.6.2: Employee {} acknowledged training for module {}", employeeId, moduleId);
    }

    /**
     * Complete a training module with score
     */
    @Transactional
    public TrainingCompletionResult completeTraining(UUID employeeId, UUID moduleId, int scorePercentage) {
        EmployeeTrainingRecord record = trainingRecordRepository
                .findLatestByEmployeeIdAndModuleId(employeeId, moduleId)
                .orElseThrow(() -> new IllegalStateException("No training record found"));

        SecurityTrainingModule module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new IllegalStateException("Module not found"));

        // Check if max attempts exceeded
        if (record.getAttempts() >= record.getMaxAttemptsAllowed()) {
            log.warn("⚠️ Max attempts exceeded for employee {} on module {}", employeeId, moduleId);
            return TrainingCompletionResult.maxAttemptsExceeded(record);
        }

        // Update record
        record.setAttempts(record.getAttempts() + 1);
        record.setScorePercentage(scorePercentage);
        record.setCompletedAt(LocalDateTime.now());
        record.setLastAccessedAt(LocalDateTime.now());

        boolean passed = scorePercentage >= module.getPassingScorePercentage();
        record.setStatus(passed ? EmployeeTrainingRecord.TrainingStatus.COMPLETED : EmployeeTrainingRecord.TrainingStatus.FAILED);

        trainingRecordRepository.save(record);

        // Generate certificate if passed
        String certificateUrl = null;
        if (passed) {
            certificateUrl = certificateService.generateTrainingCertificate(
                    employeeId,
                    module.getId(),
                    LocalDateTime.now()
            );

            record.setCertificateUrl(certificateUrl);
            record.setCertificateIssuedAt(LocalDateTime.now());
            record.setCertificateExpiresAt(LocalDateTime.now().plusYears(1)); // Annual renewal

            trainingRecordRepository.save(record);

            // Update security profile
            updateEmployeeSecurityProfile(employeeId);
        }

        // Audit log
        auditRepository.save(SecurityAwarenessAuditLog.builder()
                .eventType("TRAINING_COMPLETED")
                .entityType("TRAINING_RECORD")
                .entityId(record.getId())
                .employeeId(employeeId)
                .pciRequirement(module.getPciRequirement())
                .complianceStatus(passed ? "PASSED" : "FAILED")
                .eventData(Map.of(
                        "module_id", moduleId.toString(),
                        "score", scorePercentage,
                        "passing_score", module.getPassingScorePercentage(),
                        "attempt", record.getAttempts(),
                        "result", passed ? "PASSED" : "FAILED"
                ))
                .build());

        log.info("✅ Training completed: employeeId={}, module={}, score={}, passed={}",
                employeeId, module.getModuleCode(), scorePercentage, passed);

        return TrainingCompletionResult.builder()
                .passed(passed)
                .score(scorePercentage)
                .certificateUrl(certificateUrl)
                .attemptsRemaining(record.getMaxAttemptsAllowed() - record.getAttempts())
                .build();
    }

    /**
     * Update employee security profile (compliance percentage, risk score)
     */
    @Transactional
    public void updateEmployeeSecurityProfile(UUID employeeId) {
        EmployeeSecurityProfile profile = securityProfileRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalStateException("Profile not found"));

        // Calculate training compliance
        long totalAssigned = trainingRecordRepository.countByEmployeeIdField(employeeId);
        long totalCompleted = trainingRecordRepository.countByEmployeeIdFieldAndStatus(employeeId, EmployeeTrainingRecord.TrainingStatus.COMPLETED);

        BigDecimal compliancePercentage = totalAssigned > 0
                ? BigDecimal.valueOf(totalCompleted)
                        .divide(BigDecimal.valueOf(totalAssigned), 2, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        profile.setTotalModulesAssigned((int) totalAssigned);
        profile.setTotalModulesCompleted((int) totalCompleted);
        profile.setCompliancePercentage(compliancePercentage);
        profile.setLastTrainingCompletedAt(LocalDateTime.now());
        profile.setNextTrainingDueAt(LocalDateTime.now().plusYears(1)); // Annual training cycle

        // Calculate phishing performance
        long totalPhishingTests = phishingResultRepository.countByEmployeeId(employeeId);
        long phishingTestsFailed = phishingResultRepository.countByEmployeeIdAndResult(employeeId, com.waqiti.common.security.awareness.model.PhishingResult.FAILED);

        BigDecimal phishingSuccessRate = totalPhishingTests > 0
                ? BigDecimal.valueOf(totalPhishingTests - phishingTestsFailed)
                        .divide(BigDecimal.valueOf(totalPhishingTests), 2, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.valueOf(100);

        profile.setTotalPhishingTests((int) totalPhishingTests);
        profile.setPhishingTestsFailed((int) phishingTestsFailed);
        profile.setPhishingSuccessRatePercentage(phishingSuccessRate);

        // Calculate overall risk score (0-100, higher = more risk)
        int riskScore = calculateRiskScore(compliancePercentage, phishingSuccessRate);
        profile.setRiskScore(BigDecimal.valueOf(riskScore));

        // Determine risk level
        RiskLevel riskLevel;
        if (riskScore <= 25) {
            riskLevel = RiskLevel.LOW;
        } else if (riskScore <= 50) {
            riskLevel = RiskLevel.MEDIUM;
        } else if (riskScore <= 75) {
            riskLevel = RiskLevel.HIGH;
        } else {
            riskLevel = RiskLevel.CRITICAL;
        }
        profile.setRiskLevel(riskLevel);

        profile.setUpdatedAt(LocalDateTime.now());
        securityProfileRepository.save(profile);

        log.info("📊 Updated security profile: employeeId={}, compliance={}%, phishingSuccess={}%, riskLevel={}",
                employeeId, compliancePercentage, phishingSuccessRate, riskLevel);
    }

    /**
     * Calculate employee security risk score (0-100)
     */
    private int calculateRiskScore(BigDecimal compliancePercentage, BigDecimal phishingSuccessRate) {
        // Risk factors:
        // 1. Low training compliance (60% weight)
        // 2. Poor phishing performance (40% weight)

        double complianceRisk = (100 - compliancePercentage.doubleValue()) * 0.6;
        double phishingRisk = (100 - phishingSuccessRate.doubleValue()) * 0.4;

        int totalRisk = (int) Math.round(complianceRisk + phishingRisk);

        return Math.max(0, Math.min(100, totalRisk));
    }

    /**
     * Get employees overdue for annual training (PCI DSS audit report)
     */
    @Transactional(readOnly = true)
    public List<EmployeeSecurityProfile> getOverdueEmployees() {
        return securityProfileRepository.findByNextTrainingDueAtBefore(LocalDateTime.now());
    }

    /**
     * Get high-risk employees (for targeted training)
     */
    @Transactional(readOnly = true)
    public List<EmployeeSecurityProfile> getHighRiskEmployees() {
        return securityProfileRepository.findByRiskLevelIn(Arrays.asList(RiskLevel.HIGH, RiskLevel.CRITICAL));
    }

    /**
     * PCI DSS Compliance Report - Annual training completion status
     */
    @Transactional(readOnly = true)
    public PCIComplianceReport generatePCIComplianceReport() {
        long totalEmployees = securityProfileRepository.count();
        long compliantEmployees = securityProfileRepository.countByCompliancePercentageGreaterThanEqual(BigDecimal.valueOf(100));
        long overdueEmployees = securityProfileRepository.countByNextTrainingDueAtBefore(LocalDateTime.now());

        BigDecimal overallComplianceRate = totalEmployees > 0
                ? BigDecimal.valueOf(compliantEmployees)
                        .divide(BigDecimal.valueOf(totalEmployees), 2, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        return PCIComplianceReport.builder()
                .reportDate(LocalDateTime.now())
                .totalEmployees(totalEmployees)
                .compliantEmployees(compliantEmployees)
                .overdueEmployees(overdueEmployees)
                .overallComplianceRate(overallComplianceRate)
                .pciRequirement("12.6.1")
                .complianceStatus(overallComplianceRate.compareTo(BigDecimal.valueOf(95)) >= 0 ? "COMPLIANT" : "NON_COMPLIANT")
                .build();
    }
}
