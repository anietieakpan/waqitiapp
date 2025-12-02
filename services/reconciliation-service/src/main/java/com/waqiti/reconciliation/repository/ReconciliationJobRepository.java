package com.waqiti.reconciliation.repository;

import com.waqiti.reconciliation.domain.ReconciliationJob;
import com.waqiti.reconciliation.model.ReconciliationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReconciliationJobRepository extends JpaRepository<ReconciliationJob, UUID> {

    /**
     * Find reconciliation job by reconciliation date and job type
     */
    Optional<ReconciliationJob> findByReconciliationDateAndJobType(
        LocalDate reconciliationDate, 
        ReconciliationJob.JobType jobType
    );

    /**
     * Find all jobs by status
     */
    List<ReconciliationJob> findByStatus(ReconciliationStatus status);

    /**
     * Find all jobs by status with pagination
     */
    Page<ReconciliationJob> findByStatus(ReconciliationStatus status, Pageable pageable);

    /**
     * Find jobs within date range
     */
    List<ReconciliationJob> findByReconciliationDateBetweenOrderByReconciliationDateDesc(
        LocalDate startDate, 
        LocalDate endDate
    );

    /**
     * Find jobs within date range with pagination
     */
    Page<ReconciliationJob> findByReconciliationDateBetween(
        LocalDate startDate, 
        LocalDate endDate, 
        Pageable pageable
    );

    /**
     * Find jobs by job type within date range
     */
    List<ReconciliationJob> findByJobTypeAndReconciliationDateBetweenOrderByReconciliationDateDesc(
        ReconciliationJob.JobType jobType,
        LocalDate startDate,
        LocalDate endDate
    );

    /**
     * Find jobs that started after specific time
     */
    List<ReconciliationJob> findByStartedAtAfter(LocalDateTime startTime);

    /**
     * Find incomplete jobs (not completed and not failed)
     */
    @Query(\"SELECT j FROM ReconciliationJob j WHERE j.status IN ('IN_PROGRESS', 'PENDING') ORDER BY j.startedAt ASC\")
    List<ReconciliationJob> findIncompleteJobs();

    /**
     * Find jobs that have been running for more than specified hours
     */
    @Query(\"SELECT j FROM ReconciliationJob j WHERE j.status = 'IN_PROGRESS' AND j.startedAt < :cutoffTime\")
    List<ReconciliationJob> findLongRunningJobs(@Param(\"cutoffTime\") LocalDateTime cutoffTime);

    /**
     * Find failed jobs within date range
     */
    @Query(\"SELECT j FROM ReconciliationJob j WHERE j.status = 'FAILED' AND j.reconciliationDate BETWEEN :startDate AND :endDate ORDER BY j.startedAt DESC\")
    List<ReconciliationJob> findFailedJobsInDateRange(
        @Param(\"startDate\") LocalDate startDate,
        @Param(\"endDate\") LocalDate endDate
    );

    /**
     * Get job statistics for a date range
     */
    @Query(\"SELECT j.status, COUNT(j) FROM ReconciliationJob j WHERE j.reconciliationDate BETWEEN :startDate AND :endDate GROUP BY j.status\")
    List<Object[]> getJobStatisticsByDateRange(
        @Param(\"startDate\") LocalDate startDate,
        @Param(\"endDate\") LocalDate endDate
    );

    /**
     * Get job statistics by job type for a date range
     */
    @Query(\"SELECT j.jobType, j.status, COUNT(j) FROM ReconciliationJob j WHERE j.reconciliationDate BETWEEN :startDate AND :endDate GROUP BY j.jobType, j.status\")
    List<Object[]> getJobStatisticsByTypeAndDateRange(
        @Param(\"startDate\") LocalDate startDate,
        @Param(\"endDate\") LocalDate endDate
    );

    /**
     * Find the most recent job for a specific date and type
     */
    Optional<ReconciliationJob> findTopByReconciliationDateAndJobTypeOrderByStartedAtDesc(
        LocalDate reconciliationDate,
        ReconciliationJob.JobType jobType
    );

    /**
     * Find jobs that completed successfully within date range
     */
    @Query(\"SELECT j FROM ReconciliationJob j WHERE j.status IN ('RECONCILED', 'COMPLETED') AND j.reconciliationDate BETWEEN :startDate AND :endDate ORDER BY j.completedAt DESC\")
    List<ReconciliationJob> findSuccessfulJobsInDateRange(
        @Param(\"startDate\") LocalDate startDate,
        @Param(\"endDate\") LocalDate endDate
    );

    /**
     * Count jobs by status and date
     */
    @Query(\"SELECT COUNT(j) FROM ReconciliationJob j WHERE j.status = :status AND j.reconciliationDate = :date\")
    Long countByStatusAndDate(
        @Param(\"status\") ReconciliationStatus status,
        @Param(\"date\") LocalDate date
    );

    /**
     * Find jobs with breaks detected
     */
    @Query(\"SELECT j FROM ReconciliationJob j WHERE j.status = 'BREAKS_DETECTED' AND j.reconciliationDate BETWEEN :startDate AND :endDate ORDER BY j.startedAt DESC\")
    List<ReconciliationJob> findJobsWithBreaks(
        @Param(\"startDate\") LocalDate startDate,
        @Param(\"endDate\") LocalDate endDate
    );

    /**
     * Calculate average job duration by type
     */
    @Query(\"SELECT j.jobType, AVG(TIMESTAMPDIFF(MINUTE, j.startedAt, j.completedAt)) FROM ReconciliationJob j WHERE j.completedAt IS NOT NULL AND j.reconciliationDate BETWEEN :startDate AND :endDate GROUP BY j.jobType\")
    List<Object[]> getAverageJobDurationByType(
        @Param(\"startDate\") LocalDate startDate,
        @Param(\"endDate\") LocalDate endDate
    );

    /**
     * Delete old completed jobs (for cleanup)
     */
    @Query(\"DELETE FROM ReconciliationJob j WHERE j.status IN ('RECONCILED', 'COMPLETED') AND j.completedAt < :cutoffDate\")
    int deleteOldCompletedJobs(@Param(\"cutoffDate\") LocalDateTime cutoffDate);

    /**
     * Check if a job exists for date and type
     */
    boolean existsByReconciliationDateAndJobType(
        LocalDate reconciliationDate,
        ReconciliationJob.JobType jobType
    );
}