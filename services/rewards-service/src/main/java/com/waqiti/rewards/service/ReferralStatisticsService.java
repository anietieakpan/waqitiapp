package com.waqiti.rewards.service;

import com.waqiti.rewards.domain.ReferralStatistics;
import com.waqiti.rewards.repository.ReferralStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for aggregating and managing referral statistics
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReferralStatisticsService {

    private final ReferralStatisticsRepository statisticsRepository;

    /**
     * Gets or creates statistics for a period
     */
    @Transactional
    public ReferralStatistics getOrCreateStatistics(String programId, LocalDate periodStart, LocalDate periodEnd) {
        return statisticsRepository.findByProgramIdAndPeriodStartAndPeriodEnd(programId, periodStart, periodEnd)
                .orElseGet(() -> createNewStatistics(programId, periodStart, periodEnd));
    }

    /**
     * Creates new statistics record
     */
    private ReferralStatistics createNewStatistics(String programId, LocalDate periodStart, LocalDate periodEnd) {
        ReferralStatistics stats = ReferralStatistics.builder()
                .programId(programId)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .build();

        return statisticsRepository.save(stats);
    }

    /**
     * Increments referral metrics
     */
    @Transactional
    public void recordReferral(String programId, LocalDate periodStart, LocalDate periodEnd) {
        ReferralStatistics stats = getOrCreateStatistics(programId, periodStart, periodEnd);
        stats.incrementTotalReferrals();
        stats.recalculateMetrics();
        statisticsRepository.save(stats);
        log.debug("Recorded referral for period: program={}, period={} to {}",
                programId, periodStart, periodEnd);
    }

    /**
     * Increments successful referral count
     */
    @Transactional
    public void recordSuccessfulReferral(String programId, LocalDate periodStart, LocalDate periodEnd) {
        ReferralStatistics stats = getOrCreateStatistics(programId, periodStart, periodEnd);
        stats.incrementSuccessfulReferrals();
        stats.recalculateMetrics();
        statisticsRepository.save(stats);
    }

    /**
     * Records click metrics
     */
    @Transactional
    public void recordClick(String programId, LocalDate periodStart, LocalDate periodEnd, boolean isUnique) {
        ReferralStatistics stats = getOrCreateStatistics(programId, periodStart, periodEnd);
        stats.incrementTotalClicks();
        if (isUnique) {
            stats.incrementUniqueClicks();
        }
        stats.recalculateMetrics();
        statisticsRepository.save(stats);
    }

    /**
     * Records signup
     */
    @Transactional
    public void recordSignup(String programId, LocalDate periodStart, LocalDate periodEnd) {
        ReferralStatistics stats = getOrCreateStatistics(programId, periodStart, periodEnd);
        stats.incrementTotalSignups();
        stats.recalculateMetrics();
        statisticsRepository.save(stats);
    }

    /**
     * Records reward issuance
     */
    @Transactional
    public void recordRewardIssued(String programId, LocalDate periodStart, LocalDate periodEnd, BigDecimal amount) {
        ReferralStatistics stats = getOrCreateStatistics(programId, periodStart, periodEnd);
        stats.addRewardsIssued(amount);
        stats.recalculateMetrics();
        statisticsRepository.save(stats);
    }

    /**
     * Records revenue generated
     */
    @Transactional
    public void recordRevenue(String programId, LocalDate periodStart, LocalDate periodEnd, BigDecimal amount) {
        ReferralStatistics stats = getOrCreateStatistics(programId, periodStart, periodEnd);
        stats.addRevenueGenerated(amount);
        stats.recalculateMetrics();
        statisticsRepository.save(stats);
    }

    /**
     * Gets latest statistics for a program
     */
    public Optional<ReferralStatistics> getLatestStatistics(String programId) {
        return statisticsRepository.findLatestStatistics(programId);
    }

    /**
     * Gets all statistics for a program
     */
    public List<ReferralStatistics> getProgramStatistics(String programId) {
        return statisticsRepository.findByProgramIdOrderByPeriodDesc(programId);
    }

    /**
     * Gets statistics for a date range
     */
    public List<ReferralStatistics> getStatisticsBetween(LocalDate startDate, LocalDate endDate) {
        return statisticsRepository.findStatisticsBetween(startDate, endDate);
    }

    /**
     * Gets ROI for a period
     */
    public BigDecimal calculateROI(String programId, LocalDate periodStart, LocalDate periodEnd) {
        Optional<ReferralStatistics> stats = statisticsRepository
                .findByProgramIdAndPeriodStartAndPeriodEnd(programId, periodStart, periodEnd);

        return stats.map(ReferralStatistics::getROI).orElse(BigDecimal.ZERO);
    }

    /**
     * Gets cost per acquisition
     */
    public BigDecimal getCostPerAcquisition(String programId, LocalDate periodStart, LocalDate periodEnd) {
        Optional<ReferralStatistics> stats = statisticsRepository
                .findByProgramIdAndPeriodStartAndPeriodEnd(programId, periodStart, periodEnd);

        return stats.map(ReferralStatistics::getCostPerAcquisition).orElse(BigDecimal.ZERO);
    }
}
