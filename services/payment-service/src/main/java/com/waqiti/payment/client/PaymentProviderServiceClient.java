package com.waqiti.payment.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Feign Client for Payment Provider Service
 * Handles external payment provider operations
 */
@FeignClient(
    name = "payment-provider-service",
    path = "/api/v1/payment-provider"
)
public interface PaymentProviderServiceClient {
    
    @PostMapping("/initiate")
    @CircuitBreaker(name = "payment-provider")
    @Retry(name = "payment-provider")
    Map<String, Object> initiatePayment(@RequestBody Map<String, Object> paymentRequest);
    
    @PostMapping("/cancel")
    @CircuitBreaker(name = "payment-provider")
    @Retry(name = "payment-provider")
    Map<String, Object> cancelPayment(@RequestParam("transactionId") String transactionId,
                                       @RequestParam("provider") String provider);
    
    @PostMapping("/refund")
    @CircuitBreaker(name = "payment-provider")
    @Retry(name = "payment-provider")
    Map<String, Object> refundPayment(@RequestParam("transactionId") String transactionId,
                                       @RequestParam("amount") BigDecimal amount,
                                       @RequestParam("provider") String provider);
    
    @GetMapping("/status")
    @CircuitBreaker(name = "payment-provider")
    Map<String, Object> getPaymentStatus(@RequestParam("transactionId") String transactionId,
                                          @RequestParam("provider") String provider);
    
    @PostMapping("/validate")
    @CircuitBreaker(name = "payment-provider")
    Map<String, Object> validatePaymentMethod(@RequestBody Map<String, Object> validationRequest);
}