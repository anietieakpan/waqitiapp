package com.waqiti.compliance.service;

import com.waqiti.common.test.BaseUnitTest;
import com.waqiti.common.test.TestDataBuilder;
import com.waqiti.common.test.TestFixtures;
import com.waqiti.compliance.dto.SARFilingRequest;
import com.waqiti.compliance.dto.ScreeningRequest;
import com.waqiti.compliance.entity.SuspiciousActivityReport;
import com.waqiti.compliance.entity.SanctionScreeningResult;
import com.waqiti.compliance.entity.ComplianceStatus;
import com.waqiti.compliance.repository.SARRepository;
import com.waqiti.compliance.repository.ScreeningRepository;
import com.waqiti.compliance.client.OFACServiceClient;
import com.waqiti.compliance.client.FinCENClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for Compliance Service.
 *
 * Tests compliance workflows:
 * - SAR (Suspicious Activity Report) filing
 * - OFAC sanctions screening
 * - KYC/AML checks
 * - Regulatory reporting
 * - Transaction monitoring
 *
 * Critical for financial regulatory compliance (BSA/AML, PATRIOT Act, FinCEN).
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2024-10-19
 */
@DisplayName("Compliance Service Tests")
class ComplianceServiceTest extends BaseUnitTest {

    @Mock
    private SARRepository sarRepository;

    @Mock
    private ScreeningRepository screeningRepository;

    @Mock
    private OFACServiceClient ofacClient;

    @Mock
    private FinCENClient finCENClient;

    @Mock
    private ComplianceEventPublisher eventPublisher;

    @InjectMocks
    private ComplianceService complianceService;

    @Captor
    private ArgumentCaptor<SuspiciousActivityReport> sarCaptor;

    @Captor
    private ArgumentCaptor<SanctionScreeningResult> screeningCaptor;

    private UUID testUserId;
    private UUID testTransactionId;

