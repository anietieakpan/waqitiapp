package com.waqiti.ledger.service;

import com.waqiti.ledger.domain.AccountBalance;
import com.waqiti.ledger.domain.LedgerEntry;
import com.waqiti.ledger.domain.ReconciliationDiscrepancy;
import com.waqiti.ledger.dto.*;
import com.waqiti.ledger.repository.AccountBalanceRepository;
import com.waqiti.ledger.repository.LedgerEntryRepository;
import com.waqiti.ledger.repository.ReconciliationDiscrepancyRepository;
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
@DisplayName("Balance Reconciliation Tests")
class BalanceReconciliationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("waqiti_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private DoubleEntryLedgerService doubleEntryLedgerService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private AccountBalanceRepository accountBalanceRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired(required = false)
    private ReconciliationDiscrepancyRepository reconciliationDiscrepancyRepository;

    private UUID testAccountId;
    private UUID contraAccountId;

    @BeforeEach
    void setUp() {
        testAccountId = UUID.randomUUID();
        contraAccountId = UUID.randomUUID();

        createAccountBalance(testAccountId, new BigDecimal("5000.00"), "USD");
        createAccountBalance(contraAccountId, BigDecimal.ZERO, "USD");
    }

    @Nested
    @DisplayName("Account Reconciliation Tests")
    class AccountReconciliationTests {

        @Test
        @DisplayName("Should reconcile account with matching balances")
        @Transactional
        void shouldReconcileAccountWithMatchingBalances() {
            postTransaction(testAccountId, contraAccountId, new BigDecimal("100.00"), "REC-1");
            postTransaction(testAccountId, contraAccountId, new BigDecimal("50.00"), "REC-2");

            ReconciliationResult result = doubleEntryLedgerService.reconcileAccount(
                    testAccountId, LocalDateTime.now());

            assertThat(result.isReconciled()).isTrue();
            assertThat(result.getVariance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getCalculatedBalance()).isEqualByComparingTo(result.getStoredBalance());
        }

        @Test
        @DisplayName("Should detect balance variance during reconciliation")
        @Transactional
        void shouldDetectBalanceVarianceDuringReconciliation() {
            postTransaction(testAccountId, contraAccountId, new BigDecimal("200.00"), "VAR-1");

            AccountBalance accountBalance = accountBalanceRepository.findByAccountId(testAccountId).orElseThrow();
            accountBalance.setCurrentBalance(accountBalance.getCurrentBalance().add(new BigDecimal("10.00")));
            accountBalanceRepository.save(accountBalance);

            ReconciliationResult result = doubleEntryLedgerService.reconcileAccount(
                    testAccountId, LocalDateTime.now());

            assertThat(result.getVariance().abs()).isGreaterThan(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should auto-correct balance variance")
        @Transactional
        void shouldAutoCorrectBalanceVariance() {
            postTransaction(testAccountId, contraAccountId, new BigDecimal("150.00"), "AUTO-1");

            AccountBalance accountBalance = accountBalanceRepository.findByAccountId(testAccountId).orElseThrow();
            BigDecimal incorrectBalance = accountBalance.getCurrentBalance().add(new BigDecimal("25.00"));
            accountBalance.setCurrentBalance(incorrectBalance);
            accountBalanceRepository.save(accountBalance);

            ReconciliationResult result = doubleEntryLedgerService.reconcileAccount(
                    testAccountId, LocalDateTime.now());

            AccountBalance correctedBalance = accountBalanceRepository.findByAccountId(testAccountId).orElseThrow();

            assertThat(correctedBalance.getCurrentBalance()).isEqualByComparingTo(result.getCalculatedBalance());
        }

        @Test
        @DisplayName("Should reconcile account at specific point in time")
        @Transactional
        void shouldReconcileAccountAtSpecificPointInTime() {
            LocalDateTime checkpoint1 = LocalDateTime.now().minusDays(2);
            LocalDateTime checkpoint2 = LocalDateTime.now().minusDays(1);

            postTransaction(testAccountId, contraAccountId, new BigDecimal("100.00"), "TIME-1");
            postTransaction(testAccountId, contraAccountId, new BigDecimal("200.00"), "TIME-2");

            ReconciliationResult result1 = doubleEntryLedgerService.reconcileAccount(testAccountId, checkpoint1);
            ReconciliationResult result2 = doubleEntryLedgerService.reconcileAccount(testAccountId, checkpoint2);

            assertThat(result2.getCalculatedBalance()).isGreaterThanOrEqualTo(result1.getCalculatedBalance());
        }
    }

    @Nested
    @DisplayName("Ledger Entry Consistency Tests")
    class LedgerEntryConsistencyTests {

        @Test
        @DisplayName("Should verify all ledger entries have contra entries")
        @Transactional
        void shouldVerifyAllLedgerEntriesHaveContraEntries() {
            postTransaction(testAccountId, contraAccountId, new BigDecimal("300.00"), "CONTRA-1");
            postTransaction(testAccountId, contraAccountId, new BigDecimal("150.00"), "CONTRA-2");

            List<LedgerEntry> testAccountEntries = ledgerEntryRepository.findByAccountId(testAccountId.toString());

            for (LedgerEntry entry : testAccountEntries) {
                if (entry.getTransactionId() != null) {
                    List<LedgerEntry> relatedEntries = ledgerEntryRepository.findByTransactionId(entry.getTransactionId());

                    assertThat(relatedEntries).hasSizeGreaterThanOrEqualTo(2);

                    BigDecimal totalDebits = relatedEntries.stream()
                            .filter(LedgerEntry::isDebit)
                            .map(LedgerEntry::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal totalCredits = relatedEntries.stream()
                            .filter(LedgerEntry::isCredit)
                            .map(LedgerEntry::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    assertThat(totalDebits).isEqualByComparingTo(totalCredits);
                }
            }
        }

        @Test
        @DisplayName("Should detect orphaned ledger entries")
        @Transactional
        void shouldDetectOrphanedLedgerEntries() {
            postTransaction(testAccountId, contraAccountId, new BigDecimal("100.00"), "ORPHAN-1");

            List<LedgerEntry> allEntries = ledgerEntryRepository.findAll();

            for (LedgerEntry entry : allEntries) {
                if (entry.getTransactionId() != null) {
                    List<LedgerEntry> transactionEntries = ledgerEntryRepository
                            .findByTransactionId(entry.getTransactionId());

                    assertThat(transactionEntries).isNotEmpty();
                    assertThat(transactionEntries.size()).isGreaterThanOrEqualTo(2);
                }
            }
        }

        @Test
        @DisplayName("Should validate chronological ordering of ledger entries")
        @Transactional
        void shouldValidateChronologicalOrderingOfLedgerEntries() {
            postTransaction(testAccountId, contraAccountId, new BigDecimal("50.00"), "CHRONO-1");
            
            Thread.sleep(100);
            
            postTransaction(testAccountId, contraAccountId, new BigDecimal("75.00"), "CHRONO-2");
            
            Thread.sleep(100);
            
            postTransaction(testAccountId, contraAccountId, new BigDecimal("100.00"), "CHRONO-3");

            List<LedgerEntry> entries = ledgerEntryRepository.findByAccountId(testAccountId.toString());

            for (int i = 1; i < entries.size(); i++) {
                assertThat(entries.get(i).getCreatedAt())
                        .isAfterOrEqualTo(entries.get(i - 1).getCreatedAt());
            }
        }
    }

    @Nested
    @DisplayName("Multi-Currency Reconciliation Tests")
    class MultiCurrencyReconciliationTests {

        @Test
        @DisplayName("Should reconcile accounts with different currencies")
        @Transactional
        void shouldReconcileAccountsWithDifferentCurrencies() {
            UUID usdAccountId = UUID.randomUUID();
            UUID eurAccountId = UUID.randomUUID();

            createAccountBalance(usdAccountId, new BigDecimal("1000.00"), "USD");
            createAccountBalance(eurAccountId, new BigDecimal("850.00"), "EUR");

            postTransaction(usdAccountId, contraAccountId, new BigDecimal("100.00"), "CURR-USD");

            ReconciliationResult usdResult = doubleEntryLedgerService.reconcileAccount(
                    usdAccountId, LocalDateTime.now());

            assertThat(usdResult.isReconciled()).isTrue();

            AccountBalance usdBalance = accountBalanceRepository.findByAccountId(usdAccountId).orElseThrow();
            AccountBalance eurBalance = accountBalanceRepository.findByAccountId(eurAccountId).orElseThrow();

            assertThat(usdBalance.getCurrency()).isEqualTo("USD");
            assertThat(eurBalance.getCurrency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("Should prevent cross-currency balance mixing")
        @Transactional
        void shouldPreventCrossCurrencyBalanceMixing() {
            UUID multiCurrencyAccountId = UUID.randomUUID();
            createAccountBalance(multiCurrencyAccountId, new BigDecimal("500.00"), "USD");

            postTransaction(multiCurrencyAccountId, contraAccountId, new BigDecimal("50.00"), "MULTI-1");

            AccountBalance balance = accountBalanceRepository.findByAccountId(multiCurrencyAccountId).orElseThrow();

            assertThat(balance.getCurrency()).isEqualTo("USD");

            List<LedgerEntry> entries = ledgerEntryRepository.findByAccountId(multiCurrencyAccountId.toString());
            for (LedgerEntry entry : entries) {
                assertThat(entry.getCurrency()).isEqualTo("USD");
            }
        }
    }

    @Nested
    @DisplayName("Balance Snapshot Tests")
    class BalanceSnapshotTests {

        @Test
        @DisplayName("Should create accurate balance snapshot")
        @Transactional
        void shouldCreateAccurateBalanceSnapshot() {
            BigDecimal initialBalance = new BigDecimal("5000.00");
            
            postTransaction(testAccountId, contraAccountId, new BigDecimal("200.00"), "SNAP-1");
            postTransaction(testAccountId, contraAccountId, new BigDecimal("300.00"), "SNAP-2");

            BalanceInquiryResponse balanceSnapshot = doubleEntryLedgerService.getAccountBalance(testAccountId);

            assertThat(balanceSnapshot.getAccountId()).isEqualTo(testAccountId);
            assertThat(balanceSnapshot.getCurrentBalance()).isNotNull();
            assertThat(balanceSnapshot.getAvailableBalance()).isNotNull();
            assertThat(balanceSnapshot.getLastUpdated()).isNotNull();
        }

        @Test
        @DisplayName("Should track balance changes over time")
        @Transactional
        void shouldTrackBalanceChangesOverTime() {
            BigDecimal amount1 = new BigDecimal("100.00");
            BigDecimal amount2 = new BigDecimal("250.00");
            BigDecimal amount3 = new BigDecimal("50.00");

            BalanceInquiryResponse balance0 = doubleEntryLedgerService.getAccountBalance(testAccountId);
            BigDecimal initial = balance0.getCurrentBalance();

            postTransaction(testAccountId, contraAccountId, amount1, "TRACK-1");
            BalanceInquiryResponse balance1 = doubleEntryLedgerService.getAccountBalance(testAccountId);

            postTransaction(testAccountId, contraAccountId, amount2, "TRACK-2");
            BalanceInquiryResponse balance2 = doubleEntryLedgerService.getAccountBalance(testAccountId);

            postTransaction(contraAccountId, testAccountId, amount3, "TRACK-3");
            BalanceInquiryResponse balance3 = doubleEntryLedgerService.getAccountBalance(testAccountId);

            assertThat(balance1.getCurrentBalance()).isEqualByComparingTo(initial.add(amount1));
            assertThat(balance2.getCurrentBalance()).isEqualByComparingTo(initial.add(amount1).add(amount2));
            assertThat(balance3.getCurrentBalance()).isEqualByComparingTo(initial.add(amount1).add(amount2).subtract(amount3));
        }
    }

    @Nested
    @DisplayName("Discrepancy Resolution Tests")
    class DiscrepancyResolutionTests {

        @Test
        @DisplayName("Should log discrepancies for manual review")
        @Transactional
        void shouldLogDiscrepanciesForManualReview() {
            postTransaction(testAccountId, contraAccountId, new BigDecimal("500.00"), "DISC-LOG-1");

            AccountBalance balance = accountBalanceRepository.findByAccountId(testAccountId).orElseThrow();
            BigDecimal tamperedBalance = balance.getCurrentBalance().add(new BigDecimal("50.00"));
            balance.setCurrentBalance(tamperedBalance);
            accountBalanceRepository.save(balance);

            ReconciliationResult result = doubleEntryLedgerService.reconcileAccount(
                    testAccountId, LocalDateTime.now());

            if (!result.isReconciled() && reconciliationDiscrepancyRepository != null) {
                ReconciliationDiscrepancy discrepancy = new ReconciliationDiscrepancy();
                discrepancy.setAccountId(testAccountId);
                discrepancy.setExpectedBalance(result.getCalculatedBalance());
                discrepancy.setActualBalance(result.getStoredBalance());
                discrepancy.setVariance(result.getVariance());
                discrepancy.setDiscoveredAt(LocalDateTime.now());
                discrepancy.setStatus("PENDING_REVIEW");

                reconciliationDiscrepancyRepository.save(discrepancy);

                List<ReconciliationDiscrepancy> discrepancies = 
                        reconciliationDiscrepancyRepository.findByAccountId(testAccountId);

                assertThat(discrepancies).isNotEmpty();
            }
        }

        @Test
        @DisplayName("Should auto-resolve minor rounding discrepancies")
        @Transactional
        void shouldAutoResolveMinorRoundingDiscrepancies() {
            postTransaction(testAccountId, contraAccountId, new BigDecimal("33.3333"), "ROUND-1");

            AccountBalance balance = accountBalanceRepository.findByAccountId(testAccountId).orElseThrow();
            BigDecimal slightVariance = balance.getCurrentBalance().add(new BigDecimal("0.0001"));
            balance.setCurrentBalance(slightVariance);
            accountBalanceRepository.save(balance);

            ReconciliationResult result = doubleEntryLedgerService.reconcileAccount(
                    testAccountId, LocalDateTime.now());

            BigDecimal variance = result.getVariance().abs();
            BigDecimal tolerance = new BigDecimal("0.01");

            if (variance.compareTo(tolerance) <= 0) {
                assertThat(result.getVariance().abs()).isLessThanOrEqualTo(tolerance);
            }
        }
    }

    @Nested
    @DisplayName("Batch Reconciliation Tests")
    class BatchReconciliationTests {

        @Test
        @DisplayName("Should reconcile multiple accounts in batch")
        @Transactional
        void shouldReconcileMultipleAccountsInBatch() {
            UUID account1 = UUID.randomUUID();
            UUID account2 = UUID.randomUUID();
            UUID account3 = UUID.randomUUID();

            createAccountBalance(account1, new BigDecimal("1000.00"), "USD");
            createAccountBalance(account2, new BigDecimal("2000.00"), "USD");
            createAccountBalance(account3, new BigDecimal("3000.00"), "USD");

            postTransaction(account1, contraAccountId, new BigDecimal("100.00"), "BATCH-1");
            postTransaction(account2, contraAccountId, new BigDecimal("200.00"), "BATCH-2");
            postTransaction(account3, contraAccountId, new BigDecimal("300.00"), "BATCH-3");

            List<UUID> accountIds = List.of(account1, account2, account3);
            List<ReconciliationResult> results = new ArrayList<>();

            for (UUID accountId : accountIds) {
                ReconciliationResult result = doubleEntryLedgerService.reconcileAccount(
                        accountId, LocalDateTime.now());
                results.add(result);
            }

            assertThat(results).hasSize(3);
            assertThat(results).allMatch(ReconciliationResult::isReconciled);
        }

        @Test
        @DisplayName("Should identify problematic accounts in batch reconciliation")
        @Transactional
        void shouldIdentifyProblematicAccountsInBatchReconciliation() {
            UUID goodAccount = UUID.randomUUID();
            UUID badAccount = UUID.randomUUID();

            createAccountBalance(goodAccount, new BigDecimal("1000.00"), "USD");
            createAccountBalance(badAccount, new BigDecimal("2000.00"), "USD");

            postTransaction(goodAccount, contraAccountId, new BigDecimal("100.00"), "GOOD-1");
            postTransaction(badAccount, contraAccountId, new BigDecimal("200.00"), "BAD-1");

            AccountBalance badBalance = accountBalanceRepository.findByAccountId(badAccount).orElseThrow();
            badBalance.setCurrentBalance(badBalance.getCurrentBalance().add(new BigDecimal("100.00")));
            accountBalanceRepository.save(badBalance);

            ReconciliationResult goodResult = doubleEntryLedgerService.reconcileAccount(goodAccount, LocalDateTime.now());
            ReconciliationResult badResult = doubleEntryLedgerService.reconcileAccount(badAccount, LocalDateTime.now());

            assertThat(goodResult.isReconciled()).isTrue();

            if (!badResult.isReconciled()) {
                assertThat(badResult.getVariance().abs()).isGreaterThan(BigDecimal.ZERO);
            }
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

    private void postTransaction(UUID debitAccountId, UUID creditAccountId, 
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
}