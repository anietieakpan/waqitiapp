package com.waqiti.risk.repository;

import com.waqiti.risk.model.RiskThreshold;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Risk Threshold Repository
 *
 * MongoDB repository for managing risk thresholds
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Repository
public interface RiskThresholdRepository extends MongoRepository<RiskThreshold, String> {

    /**
     * Find threshold by name
     */
    Optional<RiskThreshold> findByThresholdName(String thresholdName);

    /**
     * Find thresholds by type
     */
    List<RiskThreshold> findByThresholdType(String thresholdType);

    /**
     * Find enabled thresholds by type
     */
    List<RiskThreshold> findByThresholdTypeAndEnabled(String thresholdType, Boolean enabled);

    /**
     * Find thresholds applicable to entity type
     */
    List<RiskThreshold> findByApplicableTo(String applicableTo);

    /**
     * Find thresholds for specific entity
     */
    List<RiskThreshold> findByEntityId(String entityId);

    /**
     * Find enabled thresholds for entity
     */
    List<RiskThreshold> findByEntityIdAndEnabled(String entityId, Boolean enabled);

    /**
     * Find global thresholds (applicable to all)
     */
    @Query("{'applicableTo': 'GLOBAL', 'enabled': true}")
    List<RiskThreshold> findGlobalThresholds();

    /**
     * Find user-specific thresholds
     */
    @Query("{'applicableTo': 'USER', 'entityId': ?0, 'enabled': true}")
    List<RiskThreshold> findUserThresholds(String userId);

    /**
     * Find merchant-specific thresholds
     */
    @Query("{'applicableTo': 'MERCHANT', 'entityId': ?0, 'enabled': true}")
    List<RiskThreshold> findMerchantThresholds(String merchantId);

    /**
     * Find effective thresholds (currently active)
     */
    @Query("{'enabled': true, " +
           "'effectiveFrom': {'$lte': ?0}, " +
           "'$or': [{'effectiveUntil': {'$gte': ?0}}, {'effectiveUntil': null}]}")
    List<RiskThreshold> findEffectiveThresholds(LocalDateTime now);

    /**
     * Find thresholds by category
     */
    List<RiskThreshold> findByCategory(String category);

    /**
     * Find thresholds by enforcement action
     */
    List<RiskThreshold> findByEnforcementAction(String enforcementAction);

    /**
     * Find thresholds with violations
     */
    @Query("{'violationCount': {'$gt': 0}}")
    List<RiskThreshold> findThresholdsWithViolations();

    /**
     * Find frequently violated thresholds
     */
    @Query("{'violationCount': {'$gte': ?0}}")
    List<RiskThreshold> findFrequentlyViolatedThresholds(Long minViolations);

    /**
     * Find thresholds by priority (descending)
     */
    List<RiskThreshold> findByEnabledOrderByPriorityDesc(Boolean enabled);

    /**
     * Find recently violated thresholds
     */
    @Query("{'lastViolationAt': {'$gte': ?0}}")
    List<RiskThreshold> findRecentlyViolatedThresholds(LocalDateTime since);

    /**
     * Count enabled thresholds
     */
    long countByEnabled(Boolean enabled);

    /**
     * Check if threshold name exists
     */
    boolean existsByThresholdName(String thresholdName);

    /**
     * Find thresholds updated since
     */
    List<RiskThreshold> findByUpdatedAtGreaterThan(LocalDateTime since);
}
