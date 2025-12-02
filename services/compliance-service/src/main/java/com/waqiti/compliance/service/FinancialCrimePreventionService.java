package com.waqiti.compliance.service;

import com.waqiti.compliance.dto.FinancialCrimeAlert;
import com.waqiti.compliance.dto.FinancialCrimeReport;
import com.waqiti.compliance.dto.SARFiling;
import com.waqiti.compliance.entity.FinancialCrimeCase;
import com.waqiti.compliance.entity.RegulatoryReport;
import com.waqiti.compliance.enums.CrimeSeverity;
import com.waqiti.compliance.enums.CrimeType;
import com.waqiti.compliance.repository.FinancialCrimeCaseRepository;
import com.waqiti.compliance.repository.RegulatoryReportRepository;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.notification.PagerDutyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Production-Grade Financial Crime Prevention Service
 *
 * Provides comprehensive financial crime detection, prevention, and response capabilities
 * including immediate account freezing, law enforcement notification, FinCEN SAR filing,
 * and regulatory compliance.
 *
 * Compliance: BSA/AML, FinCEN SAR Requirements, Bank Secrecy Act
 *
 * @author Waqiti Compliance Team
 * @version 2.0 - Production Ready
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FinancialCrimePreventionService {

    private final AmlComplianceService amlComplianceService;
    private final FinancialCrimeCaseRepository crimeCaseRepository;
    private final RegulatoryReportRepository regulatoryReportRepository;
    private final NotificationService notificationService;
    private final PagerDutyService pagerDutyService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // External API clients
    private final FinCENApiClient finCENApiClient;
    private final LawEnforcementApiClient lawEnforcementApiClient;

    // Internal service clients
    private final WalletServiceClient walletServiceClient;
    private final UserServiceClient userServiceClient;
    private final TransactionServiceClient transactionServiceClient;

    @Value("${compliance.financial-crime.auto-freeze-enabled:true}")
    private boolean autoFreezeEnabled;

    @Value("${compliance.financial-crime.pagerduty-enabled:true}")
    private boolean pagerDutyEnabled;

    @Value("${compliance.financial-crime.law-enforcement-threshold:10000}")
    private double lawEnforcementThreshold;

    /**
     * Handle critical financial crime alert with comprehensive response
     *
     * Actions taken:
     * 1. Create financial crime case
     * 2. Freeze user account (if enabled)
     * 3. Notify compliance team via PagerDuty
     * 4. Generate suspicious activity report (SAR)
     * 5. Alert law enforcement if threshold exceeded
     * 6. Publish crime alert event to Kafka
     *
     * @param userId User ID involved in financial crime
     * @param alertType Type of crime (FRAUD, MONEY_LAUNDERING, etc.)
     * @param details Crime details and evidence
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public FinancialCrimeCase handleCriticalFinancialCrimeAlert(
            String userId,
            String alertType,
            Map<String, Object> details) {

        log.error("🚨 CRITICAL FINANCIAL CRIME ALERT: user={}, type={}, details={}",
                userId, alertType, details);

        // Audit critical event
        auditService.auditCriticalEvent(
            "FINANCIAL_CRIME_ALERT",
            userId,
            Map.of("alertType", alertType, "details", details),
            true // soxRelevant
        );

        try {
            // 1. Create financial crime case for investigation
            FinancialCrimeCase crimeCase = createFinancialCrimeCase(userId, alertType, details);

            // 2. Immediate account freeze if enabled
            if (autoFreezeEnabled && isHighSeverity(alertType, details)) {
                freezeUserAccount(userId, crimeCase.getCaseId(), "FINANCIAL_CRIME_ALERT");
            }

            // 3. Notify compliance team via PagerDuty (critical alert)
            if (pagerDutyEnabled) {
                notifyComplianceTeam(crimeCase);
            }

            // 4. Generate Suspicious Activity Report (SAR) for FinCEN
            CompletableFuture<SARFiling> sarFuture = generateSuspiciousActivityReport(crimeCase);

            // 5. Alert law enforcement if amount exceeds threshold
            if (exceedsLawEnforcementThreshold(details)) {
                notifyLawEnforcement(crimeCase);
            }

            // 6. Publish crime alert event to Kafka for downstream processing
            publishCrimeAlertEvent(crimeCase);

            log.info("✅ Financial crime case created: caseId={}, userId={}, type={}",
                    crimeCase.getCaseId(), userId, alertType);

            return crimeCase;

        } catch (Exception e) {
            log.error("❌ Failed to handle financial crime alert for user={}: {}", userId, e.getMessage(), e);

            // Critical failure - escalate to senior compliance officer
            pagerDutyService.triggerCriticalIncident(
                "FINANCIAL_CRIME_PROCESSING_FAILURE",
                "Failed to process financial crime alert for user: " + userId,
                Map.of("error", e.getMessage(), "userId", userId)
            );

            throw new FinancialCrimeProcessingException(
                "Failed to handle financial crime alert", e);
        }
    }

    /**
     * Initiate emergency financial crime response protocol
     *
     * Used for severe cases requiring immediate action (e.g., terrorism financing,
     * large-scale fraud rings, organized crime)
     *
     * @param userId User ID
     * @param crimeType Type of crime
     * @param severity Severity level (CRITICAL, HIGH, MEDIUM, LOW)
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void initiateEmergencyResponse(String userId, String crimeType, String severity) {
        log.error("🚨🚨🚨 EMERGENCY FINANCIAL CRIME RESPONSE: user={}, type={}, severity={}",
                userId, crimeType, severity);

        CrimeSeverity crimeSeverity = CrimeSeverity.valueOf(severity);

        // Audit emergency response
        auditService.auditCriticalEvent(
            "EMERGENCY_FINANCIAL_CRIME_RESPONSE",
            userId,
            Map.of("crimeType", crimeType, "severity", severity),
            true
        );

        try {
            // 1. Immediate account freeze (no bypass)
            freezeUserAccount(userId, null, "EMERGENCY_PROTOCOL");

            // 2. Freeze all related accounts (family, business entities)
            freezeRelatedAccounts(userId, crimeType);

            // 3. Halt all pending transactions
            haltAllPendingTransactions(userId);

            // 4. Create high-priority investigation case
            FinancialCrimeCase emergencyCase = createEmergencyCrimeCase(userId, crimeType, crimeSeverity);

            // 5. Immediate FinCEN notification (emergency SAR)
            fileEmergencySAR(emergencyCase);

            // 6. Notify law enforcement immediately
            notifyLawEnforcementEmergency(emergencyCase);

            // 7. Alert senior compliance officers and legal team
            notifySeniorLeadership(emergencyCase);

            // 8. Preserve all evidence (transaction history, communications)
            preserveEvidence(userId, emergencyCase.getCaseId());

            log.warn("✅ Emergency response protocol completed for user={}, caseId={}",
                    userId, emergencyCase.getCaseId());

        } catch (Exception e) {
            log.error("❌ CRITICAL: Emergency response failed for user={}: {}", userId, e.getMessage(), e);

            // Ultimate escalation - CEO/CFO notification
            pagerDutyService.triggerP0Incident(
                "EMERGENCY_RESPONSE_FAILURE",
                "Emergency financial crime response failed for user: " + userId,
                Map.of("error", e.getMessage(), "crimeType", crimeType, "severity", severity)
            );

            throw new EmergencyResponseFailureException("Emergency response protocol failed", e);
        }
    }

    /**
     * Report financial crime to law enforcement authorities
     *
     * Submits report to:
     * - FinCEN (Financial Crimes Enforcement Network)
     * - FBI (if federal crime)
     * - Local law enforcement (if applicable)
     * - SEC (if securities fraud)
     *
     * @param userId User ID
     * @param crimeType Type of crime
     * @param evidence Evidence map (transactions, communications, etc.)
     */
    @Async
    @Transactional
    public CompletableFuture<RegulatoryReport> reportToAuthorities(
            String userId,
            String crimeType,
            Map<String, Object> evidence) {

        log.warn("📢 Reporting financial crime to authorities: user={}, type={}", userId, crimeType);

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Create regulatory report
                RegulatoryReport report = RegulatoryReport.builder()
                    .reportId(UUID.randomUUID().toString())
                    .userId(userId)
                    .crimeType(CrimeType.valueOf(crimeType))
                    .evidence(evidence)
                    .reportedAt(LocalDateTime.now())
                    .reportedBy("SYSTEM_AUTOMATED")
                    .status("SUBMITTED")
                    .build();

                // 2. Submit to FinCEN (Suspicious Activity Report)
                String finCENReferenceNumber = finCENApiClient.submitSAR(
                    userId, crimeType, evidence);
                report.setFinCENReferenceNumber(finCENReferenceNumber);

                // 3. Submit to FBI if federal crime (terrorism, major fraud >$1M)
                if (isFederalCrime(crimeType, evidence)) {
                    String fbiReferenceNumber = lawEnforcementApiClient.submitToFBI(
                        userId, crimeType, evidence);
                    report.setFbiReferenceNumber(fbiReferenceNumber);
                }

                // 4. Submit to SEC if securities-related
                if (isSecuritiesCrime(crimeType)) {
                    String secReferenceNumber = lawEnforcementApiClient.submitToSEC(
                        userId, crimeType, evidence);
                    report.setSecReferenceNumber(secReferenceNumber);
                }

                // 5. Save regulatory report
                report = regulatoryReportRepository.save(report);

                // 6. Audit authority notification
                auditService.auditCriticalEvent(
                    "LAW_ENFORCEMENT_NOTIFICATION",
                    userId,
                    Map.of("reportId", report.getReportId(), "crimeType", crimeType),
                    true
                );

                log.info("✅ Financial crime reported to authorities: reportId={}, finCENRef={}",
                        report.getReportId(), finCENReferenceNumber);

                return report;

            } catch (Exception e) {
                log.error("❌ Failed to report financial crime to authorities for user={}: {}",
                        userId, e.getMessage(), e);
                throw new AuthorityReportingException("Failed to report to authorities", e);
            }
        });
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private FinancialCrimeCase createFinancialCrimeCase(
            String userId, String alertType, Map<String, Object> details) {

        FinancialCrimeCase crimeCase = FinancialCrimeCase.builder()
            .caseId(UUID.randomUUID().toString())
            .userId(userId)
            .crimeType(CrimeType.valueOf(alertType))
            .severity(determineSeverity(alertType, details))
            .details(details)
            .status("OPEN")
            .createdAt(LocalDateTime.now())
            .assignedTo(autoAssignInvestigator(alertType))
            .build();

        return crimeCaseRepository.save(crimeCase);
    }

    private void freezeUserAccount(String userId, String caseId, String reason) {
        log.warn("🔒 Freezing user account: userId={}, caseId={}, reason={}", userId, caseId, reason);

        walletServiceClient.freezeAccount(userId, reason, caseId);
        userServiceClient.suspendUser(userId, reason);

        // Publish account frozen event
        kafkaTemplate.send("account-frozen-events", userId,
            Map.of("userId", userId, "caseId", caseId, "reason", reason, "timestamp", LocalDateTime.now()));
    }

    private void freezeRelatedAccounts(String userId, String crimeType) {
        // Freeze family members, business partners, linked accounts
        userServiceClient.getRelatedAccounts(userId).forEach(relatedUserId -> {
            freezeUserAccount(relatedUserId, null, "RELATED_TO_CRIME_CASE_" + userId);
        });
    }

    private void haltAllPendingTransactions(String userId) {
        log.warn("⛔ Halting all pending transactions for userId={}", userId);
        transactionServiceClient.cancelPendingTransactions(userId, "FINANCIAL_CRIME_PREVENTION");
    }

    private FinancialCrimeCase createEmergencyCrimeCase(
            String userId, String crimeType, CrimeSeverity severity) {

        return crimeCaseRepository.save(FinancialCrimeCase.builder()
            .caseId(UUID.randomUUID().toString())
            .userId(userId)
            .crimeType(CrimeType.valueOf(crimeType))
            .severity(severity)
            .status("EMERGENCY")
            .priority("P0")
            .createdAt(LocalDateTime.now())
            .assignedTo("SENIOR_COMPLIANCE_OFFICER")
            .build());
    }

    private void notifyComplianceTeam(FinancialCrimeCase crimeCase) {
        pagerDutyService.triggerIncident(
            "FINANCIAL_CRIME_ALERT",
            String.format("Financial crime detected: %s (User: %s)",
                crimeCase.getCrimeType(), crimeCase.getUserId()),
            Map.of("caseId", crimeCase.getCaseId(), "severity", crimeCase.getSeverity())
        );
    }

    private CompletableFuture<SARFiling> generateSuspiciousActivityReport(FinancialCrimeCase crimeCase) {
        return CompletableFuture.supplyAsync(() ->
            finCENApiClient.generateSAR(crimeCase));
    }

    private void notifyLawEnforcement(FinancialCrimeCase crimeCase) {
        lawEnforcementApiClient.submitAlert(crimeCase);
    }

    private void notifyLawEnforcementEmergency(FinancialCrimeCase crimeCase) {
        lawEnforcementApiClient.submitEmergencyAlert(crimeCase);
    }

    private void notifySeniorLeadership(FinancialCrimeCase crimeCase) {
        notificationService.notifyExecutives(
            "EMERGENCY_FINANCIAL_CRIME",
            String.format("Emergency crime case: %s", crimeCase.getCaseId()),
            Map.of("caseId", crimeCase.getCaseId(), "crimeType", crimeCase.getCrimeType())
        );
    }

    private void publishCrimeAlertEvent(FinancialCrimeCase crimeCase) {
        kafkaTemplate.send("financial-crime-alerts", crimeCase.getCaseId(), crimeCase);
    }

    private void fileEmergencySAR(FinancialCrimeCase crimeCase) {
        finCENApiClient.fileEmergencySAR(crimeCase);
    }

    private void preserveEvidence(String userId, String caseId) {
        // Archive all user data, transactions, communications
        transactionServiceClient.archiveUserTransactions(userId, caseId);
        userServiceClient.preserveUserData(userId, caseId);
    }

    private boolean isHighSeverity(String alertType, Map<String, Object> details) {
        // Determine if crime requires immediate account freeze
        return CrimeType.valueOf(alertType).isHighSeverity() ||
               (details.containsKey("amount") &&
                ((Number) details.get("amount")).doubleValue() > lawEnforcementThreshold);
    }

    private boolean exceedsLawEnforcementThreshold(Map<String, Object> details) {
        if (details.containsKey("amount")) {
            return ((Number) details.get("amount")).doubleValue() >= lawEnforcementThreshold;
        }
        return false;
    }

    private CrimeSeverity determineSeverity(String alertType, Map<String, Object> details) {
        CrimeType crimeType = CrimeType.valueOf(alertType);
        if (crimeType == CrimeType.TERRORISM_FINANCING) return CrimeSeverity.CRITICAL;
        if (crimeType == CrimeType.MONEY_LAUNDERING) return CrimeSeverity.HIGH;
        return CrimeSeverity.MEDIUM;
    }

    private String autoAssignInvestigator(String alertType) {
        // Auto-assign to appropriate investigator based on crime type
        return "COMPLIANCE_INVESTIGATOR_" + (alertType.hashCode() % 5 + 1);
    }

    private boolean isFederalCrime(String crimeType, Map<String, Object> evidence) {
        CrimeType type = CrimeType.valueOf(crimeType);
        return type == CrimeType.TERRORISM_FINANCING ||
               type == CrimeType.MONEY_LAUNDERING ||
               (evidence.containsKey("amount") &&
                ((Number) evidence.get("amount")).doubleValue() > 1_000_000);
    }

    private boolean isSecuritiesCrime(String crimeType) {
        return crimeType.equals("SECURITIES_FRAUD") ||
               crimeType.equals("INSIDER_TRADING");
    }

    // Custom exceptions
    public static class FinancialCrimeProcessingException extends RuntimeException {
        public FinancialCrimeProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class EmergencyResponseFailureException extends RuntimeException {
        public EmergencyResponseFailureException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class AuthorityReportingException extends RuntimeException {
        public AuthorityReportingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
