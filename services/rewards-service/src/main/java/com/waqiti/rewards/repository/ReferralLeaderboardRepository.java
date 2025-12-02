package com.waqiti.rewards.repository;

import com.waqiti.rewards.domain.ReferralLeaderboard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReferralLeaderboardRepository extends JpaRepository<ReferralLeaderboard, UUID> {

    Optional<ReferralLeaderboard> findByUserIdAndProgramIdAndPeriodTypeAndPeriodStartAndPeriodEnd(
        UUID userId, String programId, String periodType, LocalDate periodStart, LocalDate periodEnd
    );

    List<ReferralLeaderboard> findByUserId(UUID userId);

    List<ReferralLeaderboard> findByProgramId(String programId);

    @Query("SELECT l FROM ReferralLeaderboard l WHERE l.programId = :programId " +
           "AND l.periodType = :periodType " +
           "AND l.periodStart = :periodStart " +
           "AND l.periodEnd = :periodEnd " +
           "ORDER BY l.rank ASC")
    Page<ReferralLeaderboard> findLeaderboardByPeriod(
        @Param("programId") String programId,
        @Param("periodType") String periodType,
        @Param("periodStart") LocalDate periodStart,
        @Param("periodEnd") LocalDate periodEnd,
        Pageable pageable
    );

    @Query("SELECT l FROM ReferralLeaderboard l WHERE l.programId = :programId " +
           "AND l.periodType = :periodType " +
           "AND l.periodEnd = (SELECT MAX(l2.periodEnd) FROM ReferralLeaderboard l2 " +
           "WHERE l2.programId = :programId AND l2.periodType = :periodType) " +
           "ORDER BY l.rank ASC")
    Page<ReferralLeaderboard> findLatestLeaderboard(
        @Param("programId") String programId,
        @Param("periodType") String periodType,
        Pageable pageable
    );

    @Query("SELECT l FROM ReferralLeaderboard l WHERE l.programId = :programId " +
           "AND l.periodType = :periodType " +
           "ORDER BY l.totalReferrals DESC")
    Page<ReferralLeaderboard> findTopReferrers(
        @Param("programId") String programId,
        @Param("periodType") String periodType,
        Pageable pageable
    );

    @Query("SELECT l FROM ReferralLeaderboard l WHERE l.userId = :userId " +
           "AND l.programId = :programId " +
           "ORDER BY l.periodEnd DESC")
    List<ReferralLeaderboard> findUserHistory(
        @Param("userId") UUID userId,
        @Param("programId") String programId
    );
}
