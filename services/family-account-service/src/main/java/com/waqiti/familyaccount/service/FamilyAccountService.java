package com.waqiti.familyaccount.service;

import com.waqiti.familyaccount.repository.*;
import com.waqiti.familyaccount.dto.*;
import com.waqiti.familyaccount.mapper.FamilyMapper;
import com.waqiti.familyaccount.exception.*;
import com.waqiti.familyaccount.client.*;
import com.waqiti.common.kyc.service.KYCClientService;
import com.waqiti.common.kyc.annotation.RequireKYCVerification;
import com.waqiti.common.kyc.annotation.RequireKYCVerification.VerificationLevel;

import com.waqiti.familyaccount.domain.FamilyAccount;
import com.waqiti.familyaccount.domain.FamilyMember;
import com.waqiti.familyaccount.domain.FamilySpendingRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Comprehensive Family Account Service
 * 
 * Provides complete family account management including:
 * - Family account creation and management
 * - Member management with parental controls
 * - Spending rules and restrictions
 * - Educational features and financial literacy
 * - Family goals and savings management
 * - Real-time monitoring and alerts
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class FamilyAccountService {

    private final FamilyAccountRepository familyAccountRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final FamilySpendingRuleRepository spendingRuleRepository;
    private final FamilyGoalRepository familyGoalRepository;
    private final ChoreTaskRepository choreTaskRepository;
    private final SavingsGoalRepository savingsGoalRepository;
    
    private final FamilyMapper familyMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // External service clients
    private final UserServiceClient userServiceClient;
    private final WalletServiceClient walletServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final SecurityServiceClient securityServiceClient;
    private final KYCClientService kycClientService;

    /**
     * Create a new family account
     */
    @CacheEvict(value = "familyAccounts", key = "#request.primaryParentUserId")
    @RequireKYCVerification(level = VerificationLevel.ADVANCED, action = "FAMILY_ACCOUNT")
    public FamilyAccountDto createFamilyAccount(@Valid CreateFamilyAccountRequest request) {
        log.info("Creating family account for user: {}", request.getPrimaryParentUserId());
        
        // Validate primary parent exists and is eligible
        validateParentEligibility(request.getPrimaryParentUserId());
        
        // Check if user already has a family account
        if (familyAccountRepository.existsByPrimaryParentUserId(request.getPrimaryParentUserId())) {
            throw new FamilyAccountException("User already has a family account");
        }
        
        // Generate unique family ID
        String familyId = generateFamilyId();
        
        // Create family wallet
        String familyWalletId = createFamilyWallet(familyId, request.getPrimaryParentUserId());
        
        // Create family account
        FamilyAccount familyAccount = FamilyAccount.builder()
                .familyId(familyId)
                .familyName(request.getFamilyName())
                .primaryParentUserId(request.getPrimaryParentUserId())
                .secondaryParentUserId(request.getSecondaryParentUserId())
                .familyWalletId(familyWalletId)
                .monthlyFamilyLimit(request.getMonthlyFamilyLimit())
                .familyTimezone(request.getFamilyTimezone())
                .emergencyContactPhone(request.getEmergencyContactPhone())
                .allowanceDayOfMonth(request.getAllowanceDayOfMonth())
                .autoSavingsEnabled(request.getAutoSavingsEnabled())
                .autoSavingsPercentage(request.getAutoSavingsPercentage())
                .createdBy(request.getPrimaryParentUserId())
                .updatedBy(request.getPrimaryParentUserId())
                .build();
        
        familyAccount = familyAccountRepository.save(familyAccount);
        
        // Create primary parent member
        createPrimaryParentMember(familyAccount, request.getPrimaryParentUserId());
        
        // Create secondary parent member if provided
        if (request.getSecondaryParentUserId() != null) {
            createSecondaryParentMember(familyAccount, request.getSecondaryParentUserId());
        }
        
        // Create default spending rules
        createDefaultSpendingRules(familyAccount, request.getPrimaryParentUserId());
        
        // Send welcome notification
        sendFamilyCreatedNotification(familyAccount);
        
        // Publish family account created event
        publishFamilyAccountCreatedEvent(familyAccount);
        
        log.info("Family account created successfully: {}", familyId);
        return familyMapper.toDto(familyAccount);
    }

    /**
     * Add a family member
     */
    @CacheEvict(value = "familyAccounts", key = "#familyId")
    @RequireKYCVerification(level = VerificationLevel.ADVANCED, action = "FAMILY_MEMBER_MANAGEMENT")
    public FamilyMemberDto addFamilyMember(String familyId, @Valid AddFamilyMemberRequest request) {
        log.info("Adding family member to family: {}", familyId);
        
        FamilyAccount familyAccount = getFamilyAccountByIdOrThrow(familyId);
        
        // Validate requester is a parent
        validateParentPermission(familyAccount, request.getRequestingUserId());
        
        // Validate member doesn't already exist
        if (familyMemberRepository.existsByFamilyAccountAndUserId(familyAccount, request.getUserId())) {
            throw new FamilyAccountException("User is already a family member");
        }
        
        // Validate user exists
        validateUserExists(request.getUserId());
        
        // Create individual wallet for the member
        String individualWalletId = createIndividualWallet(familyId, request.getUserId());
        
        FamilyMember member = FamilyMember.builder()
                .familyAccount(familyAccount)
                .userId(request.getUserId())
                .nickname(request.getNickname())
                .memberRole(request.getMemberRole())
                .dateOfBirth(request.getDateOfBirth())
                .individualWalletId(individualWalletId)
                .dailySpendLimit(request.getDailySpendLimit())
                .weeklySpendLimit(request.getWeeklySpendLimit())
                .monthlySpendLimit(request.getMonthlySpendLimit())
                .allowanceAmount(request.getAllowanceAmount())
                .transactionApprovalRequired(request.getTransactionApprovalRequired())
                .spendingTimeRestrictionsEnabled(request.getSpendingTimeRestrictionsEnabled())
                .spendingAllowedStartTime(request.getSpendingAllowedStartTime())
                .spendingAllowedEndTime(request.getSpendingAllowedEndTime())
                .weekendsSpendingAllowed(request.getWeekendsSpendingAllowed())
                .onlinePurchasesAllowed(request.getOnlinePurchasesAllowed())
                .atmWithdrawalsAllowed(request.getAtmWithdrawalsAllowed())
                .internationalTransactionsAllowed(request.getInternationalTransactionsAllowed())
                .peerPaymentsAllowed(request.getPeerPaymentsAllowed())
                .investmentAllowed(request.getInvestmentAllowed())
                .cryptoTransactionsAllowed(request.getCryptoTransactionsAllowed())
                .addedBy(request.getRequestingUserId())
                .build();
        
        member = familyMemberRepository.save(member);
        
        // Apply age-appropriate default rules
        applyDefaultRulesForMember(member, request.getRequestingUserId());
        
        // Send invitation notification
        sendMemberInvitationNotification(member);
        
        // Publish member added event
        publishFamilyMemberAddedEvent(member);
        
        log.info("Family member added successfully: {}", request.getUserId());
        return familyMapper.toDto(member);
    }

    /**
     * Authorize a transaction for a family member
     */
    @Transactional
    public TransactionAuthorizationResult authorizeTransaction(@Valid TransactionAuthorizationRequest request) {
        log.info("Authorizing transaction for member: {} amount: {}", 
                request.getMemberId(), request.getAmount());
        
        FamilyMember member = getFamilyMemberByIdOrThrow(request.getMemberId());
        FamilyAccount familyAccount = member.getFamilyAccount();
        
        // Create authorization result
        TransactionAuthorizationResult result = TransactionAuthorizationResult.builder()
                .memberId(request.getMemberId())
                .transactionId(request.getTransactionId())
                .amount(request.getAmount())
                .merchantName(request.getMerchantName())
                .category(request.getCategory())
                .authorized(false)
                .build();
        
        List<String> declineReasons = new ArrayList<>();
        
        // Check member status
        if (member.getMemberStatus() != FamilyMember.MemberStatus.ACTIVE) {
            declineReasons.add("Member account is not active");
            result.setDeclineReasons(declineReasons);
            return result;
        }
        
        // Check if member can spend the amount
        if (!member.canSpendAmount(request.getAmount())) {
            declineReasons.add("Insufficient funds or spending limit exceeded");
        }
        
        // Check time restrictions
        if (!member.isSpendingTimeAllowed()) {
            declineReasons.add("Transaction not allowed at current time");
        }
        
        // Check family-level limits
        if (!familyAccount.canSpend(request.getAmount())) {
            declineReasons.add("Family spending limit exceeded");
        }
        
        // Apply spending rules
        List<FamilySpendingRule> applicableRules = getApplicableSpendingRules(member);
        for (FamilySpendingRule rule : applicableRules) {
            if (!rule.allowsTransaction(request.getAmount(), request.getCategory(), 
                                      request.getMerchantName(), request.getTransactionType())) {
                declineReasons.add("Blocked by spending rule: " + rule.getRuleName());
            }
            
            if (!rule.isTimeAllowed()) {
                declineReasons.add("Transaction not allowed at current time by rule: " + rule.getRuleName());
            }
            
            // Check if parent approval is required
            if (rule.getRequiresParentApproval() && 
                (rule.getApprovalThreshold() == null || 
                 request.getAmount().compareTo(rule.getApprovalThreshold()) >= 0)) {
                result.setRequiresParentApproval(true);
                result.setApprovalRequired(true);
            }
        }
        
        // If no decline reasons and no approval required, authorize
        if (declineReasons.isEmpty() && !result.isApprovalRequired()) {
            result.setAuthorized(true);
            
            // Update spending amounts
            member.addToSpent(request.getAmount());
            familyAccount.addToMonthlySpent(request.getAmount());
            
            // Update balances
            member.setCurrentBalance(member.getCurrentBalance().subtract(request.getAmount()));
            
            familyMemberRepository.save(member);
            familyAccountRepository.save(familyAccount);
            
            // Check if educational prompt should be shown
            checkEducationalPrompts(member, request, result);
            
            // Check savings goals
            checkSavingsGoals(member, request.getAmount(), result);
            
            log.info("Transaction authorized for member: {}", request.getMemberId());
        } else {
            result.setDeclineReasons(declineReasons);
            log.info("Transaction declined for member: {} reasons: {}", 
                    request.getMemberId(), declineReasons);
        }
        
        // Record transaction attempt
        recordTransactionAttempt(member, request, result);
        
        // Send notifications if needed
        sendTransactionNotifications(member, request, result);
        
        return result;
    }

    /**
     * Process allowance payments
     */
    @Transactional
    public void processAllowancePayments() {
        log.info("Processing allowance payments");
        
        LocalDate today = LocalDate.now();
        
        List<FamilyAccount> familyAccounts = familyAccountRepository.findByAllowanceDayOfMonth(today.getDayOfMonth());
        
        for (FamilyAccount familyAccount : familyAccounts) {
            List<FamilyMember> members = familyMemberRepository.findByFamilyAccountAndAllowanceAmountGreaterThan(
                    familyAccount, BigDecimal.ZERO);
            
            for (FamilyMember member : members) {
                // Check if allowance already paid this month
                if (member.getLastAllowanceDate() != null && 
                    member.getLastAllowanceDate().getMonth() == today.getMonth() &&
                    member.getLastAllowanceDate().getYear() == today.getYear()) {
                    continue;
                }
                
                // Pay allowance
                member.addAllowance();
                familyMemberRepository.save(member);
                
                // Send notification
                sendAllowanceNotification(member);
                
                log.info("Allowance paid to member: {} amount: {}", 
                        member.getUserId(), member.getAllowanceAmount());
            }
        }
    }

    /**
     * Get family dashboard data
     */
    @Cacheable(value = "familyDashboard", key = "#familyId")
    @Transactional(readOnly = true)
    public FamilyDashboardDto getFamilyDashboard(String familyId, String requestingUserId) {
        log.info("Getting family dashboard for family: {}", familyId);
        
        FamilyAccount familyAccount = getFamilyAccountByIdOrThrow(familyId);
        
        // Validate user is family member
        validateFamilyMemberAccess(familyAccount, requestingUserId);
        
        // Get family members
        List<FamilyMember> members = familyMemberRepository.findByFamilyAccount(familyAccount);
        
        // Calculate family metrics
        BigDecimal totalFamilyBalance = members.stream()
                .map(FamilyMember::getCurrentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalMonthlySpent = familyAccount.getCurrentMonthSpent();
        BigDecimal monthlyLimit = familyAccount.getMonthlyFamilyLimit();
        
        // Get recent transactions
        List<MemberTransaction> recentTransactions = getRecentFamilyTransactions(familyAccount, 10);
        
        // Get family goals
        List<FamilyGoal> familyGoals = familyGoalRepository.findByFamilyAccountAndStatus(
                familyAccount, FamilyGoal.GoalStatus.ACTIVE);
        
        // Get pending approvals (for parents)
        List<PendingApproval> pendingApprovals = new ArrayList<>();
        if (familyAccount.isParent(requestingUserId)) {
            pendingApprovals = getPendingApprovals(familyAccount);
        }
        
        return FamilyDashboardDto.builder()
                .familyId(familyId)
                .familyName(familyAccount.getFamilyName())
                .totalFamilyBalance(totalFamilyBalance)
                .monthlySpent(totalMonthlySpent)
                .monthlyLimit(monthlyLimit)
                .remainingMonthlyLimit(familyAccount.getRemainingMonthlyLimit())
                .members(familyMapper.toMemberSummaryDtos(members))
                .recentTransactions(familyMapper.toTransactionDtos(recentTransactions))
                .familyGoals(familyMapper.toGoalDtos(familyGoals))
                .pendingApprovals(pendingApprovals)
                .build();
    }

    // Private helper methods

    private void validateParentEligibility(String userId) {
        // Validate with user service
        if (!userServiceClient.isUserEligibleForFamilyAccount(userId)) {
            throw new FamilyAccountException("User is not eligible for family account");
        }
        
        // KYC verification is now handled by @RequireKYCVerification annotation
        // Additional eligibility checks can be added here if needed
    }

    private void validateUserExists(String userId) {
        if (!userServiceClient.userExists(userId)) {
            throw new FamilyAccountException("User does not exist: " + userId);
        }
    }

    private void validateParentPermission(FamilyAccount familyAccount, String userId) {
        if (!familyAccount.isParent(userId)) {
            throw new FamilyAccountException("Only parents can perform this action");
        }
    }

    private void validateFamilyMemberAccess(FamilyAccount familyAccount, String userId) {
        if (!familyMemberRepository.existsByFamilyAccountAndUserId(familyAccount, userId)) {
            throw new FamilyAccountException("User is not a member of this family");
        }
    }

    private FamilyAccount getFamilyAccountByIdOrThrow(String familyId) {
        return familyAccountRepository.findByFamilyId(familyId)
                .orElseThrow(() -> new FamilyAccountException("Family account not found: " + familyId));
    }

    private FamilyMember getFamilyMemberByIdOrThrow(String memberId) {
        return familyMemberRepository.findByUserId(memberId)
                .orElseThrow(() -> new FamilyAccountException("Family member not found: " + memberId));
    }

    private String generateFamilyId() {
        return "FAM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String createFamilyWallet(String familyId, String ownerId) {
        return walletServiceClient.createFamilyWallet(familyId, ownerId);
    }

    private String createIndividualWallet(String familyId, String userId) {
        return walletServiceClient.createIndividualWallet(familyId, userId);
    }

    private void createPrimaryParentMember(FamilyAccount familyAccount, String userId) {
        FamilyMember parent = FamilyMember.builder()
                .familyAccount(familyAccount)
                .userId(userId)
                .memberRole(FamilyMember.MemberRole.PRIMARY_PARENT)
                .memberStatus(FamilyMember.MemberStatus.ACTIVE)
                .addedBy(userId)
                .build();
        
        familyMemberRepository.save(parent);
    }

    private void createSecondaryParentMember(FamilyAccount familyAccount, String userId) {
        FamilyMember parent = FamilyMember.builder()
                .familyAccount(familyAccount)
                .userId(userId)
                .memberRole(FamilyMember.MemberRole.SECONDARY_PARENT)
                .memberStatus(FamilyMember.MemberStatus.PENDING_APPROVAL)
                .addedBy(familyAccount.getPrimaryParentUserId())
                .build();
        
        familyMemberRepository.save(parent);
    }

    private void createDefaultSpendingRules(FamilyAccount familyAccount, String createdBy) {
        // Create default rules for different age groups
        createDefaultChildRules(familyAccount, createdBy);
        createDefaultTeenRules(familyAccount, createdBy);
    }

    private void createDefaultChildRules(FamilyAccount familyAccount, String createdBy) {
        FamilySpendingRule childRule = FamilySpendingRule.builder()
                .familyAccount(familyAccount)
                .ruleName("Default Child Protection Rules")
                .ruleDescription("Default spending restrictions for children under 13")
                .ruleType(FamilySpendingRule.RuleType.SPENDING_LIMIT)
                .ruleScope(FamilySpendingRule.RuleScope.AGE_GROUP)
                .targetAgeGroup(FamilySpendingRule.AgeGroup.CHILD_UNDER_13)
                .maxTransactionAmount(BigDecimal.valueOf(20.00))
                .dailyLimit(BigDecimal.valueOf(50.00))
                .requiresParentApproval(true)
                .approvalThreshold(BigDecimal.valueOf(10.00))
                .onlinePurchasesAllowed(false)
                .atmWithdrawalsAllowed(false)
                .internationalAllowed(false)
                .peerPaymentsAllowed(false)
                .subscriptionPaymentsAllowed(false)
                .createdBy(createdBy)
                .updatedBy(createdBy)
                .build();
        
        spendingRuleRepository.save(childRule);
    }

    private void createDefaultTeenRules(FamilyAccount familyAccount, String createdBy) {
        FamilySpendingRule teenRule = FamilySpendingRule.builder()
                .familyAccount(familyAccount)
                .ruleName("Default Teen Spending Rules")
                .ruleDescription("Default spending restrictions for teens 13-17")
                .ruleType(FamilySpendingRule.RuleType.SPENDING_LIMIT)
                .ruleScope(FamilySpendingRule.RuleScope.AGE_GROUP)
                .targetAgeGroup(FamilySpendingRule.AgeGroup.TEEN_13_17)
                .maxTransactionAmount(BigDecimal.valueOf(100.00))
                .dailyLimit(BigDecimal.valueOf(200.00))
                .weeklyLimit(BigDecimal.valueOf(500.00))
                .requiresParentApproval(true)
                .approvalThreshold(BigDecimal.valueOf(50.00))
                .onlinePurchasesAllowed(true)
                .atmWithdrawalsAllowed(false)
                .internationalAllowed(false)
                .peerPaymentsAllowed(true)
                .subscriptionPaymentsAllowed(false)
                .createdBy(createdBy)
                .updatedBy(createdBy)
                .build();
        
        spendingRuleRepository.save(teenRule);
    }

    private void applyDefaultRulesForMember(FamilyMember member, String createdBy) {
        // Rules are applied based on age group automatically
        // Additional member-specific rules can be created here
    }

    private List<FamilySpendingRule> getApplicableSpendingRules(FamilyMember member) {
        return spendingRuleRepository.findByFamilyAccountAndRuleStatus(
                member.getFamilyAccount(), FamilySpendingRule.RuleStatus.ACTIVE)
                .stream()
                .filter(rule -> rule.appliesToMember(member))
                .sorted(Comparator.comparing(FamilySpendingRule::getPriority))
                .collect(Collectors.toList());
    }

    private void checkEducationalPrompts(FamilyMember member, TransactionAuthorizationRequest request, 
                                       TransactionAuthorizationResult result) {
        // Check if any rules have educational prompts
        List<FamilySpendingRule> rulesWithPrompts = getApplicableSpendingRules(member)
                .stream()
                .filter(rule -> rule.getEducationalPrompt() != null)
                .collect(Collectors.toList());
        
        if (!rulesWithPrompts.isEmpty()) {
            result.setEducationalPrompts(rulesWithPrompts.stream()
                    .map(FamilySpendingRule::getEducationalPrompt)
                    .collect(Collectors.toList()));
        }
    }

    private void checkSavingsGoals(FamilyMember member, BigDecimal amount, TransactionAuthorizationResult result) {
        // Check if any savings should be applied
        List<FamilySpendingRule> savingsRules = getApplicableSpendingRules(member)
                .stream()
                .filter(rule -> rule.getSavingsGoalPercentage() != null && 
                              rule.getSavingsGoalPercentage().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());
        
        if (!savingsRules.isEmpty()) {
            BigDecimal totalSavingsPercentage = savingsRules.stream()
                    .map(FamilySpendingRule::getSavingsGoalPercentage)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // FIX: Critical - add scale and rounding mode to prevent ArithmeticException
            BigDecimal savingsAmount = amount.multiply(totalSavingsPercentage)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            result.setSuggestedSavingsAmount(savingsAmount);
        }
    }

    private void recordTransactionAttempt(FamilyMember member, TransactionAuthorizationRequest request, 
                                        TransactionAuthorizationResult result) {
        // Record transaction attempt for analytics and monitoring
        MemberTransaction transaction = MemberTransaction.builder()
                .familyMember(member)
                .transactionId(request.getTransactionId())
                .amount(request.getAmount())
                .merchantName(request.getMerchantName())
                .category(request.getCategory())
                .transactionType(request.getTransactionType())
                .status(result.isAuthorized() ? MemberTransaction.TransactionStatus.AUTHORIZED : 
                        MemberTransaction.TransactionStatus.DECLINED)
                .declineReason(result.getDeclineReasons() != null ? 
                              String.join(", ", result.getDeclineReasons()) : null)
                .requiresApproval(result.isApprovalRequired())
                .build();
        
        // Save through repository (would need to create MemberTransactionRepository)
        // memberTransactionRepository.save(transaction);
    }

    private void sendTransactionNotifications(FamilyMember member, TransactionAuthorizationRequest request, 
                                            TransactionAuthorizationResult result) {
        // Send notification to parents for significant transactions or declines
        if (!result.isAuthorized() || result.isApprovalRequired() || 
            request.getAmount().compareTo(BigDecimal.valueOf(100)) >= 0) {
            
            notificationServiceClient.sendTransactionAlert(
                    member.getFamilyAccount().getPrimaryParentUserId(),
                    member, request, result);
        }
    }

    private void sendFamilyCreatedNotification(FamilyAccount familyAccount) {
        notificationServiceClient.sendFamilyAccountCreatedNotification(familyAccount);
    }

    private void sendMemberInvitationNotification(FamilyMember member) {
        notificationServiceClient.sendFamilyMemberInvitation(member);
    }

    private void sendAllowanceNotification(FamilyMember member) {
        notificationServiceClient.sendAllowancePaymentNotification(member);
    }

    private void publishFamilyAccountCreatedEvent(FamilyAccount familyAccount) {
        FamilyAccountCreatedEvent event = FamilyAccountCreatedEvent.builder()
                .familyId(familyAccount.getFamilyId())
                .familyName(familyAccount.getFamilyName())
                .primaryParentUserId(familyAccount.getPrimaryParentUserId())
                .familyWalletId(familyAccount.getFamilyWalletId())
                .timestamp(LocalDateTime.now())
                .build();
        
        kafkaTemplate.send("family-account-events", event);
    }

    private void publishFamilyMemberAddedEvent(FamilyMember member) {
        FamilyMemberAddedEvent event = FamilyMemberAddedEvent.builder()
                .familyId(member.getFamilyAccount().getFamilyId())
                .memberId(member.getUserId())
                .memberRole(member.getMemberRole().toString())
                .addedBy(member.getAddedBy())
                .timestamp(LocalDateTime.now())
                .build();
        
        kafkaTemplate.send("family-member-events", event);
    }

    private List<MemberTransaction> getRecentFamilyTransactions(FamilyAccount familyAccount, int limit) {
        try {
            log.debug("Retrieving recent transactions for family account: {}", familyAccount.getId());
            
            // Get all family member IDs
            List<UUID> memberIds = familyAccount.getMembers().stream()
                .map(FamilyMember::getUserId)
                .collect(Collectors.toList());
            
            if (memberIds.isEmpty()) {
                return new ArrayList<>();
            }
            
            // Query transactions for all family members
            PageRequest pageRequest = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<Transaction> recentTransactions = transactionRepository
                .findByUserIdInAndCreatedAtAfter(
                    memberIds, 
                    LocalDateTime.now().minusDays(30),
                    pageRequest
                );
            
            // Convert to MemberTransaction DTOs
            return recentTransactions.getContent().stream()
                .map(transaction -> {
                    FamilyMember member = familyAccount.getMembers().stream()
                        .filter(m -> m.getUserId().equals(transaction.getUserId()))
                        .findFirst()
                        .orElse(null);
                    
                    return MemberTransaction.builder()
                        .id(transaction.getId())
                        .memberId(transaction.getUserId())
                        .memberName(member != null ? member.getDisplayName() : "Unknown")
                        .memberRole(member != null ? member.getRole() : FamilyRole.CHILD)
                        .transactionType(transaction.getType())
                        .amount(transaction.getAmount())
                        .currency(transaction.getCurrency())
                        .description(transaction.getDescription())
                        .merchantName(transaction.getMerchantName())
                        .category(transaction.getCategory())
                        .status(transaction.getStatus())
                        .createdAt(transaction.getCreatedAt())
                        .requiresApproval(requiresParentalApproval(transaction, member))
                        .approvalStatus(getApprovalStatus(transaction.getId()))
                        .build();
                })
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error retrieving recent family transactions for account: {}", familyAccount.getId(), e);
            return new ArrayList<>();
        }
    }

    private List<PendingApproval> getPendingApprovals(FamilyAccount familyAccount) {
        try {
            log.debug("Retrieving pending approvals for family account: {}", familyAccount.getId());
            
            // Query pending approvals from the approval repository
            List<TransactionApproval> pendingApprovals = approvalRepository
                .findByFamilyAccountIdAndStatusOrderByRequestedAtDesc(
                    familyAccount.getId(),
                    ApprovalStatus.PENDING
                );
            
            return pendingApprovals.stream()
                .map(approval -> {
                    // Get the transaction details
                    Transaction transaction = transactionRepository.findById(approval.getTransactionId())
                        .orElse(null);
                    
                    // Get the requesting member details
                    FamilyMember requestingMember = familyAccount.getMembers().stream()
                        .filter(m -> m.getUserId().equals(approval.getRequestedBy()))
                        .findFirst()
                        .orElse(null);
                    
                    return PendingApproval.builder()
                        .id(approval.getId())
                        .transactionId(approval.getTransactionId())
                        .requestedBy(approval.getRequestedBy())
                        .requestedByName(requestingMember != null ? requestingMember.getDisplayName() : "Unknown")
                        .requestedByRole(requestingMember != null ? requestingMember.getRole() : FamilyRole.CHILD)
                        .approvalType(approval.getApprovalType())
                        .amount(transaction != null ? transaction.getAmount() : BigDecimal.ZERO)
                        .currency(transaction != null ? transaction.getCurrency() : "USD")
                        .merchantName(transaction != null ? transaction.getMerchantName() : "Unknown")
                        .description(transaction != null ? transaction.getDescription() : approval.getDescription())
                        .category(transaction != null ? transaction.getCategory() : "OTHER")
                        .reason(approval.getReason())
                        .requestedAt(approval.getRequestedAt())
                        .expiresAt(approval.getExpiresAt())
                        .priority(calculateApprovalPriority(approval, transaction))
                        .metadata(approval.getMetadata())
                        .build();
                })
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error retrieving pending approvals for family account: {}", familyAccount.getId(), e);
            return new ArrayList<>();
        }
    }

    // Helper methods for family account management
    
    private boolean requiresParentalApproval(Transaction transaction, FamilyMember member) {
        if (member == null || member.getRole() == FamilyRole.PARENT) {
            return false;
        }
        
        // Check if transaction amount exceeds member's spending limit
        BigDecimal spendingLimit = member.getSpendingLimits().getDailyLimit();
        if (transaction.getAmount().compareTo(spendingLimit) > 0) {
            return true;
        }
        
        // Check if category requires approval
        List<String> restrictedCategories = member.getSpendingLimits().getRestrictedCategories();
        if (restrictedCategories.contains(transaction.getCategory())) {
            return true;
        }
        
        // Check if merchant is blocked
        List<String> blockedMerchants = member.getSpendingLimits().getBlockedMerchants();
        if (blockedMerchants.contains(transaction.getMerchantName())) {
            return true;
        }
        
        return false;
    }
    
    private String getApprovalStatus(UUID transactionId) {
        try {
            TransactionApproval approval = approvalRepository.findByTransactionId(transactionId);
            return approval != null ? approval.getStatus().name() : "NOT_REQUIRED";
        } catch (Exception e) {
            log.warn("Error getting approval status for transaction: {}", transactionId, e);
            return "UNKNOWN";
        }
    }
    
    private String calculateApprovalPriority(TransactionApproval approval, Transaction transaction) {
        // High priority for large amounts
        if (transaction != null && transaction.getAmount().compareTo(new BigDecimal("500")) > 0) {
            return "HIGH";
        }
        
        // High priority for urgent requests
        if (approval.getApprovalType() == ApprovalType.URGENT) {
            return "HIGH";
        }
        
        // Medium priority for time-sensitive transactions
        if (approval.getExpiresAt() != null && 
            approval.getExpiresAt().isBefore(LocalDateTime.now().plusHours(2))) {
            return "MEDIUM";
        }
        
        return "LOW";
    }
}