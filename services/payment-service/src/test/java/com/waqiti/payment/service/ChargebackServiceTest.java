package com.waqiti.payment.service;

import com.waqiti.payment.domain.ChargebackReason;
import com.waqiti.payment.domain.ChargebackStatus;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.dto.PaymentChargebackEvent;
import com.waqiti.payment.entity.Chargeback;
import com.waqiti.payment.repository.ChargebackRepository;
import com.waqiti.payment.repository.PaymentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for ChargebackService
 *
 * Tests complete chargeback lifecycle:
 * - Initiating chargebacks
 * - Processing external chargebacks
 * - Challenging chargebacks
 * - Accepting chargebacks
 * - Resolving chargebacks
 * - Validation logic
 * - Error handling
 * - Event publishing
 * - Wallet integration
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChargebackService Tests")
class ChargebackServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ChargebackRepository chargebackRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private WalletService walletService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditService auditService;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    private ChargebackService chargebackService;

    @Captor
    private ArgumentCaptor<Chargeback> chargebackCaptor;

    @Captor
    private ArgumentCaptor<Payment> paymentCaptor;

    @Captor
    private ArgumentCaptor<String> topicCaptor;

    @Captor
    private ArgumentCaptor<Object> eventCaptor;

    private static final String CHARGEBACK_TOPIC = "payment-chargeback-events";

    @BeforeEach
    void setUp() {
        chargebackService = new ChargebackService(
                paymentRepository,
                chargebackRepository,
                kafkaTemplate,
                walletService,
                notificationService,
                auditService,
                meterRegistry
        );

        // Mock meter registry
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);
        when(meterRegistry.counter(anyString())).thenReturn(counter);
    }

    @Nested
    @DisplayName("Initiate Chargeback Tests")
    class InitiateChargebackTests {

        @Test
        @DisplayName("Should initiate chargeback successfully")
        void shouldInitiateChargebackSuccessfully() throws Exception {
            // Given
            String paymentId = "payment-123";
            BigDecimal amount = new BigDecimal("100.00");
            ChargebackReason reason = ChargebackReason.FRAUDULENT;
            String initiatedBy = "user-456";

            Payment payment = createTestPayment(paymentId, amount);
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

            Chargeback savedChargeback = createTestChargeback(paymentId, amount, reason);
            when(chargebackRepository.save(any(Chargeback.class))).thenReturn(savedChargeback);

            // Mock Kafka send
            CompletableFuture<SendResult<String, Object>> kafkaFuture =
                CompletableFuture.completedFuture(mock(SendResult.class));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(kafkaFuture);

            // When
            CompletableFuture<Chargeback> result = chargebackService.initiateChargeback(
                    paymentId, reason, amount, initiatedBy);
            Chargeback chargeback = result.get();

            // Then
            assertThat(chargeback).isNotNull();
            assertThat(chargeback.getPaymentId()).isEqualTo(paymentId);
            assertThat(chargeback.getChargebackAmount()).isEqualByComparingTo(amount);
            assertThat(chargeback.getReason()).isEqualTo(reason);

            verify(paymentRepository).findById(paymentId);
            verify(chargebackRepository).save(any(Chargeback.class));
            verify(kafkaTemplate).send(eq(CHARGEBACK_TOPIC), eq(paymentId), any());
            verify(walletService).freezeAmount(eq(payment.getMerchantId()), eq(amount), anyString());
            verify(counter, atLeastOnce()).increment();
        }

        @Test
        @DisplayName("Should throw exception when payment not found")
        void shouldThrowExceptionWhenPaymentNotFound() {
            // Given
            String paymentId = "non-existent";
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> chargebackService.initiateChargeback(
                    paymentId, ChargebackReason.FRAUDULENT, BigDecimal.TEN, "user"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Chargeback initiation failed");

            verify(chargebackRepository, never()).save(any());
            verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Should reject chargeback for refunded payment")
        void shouldRejectChargebackForRefundedPayment() {
            // Given
            String paymentId = "payment-123";
            Payment payment = createTestPayment(paymentId, new BigDecimal("100.00"));
            payment.setStatus(PaymentStatus.REFUNDED);

            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

            // When/Then
            assertThatThrownBy(() -> chargebackService.initiateChargeback(
                    paymentId, ChargebackReason.FRAUDULENT, new BigDecimal("100.00"), "user"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Chargeback initiation failed");

            verify(chargebackRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject chargeback for cancelled payment")
        void shouldRejectChargebackForCancelledPayment() {
            // Given
            String paymentId = "payment-123";
            Payment payment = createTestPayment(paymentId, new BigDecimal("100.00"));
            payment.setStatus(PaymentStatus.CANCELLED);

            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

            // When/Then
            assertThatThrownBy(() -> chargebackService.initiateChargeback(
                    paymentId, ChargebackReason.FRAUDULENT, new BigDecimal("100.00"), "user"))
                    .isInstanceOf(RuntimeException.class);

            verify(chargebackRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject chargeback exceeding payment amount")
        void shouldRejectChargebackExceedingPaymentAmount() {
            // Given
            String paymentId = "payment-123";
            Payment payment = createTestPayment(paymentId, new BigDecimal("100.00"));

            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

            // When/Then
            assertThatThrownBy(() -> chargebackService.initiateChargeback(
                    paymentId, ChargebackReason.FRAUDULENT, new BigDecimal("150.00"), "user"))
                    .isInstanceOf(RuntimeException.class);

            verify(chargebackRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject chargeback outside timeframe")
        void shouldRejectChargebackOutsideTimeframe() {
            // Given
            String paymentId = "payment-123";
            Payment payment = createTestPayment(paymentId, new BigDecimal("100.00"));
            // Set payment created 121 days ago (outside 120-day window)
            payment.setCreatedAt(Instant.now().minus(121, ChronoUnit.DAYS));

            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

            // When/Then
            assertThatThrownBy(() -> chargebackService.initiateChargeback(
                    paymentId, ChargebackReason.FRAUDULENT, new BigDecimal("100.00"), "user"))
                    .isInstanceOf(RuntimeException.class);

            verify(chargebackRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should handle Kafka publish failure")
        void shouldHandleKafkaPublishFailure() {
            // Given
            String paymentId = "payment-123";
            BigDecimal amount = new BigDecimal("100.00");
            Payment payment = createTestPayment(paymentId, amount);

            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

            Chargeback savedChargeback = createTestChargeback(paymentId, amount, ChargebackReason.FRAUDULENT);
            when(chargebackRepository.save(any(Chargeback.class))).thenReturn(savedChargeback);

            // Mock Kafka failure
            CompletableFuture<SendResult<String, Object>> kafkaFuture = new CompletableFuture<>();
            kafkaFuture.completeExceptionally(new RuntimeException("Kafka error"));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(kafkaFuture);

            // When
            CompletableFuture<Chargeback> result = chargebackService.initiateChargeback(
                    paymentId, ChargebackReason.FRAUDULENT, amount, "user");

            // Then
            assertThatThrownBy(result::get)
                    .hasCauseInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to initiate chargeback");

            verify(chargebackRepository, times(2)).save(any(Chargeback.class)); // Initial save + failure update
        }
    }

    @Nested
    @DisplayName("External Chargeback Tests")
    class ExternalChargebackTests {

        @Test
        @DisplayName("Should process external chargeback successfully")
        void shouldProcessExternalChargebackSuccessfully() {
            // Given
            String networkChargebackId = "network-123";
            String paymentId = "payment-456";
            String cardNetwork = "VISA";
            BigDecimal amount = new BigDecimal("75.00");
            Map<String, Object> networkData = new HashMap<>();
            networkData.put("arn", "12345678901234567890");

            Payment payment = createTestPayment(paymentId, new BigDecimal("100.00"));
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
            when(chargebackRepository.existsByNetworkChargebackId(networkChargebackId)).thenReturn(false);

            Chargeback savedChargeback = createTestChargeback(paymentId, amount, ChargebackReason.FRAUDULENT);
            savedChargeback.setNetworkChargebackId(networkChargebackId);
            when(chargebackRepository.save(any(Chargeback.class))).thenReturn(savedChargeback);

            CompletableFuture<SendResult<String, Object>> kafkaFuture =
                CompletableFuture.completedFuture(mock(SendResult.class));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(kafkaFuture);

            // When
            chargebackService.processExternalChargeback(
                    networkChargebackId, paymentId, ChargebackReason.FRAUDULENT, amount, cardNetwork, networkData);

            // Then
            verify(chargebackRepository).existsByNetworkChargebackId(networkChargebackId);
            verify(chargebackRepository).save(chargebackCaptor.capture());
            verify(kafkaTemplate).send(eq(CHARGEBACK_TOPIC), eq(paymentId), any());

            Chargeback captured = chargebackCaptor.getValue();
            assertThat(captured.getNetworkChargebackId()).isEqualTo(networkChargebackId);
            assertThat(captured.getCardNetwork()).isEqualTo(cardNetwork);
            assertThat(captured.getChargebackAmount()).isEqualByComparingTo(amount);
        }

        @Test
        @DisplayName("Should skip duplicate external chargeback")
        void shouldSkipDuplicateExternalChargeback() {
            // Given
            String networkChargebackId = "network-duplicate";
            when(chargebackRepository.existsByNetworkChargebackId(networkChargebackId)).thenReturn(true);

            // When
            chargebackService.processExternalChargeback(
                    networkChargebackId, "payment-123", ChargebackReason.FRAUDULENT,
                    BigDecimal.TEN, "VISA", new HashMap<>());

            // Then
            verify(chargebackRepository, never()).save(any());
            verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Should handle external chargeback with missing payment")
        void shouldHandleExternalChargebackWithMissingPayment() {
            // Given
            String networkChargebackId = "network-123";
            String paymentId = "non-existent";

            when(chargebackRepository.existsByNetworkChargebackId(networkChargebackId)).thenReturn(false);
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> chargebackService.processExternalChargeback(
                    networkChargebackId, paymentId, ChargebackReason.FRAUDULENT,
                    BigDecimal.TEN, "VISA", new HashMap<>()))
                    .isInstanceOf(RuntimeException.class);

            verify(chargebackRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Challenge Chargeback Tests")
    class ChallengeChargebackTests {

        @Test
        @DisplayName("Should challenge chargeback successfully")
        void shouldChallengeChargebackSuccessfully() throws Exception {
            // Given
            String chargebackId = "chargeback-123";
            Map<String, Object> evidence = new HashMap<>();
            evidence.put("tracking_number", "TRACK123");
            evidence.put("delivery_signature", "signature.jpg");

            Chargeback chargeback = createTestChargeback("payment-456", new BigDecimal("100.00"),
                    ChargebackReason.PRODUCT_NOT_RECEIVED);
            chargeback.setChargebackId(chargebackId);
            chargeback.setStatus(ChargebackStatus.PENDING);

            when(chargebackRepository.findById(chargebackId)).thenReturn(Optional.of(chargeback));
            when(chargebackRepository.save(any(Chargeback.class))).thenReturn(chargeback);

            CompletableFuture<SendResult<String, Object>> kafkaFuture =
                CompletableFuture.completedFuture(mock(SendResult.class));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(kafkaFuture);

            // When
            CompletableFuture<Chargeback> result = chargebackService.challengeChargeback(
                    chargebackId, evidence, "merchant-admin");
            Chargeback challenged = result.get();

            // Then
            assertThat(challenged).isNotNull();
            verify(chargebackRepository).save(chargebackCaptor.capture());

            Chargeback captured = chargebackCaptor.getValue();
            assertThat(captured.getStatus()).isEqualTo(ChargebackStatus.CHALLENGED);
            assertThat(captured.getChallengedAt()).isNotNull();
            assertThat(captured.getChallengedBy()).isEqualTo("merchant-admin");
            assertThat(captured.getChallengeEvidence()).isEqualTo(evidence);

            verify(kafkaTemplate).send(eq(CHARGEBACK_TOPIC), anyString(), any());
            verify(notificationService).sendChargebackChallengeNotification(any());
            verify(counter).increment();
        }

        @Test
        @DisplayName("Should throw exception when chargeback not found for challenge")
        void shouldThrowExceptionWhenChargebackNotFoundForChallenge() {
            // Given
            String chargebackId = "non-existent";
            when(chargebackRepository.findById(chargebackId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> chargebackService.challengeChargeback(
                    chargebackId, new HashMap<>(), "merchant"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Chargeback not found");

            verify(chargebackRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject challenge for non-challengeable status")
        void shouldRejectChallengeForNonChallengeableStatus() {
            // Given
            String chargebackId = "chargeback-123";
            Chargeback chargeback = createTestChargeback("payment-456", new BigDecimal("100.00"),
                    ChargebackReason.FRAUDULENT);
            chargeback.setChargebackId(chargebackId);
            chargeback.setStatus(ChargebackStatus.WON); // Already resolved

            when(chargebackRepository.findById(chargebackId)).thenReturn(Optional.of(chargeback));

            // When/Then
            assertThatThrownBy(() -> chargebackService.challengeChargeback(
                    chargebackId, new HashMap<>(), "merchant"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot be challenged");

            verify(chargebackRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Accept Chargeback Tests")
    class AcceptChargebackTests {

        @Test
        @DisplayName("Should accept chargeback and process refund")
        void shouldAcceptChargebackAndProcessRefund() {
            // Given
            String chargebackId = "chargeback-123";
            String paymentId = "payment-456";
            BigDecimal amount = new BigDecimal("100.00");

            Chargeback chargeback = createTestChargeback(paymentId, amount, ChargebackReason.FRAUDULENT);
            chargeback.setChargebackId(chargebackId);
            chargeback.setStatus(ChargebackStatus.PENDING);

            Payment payment = createTestPayment(paymentId, amount);

            when(chargebackRepository.findById(chargebackId)).thenReturn(Optional.of(chargeback));
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
            when(chargebackRepository.save(any(Chargeback.class))).thenReturn(chargeback);

            CompletableFuture<SendResult<String, Object>> kafkaFuture =
                CompletableFuture.completedFuture(mock(SendResult.class));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(kafkaFuture);

            // When
            Chargeback accepted = chargebackService.acceptChargeback(chargebackId, "admin-user");

            // Then
            assertThat(accepted).isNotNull();
            verify(chargebackRepository).save(chargebackCaptor.capture());

            Chargeback captured = chargebackCaptor.getValue();
            assertThat(captured.getStatus()).isEqualTo(ChargebackStatus.ACCEPTED);
            assertThat(captured.getResolvedAt()).isNotNull();
            assertThat(captured.getResolvedBy()).isEqualTo("admin-user");

            // Verify refund processing
            verify(walletService).debitWallet(eq(payment.getMerchantId()), eq(amount), anyString());
            verify(walletService).creditWallet(eq(payment.getUserId()), eq(amount), anyString());
            verify(paymentRepository).save(paymentCaptor.capture());

            Payment updatedPayment = paymentCaptor.getValue();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.CHARGEBACK_REFUNDED);

            verify(kafkaTemplate).send(eq(CHARGEBACK_TOPIC), eq(paymentId), any());
            verify(counter).increment();
        }

        @Test
        @DisplayName("Should throw exception when chargeback not found for acceptance")
        void shouldThrowExceptionWhenChargebackNotFoundForAcceptance() {
            // Given
            String chargebackId = "non-existent";
            when(chargebackRepository.findById(chargebackId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> chargebackService.acceptChargeback(chargebackId, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Chargeback not found");

            verify(chargebackRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Resolve Chargeback Tests")
    class ResolveChargebackTests {

        @Test
        @DisplayName("Should resolve chargeback as won")
        void shouldResolveChargebackAsWon() {
            // Given
            String chargebackId = "chargeback-123";
            String paymentId = "payment-456";
            BigDecimal amount = new BigDecimal("100.00");

            Chargeback chargeback = createTestChargeback(paymentId, amount, ChargebackReason.FRAUDULENT);
            chargeback.setChargebackId(chargebackId);
            chargeback.setStatus(ChargebackStatus.CHALLENGED);

            when(chargebackRepository.findById(chargebackId)).thenReturn(Optional.of(chargeback));
            when(chargebackRepository.save(any(Chargeback.class))).thenReturn(chargeback);

            CompletableFuture<SendResult<String, Object>> kafkaFuture =
                CompletableFuture.completedFuture(mock(SendResult.class));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(kafkaFuture);

            // When
            chargebackService.resolveChargeback(chargebackId, true, "Evidence accepted by network");

            // Then
            verify(chargebackRepository).save(chargebackCaptor.capture());

            Chargeback captured = chargebackCaptor.getValue();
            assertThat(captured.getStatus()).isEqualTo(ChargebackStatus.WON);
            assertThat(captured.getResolvedAt()).isNotNull();
            assertThat(captured.getResolutionDetails()).isEqualTo("Evidence accepted by network");

            // Should NOT process refund when won
            verify(walletService, never()).debitWallet(anyString(), any(), anyString());
            verify(walletService, never()).creditWallet(anyString(), any(), anyString());

            verify(kafkaTemplate).send(eq(CHARGEBACK_TOPIC), eq(paymentId), any());
            verify(notificationService).sendChargebackResolutionNotification(chargeback, true);
            verify(counter).increment();
        }

        @Test
        @DisplayName("Should resolve chargeback as lost and process refund")
        void shouldResolveChargebackAsLostAndProcessRefund() {
            // Given
            String chargebackId = "chargeback-123";
            String paymentId = "payment-456";
            BigDecimal amount = new BigDecimal("100.00");

            Chargeback chargeback = createTestChargeback(paymentId, amount, ChargebackReason.FRAUDULENT);
            chargeback.setChargebackId(chargebackId);
            chargeback.setStatus(ChargebackStatus.CHALLENGED);

            Payment payment = createTestPayment(paymentId, amount);

            when(chargebackRepository.findById(chargebackId)).thenReturn(Optional.of(chargeback));
            when(chargebackRepository.save(any(Chargeback.class))).thenReturn(chargeback);
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

            CompletableFuture<SendResult<String, Object>> kafkaFuture =
                CompletableFuture.completedFuture(mock(SendResult.class));
            when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(kafkaFuture);

            // When
            chargebackService.resolveChargeback(chargebackId, false, "Evidence insufficient");

            // Then
            verify(chargebackRepository).save(chargebackCaptor.capture());

            Chargeback captured = chargebackCaptor.getValue();
            assertThat(captured.getStatus()).isEqualTo(ChargebackStatus.LOST);
            assertThat(captured.getResolvedAt()).isNotNull();

            // Should process refund when lost
            verify(walletService).debitWallet(eq(payment.getMerchantId()), eq(amount), anyString());
            verify(walletService).creditWallet(eq(payment.getUserId()), eq(amount), anyString());

            verify(kafkaTemplate).send(eq(CHARGEBACK_TOPIC), eq(paymentId), any());
            verify(notificationService).sendChargebackResolutionNotification(chargeback, false);
            verify(counter).increment();
        }

        @Test
        @DisplayName("Should throw exception when chargeback not found for resolution")
        void shouldThrowExceptionWhenChargebackNotFoundForResolution() {
            // Given
            String chargebackId = "non-existent";
            when(chargebackRepository.findById(chargebackId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> chargebackService.resolveChargeback(chargebackId, true, "details"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Chargeback not found");

            verify(chargebackRepository, never()).save(any());
        }
    }

    // Helper methods

    private Payment createTestPayment(String paymentId, BigDecimal amount) {
        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setAmount(amount);
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setMerchantId("merchant-123");
        payment.setUserId("user-456");
        payment.setPaymentMethod("CARD");
        payment.setCardLast4("4242");
        payment.setMerchantName("Test Merchant");
        payment.setCreatedAt(Instant.now());
        return payment;
    }

    private Chargeback createTestChargeback(String paymentId, BigDecimal amount, ChargebackReason reason) {
        return Chargeback.builder()
                .chargebackId(UUID.randomUUID().toString())
                .paymentId(paymentId)
                .merchantId("merchant-123")
                .userId("user-456")
                .originalAmount(amount)
                .chargebackAmount(amount)
                .currency("USD")
                .reason(reason)
                .status(ChargebackStatus.PENDING)
                .initiatedAt(Instant.now())
                .initiatedBy("system")
                .build();
    }
}
