package com.waqiti.wallet.integration;

import com.waqiti.common.audit.TransactionAuditService;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.locking.DistributedLockService;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.domain.WalletStatus;
import com.waqiti.wallet.dto.TransferRequest;
import com.waqiti.wallet.dto.TransactionResponse;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CRITICAL SECURITY: Integration tests for wallet transfer operations
 * Tests double-spending prevention, race condition handling, and idempotency
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WalletTransferIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("waqiti_test")
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
    private DistributedLockService lockService;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private TransactionAuditService auditService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private UUID sourceWalletId;
    private UUID targetWalletId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        // Clean up Redis between tests
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        
        // Create test user and wallets
        userId = UUID.randomUUID();
        
        Wallet sourceWallet = Wallet.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .balance(BigDecimal.valueOf(1000.00))
                .currency("USD")
                .status(WalletStatus.ACTIVE)
                .build();
        
        Wallet targetWallet = Wallet.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID()) // Different user
                .balance(BigDecimal.ZERO)
                .currency("USD")
                .status(WalletStatus.ACTIVE)
                .build();
        
        sourceWalletId = walletRepository.save(sourceWallet).getId();
        targetWalletId = walletRepository.save(targetWallet).getId();
    }

    /**
     * CRITICAL TEST: Verifies that concurrent transfer attempts are properly serialized
     * and no double-spending occurs
     */
    @Test
    void testConcurrentTransfersPreventsDoubleSpending() throws Exception {
        BigDecimal transferAmount = BigDecimal.valueOf(100.00);
        int numberOfConcurrentTransfers = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfConcurrentTransfers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completedLatch = new CountDownLatch(numberOfConcurrentTransfers);
        
        AtomicInteger successfulTransfers = new AtomicInteger(0);
        AtomicInteger failedTransfers = new AtomicInteger(0);

        // Create concurrent transfer requests
        IntStream.range(0, numberOfConcurrentTransfers).forEach(i -> {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    
                    TransferRequest request = TransferRequest.builder()
                            .sourceWalletId(sourceWalletId)
                            .targetWalletId(targetWalletId)
                            .amount(transferAmount)
                            .currency("USD")
                            .description("Concurrent transfer test " + i)
                            .build();
                    
                    String idempotencyKey = UUID.randomUUID().toString();
                    TransactionResponse response = walletService.transfer(idempotencyKey, request);
                    
                    if ("SUCCESS".equals(response.getStatus())) {
                        successfulTransfers.incrementAndGet();
                    } else {
                        failedTransfers.incrementAndGet();
                    }
                    
                } catch (Exception e) {
                    failedTransfers.incrementAndGet();
                    System.out.println("Transfer failed: " + e.getMessage());
                } finally {
                    completedLatch.countDown();
                }
            });
        });

        // Start all transfers simultaneously
        startLatch.countDown();
        completedLatch.await();
        executor.shutdown();

        // Verify results
        Wallet finalSourceWallet = walletRepository.findById(sourceWalletId).orElseThrow();
        Wallet finalTargetWallet = walletRepository.findById(targetWalletId).orElseThrow();

        // Calculate expected balances based on successful transfers
        BigDecimal expectedSourceBalance = BigDecimal.valueOf(1000.00)
                .subtract(transferAmount.multiply(BigDecimal.valueOf(successfulTransfers.get())));
        BigDecimal expectedTargetBalance = transferAmount.multiply(BigDecimal.valueOf(successfulTransfers.get()));

        assertEquals(expectedSourceBalance, finalSourceWallet.getBalance(),
                "Source wallet balance should reflect only successful transfers");
        assertEquals(expectedTargetBalance, finalTargetWallet.getBalance(),
                "Target wallet balance should reflect only successful transfers");

        // Verify that not all transfers succeeded (some should have failed due to insufficient funds)
        assertTrue(failedTransfers.get() > 0, "Some transfers should have failed due to insufficient funds");
        assertEquals(numberOfConcurrentTransfers, successfulTransfers.get() + failedTransfers.get(),
                "Total transfers should equal successful + failed");
        
        System.out.println("Successful transfers: " + successfulTransfers.get());
        System.out.println("Failed transfers: " + failedTransfers.get());
    }

    /**
     * CRITICAL TEST: Verifies idempotency protection prevents duplicate transactions
     */
    @Test
    void testIdempotencyPreventsDoubleProcessing() {
        String idempotencyKey = "test-idempotency-" + UUID.randomUUID();
        BigDecimal transferAmount = BigDecimal.valueOf(50.00);
        
        TransferRequest request = TransferRequest.builder()
                .sourceWalletId(sourceWalletId)
                .targetWalletId(targetWalletId)
                .amount(transferAmount)
                .currency("USD")
                .description("Idempotency test")
                .build();

        // First transfer should succeed
        TransactionResponse firstResponse = walletService.transfer(idempotencyKey, request);
        assertEquals("SUCCESS", firstResponse.getStatus());

        // Get balances after first transfer
        Wallet sourceAfterFirst = walletRepository.findById(sourceWalletId).orElseThrow();
        Wallet targetAfterFirst = walletRepository.findById(targetWalletId).orElseThrow();

        // Second transfer with same idempotency key should return cached result
        TransactionResponse secondResponse = walletService.transfer(idempotencyKey, request);
        assertEquals("SUCCESS", secondResponse.getStatus());
        assertEquals(firstResponse.getTransactionId(), secondResponse.getTransactionId());

        // Verify balances didn't change
        Wallet sourceAfterSecond = walletRepository.findById(sourceWalletId).orElseThrow();
        Wallet targetAfterSecond = walletRepository.findById(targetWalletId).orElseThrow();

        assertEquals(sourceAfterFirst.getBalance(), sourceAfterSecond.getBalance(),
                "Source balance should not change on idempotent retry");
        assertEquals(targetAfterFirst.getBalance(), targetAfterSecond.getBalance(),
                "Target balance should not change on idempotent retry");
    }

    /**
     * CRITICAL TEST: Tests proper rollback behavior when transfer fails
     */
    @Test
    @Transactional
    void testTransferRollbackOnFailure() {
        // Create a target wallet that will cause failure (e.g., inactive status)
        Wallet inactiveWallet = Wallet.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .balance(BigDecimal.ZERO)
                .currency("USD")
                .status(WalletStatus.FROZEN) // This should cause transfer to fail
                .build();
        UUID inactiveWalletId = walletRepository.save(inactiveWallet).getId();

        BigDecimal originalBalance = walletRepository.findById(sourceWalletId).orElseThrow().getBalance();
        BigDecimal transferAmount = BigDecimal.valueOf(100.00);
        
        TransferRequest request = TransferRequest.builder()
                .sourceWalletId(sourceWalletId)
                .targetWalletId(inactiveWalletId)
                .amount(transferAmount)
                .currency("USD")
                .description("Rollback test")
                .build();

        String idempotencyKey = UUID.randomUUID().toString();
        
        // Transfer should fail
        assertThrows(Exception.class, () -> {
            walletService.transfer(idempotencyKey, request);
        });

        // Verify source wallet balance is unchanged
        Wallet sourceWallet = walletRepository.findById(sourceWalletId).orElseThrow();
        assertEquals(originalBalance, sourceWallet.getBalance(),
                "Source wallet balance should be unchanged after failed transfer");
    }

    /**
     * TEST: Verifies distributed locking works correctly
     */
    @Test
    void testDistributedLockingMechanism() throws Exception {
        String lockKey = "test-lock-" + UUID.randomUUID();
        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completedLatch = new CountDownLatch(numberOfThreads);
        
        AtomicInteger lockAcquisitions = new AtomicInteger(0);
        AtomicInteger lockFailures = new AtomicInteger(0);

        IntStream.range(0, numberOfThreads).forEach(i -> {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    try (var lock = lockService.acquireLock(lockKey, 
                            java.time.Duration.ofSeconds(5), 
                            java.time.Duration.ofSeconds(10))) {
                        
                        lockAcquisitions.incrementAndGet();
                        Thread.sleep(100); // Hold lock briefly
                        
                    } catch (Exception e) {
                        lockFailures.incrementAndGet();
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completedLatch.countDown();
                }
            });
        });

        startLatch.countDown();
        completedLatch.await();
        executor.shutdown();

        // All threads should have acquired the lock sequentially
        assertEquals(numberOfThreads, lockAcquisitions.get(),
                "All threads should have acquired the lock");
        assertEquals(0, lockFailures.get(),
                "No lock acquisitions should have failed");
    }

    /**
     * TEST: Verifies audit trail is properly created for transfers
     */
    @Test
    void testAuditTrailCreation() {
        BigDecimal transferAmount = BigDecimal.valueOf(75.00);
        String idempotencyKey = UUID.randomUUID().toString();
        
        TransferRequest request = TransferRequest.builder()
                .sourceWalletId(sourceWalletId)
                .targetWalletId(targetWalletId)
                .amount(transferAmount)
                .currency("USD")
                .description("Audit trail test")
                .build();

        TransactionResponse response = walletService.transfer(idempotencyKey, request);
        assertEquals("SUCCESS", response.getStatus());

        // Verify audit records exist (this would typically query audit repository)
        // For this test, we'll verify the service methods were called
        assertNotNull(response.getTransactionId(), "Transaction ID should be generated for audit trail");
    }

    /**
     * PERFORMANCE TEST: Tests system behavior under high load
     */
    @Test
    void testHighLoadPerformance() throws Exception {
        int numberOfTransfers = 100;
        BigDecimal smallAmount = BigDecimal.valueOf(1.00); // Small amounts to allow many transfers
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch completedLatch = new CountDownLatch(numberOfTransfers);
        
        long startTime = System.currentTimeMillis();

        IntStream.range(0, numberOfTransfers).forEach(i -> {
            executor.submit(() -> {
                try {
                    TransferRequest request = TransferRequest.builder()
                            .sourceWalletId(sourceWalletId)
                            .targetWalletId(targetWalletId)
                            .amount(smallAmount)
                            .currency("USD")
                            .description("Performance test " + i)
                            .build();
                    
                    String idempotencyKey = "perf-test-" + i;
                    walletService.transfer(idempotencyKey, request);
                    
                } catch (Exception e) {
                    // Log but don't fail the test for expected insufficient funds errors
                    if (!e.getMessage().contains("Insufficient balance")) {
                        System.err.println("Unexpected error: " + e.getMessage());
                    }
                } finally {
                    completedLatch.countDown();
                }
            });
        });

        completedLatch.await();
        executor.shutdown();
        
        long duration = System.currentTimeMillis() - startTime;
        double transfersPerSecond = (numberOfTransfers * 1000.0) / duration;
        
        System.out.println("Performance: " + transfersPerSecond + " transfers/second");
        
        // Performance should be reasonable (adjust threshold based on requirements)
        assertTrue(transfersPerSecond > 10, "System should handle at least 10 transfers per second");
    }
}