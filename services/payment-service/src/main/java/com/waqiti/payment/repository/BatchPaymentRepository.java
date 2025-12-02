package com.waqiti.payment.repository;

import com.waqiti.payment.model.BatchPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface BatchPaymentRepository extends JpaRepository<BatchPayment, Long> {
    
    Optional<BatchPayment> findByBatchId(String batchId);
    
    List<BatchPayment> findByStatus(Object status);
    
    List<BatchPayment> findByCreatedBy(String userId);
    
    List<BatchPayment> findByCreatedAtBetween(Instant start, Instant end);
    
    long countByStatus(Object status);
    
    boolean existsByBatchId(String batchId);

    boolean existsByBatchIdAndCreatedAtAfter(String batchId, Instant createdAt);
}