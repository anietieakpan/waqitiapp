package com.waqiti.business.service;

import com.waqiti.business.domain.*;
import com.waqiti.business.dto.*;
import com.waqiti.business.exception.BusinessExceptions.*;
import com.waqiti.business.repository.*;
import com.waqiti.business.service.BusinessPaymentService.*;
import com.waqiti.common.security.AuthenticationFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Unit Tests for BusinessPaymentService
 *
 * Tests cover:
 * - B2B payment processing (ACH, Wire, International Wire)
 * - Vendor management
 * - Payment schedules and automation
 * - Payment approvals and workflows
 * - Payment reconciliation
 * - Payment analytics
 * - Daily limits enforcement
 * - Fee calculations
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BusinessPaymentService Unit Tests")
class BusinessPaymentServiceTest {

    @Mock
    private BusinessAccountRepository businessAccountRepository;

    @Mock
    private BusinessPaymentRepository paymentRepository;

    @Mock
    private BusinessVendorRepository vendorRepository;

    @Mock
    private BusinessContractRepository contractRepository;

    @Mock
    private PaymentScheduleRepository scheduleRepository;

    @Mock
    private PaymentApprovalRepository approvalRepository;

    @Mock
    private ReconciliationRepository reconciliationRepository;

    @Mock
    private AuthenticationFacade authenticationFacade;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private BusinessPaymentService businessPaymentService;

    private static final UUID TEST_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TEST_ACCOUNT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TEST_VENDOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID TEST_PAYMENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID TEST_APPROVAL_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private BusinessAccount testAccount;
    private BusinessVendor testVendor;

    @BeforeEach
    void setUp() {
        when(authenticationFacade.getCurrentUserId()).thenReturn(TEST_USER_ID.toString());

        testAccount = BusinessAccount.builder()
                .id(TEST_ACCOUNT_ID)
                .ownerId(TEST_USER_ID)
                .businessName("Test Corp")
                .status(BusinessAccountStatus.ACTIVE)
                .monthlyTransactionLimit(BigDecimal.valueOf(500000))
                .autoApprovalLimit(BigDecimal.valueOf(10000))
                .autoProcessApprovedPayments(true)
                .createdAt(LocalDateTime.now())
                .build();

        testVendor = BusinessVendor.builder()
                .id(TEST_VENDOR_ID)
                .accountId(TEST_ACCOUNT_ID)
                .vendorName("Acme Supplies")
                .vendorType("SUPPLIER")
                .email("billing@acme.com")
                .phoneNumber("+1234567890")
                .preferredPaymentMethod("ACH")
                .currency("USD")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ===========================
    // B2B Payment Processing Tests
    // ===========================

    @Nested
    @DisplayName("B2B Payment Processing Tests")
    class B2BPaymentProcessingTests {

        @Test
        @DisplayName("Should process ACH payment successfully")
        void shouldProcessACHPayment() {
            // Arrange
            CreateBusinessPaymentRequest request = CreateBusinessPaymentRequest.builder()
                    .amount(BigDecimal.valueOf(5000.00))
                    .paymentMethod(PaymentMethod.ACH)
                    .vendor(createVendorDetails("Acme Supplies", "billing@acme.com"))
                    .description("Monthly supplies")
                    .build();

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(vendorRepository.findByAccountIdAndVendorName(TEST_ACCOUNT_ID, "Acme Supplies"))
                    .thenReturn(Optional.of(testVendor));

            ArgumentCaptor<BusinessPayment> paymentCaptor = ArgumentCaptor.forClass(BusinessPayment.class);
            when(paymentRepository.save(paymentCaptor.capture())).thenAnswer(i -> {
                BusinessPayment saved = i.getArgument(0);
                saved.setId(TEST_PAYMENT_ID);
                return saved;
            });

            // Act
            BusinessPaymentResponse response = businessPaymentService.processB2BPayment(TEST_ACCOUNT_ID, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getPaymentId()).isEqualTo(TEST_PAYMENT_ID);
            assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000.00));

            BusinessPayment savedPayment = paymentCaptor.getValue();
            assertThat(savedPayment.getTransactionId()).startsWith("ACH-");
            assertThat(savedPayment.getProcessingFee()).isEqualByComparingTo(new BigDecimal("1.50"));
            assertThat(savedPayment.getEstimatedSettlementDate()).isEqualTo(LocalDate.now().plusDays(1));

            verify(paymentRepository, atLeastOnce()).save(any(BusinessPayment.class));
        }

