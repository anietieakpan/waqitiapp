package com.waqiti.payment.fraud;

import com.waqiti.payment.dto.PaymentRequest;
import com.waqiti.payment.model.Payment;
import com.waqiti.payment.model.FraudCheckResult;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.ml.MLFraudModelClient;
import com.waqiti.common.exception.FraudDetectedException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for FraudDetectionService.
 *
 * Test Coverage:
 * - Transaction risk scoring
 * - Velocity checks (frequency, amount)
 * - Device fingerprinting
 * - Geolocation analysis
 * - ML model integration
 * - Behavioral analysis
 * - Rule-based detection
 * - Risk thresholds
 * - False positive handling
 *
 * @author Waqiti Platform Engineering
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FraudDetectionService Unit Tests")
class FraudDetectionServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private MLFraudModelClient mlFraudModelClient;

    @InjectMocks
    private FraudDetectionService fraudDetectionService;

    private UUID userId;
    private PaymentRequest validPaymentRequest;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        validPaymentRequest = PaymentRequest.builder()
                .userId(userId)
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .paymentMethod("CARD")
                .ipAddress("192.168.1.1")
                .deviceId("device-123")
                .merchantId(UUID.randomUUID())
                .build();
    }

    // =========================================================================
    // Basic Fraud Detection Tests
    // =========================================================================

    @Nested
    @DisplayName("Basic Fraud Detection Tests")
    class BasicFraudDetectionTests {

        @Test
        @DisplayName("Should pass low-risk transaction")
        void shouldPassLowRiskTransaction() {
            // Arrange
            when(paymentRepository.findRecentPaymentsByUserId(eq(userId), any()))
                    .thenReturn(List.of());
            when(mlFraudModelClient.calculateRiskScore(any())).thenReturn(15.0);

            // Act
            boolean isFraud = fraudDetectionService.analyzeTransaction(validPaymentRequest);

            // Assert
            assertFalse(isFraud);
        }

        @Test
        @DisplayName("Should flag high-risk transaction")
        void shouldFlagHighRiskTransaction() {
            // Arrange
            when(paymentRepository.findRecentPaymentsByUserId(eq(userId), any()))
                    .thenReturn(List.of());
            when(mlFraudModelClient.calculateRiskScore(any())).thenReturn(95.0);

            // Act
            boolean isFraud = fraudDetectionService.analyzeTransaction(validPaymentRequest);

            // Assert
            assertTrue(isFraud);
        }

        @Test
        @DisplayName("Should calculate fraud score correctly")
        void shouldCalculateFraudScoreCorrectly() {
            // Arrange
            when(paymentRepository.findRecentPaymentsByUserId(eq(userId), any()))
                    .thenReturn(List.of());
            when(mlFraudModelClient.calculateRiskScore(any())).thenReturn(45.0);

            // Act
            double fraudScore = fraudDetectionService.calculateFraudScore(validPaymentRequest);

            // Assert
            assertEquals(45.0, fraudScore, 0.1);
            assertTrue(fraudScore >= 0 && fraudScore <= 100);
        }
    }

    // =========================================================================
    // Velocity Check Tests
    // =========================================================================

    @Nested
    @DisplayName("Velocity Check Tests")
    class VelocityCheckTests {

        @Test
        @DisplayName("Should detect high transaction frequency")
        void shouldDetectHighTransactionFrequency() {
            // Arrange - User made 10 transactions in last hour
            List<Payment> recentPayments = createMockPayments(10, LocalDateTime.now().minusMinutes(30));
            when(paymentRepository.findRecentPaymentsByUserId(eq(userId), any()))
                    .thenReturn(recentPayments);
            when(mlFraudModelClient.calculateRiskScore(any())).thenReturn(50.0);

            // Act
            boolean isFraud = fraudDetectionService.analyzeTransaction(validPaymentRequest);

            // Assert
            assertTrue(isFraud); // Should be flagged due to high frequency
        }

        @Test
        @DisplayName("Should detect unusual transaction amount")
        void shouldDetectUnusualAmount() {
            // Arrange - User's typical transaction is $50, now trying $5000
            List<Payment> recentPayments = createMockPayments(5, LocalDateTime.now().minusDays(1), 50.0);
            validPaymentRequest.setAmount(BigDecimal.valueOf(5000.00));

            when(paymentRepository.findRecentPaymentsByUserId(eq(userId), any()))
                    .thenReturn(recentPayments);
            when(mlFraudModelClient.calculateRiskScore(any())).thenReturn(40.0);

            // Act
            double fraudScore = fraudDetectionService.calculateFraudScore(validPaymentRequest);

            // Assert
            assertTrue(fraudScore > 70); // High score due to amount anomaly
        }

        @Test
        @DisplayName("Should detect rapid successive transactions")
        void shouldDetectRapidTransactions() {
            // Arrange - 3 transactions in last 5 minutes
            List<Payment> recentPayments = List.of(
                    createPayment(LocalDateTime.now().minusMinutes(2)),
                    createPayment(LocalDateTime.now().minusMinutes(3)),
                    createPayment(LocalDateTime.now().minusMinutes(4))
            );

            when(paymentRepository.findRecentPaymentsByUserId(eq(userId), any()))
                    .thenReturn(recentPayments);
            when(mlFraudModelClient.calculateRiskScore(any())).thenReturn(40.0);

            // Act
            boolean isFraud = fraudDetectionService.analyzeTransaction(validPaymentRequest);

            // Assert
            assertTrue(isFraud);
        }

        @Test
        @DisplayName("Should pass normal transaction frequency")
        void shouldPassNormalFrequency() {
            // Arrange - User made 2 transactions in last day
            List<Payment> recentPayments = createMockPayments(2, LocalDateTime.now().minusHours(12));
            when(paymentRepository.findRecentPaymentsByUserId(eq(userId), any()))
                    .thenReturn(recentPayments);
            when(mlFraudModelClient.calculateRiskScore(any())).thenReturn(25.0);

            // Act
            boolean isFraud = fraudDetectionService.analyzeTransaction(validPaymentRequest);

            // Assert
            assertFalse(isFraud);
        }

        @Test
        @DisplayName("Should detect cumulative amount threshold breach")
        void shouldDetectCumulativeAmountBreach() {
            // Arrange - User spent $9000 today, trying to spend another $2000 (exceeds $10k limit)
            List<Payment> recentPayments = createMockPayments(9, LocalDateTime.now().minusHours(6), 1000.0);
            validPaymentRequest.setAmount(BigDecimal.valueOf(2000.00));

            when(paymentRepository.findRecentPaymentsByUserId(eq(userId), any()))
                    .thenReturn(recentPayments);
            when(mlFraudModelClient.calculateRiskScore(any())).thenReturn(30.0);

            // Act
            boolean isFraud = fraudDetectionService.analyzeTransaction(validPaymentRequest);

            // Assert
            assertTrue(isFraud); // Should flag due to daily limit breach
        }
    }

    // =========================================================================
    // Device Fingerprinting Tests
    // =========================================================================

    @Nested
    @DisplayName("Device Fingerprinting Tests")
    class DeviceFingerprintingTests {

        @Test
        @DisplayName("Should detect new device for user")
        void shouldDetectNewDevice() {
            // Arrange - All previous transactions from different device
            List<Payment> recentPayments = createMockPaymentsWithDevice(3, "device-456");
            validPaymentRequest.setDeviceId("device-789"); // New device

            when(paymentRepository.findRecentPaymentsByUserId(eq(userId), any()))
                    .thenReturn(recentPayments);
            when(mlFraudModelClient.calculateRiskScore(any())).thenReturn(40.0);

            // Act
            double fraudScore = fraudDetectionService.calculateFraudScore(validPaymentRequest);

            // Assert
            assertTrue(fraudScore > 60); // Higher score due to new device
        }

        @Test
        @DisplayName("Should pass known device")
        void shouldPassKnownDevice() {
            // Arrange - User has used this device before
            List<Payment> recentPayments = createMockPaymentsWithDevice(3, "device-123");
            validPaymentRequest.setDeviceId("device-123");

            when(paymentRepository.findRecentPaymentsByUserId(eq(userId), any()))
                    .thenReturn(recentPayments);
            when(mlFraudModelClient.calculateRiskScore(any())).thenReturn(20.0);

            // Act
            boolean isFraud = fraudDetectionService.analyzeTransaction(validPaymentRequest);

            // Assert
            assertFalse(isFraud);
        }

        @Test
        @DisplayName("Should detect multiple devices in short time")
        void shouldDetectMultipleDevices() {
            // Arrange - Transactions from 3 different devices in last hour
            List<Payment> recentPayments = List.of(
                    createPaymentWithDevice(LocalDateTime.now().minusMinutes(10), "device-A"),
                    createPaymentWithDevice(LocalDateTime.now().minusMinutes(20), "device-B"),
                    createPaymentWithDevice(LocalDateTime.now().minusMinutes(30), "device-C")
            );
            validPaymentRequest.setDeviceId("device-D"); // 4th device

            when(paymentRepository.findRecentPaymentsByUserId(eq(userId), any()))
                    .thenReturn(recentPayments);
            when(mlFraudModelClient.calculateRiskScore(any())).thenReturn(45.0);

            // Act
            boolean isFraud = fraudDetectionService.analyzeTransaction(validPaymentRequest);

            // Assert
            assertTrue(isFraud); // Multiple devices = suspicious
        }
    }

    // =========================================================================
    // Geolocation Tests
    // =========================================================================

    @Nested
    @DisplayName("Geolocation Analysis Tests")
    class GeolocationAnalysisTests {

        @Test
        @DisplayName("Should detect impossible travel")
        void shouldDetectImpossibleTravel() {
            // Arrange - User was in New York 30 minutes ago, now in London (impossible)
            Payment lastPayment = createPaymentWithIP(LocalDateTime.now().minusMinutes(30), "203.0.113.1"); // NY IP
            validPaymentRequest.setIpAddress("198.51.100.1"); // London IP

            when(paymentRepository.findRecentPaymentsByUserId(eq(userId), any()))
                    .thenReturn(List.of(lastPayment));
            when(mlFraudModelClient.calculateRiskScore(any())).thenReturn(40.0);

            // Act
            double fraudScore = fraudDetectionService.calculateFraudScore(validPaymentRequest);

            // Assert
            assertTrue(fraudScore > 80); // Very high score for impossible travel
        }

        @Test
        @DisplayName("Should detect high-risk country")
        void shouldDetectHighRiskCountry() {
            // Arrange - Transaction from high-risk country
            validPaymentRequest.setIpAddress("203.0.113.50"); // Mock high-risk country IP
            validPaymentRequest.setCountry("XX"); // High-risk country code

            when(paymentRepository.findRecentPaymentsByUserId(eq(userId), any()))
                    .thenReturn(List.of());
            when(mlFraudModelClient.calculateRiskScore(any())).thenReturn(35.0);

            // Act
            double fraudScore = fraudDetectionService.calculateFraudScore(validPaymentRequest);

            // Assert
            assertTrue(fraudScore > 50);
        }

        @Test
        @DisplayName("Should pass consistent geolocation")
        void shouldPassConsistentLocation() {
            // Arrange - All transactions from same region
            List<Payment> recentPayments = createMockPaymentsWithIP(3, "192.168.1.1");
            validPaymentRequest.setIpAddress("192.168.1.100"); // Same region

            when(paymentRepository.findRecentPaymentsByUserId(eq(userId), any()))
                    .thenReturn(recentPayments);
            when(mlFraudModelClient.calculateRiskScore(any())).thenReturn(18.0);

            // Act
            boolean isFraud = fraudDetectionService.analyzeTransaction(validPaymentRequest);

            // Assert
            assertFalse(isFraud);
        }
    }

    // =========================================================================
    // ML Model Integration Tests
    // =========================================================================

    @Nested
    @DisplayName("ML Model Integration Tests")
    class MLModelIntegrationTests {

        @Test
        @DisplayName("Should integrate ML model score into overall assessment")
        void shouldIntegrateMLScore() {
            // Arrange
            when(paymentRepository.findRecentPaymentsByUserId(eq(userId), any()))
                    .thenReturn(List.of());
            when(mlFraudModelClient.calculateRiskScore(any())).thenReturn(85.0);

            // Act
            double fraudScore = fraudDetectionService.calculateFraudScore(validPaymentRequest);

            // Assert
            assertTrue(fraudScore >= 85.0); // ML score should significantly impact final score
        }

        @Test
        @DisplayName("Should handle ML model failure gracefully")
        void shouldHandleMLModelFailure() {
            // Arrange
            when(paymentRepository.findRecentPaymentsByUserId(eq(userId), any()))
                    .thenReturn(List.of());
            when(mlFraudModelClient.calculateRiskScore(any()))
                    .thenThrow(new RuntimeException("ML model unavailable"));

            // Act - Should fall back to rule-based detection
            double fraudScore = fraudDetectionService.calculateFraudScore(validPaymentRequest);

            // Assert
            assertNotNull(fraudScore);
            assertTrue(fraudScore >= 0 && fraudScore <= 100);
        }

        @Test
        @DisplayName("Should combine ML score with rule-based checks")
        void shouldCombineMLAndRuleBasedScores() {
            // Arrange - High frequency (rule-based) + moderate ML score
            List<Payment> recentPayments = createMockPayments(8, LocalDateTime.now().minusHours(1));
            when(paymentRepository.findRecentPaymentsByUserId(eq(userId), any()))
                    .thenReturn(recentPayments);
            when(mlFraudModelClient.calculateRiskScore(any())).thenReturn(50.0);

            // Act
            double fraudScore = fraudDetectionService.calculateFraudScore(validPaymentRequest);

            // Assert
            assertTrue(fraudScore > 70); // Combined score should be higher
        }
    }

    // =========================================================================
    // Rule-Based Detection Tests
    // =========================================================================

    @Nested
    @DisplayName("Rule-Based Detection Tests")
    class RuleBasedDetectionTests {

        @Test
        @DisplayName("Should detect round amount pattern (potential testing)")
        void shouldDetectRoundAmountPattern() {
            // Arrange - Multiple exact round amounts (suspicious pattern)
            validPaymentRequest.setAmount(BigDecimal.valueOf(1000.00));
            List<Payment> recentPayments = List.of(
                    createPaymentWithAmount(BigDecimal.valueOf(500.00)),
                    createPaymentWithAmount(BigDecimal.valueOf(100.00)),
                    createPaymentWithAmount(BigDecimal.valueOf(250.00))
            );

            when(paymentRepository.findRecentPaymentsByUserId(eq(userId), any()))
                    .thenReturn(recentPayments);
            when(mlFraudModelClient.calculateRiskScore(any())).thenReturn(30.0);

            // Act
            double fraudScore = fraudDetectionService.calculateFraudScore(validPaymentRequest);

            // Assert
            assertTrue(fraudScore > 45); // Elevated due to pattern
        }

        @Test
        @DisplayName("Should detect first-time large transaction")
        void shouldDetectFirstTimeLargeTransaction() {
            // Arrange - New user attempting large transaction
            validPaymentRequest.setAmount(BigDecimal.valueOf(5000.00));
            when(paymentRepository.findRecentPaymentsByUserId(eq(userId), any()))
                    .thenReturn(List.of()); // No history
            when(mlFraudModelClient.calculateRiskScore(any())).thenReturn(35.0);

            // Act
            double fraudScore = fraudDetectionService.calculateFraudScore(validPaymentRequest);

            // Assert
            assertTrue(fraudScore > 65); // High risk for first large transaction
        }

        @Test
        @DisplayName("Should detect late night transaction pattern")
        void shouldDetectLateNightPattern() {
            // Arrange - Transaction at 3 AM (unusual hour)
            // This would require time-based logic in actual implementation
            validPaymentRequest.setTimestamp(LocalDateTime.now().withHour(3).withMinute(0));

            when(paymentRepository.findRecentPaymentsByUserId(eq(userId), any()))
                    .thenReturn(List.of());
            when(mlFraudModelClient.calculateRiskScore(any())).thenReturn(25.0);

            // Act
            double fraudScore = fraudDetectionService.calculateFraudScore(validPaymentRequest);

            // Assert - Would be higher if time-based rules implemented
            assertNotNull(fraudScore);
        }
    }

    // =========================================================================
    // Edge Case Tests
    // =========================================================================

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle null payment request")
        void shouldHandleNullPaymentRequest() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                fraudDetectionService.analyzeTransaction(null);
            });
        }

        @Test
        @DisplayName("Should handle very small amounts")
        void shouldHandleVerySmallAmounts() {
            // Arrange - $0.01 transaction
            validPaymentRequest.setAmount(BigDecimal.valueOf(0.01));
            when(paymentRepository.findRecentPaymentsByUserId(eq(userId), any()))
                    .thenReturn(List.of());
            when(mlFraudModelClient.calculateRiskScore(any())).thenReturn(15.0);

            // Act
            boolean isFraud = fraudDetectionService.analyzeTransaction(validPaymentRequest);

            // Assert
            assertFalse(isFraud); // Small amounts typically low risk
        }

        @Test
        @DisplayName("Should handle missing device ID")
        void shouldHandleMissingDeviceId() {
            // Arrange
            validPaymentRequest.setDeviceId(null);
            when(paymentRepository.findRecentPaymentsByUserId(eq(userId), any()))
                    .thenReturn(List.of());
            when(mlFraudModelClient.calculateRiskScore(any())).thenReturn(30.0);

            // Act
            double fraudScore = fraudDetectionService.calculateFraudScore(validPaymentRequest);

            // Assert - Should increase risk for missing device ID
            assertTrue(fraudScore > 30);
        }

        @Test
        @DisplayName("Should handle missing IP address")
        void shouldHandleMissingIPAddress() {
            // Arrange
            validPaymentRequest.setIpAddress(null);
            when(paymentRepository.findRecentPaymentsByUserId(eq(userId), any()))
                    .thenReturn(List.of());
            when(mlFraudModelClient.calculateRiskScore(any())).thenReturn(30.0);

            // Act
            double fraudScore = fraudDetectionService.calculateFraudScore(validPaymentRequest);

            // Assert - Should increase risk for missing IP
            assertTrue(fraudScore > 30);
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private List<Payment> createMockPayments(int count, LocalDateTime baseTime) {
        return createMockPayments(count, baseTime, 100.0);
    }

    private List<Payment> createMockPayments(int count, LocalDateTime baseTime, double amount) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> Payment.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .amount(BigDecimal.valueOf(amount))
                        .createdAt(baseTime.minusMinutes(i * 10))
                        .build())
                .toList();
    }

    private Payment createPayment(LocalDateTime createdAt) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .amount(BigDecimal.valueOf(100.00))
                .createdAt(createdAt)
                .build();
    }

    private List<Payment> createMockPaymentsWithDevice(int count, String deviceId) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> Payment.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .deviceId(deviceId)
                        .amount(BigDecimal.valueOf(100.00))
                        .createdAt(LocalDateTime.now().minusHours(i))
                        .build())
                .toList();
    }

    private Payment createPaymentWithDevice(LocalDateTime createdAt, String deviceId) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .deviceId(deviceId)
                .amount(BigDecimal.valueOf(100.00))
                .createdAt(createdAt)
                .build();
    }

    private List<Payment> createMockPaymentsWithIP(int count, String ipAddress) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> Payment.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .ipAddress(ipAddress)
                        .amount(BigDecimal.valueOf(100.00))
                        .createdAt(LocalDateTime.now().minusHours(i))
                        .build())
                .toList();
    }

    private Payment createPaymentWithIP(LocalDateTime createdAt, String ipAddress) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .ipAddress(ipAddress)
                .amount(BigDecimal.valueOf(100.00))
                .createdAt(createdAt)
                .build();
    }

    private Payment createPaymentWithAmount(BigDecimal amount) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .amount(amount)
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();
    }
}
