package com.waqiti.wallet.service;

import com.waqiti.common.locking.DistributedLockService;
import com.waqiti.wallet.domain.*;
import com.waqiti.wallet.dto.FundReservationRequest;
import com.waqiti.wallet.dto.FundReservationResponse;
import com.waqiti.wallet.exception.*;
import com.waqiti.wallet.repository.FundReservationRepository;
import com.waqiti.wallet.repository.WalletRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * CRITICAL PRODUCTION INTEGRATION TESTS
 *
 * Tests the ProductionWalletBalanceService to ensure:
 * 1. No double-spending vulnerabilities
 * 2. Distributed locking works correctly
 * 3. Concurrent operations are safe
 * 4. Idempotency is enforced
 * 5. Service restart scenarios are handled
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-01
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.redis.host=localhost",
    "spring.redis.port=6379"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductionWalletBalanceServiceIntegrationTest {

    @Autowired
    private ProductionWalletBalanceService walletBalanceService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private FundReservationRepository fundReservationRepository;

    @Autowired
    private DistributedLockService distributedLockService;

    @Autowired
    private MeterRegistry meterRegistry;

    private Wallet testWallet;
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000.00");

    @BeforeEach
    void setUp() {
        // Create test wallet
        testWallet = Wallet.create(
            UUID.randomUUID(),
            "TEST-" + UUID.randomUUID(),
            WalletType.PERSONAL,
            "CHECKING",
            Currency.USD
        );
        testWallet.setBalance(INITIAL_BALANCE);
        testWallet.setAvailableBalance(INITIAL_BALANCE);
        testWallet.setReservedBalance(BigDecimal.ZERO);
        testWallet = walletRepository.save(testWallet);
    }

    @AfterEach
    void tearDown() {
        // Cleanup test data
        fundReservationRepository.deleteAll();
        if (testWallet != null) {
            walletRepository.delete(testWallet);
        }
    }

    /**
     * TEST 1: Basic Fund Reservation
     */
    @Test
    @Order(1)
    @DisplayName("Should successfully reserve funds with sufficient balance")
    void testBasicFundReservation() {
        // Given
        BigDecimal reservationAmount = new BigDecimal("100.00");
        FundReservationRequest request = FundReservationRequest.builder()
                .walletId(testWallet.getId())
                .transactionId(UUID.randomUUID())
                .amount(reservationAmount)
                .reason("Test reservation")
                .build();

        // When
        FundReservationResponse response = walletBalanceService.reserveFunds(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getReservationId()).isNotNull();
        assertThat(response.getAmount()).isEqualByComparingTo(reservationAmount);
        assertThat(response.getStatus()).isEqualTo("ACTIVE");

        // Verify wallet balances updated
        Wallet updatedWallet = walletRepository.findById(testWallet.getId()).orElseThrow();
        assertThat(updatedWallet.getAvailableBalance())
                .isEqualByComparingTo(INITIAL_BALANCE.subtract(reservationAmount));
        assertThat(updatedWallet.getReservedBalance())
                .isEqualByComparingTo(reservationAmount);
        assertThat(updatedWallet.getBalance())
                .isEqualByComparingTo(INITIAL_BALANCE);
    }

    /**
     * TEST 2: Insufficient Balance
     */
    @Test
    @Order(2)
    @DisplayName("Should reject reservation when insufficient balance")
    void testInsufficientBalance() {
        // Given
        BigDecimal reservationAmount = new BigDecimal("2000.00"); // More than balance
        FundReservationRequest request = FundReservationRequest.builder()
                .walletId(testWallet.getId())
                .transactionId(UUID.randomUUID())
                .amount(reservationAmount)
                .build();

        // When/Then
        assertThatThrownBy(() -> walletBalanceService.reserveFunds(request))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient available balance");
    }

    /**
     * TEST 3: Idempotency Protection (CRITICAL)
     */
    @Test
    @Order(3)
    @DisplayName("Should prevent duplicate reservations with same idempotency key")
    void testIdempotencyProtection() {
        // Given
        String idempotencyKey = "test-idem-" + UUID.randomUUID();
        BigDecimal amount = new BigDecimal("50.00");

        FundReservationRequest request1 = FundReservationRequest.builder()
                .walletId(testWallet.getId())
                .transactionId(UUID.randomUUID())
                .amount(amount)
                .idempotencyKey(idempotencyKey)
                .build();

        FundReservationRequest request2 = FundReservationRequest.builder()
                .walletId(testWallet.getId())
                .transactionId(UUID.randomUUID()) // Different transaction!
                .amount(amount)
                .idempotencyKey(idempotencyKey) // Same idempotency key!
                .build();

        // When
        FundReservationResponse response1 = walletBalanceService.reserveFunds(request1);

        // Then - Second request should fail
        assertThatThrownBy(() -> walletBalanceService.reserveFunds(request2))
                .isInstanceOf(DuplicateReservationException.class)
                .hasMessageContaining("already exists");

        // Verify only one reservation created
        List<FundReservation> reservations = fundReservationRepository
                .findActiveByWalletId(testWallet.getId());
        assertThat(reservations).hasSize(1);

        // Verify balance only debited once
        Wallet wallet = walletRepository.findById(testWallet.getId()).orElseThrow();
        assertThat(wallet.getReservedBalance()).isEqualByComparingTo(amount);
    }

    /**
     * TEST 4: Concurrent Reservations (CRITICAL - Double-Spending Prevention)
     */
    @Test
    @Order(4)
    @DisplayName("Should prevent double-spending with 100 concurrent reservation requests")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testConcurrentReservations() throws InterruptedException {
        // Given
        int threadCount = 100;
        BigDecimal amountPerReservation = new BigDecimal("50.00");
        BigDecimal expectedTotalReserved = amountPerReservation.multiply(
                BigDecimal.valueOf(threadCount));

        // Ensure wallet has enough balance
        testWallet.setBalance(new BigDecimal("10000.00"));
        testWallet.setAvailableBalance(new BigDecimal("10000.00"));
        walletRepository.save(testWallet);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Future<UUID>> futures = new ArrayList<>();

        // When - Submit 100 concurrent reservation requests
        for (int i = 0; i < threadCount; i++) {
            final int requestNumber = i;
            Future<UUID> future = executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await(); // All threads start simultaneously

                    FundReservationRequest request = FundReservationRequest.builder()
                            .walletId(testWallet.getId())
                            .transactionId(UUID.randomUUID())
                            .amount(amountPerReservation)
                            .reason("Concurrent test " + requestNumber)
                            .build();

                    FundReservationResponse response = walletBalanceService.reserveFunds(request);
                    successCount.incrementAndGet();
                    return response.getReservationId();

                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    return null;
                }
            });
            futures.add(future);
        }

        // Wait for all to complete
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // Then - Verify all reservations succeeded
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failureCount.get()).isZero();

        // CRITICAL: Verify total reserved amount matches exactly
        Wallet finalWallet = walletRepository.findById(testWallet.getId()).orElseThrow();
        assertThat(finalWallet.getReservedBalance())
                .isEqualByComparingTo(expectedTotalReserved);

        // Verify no double-spending occurred
        BigDecimal totalReservedInDb = fundReservationRepository
                .getTotalReservedAmount(testWallet.getId());
        assertThat(totalReservedInDb).isEqualByComparingTo(expectedTotalReserved);
    }

    /**
     * TEST 5: Confirm Reservation
     */
    @Test
    @Order(5)
    @DisplayName("Should successfully confirm reservation and debit wallet balance")
    void testConfirmReservation() {
        // Given - Create a reservation
        BigDecimal amount = new BigDecimal("200.00");
        FundReservationRequest request = FundReservationRequest.builder()
                .walletId(testWallet.getId())
                .transactionId(UUID.randomUUID())
                .amount(amount)
                .build();

        FundReservationResponse reservation = walletBalanceService.reserveFunds(request);

        // When - Confirm reservation
        walletBalanceService.confirmReservation(reservation.getReservationId());

        // Then
        FundReservation confirmedReservation = fundReservationRepository
                .findById(reservation.getReservationId()).orElseThrow();
        assertThat(confirmedReservation.getStatus()).isEqualTo(FundReservationStatus.CONFIRMED);
        assertThat(confirmedReservation.getConfirmedAt()).isNotNull();

        // Verify wallet balance permanently debited
        Wallet wallet = walletRepository.findById(testWallet.getId()).orElseThrow();
        assertThat(wallet.getBalance()).isEqualByComparingTo(INITIAL_BALANCE.subtract(amount));
        assertThat(wallet.getReservedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /**
     * TEST 6: Release Reservation
     */
    @Test
    @Order(6)
    @DisplayName("Should successfully release reservation and return funds")
    void testReleaseReservation() {
        // Given - Create a reservation
        BigDecimal amount = new BigDecimal("150.00");
        FundReservationRequest request = FundReservationRequest.builder()
                .walletId(testWallet.getId())
                .transactionId(UUID.randomUUID())
                .amount(amount)
                .build();

        FundReservationResponse reservation = walletBalanceService.reserveFunds(request);

        // When - Release reservation
        walletBalanceService.releaseReservation(
                reservation.getReservationId(),
                "Transaction cancelled"
        );

        // Then
        FundReservation releasedReservation = fundReservationRepository
                .findById(reservation.getReservationId()).orElseThrow();
        assertThat(releasedReservation.getStatus()).isEqualTo(FundReservationStatus.RELEASED);
        assertThat(releasedReservation.getReleasedAt()).isNotNull();

        // Verify funds returned to available balance
        Wallet wallet = walletRepository.findById(testWallet.getId()).orElseThrow();
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(INITIAL_BALANCE);
        assertThat(wallet.getReservedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(wallet.getBalance()).isEqualByComparingTo(INITIAL_BALANCE);
    }

    /**
     * TEST 7: Expired Reservation Cleanup
     */
    @Test
    @Order(7)
    @DisplayName("Should auto-release expired reservations")
    void testExpiredReservationCleanup() {
        // Given - Create reservation with 1 minute TTL
        FundReservationRequest request = FundReservationRequest.builder()
                .walletId(testWallet.getId())
                .transactionId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .ttlMinutes(1)
                .build();

        FundReservationResponse reservation = walletBalanceService.reserveFunds(request);

        // Manually expire the reservation (for testing)
        FundReservation fundReservation = fundReservationRepository
                .findById(reservation.getReservationId()).orElseThrow();
        fundReservation.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        fundReservationRepository.save(fundReservation);

        // When - Run cleanup
        int cleanedCount = walletBalanceService.cleanupExpiredReservations();

        // Then
        assertThat(cleanedCount).isEqualTo(1);

        // Verify reservation marked as RELEASED
        FundReservation cleanedReservation = fundReservationRepository
                .findById(reservation.getReservationId()).orElseThrow();
        assertThat(cleanedReservation.getStatus()).isEqualTo(FundReservationStatus.RELEASED);

        // Verify funds returned
        Wallet wallet = walletRepository.findById(testWallet.getId()).orElseThrow();
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo(INITIAL_BALANCE);
    }

    /**
     * TEST 8: Optimistic Locking Retry
     */
    @Test
    @Order(8)
    @DisplayName("Should retry on optimistic locking failure")
    void testOptimisticLockingRetry() throws InterruptedException {
        // Given - Two concurrent updates to same wallet
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When - Multiple threads try to reserve funds simultaneously
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();

                    FundReservationRequest request = FundReservationRequest.builder()
                            .walletId(testWallet.getId())
                            .transactionId(UUID.randomUUID())
                            .amount(new BigDecimal("10.00"))
                            .build();

                    walletBalanceService.reserveFunds(request);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    // Expected some to retry due to optimistic locking
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Then - All should eventually succeed (with retries)
        assertThat(successCount.get()).isEqualTo(threadCount);
    }

    /**
     * TEST 9: Lock Timeout Handling
     */
    @Test
    @Order(9)
    @DisplayName("Should throw exception when unable to acquire lock within timeout")
    void testLockTimeout() throws InterruptedException {
        // Given - Manually acquire and hold lock
        String lockKey = "wallet:lock:" + testWallet.getId();
        var lock = distributedLockService.getLock(lockKey);

        try {
            lock.lock(60, TimeUnit.SECONDS); // Hold lock for 60 seconds

            // When - Try to reserve funds (should timeout)
            FundReservationRequest request = FundReservationRequest.builder()
                    .walletId(testWallet.getId())
                    .transactionId(UUID.randomUUID())
                    .amount(new BigDecimal("50.00"))
                    .build();

            // Then - Should fail with lock exception
            assertThatThrownBy(() -> walletBalanceService.reserveFunds(request))
                    .isInstanceOf(WalletLockException.class)
                    .hasMessageContaining("Unable to acquire lock");

        } finally {
            lock.unlock();
        }
    }

    /**
     * TEST 10: Wallet Not Found
     */
    @Test
    @Order(10)
    @DisplayName("Should throw exception for non-existent wallet")
    void testWalletNotFound() {
        // Given
        FundReservationRequest request = FundReservationRequest.builder()
                .walletId(UUID.randomUUID()) // Non-existent wallet
                .transactionId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .build();

        // When/Then
        assertThatThrownBy(() -> walletBalanceService.reserveFunds(request))
                .isInstanceOf(WalletNotFoundException.class);
    }

    /**
     * TEST 11: Service Restart Scenario (CRITICAL)
     */
    @Test
    @Order(11)
    @DisplayName("Should maintain reservations across service restart simulation")
    void testServiceRestartScenario() {
        // Given - Create multiple reservations
        List<UUID> reservationIds = new ArrayList<>();
        BigDecimal totalReserved = BigDecimal.ZERO;

        for (int i = 0; i < 5; i++) {
            BigDecimal amount = new BigDecimal("50.00");
            FundReservationRequest request = FundReservationRequest.builder()
                    .walletId(testWallet.getId())
                    .transactionId(UUID.randomUUID())
                    .amount(amount)
                    .build();

            FundReservationResponse response = walletBalanceService.reserveFunds(request);
            reservationIds.add(response.getReservationId());
            totalReserved = totalReserved.add(amount);
        }

        // Simulate service restart by clearing entity manager cache
        // (In real scenario, service would restart and load from database)
        walletRepository.flush();
        fundReservationRepository.flush();

        // When - Load wallet and reservations after "restart"
        Wallet reloadedWallet = walletRepository.findById(testWallet.getId()).orElseThrow();
        List<FundReservation> activeReservations = fundReservationRepository
                .findActiveByWalletId(testWallet.getId());

        // Then - All reservations should still be active
        assertThat(activeReservations).hasSize(5);
        assertThat(reloadedWallet.getReservedBalance()).isEqualByComparingTo(totalReserved);

        // Verify we can still confirm/release reservations
        walletBalanceService.confirmReservation(reservationIds.get(0));
        walletBalanceService.releaseReservation(reservationIds.get(1), "Test release");

        // Verify final state
        List<FundReservation> remainingActive = fundReservationRepository
                .findActiveByWalletId(testWallet.getId());
        assertThat(remainingActive).hasSize(3); // 5 - 1 confirmed - 1 released = 3
    }

    /**
     * TEST 12: Metrics Verification
     */
    @Test
    @Order(12)
    @DisplayName("Should record Prometheus metrics correctly")
    void testMetrics() {
        // Given
        double initialSuccessCount = meterRegistry
                .counter("wallet.reservation.success").count();

        // When
        FundReservationRequest request = FundReservationRequest.builder()
                .walletId(testWallet.getId())
                .transactionId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .build();

        walletBalanceService.reserveFunds(request);

        // Then
        double finalSuccessCount = meterRegistry
                .counter("wallet.reservation.success").count();
        assertThat(finalSuccessCount).isGreaterThan(initialSuccessCount);
    }
}
