package com.waqiti.familyaccount.service;

import com.waqiti.familyaccount.domain.FamilyMember;
import com.waqiti.familyaccount.domain.FamilySpendingRule;
import com.waqiti.familyaccount.domain.TransactionAttempt;
import com.waqiti.familyaccount.dto.TransactionAuthorizationRequest;
import com.waqiti.familyaccount.dto.TransactionAuthorizationResponse;
import com.waqiti.familyaccount.exception.FamilyMemberNotFoundException;
import com.waqiti.familyaccount.exception.InsufficientFundsException;
import com.waqiti.familyaccount.exception.SpendingLimitExceededException;
import com.waqiti.familyaccount.repository.FamilyMemberRepository;
import com.waqiti.familyaccount.repository.TransactionAttemptRepository;
import com.waqiti.familyaccount.service.integration.FamilyExternalServiceFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Family Transaction Authorization Service
 *
 * Handles transaction authorization logic for family members
 * Enforces spending limits, spending rules, and wallet balance
 *
 * This is the most complex service in the family-account domain
 * Coordinates multiple validation layers
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FamilyTransactionAuthorizationService {

    private final FamilyMemberRepository familyMemberRepository;
    private final TransactionAttemptRepository transactionAttemptRepository;
    private final SpendingLimitService spendingLimitService;
    private final SpendingRuleService spendingRuleService;
    private final FamilyExternalServiceFacade externalServiceFacade;

    /**
     * Authorize transaction for family member
     *
     * Multi-layer authorization logic:
     * 0. Check idempotency - if idempotency key provided and transaction already exists, return cached result
     * 1. Check member exists and is active
     * 2. Check wallet balance
     * 3. Check spending limits (daily/weekly/monthly)
     * 4. Check spending rules (merchant, time, amount restrictions)
     * 5. Check if parent approval required
     *
     * @param request Transaction authorization request
     * @param idempotencyKey Optional idempotency key for duplicate prevention
     * @return Authorization response
     */
    @Transactional
    public TransactionAuthorizationResponse authorizeTransaction(
            TransactionAuthorizationRequest request, String idempotencyKey) {
        log.info("Authorizing transaction for user: {} amount: {} idempotencyKey: {}",
                request.getUserId(), request.getTransactionAmount(), idempotencyKey != null ? idempotencyKey : "none");

        // 0. Check idempotency - return cached result if transaction already processed
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            java.util.Optional<TransactionAttempt> existingAttempt =
                transactionAttemptRepository.findByIdempotencyKey(idempotencyKey);

            if (existingAttempt.isPresent()) {
                log.info("Idempotent request detected - returning cached result for key: {}", idempotencyKey);
                return buildResponseFromCachedAttempt(existingAttempt.get());
            }

            log.debug("No existing transaction found for idempotency key: {}", idempotencyKey);
        }

        LocalDateTime attemptTime = LocalDateTime.now();
        boolean authorized = false;
        String declineReason = null;
        boolean requiresParentApproval = false;
        String approvalMessage = null;

        try {
            // 1. Get family member
            FamilyMember familyMember = familyMemberRepository.findByUserId(request.getUserId())
                .orElseThrow(() -> new FamilyMemberNotFoundException(request.getUserId()));

            // 1a. Check member is active
            if (familyMember.getMemberStatus() != FamilyMember.MemberStatus.ACTIVE) {
                declineReason = "Family member account is not active";
                return createResponse(false, declineReason, false, null, null);
            }

            // 2. Check wallet balance
            BigDecimal walletBalance = externalServiceFacade.getWalletBalance(familyMember.getWalletId());
            if (walletBalance.compareTo(request.getTransactionAmount()) < 0) {
                declineReason = "Insufficient wallet balance";
                recordTransactionAttempt(familyMember, request, attemptTime, false, declineReason, false, idempotencyKey);
                return createResponse(false, declineReason, false, null,
                    transactionAttemptRepository.findByFamilyMember(familyMember,
                        org.springframework.data.domain.PageRequest.of(0, 1)).getContent().get(0).getId());
            }

            // 3. Check spending limits
            try {
                spendingLimitService.validateSpendingLimits(familyMember, request.getTransactionAmount());
            } catch (SpendingLimitExceededException e) {
                declineReason = e.getMessage();
                recordTransactionAttempt(familyMember, request, attemptTime, false, declineReason, false, idempotencyKey);
                return createResponse(false, declineReason, false, null,
                    transactionAttemptRepository.findByFamilyMember(familyMember,
                        org.springframework.data.domain.PageRequest.of(0, 1)).getContent().get(0).getId());
            }

            // 4. Check spending rules
            FamilySpendingRule violatedRule = spendingRuleService.checkRuleViolation(
                familyMember,
                request.getMerchantCategory(),
                request.getTransactionAmount(),
                attemptTime
            );

            if (violatedRule != null) {
                if (violatedRule.getRequiresApproval()) {
                    requiresParentApproval = true;
                    approvalMessage = "Transaction requires parent approval due to rule: " + violatedRule.getRuleName();
                    authorized = false;
                } else {
                    declineReason = "Transaction violates spending rule: " + violatedRule.getRuleName();
                    recordTransactionAttempt(familyMember, request, attemptTime, false, declineReason, false, idempotencyKey);
                    return createResponse(false, declineReason, false, null,
                        transactionAttemptRepository.findByFamilyMember(familyMember,
                            org.springframework.data.domain.PageRequest.of(0, 1)).getContent().get(0).getId());
                }
            }

            // 5. Check if member requires transaction approval
            if (familyMember.getTransactionApprovalRequired() && !requiresParentApproval) {
                requiresParentApproval = true;
                approvalMessage = "All transactions for this member require parent approval";
                authorized = false;
            }

            // 6. If no violations and no approval required, authorize transaction
            if (!requiresParentApproval) {
                authorized = true;
                log.info("Transaction authorized for user: {}", request.getUserId());
            } else {
                log.info("Transaction requires parent approval for user: {}", request.getUserId());
            }

            // Record transaction attempt
            TransactionAttempt attempt = recordTransactionAttempt(
                familyMember,
                request,
                attemptTime,
                authorized,
                declineReason,
                requiresParentApproval,
                idempotencyKey
            );

            // Send notifications
            if (authorized) {
                // Notify member of authorized transaction
                externalServiceFacade.sendTransactionAuthorizedNotification(
                    familyMember.getFamilyAccount().getFamilyId(),
                    familyMember.getUserId(),
                    request.getTransactionAmount(),
                    request.getMerchantName()
                );

                // Check if approaching spending limit and notify
                if (spendingLimitService.isApproachingLimit(familyMember, SpendingLimitService.LimitType.DAILY)) {
                    externalServiceFacade.sendApproachingLimitNotification(
                        familyMember.getFamilyAccount().getFamilyId(),
                        familyMember.getUserId(),
                        "daily"
                    );
                }

            } else if (requiresParentApproval) {
                // Notify parent of pending approval
                externalServiceFacade.sendApprovalRequiredNotification(
                    familyMember.getFamilyAccount().getFamilyId(),
                    familyMember.getFamilyAccount().getPrimaryParentUserId(),
                    familyMember.getUserId(),
                    request.getTransactionAmount(),
                    request.getMerchantName()
                );
            }

            return createResponse(authorized, declineReason, requiresParentApproval, approvalMessage, attempt.getId());

        } catch (FamilyMemberNotFoundException e) {
            declineReason = "Family member not found";
            log.error("Family member not found: {}", request.getUserId());
            return createResponse(false, declineReason, false, null, null);

        } catch (Exception e) {
            declineReason = "Error processing transaction authorization: " + e.getMessage();
            log.error("Error authorizing transaction for user: {}", request.getUserId(), e);
            return createResponse(false, declineReason, false, null, null);
        }
    }

    /**
     * Approve pending transaction
     *
     * @param transactionAttemptId Transaction attempt ID
     * @param parentUserId Parent approving transaction
     * @return True if approved successfully
     */
    @Transactional
    public boolean approveTransaction(Long transactionAttemptId, String parentUserId) {
        log.info("Parent {} approving transaction: {}", parentUserId, transactionAttemptId);

        TransactionAttempt attempt = transactionAttemptRepository.findById(transactionAttemptId)
            .orElseThrow(() -> new RuntimeException("Transaction attempt not found: " + transactionAttemptId));

        FamilyMember familyMember = attempt.getFamilyMember();

        // Validate parent access
        boolean isParent = familyMember.getFamilyAccount().getPrimaryParentUserId().equals(parentUserId)
            || (familyMember.getFamilyAccount().getSecondaryParentUserId() != null
                && familyMember.getFamilyAccount().getSecondaryParentUserId().equals(parentUserId));

        if (!isParent) {
            throw new RuntimeException("Only parents can approve transactions");
        }

        // Check if already approved or declined
        if (attempt.getApprovalStatus() != null && !attempt.getApprovalStatus().equals("PENDING")) {
            throw new RuntimeException("Transaction already processed: " + attempt.getApprovalStatus());
        }

        // Re-validate wallet balance
        BigDecimal walletBalance = externalServiceFacade.getWalletBalance(familyMember.getWalletId());
        if (walletBalance.compareTo(attempt.getAmount()) < 0) {
            attempt.setApprovalStatus("DECLINED");
            attempt.setDeclineReason("Insufficient wallet balance at approval time");
            transactionAttemptRepository.save(attempt);
            return false;
        }

        // Approve transaction
        attempt.setAuthorized(true);
        attempt.setApprovalStatus("APPROVED");
        attempt.setApprovedByUserId(parentUserId);
        attempt.setApprovalTime(LocalDateTime.now());
        transactionAttemptRepository.save(attempt);

        // Send notifications
        externalServiceFacade.sendTransactionApprovedNotification(
            familyMember.getFamilyAccount().getFamilyId(),
            familyMember.getUserId(),
            attempt.getAmount(),
            attempt.getMerchantName()
        );

        log.info("Transaction approved: {}", transactionAttemptId);
        return true;
    }

    /**
     * Decline pending transaction
     *
     * @param transactionAttemptId Transaction attempt ID
     * @param parentUserId Parent declining transaction
     * @param reason Decline reason
     */
    @Transactional
    public void declineTransaction(Long transactionAttemptId, String parentUserId, String reason) {
        log.info("Parent {} declining transaction: {}", parentUserId, transactionAttemptId);

        TransactionAttempt attempt = transactionAttemptRepository.findById(transactionAttemptId)
            .orElseThrow(() -> new RuntimeException("Transaction attempt not found: " + transactionAttemptId));

        FamilyMember familyMember = attempt.getFamilyMember();

        // Validate parent access
        boolean isParent = familyMember.getFamilyAccount().getPrimaryParentUserId().equals(parentUserId)
            || (familyMember.getFamilyAccount().getSecondaryParentUserId() != null
                && familyMember.getFamilyAccount().getSecondaryParentUserId().equals(parentUserId));

        if (!isParent) {
            throw new RuntimeException("Only parents can decline transactions");
        }

        // Decline transaction
        attempt.setAuthorized(false);
        attempt.setApprovalStatus("DECLINED");
        attempt.setDeclineReason(reason);
        attempt.setApprovedByUserId(parentUserId);
        attempt.setApprovalTime(LocalDateTime.now());
        transactionAttemptRepository.save(attempt);

        // Send notification
        externalServiceFacade.sendTransactionDeclinedNotification(
            familyMember.getFamilyAccount().getFamilyId(),
            familyMember.getUserId(),
            attempt.getAmount(),
            attempt.getMerchantName(),
            reason
        );

        log.info("Transaction declined: {}", transactionAttemptId);
    }

    /**
     * Record transaction attempt
     *
     * @param familyMember Family member making the transaction
     * @param request Transaction authorization request
     * @param attemptTime Time of the attempt
     * @param authorized Whether transaction was authorized
     * @param declineReason Reason for decline (if declined)
     * @param requiresParentApproval Whether parent approval is required
     * @param idempotencyKey Idempotency key for duplicate prevention
     * @return Saved transaction attempt
     */
    private TransactionAttempt recordTransactionAttempt(
            FamilyMember familyMember,
            TransactionAuthorizationRequest request,
            LocalDateTime attemptTime,
            boolean authorized,
            String declineReason,
            boolean requiresParentApproval,
            String idempotencyKey) {

        TransactionAttempt attempt = TransactionAttempt.builder()
            .familyMember(familyMember)
            .amount(request.getTransactionAmount())
            .merchantName(request.getMerchantName())
            .merchantCategory(request.getMerchantCategory())
            .description(request.getDescription())
            .attemptTime(attemptTime)
            .authorized(authorized)
            .declineReason(declineReason)
            .requiresParentApproval(requiresParentApproval)
            .approvalStatus(requiresParentApproval ? "PENDING" : (authorized ? "APPROVED" : "DECLINED"))
            .idempotencyKey(idempotencyKey)  // Set idempotency key
            .build();

        return transactionAttemptRepository.save(attempt);
    }

    /**
     * Build response from cached transaction attempt (idempotent request)
     *
     * This method is called when the same idempotency key is sent multiple times.
     * Instead of reprocessing the transaction, we return the cached result.
     *
     * @param cachedAttempt Previously processed transaction attempt
     * @return Authorization response built from cached attempt
     */
    private TransactionAuthorizationResponse buildResponseFromCachedAttempt(TransactionAttempt cachedAttempt) {
        log.debug("Building response from cached attempt ID: {}", cachedAttempt.getId());

        String approvalMessage = null;
        if (cachedAttempt.getRequiresParentApproval() && "PENDING".equals(cachedAttempt.getApprovalStatus())) {
            approvalMessage = "Transaction requires parent approval";
        }

        return TransactionAuthorizationResponse.builder()
            .authorized(cachedAttempt.getAuthorized())
            .declineReason(cachedAttempt.getDeclineReason())
            .requiresParentApproval(cachedAttempt.getRequiresParentApproval())
            .approvalMessage(approvalMessage)
            .transactionAttemptId(cachedAttempt.getId())
            .build();
    }

    /**
     * Create authorization response
     */
    private TransactionAuthorizationResponse createResponse(
            boolean authorized,
            String declineReason,
            boolean requiresParentApproval,
            String approvalMessage,
            Long transactionAttemptId) {

        return TransactionAuthorizationResponse.builder()
            .authorized(authorized)
            .declineReason(declineReason)
            .requiresParentApproval(requiresParentApproval)
            .approvalMessage(approvalMessage)
            .transactionAttemptId(transactionAttemptId)
            .build();
    }
}
