package com.waqiti.compliance.repository;

import com.waqiti.compliance.domain.CustomerRiskProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for CustomerRiskProfile entities
 * Provides data access methods for customer risk management and AML compliance
 */
@Repository
public interface CustomerRiskProfileRepository extends JpaRepository<CustomerRiskProfile, UUID> {

    /**
     * Find risk profile by customer ID
     */
    Optional<CustomerRiskProfile> findByCustomerId(UUID customerId);

    /**
     * Find profiles by current risk level
     */
    List<CustomerRiskProfile> findByCurrentRiskLevel(CustomerRiskProfile.RiskLevel riskLevel);

    /**
     * Find profiles by KYC status
     */
    List<CustomerRiskProfile> findByKycStatus(String kycStatus);

    /**
     * Find profiles requiring review
     */
    @Query("SELECT crp FROM CustomerRiskProfile crp WHERE crp.nextReviewDate <= :date")
    List<CustomerRiskProfile> findRequiringReview(@Param("date") LocalDateTime date);

    /**
     * Find high-risk customers
     */
    @Query("SELECT crp FROM CustomerRiskProfile crp WHERE crp.currentRiskLevel = 'HIGH' " +
           "OR crp.currentRiskLevel = 'CRITICAL'")
    List<CustomerRiskProfile> findHighRiskCustomers();

    /**
     * Find profiles by country code
     */
    List<CustomerRiskProfile> findByCountryCode(String countryCode);

    /**
     * Find profiles by PEP status
     */
    List<CustomerRiskProfile> findByIsPep(boolean isPep);

    /**
     * Find profiles with sanctions matches
     */
    @Query("SELECT crp FROM CustomerRiskProfile crp WHERE crp.sanctionsScreeningResult = 'MATCH' " +
           "OR crp.sanctionsScreeningResult = 'POTENTIAL_MATCH'")
    List<CustomerRiskProfile> findWithSanctionsMatches();

    /**
     * Find profiles created within date range
     */
    List<CustomerRiskProfile> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find profiles last updated within date range
     */
    List<CustomerRiskProfile> findByLastUpdatedBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find profiles by enhanced due diligence requirement
     */
    List<CustomerRiskProfile> findByRequiresEnhancedDueDiligence(boolean requiresEdd);

    /**
     * Find profiles with ongoing monitoring
     */
    @Query("SELECT crp FROM CustomerRiskProfile crp WHERE crp.ongoingMonitoring = true " +
           "AND crp.isActive = true")
    List<CustomerRiskProfile> findWithOngoingMonitoring();

    /**
     * Find profiles by compliance officer
     */
    List<CustomerRiskProfile> findByAssignedComplianceOfficer(String complianceOfficer);

    /**
     * Find profiles with specific risk factors
     */
    @Query("SELECT crp FROM CustomerRiskProfile crp WHERE :riskFactor MEMBER OF crp.riskFactors")
    List<CustomerRiskProfile> findByRiskFactor(@Param("riskFactor") String riskFactor);

    /**
     * Find profiles with suspicious activity alerts
     */
    @Query("SELECT crp FROM CustomerRiskProfile crp WHERE crp.suspiciousActivityCount > 0")
    List<CustomerRiskProfile> findWithSuspiciousActivity();

    /**
     * Find profiles needing KYC refresh
     */
    @Query("SELECT crp FROM CustomerRiskProfile crp WHERE crp.kycExpiryDate <= :date " +
           "AND crp.isActive = true")
    List<CustomerRiskProfile> findNeedingKycRefresh(@Param("date") LocalDateTime date);

    /**
     * Count profiles by risk level
     */
    long countByCurrentRiskLevel(CustomerRiskProfile.RiskLevel riskLevel);

    /**
     * Count profiles by country
     */
    @Query("SELECT crp.countryCode, COUNT(crp) FROM CustomerRiskProfile crp " +
           "WHERE crp.isActive = true GROUP BY crp.countryCode")
    List<Object[]> countByCountry();

    /**
     * Find profiles with recent risk score changes
     */
    @Query("SELECT crp FROM CustomerRiskProfile crp WHERE crp.lastRiskScoreUpdate >= :since " +
           "AND ABS(crp.currentRiskScore - crp.previousRiskScore) > :threshold")
    List<CustomerRiskProfile> findWithRecentRiskChanges(@Param("since") LocalDateTime since,
                                                        @Param("threshold") double threshold);

    /**
     * Find profiles by industry/business type
     */
    List<CustomerRiskProfile> findByBusinessType(String businessType);

    /**
     * Find profiles with adverse media hits
     */
    @Query("SELECT crp FROM CustomerRiskProfile crp WHERE crp.adverseMediaHits > 0")
    List<CustomerRiskProfile> findWithAdverseMedia();

    /**
     * Find profiles requiring immediate attention
     */
    @Query("SELECT crp FROM CustomerRiskProfile crp WHERE " +
           "(crp.currentRiskLevel = 'CRITICAL' AND crp.lastReviewDate < :criticalReviewThreshold) " +
           "OR (crp.suspiciousActivityCount >= :suspiciousThreshold) " +
           "OR (crp.sanctionsScreeningResult = 'MATCH')")
    List<CustomerRiskProfile> findRequiringImmediateAttention(
            @Param("criticalReviewThreshold") LocalDateTime criticalReviewThreshold,
            @Param("suspiciousThreshold") int suspiciousThreshold);

