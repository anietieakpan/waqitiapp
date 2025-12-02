package com.waqiti.payment.integration;

import com.waqiti.common.test.BaseIntegrationTest;
import com.waqiti.common.test.TestDataBuilder;
import com.waqiti.common.test.TestFixtures;
import com.waqiti.payment.entity.Payment;
import com.waqiti.payment.entity.PaymentStatus;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.service.PaymentService;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for Payment Service with real database and REST API.
 *
 * Tests the full stack:
 * - REST API endpoints
 * - Service layer
 * - Repository layer
 * - Database transactions
 * - Event publishing
 *
 * Uses TestContainers for PostgreSQL and REST Assured for API testing.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2024-10-19
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Payment Service Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentServiceIntegrationTest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    private UUID testUserId;
    private UUID testMerchantId;

    @BeforeEach
    void setUpTest() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";

        testUserId = TestFixtures.VERIFIED_USER_ID;
        testMerchantId = TestFixtures.VERIFIED_MERCHANT_ID;
    }

    @Nested
    @DisplayName("Payment Creation API Tests")
    class PaymentCreationApiTests {

        @Test
        @Order(1)
        @DisplayName("Should create payment via REST API")
        void shouldCreatePaymentViaRestApi() {
            // Given
            String requestBody = String.format("""
                {
                    "userId": "%s",
                    "merchantId": "%s",
                    "amount": "100.00",
                    "currency": "USD",
                    "paymentMethod": "ACH",
                    "description": "Test payment via API"
                }
                """, testUserId, testMerchantId);

            // When / Then
            given()
                .contentType(ContentType.JSON)
                .body(requestBody)
            .when()
                .post("/api/v1/payments")
            .then()
                .statusCode(201)
                .body("status", equalTo("PENDING"))
                .body("amount", equalTo(100.00f))
                .body("currency", equalTo("USD"))
                .body("userId", equalTo(testUserId.toString()))
                .body("paymentId", notNullValue());
        }

        @Test
        @Order(2)
        @DisplayName("Should reject invalid payment amount")
        void shouldRejectInvalidPaymentAmount() {
            // Given
            String requestBody = String.format("""
                {
                    "userId": "%s",
                    "amount": "-100.00",
                    "currency": "USD"
                }
                """, testUserId);

            // When / Then
            given()
                .contentType(ContentType.JSON)
                .body(requestBody)
            .when()
                .post("/api/v1/payments")
            .then()
                .statusCode(400)
                .body("error", containsString("Amount must be positive"));
        }

        @Test
        @Order(3)
        @DisplayName("Should enforce idempotency")
        void shouldEnforceIdempotency() {
            // Given
            String idempotencyKey = UUID.randomUUID().toString();
            String requestBody = String.format("""
                {
                    "userId": "%s",
                    "amount": "100.00",
                    "currency": "USD",
                    "idempotencyKey": "%s"
                }
                """, testUserId, idempotencyKey);

            // When - First request
            String paymentId1 = given()
                .contentType(ContentType.JSON)
                .body(requestBody)
            .when()
                .post("/api/v1/payments")
            .then()
                .statusCode(201)
                .extract().path("paymentId");

            // Second request with same idempotency key
            String paymentId2 = given()
                .contentType(ContentType.JSON)
                .body(requestBody)
            .when()
                .post("/api/v1/payments")
            .then()
                .statusCode(200) // Should return existing payment
                .extract().path("paymentId");

            // Then
            assertThat(paymentId1).isEqualTo(paymentId2);
        }
    }

    @Nested
    @DisplayName("Payment Processing Tests")
    @Sql(scripts = "/test-data/payment-test-data.sql")
    class PaymentProcessingTests {

        @Test
        @DisplayName("Should process payment end-to-end")
        void shouldProcessPaymentEndToEnd() {
            // Given - Create a payment
            Payment payment = Payment.builder()
                    .paymentId(UUID.randomUUID())
                    .userId(testUserId)
                    .merchantId(testMerchantId)
                    .amount(TestFixtures.STANDARD_PAYMENT_AMOUNT)
                    .currency(TestFixtures.DEFAULT_CURRENCY)
                    .status(PaymentStatus.PENDING)
                    .paymentMethod("ACH")
                    .description("Integration test payment")
                    .build();

            Payment savedPayment = paymentRepository.save(payment);

            // When - Process the payment via API
            given()
                .pathParam("paymentId", savedPayment.getPaymentId())
            .when()
                .post("/api/v1/payments/{paymentId}/process")
            .then()
                .statusCode(200)
                .body("status", equalTo("COMPLETED"));

            // Then - Verify in database
            Payment processedPayment = paymentRepository.findByPaymentId(savedPayment.getPaymentId())
                    .orElseThrow();
            assertThat(processedPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(processedPayment.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should handle concurrent payment updates with optimistic locking")
        void shouldHandleConcurrentPaymentUpdates() {
            // Given - Create a payment
            Payment payment = Payment.builder()
                    .paymentId(UUID.randomUUID())
                    .userId(testUserId)
                    .amount(TestFixtures.STANDARD_PAYMENT_AMOUNT)
                    .currency(TestFixtures.DEFAULT_CURRENCY)
                    .status(PaymentStatus.PENDING)
                    .paymentMethod("ACH")
                    .version(0L)
                    .build();

            Payment savedPayment = paymentRepository.save(payment);

            // When - Simulate concurrent updates
            Payment payment1 = paymentRepository.findById(savedPayment.getId()).orElseThrow();
            Payment payment2 = paymentRepository.findById(savedPayment.getId()).orElseThrow();

            // First update succeeds
            payment1.setStatus(PaymentStatus.PROCESSING);
            paymentRepository.save(payment1);

            // Second update should fail due to version mismatch
            payment2.setStatus(PaymentStatus.COMPLETED);

            // Then
            assertThatThrownBy(() -> paymentRepository.save(payment2))
                    .isInstanceOf(OptimisticLockingFailureException.class);
        }
    }

    @Nested
    @DisplayName("Payment Query Tests")
    class PaymentQueryTests {

        @Test
        @DisplayName("Should retrieve payment by ID")
        void shouldRetrievePaymentById() {
            // Given - Create a payment
            Payment payment = Payment.builder()
                    .paymentId(UUID.randomUUID())
                    .userId(testUserId)
                    .amount(TestFixtures.STANDARD_PAYMENT_AMOUNT)
                    .currency(TestFixtures.DEFAULT_CURRENCY)
                    .status(PaymentStatus.COMPLETED)
                    .paymentMethod("ACH")
                    .build();

            Payment savedPayment = paymentRepository.save(payment);

            // When / Then
            given()
                .pathParam("paymentId", savedPayment.getPaymentId())
            .when()
                .get("/api/v1/payments/{paymentId}")
            .then()
                .statusCode(200)
                .body("paymentId", equalTo(savedPayment.getPaymentId().toString()))
                .body("status", equalTo("COMPLETED"))
                .body("amount", equalTo(100.00f));
        }

        @Test
        @DisplayName("Should list payments for user")
        void shouldListPaymentsForUser() {
            // Given - Create multiple payments for user
            for (int i = 0; i < 5; i++) {
                Payment payment = Payment.builder()
                        .paymentId(UUID.randomUUID())
                        .userId(testUserId)
                        .amount(TestDataBuilder.randomPaymentAmount())
                        .currency(TestFixtures.DEFAULT_CURRENCY)
                        .status(PaymentStatus.COMPLETED)
                        .paymentMethod("ACH")
                        .build();
                paymentRepository.save(payment);
            }

            // When / Then
            given()
                .pathParam("userId", testUserId)
                .queryParam("page", 0)
                .queryParam("size", 10)
            .when()
                .get("/api/v1/payments/user/{userId}")
            .then()
                .statusCode(200)
                .body("content", hasSize(greaterThanOrEqualTo(5)))
                .body("content[0].userId", equalTo(testUserId.toString()));
        }

        @Test
        @DisplayName("Should filter payments by status")
        void shouldFilterPaymentsByStatus() {
            // Given - Create payments with different statuses
            Payment completedPayment = Payment.builder()
                    .paymentId(UUID.randomUUID())
                    .userId(testUserId)
                    .amount(TestFixtures.STANDARD_PAYMENT_AMOUNT)
                    .currency(TestFixtures.DEFAULT_CURRENCY)
                    .status(PaymentStatus.COMPLETED)
                    .paymentMethod("ACH")
                    .build();
            paymentRepository.save(completedPayment);

            Payment pendingPayment = Payment.builder()
                    .paymentId(UUID.randomUUID())
                    .userId(testUserId)
                    .amount(TestFixtures.STANDARD_PAYMENT_AMOUNT)
                    .currency(TestFixtures.DEFAULT_CURRENCY)
                    .status(PaymentStatus.PENDING)
                    .paymentMethod("ACH")
                    .build();
            paymentRepository.save(pendingPayment);

            // When / Then - Filter for COMPLETED payments
            given()
                .pathParam("userId", testUserId)
                .queryParam("status", "COMPLETED")
            .when()
                .get("/api/v1/payments/user/{userId}")
            .then()
                .statusCode(200)
                .body("content.every { it.status == 'COMPLETED' }", is(true));
        }
    }

    @Nested
    @DisplayName("Database Transaction Tests")
    class DatabaseTransactionTests {

        @Test
        @DisplayName("Should rollback transaction on error")
        void shouldRollbackTransactionOnError() {
            // Given
            long initialCount = paymentRepository.count();

            // When - Attempt to create payment that will fail
            try {
                paymentService.createPaymentWithError(testUserId, TestFixtures.STANDARD_PAYMENT_AMOUNT);
            } catch (Exception e) {
                // Expected exception
            }

            // Then - Verify no payment was created (transaction rolled back)
            long finalCount = paymentRepository.count();
            assertThat(finalCount).isEqualTo(initialCount);
        }

        @Test
        @DisplayName("Should commit transaction on success")
        void shouldCommitTransactionOnSuccess() {
            // Given
            long initialCount = paymentRepository.count();

            // When
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .userId(testUserId)
                    .amount(TestFixtures.STANDARD_PAYMENT_AMOUNT)
                    .currency(TestFixtures.DEFAULT_CURRENCY)
                    .paymentMethod("ACH")
                    .build();

            Payment createdPayment = paymentService.createPayment(request);

            // Then - Verify payment exists in database
            long finalCount = paymentRepository.count();
            assertThat(finalCount).isEqualTo(initialCount + 1);

            Payment foundPayment = paymentRepository.findById(createdPayment.getId()).orElseThrow();
            assertThat(foundPayment.getPaymentId()).isEqualTo(createdPayment.getPaymentId());
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Should handle bulk payment creation efficiently")
        @Timeout(value = 5) // Should complete within 5 seconds
        void shouldHandleBulkPaymentCreationEfficiently() {
            // Given
            int paymentCount = 100;

            // When
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < paymentCount; i++) {
                Payment payment = Payment.builder()
                        .paymentId(UUID.randomUUID())
                        .userId(testUserId)
                        .amount(TestDataBuilder.randomPaymentAmount())
                        .currency(TestFixtures.DEFAULT_CURRENCY)
                        .status(PaymentStatus.PENDING)
                        .paymentMethod("ACH")
                        .build();
                paymentRepository.save(payment);
            }

            long duration = System.currentTimeMillis() - startTime;

            // Then
            log("Created %d payments in %d ms", paymentCount, duration);
            assertThat(duration).isLessThan(5000); // Should complete in under 5 seconds

            long count = paymentRepository.count();
            assertThat(count).isGreaterThanOrEqualTo(paymentCount);
        }
    }
}
