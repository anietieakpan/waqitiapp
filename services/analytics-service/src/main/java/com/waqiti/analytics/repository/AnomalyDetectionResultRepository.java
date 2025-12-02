package com.waqiti.analytics.repository;

import com.waqiti.analytics.domain.AnomalyDetectionResult;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for anomaly detection results
 */
@Repository
public interface AnomalyDetectionResultRepository extends MongoRepository<AnomalyDetectionResult, String> {
    
    Optional<AnomalyDetectionResult> findByResultId(String resultId);
    
    List<AnomalyDetectionResult> findByDetectionId(String detectionId);
    
    List<AnomalyDetectionResult> findByUserId(String userId);
    
    List<AnomalyDetectionResult> findByResultType(String resultType);
    
    List<AnomalyDetectionResult> findByStatus(String status);
    
    @Query("{'processedAt': {$gte: ?0, $lte: ?1}}")
    List<AnomalyDetectionResult> findByProcessedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("{'confidence': {$gte: ?0}}")
    List<AnomalyDetectionResult> findByConfidenceGreaterThanEqual(Double confidence);
    
    List<AnomalyDetectionResult> findByResultTypeAndStatus(String resultType, String status);
    
    long countByResultType(String resultType);
    
    long countByStatus(String status);
}