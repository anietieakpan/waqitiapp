package com.waqiti.billingorchestrator.client;

import com.waqiti.billingorchestrator.dto.request.ProcessPaymentRequest;
import com.waqiti.billingorchestrator.dto.request.RefundRequest;
import com.waqiti.billingorchestrator.dto.response.PaymentResponse;
import com.waqiti.billingorchestrator.dto.response.RefundResponse;
import com.waqiti.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fallback implementation for PaymentServiceClient
 *
 * Provides graceful degradation when payment-service is unavailable
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Component
@Slf4j
public class PaymentServiceClientFallback implements PaymentServiceClient {

    @Override
    public ApiResponse<PaymentResponse> processPayment(ProcessPaymentRequest request, String requestId) {
        log.error("Payment service unavailable - fallback triggered for payment processing: requestId={}", requestId);
        return ApiResponse.error("PAYMENT_SERVICE_UNAVAILABLE",
                "Payment service is temporarily unavailable. Please try again later.");
    }

    @Override
    public ApiResponse<PaymentResponse> getPayment(UUID paymentId) {
        log.error("Payment service unavailable - fallback triggered for payment retrieval: paymentId={}", paymentId);
        return ApiResponse.error("PAYMENT_SERVICE_UNAVAILABLE",
                "Payment service is temporarily unavailable. Please try again later.");
    }

    @Override
    public ApiResponse<RefundResponse> refundPayment(UUID paymentId, RefundRequest request, String requestId) {
        log.error("Payment service unavailable - fallback triggered for refund: paymentId={}, requestId={}",
                paymentId, requestId);
        return ApiResponse.error("PAYMENT_SERVICE_UNAVAILABLE",
                "Payment service is temporarily unavailable. Please try again later.");
    }
}
