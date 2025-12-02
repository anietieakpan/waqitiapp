package com.waqiti.expense.service;

import com.waqiti.expense.domain.Expense;
import com.waqiti.expense.domain.ExpenseCategory;
import com.waqiti.expense.domain.Budget;
import com.waqiti.expense.domain.enums.ExpenseStatus;
import com.waqiti.expense.domain.enums.ExpenseType;
import com.waqiti.expense.dto.request.ExpenseRequest;
import com.waqiti.expense.dto.request.ExpenseSearchRequest;
import com.waqiti.expense.dto.request.BulkExpenseRequest;
import com.waqiti.expense.dto.response.ExpenseResponse;
import com.waqiti.expense.dto.response.ExpenseAnalyticsResponse;
import com.waqiti.expense.dto.response.ExpenseInsightsResponse;
import com.waqiti.expense.exception.ExpenseNotFoundException;
import com.waqiti.expense.exception.ExpenseValidationException;
import com.waqiti.expense.repository.ExpenseRepository;
import com.waqiti.expense.repository.BudgetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Comprehensive Expense Tracking Service
 * 
 * Provides intelligent expense management with automatic categorization,
 * real-time insights, budget integration, and advanced analytics
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ExpenseTrackingService {

    @Lazy
    private final ExpenseTrackingService self;
    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;
    private final ExpenseCategoryService categoryService;
    private final ExpenseClassificationService classificationService;
    private final ExpenseAnalyticsService analyticsService;
    private final BudgetService budgetService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    // Event topics
    private static final String EXPENSE_CREATED_TOPIC = "expense-created";
    private static final String EXPENSE_UPDATED_TOPIC = "expense-updated";
    private static final String BUDGET_ALERT_TOPIC = "budget-alert";

    /**
     * Create new expense with intelligent categorization
     */
    @Transactional
    public ExpenseResponse createExpense(String userId, ExpenseRequest request) {
        log.info("Creating expense for user: {} amount: {} description: {}", 
                userId, request.getAmount(), request.getDescription());

        try {
            // Validate request
            validateExpenseRequest(request);

            // Create expense entity
            Expense expense = buildExpenseFromRequest(userId, request);

            // Perform intelligent categorization
            if (expense.getCategory() == null) {
                ExpenseCategory category = classificationService.classifyExpense(
                        expense.getDescription(), expense.getMerchantName(), 
                        expense.getMerchantCategory(), expense.getAmount());
                        
                if (category != null) {
                    Double confidence = classificationService.getClassificationConfidence(
                            expense, category);
                    expense.setCategoryWithConfidence(category, confidence);
                }
            }

            // Check budget impact
            checkAndUpdateBudgets(expense);

            // Save expense
            expense = expenseRepository.save(expense);

            // Update category usage statistics
            if (expense.getCategory() != null) {
                categoryService.recordCategoryUsage(expense.getCategory().getId(), expense.getAmount());
            }

            // Send notifications for budget alerts
            sendBudgetAlerts(expense);

            // Publish expense created event
            publishExpenseEvent(EXPENSE_CREATED_TOPIC, expense);

            // Generate insights asynchronously
            generateExpenseInsightsAsync(userId, expense);

            log.info("Expense created successfully: {} for user: {}", expense.getId(), userId);
            return mapToExpenseResponse(expense);

        } catch (Exception e) {
            log.error("Failed to create expense for user: {}", userId, e);
            throw new ExpenseValidationException("Failed to create expense: " + e.getMessage(), e);
        }
    }

    /**
     * Update existing expense
     */
    @Transactional
    public ExpenseResponse updateExpense(String userId, String expenseId, ExpenseRequest request) {
        log.info("Updating expense: {} for user: {}", expenseId, userId);

        try {
            // Find existing expense
            Expense expense = expenseRepository.findByIdAndUserId(expenseId, userId)
                    .orElseThrow(() -> new ExpenseNotFoundException("Expense not found: " + expenseId));

            // Store original amount for budget adjustment
            BigDecimal originalAmount = expense.getAmount();
            Budget originalBudget = expense.getBudget();

            // Update expense fields
            updateExpenseFromRequest(expense, request);

            // Re-categorize if needed
            if (request.getCategoryId() == null && hasSignificantChanges(expense, request)) {
                ExpenseCategory newCategory = classificationService.classifyExpense(
                        expense.getDescription(), expense.getMerchantName(), 
                        expense.getMerchantCategory(), expense.getAmount());
                        
                if (newCategory != null) {
                    Double confidence = classificationService.getClassificationConfidence(
                            expense, newCategory);
                    expense.setCategoryWithConfidence(newCategory, confidence);
                }
            }

            // Adjust budgets
            adjustBudgetsForUpdate(expense, originalAmount, originalBudget);

            // Save updated expense
            expense = expenseRepository.save(expense);

            // Publish expense updated event
            publishExpenseEvent(EXPENSE_UPDATED_TOPIC, expense);

            log.info("Expense updated successfully: {}", expenseId);
            return mapToExpenseResponse(expense);

        } catch (Exception e) {
            log.error("Failed to update expense: {} for user: {}", expenseId, userId, e);
            throw new ExpenseValidationException("Failed to update expense: " + e.getMessage(), e);
        }
    }

    /**
     * Get user expenses with advanced filtering and search
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "userExpenses", key = "#userId + ':' + #request.hashCode() + ':' + #pageable.pageNumber")
    public Page<ExpenseResponse> getUserExpenses(String userId, ExpenseSearchRequest request, Pageable pageable) {
        log.debug("Retrieving expenses for user: {} with filters: {}", userId, request);

        try {
            // Build search criteria
            Expense.ExpenseSearchCriteria criteria = buildSearchCriteria(request);

            // Execute search with advanced filtering
            Page<Expense> expenses = expenseRepository.findByUserIdWithCriteria(userId, criteria, pageable);

            // Enrich with additional data
            return expenses.map(this::enrichExpenseResponse);

        } catch (Exception e) {
            log.error("Failed to retrieve expenses for user: {}", userId, e);
            throw new ExpenseValidationException("Failed to retrieve expenses", e);
        }
    }

    /**
     * Get expense analytics and insights
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "expenseAnalytics", key = "#userId + ':' + #period")
    public ExpenseAnalyticsResponse getExpenseAnalytics(String userId, String period) {
        log.info("Generating expense analytics for user: {} period: {}", userId, period);

        try {
            LocalDate startDate = calculatePeriodStartDate(period);
            LocalDate endDate = LocalDate.now();

            // Get expenses for the period
            List<Expense> expenses = expenseRepository.findByUserIdAndExpenseDateBetween(
                    userId, startDate, endDate);

            // Calculate analytics
            ExpenseAnalytics analytics = analyticsService.calculateAnalytics(expenses, startDate, endDate);

            // Generate spending trends
            List<SpendingTrend> trends = analyticsService.calculateSpendingTrends(expenses, period);

            // Calculate category breakdown
            Map<String, BigDecimal> categoryBreakdown = analyticsService.calculateCategoryBreakdown(expenses);

            // Generate insights and recommendations
            List<ExpenseInsight> insights = analyticsService.generateInsights(expenses, analytics);

            return ExpenseAnalyticsResponse.builder()
                    .analytics(analytics)
                    .spendingTrends(trends)
                    .categoryBreakdown(categoryBreakdown)
                    .insights(insights)
                    .period(period)
                    .startDate(startDate)
                    .endDate(endDate)
                    .generatedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate expense analytics for user: {}", userId, e);
            throw new ExpenseValidationException("Failed to generate analytics", e);
        }
    }

    /**
     * Process bulk expense import
     */
    @Transactional
    @Async
    public CompletableFuture<BulkExpenseResult> processBulkExpenses(String userId, BulkExpenseRequest request) {
        log.info("Processing bulk expense import for user: {} with {} expenses", 
                userId, request.getExpenses().size());

        try {
            BulkExpenseResult result = new BulkExpenseResult();
            List<ExpenseResponse> successfulExpenses = new ArrayList<>();
            List<BulkExpenseError> errors = new ArrayList<>();

            for (int i = 0; i < request.getExpenses().size(); i++) {
                try {
                    ExpenseRequest expenseRequest = request.getExpenses().get(i);
                    ExpenseResponse created = self.createExpense(userId, expenseRequest);
                    successfulExpenses.add(created);
                    result.incrementSuccessCount();

                } catch (Exception e) {
                    errors.add(new BulkExpenseError(i, e.getMessage()));
                    result.incrementErrorCount();
                    log.warn("Failed to process expense at index {}: {}", i, e.getMessage());
                }
            }

            result.setSuccessfulExpenses(successfulExpenses);
            result.setErrors(errors);

            // Send completion notification
            notificationService.sendBulkImportCompleteNotification(userId, result);

            log.info("Bulk expense import completed for user: {} - Success: {} Errors: {}", 
                    userId, result.getSuccessCount(), result.getErrorCount());

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("Failed to process bulk expenses for user: {}", userId, e);
            throw new ExpenseValidationException("Failed to process bulk expenses", e);
        }
    }

    /**
     * Delete expense and adjust budgets
     */
    @Transactional
    public void deleteExpense(String userId, String expenseId) {
        log.info("Deleting expense: {} for user: {}", expenseId, userId);

        try {
            Expense expense = expenseRepository.findByIdAndUserId(expenseId, userId)
                    .orElseThrow(() -> new ExpenseNotFoundException("Expense not found: " + expenseId));

            // Remove from budget if assigned
            if (expense.getBudget() != null) {
                expense.getBudget().removeExpense(expense.getAmount());
                budgetRepository.save(expense.getBudget());
            }

            // Update category statistics
            if (expense.getCategory() != null) {
                categoryService.removeCategoryUsage(expense.getCategory().getId(), expense.getAmount());
            }

            // Delete expense
            expenseRepository.delete(expense);

            // Publish expense deleted event
            publishExpenseEvent("expense-deleted", expense);

            log.info("Expense deleted successfully: {}", expenseId);

        } catch (Exception e) {
            log.error("Failed to delete expense: {} for user: {}", expenseId, userId, e);
            throw new ExpenseValidationException("Failed to delete expense", e);
        }
    }

    /**
     * Get expense insights and recommendations
     */
    @Transactional(readOnly = true)
    public ExpenseInsightsResponse getExpenseInsights(String userId) {
        log.info("Generating expense insights for user: {}", userId);

        try {
            // Get recent expenses (last 90 days)
            LocalDate startDate = LocalDate.now().minusDays(90);
            List<Expense> recentExpenses = expenseRepository.findByUserIdAndExpenseDateAfter(userId, startDate);

            // Generate comprehensive insights
            List<ExpenseInsight> spendingInsights = analyticsService.generateSpendingInsights(recentExpenses);
            List<BudgetInsight> budgetInsights = budgetService.generateBudgetInsights(userId);
            List<CategoryInsight> categoryInsights = categoryService.generateCategoryInsights(userId);
            List<TrendInsight> trendInsights = analyticsService.generateTrendInsights(recentExpenses);

            // Generate personalized recommendations
            List<ExpenseRecommendation> recommendations = 
                    generatePersonalizedRecommendations(userId, recentExpenses);

            return ExpenseInsightsResponse.builder()
                    .spendingInsights(spendingInsights)
                    .budgetInsights(budgetInsights)
                    .categoryInsights(categoryInsights)
                    .trendInsights(trendInsights)
                    .recommendations(recommendations)
                    .generatedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate expense insights for user: {}", userId, e);
            throw new ExpenseValidationException("Failed to generate insights", e);
        }
    }

    // Private helper methods

    private void validateExpenseRequest(ExpenseRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ExpenseValidationException("Expense amount must be positive");
        }
        if (request.getDescription() == null || request.getDescription().trim().isEmpty()) {
            throw new ExpenseValidationException("Expense description is required");
        }
        if (request.getExpenseDate() != null && request.getExpenseDate().isAfter(LocalDate.now())) {
            throw new ExpenseValidationException("Expense date cannot be in the future");
        }
    }

    private Expense buildExpenseFromRequest(String userId, ExpenseRequest request) {
        Expense.ExpenseBuilder builder = Expense.builder()
                .userId(userId)
                .description(request.getDescription())
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .expenseDate(request.getExpenseDate() != null ? request.getExpenseDate() : LocalDate.now())
                .expenseType(request.getExpenseType() != null ? request.getExpenseType() : ExpenseType.PURCHASE)
                .paymentMethod(request.getPaymentMethod())
                .merchantName(request.getMerchantName())
                .merchantCategory(request.getMerchantCategory())
                .merchantId(request.getMerchantId())
                .locationCity(request.getLocationCity())
                .locationCountry(request.getLocationCountry())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .isRecurring(request.getIsRecurring())
                .recurringFrequency(request.getRecurringFrequency())
                .isReimbursable(request.getIsReimbursable())
                .isBusinessExpense(request.getIsBusinessExpense())
                .taxDeductible(request.getTaxDeductible())
                .notes(request.getNotes())
                .transactionId(request.getTransactionId())
                .status(ExpenseStatus.PENDING);

        // Set category if provided
        if (request.getCategoryId() != null) {
            ExpenseCategory category = categoryService.getCategory(request.getCategoryId());
            builder.category(category);
        }

        // Add tags
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            builder.tags(new ArrayList<>(request.getTags()));
        }

        // Add attachments
        if (request.getAttachmentUrls() != null && !request.getAttachmentUrls().isEmpty()) {
            builder.attachmentUrls(new ArrayList<>(request.getAttachmentUrls()));
        }

        return builder.build();
    }

    private void checkAndUpdateBudgets(Expense expense) {
        // Find applicable budgets
        List<Budget> applicableBudgets = budgetService.findApplicableBudgets(
                expense.getUserId(), expense.getCategory(), expense.getExpenseDate());

        for (Budget budget : applicableBudgets) {
            // Check if expense fits within budget
            if (budget.isOverBudget(expense.getAmount())) {
                expense.flagForReview("Would exceed budget: " + budget.getName());
            } else {
                // Add expense to budget
                budget.addExpense(expense.getAmount());
                expense.setBudget(budget);
                budgetRepository.save(budget);
            }
        }
    }

    private void sendBudgetAlerts(Expense expense) {
        if (expense.getBudget() != null) {
            Budget budget = expense.getBudget();
            
            if (budget.isCriticalThresholdReached()) {
                notificationService.sendBudgetCriticalAlert(expense.getUserId(), budget);
                publishBudgetAlert(budget, "CRITICAL_THRESHOLD_REACHED");
            } else if (budget.isWarningThresholdReached()) {
                notificationService.sendBudgetWarningAlert(expense.getUserId(), budget);
                publishBudgetAlert(budget, "WARNING_THRESHOLD_REACHED");
            }

            if (budget.isOverBudget()) {
                notificationService.sendBudgetExceededAlert(expense.getUserId(), budget);
                publishBudgetAlert(budget, "BUDGET_EXCEEDED");
            }
        }
    }

    @Async
    private void generateExpenseInsightsAsync(String userId, Expense expense) {
        // Generate insights in background
        try {
            analyticsService.updateUserSpendingPatterns(userId, expense);
            analyticsService.checkForAnomalies(userId, expense);
            analyticsService.updateMerchantInsights(userId, expense);
        } catch (Exception e) {
            log.warn("Failed to generate insights for expense: {}", expense.getId(), e);
        }
    }

    private ExpenseResponse mapToExpenseResponse(Expense expense) {
        return ExpenseResponse.builder()
                .id(expense.getId())
                .userId(expense.getUserId())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .currency(expense.getCurrency())
                .expenseDate(expense.getExpenseDate())
                .category(expense.getCategory() != null ? 
                        categoryService.mapToCategoryResponse(expense.getCategory()) : null)
                .expenseType(expense.getExpenseType())
                .paymentMethod(expense.getPaymentMethod())
                .status(expense.getStatus())
                .merchantName(expense.getMerchantName())
                .merchantCategory(expense.getMerchantCategory())
                .isRecurring(expense.getIsRecurring())
                .isReimbursable(expense.getIsReimbursable())
                .isBusinessExpense(expense.getIsBusinessExpense())
                .taxDeductible(expense.getTaxDeductible())
                .autoCategorized(expense.getAutoCategorized())
                .confidenceScore(expense.getConfidenceScore())
                .needsReview(expense.getNeedsReview())
                .reviewReason(expense.getReviewReason())
                .notes(expense.getNotes())
                .tags(expense.getTags())
                .attachmentUrls(expense.getAttachmentUrls())
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .build();
    }

    private void publishExpenseEvent(String topic, Expense expense) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("expenseId", expense.getId());
            event.put("userId", expense.getUserId());
            event.put("amount", expense.getAmount());
            event.put("categoryId", expense.getCategory() != null ? expense.getCategory().getId() : null);
            event.put("expenseDate", expense.getExpenseDate());
            event.put("timestamp", LocalDateTime.now());

            kafkaTemplate.send(topic, expense.getId(), event);
        } catch (Exception e) {
            log.warn("Failed to publish expense event to topic: {}", topic, e);
        }
    }

    private void publishBudgetAlert(Budget budget, String alertType) {
        try {
            Map<String, Object> alert = new HashMap<>();
            alert.put("budgetId", budget.getId());
            alert.put("userId", budget.getUserId());
            alert.put("alertType", alertType);
            alert.put("budgetName", budget.getName());
            alert.put("spentAmount", budget.getSpentAmount());
            alert.put("plannedAmount", budget.getPlannedAmount());
            alert.put("spendingPercentage", budget.getSpendingPercentage());
            alert.put("timestamp", LocalDateTime.now());

            kafkaTemplate.send(BUDGET_ALERT_TOPIC, budget.getId(), alert);
        } catch (Exception e) {
            log.warn("Failed to publish budget alert", e);
        }
    }

    // Placeholder classes and methods for complex types
    private static class ExpenseAnalytics {
        // Analytics implementation
    }

    private static class SpendingTrend {
        // Spending trend implementation
    }

    private static class ExpenseInsight {
        // Insight implementation
    }

    private static class BulkExpenseResult {
        private int successCount = 0;
        private int errorCount = 0;
        private List<ExpenseResponse> successfulExpenses;
        private List<BulkExpenseError> errors;

        public void incrementSuccessCount() { successCount++; }
        public void incrementErrorCount() { errorCount++; }
        
        // Getters and setters
        public int getSuccessCount() { return successCount; }
        public int getErrorCount() { return errorCount; }
        public List<ExpenseResponse> getSuccessfulExpenses() { return successfulExpenses; }
        public void setSuccessfulExpenses(List<ExpenseResponse> successfulExpenses) { 
            this.successfulExpenses = successfulExpenses; 
        }
        public List<BulkExpenseError> getErrors() { return errors; }
        public void setErrors(List<BulkExpenseError> errors) { this.errors = errors; }
    }

    private static class BulkExpenseError {
        private final int index;
        private final String message;

        public BulkExpenseError(int index, String message) {
            this.index = index;
            this.message = message;
        }

        public int getIndex() { return index; }
        public String getMessage() { return message; }
    }

    // Expense update and management methods
    private void updateExpenseFromRequest(Expense expense, ExpenseRequest request) {
        log.debug("Updating expense {} with new request data", expense.getId());
        
        try {
            // Update basic fields
            if (request.getDescription() != null && !request.getDescription().trim().isEmpty()) {
                expense.setDescription(request.getDescription().trim());
            }
            
            if (request.getAmount() != null && request.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                expense.setAmount(request.getAmount());
            }
            
            if (request.getCurrency() != null && !request.getCurrency().trim().isEmpty()) {
                expense.setCurrency(request.getCurrency().trim().toUpperCase());
            }
            
            if (request.getExpenseDate() != null) {
                expense.setExpenseDate(request.getExpenseDate());
            }
            
            // Update category if explicitly provided
            if (request.getCategoryId() != null) {
                try {
                    ExpenseCategory newCategory = categoryService.getCategory(request.getCategoryId());
                    expense.setCategory(newCategory);
                    expense.setAutoCategorized(false); // User explicitly set category
                } catch (Exception e) {
                    log.warn("Failed to update category to: {} for expense: {}", request.getCategoryId(), expense.getId());
                }
            }
            
            // Update expense type
            if (request.getExpenseType() != null) {
                expense.setExpenseType(request.getExpenseType());
            }
            
            // Update payment method
            if (request.getPaymentMethod() != null && !request.getPaymentMethod().trim().isEmpty()) {
                expense.setPaymentMethod(request.getPaymentMethod().trim());
            }
            
            // Update merchant information
            if (request.getMerchantName() != null && !request.getMerchantName().trim().isEmpty()) {
                expense.setMerchantName(request.getMerchantName().trim());
            }
            
            if (request.getMerchantCategory() != null && !request.getMerchantCategory().trim().isEmpty()) {
                expense.setMerchantCategory(request.getMerchantCategory().trim());
            }
            
            if (request.getMerchantId() != null && !request.getMerchantId().trim().isEmpty()) {
                expense.setMerchantId(request.getMerchantId().trim());
            }
            
            // Update location information
            if (request.getLocationCity() != null && !request.getLocationCity().trim().isEmpty()) {
                expense.setLocationCity(request.getLocationCity().trim());
            }
            
            if (request.getLocationCountry() != null && !request.getLocationCountry().trim().isEmpty()) {
                expense.setLocationCountry(request.getLocationCountry().trim());
            }
            
            if (request.getLatitude() != null) {
                expense.setLatitude(request.getLatitude());
            }
            
            if (request.getLongitude() != null) {
                expense.setLongitude(request.getLongitude());
            }
            
            // Update recurring settings
            if (request.getIsRecurring() != null) {
                expense.setIsRecurring(request.getIsRecurring());
                
                if (Boolean.TRUE.equals(request.getIsRecurring()) && request.getRecurringFrequency() != null) {
                    expense.setRecurringFrequency(request.getRecurringFrequency());
                } else if (Boolean.FALSE.equals(request.getIsRecurring())) {
                    expense.setRecurringFrequency(null);
                }
            }
            
            // Update expense classification flags
            if (request.getIsReimbursable() != null) {
                expense.setIsReimbursable(request.getIsReimbursable());
            }
            
            if (request.getIsBusinessExpense() != null) {
                expense.setIsBusinessExpense(request.getIsBusinessExpense());
            }
            
            if (request.getTaxDeductible() != null) {
                expense.setTaxDeductible(request.getTaxDeductible());
            }
            
            // Update notes
            if (request.getNotes() != null) {
                expense.setNotes(request.getNotes().trim().isEmpty() ? null : request.getNotes().trim());
            }
            
            // Update tags (replace existing tags)
            if (request.getTags() != null) {
                List<String> cleanTags = request.getTags().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(tag -> !tag.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
                expense.setTags(cleanTags.isEmpty() ? null : cleanTags);
            }
            
            // Update attachment URLs (replace existing attachments)
            if (request.getAttachmentUrls() != null) {
                List<String> cleanUrls = request.getAttachmentUrls().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(url -> !url.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
                expense.setAttachmentUrls(cleanUrls.isEmpty() ? null : cleanUrls);
            }
            
            // Update transaction reference if provided
            if (request.getTransactionId() != null && !request.getTransactionId().trim().isEmpty()) {
                expense.setTransactionId(request.getTransactionId().trim());
            }
            
            // Mark as updated
            expense.setUpdatedAt(LocalDateTime.now());
            
            // Clear any review flags since user has explicitly updated
            expense.setNeedsReview(false);
            expense.setReviewReason(null);
            
            log.debug("Successfully updated expense {} with request data", expense.getId());
            
        } catch (Exception e) {
            log.error("Failed to update expense {} from request", expense.getId(), e);
            throw new ExpenseValidationException("Failed to update expense fields: " + e.getMessage(), e);
        }
    }

    private boolean hasSignificantChanges(Expense expense, ExpenseRequest request) {
        log.debug("Checking for significant changes in expense {} that warrant re-categorization", expense.getId());
        
        try {
            // Track if any significant changes are detected
            boolean hasSignificantChange = false;
            
            // 1. Check if amount changed significantly (>20% or >$50)
            if (request.getAmount() != null && expense.getAmount() != null) {
                BigDecimal oldAmount = expense.getAmount();
                BigDecimal newAmount = request.getAmount();
                BigDecimal amountDifference = newAmount.subtract(oldAmount).abs();
                BigDecimal percentageChange = amountDifference.divide(oldAmount, 4, RoundingMode.HALF_UP);
                
                // Significant if >20% change OR absolute difference >$50
                if (percentageChange.compareTo(new BigDecimal("0.20")) > 0 || 
                    amountDifference.compareTo(new BigDecimal("50.00")) > 0) {
                    log.debug("Significant amount change detected: {} -> {} ({}% change)", 
                            oldAmount, newAmount, percentageChange.multiply(new BigDecimal("100")));
                    hasSignificantChange = true;
                }
            }
            
            // 2. Check if description changed substantially
            if (request.getDescription() != null && expense.getDescription() != null) {
                String oldDesc = expense.getDescription().toLowerCase().trim();
                String newDesc = request.getDescription().toLowerCase().trim();
                
                // Calculate simple similarity - consider significant if <60% similar
                double similarity = calculateStringSimilarity(oldDesc, newDesc);
                if (similarity < 0.6) {
                    log.debug("Significant description change detected: '{}' -> '{}' ({}% similarity)", 
                            oldDesc, newDesc, similarity * 100);
                    hasSignificantChange = true;
                }
                
                // Check for key category-indicating words
                if (hasNewCategoryKeywords(oldDesc, newDesc)) {
                    log.debug("New category keywords detected in description change");
                    hasSignificantChange = true;
                }
            }
            
            // 3. Check if merchant name changed
            if (request.getMerchantName() != null && expense.getMerchantName() != null) {
                String oldMerchant = expense.getMerchantName().toLowerCase().trim();
                String newMerchant = request.getMerchantName().toLowerCase().trim();
                
                if (!oldMerchant.equals(newMerchant)) {
                    // Different merchant likely means different category
                    double merchantSimilarity = calculateStringSimilarity(oldMerchant, newMerchant);
                    if (merchantSimilarity < 0.7) {
                        log.debug("Significant merchant change detected: '{}' -> '{}'", 
                                oldMerchant, newMerchant);
                        hasSignificantChange = true;
                    }
                }
            }
            
            // 4. Check if merchant category changed
            if (request.getMerchantCategory() != null && expense.getMerchantCategory() != null) {
                String oldCategory = expense.getMerchantCategory().toLowerCase().trim();
                String newCategory = request.getMerchantCategory().toLowerCase().trim();
                
                if (!oldCategory.equals(newCategory)) {
                    log.debug("Merchant category changed: '{}' -> '{}'", oldCategory, newCategory);
                    hasSignificantChange = true;
                }
            }
            
            // 5. Check if expense type changed
            if (request.getExpenseType() != null && expense.getExpenseType() != null) {
                if (!request.getExpenseType().equals(expense.getExpenseType())) {
                    log.debug("Expense type changed: {} -> {}", 
                            expense.getExpenseType(), request.getExpenseType());
                    hasSignificantChange = true;
                }
            }
            
            // 6. Check if payment method changed (some payment methods indicate different categories)
            if (request.getPaymentMethod() != null && expense.getPaymentMethod() != null) {
                String oldMethod = expense.getPaymentMethod().toLowerCase().trim();
                String newMethod = request.getPaymentMethod().toLowerCase().trim();
                
                // Certain payment method changes suggest category changes
                if (indicatesCategoryChange(oldMethod, newMethod)) {
                    log.debug("Payment method change suggests category change: '{}' -> '{}'", 
                            oldMethod, newMethod);
                    hasSignificantChange = true;
                }
            }
            
            // 7. Check if location changed significantly (different city/country might mean different business)
            if (hasLocationChanged(expense, request)) {
                log.debug("Significant location change detected");
                hasSignificantChange = true;
            }
            
            // 8. Check if recurring status changed (non-recurring to recurring or vice versa)
            if (request.getIsRecurring() != null && expense.getIsRecurring() != null) {
                if (!request.getIsRecurring().equals(expense.getIsRecurring())) {
                    log.debug("Recurring status changed: {} -> {}", 
                            expense.getIsRecurring(), request.getIsRecurring());
                    hasSignificantChange = true;
                }
            }
            
            log.debug("Significant changes analysis for expense {}: {}", 
                    expense.getId(), hasSignificantChange ? "DETECTED" : "NONE");
            
            return hasSignificantChange;
            
        } catch (Exception e) {
            log.warn("Error checking for significant changes in expense {}: {}", expense.getId(), e.getMessage());
            // Default to true to be safe - better to re-categorize unnecessarily than miss important changes
            return true;
        }
    }

    private void adjustBudgetsForUpdate(Expense expense, BigDecimal originalAmount, Budget originalBudget) {
        log.debug("Adjusting budgets for updated expense {} - Original amount: {}, New amount: {}", 
                expense.getId(), originalAmount, expense.getAmount());
        
        try {
            // 1. Remove expense from original budget if it had one
            if (originalBudget != null) {
                log.debug("Removing original amount {} from budget: {}", originalAmount, originalBudget.getName());
                originalBudget.removeExpense(originalAmount);
                budgetRepository.save(originalBudget);
                
                // Check if budget alert thresholds changed due to removal
                checkBudgetAlertsAfterRemoval(originalBudget, originalAmount);
            }
            
            // 2. Find applicable budgets for the updated expense
            List<Budget> applicableBudgets = budgetService.findApplicableBudgets(
                    expense.getUserId(), expense.getCategory(), expense.getExpenseDate());
            
            Budget bestMatchBudget = null;
            
            // 3. Select the most appropriate budget
            if (!applicableBudgets.isEmpty()) {
                // Priority 1: Budget that matches category exactly
                bestMatchBudget = applicableBudgets.stream()
                    .filter(budget -> budget.getCategory() != null && 
                                    budget.getCategory().equals(expense.getCategory()))
                    .findFirst()
                    .orElse(null);
                
                // Priority 2: Budget that covers the date range and has capacity
                if (bestMatchBudget == null) {
                    bestMatchBudget = applicableBudgets.stream()
                        .filter(budget -> budget.hasCapacityForAmount(expense.getAmount()))
                        .min(Comparator.comparing(Budget::getRemainingAmount))
                        .orElse(null);
                }
                
                // Priority 3: Any applicable budget (might go over limit)
                if (bestMatchBudget == null) {
                    bestMatchBudget = applicableBudgets.get(0);
                }
            }
            
            // 4. Add expense to the selected budget
            if (bestMatchBudget != null) {
                log.debug("Adding updated expense to budget: {} with amount: {}", 
                        bestMatchBudget.getName(), expense.getAmount());
                
                // Check if this would exceed budget limits
                if (bestMatchBudget.isOverBudget(expense.getAmount())) {
                    expense.flagForReview("Would exceed budget: " + bestMatchBudget.getName() + 
                                        " by " + bestMatchBudget.calculateExcessAmount(expense.getAmount()));
                    log.warn("Expense {} would exceed budget {} after update", 
                            expense.getId(), bestMatchBudget.getName());
                } else {
                    // Add to budget
                    bestMatchBudget.addExpense(expense.getAmount());
                    expense.setBudget(bestMatchBudget);
                    
                    // Clear any previous review flags related to budget
                    if (expense.getReviewReason() != null && 
                        expense.getReviewReason().contains("budget")) {
                        expense.setNeedsReview(false);
                        expense.setReviewReason(null);
                    }
                }
                
                // Save updated budget
                budgetRepository.save(bestMatchBudget);
                
                // Check for budget alerts after adding
                checkBudgetAlertsAfterAddition(bestMatchBudget, expense.getAmount());
                
            } else {
                // No applicable budget found
                expense.setBudget(null);
                log.debug("No applicable budget found for updated expense: {}", expense.getId());
                
                // Create suggestion for budget creation if this is a significant expense
                if (expense.getAmount().compareTo(new BigDecimal("100")) > 0) {
                    suggestBudgetCreation(expense);
                }
            }
            
            // 5. Handle budget transfers if the category changed significantly
            if (originalBudget != null && bestMatchBudget != null && 
                !originalBudget.equals(bestMatchBudget)) {
                
                // Log budget transfer
                log.info("Expense {} transferred from budget '{}' to budget '{}' due to update", 
                        expense.getId(), originalBudget.getName(), bestMatchBudget.getName());
                
                // Send notification about budget transfer
                notificationService.sendBudgetTransferNotification(
                    expense.getUserId(), expense, originalBudget, bestMatchBudget);
            }
            
            // 6. Recalculate budget utilization and projections
            if (bestMatchBudget != null) {
                budgetService.recalculateBudgetUtilization(bestMatchBudget);
            }
            if (originalBudget != null && !originalBudget.equals(bestMatchBudget)) {
                budgetService.recalculateBudgetUtilization(originalBudget);
            }
            
            log.debug("Successfully adjusted budgets for expense update: {}", expense.getId());
            
        } catch (Exception e) {
            log.error("Failed to adjust budgets for expense update: {}", expense.getId(), e);
            // Don't throw exception here - budget adjustment failure shouldn't prevent expense update
            // Flag for manual review instead
            expense.flagForReview("Budget adjustment failed during update: " + e.getMessage());
        }
    }

    private Expense.ExpenseSearchCriteria buildSearchCriteria(ExpenseSearchRequest request) {
        log.debug("Building search criteria from request: {}", request);
        
        try {
            Expense.ExpenseSearchCriteria.ExpenseSearchCriteriaBuilder criteriaBuilder = 
                Expense.ExpenseSearchCriteria.builder();
            
            // Basic filters
            if (request.getStartDate() != null) {
                criteriaBuilder.startDate(request.getStartDate());
            }
            
            if (request.getEndDate() != null) {
                criteriaBuilder.endDate(request.getEndDate());
            }
            
            // Amount range filters
            if (request.getMinAmount() != null) {
                criteriaBuilder.minAmount(request.getMinAmount());
            }
            
            if (request.getMaxAmount() != null) {
                criteriaBuilder.maxAmount(request.getMaxAmount());
            }
            
            // Category filters
            if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
                // Validate and filter out invalid category IDs
                List<String> validCategoryIds = request.getCategoryIds().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(id -> !id.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
                
                if (!validCategoryIds.isEmpty()) {
                    criteriaBuilder.categoryIds(validCategoryIds);
                }
            }
            
            // Status filters
            if (request.getStatuses() != null && !request.getStatuses().isEmpty()) {
                List<ExpenseStatus> validStatuses = request.getStatuses().stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
                
                if (!validStatuses.isEmpty()) {
                    criteriaBuilder.statuses(validStatuses);
                }
            }
            
            // Expense type filters
            if (request.getExpenseTypes() != null && !request.getExpenseTypes().isEmpty()) {
                List<ExpenseType> validTypes = request.getExpenseTypes().stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
                
                if (!validTypes.isEmpty()) {
                    criteriaBuilder.expenseTypes(validTypes);
                }
            }
            
            // Payment method filters
            if (request.getPaymentMethods() != null && !request.getPaymentMethods().isEmpty()) {
                List<String> validPaymentMethods = request.getPaymentMethods().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(method -> !method.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
                
                if (!validPaymentMethods.isEmpty()) {
                    criteriaBuilder.paymentMethods(validPaymentMethods);
                }
            }
            
            // Merchant filters
            if (request.getMerchantNames() != null && !request.getMerchantNames().isEmpty()) {
                List<String> validMerchantNames = request.getMerchantNames().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(name -> !name.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
                
                if (!validMerchantNames.isEmpty()) {
                    criteriaBuilder.merchantNames(validMerchantNames);
                }
            }
            
            // Text search (description/notes)
            if (request.getSearchText() != null && !request.getSearchText().trim().isEmpty()) {
                String searchText = request.getSearchText().trim();
                criteriaBuilder.searchText(searchText);
                
                // Enable fuzzy search for text queries
                if (searchText.length() > 3) {
                    criteriaBuilder.enableFuzzySearch(true);
                }
            }
            
            // Tag filters
            if (request.getTags() != null && !request.getTags().isEmpty()) {
                List<String> validTags = request.getTags().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(tag -> !tag.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
                
                if (!validTags.isEmpty()) {
                    criteriaBuilder.tags(validTags);
                    
                    // Set tag match mode (default to ANY)
                    TagMatchMode matchMode = request.getTagMatchMode() != null ? 
                        request.getTagMatchMode() : TagMatchMode.ANY;
                    criteriaBuilder.tagMatchMode(matchMode);
                }
            }
            
            // Boolean filters
            if (request.getIsRecurring() != null) {
                criteriaBuilder.isRecurring(request.getIsRecurring());
            }
            
            if (request.getIsReimbursable() != null) {
                criteriaBuilder.isReimbursable(request.getIsReimbursable());
            }
            
            if (request.getIsBusinessExpense() != null) {
                criteriaBuilder.isBusinessExpense(request.getIsBusinessExpense());
            }
            
            if (request.getTaxDeductible() != null) {
                criteriaBuilder.taxDeductible(request.getTaxDeductible());
            }
            
            if (request.getNeedsReview() != null) {
                criteriaBuilder.needsReview(request.getNeedsReview());
            }
            
            if (request.getAutoCategorized() != null) {
                criteriaBuilder.autoCategorized(request.getAutoCategorized());
            }
            
            // Location filters
            if (request.getLocationCity() != null && !request.getLocationCity().trim().isEmpty()) {
                criteriaBuilder.locationCity(request.getLocationCity().trim());
            }
            
            if (request.getLocationCountry() != null && !request.getLocationCountry().trim().isEmpty()) {
                criteriaBuilder.locationCountry(request.getLocationCountry().trim());
            }
            
            // Budget filters
            if (request.getBudgetIds() != null && !request.getBudgetIds().isEmpty()) {
                List<String> validBudgetIds = request.getBudgetIds().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(id -> !id.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
                
                if (!validBudgetIds.isEmpty()) {
                    criteriaBuilder.budgetIds(validBudgetIds);
                }
            }
            
            // Confidence score range (for auto-categorized expenses)
            if (request.getMinConfidenceScore() != null) {
                criteriaBuilder.minConfidenceScore(request.getMinConfidenceScore());
            }
            
            if (request.getMaxConfidenceScore() != null) {
                criteriaBuilder.maxConfidenceScore(request.getMaxConfidenceScore());
            }
            
            // Advanced filters
            if (request.getHasAttachments() != null) {
                criteriaBuilder.hasAttachments(request.getHasAttachments());
            }
            
            if (request.getCreatedAfter() != null) {
                criteriaBuilder.createdAfter(request.getCreatedAfter());
            }
            
            if (request.getCreatedBefore() != null) {
                criteriaBuilder.createdBefore(request.getCreatedBefore());
            }
            
            if (request.getUpdatedAfter() != null) {
                criteriaBuilder.updatedAfter(request.getUpdatedAfter());
            }
            
            if (request.getUpdatedBefore() != null) {
                criteriaBuilder.updatedBefore(request.getUpdatedBefore());
            }
            
            // Sorting preferences
            if (request.getSortBy() != null) {
                criteriaBuilder.sortBy(request.getSortBy());
            } else {
                // Default sorting
                criteriaBuilder.sortBy(ExpenseSortBy.EXPENSE_DATE_DESC);
            }
            
            if (request.getSortDirection() != null) {
                criteriaBuilder.sortDirection(request.getSortDirection());
            }
            
            // Performance optimizations
            if (request.getIncludeCategory() != null) {
                criteriaBuilder.includeCategory(request.getIncludeCategory());
            } else {
                criteriaBuilder.includeCategory(true); // Default to include
            }
            
            if (request.getIncludeBudget() != null) {
                criteriaBuilder.includeBudget(request.getIncludeBudget());
            } else {
                criteriaBuilder.includeBudget(true); // Default to include
            }
            
            Expense.ExpenseSearchCriteria criteria = criteriaBuilder.build();
            
            log.debug("Built search criteria with {} active filters", countActiveFilters(criteria));
            return criteria;
            
        } catch (Exception e) {
            log.error("Failed to build search criteria from request", e);
            throw new ExpenseValidationException("Invalid search criteria: " + e.getMessage(), e);
        }
    }

    private ExpenseResponse enrichExpenseResponse(Expense expense) {
        log.debug("Enriching expense response for expense: {}", expense.getId());
        
        try {
            // Start with the base response
            ExpenseResponse baseResponse = mapToExpenseResponse(expense);
            
            // Create enriched response builder
            ExpenseResponse.ExpenseResponseBuilder enrichedBuilder = baseResponse.toBuilder();
            
            // 1. Add category insights if category exists
            if (expense.getCategory() != null) {
                try {
                    CategoryInsights categoryInsights = categoryService.getCategoryInsights(
                        expense.getCategory().getId(), expense.getUserId());
                    
                    enrichedBuilder
                        .categoryAverageAmount(categoryInsights.getAverageAmount())
                        .categoryMonthlyTotal(categoryInsights.getCurrentMonthTotal())
                        .categoryTransactionCount(categoryInsights.getTransactionCount())
                        .categoryBudgetStatus(categoryInsights.getBudgetStatus())
                        .categoryTrend(categoryInsights.getTrend());
                    
                } catch (Exception e) {
                    log.warn("Failed to enrich category insights for expense: {}", expense.getId(), e);
                }
            }
            
            // 2. Add budget context if expense is associated with a budget
            if (expense.getBudget() != null) {
                try {
                    Budget budget = expense.getBudget();
                    BigDecimal remainingAmount = budget.getRemainingAmount();
                    Double utilizationPercentage = budget.getUtilizationPercentage();
                    
                    enrichedBuilder
                        .budgetName(budget.getName())
                        .budgetRemainingAmount(remainingAmount)
                        .budgetUtilizationPercentage(utilizationPercentage)
                        .budgetStatus(budget.getStatus().name())
                        .daysUntilBudgetReset(budget.getDaysUntilReset());
                    
                    // Add budget alert context
                    if (budget.isOverBudget()) {
                        enrichedBuilder.budgetAlert("OVER_BUDGET");
                    } else if (budget.isWarningThresholdReached()) {
                        enrichedBuilder.budgetAlert("WARNING_THRESHOLD");
                    } else if (budget.isCriticalThresholdReached()) {
                        enrichedBuilder.budgetAlert("CRITICAL_THRESHOLD");
                    }
                    
                } catch (Exception e) {
                    log.warn("Failed to enrich budget context for expense: {}", expense.getId(), e);
                }
            }
            
            // 3. Add merchant insights
            if (expense.getMerchantName() != null && !expense.getMerchantName().trim().isEmpty()) {
                try {
                    MerchantInsights merchantInsights = analyticsService.getMerchantInsights(
                        expense.getMerchantName(), expense.getUserId());
                    
                    enrichedBuilder
                        .merchantTotalSpent(merchantInsights.getTotalSpent())
                        .merchantTransactionCount(merchantInsights.getTransactionCount())
                        .merchantAverageAmount(merchantInsights.getAverageAmount())
                        .merchantLastVisit(merchantInsights.getLastVisitDate())
                        .merchantFrequencyRank(merchantInsights.getFrequencyRank());
                    
                    // Add merchant comparison
                    if (merchantInsights.getAverageAmount() != null) {
                        BigDecimal currentAmount = expense.getAmount();
                        BigDecimal avgAmount = merchantInsights.getAverageAmount();
                        
                        if (currentAmount.compareTo(avgAmount.multiply(new BigDecimal("1.5"))) > 0) {
                            enrichedBuilder.merchantSpendingNote("ABOVE_AVERAGE");
                        } else if (currentAmount.compareTo(avgAmount.multiply(new BigDecimal("0.5"))) < 0) {
                            enrichedBuilder.merchantSpendingNote("BELOW_AVERAGE");
                        } else {
                            enrichedBuilder.merchantSpendingNote("TYPICAL");
                        }
                    }
                    
                } catch (Exception e) {
                    log.warn("Failed to enrich merchant insights for expense: {}", expense.getId(), e);
                }
            }
            
            // 4. Add location insights
            if (expense.getLocationCity() != null || expense.getLocationCountry() != null) {
                try {
                    LocationInsights locationInsights = analyticsService.getLocationInsights(
                        expense.getLocationCity(), expense.getLocationCountry(), expense.getUserId());
                    
                    enrichedBuilder
                        .locationSpendingTotal(locationInsights.getTotalSpent())
                        .locationFrequency(locationInsights.getVisitFrequency())
                        .locationAverageAmount(locationInsights.getAverageAmount());
                    
                    // Add location context
                    if (locationInsights.isNewLocation()) {
                        enrichedBuilder.locationNote("NEW_LOCATION");
                    } else if (locationInsights.isFrequentLocation()) {
                        enrichedBuilder.locationNote("FREQUENT_LOCATION");
                    }
                    
                } catch (Exception e) {
                    log.warn("Failed to enrich location insights for expense: {}", expense.getId(), e);
                }
            }
            
            // 5. Add recurring expense insights
            if (Boolean.TRUE.equals(expense.getIsRecurring())) {
                try {
                    RecurringExpenseInsights recurringInsights = analyticsService.getRecurringInsights(
                        expense.getDescription(), expense.getMerchantName(), expense.getUserId());
                    
                    enrichedBuilder
                        .recurringPatternConfidence(recurringInsights.getPatternConfidence())
                        .nextExpectedDate(recurringInsights.getNextExpectedDate())
                        .recurringVariance(recurringInsights.getAmountVariance())
                        .recurringFrequency(recurringInsights.getActualFrequency());
                    
                    // Add recurring alerts
                    if (recurringInsights.hasAmountAnomaly()) {
                        enrichedBuilder.recurringAlert("AMOUNT_ANOMALY");
                    } else if (recurringInsights.hasTimingAnomaly()) {
                        enrichedBuilder.recurringAlert("TIMING_ANOMALY");
                    }
                    
                } catch (Exception e) {
                    log.warn("Failed to enrich recurring insights for expense: {}", expense.getId(), e);
                }
            }
            
            // 6. Add tax implications
            if (Boolean.TRUE.equals(expense.getTaxDeductible()) || 
                Boolean.TRUE.equals(expense.getIsBusinessExpense())) {
                
                try {
                    TaxImplications taxInfo = analyticsService.calculateTaxImplications(expense);
                    
                    enrichedBuilder
                        .estimatedTaxSaving(taxInfo.getEstimatedSaving())
                        .taxCategory(taxInfo.getTaxCategory())
                        .requiresTaxDocumentation(taxInfo.requiresDocumentation())
                        .taxDeductionConfidence(taxInfo.getDeductionConfidence());
                    
                } catch (Exception e) {
                    log.warn("Failed to calculate tax implications for expense: {}", expense.getId(), e);
                }
            }
            
            // 7. Add spending trends
            try {
                SpendingTrends trends = analyticsService.calculateSpendingTrends(
                    expense.getUserId(), expense.getCategory(), expense.getExpenseDate());
                
                enrichedBuilder
                    .monthlyTrend(trends.getMonthlyTrend())
                    .weeklyTrend(trends.getWeeklyTrend())
                    .yearOverYearChange(trends.getYearOverYearChange())
                    .seasonalIndex(trends.getSeasonalIndex());
                
                // Add trend alerts
                if (trends.isSignificantIncrease()) {
                    enrichedBuilder.trendAlert("SIGNIFICANT_INCREASE");
                } else if (trends.isSignificantDecrease()) {
                    enrichedBuilder.trendAlert("SIGNIFICANT_DECREASE");
                }
                
            } catch (Exception e) {
                log.warn("Failed to calculate spending trends for expense: {}", expense.getId(), e);
            }
            
            // 8. Add smart suggestions
            try {
                List<ExpenseSmartSuggestion> suggestions = analyticsService.generateSmartSuggestions(expense);
                if (!suggestions.isEmpty()) {
                    enrichedBuilder.smartSuggestions(suggestions);
                }
                
            } catch (Exception e) {
                log.warn("Failed to generate smart suggestions for expense: {}", expense.getId(), e);
            }
            
            // 9. Add similar expenses context
            try {
                List<SimilarExpenseInfo> similarExpenses = analyticsService.findSimilarExpenses(
                    expense, 5); // Get top 5 similar expenses
                
                if (!similarExpenses.isEmpty()) {
                    enrichedBuilder.similarExpenses(similarExpenses);
                }
                
            } catch (Exception e) {
                log.warn("Failed to find similar expenses for expense: {}", expense.getId(), e);
            }
            
            // 10. Add environmental impact (if applicable)
            if (isEnvironmentallyRelevant(expense)) {
                try {
                    EnvironmentalImpact impact = analyticsService.calculateEnvironmentalImpact(expense);
                    
                    enrichedBuilder
                        .carbonFootprint(impact.getCarbonFootprint())
                        .environmentalScore(impact.getEnvironmentalScore())
                        .sustainabilityTips(impact.getSustainabilityTips());
                    
                } catch (Exception e) {
                    log.warn("Failed to calculate environmental impact for expense: {}", expense.getId(), e);
                }
            }
            
            // 11. Add enrichment metadata
            enrichedBuilder
                .enrichmentTimestamp(LocalDateTime.now())
                .enrichmentVersion("v2.1")
                .enrichmentSource("expense-tracking-service");
            
            log.debug("Successfully enriched expense response for expense: {}", expense.getId());
            return enrichedBuilder.build();
            
        } catch (Exception e) {
            log.error("Failed to enrich expense response for expense: {}", expense.getId(), e);
            // Return base response if enrichment fails
            return mapToExpenseResponse(expense);
        }
    }

    private LocalDate calculatePeriodStartDate(String period) {
        return switch (period.toUpperCase()) {
            case "LAST_7_DAYS" -> LocalDate.now().minusDays(7);
            case "LAST_30_DAYS" -> LocalDate.now().minusDays(30);
            case "LAST_90_DAYS" -> LocalDate.now().minusDays(90);
            case "THIS_MONTH" -> YearMonth.now().atDay(1);
            case "LAST_MONTH" -> YearMonth.now().minusMonths(1).atDay(1);
            case "THIS_YEAR" -> LocalDate.now().withDayOfYear(1);
            default -> LocalDate.now().minusDays(30);
        };
    }

    private List<ExpenseRecommendation> generatePersonalizedRecommendations(String userId, List<Expense> expenses) {
        List<ExpenseRecommendation> recommendations = new ArrayList<>();
        
        try {
            if (expenses.isEmpty()) {
                recommendations.add(new ExpenseRecommendation(
                    "START_TRACKING", 
                    "Start tracking your expenses to get personalized insights",
                    "Record your daily expenses to build spending patterns",
                    RecommendationType.GENERAL
                ));
                return recommendations;
            }
            
            // Analyze spending patterns
            Map<String, BigDecimal> categoryTotals = expenses.stream()
                .collect(Collectors.groupingBy(
                    Expense::getCategory,
                    Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)
                ));
            
            BigDecimal totalSpending = categoryTotals.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // 1. High-spending category recommendations
            categoryTotals.entrySet().stream()
                .filter(entry -> entry.getValue().divide(totalSpending, 2, RoundingMode.HALF_UP)
                        .compareTo(new BigDecimal("0.30")) > 0) // Categories > 30% of total
                .forEach(entry -> {
                    String category = entry.getKey();
                    BigDecimal amount = entry.getValue();
                    
                    recommendations.add(new ExpenseRecommendation(
                        "REDUCE_" + category.toUpperCase(),
                        "Consider reducing " + category + " expenses",
                        String.format("You spent $%.2f on %s this period (%.1f%% of total). " +
                                     "Try setting a budget limit or finding alternatives.",
                                     amount, category, 
                                     amount.divide(totalSpending, 3, RoundingMode.HALF_UP)
                                          .multiply(new BigDecimal("100"))),
                        RecommendationType.BUDGET_OPTIMIZATION
                    ));
                });
            
            // 2. Frequent small purchases recommendation
            Map<String, Long> categoryCount = expenses.stream()
                .collect(Collectors.groupingBy(Expense::getCategory, Collectors.counting()));
            
            categoryCount.entrySet().stream()
                .filter(entry -> entry.getValue() > 15) // More than 15 transactions in category
                .forEach(entry -> {
                    String category = entry.getKey();
                    BigDecimal avgAmount = categoryTotals.get(category)
                        .divide(new BigDecimal(entry.getValue()), 2, RoundingMode.HALF_UP);
                    
                    if (avgAmount.compareTo(new BigDecimal("25")) < 0) { // Small amounts
                        recommendations.add(new ExpenseRecommendation(
                            "CONSOLIDATE_" + category.toUpperCase(),
                            "Consolidate small " + category + " purchases",
                            String.format("You made %d small %s purchases (avg $%.2f). " +
                                         "Consider bulk buying or subscription services to save money.",
                                         entry.getValue(), category, avgAmount),
                            RecommendationType.EFFICIENCY
                        ));
                    }
                });
            
            // 3. Weekend vs weekday spending analysis
            Map<Boolean, BigDecimal> weekendSpending = expenses.stream()
                .collect(Collectors.partitioningBy(
                    expense -> isWeekend(expense.getDate()),
                    Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)
                ));
            
            BigDecimal weekendTotal = weekendSpending.get(true);
            BigDecimal weekdayTotal = weekendSpending.get(false);
            
            if (weekendTotal.compareTo(BigDecimal.ZERO) > 0 && weekdayTotal.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal weekendRatio = weekendTotal.divide(totalSpending, 2, RoundingMode.HALF_UP);
                if (weekendRatio.compareTo(new BigDecimal("0.40")) > 0) { // Weekend spending > 40%
                    recommendations.add(new ExpenseRecommendation(
                        "WEEKEND_SPENDING",
                        "Monitor weekend spending",
                        String.format("%.1f%% of your spending happens on weekends ($%.2f). " +
                                     "Plan weekend budgets to avoid overspending.",
                                     weekendRatio.multiply(new BigDecimal("100")), weekendTotal),
                        RecommendationType.BEHAVIORAL
                    ));
                }
            }
            
            // 4. Subscription and recurring expense recommendations
            List<Expense> potentialSubscriptions = expenses.stream()
                .filter(expense -> isLikelySubscription(expense))
                .collect(Collectors.toList());
            
            if (!potentialSubscriptions.isEmpty()) {
                BigDecimal subscriptionTotal = potentialSubscriptions.stream()
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                recommendations.add(new ExpenseRecommendation(
                    "REVIEW_SUBSCRIPTIONS",
                    "Review your subscriptions",
                    String.format("Found %d potential subscription expenses totaling $%.2f. " +
                                 "Cancel unused subscriptions to save money.",
                                 potentialSubscriptions.size(), subscriptionTotal),
                    RecommendationType.SUBSCRIPTION_AUDIT
                ));
            }
            
            // 5. Budget creation recommendations for unbudgeted categories
            Set<String> budgetedCategories = getBudgetedCategories(userId);
            Set<String> expenseCategories = categoryTotals.keySet();
            
            expenseCategories.stream()
                .filter(category -> !budgetedCategories.contains(category))
                .filter(category -> categoryTotals.get(category).compareTo(new BigDecimal("100")) > 0)
                .forEach(category -> {
                    recommendations.add(new ExpenseRecommendation(
                        "CREATE_BUDGET_" + category.toUpperCase(),
                        "Create budget for " + category,
                        String.format("You spent $%.2f on %s without a budget. " +
                                     "Set spending limits to better control expenses.",
                                     categoryTotals.get(category), category),
                        RecommendationType.BUDGET_CREATION
                    ));
                });
            
            // 6. Savings opportunities based on merchant analysis
            Map<String, BigDecimal> merchantSpending = expenses.stream()
                .collect(Collectors.groupingBy(
                    expense -> expense.getMerchantName() != null ? expense.getMerchantName() : "Unknown",
                    Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)
                ));
            
            merchantSpending.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(new BigDecimal("200")) > 0)
                .limit(3) // Top 3 merchants
                .forEach(entry -> {
                    recommendations.add(new ExpenseRecommendation(
                        "LOYALTY_PROGRAM",
                        "Check loyalty programs for " + entry.getKey(),
                        String.format("You spent $%.2f at %s. Look for cashback, rewards, or loyalty programs.",
                                     entry.getValue(), entry.getKey()),
                        RecommendationType.SAVINGS_OPPORTUNITY
                    ));
                });
            
            // 7. Emergency fund recommendation
            if (totalSpending.compareTo(new BigDecimal("1000")) > 0) {
                recommendations.add(new ExpenseRecommendation(
                    "EMERGENCY_FUND",
                    "Build an emergency fund",
                    String.format("Based on your $%.2f monthly spending, consider saving $%.2f " +
                                 "for emergencies (3-6 months of expenses).",
                                 totalSpending, totalSpending.multiply(new BigDecimal("3"))),
                    RecommendationType.FINANCIAL_HEALTH
                ));
            }
            
            // Sort recommendations by priority
            recommendations.sort((r1, r2) -> r1.getType().getPriority() - r2.getType().getPriority());
            
            log.debug("Generated {} personalized recommendations for user: {}", recommendations.size(), userId);
            
        } catch (Exception e) {
            log.error("Error generating personalized recommendations for user: {}", userId, e);
            recommendations.add(new ExpenseRecommendation(
                "ERROR_FALLBACK",
                "Review your spending patterns",
                "Analyze your expenses regularly to identify saving opportunities.",
                RecommendationType.GENERAL
            ));
        }
        
        return recommendations.stream().limit(10).collect(Collectors.toList()); // Limit to top 10
    }
    
    /**
     * Check if a date falls on weekend
     */
    private boolean isWeekend(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }
    
    /**
     * Check if an expense is likely a subscription
     */
    private boolean isLikelySubscription(Expense expense) {
        String description = expense.getDescription() != null ? expense.getDescription().toLowerCase() : "";
        String merchantName = expense.getMerchantName() != null ? expense.getMerchantName().toLowerCase() : "";
        
        // Common subscription indicators
        return description.contains("subscription") ||
               description.contains("monthly") ||
               description.contains("premium") ||
               merchantName.contains("netflix") ||
               merchantName.contains("spotify") ||
               merchantName.contains("amazon prime") ||
               merchantName.contains("apple") ||
               (expense.getAmount().remainder(new BigDecimal("9.99")).compareTo(BigDecimal.ZERO) == 0) ||
               (expense.getAmount().remainder(new BigDecimal("4.99")).compareTo(BigDecimal.ZERO) == 0);
    }
    
    /**
     * Get categories that already have budgets
     */
    private Set<String> getBudgetedCategories(String userId) {
        try {
            String budgetKey = "expense:budgets:categories:" + userId;
            Set<Object> budgetedCats = redisTemplate.opsForSet().members(budgetKey);
            
            if (budgetedCats != null && !budgetedCats.isEmpty()) {
                return budgetedCats.stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());
            }
            
            List<Expense> recentExpenses = expenseRepository
                .findByUserIdAndTimestampAfter(userId, LocalDateTime.now().minusMonths(3));
            
            Set<String> activeCategories = recentExpenses.stream()
                .map(Expense::getCategory)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            
            if (!activeCategories.isEmpty()) {
                redisTemplate.opsForSet().add(budgetKey, activeCategories.toArray());
                redisTemplate.expire(budgetKey, Duration.ofDays(30));
            }
            
            return activeCategories;
        } catch (Exception e) {
            log.warn("Could not retrieve budgeted categories for user: {}", userId, e);
            return Set.of("Food", "Transportation", "Entertainment", "Shopping", "Utilities");
        }
    }

    // DTOs for recommendation system
    private static class ExpenseRecommendation {
        private final String id;
        private final String title;
        private final String description;
        private final RecommendationType type;
        private final LocalDateTime createdAt;
        
        public ExpenseRecommendation(String id, String title, String description, RecommendationType type) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.type = type;
            this.createdAt = LocalDateTime.now();
        }
        
        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public RecommendationType getType() { return type; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }
    
    private enum RecommendationType {
        GENERAL(5),
        BUDGET_OPTIMIZATION(1),
        EFFICIENCY(3),
        BEHAVIORAL(2),
        SUBSCRIPTION_AUDIT(1),
        BUDGET_CREATION(2),
        SAVINGS_OPPORTUNITY(4),
        FINANCIAL_HEALTH(5);
        
        private final int priority;
        
        RecommendationType(int priority) {
            this.priority = priority;
        }
        
        public int getPriority() { return priority; }
    }
    
    // Helper methods for expense management
    
    /**
     * Calculate string similarity using Levenshtein distance
     */
    private double calculateStringSimilarity(String str1, String str2) {
        if (str1.equals(str2)) return 1.0;
        
        int maxLength = Math.max(str1.length(), str2.length());
        if (maxLength == 0) return 1.0;
        
        int distance = calculateLevenshteinDistance(str1, str2);
        return 1.0 - (double) distance / maxLength;
    }
    
    private int calculateLevenshteinDistance(String str1, String str2) {
        int[][] dp = new int[str1.length() + 1][str2.length() + 1];
        
        for (int i = 0; i <= str1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= str2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= str1.length(); i++) {
            for (int j = 1; j <= str2.length(); j++) {
                int cost = str1.charAt(i - 1) == str2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        
        return dp[str1.length()][str2.length()];
    }
    
    /**
     * Check if description changes include new category keywords
     */
    private boolean hasNewCategoryKeywords(String oldDesc, String newDesc) {
        Set<String> categoryKeywords = Set.of(
            "gas", "fuel", "grocery", "restaurant", "coffee", "uber", "taxi", "hotel", 
            "flight", "amazon", "netflix", "spotify", "gym", "pharmacy", "medical", 
            "insurance", "subscription", "rent", "mortgage", "utility", "electric",
            "shopping", "clothing", "entertainment", "movie", "book", "software"
        );
        
        Set<String> oldKeywords = Arrays.stream(oldDesc.split("\\s+"))
            .filter(categoryKeywords::contains)
            .collect(Collectors.toSet());
            
        Set<String> newKeywords = Arrays.stream(newDesc.split("\\s+"))
            .filter(categoryKeywords::contains)
            .collect(Collectors.toSet());
        
        // Check if new description has keywords that old one didn't
        return !newKeywords.equals(oldKeywords);
    }
    
    /**
     * Check if payment method change suggests category change
     */
    private boolean indicatesCategoryChange(String oldMethod, String newMethod) {
        Map<String, String> methodToCategory = Map.of(
            "cash", "miscellaneous",
            "credit", "general",
            "debit", "general", 
            "venmo", "personal",
            "paypal", "online",
            "apple pay", "mobile",
            "google pay", "mobile"
        );
        
        String oldCategory = methodToCategory.getOrDefault(oldMethod, "unknown");
        String newCategory = methodToCategory.getOrDefault(newMethod, "unknown");
        
        return !oldCategory.equals(newCategory);
    }
    
    /**
     * Check if location changed significantly
     */
    private boolean hasLocationChanged(Expense expense, ExpenseRequest request) {
        boolean cityChanged = false;
        boolean countryChanged = false;
        
        if (request.getLocationCity() != null && expense.getLocationCity() != null) {
            cityChanged = !request.getLocationCity().trim().equalsIgnoreCase(
                expense.getLocationCity().trim());
        }
        
        if (request.getLocationCountry() != null && expense.getLocationCountry() != null) {
            countryChanged = !request.getLocationCountry().trim().equalsIgnoreCase(
                expense.getLocationCountry().trim());
        }
        
        return cityChanged || countryChanged;
    }
    
    /**
     * Check budget alerts after removal
     */
    private void checkBudgetAlertsAfterRemoval(Budget budget, BigDecimal removedAmount) {
        try {
            // If budget was over and is now within limits
            if (budget.wasOverBudget() && !budget.isOverBudget()) {
                notificationService.sendBudgetBackOnTrackNotification(
                    budget.getUserId(), budget, removedAmount);
            }
        } catch (Exception e) {
            log.warn("Failed to check budget alerts after removal: {}", e.getMessage());
        }
    }
    
    /**
     * Check budget alerts after addition
     */
    private void checkBudgetAlertsAfterAddition(Budget budget, BigDecimal addedAmount) {
        try {
            if (budget.isCriticalThresholdReached()) {
                notificationService.sendBudgetCriticalAlert(budget.getUserId(), budget);
            } else if (budget.isWarningThresholdReached()) {
                notificationService.sendBudgetWarningAlert(budget.getUserId(), budget);
            }
            
            if (budget.isOverBudget()) {
                notificationService.sendBudgetExceededAlert(budget.getUserId(), budget);
            }
        } catch (Exception e) {
            log.warn("Failed to check budget alerts after addition: {}", e.getMessage());
        }
    }
    
    /**
     * Suggest budget creation for significant expenses
     */
    private void suggestBudgetCreation(Expense expense) {
        try {
            BudgetCreationSuggestion suggestion = BudgetCreationSuggestion.builder()
                .userId(expense.getUserId())
                .category(expense.getCategory())
                .suggestedAmount(expense.getAmount().multiply(new BigDecimal("4"))) // 4x monthly
                .reason("Significant expense without budget")
                .expenseId(expense.getId())
                .build();
                
            notificationService.sendBudgetCreationSuggestion(expense.getUserId(), suggestion);
        } catch (Exception e) {
            log.warn("Failed to suggest budget creation: {}", e.getMessage());
        }
    }
    
    /**
     * Count active filters in search criteria
     */
    private int countActiveFilters(Expense.ExpenseSearchCriteria criteria) {
        int count = 0;
        
        if (criteria.getStartDate() != null) count++;
        if (criteria.getEndDate() != null) count++;
        if (criteria.getMinAmount() != null) count++;
        if (criteria.getMaxAmount() != null) count++;
        if (criteria.getCategoryIds() != null && !criteria.getCategoryIds().isEmpty()) count++;
        if (criteria.getStatuses() != null && !criteria.getStatuses().isEmpty()) count++;
        if (criteria.getSearchText() != null && !criteria.getSearchText().trim().isEmpty()) count++;
        if (criteria.getTags() != null && !criteria.getTags().isEmpty()) count++;
        // Add other criteria counts...
        
        return count;
    }
    
    /**
     * Check if expense is environmentally relevant
     */
    private boolean isEnvironmentallyRelevant(Expense expense) {
        if (expense.getCategory() == null) return false;
        
        String categoryName = expense.getCategory().getName().toLowerCase();
        return categoryName.contains("transport") || 
               categoryName.contains("travel") ||
               categoryName.contains("gas") ||
               categoryName.contains("fuel") ||
               categoryName.contains("energy") ||
               categoryName.contains("utility");
    }
    
    // Placeholder classes for other return types
    private static class BudgetInsight {}
    private static class CategoryInsight {}
    private static class TrendInsight {}
    
    // Supporting classes for enrichment
    private static class CategoryInsights {
        private BigDecimal averageAmount;
        private BigDecimal currentMonthTotal;
        private Integer transactionCount;
        private String budgetStatus;
        private String trend;
        
        // Getters
        public BigDecimal getAverageAmount() { return averageAmount; }
        public BigDecimal getCurrentMonthTotal() { return currentMonthTotal; }
        public Integer getTransactionCount() { return transactionCount; }
        public String getBudgetStatus() { return budgetStatus; }
        public String getTrend() { return trend; }
    }
    
    private static class MerchantInsights {
        private BigDecimal totalSpent;
        private Integer transactionCount;
        private BigDecimal averageAmount;
        private LocalDate lastVisitDate;
        private Integer frequencyRank;
        
        // Getters
        public BigDecimal getTotalSpent() { return totalSpent; }
        public Integer getTransactionCount() { return transactionCount; }
        public BigDecimal getAverageAmount() { return averageAmount; }
        public LocalDate getLastVisitDate() { return lastVisitDate; }
        public Integer getFrequencyRank() { return frequencyRank; }
    }
    
    private static class LocationInsights {
        private BigDecimal totalSpent;
        private Integer visitFrequency;
        private BigDecimal averageAmount;
        private boolean newLocation;
        private boolean frequentLocation;
        
        // Getters
        public BigDecimal getTotalSpent() { return totalSpent; }
        public Integer getVisitFrequency() { return visitFrequency; }
        public BigDecimal getAverageAmount() { return averageAmount; }
        public boolean isNewLocation() { return newLocation; }
        public boolean isFrequentLocation() { return frequentLocation; }
    }
    
    private static class BudgetCreationSuggestion {
        private String userId;
        private ExpenseCategory category;
        private BigDecimal suggestedAmount;
        private String reason;
        private String expenseId;
        
        public static BudgetCreationSuggestionBuilder builder() {
            return new BudgetCreationSuggestionBuilder();
        }
        
        public static class BudgetCreationSuggestionBuilder {
            private BudgetCreationSuggestion suggestion = new BudgetCreationSuggestion();
            
            public BudgetCreationSuggestionBuilder userId(String userId) {
                suggestion.userId = userId;
                return this;
            }
            
            public BudgetCreationSuggestionBuilder category(ExpenseCategory category) {
                suggestion.category = category;
                return this;
            }
            
            public BudgetCreationSuggestionBuilder suggestedAmount(BigDecimal amount) {
                suggestion.suggestedAmount = amount;
                return this;
            }
            
            public BudgetCreationSuggestionBuilder reason(String reason) {
                suggestion.reason = reason;
                return this;
            }
            
            public BudgetCreationSuggestionBuilder expenseId(String expenseId) {
                suggestion.expenseId = expenseId;
                return this;
            }
            
            public BudgetCreationSuggestion build() {
                return suggestion;
            }
        }
    }
}