package com.waqiti.rewards.service;

import com.waqiti.rewards.domain.ReferralLeaderboard;
import com.waqiti.rewards.repository.ReferralLeaderboardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing referral leaderboards
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReferralLeaderboardService {

    private final ReferralLeaderboardRepository leaderboardRepository;

    /**
     * Gets or creates leaderboard entry for user
     */
    @Transactional
    public ReferralLeaderboard getOrCreateEntry(UUID userId, String programId, String periodType,
                                                LocalDate periodStart, LocalDate periodEnd) {
        return leaderboardRepository.findByUserIdAndProgramIdAndPeriodTypeAndPeriodStartAndPeriodEnd(
                        userId, programId, periodType, periodStart, periodEnd)
                .orElseGet(() -> createNewEntry(userId, programId, periodType, periodStart, periodEnd));
    }

    private ReferralLeaderboard createNewEntry(UUID userId, String programId, String periodType,
                                               LocalDate periodStart, LocalDate periodEnd) {
        ReferralLeaderboard entry = ReferralLeaderboard.builder()
                .userId(userId)
                .programId(programId)
                .periodType(periodType)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .build();

        return leaderboardRepository.save(entry);
    }

    /**
     * Records a referral for leaderboard
     */
    @Transactional
    public void recordReferral(UUID userId, String programId, String periodType,
                              LocalDate periodStart, LocalDate periodEnd, boolean successful) {
        ReferralLeaderboard entry = getOrCreateEntry(userId, programId, periodType, periodStart, periodEnd);
        entry.incrementTotalReferrals();
        if (successful) {
            entry.incrementSuccessfulReferrals();
        }
        leaderboardRepository.save(entry);
    }

    /**
     * Records rewards earned
     */
    @Transactional
    public void recordReward(UUID userId, String programId, String periodType,
                            LocalDate periodStart, LocalDate periodEnd,
                            Long points, BigDecimal cashback) {
        ReferralLeaderboard entry = getOrCreateEntry(userId, programId, periodType, periodStart, periodEnd);
        if (points != null) {
            entry.addPointsEarned(points);
        }
        if (cashback != null) {
            entry.addCashbackEarned(cashback);
        }
        leaderboardRepository.save(entry);
    }

    /**
     * Gets leaderboard for a period
     */
    public Page<ReferralLeaderboard> getLeaderboard(String programId, String periodType,
                                                    LocalDate periodStart, LocalDate periodEnd,
                                                    Pageable pageable) {
        return leaderboardRepository.findLeaderboardByPeriod(programId, periodType, periodStart, periodEnd, pageable);
    }

    /**
     * Gets latest leaderboard
     */
    public Page<ReferralLeaderboard> getLatestLeaderboard(String programId, String periodType, Pageable pageable) {
        return leaderboardRepository.findLatestLeaderboard(programId, periodType, pageable);
    }

    /**
     * Gets user's leaderboard history
     */
    public List<ReferralLeaderboard> getUserHistory(UUID userId, String programId) {
        return leaderboardRepository.findUserHistory(userId, programId);
    }

    /**
     * Updates user rankings (scheduled job)
     */
    @Transactional
    public void updateRankings(String programId, String periodType, LocalDate periodStart, LocalDate periodEnd) {
        log.info("Updating rankings: program={}, period={}", programId, periodType);

        List<ReferralLeaderboard> entries = leaderboardRepository.findLeaderboardByPeriod(
                        programId, periodType, periodStart, periodEnd,
                        org.springframework.data.domain.Pageable.unpaged())
                .getContent();

        entries.sort((a, b) -> Integer.compare(b.getTotalReferrals(), a.getTotalReferrals()));

        int rank = 1;
        for (ReferralLeaderboard entry : entries) {
            entry.updateRank(rank++);
            leaderboardRepository.save(entry);
        }

        log.info("Updated {} leaderboard entries", entries.size());
    }
}
