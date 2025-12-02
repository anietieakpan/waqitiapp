package com.waqiti.common.client;

import com.waqiti.common.notification.TestResult;
import com.waqiti.common.notification.model.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Client for interacting with the notification microservice
 * Provides resilient communication with circuit breaker and retry capabilities
 */
@Component("notificationWebClient")
@Slf4j
public class NotificationServiceClient {

    private final WebClient webClient;
    private static final String API_PREFIX = "/api/v1/notifications";

    public NotificationServiceClient(WebClient.Builder webClientBuilder,
                                   @Value("${notification-service.url:http://localhost:8084}") String baseUrl) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    // Email notifications
    @CircuitBreaker(name = "notification-service", fallbackMethod = "emailNotificationFallback")
    @Retry(name = "notification-service")
    public NotificationResult sendEmailNotification(EmailNotificationRequest request) {
        log.debug("Sending email notification via microservice");
        
        return webClient.post()
                .uri(API_PREFIX + "/email")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(NotificationResult.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // SMS notifications
    @CircuitBreaker(name = "notification-service", fallbackMethod = "smsNotificationFallback")
    @Retry(name = "notification-service")
    public NotificationResult sendSmsNotification(SmsNotificationRequest request) {
        log.debug("Sending SMS notification via microservice");
        
        return webClient.post()
                .uri(API_PREFIX + "/sms")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(NotificationResult.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // Push notifications
    @CircuitBreaker(name = "notification-service", fallbackMethod = "pushNotificationFallback")
    @Retry(name = "notification-service")
    public NotificationResult sendPushNotification(PushNotificationRequest request) {
        log.debug("Sending push notification via microservice");
        
        return webClient.post()
                .uri(API_PREFIX + "/push")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(NotificationResult.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // In-app notifications
    @CircuitBreaker(name = "notification-service", fallbackMethod = "inAppNotificationFallback")
    @Retry(name = "notification-service")
    public NotificationResult sendInAppNotification(InAppNotificationRequest request) {
        log.debug("Sending in-app notification via microservice");
        
        return webClient.post()
                .uri(API_PREFIX + "/in-app")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(NotificationResult.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // Webhook notifications
    @CircuitBreaker(name = "notification-service", fallbackMethod = "webhookNotificationFallback")
    @Retry(name = "notification-service")
    public NotificationResult sendWebhookNotification(WebhookNotificationRequest request) {
        log.debug("Sending webhook notification via microservice");

        return webClient.post()
                .uri(API_PREFIX + "/webhook")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(NotificationResult.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // Slack notifications
    @CircuitBreaker(name = "notification-service", fallbackMethod = "slackNotificationFallback")
    @Retry(name = "notification-service")
    public NotificationResult sendSlackNotification(SlackNotificationRequest request) {
        log.debug("Sending Slack notification via microservice to channel: {}", request.getSlackChannel());

        return webClient.post()
                .uri(API_PREFIX + "/slack")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(NotificationResult.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // Multi-channel notifications
    public MultiChannelNotificationResult sendMultiChannelNotification(MultiChannelNotificationRequest request) {
        return webClient.post()
                .uri(API_PREFIX + "/multi-channel")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(MultiChannelNotificationResult.class)
                .timeout(Duration.ofSeconds(60))
                .block();
    }

    // Batch notifications
    public BatchNotificationResult sendBatchNotification(BatchNotificationRequest request) {
        return webClient.post()
                .uri(API_PREFIX + "/batch")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(BatchNotificationResult.class)
                .timeout(Duration.ofSeconds(120))
                .block();
    }

    // Templated notifications
    public NotificationResult sendTemplatedNotification(TemplatedNotificationRequest request) {
        return webClient.post()
                .uri(API_PREFIX + "/templated")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(NotificationResult.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // Schedule notifications
    public ScheduledNotificationResult scheduleNotification(ScheduledNotificationRequest request) {
        return webClient.post()
                .uri(API_PREFIX + "/schedule")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ScheduledNotificationResult.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // Cancel scheduled notification
    public void cancelScheduledNotification(String notificationId) {
        webClient.delete()
                .uri(API_PREFIX + "/schedule/{id}", notificationId)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // Get notification status
    public NotificationStatus getNotificationStatus(String notificationId) {
        return webClient.get()
                .uri(API_PREFIX + "/{id}/status", notificationId)
                .retrieve()
                .bodyToMono(NotificationStatus.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // Get delivery report
    public DeliveryReport getDeliveryReport(String notificationId) {
        return webClient.get()
                .uri(API_PREFIX + "/{id}/delivery-report", notificationId)
                .retrieve()
                .bodyToMono(DeliveryReport.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // User preferences
    public NotificationPreferences getUserPreferences(String userId) {
        return webClient.get()
                .uri(API_PREFIX + "/users/{userId}/preferences", userId)
                .retrieve()
                .bodyToMono(NotificationPreferences.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    public NotificationPreferences updateUserPreferences(String userId, NotificationPreferences preferences) {
        return webClient.put()
                .uri(API_PREFIX + "/users/{userId}/preferences", userId)
                .bodyValue(preferences)
                .retrieve()
                .bodyToMono(NotificationPreferences.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // Critical alerts
    @CircuitBreaker(name = "notification-service", fallbackMethod = "criticalAlertFallback")
    @Retry(name = "notification-service")
    public NotificationResult sendCriticalAlert(CriticalAlertRequest request) {
        log.warn("Sending critical alert via microservice: {}", request.getTitle());
        
        return webClient.post()
                .uri(API_PREFIX + "/critical-alert")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(NotificationResult.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // Security alerts
    public NotificationResult sendSecurityAlert(SecurityAlertRequest request) {
        return webClient.post()
                .uri(API_PREFIX + "/security-alert")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(NotificationResult.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // Compliance notifications
    public NotificationResult sendComplianceNotification(ComplianceNotificationRequest request) {
        return webClient.post()
                .uri(API_PREFIX + "/compliance")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(NotificationResult.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // Analytics
    public NotificationAnalytics getNotificationAnalytics(AnalyticsRequest request) {
        return webClient.post()
                .uri(API_PREFIX + "/analytics")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(NotificationAnalytics.class)
                .timeout(Duration.ofSeconds(60))
                .block();
    }

    // Template management
    public TemplateRegistrationResult registerTemplate(NotificationTemplate template) {
        return webClient.post()
                .uri(API_PREFIX + "/templates")
                .bodyValue(template)
                .retrieve()
                .bodyToMono(TemplateRegistrationResult.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    public TemplateRegistrationResult updateTemplate(String templateId, NotificationTemplate template) {
        return webClient.put()
                .uri(API_PREFIX + "/templates/{id}", templateId)
                .bodyValue(template)
                .retrieve()
                .bodyToMono(TemplateRegistrationResult.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    public void deleteTemplate(String templateId) {
        webClient.delete()
                .uri(API_PREFIX + "/templates/{id}", templateId)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    public NotificationTemplate getTemplate(String templateId) {
        return webClient.get()
                .uri(API_PREFIX + "/templates/{id}", templateId)
                .retrieve()
                .bodyToMono(NotificationTemplate.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    public List<NotificationTemplate> listTemplates(TemplateFilter filter) {
        return webClient.post()
                .uri(API_PREFIX + "/templates/search")
                .bodyValue(filter)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<NotificationTemplate>>() {})
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // Event subscriptions
    public SubscriptionResult subscribeToEvents(NotificationEventSubscription subscription) {
        return webClient.post()
                .uri(API_PREFIX + "/events/subscribe")
                .bodyValue(subscription)
                .retrieve()
                .bodyToMono(SubscriptionResult.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    public void unsubscribeFromEvents(String subscriptionId) {
        webClient.delete()
                .uri(API_PREFIX + "/events/subscribe/{id}", subscriptionId)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // Notification history
    public NotificationHistory getUserNotificationHistory(String userId, HistoryFilter filter) {
        return webClient.post()
                .uri(API_PREFIX + "/users/{userId}/history", userId)
                .bodyValue(filter)
                .retrieve()
                .bodyToMono(NotificationHistory.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // Mark as read
    public void markAsRead(String userId, String notificationId) {
        webClient.put()
                .uri(API_PREFIX + "/users/{userId}/notifications/{notificationId}/read", userId, notificationId)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    public void markAllAsRead(String userId) {
        webClient.put()
                .uri(API_PREFIX + "/users/{userId}/notifications/read-all", userId)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // Delete notification
    public void deleteNotification(String userId, String notificationId) {
        webClient.delete()
                .uri(API_PREFIX + "/users/{userId}/notifications/{notificationId}", userId, notificationId)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // Get unread count
    public UnreadCount getUnreadCount(String userId) {
        return webClient.get()
                .uri(API_PREFIX + "/users/{userId}/unread-count", userId)
                .retrieve()
                .bodyToMono(UnreadCount.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    // Test configuration
    public CompletableFuture<com.waqiti.common.notification.TestResult> testConfiguration(NotificationChannel channel, Map<String, Object> config) {
        return webClient.post()
                .uri(API_PREFIX + "/test/{channel}", channel)
                .bodyValue(config)
                .retrieve()
                .bodyToMono(com.waqiti.common.notification.TestResult.class)
                .timeout(Duration.ofSeconds(60))
                .toFuture();
    }

    // Legacy methods for backward compatibility
    @Deprecated
    public void sendEmail(String to, String subject, String content, String templateId) {
        EmailNotificationRequest request = EmailNotificationRequest.builder()
                .to(List.of(to))
                .subject(subject)
                .htmlContent(content)
                .templateId(templateId)
                .build();
        sendEmailNotification(request);
    }

    @Deprecated
    public void sendSms(String phoneNumber, String message) {
        SmsNotificationRequest request = SmsNotificationRequest.builder()
                .phoneNumber(phoneNumber)
                .message(message)
                .build();
        sendSmsNotification(request);
    }

    @Deprecated
    public void sendPushNotification(String userId, String title, String message, Map<String, Object> data) {
        PushNotificationRequest request = PushNotificationRequest.builder()
                .userId(userId)
                .title(title)
                .body(message)
                .data(data)
                .build();
        sendPushNotification(request);
    }

    // Fallback methods
    private NotificationResult emailNotificationFallback(EmailNotificationRequest request, Exception ex) {
        log.error("Email notification fallback triggered", ex);
        return buildFallbackResult(request, ex);
    }

    private NotificationResult smsNotificationFallback(SmsNotificationRequest request, Exception ex) {
        log.error("SMS notification fallback triggered", ex);
        return buildFallbackResult(request, ex);
    }

    private NotificationResult pushNotificationFallback(PushNotificationRequest request, Exception ex) {
        log.error("Push notification fallback triggered", ex);
        return buildFallbackResult(request, ex);
    }

    private NotificationResult inAppNotificationFallback(InAppNotificationRequest request, Exception ex) {
        log.error("In-app notification fallback triggered", ex);
        return buildFallbackResult(request, ex);
    }

    private NotificationResult webhookNotificationFallback(WebhookNotificationRequest request, Exception ex) {
        log.error("Webhook notification fallback triggered", ex);
        return buildFallbackResult(request, ex);
    }

    private NotificationResult slackNotificationFallback(SlackNotificationRequest request, Exception ex) {
        log.error("Slack notification fallback triggered for channel: {}", request.getSlackChannel(), ex);
        return buildFallbackResult(request, ex);
    }

    private NotificationResult criticalAlertFallback(CriticalAlertRequest request, Exception ex) {
        log.error("CRITICAL ALERT FALLBACK: {} - {}", request.getTitle(), request.getMessage(), ex);
        // In production, this should trigger alternative alerting mechanisms
        return buildFallbackResult(request, ex);
    }

    private NotificationResult buildFallbackResult(NotificationRequest request, Exception ex) {
        return NotificationResult.builder()
                .status(NotificationResult.DeliveryStatus.FAILED)
                .channel(request.getChannel())
                .errorDetails(NotificationResult.ErrorDetails.builder()
                        .code("SERVICE_UNAVAILABLE")
                        .message("Notification service unavailable: " + ex.getMessage())
                        .build())
                .queuedForRetry(true)
                .build();
    }
}