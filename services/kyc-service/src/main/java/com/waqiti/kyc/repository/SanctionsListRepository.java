package com.waqiti.kyc.repository;

import com.waqiti.kyc.entity.SanctionsListEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for sanctions list data (OFAC, UN, EU, UK, etc.)
 * 
 * CRITICAL: This repository supports financial crime prevention and regulatory compliance.
 * Data sources include:
 * - OFAC SDN (Specially Designated Nationals) List
 * - UN Security Council Consolidated List
 * - EU Consolidated Financial Sanctions List
 * - UK HM Treasury Financial Sanctions List
 * 
 * @author Waqiti Compliance Team
 * @version 2.0.0
 * @since 2025-01-27
 */
@Repository
public interface SanctionsListRepository extends JpaRepository<SanctionsListEntry, UUID> {
    
    /**
     * Check if exact name match exists in OFAC SDN list
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM SanctionsListEntry s " +
           "WHERE LOWER(s.fullName) = LOWER(:fullName) " +
           "AND s.listType = 'OFAC' " +
           "AND s.active = true " +
           "AND (:dateOfBirth IS NULL OR s.dateOfBirth = :dateOfBirth) " +
           "AND (:nationality IS NULL OR s.nationality = :nationality)")
    boolean existsInOFACList(@Param("fullName") String fullName,
                             @Param("dateOfBirth") LocalDate dateOfBirth,
                             @Param("nationality") String nationality);
    
    /**
     * Check if exact name match exists in UN sanctions list
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM SanctionsListEntry s " +
           "WHERE LOWER(s.fullName) = LOWER(:fullName) " +
           "AND s.listType = 'UN' " +
           "AND s.active = true " +
           "AND (:dateOfBirth IS NULL OR s.dateOfBirth = :dateOfBirth) " +
           "AND (:nationality IS NULL OR s.nationality = :nationality)")
    boolean existsInUNList(@Param("fullName") String fullName,
                           @Param("dateOfBirth") LocalDate dateOfBirth,
                           @Param("nationality") String nationality);
    
    /**
     * Check if exact name match exists in EU sanctions list
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM SanctionsListEntry s " +
           "WHERE LOWER(s.fullName) = LOWER(:fullName) " +
           "AND s.listType = 'EU' " +
           "AND s.active = true " +
           "AND (:dateOfBirth IS NULL OR s.dateOfBirth = :dateOfBirth) " +
           "AND (:nationality IS NULL OR s.nationality = :nationality)")
    boolean existsInEUList(@Param("fullName") String fullName,
                           @Param("dateOfBirth") LocalDate dateOfBirth,
                           @Param("nationality") String nationality);
    
    /**
     * Check if exact name match exists in UK sanctions list
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM SanctionsListEntry s " +
           "WHERE LOWER(s.fullName) = LOWER(:fullName) " +
           "AND s.listType = 'UK_HMT' " +
           "AND s.active = true " +
           "AND (:dateOfBirth IS NULL OR s.dateOfBirth = :dateOfBirth) " +
           "AND (:nationality IS NULL OR s.nationality = :nationality)")
    boolean existsInUKList(@Param("fullName") String fullName,
                           @Param("dateOfBirth") LocalDate dateOfBirth,
                           @Param("nationality") String nationality);
    
    /**
     * Fuzzy matching for similar names using similarity threshold
     * Uses PostgreSQL similarity() function or Levenshtein distance
     */
    @Query(value = "SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END " +
                   "FROM sanctions_list_entries " +
                   "WHERE active = true " +
                   "AND similarity(LOWER(full_name), LOWER(:fullName)) >= :threshold",
           nativeQuery = true)
    boolean fuzzyMatchExists(@Param("fullName") String fullName,
                            @Param("threshold") double threshold);
    
    /**
     * Find all fuzzy matches above threshold for manual review
     */
    @Query(value = "SELECT * FROM sanctions_list_entries " +
                   "WHERE active = true " +
                   "AND similarity(LOWER(full_name), LOWER(:fullName)) >= :threshold " +
                   "ORDER BY similarity(LOWER(full_name), LOWER(:fullName)) DESC " +
                   "LIMIT 10",
           nativeQuery = true)
    List<SanctionsListEntry> findFuzzyMatches(@Param("fullName") String fullName,
                                              @Param("threshold") double threshold);
    
    /**
     * Find exact match across all sanctions lists
     */
    @Query("SELECT s FROM SanctionsListEntry s " +
           "WHERE LOWER(s.fullName) = LOWER(:fullName) " +
           "AND s.active = true " +
           "ORDER BY s.riskScore DESC")
    List<SanctionsListEntry> findAllExactMatches(@Param("fullName") String fullName);
    
    /**
     * Find by passport number (critical for travel document checks)
     */
    Optional<SanctionsListEntry> findByPassportNumberAndActive(String passportNumber, boolean active);
    
    /**
     * Find by national ID number
     */
    Optional<SanctionsListEntry> findByNationalIdAndActive(String nationalId, boolean active);
    
    /**
     * Find by entity/company registration number
     */
    List<SanctionsListEntry> findByCompanyRegistrationNumberAndActive(String registrationNumber, boolean active);
    
    /**
     * Find all entries from specific list type
     */
    List<SanctionsListEntry> findByListTypeAndActive(String listType, boolean active);
    
    /**
     * Count active sanctions by nationality (for risk assessment)
     */
    @Query("SELECT COUNT(s) FROM SanctionsListEntry s " +
           "WHERE s.nationality = :nationality " +
           "AND s.active = true")
    long countByNationalityAndActive(@Param("nationality") String nationality);
    
    /**
     * Find entries updated after a specific date (for incremental updates)
     */
    @Query("SELECT s FROM SanctionsListEntry s " +
           "WHERE s.lastUpdated > :since " +
           "ORDER BY s.lastUpdated DESC")
    List<SanctionsListEntry> findUpdatedSince(@Param("since") LocalDate since);
    
    /**
     * Get statistics for monitoring dashboard
     */
    @Query("SELECT s.listType, COUNT(s), MAX(s.lastUpdated) " +
           "FROM SanctionsListEntry s " +
           "WHERE s.active = true " +
           "GROUP BY s.listType")
    List<Object[]> getSanctionsStatistics();
}