package com.waqiti.common.security.awareness.repository;

import com.waqiti.common.security.awareness.domain.PhishingSimulationCampaign;

import com.waqiti.common.security.awareness.model.*;
import com.waqiti.common.security.awareness.dto.*;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for PhishingSimulationCampaign entities
 *
 * @author Waqiti Platform Team
 */
@Repository
public interface PhishingSimulationCampaignRepository extends JpaRepository<PhishingSimulationCampaign, UUID> {

    /**
     * Find campaigns by status
     */
    List<PhishingSimulationCampaign> findByStatus(PhishingSimulationCampaign.CampaignStatus status);

    /**
     * Find scheduled campaigns due to start
     */
    @Query("SELECT c FROM PhishingSimulationCampaign c WHERE c.status = 'SCHEDULED' " +
            "AND c.scheduledDate <= :now")
    List<PhishingSimulationCampaign> findScheduledCampaignsDueToStart(LocalDateTime now);

    /**
     * Find active campaigns
     */
    List<PhishingSimulationCampaign> findByStatusIn(List<PhishingSimulationCampaign.CampaignStatus> statuses);

    /**
     * Find campaigns by status and scheduled start before a given date
     */
    List<PhishingSimulationCampaign> findByStatusAndScheduledStartBefore(
            PhishingSimulationCampaign.CampaignStatus status,
            LocalDateTime date);

    /**
     * Find campaigns by status and scheduled end before a given date
     */
    List<PhishingSimulationCampaign> findByStatusAndScheduledEndBefore(
            PhishingSimulationCampaign.CampaignStatus status,
            LocalDateTime date);
}