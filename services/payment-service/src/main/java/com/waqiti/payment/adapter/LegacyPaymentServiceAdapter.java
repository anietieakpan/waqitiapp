package com.waqiti.payment.adapter;

import com.waqiti.payment.dto.PaymentRequest;
import com.waqiti.payment.dto.PaymentResponse;
import com.waqiti.payment.dto.RefundRequest;
import com.waqiti.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Legacy Payment Service Adapter
 * Adapter to bridge legacy payment service calls to new payment service
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyPaymentServiceAdapter {
    
    private final PaymentService paymentService;
    
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("Processing payment through legacy adapter: {}", request.getTransactionId());
        
        try {
            return paymentService.processPayment(request);
        } catch (Exception e) {
            log.error("Legacy adapter payment processing failed", e);
            return createFailureResponse(request, e);
        }
    }
    
    public PaymentResponse processCardPayment(PaymentRequest request) {
        log.info("Processing card payment through legacy adapter: {}", request.getTransactionId());
        
        try {
            request.setPaymentMethod("CARD");
            return paymentService.processPayment(request);
        } catch (Exception e) {
            log.error("Legacy adapter card payment failed", e);
            return createFailureResponse(request, e);
        }
    }
    
    public PaymentResponse processACHPayment(PaymentRequest request) {
        log.info("Processing ACH payment through legacy adapter: {}", request.getTransactionId());
        
        try {
            request.setPaymentMethod("ACH");
            return paymentService.processPayment(request);
        } catch (Exception e) {
            log.error("Legacy adapter ACH payment failed", e);
            return createFailureResponse(request, e);
        }
    }
    
    public PaymentResponse processInstantPayment(PaymentRequest request) {
        log.info("Processing instant payment through legacy adapter: {}", request.getTransactionId());
        
        try {
            request.setPaymentMethod("INSTANT");
            return paymentService.processPayment(request);
        } catch (Exception e) {
            log.error("Legacy adapter instant payment failed", e);
            return createFailureResponse(request, e);
        }
    }
    
    public PaymentResponse getPaymentStatus(String transactionId) {
        log.info("Getting payment status through legacy adapter: {}", transactionId);
        
        try {
            return paymentService.getPaymentStatus(transactionId);
        } catch (Exception e) {
            log.error("Legacy adapter get status failed", e);
            return createFailureResponse(transactionId, e);
        }
    }
    
    public PaymentResponse cancelPayment(String transactionId) {
        log.info("Cancelling payment through legacy adapter: {}", transactionId);
        
        try {
            return paymentService.cancelPayment(transactionId);
        } catch (Exception e) {
            log.error("Legacy adapter cancel failed", e);
            return createFailureResponse(transactionId, e);
        }
    }
    
    public PaymentResponse processRefund(RefundRequest refundRequest) {
        log.info("Processing refund through legacy adapter: {}", refundRequest.getRefundId());
        
        try {
            return paymentService.refundPayment(
                refundRequest.getOriginalTransactionId(),
                refundRequest.getAmount(),
                refundRequest.getReason()
            );
        } catch (Exception e) {
            log.error("Legacy adapter refund failed", e);
            return PaymentResponse.builder()
                .transactionId(refundRequest.getRefundId())
                .originalTransactionId(refundRequest.getOriginalTransactionId())
                .status("REFUND_FAILED")
                .success(false)
                .message("Refund failed: " + e.getMessage())
                .timestamp(Instant.now())
                .build();
        }
    }
    
    private PaymentResponse createFailureResponse(PaymentRequest request, Exception e) {
        return PaymentResponse.builder()
            .transactionId(request.getTransactionId())
            .status("FAILED")
            .success(false)
            .message("Payment processing failed: " + e.getMessage())
            .timestamp(Instant.now())
            .build();
    }
    
    private PaymentResponse createFailureResponse(String transactionId, Exception e) {
        return PaymentResponse.builder()
            .transactionId(transactionId)
            .status("FAILED")
            .success(false)
            .message("Operation failed: " + e.getMessage())
            .timestamp(Instant.now())
            .build();
    }
}