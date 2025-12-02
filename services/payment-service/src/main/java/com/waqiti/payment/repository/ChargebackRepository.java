package com.waqiti.payment.repository;

import com.waqiti.payment.domain.Chargeback;
import com.waqiti.payment.domain.ChargebackStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChargebackRepository extends MongoRepository<Chargeback, String> {
    
    boolean existsByProviderChargebackIdOrEventId(String providerChargebackId, String eventId);
    
    Optional<Chargeback> findByPaymentId(String paymentId);
    
    boolean existsByPaymentIdAndStatus(String paymentId, ChargebackStatus status);
    
    List<Chargeback> findByMerchantIdAndStatus(String merchantId, ChargebackStatus status);
    
    @Query("{'merchantId': ?0, 'receivedAt': {'$gte': ?1, '$lte': ?2}}")
    List<Chargeback> findByMerchantIdAndDateRange(String merchantId, LocalDateTime start, LocalDateTime end);
    
    @Query("{'dueDate': {'$lte': ?0}, 'status': {'$in': ['INITIATED', 'PENDING_RESPONSE']}}")
    List<Chargeback> findOverdueChargebacks(LocalDateTime now);
    
    @Query("{'status': 'DISPUTED', 'responseSubmittedAt': {'$gte': ?0}}")
    List<Chargeback> findRecentDisputes(LocalDateTime since);
    
    List<Chargeback> findByUserIdAndReceivedAtBetween(String userId, LocalDateTime start, LocalDateTime end);
    
    @Query("{'chargebackAmount': {'$gte': ?0}}")
    List<Chargeback> findHighValueChargebacks(java.math.BigDecimal threshold);
    
    long countByMerchantIdAndStatus(String merchantId, ChargebackStatus status);
    
    @Query("{'defensible': true, 'responseStrategy': 'FIGHT'}")
    List<Chargeback> findDefensibleChargebacks();
}