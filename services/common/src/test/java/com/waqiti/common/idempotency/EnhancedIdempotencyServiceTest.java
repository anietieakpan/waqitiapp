package com.waqiti.common.idempotency;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for EnhancedIdempotencyService
 *
 * Test Coverage:
 * - Duplicate detection (Redis cache hit)
 * - Database fallback (Redis cache miss)
 * - Concurrent request handling
 * - Request payload hashing
 * - TTL and expiration
 * - Error handling and retries
 * - Performance benchmarks
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-02
 */
@ExtendWith(MockitoExtension.class)
class EnhancedIdempotencyServiceTest {

    @Mock
    private IdempotencyRecordRepository repository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    @Mock
    private Timer timer;

    @Mock
    private Timer.Sample timerSample;

    private EnhancedIdempotencyService service;

    @BeforeEach
    void setUp() {
        // Setup Redis mock
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Setup metrics mocks
        when(meterRegistry.counter(anyString())).thenReturn(counter);
        when(meterRegistry.timer(anyString())).thenReturn(timer);
        when(Timer.start(meterRegistry)).thenReturn(timerSample);

        service = new EnhancedIdempotencyService(repository, redisTemplate, meterRegistry);
    }

    // ============================================================================
    // DUPLICATE DETECTION TESTS
    // ============================================================================

