package com.waqiti.recurringpayment.service.clients;

import com.waqiti.recurringpayment.service.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * Payment Service Client for Recurring Payment Service
 */
@FeignClient(
    name = "payment-service", 
    path = "/api/v1/payments",
    fallback = PaymentServiceClientFallback.class
)
public interface PaymentServiceClient {
    
    @PostMapping
    PaymentResult processPayment(@RequestBody PaymentRequest request);
    
    @GetMapping("/{paymentId}")
    PaymentDetails getPayment(@PathVariable String paymentId);
    
    @PostMapping("/{paymentId}/refund")
    RefundResult refundPayment(@PathVariable String paymentId, @RequestBody RefundRequest request);
    
    @PostMapping("/validate")
    ValidationResult validatePaymentRequest(@RequestBody PaymentRequest request);
    
    @GetMapping("/methods/user/{userId}")
    PaymentMethodsResponse getUserPaymentMethods(@PathVariable String userId);
}