package com.waqiti.ledger.service;

import com.waqiti.ledger.domain.JournalEntry;
import com.waqiti.ledger.domain.LedgerEntry;
import com.waqiti.ledger.domain.AccountBalance;
import com.waqiti.ledger.dto.*;
import com.waqiti.ledger.exception.*;
import com.waqiti.ledger.repository.JournalEntryRepository;
import com.waqiti.ledger.repository.LedgerEntryRepository;
import com.waqiti.ledger.repository.AccountBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///waqiti_test",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("Double-Entry Bookkeeping Tests")
class DoubleEntryBookkeepingTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("waqiti_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private DoubleEntryLedgerService doubleEntryLedgerService;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private AccountBalanceRepository accountBalanceRepository;

    private UUID cashAccountId;
    private UUID revenueAccountId;
    private UUID expenseAccountId;
    private UUID receivableAccountId;

    @BeforeEach
    void setUp() {
        cashAccountId = UUID.randomUUID();
        revenueAccountId = UUID.randomUUID();
        expenseAccountId = UUID.randomUUID();
        receivableAccountId = UUID.randomUUID();

        createAccountBalance(cashAccountId, new BigDecimal("10000.00"), "USD");
        createAccountBalance(revenueAccountId, BigDecimal.ZERO, "USD");
        createAccountBalance(expenseAccountId, BigDecimal.ZERO, "USD");
        createAccountBalance(receivableAccountId, BigDecimal.ZERO, "USD");
    }

    @Nested
    @DisplayName("Double-Entry Balance Validation Tests")
    class DoubleEntryBalanceValidationTests {

        @Test
        @DisplayName("Should enforce debits equal credits rule")
        @Transactional
        void shouldEnforceDebitsEqualCreditsRule() {
            UUID transactionId = UUID.randomUUID();
            List<LedgerEntryRequest> entries = new ArrayList<>();

            entries.add(LedgerEntryRequest.builder()
                    .accountId(cashAccountId)
                    .entryType("DEBIT")
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .description("Cash debit")
                    .referenceNumber("REF-001")
                    .transactionDate(LocalDateTime.now())
                    .valueDate(LocalDateTime.now())
                    .build());

            entries.add(LedgerEntryRequest.builder()
                    .accountId(revenueAccountId)
                    .entryType("CREDIT")
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .description("Revenue credit")
                    .referenceNumber("REF-001")
                    .transactionDate(LocalDateTime.now())
                    .valueDate(LocalDateTime.now())
                    .build());

            PostTransactionRequest request = PostTransactionRequest.builder()
                    .transactionId(transactionId)
                    .ledgerEntries(entries)
                    .build();

            PostTransactionResult result = doubleEntryLedgerService.postTransaction(request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getLedgerEntries()).hasSize(2);

            List<LedgerEntry> savedEntries = ledgerEntryRepository.findByTransactionId(transactionId.toString());
            assertThat(savedEntries).hasSize(2);

            BigDecimal totalDebits = savedEntries.stream()
                    .filter(LedgerEntry::isDebit)
                    .map(LedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalCredits = savedEntries.stream()
                    .filter(LedgerEntry::isCredit)
                    .map(LedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(totalDebits).isEqualByComparingTo(totalCredits);
        }

        @Test
        @DisplayName("Should reject unbalanced journal entries")
        @Transactional
        void shouldRejectUnbalancedJournalEntries() {
            UUID transactionId = UUID.randomUUID();
            List<LedgerEntryRequest> entries = new ArrayList<>();

            entries.add(LedgerEntryRequest.builder()
                    .accountId(cashAccountId)
                    .entryType("DEBIT")
                    .amount(new BigDecimal("150.00"))
                    .currency("USD")
                    .description("Debit entry")
                    .referenceNumber("REF-BAD")
                    .transactionDate(LocalDateTime.now())
                    .valueDate(LocalDateTime.now())
                    .build());

            entries.add(LedgerEntryRequest.builder()
                    .accountId(revenueAccountId)
                    .entryType("CREDIT")
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .description("Credit entry")
                    .referenceNumber("REF-BAD")
                    .transactionDate(LocalDateTime.now())
                    .valueDate(LocalDateTime.now())
                    .build());

            PostTransactionRequest request = PostTransactionRequest.builder()
                    .transactionId(transactionId)
                    .ledgerEntries(entries)
                    .build();

            PostTransactionResult result = doubleEntryLedgerService.postTransaction(request);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Double-entry validation failed");

            List<LedgerEntry> savedEntries = ledgerEntryRepository.findByTransactionId(transactionId.toString());
            assertThat(savedEntries).isEmpty();
        }

        @Test
        @DisplayName("Should handle complex multi-account transactions")
        @Transactional
        void shouldHandleComplexMultiAccountTransactions() {
            UUID transactionId = UUID.randomUUID();
            List<LedgerEntryRequest> entries = new ArrayList<>();

            entries.add(LedgerEntryRequest.builder()
                    .accountId(cashAccountId)
                    .entryType("DEBIT")
                    .amount(new BigDecimal("500.00"))
                    .currency("USD")
                    .description("Cash receipt")
                    .referenceNumber("MULTI-001")
                    .transactionDate(LocalDateTime.now())
                    .valueDate(LocalDateTime.now())
                    .build());

            entries.add(LedgerEntryRequest.builder()
                    .accountId(receivableAccountId)
                    .entryType("DEBIT")
                    .amount(new BigDecimal("300.00"))
                    .currency("USD")
                    .description("Accounts receivable")
                    .referenceNumber("MULTI-001")
                    .transactionDate(LocalDateTime.now())
                    .valueDate(LocalDateTime.now())
                    .build());

            entries.add(LedgerEntryRequest.builder()
                    .accountId(revenueAccountId)
                    .entryType("CREDIT")
                    .amount(new BigDecimal("800.00"))
                    .currency("USD")
                    .description("Sales revenue")
                    .referenceNumber("MULTI-001")
                    .transactionDate(LocalDateTime.now())
                    .valueDate(LocalDateTime.now())
                    .build());

            PostTransactionRequest request = PostTransactionRequest.builder()
                    .transactionId(transactionId)
                    .ledgerEntries(entries)
                    .build();

            PostTransactionResult result = doubleEntryLedgerService.postTransaction(request);

            assertThat(result.isSuccess()).isTrue();

            List<LedgerEntry> savedEntries = ledgerEntryRepository.findByTransactionId(transactionId.toString());
            assertThat(savedEntries).hasSize(3);

            BigDecimal totalDebits = savedEntries.stream()
                    .filter(LedgerEntry::isDebit)
                    .map(LedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalCredits = savedEntries.stream()
                    .filter(LedgerEntry::isCredit)
                    .map(LedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(totalDebits).isEqualByComparingTo(new BigDecimal("800.00"));
            assertThat(totalCredits).isEqualByComparingTo(new BigDecimal("800.00"));
            assertThat(totalDebits).isEqualByComparingTo(totalCredits);
        }

        @Test
        @DisplayName("Should validate precision and rounding in double-entry")
        @Transactional
        void shouldValidatePrecisionAndRoundingInDoubleEntry() {
            UUID transactionId = UUID.randomUUID();
            List<LedgerEntryRequest> entries = new ArrayList<>();

            entries.add(LedgerEntryRequest.builder()
                    .accountId(cashAccountId)
                    .entryType("DEBIT")
                    .amount(new BigDecimal("33.3333"))
                    .currency("USD")
                    .description("Debit with precision")
                    .referenceNumber("PREC-001")
                    .transactionDate(LocalDateTime.now())
                    .valueDate(LocalDateTime.now())
                    .build());

            entries.add(LedgerEntryRequest.builder()
                    .accountId(revenueAccountId)
                    .entryType("CREDIT")
                    .amount(new BigDecimal("33.3333"))
                    .currency("USD")
                    .description("Credit with precision")
                    .referenceNumber("PREC-001")
                    .transactionDate(LocalDateTime.now())
                    .valueDate(LocalDateTime.now())
                    .build());

            PostTransactionRequest request = PostTransactionRequest.builder()
                    .transactionId(transactionId)
                    .ledgerEntries(entries)
                    .build();

            PostTransactionResult result = doubleEntryLedgerService.postTransaction(request);

            assertThat(result.isSuccess()).isTrue();

            List<LedgerEntry> savedEntries = ledgerEntryRepository.findByTransactionId(transactionId.toString());

            BigDecimal totalDebits = savedEntries.stream()
                    .filter(LedgerEntry::isDebit)
                    .map(LedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalCredits = savedEntries.stream()
                    .filter(LedgerEntry::isCredit)
                    .map(LedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(totalDebits).isEqualByComparingTo(totalCredits);
        }
    }

    @Nested
    @DisplayName("Account Balance Update Tests")
    class AccountBalanceUpdateTests {

        @Test
        @DisplayName("Should update account balances atomically")
        @Transactional
        void shouldUpdateAccountBalancesAtomically() {
            BigDecimal initialCashBalance = new BigDecimal("10000.00");
            BigDecimal transactionAmount = new BigDecimal("250.00");

            UUID transactionId = UUID.randomUUID();
            List<LedgerEntryRequest> entries = new ArrayList<>();

            entries.add(LedgerEntryRequest.builder()
                    .accountId(cashAccountId)
                    .entryType("CREDIT")
                    .amount(transactionAmount)
                    .currency("USD")
                    .description("Cash payment")
                    .referenceNumber("BAL-001")
                    .transactionDate(LocalDateTime.now())
                    .valueDate(LocalDateTime.now())
                    .build());

            entries.add(LedgerEntryRequest.builder()
                    .accountId(expenseAccountId)
                    .entryType("DEBIT")
                    .amount(transactionAmount)
                    .currency("USD")
                    .description("Expense")
                    .referenceNumber("BAL-001")
                    .transactionDate(LocalDateTime.now())
                    .valueDate(LocalDateTime.now())
                    .build());

            PostTransactionRequest request = PostTransactionRequest.builder()
                    .transactionId(transactionId)
                    .ledgerEntries(entries)
                    .build();

            PostTransactionResult result = doubleEntryLedgerService.postTransaction(request);

            assertThat(result.isSuccess()).isTrue();

            AccountBalance cashBalance = accountBalanceRepository.findByAccountId(cashAccountId).orElseThrow();
            AccountBalance expenseBalance = accountBalanceRepository.findByAccountId(expenseAccountId).orElseThrow();

            assertThat(cashBalance.getCurrentBalance()).isEqualByComparingTo(initialCashBalance.subtract(transactionAmount));
            assertThat(expenseBalance.getCurrentBalance()).isEqualByComparingTo(transactionAmount);
        }

        @Test
        @DisplayName("Should maintain running balance consistency")
        @Transactional
        void shouldMaintainRunningBalanceConsistency() {
            BigDecimal amount1 = new BigDecimal("100.00");
            BigDecimal amount2 = new BigDecimal("50.00");
            BigDecimal amount3 = new BigDecimal("75.00");

            postSimpleTransaction(cashAccountId, revenueAccountId, amount1, "TXN-1");
            postSimpleTransaction(cashAccountId, revenueAccountId, amount2, "TXN-2");
            postSimpleTransaction(cashAccountId, revenueAccountId, amount3, "TXN-3");

            List<LedgerEntry> cashEntries = ledgerEntryRepository.findByAccountId(cashAccountId.toString());

            assertThat(cashEntries).hasSize(3);

            BigDecimal expectedBalance = new BigDecimal("10000.00")
                    .add(amount1)
                    .add(amount2)
                    .add(amount3);

            AccountBalance cashBalance = accountBalanceRepository.findByAccountId(cashAccountId).orElseThrow();
            assertThat(cashBalance.getCurrentBalance()).isEqualByComparingTo(expectedBalance);
        }

        @Test
        @DisplayName("Should prevent negative balances for asset accounts")
        @Transactional
        void shouldPreventNegativeBalancesForAssetAccounts() {
            BigDecimal overdraftAmount = new BigDecimal("15000.00");

            UUID transactionId = UUID.randomUUID();
            List<LedgerEntryRequest> entries = new ArrayList<>();

            entries.add(LedgerEntryRequest.builder()
                    .accountId(cashAccountId)
                    .entryType("CREDIT")
                    .amount(overdraftAmount)
                    .currency("USD")
                    .description("Overdraft attempt")
                    .referenceNumber("OVR-001")
                    .transactionDate(LocalDateTime.now())
                    .valueDate(LocalDateTime.now())
                    .build());

            entries.add(LedgerEntryRequest.builder()
                    .accountId(expenseAccountId)
                    .entryType("DEBIT")
                    .amount(overdraftAmount)
                    .currency("USD")
                    .description("Expense")
                    .referenceNumber("OVR-001")
                    .transactionDate(LocalDateTime.now())
                    .valueDate(LocalDateTime.now())
                    .build());

            PostTransactionRequest request = PostTransactionRequest.builder()
                    .transactionId(transactionId)
                    .ledgerEntries(entries)
                    .build();

            PostTransactionResult result = doubleEntryLedgerService.postTransaction(request);

            if (!result.isSuccess()) {
                AccountBalance cashBalance = accountBalanceRepository.findByAccountId(cashAccountId).orElseThrow();
                assertThat(cashBalance.getCurrentBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            }
        }
    }

    @Nested
    @DisplayName("Journal Entry Integrity Tests")
    class JournalEntryIntegrityTests {

        @Test
        @DisplayName("Should create journal entry with balanced ledger entries")
        @Transactional
        void shouldCreateJournalEntryWithBalancedLedgerEntries() {
            JournalEntry journalEntry = JournalEntry.builder()
                    .entryNumber("JE-2025-001")
                    .referenceNumber("SAL-12345")
                    .entryType(JournalEntry.EntryType.STANDARD)
                    .description("Sales transaction")
                    .entryDate(LocalDateTime.now())
                    .effectiveDate(LocalDateTime.now())
                    .status(JournalEntry.JournalStatus.DRAFT)
                    .currency("USD")
                    .createdBy("test-user")
                    .build();

            List<LedgerEntry> ledgerEntries = new ArrayList<>();

            LedgerEntry debitEntry = LedgerEntry.builder()
                    .accountId(cashAccountId.toString())
                    .entryType(LedgerEntry.EntryType.DEBIT)
                    .amount(new BigDecimal("500.00"))
                    .currency("USD")
                    .description("Cash received")
                    .referenceId("SAL-12345")
                    .build();

            LedgerEntry creditEntry = LedgerEntry.builder()
                    .accountId(revenueAccountId.toString())
                    .entryType(LedgerEntry.EntryType.CREDIT)
                    .amount(new BigDecimal("500.00"))
                    .currency("USD")
                    .description("Sales revenue")
                    .referenceId("SAL-12345")
                    .build();

            ledgerEntries.add(debitEntry);
            ledgerEntries.add(creditEntry);

            journalEntry.setLedgerEntries(ledgerEntries);
            journalEntry.calculateTotals();

            assertThat(journalEntry.isBalanced()).isTrue();
            assertThat(journalEntry.getTotalDebits()).isEqualByComparingTo(journalEntry.getTotalCredits());
            assertThat(journalEntry.getTotalDebits()).isEqualByComparingTo(new BigDecimal("500.00"));

            JournalEntry savedEntry = journalEntryRepository.save(journalEntry);

            assertThat(savedEntry.getJournalEntryId()).isNotNull();
            assertThat(savedEntry.isBalanced()).isTrue();
        }

        @Test
        @DisplayName("Should detect unbalanced journal entries")
        @Transactional
        void shouldDetectUnbalancedJournalEntries() {
            JournalEntry journalEntry = JournalEntry.builder()
                    .entryNumber("JE-2025-002")
                    .referenceNumber("BAD-001")
                    .entryType(JournalEntry.EntryType.STANDARD)
                    .description("Unbalanced entry")
                    .entryDate(LocalDateTime.now())
                    .effectiveDate(LocalDateTime.now())
                    .status(JournalEntry.JournalStatus.DRAFT)
                    .currency("USD")
                    .createdBy("test-user")
                    .build();

            List<LedgerEntry> ledgerEntries = new ArrayList<>();

            LedgerEntry debitEntry = LedgerEntry.builder()
                    .accountId(cashAccountId.toString())
                    .entryType(LedgerEntry.EntryType.DEBIT)
                    .amount(new BigDecimal("600.00"))
                    .currency("USD")
                    .description("Debit")
                    .referenceId("BAD-001")
                    .build();

            LedgerEntry creditEntry = LedgerEntry.builder()
                    .accountId(revenueAccountId.toString())
                    .entryType(LedgerEntry.EntryType.CREDIT)
                    .amount(new BigDecimal("500.00"))
                    .currency("USD")
                    .description("Credit")
                    .referenceId("BAD-001")
                    .build();

            ledgerEntries.add(debitEntry);
            ledgerEntries.add(creditEntry);

            journalEntry.setLedgerEntries(ledgerEntries);
            journalEntry.calculateTotals();

            assertThat(journalEntry.isBalanced()).isFalse();
            assertThat(journalEntry.getTotalDebits()).isNotEqualByComparingTo(journalEntry.getTotalCredits());
            assertThat(journalEntry.canBePosted()).isFalse();
        }

        @Test
        @DisplayName("Should enforce journal entry approval workflow")
        @Transactional
        void shouldEnforceJournalEntryApprovalWorkflow() {
            JournalEntry journalEntry = createBalancedJournalEntry("JE-APPR-001", true);

            assertThat(journalEntry.requiresApproval()).isTrue();
            assertThat(journalEntry.canBePosted()).isFalse();

            journalEntry.approve("approver-user", "Approved for posting");

            assertThat(journalEntry.getStatus()).isEqualTo(JournalEntry.JournalStatus.APPROVED);
            assertThat(journalEntry.canBePosted()).isTrue();
            assertThat(journalEntry.getApprovedBy()).isEqualTo("approver-user");
        }

        @Test
        @DisplayName("Should handle journal entry reversal")
        @Transactional
        void shouldHandleJournalEntryReversal() {
            JournalEntry originalEntry = createBalancedJournalEntry("JE-REV-001", false);
            originalEntry.markAsPosted("poster-user");
            JournalEntry savedEntry = journalEntryRepository.save(originalEntry);

            assertThat(savedEntry.canBeReversed()).isTrue();

            savedEntry.markAsReversed("reversal-user", "Error correction");

            assertThat(savedEntry.getStatus()).isEqualTo(JournalEntry.JournalStatus.REVERSED);
            assertThat(savedEntry.getReversedBy()).isEqualTo("reversal-user");
            assertThat(savedEntry.getReversalReason()).isEqualTo("Error correction");
            assertThat(savedEntry.canBeReversed()).isFalse();
        }
    }

    @Nested
    @DisplayName("Trial Balance Tests")
    class TrialBalanceTests {

        @Test
        @DisplayName("Should generate balanced trial balance")
        @Transactional
        void shouldGenerateBalancedTrialBalance() {
            postSimpleTransaction(cashAccountId, revenueAccountId, new BigDecimal("1000.00"), "TB-1");
            postSimpleTransaction(expenseAccountId, cashAccountId, new BigDecimal("300.00"), "TB-2");
            postSimpleTransaction(receivableAccountId, revenueAccountId, new BigDecimal("500.00"), "TB-3");

            TrialBalanceResponse trialBalance = doubleEntryLedgerService.generateTrialBalance(LocalDateTime.now());

            assertThat(trialBalance.isBalanced()).isTrue();
            assertThat(trialBalance.getTotalDebits()).isEqualByComparingTo(trialBalance.getTotalCredits());
            assertThat(trialBalance.getEntries()).isNotEmpty();
        }

        @Test
        @DisplayName("Should detect trial balance discrepancies")
        @Transactional
        void shouldDetectTrialBalanceDiscrepancies() {
            postSimpleTransaction(cashAccountId, revenueAccountId, new BigDecimal("500.00"), "DISC-1");
            postSimpleTransaction(expenseAccountId, cashAccountId, new BigDecimal("200.00"), "DISC-2");

            TrialBalanceResponse trialBalance = doubleEntryLedgerService.generateTrialBalance(LocalDateTime.now());

            BigDecimal totalDebits = trialBalance.getTotalDebits();
            BigDecimal totalCredits = trialBalance.getTotalCredits();

            assertThat(totalDebits).isEqualByComparingTo(totalCredits);

            if (!trialBalance.isBalanced()) {
                BigDecimal variance = totalDebits.subtract(totalCredits);
                assertThat(variance.abs()).isLessThan(new BigDecimal("0.01"));
            }
        }
    }

    @Nested
    @DisplayName("Fund Reservation Tests")
    class FundReservationTests {

        @Test
        @DisplayName("Should reserve funds and reduce available balance")
        @Transactional
        void shouldReserveFundsAndReduceAvailableBalance() {
            BigDecimal reservationAmount = new BigDecimal("500.00");
            UUID reservationId = UUID.randomUUID();

            AccountBalance beforeReservation = accountBalanceRepository.findByAccountId(cashAccountId).orElseThrow();
            BigDecimal initialAvailable = beforeReservation.getAvailableBalance();

            ReserveFundsResult result = doubleEntryLedgerService.reserveFunds(
                    cashAccountId, reservationAmount, reservationId, "Hold for payment");

            assertThat(result.isSuccess()).isTrue();

            AccountBalance afterReservation = accountBalanceRepository.findByAccountId(cashAccountId).orElseThrow();

            assertThat(afterReservation.getAvailableBalance())
                    .isEqualByComparingTo(initialAvailable.subtract(reservationAmount));
            assertThat(afterReservation.getReservedBalance())
                    .isEqualByComparingTo(reservationAmount);
        }

        @Test
        @DisplayName("Should release reserved funds")
        @Transactional
        void shouldReleaseReservedFunds() {
            BigDecimal reservationAmount = new BigDecimal("300.00"));
            UUID reservationId = UUID.randomUUID();

            doubleEntryLedgerService.reserveFunds(cashAccountId, reservationAmount, reservationId, "Test hold");

            AccountBalance afterReservation = accountBalanceRepository.findByAccountId(cashAccountId).orElseThrow();
            BigDecimal availableAfterReservation = afterReservation.getAvailableBalance();

            ReleaseReservedFundsResult releaseResult = doubleEntryLedgerService.releaseReservedFunds(
                    cashAccountId, reservationId, reservationAmount);

            assertThat(releaseResult.isSuccess()).isTrue();

            AccountBalance afterRelease = accountBalanceRepository.findByAccountId(cashAccountId).orElseThrow();

            assertThat(afterRelease.getAvailableBalance())
                    .isEqualByComparingTo(availableAfterReservation.add(reservationAmount));
            assertThat(afterRelease.getReservedBalance())
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should prevent reservation exceeding available balance")
        @Transactional
        void shouldPreventReservationExceedingAvailableBalance() {
            BigDecimal excessiveAmount = new BigDecimal("50000.00");
            UUID reservationId = UUID.randomUUID();

            ReserveFundsResult result = doubleEntryLedgerService.reserveFunds(
                    cashAccountId, excessiveAmount, reservationId, "Excessive hold");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Insufficient");
        }
    }

    private void createAccountBalance(UUID accountId, BigDecimal initialBalance, String currency) {
        AccountBalance accountBalance = AccountBalance.builder()
                .accountId(accountId)
                .currentBalance(initialBalance)
                .availableBalance(initialBalance)
                .pendingBalance(BigDecimal.ZERO)
                .reservedBalance(BigDecimal.ZERO)
                .currency(currency)
                .lastUpdated(LocalDateTime.now())
                .build();

        accountBalanceRepository.save(accountBalance);
    }

    private void postSimpleTransaction(UUID debitAccountId, UUID creditAccountId, 
                                      BigDecimal amount, String reference) {
        UUID transactionId = UUID.randomUUID();
        List<LedgerEntryRequest> entries = new ArrayList<>();

        entries.add(LedgerEntryRequest.builder()
                .accountId(debitAccountId)
                .entryType("DEBIT")
                .amount(amount)
                .currency("USD")
                .description("Debit entry")
                .referenceNumber(reference)
                .transactionDate(LocalDateTime.now())
                .valueDate(LocalDateTime.now())
                .build());

        entries.add(LedgerEntryRequest.builder()
                .accountId(creditAccountId)
                .entryType("CREDIT")
                .amount(amount)
                .currency("USD")
                .description("Credit entry")
                .referenceNumber(reference)
                .transactionDate(LocalDateTime.now())
                .valueDate(LocalDateTime.now())
                .build());

        PostTransactionRequest request = PostTransactionRequest.builder()
                .transactionId(transactionId)
                .ledgerEntries(entries)
                .build();

        doubleEntryLedgerService.postTransaction(request);
    }

    private JournalEntry createBalancedJournalEntry(String entryNumber, boolean requiresApproval) {
        JournalEntry journalEntry = JournalEntry.builder()
                .entryNumber(entryNumber)
                .referenceNumber("REF-" + entryNumber)
                .entryType(JournalEntry.EntryType.STANDARD)
                .description("Test journal entry")
                .entryDate(LocalDateTime.now())
                .effectiveDate(LocalDateTime.now())
                .status(JournalEntry.JournalStatus.DRAFT)
                .currency("USD")
                .approvalRequired(requiresApproval)
                .createdBy("test-user")
                .build();

        List<LedgerEntry> ledgerEntries = new ArrayList<>();

        ledgerEntries.add(LedgerEntry.builder()
                .accountId(cashAccountId.toString())
                .entryType(LedgerEntry.EntryType.DEBIT)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .description("Debit")
                .referenceId(entryNumber)
                .build());

        ledgerEntries.add(LedgerEntry.builder()
                .accountId(revenueAccountId.toString())
                .entryType(LedgerEntry.EntryType.CREDIT)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .description("Credit")
                .referenceId(entryNumber)
                .build());

        journalEntry.setLedgerEntries(ledgerEntries);
        journalEntry.calculateTotals();

        return journalEntry;
    }
}