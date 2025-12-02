package com.waqiti.billingorchestrator.client;

import com.waqiti.billingorchestrator.dto.request.ProcessPaymentRequest;
import com.waqiti.billingorchestrator.dto.request.RefundRequest;
import com.waqiti.billingorchestrator.dto.response.PaymentResponse;
import com.waqiti.billingorchestrator.dto.response.RefundResponse;
import com.waqiti.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Feign Client for Payment Service
 *
 * Integrates with existing payment-service microservice
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@FeignClient(
    name = "payment-service",
    path = "/api/v1/payments",
    fallback = PaymentServiceClientFallback.class
)
public interface PaymentServiceClient {

    /**
     * Process a payment
     *
     * Calls: POST /api/v1/payments/process
     */
    @PostMapping("/process")
    ApiResponse<PaymentResponse> processPayment(
            @RequestBody ProcessPaymentRequest request,
            @RequestHeader("X-Request-ID") String requestId);

    /**
     * Get payment details
     *
     * Calls: GET /api/v1/payments/{paymentId}
     */
    @GetMapping("/{paymentId}")
    ApiResponse<PaymentResponse> getPayment(@PathVariable UUID paymentId);

    /**
     * Refund a payment
     *
     * Calls: POST /api/v1/payments/{paymentId}/refund
     */
    @PostMapping("/{paymentId}/refund")
    ApiResponse<RefundResponse> refundPayment(
            @PathVariable UUID paymentId,
            @RequestBody RefundRequest request,
            @RequestHeader("X-Request-ID") String requestId);
}
