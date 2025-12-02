package com.waqiti.notification.events.consumers;

import com.waqiti.notification.domain.*;
import com.waqiti.notification.repository.*;
import com.waqiti.notification.service.*;
import com.waqiti.common.events.billpayment.BillPaymentStatusEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillPaymentStatusEventsConsumer {

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateService notificationTemplateService;
    private final EmailService emailService;
    private final SmsService smsService;
    private final PushNotificationService pushNotificationService;
    private final InAppNotificationService inAppNotificationService;
    private final UserPreferenceService userPreferenceService;
    private final EventProcessingTrackingService eventProcessingTrackingService;
    private final MeterRegistry meterRegistry;

    private Counter eventsProcessedCounter;
    private Counter eventsFailedCounter;
    private Counter notificationsSentCounter;
    private Counter billPaymentStatusCounter;
    private Timer eventProcessingTimer;

    public BillPaymentStatusEventsConsumer(
            NotificationRepository notificationRepository,
            NotificationTemplateService notificationTemplateService,
            EmailService emailService,
            SmsService smsService,
            PushNotificationService pushNotificationService,
            InAppNotificationService inAppNotificationService,
            UserPreferenceService userPreferenceService,
            EventProcessingTrackingService eventProcessingTrackingService,
            MeterRegistry meterRegistry) {
        
        this.notificationRepository = notificationRepository;
        this.notificationTemplateService = notificationTemplateService;
        this.emailService = emailService;
        this.smsService = smsService;
        this.pushNotificationService = pushNotificationService;
        this.inAppNotificationService = inAppNotificationService;
        this.userPreferenceService = userPreferenceService;
        this.eventProcessingTrackingService = eventProcessingTrackingService;
        this.meterRegistry = meterRegistry;

        initializeMetrics();
    }

    private void initializeMetrics() {
        this.eventsProcessedCounter = Counter.builder("bill_payment_status_events_processed_total")
                .description("Total number of bill payment status events processed")
                .tag("consumer", "bill-payment-status-consumer")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("bill_payment_status_events_failed_total")
                .description("Total number of bill payment status events failed")
                .tag("consumer", "bill-payment-status-consumer")
                .register(meterRegistry);

        this.notificationsSentCounter = Counter.builder("bill_payment_status_notifications_sent_total")
                .description("Total notifications sent for bill payment status changes")
                .tag("consumer", "bill-payment-status-consumer")
                .register(meterRegistry);

        this.billPaymentStatusCounter = Counter.builder("bill_payment_statuses_total")
                .description("Total bill payment status events by status type")
                .tag("consumer", "bill-payment-status-consumer")
                .register(meterRegistry);

        this.eventProcessingTimer = Timer.builder("bill_payment_status_event_processing_duration")
                .description("Time taken to process bill payment status events")
                .tag("consumer", "bill-payment-status-consumer")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "${kafka.topics.bill-payment-status-events:bill-payment-status-events}",
            groupId = "${kafka.consumer.group-id:notification-bill-payment-status-consumer-group}",
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${kafka.consumer.concurrency:5}"
    )
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
            include = {ServiceIntegrationException.class, Exception.class},
            dltTopicSuffix = "-dlt",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            autoCreateTopics = "true"
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED, timeout = 30)
    public void handleBillPaymentStatusEvent(
            @Payload BillPaymentStatusEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = event.getCorrelationId();

        try {
            log.info("Processing bill payment status event: eventId={}, billPaymentId={}, userId={}, " +
                    "status={}, billerName={}, topic={}, partition={}, offset={}, correlationId={}",
                    event.getEventId(), event.getBillPaymentId(), event.getUserId(), event.getStatus(),
                    event.getBillerName(), topic, partition, offset, correlationId);

            if (eventProcessingTrackingService.isEventAlreadyProcessed(event.getEventId(), "BILL_PAYMENT_STATUS")) {
                log.warn("Duplicate bill payment status event detected: eventId={}, correlationId={}. Skipping processing.",
                        event.getEventId(), correlationId);
                acknowledgment.acknowledge();
                return;
            }

            validateEvent(event);

            processStatusChange(event, correlationId);

            sendStatusNotification(event, correlationId);

            if ("FAILED".equals(event.getStatus()) || "REJECTED".equals(event.getStatus())) {
                sendFailureNotification(event, correlationId);
            }

            if ("SCHEDULED".equals(event.getStatus()) && event.getScheduledDate() != null) {
                scheduleReminderNotification(event, correlationId);
            }

            if ("COMPLETED".equals(event.getStatus()) && event.getIsRecurring() != null && event.getIsRecurring()) {
                notifyAboutNextScheduledPayment(event, correlationId);
            }

            eventProcessingTrackingService.markEventAsProcessed(
                    event.getEventId(),
                    "BILL_PAYMENT_STATUS",
                    "notification-service",
                    correlationId
            );

            eventsProcessedCounter.increment();
            Counter.builder("bill_payment_status_types")
                    .tag("status", event.getStatus())
                    .register(meterRegistry)
                    .increment();

            acknowledgment.acknowledge();

            log.info("Successfully processed bill payment status event: eventId={}, billPaymentId={}, correlationId={}",
                    event.getEventId(), event.getBillPaymentId(), correlationId);

        } catch (Exception e) {
            eventsFailedCounter.increment();
            log.error("Failed to process bill payment status event: eventId={}, billPaymentId={}, correlationId={}, error={}",
                    event.getEventId(), event.getBillPaymentId(), correlationId, e.getMessage(), e);
            throw new RuntimeException("Bill payment status event processing failed", e);
        } finally {
            sample.stop(eventProcessingTimer);
        }
    }

    private void validateEvent(BillPaymentStatusEvent event) {
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID is required");
        }

        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }

        if (event.getBillPaymentId() == null || event.getBillPaymentId().trim().isEmpty()) {
            throw new IllegalArgumentException("Bill payment ID is required");
        }

        if (event.getStatus() == null || event.getStatus().trim().isEmpty()) {
            throw new IllegalArgumentException("Status is required");
        }

        if (event.getAmount() == null || event.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    @CircuitBreaker(name = "notificationService", fallbackMethod = "processStatusChangeFallback")
    @Retry(name = "notificationService")
    @TimeLimiter(name = "notificationService")
    private void processStatusChange(BillPaymentStatusEvent event, String correlationId) {
        log.debug("Processing bill payment status change: billPaymentId={}, status={}, correlationId={}",
                event.getBillPaymentId(), event.getStatus(), correlationId);

        Notification statusNotification = Notification.builder()
                .userId(event.getUserId())
                .notificationType("BILL_PAYMENT_STATUS_" + event.getStatus())
                .title(buildStatusTitle(event))
                .message(buildStatusMessage(event))
                .priority(determinePriority(event))
                .channel("IN_APP")
                .status("DELIVERED")
                .metadata(buildStatusMetadata(event))
                .createdAt(LocalDateTime.now())
                .deliveredAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

        notificationRepository.save(statusNotification);

        billPaymentStatusCounter.increment();

        log.debug("Bill payment status change recorded: billPaymentId={}, status={}, notificationId={}, correlationId={}",
                event.getBillPaymentId(), event.getStatus(), statusNotification.getId(), correlationId);
    }

    private void processStatusChangeFallback(BillPaymentStatusEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for status change processing: billPaymentId={}, correlationId={}, error={}",
                event.getBillPaymentId(), correlationId, e.getMessage());
    }

    @CircuitBreaker(name = "notificationService", fallbackMethod = "sendStatusNotificationFallback")
    @Retry(name = "notificationService")
    @TimeLimiter(name = "notificationService")
    private void sendStatusNotification(BillPaymentStatusEvent event, String correlationId) {
        log.debug("Sending bill payment status notification: userId={}, billPaymentId={}, status={}, correlationId={}",
                event.getUserId(), event.getBillPaymentId(), event.getStatus(), correlationId);

        UserNotificationPreferences prefs = userPreferenceService.getUserPreferences(event.getUserId());

        if (prefs == null || !prefs.isBillPaymentNotificationsEnabled()) {
            log.debug("Bill payment notifications disabled for user: userId={}, correlationId={}",
                    event.getUserId(), correlationId);
            return;
        }

        Map<String, Object> templateData = buildTemplateData(event);

        if (prefs.isEmailEnabled() && shouldSendEmailForStatus(event.getStatus())) {
            sendEmail(event, templateData, correlationId);
        }

        if (prefs.isSmsEnabled() && shouldSendSmsForStatus(event.getStatus())) {
            sendSms(event, templateData, correlationId);
        }

        if (prefs.isPushEnabled()) {
            sendPushNotification(event, templateData, correlationId);
        }

        sendInAppNotification(event, templateData, correlationId);

        notificationsSentCounter.increment();

        log.debug("Bill payment status notification sent: userId={}, billPaymentId={}, status={}, correlationId={}",
                event.getUserId(), event.getBillPaymentId(), event.getStatus(), correlationId);
    }

    private void sendStatusNotificationFallback(BillPaymentStatusEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for status notification: billPaymentId={}, correlationId={}, error={}",
                event.getBillPaymentId(), correlationId, e.getMessage());
    }

    @CircuitBreaker(name = "notificationService", fallbackMethod = "sendFailureNotificationFallback")
    @Retry(name = "notificationService")
    @TimeLimiter(name = "notificationService")
    private void sendFailureNotification(BillPaymentStatusEvent event, String correlationId) {
        log.debug("Sending bill payment failure notification: userId={}, billPaymentId={}, correlationId={}",
                event.getUserId(), event.getBillPaymentId(), correlationId);

        UserNotificationPreferences prefs = userPreferenceService.getUserPreferences(event.getUserId());

        if (prefs == null || !prefs.isBillPaymentNotificationsEnabled()) {
            return;
        }

        Map<String, Object> failureData = buildFailureTemplateData(event);

        if (prefs.isEmailEnabled()) {
            sendFailureEmail(event, failureData, correlationId);
        }

        if (prefs.isPushEnabled()) {
            sendFailurePushNotification(event, failureData, correlationId);
        }

        sendFailureInAppNotification(event, failureData, correlationId);

        notificationsSentCounter.increment();

        log.debug("Bill payment failure notification sent: userId={}, billPaymentId={}, correlationId={}",
                event.getUserId(), event.getBillPaymentId(), correlationId);
    }

    private void sendFailureNotificationFallback(BillPaymentStatusEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for failure notification: billPaymentId={}, correlationId={}, error={}",
                event.getBillPaymentId(), correlationId, e.getMessage());
    }

    @CircuitBreaker(name = "notificationService", fallbackMethod = "scheduleReminderNotificationFallback")
    @Retry(name = "notificationService")
    @TimeLimiter(name = "notificationService")
    private void scheduleReminderNotification(BillPaymentStatusEvent event, String correlationId) {
        log.debug("Scheduling bill payment reminder: userId={}, billPaymentId={}, scheduledDate={}, correlationId={}",
                event.getUserId(), event.getBillPaymentId(), event.getScheduledDate(), correlationId);

        LocalDate reminderDate = event.getScheduledDate().minusDays(1);

        if (reminderDate.isAfter(LocalDate.now())) {
            Map<String, Object> reminderData = buildReminderTemplateData(event);

            Notification reminder = Notification.builder()
                    .userId(event.getUserId())
                    .notificationType("BILL_PAYMENT_REMINDER")
                    .title("Upcoming Bill Payment Reminder")
                    .message(buildReminderMessage(event))
                    .priority("MEDIUM")
                    .channel("IN_APP")
                    .status("SCHEDULED")
                    .scheduledFor(reminderDate.atTime(9, 0))
                    .metadata(reminderData)
                    .createdAt(LocalDateTime.now())
                    .correlationId(correlationId)
                    .build();

            notificationRepository.save(reminder);

            log.debug("Bill payment reminder scheduled: userId={}, billPaymentId={}, reminderDate={}, correlationId={}",
                    event.getUserId(), event.getBillPaymentId(), reminderDate, correlationId);
        }
    }

    private void scheduleReminderNotificationFallback(BillPaymentStatusEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for reminder scheduling: billPaymentId={}, correlationId={}, error={}",
                event.getBillPaymentId(), correlationId, e.getMessage());
    }

    @CircuitBreaker(name = "notificationService", fallbackMethod = "notifyAboutNextScheduledPaymentFallback")
    @Retry(name = "notificationService")
    @TimeLimiter(name = "notificationService")
    private void notifyAboutNextScheduledPayment(BillPaymentStatusEvent event, String correlationId) {
        log.debug("Notifying about next scheduled bill payment: userId={}, billPaymentId={}, correlationId={}",
                event.getUserId(), event.getBillPaymentId(), correlationId);

        if (event.getNextScheduledDate() == null) {
            log.debug("No next scheduled date available: billPaymentId={}, correlationId={}",
                    event.getBillPaymentId(), correlationId);
            return;
        }

        UserNotificationPreferences prefs = userPreferenceService.getUserPreferences(event.getUserId());

        if (prefs == null || !prefs.isBillPaymentNotificationsEnabled()) {
            return;
        }

        Map<String, Object> nextPaymentData = buildNextPaymentTemplateData(event);

        if (prefs.isPushEnabled()) {
            sendNextPaymentPushNotification(event, nextPaymentData, correlationId);
        }

        sendNextPaymentInAppNotification(event, nextPaymentData, correlationId);

        log.debug("Next scheduled payment notification sent: userId={}, billPaymentId={}, nextDate={}, correlationId={}",
                event.getUserId(), event.getBillPaymentId(), event.getNextScheduledDate(), correlationId);
    }

    private void notifyAboutNextScheduledPaymentFallback(BillPaymentStatusEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for next payment notification: billPaymentId={}, correlationId={}, error={}",
                event.getBillPaymentId(), correlationId, e.getMessage());
    }

    private Map<String, Object> buildTemplateData(BillPaymentStatusEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("billPaymentId", event.getBillPaymentId());
        data.put("billerName", event.getBillerName());
        data.put("billType", event.getBillType());
        data.put("amount", formatAmount(event.getAmount()));
        data.put("currency", event.getCurrency());
        data.put("status", event.getStatus());
        data.put("accountNumber", maskAccountNumber(event.getAccountNumber()));
        data.put("dueDate", event.getDueDate());
        data.put("paidDate", event.getPaidDate());
        data.put("referenceNumber", event.getReferenceNumber());
        data.put("isRecurring", event.getIsRecurring());
        return data;
    }

    private Map<String, Object> buildFailureTemplateData(BillPaymentStatusEvent event) {
        Map<String, Object> data = buildTemplateData(event);
        data.put("failureReason", event.getFailureReason());
        data.put("retryable", event.getIsRetryable());
        data.put("supportContact", "support@example.com");
        return data;
    }

    private Map<String, Object> buildReminderTemplateData(BillPaymentStatusEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("billPaymentId", event.getBillPaymentId());
        data.put("billerName", event.getBillerName());
        data.put("billType", event.getBillType());
        data.put("amount", formatAmount(event.getAmount()));
        data.put("currency", event.getCurrency());
        data.put("scheduledDate", event.getScheduledDate());
        data.put("accountNumber", maskAccountNumber(event.getAccountNumber()));
        return data;
    }

    private Map<String, Object> buildNextPaymentTemplateData(BillPaymentStatusEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("billerName", event.getBillerName());
        data.put("billType", event.getBillType());
        data.put("amount", formatAmount(event.getAmount()));
        data.put("currency", event.getCurrency());
        data.put("nextScheduledDate", event.getNextScheduledDate());
        data.put("recurringFrequency", event.getRecurringFrequency());
        return data;
    }

    private void sendEmail(BillPaymentStatusEvent event, Map<String, Object> templateData, String correlationId) {
        try {
            String subject = buildEmailSubject(event);
            String body = notificationTemplateService.renderTemplate("BILL_PAYMENT_STATUS", templateData);
            emailService.sendEmail(event.getUserEmail(), subject, body, correlationId);
            
            saveNotificationRecord(event.getUserId(), "BILL_PAYMENT_STATUS", "EMAIL", "SENT", correlationId);
        } catch (Exception e) {
            log.error("Failed to send bill payment status email: userId={}, billPaymentId={}, correlationId={}, error={}",
                    event.getUserId(), event.getBillPaymentId(), correlationId, e.getMessage());
        }
    }

    private void sendSms(BillPaymentStatusEvent event, Map<String, Object> templateData, String correlationId) {
        try {
            String message = buildSmsMessage(event);
            smsService.sendSms(event.getUserPhone(), message, correlationId);
            
            saveNotificationRecord(event.getUserId(), "BILL_PAYMENT_STATUS", "SMS", "SENT", correlationId);
        } catch (Exception e) {
            log.error("Failed to send bill payment status SMS: userId={}, billPaymentId={}, correlationId={}, error={}",
                    event.getUserId(), event.getBillPaymentId(), correlationId, e.getMessage());
        }
    }

    private void sendPushNotification(BillPaymentStatusEvent event, Map<String, Object> templateData, 
                                     String correlationId) {
        try {
            String title = buildPushTitle(event);
            String message = buildPushMessage(event);
            
            pushNotificationService.sendPush(event.getUserId(), title, message, templateData, correlationId);
            
            saveNotificationRecord(event.getUserId(), "BILL_PAYMENT_STATUS", "PUSH", "SENT", correlationId);
        } catch (Exception e) {
            log.error("Failed to send bill payment status push notification: userId={}, billPaymentId={}, correlationId={}, error={}",
                    event.getUserId(), event.getBillPaymentId(), correlationId, e.getMessage());
        }
    }

    private void sendInAppNotification(BillPaymentStatusEvent event, Map<String, Object> templateData, 
                                      String correlationId) {
        try {
            String title = buildPushTitle(event);
            String message = buildPushMessage(event);
            
            inAppNotificationService.createNotification(event.getUserId(), title, message, 
                    templateData, correlationId);
            
            saveNotificationRecord(event.getUserId(), "BILL_PAYMENT_STATUS", "IN_APP", "DELIVERED", correlationId);
        } catch (Exception e) {
            log.error("Failed to send bill payment status in-app notification: userId={}, billPaymentId={}, correlationId={}, error={}",
                    event.getUserId(), event.getBillPaymentId(), correlationId, e.getMessage());
        }
    }

    private void sendFailureEmail(BillPaymentStatusEvent event, Map<String, Object> templateData, 
                                  String correlationId) {
        try {
            String subject = String.format("Bill Payment Failed - %s", event.getBillerName());
            String body = notificationTemplateService.renderTemplate("BILL_PAYMENT_FAILURE", templateData);
            emailService.sendEmail(event.getUserEmail(), subject, body, correlationId);
            
            saveNotificationRecord(event.getUserId(), "BILL_PAYMENT_FAILURE", "EMAIL", "SENT", correlationId);
        } catch (Exception e) {
            log.error("Failed to send bill payment failure email: userId={}, billPaymentId={}, correlationId={}, error={}",
                    event.getUserId(), event.getBillPaymentId(), correlationId, e.getMessage());
        }
    }

    private void sendFailurePushNotification(BillPaymentStatusEvent event, Map<String, Object> templateData,
                                            String correlationId) {
        try {
            String title = "Bill Payment Failed";
            String message = String.format("Payment to %s failed. %s", event.getBillerName(), 
                    event.getFailureReason() != null ? event.getFailureReason() : "Please try again.");
            
            pushNotificationService.sendPush(event.getUserId(), title, message, templateData, correlationId);
            
            saveNotificationRecord(event.getUserId(), "BILL_PAYMENT_FAILURE", "PUSH", "SENT", correlationId);
        } catch (Exception e) {
            log.error("Failed to send bill payment failure push notification: userId={}, billPaymentId={}, correlationId={}, error={}",
                    event.getUserId(), event.getBillPaymentId(), correlationId, e.getMessage());
        }
    }

    private void sendFailureInAppNotification(BillPaymentStatusEvent event, Map<String, Object> templateData,
                                             String correlationId) {
        try {
            String message = String.format("Your bill payment to %s failed. Reason: %s",
                    event.getBillerName(), event.getFailureReason() != null ? event.getFailureReason() : "Unknown");
            
            inAppNotificationService.createNotification(event.getUserId(), "Bill Payment Failed", 
                    message, templateData, correlationId);
            
            saveNotificationRecord(event.getUserId(), "BILL_PAYMENT_FAILURE", "IN_APP", "DELIVERED", correlationId);
        } catch (Exception e) {
            log.error("Failed to send bill payment failure in-app notification: userId={}, billPaymentId={}, correlationId={}, error={}",
                    event.getUserId(), event.getBillPaymentId(), correlationId, e.getMessage());
        }
    }

    private void sendNextPaymentPushNotification(BillPaymentStatusEvent event, Map<String, Object> templateData,
                                                String correlationId) {
        try {
            String title = "Next Bill Payment Scheduled";
            String message = String.format("Your next payment to %s is scheduled for %s (%s %s)",
                    event.getBillerName(), event.getNextScheduledDate(), 
                    formatAmount(event.getAmount()), event.getCurrency());
            
            pushNotificationService.sendPush(event.getUserId(), title, message, templateData, correlationId);
            
            saveNotificationRecord(event.getUserId(), "BILL_PAYMENT_NEXT_SCHEDULED", "PUSH", "SENT", correlationId);
        } catch (Exception e) {
            log.error("Failed to send next payment push notification: userId={}, billPaymentId={}, correlationId={}, error={}",
                    event.getUserId(), event.getBillPaymentId(), correlationId, e.getMessage());
        }
    }

    private void sendNextPaymentInAppNotification(BillPaymentStatusEvent event, Map<String, Object> templateData,
                                                 String correlationId) {
        try {
            String message = String.format("Your next recurring payment to %s is scheduled for %s",
                    event.getBillerName(), event.getNextScheduledDate());
            
            inAppNotificationService.createNotification(event.getUserId(), "Next Bill Payment Scheduled", 
                    message, templateData, correlationId);
            
            saveNotificationRecord(event.getUserId(), "BILL_PAYMENT_NEXT_SCHEDULED", "IN_APP", "DELIVERED", correlationId);
        } catch (Exception e) {
            log.error("Failed to send next payment in-app notification: userId={}, billPaymentId={}, correlationId={}, error={}",
                    event.getUserId(), event.getBillPaymentId(), correlationId, e.getMessage());
        }
    }

    private void saveNotificationRecord(String userId, String type, String channel, 
                                       String status, String correlationId) {
        try {
            Notification notification = Notification.builder()
                    .userId(userId)
                    .notificationType(type)
                    .channel(channel)
                    .status(status)
                    .createdAt(LocalDateTime.now())
                    .correlationId(correlationId)
                    .build();
            
            notificationRepository.save(notification);
        } catch (Exception e) {
            log.error("Failed to save notification record: userId={}, type={}, correlationId={}, error={}",
                    userId, type, correlationId, e.getMessage());
        }
    }

    private String buildStatusTitle(BillPaymentStatusEvent event) {
        switch (event.getStatus()) {
            case "COMPLETED":
                return "Bill Payment Successful";
            case "FAILED":
                return "Bill Payment Failed";
            case "PENDING":
                return "Bill Payment Pending";
            case "SCHEDULED":
                return "Bill Payment Scheduled";
            case "PROCESSING":
                return "Bill Payment Processing";
            case "CANCELLED":
                return "Bill Payment Cancelled";
            default:
                return "Bill Payment Status Update";
        }
    }

    private String buildStatusMessage(BillPaymentStatusEvent event) {
        return String.format("Bill payment to %s (%s %s) - Status: %s",
                event.getBillerName(),
                formatAmount(event.getAmount()),
                event.getCurrency(),
                event.getStatus());
    }

    private String buildReminderMessage(BillPaymentStatusEvent event) {
        return String.format("Reminder: Your bill payment to %s (%s %s) is scheduled for tomorrow",
                event.getBillerName(),
                formatAmount(event.getAmount()),
                event.getCurrency());
    }

    private String buildEmailSubject(BillPaymentStatusEvent event) {
        return String.format("Bill Payment %s - %s", 
                event.getStatus().substring(0, 1).toUpperCase() + event.getStatus().substring(1).toLowerCase(),
                event.getBillerName());
    }

    private String buildSmsMessage(BillPaymentStatusEvent event) {
        return String.format("Bill payment to %s: %s %s - Status: %s. Ref: %s",
                event.getBillerName(),
                formatAmount(event.getAmount()),
                event.getCurrency(),
                event.getStatus(),
                event.getReferenceNumber());
    }

    private String buildPushTitle(BillPaymentStatusEvent event) {
        return buildStatusTitle(event);
    }

    private String buildPushMessage(BillPaymentStatusEvent event) {
        switch (event.getStatus()) {
            case "COMPLETED":
                return String.format("Successfully paid %s %s to %s",
                        formatAmount(event.getAmount()), event.getCurrency(), event.getBillerName());
            case "FAILED":
                return String.format("Payment to %s failed. Please try again.",
                        event.getBillerName());
            case "SCHEDULED":
                return String.format("Payment to %s scheduled for %s",
                        event.getBillerName(), event.getScheduledDate());
            default:
                return buildStatusMessage(event);
        }
    }

    private String determinePriority(BillPaymentStatusEvent event) {
        switch (event.getStatus()) {
            case "FAILED":
            case "REJECTED":
                return "HIGH";
            case "COMPLETED":
                return "MEDIUM";
            default:
                return "LOW";
        }
    }

    private Map<String, Object> buildStatusMetadata(BillPaymentStatusEvent event) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("billPaymentId", event.getBillPaymentId());
        metadata.put("billerName", event.getBillerName());
        metadata.put("billType", event.getBillType());
        metadata.put("amount", event.getAmount());
        metadata.put("currency", event.getCurrency());
        metadata.put("status", event.getStatus());
        metadata.put("referenceNumber", event.getReferenceNumber());
        metadata.put("isRecurring", event.getIsRecurring());
        return metadata;
    }

    private boolean shouldSendEmailForStatus(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status) || "SCHEDULED".equals(status);
    }

    private boolean shouldSendSmsForStatus(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status);
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return accountNumber;
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toString();
    }
}