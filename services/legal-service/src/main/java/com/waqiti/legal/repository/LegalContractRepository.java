package com.waqiti.legal.repository;

import com.waqiti.legal.domain.LegalContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legal Contract Repository
 *
 * Complete data access layer for LegalContract entities with custom query methods
 * Supports contract lifecycle, renewal tracking, and compliance monitoring
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Repository
public interface LegalContractRepository extends JpaRepository<LegalContract, UUID> {

    /**
     * Find contract by contract ID
     */
    Optional<LegalContract> findByContractId(String contractId);

    /**
     * Find contracts by type
     */
    List<LegalContract> findByContractType(LegalContract.ContractType contractType);

    /**
     * Find contracts by status
     */
    List<LegalContract> findByContractStatus(LegalContract.ContractStatus contractStatus);

    /**
     * Find all active contracts
     */
    @Query("SELECT c FROM LegalContract c WHERE c.contractStatus = 'ACTIVE' " +
           "AND (c.contractEndDate IS NULL OR c.contractEndDate > :currentDate) " +
           "AND (c.contractStartDate IS NULL OR c.contractStartDate <= :currentDate)")
    List<LegalContract> findActiveContracts(@Param("currentDate") LocalDate currentDate);

    /**
     * Find contracts by primary party
     */
    List<LegalContract> findByPrimaryPartyId(String primaryPartyId);

    /**
     * Find contracts by counterparty
     */
    List<LegalContract> findByCounterpartyId(String counterpartyId);

    /**
     * Find contracts by counterparty name
     */
    List<LegalContract> findByCounterpartyName(String counterpartyName);

    /**
     * Find contracts by counterparty type
     */
    List<LegalContract> findByCounterpartyType(String counterpartyType);

    /**
     * Find expired contracts
     */
    @Query("SELECT c FROM LegalContract c WHERE c.contractEndDate IS NOT NULL " +
           "AND c.contractEndDate < :currentDate " +
           "AND c.contractStatus NOT IN ('EXPIRED', 'TERMINATED', 'RENEWED')")
    List<LegalContract> findExpiredContracts(@Param("currentDate") LocalDate currentDate);

