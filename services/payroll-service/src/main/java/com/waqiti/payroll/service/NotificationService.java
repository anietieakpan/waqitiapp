package com.waqiti.payroll.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Notification Service for Payroll Events
 * Sends notifications via Email, SMS, and Push Notifications
 * Integrates with notification-service via Kafka events
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${payroll.notifications.enable-email:true}")
    private boolean enableEmail;

    @Value("${payroll.notifications.enable-sms:false}")
    private boolean enableSMS;

    @Value("${payroll.notifications.enable-push:true}")
    private boolean enablePush;

    private static final String NOTIFICATION_TOPIC = "notification-events";
    private static final String ALERT_TOPIC = "alert-events";

    /**
     * Send payroll completion notification to company
     */
    public void sendPayrollCompletionNotification(String companyId, String batchId,
                                                   int successful, int failed, BigDecimal amount) {
        log.info("Sending payroll completion notification - Company: {}, Batch: {}, Success: {}, Failed: {}, Amount: ${}",
                 companyId, batchId, successful, failed, amount);

        NotificationEvent event = NotificationEvent.builder()
            .notificationType(NotificationType.PAYROLL_COMPLETION)
            .recipient(companyId)
            .recipientType(RecipientType.COMPANY)
            .priority(NotificationPriority.HIGH)
            .subject("Payroll Processing Complete - Batch " + batchId)
            .message(buildPayrollCompletionMessage(batchId, successful, failed, amount))
            .metadata(buildPayrollCompletionMetadata(companyId, batchId, successful, failed, amount))
            .channels(getEnabledChannels())
            .timestamp(LocalDateTime.now())
            .build();

        sendNotification(event);
    }

    /**
     * Send payroll failure alert to company
     */
    public void sendPayrollFailureAlert(String companyId, List<String> failedEmployeeIds, String correlationId) {
        log.warn("Sending payroll failure alert - Company: {}, Failed employees: {}, Correlation: {}",
                 companyId, failedEmployeeIds.size(), correlationId);

        NotificationEvent event = NotificationEvent.builder()
            .notificationType(NotificationType.PAYROLL_FAILURE)
            .recipient(companyId)
            .recipientType(RecipientType.COMPANY)
            .priority(NotificationPriority.URGENT)
            .subject("⚠️ Payroll Processing Failed")
            .message(buildPayrollFailureMessage(failedEmployeeIds.size(), correlationId))
            .metadata(buildPayrollFailureMetadata(companyId, failedEmployeeIds, correlationId))
            .channels(getEnabledChannels())
            .timestamp(LocalDateTime.now())
            .build();

        sendNotification(event);
    }

    /**
     * Send critical system alert to operations team
     */
    public void sendCriticalAlert(String alertType, String message, String correlationId) {
        log.error("CRITICAL ALERT - Type: {}, Message: {}, Correlation: {}", alertType, message, correlationId);

        AlertEvent alert = AlertEvent.builder()
            .alertType(alertType)
            .severity(AlertSeverity.CRITICAL)
            .message(message)
            .correlationId(correlationId)
            .service("payroll-service")
            .timestamp(LocalDateTime.now())
            .metadata(Map.of("correlation_id", correlationId))
            .build();

        sendAlert(alert);
    }

    /**
     * Send employee payment notification
     */
    public void sendEmployeePaymentNotification(String employeeId, String employeeName,
                                                BigDecimal netAmount, LocalDate payDate) {
        log.info("Sending payment notification to employee: {}, amount: ${}", employeeId, netAmount);

        NotificationEvent event = NotificationEvent.builder()
            .notificationType(NotificationType.PAYMENT_PROCESSED)
            .recipient(employeeId)
            .recipientType(RecipientType.EMPLOYEE)
            .priority(NotificationPriority.NORMAL)
            .subject("Your Paycheck is Ready")
            .message(buildEmployeePaymentMessage(employeeName, netAmount, payDate))
            .metadata(Map.of(
                "employee_id", employeeId,
                "net_amount", netAmount.toString(),
                "pay_date", payDate.toString()
            ))
            .channels(getEnabledChannels())
            .timestamp(LocalDateTime.now())
            .build();

        sendNotification(event);
    }

    /**
     * Send compliance violation alert
     */
    public void sendComplianceViolationAlert(String companyId, String violationType,
                                            String violationMessage, String regulation) {
        log.warn("Compliance violation alert - Company: {}, Type: {}, Regulation: {}",
                 companyId, violationType, regulation);

        NotificationEvent event = NotificationEvent.builder()
            .notificationType(NotificationType.COMPLIANCE_VIOLATION)
            .recipient(companyId)
            .recipientType(RecipientType.COMPANY)
            .priority(NotificationPriority.URGENT)
            .subject("⚠️ Compliance Violation Detected - " + violationType)
            .message(buildComplianceViolationMessage(violationType, violationMessage, regulation))
            .metadata(Map.of(
                "company_id", companyId,
                "violation_type", violationType,
                "regulation", regulation
            ))
            .channels(List.of(NotificationChannel.EMAIL, NotificationChannel.PUSH))
            .timestamp(LocalDateTime.now())
            .build();

        sendNotification(event);
    }

    /**
     * Send insufficient funds alert
     */
    public void sendInsufficientFundsAlert(String companyId, BigDecimal required,
                                          BigDecimal available, BigDecimal shortfall) {
        log.error("Insufficient funds alert - Company: {}, Required: ${}, Available: ${}, Shortfall: ${}",
                  companyId, required, available, shortfall);

        NotificationEvent event = NotificationEvent.builder()
            .notificationType(NotificationType.INSUFFICIENT_FUNDS)
            .recipient(companyId)
            .recipientType(RecipientType.COMPANY)
            .priority(NotificationPriority.URGENT)
            .subject("🚨 Insufficient Funds for Payroll")
            .message(buildInsufficientFundsMessage(required, available, shortfall))
            .metadata(Map.of(
                "company_id", companyId,
                "required_amount", required.toString(),
                "available_amount", available.toString(),
                "shortfall_amount", shortfall.toString()
            ))
            .channels(List.of(NotificationChannel.EMAIL, NotificationChannel.SMS, NotificationChannel.PUSH))
            .timestamp(LocalDateTime.now())
            .build();

        sendNotification(event);
    }

    /**
     * Send tax filing reminder
     */
    public void sendTaxFilingReminder(String companyId, String formType, LocalDate dueDate) {
        log.info("Sending tax filing reminder - Company: {}, Form: {}, Due: {}",
                 companyId, formType, dueDate);

        NotificationEvent event = NotificationEvent.builder()
            .notificationType(NotificationType.TAX_FILING_REMINDER)
            .recipient(companyId)
            .recipientType(RecipientType.COMPANY)
            .priority(NotificationPriority.NORMAL)
            .subject("Tax Filing Reminder - " + formType + " Due " + dueDate)
            .message(buildTaxFilingReminderMessage(formType, dueDate))
            .metadata(Map.of(
                "company_id", companyId,
                "form_type", formType,
                "due_date", dueDate.toString()
            ))
            .channels(List.of(NotificationChannel.EMAIL))
            .timestamp(LocalDateTime.now())
            .build();

        sendNotification(event);
    }

    /**
     * Send batch transfer completion notification
     */
    public void sendBatchTransferCompletionNotification(String companyId, String batchId,
                                                       int successCount, int failureCount,
                                                       BigDecimal totalAmount) {
        log.info("Sending batch transfer completion - Company: {}, Batch: {}, Success: {}, Failed: {}",
                 companyId, batchId, successCount, failureCount);

        NotificationEvent event = NotificationEvent.builder()
            .notificationType(NotificationType.TRANSFER_COMPLETION)
            .recipient(companyId)
            .recipientType(RecipientType.COMPANY)
            .priority(failureCount > 0 ? NotificationPriority.HIGH : NotificationPriority.NORMAL)
            .subject("Bank Transfers Complete - Batch " + batchId)
            .message(buildTransferCompletionMessage(batchId, successCount, failureCount, totalAmount))
            .metadata(Map.of(
                "company_id", companyId,
                "batch_id", batchId,
                "success_count", String.valueOf(successCount),
                "failure_count", String.valueOf(failureCount),
                "total_amount", totalAmount.toString()
            ))
            .channels(getEnabledChannels())
            .timestamp(LocalDateTime.now())
            .build();

        sendNotification(event);
    }

    // ============= Message Builders =============

    private String buildPayrollCompletionMessage(String batchId, int successful, int failed, BigDecimal amount) {
        StringBuilder sb = new StringBuilder();
        sb.append("Payroll batch ").append(batchId).append(" has been processed.\n\n");
        sb.append("✅ Successful payments: ").append(successful).append("\n");
        if (failed > 0) {
            sb.append("❌ Failed payments: ").append(failed).append("\n");
        }
        sb.append("💰 Total amount: $").append(amount).append("\n\n");
        if (failed > 0) {
            sb.append("⚠️ Please review failed payments and take appropriate action.");
        } else {
            sb.append("All payments processed successfully!");
        }
        return sb.toString();
    }

    private String buildPayrollFailureMessage(int failedCount, String correlationId) {
        return String.format(
            "Payroll processing failed for %d employee(s).\n\n" +
            "Correlation ID: %s\n\n" +
            "Please review the error details and retry the batch or contact support.",
            failedCount, correlationId
        );
    }

    private String buildEmployeePaymentMessage(String employeeName, BigDecimal netAmount, LocalDate payDate) {
        return String.format(
            "Hi %s,\n\n" +
            "Your paycheck of $%s has been processed and will be deposited on %s.\n\n" +
            "Thank you!",
            employeeName, netAmount, payDate
        );
    }

    private String buildComplianceViolationMessage(String violationType, String violationMessage, String regulation) {
        return String.format(
            "A compliance violation has been detected:\n\n" +
            "Type: %s\n" +
            "Details: %s\n" +
            "Regulation: %s\n\n" +
            "Please address this issue immediately to ensure regulatory compliance.",
            violationType, violationMessage, regulation
        );
    }

    private String buildInsufficientFundsMessage(BigDecimal required, BigDecimal available, BigDecimal shortfall) {
        return String.format(
            "🚨 URGENT: Insufficient funds to process payroll\n\n" +
            "Required: $%s\n" +
            "Available: $%s\n" +
            "Shortfall: $%s\n\n" +
            "Please add funds to your account immediately to complete payroll processing.",
            required, available, shortfall
        );
    }

    private String buildTaxFilingReminderMessage(String formType, LocalDate dueDate) {
        return String.format(
            "Reminder: Tax filing due soon\n\n" +
            "Form: %s\n" +
            "Due Date: %s\n\n" +
            "Please ensure all required tax forms are filed on time to avoid penalties.",
            formType, dueDate
        );
    }

    private String buildTransferCompletionMessage(String batchId, int successCount, int failureCount, BigDecimal totalAmount) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bank transfers for batch ").append(batchId).append(" are complete.\n\n");
        sb.append("✅ Successful transfers: ").append(successCount).append("\n");
        if (failureCount > 0) {
            sb.append("❌ Failed transfers: ").append(failureCount).append("\n");
        }
        sb.append("💰 Total transferred: $").append(totalAmount).append("\n");
        return sb.toString();
    }

    // ============= Metadata Builders =============

    private Map<String, Object> buildPayrollCompletionMetadata(String companyId, String batchId,
                                                               int successful, int failed, BigDecimal amount) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("company_id", companyId);
        metadata.put("batch_id", batchId);
        metadata.put("successful_count", successful);
        metadata.put("failed_count", failed);
        metadata.put("total_amount", amount.toString());
        metadata.put("timestamp", LocalDateTime.now().toString());
        return metadata;
    }

    private Map<String, Object> buildPayrollFailureMetadata(String companyId, List<String> failedEmployeeIds, String correlationId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("company_id", companyId);
        metadata.put("failed_employee_count", failedEmployeeIds.size());
        metadata.put("failed_employee_ids", failedEmployeeIds);
        metadata.put("correlation_id", correlationId);
        metadata.put("timestamp", LocalDateTime.now().toString());
        return metadata;
    }

    // ============= Channel Management =============

    private List<NotificationChannel> getEnabledChannels() {
        List<NotificationChannel> channels = new java.util.ArrayList<>();
        if (enableEmail) {
            channels.add(NotificationChannel.EMAIL);
        }
        if (enableSMS) {
            channels.add(NotificationChannel.SMS);
        }
        if (enablePush) {
            channels.add(NotificationChannel.PUSH);
        }
        return channels;
    }

    // ============= Kafka Integration =============

    private void sendNotification(NotificationEvent event) {
        try {
            kafkaTemplate.send(NOTIFICATION_TOPIC, event.getRecipient(), event);
            log.info("Notification sent - Type: {}, Recipient: {}, Priority: {}",
                     event.getNotificationType(), event.getRecipient(), event.getPriority());
        } catch (Exception e) {
            log.error("Failed to send notification: {}", e.getMessage(), e);
        }
    }

    private void sendAlert(AlertEvent alert) {
        try {
            kafkaTemplate.send(ALERT_TOPIC, alert.getService(), alert);
            log.error("Alert sent - Type: {}, Severity: {}, Service: {}",
                      alert.getAlertType(), alert.getSeverity(), alert.getService());
        } catch (Exception e) {
            log.error("Failed to send alert: {}", e.getMessage(), e);
        }
    }

    // ============= DTOs =============

    @lombok.Builder
    @lombok.Data
    public static class NotificationEvent {
        private NotificationType notificationType;
        private String recipient;
        private RecipientType recipientType;
        private NotificationPriority priority;
        private String subject;
        private String message;
        private Map<String, Object> metadata;
        private List<NotificationChannel> channels;
        private LocalDateTime timestamp;
    }

    @lombok.Builder
    @lombok.Data
    public static class AlertEvent {
        private String alertType;
        private AlertSeverity severity;
        private String message;
        private String correlationId;
        private String service;
        private LocalDateTime timestamp;
        private Map<String, Object> metadata;
    }

    public enum NotificationType {
        PAYROLL_COMPLETION,
        PAYROLL_FAILURE,
        PAYMENT_PROCESSED,
        COMPLIANCE_VIOLATION,
        INSUFFICIENT_FUNDS,
        TAX_FILING_REMINDER,
        TRANSFER_COMPLETION
    }

    public enum RecipientType {
        EMPLOYEE,
        COMPANY,
        ADMIN,
        OPS_TEAM
    }

    public enum NotificationPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }

    public enum NotificationChannel {
        EMAIL,
        SMS,
        PUSH,
        WEBHOOK
    }

    public enum AlertSeverity {
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }
}
