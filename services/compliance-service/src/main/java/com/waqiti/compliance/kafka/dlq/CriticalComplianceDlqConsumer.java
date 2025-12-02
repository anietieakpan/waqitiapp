package com.waqiti.compliance.kafka.dlq;

import com.waqiti.common.kafka.dlq.DlqStatus;
import com.waqiti.common.kafka.dlq.entity.DlqRecordEntity;
import com.waqiti.common.kafka.dlq.repository.DlqRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Critical Compliance DLQ Consumer.
 *
 * Consumes failed messages from critical compliance Kafka DLQ topics and persists them
 * for automatic recovery via the DLQProcessorService.
 *
 * Handles DLQ topics for:
 * - Suspicious Activity Reports (SAR) filing
 * - Currency Transaction Reports (CTR) filing
 * - OFAC sanctions screening
 * - AML alerts and investigations
 * - Regulatory reporting
 * - Fraud detection events
 *
 * Recovery Strategy:
 * - Messages are persisted to dlq_records table with PENDING status
 * - DLQProcessorService (runs every 2min) automatically processes pending messages
 * - RecoveryStrategyFactory routes to appropriate strategy
 * - No manual intervention required unless max retries exceeded
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-11-20
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CriticalComplianceDlqConsumer {

    private final DlqRecordRepository dlqRecordRepository;

    private static final String SERVICE_NAME = "compliance-service";

    /**
     * Critical compliance topics that are routed to DLQ.
     */
    private static final List<String> CRITICAL_TOPICS = Arrays.asList(
            "sar-filing",
            "ctr-filing",
            "ofac-screening",
            "aml-alert",
            "aml-investigation",
            "regulatory-reporting",
            "fraud-detection",
            "sanctions-screening",
            "pep-screening",
            "edd-required",
            "compliance-breach"
    );

    /**
     * Kafka listener for critical compliance DLQ topics.
     *
     * Topics monitored:
     * - sar-filing-dlq: Failed SAR filing messages
     * - ctr-filing-dlq: Failed CTR filing messages
     * - ofac-screening-dlq: Failed OFAC screening messages
     * - aml-alert-dlq: Failed AML alert messages
     * - aml-investigation-dlq: Failed AML investigation messages
     * - regulatory-reporting-dlq: Failed regulatory reporting messages
     * - fraud-detection-dlq: Failed fraud detection messages
     * - sanctions-screening-dlq: Failed sanctions screening messages
     * - pep-screening-dlq: Failed PEP screening messages
     * - edd-required-dlq: Failed enhanced due diligence messages
     * - compliance-breach-dlq: Failed compliance breach messages
     *
     * Consumer Configuration:
     * - Group ID: compliance-service-critical-dlq-consumer
     * - Concurrency: 3 (handles 3 partitions in parallel)
     * - Auto-offset reset: earliest (don't miss any DLQ messages)
     * - Manual acknowledgment: Only ack after successful persistence
     */
    @KafkaListener(
            topics = {
                    "sar-filing-dlq",
                    "ctr-filing-dlq",
                    "ofac-screening-dlq",
                    "aml-alert-dlq",
                    "aml-investigation-dlq",
                    "regulatory-reporting-dlq",
                    "fraud-detection-dlq",
                    "sanctions-screening-dlq",
                    "pep-screening-dlq",
                    "edd-required-dlq",
                    "compliance-breach-dlq"
            },
            groupId = "compliance-service-critical-dlq-consumer",
            concurrency = "3",
            properties = {
                    "auto.offset.reset=earliest"
            }
    )
    public void handleCriticalComplianceDlq(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        String topic = record.topic();
        String key = record.key();
        String value = record.value();

        log.info("Received critical compliance DLQ message: topic={}, partition={}, offset={}, key={}",
                topic, record.partition(), record.offset(), key);

        try {
            // 1. Extract message ID from headers or generate
            String messageId = extractMessageId(record);

            // 2. Remove -dlq suffix from topic to get original topic
            String originalTopic = topic.replace("-dlq", "");

            // 3. Extract error information from headers
            String errorReason = extractHeader(record, "error-reason");
            String errorMessage = extractHeader(record, "error-message");
            String stackTrace = extractHeader(record, "stack-trace");

            // 4. Build DLQ record entity
            DlqRecordEntity dlqRecord = DlqRecordEntity.builder()
                    .id(UUID.randomUUID())
                    .messageId(messageId)
                    .topic(originalTopic)
                    .partition(record.partition())
                    .offset(record.offset())
                    .messageKey(key)
                    .messageValue(value)
                    .headers(extractAllHeaders(record))
                    .status(DlqStatus.PENDING)
                    .retryCount(0)
                    .maxRetries(10)
                    .nextRetryTime(LocalDateTime.now()) // Immediate first retry
                    .lastFailureTime(LocalDateTime.now())
                    .lastFailureReason(errorReason != null ? errorReason : "Unknown error")
                    .errorMessage(errorMessage)
                    .stackTrace(stackTrace)
                    .serviceName(SERVICE_NAME)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            // 5. Persist to database (triggers automatic recovery via DLQProcessorService)
            dlqRecordRepository.save(dlqRecord);

            log.info("Persisted critical compliance DLQ record: messageId={}, topic={}, status={}",
                    messageId, originalTopic, DlqStatus.PENDING);

            // 6. Acknowledge Kafka message (safe to ack since persisted to database)
            acknowledgment.acknowledge();

            log.debug("Acknowledged critical compliance DLQ message: topic={}, offset={}",
                    topic, record.offset());

        } catch (Exception e) {
            log.error("Failed to persist critical compliance DLQ message: topic={}, offset={}",
                    topic, record.offset(), e);

            // DO NOT acknowledge - message will be redelivered
            // This ensures we don't lose critical compliance messages
        }
    }

    /**
     * Extract message ID from Kafka headers or generate unique ID.
     */
    private String extractMessageId(ConsumerRecord<String, String> record) {
        // Try to get messageId from headers first
        Header messageIdHeader = record.headers().lastHeader("messageId");
        if (messageIdHeader != null && messageIdHeader.value() != null) {
            return new String(messageIdHeader.value());
        }

        // Fallback: generate unique message ID from topic + partition + offset
        return String.format("%s-%d-%d-%d",
                record.topic().replace("-dlq", ""),
                record.partition(),
                record.offset(),
                System.currentTimeMillis());
    }

    /**
     * Extract specific header value from Kafka record.
     */
    private String extractHeader(ConsumerRecord<String, String> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        if (header != null && header.value() != null) {
            return new String(header.value());
        }
        return null;
    }

    /**
     * Extract all headers as JSON string for audit trail.
     */
    private String extractAllHeaders(ConsumerRecord<String, String> record) {
        StringBuilder headers = new StringBuilder("{");
        record.headers().forEach(header -> {
            if (header.value() != null) {
                headers.append("\"").append(header.key()).append("\":\"")
                        .append(new String(header.value())).append("\",");
            }
        });
        if (headers.length() > 1) {
            headers.setLength(headers.length() - 1); // Remove trailing comma
        }
        headers.append("}");
        return headers.toString();
    }
}
