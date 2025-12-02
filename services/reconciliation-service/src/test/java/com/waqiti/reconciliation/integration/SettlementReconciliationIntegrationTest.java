package com.waqiti.reconciliation.integration;

import com.waqiti.common.events.SettlementEvent;
import com.waqiti.reconciliation.consumer.SettlementEventConsumer;
import com.waqiti.reconciliation.domain.Settlement;
import com.waqiti.reconciliation.domain.SettlementDiscrepancy;
import com.waqiti.reconciliation.repository.SettlementRepository;
import com.waqiti.reconciliation.repository.SettlementDiscrepancyRepository;
import com.waqiti.reconciliation.service.ReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
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
import static org.mockito.Mockito.mock;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class SettlementReconciliationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("reconciliation_test")
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

    @Autowired
    private SettlementEventConsumer settlementEventConsumer;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private SettlementDiscrepancyRepository discrepancyRepository;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private KafkaTemplate<String, SettlementEvent> kafkaTemplate;

    @BeforeEach
    void setUp() {
        settlementRepository.deleteAll();
        discrepancyRepository.deleteAll();
    }

    @Test
    void testSettlementEventProcessing_Success() {
        String settlementId = "SETTLE-" + UUID.randomUUID();
        
        SettlementEvent event = SettlementEvent.builder()
                .settlementId(settlementId)
                .paymentProvider("STRIPE")
                .settlementAmount(new BigDecimal("5000.00"))
                .currency("USD")
                .settlementDate(LocalDate.now())
                .transactionCount(50)
                .providerFees(new BigDecimal("150.00"))
                .netSettlementAmount(new BigDecimal("4850.00"))
                .batchId("BATCH-001")
                .settlementType("STANDARD")
                .build();

        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        
        settlementEventConsumer.consumeSettlementEvent(event, acknowledgment);

        Settlement savedSettlement = settlementRepository.findByExternalSettlementId(settlementId).orElse(null);
        assertNotNull(savedSettlement, "Settlement should be saved");
        assertEquals("STRIPE", savedSettlement.getPaymentProvider());
        assertEquals(new BigDecimal("5000.00"), savedSettlement.getSettlementAmount());
        assertEquals("PENDING_RECONCILIATION", savedSettlement.getStatus());
    }

    @Test
    void testSettlementIdempotency() {
        String settlementId = "SETTLE-IDEM-" + UUID.randomUUID();
        
        SettlementEvent event = SettlementEvent.builder()
                .settlementId(settlementId)
                .paymentProvider("PAYPAL")
                .settlementAmount(new BigDecimal("3000.00"))
                .currency("USD")
                .settlementDate(LocalDate.now())
                .transactionCount(30)
                .providerFees(new BigDecimal("90.00"))
                .netSettlementAmount(new BigDecimal("2910.00"))
                .build();

        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        
        settlementEventConsumer.consumeSettlementEvent(event, acknowledgment);
        
        long countAfterFirst = settlementRepository.count();
        assertEquals(1, countAfterFirst);
        
        settlementEventConsumer.consumeSettlementEvent(event, acknowledgment);
        
        long countAfterSecond = settlementRepository.count();
        assertEquals(1, countAfterSecond, "Duplicate event should not create another settlement");
    }

    @Test
    void testDiscrepancyDetection_AmountMismatch() {
        String settlementId = "SETTLE-DISC-" + UUID.randomUUID();
        
        SettlementEvent event = SettlementEvent.builder()
                .settlementId(settlementId)
                .paymentProvider("STRIPE")
                .settlementAmount(new BigDecimal("10000.00"))
                .currency("USD")
                .settlementDate(LocalDate.now())
                .transactionCount(100)
                .providerFees(new BigDecimal("300.00"))
                .netSettlementAmount(new BigDecimal("9700.00"))
                .expectedAmount(new BigDecimal("10500.00"))
                .build();

        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        
        settlementEventConsumer.consumeSettlementEvent(event, acknowledgment);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Settlement settlement = settlementRepository.findByExternalSettlementId(settlementId).orElseThrow();
            assertEquals("DISCREPANCY_DETECTED", settlement.getStatus());
            assertTrue(settlement.isRequiresInvestigation());
            
            List<SettlementDiscrepancy> discrepancies = 
                discrepancyRepository.findBySettlementId(settlement.getId());
            assertFalse(discrepancies.isEmpty(), "Discrepancies should be detected");
            
            SettlementDiscrepancy discrepancy = discrepancies.get(0);
            assertEquals("AMOUNT_MISMATCH", discrepancy.getDiscrepancyType());
            assertNotNull(discrepancy.getDifferenceAmount());
        });
    }

    @Test
    void testSettlementReconciliation_Balanced() {
        String settlementId = "SETTLE-BAL-" + UUID.randomUUID();
        BigDecimal settlementAmount = new BigDecimal("7500.00");
        
        SettlementEvent event = SettlementEvent.builder()
                .settlementId(settlementId)
                .paymentProvider("ADYEN")
                .settlementAmount(settlementAmount)
                .currency("USD")
                .settlementDate(LocalDate.now())
                .transactionCount(75)
                .providerFees(new BigDecimal("225.00"))
                .netSettlementAmount(new BigDecimal("7275.00"))
                .expectedAmount(settlementAmount)
                .build();

        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        
        settlementEventConsumer.consumeSettlementEvent(event, acknowledgment);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Settlement settlement = settlementRepository.findByExternalSettlementId(settlementId).orElseThrow();
            assertEquals("RECONCILED", settlement.getStatus());
            assertNotNull(settlement.getReconciledAt());
            assertEquals(0, settlement.getDiscrepancyCount());
        });
    }

    @Test
    void testHighValueSettlement_RequiresInvestigation() {
        String settlementId = "SETTLE-HIGH-" + UUID.randomUUID();
        BigDecimal highValue = new BigDecimal("500000.00");
        
        SettlementEvent event = SettlementEvent.builder()
                .settlementId(settlementId)
                .paymentProvider("STRIPE")
                .settlementAmount(highValue)
                .currency("USD")
                .settlementDate(LocalDate.now())
                .transactionCount(5000)
                .providerFees(new BigDecimal("15000.00"))
                .netSettlementAmount(new BigDecimal("485000.00"))
                .build();

        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        
        settlementEventConsumer.consumeSettlementEvent(event, acknowledgment);

        Settlement settlement = settlementRepository.findByExternalSettlementId(settlementId).orElseThrow();
        assertTrue(settlement.getSettlementAmount().compareTo(new BigDecimal("100000.00")) > 0);
        assertNotNull(settlement.getPriority());
    }

    @Test
    void testSettlementWithRefundsAndChargebacks() {
        String settlementId = "SETTLE-REF-" + UUID.randomUUID();
        
        SettlementEvent event = SettlementEvent.builder()
                .settlementId(settlementId)
                .paymentProvider("PAYPAL")
                .settlementAmount(new BigDecimal("8000.00"))
                .currency("USD")
                .settlementDate(LocalDate.now())
                .transactionCount(80)
                .refundAmount(new BigDecimal("500.00"))
                .refundCount(5)
                .chargebackAmount(new BigDecimal("300.00"))
                .chargebackCount(3)
                .providerFees(new BigDecimal("240.00"))
                .netSettlementAmount(new BigDecimal("6960.00"))
                .build();

        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        
        settlementEventConsumer.consumeSettlementEvent(event, acknowledgment);

        Settlement settlement = settlementRepository.findByExternalSettlementId(settlementId).orElseThrow();
        assertEquals(new BigDecimal("500.00"), settlement.getRefundAmount());
        assertEquals(5, settlement.getRefundCount());
        assertEquals(new BigDecimal("300.00"), settlement.getChargebackAmount());
        assertEquals(3, settlement.getChargebackCount());
    }

    @Test
    void testSettlementInstructionGeneration() {
        String settlementId = "SETTLE-INST-" + UUID.randomUUID();
        
        SettlementEvent event = SettlementEvent.builder()
                .settlementId(settlementId)
                .paymentProvider("STRIPE")
                .settlementAmount(new BigDecimal("12000.00"))
                .currency("USD")
                .settlementDate(LocalDate.now())
                .transactionCount(120)
                .providerFees(new BigDecimal("360.00"))
                .netSettlementAmount(new BigDecimal("11640.00"))
                .beneficiaryAccount("ACC-123456")
                .beneficiaryBank("Wells Fargo")
                .settlementReference("SETTLE-REF-001")
                .build();

        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        
        settlementEventConsumer.consumeSettlementEvent(event, acknowledgment);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Settlement settlement = settlementRepository.findByExternalSettlementId(settlementId).orElseThrow();
            assertEquals("ACC-123456", settlement.getBeneficiaryAccount());
            assertEquals("Wells Fargo", settlement.getBeneficiaryBank());
            assertEquals("SETTLE-REF-001", settlement.getSettlementReference());
        });
    }

    @Test
    void testMultipleProvidersReconciliation() {
        String[] providers = {"STRIPE", "PAYPAL", "ADYEN", "SQUARE"};
        
        for (String provider : providers) {
            SettlementEvent event = SettlementEvent.builder()
                    .settlementId("SETTLE-" + provider + "-" + UUID.randomUUID())
                    .paymentProvider(provider)
                    .settlementAmount(new BigDecimal("5000.00"))
                    .currency("USD")
                    .settlementDate(LocalDate.now())
                    .transactionCount(50)
                    .providerFees(new BigDecimal("150.00"))
                    .netSettlementAmount(new BigDecimal("4850.00"))
                    .build();

            Acknowledgment acknowledgment = mock(Acknowledgment.class);
            settlementEventConsumer.consumeSettlementEvent(event, acknowledgment);
        }

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(4, settlementRepository.count());
            
            for (String provider : providers) {
                List<Settlement> settlements = settlementRepository.findByPaymentProvider(provider);
                assertEquals(1, settlements.size());
                assertEquals(provider, settlements.get(0).getPaymentProvider());
            }
        });
    }

    @Test
    void testConcurrentSettlementProcessing() throws Exception {
        int numberOfSettlements = 10;
        Thread[] threads = new Thread[numberOfSettlements];
        
        for (int i = 0; i < numberOfSettlements; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                SettlementEvent event = SettlementEvent.builder()
                        .settlementId("SETTLE-CONCURRENT-" + index)
                        .paymentProvider("STRIPE")
                        .settlementAmount(new BigDecimal("1000.00"))
                        .currency("USD")
                        .settlementDate(LocalDate.now())
                        .transactionCount(10)
                        .providerFees(new BigDecimal("30.00"))
                        .netSettlementAmount(new BigDecimal("970.00"))
                        .build();

                Acknowledgment acknowledgment = mock(Acknowledgment.class);
                settlementEventConsumer.consumeSettlementEvent(event, acknowledgment);
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(numberOfSettlements, settlementRepository.count());
        });
    }

    @Test
    void testSettlementPeriodTracking() {
        String settlementId = "SETTLE-PERIOD-" + UUID.randomUUID();
        LocalDate periodStart = LocalDate.now().minusDays(7);
        LocalDate periodEnd = LocalDate.now().minusDays(1);
        
        SettlementEvent event = SettlementEvent.builder()
                .settlementId(settlementId)
                .paymentProvider("STRIPE")
                .settlementAmount(new BigDecimal("35000.00"))
                .currency("USD")
                .settlementDate(LocalDate.now())
                .periodStartDate(periodStart)
                .periodEndDate(periodEnd)
                .transactionCount(350)
                .providerFees(new BigDecimal("1050.00"))
                .netSettlementAmount(new BigDecimal("33950.00"))
                .build();

        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        
        settlementEventConsumer.consumeSettlementEvent(event, acknowledgment);

        Settlement settlement = settlementRepository.findByExternalSettlementId(settlementId).orElseThrow();
        assertEquals(periodStart, settlement.getPeriodStartDate());
        assertEquals(periodEnd, settlement.getPeriodEndDate());
        assertTrue(settlement.getSettlementDate().isAfter(settlement.getPeriodEndDate()) ||
                   settlement.getSettlementDate().isEqual(settlement.getPeriodEndDate()));
    }

    @Test
    void testFraudPatternDetection_InSettlement() {
        String settlementId = "SETTLE-FRAUD-" + UUID.randomUUID();
        
        SettlementEvent event = SettlementEvent.builder()
                .settlementId(settlementId)
                .paymentProvider("STRIPE")
                .settlementAmount(new BigDecimal("10000.00"))
                .currency("USD")
                .settlementDate(LocalDate.now())
                .transactionCount(10)
                .chargebackAmount(new BigDecimal("5000.00"))
                .chargebackCount(5)
                .providerFees(new BigDecimal("300.00"))
                .netSettlementAmount(new BigDecimal("4700.00"))
                .build();

        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        
        settlementEventConsumer.consumeSettlementEvent(event, acknowledgment);

        Settlement settlement = settlementRepository.findByExternalSettlementId(settlementId).orElseThrow();
        
        double chargebackRate = settlement.getChargebackCount().doubleValue() / 
                               settlement.getTransactionCount().doubleValue();
        assertTrue(chargebackRate > 0.1, "High chargeback rate should be flagged");
        assertTrue(settlement.isRequiresInvestigation());
    }
}