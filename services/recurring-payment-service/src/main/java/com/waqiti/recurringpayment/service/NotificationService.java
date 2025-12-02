package com.waqiti.recurringpayment.service;

import org.springframework.stereotype.Service;

import java.util.Map;

// Notification Service
@Service
public class NotificationService {

    public void sendRecurringPaymentCreatedNotification(String userId, Object recurring) {
        try {
            log.info("Sending recurring payment created notification to user: {}", userId);

            NotificationRequest request = NotificationRequest.builder()
                    .userId(UUID.fromString(userId))
                    .type(NotificationType.RECURRING_PAYMENT_CREATED)
                    .title("Recurring Payment Setup")
                    .message("Your recurring payment has been successfully created.")
                    .channels(Arrays.asList(NotificationChannel.PUSH, NotificationChannel.EMAIL))
                    .priority(NotificationPriority.MEDIUM)
                    .metadata(Map.of(
                            "recurring_payment_id", extractId(recurring),
                            "amount", extractAmount(recurring),
                            "frequency", extractFrequency(recurring),
                            "next_execution", extractNextExecution(recurring)
                    ))
                    .build();

            // Send notification via external service
            notificationClient.sendNotification(request);

        } catch (Exception e) {
            log.error("Failed to send recurring payment created notification for user: {}", userId, e);
        }
    }

    public void sendRecurringPaymentPausedNotification(String userId, Object recurring) {
        try {
            log.info("Sending recurring payment paused notification to user: {}", userId);

            NotificationRequest request = NotificationRequest.builder()
                    .userId(UUID.fromString(userId))
                    .type(NotificationType.RECURRING_PAYMENT_PAUSED)
                    .title("Recurring Payment Paused")
                    .message("Your recurring payment has been paused and will not execute until resumed.")
                    .channels(Arrays.asList(NotificationChannel.PUSH, NotificationChannel.EMAIL))
                    .priority(NotificationPriority.HIGH)
                    .metadata(Map.of(
                            "recurring_payment_id", extractId(recurring),
                            "amount", extractAmount(recurring),
                            "paused_at", Instant.now().toString()
                    ))
                    .build();

            notificationClient.sendNotification(request);

        } catch (Exception e) {
            log.error("Failed to send recurring payment paused notification for user: {}", userId, e);
        }
    }

    public void sendRecurringPaymentResumedNotification(String userId, Object recurring) {
        try {
            log.info("Sending recurring payment resumed notification to user: {}", userId);

            NotificationRequest request = NotificationRequest.builder()
                    .userId(UUID.fromString(userId))
                    .type(NotificationType.RECURRING_PAYMENT_RESUMED)
                    .title("Recurring Payment Resumed")
                    .message("Your recurring payment has been resumed and will execute according to schedule.")
                    .channels(Arrays.asList(NotificationChannel.PUSH, NotificationChannel.EMAIL))
                    .priority(NotificationPriority.MEDIUM)
                    .metadata(Map.of(
                            "recurring_payment_id", extractId(recurring),
                            "amount", extractAmount(recurring),
                            "next_execution", extractNextExecution(recurring),
                            "resumed_at", Instant.now().toString()
                    ))
                    .build();

            notificationClient.sendNotification(request);

        } catch (Exception e) {
            log.error("Failed to send recurring payment resumed notification for user: {}", userId, e);
        }
    }

    public void sendRecurringPaymentCancelledNotification(String userId, Object recurring) {
        try {
            log.info("Sending recurring payment cancelled notification to user: {}", userId);

            NotificationRequest request = NotificationRequest.builder()
                    .userId(UUID.fromString(userId))
                    .type(NotificationType.RECURRING_PAYMENT_CANCELLED)
                    .title("Recurring Payment Cancelled")
                    .message("Your recurring payment has been permanently cancelled.")
                    .channels(Arrays.asList(NotificationChannel.PUSH, NotificationChannel.EMAIL))
                    .priority(NotificationPriority.HIGH)
                    .metadata(Map.of(
                            "recurring_payment_id", extractId(recurring),
                            "amount", extractAmount(recurring),
                            "cancelled_at", Instant.now().toString(),
                            "reason", "User requested cancellation"
                    ))
                    .build();

            notificationClient.sendNotification(request);

        } catch (Exception e) {
            log.error("Failed to send recurring payment cancelled notification for user: {}", userId, e);
        }
    }

