package com.waqiti.common.kafka.dlt;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Dead Letter Service
 *
 * Handles persistence of failed Kafka messages to Dead Letter Topics
 * and database for manual investigation and replay.
 *
 * @author Waqiti Platform Engineering
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeadLetterService {

    private final DeadLetterRepository deadLetterRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Value("${kafka.dlt.enabled:true}")
    private boolean dltEnabled;

    @Value("${kafka.dlt.topic-suffix:.DLT}")
    private String dltTopicSuffix;

    @Value("${kafka.dlt.local-file-path:/var/waqiti/dlt}")
    private String dltLocalFilePath;

    @Value("${kafka.dlt.consumer-group:}")
    private String consumerGroup;

    /**
     * Persist failed message to Dead Letter Topic and database
     *
     * @param record Original Kafka consumer record
     * @param exception Exception that caused the failure
     * @param retryCount Number of retries attempted
     * @param <K> Key type
     * @param <V> Value type
     * @return true if successfully persisted, false otherwise
     */
    @Transactional
    public <K, V> boolean persistToDeadLetter(
            ConsumerRecord<K, V> record,
            Exception exception,
            int retryCount) {

        if (!dltEnabled) {
            log.warn("DLT disabled - would have persisted: topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset());
            return false;
        }

        try {
            // 1. Convert value to JSON string
            String valueJson = serializeValue(record.value());

            // 2. Create dead letter record
            DeadLetterRecord dltRecord = DeadLetterRecord.builder()
                .originalTopic(record.topic())
                .originalPartition(record.partition())
                .originalOffset(record.offset())
                .originalKey(record.key() != null ? record.key().toString() : null)
                .originalValue(valueJson)
                .originalTimestamp(Instant.ofEpochMilli(record.timestamp()))
                .consumerGroup(consumerGroup)
                .failureException(exception.getClass().getName())
                .failureMessage(exception.getMessage())
                .failureStackTrace(getStackTraceAsString(exception))
                .failureTimestamp(Instant.now())
                .retryCount(retryCount)
                .investigationStatus(DeadLetterRecord.InvestigationStatus.PENDING)
                .replayed(false)
                .build();

            // 3. Send to DLT Kafka topic
            String dltTopic = record.topic() + dltTopicSuffix;
            try {
                kafkaTemplate.send(dltTopic, record.key() != null ? record.key().toString() : null, dltRecord)
                    .get(5, TimeUnit.SECONDS);

                log.info("Sent message to DLT Kafka topic: {}", dltTopic);
                incrementDltMetric("kafka_success", record.topic());

            } catch (Exception kafkaException) {
                log.error("Failed to send to DLT Kafka topic: {}", dltTopic, kafkaException);
                incrementDltMetric("kafka_failure", record.topic());
                // Continue to database persistence
            }

            // 4. Persist to database
            DeadLetterRecord saved = deadLetterRepository.save(dltRecord);

            log.info("Persisted DLT record to database: id={}, topic={}, partition={}, offset={}",
                saved.getId(), record.topic(), record.partition(), record.offset());

            incrementDltMetric("database_success", record.topic());

            return true;

        } catch (Exception dltException) {
            log.error("CATASTROPHIC: Failed to persist to DLT - DATA LOSS IMMINENT", dltException);
            incrementDltMetric("total_failure", record.topic());

            // Last resort: Write to local file system
            writeToLocalFile(record, exception);

            return false;
        }
    }

    /**
     * Replay a dead letter message back to its original topic
     *
     * @param dltRecordId ID of the DLT record to replay
     * @param replayedBy User who triggered the replay
     * @return true if successfully replayed
     */
    @Transactional
    public boolean replayDeadLetter(UUID dltRecordId, String replayedBy) {
        DeadLetterRecord dltRecord = deadLetterRepository.findById(dltRecordId)
            .orElseThrow(() -> new IllegalArgumentException("DLT record not found: " + dltRecordId));

        if (dltRecord.getReplayed()) {
            log.warn("DLT record already replayed: {}", dltRecordId);
            return false;
        }

        try {
            // Deserialize original value
            Object originalValue = objectMapper.readValue(
                dltRecord.getOriginalValue(),
                Object.class
            );

            // Send back to original topic
            kafkaTemplate.send(
                dltRecord.getOriginalTopic(),
                dltRecord.getOriginalKey(),
                originalValue
            ).get(10, TimeUnit.SECONDS);

            // Mark as replayed
            dltRecord.setReplayed(true);
            dltRecord.setReplayedAt(LocalDateTime.now());
            dltRecord.setReplayedBy(replayedBy);
            dltRecord.setInvestigationStatus(DeadLetterRecord.InvestigationStatus.REPLAYED);
            deadLetterRepository.save(dltRecord);

            log.info("Successfully replayed DLT record: id={}, topic={}",
                dltRecordId, dltRecord.getOriginalTopic());

            incrementDltMetric("replay_success", dltRecord.getOriginalTopic());

            return true;

        } catch (Exception e) {
            log.error("Failed to replay DLT record: {}", dltRecordId, e);
            incrementDltMetric("replay_failure", dltRecord.getOriginalTopic());
            return false;
        }
    }

    /**
     * Mark DLT record as resolved
     */
    @Transactional
    public void resolveDeadLetter(UUID dltRecordId, String investigatedBy, String resolutionNotes) {
        DeadLetterRecord dltRecord = deadLetterRepository.findById(dltRecordId)
            .orElseThrow(() -> new IllegalArgumentException("DLT record not found: " + dltRecordId));

        dltRecord.setInvestigationStatus(DeadLetterRecord.InvestigationStatus.RESOLVED);
        dltRecord.setInvestigatedBy(investigatedBy);
        dltRecord.setInvestigatedAt(LocalDateTime.now());
        dltRecord.setResolutionNotes(resolutionNotes);

        deadLetterRepository.save(dltRecord);

        log.info("Resolved DLT record: id={}, by={}", dltRecordId, investigatedBy);
    }

    /**
     * Serialize value to JSON string
     */
    private String serializeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("Failed to serialize value to JSON", e);
            return value != null ? value.toString() : "null";
        }
    }

    /**
     * Get stack trace as string
     */
    private String getStackTraceAsString(Exception exception) {
        StringBuilder sb = new StringBuilder();
        sb.append(exception.toString()).append("\n");

        for (StackTraceElement element : exception.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }

        if (exception.getCause() != null) {
            sb.append("Caused by: ");
            sb.append(getStackTraceAsString((Exception) exception.getCause()));
        }

        return sb.toString();
    }

    /**
     * Write to local file system as absolute last resort
     */
    private <K, V> void writeToLocalFile(ConsumerRecord<K, V> record, Exception exception) {
        try {
            Path dltDir = Paths.get(dltLocalFilePath);
            Files.createDirectories(dltDir);

            String timestamp = Instant.now().toString().replace(":", "-");
            Path dltFile = dltDir.resolve(String.format(
                "dlt-%s-%s-%d-%d.json",
                record.topic(),
                timestamp,
                record.partition(),
                record.offset()
            ));

            Map<String, Object> fileContent = Map.of(
                "topic", record.topic(),
                "partition", record.partition(),
                "offset", record.offset(),
                "key", record.key() != null ? record.key().toString() : "null",
                "value", serializeValue(record.value()),
                "timestamp", record.timestamp(),
                "exception", exception.getClass().getName(),
                "message", exception.getMessage(),
                "stackTrace", getStackTraceAsString(exception),
                "failureTime", Instant.now().toString()
            );

            String json = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(fileContent);

            Files.writeString(dltFile, json, StandardOpenOption.CREATE_NEW);

            log.warn("Wrote DLT record to local file: {}", dltFile);
            incrementDltMetric("local_file_success", record.topic());

        } catch (IOException fileException) {
            log.error("TOTAL FAILURE: Cannot write DLT to file system", fileException);
            incrementDltMetric("local_file_failure", record.topic());
        }
    }

    /**
     * Increment DLT metric
     */
    private void incrementDltMetric(String result, String topic) {
        Counter.builder("dlt.persistence")
            .tag("result", result)
            .tag("topic", topic)
            .register(meterRegistry)
            .increment();
    }
}
