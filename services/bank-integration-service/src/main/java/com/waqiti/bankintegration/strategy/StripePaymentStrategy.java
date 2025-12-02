package com.waqiti.bankintegration.strategy;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Refund;
import com.stripe.param.ChargeCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentIntentConfirmParams;
import com.stripe.param.RefundCreateParams;
import com.waqiti.bankintegration.domain.PaymentProvider;
import com.waqiti.bankintegration.dto.*;
import com.waqiti.bankintegration.exception.PaymentProcessingException;
import com.waqiti.common.exception.BusinessException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Stripe Payment Strategy Implementation
 * 
 * Handles payment processing through Stripe's payment gateway.
 * Supports payment intents, direct charges, refunds, and payment status checks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripePaymentStrategy implements PaymentStrategy {

    @Value("${stripe.api.version:2023-10-16}")
    private String apiVersion;

    @Value("${stripe.webhook.endpoint-secret:}")
    private String webhookSecret;

    @Value("${stripe.payment.capture-method:automatic}")
    private String defaultCaptureMethod;

    @Value("${stripe.payment.confirmation-method:automatic}")
    private String defaultConfirmationMethod;

    @PostConstruct
    public void init() {
        // API key will be set from provider configuration
        log.info("Stripe payment strategy initialized with API version: {}", apiVersion);
    }

    @Override
    @CircuitBreaker(name = "stripeApi", fallbackMethod = "processPaymentFallback")
    @Retry(name = "stripeApi")
    public PaymentResponse processPayment(PaymentProvider provider, PaymentRequest request) {
        try {
            // Set API key for this request
            Stripe.apiKey = provider.getApiKey();
            
            log.debug("Processing Stripe payment for amount: {} {}", 
                request.getAmount(), request.getCurrency());

            PaymentResponse response = new PaymentResponse();
            response.setRequestId(request.getRequestId());
            response.setProviderId(provider.getId());

            // Create payment based on payment method
            if (request.getPaymentMethodId() != null) {
                // Use Payment Intent API for modern payment flows
                PaymentIntent paymentIntent = createPaymentIntent(request, provider);
                
                response.setTransactionId(paymentIntent.getId());
                response.setStatus(mapStripeStatus(paymentIntent.getStatus()));
                response.setProviderTransactionId(paymentIntent.getId());
                response.setAmount(BigDecimal.valueOf(paymentIntent.getAmount()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                response.setCurrency(paymentIntent.getCurrency().toUpperCase());
                response.setProcessedAt(Instant.ofEpochSecond(paymentIntent.getCreated()));
                
                // Add additional data
                Map<String, Object> additionalData = new HashMap<>();
                additionalData.put("client_secret", paymentIntent.getClientSecret());
                additionalData.put("payment_method", paymentIntent.getPaymentMethod());
                additionalData.put("confirmation_method", paymentIntent.getConfirmationMethod());
                response.setAdditionalData(additionalData);
                
            } else if (request.getCardToken() != null) {
                // Legacy charge API for backward compatibility
                Charge charge = createCharge(request, provider);
                
                response.setTransactionId(charge.getId());
                response.setStatus(charge.getPaid() ? PaymentStatus.COMPLETED : PaymentStatus.FAILED);
                response.setProviderTransactionId(charge.getId());
                response.setAmount(BigDecimal.valueOf(charge.getAmount()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                response.setCurrency(charge.getCurrency().toUpperCase());
                response.setProcessedAt(Instant.ofEpochSecond(charge.getCreated()));
                
                if (!charge.getPaid()) {
                    response.setErrorCode(charge.getFailureCode());
                    response.setErrorMessage(charge.getFailureMessage());
                }
            } else {
                throw new BusinessException("Either paymentMethodId or cardToken must be provided");
            }

            log.info("Stripe payment processed successfully: {}", response.getTransactionId());
            return response;

        } catch (StripeException e) {
            log.error("Stripe payment failed: {}", e.getMessage(), e);
            throw new PaymentProcessingException("Stripe payment processing failed: " + e.getMessage(), e);
        }
    }

    @Override
    @CircuitBreaker(name = "stripeApi", fallbackMethod = "processRefundFallback")
    @Retry(name = "stripeApi")
    public RefundResponse processRefund(PaymentProvider provider, RefundRequest request) {
        try {
            Stripe.apiKey = provider.getApiKey();
            
            log.debug("Processing Stripe refund for transaction: {}", request.getOriginalTransactionId());

            RefundCreateParams params = RefundCreateParams.builder()
                .setCharge(request.getOriginalTransactionId())
                .setAmount(request.getAmount().multiply(BigDecimal.valueOf(100)).longValue())
                .setReason(mapRefundReason(request.getReason()))
                .setMetadata(Map.of(
                    "refund_request_id", request.getRequestId(),
                    "reason_details", request.getReasonDetails() != null ? request.getReasonDetails() : ""
                ))
                .build();

            Refund refund = Refund.create(params);

            RefundResponse response = new RefundResponse();
            response.setRefundId(refund.getId());
            response.setOriginalTransactionId(request.getOriginalTransactionId());
            response.setStatus(mapRefundStatus(refund.getStatus()));
            response.setAmount(BigDecimal.valueOf(refund.getAmount()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            response.setCurrency(refund.getCurrency().toUpperCase());
            response.setProcessedAt(Instant.ofEpochSecond(refund.getCreated()));
            response.setProviderRefundId(refund.getId());

            if ("failed".equals(refund.getStatus())) {
                response.setErrorCode(refund.getFailureReason());
                response.setErrorMessage("Refund failed: " + refund.getFailureReason());
            }

            log.info("Stripe refund processed successfully: {}", response.getRefundId());
            return response;

        } catch (StripeException e) {
            log.error("Stripe refund failed: {}", e.getMessage(), e);
            throw new PaymentProcessingException("Stripe refund processing failed: " + e.getMessage(), e);
        }
    }

    @Override
    @CircuitBreaker(name = "stripeApi")
    public PaymentResponse checkPaymentStatus(PaymentProvider provider, String transactionId) {
        try {
            Stripe.apiKey = provider.getApiKey();
            
            PaymentResponse response = new PaymentResponse();
            response.setTransactionId(transactionId);
            response.setProviderId(provider.getId());

            // Try to retrieve as PaymentIntent first
            try {
                PaymentIntent paymentIntent = PaymentIntent.retrieve(transactionId);
                response.setStatus(mapStripeStatus(paymentIntent.getStatus()));
                response.setAmount(BigDecimal.valueOf(paymentIntent.getAmount()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                response.setCurrency(paymentIntent.getCurrency().toUpperCase());
                response.setProviderTransactionId(paymentIntent.getId());
                response.setProcessedAt(Instant.ofEpochSecond(paymentIntent.getCreated()));
            } catch (StripeException e) {
                // If not a PaymentIntent, try as Charge
                Charge charge = Charge.retrieve(transactionId);
                response.setStatus(charge.getPaid() ? PaymentStatus.COMPLETED : PaymentStatus.FAILED);
                response.setAmount(BigDecimal.valueOf(charge.getAmount()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                response.setCurrency(charge.getCurrency().toUpperCase());
                response.setProviderTransactionId(charge.getId());
                response.setProcessedAt(Instant.ofEpochSecond(charge.getCreated()));
            }

            return response;

        } catch (StripeException e) {
            log.error("Failed to check Stripe payment status: {}", e.getMessage(), e);
            throw new PaymentProcessingException("Failed to check payment status: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean canHandle(PaymentProvider provider, PaymentRequest request) {
        // Stripe can handle card payments and various payment methods
        return provider.getProviderType() == ProviderType.STRIPE &&
               (request.getPaymentMethodId() != null || request.getCardToken() != null);
    }

    @Override
    public boolean isProviderHealthy(PaymentProvider provider) {
        try {
            Stripe.apiKey = provider.getApiKey();
            // Make a lightweight API call to check connectivity
            PaymentMethod.list(PaymentMethod.ListParams.builder().setLimit(1L).build());
            return true;
        } catch (Exception e) {
            log.warn("Stripe health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    @CircuitBreaker(name = "stripeApi")
    public PaymentResponse cancelPayment(PaymentProvider provider, String transactionId) {
        try {
            Stripe.apiKey = provider.getApiKey();
            
            PaymentIntent paymentIntent = PaymentIntent.retrieve(transactionId);
            PaymentIntent canceledIntent = paymentIntent.cancel();
            
            PaymentResponse response = new PaymentResponse();
            response.setTransactionId(canceledIntent.getId());
            response.setStatus(PaymentStatus.CANCELLED);
            response.setProviderId(provider.getId());
            response.setProviderTransactionId(canceledIntent.getId());
            
            log.info("Stripe payment cancelled successfully: {}", transactionId);
            return response;

        } catch (StripeException e) {
            log.error("Failed to cancel Stripe payment: {}", e.getMessage(), e);
            throw new PaymentProcessingException("Failed to cancel payment: " + e.getMessage(), e);
        }
    }

    private PaymentIntent createPaymentIntent(PaymentRequest request, PaymentProvider provider) throws StripeException {
        PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
            .setAmount(request.getAmount().multiply(BigDecimal.valueOf(100)).longValue())
            .setCurrency(request.getCurrency().toLowerCase())
            .setDescription(request.getDescription())
            .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.valueOf(
                provider.getConfiguration().getOrDefault("capture_method", defaultCaptureMethod).toUpperCase()
            ))
            .setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.valueOf(
                provider.getConfiguration().getOrDefault("confirmation_method", defaultConfirmationMethod).toUpperCase()
            ))
            .setMetadata(Map.of(
                "request_id", request.getRequestId(),
                "user_id", request.getUserId(),
                "merchant_id", request.getMerchantId() != null ? request.getMerchantId() : ""
            ));

        if (request.getPaymentMethodId() != null) {
            paramsBuilder.setPaymentMethod(request.getPaymentMethodId());
        }

        if (request.getCustomerId() != null) {
            paramsBuilder.setCustomer(request.getCustomerId());
        }

        // Add statement descriptor if provided
        if (request.getStatementDescriptor() != null) {
            paramsBuilder.setStatementDescriptor(request.getStatementDescriptor());
        }

        PaymentIntent paymentIntent = PaymentIntent.create(paramsBuilder.build());

        // Auto-confirm if requested
        if (Boolean.TRUE.equals(request.getAutoConfirm()) && 
            "manual".equals(paymentIntent.getConfirmationMethod())) {
            paymentIntent = paymentIntent.confirm(
                PaymentIntentConfirmParams.builder()
                    .setReturnUrl(request.getReturnUrl())
                    .build()
            );
        }

        return paymentIntent;
    }

    private Charge createCharge(PaymentRequest request, PaymentProvider provider) throws StripeException {
        ChargeCreateParams params = ChargeCreateParams.builder()
            .setAmount(request.getAmount().multiply(BigDecimal.valueOf(100)).longValue())
            .setCurrency(request.getCurrency().toLowerCase())
            .setDescription(request.getDescription())
            .setSource(request.getCardToken())
            .setCapture(!"manual".equals(provider.getConfiguration().get("capture_method")))
            .setMetadata(Map.of(
                "request_id", request.getRequestId(),
                "user_id", request.getUserId(),
                "merchant_id", request.getMerchantId() != null ? request.getMerchantId() : ""
            ))
            .build();

        return Charge.create(params);
    }

    private PaymentStatus mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "succeeded" -> PaymentStatus.COMPLETED;
            case "processing" -> PaymentStatus.PROCESSING;
            case "requires_payment_method", "requires_confirmation", "requires_action" -> PaymentStatus.PENDING;
            case "canceled" -> PaymentStatus.CANCELLED;
            case "requires_capture" -> PaymentStatus.AUTHORIZED;
            default -> PaymentStatus.FAILED;
        };
    }

    private RefundStatus mapRefundStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "succeeded" -> RefundStatus.COMPLETED;
            case "pending" -> RefundStatus.PENDING;
            case "failed" -> RefundStatus.FAILED;
            case "canceled" -> RefundStatus.CANCELLED;
            default -> RefundStatus.FAILED;
        };
    }

    private RefundCreateParams.Reason mapRefundReason(String reason) {
        if (reason == null) return RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER;
        
        return switch (reason.toUpperCase()) {
            case "DUPLICATE" -> RefundCreateParams.Reason.DUPLICATE;
            case "FRAUDULENT" -> RefundCreateParams.Reason.FRAUDULENT;
            case "REQUESTED_BY_CUSTOMER" -> RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER;
            default -> RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER;
        };
    }

    // Fallback methods
    public PaymentResponse processPaymentFallback(PaymentProvider provider, PaymentRequest request, Exception ex) {
        log.error("Stripe payment fallback triggered for request: {}", request.getRequestId(), ex);
        PaymentResponse response = new PaymentResponse();
        response.setRequestId(request.getRequestId());
        response.setStatus(PaymentStatus.FAILED);
        response.setErrorCode("STRIPE_UNAVAILABLE");
        response.setErrorMessage("Stripe service is temporarily unavailable");
        return response;
    }

    public RefundResponse processRefundFallback(PaymentProvider provider, RefundRequest request, Exception ex) {
        log.error("Stripe refund fallback triggered for request: {}", request.getRequestId(), ex);
        RefundResponse response = new RefundResponse();
        response.setRefundId(request.getRequestId());
        response.setStatus(RefundStatus.FAILED);
        response.setErrorCode("STRIPE_UNAVAILABLE");
        response.setErrorMessage("Stripe service is temporarily unavailable");
        return response;
    }
}