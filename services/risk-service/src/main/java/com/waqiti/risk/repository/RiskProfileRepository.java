package com.waqiti.risk.repository;

import com.waqiti.risk.domain.RiskLevel;
import com.waqiti.risk.model.RiskProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Risk Profile Repository
 *
 * MongoDB repository for managing risk profiles
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Repository
public interface RiskProfileRepository extends MongoRepository<RiskProfile, String> {

    /**
     * Find risk profile by user ID
     */
    Optional<RiskProfile> findByUserId(String userId);

    /**
     * Find risk profile by user ID and profile type
     */
    Optional<RiskProfile> findByUserIdAndProfileType(String userId, String profileType);

    /**
     * Find merchant profile (convenience method)
     */
    @Query("{'userId': ?0, 'profileType': 'MERCHANT'}")
    Optional<RiskProfile> findMerchantProfile(String merchantId);

    /**
     * Find all profiles by risk level
     */
    List<RiskProfile> findByCurrentRiskLevel(RiskLevel riskLevel);

    /**
     * Find high-risk profiles
     */
    @Query("{'currentRiskLevel': 'HIGH', 'updatedAt': {'$gte': ?0}}")
    List<RiskProfile> findHighRiskProfiles(LocalDateTime since);

    /**
     * Find profiles with score above threshold
     */
    @Query("{'currentRiskScore': {'$gte': ?0}}")
    List<RiskProfile> findByRiskScoreGreaterThanEqual(Double threshold);

    /**
     * Find profiles by verification level
     */
    List<RiskProfile> findByVerificationLevel(String verificationLevel);

    /**
     * Find profiles with active risk flags
     */
    @Query("{'activeRiskFlags': {'$exists': true, '$ne': []}}")
    List<RiskProfile> findProfilesWithActiveRiskFlags();

    /**
     * Find profiles by specific risk flag
     */
    @Query("{'activeRiskFlags': {'$in': [?0]}}")
    List<RiskProfile> findByRiskFlag(String flag);

    /**
     * Find new accounts (created within days)
     */
    @Query("{'accountCreatedAt': {'$gte': ?0}}")
    List<RiskProfile> findNewAccounts(LocalDateTime since);

    /**
     * Find profiles by merchant category
     */
    List<RiskProfile> findByMerchantCategory(String category);

    /**
     * Find profiles with high chargeback rate
     */
    @Query("{'chargebackRate': {'$gte': ?0}}")
    List<RiskProfile> findByHighChargebackRate(Double threshold);

    /**
     * Find profiles not assessed recently
     */
    @Query("{'lastAssessmentAt': {'$lt': ?0}}")
    List<RiskProfile> findStaleProfiles(LocalDateTime before);

    /**
     * Count profiles by risk level
     */
    long countByCurrentRiskLevel(RiskLevel riskLevel);

    /**
     * Find profiles updated between dates
     */
    List<RiskProfile> findByUpdatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Check if user profile exists
     */
    boolean existsByUserId(String userId);
}
