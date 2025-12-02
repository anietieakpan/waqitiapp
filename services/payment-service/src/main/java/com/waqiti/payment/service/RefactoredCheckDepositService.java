package com.waqiti.payment.service.check;

import com.waqiti.payment.dto.*;
import com.waqiti.payment.entity.CheckDeposit;
import com.waqiti.payment.repository.CheckDepositRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Refactored Check Deposit Service - Orchestrator
 * Replaces monolithic CheckDepositService.java (1,436 LOC)
 * 
 * This service now orchestrates the specialized services:
 * - CheckImageProcessingService
 * - CheckFraudDetectionService 
 * - CheckValidationService
 * - CheckDepositProcessingService
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RefactoredCheckDepositService {

    private final CheckImageProcessingService imageProcessingService;
    private final CheckFraudDetectionService fraudDetectionService;
    private final CheckValidationService validationService;
    private final CheckDepositProcessingService processingService;
    private final CheckDepositRepository checkDepositRepository;

    /**
     * Main check deposit processing method - orchestrates all sub-services
     */
    @Transactional
    public CompletableFuture<CheckDepositResponse> initiateCheckDeposit(CheckDepositRequest request) {
        log.info("Initiating check deposit: depositId={}, amount={}", 
                request.getDepositId(), request.getAmount());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Basic validation
                validationService.validateRequest(request);
                
                // 2. Process images (async)
                CompletableFuture<CheckImageAnalysisResult> imageAnalysis = 
                    imageProcessingService.processCheckImages(request);
                
                // 3. Fraud detection (async)
                CompletableFuture<CheckFraudAnalysisResult> fraudAnalysis = 
                    fraudDetectionService.analyzeFraud(request);
                
                // 4. Wait for both analyses to complete
                CheckImageAnalysisResult imageResult = imageAnalysis.join();
                CheckFraudAnalysisResult fraudResult = fraudAnalysis.join();
                
                // 5. Validate results
                if (!imageResult.isProcessingSuccessful()) {
                    return CheckDepositResponse.failed(
                        request.getDepositId(), 
                        "Image processing failed: " + imageResult.getErrorMessage()
                    );
                }
                
                // 6. Make deposit decision
                CheckDepositDecision decision = makeDepositDecision(imageResult, fraudResult);
                
                // 7. Process based on decision
                return processingService.processDeposit(request, imageResult, fraudResult, decision);
                
            } catch (Exception e) {
                log.error("Check deposit initiation failed: ", e);
                return CheckDepositResponse.failed(
                    request.getDepositId(), 
                    "Deposit processing failed: " + e.getMessage()
                );
            }
        });
    }

    /**
     * Get deposit status
     */
    public CheckDepositStatusResponse getCheckDepositStatus(UUID depositId) {
        log.info("Getting check deposit status: {}", depositId);
        
        CheckDeposit deposit = checkDepositRepository.findById(depositId)
            .orElseThrow(() -> new IllegalArgumentException("Deposit not found: " + depositId));
        
        return CheckDepositStatusResponse.builder()
            .depositId(depositId)
            .status(deposit.getStatus())
            .amount(deposit.getAmount())
            .createdAt(deposit.getCreatedAt())
            .processedAt(deposit.getProcessedAt())
            .statusMessage(deposit.getStatusMessage())
            .availableDate(deposit.getAvailableDate())
            .build();
    }

    /**
     * Manual approval for deposits requiring review
     */
    @Transactional
    public void manuallyApproveDeposit(UUID depositId, String approverNotes) {
        log.info("Manually approving deposit: {} with notes: {}", depositId, approverNotes);
        
        CheckDeposit deposit = checkDepositRepository.findById(depositId)
            .orElseThrow(() -> new IllegalArgumentException("Deposit not found: " + depositId));
        
        processingService.approveDeposit(deposit, approverNotes);
    }

    /**
     * Manual rejection for deposits
     */
    @Transactional
    public void manuallyRejectDeposit(UUID depositId, String reason) {
        log.info("Manually rejecting deposit: {} with reason: {}", depositId, reason);
        
        CheckDeposit deposit = checkDepositRepository.findById(depositId)
            .orElseThrow(() -> new IllegalArgumentException("Deposit not found: " + depositId));
        
        processingService.rejectDeposit(deposit, reason);
    }

    /**
     * Handle webhook notifications from check processing providers
     */
    public void handleCheckStatusWebhook(CheckWebhookRequest webhook) {
        log.info("Handling check status webhook: depositId={}, status={}", 
                webhook.getDepositId(), webhook.getStatus());
        
        processingService.handleWebhookUpdate(webhook);
    }

    /**
     * Process expired holds
     */
    @Transactional
    public void processExpiredHolds() {
        log.info("Processing expired check deposit holds");
        processingService.processExpiredHolds();
    }

    /**
     * Make decision on deposit based on analysis results
     */
    private CheckDepositDecision makeDepositDecision(CheckImageAnalysisResult imageResult, 
                                                   CheckFraudAnalysisResult fraudResult) {
        
        // Auto-reject if fraud score is critical
        if (fraudResult.getRiskScore().getLevel() == CheckFraudDetectionService.RiskLevel.CRITICAL) {
            return CheckDepositDecision.REJECT;
        }
        
        // Manual review if high risk or low image confidence
        if (fraudResult.getRiskScore().getLevel() == CheckFraudDetectionService.RiskLevel.HIGH ||
            imageResult.getAmountConfidence() < 0.8) {
            return CheckDepositDecision.MANUAL_REVIEW;
        }
        
        // Auto-approve for low risk
        if (fraudResult.getRiskScore().getLevel() == CheckFraudDetectionService.RiskLevel.LOW &&
            imageResult.getAmountConfidence() > 0.9) {
            return CheckDepositDecision.AUTO_APPROVE;
        }
        
        // Default to manual review
        return CheckDepositDecision.MANUAL_REVIEW;
    }
    
    public enum CheckDepositDecision {
        AUTO_APPROVE, MANUAL_REVIEW, REJECT
    }
}