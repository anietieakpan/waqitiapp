package com.waqiti.accounting.repository;

import com.waqiti.accounting.domain.TransactionFee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Transaction Fee Repository
 * Repository for transaction fee records
 */
@Repository
public interface TransactionFeeRepository extends JpaRepository<TransactionFee, UUID> {

    /**
     * Find fees for a transaction
     */
    List<TransactionFee> findByTransactionId(UUID transactionId);

    /**
     * Find fees by type
     */
    List<TransactionFee> findByFeeType(String feeType);

    /**
     * Find fees created within date range
     */
    @Query("SELECT tf FROM TransactionFee tf WHERE tf.createdAt BETWEEN :startDate AND :endDate")
    List<TransactionFee> findByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    /**
     * Sum total fees by type in date range
     */
    @Query("SELECT COALESCE(SUM(tf.amount), 0) FROM TransactionFee tf " +
           "WHERE tf.feeType = :feeType AND tf.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal sumFeesByType(
        @Param("feeType") String feeType,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    /**
     * Sum all fees in date range
     */
    @Query("SELECT COALESCE(SUM(tf.amount), 0) FROM TransactionFee tf " +
           "WHERE tf.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal sumTotalFees(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    /**
     * Count fees by type
     */
    long countByFeeType(String feeType);
}
