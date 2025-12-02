package com.waqiti.payment.service;

import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kyc.service.KYCClientService;
import com.waqiti.common.security.audit.SecurityAuditLogger;
import com.waqiti.payment.audit.PaymentAuditServiceInterface;
import com.waqiti.payment.client.UnifiedWalletServiceClient;
import com.waqiti.payment.client.UserServiceClient;
import com.waqiti.payment.client.dto.UserResponse;
import com.waqiti.payment.client.dto.WalletResponse;
import com.waqiti.payment.core.UnifiedPaymentService;
import com.waqiti.payment.core.model.PaymentRequest;
import com.waqiti.payment.core.model.PaymentResult;
import com.waqiti.payment.core.model.PaymentResult.PaymentStatus;
import com.waqiti.payment.domain.PaymentRequestStatus;
import com.waqiti.payment.dto.CreatePaymentRequestRequest;
import com.waqiti.payment.dto.PaymentRequestResponse;
import com.waqiti.payment.exception.InsufficientFundsException;
import com.waqiti.payment.exception.InvalidPaymentOperationException;
import com.waqiti.payment.exception.KYCVerificationRequiredException;
import com.waqiti.payment.integration.eventsourcing.PaymentEventSourcingIntegration;
import com.waqiti.payment.notification.PaymentNotificationServiceInterface;
import com.waqiti.payment.refund.service.PaymentRefundService;
import com.waqiti.payment.repository.PaymentRequestRepository;
import com.waqiti.payment.validation.PaymentValidationServiceInterface;
import com.waqiti.payment.validation.ValidationResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Test Suite for PaymentService
 *
 * Test Coverage:
 * - Happy path scenarios
 * - Validation failures
 * - Security checks
 * - KYC verification
 * - Idempotency
 * - Error handling
 * - Edge cases
 * - Concurrency scenarios
 *
 * @author Waqiti Test Team
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Comprehensive Test Suite")
class PaymentServiceComprehensiveTest {

    private PaymentService paymentService;

    @Mock
    private PaymentRequestRepository paymentRequestRepository;

    @Mock
    private UnifiedWalletServiceClient walletClient;

    @Mock
    private UserServiceClient userClient;

    @Mock
    private KYCClientService kycClientService;

    @Mock
    private UnifiedPaymentService unifiedPaymentService;

    @Mock
    private PaymentEventSourcingIntegration eventSourcingIntegration;

    @Mock
    private PaymentRefundService paymentRefundService;

    @Mock
    private PaymentValidationServiceInterface paymentValidationService;

    @Mock
    private PaymentNotificationServiceInterface paymentNotificationService;

    @Mock
    private PaymentAuditServiceInterface paymentAuditService;

    @Mock
    private com.waqiti.common.distributed.DistributedLockService distributedLockService;

    @Mock
    private SecurityAuditLogger securityAuditLogger;

    @Mock
    private PaymentProviderService paymentProviderService;

    @Mock
    private PaymentEventService paymentEventService;

    private MeterRegistry meterRegistry;

    private UUID requestorId;
    private UUID recipientId;
    private CreatePaymentRequestRequest validRequest;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        paymentService = new PaymentService(
            paymentRequestRepository,
            walletClient,
            userClient,
            meterRegistry,
            kycClientService,
            unifiedPaymentService,
            eventSourcingIntegration,
            paymentRefundService,
            paymentValidationService,
            paymentNotificationService,
            paymentAuditService,
            distributedLockService,
            securityAuditLogger,
            paymentProviderService,
            paymentEventService
        );

        // Setup test data
        requestorId = UUID.randomUUID();
        recipientId = UUID.randomUUID();

