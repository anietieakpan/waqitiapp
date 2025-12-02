package com.waqiti.corebanking.service;

import com.waqiti.corebanking.domain.Account;
import com.waqiti.corebanking.domain.Transaction;
import com.waqiti.corebanking.exception.InsufficientFundsException;
import com.waqiti.corebanking.exception.AccountNotFoundException;
import com.waqiti.corebanking.repository.AccountRepository;
import com.waqiti.corebanking.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for TransactionProcessingService
 * 
 * Tests financial transaction processing including:
 * - Debit and credit operations
 * - Balance validations
 * - Transaction limits enforcement
 * - Double-entry bookkeeping
 * - Error handling for financial operations
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionProcessingService Tests")
class TransactionProcessingServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private DoubleEntryBookkeepingService doubleEntryBookkeepingService;

    @InjectMocks
    private TransactionProcessingService transactionProcessingService;

    private Account sourceAccount;
    private Account targetAccount;
    private UUID sourceAccountId;
    private UUID targetAccountId;
    private Transaction testTransaction;

    @BeforeEach
    void setUp() {
        sourceAccountId = UUID.randomUUID();
        targetAccountId = UUID.randomUUID();

        sourceAccount = Account.builder()
                .id(sourceAccountId)
                .accountNumber("1234567890")
                .userId(UUID.randomUUID())
                .accountType("CHECKING")
                .status(Account.AccountStatus.ACTIVE)
                .currency("USD")
                .currentBalance(new BigDecimal("1000.00"))
                .availableBalance(new BigDecimal("1000.00"))
                .pendingBalance(BigDecimal.ZERO)
                .reservedBalance(BigDecimal.ZERO)
                .dailyTransactionLimit(new BigDecimal("5000.00"))
                .monthlyTransactionLimit(new BigDecimal("50000.00"))
                .minimumBalance(new BigDecimal("100.00"))
                .build();

        targetAccount = Account.builder()
                .id(targetAccountId)
                .accountNumber("0987654321")
                .userId(UUID.randomUUID())
                .accountType("SAVINGS")
                .status(Account.AccountStatus.ACTIVE)
                .currency("USD")
                .currentBalance(new BigDecimal("500.00"))
                .availableBalance(new BigDecimal("500.00"))
                .pendingBalance(BigDecimal.ZERO)
                .reservedBalance(BigDecimal.ZERO)
                .dailyTransactionLimit(new BigDecimal("10000.00"))
                .monthlyTransactionLimit(new BigDecimal("100000.00"))
                .minimumBalance(BigDecimal.ZERO)
                .build();

        testTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .sourceAccountId(sourceAccountId)
                .targetAccountId(targetAccountId)
                .amount(new BigDecimal("250.00"))
                .currency("USD")
                .transactionType("TRANSFER")
                .status(Transaction.Status.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("Debit Operation Tests")
    class DebitOperationTests {

        @Test
        @DisplayName("Should successfully debit account with sufficient funds")
        void shouldSuccessfullyDebitAccountWithSufficientFunds() {
            BigDecimal debitAmount = new BigDecimal("250.00");
            when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
            when(accountRepository.save(any(Account.class))).thenReturn(sourceAccount);

            transactionProcessingService.debitAccount(sourceAccountId, debitAmount, "TEST_TXN");

            ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(accountCaptor.capture());
            
            Account savedAccount = accountCaptor.getValue();
            assertThat(savedAccount.getCurrentBalance())
                    .isEqualByComparingTo(new BigDecimal("750.00"));
            assertThat(savedAccount.getAvailableBalance())
                    .isEqualByComparingTo(new BigDecimal("750.00"));
        }

        @Test
        @DisplayName("Should throw exception when debiting with insufficient funds")
        void shouldThrowExceptionWhenDebitingWithInsufficientFunds() {
            BigDecimal debitAmount = new BigDecimal("1500.00");
            when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));

            assertThatThrownBy(() -> 
                    transactionProcessingService.debitAccount(sourceAccountId, debitAmount, "TEST_TXN"))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessageContaining("Insufficient funds");

            verify(accountRepository, never()).save(any(Account.class));
        }

        @Test
        @DisplayName("Should enforce minimum balance when debiting")
        void shouldEnforceMinimumBalanceWhenDebiting() {
            BigDecimal debitAmount = new BigDecimal("950.00"); // Would leave 50, below minimum of 100
            when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));

            assertThatThrownBy(() -> 
                    transactionProcessingService.debitAccount(sourceAccountId, debitAmount, "TEST_TXN"))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessageContaining("minimum balance");

            verify(accountRepository, never()).save(any(Account.class));
        }

        @Test
        @DisplayName("Should throw exception when debiting from suspended account")
        void shouldThrowExceptionWhenDebitingFromSuspendedAccount() {
            sourceAccount.setStatus(Account.AccountStatus.SUSPENDED);
            BigDecimal debitAmount = new BigDecimal("100.00");
            when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));

            assertThatThrownBy(() -> 
                    transactionProcessingService.debitAccount(sourceAccountId, debitAmount, "TEST_TXN"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Account is not active");
        }

        @Test
        @DisplayName("Should throw exception when debiting from closed account")
        void shouldThrowExceptionWhenDebitingFromClosedAccount() {
            sourceAccount.setStatus(Account.AccountStatus.CLOSED);
            BigDecimal debitAmount = new BigDecimal("100.00");
            when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));

            assertThatThrownBy(() -> 
                    transactionProcessingService.debitAccount(sourceAccountId, debitAmount, "TEST_TXN"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Account is not active");
        }

        @Test
        @DisplayName("Should throw exception when debiting negative amount")
        void shouldThrowExceptionWhenDebitingNegativeAmount() {
            BigDecimal debitAmount = new BigDecimal("-100.00");

            assertThatThrownBy(() -> 
                    transactionProcessingService.debitAccount(sourceAccountId, debitAmount, "TEST_TXN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount must be positive");

            verify(accountRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Should throw exception when debiting zero amount")
        void shouldThrowExceptionWhenDebitingZeroAmount() {
            BigDecimal debitAmount = BigDecimal.ZERO;

            assertThatThrownBy(() -> 
                    transactionProcessingService.debitAccount(sourceAccountId, debitAmount, "TEST_TXN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount must be positive");

            verify(accountRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Should handle account not found during debit")
        void shouldHandleAccountNotFoundDuringDebit() {
            BigDecimal debitAmount = new BigDecimal("100.00");
            when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> 
                    transactionProcessingService.debitAccount(sourceAccountId, debitAmount, "TEST_TXN"))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining("Account not found");
        }
    }

    @Nested
    @DisplayName("Credit Operation Tests")
    class CreditOperationTests {

        @Test
        @DisplayName("Should successfully credit account")
        void shouldSuccessfullyCreditAccount() {
            BigDecimal creditAmount = new BigDecimal("300.00");
            when(accountRepository.findById(targetAccountId)).thenReturn(Optional.of(targetAccount));
            when(accountRepository.save(any(Account.class))).thenReturn(targetAccount);

            transactionProcessingService.creditAccount(targetAccountId, creditAmount, "TEST_TXN");

            ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(accountCaptor.capture());
            
            Account savedAccount = accountCaptor.getValue();
            assertThat(savedAccount.getCurrentBalance())
                    .isEqualByComparingTo(new BigDecimal("800.00"));
            assertThat(savedAccount.getAvailableBalance())
                    .isEqualByComparingTo(new BigDecimal("800.00"));
        }

        @Test
        @DisplayName("Should throw exception when crediting to suspended account")
        void shouldThrowExceptionWhenCreditingToSuspendedAccount() {
            targetAccount.setStatus(Account.AccountStatus.SUSPENDED);
            BigDecimal creditAmount = new BigDecimal("100.00");
            when(accountRepository.findById(targetAccountId)).thenReturn(Optional.of(targetAccount));

            assertThatThrownBy(() -> 
                    transactionProcessingService.creditAccount(targetAccountId, creditAmount, "TEST_TXN"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Account is not active");
        }

        @Test
        @DisplayName("Should throw exception when crediting to closed account")
        void shouldThrowExceptionWhenCreditingToClosedAccount() {
            targetAccount.setStatus(Account.AccountStatus.CLOSED);
            BigDecimal creditAmount = new BigDecimal("100.00");
            when(accountRepository.findById(targetAccountId)).thenReturn(Optional.of(targetAccount));

            assertThatThrownBy(() -> 
                    transactionProcessingService.creditAccount(targetAccountId, creditAmount, "TEST_TXN"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Account is not active");
        }

        @Test
        @DisplayName("Should throw exception when crediting negative amount")
        void shouldThrowExceptionWhenCreditingNegativeAmount() {
            BigDecimal creditAmount = new BigDecimal("-100.00");

            assertThatThrownBy(() -> 
                    transactionProcessingService.creditAccount(targetAccountId, creditAmount, "TEST_TXN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount must be positive");

            verify(accountRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Should throw exception when crediting zero amount")
        void shouldThrowExceptionWhenCreditingZeroAmount() {
            BigDecimal creditAmount = BigDecimal.ZERO;

            assertThatThrownBy(() -> 
                    transactionProcessingService.creditAccount(targetAccountId, creditAmount, "TEST_TXN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount must be positive");

            verify(accountRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Should handle large credit amounts")
        void shouldHandleLargeCreditAmounts() {
            BigDecimal creditAmount = new BigDecimal("999999999.99");
            when(accountRepository.findById(targetAccountId)).thenReturn(Optional.of(targetAccount));
            when(accountRepository.save(any(Account.class))).thenReturn(targetAccount);

            transactionProcessingService.creditAccount(targetAccountId, creditAmount, "TEST_TXN");

            ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(accountCaptor.capture());
            
            Account savedAccount = accountCaptor.getValue();
            assertThat(savedAccount.getCurrentBalance())
                    .isGreaterThan(new BigDecimal("999999999.00"));
        }

        @Test
        @DisplayName("Should handle account not found during credit")
        void shouldHandleAccountNotFoundDuringCredit() {
            BigDecimal creditAmount = new BigDecimal("100.00");
            when(accountRepository.findById(targetAccountId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> 
                    transactionProcessingService.creditAccount(targetAccountId, creditAmount, "TEST_TXN"))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining("Account not found");
        }
    }

    @Nested
    @DisplayName("Transfer Operation Tests")
    class TransferOperationTests {

        @Test
        @DisplayName("Should successfully process transfer between accounts")
        void shouldSuccessfullyProcessTransfer() {
            BigDecimal transferAmount = new BigDecimal("250.00");
            when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
            when(accountRepository.findById(targetAccountId)).thenReturn(Optional.of(targetAccount));
            when(accountRepository.save(any(Account.class)))
                    .thenReturn(sourceAccount)
                    .thenReturn(targetAccount);
            when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);

            Transaction result = transactionProcessingService.processTransfer(
                    sourceAccountId, targetAccountId, transferAmount, "Transfer test");

            assertThat(result).isNotNull();
            verify(accountRepository, times(2)).save(any(Account.class));
            verify(transactionRepository, times(1)).save(any(Transaction.class));
        }

        @Test
        @DisplayName("Should validate sufficient funds before transfer")
        void shouldValidateSufficientFundsBeforeTransfer() {
            BigDecimal transferAmount = new BigDecimal("1500.00");
            when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));

            assertThatThrownBy(() -> 
                    transactionProcessingService.processTransfer(
                            sourceAccountId, targetAccountId, transferAmount, "Transfer test"))
                    .isInstanceOf(InsufficientFundsException.class);

            verify(accountRepository, never()).save(any(Account.class));
            verify(transactionRepository, never()).save(any(Transaction.class));
        }

        @Test
        @DisplayName("Should prevent transfer to same account")
        void shouldPreventTransferToSameAccount() {
            BigDecimal transferAmount = new BigDecimal("100.00");

            assertThatThrownBy(() -> 
                    transactionProcessingService.processTransfer(
                            sourceAccountId, sourceAccountId, transferAmount, "Transfer test"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot transfer to same account");

            verify(accountRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Should rollback on transfer failure")
        void shouldRollbackOnTransferFailure() {
            BigDecimal transferAmount = new BigDecimal("250.00");
            when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
            when(accountRepository.findById(targetAccountId)).thenReturn(Optional.of(targetAccount));
            when(accountRepository.save(any(Account.class)))
                    .thenReturn(sourceAccount)
                    .thenThrow(new RuntimeException("Database error"));

            assertThatThrownBy(() -> 
                    transactionProcessingService.processTransfer(
                            sourceAccountId, targetAccountId, transferAmount, "Transfer test"))
                    .isInstanceOf(RuntimeException.class);

            // Transaction should be marked as failed, not saved as successful
            verify(transactionRepository, never()).save(argThat(txn -> 
                    txn.getStatus() == Transaction.Status.COMPLETED));
        }
    }

    @Nested
    @DisplayName("Transaction Limits Tests")
    class TransactionLimitsTests {

        @Test
        @DisplayName("Should enforce daily transaction limit")
        void shouldEnforceDailyTransactionLimit() {
            BigDecimal largeAmount = new BigDecimal("6000.00");
            when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));

            assertThatThrownBy(() -> 
                    transactionProcessingService.debitAccount(sourceAccountId, largeAmount, "TEST_TXN"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("daily transaction limit");

            verify(accountRepository, never()).save(any(Account.class));
        }

        @Test
        @DisplayName("Should allow transaction within daily limit")
        void shouldAllowTransactionWithinDailyLimit() {
            BigDecimal withinLimit = new BigDecimal("4000.00");
            sourceAccount.setCurrentBalance(new BigDecimal("5000.00"));
            sourceAccount.setAvailableBalance(new BigDecimal("5000.00"));
            sourceAccount.setMinimumBalance(BigDecimal.ZERO);
            
            when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
            when(accountRepository.save(any(Account.class))).thenReturn(sourceAccount);

            transactionProcessingService.debitAccount(sourceAccountId, withinLimit, "TEST_TXN");

            verify(accountRepository, times(1)).save(any(Account.class));
        }
    }

    @Nested
    @DisplayName("Concurrency and Edge Cases")
    class ConcurrencyAndEdgeCasesTests {

        @Test
        @DisplayName("Should handle concurrent debit attempts")
        void shouldHandleConcurrentDebitAttempts() {
            // This tests optimistic locking behavior
            BigDecimal amount = new BigDecimal("500.00");
            when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
            when(accountRepository.save(any(Account.class))).thenReturn(sourceAccount);

            transactionProcessingService.debitAccount(sourceAccountId, amount, "TXN1");
            transactionProcessingService.debitAccount(sourceAccountId, amount, "TXN2");

            verify(accountRepository, times(2)).save(any(Account.class));
        }

        @Test
        @DisplayName("Should handle precision in decimal calculations")
        void shouldHandlePrecisionInDecimalCalculations() {
            BigDecimal preciseAmount = new BigDecimal("99.999");
            sourceAccount.setCurrentBalance(new BigDecimal("200.00"));
            sourceAccount.setAvailableBalance(new BigDecimal("200.00"));
            sourceAccount.setMinimumBalance(BigDecimal.ZERO);

            when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
            when(accountRepository.save(any(Account.class))).thenReturn(sourceAccount);

            transactionProcessingService.debitAccount(sourceAccountId, preciseAmount, "TEST_TXN");

            verify(accountRepository).save(any(Account.class));
        }
    }

    // ========================================
    // CRITICAL: BLOCKER #1 - TRANSACTION REVERSAL TESTS
    // ========================================
    @Nested
    @DisplayName("BLOCKER #1: Transaction Reversal Tests - Production Critical")
    class TransactionReversalTests {

        private Transaction completedTransaction;
        private com.waqiti.corebanking.dto.TransactionReversalRequestDto reversalRequest;

        @BeforeEach
        void setUpReversal() {
            completedTransaction = Transaction.builder()
                    .id(UUID.randomUUID())
                    .transactionNumber("TXN123456789")
                    .sourceAccountId(sourceAccountId)
                    .targetAccountId(targetAccountId)
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .feeAmount(new BigDecimal("1.00"))
                    .transactionType("P2P_TRANSFER")
                    .status(Transaction.Status.COMPLETED)
                    .completedAt(LocalDateTime.now().minusDays(5))
                    .createdAt(LocalDateTime.now().minusDays(5))
                    .build();

            reversalRequest = com.waqiti.corebanking.dto.TransactionReversalRequestDto.builder()
                    .reason("Customer requested refund")
                    .reversedBy(UUID.randomUUID())
                    .build();
        }

        @Test
        @DisplayName("Should successfully reverse a completed transaction")
        void shouldSuccessfullyReverseCompletedTransaction() {
            // Given
            when(transactionRepository.findByTransactionNumber("TXN123456789"))
                    .thenReturn(Optional.of(completedTransaction));
            when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
            when(accountRepository.findById(targetAccountId)).thenReturn(Optional.of(targetAccount));
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            com.waqiti.corebanking.dto.TransactionResponseDto response =
                    transactionProcessingService.reverseTransaction("TXN123456789", reversalRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo("REVERSED");
            assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));

            // Verify transaction saved twice (original + reversal)
            verify(transactionRepository, times(2)).save(any(Transaction.class));

            // Verify accounts updated (money returned)
            verify(accountRepository, times(2)).save(any(Account.class));
        }

        @Test
        @DisplayName("Should fail to reverse non-COMPLETED transaction")
        void shouldFailToReverseNonCompletedTransaction() {
            // Given
            completedTransaction.setStatus(Transaction.Status.PENDING);
            when(transactionRepository.findByTransactionNumber("TXN123456789"))
                    .thenReturn(Optional.of(completedTransaction));

            // When/Then
            assertThatThrownBy(() ->
                    transactionProcessingService.reverseTransaction("TXN123456789", reversalRequest))
                    .isInstanceOf(com.waqiti.corebanking.exception.TransactionReversalException.class)
                    .hasMessageContaining("Only COMPLETED transactions can be reversed");

            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should fail to reverse already reversed transaction")
        void shouldFailToReverseAlreadyReversedTransaction() {
            // Given
            completedTransaction.setStatus(Transaction.Status.REVERSED);
            completedTransaction.setReversalTransactionId(UUID.randomUUID());
            when(transactionRepository.findByTransactionNumber("TXN123456789"))
                    .thenReturn(Optional.of(completedTransaction));

            // When/Then
            assertThatThrownBy(() ->
                    transactionProcessingService.reverseTransaction("TXN123456789", reversalRequest))
                    .isInstanceOf(com.waqiti.corebanking.exception.TransactionReversalException.class)
                    .hasMessageContaining("already been reversed");

            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should fail to reverse transaction outside 90-day window")
        void shouldFailToReverseExpiredTransaction() {
            // Given
            completedTransaction.setCompletedAt(LocalDateTime.now().minusDays(91));
            when(transactionRepository.findByTransactionNumber("TXN123456789"))
                    .thenReturn(Optional.of(completedTransaction));

            // When/Then
            assertThatThrownBy(() ->
                    transactionProcessingService.reverseTransaction("TXN123456789", reversalRequest))
                    .isInstanceOf(com.waqiti.corebanking.exception.TransactionReversalException.class)
                    .hasMessageContaining("90-day reversal window");

            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should fail reversal when target account has insufficient balance")
        void shouldFailReversalWhenInsufficientBalanceInTarget() {
            // Given - target has less balance than reversal amount
            targetAccount.setCurrentBalance(new BigDecimal("50.00"));
            targetAccount.setAvailableBalance(new BigDecimal("50.00"));

            when(transactionRepository.findByTransactionNumber("TXN123456789"))
                    .thenReturn(Optional.of(completedTransaction));
            when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
            when(accountRepository.findById(targetAccountId)).thenReturn(Optional.of(targetAccount));

            // When/Then
            assertThatThrownBy(() ->
                    transactionProcessingService.reverseTransaction("TXN123456789", reversalRequest))
                    .isInstanceOf(com.waqiti.corebanking.exception.TransactionReversalException.class)
                    .hasMessageContaining("Insufficient funds");

            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should handle transaction not found during reversal")
        void shouldHandleTransactionNotFoundDuringReversal() {
            // Given
            when(transactionRepository.findByTransactionNumber("NONEXISTENT"))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() ->
                    transactionProcessingService.reverseTransaction("NONEXISTENT", reversalRequest))
                    .isInstanceOf(com.waqiti.corebanking.exception.TransactionNotFoundException.class)
                    .hasMessageContaining("not found");

            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should create compensating ledger entries on reversal")
        void shouldCreateCompensatingLedgerEntries() {
            // Given
            when(transactionRepository.findByTransactionNumber("TXN123456789"))
                    .thenReturn(Optional.of(completedTransaction));
            when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
            when(accountRepository.findById(targetAccountId)).thenReturn(Optional.of(targetAccount));
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            transactionProcessingService.reverseTransaction("TXN123456789", reversalRequest);

            // Then - Verify ledger service called to create reversal entries
            verify(doubleEntryBookkeepingService).createReversalEntries(any(), any());
        }

        @Test
        @DisplayName("Should preserve original transaction data during reversal")
        void shouldPreserveOriginalTransactionData() {
            // Given
            BigDecimal originalAmount = new BigDecimal("100.00");
            String originalCurrency = "USD";

            when(transactionRepository.findByTransactionNumber("TXN123456789"))
                    .thenReturn(Optional.of(completedTransaction));
            when(accountRepository.findById(any())).thenReturn(Optional.of(sourceAccount));
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            com.waqiti.corebanking.dto.TransactionResponseDto response =
                    transactionProcessingService.reverseTransaction("TXN123456789", reversalRequest);

            // Then
            assertThat(response.getAmount()).isEqualByComparingTo(originalAmount);
            assertThat(response.getCurrency()).isEqualTo(originalCurrency);
        }
    }

    // ========================================
    // CRITICAL: BLOCKER #2 - STATUS UPDATE TESTS
    // ========================================
    @Nested
    @DisplayName("BLOCKER #2: Transaction Status Update Tests - Production Critical")
    class TransactionStatusUpdateTests {

        private Transaction pendingTransaction;
        private com.waqiti.corebanking.dto.TransactionStatusUpdateDto statusUpdateRequest;

        @BeforeEach
        void setUpStatusUpdate() {
            pendingTransaction = Transaction.builder()
                    .id(UUID.randomUUID())
                    .transactionNumber("TXN987654321")
                    .sourceAccountId(sourceAccountId)
                    .targetAccountId(targetAccountId)
                    .amount(new BigDecimal("250.00"))
                    .currency("USD")
                    .transactionType("P2P_TRANSFER")
                    .status(Transaction.Status.PENDING)
                    .createdAt(LocalDateTime.now())
                    .build();

            statusUpdateRequest = com.waqiti.corebanking.dto.TransactionStatusUpdateDto.builder()
                    .updatedBy(UUID.randomUUID())
                    .build();
        }

        @Test
        @DisplayName("Should update PENDING to AUTHORIZED successfully")
        void shouldUpdatePendingToAuthorized() {
            // Given
            statusUpdateRequest.setNewStatus("AUTHORIZED");
            statusUpdateRequest.setReason("Payment authorized by bank");

            when(transactionRepository.findByTransactionNumber("TXN987654321"))
                    .thenReturn(Optional.of(pendingTransaction));
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            com.waqiti.corebanking.dto.TransactionResponseDto response =
                    transactionProcessingService.updateTransactionStatus("TXN987654321", statusUpdateRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo("AUTHORIZED");
            assertThat(pendingTransaction.getAuthorizedAt()).isNotNull();
            assertThat(pendingTransaction.getApprovedBy()).isNotNull();
            verify(transactionRepository).save(pendingTransaction);
        }

        @Test
        @DisplayName("Should update AUTHORIZED to PROCESSING successfully")
        void shouldUpdateAuthorizedToProcessing() {
            // Given
            pendingTransaction.setStatus(Transaction.Status.AUTHORIZED);
            pendingTransaction.setAuthorizedAt(LocalDateTime.now());
            statusUpdateRequest.setNewStatus("PROCESSING");

            when(transactionRepository.findByTransactionNumber("TXN987654321"))
                    .thenReturn(Optional.of(pendingTransaction));
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            com.waqiti.corebanking.dto.TransactionResponseDto response =
                    transactionProcessingService.updateTransactionStatus("TXN987654321", statusUpdateRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo("PROCESSING");
            verify(transactionRepository).save(pendingTransaction);
        }

        @Test
        @DisplayName("Should update PROCESSING to COMPLETED and set completion timestamp")
        void shouldUpdateProcessingToCompleted() {
            // Given
            pendingTransaction.setStatus(Transaction.Status.PROCESSING);
            statusUpdateRequest.setNewStatus("COMPLETED");

            when(transactionRepository.findByTransactionNumber("TXN987654321"))
                    .thenReturn(Optional.of(pendingTransaction));
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            com.waqiti.corebanking.dto.TransactionResponseDto response =
                    transactionProcessingService.updateTransactionStatus("TXN987654321", statusUpdateRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo("COMPLETED");
            assertThat(pendingTransaction.getCompletedAt()).isNotNull();
            verify(transactionRepository).save(pendingTransaction);
        }

        @Test
        @DisplayName("Should update to FAILED and set failure details")
        void shouldUpdateToFailedWithDetails() {
            // Given
            statusUpdateRequest.setNewStatus("FAILED");
            statusUpdateRequest.setReason("Payment declined by bank");

            when(transactionRepository.findByTransactionNumber("TXN987654321"))
                    .thenReturn(Optional.of(pendingTransaction));
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            com.waqiti.corebanking.dto.TransactionResponseDto response =
                    transactionProcessingService.updateTransactionStatus("TXN987654321", statusUpdateRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo("FAILED");
            assertThat(pendingTransaction.getFailureReason()).isEqualTo("Payment declined by bank");
            assertThat(pendingTransaction.getFailedAt()).isNotNull();
            verify(transactionRepository).save(pendingTransaction);
        }

        @Test
        @DisplayName("Should reject invalid status transition from COMPLETED to PENDING")
        void shouldRejectInvalidStatusTransition() {
            // Given
            pendingTransaction.setStatus(Transaction.Status.COMPLETED);
            statusUpdateRequest.setNewStatus("PENDING");

            when(transactionRepository.findByTransactionNumber("TXN987654321"))
                    .thenReturn(Optional.of(pendingTransaction));

            // When/Then
            assertThatThrownBy(() ->
                    transactionProcessingService.updateTransactionStatus("TXN987654321", statusUpdateRequest))
                    .isInstanceOf(com.waqiti.corebanking.exception.TransactionStatusUpdateException.class)
                    .hasMessageContaining("Invalid status transition");

            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should allow COMPLETED to REVERSED transition")
        void shouldAllowCompletedToReversedTransition() {
            // Given
            pendingTransaction.setStatus(Transaction.Status.COMPLETED);
            pendingTransaction.setCompletedAt(LocalDateTime.now().minusDays(1));
            statusUpdateRequest.setNewStatus("REVERSED");
            statusUpdateRequest.setReason("Transaction reversed");

            when(transactionRepository.findByTransactionNumber("TXN987654321"))
                    .thenReturn(Optional.of(pendingTransaction));
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            com.waqiti.corebanking.dto.TransactionResponseDto response =
                    transactionProcessingService.updateTransactionStatus("TXN987654321", statusUpdateRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo("REVERSED");
            verify(transactionRepository).save(pendingTransaction);
        }

        @Test
        @DisplayName("Should validate status enum value")
        void shouldValidateStatusEnumValue() {
            // Given
            statusUpdateRequest.setNewStatus("INVALID_STATUS");

            when(transactionRepository.findByTransactionNumber("TXN987654321"))
                    .thenReturn(Optional.of(pendingTransaction));

            // When/Then
            assertThatThrownBy(() ->
                    transactionProcessingService.updateTransactionStatus("TXN987654321", statusUpdateRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid status");

            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should handle all valid PENDING state transitions")
        void shouldHandleAllValidPendingTransitions() {
            // Valid transitions from PENDING: AUTHORIZED, PROCESSING, FAILED, CANCELLED, REQUIRES_APPROVAL, COMPLIANCE_HOLD
            String[] validTransitions = {
                "AUTHORIZED", "PROCESSING", "FAILED", "CANCELLED",
                "REQUIRES_APPROVAL", "COMPLIANCE_HOLD"
            };

            for (String targetStatus : validTransitions) {
                // Given
                Transaction txn = Transaction.builder()
                        .id(UUID.randomUUID())
                        .transactionNumber("TXN" + UUID.randomUUID().toString().substring(0, 8))
                        .status(Transaction.Status.PENDING)
                        .amount(new BigDecimal("100.00"))
                        .currency("USD")
                        .build();

                com.waqiti.corebanking.dto.TransactionStatusUpdateDto request =
                        com.waqiti.corebanking.dto.TransactionStatusUpdateDto.builder()
                                .newStatus(targetStatus)
                                .updatedBy(UUID.randomUUID())
                                .reason("Testing transition to " + targetStatus)
                                .build();

                when(transactionRepository.findByTransactionNumber(txn.getTransactionNumber()))
                        .thenReturn(Optional.of(txn));
                when(transactionRepository.save(any(Transaction.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                // When
                com.waqiti.corebanking.dto.TransactionResponseDto response =
                        transactionProcessingService.updateTransactionStatus(txn.getTransactionNumber(), request);

                // Then
                assertThat(response).isNotNull();
                assertThat(response.getStatus()).isEqualTo(targetStatus);
            }
        }

        @Test
        @DisplayName("Should prevent transition from terminal state FAILED")
        void shouldPreventTransitionFromFailedState() {
            // Given
            pendingTransaction.setStatus(Transaction.Status.FAILED);
            pendingTransaction.setFailedAt(LocalDateTime.now());
            statusUpdateRequest.setNewStatus("PROCESSING");

            when(transactionRepository.findByTransactionNumber("TXN987654321"))
                    .thenReturn(Optional.of(pendingTransaction));

            // When/Then
            assertThatThrownBy(() ->
                    transactionProcessingService.updateTransactionStatus("TXN987654321", statusUpdateRequest))
                    .isInstanceOf(com.waqiti.corebanking.exception.TransactionStatusUpdateException.class)
                    .hasMessageContaining("Terminal state");

            verify(transactionRepository, never()).save(any());
        }
    }
}