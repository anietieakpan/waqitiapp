package com.waqiti.payment.repository;

import com.waqiti.payment.domain.ConversionStatus;
import com.waqiti.payment.domain.CurrencyConversion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CurrencyConversionRepository extends MongoRepository<CurrencyConversion, String> {
    
    boolean existsByReferenceIdOrEventId(String referenceId, String eventId);
    
    Optional<CurrencyConversion> findByPaymentId(String paymentId);
    
    List<CurrencyConversion> findByUserIdAndStatus(String userId, ConversionStatus status);
    
    @Query("{'userId': ?0, 'requestedAt': {'$gte': ?1, '$lte': ?2}}")
    List<CurrencyConversion> findByUserIdAndDateRange(String userId, LocalDateTime start, LocalDateTime end);
    
    @Query("{'userId': ?0, 'requestedAt': {'$gte': ?1}}")
    BigDecimal getDailyTotalForUser(String userId, LocalDate date);
    
    List<CurrencyConversion> findBySourceCurrencyAndTargetCurrency(String sourceCurrency, String targetCurrency);
    
    @Query("{'sourceCurrency': ?0, 'targetCurrency': ?1, 'requestedAt': {'$gte': ?2}}")
    List<CurrencyConversion> findRecentConversions(String sourceCurrency, String targetCurrency, LocalDateTime since);
    
    @Query("{'rateLocked': true, 'rateLockedUntil': {'$lt': ?0}}")
    List<CurrencyConversion> findExpiredRateLocks(LocalDateTime now);
    
    @Query("{'status': 'FAILED', 'requestedAt': {'$gte': ?0}}")
    List<CurrencyConversion> findRecentFailures(LocalDateTime since);
    
    @Query("{'effectiveSpread': {'$gte': ?0}}")
    List<CurrencyConversion> findHighSpreadConversions(BigDecimal threshold);
    
    long countByUserIdAndRequestedAtBetween(String userId, LocalDateTime start, LocalDateTime end);
}