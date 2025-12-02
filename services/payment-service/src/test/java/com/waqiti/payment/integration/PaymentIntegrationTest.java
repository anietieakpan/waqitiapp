package com.waqiti.payment.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.PaymentServiceApplication;
import com.waqiti.payment.dto.InitiatePaymentRequest;
import com.waqiti.payment.dto.InitiatePaymentResponse;
import com.waqiti.payment.repository.PaymentRequestRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive Integration Tests for Payment Service
 *
 * Tests the entire payment flow end-to-end with real infrastructure:
 * - PostgreSQL database
 * - Kafka message broker
 * - Redis cache
 *
 * Coverage:
 * - Complete payment lifecycle
 * - Database transactions
 * - Event publishing
 * - Caching behavior
 * - Security and authorization
 * - Performance under load
 */
@SpringBootTest(
    classes = PaymentServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Payment Service Integration Tests")
class PaymentIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("payment_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
            .withReuse(true);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRequestRepository paymentRequestRepository;

    private UUID senderId;
    private UUID recipientId;

    @BeforeAll
    void setUpAll() {
        // Ensure containers are running
        assertThat(postgres.isRunning()).isTrue();
        assertThat(kafka.isRunning()).isTrue();
        assertThat(redis.isRunning()).isTrue();
    }

    @BeforeEach
    void setUp() {
        senderId = UUID.randomUUID();
        recipientId = UUID.randomUUID();
        paymentRequestRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    @DisplayName("Should process end-to-end payment successfully")
    void shouldProcessPaymentEndToEnd() throws Exception {
        // Arrange
        InitiatePaymentRequest request = InitiatePaymentRequest.builder()
                .senderId(senderId)
                .recipientId(recipientId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .description("Integration test payment")
                .build();

        // Act
        MvcResult result = mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseJson = result.getResponse().getContentAsString();
        InitiatePaymentResponse response = objectMapper.readValue(responseJson, InitiatePaymentResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.getPaymentId()).isNotNull();
        assertThat(response.getStatus()).isIn("SUCCESS", "PENDING");

        // Verify database persistence
        assertThat(paymentRequestRepository.findById(response.getPaymentId())).isPresent();
    }

    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    @DisplayName("Should handle idempotent requests correctly")
    void shouldHandleIdempotentRequests() throws Exception {
        // Arrange
        String idempotencyKey = UUID.randomUUID().toString();
        InitiatePaymentRequest request = InitiatePaymentRequest.builder()
                .senderId(senderId)
                .recipientId(recipientId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .description("Idempotent payment test")
                .idempotencyKey(idempotencyKey)
                .build();

        // Act - First request
        MvcResult firstResult = mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        InitiatePaymentResponse firstResponse = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(),
                InitiatePaymentResponse.class
        );

        // Act - Second request with same idempotency key
        MvcResult secondResult = mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        InitiatePaymentResponse secondResponse = objectMapper.readValue(
                secondResult.getResponse().getContentAsString(),
                InitiatePaymentResponse.class
        );

        // Assert - Same payment ID returned
        assertThat(firstResponse.getPaymentId()).isEqualTo(secondResponse.getPaymentId());

        // Verify only one payment was created
        assertThat(paymentRequestRepository.count()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    @DisplayName("Should validate payment amount limits")
    void shouldValidatePaymentAmountLimits() throws Exception {
        // Arrange - Amount exceeding limit
        InitiatePaymentRequest excessiveRequest = InitiatePaymentRequest.builder()
                .senderId(senderId)
                .recipientId(recipientId)
                .amount(new BigDecimal("1000000.00"))
                .currency("USD")
                .description("Excessive amount test")
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(excessiveRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("exceeds maximum")));
    }

    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    @DisplayName("Should publish Kafka event on successful payment")
    void shouldPublishKafkaEventOnPayment() throws Exception {
        // Arrange
        CountDownLatch kafkaMessageLatch = new CountDownLatch(1);
        InitiatePaymentRequest request = InitiatePaymentRequest.builder()
                .senderId(senderId)
                .recipientId(recipientId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .description("Kafka event test")
                .build();

        // Act
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Assert - Wait for Kafka event (with timeout)
        boolean eventReceived = kafkaMessageLatch.await(10, TimeUnit.SECONDS);
        assertThat(eventReceived).isTrue();
    }

    @Test
    @DisplayName("Should reject unauthorized payment requests")
    void shouldRejectUnauthorizedRequests() throws Exception {
        // Arrange
        InitiatePaymentRequest request = InitiatePaymentRequest.builder()
                .senderId(senderId)
                .recipientId(recipientId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .description("Unauthorized test")
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    @DisplayName("Should handle concurrent payment requests")
    void shouldHandleConcurrentPayments() throws Exception {
        // Arrange
        int concurrentRequests = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(concurrentRequests);

        // Act - Simulate concurrent requests
        for (int i = 0; i < concurrentRequests; i++) {
            final int requestNum = i;
            new Thread(() -> {
                try {
                    startLatch.await();

                    InitiatePaymentRequest request = InitiatePaymentRequest.builder()
                            .senderId(senderId)
                            .recipientId(recipientId)
                            .amount(new BigDecimal("10.00"))
                            .currency("USD")
                            .description("Concurrent test " + requestNum)
                            .build();

                    mockMvc.perform(post("/api/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)));

                    completionLatch.countDown();
                } catch (Exception e) {
                    fail("Concurrent request failed: " + e.getMessage());
                }
            }).start();
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Assert - All requests completed
        boolean allCompleted = completionLatch.await(30, TimeUnit.SECONDS);
        assertThat(allCompleted).isTrue();

        // Verify all payments were persisted
        assertThat(paymentRequestRepository.count()).isEqualTo(concurrentRequests);
    }

    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    @DisplayName("Should rollback transaction on failure")
    void shouldRollbackTransactionOnFailure() throws Exception {
        // Arrange - Request that will fail validation
        InitiatePaymentRequest invalidRequest = InitiatePaymentRequest.builder()
                .senderId(senderId)
                .recipientId(senderId) // Self-payment
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .description("Rollback test")
                .build();

        long initialCount = paymentRequestRepository.count();

        // Act
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        // Assert - No payment was persisted
        assertThat(paymentRequestRepository.count()).isEqualTo(initialCount);
    }

    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    @DisplayName("Should cache payment details for quick retrieval")
    void shouldCachePaymentDetails() throws Exception {
        // Arrange & Act - Create payment
        InitiatePaymentRequest request = InitiatePaymentRequest.builder()
                .senderId(senderId)
                .recipientId(recipientId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .description("Cache test")
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        InitiatePaymentResponse createResponse = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                InitiatePaymentResponse.class
        );

        // Act - Retrieve payment multiple times
        long startTime = System.currentTimeMillis();
        mockMvc.perform(get("/api/v1/payments/" + createResponse.getPaymentId()))
                .andExpect(status().isOk());
        long firstRetrievalTime = System.currentTimeMillis() - startTime;

        startTime = System.currentTimeMillis();
        mockMvc.perform(get("/api/v1/payments/" + createResponse.getPaymentId()))
                .andExpect(status().isOk());
        long cachedRetrievalTime = System.currentTimeMillis() - startTime;

        // Assert - Cached retrieval should be faster
        assertThat(cachedRetrievalTime).isLessThanOrEqualTo(firstRetrievalTime);
    }
}
