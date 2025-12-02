package com.waqiti.rewards.repository;

import com.waqiti.rewards.domain.ReferralTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReferralTierRepository extends JpaRepository<ReferralTier, UUID> {

    Optional<ReferralTier> findByTierId(String tierId);

    List<ReferralTier> findByProgram_ProgramId(String programId);

    List<ReferralTier> findByProgram_ProgramIdAndIsActiveTrue(String programId);

    @Query("SELECT t FROM ReferralTier t WHERE t.program.programId = :programId " +
           "ORDER BY t.tierLevel ASC")
    List<ReferralTier> findByProgramOrderedByLevel(@Param("programId") String programId);

    @Query("SELECT t FROM ReferralTier t WHERE t.program.programId = :programId " +
           "AND t.isActive = true " +
           "AND t.minReferrals <= :referralCount " +
           "AND (t.maxReferrals IS NULL OR t.maxReferrals >= :referralCount) " +
           "ORDER BY t.tierLevel DESC")
    Optional<ReferralTier> findQualifyingTier(
        @Param("programId") String programId,
        @Param("referralCount") int referralCount
    );

    @Query("SELECT t FROM ReferralTier t WHERE t.program.programId = :programId " +
           "AND t.minReferrals > :currentReferrals " +
           "ORDER BY t.minReferrals ASC")
    Optional<ReferralTier> findNextTier(
        @Param("programId") String programId,
        @Param("currentReferrals") int currentReferrals
    );

    boolean existsByProgram_ProgramIdAndTierLevel(String programId, int tierLevel);
}
