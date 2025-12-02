package com.waqiti.accounting.repository;

import com.waqiti.accounting.domain.SettlementEntry;
import com.waqiti.accounting.domain.SettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Settlement Repository
 * Repository for merchant settlement entries
 */
@Repository
public interface SettlementRepository extends JpaRepository<SettlementEntry, String> {

    /**
     * Find settlements by merchant ID
     */
    List<SettlementEntry> findByMerchantIdOrderByCreatedAtDesc(String merchantId);

    /**
     * Find settlements by status
     */
    List<SettlementEntry> findByStatus(SettlementStatus status);

    /**
     * Find pending settlements for a date
     */
    @Query("SELECT s FROM SettlementEntry s WHERE s.status = 'PENDING' " +
           "AND s.settlementDate <= :date ORDER BY s.settlementDate ASC")
    List<SettlementEntry> findPendingSettlements(@Param("date") LocalDate date);

    /**
     * Find pending settlements with limit
     */
    @Query("SELECT s FROM SettlementEntry s WHERE s.status = 'PENDING' " +
           "AND s.settlementDate <= :date ORDER BY s.settlementDate ASC")
    List<SettlementEntry> findPendingSettlements(
        @Param("date") LocalDate date,
        @Param("limit") int limit);

    /**
     * Find settlements by transaction ID
     */
    Optional<SettlementEntry> findByTransactionId(String transactionId);

    /**
     * Find settlements by merchant and date range
     */
    @Query("SELECT s FROM SettlementEntry s WHERE s.merchantId = :merchantId " +
           "AND s.settlementDate BETWEEN :startDate AND :endDate " +
           "ORDER BY s.settlementDate DESC")
    List<SettlementEntry> findByMerchantAndDateRange(
        @Param("merchantId") String merchantId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    /**
     * Count pending settlements for merchant
     */
    long countByMerchantIdAndStatus(String merchantId, SettlementStatus status);

    /**
     * Check if settlement exists for transaction
     */
    boolean existsByTransactionId(String transactionId);
}
