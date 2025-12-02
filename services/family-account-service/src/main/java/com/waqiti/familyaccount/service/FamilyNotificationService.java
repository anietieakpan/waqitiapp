package com.waqiti.familyaccount.service;

import com.waqiti.familyaccount.domain.FamilyAccount;
import com.waqiti.familyaccount.domain.FamilyMember;
import com.waqiti.familyaccount.repository.FamilyAccountRepository;
import com.waqiti.familyaccount.repository.FamilyMemberRepository;
import com.waqiti.familyaccount.service.integration.FamilyExternalServiceFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Family Notification Service
 *
 * Centralized notification handling for family account events
 * Coordinates with external notification service
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FamilyNotificationService {

    private final FamilyAccountRepository familyAccountRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyExternalServiceFacade externalServiceFacade;

    /**
     * Notify all family members of an event
     *
     * @param familyId Family account ID
     * @param subject Notification subject
     * @param message Notification message
     */
    public void notifyAllFamilyMembers(String familyId, String subject, String message) {
        log.debug("Notifying all family members for family: {}", familyId);

        try {
            FamilyAccount familyAccount = familyAccountRepository.findByFamilyId(familyId)
                .orElse(null);

            if (familyAccount == null) {
                log.warn("Family account not found: {}", familyId);
                return;
            }

            List<FamilyMember> members = familyMemberRepository.findByFamilyAccountAndMemberStatus(
                familyAccount,
                FamilyMember.MemberStatus.ACTIVE
            );

            for (FamilyMember member : members) {
                externalServiceFacade.sendNotification(member.getUserId(), subject, message);
            }

            // Also notify parents
            externalServiceFacade.sendNotification(
                familyAccount.getPrimaryParentUserId(),
                subject,
                message
            );

            if (familyAccount.getSecondaryParentUserId() != null) {
                externalServiceFacade.sendNotification(
                    familyAccount.getSecondaryParentUserId(),
                    subject,
                    message
                );
            }

        } catch (Exception e) {
            log.error("Error notifying family members for family: {}", familyId, e);
        }
    }

    /**
     * Notify parents only
     *
     * @param familyId Family account ID
     * @param subject Notification subject
     * @param message Notification message
     */
    public void notifyParents(String familyId, String subject, String message) {
        log.debug("Notifying parents for family: {}", familyId);

        try {
            FamilyAccount familyAccount = familyAccountRepository.findByFamilyId(familyId)
                .orElse(null);

            if (familyAccount == null) {
                log.warn("Family account not found: {}", familyId);
                return;
            }

            externalServiceFacade.sendNotification(
                familyAccount.getPrimaryParentUserId(),
                subject,
                message
            );

            if (familyAccount.getSecondaryParentUserId() != null) {
                externalServiceFacade.sendNotification(
                    familyAccount.getSecondaryParentUserId(),
                    subject,
                    message
                );
            }

        } catch (Exception e) {
            log.error("Error notifying parents for family: {}", familyId, e);
        }
    }

    /**
     * Notify member of low balance
     *
     * @param familyMemberId Family member ID
     * @param currentBalance Current wallet balance
     */
    public void notifyLowBalance(Long familyMemberId, BigDecimal currentBalance) {
        log.debug("Notifying low balance for member: {}", familyMemberId);

        try {
            FamilyMember member = familyMemberRepository.findById(familyMemberId)
                .orElse(null);

            if (member == null) {
                log.warn("Family member not found: {}", familyMemberId);
                return;
            }

            String message = String.format(
                "Your wallet balance is low: %s. Please ask your parent to add more funds.",
                currentBalance
            );

            externalServiceFacade.sendNotification(
                member.getUserId(),
                "Low Balance Alert",
                message
            );

            // Also notify parents
            notifyParents(
                member.getFamilyAccount().getFamilyId(),
                "Member Low Balance Alert",
                String.format("Family member %s has a low balance: %s", member.getUserId(), currentBalance)
            );

        } catch (Exception e) {
            log.error("Error notifying low balance for member: {}", familyMemberId, e);
        }
    }

    /**
     * Notify member and parents of declined transaction
     *
     * @param familyMemberId Family member ID
     * @param amount Transaction amount
     * @param merchantName Merchant name
     * @param reason Decline reason
     */
    public void notifyDeclinedTransaction(
            Long familyMemberId,
            BigDecimal amount,
            String merchantName,
            String reason) {

        log.debug("Notifying declined transaction for member: {}", familyMemberId);

        try {
            FamilyMember member = familyMemberRepository.findById(familyMemberId)
                .orElse(null);

            if (member == null) {
                log.warn("Family member not found: {}", familyMemberId);
                return;
            }

            // Notify member
            String memberMessage = String.format(
                "Your transaction at %s for %s was declined. Reason: %s",
                merchantName,
                amount,
                reason
            );

            externalServiceFacade.sendNotification(
                member.getUserId(),
                "Transaction Declined",
                memberMessage
            );

            // Notify parents
            String parentMessage = String.format(
                "Transaction declined for family member %s at %s for %s. Reason: %s",
                member.getUserId(),
                merchantName,
                amount,
                reason
            );

            notifyParents(
                member.getFamilyAccount().getFamilyId(),
                "Member Transaction Declined",
                parentMessage
            );

        } catch (Exception e) {
            log.error("Error notifying declined transaction for member: {}", familyMemberId, e);
        }
    }

    /**
     * Notify parents of unusual spending activity
     *
     * @param familyMemberId Family member ID
     * @param activityDescription Description of unusual activity
     */
    public void notifyUnusualActivity(Long familyMemberId, String activityDescription) {
        log.warn("Notifying unusual activity for member: {}", familyMemberId);

        try {
            FamilyMember member = familyMemberRepository.findById(familyMemberId)
                .orElse(null);

            if (member == null) {
                log.warn("Family member not found: {}", familyMemberId);
                return;
            }

            String message = String.format(
                "Unusual spending activity detected for family member %s: %s",
                member.getUserId(),
                activityDescription
            );

            notifyParents(
                member.getFamilyAccount().getFamilyId(),
                "Unusual Activity Alert",
                message
            );

        } catch (Exception e) {
            log.error("Error notifying unusual activity for member: {}", familyMemberId, e);
        }
    }

    /**
     * Send weekly summary to parents
     *
     * @param familyId Family account ID
     * @param totalSpending Total spending this week
     * @param transactionCount Number of transactions
     */
    public void sendWeeklySummary(String familyId, BigDecimal totalSpending, int transactionCount) {
        log.debug("Sending weekly summary for family: {}", familyId);

        try {
            String message = String.format(
                "Weekly Family Account Summary:\n" +
                "Total Spending: %s\n" +
                "Total Transactions: %d\n" +
                "Check the app for detailed breakdown.",
                totalSpending,
                transactionCount
            );

            notifyParents(familyId, "Weekly Family Account Summary", message);

        } catch (Exception e) {
            log.error("Error sending weekly summary for family: {}", familyId, e);
        }
    }

    /**
     * Send monthly summary to parents
     *
     * @param familyId Family account ID
     * @param totalSpending Total spending this month
     * @param transactionCount Number of transactions
     * @param topCategory Top spending category
     */
    public void sendMonthlySummary(
            String familyId,
            BigDecimal totalSpending,
            int transactionCount,
            String topCategory) {

        log.debug("Sending monthly summary for family: {}", familyId);

        try {
            String message = String.format(
                "Monthly Family Account Summary:\n" +
                "Total Spending: %s\n" +
                "Total Transactions: %d\n" +
                "Top Category: %s\n" +
                "Check the app for detailed analytics.",
                totalSpending,
                transactionCount,
                topCategory
            );

            notifyParents(familyId, "Monthly Family Account Summary", message);

        } catch (Exception e) {
            log.error("Error sending monthly summary for family: {}", familyId, e);
        }
    }
}
