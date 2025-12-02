package com.waqiti.gdpr.service;

import com.waqiti.common.audit.AuditService;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.gdpr.client.*;
import com.waqiti.gdpr.service.CrossServiceDataErasureOrchestrator.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for Cross-Service Data Erasure Orchestration.
 *
 * Tests cover:
 * - Idempotency protection
 * - Parallel erasure execution
 * - Verification logic
 * - Incomplete erasure handling
 * - Retry mechanisms
 * - Proof of deletion generation
 * - Error handling and DPO alerting
 *
 * Target Coverage: 90%+
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Cross-Service Data Erasure Orchestrator Tests")
class CrossServiceDataErasureOrchestratorTest {

    @Mock private IdempotencyService idempotencyService;
    @Mock private UserServiceClient userServiceClient;
    @Mock private AccountServiceClient accountServiceClient;
    @Mock private WalletServiceClient walletServiceClient;
    @Mock private PaymentServiceClient paymentServiceClient;
    @Mock private TransactionServiceClient transactionServiceClient;
    @Mock private KYCServiceClient kycServiceClient;
    @Mock private ComplianceServiceClient complianceServiceClient;
    @Mock private NotificationServiceClient notificationServiceClient;
    @Mock private AuditServiceClient auditServiceClient;
    @Mock private AnalyticsServiceClient analyticsServiceClient;
    @Mock private AuditService auditService;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private GdprManualReviewQueueService manualReviewQueueService;
    @Mock private DataProtectionOfficerAlertService dpoAlertService;

