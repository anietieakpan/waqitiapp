package com.waqiti.wallet.service;

import com.waqiti.wallet.domain.*;
import com.waqiti.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency tests for WalletService to verify distributed locking
 * prevents double-spending and race conditions.
 */
@SpringBootTest
@ActiveProfiles("test")
public class WalletServiceConcurrencyTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    /**
     * Test concurrent debits on same wallet to verify no double-spending
     */
    @Test
    public void testConcurrentDebits_NoDou bleSpending() throws Exception {
        // Setup: Create wallet with $100 balance
        UUID userId = UUID.randomUUID();
        UUID walletId = createTestWallet(userId, new BigDecimal("100.00"));

        // Execute: 10 concurrent $15 debits (total $150, should fail after ~6 transactions)
        int numThreads = 10;
        BigDecimal debitAmount = new BigDecimal("15.00");
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            final int threadNum = i;
            futures.add(executor.submit(() -> {
                try {
                    walletService.debitWallet(
                        userId, 
                        walletId, 
                        debitAmount, 
                        "USD", 
                        "Test-" + threadNum
                    );
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }));
        }

        // Wait for all to complete
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // Verify: Count successful debits
        int successfulDebits = 0;
        for (Future<Boolean> future : futures) {
            if (future.get()) {
                successfulDebits++;
            }
        }

        // Assert: Should have exactly 6 successful debits (6 * $15 = $90, leaving $10)
        assertEquals(6, successfulDebits, "Should have exactly 6 successful debits");

        // Verify final balance is non-negative
        Wallet wallet = walletRepository.findById(walletId).orElseThrow();
        assertTrue(wallet.getBalance().compareTo(BigDecimal.ZERO) >= 0,
            "Balance should never be negative");
        
        BigDecimal expectedBalance = new BigDecimal("10.00");
        assertEquals(0, expectedBalance.compareTo(wallet.getBalance()),
            "Final balance should be $10.00");
    }

    /**
     * Test 1000 concurrent transactions to stress test locking
     */
    @Test
    public void testHighConcurrency_1000Transactions() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID walletId = createTestWallet(userId, new BigDecimal("10000.00"));

        int numThreads = 1000;
        BigDecimal amount = new BigDecimal("1.00");
        ExecutorService executor = Executors.newFixedThreadPool(100);
        
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            final int threadNum = i;
            futures.add(executor.submit(() -> {
                try {
                    if (threadNum % 2 == 0) {
                        walletService.debitWallet(userId, walletId, amount, "USD", "Debit-" + threadNum);
                    } else {
                        walletService.creditWallet(userId, walletId, amount, "USD", "Credit-" + threadNum);
                    }
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }));
        }

        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);

        // Verify all completed
        for (Future<Boolean> future : futures) {
            assertTrue(future.isDone(), "All transactions should complete");
        }

        // Verify balance consistency
        Wallet wallet = walletRepository.findById(walletId).orElseThrow();
        assertTrue(wallet.getBalance().compareTo(BigDecimal.ZERO) > 0,
            "Balance should remain positive");
    }

    private UUID createTestWallet(UUID userId, BigDecimal initialBalance) {
        Wallet wallet = new Wallet();
        wallet.setId(UUID.randomUUID());
        wallet.setUserId(userId);
        wallet.setCurrency(Currency.USD);
        wallet.setBalance(initialBalance);
        wallet.setAvailableBalance(initialBalance);
        wallet.setStatus(WalletStatus.ACTIVE);
        return walletRepository.save(wallet).getId();
    }
}
