package com.waqiti.rewards.repository;

import com.waqiti.rewards.domain.ReferralMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReferralMilestoneRepository extends JpaRepository<ReferralMilestone, UUID> {

    Optional<ReferralMilestone> findByMilestoneId(String milestoneId);

    List<ReferralMilestone> findByProgram_ProgramId(String programId);

    List<ReferralMilestone> findByProgram_ProgramIdAndIsActiveTrue(String programId);

    @Query("SELECT m FROM ReferralMilestone m WHERE m.program.programId = :programId " +
           "AND m.isActive = true ORDER BY m.displayOrder ASC, m.requiredReferrals ASC")
    List<ReferralMilestone> findActiveMilestonesOrdered(@Param("programId") String programId);

    List<ReferralMilestone> findByMilestoneType(String milestoneType);

    @Query("SELECT m FROM ReferralMilestone m WHERE m.program.programId = :programId " +
           "AND m.isActive = true " +
           "AND (m.requiredReferrals IS NULL OR m.requiredReferrals <= :referralCount) " +
           "AND (m.requiredConversions IS NULL OR m.requiredConversions <= :conversionCount)")
    List<ReferralMilestone> findEligibleMilestones(
        @Param("programId") String programId,
        @Param("referralCount") int referralCount,
        @Param("conversionCount") int conversionCount
    );

    boolean existsByMilestoneId(String milestoneId);
}
