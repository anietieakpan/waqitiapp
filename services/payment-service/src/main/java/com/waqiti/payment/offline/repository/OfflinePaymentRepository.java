package com.waqiti.payment.offline.repository;

import com.waqiti.payment.offline.domain.OfflinePayment;
import com.waqiti.payment.offline.domain.OfflinePaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OfflinePaymentRepository extends JpaRepository<OfflinePayment, UUID> {
    
    List<OfflinePayment> findBySenderIdAndStatus(String senderId, OfflinePaymentStatus status);
    
    List<OfflinePayment> findByRecipientIdAndStatus(String recipientId, OfflinePaymentStatus status);
    
    long countBySenderIdAndStatus(String senderId, OfflinePaymentStatus status);
    
    @Query("SELECT COALESCE(SUM(o.amount), 0) FROM OfflinePayment o " +
           "WHERE o.senderId = :senderId AND o.createdAt >= :since " +
           "AND o.status IN ('PENDING_SYNC', 'ACCEPTED_OFFLINE', 'SYNCING', 'SYNCED')")
    BigDecimal getDailyOfflineTotal(@Param("senderId") String senderId, @Param("since") LocalDateTime since);
    
    @Query("SELECT o FROM OfflinePayment o WHERE o.status = 'PENDING_SYNC' " +
           "AND o.createdAt < :expiryTime")
    List<OfflinePayment> findExpiredOfflinePayments(@Param("expiryTime") LocalDateTime expiryTime);
    
    @Query("SELECT o FROM OfflinePayment o WHERE o.status = 'SYNC_FAILED' " +
           "AND o.syncAttempts < :maxAttempts")
    List<OfflinePayment> findRetryableOfflinePayments(@Param("maxAttempts") int maxAttempts);
    
    List<OfflinePayment> findBySenderIdAndCreatedAtBetween(String senderId, LocalDateTime start, LocalDateTime end);
    
    List<OfflinePayment> findByRecipientIdAndCreatedAtBetween(String recipientId, LocalDateTime start, LocalDateTime end);
}