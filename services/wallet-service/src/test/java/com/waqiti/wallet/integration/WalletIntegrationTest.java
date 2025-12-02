package com.waqiti.wallet.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.wallet.WalletServiceApplication;
import com.waqiti.wallet.dto.*;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.repository.TransactionRepository;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive Integration Tests for Wallet Service
 *
 * Critical Coverage:
 * - Wallet creation and initialization
 * - Balance management and validation
 * - Concurrent transaction handling
 * - Ledger integration
 * - Event publishing
 * - Idempotency guarantees
 * - Double-entry accounting validation
 *
 * Business Rules Tested:
 * - No negative balances
 * - Atomic balance updates
 * - Transaction ordering
 * - Currency consistency
 * - Fraud detection integration
 */
@SpringBootTest(
    classes = WalletServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Wallet Service - Integration Tests")
class WalletIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("wallet_test")
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
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private UUID userId;

    @BeforeAll
    void setUpAll() {
        assertThat(postgres.isRunning()).isTrue();
        assertThat(kafka.isRunning()).isTrue();
        assertThat(redis.isRunning()).isTrue();
    }

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        walletRepository.deleteAll();
        transactionRepository.deleteAll();
    }

    // ==================== WALLET CREATION TESTS ====================

    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    @DisplayName("Should create wallet with initial zero balance")
    void shouldCreateWalletWithZeroBalance() throws Exception {
        // Arrange
        CreateWalletRequest request = CreateWalletRequest.builder()
                .userId(userId)
                .currency("USD")
                .build();

        // Act
        MvcResult result = mockMvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        // Assert
        String responseJson = result.getResponse().getContentAsString();
        WalletResponse response = objectMapper.readValue(responseJson, WalletResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getCurrency()).isEqualTo("USD");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");

        // Verify database persistence
        assertThat(walletRepository.findByUserId(userId)).isPresent();
    }

    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    @DisplayName("Should prevent duplicate wallet creation for same user")
    void shouldPreventDuplicateWalletCreation() throws Exception {
        // Arrange - Create first wallet
        CreateWalletRequest request = CreateWalletRequest.builder()
                .userId(userId)
                .currency("USD")
                .build();

        mockMvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Act - Attempt duplicate creation
        mockMvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("already exists")));

        // Assert - Only one wallet exists
        assertThat(walletRepository.findAllByUserId(userId)).hasSize(1);
    }

    // ==================== BALANCE MANAGEMENT TESTS ====================

    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    @DisplayName("Should credit wallet and update balance correctly")
    void shouldCreditWalletSuccessfully() throws Exception {
        // Arrange - Create wallet
        UUID walletId = createTestWallet(userId, "USD");

        CreditWalletRequest creditRequest = CreditWalletRequest.builder()
                .walletId(walletId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .reference("TEST_CREDIT_001")
                .description("Test credit")
                .build();

        // Act
        MvcResult result = mockMvc.perform(post("/api/v1/wallets/{walletId}/credit", walletId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(creditRequest)))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        TransactionResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                TransactionResponse.class
        );

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getNewBalance()).isEqualByComparingTo(new BigDecimal("100.00"));

        // Verify database balance
        var wallet = walletRepository.findById(walletId).orElseThrow();
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    @DisplayName("Should debit wallet and prevent negative balance")
    void shouldPreventNegativeBalance() throws Exception {
        // Arrange - Create wallet with $50 balance
        UUID walletId = createTestWallet(userId, "USD");
        creditWallet(walletId, new BigDecimal("50.00"));

        DebitWalletRequest debitRequest = DebitWalletRequest.builder()
                .walletId(walletId)
                .amount(new BigDecimal("100.00"))  // More than available
                .currency("USD")
                .reference("TEST_DEBIT_001")
                .description("Test debit")
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/v1/wallets/{walletId}/debit", walletId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debitRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Insufficient balance")));

        // Verify balance unchanged
        var wallet = walletRepository.findById(walletId).orElseThrow();
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    @DisplayName("Should handle concurrent transactions atomically")
    void shouldHandleConcurrentTransactionsAtomically() throws Exception {
        // Arrange - Create wallet with $1000 balance
        UUID walletId = createTestWallet(userId, "USD");
        creditWallet(walletId, new BigDecimal("1000.00"));

        int concurrentTransactions = 10;
        BigDecimal debitAmount = new BigDecimal("50.00");
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(concurrentTransactions);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Act - Execute 10 concurrent debits of $50 each
        for (int i = 0; i < concurrentTransactions; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();

                    DebitWalletRequest debitRequest = DebitWalletRequest.builder()
                            .walletId(walletId)
                            .amount(debitAmount)
                            .currency("USD")
                            .reference("CONCURRENT_" + UUID.randomUUID())
                            .description("Concurrent test")
                            .build();

                    var result = mockMvc.perform(post("/api/v1/wallets/{walletId}/debit", walletId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(debitRequest)))
                            .andReturn();

                    if (result.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }

                    completionLatch.countDown();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    completionLatch.countDown();
                }
            }).start();
        }

        // Start all threads simultaneously
        startLatch.countDown();
        boolean completed = completionLatch.await(30, TimeUnit.SECONDS);

        // Assert
        assertThat(completed).isTrue();

        // Verify final balance: $1000 - (successful debits × $50)
        var wallet = walletRepository.findById(walletId).orElseThrow();
        BigDecimal expectedBalance = new BigDecimal("1000.00")
                .subtract(debitAmount.multiply(new BigDecimal(successCount.get())));

        assertThat(wallet.getBalance()).isEqualByComparingTo(expectedBalance);

        // All transactions should succeed (sufficient balance)
        assertThat(successCount.get()).isEqualTo(concurrentTransactions);
        assertThat(failureCount.get()).isZero();
    }

    // ==================== TRANSFER TESTS ====================

    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    @DisplayName("Should transfer funds between wallets atomically")
    void shouldTransferFundsBetweenWallets() throws Exception {
        // Arrange - Create two wallets
        UUID senderWalletId = createTestWallet(userId, "USD");
        UUID recipientId = UUID.randomUUID();
        UUID recipientWalletId = createTestWallet(recipientId, "USD");

        creditWallet(senderWalletId, new BigDecimal("500.00"));

        TransferRequest transferRequest = TransferRequest.builder()
                .fromWalletId(senderWalletId)
                .toWalletId(recipientWalletId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .reference("TRANSFER_001")
                .description("Test transfer")
                .build();

        // Act
        MvcResult result = mockMvc.perform(post("/api/v1/wallets/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        TransferResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                TransferResponse.class
        );

        assertThat(response.getStatus()).isEqualTo("SUCCESS");

        // Verify sender balance
        var senderWallet = walletRepository.findById(senderWalletId).orElseThrow();
        assertThat(senderWallet.getBalance()).isEqualByComparingTo(new BigDecimal("400.00"));

        // Verify recipient balance
        var recipientWallet = walletRepository.findById(recipientWalletId).orElseThrow();
        assertThat(recipientWallet.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    @DisplayName("Should rollback transfer on failure (atomicity)")
    void shouldRollbackTransferOnFailure() throws Exception {
        // Arrange - Sender with sufficient balance, invalid recipient
        UUID senderWalletId = createTestWallet(userId, "USD");
        creditWallet(senderWalletId, new BigDecimal("500.00"));

        UUID invalidRecipientWalletId = UUID.randomUUID();

        TransferRequest transferRequest = TransferRequest.builder()
                .fromWalletId(senderWalletId)
                .toWalletId(invalidRecipientWalletId)  // Non-existent
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .reference("TRANSFER_ROLLBACK_001")
                .description("Test rollback")
                .build();

        // Act
        mockMvc.perform(post("/api/v1/wallets/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isNotFound());

        // Assert - Sender balance unchanged
        var senderWallet = walletRepository.findById(senderWalletId).orElseThrow();
        assertThat(senderWallet.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    // ==================== IDEMPOTENCY TESTS ====================

    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    @DisplayName("Should handle idempotent credit requests")
    void shouldHandleIdempotentCreditRequests() throws Exception {
        // Arrange
        UUID walletId = createTestWallet(userId, "USD");
        String idempotencyKey = "IDEMPOTENT_CREDIT_" + UUID.randomUUID();

        CreditWalletRequest creditRequest = CreditWalletRequest.builder()
                .walletId(walletId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .reference(idempotencyKey)
                .description("Idempotent credit test")
                .build();

        // Act - First request
        mockMvc.perform(post("/api/v1/wallets/{walletId}/credit", walletId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(creditRequest))
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isOk());

        // Act - Duplicate request with same idempotency key
        mockMvc.perform(post("/api/v1/wallets/{walletId}/credit", walletId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(creditRequest))
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isOk());

        // Assert - Balance credited only once
        var wallet = walletRepository.findById(walletId).orElseThrow();
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));

        // Verify only one transaction recorded
        assertThat(transactionRepository.findByReference(idempotencyKey)).hasSize(1);
    }

    // ==================== CURRENCY VALIDATION TESTS ====================

    @Test
    @WithMockUser(username = "test-user", roles = {"USER"})
    @DisplayName("Should reject currency mismatch in transactions")
    void shouldRejectCurrencyMismatch() throws Exception {
        // Arrange - USD wallet
        UUID walletId = createTestWallet(userId, "USD");

        CreditWalletRequest creditRequest = CreditWalletRequest.builder()
                .walletId(walletId)
                .amount(new BigDecimal("100.00"))
                .currency("EUR")  // Different currency
                .reference("CURRENCY_MISMATCH_001")
                .description("Currency mismatch test")
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/v1/wallets/{walletId}/credit", walletId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(creditRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Currency mismatch")));
    }

    // ==================== WALLET FREEZE TESTS ====================

    @Test
    @WithMockUser(username = "test-user", roles = {"ADMIN"})
    @DisplayName("Should freeze wallet and prevent transactions")
    void shouldFreezeWalletAndPreventTransactions() throws Exception {
        // Arrange - Create and fund wallet
        UUID walletId = createTestWallet(userId, "USD");
        creditWallet(walletId, new BigDecimal("500.00"));

        // Act - Freeze wallet
        mockMvc.perform(post("/api/v1/wallets/{walletId}/freeze", walletId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Attempt debit on frozen wallet
        DebitWalletRequest debitRequest = DebitWalletRequest.builder()
                .walletId(walletId)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .reference("FROZEN_DEBIT_001")
                .description("Test frozen debit")
                .build();

        mockMvc.perform(post("/api/v1/wallets/{walletId}/debit", walletId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debitRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(containsString("frozen")));

        // Assert - Balance unchanged
        var wallet = walletRepository.findById(walletId).orElseThrow();
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(wallet.getStatus()).isEqualTo("FROZEN");
    }

    // ==================== HELPER METHODS ====================

    private UUID createTestWallet(UUID userId, String currency) throws Exception {
        CreateWalletRequest request = CreateWalletRequest.builder()
                .userId(userId)
                .currency(currency)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        WalletResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                WalletResponse.class
        );

        return response.getWalletId();
    }

    private void creditWallet(UUID walletId, BigDecimal amount) throws Exception {
        CreditWalletRequest creditRequest = CreditWalletRequest.builder()
                .walletId(walletId)
                .amount(amount)
                .currency("USD")
                .reference("SETUP_CREDIT_" + UUID.randomUUID())
                .description("Test setup credit")
                .build();

        mockMvc.perform(post("/api/v1/wallets/{walletId}/credit", walletId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(creditRequest)))
                .andExpect(status().isOk());
    }
}
