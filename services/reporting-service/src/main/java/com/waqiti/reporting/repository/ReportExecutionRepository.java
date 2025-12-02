package com.waqiti.reporting.repository;

import com.waqiti.reporting.domain.ReportExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReportExecutionRepository extends JpaRepository<ReportExecution, UUID> {

    /**
     * Find executions by report type
     */
    List<ReportExecution> findByReportTypeOrderByStartedAtDesc(String reportType);

    /**
     * Find executions by status
     */
    List<ReportExecution> findByStatusOrderByStartedAtDesc(ReportExecution.ExecutionStatus status);

    /**
     * Find executions by requested user
     */
    Page<ReportExecution> findByRequestedByOrderByStartedAtDesc(String requestedBy, Pageable pageable);

    /**
     * Find executions within date range
     */
    @Query("SELECT re FROM ReportExecution re WHERE re.startedAt BETWEEN :startDate AND :endDate ORDER BY re.startedAt DESC")
    List<ReportExecution> findExecutionsInDateRange(@Param("startDate") LocalDateTime startDate, 
                                                   @Param("endDate") LocalDateTime endDate);

    /**
     * Find running executions (in progress)
     */
    @Query("SELECT re FROM ReportExecution re WHERE re.status = 'IN_PROGRESS' ORDER BY re.startedAt ASC")
    List<ReportExecution> findRunningExecutions();

    /**
     * Find failed executions for retry
     */
    @Query("SELECT re FROM ReportExecution re WHERE re.status = 'FAILED' AND re.startedAt > :sinceDate ORDER BY re.startedAt DESC")
    List<ReportExecution> findFailedExecutionsSince(@Param("sinceDate") LocalDateTime sinceDate);

    /**
     * Count executions by status and date range
     */
    @Query("SELECT COUNT(re) FROM ReportExecution re WHERE re.status = :status AND re.startedAt BETWEEN :startDate AND :endDate")
    long countExecutionsByStatusAndDateRange(@Param("status") ReportExecution.ExecutionStatus status,
                                           @Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);

    /**
     * Get execution statistics
     */
    @Query("SELECT re.status, COUNT(re) FROM ReportExecution re WHERE re.startedAt >= :sinceDate GROUP BY re.status")
    List<Object[]> getExecutionStatistics(@Param("sinceDate") LocalDateTime sinceDate);

    /**
     * Find long-running executions
     */
    @Query("SELECT re FROM ReportExecution re WHERE re.status = 'IN_PROGRESS' AND re.startedAt < :cutoffTime")
    List<ReportExecution> findLongRunningExecutions(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find executions by report type and status
     */
    List<ReportExecution> findByReportTypeAndStatusOrderByStartedAtDesc(String reportType, 
                                                                        ReportExecution.ExecutionStatus status);

    /**
     * Delete old completed executions (for cleanup)
     */
    @Modifying
    @Query("DELETE FROM ReportExecution re WHERE re.status IN ('COMPLETED', 'FAILED', 'CANCELLED') AND re.completedAt < :cutoffDate")
    int deleteOldExecutions(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Get average execution time by report type
     */
    @Query("SELECT re.reportType, AVG(EXTRACT(EPOCH FROM (re.completedAt - re.startedAt))) " +
           "FROM ReportExecution re WHERE re.status = 'COMPLETED' AND re.startedAt >= :sinceDate " +
           "GROUP BY re.reportType")
    List<Object[]> getAverageExecutionTimeByReportType(@Param("sinceDate") LocalDateTime sinceDate);

    /**
     * Find most recent successful execution for a report type
     */
    @Query("SELECT re FROM ReportExecution re WHERE re.reportType = :reportType AND re.status = 'COMPLETED' " +
           "ORDER BY re.completedAt DESC LIMIT 1")
    ReportExecution findMostRecentSuccessfulExecution(@Param("reportType") String reportType);
    
    /**
     * Find by report type and status with string status
     */
    @Query("SELECT re FROM ReportExecution re WHERE " +
           "(:reportType IS NULL OR re.reportType = :reportType) AND " +
           "(:status IS NULL OR CAST(re.status AS string) = :status)")
    Page<ReportExecution> findByReportTypeAndStatus(
        @Param("reportType") String reportType,
        @Param("status") String status,
        Pageable pageable
    );
}