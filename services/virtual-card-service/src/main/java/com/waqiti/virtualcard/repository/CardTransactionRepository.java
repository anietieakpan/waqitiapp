package com.waqiti.virtualcard.repository;

import com.waqiti.virtualcard.domain.CardTransaction;
import com.waqiti.virtualcard.domain.TransactionStatus;
import com.waqiti.virtualcard.domain.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for CardTransaction entity
 */
@Repository
public interface CardTransactionRepository extends JpaRepository<CardTransaction, String> {
    
    /**
     * Find transactions by card ID
     */
    List<CardTransaction> findByCardId(String cardId);
    
    /**
     * Find transactions by card ID with pagination
     */
    Page<CardTransaction> findByCardId(String cardId, Pageable pageable);
    
    /**
     * Find transaction by transaction ID
     */
    Optional<CardTransaction> findByTransactionId(String transactionId);
    
    /**
     * Find transactions by card ID and status
     */
    List<CardTransaction> findByCardIdAndStatus(String cardId, TransactionStatus status);
    
    /**
     * Find transactions by card ID and date range
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.cardId = :cardId AND " +
           "t.transactionDate BETWEEN :fromDate AND :toDate ORDER BY t.transactionDate DESC")
    List<CardTransaction> findByCardIdAndDateRange(@Param("cardId") String cardId,
                                                  @Param("fromDate") LocalDateTime fromDate,
                                                  @Param("toDate") LocalDateTime toDate);
    
    /**
     * Find transactions with filters
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.cardId = :cardId " +
           "AND (:fromDate IS NULL OR t.transactionDate >= :fromDate) " +
           "AND (:toDate IS NULL OR t.transactionDate <= :toDate) " +
           "AND (:status IS NULL OR t.status = :status) " +
           "AND (:minAmount IS NULL OR t.amount >= :minAmount) " +
           "AND (:maxAmount IS NULL OR t.amount <= :maxAmount) " +
           "ORDER BY t.transactionDate DESC")
    List<CardTransaction> findByCardIdAndFilters(@Param("cardId") String cardId,
                                               @Param("fromDate") LocalDateTime fromDate,
                                               @Param("toDate") LocalDateTime toDate,
                                               @Param("status") TransactionStatus status,
                                               @Param("minAmount") BigDecimal minAmount,
                                               @Param("maxAmount") BigDecimal maxAmount);
    
    /**
     * Find recent transactions by card ID
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.cardId = :cardId " +
           "ORDER BY t.transactionDate DESC")
    List<CardTransaction> findRecentByCardId(@Param("cardId") String cardId, Pageable pageable);
    
    /**
     * Find transactions by merchant name
     */
    List<CardTransaction> findByMerchantNameContainingIgnoreCase(String merchantName);
    
    /**
     * Find transactions by merchant ID
     */
    List<CardTransaction> findByMerchantId(String merchantId);
    
    /**
     * Find transactions by merchant category
     */
    List<CardTransaction> findByMerchantCategory(String merchantCategory);
    
    /**
     * Find transactions by merchant category code
     */
    List<CardTransaction> findByMerchantCategoryCode(String mcc);
    
    /**
     * Find transactions by authorization code
     */
    Optional<CardTransaction> findByAuthorizationCode(String authorizationCode);
    
    /**
     * Find international transactions
     */
    List<CardTransaction> findByIsInternationalTrue();
    
    /**
     * Find online transactions
     */
    List<CardTransaction> findByIsOnlineTrue();
    
    /**
     * Find contactless transactions
     */
    List<CardTransaction> findByIsContactlessTrue();
    
    /**
     * Find recurring transactions
     */
    List<CardTransaction> findByIsRecurringTrue();
    
    /**
     * Find 3D Secure transactions
     */
    List<CardTransaction> findByThreeDSecureTrue();
    
    /**
     * Find transactions by transaction type
     */
    List<CardTransaction> findByTransactionType(TransactionType transactionType);
    
    /**
     * Find transactions by multiple statuses
     */
    List<CardTransaction> findByStatusIn(List<TransactionStatus> statuses);
    
    /**
     * Find high-value transactions
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.amount >= :minAmount ORDER BY t.amount DESC")
    List<CardTransaction> findHighValueTransactions(@Param("minAmount") BigDecimal minAmount);
    
    /**
     * Find high-risk transactions
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.riskScore >= :minRiskScore ORDER BY t.riskScore DESC")
    List<CardTransaction> findHighRiskTransactions(@Param("minRiskScore") Integer minRiskScore);
    
    /**
     * Find suspicious transactions
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.fraudScore >= :minFraudScore ORDER BY t.fraudScore DESC")
    List<CardTransaction> findSuspiciousTransactions(@Param("minFraudScore") Integer minFraudScore);
    
    /**
     * Count transactions by card ID and status
     */
    int countByCardIdAndStatus(String cardId, TransactionStatus status);
    