    /**
     * Find contracts approaching expiration (within specified days)
     */
    @Query("SELECT c FROM LegalContract c WHERE c.contractEndDate BETWEEN :startDate AND :endDate " +
           "AND c.contractStatus = 'ACTIVE' " +
           "ORDER BY c.contractEndDate ASC")
    List<LegalContract> findContractsApproachingExpiration(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find contracts by jurisdiction
     */
    List<LegalContract> findByJurisdiction(String jurisdiction);

    /**
     * Find contracts by governing law
     */
    List<LegalContract> findByGoverningLaw(String governingLaw);

    /**
     * Find fully signed contracts
     */
    List<LegalContract> findBySignedTrue();

    /**
     * Find contracts pending signature
     */
    @Query("SELECT c FROM LegalContract c WHERE c.signed = false " +
           "AND c.contractStatus IN ('PENDING_SIGNATURE', 'PARTIALLY_SIGNED')")
    List<LegalContract> findContractsPendingSignature();

    /**
     * Find notarized contracts
     */
    List<LegalContract> findByNotarizedTrue();

    /**
     * Find contracts by compliance status
     */
    List<LegalContract> findByComplianceStatus(LegalContract.ComplianceStatus complianceStatus);

    /**
     * Find non-compliant contracts
     */
    @Query("SELECT c FROM LegalContract c WHERE c.complianceStatus IN ('NON_COMPLIANT', 'REMEDIATION_REQUIRED') " +
           "AND c.contractStatus = 'ACTIVE'")
    List<LegalContract> findNonCompliantContracts();

    /**
     * Find contracts by risk rating
     */
    List<LegalContract> findByRiskRating(LegalContract.RiskRating riskRating);

    /**
     * Find high and critical risk contracts
     */
    @Query("SELECT c FROM LegalContract c WHERE c.riskRating IN ('HIGH', 'CRITICAL') " +
           "AND c.contractStatus = 'ACTIVE' " +
           "ORDER BY c.riskRating DESC, c.contractValue DESC")
    List<LegalContract> findHighRiskContracts();

    /**
     * Find contracts by contract manager
     */
    List<LegalContract> findByContractManager(String contractManager);

    /**
     * Find contracts created by user
     */
    List<LegalContract> findByCreatedBy(String createdBy);

    /**
     * Find contracts requiring review
     */
    @Query("SELECT c FROM LegalContract c WHERE c.nextReviewDate IS NOT NULL " +
           "AND c.nextReviewDate <= :thresholdDate " +
           "AND c.contractStatus = 'ACTIVE'")
    List<LegalContract> findContractsRequiringReview(@Param("thresholdDate") LocalDate thresholdDate);

    /**
     * Find contracts with value greater than specified amount
     */
    @Query("SELECT c FROM LegalContract c WHERE c.contractValue >= :minValue " +
           "AND c.contractStatus = 'ACTIVE' " +
           "ORDER BY c.contractValue DESC")
    List<LegalContract> findContractsWithValueGreaterThan(@Param("minValue") BigDecimal minValue);

    /**
     * Find contracts starting within date range
     */
    @Query("SELECT c FROM LegalContract c WHERE c.contractStartDate BETWEEN :startDate AND :endDate " +
           "ORDER BY c.contractStartDate ASC")
    List<LegalContract> findByContractStartDateBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find contracts ending within date range
     */
    @Query("SELECT c FROM LegalContract c WHERE c.contractEndDate BETWEEN :startDate AND :endDate " +
           "ORDER BY c.contractEndDate ASC")
    List<LegalContract> findByContractEndDateBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find contracts by payment terms
     */
    List<LegalContract> findByPaymentTerms(String paymentTerms);

    /**
     * Find contracts by dispute resolution method
     */
    List<LegalContract> findByDisputeResolutionMethod(String disputeResolutionMethod);

    /**
     * Find renewed contracts
     */
    @Query("SELECT c FROM LegalContract c WHERE c.contractStatus = 'RENEWED'")
    List<LegalContract> findRenewedContracts();

    /**
     * Find contracts under negotiation
     */
    List<LegalContract> findByContractStatusIn(List<LegalContract.ContractStatus> statuses);

    /**
     * Count active contracts
     */
    @Query("SELECT COUNT(c) FROM LegalContract c WHERE c.contractStatus = 'ACTIVE' " +
           "AND (c.contractEndDate IS NULL OR c.contractEndDate > :currentDate)")
    long countActiveContracts(@Param("currentDate") LocalDate currentDate);

    /**
     * Count contracts by type
     */
    long countByContractType(LegalContract.ContractType contractType);

    /**
     * Count contracts by status
     */
    long countByContractStatus(LegalContract.ContractStatus contractStatus);

    /**
     * Calculate total active contract value
     */
    @Query("SELECT COALESCE(SUM(c.contractValue), 0) FROM LegalContract c " +
           "WHERE c.contractStatus = 'ACTIVE' " +
           "AND (c.contractEndDate IS NULL OR c.contractEndDate > :currentDate)")
    BigDecimal calculateTotalActiveContractValue(@Param("currentDate") LocalDate currentDate);

    /**
     * Calculate total contract value by counterparty
     */
    @Query("SELECT COALESCE(SUM(c.contractValue), 0) FROM LegalContract c " +
           "WHERE c.counterpartyId = :counterpartyId " +
           "AND c.contractStatus = 'ACTIVE'")
    BigDecimal calculateTotalValueByCounterparty(@Param("counterpartyId") String counterpartyId);

    /**
     * Check if contract ID exists
     */
    boolean existsByContractId(String contractId);

    /**
     * Check if active contract exists with counterparty
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM LegalContract c " +
           "WHERE c.counterpartyId = :counterpartyId " +
           "AND c.contractStatus = 'ACTIVE'")
    boolean hasActiveContractWithCounterparty(@Param("counterpartyId") String counterpartyId);

    /**
     * Find contracts requiring immediate attention
     */
    @Query("SELECT c FROM LegalContract c WHERE " +
           "(c.contractStatus = 'ACTIVE' AND c.contractEndDate BETWEEN :today AND :thresholdDate) " +
           "OR (c.complianceStatus IN ('NON_COMPLIANT', 'REMEDIATION_REQUIRED')) " +
           "OR (c.riskRating IN ('HIGH', 'CRITICAL')) " +
           "OR (c.nextReviewDate IS NOT NULL AND c.nextReviewDate <= :today) " +
           "ORDER BY c.contractEndDate ASC, c.riskRating DESC")
    List<LegalContract> findContractsRequiringAttention(
        @Param("today") LocalDate today,
        @Param("thresholdDate") LocalDate thresholdDate
    );

    /**
     * Search contracts by name (case-insensitive)
     */
    @Query("SELECT c FROM LegalContract c WHERE LOWER(c.contractName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<LegalContract> searchByContractName(@Param("searchTerm") String searchTerm);

    /**
     * Find contracts with specific currency
     */
    List<LegalContract> findByCurrencyCode(String currencyCode);

    /**
     * Find contracts executed within date range
     */
    @Query("SELECT c FROM LegalContract c WHERE c.executedDate BETWEEN :startDate AND :endDate " +
           "ORDER BY c.executedDate DESC")
    List<LegalContract> findByExecutedDateBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}
