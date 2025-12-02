package com.waqiti.rewards.repository;

import com.waqiti.rewards.domain.ReferralMilestoneAchievement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReferralMilestoneAchievementRepository extends JpaRepository<ReferralMilestoneAchievement, UUID> {

    Optional<ReferralMilestoneAchievement> findByAchievementId(String achievementId);

    List<ReferralMilestoneAchievement> findByUserId(UUID userId);

    List<ReferralMilestoneAchievement> findByMilestone_MilestoneId(String milestoneId);

    List<ReferralMilestoneAchievement> findByUserIdAndMilestone_MilestoneId(UUID userId, String milestoneId);

    List<ReferralMilestoneAchievement> findByRewardIssuedFalse();

    List<ReferralMilestoneAchievement> findByUserNotifiedFalse();

    @Query("SELECT COUNT(a) FROM ReferralMilestoneAchievement a WHERE a.userId = :userId " +
           "AND a.milestone.program.programId = :programId")
    Long countAchievementsByUserAndProgram(@Param("userId") UUID userId, @Param("programId") String programId);

    boolean existsByUserIdAndMilestone_MilestoneId(UUID userId, String milestoneId);

    @Query("SELECT a FROM ReferralMilestoneAchievement a WHERE a.userId = :userId " +
           "AND a.milestone.milestoneId = :milestoneId " +
           "ORDER BY a.achievedAt DESC")
    List<ReferralMilestoneAchievement> findUserMilestoneHistory(
        @Param("userId") UUID userId,
        @Param("milestoneId") String milestoneId
    );
}
