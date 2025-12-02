package com.waqiti.compliance.service;

import com.waqiti.common.audit.AuditService;
import com.waqiti.compliance.dto.FileSARRequest;
import com.waqiti.compliance.dto.SARResponse;
import com.waqiti.compliance.entity.SuspiciousActivityReport;
import com.waqiti.compliance.enums.SARPriority;
import com.waqiti.compliance.enums.SARStatus;
import com.waqiti.compliance.fincen.FINCENIntegrationService;
import com.waqiti.compliance.repository.SARRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Test Suite for SAR (Suspicious Activity Report) Filing Service.
 *
 * Tests compliance with:
 * - 31 U.S.C. § 5318(g) - SAR filing requirements
 * - 31 CFR 1020.320 - Reports by financial institutions of suspicious transactions
 * - FinCEN SAR Electronic Filing Requirements
 *
 * Critical Requirements:
 * - SAR must be filed for suspicious transactions ≥ $5,000
 * - Must be filed within 30 calendar days of initial detection
 * - Executive review required for amounts > $100,000
 * - Must include the 5 W's (Who, What, When, Where, Why)
 * - Cannot notify subject of SAR filing (Tipping Off prohibition)
 *
 * @author Waqiti Compliance Engineering
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SAR Filing Service Tests")
class ProductionSARFilingServiceTest {

    @Mock
    private SARRepository sarRepository;

    @Mock
    private FINCENIntegrationService fincenIntegrationService;

    @Mock
    private AuditService auditService;

    @Mock
    private ComplianceNotificationService notificationService;

    @Mock
    private CaseManagementService caseManagementService;

    @InjectMocks
    private ProductionSARFilingService sarFilingService;

    @Captor
    private ArgumentCaptor<SuspiciousActivityReport> sarCaptor;

    private static final BigDecimal SAR_THRESHOLD = new BigDecimal("5000.00");
    private static final BigDecimal EXECUTIVE_REVIEW_THRESHOLD = new BigDecimal("100000.00");
    private static final String TEST_USER_ID = "user-123";
    private static final String TEST_TRANSACTION_ID = "txn-456";
    private static final String TEST_COMPLIANCE_OFFICER = "officer-789";

    @BeforeEach
    void setUp() {
        reset(sarRepository, fincenIntegrationService, auditService, notificationService, caseManagementService);
    }

    @Nested
    @DisplayName("SAR Threshold and Filing Tests")
    class SARThresholdTests {

