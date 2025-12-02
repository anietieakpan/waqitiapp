package com.waqiti.wallet.service.impl;

import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.domain.WalletStatus;
import com.waqiti.wallet.domain.WalletType;
import com.waqiti.wallet.domain.Transaction;
import com.waqiti.wallet.domain.TransactionType;
import com.waqiti.wallet.domain.TransactionStatus;
import com.waqiti.wallet.domain.PaymentMethod;
import com.waqiti.wallet.domain.Currency;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.repository.TransactionRepository;
import com.waqiti.wallet.service.BankingService;
import com.waqiti.wallet.service.CardService;
import com.waqiti.wallet.service.CryptoService;
import com.waqiti.wallet.service.NotificationService;
import com.waqiti.wallet.dto.*;
import com.waqiti.wallet.metrics.WalletMetricsService;
import com.waqiti.common.audit.ComprehensiveTransactionAuditService;
import com.waqiti.common.security.SecurityContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///waqiti_test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "wallet.default.daily-limit=5000.00",
        "wallet.default.monthly-limit=50000.00",
        "wallet.minimum-transaction-amount=0.01"
})
@DisplayName("Wallet Service Implementation Tests")
class WalletServiceImplTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("waqiti_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private WalletServiceImpl walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @MockBean
    private BankingService bankingService;

    @MockBean
    private CardService cardService;

    @MockBean
    private CryptoService cryptoService;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private WalletMetricsService metricsService;

    @MockBean
    private ComprehensiveTransactionAuditService comprehensiveAuditService;

    @MockBean
    private SecurityContext securityContext;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private MeterRegistry meterRegistry;

    @MockBean
    private CacheManager cacheManager;

    @MockBean
    private Counter counter;

    @MockBean
    private Timer timer;

    @MockBean
    private Timer.Sample timerSample;

    private String testUserId;
    private String testWalletId;

    @BeforeEach
    void setUp() {
        walletRepository.deleteAll();
        transactionRepository.deleteAll();

        testUserId = UUID.randomUUID().toString();
        testWalletId = UUID.randomUUID().toString();

        // Mock default behaviors
        when(meterRegistry.counter(anyString())).thenReturn(counter);
        when(meterRegistry.timer(anyString())).thenReturn(timer);
        when(Timer.start(meterRegistry)).thenReturn(timerSample);
        when(counter.increment()).thenReturn(counter);
        when(counter.increment(anyDouble())).thenReturn(counter);
        when(timerSample.stop(any(Timer.class))).thenReturn(timer);

        Counter.Builder counterBuilder = mock(Counter.Builder.class);
        Timer.Builder timerBuilder = mock(Timer.Builder.class);
        when(Counter.builder(anyString())).thenReturn(counterBuilder);
        when(Timer.builder(anyString())).thenReturn(timerBuilder);
        when(counterBuilder.tag(anyString(), anyString())).thenReturn(counterBuilder);
        when(counterBuilder.register(any(MeterRegistry.class))).thenReturn(counter);
        when(timerBuilder.register(any(MeterRegistry.class))).thenReturn(timer);
    }

    @Nested
    @DisplayName("Wallet Creation Tests")
    class WalletCreationTests {

        @Test
        @Transactional
        @DisplayName("Should create wallet with default limits successfully")
        void shouldCreateWalletWithDefaultLimitsSuccessfully() {
            CreateWalletRequest request = CreateWalletRequest.builder()
                .userId(testUserId)
                .walletType(WalletType.PERSONAL)
                .currency(Currency.USD)
                .build();

            WalletResponse response = walletService.createWallet(request);

            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(testUserId);
            assertThat(response.getWalletType()).isEqualTo(WalletType.PERSONAL);
            assertThat(response.getCurrency()).isEqualTo(Currency.USD);
            assertThat(response.getBalance()).isEqualTo(BigDecimal.ZERO);
            assertThat(response.getAvailableBalance()).isEqualTo(BigDecimal.ZERO);
            assertThat(response.getDailyLimit()).isEqualTo(new BigDecimal("5000.00"));
            assertThat(response.getMonthlyLimit()).isEqualTo(new BigDecimal("50000.00"));
            assertThat(response.getStatus()).isEqualTo(WalletStatus.ACTIVE);
            assertThat(response.getCreatedAt()).isNotNull();

            List<Wallet> wallets = walletRepository.findAll();
            assertThat(wallets).hasSize(1);

            verify(comprehensiveAuditService).auditWalletCreation(
                eq(testUserId), 
                any(Wallet.class), 
                contains("Wallet created via API")
            );
        }

        @Test
        @Transactional
        @DisplayName("Should create wallet with custom limits")
        void shouldCreateWalletWithCustomLimits() {
            CreateWalletRequest request = CreateWalletRequest.builder()
                .userId(testUserId)
                .walletType(WalletType.BUSINESS)
                .currency(Currency.EUR)
                .dailyLimit(new BigDecimal("10000.00"))
                .monthlyLimit(new BigDecimal("100000.00"))
                .build();

            WalletResponse response = walletService.createWallet(request);

            assertThat(response.getDailyLimit()).isEqualTo(new BigDecimal("10000.00"));
            assertThat(response.getMonthlyLimit()).isEqualTo(new BigDecimal("100000.00"));
            assertThat(response.getCurrency()).isEqualTo(Currency.EUR);
            assertThat(response.getWalletType()).isEqualTo(WalletType.BUSINESS);
        }

        @Test
        @Transactional
        @DisplayName("Should return existing wallet if duplicate creation attempted")
        void shouldReturnExistingWalletIfDuplicateCreationAttempted() {
            // Create first wallet
            Wallet existingWallet = createTestWallet();
            existingWallet.setUserId(testUserId);
            existingWallet.setCurrency(Currency.USD);
            existingWallet.setWalletType(WalletType.PERSONAL);
            walletRepository.save(existingWallet);

            CreateWalletRequest request = CreateWalletRequest.builder()
                .userId(testUserId)
                .walletType(WalletType.PERSONAL)
                .currency(Currency.USD)
                .build();

            WalletResponse response = walletService.createWallet(request);

            assertThat(response.getId()).isEqualTo(existingWallet.getId());

            List<Wallet> wallets = walletRepository.findAll();
            assertThat(wallets).hasSize(1);
        }

        @Test
        @Transactional
        @DisplayName("Should create bank account wallet successfully")
        void shouldCreateBankAccountWalletSuccessfully() {
            when(bankingService.createAccount(any(BankAccountCreateRequest.class)))
                .thenReturn(BankAccountCreateResponse.builder().successful(true).build());

            CreateWalletRequest request = CreateWalletRequest.builder()
                .userId(testUserId)
                .walletType(WalletType.BANK_ACCOUNT)
                .currency(Currency.USD)
                .bankAccountType("CHECKING")
                .build();

            WalletResponse response = walletService.createWallet(request);

            assertThat(response.getWalletType()).isEqualTo(WalletType.BANK_ACCOUNT);

            verify(bankingService).createAccount(any(BankAccountCreateRequest.class));
        }

        @Test
        @DisplayName("Should validate required fields for wallet creation")
        void shouldValidateRequiredFieldsForWalletCreation() {
            CreateWalletRequest invalidRequest = CreateWalletRequest.builder()
                .userId(null) // Missing user ID
                .walletType(WalletType.PERSONAL)
                .currency(Currency.USD)
                .build();

            assertThatThrownBy(() -> walletService.createWallet(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID is required");
        }

        @Test
        @DisplayName("Should validate wallet type for creation")
        void shouldValidateWalletTypeForCreation() {
            CreateWalletRequest invalidRequest = CreateWalletRequest.builder()
                .userId(testUserId)
                .walletType(null) // Missing wallet type
                .currency(Currency.USD)
                .build();

            assertThatThrownBy(() -> walletService.createWallet(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wallet type is required");
        }

        @Test
        @DisplayName("Should validate currency for creation")
        void shouldValidateWalletCurrencyForCreation() {
            CreateWalletRequest invalidRequest = CreateWalletRequest.builder()
                .userId(testUserId)
                .walletType(WalletType.PERSONAL)
                .currency(null) // Missing currency
                .build();

            assertThatThrownBy(() -> walletService.createWallet(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency is required");
        }
    }

    @Nested
    @DisplayName("Wallet Retrieval Tests")
    class WalletRetrievalTests {

        @Test
        @Transactional
        @DisplayName("Should retrieve wallet by ID successfully")
        void shouldRetrieveWalletByIdSuccessfully() {
            Wallet wallet = createTestWallet();
            wallet = walletRepository.save(wallet);

            WalletResponse response = walletService.getWallet(wallet.getId());

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(wallet.getId());
            assertThat(response.getUserId()).isEqualTo(wallet.getUserId());
            assertThat(response.getWalletType()).isEqualTo(wallet.getWalletType());
            assertThat(response.getCurrency()).isEqualTo(wallet.getCurrency());
            assertThat(response.getBalance()).isEqualTo(wallet.getBalance());
        }

        @Test
        @DisplayName("Should throw exception when wallet not found")
        void shouldThrowExceptionWhenWalletNotFound() {
            String nonExistentWalletId = UUID.randomUUID().toString();

            assertThatThrownBy(() -> walletService.getWallet(nonExistentWalletId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wallet not found");
        }

        @Test
        @Transactional
        @DisplayName("Should retrieve user wallets successfully")
        void shouldRetrieveUserWalletsSuccessfully() {
            // Create multiple wallets for user
            Wallet wallet1 = createTestWallet();
            wallet1.setUserId(testUserId);
            wallet1.setCurrency(Currency.USD);
            walletRepository.save(wallet1);

            Wallet wallet2 = createTestWallet();
            wallet2.setUserId(testUserId);
            wallet2.setCurrency(Currency.EUR);
            walletRepository.save(wallet2);

            // Create wallet for different user
            Wallet otherUserWallet = createTestWallet();
            otherUserWallet.setUserId(UUID.randomUUID().toString());
            walletRepository.save(otherUserWallet);

            List<WalletResponse> userWallets = walletService.getUserWallets(testUserId);

            assertThat(userWallets).hasSize(2);
            assertThat(userWallets).extracting(WalletResponse::getUserId)
                .containsOnly(testUserId);
            assertThat(userWallets).extracting(WalletResponse::getCurrency)
                .containsExactlyInAnyOrder(Currency.USD, Currency.EUR);
        }

        @Test
        @Transactional
        @DisplayName("Should return empty list when user has no wallets")
        void shouldReturnEmptyListWhenUserHasNoWallets() {
            String userWithNoWallets = UUID.randomUUID().toString();

            List<WalletResponse> userWallets = walletService.getUserWallets(userWithNoWallets);

            assertThat(userWallets).isEmpty();
        }
    }

    @Nested
    @DisplayName("Wallet Transfer Tests")
    class WalletTransferTests {

        @Test
        @Transactional
        @DisplayName("Should transfer between wallets successfully")
        void shouldTransferBetweenWalletsSuccessfully() {
            // Create source wallet with balance
            Wallet sourceWallet = createTestWallet();
            sourceWallet.setBalance(new BigDecimal("1000.00"));
            sourceWallet.setAvailableBalance(new BigDecimal("1000.00"));
            sourceWallet = walletRepository.save(sourceWallet);

            // Create destination wallet
            Wallet destWallet = createTestWallet();
            destWallet.setBalance(new BigDecimal("500.00"));
            destWallet.setAvailableBalance(new BigDecimal("500.00"));
            destWallet = walletRepository.save(destWallet);

            TransferRequest request = TransferRequest.builder()
                .fromWalletId(sourceWallet.getId())
                .toWalletId(destWallet.getId())
                .amount(new BigDecimal("200.00"))
                .currency(Currency.USD)
                .description("Test transfer")
                .build();

            TransferResponse response = walletService.transfer(request);

            assertThat(response).isNotNull();
            assertThat(response.isSuccessful()).isTrue();
            assertThat(response.getAmount()).isEqualTo(new BigDecimal("200.00"));
            assertThat(response.getDebitTransactionId()).isNotNull();
            assertThat(response.getCreditTransactionId()).isNotNull();

            // Verify wallet balances updated
            Wallet updatedSource = walletRepository.findById(sourceWallet.getId()).orElseThrow();
            Wallet updatedDest = walletRepository.findById(destWallet.getId()).orElseThrow();

            assertThat(updatedSource.getBalance()).isEqualTo(new BigDecimal("800.00"));
            assertThat(updatedSource.getAvailableBalance()).isEqualTo(new BigDecimal("800.00"));
            assertThat(updatedDest.getBalance()).isEqualTo(new BigDecimal("700.00"));
            assertThat(updatedDest.getAvailableBalance()).isEqualTo(new BigDecimal("700.00"));

            // Verify transactions created
            List<Transaction> transactions = transactionRepository.findAll();
            assertThat(transactions).hasSize(2);

            verify(comprehensiveAuditService, times(2)).auditBalanceChange(
                anyString(), anyString(), any(BigDecimal.class), any(BigDecimal.class),
                anyString(), anyString()
            );
        }

        @Test
        @Transactional
        @DisplayName("Should fail transfer when insufficient balance")
        void shouldFailTransferWhenInsufficientBalance() {
            // Create source wallet with insufficient balance
            Wallet sourceWallet = createTestWallet();
            sourceWallet.setBalance(new BigDecimal("50.00"));
            sourceWallet.setAvailableBalance(new BigDecimal("50.00"));
            sourceWallet = walletRepository.save(sourceWallet);

            Wallet destWallet = createTestWallet();
            destWallet = walletRepository.save(destWallet);

            TransferRequest request = TransferRequest.builder()
                .fromWalletId(sourceWallet.getId())
                .toWalletId(destWallet.getId())
                .amount(new BigDecimal("100.00")) // More than available
                .currency(Currency.USD)
                .build();

            TransferResponse response = walletService.transfer(request);

            assertThat(response.isSuccessful()).isFalse();
            assertThat(response.getErrorCode()).isEqualTo("INSUFFICIENT_BALANCE");
            assertThat(response.getErrorMessage()).contains("Insufficient balance");

            // Verify no transactions created
            List<Transaction> transactions = transactionRepository.findAll();
            assertThat(transactions).isEmpty();
        }

        @Test
        @Transactional
        @DisplayName("Should fail transfer when daily limit exceeded")
        void shouldFailTransferWhenDailyLimitExceeded() {
            // Setup source wallet with high balance but low daily limit
            Wallet sourceWallet = createTestWallet();
            sourceWallet.setBalance(new BigDecimal("10000.00"));
            sourceWallet.setAvailableBalance(new BigDecimal("10000.00"));
            sourceWallet.setDailyLimit(new BigDecimal("100.00")); // Low daily limit
            sourceWallet = walletRepository.save(sourceWallet);

            Wallet destWallet = createTestWallet();
            destWallet = walletRepository.save(destWallet);

            // Mock repository to return daily total near limit
            when(transactionRepository.getTotalDebitAmountByWalletAndDateRange(
                eq(sourceWallet.getId()), any(), any()))
                .thenReturn(new BigDecimal("50.00")); // Already used $50 today

            TransferRequest request = TransferRequest.builder()
                .fromWalletId(sourceWallet.getId())
                .toWalletId(destWallet.getId())
                .amount(new BigDecimal("75.00")) // Would exceed daily limit
                .currency(Currency.USD)
                .build();

            TransferResponse response = walletService.transfer(request);

            assertThat(response.isSuccessful()).isFalse();
            assertThat(response.getErrorCode()).isEqualTo("DAILY_LIMIT_EXCEEDED");
            assertThat(response.getErrorMessage()).contains("Daily transfer limit exceeded");
        }

        @Test
        @DisplayName("Should validate transfer request fields")
        void shouldValidateTransferRequestFields() {
            TransferRequest invalidRequest = TransferRequest.builder()
                .fromWalletId(null) // Missing source wallet
                .toWalletId(testWalletId)
                .amount(new BigDecimal("100.00"))
                .currency(Currency.USD)
                .build();

            assertThatThrownBy(() -> walletService.transfer(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source wallet ID is required");
        }

        @Test
        @DisplayName("Should prevent transfer to same wallet")
        void shouldPreventTransferToSameWallet() {
            TransferRequest invalidRequest = TransferRequest.builder()
                .fromWalletId(testWalletId)
                .toWalletId(testWalletId) // Same wallet
                .amount(new BigDecimal("100.00"))
                .currency(Currency.USD)
                .build();

            assertThatThrownBy(() -> walletService.transfer(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot transfer to the same wallet");
        }

        @Test
        @DisplayName("Should validate minimum transfer amount")
        void shouldValidateMinimumTransferAmount() {
            TransferRequest invalidRequest = TransferRequest.builder()
                .fromWalletId(testWalletId)
                .toWalletId(UUID.randomUUID().toString())
                .amount(new BigDecimal("0.005")) // Below minimum
                .currency(Currency.USD)
                .build();

            assertThatThrownBy(() -> walletService.transfer(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid transfer amount");
        }
    }

    @Nested
    @DisplayName("Wallet Deposit Tests")
    class WalletDepositTests {

        @Test
        @Transactional
        @DisplayName("Should process bank transfer deposit successfully")
        void shouldProcessBankTransferDepositSuccessfully() {
            Wallet wallet = createTestWallet();
            wallet.setBalance(new BigDecimal("100.00"));
            wallet.setAvailableBalance(new BigDecimal("100.00"));
            wallet = walletRepository.save(wallet);

            when(bankingService.processDeposit(any(DepositRequest.class)))
                .thenReturn(DepositProcessingResult.success("BANK_TXN_123", "TestBank"));

            DepositRequest request = DepositRequest.builder()
                .walletId(wallet.getId())
                .amount(new BigDecimal("250.00"))
                .currency(Currency.USD)
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentMethodId("bank-account-123")
                .description("Test deposit")
                .build();

            DepositResponse response = walletService.deposit(request);

            assertThat(response.isSuccessful()).isTrue();
            assertThat(response.getAmount()).isEqualTo(new BigDecimal("250.00"));
            assertThat(response.getNewBalance()).isEqualTo(new BigDecimal("350.00"));
            assertThat(response.getProviderTransactionId()).isEqualTo("BANK_TXN_123");

            // Verify wallet balance updated
            Wallet updatedWallet = walletRepository.findById(wallet.getId()).orElseThrow();
            assertThat(updatedWallet.getBalance()).isEqualTo(new BigDecimal("350.00"));
            assertThat(updatedWallet.getAvailableBalance()).isEqualTo(new BigDecimal("350.00"));

            // Verify transaction created
            List<Transaction> transactions = transactionRepository.findAll();
            assertThat(transactions).hasSize(1);
            Transaction transaction = transactions.get(0);
            assertThat(transaction.getType()).isEqualTo(TransactionType.CREDIT);
            assertThat(transaction.getAmount()).isEqualTo(new BigDecimal("250.00"));
            assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

            verify(comprehensiveAuditService).auditDeposit(eq(request), anyString(), eq(transaction), eq("TestBank"), eq("SUCCESS"));
            verify(notificationService).sendDepositCompleted(eq(wallet.getUserId()), anyString(), eq(new BigDecimal("250.00")));
        }

        @Test
        @Transactional
        @DisplayName("Should process credit card deposit successfully")
        void shouldProcessCreditCardDepositSuccessfully() {
            Wallet wallet = createTestWallet();
            wallet = walletRepository.save(wallet);

            when(cardService.processDeposit(any(DepositRequest.class)))
                .thenReturn(DepositProcessingResult.success("CARD_TXN_456", "Visa"));

            DepositRequest request = DepositRequest.builder()
                .walletId(wallet.getId())
                .amount(new BigDecimal("100.00"))
                .currency(Currency.USD)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .paymentMethodId("card-123")
                .build();

            DepositResponse response = walletService.deposit(request);

            assertThat(response.isSuccessful()).isTrue();
            assertThat(response.getProviderTransactionId()).isEqualTo("CARD_TXN_456");

            verify(cardService).processDeposit(request);
        }

        @Test
        @Transactional
        @DisplayName("Should handle deposit failure from payment processor")
        void shouldHandleDepositFailureFromPaymentProcessor() {
            Wallet wallet = createTestWallet();
            wallet = walletRepository.save(wallet);

            when(bankingService.processDeposit(any(DepositRequest.class)))
                .thenReturn(DepositProcessingResult.failed("BANK_ERROR", "Bank service unavailable"));

            DepositRequest request = DepositRequest.builder()
                .walletId(wallet.getId())
                .amount(new BigDecimal("100.00"))
                .currency(Currency.USD)
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .build();

            DepositResponse response = walletService.deposit(request);

            assertThat(response.isSuccessful()).isFalse();
            assertThat(response.getErrorCode()).isEqualTo("BANK_ERROR");
            assertThat(response.getErrorMessage()).isEqualTo("Bank service unavailable");

            // Verify wallet balance unchanged
            Wallet unchangedWallet = walletRepository.findById(wallet.getId()).orElseThrow();
            assertThat(unchangedWallet.getBalance()).isEqualTo(BigDecimal.ZERO);

            // Verify no transaction created
            List<Transaction> transactions = transactionRepository.findAll();
            assertThat(transactions).isEmpty();
        }

        @Test
        @Transactional
        @DisplayName("Should fail deposit for inactive wallet")
        void shouldFailDepositForInactiveWallet() {
            Wallet wallet = createTestWallet();
            wallet.setStatus(WalletStatus.FROZEN);
            wallet = walletRepository.save(wallet);

            DepositRequest request = DepositRequest.builder()
                .walletId(wallet.getId())
                .amount(new BigDecimal("100.00"))
                .currency(Currency.USD)
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .build();

            DepositResponse response = walletService.deposit(request);

            assertThat(response.isSuccessful()).isFalse();
            assertThat(response.getErrorCode()).isEqualTo("WALLET_INACTIVE");
            assertThat(response.getErrorMessage()).contains("Wallet is not active");
        }

        @Test
        @DisplayName("Should validate deposit request fields")
        void shouldValidateDepositRequestFields() {
            DepositRequest invalidRequest = DepositRequest.builder()
                .walletId(null) // Missing wallet ID
                .amount(new BigDecimal("100.00"))
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .build();

            assertThatThrownBy(() -> walletService.deposit(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wallet ID is required");
        }
    }

    @Nested
    @DisplayName("Wallet Withdrawal Tests")
    class WalletWithdrawalTests {

        @Test
        @Transactional
        @DisplayName("Should process bank transfer withdrawal successfully")
        void shouldProcessBankTransferWithdrawalSuccessfully() {
            Wallet wallet = createTestWallet();
            wallet.setBalance(new BigDecimal("500.00"));
            wallet.setAvailableBalance(new BigDecimal("500.00"));
            wallet = walletRepository.save(wallet);

            when(bankingService.processWithdrawal(any(WithdrawRequest.class)))
                .thenReturn(WithdrawProcessingResult.success("BANK_WITHDRAW_123", "TestBank"));

            WithdrawRequest request = WithdrawRequest.builder()
                .walletId(wallet.getId())
                .amount(new BigDecimal("150.00"))
                .currency(Currency.USD)
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentMethodId("bank-account-123")
                .description("Test withdrawal")
                .build();

            WithdrawResponse response = walletService.withdraw(request);

            assertThat(response.isSuccessful()).isTrue();
            assertThat(response.getAmount()).isEqualTo(new BigDecimal("150.00"));
            assertThat(response.getNewBalance()).isEqualTo(new BigDecimal("350.00"));
            assertThat(response.getProviderTransactionId()).isEqualTo("BANK_WITHDRAW_123");

            // Verify wallet balance updated
            Wallet updatedWallet = walletRepository.findById(wallet.getId()).orElseThrow();
            assertThat(updatedWallet.getBalance()).isEqualTo(new BigDecimal("350.00"));
            assertThat(updatedWallet.getAvailableBalance()).isEqualTo(new BigDecimal("350.00"));

            // Verify transaction created
            List<Transaction> transactions = transactionRepository.findAll();
            assertThat(transactions).hasSize(1);
            Transaction transaction = transactions.get(0);
            assertThat(transaction.getType()).isEqualTo(TransactionType.DEBIT);
            assertThat(transaction.getAmount()).isEqualTo(new BigDecimal("-150.00")); // Negative for debit
            assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

            verify(comprehensiveAuditService).auditWithdrawal(eq(request), anyString(), eq(transaction), eq("TestBank"), eq("SUCCESS"));
            verify(notificationService).sendWithdrawalCompleted(eq(wallet.getUserId()), anyString(), eq(new BigDecimal("150.00")));
        }

        @Test
        @Transactional
        @DisplayName("Should fail withdrawal when insufficient balance")
        void shouldFailWithdrawalWhenInsufficientBalance() {
            Wallet wallet = createTestWallet();
            wallet.setBalance(new BigDecimal("50.00"));
            wallet.setAvailableBalance(new BigDecimal("50.00"));
            wallet = walletRepository.save(wallet);

            WithdrawRequest request = WithdrawRequest.builder()
                .walletId(wallet.getId())
                .amount(new BigDecimal("100.00")) // More than available
                .currency(Currency.USD)
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .build();

            WithdrawResponse response = walletService.withdraw(request);

            assertThat(response.isSuccessful()).isFalse();
            assertThat(response.getErrorCode()).isEqualTo("INSUFFICIENT_BALANCE");
            assertThat(response.getErrorMessage()).contains("Insufficient balance for withdrawal");

            // Verify wallet balance unchanged
            Wallet unchangedWallet = walletRepository.findById(wallet.getId()).orElseThrow();
            assertThat(unchangedWallet.getBalance()).isEqualTo(new BigDecimal("50.00"));

            // Verify no transaction created
            List<Transaction> transactions = transactionRepository.findAll();
            assertThat(transactions).isEmpty();
        }

        @Test
        @Transactional
        @DisplayName("Should handle withdrawal failure from payment processor")
        void shouldHandleWithdrawalFailureFromPaymentProcessor() {
            Wallet wallet = createTestWallet();
            wallet.setBalance(new BigDecimal("500.00"));
            wallet.setAvailableBalance(new BigDecimal("500.00"));
            wallet = walletRepository.save(wallet);

            when(bankingService.processWithdrawal(any(WithdrawRequest.class)))
                .thenReturn(WithdrawProcessingResult.failed("BANK_ERROR", "Bank service unavailable"));

            WithdrawRequest request = WithdrawRequest.builder()
                .walletId(wallet.getId())
                .amount(new BigDecimal("100.00"))
                .currency(Currency.USD)
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .build();

            WithdrawResponse response = walletService.withdraw(request);

            assertThat(response.isSuccessful()).isFalse();
            assertThat(response.getErrorCode()).isEqualTo("BANK_ERROR");
            assertThat(response.getErrorMessage()).isEqualTo("Bank service unavailable");

            // Verify wallet balance unchanged
            Wallet unchangedWallet = walletRepository.findById(wallet.getId()).orElseThrow();
            assertThat(unchangedWallet.getBalance()).isEqualTo(new BigDecimal("500.00"));

            // Verify no transaction created
            List<Transaction> transactions = transactionRepository.findAll();
            assertThat(transactions).isEmpty();
        }

        @Test
        @DisplayName("Should validate withdrawal request fields")
        void shouldValidateWithdrawalRequestFields() {
            WithdrawRequest invalidRequest = WithdrawRequest.builder()
                .walletId(testWalletId)
                .amount(null) // Missing amount
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .build();

            assertThatThrownBy(() -> walletService.withdraw(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid withdrawal amount");
        }
    }

    @Nested
    @DisplayName("Balance and Analytics Tests")
    class BalanceAndAnalyticsTests {

        @Test
        @Transactional
        @DisplayName("Should retrieve wallet balance successfully")
        void shouldRetrieveWalletBalanceSuccessfully() {
            Wallet wallet = createTestWallet();
            wallet.setBalance(new BigDecimal("1250.75"));
            wallet.setAvailableBalance(new BigDecimal("1000.50"));
            wallet = walletRepository.save(wallet);

            BalanceResponse response = walletService.getBalance(wallet.getId());

            assertThat(response).isNotNull();
            assertThat(response.getWalletId()).isEqualTo(wallet.getId());
            assertThat(response.getBalance()).isEqualTo(new BigDecimal("1250.75"));
            assertThat(response.getAvailableBalance()).isEqualTo(new BigDecimal("1000.50"));
            assertThat(response.getCurrency()).isEqualTo(wallet.getCurrency());
            assertThat(response.getLastUpdated()).isNotNull();
        }

        @Test
        @Transactional
        @DisplayName("Should generate wallet analytics successfully")
        void shouldGenerateWalletAnalyticsSuccessfully() {
            // Create wallet
            Wallet wallet = createTestWallet();
            wallet.setUserId(testUserId);
            wallet.setBalance(new BigDecimal("1000.00"));
            wallet = walletRepository.save(wallet);

            // Create transactions
            Transaction creditTx = Transaction.builder()
                .id(UUID.randomUUID().toString())
                .walletId(wallet.getId())
                .userId(testUserId)
                .type(TransactionType.CREDIT)
                .amount(new BigDecimal("500.00"))
                .currency(Currency.USD)
                .status(TransactionStatus.COMPLETED)
                .createdAt(LocalDateTime.now().minusDays(5))
                .build();
            transactionRepository.save(creditTx);

            Transaction debitTx = Transaction.builder()
                .id(UUID.randomUUID().toString())
                .walletId(wallet.getId())
                .userId(testUserId)
                .type(TransactionType.DEBIT)
                .amount(new BigDecimal("-200.00"))
                .currency(Currency.USD)
                .status(TransactionStatus.COMPLETED)
                .createdAt(LocalDateTime.now().minusDays(3))
                .build();
            transactionRepository.save(debitTx);

            Map<String, Object> analytics = walletService.getWalletAnalytics(testUserId, "month");

            assertThat(analytics).isNotNull();
            assertThat(analytics.get("totalBalance")).isEqualTo(new BigDecimal("1000.00"));
            assertThat(analytics.get("totalTransactions")).isEqualTo(2L);
            assertThat(analytics.get("totalIncome")).isEqualTo(new BigDecimal("500.00"));
            assertThat(analytics.get("totalExpense")).isEqualTo(new BigDecimal("200.00"));
            assertThat(analytics.get("netFlow")).isEqualTo(new BigDecimal("300.00"));
            assertThat(analytics.get("walletCount")).isEqualTo(1);
            assertThat(analytics.get("period")).isEqualTo("month");
        }

        @Test
        @Transactional
        @DisplayName("Should retrieve transaction history with pagination")
        void shouldRetrieveTransactionHistoryWithPagination() {
            Wallet wallet = createTestWallet();
            wallet = walletRepository.save(wallet);

            // Create multiple transactions
            for (int i = 0; i < 5; i++) {
                Transaction transaction = Transaction.builder()
                    .id(UUID.randomUUID().toString())
                    .walletId(wallet.getId())
                    .userId(wallet.getUserId())
                    .type(TransactionType.CREDIT)
                    .amount(new BigDecimal("100.00"))
                    .currency(Currency.USD)
                    .status(TransactionStatus.COMPLETED)
                    .createdAt(LocalDateTime.now().minusDays(i))
                    .build();
                transactionRepository.save(transaction);
            }

            Pageable pageable = PageRequest.of(0, 3);
            Page<TransactionResponse> transactions = walletService.getTransactions(wallet.getId(), pageable);

            assertThat(transactions).isNotNull();
            assertThat(transactions.getContent()).hasSize(3);
            assertThat(transactions.getTotalElements()).isEqualTo(5);
            assertThat(transactions.getTotalPages()).isEqualTo(2);
            assertThat(transactions.isFirst()).isTrue();
            assertThat(transactions.hasNext()).isTrue();

            // Verify transactions are ordered by creation date descending
            List<TransactionResponse> content = transactions.getContent();
            for (int i = 0; i < content.size() - 1; i++) {
                assertThat(content.get(i).getCreatedAt())
                    .isAfterOrEqualTo(content.get(i + 1).getCreatedAt());
            }
        }
    }

    @Nested
    @DisplayName("Wallet Status Management Tests")
    class WalletStatusManagementTests {

        @Test
        @Transactional
        @DisplayName("Should update wallet status successfully")
        void shouldUpdateWalletStatusSuccessfully() {
            Wallet wallet = createTestWallet();
            wallet.setStatus(WalletStatus.ACTIVE);
            wallet = walletRepository.save(wallet);

            WalletResponse response = walletService.updateWalletStatus(wallet.getId(), WalletStatus.FROZEN);

            assertThat(response.getStatus()).isEqualTo(WalletStatus.FROZEN);

            // Verify wallet updated in database
            Wallet updatedWallet = walletRepository.findById(wallet.getId()).orElseThrow();
            assertThat(updatedWallet.getStatus()).isEqualTo(WalletStatus.FROZEN);

            verify(comprehensiveAuditService).auditWalletStatusChange(
                eq(updatedWallet), 
                eq("ACTIVE"), 
                eq("FROZEN"), 
                eq("Status updated via API"), 
                anyString()
            );
            verify(notificationService).sendWalletStatusChanged(eq(wallet.getUserId()), eq(wallet.getId()), eq(WalletStatus.FROZEN));
        }
    }

    @Nested
    @DisplayName("Error Handling and Edge Cases Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle analytics generation error gracefully")
        void shouldHandleAnalyticsGenerationErrorGracefully() {
            String nonExistentUserId = UUID.randomUUID().toString();

            Map<String, Object> analytics = walletService.getWalletAnalytics(nonExistentUserId, "month");

            assertThat(analytics).containsKey("error");
            assertThat(analytics.get("error")).isEqualTo("Failed to generate analytics");
        }

        @Test
        @Transactional
        @DisplayName("Should handle wallet creation fallback")
        void shouldHandleWalletCreationFallback() {
            CreateWalletRequest request = CreateWalletRequest.builder()
                .userId(testUserId)
                .walletType(WalletType.PERSONAL)
                .currency(Currency.USD)
                .build();

            Exception testException = new RuntimeException("Database error");

            assertThatThrownBy(() -> walletService.createWalletFallback(request, testException))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Wallet service temporarily unavailable");
        }

        @Test
        @DisplayName("Should handle transfer fallback gracefully")
        void shouldHandleTransferFallbackGracefully() {
            TransferRequest request = TransferRequest.builder()
                .fromWalletId(testWalletId)
                .toWalletId(UUID.randomUUID().toString())
                .amount(new BigDecimal("100.00"))
                .currency(Currency.USD)
                .build();

            Exception testException = new RuntimeException("Service error");
            TransferResponse response = walletService.transferFallback(request, testException);

            assertThat(response.isSuccessful()).isFalse();
            assertThat(response.getErrorCode()).isEqualTo("SERVICE_UNAVAILABLE");
            assertThat(response.getErrorMessage()).contains("Transfer service temporarily unavailable");
        }
    }

    /**
     * Helper methods
     */
    private Wallet createTestWallet() {
        return Wallet.builder()
            .id(UUID.randomUUID().toString())
            .userId(testUserId)
            .walletType(WalletType.PERSONAL)
            .currency(Currency.USD)
            .balance(BigDecimal.ZERO)
            .availableBalance(BigDecimal.ZERO)
            .dailyLimit(new BigDecimal("5000.00"))
            .monthlyLimit(new BigDecimal("50000.00"))
            .status(WalletStatus.ACTIVE)
            .interestEnabled(false)
            .overdraftEnabled(false)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }
}