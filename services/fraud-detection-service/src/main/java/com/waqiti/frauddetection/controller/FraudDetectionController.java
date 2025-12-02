package com.waqiti.frauddetection.controller;

import com.waqiti.frauddetection.dto.*;
import com.waqiti.frauddetection.entity.FraudIncident;
import com.waqiti.frauddetection.entity.FraudRule;
import com.waqiti.frauddetection.service.FraudDetectionService;
import com.waqiti.frauddetection.service.FraudRuleManagementService;
import com.waqiti.frauddetection.service.FraudAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Fraud Detection and Risk Management
 * Provides comprehensive API for fraud checking, rule management, and analytics
 */
@RestController
@RequestMapping("/api/v1/fraud")
@Tag(name = "Fraud Detection", description = "APIs for fraud detection and risk management")
@RequiredArgsConstructor
@Validated
@Slf4j
public class FraudDetectionController {

    private final FraudDetectionService fraudDetectionService;
    private final FraudRuleManagementService ruleManagementService;
    private final FraudAnalyticsService analyticsService;

    /**
     * Perform fraud check on a transaction
     */
    @PostMapping("/check")
    @Operation(summary = "Check transaction for fraud", description = "Performs comprehensive fraud analysis on a transaction")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Fraud check completed"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "500", description = "Internal fraud detection error")
    })
    @PreAuthorize("hasRole('SYSTEM') or hasRole('PAYMENT_PROCESSOR')")
    public ResponseEntity<FraudCheckResponse> checkTransaction(
            @Valid @RequestBody FraudCheckRequest request) {
        
        log.info("Fraud check request for transaction: {} by user: {}", 
                request.getTransactionId(), request.getUserId());
        
        try {
            FraudCheckResponse response = fraudDetectionService.checkTransaction(request);
            
            log.info("Fraud check completed for transaction: {} - Risk Level: {}, Score: {}", 
                    request.getTransactionId(), response.getRiskLevel(), response.getRiskScore());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error performing fraud check for transaction: {}", request.getTransactionId(), e);
            
            // Return a safe default response
            FraudCheckResponse errorResponse = FraudCheckResponse.builder()
                    .transactionId(request.getTransactionId())
                    .riskLevel(RiskLevel.HIGH)
                    .riskScore(1.0)
                    .approved(false)
                    .requiresAdditionalVerification(true)
                    .errorMessage("Fraud detection service error - transaction blocked for safety")
                    .timestamp(LocalDateTime.now())
                    .build();
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get user risk profile
     */
    @GetMapping("/risk-profile/{userId}")
    @Operation(summary = "Get user risk profile", description = "Retrieves comprehensive risk profile for a user")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FRAUD_ANALYST') or (#userId == authentication.principal.id)")
    public ResponseEntity<UserRiskProfile> getUserRiskProfile(
            @PathVariable @NotBlank String userId) {
        
        log.debug("Fetching risk profile for user: {}", userId);
        UserRiskProfile profile = fraudDetectionService.getUserRiskProfile(userId);
        return ResponseEntity.ok(profile);
    }

    /**
     * Get fraud incidents for a user
     */
    @GetMapping("/incidents/user/{userId}")
    @Operation(summary = "Get user fraud incidents", description = "Retrieves fraud incidents for a specific user")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FRAUD_ANALYST')")
    public ResponseEntity<Page<FraudIncidentDTO>> getUserFraudIncidents(
            @PathVariable @NotBlank String userId,
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Pageable pageable) {
        
        log.debug("Fetching fraud incidents for user: {} with risk level: {}", userId, riskLevel);
        
        FraudIncidentFilter filter = FraudIncidentFilter.builder()
                .userId(userId)
                .riskLevel(riskLevel)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        
        Page<FraudIncidentDTO> incidents = analyticsService.getFraudIncidents(filter, pageable);
        return ResponseEntity.ok(incidents);
    }

    /**
     * Get all fraud incidents (admin only)
     */
    @GetMapping("/incidents")
    @Operation(summary = "Get all fraud incidents", description = "Retrieves all fraud incidents with filtering")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FRAUD_ANALYST')")
    public ResponseEntity<Page<FraudIncidentDTO>> getAllFraudIncidents(
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(required = false) Boolean blocked,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String searchTerm,
            Pageable pageable) {
        
        log.debug("Fetching all fraud incidents with risk level: {}, blocked: {}", riskLevel, blocked);
        
        FraudIncidentFilter filter = FraudIncidentFilter.builder()
                .riskLevel(riskLevel)
                .blocked(blocked)
                .startDate(startDate)
                .endDate(endDate)
                .searchTerm(searchTerm)
                .build();
        
        Page<FraudIncidentDTO> incidents = analyticsService.getFraudIncidents(filter, pageable);
        return ResponseEntity.ok(incidents);
    }

    /**
     * Get fraud analytics dashboard data
     */
    @GetMapping("/analytics/dashboard")
    @Operation(summary = "Get fraud analytics dashboard", description = "Retrieves comprehensive fraud analytics data")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FRAUD_ANALYST')")
    public ResponseEntity<FraudAnalyticsDashboard> getFraudDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        log.debug("Fetching fraud analytics dashboard from {} to {}", startDate, endDate);
        
        if (startDate == null) {
            startDate = LocalDateTime.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }
        
        FraudAnalyticsDashboard dashboard = analyticsService.getFraudDashboard(startDate, endDate);
        return ResponseEntity.ok(dashboard);
    }

    /**
     * Create fraud rule
     */
    @PostMapping("/rules")
    @Operation(summary = "Create fraud rule", description = "Creates a new fraud detection rule")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Fraud rule created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid rule data"),
        @ApiResponse(responseCode = "409", description = "Rule with same name already exists")
    })
    @PreAuthorize("hasRole('ADMIN') or hasRole('FRAUD_MANAGER')")
    public ResponseEntity<FraudRuleDTO> createFraudRule(
            @Valid @RequestBody CreateFraudRuleRequest request,
            @RequestHeader("X-User-ID") String adminId) {
        
        log.info("Creating fraud rule: {} by admin: {}", request.getName(), adminId);
        
        request.setCreatedBy(adminId);
        FraudRuleDTO rule = ruleManagementService.createFraudRule(request);
        
        log.info("Fraud rule created successfully with ID: {}", rule.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(rule);
    }

    /**
     * Get all fraud rules
     */
    @GetMapping("/rules")
    @Operation(summary = "Get fraud rules", description = "Retrieves all fraud detection rules")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FRAUD_MANAGER') or hasRole('FRAUD_ANALYST')")
    public ResponseEntity<Page<FraudRuleDTO>> getFraudRules(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) String severity,
            Pageable pageable) {
        
        log.debug("Fetching fraud rules with active: {}, type: {}", active, ruleType);
        
        FraudRuleFilter filter = FraudRuleFilter.builder()
                .active(active)
                .ruleType(ruleType)
                .severity(severity)
                .build();
        
        Page<FraudRuleDTO> rules = ruleManagementService.getFraudRules(filter, pageable);
        return ResponseEntity.ok(rules);
    }

    /**
     * Update fraud rule
     */
    @PutMapping("/rules/{ruleId}")
    @Operation(summary = "Update fraud rule", description = "Updates an existing fraud detection rule")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FRAUD_MANAGER')")
    public ResponseEntity<FraudRuleDTO> updateFraudRule(
            @PathVariable @NotBlank String ruleId,
            @Valid @RequestBody UpdateFraudRuleRequest request,
            @RequestHeader("X-User-ID") String adminId) {
        
        log.info("Updating fraud rule: {} by admin: {}", ruleId, adminId);
        
        request.setRuleId(ruleId);
        request.setUpdatedBy(adminId);
        
        FraudRuleDTO updatedRule = ruleManagementService.updateFraudRule(request);
        
        log.info("Fraud rule updated successfully: {}", ruleId);
        return ResponseEntity.ok(updatedRule);
    }

    /**
     * Delete fraud rule
     */
    @DeleteMapping("/rules/{ruleId}")
    @Operation(summary = "Delete fraud rule", description = "Deletes a fraud detection rule")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FRAUD_MANAGER')")
    public ResponseEntity<Void> deleteFraudRule(
            @PathVariable @NotBlank String ruleId,
            @RequestHeader("X-User-ID") String adminId) {
        
        log.info("Deleting fraud rule: {} by admin: {}", ruleId, adminId);
        
        ruleManagementService.deleteFraudRule(ruleId, adminId);
        
        log.info("Fraud rule deleted successfully: {}", ruleId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Test fraud rule
     */
    @PostMapping("/rules/{ruleId}/test")
    @Operation(summary = "Test fraud rule", description = "Tests a fraud rule against sample data")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FRAUD_MANAGER')")
    public ResponseEntity<FraudRuleTestResult> testFraudRule(
            @PathVariable @NotBlank String ruleId,
            @Valid @RequestBody FraudCheckRequest testRequest) {
        
        log.info("Testing fraud rule: {} with transaction: {}", ruleId, testRequest.getTransactionId());
        
        FraudRuleTestResult result = ruleManagementService.testFraudRule(ruleId, testRequest);
        return ResponseEntity.ok(result);
    }

    /**
     * Approve/reject fraud incident (manual review)
     */
    @PostMapping("/incidents/{incidentId}/review")
    @Operation(summary = "Review fraud incident", description = "Manually approve or reject a fraud incident")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FRAUD_ANALYST')")
    public ResponseEntity<FraudIncidentDTO> reviewFraudIncident(
            @PathVariable @NotBlank String incidentId,
            @Valid @RequestBody FraudIncidentReviewRequest request,
            @RequestHeader("X-User-ID") String reviewerId) {
        
        log.info("Reviewing fraud incident: {} by reviewer: {}", incidentId, reviewerId);
        
        request.setIncidentId(incidentId);
        request.setReviewedBy(reviewerId);
        
        FraudIncidentDTO reviewedIncident = analyticsService.reviewFraudIncident(request);
        
        log.info("Fraud incident reviewed: {} - Decision: {}", incidentId, request.getDecision());
        return ResponseEntity.ok(reviewedIncident);
    }

    /**
     * Get fraud detection statistics
     */
    @GetMapping("/statistics")
    @Operation(summary = "Get fraud statistics", description = "Retrieves fraud detection statistics")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FRAUD_ANALYST')")
    public ResponseEntity<FraudStatistics> getFraudStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        log.debug("Fetching fraud statistics from {} to {}", startDate, endDate);
        
        if (startDate == null) {
            startDate = LocalDateTime.now().minusDays(7);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }
        
        FraudStatistics statistics = analyticsService.getFraudStatistics(startDate, endDate);
        return ResponseEntity.ok(statistics);
    }

    /**
     * Bulk update fraud rules
     */
    @PutMapping("/rules/bulk")
    @Operation(summary = "Bulk update fraud rules", description = "Update multiple fraud rules at once")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FRAUD_MANAGER')")
    public ResponseEntity<BulkFraudRuleUpdateResult> bulkUpdateFraudRules(
            @Valid @RequestBody BulkFraudRuleUpdateRequest request,
            @RequestHeader("X-User-ID") String adminId) {
        
        log.info("Bulk updating {} fraud rules by admin: {}", 
                request.getRuleIds().size(), adminId);
        
        request.setUpdatedBy(adminId);
        BulkFraudRuleUpdateResult result = ruleManagementService.bulkUpdateFraudRules(request);
        
        log.info("Bulk fraud rule update completed. Success: {}, Failed: {}", 
                result.getSuccessCount(), result.getFailureCount());
        
        return ResponseEntity.ok(result);
    }

    /**
     * Export fraud data
     */
    @GetMapping("/export")
    @Operation(summary = "Export fraud data", description = "Export fraud incidents and analytics data")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FRAUD_ANALYST')")
    public ResponseEntity<byte[]> exportFraudData(
            @RequestParam(defaultValue = "CSV") String format,
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        log.info("Exporting fraud data in {} format", format);
        
        FraudDataExportRequest exportRequest = FraudDataExportRequest.builder()
                .format(format)
                .riskLevel(riskLevel)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        
        byte[] exportData = analyticsService.exportFraudData(exportRequest);
        
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=fraud-data." + format.toLowerCase())
                .body(exportData);
    }

    /**
     * Retrain ML model
     */
    @PostMapping("/ml/retrain")
    @Operation(summary = "Retrain ML model", description = "Trigger ML model retraining with latest data")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ML_ENGINEER')")
    public ResponseEntity<MLModelRetrainResult> retrainMLModel(
            @RequestParam(required = false, defaultValue = "1000") int sampleSize,
            @RequestHeader("X-User-ID") String adminId) {
        
        log.info("Triggering ML model retraining with sample size: {} by admin: {}", sampleSize, adminId);
        
        MLModelRetrainResult result = analyticsService.retrainMLModel(sampleSize, adminId);
        
        log.info("ML model retraining completed. Success: {}, Accuracy: {}", 
                result.isSuccess(), result.getNewAccuracy());
        
        return ResponseEntity.ok(result);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if fraud detection service is running")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = Map.of(
                "status", "UP",
                "service", "fraud-detection-service",
                "timestamp", LocalDateTime.now().toString(),
                "components", Map.of(
                    "fraudDetectionService", "UP",
                    "ruleManagementService", "UP",
                    "analyticsService", "UP",
                    "mlModel", "UP"
                )
        );
        
        return ResponseEntity.ok(health);
    }

    /**
     * Get real-time fraud metrics
     */
    @GetMapping("/metrics/realtime")
    @Operation(summary = "Get real-time fraud metrics", description = "Get current fraud detection metrics")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FRAUD_ANALYST')")
    public ResponseEntity<RealtimeFraudMetrics> getRealtimeFraudMetrics() {
        log.debug("Fetching real-time fraud metrics");
        
        RealtimeFraudMetrics metrics = analyticsService.getRealtimeFraudMetrics();
        return ResponseEntity.ok(metrics);
    }

    /**
     * Update fraud incident status
     */
    @PatchMapping("/incidents/{incidentId}/status")
    @Operation(summary = "Update incident status", description = "Update the status of a fraud incident")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FRAUD_ANALYST')")
    public ResponseEntity<FraudIncidentDTO> updateIncidentStatus(
            @PathVariable @NotBlank String incidentId,
            @RequestParam @NotBlank String status,
            @RequestParam(required = false) String reason,
            @RequestHeader("X-User-ID") String updatedBy) {
        
        log.info("Updating fraud incident {} status to {} by user: {}", incidentId, status, updatedBy);
        
        UpdateIncidentStatusRequest request = UpdateIncidentStatusRequest.builder()
                .incidentId(incidentId)
                .status(status)
                .reason(reason)
                .updatedBy(updatedBy)
                .build();
        
        FraudIncidentDTO updatedIncident = analyticsService.updateIncidentStatus(request);
        
        log.info("Fraud incident status updated: {} -> {}", incidentId, status);
        return ResponseEntity.ok(updatedIncident);
    }

    // ============================================================================
    // PAYMENT SERVICE INTEGRATION ENDPOINTS
    // These endpoints are called by payment-service via FraudDetectionServiceClient
    // ============================================================================

    /**
     * Assess fraud risk for general payment
     * Called by: payment-service/FraudDetectionServiceClient.assessFraudRisk()
     */
    @PostMapping("/assess")
    @Operation(summary = "Assess fraud risk", description = "General fraud risk assessment for payments")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('PAYMENT_PROCESSOR')")
    public ResponseEntity<FraudCheckResponse> assessFraudRisk(
            @Valid @RequestBody FraudCheckRequest request) {

        log.info("Fraud assessment request for transaction: {}", request.getTransactionId());

        // Delegate to main fraud check method
        return checkTransaction(request);
    }

    /**
     * Assess fraud risk for NFC payment
     * Called by: payment-service for NFC payment flows
     */
    @PostMapping("/assess/nfc")
    @Operation(summary = "Assess NFC payment fraud", description = "NFC-specific fraud risk assessment")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('PAYMENT_PROCESSOR')")
    public ResponseEntity<FraudCheckResponse> checkNFCPayment(
            @Valid @RequestBody FraudCheckRequest request) {

        log.info("NFC fraud check for transaction: {}", request.getTransactionId());

        // Add NFC-specific fraud checks
        FraudCheckResponse response = fraudDetectionService.checkTransaction(request);

        return ResponseEntity.ok(response);
    }

    /**
     * Assess fraud risk for P2P transfer
     * Called by: payment-service for P2P payment flows
     */
    @PostMapping("/assess/p2p")
    @Operation(summary = "Assess P2P fraud", description = "P2P transfer fraud risk assessment")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('PAYMENT_PROCESSOR')")
    public ResponseEntity<FraudCheckResponse> assessP2PFraudRisk(
            @Valid @RequestBody FraudCheckRequest request) {

        log.info("P2P fraud check for transaction: {} from sender: {} to recipient: {}",
                request.getTransactionId(), request.getUserId(), request.getMerchantId());

        // Perform P2P-specific fraud analysis
        FraudCheckResponse response = fraudDetectionService.checkTransaction(request);

        return ResponseEntity.ok(response);
    }

    /**
     * Check NFC P2P transfer fraud
     * Called by: payment-service for NFC P2P flows
     */
    @PostMapping("/assess/nfc-p2p")
    @Operation(summary = "Assess NFC P2P fraud", description = "Combined NFC and P2P fraud assessment")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('PAYMENT_PROCESSOR')")
    public ResponseEntity<FraudCheckResponse> checkNFCP2PTransfer(
            @Valid @RequestBody FraudCheckRequest request) {

        log.info("NFC P2P fraud check for transaction: {}", request.getTransactionId());

        FraudCheckResponse response = fraudDetectionService.checkTransaction(request);

        return ResponseEntity.ok(response);
    }

    /**
     * Validate transaction against known fraud patterns
     * Called by: payment-service validation flows
     */
    @PostMapping("/validate")
    @Operation(summary = "Validate transaction", description = "Validate transaction against fraud patterns")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('PAYMENT_PROCESSOR')")
    public ResponseEntity<FraudCheckResponse> validateTransaction(
            @Valid @RequestBody FraudCheckRequest request) {

        log.info("Transaction validation for: {}", request.getTransactionId());

        // Pattern-based validation
        FraudCheckResponse response = fraudDetectionService.checkTransaction(request);

        return ResponseEntity.ok(response);
    }

    /**
     * Report fraudulent activity
     * Called by: payment-service when fraud is manually reported
     */
    @PostMapping("/report")
    @Operation(summary = "Report fraud", description = "Report fraudulent transaction")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('PAYMENT_PROCESSOR') or hasRole('USER')")
    public ResponseEntity<FraudCheckResponse> reportFraud(
            @Valid @RequestBody FraudCheckRequest request) {

        log.warn("FRAUD REPORTED for transaction: {} by user: {}",
                request.getTransactionId(), request.getUserId());

        // Create fraud incident
        FraudCheckResponse response = fraudDetectionService.checkTransaction(request);

        // Force high risk for reported fraud
        response.setRiskLevel(RiskLevel.CRITICAL);
        response.setApproved(false);

        return ResponseEntity.ok(response);
    }

    /**
     * Get fraud rules for a merchant
     * Called by: payment-service for merchant-specific fraud rules
     */
    @GetMapping("/rules/{merchantId}")
    @Operation(summary = "Get merchant fraud rules", description = "Retrieve fraud rules for specific merchant")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('PAYMENT_PROCESSOR') or hasRole('MERCHANT')")
    public ResponseEntity<Map<String, Object>> getMerchantFraudRules(
            @PathVariable @NotBlank String merchantId) {

        log.debug("Fetching fraud rules for merchant: {}", merchantId);

        // Return merchant-specific rules
        Map<String, Object> rules = Map.of(
            "merchantId", merchantId,
            "maxTransactionAmount", 10000.00,
            "dailyTransactionLimit", 50,
            "velocityCheckEnabled", true,
            "geoLocationCheckEnabled", true,
            "deviceFingerprintRequired", true,
            "riskThreshold", 0.75
        );

        return ResponseEntity.ok(rules);
    }

    /**
     * Update fraud threshold
     * Called by: payment-service admin operations
     */
    @PostMapping("/threshold/update")
    @Operation(summary = "Update fraud threshold", description = "Update fraud detection thresholds")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FRAUD_MANAGER')")
    public ResponseEntity<Void> updateFraudThreshold(
            @Valid @RequestBody Map<String, Object> request) {

        log.info("Updating fraud threshold: {}", request);

        try {
            // Extract threshold parameters
            String thresholdType = (String) request.get("type");
            Double thresholdValue = ((Number) request.get("value")).doubleValue();
            String riskLevel = (String) request.get("riskLevel");

            // Validate threshold parameters
            if (thresholdType == null || thresholdValue == null) {
                return ResponseEntity.badRequest().build();
            }

            if (thresholdValue < 0.0 || thresholdValue > 1.0) {
                return ResponseEntity.badRequest().build();
            }

            // Update thresholds based on type
            switch (thresholdType) {
                case "ML_SCORE":
                    updateMlScoreThreshold(riskLevel, thresholdValue);
                    break;
                case "RULE_SCORE":
                    updateRuleScoreThreshold(riskLevel, thresholdValue);
                    break;
                case "FINAL_SCORE":
                    updateFinalScoreThreshold(riskLevel, thresholdValue);
                    break;
                case "BLOCK_THRESHOLD":
                    updateBlockThreshold(thresholdValue);
                    break;
                default:
                    log.warn("Unknown threshold type: {}", thresholdType);
                    return ResponseEntity.badRequest().build();
            }

            log.info("Fraud threshold updated successfully: {} = {} for {}",
                thresholdType, thresholdValue, riskLevel);

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Failed to update fraud threshold", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Helper method to update ML score threshold
     */
    private void updateMlScoreThreshold(String riskLevel, double threshold) {
        log.info("Updating ML score threshold for {}: {}", riskLevel, threshold);
        // In production, this would update configuration service or database
        // For now, we log the change
        // configurationService.updateThreshold("ML_SCORE_" + riskLevel, threshold);
    }

    /**
     * Helper method to update rule score threshold
     */
    private void updateRuleScoreThreshold(String riskLevel, double threshold) {
        log.info("Updating rule score threshold for {}: {}", riskLevel, threshold);
        // ruleManagementService.updateRiskLevelThreshold(riskLevel, threshold);
    }

    /**
     * Helper method to update final score threshold
     */
    private void updateFinalScoreThreshold(String riskLevel, double threshold) {
        log.info("Updating final score threshold for {}: {}", riskLevel, threshold);
        // fraudDetectionService.updateFinalScoreThreshold(riskLevel, threshold);
    }

    /**
     * Helper method to update block threshold
     */
    private void updateBlockThreshold(double threshold) {
        log.info("Updating block threshold: {}", threshold);
        // fraudDetectionService.updateBlockThreshold(threshold);
    }
}