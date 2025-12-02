package com.waqiti.payment.repository;

import com.waqiti.payment.domain.Settlement;
import com.waqiti.payment.domain.SettlementStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SettlementRepository extends MongoRepository<Settlement, String> {
    
    boolean existsByReferenceIdOrEventId(String referenceId, String eventId);
    
    List<Settlement> findByMerchantIdAndStatus(String merchantId, SettlementStatus status);
    
    List<Settlement> findByMerchantIdAndSettlementDateBetween(String merchantId, LocalDate start, LocalDate end);
    
    List<Settlement> findByStatusAndSettlementDateBefore(SettlementStatus status, LocalDate date);
    
    @Query("{'merchantId': ?0, 'createdAt': {'$gte': ?1, '$lte': ?2}}")
    List<Settlement> findByMerchantIdAndDateRange(String merchantId, LocalDateTime start, LocalDateTime end);
    
    Optional<Settlement> findByTransferId(String transferId);
    
    List<Settlement> findByStatusInAndCreatedAtBefore(List<SettlementStatus> statuses, LocalDateTime before);
    
    @Query("{'status': 'FAILED', 'failedAt': {'$gte': ?0}}")
    List<Settlement> findRecentFailures(LocalDateTime since);
    
    long countByMerchantIdAndStatus(String merchantId, SettlementStatus status);
}