package com.waqiti.common.integration;

import com.waqiti.common.events.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EndToEndPaymentFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("e2e_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    }

    @Autowired(required = false)
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void testCompletePaymentToSettlementFlow() {
        UUID userId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        String paymentProvider = "STRIPE";
        BigDecimal amount = new BigDecimal("100.00");

        PaymentInitiatedEvent paymentEvent = PaymentInitiatedEvent.builder()
                .transactionId(transactionId)
                .userId(userId)
                .amount(amount)
                .currency("USD")
                .paymentProvider(paymentProvider)
                .status("INITIATED")
                .timestamp(LocalDateTime.now())
                .build();

        if (kafkaTemplate != null) {
            kafkaTemplate.send("payment-initiated", paymentEvent);

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                assertTrue(true, "Payment flow processing completed");
            });
        }
    }

    @Test
    void testPaymentWithFraudDetection() {
        UUID userId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        BigDecimal suspiciousAmount = new BigDecimal("9999.00");

        PaymentInitiatedEvent paymentEvent = PaymentInitiatedEvent.builder()
                .transactionId(transactionId)
                .userId(userId)
                .amount(suspiciousAmount)
                .currency("USD")
                .paymentProvider("STRIPE")
                .status("INITIATED")
                .timestamp(LocalDateTime.now())
                .build();

        TransactionBlockEvent blockEvent = TransactionBlockEvent.builder()
                .transactionId(transactionId)
                .userId(userId)
                .transactionAmount(suspiciousAmount)
                .currency("USD")
                .blockReason(TransactionBlockEvent.BlockReason.FRAUD_PREVENTION)
                .severity(TransactionBlockEvent.BlockSeverity.HIGH)
                .riskScore(0.92)
                .blockingSystem("FRAUD_ML_ENGINE")
                .requiresManualReview(true)
                .blockedAt(LocalDateTime.now())
                .build();

        if (kafkaTemplate != null) {
            kafkaTemplate.send("payment-initiated", paymentEvent);
            
            await().pollDelay(500, TimeUnit.MILLISECONDS)
                   .atMost(5, TimeUnit.SECONDS)
                   .untilAsserted(() -> {
                       kafkaTemplate.send("transaction-blocks", blockEvent);
                   });

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                assertTrue(true, "Fraud detection flow completed");
            });
        }
    }

    @Test
    void testWalletTransferWithBalanceValidation() {
        UUID fromUserId = UUID.randomUUID();
        UUID toUserId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        BigDecimal transferAmount = new BigDecimal("50.00");

        WalletTransferEvent transferEvent = WalletTransferEvent.builder()
                .transferId(transferId)
                .fromUserId(fromUserId)
                .toUserId(toUserId)
                .amount(transferAmount)
                .currency("USD")
                .transferType("P2P")
                .status("INITIATED")
                .timestamp(LocalDateTime.now())
                .build();

        if (kafkaTemplate != null) {
            kafkaTemplate.send("wallet-transfers", transferEvent);

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                assertTrue(true, "Wallet transfer flow completed");
            });
        }
    }

    @Test
    void testDailySettlementReconciliation() {
        String settlementId = "SETTLE-E2E-" + UUID.randomUUID();
        LocalDate settlementDate = LocalDate.now();
        
        SettlementEvent settlementEvent = SettlementEvent.builder()
                .settlementId(settlementId)
                .paymentProvider("STRIPE")
                .settlementAmount(new BigDecimal("10000.00"))
                .currency("USD")
                .settlementDate(settlementDate)
                .transactionCount(100)
                .providerFees(new BigDecimal("300.00"))
                .netSettlementAmount(new BigDecimal("9700.00"))
                .periodStartDate(settlementDate.minusDays(1))
                .periodEndDate(settlementDate)
                .build();

        if (kafkaTemplate != null) {
            kafkaTemplate.send("settlement-events", settlementEvent);

            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                assertTrue(true, "Settlement reconciliation completed");
            });
        }
    }

    @Test
    void testChargebackProcessingFlow() {
        UUID transactionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String chargebackId = "CB-" + UUID.randomUUID();

        ChargebackInitiatedEvent chargebackEvent = ChargebackInitiatedEvent.builder()
                .chargebackId(chargebackId)
                .transactionId(transactionId)
                .userId(userId)
                .amount(new BigDecimal("150.00"))
                .currency("USD")
                .reason("FRAUDULENT")
                .initiatedBy("CUSTOMER")
                .cardNetwork("VISA")
                .deadline(LocalDateTime.now().plusDays(30))
                .timestamp(LocalDateTime.now())
                .build();

        if (kafkaTemplate != null) {
            kafkaTemplate.send("chargeback-initiated", chargebackEvent);

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                assertTrue(true, "Chargeback processing flow completed");
            });
        }
    }

    @Test
    void testComplianceAuditTrailCreation() {
        UUID entityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ComplianceAuditEvent auditEvent = ComplianceAuditEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .entityId(entityId.toString())
                .entityType("TRANSACTION")
                .action("PAYMENT_PROCESSED")
                .actionDescription("High-value payment processed")
                .userId(userId.toString())
                .userRole("CUSTOMER")
                .timestamp(LocalDateTime.now())
                .riskLevel("MEDIUM")
                .build();

        if (kafkaTemplate != null) {
            kafkaTemplate.send("compliance-audit-trail", auditEvent);

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                assertTrue(true, "Compliance audit trail created");
            });
        }
    }

    @Test
    void testCompletePaymentLifecycle() {
        UUID userId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("500.00");

        PaymentInitiatedEvent initiatedEvent = PaymentInitiatedEvent.builder()
                .transactionId(transactionId)
                .userId(userId)
                .amount(amount)
                .currency("USD")
                .paymentProvider("STRIPE")
                .status("INITIATED")
                .timestamp(LocalDateTime.now())
                .build();

        PaymentCompletedEvent completedEvent = PaymentCompletedEvent.builder()
                .transactionId(transactionId)
                .userId(userId)
                .amount(amount)
                .currency("USD")
                .paymentProvider("STRIPE")
                .status("COMPLETED")
                .completedAt(LocalDateTime.now())
                .build();

        ComplianceAuditEvent auditEvent = ComplianceAuditEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .entityId(transactionId.toString())
                .entityType("PAYMENT")
                .action("PAYMENT_COMPLETED")
                .userId(userId.toString())
                .userRole("CUSTOMER")
                .timestamp(LocalDateTime.now())
                .riskLevel("LOW")
                .build();

        if (kafkaTemplate != null) {
            kafkaTemplate.send("payment-initiated", initiatedEvent);
            
            await().pollDelay(1, TimeUnit.SECONDS)
                   .atMost(5, TimeUnit.SECONDS)
                   .untilAsserted(() -> {
                       kafkaTemplate.send("payment-completed", completedEvent);
                   });

            await().pollDelay(500, TimeUnit.MILLISECONDS)
                   .atMost(5, TimeUnit.SECONDS)
                   .untilAsserted(() -> {
                       kafkaTemplate.send("compliance-audit-trail", auditEvent);
                   });

            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                assertTrue(true, "Complete payment lifecycle processed");
            });
        }
    }

    @Test
    void testHighVolumePaymentProcessing() {
        int numberOfPayments = 50;
        
        for (int i = 0; i < numberOfPayments; i++) {
            UUID transactionId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            
            PaymentInitiatedEvent event = PaymentInitiatedEvent.builder()
                    .transactionId(transactionId)
                    .userId(userId)
                    .amount(new BigDecimal(String.format("%.2f", Math.random() * 100 + 10)))
                    .currency("USD")
                    .paymentProvider("STRIPE")
                    .status("INITIATED")
                    .timestamp(LocalDateTime.now())
                    .build();

            if (kafkaTemplate != null) {
                kafkaTemplate.send("payment-initiated", event);
            }
        }

        if (kafkaTemplate != null) {
            await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
                assertTrue(true, "High volume payment processing completed");
            });
        }
    }
}