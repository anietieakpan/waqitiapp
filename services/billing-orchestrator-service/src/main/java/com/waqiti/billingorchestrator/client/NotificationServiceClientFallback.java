package com.waqiti.billingorchestrator.client;

import com.waqiti.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback implementation for NotificationServiceClient
 *
 * Provides graceful degradation when notification-service is unavailable
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Component
@Slf4j
public class NotificationServiceClientFallback implements NotificationServiceClient {

    @Override
    public ApiResponse<Void> sendEmail(EmailNotificationRequest request, String requestId) {
        log.error("Notification service unavailable - email not sent: userId={}, requestId={}",
                request.userId(), requestId);
        // Don't fail billing operations if notification fails
        return ApiResponse.success(null);
    }

    @Override
    public ApiResponse<Void> sendSms(SmsNotificationRequest request, String requestId) {
        log.error("Notification service unavailable - SMS not sent: userId={}, requestId={}",
                request.userId(), requestId);
        // Don't fail billing operations if notification fails
        return ApiResponse.success(null);
    }

    @Override
    public ApiResponse<Void> sendPush(PushNotificationRequest request, String requestId) {
        log.error("Notification service unavailable - push notification not sent: userId={}, requestId={}",
                request.userId(), requestId);
        // Don't fail billing operations if notification fails
        return ApiResponse.success(null);
    }
}
