package com.waqiti.familyaccount.service;

import com.waqiti.familyaccount.domain.FamilyMember;
import com.waqiti.familyaccount.domain.TransactionAttempt;
import com.waqiti.familyaccount.exception.SpendingLimitExceededException;
import com.waqiti.familyaccount.repository.TransactionAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * Spending Limit Service
 *
 * Handles spending limit validation and tracking
 * Calculates daily, weekly, and monthly spending
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpendingLimitService {

    private final TransactionAttemptRepository transactionAttemptRepository;

    /**
     * Check if transaction is within spending limits
     *
     * @param familyMember Family member attempting transaction
     * @param transactionAmount Transaction amount
     * @throws SpendingLimitExceededException if any limit exceeded
     */
    @Transactional(readOnly = true)
    public void validateSpendingLimits(FamilyMember familyMember, BigDecimal transactionAmount) {
        log.debug("Validating spending limits for member: {} amount: {}", familyMember.getUserId(), transactionAmount);

        // Check daily limit
        if (familyMember.getDailySpendingLimit() != null) {
            BigDecimal dailySpent = calculateDailySpending(familyMember);
            BigDecimal totalDaily = dailySpent.add(transactionAmount);

            if (totalDaily.compareTo(familyMember.getDailySpendingLimit()) > 0) {
                log.warn("Daily spending limit exceeded for member: {}", familyMember.getUserId());
                throw new SpendingLimitExceededException(
                    "Daily",
                    familyMember.getDailySpendingLimit(),
                    totalDaily
                );
            }
        }

        // Check weekly limit
        if (familyMember.getWeeklySpendingLimit() != null) {
            BigDecimal weeklySpent = calculateWeeklySpending(familyMember);
            BigDecimal totalWeekly = weeklySpent.add(transactionAmount);

            if (totalWeekly.compareTo(familyMember.getWeeklySpendingLimit()) > 0) {
                log.warn("Weekly spending limit exceeded for member: {}", familyMember.getUserId());
                throw new SpendingLimitExceededException(
                    "Weekly",
                    familyMember.getWeeklySpendingLimit(),
                    totalWeekly
                );
            }
        }

        // Check monthly limit
        if (familyMember.getMonthlySpendingLimit() != null) {
            BigDecimal monthlySpent = calculateMonthlySpending(familyMember);
            BigDecimal totalMonthly = monthlySpent.add(transactionAmount);

            if (totalMonthly.compareTo(familyMember.getMonthlySpendingLimit()) > 0) {
                log.warn("Monthly spending limit exceeded for member: {}", familyMember.getUserId());
                throw new SpendingLimitExceededException(
                    "Monthly",
                    familyMember.getMonthlySpendingLimit(),
                    totalMonthly
                );
            }
        }

        log.debug("Spending limits validated successfully for member: {}", familyMember.getUserId());
    }

    /**
     * Calculate daily spending for family member
     *
     * @param familyMember Family member
     * @return Total spending today
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateDailySpending(FamilyMember familyMember) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);

        return calculateSpendingInPeriod(familyMember, startOfDay, endOfDay);
    }

    /**
     * Calculate weekly spending for family member
     *
     * @param familyMember Family member
     * @return Total spending this week
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateWeeklySpending(FamilyMember familyMember) {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfWeek = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            .atStartOfDay();
        LocalDateTime endOfWeek = today.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY))
            .atTime(LocalTime.MAX);

        return calculateSpendingInPeriod(familyMember, startOfWeek, endOfWeek);
    }

    /**
     * Calculate monthly spending for family member
     *
     * @param familyMember Family member
     * @return Total spending this month
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateMonthlySpending(FamilyMember familyMember) {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfMonth = today.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();
        LocalDateTime endOfMonth = today.with(TemporalAdjusters.lastDayOfMonth()).atTime(LocalTime.MAX);

        return calculateSpendingInPeriod(familyMember, startOfMonth, endOfMonth);
    }

    /**
     * Calculate spending in a specific time period
     *
     * @param familyMember Family member
     * @param startTime Period start
     * @param endTime Period end
     * @return Total spending in period
     */
    private BigDecimal calculateSpendingInPeriod(FamilyMember familyMember, LocalDateTime startTime, LocalDateTime endTime) {
        List<TransactionAttempt> attempts = transactionAttemptRepository.findByFamilyMemberAndAttemptTimeBetween(
            familyMember,
            startTime,
            endTime
        );

        return attempts.stream()
            .filter(TransactionAttempt::getAuthorized)
            .map(TransactionAttempt::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get remaining daily spending for family member
     *
     * @param familyMember Family member
     * @return Remaining daily spending allowance, or a very large value if no limit is set (unlimited)
     */
    @Transactional(readOnly = true)
    public BigDecimal getRemainingDailySpending(FamilyMember familyMember) {
        if (familyMember.getDailySpendingLimit() == null) {
            // No daily limit set - return max value to indicate unlimited spending
            log.debug("No daily spending limit set for member: {}", familyMember.getUserId());
            return BigDecimal.valueOf(Double.MAX_VALUE);
        }

        BigDecimal spent = calculateDailySpending(familyMember);
        BigDecimal remaining = familyMember.getDailySpendingLimit().subtract(spent);

        return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining;
    }

    /**
     * Get remaining weekly spending for family member
     *
     * @param familyMember Family member
     * @return Remaining weekly spending allowance, or a very large value if no limit is set (unlimited)
     */
    @Transactional(readOnly = true)
    public BigDecimal getRemainingWeeklySpending(FamilyMember familyMember) {
        if (familyMember.getWeeklySpendingLimit() == null) {
            // No weekly limit set - return max value to indicate unlimited spending
            log.debug("No weekly spending limit set for member: {}", familyMember.getUserId());
            return BigDecimal.valueOf(Double.MAX_VALUE);
        }

        BigDecimal spent = calculateWeeklySpending(familyMember);
        BigDecimal remaining = familyMember.getWeeklySpendingLimit().subtract(spent);

        return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining;
    }

    /**
     * Get remaining monthly spending for family member
     *
     * @param familyMember Family member
     * @return Remaining monthly spending allowance, or a very large value if no limit is set (unlimited)
     */
    @Transactional(readOnly = true)
    public BigDecimal getRemainingMonthlySpending(FamilyMember familyMember) {
        if (familyMember.getMonthlySpendingLimit() == null) {
            // No monthly limit set - return max value to indicate unlimited spending
            log.debug("No monthly spending limit set for member: {}", familyMember.getUserId());
            return BigDecimal.valueOf(Double.MAX_VALUE);
        }

        BigDecimal spent = calculateMonthlySpending(familyMember);
        BigDecimal remaining = familyMember.getMonthlySpendingLimit().subtract(spent);

        return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining;
    }

    /**
     * Check if member is approaching spending limit (80% threshold)
     *
     * @param familyMember Family member
     * @param limitType Type of limit (DAILY, WEEKLY, MONTHLY)
     * @return True if member is approaching limit
     */
    @Transactional(readOnly = true)
    public boolean isApproachingLimit(FamilyMember familyMember, LimitType limitType) {
        BigDecimal limit;
        BigDecimal spent;

        switch (limitType) {
            case DAILY:
                if (familyMember.getDailySpendingLimit() == null) return false;
                limit = familyMember.getDailySpendingLimit();
                spent = calculateDailySpending(familyMember);
                break;
            case WEEKLY:
                if (familyMember.getWeeklySpendingLimit() == null) return false;
                limit = familyMember.getWeeklySpendingLimit();
                spent = calculateWeeklySpending(familyMember);
                break;
            case MONTHLY:
                if (familyMember.getMonthlySpendingLimit() == null) return false;
                limit = familyMember.getMonthlySpendingLimit();
                spent = calculateMonthlySpending(familyMember);
                break;
            default:
                return false;
        }

        BigDecimal threshold = limit.multiply(new BigDecimal("0.80"));
        return spent.compareTo(threshold) >= 0;
    }

    /**
     * Limit type enumeration
     */
    public enum LimitType {
        DAILY,
        WEEKLY,
        MONTHLY
    }
}
