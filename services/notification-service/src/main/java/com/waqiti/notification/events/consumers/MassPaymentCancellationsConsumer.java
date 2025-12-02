package com.waqiti.notification.events.consumers;

import com.waqiti.notification.domain.*;
import com.waqiti.notification.repository.*;
import com.waqiti.notification.service.*;
import com.waqiti.common.events.payment.MassPaymentCancellationEvent;
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
public class MassPaymentCancellationsConsumer {

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

    public MassPaymentCancellationsConsumer(
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
        this.eventsProcessedCounter = Counter.builder("mass_payment_cancellation_events_processed_total")
                .description("Total number of mass payment cancellation events processed")
                .tag("consumer", "mass-payment-cancellation-consumer")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("mass_payment_cancellation_events_failed_total")
                .description("Total number of mass payment cancellation events failed")
                .tag("consumer", "mass-payment-cancellation-consumer")
                .register(meterRegistry);

        this.notificationsSentCounter = Counter.builder("mass_payment_cancellation_notifications_sent_total")
                .description("Total notifications sent for mass payment cancellations")
                .tag("consumer", "mass-payment-cancellation-consumer")
                .register(meterRegistry);

        this.recipientsNotifiedCounter = Counter.builder("mass_payment_cancellation_recipients_notified_total")
                .description("Total recipients notified about mass payment cancellations")
                .tag("consumer", "mass-payment-cancellation-consumer")
                .register(meterRegistry);

        this.eventProcessingTimer = Timer.builder("mass_payment_cancellation_event_processing_duration")
                .description("Time taken to process mass payment cancellation events")
                .tag("consumer", "mass-payment-cancellation-consumer")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "${kafka.topics.mass-payment-cancellations:mass-payment-cancellations}",
            groupId = "${kafka.consumer.group-id:notification-mass-payment-cancellation-consumer-group}",
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
    public void handleMassPaymentCancellation(
            @Payload MassPaymentCancellationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = event.getCorrelationId();

        try {
            log.info("Processing mass payment cancellation event: eventId={}, batchId={}, initiatorId={}, " +
                    "affectedRecipients={}, cancellationReason={}, topic={}, partition={}, offset={}, correlationId={}",
                    event.getEventId(), event.getBatchId(), event.getInitiatorUserId(), 
                    event.getAffectedRecipients(), event.getCancellationReason(),
                    topic, partition, offset, correlationId);

            if (eventProcessingTrackingService.isEventAlreadyProcessed(event.getEventId(), "MASS_PAYMENT_CANCELLATION")) {
                log.warn("Duplicate mass payment cancellation event detected: eventId={}, correlationId={}. Skipping processing.",
                        event.getEventId(), correlationId);
                acknowledgment.acknowledge();
                return;
            }

            validateEvent(event);

            notifyInitiator(event, correlationId);

            notifyRecipients(event, correlationId);

            if (event.getRefundAmount() != null && event.getRefundAmount().compareTo(BigDecimal.ZERO) > 0) {
                notifyAboutRefunds(event, correlationId);
            }

            createCancellationRecord(event, correlationId);

            eventProcessingTrackingService.markEventAsProcessed(
                    event.getEventId(),
                    "MASS_PAYMENT_CANCELLATION",
                    "notification-service",
                    correlationId
            );

            eventsProcessedCounter.increment();
            Counter.builder("mass_payment_cancellations_by_reason")
                    .tag("cancellation_reason", event.getCancellationReason())
                    .register(meterRegistry)
                    .increment();

            acknowledgment.acknowledge();

            log.info("Successfully processed mass payment cancellation event: eventId={}, batchId={}, correlationId={}",
                    event.getEventId(), event.getBatchId(), correlationId);

        } catch (Exception e) {
            eventsFailedCounter.increment();
            log.error("Failed to process mass payment cancellation event: eventId={}, batchId={}, correlationId={}, error={}",
                    event.getEventId(), event.getBatchId(), correlationId, e.getMessage(), e);
            throw new RuntimeException("Mass payment cancellation event processing failed", e);
        } finally {
            sample.stop(eventProcessingTimer);
        }
    }

    private void validateEvent(MassPaymentCancellationEvent event) {
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID is required");
        }

        if (event.getBatchId() == null || event.getBatchId().trim().isEmpty()) {
            throw new IllegalArgumentException("Batch ID is required");
        }

        if (event.getInitiatorUserId() == null || event.getInitiatorUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("Initiator user ID is required");
        }

        if (event.getCancelledBy() == null || event.getCancelledBy().trim().isEmpty()) {
            throw new IllegalArgumentException("Cancelled by is required");
        }

        if (event.getCancellationReason() == null || event.getCancellationReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Cancellation reason is required");
        }
    }