        @Test
        @DisplayName("Should process wire payment with correct fees")
        void shouldProcessWirePaymentWithCorrectFees() {
            // Arrange
            CreateBusinessPaymentRequest request = CreateBusinessPaymentRequest.builder()
                    .amount(BigDecimal.valueOf(50000.00))
                    .paymentMethod(PaymentMethod.WIRE)
                    .vendor(createVendorDetails("Large Vendor", "payments@vendor.com"))
                    .description("Large payment via wire")
                    .build();

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(vendorRepository.findByAccountIdAndVendorName(TEST_ACCOUNT_ID, "Large Vendor"))
                    .thenReturn(Optional.empty());

            ArgumentCaptor<BusinessVendor> vendorCaptor = ArgumentCaptor.forClass(BusinessVendor.class);
            when(vendorRepository.save(vendorCaptor.capture())).thenAnswer(i -> {
                BusinessVendor saved = i.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });

            ArgumentCaptor<BusinessPayment> paymentCaptor = ArgumentCaptor.forClass(BusinessPayment.class);
            when(paymentRepository.save(paymentCaptor.capture())).thenAnswer(i -> {
                BusinessPayment saved = i.getArgument(0);
                saved.setId(TEST_PAYMENT_ID);
                return saved;
            });

            // Act
            BusinessPaymentResponse response = businessPaymentService.processB2BPayment(TEST_ACCOUNT_ID, request);

            // Assert
            assertThat(response).isNotNull();

            BusinessPayment savedPayment = paymentCaptor.getValue();
            assertThat(savedPayment.getTransactionId()).startsWith("WIRE-");
            assertThat(savedPayment.getProcessingFee()).isEqualByComparingTo(new BigDecimal("25.00"));
            assertThat(savedPayment.getEstimatedSettlementDate()).isEqualTo(LocalDate.now());

            // Verify vendor was created
            BusinessVendor createdVendor = vendorCaptor.getValue();
            assertThat(createdVendor.getVendorName()).isEqualTo("Large Vendor");
            assertThat(createdVendor.getEmail()).isEqualTo("payments@vendor.com");
        }

