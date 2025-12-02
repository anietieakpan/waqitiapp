package com.waqiti.dispute.service;

import com.waqiti.dispute.entity.ProcessedEvent;
import com.waqiti.dispute.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DistributedIdempotencyService
 *
 * Tests the hybrid Redis + PostgreSQL idempotency implementation
 *
 * @author Waqiti Production Team
 * @version 2.0.0-PRODUCTION
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DistributedIdempotencyService Tests")
class DistributedIdempotencyServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private DistributedIdempotencyService idempotencyService;

    private static final String TEST_EVENT_KEY = "test-event-123";
    private static final String TEST_OPERATION_ID = "op-456";
    private static final String IDEMPOTENCY_PREFIX = "idempotency:";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Should return false when event not processed (Redis miss, DB miss)")
    void testIsAlreadyProcessed_NotProcessed() {
        // Given
        when(redisTemplate.hasKey(IDEMPOTENCY_PREFIX + TEST_EVENT_KEY)).thenReturn(false);
        when(processedEventRepository.findByEventKey(TEST_EVENT_KEY)).thenReturn(Optional.empty());

        // When
        boolean result = idempotencyService.isAlreadyProcessed(TEST_EVENT_KEY);

        // Then
        assertThat(result).isFalse();
        verify(redisTemplate).hasKey(IDEMPOTENCY_PREFIX + TEST_EVENT_KEY);
        verify(processedEventRepository).findByEventKey(TEST_EVENT_KEY);
    }

    @Test
    @DisplayName("Should return true when event found in Redis (fast path)")
    void testIsAlreadyProcessed_FoundInRedis() {
        // Given
        when(redisTemplate.hasKey(IDEMPOTENCY_PREFIX + TEST_EVENT_KEY)).thenReturn(true);

        // When
        boolean result = idempotencyService.isAlreadyProcessed(TEST_EVENT_KEY);

        // Then
        assertThat(result).isTrue();
        verify(redisTemplate).hasKey(IDEMPOTENCY_PREFIX + TEST_EVENT_KEY);
        verify(processedEventRepository, never()).findByEventKey(any());
    }

    @Test
    @DisplayName("Should return true when event found in DB and restore to Redis")
    void testIsAlreadyProcessed_FoundInDatabaseAndRestored() {
        // Given
        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventKey(TEST_EVENT_KEY)
                .operationId(TEST_OPERATION_ID)
                .processedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(redisTemplate.hasKey(IDEMPOTENCY_PREFIX + TEST_EVENT_KEY)).thenReturn(false);
        when(processedEventRepository.findByEventKey(TEST_EVENT_KEY)).thenReturn(Optional.of(processedEvent));

        // When
        boolean result = idempotencyService.isAlreadyProcessed(TEST_EVENT_KEY);

        // Then
        assertThat(result).isTrue();
        verify(redisTemplate).hasKey(IDEMPOTENCY_PREFIX + TEST_EVENT_KEY);
        verify(processedEventRepository).findByEventKey(TEST_EVENT_KEY);
        verify(valueOperations).set(eq(IDEMPOTENCY_PREFIX + TEST_EVENT_KEY), eq(TEST_OPERATION_ID), eq(7L), eq(TimeUnit.DAYS));
    }

    @Test
    @DisplayName("Should successfully mark event as processed in both Redis and DB")
    void testMarkAsProcessed_Success() {
        // Given
        String result = "SUCCESS";
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        // When
        idempotencyService.markAsProcessed(TEST_EVENT_KEY, TEST_OPERATION_ID, result);

        // Then
        verify(valueOperations).setIfAbsent(
                eq(IDEMPOTENCY_PREFIX + TEST_EVENT_KEY),
                eq(TEST_OPERATION_ID),
                eq(7L),
                eq(TimeUnit.DAYS)
        );
        verify(processedEventRepository).save(argThat(event ->
                event.getEventKey().equals(TEST_EVENT_KEY) &&
                event.getOperationId().equals(TEST_OPERATION_ID) &&
                event.getResult().equals(result) &&
                event.getExpiresAt() != null
        ));
    }

    @Test
    @DisplayName("Should handle Redis failure gracefully and still save to DB")
    void testMarkAsProcessed_RedisFailureButDbSuccess() {
        // Given
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenThrow(new RuntimeException("Redis connection failed"));

        // When
        idempotencyService.markAsProcessed(TEST_EVENT_KEY, TEST_OPERATION_ID, "SUCCESS");

        // Then
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("Should return true when trying to acquire lock successfully")
    void testTryAcquireLock_Success() {
        // Given
        String lockKey = "lock:" + TEST_EVENT_KEY;
        when(valueOperations.setIfAbsent(eq(lockKey), anyString(), eq(30L), eq(TimeUnit.SECONDS))).thenReturn(true);

        // When
        boolean result = idempotencyService.tryAcquireLock(TEST_EVENT_KEY, 30);

        // Then
        assertThat(result).isTrue();
        verify(valueOperations).setIfAbsent(eq(lockKey), anyString(), eq(30L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Should return false when lock is already held")
    void testTryAcquireLock_LockAlreadyHeld() {
        // Given
        String lockKey = "lock:" + TEST_EVENT_KEY;
        when(valueOperations.setIfAbsent(eq(lockKey), anyString(), eq(30L), eq(TimeUnit.SECONDS))).thenReturn(false);

        // When
        boolean result = idempotencyService.tryAcquireLock(TEST_EVENT_KEY, 30);

        // Then
        assertThat(result).isFalse();
        verify(valueOperations).setIfAbsent(eq(lockKey), anyString(), eq(30L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Should successfully release lock")
    void testReleaseLock() {
        // Given
        String lockKey = "lock:" + TEST_EVENT_KEY;

        // When
        idempotencyService.releaseLock(TEST_EVENT_KEY);

        // Then
        verify(redisTemplate).delete(lockKey);
    }

    @Test
    @DisplayName("Should cleanup expired events from database")
    void testCleanupExpiredEvents() {
        // Given
        int deletedCount = 42;
        when(processedEventRepository.deleteByExpiresAtBefore(any(LocalDateTime.class))).thenReturn(deletedCount);

        // When
        idempotencyService.cleanupExpiredEvents();

        // Then
        verify(processedEventRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Should handle null event key gracefully")
    void testIsAlreadyProcessed_NullEventKey() {
        // When
        boolean result = idempotencyService.isAlreadyProcessed(null);

        // Then
        assertThat(result).isFalse();
        verify(redisTemplate, never()).hasKey(anyString());
        verify(processedEventRepository, never()).findByEventKey(any());
    }

    @Test
    @DisplayName("Should handle empty event key gracefully")
    void testIsAlreadyProcessed_EmptyEventKey() {
        // When
        boolean result = idempotencyService.isAlreadyProcessed("");

        // Then
        assertThat(result).isFalse();
        verify(redisTemplate, never()).hasKey(anyString());
        verify(processedEventRepository, never()).findByEventKey(any());
    }
}
