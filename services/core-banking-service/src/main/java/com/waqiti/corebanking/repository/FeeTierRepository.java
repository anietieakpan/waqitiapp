package com.waqiti.corebanking.repository;

import com.waqiti.corebanking.domain.FeeTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Fee Tier Repository
 * 
 * Repository interface for FeeTier entity operations
 */
@Repository
public interface FeeTierRepository extends JpaRepository<FeeTier, UUID> {

    /**
     * Find fee tiers by fee schedule ID ordered by tier order
     */
    List<FeeTier> findByFeeScheduleIdOrderByTierOrder(UUID feeScheduleId);

    /**
     * Find fee tiers by fee schedule ID and tier order
     */
    @Query("SELECT ft FROM FeeTier ft WHERE ft.feeScheduleId = :feeScheduleId " +
           "AND ft.tierOrder = :tierOrder")
    List<FeeTier> findByFeeScheduleIdAndTierOrder(@Param("feeScheduleId") UUID feeScheduleId, 
                                                 @Param("tierOrder") Integer tierOrder);

    /**
     * Count fee tiers for a fee schedule
     */
    long countByFeeScheduleId(UUID feeScheduleId);

    /**
     * Find the highest tier order for a fee schedule
     */
    @Query("SELECT MAX(ft.tierOrder) FROM FeeTier ft WHERE ft.feeScheduleId = :feeScheduleId")
    Integer findMaxTierOrderByFeeScheduleId(@Param("feeScheduleId") UUID feeScheduleId);

    /**
     * Delete all fee tiers for a fee schedule
     */
    void deleteByFeeScheduleId(UUID feeScheduleId);

    /**
     * Find fee tiers with free quantities
     */
    @Query("SELECT ft FROM FeeTier ft WHERE ft.freeQuantity IS NOT NULL AND ft.freeQuantity > 0")
    List<FeeTier> findWithFreeQuantity();
}