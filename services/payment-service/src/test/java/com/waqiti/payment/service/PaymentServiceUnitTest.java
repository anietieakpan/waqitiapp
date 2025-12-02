package com.waqiti.payment.service;

import com.waqiti.common.test.BaseUnitTest;
import com.waqiti.common.test.TestDataBuilder;
import com.waqiti.common.test.TestFixtures;
import com.waqiti.payment.entity.Payment;
import com.waqiti.payment.entity.PaymentStatus;
import com.waqiti.payment.exception.InsufficientFundsException;
import com.waqiti.payment.exception.PaymentNotFoundException;
import com.waqiti.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for PaymentService.
 *
 * Tests cover:
 * - Payment creation and validation
 * - Payment processing workflows
 * - Error handling and edge cases
 * - Idempotency guarantees
 * - Fraud detection integration
 * - State transitions
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2024-10-19
 */
@DisplayName("Payment Service Unit Tests")
class PaymentServiceUnitTest extends BaseUnitTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private WalletServiceClient walletServiceClient;

    @Mock
    private FraudDetectionServiceClient fraudDetectionClient;

    @Mock
    private ComplianceServiceClient complianceServiceClient;

    @Mock
    private PaymentEventPublisher eventPublisher;

    @InjectMocks
    private PaymentService paymentService;

    @Captor
    private ArgumentCaptor<Payment> paymentCaptor;

    private UUID userId;
    private UUID merchantId;
    private Payment testPayment;

    @BeforeEach
    @Override
    protected void setUp() {
        userId = TestFixtures.VERIFIED_USER_ID;
        merchantId = TestFixtures.VERIFIED_MERCHANT_ID;

        testPayment = Payment.builder()
                .id(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .userId(userId)
                .merchantId(merchantId)
                .amount(TestFixtures.STANDARD_PAYMENT_AMOUNT)
                .currency(TestFixtures.DEFAULT_CURRENCY)
                .status(PaymentStatus.PENDING)
                .paymentMethod("ACH")
                .description("Test payment")
                .createdAt(now().atZone(fixedClock.getZone()).toLocalDateTime())
                .updatedAt(now().atZone(fixedClock.getZone()).toLocalDateTime())
                .version(0L)
                .build();
    }

    @Nested
    @DisplayName("Payment Creation Tests")
    class PaymentCreationTests {

        @Test
        @DisplayName("Should create payment with valid data")
        void shouldCreatePaymentWithValidData() {
            // Given
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .userId(userId)
                    .merchantId(merchantId)
                    .amount(TestFixtures.STANDARD_PAYMENT_AMOUNT)
                    .currency(TestFixtures.DEFAULT_CURRENCY)
                    .paymentMethod("ACH")
                    .description("Test payment")
                    .build();

            when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);
            when(walletServiceClient.checkBalance(userId, TestFixtures.DEFAULT_CURRENCY))
                    .thenReturn(TestFixtures.STANDARD_WALLET_BALANCE);

            // When
            Payment createdPayment = paymentService.createPayment(request);

            // Then
            assertThat(createdPayment).isNotNull();
            assertThat(createdPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(createdPayment.getAmount()).isEqualByComparingTo(TestFixtures.STANDARD_PAYMENT_AMOUNT);

            verify(paymentRepository).save(paymentCaptor.capture());
            Payment savedPayment = paymentCaptor.getValue();
            assertThat(savedPayment.getUserId()).isEqualTo(userId);
            assertThat(savedPayment.getMerchantId()).isEqualTo(merchantId);
        }

        @Test
        @DisplayName("Should reject payment with negative amount")
        void shouldRejectPaymentWithNegativeAmount() {
            // Given
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .userId(userId)
                    .amount(new BigDecimal("-100.00"))
                    .currency(TestFixtures.DEFAULT_CURRENCY)
                    .build();

            // When / Then
            assertThatThrownBy(() -> paymentService.createPayment(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount must be positive");

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject payment with zero amount")
        void shouldRejectPaymentWithZeroAmount() {
            // Given
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .userId(userId)
                    .amount(BigDecimal.ZERO)
                    .currency(TestFixtures.DEFAULT_CURRENCY)
                    .build();

            // When / Then
            assertThatThrownBy(() -> paymentService.createPayment(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount must be positive");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "INVALID"})
        @DisplayName("Should reject payment with invalid currency")
        void shouldRejectPaymentWithInvalidCurrency(String currency) {
            // Given
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .userId(userId)
                    .amount(TestFixtures.STANDARD_PAYMENT_AMOUNT)
                    .currency(currency)
                    .build();

            // When / Then
            assertThatThrownBy(() -> paymentService.createPayment(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid currency");
        }

        @Test
        @DisplayName("Should enforce idempotency with duplicate key")
        void shouldEnforceIdempotencyWithDuplicateKey() {
            // Given
            String idempotencyKey = TestFixtures.STANDARD_IDEMPOTENCY_KEY;
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .userId(userId)
                    .amount(TestFixtures.STANDARD_PAYMENT_AMOUNT)
                    .currency(TestFixtures.DEFAULT_CURRENCY)
                    .idempotencyKey(idempotencyKey)
                    .build();

            when(paymentRepository.findByIdempotencyKey(idempotencyKey))
                    .thenReturn(Optional.of(testPayment));

            // When
            Payment result = paymentService.createPayment(request);

            // Then
            assertThat(result).isEqualTo(testPayment);
            verify(paymentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Payment Processing Tests")
    class PaymentProcessingTests {

        @Test
        @DisplayName("Should process payment successfully")
        void shouldProcessPaymentSuccessfully() {
            // Given
            UUID paymentId = testPayment.getPaymentId();
            when(paymentRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(testPayment));
            when(walletServiceClient.debit(any(), any(), any())).thenReturn(true);
            when(fraudDetectionClient.checkPayment(any())).thenReturn(FraudCheckResult.lowRisk());
            when(paymentRepository.save(any())).thenReturn(testPayment);

            // When
            Payment processedPayment = paymentService.processPayment(paymentId);

            // Then
            assertThat(processedPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            verify(walletServiceClient).debit(eq(userId), eq(testPayment.getAmount()), any());
            verify(eventPublisher).publishPaymentCompletedEvent(any());
        }

        @Test
        @DisplayName("Should handle insufficient funds")
        void shouldHandleInsufficientFunds() {
            // Given
            UUID paymentId = testPayment.getPaymentId();
            when(paymentRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(testPayment));
            when(walletServiceClient.debit(any(), any(), any()))
                    .thenThrow(new InsufficientFundsException("Insufficient balance"));

            // When / Then
            assertThatThrownBy(() -> paymentService.processPayment(paymentId))
                    .isInstanceOf(InsufficientFundsException.class);

            verify(paymentRepository).save(paymentCaptor.capture());
            Payment savedPayment = paymentCaptor.getValue();
            assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(savedPayment.getFailureReason()).contains("Insufficient");
        }

        @Test
        @DisplayName("Should block high-risk payment")
        void shouldBlockHighRiskPayment() {
            // Given
            UUID paymentId = testPayment.getPaymentId();
            when(paymentRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(testPayment));
            when(fraudDetectionClient.checkPayment(any())).thenReturn(FraudCheckResult.highRisk());

            // When / Then
            assertThatThrownBy(() -> paymentService.processPayment(paymentId))
                    .isInstanceOf(FraudDetectedException.class);

            verify(paymentRepository).save(paymentCaptor.capture());
            Payment savedPayment = paymentCaptor.getValue();
            assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.DECLINED);
            assertThat(savedPayment.getFraudScore()).isGreaterThan(TestFixtures.FRAUD_THRESHOLD);
        }

        @Test
        @DisplayName("Should handle payment not found")
        void shouldHandlePaymentNotFound() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            when(paymentRepository.findByPaymentId(nonExistentId)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> paymentService.processPayment(nonExistentId))
                    .isInstanceOf(PaymentNotFoundException.class)
                    .hasMessageContaining(nonExistentId.toString());
        }

        @ParameterizedTest
        @EnumSource(value = PaymentStatus.class, names = {"COMPLETED", "CANCELLED", "DECLINED"})
        @DisplayName("Should not reprocess payment in terminal state")
        void shouldNotReprocessPaymentInTerminalState(PaymentStatus terminalStatus) {
            // Given
            testPayment.setStatus(terminalStatus);
            when(paymentRepository.findByPaymentId(testPayment.getPaymentId()))
                    .thenReturn(Optional.of(testPayment));

            // When / Then
            assertThatThrownBy(() -> paymentService.processPayment(testPayment.getPaymentId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot process payment in state: " + terminalStatus);
        }
    }

    @Nested
    @DisplayName("Payment Retry Tests")
    class PaymentRetryTests {

        @Test
        @DisplayName("Should retry failed payment successfully")
        void shouldRetryFailedPaymentSuccessfully() {
            // Given
            testPayment.setStatus(PaymentStatus.FAILED);
            testPayment.setRetryCount(1);
            UUID paymentId = testPayment.getPaymentId();

            when(paymentRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(testPayment));
            when(walletServiceClient.debit(any(), any(), any())).thenReturn(true);
            when(fraudDetectionClient.checkPayment(any())).thenReturn(FraudCheckResult.lowRisk());
            when(paymentRepository.save(any())).thenReturn(testPayment);

            // When
            Payment retriedPayment = paymentService.retryPayment(paymentId);

            // Then
            assertThat(retriedPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(retriedPayment.getRetryCount()).isEqualTo(2);
            verify(paymentRepository).save(any());
        }

        @Test
        @DisplayName("Should not retry payment exceeding max retries")
        void shouldNotRetryPaymentExceedingMaxRetries() {
            // Given
            testPayment.setStatus(PaymentStatus.FAILED);
            testPayment.setRetryCount(3); // Max retries reached
            UUID paymentId = testPayment.getPaymentId();

            when(paymentRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(testPayment));

            // When / Then
            assertThatThrownBy(() -> paymentService.retryPayment(paymentId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Maximum retry attempts exceeded");

            verify(walletServiceClient, never()).debit(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Payment Cancellation Tests")
    class PaymentCancellationTests {

        @Test
        @DisplayName("Should cancel pending payment")
        void shouldCancelPendingPayment() {
            // Given
            UUID paymentId = testPayment.getPaymentId();
            when(paymentRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(testPayment));
            when(paymentRepository.save(any())).thenReturn(testPayment);

            // When
            Payment cancelledPayment = paymentService.cancelPayment(paymentId, "User requested cancellation");

            // Then
            assertThat(cancelledPayment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            verify(eventPublisher).publishPaymentCancelledEvent(any());
        }

        @Test
        @DisplayName("Should not cancel completed payment")
        void shouldNotCancelCompletedPayment() {
            // Given
            testPayment.setStatus(PaymentStatus.COMPLETED);
            UUID paymentId = testPayment.getPaymentId();
            when(paymentRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(testPayment));

            // When / Then
            assertThatThrownBy(() -> paymentService.cancelPayment(paymentId, "Cancel"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot cancel payment in state: COMPLETED");
        }
    }

    @Nested
    @DisplayName("Parameterized Tests")
    class ParameterizedTests {

        @ParameterizedTest
        @CsvSource({
            "100.00, USD, true",
            "10000.00, USD, true",
            "0.01, USD, true",
            "100000.00, EUR, true"
        })
        @DisplayName("Should validate payment amounts")
        void shouldValidatePaymentAmounts(String amount, String currency, boolean expected) {
            // Given
            BigDecimal paymentAmount = new BigDecimal(amount);

            // When
            boolean isValid = paymentService.isValidPaymentAmount(paymentAmount, currency);

            // Then
            assertThat(isValid).isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(doubles = {1.00, 10.50, 100.00, 1000.00, 5000.00})
        @DisplayName("Should calculate fees correctly for different amounts")
        void shouldCalculateFeesCorrectly(double amount) {
            // Given
            BigDecimal paymentAmount = new BigDecimal(amount).setScale(4);

            // When
            BigDecimal fee = paymentService.calculateFee(paymentAmount);

            // Then
            assertThat(fee).isNotNull();
            assertThat(fee).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            // Fee should be reasonable (e.g., 2-3% of amount)
            BigDecimal maxExpectedFee = paymentAmount.multiply(new BigDecimal("0.03"));
            assertThat(fee).isLessThanOrEqualTo(maxExpectedFee);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle concurrent payment processing")
        void shouldHandleConcurrentPaymentProcessing() {
            // Given - simulate optimistic locking exception
            UUID paymentId = testPayment.getPaymentId();
            when(paymentRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(testPayment));
            when(paymentRepository.save(any()))
                    .thenThrow(new OptimisticLockingFailureException("Concurrent modification"));

            // When / Then
            assertThatThrownBy(() -> paymentService.processPayment(paymentId))
                    .isInstanceOf(OptimisticLockingFailureException.class);
        }

        @Test
        @DisplayName("Should handle external service timeout")
        void shouldHandleExternalServiceTimeout() {
            // Given
            UUID paymentId = testPayment.getPaymentId();
            when(paymentRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(testPayment));
            when(walletServiceClient.debit(any(), any(), any()))
                    .thenThrow(new ServiceTimeoutException("Wallet service timeout"));

            // When / Then
            assertThatThrownBy(() -> paymentService.processPayment(paymentId))
                    .isInstanceOf(ServiceTimeoutException.class);

            verify(paymentRepository).save(paymentCaptor.capture());
            Payment savedPayment = paymentCaptor.getValue();
            assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING); // Should remain pending for retry
        }

        @Test
        @DisplayName("Should handle null payment ID gracefully")
        void shouldHandleNullPaymentIdGracefully() {
            // When / Then
            assertThatThrownBy(() -> paymentService.processPayment(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Payment ID cannot be null");
        }
    }
}
