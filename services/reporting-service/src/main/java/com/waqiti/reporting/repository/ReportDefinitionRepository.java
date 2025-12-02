package com.waqiti.reporting.repository;

import com.waqiti.reporting.domain.ReportDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, UUID> {

    /**
     * Find report by unique code
     */
    Optional<ReportDefinition> findByReportCode(String reportCode);

    /**
     * Find reports by category
     */
    List<ReportDefinition> findByCategoryAndIsActiveTrue(ReportDefinition.ReportCategory category);

    /**
     * Find reports by type
     */
    List<ReportDefinition> findByReportTypeAndIsActiveTrue(ReportDefinition.ReportType reportType);

    /**
     * Find all active reports
     */
    List<ReportDefinition> findByIsActiveTrueOrderByReportName();

    /**
     * Find schedulable reports
     */
    List<ReportDefinition> findByIsActiveTrueAndIsSchedulableTrueOrderByReportName();

    /**
     * Search reports by name or description
     */
    @Query("SELECT rd FROM ReportDefinition rd WHERE rd.isActive = true AND " +
           "(LOWER(rd.reportName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(rd.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<ReportDefinition> searchReports(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Find reports by required permission
     */
    @Query("SELECT rd FROM ReportDefinition rd WHERE rd.isActive = true AND :permission MEMBER OF rd.requiredPermissions")
    List<ReportDefinition> findByRequiredPermission(@Param("permission") String permission);

    /**
     * Find reports that user can access based on permissions
     */
    @Query("SELECT rd FROM ReportDefinition rd WHERE rd.isActive = true AND " +
           "(rd.requiredPermissions IS EMPTY OR EXISTS " +
           "(SELECT 1 FROM rd.requiredPermissions rp WHERE rp IN :userPermissions))")
    List<ReportDefinition> findAccessibleReports(@Param("userPermissions") List<String> userPermissions);

    /**
     * Find reports by category and permissions
     */
    @Query("SELECT rd FROM ReportDefinition rd WHERE rd.isActive = true AND rd.category = :category AND " +
           "(rd.requiredPermissions IS EMPTY OR EXISTS " +
           "(SELECT 1 FROM rd.requiredPermissions rp WHERE rp IN :userPermissions))")
    List<ReportDefinition> findByCategoryAndPermissions(@Param("category") ReportDefinition.ReportCategory category,
                                                        @Param("userPermissions") List<String> userPermissions);

    /**
     * Find reports that support specific output format
     */
    @Query("SELECT rd FROM ReportDefinition rd WHERE rd.isActive = true AND :format MEMBER OF rd.supportedFormats")
    List<ReportDefinition> findBySupportedFormat(@Param("format") ReportDefinition.OutputFormat format);

    /**
     * Find reports created by user
     */
    List<ReportDefinition> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    /**
     * Get report categories with counts
     */
    @Query("SELECT rd.category, COUNT(rd) FROM ReportDefinition rd WHERE rd.isActive = true GROUP BY rd.category")
    List<Object[]> getReportCategoryCounts();

    /**
     * Get report types with counts
     */
    @Query("SELECT rd.reportType, COUNT(rd) FROM ReportDefinition rd WHERE rd.isActive = true GROUP BY rd.reportType")
    List<Object[]> getReportTypeCounts();

    /**
     * Find reports that require approval
     */
    List<ReportDefinition> findByIsActiveTrueAndRequiresApprovalTrueOrderByReportName();

    /**
     * Check if report code exists
     */
    boolean existsByReportCode(String reportCode);

    /**
     * Count active reports by category
     */
    long countByCategoryAndIsActiveTrue(ReportDefinition.ReportCategory category);
}