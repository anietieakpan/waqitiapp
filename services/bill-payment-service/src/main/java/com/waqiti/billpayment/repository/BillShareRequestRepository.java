package com.waqiti.billpayment.repository;

import com.waqiti.billpayment.entity.BillShareRequest;
import com.waqiti.billpayment.entity.ShareStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for BillShareRequest entity operations
 */
@Repository
public interface BillShareRequestRepository extends JpaRepository<BillShareRequest, UUID> {

    List<BillShareRequest> findByBillId(UUID billId);

    List<BillShareRequest> findByCreatorUserId(String creatorUserId);

    List<BillShareRequest> findByParticipantUserId(String participantUserId);

    List<BillShareRequest> findByParticipantUserIdAndStatus(String participantUserId, ShareStatus status);

    @Query("SELECT bsr FROM BillShareRequest bsr WHERE bsr.status = 'ACCEPTED' " +
           "AND bsr.paymentId IS NULL")
    List<BillShareRequest> findUnpaidAcceptedShares();

    @Query("SELECT bsr FROM BillShareRequest bsr WHERE bsr.status = 'PENDING' " +
           "AND bsr.expiresAt < :now")
    List<BillShareRequest> findExpiredPendingShares(@Param("now") LocalDateTime now);

    @Query("SELECT SUM(bsr.shareAmount) FROM BillShareRequest bsr WHERE bsr.billId = :billId " +
           "AND bsr.status IN ('ACCEPTED', 'PAID')")
    java.math.BigDecimal getTotalSharedAmount(@Param("billId") UUID billId);

    long countByBillIdAndStatus(UUID billId, ShareStatus status);

    boolean existsByBillIdAndParticipantUserIdAndStatusIn(UUID billId, String participantUserId, List<ShareStatus> statuses);
}
