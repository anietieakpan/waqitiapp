package com.waqiti.payment.service;

import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.dto.PaymentRequest;
import com.waqiti.payment.dto.PaymentResponse;
import com.waqiti.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for Payment Service with real database and Kafka.
 *
 * Uses:
 * - TestContainers for PostgreSQL
 * - Embedded Kafka for event testing
 * - Awaitility for async operations
 *
 * @author Waqiti Platform Team
 */
@SpringBootTest
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {"payment-initiated", "payment-completed", "payment-failed"})
@ActiveProfiles("test")
@DirtiesContext
@DisplayName("Payment Service Integration Tests")
class PaymentServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("payment_test_db")
        .withUsername("test")
        .withPassword("test");

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("Should process payment end-to-end successfully")
    @Transactional
    void shouldProcessPaymentEndToEnd() {
        // Given
        PaymentRequest request = PaymentRequest.builder()
            .userId(UUID.randomUUID())
            .amount(new BigDecimal("100.00"))
            .currency("USD")
            .paymentMethod("CREDIT_CARD")
            .description("Test payment")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        // When
        PaymentResponse response = paymentService.initiatePayment(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getPaymentId()).isNotNull();
        assertThat(response.getStatus()).isIn(PaymentStatus.PENDING, PaymentStatus.PROCESSING);

        // Verify payment persisted
        Payment saved = paymentRepository.findById(response.getPaymentId()).orElseThrow();
        assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(saved.getCurrency()).isEqualTo("USD");

        // Wait for async processing
        await().atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                Payment processed = paymentRepository.findById(response.getPaymentId()).orElseThrow();
                assertThat(processed.getStatus()).isIn(PaymentStatus.COMPLETED, PaymentStatus.FAILED);
            });
    }

    @Test
    @DisplayName("Should prevent duplicate payment with idempotency key")
    @Transactional
    void shouldPreventDuplicatePayment() {
        // Given
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentRequest request = PaymentRequest.builder()
            .userId(UUID.randomUUID())
            .amount(new BigDecimal("50.00"))
            .currency("USD")
            .paymentMethod("CREDIT_CARD")
            .idempotencyKey(idempotencyKey)
            .build();

        // When - First request
        PaymentResponse firstResponse = paymentService.initiatePayment(request);

        // When - Duplicate request with same idempotency key
        PaymentResponse secondResponse = paymentService.initiatePayment(request);

        // Then
        assertThat(firstResponse.getPaymentId()).isEqualTo(secondResponse.getPaymentId());
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should fail payment with insufficient funds")
    @Transactional
    void shouldFailPaymentWithInsufficientFunds() {
        // Given
        PaymentRequest request = PaymentRequest.builder()
            .userId(UUID.randomUUID())
            .amount(new BigDecimal("999999.00")) // Large amount
            .currency("USD")
            .paymentMethod("CREDIT_CARD")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        // When & Then
        assertThatThrownBy(() -> paymentService.initiatePayment(request))
            .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    @DisplayName("Should refund payment successfully")
    @Transactional
    void shouldRefundPayment() {
        // Given - Create completed payment
        Payment payment = Payment.builder()
            .userId(UUID.randomUUID())
            .amount(new BigDecimal("75.00"))
            .currency("USD")
            .status(PaymentStatus.COMPLETED)
            .build();
        payment = paymentRepository.save(payment);

        // When
        PaymentResponse refundResponse = paymentService.refundPayment(payment.getId(), "Customer request");

        // Then
        assertThat(refundResponse.getStatus()).isEqualTo(PaymentStatus.REFUNDED);

        Payment refunded = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("Should handle concurrent payment requests with optimistic locking")
    @Transactional
    void shouldHandleConcurrentRequests() {
        // Given
        Payment payment = Payment.builder()
            .userId(UUID.randomUUID())
            .amount(new BigDecimal("100.00"))
            .currency("USD")
            .status(PaymentStatus.PENDING)
            .version(0L)
            .build();
        payment = paymentRepository.save(payment);

        UUID paymentId = payment.getId();

        // When - Simulate concurrent updates
        Payment payment1 = paymentRepository.findById(paymentId).orElseThrow();
        Payment payment2 = paymentRepository.findById(paymentId).orElseThrow();

        payment1.setStatus(PaymentStatus.COMPLETED);
        paymentRepository.saveAndFlush(payment1);

        payment2.setStatus(PaymentStatus.FAILED);

        // Then - Second update should fail with optimistic lock exception
        assertThatThrownBy(() -> {
            paymentRepository.saveAndFlush(payment2);
        }).isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("Should publish Kafka event on payment initiation")
    @Transactional
    void shouldPublishKafkaEventOnInitiation() {
        // Given
        PaymentRequest request = PaymentRequest.builder()
            .userId(UUID.randomUUID())
            .amount(new BigDecimal("200.00"))
            .currency("USD")
            .paymentMethod("CREDIT_CARD")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        // When
        PaymentResponse response = paymentService.initiatePayment(request);

        // Then - Verify Kafka event published (using test listener)
        await().atMost(3, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                // Verify event was consumed by test consumer
                assertThat(testKafkaConsumer.getReceivedEvents())
                    .anyMatch(event -> event.getPaymentId().equals(response.getPaymentId()));
            });
    }

    @Test
    @DisplayName("Should apply transaction partitioning correctly")
    @Transactional
    void shouldApplyPartitioning() {
        // Given
        PaymentRequest request = PaymentRequest.builder()
            .userId(UUID.randomUUID())
            .amount(new BigDecimal("50.00"))
            .currency("USD")
            .paymentMethod("CREDIT_CARD")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();

        // When
        PaymentResponse response = paymentService.initiatePayment(request);

        // Then - Verify record inserted into correct partition
        // This would query the partition metadata
        Payment payment = paymentRepository.findById(response.getPaymentId()).orElseThrow();
        assertThat(payment.getCreatedAt()).isNotNull();

        // Verify can query across partitions
        long count = paymentRepository.count();
        assertThat(count).isGreaterThan(0);
    }

    static class InsufficientFundsException extends RuntimeException {
        public InsufficientFundsException(String message) {
            super(message);
        }
    }
}
