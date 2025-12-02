package com.waqiti.common.test;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Base class for Kafka integration tests with embedded Kafka broker.
 *
 * Provides:
 * - Embedded Kafka broker for testing
 * - Test producers and consumers
 * - Message verification utilities
 * - Topic management
 * - Event polling and assertions
 *
 * Usage:
 * <pre>
 * {@code
 * @EmbeddedKafka(topics = {"payment-events", "wallet-events"})
 * class PaymentEventTest extends BaseKafkaTest {
 *     @Test
 *     void shouldPublishPaymentEvent() {
 *         // Publish event
 *         paymentService.processPayment(payment);
 *
 *         // Verify event was published
 *         PaymentEvent event = pollForEvent("payment-events", PaymentEvent.class);
 *         assertThat(event.getPaymentId()).isEqualTo(payment.getId());
 *     }
 * }
 * }
 * </pre>
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2024-10-19
 */
@SpringBootTest
@EmbeddedKafka(
    partitions = 3,
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:9092",
        "port=9092"
    }
)
@ActiveProfiles("kafka-test")
public abstract class BaseKafkaTest {

    @Autowired
    protected EmbeddedKafkaBroker embeddedKafkaBroker;

    protected KafkaTemplate<String, Object> kafkaTemplate;

    protected Map<String, Consumer<String, String>> consumers = new HashMap<>();

    protected static final int DEFAULT_POLL_TIMEOUT_SECONDS = 10;

    /**
     * Set up Kafka test environment before each test.
     */
    @BeforeEach
    public void setUpKafka() {
        // Create Kafka producer
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        ProducerFactory<String, Object> producerFactory = new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);

        // Additional setup hook
        setUpKafkaTest();
    }

    /**
     * Hook for subclasses to perform additional Kafka setup.
     */
    protected void setUpKafkaTest() {
        // Override in subclasses if needed
    }

    /**
     * Create a test consumer for a specific topic.
     *
     * @param topic Topic to consume from
     * @return Consumer instance
     */
    protected Consumer<String, String> createConsumer(String topic) {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
            "test-group-" + UUID.randomUUID(),
            "false",
            embeddedKafkaBroker
        );
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Collections.singletonList(topic));

        consumers.put(topic, consumer);
        return consumer;
    }

    /**
     * Send a message to a Kafka topic.
     *
     * @param topic Topic name
     * @param key Message key
     * @param payload Message payload
     */
    protected void sendMessage(String topic, String key, Object payload) {
        kafkaTemplate.send(topic, key, payload);
        kafkaTemplate.flush();
    }

    /**
     * Send a message to a Kafka topic without a key.
     *
     * @param topic Topic name
     * @param payload Message payload
     */
    protected void sendMessage(String topic, Object payload) {
        sendMessage(topic, null, payload);
    }

    /**
     * Poll for a single message from a topic.
     *
     * @param topic Topic to poll from
     * @param timeoutSeconds Timeout in seconds
     * @return Consumer record or null if timeout
     */
    protected ConsumerRecord<String, String> pollForMessage(String topic, int timeoutSeconds) {
        Consumer<String, String> consumer = consumers.computeIfAbsent(topic, this::createConsumer);

        long endTime = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        while (System.currentTimeMillis() < endTime) {
            var records = consumer.poll(Duration.ofMillis(100));
            if (!records.isEmpty()) {
                return records.iterator().next();
            }
        }
        return null;
    }

    /**
     * Poll for a single message from a topic with default timeout.
     *
     * @param topic Topic to poll from
     * @return Consumer record or null if timeout
     */
    protected ConsumerRecord<String, String> pollForMessage(String topic) {
        return pollForMessage(topic, DEFAULT_POLL_TIMEOUT_SECONDS);
    }

    /**
     * Poll for multiple messages from a topic.
     *
     * @param topic Topic to poll from
     * @param expectedCount Expected number of messages
     * @param timeoutSeconds Timeout in seconds
     * @return List of consumer records
     */
    protected List<ConsumerRecord<String, String>> pollForMessages(
            String topic,
            int expectedCount,
            int timeoutSeconds) {

        Consumer<String, String> consumer = consumers.computeIfAbsent(topic, this::createConsumer);
        List<ConsumerRecord<String, String>> allRecords = new ArrayList<>();

        long endTime = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        while (System.currentTimeMillis() < endTime && allRecords.size() < expectedCount) {
            var records = consumer.poll(Duration.ofMillis(100));
            records.forEach(allRecords::add);
        }
        return allRecords;
    }

    /**
     * Poll for multiple messages from a topic with default timeout.
     *
     * @param topic Topic to poll from
     * @param expectedCount Expected number of messages
     * @return List of consumer records
     */
    protected List<ConsumerRecord<String, String>> pollForMessages(String topic, int expectedCount) {
        return pollForMessages(topic, expectedCount, DEFAULT_POLL_TIMEOUT_SECONDS);
    }

    /**
     * Wait for a topic to have at least one message.
     *
     * @param topic Topic name
     * @param timeoutSeconds Timeout in seconds
     * @return true if message arrived, false if timeout
     */
    protected boolean waitForMessage(String topic, int timeoutSeconds) {
        return pollForMessage(topic, timeoutSeconds) != null;
    }

    /**
     * Wait for a topic to have at least one message with default timeout.
     *
     * @param topic Topic name
     * @return true if message arrived, false if timeout
     */
    protected boolean waitForMessage(String topic) {
        return waitForMessage(topic, DEFAULT_POLL_TIMEOUT_SECONDS);
    }

    /**
     * Verify that a topic has exactly the expected number of messages.
     *
     * @param topic Topic name
     * @param expectedCount Expected count
     * @param timeoutSeconds Timeout in seconds
     * @return true if count matches
     */
    protected boolean verifyMessageCount(String topic, int expectedCount, int timeoutSeconds) {
        List<ConsumerRecord<String, String>> messages = pollForMessages(topic, expectedCount, timeoutSeconds);
        return messages.size() == expectedCount;
    }

    /**
     * Clear all messages from a topic.
     *
     * @param topic Topic to clear
     */
    protected void clearTopic(String topic) {
        Consumer<String, String> consumer = consumers.computeIfAbsent(topic, this::createConsumer);
        consumer.poll(Duration.ofMillis(100));
        consumer.commitSync();
    }

    /**
     * Close all test consumers.
     */
    protected void closeConsumers() {
        consumers.values().forEach(Consumer::close);
        consumers.clear();
    }

    /**
     * Get the embedded Kafka broker.
     *
     * @return Embedded Kafka broker
     */
    protected EmbeddedKafkaBroker getEmbeddedKafkaBroker() {
        return embeddedKafkaBroker;
    }

    /**
     * Get the bootstrap servers address.
     *
     * @return Bootstrap servers
     */
    protected String getBootstrapServers() {
        return embeddedKafkaBroker.getBrokersAsString();
    }

    /**
     * Log Kafka test message.
     *
     * @param message Message to log
     */
    protected void log(String message) {
        System.out.println("[KAFKA TEST] " + message);
    }

    /**
     * Log Kafka test message with formatting.
     *
     * @param format Format string
     * @param args Arguments
     */
    protected void log(String format, Object... args) {
        System.out.println("[KAFKA TEST] " + String.format(format, args));
    }
}
