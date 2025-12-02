package com.waqiti.notification.events.consumers;

import com.waqiti.notification.domain.*;
import com.waqiti.notification.repository.*;
import com.waqiti.notification.service.*;
import com.waqiti.common.events.payment.MassPaymentCompletionEvent;
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
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class MassPaymentCompletionsConsumer {

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
    private Counter recipientsNotifiedCounter;
    private Timer eventProcessingTimer;

    public MassPaymentCompletionsConsumer(
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
        this.eventsProcessedCounter = Counter.builder("mass_payment_completion_events_processed_total")
                .description("Total number of mass payment completion events processed")
                .tag("consumer", "mass-payment-completion-consumer")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("mass_payment_completion_events_failed_total")
                .description("Total number of mass payment completion events failed")
                .tag("consumer", "mass-payment-completion-consumer")
                .register(meterRegistry);

        this.notificationsSentCounter = Counter.builder("mass_payment_completion_notifications_sent_total")
                .description("Total notifications sent for mass payment completions")
                .tag("consumer", "mass-payment-completion-consumer")
                .register(meterRegistry);

        this.recipientsNotifiedCounter = Counter.builder("mass_payment_recipients_notified_total")
                .description("Total recipients notified for mass payment completions")
                .tag("consumer", "mass-payment-completion-consumer")
                .register(meterRegistry);

        this.eventProcessingTimer = Timer.builder("mass_payment_completion_event_processing_duration")
                .description("Time taken to process mass payment completion events")
                .tag("consumer", "mass-payment-completion-consumer")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "${kafka.topics.mass-payment-completions:mass-payment-completions}",
            groupId = "${kafka.consumer.group-id:notification-mass-payment-completion-consumer-group}",
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${kafka.consumer.concurrency:3}"
    )
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
            include = {ServiceIntegrationException.class, Exception.class},
            dltTopicSuffix = "-dlt",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            autoCreateTopics = "true"
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED, timeout = 60)
    public void handleMassPaymentCompletion(
            @Payload MassPaymentCompletionEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = event.getCorrelationId();

        try {
            log.info("Processing mass payment completion event: eventId={}, batchId={}, initiatorId={}, " +
                    "totalRecipients={}, successfulPayments={}, topic={}, partition={}, offset={}, correlationId={}",
                    event.getEventId(), event.getBatchId(), event.getInitiatorUserId(), 
                    event.getTotalRecipients(), event.getSuccessfulPayments(),
                    topic, partition, offset, correlationId);

            if (eventProcessingTrackingService.isEventAlreadyProcessed(event.getEventId(), "MASS_PAYMENT_COMPLETION")) {
                log.warn("Duplicate mass payment completion event detected: eventId={}, correlationId={}. Skipping processing.",
                        event.getEventId(), correlationId);
                acknowledgment.acknowledge();
                return;
            }

            validateEvent(event);

            notifyInitiator(event, correlationId);

            notifyRecipients(event, correlationId);

            if (event.getFailedPayments() != null && event.getFailedPayments() > 0) {
                notifyAboutFailures(event, correlationId);
            }

            createCompletionSummary(event, correlationId);

            eventProcessingTrackingService.markEventAsProcessed(
                    event.getEventId(),
                    "MASS_PAYMENT_COMPLETION",
                    "notification-service",
                    correlationId
            );

            eventsProcessedCounter.increment();

            acknowledgment.acknowledge();

            log.info("Successfully processed mass payment completion event: eventId={}, batchId={}, correlationId={}",
                    event.getEventId(), event.getBatchId(), correlationId);

        } catch (Exception e) {
            eventsFailedCounter.increment();
            log.error("Failed to process mass payment completion event: eventId={}, batchId={}, correlationId={}, error={}",
                    event.getEventId(), event.getBatchId(), correlationId, e.getMessage(), e);
            throw new RuntimeException("Mass payment completion event processing failed", e);
        } finally {
            sample.stop(eventProcessingTimer);
        }
    }

    private void validateEvent(MassPaymentCompletionEvent event) {
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID is required");
        }

        if (event.getBatchId() == null || event.getBatchId().trim().isEmpty()) {
            throw new IllegalArgumentException("Batch ID is required");
        }

        if (event.getInitiatorUserId() == null || event.getInitiatorUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("Initiator user ID is required");
        }

        if (event.getTotalRecipients() == null || event.getTotalRecipients() <= 0) {
            throw new IllegalArgumentException("Total recipients must be positive");
        }

        if (event.getTotalAmount() == null || event.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total amount must be positive");
        }
    }

    @CircuitBreaker(name = "notificationService", fallbackMethod = "notifyInitiatorFallback")
    @Retry(name = "notificationService")
    @TimeLimiter(name = "notificationService")
    private void notifyInitiator(MassPaymentCompletionEvent event, String correlationId) {
        log.debug("Notifying initiator about mass payment completion: initiatorId={}, batchId={}, correlationId={}",
                event.getInitiatorUserId(), event.getBatchId(), correlationId);

        UserNotificationPreferences prefs = userPreferenceService.getUserPreferences(event.getInitiatorUserId());

        if (prefs == null || !prefs.isPaymentNotificationsEnabled()) {
            log.debug("Payment notifications disabled for initiator: userId={}, correlationId={}",
                    event.getInitiatorUserId(), correlationId);
            return;
        }

        Map<String, Object> templateData = buildInitiatorTemplateData(event);

        if (prefs.isEmailEnabled()) {
            sendInitiatorEmail(event, templateData, correlationId);
        }

        if (prefs.isPushEnabled()) {
            sendInitiatorPushNotification(event, templateData, correlationId);
        }

        sendInitiatorInAppNotification(event, templateData, correlationId);

        notificationsSentCounter.increment();

        log.debug("Initiator notified: initiatorId={}, batchId={}, correlationId={}",
                event.getInitiatorUserId(), event.getBatchId(), correlationId);
    }

    private void notifyInitiatorFallback(MassPaymentCompletionEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for initiator notification: initiatorId={}, correlationId={}, error={}",
                event.getInitiatorUserId(), correlationId, e.getMessage());
    }

    @CircuitBreaker(name = "notificationService", fallbackMethod = "notifyRecipientsFallback")
    @Retry(name = "notificationService")
    @TimeLimiter(name = "notificationService")
    private void notifyRecipients(MassPaymentCompletionEvent event, String correlationId) {
        log.debug("Notifying recipients about mass payment completion: batchId={}, totalRecipients={}, correlationId={}",
                event.getBatchId(), event.getTotalRecipients(), correlationId);

        if (event.getRecipientDetails() == null || event.getRecipientDetails().isEmpty()) {
            log.warn("No recipient details available for notification: batchId={}, correlationId={}",
                    event.getBatchId(), correlationId);
            return;
        }

        int notifiedCount = 0;

        for (Map<String, Object> recipientData : event.getRecipientDetails()) {
            try {
                String recipientId = (String) recipientData.get("recipientId");
                String paymentStatus = (String) recipientData.get("status");

                if ("SUCCESS".equals(paymentStatus) || "COMPLETED".equals(paymentStatus)) {
                    notifyRecipient(recipientId, recipientData, event, correlationId);
                    notifiedCount++;
                }

            } catch (Exception e) {
                log.error("Failed to notify recipient: recipientData={}, correlationId={}, error={}",
                        recipientData, correlationId, e.getMessage());
            }
        }

        recipientsNotifiedCounter.increment(notifiedCount);

        log.debug("Recipients notified: batchId={}, notifiedCount={}, correlationId={}",
                event.getBatchId(), notifiedCount, correlationId);
    }

    private void notifyRecipientsFallback(MassPaymentCompletionEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for recipients notification: batchId={}, correlationId={}, error={}",
                event.getBatchId(), correlationId, e.getMessage());
    }

    @CircuitBreaker(name = "notificationService", fallbackMethod = "notifyRecipientFallback")
    @Retry(name = "notificationService")
    @TimeLimiter(name = "notificationService")
    private void notifyRecipient(String recipientId, Map<String, Object> recipientData, 
                                 MassPaymentCompletionEvent event, String correlationId) {
        
        log.trace("Notifying individual recipient: recipientId={}, batchId={}, correlationId={}",
                recipientId, event.getBatchId(), correlationId);

        UserNotificationPreferences prefs = userPreferenceService.getUserPreferences(recipientId);

        if (prefs == null || !prefs.isPaymentNotificationsEnabled()) {
            log.trace("Payment notifications disabled for recipient: recipientId={}, correlationId={}",
                    recipientId, correlationId);
            return;
        }

        Map<String, Object> templateData = buildRecipientTemplateData(recipientData, event);

        if (prefs.isEmailEnabled()) {
            sendRecipientEmail(recipientId, templateData, correlationId);
        }

        if (prefs.isSmsEnabled()) {
            sendRecipientSms(recipientId, templateData, correlationId);
        }

        if (prefs.isPushEnabled()) {
            sendRecipientPushNotification(recipientId, templateData, correlationId);
        }

        sendRecipientInAppNotification(recipientId, templateData, correlationId);

        log.trace("Recipient notified: recipientId={}, batchId={}, correlationId={}",
                recipientId, event.getBatchId(), correlationId);
    }

    private void notifyRecipientFallback(String recipientId, Map<String, Object> recipientData,
                                        MassPaymentCompletionEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for recipient notification: recipientId={}, correlationId={}, error={}",
                recipientId, correlationId, e.getMessage());
    }

    @CircuitBreaker(name = "notificationService", fallbackMethod = "notifyAboutFailuresFallback")
    @Retry(name = "notificationService")
    @TimeLimiter(name = "notificationService")
    private void notifyAboutFailures(MassPaymentCompletionEvent event, String correlationId) {
        log.debug("Notifying about payment failures: batchId={}, failedPayments={}, correlationId={}",
                event.getBatchId(), event.getFailedPayments(), correlationId);

        UserNotificationPreferences prefs = userPreferenceService.getUserPreferences(event.getInitiatorUserId());

        if (prefs == null || !prefs.isPaymentNotificationsEnabled()) {
            return;
        }

        Map<String, Object> templateData = buildFailureNotificationData(event);

        if (prefs.isEmailEnabled()) {
            sendFailureEmail(event, templateData, correlationId);
        }

        if (prefs.isPushEnabled()) {
            sendFailurePushNotification(event, templateData, correlationId);
        }

        sendFailureInAppNotification(event, templateData, correlationId);

        notificationsSentCounter.increment();

        log.debug("Failure notification sent: batchId={}, failedPayments={}, correlationId={}",
                event.getBatchId(), event.getFailedPayments(), correlationId);
    }

    private void notifyAboutFailuresFallback(MassPaymentCompletionEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for failure notification: batchId={}, correlationId={}, error={}",
                event.getBatchId(), correlationId, e.getMessage());
    }

    @CircuitBreaker(name = "notificationService", fallbackMethod = "createCompletionSummaryFallback")
    @Retry(name = "notificationService")
    @TimeLimiter(name = "notificationService")
    private void createCompletionSummary(MassPaymentCompletionEvent event, String correlationId) {
        log.debug("Creating completion summary: batchId={}, correlationId={}",
                event.getBatchId(), correlationId);

        Notification summaryNotification = Notification.builder()
                .userId(event.getInitiatorUserId())
                .notificationType("MASS_PAYMENT_SUMMARY")
                .title("Mass Payment Batch Completed")
                .message(buildSummaryMessage(event))
                .priority(determinePriority(event))
                .channel("IN_APP")
                .status("DELIVERED")
                .metadata(buildSummaryMetadata(event))
                .createdAt(LocalDateTime.now())
                .deliveredAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

        notificationRepository.save(summaryNotification);

        log.debug("Completion summary created: batchId={}, notificationId={}, correlationId={}",
                event.getBatchId(), summaryNotification.getId(), correlationId);
    }

    private void createCompletionSummaryFallback(MassPaymentCompletionEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for completion summary: batchId={}, correlationId={}, error={}",
                event.getBatchId(), correlationId, e.getMessage());
    }

    private Map<String, Object> buildInitiatorTemplateData(MassPaymentCompletionEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("batchId", event.getBatchId());
        data.put("batchName", event.getBatchName());
        data.put("totalRecipients", event.getTotalRecipients());
        data.put("successfulPayments", event.getSuccessfulPayments());
        data.put("failedPayments", event.getFailedPayments());
        data.put("totalAmount", formatAmount(event.getTotalAmount()));
        data.put("currency", event.getCurrency());
        data.put("completedAt", event.getCompletedAt());
        data.put("successRate", calculateSuccessRate(event));
        data.put("processingTime", calculateProcessingTime(event));
        return data;
    }

    private Map<String, Object> buildRecipientTemplateData(Map<String, Object> recipientData, 
                                                           MassPaymentCompletionEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("recipientName", recipientData.get("recipientName"));
        data.put("amount", formatAmount((BigDecimal) recipientData.get("amount")));
        data.put("currency", event.getCurrency());
        data.put("transactionId", recipientData.get("transactionId"));
        data.put("senderName", event.getInitiatorName());
        data.put("description", recipientData.get("description"));
        data.put("completedAt", event.getCompletedAt());
        data.put("batchName", event.getBatchName());
        return data;
    }

    private Map<String, Object> buildFailureNotificationData(MassPaymentCompletionEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("batchId", event.getBatchId());
        data.put("batchName", event.getBatchName());
        data.put("failedPayments", event.getFailedPayments());
        data.put("totalRecipients", event.getTotalRecipients());
        data.put("failureReasons", event.getFailureReasons());
        data.put("supportContact", "support@example.com");
        return data;
    }

    private void sendInitiatorEmail(MassPaymentCompletionEvent event, Map<String, Object> templateData, 
                                    String correlationId) {
        try {
            String subject = String.format("Mass Payment Batch %s Completed", event.getBatchName());
            String body = notificationTemplateService.renderTemplate("MASS_PAYMENT_COMPLETION_INITIATOR", templateData);
            emailService.sendEmail(event.getInitiatorEmail(), subject, body, correlationId);
            
            saveNotificationRecord(event.getInitiatorUserId(), "MASS_PAYMENT_COMPLETION", 
                    "EMAIL", "SENT", correlationId);
        } catch (Exception e) {
            log.error("Failed to send initiator email: initiatorId={}, correlationId={}, error={}",
                    event.getInitiatorUserId(), correlationId, e.getMessage());
        }
    }

    private void sendInitiatorPushNotification(MassPaymentCompletionEvent event, Map<String, Object> templateData,
                                              String correlationId) {
        try {
            String title = "Mass Payment Completed";
            String message = String.format("Batch %s: %d/%d payments successful", 
                    event.getBatchName(), event.getSuccessfulPayments(), event.getTotalRecipients());
            
            pushNotificationService.sendPush(event.getInitiatorUserId(), title, message, 
                    templateData, correlationId);
            
            saveNotificationRecord(event.getInitiatorUserId(), "MASS_PAYMENT_COMPLETION", 
                    "PUSH", "SENT", correlationId);
        } catch (Exception e) {
            log.error("Failed to send initiator push notification: initiatorId={}, correlationId={}, error={}",
                    event.getInitiatorUserId(), correlationId, e.getMessage());
        }
    }

    private void sendInitiatorInAppNotification(MassPaymentCompletionEvent event, Map<String, Object> templateData,
                                               String correlationId) {
        try {
            String message = String.format("Your mass payment batch '%s' has been completed. %d of %d payments successful.",
                    event.getBatchName(), event.getSuccessfulPayments(), event.getTotalRecipients());
            
            inAppNotificationService.createNotification(event.getInitiatorUserId(), 
                    "Mass Payment Completed", message, templateData, correlationId);
            
            saveNotificationRecord(event.getInitiatorUserId(), "MASS_PAYMENT_COMPLETION", 
                    "IN_APP", "DELIVERED", correlationId);
        } catch (Exception e) {
            log.error("Failed to send initiator in-app notification: initiatorId={}, correlationId={}, error={}",
                    event.getInitiatorUserId(), correlationId, e.getMessage());
        }
    }

    private void sendRecipientEmail(String recipientId, Map<String, Object> templateData, String correlationId) {
        try {
            String email = (String) templateData.get("recipientEmail");
            if (email == null || email.trim().isEmpty()) {
                return;
            }

            String subject = "Payment Received";
            String body = notificationTemplateService.renderTemplate("MASS_PAYMENT_RECEIVED", templateData);
            emailService.sendEmail(email, subject, body, correlationId);
            
            saveNotificationRecord(recipientId, "PAYMENT_RECEIVED", "EMAIL", "SENT", correlationId);
        } catch (Exception e) {
            log.error("Failed to send recipient email: recipientId={}, correlationId={}, error={}",
                    recipientId, correlationId, e.getMessage());
        }
    }

    private void sendRecipientSms(String recipientId, Map<String, Object> templateData, String correlationId) {
        try {
            String phoneNumber = (String) templateData.get("recipientPhone");
            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                return;
            }

            String message = String.format("You received %s %s from %s. Transaction ID: %s",
                    templateData.get("amount"), templateData.get("currency"),
                    templateData.get("senderName"), templateData.get("transactionId"));
            
            smsService.sendSms(phoneNumber, message, correlationId);
            
            saveNotificationRecord(recipientId, "PAYMENT_RECEIVED", "SMS", "SENT", correlationId);
        } catch (Exception e) {
            log.error("Failed to send recipient SMS: recipientId={}, correlationId={}, error={}",
                    recipientId, correlationId, e.getMessage());
        }
    }

    private void sendRecipientPushNotification(String recipientId, Map<String, Object> templateData, 
                                              String correlationId) {
        try {
            String title = "Payment Received";
            String message = String.format("You received %s %s from %s",
                    templateData.get("amount"), templateData.get("currency"), templateData.get("senderName"));
            
            pushNotificationService.sendPush(recipientId, title, message, templateData, correlationId);
            
            saveNotificationRecord(recipientId, "PAYMENT_RECEIVED", "PUSH", "SENT", correlationId);
        } catch (Exception e) {
            log.error("Failed to send recipient push notification: recipientId={}, correlationId={}, error={}",
                    recipientId, correlationId, e.getMessage());
        }
    }

    private void sendRecipientInAppNotification(String recipientId, Map<String, Object> templateData, 
                                               String correlationId) {
        try {
            String message = String.format("You received %s %s from %s",
                    templateData.get("amount"), templateData.get("currency"), templateData.get("senderName"));
            
            inAppNotificationService.createNotification(recipientId, "Payment Received", 
                    message, templateData, correlationId);
            
            saveNotificationRecord(recipientId, "PAYMENT_RECEIVED", "IN_APP", "DELIVERED", correlationId);
        } catch (Exception e) {
            log.error("Failed to send recipient in-app notification: recipientId={}, correlationId={}, error={}",
                    recipientId, correlationId, e.getMessage());
        }
    }

    private void sendFailureEmail(MassPaymentCompletionEvent event, Map<String, Object> templateData, 
                                  String correlationId) {
        try {
            String subject = String.format("Action Required: %d Payments Failed in Batch %s", 
                    event.getFailedPayments(), event.getBatchName());
            String body = notificationTemplateService.renderTemplate("MASS_PAYMENT_FAILURES", templateData);
            emailService.sendEmail(event.getInitiatorEmail(), subject, body, correlationId);
            
            saveNotificationRecord(event.getInitiatorUserId(), "MASS_PAYMENT_FAILURES", 
                    "EMAIL", "SENT", correlationId);
        } catch (Exception e) {
            log.error("Failed to send failure email: initiatorId={}, correlationId={}, error={}",
                    event.getInitiatorUserId(), correlationId, e.getMessage());
        }
    }

    private void sendFailurePushNotification(MassPaymentCompletionEvent event, Map<String, Object> templateData,
                                            String correlationId) {
        try {
            String title = "Payment Failures Detected";
            String message = String.format("%d payments failed in batch %s. Review required.",
                    event.getFailedPayments(), event.getBatchName());
            
            pushNotificationService.sendPush(event.getInitiatorUserId(), title, message, 
                    templateData, correlationId);
            
            saveNotificationRecord(event.getInitiatorUserId(), "MASS_PAYMENT_FAILURES", 
                    "PUSH", "SENT", correlationId);
        } catch (Exception e) {
            log.error("Failed to send failure push notification: initiatorId={}, correlationId={}, error={}",
                    event.getInitiatorUserId(), correlationId, e.getMessage());
        }
    }

    private void sendFailureInAppNotification(MassPaymentCompletionEvent event, Map<String, Object> templateData,
                                             String correlationId) {
        try {
            String message = String.format("%d payments in batch '%s' failed. Please review and retry.",
                    event.getFailedPayments(), event.getBatchName());
            
            inAppNotificationService.createNotification(event.getInitiatorUserId(), 
                    "Payment Failures", message, templateData, correlationId);
            
            saveNotificationRecord(event.getInitiatorUserId(), "MASS_PAYMENT_FAILURES", 
                    "IN_APP", "DELIVERED", correlationId);
        } catch (Exception e) {
            log.error("Failed to send failure in-app notification: initiatorId={}, correlationId={}, error={}",
                    event.getInitiatorUserId(), correlationId, e.getMessage());
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

    private String buildSummaryMessage(MassPaymentCompletionEvent event) {
        return String.format("Mass payment batch '%s' completed: %d of %d payments successful (%s). Total amount: %s %s",
                event.getBatchName(),
                event.getSuccessfulPayments(),
                event.getTotalRecipients(),
                calculateSuccessRate(event),
                formatAmount(event.getTotalAmount()),
                event.getCurrency());
    }

    private String determinePriority(MassPaymentCompletionEvent event) {
        if (event.getFailedPayments() != null && event.getFailedPayments() > 0) {
            double failureRate = (double) event.getFailedPayments() / event.getTotalRecipients();
            if (failureRate > 0.1) {
                return "HIGH";
            }
        }
        return "MEDIUM";
    }

    private Map<String, Object> buildSummaryMetadata(MassPaymentCompletionEvent event) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("batchId", event.getBatchId());
        metadata.put("totalRecipients", event.getTotalRecipients());
        metadata.put("successfulPayments", event.getSuccessfulPayments());
        metadata.put("failedPayments", event.getFailedPayments());
        metadata.put("totalAmount", event.getTotalAmount());
        metadata.put("currency", event.getCurrency());
        metadata.put("completedAt", event.getCompletedAt());
        return metadata;
    }

    private String calculateSuccessRate(MassPaymentCompletionEvent event) {
        if (event.getTotalRecipients() == 0) {
            return "0%";
        }
        double rate = ((double) event.getSuccessfulPayments() / event.getTotalRecipients()) * 100;
        return String.format("%.1f%%", rate);
    }

    private String calculateProcessingTime(MassPaymentCompletionEvent event) {
        if (event.getInitiatedAt() == null || event.getCompletedAt() == null) {
            return "N/A";
        }
        
        long seconds = java.time.Duration.between(event.getInitiatedAt(), event.getCompletedAt()).getSeconds();
        
        if (seconds < 60) {
            return seconds + " seconds";
        } else if (seconds < 3600) {
            return (seconds / 60) + " minutes";
        } else {
            return (seconds / 3600) + " hours";
        }
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toString();
    }
}