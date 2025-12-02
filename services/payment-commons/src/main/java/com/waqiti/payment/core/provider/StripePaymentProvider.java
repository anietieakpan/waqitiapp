package com.waqiti.payment.core.provider;

import com.waqiti.payment.core.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.waqiti.payment.core.exception.PaymentProviderException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Stripe payment provider implementation
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class StripePaymentProvider implements PaymentProvider {

    private final WebClient stripeWebClient;
    
    @Value("${stripe.secret-key}")
    private String secretKey;
    
    @Value("${stripe.public-key}")
    private String publicKey;
    
    private static final Set<PaymentType> SUPPORTED_TYPES = Set.of(
        PaymentType.P2P,
        PaymentType.MERCHANT,
        PaymentType.CARD,
        PaymentType.REFUND
    );

    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        log.info("Processing Stripe payment: {}", request.getPaymentId());
        
        try {
            // Validate request
            ValidationResult validation = validatePayment(request);
            if (!validation.isValid()) {
                return PaymentResult.error(validation.getErrorMessage());
            }
            
            // Create Stripe payment intent
            Map<String, Object> paymentIntent = createPaymentIntent(request);
            
            // Process payment
            Map<String, Object> result = executeStripePayment(paymentIntent, request);
            
            // Parse response
            return parseStripeResponse(result, request);
            
        } catch (Exception e) {
            log.error("Stripe payment failed: ", e);
            return PaymentResult.error("Stripe payment failed: " + e.getMessage());
        }
    }

    @Override
    public PaymentResult processRefund(RefundRequest request) {
        log.info("Processing Stripe refund: {}", request.getOriginalPaymentId());
        
        try {
            Map<String, Object> refundRequest = Map.of(
                "payment_intent", request.getOriginalPaymentId(),
                "amount", request.getAmount().multiply(new BigDecimal("100")).intValue(), // Convert to cents
                "reason", request.getReason()
            );
            
            Map<String, Object> result = callStripeApi("/v1/refunds", refundRequest);
            
            return parseRefundResponse(result, request);
            
        } catch (Exception e) {
            log.error("Stripe refund failed: ", e);
            return PaymentResult.error("Stripe refund failed: " + e.getMessage());
        }
    }

    @Override
    public PaymentStatus getPaymentStatus(String paymentId) {
        try {
            Map<String, Object> result = callStripeApi("/v1/payment_intents/" + paymentId, null);
            String status = (String) result.get("status");
            
            return switch (status) {
                case "succeeded" -> PaymentStatus.COMPLETED;
                case "processing" -> PaymentStatus.PROCESSING;
                case "requires_payment_method" -> PaymentStatus.FAILED;
                case "requires_confirmation" -> PaymentStatus.PENDING;
                case "canceled" -> PaymentStatus.CANCELLED;
                default -> PaymentStatus.PENDING;
            };
            
        } catch (Exception e) {
            log.error("Failed to get Stripe payment status: ", e);
            return PaymentStatus.FAILED;
        }
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.STRIPE;
    }

    @Override
    public boolean canHandle(PaymentType paymentType) {
        return SUPPORTED_TYPES.contains(paymentType);
    }

    @Override
    public ValidationResult validatePayment(PaymentRequest request) {
        // Validate amount
        if (request.getAmount().compareTo(new BigDecimal("0.50")) < 0) {
            return ValidationResult.invalid("Minimum amount for Stripe is $0.50");
        }
        
        if (request.getAmount().compareTo(new BigDecimal("999999.99")) > 0) {
            return ValidationResult.invalid("Maximum amount for Stripe is $999,999.99");
        }
        
        // Validate payment method
        if (request.getPaymentMethodId() == null) {
            return ValidationResult.invalid("Payment method is required for Stripe");
        }
        
        return ValidationResult.valid();
    }

    @Override
    public FeeCalculation calculateFees(PaymentRequest request) {
        // Stripe fees: 2.9% + $0.30 for US cards
        BigDecimal percentageFee = request.getAmount().multiply(new BigDecimal("0.029"));
        BigDecimal fixedFee = new BigDecimal("0.30");
        BigDecimal totalFee = percentageFee.add(fixedFee);
        
        return FeeCalculation.builder()
            .processingFee(totalFee)
            .networkFee(BigDecimal.ZERO)
            .totalFees(totalFee)
            .feeStructure("2.9% + $0.30")
            .build();
    }

    @Override
    public boolean isHealthy() {
        try {
            // Simple health check - get account info
            callStripeApi("/v1/account", null);
            return true;
        } catch (Exception e) {
            log.error("Stripe health check failed: ", e);
            return false;
        }
    }

    @Override
    public ProviderCapabilities getCapabilities() {
        return ProviderCapabilities.builder()
            .supportsRefunds(true)
            .supportsPartialRefunds(true)
            .supportsRecurring(true)
            .supportsInstantTransfer(false)
            .supportsInternational(true)
            .supportedCurrencies(Set.of("USD", "EUR", "GBP", "CAD", "AUD"))
            .maxAmount(new BigDecimal("999999.99"))
            .minAmount(new BigDecimal("0.50"))
            .build();
    }

    @Override
    public ProviderConfiguration getConfiguration() {
        return ProviderConfiguration.builder()
            .providerType(ProviderType.STRIPE)
            .isEnabled(secretKey != null && !secretKey.isEmpty())
            .configuration(Map.of(
                "api_version", "2023-10-16",
                "webhook_signing_secret", "whsec_***",
                "connect_enabled", false
            ))
            .build();
    }

    private Map<String, Object> createPaymentIntent(PaymentRequest request) {
        Map<String, Object> params = new HashMap<>();
        params.put("amount", request.getAmount().multiply(new BigDecimal("100")).intValue()); // Convert to cents
        params.put("currency", "usd");
        params.put("payment_method", request.getPaymentMethodId());
        params.put("confirmation_method", "manual");
        params.put("confirm", true);
        params.put("description", request.getDescription());
        
        // Add metadata
        Map<String, String> metadata = new HashMap<>();
        metadata.put("waqiti_payment_id", request.getPaymentId().toString());
        metadata.put("payment_type", request.getType().name());
        metadata.put("from_user", request.getFromUserId());
        metadata.put("to_user", request.getToUserId());
        params.put("metadata", metadata);
        
        return params;
    }

    private Map<String, Object> executeStripePayment(Map<String, Object> paymentIntent, PaymentRequest request) {
        return callStripeApi("/v1/payment_intents", paymentIntent);
    }

    private PaymentResult parseStripeResponse(Map<String, Object> response, PaymentRequest request) {
        String status = (String) response.get("status");
        String id = (String) response.get("id");
        
        PaymentResult.PaymentResultBuilder builder = PaymentResult.builder()
            .paymentId(request.getPaymentId())
            .transactionId(id)
            .amount(request.getAmount())
            .processedAt(LocalDateTime.now())
            .providerTransactionId(id)
            .providerResponse(response);
        
        switch (status) {
            case "succeeded" -> {
                return builder
                    .status(PaymentStatus.COMPLETED)
                    .message("Payment completed successfully")
                    .build();
            }
            case "processing" -> {
                return builder
                    .status(PaymentStatus.PROCESSING)
                    .message("Payment is being processed")
                    .build();
            }
            case "requires_action" -> {
                return builder
                    .status(PaymentStatus.REVIEW_REQUIRED)
                    .message("Payment requires additional action")
                    .build();
            }
            default -> {
                return builder
                    .status(PaymentStatus.FAILED)
                    .errorMessage("Payment failed with status: " + status)
                    .build();
            }
        }
    }

    private PaymentResult parseRefundResponse(Map<String, Object> response, RefundRequest request) {
        String status = (String) response.get("status");
        String id = (String) response.get("id");
        
        if ("succeeded".equals(status)) {
            return PaymentResult.builder()
                .transactionId(id)
                .status(PaymentStatus.REFUNDED)
                .amount(request.getAmount())
                .message("Refund completed successfully")
                .processedAt(LocalDateTime.now())
                .providerTransactionId(id)
                .providerResponse(response)
                .build();
        } else {
            return PaymentResult.builder()
                .status(PaymentStatus.FAILED)
                .errorMessage("Refund failed with status: " + status)
                .processedAt(LocalDateTime.now())
                .providerResponse(response)
                .build();
        }
    }

    private Map<String, Object> callStripeApi(String endpoint, Map<String, Object> params) {
        // Simplified Stripe API call - in production use Stripe SDK
        try {
            return stripeWebClient
                .post()
                .uri(endpoint)
                .header("Authorization", "Bearer " + secretKey)
                .bodyValue(params != null ? params : Map.of())
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        } catch (Exception e) {
            throw new PaymentProviderException("Stripe API call failed", "Stripe", endpoint, e);
        }
    }
}