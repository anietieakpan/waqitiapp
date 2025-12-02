package com.waqiti.recurringpayment.service.clients;

import com.waqiti.recurringpayment.domain.RecurringExecution;
import com.waqiti.recurringpayment.domain.RecurringPayment;
import com.waqiti.recurringpayment.service.dto.FraudCheckRequest;
import com.waqiti.recurringpayment.service.dto.FraudCheckResult;
import com.waqiti.recurringpayment.service.dto.RiskProfile;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * Fraud Detection Service Client for Recurring Payment Service
 * 
 * CRITICAL FIX: Added fallback to prevent payment failures when fraud service unavailable
 */
@FeignClient(
    name = "fraud-detection-service", 
    path = "/api/v1/fraud",
    fallback = FraudDetectionServiceClientFallback.class
)
public interface FraudDetectionServiceClient {
    
    @PostMapping("/check/recurring")
    FraudCheckResult checkRecurringPayment(@RequestBody RecurringPayment recurring, 
                                         @RequestBody RecurringExecution execution);
    
    @PostMapping("/check/payment")
    FraudCheckResult checkPayment(@RequestBody FraudCheckRequest request);
    
    @GetMapping("/profile/{userId}")
    RiskProfile getUserRiskProfile(@PathVariable String userId);
    
    @PostMapping("/profile/{userId}/update")
    void updateUserRiskProfile(@PathVariable String userId, 
                             @RequestBody RiskProfile profile);
    
    @PostMapping("/report/suspicious")
    void reportSuspiciousActivity(@RequestParam String userId,
                                @RequestParam String activityType,
                                @RequestBody Object activityDetails);
    
    @GetMapping("/rules/recurring")
    RecurringPaymentRules getRecurringPaymentRules();
}