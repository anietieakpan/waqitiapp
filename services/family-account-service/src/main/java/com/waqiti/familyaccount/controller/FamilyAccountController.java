package com.waqiti.familyaccount.controller;

import com.waqiti.common.api.ApiResponse;
import com.waqiti.common.exception.ResourceNotFoundException;
import com.waqiti.familyaccount.dto.*;
import com.waqiti.familyaccount.repository.FamilyMemberRepository;
import com.waqiti.familyaccount.service.*;
import com.waqiti.familyaccount.service.query.FamilyAccountQueryService;
import com.waqiti.familyaccount.service.query.FamilyMemberQueryService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Family Account Controller V2 (Refactored)
 *
 * REST controller for family account operations
 * Uses refactored services following Single Responsibility Principle
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@RestController
@RequestMapping("/api/v2/family-accounts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Family Account Management V2", description = "Refactored family account operations with improved architecture")
public class FamilyAccountController {

    // Command Services (Write operations)
    private final FamilyAccountManagementService familyAccountManagementService;
    private final FamilyMemberManagementService familyMemberManagementService;
    private final SpendingRuleService spendingRuleService;
    private final FamilyAllowanceService familyAllowanceService;
    private final FamilyTransactionAuthorizationService transactionAuthorizationService;

    // Query Services (Read operations - CQRS pattern)
    private final FamilyAccountQueryService familyAccountQueryService;
    private final FamilyMemberQueryService familyMemberQueryService;

    // Supporting Services
    private final SpendingLimitService spendingLimitService;

    // Repository for direct entity access
    private final FamilyMemberRepository familyMemberRepository;

    // ==================== FAMILY ACCOUNT OPERATIONS ====================

