package com.waqiti.billingorchestrator.repository;

import com.waqiti.billingorchestrator.entity.TaxRate;
import com.waqiti.billingorchestrator.entity.TaxRate.TaxType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for TaxRate entities
 *
 * @author Waqiti Billing Team
 * @since 1.0
 */
@Repository
public interface TaxRateRepository extends JpaRepository<TaxRate, UUID> {

    /**
     * Find active tax rates for jurisdiction
     */
    @Query("SELECT t FROM TaxRate t WHERE t.jurisdiction = :jurisdiction " +
           "AND t.active = TRUE AND t.effectiveDate <= :now " +
           "AND (t.expiryDate IS NULL OR t.expiryDate > :now) " +
           "ORDER BY t.effectiveDate DESC")
    List<TaxRate> findActiveByJurisdiction(
        @Param("jurisdiction") String jurisdiction,
        @Param("now") LocalDate now
    );

    /**
     * Find tax rate by jurisdiction and type
     */
    @Query("SELECT t FROM TaxRate t WHERE t.jurisdiction = :jurisdiction " +
           "AND t.taxType = :taxType AND t.active = TRUE " +
           "AND t.effectiveDate <= :now " +
           "AND (t.expiryDate IS NULL OR t.expiryDate > :now)")
    Optional<TaxRate> findByJurisdictionAndType(
        @Param("jurisdiction") String jurisdiction,
        @Param("taxType") TaxType taxType,
        @Param("now") LocalDate now
    );

    /**
     * Find all tax rates for location (country, state, city)
     */
    @Query("SELECT t FROM TaxRate t WHERE " +
           "(t.countryCode = :countryCode OR :countryCode IS NULL) AND " +
           "(t.stateProvince = :state OR :state IS NULL) AND " +
           "(t.city = :city OR :city IS NULL) AND " +
           "t.active = TRUE AND t.effectiveDate <= :now " +
           "AND (t.expiryDate IS NULL OR t.expiryDate > :now)")
    List<TaxRate> findByLocation(
        @Param("countryCode") String countryCode,
        @Param("state") String state,
        @Param("city") String city,
        @Param("now") LocalDate now
    );
}
