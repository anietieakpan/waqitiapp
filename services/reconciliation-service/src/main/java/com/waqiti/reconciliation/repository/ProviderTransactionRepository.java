package com.waqiti.reconciliation.repository;

import com.waqiti.reconciliation.model.ProviderTransaction;
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
 * Repository for managing ProviderTransaction entities
 * Handles transactions from external payment providers (Stripe, PayPal, etc.)
 */
@Repository
public interface ProviderTransactionRepository extends JpaRepository<ProviderTransaction, String> {

    /**
     * Find provider transaction by provider transaction ID and amount
     */
    @Query("SELECT pt FROM ProviderTransaction pt WHERE pt.providerTransactionId = :providerTransactionId " +
           "AND pt.amount = :amount AND pt.matched = false")
    List<ProviderTransaction> findByProviderTransactionIdAndAmount(
            @Param("providerTransactionId") String providerTransactionId,
            @Param("amount") BigDecimal amount
    );

    /**
     * Find provider transactions by amount and timestamp range
     */
    @Query("SELECT pt FROM ProviderTransaction pt WHERE pt.amount = :amount " +
           "AND pt.timestamp BETWEEN :startTime AND :endTime AND pt.matched = false")
    List<ProviderTransaction> findByAmountAndTimestampBetween(
            @Param("amount") BigDecimal amount,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Find provider transactions by amount range and timestamp range
     */
    @Query("SELECT pt FROM ProviderTransaction pt WHERE pt.amount BETWEEN :minAmount AND :maxAmount " +
           "AND pt.timestamp BETWEEN :startTime AND :endTime AND pt.matched = false")
    List<ProviderTransaction> findByAmountBetweenAndTimestampBetween(
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Find unmatched provider transactions before a cutoff time
     */
    @Query("SELECT pt FROM ProviderTransaction pt WHERE pt.matched = false " +
           "AND pt.timestamp < :cutoffTime ORDER BY pt.timestamp ASC")
    List<ProviderTransaction> findUnmatchedBefore(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find provider transactions by provider and status
     */
    @Query("SELECT pt FROM ProviderTransaction pt WHERE pt.provider = :provider AND pt.status = :status")
    List<ProviderTransaction> findByProviderAndStatus(
            @Param("provider") String provider,
            @Param("status") String status
    );

    /**
     * Find provider transactions by matched transaction ID
     */
    Optional<ProviderTransaction> findByMatchedTransactionId(String matchedTransactionId);

    /**
     * Find provider transactions by provider type
     */
    @Query("SELECT pt FROM ProviderTransaction pt WHERE pt.provider = :provider ORDER BY pt.timestamp DESC")
    List<ProviderTransaction> findByProvider(@Param("provider") String provider);

    /**
     * Find provider transactions by date range
     */
    @Query("SELECT pt FROM ProviderTransaction pt WHERE pt.timestamp BETWEEN :startDate AND :endDate")
    List<ProviderTransaction> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Count unmatched provider transactions
     */
    @Query("SELECT COUNT(pt) FROM ProviderTransaction pt WHERE pt.matched = false")
    long countUnmatched();

    /**
     * Find provider transactions by currency
     */
    @Query("SELECT pt FROM ProviderTransaction pt WHERE pt.currency = :currency AND pt.matched = false")
    List<ProviderTransaction> findUnmatchedByCurrency(@Param("currency") String currency);

    /**
     * Find provider transactions by external reference
     */
    Optional<ProviderTransaction> findByExternalReference(String externalReference);

    /**
     * Find provider transactions for reconciliation by provider and date range
     */
    @Query("SELECT pt FROM ProviderTransaction pt WHERE pt.provider = :provider " +
           "AND pt.timestamp BETWEEN :startDate AND :endDate AND pt.matched = false")
    List<ProviderTransaction> findByProviderAndDateRangeUnmatched(
            @Param("provider") String provider,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find matched provider transactions by date range
     */
    @Query("SELECT pt FROM ProviderTransaction pt WHERE pt.matched = true " +
           "AND pt.matchedAt BETWEEN :startDate AND :endDate")
    List<ProviderTransaction> findMatchedBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find provider transactions with high amounts for priority reconciliation
     */
    @Query("SELECT pt FROM ProviderTransaction pt WHERE pt.amount > :threshold " +
           "AND pt.matched = false ORDER BY pt.amount DESC")
    List<ProviderTransaction> findHighValueUnmatched(@Param("threshold") BigDecimal threshold);

    /**
     * Find provider transactions by webhook ID
     */
    Optional<ProviderTransaction> findByWebhookId(String webhookId);

    /**
     * Find failed provider transactions
     */
    @Query("SELECT pt FROM ProviderTransaction pt WHERE pt.status IN :failedStatuses")
    List<ProviderTransaction> findByFailedStatuses(@Param("failedStatuses") List<String> failedStatuses);

    /**
     * Find provider transactions with pagination
     */
    @Query("SELECT pt FROM ProviderTransaction pt WHERE pt.matched = :matched ORDER BY pt.timestamp DESC")
    Page<ProviderTransaction> findByMatchedWithPaging(
            @Param("matched") boolean matched,
            Pageable pageable
    );

    /**
     * Find provider transactions for settlement
     */
    @Query("SELECT pt FROM ProviderTransaction pt WHERE pt.provider = :provider " +
           "AND pt.settlementStatus = 'PENDING' AND pt.timestamp < :cutoffTime")
    List<ProviderTransaction> findForSettlement(
            @Param("provider") String provider,
            @Param("cutoffTime") LocalDateTime cutoffTime
    );

    /**
     * Find potential duplicate provider transactions
     */
    @Query("SELECT pt FROM ProviderTransaction pt WHERE pt.amount = :amount " +
           "AND pt.provider = :provider AND pt.timestamp BETWEEN :startTime AND :endTime " +
           "AND pt.id != :excludeId")
    List<ProviderTransaction> findPotentialDuplicates(
            @Param("amount") BigDecimal amount,
            @Param("provider") String provider,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("excludeId") String excludeId
    );

    /**
     * Update match status
     */
    @Query("UPDATE ProviderTransaction pt SET pt.matched = :matched, pt.matchedAt = :matchedAt, " +
           "pt.matchedTransactionId = :matchedTransactionId WHERE pt.id = :id")
    void updateMatchStatus(
            @Param("id") String id,
            @Param("matched") boolean matched,
            @Param("matchedAt") LocalDateTime matchedAt,
            @Param("matchedTransactionId") String matchedTransactionId
    );
}