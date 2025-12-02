package com.waqiti.billpayment.repository;

import com.waqiti.billpayment.entity.AutoPayConfig;
import com.waqiti.billpayment.entity.AutoPayStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for AutoPayConfig entity operations
 */
@Repository
public interface AutoPayConfigRepository extends JpaRepository<AutoPayConfig, UUID> {

    List<AutoPayConfig> findByUserId(String userId);

    List<AutoPayConfig> findByUserIdAndStatus(String userId, AutoPayStatus status);

    Optional<AutoPayConfig> findByUserIdAndBillId(String userId, UUID billId);

    List<AutoPayConfig> findByBillerId(UUID billerId);

    @Query("SELECT apc FROM AutoPayConfig apc WHERE apc.status = 'ACTIVE' " +
           "AND apc.nextPaymentDate <= :now AND apc.deletedAt IS NULL")
    List<AutoPayConfig> findAutoPaymentsDueNow(@Param("now") LocalDateTime now);

    @Query("SELECT apc FROM AutoPayConfig apc WHERE apc.status = 'SUSPENDED' " +
           "AND apc.failureCount >= apc.maxFailureCount")
    List<AutoPayConfig> findSuspendedConfigs();

    long countByUserIdAndStatus(String userId, AutoPayStatus status);

    boolean existsByUserIdAndBillId(String userId, UUID billId);

    /**
     * Find auto-pay configurations by user and enabled status
     */
    List<AutoPayConfig> findByUserIdAndIsEnabled(String userId, boolean isEnabled);

    @Query("UPDATE AutoPayConfig apc SET apc.deletedAt = :now, apc.deletedBy = :deletedBy WHERE apc.id = :id")
    void softDelete(@Param("id") UUID id, @Param("deletedBy") String deletedBy, @Param("now") LocalDateTime now);
}
