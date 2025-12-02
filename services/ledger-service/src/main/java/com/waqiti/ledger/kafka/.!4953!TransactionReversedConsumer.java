package com.waqiti.ledger.kafka;

import com.waqiti.ledger.service.LedgerService;
import com.waqiti.ledger.service.DoubleEntryBookkeepingService;
import com.waqiti.ledger.model.LedgerEntry;
import com.waqiti.ledger.model.LedgerEntryType;
import com.waqiti.common.events.TransactionReversedEvent;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL FINANCIAL CONSUMER - Transaction Reversal Handler
 *
 * This consumer was MISSING causing ledger inconsistencies.
 *
 * Without this consumer:
 * - Transaction reversals are not recorded in the ledger
 * - Double-entry bookkeeping is broken
 * - Financial statements become inaccurate
 * - Audit trail has gaps
 * - Regulatory compliance fails
 * - Account balances do not reconcile
 *
 * IDEMPOTENCY PROTECTION:
 * - Uses Redis-backed IdempotencyService for distributed idempotency
 * - 90-day TTL for reversal tracking (regulatory requirement)
 * - CRITICAL: Prevents duplicate reversal entries in ledger
 *
 * Features:
 * - Double-entry bookkeeping for reversals
 * - Automatic ledger entry creation
 * - Account balance adjustment
 * - Audit trail maintenance
 * - Reversal reason tracking
 * - Multi-currency support
 * - Regulatory compliance reporting
 *
 * FINANCIAL ACCURACY DEPENDS ON THIS CONSUMER - DO NOT DISABLE
 *
 * @author Waqiti Ledger Team
 * @version 1.0.0
 * @since 2024-11-10
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionReversedConsumer {

    private final LedgerService ledgerService;
    private final DoubleEntryBookkeepingService bookkeepingService;
    private final IdempotencyService idempotencyService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuditService auditService;

    @KafkaListener(
        topics = "transaction-reversed",
        groupId = "ledger-transaction-reversal-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processTransactionReversal(
            @Payload TransactionReversedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        String transactionId = event.getTransactionId();
        String reversalId = event.getReversalId();
        String correlationId = String.format("transaction-reversal-%s-%s-p%d-o%d",
            transactionId, reversalId, partition, offset);

        // CRITICAL IDEMPOTENCY CHECK
        String idempotencyKey = "transaction-reversal:" + transactionId + ":" + reversalId + ":" + event.getEventId();
        UUID operationId = UUID.randomUUID();
        Duration ttl = Duration.ofDays(90); // 90-day retention for regulatory compliance

        try {
            if (!idempotencyService.startOperation(idempotencyKey, operationId, ttl)) {
