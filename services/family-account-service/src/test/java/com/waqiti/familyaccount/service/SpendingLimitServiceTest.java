package com.waqiti.familyaccount.service;

import com.waqiti.familyaccount.domain.FamilyMember;
import com.waqiti.familyaccount.domain.TransactionAttempt;
import com.waqiti.familyaccount.exception.SpendingLimitExceededException;
import com.waqiti.familyaccount.repository.TransactionAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SpendingLimitService
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@ExtendWith(MockitoExtension.class)
class SpendingLimitServiceTest {

    @Mock
    private TransactionAttemptRepository transactionAttemptRepository;

    @InjectMocks
    private SpendingLimitService spendingLimitService;

    private FamilyMember familyMember;

    @BeforeEach
    void setUp() {
        familyMember = FamilyMember.builder()
            .userId("user123")
            .dailySpendingLimit(new BigDecimal("100.00"))
            .weeklySpendingLimit(new BigDecimal("500.00"))
            .monthlySpendingLimit(new BigDecimal("2000.00"))
            .build();
    }

    // ==================== validateSpendingLimits() Tests ====================

    @Test
    void testValidateSpendingLimits_WithinAllLimits_ShouldPass() {
        // Given
        BigDecimal transactionAmount = new BigDecimal("50.00");

        // Mock daily spending
        when(transactionAttemptRepository.findByFamilyMemberAndAttemptTimeBetween(
            eq(familyMember), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList());

        // When / Then
        assertDoesNotThrow(() ->
            spendingLimitService.validateSpendingLimits(familyMember, transactionAmount)
        );

        verify(transactionAttemptRepository, times(3))
            .findByFamilyMemberAndAttemptTimeBetween(eq(familyMember), any(), any());
    }

    @Test
    void testValidateSpendingLimits_ExceedsDailyLimit_ShouldThrowException() {
        // Given
        BigDecimal transactionAmount = new BigDecimal("60.00");

        // Mock daily spending (already spent 50)
        TransactionAttempt previousTransaction = TransactionAttempt.builder()
            .amount(new BigDecimal("50.00"))
            .authorized(true)
            .build();

        when(transactionAttemptRepository.findByFamilyMemberAndAttemptTimeBetween(
            eq(familyMember), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of(previousTransaction));

        // When / Then
        SpendingLimitExceededException exception = assertThrows(
            SpendingLimitExceededException.class,
            () -> spendingLimitService.validateSpendingLimits(familyMember, transactionAmount)
        );

        assertTrue(exception.getMessage().contains("Daily"));
        assertTrue(exception.getMessage().contains("100.00"));
    }

    @Test
    void testValidateSpendingLimits_ExceedsWeeklyLimit_ShouldThrowException() {
        // Given
        BigDecimal transactionAmount = new BigDecimal("100.00"));

        // Mock daily spending (within limit)
        when(transactionAttemptRepository.findByFamilyMemberAndAttemptTimeBetween(
            eq(familyMember), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList())  // Daily
            .thenReturn(createTransactionList(new BigDecimal("450.00")));  // Weekly

        // When / Then
        SpendingLimitExceededException exception = assertThrows(
            SpendingLimitExceededException.class,
            () -> spendingLimitService.validateSpendingLimits(familyMember, transactionAmount)
        );

        assertTrue(exception.getMessage().contains("Weekly"));
    }

    @Test
    void testValidateSpendingLimits_ExceedsMonthlyLimit_ShouldThrowException() {
        // Given
        BigDecimal transactionAmount = new BigDecimal("100.00");

        // Mock spending
        when(transactionAttemptRepository.findByFamilyMemberAndAttemptTimeBetween(
            eq(familyMember), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList())  // Daily
            .thenReturn(Collections.emptyList())  // Weekly
            .thenReturn(createTransactionList(new BigDecimal("1950.00")));  // Monthly

        // When / Then
        SpendingLimitExceededException exception = assertThrows(
            SpendingLimitExceededException.class,
            () -> spendingLimitService.validateSpendingLimits(familyMember, transactionAmount)
        );

        assertTrue(exception.getMessage().contains("Monthly"));
    }

    @Test
    void testValidateSpendingLimits_NoLimitsSet_ShouldPass() {
        // Given
        FamilyMember memberWithNoLimits = FamilyMember.builder()
            .userId("user456")
            .build();

        BigDecimal transactionAmount = new BigDecimal("1000.00");

        // When / Then
        assertDoesNotThrow(() ->
            spendingLimitService.validateSpendingLimits(memberWithNoLimits, transactionAmount)
        );

        verify(transactionAttemptRepository, never())
            .findByFamilyMemberAndAttemptTimeBetween(any(), any(), any());
    }

    // ==================== calculateDailySpending() Tests ====================

    @Test
    void testCalculateDailySpending_WithTransactions_ShouldReturnTotal() {
        // Given
        List<TransactionAttempt> transactions = Arrays.asList(
            createAuthorizedTransaction(new BigDecimal("25.00")),
            createAuthorizedTransaction(new BigDecimal("30.00")),
            createDeclinedTransaction(new BigDecimal("15.00"))  // Should not be counted
        );

        when(transactionAttemptRepository.findByFamilyMemberAndAttemptTimeBetween(
            eq(familyMember), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(transactions);

        // When
        BigDecimal result = spendingLimitService.calculateDailySpending(familyMember);

        // Then
        assertEquals(new BigDecimal("55.00"), result);
    }

    @Test
    void testCalculateDailySpending_NoTransactions_ShouldReturnZero() {
        // Given
        when(transactionAttemptRepository.findByFamilyMemberAndAttemptTimeBetween(
            eq(familyMember), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList());

        // When
        BigDecimal result = spendingLimitService.calculateDailySpending(familyMember);

        // Then
        assertEquals(BigDecimal.ZERO, result);
    }

    // ==================== getRemainingDailySpending() Tests ====================

    @Test
    void testGetRemainingDailySpending_WithSpending_ShouldReturnRemaining() {
        // Given
        List<TransactionAttempt> transactions = createTransactionList(new BigDecimal("40.00"));

        when(transactionAttemptRepository.findByFamilyMemberAndAttemptTimeBetween(
            eq(familyMember), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(transactions);

        // When
        BigDecimal result = spendingLimitService.getRemainingDailySpending(familyMember);

        // Then
        assertEquals(new BigDecimal("60.00"), result);
    }

    @Test
    void testGetRemainingDailySpending_ExceededLimit_ShouldReturnZero() {
        // Given
        List<TransactionAttempt> transactions = createTransactionList(new BigDecimal("120.00"));

        when(transactionAttemptRepository.findByFamilyMemberAndAttemptTimeBetween(
            eq(familyMember), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(transactions);

        // When
        BigDecimal result = spendingLimitService.getRemainingDailySpending(familyMember);

        // Then
        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void testGetRemainingDailySpending_NoLimitSet_ShouldReturnNull() {
        // Given
        FamilyMember memberWithNoLimit = FamilyMember.builder()
            .userId("user456")
            .build();

        // When
        BigDecimal result = spendingLimitService.getRemainingDailySpending(memberWithNoLimit);

        // Then
        assertNull(result);
    }

    // ==================== isApproachingLimit() Tests ====================

    @Test
    void testIsApproachingLimit_At85Percent_ShouldReturnTrue() {
        // Given
        List<TransactionAttempt> transactions = createTransactionList(new BigDecimal("85.00"));

        when(transactionAttemptRepository.findByFamilyMemberAndAttemptTimeBetween(
            eq(familyMember), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(transactions);

        // When
        boolean result = spendingLimitService.isApproachingLimit(
            familyMember,
            SpendingLimitService.LimitType.DAILY
        );

        // Then
        assertTrue(result);
    }

    @Test
    void testIsApproachingLimit_At50Percent_ShouldReturnFalse() {
        // Given
        List<TransactionAttempt> transactions = createTransactionList(new BigDecimal("50.00"));

        when(transactionAttemptRepository.findByFamilyMemberAndAttemptTimeBetween(
            eq(familyMember), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(transactions);

        // When
        boolean result = spendingLimitService.isApproachingLimit(
            familyMember,
            SpendingLimitService.LimitType.DAILY
        );

        // Then
        assertFalse(result);
    }

    @Test
    void testIsApproachingLimit_NoLimitSet_ShouldReturnFalse() {
        // Given
        FamilyMember memberWithNoLimit = FamilyMember.builder()
            .userId("user456")
            .build();

        // When
        boolean result = spendingLimitService.isApproachingLimit(
            memberWithNoLimit,
            SpendingLimitService.LimitType.DAILY
        );

        // Then
        assertFalse(result);
    }

    // ==================== Helper Methods ====================

    private TransactionAttempt createAuthorizedTransaction(BigDecimal amount) {
        return TransactionAttempt.builder()
            .amount(amount)
            .authorized(true)
            .build();
    }

    private TransactionAttempt createDeclinedTransaction(BigDecimal amount) {
        return TransactionAttempt.builder()
            .amount(amount)
            .authorized(false)
            .build();
    }

    private List<TransactionAttempt> createTransactionList(BigDecimal totalAmount) {
        return List.of(createAuthorizedTransaction(totalAmount));
    }
}
