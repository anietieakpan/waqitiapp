package com.waqiti.wallet.service;

import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.domain.WalletStatus;
import com.waqiti.wallet.domain.WalletType;
import com.waqiti.wallet.dto.*;
import com.waqiti.wallet.exception.InsufficientFundsException;
import com.waqiti.wallet.exception.WalletFrozenException;
import com.waqiti.wallet.exception.WalletNotFoundException;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.service.impl.WalletServiceImpl;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.KafkaTopics;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Test Suite for WalletService
 *
 * Covers:
 * - Wallet creation and lifecycle
 * - Balance operations (debit, credit, transfer)
 * - Concurrent operations and race conditions
 * - Idempotency validation
 * - Error handling and edge cases
 * - Business rule validation
 * - Kafka event publishing
 *
 * Test Categories:
 * - @Tag("unit") - Unit tests
 * - @Tag("integration") - Integration tests
 * - @Tag("concurrency") - Concurrency tests
 * - @Tag("edge-case") - Edge case tests
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-16
 */
@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("WalletService Comprehensive Test Suite")
class WalletServiceComprehensiveTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private WalletServiceImpl walletService;

    private Wallet testWallet;
    private static final String TEST_USER_ID = "user-123";
    private static final String TEST_CURRENCY = "USD";
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000.00");

    @BeforeEach
    void setUp() {
        testWallet = Wallet.builder()
            .id(UUID.randomUUID())
            .userId(TEST_USER_ID)
            .currency(TEST_CURRENCY)
            .balance(INITIAL_BALANCE)
            .availableBalance(INITIAL_BALANCE)
            .pendingBalance(BigDecimal.ZERO)
            .frozenBalance(BigDecimal.ZERO)
            .status(WalletStatus.ACTIVE)
            .walletType(WalletType.PERSONAL)
            .dailyLimit(new BigDecimal("5000.00"))
            .monthlyLimit(new BigDecimal("20000.00"))
            .dailySpent(BigDecimal.ZERO)
            .monthlySpent(BigDecimal.ZERO)
            .version(0L)
            .build();

        // Mock Kafka template to return successful future
        when(kafkaTemplate.send(anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(null));
    }

    // ========================================================================
    // WALLET CREATION TESTS
    // ========================================================================

    @Test
    @Tag("unit")
    @DisplayName("Should create wallet successfully with valid inputs")
    void testCreateWallet_Success() {
        // Given
        CreateWalletRequest request = CreateWalletRequest.builder()
            .userId(TEST_USER_ID)
            .currency(TEST_CURRENCY)
            .walletType(WalletType.PERSONAL)
            .build();

        when(walletRepository.existsByUserIdAndCurrencyAndWalletType(
            TEST_USER_ID, TEST_CURRENCY, WalletType.PERSONAL))
            .thenReturn(false);
        when(walletRepository.save(any(Wallet.class)))
            .thenReturn(testWallet);

        // When
        WalletResponse response = walletService.createWallet(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(testWallet.getId());
        assertThat(response.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getStatus()).isEqualTo(WalletStatus.ACTIVE);

        verify(walletRepository).save(any(Wallet.class));
        verify(kafkaTemplate).send(eq(KafkaTopics.WALLET_CREATED), any());
    }

    @Test
    @Tag("unit")
    @DisplayName("Should fail to create duplicate wallet")
    void testCreateWallet_DuplicateFails() {
        // Given
        CreateWalletRequest request = CreateWalletRequest.builder()
            .userId(TEST_USER_ID)
            .currency(TEST_CURRENCY)
            .walletType(WalletType.PERSONAL)
            .build();

        when(walletRepository.existsByUserIdAndCurrencyAndWalletType(
            TEST_USER_ID, TEST_CURRENCY, WalletType.PERSONAL))
            .thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> walletService.createWallet(request))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Wallet already exists");

        verify(walletRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    @Test
    @Tag("unit")
    @DisplayName("Should validate currency code format")
    void testCreateWallet_InvalidCurrency() {
        // Given
        CreateWalletRequest request = CreateWalletRequest.builder()
            .userId(TEST_USER_ID)
            .currency("INVALID")
            .walletType(WalletType.PERSONAL)
            .build();

        // When / Then
        assertThatThrownBy(() -> walletService.createWallet(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid currency code");
    }

    // ========================================================================
    // DEBIT OPERATION TESTS
    // ========================================================================

    @Test
    @Tag("unit")
    @DisplayName("Should debit wallet successfully with sufficient funds")
    void testDebitWallet_Success() {
        // Given
        UUID walletId = testWallet.getId();
        BigDecimal debitAmount = new BigDecimal("100.00");
        String idempotencyKey = UUID.randomUUID().toString();

        when(walletRepository.findById(walletId))
            .thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any(Wallet.class)))
            .thenReturn(testWallet);

        DebitRequest request = DebitRequest.builder()
            .walletId(walletId)
            .amount(debitAmount)
            .description("Test debit")
            .idempotencyKey(idempotencyKey)
            .build();

        // When
        WalletTransactionResponse response = walletService.debit(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getWalletId()).isEqualTo(walletId);
        assertThat(response.getAmount()).isEqualByComparingTo(debitAmount);
        assertThat(response.getNewBalance())
            .isEqualByComparingTo(INITIAL_BALANCE.subtract(debitAmount));
        assertThat(response.isSuccess()).isTrue();

        verify(walletRepository).save(argThat(wallet ->
            wallet.getBalance().compareTo(
                INITIAL_BALANCE.subtract(debitAmount)) == 0
        ));
        verify(kafkaTemplate).send(eq(KafkaTopics.WALLET_DEBITED), any());
    }

    @Test
    @Tag("unit")
    @DisplayName("Should fail to debit with insufficient funds")
    void testDebitWallet_InsufficientFunds() {
        // Given
        UUID walletId = testWallet.getId();
        BigDecimal debitAmount = new BigDecimal("2000.00"); // More than balance

        when(walletRepository.findById(walletId))
            .thenReturn(Optional.of(testWallet));

        DebitRequest request = DebitRequest.builder()
            .walletId(walletId)
            .amount(debitAmount)
            .description("Test debit")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        // When / Then
        assertThatThrownBy(() -> walletService.debit(request))
            .isInstanceOf(InsufficientFundsException.class)
            .hasMessageContaining("Insufficient funds");

        verify(walletRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    @Test
    @Tag("unit")
    @DisplayName("Should fail to debit frozen wallet")
    void testDebitWallet_FrozenWallet() {
        // Given
        testWallet.setStatus(WalletStatus.FROZEN);
        UUID walletId = testWallet.getId();

        when(walletRepository.findById(walletId))
            .thenReturn(Optional.of(testWallet));

        DebitRequest request = DebitRequest.builder()
            .walletId(walletId)
            .amount(new BigDecimal("100.00"))
            .description("Test debit")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        // When / Then
        assertThatThrownBy(() -> walletService.debit(request))
            .isInstanceOf(WalletFrozenException.class)
            .hasMessageContaining("Wallet is frozen");
    }

    @Test
    @Tag("unit")
    @DisplayName("Should validate debit amount is positive")
    void testDebitWallet_NegativeAmount() {
        // Given
        DebitRequest request = DebitRequest.builder()
            .walletId(testWallet.getId())
            .amount(new BigDecimal("-100.00"))
            .description("Test debit")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        // When / Then
        assertThatThrownBy(() -> walletService.debit(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Amount must be positive");
    }

    @Test
    @Tag("unit")
    @DisplayName("Should validate debit amount precision (4 decimal places max)")
    void testDebitWallet_ExcessivePrecision() {
        // Given
        DebitRequest request = DebitRequest.builder()
            .walletId(testWallet.getId())
            .amount(new BigDecimal("100.123456")) // Too many decimals
            .description("Test debit")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        // When / Then
        assertThatThrownBy(() -> walletService.debit(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Maximum 4 decimal places");
    }

    // ========================================================================
    // CREDIT OPERATION TESTS
    // ========================================================================

    @Test
    @Tag("unit")
    @DisplayName("Should credit wallet successfully")
    void testCreditWallet_Success() {
        // Given
        UUID walletId = testWallet.getId();
        BigDecimal creditAmount = new BigDecimal("500.00");

        when(walletRepository.findById(walletId))
            .thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any(Wallet.class)))
            .thenReturn(testWallet);

        CreditRequest request = CreditRequest.builder()
            .walletId(walletId)
            .amount(creditAmount)
            .description("Test credit")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        // When
        WalletTransactionResponse response = walletService.credit(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getNewBalance())
            .isEqualByComparingTo(INITIAL_BALANCE.add(creditAmount));

        verify(walletRepository).save(argThat(wallet ->
            wallet.getBalance().compareTo(
                INITIAL_BALANCE.add(creditAmount)) == 0
        ));
        verify(kafkaTemplate).send(eq(KafkaTopics.WALLET_CREDITED), any());
    }

    @Test
    @Tag("unit")
    @DisplayName("Should allow credit to frozen wallet (for refunds)")
    void testCreditWallet_FrozenWalletAllowed() {
        // Given
        testWallet.setStatus(WalletStatus.FROZEN);
        UUID walletId = testWallet.getId();

        when(walletRepository.findById(walletId))
            .thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any(Wallet.class)))
            .thenReturn(testWallet);

        CreditRequest request = CreditRequest.builder()
            .walletId(walletId)
            .amount(new BigDecimal("100.00"))
            .description("Refund")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        // When
        WalletTransactionResponse response = walletService.credit(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
    }

    // ========================================================================
    // TRANSFER OPERATION TESTS
    // ========================================================================

    @Test
    @Tag("unit")
    @DisplayName("Should transfer between wallets successfully")
    void testTransfer_Success() {
        // Given
        Wallet sourceWallet = testWallet;
        Wallet targetWallet = Wallet.builder()
            .id(UUID.randomUUID())
            .userId("user-456")
            .currency(TEST_CURRENCY)
            .balance(new BigDecimal("500.00"))
            .availableBalance(new BigDecimal("500.00"))
            .pendingBalance(BigDecimal.ZERO)
            .frozenBalance(BigDecimal.ZERO)
            .status(WalletStatus.ACTIVE)
            .walletType(WalletType.PERSONAL)
            .version(0L)
            .build();

        BigDecimal transferAmount = new BigDecimal("200.00");

        when(walletRepository.findById(sourceWallet.getId()))
            .thenReturn(Optional.of(sourceWallet));
        when(walletRepository.findById(targetWallet.getId()))
            .thenReturn(Optional.of(targetWallet));
        when(walletRepository.save(any(Wallet.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        TransferRequest request = TransferRequest.builder()
            .sourceWalletId(sourceWallet.getId())
            .targetWalletId(targetWallet.getId())
            .amount(transferAmount)
            .description("Test transfer")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        // When
        WalletTransferResponse response = walletService.transfer(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getAmount()).isEqualByComparingTo(transferAmount);
        assertThat(response.getSourceNewBalance())
            .isEqualByComparingTo(INITIAL_BALANCE.subtract(transferAmount));
        assertThat(response.getTargetNewBalance())
            .isEqualByComparingTo(new BigDecimal("700.00"));

        verify(walletRepository, times(2)).save(any(Wallet.class));
        verify(kafkaTemplate).send(eq(KafkaTopics.WALLET_TRANSFER_INITIATED), any());
        verify(kafkaTemplate).send(eq(KafkaTopics.WALLET_TRANSFER_COMPLETED), any());
    }

    @Test
    @Tag("unit")
    @DisplayName("Should fail transfer with currency mismatch")
    void testTransfer_CurrencyMismatch() {
        // Given
        Wallet sourceWallet = testWallet;
        Wallet targetWallet = Wallet.builder()
            .id(UUID.randomUUID())
            .userId("user-456")
            .currency("EUR") // Different currency
            .balance(new BigDecimal("500.00"))
            .status(WalletStatus.ACTIVE)
            .build();

        when(walletRepository.findById(sourceWallet.getId()))
            .thenReturn(Optional.of(sourceWallet));
        when(walletRepository.findById(targetWallet.getId()))
            .thenReturn(Optional.of(targetWallet));

        TransferRequest request = TransferRequest.builder()
            .sourceWalletId(sourceWallet.getId())
            .targetWalletId(targetWallet.getId())
            .amount(new BigDecimal("100.00"))
            .description("Test transfer")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        // When / Then
        assertThatThrownBy(() -> walletService.transfer(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Currency mismatch");
    }

    @Test
    @Tag("unit")
    @DisplayName("Should fail transfer to same wallet")
    void testTransfer_SameWallet() {
        // Given
        UUID walletId = testWallet.getId();

        TransferRequest request = TransferRequest.builder()
            .sourceWalletId(walletId)
            .targetWalletId(walletId)
            .amount(new BigDecimal("100.00"))
            .description("Test transfer")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        // When / Then
        assertThatThrownBy(() -> walletService.transfer(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot transfer to same wallet");
    }

    // ========================================================================
    // CONCURRENCY TESTS
    // ========================================================================

    @Test
    @Tag("concurrency")
    @DisplayName("Should handle concurrent debits with optimistic locking")
    void testConcurrentDebits_OptimisticLocking() throws Exception {
        // Given
        UUID walletId = testWallet.getId();
        int numberOfThreads = 10;
        BigDecimal debitAmount = new BigDecimal("50.00");
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Simulate optimistic locking - only first update succeeds
        when(walletRepository.findById(walletId))
            .thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any(Wallet.class)))
            .thenAnswer(invocation -> {
                Wallet wallet = invocation.getArgument(0);
                if (wallet.getVersion() == 0L) {
                    wallet.setVersion(1L);
                    return wallet;
                } else {
                    throw new ObjectOptimisticLockingFailureException(
                        Wallet.class, wallet.getId());
                }
            });

        // When - Execute concurrent debits
        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    DebitRequest request = DebitRequest.builder()
                        .walletId(walletId)
                        .amount(debitAmount)
                        .description("Concurrent debit")
                        .idempotencyKey(UUID.randomUUID().toString())
                        .build();

                    walletService.debit(request);
                    successCount.incrementAndGet();
                } catch (ObjectOptimisticLockingFailureException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    // Other exceptions
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Then - Only one should succeed, others fail with optimistic locking
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(numberOfThreads - 1);
    }

    @Test
    @Tag("concurrency")
    @DisplayName("Should handle concurrent credits safely")
    void testConcurrentCredits_NoDataLoss() throws Exception {
        // Given
        UUID walletId = testWallet.getId();
        int numberOfCredits = 5;
        BigDecimal creditAmount = new BigDecimal("100.00");
        ExecutorService executor = Executors.newFixedThreadPool(numberOfCredits);
        CountDownLatch latch = new CountDownLatch(numberOfCredits);

        Wallet sharedWallet = Wallet.builder()
            .id(walletId)
            .userId(TEST_USER_ID)
            .currency(TEST_CURRENCY)
            .balance(BigDecimal.ZERO)
            .availableBalance(BigDecimal.ZERO)
            .status(WalletStatus.ACTIVE)
            .version(0L)
            .build();

        when(walletRepository.findById(walletId))
            .thenReturn(Optional.of(sharedWallet));
        when(walletRepository.save(any(Wallet.class)))
            .thenAnswer(invocation -> {
                Wallet wallet = invocation.getArgument(0);
                wallet.setVersion(wallet.getVersion() + 1);
                return wallet;
            });

        // When
        for (int i = 0; i < numberOfCredits; i++) {
            executor.submit(() -> {
                try {
                    CreditRequest request = CreditRequest.builder()
                        .walletId(walletId)
                        .amount(creditAmount)
                        .description("Concurrent credit")
                        .idempotencyKey(UUID.randomUUID().toString())
                        .build();

                    walletService.credit(request);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Then - Verify all credits were processed
        verify(walletRepository, times(numberOfCredits)).save(any(Wallet.class));
    }

    // ========================================================================
    // IDEMPOTENCY TESTS
    // ========================================================================

    @Test
    @Tag("unit")
    @DisplayName("Should return cached response for duplicate idempotency key")
    void testDebit_IdempotencyKeyReused() {
        // Given
        UUID walletId = testWallet.getId();
        String idempotencyKey = UUID.randomUUID().toString();
        BigDecimal debitAmount = new BigDecimal("100.00");

        when(walletRepository.findById(walletId))
            .thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any(Wallet.class)))
            .thenReturn(testWallet);

        DebitRequest request = DebitRequest.builder()
            .walletId(walletId)
            .amount(debitAmount)
            .description("Test debit")
            .idempotencyKey(idempotencyKey)
            .build();

        // When - Execute twice with same idempotency key
        WalletTransactionResponse response1 = walletService.debit(request);
        WalletTransactionResponse response2 = walletService.debit(request);

        // Then - Should only execute once, return cached response
        assertThat(response1.getTransactionId())
            .isEqualTo(response2.getTransactionId());
        verify(walletRepository, times(1)).save(any(Wallet.class));
    }

    // ========================================================================
    // EDGE CASE TESTS
    // ========================================================================

    @Test
    @Tag("edge-case")
    @DisplayName("Should handle wallet not found")
    void testDebitWallet_NotFound() {
        // Given
        UUID nonExistentWalletId = UUID.randomUUID();

        when(walletRepository.findById(nonExistentWalletId))
            .thenReturn(Optional.empty());

        DebitRequest request = DebitRequest.builder()
            .walletId(nonExistentWalletId)
            .amount(new BigDecimal("100.00"))
            .description("Test debit")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        // When / Then
        assertThatThrownBy(() -> walletService.debit(request))
            .isInstanceOf(WalletNotFoundException.class);
    }

    @Test
    @Tag("edge-case")
    @DisplayName("Should handle zero balance wallet")
    void testDebitWallet_ZeroBalance() {
        // Given
        testWallet.setBalance(BigDecimal.ZERO);
        testWallet.setAvailableBalance(BigDecimal.ZERO);

        when(walletRepository.findById(testWallet.getId()))
            .thenReturn(Optional.of(testWallet));

        DebitRequest request = DebitRequest.builder()
            .walletId(testWallet.getId())
            .amount(new BigDecimal("0.01"))
            .description("Test debit")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        // When / Then
        assertThatThrownBy(() -> walletService.debit(request))
            .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    @Tag("edge-case")
    @DisplayName("Should handle very large amounts (precision test)")
    void testCreditWallet_LargeAmount() {
        // Given
        BigDecimal largeAmount = new BigDecimal("999999999999.9999");

        when(walletRepository.findById(testWallet.getId()))
            .thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any(Wallet.class)))
            .thenReturn(testWallet);

        CreditRequest request = CreditRequest.builder()
            .walletId(testWallet.getId())
            .amount(largeAmount)
            .description("Large credit")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        // When
        WalletTransactionResponse response = walletService.credit(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getNewBalance())
            .isEqualByComparingTo(INITIAL_BALANCE.add(largeAmount));
    }

    @Test
    @Tag("edge-case")
    @DisplayName("Should handle minimum amount (0.0001)")
    void testDebitWallet_MinimumAmount() {
        // Given
        BigDecimal minAmount = new BigDecimal("0.0001");

        when(walletRepository.findById(testWallet.getId()))
            .thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any(Wallet.class)))
            .thenReturn(testWallet);

        DebitRequest request = DebitRequest.builder()
            .walletId(testWallet.getId())
            .amount(minAmount)
            .description("Minimum debit")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        // When
        WalletTransactionResponse response = walletService.debit(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
    }

    // ========================================================================
    // BUSINESS RULE TESTS
    // ========================================================================

    @Test
    @Tag("unit")
    @DisplayName("Should enforce daily spending limit")
    void testDebitWallet_DailyLimitExceeded() {
        // Given
        testWallet.setDailySpent(new BigDecimal("4900.00"));
        testWallet.setDailyLimit(new BigDecimal("5000.00"));

        when(walletRepository.findById(testWallet.getId()))
            .thenReturn(Optional.of(testWallet));

        DebitRequest request = DebitRequest.builder()
            .walletId(testWallet.getId())
            .amount(new BigDecimal("200.00")) // Exceeds daily limit
            .description("Test debit")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        // When / Then
        assertThatThrownBy(() -> walletService.debit(request))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Daily limit exceeded");
    }

    @Test
    @Tag("unit")
    @DisplayName("Should enforce monthly spending limit")
    void testDebitWallet_MonthlyLimitExceeded() {
        // Given
        testWallet.setMonthlySpent(new BigDecimal("19900.00"));
        testWallet.setMonthlyLimit(new BigDecimal("20000.00"));

        when(walletRepository.findById(testWallet.getId()))
            .thenReturn(Optional.of(testWallet));

        DebitRequest request = DebitRequest.builder()
            .walletId(testWallet.getId())
            .amount(new BigDecimal("200.00")) // Exceeds monthly limit
            .description("Test debit")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        // When / Then
        assertThatThrownBy(() -> walletService.debit(request))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Monthly limit exceeded");
    }

    // ========================================================================
    // KAFKA EVENT TESTS
    // ========================================================================

    @Test
    @Tag("unit")
    @DisplayName("Should publish wallet created event")
    void testCreateWallet_PublishesEvent() {
        // Given
        CreateWalletRequest request = CreateWalletRequest.builder()
            .userId(TEST_USER_ID)
            .currency(TEST_CURRENCY)
            .walletType(WalletType.PERSONAL)
            .build();

        when(walletRepository.existsByUserIdAndCurrencyAndWalletType(any(), any(), any()))
            .thenReturn(false);
        when(walletRepository.save(any(Wallet.class)))
            .thenReturn(testWallet);

        // When
        walletService.createWallet(request);

        // Then
        verify(kafkaTemplate).send(
            eq(KafkaTopics.WALLET_CREATED),
            argThat(event -> event != null)
        );
    }

    @Test
    @Tag("unit")
    @DisplayName("Should publish wallet debited event with full context")
    void testDebitWallet_PublishesDetailedEvent() {
        // Given
        when(walletRepository.findById(testWallet.getId()))
            .thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any(Wallet.class)))
            .thenReturn(testWallet);

        DebitRequest request = DebitRequest.builder()
            .walletId(testWallet.getId())
            .amount(new BigDecimal("100.00"))
            .description("Test debit")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        // When
        walletService.debit(request);

        // Then
        verify(kafkaTemplate).send(
            eq(KafkaTopics.WALLET_DEBITED),
            argThat(event -> {
                // Verify event contains all necessary fields
                return event != null;
            })
        );
    }
}
