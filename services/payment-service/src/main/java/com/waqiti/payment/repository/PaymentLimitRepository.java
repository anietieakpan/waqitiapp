package com.waqiti.payment.repository;

import com.waqiti.payment.domain.PaymentLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentLimitRepository extends JpaRepository<PaymentLimit, String> {
    
    Optional<PaymentLimit> findByUserId(String userId);
    
    Optional<PaymentLimit> findByUserIdAndLimitType(String userId, String limitType);
    
    List<PaymentLimit> findByUserIdAndStatus(String userId, String status);
    
    @Query("SELECT pl FROM PaymentLimit pl WHERE pl.userId = :userId AND pl.limitType = :limitType AND pl.status = 'ACTIVE'")
    Optional<PaymentLimit> findActiveLimitByUserIdAndType(@Param("userId") String userId, @Param("limitType") String limitType);
    
    @Query("SELECT pl FROM PaymentLimit pl WHERE pl.userId = :userId AND pl.dailyLimit < :amount")
    List<PaymentLimit> findByUserIdAndDailyLimitLessThan(@Param("userId") String userId, @Param("amount") BigDecimal amount);
    
    @Query("SELECT pl FROM PaymentLimit pl WHERE pl.expiresAt < :date AND pl.status = 'ACTIVE'")
    List<PaymentLimit> findExpiredLimits(@Param("date") LocalDateTime date);
    
    List<PaymentLimit> findByStatus(String status);
    
    @Query("SELECT COUNT(pl) FROM PaymentLimit pl WHERE pl.userId = :userId AND pl.status = 'ACTIVE'")
    long countActiveLimitsByUserId(@Param("userId") String userId);
}