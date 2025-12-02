package com.waqiti.transaction.client;

import com.waqiti.transaction.dto.ComplianceCheckRequest;
import com.waqiti.transaction.dto.ComplianceCheckResponse;
import com.waqiti.transaction.dto.RiskAssessmentRequest;
import com.waqiti.transaction.dto.RiskAssessmentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * P1 FIX: Changed path from /api/compliance to /api/v1/compliance to match actual endpoints
 */
@FeignClient(
    name = "compliance-service",
    path = "/api/v1/compliance",  // ✅ FIXED: Was /api/compliance
    fallback = ComplianceServiceClientFallback.class,
    configuration = ComplianceServiceClientConfiguration.class
)
public interface ComplianceServiceClient {

    @PostMapping("/check")
    ComplianceCheckResponse performComplianceCheck(@RequestBody ComplianceCheckRequest request);

    @PostMapping("/risk-assessment")
    RiskAssessmentResponse performRiskAssessment(@RequestBody RiskAssessmentRequest request);

    @GetMapping("/transaction/{transactionId}/status")
    String getComplianceStatus(@PathVariable String transactionId);

    @PostMapping("/transaction/{transactionId}/flag")
    void flagTransaction(@PathVariable String transactionId, @RequestParam String reason);
}