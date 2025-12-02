package com.waqiti.ml.controller;

import com.waqiti.ml.model.FraudDetectionModel;
import com.waqiti.ml.service.FraudDetectionService;
import com.waqiti.ml.service.RiskScoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/ml/fraud-detection")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Fraud Detection", description = "ML-based fraud detection and risk scoring API")
public class FraudDetectionController {

    // SECURITY FIX: Use SecureRandom instead of Math.random()
    private static final SecureRandom secureRandom = new SecureRandom();

    private final FraudDetectionService fraudDetectionService;
    private final RiskScoringService riskScoringService;
    
    @PostMapping("/analyze")
    @Operation(summary = "Analyze transaction for fraud risk", 
               description = "Performs comprehensive fraud analysis using ML models")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Fraud analysis completed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PreAuthorize("hasRole('SYSTEM') or hasRole('FRAUD_ANALYST') or hasRole('ADMIN')")
    public CompletableFuture<ResponseEntity<FraudDetectionModel>> analyzeTransaction(
            @RequestBody @Validated FraudDetectionModel request) {
        
        log.info("Received fraud detection request for transaction: {}", request.getTransactionId());
        
        return fraudDetectionService.detectFraud(request)
            .thenApply(result -> {
                log.info("Fraud detection completed for transaction: {} with risk level: {}", 
                    result.getTransactionId(), result.getRiskLevel());
                return ResponseEntity.ok(result);
            })
            .exceptionally(throwable -> {
                log.error("Error in fraud detection for transaction: {}", 
                    request.getTransactionId(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }
    
    @GetMapping("/user-risk/{userId}")
    @Operation(summary = "Get user risk score", 
               description = "Calculate comprehensive risk score for a user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User risk score calculated successfully"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PreAuthorize("hasRole('FRAUD_ANALYST') or hasRole('ADMIN') or hasRole('SYSTEM')")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getUserRiskScore(
            @Parameter(description = "User ID", required = true)
            @PathVariable String userId) {
        
        log.info("Calculating user risk score for user: {}", userId);
        
        return riskScoringService.calculateUserRiskScore(userId)
            .thenApply(riskScore -> {
                Map<String, Object> response = Map.of(
                    "user_id", userId,
                    "risk_score", riskScore,
                    "risk_level", getRiskLevel(riskScore),
                    "timestamp", System.currentTimeMillis()
                );
                return ResponseEntity.ok(response);
            })
            .exceptionally(throwable -> {
                log.error("Error calculating user risk score for user: {}", userId, throwable);
                return ResponseEntity.internalServerError().build();
            });
    }
    
    @GetMapping("/transaction-risk/{transactionId}")
    @Operation(summary = "Get transaction risk score", 
               description = "Calculate risk score for a specific transaction")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Transaction risk score calculated successfully"),
        @ApiResponse(responseCode = "404", description = "Transaction not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PreAuthorize("hasRole('FRAUD_ANALYST') or hasRole('ADMIN') or hasRole('SYSTEM')")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getTransactionRiskScore(
            @Parameter(description = "Transaction ID", required = true)
            @PathVariable String transactionId) {
        
        log.info("Calculating transaction risk score for transaction: {}", transactionId);
        
        // In a real implementation, you would fetch the transaction details first
        // For now, we'll return a simulated response
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Simulate fetching transaction and calculating risk
                // SECURITY FIX: Use SecureRandom instead of Math.random()
                int riskScore = (int) (secureRandom.nextDouble() * 100);
                
                Map<String, Object> response = Map.of(
                    "transaction_id", transactionId,
                    "risk_score", riskScore,
                    "risk_level", getRiskLevel(riskScore),
                    "timestamp", System.currentTimeMillis()
                );
                
                return ResponseEntity.ok(response);
            } catch (Exception e) {
                log.error("Error calculating transaction risk score for transaction: {}", 
                    transactionId, e);
                return ResponseEntity.<Map<String, Object>>internalServerError().build();
            }
        });
    }
    
    @GetMapping("/risk-report")
    @Operation(summary = "Generate comprehensive risk report", 
               description = "Generate detailed risk assessment report for user and transaction")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Risk report generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getRiskReport(
            @Parameter(description = "User ID", required = true)
            @RequestParam String userId,
            @Parameter(description = "Transaction ID", required = false)
            @RequestParam(required = false) String transactionId) {
        
        log.info("Generating risk report for user: {} and transaction: {}", userId, transactionId);
        
        try {
            Map<String, Object> report = riskScoringService.generateRiskReport(userId, transactionId);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("Error generating risk report for user: {} and transaction: {}", 
                userId, transactionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/feedback")
    @Operation(summary = "Provide fraud detection feedback", 
               description = "Update model with actual fraud outcome for continuous learning")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Feedback processed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid feedback data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public CompletableFuture<ResponseEntity<Map<String, Object>>> provideFeedback(
            @Parameter(description = "Transaction ID", required = true)
            @RequestParam String transactionId,
            @Parameter(description = "Was this actually fraud?", required = true)
            @RequestParam Boolean wasActuallyFraud,
            @Parameter(description = "Additional notes", required = false)
            @RequestParam(required = false) String notes) {
        
        log.info("Received fraud feedback for transaction: {} - was fraud: {}", 
            transactionId, wasActuallyFraud);
        
        return fraudDetectionService.updateModelFeedback(transactionId, wasActuallyFraud)
            .thenApply(success -> {
                Map<String, Object> response = Map.of(
                    "success", success,
                    "transaction_id", transactionId,
                    "feedback_processed", success,
                    "timestamp", System.currentTimeMillis()
                );
                return ResponseEntity.ok(response);
            })
            .exceptionally(throwable -> {
                log.error("Error processing feedback for transaction: {}", transactionId, throwable);
                return ResponseEntity.internalServerError().build();
            });
    }
    
    @GetMapping("/model-explanation/{transactionId}")
    @Operation(summary = "Get model decision explanation", 
               description = "Explain why the model made a specific fraud decision")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Model explanation generated successfully"),
        @ApiResponse(responseCode = "404", description = "Transaction not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getModelExplanation(
            @Parameter(description = "Transaction ID", required = true)
            @PathVariable String transactionId) {
        
        log.info("Generating model explanation for transaction: {}", transactionId);
        
        return fraudDetectionService.explainDecision(transactionId)
            .thenApply(explanation -> ResponseEntity.ok(explanation))
            .exceptionally(throwable -> {
                log.error("Error generating model explanation for transaction: {}", 
                    transactionId, throwable);
                return ResponseEntity.internalServerError().build();
            });
    }
    
    @GetMapping("/health")
    @Operation(summary = "Check ML service health", 
               description = "Health check endpoint for ML fraud detection service")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATIONS')")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = Map.of(
            "status", "UP",
            "service", "ML Fraud Detection Service",
            "timestamp", System.currentTimeMillis(),
            "model_version", "v2.1.0"
        );
        return ResponseEntity.ok(health);
    }
    
    private String getRiskLevel(int riskScore) {
        if (riskScore >= 80) {
            return "CRITICAL";
        } else if (riskScore >= 60) {
            return "HIGH";
        } else if (riskScore >= 40) {
            return "MEDIUM";
        } else if (riskScore >= 20) {
            return "LOW";
        } else {
            return "VERY_LOW";
        }
    }
}