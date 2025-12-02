package com.waqiti.payment.core.provider;

import com.waqiti.payment.core.model.*;
import com.waqiti.payment.core.exception.PaymentProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class SquarePaymentProvider implements PaymentProvider {

    private final WebClient squareWebClient;
    
    @Value("${square.access-token}")
    private String accessToken;
    
    @Value("${square.application-id}")
    private String applicationId;
    
    @Value("${square.environment:production}")
    private String environment;
    
    private static final Set<PaymentType> SUPPORTED_TYPES = Set.of(
        PaymentType.P2P,
        PaymentType.MERCHANT,
        PaymentType.CARD,
        PaymentType.REFUND,
        PaymentType.IN_STORE
    );

    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        log.info("Processing Square payment: {}", request.getPaymentId());
        
        try {
            ValidationResult validation = validatePayment(request);
            if (!validation.isValid()) {
                return PaymentResult.error(validation.getErrorMessage());
            }
            
            Map<String, Object> paymentRequest = createSquarePaymentRequest(request);
            Map<String, Object> result = executeSquarePayment(paymentRequest);
            
            return parseSquareResponse(result, request);
            
        } catch (Exception e) {
            log.error("Square payment failed: ", e);
            return PaymentResult.error("Square payment failed: " + e.getMessage());
        }
    }

    @Override
    public PaymentResult processRefund(RefundRequest request) {
        log.info("Processing Square refund: {}", request.getOriginalPaymentId());
        
        try {
            Map<String, Object> refundRequest = createSquareRefundRequest(request);
            Map<String, Object> result = callSquareApi("/v2/refunds", refundRequest);
            
            return parseRefundResponse(result, request);
            
        } catch (Exception e) {
            log.error("Square refund failed: ", e);
            return PaymentResult.error("Square refund failed: " + e.getMessage());
        }
    }

    @Override
    public PaymentStatus getPaymentStatus(String paymentId) {
        try {
            Map<String, Object> result = callSquareApi("/v2/payments/" + paymentId, null);
            Map<String, Object> payment = (Map<String, Object>) result.get("payment");
            String status = (String) payment.get("status");
            
            return switch (status) {
                case "COMPLETED" -> PaymentStatus.COMPLETED;
                case "APPROVED" -> PaymentStatus.PROCESSING;
                case "PENDING" -> PaymentStatus.PENDING;
                case "CANCELED" -> PaymentStatus.CANCELLED;
                case "FAILED" -> PaymentStatus.FAILED;
                default -> PaymentStatus.PENDING;
            };
            
        } catch (Exception e) {
            log.error("Failed to get Square payment status: ", e);
            return PaymentStatus.FAILED;
        }
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.SQUARE;
    }

    @Override
    public boolean canHandle(PaymentType paymentType) {
        return SUPPORTED_TYPES.contains(paymentType);
    }

    @Override
    public ValidationResult validatePayment(PaymentRequest request) {
        if (request.getAmount().compareTo(new BigDecimal("0.01")) < 0) {
            return ValidationResult.invalid("Minimum amount for Square is $0.01");
        }
        
        if (request.getAmount().compareTo(new BigDecimal("50000")) > 0) {
            return ValidationResult.invalid("Maximum amount for Square is $50,000");
        }
        
        if (request.getPaymentMethodId() == null) {
            return ValidationResult.invalid("Payment method is required for Square");
        }
        
        return ValidationResult.valid();
    }

    @Override
    public FeeCalculation calculateFees(PaymentRequest request) {
        // Square fees: 2.6% + $0.10 for card-present, 2.9% + $0.30 for card-not-present
        boolean isCardPresent = request.getType() == PaymentType.IN_STORE;
        
        BigDecimal percentageRate = isCardPresent ? 
            new BigDecimal("0.026") : new BigDecimal("0.029");
        BigDecimal fixedFee = isCardPresent ? 
            new BigDecimal("0.10") : new BigDecimal("0.30");
            
        BigDecimal percentageFee = request.getAmount().multiply(percentageRate);
        BigDecimal totalFee = percentageFee.add(fixedFee);
        
        return FeeCalculation.builder()
            .processingFee(totalFee)
            .networkFee(BigDecimal.ZERO)
            .totalFees(totalFee)
            .feeStructure(isCardPresent ? "2.6% + $0.10" : "2.9% + $0.30")
            .build();
    }

    @Override
    public boolean isHealthy() {
        try {
            callSquareApi("/v2/locations", null);
            return true;
        } catch (Exception e) {
            log.error("Square health check failed: ", e);
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
            .supportsInternational(false)
            .supportsInStore(true)
            .supportedCurrencies(Set.of("USD", "CAD", "AUD", "GBP", "EUR"))
            .maxAmount(new BigDecimal("50000"))
            .minAmount(new BigDecimal("0.01"))
            .build();
    }

    @Override
    public ProviderConfiguration getConfiguration() {
        return ProviderConfiguration.builder()
            .providerType(ProviderType.SQUARE)
            .isEnabled(accessToken != null && !accessToken.isEmpty())
            .configuration(Map.of(
                "api_version", "2023-12-13",
                "environment", environment,
                "webhook_signature_key", "whsig_***",
                "application_id", applicationId,
                "supports_in_store", true
            ))
            .build();
    }

    private Map<String, Object> createSquarePaymentRequest(PaymentRequest request) {
        Map<String, Object> amountMoney = Map.of(
            "amount", request.getAmount().multiply(new BigDecimal("100")).longValue(), // Convert to cents
            "currency", "USD"
        );
        
        Map<String, Object> params = new HashMap<>();
        params.put("source_id", request.getPaymentMethodId());
        params.put("idempotency_key", UUID.randomUUID().toString());
        params.put("amount_money", amountMoney);
        params.put("autocomplete", true);
        params.put("note", request.getDescription());
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("waqiti_payment_id", request.getPaymentId().toString());
        metadata.put("from_user", request.getFromUserId());
        metadata.put("to_user", request.getToUserId());
        metadata.put("payment_type", request.getType().name());
        
        return params;
    }

    private Map<String, Object> createSquareRefundRequest(RefundRequest request) {
        Map<String, Object> amountMoney = Map.of(
            "amount", request.getAmount().multiply(new BigDecimal("100")).longValue(),
            "currency", "USD"
        );
        
        return Map.of(
            "idempotency_key", UUID.randomUUID().toString(),
            "amount_money", amountMoney,
            "payment_id", request.getOriginalPaymentId(),
            "reason", request.getReason()
        );
    }

    private Map<String, Object> executeSquarePayment(Map<String, Object> paymentRequest) {
        return callSquareApi("/v2/payments", paymentRequest);
    }

    private PaymentResult parseSquareResponse(Map<String, Object> response, PaymentRequest request) {
        Map<String, Object> payment = (Map<String, Object>) response.get("payment");
        String id = (String) payment.get("id");
        String status = (String) payment.get("status");
        
        PaymentResult.PaymentResultBuilder builder = PaymentResult.builder()
            .paymentId(request.getPaymentId())
            .transactionId(id)
            .amount(request.getAmount())
            .processedAt(LocalDateTime.now())
            .providerTransactionId(id)
            .providerResponse(response);
        
        switch (status) {
            case "COMPLETED" -> {
                return builder
                    .status(PaymentStatus.COMPLETED)
                    .message("Square payment completed successfully")
                    .build();
            }
            case "APPROVED" -> {
                return builder
                    .status(PaymentStatus.PROCESSING)
                    .message("Square payment approved and processing")
                    .build();
            }
            case "PENDING" -> {
                return builder
                    .status(PaymentStatus.PENDING)
                    .message("Square payment pending approval")
                    .build();
            }
            case "FAILED" -> {
                return builder
                    .status(PaymentStatus.FAILED)
                    .errorMessage("Square payment failed")
                    .build();
            }
            case "CANCELED" -> {
                return builder
                    .status(PaymentStatus.CANCELLED)
                    .message("Square payment was canceled")
                    .build();
            }
            default -> {
                return builder
                    .status(PaymentStatus.FAILED)
                    .errorMessage("Unknown Square payment status: " + status)
                    .build();
            }
        }
    }

    private PaymentResult parseRefundResponse(Map<String, Object> response, RefundRequest request) {
        Map<String, Object> refund = (Map<String, Object>) response.get("refund");
        String id = (String) refund.get("id");
        String status = (String) refund.get("status");
        
        if ("COMPLETED".equals(status)) {
            return PaymentResult.builder()
                .transactionId(id)
                .status(PaymentStatus.REFUNDED)
                .amount(request.getAmount())
                .message("Square refund completed successfully")
                .processedAt(LocalDateTime.now())
                .providerTransactionId(id)
                .providerResponse(response)
                .build();
        } else {
            return PaymentResult.builder()
                .status(PaymentStatus.FAILED)
                .errorMessage("Square refund failed with status: " + status)
                .processedAt(LocalDateTime.now())
                .providerResponse(response)
                .build();
        }
    }

    private Map<String, Object> callSquareApi(String endpoint, Map<String, Object> params) {
        try {
            WebClient.RequestBodySpec request = squareWebClient
                .post()
                .uri(endpoint)
                .header("Authorization", "Bearer " + accessToken)
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
            throw new PaymentProviderException("Square API call failed", "Square", endpoint, e);
        }
    }
}