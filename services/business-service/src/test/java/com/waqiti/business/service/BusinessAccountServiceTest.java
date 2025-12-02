package com.waqiti.business.service;

import com.waqiti.business.domain.*;
import com.waqiti.business.dto.*;
import com.waqiti.business.exception.BusinessExceptions.*;
import com.waqiti.business.repository.*;
import com.waqiti.common.security.AuthenticationFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive Unit Tests for BusinessAccountService
 *
 * Tests cover all major service operations:
 * - Business account onboarding and management
 * - Sub-account operations
 * - Employee management
 * - Expense approval workflows
 * - Invoice generation and sending
 * - Tax report generation
 * - Settings management
 * - Security (ownership validation)
 *
 * Uses Mockito for all repository dependencies
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BusinessAccountService Unit Tests")
class BusinessAccountServiceTest {

    @Mock
    private BusinessAccountRepository businessAccountRepository;

    @Mock
    private BusinessSubAccountRepository subAccountRepository;

    @Mock
    private BusinessEmployeeRepository employeeRepository;

    @Mock
    private BusinessExpenseRepository expenseRepository;

    @Mock
    private BusinessInvoiceRepository invoiceRepository;

    @Mock
    private BusinessTaxDocumentRepository taxDocumentRepository;

    @Mock
    private AuthenticationFacade authenticationFacade;

    @InjectMocks
    private BusinessAccountService businessAccountService;

    private static final UUID TEST_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TEST_ACCOUNT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TEST_EMPLOYEE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID TEST_INVOICE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID TEST_EXPENSE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private BusinessAccount testAccount;

    @BeforeEach
    void setUp() {
        // Default authenticated user
        when(authenticationFacade.getCurrentUserId()).thenReturn(TEST_USER_ID.toString());

        // Create test business account
        testAccount = BusinessAccount.builder()
                .id(TEST_ACCOUNT_ID)
                .ownerId(TEST_USER_ID)
                .businessName("Test Corp")
                .businessType("LLC")
                .industry("Technology")
                .registrationNumber("REG123456")
                .taxId("TAX987654")
                .address("123 Test St")
                .phoneNumber("+1234567890")
                .email("test@testcorp.com")
                .website("https://testcorp.com")
                .status(BusinessAccountStatus.ACTIVE)
                .monthlyTransactionLimit(BigDecimal.valueOf(100000))
                .riskLevel(RiskLevel.LOW)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ===========================
    // Business Onboarding Tests
    // ===========================

    @Nested
    @DisplayName("Business Onboarding Tests")
    class BusinessOnboardingTests {

        @Test
        @DisplayName("Should successfully onboard a new business")
        void shouldOnboardNewBusiness() {
            // Arrange
            BusinessOnboardingRequest request = BusinessOnboardingRequest.builder()
                    .businessName("New Business LLC")
                    .businessType("LLC")
                    .industry("Retail")
                    .registrationNumber("NEW123")
                    .taxId("TAX123")
                    .address("456 New St")
                    .phoneNumber("+1987654321")
                    .email("contact@newbiz.com")
                    .website("https://newbiz.com")
                    .build();

            when(businessAccountRepository.existsByOwnerId(TEST_USER_ID)).thenReturn(false);

            ArgumentCaptor<BusinessAccount> accountCaptor = ArgumentCaptor.forClass(BusinessAccount.class);
            when(businessAccountRepository.save(accountCaptor.capture())).thenAnswer(i -> {
                BusinessAccount saved = i.getArgument(0);
                saved.setId(TEST_ACCOUNT_ID);
                return saved;
            });

            // Act
            BusinessOnboardingResponse response = businessAccountService.onboardBusiness(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
            assertThat(response.getBusinessName()).isEqualTo("New Business LLC");
            assertThat(response.getStatus()).isEqualTo(BusinessAccountStatus.PENDING_VERIFICATION.toString());
            assertThat(response.isVerificationRequired()).isTrue();
            assertThat(response.getEstimatedVerificationTime()).isNotNull();

            // Verify saved account
            BusinessAccount savedAccount = accountCaptor.getValue();
            assertThat(savedAccount.getBusinessName()).isEqualTo("New Business LLC");
            assertThat(savedAccount.getOwnerId()).isEqualTo(TEST_USER_ID);
            assertThat(savedAccount.getStatus()).isEqualTo(BusinessAccountStatus.PENDING_VERIFICATION);
            assertThat(savedAccount.getMonthlyTransactionLimit()).isEqualByComparingTo(BigDecimal.valueOf(100000));
            assertThat(savedAccount.getRiskLevel()).isEqualTo(RiskLevel.LOW);

            verify(businessAccountRepository).existsByOwnerId(TEST_USER_ID);
            verify(businessAccountRepository).save(any(BusinessAccount.class));
        }

        @Test
        @DisplayName("Should reject onboarding if user already has a business account")
        void shouldRejectDuplicateBusinessAccount() {
            // Arrange
            BusinessOnboardingRequest request = BusinessOnboardingRequest.builder()
                    .businessName("Duplicate Business")
                    .businessType("Corporation")
                    .build();

            when(businessAccountRepository.existsByOwnerId(TEST_USER_ID)).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> businessAccountService.onboardBusiness(request))
                    .isInstanceOf(DuplicateBusinessAccountException.class)
                    .hasMessageContaining("User already has a business account");

            verify(businessAccountRepository).existsByOwnerId(TEST_USER_ID);
            verify(businessAccountRepository, never()).save(any());
        }
    }

    // ===========================
    // Profile Management Tests
    // ===========================

    @Nested
    @DisplayName("Profile Management Tests")
    class ProfileManagementTests {

        @Test
        @DisplayName("Should get business profile for authorized user")
        void shouldGetBusinessProfile() {
            // Arrange
            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));

            // Act
            BusinessProfileResponse response = businessAccountService.getBusinessProfile(TEST_ACCOUNT_ID);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
            assertThat(response.getBusinessName()).isEqualTo("Test Corp");
            assertThat(response.getBusinessType()).isEqualTo("LLC");
            assertThat(response.getIndustry()).isEqualTo("Technology");
            assertThat(response.getEmail()).isEqualTo("test@testcorp.com");

            verify(businessAccountRepository).findById(TEST_ACCOUNT_ID);
        }

