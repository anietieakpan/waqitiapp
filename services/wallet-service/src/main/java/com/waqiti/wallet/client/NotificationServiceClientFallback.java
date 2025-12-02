package com.waqiti.wallet.client;

import com.waqiti.wallet.dto.WalletClosureNotificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback for NotificationServiceClient
 *
 * Provides graceful degradation when notification-service is unavailable.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Slf4j
@Component
public class NotificationServiceClientFallback implements NotificationServiceClient {

    @Override
    public void sendWalletClosureNotification(WalletClosureNotificationRequest request) {
        log.warn("NotificationServiceClient fallback triggered for wallet closure notification: userId={}",
                request.getUserId());
        // Notification failure should not block wallet closure
        // Notification will be retried via circuit breaker or sent via alternative channel
    }
}
