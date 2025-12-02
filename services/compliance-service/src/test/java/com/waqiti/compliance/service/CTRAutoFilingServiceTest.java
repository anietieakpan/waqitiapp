package com.waqiti.compliance.service;

import com.waqiti.common.audit.AuditService;
import com.waqiti.compliance.ctr.CTRFormData;
import com.waqiti.compliance.entity.CTR;
import com.waqiti.compliance.entity.CTRExemption;
import com.waqiti.compliance.enums.CTRStatus;
import com.waqiti.compliance.fincen.FINCENIntegrationService;
import com.waqiti.compliance.repository.CTRExemptionRepository;
import com.waqiti.compliance.repository.CTRRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Test Suite for CTR Auto-Filing Service.
 *
 * Tests BSA/FinCEN Currency Transaction Report (CTR) filing requirements:
 * - 31 U.S.C. § 5313 - Reports relating to coins and currency
 * - 31 CFR 1020.310 - Reports of transactions in currency
 * - 31 CFR 103.22 - Reports of transactions in currency
 *
 * Critical Thresholds:
 * - CTR filing required for transactions > $10,000 in cash
 * - Must be filed within 15 calendar days
 * - Aggregation required for related transactions
 *
 * @author Waqiti Compliance Engineering
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CTR Auto-Filing Service Tests")
class CTRAutoFilingServiceTest {

    @Mock
    private CTRRepository ctrRepository;

    @Mock
    private CTRExemptionRepository exemptionRepository;

    @Mock
    private FINCENIntegrationService fincenIntegrationService;

    @Mock
    private AuditService auditService;

    @Mock
    private ComplianceNotificationService notificationService;

    @InjectMocks
    private CTRAutoFilingService ctrAutoFilingService;

    @Captor
    private ArgumentCaptor<CTR> ctrCaptor;

