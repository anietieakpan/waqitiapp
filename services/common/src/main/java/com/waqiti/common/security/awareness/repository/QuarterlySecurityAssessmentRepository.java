package com.waqiti.common.security.awareness.repository;

import com.waqiti.common.security.awareness.domain.QuarterlySecurityAssessment;

import com.waqiti.common.security.awareness.dto.*;
import com.waqiti.common.security.awareness.model.*;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for QuarterlySecurityAssessment entities
 *
 * @author Waqiti Platform Team
 */
@Repository
public interface QuarterlySecurityAssessmentRepository extends JpaRepository<QuarterlySecurityAssessment, UUID> {

    /**
     * Find assessment by quarter and year
     */
    Optional<QuarterlySecurityAssessment> findByQuarterAndYear(Integer quarter, Integer year);

    /**
     * Find published assessments
     */
    List<QuarterlySecurityAssessment> findByStatus(QuarterlySecurityAssessment.AssessmentStatus status);

    /**
     * Find currently available assessments
     */
    @Query("SELECT a FROM QuarterlySecurityAssessment a WHERE a.status = 'PUBLISHED' " +
            "AND (a.availableFrom IS NULL OR a.availableFrom <= :now) " +
            "AND (a.availableUntil IS NULL OR a.availableUntil >= :now)")
    List<QuarterlySecurityAssessment> findAvailableAssessments(@Param("now") LocalDateTime now);

    /**
     * Find assessment by quarter, year and type
     */
    Optional<QuarterlySecurityAssessment> findByQuarterAndYearAndAssessmentType(
            Integer quarter, Integer year, QuarterlySecurityAssessment.AssessmentType type);

    /**
     * Find assessments by status and availability window
     */
    List<QuarterlySecurityAssessment> findByStatusAndAvailableFromBeforeAndAvailableUntilAfter(
            QuarterlySecurityAssessment.AssessmentStatus status,
            LocalDateTime availableFrom,
            LocalDateTime availableUntil);
}