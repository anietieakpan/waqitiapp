package com.waqiti.kyc.repository;

import com.waqiti.kyc.domain.ComplianceReport;
import com.waqiti.kyc.domain.ComplianceReport.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for ComplianceReport entities
 */
@Repository
public interface ComplianceReportRepository extends JpaRepository<ComplianceReport, String> {
    
    List<ComplianceReport> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);
    
    List<ComplianceReport> findByOrganizationIdAndRecurringTrue(String organizationId);
    
    List<ComplianceReport> findByStatusAndNextRunAtBefore(ReportStatus status, LocalDateTime dateTime);
    
    @Query("SELECT r FROM ComplianceReport r WHERE r.organizationId = :orgId " +
           "AND r.reportType = :type AND r.createdAt BETWEEN :start AND :end")
    List<ComplianceReport> findByOrganizationAndTypeAndDateRange(
            @Param("orgId") String organizationId,
            @Param("type") String reportType,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
    
    List<ComplianceReport> findByRequestedBy(String userId);
    
    @Query("SELECT COUNT(r) FROM ComplianceReport r WHERE r.organizationId = :orgId " +
           "AND r.status = :status")
    long countByOrganizationIdAndStatus(
            @Param("orgId") String organizationId,
            @Param("status") ReportStatus status
    );
}