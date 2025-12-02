package com.waqiti.payment.repository;

import com.waqiti.payment.domain.FeeCalculation;
import com.waqiti.payment.domain.FeeStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FeeCalculationRepository extends MongoRepository<FeeCalculation, String> {
    
    boolean existsByTransactionIdAndEventId(String transactionId, String eventId);
    
    Optional<FeeCalculation> findByTransactionId(String transactionId);
    
    List<FeeCalculation> findByMerchantIdAndCalculatedAtBetween(String merchantId, LocalDateTime start, LocalDateTime end);
    
    List<FeeCalculation> findByStatus(FeeStatus status);
    
    @Query("{'merchantId': ?0, 'totalFee': {'$gte': ?1}}")
    List<FeeCalculation> findByMerchantIdAndTotalFeeGreaterThan(String merchantId, BigDecimal amount);
    
    @Query("{'hasOverride': true, 'overrideAppliedAt': {'$gte': ?0, '$lte': ?1}}")
    List<FeeCalculation> findOverridesBetween(LocalDateTime start, LocalDateTime end);
    
    long countByMerchantIdAndStatus(String merchantId, FeeStatus status);
    
    @Query("{'merchantId': ?0, 'calculatedAt': {'$gte': ?1, '$lte': ?2}}")
    List<FeeCalculation> findByMerchantIdAndPeriod(String merchantId, LocalDateTime start, LocalDateTime end);
    
    @Query("{'effectiveFeePercentage': {'$gte': ?0}}")
    List<FeeCalculation> findHighFeeTransactions(BigDecimal threshold);
    
    @Query("{'feeCapped': true, 'calculatedAt': {'$gte': ?0}}")
    List<FeeCalculation> findCappedFeesSince(LocalDateTime since);
}