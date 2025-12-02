package com.waqiti.account.kafka.dlq;

/**
 * Metrics service interface for DLQ monitoring
 *
 * <p>Tracks DLQ-specific metrics beyond standard Micrometer counters.
 * Implementations should integrate with monitoring dashboards and
 * support threshold-based alerting.</p>
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
public interface DlqMetricsService {

    /**
     * Record discarded message
     *
     * @param handlerName Handler name
     * @param topic Original topic
     * @param reason Discard reason
     */
    void recordDiscardedMessage(String handlerName, String topic, String reason);

    /**
     * Record recovery success
     *
     * @param handlerName Handler name
     * @param retryAttempt Retry attempt that succeeded
     * @param duration Recovery duration in milliseconds
     */
    void recordRecoverySuccess(String handlerName, int retryAttempt, long duration);

    /**
     * Record recovery failure
     *
     * @param handlerName Handler name
     * @param retryAttempt Final retry attempt
     * @param reason Failure reason
     */
    void recordRecoveryFailure(String handlerName, int retryAttempt, String reason);

    /**
     * Record SLA breach
     *
     * @param handlerName Handler name
     * @param priority Review priority
     * @param breachDuration SLA breach duration in milliseconds
     */
    void recordSlaBreach(String handlerName, String priority, long breachDuration);

    /**
     * Record manual intervention
     *
     * @param handlerName Handler name
     * @param action Action taken (RESOLVED, ESCALATED, DISMISSED)
     * @param duration Time to resolution in milliseconds
     */
    void recordManualIntervention(String handlerName, String action, long duration);
}
