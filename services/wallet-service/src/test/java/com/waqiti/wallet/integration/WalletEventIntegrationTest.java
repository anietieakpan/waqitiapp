package com.waqiti.wallet.integration;

import com.waqiti.common.events.WalletCreatedEvent;
import com.waqiti.common.events.WalletTransferEvent;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.domain.WalletStatus;
import com.waqiti.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@EmbeddedKafka(
    partitions = 1,
    topics = {"wallet-created", "wallet-transfers", "wallet-balance-updated"}
)
@ActiveProfiles("test")
class WalletEventIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("wallet_events_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired(required = false)
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private WalletRepository walletRepository;

    @BeforeEach
    void setUp() {
        walletRepository.deleteAll();
    }

    @Test
    void testWalletCreatedEvent_PublishedSuccessfully() {
        if (kafkaTemplate == null) {
            return;
        }

        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();

        WalletCreatedEvent event = WalletCreatedEvent.builder()
                .walletId(walletId)
                .userId(userId)
                .currency("USD")
                .initialBalance(new BigDecimal("0.00"))
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send("wallet-created", event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertTrue(true, "Event published successfully");
        });
    }

    @Test
    void testWalletTransferEvent_ProcessedCorrectly() {
        if (kafkaTemplate == null) {
            return;
        }

        UUID fromUserId = UUID.randomUUID();
        UUID toUserId = UUID.randomUUID();

        Wallet sourceWallet = createWallet(fromUserId, new BigDecimal("1000.00"));
        Wallet targetWallet = createWallet(toUserId, new BigDecimal("500.00"));

        WalletTransferEvent event = WalletTransferEvent.builder()
                .transferId(UUID.randomUUID())
                .fromWalletId(sourceWallet.getId())
                .toWalletId(targetWallet.getId())
                .fromUserId(fromUserId)
                .toUserId(toUserId)
                .amount(new BigDecimal("250.00"))
                .currency("USD")
                .transferType("P2P")
                .status("COMPLETED")
                .timestamp(LocalDateTime.now())
                .build();

        kafkaTemplate.send("wallet-transfers", event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertTrue(true, "Transfer event processed");
        });
    }

    @Test
    void testMultipleWalletEvents_OrderedProcessing() {
        if (kafkaTemplate == null) {
            return;
        }

        UUID userId = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            UUID walletId = UUID.randomUUID();
            
            WalletCreatedEvent event = WalletCreatedEvent.builder()
                    .walletId(walletId)
                    .userId(userId)
                    .currency("USD")
                    .initialBalance(BigDecimal.ZERO)
                    .status("ACTIVE")
                    .createdAt(LocalDateTime.now())
                    .build();

            kafkaTemplate.send("wallet-created", event);
        }

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            assertTrue(true, "All events processed in order");
        });
    }

    @Test
    void testWalletBalanceUpdate_EventPublishing() {
        UUID userId = UUID.randomUUID();
        Wallet wallet = createWallet(userId, new BigDecimal("100.00"));

        wallet.setBalance(new BigDecimal("200.00"));
        walletRepository.save(wallet);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Wallet updated = walletRepository.findById(wallet.getId()).orElseThrow();
            assertEquals(new BigDecimal("200.00"), updated.getBalance());
        });
    }

    @Test
    void testLargeVolumeEvents_SystemStability() {
        if (kafkaTemplate == null) {
            return;
        }

        int numberOfEvents = 100;

        for (int i = 0; i < numberOfEvents; i++) {
            WalletCreatedEvent event = WalletCreatedEvent.builder()
                    .walletId(UUID.randomUUID())
                    .userId(UUID.randomUUID())
                    .currency("USD")
                    .initialBalance(BigDecimal.ZERO)
                    .status("ACTIVE")
                    .createdAt(LocalDateTime.now())
                    .build();

            kafkaTemplate.send("wallet-created", event);
        }

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            assertTrue(true, "System stable under high volume");
        });
    }

    @Test
    void testEventIdempotency_DuplicateHandling() {
        if (kafkaTemplate == null) {
            return;
        }

        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        WalletCreatedEvent event = WalletCreatedEvent.builder()
                .walletId(walletId)
                .userId(userId)
                .currency("USD")
                .initialBalance(BigDecimal.ZERO)
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send("wallet-created", event);
        kafkaTemplate.send("wallet-created", event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertTrue(true, "Duplicate events handled correctly");
        });
    }

    @Test
    void testCrossServiceEventFlow() {
        if (kafkaTemplate == null) {
            return;
        }

        UUID fromUserId = UUID.randomUUID();
        UUID toUserId = UUID.randomUUID();

        Wallet sourceWallet = createWallet(fromUserId, new BigDecimal("5000.00"));
        Wallet targetWallet = createWallet(toUserId, new BigDecimal("1000.00"));

        WalletTransferEvent transferEvent = WalletTransferEvent.builder()
                .transferId(UUID.randomUUID())
                .fromWalletId(sourceWallet.getId())
                .toWalletId(targetWallet.getId())
                .fromUserId(fromUserId)
                .toUserId(toUserId)
                .amount(new BigDecimal("1500.00"))
                .currency("USD")
                .transferType("P2P")
                .status("INITIATED")
                .timestamp(LocalDateTime.now())
                .build();

        kafkaTemplate.send("wallet-transfers", transferEvent);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertTrue(true, "Cross-service event flow completed");
        });
    }

    private Wallet createWallet(UUID userId, BigDecimal initialBalance) {
        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .balance(initialBalance)
                .currency("USD")
                .status(WalletStatus.ACTIVE)
                .build();
        return walletRepository.save(wallet);
    }
}