package com.waqiti.wallet.service;

import com.waqiti.common.events.FraudDetectionEvent;

import java.util.UUID;

/**
 * Fraud Event Notification Service.
 *
 * <p>Handles all notifications related to fraud detection events including:
 * <ul>
 *   <li>User notifications (email, SMS, push)</li>
 *   <li>Security operations alerts (PagerDuty, Slack)</li>
 *   <li>Enhanced monitoring activation</li>
 *   <li>User risk profile updates</li>
 * </ul>
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 * @since 2025-10-18
 */
public interface FraudEventNotificationService {

    /**
     * Notify user that their wallet has been frozen due to fraud detection.
     *
     * @param userId user ID
     * @param event fraud detection event
     * @param walletCount number of wallets frozen
     */
    void notifyUserOfFraudFreeze(UUID userId, FraudDetectionEvent event, int walletCount);

    /**
     * Enable enhanced monitoring for a user after fraud detection.
     *
     * @param userId user ID
     * @param event fraud detection event
     */
    void enableEnhancedMonitoring(UUID userId, FraudDetectionEvent event);

    /**
     * Update user's fraud risk profile.
     *
     * @param userId user ID
     * @param riskScore new risk score (0-1.0)
     */
    void updateUserRiskProfile(UUID userId, double riskScore);

    /**
     * Alert security operations team about critical event.
     *
     * @param alertType type of alert
     * @param message alert message
     */
    void alertSecurityOps(String alertType, String message);
}
