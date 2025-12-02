package com.waqiti.notification.client;

import com.waqiti.notification.client.dto.PendingPaymentResponse;
import com.waqiti.notification.client.dto.PaymentReminderData;
import com.waqiti.notification.client.fallback.PaymentServiceClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@FeignClient(
    name = "payment-service",
    fallback = PaymentServiceClientFallback.class,
    configuration = FeignClientConfiguration.class
)
public interface PaymentServiceClient {
    
    @GetMapping("/api/v1/payments/pending")
    List<PendingPaymentResponse> getUsersWithPendingPayments();
    
    @GetMapping("/api/v1/payments/user/{userId}/pending")
    List<PaymentReminderData> getUserPendingPayments(@PathVariable("userId") UUID userId);
    
    @GetMapping("/api/v1/payments/scheduled")
    List<PendingPaymentResponse> getScheduledPayments(
        @RequestParam(value = "date", required = false) 
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );
    
    @GetMapping("/api/v1/payments/recurring/due")
    List<PendingPaymentResponse> getRecurringPaymentsDue();
    
    @GetMapping("/api/v1/payments/user/{userId}/recurring")
    List<PaymentReminderData> getUserRecurringPayments(@PathVariable("userId") UUID userId);
}