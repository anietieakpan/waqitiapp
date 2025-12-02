package com.waqiti.recurringpayment.service.clients;

import com.waqiti.recurringpayment.domain.RecurringExecution;
import com.waqiti.recurringpayment.domain.RecurringPayment;
import com.waqiti.recurringpayment.service.dto.FraudCheckResult;
import com.waqiti.recurringpayment.service.dto.NotificationPreferences;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * Notification Service Client for Recurring Payment Service
 */
@FeignClient(
    name = "notification-service", 
    path = "/api/v1/notifications",
    fallback = NotificationServiceClientFallback.class
)
public interface NotificationServiceClient {
    
    @PostMapping("/recurring/created")
    void sendRecurringPaymentCreatedNotification(@RequestParam String userId, 
                                               @RequestBody RecurringPayment recurring);
    
    @PostMapping("/recurring/updated")
    void sendRecurringPaymentUpdatedNotification(@RequestParam String userId, 
                                               @RequestBody RecurringPayment recurring);
    
    @PostMapping("/recurring/paused")
    void sendRecurringPaymentPausedNotification(@RequestParam String userId, 
                                              @RequestBody RecurringPayment recurring);
    
    @PostMapping("/recurring/resumed")
    void sendRecurringPaymentResumedNotification(@RequestParam String userId, 
                                               @RequestBody RecurringPayment recurring);
    
    @PostMapping("/recurring/cancelled")
    void sendRecurringPaymentCancelledNotification(@RequestParam String userId, 
                                                 @RequestBody RecurringPayment recurring);
    
    @PostMapping("/recurring/success")
    void sendRecurringPaymentSuccessNotification(@RequestParam String userId, 
                                               @RequestBody RecurringPayment recurring,
                                               @RequestBody RecurringExecution execution);
    
    @PostMapping("/recurring/failed")
    void sendRecurringPaymentFailedNotification(@RequestParam String userId, 
                                              @RequestBody RecurringPayment recurring,
                                              @RequestBody RecurringExecution execution,
                                              @RequestParam String message);
    
    @PostMapping("/recurring/reminder")
    void sendPaymentReminderNotification(@RequestParam String userId, 
                                       @RequestBody RecurringPayment recurring,
                                       @RequestParam int daysUntilPayment);
    
    @PostMapping("/recurring/upcoming")
    void sendUpcomingPaymentNotification(@RequestParam String userId,
                                       @RequestBody RecurringPayment recurring,
                                       @RequestParam int hoursUntilPayment);
    
    @PostMapping("/fraud/alert")
    void sendFraudAlertNotification(@RequestParam String userId, 
                                  @RequestBody RecurringPayment recurring,
                                  @RequestBody RecurringExecution execution,
                                  @RequestBody FraudCheckResult fraudResult);
    
    @PostMapping("/recurring/completed")
    void sendRecurringPaymentCompletedNotification(@RequestParam String userId,
                                                 @RequestBody RecurringPayment recurring);
    
    @GetMapping("/preferences/{userId}")
    NotificationPreferences getUserNotificationPreferences(@PathVariable String userId);
}