package com.waqiti.saga.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Client for communicating with the notification service from saga steps
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.notification-service.url:http://notification-service}")
    private String notificationServiceUrl;

    @Value("${notification.timeout:10000}")
    private int timeoutMs;

    /**
     * Send push notification (async, best effort)
     */
    public CompletableFuture<Boolean> sendPushNotification(String userId, String title, String message, 
                                                          Map<String, Object> data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Sending push notification to user: {}", userId);

                String url = notificationServiceUrl + "/api/v1/notifications/push";
                
                Map<String, Object> request = Map.of(
                    "userId", userId,
                    "title", title,
                    "message", message,
                    "data", data != null ? data : Map.of(),
                    "priority", "HIGH",
                    "type", "TRANSACTION_UPDATE"
                );
                
                HttpHeaders headers = createHeaders();
                HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(request, headers);
                
                ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, httpEntity, Map.class);
                
                Map<String, Object> result = response.getBody();
                boolean success = result != null && Boolean.TRUE.equals(result.get("success"));
                
                log.debug("Push notification sent to user: {}, success: {}", userId, success);
                return success;
                
            } catch (Exception e) {
                log.warn("Failed to send push notification to user: {}", userId, e);
                return false; // Don't fail saga for notification failures
            }
        });
    }

    /**
     * Send email notification (async, best effort)
     */
    public CompletableFuture<Boolean> sendEmailNotification(String userId, String email, 
                                                           String templateId, Map<String, Object> templateData) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Sending email notification to user: {} ({})", userId, email);

                String url = notificationServiceUrl + "/api/v1/notifications/email";
                
                Map<String, Object> request = Map.of(
                    "userId", userId,
                    "email", email,
                    "templateId", templateId,
                    "templateData", templateData != null ? templateData : Map.of(),
                    "priority", "NORMAL"
                );
                
                HttpHeaders headers = createHeaders();
                HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(request, headers);
                
                ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, httpEntity, Map.class);
                
                Map<String, Object> result = response.getBody();
                boolean success = result != null && Boolean.TRUE.equals(result.get("success"));
                
                log.debug("Email notification sent to user: {}, success: {}", userId, success);
                return success;
                
            } catch (Exception e) {
                log.warn("Failed to send email notification to user: {}", userId, e);
                return false; // Don't fail saga for notification failures
            }
        });
    }

    /**
     * Send SMS notification (async, best effort)
     */
    public CompletableFuture<Boolean> sendSmsNotification(String userId, String phoneNumber, 
                                                         String message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Sending SMS notification to user: {} ({})", userId, phoneNumber);

                String url = notificationServiceUrl + "/api/v1/notifications/sms";
                
                Map<String, Object> request = Map.of(
                    "userId", userId,
                    "phoneNumber", phoneNumber,
                    "message", message,
                    "priority", "HIGH"
                );
                
                HttpHeaders headers = createHeaders();
                HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(request, headers);
                
                ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, httpEntity, Map.class);
                
                Map<String, Object> result = response.getBody();
                boolean success = result != null && Boolean.TRUE.equals(result.get("success"));
                
                log.debug("SMS notification sent to user: {}, success: {}", userId, success);
                return success;
                
            } catch (Exception e) {
                log.warn("Failed to send SMS notification to user: {}", userId, e);
                return false; // Don't fail saga for notification failures
            }
        });
    }

    /**
     * Send transaction completion notification
     */
    @Retryable(value = {Exception.class}, maxAttempts = 2, backoff = @Backoff(delay = 1000))
    public CompletableFuture<Boolean> sendTransactionCompletionNotification(String userId, 
                                                                           String transactionId, 
                                                                           String transactionType,
                                                                           String status) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Sending transaction completion notification: transactionId={}, status={}", 
                    transactionId, status);

                String title = getNotificationTitle(transactionType, status);
                String message = getNotificationMessage(transactionType, status, transactionId);
                
                Map<String, Object> data = Map.of(
                    "transactionId", transactionId,
                    "transactionType", transactionType,
                    "status", status,
                    "timestamp", LocalDateTime.now().toString()
                );
                
                return sendPushNotification(userId, title, message, data).get();
                
            } catch (Exception e) {
                log.warn("Failed to send transaction completion notification: transactionId={}", 
                    transactionId, e);
                return false;
            }
        });
    }

    /**
     * Send transaction failure notification
     */
    public CompletableFuture<Boolean> sendTransactionFailureNotification(String userId, 
                                                                        String transactionId, 
                                                                        String transactionType,
                                                                        String reason) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Sending transaction failure notification: transactionId={}, reason={}", 
                    transactionId, reason);

                String title = "Transaction Failed";
                String message = String.format("Your %s transaction could not be completed. Reference: %s", 
                    transactionType.toLowerCase().replace("_", " "), transactionId);
                
                Map<String, Object> data = Map.of(
                    "transactionId", transactionId,
                    "transactionType", transactionType,
                    "status", "FAILED",
                    "reason", reason,
                    "timestamp", LocalDateTime.now().toString()
                );
                
                return sendPushNotification(userId, title, message, data).get();
                
            } catch (Exception e) {
                log.warn("Failed to send transaction failure notification: transactionId={}", 
                    transactionId, e);
                return false;
            }
        });
    }

    /**
     * Cancel scheduled notification
     */
    public CompletableFuture<Boolean> cancelNotification(String notificationId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Canceling notification: {}", notificationId);

                String url = notificationServiceUrl + "/api/v1/notifications/" + notificationId + "/cancel";
                
                HttpHeaders headers = createHeaders();
                HttpEntity<?> httpEntity = new HttpEntity<>(headers);
                
                ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, httpEntity, Map.class);
                
                Map<String, Object> result = response.getBody();
                boolean success = result != null && Boolean.TRUE.equals(result.get("success"));
                
                log.debug("Notification cancellation result: notificationId={}, success={}", 
                    notificationId, success);
                return success;
                
            } catch (Exception e) {
                log.warn("Failed to cancel notification: {}", notificationId, e);
                return false;
            }
        });
    }

    /**
     * Health check for notification service
     */
    public boolean isNotificationServiceHealthy() {
        try {
            String url = notificationServiceUrl + "/actuator/health";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.debug("Notification service health check failed", e);
            return false;
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Service-Name", "saga-orchestration-service");
        headers.set("X-Request-ID", UUID.randomUUID().toString());
        return headers;
    }

    private String getNotificationTitle(String transactionType, String status) {
        if ("COMPLETED".equals(status)) {
            switch (transactionType.toUpperCase()) {
                case "P2P_TRANSFER":
                    return "Transfer Completed";
                case "DEPOSIT":
                    return "Deposit Completed";
                case "WITHDRAWAL":
                    return "Withdrawal Completed";
                default:
                    return "Transaction Completed";
            }
        } else if ("FAILED".equals(status)) {
            return "Transaction Failed";
        } else {
            return "Transaction Update";
        }
    }

    private String getNotificationMessage(String transactionType, String status, String transactionId) {
        if ("COMPLETED".equals(status)) {
            return String.format("Your %s has been completed successfully. Reference: %s", 
                transactionType.toLowerCase().replace("_", " "), transactionId);
        } else if ("FAILED".equals(status)) {
            return String.format("Your %s could not be completed. Reference: %s", 
                transactionType.toLowerCase().replace("_", " "), transactionId);
        } else {
            return String.format("Your %s status has been updated to %s. Reference: %s", 
                transactionType.toLowerCase().replace("_", " "), status, transactionId);
        }
    }
}