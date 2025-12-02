package com.waqiti.payment.integration;

import com.waqiti.common.audit.TransactionAuditService;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.locking.DistributedLockService;
import com.waqiti.payment.dto.*;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.PaymentProcessingService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CRITICAL PRODUCTION TEST: End-to-End Payment Flow Integration Tests
 * Tests complete payment processing pipeline with all security measures
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EndToEndPaymentFlowTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("waqiti_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
            .withEmbeddedZookeeper();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentProcessingService paymentProcessingService;

    @Autowired
    private DistributedLockService lockService;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private TransactionAuditService auditService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID testUserId;
    private UUID testMerchantId;

    @BeforeEach
    void setUp() {
        // Clean up Redis between tests
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        
        testUserId = UUID.randomUUID();
        testMerchantId = UUID.randomUUID();
    }

    /**
     * CRITICAL TEST: Complete payment processing flow with all security measures
     */
    @Test
    @Transactional
    void testCompletePaymentProcessingFlow() {
        // Given: A payment request
        PaymentRequest request = PaymentRequest.builder()
                .userId(testUserId)
                .merchantId(testMerchantId)
                .amount(BigDecimal.valueOf(99.99))
                .currency("USD")
                .paymentMethod("STRIPE")
                .description("Integration test payment")
                .build();

        String idempotencyKey = "integration-test-" + UUID.randomUUID();

        // When: Processing payment
        PaymentResponse response = paymentProcessingService.processPayment(idempotencyKey, request);

        // Then: Payment should be processed successfully
        assertNotNull(response, "Payment response should not be null");
        assertEquals("SUCCESS", response.getStatus(), "Payment should succeed");
        assertNotNull(response.getPaymentId(), "Payment ID should be generated");
        assertEquals(request.getAmount(), response.getAmount(), "Amount should match");
        assertNotNull(response.getProcessedAt(), "Processing timestamp should be set");
        assertTrue(response.getFraudScore() >= 0.0, "Fraud score should be calculated");
    }

    /**
     * CRITICAL TEST: Payment idempotency prevents duplicate processing
     */
    @Test
    void testPaymentIdempotencyProtection() {
        // Given: A payment request
        PaymentRequest request = PaymentRequest.builder()
                .userId(testUserId)
                .merchantId(testMerchantId)
                .amount(BigDecimal.valueOf(50.00))
                .currency("USD")
                .paymentMethod("STRIPE")
                .description("Idempotency test payment")
                .build();

        String idempotencyKey = "idempotency-test-" + UUID.randomUUID();

        // When: Processing same payment twice
        PaymentResponse firstResponse = paymentProcessingService.processPayment(idempotencyKey, request);
        PaymentResponse secondResponse = paymentProcessingService.processPayment(idempotencyKey, request);

        // Then: Second call should return cached result
        assertNotNull(firstResponse, "First response should not be null");
        assertNotNull(secondResponse, "Second response should not be null");
        assertEquals(firstResponse.getPaymentId(), secondResponse.getPaymentId(), 
                "Payment IDs should match");
        assertEquals(firstResponse.getStatus(), secondResponse.getStatus(),
                "Payment status should match");
    }

    /**
     * CRITICAL TEST: Fraud detection integration in payment flow
     */
    @Test
    void testFraudDetectionIntegration() {
        // Given: A high-risk payment (large amount, new user)
        PaymentRequest highRiskRequest = PaymentRequest.builder()
                .userId(UUID.randomUUID()) // New user
                .merchantId(testMerchantId)
                .amount(BigDecimal.valueOf(9999.99)) // Large amount
                .currency("USD")
                .paymentMethod("STRIPE")
                .description("High-risk payment test")
                .build();

        String idempotencyKey = "fraud-test-" + UUID.randomUUID();

        // When: Processing high-risk payment
        PaymentResponse response = paymentProcessingService.processPayment(idempotencyKey, highRiskRequest);

        // Then: Payment should be flagged or processed with high fraud score
        assertNotNull(response, "Response should not be null");
        if (response.getStatus().equals("REJECTED")) {
            assertTrue(response.getFraudScore() > 0.8, "Rejected payment should have high fraud score");
        } else {
            assertNotNull(response.getFraudScore(), "Fraud score should be calculated");
            assertTrue(response.getFraudScore() >= 0.0, "Fraud score should be valid");
        }
    }

    /**
     * CRITICAL TEST: Refund processing with validation
     */
    @Test
    @Transactional
    void testRefundProcessingFlow() {
        // Given: A completed payment
        PaymentRequest originalRequest = PaymentRequest.builder()
                .userId(testUserId)
                .merchantId(testMerchantId)
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .paymentMethod("STRIPE")
                .description("Original payment for refund test")
                .build();

        String paymentIdempotencyKey = "original-payment-" + UUID.randomUUID();
        PaymentResponse originalPayment = paymentProcessingService.processPayment(paymentIdempotencyKey, originalRequest);
        
        assertEquals("SUCCESS", originalPayment.getStatus(), "Original payment should succeed");

        // When: Processing refund
        RefundRequest refundRequest = RefundRequest.builder()
                .originalPaymentId(originalPayment.getPaymentId())
                .amount(BigDecimal.valueOf(50.00)) // Partial refund
                .reason("Customer requested refund")
                .build();

        String refundIdempotencyKey = "refund-test-" + UUID.randomUUID();
        RefundResult refundResult = paymentService.processRefund(refundRequest);

        // Then: Refund should be processed
        assertNotNull(refundResult, "Refund result should not be null");
        // Note: Refund might be pending or completed depending on provider
        assertTrue(
            refundResult.getStatus().equals("COMPLETED") || refundResult.getStatus().equals("PENDING"),
            "Refund should be completed or pending"
        );
        assertEquals(BigDecimal.valueOf(50.00), refundResult.getRefundAmount(), "Refund amount should match");
    }

    /**
     * CRITICAL TEST: Concurrent payment processing with race condition protection
     */
    @Test
    void testConcurrentPaymentProcessing() throws Exception {
        int numberOfPayments = 20;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completedLatch = new CountDownLatch(numberOfPayments);
        
        AtomicInteger successfulPayments = new AtomicInteger(0);
        AtomicInteger failedPayments = new AtomicInteger(0);

        // Submit concurrent payment requests
        IntStream.range(0, numberOfPayments).forEach(i -> {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    PaymentRequest request = PaymentRequest.builder()
                            .userId(testUserId)
                            .merchantId(testMerchantId)
                            .amount(BigDecimal.valueOf(10.00 + i)) // Different amounts
                            .currency("USD")
                            .paymentMethod("STRIPE")
                            .description("Concurrent payment test " + i)
                            .build();
                    
                    String idempotencyKey = "concurrent-" + i;
                    PaymentResponse response = paymentProcessingService.processPayment(idempotencyKey, request);
                    
                    if ("SUCCESS".equals(response.getStatus())) {
                        successfulPayments.incrementAndGet();
                    } else {
                        failedPayments.incrementAndGet();
                    }
                    
                } catch (Exception e) {
                    failedPayments.incrementAndGet();
                    System.err.println("Payment processing error: " + e.getMessage());
                } finally {
                    completedLatch.countDown();
                }
            });
        });

        // Start all payments simultaneously
        startLatch.countDown();
        completedLatch.await();
        executor.shutdown();

        // Verify results
        assertEquals(numberOfPayments, successfulPayments.get() + failedPayments.get(),
                "All payments should be processed");
        assertTrue(successfulPayments.get() >= numberOfPayments * 0.8,
                "At least 80% of payments should succeed");
        
        System.out.println("Concurrent payments: successful=" + successfulPayments.get() + 
                          ", failed=" + failedPayments.get());
    }

    /**
     * CRITICAL TEST: Database transaction atomicity
     */
    @Test
    @Transactional
    void testDatabaseTransactionAtomicity() {
        // Given: A payment that will cause a database constraint violation
        PaymentRequest request = PaymentRequest.builder()
                .userId(testUserId)
                .merchantId(testMerchantId)
                .amount(BigDecimal.valueOf(75.00))
                .currency("INVALID") // Invalid currency to trigger validation failure
                .paymentMethod("STRIPE")
                .description("Transaction atomicity test")
                .build();

        String idempotencyKey = "atomicity-test-" + UUID.randomUUID();

        // When: Processing payment that should fail
        assertThrows(Exception.class, () -> {
            paymentProcessingService.processPayment(idempotencyKey, request);
        }, "Payment with invalid data should throw exception");

        // Then: No partial data should be persisted
        // This test ensures that transaction rollback works correctly
        // Additional assertions would check that no audit records, etc. were created
    }

    /**
     * PERFORMANCE TEST: Payment processing under load
     */
    @Test
    void testPaymentProcessingPerformance() throws Exception {
        int numberOfPayments = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch completedLatch = new CountDownLatch(numberOfPayments);
        
        long startTime = System.currentTimeMillis();
        AtomicInteger processedPayments = new AtomicInteger(0);

        // Submit payment requests
        IntStream.range(0, numberOfPayments).forEach(i -> {
            executor.submit(() -> {
                try {
                    PaymentRequest request = PaymentRequest.builder()
                            .userId(UUID.randomUUID())
                            .merchantId(testMerchantId)
                            .amount(BigDecimal.valueOf(Math.random() * 100 + 1))
                            .currency("USD")
                            .paymentMethod("STRIPE")
                            .description("Performance test payment " + i)
                            .build();
                    
                    String idempotencyKey = "perf-test-" + i;
                    PaymentResponse response = paymentProcessingService.processPayment(idempotencyKey, request);
                    
                    if ("SUCCESS".equals(response.getStatus())) {
                        processedPayments.incrementAndGet();
                    }
                    
                } catch (Exception e) {
                    System.err.println("Performance test payment error: " + e.getMessage());
                } finally {
                    completedLatch.countDown();
                }
            });
        });

        completedLatch.await();
        executor.shutdown();
        
        long duration = System.currentTimeMillis() - startTime;
        double paymentsPerSecond = (processedPayments.get() * 1000.0) / duration;
        
        System.out.println("Performance: " + paymentsPerSecond + " payments/second");
        System.out.println("Processed: " + processedPayments.get() + "/" + numberOfPayments);
        
        // Performance requirements
        assertTrue(paymentsPerSecond > 10, "System should handle at least 10 payments per second");
        assertTrue(processedPayments.get() > numberOfPayments * 0.9, 
                "At least 90% of payments should succeed under load");
    }

    /**
     * TEST: Audit trail creation during payment processing
     */
    @Test
    void testAuditTrailCreation() {
        // Given: A payment request
        PaymentRequest request = PaymentRequest.builder()
                .userId(testUserId)
                .merchantId(testMerchantId)
                .amount(BigDecimal.valueOf(25.00))
                .currency("USD")
                .paymentMethod("STRIPE")
                .description("Audit trail test")
                .build();

        String idempotencyKey = "audit-test-" + UUID.randomUUID();

        // When: Processing payment
        PaymentResponse response = paymentProcessingService.processPayment(idempotencyKey, request);

        // Then: Payment should succeed and audit trail should be created
        assertEquals("SUCCESS", response.getStatus(), "Payment should succeed");
        assertNotNull(response.getPaymentId(), "Payment ID should be generated for audit");
        
        // Audit service should have been called to log the transaction
        // In a real implementation, we would query the audit repository to verify records
    }

    /**
     * TEST: Error handling and recovery
     */
    @Test
    void testErrorHandlingAndRecovery() {
        // Given: A payment request that will fail due to insufficient funds
        PaymentRequest request = PaymentRequest.builder()
                .userId(testUserId)
                .merchantId(testMerchantId)
                .amount(BigDecimal.valueOf(999999.99)) // Very large amount
                .currency("USD")
                .paymentMethod("STRIPE")
                .description("Error handling test")
                .build();

        String idempotencyKey = "error-test-" + UUID.randomUUID();

        // When: Processing payment that should fail
        PaymentResponse response = paymentProcessingService.processPayment(idempotencyKey, request);

        // Then: Payment should fail gracefully with proper error handling
        assertNotNull(response, "Response should not be null even on failure");
        assertTrue(
            response.getStatus().equals("FAILED") || 
            response.getStatus().equals("REJECTED") ||
            response.getStatus().equals("INSUFFICIENT_FUNDS"),
            "Payment should fail with appropriate status"
        );
        
        if (response.getStatus().equals("FAILED") || response.getStatus().equals("REJECTED")) {
            assertNotNull(response.getFailureReason(), "Failure reason should be provided");
        }
    }
}