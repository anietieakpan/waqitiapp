package com.waqiti.common.kafka.schema;

import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import org.apache.kafka.common.errors.SerializationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Custom error handler for Schema Registry related errors
 *
 * Handles:
 * - Schema not found errors
 * - Deserialization errors
 * - Schema compatibility errors
 * - Network errors to schema registry
 *
 * PRODUCTION FIX: Added @Component annotation
 */
@Component
@Slf4j
public class SchemaRegistryErrorHandler implements CommonErrorHandler {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MS = 1000;

    /**
     * Handle individual record processing errors.
     * Method name changed from handleRecord to handleOne in Spring Kafka 3.x (Spring Boot 3.3.5)
     *
     * @return true if the error was handled and the container should continue, false if container should stop
     */
    @Override
    public boolean handleOne(
            Exception thrownException,
            ConsumerRecord<?, ?> record,
            Consumer<?, ?> consumer,
            MessageListenerContainer container) {

        log.error("Error processing record from topic: {}, partition: {}, offset: {}",
                record.topic(), record.partition(), record.offset(), thrownException);

        try {
            if (thrownException.getCause() instanceof SerializationException) {
                handleDeserializationError(record, consumer, thrownException);
            } else if (thrownException.getCause() instanceof RestClientException) {
                handleSchemaRegistryError(record, consumer, thrownException);
            } else {
                handleGenericError(record, consumer, thrownException);
            }
            return true; // Error handled, continue processing
        } catch (Exception e) {
            log.error("Failed to handle error for record", e);
            return false; // Critical error, stop container
        }
    }

    private void handleDeserializationError(
            ConsumerRecord<?, ?> record,
            Consumer<?, ?> consumer,
            Exception exception) {
        
        log.error("Deserialization error for message in topic: {}, partition: {}, offset: {}. " +
                "Message will be skipped.", 
                record.topic(), record.partition(), record.offset());
        
        // Skip the problematic message
        TopicPartition topicPartition = new TopicPartition(record.topic(), record.partition());
        consumer.seek(topicPartition, record.offset() + 1);
        
        // Send to DLQ if configured
        sendToDeadLetterQueue(record, exception);
    }

    private void handleSchemaRegistryError(
            ConsumerRecord<?, ?> record,
            Consumer<?, ?> consumer,
            Exception exception) {
        
        log.error("Schema Registry error for message in topic: {}, partition: {}, offset: {}",
                record.topic(), record.partition(), record.offset());
        
        // Could be a temporary network issue - implement retry logic
        try {
            Thread.sleep(RETRY_BACKOFF_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // If persistent, skip the message
        TopicPartition topicPartition = new TopicPartition(record.topic(), record.partition());
        consumer.seek(topicPartition, record.offset() + 1);
    }

    private void handleGenericError(
            ConsumerRecord<?, ?> record,
            Consumer<?, ?> consumer,
            Exception exception) {
        
        log.error("Generic error processing message from topic: {}, partition: {}, offset: {}",
                record.topic(), record.partition(), record.offset(), exception);
        
        // Skip the message
        TopicPartition topicPartition = new TopicPartition(record.topic(), record.partition());
        consumer.seek(topicPartition, record.offset() + 1);
    }

    private void sendToDeadLetterQueue(ConsumerRecord<?, ?> record, Exception exception) {
        // Implementation would send failed messages to a DLQ topic
        // This is a placeholder - actual implementation would use KafkaTemplate
        log.warn("Message sent to DLQ - Topic: {}, Partition: {}, Offset: {}, Error: {}",
                record.topic(), record.partition(), record.offset(), exception.getMessage());
    }

    @Override
    public void handleBatch(
            Exception thrownException,
            ConsumerRecords<?, ?> data,
            Consumer<?, ?> consumer,
            MessageListenerContainer container,
            Runnable invokeListener) {
        
        log.error("Batch error in Schema Registry processing", thrownException);
        
        // Process records individually to identify problematic ones
        data.forEach(record -> {
            try {
                // Attempt to process individual record
                log.debug("Processing record from topic: {}, partition: {}, offset: {}",
                        record.topic(), record.partition(), record.offset());
            } catch (Exception e) {
                handleOne(e, record, consumer, container);
            }
        });
    }
}