    private CrossServiceDataErasureOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new CrossServiceDataErasureOrchestrator(
            userServiceClient,
            accountServiceClient,
            walletServiceClient,
            paymentServiceClient,
            transactionServiceClient,
            kycServiceClient,
            complianceServiceClient,
            notificationServiceClient,
            auditServiceClient,
            analyticsServiceClient,
            idempotencyService,
            auditService,
            kafkaTemplate,
            manualReviewQueueService,
            dpoAlertService
        );
    }

    @Nested
    @DisplayName("Idempotency Tests")
    class IdempotencyTests {

        @Test
        @DisplayName("Should prevent duplicate erasure requests")
        void testPreventDuplicateErasure() {
            // Given
            String userId = "user123";
            String requestId = "req456";
            when(idempotencyService.startOperation(anyString(), any(UUID.class), any(Duration.class)))
                .thenReturn(false); // Already processed

            // When
            DataErasureResult result = orchestrator.eraseUserDataAcrossAllServices(
                userId, requestId, ErasureType.COMPLETE
            );

            // Then
            assertEquals(ErasureStatus.DUPLICATE, result.getStatus());
            verify(idempotencyService).startOperation(
                eq("gdpr-erasure:" + userId + ":" + requestId),
                any(UUID.class),
                eq(Duration.ofDays(90))
            );
            // Verify no actual erasure was attempted
            verifyNoInteractions(userServiceClient);
        }

        @Test
        @DisplayName("Should allow first erasure request")
        void testAllowFirstErasureRequest() {
            // Given
            String userId = "user123";
            String requestId = "req456";
            when(idempotencyService.startOperation(anyString(), any(UUID.class), any(Duration.class)))
                .thenReturn(true); // Not processed yet

            // Mock successful erasure from all services
            when(userServiceClient.eraseUserData(userId, requestId)).thenReturn(true);
            when(accountServiceClient.eraseAccountData(userId, requestId)).thenReturn(true);
            when(walletServiceClient.eraseWalletData(userId, requestId)).thenReturn(true);
            when(paymentServiceClient.erasePaymentData(userId, requestId)).thenReturn(true);
            when(transactionServiceClient.eraseTransactionData(userId, requestId)).thenReturn(true);
            when(kycServiceClient.eraseKYCData(userId, requestId)).thenReturn(true);
            when(complianceServiceClient.eraseComplianceData(userId, requestId)).thenReturn(true);
            when(notificationServiceClient.eraseNotificationData(userId, requestId)).thenReturn(true);
            when(auditServiceClient.anonymizeAuditLogs(userId, requestId)).thenReturn(true);
            when(analyticsServiceClient.eraseAnalyticsData(userId, requestId)).thenReturn(true);

            // Mock verification - no data remains
            when(userServiceClient.userDataExists(userId)).thenReturn(false);
            when(accountServiceClient.accountDataExists(userId)).thenReturn(false);
            when(walletServiceClient.walletDataExists(userId)).thenReturn(false);
            when(paymentServiceClient.paymentDataExists(userId)).thenReturn(false);

            // When
            DataErasureResult result = orchestrator.eraseUserDataAcrossAllServices(
                userId, requestId, ErasureType.COMPLETE
            );

            // Then
            assertEquals(ErasureStatus.COMPLETE, result.getStatus());
            verify(idempotencyService).completeOperation(
                anyString(), any(UUID.class), any(), eq(Duration.ofDays(90))
            );
        }
    }

    @Nested
    @DisplayName("Parallel Erasure Execution Tests")
    class ParallelErasureTests {

        @Test
        @DisplayName("Should execute erasure across multiple services in parallel")
        void testParallelErasure() {
            // Given
            String userId = "user123";
            String requestId = "req456";
            when(idempotencyService.startOperation(anyString(), any(UUID.class), any(Duration.class)))
                .thenReturn(true);

            // Mock all services to succeed
            when(userServiceClient.eraseUserData(userId, requestId)).thenReturn(true);
            when(accountServiceClient.eraseAccountData(userId, requestId)).thenReturn(true);
            when(walletServiceClient.eraseWalletData(userId, requestId)).thenReturn(true);
            when(paymentServiceClient.erasePaymentData(userId, requestId)).thenReturn(true);
            when(transactionServiceClient.eraseTransactionData(userId, requestId)).thenReturn(true);
            when(kycServiceClient.eraseKYCData(userId, requestId)).thenReturn(true);
            when(complianceServiceClient.eraseComplianceData(userId, requestId)).thenReturn(true);
            when(notificationServiceClient.eraseNotificationData(userId, requestId)).thenReturn(true);
            when(auditServiceClient.anonymizeAuditLogs(userId, requestId)).thenReturn(true);
            when(analyticsServiceClient.eraseAnalyticsData(userId, requestId)).thenReturn(true);

            // Mock verification
            when(userServiceClient.userDataExists(userId)).thenReturn(false);
            when(accountServiceClient.accountDataExists(userId)).thenReturn(false);
            when(walletServiceClient.walletDataExists(userId)).thenReturn(false);
            when(paymentServiceClient.paymentDataExists(userId)).thenReturn(false);

            // When
            DataErasureResult result = orchestrator.eraseUserDataAcrossAllServices(
                userId, requestId, ErasureType.COMPLETE
            );

            // Then
            assertEquals(ErasureStatus.COMPLETE, result.getStatus());
            assertTrue(result.getServicesProcessed() > 0);

            // Verify all services were called
            verify(userServiceClient).eraseUserData(userId, requestId);
            verify(accountServiceClient).eraseAccountData(userId, requestId);
            verify(walletServiceClient).eraseWalletData(userId, requestId);
            verify(paymentServiceClient).erasePaymentData(userId, requestId);
        }

        @Test
        @DisplayName("Should handle partial service failures with retry")
        void testPartialServiceFailureWithRetry() {
            // Given
            String userId = "user123";
            String requestId = "req456";
            when(idempotencyService.startOperation(anyString(), any(UUID.class), any(Duration.class)))
                .thenReturn(true);

            // Mock one service to fail twice then succeed
            when(userServiceClient.eraseUserData(userId, requestId))
                .thenThrow(new RuntimeException("Temporary failure"))
                .thenThrow(new RuntimeException("Temporary failure"))
                .thenReturn(true);

            // Other services succeed
            when(accountServiceClient.eraseAccountData(userId, requestId)).thenReturn(true);
            when(walletServiceClient.eraseWalletData(userId, requestId)).thenReturn(true);

            // Mock verification
            when(userServiceClient.userDataExists(userId)).thenReturn(false);
            when(accountServiceClient.accountDataExists(userId)).thenReturn(false);
            when(walletServiceClient.walletDataExists(userId)).thenReturn(false);

            // When/Then
            // This should succeed after retries
            assertDoesNotThrow(() ->
                orchestrator.eraseUserDataAcrossAllServices(userId, requestId, ErasureType.COMPLETE)
            );

            // Verify retry attempts (3 attempts)
            verify(userServiceClient, atLeast(3)).eraseUserData(userId, requestId);
        }
    }

    @Nested
    @DisplayName("Verification Tests")
    class VerificationTests {

        @Test
        @DisplayName("Should verify complete erasure across all services")
        void testVerifyCompleteErasure() {
            // Given
            String userId = "user123";
            String requestId = "req456";
            when(idempotencyService.startOperation(anyString(), any(UUID.class), any(Duration.class)))
                .thenReturn(true);

            // Mock successful erasure
            when(userServiceClient.eraseUserData(userId, requestId)).thenReturn(true);
            when(accountServiceClient.eraseAccountData(userId, requestId)).thenReturn(true);
            when(walletServiceClient.eraseWalletData(userId, requestId)).thenReturn(true);
            when(paymentServiceClient.erasePaymentData(userId, requestId)).thenReturn(true);
            when(transactionServiceClient.eraseTransactionData(userId, requestId)).thenReturn(true);
            when(kycServiceClient.eraseKYCData(userId, requestId)).thenReturn(true);
            when(complianceServiceClient.eraseComplianceData(userId, requestId)).thenReturn(true);
            when(notificationServiceClient.eraseNotificationData(userId, requestId)).thenReturn(true);
            when(auditServiceClient.anonymizeAuditLogs(userId, requestId)).thenReturn(true);
            when(analyticsServiceClient.eraseAnalyticsData(userId, requestId)).thenReturn(true);

            // Mock verification - NO data remains
            when(userServiceClient.userDataExists(userId)).thenReturn(false);
            when(accountServiceClient.accountDataExists(userId)).thenReturn(false);
            when(walletServiceClient.walletDataExists(userId)).thenReturn(false);
            when(paymentServiceClient.paymentDataExists(userId)).thenReturn(false);

            // When
            DataErasureResult result = orchestrator.eraseUserDataAcrossAllServices(
                userId, requestId, ErasureType.COMPLETE
            );

            // Then
            assertEquals(ErasureStatus.COMPLETE, result.getStatus());
            assertNotNull(result.getProofOfDeletion());
            assertTrue(result.getProofOfDeletion().isVerificationComplete());
        }

        @Test
        @DisplayName("Should detect incomplete erasure and escalate to DPO")
        void testDetectIncompleteErasure() {
            // Given
            String userId = "user123";
            String requestId = "req456";
            when(idempotencyService.startOperation(anyString(), any(UUID.class), any(Duration.class)))
                .thenReturn(true);

            // Mock erasure calls
            when(userServiceClient.eraseUserData(userId, requestId)).thenReturn(true);
            when(accountServiceClient.eraseAccountData(userId, requestId)).thenReturn(true);
            when(walletServiceClient.eraseWalletData(userId, requestId)).thenReturn(true);
            when(paymentServiceClient.erasePaymentData(userId, requestId)).thenReturn(true);
            when(transactionServiceClient.eraseTransactionData(userId, requestId)).thenReturn(true);
            when(kycServiceClient.eraseKYCData(userId, requestId)).thenReturn(true);
            when(complianceServiceClient.eraseComplianceData(userId, requestId)).thenReturn(true);
            when(notificationServiceClient.eraseNotificationData(userId, requestId)).thenReturn(true);
            when(auditServiceClient.anonymizeAuditLogs(userId, requestId)).thenReturn(true);
            when(analyticsServiceClient.eraseAnalyticsData(userId, requestId)).thenReturn(true);

            // Mock verification - data STILL EXISTS in one service
            when(userServiceClient.userDataExists(userId)).thenReturn(false);
            when(accountServiceClient.accountDataExists(userId)).thenReturn(false);
            when(walletServiceClient.walletDataExists(userId)).thenReturn(true); // ← Still has data!
            when(paymentServiceClient.paymentDataExists(userId)).thenReturn(false);

            // When/Then
            assertThrows(IncompleteErasureException.class, () ->
                orchestrator.eraseUserDataAcrossAllServices(userId, requestId, ErasureType.COMPLETE)
            );

            // Verify DPO was alerted
            verify(dpoAlertService).sendUrgentAlert(
                eq("GDPR_INCOMPLETE_ERASURE"),
                contains("Incomplete data erasure"),
                anyMap()
            );

            // Verify manual review queue
            verify(manualReviewQueueService).addToQueue(
                eq(userId),
                eq(requestId),
                eq("DATA_ERASURE_FAILURE"),
                contains("Incomplete erasure")
            );
        }
    }

    @Nested
    @DisplayName("Proof of Deletion Tests")
    class ProofOfDeletionTests {

        @Test
        @DisplayName("Should generate proof of deletion with all required fields")
        void testGenerateProofOfDeletion() {
            // Given
            String userId = "user123";
            String requestId = "req456";
            when(idempotencyService.startOperation(anyString(), any(UUID.class), any(Duration.class)))
                .thenReturn(true);

            // Mock successful erasure and verification
            mockSuccessfulErasureAndVerification(userId, requestId);

            // When
            DataErasureResult result = orchestrator.eraseUserDataAcrossAllServices(
                userId, requestId, ErasureType.COMPLETE
            );

            // Then
            ProofOfDeletion proof = result.getProofOfDeletion();
            assertNotNull(proof);
            assertNotNull(proof.getProofId());
            assertEquals(userId, proof.getUserId());
            assertEquals(requestId, proof.getRequestId());
            assertNotNull(proof.getErasureDate());
            assertTrue(proof.getServicesProcessed() > 0);
            assertTrue(proof.isVerificationComplete());
            assertEquals("GDPR Article 17", proof.getComplianceStandard());
            assertNotNull(proof.getGeneratedAt());
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should mark idempotency operation as failed on error")
        void testMarkIdempotencyFailedOnError() {
            // Given
            String userId = "user123";
            String requestId = "req456";
            when(idempotencyService.startOperation(anyString(), any(UUID.class), any(Duration.class)))
                .thenReturn(true);

            // Mock all services to throw exception
            when(userServiceClient.eraseUserData(userId, requestId))
                .thenThrow(new RuntimeException("Service down"));
            when(accountServiceClient.eraseAccountData(userId, requestId))
                .thenThrow(new RuntimeException("Service down"));
            when(walletServiceClient.eraseWalletData(userId, requestId))
                .thenThrow(new RuntimeException("Service down"));

            // When/Then
            assertThrows(DataErasureException.class, () ->
                orchestrator.eraseUserDataAcrossAllServices(userId, requestId, ErasureType.COMPLETE)
            );

            // Verify idempotency marked as failed
            verify(idempotencyService).failOperation(
                anyString(),
                any(UUID.class),
                contains("RuntimeException")
            );
        }

        @Test
        @DisplayName("Should alert DPO on critical failure")
        void testAlertDPOOnCriticalFailure() {
            // Given
            String userId = "user123";
            String requestId = "req456";
            when(idempotencyService.startOperation(anyString(), any(UUID.class), any(Duration.class)))
                .thenReturn(true);

            // Mock critical failure
            when(userServiceClient.eraseUserData(userId, requestId))
                .thenThrow(new RuntimeException("Critical system failure"));

            // When/Then
            assertThrows(DataErasureException.class, () ->
                orchestrator.eraseUserDataAcrossAllServices(userId, requestId, ErasureType.COMPLETE)
            );

            // Verify DPO alert
            verify(dpoAlertService).sendUrgentAlert(
                eq("GDPR_ERASURE_FAILURE"),
                contains("CRITICAL"),
                argThat(map ->
                    map.containsKey("userId") &&
                    map.containsKey("requestId") &&
                    map.containsKey("error")
                )
            );
        }

        @Test
        @DisplayName("Should send to manual review queue on failure")
        void testSendToManualReviewOnFailure() {
            // Given
            String userId = "user123";
            String requestId = "req456";
            when(idempotencyService.startOperation(anyString(), any(UUID.class), any(Duration.class)))
                .thenReturn(true);

            // Mock failure
            when(userServiceClient.eraseUserData(userId, requestId))
                .thenThrow(new RuntimeException("Persistent failure"));

            // When/Then
            assertThrows(DataErasureException.class, () ->
                orchestrator.eraseUserDataAcrossAllServices(userId, requestId, ErasureType.COMPLETE)
            );

            // Verify manual review queue
            verify(manualReviewQueueService).addToQueue(
                eq(userId),
                eq(requestId),
                eq("DATA_ERASURE_FAILURE"),
                anyString()
            );
        }
    }

    // Helper methods

    private void mockSuccessfulErasureAndVerification(String userId, String requestId) {
        // Mock successful erasure
        when(userServiceClient.eraseUserData(userId, requestId)).thenReturn(true);
        when(accountServiceClient.eraseAccountData(userId, requestId)).thenReturn(true);
        when(walletServiceClient.eraseWalletData(userId, requestId)).thenReturn(true);
        when(paymentServiceClient.erasePaymentData(userId, requestId)).thenReturn(true);
        when(transactionServiceClient.eraseTransactionData(userId, requestId)).thenReturn(true);
        when(kycServiceClient.eraseKYCData(userId, requestId)).thenReturn(true);
        when(complianceServiceClient.eraseComplianceData(userId, requestId)).thenReturn(true);
        when(notificationServiceClient.eraseNotificationData(userId, requestId)).thenReturn(true);
        when(auditServiceClient.anonymizeAuditLogs(userId, requestId)).thenReturn(true);
        when(analyticsServiceClient.eraseAnalyticsData(userId, requestId)).thenReturn(true);

        // Mock successful verification
        when(userServiceClient.userDataExists(userId)).thenReturn(false);
        when(accountServiceClient.accountDataExists(userId)).thenReturn(false);
        when(walletServiceClient.walletDataExists(userId)).thenReturn(false);
        when(paymentServiceClient.paymentDataExists(userId)).thenReturn(false);
    }
}
