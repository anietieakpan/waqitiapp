package com.waqiti.payment.kafka;

import com.waqiti.common.audit.AuditService;
import com.waqiti.common.events.Payment3DSAuthenticationEvent;
import com.waqiti.common.fraud.FraudService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.payment.domain.*;
import com.waqiti.payment.metrics.AuthenticationMetricsService;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.repository.ThreeDSAuthenticationRepository;
import com.waqiti.payment.service.*;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///waqiti_test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "payment.3ds.challenge-timeout-minutes=10",
        "payment.3ds.frictionless-timeout-seconds=30",
        "payment.3ds.step-up-timeout-minutes=5",
        "payment.3ds.high-risk-threshold=1000.00",
        "payment.3ds.sca-threshold=500.00",
        "payment.3ds.low-value-exemption=30.00",
        "payment.fraud.high-risk-score=75.0"
})
@DisplayName("3DS Authentication Events Consumer Tests")
class Payment3DSAuthenticationEventsConsumerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("waqiti_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private Payment3DSAuthenticationEventsConsumer authenticationConsumer;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ThreeDSAuthenticationRepository authRepository;

    @MockBean
    private ThreeDSAuthenticationService threeDSService;

    @MockBean
    private StepUpAuthenticationService stepUpService;

    @MockBean
    private PaymentFraudService paymentFraudService;

    @MockBean
    private PaymentGatewayService gatewayService;

    @MockBean
    private AuthenticationMetricsService metricsService;

    @MockBean
    private AuditService auditService;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private SecurityContext securityContext;

    @MockBean
    private FraudService fraudService;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private Acknowledgment acknowledgment;

    private Payment testPayment;
    private String testPaymentId;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        authRepository.deleteAll();

        testPaymentId = UUID.randomUUID().toString();
        testPayment = Payment.builder()
                .id(testPaymentId)
                .amount(new BigDecimal("750.00"))
                .currency("EUR")
                .customerId(UUID.randomUUID().toString())
                .merchantId("MERCHANT_001")
                .status(PaymentStatus.INITIATED)
                .issuerCountry("DE")
                .acquirerCountry("FR")
                .region("EU")
                .cardNumber("4111111111111111")
                .gatewayId("stripe")
                .createdAt(LocalDateTime.now())
                .build();

        testPayment = paymentRepository.save(testPayment);
    }

    @Nested
    @DisplayName("Authentication Required Tests")
    class AuthenticationRequiredTests {

        @Test
        @Transactional
        @DisplayName("Should require authentication for high-value EEA transaction")
        void shouldRequireAuthenticationForHighValueEEATransaction() {
            testPayment.setAmount(new BigDecimal("750.00")); // Above SCA threshold
            paymentRepository.save(testPayment);

            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "AUTHENTICATION_REQUIRED", "2.1.0");

            when(threeDSService.isChallengeRequired(anyString())).thenReturn(true);
            when(threeDSService.extractChallengeUrl(anyString()))
                    .thenReturn("https://challenge.3ds.test/challenge");

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            Payment updated = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updated.isAuthenticationRequired()).isTrue();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.AUTHENTICATION_PENDING);

            List<ThreeDSAuthentication> auths = authRepository.findAll();
            assertThat(auths).hasSize(1);
            assertThat(auths.get(0).isRequired()).isTrue();
            assertThat(auths.get(0).isChallengeRequired()).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("Should apply low-value exemption for small transactions")
        void shouldApplyLowValueExemptionForSmallTransactions() {
            testPayment.setAmount(new BigDecimal("25.00")); // Below LVT threshold
            paymentRepository.save(testPayment);

            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "AUTHENTICATION_REQUIRED", "2.1.0");

            when(paymentRepository.getTodayLVTExemptionCount(anyString(), any())).thenReturn(2);
            when(paymentRepository.getTodayLVTExemptionAmount(anyString(), any()))
                    .thenReturn(new BigDecimal("50.00"));

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            Payment updated = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updated.isExemptionApplied()).isTrue();
            assertThat(updated.getExemptionType()).isEqualTo("LOW_VALUE");

            verify(kafkaTemplate).send(eq("payment-processing-events"), any());
        }

        @Test
        @Transactional
        @DisplayName("Should apply trusted merchant exemption")
        void shouldApplyTrustedMerchantExemption() {
            testPayment.setAmount(new BigDecimal("400.00")); // Below SCA threshold
            paymentRepository.save(testPayment);

            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "AUTHENTICATION_REQUIRED", "2.1.0");

            when(paymentRepository.isTrustedMerchant(testPayment.getMerchantId())).thenReturn(true);

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            Payment updated = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updated.isExemptionApplied()).isTrue();
            assertThat(updated.getExemptionType()).isEqualTo("TRUSTED_MERCHANT");
            assertThat(updated.getLiabilityShift()).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("Should apply recurring payment exemption")
        void shouldApplyRecurringPaymentExemption() {
            testPayment.setRecurringPayment(true);
            testPayment.setInitialAuthenticationCompleted(true);
            paymentRepository.save(testPayment);

            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "AUTHENTICATION_REQUIRED", "2.1.0");

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            Payment updated = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updated.isExemptionApplied()).isTrue();
            assertThat(updated.getExemptionType()).isEqualTo("RECURRING");
        }

        @Test
        @Transactional
        @DisplayName("Should apply TRA exemption for low-risk transactions")
        void shouldApplyTRAExemptionForLowRiskTransactions() {
            testPayment.setAmount(new BigDecimal("400.00"));
            testPayment.setCustomerRiskScore(15.0);
            testPayment.setMerchantRiskScore(10.0);
            paymentRepository.save(testPayment);

            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "AUTHENTICATION_REQUIRED", "2.1.0");

            when(paymentRepository.getMerchantFraudRate(anyString())).thenReturn(0.005); // 0.5%
            when(paymentRepository.getAcquirerFraudRate(anyString())).thenReturn(0.03); // 3%

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            Payment updated = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updated.isExemptionApplied()).isTrue();
            assertThat(updated.getExemptionType()).isEqualTo("TRA");
        }

        @Test
        @Transactional
        @DisplayName("Should require authentication for high-risk transactions")
        void shouldRequireAuthenticationForHighRiskTransactions() {
            testPayment.setCustomerRiskScore(80.0);
            testPayment.setMerchantRiskScore(70.0);
            paymentRepository.save(testPayment);

            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "AUTHENTICATION_REQUIRED", "2.1.0");

            when(threeDSService.isChallengeRequired(anyString())).thenReturn(true);

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            Payment updated = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updated.isAuthenticationRequired()).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("Should handle daily LVT exemption limits")
        void shouldHandleDailyLVTExemptionLimits() {
            testPayment.setAmount(new BigDecimal("20.00"));
            paymentRepository.save(testPayment);

            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "AUTHENTICATION_REQUIRED", "2.1.0");

            // Simulate daily limits reached
            when(paymentRepository.getTodayLVTExemptionCount(anyString(), any())).thenReturn(5);

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            Payment updated = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updated.isAuthenticationRequired()).isTrue(); // Should require auth when limits exceeded
        }
    }

    @Nested
    @DisplayName("Challenge Flow Tests")
    class ChallengeFlowTests {

        @Test
        @Transactional
        @DisplayName("Should initiate challenge flow")
        void shouldInitiateChallengeFlow() {
            ThreeDSAuthentication auth = createAuthenticationRecord();
            auth = authRepository.save(auth);

            testPayment.setThreeDSAuthenticationId(auth.getId());
            paymentRepository.save(testPayment);

            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "CHALLENGE_INITIATED", "2.1.0");
            event.setChallengeUrl("https://challenge.3ds.test/challenge");

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            ThreeDSAuthentication updated = authRepository.findById(auth.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(AuthenticationStatus.CHALLENGE_PENDING);
            assertThat(updated.getChallengeUrl()).isEqualTo("https://challenge.3ds.test/challenge");
            assertThat(updated.getChallengeInitiatedAt()).isNotNull();

            verify(notificationService).sendCustomerNotification(
                    eq(testPayment.getCustomerId()),
                    eq("Payment Authentication Required"),
                    contains("authentication"),
                    eq(NotificationService.Priority.HIGH)
            );
        }

        @Test
        @Transactional
        @DisplayName("Should handle successful challenge completion")
        void shouldHandleSuccessfulChallengeCompletion() {
            ThreeDSAuthentication auth = createAuthenticationRecord();
            auth.setStatus(AuthenticationStatus.CHALLENGE_PENDING);
            auth = authRepository.save(auth);

            testPayment.setThreeDSAuthenticationId(auth.getId());
            paymentRepository.save(testPayment);

            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "CHALLENGE_COMPLETED", "2.1.0");
            event.setAuthenticationStatus("SUCCESS");
            event.setAuthenticationValue("AAIBAgSBHwAAAAA");
            event.setEci("05");
            event.setTransactionId("3ac7caa7-aa42-2663-791b-2ac05a542c4a");

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            ThreeDSAuthentication updated = authRepository.findById(auth.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(AuthenticationStatus.SUCCESS);
            assertThat(updated.getAuthenticationValue()).isEqualTo("AAIBAgSBHwAAAAA");
            assertThat(updated.getElectronicCommerceIndicator()).isEqualTo("05");
            assertThat(updated.getChallengeCompletedAt()).isNotNull();

            Payment updatedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.AUTHENTICATED);
            assertThat(updatedPayment.getLiabilityShift()).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("Should handle failed challenge completion")
        void shouldHandleFailedChallengeCompletion() {
            ThreeDSAuthentication auth = createAuthenticationRecord();
            auth.setStatus(AuthenticationStatus.CHALLENGE_PENDING);
            auth = authRepository.save(auth);

            testPayment.setThreeDSAuthenticationId(auth.getId());
            paymentRepository.save(testPayment);

            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "CHALLENGE_COMPLETED", "2.1.0");
            event.setAuthenticationStatus("FAILED");
            event.setFailureReason("Customer cancelled authentication");

            when(paymentFraudService.assessFailedAuthenticationRisk(any())).thenReturn(45.0);

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            ThreeDSAuthentication updated = authRepository.findById(auth.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(AuthenticationStatus.FAILED);
            assertThat(updated.getFailureReason()).isEqualTo("Customer cancelled authentication");

            Payment updatedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.AUTHENTICATION_FAILED);
            assertThat(updatedPayment.getLiabilityShift()).isFalse();
        }

        @Test
        @Transactional
        @DisplayName("Should handle authentication timeout")
        void shouldHandleAuthenticationTimeout() {
            ThreeDSAuthentication auth = createAuthenticationRecord();
            auth.setStatus(AuthenticationStatus.CHALLENGE_PENDING);
            auth = authRepository.save(auth);

            testPayment.setThreeDSAuthenticationId(auth.getId());
            paymentRepository.save(testPayment);

            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "AUTHENTICATION_TIMEOUT", "2.1.0");
            event.setTimeoutType("CHALLENGE_TIMEOUT");

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            ThreeDSAuthentication updated = authRepository.findById(auth.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(AuthenticationStatus.TIMEOUT);
            assertThat(updated.getTimeoutAt()).isNotNull();

            Payment updatedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.AUTHENTICATION_TIMEOUT);

            verify(notificationService).sendCustomerNotification(
                    eq(testPayment.getCustomerId()),
                    eq("Payment Authentication Timed Out"),
                    contains("expired"),
                    eq(NotificationService.Priority.MEDIUM)
            );
        }

        @Test
        @Transactional
        @DisplayName("Should handle authentication abandonment")
        void shouldHandleAuthenticationAbandonment() {
            ThreeDSAuthentication auth = createAuthenticationRecord();
            auth.setStatus(AuthenticationStatus.CHALLENGE_PENDING);
            auth = authRepository.save(auth);

            testPayment.setThreeDSAuthenticationId(auth.getId());
            paymentRepository.save(testPayment);

            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "AUTHENTICATION_ABANDONED", "2.1.0");

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            ThreeDSAuthentication updated = authRepository.findById(auth.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(AuthenticationStatus.ABANDONED);
            assertThat(updated.getAbandonedAt()).isNotNull();

            Payment updatedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.ABANDONED);
        }
    }

    @Nested
    @DisplayName("Frictionless Flow Tests")
    class FrictionlessFlowTests {

        @Test
        @Transactional
        @DisplayName("Should handle successful frictionless authentication")
        void shouldHandleSuccessfulFrictionlessAuthentication() {
            ThreeDSAuthentication auth = createAuthenticationRecord();
            auth.setFlowType("FRICTIONLESS");
            auth.setChallengeRequired(false);
            auth = authRepository.save(auth);

            testPayment.setThreeDSAuthenticationId(auth.getId());
            paymentRepository.save(testPayment);

            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "FRICTIONLESS_COMPLETED", "2.1.0");
            event.setAuthenticationStatus("SUCCESS");
            event.setAuthenticationValue("AAIBAgSBHwAAAAA");
            event.setEci("05");

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            ThreeDSAuthentication updated = authRepository.findById(auth.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(AuthenticationStatus.SUCCESS);
            assertThat(updated.getFrictionlessCompletedAt()).isNotNull();

            Payment updatedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.AUTHENTICATED);
            assertThat(updatedPayment.getLiabilityShift()).isTrue();

            verify(kafkaTemplate).send(eq("payment-processing-events"), any());
        }

        @Test
        @Transactional
        @DisplayName("Should handle failed frictionless authentication")
        void shouldHandleFailedFrictionlessAuthentication() {
            ThreeDSAuthentication auth = createAuthenticationRecord();
            auth.setFlowType("FRICTIONLESS");
            auth = authRepository.save(auth);

            testPayment.setThreeDSAuthenticationId(auth.getId());
            paymentRepository.save(testPayment);

            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "FRICTIONLESS_COMPLETED", "2.1.0");
            event.setAuthenticationStatus("FAILED");
            event.setFailureReason("Issuer declined authentication");

            when(paymentFraudService.assessFailedAuthenticationRisk(any())).thenReturn(60.0);

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            ThreeDSAuthentication updated = authRepository.findById(auth.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(AuthenticationStatus.FAILED);

            Payment updatedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.AUTHENTICATION_FAILED);
            assertThat(updatedPayment.getLiabilityShift()).isFalse();
        }
    }

    @Nested
    @DisplayName("Step-Up Authentication Tests")
    class StepUpAuthenticationTests {

        @Test
        @Transactional
        @DisplayName("Should initiate step-up authentication for high-value failed auth")
        void shouldInitiateStepUpAuthenticationForHighValueFailedAuth() {
            testPayment.setAmount(new BigDecimal("1500.00")); // Above high-risk threshold
            paymentRepository.save(testPayment);

            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "AUTHENTICATION_FAILED", "2.1.0");
            event.setFailureReason("SOFT_DECLINE");

            when(stepUpService.initiateStepUpAuthentication(anyString(), anyString(), anyString()))
                    .thenReturn("step-up-session-123");

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            Payment updated = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.STEP_UP_AUTHENTICATION);
            assertThat(updated.getStepUpInitiatedAt()).isNotNull();
        }

        @Test
        @Transactional
        @DisplayName("Should not attempt step-up for hard decline")
        void shouldNotAttemptStepUpForHardDecline() {
            testPayment.setAmount(new BigDecimal("1500.00"));
            paymentRepository.save(testPayment);

            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "AUTHENTICATION_FAILED", "2.1.0");
            event.setFailureReason("HARD_DECLINE");

            when(paymentFraudService.assessFailedAuthenticationRisk(any())).thenReturn(30.0);

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            Payment updated = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.AUTHENTICATION_FAILED);
            assertThat(updated.getStepUpInitiatedAt()).isNull();
        }

        @Test
        @Transactional
        @DisplayName("Should handle step-up authentication requirement")
        void shouldHandleStepUpAuthenticationRequirement() {
            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "STEP_UP_REQUIRED", "2.1.0");
            event.setStepUpReason("Primary authentication failed");

            when(stepUpService.initiateStepUpAuthentication(anyString(), anyString(), anyString()))
                    .thenReturn("step-up-session-456");

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            Payment updated = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.STEP_UP_AUTHENTICATION);
        }

        @Test
        @Transactional
        @DisplayName("Should handle step-up initiation failure")
        void shouldHandleStepUpInitiationFailure() {
            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "STEP_UP_REQUIRED", "2.1.0");
            event.setStepUpReason("Primary authentication failed");

            when(stepUpService.initiateStepUpAuthentication(anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Step-up service unavailable"));

            when(paymentFraudService.assessFailedAuthenticationRisk(any())).thenReturn(40.0);

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            Payment updated = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.AUTHENTICATION_FAILED);
        }
    }

    @Nested
    @DisplayName("Fraud Detection Tests")
    class FraudDetectionTests {

        @Test
        @Transactional
        @DisplayName("Should detect suspicious 3DS patterns")
        void shouldDetectSuspicious3DSPatterns() {
            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "AUTHENTICATION_REQUIRED", "2.1.0");

            when(fraudService.isSuspicious3DSPattern(any())).thenReturn(true);

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            Payment updated = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.FRAUD_REVIEW);
            assertThat(updated.getFraudReason()).contains("Suspicious 3DS");

            verify(notificationService).sendSecurityAlert(
                    eq("Suspicious 3DS Authentication Pattern"),
                    contains("Unusual 3DS authentication pattern"),
                    eq(NotificationService.Priority.HIGH)
            );
        }

        @Test
        @Transactional
        @DisplayName("Should handle high-risk failed authentication")
        void shouldHandleHighRiskFailedAuthentication() {
            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "AUTHENTICATION_FAILED", "2.1.0");
            event.setFailureReason("Multiple failed attempts");

            when(paymentFraudService.assessFailedAuthenticationRisk(any())).thenReturn(85.0);

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            Payment updated = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.BLOCKED);

            verify(kafkaTemplate).send(eq("fraud-alert-events"), any());
        }

        @Test
        @Transactional
        @DisplayName("Should record successful authentication for fraud analysis")
        void shouldRecordSuccessfulAuthenticationForFraudAnalysis() {
            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "AUTHENTICATION_SUCCESS", "2.1.0");

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            verify(paymentFraudService).recordSuccessfulAuthentication(
                    eq(testPaymentId), contains("3ds-"));
        }
    }

    @Nested
    @DisplayName("Metrics and Auditing Tests")
    class MetricsAndAuditingTests {

        @Test
        @Transactional
        @DisplayName("Should record authentication requirement metrics")
        void shouldRecordAuthenticationRequirementMetrics() {
            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "AUTHENTICATION_REQUIRED", "2.1.0");

            when(paymentRepository.getTodayLVTExemptionCount(anyString(), any())).thenReturn(2);
            when(paymentRepository.getTodayLVTExemptionAmount(anyString(), any()))
                    .thenReturn(new BigDecimal("50.00"));

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            verify(metricsService).recordAuthenticationRequirement(
                    anyBoolean(), anyString(), any(BigDecimal.class));
        }

        @Test
        @Transactional
        @DisplayName("Should record challenge metrics")
        void shouldRecordChallengeMetrics() {
            ThreeDSAuthentication auth = createAuthenticationRecord();
            auth = authRepository.save(auth);

            testPayment.setThreeDSAuthenticationId(auth.getId());
            paymentRepository.save(testPayment);

            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "CHALLENGE_INITIATED", "2.1.0");

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            verify(metricsService).recordChallengeInitiated("2.1.0");
        }

        @Test
        @Transactional
        @DisplayName("Should audit all authentication events")
        void shouldAuditAllAuthenticationEvents() {
            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "AUTHENTICATION_REQUIRED", "2.1.0");

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            verify(auditService).logFinancialEvent(
                    eq("3DS_AUTH_EVENT_PROCESSED"),
                    eq(testPaymentId),
                    argThat(metadata -> 
                        metadata.containsKey("eventType") && 
                        metadata.containsKey("threeDSVersion") &&
                        metadata.containsKey("correlationId"))
            );
        }

        @Test
        @Transactional
        @DisplayName("Should record exemption metrics")
        void shouldRecordExemptionMetrics() {
            testPayment.setAmount(new BigDecimal("25.00"));
            paymentRepository.save(testPayment);

            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "EXEMPTION_APPLIED", "2.1.0");
            event.setExemptionType("LOW_VALUE");

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            verify(metricsService).recordExemptionApplied("LOW_VALUE", testPayment.getAmount());
        }

        @Test
        @Transactional
        @DisplayName("Should track exemption usage for regulatory reporting")
        void shouldTrackExemptionUsageForRegulatoryReporting() {
            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "EXEMPTION_APPLIED", "2.1.0");
            event.setExemptionType("TRA");
            event.setExemptionReason("Low risk transaction");

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            verify(kafkaTemplate).send(eq("exemption-usage-events"), any());
        }
    }

    @Nested
    @DisplayName("3DS Version Compatibility Tests")
    class ThreeDSVersionCompatibilityTests {

        @Test
        @Transactional
        @DisplayName("Should handle 3DS v1.0 authentication")
        void shouldHandle3DSv1Authentication() {
            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "AUTHENTICATION_REQUIRED", "1.0.2");

            when(threeDSService.isChallengeRequired(anyString())).thenReturn(true);

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            List<ThreeDSAuthentication> auths = authRepository.findAll();
            assertThat(auths).hasSize(1);
            assertThat(auths.get(0).getThreeDSVersion()).isEqualTo("1.0.2");
        }

        @Test
        @Transactional
        @DisplayName("Should handle 3DS v2.1 authentication")
        void shouldHandle3DSv21Authentication() {
            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "AUTHENTICATION_REQUIRED", "2.1.0");

            when(threeDSService.isChallengeRequired(anyString())).thenReturn(false);

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            List<ThreeDSAuthentication> auths = authRepository.findAll();
            assertThat(auths).hasSize(1);
            assertThat(auths.get(0).getThreeDSVersion()).isEqualTo("2.1.0");
        }

        @Test
        @Transactional
        @DisplayName("Should handle 3DS v2.2 authentication")
        void shouldHandle3DSv22Authentication() {
            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "AUTHENTICATION_REQUIRED", "2.2.0");

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            List<ThreeDSAuthentication> auths = authRepository.findAll();
            assertThat(auths).hasSize(1);
            assertThat(auths.get(0).getThreeDSVersion()).isEqualTo("2.2.0");
        }

        @Test
        @Transactional
        @DisplayName("Should default to 3DS v2.1 when version not specified")
        void shouldDefaultTo3DSv21WhenVersionNotSpecified() {
            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "AUTHENTICATION_REQUIRED", null);

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            List<ThreeDSAuthentication> auths = authRepository.findAll();
            assertThat(auths).hasSize(1);
            assertThat(auths.get(0).getThreeDSVersion()).isEqualTo("2.1.0");
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @Transactional
        @DisplayName("Should handle authentication service failures")
        void shouldHandleAuthenticationServiceFailures() {
            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "AUTHENTICATION_REQUIRED", "2.1.0");

            when(threeDSService.initiateAuthentication(anyString(), any(), anyString()))
                    .thenThrow(new RuntimeException("3DS service unavailable"));

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            List<ThreeDSAuthentication> auths = authRepository.findAll();
            assertThat(auths).hasSize(1);
            assertThat(auths.get(0).getStatus()).isEqualTo(AuthenticationStatus.FAILED);
            assertThat(auths.get(0).getFailureReason()).contains("3DS service unavailable");
        }

        @Test
        @Transactional
        @DisplayName("Should handle missing payment")
        void shouldHandleMissingPayment() {
            String nonExistentPaymentId = UUID.randomUUID().toString();
            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "AUTHENTICATION_REQUIRED", "2.1.0");
            event.setPaymentId(nonExistentPaymentId);

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            verify(kafkaTemplate).send(eq("payment-3ds-authentication-events-dlq"), any());
            verify(notificationService).sendOperationalAlert(
                    eq("3DS Authentication Event Processing Failed"),
                    contains("Failed to process 3DS authentication event"),
                    eq(NotificationService.Priority.HIGH)
            );
        }

        @Test
        @Transactional
        @DisplayName("Should handle invalid event data")
        void shouldHandleInvalidEventData() {
            Payment3DSAuthenticationEvent event = Payment3DSAuthenticationEvent.builder()
                    .paymentId(null) // Invalid - missing payment ID
                    .eventType("AUTHENTICATION_REQUIRED")
                    .timestamp(Instant.now())
                    .build();

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            verify(kafkaTemplate).send(eq("payment-3ds-authentication-events-dlq"), any());
        }

        @Test
        @Transactional
        @DisplayName("Should acknowledge message even on errors")
        void shouldAcknowledgeMessageEvenOnErrors() {
            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "AUTHENTICATION_REQUIRED", "2.1.0");

            when(securityContext.validateFinancialOperation(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Security validation failed"));

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @Transactional
        @DisplayName("Should process authentication event within acceptable time")
        void shouldProcessAuthenticationEventWithinAcceptableTime() {
            Payment3DSAuthenticationEvent event = createAuthenticationEvent(
                    "AUTHENTICATION_REQUIRED", "2.1.0");

            long startTime = System.currentTimeMillis();

            authenticationConsumer.handlePayment3DSAuthenticationEvent(
                    event, 0, 0L, "topic", acknowledgment);

            long duration = System.currentTimeMillis() - startTime;
            assertThat(duration).isLessThan(2000); // Should complete within 2 seconds
        }

        @Test
        @Transactional
        @DisplayName("Should handle concurrent authentication events")
        void shouldHandleConcurrentAuthenticationEvents() {
            // Create multiple payments
            List<Payment> payments = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Payment payment = Payment.builder()
                        .id(UUID.randomUUID().toString())
                        .amount(new BigDecimal("100.00"))
                        .currency("EUR")
                        .customerId(UUID.randomUUID().toString())
                        .merchantId("MERCHANT_00" + i)
                        .status(PaymentStatus.INITIATED)
                        .issuerCountry("DE")
                        .acquirerCountry("FR")
                        .createdAt(LocalDateTime.now())
                        .build();
                payments.add(paymentRepository.save(payment));
            }

            // Process events concurrently
            payments.parallelStream().forEach(payment -> {
                Payment3DSAuthenticationEvent event = Payment3DSAuthenticationEvent.builder()
                        .paymentId(payment.getId())
                        .eventType("AUTHENTICATION_REQUIRED")
                        .threeDSVersion("2.1.0")
                        .timestamp(Instant.now())
                        .build();

                authenticationConsumer.handlePayment3DSAuthenticationEvent(
                        event, 0, 0L, "topic", acknowledgment);
            });

            List<ThreeDSAuthentication> auths = authRepository.findAll();
            assertThat(auths).hasSize(5);
        }
    }

    // Helper methods
    private Payment3DSAuthenticationEvent createAuthenticationEvent(String eventType, String version) {
        return Payment3DSAuthenticationEvent.builder()
                .paymentId(testPaymentId)
                .eventType(eventType)
                .threeDSVersion(version)
                .timestamp(Instant.now())
                .build();
    }

    private ThreeDSAuthentication createAuthenticationRecord() {
        return ThreeDSAuthentication.builder()
                .id(UUID.randomUUID().toString())
                .paymentId(testPaymentId)
                .threeDSVersion("2.1.0")
                .status(AuthenticationStatus.INITIATED)
                .initiatedAt(LocalDateTime.now())
                .required(true)
                .correlationId("test-correlation-123")
                .build();
    }
}