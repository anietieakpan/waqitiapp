package com.waqiti.payment.contract;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.waqiti.payment.PaymentServiceApplication;
import com.waqiti.payment.domain.PaymentRequestEntity;
import com.waqiti.payment.repository.PaymentRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Contract Tests for Payment Service using Pact
 *
 * Verifies API contracts with consumer services:
 * - wallet-service
 * - notification-service
 * - analytics-service
 * - frontend applications
 *
 * Ensures backward compatibility and API stability
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = PaymentServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Provider("payment-service")
@PactFolder("pacts")
@ActiveProfiles("contract-test")
public class PaymentContractTest {

    @LocalServerPort
    private int port;

    private final PaymentRequestRepository paymentRequestRepository;

    public PaymentContractTest(PaymentRequestRepository paymentRequestRepository) {
        this.paymentRequestRepository = paymentRequestRepository;
    }

    @BeforeEach
    void setUp(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", port));
        paymentRequestRepository.deleteAll();
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        context.verifyInteraction();
    }

    // ==================== PROVIDER STATES ====================

    @State("a payment request exists")
    void setupExistingPayment() {
        UUID paymentId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        PaymentRequestEntity payment = PaymentRequestEntity.builder()
                .id(paymentId)
                .senderId(UUID.randomUUID())
                .recipientId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status("COMPLETED")
                .description("Contract test payment")
                .createdAt(LocalDateTime.now())
                .build();

        paymentRequestRepository.save(payment);
    }

    @State("no payment exists with ID")
    void setupNoPayment() {
        // Ensure database is empty
        paymentRequestRepository.deleteAll();
    }

    @State("a pending payment exists")
    void setupPendingPayment() {
        UUID paymentId = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");
        PaymentRequestEntity payment = PaymentRequestEntity.builder()
                .id(paymentId)
                .senderId(UUID.randomUUID())
                .recipientId(UUID.randomUUID())
                .amount(new BigDecimal("250.00"))
                .currency("USD")
                .status("PENDING")
                .description("Pending payment for contract test")
                .createdAt(LocalDateTime.now())
                .build();

        paymentRequestRepository.save(payment);
    }

    @State("a failed payment exists")
    void setupFailedPayment() {
        UUID paymentId = UUID.fromString("770e8400-e29b-41d4-a716-446655440002");
        PaymentRequestEntity payment = PaymentRequestEntity.builder()
                .id(paymentId)
                .senderId(UUID.randomUUID())
                .recipientId(UUID.randomUUID())
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .status("FAILED")
                .description("Failed payment for contract test")
                .failureReason("Insufficient funds")
                .createdAt(LocalDateTime.now())
                .build();

        paymentRequestRepository.save(payment);
    }

    @State("user has sufficient balance")
    void setupSufficientBalance() {
        // Mock wallet service response for sufficient balance
        // In actual implementation, use WireMock or test doubles
    }

    @State("user has insufficient balance")
    void setupInsufficientBalance() {
        // Mock wallet service response for insufficient balance
    }

    @State("payment service is healthy")
    void setupHealthyService() {
        // Service is running, no special setup needed
    }

    @State("multiple payments exist for user")
    void setupMultiplePayments() {
        UUID userId = UUID.fromString("880e8400-e29b-41d4-a716-446655440003");

        for (int i = 0; i < 5; i++) {
            PaymentRequestEntity payment = PaymentRequestEntity.builder()
                    .id(UUID.randomUUID())
                    .senderId(userId)
                    .recipientId(UUID.randomUUID())
                    .amount(new BigDecimal("50.00"))
                    .currency("USD")
                    .status(i % 2 == 0 ? "COMPLETED" : "PENDING")
                    .description("Multiple payment " + i)
                    .createdAt(LocalDateTime.now().minusDays(i))
                    .build();

            paymentRequestRepository.save(payment);
        }
    }
}