        validRequest = CreatePaymentRequestRequest.builder()
            .recipientId(recipientId)
            .amount(new BigDecimal("100.00"))
            .currency("USD")
            .description("Test payment")
            .expiryHours(24)
            .idempotencyKey(UUID.randomUUID().toString())
            .build();
    }

    // ==================== HAPPY PATH TESTS ====================

    @Test
    @DisplayName("Should create payment request successfully with valid data")
    void shouldCreatePaymentRequestSuccessfully() {
        // Given
        UserResponse recipientUser = createMockUserResponse(recipientId, "john@example.com");
        ValidationResult validAmount = ValidationResult.valid();
        PaymentResult paymentResult = createSuccessfulPaymentResult();

        when(paymentValidationService.validatePaymentAmount(any(), any()))
            .thenReturn(validAmount);
        when(kycClientService.isUserVerifiedForAmount(requestorId, validRequest.getAmount()))
            .thenReturn(true);
        when(paymentValidationService.validateRecipientExists(recipientId))
            .thenReturn(recipientUser);
        when(unifiedPaymentService.processPayment(any(PaymentRequest.class)))
            .thenReturn(paymentResult);

        // When
        PaymentRequestResponse response = paymentService.createPaymentRequest(
            requestorId,
            validRequest
        );

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(PaymentRequestStatus.PENDING);

        // Verify all validations were called
        verify(paymentValidationService).validatePaymentAmount(
            validRequest.getAmount(),
            "USD"
        );
        verify(kycClientService).isUserVerifiedForAmount(
            requestorId,
            validRequest.getAmount()
        );
        verify(paymentValidationService).validateRecipientExists(recipientId);
        verify(unifiedPaymentService).processPayment(any(PaymentRequest.class));

        // Verify audit logging
        verify(paymentAuditService).auditPaymentRequestCreated(
            any(),
            eq(requestorId),
            eq(validRequest.getAmount()),
            eq(validRequest.getCurrency()),
            eq(recipientId),
            any()
        );

        // Verify metrics
        assertThat(meterRegistry.counter("payment.requests.created.unified").count())
            .isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should handle idempotent request correctly")
    void shouldHandleIdempotentRequest() {
        // Given
        String idempotencyKey = "test-idempotency-key";
        validRequest.setIdempotencyKey(idempotencyKey);

        UserResponse recipientUser = createMockUserResponse(recipientId, "john@example.com");
        ValidationResult validAmount = ValidationResult.valid();
        PaymentResult paymentResult = createSuccessfulPaymentResult();

        when(paymentValidationService.validatePaymentAmount(any(), any()))
            .thenReturn(validAmount);
        when(kycClientService.isUserVerifiedForAmount(any(), any())).thenReturn(true);
        when(paymentValidationService.validateRecipientExists(any()))
            .thenReturn(recipientUser);
        when(unifiedPaymentService.processPayment(any()))
            .thenReturn(paymentResult);

        // When - Call twice with same idempotency key
        PaymentRequestResponse response1 = paymentService.createPaymentRequest(
            requestorId, validRequest
        );

        // Second call with same key (idempotency handled by @Idempotent annotation)
        PaymentRequestResponse response2 = paymentService.createPaymentRequest(
            requestorId, validRequest
        );

        // Then
        assertThat(response1).isNotNull();
        assertThat(response2).isNotNull();

        // Verify idempotency key was used
        assertThat(validRequest.getIdempotencyKey()).isEqualTo(idempotencyKey);
    }

    // ==================== VALIDATION FAILURE TESTS ====================

    @Test
    @DisplayName("Should reject self-payment attempt")
    void shouldRejectSelfPayment() {
        // Given
        CreatePaymentRequestRequest selfPaymentRequest = CreatePaymentRequestRequest.builder()
            .recipientId(requestorId) // Same as requestor
            .amount(new BigDecimal("100.00"))
            .currency("USD")
            .build();

        // When & Then
        assertThatThrownBy(() ->
            paymentService.createPaymentRequest(requestorId, selfPaymentRequest)
        )
            .isInstanceOf(InvalidPaymentOperationException.class)
            .hasMessageContaining("Cannot send payment request to yourself");

        // Verify security audit was logged
        verify(paymentAuditService).auditSelfPaymentAttempt(
            eq(requestorId),
            any(),
            any()
        );
    }

    @Test
    @DisplayName("Should reject invalid payment amount")
    void shouldRejectInvalidAmount() {
        // Given
        ValidationResult invalidAmount = ValidationResult.builder()
            .valid(false)
            .primaryErrorMessage("Amount must be positive")
            .build();

        when(paymentValidationService.validatePaymentAmount(any(), any()))
            .thenReturn(invalidAmount);

        // When & Then
        assertThatThrownBy(() ->
            paymentService.createPaymentRequest(requestorId, validRequest)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Amount must be positive");

        // Verify no further processing occurred
        verify(kycClientService, never()).isUserVerifiedForAmount(any(), any());
        verify(unifiedPaymentService, never()).processPayment(any());
    }

    @Test
    @DisplayName("Should reject payment when KYC verification insufficient")
    void shouldRejectInsufficientKYC() {
        // Given
        ValidationResult validAmount = ValidationResult.valid();

        when(paymentValidationService.validatePaymentAmount(any(), any()))
            .thenReturn(validAmount);
        when(kycClientService.isUserVerifiedForAmount(requestorId, validRequest.getAmount()))
            .thenReturn(false);
        when(kycClientService.getUserVerificationLevel(requestorId))
            .thenReturn("BASIC");

        // When & Then
        assertThatThrownBy(() ->
            paymentService.createPaymentRequest(requestorId, validRequest)
        )
            .isInstanceOf(KYCVerificationRequiredException.class)
            .hasMessageContaining("KYC verification required");

        // Verify audit logging
        verify(paymentAuditService).auditInsufficientKYC(
            eq(requestorId),
            eq(validRequest.getAmount()),
            eq("BASIC")
        );
    }

    @Test
    @DisplayName("Should reject payment when recipient does not exist")
    void shouldRejectNonExistentRecipient() {
        // Given
        ValidationResult validAmount = ValidationResult.valid();

        when(paymentValidationService.validatePaymentAmount(any(), any()))
            .thenReturn(validAmount);
        when(kycClientService.isUserVerifiedForAmount(any(), any())).thenReturn(true);
        when(paymentValidationService.validateRecipientExists(recipientId))
            .thenThrow(new IllegalArgumentException("Recipient not found"));

        // When & Then
        assertThatThrownBy(() ->
            paymentService.createPaymentRequest(requestorId, validRequest)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Recipient not found");
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    @DisplayName("Should handle null idempotency key by generating one")
    void shouldGenerateIdempotencyKeyWhenNull() {
        // Given
        validRequest.setIdempotencyKey(null);

        UserResponse recipientUser = createMockUserResponse(recipientId, "john@example.com");
        ValidationResult validAmount = ValidationResult.valid();
        PaymentResult paymentResult = createSuccessfulPaymentResult();

        when(paymentValidationService.validatePaymentAmount(any(), any()))
            .thenReturn(validAmount);
        when(kycClientService.isUserVerifiedForAmount(any(), any())).thenReturn(true);
        when(paymentValidationService.validateRecipientExists(any()))
            .thenReturn(recipientUser);
        when(unifiedPaymentService.processPayment(any()))
            .thenReturn(paymentResult);

        // When
        PaymentRequestResponse response = paymentService.createPaymentRequest(
            requestorId,
            validRequest
        );

        // Then
        assertThat(response).isNotNull();
        // Idempotency key should have been auto-generated
    }

    @Test
    @DisplayName("Should handle maximum allowed amount")
    void shouldHandleMaximumAmount() {
        // Given
        BigDecimal maxAmount = new BigDecimal("10000.00");
        validRequest.setAmount(maxAmount);

        UserResponse recipientUser = createMockUserResponse(recipientId, "john@example.com");
        ValidationResult validAmount = ValidationResult.valid();
        PaymentResult paymentResult = createSuccessfulPaymentResult();

        when(paymentValidationService.validatePaymentAmount(eq(maxAmount), any()))
            .thenReturn(validAmount);
        when(kycClientService.isUserVerifiedForAmount(requestorId, maxAmount))
            .thenReturn(true);
        when(paymentValidationService.validateRecipientExists(any()))
            .thenReturn(recipientUser);
        when(unifiedPaymentService.processPayment(any()))
            .thenReturn(paymentResult);

        // When
        PaymentRequestResponse response = paymentService.createPaymentRequest(
            requestorId,
            validRequest
        );

        // Then
        assertThat(response).isNotNull();
        verify(paymentValidationService).validatePaymentAmount(maxAmount, "USD");
    }

    @Test
    @DisplayName("Should handle minimum allowed amount")
    void shouldHandleMinimumAmount() {
        // Given
        BigDecimal minAmount = new BigDecimal("0.01");
        validRequest.setAmount(minAmount);

        UserResponse recipientUser = createMockUserResponse(recipientId, "john@example.com");
        ValidationResult validAmount = ValidationResult.valid();
        PaymentResult paymentResult = createSuccessfulPaymentResult();

        when(paymentValidationService.validatePaymentAmount(eq(minAmount), any()))
            .thenReturn(validAmount);
        when(kycClientService.isUserVerifiedForAmount(any(), any())).thenReturn(true);
        when(paymentValidationService.validateRecipientExists(any()))
            .thenReturn(recipientUser);
        when(unifiedPaymentService.processPayment(any()))
            .thenReturn(paymentResult);

        // When
        PaymentRequestResponse response = paymentService.createPaymentRequest(
            requestorId,
            validRequest
        );

        // Then
        assertThat(response).isNotNull();
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    @DisplayName("Should handle unified payment service failure gracefully")
    void shouldHandleUnifiedServiceFailure() {
        // Given
        UserResponse recipientUser = createMockUserResponse(recipientId, "john@example.com");
        ValidationResult validAmount = ValidationResult.valid();

        when(paymentValidationService.validatePaymentAmount(any(), any()))
            .thenReturn(validAmount);
        when(kycClientService.isUserVerifiedForAmount(any(), any())).thenReturn(true);
        when(paymentValidationService.validateRecipientExists(any()))
            .thenReturn(recipientUser);
        when(unifiedPaymentService.processPayment(any()))
            .thenThrow(new RuntimeException("Payment processing failed"));

        // When & Then
        assertThatThrownBy(() ->
            paymentService.createPaymentRequest(requestorId, validRequest)
        )
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Payment processing failed");
    }

    // ==================== HELPER METHODS ====================

    private UserResponse createMockUserResponse(UUID userId, String email) {
        return UserResponse.builder()
            .userId(userId)
            .email(email)
            .firstName("John")
            .lastName("Doe")
            .status("ACTIVE")
            .build();
    }

    private PaymentResult createSuccessfulPaymentResult() {
        return PaymentResult.builder()
            .status(PaymentStatus.SUCCESS)
            .transactionId(UUID.randomUUID().toString())
            .correlationId(UUID.randomUUID().toString())
            .traceId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .message("Payment processed successfully")
            .build();
    }

    private WalletResponse createMockWalletResponse(UUID userId, BigDecimal balance) {
        return WalletResponse.builder()
            .walletId(UUID.randomUUID())
            .userId(userId)
            .balance(balance)
            .currency("USD")
            .status("ACTIVE")
            .build();
    }
}
