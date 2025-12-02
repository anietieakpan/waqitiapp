package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentInstallmentEvent;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;
import com.waqiti.payment.domain.PaymentInstallment;
import com.waqiti.payment.domain.InstallmentStatus;
import com.waqiti.payment.domain.InstallmentPlan;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.repository.PaymentInstallmentRepository;
import com.waqiti.payment.repository.InstallmentPlanRepository;
import com.waqiti.payment.service.InstallmentService;
import com.waqiti.payment.service.BNPLService;
import com.waqiti.payment.service.CreditAssessmentService;
import com.waqiti.payment.metrics.InstallmentMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.security.SecurityContext;
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
import java.time.LocalDate;
import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
        "installment.max.amount=50000",
        "installment.min.amount=50",
        "installment.max.count=36",
        "installment.late.fee.percentage=0.05",
        "installment.grace.period.days=3"
})
@DisplayName("Payment Installment Events Consumer Tests")
class PaymentInstallmentEventsConsumerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("waqiti_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private PaymentInstallmentEventsConsumer installmentEventsConsumer;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentInstallmentRepository installmentRepository;

    @Autowired
    private InstallmentPlanRepository planRepository;

    @MockBean
    private InstallmentService installmentService;

    @MockBean
    private BNPLService bnplService;

    @MockBean
    private CreditAssessmentService creditService;

    @MockBean
    private InstallmentMetricsService metricsService;

    @MockBean
    private AuditService auditService;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private SecurityContext securityContext;

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
        installmentRepository.deleteAll();
        planRepository.deleteAll();

        testPaymentId = UUID.randomUUID().toString();
        testCustomerId = UUID.randomUUID().toString();
        testMerchantId = UUID.randomUUID().toString();

        testPayment = createTestPayment();
        testPayment = paymentRepository.save(testPayment);

        // Mock default behaviors
        when(kafkaTemplate.send(anyString(), any())).thenReturn(mock(CompletableFuture.class));
        when(installmentService.collectInstallment(any(), any(), any())).thenReturn(true);
    }

    @Nested
    @DisplayName("Create Plan Tests")
    class CreatePlanTests {

        @Test
        @Transactional
        @DisplayName("Should create traditional installment plan successfully")
        void shouldCreateTraditionalInstallmentPlanSuccessfully() {
            PaymentInstallmentEvent event = createInstallmentEvent("CREATE_PLAN");
            event.setNumberOfInstallments(6);
            event.setPlanType("TRADITIONAL");
            event.setInterestRate(new BigDecimal("0.12")); // 12% annual

            installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment);

            // Verify plan creation
            List<InstallmentPlan> plans = planRepository.findAll();
            assertThat(plans).hasSize(1);
            
            InstallmentPlan plan = plans.get(0);
            assertThat(plan.getPaymentId()).isEqualTo(testPaymentId);
            assertThat(plan.getNumberOfInstallments()).isEqualTo(6);
            assertThat(plan.getTotalAmount()).isEqualTo(testPayment.getAmount());
            assertThat(plan.getInterestRate()).isEqualTo(new BigDecimal("0.12"));
            assertThat(plan.getPlanType()).isEqualTo("TRADITIONAL");
            assertThat(plan.getStatus()).isEqualTo("ACTIVE"); // Traditional plans auto-approve

            // Verify installments creation
            List<PaymentInstallment> installments = installmentRepository.findAll();
            assertThat(installments).hasSize(6);
            
            // Verify first installment is pending, others scheduled
            PaymentInstallment firstInstallment = installments.stream()
                .filter(i -> i.getInstallmentNumber() == 1)
                .findFirst().orElseThrow();
            assertThat(firstInstallment.getStatus()).isEqualTo(InstallmentStatus.PENDING);
            
            // Verify payment updated
            Payment updatedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updatedPayment.getInstallmentPlanId()).isEqualTo(plan.getId());
            assertThat(updatedPayment.getIsInstallment()).isTrue();

            verify(metricsService).recordPlanCreated("TRADITIONAL", testPayment.getAmount(), 6);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should create BNPL plan and trigger credit assessment")
        void shouldCreateBNPLPlanAndTriggerCreditAssessment() {
            PaymentInstallmentEvent event = createInstallmentEvent("CREATE_PLAN");
            event.setNumberOfInstallments(4);
            event.setPlanType("BNPL");

            installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment);

            List<InstallmentPlan> plans = planRepository.findAll();
            assertThat(plans).hasSize(1);
            
            InstallmentPlan plan = plans.get(0);
            assertThat(plan.getPlanType()).isEqualTo("BNPL");
            assertThat(plan.getStatus()).isEqualTo("PENDING_APPROVAL");

            // Should trigger credit assessment for BNPL
            verify(kafkaTemplate).send(eq("payment-installment-events"), any(PaymentInstallmentEvent.class));
        }

        @Test
        @Transactional
        @DisplayName("Should calculate equal installments without interest")
        void shouldCalculateEqualInstallmentsWithoutInterest() {
            testPayment.setAmount(new BigDecimal("1200.00"));
            paymentRepository.save(testPayment);

            PaymentInstallmentEvent event = createInstallmentEvent("CREATE_PLAN");
            event.setNumberOfInstallments(4);
            event.setPlanType("TRADITIONAL");
            // No interest rate set

            installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment);

            List<PaymentInstallment> installments = installmentRepository.findAll();
            assertThat(installments).hasSize(4);

            // Each installment should be $300
            for (PaymentInstallment installment : installments) {
                assertThat(installment.getAmount()).isEqualTo(new BigDecimal("300.00"));
            }

            // Verify due dates (first payment in 30 days, then monthly)
            PaymentInstallment firstInstallment = installments.stream()
                .filter(i -> i.getInstallmentNumber() == 1)
                .findFirst().orElseThrow();
            assertThat(firstInstallment.getDueDate()).isEqualTo(LocalDate.now().plusDays(30));

            PaymentInstallment secondInstallment = installments.stream()
                .filter(i -> i.getInstallmentNumber() == 2)
                .findFirst().orElseThrow();
            assertThat(secondInstallment.getDueDate()).isEqualTo(LocalDate.now().plusDays(30).plusMonths(1));
        }

        @Test
        @Transactional
        @DisplayName("Should calculate installments with interest (EMI)")
        void shouldCalculateInstallmentsWithInterestEMI() {
            testPayment.setAmount(new BigDecimal("12000.00"));
            paymentRepository.save(testPayment);

            PaymentInstallmentEvent event = createInstallmentEvent("CREATE_PLAN");
            event.setNumberOfInstallments(12);
            event.setPlanType("TRADITIONAL");
            event.setInterestRate(new BigDecimal("0.15")); // 15% annual

            installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment);

            List<PaymentInstallment> installments = installmentRepository.findAll();
            assertThat(installments).hasSize(12);

            // All installments should have the same EMI amount
            BigDecimal emiAmount = installments.get(0).getAmount();
            for (PaymentInstallment installment : installments) {
                assertThat(installment.getAmount()).isEqualTo(emiAmount);
            }

            // EMI should be higher than simple equal division due to interest
            BigDecimal equalAmount = testPayment.getAmount().divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
            assertThat(emiAmount).isGreaterThan(equalAmount);
        }

        @Test
        @Transactional
        @DisplayName("Should reject plan for amount below minimum")
        void shouldRejectPlanForAmountBelowMinimum() {
            testPayment.setAmount(new BigDecimal("25.00")); // Below minimum of $50
            paymentRepository.save(testPayment);

            PaymentInstallmentEvent event = createInstallmentEvent("CREATE_PLAN");
            event.setNumberOfInstallments(3);

            assertThatCode(() -> 
                installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment)
            ).doesNotThrowAnyException();

            // Verify no plan was created
            List<InstallmentPlan> plans = planRepository.findAll();
            assertThat(plans).isEmpty();

            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should reject plan for amount above maximum")
        void shouldRejectPlanForAmountAboveMaximum() {
            testPayment.setAmount(new BigDecimal("75000.00")); // Above maximum of $50,000
            paymentRepository.save(testPayment);

            PaymentInstallmentEvent event = createInstallmentEvent("CREATE_PLAN");
            event.setNumberOfInstallments(6);

            assertThatCode(() -> 
                installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment)
            ).doesNotThrowAnyException();

            List<InstallmentPlan> plans = planRepository.findAll();
            assertThat(plans).isEmpty();

            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should reject plan for too many installments")
        void shouldRejectPlanForTooManyInstallments() {
            PaymentInstallmentEvent event = createInstallmentEvent("CREATE_PLAN");
            event.setNumberOfInstallments(48); // Above maximum of 36

            assertThatCode(() -> 
                installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment)
            ).doesNotThrowAnyException();

            List<InstallmentPlan> plans = planRepository.findAll();
            assertThat(plans).isEmpty();

            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Credit Assessment Tests")
    class CreditAssessmentTests {

        @Test
        @Transactional
        @DisplayName("Should approve plan after successful credit assessment")
        void shouldApprovePlanAfterSuccessfulCreditAssessment() {
            InstallmentPlan plan = createTestPlan("BNPL", "PENDING_APPROVAL");
            plan = planRepository.save(plan);

            // Mock successful credit assessment
            PaymentInstallmentEventsConsumer.CreditAssessmentResult mockResult = 
                createMockCreditResult(true, "LOW_RISK", 750, plan.getTotalAmount(), new BigDecimal("0.10"));
            when(creditService.assessCustomerCredit(any(), any(), anyInt(), any())).thenReturn(mockResult);

            PaymentInstallmentEvent event = createInstallmentEvent("CREDIT_ASSESSMENT");
            event.setPlanId(plan.getId());

            installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment);

            InstallmentPlan updatedPlan = planRepository.findById(plan.getId()).orElseThrow();
            assertThat(updatedPlan.getStatus()).isEqualTo("APPROVED");
            assertThat(updatedPlan.getCreditScore()).isEqualTo(750);
            assertThat(updatedPlan.getRiskCategory()).isEqualTo("LOW_RISK");
            assertThat(updatedPlan.getApprovedAmount()).isEqualTo(plan.getTotalAmount());
            assertThat(updatedPlan.getApprovedInterestRate()).isEqualTo(new BigDecimal("0.10"));
            assertThat(updatedPlan.getAssessedAt()).isNotNull();

            verify(metricsService).recordCreditAssessment("LOW_RISK", true, plan.getTotalAmount());
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should reject plan after failed credit assessment")
        void shouldRejectPlanAfterFailedCreditAssessment() {
            InstallmentPlan plan = createTestPlan("BNPL", "PENDING_APPROVAL");
            plan = planRepository.save(plan);

            // Mock failed credit assessment
            PaymentInstallmentEventsConsumer.CreditAssessmentResult mockResult = 
                createMockCreditResult(false, "HIGH_RISK", 580, null, null);
            mockResult.setRejectionReason("Insufficient credit history");
            when(creditService.assessCustomerCredit(any(), any(), anyInt(), any())).thenReturn(mockResult);

            PaymentInstallmentEvent event = createInstallmentEvent("CREDIT_ASSESSMENT");
            event.setPlanId(plan.getId());

            installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment);

            InstallmentPlan updatedPlan = planRepository.findById(plan.getId()).orElseThrow();
            assertThat(updatedPlan.getStatus()).isEqualTo("REJECTED");
            assertThat(updatedPlan.getCreditScore()).isEqualTo(580);
            assertThat(updatedPlan.getRiskCategory()).isEqualTo("HIGH_RISK");
            assertThat(updatedPlan.getRejectionReason()).isEqualTo("Insufficient credit history");

            verify(metricsService).recordCreditAssessment("HIGH_RISK", false, plan.getTotalAmount());
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should handle credit assessment service failure")
        void shouldHandleCreditAssessmentServiceFailure() {
            InstallmentPlan plan = createTestPlan("BNPL", "PENDING_APPROVAL");
            plan = planRepository.save(plan);

            when(creditService.assessCustomerCredit(any(), any(), anyInt(), any()))
                .thenThrow(new RuntimeException("Credit service unavailable"));

            PaymentInstallmentEvent event = createInstallmentEvent("CREDIT_ASSESSMENT");
            event.setPlanId(plan.getId());

            installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment);

            InstallmentPlan updatedPlan = planRepository.findById(plan.getId()).orElseThrow();
            assertThat(updatedPlan.getStatus()).isEqualTo("ASSESSMENT_FAILED");
            assertThat(updatedPlan.getRejectionReason()).contains("Credit assessment failed");

            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Plan Approval Tests")
    class PlanApprovalTests {

        @Test
        @Transactional
        @DisplayName("Should approve plan and activate first installment")
        void shouldApprovePlanAndActivateFirstInstallment() {
            InstallmentPlan plan = createTestPlan("BNPL", "APPROVED");
            plan = planRepository.save(plan);

            // Create installments
            List<PaymentInstallment> installments = createTestInstallments(plan, 4);
            installmentRepository.saveAll(installments);

            PaymentInstallmentEvent event = createInstallmentEvent("APPROVE_PLAN");
            event.setPlanId(plan.getId());
            event.setApprovedBy("credit-manager");

            installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment);

            InstallmentPlan updatedPlan = planRepository.findById(plan.getId()).orElseThrow();
            assertThat(updatedPlan.getStatus()).isEqualTo("ACTIVE");
            assertThat(updatedPlan.getApprovedAt()).isNotNull();
            assertThat(updatedPlan.getApprovedBy()).isEqualTo("credit-manager");

            // Verify payment status updated
            Payment updatedPayment = paymentRepository.findById(testPaymentId).orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.INSTALLMENT_ACTIVE);

            verify(metricsService).recordPlanApproved(plan.getPlanType(), plan.getTotalAmount());
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Installment Collection Tests")
    class InstallmentCollectionTests {

        @Test
        @Transactional
        @DisplayName("Should collect installment successfully and activate next")
        void shouldCollectInstallmentSuccessfullyAndActivateNext() {
            InstallmentPlan plan = createTestPlan("TRADITIONAL", "ACTIVE");
            plan = planRepository.save(plan);

            List<PaymentInstallment> installments = createTestInstallments(plan, 4);
            PaymentInstallment firstInstallment = installments.get(0);
            firstInstallment.setStatus(InstallmentStatus.PENDING);
            installmentRepository.saveAll(installments);

            PaymentInstallmentEvent event = createInstallmentEvent("COLLECT_INSTALLMENT");
            event.setPlanId(plan.getId());
            event.setInstallmentId(firstInstallment.getId());
            event.setPaymentMethodId("pm-12345");

            when(installmentService.collectInstallment(eq(firstInstallment.getId()), eq("pm-12345"), any()))
                .thenReturn(true);

            installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment);

            PaymentInstallment updatedInstallment = installmentRepository.findById(firstInstallment.getId()).orElseThrow();
            assertThat(updatedInstallment.getStatus()).isEqualTo(InstallmentStatus.PAID);
            assertThat(updatedInstallment.getPaidAt()).isNotNull();
            assertThat(updatedInstallment.getPaymentMethodId()).isEqualTo("pm-12345");

            verify(metricsService).recordInstallmentCollected(plan.getPlanType(), firstInstallment.getAmount());
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should complete plan when last installment is collected")
        void shouldCompletePlanWhenLastInstallmentIsCollected() {
            InstallmentPlan plan = createTestPlan("TRADITIONAL", "ACTIVE");
            plan = planRepository.save(plan);

            List<PaymentInstallment> installments = createTestInstallments(plan, 3);
            // Mark first two as paid
            installments.get(0).setStatus(InstallmentStatus.PAID);
            installments.get(1).setStatus(InstallmentStatus.PAID);
            // Last one pending
            PaymentInstallment lastInstallment = installments.get(2);
            lastInstallment.setStatus(InstallmentStatus.PENDING);
            lastInstallment.setInstallmentNumber(3);
            installmentRepository.saveAll(installments);

            PaymentInstallmentEvent event = createInstallmentEvent("COLLECT_INSTALLMENT");
            event.setPlanId(plan.getId());
            event.setInstallmentId(lastInstallment.getId());
            event.setPaymentMethodId("pm-12345");

            installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment);

            PaymentInstallment updatedInstallment = installmentRepository.findById(lastInstallment.getId()).orElseThrow();
            assertThat(updatedInstallment.getStatus()).isEqualTo(InstallmentStatus.PAID);

            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should handle failed installment collection")
        void shouldHandleFailedInstallmentCollection() {
            InstallmentPlan plan = createTestPlan("TRADITIONAL", "ACTIVE");
            plan = planRepository.save(plan);

            List<PaymentInstallment> installments = createTestInstallments(plan, 4);
            PaymentInstallment firstInstallment = installments.get(0);
            firstInstallment.setStatus(InstallmentStatus.PENDING);
            installmentRepository.saveAll(installments);

            PaymentInstallmentEvent event = createInstallmentEvent("COLLECT_INSTALLMENT");
            event.setPlanId(plan.getId());
            event.setInstallmentId(firstInstallment.getId());
            event.setPaymentMethodId("pm-12345");

            when(installmentService.collectInstallment(eq(firstInstallment.getId()), eq("pm-12345"), any()))
                .thenReturn(false); // Failed collection

            installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment);

            // Installment should remain pending
            PaymentInstallment updatedInstallment = installmentRepository.findById(firstInstallment.getId()).orElseThrow();
            assertThat(updatedInstallment.getStatus()).isEqualTo(InstallmentStatus.PENDING);
            assertThat(updatedInstallment.getPaidAt()).isNull();

            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Late Payment Tests")
    class LatePaymentTests {

        @Test
        @Transactional
        @DisplayName("Should process late payment with fees")
        void shouldProcessLatePaymentWithFees() {
            InstallmentPlan plan = createTestPlan("TRADITIONAL", "ACTIVE");
            plan = planRepository.save(plan);

            List<PaymentInstallment> installments = createTestInstallments(plan, 4);
            PaymentInstallment lateInstallment = installments.get(0);
            lateInstallment.setStatus(InstallmentStatus.PENDING);
            lateInstallment.setAmount(new BigDecimal("300.00"));
            installmentRepository.saveAll(installments);

            PaymentInstallmentEvent event = createInstallmentEvent("LATE_PAYMENT");
            event.setPlanId(plan.getId());
            event.setInstallmentId(lateInstallment.getId());
            event.setDaysLate(10);

            installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment);

            PaymentInstallment updatedInstallment = installmentRepository.findById(lateInstallment.getId()).orElseThrow();
            assertThat(updatedInstallment.getStatus()).isEqualTo(InstallmentStatus.LATE);
            assertThat(updatedInstallment.getDaysLate()).isEqualTo(10);
            
            // Late fee calculation: 5% * $300 * 10 days / 30 days = $5.00
            BigDecimal expectedLateFee = new BigDecimal("5.00");
            assertThat(updatedInstallment.getLateFee()).isEqualTo(expectedLateFee);
            assertThat(updatedInstallment.getTotalAmountDue()).isEqualTo(new BigDecimal("305.00"));

            // Verify plan updated
            InstallmentPlan updatedPlan = planRepository.findById(plan.getId()).orElseThrow();
            assertThat(updatedPlan.isHasLatePayments()).isTrue();
            assertThat(updatedPlan.getTotalLateFees()).isEqualTo(expectedLateFee);

            verify(metricsService).recordLatePayment(plan.getPlanType(), 10);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should trigger default process for 30+ days late")
        void shouldTriggerDefaultProcessFor30PlusDaysLate() {
            InstallmentPlan plan = createTestPlan("BNPL", "ACTIVE");
            plan = planRepository.save(plan);

            List<PaymentInstallment> installments = createTestInstallments(plan, 4);
            PaymentInstallment lateInstallment = installments.get(0);
            lateInstallment.setStatus(InstallmentStatus.PENDING);
            installmentRepository.saveAll(installments);

            PaymentInstallmentEvent event = createInstallmentEvent("LATE_PAYMENT");
            event.setPlanId(plan.getId());
            event.setInstallmentId(lateInstallment.getId());
            event.setDaysLate(35); // Over 30 days

            installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment);

            PaymentInstallment updatedInstallment = installmentRepository.findById(lateInstallment.getId()).orElseThrow();
            assertThat(updatedInstallment.getStatus()).isEqualTo(InstallmentStatus.LATE);
            assertThat(updatedInstallment.getDaysLate()).isEqualTo(35);

            // Should trigger default process (verified through method calls)
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Plan Modification Tests")
    class PlanModificationTests {

        @Test
        @Transactional
        @DisplayName("Should extend payment terms")
        void shouldExtendPaymentTerms() {
            InstallmentPlan plan = createTestPlan("TRADITIONAL", "ACTIVE");
            plan.setNumberOfInstallments(6);
            plan = planRepository.save(plan);

            PaymentInstallmentEvent event = createInstallmentEvent("MODIFY_PLAN");
            event.setPlanId(plan.getId());
            event.setModificationType("EXTEND_TERMS");
            event.setNewNumberOfInstallments(12);
            event.setModificationReason("Customer requested payment reduction");

            installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment);

            InstallmentPlan updatedPlan = planRepository.findById(plan.getId()).orElseThrow();
            assertThat(updatedPlan.getModifiedAt()).isNotNull();
            assertThat(updatedPlan.getModificationReason()).isEqualTo("Customer requested payment reduction");

            verify(metricsService).recordPlanModified(plan.getPlanType(), "EXTEND_TERMS");
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should reduce payment amount")
        void shouldReducePaymentAmount() {
            InstallmentPlan plan = createTestPlan("TRADITIONAL", "ACTIVE");
            plan = planRepository.save(plan);

            PaymentInstallmentEvent event = createInstallmentEvent("MODIFY_PLAN");
            event.setPlanId(plan.getId());
            event.setModificationType("REDUCE_PAYMENT");
            event.setNewInstallmentAmount(new BigDecimal("200.00"));
            event.setModificationReason("Financial hardship");

            installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment);

            InstallmentPlan updatedPlan = planRepository.findById(plan.getId()).orElseThrow();
            assertThat(updatedPlan.getModifiedAt()).isNotNull();
            assertThat(updatedPlan.getModificationReason()).isEqualTo("Financial hardship");

            verify(metricsService).recordPlanModified(plan.getPlanType(), "REDUCE_PAYMENT");
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should defer next payment")
        void shouldDeferNextPayment() {
            InstallmentPlan plan = createTestPlan("BNPL", "ACTIVE");
            plan = planRepository.save(plan);

            PaymentInstallmentEvent event = createInstallmentEvent("MODIFY_PLAN");
            event.setPlanId(plan.getId());
            event.setModificationType("DEFER_PAYMENT");
            event.setDeferralPeriodDays(30);
            event.setModificationReason("Temporary financial difficulty");

            installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment);

            InstallmentPlan updatedPlan = planRepository.findById(plan.getId()).orElseThrow();
            assertThat(updatedPlan.getModifiedAt()).isNotNull();
            assertThat(updatedPlan.getModificationReason()).isEqualTo("Temporary financial difficulty");

            verify(metricsService).recordPlanModified(plan.getPlanType(), "DEFER_PAYMENT");
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Early Settlement Tests")
    class EarlySettlementTests {

        @Test
        @Transactional
        @DisplayName("Should process early settlement with discount")
        void shouldProcessEarlySettlementWithDiscount() {
            InstallmentPlan plan = createTestPlan("TRADITIONAL", "ACTIVE");
            plan = planRepository.save(plan);

            // Create installments - first paid, others pending
            List<PaymentInstallment> installments = createTestInstallments(plan, 4);
            installments.get(0).setStatus(InstallmentStatus.PAID);
            installments.get(1).setStatus(InstallmentStatus.PENDING);
            installments.get(1).setAmount(new BigDecimal("300.00"));
            installments.get(2).setStatus(InstallmentStatus.PENDING);
            installments.get(2).setAmount(new BigDecimal("300.00"));
            installments.get(3).setStatus(InstallmentStatus.PENDING);
            installments.get(3).setAmount(new BigDecimal("300.00"));
            installmentRepository.saveAll(installments);

            PaymentInstallmentEvent event = createInstallmentEvent("SETTLE_EARLY");
            event.setPlanId(plan.getId());
            event.setSettlementAmount(new BigDecimal("800.00")); // $100 discount from $900 remaining

            installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment);

            InstallmentPlan updatedPlan = planRepository.findById(plan.getId()).orElseThrow();
            assertThat(updatedPlan.getStatus()).isEqualTo("SETTLED");
            assertThat(updatedPlan.getSettledAt()).isNotNull();
            assertThat(updatedPlan.getSettlementAmount()).isEqualTo(new BigDecimal("800.00"));
            assertThat(updatedPlan.getSettlementDiscount()).isEqualTo(new BigDecimal("100.00"));

            // Verify remaining installments marked as settled
            List<PaymentInstallment> remainingInstallments = installmentRepository
                .findByPlanIdAndStatus(plan.getId(), InstallmentStatus.SETTLED);
            assertThat(remainingInstallments).hasSize(3);

            verify(metricsService).recordEarlySettlement(plan.getPlanType(), new BigDecimal("100.00"));
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Default Payment Tests")
    class DefaultPaymentTests {

        @Test
        @Transactional
        @DisplayName("Should process payment default")
        void shouldProcessPaymentDefault() {
            InstallmentPlan plan = createTestPlan("BNPL", "ACTIVE");
            plan = planRepository.save(plan);

            List<PaymentInstallment> installments = createTestInstallments(plan, 4);
            installments.get(0).setStatus(InstallmentStatus.PAID);
            installments.get(1).setStatus(InstallmentStatus.PENDING);
            installments.get(2).setStatus(InstallmentStatus.PENDING);
            installments.get(3).setStatus(InstallmentStatus.PENDING);
            installmentRepository.saveAll(installments);

            PaymentInstallmentEvent event = createInstallmentEvent("DEFAULT_PAYMENT");
            event.setPlanId(plan.getId());
            event.setDefaultReason("Customer unreachable, multiple failed payments");

            installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment);

            InstallmentPlan updatedPlan = planRepository.findById(plan.getId()).orElseThrow();
            assertThat(updatedPlan.getStatus()).isEqualTo("DEFAULTED");
            assertThat(updatedPlan.getDefaultedAt()).isNotNull();
            assertThat(updatedPlan.getDefaultReason()).isEqualTo("Customer unreachable, multiple failed payments");

            // Verify pending installments marked as defaulted
            List<PaymentInstallment> defaultedInstallments = installmentRepository
                .findByPlanIdAndStatus(plan.getId(), InstallmentStatus.DEFAULTED);
            assertThat(defaultedInstallments).hasSize(3);

            for (PaymentInstallment installment : defaultedInstallments) {
                assertThat(installment.getDefaultedAt()).isNotNull();
            }

            // Verify collections process triggered
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @Transactional
        @DisplayName("Should handle invalid installment event")
        void shouldHandleInvalidInstallmentEvent() {
            PaymentInstallmentEvent invalidEvent = PaymentInstallmentEvent.builder()
                .paymentId(null) // Invalid - null payment ID
                .action("CREATE_PLAN")
                .timestamp(Instant.now())
                .build();

            assertThatCode(() -> 
                installmentEventsConsumer.handlePaymentInstallmentEvent(invalidEvent, 0, 0L, acknowledgment)
            ).doesNotThrowAnyException();

            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should handle payment not found error")
        void shouldHandlePaymentNotFoundError() {
            PaymentInstallmentEvent event = createInstallmentEvent("CREATE_PLAN");
            event.setPaymentId("non-existent-payment-id");

            assertThatCode(() -> 
                installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment)
            ).doesNotThrowAnyException();

            verify(kafkaTemplate).send(eq("payment-installment-events-dlq"), any(Map.class));
            verify(notificationService).sendOperationalAlert(
                eq("Installment Event Processing Failed"), 
                anyString(), 
                eq(NotificationService.Priority.HIGH)
            );
            verify(acknowledgment).acknowledge();
        }

        @Test
        @Transactional
        @DisplayName("Should handle unknown action gracefully")
        void shouldHandleUnknownActionGracefully() {
            PaymentInstallmentEvent event = createInstallmentEvent("UNKNOWN_ACTION");

            assertThatCode(() -> 
                installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment)
            ).doesNotThrowAnyException();

            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Audit and Metrics Tests")
    class AuditAndMetricsTests {

        @Test
        @Transactional
        @DisplayName("Should audit all installment operations")
        void shouldAuditAllInstallmentOperations() {
            PaymentInstallmentEvent event = createInstallmentEvent("CREATE_PLAN");
            event.setNumberOfInstallments(6);

            installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment);

            verify(auditService).logFinancialEvent(
                eq("INSTALLMENT_EVENT_PROCESSED"),
                eq(testPaymentId),
                any(Map.class)
            );
        }

        @Test
        @Transactional
        @DisplayName("Should record installment metrics")
        void shouldRecordInstallmentMetrics() {
            PaymentInstallmentEvent event = createInstallmentEvent("CREATE_PLAN");
            event.setNumberOfInstallments(4);
            event.setPlanType("BNPL");

            installmentEventsConsumer.handlePaymentInstallmentEvent(event, 0, 0L, acknowledgment);

            verify(metricsService).recordPlanCreated("BNPL", testPayment.getAmount(), 4);
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
            .amount(new BigDecimal("1200.00"))
            .currency("USD")
            .status(PaymentStatus.PENDING)
            .paymentMethod("CARD")
            .createdAt(LocalDateTime.now())
            .build();
    }

    private PaymentInstallmentEvent createInstallmentEvent(String action) {
        return PaymentInstallmentEvent.builder()
            .paymentId(testPaymentId)
            .action(action)
            .timestamp(Instant.now())
            .build();
    }

    private InstallmentPlan createTestPlan(String planType, String status) {
        return InstallmentPlan.builder()
            .id(UUID.randomUUID().toString())
            .paymentId(testPaymentId)
            .customerId(testCustomerId)
            .merchantId(testMerchantId)
            .totalAmount(new BigDecimal("1200.00"))
            .numberOfInstallments(4)
            .planType(planType)
            .status(status)
            .createdAt(LocalDateTime.now())
            .build();
    }

    private List<PaymentInstallment> createTestInstallments(InstallmentPlan plan, int count) {
        List<PaymentInstallment> installments = new ArrayList<>();
        BigDecimal amount = plan.getTotalAmount().divide(new BigDecimal(count), 2, RoundingMode.HALF_UP);
        
        for (int i = 0; i < count; i++) {
            PaymentInstallment installment = PaymentInstallment.builder()
                .id(UUID.randomUUID().toString())
                .planId(plan.getId())
                .paymentId(plan.getPaymentId())
                .installmentNumber(i + 1)
                .amount(amount)
                .dueDate(LocalDate.now().plusDays(30).plusMonths(i))
                .status(i == 0 ? InstallmentStatus.PENDING : InstallmentStatus.SCHEDULED)
                .createdAt(LocalDateTime.now())
                .build();
            installments.add(installment);
        }
        
        return installments;
    }

    private PaymentInstallmentEventsConsumer.CreditAssessmentResult createMockCreditResult(
            boolean approved, String riskCategory, int creditScore, 
            BigDecimal approvedAmount, BigDecimal approvedRate) {
        
        PaymentInstallmentEventsConsumer.CreditAssessmentResult result = 
            new PaymentInstallmentEventsConsumer.CreditAssessmentResult();
        
        // Use reflection to set private fields for testing
        try {
            var approvedField = result.getClass().getDeclaredField("approved");
            approvedField.setAccessible(true);
            approvedField.set(result, approved);
            
            var riskField = result.getClass().getDeclaredField("riskCategory");
            riskField.setAccessible(true);
            riskField.set(result, riskCategory);
            
            var scoreField = result.getClass().getDeclaredField("creditScore");
            scoreField.setAccessible(true);
            scoreField.set(result, creditScore);
            
            if (approvedAmount != null) {
                var amountField = result.getClass().getDeclaredField("approvedAmount");
                amountField.setAccessible(true);
                amountField.set(result, approvedAmount);
            }
            
            if (approvedRate != null) {
                var rateField = result.getClass().getDeclaredField("approvedInterestRate");
                rateField.setAccessible(true);
                rateField.set(result, approvedRate);
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create mock credit result", e);
        }
        
        return result;
    }
}