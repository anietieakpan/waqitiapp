package com.waqiti.crypto.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Crypto Transaction Pattern Repository
 * 
 * Provides pattern analysis for cryptocurrency transactions to support fraud detection.
 * This repository is specifically designed for crypto-specific behavioral patterns.
 * 
 * @author Waqiti Crypto Security Team
 * @version 1.0 - Production Implementation
 */
@Repository
public interface CryptoTransactionPatternRepository extends JpaRepository<CryptoTransactionPatternEntity, UUID> {

    /**
     * Find typical country for user based on transaction history
     */
    @Query(value = "SELECT country_code FROM crypto_transaction_patterns " +
           "WHERE user_id = :userId AND timestamp > :since " +
           "GROUP BY country_code ORDER BY COUNT(*) DESC LIMIT 1", 
           nativeQuery = true)
    Optional<String> findTypicalCountryByUserId(@Param("userId") UUID userId);
    
    /**
     * Check if device fingerprint has been used by user recently
     */
    @Query("SELECT COUNT(p) > 0 FROM CryptoTransactionPatternEntity p " +
           "WHERE p.userId = :userId AND p.deviceFingerprint = :deviceFingerprint " +
           "AND p.timestamp >= :timestampAfter")
    Boolean existsByUserIdAndDeviceFingerprintAndTimestampAfter(
        @Param("userId") UUID userId,
        @Param("deviceFingerprint") String deviceFingerprint,
        @Param("timestampAfter") LocalDateTime timestampAfter
    );
    
    /**
     * Find recent transactions by user
     */
    @Query("SELECT p FROM CryptoTransactionPatternEntity p " +
           "WHERE p.userId = :userId AND p.timestamp >= :timestampAfter " +
           "ORDER BY p.timestamp DESC")
    List<CryptoTransactionPatternEntity> findByUserIdAndTimestampAfter(
        @Param("userId") UUID userId,
        @Param("timestampAfter") LocalDateTime timestampAfter
    );
    
    /**
     * Find sent transactions to specific address
     */
    @Query("SELECT p FROM CryptoTransactionPatternEntity p " +
           "WHERE p.userId = :userId AND p.toAddress = :toAddress " +
           "AND p.transactionType = 'SEND' " +
           "ORDER BY p.timestamp DESC")
    List<CryptoTransactionPatternEntity> findSentTransactionsByUserAndAddress(
        @Param("userId") UUID userId,
        @Param("toAddress") String toAddress
    );
    
    /**
     * Find received transactions from specific address
     */
    @Query("SELECT p FROM CryptoTransactionPatternEntity p " +
           "WHERE p.userId = :userId AND p.fromAddress = :fromAddress " +
           "AND p.transactionType = 'RECEIVE' " +
           "ORDER BY p.timestamp DESC")
    List<CryptoTransactionPatternEntity> findReceivedTransactionsByUserFromAddress(
        @Param("userId") UUID userId,
        @Param("fromAddress") String fromAddress
    );
    
