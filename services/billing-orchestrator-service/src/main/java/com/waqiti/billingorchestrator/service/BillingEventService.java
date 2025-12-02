package com.waqiti.billingorchestrator.service;

import com.waqiti.billingorchestrator.entity.BillingCycle;
import com.waqiti.billingorchestrator.entity.BillingEvent;
import com.waqiti.billingorchestrator.repository.BillingEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing billing events (audit trail)
 *
 * PRODUCTION-READY with comprehensive event tracking
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BillingEventService {

    private final BillingEventRepository billingEventRepository;

    /**
     * Create and persist a billing event
     *
     * @param cycle Billing cycle
     * @param eventType Event type
     * @param description Event description
     * @return Created event
     */
    @Transactional
    public BillingEvent createEvent(BillingCycle cycle, BillingEvent.EventType eventType, String description) {
        log.debug("Creating billing event: type={}, cycle={}", eventType, cycle.getId());

        BillingEvent event = BillingEvent.builder()
                .billingCycle(cycle)
                .eventType(eventType)
                .eventTimestamp(LocalDateTime.now())
                .description(description)
                .systemGenerated(true)
                .build();

        event = billingEventRepository.save(event);

        log.info("Created billing event: id={}, type={}, cycle={}", event.getId(), eventType, cycle.getId());
        return event;
    }

    /**
     * Create event with additional metadata
     */
    @Transactional
    public BillingEvent createEvent(BillingCycle cycle, BillingEvent.EventType eventType,
                                   String description, String performedBy) {
        BillingEvent event = BillingEvent.builder()
                .billingCycle(cycle)
                .eventType(eventType)
                .eventTimestamp(LocalDateTime.now())
                .description(description)
                .performedBy(performedBy)
                .systemGenerated(false)
                .build();

        return billingEventRepository.save(event);
    }

    /**
     * Create payment event
     */
    @Transactional
    public BillingEvent createPaymentEvent(BillingCycle cycle, BillingEvent.EventType eventType,
                                          String amount, String currency, String description) {
        return billingEventRepository.save(
            BillingEvent.createPaymentEvent(cycle, eventType, amount, currency, description)
        );
    }

    /**
     * Create error event
     */
    @Transactional
    public BillingEvent createErrorEvent(BillingCycle cycle, String errorCode, String errorMessage) {
        return billingEventRepository.save(
            BillingEvent.createErrorEvent(cycle, BillingEvent.EventType.ERROR_OCCURRED, errorCode, errorMessage)
        );
    }

    /**
     * Create notification event
     */
    @Transactional
    public BillingEvent createNotificationEvent(BillingCycle cycle, String notificationType,
                                               String channel, String recipient, boolean sent) {
        return billingEventRepository.save(
            BillingEvent.createNotificationEvent(cycle, notificationType, channel, recipient, sent)
        );
    }

    /**
     * Get events for billing cycle
     */
    @Transactional(readOnly = true)
    public List<BillingEvent> getEventsByCycle(UUID cycleId) {
        return billingEventRepository.findByBillingCycleIdOrderByEventTimestampDesc(cycleId);
    }

    /**
     * Get events requiring retry
     */
    @Transactional(readOnly = true)
    public List<BillingEvent> getEventsRequiringRetry() {
        return billingEventRepository.findEventsRequiringRetry(LocalDateTime.now());
    }

    /**
     * Get events requiring manual intervention
     */
    @Transactional(readOnly = true)
    public List<BillingEvent> getEventsRequiringManualIntervention(LocalDateTime since) {
        return billingEventRepository.findEventsRequiringManualIntervention(since);
    }

    /**
     * Schedule event retry
     */
    @Transactional
    public void scheduleRetry(UUID eventId) {
        billingEventRepository.findById(eventId).ifPresent(event -> {
            if (event.isRetryable()) {
                event.scheduleRetry();
                billingEventRepository.save(event);
                log.info("Scheduled retry for event: id={}, retryCount={}, nextRetryAt={}",
                        eventId, event.getRetryCount(), event.getNextRetryAt());
            }
        });
    }
}
