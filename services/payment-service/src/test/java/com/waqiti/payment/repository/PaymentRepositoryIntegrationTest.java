package com.waqiti.payment.repository;

import com.waqiti.payment.model.Payment;
import com.waqiti.payment.model.PaymentStatus;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for PaymentRepository using TestContainers.
 * Tests actual database interactions with real PostgreSQL instance.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PaymentRepository Integration Tests")
class PaymentRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PaymentRepository paymentRepository;

    private UUID userId;
    private UUID merchantId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        merchantId = UUID.randomUUID();
        paymentRepository.deleteAll();
    }

    @Test
    @DisplayName("Should save and retrieve payment successfully")
    void shouldSaveAndRetrievePayment() {
        // Arrange
        Payment payment = createTestPayment();

        // Act
        Payment savedPayment = paymentRepository.save(payment);
        Optional<Payment> retrievedPayment = paymentRepository.findById(savedPayment.getId());

        // Assert
        assertTrue(retrievedPayment.isPresent());
        assertEquals(savedPayment.getId(), retrievedPayment.get().getId());
        assertEquals(savedPayment.getAmount(), retrievedPayment.get().getAmount());
        assertEquals(savedPayment.getUserId(), retrievedPayment.get().getUserId());
    }

    @Test
    @DisplayName("Should find payments by user ID")
    void shouldFindPaymentsByUserId() {
        // Arrange
        Payment payment1 = createTestPayment();
        Payment payment2 = createTestPayment();
        payment2.setAmount(BigDecimal.valueOf(200.00));

        paymentRepository.save(payment1);
        paymentRepository.save(payment2);

        // Act
        List<Payment> payments = paymentRepository.findByUserId(userId);

        // Assert
        assertEquals(2, payments.size());
        assertTrue(payments.stream().allMatch(p -> p.getUserId().equals(userId)));
    }

    @Test
    @DisplayName("Should find payments by status")
    void shouldFindPaymentsByStatus() {
        // Arrange
        Payment completedPayment = createTestPayment();
        completedPayment.setStatus(PaymentStatus.COMPLETED);

        Payment pendingPayment = createTestPayment();
        pendingPayment.setStatus(PaymentStatus.PENDING);

        paymentRepository.save(completedPayment);
        paymentRepository.save(pendingPayment);

        // Act
        List<Payment> completedPayments = paymentRepository.findByStatus(PaymentStatus.COMPLETED);

        // Assert
        assertEquals(1, completedPayments.size());
        assertEquals(PaymentStatus.COMPLETED, completedPayments.get(0).getStatus());
    }

    @Test
    @DisplayName("Should find recent payments by user")
    void shouldFindRecentPaymentsByUser() {
        // Arrange
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);

        Payment recentPayment = createTestPayment();
        recentPayment.setCreatedAt(LocalDateTime.now().minusHours(2));

        Payment oldPayment = createTestPayment();
        oldPayment.setCreatedAt(LocalDateTime.now().minusDays(2));

        paymentRepository.save(recentPayment);
        paymentRepository.save(oldPayment);

        // Act
        List<Payment> recentPayments = paymentRepository.findRecentPaymentsByUserId(userId, cutoff);

        // Assert
        assertEquals(1, recentPayments.size());
        assertTrue(recentPayments.get(0).getCreatedAt().isAfter(cutoff));
    }

    @Test
    @DisplayName("Should handle optimistic locking")
    void shouldHandleOptimisticLocking() {
        // Arrange
        Payment payment = createTestPayment();
        Payment savedPayment = paymentRepository.save(payment);

        // Act & Assert - Simulate concurrent updates
        Payment payment1 = paymentRepository.findById(savedPayment.getId()).get();
        Payment payment2 = paymentRepository.findById(savedPayment.getId()).get();

        payment1.setStatus(PaymentStatus.PROCESSING);
        paymentRepository.save(payment1);

        payment2.setStatus(PaymentStatus.COMPLETED);

        // Second save should fail due to version mismatch
        assertThrows(Exception.class, () -> paymentRepository.saveAndFlush(payment2));
    }

    private Payment createTestPayment() {
        return Payment.builder()
                .userId(userId)
                .merchantId(merchantId)
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .status(PaymentStatus.PENDING)
                .paymentMethod("CARD")
                .createdAt(LocalDateTime.now())
                .version(0L)
                .build();
    }
}
