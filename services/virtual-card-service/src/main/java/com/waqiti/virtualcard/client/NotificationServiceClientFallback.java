package com.waqiti.virtualcard.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;

/**
 * Fallback implementation for NotificationServiceClient
 *
 * Provides graceful degradation when notification service is unavailable
 * Notifications are logged but application continues to function
 */
@Slf4j
@Component
public class NotificationServiceClientFallback implements NotificationServiceClient {

    @Override
    public NotificationResponse sendCardCreatedNotification(CardNotificationRequest request) {
        log.warn("Notification service unavailable - card created notification not sent. userId={}, cardId={}",
            request.getUserId(), request.getCardId());
        return createFallbackResponse("Card created notification queued");
    }

    @Override
    public NotificationResponse sendCardStatusNotification(CardNotificationRequest request) {
        log.warn("Notification service unavailable - card status notification not sent. userId={}, cardId={}",
            request.getUserId(), request.getCardId());
        return createFallbackResponse("Card status notification queued");
    }

    @Override
    public NotificationResponse sendTransactionNotification(TransactionNotificationRequest request) {
        log.warn("Notification service unavailable - transaction notification not sent. userId={}, transactionId={}",
            request.getUserId(), request.getTransactionId());
        return createFallbackResponse("Transaction notification queued");
    }

    @Override
    public NotificationResponse sendSecurityAlert(SecurityAlertRequest request) {
        log.error("CRITICAL: Notification service unavailable - security alert not sent. userId={}, alertType={}",
            request.getUserId(), request.getAlertType());
        // Security alerts are critical - log at ERROR level
        return createFallbackResponse("Security alert queued");
    }

    @Override
    public NotificationResponse sendCardFundedNotification(CardNotificationRequest request) {
        log.warn("Notification service unavailable - card funded notification not sent. userId={}, cardId={}",
            request.getUserId(), request.getCardId());
        return createFallbackResponse("Card funded notification queued");
    }

    @Override
    public NotificationResponse sendCardWithdrawalNotification(CardNotificationRequest request) {
        log.warn("Notification service unavailable - card withdrawal notification not sent. userId={}, cardId={}",
            request.getUserId(), request.getCardId());
        return createFallbackResponse("Card withdrawal notification queued");
    }

    @Override
    public NotificationResponse sendTransactionDeclinedNotification(TransactionNotificationRequest request) {
        log.warn("Notification service unavailable - transaction declined notification not sent. userId={}, transactionId={}",
            request.getUserId(), request.getTransactionId());
        return createFallbackResponse("Transaction declined notification queued");
    }

    @Override
    public NotificationResponse sendFraudAlert(FraudAlertRequest request) {
        log.error("CRITICAL: Notification service unavailable - fraud alert not sent. userId={}, cardId={}",
            request.getUserId(), request.getCardId());
        // Fraud alerts are critical - log at ERROR level
        return createFallbackResponse("Fraud alert queued");
    }

    @Override
    public NotificationResponse sendCardClosedNotification(CardNotificationRequest request) {
        log.warn("Notification service unavailable - card closed notification not sent. userId={}, cardId={}",
            request.getUserId(), request.getCardId());
        return createFallbackResponse("Card closed notification queued");
    }

    @Override
    public NotificationResponse sendCardExpiredNotification(CardNotificationRequest request) {
        log.warn("Notification service unavailable - card expired notification not sent. userId={}, cardId={}",
            request.getUserId(), request.getCardId());
        return createFallbackResponse("Card expired notification queued");
    }

    private NotificationResponse createFallbackResponse(String message) {
        return NotificationResponse.builder()
            .success(false)
            .errorCode("SERVICE_UNAVAILABLE")
            .message(message)
            .channelResults(new HashMap<>())
            .build();
    }
}
