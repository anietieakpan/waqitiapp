package com.waqiti.corebanking.service;

import com.waqiti.corebanking.entity.StatementJob;
import com.waqiti.corebanking.repository.StatementJobRepository;
import com.waqiti.corebanking.exception.StatementJobNotFoundException;
import com.waqiti.corebanking.exception.StatementJobCreationException;
import com.waqiti.common.tracing.Traced;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing statement generation jobs
 * Handles job lifecycle, status tracking, retry logic, and cleanup
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class StatementJobService {

    private final StatementJobRepository statementJobRepository;
    private final StatementGenerationService statementGenerationService;

    /**
     * Creates a new statement generation job
     */
    @Traced(operationName = "create-statement-job", businessOperation = "statement-generation", priority = Traced.TracingPriority.HIGH)
    public StatementJob createStatementJob(UUID accountId, UUID userId, LocalDate startDate, 
                                         LocalDate endDate, StatementJob.StatementFormat format) {
        return createStatementJob(accountId, userId, startDate, endDate, format, 1);
    }

    /**
     * Creates a new statement generation job with priority
     */
    @Traced(operationName = "create-statement-job", businessOperation = "statement-generation", priority = Traced.TracingPriority.HIGH)
    public StatementJob createStatementJob(UUID accountId, UUID userId, LocalDate startDate, 
                                         LocalDate endDate, StatementJob.StatementFormat format, Integer priority) {
        try {
            log.info("Creating statement job: account={}, user={}, period={}to{}, format={}", 
                    accountId, userId, startDate, endDate, format);

            // Check for duplicate pending/in-progress jobs
            List<StatementJob> duplicates = statementJobRepository.findDuplicateJobs(
                accountId, startDate, endDate, format, UUID.randomUUID());
            
            if (!duplicates.isEmpty()) {
                log.warn("Duplicate statement job found for account={}, returning existing job: {}", 
                        accountId, duplicates.get(0).getJobId());
                return duplicates.get(0);
            }

            // Estimate processing time based on date range and format
            LocalDateTime estimatedCompletion = estimateCompletionTime(startDate, endDate, format);

            StatementJob job = StatementJob.builder()
                .accountId(accountId)
                .userId(userId)
                .startDate(startDate)
                .endDate(endDate)
                .format(format)
                .status(StatementJob.JobStatus.PENDING)
                .priority(priority != null ? priority : 1)
                .estimatedCompletionTime(estimatedCompletion)
                .message("Statement job created")
                .build();

            StatementJob savedJob = statementJobRepository.save(job);
            
            log.info("Statement job created successfully: {}", savedJob.getJobId());
            return savedJob;

        } catch (Exception e) {
            log.error("Failed to create statement job for account: {}", accountId, e);
            throw new StatementJobCreationException("Failed to create statement job", accountId, userId, e);
        }
    }

    /**
     * Gets statement job by ID
     */
    @Transactional(readOnly = true)
    @Traced(operationName = "get-statement-job", businessOperation = "job-inquiry", priority = Traced.TracingPriority.MEDIUM)
    public StatementJob getStatementJob(UUID jobId) {
        log.debug("Retrieving statement job: {}", jobId);
        
        return statementJobRepository.findById(jobId)
            .orElseThrow(() -> {
                log.warn("Statement job not found: {}", jobId);
                return new StatementJobNotFoundException("Statement job not found: " + jobId, jobId);
            });
    }

    /**
     * Gets statement jobs for account with pagination
     */
    @Transactional(readOnly = true)
    @Traced(operationName = "get-account-jobs", businessOperation = "job-inquiry", priority = Traced.TracingPriority.MEDIUM)
    public Page<StatementJob> getAccountStatementJobs(UUID accountId, Pageable pageable) {
        log.debug("Retrieving statement jobs for account: {}", accountId);
        return statementJobRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
    }

    /**
     * Gets statement jobs for user with pagination
     */
    @Transactional(readOnly = true)
    @Traced(operationName = "get-user-jobs", businessOperation = "job-inquiry", priority = Traced.TracingPriority.MEDIUM)
    public Page<StatementJob> getUserStatementJobs(UUID userId, Pageable pageable) {
        log.debug("Retrieving statement jobs for user: {}", userId);
        return statementJobRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * Processes statement generation job asynchronously
     */
    @Async("statementTaskExecutor")
    @Traced(operationName = "process-statement-job", businessOperation = "statement-generation", priority = Traced.TracingPriority.HIGH)
    public CompletableFuture<StatementJob> processStatementJobAsync(UUID jobId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                StatementJob job = getStatementJob(jobId);
                
                if (job.getStatus() != StatementJob.JobStatus.PENDING) {
                    log.warn("Statement job {} is not pending (status: {}), skipping processing", 
                            jobId, job.getStatus());
                    return job;
                }

                log.info("Starting statement job processing: {}", jobId);
                
                // Mark job as started
                job.markAsStarted();
                statementJobRepository.save(job);

                // Generate statement
                StatementGenerationService.StatementResult result = statementGenerationService.generateStatement(
                    job.getAccountId(), job.getStartDate(), job.getEndDate(), 
                    convertToStatementFormat(job.getFormat()));

                if ("SUCCESS".equals(result.getStatus())) {
                    // Generate file path and download URL
                    String fileName = generateFileName(job);
                    String filePath = "/statements/" + fileName;
                    String downloadUrl = "/api/v1/statements/download/" + jobId;
                    Long fileSize = (long) result.getStatementData().length;

                    // Mark job as completed
                    job.markAsCompleted(filePath, fileSize, downloadUrl);
                    
                    // Set transaction count from result
                    job.setTransactionCount(result.getTransactionCount());
                    
                } else {
                    // Mark job as failed
                    String errorMessage = result.getError() != null ? 
                        result.getError() : "Statement generation failed";
                    job.markAsFailed(errorMessage);
                }

                StatementJob updatedJob = statementJobRepository.save(job);
                
                log.info("Statement job processing completed: {} (status: {})", 
                        jobId, updatedJob.getStatus());
                
                return updatedJob;

            } catch (Exception e) {
                log.error("Statement job processing failed: {}", jobId, e);
                
                try {
                    StatementJob job = getStatementJob(jobId);
                    job.markAsFailed("Processing error: " + e.getMessage());
                    return statementJobRepository.save(job);
                } catch (Exception saveException) {
                    log.error("Failed to update job status after processing error: {}", jobId, saveException);
                    throw new RuntimeException("Job processing failed and status update failed", e);
                }
            }
        });
    }

    /**
     * Retries failed jobs
     */
    @Traced(operationName = "retry-statement-jobs", businessOperation = "job-retry", priority = Traced.TracingPriority.MEDIUM)
    public List<StatementJob> retryFailedJobs() {
        log.info("Starting retry of failed statement jobs");
        
        List<StatementJob> jobsToRetry = statementJobRepository.findJobsRequiringRetry();
        
        log.info("Found {} jobs requiring retry", jobsToRetry.size());
        
        for (StatementJob job : jobsToRetry) {
            try {
                log.info("Retrying statement job: {} (attempt {}/{})", 
                        job.getJobId(), job.getRetryCount() + 1, job.getMaxRetryAttempts());
                
                job.incrementRetryCount();
                job.setStatus(StatementJob.JobStatus.PENDING);
                job.setMessage("Job queued for retry");
                job.setErrorMessage(null);
                
                statementJobRepository.save(job);
                
                // Start async processing
                processStatementJobAsync(job.getJobId());
                
            } catch (Exception e) {
                log.error("Failed to retry statement job: {}", job.getJobId(), e);
            }
        }
        
        return jobsToRetry;
    }

    /**
     * Cancels a pending or in-progress job
     */
    @Traced(operationName = "cancel-statement-job", businessOperation = "job-management", priority = Traced.TracingPriority.MEDIUM)
    public StatementJob cancelStatementJob(UUID jobId) {
        log.info("Cancelling statement job: {}", jobId);
        
        StatementJob job = getStatementJob(jobId);
        
        if (job.getStatus() == StatementJob.JobStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel completed job");
        }
        
        if (job.getStatus() == StatementJob.JobStatus.CANCELLED) {
            log.warn("Statement job {} is already cancelled", jobId);
            return job;
        }
        
        job.markAsCancelled();
        StatementJob updatedJob = statementJobRepository.save(job);
        
        log.info("Statement job cancelled: {}", jobId);
        return updatedJob;
    }

    /**
     * Cleans up expired jobs and old completed jobs
     */
    @Traced(operationName = "cleanup-statement-jobs", businessOperation = "job-maintenance", priority = Traced.TracingPriority.LOW)
    public void cleanupJobs() {
        log.info("Starting statement job cleanup");
        
        // Mark expired pending jobs
        LocalDateTime expiredTime = LocalDateTime.now().minusHours(24); // 24 hour timeout
        List<StatementJob> expiredJobs = statementJobRepository.findExpiredJobs(expiredTime);
        
        for (StatementJob job : expiredJobs) {
            log.info("Marking expired job: {}", job.getJobId());
            job.setStatus(StatementJob.JobStatus.EXPIRED);
            job.setMessage("Job expired after 24 hours");
            statementJobRepository.save(job);
        }
        
        // Find stuck in-progress jobs
        LocalDateTime stuckTime = LocalDateTime.now().minusHours(2); // 2 hour processing timeout
        List<StatementJob> stuckJobs = statementJobRepository.findStuckInProgressJobs(stuckTime);
        
        for (StatementJob job : stuckJobs) {
            log.warn("Found stuck in-progress job: {}, marking as failed", job.getJobId());
            job.markAsFailed("Job stuck in processing");
            statementJobRepository.save(job);
        }
        
        // Clean up jobs with expired downloads
        List<StatementJob> expiredDownloads = statementJobRepository.findJobsWithExpiredDownloads(LocalDateTime.now());
        
        for (StatementJob job : expiredDownloads) {
            log.info("Cleaning up expired download for job: {}", job.getJobId());
            job.setDownloadUrl(null);
            job.setFilePath(null);
            job.setMessage("Download link expired");
            statementJobRepository.save(job);
        }
        
        // Delete very old completed/failed jobs (older than 90 days)
        LocalDateTime cleanupTime = LocalDateTime.now().minusDays(90);
        List<StatementJob> oldJobs = statementJobRepository.findJobsForCleanup(cleanupTime);
        
        log.info("Deleting {} old completed/failed jobs", oldJobs.size());
        statementJobRepository.deleteAll(oldJobs);
        
        log.info("Statement job cleanup completed");
    }

    /**
     * Gets job statistics
     */
    @Transactional(readOnly = true)
    @Traced(operationName = "get-job-statistics", businessOperation = "job-reporting", priority = Traced.TracingPriority.LOW)
    public JobStatistics getJobStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        log.debug("Getting job statistics for period: {} to {}", startDate, endDate);
        
        List<Object[]> stats = statementJobRepository.getJobStatistics(startDate, endDate);
        List<Object[]> avgProcessingTimes = statementJobRepository.getAverageProcessingTimeByFormat();
        long activeJobs = statementJobRepository.countActiveJobs();
        
        return JobStatistics.builder()
            .totalJobs(stats.stream().mapToLong(row -> ((Number) row[1]).longValue()).sum())
            .activeJobs(activeJobs)
            .completedJobs(getCountForStatus(stats, StatementJob.JobStatus.COMPLETED))
            .failedJobs(getCountForStatus(stats, StatementJob.JobStatus.FAILED))
            .pendingJobs(getCountForStatus(stats, StatementJob.JobStatus.PENDING))
            .inProgressJobs(getCountForStatus(stats, StatementJob.JobStatus.IN_PROGRESS))
            .averageProcessingTimeMs(getAverageProcessingTime(stats))
            .periodStart(startDate)
            .periodEnd(endDate)
            .build();
    }

    // Helper methods

    private LocalDateTime estimateCompletionTime(LocalDate startDate, LocalDate endDate, StatementJob.StatementFormat format) {
        // Estimate based on date range and format
        long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        
        int baseMinutes = 2; // Base processing time
        int additionalMinutes = (int) (daysDiff / 30); // 1 minute per month
        
        if (format == StatementJob.StatementFormat.PDF) {
            additionalMinutes += 2; // PDF takes longer
        }
        
        return LocalDateTime.now().plusMinutes(baseMinutes + additionalMinutes);
    }

    private StatementGenerationService.StatementFormat convertToStatementFormat(StatementJob.StatementFormat format) {
        return StatementGenerationService.StatementFormat.valueOf(format.name());
    }

    private String generateFileName(StatementJob job) {
        return String.format("statement_%s_%s_to_%s.%s", 
            job.getAccountId().toString().substring(0, 8),
            job.getStartDate(),
            job.getEndDate(),
            job.getFormat().name().toLowerCase());
    }

    private long getCountForStatus(List<Object[]> stats, StatementJob.JobStatus status) {
        return stats.stream()
            .filter(row -> status.equals(row[0]))
            .mapToLong(row -> ((Number) row[1]).longValue())
            .findFirst()
            .orElse(0L);
    }

    private double getAverageProcessingTime(List<Object[]> stats) {
        return stats.stream()
            .mapToDouble(row -> row[2] != null ? ((Number) row[2]).doubleValue() : 0.0)
            .average()
            .orElse(0.0);
    }

    // DTOs

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class JobStatistics {
        private long totalJobs;
        private long activeJobs;
        private long completedJobs;
        private long failedJobs;
        private long pendingJobs;
        private long inProgressJobs;
        private double averageProcessingTimeMs;
        private LocalDateTime periodStart;
        private LocalDateTime periodEnd;
    }
}