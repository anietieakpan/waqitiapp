package com.waqiti.wallet.service;

import com.waqiti.common.events.FraudDetectionEvent;

/**
 * Compliance Alert Service.
 *
 * <p>Manages compliance-related alerts and case creation for fraud and AML events.
 * Integrates with case management systems and regulatory reporting.
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 * @since 2025-10-18
 */
public interface ComplianceAlertService {

    /**
     * Send critical fraud alert to compliance team.
     *
     * @param event fraud detection event
     * @param walletsAffected number of wallets affected
     */
    void sendCriticalFraudAlert(FraudDetectionEvent event, int walletsAffected);

    /**
     * Create fraud review case with SLA.
     *
     * @param event fraud detection event
     * @param walletsAffected number of wallets affected
     * @param reviewSlaHours SLA in hours for review
     */
    void createFraudReviewCase(FraudDetectionEvent event, int walletsAffected, int reviewSlaHours);

    /**
     * Add fraud event to manual review queue.
     *
     * @param event fraud detection event
     */
    void addToManualReviewQueue(FraudDetectionEvent event);

    /**
     * Create critical DLT alert for failed processing.
     *
     * @param event fraud detection event
     * @param errorMessage error details
     */
    void createCriticalDltAlert(FraudDetectionEvent event, String errorMessage);
}
