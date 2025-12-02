package com.waqiti.discovery.service;

import com.waqiti.discovery.domain.ServiceHealthStatus;

/**
 * Notification Service Interface
 * Sends alerts and notifications for service events
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
public interface NotificationService {

    /**
     * Send service unhealthy alert
     *
     * @param serviceName service name
     * @param healthStatus health status
     */
    void sendServiceUnhealthyAlert(String serviceName, ServiceHealthStatus healthStatus);

    /**
     * Send service recovered notification
     *
     * @param serviceName service name
     */
    void sendServiceRecoveredNotification(String serviceName);
}
