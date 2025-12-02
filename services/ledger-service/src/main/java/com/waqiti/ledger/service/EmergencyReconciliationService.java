package com.waqiti.ledger.service;

import com.waqiti.ledger.dto.EmergencyReconciliationRequest;
import com.waqiti.ledger.dto.EmergencyReconciliationResponse;
import com.waqiti.ledger.entity.AccountBalanceEntity;
import com.waqiti.ledger.repository.AccountBalanceRepository;
import com.waqiti.ledger.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Emergency Reconciliation Service
 *
 * Handles critical reconciliation failures that require immediate attention.
 * This service is invoked when DLQ handlers detect critical discrepancies
 * that could indicate data corruption, system failures, or fraud.
 *
 * Security: CRITICAL
 * Compliance: SOX, Basel III
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmergencyReconciliationService {

    private final AccountBalanceRepository accountBalanceRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ReconciliationService reconciliationService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    /**
     * Performs emergency reconciliation for a critical discrepancy
     *
     * This is a CRITICAL operation that should only be triggered when:
     * 1. Normal reconciliation has failed multiple times
     * 2. Discrepancy exceeds critical threshold (e.g., > $10,000)
     * 3. DLQ handler has exhausted all automatic recovery options
     *
     * @param request Emergency reconciliation request with context
     * @return Emergency reconciliation response with actions taken
     */
    @Transactional
    public EmergencyReconciliationResponse performEmergencyReconciliation(EmergencyReconciliationRequest request) {
        log.error("🚨 CRITICAL: Emergency reconciliation initiated - Account: {}, Discrepancy: {}, Reason: {}",
                request.getAccountId(), request.getDiscrepancyAmount(), request.getReason());

        // Audit trail
        auditLogService.logCriticalEvent(
                "EMERGENCY_RECONCILIATION_INITIATED",
                request.getAccountId().toString(),
                buildAuditDetails(request)
        );

        EmergencyReconciliationResponse response = EmergencyReconciliationResponse.builder()
                .reconciliationId(UUID.randomUUID())
                .accountId(request.getAccountId())
                .initiatedAt(LocalDateTime.now())
                .build();

        try {
            // Step 1: Freeze the account to prevent further transactions
            freezeAccount(request.getAccountId(), "EMERGENCY_RECONCILIATION_IN_PROGRESS");
            response.addAction("Account frozen to prevent further transactions");

            // Step 2: Calculate actual balance from ledger entries
            BigDecimal calculatedBalance = calculateBalanceFromLedger(request.getAccountId(), request.getCurrency());
            response.setCalculatedLedgerBalance(calculatedBalance);
            response.addAction("Calculated balance from ledger: " + calculatedBalance);

            // Step 3: Get current recorded balance
            AccountBalanceEntity currentBalance = accountBalanceRepository
                    .findByAccountId(request.getAccountId().toString())
                    .orElseThrow(() -> new IllegalStateException("Account balance not found: " + request.getAccountId()));
            response.setRecordedBalance(currentBalance.getCurrentBalance());

            // Step 4: Calculate and verify discrepancy
            BigDecimal discrepancy = calculatedBalance.subtract(currentBalance.getCurrentBalance()).abs();
            response.setDiscrepancyAmount(discrepancy);
            response.addAction("Verified discrepancy: " + discrepancy);

            // Step 5: Determine severity and action plan
            DiscrepancySeverity severity = determineDiscrepancySeverity(discrepancy);
            response.setSeverity(severity.name());
            response.addAction("Severity level: " + severity);

            // Step 6: Execute corrective action based on severity
            switch (severity) {
                case CRITICAL:
                    // >$100,000 - Requires C-suite approval and manual review
                    response.setRequiresManualReview(true);
                    response.setEscalationLevel("C_SUITE");
                    response.addAction("CRITICAL: Requires CFO/CEO approval - Manual review required");
                    createManualReviewTask(request, discrepancy, "C_SUITE");
                    break;

                case HIGH:
                    // $10,000 - $100,000 - Requires Controller approval
                    response.setRequiresManualReview(true);
                    response.setEscalationLevel("CONTROLLER");
                    response.addAction("HIGH: Requires Controller approval");
                    createManualReviewTask(request, discrepancy, "CONTROLLER");
                    break;

                case MEDIUM:
                    // $1,000 - $10,000 - Requires Finance Manager review
                    response.setRequiresManualReview(true);
                    response.setEscalationLevel("FINANCE_MANAGER");
                    response.addAction("MEDIUM: Requires Finance Manager review");
                    createManualReviewTask(request, discrepancy, "FINANCE_MANAGER");
                    break;

                case LOW:
                    // <$1,000 - Can attempt automatic correction
                    response.setRequiresManualReview(false);
                    attemptAutomaticCorrection(request, calculatedBalance, currentBalance);
                    response.addAction("LOW: Attempted automatic correction");
                    break;
            }

            // Step 7: Create reconciliation report
            UUID reportId = createReconciliationReport(request, response);
            response.setReportId(reportId);
            response.addAction("Created reconciliation report: " + reportId);

            // Step 8: Send notifications based on severity
            sendEmergencyNotifications(request, response, severity);
            response.addAction("Sent emergency notifications to relevant stakeholders");

            response.setStatus("COMPLETED");
            response.setCompletedAt(LocalDateTime.now());

            log.info("Emergency reconciliation completed - Reconciliation ID: {}, Status: {}, Severity: {}",
                    response.getReconciliationId(), response.getStatus(), severity);

        } catch (Exception e) {
            log.error("Emergency reconciliation FAILED: {}", e.getMessage(), e);
            response.setStatus("FAILED");
            response.setErrorMessage(e.getMessage());
            response.setCompletedAt(LocalDateTime.now());

            // Critical: If emergency reconciliation fails, escalate to highest level
            notificationService.sendCriticalAlert(
                    "EMERGENCY_RECONCILIATION_FAILED",
                    String.format("Emergency reconciliation failed for account %s. Immediate action required!", request.getAccountId()),
                    List.of("CFO", "CTO", "CEO")
            );
        }

        // Final audit log
        auditLogService.logCriticalEvent(
                "EMERGENCY_RECONCILIATION_COMPLETED",
                request.getAccountId().toString(),
                buildCompletionAuditDetails(response)
        );

        return response;
    }

    /**
     * Freezes an account to prevent further transactions during reconciliation
     */
    private void freezeAccount(UUID accountId, String reason) {
        AccountBalanceEntity balance = accountBalanceRepository
                .findByAccountId(accountId.toString())
                .orElseThrow(() -> new IllegalStateException("Account not found: " + accountId));

        balance.setFrozen(true);
        accountBalanceRepository.save(balance);

        log.warn("🔒 Account frozen: {} - Reason: {}", accountId, reason);

        auditLogService.logSecurityEvent(
                "ACCOUNT_FROZEN",
                accountId.toString(),
                Map.of("reason", reason, "timestamp", LocalDateTime.now())
        );
    }

    /**
     * Calculates actual balance from ledger entries (source of truth)
     */
    private BigDecimal calculateBalanceFromLedger(UUID accountId, String currency) {
        // Use repository method to calculate balance from all ledger entries
        BigDecimal creditTotal = ledgerEntryRepository.calculateTotalCredits(accountId, currency);
        BigDecimal debitTotal = ledgerEntryRepository.calculateTotalDebits(accountId, currency);

        // Net balance = Credits - Debits (standard accounting)
        return creditTotal.subtract(debitTotal);
    }

    /**
     * Determines severity based on discrepancy amount
     */
    private DiscrepancySeverity determineDiscrepancySeverity(BigDecimal discrepancy) {
        BigDecimal absDiscrepancy = discrepancy.abs();

        if (absDiscrepancy.compareTo(new BigDecimal("100000")) > 0) {
            return DiscrepancySeverity.CRITICAL;
        } else if (absDiscrepancy.compareTo(new BigDecimal("10000")) > 0) {
            return DiscrepancySeverity.HIGH;
        } else if (absDiscrepancy.compareTo(new BigDecimal("1000")) > 0) {
            return DiscrepancySeverity.MEDIUM;
        } else {
            return DiscrepancySeverity.LOW;
        }
    }

    /**
     * Creates a manual review task for finance team
     */
    private void createManualReviewTask(EmergencyReconciliationRequest request, BigDecimal discrepancy, String assigneeLevel) {
        log.info("Creating manual review task - Account: {}, Discrepancy: {}, Assignee Level: {}",
                request.getAccountId(), discrepancy, assigneeLevel);

        // This would integrate with a task management system
        // For now, log and send notification
        notificationService.createTask(
                "EMERGENCY_RECONCILIATION_REVIEW",
                String.format("Emergency reconciliation required for account %s - Discrepancy: %s",
                    request.getAccountId(), discrepancy),
                assigneeLevel,
                "CRITICAL",
                Map.of(
                    "accountId", request.getAccountId().toString(),
                    "discrepancy", discrepancy.toString(),
                    "reason", request.getReason(),
                    "initiatedAt", LocalDateTime.now().toString()
                )
        );
    }

    /**
     * Attempts automatic correction for low-severity discrepancies
     */
    private void attemptAutomaticCorrection(
            EmergencyReconciliationRequest request,
            BigDecimal calculatedBalance,
            AccountBalanceEntity currentBalance) {

        log.info("Attempting automatic correction - Account: {}, Calculated: {}, Recorded: {}",
                request.getAccountId(), calculatedBalance, currentBalance.getCurrentBalance());

        // Update the balance to match ledger (source of truth)
        currentBalance.setCurrentBalance(calculatedBalance);
        currentBalance.setNetBalance(calculatedBalance);
        accountBalanceRepository.save(currentBalance);

        log.info("✅ Automatic correction applied - New balance: {}", calculatedBalance);

        auditLogService.logCriticalEvent(
                "AUTOMATIC_BALANCE_CORRECTION",
                request.getAccountId().toString(),
                Map.of(
                    "oldBalance", currentBalance.getCurrentBalance().toString(),
                    "newBalance", calculatedBalance.toString(),
                    "correctionReason", "EMERGENCY_RECONCILIATION_AUTO_CORRECTION"
                )
        );
    }

    /**
     * Creates detailed reconciliation report
     */
    private UUID createReconciliationReport(EmergencyReconciliationRequest request, EmergencyReconciliationResponse response) {
        // This would create a detailed PDF/HTML report
        // For now, return generated UUID
        UUID reportId = UUID.randomUUID();

        log.info("Created reconciliation report: {} for account: {}", reportId, request.getAccountId());

        return reportId;
    }

    /**
     * Sends emergency notifications based on severity
     */
    private void sendEmergencyNotifications(
            EmergencyReconciliationRequest request,
            EmergencyReconciliationResponse response,
            DiscrepancySeverity severity) {

        List<String> recipients = new ArrayList<>();

        switch (severity) {
            case CRITICAL:
                recipients.addAll(List.of("CFO", "CEO", "CTO", "CONTROLLER", "FINANCE_OPS", "COMPLIANCE"));
                notificationService.sendPagerDutyAlert("P0", buildAlertMessage(request, response));
                break;
            case HIGH:
                recipients.addAll(List.of("CFO", "CONTROLLER", "FINANCE_OPS"));
                notificationService.sendPagerDutyAlert("P1", buildAlertMessage(request, response));
                break;
            case MEDIUM:
                recipients.addAll(List.of("CONTROLLER", "FINANCE_MANAGER", "FINANCE_OPS"));
                notificationService.sendSlackNotification("#finance-ops", buildAlertMessage(request, response));
                break;
            case LOW:
                recipients.addAll(List.of("FINANCE_OPS"));
                notificationService.sendSlackNotification("#finance-ops", buildAlertMessage(request, response));
                break;
        }

        notificationService.sendEmailAlert(
                recipients,
                "🚨 EMERGENCY RECONCILIATION: " + request.getAccountId(),
                buildEmailBody(request, response)
        );
    }

    private String buildAlertMessage(EmergencyReconciliationRequest request, EmergencyReconciliationResponse response) {
        return String.format(
                "🚨 EMERGENCY RECONCILIATION\n" +
                "Account: %s\n" +
                "Discrepancy: %s %s\n" +
                "Severity: %s\n" +
                "Reason: %s\n" +
                "Status: %s\n" +
                "Reconciliation ID: %s",
                request.getAccountId(),
                response.getDiscrepancyAmount(),
                request.getCurrency(),
                response.getSeverity(),
                request.getReason(),
                response.getStatus(),
                response.getReconciliationId()
        );
    }

    private String buildEmailBody(EmergencyReconciliationRequest request, EmergencyReconciliationResponse response) {
        StringBuilder body = new StringBuilder();
        body.append("<h2>🚨 EMERGENCY RECONCILIATION ALERT</h2>\n");
        body.append("<p><strong>An emergency reconciliation has been initiated due to critical discrepancy.</strong></p>\n");
        body.append("<h3>Details:</h3>\n");
        body.append("<ul>\n");
        body.append(String.format("  <li><strong>Reconciliation ID:</strong> %s</li>\n", response.getReconciliationId()));
        body.append(String.format("  <li><strong>Account ID:</strong> %s</li>\n", request.getAccountId()));
        body.append(String.format("  <li><strong>Discrepancy Amount:</strong> %s %s</li>\n", response.getDiscrepancyAmount(), request.getCurrency()));
        body.append(String.format("  <li><strong>Severity:</strong> %s</li>\n", response.getSeverity()));
        body.append(String.format("  <li><strong>Reason:</strong> %s</li>\n", request.getReason()));
        body.append(String.format("  <li><strong>Status:</strong> %s</li>\n", response.getStatus()));
        body.append(String.format("  <li><strong>Manual Review Required:</strong> %s</li>\n", response.isRequiresManualReview() ? "YES" : "NO"));
        if (response.isRequiresManualReview()) {
            body.append(String.format("  <li><strong>Escalation Level:</strong> %s</li>\n", response.getEscalationLevel()));
        }
        body.append("</ul>\n");
        body.append("<h3>Actions Taken:</h3>\n");
        body.append("<ol>\n");
        for (String action : response.getActionsTaken()) {
            body.append(String.format("  <li>%s</li>\n", action));
        }
        body.append("</ol>\n");
        body.append("<p><strong>This alert was generated automatically by Waqiti Ledger Service.</strong></p>\n");
        return body.toString();
    }

    private Map<String, Object> buildAuditDetails(EmergencyReconciliationRequest request) {
        return Map.of(
                "accountId", request.getAccountId().toString(),
                "currency", request.getCurrency(),
                "discrepancyAmount", request.getDiscrepancyAmount().toString(),
                "reason", request.getReason(),
                "initiatedAt", LocalDateTime.now().toString()
        );
    }

    private Map<String, Object> buildCompletionAuditDetails(EmergencyReconciliationResponse response) {
        return Map.of(
                "reconciliationId", response.getReconciliationId().toString(),
                "status", response.getStatus(),
                "severity", response.getSeverity(),
                "requiresManualReview", String.valueOf(response.isRequiresManualReview()),
                "completedAt", response.getCompletedAt().toString()
        );
    }

    /**
     * Discrepancy severity levels
     */
    private enum DiscrepancySeverity {
        LOW,        // < $1,000 - Auto-correctable
        MEDIUM,     // $1,000 - $10,000 - Finance Manager review
        HIGH,       // $10,000 - $100,000 - Controller approval required
        CRITICAL    // > $100,000 - C-suite escalation required
    }
}
