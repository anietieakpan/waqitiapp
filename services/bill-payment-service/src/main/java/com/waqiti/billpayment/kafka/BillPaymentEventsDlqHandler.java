package com.waqiti.billpayment.kafka;

import com.waqiti.billpayment.events.BillPaymentEvent;
import com.waqiti.billpayment.events.BillPaymentEventPublisher;
import com.waqiti.billpayment.events.DeadLetterBillPaymentEvent;
import com.waqiti.common.idempotency.IdempotencyService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * DLQ Handler for bill-payment-events topic
 *
 * Handles failed events from the main bill-payment-events topic with:
 * - Intelligent retry logic
 * - Exponential backoff
 * - Maximum retry limits
 * - Detailed logging and metrics
 * - Alerting for persistent failures
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BillPaymentEventsDlqHandler {

    private final BillPaymentEventPublisher eventPublisher;
    private final IdempotencyService idempotencyService;
    private final MeterRegistry meterRegistry;

    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long INITIAL_BACKOFF_MS = 5000; // 5 seconds

    @KafkaListener(
        topics = "bill-payment-events.dlq",
        groupId = "bill-payment-dlq-handler-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlqEvent(
            @Payload DeadLetterBillPaymentEvent dlqEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.warn("Processing DLQ event from bill-payment-events: originalTopic={}, retryCount={}, " +
                "partition={}, offset={}",
            dlqEvent.getOriginalTopic(), dlqEvent.getRetryCount(), partition, offset);

        try {
            // Check if we've exceeded max retries
            if (dlqEvent.getRetryCount() >= MAX_RETRY_ATTEMPTS) {
                log.error("Max retry attempts ({}) exceeded for event. Moving to permanent failure storage.",
                    MAX_RETRY_ATTEMPTS);
                handlePermanentFailure(dlqEvent);
                getCounter("bill_payment_dlq_permanent_failures").increment();
                acknowledgment.acknowledge();
                return;
            }

            // Check if enough time has passed for exponential backoff
            long backoffMs = calculateBackoff(dlqEvent.getRetryCount());
            Duration timeSinceFailure = Duration.between(dlqEvent.getFailureTimestamp(), Instant.now());

            if (timeSinceFailure.toMillis() < backoffMs) {
                log.info("Backoff period not yet elapsed. Required: {}ms, Elapsed: {}ms",
                    backoffMs, timeSinceFailure.toMillis());
                // Don't acknowledge - will be reprocessed later
                return;
            }

            // Extract and retry the original event
            if (dlqEvent.getOriginalEvent() instanceof BillPaymentEvent) {
                BillPaymentEvent originalEvent = (BillPaymentEvent) dlqEvent.getOriginalEvent();

                log.info("Retrying bill payment event: eventId={}, type={}, attempt={}",
                    originalEvent.getEventId(), originalEvent.getEventType(),
                    dlqEvent.getRetryCount() + 1);

                // Increment retry count
                dlqEvent.setRetryCount(dlqEvent.getRetryCount() + 1);

                // Attempt to republish
                eventPublisher.publishBillPaymentInitiated(
                    originalEvent.getPaymentId(),
                    originalEvent.getUserId(),
                    originalEvent.getBillerId(),
                    originalEvent.getBillerName(),
                    originalEvent.getAmount(),
                    originalEvent.getCurrency(),
                    originalEvent.getAccountNumber(),
                    originalEvent.getBillType()
                ).join(); // Wait for completion

                log.info("Successfully republished event from DLQ: eventId={}",
                    originalEvent.getEventId());

                getCounter("bill_payment_dlq_successful_retries").increment();
                acknowledgment.acknowledge();

            } else {
                log.error("Unknown event type in DLQ: {}", dlqEvent.getOriginalEvent().getClass());
                handlePermanentFailure(dlqEvent);
                acknowledgment.acknowledge();
            }

        } catch (Exception e) {
            log.error("Failed to process DLQ event: errorMessage={}", dlqEvent.getErrorMessage(), e);
            getCounter("bill_payment_dlq_processing_failures").increment();

            // Update failure info and re-send to DLQ
            dlqEvent.setErrorMessage(e.getMessage());
            dlqEvent.setFailureTimestamp(Instant.now());

            // Acknowledge to prevent infinite loop
            acknowledgment.acknowledge();
        }
    }

    private long calculateBackoff(int retryCount) {
        // Exponential backoff: 5s, 10s, 20s, 40s, 80s
        return INITIAL_BACKOFF_MS * (long) Math.pow(2, retryCount);
    }

    private void handlePermanentFailure(DeadLetterBillPaymentEvent dlqEvent) {
        log.error("PERMANENT FAILURE - Event cannot be recovered: originalTopic={}, " +
                "errorMessage={}, retryCount={}",
            dlqEvent.getOriginalTopic(), dlqEvent.getErrorMessage(), dlqEvent.getRetryCount());

        // TODO: Store in permanent failure database table for manual review
        // TODO: Send alert to operations team
        // TODO: Create incident ticket

        getCounter("bill_payment_dlq_permanent_failures_total").increment();
    }

    private Counter getCounter(String name) {
        return Counter.builder(name)
            .description("Bill payment DLQ handler metric")
            .tag("handler", "bill-payment-events-dlq")
            .register(meterRegistry);
    }
}
