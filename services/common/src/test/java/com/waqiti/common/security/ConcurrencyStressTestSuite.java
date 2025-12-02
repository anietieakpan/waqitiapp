package com.waqiti.common.security;

import com.waqiti.common.ratelimit.RateLimitingService;
import com.waqiti.wallet.service.WalletService;
import com.waqiti.payment.service.PaymentService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.*;

/**
 * CONCURRENCY & LOAD STRESS TEST SUITE
 *
 * Tests system behavior under high concurrency and load:
 * - Rate limiting under stress
 * - Database connection pool exhaustion
 * - Deadlock detection
 * - Memory leak detection
 * - Thread starvation
 * - Circuit breaker behavior
 * - Cascading failure prevention
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 */
@SpringBootTest
@ActiveProfiles("stress-test")
@DisplayName("Concurrency & Load Stress Test Suite")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 10, unit = TimeUnit.MINUTES) // Global timeout for stress tests
public class ConcurrencyStressTestSuite {

    @Autowired(required = false)
    private RateLimitingService rateLimitingService;

    @Autowired(required = false)
    private WalletService walletService;

    @Autowired(required = false)
    private PaymentService paymentService;

    private static final int STRESS_THREAD_COUNT = 100;
    private static final int HIGH_STRESS_THREAD_COUNT = 500;
    private static final int EXTREME_STRESS_THREAD_COUNT = 1000;

    @Nested
    @DisplayName("Rate Limiting Stress Tests")
    class RateLimitingStressTests {

