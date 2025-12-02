package com.waqiti.frauddetection.repository;

import com.waqiti.frauddetection.entity.UserBehaviorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UserBehaviorProfile entities
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-02
 */
@Repository
public interface UserBehaviorProfileRepository extends JpaRepository<UserBehaviorProfile, UUID> {

    /**
     * Find profile by user ID
     */
    Optional<UserBehaviorProfile> findByUserId(UUID userId);

    /**
     * Check if profile exists for user
     */
    boolean existsByUserId(UUID userId);

    /**
     * Get high-trust users (trust score >= 80)
     */
    @Query("SELECT p FROM UserBehaviorProfile p WHERE " +
           "p.successfulTransactionCount > 100 AND " +
           "p.fraudIncidentCount = 0 AND " +
           "p.kycVerified = true")
    java.util.List<UserBehaviorProfile> findHighTrustUsers();
}
