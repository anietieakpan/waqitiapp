package com.waqiti.payment.core.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.core.model.*;
import com.waqiti.payment.core.exception.PaymentProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class AdyenPaymentProvider implements PaymentProvider {

    private final WebClient adyenWebClient;
    private final ObjectMapper objectMapper;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    @Value("${adyen.api-key}")
    private String apiKey;
    
    @Value("${adyen.merchant-account}")
    private String merchantAccount;
    
    @Value("${adyen.environment:live}")
    private String environment;
    
    private static final Set<PaymentType> SUPPORTED_TYPES = Set.of(
        PaymentType.P2P,
        PaymentType.MERCHANT,
        PaymentType.CARD,
        PaymentType.REFUND,
        PaymentType.INTERNATIONAL
    );

    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        log.info("Processing Adyen payment: {}", request.getPaymentId());
        
        try {
            ValidationResult validation = validatePayment(request);
            if (!validation.isValid()) {
                return PaymentResult.error(validation.getErrorMessage());
            }
            
            Map<String, Object> paymentRequest = createAdyenPaymentRequest(request);
            Map<String, Object> result = executeAdyenPayment(paymentRequest);
            
            return parseAdyenResponse(result, request);
            
        } catch (Exception e) {
            log.error("Adyen payment failed: ", e);
            return PaymentResult.error("Adyen payment failed: " + e.getMessage());
        }
    }

    @Override
    public PaymentResult refundPayment(RefundRequest request) {
        log.info("Processing Adyen refund: {}", request.getOriginalPaymentId());

        try {
            Map<String, Object> refundRequest = createAdyenRefundRequest(request);
            Map<String, Object> result = callAdyenApi("/refund", refundRequest);

            return parseRefundResponse(result, request);

        } catch (Exception e) {
            log.error("Adyen refund failed: ", e);
            return PaymentResult.error("Adyen refund failed: " + e.getMessage());
        }
    }

    @Override
    public PaymentStatus getPaymentStatus(String paymentId) {
        try {
            // Adyen uses PSP reference for status lookup
            Map<String, Object> statusRequest = Map.of(
                "originalReference", paymentId
            );
            
            Map<String, Object> result = callAdyenApi("/authorise", statusRequest);
            String resultCode = (String) result.get("resultCode");
            
            return switch (resultCode) {
                case "Authorised" -> PaymentStatus.COMPLETED;
                case "Received", "RedirectShopper" -> PaymentStatus.PROCESSING;
                case "Pending" -> PaymentStatus.PENDING;
                case "Refused", "Error", "Cancelled" -> PaymentStatus.FAILED;
                default -> PaymentStatus.PENDING;
            };
            
        } catch (Exception e) {
            log.error("Failed to get Adyen payment status: ", e);
            return PaymentStatus.FAILED;
        }
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.ADYEN;
    }

    @Override
    public boolean canHandle(PaymentType paymentType) {
        return SUPPORTED_TYPES.contains(paymentType);
    }

    @Override
    public ValidationResult validatePayment(PaymentRequest request) {
        if (request.getAmount().compareTo(new BigDecimal("0.01")) < 0) {
            return ValidationResult.invalid("Minimum amount for Adyen is $0.01");
        }
        
        if (request.getAmount().compareTo(new BigDecimal("100000")) > 0) {
            return ValidationResult.invalid("Maximum amount for Adyen is $100,000");
        }
        
        if (request.getPaymentMethodId() == null) {
            return ValidationResult.invalid("Payment method is required for Adyen");
        }
        
        return ValidationResult.valid();
    }

    @Override
    public FeeCalculation calculateFees(PaymentRequest request) {
        // Adyen fees vary by region and card type - using EU/US rates
        // Interchange+ pricing: typically 0.05% - 1.25% + scheme fees
        BigDecimal interchangeFee = request.getAmount().multiply(new BigDecimal("0.0125"));
        BigDecimal schemeFee = new BigDecimal("0.10");
        BigDecimal adyenFee = request.getAmount().multiply(new BigDecimal("0.001")); // 0.1%
        BigDecimal totalFee = interchangeFee.add(schemeFee).add(adyenFee);
        
        return FeeCalculation.builder()
            .processingFee(adyenFee)
            .networkFee(interchangeFee.add(schemeFee))
            .totalFees(totalFee)
            .feeStructure("Interchange+ 1.25% + €0.10 + Adyen 0.1%")
            .build();
    }

    @Override
    public boolean isAvailable() {
        // Check if API key and merchant account are configured
        if (apiKey == null || apiKey.isEmpty() || merchantAccount == null || merchantAccount.isEmpty()) {
            log.warn("Adyen provider not available: missing API key or merchant account");
            return false;
        }

        try {
            // Simple health check - test connectivity
            Map<String, Object> healthRequest = Map.of(
                "merchantAccount", merchantAccount
            );
            callAdyenApi("/directory", healthRequest);
            return true;
        } catch (Exception e) {
            log.error("Adyen availability check failed: ", e);
            return false;
        }
    }

    @Override
    public ProviderCapabilities getCapabilities() {
        return ProviderCapabilities.builder()
            .supportsRefunds(true)
            .supportsPartialRefunds(true)
            .supportsRecurring(true)
            .supportsInstantTransfer(true)
            .supportsInternational(true)
            .supportsMultiCurrency(true)
            .supports3DS(true)
            .supportedCurrencies(Set.of("USD", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "NOK", "SEK", "DKK"))
            .maxAmount(new BigDecimal("100000"))
            .minAmount(new BigDecimal("0.01"))
            .build();
    }

    @Override
    public ProviderConfiguration getConfiguration() {
        return ProviderConfiguration.builder()
            .providerType(ProviderType.ADYEN)
            .isEnabled(apiKey != null && !apiKey.isEmpty() && merchantAccount != null)
            .configuration(Map.of(
                "environment", environment,
                "merchant_account", merchantAccount,
                "api_version", "v70",
                "supports_3ds", true,
                "supports_multicurrency", true,
                "webhook_hmac_key", "***"
            ))
            .build();
    }

    private Map<String, Object> createAdyenPaymentRequest(PaymentRequest request) {
        Map<String, Object> amount = Map.of(
            "value", request.getAmount().multiply(new BigDecimal("100")).longValue(), // Convert to minor units
            "currency", "USD"
        );
        
        Map<String, Object> paymentMethod = Map.of(
            "type", "scheme",
            "storedPaymentMethodId", request.getPaymentMethodId()
        );
        
        Map<String, Object> params = new HashMap<>();
        params.put("merchantAccount", merchantAccount);
        params.put("amount", amount);
        params.put("paymentMethod", paymentMethod);
        params.put("reference", request.getPaymentId().toString());
        params.put("shopperReference", request.getFromUserId());
        params.put("capture", true);
        
        // Add 3D Secure configuration
        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put("allow3DS2", "true");
        additionalData.put("executeThreeD", "true");
        params.put("additionalData", additionalData);
        
        // Add metadata
        Map<String, String> metadata = new HashMap<>();
        metadata.put("waqiti_payment_id", request.getPaymentId().toString());
        metadata.put("payment_type", request.getType().name());
        metadata.put("from_user", request.getFromUserId());
        metadata.put("to_user", request.getToUserId());
        params.put("metadata", metadata);
        
        return params;
    }

    private Map<String, Object> createAdyenRefundRequest(RefundRequest request) {
        Map<String, Object> amount = Map.of(
            "value", request.getAmount().multiply(new BigDecimal("100")).longValue(),
            "currency", "USD"
        );
        
        return Map.of(
            "merchantAccount", merchantAccount,
            "originalReference", request.getOriginalPaymentId(),
            "modificationAmount", amount,
            "reference", "REF-" + System.currentTimeMillis() + "-" + SECURE_RANDOM.nextInt(1000)
        );
    }

    private Map<String, Object> executeAdyenPayment(Map<String, Object> paymentRequest) {
        return callAdyenApi("/payments", paymentRequest);
    }

    private PaymentResult parseAdyenResponse(Map<String, Object> response, PaymentRequest request) {
        String pspReference = (String) response.get("pspReference");
        String resultCode = (String) response.get("resultCode");
        
        PaymentResult.PaymentResultBuilder builder = PaymentResult.builder()
            .paymentId(request.getPaymentId())
            .transactionId(pspReference)
            .amount(request.getAmount())
            .processedAt(LocalDateTime.now())
            .providerTransactionId(pspReference)
            .providerResponse(convertMapToJson(response));
        
        switch (resultCode) {
            case "Authorised" -> {
                return builder
                    .status(PaymentStatus.COMPLETED)
                    .message("Adyen payment authorized successfully")
                    .build();
            }
            case "Received" -> {
                return builder
                    .status(PaymentStatus.PROCESSING)
                    .message("Adyen payment received and processing")
                    .build();
            }
            case "Pending" -> {
                return builder
                    .status(PaymentStatus.PENDING)
                    .message("Adyen payment pending approval")
                    .build();
            }
            case "RedirectShopper" -> {
                Map<String, Object> action = (Map<String, Object>) response.get("action");
                return builder
                    .status(PaymentStatus.REVIEW_REQUIRED)
                    .message("Adyen payment requires additional authentication")
                    .additionalData(action)
                    .build();
            }
            default -> {
                return builder
                    .status(PaymentStatus.FAILED)
                    .errorMessage("Adyen payment failed with result: " + resultCode)
                    .build();
            }
        }
    }

    private PaymentResult parseRefundResponse(Map<String, Object> response, RefundRequest request) {
        String pspReference = (String) response.get("pspReference");
        String response_code = (String) response.get("response");
        
        if ("[refund-received]".equals(response_code)) {
            return PaymentResult.builder()
                .transactionId(pspReference)
                .status(PaymentStatus.REFUNDED)
                .amount(request.getAmount())
                .message("Adyen refund received and processing")
                .processedAt(LocalDateTime.now())
                .providerTransactionId(pspReference)
                .providerResponse(convertMapToJson(response))
                .build();
        } else {
            return PaymentResult.builder()
                .status(PaymentStatus.FAILED)
                .errorMessage("Adyen refund failed: " + response_code)
                .processedAt(LocalDateTime.now())
                .providerResponse(convertMapToJson(response))
                .build();
        }
    }

    private String convertMapToJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Failed to convert map to JSON, returning empty object", e);
            return "{}";
        }
    }

    private Map<String, Object> callAdyenApi(String endpoint, Map<String, Object> params) {
        try {
            WebClient.RequestBodySpec request = adyenWebClient
                .post()
                .uri(endpoint)
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json");
                
            if (params != null) {
                return request.bodyValue(params)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            } else {
                return request
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            }
        } catch (Exception e) {
            throw new PaymentProviderException("Adyen API call failed", "Adyen", endpoint, e);
        }
    }
}