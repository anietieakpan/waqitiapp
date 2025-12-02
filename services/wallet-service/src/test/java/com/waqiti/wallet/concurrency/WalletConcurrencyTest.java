package com.waqiti.wallet.concurrency;

import com.waqiti.common.test.BaseIntegrationTest;
import com.waqiti.common.test.TestDataBuilder;
import com.waqiti.common.test.TestFixtures;
import com.waqiti.wallet.entity.Wallet;
import com.waqiti.wallet.entity.WalletStatus;
import com.waqiti.wallet.entity.WalletType;
import com.waqiti.wallet.exception.InsufficientBalanceException;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.service.WalletService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * Comprehensive concurrency tests for Wallet Service.
 *
 * Tests concurrent operations that could lead to:
 * - Lost updates
 * - Race conditions
 * - Deadlocks
 * - Balance inconsistencies
 * - Double-spending
 *
 * Critical for ensuring financial data integrity under concurrent load.
 * Uses optimistic locking to prevent lost updates.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2024-10-19
 */
@SpringBootTest
@DisplayName("Wallet Concurrency Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WalletConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    private ExecutorService executorService;
    private static final int THREAD_POOL_SIZE = 20;
    private static final int CONCURRENT_OPERATIONS = 100;

    private String testUserId;
    private Wallet testWallet;

    @BeforeEach
    void setUpConcurrencyTest() {
        executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        testUserId = TestFixtures.VERIFIED_USER_ID.toString();

        // Create test wallet with initial balance
        testWallet = Wallet.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .currency(TestFixtures.DEFAULT_CURRENCY)
                .balance(new BigDecimal("10000.0000"))
                .availableBalance(new BigDecimal("10000.0000"))
                .pendingBalance(BigDecimal.ZERO.setScale(4))
                .frozenBalance(BigDecimal.ZERO.setScale(4))
                .status(WalletStatus.ACTIVE)
                .walletType(WalletType.PRIMARY)
                .version(0L)
                .dailyLimit(new BigDecimal("5000.0000"))
                .monthlyLimit(new BigDecimal("25000.0000"))
                .dailySpent(BigDecimal.ZERO.setScale(4))
                .monthlySpent(BigDecimal.ZERO.setScale(4))
                .build();

        testWallet = walletRepository.save(testWallet);
        log("Created test wallet with balance: " + testWallet.getBalance());
    }

    @AfterEach
    void tearDownConcurrencyTest() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Nested
    @DisplayName("Concurrent Credit Operations")
    class ConcurrentCreditTests {

        @Test
        @Order(1)
        @DisplayName("Should handle concurrent credits without lost updates")
        void shouldHandleConcurrentCreditsWithoutLostUpdates() throws Exception {
            // Given
            BigDecimal creditAmount = new BigDecimal("100.0000");
            int numberOfCredits = 50;
            BigDecimal expectedFinalBalance = testWallet.getBalance()
                    .add(creditAmount.multiply(new BigDecimal(numberOfCredits)));

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(numberOfCredits);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger retryCount = new AtomicInteger(0);

            // When - Execute concurrent credits
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < numberOfCredits; i++) {
                Future<?> future = executorService.submit(() -> {
                    try {
                        startLatch.await(); // Wait for all threads to be ready

                        boolean success = false;
                        int attempts = 0;
                        while (!success && attempts < 5) {
                            try {
                                walletService.credit(
                                        testWallet.getId(),
                                        creditAmount,
                                        "Concurrent credit test",
                                        "test-user"
                                );
                                success = true;
                                successCount.incrementAndGet();
                            } catch (OptimisticLockingFailureException e) {
                                retryCount.incrementAndGet();
                                attempts++;
                                Thread.sleep(10 * attempts); // Exponential backoff
                            }
                        }
                    } catch (Exception e) {
                        log("Error in credit operation: " + e.getMessage());
                    } finally {
                        completionLatch.countDown();
                    }
                });
                futures.add(future);
            }

            // Start all operations simultaneously
            startLatch.countDown();

            // Wait for completion
            boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
            assertThat(completed).isTrue();

            // Then - Verify final balance
            Wallet finalWallet = walletRepository.findById(testWallet.getId()).orElseThrow();

            log("Final balance: " + finalWallet.getBalance());
            log("Expected balance: " + expectedFinalBalance);
            log("Successful operations: " + successCount.get());
            log("Total retries: " + retryCount.get());

            assertThat(finalWallet.getBalance()).isEqualByComparingTo(expectedFinalBalance);
            assertThat(successCount.get()).isEqualTo(numberOfCredits);
        }

        @Test
        @Order(2)
        @DisplayName("Should maintain balance accuracy under high concurrency")
        @Timeout(value = 60)
        void shouldMaintainBalanceAccuracyUnderHighConcurrency() throws Exception {
            // Given
            int operationsPerThread = 10;
            BigDecimal operationAmount = new BigDecimal("10.0000");
            BigDecimal expectedFinalBalance = testWallet.getBalance()
                    .add(operationAmount.multiply(new BigDecimal(THREAD_POOL_SIZE * operationsPerThread)));

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(THREAD_POOL_SIZE);
            ConcurrentHashMap<String, Integer> operationCounts = new ConcurrentHashMap<>();

            // When
            for (int threadId = 0; threadId < THREAD_POOL_SIZE; threadId++) {
                final String threadName = "Thread-" + threadId;
                executorService.submit(() -> {
                    try {
                        startLatch.await();
                        int successfulOps = 0;

                        for (int i = 0; i < operationsPerThread; i++) {
                            int retries = 0;
                            while (retries < 10) {
                                try {
                                    walletService.credit(
                                            testWallet.getId(),
                                            operationAmount,
                                            "Concurrent operation",
                                            threadName
                                    );
                                    successfulOps++;
                                    break;
                                } catch (OptimisticLockingFailureException e) {
                                    retries++;
                                    Thread.sleep(5 * retries);
                                }
                            }
                        }
                        operationCounts.put(threadName, successfulOps);
                    } catch (Exception e) {
                        log("Error in thread %s: %s", threadName, e.getMessage());
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = completionLatch.await(60, TimeUnit.SECONDS);

            // Then
            assertThat(completed).isTrue();

            Wallet finalWallet = walletRepository.findById(testWallet.getId()).orElseThrow();
            int totalOperations = operationCounts.values().stream().mapToInt(Integer::intValue).sum();

            log("Total successful operations: " + totalOperations);
            log("Expected operations: " + (THREAD_POOL_SIZE * operationsPerThread));
            log("Final balance: " + finalWallet.getBalance());
            log("Expected balance: " + expectedFinalBalance);

            assertThat(totalOperations).isEqualTo(THREAD_POOL_SIZE * operationsPerThread);
            assertThat(finalWallet.getBalance()).isEqualByComparingTo(expectedFinalBalance);
        }
    }

    @Nested
    @DisplayName("Concurrent Debit Operations")
    class ConcurrentDebitTests {

        @Test
        @Order(3)
        @DisplayName("Should prevent double-spending with concurrent debits")
        void shouldPreventDoubleSpendingWithConcurrentDebits() throws Exception {
            // Given - Wallet has $100, attempt 10 concurrent debits of $50 each
            Wallet smallBalanceWallet = Wallet.builder()
                    .id(UUID.randomUUID())
                    .userId(testUserId)
                    .currency(TestFixtures.DEFAULT_CURRENCY)
                    .balance(new BigDecimal("100.0000"))
                    .availableBalance(new BigDecimal("100.0000"))
                    .pendingBalance(BigDecimal.ZERO.setScale(4))
                    .frozenBalance(BigDecimal.ZERO.setScale(4))
                    .status(WalletStatus.ACTIVE)
                    .walletType(WalletType.PRIMARY)
                    .version(0L)
                    .build();

            smallBalanceWallet = walletRepository.save(smallBalanceWallet);

            BigDecimal debitAmount = new BigDecimal("50.0000");
            int numberOfDebits = 10;

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(numberOfDebits);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            UUID walletId = smallBalanceWallet.getId();

            // When
            for (int i = 0; i < numberOfDebits; i++) {
                executorService.submit(() -> {
                    try {
                        startLatch.await();

                        int retries = 0;
                        while (retries < 5) {
                            try {
                                walletService.debit(
                                        walletId,
                                        debitAmount,
                                        "Concurrent debit test",
                                        "test-user"
                                );
                                successCount.incrementAndGet();
                                break;
                            } catch (InsufficientBalanceException e) {
                                failureCount.incrementAndGet();
                                break; // Don't retry on insufficient balance
                            } catch (OptimisticLockingFailureException e) {
                                retries++;
                                Thread.sleep(10 * retries);
                            }
                        }
                    } catch (Exception e) {
                        log("Error: " + e.getMessage());
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = completionLatch.await(30, TimeUnit.SECONDS);

            // Then
            assertThat(completed).isTrue();

            Wallet finalWallet = walletRepository.findById(walletId).orElseThrow();

            log("Successful debits: " + successCount.get());
            log("Failed debits: " + failureCount.get());
            log("Final balance: " + finalWallet.getBalance());

            // Only 2 debits of $50 should succeed ($100 / $50 = 2)
            assertThat(successCount.get()).isEqualTo(2);
            assertThat(failureCount.get()).isGreaterThan(0); // At least some should fail
            assertThat(finalWallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO.setScale(4));
        }

        @Test
        @Order(4)
        @DisplayName("Should handle mixed credit/debit operations correctly")
        void shouldHandleMixedCreditDebitOperationsCorrectly() throws Exception {
            // Given
            BigDecimal initialBalance = testWallet.getBalance();
            int numberOfOperations = 100;
            BigDecimal operationAmount = new BigDecimal("50.0000");

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(numberOfOperations);
            AtomicInteger creditCount = new AtomicInteger(0);
            AtomicInteger debitCount = new AtomicInteger(0);

            // When - Half credits, half debits
            for (int i = 0; i < numberOfOperations; i++) {
                final boolean isCredit = (i % 2 == 0);
                executorService.submit(() -> {
                    try {
                        startLatch.await();

                        int retries = 0;
                        while (retries < 10) {
                            try {
                                if (isCredit) {
                                    walletService.credit(
                                            testWallet.getId(),
                                            operationAmount,
                                            "Mixed operation credit",
                                            "test-user"
                                    );
                                    creditCount.incrementAndGet();
                                } else {
                                    walletService.debit(
                                            testWallet.getId(),
                                            operationAmount,
                                            "Mixed operation debit",
                                            "test-user"
                                    );
                                    debitCount.incrementAndGet();
                                }
                                break;
                            } catch (OptimisticLockingFailureException e) {
                                retries++;
                                Thread.sleep(5 * retries);
                            } catch (InsufficientBalanceException e) {
                                break; // Stop retrying on insufficient balance
                            }
                        }
                    } catch (Exception e) {
                        log("Error: " + e.getMessage());
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = completionLatch.await(60, TimeUnit.SECONDS);

            // Then
            assertThat(completed).isTrue();

            Wallet finalWallet = walletRepository.findById(testWallet.getId()).orElseThrow();

            log("Credits: " + creditCount.get());
            log("Debits: " + debitCount.get());
            log("Initial balance: " + initialBalance);
            log("Final balance: " + finalWallet.getBalance());

            // Balance should be: initial + (credits * amount) - (debits * amount)
            BigDecimal expectedBalance = initialBalance
                    .add(operationAmount.multiply(new BigDecimal(creditCount.get())))
                    .subtract(operationAmount.multiply(new BigDecimal(debitCount.get())));

            assertThat(finalWallet.getBalance()).isEqualByComparingTo(expectedBalance);
        }
    }

    @Nested
    @DisplayName("Concurrent Freeze/Unfreeze Operations")
    class ConcurrentFreezeTests {

        @Test
        @Order(5)
        @DisplayName("Should handle concurrent freeze operations correctly")
        void shouldHandleConcurrentFreezeOperationsCorrectly() throws Exception {
            // Given
            int numberOfFreezes = 20;
            BigDecimal freezeAmount = new BigDecimal("100.0000");

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(numberOfFreezes);
            AtomicInteger successCount = new AtomicInteger(0);

            // When
            for (int i = 0; i < numberOfFreezes; i++) {
                executorService.submit(() -> {
                    try {
                        startLatch.await();

                        int retries = 0;
                        while (retries < 5) {
                            try {
                                walletService.freezeAmount(
                                        testWallet.getId(),
                                        freezeAmount,
                                        "Concurrent freeze test",
                                        "test-user"
                                );
                                successCount.incrementAndGet();
                                break;
                            } catch (OptimisticLockingFailureException e) {
                                retries++;
                                Thread.sleep(10 * retries);
                            } catch (InsufficientBalanceException e) {
                                break; // Can't freeze more than available
                            }
                        }
                    } catch (Exception e) {
                        log("Error: " + e.getMessage());
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = completionLatch.await(30, TimeUnit.SECONDS);

            // Then
            assertThat(completed).isTrue();

            Wallet finalWallet = walletRepository.findById(testWallet.getId()).orElseThrow();

            log("Successful freezes: " + successCount.get());
            log("Frozen balance: " + finalWallet.getFrozenBalance());
            log("Available balance: " + finalWallet.getAvailableBalance());

            BigDecimal expectedFrozenBalance = freezeAmount.multiply(new BigDecimal(successCount.get()));
            assertThat(finalWallet.getFrozenBalance()).isEqualByComparingTo(expectedFrozenBalance);

            // Total balance should remain unchanged
            assertThat(finalWallet.getBalance()).isEqualByComparingTo(testWallet.getBalance());

            // Available balance should be reduced by frozen amount
            BigDecimal expectedAvailableBalance = testWallet.getBalance().subtract(expectedFrozenBalance);
            assertThat(finalWallet.getAvailableBalance()).isEqualByComparingTo(expectedAvailableBalance);
        }
    }

    @Nested
    @DisplayName("Stress Tests")
    class StressTests {

        @Test
        @Order(6)
        @DisplayName("Should handle extreme concurrent load")
        @Timeout(value = 120)
        void shouldHandleExtremeConcurrentLoad() throws Exception {
            // Given
            int numberOfOperations = 500;
            BigDecimal operationAmount = new BigDecimal("10.0000");

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(numberOfOperations);
            ConcurrentHashMap<String, AtomicInteger> operationCounts = new ConcurrentHashMap<>();
            operationCounts.put("credits", new AtomicInteger(0));
            operationCounts.put("debits", new AtomicInteger(0));
            operationCounts.put("freezes", new AtomicInteger(0));

            // When - Random mix of operations
            for (int i = 0; i < numberOfOperations; i++) {
                executorService.submit(() -> {
                    try {
                        startLatch.await();

                        // Randomly choose operation type
                        int operation = ThreadLocalRandom.current().nextInt(3);
                        int retries = 0;

                        while (retries < 10) {
                            try {
                                switch (operation) {
                                    case 0 -> {
                                        walletService.credit(testWallet.getId(), operationAmount, "Stress test", "test");
                                        operationCounts.get("credits").incrementAndGet();
                                    }
                                    case 1 -> {
                                        walletService.debit(testWallet.getId(), operationAmount, "Stress test", "test");
                                        operationCounts.get("debits").incrementAndGet();
                                    }
                                    case 2 -> {
                                        walletService.freezeAmount(testWallet.getId(), operationAmount, "Stress test", "test");
                                        operationCounts.get("freezes").incrementAndGet();
                                    }
                                }
                                break;
                            } catch (OptimisticLockingFailureException e) {
                                retries++;
                                Thread.sleep(5 * retries);
                            } catch (InsufficientBalanceException e) {
                                break;
                            }
                        }
                    } catch (Exception e) {
                        log("Error: " + e.getMessage());
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = completionLatch.await(120, TimeUnit.SECONDS);

            // Then
            assertThat(completed).isTrue();

            Wallet finalWallet = walletRepository.findById(testWallet.getId()).orElseThrow();

            log("Credits: " + operationCounts.get("credits").get());
            log("Debits: " + operationCounts.get("debits").get());
            log("Freezes: " + operationCounts.get("freezes").get());
            log("Final balance: " + finalWallet.getBalance());

            // Verify balance integrity
            finalWallet.validateState(); // Should not throw exception

            assertThat(finalWallet.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }
    }
}
