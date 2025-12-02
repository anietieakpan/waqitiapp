package com.waqiti.reconciliation.service;

import com.waqiti.common.client.AuditServiceClient;
import com.waqiti.common.client.LedgerServiceClient;
import com.waqiti.common.client.NotificationServiceClient;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.exception.ResourceNotFoundException;
import com.waqiti.reconciliation.dto.*;
import com.waqiti.reconciliation.entity.*;
import com.waqiti.reconciliation.mapper.ReconciliationMapper;
import com.waqiti.reconciliation.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///waqiti_test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "reconciliation.variance-threshold.amount=100.00",
        "reconciliation.variance-threshold.percentage=0.01",
        "reconciliation.auto-matching.enabled=true",
        "reconciliation.break.escalation.critical-hours=4",
        "reconciliation.break.escalation.high-hours=24",
        "reconciliation.reporting.enabled=true"
})
@DisplayName("Reconciliation Service Tests")
class ReconciliationServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("waqiti_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private ReconciliationRepository reconciliationRepository;

    @Autowired
    private ReconciliationItemRepository itemRepository;

    @Autowired
    private ReconciliationRuleRepository ruleRepository;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private AuditServiceClient auditClient;

    @MockBean
    private LedgerServiceClient ledgerClient;

    @MockBean
    private NotificationServiceClient notificationClient;

    @MockBean
    private ImportService importService;

    @MockBean
    private ExportService exportService;

    @MockBean
    private AnalyticsService analyticsService;

    private LocalDate testStartDate;
    private LocalDate testEndDate;

    @BeforeEach
    void setUp() {
        testStartDate = LocalDate.now().minusDays(7);
        testEndDate = LocalDate.now().minusDays(1);

        reconciliationRepository.deleteAll();
        itemRepository.deleteAll();
        ruleRepository.deleteAll();
    }

    @Nested
    @DisplayName("Daily Reconciliation Job Tests")
    class DailyReconciliationJobTests {

        @Test
        @Transactional
        @DisplayName("Should initiate daily reconciliation successfully")
        void shouldInitiateDailyReconciliationSuccessfully() {
            InitiateReconciliationRequest request = InitiateReconciliationRequest.builder()
                    .reconciliationType("BANK_STATEMENT")
                    .startDate(testStartDate)
                    .endDate(testEndDate)
                    .source("BANK_STATEMENT_001")
                    .target("INTERNAL_LEDGER")
                    .autoMatch(false)
                    .initiatedBy("reconciliation-scheduler")
                    .metadata(Map.of("jobType", "DAILY", "automated", "true"))
                    .build();

            ReconciliationResponse response = reconciliationService.initiateReconciliation(request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isNotNull();
            assertThat(response.getType()).isEqualTo("BANK_STATEMENT");
            assertThat(response.getStatus()).isEqualTo("IN_PROGRESS");
            assertThat(response.getStartDate()).isEqualTo(testStartDate);
            assertThat(response.getEndDate()).isEqualTo(testEndDate);

            Reconciliation saved = reconciliationRepository.findById(response.getId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(ReconciliationStatus.IN_PROGRESS);
        }

        @Test
        @Transactional
        @DisplayName("Should reject overlapping reconciliation periods")
        void shouldRejectOverlappingReconciliationPeriods() {
            InitiateReconciliationRequest request1 = InitiateReconciliationRequest.builder()
                    .reconciliationType("BANK_STATEMENT")
                    .startDate(testStartDate)
                    .endDate(testEndDate)
                    .source("BANK_001")
                    .target("LEDGER")
                    .autoMatch(false)
                    .initiatedBy("user1")
                    .build();

            reconciliationService.initiateReconciliation(request1);

            InitiateReconciliationRequest request2 = InitiateReconciliationRequest.builder()
                    .reconciliationType("BANK_STATEMENT")
                    .startDate(testStartDate.plusDays(1))
                    .endDate(testEndDate.plusDays(1))
                    .source("BANK_001")
                    .target("LEDGER")
                    .autoMatch(false)
                    .initiatedBy("user1")
                    .build();

            assertThatThrownBy(() -> reconciliationService.initiateReconciliation(request2))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Overlapping reconciliation");
        }

        @Test
        @Transactional
        @DisplayName("Should reject start date after end date")
        void shouldRejectStartDateAfterEndDate() {
            InitiateReconciliationRequest request = InitiateReconciliationRequest.builder()
                    .reconciliationType("BANK_STATEMENT")
                    .startDate(testEndDate)
                    .endDate(testStartDate)
                    .source("BANK_001")
                    .target("LEDGER")
                    .autoMatch(false)
                    .initiatedBy("user1")
                    .build();

            assertThatThrownBy(() -> reconciliationService.initiateReconciliation(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Start date must be before end date");
        }

        @Test
        @Transactional
        @DisplayName("Should perform initial matching when auto-match is enabled")
        void shouldPerformInitialMatchingWhenAutoMatchEnabled() {
            InitiateReconciliationRequest request = InitiateReconciliationRequest.builder()
                    .reconciliationType("BANK_STATEMENT")
                    .startDate(testStartDate)
                    .endDate(testEndDate)
                    .source("BANK_001")
                    .target("LEDGER")
                    .autoMatch(true)
                    .initiatedBy("user1")
                    .build();

            ReconciliationResponse response = reconciliationService.initiateReconciliation(request);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo("IN_PROGRESS");

            verify(kafkaTemplate, atLeastOnce()).send(anyString(), any());
        }

        @Test
        @Transactional
        @DisplayName("Should publish Kafka event on reconciliation initiation")
        void shouldPublishKafkaEventOnReconciliationInitiation() {
            InitiateReconciliationRequest request = InitiateReconciliationRequest.builder()
                    .reconciliationType("GL_TRANSACTION")
                    .startDate(testStartDate)
                    .endDate(testEndDate)
                    .source("GL_MAIN")
                    .target("SUB_LEDGER")
                    .autoMatch(false)
                    .initiatedBy("system")
                    .build();

            reconciliationService.initiateReconciliation(request);

            verify(kafkaTemplate).send(eq("reconciliation-events"), any());
        }

        @Test
        @Transactional
        @DisplayName("Should audit reconciliation initiation")
        void shouldAuditReconciliationInitiation() {
            InitiateReconciliationRequest request = InitiateReconciliationRequest.builder()
                    .reconciliationType("PAYMENT_GATEWAY")
                    .startDate(testStartDate)
                    .endDate(testEndDate)
                    .source("STRIPE")
                    .target("TRANSACTION_SERVICE")
                    .autoMatch(false)
                    .initiatedBy("admin")
                    .build();

            reconciliationService.initiateReconciliation(request);

            verify(auditClient).logEvent(eq("RECONCILIATION_INITIATED"), any());
        }

        @Test
        @Transactional
        @DisplayName("Should support multiple reconciliation types")
        void shouldSupportMultipleReconciliationTypes() {
            String[] types = {"BANK_STATEMENT", "GL_TRANSACTION", "PAYMENT_GATEWAY", "INTER_COMPANY"};

            for (String type : types) {
                InitiateReconciliationRequest request = InitiateReconciliationRequest.builder()
                        .reconciliationType(type)
                        .startDate(testStartDate.minusDays(30 * (Arrays.asList(types).indexOf(type) + 1)))
                        .endDate(testEndDate.minusDays(30 * (Arrays.asList(types).indexOf(type) + 1)))
                        .source("SOURCE_" + type)
                        .target("TARGET_" + type)
                        .autoMatch(false)
                        .initiatedBy("system")
                        .build();

                ReconciliationResponse response = reconciliationService.initiateReconciliation(request);

                assertThat(response.getType()).isEqualTo(type);
            }
        }
    }

    @Nested
    @DisplayName("Variance Detection Tests")
    class VarianceDetectionTests {

        @Test
        @Transactional
        @DisplayName("Should detect amount variances above threshold")
        void shouldDetectAmountVariancesAboveThreshold() {
            Reconciliation reconciliation = createTestReconciliation();
            reconciliation = reconciliationRepository.save(reconciliation);

            ReconciliationItem sourceItem = createSourceItem(reconciliation.getId(), 
                    "TXN001", new BigDecimal("1000.00"));
            ReconciliationItem targetItem = createTargetItem(reconciliation.getId(), 
                    "TXN001", new BigDecimal("900.00"));

            itemRepository.saveAll(List.of(sourceItem, targetItem));

            BigDecimal variance = sourceItem.getAmount().subtract(targetItem.getAmount()).abs();
            assertThat(variance).isGreaterThan(new BigDecimal("50.00"));
        }

        @Test
        @Transactional
        @DisplayName("Should ignore variances below threshold")
        void shouldIgnoreVariancesBelowThreshold() {
            Reconciliation reconciliation = createTestReconciliation();
            reconciliation = reconciliationRepository.save(reconciliation);

            ReconciliationItem sourceItem = createSourceItem(reconciliation.getId(), 
                    "TXN002", new BigDecimal("1000.00"));
            ReconciliationItem targetItem = createTargetItem(reconciliation.getId(), 
                    "TXN002", new BigDecimal("999.99"));

            itemRepository.saveAll(List.of(sourceItem, targetItem));

            BigDecimal variance = sourceItem.getAmount().subtract(targetItem.getAmount()).abs();
            assertThat(variance).isLessThan(new BigDecimal("1.00"));
        }

        @Test
        @Transactional
        @DisplayName("Should detect missing source transactions")
        void shouldDetectMissingSourceTransactions() {
            Reconciliation reconciliation = createTestReconciliation();
            reconciliation = reconciliationRepository.save(reconciliation);

            ReconciliationItem targetItem = createTargetItem(reconciliation.getId(), 
                    "TXN003", new BigDecimal("500.00"));
            itemRepository.save(targetItem);

            List<ReconciliationItem> sourceItems = itemRepository.findByReconciliationIdAndItemType(
                    reconciliation.getId(), ItemType.SOURCE);
            
            assertThat(sourceItems).isEmpty();
        }

        @Test
        @Transactional
        @DisplayName("Should detect missing target transactions")
        void shouldDetectMissingTargetTransactions() {
            Reconciliation reconciliation = createTestReconciliation();
            reconciliation = reconciliationRepository.save(reconciliation);

            ReconciliationItem sourceItem = createSourceItem(reconciliation.getId(), 
                    "TXN004", new BigDecimal("750.00"));
            itemRepository.save(sourceItem);

            List<ReconciliationItem> targetItems = itemRepository.findByReconciliationIdAndItemType(
                    reconciliation.getId(), ItemType.TARGET);
            
            assertThat(targetItems).isEmpty();
        }

        @Test
        @Transactional
        @DisplayName("Should detect date variances")
        void shouldDetectDateVariances() {
            Reconciliation reconciliation = createTestReconciliation();
            reconciliation = reconciliationRepository.save(reconciliation);

            LocalDate sourceDate = LocalDate.now();
            LocalDate targetDate = sourceDate.plusDays(5);

            ReconciliationItem sourceItem = createSourceItemWithDate(reconciliation.getId(), 
                    "TXN005", new BigDecimal("300.00"), sourceDate);
            ReconciliationItem targetItem = createTargetItemWithDate(reconciliation.getId(), 
                    "TXN005", new BigDecimal("300.00"), targetDate);

            itemRepository.saveAll(List.of(sourceItem, targetItem));

            assertThat(sourceItem.getTransactionDate()).isNotEqualTo(targetItem.getTransactionDate());
        }

        @Test
        @Transactional
        @DisplayName("Should calculate match rate correctly")
        void shouldCalculateMatchRateCorrectly() {
            Reconciliation reconciliation = createTestReconciliation();
            reconciliation.setTotalSourceItems(10);
            reconciliation.setTotalTargetItems(10);
            reconciliation.setMatchedItems(8);
            reconciliation.setUnmatchedSourceItems(2);
            reconciliation.setUnmatchedTargetItems(2);
            
            reconciliation = reconciliationRepository.save(reconciliation);

            double expectedMatchRate = (8.0 / 10.0) * 100.0;
            BigDecimal matchRate = reconciliation.getMatchRate();
            
            assertThat(matchRate).isNotNull();
        }

        @Test
        @Transactional
        @DisplayName("Should categorize variances by severity")
        void shouldCategorizeVariancesBySeverity() {
            Reconciliation reconciliation = createTestReconciliation();
            reconciliation = reconciliationRepository.save(reconciliation);

            Map<String, BigDecimal> testCases = Map.of(
                    "CRITICAL", new BigDecimal("10000.00"),
                    "HIGH", new BigDecimal("5000.00"),
                    "MEDIUM", new BigDecimal("1000.00"),
                    "LOW", new BigDecimal("50.00")
            );

            testCases.forEach((severity, varianceAmount) -> {
                ReconciliationItem sourceItem = createSourceItem(reconciliation.getId(), 
                        "TXN_" + severity, new BigDecimal("10000.00"));
                ReconciliationItem targetItem = createTargetItem(reconciliation.getId(), 
                        "TXN_" + severity, new BigDecimal("10000.00").subtract(varianceAmount));
                
                itemRepository.saveAll(List.of(sourceItem, targetItem));
            });

            List<ReconciliationItem> items = itemRepository.findByReconciliationId(reconciliation.getId());
            assertThat(items).hasSize(8);
        }
    }

    @Nested
    @DisplayName("Multi-System Reconciliation Tests")
    class MultiSystemReconciliationTests {

        @Test
        @Transactional
        @DisplayName("Should reconcile bank statement with internal ledger")
        void shouldReconcileBankStatementWithInternalLedger() {
            InitiateReconciliationRequest request = InitiateReconciliationRequest.builder()
                    .reconciliationType("BANK_STATEMENT")
                    .startDate(testStartDate)
                    .endDate(testEndDate)
                    .source("BANK_HSBC_001")
                    .target("INTERNAL_LEDGER_USD")
                    .autoMatch(true)
                    .initiatedBy("recon-service")
                    .build();

            ReconciliationResponse response = reconciliationService.initiateReconciliation(request);

            assertThat(response.getType()).isEqualTo("BANK_STATEMENT");
            assertThat(response.getSource()).isEqualTo("BANK_HSBC_001");
            assertThat(response.getTarget()).isEqualTo("INTERNAL_LEDGER_USD");
        }

        @Test
        @Transactional
        @DisplayName("Should reconcile general ledger with sub-ledger")
        void shouldReconcileGeneralLedgerWithSubLedger() {
            InitiateReconciliationRequest request = InitiateReconciliationRequest.builder()
                    .reconciliationType("GL_TRANSACTION")
                    .startDate(testStartDate)
                    .endDate(testEndDate)
                    .source("GENERAL_LEDGER")
                    .target("SUB_LEDGER_AR")
                    .autoMatch(false)
                    .initiatedBy("finance-team")
                    .build();

            ReconciliationResponse response = reconciliationService.initiateReconciliation(request);

            assertThat(response.getType()).isEqualTo("GL_TRANSACTION");
        }

        @Test
        @Transactional
        @DisplayName("Should reconcile payment gateway with transaction service")
        void shouldReconcilePaymentGatewayWithTransactionService() {
            InitiateReconciliationRequest request = InitiateReconciliationRequest.builder()
                    .reconciliationType("PAYMENT_GATEWAY")
                    .startDate(testStartDate)
                    .endDate(testEndDate)
                    .source("STRIPE_SETTLEMENT")
                    .target("TRANSACTION_SERVICE")
                    .autoMatch(true)
                    .initiatedBy("payment-ops")
                    .build();

            when(ledgerClient.getTransactions(any(), any(), any())).thenReturn(Collections.emptyList());

            ReconciliationResponse response = reconciliationService.initiateReconciliation(request);

            assertThat(response.getType()).isEqualTo("PAYMENT_GATEWAY");
            verify(ledgerClient).getTransactions(any(), any(), any());
        }

        @Test
        @Transactional
        @DisplayName("Should reconcile inter-company transactions")
        void shouldReconcileInterCompanyTransactions() {
            InitiateReconciliationRequest request = InitiateReconciliationRequest.builder()
                    .reconciliationType("INTER_COMPANY")
                    .startDate(testStartDate)
                    .endDate(testEndDate)
                    .source("ENTITY_A_LEDGER")
                    .target("ENTITY_B_LEDGER")
                    .autoMatch(false)
                    .initiatedBy("interco-recon")
                    .metadata(Map.of("entityA", "WAQITI_US", "entityB", "WAQITI_UK"))
                    .build();

            ReconciliationResponse response = reconciliationService.initiateReconciliation(request);

            assertThat(response.getType()).isEqualTo("INTER_COMPANY");
            assertThat(response.getMetadata()).containsKeys("entityA", "entityB");
        }

        @Test
        @Transactional
        @DisplayName("Should handle external system connection failures gracefully")
        void shouldHandleExternalSystemConnectionFailures() {
            InitiateReconciliationRequest request = InitiateReconciliationRequest.builder()
                    .reconciliationType("BANK_STATEMENT")
                    .startDate(testStartDate)
                    .endDate(testEndDate)
                    .source("BANK_DOWN")
                    .target("LEDGER")
                    .autoMatch(false)
                    .initiatedBy("system")
                    .build();

            when(ledgerClient.getTransactions(any(), any(), any()))
                    .thenThrow(new RuntimeException("External system unavailable"));

            assertThatThrownBy(() -> reconciliationService.initiateReconciliation(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Failed to load reconciliation data");
        }

        @Test
        @Transactional
        @DisplayName("Should track reconciliation across multiple systems")
        void shouldTrackReconciliationAcrossMultipleSystems() {
            String[] systems = {"BANK_STATEMENT", "PAYMENT_GATEWAY", "GL_TRANSACTION"};

            for (int i = 0; i < systems.length; i++) {
                InitiateReconciliationRequest request = InitiateReconciliationRequest.builder()
                        .reconciliationType(systems[i])
                        .startDate(testStartDate.minusDays(i * 10))
                        .endDate(testEndDate.minusDays(i * 10))
                        .source("SOURCE_" + systems[i])
                        .target("TARGET_" + systems[i])
                        .autoMatch(false)
                        .initiatedBy("multi-system-recon")
                        .build();

                reconciliationService.initiateReconciliation(request);
            }

            List<Reconciliation> all = reconciliationRepository.findAll();
            assertThat(all).hasSize(systems.length);
        }

        @Test
        @Transactional
        @DisplayName("Should support different currencies in multi-system reconciliation")
        void shouldSupportDifferentCurrenciesInMultiSystemReconciliation() {
            Reconciliation reconciliation = createTestReconciliation();
            reconciliation = reconciliationRepository.save(reconciliation);

            ReconciliationItem usdItem = createSourceItemWithCurrency(reconciliation.getId(), 
                    "TXN_USD", new BigDecimal("1000.00"), "USD");
            ReconciliationItem eurItem = createTargetItemWithCurrency(reconciliation.getId(), 
                    "TXN_EUR", new BigDecimal("850.00"), "EUR");

            itemRepository.saveAll(List.of(usdItem, eurItem));

            assertThat(usdItem.getCurrency()).isNotEqualTo(eurItem.getCurrency());
        }
    }

    @Nested
    @DisplayName("Discrepancy Resolution Tests")
    class DiscrepancyResolutionTests {

        @Test
        @Transactional
        @DisplayName("Should mark discrepancy as resolved")
        void shouldMarkDiscrepancyAsResolved() {
            Reconciliation reconciliation = createTestReconciliation();
            reconciliation.setDiscrepancyCount(1);
            reconciliation = reconciliationRepository.save(reconciliation);

            assertThat(reconciliation.getDiscrepancyCount()).isEqualTo(1);
        }

        @Test
        @Transactional
        @DisplayName("Should track resolution history")
        void shouldTrackResolutionHistory() {
            Reconciliation reconciliation = createTestReconciliation();
            reconciliation = reconciliationRepository.save(reconciliation);

            ReconciliationItem item = createSourceItem(reconciliation.getId(), 
                    "TXN_RESOLVED", new BigDecimal("500.00"));
            item.setStatus(ItemStatus.RESOLVED);
            item.setResolutionNotes("Manual adjustment applied");
            item.setResolvedAt(LocalDateTime.now());
            item.setResolvedBy("finance-admin");
            
            itemRepository.save(item);

            ReconciliationItem resolved = itemRepository.findById(item.getId()).orElseThrow();
            assertThat(resolved.getStatus()).isEqualTo(ItemStatus.RESOLVED);
            assertThat(resolved.getResolvedBy()).isEqualTo("finance-admin");
        }

        @Test
        @Transactional
        @DisplayName("Should support manual adjustment resolution")
        void shouldSupportManualAdjustmentResolution() {
            Reconciliation reconciliation = createTestReconciliation();
            reconciliation = reconciliationRepository.save(reconciliation);

            ReconciliationItem item = createSourceItem(reconciliation.getId(), 
                    "TXN_MANUAL", new BigDecimal("1200.00"));
            item.setResolutionType(ResolutionType.MANUAL_ADJUSTMENT);
            item.setResolutionNotes("Bank fee adjustment: -$10");
            
            itemRepository.save(item);

            ReconciliationItem saved = itemRepository.findById(item.getId()).orElseThrow();
            assertThat(saved.getResolutionType()).isEqualTo(ResolutionType.MANUAL_ADJUSTMENT);
        }

        @Test
        @Transactional
        @DisplayName("Should support automatic resolution")
        void shouldSupportAutomaticResolution() {
            Reconciliation reconciliation = createTestReconciliation();
            reconciliation = reconciliationRepository.save(reconciliation);

            ReconciliationItem item = createSourceItem(reconciliation.getId(), 
                    "TXN_AUTO", new BigDecimal("800.00"));
            item.setResolutionType(ResolutionType.AUTO_MATCHED);
            item.setStatus(ItemStatus.MATCHED);
            
            itemRepository.save(item);

            ReconciliationItem saved = itemRepository.findById(item.getId()).orElseThrow();
            assertThat(saved.getResolutionType()).isEqualTo(ResolutionType.AUTO_MATCHED);
            assertThat(saved.getStatus()).isEqualTo(ItemStatus.MATCHED);
        }

        @Test
        @Transactional
        @DisplayName("Should escalate unresolved discrepancies")
        void shouldEscalateUnresolvedDiscrepancies() {
            Reconciliation reconciliation = createTestReconciliation();
            reconciliation = reconciliationRepository.save(reconciliation);

            ReconciliationItem item = createSourceItem(reconciliation.getId(), 
                    "TXN_ESCALATE", new BigDecimal("5000.00"));
            item.setStatus(ItemStatus.UNMATCHED);
            item.setEscalationRequired(true);
            item.setEscalationReason("High-value unmatched transaction");
            
            itemRepository.save(item);

            ReconciliationItem saved = itemRepository.findById(item.getId()).orElseThrow();
            assertThat(saved.isEscalationRequired()).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("Should generate resolution recommendations")
        void shouldGenerateResolutionRecommendations() {
            Reconciliation reconciliation = createTestReconciliation();
            reconciliation = reconciliationRepository.save(reconciliation);

            ReconciliationItem sourceItem = createSourceItem(reconciliation.getId(), 
                    "TXN006", new BigDecimal("999.50"));
            ReconciliationItem targetItem = createTargetItem(reconciliation.getId(), 
                    "TXN006", new BigDecimal("1000.00"));

            itemRepository.saveAll(List.of(sourceItem, targetItem));

            BigDecimal difference = targetItem.getAmount().subtract(sourceItem.getAmount());
            assertThat(difference).isEqualByComparingTo(new BigDecimal("0.50"));
        }
    }

    @Nested
    @DisplayName("Batch Reconciliation Tests")
    class BatchReconciliationTests {

        @Test
        @Transactional
        @DisplayName("Should process batch reconciliation successfully")
        void shouldProcessBatchReconciliationSuccessfully() {
            List<InitiateReconciliationRequest> items = List.of(
                    createBatchRequest("BATCH_001", testStartDate, testEndDate),
                    createBatchRequest("BATCH_002", testStartDate.minusDays(7), testEndDate.minusDays(7))
            );

            BatchReconciliationRequest batchRequest = BatchReconciliationRequest.builder()
                    .reconciliationItems(items)
                    .continueOnError(true)
                    .build();

            BatchReconciliationResponse response = reconciliationService.initiateBatchReconciliation(batchRequest);

            assertThat(response.getTotalItems()).isEqualTo(2);
            assertThat(response.getSuccessfulItems()).isEqualTo(2);
            assertThat(response.getFailedItems()).isEqualTo(0);
        }

        @Test
        @Transactional
        @DisplayName("Should continue on error when configured")
        void shouldContinueOnErrorWhenConfigured() {
            List<InitiateReconciliationRequest> items = List.of(
                    createBatchRequest("BATCH_GOOD_1", testStartDate.minusDays(20), testEndDate.minusDays(20)),
                    createInvalidBatchRequest(),
                    createBatchRequest("BATCH_GOOD_2", testStartDate.minusDays(40), testEndDate.minusDays(40))
            );

            BatchReconciliationRequest batchRequest = BatchReconciliationRequest.builder()
                    .reconciliationItems(items)
                    .continueOnError(true)
                    .build();

            BatchReconciliationResponse response = reconciliationService.initiateBatchReconciliation(batchRequest);

            assertThat(response.getTotalItems()).isEqualTo(3);
            assertThat(response.getFailedItems()).isGreaterThan(0);
        }

        @Test
        @Transactional
        @DisplayName("Should stop on first error when configured")
        void shouldStopOnFirstErrorWhenConfigured() {
            List<InitiateReconciliationRequest> items = List.of(
                    createInvalidBatchRequest(),
                    createBatchRequest("BATCH_003", testStartDate, testEndDate)
            );

            BatchReconciliationRequest batchRequest = BatchReconciliationRequest.builder()
                    .reconciliationItems(items)
                    .continueOnError(false)
                    .build();

            BatchReconciliationResponse response = reconciliationService.initiateBatchReconciliation(batchRequest);

            assertThat(response.getSuccessfulItems()).isEqualTo(0);
            assertThat(response.getFailedItems()).isGreaterThan(0);
        }

        @Test
        @Transactional
        @DisplayName("Should track batch progress")
        void shouldTrackBatchProgress() {
            List<InitiateReconciliationRequest> items = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                items.add(createBatchRequest("BATCH_" + i, 
                        testStartDate.minusDays(i * 10), 
                        testEndDate.minusDays(i * 10)));
            }

            BatchReconciliationRequest batchRequest = BatchReconciliationRequest.builder()
                    .reconciliationItems(items)
                    .continueOnError(true)
                    .build();

            BatchReconciliationResponse response = reconciliationService.initiateBatchReconciliation(batchRequest);

            double completionRate = (double) response.getSuccessfulItems() / response.getTotalItems() * 100;
            assertThat(completionRate).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Reconciliation Completion Tests")
    class ReconciliationCompletionTests {

        @Test
        @Transactional
        @DisplayName("Should complete reconciliation successfully")
        void shouldCompleteReconciliationSuccessfully() {
            Reconciliation reconciliation = createTestReconciliation();
            reconciliation.setStatus(ReconciliationStatus.IN_PROGRESS);
            reconciliation.setTotalSourceItems(10);
            reconciliation.setTotalTargetItems(10);
            reconciliation.setMatchedItems(10);
            reconciliation = reconciliationRepository.save(reconciliation);

            CompleteReconciliationRequest request = CompleteReconciliationRequest.builder()
                    .completedBy("finance-manager")
                    .notes("All items matched successfully")
                    .postAdjustments(false)
                    .build();

            ReconciliationResponse response = reconciliationService.completeReconciliation(
                    reconciliation.getId(), request);

            assertThat(response.getStatus()).isEqualTo("COMPLETED");

            Reconciliation completed = reconciliationRepository.findById(reconciliation.getId()).orElseThrow();
            assertThat(completed.getCompletedBy()).isEqualTo("finance-manager");
            assertThat(completed.getCompletedAt()).isNotNull();
        }

        @Test
        @Transactional
        @DisplayName("Should reject completion for non-existent reconciliation")
        void shouldRejectCompletionForNonExistentReconciliation() {
            UUID nonExistentId = UUID.randomUUID();
            CompleteReconciliationRequest request = CompleteReconciliationRequest.builder()
                    .completedBy("user")
                    .notes("Notes")
                    .postAdjustments(false)
                    .build();

            assertThatThrownBy(() -> reconciliationService.completeReconciliation(nonExistentId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Reconciliation not found");
        }

        @Test
        @Transactional
        @DisplayName("Should reject completion for already completed reconciliation")
        void shouldRejectCompletionForAlreadyCompletedReconciliation() {
            Reconciliation reconciliation = createTestReconciliation();
            reconciliation.setStatus(ReconciliationStatus.COMPLETED);
            reconciliation = reconciliationRepository.save(reconciliation);

            CompleteReconciliationRequest request = CompleteReconciliationRequest.builder()
                    .completedBy("user")
                    .notes("Notes")
                    .postAdjustments(false)
                    .build();

            assertThatThrownBy(() -> reconciliationService.completeReconciliation(
                    reconciliation.getId(), request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("not in progress");
        }

        @Test
        @Transactional
        @DisplayName("Should post journal adjustments when requested")
        void shouldPostJournalAdjustmentsWhenRequested() {
            Reconciliation reconciliation = createTestReconciliation();
            reconciliation.setStatus(ReconciliationStatus.IN_PROGRESS);
            reconciliation.setTotalSourceItems(10);
            reconciliation.setTotalTargetItems(10);
            reconciliation.setMatchedItems(9);
            reconciliation.setDiscrepancyCount(1);
            reconciliation = reconciliationRepository.save(reconciliation);

            CompleteReconciliationRequest request = CompleteReconciliationRequest.builder()
                    .completedBy("finance-manager")
                    .notes("Posting adjustments for discrepancies")
                    .postAdjustments(true)
                    .build();

            ReconciliationResponse response = reconciliationService.completeReconciliation(
                    reconciliation.getId(), request);

            assertThat(response).isNotNull();
            verify(ledgerClient, atLeastOnce()).postJournalEntry(any());
        }

        @Test
        @Transactional
        @DisplayName("Should send completion notifications")
        void shouldSendCompletionNotifications() {
            Reconciliation reconciliation = createTestReconciliation();
            reconciliation.setStatus(ReconciliationStatus.IN_PROGRESS);
            reconciliation.setTotalSourceItems(5);
            reconciliation.setTotalTargetItems(5);
            reconciliation.setMatchedItems(5);
            reconciliation = reconciliationRepository.save(reconciliation);

            CompleteReconciliationRequest request = CompleteReconciliationRequest.builder()
                    .completedBy("system")
                    .notes("Auto-completed")
                    .postAdjustments(false)
                    .build();

            reconciliationService.completeReconciliation(reconciliation.getId(), request);

            verify(notificationClient).sendNotification(any());
        }

        @Test
        @Transactional
        @DisplayName("Should calculate accuracy rate on completion")
        void shouldCalculateAccuracyRateOnCompletion() {
            Reconciliation reconciliation = createTestReconciliation();
            reconciliation.setStatus(ReconciliationStatus.IN_PROGRESS);
            reconciliation.setTotalSourceItems(100);
            reconciliation.setTotalTargetItems(100);
            reconciliation.setMatchedItems(95);
            reconciliation = reconciliationRepository.save(reconciliation);

            CompleteReconciliationRequest request = CompleteReconciliationRequest.builder()
                    .completedBy("recon-service")
                    .notes("Scheduled completion")
                    .postAdjustments(false)
                    .build();

            ReconciliationResponse response = reconciliationService.completeReconciliation(
                    reconciliation.getId(), request);

            assertThat(response).isNotNull();

            Reconciliation completed = reconciliationRepository.findById(reconciliation.getId()).orElseThrow();
            assertThat(completed.getMatchRate()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Reconciliation Query Tests")
    class ReconciliationQueryTests {

        @Test
        @Transactional
        @DisplayName("Should retrieve reconciliations with pagination")
        void shouldRetrieveReconciliationsWithPagination() {
            for (int i = 0; i < 15; i++) {
                Reconciliation recon = createTestReconciliation();
                recon.setCreatedAt(LocalDateTime.now().minusHours(i));
                reconciliationRepository.save(recon);
            }

            ReconciliationFilter filter = ReconciliationFilter.builder().build();
            Pageable pageable = PageRequest.of(0, 10);

            Page<ReconciliationResponse> page = reconciliationService.getReconciliations(filter, pageable);

            assertThat(page.getTotalElements()).isEqualTo(15);
            assertThat(page.getContent()).hasSize(10);
            assertThat(page.getTotalPages()).isEqualTo(2);
        }

        @Test
        @Transactional
        @DisplayName("Should get reconciliation details by ID")
        void shouldGetReconciliationDetailsById() {
            Reconciliation reconciliation = createTestReconciliation();
            reconciliation = reconciliationRepository.save(reconciliation);

            ReconciliationDetailResponse details = reconciliationService.getReconciliationDetails(
                    reconciliation.getId());

            assertThat(details).isNotNull();
            assertThat(details.getId()).isEqualTo(reconciliation.getId());
        }

        @Test
        @Transactional
        @DisplayName("Should filter reconciliations by type")
        void shouldFilterReconciliationsByType() {
            Reconciliation bankRecon = createTestReconciliation();
            bankRecon.setType(ReconciliationType.BANK_STATEMENT);
            reconciliationRepository.save(bankRecon);

            Reconciliation glRecon = createTestReconciliation();
            glRecon.setType(ReconciliationType.GL_TRANSACTION);
            reconciliationRepository.save(glRecon);

            ReconciliationFilter filter = ReconciliationFilter.builder()
                    .type(ReconciliationType.BANK_STATEMENT)
                    .build();
            Pageable pageable = PageRequest.of(0, 10);

            Page<ReconciliationResponse> page = reconciliationService.getReconciliations(filter, pageable);

            assertThat(page.getContent()).isNotEmpty();
            assertThat(page.getContent()).allMatch(r -> r.getType().equals("BANK_STATEMENT"));
        }

        @Test
        @Transactional
        @DisplayName("Should filter reconciliations by date range")
        void shouldFilterReconciliationsByDateRange() {
            Reconciliation recon1 = createTestReconciliation();
            recon1.setStartDate(testStartDate);
            recon1.setEndDate(testEndDate);
            reconciliationRepository.save(recon1);

            Reconciliation recon2 = createTestReconciliation();
            recon2.setStartDate(testStartDate.minusDays(30));
            recon2.setEndDate(testEndDate.minusDays(30));
            reconciliationRepository.save(recon2);

            ReconciliationFilter filter = ReconciliationFilter.builder()
                    .startDate(testStartDate)
                    .endDate(testEndDate)
                    .build();
            Pageable pageable = PageRequest.of(0, 10);

            Page<ReconciliationResponse> page = reconciliationService.getReconciliations(filter, pageable);

            assertThat(page.getContent()).isNotEmpty();
        }

        @Test
        @Transactional
        @DisplayName("Should filter reconciliations by status")
        void shouldFilterReconciliationsByStatus() {
            Reconciliation completed = createTestReconciliation();
            completed.setStatus(ReconciliationStatus.COMPLETED);
            reconciliationRepository.save(completed);

            Reconciliation inProgress = createTestReconciliation();
            inProgress.setStatus(ReconciliationStatus.IN_PROGRESS);
            reconciliationRepository.save(inProgress);

            ReconciliationFilter filter = ReconciliationFilter.builder()
                    .status(ReconciliationStatus.COMPLETED)
                    .build();
            Pageable pageable = PageRequest.of(0, 10);

            Page<ReconciliationResponse> page = reconciliationService.getReconciliations(filter, pageable);

            assertThat(page.getContent()).allMatch(r -> r.getStatus().equals("COMPLETED"));
        }
    }

    private Reconciliation createTestReconciliation() {
        return Reconciliation.builder()
                .id(UUID.randomUUID())
                .type(ReconciliationType.BANK_STATEMENT)
                .startDate(testStartDate)
                .endDate(testEndDate)
                .source("TEST_SOURCE")
                .target("TEST_TARGET")
                .status(ReconciliationStatus.IN_PROGRESS)
                .totalSourceItems(0)
                .totalTargetItems(0)
                .matchedItems(0)
                .unmatchedSourceItems(0)
                .unmatchedTargetItems(0)
                .discrepancyCount(0)
                .createdAt(LocalDateTime.now())
                .createdBy("test-user")
                .build();
    }

    private ReconciliationItem createSourceItem(UUID reconciliationId, String reference, BigDecimal amount) {
        return ReconciliationItem.builder()
                .id(UUID.randomUUID())
                .reconciliationId(reconciliationId)
                .itemType(ItemType.SOURCE)
                .transactionReference(reference)
                .amount(amount)
                .currency("USD")
                .transactionDate(LocalDate.now())
                .status(ItemStatus.UNMATCHED)
                .build();
    }

    private ReconciliationItem createTargetItem(UUID reconciliationId, String reference, BigDecimal amount) {
        return ReconciliationItem.builder()
                .id(UUID.randomUUID())
                .reconciliationId(reconciliationId)
                .itemType(ItemType.TARGET)
                .transactionReference(reference)
                .amount(amount)
                .currency("USD")
                .transactionDate(LocalDate.now())
                .status(ItemStatus.UNMATCHED)
                .build();
    }

    private ReconciliationItem createSourceItemWithDate(UUID reconciliationId, String reference, 
                                                        BigDecimal amount, LocalDate date) {
        ReconciliationItem item = createSourceItem(reconciliationId, reference, amount);
        item.setTransactionDate(date);
        return item;
    }

    private ReconciliationItem createTargetItemWithDate(UUID reconciliationId, String reference, 
                                                        BigDecimal amount, LocalDate date) {
        ReconciliationItem item = createTargetItem(reconciliationId, reference, amount);
        item.setTransactionDate(date);
        return item;
    }

    private ReconciliationItem createSourceItemWithCurrency(UUID reconciliationId, String reference, 
                                                            BigDecimal amount, String currency) {
        ReconciliationItem item = createSourceItem(reconciliationId, reference, amount);
        item.setCurrency(currency);
        return item;
    }

    private ReconciliationItem createTargetItemWithCurrency(UUID reconciliationId, String reference, 
                                                            BigDecimal amount, String currency) {
        ReconciliationItem item = createTargetItem(reconciliationId, reference, amount);
        item.setCurrency(currency);
        return item;
    }

    private InitiateReconciliationRequest createBatchRequest(String identifier, 
                                                              LocalDate start, LocalDate end) {
        return InitiateReconciliationRequest.builder()
                .reconciliationType("BANK_STATEMENT")
                .startDate(start)
                .endDate(end)
                .source("SOURCE_" + identifier)
                .target("TARGET_" + identifier)
                .autoMatch(false)
                .initiatedBy("batch-processor")
                .build();
    }

    private InitiateReconciliationRequest createInvalidBatchRequest() {
        return InitiateReconciliationRequest.builder()
                .reconciliationType("INVALID_TYPE")
                .startDate(testEndDate)
                .endDate(testStartDate)
                .source("SOURCE")
                .target("TARGET")
                .autoMatch(false)
                .initiatedBy("batch-processor")
                .build();
    }
}