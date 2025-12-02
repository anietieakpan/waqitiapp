package com.waqiti.wallet.service;

import com.waqiti.common.audit.AuditService;
import com.waqiti.wallet.domain.*;
import com.waqiti.wallet.dto.*;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.repository.TransactionRepository;
import com.waqiti.ledger.client.LedgerServiceClient;
import com.waqiti.payment.client.PaymentServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CRITICAL: Integration tests for Wallet Service with real database
 * COVERAGE: Tests wallet operations with actual persistence and concurrency
 * IMPACT: Ensures wallet balance integrity and prevents double-spending
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@Testcontainers
@ActiveProfiles("integration-test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///waqiti_test",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("Wallet Service Integration Tests")
class WalletServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("waqiti_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @MockBean
    private LedgerServiceClient ledgerServiceClient;

    @MockBean
    private PaymentServiceClient paymentServiceClient;

    @MockBean
    private AuditService auditService;

    private Wallet testWallet;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        
        testWallet = Wallet.builder()
                .userId(testUserId)
                .currency("USD")
                .balance(new BigDecimal("1000.00"))
                .availableBalance(new BigDecimal("1000.00"))
                .status(WalletStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        
        // Save wallet to database
        testWallet = walletRepository.save(testWallet);

        // Mock ledger service to return success
        when(ledgerServiceClient.recordTransaction(any())).thenReturn(LedgerResult.successful());
    }

    @Nested
    @DisplayName("Wallet Creation and Management Tests")
    class WalletCreationTests {

        @Test
        @DisplayName("Should create new wallet successfully")
        @Transactional
        void shouldCreateNewWalletSuccessfully() {
            // GIVEN
            UUID newUserId = UUID.randomUUID();
            CreateWalletRequestDto request = CreateWalletRequestDto.builder()
                    .userId(newUserId)
                    .currency("EUR")
                    .initialBalance(new BigDecimal("500.00"))
                    .build();

            // WHEN
            WalletResponseDto response = walletService.createWallet(request, "admin-user", "req-123");

            // THEN
            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(newUserId);
            assertThat(response.getCurrency()).isEqualTo("EUR");
            assertThat(response.getBalance()).isEqualTo(new BigDecimal("500.00"));
            assertThat(response.getStatus()).isEqualTo(WalletStatus.ACTIVE);

            // Verify wallet is persisted in database
            Wallet savedWallet = walletRepository.findByUserIdAndCurrency(newUserId, "EUR")
                    .orElseThrow(() -> new AssertionError("Wallet should be saved"));
            assertThat(savedWallet.getBalance()).isEqualTo(new BigDecimal("500.00"));

            verify(auditService).logFinancialOperation(eq("WALLET_CREATED"), eq("admin-user"),
                    any(), any(), eq(new BigDecimal("500.00")), eq("EUR"), eq("req-123"), any());
        }

        @Test
        @DisplayName("Should prevent duplicate wallet creation")
        @Transactional
        void shouldPreventDuplicateWalletCreation() {
            // GIVEN - wallet already exists for this user and currency
            CreateWalletRequestDto request = CreateWalletRequestDto.builder()
                    .userId(testUserId)
                    .currency("USD") // Same as existing wallet
                    .initialBalance(new BigDecimal("100.00"))
                    .build();

            // WHEN & THEN
            assertThatThrownBy(() -> walletService.createWallet(request, "admin-user", "req-123"))
                    .isInstanceOf(DuplicateWalletException.class)
                    .hasMessage("Wallet already exists for user and currency");
        }
    }

    @Nested
    @DisplayName("Balance Operations Tests")
    class BalanceOperationsTests {

        @Test
        @DisplayName("Should credit wallet successfully")
        @Transactional
        void shouldCreditWalletSuccessfully() {
            // GIVEN
            CreditWalletRequestDto request = CreditWalletRequestDto.builder()
                    .amount(new BigDecimal("250.00"))
                    .currency("USD")
                    .description("Test credit")
                    .sourceAccount("bank-account-123")
                    .build();

            BigDecimal originalBalance = testWallet.getBalance();

            // WHEN
            TransactionResponseDto response = walletService.creditWallet(
                    testWallet.getId(), request, testUserId.toString(), "req-456", "bank-account-123", "device-789");

            // THEN
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getNewBalance()).isEqualTo(originalBalance.add(request.getAmount()));

            // Verify wallet balance is updated in database
            Wallet updatedWallet = walletRepository.findById(testWallet.getId())
                    .orElseThrow(() -> new AssertionError("Wallet should exist"));
            assertThat(updatedWallet.getBalance()).isEqualTo(new BigDecimal("1250.00"));
            assertThat(updatedWallet.getAvailableBalance()).isEqualTo(new BigDecimal("1250.00"));

            // Verify transaction is recorded
            List<Transaction> transactions = transactionRepository.findByWalletIdOrderByCreatedAtDesc(testWallet.getId());
            assertThat(transactions).hasSize(1);
            assertThat(transactions.get(0).getAmount()).isEqualTo(new BigDecimal("250.00"));
            assertThat(transactions.get(0).getType()).isEqualTo(TransactionType.CREDIT);
            assertThat(transactions.get(0).getStatus()).isEqualTo(TransactionStatus.COMPLETED);

            verify(ledgerServiceClient).recordTransaction(argThat(ledgerEntry -> 
                    ledgerEntry.getAmount().equals(new BigDecimal("250.00")) &&
                    ledgerEntry.getTransactionType() == LedgerTransactionType.CREDIT));
        }

        @Test
        @DisplayName("Should debit wallet successfully")
        @Transactional
        void shouldDebitWalletSuccessfully() {
            // GIVEN
            DebitWalletRequestDto request = DebitWalletRequestDto.builder()
                    .amount(new BigDecimal("300.00"))
                    .currency("USD")
                    .description("Test debit")
                    .destinationAccount("external-account-456")
                    .build();

            BigDecimal originalBalance = testWallet.getBalance();

            // WHEN
            TransactionResponseDto response = walletService.debitWallet(
                    testWallet.getId(), request, testUserId.toString(), "req-789", "external-account-456", "device-123");

            // THEN
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getNewBalance()).isEqualTo(originalBalance.subtract(request.getAmount()));

            // Verify wallet balance is updated in database
            Wallet updatedWallet = walletRepository.findById(testWallet.getId())
                    .orElseThrow(() -> new AssertionError("Wallet should exist"));
            assertThat(updatedWallet.getBalance()).isEqualTo(new BigDecimal("700.00"));
            assertThat(updatedWallet.getAvailableBalance()).isEqualTo(new BigDecimal("700.00"));

            // Verify transaction is recorded
            List<Transaction> transactions = transactionRepository.findByWalletIdOrderByCreatedAtDesc(testWallet.getId());
            assertThat(transactions).hasSize(1);
            assertThat(transactions.get(0).getAmount()).isEqualTo(new BigDecimal("300.00"));
            assertThat(transactions.get(0).getType()).isEqualTo(TransactionType.DEBIT);
            assertThat(transactions.get(0).getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        }

        @Test
        @DisplayName("Should prevent overdraft when debiting wallet")
        @Transactional
        void shouldPreventOverdraftWhenDebitingWallet() {
            // GIVEN
            DebitWalletRequestDto request = DebitWalletRequestDto.builder()
                    .amount(new BigDecimal("1500.00")) // More than available balance
                    .currency("USD")
                    .description("Overdraft attempt")
                    .destinationAccount("external-account")
                    .build();

            BigDecimal originalBalance = testWallet.getBalance();

            // WHEN & THEN
            assertThatThrownBy(() -> walletService.debitWallet(
                    testWallet.getId(), request, testUserId.toString(), "req-999", "external-account", null))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessage("Insufficient funds for debit operation");

            // Verify wallet balance remains unchanged
            Wallet unchangedWallet = walletRepository.findById(testWallet.getId())
                    .orElseThrow(() -> new AssertionError("Wallet should exist"));
            assertThat(unchangedWallet.getBalance()).isEqualTo(originalBalance);

            // Verify no transaction was recorded
            List<Transaction> transactions = transactionRepository.findByWalletIdOrderByCreatedAtDesc(testWallet.getId());
            assertThat(transactions).isEmpty();

            verify(auditService).logFinancialOperation(eq("WALLET_DEBIT_INSUFFICIENT_FUNDS"), 
                    eq(testUserId.toString()), any(), any(), any(), any(), eq("req-999"), any());
        }
    }

    @Nested
    @DisplayName("Concurrency and Race Condition Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Should handle concurrent balance updates without double-spending")
        void shouldHandleConcurrentBalanceUpdatesWithoutDoubleSpending() throws Exception {
            // GIVEN
            int numberOfConcurrentDebits = 10;
            BigDecimal debitAmount = new BigDecimal("50.00"); // Total: $500
            BigDecimal originalBalance = testWallet.getBalance(); // $1000

            ExecutorService executorService = Executors.newFixedThreadPool(numberOfConcurrentDebits);

            // WHEN - Execute concurrent debit operations
            CompletableFuture<TransactionResponseDto>[] futures = new CompletableFuture[numberOfConcurrentDebits];
            
            for (int i = 0; i < numberOfConcurrentDebits; i++) {
                final int operationId = i;
                futures[i] = CompletableFuture.supplyAsync(() -> {
                    try {
                        DebitWalletRequestDto request = DebitWalletRequestDto.builder()
                                .amount(debitAmount)
                                .currency("USD")
                                .description("Concurrent debit " + operationId)
                                .destinationAccount("account-" + operationId)
                                .build();

                        return walletService.debitWallet(testWallet.getId(), request, 
                                testUserId.toString(), "req-" + operationId, "account-" + operationId, null);
                    } catch (Exception e) {
                        // Some operations might fail due to insufficient funds
                        return TransactionResponseDto.failure(e.getMessage());
                    }
                }, executorService);
            }

            // Wait for all operations to complete
            CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);

            // THEN - Verify final state
            Wallet finalWallet = walletRepository.findById(testWallet.getId())
                    .orElseThrow(() -> new AssertionError("Wallet should exist"));

            // Count successful transactions
            List<Transaction> completedTransactions = transactionRepository
                    .findByWalletIdAndStatusOrderByCreatedAtDesc(testWallet.getId(), TransactionStatus.COMPLETED);
            
            BigDecimal totalDebited = completedTransactions.stream()
                    .filter(tx -> tx.getType() == TransactionType.DEBIT)
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Verify balance consistency
            assertThat(finalWallet.getBalance()).isEqualTo(originalBalance.subtract(totalDebited));
            
            // Verify no double-spending occurred
            assertThat(finalWallet.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            
            // Verify total debited doesn't exceed original balance
            assertThat(totalDebited).isLessThanOrEqualTo(originalBalance);

            executorService.shutdown();
        }

        @Test
        @DisplayName("Should maintain balance consistency during mixed concurrent operations")
        void shouldMaintainBalanceConsistencyDuringMixedConcurrentOperations() throws Exception {
            // GIVEN
            int numberOfOperations = 20;
            ExecutorService executorService = Executors.newFixedThreadPool(numberOfOperations);
            BigDecimal originalBalance = testWallet.getBalance();

            // WHEN - Execute mixed concurrent credit and debit operations
            CompletableFuture<Void>[] futures = new CompletableFuture[numberOfOperations];
            
            for (int i = 0; i < numberOfOperations; i++) {
                final int operationId = i;
                final boolean isCredit = i % 2 == 0; // Alternate between credit and debit
                
                futures[i] = CompletableFuture.runAsync(() -> {
                    try {
                        if (isCredit) {
                            CreditWalletRequestDto request = CreditWalletRequestDto.builder()
                                    .amount(new BigDecimal("25.00"))
                                    .currency("USD")
                                    .description("Concurrent credit " + operationId)
                                    .sourceAccount("source-" + operationId)
                                    .build();

                            walletService.creditWallet(testWallet.getId(), request, 
                                    testUserId.toString(), "credit-req-" + operationId, 
                                    "source-" + operationId, null);
                        } else {
                            DebitWalletRequestDto request = DebitWalletRequestDto.builder()
                                    .amount(new BigDecimal("20.00"))
                                    .currency("USD")
                                    .description("Concurrent debit " + operationId)
                                    .destinationAccount("dest-" + operationId)
                                    .build();

                            walletService.debitWallet(testWallet.getId(), request, 
                                    testUserId.toString(), "debit-req-" + operationId, 
                                    "dest-" + operationId, null);
                        }
                    } catch (Exception e) {
                        // Some debit operations might fail due to insufficient funds
                        System.err.println("Operation " + operationId + " failed: " + e.getMessage());
                    }
                }, executorService);
            }

            // Wait for all operations to complete
            CompletableFuture.allOf(futures).get(45, TimeUnit.SECONDS);

            // THEN - Verify balance consistency
            Wallet finalWallet = walletRepository.findById(testWallet.getId())
                    .orElseThrow(() -> new AssertionError("Wallet should exist"));

            List<Transaction> allTransactions = transactionRepository
                    .findByWalletIdAndStatusOrderByCreatedAtDesc(testWallet.getId(), TransactionStatus.COMPLETED);

            BigDecimal totalCredits = allTransactions.stream()
                    .filter(tx -> tx.getType() == TransactionType.CREDIT)
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalDebits = allTransactions.stream()
                    .filter(tx -> tx.getType() == TransactionType.DEBIT)
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal expectedBalance = originalBalance.add(totalCredits).subtract(totalDebits);

            // Verify final balance matches expected calculation
            assertThat(finalWallet.getBalance()).isEqualTo(expectedBalance);
            
            // Verify balance is never negative
            assertThat(finalWallet.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);

            executorService.shutdown();
        }
    }

    @Nested
    @DisplayName("Wallet Security and Validation Tests")
    class SecurityAndValidationTests {

        @Test
        @DisplayName("Should validate user ownership for wallet operations")
        @Transactional
        void shouldValidateUserOwnershipForWalletOperations() {
            // GIVEN
            UUID unauthorizedUserId = UUID.randomUUID();
            CreditWalletRequestDto request = CreditWalletRequestDto.builder()
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .description("Unauthorized credit attempt")
                    .sourceAccount("bank-account")
                    .build();

            // WHEN & THEN
            assertThatThrownBy(() -> walletService.creditWallet(testWallet.getId(), request, 
                    unauthorizedUserId.toString(), "req-unauthorized", "bank-account", null))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("User not authorized to access this wallet");

            verify(auditService).logSecurityEvent(eq("UNAUTHORIZED_WALLET_ACCESS"), 
                    eq(unauthorizedUserId.toString()), any(), any());
        }

        @Test
        @DisplayName("Should prevent operations on frozen wallet")
        @Transactional
        void shouldPreventOperationsOnFrozenWallet() {
            // GIVEN - Freeze the wallet
            testWallet.setStatus(WalletStatus.FROZEN);
            walletRepository.save(testWallet);

            CreditWalletRequestDto request = CreditWalletRequestDto.builder()
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .description("Credit to frozen wallet")
                    .sourceAccount("bank-account")
                    .build();

            // WHEN & THEN
            assertThatThrownBy(() -> walletService.creditWallet(testWallet.getId(), request, 
                    testUserId.toString(), "req-frozen", "bank-account", null))
                    .isInstanceOf(WalletFrozenException.class)
                    .hasMessage("Wallet is frozen and cannot be used for transactions");
        }

        @Test
        @DisplayName("Should validate transaction amounts")
        @Transactional
        void shouldValidateTransactionAmounts() {
            // GIVEN
            CreditWalletRequestDto negativeAmountRequest = CreditWalletRequestDto.builder()
                    .amount(new BigDecimal("-100.00")) // Negative amount
                    .currency("USD")
                    .description("Invalid credit")
                    .sourceAccount("bank-account")
                    .build();

            // WHEN & THEN
            assertThatThrownBy(() -> walletService.creditWallet(testWallet.getId(), 
                    negativeAmountRequest, testUserId.toString(), "req-invalid", "bank-account", null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Transaction amount must be positive");
        }

        @Test
        @DisplayName("Should validate currency matching")
        @Transactional
        void shouldValidateCurrencyMatching() {
            // GIVEN
            CreditWalletRequestDto wrongCurrencyRequest = CreditWalletRequestDto.builder()
                    .amount(new BigDecimal("100.00"))
                    .currency("EUR") // Wallet is USD
                    .description("Wrong currency credit")
                    .sourceAccount("bank-account")
                    .build();

            // WHEN & THEN
            assertThatThrownBy(() -> walletService.creditWallet(testWallet.getId(), 
                    wrongCurrencyRequest, testUserId.toString(), "req-currency", "bank-account", null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Transaction currency does not match wallet currency");
        }
    }

    @Nested
    @DisplayName("Ledger Integration Tests")
    class LedgerIntegrationTests {

        @Test
        @DisplayName("Should handle ledger service failure gracefully")
        @Transactional
        void shouldHandleLedgerServiceFailureGracefully() {
            // GIVEN
            when(ledgerServiceClient.recordTransaction(any()))
                    .thenReturn(LedgerResult.failure("Ledger service unavailable"));

            CreditWalletRequestDto request = CreditWalletRequestDto.builder()
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .description("Credit with ledger failure")
                    .sourceAccount("bank-account")
                    .build();

            // WHEN & THEN
            assertThatThrownBy(() -> walletService.creditWallet(testWallet.getId(), request, 
                    testUserId.toString(), "req-ledger-fail", "bank-account", null))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessage("Unable to record transaction in ledger - transaction rolled back");

            // Verify wallet balance is not changed
            Wallet unchangedWallet = walletRepository.findById(testWallet.getId())
                    .orElseThrow(() -> new AssertionError("Wallet should exist"));
            assertThat(unchangedWallet.getBalance()).isEqualTo(new BigDecimal("1000.00"));

            // Verify no transaction was recorded
            List<Transaction> transactions = transactionRepository.findByWalletIdOrderByCreatedAtDesc(testWallet.getId());
            assertThat(transactions).isEmpty();
        }

        @Test
        @DisplayName("Should retry ledger service calls on timeout")
        @Transactional
        void shouldRetryLedgerServiceCallsOnTimeout() {
            // GIVEN
            when(ledgerServiceClient.recordTransaction(any()))
                    .thenThrow(new ServiceTimeoutException("Ledger timeout"))
                    .thenThrow(new ServiceTimeoutException("Ledger timeout"))
                    .thenReturn(LedgerResult.successful()); // Succeeds on third try

            CreditWalletRequestDto request = CreditWalletRequestDto.builder()
                    .amount(new BigDecimal("150.00"))
                    .currency("USD")
                    .description("Credit with ledger retry")
                    .sourceAccount("bank-account")
                    .build();

            // WHEN
            TransactionResponseDto response = walletService.creditWallet(testWallet.getId(), request, 
                    testUserId.toString(), "req-retry", "bank-account", null);

            // THEN
            assertThat(response.isSuccess()).isTrue();
            
            // Verify ledger service was called 3 times (2 failures + 1 success)
            verify(ledgerServiceClient, times(3)).recordTransaction(any());
            
            // Verify wallet balance was updated
            Wallet updatedWallet = walletRepository.findById(testWallet.getId())
                    .orElseThrow(() -> new AssertionError("Wallet should exist"));
            assertThat(updatedWallet.getBalance()).isEqualTo(new BigDecimal("1150.00"));
        }
    }

    // Helper classes and exceptions
    private static class LedgerResult {
        private boolean success;
        private String errorMessage;
        
        public static LedgerResult successful() {
            LedgerResult result = new LedgerResult();
            result.success = true;
            return result;
        }
        
        public static LedgerResult failure(String errorMessage) {
            LedgerResult result = new LedgerResult();
            result.success = false;
            result.errorMessage = errorMessage;
            return result;
        }
        
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }

    private static class DuplicateWalletException extends RuntimeException {
        public DuplicateWalletException(String message) { super(message); }
    }

    private static class InsufficientFundsException extends RuntimeException {
        public InsufficientFundsException(String message) { super(message); }
    }

    private static class WalletFrozenException extends RuntimeException {
        public WalletFrozenException(String message) { super(message); }
    }

    private static class ValidationException extends RuntimeException {
        public ValidationException(String message) { super(message); }
    }

    private static class ExternalServiceException extends RuntimeException {
        public ExternalServiceException(String message) { super(message); }
    }

    private static class ServiceTimeoutException extends RuntimeException {
        public ServiceTimeoutException(String message) { super(message); }
    }

    private static class SecurityException extends RuntimeException {
        public SecurityException(String message) { super(message); }
    }
}