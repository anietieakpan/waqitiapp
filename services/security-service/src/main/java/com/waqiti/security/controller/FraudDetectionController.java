/**
 * Fraud Detection Controller
 * Advanced fraud detection and risk assessment endpoints
 */
package com.waqiti.security.controller;

import com.waqiti.security.dto.*;
import com.waqiti.security.service.FraudDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fraud-detection")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Fraud Detection", description = "Advanced fraud detection and risk assessment")
@SecurityRequirement(name = "bearerAuth")
public class FraudDetectionController {

    private final FraudDetectionService fraudDetectionService;

    @PostMapping("/analyze")
    @Operation(summary = "Analyze transaction for fraud risk")
    @PreAuthorize("hasAnyRole('SYSTEM', 'ADMIN', 'FRAUD_ANALYST')")
    public ResponseEntity<FraudAnalysisResponse> analyzeTransaction(
            @Valid @RequestBody FraudAnalysisRequest request) {
        log.info("Fraud analysis requested for transaction: {}", request.getTransactionId());
        
        FraudAnalysisResponse response = fraudDetectionService.analyzeTransaction(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/real-time-score")
    @Operation(summary = "Get real-time fraud score")
    @PreAuthorize("hasAnyRole('SYSTEM', 'ADMIN', 'FRAUD_ANALYST')")
    public ResponseEntity<RealTimeFraudScoreResponse> getRealTimeFraudScore(
            @Valid @RequestBody RealTimeFraudScoreRequest request) {
        log.info("Real-time fraud score requested for user: {}", request.getUserId());
        
        RealTimeFraudScoreResponse response = fraudDetectionService.getRealTimeFraudScore(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/alerts")
    @Operation(summary = "Get fraud alerts")
    @PreAuthorize("hasAnyRole('ADMIN', 'FRAUD_ANALYST', 'MANAGER')")
    public ResponseEntity<Page<FraudAlertResponse>> getFraudAlerts(
            @Parameter(description = "Filter by severity") @RequestParam(required = false) String severity,
            @Parameter(description = "Filter by status") @RequestParam(required = false) String status,
            @Parameter(description = "Filter by user ID") @RequestParam(required = false) UUID userId,
            Pageable pageable) {
        log.info("Fraud alerts requested with filters - severity: {}, status: {}, userId: {}", 
                severity, status, userId);
        
        Page<FraudAlertResponse> alerts = fraudDetectionService.getFraudAlerts(
                severity, status, userId, pageable);
        return ResponseEntity.ok(alerts);
    }

    @PostMapping("/alerts/{alertId}/acknowledge")
    @Operation(summary = "Acknowledge fraud alert")
    @PreAuthorize("hasAnyRole('ADMIN', 'FRAUD_ANALYST')")
    public ResponseEntity<FraudAlertResponse> acknowledgeFraudAlert(
            @PathVariable UUID alertId,
            @Valid @RequestBody AcknowledgeFraudAlertRequest request) {
        log.info("Acknowledging fraud alert: {} by analyst: {}", alertId, request.getAnalystId());
        
        FraudAlertResponse response = fraudDetectionService.acknowledgeFraudAlert(alertId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/velocity-check")
    @Operation(summary = "Perform velocity check")
    @PreAuthorize("hasAnyRole('SYSTEM', 'ADMIN', 'FRAUD_ANALYST')")
    public ResponseEntity<VelocityCheckResponse> performVelocityCheck(
            @Valid @RequestBody VelocityCheckRequest request) {
        log.info("Velocity check requested for user: {}", request.getUserId());
        
        VelocityCheckResponse response = fraudDetectionService.performVelocityCheck(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/patterns/suspicious")
    @Operation(summary = "Get suspicious patterns detected")
    @PreAuthorize("hasAnyRole('ADMIN', 'FRAUD_ANALYST', 'MANAGER')")
    public ResponseEntity<List<SuspiciousPatternResponse>> getSuspiciousPatterns(
            @Parameter(description = "Filter by pattern type") @RequestParam(required = false) String patternType,
            @Parameter(description = "Filter by user ID") @RequestParam(required = false) UUID userId,
            @Parameter(description = "Number of days to look back") @RequestParam(defaultValue = "7") int days) {
        log.info("Suspicious patterns requested - type: {}, userId: {}, days: {}", 
                patternType, userId, days);
        
        List<SuspiciousPatternResponse> patterns = fraudDetectionService.getSuspiciousPatterns(
                patternType, userId, days);
        return ResponseEntity.ok(patterns);
    }

    @PostMapping("/blacklist")
    @Operation(summary = "Add entity to blacklist")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BlacklistResponse> addToBlacklist(
            @Valid @RequestBody AddToBlacklistRequest request) {
        log.info("Adding to blacklist - type: {}, value: {}", 
                request.getEntityType(), request.getMaskedValue());
        
        BlacklistResponse response = fraudDetectionService.addToBlacklist(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/blacklist")
    @Operation(summary = "Get blacklist entries")
    @PreAuthorize("hasAnyRole('ADMIN', 'FRAUD_ANALYST')")
    public ResponseEntity<Page<BlacklistResponse>> getBlacklistEntries(
            @Parameter(description = "Filter by entity type") @RequestParam(required = false) String entityType,
            @Parameter(description = "Filter by status") @RequestParam(required = false) String status,
            Pageable pageable) {
        log.info("Blacklist entries requested - type: {}, status: {}", entityType, status);
        
        Page<BlacklistResponse> entries = fraudDetectionService.getBlacklistEntries(
                entityType, status, pageable);
        return ResponseEntity.ok(entries);
    }

    @DeleteMapping("/blacklist/{id}")
    @Operation(summary = "Remove from blacklist")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeFromBlacklist(@PathVariable UUID id) {
        log.info("Removing from blacklist: {}", id);
        fraudDetectionService.removeFromBlacklist(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/whitelist")
    @Operation(summary = "Add entity to whitelist")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WhitelistResponse> addToWhitelist(
            @Valid @RequestBody AddToWhitelistRequest request) {
        log.info("Adding to whitelist - type: {}, value: {}", 
                request.getEntityType(), request.getMaskedValue());
        
        WhitelistResponse response = fraudDetectionService.addToWhitelist(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/behavioral-analysis/{userId}")
    @Operation(summary = "Get behavioral analysis for user")
    @PreAuthorize("hasAnyRole('ADMIN', 'FRAUD_ANALYST')")
    public ResponseEntity<BehavioralAnalysisResponse> getBehavioralAnalysis(
            @PathVariable UUID userId,
            @Parameter(description = "Number of days for analysis") @RequestParam(defaultValue = "30") int days) {
        log.info("Behavioral analysis requested for user: {} over {} days", userId, days);
        
        BehavioralAnalysisResponse response = fraudDetectionService.getBehavioralAnalysis(userId, days);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/machine-learning/retrain")
    @Operation(summary = "Trigger ML model retraining")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MlModelRetrainingResponse> triggerMlRetraining(
            @Valid @RequestBody MlModelRetrainingRequest request) {
        log.info("ML model retraining triggered for model: {}", request.getModelType());
        
        MlModelRetrainingResponse response = fraudDetectionService.triggerMlRetraining(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/machine-learning/model-performance")
    @Operation(summary = "Get ML model performance metrics")
    @PreAuthorize("hasAnyRole('ADMIN', 'FRAUD_ANALYST', 'MANAGER')")
    public ResponseEntity<MlModelPerformanceResponse> getMlModelPerformance(
            @Parameter(description = "Model type") @RequestParam String modelType,
            @Parameter(description = "Number of days for metrics") @RequestParam(defaultValue = "30") int days) {
        log.info("ML model performance requested for model: {} over {} days", modelType, days);
        
        MlModelPerformanceResponse response = fraudDetectionService.getMlModelPerformance(modelType, days);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/device-fingerprint/analyze")
    @Operation(summary = "Analyze device fingerprint")
    @PreAuthorize("hasAnyRole('SYSTEM', 'ADMIN', 'FRAUD_ANALYST')")
    public ResponseEntity<DeviceFingerprintAnalysisResponse> analyzeDeviceFingerprint(
            @Valid @RequestBody DeviceFingerprintAnalysisRequest request) {
        log.info("Device fingerprint analysis requested for user: {}", request.getUserId());
        
        DeviceFingerprintAnalysisResponse response = fraudDetectionService.analyzeDeviceFingerprint(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/geolocation/risk-assessment/{userId}")
    @Operation(summary = "Get geolocation risk assessment")
    @PreAuthorize("hasAnyRole('ADMIN', 'FRAUD_ANALYST')")
    public ResponseEntity<GeolocationRiskResponse> getGeolocationRisk(
            @PathVariable UUID userId,
            @Parameter(description = "IP address") @RequestParam String ipAddress,
            @Parameter(description = "Latitude") @RequestParam(required = false) Double latitude,
            @Parameter(description = "Longitude") @RequestParam(required = false) Double longitude) {
        log.info("Geolocation risk assessment requested for user: {} from IP: {}", userId, ipAddress);
        
        GeolocationRiskResponse response = fraudDetectionService.getGeolocationRisk(
                userId, ipAddress, latitude, longitude);
        return ResponseEntity.ok(response);
    }
}