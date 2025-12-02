package com.waqiti.business.service;

import com.waqiti.business.domain.*;
import com.waqiti.business.dto.*;
import com.waqiti.business.exception.BusinessExceptions.*;
import com.waqiti.business.repository.*;
import com.waqiti.common.security.AuthenticationFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BusinessAccountService {

    private final BusinessAccountRepository businessAccountRepository;
    private final BusinessSubAccountRepository subAccountRepository;
    private final BusinessEmployeeRepository employeeRepository;
    private final BusinessExpenseRepository expenseRepository;
    private final BusinessInvoiceRepository invoiceRepository;
    private final BusinessTaxDocumentRepository taxDocumentRepository;
    private final AuthenticationFacade authenticationFacade;
    private final EmailService emailService;

    // Account Management
    public BusinessOnboardingResponse onboardBusiness(BusinessOnboardingRequest request) {
        log.info("Starting business onboarding for: {}", request.getBusinessName());
        
        String currentUserId = authenticationFacade.getCurrentUserId();
        
        // Check if user already has a business account
        if (businessAccountRepository.existsByOwnerId(UUID.fromString(currentUserId))) {
            throw new DuplicateBusinessAccountException("User already has a business account");
        }
        
        BusinessAccount businessAccount = BusinessAccount.builder()
                .ownerId(UUID.fromString(currentUserId))
                .businessName(request.getBusinessName())
                .businessType(request.getBusinessType())
                .industry(request.getIndustry())
                .registrationNumber(request.getRegistrationNumber())
                .taxId(request.getTaxId())
                .address(request.getAddress())
                .phoneNumber(request.getPhoneNumber())
                .email(request.getEmail())
                .website(request.getWebsite())
                .status(BusinessAccountStatus.PENDING_VERIFICATION)
                .monthlyTransactionLimit(BigDecimal.valueOf(100000)) // Default limit
                .riskLevel(RiskLevel.LOW)
                .createdAt(LocalDateTime.now())
                .build();
        
        businessAccount = businessAccountRepository.save(businessAccount);
        
        log.info("Business account created with ID: {}", businessAccount.getId());
        
        return BusinessOnboardingResponse.builder()
                .accountId(businessAccount.getId())
                .businessName(businessAccount.getBusinessName())
                .status(businessAccount.getStatus().toString())
                .verificationRequired(true)
                .estimatedVerificationTime("2-3 business days")
                .build();
    }

    @Transactional(readOnly = true)
    public BusinessProfileResponse getBusinessProfile(UUID accountId) {
        BusinessAccount account = findBusinessAccountById(accountId);
        validateOwnership(account);
        
        return mapToProfileResponse(account);
    }

    public BusinessProfileResponse updateBusinessProfile(UUID accountId, UpdateBusinessProfileRequest request) {
        BusinessAccount account = findBusinessAccountById(accountId);
        validateOwnership(account);
        
        account.setBusinessName(request.getBusinessName());
        account.setIndustry(request.getIndustry());
        account.setAddress(request.getAddress());
        account.setPhoneNumber(request.getPhoneNumber());
        account.setEmail(request.getEmail());
        account.setWebsite(request.getWebsite());
        account.setUpdatedAt(LocalDateTime.now());
        
        account = businessAccountRepository.save(account);
        
        return mapToProfileResponse(account);
    }

    // Sub-account Management
    public SubAccountResponse createSubAccount(UUID accountId, CreateSubAccountRequest request) {
        BusinessAccount mainAccount = findBusinessAccountById(accountId);
        validateOwnership(mainAccount);
        
        if (subAccountRepository.countByMainAccountId(accountId) >= 10) {
            throw new SubAccountLimitExceededException("Maximum number of sub-accounts reached");
        }
        
        BusinessSubAccount subAccount = BusinessSubAccount.builder()
                .mainAccountId(accountId)
                .accountName(request.getAccountName())
                .accountType(request.getAccountType())
                .purpose(request.getPurpose())
                .spendingLimit(request.getSpendingLimit())
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
        
        subAccount = subAccountRepository.save(subAccount);
        
        return mapToSubAccountResponse(subAccount);
    }

    @Transactional(readOnly = true)
    public List<SubAccountResponse> getSubAccounts(UUID accountId) {
        BusinessAccount account = findBusinessAccountById(accountId);
        validateOwnership(account);
        
        List<BusinessSubAccount> subAccounts = subAccountRepository.findByMainAccountIdAndIsActiveTrue(accountId);
        
        return subAccounts.stream()
                .map(this::mapToSubAccountResponse)
                .collect(Collectors.toList());
    }

    // Employee Management
    public BusinessEmployeeResponse addEmployee(UUID accountId, AddEmployeeRequest request) {
        BusinessAccount account = findBusinessAccountById(accountId);
        validateOwnership(account);
        
        if (employeeRepository.existsByAccountIdAndEmail(accountId, request.getEmail())) {
            throw new DuplicateEmployeeException("Employee with this email already exists");
        }
        
        BusinessEmployee employee = BusinessEmployee.builder()
                .accountId(accountId)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .department(request.getDepartment())
                .role(request.getRole())
                .spendingLimit(request.getSpendingLimit())
                .status(EmployeeStatus.ACTIVE)
                .hireDate(request.getHireDate())
                .permissions(request.getPermissions())
                .createdAt(LocalDateTime.now())
                .build();
        
        employee = employeeRepository.save(employee);
        
        return mapToEmployeeResponse(employee);
    }

    @Transactional(readOnly = true)
    public Page<BusinessEmployeeResponse> getEmployees(UUID accountId, EmployeeFilter filter, Pageable pageable) {
        BusinessAccount account = findBusinessAccountById(accountId);
        validateOwnership(account);
        
        Page<BusinessEmployee> employees = employeeRepository.findByFilters(
                accountId, filter.getDepartment(), filter.getRole(), filter.getStatus(), pageable);
        
        return employees.map(this::mapToEmployeeResponse);
    }

    public BusinessEmployeeResponse updateEmployee(UUID accountId, UUID employeeId, UpdateEmployeeRequest request) {
        BusinessAccount account = findBusinessAccountById(accountId);
        validateOwnership(account);
        
        BusinessEmployee employee = employeeRepository.findByIdAndAccountId(employeeId, accountId)
                .orElseThrow(() -> new EmployeeNotFoundException("Employee not found"));
        
        employee.setFirstName(request.getFirstName());
        employee.setLastName(request.getLastName());
        employee.setPhoneNumber(request.getPhoneNumber());
        employee.setDepartment(request.getDepartment());
        employee.setRole(request.getRole());
        employee.setSpendingLimit(request.getSpendingLimit());
        employee.setPermissions(request.getPermissions());
        employee.setUpdatedAt(LocalDateTime.now());
        
        employee = employeeRepository.save(employee);
        
        return mapToEmployeeResponse(employee);
    }

    // Expense Management
    public ExpenseResponse createExpense(UUID accountId, CreateExpenseRequest request) {
        BusinessAccount account = findBusinessAccountById(accountId);
        String currentUserId = authenticationFacade.getCurrentUserId();
        
        BusinessExpense expense = BusinessExpense.builder()
                .accountId(accountId)
                .employeeId(request.getEmployeeId())
                .category(request.getCategory())
                .description(request.getDescription())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .expenseDate(request.getExpenseDate())
                .merchant(request.getMerchant())
                .receiptUrl(request.getReceiptUrl())
                .status(ExpenseStatus.PENDING)
                .submittedBy(UUID.fromString(currentUserId))
                .submittedAt(LocalDateTime.now())
                .build();
        
        expense = expenseRepository.save(expense);
        
        return mapToExpenseResponse(expense);
    }

    @Transactional(readOnly = true)
    public Page<ExpenseResponse> getExpenses(UUID accountId, ExpenseFilter filter, Pageable pageable) {
        BusinessAccount account = findBusinessAccountById(accountId);
        validateOwnership(account);
        
        Page<BusinessExpense> expenses = expenseRepository.findByFilters(
                accountId, filter.getCategory(), filter.getStatus(), 
                filter.getEmployeeId(), filter.getStartDate(), filter.getEndDate(), pageable);
        
        return expenses.map(this::mapToExpenseResponse);
    }

    public ExpenseResponse approveExpense(UUID accountId, UUID expenseId, ApproveExpenseRequest request) {
        BusinessAccount account = findBusinessAccountById(accountId);
        validateOwnership(account);
        
        BusinessExpense expense = expenseRepository.findByIdAndAccountId(expenseId, accountId)
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found"));
        
        if (expense.getStatus() != ExpenseStatus.PENDING) {
            throw new InvalidExpenseStatusException("Expense is not in pending status");
        }
        
        expense.setStatus(request.isApproved() ? ExpenseStatus.APPROVED : ExpenseStatus.REJECTED);
        expense.setApprovalNotes(request.getNotes());
        expense.setApprovedBy(UUID.fromString(authenticationFacade.getCurrentUserId()));
        expense.setApprovedAt(LocalDateTime.now());
        
        expense = expenseRepository.save(expense);
        
        return mapToExpenseResponse(expense);
    }

    // Invoice Management
    public InvoiceResponse createInvoice(UUID accountId, CreateInvoiceRequest request) {
        BusinessAccount account = findBusinessAccountById(accountId);
        validateOwnership(account);
        
        String invoiceNumber = generateInvoiceNumber(accountId);
        
        BusinessInvoice invoice = BusinessInvoice.builder()
                .accountId(accountId)
                .invoiceNumber(invoiceNumber)
                .customerName(request.getCustomerName())
                .customerEmail(request.getCustomerEmail())
                .customerAddress(request.getCustomerAddress())
                .items(request.getItems())
                .subtotal(request.getSubtotal())
                .taxAmount(request.getTaxAmount())
                .totalAmount(request.getTotalAmount())
                .currency(request.getCurrency())
                .dueDate(request.getDueDate())
                .notes(request.getNotes())
                .status(InvoiceStatus.DRAFT)
                .createdAt(LocalDateTime.now())
                .build();
        
        invoice = invoiceRepository.save(invoice);
        
        return mapToInvoiceResponse(invoice);
    }

    @Transactional(readOnly = true)
    public Page<InvoiceResponse> getInvoices(UUID accountId, InvoiceFilter filter, Pageable pageable) {
        BusinessAccount account = findBusinessAccountById(accountId);
        validateOwnership(account);
        
        Page<BusinessInvoice> invoices = invoiceRepository.findByFilters(
                accountId, filter.getStatus(), filter.getCustomerName(), pageable);
        
        return invoices.map(this::mapToInvoiceResponse);
    }

    public void sendInvoice(UUID accountId, UUID invoiceId) {
        BusinessAccount account = findBusinessAccountById(accountId);
        validateOwnership(account);
        
        BusinessInvoice invoice = invoiceRepository.findByIdAndAccountId(invoiceId, accountId)
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found"));
        
        if (invoice.getStatus() == InvoiceStatus.SENT || invoice.getStatus() == InvoiceStatus.PAID) {
            throw new InvalidInvoiceStatusException("Invoice has already been sent");
        }
        
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setSentAt(LocalDateTime.now());
        invoiceRepository.save(invoice);
        
        // Send invoice via email service
        try {
            sendInvoiceEmail(invoice);
            log.info("Invoice {} sent successfully to customer {}", 
                    invoice.getInvoiceNumber(), invoice.getCustomerEmail());
        } catch (Exception e) {
            log.error("Failed to send invoice email for invoice: {}", invoice.getInvoiceNumber(), e);
            // Revert status if email fails
            invoice.setStatus(InvoiceStatus.PENDING);
            invoice.setSentAt(null);
            invoiceRepository.save(invoice);
            throw new BusinessServiceException("Failed to send invoice email: " + e.getMessage());
        }
    }

    private void sendInvoiceEmail(BusinessInvoice invoice) {
        try {
            // Get business account for sender details
            BusinessAccount account = businessAccountRepository.findById(invoice.getAccountId())
                    .orElseThrow(() -> new BusinessAccountNotFoundException("Account not found"));

            // Create email content
            String subject = String.format("Invoice %s from %s",
                    invoice.getInvoiceNumber(),
                    account.getBusinessName());

            StringBuilder htmlContent = new StringBuilder();
            htmlContent.append("<!DOCTYPE html>");
            htmlContent.append("<html><head><style>");
            htmlContent.append("body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }");
            htmlContent.append("h2 { color: #2c3e50; }");
            htmlContent.append("table { width: 100%; border-collapse: collapse; margin: 20px 0; }");
            htmlContent.append("th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }");
            htmlContent.append("th { background-color: #f8f9fa; font-weight: bold; }");
            htmlContent.append(".total { font-size: 1.2em; font-weight: bold; color: #2c3e50; }");
            htmlContent.append("</style></head><body>");

            htmlContent.append("<h2>Invoice from ").append(account.getBusinessName()).append("</h2>");
            htmlContent.append("<p><strong>Invoice Number:</strong> ").append(invoice.getInvoiceNumber()).append("</p>");
            htmlContent.append("<p><strong>Customer:</strong> ").append(invoice.getCustomerName()).append("</p>");
            htmlContent.append("<p><strong>Due Date:</strong> ").append(invoice.getDueDate()).append("</p>");

            if (invoice.getItems() != null && !invoice.getItems().isEmpty()) {
                htmlContent.append("<h3>Invoice Items</h3>");
                htmlContent.append("<table>");
                htmlContent.append("<thead><tr><th>Description</th><th>Amount</th></tr></thead>");
                htmlContent.append("<tbody>");

                for (Object item : invoice.getItems()) {
                    htmlContent.append("<tr>");
                    htmlContent.append("<td>").append(item.toString()).append("</td>");
                    htmlContent.append("</tr>");
                }

                htmlContent.append("</tbody></table>");
            }

            htmlContent.append("<p><strong>Subtotal:</strong> ").append(invoice.getSubtotal()).append(" ").append(invoice.getCurrency()).append("</p>");
            htmlContent.append("<p><strong>Tax:</strong> ").append(invoice.getTaxAmount()).append(" ").append(invoice.getCurrency()).append("</p>");
            htmlContent.append("<p class='total'><strong>Total Amount:</strong> ").append(invoice.getTotalAmount()).append(" ").append(invoice.getCurrency()).append("</p>");

            if (invoice.getNotes() != null && !invoice.getNotes().trim().isEmpty()) {
                htmlContent.append("<p><strong>Notes:</strong> ").append(invoice.getNotes()).append("</p>");
            }

            htmlContent.append("<hr>");
            htmlContent.append("<p>Please remit payment by the due date.</p>");
            htmlContent.append("<p>Thank you for your business!</p>");
            htmlContent.append("<p style='color: #888; font-size: 0.9em;'>").append(account.getBusinessName());
            if (account.getAddress() != null) {
                htmlContent.append("<br>").append(account.getAddress());
            }
            if (account.getPhoneNumber() != null) {
                htmlContent.append("<br>Phone: ").append(account.getPhoneNumber());
            }
            htmlContent.append("</p>");
            htmlContent.append("</body></html>");

            // Queue email for reliable delivery using Outbox pattern
            EmailService.EmailRequest emailRequest = EmailService.EmailRequest.builder()
                    .recipientEmail(invoice.getCustomerEmail())
                    .recipientName(invoice.getCustomerName())
                    .senderEmail(account.getEmail())
                    .senderName(account.getBusinessName())
                    .subject(subject)
                    .htmlContent(htmlContent.toString())
                    .emailType(com.waqiti.business.domain.EmailOutbox.EmailType.INVOICE)
                    .priority(3) // High priority for invoices
                    .build();

            UUID emailId = emailService.queueEmail(emailRequest);

            log.info("Invoice email queued successfully: {} for invoice: {} to: {}",
                    emailId, invoice.getInvoiceNumber(), invoice.getCustomerEmail());

        } catch (Exception e) {
            log.error("Failed to queue invoice email for invoice: {}", invoice.getInvoiceNumber(), e);
            throw new RuntimeException("Failed to send invoice email: " + e.getMessage(), e);
        }
    }

    // Tax and Compliance
    @Transactional(readOnly = true)
    public List<TaxDocumentResponse> getTaxDocuments(UUID accountId, Integer year) {
        BusinessAccount account = findBusinessAccountById(accountId);
        validateOwnership(account);
        
        List<BusinessTaxDocument> documents = taxDocumentRepository.findByAccountIdAndYear(accountId, year);
        
        return documents.stream()
                .map(this::mapToTaxDocumentResponse)
                .collect(Collectors.toList());
    }

    public TaxReportResponse generateTaxReport(UUID accountId, GenerateTaxReportRequest request) {
        BusinessAccount account = findBusinessAccountById(accountId);
        validateOwnership(account);
        
        // Generate tax report based on expenses and invoices
        List<BusinessExpense> expenses = expenseRepository.findByAccountIdAndExpenseDateBetween(
                accountId, request.getStartDate().atStartOfDay(), request.getEndDate().atTime(23, 59, 59));
        
        List<BusinessInvoice> invoices = invoiceRepository.findByAccountIdAndCreatedAtBetween(
                accountId, request.getStartDate().atStartOfDay(), request.getEndDate().atTime(23, 59, 59));
        
        BigDecimal totalExpenses = expenses.stream()
                .filter(e -> e.getStatus() == ExpenseStatus.APPROVED)
                .map(BusinessExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalRevenue = invoices.stream()
                .filter(i -> i.getStatus() == InvoiceStatus.PAID)
                .map(BusinessInvoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return TaxReportResponse.builder()
                .reportId(UUID.randomUUID())
                .accountId(accountId)
                .reportPeriod(request.getStartDate() + " to " + request.getEndDate())
                .totalRevenue(totalRevenue)
                .totalExpenses(totalExpenses)
                .netIncome(totalRevenue.subtract(totalExpenses))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // Settings Management
    @Transactional(readOnly = true)
    public BusinessSettingsResponse getBusinessSettings(UUID accountId) {
        BusinessAccount account = findBusinessAccountById(accountId);
        validateOwnership(account);
        
        return BusinessSettingsResponse.builder()
                .accountId(accountId)
                .monthlyTransactionLimit(account.getMonthlyTransactionLimit())
                .autoApprovalLimit(account.getAutoApprovalLimit())
                .requireReceiptForExpenses(account.isRequireReceiptForExpenses())
                .allowEmployeeCardRequests(account.isAllowEmployeeCardRequests())
                .build();
    }

    public BusinessSettingsResponse updateBusinessSettings(UUID accountId, UpdateBusinessSettingsRequest request) {
        BusinessAccount account = findBusinessAccountById(accountId);
        validateOwnership(account);
        
        account.setMonthlyTransactionLimit(request.getMonthlyTransactionLimit());
        account.setAutoApprovalLimit(request.getAutoApprovalLimit());
        account.setRequireReceiptForExpenses(request.isRequireReceiptForExpenses());
        account.setAllowEmployeeCardRequests(request.isAllowEmployeeCardRequests());
        account.setUpdatedAt(LocalDateTime.now());
        
        businessAccountRepository.save(account);
        
        return getBusinessSettings(accountId);
    }

    // Helper Methods
    private BusinessAccount findBusinessAccountById(UUID accountId) {
        return businessAccountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessAccountNotFoundException("Business account not found"));
    }

    private void validateOwnership(BusinessAccount account) {
        String currentUserId = authenticationFacade.getCurrentUserId();
        if (!account.getOwnerId().toString().equals(currentUserId)) {
            throw new UnauthorizedBusinessAccessException("User is not authorized to access this business account");
        }
    }

    private String generateInvoiceNumber(UUID accountId) {
        String prefix = "INV-" + accountId.toString().substring(0, 8).toUpperCase();
        long count = invoiceRepository.countByAccountId(accountId) + 1;
        return String.format("%s-%05d", prefix, count);
    }

    // Mapping Methods
    private BusinessProfileResponse mapToProfileResponse(BusinessAccount account) {
        return BusinessProfileResponse.builder()
                .accountId(account.getId())
                .businessName(account.getBusinessName())
                .businessType(account.getBusinessType())
                .industry(account.getIndustry())
                .registrationNumber(account.getRegistrationNumber())
                .taxId(account.getTaxId())
                .address(account.getAddress())
                .phoneNumber(account.getPhoneNumber())
                .email(account.getEmail())
                .website(account.getWebsite())
                .status(account.getStatus().toString())
                .createdAt(account.getCreatedAt())
                .build();
    }

    private SubAccountResponse mapToSubAccountResponse(BusinessSubAccount subAccount) {
        return SubAccountResponse.builder()
                .subAccountId(subAccount.getId())
                .accountName(subAccount.getAccountName())
                .accountType(subAccount.getAccountType())
                .purpose(subAccount.getPurpose())
                .spendingLimit(subAccount.getSpendingLimit())
                .isActive(subAccount.isActive())
                .createdAt(subAccount.getCreatedAt())
                .build();
    }

    private BusinessEmployeeResponse mapToEmployeeResponse(BusinessEmployee employee) {
        return BusinessEmployeeResponse.builder()
                .employeeId(employee.getId())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .email(employee.getEmail())
                .phoneNumber(employee.getPhoneNumber())
                .department(employee.getDepartment())
                .role(employee.getRole())
                .spendingLimit(employee.getSpendingLimit())
                .status(employee.getStatus().toString())
                .hireDate(employee.getHireDate())
                .permissions(employee.getPermissions())
                .createdAt(employee.getCreatedAt())
                .build();
    }

    private ExpenseResponse mapToExpenseResponse(BusinessExpense expense) {
        return ExpenseResponse.builder()
                .expenseId(expense.getId())
                .employeeId(expense.getEmployeeId())
                .category(expense.getCategory())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .currency(expense.getCurrency())
                .expenseDate(expense.getExpenseDate())
                .merchant(expense.getMerchant())
                .receiptUrl(expense.getReceiptUrl())
                .status(expense.getStatus().toString())
                .submittedAt(expense.getSubmittedAt())
                .approvedAt(expense.getApprovedAt())
                .approvalNotes(expense.getApprovalNotes())
                .build();
    }

    private InvoiceResponse mapToInvoiceResponse(BusinessInvoice invoice) {
        return InvoiceResponse.builder()
                .invoiceId(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .customerName(invoice.getCustomerName())
                .customerEmail(invoice.getCustomerEmail())
                .subtotal(invoice.getSubtotal())
                .taxAmount(invoice.getTaxAmount())
                .totalAmount(invoice.getTotalAmount())
                .currency(invoice.getCurrency())
                .dueDate(invoice.getDueDate())
                .status(invoice.getStatus().toString())
                .createdAt(invoice.getCreatedAt())
                .sentAt(invoice.getSentAt())
                .paidAt(invoice.getPaidAt())
                .build();
    }

    private TaxDocumentResponse mapToTaxDocumentResponse(BusinessTaxDocument document) {
        return TaxDocumentResponse.builder()
                .documentId(document.getId())
                .documentType(document.getDocumentType())
                .year(document.getYear())
                .fileUrl(document.getFileUrl())
                .generatedAt(document.getGeneratedAt())
                .build();
    }
}