package com.waqiti.payment.events.consumers;

import com.waqiti.payment.domain.*;
import com.waqiti.payment.repository.*;
import com.waqiti.payment.service.*;
import com.waqiti.common.events.payment.GroupPaymentEvent;
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
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroupPaymentEventsConsumer {

    private final GroupPaymentRepository groupPaymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final LedgerServiceClient ledgerServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final EventProcessingTrackingService eventProcessingTrackingService;
    private final MeterRegistry meterRegistry;

    private Counter eventsProcessedCounter;
    private Counter eventsFailedCounter;
    private Counter groupPaymentStatusCounter;
    private Counter notificationsSentCounter;
    private Timer eventProcessingTimer;

    public GroupPaymentEventsConsumer(
            GroupPaymentRepository groupPaymentRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            LedgerServiceClient ledgerServiceClient,
            NotificationServiceClient notificationServiceClient,
            EventProcessingTrackingService eventProcessingTrackingService,
            MeterRegistry meterRegistry) {
        
        this.groupPaymentRepository = groupPaymentRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.ledgerServiceClient = ledgerServiceClient;
        this.notificationServiceClient = notificationServiceClient;
        this.eventProcessingTrackingService = eventProcessingTrackingService;
        this.meterRegistry = meterRegistry;

        initializeMetrics();
    }

    private void initializeMetrics() {
        this.eventsProcessedCounter = Counter.builder("group_payment_events_processed_total")
                .description("Total number of group payment events processed")
                .tag("consumer", "group-payment-consumer")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("group_payment_events_failed_total")
                .description("Total number of group payment events failed")
                .tag("consumer", "group-payment-consumer")
                .register(meterRegistry);

        this.groupPaymentStatusCounter = Counter.builder("group_payment_status_updates_total")
                .description("Total group payment status updates")
                .tag("consumer", "group-payment-consumer")
                .register(meterRegistry);

        this.notificationsSentCounter = Counter.builder("group_payment_notifications_sent_total")
                .description("Total notifications sent for group payments")
                .tag("consumer", "group-payment-consumer")
                .register(meterRegistry);

        this.eventProcessingTimer = Timer.builder("group_payment_event_processing_duration")
                .description("Time taken to process group payment events")
                .tag("consumer", "group-payment-consumer")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "${kafka.topics.group-payment-events:group-payment-events}",
            groupId = "${kafka.consumer.group-id:payment-group-payment-consumer-group}",
            containerFactory = "criticalFinancialKafkaListenerContainerFactory",
            concurrency = "${kafka.consumer.concurrency:5}"
    )
    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 30000),
            include = {ServiceIntegrationException.class, Exception.class},
            exclude = {IllegalArgumentException.class},
            dltTopicSuffix = "-dlt",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            autoCreateTopics = "true"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRED, timeout = 60, rollbackFor = Exception.class)
    public void handleGroupPaymentEvent(
            @Payload GroupPaymentEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = event.getCorrelationId();

        try {
            log.info("Processing group payment event: eventId={}, groupPaymentId={}, organizerId={}, " +
                    "eventType={}, participantsCount={}, totalAmount={}, topic={}, partition={}, offset={}, correlationId={}",
                    event.getEventId(), event.getGroupPaymentId(), event.getOrganizerId(), 
                    event.getEventType(), event.getTotalParticipants(), event.getTotalAmount(),
                    topic, partition, offset, correlationId);

            if (eventProcessingTrackingService.isEventAlreadyProcessed(event.getEventId(), "GROUP_PAYMENT")) {
                log.warn("Duplicate group payment event detected: eventId={}, correlationId={}. Skipping processing.",
                        event.getEventId(), correlationId);
                acknowledgment.acknowledge();
                return;
            }

            validateEvent(event);

            processGroupPaymentEvent(event, correlationId);

            recordLedgerEntries(event, correlationId);

            notifyParticipants(event, correlationId);

            eventProcessingTrackingService.markEventAsProcessed(
                    event.getEventId(),
                    "GROUP_PAYMENT",
                    "payment-service",
                    correlationId
            );

            eventsProcessedCounter.increment();
            Counter.builder("group_payment_event_types")
                    .tag("event_type", event.getEventType())
                    .register(meterRegistry)
                    .increment();

            acknowledgment.acknowledge();

            log.info("Successfully processed group payment event: eventId={}, groupPaymentId={}, correlationId={}",
                    event.getEventId(), event.getGroupPaymentId(), correlationId);

        } catch (Exception e) {
            eventsFailedCounter.increment();
            log.error("Failed to process group payment event: eventId={}, groupPaymentId={}, correlationId={}, error={}",
                    event.getEventId(), event.getGroupPaymentId(), correlationId, e.getMessage(), e);
            throw new RuntimeException("Group payment event processing failed", e);
        } finally {
            eventProcessingTimer.stop(sample);
        }
    }

    private void validateEvent(GroupPaymentEvent event) {
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID is required");
        }

        if (event.getGroupPaymentId() == null || event.getGroupPaymentId().trim().isEmpty()) {
            throw new IllegalArgumentException("Group payment ID is required");
        }

        if (event.getOrganizerId() == null || event.getOrganizerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Organizer ID is required");
        }

        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }

        if (event.getTotalAmount() == null || event.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total amount must be positive");
        }
    }

    @CircuitBreaker(name = "paymentService", fallbackMethod = "processGroupPaymentEventFallback")
    @Retry(name = "paymentService")
    @TimeLimiter(name = "paymentService")
    private void processGroupPaymentEvent(GroupPaymentEvent event, String correlationId) {
        log.debug("Processing group payment event: groupPaymentId={}, eventType={}, correlationId={}",
                event.getGroupPaymentId(), event.getEventType(), correlationId);

        switch (event.getEventType()) {
            case "CREATED":
                handleGroupPaymentCreated(event, correlationId);
                break;
            case "PARTICIPANT_JOINED":
                handleParticipantJoined(event, correlationId);
                break;
            case "PARTICIPANT_LEFT":
                handleParticipantLeft(event, correlationId);
                break;
            case "PAYMENT_INITIATED":
                handlePaymentInitiated(event, correlationId);
                break;
            case "PAYMENT_COMPLETED":
                handlePaymentCompleted(event, correlationId);
                break;
            case "PAYMENT_FAILED":
                handlePaymentFailed(event, correlationId);
                break;
            case "SPLIT_UPDATED":
                handleSplitUpdated(event, correlationId);
                break;
            case "CANCELLED":
                handleGroupPaymentCancelled(event, correlationId);
                break;
            case "COMPLETED":
                handleGroupPaymentFullyCompleted(event, correlationId);
                break;
            default:
                log.warn("Unknown group payment event type: {}", event.getEventType());
        }

        groupPaymentStatusCounter.increment();
    }

    private void processGroupPaymentEventFallback(GroupPaymentEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for group payment processing: groupPaymentId={}, correlationId={}, error={}",
                event.getGroupPaymentId(), correlationId, e.getMessage());
    }

    private void handleGroupPaymentCreated(GroupPaymentEvent event, String correlationId) {
        log.info("Creating group payment record: groupPaymentId={}, correlationId={}",
                event.getGroupPaymentId(), correlationId);

        GroupPayment groupPayment = GroupPayment.builder()
                .groupPaymentId(event.getGroupPaymentId())
                .organizerId(event.getOrganizerId())
                .title(event.getTitle())
                .description(event.getDescription())
                .totalAmount(event.getTotalAmount())
                .currency(event.getCurrency())
                .totalParticipants(event.getTotalParticipants())
                .splitType(event.getSplitType())
                .status("CREATED")
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

        groupPaymentRepository.save(groupPayment);
    }

    private void handleParticipantJoined(GroupPaymentEvent event, String correlationId) {
        log.info("Participant joined group payment: groupPaymentId={}, participantId={}, correlationId={}",
                event.getGroupPaymentId(), event.getParticipantId(), correlationId);

        Optional<GroupPayment> groupPaymentOpt = groupPaymentRepository
                .findByGroupPaymentId(event.getGroupPaymentId());

        if (groupPaymentOpt.isPresent()) {
            GroupPayment groupPayment = groupPaymentOpt.get();
            groupPayment.setTotalParticipants(groupPayment.getTotalParticipants() + 1);
            groupPayment.setUpdatedAt(LocalDateTime.now());
            groupPaymentRepository.save(groupPayment);
        }
    }

    private void handleParticipantLeft(GroupPaymentEvent event, String correlationId) {
        log.info("Participant left group payment: groupPaymentId={}, participantId={}, correlationId={}",
                event.getGroupPaymentId(), event.getParticipantId(), correlationId);

        Optional<GroupPayment> groupPaymentOpt = groupPaymentRepository
                .findByGroupPaymentId(event.getGroupPaymentId());

        if (groupPaymentOpt.isPresent()) {
            GroupPayment groupPayment = groupPaymentOpt.get();
            groupPayment.setTotalParticipants(Math.max(0, groupPayment.getTotalParticipants() - 1));
            groupPayment.setUpdatedAt(LocalDateTime.now());
            groupPaymentRepository.save(groupPayment);
        }
    }

    private void handlePaymentInitiated(GroupPaymentEvent event, String correlationId) {
        log.info("Payment initiated for group payment: groupPaymentId={}, participantId={}, amount={}, correlationId={}",
                event.getGroupPaymentId(), event.getParticipantId(), event.getParticipantAmount(), correlationId);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .groupPaymentId(event.getGroupPaymentId())
                .userId(event.getParticipantId())
                .amount(event.getParticipantAmount())
                .currency(event.getCurrency())
                .status("PENDING")
                .transactionType("GROUP_PAYMENT")
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

        paymentTransactionRepository.save(transaction);
    }

    private void handlePaymentCompleted(GroupPaymentEvent event, String correlationId) {
        log.info("Payment completed for group payment: groupPaymentId={}, participantId={}, amount={}, correlationId={}",
                event.getGroupPaymentId(), event.getParticipantId(), event.getParticipantAmount(), correlationId);

        Optional<GroupPayment> groupPaymentOpt = groupPaymentRepository
                .findByGroupPaymentId(event.getGroupPaymentId());

        if (groupPaymentOpt.isPresent()) {
            GroupPayment groupPayment = groupPaymentOpt.get();
            groupPayment.setPaidParticipants(groupPayment.getPaidParticipants() + 1);
            groupPayment.setCollectedAmount(
                    groupPayment.getCollectedAmount().add(event.getParticipantAmount())
            );
            groupPayment.setUpdatedAt(LocalDateTime.now());

            if (groupPayment.getPaidParticipants().equals(groupPayment.getTotalParticipants())) {
                groupPayment.setStatus("COMPLETED");
                groupPayment.setCompletedAt(LocalDateTime.now());
            }

            groupPaymentRepository.save(groupPayment);
        }
    }

    private void handlePaymentFailed(GroupPaymentEvent event, String correlationId) {
        log.warn("Payment failed for group payment: groupPaymentId={}, participantId={}, reason={}, correlationId={}",
                event.getGroupPaymentId(), event.getParticipantId(), event.getFailureReason(), correlationId);
    }

    private void handleSplitUpdated(GroupPaymentEvent event, String correlationId) {
        log.info("Split updated for group payment: groupPaymentId={}, newSplitType={}, correlationId={}",
                event.getGroupPaymentId(), event.getSplitType(), correlationId);

        Optional<GroupPayment> groupPaymentOpt = groupPaymentRepository
                .findByGroupPaymentId(event.getGroupPaymentId());

        if (groupPaymentOpt.isPresent()) {
            GroupPayment groupPayment = groupPaymentOpt.get();
            groupPayment.setSplitType(event.getSplitType());
            groupPayment.setUpdatedAt(LocalDateTime.now());
            groupPaymentRepository.save(groupPayment);
        }
    }

    private void handleGroupPaymentCancelled(GroupPaymentEvent event, String correlationId) {
        log.info("Group payment cancelled: groupPaymentId={}, reason={}, correlationId={}",
                event.getGroupPaymentId(), event.getCancellationReason(), correlationId);

        Optional<GroupPayment> groupPaymentOpt = groupPaymentRepository
                .findByGroupPaymentId(event.getGroupPaymentId());

        if (groupPaymentOpt.isPresent()) {
            GroupPayment groupPayment = groupPaymentOpt.get();
            groupPayment.setStatus("CANCELLED");
            groupPayment.setCancellationReason(event.getCancellationReason());
            groupPayment.setCancelledAt(LocalDateTime.now());
            groupPaymentRepository.save(groupPayment);
        }
    }

    private void handleGroupPaymentFullyCompleted(GroupPaymentEvent event, String correlationId) {
        log.info("Group payment fully completed: groupPaymentId={}, totalCollected={}, correlationId={}",
                event.getGroupPaymentId(), event.getTotalAmount(), correlationId);

        Optional<GroupPayment> groupPaymentOpt = groupPaymentRepository
                .findByGroupPaymentId(event.getGroupPaymentId());

        if (groupPaymentOpt.isPresent()) {
            GroupPayment groupPayment = groupPaymentOpt.get();
            groupPayment.setStatus("COMPLETED");
            groupPayment.setCompletedAt(LocalDateTime.now());
            groupPaymentRepository.save(groupPayment);
        }
    }

    @CircuitBreaker(name = "ledgerService", fallbackMethod = "recordLedgerEntriesFallback")
    @Retry(name = "ledgerService")
    @TimeLimiter(name = "ledgerService")
    private void recordLedgerEntries(GroupPaymentEvent event, String correlationId) {
        if (!"PAYMENT_COMPLETED".equals(event.getEventType())) {
            return;
        }

        log.debug("Recording ledger entries for group payment: groupPaymentId={}, correlationId={}",
                event.getGroupPaymentId(), correlationId);

        Map<String, Object> ledgerData = new HashMap<>();
        ledgerData.put("groupPaymentId", event.getGroupPaymentId());
        ledgerData.put("participantId", event.getParticipantId());
        ledgerData.put("organizerId", event.getOrganizerId());
        ledgerData.put("amount", event.getParticipantAmount());
        ledgerData.put("currency", event.getCurrency());
        ledgerData.put("transactionType", "GROUP_PAYMENT");
        ledgerData.put("correlationId", correlationId);

        ledgerServiceClient.recordGroupPaymentLedgerEntry(ledgerData, correlationId);

        log.debug("Ledger entries recorded for group payment: groupPaymentId={}, correlationId={}",
                event.getGroupPaymentId(), correlationId);
    }

    private void recordLedgerEntriesFallback(GroupPaymentEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for ledger entries: groupPaymentId={}, correlationId={}, error={}",
                event.getGroupPaymentId(), correlationId, e.getMessage());
    }

    @CircuitBreaker(name = "notificationService", fallbackMethod = "notifyParticipantsFallback")
    @Retry(name = "notificationService")
    @TimeLimiter(name = "notificationService")
    private void notifyParticipants(GroupPaymentEvent event, String correlationId) {
        log.debug("Notifying participants about group payment event: groupPaymentId={}, eventType={}, correlationId={}",
                event.getGroupPaymentId(), event.getEventType(), correlationId);

        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("groupPaymentId", event.getGroupPaymentId());
        notificationData.put("eventType", event.getEventType());
        notificationData.put("organizerId", event.getOrganizerId());
        notificationData.put("title", event.getTitle());
        notificationData.put("totalAmount", event.getTotalAmount());
        notificationData.put("currency", event.getCurrency());
        notificationData.put("participantId", event.getParticipantId());
        notificationData.put("participantAmount", event.getParticipantAmount());
        notificationData.put("notificationType", determineNotificationType(event.getEventType()));
        notificationData.put("correlationId", correlationId);

        notificationServiceClient.sendNotification(notificationData, correlationId);

        notificationsSentCounter.increment();

        log.debug("Participants notified about group payment event: groupPaymentId={}, eventType={}, correlationId={}",
                event.getGroupPaymentId(), event.getEventType(), correlationId);
    }

    private void notifyParticipantsFallback(GroupPaymentEvent event, String correlationId, Exception e) {
        log.error("Circuit breaker activated for participant notification: groupPaymentId={}, correlationId={}, error={}",
                event.getGroupPaymentId(), correlationId, e.getMessage());
    }

    private String determineNotificationType(String eventType) {
        switch (eventType) {
            case "CREATED":
                return "GROUP_PAYMENT_CREATED";
            case "PARTICIPANT_JOINED":
                return "GROUP_PAYMENT_PARTICIPANT_JOINED";
            case "PARTICIPANT_LEFT":
                return "GROUP_PAYMENT_PARTICIPANT_LEFT";
            case "PAYMENT_INITIATED":
                return "GROUP_PAYMENT_INITIATED";
            case "PAYMENT_COMPLETED":
                return "GROUP_PAYMENT_PARTICIPANT_PAID";
            case "PAYMENT_FAILED":
                return "GROUP_PAYMENT_FAILED";
            case "SPLIT_UPDATED":
                return "GROUP_PAYMENT_SPLIT_UPDATED";
            case "CANCELLED":
                return "GROUP_PAYMENT_CANCELLED";
            case "COMPLETED":
                return "GROUP_PAYMENT_COMPLETED";
            default:
                return "GROUP_PAYMENT_UPDATE";
        }
    }
}