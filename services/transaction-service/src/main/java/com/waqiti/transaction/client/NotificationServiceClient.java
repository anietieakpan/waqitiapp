package com.waqiti.transaction.client;

import com.waqiti.transaction.dto.NotificationRequest;
import com.waqiti.transaction.dto.NotificationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
    name = "notification-service", 
    path = "/api/notifications",
    fallback = NotificationServiceClientFallback.class
)
public interface NotificationServiceClient {

    @PostMapping("/send")
    NotificationResponse sendNotification(@RequestBody NotificationRequest request);

    @PostMapping("/transaction/{transactionId}/status")
    void notifyTransactionStatus(@PathVariable String transactionId, @RequestParam String status);

    @PostMapping("/transaction/{transactionId}/approval-required")
    void notifyApprovalRequired(@PathVariable String transactionId, @RequestParam String approver);

    @PostMapping("/transaction/{transactionId}/completed")
    void notifyTransactionCompleted(@PathVariable String transactionId);

    @PostMapping("/customer/{customerId}/freeze")
    void sendCustomerFreezeNotification(@PathVariable String customerId, 
                                      @RequestParam String reason, 
                                      @RequestParam Boolean emergencyFreeze, 
                                      @RequestParam(required = false) Integer duration);

    @PostMapping("/merchant/{merchantId}/freeze")
    void sendMerchantFreezeNotification(@PathVariable String merchantId, 
                                      @RequestParam String reason, 
                                      @RequestParam Boolean emergencyFreeze, 
                                      @RequestParam(required = false) Integer duration);

    @PostMapping("/compliance/freeze")
    void sendComplianceFreezeNotification(@RequestParam String requestId, 
                                         @RequestParam String reason, 
                                         @RequestParam java.util.Set<String> complianceFlags);

    @PostMapping("/compliance/sar-filing")
    void triggerSarFiling(@RequestParam(required = false) String customerId, 
                         @RequestParam(required = false) String merchantId, 
                         @RequestParam(required = false) String transactionId, 
                         @RequestParam String reason, 
                         @RequestParam(required = false) java.math.BigDecimal amount);

    @PostMapping("/compliance/review")
    void triggerComplianceReview(@RequestParam String requestId, 
                                @RequestParam java.util.Set<String> complianceFlags, 
                                @RequestParam String priority);
}