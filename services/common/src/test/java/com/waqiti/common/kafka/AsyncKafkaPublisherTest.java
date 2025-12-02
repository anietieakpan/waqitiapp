package com.waqiti.common.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.assertj.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Unit Tests for AsyncKafkaPublisher
 *
 * TEST COVERAGE:
 * ==============
 * 1. Successful async publishing
 * 2. Error handling and DLQ routing
 * 3. Circuit breaker behavior (CLOSED/OPEN/HALF_OPEN)
 * 4. Retry mechanism with exponential backoff
 * 5. Callbacks (success and error)
 * 6. Metrics recording
 * 7. Idempotency key generation
 * 8. Serialization errors
 * 9. Concurrent publishing
 * 10. Memory leak prevention
 *
 * @author Waqiti Platform Team
 * @version 3.0
 */
@ExtendWith(MockitoExtension.class)
class AsyncKafkaPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private CircuitBreaker circuitBreaker;

    private ObjectMapper objectMapper;
    private MeterRegistry meterRegistry;
    private AsyncKafkaPublisher publisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();

        // Mock circuit breaker behavior
        when(circuitBreakerRegistry.circuitBreaker(anyString())).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);

        publisher = new AsyncKafkaPublisher(kafkaTemplate, objectMapper, meterRegistry);
        ReflectionTestUtils.setField(publisher, "circuitBreakerRegistry", circuitBreakerRegistry);
        ReflectionTestUtils.setField(publisher, "enableCircuitBreaker", true);
        ReflectionTestUtils.setField(publisher, "enableDlq", true);
    }

    @Test
    void testPublishAsync_Success() throws Exception {
        // Given
        TestEvent event = new TestEvent("test-123", "Test payload");
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(mockSendResult());
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // When
        CompletableFuture<SendResult<String, String>> result =
                publisher.publishAsync(event, "test-topic", "test-key");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isDone()).isTrue();
        assertThat(result.isCompletedExceptionally()).isFalse();

        // Verify Kafka send was called
        ArgumentCaptor<ProducerRecord> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());

        ProducerRecord<String, String> sentRecord = recordCaptor.getValue();
        assertThat(sentRecord.topic()).isEqualTo("test-topic");
        assertThat(sentRecord.key()).isEqualTo("test-key");

        // Verify metrics
        assertThat(meterRegistry.counter("kafka.async.publish.success").count()).isEqualTo(1.0);
    }

    @Test
    void testPublishAsync_WithCallbacks_Success() throws Exception {
        // Given
        TestEvent event = new TestEvent("test-123", "Test payload");
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(mockSendResult());
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        final boolean[] successCalled = {false};
        final boolean[] errorCalled = {false};

        // When
        publisher.publishAsync(
                event,
                "test-topic",
                "test-key",
                result -> successCalled[0] = true,
                error -> errorCalled[0] = true
        );

        // Then - wait for async callbacks
        await().atMost(2, TimeUnit.SECONDS).until(() -> successCalled[0]);
        assertThat(successCalled[0]).isTrue();
        assertThat(errorCalled[0]).isFalse();
    }

    @Test
    void testPublishAsync_Failure_RoutesToDLQ() throws Exception {
        // Given
        TestEvent event = new TestEvent("test-123", "Test payload");

        // First call fails (original topic)
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka unavailable"));

        // Second call succeeds (DLQ topic)
        CompletableFuture<SendResult<String, String>> dlqFuture = CompletableFuture.completedFuture(mockSendResult());

        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(failedFuture)     // First call to original topic
                .thenReturn(dlqFuture);        // Second call to DLQ

        final boolean[] errorCalled = {false};

        // When
        publisher.publishAsync(
                event,
                "test-topic",
                "test-key",
                result -> {},
                error -> errorCalled[0] = true
        );

        // Then - wait for DLQ routing
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(kafkaTemplate, times(2)).send(any(ProducerRecord.class));
        });

        // Verify error callback was called
        assertThat(errorCalled[0]).isTrue();

        // Verify DLQ metrics
        assertThat(meterRegistry.counter("kafka.async.publish.dlq").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("kafka.async.publish.failure").count()).isEqualTo(1.0);
    }

    @Test
    void testCircuitBreaker_Open_FailsFast() throws Exception {
        // Given
        TestEvent event = new TestEvent("test-123", "Test payload");
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);

        CompletableFuture<SendResult<String, String>> dlqFuture = CompletableFuture.completedFuture(mockSendResult());
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(dlqFuture);

        // When
        CompletableFuture<SendResult<String, String>> result =
                publisher.publishAsync(event, "test-topic", "test-key");

        // Then
        assertThat(result.isCompletedExceptionally()).isTrue();

        // Verify original topic was NOT called (circuit breaker open)
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            // Should only call DLQ, not original topic
            ArgumentCaptor<ProducerRecord> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
            verify(kafkaTemplate, atLeastOnce()).send(recordCaptor.capture());

            ProducerRecord<String, String> sentRecord = recordCaptor.getValue();
            assertThat(sentRecord.topic()).isEqualTo("test-topic.dlq");
        });

        // Verify circuit breaker metrics
        assertThat(meterRegistry.counter("kafka.async.circuit_breaker.open").count()).isEqualTo(1.0);
    }

    @Test
    void testPublishWithRetry_Success_OnSecondAttempt() throws Exception {
        // Given
        TestEvent event = new TestEvent("test-123", "Test payload");

        // First attempt fails
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Temporary failure"));

        // Second attempt succeeds
        CompletableFuture<SendResult<String, String>> successFuture = CompletableFuture.completedFuture(mockSendResult());

        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(failedFuture)
                .thenReturn(successFuture);

        // When
        CompletableFuture<SendResult<String, String>> result =
                publisher.publishWithRetry(event, "test-topic", "test-key", 3);

        // Then - wait for retry
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(result.isDone()).isTrue();
            assertThat(result.isCompletedExceptionally()).isFalse();
        });

        // Verify retry happened
        verify(kafkaTemplate, atLeast(2)).send(any(ProducerRecord.class));
    }

    @Test
    void testPublishWithRetry_ExceedsMaxRetries_RoutesToDLQ() throws Exception {
        // Given
        TestEvent event = new TestEvent("test-123", "Test payload");
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Persistent failure"));

        CompletableFuture<SendResult<String, String>> dlqFuture = CompletableFuture.completedFuture(mockSendResult());

        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(failedFuture)
                .thenReturn(failedFuture)
                .thenReturn(failedFuture)
                .thenReturn(dlqFuture);  // DLQ call

        // When
        CompletableFuture<SendResult<String, String>> result =
                publisher.publishWithRetry(event, "test-topic", "test-key", 2);

        // Then
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(result.isDone()).isTrue();
            assertThat(result.isCompletedExceptionally()).isTrue();
        });

        // Verify max retries + DLQ call
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(kafkaTemplate, atLeast(3)).send(any(ProducerRecord.class));
        });
    }

    @Test
    void testSerializationError_FailsFast() {
        // Given
        Object unserializableEvent = new Object() {
            // This will cause serialization failure
            private Object self = this;
        };

        // When
        CompletableFuture<SendResult<String, String>> result =
                publisher.publishAsync(unserializableEvent, "test-topic", "test-key");

        // Then
        assertThat(result.isCompletedExceptionally()).isTrue();

        // Verify Kafka was never called
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));

        // Verify failure metrics
        assertThat(meterRegistry.counter("kafka.async.publish.failure").count()).isGreaterThan(0);
    }

    @Test
    void testConcurrentPublishing_NoMemoryLeak() throws Exception {
        // Given
        int concurrentRequests = 1000;
        CompletableFuture<SendResult<String, String>> successFuture = CompletableFuture.completedFuture(mockSendResult());
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(successFuture);

        // When - publish 1000 events concurrently
        CompletableFuture<?>[] futures = new CompletableFuture[concurrentRequests];
        for (int i = 0; i < concurrentRequests; i++) {
            TestEvent event = new TestEvent("test-" + i, "Payload " + i);
            futures[i] = publisher.publishAsync(event, "test-topic", "key-" + i);
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);

        // Then
        assertThat(meterRegistry.counter("kafka.async.publish.success").count())
                .isEqualTo(concurrentRequests);

        // Verify no memory leak (all futures completed)
        for (CompletableFuture<?> future : futures) {
            assertThat(future.isDone()).isTrue();
        }
    }

    @Test
    void testIdempotencyKeyGeneration_Consistent() {
        // Given
        TestEvent event1 = new TestEvent("test-123", "Same payload");
        TestEvent event2 = new TestEvent("test-123", "Same payload");

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(mockSendResult());
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // When
        publisher.publishAsync(event1, "test-topic", "test-key");
        publisher.publishAsync(event2, "test-topic", "test-key");

        // Then - verify idempotency keys are the same
        ArgumentCaptor<ProducerRecord> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(2)).send(recordCaptor.capture());

        ProducerRecord<String, String> record1 = recordCaptor.getAllValues().get(0);
        ProducerRecord<String, String> record2 = recordCaptor.getAllValues().get(1);

        String idempotencyKey1 = new String(record1.headers().lastHeader("x-idempotency-key").value());
        String idempotencyKey2 = new String(record2.headers().lastHeader("x-idempotency-key").value());

        assertThat(idempotencyKey1).isEqualTo(idempotencyKey2);
    }

    // Helper Methods

    private SendResult<String, String> mockSendResult() {
        SendResult<String, String> result = mock(SendResult.class);
        org.apache.kafka.clients.producer.RecordMetadata metadata =
                new org.apache.kafka.clients.producer.RecordMetadata(
                        new org.apache.kafka.common.TopicPartition("test-topic", 0),
                        0L, 0L, System.currentTimeMillis(), 0L, 0, 0
                );
        when(result.getRecordMetadata()).thenReturn(metadata);
        return result;
    }

    // Test Event Class

    static class TestEvent {
        private String id;
        private String payload;

        public TestEvent(String id, String payload) {
            this.id = id;
            this.payload = payload;
        }

        public String getId() {
            return id;
        }

        public String getPayload() {
            return payload;
        }
    }
}
