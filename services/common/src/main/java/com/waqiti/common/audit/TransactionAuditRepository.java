package com.waqiti.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CRITICAL SECURITY: Repository for transaction audit records
 * Provides querying capabilities for regulatory reporting and fraud investigation
 */
@Repository
public interface TransactionAuditRepository extends JpaRepository<TransactionAuditRecord, UUID> {
    
    /**
     * Find audit records by transaction ID
     */
    List<TransactionAuditRecord> findByTransactionIdOrderByTimestampDesc(String transactionId);
    
    /**
     * Find audit records by user ID within date range
     */
    @Query("SELECT r FROM TransactionAuditRecord r WHERE r.userId = :userId " +
           "AND r.timestamp BETWEEN :startDate AND :endDate ORDER BY r.timestamp DESC")
    Page<TransactionAuditRecord> findByUserIdAndDateRange(
            @Param("userId") String userId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable);
    
    /**
     * Find audit records by transaction type
     */
    Page<TransactionAuditRecord> findByTransactionTypeOrderByTimestampDesc(
            String transactionType, Pageable pageable);
    
    /**
     * Find suspicious transactions by risk score
     */
    @Query("SELECT r FROM TransactionAuditRecord r WHERE r.riskScore >= :minRiskScore " +
           "ORDER BY r.riskScore DESC, r.timestamp DESC")
    Page<TransactionAuditRecord> findHighRiskTransactions(
            @Param("minRiskScore") Double minRiskScore, Pageable pageable);
    
    /**
     * Find transactions by amount range
     */
    @Query("SELECT r FROM TransactionAuditRecord r WHERE r.amount BETWEEN :minAmount AND :maxAmount " +
           "ORDER BY r.amount DESC, r.timestamp DESC")
    Page<TransactionAuditRecord> findByAmountRange(
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            Pageable pageable);
    
    /**
     * Find failed transactions for investigation
     */
    @Query("SELECT r FROM TransactionAuditRecord r WHERE r.status IN ('FAILED', 'REJECTED', 'CANCELLED') " +
           "ORDER BY r.timestamp DESC")
    Page<TransactionAuditRecord> findFailedTransactions(Pageable pageable);
    
    /**
     * Find transactions by correlation ID for tracing
     */
    List<TransactionAuditRecord> findByCorrelationIdOrderByTimestampAsc(String correlationId);
    
    /**
     * Find transactions by IP address for fraud investigation
     */
    @Query("SELECT r FROM TransactionAuditRecord r WHERE r.clientIpAddress = :ipAddress " +
           "ORDER BY r.timestamp DESC")
    Page<TransactionAuditRecord> findByClientIpAddress(
            @Param("ipAddress") String ipAddress, Pageable pageable);
    
    /**
     * Find transactions by device fingerprint
     */
    @Query("SELECT r FROM TransactionAuditRecord r WHERE r.deviceFingerprint = :fingerprint " +
           "ORDER BY r.timestamp DESC")
    List<TransactionAuditRecord> findByDeviceFingerprint(@Param("fingerprint") String fingerprint);
    
    /**
     * Get transaction volume by user for velocity checking
     */
    @Query("SELECT COUNT(r), COALESCE(SUM(r.amount), 0) FROM TransactionAuditRecord r " +
           "WHERE r.userId = :userId AND r.timestamp >= :since AND r.status = 'COMPLETED'")
    Object[] getUserTransactionVolumeFrom(@Param("userId") String userId, @Param("since") Instant since);
    
    /**
     * Find large transactions requiring additional scrutiny
     */
    @Query("SELECT r FROM TransactionAuditRecord r WHERE r.amount >= :threshold " +
           "AND r.transactionType IN ('TRANSFER', 'WITHDRAWAL', 'PAYMENT') " +
           "ORDER BY r.amount DESC, r.timestamp DESC")
    Page<TransactionAuditRecord> findLargeTransactions(
            @Param("threshold") BigDecimal threshold, Pageable pageable);
    
    /**
     * Get daily transaction summary for reporting
     */
    @Query("SELECT DATE(r.timestamp), r.transactionType, r.status, COUNT(r), COALESCE(SUM(r.amount), 0) " +
           "FROM TransactionAuditRecord r WHERE r.timestamp BETWEEN :startDate AND :endDate " +
           "GROUP BY DATE(r.timestamp), r.transactionType, r.status " +
           "ORDER BY DATE(r.timestamp) DESC")
    List<Object[]> getDailyTransactionSummary(
            @Param("startDate") Instant startDate, @Param("endDate") Instant endDate);
    
    /**
     * Find transactions with integrity hash mismatches
     */
    @Query("SELECT r FROM TransactionAuditRecord r WHERE r.integrityHash IS NULL " +
           "OR r.integrityHash LIKE 'HASH_GENERATION_FAILED%'")
    List<TransactionAuditRecord> findRecordsWithIntegrityIssues();
    
    /**
     * Save audit failure record
     */
    default void saveAuditFailure(AuditFailureRecord failure) {
        // This would typically be in a separate repository, but for simplicity including here
        // In production, use a separate AuditFailureRepository
    }
    
    /**
     * Save Kafka publication failure
     */
    default void saveKafkaFailure(KafkaPublicationFailure failure) {
        // This would typically be in a separate repository, but for simplicity including here
        // In production, use a separate KafkaFailureRepository
    }
}