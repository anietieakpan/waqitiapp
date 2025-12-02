package com.waqiti.bnpl.repository;

import com.waqiti.bnpl.domain.CreditAssessment;
import com.waqiti.bnpl.domain.enums.AssessmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for credit assessments
 */
@Repository
public interface CreditAssessmentRepository extends JpaRepository<CreditAssessment, Long> {

    /**
     * Find valid assessment for a user
     */
    @Query("SELECT a FROM CreditAssessment a " +
           "WHERE a.userId = :userId " +
           "AND a.status = 'APPROVED' " +
           "AND a.assessmentDate > :validityDate " +
           "AND a.expiryDate > CURRENT_TIMESTAMP " +
           "ORDER BY a.assessmentDate DESC " +
           "LIMIT 1")
    Optional<CreditAssessment> findValidAssessmentForUser(
            @Param("userId") String userId,
            @Param("validityDate") LocalDateTime validityDate);

    /**
     * Find assessments by user ID
     */
    List<CreditAssessment> findByUserIdOrderByAssessmentDateDesc(String userId);

    /**
     * Find assessments by status
     */
    List<CreditAssessment> findByStatus(AssessmentStatus status);

    /**
     * Find expired assessments
     */
    @Query("SELECT a FROM CreditAssessment a " +
           "WHERE a.expiryDate < CURRENT_TIMESTAMP " +
           "AND a.status != 'EXPIRED'")
    List<CreditAssessment> findExpiredAssessments();

    /**
     * Get average credit score for a user
     */
    @Query("SELECT AVG(a.creditScore) FROM CreditAssessment a " +
           "WHERE a.userId = :userId " +
           "AND a.status = 'APPROVED'")
    Double getAverageCreditScore(@Param("userId") String userId);

    /**
     * Count assessments by user and status
     */
    long countByUserIdAndStatus(String userId, AssessmentStatus status);

    /**
     * Find recent assessments for monitoring
     */
    @Query("SELECT a FROM CreditAssessment a " +
           "WHERE a.assessmentDate > :sinceDate " +
           "ORDER BY a.assessmentDate DESC")
    List<CreditAssessment> findRecentAssessments(
            @Param("sinceDate") LocalDateTime sinceDate);
}