package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentHoldEvent;
import com.waqiti.common.events.PaymentStatusUpdatedEvent;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.domain.PaymentHold;
import com.waqiti.payment.domain.HoldType;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.repository.PaymentHoldRepository;
import com.waqiti.payment.service.PaymentHoldService;
import com.waqiti.payment.service.AuthorizationHoldService;
import com.waqiti.payment.service.FraudHoldService;
import com.waqiti.payment.service.ComplianceHoldService;
import com.waqiti.payment.metrics.HoldMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.fraud.FraudService;
import com.waqiti.ledger.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///waqiti_test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "payment.hold.authorization.duration.days=7",
        "payment.hold.fraud.duration.days=14",
        "payment.hold.compliance.duration.days=30",
        "payment.hold.regulatory.duration.days=90",
        "payment.hold.high-value.threshold=10000",
        "payment.hold.regulatory.threshold=50000",
        "payment.hold.suspicious.threshold=25000"
})
@DisplayName("Payment Hold Events Consumer Tests")
class PaymentHoldEventsConsumerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("waqiti_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private PaymentHoldEventsConsumer holdEventsConsumer;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentHoldRepository holdRepository;

    @MockBean
    private PaymentHoldService holdService;

    @MockBean
    private AuthorizationHoldService authHoldService;

    @MockBean
    private FraudHoldService fraudHoldService;

    @MockBean
    private ComplianceHoldService complianceHoldService;

    @MockBean
    private HoldMetricsService metricsService;

    @MockBean
    private AuditService auditService;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private SecurityContext securityContext;

    @MockBean
    private FraudService fraudService;

    @MockBean
    private LedgerService ledgerService;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private Acknowledgment acknowledgment;

    private String testPaymentId;
    private Payment testPayment;
    private String testCustomerId;
    private String testMerchantId;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        holdRepository.deleteAll();

        testPaymentId = UUID.randomUUID().toString();
        testCustomerId = UUID.randomUUID().toString();
        testMerchantId = UUID.randomUUID().toString();

        testPayment = createTestPayment();
        testPayment = paymentRepository.save(testPayment);

        // Mock default behaviors
        when(fraudService.isSuspiciousHoldPattern(any())).thenReturn(false);
        when(authHoldService.placeAuthorizationHold(any(), any(), any()))
                .thenReturn("gw-hold-" + UUID.randomUUID().toString());
        when(kafkaTemplate.send(anyString(), any())).thenReturn(mock(CompletableFuture.class));
    }

    @Nested
    @DisplayName("Authorization Hold Tests")
    class AuthorizationHoldTests {

        @Test
        @Transactional
        @DisplayName("Should place authorization hold successfully")
        void shouldPlaceAuthorizationHoldSuccessfully() {
            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "AUTHORIZATION");
            event.setHoldAmount(new BigDecimal("1000.00"));
            event.setHoldReason("Pre-authorization for hotel booking");

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            Payment updatedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.HELD);
            assertThat(updatedPayment.getHoldType()).isEqualTo("AUTHORIZATION");
            assertThat(updatedPayment.getHoldAmount()).isEqualTo(new BigDecimal("1000.00"));
            assertThat(updatedPayment.getHoldReason()).isEqualTo("Pre-authorization for hotel booking");
            assertThat(updatedPayment.getHoldExpirationDate()).isAfter(LocalDateTime.now().plusDays(6));

            List<PaymentHold> holds = holdRepository.findAll();
            assertThat(holds).hasSize(1);
            
            PaymentHold hold = holds.get(0);
            assertThat(hold.getPaymentId()).isEqualTo(testPaymentId);
            assertThat(hold.getHoldType()).isEqualTo("AUTHORIZATION");
            assertThat(hold.getStatus()).isEqualTo("ACTIVE");

            verify(authHoldService).placeAuthorizationHold(eq(testPaymentId), eq(new BigDecimal("1000.00")), anyString());
            verify(ledgerService).recordHoldPlacement(eq(testPaymentId), anyString(), eq(new BigDecimal("1000.00")), eq("AUTHORIZATION"), anyString());
            verify(metricsService).recordHoldPlaced("AUTHORIZATION", new BigDecimal("1000.00"));
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should release authorization hold successfully")
        void shouldReleaseAuthorizationHoldSuccessfully() {
            // First place a hold
            PaymentHold existingHold = createTestHold("AUTHORIZATION");
            existingHold = holdRepository.save(existingHold);
            
            testPayment.setStatus(PaymentStatus.HELD);
            testPayment.setHoldId(existingHold.getId());
            paymentRepository.save(testPayment);

            PaymentHoldEvent releaseEvent = createHoldEvent("RELEASE_HOLD", "AUTHORIZATION");
            releaseEvent.setReleaseReason("Customer verification completed");

            holdEventsConsumer.handlePaymentHoldEvent(releaseEvent, 0, 0L, "topic", acknowledgment);

            PaymentHold updatedHold = holdRepository.findById(existingHold.getId()).orElseThrow();
            assertThat(updatedHold.getStatus()).isEqualTo("RELEASED");
            assertThat(updatedHold.getReleasedAt()).isNotNull();
            assertThat(updatedHold.getReleaseReason()).isEqualTo("Customer verification completed");

            Payment updatedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
            assertThat(updatedPayment.getHoldReleasedAt()).isNotNull();

            verify(authHoldService).releaseAuthorizationHold(anyString(), eq("Customer verification completed"), anyString());
            verify(ledgerService).recordHoldRelease(eq(testPaymentId), eq(existingHold.getId()), any(), eq("Customer verification completed"), anyString());
            verify(metricsService).recordHoldReleased("AUTHORIZATION", any());
        }

        @Test
        @Transactional
        @DisplayName("Should handle authorization hold extension")
        void shouldHandleAuthorizationHoldExtension() {
            PaymentHold existingHold = createTestHold("AUTHORIZATION");
            existingHold.setExpiresAt(LocalDateTime.now().plusDays(3));
            existingHold = holdRepository.save(existingHold);
            
            testPayment.setStatus(PaymentStatus.HELD);
            testPayment.setHoldId(existingHold.getId());
            paymentRepository.save(testPayment);

            LocalDateTime newExpiration = LocalDateTime.now().plusDays(10);
            PaymentHoldEvent extensionEvent = createHoldEvent("EXTEND_HOLD", "AUTHORIZATION");
            extensionEvent.setNewExpirationDate(newExpiration);
            extensionEvent.setExtensionReason("Hotel stay extended");

            holdEventsConsumer.handlePaymentHoldEvent(extensionEvent, 0, 0L, "topic", acknowledgment);

            PaymentHold updatedHold = holdRepository.findById(existingHold.getId()).orElseThrow();
            assertThat(updatedHold.getExpiresAt()).isEqualTo(newExpiration);
            assertThat(updatedHold.getExtendedAt()).isNotNull();
            assertThat(updatedHold.getExtensionReason()).isEqualTo("Hotel stay extended");

            Payment updatedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updatedPayment.getHoldExpirationDate()).isEqualTo(newExpiration);

            verify(metricsService).recordHoldExtended("AUTHORIZATION");
        }

        @Test
        @Transactional
        @DisplayName("Should reject hold when payment amount is exceeded")
        void shouldRejectHoldWhenPaymentAmountIsExceeded() {
            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "AUTHORIZATION");
            event.setHoldAmount(new BigDecimal("2000.00")); // Greater than payment amount

            assertThatCode(() -> 
                holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment)
            ).doesNotThrowAnyException();

            // Verify hold was not created
            List<PaymentHold> holds = holdRepository.findAll();
            assertThat(holds).isEmpty();

            // Payment status should remain unchanged
            Payment updatedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        @Transactional
        @DisplayName("Should handle partial authorization hold release")
        void shouldHandlePartialAuthorizationHoldRelease() {
            PaymentHold existingHold = createTestHold("AUTHORIZATION");
            existingHold.setHoldAmount(new BigDecimal("1000.00"));
            existingHold = holdRepository.save(existingHold);
            
            testPayment.setStatus(PaymentStatus.HELD);
            testPayment.setHoldAmount(new BigDecimal("1000.00"));
            paymentRepository.save(testPayment);

            PaymentHoldEvent partialReleaseEvent = createHoldEvent("PARTIAL_RELEASE", "AUTHORIZATION");
            partialReleaseEvent.setReleaseAmount(new BigDecimal("300.00"));

            holdEventsConsumer.handlePaymentHoldEvent(partialReleaseEvent, 0, 0L, "topic", acknowledgment);

            PaymentHold updatedHold = holdRepository.findById(existingHold.getId()).orElseThrow();
            assertThat(updatedHold.getHoldAmount()).isEqualTo(new BigDecimal("700.00"));
            assertThat(updatedHold.isPartiallyReleased()).isTrue();
            assertThat(updatedHold.getLastPartialReleaseAt()).isNotNull();

            Payment updatedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updatedPayment.getHoldAmount()).isEqualTo(new BigDecimal("700.00"));

            verify(ledgerService).recordPartialHoldRelease(eq(testPaymentId), eq(existingHold.getId()), 
                eq(new BigDecimal("300.00")), eq(new BigDecimal("700.00")), anyString());
            verify(metricsService).recordPartialHoldRelease("AUTHORIZATION", new BigDecimal("300.00"));
        }

        @Test
        @Transactional
        @DisplayName("Should handle expired authorization hold")
        void shouldHandleExpiredAuthorizationHold() {
            PaymentHold expiredHold = createTestHold("AUTHORIZATION");
            expiredHold.setExpiresAt(LocalDateTime.now().minusHours(1));
            expiredHold = holdRepository.save(expiredHold);

            PaymentHoldEvent expirationEvent = createHoldEvent("HOLD_EXPIRED", "AUTHORIZATION");
            expirationEvent.setHoldId(expiredHold.getId());
            expirationEvent.setExpirationReason("Automatic expiration");

            holdEventsConsumer.handlePaymentHoldEvent(expirationEvent, 0, 0L, "topic", acknowledgment);

            PaymentHold updatedHold = holdRepository.findById(expiredHold.getId()).orElseThrow();
            assertThat(updatedHold.getStatus()).isEqualTo("RELEASED"); // Should auto-release expired holds
        }
    }

    @Nested
    @DisplayName("Fraud Hold Tests")
    class FraudHoldTests {

        @Test
        @Transactional
        @DisplayName("Should place fraud prevention hold with high risk score")
        void shouldPlaceFraudPreventionHoldWithHighRiskScore() {
            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "FRAUD_PREVENTION");
            event.setRiskScore(85.5);
            event.setHoldReason("High risk transaction pattern detected");

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            Payment updatedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.HELD);
            assertThat(updatedPayment.getHoldType()).isEqualTo("FRAUD_PREVENTION");

            List<PaymentHold> holds = holdRepository.findAll();
            assertThat(holds).hasSize(1);
            
            PaymentHold hold = holds.get(0);
            assertThat(hold.getHoldType()).isEqualTo("FRAUD_PREVENTION");
            assertThat(hold.getRiskScore()).isEqualTo(85.5);
            assertThat(hold.isReviewRequired()).isTrue();
            assertThat(hold.getReviewPriority()).isEqualTo("URGENT");
            assertThat(hold.getReviewBy()).isAfter(LocalDateTime.now().plusHours(3));

            verify(fraudHoldService).submitForReview(eq(hold), eq("URGENT"), anyString());
            verify(notificationService).sendSecurityAlert(
                eq("Urgent Fraud Hold Review Required"), 
                anyString(), 
                eq(NotificationService.Priority.CRITICAL)
            );
        }

        @Test
        @Transactional
        @DisplayName("Should detect suspicious hold manipulation pattern")
        void shouldDetectSuspiciousHoldManipulationPattern() {
            when(fraudService.isSuspiciousHoldPattern(any())).thenReturn(true);

            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "FRAUD_PREVENTION");

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            // Verify suspicious pattern handling
            Payment updatedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.SECURITY_REVIEW);

            verify(notificationService).sendSecurityAlert(
                eq("Suspicious Hold Pattern Detected"), 
                anyString(), 
                eq(NotificationService.Priority.HIGH)
            );
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should categorize fraud hold priority based on amount and risk")
        void shouldCategorizeFraudHoldPriorityBasedOnAmountAndRisk() {
            // Test high value, medium risk
            testPayment.setAmount(new BigDecimal("15000.00"));
            paymentRepository.save(testPayment);

            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "FRAUD_PREVENTION");
            event.setRiskScore(65.0);
            event.setHoldReason("Medium risk, high value transaction");

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            List<PaymentHold> holds = holdRepository.findAll();
            assertThat(holds).hasSize(1);
            
            PaymentHold hold = holds.get(0);
            assertThat(hold.getReviewPriority()).isEqualTo("HIGH");
            assertThat(hold.getReviewBy()).isAfter(LocalDateTime.now().plusHours(23));

            verify(fraudHoldService).submitForReview(eq(hold), eq("HIGH"), anyString());
        }

        @Test
        @Transactional
        @DisplayName("Should handle standard priority fraud hold")
        void shouldHandleStandardPriorityFraudHold() {
            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "FRAUD_PREVENTION");
            event.setRiskScore(45.0);
            event.setHoldReason("Routine fraud screening");

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            List<PaymentHold> holds = holdRepository.findAll();
            assertThat(holds).hasSize(1);
            
            PaymentHold hold = holds.get(0);
            assertThat(hold.getReviewPriority()).isEqualTo("STANDARD");
            assertThat(hold.getReviewBy()).isAfter(LocalDateTime.now().plusHours(23));

            verify(fraudHoldService).submitForReview(eq(hold), eq("STANDARD"), anyString());
            verify(notificationService, never()).sendSecurityAlert(anyString(), anyString(), any());
        }

        @Test
        @Transactional
        @DisplayName("Should set fraud hold expiration to 14 days")
        void shouldSetFraudHoldExpirationTo14Days() {
            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "FRAUD_PREVENTION");

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            Payment updatedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updatedPayment.getHoldExpirationDate()).isAfter(LocalDateTime.now().plusDays(13));
            assertThat(updatedPayment.getHoldExpirationDate()).isBefore(LocalDateTime.now().plusDays(15));

            List<PaymentHold> holds = holdRepository.findAll();
            PaymentHold hold = holds.get(0);
            assertThat(hold.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(13));
        }
    }

    @Nested
    @DisplayName("Compliance Hold Tests")
    class ComplianceHoldTests {

        @Test
        @Transactional
        @DisplayName("Should place compliance hold with regulatory reporting")
        void shouldPlaceComplianceHoldWithRegulatoryReporting() {
            testPayment.setAmount(new BigDecimal("75000.00")); // Above regulatory threshold
            paymentRepository.save(testPayment);

            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "COMPLIANCE");
            event.setHoldReason("Large transaction compliance review");

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            Payment updatedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.HELD);
            assertThat(updatedPayment.getHoldType()).isEqualTo("COMPLIANCE");

            List<PaymentHold> holds = holdRepository.findAll();
            assertThat(holds).hasSize(1);
            
            PaymentHold hold = holds.get(0);
            assertThat(hold.isReviewRequired()).isTrue();
            assertThat(hold.isRegulatoryReportingRequired()).isTrue();
            assertThat(hold.getReviewBy()).isAfter(LocalDateTime.now().plusDays(2));

            verify(complianceHoldService).submitForReview(eq(hold), anyString());
            verify(kafkaTemplate).send(eq("regulatory-reporting-events"), any(Map.class));
        }

        @Test
        @Transactional
        @DisplayName("Should place compliance hold without regulatory reporting for smaller amounts")
        void shouldPlaceComplianceHoldWithoutRegulatoryReportingForSmallerAmounts() {
            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "COMPLIANCE");
            event.setHoldReason("AML screening required");

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            List<PaymentHold> holds = holdRepository.findAll();
            assertThat(holds).hasSize(1);
            
            PaymentHold hold = holds.get(0);
            assertThat(hold.isReviewRequired()).isTrue();
            assertThat(hold.isRegulatoryReportingRequired()).isFalse();

            verify(complianceHoldService).submitForReview(eq(hold), anyString());
            verify(kafkaTemplate, never()).send(eq("regulatory-reporting-events"), any());
        }

        @Test
        @Transactional
        @DisplayName("Should set compliance hold expiration to 30 days")
        void shouldSetComplianceHoldExpirationTo30Days() {
            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "COMPLIANCE");

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            Payment updatedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updatedPayment.getHoldExpirationDate()).isAfter(LocalDateTime.now().plusDays(29));
            assertThat(updatedPayment.getHoldExpirationDate()).isBefore(LocalDateTime.now().plusDays(31));
        }
    }

    @Nested
    @DisplayName("Regulatory Hold Tests")
    class RegulatoryHoldTests {

        @Test
        @Transactional
        @DisplayName("Should place regulatory hold with notification")
        void shouldPlaceRegulatoryHoldWithNotification() {
            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "REGULATORY");
            event.setHoldReason("BSA/AML investigation required");
            event.setRegulationType("BSA");

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            Payment updatedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.HELD);
            assertThat(updatedPayment.getHoldType()).isEqualTo("REGULATORY");

            List<PaymentHold> holds = holdRepository.findAll();
            assertThat(holds).hasSize(1);
            
            PaymentHold hold = holds.get(0);
            assertThat(hold.getRegulationType()).isEqualTo("BSA");
            assertThat(hold.isRegulatoryReportingRequired()).isTrue();
            assertThat(hold.isReviewRequired()).isTrue();

            verify(notificationService).sendComplianceAlert(
                eq("Regulatory Hold Placed"), 
                anyString(), 
                eq(NotificationService.Priority.HIGH)
            );
        }

        @Test
        @Transactional
        @DisplayName("Should set regulatory hold expiration to 90 days")
        void shouldSetRegulatoryHoldExpirationTo90Days() {
            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "REGULATORY");
            event.setRegulationType("OFAC");

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            Payment updatedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updatedPayment.getHoldExpirationDate()).isAfter(LocalDateTime.now().plusDays(89));
            assertThat(updatedPayment.getHoldExpirationDate()).isBefore(LocalDateTime.now().plusDays(91));
        }
    }

    @Nested
    @DisplayName("Manual Review Hold Tests")
    class ManualReviewHoldTests {

        @Test
        @Transactional
        @DisplayName("Should place manual review hold and queue for review")
        void shouldPlaceManualReviewHoldAndQueueForReview() {
            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "MANUAL_REVIEW");
            event.setHoldReason("Unusual transaction pattern");
            event.setRiskScore(70.0);

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            Payment updatedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.HELD);
            assertThat(updatedPayment.getHoldType()).isEqualTo("MANUAL_REVIEW");

            List<PaymentHold> holds = holdRepository.findAll();
            assertThat(holds).hasSize(1);
            
            PaymentHold hold = holds.get(0);
            assertThat(hold.isReviewRequired()).isTrue();
            assertThat(hold.getReviewBy()).isAfter(LocalDateTime.now().plusHours(23));

            verify(kafkaTemplate).send(eq("manual-review-queue"), any(Map.class));
        }
    }

    @Nested
    @DisplayName("Hold Escalation Tests")
    class HoldEscalationTests {

        @Test
        @Transactional
        @DisplayName("Should handle hold escalation event")
        void shouldHandleHoldEscalationEvent() {
            PaymentHoldEvent escalationEvent = createHoldEvent("ESCALATE_HOLD", "FRAUD_PREVENTION");
            escalationEvent.setEscalationReason("Review SLA exceeded");

            assertThatCode(() -> 
                holdEventsConsumer.handlePaymentHoldEvent(escalationEvent, 0, 0L, "topic", acknowledgment)
            ).doesNotThrowAnyException();

            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Customer and Merchant Notification Tests")
    class NotificationTests {

        @Test
        @Transactional
        @DisplayName("Should send customer notification for significant hold amounts")
        void shouldSendCustomerNotificationForSignificantHoldAmounts() {
            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "FRAUD_PREVENTION");
            event.setHoldAmount(new BigDecimal("500.00"));
            event.setHoldReason("Fraud screening");

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            verify(notificationService).sendCustomerNotification(
                eq(testCustomerId),
                eq("Payment PLACED"),
                anyString(),
                eq(NotificationService.Priority.MEDIUM)
            );
        }

        @Test
        @Transactional
        @DisplayName("Should send merchant notification when hold is placed")
        void shouldSendMerchantNotificationWhenHoldIsPlaced() {
            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "COMPLIANCE");

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            verify(notificationService).sendMerchantNotification(
                eq(testMerchantId),
                eq("Payment Hold Notification"),
                anyString(),
                eq(NotificationService.Priority.LOW)
            );
        }

        @Test
        @Transactional
        @DisplayName("Should not send customer notification for small amounts")
        void shouldNotSendCustomerNotificationForSmallAmounts() {
            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "AUTHORIZATION");
            event.setHoldAmount(new BigDecimal("50.00"));

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            verify(notificationService, never()).sendCustomerNotification(anyString(), anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @Transactional
        @DisplayName("Should handle invalid hold event gracefully")
        void shouldHandleInvalidHoldEventGracefully() {
            PaymentHoldEvent invalidEvent = PaymentHoldEvent.builder()
                .paymentId(null) // Invalid - null payment ID
                .holdAction("PLACE_HOLD")
                .timestamp(Instant.now())
                .build();

            assertThatCode(() -> 
                holdEventsConsumer.handlePaymentHoldEvent(invalidEvent, 0, 0L, "topic", acknowledgment)
            ).doesNotThrowAnyException();

            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should handle payment not found error")
        void shouldHandlePaymentNotFoundError() {
            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "AUTHORIZATION");
            event.setPaymentId("non-existent-payment-id");

            assertThatCode(() -> 
                holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment)
            ).doesNotThrowAnyException();

            verify(kafkaTemplate).send(eq("payment-hold-events-dlq"), any(Map.class));
            verify(notificationService).sendOperationalAlert(
                eq("Hold Event Processing Failed"), 
                anyString(), 
                eq(NotificationService.Priority.HIGH)
            );
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should handle authorization service failure")
        void shouldHandleAuthorizationServiceFailure() {
            when(authHoldService.placeAuthorizationHold(any(), any(), any()))
                .thenThrow(new RuntimeException("Gateway unavailable"));

            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "AUTHORIZATION");

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            // Verify hold was created but marked as failed
            List<PaymentHold> holds = holdRepository.findAll();
            assertThat(holds).hasSize(1);
            
            PaymentHold hold = holds.get(0);
            assertThat(hold.getStatus()).isEqualTo("FAILED");
            assertThat(hold.getFailureReason()).contains("Gateway unavailable");
        }

        @Test
        @Transactional
        @DisplayName("Should handle invalid partial release amount")
        void shouldHandleInvalidPartialReleaseAmount() {
            PaymentHold existingHold = createTestHold("AUTHORIZATION");
            existingHold.setHoldAmount(new BigDecimal("1000.00"));
            holdRepository.save(existingHold);

            PaymentHoldEvent partialReleaseEvent = createHoldEvent("PARTIAL_RELEASE", "AUTHORIZATION");
            partialReleaseEvent.setReleaseAmount(new BigDecimal("1500.00")); // Greater than hold amount

            assertThatCode(() -> 
                holdEventsConsumer.handlePaymentHoldEvent(partialReleaseEvent, 0, 0L, "topic", acknowledgment)
            ).doesNotThrowAnyException();

            // Verify hold was not modified
            PaymentHold unchangedHold = holdRepository.findById(existingHold.getId()).orElseThrow();
            assertThat(unchangedHold.getHoldAmount()).isEqualTo(new BigDecimal("1000.00"));
            assertThat(unchangedHold.isPartiallyReleased()).isFalse();

            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Payment Status Update Tests")
    class PaymentStatusUpdateTests {

        @Test
        @Transactional
        @DisplayName("Should publish payment status update when hold is placed")
        void shouldPublishPaymentStatusUpdateWhenHoldIsPlaced() {
            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "AUTHORIZATION");

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            verify(kafkaTemplate).send(eq("payment-status-updated-events"), any(PaymentStatusUpdatedEvent.class));
        }

        @Test
        @Transactional
        @DisplayName("Should publish payment status update when hold is released")
        void shouldPublishPaymentStatusUpdateWhenHoldIsReleased() {
            PaymentHold existingHold = createTestHold("AUTHORIZATION");
            existingHold = holdRepository.save(existingHold);
            
            testPayment.setStatus(PaymentStatus.HELD);
            paymentRepository.save(testPayment);

            PaymentHoldEvent releaseEvent = createHoldEvent("RELEASE_HOLD", "AUTHORIZATION");

            holdEventsConsumer.handlePaymentHoldEvent(releaseEvent, 0, 0L, "topic", acknowledgment);

            verify(kafkaTemplate, times(2)).send(eq("payment-status-updated-events"), any(PaymentStatusUpdatedEvent.class));
        }
    }

    @Nested
    @DisplayName("Audit and Metrics Tests")
    class AuditAndMetricsTests {

        @Test
        @Transactional
        @DisplayName("Should audit all hold operations")
        void shouldAuditAllHoldOperations() {
            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "FRAUD_PREVENTION");

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            verify(auditService).logFinancialEvent(
                eq("HOLD_EVENT_PROCESSED"),
                eq(testPaymentId),
                any(Map.class)
            );
        }

        @Test
        @Transactional
        @DisplayName("Should record hold metrics")
        void shouldRecordHoldMetrics() {
            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "COMPLIANCE");
            event.setHoldAmount(new BigDecimal("2500.00"));

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            verify(metricsService).recordHoldPlaced("COMPLIANCE", new BigDecimal("2500.00"));
        }

        @Test
        @Transactional
        @DisplayName("Should record ledger entries for hold operations")
        void shouldRecordLedgerEntriesForHoldOperations() {
            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "AUTHORIZATION");
            event.setHoldAmount(new BigDecimal("750.00"));

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            verify(ledgerService).recordHoldPlacement(
                eq(testPaymentId),
                anyString(),
                eq(new BigDecimal("750.00")),
                eq("AUTHORIZATION"),
                anyString()
            );
        }
    }

    @Nested
    @DisplayName("Hold Eligibility Tests")
    class HoldEligibilityTests {

        @Test
        @Transactional
        @DisplayName("Should reject hold for completed payment")
        void shouldRejectHoldForCompletedPayment() {
            testPayment.setStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(testPayment);

            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "AUTHORIZATION");

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            // Verify no hold was created
            List<PaymentHold> holds = holdRepository.findAll();
            assertThat(holds).isEmpty();

            // Payment status should remain unchanged
            Payment unchangedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(unchangedPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @Transactional
        @DisplayName("Should reject hold for failed payment")
        void shouldRejectHoldForFailedPayment() {
            testPayment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(testPayment);

            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "FRAUD_PREVENTION");

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            // Verify no hold was created
            List<PaymentHold> holds = holdRepository.findAll();
            assertThat(holds).isEmpty();
        }

        @Test
        @Transactional
        @DisplayName("Should allow hold for pending payment")
        void shouldAllowHoldForPendingPayment() {
            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "AUTHORIZATION");

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            List<PaymentHold> holds = holdRepository.findAll();
            assertThat(holds).hasSize(1);

            Payment updatedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.HELD);
        }

        @Test
        @Transactional
        @DisplayName("Should allow hold for authorized payment")
        void shouldAllowHoldForAuthorizedPayment() {
            testPayment.setStatus(PaymentStatus.AUTHORIZED);
            paymentRepository.save(testPayment);

            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "COMPLIANCE");

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            List<PaymentHold> holds = holdRepository.findAll();
            assertThat(holds).hasSize(1);
        }

        @Test
        @Transactional
        @DisplayName("Should allow hold for processing payment")
        void shouldAllowHoldForProcessingPayment() {
            testPayment.setStatus(PaymentStatus.PROCESSING);
            paymentRepository.save(testPayment);

            PaymentHoldEvent event = createHoldEvent("PLACE_HOLD", "FRAUD_PREVENTION");

            holdEventsConsumer.handlePaymentHoldEvent(event, 0, 0L, "topic", acknowledgment);

            List<PaymentHold> holds = holdRepository.findAll();
            assertThat(holds).hasSize(1);
        }
    }

    /**
     * Helper methods
     */
    private Payment createTestPayment() {
        return Payment.builder()
            .id(testPaymentId)
            .customerId(testCustomerId)
            .merchantId(testMerchantId)
            .amount(new BigDecimal("1500.00"))
            .currency("USD")
            .status(PaymentStatus.PENDING)
            .paymentMethod("CARD")
            .createdAt(LocalDateTime.now())
            .build();
    }

    private PaymentHoldEvent createHoldEvent(String action, String holdType) {
        return PaymentHoldEvent.builder()
            .paymentId(testPaymentId)
            .holdAction(action)
            .holdType(holdType)
            .holdReason("Test hold reason")
            .initiatedBy("test-user")
            .timestamp(Instant.now())
            .build();
    }

    private PaymentHold createTestHold(String holdType) {
        return PaymentHold.builder()
            .id(UUID.randomUUID().toString())
            .paymentId(testPaymentId)
            .holdType(holdType)
            .holdAmount(new BigDecimal("1000.00"))
            .holdReason("Test hold")
            .initiatedBy("test-user")
            .placedAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusDays(7))
            .status("ACTIVE")
            .correlationId("test-correlation-id")
            .build();
    }
}