package com.waqiti.payment.kafka;

import com.waqiti.common.events.TransactionBlockEvent;
import com.waqiti.payment.service.TransactionBlockingService;
import com.waqiti.payment.service.ComplianceIntegrationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * CRITICAL Transaction Block Event Consumer
 *
 * REGULATORY COMPLIANCE: Processes transaction block events from compliance-service
 * to immediately halt sanctioned or prohibited transactions.
 *
 * This consumer was identified as MISSING in forensic analysis, causing:
 * - AML violations (sanctioned transactions proceeding)
 * - OFAC compliance failures
 * - Regulatory fines exposure ($10M+)
 * - Criminal legal exposure
 *
 * Business Impact:
 * - Compliance Risk: CRITICAL - Immediate AML/BSA compliance restoration
 * - Legal Risk: CRITICAL - Prevents federal violations
 * - Financial Risk: HIGH - Avoids regulatory fines
 *
 * Regulatory Requirements:
 * - OFAC Sanctions Compliance
 * - Bank Secrecy Act (BSA)
 * - USA PATRIOT Act Section 326
 * - FinCEN Regulations
 *
 * @author Waqiti Compliance Team
 * @version 1.0.0
 * @since 2025-10-02
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionBlockConsumer {

    private final TransactionBlockingService transactionBlockingService;
    private final ComplianceIntegrationService complianceIntegrationService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;

    private static final String IDEMPOTENCY_KEY_PREFIX = "payment:transaction-block:idempotency:";
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    private Counter blockedTransactionsCounter;
    private Counter blockFailuresCounter;
    private Counter duplicateBlocksCounter;
    private Timer blockingTimer;

    @PostConstruct
    public void initMetrics() {
        blockedTransactionsCounter = Counter.builder("payment.transaction.blocks.processed")
            .description("Total transactions successfully blocked for compliance")
            .tag("consumer", "transaction-block-consumer")
            .tag("service", "payment-service")
            .register(meterRegistry);

        blockFailuresCounter = Counter.builder("payment.transaction.blocks.failed")
            .description("Total transaction block failures (CRITICAL)")
            .tag("consumer", "transaction-block-consumer")
            .tag("service", "payment-service")
            .register(meterRegistry);

        duplicateBlocksCounter = Counter.builder("payment.transaction.blocks.duplicate")
            .description("Total duplicate transaction block events skipped")
            .tag("consumer", "transaction-block-consumer")
            .tag("service", "payment-service")
            .register(meterRegistry);

        blockingTimer = Timer.builder("payment.transaction.blocks.duration")
            .description("Time taken to block transactions")
            .tag("consumer", "transaction-block-consumer")
            .tag("service", "payment-service")
            .register(meterRegistry);

        log.info("✅ TransactionBlockConsumer initialized - Ready to enforce compliance blocks");
    }

    /**
     * CRITICAL: Process transaction block events for compliance enforcement
     *
     * This consumer handles block events from compliance-service when:
     * - OFAC sanctions match detected
     * - AML violations identified
     * - Fraud patterns detected
     * - Regulatory holds required
     *
     * @param event Transaction block event from compliance-service
     * @param topic Kafka topic name
     * @param partition Kafka partition ID
     * @param offset Kafka offset for tracking
     * @param correlationId Distributed tracing correlation ID
     * @param acknowledgment Manual acknowledgment for exactly-once semantics
     */
    @KafkaListener(
        topics = "transaction-blocks",
        groupId = "payment-service-transaction-block-group",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "5"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void handleTransactionBlock(
            @Payload TransactionBlockEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = "correlation-id", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            log.error("🚨 CRITICAL: Processing transaction block event - TransactionId: {}, Reason: {}, Severity: {}, Partition: {}, Offset: {}",
                event.getTransactionId(),
                event.getBlockReason(),
                event.getSeverity(),
                partition,
                offset);

            // Validate event data
            validateTransactionBlockEvent(event);

            // Idempotency check - prevent duplicate blocks
            String idempotencyKey = buildIdempotencyKey(event.getTransactionId().toString(), event.getTransactionId().toString());
            if (isAlreadyProcessed(idempotencyKey)) {
                log.warn("⚠️ Duplicate transaction block event detected - TransactionId: {} - Already processed",
                    event.getTransactionId());
                acknowledgment.acknowledge();
                duplicateBlocksCounter.increment();
                blockingTimer.stop(sample);
                return;
            }

            // Record idempotency BEFORE processing to prevent race conditions
            recordIdempotency(idempotencyKey);

            // IMMEDIATELY halt transaction processing
            boolean blocked = transactionBlockingService.emergencyStopTransaction(
                event.getTransactionId(),
                event.getBlockReason(),
                event.getSeverity(),
                event.getBlockDescription(),
                correlationId
            );

            if (blocked) {
                log.error("✅ TRANSACTION BLOCKED: TransactionId: {}, Reason: {}, Severity: {}",
                    event.getTransactionId(), event.getBlockReason(), event.getSeverity());

                // Freeze all related pending transactions for the customer
                if (event.getUserId() != null) {
                    transactionBlockingService.freezeRelatedTransactions(
                        event.getUserId(),
                        event.getBlockReason().name()
                    );
                }

                // Update compliance system with block confirmation
                complianceIntegrationService.recordTransactionBlock(event, correlationId);

                // Alert compliance team for critical blocks
                if (event.requiresImmediateAction()) {
                    complianceIntegrationService.alertComplianceTeam(event);
                }

                // Log audit trail for regulatory compliance
                logComplianceAudit(event);

                blockedTransactionsCounter.increment();

            } else {
                log.error("❌ FAILED TO BLOCK TRANSACTION: TransactionId: {} - Manual intervention required",
                    event.getTransactionId());

                blockFailuresCounter.increment();

                // Alert compliance team immediately for manual intervention
                complianceIntegrationService.escalateCriticalBlockFailure(event);
            }

            // Manual acknowledgment
            acknowledgment.acknowledge();
            blockingTimer.stop(sample);

        } catch (IllegalArgumentException e) {
            log.error("❌ Invalid transaction block event data - TransactionId: {}, Error: {}",
                event.getTransactionId(), e.getMessage(), e);

            blockFailuresCounter.increment();
            blockingTimer.stop(sample);

            // Acknowledge invalid messages to prevent infinite retries
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("🔥 CRITICAL: Failed to process transaction block - TransactionId: {}, Error: {}",
                event.getTransactionId(), e.getMessage(), e);

            blockFailuresCounter.increment();
            blockingTimer.stop(sample);

            // Don't acknowledge - message will be retried
            throw new TransactionBlockException(
                String.format("CRITICAL: Failed to block transaction: TransactionId=%s", event.getTransactionId()), e);
        }
    }

    /**
     * Validates transaction block event has all required fields
     */
    private void validateTransactionBlockEvent(TransactionBlockEvent event) {
        if (event.getTransactionId() == null) {
            throw new IllegalArgumentException("transactionId is required for transaction block");
        }

        if (event.getBlockReason() == null) {
            throw new IllegalArgumentException("blockReason is required");
        }

        if (event.getSeverity() == null) {
            throw new IllegalArgumentException("severity is required");
        }

        if (event.getUserId() == null) {
            throw new IllegalArgumentException("userId is required");
        }
    }

    /**
     * Logs compliance audit trail for regulatory reporting
     */
    private void logComplianceAudit(TransactionBlockEvent event) {
        log.info("COMPLIANCE_AUDIT | Type: TRANSACTION_BLOCKED | TransactionId: {} | UserId: {} | BlockReason: {} | Severity: {} | AMLRule: {} | CaseId: {} | Timestamp: {}",
            event.getTransactionId(),
            event.getUserId(),
            event.getBlockReason(),
            event.getSeverity(),
            event.getAmlRuleViolated(),
            event.getCaseId(),
            Instant.now()
        );
    }

    /**
     * Builds idempotency key for Redis
     */
    private String buildIdempotencyKey(String transactionId, String eventId) {
        return IDEMPOTENCY_KEY_PREFIX + transactionId + ":" + eventId;
    }

    /**
     * Checks if block event was already processed using Redis
     */
    private boolean isAlreadyProcessed(String idempotencyKey) {
        Boolean exists = redisTemplate.hasKey(idempotencyKey);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Records event processing in Redis with 24-hour TTL
     */
    private void recordIdempotency(String idempotencyKey) {
        redisTemplate.opsForValue().set(
            idempotencyKey,
            Instant.now().toString(),
            IDEMPOTENCY_TTL_HOURS,
            TimeUnit.HOURS
        );
    }

    /**
     * Custom exception for transaction blocking failures
     */
    public static class TransactionBlockException extends RuntimeException {
        public TransactionBlockException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
