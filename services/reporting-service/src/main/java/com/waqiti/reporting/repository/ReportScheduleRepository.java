package com.waqiti.reporting.repository;

import com.waqiti.reporting.domain.ReportSchedule;
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
public interface ReportScheduleRepository extends JpaRepository<ReportSchedule, UUID> {

    /**
     * Find active schedules by frequency
     */
    List<ReportSchedule> findByIsActiveTrueAndFrequencyOrderByNextExecution(ReportSchedule.ScheduleFrequency frequency);

    /**
     * Find schedules due for execution
     */
    @Query("SELECT rs FROM ReportSchedule rs WHERE rs.isActive = true AND rs.nextExecution <= :currentTime")
    List<ReportSchedule> findSchedulesDueForExecution(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Find schedules by created user
     */
    List<ReportSchedule> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    /**
     * Find schedules for a specific report
     */
    @Query("SELECT rs FROM ReportSchedule rs WHERE rs.reportDefinition.reportId = :reportId")
    List<ReportSchedule> findByReportId(@Param("reportId") UUID reportId);

    /**
     * Find active schedules
     */
    List<ReportSchedule> findByIsActiveTrueOrderByNextExecution();

    /**
     * Find schedules by frequency and status
     */
    List<ReportSchedule> findByFrequencyAndIsActiveTrueOrderByNextExecution(ReportSchedule.ScheduleFrequency frequency);

    /**
     * Find schedules with failed last execution
     */
    @Query("SELECT rs FROM ReportSchedule rs WHERE rs.isActive = true AND rs.lastExecutionStatus = 'FAILED'")
    List<ReportSchedule> findFailedSchedules();

    /**
     * Find schedules with email notification enabled
     */
    List<ReportSchedule> findByIsActiveTrueAndEmailNotificationTrueOrderByNextExecution();

    /**
     * Find schedules by recipient email
     */
    @Query("SELECT rs FROM ReportSchedule rs WHERE rs.isActive = true AND :email MEMBER OF rs.recipients")
    List<ReportSchedule> findByRecipientEmail(@Param("email") String email);

    /**
     * Find overdue schedules (past next execution time)
     */
    @Query("SELECT rs FROM ReportSchedule rs WHERE rs.isActive = true AND rs.nextExecution < :currentTime")
    List<ReportSchedule> findOverdueSchedules(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Count schedules by frequency
     */
    @Query("SELECT rs.frequency, COUNT(rs) FROM ReportSchedule rs WHERE rs.isActive = true GROUP BY rs.frequency")
    List<Object[]> countSchedulesByFrequency();

    /**
     * Find schedules to execute within time window
     */
    @Query("SELECT rs FROM ReportSchedule rs WHERE rs.isActive = true AND " +
           "rs.nextExecution BETWEEN :startTime AND :endTime ORDER BY rs.nextExecution")
    List<ReportSchedule> findSchedulesInTimeWindow(@Param("startTime") LocalDateTime startTime,
                                                  @Param("endTime") LocalDateTime endTime);

    /**
     * Get schedule execution statistics
     */
    @Query("SELECT rs.lastExecutionStatus, COUNT(rs) FROM ReportSchedule rs WHERE rs.isActive = true " +
           "AND rs.lastExecuted IS NOT NULL GROUP BY rs.lastExecutionStatus")
    List<Object[]> getScheduleExecutionStatistics();

    /**
     * Find schedules by report category
     */
    @Query("SELECT rs FROM ReportSchedule rs WHERE rs.isActive = true AND rs.reportDefinition.category = :category")
    List<ReportSchedule> findByReportCategory(@Param("category") ReportSchedule.ScheduleFrequency category);

    /**
     * Find schedules with specific output format
     */
    List<ReportSchedule> findByIsActiveTrueAndOutputFormatOrderByNextExecution(ReportSchedule.OutputFormat outputFormat);

    /**
     * Update next execution time for schedule
     */
    @Modifying
    @Query("UPDATE ReportSchedule rs SET rs.nextExecution = :nextExecution WHERE rs.scheduleId = :scheduleId")
    int updateNextExecution(@Param("scheduleId") UUID scheduleId, @Param("nextExecution") LocalDateTime nextExecution);

    /**
     * Update last execution details
     */
    @Modifying
    @Query("UPDATE ReportSchedule rs SET rs.lastExecuted = :lastExecuted, rs.lastExecutionStatus = :status, " +
           "rs.lastExecutionError = :error WHERE rs.scheduleId = :scheduleId")
    int updateLastExecution(@Param("scheduleId") UUID scheduleId,
                          @Param("lastExecuted") LocalDateTime lastExecuted,
                          @Param("status") ReportSchedule.ExecutionStatus status,
                          @Param("error") String error);

    /**
     * Count active schedules
     */
    long countByIsActiveTrue();

    /**
     * Find schedules created between dates
     */
    @Query("SELECT rs FROM ReportSchedule rs WHERE rs.createdAt BETWEEN :startDate AND :endDate ORDER BY rs.createdAt DESC")
    Page<ReportSchedule> findSchedulesCreatedBetween(@Param("startDate") LocalDateTime startDate,
                                                   @Param("endDate") LocalDateTime endDate,
                                                   Pageable pageable);
    
    /**
     * Find schedules by active status
     */
    Page<ReportSchedule> findByActive(Boolean active, Pageable pageable);
}