        @Test
        @DisplayName("Should create SAR for suspicious transaction ≥ $5,000")
        void shouldCreateSAR_WhenAmountMeetsThreshold() {
            // Given
            BigDecimal amount = new BigDecimal("5000.00");
            FileSARRequest request = createSARRequest(amount, "Structuring detected");

            when(sarRepository.save(any(SuspiciousActivityReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            SARResponse response = sarFilingService.fileSAR(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getSarId()).isNotNull();
            assertThat(response.getStatus()).isEqualTo(SARStatus.PENDING_REVIEW);

            verify(sarRepository).save(sarCaptor.capture());
            SuspiciousActivityReport sar = sarCaptor.getValue();

            assertThat(sar.getAmount()).isEqualByComparingTo(amount);
            assertThat(sar.getUserId()).isEqualTo(TEST_USER_ID);
            assertThat(sar.getTransactionId()).isEqualTo(TEST_TRANSACTION_ID);
        }

        @Test
        @DisplayName("Should NOT create SAR for transactions below $5,000")
        void shouldNotCreateSAR_WhenBelowThreshold() {
            // Given
            BigDecimal amount = new BigDecimal("4999.99");
            FileSARRequest request = createSARRequest(amount, "Low amount suspicious activity");

            // When & Then
            assertThatThrownBy(() -> sarFilingService.fileSAR(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("below SAR threshold");

            verify(sarRepository, never()).save(any(SuspiciousActivityReport.class));
        }

        @ParameterizedTest
        @CsvSource({
            "5000.00, true",
            "10000.00, true",
            "50000.00, true",
            "4999.99, false",
            "0.01, false"
        })
        @DisplayName("Should correctly validate SAR threshold amounts")
        void shouldValidateSARThresholds(BigDecimal amount, boolean shouldFile) {
            // Given
            FileSARRequest request = createSARRequest(amount, "Test suspicious activity");

            if (shouldFile) {
                when(sarRepository.save(any(SuspiciousActivityReport.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            }

            // When & Then
            if (shouldFile) {
                SARResponse response = sarFilingService.fileSAR(request);
                assertThat(response).isNotNull();
                verify(sarRepository).save(any(SuspiciousActivityReport.class));
            } else {
                assertThatThrownBy(() -> sarFilingService.fileSAR(request))
                    .isInstanceOf(IllegalArgumentException.class);
                verify(sarRepository, never()).save(any(SuspiciousActivityReport.class));
            }
        }
    }

    @Nested
    @DisplayName("Executive Review Tests")
    class ExecutiveReviewTests {

        @Test
        @DisplayName("Should require executive review for amounts > $100,000")
        void shouldRequireExecutiveReview_ForLargeAmounts() {
            // Given
            BigDecimal largeAmount = new BigDecimal("150000.00");
            FileSARRequest request = createSARRequest(largeAmount, "Large suspicious transaction");

            when(sarRepository.save(any(SuspiciousActivityReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            SARResponse response = sarFilingService.fileSAR(request);

            // Then
            verify(sarRepository).save(sarCaptor.capture());
            SuspiciousActivityReport sar = sarCaptor.getValue();

            assertThat(sar.requiresExecutiveReview()).isTrue();
            assertThat(sar.getStatus()).isEqualTo(SARStatus.PENDING_EXECUTIVE_REVIEW);
            assertThat(sar.getPriority()).isEqualTo(SARPriority.CRITICAL);

            verify(notificationService).sendExecutiveAlert(
                eq("HIGH_VALUE_SAR"),
                anyString(),
                anyMap()
            );
        }

        @Test
        @DisplayName("Should NOT require executive review for amounts ≤ $100,000")
        void shouldNotRequireExecutiveReview_ForStandardAmounts() {
            // Given
            BigDecimal standardAmount = new BigDecimal("50000.00");
            FileSARRequest request = createSARRequest(standardAmount, "Standard suspicious activity");

            when(sarRepository.save(any(SuspiciousActivityReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            SARResponse response = sarFilingService.fileSAR(request);

            // Then
            verify(sarRepository).save(sarCaptor.capture());
            SuspiciousActivityReport sar = sarCaptor.getValue();

            assertThat(sar.requiresExecutiveReview()).isFalse();
            assertThat(sar.getStatus()).isEqualTo(SARStatus.PENDING_REVIEW);

            verify(notificationService, never()).sendExecutiveAlert(anyString(), anyString(), anyMap());
        }
    }

    @Nested
    @DisplayName("The 5 W's Validation Tests")
    class FiveWsValidationTests {

        @Test
        @DisplayName("Should validate WHO is included in SAR narrative")
        void shouldValidateWho() {
            // Given
            FileSARRequest request = createSARRequest(
                new BigDecimal("10000.00"),
                "Transaction by customer"
            );
            request.setNarrative(""); // Missing WHO

            // When & Then
            assertThatThrownBy(() -> sarFilingService.fileSAR(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WHO");
        }

        @Test
        @DisplayName("Should validate WHAT is included in SAR narrative")
        void shouldValidateWhat() {
            // Given
            FileSARRequest request = createSARRequest(
                new BigDecimal("10000.00"),
                "Customer John Doe" // Has WHO, missing WHAT
            );
            request.setNarrative("Customer John Doe");

            // When & Then
            assertThatThrownBy(() -> sarFilingService.fileSAR(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WHAT");
        }

        @Test
        @DisplayName("Should validate WHEN is included in SAR narrative")
        void shouldValidateWhen() {
            // Given
            FileSARRequest request = createSARRequest(
                new BigDecimal("10000.00"),
                "Customer John Doe conducted suspicious wire transfer" // Has WHO, WHAT, missing WHEN
            );

            // When & Then
            assertThatThrownBy(() -> sarFilingService.fileSAR(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WHEN");
        }

        @Test
        @DisplayName("Should validate WHERE is included in SAR narrative")
        void shouldValidateWhere() {
            // Given
            FileSARRequest request = createSARRequest(
                new BigDecimal("10000.00"),
                "Customer John Doe conducted wire transfer on November 10, 2025" // Missing WHERE
            );

            // When & Then
            assertThatThrownBy(() -> sarFilingService.fileSAR(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WHERE");
        }

        @Test
        @DisplayName("Should validate WHY is included in SAR narrative")
        void shouldValidateWhy() {
            // Given
            FileSARRequest request = createSARRequest(
                new BigDecimal("10000.00"),
                "Customer John Doe at Main Street Branch conducted wire transfer on November 10, 2025" // Missing WHY
            );

            // When & Then
            assertThatThrownBy(() -> sarFilingService.fileSAR(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WHY");
        }

        @Test
        @DisplayName("Should accept SAR with complete 5 W's narrative")
        void shouldAcceptComplete5WsNarrative() {
            // Given
            FileSARRequest request = createSARRequest(
                new BigDecimal("10000.00"),
                "Customer John Doe (WHO) at Main Street Branch (WHERE) conducted a wire transfer (WHAT) " +
                "on November 10, 2025 (WHEN) which appears to be structuring to avoid CTR filing (WHY)"
            );

            when(sarRepository.save(any(SuspiciousActivityReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            SARResponse response = sarFilingService.fileSAR(request);

            // Then
            assertThat(response).isNotNull();
            verify(sarRepository).save(any(SuspiciousActivityReport.class));
        }
    }

    @Nested
    @DisplayName("Filing Deadline Tests")
    class FilingDeadlineTests {

        @Test
        @DisplayName("Should set filing deadline to 30 days from detection")
        void shouldSetCorrectFilingDeadline() {
            // Given
            LocalDate detectionDate = LocalDate.of(2025, 11, 10);
            FileSARRequest request = createSARRequestWithDate(
                new BigDecimal("15000.00"),
                "Complete narrative with all 5 W's",
                detectionDate
            );

            when(sarRepository.save(any(SuspiciousActivityReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            SARResponse response = sarFilingService.fileSAR(request);

            // Then
            verify(sarRepository).save(sarCaptor.capture());
            SuspiciousActivityReport sar = sarCaptor.getValue();

            LocalDate expectedDeadline = detectionDate.plusDays(30);
            assertThat(sar.getFilingDeadline().toLocalDate()).isEqualTo(expectedDeadline);
        }

        @Test
        @DisplayName("Should flag overdue SARs for escalation")
        void shouldFlagOverdueSARs() {
            // Given
            LocalDateTime pastDeadline = LocalDateTime.now().minusDays(1);
            SuspiciousActivityReport overdueSAR = new SuspiciousActivityReport();
            overdueSAR.setId("sar-123");
            overdueSAR.setFilingDeadline(pastDeadline);
            overdueSAR.setStatus(SARStatus.PENDING_REVIEW);

            when(sarRepository.findOverdueSARs(any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(overdueSAR));

            // When
            sarFilingService.flagOverdueSARs();

            // Then
            verify(notificationService).sendCriticalAlert(
                eq("OVERDUE_SAR"),
                contains("sar-123"),
                anyMap()
            );
            verify(auditService).logComplianceEvent(
                eq("SAR_FILING_OVERDUE"),
                anyString(),
                anyMap()
            );
        }
    }

    @Nested
    @DisplayName("FinCEN Submission Tests")
    class FincenSubmissionTests {

        @Test
        @DisplayName("Should successfully submit SAR to FinCEN")
        void shouldSubmitSARToFincen() {
            // Given
            SuspiciousActivityReport sar = createTestSAR();
            sar.setStatus(SARStatus.APPROVED);

            when(fincenIntegrationService.submitSAR(any()))
                .thenReturn("BSA-SAR-2025-12345");
            when(sarRepository.save(any(SuspiciousActivityReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            sarFilingService.submitToFincen(sar);

            // Then
            verify(fincenIntegrationService).submitSAR(any());
            verify(sarRepository).save(sarCaptor.capture());

            SuspiciousActivityReport updatedSAR = sarCaptor.getValue();
            assertThat(updatedSAR.getStatus()).isEqualTo(SARStatus.FILED);
            assertThat(updatedSAR.getBsaId()).isEqualTo("BSA-SAR-2025-12345");
            assertThat(updatedSAR.getFiledAt()).isNotNull();
        }

        @Test
        @DisplayName("Should handle FinCEN submission failure")
        void shouldHandleFincenSubmissionFailure() {
            // Given
            SuspiciousActivityReport sar = createTestSAR();
            sar.setStatus(SARStatus.APPROVED);

            when(fincenIntegrationService.submitSAR(any()))
                .thenThrow(new RuntimeException("FinCEN connection failed"));
            when(sarRepository.save(any(SuspiciousActivityReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            assertThatThrownBy(() -> sarFilingService.submitToFincen(sar))
                .isInstanceOf(RuntimeException.class);

            // Then
            verify(sarRepository).save(sarCaptor.capture());
            SuspiciousActivityReport updatedSAR = sarCaptor.getValue();

            assertThat(updatedSAR.getStatus()).isEqualTo(SARStatus.SUBMISSION_FAILED);
            assertThat(updatedSAR.getErrorMessage()).contains("FinCEN connection failed");
        }

        @Test
        @DisplayName("Should NOT submit SAR before approval")
        void shouldNotSubmitBeforeApproval() {
            // Given
            SuspiciousActivityReport sar = createTestSAR();
            sar.setStatus(SARStatus.PENDING_REVIEW); // Not yet approved

            // When & Then
            assertThatThrownBy(() -> sarFilingService.submitToFincen(sar))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not approved");

            verify(fincenIntegrationService, never()).submitSAR(any());
        }
    }

    @Nested
    @DisplayName("Tipping Off Prohibition Tests")
    class TippingOffTests {

        @Test
        @DisplayName("Should NOT notify subject when SAR is filed")
        void shouldNotNotifySubject_WhenSARFiled() {
            // Given
            FileSARRequest request = createSARRequest(
                new BigDecimal("25000.00"),
                "Complete SAR narrative"
            );

            when(sarRepository.save(any(SuspiciousActivityReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            sarFilingService.fileSAR(request);

            // Then - Verify NO notification sent to user
            verify(notificationService, never()).sendUserNotification(
                eq(TEST_USER_ID),
                anyString(),
                anyMap()
            );

            // Verify only compliance team is notified
            verify(notificationService).sendComplianceNotification(
                eq("NEW_SAR_FILED"),
                anyString(),
                anyMap()
            );
        }

        @Test
        @DisplayName("Should log tipping off violation if attempted")
        void shouldLogTippingOffViolation() {
            // This is a negative test - the service should NEVER allow this
            // Including this test to ensure the prohibition is enforced

            SuspiciousActivityReport sar = createTestSAR();

            // Attempt to notify user (should be blocked)
            assertThatThrownBy(() ->
                sarFilingService.notifyUserOfSAR(sar.getUserId(), sar.getId())
            )
                .isInstanceOf(IllegalOperationException.class)
                .hasMessageContaining("Tipping off prohibited");

            verify(auditService).logSecurityViolation(
                eq("TIPPING_OFF_ATTEMPT"),
                anyString(),
                anyMap()
            );
        }
    }

    @Nested
    @DisplayName("Case Management Integration Tests")
    class CaseManagementTests {

        @Test
        @DisplayName("Should create case when SAR is filed")
        void shouldCreateCase_WhenSARFiled() {
            // Given
            FileSARRequest request = createSARRequest(
                new BigDecimal("30000.00"),
                "Suspicious activity requiring investigation"
            );

            when(sarRepository.save(any(SuspiciousActivityReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
            when(caseManagementService.createCase(anyString(), anyString(), anyMap()))
                .thenReturn("CASE-12345");

            // When
            SARResponse response = sarFilingService.fileSAR(request);

            // Then
            verify(caseManagementService).createCase(
                eq("SAR_INVESTIGATION"),
                anyString(),
                anyMap()
            );

            verify(sarRepository).save(sarCaptor.capture());
            SuspiciousActivityReport sar = sarCaptor.getValue();
            assertThat(sar.getCaseId()).isEqualTo("CASE-12345");
        }
    }

    @Nested
    @DisplayName("Audit Trail Tests")
    class AuditTrailTests {

        @Test
        @DisplayName("Should maintain immutable audit trail for SAR")
        void shouldMaintainImmutableAuditTrail() {
            // Given
            FileSARRequest request = createSARRequest(
                new BigDecimal("20000.00"),
                "Complete SAR with all details"
            );

            when(sarRepository.save(any(SuspiciousActivityReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            sarFilingService.fileSAR(request);

            // Then
            verify(sarRepository).save(sarCaptor.capture());
            SuspiciousActivityReport sar = sarCaptor.getValue();

            assertThat(sar.isImmutable()).isTrue();
            assertThat(sar.getCreatedAt()).isNotNull();
            assertThat(sar.getCreatedBy()).isEqualTo(TEST_COMPLIANCE_OFFICER);

            verify(auditService).logComplianceEvent(
                eq("SAR_CREATED"),
                anyString(),
                argThat(map ->
                    map.containsKey("sarId") &&
                    map.containsKey("amount") &&
                    map.containsKey("userId")
                )
            );
        }

        @Test
        @DisplayName("Should log all SAR status changes")
        void shouldLogAllStatusChanges() {
            // Given
            SuspiciousActivityReport sar = createTestSAR();
            sar.setStatus(SARStatus.PENDING_REVIEW);

            when(sarRepository.save(any(SuspiciousActivityReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            sarFilingService.approveSAR(sar.getId(), TEST_COMPLIANCE_OFFICER);

            // Then
            verify(auditService).logComplianceEvent(
                eq("SAR_STATUS_CHANGED"),
                anyString(),
                argThat(map ->
                    map.containsKey("oldStatus") &&
                    map.containsKey("newStatus") &&
                    map.containsKey("approvedBy")
                )
            );
        }
    }

    // Helper methods
    private FileSARRequest createSARRequest(BigDecimal amount, String narrative) {
        return createSARRequestWithDate(amount, narrative, LocalDate.now());
    }

    private FileSARRequest createSARRequestWithDate(BigDecimal amount, String narrative, LocalDate detectionDate) {
        FileSARRequest request = new FileSARRequest();
        request.setUserId(TEST_USER_ID);
        request.setTransactionId(TEST_TRANSACTION_ID);
        request.setAmount(amount);
        request.setNarrative(narrative);
        request.setDetectionDate(detectionDate);
        request.setComplianceOfficer(TEST_COMPLIANCE_OFFICER);
        request.setSuspiciousActivityType("STRUCTURING");
        return request;
    }

    private SuspiciousActivityReport createTestSAR() {
        SuspiciousActivityReport sar = new SuspiciousActivityReport();
        sar.setId("sar-test-123");
        sar.setUserId(TEST_USER_ID);
        sar.setTransactionId(TEST_TRANSACTION_ID);
        sar.setAmount(new BigDecimal("25000.00"));
        sar.setNarrative("Complete SAR narrative with all 5 W's");
        sar.setFilingDeadline(LocalDateTime.now().plusDays(30));
        sar.setStatus(SARStatus.PENDING_REVIEW);
        sar.setCreatedBy(TEST_COMPLIANCE_OFFICER);
        return sar;
    }
}
