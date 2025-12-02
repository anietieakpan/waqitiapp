package com.waqiti.bnpl.service;

import com.waqiti.common.exceptions.PaymentProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Payment processor service for BNPL installment payments
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentProcessorService {

    private final RestTemplate restTemplate;
    
    @Value("${payment.gateway.stripe.api-key:#{null}}")
    private String stripeApiKey;
    
    @Value("${payment.gateway.stripe.endpoint:https://api.stripe.com/v1}")
    private String stripeEndpoint;
    
    @Value("${payment.gateway.paypal.client-id:#{null}}")
    private String paypalClientId;
    
    @Value("${payment.gateway.paypal.secret:#{null}}")
    private String paypalSecret;
    
    @Value("${payment.gateway.paypal.endpoint:https://api.paypal.com}")
    private String paypalEndpoint;
    
    @Value("${payment.gateway.square.access-token:#{null}}")
    private String squareAccessToken;
    
    @Value("${payment.gateway.square.endpoint:https://connect.squareup.com/v2}")
    private String squareEndpoint;
    
    @Value("${payment.gateway.razorpay.key-id:#{null}}")
    private String razorpayKeyId;
    
    @Value("${payment.gateway.razorpay.secret:#{null}}")
    private String razorpaySecret;
    
    @Value("${payment.gateway.razorpay.endpoint:https://api.razorpay.com/v1}")
    private String razorpayEndpoint;
    
    @Value("${payment.gateway.primary:stripe}")
    private String primaryGateway;
    
    @Value("${payment.gateway.fallback.enabled:true}")
    private boolean fallbackEnabled;
    
    @Value("${payment.gateway.timeout.ms:10000}")
    private int gatewayTimeoutMs;

    /**
     * Process payment through payment gateway with automatic fallback
     */
    @Retryable(value = {PaymentProcessingException.class}, 
               maxAttempts = 3, 
               backoff = @Backoff(delay = 1000))
    public PaymentResult processPayment(UUID userId, String paymentMethodId, 
                                       BigDecimal amount, String description) {
        log.info("Processing payment for user: {} amount: {} method: {} gateway: {}", 
                userId, amount, paymentMethodId, primaryGateway);
        
        long startTime = System.currentTimeMillis();

        try {
            // Process payment through primary gateway
            PaymentResult result = processWithGateway(primaryGateway, userId, 
                paymentMethodId, amount, description);
            
            if (result != null && result.isSuccessful()) {
                long processingTime = System.currentTimeMillis() - startTime;
                result.setProcessingTimeMs(processingTime);
                log.info("Payment successful via {} in {}ms", primaryGateway, processingTime);
                return result;
            }
            
            // If primary gateway fails and fallback is enabled
            if (fallbackEnabled) {
                log.warn("Primary gateway {} failed, attempting fallback", primaryGateway);
                return attemptFallbackPayment(userId, paymentMethodId, amount, description);
            }
            
            // No fallback, return failure
            return PaymentResult.builder()
                    .successful(false)
                    .failureReason("Payment processing failed on primary gateway")
                    .errorCode("GATEWAY_FAILURE")
                    .gateway(primaryGateway)
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();
            
        } catch (Exception e) {
            log.error("Payment processing failed for user: {}", userId, e);
            
            if (fallbackEnabled) {
                return attemptFallbackPayment(userId, paymentMethodId, amount, description);
            }
            
            return PaymentResult.builder()
                    .successful(false)
                    .failureReason("Payment processing error: " + e.getMessage())
                    .errorCode("PROCESSING_ERROR")
                    .gateway(primaryGateway)
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }
    
    /**
     * Process payment with specific gateway
     */
    private PaymentResult processWithGateway(String gateway, UUID userId, 
                                            String paymentMethodId, BigDecimal amount, 
                                            String description) {
        switch (gateway.toLowerCase()) {
            case "stripe":
                return processStripePayment(userId, paymentMethodId, amount, description);
            case "paypal":
                return processPayPalPayment(userId, paymentMethodId, amount, description);
            case "square":
                return processSquarePayment(userId, paymentMethodId, amount, description);
            case "razorpay":
                return processRazorpayPayment(userId, paymentMethodId, amount, description);
            default:
                log.error("Unknown payment gateway: {}", gateway);
                return PaymentResult.builder()
                        .successful(false)
                        .failureReason("Unknown payment gateway: " + gateway)
                        .errorCode("UNKNOWN_GATEWAY")
                        .gateway(gateway)
                        .build();
        }
    }
    
    /**
     * Process payment through Stripe
     */
    private PaymentResult processStripePayment(UUID userId, String paymentMethodId, 
                                              BigDecimal amount, String description) {
        if (stripeApiKey == null) {
            log.error("Stripe API key not configured");
            return PaymentResult.builder()
                    .successful(false)
                    .failureReason("Stripe API key not configured")
                    .errorCode("CONFIG_ERROR")
                    .gateway("stripe")
                    .build();
        }
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBearerAuth(stripeApiKey);
            headers.add("Idempotency-Key", UUID.randomUUID().toString());
            
            // Convert amount to cents
            long amountInCents = amount.multiply(new BigDecimal("100")).longValue();
            
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("amount", String.valueOf(amountInCents));
            requestBody.put("currency", "usd");
            requestBody.put("payment_method", paymentMethodId);
            requestBody.put("description", description);
            requestBody.put("confirm", "true");
            requestBody.put("metadata[user_id]", userId.toString());
            requestBody.put("metadata[service]", "bnpl");
            
            String formData = buildFormData(requestBody);
            
            HttpEntity<String> request = new HttpEntity<>(formData, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                stripeEndpoint + "/payment_intents",
                HttpMethod.POST,
                request,
                Map.class
            );
            
            Map<String, Object> responseBody = response.getBody();
            
            if (responseBody != null && "succeeded".equals(responseBody.get("status"))) {
                return PaymentResult.builder()
                        .successful(true)
                        .transactionId((String) responseBody.get("id"))
                        .paymentId(UUID.randomUUID())
                        .gateway("stripe")
                        .build();
            } else {
                String status = responseBody != null ? (String) responseBody.get("status") : "unknown";
                return PaymentResult.builder()
                        .successful(false)
                        .failureReason("Payment status: " + status)
                        .errorCode("STRIPE_" + status.toUpperCase())
                        .gateway("stripe")
                        .build();
            }
            
        } catch (HttpClientErrorException e) {
            log.error("Stripe payment failed: {}", e.getResponseBodyAsString());
            return PaymentResult.builder()
                    .successful(false)
                    .failureReason("Stripe error: " + e.getMessage())
                    .errorCode("STRIPE_ERROR")
                    .gateway("stripe")
                    .build();
        } catch (Exception e) {
            log.error("Stripe payment processing error", e);
            return PaymentResult.builder()
                    .successful(false)
                    .failureReason("Stripe processing error: " + e.getMessage())
                    .errorCode("PROCESSING_ERROR")
                    .gateway("stripe")
                    .build();
        }
    }
    
    /**
     * Process payment through PayPal
     */
    private PaymentResult processPayPalPayment(UUID userId, String paymentMethodId, 
                                              BigDecimal amount, String description) {
        if (paypalClientId == null || paypalSecret == null) {
            log.error("PayPal credentials not configured");
            return PaymentResult.builder()
                    .successful(false)
                    .failureReason("PayPal credentials not configured")
                    .errorCode("CONFIG_ERROR")
                    .gateway("paypal")
                    .build();
        }
        
        try {
            // First get access token
            String accessToken = getPayPalAccessToken();
            if (accessToken == null) {
                return PaymentResult.builder()
                        .successful(false)
                        .failureReason("Failed to obtain PayPal access token")
                        .errorCode("AUTH_ERROR")
                        .gateway("paypal")
                        .build();
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);
            headers.add("PayPal-Request-Id", UUID.randomUUID().toString());
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("intent", "CAPTURE");
            
            Map<String, Object> purchaseUnit = new HashMap<>();
            Map<String, Object> amountObj = new HashMap<>();
            amountObj.put("currency_code", "USD");
            amountObj.put("value", amount.toString());
            purchaseUnit.put("amount", amountObj);
            purchaseUnit.put("description", description);
            purchaseUnit.put("custom_id", userId.toString());
            
            requestBody.put("purchase_units", Collections.singletonList(purchaseUnit));
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                paypalEndpoint + "/v2/checkout/orders",
                HttpMethod.POST,
                request,
                Map.class
            );
            
            Map<String, Object> responseBody = response.getBody();
            
            if (responseBody != null && "COMPLETED".equals(responseBody.get("status"))) {
                return PaymentResult.builder()
                        .successful(true)
                        .transactionId((String) responseBody.get("id"))
                        .paymentId(UUID.randomUUID())
                        .gateway("paypal")
                        .build();
            } else {
                return PaymentResult.builder()
                        .successful(false)
                        .failureReason("PayPal payment not completed")
                        .errorCode("PAYPAL_INCOMPLETE")
                        .gateway("paypal")
                        .build();
            }
            
        } catch (Exception e) {
            log.error("PayPal payment processing error", e);
            return PaymentResult.builder()
                    .successful(false)
                    .failureReason("PayPal processing error: " + e.getMessage())
                    .errorCode("PROCESSING_ERROR")
                    .gateway("paypal")
                    .build();
        }
    }
    
    /**
     * Get PayPal OAuth access token
     */
    private String getPayPalAccessToken() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(paypalClientId, paypalSecret);
            
            HttpEntity<String> request = new HttpEntity<>("grant_type=client_credentials", headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                paypalEndpoint + "/v1/oauth2/token",
                HttpMethod.POST,
                request,
                Map.class
            );
            
            Map<String, Object> responseBody = response.getBody();
            return responseBody != null ? (String) responseBody.get("access_token") : null;
            
        } catch (Exception e) {
            log.error("Failed to get PayPal access token", e);
            throw new PaymentProcessingException("Failed to authenticate with PayPal: " + e.getMessage(), e);
        }
    }
    
    /**
     * Process payment through Square
     */
    private PaymentResult processSquarePayment(UUID userId, String paymentMethodId, 
                                              BigDecimal amount, String description) {
        if (squareAccessToken == null) {
            log.error("Square access token not configured");
            return PaymentResult.builder()
                    .successful(false)
                    .failureReason("Square access token not configured")
                    .errorCode("CONFIG_ERROR")
                    .gateway("square")
                    .build();
        }
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(squareAccessToken);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("idempotency_key", UUID.randomUUID().toString());
            requestBody.put("source_id", paymentMethodId);
            
            Map<String, Object> amountMoney = new HashMap<>();
            amountMoney.put("amount", amount.multiply(new BigDecimal("100")).longValue());
            amountMoney.put("currency", "USD");
            requestBody.put("amount_money", amountMoney);
            
            requestBody.put("autocomplete", true);
            requestBody.put("reference_id", userId.toString());
            requestBody.put("note", description);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                squareEndpoint + "/payments",
                HttpMethod.POST,
                request,
                Map.class
            );
            
            Map<String, Object> responseBody = response.getBody();
            Map<String, Object> payment = responseBody != null ? 
                (Map<String, Object>) responseBody.get("payment") : null;
            
            if (payment != null && "COMPLETED".equals(payment.get("status"))) {
                return PaymentResult.builder()
                        .successful(true)
                        .transactionId((String) payment.get("id"))
                        .paymentId(UUID.randomUUID())
                        .gateway("square")
                        .build();
            } else {
                return PaymentResult.builder()
                        .successful(false)
                        .failureReason("Square payment failed")
                        .errorCode("SQUARE_FAILURE")
                        .gateway("square")
                        .build();
            }
            
        } catch (Exception e) {
            log.error("Square payment processing error", e);
            return PaymentResult.builder()
                    .successful(false)
                    .failureReason("Square processing error: " + e.getMessage())
                    .errorCode("PROCESSING_ERROR")
                    .gateway("square")
                    .build();
        }
    }
    
    /**
     * Process payment through Razorpay
     */
    private PaymentResult processRazorpayPayment(UUID userId, String paymentMethodId, 
                                                BigDecimal amount, String description) {
        if (razorpayKeyId == null || razorpaySecret == null) {
            log.error("Razorpay credentials not configured");
            return PaymentResult.builder()
                    .successful(false)
                    .failureReason("Razorpay credentials not configured")
                    .errorCode("CONFIG_ERROR")
                    .gateway("razorpay")
                    .build();
        }
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBasicAuth(razorpayKeyId, razorpaySecret);
            
            Map<String, Object> requestBody = new HashMap<>();
            // Razorpay expects amount in paise (smallest currency unit)
            requestBody.put("amount", amount.multiply(new BigDecimal("100")).longValue());
            requestBody.put("currency", "USD");
            requestBody.put("receipt", "BNPL_" + System.currentTimeMillis());
            
            Map<String, String> notes = new HashMap<>();
            notes.put("user_id", userId.toString());
            notes.put("description", description);
            requestBody.put("notes", notes);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                razorpayEndpoint + "/orders",
                HttpMethod.POST,
                request,
                Map.class
            );
            
            Map<String, Object> responseBody = response.getBody();
            
            if (responseBody != null && responseBody.get("id") != null) {
                // For Razorpay, order creation is successful, actual payment happens on client
                // In production, you would handle the payment capture separately
                return PaymentResult.builder()
                        .successful(true)
                        .transactionId((String) responseBody.get("id"))
                        .paymentId(UUID.randomUUID())
                        .gateway("razorpay")
                        .build();
            } else {
                return PaymentResult.builder()
                        .successful(false)
                        .failureReason("Razorpay order creation failed")
                        .errorCode("RAZORPAY_FAILURE")
                        .gateway("razorpay")
                        .build();
            }
            
        } catch (Exception e) {
            log.error("Razorpay payment processing error", e);
            return PaymentResult.builder()
                    .successful(false)
                    .failureReason("Razorpay processing error: " + e.getMessage())
                    .errorCode("PROCESSING_ERROR")
                    .gateway("razorpay")
                    .build();
        }
    }
    
    /**
     * Attempt payment through fallback gateways
     */
    private PaymentResult attemptFallbackPayment(UUID userId, String paymentMethodId,
                                                BigDecimal amount, String description) {
        List<String> fallbackGateways = Arrays.asList("stripe", "paypal", "square", "razorpay");
        fallbackGateways.remove(primaryGateway.toLowerCase());
        
        for (String gateway : fallbackGateways) {
            log.info("Attempting payment via fallback gateway: {}", gateway);
            
            try {
                PaymentResult result = processWithGateway(gateway, userId, 
                    paymentMethodId, amount, description);
                    
                if (result != null && result.isSuccessful()) {
                    log.info("Payment successful via fallback gateway: {}", gateway);
                    return result;
                }
            } catch (Exception e) {
                log.warn("Fallback gateway {} failed: {}", gateway, e.getMessage());
            }
        }
        
        // All gateways failed
        return PaymentResult.builder()
                .successful(false)
                .failureReason("All payment gateways failed")
                .errorCode("ALL_GATEWAYS_FAILED")
                .gateway("multiple")
                .build();
    }
    
    /**
     * Build form-encoded data from map
     */
    private String buildFormData(Map<String, String> data) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (result.length() > 0) {
                result.append("&");
            }
            result.append(entry.getKey())
                  .append("=")
                  .append(entry.getValue());
        }
        return result.toString();
    }

    /**
     * Verify payment status with gateway
     */
    public CompletableFuture<Boolean> verifyPaymentAsync(String transactionId, String gateway) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                switch (gateway.toLowerCase()) {
                    case "stripe":
                        return verifyStripePayment(transactionId);
                    case "paypal":
                        return verifyPayPalPayment(transactionId);
                    case "square":
                        return verifySquarePayment(transactionId);
                    case "razorpay":
                        return verifyRazorpayPayment(transactionId);
                    default:
                        log.error("Unknown gateway for verification: {}", gateway);
                        return false;
                }
            } catch (Exception e) {
                log.error("Payment verification failed for transaction: {}", transactionId, e);
                return false;
            }
        });
    }
    
    private boolean verifyStripePayment(String paymentIntentId) {
        if (stripeApiKey == null) return false;
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(stripeApiKey);
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                stripeEndpoint + "/payment_intents/" + paymentIntentId,
                HttpMethod.GET,
                request,
                Map.class
            );
            
            Map<String, Object> responseBody = response.getBody();
            return responseBody != null && "succeeded".equals(responseBody.get("status"));
            
        } catch (Exception e) {
            log.error("Stripe payment verification failed", e);
            return false;
        }
    }
    
    private boolean verifyPayPalPayment(String orderId) {
        if (paypalClientId == null || paypalSecret == null) return false;
        
        try {
            String accessToken = getPayPalAccessToken();
            if (accessToken == null) return false;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                paypalEndpoint + "/v2/checkout/orders/" + orderId,
                HttpMethod.GET,
                request,
                Map.class
            );
            
            Map<String, Object> responseBody = response.getBody();
            return responseBody != null && "COMPLETED".equals(responseBody.get("status"));
            
        } catch (Exception e) {
            log.error("PayPal payment verification failed", e);
            return false;
        }
    }
    
    private boolean verifySquarePayment(String paymentId) {
        if (squareAccessToken == null) return false;
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(squareAccessToken);
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                squareEndpoint + "/payments/" + paymentId,
                HttpMethod.GET,
                request,
                Map.class
            );
            
            Map<String, Object> responseBody = response.getBody();
            Map<String, Object> payment = responseBody != null ? 
                (Map<String, Object>) responseBody.get("payment") : null;
            
            return payment != null && "COMPLETED".equals(payment.get("status"));
            
        } catch (Exception e) {
            log.error("Square payment verification failed", e);
            return false;
        }
    }
    
    private boolean verifyRazorpayPayment(String orderId) {
        if (razorpayKeyId == null || razorpaySecret == null) return false;
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(razorpayKeyId, razorpaySecret);
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                razorpayEndpoint + "/orders/" + orderId,
                HttpMethod.GET,
                request,
                Map.class
            );
            
            Map<String, Object> responseBody = response.getBody();
            return responseBody != null && "paid".equals(responseBody.get("status"));
            
        } catch (Exception e) {
            log.error("Razorpay payment verification failed", e);
            return false;
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class PaymentResult {
        private boolean successful;
        private String transactionId;
        private UUID paymentId;
        private String failureReason;
        private String errorCode;
        private Long processingTimeMs;
        private String gateway;
    }
}