    private static final BigDecimal CTR_THRESHOLD = new BigDecimal("10000.00");
    private static final String TEST_USER_ID = "user-123";
    private static final String TEST_ACCOUNT_ID = "account-456";

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(ctrRepository, exemptionRepository, fincenIntegrationService, auditService, notificationService);
    }

    @Nested
    @DisplayName("CTR Threshold Detection Tests")
    class ThresholdDetectionTests {

        @Test
        @DisplayName("Should create CTR when cash deposits exceed $10,000")
        void shouldCreateCTR_WhenCashDepositsExceedThreshold() {
            // Given
            BigDecimal depositAmount = new BigDecimal("15000.00");
            TransactionAggregateDTO aggregate = createAggregate(TEST_USER_ID, depositAmount, BigDecimal.ZERO);

            when(exemptionRepository.findActiveExemptionByUserId(TEST_USER_ID))
                .thenReturn(Optional.empty());
            when(ctrRepository.save(any(CTR.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            ctrAutoFilingService.processAggregateForCTR(aggregate);

            // Then
            verify(ctrRepository).save(ctrCaptor.capture());
            CTR savedCTR = ctrCaptor.getValue();

            assertThat(savedCTR).isNotNull();
            assertThat(savedCTR.getUserId()).isEqualTo(TEST_USER_ID);
            assertThat(savedCTR.getCashInAmount()).isEqualByComparingTo(depositAmount);
            assertThat(savedCTR.getStatus()).isEqualTo(CTRStatus.PENDING);
            assertThat(savedCTR.getFilingDeadline()).isAfter(LocalDateTime.now());

            verify(auditService).logComplianceEvent(
                eq("CTR_CREATED"),
                anyString(),
                anyMap()
            );
        }

        @Test
        @DisplayName("Should create CTR when cash withdrawals exceed $10,000")
        void shouldCreateCTR_WhenCashWithdrawalsExceedThreshold() {
            // Given
            BigDecimal withdrawalAmount = new BigDecimal("12500.00");
            TransactionAggregateDTO aggregate = createAggregate(TEST_USER_ID, BigDecimal.ZERO, withdrawalAmount);

            when(exemptionRepository.findActiveExemptionByUserId(TEST_USER_ID))
                .thenReturn(Optional.empty());
            when(ctrRepository.save(any(CTR.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            ctrAutoFilingService.processAggregateForCTR(aggregate);

            // Then
            verify(ctrRepository).save(ctrCaptor.capture());
            CTR savedCTR = ctrCaptor.getValue();

            assertThat(savedCTR.getCashOutAmount()).isEqualByComparingTo(withdrawalAmount);
            assertThat(savedCTR.getCashInAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should create CTR when combined cash transactions exceed $10,000")
        void shouldCreateCTR_WhenCombinedTransactionsExceedThreshold() {
            // Given
            BigDecimal cashIn = new BigDecimal("6000.00");
            BigDecimal cashOut = new BigDecimal("5000.00");
            TransactionAggregateDTO aggregate = createAggregate(TEST_USER_ID, cashIn, cashOut);

            when(exemptionRepository.findActiveExemptionByUserId(TEST_USER_ID))
                .thenReturn(Optional.empty());
            when(ctrRepository.save(any(CTR.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            ctrAutoFilingService.processAggregateForCTR(aggregate);

            // Then
            verify(ctrRepository).save(ctrCaptor.capture());
            CTR savedCTR = ctrCaptor.getValue();

            assertThat(savedCTR.getCashInAmount()).isEqualByComparingTo(cashIn);
            assertThat(savedCTR.getCashOutAmount()).isEqualByComparingTo(cashOut);
        }

        @Test
        @DisplayName("Should NOT create CTR when cash transactions below $10,000")
        void shouldNotCreateCTR_WhenBelowThreshold() {
            // Given
            BigDecimal cashIn = new BigDecimal("5000.00");
            BigDecimal cashOut = new BigDecimal("3000.00");
            TransactionAggregateDTO aggregate = createAggregate(TEST_USER_ID, cashIn, cashOut);

            // When
            ctrAutoFilingService.processAggregateForCTR(aggregate);

            // Then
            verify(ctrRepository, never()).save(any(CTR.class));
            verify(fincenIntegrationService, never()).fileCTR(any());
        }

        @Test
        @DisplayName("Should NOT create CTR when amount exactly equals $10,000 threshold")
        void shouldNotCreateCTR_WhenExactlyAtThreshold() {
            // Given - BSA requires OVER $10,000, not equal to
            BigDecimal exactThreshold = new BigDecimal("10000.00");
            TransactionAggregateDTO aggregate = createAggregate(TEST_USER_ID, exactThreshold, BigDecimal.ZERO);

            // When
            ctrAutoFilingService.processAggregateForCTR(aggregate);

            // Then
            verify(ctrRepository, never()).save(any(CTR.class));
        }

        @Test
        @DisplayName("Should create CTR when amount is one cent over $10,000")
        void shouldCreateCTR_WhenOneCentOverThreshold() {
            // Given
            BigDecimal overThreshold = new BigDecimal("10000.01");
            TransactionAggregateDTO aggregate = createAggregate(TEST_USER_ID, overThreshold, BigDecimal.ZERO);

            when(exemptionRepository.findActiveExemptionByUserId(TEST_USER_ID))
                .thenReturn(Optional.empty());
            when(ctrRepository.save(any(CTR.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            ctrAutoFilingService.processAggregateForCTR(aggregate);

            // Then
            verify(ctrRepository).save(any(CTR.class));
        }
    }

    @Nested
    @DisplayName("CTR Exemption Tests")
    class ExemptionTests {

        @Test
        @DisplayName("Should NOT file CTR when user has active exemption")
        void shouldNotFileCTR_WhenUserIsExempt() {
            // Given
            BigDecimal amount = new BigDecimal("50000.00");
            TransactionAggregateDTO aggregate = createAggregate(TEST_USER_ID, amount, BigDecimal.ZERO);

            CTRExemption exemption = new CTRExemption();
            exemption.setUserId(TEST_USER_ID);
            exemption.setExemptionType("PHASE_IN");
            exemption.setActive(true);
            exemption.setExpiryDate(LocalDate.now().plusDays(30));

            when(exemptionRepository.findActiveExemptionByUserId(TEST_USER_ID))
                .thenReturn(Optional.of(exemption));

            // When
            ctrAutoFilingService.processAggregateForCTR(aggregate);

            // Then
            verify(ctrRepository, never()).save(any(CTR.class));
            verify(auditService).logComplianceEvent(
                eq("CTR_EXEMPTION_APPLIED"),
                contains(TEST_USER_ID),
                anyMap()
            );
        }

        @Test
        @DisplayName("Should file CTR when exemption has expired")
        void shouldFileCTR_WhenExemptionExpired() {
            // Given
            BigDecimal amount = new BigDecimal("15000.00");
            TransactionAggregateDTO aggregate = createAggregate(TEST_USER_ID, amount, BigDecimal.ZERO);

            CTRExemption exemption = new CTRExemption();
            exemption.setUserId(TEST_USER_ID);
            exemption.setExpiryDate(LocalDate.now().minusDays(1)); // Expired yesterday
            exemption.setActive(false);

            when(exemptionRepository.findActiveExemptionByUserId(TEST_USER_ID))
                .thenReturn(Optional.empty()); // Repository should not return expired exemptions
            when(ctrRepository.save(any(CTR.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            ctrAutoFilingService.processAggregateForCTR(aggregate);

            // Then
            verify(ctrRepository).save(any(CTR.class));
        }

        @Test
        @DisplayName("Should file CTR when user has no exemption")
        void shouldFileCTR_WhenNoExemption() {
            // Given
            BigDecimal amount = new BigDecimal("20000.00");
            TransactionAggregateDTO aggregate = createAggregate(TEST_USER_ID, amount, BigDecimal.ZERO);

            when(exemptionRepository.findActiveExemptionByUserId(TEST_USER_ID))
                .thenReturn(Optional.empty());
            when(ctrRepository.save(any(CTR.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            ctrAutoFilingService.processAggregateForCTR(aggregate);

            // Then
            verify(ctrRepository).save(any(CTR.class));
        }
    }

    @Nested
    @DisplayName("Filing Deadline Tests")
    class FilingDeadlineTests {

        @Test
        @DisplayName("Should set filing deadline to 15 calendar days from transaction date")
        void shouldSetCorrectFilingDeadline() {
            // Given
            BigDecimal amount = new BigDecimal("15000.00");
            LocalDate transactionDate = LocalDate.of(2025, 11, 10);
            TransactionAggregateDTO aggregate = createAggregateWithDate(
                TEST_USER_ID, amount, BigDecimal.ZERO, transactionDate
            );

            when(exemptionRepository.findActiveExemptionByUserId(TEST_USER_ID))
                .thenReturn(Optional.empty());
            when(ctrRepository.save(any(CTR.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            ctrAutoFilingService.processAggregateForCTR(aggregate);

            // Then
            verify(ctrRepository).save(ctrCaptor.capture());
            CTR savedCTR = ctrCaptor.getValue();

            LocalDate expectedDeadline = transactionDate.plusDays(15);
            assertThat(savedCTR.getFilingDeadline().toLocalDate())
                .isEqualTo(expectedDeadline);
        }

        @Test
        @DisplayName("Should flag CTR as overdue when not filed within 15 days")
        void shouldFlagOverdueCTRs() {
            // Given
            LocalDateTime pastDeadline = LocalDateTime.now().minusDays(1);
            CTR overdueCTR = new CTR();
            overdueCTR.setId("ctr-123");
            overdueCTR.setFilingDeadline(pastDeadline);
            overdueCTR.setStatus(CTRStatus.PENDING);

            when(ctrRepository.findOverdueCTRs(any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(overdueCTR));

            // When
            ctrAutoFilingService.flagOverdueCTRs();

            // Then
            verify(notificationService).sendCriticalAlert(
                eq("OVERDUE_CTR"),
                contains("ctr-123"),
                anyMap()
            );
            verify(auditService).logComplianceEvent(
                eq("CTR_OVERDUE"),
                anyString(),
                anyMap()
            );
        }
    }

    @Nested
    @DisplayName("FinCEN Integration Tests")
    class FincenIntegrationTests {

        @Test
        @DisplayName("Should successfully submit CTR to FinCEN")
        void shouldSubmitCTRToFincen() {
            // Given
            CTR ctr = createTestCTR();
            ctr.setStatus(CTRStatus.PENDING);

            when(fincenIntegrationService.fileCTR(any(CTRFormData.class)))
                .thenReturn("FINCEN-ACK-12345");
            when(ctrRepository.save(any(CTR.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            ctrAutoFilingService.submitToFincen(ctr);

            // Then
            verify(fincenIntegrationService).fileCTR(any(CTRFormData.class));
            verify(ctrRepository).save(ctrCaptor.capture());

            CTR updatedCTR = ctrCaptor.getValue();
            assertThat(updatedCTR.getStatus()).isEqualTo(CTRStatus.FILED);
            assertThat(updatedCTR.getFincenAcknowledgmentId()).isEqualTo("FINCEN-ACK-12345");
            assertThat(updatedCTR.getFiledAt()).isNotNull();
        }

        @Test
        @DisplayName("Should handle FinCEN submission failure gracefully")
        void shouldHandleFincenFailure() {
            // Given
            CTR ctr = createTestCTR();
            ctr.setStatus(CTRStatus.PENDING);

            when(fincenIntegrationService.fileCTR(any(CTRFormData.class)))
                .thenThrow(new RuntimeException("FinCEN service unavailable"));
            when(ctrRepository.save(any(CTR.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            assertThatThrownBy(() -> ctrAutoFilingService.submitToFincen(ctr))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("FinCEN service unavailable");

            // Then
            verify(ctrRepository).save(ctrCaptor.capture());
            CTR updatedCTR = ctrCaptor.getValue();
            assertThat(updatedCTR.getStatus()).isEqualTo(CTRStatus.FAILED);
            assertThat(updatedCTR.getErrorMessage()).contains("FinCEN service unavailable");
        }

        @Test
        @DisplayName("Should retry failed CTR submissions")
        void shouldRetryFailedSubmissions() {
            // Given
            CTR failedCTR = createTestCTR();
            failedCTR.setStatus(CTRStatus.FAILED);
            failedCTR.setRetryCount(1);

            when(ctrRepository.findFailedCTRsForRetry())
                .thenReturn(Arrays.asList(failedCTR));
            when(fincenIntegrationService.fileCTR(any(CTRFormData.class)))
                .thenReturn("FINCEN-ACK-RETRY-123");
            when(ctrRepository.save(any(CTR.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            ctrAutoFilingService.retryFailedCTRs();

            // Then
            verify(fincenIntegrationService).fileCTR(any(CTRFormData.class));
            verify(ctrRepository).save(ctrCaptor.capture());

            CTR retriedCTR = ctrCaptor.getValue();
            assertThat(retriedCTR.getStatus()).isEqualTo(CTRStatus.FILED);
            assertThat(retriedCTR.getRetryCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should give up after maximum retry attempts")
        void shouldGiveUpAfterMaxRetries() {
            // Given
            CTR failedCTR = createTestCTR();
            failedCTR.setStatus(CTRStatus.FAILED);
            failedCTR.setRetryCount(5); // Max retries reached

            when(ctrRepository.findFailedCTRsForRetry())
                .thenReturn(Arrays.asList(failedCTR));
            when(ctrRepository.save(any(CTR.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            ctrAutoFilingService.retryFailedCTRs();

            // Then
            verify(fincenIntegrationService, never()).fileCTR(any());
            verify(notificationService).sendCriticalAlert(
                eq("CTR_MAX_RETRIES_EXCEEDED"),
                anyString(),
                anyMap()
            );
        }
    }

    @Nested
    @DisplayName("Transaction Aggregation Tests")
    class AggregationTests {

        @Test
        @DisplayName("Should aggregate multiple transactions within 24 hours")
        void shouldAggregateTransactionsWithin24Hours() {
            // Given
            LocalDate today = LocalDate.now();
            List<Transaction> transactions = Arrays.asList(
                createTransaction("tx-1", new BigDecimal("3000.00"), today),
                createTransaction("tx-2", new BigDecimal("4000.00"), today),
                createTransaction("tx-3", new BigDecimal("5000.00"), today)
            );

            when(ctrRepository.findTransactionsByUserAndDate(TEST_USER_ID, today, today.plusDays(1)))
                .thenReturn(transactions);

            // When
            BigDecimal total = ctrAutoFilingService.aggregateTransactions(TEST_USER_ID, today);

            // Then
            assertThat(total).isEqualByComparingTo(new BigDecimal("12000.00"));
        }

        @Test
        @DisplayName("Should NOT aggregate transactions across different days")
        void shouldNotAggregateAcrossDays() {
            // Given
            LocalDate today = LocalDate.now();
            LocalDate yesterday = today.minusDays(1);

            // Transactions should be aggregated separately by day
            List<Transaction> todayTxns = Arrays.asList(
                createTransaction("tx-1", new BigDecimal("6000.00"), today)
            );
            List<Transaction> yesterdayTxns = Arrays.asList(
                createTransaction("tx-2", new BigDecimal("6000.00"), yesterday)
            );

            when(ctrRepository.findTransactionsByUserAndDate(TEST_USER_ID, today, today.plusDays(1)))
                .thenReturn(todayTxns);
            when(ctrRepository.findTransactionsByUserAndDate(TEST_USER_ID, yesterday, yesterday.plusDays(1)))
                .thenReturn(yesterdayTxns);

            // When
            BigDecimal todayTotal = ctrAutoFilingService.aggregateTransactions(TEST_USER_ID, today);
            BigDecimal yesterdayTotal = ctrAutoFilingService.aggregateTransactions(TEST_USER_ID, yesterday);

            // Then
            assertThat(todayTotal).isEqualByComparingTo(new BigDecimal("6000.00"));
            assertThat(yesterdayTotal).isEqualByComparingTo(new BigDecimal("6000.00"));
            // Each day is below threshold independently, but would exceed if aggregated
        }
    }

    @Nested
    @DisplayName("Audit Trail Tests")
    class AuditTrailTests {

        @Test
        @DisplayName("Should log audit event when CTR is created")
        void shouldLogAuditEventOnCreation() {
            // Given
            BigDecimal amount = new BigDecimal("15000.00");
            TransactionAggregateDTO aggregate = createAggregate(TEST_USER_ID, amount, BigDecimal.ZERO);

            when(exemptionRepository.findActiveExemptionByUserId(TEST_USER_ID))
                .thenReturn(Optional.empty());
            when(ctrRepository.save(any(CTR.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            ctrAutoFilingService.processAggregateForCTR(aggregate);

            // Then
            verify(auditService).logComplianceEvent(
                eq("CTR_CREATED"),
                anyString(),
                argThat(map ->
                    map.containsKey("userId") &&
                    map.containsKey("amount") &&
                    map.containsKey("ctrId")
                )
            );
        }

        @Test
        @DisplayName("Should log audit event when CTR is filed")
        void shouldLogAuditEventOnFiling() {
            // Given
            CTR ctr = createTestCTR();
            when(fincenIntegrationService.fileCTR(any(CTRFormData.class)))
                .thenReturn("ACK-123");
            when(ctrRepository.save(any(CTR.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            ctrAutoFilingService.submitToFincen(ctr);

            // Then
            verify(auditService).logComplianceEvent(
                eq("CTR_FILED"),
                anyString(),
                argThat(map ->
                    map.containsKey("ctrId") &&
                    map.containsKey("acknowledgmentId")
                )
            );
        }
    }

    // Helper methods
    private TransactionAggregateDTO createAggregate(String userId, BigDecimal cashIn, BigDecimal cashOut) {
        return createAggregateWithDate(userId, cashIn, cashOut, LocalDate.now());
    }

    private TransactionAggregateDTO createAggregateWithDate(
        String userId, BigDecimal cashIn, BigDecimal cashOut, LocalDate date
    ) {
        TransactionAggregateDTO dto = new TransactionAggregateDTO();
        dto.setUserId(userId);
        dto.setCashInTotal(cashIn);
        dto.setCashOutTotal(cashOut);
        dto.setAggregationDate(date);
        return dto;
    }

    private CTR createTestCTR() {
        CTR ctr = new CTR();
        ctr.setId("ctr-test-123");
        ctr.setUserId(TEST_USER_ID);
        ctr.setAccountId(TEST_ACCOUNT_ID);
        ctr.setCashInAmount(new BigDecimal("15000.00"));
        ctr.setCashOutAmount(BigDecimal.ZERO);
        ctr.setFilingDeadline(LocalDateTime.now().plusDays(15));
        ctr.setStatus(CTRStatus.PENDING);
        return ctr;
    }

    private Transaction createTransaction(String id, BigDecimal amount, LocalDate date) {
        Transaction tx = new Transaction();
        tx.setId(id);
        tx.setAmount(amount);
        tx.setTransactionDate(date.atStartOfDay());
        return tx;
    }

    // Placeholder classes (should match actual implementation)
    static class TransactionAggregateDTO {
        private String userId;
        private BigDecimal cashInTotal;
        private BigDecimal cashOutTotal;
        private LocalDate aggregationDate;

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public BigDecimal getCashInTotal() { return cashInTotal; }
        public void setCashInTotal(BigDecimal cashInTotal) { this.cashInTotal = cashInTotal; }
        public BigDecimal getCashOutTotal() { return cashOutTotal; }
        public void setCashOutTotal(BigDecimal cashOutTotal) { this.cashOutTotal = cashOutTotal; }
        public LocalDate getAggregationDate() { return aggregationDate; }
        public void setAggregationDate(LocalDate aggregationDate) { this.aggregationDate = aggregationDate; }
    }

    static class Transaction {
        private String id;
        private BigDecimal amount;
        private LocalDateTime transactionDate;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public LocalDateTime getTransactionDate() { return transactionDate; }
        public void setTransactionDate(LocalDateTime transactionDate) { this.transactionDate = transactionDate; }
    }
}
