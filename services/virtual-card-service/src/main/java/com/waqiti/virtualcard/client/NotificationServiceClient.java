package com.waqiti.virtualcard.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Feign Client for Notification Service
 *
 * Provides notification operations with:
 * - Circuit breaker protection
 * - Automatic retries
 * - Fallback for graceful degradation
 * - Support for multiple notification channels (email, SMS, push)
 */
@FeignClient(
    name = "notification-service",
    url = "${services.notification-service.url:http://notification-service:8084}",
    fallback = NotificationServiceClientFallback.class,
    configuration = FeignClientConfiguration.class
)
public interface NotificationServiceClient {

    /**
     * Send card created notification
     *
     * @param request Notification request
     * @return Notification result
     */
    @PostMapping("/api/v1/notifications/card-created")
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendNotificationFallback")
    @Retry(name = "notification-service")
    NotificationResponse sendCardCreatedNotification(@RequestBody CardNotificationRequest request);

    /**
     * Send card status change notification
     *
     * @param request Notification request
     * @return Notification result
     */
    @PostMapping("/api/v1/notifications/card-status-changed")
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendNotificationFallback")
    @Retry(name = "notification-service")
    NotificationResponse sendCardStatusNotification(@RequestBody CardNotificationRequest request);

    /**
     * Send transaction notification
     *
     * @param request Notification request
     * @return Notification result
     */
    @PostMapping("/api/v1/notifications/transaction")
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendNotificationFallback")
    @Retry(name = "notification-service")
    NotificationResponse sendTransactionNotification(@RequestBody TransactionNotificationRequest request);

    /**
     * Send security alert
     *
     * @param request Security alert request
     * @return Notification result
     */
    @PostMapping("/api/v1/notifications/security-alert")
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendNotificationFallback")
    @Retry(name = "notification-service")
    NotificationResponse sendSecurityAlert(@RequestBody SecurityAlertRequest request);

    /**
     * Send card funding notification
     *
     * @param request Notification request
     * @return Notification result
     */
    @PostMapping("/api/v1/notifications/card-funded")
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendNotificationFallback")
    @Retry(name = "notification-service")
    NotificationResponse sendCardFundedNotification(@RequestBody CardNotificationRequest request);

    /**
     * Send card withdrawal notification
     *
     * @param request Notification request
     * @return Notification result
     */
    @PostMapping("/api/v1/notifications/card-withdrawal")
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendNotificationFallback")
    @Retry(name = "notification-service")
    NotificationResponse sendCardWithdrawalNotification(@RequestBody CardNotificationRequest request);

    /**
     * Send transaction declined notification
     *
     * @param request Notification request
     * @return Notification result
     */
    @PostMapping("/api/v1/notifications/transaction-declined")
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendNotificationFallback")
    @Retry(name = "notification-service")
    NotificationResponse sendTransactionDeclinedNotification(@RequestBody TransactionNotificationRequest request);

    /**
     * Send fraud alert notification
     *
     * @param request Fraud alert request
     * @return Notification result
     */
    @PostMapping("/api/v1/notifications/fraud-alert")
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendNotificationFallback")
    @Retry(name = "notification-service")
    NotificationResponse sendFraudAlert(@RequestBody FraudAlertRequest request);

    /**
     * Send card closed notification
     *
     * @param request Notification request
     * @return Notification result
     */
    @PostMapping("/api/v1/notifications/card-closed")
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendNotificationFallback")
    @Retry(name = "notification-service")
    NotificationResponse sendCardClosedNotification(@RequestBody CardNotificationRequest request);

    /**
     * Send card expired notification
     *
     * @param request Notification request
     * @return Notification result
     */
    @PostMapping("/api/v1/notifications/card-expired")
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendNotificationFallback")
    @Retry(name = "notification-service")
    NotificationResponse sendCardExpiredNotification(@RequestBody CardNotificationRequest request);

    // DTOs for notification operations

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class CardNotificationRequest {
        private String userId;
        private String cardId;
        private String message;
        private Map<String, Object> data;
        private NotificationChannel[] channels; // EMAIL, SMS, PUSH
        private NotificationPriority priority;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class TransactionNotificationRequest {
        private String userId;
        private String cardId;
        private String transactionId;
        private String amount;
        private String currency;
        private String merchantName;
        private String message;
        private Map<String, Object> data;
        private NotificationChannel[] channels;
        private NotificationPriority priority;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class SecurityAlertRequest {
        private String userId;
        private String alertType;
        private String message;
        private String severity; // LOW, MEDIUM, HIGH, CRITICAL
        private Map<String, Object> data;
        private NotificationChannel[] channels;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class FraudAlertRequest {
        private String userId;
        private String cardId;
        private String transactionId;
        private String fraudReason;
        private String riskScore;
        private Map<String, Object> data;
        private NotificationChannel[] channels;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class NotificationResponse {
        private boolean success;
        private String notificationId;
        private String message;
        private String errorCode;
        private Map<String, ChannelResult> channelResults;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class ChannelResult {
        private boolean sent;
        private String messageId;
        private String error;
    }

    enum NotificationChannel {
        EMAIL,
        SMS,
        PUSH,
        IN_APP
    }

    enum NotificationPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }
}
