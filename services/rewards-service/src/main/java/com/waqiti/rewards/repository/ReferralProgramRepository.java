package com.waqiti.rewards.repository;

import com.waqiti.rewards.domain.ReferralProgram;
import com.waqiti.rewards.enums.ProgramType;
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

/**
 * Repository for ReferralProgram Entity
 *
 * Provides data access methods for referral program management
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Repository
public interface ReferralProgramRepository extends JpaRepository<ReferralProgram, UUID> {

    /**
     * Finds a program by its unique program ID
     */
    Optional<ReferralProgram> findByProgramId(String programId);

    /**
     * Finds all active programs
     */
    List<ReferralProgram> findByIsActiveTrue();

    /**
     * Finds all active and public programs
     */
    List<ReferralProgram> findByIsActiveTrueAndIsPublicTrue();

    /**
     * Finds programs by type
     */
    List<ReferralProgram> findByProgramType(ProgramType programType);

    /**
     * Finds active programs by type
     */
    List<ReferralProgram> findByProgramTypeAndIsActiveTrue(ProgramType programType);

    /**
     * Finds programs currently active (within date range)
     */
    @Query("SELECT p FROM ReferralProgram p WHERE p.isActive = true " +
           "AND p.startDate <= :today " +
           "AND (p.endDate IS NULL OR p.endDate >= :today)")
    List<ReferralProgram> findCurrentlyActivePrograms(@Param("today") LocalDate today);

    /**
     * Finds programs by creator
     */
    Page<ReferralProgram> findByCreatedBy(String createdBy, Pageable pageable);

    /**
     * Checks if a program exists by program ID
     */
    boolean existsByProgramId(String programId);

    /**
     * Finds programs that have exceeded their budget
     */
    @Query("SELECT p FROM ReferralProgram p WHERE p.maxProgramBudget IS NOT NULL " +
           "AND p.totalRewardsIssued >= p.maxProgramBudget")
    List<ReferralProgram> findProgramsExceedingBudget();

    /**
     * Finds programs by name containing (case-insensitive search)
     */
    Page<ReferralProgram> findByProgramNameContainingIgnoreCase(String name, Pageable pageable);

    /**
     * Finds programs ending within the next N days
     */
    @Query("SELECT p FROM ReferralProgram p WHERE p.isActive = true " +
           "AND p.endDate IS NOT NULL " +
           "AND p.endDate BETWEEN :startDate AND :endDate")
    List<ReferralProgram> findProgramsEndingSoon(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Finds programs with high conversion rates (above threshold)
     */
    @Query("SELECT p FROM ReferralProgram p WHERE p.conversionRate >= :minRate " +
           "ORDER BY p.conversionRate DESC")
    List<ReferralProgram> findHighPerformingPrograms(@Param("minRate") java.math.BigDecimal minRate);

    /**
     * Gets total rewards issued across all programs
     */
    @Query("SELECT COALESCE(SUM(p.totalRewardsIssued), 0) FROM ReferralProgram p WHERE p.isActive = true")
    java.math.BigDecimal getTotalRewardsIssuedAllPrograms();

    /**
     * Gets total referrals across all active programs
     */
    @Query("SELECT COALESCE(SUM(p.totalReferrals), 0) FROM ReferralProgram p WHERE p.isActive = true")
    Long getTotalReferralsAllPrograms();

    /**
     * Finds programs by target audience
     */
    @Query("SELECT DISTINCT p FROM ReferralProgram p JOIN p.targetAudience ta WHERE ta IN :audiences")
    List<ReferralProgram> findByTargetAudienceIn(@Param("audiences") List<String> audiences);

    /**
     * Finds programs that allow more referrals for a user
     */
    @Query("SELECT p FROM ReferralProgram p WHERE p.isActive = true " +
           "AND (p.maxReferralsPerUser IS NULL OR p.maxReferralsPerUser > :currentCount)")
    List<ReferralProgram> findProgramsAllowingMoreReferrals(@Param("currentCount") int currentCount);
}
