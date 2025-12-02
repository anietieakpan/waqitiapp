package com.waqiti.rewards.repository;

import com.waqiti.rewards.domain.ReferralReward;
import com.waqiti.rewards.enums.RewardStatus;
import com.waqiti.rewards.enums.RewardType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ReferralReward Entity
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Repository
public interface ReferralRewardRepository extends JpaRepository<ReferralReward, UUID> {

    /**
     * Finds a reward by reward ID
     */
    Optional<ReferralReward> findByRewardId(String rewardId);

    /**
     * Finds all rewards for a referral
     */
    List<ReferralReward> findByReferralId(String referralId);

    /**
     * Finds all rewards for a user (as recipient)
     */
    Page<ReferralReward> findByRecipientUserId(UUID userId, Pageable pageable);

    /**
     * Finds rewards for a user by status
     */
    List<ReferralReward> findByRecipientUserIdAndStatus(UUID userId, RewardStatus status);

    /**
     * Finds rewards for a user by recipient type
     */
    List<ReferralReward> findByRecipientUserIdAndRecipientType(UUID userId, String recipientType);

    /**
     * Finds all rewards for a program
     */
    Page<ReferralReward> findByProgramId(String programId, Pageable pageable);

    /**
     * Finds rewards by status
     */
    List<ReferralReward> findByStatus(RewardStatus status);

    /**
     * Finds pending rewards
     */
    List<ReferralReward> findByStatusOrderByEarnedAtAsc(RewardStatus status);

    /**
     * Finds rewards expiring soon
     */
    @Query("SELECT r FROM ReferralReward r WHERE r.status = :status " +
           "AND r.expiryDate BETWEEN :startDate AND :endDate")
    List<ReferralReward> findRewardsExpiringSoon(
        @Param("status") RewardStatus status,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Finds expired rewards that haven't been marked as expired
     */
    @Query("SELECT r FROM ReferralReward r WHERE r.status != 'EXPIRED' " +
           "AND r.expiryDate < :today")
    List<ReferralReward> findExpiredRewards(@Param("today") LocalDate today);

    /**
     * Finds rewards requiring approval
     */
    List<ReferralReward> findByRequiresApprovalTrueAndStatus(RewardStatus status);

    /**
     * Finds rewards by type
     */
    List<ReferralReward> findByRewardType(RewardType rewardType);

    /**
     * Gets total rewards issued for a user
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN r.rewardType = 'POINTS' THEN r.pointsAmount ELSE 0 END), 0) " +
           "FROM ReferralReward r WHERE r.recipientUserId = :userId AND r.status IN :statuses")
    Long getTotalPointsEarned(@Param("userId") UUID userId, @Param("statuses") List<RewardStatus> statuses);

    /**
     * Gets total cashback earned for a user
     */
    @Query("SELECT COALESCE(SUM(r.cashbackAmount), 0) FROM ReferralReward r " +
           "WHERE r.recipientUserId = :userId " +
           "AND r.rewardType IN ('CASHBACK', 'BONUS') " +
           "AND r.status IN :statuses")
    BigDecimal getTotalCashbackEarned(@Param("userId") UUID userId, @Param("statuses") List<RewardStatus> statuses);

    /**
     * Gets total rewards issued for a program
     */
    @Query("SELECT COALESCE(SUM(COALESCE(r.cashbackAmount, 0)), 0) FROM ReferralReward r " +
           "WHERE r.programId = :programId")
    BigDecimal getTotalRewardsIssuedForProgram(@Param("programId") String programId);

    /**
     * Counts rewards by status for a program
     */
    @Query("SELECT COUNT(r) FROM ReferralReward r WHERE r.programId = :programId AND r.status = :status")
    Long countByProgramIdAndStatus(@Param("programId") String programId, @Param("status") RewardStatus status);

    /**
     * Finds rewards issued within a date range
     */
    @Query("SELECT r FROM ReferralReward r WHERE r.issuedAt BETWEEN :startDate AND :endDate")
    List<ReferralReward> findRewardsIssuedBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Gets average reward amount for a program
     */
    @Query("SELECT AVG(COALESCE(r.cashbackAmount, 0)) FROM ReferralReward r " +
           "WHERE r.programId = :programId AND r.status = 'ISSUED'")
    BigDecimal getAverageRewardAmount(@Param("programId") String programId);

    /**
     * Finds top earners (users with highest rewards)
     */
    @Query("SELECT r.recipientUserId, SUM(COALESCE(r.cashbackAmount, 0)) as totalEarned " +
           "FROM ReferralReward r " +
           "WHERE r.status IN :statuses " +
           "GROUP BY r.recipientUserId " +
           "ORDER BY totalEarned DESC")
    Page<Object[]> findTopEarners(@Param("statuses") List<RewardStatus> statuses, Pageable pageable);

    /**
     * Checks if user has received reward for a specific referral
     */
    boolean existsByReferralIdAndRecipientUserId(String referralId, UUID userId);

    /**
     * Counts total rewards for a user
     */
    Long countByRecipientUserId(UUID userId);

    /**
     * Finds unredeemed rewards for a user
     */
    @Query("SELECT r FROM ReferralReward r WHERE r.recipientUserId = :userId " +
           "AND r.status = 'ISSUED' AND (r.expiryDate IS NULL OR r.expiryDate >= :today)")
    List<ReferralReward> findUnredeemedRewards(@Param("userId") UUID userId, @Param("today") LocalDate today);

    /**
     * Gets redemption rate for a program
     */
    @Query("SELECT CAST(COUNT(CASE WHEN r.status = 'REDEEMED' THEN 1 END) AS double) / " +
           "CAST(COUNT(CASE WHEN r.status = 'ISSUED' THEN 1 END) AS double) " +
           "FROM ReferralReward r WHERE r.programId = :programId")
    Double getRedemptionRate(@Param("programId") String programId);
}
