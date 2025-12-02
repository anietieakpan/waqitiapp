package com.waqiti.common.security.awareness.repository;

import com.waqiti.common.security.awareness.domain.AssessmentResult;

import com.waqiti.common.security.awareness.model.*;
import com.waqiti.common.security.awareness.dto.*;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for AssessmentResult entities
 *
 * @author Waqiti Platform Team
 */
@Repository
public interface AssessmentResultRepository extends JpaRepository<AssessmentResult, UUID> {

    /**
     * Find results by assessment
     */
    List<AssessmentResult> findByAssessmentId(UUID assessmentId);

    /**
     * Find results by employee
     */
    List<AssessmentResult> findByEmployeeId(UUID employeeId);

    /**
     * Find result by assessment and employee
     */
    Optional<AssessmentResult> findByAssessmentIdAndEmployeeId(UUID assessmentId, UUID employeeId);

    /**
     * Count attempts for employee on assessment
     */
    @Query("SELECT COUNT(r) FROM AssessmentResult r WHERE r.assessment.id = :assessmentId " +
            "AND r.employee.id = :employeeId")
    Long countAttemptsByAssessmentAndEmployee(
            @Param("assessmentId") UUID assessmentId,
            @Param("employeeId") UUID employeeId
    );

    /**
     * Get average score for employee
     */
    @Query("SELECT AVG(r.score) FROM AssessmentResult r WHERE r.employee.id = :employeeId " +
            "AND r.passed = true")
    Optional<BigDecimal> getAverageScoreByEmployeeId(@Param("employeeId") UUID employeeId);

    /**
     * Count completed results by assessment
     */
    long countByAssessment_IdAndCompletedAtIsNotNull(UUID assessmentId);

    /**
     * Count passed results by assessment
     */
    long countByAssessment_IdAndPassedTrue(UUID assessmentId);

    /**
     * Count failed results by assessment
     */
    long countByAssessment_IdAndPassedFalse(UUID assessmentId);

    /**
     * Calculate average score by assessment
     */
    @Query("SELECT AVG(r.score) FROM AssessmentResult r WHERE r.assessment.id = :assessmentId " +
            "AND r.completedAt IS NOT NULL")
    BigDecimal calculateAverageScoreByAssessmentId(@Param("assessmentId") UUID assessmentId);
}