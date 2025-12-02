package com.waqiti.wallet.service;

import java.util.List;
import java.util.UUID;

/**
 * Audit Log Service for wallet freeze operations.
 *
 * <p>Provides comprehensive audit trail for compliance and regulatory requirements:
 * <ul>
 *   <li>PCI DSS 10.2 - Audit trail requirements</li>
 *   <li>FinCEN 31 CFR 1022.210 - Record retention</li>
 *   <li>GDPR Article 30 - Records of processing activities</li>
 * </ul>
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 * @since 2025-10-18
 */
public interface AuditLogService {

    /**
     * Log wallet freeze event for audit trail.
     *
     * @param userId user whose wallets were frozen
     * @param walletIds list of frozen wallet IDs
     * @param freezeReason reason for freeze
     * @param severity severity level
     */
    void logWalletFreeze(UUID userId, List<String> walletIds, String freezeReason, String severity);

    /**
     * Link freeze record to case management system.
     *
     * @param walletId wallet ID
     * @param caseId case management system ID
     * @param freezeReason freeze reason
     */
    void linkToCaseManagement(String walletId, String caseId, String freezeReason);

    /**
     * Log wallet unfreeze event.
     *
     * @param userId user whose wallets were unfrozen
     * @param walletIds list of unfrozen wallet IDs
     * @param unfreezeReason reason for unfreeze
     */
    void logWalletUnfreeze(UUID userId, List<String> walletIds, String unfreezeReason);

    /**
     * Log fraud detection event processing.
     *
     * @param eventId fraud event ID
     * @param userId affected user
     * @param action action taken
     * @param riskScore risk score that triggered action
     */
    void logFraudEventProcessing(UUID eventId, UUID userId, String action, Double riskScore);

    /**
     * Log wallet freeze action (called from fraud consumer).
     *
     * @param userId user ID
     * @param eventId fraud event ID
     * @param walletsAffected number of wallets frozen
     * @param actionType type of action taken
     * @param riskScore risk score
     * @param fraudReason fraud reason
     */
    void logWalletFreezeAction(UUID userId, UUID eventId, int walletsAffected,
                               String actionType, double riskScore, String fraudReason);

    /**
     * Log fraud review flag event.
     *
     * @param userId user ID
     * @param eventId fraud event ID
     * @param reviewType type of review
     * @param riskScore risk score
     * @param fraudReason fraud reason
     */
    void logFraudReviewFlag(UUID userId, UUID eventId, String reviewType,
                           double riskScore, String fraudReason);

    /**
     * Log fraud monitoring event (low-risk).
     *
     * @param userId user ID
     * @param eventId fraud event ID
     * @param monitoringType type of monitoring
     * @param riskScore risk score
     * @param fraudReason fraud reason
     */
    void logFraudMonitoringEvent(UUID userId, UUID eventId, String monitoringType,
                                double riskScore, String fraudReason);
}
