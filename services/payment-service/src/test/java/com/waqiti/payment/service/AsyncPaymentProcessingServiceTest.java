package com.waqiti.payment.service;

import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.dto.PaymentRequest;
import com.waqiti.payment.dto.PaymentResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for Async Payment Processing Service
 *
 * Tests cover:
 * - Parallel validation (fraud + compliance)
 * - Sequential settlement after validation
 * - Async notifications and analytics
 * - Error handling and compensation
 * - Performance characteristics
 * - Concurrent payment processing
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class AsyncPaymentProcessingServiceTest {

    @Mock
    private FraudDetectionService fraudDetectionService;

    @Mock
    private ComplianceService complianceService;

    @Mock
    private SettlementService settlementService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private PaymentRepository paymentRepository;

    private MeterRegistry meterRegistry;
    private AsyncPaymentProcessingService paymentProcessingService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        paymentProcessingService = new AsyncPaymentProcessingService(
                fraudDetectionService,
                complianceService,
                settlementService,
                notificationService,
                analyticsService,
                paymentRepository,
                meterRegistry);
    }

    @Test
    @DisplayName("Should process payment successfully with parallel validation")
    void testSuccessfulPaymentProcessing() throws ExecutionException, InterruptedException {
        // Given
        PaymentRequest request = PaymentRequest.builder()
                .sourceAccountId("source-123")
                .destinationAccountId("dest-456")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();

        String correlationId = UUID.randomUUID().toString();

        // Mock successful validation
        when(fraudDetectionService.checkPayment(anyString(), anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(true);
        when(complianceService.screenPayment(anyString(), anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(true);

        // Mock payment creation and settlement
        Payment mockPayment = Payment.builder()
                .id(UUID.randomUUID().toString())
                .sourceAccountId(request.getSourceAccountId())
                .destinationAccountId(request.getDestinationAccountId())
                .amount(request.getAmount())
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.save(any(Payment.class))).thenReturn(mockPayment);
        doNothing().when(settlementService).settlePayment(any(Payment.class), any(PaymentRequest.class), anyString());

        // When
        CompletableFuture<PaymentResponse> futureResponse =
                paymentProcessingService.processPaymentAsync(request, correlationId);

        PaymentResponse response = futureResponse.get(10, TimeUnit.SECONDS);

        // Then
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.getPaymentId()).isNotNull();
        assertThat(response.getCorrelationId()).isEqualTo(correlationId);

        // Verify parallel execution (both should be called)
        verify(fraudDetectionService, times(1)).checkPayment(anyString(), anyString(), any(BigDecimal.class), anyString());
        verify(complianceService, times(1)).screenPayment(anyString(), anyString(), any(BigDecimal.class), anyString());

        // Verify settlement called after validation
        verify(settlementService, times(1)).settlePayment(any(Payment.class), any(PaymentRequest.class), anyString());

        // Verify async operations (may not complete immediately)
        verify(notificationService, timeout(2000)).sendPaymentNotifications(any(Payment.class), anyString());
        verify(analyticsService, timeout(2000)).publishPaymentEvent(any(Payment.class), anyString());
    }

    @Test
    @DisplayName("Should reject payment when fraud check fails")
    void testPaymentRejectedByFraudCheck() throws ExecutionException, InterruptedException {
        // Given
        PaymentRequest request = PaymentRequest.builder()
                .sourceAccountId("source-123")
                .destinationAccountId("dest-456")
                .amount(new BigDecimal("10000.00"))
                .currency("USD")
                .build();

        String correlationId = UUID.randomUUID().toString();

        // Mock fraud check failure
        when(fraudDetectionService.checkPayment(anyString(), anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(false);

        // Compliance check passes
        when(complianceService.screenPayment(anyString(), anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(true);

        Payment mockPayment = Payment.builder()
                .id(UUID.randomUUID().toString())
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.save(any(Payment.class))).thenReturn(mockPayment);

        // When
        CompletableFuture<PaymentResponse> futureResponse =
                paymentProcessingService.processPaymentAsync(request, correlationId);

        PaymentResponse response = futureResponse.get(10, TimeUnit.SECONDS);

        // Then
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.getFailureReason()).contains("Fraud check failed");

        // Verify settlement was NOT called
        verify(settlementService, never()).settlePayment(any(), any(), anyString());
    }

    @Test
    @DisplayName("Should reject payment when compliance check fails")
    void testPaymentRejectedByComplianceCheck() throws ExecutionException, InterruptedException {
        // Given
        PaymentRequest request = PaymentRequest.builder()
                .sourceAccountId("source-123")
                .destinationAccountId("dest-456")
                .amount(new BigDecimal("5000.00"))
                .currency("USD")
                .build();

        String correlationId = UUID.randomUUID().toString();

        // Fraud check passes
        when(fraudDetectionService.checkPayment(anyString(), anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(true);

        // Mock compliance check failure
        when(complianceService.screenPayment(anyString(), anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(false);

        Payment mockPayment = Payment.builder()
                .id(UUID.randomUUID().toString())
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.save(any(Payment.class))).thenReturn(mockPayment);

        // When
        CompletableFuture<PaymentResponse> futureResponse =
                paymentProcessingService.processPaymentAsync(request, correlationId);

        PaymentResponse response = futureResponse.get(10, TimeUnit.SECONDS);

        // Then
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.getFailureReason()).contains("Compliance check failed");

        // Verify settlement was NOT called
        verify(settlementService, never()).settlePayment(any(), any(), anyString());
    }

    @Test
    @DisplayName("Performance: Parallel validation should be faster than sequential")
    void testParallelValidationPerformance() throws ExecutionException, InterruptedException {
        // Given
        PaymentRequest request = PaymentRequest.builder()
                .sourceAccountId("source-123")
                .destinationAccountId("dest-456")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();

        String correlationId = UUID.randomUUID().toString();

        // Simulate slow validation (3s fraud + 5s compliance)
        when(fraudDetectionService.checkPayment(anyString(), anyString(), any(BigDecimal.class), anyString()))
                .thenAnswer(invocation -> {
                    Thread.sleep(3000); // 3 seconds
                    return true;
                });

        when(complianceService.screenPayment(anyString(), anyString(), any(BigDecimal.class), anyString()))
                .thenAnswer(invocation -> {
                    Thread.sleep(5000); // 5 seconds
                    return true;
                });

        Payment mockPayment = Payment.builder()
                .id(UUID.randomUUID().toString())
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.save(any(Payment.class))).thenReturn(mockPayment);
        doNothing().when(settlementService).settlePayment(any(), any(), anyString());

        // When
        long startTime = System.currentTimeMillis();

        CompletableFuture<PaymentResponse> futureResponse =
                paymentProcessingService.processPaymentAsync(request, correlationId);

        PaymentResponse response = futureResponse.get(15, TimeUnit.SECONDS);

        long duration = System.currentTimeMillis() - startTime;

        // Then
        // Sequential would take 3s + 5s = 8s
        // Parallel should take MAX(3s, 5s) = ~5s (+ overhead)
        assertThat(duration).isGreaterThan(5000).isLessThan(7000); // 5-7s range

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should handle settlement errors gracefully")
    void testSettlementErrorHandling() throws ExecutionException, InterruptedException {
        // Given
        PaymentRequest request = PaymentRequest.builder()
                .sourceAccountId("source-123")
                .destinationAccountId("dest-456")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();

        String correlationId = UUID.randomUUID().toString();

        when(fraudDetectionService.checkPayment(anyString(), anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(true);
        when(complianceService.screenPayment(anyString(), anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(true);

        Payment mockPayment = Payment.builder()
                .id(UUID.randomUUID().toString())
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.save(any(Payment.class))).thenReturn(mockPayment);

        // Mock settlement failure
        doThrow(new RuntimeException("Insufficient funds"))
                .when(settlementService).settlePayment(any(), any(), anyString());

        // When
        CompletableFuture<PaymentResponse> futureResponse =
                paymentProcessingService.processPaymentAsync(request, correlationId);

        PaymentResponse response = futureResponse.get(10, TimeUnit.SECONDS);

        // Then
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.getFailureReason()).contains("Settlement error");
    }

    @Test
    @DisplayName("Should not fail payment if notifications fail (fire-and-forget)")
    void testNotificationFailureDoesNotAffectPayment() throws ExecutionException, InterruptedException {
        // Given
        PaymentRequest request = PaymentRequest.builder()
                .sourceAccountId("source-123")
                .destinationAccountId("dest-456")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();

        String correlationId = UUID.randomUUID().toString();

        when(fraudDetectionService.checkPayment(anyString(), anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(true);
        when(complianceService.screenPayment(anyString(), anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(true);

        Payment mockPayment = Payment.builder()
                .id(UUID.randomUUID().toString())
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.save(any(Payment.class))).thenReturn(mockPayment);
        doNothing().when(settlementService).settlePayment(any(), any(), anyString());

        // Mock notification failure
        doThrow(new RuntimeException("Email service down"))
                .when(notificationService).sendPaymentNotifications(any(), anyString());

        // When
        CompletableFuture<PaymentResponse> futureResponse =
                paymentProcessingService.processPaymentAsync(request, correlationId);

        PaymentResponse response = futureResponse.get(10, TimeUnit.SECONDS);

        // Then - Payment should still succeed
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should handle concurrent payment requests efficiently")
    void testConcurrentPaymentProcessing() throws InterruptedException {
        // Given
        when(fraudDetectionService.checkPayment(anyString(), anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(true);
        when(complianceService.screenPayment(anyString(), anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(true);

        Payment mockPayment = Payment.builder()
                .id(UUID.randomUUID().toString())
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.save(any(Payment.class))).thenReturn(mockPayment);
        doNothing().when(settlementService).settlePayment(any(), any(), anyString());

        // When - Process 100 concurrent payments
        int concurrentPayments = 100;
        CompletableFuture<PaymentResponse>[] futures = new CompletableFuture[concurrentPayments];

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < concurrentPayments; i++) {
            PaymentRequest request = PaymentRequest.builder()
                    .sourceAccountId("source-" + i)
                    .destinationAccountId("dest-" + i)
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .build();

            futures[i] = paymentProcessingService.processPaymentAsync(
                    request, UUID.randomUUID().toString());
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);

        long duration = System.currentTimeMillis() - startTime;

        // Then - All should succeed
        int successCount = 0;
        for (CompletableFuture<PaymentResponse> future : futures) {
            PaymentResponse response = future.get();
            if (response.getStatus() == PaymentStatus.COMPLETED) {
                successCount++;
            }
        }

        assertThat(successCount).isEqualTo(concurrentPayments);

        // Concurrent processing should be much faster than sequential
        // Sequential: 100 payments * 2s = 200s
        // Concurrent: < 10s with proper async execution
        assertThat(duration).isLessThan(10000);
    }
}
