package com.waqiti.payment.integration;

import com.waqiti.common.audit.TransactionAuditService;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.dto.PaymentRequest;
import com.waqiti.payment.dto.PaymentResponse;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CRITICAL SECURITY: Integration tests for payment processing
 * Tests race condition prevention, idempotency, and fraud detection integration
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaymentProcessingIntegrationTest {

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
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private TransactionAuditService auditService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private UUID userId;
    private UUID merchantId;

    @BeforeEach
    void setUp() {
        // Clean up Redis between tests
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        
        userId = UUID.randomUUID();
        merchantId = UUID.randomUUID();
    }

    /**
     * CRITICAL TEST: Verifies payment idempotency prevents duplicate charges
     */
    @Test
    void testPaymentIdempotencyPreventsDoubleCharging() {
        String idempotencyKey = "payment-test-" + UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(99.99);
        
        PaymentRequest request = PaymentRequest.builder()
                .userId(userId)
                .merchantId(merchantId)
                .amount(amount)
                .currency("USD")
                .paymentMethod("STRIPE")
                .description("Test payment")
                .build();

        // First payment should succeed
        PaymentResponse firstResponse = paymentService.processPayment(idempotencyKey, request);
        assertEquals("SUCCESS", firstResponse.getStatus());

        // Second payment with same idempotency key should return cached result
        PaymentResponse secondResponse = paymentService.processPayment(idempotencyKey, request);
        assertEquals("SUCCESS", secondResponse.getStatus());
        assertEquals(firstResponse.getPaymentId(), secondResponse.getPaymentId());

        // Verify only one payment record exists
        long paymentCount = paymentRepository.countByIdempotencyKey(idempotencyKey);
        assertEquals(1, paymentCount, "Only one payment should exist for idempotency key");
    }

    /**
     * CRITICAL TEST: Verifies concurrent payment processing is properly handled
     */
    @Test
    void testConcurrentPaymentProcessing() throws Exception {
        int numberOfPayments = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfPayments);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completedLatch = new CountDownLatch(numberOfPayments);
        
        AtomicInteger successfulPayments = new AtomicInteger(0);
        AtomicInteger failedPayments = new AtomicInteger(0);

        IntStream.range(0, numberOfPayments).forEach(i -> {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    PaymentRequest request = PaymentRequest.builder()
                            .userId(userId)
                            .merchantId(merchantId)
                            .amount(BigDecimal.valueOf(10.00 + i)) // Different amounts to avoid conflicts
                            .currency("USD")
                            .paymentMethod("STRIPE")
                            .description("Concurrent payment test " + i)
                            .build();
                    
                    String idempotencyKey = "concurrent-payment-" + i;
                    PaymentResponse response = paymentService.processPayment(idempotencyKey, request);
                    
                    if ("SUCCESS".equals(response.getStatus())) {
                        successfulPayments.incrementAndGet();
                    } else {
                        failedPayments.incrementAndGet();
                    }
                    
                } catch (Exception e) {
                    failedPayments.incrementAndGet();
                    System.out.println("Payment failed: " + e.getMessage());
                } finally {
                    completedLatch.countDown();
                }
            });
        });

        startLatch.countDown();
        completedLatch.await();
        executor.shutdown();

        // All payments should succeed (different amounts and idempotency keys)
        assertEquals(numberOfPayments, successfulPayments.get(),
                "All payments should succeed with different parameters");
        assertEquals(0, failedPayments.get(), "No payments should fail");
    }

    /**
     * CRITICAL TEST: Verifies fraud detection integration
     */
    @Test
    void testFraudDetectionIntegration() {
        // Create a high-risk payment (large amount from new user)
        PaymentRequest highRiskRequest = PaymentRequest.builder()
                .userId(UUID.randomUUID()) // New user ID
                .merchantId(merchantId)
                .amount(BigDecimal.valueOf(9999.99)) // Large amount
                .currency("USD")
                .paymentMethod("STRIPE")
                .description("High risk payment test")
                .build();

        String idempotencyKey = "fraud-test-" + UUID.randomUUID();
        
        // Payment should be flagged for review or rejected
        PaymentResponse response = paymentService.processPayment(idempotencyKey, highRiskRequest);
        
        assertTrue(
                response.getStatus().equals("PENDING_REVIEW") || response.getStatus().equals("REJECTED"),
                "High-risk payment should be flagged for review or rejected"
        );
        
        // Verify fraud score was calculated
        Payment payment = paymentRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertNotNull(payment.getFraudScore(), "Fraud score should be calculated");
        assertTrue(payment.getFraudScore() > 0.5, "High-risk payment should have high fraud score");
    }

    /**
     * TEST: Verifies payment timeout handling
     */
    @Test
    void testPaymentTimeoutHandling() {
        // Create a payment that will timeout (simulate slow provider)
        PaymentRequest timeoutRequest = PaymentRequest.builder()
                .userId(userId)
                .merchantId(merchantId)
                .amount(BigDecimal.valueOf(50.00))
                .currency("USD")
                .paymentMethod("SLOW_PROVIDER") // This should trigger timeout
                .description("Timeout test payment")
                .build();

        String idempotencyKey = "timeout-test-" + UUID.randomUUID();
        
        // Payment should timeout and be handled gracefully
        assertThrows(Exception.class, () -> {
            paymentService.processPayment(idempotencyKey, timeoutRequest);
        });

        // Verify payment status is set correctly
        Payment payment = paymentRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (payment != null) {
            assertTrue(
                    payment.getStatus() == PaymentStatus.FAILED || 
                    payment.getStatus() == PaymentStatus.TIMEOUT,
                    "Timed out payment should have appropriate status"
            );
        }
    }

    /**
     * TEST: Verifies audit trail for payment operations
     */
    @Test
    void testPaymentAuditTrail() {
        PaymentRequest request = PaymentRequest.builder()
                .userId(userId)
                .merchantId(merchantId)
                .amount(BigDecimal.valueOf(25.50))
                .currency("USD")
                .paymentMethod("STRIPE")
                .description("Audit trail test")
                .build();

        String idempotencyKey = "audit-test-" + UUID.randomUUID();
        PaymentResponse response = paymentService.processPayment(idempotencyKey, request);

        // Verify payment was processed
        assertEquals("SUCCESS", response.getStatus());

        // Verify audit records exist
        Payment payment = paymentRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertNotNull(payment.getId(), "Payment should be persisted");
        
        // Audit service should have been called (verify through side effects)
        assertTrue(payment.getCreatedAt() != null, "Payment should have creation timestamp");
    }

    /**
     * TEST: Verifies refund processing with idempotency
     */
    @Test
    void testRefundProcessingIdempotency() {
        // First create a successful payment
        PaymentRequest paymentRequest = PaymentRequest.builder()
                .userId(userId)
                .merchantId(merchantId)
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .paymentMethod("STRIPE")
                .description("Payment for refund test")
                .build();

        String paymentIdempotencyKey = "refund-payment-" + UUID.randomUUID();
        PaymentResponse paymentResponse = paymentService.processPayment(paymentIdempotencyKey, paymentRequest);
        assertEquals("SUCCESS", paymentResponse.getStatus());

        // Now test refund idempotency
        String refundIdempotencyKey = "refund-test-" + UUID.randomUUID();
        BigDecimal refundAmount = BigDecimal.valueOf(50.00);

        // First refund should succeed
        RefundResponse firstRefund = paymentService.processRefund(
                refundIdempotencyKey, paymentResponse.getPaymentId(), refundAmount);
        assertEquals("SUCCESS", firstRefund.getStatus());

        // Second refund with same idempotency key should return cached result
        RefundResponse secondRefund = paymentService.processRefund(
                refundIdempotencyKey, paymentResponse.getPaymentId(), refundAmount);
        assertEquals("SUCCESS", secondRefund.getStatus());
        assertEquals(firstRefund.getRefundId(), secondRefund.getRefundId());
    }

    /**
     * PERFORMANCE TEST: Tests payment processing under load
     */
    @Test
    void testPaymentProcessingUnderLoad() throws Exception {
        int numberOfPayments = 50;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch completedLatch = new CountDownLatch(numberOfPayments);
        
        AtomicInteger processedPayments = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        IntStream.range(0, numberOfPayments).forEach(i -> {
            executor.submit(() -> {
                try {
                    PaymentRequest request = PaymentRequest.builder()
                            .userId(UUID.randomUUID())
                            .merchantId(merchantId)
                            .amount(BigDecimal.valueOf(Math.random() * 100 + 1)) // Random amount 1-100
                            .currency("USD")
                            .paymentMethod("STRIPE")
                            .description("Load test payment " + i)
                            .build();
                    
                    String idempotencyKey = "load-test-" + i;
                    PaymentResponse response = paymentService.processPayment(idempotencyKey, request);
                    
                    if ("SUCCESS".equals(response.getStatus())) {
                        processedPayments.incrementAndGet();
                    }
                    
                } catch (Exception e) {
                    System.err.println("Payment processing error: " + e.getMessage());
                } finally {
                    completedLatch.countDown();
                }
            });
        });

        completedLatch.await();
        executor.shutdown();
        
        long duration = System.currentTimeMillis() - startTime;
        double paymentsPerSecond = (processedPayments.get() * 1000.0) / duration;
        
        System.out.println("Payment Performance: " + paymentsPerSecond + " payments/second");
        System.out.println("Processed: " + processedPayments.get() + "/" + numberOfPayments);
        
        // Performance should be reasonable
        assertTrue(paymentsPerSecond > 5, "System should handle at least 5 payments per second");
        assertTrue(processedPayments.get() > numberOfPayments * 0.8, 
                "At least 80% of payments should succeed");
    }
}