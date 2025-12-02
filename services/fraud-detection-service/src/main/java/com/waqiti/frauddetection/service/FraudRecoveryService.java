package com.waqiti.frauddetection.service;

import com.waqiti.frauddetection.dto.FraudAlertEvent;
import com.waqiti.frauddetection.entity.FraudAlert;
import com.waqiti.frauddetection.repository.FraudAlertRepository;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Fraud Recovery Service
 *
 * Handles recovery of failed fraud detection events from DLQ.
 * Implements business logic for fraud alert processing and security incident creation.
 *
 * @author Waqiti Security Team
 * @version 2.0.0
 * @since 2025-11-04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudRecoveryService {

    private final FraudAlertRepository fraudAlertRepository;
    private final SecurityIncidentService securityIncidentService;
    private final UserAccountService userAccountService;
    private final FraudAnalystNotificationService notificationService;
    private final AuditService auditService;

    /**
     * Check if fraud alert already handled
     */
    @Transactional(readOnly = true)
    public boolean isAlreadyHandled(String fraudAlertId) {
        return fraudAlertRepository.findByFraudAlertId(fraudAlertId)
                .map(alert -> alert.getStatus().equals("PROCESSED") ||
                             alert.getStatus().equals("RESOLVED"))
                .orElse(false);
    }

    /**
     * Attempt to recover failed fraud alert
     *
     * @param fraudEvent Failed fraud alert event
     * @param severity Fraud severity level
     * @param retryCount Current retry attempt
     * @return Recovery result
     */
    @Transactional
    public FraudRecoveryResult recoverFraudAlert(
            FraudAlertEvent fraudEvent,
            FraudSeverity severity,
            int retryCount) {

        log.info("Attempting fraud alert recovery: fraudAlertId={}, severity={}, retryCount={}",
                fraudEvent.getFraudAlertId(), severity, retryCount);

        try {
            // Get or create fraud alert record
            FraudAlert alert = fraudAlertRepository.findByFraudAlertId(fraudEvent.getFraudAlertId())
                    .orElseGet(() -> createFraudAlert(fraudEvent));

            // Process based on fraud type
            String fraudType = fraudEvent.getFraudType();

            if ("ACCOUNT_TAKEOVER".equals(fraudType)) {
                return handleAccountTakeoverRecovery(alert, fraudEvent, severity);

            } else if ("PAYMENT_FRAUD".equals(fraudType)) {
                return handlePaymentFraudRecovery(alert, fraudEvent, severity);

            } else if ("IDENTITY_THEFT".equals(fraudType)) {
                return handleIdentityTheftRecovery(alert, fraudEvent, severity);

            } else if ("SUSPICIOUS_ACTIVITY".equals(fraudType)) {
                return handleSuspiciousActivityRecovery(alert, fraudEvent, severity);

            } else {
                return handleGenericFraudRecovery(alert, fraudEvent, severity);
            }

        } catch (Exception e) {
            log.error("Exception during fraud alert recovery: fraudAlertId={}",
                    fraudEvent.getFraudAlertId(), e);

            return FraudRecoveryResult.builder()
                    .recovered(false)
                    .retriable(true)
                    .failureReason("Exception: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Create security incident for critical fraud
     */
    public String createSecurityIncident(
            FraudAlertEvent fraudEvent,
            String failureReason,
            int retryCount) {

        log.error("Creating security incident for failed fraud alert: fraudAlertId={}, reason={}",
                fraudEvent.getFraudAlertId(), failureReason);

        String incidentId = securityIncidentService.createIncident(
                "FRAUD_ALERT_PROCESSING_FAILED",
                fraudEvent.getUserId(),
                fraudEvent.getFraudType(),
                fraudEvent.getRiskScore(),
                failureReason,
                Map.of(
                        "fraudAlertId", fraudEvent.getFraudAlertId(),
                        "retryCount", String.valueOf(retryCount),
                        "failureReason", failureReason
                )
        );

        auditService.logSecurityEvent(
                "SECURITY_INCIDENT_CREATED",
                fraudEvent.getUserId(),
                Map.of(
                        "incidentId", incidentId,
                        "fraudAlertId", fraudEvent.getFraudAlertId(),
                        "fraudType", fraudEvent.getFraudType()
                )
        );

        return incidentId;
    }

    /**
     * Notify fraud analysts of critical failure
     */
    public void notifyFraudAnalysts(
            FraudAlertEvent fraudEvent,
            FraudSeverity severity,
            String alertType,
            String reason) {

        log.warn("Notifying fraud analysts: fraudAlertId={}, severity={}, type={}",
                fraudEvent.getFraudAlertId(), severity, alertType);

        notificationService.sendFraudAnalystAlert(
                fraudEvent.getFraudAlertId(),
                fraudEvent.getUserId(),
                fraudEvent.getFraudType(),
                fraudEvent.getRiskScore(),
                severity.name(),
                alertType,
                reason
        );
    }

    /**
     * Emergency account freeze for suspected takeover
     */
    @Transactional
    public void freezeAccountEmergency(String userId, String reason) {
        log.error("EMERGENCY ACCOUNT FREEZE: userId={}, reason={}", userId, reason);

        userAccountService.freezeAccount(userId, reason, true); // immediate = true

        auditService.logSecurityEvent(
                "EMERGENCY_ACCOUNT_FREEZE",
                userId,
                Map.of("reason", reason, "source", "FraudRecoveryService")
        );
    }

    // ========== PRIVATE RECOVERY METHODS ==========

    private FraudAlert createFraudAlert(FraudAlertEvent event) {
        FraudAlert alert = new FraudAlert();
        alert.setFraudAlertId(event.getFraudAlertId());
        alert.setUserId(event.getUserId());
        alert.setFraudType(event.getFraudType());
        alert.setRiskScore(event.getRiskScore());
        alert.setStatus("PENDING");
        return fraudAlertRepository.save(alert);
    }

    private FraudRecoveryResult handleAccountTakeoverRecovery(
            FraudAlert alert,
            FraudAlertEvent event,
            FraudSeverity severity) {

        // For account takeover, immediate action required
        if (severity == FraudSeverity.CRITICAL || severity == FraudSeverity.HIGH) {
            // Freeze account immediately
            freezeAccountEmergency(event.getUserId(), "Account takeover suspected");

            alert.setStatus("PROCESSED");
            alert.setActionTaken("ACCOUNT_FROZEN");
            fraudAlertRepository.save(alert);

            return FraudRecoveryResult.builder()
                    .recovered(true)
                    .recoveryAction("ACCOUNT_FROZEN")
                    .details("Account frozen due to takeover suspicion")
                    .build();
        }

        return FraudRecoveryResult.builder()
                .recovered(false)
                .retriable(false)
                .failureReason("Medium/Low severity account takeover - manual review required")
                .build();
    }

    private FraudRecoveryResult handlePaymentFraudRecovery(
            FraudAlert alert,
            FraudAlertEvent event,
            FraudSeverity severity) {

        // Payment fraud - flag transaction for review
        alert.setStatus("UNDER_REVIEW");
        fraudAlertRepository.save(alert);

        return FraudRecoveryResult.builder()
                .recovered(true)
                .recoveryAction("FLAGGED_FOR_REVIEW")
                .details("Payment fraud flagged for manual review")
                .build();
    }

    private FraudRecoveryResult handleIdentityTheftRecovery(
            FraudAlert alert,
            FraudAlertEvent event,
            FraudSeverity severity) {

        // Identity theft - require additional verification
        alert.setStatus("VERIFICATION_REQUIRED");
        fraudAlertRepository.save(alert);

        return FraudRecoveryResult.builder()
                .recovered(true)
                .recoveryAction("VERIFICATION_REQUIRED")
                .details("Identity theft - additional verification required")
                .build();
    }

    private FraudRecoveryResult handleSuspiciousActivityRecovery(
            FraudAlert alert,
            FraudAlertEvent event,
            FraudSeverity severity) {

        // Suspicious activity - monitor and log
        alert.setStatus("MONITORING");
        fraudAlertRepository.save(alert);

        return FraudRecoveryResult.builder()
                .recovered(true)
                .recoveryAction("MONITORING_ENABLED")
                .details("Suspicious activity - enhanced monitoring enabled")
                .build();
    }

    private FraudRecoveryResult handleGenericFraudRecovery(
            FraudAlert alert,
            FraudAlertEvent event,
            FraudSeverity severity) {

        // Generic fraud - log and flag
        alert.setStatus("FLAGGED");
        fraudAlertRepository.save(alert);

        return FraudRecoveryResult.builder()
                .recovered(true)
                .recoveryAction("FLAGGED")
                .details("Generic fraud alert flagged")
                .build();
    }

    public enum FraudSeverity {
        CRITICAL, HIGH, MEDIUM, LOW
    }
}