    /**
     * Count transactions by card ID and date range
     */
    @Query("SELECT COUNT(t) FROM CardTransaction t WHERE t.cardId = :cardId AND " +
           "t.transactionDate BETWEEN :fromDate AND :toDate")
    long countByCardIdAndDateRange(@Param("cardId") String cardId,
                                  @Param("fromDate") LocalDateTime fromDate,
                                  @Param("toDate") LocalDateTime toDate);
    
    /**
     * Sum transaction amounts by card ID and status
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM CardTransaction t WHERE t.cardId = :cardId AND t.status = :status")
    BigDecimal sumAmountByCardIdAndStatus(@Param("cardId") String cardId, @Param("status") TransactionStatus status);
    
    /**
     * Sum transaction amounts by card ID and date range
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM CardTransaction t WHERE t.cardId = :cardId AND " +
           "t.transactionDate BETWEEN :fromDate AND :toDate")
    BigDecimal sumAmountByCardIdAndDateRange(@Param("cardId") String cardId,
                                           @Param("fromDate") LocalDateTime fromDate,
                                           @Param("toDate") LocalDateTime toDate);
    
    /**
     * Find daily transaction statistics
     */
    @Query("SELECT DATE(t.transactionDate), COUNT(t), COALESCE(SUM(t.amount), 0) " +
           "FROM CardTransaction t WHERE t.cardId = :cardId AND " +
           "t.transactionDate >= :fromDate GROUP BY DATE(t.transactionDate) " +
           "ORDER BY DATE(t.transactionDate) DESC")
    List<Object[]> findDailyTransactionStats(@Param("cardId") String cardId, 
                                           @Param("fromDate") LocalDateTime fromDate);
    
    /**
     * Find merchant spending statistics
     */
    @Query("SELECT t.merchantName, COUNT(t), COALESCE(SUM(t.amount), 0) " +
           "FROM CardTransaction t WHERE t.cardId = :cardId AND " +
           "t.status = :status GROUP BY t.merchantName " +
           "ORDER BY SUM(t.amount) DESC")
    List<Object[]> findMerchantSpendingStats(@Param("cardId") String cardId, 
                                           @Param("status") TransactionStatus status);
    
    /**
     * Find category spending statistics
     */
    @Query("SELECT t.merchantCategory, COUNT(t), COALESCE(SUM(t.amount), 0) " +
           "FROM CardTransaction t WHERE t.cardId = :cardId AND " +
           "t.status = :status GROUP BY t.merchantCategory " +
           "ORDER BY SUM(t.amount) DESC")
    List<Object[]> findCategorySpendingStats(@Param("cardId") String cardId, 
                                           @Param("status") TransactionStatus status);
    
    /**
     * Find failed transactions requiring investigation
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.status IN :failedStatuses AND " +
           "t.transactionDate >= :fromDate ORDER BY t.transactionDate DESC")
    List<CardTransaction> findFailedTransactionsForInvestigation(@Param("failedStatuses") List<TransactionStatus> failedStatuses,
                                                               @Param("fromDate") LocalDateTime fromDate);
    
    /**
     * Find duplicate transactions (same amount, merchant, within time window)
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.cardId = :cardId AND " +
           "t.amount = :amount AND t.merchantName = :merchantName AND " +
           "t.transactionDate BETWEEN :startTime AND :endTime AND t.id != :excludeId")
    List<CardTransaction> findPotentialDuplicates(@Param("cardId") String cardId,
                                                @Param("amount") BigDecimal amount,
                                                @Param("merchantName") String merchantName,
                                                @Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime,
                                                @Param("excludeId") String excludeId);
    
    /**
     * Find transactions by terminal ID
     */
    List<CardTransaction> findByTerminalId(String terminalId);
    
    /**
     * Find transactions by retrieval reference
     */
    Optional<CardTransaction> findByRetrievalReference(String retrievalReference);
    
    /**
     * Find transactions requiring MFA
     */
    @Query("SELECT t FROM CardTransaction t WHERE t.mfaVerified = false AND " +
           "t.status = :status AND t.transactionDate >= :fromDate")
    List<CardTransaction> findTransactionsRequiringMfa(@Param("status") TransactionStatus status,
                                                      @Param("fromDate") LocalDateTime fromDate);
    
    /**
     * Find transactions by country
     */
    List<CardTransaction> findByMerchantCountry(String country);
    
    /**
     * Count international transactions
     */
    @Query("SELECT COUNT(t) FROM CardTransaction t WHERE t.cardId = :cardId AND t.isInternational = true")
    long countInternationalTransactions(@Param("cardId") String cardId);
    
    /**
     * Find velocity violations (multiple transactions in short time)
     */
    @Query("SELECT t.cardId, COUNT(t) FROM CardTransaction t WHERE " +
           "t.transactionDate BETWEEN :startTime AND :endTime " +
           "GROUP BY t.cardId HAVING COUNT(t) > :threshold")
    List<Object[]> findVelocityViolations(@Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime,
                                        @Param("threshold") long threshold);
}