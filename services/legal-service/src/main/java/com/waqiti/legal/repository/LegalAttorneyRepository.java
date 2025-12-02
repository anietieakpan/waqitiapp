package com.waqiti.legal.repository;

import com.waqiti.legal.domain.LegalAttorney;
import com.waqiti.legal.service.LegalOrderAssignmentService.SkillLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Legal Attorney entities
 *
 * @author Waqiti Legal Technology Team
 * @version 1.0.0
 * @since 2025-10-23
 */
@Repository
public interface LegalAttorneyRepository extends JpaRepository<LegalAttorney, String> {

    /**
     * Find all active attorneys
     */
    List<LegalAttorney> findByActiveTrue();

    /**
     * Find attorneys by skill level
     */
    List<LegalAttorney> findBySkillLevelAndActiveTrue(SkillLevel skillLevel);

    /**
     * Find attorneys with availability (not at capacity)
     */
    @Query("SELECT a FROM LegalAttorney a WHERE a.active = true AND a.activeWorkloadCount < a.maxWorkloadCapacity")
    List<LegalAttorney> findAvailableAttorneys();

    /**
     * Find attorneys by specialty
     */
    @Query("SELECT a FROM LegalAttorney a JOIN a.specialties s WHERE s = :specialty AND a.active = true")
    List<LegalAttorney> findBySpecialty(@Param("specialty") String specialty);

    /**
     * Find attorney by attorney ID
     */
    Optional<LegalAttorney> findByAttorneyId(String attorneyId);

    /**
     * Find attorneys by email
     */
    Optional<LegalAttorney> findByEmail(String email);

    /**
     * Get workload statistics
     */
    @Query("SELECT AVG(a.activeWorkloadCount) FROM LegalAttorney a WHERE a.active = true")
    Double getAverageWorkload();

    /**
     * Find overloaded attorneys (at or above capacity)
     */
    @Query("SELECT a FROM LegalAttorney a WHERE a.active = true AND a.activeWorkloadCount >= a.maxWorkloadCapacity")
    List<LegalAttorney> findOverloadedAttorneys();
}
