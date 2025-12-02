package com.waqiti.ledger.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Compliance Service Client
 *
 * Feign client for compliance checks and regulatory reporting
 */
@FeignClient(
    name = "compliance-service",
    path = "/api/v1/compliance",
    fallback = ComplianceServiceClientFallback.class
)
public interface ComplianceServiceClient {
    
    @PostMapping("/validate/transaction")
    ResponseEntity<ComplianceValidationResponse> validateTransaction(
        @RequestBody TransactionComplianceRequest request
    );
    
    @PostMapping("/validate/account")
    ResponseEntity<ComplianceValidationResponse> validateAccount(
        @RequestBody AccountComplianceRequest request
    );
    
    @PostMapping("/aml/screen")
    ResponseEntity<AMLScreeningResponse> performAMLScreening(
        @RequestBody AMLScreeningRequest request
    );
    
    @PostMapping("/report/suspicious-activity")
    ResponseEntity<SARResponse> reportSuspiciousActivity(
        @RequestBody SuspiciousActivityRequest request
    );
    
    @GetMapping("/rules/account-type/{accountType}")
    ResponseEntity<ComplianceRulesResponse> getAccountTypeRules(
        @PathVariable String accountType
    );
    
    // DTOs for compliance operations
    record ComplianceValidationResponse(
        boolean compliant,
        String riskLevel,
        String message,
        Map<String, Object> details
    ) {}
    
    record TransactionComplianceRequest(
        UUID transactionId,
        String transactionType,
        java.math.BigDecimal amount,
        String currency,
        UUID sourceAccountId,
        UUID destinationAccountId,
        Map<String, Object> metadata
    ) {}
    
    record AccountComplianceRequest(
        UUID accountId,
        String accountType,
        UUID userId,
        String jurisdiction,
        Map<String, Object> metadata
    ) {}
    
    record AMLScreeningResponse(
        boolean passed,
        String riskScore,
        String screeningId,
        Map<String, Object> findings
    ) {}
    
    record AMLScreeningRequest(
        UUID entityId,
        String entityType,
        String name,
        String country,
        Map<String, Object> additionalInfo
    ) {}
    
    record SARResponse(
        String reportId,
        String status,
        String filingDate
    ) {}
    
    record SuspiciousActivityRequest(
        UUID accountId,
        UUID transactionId,
        String activityType,
        String description,
        Map<String, Object> evidence
    ) {}
    
    record ComplianceRulesResponse(
        String accountType,
        Map<String, Object> rules,
        Map<String, Object> limits
    ) {}
}