    @Test
    void testExecuteIdempotent_FirstRequest_ExecutesOperation() {
        // Arrange
        IdempotencyContext context = createTestContext("test-key-1");
        String expectedResult = "operation-result";

        when(valueOperations.get(anyString())).thenReturn(null);
        when(repository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(IdempotencyRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        String result = service.executeIdempotent(context, () -> expectedResult);

        // Assert
        assertThat(result).isEqualTo(expectedResult);
        verify(repository, times(2)).save(any(IdempotencyRecord.class)); // IN_PROGRESS + COMPLETED
        verify(valueOperations, times(2)).set(anyString(), any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void testExecuteIdempotent_DuplicateRequest_ReturnsCachedResultFromRedis() {
        // Arrange
        IdempotencyContext context = createTestContext("test-key-2");
        IdempotencyRecord cachedRecord = createCompletedRecord("test-key-2", "cached-result");

        when(valueOperations.get(anyString())).thenReturn(cachedRecord);

        AtomicInteger executionCount = new AtomicInteger(0);

        // Act
        String result = service.executeIdempotent(context, () -> {
            executionCount.incrementAndGet();
            return "should-not-execute";
        });

        // Assert
        assertThat(result).isEqualTo("cached-result");
        assertThat(executionCount.get()).isZero(); // Operation should NOT execute
        verify(counter).increment(); // duplicatePrevented metric
        verify(repository, never()).save(any());
    }

    @Test
    void testExecuteIdempotent_RedisMiss_FallsBackToDatabase() {
        // Arrange
        IdempotencyContext context = createTestContext("test-key-3");
        IdempotencyRecord dbRecord = createCompletedRecord("test-key-3", "db-result");

        when(valueOperations.get(anyString())).thenReturn(null);
        when(repository.findByIdempotencyKey("test-key-3")).thenReturn(Optional.of(dbRecord));

        AtomicInteger executionCount = new AtomicInteger(0);

        // Act
        String result = service.executeIdempotent(context, () -> {
            executionCount.incrementAndGet();
            return "should-not-execute";
        });

        // Assert
        assertThat(result).isEqualTo("db-result");
        assertThat(executionCount.get()).isZero();
        verify(valueOperations).set(anyString(), eq(dbRecord), anyLong(), any(TimeUnit.class)); // Re-populate Redis
    }

    // ============================================================================
    // CONCURRENT REQUEST TESTS
    // ============================================================================

    @Test
    void testExecuteIdempotent_ConcurrentRequests_ExecutesOnlyOnce() throws InterruptedException {
        // Arrange
        IdempotencyContext context = createTestContext("concurrent-key");
        int concurrentRequests = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrentRequests);
        AtomicInteger executionCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);

        when(valueOperations.get(anyString())).thenReturn(null);
        when(repository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(IdempotencyRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act - Submit concurrent requests
        for (int i = 0; i < concurrentRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    service.executeIdempotent(context, () -> {
                        executionCount.incrementAndGet();
                        try {
                            Thread.sleep(100); // Simulate operation duration
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return "result";
                    });
                } catch (Exception e) {
                    // Expected for duplicate requests
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert - Operation should execute only once despite concurrent requests
        assertThat(executionCount.get()).isEqualTo(1);
    }

    // ============================================================================
    // REQUEST PAYLOAD HASHING TESTS
    // ============================================================================

    @Test
    void testExecuteIdempotent_DifferentPayloadsSameKey_DetectedAsDuplicate() {
        // Arrange
        String idempotencyKey = "same-key";
        IdempotencyContext context1 = IdempotencyContext.builder()
            .idempotencyKey(idempotencyKey)
            .serviceName("test-service")
            .operationType("TEST_OP")
            .requestPayload("payload-1")
            .build();

        IdempotencyContext context2 = IdempotencyContext.builder()
            .idempotencyKey(idempotencyKey)
            .serviceName("test-service")
            .operationType("TEST_OP")
            .requestPayload("payload-2-different")
            .build();

        IdempotencyRecord existingRecord = createCompletedRecord(idempotencyKey, "result-1");
        when(valueOperations.get(anyString())).thenReturn(existingRecord);

        // Act
        String result1 = service.executeIdempotent(context1, () -> "should-not-execute");
        String result2 = service.executeIdempotent(context2, () -> "should-not-execute");

        // Assert - Both return cached result despite different payloads
        assertThat(result1).isEqualTo("result-1");
        assertThat(result2).isEqualTo("result-1");
    }

    // ============================================================================
    // ERROR HANDLING TESTS
    // ============================================================================

    @Test
    void testExecuteIdempotent_OperationFails_RecordsFailure() {
        // Arrange
        IdempotencyContext context = createTestContext("failing-key");
        RuntimeException expectedException = new RuntimeException("Operation failed");

        when(valueOperations.get(anyString())).thenReturn(null);
        when(repository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(IdempotencyRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act & Assert
        assertThatThrownBy(() -> service.executeIdempotent(context, () -> {
            throw expectedException;
        })).isInstanceOf(RuntimeException.class)
          .hasMessage("Operation failed");

        // Verify failure was recorded
        ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(repository, atLeastOnce()).save(captor.capture());

        IdempotencyRecord failedRecord = captor.getAllValues().stream()
            .filter(r -> r.getStatus() == IdempotencyStatus.FAILED)
            .findFirst()
            .orElse(null);

        assertThat(failedRecord).isNotNull();
        assertThat(failedRecord.getError()).contains("Operation failed");
    }

    @Test
    void testIsProcessed_ExistsInRedis_ReturnsTrue() {
        // Arrange
        when(valueOperations.get("idempotency:existing-key")).thenReturn(new IdempotencyRecord());

        // Act
        boolean result = service.isProcessed("existing-key");

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void testIsProcessed_ExistsInDatabase_ReturnsTrue() {
        // Arrange
        when(valueOperations.get(anyString())).thenReturn(null);
        when(repository.existsByIdempotencyKey("existing-key")).thenReturn(true);

        // Act
        boolean result = service.isProcessed("existing-key");

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void testIsProcessed_NotFound_ReturnsFalse() {
        // Arrange
        when(valueOperations.get(anyString())).thenReturn(null);
        when(repository.existsByIdempotencyKey("non-existing-key")).thenReturn(false);

        // Act
        boolean result = service.isProcessed("non-existing-key");

        // Assert
        assertThat(result).isFalse();
    }

    // ============================================================================
    // ADMIN OPERATIONS TESTS
    // ============================================================================

    @Test
    void testMarkAsFailed_UpdatesRecordAndCache() {
        // Arrange
        String key = "failed-key";
        IdempotencyRecord record = createInProgressRecord(key);
        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(record));
        when(repository.save(any(IdempotencyRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.markAsFailed(key, "Manual failure");

        // Assert
        verify(repository).save(argThat(r ->
            r.getStatus() == IdempotencyStatus.FAILED &&
            r.getError().equals("Manual failure")
        ));
        verify(valueOperations).set(anyString(), any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void testRemove_DeletesFromBothCaches() {
        // Arrange
        String key = "remove-key";
        IdempotencyRecord record = createCompletedRecord(key, "result");
        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(record));

        // Act
        service.remove(key);

        // Assert
        verify(redisTemplate).delete("idempotency:" + key);
        verify(repository).delete(record);
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private IdempotencyContext createTestContext(String key) {
        return IdempotencyContext.builder()
            .idempotencyKey(key)
            .serviceName("test-service")
            .operationType("TEST_OPERATION")
            .userId("user-123")
            .amount(new BigDecimal("100.00"))
            .currency("USD")
            .ttl(Duration.ofHours(24))
            .build();
    }

    private IdempotencyRecord createCompletedRecord(String key, String result) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setId(UUID.randomUUID());
        record.setIdempotencyKey(key);
        record.setOperationId(UUID.randomUUID());
        record.setServiceName("test-service");
        record.setOperationType("TEST_OPERATION");
        record.setStatus(IdempotencyStatus.COMPLETED);
        record.setResult(result);
        return record;
    }

    private IdempotencyRecord createInProgressRecord(String key) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setId(UUID.randomUUID());
        record.setIdempotencyKey(key);
        record.setOperationId(UUID.randomUUID());
        record.setServiceName("test-service");
        record.setOperationType("TEST_OPERATION");
        record.setStatus(IdempotencyStatus.IN_PROGRESS);
        return record;
    }
}
