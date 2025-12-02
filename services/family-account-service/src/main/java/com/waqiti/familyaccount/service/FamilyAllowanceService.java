package com.waqiti.familyaccount.service;

import com.waqiti.familyaccount.domain.FamilyAccount;
import com.waqiti.familyaccount.domain.FamilyMember;
import com.waqiti.familyaccount.exception.FamilyAccountException;
import com.waqiti.familyaccount.exception.InsufficientFundsException;
import com.waqiti.familyaccount.repository.FamilyAccountRepository;
import com.waqiti.familyaccount.repository.FamilyMemberRepository;
import com.waqiti.familyaccount.service.integration.FamilyExternalServiceFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Family Allowance Service
 *
 * Handles allowance payments to family members
 * Supports scheduled allowance processing
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FamilyAllowanceService {

    private final FamilyAccountRepository familyAccountRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyExternalServiceFacade externalServiceFacade;

    /**
     * Process manual allowance payment
     *
     * @param familyMemberId Family member ID
     * @param amount Allowance amount
     * @param requestingUserId User initiating payment (must be parent)
     * @throws FamilyAccountException if validation fails
     * @throws InsufficientFundsException if family wallet has insufficient funds
     */
    @Transactional
    public void payAllowance(Long familyMemberId, BigDecimal amount, String requestingUserId) {
        log.info("Processing manual allowance payment: {} for member: {}", amount, familyMemberId);

        FamilyMember familyMember = familyMemberRepository.findById(familyMemberId)
            .orElseThrow(() -> new FamilyAccountException("Family member not found: " + familyMemberId));

        FamilyAccount familyAccount = familyMember.getFamilyAccount();

        // Validate requesting user is parent
        validateParentAccess(familyAccount, requestingUserId);

        // Validate amount
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new FamilyAccountException("Allowance amount must be greater than zero");
        }

        // Check family wallet balance
        BigDecimal familyWalletBalance = externalServiceFacade.getWalletBalance(familyAccount.getFamilyWalletId());
        if (familyWalletBalance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(familyWalletBalance, amount);
        }

        // Transfer from family wallet to member wallet
        externalServiceFacade.transferFunds(
            familyAccount.getFamilyWalletId(),
            familyMember.getWalletId(),
            amount,
            "Manual allowance payment"
        );

        // Update last allowance date
        familyMember.setLastAllowanceDate(LocalDate.now());
        familyMemberRepository.save(familyMember);

        // Send notification
        externalServiceFacade.sendAllowancePaidNotification(
            familyAccount.getFamilyId(),
            familyMember.getUserId(),
            amount
        );

        log.info("Successfully processed manual allowance payment for member: {}", familyMemberId);
    }

    /**
     * Process scheduled allowance payments
     * Runs daily at 9:00 AM
     */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void processScheduledAllowances() {
        log.info("Starting scheduled allowance processing");

        try {
            // Process monthly allowances
            processMonthlyAllowances();

            // Process weekly allowances
            processWeeklyAllowances();

            // Process daily allowances
            processDailyAllowances();

            log.info("Completed scheduled allowance processing");
        } catch (Exception e) {
            log.error("Error processing scheduled allowances", e);
        }
    }

    /**
     * Process monthly allowances
     */
    private void processMonthlyAllowances() {
        int currentDay = LocalDate.now().getDayOfMonth();

        List<FamilyAccount> familyAccounts = familyAccountRepository.findByAllowanceDayOfMonth(currentDay);

        for (FamilyAccount familyAccount : familyAccounts) {
            List<FamilyMember> members = familyMemberRepository.findByFamilyAccountAndMemberStatus(
                familyAccount,
                FamilyMember.MemberStatus.ACTIVE
            );

            for (FamilyMember member : members) {
                if ("MONTHLY".equalsIgnoreCase(member.getAllowanceFrequency())
                        && member.getAllowanceAmount() != null
                        && member.getAllowanceAmount().compareTo(BigDecimal.ZERO) > 0) {

                    processAllowancePayment(familyAccount, member);
                }
            }
        }
    }

    /**
     * Process weekly allowances
     */
    private void processWeeklyAllowances() {
        List<FamilyAccount> allFamilyAccounts = familyAccountRepository.findAll();

        for (FamilyAccount familyAccount : allFamilyAccounts) {
            List<FamilyMember> members = familyMemberRepository.findByFamilyAccountAndMemberStatus(
                familyAccount,
                FamilyMember.MemberStatus.ACTIVE
            );

            for (FamilyMember member : members) {
                if ("WEEKLY".equalsIgnoreCase(member.getAllowanceFrequency())
                        && member.getAllowanceAmount() != null
                        && member.getAllowanceAmount().compareTo(BigDecimal.ZERO) > 0) {

                    // Check if 7 days have passed since last allowance
                    if (shouldPayWeeklyAllowance(member)) {
                        processAllowancePayment(familyAccount, member);
                    }
                }
            }
        }
    }

    /**
     * Process daily allowances
     */
    private void processDailyAllowances() {
        List<FamilyAccount> allFamilyAccounts = familyAccountRepository.findAll();

        for (FamilyAccount familyAccount : allFamilyAccounts) {
            List<FamilyMember> members = familyMemberRepository.findByFamilyAccountAndMemberStatus(
                familyAccount,
                FamilyMember.MemberStatus.ACTIVE
            );

            for (FamilyMember member : members) {
                if ("DAILY".equalsIgnoreCase(member.getAllowanceFrequency())
                        && member.getAllowanceAmount() != null
                        && member.getAllowanceAmount().compareTo(BigDecimal.ZERO) > 0) {

                    // Check if 1 day has passed since last allowance
                    if (shouldPayDailyAllowance(member)) {
                        processAllowancePayment(familyAccount, member);
                    }
                }
            }
        }
    }

    /**
     * Process allowance payment for a member
     */
    private void processAllowancePayment(FamilyAccount familyAccount, FamilyMember member) {
        try {
            log.debug("Processing allowance for member: {}", member.getUserId());

            // Check family wallet balance
            BigDecimal familyWalletBalance = externalServiceFacade.getWalletBalance(familyAccount.getFamilyWalletId());
            if (familyWalletBalance.compareTo(member.getAllowanceAmount()) < 0) {
                log.warn("Insufficient funds for allowance payment: family={}, member={}, required={}, available={}",
                    familyAccount.getFamilyId(), member.getUserId(), member.getAllowanceAmount(), familyWalletBalance);

                // Notify parent about insufficient funds
                externalServiceFacade.sendInsufficientFundsNotification(
                    familyAccount.getFamilyId(),
                    familyAccount.getPrimaryParentUserId(),
                    member.getUserId()
                );
                return;
            }

            // Transfer funds
            externalServiceFacade.transferFunds(
                familyAccount.getFamilyWalletId(),
                member.getWalletId(),
                member.getAllowanceAmount(),
                "Scheduled " + member.getAllowanceFrequency().toLowerCase() + " allowance"
            );

            // Handle auto-savings if enabled
            if (familyAccount.getAutoSavingsEnabled() && familyAccount.getAutoSavingsPercentage() != null) {
                BigDecimal savingsAmount = member.getAllowanceAmount()
                    .multiply(familyAccount.getAutoSavingsPercentage())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

                if (savingsAmount.compareTo(BigDecimal.ZERO) > 0) {
                    externalServiceFacade.transferToSavings(
                        member.getWalletId(),
                        member.getUserId(),
                        savingsAmount
                    );
                }
            }

            // Update last allowance date
            member.setLastAllowanceDate(LocalDate.now());
            familyMemberRepository.save(member);

            // Send success notification
            externalServiceFacade.sendAllowancePaidNotification(
                familyAccount.getFamilyId(),
                member.getUserId(),
                member.getAllowanceAmount()
            );

            log.info("Successfully processed allowance for member: {}", member.getUserId());

        } catch (Exception e) {
            log.error("Error processing allowance for member: {}", member.getUserId(), e);
            // Continue with other members even if one fails
        }
    }

    /**
     * Check if weekly allowance should be paid
     */
    private boolean shouldPayWeeklyAllowance(FamilyMember member) {
        if (member.getLastAllowanceDate() == null) {
            return true;
        }

        long daysSinceLastAllowance = ChronoUnit.DAYS.between(member.getLastAllowanceDate(), LocalDate.now());
        return daysSinceLastAllowance >= 7;
    }

    /**
     * Check if daily allowance should be paid
     */
    private boolean shouldPayDailyAllowance(FamilyMember member) {
        if (member.getLastAllowanceDate() == null) {
            return true;
        }

        return !member.getLastAllowanceDate().equals(LocalDate.now());
    }

    /**
     * Validate user is a parent
     */
    private void validateParentAccess(FamilyAccount familyAccount, String userId) {
        boolean isParent = familyAccount.getPrimaryParentUserId().equals(userId)
            || (familyAccount.getSecondaryParentUserId() != null
                && familyAccount.getSecondaryParentUserId().equals(userId));

        if (!isParent) {
            throw new FamilyAccountException("Only parents can process allowance payments");
        }
    }
}
