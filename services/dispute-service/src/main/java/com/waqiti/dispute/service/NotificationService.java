package com.waqiti.dispute.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Notification Service (Dispute Service Wrapper)
 *
 * Provides notification capabilities for dispute-related events
 * Wraps common notification service or provides standalone implementation
 *
 * @author Waqiti Dispute Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    // Uncomment if common notification service is available
    // private final com.waqiti.common.notification.NotificationService commonNotificationService;

    /**
     * Send notification to user
     */
    public void sendNotification(String userId, String message) {
        log.info("Sending notification to user {}: {}", userId, message);
        // Implementation or delegate to common service
    }

    /**
     * Send dispute status notification
     */
    public void sendDisputeStatusNotification(String userId, String disputeId, String status) {
        log.info("Sending dispute status notification: user={}, dispute={}, status={}",
            userId, disputeId, status);
        sendNotification(userId, String.format("Your dispute %s status: %s", disputeId, status));
    }

    /**
     * Send dispute resolution notification
     */
    public void sendDisputeResolutionNotification(String userId, String disputeId, String resolution) {
        log.info("Sending dispute resolution notification: user={}, dispute={}, resolution={}",
            userId, disputeId, resolution);
        sendNotification(userId, String.format("Your dispute %s has been resolved: %s", disputeId, resolution));
    }
}
