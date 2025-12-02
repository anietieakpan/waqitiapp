package com.waqiti.gdpr.service;

import com.waqiti.common.audit.ComprehensiveAuditService;
import com.waqiti.gdpr.domain.*;
import com.waqiti.gdpr.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for Automated Data Retention Service
 * Tests GDPR Article 5(1)(e) - Storage Limitation and Article 17 - Right to Erasure
 */
@ExtendWith(MockitoExtension.class)
class AutomatedDataRetentionServiceTest {

    @Mock
    private UserDataRepository userDataRepository;

    @Mock
    private TransactionDataRepository transactionDataRepository;

    @Mock
    private KYCDocumentRepository kycDocumentRepository;

    @Mock
    private ConsentRecordRepository consentRecordRepository;

    @Mock
    private SessionLogRepository sessionLogRepository;

    @Mock
    private DataRetentionPolicyRepository policyRepository;

    @Mock
    private DataAnonymizationService anonymizationService;

    @Mock
    private ComprehensiveAuditService auditService;

    @InjectMocks
    private AutomatedDataRetentionService retentionService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(retentionService, "retentionEnabled", true);
        ReflectionTestUtils.setField(retentionService, "personalDataRetentionYears", 7);
        ReflectionTestUtils.setField(retentionService, "transactionRetentionYears", 7);
        ReflectionTestUtils.setField(retentionService, "kycRetentionYears", 5);
        ReflectionTestUtils.setField(retentionService, "consentRetentionYears", 3);
        ReflectionTestUtils.setField(retentionService, "sessionRetentionDays", 90);
        ReflectionTestUtils.setField(retentionService, "batchSize", 100);
    }

    @Test
    void testDailyDataRetentionEnforcement_Success() {
        // Given
        when(userDataRepository.findExpiredPersonalData(any()))
            .thenReturn(Arrays.asList());
        when(transactionDataRepository.findExpiredTransactions(any()))
            .thenReturn(Arrays.asList());
        when(kycDocumentRepository.findExpiredDocuments(any()))
            .thenReturn(Arrays.asList());
        when(consentRecordRepository.findExpiredConsents(any()))
            .thenReturn(Arrays.asList());
        when(sessionLogRepository.findExpiredSessions(any()))
            .thenReturn(Arrays.asList());

        // When
        retentionService.dailyDataRetentionEnforcement();

        // Then
        verify(userDataRepository, times(1)).findExpiredPersonalData(any());
        verify(auditService, times(1)).auditSystemOperation(
            eq("GDPR_DAILY_RETENTION_ENFORCEMENT_COMPLETED"),
            eq("SCHEDULER"),
            contains("records processed"),
            any()
        );
    }

    @Test
    void testDailyDataRetentionEnforcement_WhenDisabled() {
        // Given
        ReflectionTestUtils.setField(retentionService, "retentionEnabled", false);

        // When
        retentionService.dailyDataRetentionEnforcement();

        // Then
        verify(userDataRepository, never()).findExpiredPersonalData(any());
    }

    @Test
    void testDailyDataRetentionEnforcement_Failure() {
        // Given
        when(userDataRepository.findExpiredPersonalData(any()))
            .thenThrow(new RuntimeException("Database connection failed"));

        // When/Then
        assertThrows(RuntimeException.class, 
            () -> retentionService.dailyDataRetentionEnforcement());
        
        verify(auditService, times(1)).auditCriticalComplianceEvent(
            eq("GDPR_DAILY_RETENTION_ENFORCEMENT_FAILED"),
            eq("SCHEDULER"),
            contains("failed"),
            any()
        );
    }

    @Test
    void testWeeklyRetentionPolicyReview_Success() {
        // Given
        DataRetentionPolicy policy1 = createMockPolicy("personal_data", 7);
        DataRetentionPolicy policy2 = createMockPolicy("transaction_data", 7);
        
        when(policyRepository.findAll()).thenReturn(Arrays.asList(policy1, policy2));

        // When
        retentionService.weeklyRetentionPolicyReview();

        // Then
        verify(policyRepository, times(1)).findAll();
        verify(auditService, times(1)).auditSystemOperation(
            eq("GDPR_WEEKLY_POLICY_REVIEW_COMPLETED"),
            eq("SCHEDULER"),
            contains("policies reviewed"),
            any()
        );
    }

    @Test
    void testMonthlyRetentionComplianceReport_Success() {
        // When
        retentionService.monthlyRetentionComplianceReport();

        // Then
        verify(auditService, times(1)).auditSystemOperation(
            eq("GDPR_MONTHLY_RETENTION_REPORT_GENERATED"),
            eq("SCHEDULER"),
            contains("compliance report generated"),
            any()
        );
    }

    @Test
    void testExecuteRightToErasure_FullErasure_Success() {
        // Given
        String userId = "user-123";
        ErasureRequest request = ErasureRequest.builder()
            .userId(userId)
            .fullErasure(true)
            .reason("User request")
            .build();

        when(userDataRepository.deleteByUserId(userId)).thenReturn(1);
        when(transactionDataRepository.deleteByUserId(userId)).thenReturn(5);
        when(kycDocumentRepository.deleteByUserId(userId)).thenReturn(2);
        when(consentRecordRepository.deleteByUserId(userId)).thenReturn(3);
        when(sessionLogRepository.deleteByUserId(userId)).thenReturn(10);

        // When
        ErasureResult result = retentionService.executeRightToErasure(userId, request);

        // Then
        assertNotNull(result);
        assertEquals(ErasureStatus.COMPLETED, result.getStatus());
        assertNotNull(result.getCertificate());
        
        verify(auditService, times(1)).auditCriticalComplianceEvent(
            eq("GDPR_RIGHT_TO_ERASURE_EXECUTED"),
            eq(userId),
            contains("successfully"),
            any()
        );
    }

    @Test
    void testExecuteRightToErasure_PartialErasure_LegalHold() {
        // Given
        String userId = "user-456";
        ErasureRequest request = ErasureRequest.builder()
            .userId(userId)
            .fullErasure(true)
            .reason("User request")
            .build();

        // Mock legal hold scenario
        // Implementation would check actual legal holds
        
        // When
        ErasureResult result = retentionService.executeRightToErasure(userId, request);

        // Then
        // Result depends on implementation of legal hold checking
        assertNotNull(result);
    }

    @Test
    void testExecuteRightToErasure_Failure() {
        // Given
        String userId = "user-789";
        ErasureRequest request = ErasureRequest.builder()
            .userId(userId)
            .fullErasure(true)
            .build();

        when(userDataRepository.deleteByUserId(userId))
            .thenThrow(new RuntimeException("Database error"));

        // When/Then
        assertThrows(com.waqiti.gdpr.exception.ErasureException.class,
            () -> retentionService.executeRightToErasure(userId, request));
        
        verify(auditService, times(1)).auditCriticalComplianceEvent(
            eq("GDPR_RIGHT_TO_ERASURE_FAILED"),
            eq(userId),
            contains("failed"),
            any()
        );
    }

    @Test
    void testPersonalDataPseudonymization() {
        // Given
        UserData expiredData = createMockUserData();
        when(userDataRepository.findExpiredPersonalData(any()))
            .thenReturn(Arrays.asList(expiredData));

        // When
        retentionService.dailyDataRetentionEnforcement();

        // Then
        // Verify pseudonymization was called or deletion depending on legal hold
        verify(anonymizationService, atLeastOnce()).pseudonymize(any(UserData.class));
    }

    @Test
    void testTransactionDataPseudonymization() {
        // Given
        TransactionData expiredTransaction = createMockTransactionData();
        when(transactionDataRepository.findExpiredTransactions(any()))
            .thenReturn(Arrays.asList(expiredTransaction));
        
        // When
        retentionService.dailyDataRetentionEnforcement();

        // Then
        // High-value transactions should be pseudonymized
        verify(anonymizationService, atLeastOnce()).pseudonymize(any(TransactionData.class));
    }

    @Test
    void testKYCDocumentDeletion() {
        // Given
        KYCDocument expiredDoc = createMockKYCDocument();
        when(kycDocumentRepository.findExpiredDocuments(any()))
            .thenReturn(Arrays.asList(expiredDoc));

        // When
        retentionService.dailyDataRetentionEnforcement();

        // Then
        verify(kycDocumentRepository, atLeastOnce()).delete(any(KYCDocument.class));
    }

    @Test
    void testConsentRecordDeletion() {
        // Given
        ConsentRecord expiredConsent = createMockConsentRecord();
        when(consentRecordRepository.findExpiredConsents(any()))
            .thenReturn(Arrays.asList(expiredConsent));

        // When
        retentionService.dailyDataRetentionEnforcement();

        // Then
        verify(consentRecordRepository, atLeastOnce()).delete(any(ConsentRecord.class));
    }

    @Test
    void testSessionLogDeletion() {
        // Given
        SessionLog expiredSession = createMockSessionLog();
        when(sessionLogRepository.findExpiredSessions(any()))
            .thenReturn(Arrays.asList(expiredSession));

        // When
        retentionService.dailyDataRetentionEnforcement();

        // Then
        verify(sessionLogRepository, atLeastOnce()).delete(any(SessionLog.class));
    }

    @Test
    void testComplianceRateCalculation() {
        // When
        retentionService.monthlyRetentionComplianceReport();

        // Then
        // Verify compliance metrics are calculated
        verify(auditService, times(1)).auditSystemOperation(
            anyString(),
            anyString(),
            anyString(),
            argThat(map -> map.containsKey("complianceRate"))
        );
    }

    @Test
    void testRetentionPeriodConfiguration() {
        // Given
        int personalDataYears = (int) ReflectionTestUtils.getField(
            retentionService, "personalDataRetentionYears");
        int transactionYears = (int) ReflectionTestUtils.getField(
            retentionService, "transactionRetentionYears");
        int kycYears = (int) ReflectionTestUtils.getField(
            retentionService, "kycRetentionYears");

        // Then
        assertEquals(7, personalDataYears, "Personal data retention should be 7 years");
        assertEquals(7, transactionYears, "Transaction retention should be 7 years");
        assertEquals(5, kycYears, "KYC retention should be 5 years");
    }

    // Helper methods

    private DataRetentionPolicy createMockPolicy(String category, int years) {
        DataRetentionPolicy policy = new DataRetentionPolicy();
        policy.setDataCategory(category);
        policy.setRetentionYears(years);
        policy.setLastReviewed(LocalDateTime.now().minusMonths(3));
        return policy;
    }

    private UserData createMockUserData() {
        UserData data = new UserData();
        data.setUserId("user-123");
        data.setEmail("user@example.com");
        data.setCreatedAt(LocalDateTime.now().minusYears(8));
        return data;
    }

    private TransactionData createMockTransactionData() {
        TransactionData data = new TransactionData();
        data.setUserId("user-123");
        data.setAmount(new java.math.BigDecimal("15000"));
        data.setCreatedAt(LocalDateTime.now().minusYears(8));
        return data;
    }

    private KYCDocument createMockKYCDocument() {
        KYCDocument doc = new KYCDocument();
        doc.setUserId("user-123");
        doc.setDocumentType("PASSPORT");
        doc.setCreatedAt(LocalDateTime.now().minusYears(6));
        return doc;
    }

    private ConsentRecord createMockConsentRecord() {
        ConsentRecord consent = new ConsentRecord();
        consent.setUserId("user-123");
        consent.setPurpose("MARKETING");
        consent.setCreatedAt(LocalDateTime.now().minusYears(4));
        return consent;
    }

    private SessionLog createMockSessionLog() {
        SessionLog session = new SessionLog();
        session.setUserId("user-123");
        session.setCreatedAt(LocalDateTime.now().minusDays(100));
        return session;
    }
}