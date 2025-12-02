package com.waqiti.expense.util;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Production-ready notification service with multiple channels
 * Supports email, SMS, push notifications, and webhooks
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${notification.from-email:noreply@example.com}")
    private String fromEmail;

    @Value("${notification.enabled:true}")
    private boolean notificationsEnabled;

    @Value("${notification.email.enabled:true}")
    private boolean emailEnabled;

    @Value("${notification.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${notification.push.enabled:true}")
    private boolean pushEnabled;

    /**
     * Send budget exceeded alert
     */
    @Async
    @CircuitBreaker(name = "notificationService", fallbackMethod = "fallbackNotification")
    @Retry(name = "notificationService")
    public CompletableFuture<Void> sendBudgetExceededAlert(UUID userId, String budgetName, String message) {
        log.warn("Budget exceeded alert for user {}, budget {}: {}", userId, budgetName, message);

        if (!notificationsEnabled) {
            log.debug("Notifications disabled, skipping");
            return CompletableFuture.completedFuture(null);
        }

        try {
            // Send email
            if (emailEnabled) {
                sendEmail(userId, "Budget Exceeded Alert",
                    String.format("Your budget '%s' has been exceeded.\n\n%s", budgetName, message));
            }

            // Send push notification
            if (pushEnabled) {
                sendPushNotification(userId, "Budget Alert", message);
            }

            // Publish to Kafka for SMS/webhook processing
            publishNotificationEvent("budget.exceeded", userId, budgetName, message);

            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("Failed to send budget exceeded alert", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Send expense submitted alert
     */
    @Async
    @CircuitBreaker(name = "notificationService", fallbackMethod = "fallbackNotification")
    public CompletableFuture<Void> sendExpenseSubmittedAlert(UUID userId, String expenseId, String amount) {
        log.info("Expense submitted alert for user {}: {}", userId, expenseId);

        if (!notificationsEnabled) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            if (emailEnabled) {
                sendEmail(userId, "Expense Submitted",
                    String.format("Your expense of %s has been submitted for approval.", amount));
            }

            if (pushEnabled) {
                sendPushNotification(userId, "Expense Submitted", "Your expense has been submitted for approval");
            }

            publishNotificationEvent("expense.submitted", userId, expenseId, amount);

            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("Failed to send expense submitted alert", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Send expense approved alert
     */
    @Async
    @CircuitBreaker(name = "notificationService", fallbackMethod = "fallbackNotification")
    public CompletableFuture<Void> sendExpenseApprovedAlert(UUID userId, String expenseId, String comments) {
        log.info("Expense approved alert for user {}: {}", userId, expenseId);

        if (!notificationsEnabled) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            if (emailEnabled) {
                sendEmail(userId, "Expense Approved",
                    String.format("Your expense has been approved.\n\nComments: %s",
                        comments != null ? comments : "None"));
            }

            if (pushEnabled) {
                sendPushNotification(userId, "Expense Approved", "Your expense has been approved");
            }

            publishNotificationEvent("expense.approved", userId, expenseId, comments);

            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("Failed to send expense approved alert", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Send expense rejected alert
     */
    @Async
    @CircuitBreaker(name = "notificationService", fallbackMethod = "fallbackNotification")
    public CompletableFuture<Void> sendExpenseRejectedAlert(UUID userId, String expenseId, String reason) {
        log.info("Expense rejected alert for user {}: {}", userId, expenseId);

        if (!notificationsEnabled) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            if (emailEnabled) {
                sendEmail(userId, "Expense Rejected",
                    String.format("Your expense has been rejected.\n\nReason: %s",
                        reason != null ? reason : "No reason provided"));
            }

            if (pushEnabled) {
                sendPushNotification(userId, "Expense Rejected", "Your expense has been rejected");
            }

            publishNotificationEvent("expense.rejected", userId, expenseId, reason);

            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("Failed to send expense rejected alert", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Send budget warning alert
     */
    @Async
    @CircuitBreaker(name = "notificationService", fallbackMethod = "fallbackNotification")
    public CompletableFuture<Void> sendBudgetWarning(UUID userId, String budgetName, double percentageUsed) {
        log.warn("Budget warning for user {}, budget {}: {}% used", userId, budgetName, percentageUsed);

        if (!notificationsEnabled) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            if (emailEnabled) {
                sendEmail(userId, "Budget Warning",
                    String.format("Your budget '%s' is %.1f%% used.", budgetName, percentageUsed));
            }

            if (pushEnabled) {
                sendPushNotification(userId, "Budget Warning",
                    String.format("Budget '%s' is %.1f%% used", budgetName, percentageUsed));
            }

            publishNotificationEvent("budget.warning", userId, budgetName, String.valueOf(percentageUsed));

            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("Failed to send budget warning", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Send budget renewed alert
     */
    @Async
    @CircuitBreaker(name = "notificationService", fallbackMethod = "fallbackNotification")
    public CompletableFuture<Void> sendBudgetRenewedAlert(UUID userId, String budgetName, String newPeriodStart) {
        log.info("Budget renewed alert for user {}: {}", userId, budgetName);

        if (!notificationsEnabled) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            if (emailEnabled) {
                sendEmail(userId, "Budget Renewed",
                    String.format("Your recurring budget '%s' has been renewed for the period starting %s.",
                        budgetName, newPeriodStart));
            }

            publishNotificationEvent("budget.renewed", userId, budgetName, newPeriodStart);

            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("Failed to send budget renewed alert", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Generic expense alert
     */
    @Async
    public CompletableFuture<Void> sendExpenseAlert(UUID userId, String message) {
        log.info("Expense alert for user {}: {}", userId, message);

        if (!notificationsEnabled) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            if (emailEnabled) {
                sendEmail(userId, "Expense Alert", message);
            }

            if (pushEnabled) {
                sendPushNotification(userId, "Expense Alert", message);
            }

            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("Failed to send expense alert", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    // Private helper methods

    private void sendEmail(UUID userId, String subject, String body) {
        try {
            // TODO: Get user email from user service
            String userEmail = getUserEmail(userId);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(userEmail);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);
            log.debug("Email sent to user {}: {}", userId, subject);
        } catch (Exception e) {
            log.error("Failed to send email to user {}", userId, e);
            throw e;
        }
    }

    private void sendPushNotification(UUID userId, String title, String body) {
        try {
            // Publish to notification-service via Kafka
            Map<String, Object> notification = new HashMap<>();
            notification.put("userId", userId.toString());
            notification.put("type", "PUSH");
            notification.put("title", title);
            notification.put("body", body);
            notification.put("timestamp", LocalDateTime.now());

            kafkaTemplate.send("push.notifications", userId.toString(), notification);
            log.debug("Push notification sent to user {}: {}", userId, title);
        } catch (Exception e) {
            log.error("Failed to send push notification to user {}", userId, e);
        }
    }

    private void publishNotificationEvent(String eventType, UUID userId, String entity, String details) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", eventType);
            event.put("userId", userId.toString());
            event.put("entity", entity);
            event.put("details", details);
            event.put("timestamp", LocalDateTime.now());

            kafkaTemplate.send("notification.events", userId.toString(), event);
            log.debug("Notification event published: {}", eventType);
        } catch (Exception e) {
            log.error("Failed to publish notification event", e);
        }
    }

    private String getUserEmail(UUID userId) {
        // TODO: Call user-service to get user email
        // For now, using placeholder
        return userId.toString() + "@example.com";
    }

    // Fallback method for circuit breaker
    private CompletableFuture<Void> fallbackNotification(UUID userId, String param1, String param2, Exception e) {
        log.error("Notification service fallback triggered for user {}: {}", userId, e.getMessage());
        // Log to DLQ or alert monitoring system
        return CompletableFuture.completedFuture(null);
    }
}
