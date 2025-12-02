package com.waqiti.account.client;

import com.waqiti.account.dto.ComplianceCheckResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Feign client for communication with Compliance Service
 *
 * <p><b>Fault Tolerance:</b></p>
 * <ul>
 *   <li>Circuit Breaker: Compliance profile (60% failure threshold, 10s timeout)</li>
 *   <li>Fallback: Safe defaults with manual review flagging</li>
 *   <li>Retry: Automatic retry for transient KYC/AML failures</li>
 * </ul>
 *
 * <p><b>⚠️ COMPLIANCE CRITICAL:</b> Fallback behavior flags all operations for
 * manual compliance review to ensure regulatory requirements are met.</p>
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@FeignClient(
    name = "compliance-service",
    path = "/api/compliance",
    fallback = ComplianceServiceClientFallback.class,
    configuration = FeignConfiguration.class
)
public interface ComplianceServiceClient {
    
    @PostMapping("/check/account-creation")
    ComplianceCheckResult checkAccountCreationCompliance(
        @RequestParam("userId") UUID userId,
        @RequestParam("accountType") String accountType
    );
    
    @PostMapping("/check/status-change")
    ComplianceCheckResult checkStatusChangeCompliance(
        @RequestParam("accountId") UUID accountId,
        @RequestParam("newStatus") String newStatus
    );
    
    @PostMapping("/check/transaction")
    ComplianceCheckResult checkTransactionCompliance(
        @RequestParam("accountId") UUID accountId,
        @RequestParam("transactionType") String transactionType,
        @RequestParam("amount") String amount
    );
    
    @GetMapping("/accounts/{accountId}/level")
    String getAccountComplianceLevel(@PathVariable("accountId") UUID accountId);
    
    @PostMapping("/accounts/{accountId}/review")
    void flagAccountForReview(
        @PathVariable("accountId") UUID accountId,
        @RequestParam("reason") String reason
    );
}