    /**
     * Create a new family account
     *
     * Rate Limited: 5 requests per minute to prevent abuse
     */
    @PostMapping
    @Operation(summary = "Create family account", description = "Creates a new family account with the authenticated user as primary parent. Rate limited to 5 requests/minute.")
    @PreAuthorize("hasRole('USER')")
    @RateLimiter(name = "family-account-creation", fallbackMethod = "createFamilyAccountFallback")
    public ResponseEntity<ApiResponse<FamilyAccountResponse>> createFamilyAccount(
            @Valid @RequestBody CreateFamilyAccountRequest request) {

        String userId = getCurrentUserId();
        log.info("Creating family account for user: {}", userId);

        // Ensure requesting user is the primary parent
        request.setPrimaryParentUserId(userId);

        FamilyAccountDto familyAccount = familyAccountManagementService.createFamilyAccount(request, userId);

        FamilyAccountResponse response = mapToFamilyAccountResponse(familyAccount);
        response.setMessage("Family account created successfully");

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * Get family account details
     */
    @GetMapping("/{familyId}")
    @Operation(summary = "Get family account", description = "Retrieves family account details by family ID")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<FamilyAccountResponse>> getFamilyAccount(
            @PathVariable String familyId) {

        String userId = getCurrentUserId();
        log.info("Getting family account: {} for user: {}", familyId, userId);

        FamilyAccountDto familyAccount = familyAccountQueryService.getFamilyAccount(familyId, userId);

        FamilyAccountResponse response = mapToFamilyAccountResponse(familyAccount);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get all family accounts where user is parent
     */
    @GetMapping("/my-families")
    @Operation(summary = "Get my family accounts", description = "Retrieves all family accounts where user is a parent")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<FamilyAccountResponse>>> getMyFamilyAccounts() {

        String userId = getCurrentUserId();
        log.info("Getting family accounts for user: {}", userId);

        List<FamilyAccountDto> familyAccounts = familyAccountQueryService.getFamilyAccountsByParent(userId);

        List<FamilyAccountResponse> responses = familyAccounts.stream()
                .map(this::mapToFamilyAccountResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * Update family account
     */
    @PutMapping("/{familyId}")
    @Operation(summary = "Update family account", description = "Updates family account settings (parents only)")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<FamilyAccountResponse>> updateFamilyAccount(
            @PathVariable String familyId,
            @Valid @RequestBody UpdateFamilyAccountRequest request) {

        String userId = getCurrentUserId();
        log.info("Updating family account: {} by user: {}", familyId, userId);

        FamilyAccountDto familyAccount = familyAccountManagementService.updateFamilyAccount(familyId, request, userId);

        FamilyAccountResponse response = mapToFamilyAccountResponse(familyAccount);
        response.setMessage("Family account updated successfully");

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Delete family account
     */
    @DeleteMapping("/{familyId}")
    @Operation(summary = "Delete family account", description = "Deletes family account (primary parent only)")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> deleteFamilyAccount(
            @PathVariable String familyId) {

        String userId = getCurrentUserId();
        log.info("Deleting family account: {} by user: {}", familyId, userId);

        familyAccountManagementService.deleteFamilyAccount(familyId, userId);

        return ResponseEntity.ok(ApiResponse.success(null, "Family account deleted successfully"));
    }

    // ==================== FAMILY MEMBER OPERATIONS ====================

    /**
     * Add family member
     *
     * Rate Limited: 20 requests per minute to prevent abuse
     */
    @PostMapping("/{familyId}/members")
    @Operation(summary = "Add family member", description = "Adds a new member to the family account (parents only). Rate limited to 20 requests/minute.")
    @PreAuthorize("hasRole('USER')")
    @RateLimiter(name = "member-management", fallbackMethod = "addFamilyMemberFallback")
    public ResponseEntity<ApiResponse<FamilyMemberResponse>> addFamilyMember(
            @PathVariable String familyId,
            @Valid @RequestBody AddFamilyMemberRequest request) {

        String userId = getCurrentUserId();
        log.info("Adding family member to family: {} by user: {}", familyId, userId);

        // Set family ID from path
        request.setFamilyId(familyId);

        FamilyMemberDto familyMember = familyMemberManagementService.addFamilyMember(request, userId);

        FamilyMemberResponse response = mapToFamilyMemberResponse(familyMember);
        response.setMessage("Family member added successfully");

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * Get family member
     */
    @GetMapping("/{familyId}/members/{userId}")
    @Operation(summary = "Get family member", description = "Retrieves family member details")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<FamilyMemberResponse>> getFamilyMember(
            @PathVariable String familyId,
            @PathVariable String userId) {

        String requestingUserId = getCurrentUserId();
        log.info("Getting family member: {} for user: {}", userId, requestingUserId);

        FamilyMemberDto familyMember = familyMemberQueryService.getFamilyMember(userId, requestingUserId);

        FamilyMemberResponse response = mapToFamilyMemberResponse(familyMember);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get all family members
     */
    @GetMapping("/{familyId}/members")
    @Operation(summary = "Get family members", description = "Retrieves all members of a family account")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<FamilyMemberResponse>>> getFamilyMembers(
            @PathVariable String familyId) {

        String userId = getCurrentUserId();
        log.info("Getting family members for family: {} by user: {}", familyId, userId);

        List<FamilyMemberDto> members = familyMemberQueryService.getFamilyMembers(familyId, userId);

        List<FamilyMemberResponse> responses = members.stream()
                .map(this::mapToFamilyMemberResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * Update family member
     */
    @PutMapping("/{familyId}/members/{userId}")
    @Operation(summary = "Update family member", description = "Updates family member settings (parents only)")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<FamilyMemberResponse>> updateFamilyMember(
            @PathVariable String familyId,
            @PathVariable String userId,
            @Valid @RequestBody UpdateFamilyMemberRequest request) {

        String requestingUserId = getCurrentUserId();
        log.info("Updating family member: {} by user: {}", userId, requestingUserId);

        FamilyMemberDto familyMember = familyMemberManagementService.updateFamilyMember(userId, request, requestingUserId);

        FamilyMemberResponse response = mapToFamilyMemberResponse(familyMember);
        response.setMessage("Family member updated successfully");

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Remove family member
     */
    @DeleteMapping("/{familyId}/members/{userId}")
    @Operation(summary = "Remove family member", description = "Removes member from family account (parents only)")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> removeFamilyMember(
            @PathVariable String familyId,
            @PathVariable String userId) {

        String requestingUserId = getCurrentUserId();
        log.info("Removing family member: {} by user: {}", userId, requestingUserId);

        familyMemberManagementService.removeFamilyMember(userId, requestingUserId);

        return ResponseEntity.ok(ApiResponse.success(null, "Family member removed successfully"));
    }

    // ==================== ALLOWANCE OPERATIONS ====================

    /**
     * Pay allowance manually
     */
    @PostMapping("/{familyId}/members/{memberId}/allowance/pay")
    @Operation(summary = "Pay allowance", description = "Manually pays allowance to family member (parents only)")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> payAllowance(
            @PathVariable String familyId,
            @PathVariable Long memberId,
            @RequestParam BigDecimal amount) {

        String userId = getCurrentUserId();
        log.info("Paying allowance to member: {} by user: {}", memberId, userId);

        familyAllowanceService.payAllowance(memberId, amount, userId);

        return ResponseEntity.ok(ApiResponse.success(null, "Allowance paid successfully"));
    }

    // ==================== TRANSACTION AUTHORIZATION ====================

    /**
     * Authorize transaction
     *
     * Supports idempotency via Idempotency-Key header.
     * If the same idempotency key is sent multiple times, the cached result
     * from the first transaction will be returned without reprocessing.
     *
     * This prevents duplicate charges due to network issues, timeouts, or client retries.
     *
     * Rate Limited: 10 requests per second per user to prevent abuse
     *
     * @param idempotencyKey Optional idempotency key for duplicate prevention (recommended: UUID)
     * @param request Transaction authorization request
     * @return Authorization response
     */
    @PostMapping("/transactions/authorize")
    @Operation(summary = "Authorize transaction", description = "Authorizes a transaction for a family member. Supports idempotency via Idempotency-Key header. Rate limited to 10 requests/second.")
    @PreAuthorize("hasRole('USER')")
    @RateLimiter(name = "transaction-authorization", fallbackMethod = "transactionAuthorizationFallback")
    public ResponseEntity<ApiResponse<TransactionAuthorizationResponse>> authorizeTransaction(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransactionAuthorizationRequest request) {

        log.info("Authorizing transaction for user: {} with idempotency key: {}",
                request.getUserId(), idempotencyKey != null ? idempotencyKey : "none");

        TransactionAuthorizationResponse response = transactionAuthorizationService.authorizeTransaction(
                request, idempotencyKey);

        String message = response.getAuthorized()
            ? "Transaction authorized"
            : (response.getRequiresParentApproval()
                ? "Transaction requires parent approval"
                : "Transaction declined");

        return ResponseEntity.ok(ApiResponse.success(response, message));
    }

    /**
     * Approve pending transaction
     */
    @PostMapping("/transactions/{transactionAttemptId}/approve")
    @Operation(summary = "Approve transaction", description = "Parent approves pending transaction")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> approveTransaction(
            @PathVariable Long transactionAttemptId) {

        String userId = getCurrentUserId();
        log.info("Approving transaction: {} by user: {}", transactionAttemptId, userId);

        boolean approved = transactionAuthorizationService.approveTransaction(transactionAttemptId, userId);

        String message = approved ? "Transaction approved successfully" : "Transaction approval failed";

        return ResponseEntity.ok(ApiResponse.success(null, message));
    }

    /**
     * Decline pending transaction
     */
    @PostMapping("/transactions/{transactionAttemptId}/decline")
    @Operation(summary = "Decline transaction", description = "Parent declines pending transaction")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> declineTransaction(
            @PathVariable Long transactionAttemptId,
            @RequestParam String reason) {

        String userId = getCurrentUserId();
        log.info("Declining transaction: {} by user: {}", transactionAttemptId, userId);

        transactionAuthorizationService.declineTransaction(transactionAttemptId, userId, reason);

        return ResponseEntity.ok(ApiResponse.success(null, "Transaction declined successfully"));
    }

    // ==================== SPENDING LIMIT QUERIES ====================

    /**
     * Get member spending summary
     */
    @GetMapping("/{familyId}/members/{memberId}/spending")
    @Operation(summary = "Get member spending", description = "Retrieves spending summary for family member")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<MemberSpendingSummary>> getMemberSpending(
            @PathVariable String familyId,
            @PathVariable Long memberId) {

        String userId = getCurrentUserId();
        log.info("Getting spending summary for member: {} by user: {}", memberId, userId);

        // Get member from repository
        com.waqiti.familyaccount.domain.FamilyMember member =
            getFamilyMemberEntity(memberId);

        BigDecimal dailySpent = spendingLimitService.calculateDailySpending(member);
        BigDecimal weeklySpent = spendingLimitService.calculateWeeklySpending(member);
        BigDecimal monthlySpent = spendingLimitService.calculateMonthlySpending(member);

        BigDecimal dailyRemaining = spendingLimitService.getRemainingDailySpending(member);
        BigDecimal weeklyRemaining = spendingLimitService.getRemainingWeeklySpending(member);
        BigDecimal monthlyRemaining = spendingLimitService.getRemainingMonthlySpending(member);

        MemberSpendingSummary summary = MemberSpendingSummary.builder()
            .dailySpent(dailySpent)
            .weeklySpent(weeklySpent)
            .monthlySpent(monthlySpent)
            .dailyRemaining(dailyRemaining)
            .weeklyRemaining(weeklyRemaining)
            .monthlyRemaining(monthlyRemaining)
            .dailyLimit(member.getDailySpendingLimit())
            .weeklyLimit(member.getWeeklySpendingLimit())
            .monthlyLimit(member.getMonthlySpendingLimit())
            .build();

        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    // ==================== SPENDING RULES ====================

    /**
     * Create spending rule
     */
    @PostMapping("/{familyId}/spending-rules")
    @Operation(summary = "Create spending rule", description = "Creates a spending rule for family (parents only)")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<com.waqiti.familyaccount.domain.FamilySpendingRule>> createSpendingRule(
            @PathVariable String familyId,
            @Valid @RequestBody CreateSpendingRuleRequest request) {

        String userId = getCurrentUserId();
        log.info("Creating spending rule for family: {} by user: {}", familyId, userId);

        request.setFamilyId(familyId);

        com.waqiti.familyaccount.domain.FamilySpendingRule rule =
            spendingRuleService.createSpendingRule(request, userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(rule, "Spending rule created successfully"));
    }

    /**
     * Get spending rules
     */
    @GetMapping("/{familyId}/spending-rules")
    @Operation(summary = "Get spending rules", description = "Retrieves all spending rules for family")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<com.waqiti.familyaccount.domain.FamilySpendingRule>>> getSpendingRules(
            @PathVariable String familyId) {

        String userId = getCurrentUserId();
        log.info("Getting spending rules for family: {} by user: {}", familyId, userId);

        List<com.waqiti.familyaccount.domain.FamilySpendingRule> rules =
            spendingRuleService.getFamilySpendingRules(familyId, userId);

        return ResponseEntity.ok(ApiResponse.success(rules));
    }

    // ==================== HEALTH CHECK ====================

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.success("Family Account V2 service is healthy"));
    }

    // ==================== HELPER METHODS ====================

    /**
     * Get current authenticated user ID
     */
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        return authentication.getName();
    }

    /**
     * Get family member entity (helper for spending queries)
     * Production-ready implementation with proper error handling
     */
    private com.waqiti.familyaccount.domain.FamilyMember getFamilyMemberEntity(Long memberId) {
        log.debug("Retrieving family member entity for memberId: {}", memberId);

        return familyMemberRepository.findById(memberId)
            .orElseThrow(() -> {
                log.warn("Family member not found with id: {}", memberId);
                return new ResourceNotFoundException(
                    "Family member not found with id: " + memberId);
            });
    }

    /**
     * Map FamilyAccountDto to FamilyAccountResponse
     */
    private FamilyAccountResponse mapToFamilyAccountResponse(FamilyAccountDto dto) {
        return FamilyAccountResponse.builder()
                .familyId(dto.getFamilyId())
                .familyName(dto.getFamilyName())
                .primaryParentUserId(dto.getPrimaryParentUserId())
                .secondaryParentUserId(dto.getSecondaryParentUserId())
                .familyWalletId(dto.getFamilyWalletId())
                .allowanceDayOfMonth(dto.getAllowanceDayOfMonth())
                .autoSavingsPercentage(dto.getAutoSavingsPercentage())
                .autoSavingsEnabled(dto.getAutoSavingsEnabled())
                .defaultDailyLimit(dto.getDefaultDailyLimit())
                .defaultWeeklyLimit(dto.getDefaultWeeklyLimit())
                .defaultMonthlyLimit(dto.getDefaultMonthlyLimit())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .memberCount(dto.getMemberCount())
                .build();
    }

    /**
     * Map FamilyMemberDto to FamilyMemberResponse
     */
    private FamilyMemberResponse mapToFamilyMemberResponse(FamilyMemberDto dto) {
        return FamilyMemberResponse.builder()
                .userId(dto.getUserId())
                .familyId(dto.getFamilyId())
                .memberRole(dto.getMemberRole())
                .memberStatus(dto.getMemberStatus())
                .dateOfBirth(dto.getDateOfBirth())
                .age(dto.getAge())
                .allowanceAmount(dto.getAllowanceAmount())
                .allowanceFrequency(dto.getAllowanceFrequency())
                .lastAllowanceDate(dto.getLastAllowanceDate())
                .dailySpendingLimit(dto.getDailySpendingLimit())
                .weeklySpendingLimit(dto.getWeeklySpendingLimit())
                .monthlySpendingLimit(dto.getMonthlySpendingLimit())
                .transactionApprovalRequired(dto.getTransactionApprovalRequired())
                .canViewFamilyAccount(dto.getCanViewFamilyAccount())
                .joinedAt(dto.getJoinedAt())
                .build();
    }
}
