package com.waqiti.rewards.repository;

import com.waqiti.rewards.domain.ReferralCampaign;
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
public interface ReferralCampaignRepository extends JpaRepository<ReferralCampaign, UUID> {

    Optional<ReferralCampaign> findByCampaignId(String campaignId);

    List<ReferralCampaign> findByProgram_ProgramId(String programId);

    List<ReferralCampaign> findByStatus(String status);

    List<ReferralCampaign> findByCampaignType(String campaignType);

    @Query("SELECT c FROM ReferralCampaign c WHERE c.status = 'ACTIVE' " +
           "AND c.startDate <= :today " +
           "AND (c.endDate IS NULL OR c.endDate >= :today)")
    List<ReferralCampaign> findActiveCampaigns(@Param("today") LocalDate today);

    @Query("SELECT c FROM ReferralCampaign c WHERE c.status = 'ACTIVE' " +
           "AND c.endDate BETWEEN :startDate AND :endDate")
    List<ReferralCampaign> findCampaignsEndingSoon(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    Page<ReferralCampaign> findByCreatedBy(String createdBy, Pageable pageable);

    @Query("SELECT c FROM ReferralCampaign c WHERE c.program.programId = :programId " +
           "ORDER BY c.totalReferrals DESC")
    Page<ReferralCampaign> findTopPerformingCampaigns(
        @Param("programId") String programId,
        Pageable pageable
    );

    boolean existsByCampaignId(String campaignId);
}
