package com.waqiti.kyc.service.impl;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.waqiti.kyc.domain.BatchKYCJob;
import com.waqiti.kyc.domain.BatchKYCJob.JobStatus;
import com.waqiti.kyc.dto.request.BatchKYCRequest;
import com.waqiti.kyc.dto.request.KYCVerificationRequest;
import com.waqiti.kyc.dto.response.BatchKYCResponse;
import com.waqiti.kyc.dto.response.BatchKYCStatus;
import com.waqiti.kyc.dto.response.KYCVerificationResponse;
import com.waqiti.kyc.exception.BatchProcessingException;
import com.waqiti.kyc.repository.BatchKYCJobRepository;
import com.waqiti.kyc.service.BatchKYCService;
import com.waqiti.kyc.service.KYCVerificationService;
import com.waqiti.common.event.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Implementation of BatchKYCService for bulk KYC processing
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BatchKYCServiceImpl implements BatchKYCService {
    
    private final BatchKYCJobRepository batchJobRepository;
    private final KYCVerificationService verificationService;
    private final EventPublisher eventPublisher;
    
    private final ExecutorService batchExecutor = Executors.newFixedThreadPool(10);
    private static final int BATCH_SIZE = 50;
    private static final int MAX_CONCURRENT_VERIFICATIONS = 20;
    
    @Override
    @Async
    public CompletableFuture<BatchKYCResponse> submitBatchVerification(BatchKYCRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Starting batch KYC verification for {} users", request.getUsers().size());
            
            // Create batch job
            BatchKYCJob batchJob = createBatchJob(request);
            
            // Process users in batches
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            List<BatchKYCRequest.UserKYCRequest> users = request.getUsers();
            
            for (int i = 0; i < users.size(); i += BATCH_SIZE) {
                int endIndex = Math.min(i + BATCH_SIZE, users.size());
                List<BatchKYCRequest.UserKYCRequest> batch = users.subList(i, endIndex);
                
                CompletableFuture<Void> batchFuture = processBatch(batchJob, batch);
                futures.add(batchFuture);
            }
            
            // Wait for all batches to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenRun(() -> completeBatchJob(batchJob))
                    .exceptionally(ex -> {
                        failBatchJob(batchJob, ex.getMessage());
                        return null;
                    });
            
            // Return initial response
            return BatchKYCResponse.builder()
                    .batchId(batchJob.getId())
                    .status(batchJob.getStatus())
                    .totalUsers(batchJob.getTotalUsers())
                    .startedAt(batchJob.getStartedAt())
                    .build();
        });
    }
    
    @Override
    public BatchKYCStatus getBatchStatus(String batchId) {
        BatchKYCJob batchJob = batchJobRepository.findById(batchId)
                .orElseThrow(() -> new BatchProcessingException("Batch job not found: " + batchId));
        
        return BatchKYCStatus.builder()
                .batchId(batchJob.getId())
                .status(batchJob.getStatus())
                .totalUsers(batchJob.getTotalUsers())
                .processedUsers(batchJob.getProcessedUsers())
                .successfulUsers(batchJob.getSuccessfulUsers())
                .failedUsers(batchJob.getFailedUsers())
                .progressPercentage(calculateProgress(batchJob))
                .startedAt(batchJob.getStartedAt())
                .completedAt(batchJob.getCompletedAt())
                .estimatedCompletionTime(estimateCompletionTime(batchJob))
                .build();
    }
    
    @Override
    public BatchKYCResponse getBatchResults(String batchId) {
        BatchKYCJob batchJob = batchJobRepository.findById(batchId)
                .orElseThrow(() -> new BatchProcessingException("Batch job not found: " + batchId));
        
        return BatchKYCResponse.builder()
                .batchId(batchJob.getId())
                .status(batchJob.getStatus())
                .totalUsers(batchJob.getTotalUsers())
                .processedUsers(batchJob.getProcessedUsers())
                .successfulUsers(batchJob.getSuccessfulUsers())
                .failedUsers(batchJob.getFailedUsers())
                .startedAt(batchJob.getStartedAt())
                .completedAt(batchJob.getCompletedAt())
                .results(batchJob.getResults())
                .errors(batchJob.getErrors())
                .build();
    }
    
    @Override
    public void cancelBatch(String batchId) {
        BatchKYCJob batchJob = batchJobRepository.findById(batchId)
                .orElseThrow(() -> new BatchProcessingException("Batch job not found: " + batchId));
        
        if (batchJob.getStatus() != JobStatus.IN_PROGRESS) {
            throw new BatchProcessingException("Cannot cancel batch in status: " + batchJob.getStatus());
        }
        
        batchJob.setStatus(JobStatus.CANCELLED);
        batchJob.setCompletedAt(LocalDateTime.now());
        batchJobRepository.save(batchJob);
        
        log.info("Cancelled batch job: {}", batchId);
    }
    
    @Override
    @Async
    public CompletableFuture<BatchKYCResponse> retryBatch(String batchId, boolean failedOnly) {
        return CompletableFuture.supplyAsync(() -> {
            BatchKYCJob originalJob = batchJobRepository.findById(batchId)
                    .orElseThrow(() -> new BatchProcessingException("Batch job not found: " + batchId));
            
            // Create new batch job for retry
            BatchKYCJob retryJob = new BatchKYCJob();
            retryJob.setOrganizationId(originalJob.getOrganizationId());
            retryJob.setRequestedBy(originalJob.getRequestedBy());
            retryJob.setStatus(JobStatus.IN_PROGRESS);
            retryJob.setStartedAt(LocalDateTime.now());
            retryJob.setParentJobId(originalJob.getId());
            
            List<String> usersToRetry;
            if (failedOnly) {
                usersToRetry = originalJob.getErrors().keySet().stream().collect(Collectors.toList());
            } else {
                usersToRetry = new ArrayList<>(originalJob.getResults().keySet());
            }
            
            retryJob.setTotalUsers(usersToRetry.size());
            batchJobRepository.save(retryJob);
            
            // Process retry
            processRetryBatch(retryJob, originalJob, usersToRetry);
            
            return BatchKYCResponse.builder()
                    .batchId(retryJob.getId())
                    .status(retryJob.getStatus())
                    .totalUsers(retryJob.getTotalUsers())
                    .startedAt(retryJob.getStartedAt())
                    .build();
        });
    }
    
    @Override
    public byte[] exportBatchResults(String batchId) {
        BatchKYCJob batchJob = batchJobRepository.findById(batchId)
                .orElseThrow(() -> new BatchProcessingException("Batch job not found: " + batchId));
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             CSVWriter csvWriter = new CSVWriter(new OutputStreamWriter(baos))) {
            
            // Write headers
            String[] headers = {"User ID", "Status", "Verification ID", "Provider", 
                               "Verification Level", "Completed At", "Error Message"};
            csvWriter.writeNext(headers);
            
            // Write successful results
            for (Map.Entry<String, String> entry : batchJob.getResults().entrySet()) {
                String userId = entry.getKey();
                String verificationId = entry.getValue();
                
                String[] row = {
                    userId,
                    "SUCCESS",
                    verificationId,
                    batchJob.getProvider(),
                    batchJob.getVerificationLevel(),
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    ""
                };
                csvWriter.writeNext(row);
            }
            
            // Write failed results
            for (Map.Entry<String, String> entry : batchJob.getErrors().entrySet()) {
                String userId = entry.getKey();
                String error = entry.getValue();
                
                String[] row = {
                    userId,
                    "FAILED",
                    "",
                    batchJob.getProvider(),
                    batchJob.getVerificationLevel(),
                    "",
                    error
                };
                csvWriter.writeNext(row);
            }
            
            csvWriter.flush();
            return baos.toByteArray();
            
        } catch (IOException e) {
            log.error("Failed to export batch results", e);
            throw new BatchProcessingException("Failed to export batch results");
        }
    }
    
    @Override
    public List<BatchKYCStatus> getOrganizationBatches(String organizationId) {
        List<BatchKYCJob> jobs = batchJobRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId);
        
        return jobs.stream()
                .map(job -> BatchKYCStatus.builder()
                        .batchId(job.getId())
                        .status(job.getStatus())
                        .totalUsers(job.getTotalUsers())
                        .processedUsers(job.getProcessedUsers())
                        .successfulUsers(job.getSuccessfulUsers())
                        .failedUsers(job.getFailedUsers())
                        .startedAt(job.getStartedAt())
                        .completedAt(job.getCompletedAt())
                        .build())
                .collect(Collectors.toList());
    }
    
    @Override
    @Async
    public CompletableFuture<BatchKYCResponse> processCsvBatch(byte[] csvData, String organizationId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<BatchKYCRequest.UserKYCRequest> users = parseCsvData(csvData);
                
                BatchKYCRequest request = BatchKYCRequest.builder()
                        .organizationId(organizationId)
                        .users(users)
                        .notifyOnCompletion(true)
                        .build();
                
                try {
                    return submitBatchVerification(request).get(10, java.util.concurrent.TimeUnit.MINUTES);
                } catch (java.util.concurrent.TimeoutException e) {
                    log.error("CSV batch submission timed out after 10 minutes for organization: {}", organizationId, e);
                    throw new BatchProcessingException("CSV batch submission timed out", e);
                } catch (java.util.concurrent.ExecutionException e) {
                    log.error("CSV batch submission execution failed for organization: {}", organizationId, e.getCause());
                    throw new BatchProcessingException("CSV batch submission failed: " + e.getCause().getMessage(), e.getCause());
                } catch (java.util.concurrent.InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("CSV batch submission interrupted for organization: {}", organizationId, e);
                    throw new BatchProcessingException("CSV batch submission interrupted", e);
                }

            } catch (Exception e) {
                log.error("Failed to process CSV batch", e);
                throw new BatchProcessingException("Failed to process CSV batch: " + e.getMessage());
            }
        });
    }
    
    @Override
    public Map<String, Object> getBatchStatistics(String organizationId) {
        Map<String, Object> stats = new HashMap<>();
        
        List<BatchKYCJob> jobs = batchJobRepository.findByOrganizationId(organizationId);
        
        stats.put("totalBatches", jobs.size());
        stats.put("totalUsersProcessed", jobs.stream().mapToInt(BatchKYCJob::getTotalUsers).sum());
        stats.put("totalSuccessful", jobs.stream().mapToInt(BatchKYCJob::getSuccessfulUsers).sum());
        stats.put("totalFailed", jobs.stream().mapToInt(BatchKYCJob::getFailedUsers).sum());
        
        // Calculate success rate
        long totalProcessed = jobs.stream()
                .filter(j -> j.getStatus() == JobStatus.COMPLETED)
                .mapToInt(BatchKYCJob::getProcessedUsers)
                .sum();
        
        long totalSuccess = jobs.stream()
                .filter(j -> j.getStatus() == JobStatus.COMPLETED)
                .mapToInt(BatchKYCJob::getSuccessfulUsers)
                .sum();
        
        double successRate = totalProcessed > 0 ? (double) totalSuccess / totalProcessed * 100 : 0.0;
        stats.put("successRate", String.format("%.2f%%", successRate));
        
        // Status breakdown
        Map<String, Long> statusCounts = jobs.stream()
                .collect(Collectors.groupingBy(
                        j -> j.getStatus().toString(),
                        Collectors.counting()
                ));
        stats.put("statusBreakdown", statusCounts);
        
        // Recent batches
        List<BatchKYCJob> recentJobs = jobs.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(5)
                .collect(Collectors.toList());
        stats.put("recentBatches", recentJobs);
        
        return stats;
    }
    
    // Helper methods
    
    private BatchKYCJob createBatchJob(BatchKYCRequest request) {
        BatchKYCJob batchJob = new BatchKYCJob();
        batchJob.setOrganizationId(request.getOrganizationId());
        batchJob.setRequestedBy(request.getRequestedBy());
        batchJob.setTotalUsers(request.getUsers().size());
        batchJob.setStatus(JobStatus.IN_PROGRESS);
        batchJob.setStartedAt(LocalDateTime.now());
        batchJob.setProvider(request.getProvider());
        batchJob.setVerificationLevel(request.getVerificationLevel());
        batchJob.setNotifyOnCompletion(request.isNotifyOnCompletion());
        
        return batchJobRepository.save(batchJob);
    }
    
    private CompletableFuture<Void> processBatch(BatchKYCJob batchJob, 
                                                 List<BatchKYCRequest.UserKYCRequest> batch) {
        return CompletableFuture.runAsync(() -> {
            Semaphore semaphore = new Semaphore(MAX_CONCURRENT_VERIFICATIONS);
            List<CompletableFuture<Void>> verificationFutures = new ArrayList<>();
            
            for (BatchKYCRequest.UserKYCRequest user : batch) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        semaphore.acquire();
                        processUserVerification(batchJob, user);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("Interrupted while processing user", e);
                    } finally {
                        semaphore.release();
                    }
                }, batchExecutor);
                
                verificationFutures.add(future);
            }
            
            try {
                CompletableFuture.allOf(verificationFutures.toArray(new CompletableFuture[0]))
                    .get(10, java.util.concurrent.TimeUnit.MINUTES);
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("Batch KYC verification timed out after 10 minutes. Batch size: {}", batch.size(), e);
                verificationFutures.forEach(f -> f.cancel(true));
                throw new RuntimeException("Batch verification timed out", e);
            } catch (java.util.concurrent.ExecutionException e) {
                log.error("Batch KYC verification execution failed. Batch size: {}", batch.size(), e.getCause());
                throw new RuntimeException("Batch verification failed: " + e.getCause().getMessage(), e.getCause());
            } catch (java.util.concurrent.InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Batch KYC verification interrupted. Batch size: {}", batch.size(), e);
                throw new RuntimeException("Batch verification interrupted", e);
            }
        }, batchExecutor);
    }
    
    private void processUserVerification(BatchKYCJob batchJob, BatchKYCRequest.UserKYCRequest user) {
        try {
            KYCVerificationRequest request = KYCVerificationRequest.builder()
                    .userId(user.getUserId())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .email(user.getEmail())
                    .dateOfBirth(user.getDateOfBirth())
                    .country(user.getCountry())
                    .verificationLevel(batchJob.getVerificationLevel())
                    .provider(batchJob.getProvider())
                    .build();
            
            KYCVerificationResponse response;
            try {
                response = verificationService.initiateVerification(request.getUserId(), request)
                    .get(30, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("KYC verification initiation timed out after 30 seconds for user: {}", user.getUserId(), e);
                throw new RuntimeException("KYC verification initiation timed out", e);
            } catch (java.util.concurrent.ExecutionException e) {
                log.error("KYC verification initiation execution failed for user: {}", user.getUserId(), e.getCause());
                throw new RuntimeException("KYC verification initiation failed: " + e.getCause().getMessage(), e.getCause());
            } catch (java.util.concurrent.InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("KYC verification initiation interrupted for user: {}", user.getUserId(), e);
                throw new RuntimeException("KYC verification initiation interrupted", e);
            }

            // Update batch job with success - use atomic operations
            updateBatchJobSuccess(batchJob, user.getUserId(), response.getVerificationId());
            
            log.debug("Successfully processed verification for user: {}", user.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to process verification for user: {}", user.getUserId(), e);
            
            // Update batch job with failure - use atomic operations
            updateBatchJobFailure(batchJob, user.getUserId(), e.getMessage());
        }
    }
    
    private void completeBatchJob(BatchKYCJob batchJob) {
        batchJob.setStatus(JobStatus.COMPLETED);
        batchJob.setCompletedAt(LocalDateTime.now());
        batchJobRepository.save(batchJob);
        
        log.info("Completed batch job: {} - Successful: {}, Failed: {}", 
                batchJob.getId(), batchJob.getSuccessfulUsers(), batchJob.getFailedUsers());
        
        // Send notification if requested
        if (batchJob.isNotifyOnCompletion()) {
            sendBatchCompletionNotification(batchJob);
        }
    }
    
    private void failBatchJob(BatchKYCJob batchJob, String error) {
        batchJob.setStatus(JobStatus.FAILED);
        batchJob.setCompletedAt(LocalDateTime.now());
        batchJob.setErrorMessage(error);
        batchJobRepository.save(batchJob);
        
        log.error("Failed batch job: {} - Error: {}", batchJob.getId(), error);
    }
    
    private void processRetryBatch(BatchKYCJob retryJob, BatchKYCJob originalJob, 
                                   List<String> usersToRetry) {
        // Implementation similar to processBatch but retrieves user data from original job
        log.info("Processing retry batch: {} for {} users", retryJob.getId(), usersToRetry.size());
    }
    
    private List<BatchKYCRequest.UserKYCRequest> parseCsvData(byte[] csvData) throws IOException {
        List<BatchKYCRequest.UserKYCRequest> users = new ArrayList<>();
        
        try (CSVReader reader = new CSVReader(new InputStreamReader(new ByteArrayInputStream(csvData)))) {
            String[] headers = reader.readNext(); // Skip headers
            String[] line;
            
            while ((line = reader.readNext()) != null) {
                if (line.length >= 6) {
                    BatchKYCRequest.UserKYCRequest user = BatchKYCRequest.UserKYCRequest.builder()
                            .userId(line[0])
                            .firstName(line[1])
                            .lastName(line[2])
                            .email(line[3])
                            .dateOfBirth(line[4])
                            .country(line[5])
                            .build();
                    users.add(user);
                }
            }
        }
        
        return users;
    }
    
    private double calculateProgress(BatchKYCJob batchJob) {
        if (batchJob.getTotalUsers() == 0) return 0.0;
        return (double) batchJob.getProcessedUsers() / batchJob.getTotalUsers() * 100;
    }
    
    private LocalDateTime estimateCompletionTime(BatchKYCJob batchJob) {
        if (batchJob.getStatus() != JobStatus.IN_PROGRESS) {
            return batchJob.getCompletedAt();
        }
        
        // Simple estimation based on current progress
        if (batchJob.getProcessedUsers() == 0) {
            log.warn("CRITICAL: Cannot estimate batch completion time - No users processed yet for batch: {}", batchJob.getId());
            // Return a conservative estimate of 1 hour from now
            return LocalDateTime.now().plusHours(1);
        }
        
        long elapsedSeconds = java.time.Duration.between(
                batchJob.getStartedAt(), LocalDateTime.now()).getSeconds();
        double rate = (double) batchJob.getProcessedUsers() / elapsedSeconds;
        long remainingUsers = batchJob.getTotalUsers() - batchJob.getProcessedUsers();
        long estimatedRemainingSeconds = (long) (remainingUsers / rate);
        
        return LocalDateTime.now().plusSeconds(estimatedRemainingSeconds);
    }
    
    private void sendBatchCompletionNotification(BatchKYCJob batchJob) {
        // Send notification via event publisher
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("batchId", batchJob.getId());
        eventData.put("totalUsers", batchJob.getTotalUsers());
        eventData.put("successfulUsers", batchJob.getSuccessfulUsers());
        eventData.put("failedUsers", batchJob.getFailedUsers());
        eventData.put("completedAt", batchJob.getCompletedAt());
        
        eventPublisher.publish("kyc.batch.completed", eventData);
    }
    
    /**
     * Update batch job with success using atomic operations
     */
    @Transactional
    private void updateBatchJobSuccess(BatchKYCJob batchJob, String userId, String verificationId) {
        // Use database-level atomic updates to avoid synchronization
        batchJobRepository.updateSuccess(batchJob.getId(), userId, verificationId);
        
        // Refresh the entity for subsequent operations
        batchJobRepository.findById(batchJob.getId()).ifPresent(updatedJob -> {
            batchJob.setProcessedUsers(updatedJob.getProcessedUsers());
            batchJob.setSuccessfulUsers(updatedJob.getSuccessfulUsers());
            batchJob.getResults().putAll(updatedJob.getResults());
        });
    }
    
    /**
     * Update batch job with failure using atomic operations
     */
    @Transactional
    private void updateBatchJobFailure(BatchKYCJob batchJob, String userId, String errorMessage) {
        // Use database-level atomic updates to avoid synchronization
        batchJobRepository.updateFailure(batchJob.getId(), userId, errorMessage);
        
        // Refresh the entity for subsequent operations
        batchJobRepository.findById(batchJob.getId()).ifPresent(updatedJob -> {
            batchJob.setProcessedUsers(updatedJob.getProcessedUsers());
            batchJob.setFailedUsers(updatedJob.getFailedUsers());
            batchJob.getErrors().putAll(updatedJob.getErrors());
        });
    }
}