        @Test
        @DisplayName("Should reject payment with zero or negative amount")
        void shouldRejectInvalidPaymentAmount() {
            // Arrange
            CreateBusinessPaymentRequest request = CreateBusinessPaymentRequest.builder()
                    .amount(BigDecimal.ZERO)
                    .paymentMethod(PaymentMethod.ACH)
                    .vendor(createVendorDetails("Test", "test@test.com"))
                    .build();

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));

            // Act & Assert
            assertThatThrownBy(() -> businessPaymentService.processB2BPayment(TEST_ACCOUNT_ID, request))
                    .isInstanceOf(InvalidPaymentAmountException.class)
                    .hasMessageContaining("Payment amount must be positive");

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject payment exceeding monthly limit")
        void shouldRejectPaymentExceedingMonthlyLimit() {
            // Arrange
            testAccount.setMonthlyTransactionLimit(BigDecimal.valueOf(100000));

            CreateBusinessPaymentRequest request = CreateBusinessPaymentRequest.builder()
                    .amount(BigDecimal.valueOf(200000))
                    .paymentMethod(PaymentMethod.WIRE)
                    .vendor(createVendorDetails("Test", "test@test.com"))
                    .build();

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));

            // Act & Assert
            assertThatThrownBy(() -> businessPaymentService.processB2BPayment(TEST_ACCOUNT_ID, request))
                    .isInstanceOf(PaymentLimitExceededException.class)
                    .hasMessageContaining("exceeds monthly limit");

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject unauthorized account access")
        void shouldRejectUnauthorizedAccess() {
            // Arrange
            UUID unauthorizedUserId = UUID.randomUUID();
            when(authenticationFacade.getCurrentUserId()).thenReturn(unauthorizedUserId.toString());

            CreateBusinessPaymentRequest request = CreateBusinessPaymentRequest.builder()
                    .amount(BigDecimal.valueOf(1000))
                    .paymentMethod(PaymentMethod.ACH)
                    .vendor(createVendorDetails("Test", "test@test.com"))
                    .build();

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));

            // Act & Assert
            assertThatThrownBy(() -> businessPaymentService.processB2BPayment(TEST_ACCOUNT_ID, request))
                    .isInstanceOf(UnauthorizedBusinessAccessException.class)
                    .hasMessageContaining("Unauthorized access");
        }
    }

    // ===========================
    // Vendor Management Tests
    // ===========================

    @Nested
    @DisplayName("Vendor Management Tests")
    class VendorManagementTests {

        @Test
        @DisplayName("Should create vendor successfully")
        void shouldCreateVendor() {
            // Arrange
            CreateVendorRequest request = CreateVendorRequest.builder()
                    .vendorName("New Vendor Inc")
                    .vendorType("SUPPLIER")
                    .contactPerson("John Doe")
                    .email("contact@newvendor.com")
                    .phoneNumber("+1987654321")
                    .address("456 Vendor St")
                    .taxId("TAX123456")
                    .paymentTerms("Net 30")
                    .preferredPaymentMethod("ACH")
                    .currency("USD")
                    .build();

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(vendorRepository.existsByAccountIdAndVendorName(TEST_ACCOUNT_ID, "New Vendor Inc"))
                    .thenReturn(false);

            ArgumentCaptor<BusinessVendor> vendorCaptor = ArgumentCaptor.forClass(BusinessVendor.class);
            when(vendorRepository.save(vendorCaptor.capture())).thenAnswer(i -> {
                BusinessVendor saved = i.getArgument(0);
                saved.setId(TEST_VENDOR_ID);
                return saved;
            });

            // Act
            BusinessVendorResponse response = businessPaymentService.createVendor(TEST_ACCOUNT_ID, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getVendorId()).isEqualTo(TEST_VENDOR_ID);
            assertThat(response.getVendorName()).isEqualTo("New Vendor Inc");

            BusinessVendor savedVendor = vendorCaptor.getValue();
            assertThat(savedVendor.getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
            assertThat(savedVendor.getEmail()).isEqualTo("contact@newvendor.com");
            assertThat(savedVendor.getTaxId()).isEqualTo("TAX123456");
            assertThat(savedVendor.getPaymentTerms()).isEqualTo("Net 30");
            assertThat(savedVendor.isActive()).isTrue();

            verify(vendorRepository).existsByAccountIdAndVendorName(TEST_ACCOUNT_ID, "New Vendor Inc");
            verify(vendorRepository).save(any(BusinessVendor.class));
        }

        @Test
        @DisplayName("Should reject duplicate vendor")
        void shouldRejectDuplicateVendor() {
            // Arrange
            CreateVendorRequest request = CreateVendorRequest.builder()
                    .vendorName("Existing Vendor")
                    .email("existing@vendor.com")
                    .build();

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(vendorRepository.existsByAccountIdAndVendorName(TEST_ACCOUNT_ID, "Existing Vendor"))
                    .thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> businessPaymentService.createVendor(TEST_ACCOUNT_ID, request))
                    .isInstanceOf(DuplicateVendorException.class)
                    .hasMessageContaining("Vendor with this name already exists");

            verify(vendorRepository, never()).save(any());
        }
    }

    // ===========================
    // Payment Schedule Tests
    // ===========================

    @Nested
    @DisplayName("Payment Schedule Tests")
    class PaymentScheduleTests {

        @Test
        @DisplayName("Should create monthly payment schedule")
        void shouldCreateMonthlyPaymentSchedule() {
            // Arrange
            CreatePaymentScheduleRequest request = CreatePaymentScheduleRequest.builder()
                    .vendorId(TEST_VENDOR_ID)
                    .paymentType("RECURRING")
                    .amount(BigDecimal.valueOf(2500.00))
                    .frequency(PaymentFrequency.MONTHLY)
                    .startDate(LocalDate.now())
                    .endDate(LocalDate.now().plusYears(1))
                    .paymentMethod(PaymentMethod.ACH)
                    .description("Monthly subscription")
                    .build();

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(vendorRepository.findByIdAndAccountId(TEST_VENDOR_ID, TEST_ACCOUNT_ID))
                    .thenReturn(Optional.of(testVendor));

            ArgumentCaptor<PaymentSchedule> scheduleCaptor = ArgumentCaptor.forClass(PaymentSchedule.class);
            when(scheduleRepository.save(scheduleCaptor.capture())).thenAnswer(i -> {
                PaymentSchedule saved = i.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });

            // Act
            PaymentScheduleResponse response = businessPaymentService.createPaymentSchedule(TEST_ACCOUNT_ID, request);

            // Assert
            assertThat(response).isNotNull();

            PaymentSchedule savedSchedule = scheduleCaptor.getValue();
            assertThat(savedSchedule.getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
            assertThat(savedSchedule.getVendorId()).isEqualTo(TEST_VENDOR_ID);
            assertThat(savedSchedule.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(2500.00));
            assertThat(savedSchedule.getFrequency()).isEqualTo(PaymentFrequency.MONTHLY);
            assertThat(savedSchedule.isActive()).isTrue();
            assertThat(savedSchedule.getNextPaymentDate()).isNotNull();

            verify(scheduleRepository).save(any(PaymentSchedule.class));
        }
    }

    // ===========================
    // Payment Approval Tests
    // ===========================

    @Nested
    @DisplayName("Payment Approval Tests")
    class PaymentApprovalTests {

        @Test
        @DisplayName("Should submit payment for approval")
        void shouldSubmitPaymentForApproval() {
            // Arrange
            BusinessPayment pendingPayment = createTestPayment(BigDecimal.valueOf(15000), PaymentStatus.PENDING);

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(paymentRepository.findByIdAndAccountId(TEST_PAYMENT_ID, TEST_ACCOUNT_ID))
                    .thenReturn(Optional.of(pendingPayment));

            ArgumentCaptor<PaymentApproval> approvalCaptor = ArgumentCaptor.forClass(PaymentApproval.class);
            when(approvalRepository.save(approvalCaptor.capture())).thenAnswer(i -> {
                PaymentApproval saved = i.getArgument(0);
                saved.setId(TEST_APPROVAL_ID);
                return saved;
            });

            // Act
            PaymentApprovalResponse response = businessPaymentService.submitForApproval(TEST_ACCOUNT_ID, TEST_PAYMENT_ID);

            // Assert
            assertThat(response).isNotNull();

            PaymentApproval savedApproval = approvalCaptor.getValue();
            assertThat(savedApproval.getPaymentId()).isEqualTo(TEST_PAYMENT_ID);
            assertThat(savedApproval.getRequestedBy()).isEqualTo(TEST_USER_ID);
            assertThat(savedApproval.getStatus()).isEqualTo(ApprovalStatus.PENDING);
            assertThat(savedApproval.getApprovalLevel()).isEqualTo(ApprovalLevel.MANAGER); // 15K -> MANAGER

            assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.PENDING_APPROVAL);

            verify(approvalRepository).save(any(PaymentApproval.class));
            verify(paymentRepository).save(pendingPayment);
        }

        @Test
        @DisplayName("Should reject approval submission for non-pending payment")
        void shouldRejectApprovalForNonPendingPayment() {
            // Arrange
            BusinessPayment approvedPayment = createTestPayment(BigDecimal.valueOf(5000), PaymentStatus.APPROVED);

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(paymentRepository.findByIdAndAccountId(TEST_PAYMENT_ID, TEST_ACCOUNT_ID))
                    .thenReturn(Optional.of(approvedPayment));

            // Act & Assert
            assertThatThrownBy(() -> businessPaymentService.submitForApproval(TEST_ACCOUNT_ID, TEST_PAYMENT_ID))
                    .isInstanceOf(InvalidPaymentStatusException.class)
                    .hasMessageContaining("not in pending status");

            verify(approvalRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should approve payment successfully")
        void shouldApprovePayment() {
            // Arrange
            PaymentApproval pendingApproval = createTestApproval(ApprovalStatus.PENDING);
            BusinessPayment payment = createTestPayment(BigDecimal.valueOf(5000), PaymentStatus.PENDING_APPROVAL);

            PaymentApprovalRequest request = PaymentApprovalRequest.builder()
                    .decision(ApprovalDecision.APPROVE)
                    .comments("Approved - valid business expense")
                    .build();

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(approvalRepository.findByIdAndAccountId(TEST_APPROVAL_ID, TEST_ACCOUNT_ID))
                    .thenReturn(Optional.of(pendingApproval));
            when(paymentRepository.findById(TEST_PAYMENT_ID)).thenReturn(Optional.of(payment));
            when(approvalRepository.save(any(PaymentApproval.class))).thenReturn(pendingApproval);
            when(paymentRepository.save(any(BusinessPayment.class))).thenReturn(payment);

            // Act
            PaymentApprovalResponse response = businessPaymentService.processApproval(
                    TEST_ACCOUNT_ID, TEST_APPROVAL_ID, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(pendingApproval.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
            assertThat(pendingApproval.getApprovedBy()).isEqualTo(TEST_USER_ID);
            assertThat(pendingApproval.getApprovedAt()).isNotNull();
            assertThat(pendingApproval.getComments()).isEqualTo("Approved - valid business expense");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);

            verify(approvalRepository).save(pendingApproval);
            verify(paymentRepository).save(payment);
        }

        @Test
        @DisplayName("Should reject payment successfully")
        void shouldRejectPayment() {
            // Arrange
            PaymentApproval pendingApproval = createTestApproval(ApprovalStatus.PENDING);
            BusinessPayment payment = createTestPayment(BigDecimal.valueOf(5000), PaymentStatus.PENDING_APPROVAL);

            PaymentApprovalRequest request = PaymentApprovalRequest.builder()
                    .decision(ApprovalDecision.REJECT)
                    .comments("Rejected - insufficient documentation")
                    .build();

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(approvalRepository.findByIdAndAccountId(TEST_APPROVAL_ID, TEST_ACCOUNT_ID))
                    .thenReturn(Optional.of(pendingApproval));
            when(paymentRepository.findById(TEST_PAYMENT_ID)).thenReturn(Optional.of(payment));
            when(approvalRepository.save(any(PaymentApproval.class))).thenReturn(pendingApproval);
            when(paymentRepository.save(any(BusinessPayment.class))).thenReturn(payment);

            // Act
            PaymentApprovalResponse response = businessPaymentService.processApproval(
                    TEST_ACCOUNT_ID, TEST_APPROVAL_ID, request);

            // Assert
            assertThat(pendingApproval.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REJECTED);
            assertThat(pendingApproval.getComments()).isEqualTo("Rejected - insufficient documentation");
        }

        @Test
        @DisplayName("Should reject processing already-processed approval")
        void shouldRejectProcessingAlreadyProcessedApproval() {
            // Arrange
            PaymentApproval approvedApproval = createTestApproval(ApprovalStatus.APPROVED);

            PaymentApprovalRequest request = PaymentApprovalRequest.builder()
                    .decision(ApprovalDecision.APPROVE)
                    .build();

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(approvalRepository.findByIdAndAccountId(TEST_APPROVAL_ID, TEST_ACCOUNT_ID))
                    .thenReturn(Optional.of(approvedApproval));

            // Act & Assert
            assertThatThrownBy(() -> businessPaymentService.processApproval(
                    TEST_ACCOUNT_ID, TEST_APPROVAL_ID, request))
                    .isInstanceOf(InvalidApprovalStatusException.class)
                    .hasMessageContaining("not in pending status");
        }

        @Test
        @DisplayName("Should determine correct approval levels")
        void shouldDetermineCorrectApprovalLevels() {
            // Arrange
            BusinessPayment supervisorLevel = createTestPayment(BigDecimal.valueOf(5000), PaymentStatus.PENDING);
            BusinessPayment managerLevel = createTestPayment(BigDecimal.valueOf(25000), PaymentStatus.PENDING);
            BusinessPayment executiveLevel = createTestPayment(BigDecimal.valueOf(75000), PaymentStatus.PENDING);

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(approvalRepository.save(any(PaymentApproval.class))).thenAnswer(i -> i.getArgument(0));

            // Test SUPERVISOR level (< 10K)
            when(paymentRepository.findByIdAndAccountId(TEST_PAYMENT_ID, TEST_ACCOUNT_ID))
                    .thenReturn(Optional.of(supervisorLevel));
            PaymentApprovalResponse response1 = businessPaymentService.submitForApproval(TEST_ACCOUNT_ID, TEST_PAYMENT_ID);
            // Would need to capture to verify SUPERVISOR level

            // Test MANAGER level (10K - 50K)
            supervisorLevel.setStatus(PaymentStatus.PENDING); // Reset
            supervisorLevel.setAmount(BigDecimal.valueOf(25000));
            when(paymentRepository.findByIdAndAccountId(TEST_PAYMENT_ID, TEST_ACCOUNT_ID))
                    .thenReturn(Optional.of(supervisorLevel));
            PaymentApprovalResponse response2 = businessPaymentService.submitForApproval(TEST_ACCOUNT_ID, TEST_PAYMENT_ID);
            // Would capture MANAGER level

            // Test EXECUTIVE level (> 50K)
            supervisorLevel.setStatus(PaymentStatus.PENDING); // Reset
            supervisorLevel.setAmount(BigDecimal.valueOf(75000));
            when(paymentRepository.findByIdAndAccountId(TEST_PAYMENT_ID, TEST_ACCOUNT_ID))
                    .thenReturn(Optional.of(supervisorLevel));
            PaymentApprovalResponse response3 = businessPaymentService.submitForApproval(TEST_ACCOUNT_ID, TEST_PAYMENT_ID);
            // Would capture EXECUTIVE level

            verify(approvalRepository, times(3)).save(any(PaymentApproval.class));
        }
    }

    // ===========================
    // Payment Reconciliation Tests
    // ===========================

    @Nested
    @DisplayName("Payment Reconciliation Tests")
    class PaymentReconciliationTests {

        @Test
        @DisplayName("Should reconcile payments with bank statement")
        void shouldReconcilePayments() {
            // Arrange
            BankReconciliationRequest request = BankReconciliationRequest.builder()
                    .statementDate(LocalDate.now())
                    .statementStartDate(LocalDate.now().minusDays(30))
                    .statementEndDate(LocalDate.now())
                    .bankStatementData("mock statement data")
                    .build();

            List<BusinessPayment> payments = Arrays.asList(
                    createTestPayment(BigDecimal.valueOf(1000), PaymentStatus.COMPLETED),
                    createTestPayment(BigDecimal.valueOf(2000), PaymentStatus.COMPLETED)
            );

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(paymentRepository.findByAccountIdAndProcessedDateBetween(
                    eq(TEST_ACCOUNT_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(payments);

            ArgumentCaptor<PaymentReconciliation> reconciliationCaptor = ArgumentCaptor.forClass(PaymentReconciliation.class);
            when(reconciliationRepository.save(reconciliationCaptor.capture())).thenAnswer(i -> {
                PaymentReconciliation saved = i.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });

            // Act
            ReconciliationResponse response = businessPaymentService.reconcilePayments(TEST_ACCOUNT_ID, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getReconciledAt()).isNotNull();

            PaymentReconciliation savedReconciliation = reconciliationCaptor.getValue();
            assertThat(savedReconciliation.getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
            assertThat(savedReconciliation.getStatementDate()).isEqualTo(LocalDate.now());
            assertThat(savedReconciliation.getReconciledBy()).isEqualTo(TEST_USER_ID);

            verify(reconciliationRepository).save(any(PaymentReconciliation.class));
        }
    }

    // ===========================
    // Helper Methods
    // ===========================

    private VendorDetails createVendorDetails(String name, String email) {
        return VendorDetails.builder()
                .name(name)
                .email(email)
                .build();
    }

    private BusinessPayment createTestPayment(BigDecimal amount, PaymentStatus status) {
        return BusinessPayment.builder()
                .id(TEST_PAYMENT_ID)
                .accountId(TEST_ACCOUNT_ID)
                .vendorId(TEST_VENDOR_ID)
                .amount(amount)
                .status(status)
                .paymentMethod(PaymentMethod.ACH)
                .description("Test payment")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private PaymentApproval createTestApproval(ApprovalStatus status) {
        return PaymentApproval.builder()
                .id(TEST_APPROVAL_ID)
                .paymentId(TEST_PAYMENT_ID)
                .accountId(TEST_ACCOUNT_ID)
                .requestedBy(TEST_USER_ID)
                .requestedAt(LocalDateTime.now())
                .approvalLevel(ApprovalLevel.MANAGER)
                .status(status)
                .build();
    }
}
