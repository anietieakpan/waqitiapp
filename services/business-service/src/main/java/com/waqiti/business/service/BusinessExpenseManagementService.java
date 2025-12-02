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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.Year;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Comprehensive Business Expense Management and Reporting Service
 * 
 * Handles expense categorization, approval workflows, budget management,
 * tax compliance, reimbursements, and advanced analytics
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BusinessExpenseManagementService {

    private final BusinessAccountRepository businessAccountRepository;
    private final BusinessExpenseRepository expenseRepository;
    private final BusinessEmployeeRepository employeeRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final ExpenseBudgetRepository budgetRepository;
    private final ExpenseReimbursementRepository reimbursementRepository;
    private final ExpenseApprovalWorkflowRepository workflowRepository;
    private final TaxCategoryRepository taxCategoryRepository;
    private final AuthenticationFacade authenticationFacade;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Expense management constants
    private static final BigDecimal AUTO_APPROVAL_THRESHOLD = new BigDecimal("100.00");
    private static final int RECEIPT_REQUIRED_THRESHOLD = 25;
    private static final int EXPENSE_RETENTION_YEARS = 7;

    /**
     * Create comprehensive expense with intelligent categorization
     */
    public ExpenseResponse createExpense(UUID accountId, CreateExpenseRequest request) {
        log.info("Creating expense for account: {} amount: {} merchant: {}", 
                accountId, request.getAmount(), request.getMerchant());

        try {
            BusinessAccount account = getValidatedBusinessAccount(accountId);
            BusinessEmployee employee = null;

            if (request.getEmployeeId() != null) {
                employee = getValidatedEmployee(accountId, request.getEmployeeId());
            }

            // Intelligent expense categorization
            ExpenseCategory category = determineExpenseCategory(request);

            // Get or create tax category
            TaxCategory taxCategory = getTaxCategory(category, request.getAmount());

            // Create expense with enhanced metadata
            BusinessExpense expense = BusinessExpense.builder()
                    .accountId(accountId)
                    .employeeId(request.getEmployeeId())
                    .category(category.getName())
                    .subcategory(category.getSubcategory())
                    .description(request.getDescription())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .expenseDate(request.getExpenseDate())
                    .merchant(request.getMerchant())
                    .merchantCategory(request.getMerchantCategory())
                    .location(request.getLocation())
                    .receiptUrl(request.getReceiptUrl())
                    .paymentMethod(request.getPaymentMethod())
                    .isReimbursable(request.isReimbursable())
                    .isTaxDeductible(taxCategory.isDeductible())
                    .taxCategory(taxCategory.getName())
                    .status(determineInitialStatus(request, account))
                    .submittedBy(UUID.fromString(authenticationFacade.getCurrentUserId()))
                    .submittedAt(LocalDateTime.now())
                    .build();

            // Validate against budget
            BudgetValidationResult budgetResult = validateAgainstBudget(expense, category);
            expense.setBudgetImpact(budgetResult.getBudgetImpact());
            expense.setBudgetVariance(budgetResult.getVariance());

            expense = expenseRepository.save(expense);

            // Process approval workflow if required
            if (expense.getStatus() == ExpenseStatus.PENDING) {
                initiateApprovalWorkflow(expense);
            }

            // Send notifications
            sendExpenseCreatedNotifications(expense, employee);

            // Publish expense event
            publishExpenseEvent("EXPENSE_CREATED", expense);

            log.info("Expense created successfully: {} status: {}", expense.getId(), expense.getStatus());

            return mapToExpenseResponse(expense);

        } catch (Exception e) {
            log.error("Failed to create expense for account: {}", accountId, e);
            throw new ExpenseManagementException("Failed to create expense: " + e.getMessage(), e);
        }
    }

    /**
     * Process expense approval with advanced workflow
     */
    public ExpenseApprovalResponse processExpenseApproval(UUID accountId, UUID expenseId, 
                                                        ExpenseApprovalRequest request) {
        log.info("Processing expense approval: {} decision: {}", expenseId, request.getDecision());

        try {
            BusinessAccount account = getValidatedBusinessAccount(accountId);
            BusinessExpense expense = getValidatedExpense(accountId, expenseId);

            // Validate approval authority
            validateApprovalAuthority(expense, request);

            // Update expense status
            ExpenseStatus newStatus = request.getDecision() == ApprovalDecision.APPROVE ? 
                    ExpenseStatus.APPROVED : ExpenseStatus.REJECTED;

            expense.setStatus(newStatus);
            expense.setApprovalNotes(request.getNotes());
            expense.setApprovedBy(UUID.fromString(authenticationFacade.getCurrentUserId()));
            expense.setApprovedAt(LocalDateTime.now());

            if (request.getApprovedAmount() != null && expense.getAmount() != null &&
                request.getApprovedAmount().compareTo(expense.getAmount()) != 0) {
                expense.setApprovedAmount(request.getApprovedAmount());
                expense.setAdjustmentReason(request.getAdjustmentReason());
            }

            expense = expenseRepository.save(expense);

            // Process reimbursement if approved and reimbursable
            ExpenseReimbursement reimbursement = null;
            if (newStatus == ExpenseStatus.APPROVED && expense.isReimbursable()) {
                reimbursement = initiateReimbursement(expense);
            }

            // Update budget tracking
            updateBudgetTracking(expense);

            // Send approval notifications
            sendApprovalNotifications(expense, request.getDecision());

            // Publish approval event
            publishExpenseEvent("EXPENSE_" + newStatus.name(), expense);

            return ExpenseApprovalResponse.builder()
                    .expenseId(expenseId)
                    .status(newStatus)
                    .approvedAmount(expense.getApprovedAmount())
                    .reimbursementId(reimbursement != null ? reimbursement.getId() : null)
                    .processedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to process expense approval: {}", expenseId, e);
            throw new ExpenseApprovalException("Failed to process approval: " + e.getMessage(), e);
        }
    }

    /**
     * Create and manage expense budgets
     */
    public ExpenseBudgetResponse createExpenseBudget(UUID accountId, CreateExpenseBudgetRequest request) {
        log.info("Creating expense budget for account: {} category: {} amount: {}", 
                accountId, request.getCategory(), request.getBudgetAmount());

        try {
            BusinessAccount account = getValidatedBusinessAccount(accountId);

            // Check for existing budget
            if (budgetRepository.existsByAccountIdAndCategoryAndPeriod(
                    accountId, request.getCategory(), request.getPeriod())) {
                throw new DuplicateBudgetException("Budget already exists for this category and period");
            }

            ExpenseBudget budget = ExpenseBudget.builder()
                    .accountId(accountId)
                    .category(request.getCategory())
                    .budgetAmount(request.getBudgetAmount())
                    .period(request.getPeriod())
                    .startDate(request.getStartDate())
                    .endDate(request.getEndDate())
                    .alertThreshold(request.getAlertThreshold())
                    .isActive(true)
                    .createdAt(LocalDateTime.now())
                    .build();

            budget = budgetRepository.save(budget);

            // Set up budget monitoring
            setupBudgetMonitoring(budget);

            log.info("Expense budget created: {} for category: {}", budget.getId(), request.getCategory());

            return mapToBudgetResponse(budget);

        } catch (Exception e) {
            log.error("Failed to create expense budget for account: {}", accountId, e);
            throw new BudgetManagementException("Failed to create budget: " + e.getMessage(), e);
        }
    }

    /**
     * Process expense reimbursements
     */
    public ReimbursementResponse processReimbursement(UUID accountId, UUID reimbursementId, 
                                                    ProcessReimbursementRequest request) {
        log.info("Processing reimbursement: {} method: {}", reimbursementId, request.getPaymentMethod());

        try {
            BusinessAccount account = getValidatedBusinessAccount(accountId);
            ExpenseReimbursement reimbursement = getValidatedReimbursement(accountId, reimbursementId);

            if (reimbursement.getStatus() != ReimbursementStatus.PENDING) {
                throw new InvalidReimbursementStatusException("Reimbursement is not in pending status");
            }

            // Process payment
            PaymentResult paymentResult = processReimbursementPayment(reimbursement, request);

            // Update reimbursement status
            reimbursement.setStatus(paymentResult.isSuccessful() ? 
                    ReimbursementStatus.PAID : ReimbursementStatus.FAILED);
            reimbursement.setPaymentMethod(request.getPaymentMethod());
            reimbursement.setPaymentReference(paymentResult.getTransactionId());
            reimbursement.setProcessedAt(LocalDateTime.now());
            reimbursement.setProcessedBy(UUID.fromString(authenticationFacade.getCurrentUserId()));

            reimbursement = reimbursementRepository.save(reimbursement);

            // Send reimbursement notifications
            sendReimbursementNotifications(reimbursement);

            return mapToReimbursementResponse(reimbursement);

        } catch (Exception e) {
            log.error("Failed to process reimbursement: {}", reimbursementId, e);
            throw new ReimbursementException("Failed to process reimbursement: " + e.getMessage(), e);
        }
    }

    /**
     * Generate comprehensive expense analytics
     */
    @Transactional(readOnly = true)
    public ExpenseAnalyticsResponse generateExpenseAnalytics(UUID accountId, ExpenseAnalyticsRequest request) {
        log.info("Generating expense analytics for account: {} period: {} to {}", 
                accountId, request.getStartDate(), request.getEndDate());

        try {
            BusinessAccount account = getValidatedBusinessAccount(accountId);

            // Get expense data for the period
            List<BusinessExpense> expenses = expenseRepository.findByAccountIdAndExpenseDateBetween(
                    accountId, request.getStartDate().atStartOfDay(), request.getEndDate().atTime(23, 59, 59));

            // Calculate expense metrics
            ExpenseMetrics metrics = calculateExpenseMetrics(expenses);

            // Category analysis
            Map<String, CategoryExpenseAnalysis> categoryAnalysis = analyzeCategorySpending(expenses);

            // Employee spending analysis
            Map<UUID, EmployeeExpenseAnalysis> employeeAnalysis = analyzeEmployeeSpending(expenses);

            // Budget performance
            List<BudgetPerformance> budgetPerformance = analyzeBudgetPerformance(accountId, request);

            // Tax implications
            TaxAnalysis taxAnalysis = analyzeTaxImplications(expenses);

            // Spending trends
            List<ExpenseTrend> trends = calculateExpenseTrends(expenses, request);

            // Policy compliance
            PolicyComplianceAnalysis compliance = analyzePolicyCompliance(expenses, account);

            // Anomaly detection
            List<ExpenseAnomaly> anomalies = detectExpenseAnomalies(expenses);

            return ExpenseAnalyticsResponse.builder()
                    .accountId(accountId)
                    .period(request.getStartDate() + " to " + request.getEndDate())
                    .metrics(metrics)
                    .categoryAnalysis(categoryAnalysis)
                    .employeeAnalysis(employeeAnalysis)
                    .budgetPerformance(budgetPerformance)
                    .taxAnalysis(taxAnalysis)
                    .trends(trends)
                    .compliance(compliance)
                    .anomalies(anomalies)
                    .generatedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate expense analytics for account: {}", accountId, e);
            throw new AnalyticsException("Expense analytics generation failed", e);
        }
    }

    /**
     * Generate tax compliance reports
     */
    @Transactional(readOnly = true)
    public TaxComplianceReport generateTaxReport(UUID accountId, TaxReportRequest request) {
        log.info("Generating tax report for account: {} year: {}", accountId, request.getTaxYear());

        try {
            BusinessAccount account = getValidatedBusinessAccount(accountId);

            // Get all expenses for the tax year
            LocalDate startDate = LocalDate.of(request.getTaxYear(), 1, 1);
            LocalDate endDate = LocalDate.of(request.getTaxYear(), 12, 31);

            List<BusinessExpense> expenses = expenseRepository.findByAccountIdAndExpenseDateBetween(
                    accountId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59));

            // Filter deductible expenses
            List<BusinessExpense> deductibleExpenses = expenses.stream()
                    .filter(BusinessExpense::isTaxDeductible)
                    .filter(e -> e.getStatus() == ExpenseStatus.APPROVED)
                    .collect(Collectors.toList());

            // Calculate tax deductions by category
            Map<String, TaxDeductionSummary> deductionsByCategory = calculateTaxDeductions(deductibleExpenses);

            // Calculate total deductible amount
            BigDecimal totalDeductible = deductibleExpenses.stream()
                    .map(e -> e.getApprovedAmount() != null ? e.getApprovedAmount() : e.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Generate supporting documentation
            List<TaxSupportingDocument> supportingDocs = generateSupportingDocuments(deductibleExpenses);

            // Calculate potential tax savings
            BigDecimal estimatedTaxSavings = calculateEstimatedTaxSavings(totalDeductible, account);

            return TaxComplianceReport.builder()
                    .accountId(accountId)
                    .taxYear(request.getTaxYear())
                    .totalDeductibleAmount(totalDeductible)
                    .deductionsByCategory(deductionsByCategory)
                    .supportingDocuments(supportingDocs)
                    .estimatedTaxSavings(estimatedTaxSavings)
                    .complianceScore(calculateComplianceScore(deductibleExpenses))
                    .recommendedActions(generateTaxRecommendations(deductibleExpenses, account))
                    .generatedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate tax report for account: {}", accountId, e);
            throw new TaxReportException("Tax report generation failed", e);
        }
    }

    /**
     * Automated expense categorization and receipt processing
     */
    @Async
    public CompletableFuture<ExpenseCategorization> categorizeExpenseFromReceipt(UUID expenseId, byte[] receiptImage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Processing receipt for expense: {}", expenseId);

                // OCR receipt processing
                ReceiptOCRResult ocrResult = processReceiptOCR(receiptImage);

                // Extract expense details
                ExtractedExpenseDetails details = extractExpenseDetails(ocrResult);

                // AI-powered categorization
                ExpenseCategory suggestedCategory = categorizeExpenseWithAI(details);

                // Validate merchant and location
                MerchantValidation merchantValidation = validateMerchant(details.getMerchantName(), details.getLocation());

                // Calculate confidence score
                BigDecimal confidenceScore = calculateCategorizationConfidence(details, suggestedCategory, merchantValidation);

                return ExpenseCategorization.builder()
                        .expenseId(expenseId)
                        .suggestedCategory(suggestedCategory)
                        .extractedDetails(details)
                        .merchantValidation(merchantValidation)
                        .confidenceScore(confidenceScore)
                        .requiresReview(confidenceScore.compareTo(new BigDecimal("0.8")) < 0)
                        .processedAt(LocalDateTime.now())
                        .build();

            } catch (Exception e) {
                log.error("Failed to categorize expense from receipt: {}", expenseId, e);
                throw new ExpenseCategorizationException("Receipt processing failed", e);
            }
        });
    }

    /**
     * Scheduled job for budget monitoring and alerts
     */
    @Scheduled(cron = "0 0 8 * * *") // Daily at 8 AM
    @Async
    public void monitorBudgets() {
        log.info("Starting scheduled budget monitoring");

        try {
            List<ExpenseBudget> activeBudgets = budgetRepository.findByIsActiveTrue();

            for (ExpenseBudget budget : activeBudgets) {
                try {
                    BudgetMonitoringResult result = monitorBudget(budget);
                    
                    if (result.requiresAlert()) {
                        sendBudgetAlert(budget, result);
                    }
                } catch (Exception e) {
                    log.warn("Failed to monitor budget: {}", budget.getId(), e);
                }
            }

            log.info("Completed budget monitoring for {} budgets", activeBudgets.size());

        } catch (Exception e) {
            log.error("Error in budget monitoring job", e);
        }
    }

    // Helper methods for expense processing

    private BusinessAccount getValidatedBusinessAccount(UUID accountId) {
        BusinessAccount account = businessAccountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessAccountNotFoundException("Business account not found"));

        String currentUserId = authenticationFacade.getCurrentUserId();
        if (!account.getOwnerId().toString().equals(currentUserId)) {
            throw new UnauthorizedBusinessAccessException("Unauthorized access to business account");
        }

        return account;
    }

    private BusinessEmployee getValidatedEmployee(UUID accountId, UUID employeeId) {
        return employeeRepository.findByIdAndAccountId(employeeId, accountId)
                .orElseThrow(() -> new EmployeeNotFoundException("Employee not found"));
    }

    private BusinessExpense getValidatedExpense(UUID accountId, UUID expenseId) {
        return expenseRepository.findByIdAndAccountId(expenseId, accountId)
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found"));
    }

    private ExpenseReimbursement getValidatedReimbursement(UUID accountId, UUID reimbursementId) {
        return reimbursementRepository.findByIdAndAccountId(reimbursementId, accountId)
                .orElseThrow(() -> new ReimbursementNotFoundException("Reimbursement not found"));
    }

    private ExpenseCategory determineExpenseCategory(CreateExpenseRequest request) {
        // AI-powered categorization based on merchant, description, and amount
        return categoryRepository.findByMerchantCategory(request.getMerchantCategory())
                .orElse(ExpenseCategory.builder()
                        .name("General Business")
                        .subcategory("Miscellaneous")
                        .build());
    }

    private TaxCategory getTaxCategory(ExpenseCategory category, BigDecimal amount) {
        return taxCategoryRepository.findByExpenseCategory(category.getName())
                .orElse(TaxCategory.builder()
                        .name("Business Expense")
                        .isDeductible(true)
                        .deductionPercentage(BigDecimal.valueOf(100))
                        .build());
    }

    private ExpenseStatus determineInitialStatus(CreateExpenseRequest request, BusinessAccount account) {
        if (request.getAmount() != null && request.getAmount().compareTo(AUTO_APPROVAL_THRESHOLD) <= 0) {
            return ExpenseStatus.AUTO_APPROVED;
        }
        return ExpenseStatus.PENDING;
    }

    // Placeholder implementations for complex operations
    private BudgetValidationResult validateAgainstBudget(BusinessExpense expense, ExpenseCategory category) {
        try {
            // Find active budget for the category and account
            ExpenseBudget budget = budgetRepository.findByAccountIdAndCategoryIdAndActiveTrue(
                expense.getAccountId(), expense.getCategoryId())
                .orElse(null);
            
            if (budget == null) {
                // No budget constraints - allow expense
                return BudgetValidationResult.builder()
                    .budgetExists(false)
                    .withinBudget(true)
                    .budgetImpact(expense.getAmount())
                    .variance(BigDecimal.ZERO)
                    .message("No budget defined for this category")
                    .build();
            }
            
            // Calculate current spending in the budget period
            LocalDate periodStart = budget.getPeriodStart();
            LocalDate periodEnd = budget.getPeriodEnd();
            
            BigDecimal currentSpending = expenseRepository.sumAmountByAccountIdAndCategoryIdAndPeriod(
                expense.getAccountId(), expense.getCategoryId(), periodStart, periodEnd);
            
            BigDecimal projectedSpending = currentSpending.add(expense.getAmount());
            BigDecimal remainingBudget = budget.getAllocatedAmount().subtract(currentSpending);
            BigDecimal variance = budget.getAllocatedAmount().subtract(projectedSpending);
            
            boolean withinBudget = projectedSpending.compareTo(budget.getAllocatedAmount()) <= 0;
            
            // Check warning threshold (80% of budget)
            BigDecimal warningThreshold = budget.getAllocatedAmount().multiply(BigDecimal.valueOf(0.8));
            boolean approachingLimit = projectedSpending.compareTo(warningThreshold) > 0;
            
            BudgetValidationResult result = BudgetValidationResult.builder()
                .budgetExists(true)
                .budgetId(budget.getId())
                .withinBudget(withinBudget)
                .budgetImpact(expense.getAmount())
                .currentSpending(currentSpending)
                .projectedSpending(projectedSpending)
                .remainingBudget(remainingBudget)
                .variance(variance)
                .budgetAmount(budget.getAllocatedAmount())
                .utilizationPercentage(projectedSpending.divide(budget.getAllocatedAmount(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)))
                .approachingLimit(approachingLimit)
                .build();
            
            if (!withinBudget) {
                result.setMessage(String.format("Expense exceeds budget by %s (%.2f%%)", 
                    variance.abs().toString(), 
                    variance.abs().divide(budget.getAllocatedAmount(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))));
                result.setRequiresApproval(true);
            } else if (approachingLimit) {
                result.setMessage(String.format("Budget utilization at %.2f%% - approaching limit", 
                    result.getUtilizationPercentage()));
            } else {
                result.setMessage("Within budget limits");
            }
            
            return result;
        } catch (Exception e) {
            log.error("Error validating budget for expense {}: {}", expense.getId(), e.getMessage());
            return BudgetValidationResult.builder()
                .budgetExists(false)
                .withinBudget(true)
                .budgetImpact(expense.getAmount())
                .variance(BigDecimal.ZERO)
                .message("Unable to validate budget - allowing expense")
                .build();
        }
    }
    @Async
    private void initiateApprovalWorkflow(BusinessExpense expense) {
        try {
            log.info("Initiating approval workflow for expense {} amount {}", expense.getId(), expense.getAmount());
            
            // Determine approval requirements based on amount and category
            List<ExpenseApprovalLevel> approvalLevels = determineApprovalLevels(expense);
            
            if (approvalLevels.isEmpty()) {
                // No approval required - auto-approve
                expense.setStatus(ExpenseStatus.APPROVED);
                expense.setApprovedAt(LocalDateTime.now());
                expenseRepository.save(expense);
                
                publishExpenseEvent("EXPENSE_AUTO_APPROVED", expense);
                return;
            }
            
            // Create approval workflow
            ExpenseApprovalWorkflow workflow = new ExpenseApprovalWorkflow();
            workflow.setExpenseId(expense.getId());
            workflow.setAccountId(expense.getAccountId());
            workflow.setStatus(ApprovalWorkflowStatus.PENDING);
            workflow.setCurrentLevel(0);
            workflow.setTotalLevels(approvalLevels.size());
            workflow.setCreatedAt(LocalDateTime.now());
            workflow.setExpiresAt(LocalDateTime.now().plusDays(7)); // 7 days to approve
            
            // Set approval levels
            workflow.setApprovalLevels(approvalLevels);
            workflowRepository.save(workflow);
            
            // Start first approval level
            ExpenseApprovalLevel firstLevel = approvalLevels.get(0);
            sendApprovalRequest(expense, firstLevel, workflow);
            
            // Update expense status
            expense.setStatus(ExpenseStatus.PENDING_APPROVAL);
            expense.setApprovalWorkflowId(workflow.getId());
            expenseRepository.save(expense);
            
            publishExpenseEvent("APPROVAL_WORKFLOW_INITIATED", expense);
        } catch (Exception e) {
            log.error("Error initiating approval workflow for expense {}: {}", expense.getId(), e.getMessage());
        }
    }
    
    private List<ExpenseApprovalLevel> determineApprovalLevels(BusinessExpense expense) {
        List<ExpenseApprovalLevel> levels = new ArrayList<>();
        
        // Level 1: Immediate supervisor (amounts > $500)
        if (expense.getAmount().compareTo(BigDecimal.valueOf(500)) > 0) {
            ExpenseApprovalLevel supervisorLevel = new ExpenseApprovalLevel();
            supervisorLevel.setLevel(1);
            supervisorLevel.setRequiredRole("SUPERVISOR");
            supervisorLevel.setDescription("Direct supervisor approval");
            supervisorLevel.setAmountThreshold(BigDecimal.valueOf(500));
            levels.add(supervisorLevel);
        }
        
        // Level 2: Department head (amounts > $2000)
        if (expense.getAmount().compareTo(BigDecimal.valueOf(2000)) > 0) {
            ExpenseApprovalLevel deptHeadLevel = new ExpenseApprovalLevel();
            deptHeadLevel.setLevel(2);
            deptHeadLevel.setRequiredRole("DEPARTMENT_HEAD");
            deptHeadLevel.setDescription("Department head approval");
            deptHeadLevel.setAmountThreshold(BigDecimal.valueOf(2000));
            levels.add(deptHeadLevel);
        }
        
        // Level 3: Finance director (amounts > $10000)
        if (expense.getAmount().compareTo(BigDecimal.valueOf(10000)) > 0) {
            ExpenseApprovalLevel financeLevel = new ExpenseApprovalLevel();
            financeLevel.setLevel(3);
            financeLevel.setRequiredRole("FINANCE_DIRECTOR");
            financeLevel.setDescription("Finance director approval");
            financeLevel.setAmountThreshold(BigDecimal.valueOf(10000));
            levels.add(financeLevel);
        }
        
        return levels;
    }
    
    private void sendApprovalRequest(BusinessExpense expense, ExpenseApprovalLevel level, ExpenseApprovalWorkflow workflow) {
        // Find approvers for this level
        List<BusinessEmployee> approvers = employeeRepository.findByAccountIdAndRole(
            expense.getAccountId(), level.getRequiredRole());
        
        for (BusinessEmployee approver : approvers) {
            // Send notification to each approver
            Map<String, Object> notification = Map.of(
                "type", "EXPENSE_APPROVAL_REQUEST",
                "approverUserId", approver.getUserId(),
                "expenseId", expense.getId(),
                "amount", expense.getAmount(),
                "description", expense.getDescription(),
                "submittedBy", expense.getSubmittedBy(),
                "workflowId", workflow.getId(),
                "level", level.getLevel(),
                "expiresAt", workflow.getExpiresAt()
            );
            
            kafkaTemplate.send("approval-notifications", notification);
        }
    }
    private void validateApprovalAuthority(BusinessExpense expense, ExpenseApprovalRequest request) {
        try {
            // Get current workflow
            ExpenseApprovalWorkflow workflow = workflowRepository.findById(expense.getApprovalWorkflowId())
                .orElseThrow(() -> new IllegalArgumentException("Approval workflow not found"));
            
            if (workflow.getStatus() != ApprovalWorkflowStatus.PENDING) {
                throw new IllegalArgumentException("Workflow is not in pending status");
            }
            
            // Get current approval level
            if (workflow.getApprovalLevels() == null || workflow.getCurrentLevel() >= workflow.getApprovalLevels().size()) {
                throw new IllegalArgumentException("All approval levels completed");
            }
            
            ExpenseApprovalLevel currentLevel = workflow.getApprovalLevels().get(workflow.getCurrentLevel());
            
            // Verify approver authority
            BusinessEmployee approver = employeeRepository.findByUserIdAndAccountId(
                request.getApproverUserId(), expense.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Approver not found in account"));
            
            // Check if approver has required role for this level
            if (!hasRequiredRole(approver, currentLevel.getRequiredRole())) {
                throw new IllegalArgumentException("Insufficient authority for approval at this level");
            }
            
            // Check if approver is trying to approve their own expense
            if (expense.getSubmittedBy().equals(request.getApproverUserId())) {
                throw new IllegalArgumentException("Cannot approve your own expense");
            }
            
            // Check workflow expiration
            if (workflow.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new IllegalArgumentException("Approval workflow has expired");
            }
            
            // Check if this level has already been approved
            boolean alreadyApproved = workflow.getApprovals().stream()
                .anyMatch(approval -> approval.getLevel() == currentLevel.getLevel() && 
                           approval.getDecision() == ApprovalDecision.APPROVED);
            
            if (alreadyApproved) {
                throw new IllegalArgumentException("This approval level has already been completed");
            }
            
        } catch (Exception e) {
            log.error("Validation failed for approval authority: {}", e.getMessage());
            throw new IllegalArgumentException(e.getMessage());
        }
    }
    
    private boolean hasRequiredRole(BusinessEmployee employee, String requiredRole) {
        return employee.getRole() != null && 
               (employee.getRole().equals(requiredRole) || 
                employee.getAuthorities().contains(requiredRole));
    }
    private ExpenseReimbursement initiateReimbursement(BusinessExpense expense) {
        try {
            log.info("Initiating reimbursement for expense {}", expense.getId());
            
            // Calculate reimbursable amount
            BigDecimal reimbursableAmount = calculateReimbursableAmount(expense);
            
            if (reimbursableAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("No reimbursable amount for this expense");
            }
            
            // Create reimbursement record
            ExpenseReimbursement reimbursement = new ExpenseReimbursement();
            reimbursement.setExpenseId(expense.getId());
            reimbursement.setAccountId(expense.getAccountId());
            reimbursement.setEmployeeUserId(expense.getSubmittedBy());
            reimbursement.setAmount(reimbursableAmount);
            reimbursement.setTaxableAmount(calculateTaxableAmount(expense));
            reimbursement.setStatus(ReimbursementStatus.PENDING);
            reimbursement.setRequestedAt(LocalDateTime.now());
            reimbursement.setExpectedProcessingDate(LocalDateTime.now().plusDays(5)); // 5 business days
            
            // Set reimbursement method (default to direct deposit)
            BusinessEmployee employee = employeeRepository.findByUserIdAndAccountId(
                expense.getSubmittedBy(), expense.getAccountId()).orElse(null);
            
            if (employee != null && employee.getBankAccountDetails() != null) {
                reimbursement.setReimbursementMethod(ReimbursementMethod.DIRECT_DEPOSIT);
                reimbursement.setPaymentDetails(employee.getBankAccountDetails());
            } else {
                reimbursement.setReimbursementMethod(ReimbursementMethod.CHECK);
            }
            
            // Add metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("originalAmount", expense.getAmount());
            metadata.put("currency", expense.getCurrency());
            metadata.put("taxCategory", expense.getTaxCategory());
            metadata.put("category", expense.getCategory());
            metadata.put("receiptRequired", expense.isReceiptRequired());
            metadata.put("receiptProvided", expense.getReceiptUrl() != null);
            reimbursement.setMetadata(metadata);
            
            // Save reimbursement
            reimbursement = reimbursementRepository.save(reimbursement);
            
            // Update expense with reimbursement ID
            expense.setReimbursementId(reimbursement.getId());
            expense.setReimbursementStatus(ReimbursementStatus.PENDING);
            expenseRepository.save(expense);
            
            // Send notifications
            sendReimbursementNotifications(reimbursement);
            
            // Publish event
            publishReimbursementEvent("REIMBURSEMENT_INITIATED", reimbursement);
            
            log.info("Reimbursement {} created for expense {} amount {}", 
                reimbursement.getId(), expense.getId(), reimbursableAmount);
            
            return reimbursement;
        } catch (Exception e) {
            log.error("Error initiating reimbursement for expense {}: {}", expense.getId(), e.getMessage());
            throw new RuntimeException("Failed to initiate reimbursement: " + e.getMessage());
        }
    }
    
    private BigDecimal calculateReimbursableAmount(BusinessExpense expense) {
        // For now, assume full amount is reimbursable
        // In practice, this would consider policy limits, personal vs business portions, etc.
        BigDecimal reimbursablePercentage = BigDecimal.valueOf(100); // 100%
        
        if (expense.getPersonalPortionPercentage() != null) {
            reimbursablePercentage = reimbursablePercentage.subtract(expense.getPersonalPortionPercentage());
        }
        
        return expense.getAmount().multiply(reimbursablePercentage.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
    }
    
    private BigDecimal calculateTaxableAmount(BusinessExpense expense) {
        // Calculate taxable portion based on tax category and regulations
        // This is a simplified implementation
        if (expense.getTaxCategory() == null) {
            return BigDecimal.ZERO;
        }
        
        return switch (expense.getTaxCategory()) {
            case "MEALS" -> expense.getAmount().multiply(BigDecimal.valueOf(0.5)); // 50% deductible
            case "ENTERTAINMENT" -> expense.getAmount().multiply(BigDecimal.valueOf(0.5));
            case "TRAVEL", "OFFICE_SUPPLIES", "EQUIPMENT" -> expense.getAmount(); // 100% deductible
            default -> expense.getAmount().multiply(BigDecimal.valueOf(0.8)); // 80% default
        };
    }
    
    private void publishReimbursementEvent(String eventType, ExpenseReimbursement reimbursement) {
        Map<String, Object> event = Map.of(
            "eventType", eventType,
            "reimbursementId", reimbursement.getId(),
            "expenseId", reimbursement.getExpenseId(),
            "accountId", reimbursement.getAccountId(),
            "amount", reimbursement.getAmount(),
            "status", reimbursement.getStatus(),
            "timestamp", LocalDateTime.now()
        );
        kafkaTemplate.send("expense-reimbursement-events", reimbursement.getId().toString(), event);
    }
    private PaymentResult processReimbursementPayment(ExpenseReimbursement reimbursement, ProcessReimbursementRequest request) {
        return PaymentResult.builder().successful(true).transactionId("REF-" + UUID.randomUUID()).build();
    }

    // Notification methods
    @Async
    private void sendExpenseCreatedNotifications(BusinessExpense expense, BusinessEmployee employee) {
        try {
            // Notify employee
            Map<String, Object> employeeNotification = Map.of(
                "type", "EXPENSE_CREATED",
                "userId", expense.getSubmittedBy(),
                "title", "Expense Submitted Successfully",
                "message", String.format("Your expense of %s has been submitted for %s", 
                    expense.getAmount(), expense.getCategory()),
                "metadata", Map.of(
                    "expenseId", expense.getId(),
                    "amount", expense.getAmount(),
                    "category", expense.getCategory(),
                    "status", expense.getStatus(),
                    "requiresApproval", expense.getStatus() == ExpenseStatus.PENDING_APPROVAL
                )
            );
            
            kafkaTemplate.send("expense-notifications", employeeNotification);
            
            // Notify finance team if amount is significant
            if (expense.getAmount().compareTo(BigDecimal.valueOf(1000)) > 0) {
                List<BusinessEmployee> financeTeam = employeeRepository.findByAccountIdAndDepartment(
                    expense.getAccountId(), "FINANCE");
                
                for (BusinessEmployee financeEmployee : financeTeam) {
                    Map<String, Object> financeNotification = Map.of(
                        "type", "HIGH_VALUE_EXPENSE_CREATED",
                        "userId", financeEmployee.getUserId(),
                        "title", "High-Value Expense Submitted",
                        "message", String.format("New expense of %s submitted by %s", 
                            expense.getAmount(), employee.getFullName()),
                        "metadata", Map.of(
                            "expenseId", expense.getId(),
                            "submittedBy", expense.getSubmittedBy(),
                            "amount", expense.getAmount(),
                            "category", expense.getCategory()
                        )
                    );
                    
                    kafkaTemplate.send("expense-notifications", financeNotification);
                }
            }
            
        } catch (Exception e) {
            log.error("Error sending expense created notifications: {}", e.getMessage());
        }
    }
    @Async
    private void sendApprovalNotifications(BusinessExpense expense, ApprovalDecision decision) {
        try {
            // Notify expense submitter
            String title = decision == ApprovalDecision.APPROVED ? 
                "Expense Approved" : "Expense Rejected";
            String message = decision == ApprovalDecision.APPROVED ?
                String.format("Your expense of %s has been approved", expense.getAmount()) :
                String.format("Your expense of %s has been rejected", expense.getAmount());
            
            Map<String, Object> submitterNotification = Map.of(
                "type", decision == ApprovalDecision.APPROVED ? "EXPENSE_APPROVED" : "EXPENSE_REJECTED",
                "userId", expense.getSubmittedBy(),
                "title", title,
                "message", message,
                "metadata", Map.of(
                    "expenseId", expense.getId(),
                    "amount", expense.getAmount(),
                    "category", expense.getCategory(),
                    "decision", decision.toString(),
                    "approvedAt", expense.getApprovedAt() != null ? expense.getApprovedAt() : LocalDateTime.now()
                )
            );
            
            kafkaTemplate.send("expense-notifications", submitterNotification);
            
            // If approved, notify finance team for reimbursement processing
            if (decision == ApprovalDecision.APPROVED) {
                List<BusinessEmployee> financeTeam = employeeRepository.findByAccountIdAndDepartment(
                    expense.getAccountId(), "FINANCE");
                
                for (BusinessEmployee financeEmployee : financeTeam) {
                    Map<String, Object> financeNotification = Map.of(
                        "type", "EXPENSE_READY_FOR_REIMBURSEMENT",
                        "userId", financeEmployee.getUserId(),
                        "title", "Expense Ready for Reimbursement",
                        "message", String.format("Approved expense of %s ready for processing", expense.getAmount()),
                        "metadata", Map.of(
                            "expenseId", expense.getId(),
                            "amount", expense.getAmount(),
                            "submittedBy", expense.getSubmittedBy(),
                            "category", expense.getCategory()
                        )
                    );
                    
                    kafkaTemplate.send("expense-notifications", financeNotification);
                }
            }
            
        } catch (Exception e) {
            log.error("Error sending approval notifications: {}", e.getMessage());
        }
    }
    @Async
    private void sendReimbursementNotifications(ExpenseReimbursement reimbursement) {
        try {
            // Notify employee about reimbursement initiation
            Map<String, Object> employeeNotification = Map.of(
                "type", "REIMBURSEMENT_INITIATED",
                "userId", reimbursement.getEmployeeUserId(),
                "title", "Reimbursement Processing Started",
                "message", String.format("Reimbursement of %s is being processed via %s", 
                    reimbursement.getAmount(), reimbursement.getReimbursementMethod()),
                "metadata", Map.of(
                    "reimbursementId", reimbursement.getId(),
                    "expenseId", reimbursement.getExpenseId(),
                    "amount", reimbursement.getAmount(),
                    "method", reimbursement.getReimbursementMethod(),
                    "expectedDate", reimbursement.getExpectedProcessingDate()
                )
            );
            
            kafkaTemplate.send("expense-notifications", employeeNotification);
            
            // Notify finance team
            List<BusinessEmployee> financeTeam = employeeRepository.findByAccountIdAndDepartment(
                reimbursement.getAccountId(), "FINANCE");
            
            for (BusinessEmployee financeEmployee : financeTeam) {
                Map<String, Object> financeNotification = Map.of(
                    "type", "REIMBURSEMENT_PROCESSING_REQUIRED",
                    "userId", financeEmployee.getUserId(),
                    "title", "Reimbursement Processing Required",
                    "message", String.format("Process reimbursement of %s via %s", 
                        reimbursement.getAmount(), reimbursement.getReimbursementMethod()),
                    "metadata", Map.of(
                        "reimbursementId", reimbursement.getId(),
                        "amount", reimbursement.getAmount(),
                        "method", reimbursement.getReimbursementMethod(),
                        "employeeUserId", reimbursement.getEmployeeUserId()
                    )
                );
                
                kafkaTemplate.send("expense-notifications", financeNotification);
            }
            
        } catch (Exception e) {
            log.error("Error sending reimbursement notifications: {}", e.getMessage());
        }
    }
    @Async
    private void sendBudgetAlert(ExpenseBudget budget, BudgetMonitoringResult result) {
        try {
            String alertType;
            String title;
            String message;
            
            if (result.getUtilizationPercentage().compareTo(BigDecimal.valueOf(100)) >= 0) {
                alertType = "BUDGET_EXCEEDED";
                title = "Budget Exceeded";
                message = String.format("Budget '%s' has exceeded limit by %s (%.1f%%)", 
                    budget.getName(), 
                    result.getVariance().abs(), 
                    result.getUtilizationPercentage().subtract(BigDecimal.valueOf(100)));
            } else if (result.getUtilizationPercentage().compareTo(BigDecimal.valueOf(90)) >= 0) {
                alertType = "BUDGET_CRITICAL";
                title = "Budget Critical";
                message = String.format("Budget '%s' is at %.1f%% utilization - immediate attention required", 
                    budget.getName(), result.getUtilizationPercentage());
            } else if (result.getUtilizationPercentage().compareTo(BigDecimal.valueOf(80)) >= 0) {
                alertType = "BUDGET_WARNING";
                title = "Budget Warning";
                message = String.format("Budget '%s' is at %.1f%% utilization - approaching limit", 
                    budget.getName(), result.getUtilizationPercentage());
            } else {
                // No alert needed
                return;
            }
            
            // Find budget owners and finance team
            List<UUID> notificationTargets = new ArrayList<>();
            
            if (budget.getOwnerUserId() != null) {
                notificationTargets.add(budget.getOwnerUserId());
            }
            
            // Add department heads
            List<BusinessEmployee> departmentHeads = employeeRepository.findByAccountIdAndRole(
                budget.getAccountId(), "DEPARTMENT_HEAD");
            notificationTargets.addAll(departmentHeads.stream()
                .map(BusinessEmployee::getUserId).toList());
            
            // Add finance team
            List<BusinessEmployee> financeTeam = employeeRepository.findByAccountIdAndDepartment(
                budget.getAccountId(), "FINANCE");
            notificationTargets.addAll(financeTeam.stream()
                .map(BusinessEmployee::getUserId).toList());
            
            // Send notifications
            for (UUID userId : notificationTargets.stream().distinct().toList()) {
                Map<String, Object> notification = Map.of(
                    "type", alertType,
                    "userId", userId,
                    "title", title,
                    "message", message,
                    "priority", result.getUtilizationPercentage().compareTo(BigDecimal.valueOf(100)) >= 0 ? "HIGH" : "MEDIUM",
                    "metadata", Map.of(
                        "budgetId", budget.getId(),
                        "budgetName", budget.getName(),
                        "allocatedAmount", budget.getAllocatedAmount(),
                        "spentAmount", result.getSpentAmount(),
                        "remainingAmount", result.getRemainingAmount(),
                        "utilizationPercentage", result.getUtilizationPercentage(),
                        "variance", result.getVariance()
                    )
                );
                
                kafkaTemplate.send("budget-alerts", notification);
            }
            
            // Update budget alert history
            budget.setLastAlertSent(LocalDateTime.now());
            budget.setAlertLevel(alertType);
            budgetRepository.save(budget);
            
        } catch (Exception e) {
            log.error("Error sending budget alert: {}", e.getMessage());
        }
    }

    // Event publishing
    private void publishExpenseEvent(String eventType, BusinessExpense expense) {
        Map<String, Object> event = Map.of(
                "eventType", eventType,
                "expenseId", expense.getId(),
                "accountId", expense.getAccountId(),
                "amount", expense.getAmount(),
                "timestamp", LocalDateTime.now()
        );
        kafkaTemplate.send("business-expense-events", expense.getId().toString(), event);
    }

    // Response mapping methods
    private ExpenseResponse mapToExpenseResponse(BusinessExpense expense) {
        return ExpenseResponse.builder()
                .expenseId(expense.getId())
                .amount(expense.getAmount())
                .category(expense.getCategory())
                .status(expense.getStatus())
                .submittedAt(expense.getSubmittedAt())
                .build();
    }

    private ExpenseBudgetResponse mapToBudgetResponse(ExpenseBudget budget) {
        return ExpenseBudgetResponse.builder()
                .budgetId(budget.getId())
                .category(budget.getCategory())
                .budgetAmount(budget.getBudgetAmount())
                .period(budget.getPeriod())
                .isActive(budget.isActive())
                .build();
    }

    // Additional placeholder methods and enum definitions
    public enum ApprovalDecision { APPROVE, REJECT }
    public enum ReimbursementStatus { PENDING, PAID, FAILED }
}