    @CircuitBreaker(name = "notificationService", fallbackMethod = "notifyInitiatorFallback")
    @Retry(name = "notificationService")
    @TimeLimiter(name = "notificationService")
    private void notifyInitiator(MassPaymentCancellationEvent event, String correlationId) {
        log.debug("Notifying initiator about mass payment cancellation: initiatorId={}, batchId={}, correlationId={}",
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

        log.debug("Initiator notified about cancellation: initiatorId={}, batchId={}, correlationId={}",
                event.getInitiatorUserId(), event.getBatchId(), correlationId);
    }

    private void notifyInitiatorFallback(MassPaymentCancellationEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for initiator notification: initiatorId={}, correlationId={}, error={}",
                event.getInitiatorUserId(), correlationId, e.getMessage());
    }

    @CircuitBreaker(name = "notificationService", fallbackMethod = "notifyRecipientsFallback")
    @Retry(name = "notificationService")
    @TimeLimiter(name = "notificationService")
    private void notifyRecipients(MassPaymentCancellationEvent event, String correlationId) {
        log.debug("Notifying recipients about mass payment cancellation: batchId={}, affectedRecipients={}, correlationId={}",
                event.getBatchId(), event.getAffectedRecipients(), correlationId);

        if (event.getRecipientDetails() == null || event.getRecipientDetails().isEmpty()) {
            log.warn("No recipient details available for cancellation notification: batchId={}, correlationId={}",
                    event.getBatchId(), correlationId);
            return;
        }

        int notifiedCount = 0;

        for (Map<String, Object> recipientData : event.getRecipientDetails()) {
            try {
                String recipientId = (String) recipientData.get("recipientId");
                String paymentStatus = (String) recipientData.get("status");

                if ("CANCELLED".equals(paymentStatus) || "PENDING_CANCELLATION".equals(paymentStatus)) {
                    notifyRecipient(recipientId, recipientData, event, correlationId);
                    notifiedCount++;
                }

            } catch (Exception e) {
                log.error("Failed to notify recipient about cancellation: recipientData={}, correlationId={}, error={}",
                        recipientData, correlationId, e.getMessage());
            }
        }

        recipientsNotifiedCounter.increment(notifiedCount);

        log.debug("Recipients notified about cancellation: batchId={}, notifiedCount={}, correlationId={}",
                event.getBatchId(), notifiedCount, correlationId);
    }

