package com.waqiti.rewards.repository;

import com.waqiti.rewards.domain.LoyaltyAccount;
import com.waqiti.rewards.domain.RewardTier;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoyaltyAccountRepository extends MongoRepository<LoyaltyAccount, String> {
    
    Optional<LoyaltyAccount> findByUserId(String userId);
    
    List<LoyaltyAccount> findByCurrentTier(RewardTier tier);
    
    @Query("{'currentBalance': {'$gte': ?0}}")
    List<LoyaltyAccount> findByMinimumBalance(BigDecimal minimumBalance);
    
    @Query("{'tierExpiryDate': {'$lte': ?0}}")
    List<LoyaltyAccount> findExpiringTiers(LocalDateTime date);
    
    @Query("{'lastActivityAt': {'$lte': ?0}}")
    List<LoyaltyAccount> findInactiveAccounts(LocalDateTime cutoffDate);
    
    long countByCurrentTier(RewardTier tier);
    
    @Query("{'yearlySpend': {'$gte': ?0}, 'currentTier': ?1}")
    List<LoyaltyAccount> findEligibleForUpgrade(BigDecimal spendThreshold, RewardTier currentTier);
    
    @Query("{'pointsExpiringNextMonth': {'$gt': 0}}")
    List<LoyaltyAccount> findAccountsWithExpiringPoints();
}