    public void sendRecurringPaymentSuccessNotification(String userId, Object recurring, Object execution) {
        try {
            log.info("Sending recurring payment success notification to user: {}", userId);

            NotificationRequest request = NotificationRequest.builder()
                    .userId(UUID.fromString(userId))
                    .type(NotificationType.RECURRING_PAYMENT_SUCCESS)
                    .title("Recurring Payment Successful")
                    .message(String.format("Your recurring payment of %s was processed successfully.", extractAmount(recurring)))
                    .channels(Arrays.asList(NotificationChannel.PUSH, NotificationChannel.SMS))
                    .priority(NotificationPriority.MEDIUM)
                    .metadata(Map.of(
                            "recurring_payment_id", extractId(recurring),
                            "execution_id", extractExecutionId(execution),
                            "amount", extractAmount(recurring),
                            "executed_at", extractExecutedAt(execution),
                            "next_execution", extractNextExecution(recurring),
                            "transaction_id", extractTransactionId(execution)
                    ))
                    .build();

            notificationClient.sendNotification(request);

        } catch (Exception e) {
            log.error("Failed to send recurring payment success notification for user: {}", userId, e);
        }
    }

    public void sendRecurringPaymentFailedNotification(String userId, Object recurring, Object execution, String message) {
        try {
            log.info("Sending recurring payment failed notification to user: {}", userId);

            NotificationRequest request = NotificationRequest.builder()
                    .userId(UUID.fromString(userId))
                    .type(NotificationType.RECURRING_PAYMENT_FAILED)
                    .title("Recurring Payment Failed")
                    .message(String.format("Your recurring payment of %s failed: %s", extractAmount(recurring), message))
                    .channels(Arrays.asList(NotificationChannel.PUSH, NotificationChannel.EMAIL, NotificationChannel.SMS))
                    .priority(NotificationPriority.HIGH)
                    .metadata(Map.of(
                            "recurring_payment_id", extractId(recurring),
                            "execution_id", extractExecutionId(execution),
                            "amount", extractAmount(recurring),
                            "failed_at", extractFailedAt(execution),
                            "failure_reason", message,
                            "retry_count", extractRetryCount(execution),
                            "next_retry", extractNextRetry(execution),
                            "action_required", "Please check your payment method or account balance"
                    ))
                    .build();

            notificationClient.sendNotification(request);

            // Also send email with detailed failure information
            sendDetailedFailureEmail(userId, recurring, execution, message);

        } catch (Exception e) {
            log.error("Failed to send recurring payment failed notification for user: {}", userId, e);
        }
    }

    // Helper methods for extracting data from objects

