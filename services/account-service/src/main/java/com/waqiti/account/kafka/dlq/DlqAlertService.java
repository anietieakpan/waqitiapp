package com.waqiti.account.kafka.dlq;

import java.util.Map;

/**
 * Alert service interface for DLQ events
 *
 * <p>Implementations should integrate with:</p>
 * <ul>
 *   <li>PagerDuty - for critical production incidents</li>
 *   <li>Slack - for team notifications and warnings</li>
 *   <li>Email - for compliance and audit notifications</li>
 * </ul>
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
public interface DlqAlertService {

    /**
     * Send critical alert (PagerDuty + Slack)
     *
     * <p>Use for: handler failures, high-impact permanent failures,
     * critical priority manual reviews (15min SLA)</p>
     *
     * @param title Alert title
     * @param message Alert message
     * @param context Additional context metadata
     */
    void sendCriticalAlert(String title, String message, Map<String, String> context);

    /**
     * Send high priority alert (Slack + Email)
     *
     * <p>Use for: manual review required (1hr SLA), external service
     * failures, database issues</p>
     *
     * @param title Alert title
     * @param message Alert message
     * @param context Additional context metadata
     */
    void sendHighPriorityAlert(String title, String message, Map<String, String> context);

    /**
     * Send warning alert (Slack only)
     *
     * <p>Use for: approaching max retries, medium priority reviews,
     * elevated error rates</p>
     *
     * @param title Alert title
     * @param message Alert message
     * @param context Additional context metadata
     */
    void sendWarningAlert(String title, String message, Map<String, String> context);

    /**
     * Send info notification (Slack only)
     *
     * <p>Use for: recovery success, metrics thresholds, informational updates</p>
     *
     * @param title Notification title
     * @param message Notification message
     * @param context Additional context metadata
     */
    void sendInfoNotification(String title, String message, Map<String, String> context);
}