        @Test
        @DisplayName("Should throw exception when getting profile of non-existent account")
        void shouldThrowExceptionForNonExistentAccount() {
            // Arrange
            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> businessAccountService.getBusinessProfile(TEST_ACCOUNT_ID))
                    .isInstanceOf(BusinessAccountNotFoundException.class)
                    .hasMessageContaining("Business account not found");

            verify(businessAccountRepository).findById(TEST_ACCOUNT_ID);
        }

        @Test
        @DisplayName("Should throw exception when unauthorized user accesses profile")
        void shouldRejectUnauthorizedProfileAccess() {
            // Arrange
            UUID unauthorizedUserId = UUID.randomUUID();
            when(authenticationFacade.getCurrentUserId()).thenReturn(unauthorizedUserId.toString());
            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));

            // Act & Assert
            assertThatThrownBy(() -> businessAccountService.getBusinessProfile(TEST_ACCOUNT_ID))
                    .isInstanceOf(UnauthorizedBusinessAccessException.class)
                    .hasMessageContaining("not authorized to access this business account");

            verify(businessAccountRepository).findById(TEST_ACCOUNT_ID);
        }

        @Test
        @DisplayName("Should successfully update business profile")
        void shouldUpdateBusinessProfile() {
            // Arrange
            UpdateBusinessProfileRequest request = UpdateBusinessProfileRequest.builder()
                    .businessName("Updated Corp")
                    .industry("Finance")
                    .address("789 Updated Ave")
                    .phoneNumber("+1555555555")
                    .email("updated@corp.com")
                    .website("https://updated.com")
                    .build();

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(businessAccountRepository.save(any(BusinessAccount.class))).thenReturn(testAccount);

            // Act
            BusinessProfileResponse response = businessAccountService.updateBusinessProfile(TEST_ACCOUNT_ID, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(testAccount.getBusinessName()).isEqualTo("Updated Corp");
            assertThat(testAccount.getIndustry()).isEqualTo("Finance");
            assertThat(testAccount.getEmail()).isEqualTo("updated@corp.com");
            assertThat(testAccount.getUpdatedAt()).isNotNull();

            verify(businessAccountRepository).findById(TEST_ACCOUNT_ID);
            verify(businessAccountRepository).save(testAccount);
        }
    }

    // ===========================
    // Sub-Account Management Tests
    // ===========================

    @Nested
    @DisplayName("Sub-Account Management Tests")
    class SubAccountManagementTests {

        @Test
        @DisplayName("Should create sub-account successfully")
        void shouldCreateSubAccount() {
            // Arrange
            CreateSubAccountRequest request = CreateSubAccountRequest.builder()
                    .accountName("Marketing Budget")
                    .accountType("EXPENSE")
                    .purpose("Marketing campaigns")
                    .spendingLimit(BigDecimal.valueOf(50000))
                    .build();

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(subAccountRepository.countByMainAccountId(TEST_ACCOUNT_ID)).thenReturn(3L);

            ArgumentCaptor<BusinessSubAccount> subAccountCaptor = ArgumentCaptor.forClass(BusinessSubAccount.class);
            when(subAccountRepository.save(subAccountCaptor.capture())).thenAnswer(i -> {
                BusinessSubAccount saved = i.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });

            // Act
            SubAccountResponse response = businessAccountService.createSubAccount(TEST_ACCOUNT_ID, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getAccountName()).isEqualTo("Marketing Budget");
            assertThat(response.getAccountType()).isEqualTo("EXPENSE");

            BusinessSubAccount savedSubAccount = subAccountCaptor.getValue();
            assertThat(savedSubAccount.getMainAccountId()).isEqualTo(TEST_ACCOUNT_ID);
            assertThat(savedSubAccount.getSpendingLimit()).isEqualByComparingTo(BigDecimal.valueOf(50000));
            assertThat(savedSubAccount.isActive()).isTrue();

            verify(subAccountRepository).countByMainAccountId(TEST_ACCOUNT_ID);
            verify(subAccountRepository).save(any(BusinessSubAccount.class));
        }

        @Test
        @DisplayName("Should reject sub-account creation when limit exceeded")
        void shouldRejectSubAccountCreationWhenLimitExceeded() {
            // Arrange
            CreateSubAccountRequest request = CreateSubAccountRequest.builder()
                    .accountName("Extra Account")
                    .accountType("EXPENSE")
                    .build();

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(subAccountRepository.countByMainAccountId(TEST_ACCOUNT_ID)).thenReturn(10L);

            // Act & Assert
            assertThatThrownBy(() -> businessAccountService.createSubAccount(TEST_ACCOUNT_ID, request))
                    .isInstanceOf(SubAccountLimitExceededException.class)
                    .hasMessageContaining("Maximum number of sub-accounts reached");

            verify(subAccountRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should retrieve active sub-accounts")
        void shouldRetrieveActiveSubAccounts() {
            // Arrange
            List<BusinessSubAccount> subAccounts = Arrays.asList(
                    createTestSubAccount("Marketing", "EXPENSE"),
                    createTestSubAccount("Operations", "OPERATIONAL")
            );

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(subAccountRepository.findByMainAccountIdAndIsActiveTrue(TEST_ACCOUNT_ID)).thenReturn(subAccounts);

            // Act
            List<SubAccountResponse> responses = businessAccountService.getSubAccounts(TEST_ACCOUNT_ID);

            // Assert
            assertThat(responses).hasSize(2);
            assertThat(responses).extracting(SubAccountResponse::getAccountName)
                    .containsExactly("Marketing", "Operations");

            verify(subAccountRepository).findByMainAccountIdAndIsActiveTrue(TEST_ACCOUNT_ID);
        }
    }

    // ===========================
    // Employee Management Tests
    // ===========================

    @Nested
    @DisplayName("Employee Management Tests")
    class EmployeeManagementTests {

        @Test
        @DisplayName("Should add employee successfully")
        void shouldAddEmployee() {
            // Arrange
            AddEmployeeRequest request = AddEmployeeRequest.builder()
                    .firstName("John")
                    .lastName("Doe")
                    .email("john.doe@testcorp.com")
                    .phoneNumber("+1234567890")
                    .department("Engineering")
                    .role("Software Engineer")
                    .spendingLimit(BigDecimal.valueOf(5000))
                    .hireDate(LocalDate.now())
                    .permissions(List.of("EXPENSE_SUBMIT", "CARD_REQUEST"))
                    .build();

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(employeeRepository.existsByAccountIdAndEmail(TEST_ACCOUNT_ID, "john.doe@testcorp.com")).thenReturn(false);

            ArgumentCaptor<BusinessEmployee> employeeCaptor = ArgumentCaptor.forClass(BusinessEmployee.class);
            when(employeeRepository.save(employeeCaptor.capture())).thenAnswer(i -> {
                BusinessEmployee saved = i.getArgument(0);
                saved.setId(TEST_EMPLOYEE_ID);
                return saved;
            });

            // Act
            BusinessEmployeeResponse response = businessAccountService.addEmployee(TEST_ACCOUNT_ID, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getEmployeeId()).isEqualTo(TEST_EMPLOYEE_ID);
            assertThat(response.getFirstName()).isEqualTo("John");
            assertThat(response.getLastName()).isEqualTo("Doe");
            assertThat(response.getEmail()).isEqualTo("john.doe@testcorp.com");
            assertThat(response.getDepartment()).isEqualTo("Engineering");

            BusinessEmployee savedEmployee = employeeCaptor.getValue();
            assertThat(savedEmployee.getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
            assertThat(savedEmployee.getSpendingLimit()).isEqualByComparingTo(BigDecimal.valueOf(5000));

            verify(employeeRepository).existsByAccountIdAndEmail(TEST_ACCOUNT_ID, "john.doe@testcorp.com");
            verify(employeeRepository).save(any(BusinessEmployee.class));
        }

        @Test
        @DisplayName("Should reject duplicate employee email")
        void shouldRejectDuplicateEmployeeEmail() {
            // Arrange
            AddEmployeeRequest request = AddEmployeeRequest.builder()
                    .firstName("Jane")
                    .lastName("Doe")
                    .email("existing@testcorp.com")
                    .build();

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(employeeRepository.existsByAccountIdAndEmail(TEST_ACCOUNT_ID, "existing@testcorp.com")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> businessAccountService.addEmployee(TEST_ACCOUNT_ID, request))
                    .isInstanceOf(DuplicateEmployeeException.class)
                    .hasMessageContaining("Employee with this email already exists");

            verify(employeeRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should update employee successfully")
        void shouldUpdateEmployee() {
            // Arrange
            BusinessEmployee existingEmployee = createTestEmployee("John", "Doe", "john@test.com");

            UpdateEmployeeRequest request = UpdateEmployeeRequest.builder()
                    .firstName("John")
                    .lastName("Smith") // Changed last name
                    .phoneNumber("+1111111111")
                    .department("Product")
                    .role("Senior Engineer")
                    .spendingLimit(BigDecimal.valueOf(10000))
                    .permissions(List.of("EXPENSE_APPROVE", "CARD_ISSUE"))
                    .build();

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(employeeRepository.findByIdAndAccountId(TEST_EMPLOYEE_ID, TEST_ACCOUNT_ID))
                    .thenReturn(Optional.of(existingEmployee));
            when(employeeRepository.save(any(BusinessEmployee.class))).thenReturn(existingEmployee);

            // Act
            BusinessEmployeeResponse response = businessAccountService.updateEmployee(
                    TEST_ACCOUNT_ID, TEST_EMPLOYEE_ID, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(existingEmployee.getLastName()).isEqualTo("Smith");
            assertThat(existingEmployee.getDepartment()).isEqualTo("Product");
            assertThat(existingEmployee.getRole()).isEqualTo("Senior Engineer");
            assertThat(existingEmployee.getSpendingLimit()).isEqualByComparingTo(BigDecimal.valueOf(10000));
            assertThat(existingEmployee.getUpdatedAt()).isNotNull();

            verify(employeeRepository).save(existingEmployee);
        }

        @Test
        @DisplayName("Should get employees with filters")
        void shouldGetEmployeesWithFilters() {
            // Arrange
            EmployeeFilter filter = EmployeeFilter.builder()
                    .department("Engineering")
                    .status(EmployeeStatus.ACTIVE)
                    .build();

            Pageable pageable = PageRequest.of(0, 20);

            List<BusinessEmployee> employees = Arrays.asList(
                    createTestEmployee("John", "Doe", "john@test.com"),
                    createTestEmployee("Jane", "Smith", "jane@test.com")
            );

            Page<BusinessEmployee> employeePage = new PageImpl<>(employees, pageable, employees.size());

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(employeeRepository.findByFilters(eq(TEST_ACCOUNT_ID), eq("Engineering"),
                    isNull(), eq(EmployeeStatus.ACTIVE), eq(pageable))).thenReturn(employeePage);

            // Act
            Page<BusinessEmployeeResponse> response = businessAccountService.getEmployees(
                    TEST_ACCOUNT_ID, filter, pageable);

            // Assert
            assertThat(response.getContent()).hasSize(2);
            assertThat(response.getContent()).extracting(BusinessEmployeeResponse::getFirstName)
                    .containsExactly("John", "Jane");

            verify(employeeRepository).findByFilters(eq(TEST_ACCOUNT_ID), eq("Engineering"),
                    isNull(), eq(EmployeeStatus.ACTIVE), eq(pageable));
        }
    }

    // ===========================
    // Expense Management Tests
    // ===========================

    @Nested
    @DisplayName("Expense Management Tests")
    class ExpenseManagementTests {

        @Test
        @DisplayName("Should create expense successfully")
        void shouldCreateExpense() {
            // Arrange
            CreateExpenseRequest request = CreateExpenseRequest.builder()
                    .employeeId(TEST_EMPLOYEE_ID)
                    .category("TRAVEL")
                    .description("Business trip to NYC")
                    .amount(BigDecimal.valueOf(1500.00))
                    .currency("USD")
                    .expenseDate(LocalDateTime.now())
                    .merchant("United Airlines")
                    .receiptUrl("https://receipts.com/abc123")
                    .build();

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));

            ArgumentCaptor<BusinessExpense> expenseCaptor = ArgumentCaptor.forClass(BusinessExpense.class);
            when(expenseRepository.save(expenseCaptor.capture())).thenAnswer(i -> {
                BusinessExpense saved = i.getArgument(0);
                saved.setId(TEST_EXPENSE_ID);
                return saved;
            });

            // Act
            ExpenseResponse response = businessAccountService.createExpense(TEST_ACCOUNT_ID, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getExpenseId()).isEqualTo(TEST_EXPENSE_ID);
            assertThat(response.getCategory()).isEqualTo("TRAVEL");
            assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1500.00));

            BusinessExpense savedExpense = expenseCaptor.getValue();
            assertThat(savedExpense.getStatus()).isEqualTo(ExpenseStatus.PENDING);
            assertThat(savedExpense.getSubmittedBy()).isEqualTo(TEST_USER_ID);
            assertThat(savedExpense.getSubmittedAt()).isNotNull();

            verify(expenseRepository).save(any(BusinessExpense.class));
        }

        @Test
        @DisplayName("Should approve expense successfully")
        void shouldApproveExpense() {
            // Arrange
            BusinessExpense pendingExpense = createTestExpense(BigDecimal.valueOf(500), ExpenseStatus.PENDING);

            ApproveExpenseRequest request = ApproveExpenseRequest.builder()
                    .approved(true)
                    .notes("Approved - valid business expense")
                    .build();

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(expenseRepository.findByIdAndAccountId(TEST_EXPENSE_ID, TEST_ACCOUNT_ID))
                    .thenReturn(Optional.of(pendingExpense));
            when(expenseRepository.save(any(BusinessExpense.class))).thenReturn(pendingExpense);

            // Act
            ExpenseResponse response = businessAccountService.approveExpense(
                    TEST_ACCOUNT_ID, TEST_EXPENSE_ID, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(pendingExpense.getStatus()).isEqualTo(ExpenseStatus.APPROVED);
            assertThat(pendingExpense.getApprovalNotes()).isEqualTo("Approved - valid business expense");
            assertThat(pendingExpense.getApprovedBy()).isEqualTo(TEST_USER_ID);
            assertThat(pendingExpense.getApprovedAt()).isNotNull();

            verify(expenseRepository).save(pendingExpense);
        }

        @Test
        @DisplayName("Should reject expense successfully")
        void shouldRejectExpense() {
            // Arrange
            BusinessExpense pendingExpense = createTestExpense(BigDecimal.valueOf(500), ExpenseStatus.PENDING);

            ApproveExpenseRequest request = ApproveExpenseRequest.builder()
                    .approved(false)
                    .notes("Insufficient documentation")
                    .build();

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(expenseRepository.findByIdAndAccountId(TEST_EXPENSE_ID, TEST_ACCOUNT_ID))
                    .thenReturn(Optional.of(pendingExpense));
            when(expenseRepository.save(any(BusinessExpense.class))).thenReturn(pendingExpense);

            // Act
            ExpenseResponse response = businessAccountService.approveExpense(
                    TEST_ACCOUNT_ID, TEST_EXPENSE_ID, request);

            // Assert
            assertThat(pendingExpense.getStatus()).isEqualTo(ExpenseStatus.REJECTED);
            assertThat(pendingExpense.getApprovalNotes()).isEqualTo("Insufficient documentation");

            verify(expenseRepository).save(pendingExpense);
        }

        @Test
        @DisplayName("Should reject approval of non-pending expense")
        void shouldRejectApprovalOfNonPendingExpense() {
            // Arrange
            BusinessExpense approvedExpense = createTestExpense(BigDecimal.valueOf(500), ExpenseStatus.APPROVED);

            ApproveExpenseRequest request = ApproveExpenseRequest.builder()
                    .approved(true)
                    .build();

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(expenseRepository.findByIdAndAccountId(TEST_EXPENSE_ID, TEST_ACCOUNT_ID))
                    .thenReturn(Optional.of(approvedExpense));

            // Act & Assert
            assertThatThrownBy(() -> businessAccountService.approveExpense(
                    TEST_ACCOUNT_ID, TEST_EXPENSE_ID, request))
                    .isInstanceOf(InvalidExpenseStatusException.class)
                    .hasMessageContaining("not in pending status");

            verify(expenseRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should get expenses with filters")
        void shouldGetExpensesWithFilters() {
            // Arrange
            ExpenseFilter filter = ExpenseFilter.builder()
                    .category("TRAVEL")
                    .status(ExpenseStatus.APPROVED)
                    .startDate(LocalDate.now().minusMonths(1))
                    .endDate(LocalDate.now())
                    .build();

            Pageable pageable = PageRequest.of(0, 20);

            List<BusinessExpense> expenses = Arrays.asList(
                    createTestExpense(BigDecimal.valueOf(500), ExpenseStatus.APPROVED),
                    createTestExpense(BigDecimal.valueOf(750), ExpenseStatus.APPROVED)
            );

            Page<BusinessExpense> expensePage = new PageImpl<>(expenses, pageable, expenses.size());

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(expenseRepository.findByFilters(eq(TEST_ACCOUNT_ID), eq("TRAVEL"),
                    eq(ExpenseStatus.APPROVED), isNull(), any(), any(), eq(pageable)))
                    .thenReturn(expensePage);

            // Act
            Page<ExpenseResponse> response = businessAccountService.getExpenses(
                    TEST_ACCOUNT_ID, filter, pageable);

            // Assert
            assertThat(response.getContent()).hasSize(2);
            assertThat(response.getContent()).extracting(ExpenseResponse::getAmount)
                    .contains(BigDecimal.valueOf(500), BigDecimal.valueOf(750));
        }
    }

    // ===========================
    // Invoice Management Tests
    // ===========================

    @Nested
    @DisplayName("Invoice Management Tests")
    class InvoiceManagementTests {

        @Test
        @DisplayName("Should create invoice with generated invoice number")
        void shouldCreateInvoiceWithGeneratedNumber() {
            // Arrange
            CreateInvoiceRequest request = CreateInvoiceRequest.builder()
                    .customerName("Acme Corp")
                    .customerEmail("billing@acme.com")
                    .customerAddress("123 Acme St")
                    .items(List.of("Consulting Services - 40 hours @ $150/hr"))
                    .subtotal(BigDecimal.valueOf(6000.00))
                    .taxAmount(BigDecimal.valueOf(480.00))
                    .totalAmount(BigDecimal.valueOf(6480.00))
                    .currency("USD")
                    .dueDate(LocalDate.now().plusDays(30))
                    .notes("Payment due within 30 days")
                    .build();

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(invoiceRepository.countByAccountId(TEST_ACCOUNT_ID)).thenReturn(42L);

            ArgumentCaptor<BusinessInvoice> invoiceCaptor = ArgumentCaptor.forClass(BusinessInvoice.class);
            when(invoiceRepository.save(invoiceCaptor.capture())).thenAnswer(i -> {
                BusinessInvoice saved = i.getArgument(0);
                saved.setId(TEST_INVOICE_ID);
                return saved;
            });

            // Act
            InvoiceResponse response = businessAccountService.createInvoice(TEST_ACCOUNT_ID, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getInvoiceId()).isEqualTo(TEST_INVOICE_ID);

            BusinessInvoice savedInvoice = invoiceCaptor.getValue();
            assertThat(savedInvoice.getInvoiceNumber()).matches("INV-[A-Z0-9]{8}-\\d{5}");
            assertThat(savedInvoice.getInvoiceNumber()).contains("00043"); // Count + 1
            assertThat(savedInvoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
            assertThat(savedInvoice.getCustomerName()).isEqualTo("Acme Corp");
            assertThat(savedInvoice.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(6480.00));

            verify(invoiceRepository).countByAccountId(TEST_ACCOUNT_ID);
            verify(invoiceRepository).save(any(BusinessInvoice.class));
        }

        @Test
        @DisplayName("Should get invoices with filters")
        void shouldGetInvoicesWithFilters() {
            // Arrange
            InvoiceFilter filter = InvoiceFilter.builder()
                    .status(InvoiceStatus.SENT)
                    .customerName("Acme")
                    .build();

            Pageable pageable = PageRequest.of(0, 20);

            List<BusinessInvoice> invoices = Arrays.asList(
                    createTestInvoice("Acme Corp", InvoiceStatus.SENT),
                    createTestInvoice("Acme Industries", InvoiceStatus.SENT)
            );

            Page<BusinessInvoice> invoicePage = new PageImpl<>(invoices, pageable, invoices.size());

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(invoiceRepository.findByFilters(TEST_ACCOUNT_ID, InvoiceStatus.SENT, "Acme", pageable))
                    .thenReturn(invoicePage);

            // Act
            Page<InvoiceResponse> response = businessAccountService.getInvoices(
                    TEST_ACCOUNT_ID, filter, pageable);

            // Assert
            assertThat(response.getContent()).hasSize(2);
            assertThat(response.getContent()).extracting(InvoiceResponse::getCustomerName)
                    .contains("Acme Corp", "Acme Industries");
        }

        @Test
        @DisplayName("Should send invoice and update status")
        void shouldSendInvoiceSuccessfully() {
            // Arrange
            BusinessInvoice draftInvoice = createTestInvoice("Test Customer", InvoiceStatus.DRAFT);

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(invoiceRepository.findByIdAndAccountId(TEST_INVOICE_ID, TEST_ACCOUNT_ID))
                    .thenReturn(Optional.of(draftInvoice));
            when(invoiceRepository.save(any(BusinessInvoice.class))).thenReturn(draftInvoice);

            // Act
            businessAccountService.sendInvoice(TEST_ACCOUNT_ID, TEST_INVOICE_ID);

            // Assert
            assertThat(draftInvoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
            assertThat(draftInvoice.getSentAt()).isNotNull();

            verify(invoiceRepository, times(1)).save(draftInvoice);
        }

        @Test
        @DisplayName("Should reject sending invoice that's already sent")
        void shouldRejectSendingAlreadySentInvoice() {
            // Arrange
            BusinessInvoice sentInvoice = createTestInvoice("Test Customer", InvoiceStatus.SENT);
            sentInvoice.setSentAt(LocalDateTime.now().minusDays(1));

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(invoiceRepository.findByIdAndAccountId(TEST_INVOICE_ID, TEST_ACCOUNT_ID))
                    .thenReturn(Optional.of(sentInvoice));

            // Act & Assert
            assertThatThrownBy(() -> businessAccountService.sendInvoice(TEST_ACCOUNT_ID, TEST_INVOICE_ID))
                    .isInstanceOf(InvalidInvoiceStatusException.class)
                    .hasMessageContaining("Invoice has already been sent");

            verify(invoiceRepository, never()).save(any());
        }
    }

    // ===========================
    // Tax and Compliance Tests
    // ===========================

    @Nested
    @DisplayName("Tax and Compliance Tests")
    class TaxAndComplianceTests {

        @Test
        @DisplayName("Should generate tax report with correct calculations")
        void shouldGenerateTaxReport() {
            // Arrange
            GenerateTaxReportRequest request = GenerateTaxReportRequest.builder()
                    .startDate(LocalDate.of(2024, 1, 1))
                    .endDate(LocalDate.of(2024, 12, 31))
                    .build();

            List<BusinessExpense> expenses = Arrays.asList(
                    createTestExpense(BigDecimal.valueOf(1000), ExpenseStatus.APPROVED),
                    createTestExpense(BigDecimal.valueOf(2000), ExpenseStatus.APPROVED),
                    createTestExpense(BigDecimal.valueOf(500), ExpenseStatus.PENDING) // Should be excluded
            );

            List<BusinessInvoice> invoices = Arrays.asList(
                    createTestInvoice("Customer 1", InvoiceStatus.PAID, BigDecimal.valueOf(5000)),
                    createTestInvoice("Customer 2", InvoiceStatus.PAID, BigDecimal.valueOf(3000)),
                    createTestInvoice("Customer 3", InvoiceStatus.SENT, BigDecimal.valueOf(2000)) // Should be excluded
            );

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(expenseRepository.findByAccountIdAndExpenseDateBetween(
                    eq(TEST_ACCOUNT_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(expenses);
            when(invoiceRepository.findByAccountIdAndCreatedAtBetween(
                    eq(TEST_ACCOUNT_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(invoices);

            // Act
            TaxReportResponse response = businessAccountService.generateTaxReport(TEST_ACCOUNT_ID, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getTotalExpenses()).isEqualByComparingTo(BigDecimal.valueOf(3000)); // 1000 + 2000
            assertThat(response.getTotalRevenue()).isEqualByComparingTo(BigDecimal.valueOf(8000)); // 5000 + 3000
            assertThat(response.getNetIncome()).isEqualByComparingTo(BigDecimal.valueOf(5000)); // 8000 - 3000
            assertThat(response.getReportPeriod()).contains("2024-01-01").contains("2024-12-31");
            assertThat(response.getGeneratedAt()).isNotNull();

            verify(expenseRepository).findByAccountIdAndExpenseDateBetween(
                    eq(TEST_ACCOUNT_ID), any(LocalDateTime.class), any(LocalDateTime.class));
            verify(invoiceRepository).findByAccountIdAndCreatedAtBetween(
                    eq(TEST_ACCOUNT_ID), any(LocalDateTime.class), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("Should get tax documents for specific year")
        void shouldGetTaxDocuments() {
            // Arrange
            List<BusinessTaxDocument> documents = Arrays.asList(
                    createTestTaxDocument("1099-K", 2024),
                    createTestTaxDocument("W-9", 2024)
            );

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(taxDocumentRepository.findByAccountIdAndYear(TEST_ACCOUNT_ID, 2024))
                    .thenReturn(documents);

            // Act
            List<TaxDocumentResponse> responses = businessAccountService.getTaxDocuments(TEST_ACCOUNT_ID, 2024);

            // Assert
            assertThat(responses).hasSize(2);
            assertThat(responses).extracting(TaxDocumentResponse::getDocumentType)
                    .containsExactly("1099-K", "W-9");

            verify(taxDocumentRepository).findByAccountIdAndYear(TEST_ACCOUNT_ID, 2024);
        }
    }

    // ===========================
    // Settings Management Tests
    // ===========================

    @Nested
    @DisplayName("Settings Management Tests")
    class SettingsManagementTests {

        @Test
        @DisplayName("Should get business settings")
        void shouldGetBusinessSettings() {
            // Arrange
            testAccount.setAutoApprovalLimit(BigDecimal.valueOf(500));
            testAccount.setRequireReceiptForExpenses(true);
            testAccount.setAllowEmployeeCardRequests(false);

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));

            // Act
            BusinessSettingsResponse response = businessAccountService.getBusinessSettings(TEST_ACCOUNT_ID);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getMonthlyTransactionLimit()).isEqualByComparingTo(BigDecimal.valueOf(100000));
            assertThat(response.getAutoApprovalLimit()).isEqualByComparingTo(BigDecimal.valueOf(500));
            assertThat(response.isRequireReceiptForExpenses()).isTrue();
            assertThat(response.isAllowEmployeeCardRequests()).isFalse();
        }

        @Test
        @DisplayName("Should update business settings")
        void shouldUpdateBusinessSettings() {
            // Arrange
            UpdateBusinessSettingsRequest request = UpdateBusinessSettingsRequest.builder()
                    .monthlyTransactionLimit(BigDecimal.valueOf(200000))
                    .autoApprovalLimit(BigDecimal.valueOf(1000))
                    .requireReceiptForExpenses(false)
                    .allowEmployeeCardRequests(true)
                    .build();

            when(businessAccountRepository.findById(TEST_ACCOUNT_ID)).thenReturn(Optional.of(testAccount));
            when(businessAccountRepository.save(any(BusinessAccount.class))).thenReturn(testAccount);

            // Act
            BusinessSettingsResponse response = businessAccountService.updateBusinessSettings(
                    TEST_ACCOUNT_ID, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(testAccount.getMonthlyTransactionLimit()).isEqualByComparingTo(BigDecimal.valueOf(200000));
            assertThat(testAccount.getAutoApprovalLimit()).isEqualByComparingTo(BigDecimal.valueOf(1000));
            assertThat(testAccount.isRequireReceiptForExpenses()).isFalse();
            assertThat(testAccount.isAllowEmployeeCardRequests()).isTrue();
            assertThat(testAccount.getUpdatedAt()).isNotNull();

            verify(businessAccountRepository).save(testAccount);
        }
    }

    // ===========================
    // Helper Methods
    // ===========================

    private BusinessSubAccount createTestSubAccount(String name, String type) {
        return BusinessSubAccount.builder()
                .id(UUID.randomUUID())
                .mainAccountId(TEST_ACCOUNT_ID)
                .accountName(name)
                .accountType(type)
                .purpose("Test purpose")
                .spendingLimit(BigDecimal.valueOf(10000))
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private BusinessEmployee createTestEmployee(String firstName, String lastName, String email) {
        return BusinessEmployee.builder()
                .id(TEST_EMPLOYEE_ID)
                .accountId(TEST_ACCOUNT_ID)
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .phoneNumber("+1234567890")
                .department("Engineering")
                .role("Engineer")
                .spendingLimit(BigDecimal.valueOf(5000))
                .status(EmployeeStatus.ACTIVE)
                .hireDate(LocalDate.now())
                .permissions(List.of("EXPENSE_SUBMIT"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private BusinessExpense createTestExpense(BigDecimal amount, ExpenseStatus status) {
        return BusinessExpense.builder()
                .id(TEST_EXPENSE_ID)
                .accountId(TEST_ACCOUNT_ID)
                .employeeId(TEST_EMPLOYEE_ID)
                .category("TRAVEL")
                .description("Test expense")
                .amount(amount)
                .currency("USD")
                .expenseDate(LocalDateTime.now())
                .merchant("Test Merchant")
                .status(status)
                .submittedBy(TEST_USER_ID)
                .submittedAt(LocalDateTime.now())
                .build();
    }

    private BusinessInvoice createTestInvoice(String customerName, InvoiceStatus status) {
        return createTestInvoice(customerName, status, BigDecimal.valueOf(1000.00));
    }

    private BusinessInvoice createTestInvoice(String customerName, InvoiceStatus status, BigDecimal totalAmount) {
        return BusinessInvoice.builder()
                .id(TEST_INVOICE_ID)
                .accountId(TEST_ACCOUNT_ID)
                .invoiceNumber("INV-TEST-00001")
                .customerName(customerName)
                .customerEmail(customerName.toLowerCase().replace(" ", "") + "@test.com")
                .customerAddress("123 Test St")
                .items(List.of("Service - $" + totalAmount))
                .subtotal(totalAmount)
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(totalAmount)
                .currency("USD")
                .dueDate(LocalDate.now().plusDays(30))
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private BusinessTaxDocument createTestTaxDocument(String documentType, Integer year) {
        return BusinessTaxDocument.builder()
                .id(UUID.randomUUID())
                .accountId(TEST_ACCOUNT_ID)
                .documentType(documentType)
                .year(year)
                .fileUrl("https://documents.test.com/" + documentType.toLowerCase() + "-" + year + ".pdf")
                .generatedAt(LocalDateTime.now())
                .build();
    }
}
