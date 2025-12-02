package com.waqiti.corebanking.service;

import com.waqiti.corebanking.entity.StatementJob;
import com.waqiti.corebanking.repository.StatementJobRepository;
import com.waqiti.common.tracing.Traced;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Scheduled service for processing statement generation jobs
 * Handles job queue processing, retries, and cleanup
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class StatementJobScheduler {

    private final StatementJobRepository statementJobRepository;
    private final StatementJobService statementJobService;
    
    private static final int MAX_CONCURRENT_JOBS = 5;
    private static final int BATCH_SIZE = 10;

    /**
     * Process pending statement jobs every 30 seconds
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    @Traced(operationName = "process-pending-jobs", businessOperation = "job-processing", priority = Traced.TracingPriority.MEDIUM)
    public void processPendingJobs() {
        try {
            log.debug("Starting processing of pending statement jobs");
            
            // Check how many jobs are currently in progress
            long inProgressCount = statementJobRepository.countByStatus(StatementJob.JobStatus.IN_PROGRESS);
            
            if (inProgressCount >= MAX_CONCURRENT_JOBS) {
                log.debug("Maximum concurrent jobs reached ({}/{}), skipping batch", 
                        inProgressCount, MAX_CONCURRENT_JOBS);
                return;
            }
            
            // Get next batch of pending jobs
            int availableSlots = (int) (MAX_CONCURRENT_JOBS - inProgressCount);
            int batchSize = Math.min(BATCH_SIZE, availableSlots);
            
            List<StatementJob> pendingJobs = statementJobRepository.findPendingJobsForProcessing(
                PageRequest.of(0, batchSize));
            
            if (pendingJobs.isEmpty()) {
                log.debug("No pending statement jobs found");
                return;
            }
            
            log.info("Processing {} pending statement jobs", pendingJobs.size());
            
            // Start processing jobs asynchronously
            for (StatementJob job : pendingJobs) {
                try {
                    log.info("Starting processing of statement job: {}", job.getJobId());
                    CompletableFuture<StatementJob> future = statementJobService.processStatementJobAsync(job.getJobId());
                    
                    // Handle completion (success or failure)
                    future.whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            log.error("Statement job processing failed: {}", job.getJobId(), throwable);
                        } else {
                            log.info("Statement job processing completed: {} (status: {})", 
                                    job.getJobId(), result.getStatus());
                        }
                    });
                    
                } catch (Exception e) {
                    log.error("Failed to start processing statement job: {}", job.getJobId(), e);
                }
            }
            
        } catch (Exception e) {
            log.error("Error during pending job processing", e);
        }
    }

    /**
     * Retry failed jobs every 5 minutes
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    @Traced(operationName = "retry-failed-jobs", businessOperation = "job-retry", priority = Traced.TracingPriority.LOW)
    public void retryFailedJobs() {
        try {
            log.debug("Starting retry of failed statement jobs");
            
            List<StatementJob> retriedJobs = statementJobService.retryFailedJobs();
            
            if (!retriedJobs.isEmpty()) {
                log.info("Retried {} failed statement jobs", retriedJobs.size());
            }
            
        } catch (Exception e) {
            log.error("Error during failed job retry", e);
        }
    }

    /**
     * Clean up old jobs every hour
     */
    @Scheduled(fixedDelay = 3600000, initialDelay = 300000)
    @Traced(operationName = "cleanup-old-jobs", businessOperation = "job-maintenance", priority = Traced.TracingPriority.LOW)
    public void cleanupOldJobs() {
        try {
            log.debug("Starting statement job cleanup");
            
            statementJobService.cleanupJobs();
            
            log.debug("Statement job cleanup completed");
            
        } catch (Exception e) {
            log.error("Error during job cleanup", e);
        }
    }

    /**
     * Log job queue statistics every 10 minutes
     */
    @Scheduled(fixedDelay = 600000, initialDelay = 120000)
    @Traced(operationName = "log-job-statistics", businessOperation = "monitoring", priority = Traced.TracingPriority.LOW)
    public void logJobStatistics() {
        try {
            long pendingCount = statementJobRepository.countByStatus(StatementJob.JobStatus.PENDING);
            long inProgressCount = statementJobRepository.countByStatus(StatementJob.JobStatus.IN_PROGRESS);
            long completedCount = statementJobRepository.countByStatus(StatementJob.JobStatus.COMPLETED);
            long failedCount = statementJobRepository.countByStatus(StatementJob.JobStatus.FAILED);
            long cancelledCount = statementJobRepository.countByStatus(StatementJob.JobStatus.CANCELLED);
            long expiredCount = statementJobRepository.countByStatus(StatementJob.JobStatus.EXPIRED);
            
            log.info("Statement job queue statistics: pending={}, in-progress={}, completed={}, " +
                    "failed={}, cancelled={}, expired={}", 
                    pendingCount, inProgressCount, completedCount, failedCount, 
                    cancelledCount, expiredCount);
            
            // Alert if queue is backing up
            if (pendingCount > 50) {
                log.warn("Statement job queue is backing up: {} pending jobs", pendingCount);
            }
            
            if (failedCount > 20) {
                log.warn("High number of failed statement jobs: {}", failedCount);
            }
            
        } catch (Exception e) {
            log.error("Error getting job statistics", e);
        }
    }
    
    /**
     * Process high priority jobs immediately every 10 seconds
     */
    @Scheduled(fixedDelay = 10000, initialDelay = 5000)
    @Traced(operationName = "process-priority-jobs", businessOperation = "priority-processing", priority = Traced.TracingPriority.HIGH)
    public void processHighPriorityJobs() {
        try {
            // Process high priority jobs (priority >= 5) immediately
            List<StatementJob> priorityJobs = statementJobRepository.findHighPriorityJobs(5);
            
            if (priorityJobs.isEmpty()) {
                return;
            }
            
            log.info("Processing {} high priority statement jobs", priorityJobs.size());
            
            for (StatementJob job : priorityJobs) {
                if (job.getStatus() == StatementJob.JobStatus.PENDING) {
                    try {
                        log.info("Processing high priority statement job: {} (priority: {})", 
                                job.getJobId(), job.getPriority());
                        statementJobService.processStatementJobAsync(job.getJobId());
                    } catch (Exception e) {
                        log.error("Failed to process high priority job: {}", job.getJobId(), e);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Error processing high priority jobs", e);
        }
    }
}