    /**
     * Find profiles with specific compliance flags
     */
    @Query("SELECT crp FROM CustomerRiskProfile crp WHERE :flag MEMBER OF crp.complianceFlags")
    List<CustomerRiskProfile> findByComplianceFlag(@Param("flag") String flag);

    /**
     * Search profiles by multiple criteria
     */
    @Query("SELECT crp FROM CustomerRiskProfile crp WHERE " +
           "(:riskLevel IS NULL OR crp.currentRiskLevel = :riskLevel) AND " +
           "(:countryCode IS NULL OR crp.countryCode = :countryCode) AND " +
           "(:isPep IS NULL OR crp.isPep = :isPep) AND " +
           "(:requiresEdd IS NULL OR crp.requiresEnhancedDueDiligence = :requiresEdd) AND " +
           "(:startDate IS NULL OR crp.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR crp.createdAt <= :endDate)")
    Page<CustomerRiskProfile> searchByCriteria(
            @Param("riskLevel") CustomerRiskProfile.RiskLevel riskLevel,
            @Param("countryCode") String countryCode,
            @Param("isPep") Boolean isPep,
            @Param("requiresEdd") Boolean requiresEdd,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    /**
     * Find profiles with expired documentation
     */
    @Query("SELECT crp FROM CustomerRiskProfile crp WHERE " +
           "crp.identityDocumentExpiryDate <= :date " +
           "OR crp.kycExpiryDate <= :date " +
           "OR crp.addressDocumentExpiryDate <= :date")
    List<CustomerRiskProfile> findWithExpiredDocumentation(@Param("date") LocalDateTime date);

    /**
     * Find profiles by transaction volume risk
     */
    @Query("SELECT crp FROM CustomerRiskProfile crp WHERE crp.averageMonthlyVolume > :threshold " +
           "AND crp.currentRiskLevel != 'LOW'")
    List<CustomerRiskProfile> findHighVolumeCustomers(@Param("threshold") double threshold);

    /**
     * Find profiles with incomplete due diligence
     */
    @Query("SELECT crp FROM CustomerRiskProfile crp WHERE " +
           "crp.dueDiligenceStatus = 'INCOMPLETE' " +
           "OR crp.dueDiligenceStatus = 'PENDING' " +
           "OR (crp.requiresEnhancedDueDiligence = true AND crp.enhancedDueDiligenceStatus != 'COMPLETED')")
    List<CustomerRiskProfile> findWithIncompleteDueDiligence();

    /**
     * Find profiles for periodic review based on risk level
     */
    @Query("SELECT crp FROM CustomerRiskProfile crp WHERE " +
           "(crp.currentRiskLevel = 'HIGH' AND crp.lastReviewDate <= :highRiskThreshold) " +
           "OR (crp.currentRiskLevel = 'MEDIUM' AND crp.lastReviewDate <= :mediumRiskThreshold) " +
           "OR (crp.currentRiskLevel = 'LOW' AND crp.lastReviewDate <= :lowRiskThreshold)")
    List<CustomerRiskProfile> findForPeriodicReview(
            @Param("highRiskThreshold") LocalDateTime highRiskThreshold,
            @Param("mediumRiskThreshold") LocalDateTime mediumRiskThreshold,
            @Param("lowRiskThreshold") LocalDateTime lowRiskThreshold);

    /**
     * Update risk score for customer
     */
    @Query("UPDATE CustomerRiskProfile crp SET " +
           "crp.previousRiskScore = crp.currentRiskScore, " +
           "crp.currentRiskScore = :newScore, " +
           "crp.lastRiskScoreUpdate = :updateTime " +
           "WHERE crp.customerId = :customerId")
    void updateRiskScore(@Param("customerId") UUID customerId,
                        @Param("newScore") double newScore,
                        @Param("updateTime") LocalDateTime updateTime);

    /**
     * Update KYC status for customer
     */
    @Query("UPDATE CustomerRiskProfile crp SET " +
           "crp.kycStatus = :status, " +
           "crp.kycCompletedDate = :completedDate, " +
           "crp.kycExpiryDate = :expiryDate, " +
           "crp.lastUpdated = :updateTime " +
           "WHERE crp.customerId = :customerId")
    void updateKycStatus(@Param("customerId") UUID customerId,
                        @Param("status") String status,
                        @Param("completedDate") LocalDateTime completedDate,
                        @Param("expiryDate") LocalDateTime expiryDate,
                        @Param("updateTime") LocalDateTime updateTime);

    /**
     * Archive old inactive profiles (for data retention)
     */
    @Query("UPDATE CustomerRiskProfile crp SET crp.isActive = false, crp.archivedAt = :archiveTime " +
           "WHERE crp.isActive = true " +
           "AND crp.lastUpdated < :cutoffDate " +
           "AND crp.currentRiskLevel = 'LOW' " +
           "AND crp.suspiciousActivityCount = 0")
    void archiveOldInactiveProfiles(@Param("cutoffDate") LocalDateTime cutoffDate,
                                   @Param("archiveTime") LocalDateTime archiveTime);
}