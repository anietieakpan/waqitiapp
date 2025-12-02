package com.waqiti.savings.controller;

import com.waqiti.common.api.ApiResponse;
import com.waqiti.savings.dto.*;
import com.waqiti.savings.service.SavingsAccountService;
import com.waqiti.savings.service.SavingsGoalService;
import com.waqiti.savings.service.AutoSaveService;
import com.waqiti.common.security.SecurityContextUtil;
import com.waqiti.common.security.ResourceOwnershipAspect.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/savings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Savings Management", description = "Comprehensive savings accounts, goals, and automation")
public class SavingsController {

    private final SavingsAccountService savingsAccountService;
    private final SavingsGoalService savingsGoalService;
    private final AutoSaveService autoSaveService;

    // Savings Account Management
    @PostMapping("/accounts")
    @Operation(summary = "Create a new savings account")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<SavingsAccountResponse>> createSavingsAccount(
            @Valid @RequestBody CreateSavingsAccountRequest request) {
        
        // SECURITY FIX: Override user ID with authenticated user
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();
        request.setUserId(authenticatedUserId);
        log.info("Creating savings account: {} for user: {}", 
                request.getAccountName(), request.getUserId());
        
        SavingsAccountResponse response = savingsAccountService.createSavingsAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/accounts/{accountId}")
    @Operation(summary = "Get savings account details")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.SAVINGS_ACCOUNT, resourceIdParam = "accountId", operation = "VIEW")
    public ResponseEntity<ApiResponse<SavingsAccountResponse>> getSavingsAccount(
            @PathVariable UUID accountId) {
        log.info("Retrieving savings account: {}", accountId);
        
        SavingsAccountResponse response = savingsAccountService.getSavingsAccount(accountId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/users/{userId}/accounts")
    @Operation(summary = "Get all savings accounts for a user")
    @PreAuthorize("@userOwnershipValidator.isCurrentUser(authentication.name, #userId) or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SavingsAccountResponse>>> getUserSavingsAccounts(
            @PathVariable UUID userId) {
        
        // SECURITY FIX: Override user ID with authenticated user for non-admin
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();
        if (!SecurityContextUtil.hasRole("ADMIN")) {
            userId = authenticatedUserId;
        }
        log.info("Retrieving savings accounts for user: {}", userId);
        
        List<SavingsAccountResponse> response = savingsAccountService.getUserSavingsAccounts(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/accounts/{accountId}")
    @Operation(summary = "Update savings account")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.SAVINGS_ACCOUNT, resourceIdParam = "accountId", operation = "UPDATE")
    public ResponseEntity<ApiResponse<SavingsAccountResponse>> updateSavingsAccount(
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateSavingsAccountRequest request) {
        log.info("Updating savings account: {}", accountId);
        
        SavingsAccountResponse response = savingsAccountService.updateSavingsAccount(accountId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/accounts/{accountId}/close")
    @Operation(summary = "Close savings account")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.SAVINGS_ACCOUNT, resourceIdParam = "accountId", operation = "CLOSE")
    public ResponseEntity<ApiResponse<Void>> closeSavingsAccount(
            @PathVariable UUID accountId,
            @RequestParam String reason) {
        log.info("Closing savings account: {} reason: {}", accountId, reason);
        
        savingsAccountService.closeSavingsAccount(accountId, reason);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // Deposit and Withdrawal Operations
    @PostMapping("/accounts/{accountId}/deposit")
    @Operation(summary = "Deposit money to savings account")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.SAVINGS_ACCOUNT, resourceIdParam = "accountId", operation = "DEPOSIT")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @PathVariable UUID accountId,
            @Valid @RequestBody DepositRequest request) {
        log.info("Depositing {} to savings account: {}", request.getAmount(), accountId);
        
        TransactionResponse response = savingsAccountService.deposit(accountId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/accounts/{accountId}/withdraw")
    @Operation(summary = "Withdraw money from savings account")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.SAVINGS_ACCOUNT, resourceIdParam = "accountId", operation = "WITHDRAW")
    @RequiresMFA(threshold = 500) // CRITICAL P0 FIX: Lowered from $1000 to $500 for enhanced security
    @FraudCheck(level = FraudCheckLevel.HIGH)
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
            @PathVariable UUID accountId,
            @Valid @RequestBody WithdrawRequest request) {
        log.info("Withdrawing {} from savings account: {}", request.getAmount(), accountId);
        
        TransactionResponse response = savingsAccountService.withdraw(accountId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/accounts/{fromAccountId}/transfer")
    @Operation(summary = "Transfer between savings accounts")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.SAVINGS_ACCOUNT, resourceIdParam = "fromAccountId", operation = "TRANSFER")
    @RequiresMFA(threshold = 5000)
    public ResponseEntity<ApiResponse<TransferResponse>> transfer(
            @PathVariable UUID fromAccountId,
            @Valid @RequestBody TransferRequest request) {
        log.info("Transferring {} from account: {} to account: {}", 
                request.getAmount(), fromAccountId, request.getToAccountId());
        
        TransferResponse response = savingsAccountService.transfer(fromAccountId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Savings Goals
    @PostMapping("/goals")
    @Operation(summary = "Create a new savings goal")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<SavingsGoalResponse>> createSavingsGoal(
            @Valid @RequestBody CreateSavingsGoalRequest request) {
        
        // SECURITY FIX: Override user ID with authenticated user
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();
        request.setUserId(authenticatedUserId);
        log.info("Creating savings goal: {} target: {}", request.getGoalName(), request.getTargetAmount());
        
        SavingsGoalResponse response = savingsGoalService.createSavingsGoal(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/goals/{goalId}")
    @Operation(summary = "Get savings goal details")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.SAVINGS_GOAL, resourceIdParam = "goalId", operation = "VIEW")
    public ResponseEntity<ApiResponse<SavingsGoalResponse>> getSavingsGoal(
            @PathVariable UUID goalId) {
        log.info("Retrieving savings goal: {}", goalId);
        
        SavingsGoalResponse response = savingsGoalService.getSavingsGoal(goalId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/users/{userId}/goals")
    @Operation(summary = "Get all savings goals for a user")
    @PreAuthorize("@userOwnershipValidator.isCurrentUser(authentication.name, #userId) or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SavingsGoalResponse>>> getUserSavingsGoals(
            @PathVariable UUID userId,
            @RequestParam(required = false) String status) {
        
        // SECURITY FIX: Override user ID with authenticated user for non-admin
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();
        if (!SecurityContextUtil.hasRole("ADMIN")) {
            userId = authenticatedUserId;
        }
        log.info("Retrieving savings goals for user: {} status: {}", userId, status);
        
        List<SavingsGoalResponse> response = savingsGoalService.getUserSavingsGoals(userId, status);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/goals/{goalId}")
    @Operation(summary = "Update savings goal")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.SAVINGS_GOAL, resourceIdParam = "goalId", operation = "UPDATE")
    public ResponseEntity<ApiResponse<SavingsGoalResponse>> updateSavingsGoal(
            @PathVariable UUID goalId,
            @Valid @RequestBody UpdateSavingsGoalRequest request) {
        log.info("Updating savings goal: {}", goalId);
        
        SavingsGoalResponse response = savingsGoalService.updateSavingsGoal(goalId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/goals/{goalId}/contribute")
    @Operation(summary = "Contribute to savings goal")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.SAVINGS_GOAL, resourceIdParam = "goalId", operation = "CONTRIBUTE")
    public ResponseEntity<ApiResponse<ContributionResponse>> contributeToGoal(
            @PathVariable UUID goalId,
            @Valid @RequestBody ContributeRequest request) {
        log.info("Contributing {} to goal: {}", request.getAmount(), goalId);
        
        ContributionResponse response = savingsGoalService.contributeToGoal(goalId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/goals/{goalId}/contributions")
    @Operation(summary = "Get goal contribution history")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.SAVINGS_GOAL, resourceIdParam = "goalId", operation = "VIEW_CONTRIBUTIONS")
    public ResponseEntity<ApiResponse<Page<ContributionResponse>>> getGoalContributions(
            @PathVariable UUID goalId,
            Pageable pageable) {
        log.info("Retrieving contributions for goal: {}", goalId);
        
        Page<ContributionResponse> response = savingsGoalService.getGoalContributions(goalId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/goals/{goalId}/complete")
    @Operation(summary = "Mark savings goal as completed")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.SAVINGS_GOAL, resourceIdParam = "goalId", operation = "COMPLETE")
    public ResponseEntity<ApiResponse<SavingsGoalResponse>> completeGoal(
            @PathVariable UUID goalId) {
        log.info("Completing savings goal: {}", goalId);
        
        SavingsGoalResponse response = savingsGoalService.completeGoal(goalId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Auto-Save Rules
    @PostMapping("/auto-save/rules")
    @Operation(summary = "Create auto-save rule")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<AutoSaveRuleResponse>> createAutoSaveRule(
            @Valid @RequestBody CreateAutoSaveRuleRequest request) {
        log.info("Creating auto-save rule: {} for account: {}", 
                request.getRuleName(), request.getSavingsAccountId());
        
        AutoSaveRuleResponse response = autoSaveService.createAutoSaveRule(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/auto-save/rules/{ruleId}")
    @Operation(summary = "Get auto-save rule details")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<AutoSaveRuleResponse>> getAutoSaveRule(
            @PathVariable UUID ruleId) {
        log.info("Retrieving auto-save rule: {}", ruleId);
        
        AutoSaveRuleResponse response = autoSaveService.getAutoSaveRule(ruleId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/accounts/{accountId}/auto-save/rules")
    @Operation(summary = "Get auto-save rules for account")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<AutoSaveRuleResponse>>> getAccountAutoSaveRules(
            @PathVariable UUID accountId) {
        log.info("Retrieving auto-save rules for account: {}", accountId);
        
        List<AutoSaveRuleResponse> response = autoSaveService.getAccountAutoSaveRules(accountId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/auto-save/rules/{ruleId}")
    @Operation(summary = "Update auto-save rule")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<AutoSaveRuleResponse>> updateAutoSaveRule(
            @PathVariable UUID ruleId,
            @Valid @RequestBody UpdateAutoSaveRuleRequest request) {
        log.info("Updating auto-save rule: {}", ruleId);
        
        AutoSaveRuleResponse response = autoSaveService.updateAutoSaveRule(ruleId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/auto-save/rules/{ruleId}/toggle")
    @Operation(summary = "Enable/Disable auto-save rule")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<AutoSaveRuleResponse>> toggleAutoSaveRule(
            @PathVariable UUID ruleId,
            @RequestParam boolean enabled) {
        log.info("Toggling auto-save rule: {} to: {}", ruleId, enabled);
        
        AutoSaveRuleResponse response = autoSaveService.toggleAutoSaveRule(ruleId, enabled);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/auto-save/rules/{ruleId}")
    @Operation(summary = "Delete auto-save rule")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> deleteAutoSaveRule(
            @PathVariable UUID ruleId) {
        log.info("Deleting auto-save rule: {}", ruleId);
        
        autoSaveService.deleteAutoSaveRule(ruleId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // Interest and Rewards
    @GetMapping("/accounts/{accountId}/interest")
    @Operation(summary = "Get interest earned on savings account")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<InterestSummaryResponse>> getInterestSummary(
            @PathVariable UUID accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Retrieving interest summary for account: {} from {} to {}", 
                accountId, startDate, endDate);
        
        InterestSummaryResponse response = savingsAccountService.getInterestSummary(
                accountId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/accounts/{accountId}/calculate-interest")
    @Operation(summary = "Calculate and apply interest")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<InterestCalculationResponse>> calculateInterest(
            @PathVariable UUID accountId) {
        log.info("Calculating interest for account: {}", accountId);
        
        InterestCalculationResponse response = savingsAccountService.calculateAndApplyInterest(accountId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Milestones and Achievements
    @GetMapping("/goals/{goalId}/milestones")
    @Operation(summary = "Get savings goal milestones")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<MilestoneResponse>>> getGoalMilestones(
            @PathVariable UUID goalId) {
        log.info("Retrieving milestones for goal: {}", goalId);
        
        List<MilestoneResponse> response = savingsGoalService.getGoalMilestones(goalId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/users/{userId}/achievements")
    @Operation(summary = "Get user savings achievements")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<AchievementResponse>>> getUserAchievements(
            @PathVariable UUID userId) {
        log.info("Retrieving achievements for user: {}", userId);
        
        List<AchievementResponse> response = savingsAccountService.getUserAchievements(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Analytics and Reports
    @GetMapping("/users/{userId}/analytics")
    @Operation(summary = "Get savings analytics for user")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<SavingsAnalyticsResponse>> getSavingsAnalytics(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "30") Integer days) {
        log.info("Retrieving savings analytics for user: {} for {} days", userId, days);
        
        SavingsAnalyticsResponse response = savingsAccountService.getSavingsAnalytics(userId, days);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/accounts/{accountId}/transactions")
    @Operation(summary = "Get transaction history for savings account")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.SAVINGS_ACCOUNT, resourceIdParam = "accountId", operation = "VIEW_TRANSACTIONS")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getAccountTransactions(
            @PathVariable UUID accountId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable) {
        log.info("Retrieving transactions for account: {}", accountId);
        
        Page<TransactionResponse> response = savingsAccountService.getAccountTransactions(
                accountId, type, startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/accounts/{accountId}/statement")
    @Operation(summary = "Generate account statement")
    @PreAuthorize("hasRole('USER')")
    @ValidateOwnership(resourceType = ResourceType.SAVINGS_ACCOUNT, resourceIdParam = "accountId", operation = "GENERATE_STATEMENT")
    public ResponseEntity<ApiResponse<StatementResponse>> generateStatement(
            @PathVariable UUID accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "PDF") String format) {
        log.info("Generating statement for account: {} from {} to {} in {} format", 
                accountId, startDate, endDate, format);
        
        StatementResponse response = savingsAccountService.generateStatement(
                accountId, startDate, endDate, format);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Savings Recommendations
    @GetMapping("/users/{userId}/recommendations")
    @Operation(summary = "Get personalized savings recommendations")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<SavingsRecommendationResponse>>> getSavingsRecommendations(
            @PathVariable UUID userId) {
        log.info("Retrieving savings recommendations for user: {}", userId);
        
        List<SavingsRecommendationResponse> response = savingsAccountService.getSavingsRecommendations(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/goals/{goalId}/projections")
    @Operation(summary = "Get goal completion projections")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<GoalProjectionResponse>> getGoalProjections(
            @PathVariable UUID goalId) {
        log.info("Retrieving projections for goal: {}", goalId);
        
        GoalProjectionResponse response = savingsGoalService.getGoalProjections(goalId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Health Check
    @GetMapping("/health")
    @Operation(summary = "Health check for savings service")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.success("Savings Service is healthy"));
    }
}