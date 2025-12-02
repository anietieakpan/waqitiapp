package com.waqiti.frauddetection.repository;

import com.waqiti.frauddetection.entity.FraudPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FraudPredictionRepository extends JpaRepository<FraudPrediction, String> {
    
    List<FraudPrediction> findByUserIdAndCreatedAtAfter(String userId, LocalDateTime after);
    
    List<FraudPrediction> findByTransactionIdAndCreatedAtAfter(String transactionId, LocalDateTime after);
}