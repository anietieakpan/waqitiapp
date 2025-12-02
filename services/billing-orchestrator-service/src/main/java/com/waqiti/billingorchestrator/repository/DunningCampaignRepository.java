package com.waqiti.billingorchestrator.repository;

import com.waqiti.billingorchestrator.entity.DunningCampaign;
import com.waqiti.billingorchestrator.entity.DunningCampaign.DunningStatus;
import com.waqiti.billingorchestrator.entity.DunningCampaign.DunningStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for DunningCampaign entities
 *
 * @author Waqiti Billing Team
 * @since 1.0
 */
@Repository
public interface DunningCampaignRepository extends JpaRepository<DunningCampaign, UUID> {

    /**
     * Find active campaigns for account
     */
    List<DunningCampaign> findByAccountIdAndStatus(UUID accountId, DunningStatus status);

    /**
     * Find campaigns due for next action
     */
    @Query("SELECT d FROM DunningCampaign d WHERE d.status = 'ACTIVE' " +
           "AND d.nextActionDate <= :now ORDER BY d.nextActionDate ASC")
    List<DunningCampaign> findDueForAction(@Param("now") LocalDateTime now);

    /**
     * Find campaigns by invoice
     */
    Optional<DunningCampaign> findByInvoiceIdAndStatus(UUID invoiceId, DunningStatus status);

    /**
     * Count active campaigns by account
     */
    long countByAccountIdAndStatus(UUID accountId, DunningStatus status);

    /**
     * Find campaigns at specific stage
     */
    List<DunningCampaign> findByCurrentStageAndStatus(DunningStage stage, DunningStatus status);
}