    private void notifyRecipientsFallback(MassPaymentCancellationEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for recipients notification: batchId={}, correlationId={}, error={}",
                event.getBatchId(), correlationId, e.getMessage());
    }

    @CircuitBreaker(name = "notificationService", fallbackMethod = "notifyRecipientFallback")
    @Retry(name = "notificationService")
    @TimeLimiter(name = "notificationService")
    private void notifyRecipient(String recipientId, Map<String, Object> recipientData, 
                                 MassPaymentCancellationEvent event, String correlationId) {
        
        log.trace("Notifying individual recipient about cancellation: recipientId={}, batchId={}, correlationId={}",
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

        log.trace("Recipient notified about cancellation: recipientId={}, batchId={}, correlationId={}",
                recipientId, event.getBatchId(), correlationId);
    }

    private void notifyRecipientFallback(String recipientId, Map<String, Object> recipientData,
                                        MassPaymentCancellationEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for recipient notification: recipientId={}, correlationId={}, error={}",
                recipientId, correlationId, e.getMessage());
    }

    @CircuitBreaker(name = "notificationService", fallbackMethod = "notifyAboutRefundsFallback")
    @Retry(name = "notificationService")
    @TimeLimiter(name = "notificationService")
    private void notifyAboutRefunds(MassPaymentCancellationEvent event, String correlationId) {
        log.debug("Notifying about refunds: batchId={}, refundAmount={}, correlationId={}",
                event.getBatchId(), event.getRefundAmount(), correlationId);

        UserNotificationPreferences prefs = userPreferenceService.getUserPreferences(event.getInitiatorUserId());

        if (prefs == null || !prefs.isPaymentNotificationsEnabled()) {
            return;
        }

        Map<String, Object> templateData = buildRefundNotificationData(event);

        if (prefs.isEmailEnabled()) {
            sendRefundEmail(event, templateData, correlationId);
        }

        if (prefs.isPushEnabled()) {
            sendRefundPushNotification(event, templateData, correlationId);
        }

        sendRefundInAppNotification(event, templateData, correlationId);

        notificationsSentCounter.increment();

        log.debug("Refund notification sent: batchId={}, refundAmount={}, correlationId={}",
                event.getBatchId(), event.getRefundAmount(), correlationId);
    }

    private void notifyAboutRefundsFallback(MassPaymentCancellationEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for refund notification: batchId={}, correlationId={}, error={}",
                event.getBatchId(), correlationId, e.getMessage());
    }

    @CircuitBreaker(name = "notificationService", fallbackMethod = "createCancellationRecordFallback")
    @Retry(name = "notificationService")
    @TimeLimiter(name = "notificationService")
    private void createCancellationRecord(MassPaymentCancellationEvent event, String correlationId) {
        log.debug("Creating cancellation record: batchId={}, correlationId={}",
                event.getBatchId(), correlationId);

        Notification cancellationNotification = Notification.builder()
                .userId(event.getInitiatorUserId())
                .notificationType("MASS_PAYMENT_CANCELLATION")
                .title("Mass Payment Batch Cancelled")
                .message(buildCancellationMessage(event))
                .priority(determinePriority(event))
                .channel("IN_APP")
                .status("DELIVERED")
                .metadata(buildCancellationMetadata(event))
                .createdAt(LocalDateTime.now())
                .deliveredAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

        notificationRepository.save(cancellationNotification);

        log.debug("Cancellation record created: batchId={}, notificationId={}, correlationId={}",
                event.getBatchId(), cancellationNotification.getId(), correlationId);
    }

    private void createCancellationRecordFallback(MassPaymentCancellationEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for cancellation record: batchId={}, correlationId={}, error={}",
                event.getBatchId(), correlationId, e.getMessage());
    }

    private Map<String, Object> buildInitiatorTemplateData(MassPaymentCancellationEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("batchId", event.getBatchId());
        data.put("batchName", event.getBatchName());
        data.put("affectedRecipients", event.getAffectedRecipients());
        data.put("totalAmount", formatAmount(event.getTotalAmount()));
        data.put("currency", event.getCurrency());
        data.put("cancellationReason", event.getCancellationReason());
        data.put("cancelledBy", event.getCancelledBy());
        data.put("cancelledAt", event.getCancelledAt());
        data.put("refundAmount", formatAmount(event.getRefundAmount()));
        data.put("refundStatus", event.getRefundStatus());
        data.put("refundEta", event.getRefundEta());
        return data;
    }

    private Map<String, Object> buildRecipientTemplateData(Map<String, Object> recipientData, 
                                                           MassPaymentCancellationEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("recipientName", recipientData.get("recipientName"));
        data.put("expectedAmount", formatAmount((BigDecimal) recipientData.get("amount")));
        data.put("currency", event.getCurrency());
        data.put("senderName", event.getInitiatorName());
        data.put("batchName", event.getBatchName());
        data.put("cancellationReason", getCancellationReasonForRecipient(event.getCancellationReason()));
        data.put("cancelledAt", event.getCancelledAt());
        return data;
    }

    private Map<String, Object> buildRefundNotificationData(MassPaymentCancellationEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("batchId", event.getBatchId());
        data.put("batchName", event.getBatchName());
        data.put("refundAmount", formatAmount(event.getRefundAmount()));
        data.put("currency", event.getCurrency());
        data.put("refundStatus", event.getRefundStatus());
        data.put("refundEta", event.getRefundEta());
        data.put("refundMethod", event.getRefundMethod());
        return data;
    }

    private void sendInitiatorEmail(MassPaymentCancellationEvent event, Map<String, Object> templateData, 
                                    String correlationId) {
        try {
            String subject = String.format("Mass Payment Batch %s Cancelled", event.getBatchName());
            String body = notificationTemplateService.renderTemplate("MASS_PAYMENT_CANCELLATION_INITIATOR", templateData);
            emailService.sendEmail(event.getInitiatorEmail(), subject, body, correlationId);
            
            saveNotificationRecord(event.getInitiatorUserId(), "MASS_PAYMENT_CANCELLATION", 
                    "EMAIL", "SENT", correlationId);
        } catch (Exception e) {
            log.error("Failed to send initiator cancellation email: initiatorId={}, correlationId={}, error={}",
                    event.getInitiatorUserId(), correlationId, e.getMessage());
        }
    }

    private void sendInitiatorPushNotification(MassPaymentCancellationEvent event, Map<String, Object> templateData,
                                              String correlationId) {
        try {
            String title = "Mass Payment Cancelled";
            String message = String.format("Batch %s has been cancelled. %d recipients affected.",
                    event.getBatchName(), event.getAffectedRecipients());
            
            pushNotificationService.sendPush(event.getInitiatorUserId(), title, message, 
                    templateData, correlationId);
            
            saveNotificationRecord(event.getInitiatorUserId(), "MASS_PAYMENT_CANCELLATION", 
                    "PUSH", "SENT", correlationId);
        } catch (Exception e) {
            log.error("Failed to send initiator cancellation push notification: initiatorId={}, correlationId={}, error={}",
                    event.getInitiatorUserId(), correlationId, e.getMessage());
        }
    }

    private void sendInitiatorInAppNotification(MassPaymentCancellationEvent event, Map<String, Object> templateData,
                                               String correlationId) {
        try {
            String message = String.format("Your mass payment batch '%s' has been cancelled. %d recipients affected. Reason: %s",
                    event.getBatchName(), event.getAffectedRecipients(), event.getCancellationReason());
            
            inAppNotificationService.createNotification(event.getInitiatorUserId(), 
                    "Mass Payment Cancelled", message, templateData, correlationId);
            
            saveNotificationRecord(event.getInitiatorUserId(), "MASS_PAYMENT_CANCELLATION", 
                    "IN_APP", "DELIVERED", correlationId);
        } catch (Exception e) {
            log.error("Failed to send initiator cancellation in-app notification: initiatorId={}, correlationId={}, error={}",
                    event.getInitiatorUserId(), correlationId, e.getMessage());
        }
    }

    private void sendRecipientEmail(String recipientId, Map<String, Object> templateData, String correlationId) {
        try {
            String email = (String) templateData.get("recipientEmail");
            if (email == null || email.trim().isEmpty()) {
                return;
            }

            String subject = "Payment Cancelled";
            String body = notificationTemplateService.renderTemplate("MASS_PAYMENT_CANCELLED_RECIPIENT", templateData);
            emailService.sendEmail(email, subject, body, correlationId);
            
            saveNotificationRecord(recipientId, "PAYMENT_CANCELLED", "EMAIL", "SENT", correlationId);
        } catch (Exception e) {
            log.error("Failed to send recipient cancellation email: recipientId={}, correlationId={}, error={}",
                    recipientId, correlationId, e.getMessage());
        }
    }

    private void sendRecipientSms(String recipientId, Map<String, Object> templateData, String correlationId) {
        try {
            String phoneNumber = (String) templateData.get("recipientPhone");
            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                return;
            }

            String message = String.format("Expected payment of %s %s from %s has been cancelled.",
                    templateData.get("expectedAmount"), templateData.get("currency"),
                    templateData.get("senderName"));
            
            smsService.sendSms(phoneNumber, message, correlationId);
            
            saveNotificationRecord(recipientId, "PAYMENT_CANCELLED", "SMS", "SENT", correlationId);
        } catch (Exception e) {
            log.error("Failed to send recipient cancellation SMS: recipientId={}, correlationId={}, error={}",
                    recipientId, correlationId, e.getMessage());
        }
    }

    private void sendRecipientPushNotification(String recipientId, Map<String, Object> templateData, 
                                              String correlationId) {
        try {
            String title = "Payment Cancelled";
            String message = String.format("Expected payment of %s %s from %s has been cancelled.",
                    templateData.get("expectedAmount"), templateData.get("currency"), 
                    templateData.get("senderName"));
            
            pushNotificationService.sendPush(recipientId, title, message, templateData, correlationId);
            
            saveNotificationRecord(recipientId, "PAYMENT_CANCELLED", "PUSH", "SENT", correlationId);
        } catch (Exception e) {
            log.error("Failed to send recipient cancellation push notification: recipientId={}, correlationId={}, error={}",
                    recipientId, correlationId, e.getMessage());
        }
    }

    private void sendRecipientInAppNotification(String recipientId, Map<String, Object> templateData, 
                                               String correlationId) {
        try {
            String message = String.format("Expected payment of %s %s from %s has been cancelled.",
                    templateData.get("expectedAmount"), templateData.get("currency"), 
                    templateData.get("senderName"));
            
            inAppNotificationService.createNotification(recipientId, "Payment Cancelled", 
                    message, templateData, correlationId);
            
            saveNotificationRecord(recipientId, "PAYMENT_CANCELLED", "IN_APP", "DELIVERED", correlationId);
        } catch (Exception e) {
            log.error("Failed to send recipient cancellation in-app notification: recipientId={}, correlationId={}, error={}",
                    recipientId, correlationId, e.getMessage());
        }
    }

    private void sendRefundEmail(MassPaymentCancellationEvent event, Map<String, Object> templateData, 
                                 String correlationId) {
        try {
            String subject = String.format("Refund Processing for Cancelled Batch %s", event.getBatchName());
            String body = notificationTemplateService.renderTemplate("MASS_PAYMENT_REFUND", templateData);
            emailService.sendEmail(event.getInitiatorEmail(), subject, body, correlationId);
            
            saveNotificationRecord(event.getInitiatorUserId(), "MASS_PAYMENT_REFUND", 
                    "EMAIL", "SENT", correlationId);
        } catch (Exception e) {
            log.error("Failed to send refund email: initiatorId={}, correlationId={}, error={}",
                    event.getInitiatorUserId(), correlationId, e.getMessage());
        }
    }

    private void sendRefundPushNotification(MassPaymentCancellationEvent event, Map<String, Object> templateData,
                                           String correlationId) {
        try {
            String title = "Refund Processing";
            String message = String.format("Refund of %s %s is being processed for cancelled batch %s.",
                    formatAmount(event.getRefundAmount()), event.getCurrency(), event.getBatchName());
            
            pushNotificationService.sendPush(event.getInitiatorUserId(), title, message, 
                    templateData, correlationId);
            
            saveNotificationRecord(event.getInitiatorUserId(), "MASS_PAYMENT_REFUND", 
                    "PUSH", "SENT", correlationId);
        } catch (Exception e) {
            log.error("Failed to send refund push notification: initiatorId={}, correlationId={}, error={}",
                    event.getInitiatorUserId(), correlationId, e.getMessage());
        }
    }

    private void sendRefundInAppNotification(MassPaymentCancellationEvent event, Map<String, Object> templateData,
                                            String correlationId) {
        try {
            String message = String.format("Refund of %s %s is being processed for cancelled batch '%s'. ETA: %s",
                    formatAmount(event.getRefundAmount()), event.getCurrency(), 
                    event.getBatchName(), event.getRefundEta());
            
            inAppNotificationService.createNotification(event.getInitiatorUserId(), 
                    "Refund Processing", message, templateData, correlationId);
            
            saveNotificationRecord(event.getInitiatorUserId(), "MASS_PAYMENT_REFUND", 
                    "IN_APP", "DELIVERED", correlationId);
        } catch (Exception e) {
            log.error("Failed to send refund in-app notification: initiatorId={}, correlationId={}, error={}",
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

    private String buildCancellationMessage(MassPaymentCancellationEvent event) {
        return String.format("Mass payment batch '%s' cancelled: %d recipients affected. Reason: %s. Refund: %s %s (%s)",
                event.getBatchName(),
                event.getAffectedRecipients(),
                event.getCancellationReason(),
                formatAmount(event.getRefundAmount()),
                event.getCurrency(),
                event.getRefundStatus());
    }

    private String determinePriority(MassPaymentCancellationEvent event) {
        if ("FRAUD_DETECTED".equals(event.getCancellationReason()) || 
            "COMPLIANCE_VIOLATION".equals(event.getCancellationReason())) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private Map<String, Object> buildCancellationMetadata(MassPaymentCancellationEvent event) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("batchId", event.getBatchId());
        metadata.put("affectedRecipients", event.getAffectedRecipients());
        metadata.put("totalAmount", event.getTotalAmount());
        metadata.put("currency", event.getCurrency());
        metadata.put("cancellationReason", event.getCancellationReason());
        metadata.put("cancelledBy", event.getCancelledBy());
        metadata.put("cancelledAt", event.getCancelledAt());
        metadata.put("refundAmount", event.getRefundAmount());
        metadata.put("refundStatus", event.getRefundStatus());
        return metadata;
    }

    private String getCancellationReasonForRecipient(String internalReason) {
        switch (internalReason) {
            case "FRAUD_DETECTED":
                return "security reasons";
            case "INSUFFICIENT_FUNDS":
                return "insufficient funds";
            case "USER_REQUESTED":
                return "sender's request";
            case "COMPLIANCE_VIOLATION":
                return "compliance requirements";
            case "SYSTEM_ERROR":
                return "technical issues";
            default:
                return "administrative reasons";
        }
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toString();
    }
}