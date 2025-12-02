package com.waqiti.corebanking.repository;

import com.waqiti.corebanking.entity.StatementJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for StatementJob entities
 */
@Repository
public interface StatementJobRepository extends JpaRepository<StatementJob, UUID> {
    
    /**
     * Find jobs by account ID
     */
    Page<StatementJob> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);
    
    /**
     * Find jobs by user ID
     */
    Page<StatementJob> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    
    /**
     * Find jobs by status
     */
    List<StatementJob> findByStatus(StatementJob.JobStatus status);
    
    /**
     * Find jobs by status ordered by priority and creation time
     */
    List<StatementJob> findByStatusOrderByPriorityDescCreatedAtAsc(StatementJob.JobStatus status);
    
    /**
     * Find jobs requiring retry
     */
    @Query("SELECT j FROM StatementJob j WHERE j.status = 'FAILED' AND j.retryCount < j.maxRetryAttempts")
    List<StatementJob> findJobsRequiringRetry();
    
    /**
     * Find expired jobs
     */
    @Query("SELECT j FROM StatementJob j WHERE j.status = 'PENDING' AND j.createdAt < :cutoffTime")
    List<StatementJob> findExpiredJobs(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * Find jobs with expired downloads
     */
    @Query("SELECT j FROM StatementJob j WHERE j.status = 'COMPLETED' AND j.downloadExpiresAt < :now")
    List<StatementJob> findJobsWithExpiredDownloads(@Param("now") LocalDateTime now);
    
    /**
     * Find pending jobs for processing
     */
    @Query("SELECT j FROM StatementJob j WHERE j.status = 'PENDING' ORDER BY j.priority DESC, j.createdAt ASC")
    List<StatementJob> findPendingJobsForProcessing(Pageable pageable);
    
    /**
     * Find jobs in progress for monitoring
     */
    @Query("SELECT j FROM StatementJob j WHERE j.status = 'IN_PROGRESS' AND j.startedAt < :cutoffTime")
    List<StatementJob> findStuckInProgressJobs(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * Count jobs by status
     */
    long countByStatus(StatementJob.JobStatus status);
    
    /**
     * Count jobs by account and date range
     */
    @Query("SELECT COUNT(j) FROM StatementJob j WHERE j.accountId = :accountId AND j.createdAt BETWEEN :startDate AND :endDate")
    long countByAccountIdAndDateRange(
        @Param("accountId") UUID accountId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    /**
     * Find jobs by account and status
     */
    List<StatementJob> findByAccountIdAndStatus(UUID accountId, StatementJob.JobStatus status);
    
    /**
     * Find recent jobs for cleanup
     */
    @Query("SELECT j FROM StatementJob j WHERE j.status IN ('COMPLETED', 'FAILED', 'CANCELLED') AND j.completedAt < :cutoffTime")
    List<StatementJob> findJobsForCleanup(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * Get job statistics
     */
    @Query("SELECT j.status, COUNT(j), AVG(j.processingTimeMs) FROM StatementJob j " +
           "WHERE j.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY j.status")
    List<Object[]> getJobStatistics(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find duplicate jobs (same account, date range, format, and status)
     */
    @Query("SELECT j FROM StatementJob j WHERE j.accountId = :accountId AND j.startDate = :startDate " +
           "AND j.endDate = :endDate AND j.format = :format AND j.status IN ('PENDING', 'IN_PROGRESS') " +
           "AND j.jobId != :excludeJobId")
    List<StatementJob> findDuplicateJobs(
        @Param("accountId") UUID accountId,
        @Param("startDate") java.time.LocalDate startDate,
        @Param("endDate") java.time.LocalDate endDate,
        @Param("format") StatementJob.StatementFormat format,
        @Param("excludeJobId") UUID excludeJobId
    );
    
    /**
     * Find jobs by file path (for cleanup)
     */
    Optional<StatementJob> findByFilePath(String filePath);
    
    /**
     * Find high priority jobs
     */
    @Query("SELECT j FROM StatementJob j WHERE j.priority >= :priority AND j.status = 'PENDING' " +
           "ORDER BY j.priority DESC, j.createdAt ASC")
    List<StatementJob> findHighPriorityJobs(@Param("priority") Integer priority);
    
    /**
     * Get average processing time by format
     */
    @Query("SELECT j.format, AVG(j.processingTimeMs) FROM StatementJob j " +
           "WHERE j.status = 'COMPLETED' AND j.processingTimeMs IS NOT NULL " +
           "GROUP BY j.format")
    List<Object[]> getAverageProcessingTimeByFormat();
    
    /**
     * Count active jobs (pending or in progress)
     */
    @Query("SELECT COUNT(j) FROM StatementJob j WHERE j.status IN ('PENDING', 'IN_PROGRESS')")
    long countActiveJobs();
    
    /**
     * Find jobs by user and date range
     */
    @Query("SELECT j FROM StatementJob j WHERE j.userId = :userId " +
           "AND j.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY j.createdAt DESC")
    List<StatementJob> findByUserIdAndDateRange(
        @Param("userId") UUID userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
}