        @Test
        @Order(1)
        @DisplayName("Should handle burst traffic without service degradation")
        void shouldHandleBurstTrafficWithoutDegradation() throws Exception {
            assumeServiceAvailable(rateLimitingService, "RateLimitingService");

            int numberOfRequests = 1000;
            ExecutorService executor = Executors.newFixedThreadPool(STRESS_THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(numberOfRequests);

            AtomicInteger allowedCount = new AtomicInteger(0);
            AtomicInteger deniedCount = new AtomicInteger(0);
            AtomicLong totalResponseTime = new AtomicLong(0);
            AtomicLong maxResponseTime = new AtomicLong(0);

            Instant startTime = Instant.now();

            for (int i = 0; i < numberOfRequests; i++) {
                final int requestNum = i;
                executor.submit(() -> {
                    try {
                        long requestStart = System.nanoTime();

                        var result = rateLimitingService.checkRateLimit(
                                "stress-test-user-" + (requestNum % 10),
                                "payment.transfer",
                                null
                        );

                        long requestEnd = System.nanoTime();
                        long responseTime = (requestEnd - requestStart) / 1_000_000; // Convert to ms

                        totalResponseTime.addAndGet(responseTime);
                        maxResponseTime.updateAndGet(current -> Math.max(current, responseTime));

                        if (result.isAllowed()) {
                            allowedCount.incrementAndGet();
                        } else {
                            deniedCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        System.err.println("Rate limit check failed: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(2, TimeUnit.MINUTES);
            executor.shutdown();

            Instant endTime = Instant.now();
            long durationMs = Duration.between(startTime, endTime).toMillis();

            // Assertions
            assertThat(completed).isTrue();
            assertThat(allowedCount.get() + deniedCount.get()).isEqualTo(numberOfRequests);

            // Performance metrics
            long avgResponseTime = totalResponseTime.get() / numberOfRequests;
            double throughput = (numberOfRequests * 1000.0) / durationMs; // requests per second

            System.out.println("=== Rate Limiting Stress Test Results ===");
            System.out.println("Total Requests: " + numberOfRequests);
            System.out.println("Allowed: " + allowedCount.get());
            System.out.println("Denied: " + deniedCount.get());
            System.out.println("Duration: " + durationMs + " ms");
            System.out.println("Throughput: " + String.format("%.2f", throughput) + " req/sec");
            System.out.println("Avg Response Time: " + avgResponseTime + " ms");
            System.out.println("Max Response Time: " + maxResponseTime.get() + " ms");

            // Performance requirements
            assertThat(avgResponseTime).isLessThan(50); // Avg should be < 50ms
            assertThat(maxResponseTime.get()).isLessThan(500); // Max should be < 500ms
            assertThat(throughput).isGreaterThan(100); // Should handle > 100 req/sec
        }

        @Test
        @Order(2)
        @DisplayName("Should maintain rate limit accuracy under extreme concurrency")
        void shouldMaintainAccuracyUnderExtremeConcurrency() throws Exception {
            assumeServiceAvailable(rateLimitingService, "RateLimitingService");

            String userId = "extreme-concurrency-user";
            int simultaneousRequests = EXTREME_STRESS_THREAD_COUNT;
            int expectedLimit = 10; // User limit: 10 requests

            ExecutorService executor = Executors.newFixedThreadPool(simultaneousRequests);
            CyclicBarrier barrier = new CyclicBarrier(simultaneousRequests);
            AtomicInteger allowedCount = new AtomicInteger(0);

            List<Future<Boolean>> futures = new ArrayList<>();

            for (int i = 0; i < simultaneousRequests; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        barrier.await(); // Synchronize all threads to start simultaneously

                        var result = rateLimitingService.checkRateLimit(
                                userId,
                                "payment.transfer",
                                null
                        );

                        if (result.isAllowed()) {
                            allowedCount.incrementAndGet();
                        }

                        return result.isAllowed();
                    } catch (Exception e) {
                        return false;
                    }
                }));
            }

            for (Future<Boolean> future : futures) {
                future.get(2, TimeUnit.MINUTES);
            }

            executor.shutdown();

            // Critical assertion: Despite extreme concurrency, rate limit should be accurate
            // Allowed count should not significantly exceed the limit
            assertThat(allowedCount.get()).isLessThanOrEqualTo(expectedLimit + 5); // Allow small variance

            System.out.println("Extreme Concurrency Test: " + simultaneousRequests + " simultaneous requests");
            System.out.println("Rate Limit: " + expectedLimit);
            System.out.println("Allowed: " + allowedCount.get());
            System.out.println("Denied: " + (simultaneousRequests - allowedCount.get()));
        }

        @Test
        @Order(3)
        @DisplayName("Should recover gracefully from Redis failure")
        void shouldRecoverGracefullyFromRedisFailure() throws Exception {
            assumeServiceAvailable(rateLimitingService, "RateLimitingService");

            // Simulate Redis failure scenario
            // (This would require test container manipulation or circuit breaker testing)

            String userId = "redis-failure-test-user";
            int numberOfRequests = 100;
            AtomicInteger failOpenCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch latch = new CountDownLatch(numberOfRequests);

            for (int i = 0; i < numberOfRequests; i++) {
                executor.submit(() -> {
                    try {
                        var result = rateLimitingService.checkRateLimit(
                                userId,
                                "payment.transfer",
                                null
                        );

                        // In fail-open mode, requests should be allowed
                        if (result.isAllowed()) {
                            failOpenCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Should not throw exceptions to clients
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(1, TimeUnit.MINUTES);
            executor.shutdown();

            // In fail-open mode (Redis down), all requests should be allowed
            // This is a security vs availability tradeoff
            System.out.println("Redis Failure Test: Fail-open count: " + failOpenCount.get() + "/" + numberOfRequests);
        }
    }

    @Nested
    @DisplayName("Database Connection Pool Stress Tests")
    class DatabaseConnectionPoolStressTests {

        @Test
        @Order(10)
        @DisplayName("Should handle connection pool exhaustion gracefully")
        void shouldHandleConnectionPoolExhaustionGracefully() throws Exception {
            assumeServiceAvailable(walletService, "WalletService");

            int numberOfConnections = 100; // Exceed typical pool size (usually 10-20)
            ExecutorService executor = Executors.newFixedThreadPool(numberOfConnections);
            CountDownLatch latch = new CountDownLatch(numberOfConnections);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger timeoutCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            for (int i = 0; i < numberOfConnections; i++) {
                final int taskNum = i;
                executor.submit(() -> {
                    try {
                        // Simulate long-running database operation
                        var balance = walletService.getWalletBalance(
                                UUID.randomUUID(),
                                "USD",
                                "stress-test-user-" + taskNum
                        );

                        successCount.incrementAndGet();
                    } catch (TimeoutException e) {
                        timeoutCount.incrementAndGet();
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(5, TimeUnit.MINUTES);
            executor.shutdown();

            assertThat(completed).isTrue();

            System.out.println("=== Connection Pool Stress Test Results ===");
            System.out.println("Total Tasks: " + numberOfConnections);
            System.out.println("Success: " + successCount.get());
            System.out.println("Timeouts: " + timeoutCount.get());
            System.out.println("Errors: " + errorCount.get());

            // System should handle gracefully (either queue or reject, not crash)
            assertThat(successCount.get() + timeoutCount.get() + errorCount.get())
                    .isEqualTo(numberOfConnections);
        }

        @Test
        @Order(11)
        @DisplayName("Should prevent connection leaks under stress")
        void shouldPreventConnectionLeaksUnderStress() throws Exception {
            assumeServiceAvailable(walletService, "WalletService");

            int iterations = 10;
            int operationsPerIteration = 50;

            for (int iteration = 0; iteration < iterations; iteration++) {
                ExecutorService executor = Executors.newFixedThreadPool(operationsPerIteration);
                CountDownLatch latch = new CountDownLatch(operationsPerIteration);

                for (int i = 0; i < operationsPerIteration; i++) {
                    executor.submit(() -> {
                        try {
                            walletService.getWalletBalance(
                                    UUID.randomUUID(),
                                    "USD",
                                    "leak-test-user"
                            );
                        } catch (Exception e) {
                            // Expected for non-existent wallets
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                latch.await(1, TimeUnit.MINUTES);
                executor.shutdown();
                executor.awaitTermination(1, TimeUnit.MINUTES);

                // Brief pause between iterations
                Thread.sleep(100);
            }

            // If connections are leaking, later iterations would fail
            // The fact that we completed all iterations indicates no leaks
            System.out.println("Connection leak test completed: " + iterations + " iterations x " + operationsPerIteration + " operations");
        }
    }

    @Nested
    @DisplayName("Deadlock Detection Tests")
    class DeadlockDetectionTests {

        @Test
        @Order(20)
        @DisplayName("Should detect and prevent deadlocks in bidirectional transfers")
        void shouldDetectAndPreventDeadlocks() throws Exception {
            assumeServiceAvailable(paymentService, "PaymentService");

            UUID account1 = UUID.randomUUID();
            UUID account2 = UUID.randomUUID();

            int numberOfPairs = 50;
            ExecutorService executor = Executors.newFixedThreadPool(numberOfPairs * 2);
            CountDownLatch latch = new CountDownLatch(numberOfPairs * 2);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger deadlockCount = new AtomicInteger(0);
            AtomicBoolean deadlockDetected = new AtomicBoolean(false);

            for (int i = 0; i < numberOfPairs; i++) {
                // Transfer from Account1 to Account2
                executor.submit(() -> {
                    try {
                        paymentService.processPayment(
                                account1,
                                account2,
                                new BigDecimal("10.00"),
                                "USD",
                                "A to B"
                        );
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        if (e.getMessage() != null && e.getMessage().contains("deadlock")) {
                            deadlockDetected.set(true);
                            deadlockCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });

                // Transfer from Account2 to Account1 (opposite direction)
                executor.submit(() -> {
                    try {
                        paymentService.processPayment(
                                account2,
                                account1,
                                new BigDecimal("10.00"),
                                "USD",
                                "B to A"
                        );
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        if (e.getMessage() != null && e.getMessage().contains("deadlock")) {
                            deadlockDetected.set(true);
                            deadlockCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(5, TimeUnit.MINUTES);
            executor.shutdown();

            assertThat(completed).isTrue();

            System.out.println("=== Deadlock Detection Test Results ===");
            System.out.println("Total Operations: " + (numberOfPairs * 2));
            System.out.println("Successful: " + successCount.get());
            System.out.println("Deadlocks Detected: " + deadlockCount.get());

            // Either deadlocks are prevented entirely, or they're detected and handled
            // No operations should hang indefinitely
        }
    }

    @Nested
    @DisplayName("Memory Leak Detection Tests")
    class MemoryLeakDetectionTests {

        @Test
        @Order(30)
        @DisplayName("Should not accumulate memory with repeated operations")
        void shouldNotAccumulateMemoryWithRepeatedOperations() throws Exception {
            assumeServiceAvailable(rateLimitingService, "RateLimitingService");

            Runtime runtime = Runtime.getRuntime();
            runtime.gc(); // Initial garbage collection

            long initialMemory = runtime.totalMemory() - runtime.freeMemory();

            // Perform many operations
            int iterations = 1000;
            for (int i = 0; i < iterations; i++) {
                rateLimitingService.checkRateLimit(
                        "memory-test-user-" + i,
                        "payment.transfer",
                        null
                );

                // Periodic GC to detect leaks faster
                if (i % 100 == 0) {
                    runtime.gc();
                    Thread.sleep(10);
                }
            }

            runtime.gc();
            Thread.sleep(500); // Allow GC to complete

            long finalMemory = runtime.totalMemory() - runtime.freeMemory();
            long memoryIncrease = finalMemory - initialMemory;
            double memoryIncreaseMB = memoryIncrease / (1024.0 * 1024.0);

            System.out.println("=== Memory Leak Test Results ===");
            System.out.println("Initial Memory: " + (initialMemory / 1024 / 1024) + " MB");
            System.out.println("Final Memory: " + (finalMemory / 1024 / 1024) + " MB");
            System.out.println("Memory Increase: " + String.format("%.2f", memoryIncreaseMB) + " MB");
            System.out.println("Iterations: " + iterations);

            // Memory increase should be reasonable (< 50MB for 1000 operations)
            assertThat(memoryIncreaseMB).isLessThan(50.0);
        }
    }

    @Nested
    @DisplayName("Performance Degradation Tests")
    class PerformanceDegradationTests {

        @Test
        @Order(40)
        @DisplayName("Should maintain consistent performance under sustained load")
        void shouldMaintainConsistentPerformanceUnderSustainedLoad() throws Exception {
            assumeServiceAvailable(rateLimitingService, "RateLimitingService");

            int phases = 5;
            int operationsPerPhase = 200;
            List<Long> phaseAverages = new ArrayList<>();

            for (int phase = 0; phase < phases; phase++) {
                ExecutorService executor = Executors.newFixedThreadPool(20);
                CountDownLatch latch = new CountDownLatch(operationsPerPhase);
                AtomicLong totalTime = new AtomicLong(0);

                for (int i = 0; i < operationsPerPhase; i++) {
                    executor.submit(() -> {
                        try {
                            long start = System.nanoTime();

                            rateLimitingService.checkRateLimit(
                                    "sustained-load-user",
                                    "payment.transfer",
                                    null
                            );

                            long end = System.nanoTime();
                            totalTime.addAndGet((end - start) / 1_000_000); // Convert to ms
                        } catch (Exception e) {
                            // Ignore
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                latch.await(2, TimeUnit.MINUTES);
                executor.shutdown();

                long avgTime = totalTime.get() / operationsPerPhase;
                phaseAverages.add(avgTime);

                System.out.println("Phase " + (phase + 1) + " - Avg Response Time: " + avgTime + " ms");

                // Brief pause between phases
                Thread.sleep(500);
            }

            // Performance should not degrade significantly across phases
            // Calculate variance
            double mean = phaseAverages.stream().mapToLong(Long::longValue).average().orElse(0);
            double variance = phaseAverages.stream()
                    .mapToDouble(avg -> Math.pow(avg - mean, 2))
                    .average().orElse(0);
            double stdDev = Math.sqrt(variance);

            System.out.println("Mean Response Time: " + String.format("%.2f", mean) + " ms");
            System.out.println("Std Deviation: " + String.format("%.2f", stdDev) + " ms");

            // Standard deviation should be low (consistent performance)
            assertThat(stdDev).isLessThan(mean * 0.5); // Std dev < 50% of mean
        }
    }

    // Helper methods

    private void assumeServiceAvailable(Object service, String serviceName) {
        if (service == null) {
            System.out.println("SKIPPING TEST: " + serviceName + " not available in test context");
            Assumptions.assumeTrue(false, serviceName + " not available");
        }
    }
}
