package com.waqiti.frauddetection.repository;

import com.waqiti.frauddetection.entity.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Customer Profile Repository
 *
 * Data access layer for customer profiles with optimized queries.
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0 - Production Implementation
 */
@Repository
public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, UUID> {

    /**
     * Find customer profile by customer ID
     */
    Optional<CustomerProfile> findByCustomerId(UUID customerId);

    /**
     * Find high-risk customers
     */
    @Query("SELECT cp FROM CustomerProfile cp WHERE cp.currentRiskLevel = 'HIGH' AND cp.lastTransactionDate >= :since")
    List<CustomerProfile> findHighRiskCustomersSince(@Param("since") LocalDateTime since);

    /**
     * Find customers with recent fraud activity
     */
    @Query("SELECT cp FROM CustomerProfile cp WHERE cp.lastFraudDate >= :since ORDER BY cp.lastFraudDate DESC")
    List<CustomerProfile> findCustomersWithRecentFraud(@Param("since") LocalDateTime since);

    /**
     * Count customers by risk level
     */
    @Query("SELECT cp.currentRiskLevel, COUNT(cp) FROM CustomerProfile cp GROUP BY cp.currentRiskLevel")
    List<Object[]> countCustomersByRiskLevel();

    /**
     * Find customers requiring Enhanced Due Diligence
     */
    @Query("SELECT cp FROM CustomerProfile cp WHERE cp.currentRiskLevel = 'HIGH' OR cp.fraudCount >= 3")
    List<CustomerProfile> findCustomersRequiringEDD();

    /**
     * Check if customer exists
     */
    boolean existsByCustomerId(UUID customerId);
}
