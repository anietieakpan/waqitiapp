package com.waqiti.fraud.integration;

import com.waqiti.common.events.TransactionBlockEvent;
import com.waqiti.fraud.consumer.TransactionBlockEventConsumer;
import com.waqiti.fraud.domain.BlockedTransaction;
import com.waqiti.fraud.domain.FraudAlert;
import com.waqiti.fraud.repository.BlockedTransactionRepository;
import com.waqiti.fraud.repository.FraudAlertRepository;
import com.waqiti.fraud.service.FraudInvestigationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class TransactionBlockIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("fraud_test")
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
    }

    @Autowired
    private TransactionBlockEventConsumer transactionBlockConsumer;

    @Autowired
    private BlockedTransactionRepository blockedTransactionRepository;

    @Autowired
    private FraudAlertRepository fraudAlertRepository;

    @Autowired
    private FraudInvestigationService investigationService;

    @BeforeEach
    void setUp() {
        blockedTransactionRepository.deleteAll();
        fraudAlertRepository.deleteAll();
    }

    @Test
    void testTransactionBlocking_HighConfidenceFraud() {
        UUID transactionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        
        TransactionBlockEvent event = TransactionBlockEvent.builder()
                .transactionId(transactionId)
                .userId(userId)
                .transactionAmount(new BigDecimal("5000.00"))
                .currency("USD")
                .blockReason(TransactionBlockEvent.BlockReason.FRAUD_PREVENTION)
                .severity(TransactionBlockEvent.BlockSeverity.CRITICAL)
                .riskScore(0.95)
                .blockingSystem("ML_FRAUD_ENGINE")
                .requiresManualReview(true)
                .blockedAt(LocalDateTime.now())
                .build();

        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        
        transactionBlockConsumer.processTransactionBlock(event, "transaction-blocks", 0, 0L, null, acknowledgment);

        BlockedTransaction blockedTx = blockedTransactionRepository
                .findByTransactionId(transactionId.toString()).orElseThrow();
        
        assertEquals("BLOCKED", blockedTx.getStatus());
        assertEquals(0.95, blockedTx.getConfidenceScore());
        assertTrue(blockedTx.isRequiresInvestigation());
        
        List<FraudAlert> alerts = fraudAlertRepository.findByTransactionId(transactionId);
        assertFalse(alerts.isEmpty());
        assertEquals("CRITICAL", alerts.get(0).getSeverity());
    }

    @Test
    void testTransactionBlockIdempotency() {
        UUID transactionId = UUID.randomUUID();
        
        TransactionBlockEvent event = TransactionBlockEvent.builder()
                .transactionId(transactionId)
                .userId(UUID.randomUUID())
                .transactionAmount(new BigDecimal("100.00"))
                .currency("USD")
                .blockReason(TransactionBlockEvent.BlockReason.AML_PATTERN_DETECTION)
                .severity(TransactionBlockEvent.BlockSeverity.MEDIUM)
                .riskScore(0.70)
                .blockingSystem("AML_ENGINE")
                .requiresManualReview(false)
                .blockedAt(LocalDateTime.now())
                .build();

        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        
        transactionBlockConsumer.processTransactionBlock(event, "transaction-blocks", 0, 0L, null, acknowledgment);
        
        long countFirst = blockedTransactionRepository.count();
        assertEquals(1, countFirst);
        
        transactionBlockConsumer.processTransactionBlock(event, "transaction-blocks", 0, 0L, null, acknowledgment);
        
        long countSecond = blockedTransactionRepository.count();
        assertEquals(1, countSecond, "Duplicate block event should not create another record");
    }

    @Test
    void testBlockProcessingSpeed_Sub100ms() {
        UUID transactionId = UUID.randomUUID();
        
        TransactionBlockEvent event = TransactionBlockEvent.builder()
                .transactionId(transactionId)
                .userId(UUID.randomUUID())
                .transactionAmount(new BigDecimal("250.00"))
                .currency("USD")
                .blockReason(TransactionBlockEvent.BlockReason.FRAUD_PREVENTION)
                .severity(TransactionBlockEvent.BlockSeverity.HIGH)
                .riskScore(0.85)
                .blockingSystem("REAL_TIME_FRAUD_ENGINE")
                .requiresManualReview(true)
                .blockedAt(LocalDateTime.now())
                .build();

        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        
        long startTime = System.currentTimeMillis();
        transactionBlockConsumer.processTransactionBlock(event, "transaction-blocks", 0, 0L, null, acknowledgment);
        long processingTime = System.currentTimeMillis() - startTime;
        
        assertTrue(processingTime < 100, 
            "Transaction blocking should complete in <100ms, actual: " + processingTime + "ms");
        
        assertTrue(blockedTransactionRepository.existsByTransactionId(transactionId.toString()));
    }

    @Test
    void testAutoUnblockEligibility_LowConfidence() {
        UUID transactionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        
        TransactionBlockEvent event = TransactionBlockEvent.builder()
                .transactionId(transactionId)
                .userId(userId)
                .transactionAmount(new BigDecimal("50.00"))
                .currency("USD")
                .blockReason(TransactionBlockEvent.BlockReason.AML_VELOCITY_CHECK)
                .severity(TransactionBlockEvent.BlockSeverity.LOW)
                .riskScore(0.55)
                .blockingSystem("VELOCITY_CHECK")
                .requiresManualReview(false)
                .temporaryBlock(true)
                .blockExpiresAt(LocalDateTime.now().plusMinutes(5))
                .blockedAt(LocalDateTime.now())
                .build();

        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        
        transactionBlockConsumer.processTransactionBlock(event, "transaction-blocks", 0, 0L, null, acknowledgment);

        BlockedTransaction blockedTx = blockedTransactionRepository
                .findByTransactionId(transactionId.toString()).orElseThrow();
        
        assertTrue(blockedTx.isTemporary());
        assertNotNull(blockedTx.getExpiresAt());
        assertTrue(blockedTx.getConfidenceScore() < 0.60);
    }

    @Test
    void testHighValueTransaction_ImmediateReview() {
        UUID transactionId = UUID.randomUUID();
        BigDecimal highValue = new BigDecimal("50000.00");
        
        TransactionBlockEvent event = TransactionBlockEvent.builder()
                .transactionId(transactionId)
                .userId(UUID.randomUUID())
                .transactionAmount(highValue)
                .currency("USD")
                .blockReason(TransactionBlockEvent.BlockReason.MANUAL_REVIEW)
                .severity(TransactionBlockEvent.BlockSeverity.CRITICAL)
                .riskScore(0.90)
                .blockingSystem("HIGH_VALUE_REVIEW")
                .requiresManualReview(true)
                .notifyRegulators(true)
                .blockedAt(LocalDateTime.now())
                .build();

        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        
        transactionBlockConsumer.processTransactionBlock(event, "transaction-blocks", 0, 0L, null, acknowledgment);

        BlockedTransaction blockedTx = blockedTransactionRepository
                .findByTransactionId(transactionId.toString()).orElseThrow();
        
        assertTrue(blockedTx.getAmount().compareTo(new BigDecimal("10000.00")) > 0);
        assertTrue(blockedTx.isRequiresInvestigation());
        
        List<FraudAlert> alerts = fraudAlertRepository.findByTransactionId(transactionId);
        assertEquals("CRITICAL", alerts.get(0).getSeverity());
        assertTrue(alerts.get(0).isRequiresImmediateAction());
    }

    @Test
    void testSanctionsMatch_CriticalBlocking() {
        UUID transactionId = UUID.randomUUID();
        
        TransactionBlockEvent event = TransactionBlockEvent.builder()
                .transactionId(transactionId)
                .userId(UUID.randomUUID())
                .transactionAmount(new BigDecimal("1000.00"))
                .currency("USD")
                .blockReason(TransactionBlockEvent.BlockReason.SANCTIONS_MATCH)
                .severity(TransactionBlockEvent.BlockSeverity.CRITICAL)
                .riskScore(1.0)
                .blockingSystem("SANCTIONS_SCREENING")
                .requiresManualReview(true)
                .notifyRegulators(true)
                .blockedAt(LocalDateTime.now())
                .build();

        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        
        transactionBlockConsumer.processTransactionBlock(event, "transaction-blocks", 0, 0L, null, acknowledgment);

        BlockedTransaction blockedTx = blockedTransactionRepository
                .findByTransactionId(transactionId.toString()).orElseThrow();
        
        assertEquals("SANCTIONS_MATCH", blockedTx.getBlockReason());
        assertEquals(1.0, blockedTx.getConfidenceScore());
        assertFalse(blockedTx.isTemporary(), "Sanctions blocks should not be temporary");
    }

    @Test
    void testConcurrentBlockProcessing() throws Exception {
        int numberOfBlocks = 20;
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(numberOfBlocks);
        
        for (int i = 0; i < numberOfBlocks; i++) {
            final UUID transactionId = UUID.randomUUID();
            executor.submit(() -> {
                try {
                    TransactionBlockEvent event = TransactionBlockEvent.builder()
                            .transactionId(transactionId)
                            .userId(UUID.randomUUID())
                            .transactionAmount(new BigDecimal("100.00"))
                            .currency("USD")
                            .blockReason(TransactionBlockEvent.BlockReason.FRAUD_PREVENTION)
                            .severity(TransactionBlockEvent.BlockSeverity.MEDIUM)
                            .riskScore(0.75)
                            .blockingSystem("CONCURRENT_TEST")
                            .requiresManualReview(false)
                            .blockedAt(LocalDateTime.now())
                            .build();

                    Acknowledgment acknowledgment = mock(Acknowledgment.class);
                    transactionBlockConsumer.processTransactionBlock(
                        event, "transaction-blocks", 0, 0L, null, acknowledgment);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(numberOfBlocks, blockedTransactionRepository.count());
        });
    }

    @Test
    void testWorkflowAssignment_BasedOnRiskScore() {
        UUID highRiskTxId = UUID.randomUUID();
        UUID mediumRiskTxId = UUID.randomUUID();
        UUID lowRiskTxId = UUID.randomUUID();
        
        TransactionBlockEvent highRisk = createBlockEvent(highRiskTxId, 0.96, 
            TransactionBlockEvent.BlockSeverity.CRITICAL);
        TransactionBlockEvent mediumRisk = createBlockEvent(mediumRiskTxId, 0.78, 
            TransactionBlockEvent.BlockSeverity.MEDIUM);
        TransactionBlockEvent lowRisk = createBlockEvent(lowRiskTxId, 0.66, 
            TransactionBlockEvent.BlockSeverity.LOW);

        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        
        transactionBlockConsumer.processTransactionBlock(highRisk, "transaction-blocks", 0, 0L, null, acknowledgment);
        transactionBlockConsumer.processTransactionBlock(mediumRisk, "transaction-blocks", 0, 0L, null, acknowledgment);
        transactionBlockConsumer.processTransactionBlock(lowRisk, "transaction-blocks", 0, 0L, null, acknowledgment);

        BlockedTransaction highRiskTx = blockedTransactionRepository
                .findByTransactionId(highRiskTxId.toString()).orElseThrow();
        assertEquals("IMMEDIATE_REVIEW", highRiskTx.getInvestigationWorkflow());
        
        BlockedTransaction mediumRiskTx = blockedTransactionRepository
                .findByTransactionId(mediumRiskTxId.toString()).orElseThrow();
        assertEquals("STANDARD_REVIEW", mediumRiskTx.getInvestigationWorkflow());
        
        BlockedTransaction lowRiskTx = blockedTransactionRepository
                .findByTransactionId(lowRiskTxId.toString()).orElseThrow();
        assertEquals("AUTOMATED_REVIEW", lowRiskTx.getInvestigationWorkflow());
    }

    @Test
    void testComplianceViolationTracking() {
        UUID transactionId = UUID.randomUUID();
        List<String> violations = List.of("AML_VELOCITY", "HIGH_RISK_COUNTRY", "SUSPICIOUS_PATTERN");
        
        TransactionBlockEvent event = TransactionBlockEvent.builder()
                .transactionId(transactionId)
                .userId(UUID.randomUUID())
                .transactionAmount(new BigDecimal("2000.00"))
                .currency("USD")
                .blockReason(TransactionBlockEvent.BlockReason.COMPLIANCE_RULE)
                .severity(TransactionBlockEvent.BlockSeverity.HIGH)
                .riskScore(0.88)
                .complianceViolations(violations)
                .blockingSystem("COMPLIANCE_ENGINE")
                .requiresManualReview(true)
                .blockedAt(LocalDateTime.now())
                .build();

        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        
        transactionBlockConsumer.processTransactionBlock(event, "transaction-blocks", 0, 0L, null, acknowledgment);

        BlockedTransaction blockedTx = blockedTransactionRepository
                .findByTransactionId(transactionId.toString()).orElseThrow();
        
        assertNotNull(blockedTx.getRiskFactors());
        assertTrue(blockedTx.getRiskFactors().containsAll(violations));
    }

    private TransactionBlockEvent createBlockEvent(UUID transactionId, double riskScore, 
                                                   TransactionBlockEvent.BlockSeverity severity) {
        return TransactionBlockEvent.builder()
                .transactionId(transactionId)
                .userId(UUID.randomUUID())
                .transactionAmount(new BigDecimal("500.00"))
                .currency("USD")
                .blockReason(TransactionBlockEvent.BlockReason.FRAUD_PREVENTION)
                .severity(severity)
                .riskScore(riskScore)
                .blockingSystem("TEST_ENGINE")
                .requiresManualReview(riskScore > 0.8)
                .blockedAt(LocalDateTime.now())
                .build();
    }
}