    /**
     * Count transactions by user in time window
     */
    @Query("SELECT COUNT(p) FROM CryptoTransactionPatternEntity p " +
           "WHERE p.userId = :userId " +
           "AND p.timestamp BETWEEN :startTime AND :endTime")
    Long countByUserIdAndTimestampBetween(
        @Param("userId") UUID userId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Get total transaction amount by user in time window
     */
    @Query("SELECT COALESCE(SUM(p.amountUsd), 0) FROM CryptoTransactionPatternEntity p " +
           "WHERE p.userId = :userId " +
           "AND p.timestamp BETWEEN :startTime AND :endTime")
    BigDecimal sumAmountByUserIdAndTimestampBetween(
        @Param("userId") UUID userId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Find transactions with round amounts (potential structuring)
     */
    @Query("SELECT p FROM CryptoTransactionPatternEntity p " +
           "WHERE p.userId = :userId " +
           "AND p.isRoundAmount = true " +
           "AND p.timestamp >= :timestampAfter " +
           "ORDER BY p.timestamp DESC")
    List<CryptoTransactionPatternEntity> findRoundAmountTransactions(
        @Param("userId") UUID userId,
        @Param("timestampAfter") LocalDateTime timestampAfter
    );
    
    /**
     * Find high-risk transactions for user
     */
    @Query("SELECT p FROM CryptoTransactionPatternEntity p " +
           "WHERE p.userId = :userId " +
           "AND p.riskScore >= :riskThreshold " +
           "ORDER BY p.riskScore DESC, p.timestamp DESC")
    List<CryptoTransactionPatternEntity> findHighRiskTransactionsByUser(
        @Param("userId") UUID userId,
        @Param("riskThreshold") Double riskThreshold
    );
    
    /**
     * Find frequent recipients for user
     */
    @Query("SELECT p.toAddress, COUNT(p) as frequency FROM CryptoTransactionPatternEntity p " +
           "WHERE p.userId = :userId AND p.toAddress IS NOT NULL " +
           "AND p.timestamp >= :timestampAfter " +
           "GROUP BY p.toAddress HAVING COUNT(p) >= :minCount " +
           "ORDER BY frequency DESC")
    List<Object[]> findFrequentRecipients(
        @Param("userId") UUID userId,
        @Param("timestampAfter") LocalDateTime timestampAfter,
        @Param("minCount") Integer minCount
    );
    
    /**
     * Find circular transaction patterns (potential money laundering)
     */
    @Query(value = "WITH RECURSIVE circular_txn AS ( " +
           "  SELECT t1.user_id, t1.to_address, t1.from_address, 1 as depth, " +
           "         ARRAY[t1.to_address] as path " +
           "  FROM crypto_transaction_patterns t1 " +
           "  WHERE t1.user_id = :userId AND t1.timestamp > :since " +
           "  UNION ALL " +
           "  SELECT c.user_id, t2.to_address, t2.from_address, c.depth + 1, " +
           "         c.path || t2.to_address " +
           "  FROM circular_txn c " +
           "  JOIN crypto_transaction_patterns t2 ON c.to_address = t2.from_address " +
           "  WHERE c.depth < 5 AND NOT t2.to_address = ANY(c.path) " +
           ") " +
           "SELECT DISTINCT from_address FROM circular_txn " +
           "WHERE to_address = :toAddress AND depth >= 2", 
           nativeQuery = true)
    List<String> findCircularTransactionPath(
        @Param("userId") UUID userId,
        @Param("toAddress") String toAddress,
        @Param("since") LocalDateTime since
    );
    
    /**
     * Get average transaction amount for user
     */
    @Query("SELECT COALESCE(AVG(p.amountUsd), 0) FROM CryptoTransactionPatternEntity p " +
           "WHERE p.userId = :userId " +
           "AND p.timestamp >= :timestampAfter")
    BigDecimal getAverageTransactionAmount(
        @Param("userId") UUID userId,
        @Param("timestampAfter") LocalDateTime timestampAfter
    );
    
    /**
     * Find split transaction patterns (amounts adding up to suspicious total)
     */
    @Query(value = "SELECT SUM(amount_usd) as total_amount, COUNT(*) as txn_count " +
           "FROM crypto_transaction_patterns " +
           "WHERE user_id = :userId " +
           "AND timestamp BETWEEN :startTime AND :endTime " +
           "AND amount_usd < :maxIndividualAmount " +
           "GROUP BY DATE_TRUNC('hour', timestamp) " +
           "HAVING SUM(amount_usd) >= :suspiciousTotal " +
           "AND COUNT(*) >= 3", 
           nativeQuery = true)
    List<Object[]> findSplitTransactionPatterns(
        @Param("userId") UUID userId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        @Param("maxIndividualAmount") BigDecimal maxIndividualAmount,
        @Param("suspiciousTotal") BigDecimal suspiciousTotal
    );
    
    /**
     * Check if user has used specific currency before
     */
    @Query("SELECT COUNT(p) > 0 FROM CryptoTransactionPatternEntity p " +
           "WHERE p.userId = :userId AND p.currency = :currency")
    Boolean existsByUserIdAndCurrency(
        @Param("userId") UUID userId,
        @Param("currency") String currency
    );
    
    /**
     * Get distinct currencies used by user
     */
    @Query("SELECT DISTINCT p.currency FROM CryptoTransactionPatternEntity p " +
           "WHERE p.userId = :userId")
    List<String> findDistinctCurrenciesByUserId(@Param("userId") UUID userId);
    
    /**
     * Find sequential transactions (rapid succession)
     */
    @Query("SELECT p FROM CryptoTransactionPatternEntity p " +
           "WHERE p.userId = :userId " +
           "AND p.timestamp >= :timestampAfter " +
           "ORDER BY p.timestamp ASC")
    List<CryptoTransactionPatternEntity> findSequentialTransactions(
        @Param("userId") UUID userId,
        @Param("timestampAfter") LocalDateTime timestampAfter
    );
    
    /**
     * Count distinct IP addresses used by user
     */
    @Query("SELECT COUNT(DISTINCT p.ipAddress) FROM CryptoTransactionPatternEntity p " +
           "WHERE p.userId = :userId " +
           "AND p.timestamp >= :timestampAfter")
    Long countDistinctIpAddresses(
        @Param("userId") UUID userId,
        @Param("timestampAfter") LocalDateTime timestampAfter
    );
    
    /**
     * Count distinct devices used by user
     */
    @Query("SELECT COUNT(DISTINCT p.deviceFingerprint) FROM CryptoTransactionPatternEntity p " +
           "WHERE p.userId = :userId " +
           "AND p.timestamp >= :timestampAfter " +
           "AND p.deviceFingerprint IS NOT NULL")
    Long countDistinctDevices(
        @Param("userId") UUID userId,
        @Param("timestampAfter") LocalDateTime timestampAfter
    );
    
    /**
     * Find first transaction by user for specific currency
     */
    @Query("SELECT p FROM CryptoTransactionPatternEntity p " +
           "WHERE p.userId = :userId AND p.currency = :currency " +
           "ORDER BY p.timestamp ASC LIMIT 1")
    Optional<CryptoTransactionPatternEntity> findFirstTransactionForCurrency(
        @Param("userId") UUID userId,
        @Param("currency") String currency
    );
}