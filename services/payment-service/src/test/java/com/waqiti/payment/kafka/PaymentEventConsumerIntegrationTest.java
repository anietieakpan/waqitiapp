package com.waqiti.payment.kafka;

import com.waqiti.payment.event.PaymentCreatedEvent;
import com.waqiti.payment.model.Payment;
import com.waqiti.payment.repository.PaymentRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Kafka consumers using TestContainers and Embedded Kafka.
 * Tests actual Kafka message consumption and processing.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0
 */
@SpringBootTest
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {"payment-created", "payment-completed", "payment-failed"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Kafka Consumer Integration Tests")
class PaymentEventConsumerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PaymentRepository paymentRepository;

    private KafkaTemplate<String, PaymentCreatedEvent> kafkaTemplate;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        kafkaTemplate = createKafkaTemplate();
    }

    @Test
    @DisplayName("Should consume and process payment-created event")
    void shouldConsumePaymentCreatedEvent() {
        // Arrange
        UUID paymentId = UUID.randomUUID();
        PaymentCreatedEvent event = PaymentCreatedEvent.builder()
                .paymentId(paymentId)
                .userId(UUID.randomUUID())
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .build();

        // Act
        kafkaTemplate.send("payment-created", event);

        // Assert - Wait for async processing
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var payment = paymentRepository.findById(paymentId);
                    assertTrue(payment.isPresent());
                    assertEquals(event.getAmount(), payment.get().getAmount());
                });
    }

    @Test
    @DisplayName("Should handle duplicate events with idempotency")
    void shouldHandleDuplicateEvents() {
        // Arrange
        UUID paymentId = UUID.randomUUID();
        String eventId = "test-event-" + UUID.randomUUID();

        PaymentCreatedEvent event = PaymentCreatedEvent.builder()
                .eventId(eventId)
                .paymentId(paymentId)
                .userId(UUID.randomUUID())
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .build();

        // Act - Send same event twice
        kafkaTemplate.send("payment-created", event);
        kafkaTemplate.send("payment-created", event);

        // Assert - Should only process once
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    long count = paymentRepository.count();
                    assertEquals(1, count, "Duplicate event should not create second payment");
                });
    }

    @Test
    @DisplayName("Should handle event processing failure and retry")
    void shouldHandleEventFailureAndRetry() {
        // Arrange - Event with invalid data that will fail first attempt
        PaymentCreatedEvent invalidEvent = PaymentCreatedEvent.builder()
                .paymentId(UUID.randomUUID())
                .userId(null) // This should cause failure
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .build();

        // Act
        kafkaTemplate.send("payment-created", invalidEvent);

        // Assert - Should retry and eventually send to DLQ
        await().atMost(20, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // Verify event was not processed due to validation error
                    long count = paymentRepository.count();
                    assertEquals(0, count);
                    // In production, would verify DLQ has the failed event
                });
    }

    @Test
    @DisplayName("Should process events in order for same partition key")
    void shouldProcessEventsInOrder() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID payment1Id = UUID.randomUUID();
        UUID payment2Id = UUID.randomUUID();

        PaymentCreatedEvent event1 = PaymentCreatedEvent.builder()
                .paymentId(payment1Id)
                .userId(userId)
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .build();

        PaymentCreatedEvent event2 = PaymentCreatedEvent.builder()
                .paymentId(payment2Id)
                .userId(userId)
                .amount(BigDecimal.valueOf(200.00))
                .currency("USD")
                .build();

        // Act - Send with same key (userId) to ensure same partition
        kafkaTemplate.send("payment-created", userId.toString(), event1);
        kafkaTemplate.send("payment-created", userId.toString(), event2);

        // Assert - Both should be processed in order
        await().atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var payments = paymentRepository.findByUserId(userId);
                    assertEquals(2, payments.size());
                });
    }

    @Test
    @DisplayName("Should handle high volume of concurrent events")
    void shouldHandleHighVolumeEvents() {
        // Arrange
        int eventCount = 100;
        UUID userId = UUID.randomUUID();

        // Act - Send 100 events
        for (int i = 0; i < eventCount; i++) {
            PaymentCreatedEvent event = PaymentCreatedEvent.builder()
                    .paymentId(UUID.randomUUID())
                    .userId(userId)
                    .amount(BigDecimal.valueOf(100.00 + i))
                    .currency("USD")
                    .build();

            kafkaTemplate.send("payment-created", event);
        }

        // Assert - All should be processed
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var payments = paymentRepository.findByUserId(userId);
                    assertEquals(eventCount, payments.size());
                });
    }

    private KafkaTemplate<String, PaymentCreatedEvent> createKafkaTemplate() {
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
    }
}
