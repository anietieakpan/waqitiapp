package com.waqiti.compliance.controller;

import com.waqiti.compliance.model.AMLTransaction;
import com.waqiti.compliance.service.DroolsAMLRuleEngine;
import com.waqiti.compliance.service.DroolsAMLRuleEngine.ComplianceScreeningResult;
import com.waqiti.compliance.service.DroolsAMLRuleEngine.ComplianceMetrics;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for AML/CTF Compliance Operations
 * 
 * Provides endpoints for:
 * - Real-time transaction screening
 * - Batch transaction processing
 * - Compliance metrics and monitoring
 * - Rule management
 */
@RestController
@RequestMapping("/api/v1/compliance/aml")
@Tag(name = "AML Compliance", description = "Anti-Money Laundering and Counter-Terrorism Financing compliance operations")
@SecurityRequirement(name = "bearer-jwt")
@Validated
@RequiredArgsConstructor
@Slf4j
public class AMLComplianceController {
    
    private final DroolsAMLRuleEngine ruleEngine;
    
    /**
     * Screen a single transaction for AML/CTF compliance
     */
    @PostMapping("/screen")
    @Operation(summary = "Screen transaction for AML/CTF compliance", 
               description = "Evaluates a transaction against AML/CTF rules and returns risk assessment")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER', 'SYSTEM', 'ADMIN')")
    public ResponseEntity<ComplianceScreeningResult> screenTransaction(
            @Valid @RequestBody AMLTransaction transaction) {
        
        log.info("Screening transaction: {} for customer: {}", 
                transaction.getTransactionId(), transaction.getCustomerId());
        
        try {
            ComplianceScreeningResult result = ruleEngine.screenTransaction(transaction);
            
            // Return different status codes based on screening result
            if (result.shouldBlock()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
            } else if (result.requiresReview()) {
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
            } else {
                return ResponseEntity.ok(result);
            }
            
        } catch (Exception e) {
            log.error("Error screening transaction: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ComplianceScreeningResult.error(transaction.getTransactionId(), e.getMessage()));
        }
    }
    
    /**
     * Screen multiple transactions in batch
     */
    @PostMapping("/screen/batch")
    @Operation(summary = "Batch screen transactions", 
               description = "Screens multiple transactions efficiently in batch mode")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER', 'SYSTEM', 'ADMIN')")
    public ResponseEntity<BatchScreeningResponse> screenTransactionsBatch(
            @Valid @RequestBody @NotNull List<AMLTransaction> transactions) {
        
        log.info("Batch screening {} transactions", transactions.size());
        
        try {
            List<ComplianceScreeningResult> results = ruleEngine.screenTransactionsBatch(transactions);
            
            BatchScreeningResponse response = new BatchScreeningResponse();
            response.setTotalProcessed(results.size());
            response.setResults(results);
            
            // Calculate summary statistics
            long blocked = results.stream().filter(ComplianceScreeningResult::shouldBlock).count();
            long flagged = results.stream().filter(ComplianceScreeningResult::requiresReview).count();
            long sarRequired = results.stream().filter(ComplianceScreeningResult::requiresSAR).count();
            
            response.setBlockedCount(blocked);
            response.setFlaggedCount(flagged);
            response.setSarRequiredCount(sarRequired);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error in batch screening: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Asynchronously screen a transaction
     */
    @PostMapping("/screen/async")
    @Operation(summary = "Async transaction screening", 
               description = "Screens a transaction asynchronously and returns immediately")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER', 'SYSTEM')")
    public ResponseEntity<AsyncScreeningResponse> screenTransactionAsync(
            @Valid @RequestBody AMLTransaction transaction) {
        
        log.info("Async screening transaction: {}", transaction.getTransactionId());
        
        CompletableFuture<ComplianceScreeningResult> futureResult = 
            ruleEngine.screenTransactionAsync(transaction);
        
        AsyncScreeningResponse response = new AsyncScreeningResponse();
        response.setTransactionId(transaction.getTransactionId());
        response.setStatus("PROCESSING");
        response.setMessage("Transaction queued for screening");
        
        // Handle the result asynchronously
        futureResult.thenAccept(result -> {
            log.info("Async screening completed for transaction: {}, Risk Level: {}", 
                    transaction.getTransactionId(), result.getRiskLevel());
            // Would typically send result via webhook or message queue
        });
        
        return ResponseEntity.accepted().body(response);
    }
    
    /**
     * Get compliance metrics
     */
    @GetMapping("/metrics")
    @Operation(summary = "Get compliance metrics", 
               description = "Returns current AML/CTF compliance metrics and statistics")
    @PreAuthorize("hasAnyRole('COMPLIANCE_OFFICER', 'AUDITOR', 'ADMIN')")
    public ResponseEntity<ComplianceMetrics> getMetrics() {
        log.debug("Fetching compliance metrics");
        
        try {
            ComplianceMetrics metrics = ruleEngine.getMetrics();
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            log.error("Error fetching metrics: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Reload compliance rules (dynamic rule update)
     */
    @PostMapping("/rules/reload")
    @Operation(summary = "Reload compliance rules", 
               description = "Dynamically reloads AML/CTF rules without service restart")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RuleReloadResponse> reloadRules() {
        log.info("Reloading AML/CTF rules");
        
        try {
            ruleEngine.reloadRules();
            
            RuleReloadResponse response = new RuleReloadResponse();
            response.setStatus("SUCCESS");
            response.setMessage("Rules reloaded successfully");
            response.setTimestamp(java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to reload rules: {}", e.getMessage(), e);
            
            RuleReloadResponse response = new RuleReloadResponse();
            response.setStatus("FAILED");
            response.setMessage("Failed to reload rules: " + e.getMessage());
            response.setTimestamp(java.time.LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if AML compliance service is operational")
    public ResponseEntity<HealthResponse> health() {
        HealthResponse response = new HealthResponse();
        response.setStatus("UP");
        response.setService("AML Compliance Engine");
        response.setTimestamp(java.time.LocalDateTime.now());
        
        // Add metrics to health check
        try {
            ComplianceMetrics metrics = ruleEngine.getMetrics();
            response.setTransactionsProcessed(metrics.getTotalTransactionsProcessed());
            response.setMessage("Service operational");
        } catch (Exception e) {
            response.setMessage("Service operational with warnings: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    // Response DTOs
    
    @lombok.Data
    public static class BatchScreeningResponse {
        private int totalProcessed;
        private long blockedCount;
        private long flaggedCount;
        private long sarRequiredCount;
        private List<ComplianceScreeningResult> results;
    }
    
    @lombok.Data
    public static class AsyncScreeningResponse {
        private String transactionId;
        private String status;
        private String message;
    }
    
    @lombok.Data
    public static class RuleReloadResponse {
        private String status;
        private String message;
        private java.time.LocalDateTime timestamp;
    }
    
    @lombok.Data
    public static class HealthResponse {
        private String status;
        private String service;
        private String message;
        private long transactionsProcessed;
        private java.time.LocalDateTime timestamp;
    }
}