package com.waqiti.wallet.repository;

import com.waqiti.wallet.domain.RefundRecord;
import com.waqiti.wallet.domain.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundRecordRepository extends JpaRepository<RefundRecord, UUID> {

    /**
     * Find refund by correlation ID
     */
    Optional<RefundRecord> findByCorrelationId(String correlationId);

    /**
     * Find refunds by wallet ID
     */
    List<RefundRecord> findByWalletId(UUID walletId);

    /**
     * Find refunds by user ID
     */
    List<RefundRecord> findByUserId(UUID userId);

    /**
     * Find refunds by status
     */
    List<RefundRecord> findByStatus(RefundStatus status);

    /**
     * Find pending refunds older than a specific time
     */
    List<RefundRecord> findByStatusAndCreatedAtBefore(RefundStatus status, LocalDateTime beforeTime);

    /**
     * Count refunds by wallet and status
     */
    @Query("SELECT COUNT(r) FROM RefundRecord r WHERE r.walletId = :walletId AND r.status = :status")
    Long countByWalletIdAndStatus(@Param("walletId") UUID walletId, @Param("status") RefundStatus status);
}
