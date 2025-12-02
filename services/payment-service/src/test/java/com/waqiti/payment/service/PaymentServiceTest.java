package com.waqiti.payment.service;

import com.waqiti.common.audit.AuditService;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.dto.*;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.fraud.service.FraudDetectionService;
import com.waqiti.wallet.client.WalletServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CRITICAL: Comprehensive test suite for Payment Service
 * COVERAGE: Tests all business logic, edge cases, and error conditions
 * IMPACT: Ensures financial transaction integrity and prevents money loss
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@DisplayName("Payment Service Tests")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private WalletServiceClient walletServiceClient;

    @Mock
    private FraudDetectionService fraudDetectionService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private PaymentService paymentService;

    private Payment testPayment;
    private CreatePaymentRequestDto testRequest;
    private UUID testUserId;
    private UUID testPaymentId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testPaymentId = UUID.randomUUID();
        
        testRequest = CreatePaymentRequestDto.builder()
                .requestorId(testUserId)
                .recipientId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .description("Test payment")
                .build();

        testPayment = Payment.builder()
                .id(testPaymentId)
                .requestorId(testRequest.getRequestorId())
                .recipientId(testRequest.getRecipientId())
                .amount(testRequest.getAmount())
                .currency(testRequest.getCurrency())
                .description(testRequest.getDescription())
                .status(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("Payment Request Creation Tests")
    class PaymentRequestCreationTests {

        @Test
        @DisplayName("Should create payment request successfully with valid data")
        void shouldCreatePaymentRequestSuccessfully() {
            // GIVEN
            String requestId = "test-request-id";
            String deviceId = "test-device-id";
            
            when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);
            when(walletServiceClient.getAvailableBalance(testUserId, "USD"))
                    .thenReturn(new BigDecimal("1000.00"));
            when(fraudDetectionService.screenPaymentRequest(any(), any(), any()))
                    .thenReturn(FraudScreeningResult.approved());

            // WHEN
            PaymentRequestResponse response = paymentService.createPaymentRequest(
                    testRequest, requestId, deviceId);

            // THEN
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(testPaymentId);
            assertThat(response.getAmount()).isEqualTo(testRequest.getAmount());
            assertThat(response.getCurrency()).isEqualTo(testRequest.getCurrency());
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.PENDING);

            verify(auditService).logFinancialOperation(eq("PAYMENT_REQUEST_CREATED"), 
                    eq(testUserId.toString()), any(), any(), any(), any(), eq(requestId), any());
            verify(paymentRepository).save(argThat(payment -> 
                    payment.getAmount().equals(testRequest.getAmount()) &&
                    payment.getCurrency().equals(testRequest.getCurrency())));
        }

        @Test
        @DisplayName("Should reject payment request when insufficient funds")
        void shouldRejectPaymentRequestWhenInsufficientFunds() {
            // GIVEN
            when(walletServiceClient.getAvailableBalance(testUserId, "USD"))
                    .thenReturn(new BigDecimal("50.00")); // Less than requested amount

            // WHEN & THEN
            assertThatThrownBy(() -> paymentService.createPaymentRequest(testRequest, "req-id", null))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessage("Insufficient funds for payment request");

            verify(paymentRepository, never()).save(any());
            verify(auditService).logFinancialOperation(eq("PAYMENT_REQUEST_INSUFFICIENT_FUNDS"), 
                    any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should reject payment request when fraud detection blocks it")
        void shouldRejectPaymentRequestWhenFraudDetected() {
            // GIVEN
            when(walletServiceClient.getAvailableBalance(testUserId, "USD"))
                    .thenReturn(new BigDecimal("1000.00"));
            when(fraudDetectionService.screenPaymentRequest(any(), any(), any()))
                    .thenReturn(FraudScreeningResult.blocked("Suspicious activity detected"));

            // WHEN & THEN
            assertThatThrownBy(() -> paymentService.createPaymentRequest(testRequest, "req-id", null))
                    .isInstanceOf(FraudDetectionException.class)
                    .hasMessage("Payment blocked by fraud detection: Suspicious activity detected");

            verify(paymentRepository, never()).save(any());
            verify(auditService).logSecurityEvent(eq("PAYMENT_BLOCKED_FRAUD"), any(), any(), any());
        }

        @Test
        @DisplayName("Should validate payment amount is positive")
        void shouldValidatePaymentAmountIsPositive() {
            // GIVEN
            testRequest.setAmount(new BigDecimal("-100.00"));

            // WHEN & THEN
            assertThatThrownBy(() -> paymentService.createPaymentRequest(testRequest, "req-id", null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Payment amount must be positive");

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should validate payment amount doesn't exceed daily limit")
        void shouldValidatePaymentAmountDoesNotExceedDailyLimit() {
            // GIVEN
            testRequest.setAmount(new BigDecimal("10001.00")); // Over daily limit
            when(walletServiceClient.getAvailableBalance(testUserId, "USD"))
                    .thenReturn(new BigDecimal("20000.00"));

            // WHEN & THEN
            assertThatThrownBy(() -> paymentService.createPaymentRequest(testRequest, "req-id", null))
                    .isInstanceOf(PaymentLimitExceededException.class)
                    .hasMessage("Payment amount exceeds daily limit");

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should handle wallet service timeout gracefully")
        void shouldHandleWalletServiceTimeoutGracefully() {
            // GIVEN
            when(walletServiceClient.getAvailableBalance(testUserId, "USD"))
                    .thenThrow(new ServiceTimeoutException("Wallet service timeout"));

            // WHEN & THEN
            assertThatThrownBy(() -> paymentService.createPaymentRequest(testRequest, "req-id", null))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessage("Unable to verify account balance - please try again");

            verify(auditService).logSystemError(eq("WALLET_SERVICE_TIMEOUT"), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Payment Acceptance Tests")
    class PaymentAcceptanceTests {

        @Test
        @DisplayName("Should accept payment request successfully")
        void shouldAcceptPaymentRequestSuccessfully() {
            // GIVEN
            testPayment.setStatus(PaymentStatus.PENDING);
            String acceptorId = testPayment.getRecipientId().toString();
            String requestId = "accept-request-id";
            
            when(paymentRepository.findById(testPaymentId)).thenReturn(Optional.of(testPayment));
            when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);
            when(walletServiceClient.getAvailableBalance(testPayment.getRequestorId(), "USD"))
                    .thenReturn(new BigDecimal("1000.00"));
            when(walletServiceClient.transferFunds(any())).thenReturn(TransferResult.successful());

            // WHEN
            PaymentRequestResponse response = paymentService.acceptPaymentRequest(
                    testPaymentId, acceptorId, requestId, "device-id");

            // THEN
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

            verify(auditService).logFinancialOperation(eq("PAYMENT_REQUEST_ACCEPTED"), 
                    eq(acceptorId), any(), any(), any(), any(), eq(requestId), any());
            verify(walletServiceClient).transferFunds(argThat(transfer -> 
                    transfer.getAmount().equals(testPayment.getAmount())));
        }

        @Test
        @DisplayName("Should reject acceptance of non-existent payment")
        void shouldRejectAcceptanceOfNonExistentPayment() {
            // GIVEN
            when(paymentRepository.findById(testPaymentId)).thenReturn(Optional.empty());

            // WHEN & THEN
            assertThatThrownBy(() -> paymentService.acceptPaymentRequest(
                    testPaymentId, "user-id", "req-id", null))
                    .isInstanceOf(PaymentNotFoundException.class)
                    .hasMessage("Payment not found: " + testPaymentId);

            verify(walletServiceClient, never()).transferFunds(any());
        }

        @Test
        @DisplayName("Should reject acceptance by unauthorized user")
        void shouldRejectAcceptanceByUnauthorizedUser() {
            // GIVEN
            testPayment.setStatus(PaymentStatus.PENDING);
            when(paymentRepository.findById(testPaymentId)).thenReturn(Optional.of(testPayment));
            
            String unauthorizedUserId = UUID.randomUUID().toString();

            // WHEN & THEN
            assertThatThrownBy(() -> paymentService.acceptPaymentRequest(
                    testPaymentId, unauthorizedUserId, "req-id", null))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("User not authorized to accept this payment");

            verify(walletServiceClient, never()).transferFunds(any());
            verify(auditService).logSecurityEvent(eq("UNAUTHORIZED_PAYMENT_ACCESS"), 
                    eq(unauthorizedUserId), any(), any());
        }

        @Test
        @DisplayName("Should reject acceptance of already processed payment")
        void shouldRejectAcceptanceOfAlreadyProcessedPayment() {
            // GIVEN
            testPayment.setStatus(PaymentStatus.COMPLETED);
            when(paymentRepository.findById(testPaymentId)).thenReturn(Optional.of(testPayment));

            // WHEN & THEN
            assertThatThrownBy(() -> paymentService.acceptPaymentRequest(
                    testPaymentId, testPayment.getRecipientId().toString(), "req-id", null))
                    .isInstanceOf(InvalidPaymentOperationException.class)
                    .hasMessage("Payment is not in a state that can be accepted");

            verify(walletServiceClient, never()).transferFunds(any());
        }

        @Test
        @DisplayName("Should handle insufficient funds during acceptance")
        void shouldHandleInsufficientFundsDuringAcceptance() {
            // GIVEN
            testPayment.setStatus(PaymentStatus.PENDING);
            when(paymentRepository.findById(testPaymentId)).thenReturn(Optional.of(testPayment));
            when(walletServiceClient.getAvailableBalance(testPayment.getRequestorId(), "USD"))
                    .thenReturn(new BigDecimal("50.00")); // Less than payment amount

            // WHEN & THEN
            assertThatThrownBy(() -> paymentService.acceptPaymentRequest(
                    testPaymentId, testPayment.getRecipientId().toString(), "req-id", null))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessage("Requestor no longer has sufficient funds");

            verify(walletServiceClient, never()).transferFunds(any());
            verify(paymentRepository).save(argThat(payment -> 
                    payment.getStatus() == PaymentStatus.FAILED));
        }
    }

    @Nested
    @DisplayName("Payment Rejection Tests")
    class PaymentRejectionTests {

        @Test
        @DisplayName("Should reject payment request successfully")
        void shouldRejectPaymentRequestSuccessfully() {
            // GIVEN
            testPayment.setStatus(PaymentStatus.PENDING);
            String rejectorId = testPayment.getRecipientId().toString();
            RejectPaymentRequestDto rejectRequest = new RejectPaymentRequestDto("Not needed");
            
            when(paymentRepository.findById(testPaymentId)).thenReturn(Optional.of(testPayment));
            when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);

            // WHEN
            PaymentRequestResponse response = paymentService.rejectPaymentRequest(
                    testPaymentId, rejectRequest, rejectorId, "req-id");

            // THEN
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.REJECTED);

            verify(auditService).logFinancialOperation(eq("PAYMENT_REQUEST_REJECTED"), 
                    eq(rejectorId), any(), any(), any(), any(), any(), any(), eq("Not needed"));
            verify(paymentRepository).save(argThat(payment -> 
                    payment.getStatus() == PaymentStatus.REJECTED &&
                    payment.getRejectionReason().equals("Not needed")));
        }

        @Test
        @DisplayName("Should reject rejection by unauthorized user")
        void shouldRejectRejectionByUnauthorizedUser() {
            // GIVEN
            testPayment.setStatus(PaymentStatus.PENDING);
            when(paymentRepository.findById(testPaymentId)).thenReturn(Optional.of(testPayment));
            
            String unauthorizedUserId = UUID.randomUUID().toString();

            // WHEN & THEN
            assertThatThrownBy(() -> paymentService.rejectPaymentRequest(
                    testPaymentId, null, unauthorizedUserId, "req-id"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("User not authorized to reject this payment");

            verify(auditService).logSecurityEvent(eq("UNAUTHORIZED_PAYMENT_ACCESS"), 
                    eq(unauthorizedUserId), any(), any());
        }
    }

    @Nested
    @DisplayName("Payment Retrieval Tests")
    class PaymentRetrievalTests {

        @Test
        @DisplayName("Should retrieve payment successfully")
        void shouldRetrievePaymentSuccessfully() {
            // GIVEN
            when(paymentRepository.findById(testPaymentId)).thenReturn(Optional.of(testPayment));

            // WHEN
            Optional<PaymentDetailsDto> result = paymentService.getPaymentDetails(
                    testPaymentId, testUserId.toString());

            // THEN
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(testPaymentId);
            assertThat(result.get().getAmount()).isEqualTo(testPayment.getAmount());

            verify(auditService).logDataAccess(eq("PAYMENT_ACCESSED"), 
                    eq(testUserId.toString()), eq(testPaymentId.toString()), any(), any());
        }

        @Test
        @DisplayName("Should return empty for non-existent payment")
        void shouldReturnEmptyForNonExistentPayment() {
            // GIVEN
            when(paymentRepository.findById(testPaymentId)).thenReturn(Optional.empty());

            // WHEN
            Optional<PaymentDetailsDto> result = paymentService.getPaymentDetails(
                    testPaymentId, testUserId.toString());

            // THEN
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should reject access by unauthorized user")
        void shouldRejectAccessByUnauthorizedUser() {
            // GIVEN
            when(paymentRepository.findById(testPaymentId)).thenReturn(Optional.of(testPayment));
            String unauthorizedUserId = UUID.randomUUID().toString();

            // WHEN & THEN
            assertThatThrownBy(() -> paymentService.getPaymentDetails(
                    testPaymentId, unauthorizedUserId))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("User not authorized to access this payment");

            verify(auditService).logSecurityEvent(eq("UNAUTHORIZED_PAYMENT_ACCESS"), 
                    eq(unauthorizedUserId), any(), any());
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesAndErrorHandling {

        @Test
        @DisplayName("Should handle null payment request gracefully")
        void shouldHandleNullPaymentRequestGracefully() {
            // WHEN & THEN
            assertThatThrownBy(() -> paymentService.createPaymentRequest(null, "req-id", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Payment request cannot be null");
        }

        @Test
        @DisplayName("Should handle concurrent payment acceptance attempts")
        void shouldHandleConcurrentPaymentAcceptanceAttempts() {
            // GIVEN
            testPayment.setStatus(PaymentStatus.PENDING);
            when(paymentRepository.findById(testPaymentId)).thenReturn(Optional.of(testPayment));
            when(paymentRepository.save(any(Payment.class)))
                    .thenThrow(new ConcurrencyException("Payment already processed"))
                    .thenReturn(testPayment);

            // WHEN & THEN
            assertThatThrownBy(() -> paymentService.acceptPaymentRequest(
                    testPaymentId, testPayment.getRecipientId().toString(), "req-id", null))
                    .isInstanceOf(ConcurrencyException.class)
                    .hasMessage("Payment already processed");

            verify(auditService).logSystemError(eq("PAYMENT_CONCURRENCY_ERROR"), any(), any(), any());
        }

        @Test
        @DisplayName("Should handle database connection errors gracefully")
        void shouldHandleDatabaseConnectionErrorsGracefully() {
            // GIVEN
            when(paymentRepository.findById(testPaymentId))
                    .thenThrow(new DataAccessException("Database connection failed"));

            // WHEN & THEN
            assertThatThrownBy(() -> paymentService.getPaymentDetails(
                    testPaymentId, testUserId.toString()))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessage("Service temporarily unavailable - please try again");

            verify(auditService).logSystemError(eq("DATABASE_CONNECTION_ERROR"), any(), any(), any());
        }

        @Test
        @DisplayName("Should validate currency code format")
        void shouldValidateCurrencyCodeFormat() {
            // GIVEN
            testRequest.setCurrency("INVALID");

            // WHEN & THEN
            assertThatThrownBy(() -> paymentService.createPaymentRequest(testRequest, "req-id", null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Invalid currency code: INVALID");
        }

        @Test
        @DisplayName("Should handle payment amount precision correctly")
        void shouldHandlePaymentAmountPrecisionCorrectly() {
            // GIVEN
            testRequest.setAmount(new BigDecimal("100.999")); // More than 2 decimal places

            // WHEN & THEN
            assertThatThrownBy(() -> paymentService.createPaymentRequest(testRequest, "req-id", null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Payment amount cannot have more than 2 decimal places");
        }
    }

    // Helper classes for test data
    private static class FraudScreeningResult {
        private boolean blocked;
        private String reason;
        
        public static FraudScreeningResult approved() {
            FraudScreeningResult result = new FraudScreeningResult();
            result.blocked = false;
            return result;
        }
        
        public static FraudScreeningResult blocked(String reason) {
            FraudScreeningResult result = new FraudScreeningResult();
            result.blocked = true;
            result.reason = reason;
            return result;
        }
        
        public boolean isBlocked() { return blocked; }
        public String getReason() { return reason; }
    }

    private static class TransferResult {
        private boolean successful;
        
        public static TransferResult successful() {
            TransferResult result = new TransferResult();
            result.successful = true;
            return result;
        }
        
        public boolean isSuccessful() { return successful; }
    }

    // Exception classes
    private static class ServiceTimeoutException extends RuntimeException {
        public ServiceTimeoutException(String message) { super(message); }
    }
    
    private static class FraudDetectionException extends RuntimeException {
        public FraudDetectionException(String message) { super(message); }
    }
    
    private static class ValidationException extends RuntimeException {
        public ValidationException(String message) { super(message); }
    }
    
    private static class PaymentLimitExceededException extends RuntimeException {
        public PaymentLimitExceededException(String message) { super(message); }
    }
    
    private static class ExternalServiceException extends RuntimeException {
        public ExternalServiceException(String message) { super(message); }
    }
    
    private static class PaymentNotFoundException extends RuntimeException {
        public PaymentNotFoundException(String message) { super(message); }
    }
    
    private static class InvalidPaymentOperationException extends RuntimeException {
        public InvalidPaymentOperationException(String message) { super(message); }
    }
    
    private static class InsufficientFundsException extends RuntimeException {
        public InsufficientFundsException(String message) { super(message); }
    }
    
    private static class SecurityException extends RuntimeException {
        public SecurityException(String message) { super(message); }
    }
    
    private static class ConcurrencyException extends RuntimeException {
        public ConcurrencyException(String message) { super(message); }
    }
    
    private static class DataAccessException extends RuntimeException {
        public DataAccessException(String message) { super(message); }
    }
}