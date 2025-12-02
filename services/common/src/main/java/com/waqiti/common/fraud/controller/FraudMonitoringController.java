package com.waqiti.common.fraud.controller;

import com.waqiti.common.fraud.RealTimeFraudMonitoringService;
import com.waqiti.common.fraud.alert.FraudAlertService;
import com.waqiti.common.fraud.alert.FraudAlertStatistics;
import com.waqiti.common.fraud.dto.*;
import com.waqiti.common.fraud.mapper.FraudMapper;
import com.waqiti.common.fraud.model.FraudPatternType;
import com.waqiti.common.security.ValidateOwnership;
import com.waqiti.common.security.ResourceType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.concurrent.CompletableFuture;

/**
 * REST controller for fraud monitoring and management operations
 * Provides endpoints for fraud analysts and system administrators
 */
@RestController
@RequestMapping("/api/v1/fraud")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Fraud Monitoring", description = "Real-time fraud detection and monitoring APIs")
public class FraudMonitoringController {

    private final RealTimeFraudMonitoringService fraudMonitoringService;
    private final FraudAlertService fraudAlertService;
    private final FraudMapper fraudMapper;
    
    /**
     * Manual fraud analysis for specific transaction
     */
    @PostMapping("/analyze/{transactionId}")
    @Operation(summary = "Analyze transaction for fraud", 
               description = "Perform manual fraud analysis on a specific transaction")
    @PreAuthorize("hasRole('FRAUD_ANALYST') or hasRole('ADMIN')")
    @ValidateOwnership(resourceType = ResourceType.TRANSACTION, resourceIdParam = "transactionId", operation = "ANALYZE")
    public ResponseEntity<CompletableFuture<FraudAnalysisResult>> analyzeTransaction(
            @Parameter(description = "Transaction ID to analyze")
            @PathVariable String transactionId,
            @Valid @RequestBody FraudAnalysisRequest request) {

        log.info("Manual fraud analysis requested for transaction: {} by user: {}",
            transactionId, request.getAnalystId());

        // Convert DTO to Model using mapper
        com.waqiti.common.fraud.model.TransactionEvent event = fraudMapper.toModel(
            TransactionEvent.builder()
                .transactionId(transactionId)
                .userId(request.getUserId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .transactionType(request.getTransactionType())
                .merchantId(request.getMerchantId())
                .ipAddress(request.getIpAddress())
                .deviceId(request.getDeviceId())
                .userAgent(request.getUserAgent())
                .location(request.getLocation())
                .timestamp(request.getTimestamp())
                .build()
        );

        // Service returns model, convert to DTO for API response
        CompletableFuture<com.waqiti.common.fraud.FraudAnalysisResult> modelResult =
            fraudMonitoringService.analyzeTransaction(event);
        CompletableFuture<FraudAnalysisResult> dtoResult =
            modelResult.thenApply(fraudMapper::toDto);

        return ResponseEntity.ok(dtoResult);
    }
    
    /**
     * Get fraud analysis result
     */
    @GetMapping("/analysis/{transactionId}")
    @Operation(summary = "Get fraud analysis result", 
               description = "Retrieve fraud analysis result for a transaction")
    @PreAuthorize("hasRole('FRAUD_ANALYST') or hasRole('ADMIN')")
    @ValidateOwnership(resourceType = ResourceType.TRANSACTION, resourceIdParam = "transactionId", operation = "VIEW")
    public ResponseEntity<FraudAnalysisResult> getFraudAnalysis(
            @Parameter(description = "Transaction ID") 
            @PathVariable String transactionId) {
        
        // In production, this would query a fraud analysis repository
        log.debug("Retrieving fraud analysis for transaction: {}", transactionId);
        
        // For now, return a sample response
        return ResponseEntity.ok(FraudAnalysisResult.builder()
            .transactionId(transactionId)
            .riskLevel(FraudRiskLevel.LOW)
            .build());
    }
    
    /**
     * Get current fraud alerts
     */
    @GetMapping("/alerts")
    @Operation(summary = "Get fraud alerts", 
               description = "Retrieve current fraud alerts with pagination")
    @PreAuthorize("hasRole('FRAUD_ANALYST') or hasRole('ADMIN')")
    public ResponseEntity<FraudAlertStatistics> getFraudAlerts() {
        
        log.debug("Retrieving fraud alert statistics");
        FraudAlertStatistics stats = fraudAlertService.getAlertStatistics();
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Acknowledge fraud alert
     */
    @PostMapping("/alerts/{alertId}/acknowledge")
    @Operation(summary = "Acknowledge fraud alert", 
               description = "Acknowledge a fraud alert to stop escalation")
    @PreAuthorize("hasRole('FRAUD_ANALYST') or hasRole('ADMIN')")
    public ResponseEntity<Void> acknowledgeAlert(
            @Parameter(description = "Alert ID to acknowledge") 
            @PathVariable String alertId,
            @Valid @RequestBody AlertAcknowledgmentRequest request) {
        
        log.info("Acknowledging fraud alert: {} by {}", alertId, request.getAcknowledgedBy());
        
        fraudAlertService.acknowledgeAlert(alertId, request.getAcknowledgedBy());
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Resolve fraud alert
     */
    @PostMapping("/alerts/{alertId}/resolve")
    @Operation(summary = "Resolve fraud alert", 
               description = "Resolve a fraud alert with resolution details")
    @PreAuthorize("hasRole('FRAUD_ANALYST') or hasRole('ADMIN')")
    public ResponseEntity<Void> resolveAlert(
            @Parameter(description = "Alert ID to resolve") 
            @PathVariable String alertId,
            @Valid @RequestBody AlertResolutionRequest request) {
        
        log.info("Resolving fraud alert: {} by {} - {}", 
            alertId, request.getResolvedBy(), request.getResolution());
        
        fraudAlertService.resolveAlert(alertId, request.getResolvedBy(), request.getResolution());
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Get fraud monitoring statistics
     */
    @GetMapping("/statistics")
    @Operation(summary = "Get fraud monitoring statistics", 
               description = "Retrieve comprehensive fraud monitoring statistics")
    @PreAuthorize("hasRole('FRAUD_ANALYST') or hasRole('ADMIN')")
    public ResponseEntity<FraudMonitoringStatistics> getFraudStatistics(
            @Parameter(description = "Time period for statistics (days)") 
            @RequestParam(defaultValue = "7") int days) {
        
        log.debug("Retrieving fraud statistics for {} days", days);
        
        // In production, this would aggregate actual statistics
        FraudMonitoringStatistics stats = FraudMonitoringStatistics.builder()
            .totalTransactionsAnalyzed(150000L)
            .fraudDetected(45L)
            .falsePositives(12L)
            .averageFraudScore(0.15)
            .highRiskTransactions(125L)
            .blockedTransactions(33L)
            .build();
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Get user risk profile
     */
    @GetMapping("/users/{userId}/risk-profile")
    @Operation(summary = "Get user risk profile", 
               description = "Retrieve risk profile for a specific user")
    @PreAuthorize("hasRole('FRAUD_ANALYST') or hasRole('ADMIN')")
    @ValidateOwnership(resourceType = ResourceType.RISK_PROFILE, resourceIdParam = "userId", operation = "VIEW")
    public ResponseEntity<UserRiskProfile> getUserRiskProfile(
            @Parameter(description = "User ID") 
            @PathVariable String userId) {
        
        log.debug("Retrieving risk profile for user: {}", userId);
        
        // In production, this would query user risk profile service
        UserRiskProfile profile = UserRiskProfile.createDefault(userId);
        
        return ResponseEntity.ok(profile);
    }
    
    /**
     * Update user risk profile
     */
    @PutMapping("/users/{userId}/risk-profile")
    @Operation(summary = "Update user risk profile", 
               description = "Update risk profile for a specific user")
    @PreAuthorize("hasRole('FRAUD_MANAGER') or hasRole('ADMIN')")
    @ValidateOwnership(resourceType = ResourceType.RISK_PROFILE, resourceIdParam = "userId", operation = "UPDATE")
    public ResponseEntity<Void> updateUserRiskProfile(
            @Parameter(description = "User ID") 
            @PathVariable String userId,
            @Valid @RequestBody UserRiskProfileUpdateRequest request) {
        
        log.info("Updating risk profile for user: {} by {}", userId, request.getUpdatedBy());
        
        // In production, this would update the user risk profile
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Get fraud patterns
     */
    @GetMapping("/patterns")
    @Operation(summary = "Get fraud patterns", 
               description = "Retrieve detected fraud patterns")
    @PreAuthorize("hasRole('FRAUD_ANALYST') or hasRole('ADMIN')")
    public ResponseEntity<FraudPatternSummary> getFraudPatterns(
            @Parameter(description = "Pattern type filter") 
            @RequestParam(required = false) FraudPatternType patternType,
            @Parameter(description = "Time period (days)") 
            @RequestParam(defaultValue = "30") int days) {
        
        log.debug("Retrieving fraud patterns for type: {} and {} days", patternType, days);
        
        // In production, this would query pattern detection service
        FraudPatternSummary summary = FraudPatternSummary.builder()
            .totalPatterns(25L)
            .cardTestingPatterns(8L)
            .accountTakeoverPatterns(5L)
            .moneyLaunderingPatterns(3L)
            .syntheticIdentityPatterns(9L)
            .build();
        
        return ResponseEntity.ok(summary);
    }
    
    /**
     * Whitelist entity (reduce fraud scoring)
     */
    @PostMapping("/whitelist")
    @Operation(summary = "Add entity to whitelist", 
               description = "Add user, merchant, or IP to fraud whitelist")
    @PreAuthorize("hasRole('FRAUD_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<Void> addToWhitelist(
            @Valid @RequestBody WhitelistRequest request) {
        
        log.info("Adding to whitelist: {} = {} by {}", 
            request.getEntityType(), request.getEntityValue(), request.getAddedBy());
        
        // In production, this would update whitelist service
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Blacklist entity (increase fraud scoring)
     */
    @PostMapping("/blacklist")
    @Operation(summary = "Add entity to blacklist", 
               description = "Add user, merchant, or IP to fraud blacklist")
    @PreAuthorize("hasRole('FRAUD_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<Void> addToBlacklist(
            @Valid @RequestBody BlacklistRequest request) {
        
        log.warn("Adding to blacklist: {} = {} by {} - Reason: {}", 
            request.getEntityType(), request.getEntityValue(), 
            request.getAddedBy(), request.getReason());
        
        // In production, this would update blacklist service
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Model feedback endpoint
     */
    @PostMapping("/model/feedback")
    @Operation(summary = "Provide model feedback", 
               description = "Provide feedback to improve fraud detection model")
    @PreAuthorize("hasRole('FRAUD_ANALYST') or hasRole('ADMIN')")
    public ResponseEntity<Void> provideModelFeedback(
            @Valid @RequestBody ModelFeedbackRequest request) {
        
        log.info("Providing model feedback for transaction: {} - isFraud: {}", 
            request.getTransactionId(), request.isActualFraud());
        
        fraudMonitoringService.getFraudScoringEngine().updateModelWithFeedback(
            request.getTransactionId(), 
            request.isActualFraud(), 
            request.getActualLoss()
        );
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Get fraud monitoring health status
     */
    @GetMapping("/health")
    @Operation(summary = "Get fraud monitoring health", 
               description = "Check health status of fraud monitoring components")
    @PreAuthorize("hasRole('FRAUD_ANALYST') or hasRole('ADMIN')")
    public ResponseEntity<FraudMonitoringHealth> getHealth() {
        
        // In production, this would check component health
        FraudMonitoringHealth health = FraudMonitoringHealth.builder()
            .overallStatus(HealthStatus.HEALTHY)
            .mlModelStatus(HealthStatus.HEALTHY)
            .alertServiceStatus(HealthStatus.HEALTHY)
            .kafkaStatus(HealthStatus.HEALTHY)
            .lastHealthCheck(java.time.LocalDateTime.now())
            .build();
        
        return ResponseEntity.ok(health);
    }
}