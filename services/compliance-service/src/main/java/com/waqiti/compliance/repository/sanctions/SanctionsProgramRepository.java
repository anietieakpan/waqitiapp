package com.waqiti.compliance.repository.sanctions;

import com.waqiti.compliance.model.sanctions.SanctionsProgram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Sanctions Programs
 *
 * Provides data access for sanctions programs from OFAC, EU, and UN.
 * Examples: UKRAINE-EO13661, IRAN, SYRIA, TERRORISM, CYBER2
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2025-11-19
 */
@Repository
public interface SanctionsProgramRepository extends JpaRepository<SanctionsProgram, UUID> {

    /**
     * Find program by unique program code
     *
     * @param programCode Program code (e.g., "IRAN", "UKRAINE-EO13661")
     * @return Optional program
     */
    Optional<SanctionsProgram> findByProgramCode(String programCode);

    /**
     * Find all programs by jurisdiction
     *
     * @param jurisdiction US, EU, UN
     * @return List of programs
     */
    List<SanctionsProgram> findByJurisdiction(String jurisdiction);

    /**
     * Find all active programs
     *
     * @return List of active programs
     */
    @Query("SELECT p FROM SanctionsProgram p WHERE p.isActive = true")
    List<SanctionsProgram> findAllActive();

    /**
     * Find active programs by jurisdiction
     *
     * @param jurisdiction US, EU, UN
     * @return List of active programs
     */
    @Query("SELECT p FROM SanctionsProgram p " +
           "WHERE p.jurisdiction = :jurisdiction " +
           "AND p.isActive = true")
    List<SanctionsProgram> findActiveByJurisdiction(@Param("jurisdiction") String jurisdiction);

    /**
     * Find programs by program type
     *
     * @param programType Program type
     * @return List of programs
     */
    List<SanctionsProgram> findByProgramType(String programType);

    /**
     * Find programs targeting a specific country
     *
     * @param countryCode ISO country code
     * @return List of programs
     */
    @Query("SELECT p FROM SanctionsProgram p " +
           "WHERE p.targetCountries LIKE %:countryCode% " +
           "AND p.isActive = true")
    List<SanctionsProgram> findProgramsTargetingCountry(@Param("countryCode") String countryCode);

    /**
     * Find programs by executive order
     *
     * @param executiveOrder Executive order reference
     * @return List of programs
     */
    List<SanctionsProgram> findByExecutiveOrder(String executiveOrder);

    /**
     * Count programs by jurisdiction
     *
     * @param jurisdiction US, EU, UN
     * @return Count of programs
     */
    @Query("SELECT COUNT(p) FROM SanctionsProgram p " +
           "WHERE p.jurisdiction = :jurisdiction " +
           "AND p.isActive = true")
    long countActiveByJurisdiction(@Param("jurisdiction") String jurisdiction);

    /**
     * Search programs by name
     *
     * @param name Program name or partial name
     * @return List of matching programs
     */
    @Query("SELECT p FROM SanctionsProgram p " +
           "WHERE LOWER(p.programName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<SanctionsProgram> searchByProgramName(@Param("name") String name);

    /**
     * Find programs by issuing authority
     *
     * @param authority Issuing authority
     * @return List of programs
     */
    List<SanctionsProgram> findByIssuingAuthority(String authority);
}
