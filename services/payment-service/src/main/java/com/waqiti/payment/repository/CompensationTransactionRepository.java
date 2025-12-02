package com.waqiti.payment.repository;

import com.waqiti.payment.entity.CompensationTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompensationTransactionRepository extends JpaRepository<CompensationTransaction, String> {
    
    Optional<CompensationTransaction> findByPaymentId(String paymentId);
    
    List<CompensationTransaction> findByStatus(String status);
    
    @Query("SELECT c FROM CompensationTransaction c WHERE c.paymentId = :paymentId ORDER BY c.initiatedAt DESC")
    List<CompensationTransaction> findAllByPaymentIdOrderByInitiatedAtDesc(@Param("paymentId") String paymentId);
    
    @Query("SELECT c FROM CompensationTransaction c WHERE c.status = :status AND c.initiatedAt < :before")
    List<CompensationTransaction> findStuckCompensations(@Param("status") String status, @Param("before") LocalDateTime before);
    
    @Query("SELECT c FROM CompensationTransaction c WHERE c.status = 'IN_PROGRESS' AND c.retryCount < :maxRetries")
    List<CompensationTransaction> findRetryableCompensations(@Param("maxRetries") int maxRetries);
    
    @Query("SELECT COUNT(c) FROM CompensationTransaction c WHERE c.status = :status")
    long countByStatus(@Param("status") String status);
    
    @Query("SELECT c FROM CompensationTransaction c WHERE c.initiatedAt >= :since")
    List<CompensationTransaction> findRecentCompensations(@Param("since") LocalDateTime since);
}