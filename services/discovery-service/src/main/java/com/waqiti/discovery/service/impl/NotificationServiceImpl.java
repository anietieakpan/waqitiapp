package com.waqiti.discovery.service.impl;

import com.waqiti.discovery.domain.ServiceHealthStatus;
import com.waqiti.discovery.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Notification Service Implementation
 * Stub implementation for sending notifications
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    @Override
    public void sendServiceUnhealthyAlert(String serviceName, ServiceHealthStatus healthStatus) {
        log.warn("ALERT: Service {} is unhealthy. Status: {}, Healthy instances: {}/{}",
            serviceName,
            healthStatus.getStatus(),
            healthStatus.getHealthyInstances(),
            healthStatus.getTotalInstances());

        // TODO: Implement actual notification sending (email, SMS, Slack, PagerDuty, etc.)
    }

    @Override
    public void sendServiceRecoveredNotification(String serviceName) {
        log.info("NOTIFICATION: Service {} has recovered and is now healthy", serviceName);

        // TODO: Implement actual notification sending
    }
}
