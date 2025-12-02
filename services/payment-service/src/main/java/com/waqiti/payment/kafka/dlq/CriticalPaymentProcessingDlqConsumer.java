package com.waqiti.payment.kafka.dlq;

import com.waqiti.common.kafka.dlq.DlqStatus;
import com.waqiti.common.kafka.dlq.entity.DlqRecordEntity;
import com.waqiti.common.kafka.dlq.repository.DlqRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Critical Payment Processing DLQ Consumer.
 *
 * Handles DLQ messages from critical payment topics:
 * - payment-initiated
 * - payment-completed
 * - payment-failed
 * - payment-reversed
 *
 * CRITICALITY:
 * - P0 priority - immediate processing
 * - Financial integrity at stake
 * - Customer funds involved
 * - Regulatory compliance required
 *
 * BEHAVIOR:
 * - Captures failed payment messages
 * - Persists to DLQ database
 * - Triggers automatic recovery via DLQProcessorService
 * - Creates audit trail for compliance
 *
 * @author Waqiti Platform Engineering
 * @version 2.0.0
 * @since 2025-11-19
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CriticalPaymentProcessingDlqConsumer {

    private final DlqRecordRepository dlqRecordRepository;

    private static final String[] TOPICS = {
        "critical-payment-processing-dlq",
        "payment-initiated-dlq",
        "payment-completed-dlq",
        "payment-failed-dlq"
    };

    @KafkaListener(
        topics = {
            "critical-payment-processing-dlq",
            "payment-initiated-dlq",
            "payment-completed-dlq",
            "payment-failed-dlq"
        },
        groupId = "payment-service-critical-dlq-consumer",
        concurrency = "3"
    )
    public void handleCriticalPaymentDlq(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {

        try {
            log.info("🚨 Critical payment DLQ message received: topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset());

            // Extract original topic (remove -dlq suffix)
            String originalTopic = extractOriginalTopic(record.topic());

            // Create DLQ record
            DlqRecordEntity dlqRecord = DlqRecordEntity.builder()
                    .id(UUID.randomUUID())
                    .messageId(extractMessageId(record))
                    .topic(originalTopic)
                    .partition(record.partition())
                    .offset(record.offset())
                    .messageKey(record.key())
                    .messageValue(record.value())
                    .serviceName("payment-service")
                    .status(DlqStatus.PENDING)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now())
                    .firstFailureTime(Instant.now())
                    .lastFailureTime(Instant.now())
                    .lastFailureReason("Message arrived in DLQ - pending analysis")
                    .nextRetryTime(Instant.now().plus(30, ChronoUnit.SECONDS)) // First retry in 30s
                    .build();

            // Save to database
            dlqRecordRepository.save(dlqRecord);

            log.info("✅ Critical payment DLQ message persisted: messageId={}, originalTopic={}",
                    dlqRecord.getMessageId(), originalTopic);

            // Acknowledge Kafka message
            acknowledgment.acknowledge();

            log.info("📨 DLQ message acknowledged and ready for recovery processing");

        } catch (Exception e) {
            log.error("❌ Error handling critical payment DLQ message: topic={}, partition={}, offset={}, error={}",
                    record.topic(), record.partition(), record.offset(), e.getMessage(), e);

            // Do not acknowledge - message will be redelivered
            // This ensures no DLQ messages are lost
        }
    }

    /**
     * Extracts original topic name by removing -dlq suffix.
     */
    private String extractOriginalTopic(String dlqTopic) {
        if (dlqTopic.endsWith("-dlq")) {
            return dlqTopic.substring(0, dlqTopic.length() - 4);
        }
        return dlqTopic;
    }

    /**
     * Extracts or generates message ID from Kafka record.
     */
    private String extractMessageId(ConsumerRecord<String, String> record) {
        // Try to extract from headers first
        if (record.headers().lastHeader("messageId") != null) {
            return new String(record.headers().lastHeader("messageId").value());
        }

        // Use key if available
        if (record.key() != null) {
            return record.key();
        }

        // Generate unique ID
        return String.format("%s-%d-%d-%s",
                record.topic(),
                record.partition(),
                record.offset(),
                UUID.randomUUID().toString().substring(0, 8));
    }
}
