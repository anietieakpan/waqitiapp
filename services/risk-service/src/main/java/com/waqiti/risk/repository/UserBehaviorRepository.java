package com.waqiti.risk.repository;

import com.waqiti.risk.model.UserBehaviorData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * User Behavior Repository
 *
 * MongoDB repository for managing user behavioral data
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Repository
public interface UserBehaviorRepository extends MongoRepository<UserBehaviorData, String> {

    /**
     * Find behavior data by user ID
     */
    List<UserBehaviorData> findByUserId(String userId);

    /**
     * Find most recent behavior data for user
     */
    @Query("{'userId': ?0}")
    Optional<UserBehaviorData> findLatestByUserId(String userId);

    /**
     * Find behavior data for user within period
     */
    List<UserBehaviorData> findByUserIdAndPeriodStartGreaterThanEqual(String userId, LocalDateTime since);

    /**
     * Find behavior data by aggregation period
     */
    List<UserBehaviorData> findByAggregationPeriod(String aggregationPeriod);

    /**
     * Find behavior data for user by period type
     */
    List<UserBehaviorData> findByUserIdAndAggregationPeriod(String userId, String aggregationPeriod);

    /**
     * Find users with unusual behavior
     */
    @Query("{'anomalyScore': {'$gte': ?0}}")
    List<UserBehaviorData> findUnusualBehavior(Double anomalyThreshold);

    /**
     * Find users with low consistency
     */
    @Query("{'consistencyScore': {'$lt': ?0}}")
    List<UserBehaviorData> findLowConsistencyBehavior(Double consistencyThreshold);

    /**
     * Find users with high deviation from baseline
     */
    @Query("{'deviationFromBaseline': {'$gte': ?0}}")
    List<UserBehaviorData> findHighDeviationBehavior(Double deviationThreshold);

    /**
     * Find new users (first transaction recent)
     */
    @Query("{'firstTransactionAt': {'$gte': ?0}}")
    List<UserBehaviorData> findNewUserBehavior(LocalDateTime since);

    /**
     * Find inactive users
     */
    @Query("{'lastTransactionAt': {'$lt': ?0}}")
    List<UserBehaviorData> findInactiveUserBehavior(LocalDateTime before);

    /**
     * Find users with high transaction frequency
     */
    @Query("{'maxTransactionsPerHour': {'$gte': ?0}}")
    List<UserBehaviorData> findHighFrequencyUsers(Integer threshold);

    /**
     * Find multi-device users
     */
    @Query("{'multipleDeviceUser': true}")
    List<UserBehaviorData> findMultiDeviceUsers();

    /**
     * Find frequent travelers
     */
    @Query("{'frequentTraveler': true}")
    List<UserBehaviorData> findFrequentTravelers();

    /**
     * Find users with failed transactions
     */
    @Query("{'failedTransactionAttempts': {'$gte': ?0}}")
    List<UserBehaviorData> findUsersWithFailedTransactions(Integer threshold);

    /**
     * Find users with chargebacks
     */
    @Query("{'chargebackCount': {'$gt': 0}}")
    List<UserBehaviorData> findUsersWithChargebacks();

    /**
     * Get user behavior for period
     */
    @Query("{'userId': ?0, 'periodStart': {'$gte': ?1}}")
    com.waqiti.risk.dto.UserBehavior getUserBehavior(String userId, LocalDateTime since);

    /**
     * Find behavior data by period range
     */
    @Query("{'periodStart': {'$gte': ?0, '$lte': ?1}}")
    List<UserBehaviorData> findByPeriodRange(LocalDateTime start, LocalDateTime end);

    /**
     * Find recently analyzed behavior
     */
    @Query("{'lastAnalyzedAt': {'$gte': ?0}}")
    List<UserBehaviorData> findRecentlyAnalyzed(LocalDateTime since);

    /**
     * Find stale behavior data (needs re-analysis)
     */
    @Query("{'lastAnalyzedAt': {'$lt': ?0}}")
    List<UserBehaviorData> findStaleBehaviorData(LocalDateTime before);

    /**
     * Count behavior records for user
     */
    long countByUserId(String userId);
}