    @BeforeEach
    @Override
    protected void setUp() {
        testUserId = TestFixtures.VERIFIED_USER_ID;
        testTransactionId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("SAR Filing Tests")
    class SARFilingTests {

        @Test
        @DisplayName("Should file SAR for suspicious transaction over threshold")
        void shouldFileSARForSuspiciousTransactionOverThreshold() {
            // Given
            SARFilingRequest request = SARFilingRequest.builder()
                    .userId(testUserId)
                    .transactionId(testTransactionId)
                    .amount(new BigDecimal("10000.00")) // Above $5000 threshold
                    .currency("USD")
                    .suspiciousActivity("Multiple large cash deposits")
                    .narrativeDescription("Subject made 5 cash deposits of $2000 each within 24 hours")
                    .build();

            SuspiciousActivityReport expectedSAR = SuspiciousActivityReport.builder()
                    .id(UUID.randomUUID())
                    .userId(testUserId)
                    .transactionId(testTransactionId)
                    .amount(request.getAmount())
                    .status(ComplianceStatus.SUBMITTED)
                    .build();

            when(sarRepository.save(any(SuspiciousActivityReport.class))).thenReturn(expectedSAR);
            when(finCENClient.submitSAR(any())).thenReturn("SAR-2024-12345");

            // When
            SuspiciousActivityReport filedSAR = complianceService.fileSAR(request);

            // Then
            assertThat(filedSAR).isNotNull();
            assertThat(filedSAR.getStatus()).isEqualTo(ComplianceStatus.SUBMITTED);

            verify(sarRepository).save(sarCaptor.capture());
            SuspiciousActivityReport capturedSAR = sarCaptor.getValue();
            assertThat(capturedSAR.getAmount()).isEqualByComparingTo(request.getAmount());
            assertThat(capturedSAR.getUserId()).isEqualTo(testUserId);

            verify(finCENClient).submitSAR(any());
            verify(eventPublisher).publishSARFiledEvent(any());
        }

        @Test
        @DisplayName("Should auto-generate SAR for transaction over $10k")
        void shouldAutoGenerateSARForLargeTransaction() {
            // Given
            BigDecimal largeAmount = new BigDecimal("15000.00");
            when(sarRepository.save(any())).thenReturn(new SuspiciousActivityReport());

            // When
            boolean sarRequired = complianceService.checkAndFileAutomaticSAR(
                    testUserId,
                    testTransactionId,
                    largeAmount,
                    "USD"
            );

            // Then
            assertThat(sarRequired).isTrue();
            verify(sarRepository).save(any());
            verify(eventPublisher).publishSARFiledEvent(any());
        }

        @Test
        @DisplayName("Should not file SAR for normal transactions")
        void shouldNotFileSARForNormalTransactions() {
            // Given
            BigDecimal normalAmount = new BigDecimal("100.00");

            // When
            boolean sarRequired = complianceService.checkAndFileAutomaticSAR(
                    testUserId,
                    testTransactionId,
                    normalAmount,
                    "USD"
            );

            // Then
            assertThat(sarRequired).isFalse();
            verify(sarRepository, never()).save(any());
        }

        @ParameterizedTest
        @CsvSource({
            "5000.00, true",   // At threshold
            "5000.01, true",   // Above threshold
            "10000.00, true",  // Well above
            "4999.99, false",  // Below threshold
            "100.00, false"    // Normal transaction
        })
        @DisplayName("Should correctly identify SAR-worthy amounts")
        void shouldCorrectlyIdentifySARWorthyAmounts(String amount, boolean shouldFileSAR) {
            // Given
            BigDecimal transactionAmount = new BigDecimal(amount);

            // When
            boolean requiresSAR = complianceService.requiresSAR(transactionAmount);

            // Then
            assertThat(requiresSAR).isEqualTo(shouldFileSAR);
        }

        @Test
        @DisplayName("Should handle SAR filing failure gracefully")
        void shouldHandleSARFilingFailureGracefully() {
            // Given
            SARFilingRequest request = SARFilingRequest.builder()
                    .userId(testUserId)
                    .transactionId(testTransactionId)
                    .amount(TestFixtures.SAR_THRESHOLD_AMOUNT)
                    .build();

            when(sarRepository.save(any())).thenReturn(new SuspiciousActivityReport());
            when(finCENClient.submitSAR(any())).thenThrow(new FinCENServiceException("FinCEN service unavailable"));

            // When
            SuspiciousActivityReport sar = complianceService.fileSAR(request);

            // Then
            verify(sarRepository).save(sarCaptor.capture());
            SuspiciousActivityReport capturedSAR = sarCaptor.getValue();
            assertThat(capturedSAR.getStatus()).isEqualTo(ComplianceStatus.PENDING); // Should be marked for retry

            verify(eventPublisher).publishSARFilingFailedEvent(any());
        }
    }

    @Nested
    @DisplayName("OFAC Sanctions Screening Tests")
    class OFACSanctionsScreeningTests {

        @Test
        @DisplayName("Should flag sanctioned entity")
        void shouldFlagSanctionedEntity() {
            // Given
            ScreeningRequest request = ScreeningRequest.builder()
                    .name(TestFixtures.SANCTIONED_ENTITY_NAME)
                    .dateOfBirth("1980-01-01")
                    .country("IR") // High-risk country
                    .build();

            when(ofacClient.screenEntity(any())).thenReturn(OFACScreeningResponse.builder()
                    .match(true)
                    .matchScore(0.95)
                    .sanctionListName("SDN")
                    .build());

            when(screeningRepository.save(any())).thenReturn(new SanctionScreeningResult());

            // When
            SanctionScreeningResult result = complianceService.screenForSanctions(request);

            // Then
            assertThat(result.isMatch()).isTrue();
            assertThat(result.getRiskLevel()).isEqualTo("HIGH");

            verify(screeningRepository).save(screeningCaptor.capture());
            SanctionScreeningResult capturedResult = screeningCaptor.getValue();
            assertThat(capturedResult.isMatch()).isTrue();

            verify(eventPublisher).publishSanctionMatchFoundEvent(any());
        }

        @Test
        @DisplayName("Should clear clean entity")
        void shouldClearCleanEntity() {
            // Given
            ScreeningRequest request = ScreeningRequest.builder()
                    .name(TestFixtures.CLEAN_ENTITY_NAME)
                    .dateOfBirth("1985-05-15")
                    .country("US")
                    .build();

            when(ofacClient.screenEntity(any())).thenReturn(OFACScreeningResponse.builder()
                    .match(false)
                    .matchScore(0.0)
                    .build());

            when(screeningRepository.save(any())).thenReturn(new SanctionScreeningResult());

            // When
            SanctionScreeningResult result = complianceService.screenForSanctions(request);

            // Then
            assertThat(result.isMatch()).isFalse();
            assertThat(result.getRiskLevel()).isEqualTo("LOW");

            verify(ofacClient).screenEntity(any());
            verify(eventPublisher, never()).publishSanctionMatchFoundEvent(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"IR", "KP", "SY", "CU"})
        @DisplayName("Should flag high-risk countries")
        void shouldFlagHighRiskCountries(String countryCode) {
            // When
            boolean isHighRisk = complianceService.isHighRiskCountry(countryCode);

            // Then
            assertThat(isHighRisk).isTrue();
        }

        @Test
        @DisplayName("Should perform enhanced due diligence for high-risk countries")
        void shouldPerformEnhancedDueDiligenceForHighRiskCountries() {
            // Given
            ScreeningRequest request = ScreeningRequest.builder()
                    .name("Test Person")
                    .country(TestFixtures.HIGH_RISK_COUNTRY)
                    .build();

            when(ofacClient.screenEntity(any())).thenReturn(OFACScreeningResponse.noMatch());
            when(screeningRepository.save(any())).thenReturn(new SanctionScreeningResult());

            // When
            SanctionScreeningResult result = complianceService.screenForSanctions(request);

            // Then
            // Even without a match, high-risk country should trigger enhanced screening
            verify(ofacClient).screenEntity(any());
            verify(ofacClient).performEnhancedDueDiligence(any());

            assertThat(result.isEnhancedDueDiligencePerformed()).isTrue();
        }

        @Test
        @DisplayName("Should handle OFAC service timeout")
        void shouldHandleOFACServiceTimeout() {
            // Given
            ScreeningRequest request = ScreeningRequest.builder()
                    .name("Test Person")
                    .build();

            when(ofacClient.screenEntity(any())).thenThrow(new ServiceTimeoutException("OFAC service timeout"));

            // When / Then
            assertThatThrownBy(() -> complianceService.screenForSanctions(request))
                    .isInstanceOf(ServiceTimeoutException.class);

            verify(eventPublisher).publishScreeningFailedEvent(any());
        }
    }

    @Nested
    @DisplayName("Transaction Monitoring Tests")
    class TransactionMonitoringTests {

        @Test
        @DisplayName("Should detect structuring pattern")
        void shouldDetectStructuringPattern() {
            // Given - Multiple transactions just below reporting threshold
            List<TransactionRecord> transactions = List.of(
                new TransactionRecord(testUserId, new BigDecimal("9900.00"), now().atZone(fixedClock.getZone()).toLocalDateTime()),
                new TransactionRecord(testUserId, new BigDecimal("9800.00"), now().atZone(fixedClock.getZone()).toLocalDateTime().plusHours(1)),
                new TransactionRecord(testUserId, new BigDecimal("9700.00"), now().atZone(fixedClock.getZone()).toLocalDateTime().plusHours(2))
            );

            // When
            StructuringAlert alert = complianceService.detectStructuring(testUserId, transactions);

            // Then
            assertThat(alert).isNotNull();
            assertThat(alert.isStructuringDetected()).isTrue();
            assertThat(alert.getTotalAmount()).isGreaterThan(new BigDecimal("29000.00"));
            assertThat(alert.getTransactionCount()).isEqualTo(3);

            verify(eventPublisher).publishStructuringAlertEvent(any());
        }

        @Test
        @DisplayName("Should detect rapid-fire transactions")
        void shouldDetectRapidFireTransactions() {
            // Given - Many transactions in short time
            List<TransactionRecord> transactions = TestDataBuilder.listOf(10,
                () -> new TransactionRecord(
                    testUserId,
                    TestDataBuilder.randomPaymentAmount(),
                    now().atZone(fixedClock.getZone()).toLocalDateTime()
                ));

            // When
            VelocityAlert alert = complianceService.checkTransactionVelocity(testUserId, transactions);

            // Then
            assertThat(alert).isNotNull();
            assertThat(alert.isVelocityExceeded()).isTrue();
            assertThat(alert.getTransactionCount()).isGreaterThanOrEqualTo(10);

            verify(eventPublisher).publishVelocityAlertEvent(any());
        }

        @Test
        @DisplayName("Should track cumulative daily transaction volume")
        void shouldTrackCumulativeDailyTransactionVolume() {
            // Given
            UUID userId = testUserId;
            LocalDateTime today = now().atZone(fixedClock.getZone()).toLocalDateTime();

            // When
            complianceService.recordTransaction(userId, new BigDecimal("1000.00"), today);
            complianceService.recordTransaction(userId, new BigDecimal("2000.00"), today.plusHours(1));
            complianceService.recordTransaction(userId, new BigDecimal("3000.00"), today.plusHours(2));

            BigDecimal dailyTotal = complianceService.getDailyTransactionTotal(userId, today.toLocalDate());

            // Then
            assertThat(dailyTotal).isEqualByComparingTo(new BigDecimal("6000.00"));
        }
    }

    @Nested
    @DisplayName("KYC/AML Compliance Tests")
    class KYCComplianceTests {

        @Test
        @DisplayName("Should require KYC for transactions over $1000")
        void shouldRequireKYCForLargeTransactions() {
            // Given
            BigDecimal amount = new BigDecimal("1500.00");

            // When
            boolean kycRequired = complianceService.isKYCRequired(testUserId, amount);

            // Then
            assertThat(kycRequired).isTrue();
        }

        @Test
        @DisplayName("Should allow small transactions without KYC")
        void shouldAllowSmallTransactionsWithoutKYC() {
            // Given
            BigDecimal amount = new BigDecimal("500.00");

            // When
            boolean kycRequired = complianceService.isKYCRequired(testUserId, amount);

            // Then
            assertThat(kycRequired).isFalse();
        }

        @Test
        @DisplayName("Should verify identity documents")
        void shouldVerifyIdentityDocuments() {
            // Given
            KYCVerificationRequest request = KYCVerificationRequest.builder()
                    .userId(testUserId)
                    .documentType("PASSPORT")
                    .documentNumber("P12345678")
                    .issueCountry("US")
                    .expiryDate("2030-12-31")
                    .build();

            when(kycServiceClient.verifyDocument(any())).thenReturn(KYCVerificationResult.verified());

            // When
            KYCVerificationResult result = complianceService.performKYC(request);

            // Then
            assertThat(result.isVerified()).isTrue();
            verify(eventPublisher).publishKYCCompletedEvent(any());
        }
    }

    @Nested
    @DisplayName("Regulatory Reporting Tests")
    class RegulatoryReportingTests {

        @Test
        @DisplayName("Should generate CTR for cash transaction over $10k")
        void shouldGenerateCTRForLargeCashTransaction() {
            // Given
            CurrencyTransactionRequest request = CurrencyTransactionRequest.builder()
                    .userId(testUserId)
                    .amount(new BigDecimal("12000.00"))
                    .transactionType("CASH_DEPOSIT")
                    .build();

            when(finCENClient.submitCTR(any())).thenReturn("CTR-2024-67890");

            // When
            CurrencyTransactionReport ctr = complianceService.fileCTR(request);

            // Then
            assertThat(ctr).isNotNull();
            assertThat(ctr.getFilingReference()).isEqualTo("CTR-2024-67890");

            verify(finCENClient).submitCTR(any());
            verify(eventPublisher).publishCTRFiledEvent(any());
        }

        @Test
        @DisplayName("Should aggregate reporting data")
        void shouldAggregateReportingData() {
            // Given
            LocalDateTime startDate = now().atZone(fixedClock.getZone()).toLocalDateTime().minusDays(30);
            LocalDateTime endDate = now().atZone(fixedClock.getZone()).toLocalDateTime();

            // When
            ComplianceReport report = complianceService.generateComplianceReport(startDate, endDate);

            // Then
            assertThat(report).isNotNull();
            assertThat(report.getTotalSARsFiled()).isGreaterThanOrEqualTo(0);
            assertThat(report.getTotalCTRsFiled()).isGreaterThanOrEqualTo(0);
            assertThat(report.getTotalScreenings()).isGreaterThanOrEqualTo(0);
        }
    }
}