    private String extractId(Object obj) {
        if (obj == null) return "unknown";
        try {
            // Use reflection to get id field
            var field = obj.getClass().getDeclaredField("id");
            field.setAccessible(true);
            Object id = field.get(obj);
            return id != null ? id.toString() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String extractAmount(Object obj) {
        if (obj == null) return "0.00";
        try {
            var field = obj.getClass().getDeclaredField("amount");
            field.setAccessible(true);
            Object amount = field.get(obj);
            return amount != null ? amount.toString() : "0.00";
        } catch (Exception e) {
            return "0.00";
        }
    }

    private String extractFrequency(Object obj) {
        if (obj == null) return "unknown";
        try {
            var field = obj.getClass().getDeclaredField("frequency");
            field.setAccessible(true);
            Object frequency = field.get(obj);
            return frequency != null ? frequency.toString() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String extractNextExecution(Object obj) {
        if (obj == null) return Instant.now().toString();
        try {
            var field = obj.getClass().getDeclaredField("nextExecutionDate");
            field.setAccessible(true);
            Object nextExecution = field.get(obj);
            return nextExecution != null ? nextExecution.toString() : Instant.now().toString();
        } catch (Exception e) {
            return Instant.now().toString();
        }
    }

    private String extractExecutionId(Object obj) {
        if (obj == null) return "unknown";
        try {
            var field = obj.getClass().getDeclaredField("executionId");
            field.setAccessible(true);
            Object executionId = field.get(obj);
            return executionId != null ? executionId.toString() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String extractExecutedAt(Object obj) {
        if (obj == null) return Instant.now().toString();
        try {
            var field = obj.getClass().getDeclaredField("executedAt");
            field.setAccessible(true);
            Object executedAt = field.get(obj);
            return executedAt != null ? executedAt.toString() : Instant.now().toString();
        } catch (Exception e) {
            return Instant.now().toString();
        }
    }

    private String extractTransactionId(Object obj) {
        if (obj == null) return "unknown";
        try {
            var field = obj.getClass().getDeclaredField("transactionId");
            field.setAccessible(true);
            Object transactionId = field.get(obj);
            return transactionId != null ? transactionId.toString() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String extractFailedAt(Object obj) {
        if (obj == null) return Instant.now().toString();
        try {
            var field = obj.getClass().getDeclaredField("failedAt");
            field.setAccessible(true);
            Object failedAt = field.get(obj);
            return failedAt != null ? failedAt.toString() : Instant.now().toString();
        } catch (Exception e) {
            return Instant.now().toString();
        }
    }

    private String extractRetryCount(Object obj) {
        if (obj == null) return "0";
        try {
            var field = obj.getClass().getDeclaredField("retryCount");
            field.setAccessible(true);
            Object retryCount = field.get(obj);
            return retryCount != null ? retryCount.toString() : "0";
        } catch (Exception e) {
            return "0";
        }
    }

    private String extractNextRetry(Object obj) {
        if (obj == null) return "Not scheduled";
        try {
            var field = obj.getClass().getDeclaredField("nextRetryAt");
            field.setAccessible(true);
            Object nextRetry = field.get(obj);
            return nextRetry != null ? nextRetry.toString() : "Not scheduled";
        } catch (Exception e) {
            return "Not scheduled";
        }
    }

    private void sendDetailedFailureEmail(String userId, Object recurring, Object execution, String message) {
        try {
            log.info("Sending detailed failure email to user: {}", userId);

            EmailNotificationRequest emailRequest = EmailNotificationRequest.builder()
                    .userId(UUID.fromString(userId))
                    .templateId("recurring_payment_failure_detailed")
                    .subject("Action Required: Recurring Payment Failed")
                    .templateData(Map.of(
                            "user_id", userId,
                            "payment_amount", extractAmount(recurring),
                            "failure_reason", message,
                            "retry_count", extractRetryCount(execution),
                            "next_retry", extractNextRetry(execution),
                            "support_link", "https://support.example.com/recurring-payments",
                            "account_settings_link", "https://app.example.com/settings/payment-methods"
                    ))
                    .priority(NotificationPriority.HIGH)
                    .build();

            notificationClient.sendEmail(emailRequest);

        } catch (Exception e) {
            log.error("Failed to send detailed failure email for user: {}", userId, e);
        }
    }

    public void sendPaymentReminderNotification(String userId, Object recurring, int daysUntil) {
        try {
            log.info("Sending payment reminder notification to user: {} for payment in {} days", userId, daysUntil);

            String reminderMessage = daysUntil == 0
                    ? String.format("Your recurring payment of %s will be processed today.", extractAmount(recurring))
                    : String.format("Your recurring payment of %s will be processed in %d day%s.",
                    extractAmount(recurring), daysUntil, daysUntil == 1 ? "" : "s");

            NotificationRequest request = NotificationRequest.builder()
                    .userId(UUID.fromString(userId))
                    .type(NotificationType.RECURRING_PAYMENT_REMINDER)
                    .title(daysUntil == 0 ? "Payment Processing Today" : "Upcoming Payment Reminder")
                    .message(reminderMessage)
                    .channels(Arrays.asList(NotificationChannel.PUSH, NotificationChannel.EMAIL))
                    .priority(daysUntil == 0 ? NotificationPriority.HIGH : NotificationPriority.MEDIUM)
                    .metadata(Map.of(
                            "recurring_payment_id", extractId(recurring),
                            "amount", extractAmount(recurring),
                            "days_until_payment", String.valueOf(daysUntil),
                            "next_execution", extractNextExecution(recurring),
                            "reminder_type", daysUntil == 0 ? "TODAY" : "UPCOMING",
                            "account_balance_check", "Please ensure sufficient funds are available"
                    ))
                    .build();

            notificationClient.sendNotification(request);

        } catch (Exception e) {
            log.error("Failed to send payment reminder notification for user: {}", userId, e);
        }
    }

    public void sendFraudAlertNotification(String userId, Object recurring, Object execution, Object fraudResult) {
        try {
            log.warn("Sending fraud alert notification to user: {} for recurring payment", userId);

            NotificationRequest request = NotificationRequest.builder()
                    .userId(UUID.fromString(userId))
                    .type(NotificationType.SECURITY_ALERT)
                    .title("🚨 Security Alert: Payment Flagged")
                    .message("A recurring payment was flagged for potential fraud. Please verify your account immediately.")
                    .channels(Arrays.asList(NotificationChannel.PUSH, NotificationChannel.EMAIL, NotificationChannel.SMS))
                    .priority(NotificationPriority.CRITICAL)
                    .metadata(Map.of(
                            "recurring_payment_id", extractId(recurring),
                            "execution_id", extractExecutionId(execution),
                            "amount", extractAmount(recurring),
                            "fraud_score", extractFraudScore(fraudResult),
                            "flagged_at", Instant.now().toString(),
                            "alert_type", "RECURRING_PAYMENT_FRAUD",
                            "action_required", "IMMEDIATE_VERIFICATION",
                            "security_link", "https://app.example.com/security/alerts",
                            "support_phone", "+1-800-WAQITI1"
                    ))
                    .build();

            notificationClient.sendNotification(request);

            // Also trigger security workflow
            triggerSecurityWorkflow(userId, recurring, execution, fraudResult);

        } catch (Exception e) {
            log.error("Failed to send fraud alert notification for user: {}", userId, e);
        }
    }

    private String extractFraudScore(Object obj) {
        if (obj == null) return "0.0";
        try {
            var field = obj.getClass().getDeclaredField("fraudScore");
            field.setAccessible(true);
            Object fraudScore = field.get(obj);
            return fraudScore != null ? fraudScore.toString() : "0.0";
        } catch (Exception e) {
            return "0.0";
        }
    }

    private void triggerSecurityWorkflow(String userId, Object recurring, Object execution, Object fraudResult) {
        try {
            log.info("Triggering security workflow for user: {} due to fraud alert", userId);

            SecurityAlertRequest securityRequest = SecurityAlertRequest.builder()
                    .userId(UUID.fromString(userId))
                    .alertType("RECURRING_PAYMENT_FRAUD")
                    .severity("HIGH")
                    .details(Map.of(
                            "recurring_payment_id", extractId(recurring),
                            "execution_id", extractExecutionId(execution),
                            "fraud_score", extractFraudScore(fraudResult),
                            "payment_amount", extractAmount(recurring)
                    ))
                    .autoActions(Arrays.asList("SUSPEND_PAYMENT", "REQUIRE_VERIFICATION"))
                    .build();

            securityServiceClient.createSecurityAlert(securityRequest);

        } catch (Exception e) {
            log.error("Failed to trigger security workflow for user: {}", userId, e);
        }
    }
}

