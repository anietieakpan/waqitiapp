package com.waqiti.accounting.repository;

import com.waqiti.accounting.domain.TaxCalculation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Tax Calculation Repository
 * Repository for tax calculation records
 */
@Repository
public interface TaxCalculationRepository extends JpaRepository<TaxCalculation, UUID> {

    /**
     * Find tax calculations for a transaction
     */
    List<TaxCalculation> findByTransactionId(UUID transactionId);

    /**
     * Find tax calculations by type
     */
    List<TaxCalculation> findByTaxType(String taxType);

    /**
     * Find tax calculations by jurisdiction
     */
    List<TaxCalculation> findByJurisdiction(String jurisdiction);

    /**
     * Find tax calculations within date range
     */
    @Query("SELECT tc FROM TaxCalculation tc WHERE tc.createdAt BETWEEN :startDate AND :endDate")
    List<TaxCalculation> findByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    /**
     * Sum total taxes by type in date range
     */
    @Query("SELECT COALESCE(SUM(tc.taxAmount), 0) FROM TaxCalculation tc " +
           "WHERE tc.taxType = :taxType AND tc.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal sumTaxesByType(
        @Param("taxType") String taxType,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    /**
     * Sum all taxes in date range
     */
    @Query("SELECT COALESCE(SUM(tc.taxAmount), 0) FROM TaxCalculation tc " +
           "WHERE tc.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal sumTotalTaxes(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    /**
     * Sum taxes by jurisdiction in date range
     */
    @Query("SELECT COALESCE(SUM(tc.taxAmount), 0) FROM TaxCalculation tc " +
           "WHERE tc.jurisdiction = :jurisdiction AND tc.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal sumTaxesByJurisdiction(
        @Param("jurisdiction") String jurisdiction,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    /**
     * Count calculations by tax type
     */
    long countByTaxType(String taxType);
}
