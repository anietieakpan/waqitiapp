package com.waqiti.wallet.integration;

import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.domain.WalletStatus;
import com.waqiti.wallet.domain.WalletTransaction;
import com.waqiti.wallet.dto.DepositRequest;
import com.waqiti.wallet.dto.WithdrawalRequest;
import com.waqiti.wallet.dto.TransferRequest;
import com.waqiti.wallet.dto.TransactionResponse;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.repository.WalletTransactionRepository;
import com.waqiti.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class WalletBalanceConsistencyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("wallet_consistency_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    void testBalanceConsistency_DepositAndWithdrawal() {
        UUID userId = UUID.randomUUID();
        Wallet wallet = createWallet(userId, new BigDecimal("1000.00"));
        UUID walletId = wallet.getId();

        DepositRequest depositRequest = DepositRequest.builder()
                .walletId(walletId)
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .description("Test deposit")
                .build();

        TransactionResponse depositResponse = walletService.deposit(UUID.randomUUID().toString(), depositRequest);
        assertEquals("SUCCESS", depositResponse.getStatus());

        Wallet afterDeposit = walletRepository.findById(walletId).orElseThrow();
        assertEquals(new BigDecimal("1500.00"), afterDeposit.getBalance());

        WithdrawalRequest withdrawalRequest = WithdrawalRequest.builder()
                .walletId(walletId)
                .amount(new BigDecimal("300.00"))
                .currency("USD")
                .description("Test withdrawal")
                .build();

        TransactionResponse withdrawalResponse = walletService.withdraw(UUID.randomUUID().toString(), withdrawalRequest);
        assertEquals("SUCCESS", withdrawalResponse.getStatus());

        Wallet afterWithdrawal = walletRepository.findById(walletId).orElseThrow();
        assertEquals(new BigDecimal("1200.00"), afterWithdrawal.getBalance());
    }

    @Test
    void testBalanceConsistency_MultipleOperations() {
        UUID userId = UUID.randomUUID();
        Wallet wallet = createWallet(userId, new BigDecimal("5000.00"));
        UUID walletId = wallet.getId();

        BigDecimal expectedBalance = new BigDecimal("5000.00");

        for (int i = 0; i < 10; i++) {
            DepositRequest depositRequest = DepositRequest.builder()
                    .walletId(walletId)
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .description("Deposit " + i)
                    .build();

            walletService.deposit(UUID.randomUUID().toString(), depositRequest);
            expectedBalance = expectedBalance.add(new BigDecimal("100.00"));
        }

        for (int i = 0; i < 5; i++) {
            WithdrawalRequest withdrawalRequest = WithdrawalRequest.builder()
                    .walletId(walletId)
                    .amount(new BigDecimal("50.00"))
                    .currency("USD")
                    .description("Withdrawal " + i)
                    .build();

            walletService.withdraw(UUID.randomUUID().toString(), withdrawalRequest);
            expectedBalance = expectedBalance.subtract(new BigDecimal("50.00"));
        }

        Wallet finalWallet = walletRepository.findById(walletId).orElseThrow();
        assertEquals(expectedBalance, finalWallet.getBalance());
        assertEquals(new BigDecimal("5750.00"), finalWallet.getBalance());
    }

    @Test
    void testTransactionHistory_MatchesBalance() {
        UUID userId = UUID.randomUUID();
        Wallet wallet = createWallet(userId, new BigDecimal("2000.00"));
        UUID walletId = wallet.getId();

        List<BigDecimal> deposits = List.of(
            new BigDecimal("100.00"),
            new BigDecimal("250.00"),
            new BigDecimal("75.50")
        );

        List<BigDecimal> withdrawals = List.of(
            new BigDecimal("50.00"),
            new BigDecimal("125.00")
        );

        for (BigDecimal amount : deposits) {
            DepositRequest request = DepositRequest.builder()
                    .walletId(walletId)
                    .amount(amount)
                    .currency("USD")
                    .description("Test deposit")
                    .build();
            walletService.deposit(UUID.randomUUID().toString(), request);
        }

        for (BigDecimal amount : withdrawals) {
            WithdrawalRequest request = WithdrawalRequest.builder()
                    .walletId(walletId)
                    .amount(amount)
                    .currency("USD")
                    .description("Test withdrawal")
                    .build();
            walletService.withdraw(UUID.randomUUID().toString(), request);
        }

        Wallet finalWallet = walletRepository.findById(walletId).orElseThrow();
        List<WalletTransaction> transactions = transactionRepository.findByWalletId(walletId);

        BigDecimal calculatedBalance = new BigDecimal("2000.00");
        for (WalletTransaction tx : transactions) {
            if ("DEPOSIT".equals(tx.getType())) {
                calculatedBalance = calculatedBalance.add(tx.getAmount());
            } else if ("WITHDRAWAL".equals(tx.getType())) {
                calculatedBalance = calculatedBalance.subtract(tx.getAmount());
            }
        }

        assertEquals(finalWallet.getBalance(), calculatedBalance,
            "Wallet balance should match sum of all transactions");
    }

    @Test
    void testConcurrentDeposits_BalanceConsistency() throws Exception {
        UUID userId = UUID.randomUUID();
        Wallet wallet = createWallet(userId, new BigDecimal("0.00"));
        UUID walletId = wallet.getId();

        int numberOfDeposits = 50;
        BigDecimal depositAmount = new BigDecimal("10.00");
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(numberOfDeposits);

        IntStream.range(0, numberOfDeposits).forEach(i -> {
            executor.submit(() -> {
                try {
                    DepositRequest request = DepositRequest.builder()
                            .walletId(walletId)
                            .amount(depositAmount)
                            .currency("USD")
                            .description("Concurrent deposit " + i)
                            .build();

                    walletService.deposit(UUID.randomUUID().toString(), request);
                } catch (Exception e) {
                    System.err.println("Deposit failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await();
        executor.shutdown();

        Wallet finalWallet = walletRepository.findById(walletId).orElseThrow();
        BigDecimal expectedBalance = depositAmount.multiply(BigDecimal.valueOf(numberOfDeposits));

        assertEquals(expectedBalance, finalWallet.getBalance(),
            "All concurrent deposits should be reflected in balance");
    }

    @Test
    void testConcurrentWithdrawals_PreventOverdraft() throws Exception {
        UUID userId = UUID.randomUUID();
        Wallet wallet = createWallet(userId, new BigDecimal("500.00"));
        UUID walletId = wallet.getId();

        int numberOfWithdrawals = 20;
        BigDecimal withdrawalAmount = new BigDecimal("50.00");
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(numberOfWithdrawals);

        AtomicInteger successfulWithdrawals = new AtomicInteger(0);
        AtomicInteger failedWithdrawals = new AtomicInteger(0);

        IntStream.range(0, numberOfWithdrawals).forEach(i -> {
            executor.submit(() -> {
                try {
                    WithdrawalRequest request = WithdrawalRequest.builder()
                            .walletId(walletId)
                            .amount(withdrawalAmount)
                            .currency("USD")
                            .description("Concurrent withdrawal " + i)
                            .build();

                    TransactionResponse response = walletService.withdraw(UUID.randomUUID().toString(), request);
                    if ("SUCCESS".equals(response.getStatus())) {
                        successfulWithdrawals.incrementAndGet();
                    } else {
                        failedWithdrawals.incrementAndGet();
                    }
                } catch (Exception e) {
                    failedWithdrawals.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await();
        executor.shutdown();

        Wallet finalWallet = walletRepository.findById(walletId).orElseThrow();
        
        assertTrue(finalWallet.getBalance().compareTo(BigDecimal.ZERO) >= 0,
            "Balance should never go negative");
        
        BigDecimal expectedBalance = new BigDecimal("500.00")
            .subtract(withdrawalAmount.multiply(BigDecimal.valueOf(successfulWithdrawals.get())));
        
        assertEquals(expectedBalance, finalWallet.getBalance());
        assertEquals(10, successfulWithdrawals.get(), "Only 10 withdrawals should succeed (500/50)");
    }

    @Test
    void testMixedConcurrentOperations_BalanceIntegrity() throws Exception {
        UUID userId = UUID.randomUUID();
        Wallet wallet = createWallet(userId, new BigDecimal("1000.00"));
        UUID walletId = wallet.getId();

        int operations = 30;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(operations);

        List<BigDecimal> successfulDeposits = new ArrayList<>();
        List<BigDecimal> successfulWithdrawals = new ArrayList<>();

        IntStream.range(0, operations).forEach(i -> {
            executor.submit(() -> {
                try {
                    if (i % 2 == 0) {
                        BigDecimal amount = new BigDecimal("25.00");
                        DepositRequest request = DepositRequest.builder()
                                .walletId(walletId)
                                .amount(amount)
                                .currency("USD")
                                .description("Mixed operation deposit " + i)
                                .build();

                        TransactionResponse response = walletService.deposit(UUID.randomUUID().toString(), request);
                        if ("SUCCESS".equals(response.getStatus())) {
                            synchronized (successfulDeposits) {
                                successfulDeposits.add(amount);
                            }
                        }
                    } else {
                        BigDecimal amount = new BigDecimal("15.00");
                        WithdrawalRequest request = WithdrawalRequest.builder()
                                .walletId(walletId)
                                .amount(amount)
                                .currency("USD")
                                .description("Mixed operation withdrawal " + i)
                                .build();

                        TransactionResponse response = walletService.withdraw(UUID.randomUUID().toString(), request);
                        if ("SUCCESS".equals(response.getStatus())) {
                            synchronized (successfulWithdrawals) {
                                successfulWithdrawals.add(amount);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Operation failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await();
        executor.shutdown();

        Wallet finalWallet = walletRepository.findById(walletId).orElseThrow();
        
        BigDecimal totalDeposits = successfulDeposits.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalWithdrawals = successfulWithdrawals.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expectedBalance = new BigDecimal("1000.00")
            .add(totalDeposits)
            .subtract(totalWithdrawals);

        assertEquals(expectedBalance, finalWallet.getBalance(),
            "Balance should match initial + deposits - withdrawals");
        assertTrue(finalWallet.getBalance().compareTo(BigDecimal.ZERO) >= 0,
            "Balance should never be negative");
    }

    @Test
    void testOptimisticLocking_PreventsLostUpdates() throws Exception {
        UUID userId = UUID.randomUUID();
        Wallet wallet = createWallet(userId, new BigDecimal("1000.00"));
        UUID walletId = wallet.getId();

        int concurrentUpdates = 20;
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(concurrentUpdates);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger retryCount = new AtomicInteger(0);

        IntStream.range(0, concurrentUpdates).forEach(i -> {
            executor.submit(() -> {
                try {
                    DepositRequest request = DepositRequest.builder()
                            .walletId(walletId)
                            .amount(new BigDecimal("5.00"))
                            .currency("USD")
                            .description("Optimistic lock test " + i)
                            .build();

                    walletService.deposit(UUID.randomUUID().toString(), request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    if (e.getMessage().contains("OptimisticLock")) {
                        retryCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await();
        executor.shutdown();

        Wallet finalWallet = walletRepository.findById(walletId).orElseThrow();
        BigDecimal expectedBalance = new BigDecimal("1000.00")
            .add(new BigDecimal("5.00").multiply(BigDecimal.valueOf(successCount.get())));

        assertEquals(expectedBalance, finalWallet.getBalance());
        assertEquals(concurrentUpdates, successCount.get(), "All updates should eventually succeed");
    }

    @Test
    void testWalletFreezing_PreventsFurtherTransactions() {
        UUID userId = UUID.randomUUID();
        Wallet wallet = createWallet(userId, new BigDecimal("500.00"));
        UUID walletId = wallet.getId();

        wallet.setStatus(WalletStatus.FROZEN);
        walletRepository.save(wallet);

        DepositRequest depositRequest = DepositRequest.builder()
                .walletId(walletId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .description("Attempt on frozen wallet")
                .build();

        assertThrows(Exception.class, () -> {
            walletService.deposit(UUID.randomUUID().toString(), depositRequest);
        }, "Operations on frozen wallet should fail");
    }

    @Test
    void testMultiCurrencyBalance_IndependentTracking() {
        UUID userId = UUID.randomUUID();
        
        Wallet usdWallet = createWallet(userId, new BigDecimal("1000.00"), "USD");
        Wallet eurWallet = createWallet(userId, new BigDecimal("500.00"), "EUR");

        DepositRequest usdDeposit = DepositRequest.builder()
                .walletId(usdWallet.getId())
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .description("USD deposit")
                .build();

        DepositRequest eurDeposit = DepositRequest.builder()
                .walletId(eurWallet.getId())
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .description("EUR deposit")
                .build();

        walletService.deposit(UUID.randomUUID().toString(), usdDeposit);
        walletService.deposit(UUID.randomUUID().toString(), eurDeposit);

        Wallet finalUsdWallet = walletRepository.findById(usdWallet.getId()).orElseThrow();
        Wallet finalEurWallet = walletRepository.findById(eurWallet.getId()).orElseThrow();

        assertEquals(new BigDecimal("1200.00"), finalUsdWallet.getBalance());
        assertEquals(new BigDecimal("600.00"), finalEurWallet.getBalance());
    }

    private Wallet createWallet(UUID userId, BigDecimal initialBalance) {
        return createWallet(userId, initialBalance, "USD");
    }

    private Wallet createWallet(UUID userId, BigDecimal initialBalance, String currency) {
        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .balance(initialBalance)
                .currency(currency)
                .status(WalletStatus.ACTIVE)
                .build();
        return walletRepository.save(wallet);
    }
}