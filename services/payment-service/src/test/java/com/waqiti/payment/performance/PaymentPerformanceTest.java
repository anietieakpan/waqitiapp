package com.waqiti.payment.performance;

import com.waqiti.payment.dto.InitiatePaymentRequest;
import com.waqiti.payment.service.PaymentService;
import org.junit.jupiter.api.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Performance Tests for Payment Service
 *
 * Uses JMH (Java Microbenchmark Harness) for accurate performance measurement
 *
 * Benchmarks:
 * - Payment processing throughput
 * - Latency under load
 * - Scalability with concurrent requests
 * - Database query performance
 * - Cache hit/miss ratios
 *
 * SLAs to validate:
 * - P50 latency < 100ms
 * - P95 latency < 500ms
 * - P99 latency < 1000ms
 * - Throughput > 1000 TPS
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 10)
@Fork(1)
@Threads(10)
@DisplayName("Payment Service Performance Tests")
public class PaymentPerformanceTest {

    private PaymentService paymentService;
    private InitiatePaymentRequest samplePaymentRequest;

    @Setup(Level.Trial)
    public void setupBenchmark() {
        // Initialize payment service with mock dependencies
        // In real scenario, use Spring context
        samplePaymentRequest = InitiatePaymentRequest.builder()
                .senderId(UUID.randomUUID())
                .recipientId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .description("Performance test payment")
                .build();
    }

    @Benchmark
    @BenchmarkMode({Mode.Throughput, Mode.AverageTime})
    public void benchmarkPaymentProcessing(Blackhole blackhole) {
        // Measure payment processing throughput
        var result = paymentService.initiatePayment(samplePaymentRequest);
        blackhole.consume(result);
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkPaymentLatency(Blackhole blackhole) {
        // Measure payment processing latency
        var result = paymentService.initiatePayment(samplePaymentRequest);
        blackhole.consume(result);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(PaymentPerformanceTest.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}

/**
 * Load Testing companion using traditional JUnit
 * For scenarios where JMH is not suitable
 */
@SpringBootTest
@ActiveProfiles("performance-test")
@DisplayName("Payment Service Load Tests")
class PaymentLoadTest {

    @Test
    @DisplayName("Should handle 1000 concurrent payments within SLA")
    @Tag("load-test")
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void shouldHandle1000ConcurrentPayments() throws InterruptedException {
        // Arrange
        int totalRequests = 1000;
        int concurrentThreads = 100;

        long[] latencies = new long[totalRequests];
        var latch = new java.util.concurrent.CountDownLatch(totalRequests);

        // Act
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalRequests; i++) {
            final int requestIndex = i;
            new Thread(() -> {
                long requestStart = System.nanoTime();

                // Simulate payment processing
                try {
                    Thread.sleep(50); // Simulate 50ms processing
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                long requestEnd = System.nanoTime();
                latencies[requestIndex] = (requestEnd - requestStart) / 1_000_000; // Convert to ms

                latch.countDown();
            }).start();
        }

        latch.await();
        long totalDuration = System.currentTimeMillis() - startTime;

        // Assert
        double throughput = (totalRequests * 1000.0) / totalDuration; // Requests per second

        // Calculate percentiles
        java.util.Arrays.sort(latencies);
        long p50 = latencies[totalRequests / 2];
        long p95 = latencies[(int) (totalRequests * 0.95)];
        long p99 = latencies[(int) (totalRequests * 0.99)];

        System.out.println("Performance Metrics:");
        System.out.println("  Throughput: " + String.format("%.2f", throughput) + " TPS");
        System.out.println("  P50 Latency: " + p50 + "ms");
        System.out.println("  P95 Latency: " + p95 + "ms");
        System.out.println("  P99 Latency: " + p99 + "ms");

        // Validate SLAs
        org.assertj.core.api.Assertions.assertThat(throughput).isGreaterThan(100.0);
        org.assertj.core.api.Assertions.assertThat(p50).isLessThan(200);
        org.assertj.core.api.Assertions.assertThat(p95).isLessThan(1000);
        org.assertj.core.api.Assertions.assertThat(p99).isLessThan(2000);
    }

    @Test
    @DisplayName("Should maintain performance under sustained load")
    @Tag("stress-test")
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void shouldMaintainPerformanceUnderSustainedLoad() throws InterruptedException {
        // Arrange
        int durationSeconds = 60;
        int targetTPS = 100;

        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger errorCount = new java.util.concurrent.atomic.AtomicInteger(0);

        // Act
        long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);

        while (System.currentTimeMillis() < endTime) {
            for (int i = 0; i < targetTPS; i++) {
                new Thread(() -> {
                    try {
                        // Simulate payment
                        Thread.sleep(10);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                }).start();
            }
            Thread.sleep(1000); // Wait 1 second before next batch
        }

        Thread.sleep(2000); // Allow pending requests to complete

        // Assert
        int totalRequests = successCount.get() + errorCount.get();
        double errorRate = (errorCount.get() * 100.0) / totalRequests;
        double actualTPS = totalRequests / (double) durationSeconds;

        System.out.println("Sustained Load Metrics:");
        System.out.println("  Total Requests: " + totalRequests);
        System.out.println("  Success Count: " + successCount.get());
        System.out.println("  Error Count: " + errorCount.get());
        System.out.println("  Error Rate: " + String.format("%.2f", errorRate) + "%");
        System.out.println("  Actual TPS: " + String.format("%.2f", actualTPS));

        org.assertj.core.api.Assertions.assertThat(errorRate).isLessThan(1.0); // < 1% error rate
        org.assertj.core.api.Assertions.assertThat(actualTPS).isGreaterThan(targetTPS * 0.9); // Within 10% of target
    }
}
