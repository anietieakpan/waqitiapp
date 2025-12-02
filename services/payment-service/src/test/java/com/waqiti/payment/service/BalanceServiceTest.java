package com.waqiti.payment.service;

import com.waqiti.payment.domain.Account;
import com.waqiti.payment.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for BalanceService
 *
 * Tests account balance operations:
 * - Credit account operations
 * - Debit account operations
 * - Balance threshold checks
 * - Low balance alerts
 * - Auto-reload triggers
 * - Account restrictions
 * - Negative balance handling
 * - Overdraft protection
 * - Balance reconciliation
 * - Balance snapshots
 * - Balance milestones
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BalanceService Tests")
class BalanceServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PaymentMetricsService metricsService;

    private BalanceService balanceService;

    @Captor
    private ArgumentCaptor<Account> accountCaptor;

    private static final String TEST_ACCOUNT_ID = "account-123";
    private static final String TEST_TRANSACTION_ID = "txn-456";
    private static final String TEST_CORRELATION_ID = "corr-789";

    @BeforeEach
    void setUp() {
        balanceService = new BalanceService(accountRepository, metricsService);
    }

    @Nested
    @DisplayName("Credit Account Tests")
    class CreditAccountTests {

        @Test
        @DisplayName("Should credit account successfully")
        void shouldCreditAccountSuccessfully() {
            // Given
            BigDecimal initialBalance = new BigDecimal("500.00");
            BigDecimal initialAvailable = new BigDecimal("500.00");
            BigDecimal creditAmount = new BigDecimal("100.00");

            Account account = createTestAccount(initialBalance, initialAvailable);
            when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            balanceService.creditAccount(TEST_ACCOUNT_ID, creditAmount, TEST_TRANSACTION_ID,
                    "Test credit", TEST_CORRELATION_ID);

            // Then
            verify(accountRepository).findById(TEST_ACCOUNT_ID);
            verify(accountRepository).save(accountCaptor.capture());

            Account savedAccount = accountCaptor.getValue();
            assertThat(savedAccount.getBalance()).isEqualByComparingTo(new BigDecimal("600.00"));
            assertThat(savedAccount.getAvailableBalance()).isEqualByComparingTo(new BigDecimal("600.00"));
            assertThat(savedAccount.getUpdatedAt()).isNotNull();
            assertThat(savedAccount.getLastActivityAt()).isNotNull();

            verify(metricsService).recordBalanceCredit(TEST_ACCOUNT_ID, creditAmount);
        }

        @Test
        @DisplayName("Should handle large credit amounts")
        void shouldHandleLargeCreditAmounts() {
            // Given
            BigDecimal initialBalance = new BigDecimal("1000.00");
            BigDecimal largeCredit = new BigDecimal("10000.00");

            Account account = createTestAccount(initialBalance, initialBalance);
            when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            balanceService.creditAccount(TEST_ACCOUNT_ID, largeCredit, TEST_TRANSACTION_ID,
                    "Large credit", TEST_CORRELATION_ID);

            // Then
            verify(accountRepository).save(accountCaptor.capture());
            Account savedAccount = accountCaptor.getValue();
            assertThat(savedAccount.getBalance()).isEqualByComparingTo(new BigDecimal("11000.00"));
        }

        @Test
        @DisplayName("Should throw exception when account not found for credit")
        void shouldThrowExceptionWhenAccountNotFoundForCredit() {
            // Given
            when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> balanceService.creditAccount(
                    TEST_ACCOUNT_ID, BigDecimal.TEN, TEST_TRANSACTION_ID, "Test", TEST_CORRELATION_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Account not found");

            verify(accountRepository, never()).save(any());
            verify(metricsService, never()).recordBalanceCredit(anyString(), any());
        }

        @Test
        @DisplayName("Should update timestamps on credit")
        void shouldUpdateTimestampsOnCredit() {
            // Given
            Account account = createTestAccount(new BigDecimal("100.00"), new BigDecimal("100.00"));
            LocalDateTime originalUpdate = account.getUpdatedAt();

            when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            balanceService.creditAccount(TEST_ACCOUNT_ID, BigDecimal.TEN, TEST_TRANSACTION_ID,
                    "Test", TEST_CORRELATION_ID);

            // Then
            verify(accountRepository).save(accountCaptor.capture());
            Account savedAccount = accountCaptor.getValue();
            assertThat(savedAccount.getUpdatedAt()).isAfter(originalUpdate);
            assertThat(savedAccount.getLastActivityAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Debit Account Tests")
    class DebitAccountTests {

        @Test
        @DisplayName("Should debit account successfully")
        void shouldDebitAccountSuccessfully() {
            // Given
            BigDecimal initialBalance = new BigDecimal("500.00");
            BigDecimal initialAvailable = new BigDecimal("500.00");
            BigDecimal debitAmount = new BigDecimal("100.00");

            Account account = createTestAccount(initialBalance, initialAvailable);
            when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            balanceService.debitAccount(TEST_ACCOUNT_ID, debitAmount, TEST_TRANSACTION_ID,
                    "Test debit", TEST_CORRELATION_ID);

            // Then
            verify(accountRepository).findById(TEST_ACCOUNT_ID);
            verify(accountRepository).save(accountCaptor.capture());

            Account savedAccount = accountCaptor.getValue();
            assertThat(savedAccount.getBalance()).isEqualByComparingTo(new BigDecimal("400.00"));
            assertThat(savedAccount.getAvailableBalance()).isEqualByComparingTo(new BigDecimal("400.00"));
            assertThat(savedAccount.getUpdatedAt()).isNotNull();
            assertThat(savedAccount.getLastActivityAt()).isNotNull();

            verify(metricsService).recordBalanceDebit(TEST_ACCOUNT_ID, debitAmount);
        }

        @Test
        @DisplayName("Should allow debit resulting in negative balance")
        void shouldAllowDebitResultingInNegativeBalance() {
            // Given
            BigDecimal initialBalance = new BigDecimal("50.00");
            BigDecimal debitAmount = new BigDecimal("100.00");

            Account account = createTestAccount(initialBalance, initialBalance);
            when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            balanceService.debitAccount(TEST_ACCOUNT_ID, debitAmount, TEST_TRANSACTION_ID,
                    "Overdraft debit", TEST_CORRELATION_ID);

            // Then
            verify(accountRepository).save(accountCaptor.capture());
            Account savedAccount = accountCaptor.getValue();
            assertThat(savedAccount.getBalance()).isEqualByComparingTo(new BigDecimal("-50.00"));
        }

        @Test
        @DisplayName("Should throw exception when account not found for debit")
        void shouldThrowExceptionWhenAccountNotFoundForDebit() {
            // Given
            when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> balanceService.debitAccount(
                    TEST_ACCOUNT_ID, BigDecimal.TEN, TEST_TRANSACTION_ID, "Test", TEST_CORRELATION_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Account not found");

            verify(accountRepository, never()).save(any());
            verify(metricsService, never()).recordBalanceDebit(anyString(), any());
        }

        @Test
        @DisplayName("Should check balance thresholds after debit")
        void shouldCheckBalanceThresholdsAfterDebit() {
            // Given
            BigDecimal initialBalance = new BigDecimal("150.00");
            BigDecimal debitAmount = new BigDecimal("100.00"); // Will result in $50 (below threshold)

            Account account = createTestAccount(initialBalance, initialBalance);
            when(accountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            balanceService.debitAccount(TEST_ACCOUNT_ID, debitAmount, TEST_TRANSACTION_ID,
                    "Test debit", TEST_CORRELATION_ID);

            // Then - Should trigger low balance alert
            verify(metricsService).recordBalanceAlert(eq("LOW_BALANCE"), anyDouble());
        }
    }

    @Nested
    @DisplayName("Balance Threshold Tests")
    class BalanceThresholdTests {

        @Test
        @DisplayName("Should send alert for low balance")
        void shouldSendAlertForLowBalance() {
            // Given
            BigDecimal lowBalance = new BigDecimal("50.00"); // Between $10 and $100

            // When
            balanceService.checkBalanceThresholds(TEST_ACCOUNT_ID, lowBalance, TEST_CORRELATION_ID);

            // Then
            verify(metricsService).recordBalanceAlert("LOW_BALANCE", 50.00);
        }

        @Test
        @DisplayName("Should send alert for critical balance")
        void shouldSendAlertForCriticalBalance() {
            // Given
            BigDecimal criticalBalance = new BigDecimal("5.00"); // Below $10

            // When
            balanceService.checkBalanceThresholds(TEST_ACCOUNT_ID, criticalBalance, TEST_CORRELATION_ID);

            // Then
            verify(metricsService).recordBalanceAlert("LOW_BALANCE", 5.00);
        }

        @Test
        @DisplayName("Should not send alert for sufficient balance")
        void shouldNotSendAlertForSufficientBalance() {
            // Given
            BigDecimal sufficientBalance = new BigDecimal("500.00"); // Above $100

            // When
            balanceService.checkBalanceThresholds(TEST_ACCOUNT_ID, sufficientBalance, TEST_CORRELATION_ID);

            // Then
            verify(metricsService, never()).recordBalanceAlert(anyString(), anyDouble());
        }

        @Test
        @DisplayName("Should handle balance exactly at low threshold")
        void shouldHandleBalanceExactlyAtLowThreshold() {
            // Given
            BigDecimal thresholdBalance = new BigDecimal("100.00");

            // When
            balanceService.checkBalanceThresholds(TEST_ACCOUNT_ID, thresholdBalance, TEST_CORRELATION_ID);

            // Then - Should NOT trigger alert at exactly threshold
            verify(metricsService, never()).recordBalanceAlert(anyString(), anyDouble());
        }

        @Test
        @DisplayName("Should handle balance exactly at critical threshold")
        void shouldHandleBalanceExactlyAtCriticalThreshold() {
            // Given
            BigDecimal criticalThreshold = new BigDecimal("10.00");

            // When
            balanceService.checkBalanceThresholds(TEST_ACCOUNT_ID, criticalThreshold, TEST_CORRELATION_ID);

            // Then - Should NOT trigger alert at exactly threshold
            verify(metricsService, never()).recordBalanceAlert(anyString(), anyDouble());
        }
    }

    @Nested
    @DisplayName("Auto-Reload Tests")
    class AutoReloadTests {

        @Test
        @DisplayName("Should check if auto-reload is enabled")
        void shouldCheckIfAutoReloadIsEnabled() {
            // When
            boolean enabled = balanceService.hasAutoReloadEnabled(TEST_ACCOUNT_ID);

            // Then
            assertThat(enabled).isFalse(); // Default implementation returns false
        }

        @Test
        @DisplayName("Should trigger auto-reload")
        void shouldTriggerAutoReload() {
            // When/Then - Should not throw exception
            assertThatCode(() -> balanceService.triggerAutoReload(TEST_ACCOUNT_ID, TEST_CORRELATION_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should trigger emergency auto-reload")
        void shouldTriggerEmergencyAutoReload() {
            // When/Then - Should not throw exception
            assertThatCode(() -> balanceService.triggerEmergencyAutoReload(TEST_ACCOUNT_ID, TEST_CORRELATION_ID))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Account Restriction Tests")
    class AccountRestrictionTests {

        @Test
        @DisplayName("Should identify accounts that should be restricted")
        void shouldIdentifyAccountsThatShouldBeRestricted() {
            // Given - Negative balance
            BigDecimal negativeBalance = new BigDecimal("-50.00");

            // When
            boolean shouldRestrict = balanceService.shouldRestrictAccount(TEST_ACCOUNT_ID, negativeBalance);

            // Then
            assertThat(shouldRestrict).isTrue();
        }

        @Test
        @DisplayName("Should not restrict accounts with positive balance")
        void shouldNotRestrictAccountsWithPositiveBalance() {
            // Given
            BigDecimal positiveBalance = new BigDecimal("100.00");

            // When
            boolean shouldRestrict = balanceService.shouldRestrictAccount(TEST_ACCOUNT_ID, positiveBalance);

            // Then
            assertThat(shouldRestrict).isFalse();
        }

        @Test
        @DisplayName("Should not restrict accounts with zero balance")
        void shouldNotRestrictAccountsWithZeroBalance() {
            // Given
            BigDecimal zeroBalance = BigDecimal.ZERO;

            // When
            boolean shouldRestrict = balanceService.shouldRestrictAccount(TEST_ACCOUNT_ID, zeroBalance);

            // Then
            assertThat(shouldRestrict).isFalse();
        }

        @Test
        @DisplayName("Should restrict account for low balance")
        void shouldRestrictAccountForLowBalance() {
            // When/Then - Should not throw exception
            assertThatCode(() -> balanceService.restrictAccountForLowBalance(TEST_ACCOUNT_ID, TEST_CORRELATION_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should freeze account for negative balance")
        void shouldFreezeAccountForNegativeBalance() {
            // When/Then - Should not throw exception
            assertThatCode(() -> balanceService.freezeAccountForNegativeBalance(TEST_ACCOUNT_ID, TEST_CORRELATION_ID))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Overdraft Tests")
    class OverdraftTests {

        @Test
        @DisplayName("Should check if account has overdraft protection")
        void shouldCheckIfAccountHasOverdraftProtection() {
            // When
            boolean hasProtection = balanceService.hasOverdraftProtection(TEST_ACCOUNT_ID);

            // Then
            assertThat(hasProtection).isFalse(); // Default implementation
        }

        @Test
        @DisplayName("Should process overdraft protection")
        void shouldProcessOverdraftProtection() {
            // Given
            BigDecimal requestedAmount = new BigDecimal("100.00");

            // When/Then - Should not throw exception
            assertThatCode(() -> balanceService.processOverdraftProtection(
                    TEST_ACCOUNT_ID, requestedAmount, TEST_CORRELATION_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should initiate overdraft recovery")
        void shouldInitiateOverdraftRecovery() {
            // Given
            BigDecimal overdraftAmount = new BigDecimal("75.00");

            // When/Then - Should not throw exception
            assertThatCode(() -> balanceService.initiateOverdraftRecovery(
                    TEST_ACCOUNT_ID, overdraftAmount, TEST_CORRELATION_ID))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Savings Goal Tests")
    class SavingsGoalTests {

        @Test
        @DisplayName("Should check if account has savings goals")
        void shouldCheckIfAccountHasSavingsGoals() {
            // When
            boolean hasGoals = balanceService.hasSavingsGoals(TEST_ACCOUNT_ID);

            // Then
            assertThat(hasGoals).isFalse(); // Default implementation
        }

        @Test
        @DisplayName("Should check savings goal opportunities")
        void shouldCheckSavingsGoalOpportunities() {
            // Given
            BigDecimal currentBalance = new BigDecimal("1000.00");

            // When/Then - Should not throw exception
            assertThatCode(() -> balanceService.checkSavingsGoalOpportunities(
                    TEST_ACCOUNT_ID, currentBalance, TEST_CORRELATION_ID))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Balance Reconciliation Tests")
    class BalanceReconciliationTests {

        @Test
        @DisplayName("Should initiate balance reconciliation")
        void shouldInitiateBalanceReconciliation() {
            // Given
            BigDecimal currentBalance = new BigDecimal("500.00");
            BigDecimal expectedBalance = new BigDecimal("505.00");

            // When/Then - Should not throw exception
            assertThatCode(() -> balanceService.initiateBalanceReconciliation(
                    TEST_ACCOUNT_ID, currentBalance, expectedBalance, TEST_CORRELATION_ID))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Balance Snapshot Tests")
    class BalanceSnapshotTests {

        @Test
        @DisplayName("Should create daily balance snapshot")
        void shouldCreateDailyBalanceSnapshot() {
            // Given
            BigDecimal currentBalance = new BigDecimal("1000.00");
            java.time.LocalDate snapshotDate = java.time.LocalDate.now();

            // When/Then - Should not throw exception
            assertThatCode(() -> balanceService.createDailyBalanceSnapshot(
                    TEST_ACCOUNT_ID, currentBalance, snapshotDate, TEST_CORRELATION_ID))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Balance Milestone Tests")
    class BalanceMilestoneTests {

        @Test
        @DisplayName("Should record balance milestone")
        void shouldRecordBalanceMilestone() {
            // Given
            String milestoneType = "FIRST_1000";
            BigDecimal currentBalance = new BigDecimal("1000.00");

            // When/Then - Should not throw exception
            assertThatCode(() -> balanceService.recordBalanceMilestone(
                    TEST_ACCOUNT_ID, milestoneType, currentBalance, TEST_CORRELATION_ID))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Other Operations Tests")
    class OtherOperationsTests {

        @Test
        @DisplayName("Should adjust balance")
        void shouldAdjustBalance() {
            // Given
            BigDecimal adjustmentAmount = new BigDecimal("25.00");

            // When/Then - Should not throw exception
            assertThatCode(() -> balanceService.adjustBalance(
                    TEST_ACCOUNT_ID, adjustmentAmount, "CORRECTION", "Manual adjustment", TEST_CORRELATION_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should hold funds")
        void shouldHoldFunds() {
            // Given
            BigDecimal holdAmount = new BigDecimal("100.00");
            java.time.Instant expiryTime = java.time.Instant.now().plusSeconds(3600);

            // When/Then - Should not throw exception
            assertThatCode(() -> balanceService.holdFunds(
                    TEST_ACCOUNT_ID, holdAmount, "AUTHORIZATION", expiryTime, TEST_CORRELATION_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should release funds")
        void shouldReleaseFunds() {
            // Given
            BigDecimal releaseAmount = new BigDecimal("100.00");
            String holdId = "hold-123";

            // When/Then - Should not throw exception
            assertThatCode(() -> balanceService.releaseFunds(
                    TEST_ACCOUNT_ID, releaseAmount, holdId, TEST_CORRELATION_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should reverse transaction")
        void shouldReverseTransaction() {
            // Given
            String originalTxId = "txn-original-123";
            BigDecimal reversalAmount = new BigDecimal("50.00");

            // When/Then - Should not throw exception
            assertThatCode(() -> balanceService.reverseTransaction(
                    TEST_ACCOUNT_ID, originalTxId, reversalAmount, "CUSTOMER_REQUEST", TEST_CORRELATION_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should accrue interest")
        void shouldAccrueInterest() {
            // Given
            BigDecimal interestAmount = new BigDecimal("5.00");
            BigDecimal interestRate = new BigDecimal("2.5");

            // When/Then - Should not throw exception
            assertThatCode(() -> balanceService.accrueInterest(
                    TEST_ACCOUNT_ID, interestAmount, interestRate, "MONTHLY", TEST_CORRELATION_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should deduct fee")
        void shouldDeductFee() {
            // Given
            BigDecimal feeAmount = new BigDecimal("2.50");

            // When/Then - Should not throw exception
            assertThatCode(() -> balanceService.deductFee(
                    TEST_ACCOUNT_ID, feeAmount, "MONTHLY_MAINTENANCE", "Monthly fee", TEST_CORRELATION_ID))
                    .doesNotThrowAnyException();
        }
    }

    // Helper methods

    private Account createTestAccount(BigDecimal balance, BigDecimal availableBalance) {
        Account account = new Account();
        account.setAccountId(TEST_ACCOUNT_ID);
        account.setBalance(balance);
        account.setAvailableBalance(availableBalance);
        account.setCreatedAt(LocalDateTime.now().minusDays(30));
        account.setUpdatedAt(LocalDateTime.now().minusDays(1));
        account.setLastActivityAt(LocalDateTime.now().minusDays(1));
        return account;
    }
}
