package com.waqiti.risk.repository;

import com.waqiti.risk.domain.RiskAssessment;
import com.waqiti.risk.domain.RiskLevel;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RiskAssessmentRepository extends MongoRepository<RiskAssessment, String> {
    
    boolean existsByTransactionIdAndEventId(String transactionId, String eventId);
    
    Optional<RiskAssessment> findByTransactionId(String transactionId);
    
    List<RiskAssessment> findByUserIdAndAssessmentStartedAtBetween(String userId, LocalDateTime start, LocalDateTime end);
    
    @Query("{'riskLevel': {'$in': ['HIGH', 'CRITICAL']}, 'assessmentStartedAt': {'$gte': ?0}}")
    List<RiskAssessment> findHighRiskAssessmentsSince(LocalDateTime since);
    
    List<RiskAssessment> findByRiskLevelAndAssessmentStartedAtBetween(RiskLevel riskLevel, LocalDateTime start, LocalDateTime end);
    
    @Query("{'riskScore': {'$gte': ?0}}")
    List<RiskAssessment> findByRiskScoreGreaterThan(Double threshold);
    
    @Query("{'userId': ?0, 'assessmentStartedAt': {'$gte': ?1}}")
    List<RiskAssessment> findRecentAssessmentsForUser(String userId, LocalDateTime since);
    
    @Query("{'merchantId': ?0, 'riskLevel': 'HIGH', 'assessmentStartedAt': {'$gte': ?1}}")
    List<RiskAssessment> findHighRiskForMerchant(String merchantId, LocalDateTime since);
    
    long countByRiskLevelAndAssessmentStartedAtBetween(RiskLevel riskLevel, LocalDateTime start, LocalDateTime end);
    
    @Query("{'decision': 'BLOCK', 'assessmentStartedAt': {'$gte': ?0, '$lte': ?1}}")
    List<RiskAssessment> findBlockedTransactionsBetween(LocalDateTime start, LocalDateTime end);
}