package com.waqiti.payment.integration.cashapp;

import com.waqiti.payment.core.model.UnifiedPaymentRequest;
import com.waqiti.payment.core.model.PaymentResult;
import com.waqiti.payment.core.provider.PaymentProvider;
import com.waqiti.payment.dto.request.PaymentRequest;
import com.waqiti.payment.dto.response.PaymentResponse;
import com.waqiti.payment.domain.Transaction;
import com.waqiti.payment.exception.PaymentProcessingException;
import com.waqiti.common.security.EncryptionService;
import com.waqiti.common.cache.CacheService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * CashApp Payment Provider Integration
 * 
 * Provides comprehensive payment processing through CashApp Pay API
 * including P2P transfers, merchant payments, and instant transfers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CashAppPaymentProvider implements PaymentProvider {

    @Value("${cashapp.api.client-id}")
    private String clientId;
    
    @Value("${cashapp.api.client-secret}")
    private String clientSecret;
    
    @Value("${cashapp.api.environment:sandbox}")
    private String environment;
    
    @Value("${cashapp.api.base-url:https://api.sandbox.cash.app}")
    private String baseUrl;
    
    @Value("${cashapp.webhook.secret}")
    private String webhookSecret;
    
    @Value("${app.base-url}")
    private String appBaseUrl;

    private final RestTemplate restTemplate;
    private final EncryptionService encryptionService;
    private final CacheService cacheService;
    private final CashAppWebhookHandler webhookHandler;
    
    private String accessToken;
    private LocalDateTime tokenExpiresAt;

    @PostConstruct
    public void init() {
        if ("production".equalsIgnoreCase(environment)) {
            this.baseUrl = "https://api.cash.app";
        }
        
        // Initialize access token
        refreshAccessToken();
        
        log.info("CashApp payment provider initialized for environment: {}", environment);
    }

    @Override
    public String getProviderName() {
        return "CASHAPP";
    }

    @Override
    public boolean isAvailable() {
        try {
            // Test API connectivity
            return pingCashAppAPI();
        } catch (Exception e) {
            log.error("CashApp availability check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("Processing CashApp payment for amount: {} {}", request.getAmount(), request.getCurrency());
        
        try {
            ensureValidAccessToken();
            
            // Create CashApp payment request
            CashAppPaymentRequest cashAppRequest = CashAppPaymentRequest.builder()
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .description(request.getDescription())
                    .customerId(request.getCustomerId())
                    .merchantId(request.getMerchantId())
                    .reference(request.getTransactionId())
                    .metadata(request.getMetadata())
                    .build();
            
            // Determine payment type
            if (request.getPaymentMethodId() != null) {
                return processStoredPaymentMethod(cashAppRequest, request.getPaymentMethodId());
            } else if (request.getMetadata().containsKey("recipientCashTag")) {
                return processP2PPayment(cashAppRequest);
            } else {
                return processMerchantPayment(cashAppRequest);
            }
            
        } catch (Exception e) {
            log.error("CashApp payment processing failed: {}", e.getMessage(), e);
            throw new PaymentProcessingException("Payment processing failed: " + e.getMessage());
        }
    }

    @Override
    public PaymentResponse capturePayment(String transactionId, BigDecimal amount) {
        log.info("Capturing CashApp payment: {} for amount: {}", transactionId, amount);
        
        try {
            ensureValidAccessToken();
            
            String url = baseUrl + "/v1/payments/" + transactionId + "/capture";
            
            Map<String, Object> captureRequest = Map.of(
                "amount", amount.toString(),
                "capture_method", "automatic"
            );
            
            HttpHeaders headers = createAuthenticatedHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(captureRequest, headers);
            
            ResponseEntity<CashAppPaymentResponse> response = restTemplate.postForEntity(
                    url, entity, CashAppPaymentResponse.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return mapToCashAppPaymentResponse(response.getBody());
            } else {
                throw new PaymentProcessingException("Failed to capture payment: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Failed to capture CashApp payment: {}", e.getMessage());
            throw new PaymentProcessingException("Failed to capture payment: " + e.getMessage());
        }
    }

    @Override
    public PaymentResponse refundPayment(String transactionId, BigDecimal amount, String reason) {
        log.info("Processing CashApp refund for transaction: {} amount: {}", transactionId, amount);
        
        try {
            ensureValidAccessToken();
            
            String url = baseUrl + "/v1/payments/" + transactionId + "/refunds";
            
            Map<String, Object> refundRequest = Map.of(
                "amount", amount.toString(),
                "reason", reason != null ? reason : "Customer requested refund",
                "refund_speed", "instant"
            );
            
            HttpHeaders headers = createAuthenticatedHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(refundRequest, headers);
            
            ResponseEntity<CashAppRefundResponse> response = restTemplate.postForEntity(
                    url, entity, CashAppRefundResponse.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                CashAppRefundResponse refundResponse = response.getBody();
                
                return PaymentResponse.builder()
                        .transactionId(refundResponse.getRefundId())
                        .originalTransactionId(transactionId)
                        .status(mapRefundStatus(refundResponse.getStatus()))
                        .amount(amount.negate())
                        .currency(refundResponse.getCurrency())
                        .processedAt(LocalDateTime.parse(refundResponse.getProcessedAt()))
                        .providerResponse(response.getBody().toString())
                        .build();
            } else {
                throw new PaymentProcessingException("Failed to process refund: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Failed to process CashApp refund: {}", e.getMessage());
            throw new PaymentProcessingException("Failed to process refund: " + e.getMessage());
        }
    }

    @Override
    public PaymentResponse cancelPayment(String transactionId) {
        log.info("Cancelling CashApp payment: {}", transactionId);
        
        try {
            ensureValidAccessToken();
            
            String url = baseUrl + "/v1/payments/" + transactionId + "/cancel";
            
            HttpHeaders headers = createAuthenticatedHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<CashAppPaymentResponse> response = restTemplate.postForEntity(
                    url, entity, CashAppPaymentResponse.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return PaymentResponse.builder()
                        .transactionId(transactionId)
                        .status(Transaction.Status.CANCELLED)
                        .processedAt(LocalDateTime.now())
                        .providerResponse(response.getBody().toString())
                        .build();
            } else {
                throw new PaymentProcessingException("Failed to cancel payment: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Failed to cancel CashApp payment: {}", e.getMessage());
            throw new PaymentProcessingException("Failed to cancel payment: " + e.getMessage());
        }
    }

    /**
     * Process peer-to-peer payment via CashApp
     */
    public CompletableFuture<CashAppP2PResult> processP2PTransfer(CashAppP2PRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ensureValidAccessToken();
                
                String url = baseUrl + "/v1/payments/p2p";
                
                Map<String, Object> p2pRequest = Map.of(
                    "amount", request.getAmount().toString(),
                    "currency", request.getCurrency(),
                    "recipient_cashtag", request.getRecipientCashTag(),
                    "sender_cashtag", request.getSenderCashTag(),
                    "note", request.getNote(),
                    "request_id", request.getRequestId()
                );
                
                HttpHeaders headers = createAuthenticatedHeaders();
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(p2pRequest, headers);
                
                ResponseEntity<CashAppP2PResponse> response = restTemplate.postForEntity(
                        url, entity, CashAppP2PResponse.class);
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    CashAppP2PResponse p2pResponse = response.getBody();
                    
                    return CashAppP2PResult.builder()
                            .transferId(p2pResponse.getTransferId())
                            .status(p2pResponse.getStatus())
                            .amount(request.getAmount())
                            .currency(request.getCurrency())
                            .recipientCashTag(request.getRecipientCashTag())
                            .senderCashTag(request.getSenderCashTag())
                            .processedAt(LocalDateTime.parse(p2pResponse.getProcessedAt()))
                            .estimatedSettlement(LocalDateTime.parse(p2pResponse.getEstimatedSettlement()))
                            .build();
                } else {
                    throw new PaymentProcessingException("P2P transfer failed: " + response.getStatusCode());
                }
                
            } catch (Exception e) {
                log.error("Failed to process CashApp P2P transfer", e);
                throw new PaymentProcessingException("P2P transfer failed: " + e.getMessage());
            }
        });
    }

    /**
     * Create instant deposit to bank account
     */
    public CompletableFuture<CashAppInstantDepositResult> createInstantDeposit(CashAppDepositRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ensureValidAccessToken();
                
                String url = baseUrl + "/v1/deposits/instant";
                
                Map<String, Object> depositRequest = Map.of(
                    "amount", request.getAmount().toString(),
                    "currency", request.getCurrency(),
                    "bank_account_id", request.getBankAccountId(),
                    "description", request.getDescription(),
                    "fee_type", "standard" // or "instant" for faster processing
                );
                
                HttpHeaders headers = createAuthenticatedHeaders();
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(depositRequest, headers);
                
                ResponseEntity<CashAppDepositResponse> response = restTemplate.postForEntity(
                        url, entity, CashAppDepositResponse.class);
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    CashAppDepositResponse depositResponse = response.getBody();
                    
                    return CashAppInstantDepositResult.builder()
                            .depositId(depositResponse.getDepositId())
                            .status(depositResponse.getStatus())
                            .amount(request.getAmount())
                            .currency(request.getCurrency())
                            .processingFee(new BigDecimal(depositResponse.getProcessingFee()))
                            .estimatedArrival(LocalDateTime.parse(depositResponse.getEstimatedArrival()))
                            .build();
                } else {
                    throw new PaymentProcessingException("Instant deposit failed: " + response.getStatusCode());
                }
                
            } catch (Exception e) {
                log.error("Failed to create CashApp instant deposit", e);
                throw new PaymentProcessingException("Instant deposit failed: " + e.getMessage());
            }
        });
    }

    /**
     * Verify webhook signature
     */
    public boolean verifyWebhookSignature(String payload, String signature, String timestamp) {
        try {
            return webhookHandler.verifySignature(payload, signature, timestamp, webhookSecret);
        } catch (Exception e) {
            log.error("Failed to verify CashApp webhook signature: {}", e.getMessage());
            return false;
        }
    }

    // Private helper methods

    private PaymentResponse processStoredPaymentMethod(CashAppPaymentRequest request, String paymentMethodId) 
            throws Exception {
        
        String url = baseUrl + "/v1/payments/charge";
        
        Map<String, Object> chargeRequest = Map.of(
            "amount", request.getAmount().toString(),
            "currency", request.getCurrency(),
            "payment_method_id", paymentMethodId,
            "description", request.getDescription(),
            "customer_id", request.getCustomerId(),
            "capture", true
        );
        
        HttpHeaders headers = createAuthenticatedHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(chargeRequest, headers);
        
        ResponseEntity<CashAppPaymentResponse> response = restTemplate.postForEntity(
                url, entity, CashAppPaymentResponse.class);
        
        if (response.getStatusCode() == HttpStatus.OK) {
            return mapToCashAppPaymentResponse(response.getBody());
        } else {
            throw new PaymentProcessingException("Stored payment method charge failed: " + response.getStatusCode());
        }
    }

    private PaymentResponse processP2PPayment(CashAppPaymentRequest request) throws Exception {
        String recipientCashTag = (String) request.getMetadata().get("recipientCashTag");
        
        String url = baseUrl + "/v1/payments/send";
        
        Map<String, Object> sendRequest = Map.of(
            "amount", request.getAmount().toString(),
            "currency", request.getCurrency(),
            "recipient_cashtag", recipientCashTag,
            "note", request.getDescription(),
            "request_id", request.getReference()
        );
        
        HttpHeaders headers = createAuthenticatedHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(sendRequest, headers);
        
        ResponseEntity<CashAppPaymentResponse> response = restTemplate.postForEntity(
                url, entity, CashAppPaymentResponse.class);
        
        if (response.getStatusCode() == HttpStatus.OK) {
            return mapToCashAppPaymentResponse(response.getBody());
        } else {
            throw new PaymentProcessingException("P2P payment failed: " + response.getStatusCode());
        }
    }

    private PaymentResponse processMerchantPayment(CashAppPaymentRequest request) throws Exception {
        String url = baseUrl + "/v1/payments/merchant";
        
        Map<String, Object> merchantRequest = Map.of(
            "amount", request.getAmount().toString(),
            "currency", request.getCurrency(),
            "merchant_id", request.getMerchantId(),
            "description", request.getDescription(),
            "reference", request.getReference(),
            "return_url", appBaseUrl + "/payment/success",
            "cancel_url", appBaseUrl + "/payment/cancel"
        );
        
        HttpHeaders headers = createAuthenticatedHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(merchantRequest, headers);
        
        ResponseEntity<CashAppPaymentResponse> response = restTemplate.postForEntity(
                url, entity, CashAppPaymentResponse.class);
        
        if (response.getStatusCode() == HttpStatus.OK) {
            CashAppPaymentResponse paymentResponse = response.getBody();
            
            return PaymentResponse.builder()
                    .transactionId(paymentResponse.getPaymentId())
                    .status(mapPaymentStatus(paymentResponse.getStatus()))
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .requiresAction(paymentResponse.getApprovalUrl() != null)
                    .redirectUrl(paymentResponse.getApprovalUrl())
                    .processedAt(LocalDateTime.parse(paymentResponse.getCreatedAt()))
                    .providerResponse(paymentResponse.toString())
                    .metadata(Map.of(
                            "approval_url", paymentResponse.getApprovalUrl(),
                            "status", paymentResponse.getStatus()
                    ))
                    .build();
        } else {
            throw new PaymentProcessingException("Merchant payment failed: " + response.getStatusCode());
        }
    }

    private void ensureValidAccessToken() {
        if (accessToken == null || tokenExpiresAt == null || 
            LocalDateTime.now().isAfter(tokenExpiresAt.minusMinutes(5))) {
            refreshAccessToken();
        }
    }

    private void refreshAccessToken() {
        try {
            String url = baseUrl + "/v1/oauth/token";
            
            Map<String, String> tokenRequest = Map.of(
                "grant_type", "client_credentials",
                "client_id", clientId,
                "client_secret", clientSecret,
                "scope", "payments:read payments:write transfers:write"
            );
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(tokenRequest, headers);
            
            ResponseEntity<CashAppTokenResponse> response = restTemplate.postForEntity(
                    url, entity, CashAppTokenResponse.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                CashAppTokenResponse tokenResponse = response.getBody();
                this.accessToken = tokenResponse.getAccessToken();
                this.tokenExpiresAt = LocalDateTime.now().plusSeconds(tokenResponse.getExpiresIn() - 300); // 5min buffer
                
                log.debug("CashApp access token refreshed, expires at: {}", tokenExpiresAt);
            } else {
                throw new PaymentProcessingException("Failed to refresh access token: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Failed to refresh CashApp access token: {}", e.getMessage());
            throw new PaymentProcessingException("Failed to refresh access token: " + e.getMessage());
        }
    }

    private HttpHeaders createAuthenticatedHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        headers.set("X-API-Version", "2024-01-01");
        headers.set("User-Agent", "Waqiti/1.0");
        return headers;
    }

    private boolean pingCashAppAPI() {
        try {
            ensureValidAccessToken();
            
            String url = baseUrl + "/v1/health";
            HttpHeaders headers = createAuthenticatedHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);
            
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            return false;
        }
    }

    private PaymentResponse mapToCashAppPaymentResponse(CashAppPaymentResponse cashAppResponse) {
        return PaymentResponse.builder()
                .transactionId(cashAppResponse.getPaymentId())
                .status(mapPaymentStatus(cashAppResponse.getStatus()))
                .amount(new BigDecimal(cashAppResponse.getAmount()))
                .currency(cashAppResponse.getCurrency())
                .providerTransactionId(cashAppResponse.getPaymentId())
                .providerResponse(cashAppResponse.toString())
                .processedAt(LocalDateTime.parse(cashAppResponse.getProcessedAt()))
                .metadata(Map.of(
                        "status", cashAppResponse.getStatus(),
                        "fee", cashAppResponse.getFee()
                ))
                .build();
    }

    private Transaction.Status mapPaymentStatus(String cashAppStatus) {
        return switch (cashAppStatus) {
            case "completed" -> Transaction.Status.COMPLETED;
            case "pending" -> Transaction.Status.PENDING;
            case "processing" -> Transaction.Status.PROCESSING;
            case "failed" -> Transaction.Status.FAILED;
            case "cancelled" -> Transaction.Status.CANCELLED;
            case "authorized" -> Transaction.Status.AUTHORIZED;
            default -> Transaction.Status.PENDING;
        };
    }

    private Transaction.Status mapRefundStatus(String refundStatus) {
        return switch (refundStatus) {
            case "completed" -> Transaction.Status.REFUNDED;
            case "pending" -> Transaction.Status.PROCESSING;
            case "failed" -> Transaction.Status.FAILED;
            case "cancelled" -> Transaction.Status.CANCELLED;
            default -> Transaction.Status.PROCESSING;
        };
    }

    // Response DTOs

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class CashAppTokenResponse {
        private String accessToken;
        private String tokenType;
        private long expiresIn;
        private String scope;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class CashAppPaymentResponse {
        private String paymentId;
        private String status;
        private String amount;
        private String currency;
        private String fee;
        private String approvalUrl;
        private String createdAt;
        private String processedAt;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class CashAppRefundResponse {
        private String refundId;
        private String status;
        private String amount;
        private String currency;
        private String processedAt;
    }

    // Request/Result DTOs

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CashAppPaymentRequest {
        private BigDecimal amount;
        private String currency;
        private String description;
        private String customerId;
        private String merchantId;
        private String reference;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CashAppP2PRequest {
        private BigDecimal amount;
        private String currency;
        private String recipientCashTag;
        private String senderCashTag;
        private String note;
        private String requestId;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CashAppP2PResult {
        private String transferId;
        private String status;
        private BigDecimal amount;
        private String currency;
        private String recipientCashTag;
        private String senderCashTag;
        private LocalDateTime processedAt;
        private LocalDateTime estimatedSettlement;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CashAppDepositRequest {
        private BigDecimal amount;
        private String currency;
        private String bankAccountId;
        private String description;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CashAppInstantDepositResult {
        private String depositId;
        private String status;
        private BigDecimal amount;
        private String currency;
        private BigDecimal processingFee;
        private LocalDateTime estimatedArrival;
    }

    // Mock response classes for API integration
    @lombok.Data
    private static class CashAppP2PResponse {
        private String transferId;
        private String status;
        private String processedAt;
        private String estimatedSettlement;
    }

    @lombok.Data
    private static class CashAppDepositResponse {
        private String depositId;
        private String status;
        private String processingFee;
        private String estimatedArrival;
    }
}