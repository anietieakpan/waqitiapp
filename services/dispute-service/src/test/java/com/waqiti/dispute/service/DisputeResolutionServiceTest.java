package com.waqiti.dispute.service;

import com.waqiti.dispute.dto.CreateDisputeRequest;
import com.waqiti.dispute.dto.DisputeDTO;
import com.waqiti.dispute.entity.Dispute;
import com.waqiti.dispute.entity.DisputeStatus;
import com.waqiti.dispute.repository.DisputeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DisputeResolutionService
 *
 * Tests core dispute resolution business logic
 *
 * @author Waqiti Production Team
 * @version 2.0.0-PRODUCTION
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DisputeResolutionService Tests")
class DisputeResolutionServiceTest {

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private DisputeAnalysisService analysisService;

    @Mock
    private DisputeNotificationService notificationService;

    @InjectMocks
    private DisputeResolutionService resolutionService;

    private Dispute testDispute;
    private CreateDisputeRequest testRequest;

    @BeforeEach
    void setUp() {
        // Setup test dispute
        testDispute = Dispute.builder()
                .disputeId(UUID.randomUUID().toString())
                .transactionId("txn-123")
                .userId("user-456")
                .merchantId("merchant-789")
                .disputeAmount(new BigDecimal("100.00"))
                .currency("USD")
                .status(DisputeStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build();

        // Setup test request
        testRequest = CreateDisputeRequest.builder()
                .transactionId("txn-123")
                .initiatorId("user-456")
                .disputeType("UNAUTHORIZED_TRANSACTION")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .description("Unauthorized charge on my account")
                .build();
    }

    @Test
    @DisplayName("Should create dispute successfully")
    void testCreateDispute_Success() {
        // Given
        when(disputeRepository.save(any(Dispute.class))).thenReturn(testDispute);

        // When
        DisputeDTO result = resolutionService.createDispute(testRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getDisputeId()).isNotNull();
        assertThat(result.getTransactionId()).isEqualTo("txn-123");
        assertThat(result.getUserId()).isEqualTo("user-456");
        assertThat(result.getStatus()).isEqualTo(DisputeStatus.OPEN);

        verify(disputeRepository).save(argThat(dispute ->
                dispute.getTransactionId().equals("txn-123") &&
                dispute.getUserId().equals("user-456") &&
                dispute.getStatus() == DisputeStatus.OPEN
        ));
    }

    @Test
    @DisplayName("Should validate required fields when creating dispute")
    void testCreateDispute_MissingTransactionId_ThrowsException() {
        // Given
        testRequest.setTransactionId(null);

        // When & Then
        assertThatThrownBy(() -> resolutionService.createDispute(testRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transaction ID");
    }

    @Test
    @DisplayName("Should get dispute by ID successfully")
    void testGetDispute_Success() {
        // Given
        String disputeId = testDispute.getDisputeId();
        String userId = testDispute.getUserId();
        when(disputeRepository.findByDisputeId(disputeId)).thenReturn(Optional.of(testDispute));

        // When
        DisputeDTO result = resolutionService.getDispute(disputeId, userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getDisputeId()).isEqualTo(disputeId);
        assertThat(result.getUserId()).isEqualTo(userId);

        verify(disputeRepository).findByDisputeId(disputeId);
    }

    @Test
    @DisplayName("Should throw exception when dispute not found")
    void testGetDispute_NotFound_ThrowsException() {
        // Given
        String disputeId = "non-existent";
        when(disputeRepository.findByDisputeId(disputeId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> resolutionService.getDispute(disputeId, "user-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("Should reject access when user ID mismatch")
    void testGetDispute_UserIdMismatch_ThrowsException() {
        // Given
        String disputeId = testDispute.getDisputeId();
        when(disputeRepository.findByDisputeId(disputeId)).thenReturn(Optional.of(testDispute));

        // When & Then
        assertThatThrownBy(() -> resolutionService.getDispute(disputeId, "wrong-user"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not authorized");
    }

    @Test
    @DisplayName("Should approve dispute successfully")
    void testApproveDispute_Success() {
        // Given
        UUID disputeId = UUID.fromString(testDispute.getDisputeId());
        UUID customerId = UUID.fromString(testDispute.getUserId());
        BigDecimal refundAmount = new BigDecimal("100.00");
        String resolutionReason = "Legitimate dispute";

        when(disputeRepository.findByDisputeId(testDispute.getDisputeId())).thenReturn(Optional.of(testDispute));
        when(disputeRepository.save(any(Dispute.class))).thenReturn(testDispute);

        // When
        resolutionService.approveDispute(disputeId, customerId, refundAmount, "USD", resolutionReason);

        // Then
        verify(disputeRepository).save(argThat(dispute ->
                dispute.getStatus() == DisputeStatus.RESOLVED &&
                dispute.getResolutionReason().equals(resolutionReason)
        ));
    }

    @Test
    @DisplayName("Should deny dispute successfully")
    void testDenyDispute_Success() {
        // Given
        UUID disputeId = UUID.fromString(testDispute.getDisputeId());
        UUID customerId = UUID.fromString(testDispute.getUserId());
        String denialReason = "Insufficient evidence";

        when(disputeRepository.findByDisputeId(testDispute.getDisputeId())).thenReturn(Optional.of(testDispute));
        when(disputeRepository.save(any(Dispute.class))).thenReturn(testDispute);

        // When
        resolutionService.denyDispute(disputeId, customerId, denialReason);

        // Then
        verify(disputeRepository).save(argThat(dispute ->
                dispute.getStatus() == DisputeStatus.CLOSED &&
                dispute.getResolutionReason().equals(denialReason)
        ));
    }

    @Test
    @DisplayName("Should partially approve dispute with adjusted amount")
    void testPartiallyApproveDispute_Success() {
        // Given
        UUID disputeId = UUID.fromString(testDispute.getDisputeId());
        UUID customerId = UUID.fromString(testDispute.getUserId());
        BigDecimal originalAmount = new BigDecimal("100.00");
        BigDecimal approvedAmount = new BigDecimal("50.00");
        String reason = "Partial responsibility";

        when(disputeRepository.findByDisputeId(testDispute.getDisputeId())).thenReturn(Optional.of(testDispute));
        when(disputeRepository.save(any(Dispute.class))).thenReturn(testDispute);

        // When
        resolutionService.partiallyApproveDispute(disputeId, customerId, originalAmount, approvedAmount, "USD", reason);

        // Then
        verify(disputeRepository).save(argThat(dispute ->
                dispute.getStatus() == DisputeStatus.PARTIALLY_RESOLVED
        ));
    }

    @Test
    @DisplayName("Should escalate dispute for manual review")
    void testEscalateForManualReview_Success() {
        // Given
        UUID disputeId = UUID.fromString(testDispute.getDisputeId());
        String escalationReason = "Complex case requiring specialist review";
        String escalationCategory = "FRAUD_INVESTIGATION";

        when(disputeRepository.findByDisputeId(testDispute.getDisputeId())).thenReturn(Optional.of(testDispute));
        when(disputeRepository.save(any(Dispute.class))).thenReturn(testDispute);

        // When
        resolutionService.escalateForManualReview(disputeId, escalationReason, escalationCategory);

        // Then
        verify(disputeRepository).save(argThat(dispute ->
                dispute.getStatus() == DisputeStatus.UNDER_REVIEW
        ));
    }

    @Test
    @DisplayName("Should process auto-resolution successfully")
    void testProcessAutoResolution_Success() {
        // Given
        UUID disputeId = UUID.fromString(testDispute.getDisputeId());
        UUID customerId = UUID.fromString(testDispute.getUserId());
        String resolutionType = "AI_APPROVED";
        String resolutionDecision = "APPROVE";
        BigDecimal confidenceScore = new BigDecimal("0.95");
        String aiModelVersion = "v2.1";

        when(disputeRepository.findByDisputeId(testDispute.getDisputeId())).thenReturn(Optional.of(testDispute));
        when(disputeRepository.save(any(Dispute.class))).thenReturn(testDispute);

        // When
        resolutionService.processAutoResolution(disputeId, customerId, resolutionType, resolutionDecision,
                confidenceScore, aiModelVersion);

        // Then
        verify(disputeRepository).save(argThat(dispute ->
                dispute.getStatus() == DisputeStatus.RESOLVED &&
                dispute.isAutoResolutionAttempted()
        ));
    }

    @Test
    @DisplayName("Should mark dispute for emergency review")
    void testMarkForEmergencyReview_Success() {
        // Given
        UUID disputeId = UUID.fromString(testDispute.getDisputeId());
        String emergencyReason = "Large amount with fraud indicators";
        String severity = "CRITICAL";

        when(disputeRepository.findByDisputeId(testDispute.getDisputeId())).thenReturn(Optional.of(testDispute));
        when(disputeRepository.save(any(Dispute.class))).thenReturn(testDispute);

        // When
        resolutionService.markForEmergencyReview(disputeId, emergencyReason, severity);

        // Then
        verify(disputeRepository).save(argThat(dispute ->
                dispute.getStatus() == DisputeStatus.ESCALATED
        ));
    }

    @Test
    @DisplayName("Should record processing failure")
    void testRecordProcessingFailure_Success() {
        // Given
        UUID disputeId = UUID.fromString(testDispute.getDisputeId());
        String failureReason = "Payment gateway timeout";
        String errorCode = "GATEWAY_TIMEOUT";
        String stackTrace = "java.net.SocketTimeoutException...";

        when(disputeRepository.findByDisputeId(testDispute.getDisputeId())).thenReturn(Optional.of(testDispute));
        when(disputeRepository.save(any(Dispute.class))).thenReturn(testDispute);

        // When
        resolutionService.recordProcessingFailure(disputeId, failureReason, errorCode, stackTrace);

        // Then
        verify(disputeRepository).save(argThat(dispute ->
                dispute.getStatus() == DisputeStatus.PROCESSING_FAILED
        ));
    }

    @Test
    @DisplayName("Should validate dispute amount is positive")
    void testCreateDispute_NegativeAmount_ThrowsException() {
        // Given
        testRequest.setAmount(new BigDecimal("-100.00"));

        // When & Then
        assertThatThrownBy(() -> resolutionService.createDispute(testRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    @DisplayName("Should validate dispute amount is not zero")
    void testCreateDispute_ZeroAmount_ThrowsException() {
        // Given
        testRequest.setAmount(BigDecimal.ZERO);

        // When & Then
        assertThatThrownBy(() -> resolutionService.createDispute(testRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }
}
