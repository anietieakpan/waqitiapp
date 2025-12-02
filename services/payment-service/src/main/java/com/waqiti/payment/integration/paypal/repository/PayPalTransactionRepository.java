package com.waqiti.payment.integration.paypal.repository;

import com.waqiti.payment.integration.paypal.domain.PayPalTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PayPal transaction repository
 */
@Repository
public interface PayPalTransactionRepository extends JpaRepository<PayPalTransaction, UUID> {
    
    Optional<PayPalTransaction> findByPaypalOrderId(String paypalOrderId);
    
    Optional<PayPalTransaction> findByPaypalTransactionId(String paypalTransactionId);
    
    List<PayPalTransaction> findByUserIdAndCreatedAtBetween(UUID userId, LocalDateTime start, LocalDateTime end);
    
    @Query("SELECT pt FROM PayPalTransaction pt WHERE pt.userId = :userId AND pt.status = :status")
    List<PayPalTransaction> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") String status);
    
    @Query("SELECT COUNT(pt) FROM PayPalTransaction pt WHERE pt.createdAt >= :since")
    long countTransactionsSince(@Param("since") LocalDateTime since);
}