package com.waqiti.kyc.repository;

import com.waqiti.kyc.entity.PEPEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Politically Exposed Persons (PEP) database
 * 
 * PEPs are individuals who hold or have held prominent public positions.
 * Enhanced Due Diligence (EDD) is required for PEPs under AML regulations.
 * 
 * Categories:
 * - Current PEPs: Individuals currently holding positions
 * - Former PEPs: Individuals who held positions (monitored for 1-2 years after leaving)
 * - RCA (Relatives and Close Associates): Family members and known associates of PEPs
 * 
 * @author Waqiti Compliance Team
 * @version 2.0.0
 * @since 2025-01-27
 */
@Repository
public interface PEPDatabaseRepository extends JpaRepository<PEPEntry, UUID> {
    
    /**
     * Check if individual is a current Politically Exposed Person
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM PEPEntry p " +
           "WHERE LOWER(p.fullName) = LOWER(:fullName) " +
           "AND p.pepStatus = 'CURRENT' " +
           "AND p.active = true " +
           "AND (:nationality IS NULL OR p.nationality = :nationality)")
    boolean isPoliticallyExposed(@Param("fullName") String fullName,
                                 @Param("nationality") String nationality);
    
    /**
     * Check if individual is a Relative or Close Associate (RCA) of a PEP
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM PEPEntry p " +
           "WHERE LOWER(p.fullName) = LOWER(:fullName) " +
           "AND p.pepCategory = 'RCA' " +
           "AND p.active = true " +
           "AND (:nationality IS NULL OR p.nationality = :nationality)")
    boolean isRelativeOrCloseAssociate(@Param("fullName") String fullName,
                                       @Param("nationality") String nationality);
    
    /**
     * Check if individual is a former PEP (still requires enhanced monitoring)
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM PEPEntry p " +
           "WHERE LOWER(p.fullName) = LOWER(:fullName) " +
           "AND p.pepStatus = 'FORMER' " +
           "AND p.active = true " +
           "AND p.exitDate > :monitoringCutoff " +
           "AND (:nationality IS NULL OR p.nationality = :nationality)")
    boolean isFormerPEP(@Param("fullName") String fullName,
                       @Param("nationality") String nationality,
                       @Param("monitoringCutoff") LocalDate monitoringCutoff);
    
    /**
     * Find all PEP entries for an individual (exact match)
     */
    @Query("SELECT p FROM PEPEntry p " +
           "WHERE LOWER(p.fullName) = LOWER(:fullName) " +
           "AND p.active = true " +
           "ORDER BY p.riskLevel DESC, p.entryDate DESC")
    List<PEPEntry> findAllPEPEntries(@Param("fullName") String fullName);
    
    /**
     * Find PEP entry by passport number
     */
    Optional<PEPEntry> findByPassportNumberAndActive(String passportNumber, boolean active);
    
    /**
     * Find PEP entry by national ID
     */
    Optional<PEPEntry> findByNationalIdAndActive(String nationalId, boolean active);
    
    /**
     * Find PEPs by position type
     */
    @Query("SELECT p FROM PEPEntry p " +
           "WHERE p.positionType = :positionType " +
           "AND p.pepStatus = 'CURRENT' " +
           "AND p.active = true " +
           "ORDER BY p.riskLevel DESC")
    List<PEPEntry> findByPositionType(@Param("positionType") String positionType);
    
    /**
     * Find high-risk PEPs by country
     */
    @Query("SELECT p FROM PEPEntry p " +
           "WHERE p.country = :country " +
           "AND p.riskLevel IN ('HIGH', 'CRITICAL') " +
           "AND p.active = true " +
           "ORDER BY p.riskLevel DESC, p.fullName")
    List<PEPEntry> findHighRiskPEPsByCountry(@Param("country") String country);
    
    /**
     * Fuzzy matching for PEP names
     */
    @Query(value = "SELECT * FROM pep_entries " +
                   "WHERE active = true " +
                   "AND similarity(LOWER(full_name), LOWER(:fullName)) >= :threshold " +
                   "ORDER BY similarity(LOWER(full_name), LOWER(:fullName)) DESC " +
                   "LIMIT 10",
           nativeQuery = true)
    List<PEPEntry> findFuzzyMatches(@Param("fullName") String fullName,
                                    @Param("threshold") double threshold);
    
    /**
     * Count PEPs by risk level (for monitoring dashboard)
     */
    @Query("SELECT p.riskLevel, COUNT(p) FROM PEPEntry p " +
           "WHERE p.active = true " +
           "AND p.pepStatus = 'CURRENT' " +
           "GROUP BY p.riskLevel " +
           "ORDER BY p.riskLevel DESC")
    List<Object[]> countByRiskLevel();
    
    /**
     * Find PEPs requiring periodic review
     */
    @Query("SELECT p FROM PEPEntry p " +
           "WHERE p.active = true " +
           "AND p.lastReviewDate < :reviewCutoff " +
           "ORDER BY p.lastReviewDate ASC")
    List<PEPEntry> findRequiringReview(@Param("reviewCutoff") LocalDate reviewCutoff);
    
    /**
     * Find former PEPs outside monitoring period (can be archived)
     */
    @Query("SELECT p FROM PEPEntry p " +
           "WHERE p.pepStatus = 'FORMER' " +
           "AND p.exitDate < :archiveCutoff " +
           "AND p.active = true")
    List<PEPEntry> findFormerPEPsForArchival(@Param("archiveCutoff") LocalDate archiveCutoff);
}