package com.waqiti.card.repository;

import com.waqiti.card.entity.CardDispute;
import com.waqiti.card.enums.DisputeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CardDisputeRepository - Spring Data JPA repository for CardDispute entity
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Repository
public interface CardDisputeRepository extends JpaRepository<CardDispute, UUID>, JpaSpecificationExecutor<CardDispute> {

    Optional<CardDispute> findByDisputeId(String disputeId);

    Optional<CardDispute> findByCaseNumber(String caseNumber);

    Optional<CardDispute> findByTransactionId(UUID transactionId);

    List<CardDispute> findByCardId(UUID cardId);

    List<CardDispute> findByUserId(UUID userId);

    List<CardDispute> findByDisputeStatus(DisputeStatus status);

    @Query("SELECT d FROM CardDispute d WHERE d.disputeStatus IN ('OPEN', 'INVESTIGATING', 'AWAITING_MERCHANT_RESPONSE', 'AWAITING_CARDHOLDER_RESPONSE') AND d.deletedAt IS NULL")
    List<CardDispute> findActiveDisputes();

    @Query("SELECT d FROM CardDispute d WHERE d.disputeStatus = 'AWAITING_MERCHANT_RESPONSE' AND d.merchantResponseDeadline < :currentDateTime AND d.deletedAt IS NULL")
    List<CardDispute> findOverdueMerchantResponses(@Param("currentDateTime") LocalDateTime currentDateTime);

    @Query("SELECT d FROM CardDispute d WHERE d.chargebackIssued = true AND d.deletedAt IS NULL")
    List<CardDispute> findChargebackDisputes();

    @Query("SELECT d FROM CardDispute d WHERE d.escalatedToArbitration = true AND d.deletedAt IS NULL")
    List<CardDispute> findArbitrationDisputes();

    @Query("SELECT d FROM CardDispute d WHERE d.isFraudRelated = true AND d.deletedAt IS NULL")
    List<CardDispute> findFraudRelatedDisputes();

    @Query("SELECT d FROM CardDispute d WHERE d.assignedTo = :assignee AND d.disputeStatus IN ('OPEN', 'INVESTIGATING') AND d.deletedAt IS NULL")
    List<CardDispute> findDisputesAssignedTo(@Param("assignee") String assignee);

    long countByUserId(UUID userId);

    long countByDisputeStatus(DisputeStatus status);
}
