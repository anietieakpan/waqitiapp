package com.waqiti.common.client;

import com.waqiti.common.resilience.ResilientServiceExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

/**
 * External service client with built-in circuit breakers and resilience patterns
 * Demonstrates how to properly wrap external service calls with fault tolerance
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalServiceClient {

    private final RestTemplate restTemplate;
    private final ResilientServiceExecutor resilientExecutor;

    @Value("${external.services.payment.url:https://api.payment-gateway.com}")
    private String paymentServiceUrl;

    @Value("${external.services.kyc.url:https://api.kyc-provider.com}")
    private String kycServiceUrl;

    @Value("${external.services.currency.url:https://api.currency-exchange.com}")
    private String currencyServiceUrl;

    @Value("${external.services.notification.url:https://api.notification-service.com}")
    private String notificationServiceUrl;

    /**
     * Process payment with comprehensive resilience patterns
     */
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("Processing payment with resilience patterns: {}", request.getTransactionId());
        
        return resilientExecutor.executePaymentCall(() -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "Bearer " + getPaymentServiceToken());
                
                HttpEntity<PaymentRequest> entity = new HttpEntity<>(request, headers);
                
                ResponseEntity<PaymentResponse> response = restTemplate.postForEntity(
                    paymentServiceUrl + "/process", entity, PaymentResponse.class);
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    log.info("Payment processed successfully: {}", request.getTransactionId());
                    return response.getBody();
                } else {
                    throw new ExternalServiceException("Payment processing failed with status: " + response.getStatusCode());
                }
                
            } catch (Exception e) {
                log.error("Payment processing failed for transaction: {}", request.getTransactionId(), e);
                throw new ExternalServiceException("Payment processing failed", e);
            }
        });
    }

    /**
     * Verify KYC with resilience patterns
     */
    public KycVerificationResponse verifyKyc(KycVerificationRequest request) {
        log.info("Verifying KYC with resilience patterns: {}", request.getUserId());
        
        return resilientExecutor.executeKycCall(() -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "Bearer " + getKycServiceToken());
                
                HttpEntity<KycVerificationRequest> entity = new HttpEntity<>(request, headers);
                
                ResponseEntity<KycVerificationResponse> response = restTemplate.postForEntity(
                    kycServiceUrl + "/verify", entity, KycVerificationResponse.class);
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    log.info("KYC verification completed: {}", request.getUserId());
                    return response.getBody();
                } else {
                    throw new ExternalServiceException("KYC verification failed with status: " + response.getStatusCode());
                }
                
            } catch (Exception e) {
                log.error("KYC verification failed for user: {}", request.getUserId(), e);
                throw new ExternalServiceException("KYC verification failed", e);
            }
        });
    }

    /**
     * Get exchange rates with circuit breaker
     */
    public ExchangeRateResponse getExchangeRates(String baseCurrency, String targetCurrency) {
        log.debug("Fetching exchange rates: {} to {}", baseCurrency, targetCurrency);
        
        return resilientExecutor.executeWithCircuitBreakerAndRetry("currency-exchange", "default", () -> {
            try {
                String url = String.format("%s/rates?base=%s&target=%s", 
                    currencyServiceUrl, baseCurrency, targetCurrency);
                
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + getCurrencyServiceToken());
                
                HttpEntity<?> entity = new HttpEntity<>(headers);
                
                ResponseEntity<ExchangeRateResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, ExchangeRateResponse.class);
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    return response.getBody();
                } else {
                    throw new ExternalServiceException("Exchange rate fetch failed with status: " + response.getStatusCode());
                }
                
            } catch (Exception e) {
                log.error("Failed to fetch exchange rates: {} to {}", baseCurrency, targetCurrency, e);
                throw new ExternalServiceException("Exchange rate fetch failed", e);
            }
        });
    }

    /**
     * Send notification asynchronously with resilience
     */
    public CompletableFuture<NotificationResponse> sendNotification(NotificationRequest request) {
        log.info("Sending notification with resilience patterns: {}", request.getRecipient());
        
        return resilientExecutor.executeNotificationCall(() -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("Authorization", "Bearer " + getNotificationServiceToken());
                    
                    HttpEntity<NotificationRequest> entity = new HttpEntity<>(request, headers);
                    
                    ResponseEntity<NotificationResponse> response = restTemplate.postForEntity(
                        notificationServiceUrl + "/send", entity, NotificationResponse.class);
                    
                    if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        log.info("Notification sent successfully: {}", request.getRecipient());
                        return response.getBody();
                    } else {
                        throw new ExternalServiceException("Notification send failed with status: " + response.getStatusCode());
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to send notification to: {}", request.getRecipient(), e);
                    throw new ExternalServiceException("Notification send failed", e);
                }
            });
        });
    }

    /**
     * Perform health check on external services
     */
    public ServiceHealthStatus checkExternalServiceHealth(String serviceName) {
        return resilientExecutor.executeWithCircuitBreaker(serviceName, () -> {
            try {
                String healthUrl = getHealthUrlForService(serviceName);
                
                ResponseEntity<String> response = restTemplate.getForEntity(
                    healthUrl, String.class);
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    return ServiceHealthStatus.builder()
                        .serviceName(serviceName)
                        .status("UP")
                        .responseTime(System.currentTimeMillis())
                        .circuitBreakerState(resilientExecutor.getCircuitBreakerState(serviceName).toString())
                        .build();
                } else {
                    return ServiceHealthStatus.builder()
                        .serviceName(serviceName)
                        .status("DOWN")
                        .responseTime(System.currentTimeMillis())
                        .error("Health check returned: " + response.getStatusCode())
                        .circuitBreakerState(resilientExecutor.getCircuitBreakerState(serviceName).toString())
                        .build();
                }
                
            } catch (Exception e) {
                log.error("Health check failed for service: {}", serviceName, e);
                return ServiceHealthStatus.builder()
                    .serviceName(serviceName)
                    .status("DOWN")
                    .responseTime(System.currentTimeMillis())
                    .error(e.getMessage())
                    .circuitBreakerState(resilientExecutor.getCircuitBreakerState(serviceName).toString())
                    .build();
            }
        });
    }

    // Helper methods for token management (would be implemented with actual auth)
    
    private String getPaymentServiceToken() {
        return "mock-payment-token"; // In production, implement proper token management
    }
    
    private String getKycServiceToken() {
        return "mock-kyc-token";
    }
    
    private String getCurrencyServiceToken() {
        return "mock-currency-token";
    }
    
    private String getNotificationServiceToken() {
        return "mock-notification-token";
    }
    
    private String getHealthUrlForService(String serviceName) {
        switch (serviceName) {
            case "payment-gateway": return paymentServiceUrl + "/health";
            case "kyc-service": return kycServiceUrl + "/health";
            case "currency-exchange": return currencyServiceUrl + "/health";
            case "notification-service": return notificationServiceUrl + "/health";
            default: return "http://localhost:8080/health";
        }
    }

    // DTOs for external service communication
    
    @lombok.Data
    @lombok.Builder
    public static class PaymentRequest {
        private String transactionId;
        private String amount;
        private String currency;
        private String merchantId;
        private String cardToken;
    }

    @lombok.Data
    @lombok.Builder
    public static class PaymentResponse {
        private String transactionId;
        private String status;
        private String authorizationCode;
        private String errorMessage;
    }

    @lombok.Data
    @lombok.Builder
    public static class KycVerificationRequest {
        private String userId;
        private String documentType;
        private String documentNumber;
        private String fullName;
        private String dateOfBirth;
    }

    @lombok.Data
    @lombok.Builder
    public static class KycVerificationResponse {
        private String userId;
        private String verificationStatus;
        private String riskLevel;
        private String reason;
    }

    @lombok.Data
    @lombok.Builder
    public static class ExchangeRateResponse {
        private String baseCurrency;
        private String targetCurrency;
        private double rate;
        private long timestamp;
    }

    @lombok.Data
    @lombok.Builder
    public static class NotificationRequest {
        private String recipient;
        private String type;
        private String subject;
        private String message;
        private String channel;
    }

    @lombok.Data
    @lombok.Builder
    public static class NotificationResponse {
        private String notificationId;
        private String status;
        private String deliveryTime;
    }

    @lombok.Data
    @lombok.Builder
    public static class ServiceHealthStatus {
        private String serviceName;
        private String status;
        private long responseTime;
        private String error;
        private String circuitBreakerState;
    }

    public static class ExternalServiceException extends RuntimeException {
        public ExternalServiceException(String message) {
            super(message);
        }
        
        public ExternalServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}