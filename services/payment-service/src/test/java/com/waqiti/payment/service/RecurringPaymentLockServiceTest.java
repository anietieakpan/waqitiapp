package com.waqiti.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for RecurringPaymentLockService
 *
 * @author Waqiti Platform Team
 * @version 3.0.0
 * @since 2025-10-11
 */
@ExtendWith(MockitoExtension.class)
class RecurringPaymentLockServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RecurringPaymentLockService lockService;

    private UUID testScheduleId;
    private String testExecutionDate;
    private String expectedLockKey;

    @BeforeEach
    void setUp() {
        testScheduleId = UUID.randomUUID();
        testExecutionDate = "2025-10-11";
        expectedLockKey = "recurring_payment_lock:" + testScheduleId + ":" + testExecutionDate;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void testAcquireLock_FirstAttempt_Success() {
        // Arrange
        when(valueOperations.setIfAbsent(
            eq(expectedLockKey),
            anyString(),
            anyLong(),
            eq(TimeUnit.MILLISECONDS)
        )).thenReturn(true);

        // Act
        String lockToken = lockService.acquireLock(testScheduleId, testExecutionDate);

        // Assert
        assertThat(lockToken).isNotNull();
        assertThat(lockToken).matches("[0-9a-f-]{36}"); // UUID format

        verify(valueOperations, times(1)).setIfAbsent(
            eq(expectedLockKey),
            eq(lockToken),
            anyLong(),
            eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    void testAcquireLock_AlreadyLocked_RetriesAndFails() {
        // Arrange
        when(valueOperations.setIfAbsent(
            eq(expectedLockKey),
            anyString(),
            anyLong(),
            eq(TimeUnit.MILLISECONDS)
        )).thenReturn(false);

        // Act
        String lockToken = lockService.acquireLock(testScheduleId, testExecutionDate);

        // Assert
        assertThat(lockToken).isNull();

        // Should retry 3 times
        verify(valueOperations, times(3)).setIfAbsent(
            eq(expectedLockKey),
            anyString(),
            anyLong(),
            eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    void testAcquireLock_SucceedsOnSecondAttempt() {
        // Arrange
        when(valueOperations.setIfAbsent(
            eq(expectedLockKey),
            anyString(),
            anyLong(),
            eq(TimeUnit.MILLISECONDS)
        ))
        .thenReturn(false)  // First attempt fails
        .thenReturn(true);  // Second attempt succeeds

        // Act
        String lockToken = lockService.acquireLock(testScheduleId, testExecutionDate);

        // Assert
        assertThat(lockToken).isNotNull();

        verify(valueOperations, times(2)).setIfAbsent(
            eq(expectedLockKey),
            anyString(),
            anyLong(),
            eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    void testReleaseLock_ValidToken_Success() {
        // Arrange
        String lockToken = UUID.randomUUID().toString();
        when(valueOperations.get(expectedLockKey)).thenReturn(lockToken);
        when(redisTemplate.delete(expectedLockKey)).thenReturn(true);

        // Act
        boolean result = lockService.releaseLock(testScheduleId, testExecutionDate, lockToken);

        // Assert
        assertThat(result).isTrue();

        verify(valueOperations, times(1)).get(expectedLockKey);
        verify(redisTemplate, times(1)).delete(expectedLockKey);
    }

    @Test
    void testReleaseLock_TokenMismatch_ReturnsFalse() {
        // Arrange
        String lockToken = UUID.randomUUID().toString();
        String differentToken = UUID.randomUUID().toString();
        when(valueOperations.get(expectedLockKey)).thenReturn(differentToken);

        // Act
        boolean result = lockService.releaseLock(testScheduleId, testExecutionDate, lockToken);

        // Assert
        assertThat(result).isFalse();

        verify(valueOperations, times(1)).get(expectedLockKey);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void testReleaseLock_LockExpired_ReturnsFalse() {
        // Arrange
        String lockToken = UUID.randomUUID().toString();
        when(valueOperations.get(expectedLockKey)).thenReturn(null);

        // Act
        boolean result = lockService.releaseLock(testScheduleId, testExecutionDate, lockToken);

        // Assert
        assertThat(result).isFalse();

        verify(valueOperations, times(1)).get(expectedLockKey);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void testExtendLock_ValidToken_Success() {
        // Arrange
        String lockToken = UUID.randomUUID().toString();
        when(valueOperations.get(expectedLockKey)).thenReturn(lockToken);
        when(redisTemplate.expire(eq(expectedLockKey), anyLong(), eq(TimeUnit.MILLISECONDS)))
            .thenReturn(true);

        // Act
        boolean result = lockService.extendLock(testScheduleId, testExecutionDate, lockToken);

        // Assert
        assertThat(result).isTrue();

        verify(valueOperations, times(1)).get(expectedLockKey);
        verify(redisTemplate, times(1)).expire(
            eq(expectedLockKey),
            anyLong(),
            eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    void testExtendLock_TokenMismatch_ReturnsFalse() {
        // Arrange
        String lockToken = UUID.randomUUID().toString();
        String differentToken = UUID.randomUUID().toString();
        when(valueOperations.get(expectedLockKey)).thenReturn(differentToken);

        // Act
        boolean result = lockService.extendLock(testScheduleId, testExecutionDate, lockToken);

        // Assert
        assertThat(result).isFalse();

        verify(valueOperations, times(1)).get(expectedLockKey);
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any());
    }

    @Test
    void testExtendLock_LockExpired_ReturnsFalse() {
        // Arrange
        String lockToken = UUID.randomUUID().toString();
        when(valueOperations.get(expectedLockKey)).thenReturn(null);

        // Act
        boolean result = lockService.extendLock(testScheduleId, testExecutionDate, lockToken);

        // Assert
        assertThat(result).isFalse();

        verify(valueOperations, times(1)).get(expectedLockKey);
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any());
    }

    @Test
    void testLockKeyGeneration_CorrectFormat() {
        // Arrange & Act
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any()))
            .thenReturn(true);

        lockService.acquireLock(testScheduleId, testExecutionDate);

        // Assert
        verify(valueOperations).setIfAbsent(
            eq(expectedLockKey),
            anyString(),
            anyLong(),
            eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    void testConcurrentAccess_OnlyOneAcquiresLock() {
        // Arrange
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any()))
            .thenReturn(true)   // First caller succeeds
            .thenReturn(false); // Second caller fails

        // Act
        String token1 = lockService.acquireLock(testScheduleId, testExecutionDate);
        String token2 = lockService.acquireLock(testScheduleId, testExecutionDate);

        // Assert
        assertThat(token1).isNotNull();
        assertThat(token2).isNull();
    }

    @Test
    void testLockTimeout_AutomaticallyExpires() {
        // This test verifies that locks are set with a timeout
        // Arrange
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any()))
            .thenReturn(true);

        // Act
        lockService.acquireLock(testScheduleId, testExecutionDate);

        // Assert - Verify timeout is set (5 minutes = 300000ms)
        verify(valueOperations).setIfAbsent(
            anyString(),
            anyString(),
            eq(300000L),  // 5 minutes
            eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    void testReleaseLock_ExceptionHandling() {
        // Arrange
        String lockToken = UUID.randomUUID().toString();
        when(valueOperations.get(expectedLockKey))
            .thenThrow(new RuntimeException("Redis connection error"));

        // Act
        boolean result = lockService.releaseLock(testScheduleId, testExecutionDate, lockToken);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void testAcquireLock_ExceptionOnFirstAttempt_Retries() {
        // Arrange
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any()))
            .thenThrow(new RuntimeException("Redis error"))
            .thenReturn(true);

        // Act
        String lockToken = lockService.acquireLock(testScheduleId, testExecutionDate);

        // Assert - Should retry and succeed on second attempt
        assertThat(lockToken).isNotNull();
        verify(valueOperations, times(2)).setIfAbsent(
            anyString(), anyString(), anyLong(), any());
    }
}
