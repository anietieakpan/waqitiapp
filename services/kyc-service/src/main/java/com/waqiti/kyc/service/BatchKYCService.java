package com.waqiti.kyc.service;

import com.waqiti.kyc.dto.request.BatchKYCRequest;
import com.waqiti.kyc.dto.response.BatchKYCResponse;
import com.waqiti.kyc.dto.response.BatchKYCStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for handling batch KYC verification operations
 */
public interface BatchKYCService {
    
    /**
     * Submit a batch of users for KYC verification
     * @param request The batch KYC request
     * @return The batch job response
     */
    CompletableFuture<BatchKYCResponse> submitBatchVerification(BatchKYCRequest request);
    
    /**
     * Get the status of a batch verification job
     * @param batchId The batch job ID
     * @return The batch status
     */
    BatchKYCStatus getBatchStatus(String batchId);
    
    /**
     * Get detailed results for a batch verification job
     * @param batchId The batch job ID
     * @return The batch results
     */
    BatchKYCResponse getBatchResults(String batchId);
    
    /**
     * Cancel a batch verification job
     * @param batchId The batch job ID
     */
    void cancelBatch(String batchId);
    
    /**
     * Retry failed verifications in a batch
     * @param batchId The batch job ID
     * @param failedOnly Whether to retry only failed verifications
     * @return New batch job response
     */
    CompletableFuture<BatchKYCResponse> retryBatch(String batchId, boolean failedOnly);
    
    /**
     * Export batch results to CSV
     * @param batchId The batch job ID
     * @return CSV data as byte array
     */
    byte[] exportBatchResults(String batchId);
    
    /**
     * Get all batch jobs for an organization
     * @param organizationId The organization ID
     * @return List of batch jobs
     */
    List<BatchKYCStatus> getOrganizationBatches(String organizationId);
    
    /**
     * Process CSV file for batch KYC verification
     * @param csvData The CSV file data
     * @param organizationId The organization ID
     * @return The batch job response
     */
    CompletableFuture<BatchKYCResponse> processCsvBatch(byte[] csvData, String organizationId);
    
    /**
     * Get batch processing statistics
     * @param organizationId The organization ID
     * @return Batch processing statistics
     */
    Map<String, Object> getBatchStatistics(String organizationId);
}