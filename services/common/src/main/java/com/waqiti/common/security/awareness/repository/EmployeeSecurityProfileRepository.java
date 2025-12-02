package com.waqiti.common.security.awareness.repository;

import com.waqiti.common.security.awareness.domain.EmployeeSecurityProfile;

import com.waqiti.common.security.awareness.model.*;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for EmployeeSecurityProfile entities
 *
 * @author Waqiti Platform Team
 */
@Repository
public interface EmployeeSecurityProfileRepository extends JpaRepository<EmployeeSecurityProfile, UUID> {

    /**
     * Find profile by employee ID
     */
    Optional<EmployeeSecurityProfile> findByEmployeeId(UUID employeeId);

    /**
     * Find profiles by risk level
     */
    List<EmployeeSecurityProfile> findByRiskLevel(EmployeeSecurityProfile.RiskLevel riskLevel);

    /**
     * Find high-risk profiles
     */
    @Query("SELECT p FROM EmployeeSecurityProfile p WHERE p.riskLevel IN ('HIGH', 'CRITICAL')")
    List<EmployeeSecurityProfile> findHighRiskProfiles();

    /**
     * Find non-compliant profiles
     */
    @Query("SELECT p FROM EmployeeSecurityProfile p WHERE p.complianceStatus != 'COMPLIANT'")
    List<EmployeeSecurityProfile> findNonCompliantProfiles();

    /**
     * Find profiles by next training due date between range
     */
    List<EmployeeSecurityProfile> findByNextTrainingDueAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Find profiles where training is overdue
     */
    List<EmployeeSecurityProfile> findByNextTrainingDueAtBefore(LocalDateTime date);

    /**
     * Find profiles by risk level in list
     */
    List<EmployeeSecurityProfile> findByRiskLevelIn(List<EmployeeSecurityProfile.RiskLevel> riskLevels);

    /**
     * Count profiles with compliance >= threshold
     */
    long countByCompliancePercentageGreaterThanEqual(BigDecimal threshold);

    /**
     * Count profiles with training overdue
     */
    long countByNextTrainingDueAtBefore(LocalDateTime date);
}