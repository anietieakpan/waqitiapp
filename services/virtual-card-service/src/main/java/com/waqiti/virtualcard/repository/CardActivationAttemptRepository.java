package com.waqiti.virtualcard.repository;

import com.waqiti.virtualcard.domain.CardActivationAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for Card Activation Attempt operations
 */
@Repository
public interface CardActivationAttemptRepository extends JpaRepository<CardActivationAttempt, String> {
    
    /**
     * Find attempts by card ID
     */
    List<CardActivationAttempt> findByCardIdOrderByAttemptedAtDesc(String cardId);
    
    /**
     * Find attempts by user ID
     */
    List<CardActivationAttempt> findByUserIdOrderByAttemptedAtDesc(String userId);
    
    /**
     * Find failed attempts by card ID
     */
    List<CardActivationAttempt> findByCardIdAndSuccessfulFalseOrderByAttemptedAtDesc(String cardId);
    
    /**
     * Count failed attempts for a card within time period
     */
    @Query("SELECT COUNT(a) FROM CardActivationAttempt a WHERE a.cardId = :cardId " +
           "AND a.successful = false AND a.attemptedAt > :since")
    long countFailedAttemptsSince(@Param("cardId") String cardId, @Param("since") Instant since);
    
    /**
     * Find attempts by IP address
     */
    List<CardActivationAttempt> findByIpAddressOrderByAttemptedAtDesc(String ipAddress);
    
    /**
     * Find attempts within date range
     */
    @Query("SELECT a FROM CardActivationAttempt a WHERE a.attemptedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY a.attemptedAt DESC")
    List<CardActivationAttempt> findByAttemptedAtBetween(@Param("startDate") Instant startDate,
                                                         @Param("endDate") Instant endDate);
    
    /**
     * Find suspicious activation patterns (multiple failed attempts from same IP)
     */
    @Query("SELECT a.ipAddress, COUNT(a) FROM CardActivationAttempt a " +
           "WHERE a.successful = false AND a.attemptedAt > :since " +
           "GROUP BY a.ipAddress HAVING COUNT(a) > :threshold")
    List<Object[]> findSuspiciousIpPatterns(@Param("since") Instant since, @Param("threshold") long threshold);
}