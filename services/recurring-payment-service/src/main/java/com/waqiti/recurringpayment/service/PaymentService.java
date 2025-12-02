package com.waqiti.recurringpayment.service;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

// Payment Service Client
@FeignClient(
    name = "payment-service",
    url = "${feign.client.config.payment-service.url}",
    fallback = PaymentServiceFallback.class
)
public interface PaymentService {
    
    @PostMapping("/payments")
    PaymentResult processPayment(@RequestBody PaymentRequest request);
    
    @GetMapping("/payments/{paymentId}")
    PaymentResult getPayment(@PathVariable String paymentId);
    
    @PostMapping("/payments/{paymentId}/cancel")
    void cancelPayment(@PathVariable String paymentId, @RequestParam String reason);
}
