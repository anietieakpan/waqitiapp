package com.waqiti.payment.events.consumers;

import com.waqiti.payment.events.GroupPaymentEvent;
import com.waqiti.payment.client.LedgerServiceClient;
import com.waqiti.payment.client.NotificationServiceClient;
import com.waqiti.payment.client.AnalyticsServiceClient;
import com.waqiti.payment.dto.ledger.RecordGroupPaymentRequest;
import com.waqiti.payment.dto.ledger.RecordGroupPaymentResponse;
import com.waqiti.payment.dto.notification.GroupPaymentNotificationRequest;
import com.waqiti.payment.dto.analytics.RecordGroupPaymentAnalyticsRequest;
import com.waqiti.payment.service.IdempotencyService;
import com.waqiti.payment.service.AuditService;
import com.waqiti.payment.exception.ServiceIntegrationException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class GroupPaymentEventConsumer {

    private final LedgerServiceClient ledgerServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final AnalyticsServiceClient analyticsServiceClient;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;

    @KafkaListener(
        topics = "${kafka.topics.group-payment-events:group-payment-events}",
        groupId = "${kafka.consumer.group-id:payment-service-consumer-group}",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "${kafka.consumer.concurrency:3}"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        include = {ServiceIntegrationException.class, Exception.class},
        dltTopicSuffix = "-dlt",
        autoCreateTopics = "true",
        dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRED, timeout = 30, rollbackFor = Exception.class)
    public void handleGroupPaymentEvent(
            @Payload GroupPaymentEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {

        Instant startTime = Instant.now();
        String correlationId = event.getCorrelationId() != null ? 
            event.getCorrelationId() : UUID.randomUUID().toString();

        log.info("GROUP_PAYMENT: Processing group payment event - eventId={}, groupPaymentId={}, " +
                "eventType={}, createdBy={}, totalAmount={}, status={}, topic={}, partition={}, offset={}",
                event.getEventId(), event.getGroupPaymentId(), event.getEventType(), 
                event.getCreatedBy(), event.getTotalAmount(), event.getStatus(), 
                topic, partition, offset);

        try {
            validateEvent(event);

            String idempotencyKey = String.format("group-payment-event:%s:%s", 
                event.getEventId(), event.getGroupPaymentId());
            
            boolean processed = idempotencyService.executeIdempotent(idempotencyKey, () -> {
                processGroupPaymentEvent(event, correlationId, startTime);
                return true;
            }, Duration.ofHours(24));

            if (!processed) {
                log.info("GROUP_PAYMENT: Duplicate event detected and skipped - eventId={}, groupPaymentId={}",
                    event.getEventId(), event.getGroupPaymentId());
            }

            acknowledgment.acknowledge();
            
            long processingTimeMs = Duration.between(startTime, Instant.now()).toMillis();
            log.info("GROUP_PAYMENT: Successfully processed group payment event - " +
                    "eventId={}, groupPaymentId={}, eventType={}, processingTimeMs={}",
                    event.getEventId(), event.getGroupPaymentId(), event.getEventType(), processingTimeMs);

        } catch (Exception e) {
            log.error("GROUP_PAYMENT: Failed to process group payment event - " +
                    "eventId={}, groupPaymentId={}, eventType={}, error={}",
                    event.getEventId(), event.getGroupPaymentId(), event.getEventType(), e.getMessage(), e);
            
            auditService.logEventProcessingFailure(
                "GroupPaymentEvent",
                event.getEventId(),
                event.getCreatedBy(),
                e.getMessage(),
                Map.of(
                    "groupPaymentId", event.getGroupPaymentId(),
                    "eventType", event.getEventType(),
                    "totalAmount", event.getTotalAmount().toString(),
                    "status", event.getStatus(),
                    "participantCount", event.getParticipants() != null ? 
                        String.valueOf(event.getParticipants().size()) : "0"
                )
            );
            
            throw new ServiceIntegrationException(
                "Failed to process group payment event: " + e.getMessage(), e);
        }
    }

    private void processGroupPaymentEvent(
            GroupPaymentEvent event, 
            String correlationId,
            Instant startTime) {

        log.info("GROUP_PAYMENT: Starting group payment processing pipeline - " +
                "eventId={}, groupPaymentId={}, eventType={}",
                event.getEventId(), event.getGroupPaymentId(), event.getEventType());

        switch (event.getEventType()) {
            case "GROUP_PAYMENT_CREATED":
                handleGroupPaymentCreated(event, correlationId);
                break;
            
            case "GROUP_PAYMENT_UPDATED":
                handleGroupPaymentUpdated(event, correlationId);
                break;
            
            case "GROUP_PAYMENT_PARTICIPANT_PAID":
                handleParticipantPaid(event, correlationId);
                break;
            
            case "GROUP_PAYMENT_SETTLED":
                handleGroupPaymentSettled(event, correlationId);
                break;
            
            case "GROUP_PAYMENT_CANCELLED":
                handleGroupPaymentCancelled(event, correlationId);
                break;
            
            case "GROUP_PAYMENT_REMINDER_SENT":
                handleReminderSent(event, correlationId);
                break;
            
            default:
                log.warn("GROUP_PAYMENT: Unknown event type - eventType={}, groupPaymentId={}",
                    event.getEventType(), event.getGroupPaymentId());
                handleGenericGroupPaymentEvent(event, correlationId);
        }

        recordInLedger(event, correlationId);

        updateAnalytics(event, correlationId);

        logAuditEntry(event, correlationId, startTime);

        log.info("GROUP_PAYMENT: Completed group payment processing pipeline - " +
                "eventId={}, groupPaymentId={}, eventType={}",
                event.getEventId(), event.getGroupPaymentId(), event.getEventType());
    }

    private void handleGroupPaymentCreated(GroupPaymentEvent event, String correlationId) {
        try {
            log.info("GROUP_PAYMENT: Processing GROUP_PAYMENT_CREATED - groupPaymentId={}, createdBy={}, amount={}",
                event.getGroupPaymentId(), event.getCreatedBy(), event.getTotalAmount());

            sendCreationNotifications(event, correlationId);

            log.info("GROUP_PAYMENT: GROUP_PAYMENT_CREATED processed - groupPaymentId={}",
                event.getGroupPaymentId());

        } catch (Exception e) {
            log.error("GROUP_PAYMENT: Failed to handle GROUP_PAYMENT_CREATED - groupPaymentId={}, error={}",
                event.getGroupPaymentId(), e.getMessage(), e);
        }
    }

    private void handleGroupPaymentUpdated(GroupPaymentEvent event, String correlationId) {
        try {
            log.info("GROUP_PAYMENT: Processing GROUP_PAYMENT_UPDATED - groupPaymentId={}, status={}",
                event.getGroupPaymentId(), event.getStatus());

            sendUpdateNotifications(event, correlationId);

            log.info("GROUP_PAYMENT: GROUP_PAYMENT_UPDATED processed - groupPaymentId={}",
                event.getGroupPaymentId());

        } catch (Exception e) {
            log.error("GROUP_PAYMENT: Failed to handle GROUP_PAYMENT_UPDATED - groupPaymentId={}, error={}",
                event.getGroupPaymentId(), e.getMessage(), e);
        }
    }

    private void handleParticipantPaid(GroupPaymentEvent event, String correlationId) {
        try {
            log.info("GROUP_PAYMENT: Processing PARTICIPANT_PAID - groupPaymentId={}",
                event.getGroupPaymentId());

            sendParticipantPaidNotifications(event, correlationId);

            checkAndNotifyIfFullyPaid(event, correlationId);

            log.info("GROUP_PAYMENT: PARTICIPANT_PAID processed - groupPaymentId={}",
                event.getGroupPaymentId());

        } catch (Exception e) {
            log.error("GROUP_PAYMENT: Failed to handle PARTICIPANT_PAID - groupPaymentId={}, error={}",
                event.getGroupPaymentId(), e.getMessage(), e);
        }
    }

    private void handleGroupPaymentSettled(GroupPaymentEvent event, String correlationId) {
        try {
            log.info("GROUP_PAYMENT: Processing GROUP_PAYMENT_SETTLED - groupPaymentId={}, amount={}",
                event.getGroupPaymentId(), event.getTotalAmount());

            sendSettlementNotifications(event, correlationId);

            log.info("GROUP_PAYMENT: GROUP_PAYMENT_SETTLED processed - groupPaymentId={}",
                event.getGroupPaymentId());

        } catch (Exception e) {
            log.error("GROUP_PAYMENT: Failed to handle GROUP_PAYMENT_SETTLED - groupPaymentId={}, error={}",
                event.getGroupPaymentId(), e.getMessage(), e);
        }
    }

    private void handleGroupPaymentCancelled(GroupPaymentEvent event, String correlationId) {
        try {
            log.info("GROUP_PAYMENT: Processing GROUP_PAYMENT_CANCELLED - groupPaymentId={}",
                event.getGroupPaymentId());

            sendCancellationNotifications(event, correlationId);

            log.info("GROUP_PAYMENT: GROUP_PAYMENT_CANCELLED processed - groupPaymentId={}",
                event.getGroupPaymentId());

        } catch (Exception e) {
            log.error("GROUP_PAYMENT: Failed to handle GROUP_PAYMENT_CANCELLED - groupPaymentId={}, error={}",
                event.getGroupPaymentId(), e.getMessage(), e);
        }
    }

    private void handleReminderSent(GroupPaymentEvent event, String correlationId) {
        try {
            log.info("GROUP_PAYMENT: Processing REMINDER_SENT - groupPaymentId={}",
                event.getGroupPaymentId());

            log.info("GROUP_PAYMENT: REMINDER_SENT processed - groupPaymentId={}",
                event.getGroupPaymentId());

        } catch (Exception e) {
            log.error("GROUP_PAYMENT: Failed to handle REMINDER_SENT - groupPaymentId={}, error={}",
                event.getGroupPaymentId(), e.getMessage(), e);
        }
    }

    private void handleGenericGroupPaymentEvent(GroupPaymentEvent event, String correlationId) {
        log.info("GROUP_PAYMENT: Processing generic group payment event - eventType={}, groupPaymentId={}",
            event.getEventType(), event.getGroupPaymentId());
    }

    private void sendCreationNotifications(GroupPaymentEvent event, String correlationId) {
        try {
            if (event.getParticipants() == null || event.getParticipants().isEmpty()) {
                log.warn("GROUP_PAYMENT: No participants to notify - groupPaymentId={}",
                    event.getGroupPaymentId());
                return;
            }

            for (GroupPaymentEvent.GroupPaymentParticipant participant : event.getParticipants()) {
                if (!participant.getUserId().equals(event.getCreatedBy())) {
                    GroupPaymentNotificationRequest request = GroupPaymentNotificationRequest.builder()
                        .userId(participant.getUserId())
                        .groupPaymentId(event.getGroupPaymentId())
                        .notificationType("GROUP_PAYMENT_CREATED")
                        .title(event.getTitle())
                        .creatorName(getCreatorName(event))
                        .amountOwed(participant.getAmountOwed())
                        .currency(event.getCurrency())
                        .dueDate(event.getDueDate())
                        .participantCount(event.getParticipants().size())
                        .message(buildCreationMessage(event, participant))
                        .channels(List.of("EMAIL", "PUSH_NOTIFICATION"))
                        .priority("MEDIUM")
                        .correlationId(correlationId)
                        .timestamp(Instant.now())
                        .build();

                    sendGroupPaymentNotificationWithRetry(request);
                }
            }

            sendCreatorConfirmationNotification(event, correlationId);

            log.info("GROUP_PAYMENT: Creation notifications sent - groupPaymentId={}, participantCount={}",
                event.getGroupPaymentId(), event.getParticipants().size());

        } catch (Exception e) {
            log.warn("GROUP_PAYMENT: Failed to send creation notifications - groupPaymentId={}, error={}",
                event.getGroupPaymentId(), e.getMessage());
        }
    }

    private void sendUpdateNotifications(GroupPaymentEvent event, String correlationId) {
        try {
            if (event.getParticipants() == null || event.getParticipants().isEmpty()) {
                return;
            }

            for (GroupPaymentEvent.GroupPaymentParticipant participant : event.getParticipants()) {
                GroupPaymentNotificationRequest request = GroupPaymentNotificationRequest.builder()
                    .userId(participant.getUserId())
                    .groupPaymentId(event.getGroupPaymentId())
                    .notificationType("GROUP_PAYMENT_UPDATED")
                    .title(event.getTitle())
                    .message(buildUpdateMessage(event))
                    .channels(List.of("PUSH_NOTIFICATION"))
                    .priority("LOW")
                    .correlationId(correlationId)
                    .timestamp(Instant.now())
                    .build();

                sendGroupPaymentNotificationWithRetry(request);
            }

            log.info("GROUP_PAYMENT: Update notifications sent - groupPaymentId={}",
                event.getGroupPaymentId());

        } catch (Exception e) {
            log.warn("GROUP_PAYMENT: Failed to send update notifications - groupPaymentId={}, error={}",
                event.getGroupPaymentId(), e.getMessage());
        }
    }

    private void sendParticipantPaidNotifications(GroupPaymentEvent event, String correlationId) {
        try {
            GroupPaymentNotificationRequest creatorRequest = GroupPaymentNotificationRequest.builder()
                .userId(event.getCreatedBy())
                .groupPaymentId(event.getGroupPaymentId())
                .notificationType("PARTICIPANT_PAID")
                .title(event.getTitle())
                .message(buildParticipantPaidMessage(event))
                .channels(List.of("PUSH_NOTIFICATION"))
                .priority("MEDIUM")
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .build();

            sendGroupPaymentNotificationWithRetry(creatorRequest);

            log.info("GROUP_PAYMENT: Participant paid notifications sent - groupPaymentId={}",
                event.getGroupPaymentId());

        } catch (Exception e) {
            log.warn("GROUP_PAYMENT: Failed to send participant paid notifications - groupPaymentId={}, error={}",
                event.getGroupPaymentId(), e.getMessage());
        }
    }

    private void checkAndNotifyIfFullyPaid(GroupPaymentEvent event, String correlationId) {
        if (event.getParticipants() == null) {
            return;
        }

        boolean allPaid = event.getParticipants().stream()
            .allMatch(p -> "PAID".equals(p.getPaymentStatus()));

        if (allPaid) {
            GroupPaymentNotificationRequest request = GroupPaymentNotificationRequest.builder()
                .userId(event.getCreatedBy())
                .groupPaymentId(event.getGroupPaymentId())
                .notificationType("GROUP_PAYMENT_FULLY_PAID")
                .title(event.getTitle())
                .message("All participants have paid their share!")
                .channels(List.of("EMAIL", "PUSH_NOTIFICATION"))
                .priority("HIGH")
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .build();

            sendGroupPaymentNotificationWithRetry(request);
        }
    }

    private void sendSettlementNotifications(GroupPaymentEvent event, String correlationId) {
        try {
            if (event.getParticipants() == null || event.getParticipants().isEmpty()) {
                return;
            }

            for (GroupPaymentEvent.GroupPaymentParticipant participant : event.getParticipants()) {
                GroupPaymentNotificationRequest request = GroupPaymentNotificationRequest.builder()
                    .userId(participant.getUserId())
                    .groupPaymentId(event.getGroupPaymentId())
                    .notificationType("GROUP_PAYMENT_SETTLED")
                    .title(event.getTitle())
                    .message(buildSettlementMessage(event))
                    .channels(List.of("EMAIL", "PUSH_NOTIFICATION"))
                    .priority("MEDIUM")
                    .correlationId(correlationId)
                    .timestamp(Instant.now())
                    .build();

                sendGroupPaymentNotificationWithRetry(request);
            }

            log.info("GROUP_PAYMENT: Settlement notifications sent - groupPaymentId={}",
                event.getGroupPaymentId());

        } catch (Exception e) {
            log.warn("GROUP_PAYMENT: Failed to send settlement notifications - groupPaymentId={}, error={}",
                event.getGroupPaymentId(), e.getMessage());
        }
    }

    private void sendCancellationNotifications(GroupPaymentEvent event, String correlationId) {
        try {
            if (event.getParticipants() == null || event.getParticipants().isEmpty()) {
                return;
            }

            for (GroupPaymentEvent.GroupPaymentParticipant participant : event.getParticipants()) {
                GroupPaymentNotificationRequest request = GroupPaymentNotificationRequest.builder()
                    .userId(participant.getUserId())
                    .groupPaymentId(event.getGroupPaymentId())
                    .notificationType("GROUP_PAYMENT_CANCELLED")
                    .title(event.getTitle())
                    .message(buildCancellationMessage(event))
                    .channels(List.of("PUSH_NOTIFICATION"))
                    .priority("MEDIUM")
                    .correlationId(correlationId)
                    .timestamp(Instant.now())
                    .build();

                sendGroupPaymentNotificationWithRetry(request);
            }

            log.info("GROUP_PAYMENT: Cancellation notifications sent - groupPaymentId={}",
                event.getGroupPaymentId());

        } catch (Exception e) {
            log.warn("GROUP_PAYMENT: Failed to send cancellation notifications - groupPaymentId={}, error={}",
                event.getGroupPaymentId(), e.getMessage());
        }
    }

    private void sendCreatorConfirmationNotification(GroupPaymentEvent event, String correlationId) {
        GroupPaymentNotificationRequest request = GroupPaymentNotificationRequest.builder()
            .userId(event.getCreatedBy())
            .groupPaymentId(event.getGroupPaymentId())
            .notificationType("GROUP_PAYMENT_CREATED_CONFIRMATION")
            .title(event.getTitle())
            .message("Your group payment has been created successfully!")
            .participantCount(event.getParticipants() != null ? event.getParticipants().size() : 0)
            .channels(List.of("PUSH_NOTIFICATION"))
            .priority("LOW")
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();

        sendGroupPaymentNotificationWithRetry(request);
    }

    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendGroupPaymentNotificationFallback")
    @Retry(name = "notification-service")
    @TimeLimiter(name = "notification-service")
    private void sendGroupPaymentNotificationWithRetry(GroupPaymentNotificationRequest request) {
        notificationServiceClient.sendGroupPaymentNotification(request);
    }

    private void sendGroupPaymentNotificationFallback(GroupPaymentNotificationRequest request, Exception e) {
        log.warn("GROUP_PAYMENT: Notification fallback - userId={}, groupPaymentId={}. " +
                "Notification will be sent via batch job.",
                request.getUserId(), request.getGroupPaymentId());
    }

    private void recordInLedger(GroupPaymentEvent event, String correlationId) {
        try {
            log.info("GROUP_PAYMENT: Recording in ledger - groupPaymentId={}, amount={}",
                event.getGroupPaymentId(), event.getTotalAmount());

            RecordGroupPaymentRequest request = RecordGroupPaymentRequest.builder()
                .groupPaymentId(event.getGroupPaymentId())
                .eventType(event.getEventType())
                .createdBy(event.getCreatedBy())
                .totalAmount(event.getTotalAmount())
                .currency(event.getCurrency())
                .status(event.getStatus())
                .participants(convertParticipantsForLedger(event.getParticipants()))
                .unifiedTransactionId(event.getUnifiedTransactionId())
                .metadata(event.getMetadata())
                .correlationId(correlationId)
                .timestamp(event.getTimestamp())
                .build();

            recordGroupPaymentInLedgerWithRetry(request);

            log.info("GROUP_PAYMENT: Successfully recorded in ledger - groupPaymentId={}",
                event.getGroupPaymentId());

        } catch (Exception e) {
            log.error("GROUP_PAYMENT: CRITICAL - Failed to record in ledger - " +
                    "groupPaymentId={}, error={}. Manual reconciliation required.",
                    event.getGroupPaymentId(), e.getMessage(), e);
            
            throw new ServiceIntegrationException(
                "CRITICAL: Failed to record group payment in ledger", e);
        }
    }

    @CircuitBreaker(name = "ledger-service", fallbackMethod = "recordGroupPaymentInLedgerFallback")
    @Retry(name = "ledger-service")
    @TimeLimiter(name = "ledger-service")
    private void recordGroupPaymentInLedgerWithRetry(RecordGroupPaymentRequest request) {
        ledgerServiceClient.recordGroupPayment(request);
    }

    private void recordGroupPaymentInLedgerFallback(RecordGroupPaymentRequest request, Exception e) {
        log.error("GROUP_PAYMENT: CRITICAL - Ledger recording failed after retries - " +
                "groupPaymentId={}. Escalating to manual reconciliation queue.",
                request.getGroupPaymentId());
        
        auditService.logCriticalLedgerFailure(
            "GROUP_PAYMENT_LEDGER_FAILED",
            request.getCreatedBy(),
            request.getGroupPaymentId(),
            request.getTotalAmount().toString(),
            "Failed to record group payment in ledger: " + e.getMessage()
        );
        
        throw new ServiceIntegrationException(
            "CRITICAL: Ledger recording failed - manual reconciliation required", e);
    }

    private void updateAnalytics(GroupPaymentEvent event, String correlationId) {
        try {
            log.info("GROUP_PAYMENT: Updating analytics - groupPaymentId={}",
                event.getGroupPaymentId());

            RecordGroupPaymentAnalyticsRequest request = RecordGroupPaymentAnalyticsRequest.builder()
                .groupPaymentId(event.getGroupPaymentId())
                .eventType(event.getEventType())
                .createdBy(event.getCreatedBy())
                .totalAmount(event.getTotalAmount())
                .currency(event.getCurrency())
                .status(event.getStatus())
                .splitType(event.getSplitType())
                .participantCount(event.getParticipants() != null ? event.getParticipants().size() : 0)
                .category(event.getCategory())
                .timestamp(event.getTimestamp())
                .correlationId(correlationId)
                .build();

            updateAnalyticsWithRetry(request);

            log.info("GROUP_PAYMENT: Successfully updated analytics - groupPaymentId={}",
                event.getGroupPaymentId());

        } catch (Exception e) {
            log.warn("GROUP_PAYMENT: Failed to update analytics (non-critical) - groupPaymentId={}, error={}",
                event.getGroupPaymentId(), e.getMessage());
        }
    }

    @CircuitBreaker(name = "analytics-service", fallbackMethod = "updateAnalyticsFallback")
    @Retry(name = "analytics-service")
    @TimeLimiter(name = "analytics-service")
    private void updateAnalyticsWithRetry(RecordGroupPaymentAnalyticsRequest request) {
        analyticsServiceClient.recordGroupPaymentAnalytics(request);
    }

    private void updateAnalyticsFallback(RecordGroupPaymentAnalyticsRequest request, Exception e) {
        log.warn("GROUP_PAYMENT: Analytics update fallback - groupPaymentId={}. " +
                "Analytics will be rebuilt from transaction logs.",
                request.getGroupPaymentId());
    }

    private void logAuditEntry(GroupPaymentEvent event, String correlationId, Instant startTime) {
        try {
            long processingTimeMs = Duration.between(startTime, Instant.now()).toMillis();
            
            auditService.logGroupPaymentEvent(
                event.getEventId(),
                event.getGroupPaymentId(),
                event.getEventType(),
                event.getCreatedBy(),
                event.getTotalAmount(),
                event.getCurrency(),
                event.getStatus(),
                event.getParticipants() != null ? event.getParticipants().size() : 0,
                processingTimeMs,
                correlationId,
                Map.of(
                    "splitType", event.getSplitType() != null ? event.getSplitType() : "N/A",
                    "category", event.getCategory() != null ? event.getCategory() : "N/A",
                    "hasReceipt", event.getReceiptImageUrl() != null,
                    "eventProcessedAt", Instant.now()
                )
            );

            log.debug("GROUP_PAYMENT: Audit entry logged - eventId={}, groupPaymentId={}, processingTimeMs={}",
                event.getEventId(), event.getGroupPaymentId(), processingTimeMs);

        } catch (Exception e) {
            log.error("GROUP_PAYMENT: Failed to log audit entry - eventId={}, error={}",
                event.getEventId(), e.getMessage(), e);
        }
    }

    private void validateEvent(GroupPaymentEvent event) {
        List<String> errors = new ArrayList<>();

        if (event == null) {
            throw new IllegalArgumentException("GroupPaymentEvent cannot be null");
        }

        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            errors.add("eventId is required");
        }

        if (event.getGroupPaymentId() == null || event.getGroupPaymentId().trim().isEmpty()) {
            errors.add("groupPaymentId is required");
        }

        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            errors.add("eventType is required");
        }

        if (event.getCreatedBy() == null || event.getCreatedBy().trim().isEmpty()) {
            errors.add("createdBy is required");
        }

        if (event.getTotalAmount() == null || event.getTotalAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            errors.add("totalAmount must be greater than zero");
        }

        if (event.getCurrency() == null || event.getCurrency().trim().isEmpty()) {
            errors.add("currency is required");
        }

        if (!errors.isEmpty()) {
            String errorMessage = "Invalid GroupPaymentEvent: " + String.join(", ", errors);
            log.error("GROUP_PAYMENT: Event validation failed - eventId={}, errors={}",
                event.getEventId(), errors);
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private List<Map<String, Object>> convertParticipantsForLedger(
            List<GroupPaymentEvent.GroupPaymentParticipant> participants) {
        if (participants == null) {
            return List.of();
        }

        return participants.stream()
            .map(p -> Map.of(
                "userId", p.getUserId(),
                "amountOwed", p.getAmountOwed().toString(),
                "amountPaid", p.getAmountPaid() != null ? p.getAmountPaid().toString() : "0",
                "paymentStatus", p.getPaymentStatus()
            ))
            .collect(Collectors.toList());
    }

    private String getCreatorName(GroupPaymentEvent event) {
        return event.getMetadata() != null && event.getMetadata().containsKey("creatorName") ?
            event.getMetadata().get("creatorName").toString() : "Someone";
    }

    private String buildCreationMessage(GroupPaymentEvent event, 
            GroupPaymentEvent.GroupPaymentParticipant participant) {
        return String.format("%s added you to a group payment '%s'. Your share: %s %s",
            getCreatorName(event), event.getTitle(), 
            participant.getAmountOwed(), event.getCurrency());
    }

    private String buildUpdateMessage(GroupPaymentEvent event) {
        return String.format("Group payment '%s' has been updated", event.getTitle());
    }

    private String buildParticipantPaidMessage(GroupPaymentEvent event) {
        return String.format("A participant has paid their share in '%s'", event.getTitle());
    }

    private String buildSettlementMessage(GroupPaymentEvent event) {
        return String.format("Group payment '%s' has been settled. Total: %s %s",
            event.getTitle(), event.getTotalAmount(), event.getCurrency());
    }

    private String buildCancellationMessage(GroupPaymentEvent event) {
        return String.format("Group payment '%s' has been cancelled", event.getTitle());
    }

    @KafkaListener(
        topics = "${kafka.topics.group-payment-events-dlt:group-payment-events-dlt}",
        groupId = "${kafka.consumer.group-id:payment-service-consumer-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDeadLetterTopic(
            @Payload GroupPaymentEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        log.error("GROUP_PAYMENT: DLT - Event processing failed after all retries - " +
                "eventId={}, groupPaymentId={}, eventType={}, error={}. Manual intervention required.",
                event.getEventId(), event.getGroupPaymentId(), event.getEventType(), exceptionMessage);

        auditService.logDeadLetterEvent(
            "GroupPaymentEvent",
            event.getEventId(),
            event.getCreatedBy(),
            exceptionMessage,
            Map.of(
                "groupPaymentId", event.getGroupPaymentId(),
                "eventType", event.getEventType(),
                "totalAmount", event.getTotalAmount().toString(),
                "status", event.getStatus(),
                "dltTopic", topic
            )
        );
    }
}