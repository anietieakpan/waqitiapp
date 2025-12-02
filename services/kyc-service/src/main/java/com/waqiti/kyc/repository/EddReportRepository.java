package com.waqiti.kyc.repository;

import com.waqiti.kyc.entity.EddReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Enhanced Due Diligence (EDD) Reports
 * 
 * EDD is required for high-risk customers including:
 * - PEPs (Politically Exposed Persons)
 * - High-value transactions (typically >$10,000)
 * - Customers from high-risk jurisdictions
 * - Customers with adverse media mentions
 * 
 * @author Waqiti Compliance Team
 * @version 2.0.0
 * @since 2025-01-27
 */
@Repository
public interface EddReportRepository extends JpaRepository<EddReportEntity, UUID> {
    
    /**
     * Find latest EDD report for a user
     */
    @Query("SELECT e FROM EddReportEntity e " +
           "WHERE e.userId = :userId " +
           "ORDER BY e.generatedAt DESC " +
           "LIMIT 1")
    Optional<EddReportEntity> findLatestByUserId(@Param("userId") String userId);
    
    /**
     * Find all EDD reports for a user
     */
    @Query("SELECT e FROM EddReportEntity e " +
           "WHERE e.userId = :userId " +
           "ORDER BY e.generatedAt DESC")
    List<EddReportEntity> findAllByUserId(@Param("userId") String userId);
    
    /**
     * Find approved EDD reports
     */
    @Query("SELECT e FROM EddReportEntity e " +
           "WHERE e.userId = :userId " +
           "AND e.status = 'APPROVED' " +
           "AND e.expiryDate > :now " +
           "ORDER BY e.generatedAt DESC")
    List<EddReportEntity> findApprovedReports(@Param("userId") String userId,
                                              @Param("now") LocalDateTime now);
    
    /**
     * Find EDD reports requiring review
     */
    @Query("SELECT e FROM EddReportEntity e " +
           "WHERE e.status = 'PENDING_REVIEW' " +
           "ORDER BY e.riskScore DESC, e.generatedAt ASC")
    List<EddReportEntity> findPendingReview();
    
    /**
     * Find EDD reports expiring soon
     */
    @Query("SELECT e FROM EddReportEntity e " +
           "WHERE e.status = 'APPROVED' " +
           "AND e.expiryDate BETWEEN :start AND :end " +
           "ORDER BY e.expiryDate ASC")
    List<EddReportEntity> findExpiringSoon(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);
    
    /**
     * Find high-risk EDD reports
     */
    @Query("SELECT e FROM EddReportEntity e " +
           "WHERE e.riskLevel IN ('HIGH', 'CRITICAL') " +
           "AND e.status = 'APPROVED' " +
           "ORDER BY e.riskScore DESC")
    List<EddReportEntity> findHighRiskReports();
    
    /**
     * Count EDD reports by status
     */
    @Query("SELECT e.status, COUNT(e) FROM EddReportEntity e " +
           "GROUP BY e.status")
    List<Object[]> countByStatus();
    
    /**
     * Find EDD reports with adverse media findings
     */
    @Query("SELECT e FROM EddReportEntity e " +
           "WHERE e.adverseMediaFound = true " +
           "AND e.status IN ('APPROVED', 'PENDING_REVIEW') " +
           "ORDER BY e.generatedAt DESC")
    List<EddReportEntity> findWithAdverseMedia();
    
    /**
     * Find EDD reports for specific risk score range
     */
    @Query("SELECT e FROM EddReportEntity e " +
           "WHERE e.riskScore BETWEEN :minScore AND :maxScore " +
           "ORDER BY e.riskScore DESC, e.generatedAt DESC")
    List<EddReportEntity> findByRiskScoreRange(@Param("minScore") Double minScore,
                                               @Param("maxScore") Double maxScore);
}