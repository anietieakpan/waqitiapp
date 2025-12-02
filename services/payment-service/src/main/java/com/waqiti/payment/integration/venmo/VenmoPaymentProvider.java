package com.waqiti.payment.integration.venmo;

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
 * Venmo Payment Provider Integration
 * 
 * Provides comprehensive payment processing through Venmo API
 * including social payments, P2P transfers, and merchant transactions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VenmoPaymentProvider implements PaymentProvider {

    @Value("${venmo.api.client-id}")
    private String clientId;
    
    @Value("${venmo.api.client-secret}")
    private String clientSecret;
    
    @Value("${venmo.api.environment:sandbox}")
    private String environment;
    
    @Value("${venmo.api.base-url:https://sandbox-api.venmo.com}")
    private String baseUrl;
    
    @Value("${venmo.webhook.secret}")
    private String webhookSecret;
    
    @Value("${app.base-url}")
    private String appBaseUrl;

    private final RestTemplate restTemplate;
    private final EncryptionService encryptionService;
    private final CacheService cacheService;
    private final VenmoWebhookHandler webhookHandler;
    
    private String accessToken;
    private LocalDateTime tokenExpiresAt;

    @PostConstruct
    public void init() {
        if ("production".equalsIgnoreCase(environment)) {
            this.baseUrl = "https://api.venmo.com";
        }
        
        // Initialize access token
        refreshAccessToken();
        
        log.info("Venmo payment provider initialized for environment: {}", environment);
    }

    @Override
    public String getProviderName() {
        return "VENMO";
    }

    @Override
    public boolean isAvailable() {
        try {
            return pingVenmoAPI();
        } catch (Exception e) {
            log.error("Venmo availability check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("Processing Venmo payment for amount: {} {}", request.getAmount(), request.getCurrency());
        
        try {
            ensureValidAccessToken();
            
            // Create Venmo payment request
            VenmoPaymentRequest venmoRequest = VenmoPaymentRequest.builder()
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .note(request.getDescription())
                    .userId(request.getCustomerId())
                    .audience(VenmoAudience.PRIVATE) // Default to private
                    .reference(request.getTransactionId())
                    .metadata(request.getMetadata())
                    .build();
            
            // Determine payment type based on metadata
            if (request.getMetadata().containsKey("recipientVenmoId")) {
                return processP2PPayment(venmoRequest);
            } else if (request.getMetadata().containsKey("merchantVenmoId")) {
                return processMerchantPayment(venmoRequest);
            } else {
                return processChargePayment(venmoRequest);
            }
            
        } catch (Exception e) {
            log.error("Venmo payment processing failed: {}", e.getMessage(), e);
            throw new PaymentProcessingException("Payment processing failed: " + e.getMessage());
        }
    }

    @Override
    public PaymentResponse capturePayment(String transactionId, BigDecimal amount) {
        log.info("Capturing Venmo payment: {} for amount: {}", transactionId, amount);
        
        // Note: Venmo typically processes payments immediately, but we'll implement capture for completeness
        try {
            ensureValidAccessToken();
            
            String url = baseUrl + "/v1/payments/" + transactionId + "/capture";
            
            Map<String, Object> captureRequest = Map.of(
                "amount", amount.toString(),
                "note", "Payment captured"
            );
            
            HttpHeaders headers = createAuthenticatedHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(captureRequest, headers);
            
            ResponseEntity<VenmoPaymentResponse> response = restTemplate.postForEntity(
                    url, entity, VenmoPaymentResponse.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return mapToVenmoPaymentResponse(response.getBody());
            } else {
                throw new PaymentProcessingException("Failed to capture payment: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Failed to capture Venmo payment: {}", e.getMessage());
            throw new PaymentProcessingException("Failed to capture payment: " + e.getMessage());
        }
    }

    @Override
    public PaymentResponse refundPayment(String transactionId, BigDecimal amount, String reason) {
        log.info("Processing Venmo refund for transaction: {} amount: {}", transactionId, amount);
        
        try {
            ensureValidAccessToken();
            
            String url = baseUrl + "/v1/payments/" + transactionId + "/refund";
            
            Map<String, Object> refundRequest = Map.of(
                "amount", amount.toString(),
                "note", reason != null ? reason : "Refund requested",
                "audience", "private"
            );
            
            HttpHeaders headers = createAuthenticatedHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(refundRequest, headers);
            
            ResponseEntity<VenmoRefundResponse> response = restTemplate.postForEntity(
                    url, entity, VenmoRefundResponse.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                VenmoRefundResponse refundResponse = response.getBody();
                
                return PaymentResponse.builder()
                        .transactionId(refundResponse.getRefundId())
                        .originalTransactionId(transactionId)
                        .status(mapRefundStatus(refundResponse.getStatus()))
                        .amount(amount.negate())
                        .currency(refundResponse.getCurrency())
                        .processedAt(LocalDateTime.parse(refundResponse.getCreatedAt()))
                        .providerResponse(response.getBody().toString())
                        .build();
            } else {
                throw new PaymentProcessingException("Failed to process refund: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Failed to process Venmo refund: {}", e.getMessage());
            throw new PaymentProcessingException("Failed to process refund: " + e.getMessage());
        }
    }

    @Override
    public PaymentResponse cancelPayment(String transactionId) {
        log.info("Cancelling Venmo payment: {}", transactionId);
        
        try {
            ensureValidAccessToken();
            
            String url = baseUrl + "/v1/payments/" + transactionId + "/cancel";
            
            HttpHeaders headers = createAuthenticatedHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<VenmoPaymentResponse> response = restTemplate.postForEntity(
                    url, entity, VenmoPaymentResponse.class);
            
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
            log.error("Failed to cancel Venmo payment: {}", e.getMessage());
            throw new PaymentProcessingException("Failed to cancel payment: " + e.getMessage());
        }
    }

    /**
     * Process social payment with Venmo - includes public feed posting
     */
    public CompletableFuture<VenmoSocialPaymentResult> processSocialPayment(VenmoSocialPaymentRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ensureValidAccessToken();
                
                String url = baseUrl + "/v1/payments/social";
                
                Map<String, Object> socialPaymentRequest = Map.of(
                    "user_id", request.getRecipientVenmoId(),
                    "amount", request.getAmount().toString(),
                    "note", request.getNote(),
                    "audience", request.getAudience().name().toLowerCase(),
                    "emoji", request.getEmoji(),
                    "funding_source_id", request.getFundingSourceId(),
                    "pin", request.getPin()
                );
                
                HttpHeaders headers = createAuthenticatedHeaders();
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(socialPaymentRequest, headers);
                
                ResponseEntity<VenmoSocialPaymentResponse> response = restTemplate.postForEntity(
                        url, entity, VenmoSocialPaymentResponse.class);
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    VenmoSocialPaymentResponse paymentResponse = response.getBody();
                    
                    return VenmoSocialPaymentResult.builder()
                            .paymentId(paymentResponse.getPaymentId())
                            .status(paymentResponse.getStatus())
                            .amount(request.getAmount())
                            .recipientVenmoId(request.getRecipientVenmoId())
                            .note(request.getNote())
                            .audience(request.getAudience())
                            .feedStoryId(paymentResponse.getFeedStoryId())
                            .createdAt(LocalDateTime.parse(paymentResponse.getCreatedAt()))
                            .build();
                } else {
                    throw new PaymentProcessingException("Social payment failed: " + response.getStatusCode());
                }
                
            } catch (Exception e) {
                log.error("Failed to process Venmo social payment", e);
                throw new PaymentProcessingException("Social payment failed: " + e.getMessage());
            }
        });
    }

    /**
     * Request payment from another Venmo user
     */
    public CompletableFuture<VenmoPaymentRequestResult> requestPayment(VenmoPaymentRequestRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ensureValidAccessToken();
                
                String url = baseUrl + "/v1/payments/request";
                
                Map<String, Object> requestPayment = Map.of(
                    "user_id", request.getFromVenmoId(),
                    "amount", request.getAmount().toString(),
                    "note", request.getNote(),
                    "audience", request.getAudience().name().toLowerCase(),
                    "reminder_frequency", request.getReminderFrequency()
                );
                
                HttpHeaders headers = createAuthenticatedHeaders();
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestPayment, headers);
                
                ResponseEntity<VenmoPaymentRequestResponse> response = restTemplate.postForEntity(
                        url, entity, VenmoPaymentRequestResponse.class);
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    VenmoPaymentRequestResponse requestResponse = response.getBody();
                    
                    return VenmoPaymentRequestResult.builder()
                            .requestId(requestResponse.getRequestId())
                            .status(requestResponse.getStatus())
                            .amount(request.getAmount())
                            .fromVenmoId(request.getFromVenmoId())
                            .note(request.getNote())
                            .audience(request.getAudience())
                            .createdAt(LocalDateTime.parse(requestResponse.getCreatedAt()))
                            .expiresAt(LocalDateTime.parse(requestResponse.getExpiresAt()))
                            .build();
                } else {
                    throw new PaymentProcessingException("Payment request failed: " + response.getStatusCode());
                }
                
            } catch (Exception e) {
                log.error("Failed to create Venmo payment request", e);
                throw new PaymentProcessingException("Payment request failed: " + e.getMessage());
            }
        });
    }

    /**
     * Get user's Venmo profile information
     */
    public VenmoUserProfile getUserProfile(String venmoUserId) {
        try {
            ensureValidAccessToken();
            
            String url = baseUrl + "/v1/users/" + venmoUserId;
            
            HttpHeaders headers = createAuthenticatedHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<VenmoUserProfileResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, VenmoUserProfileResponse.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                VenmoUserProfileResponse profileResponse = response.getBody();
                
                return VenmoUserProfile.builder()
                        .userId(profileResponse.getId())
                        .username(profileResponse.getUsername())
                        .displayName(profileResponse.getDisplayName())
                        .profilePictureUrl(profileResponse.getProfilePictureUrl())
                        .isActive(profileResponse.isActive())
                        .joinedDate(LocalDateTime.parse(profileResponse.getJoinedDate()))
                        .build();
            } else {
                throw new PaymentProcessingException("Failed to get user profile: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Failed to get Venmo user profile: {}", e.getMessage());
            throw new PaymentProcessingException("Failed to get user profile: " + e.getMessage());
        }
    }

    /**
     * Verify webhook signature
     */
    public boolean verifyWebhookSignature(String payload, String signature) {
        try {
            return webhookHandler.verifySignature(payload, signature, webhookSecret);
        } catch (Exception e) {
            log.error("Failed to verify Venmo webhook signature: {}", e.getMessage());
            return false;
        }
    }

    // Private helper methods

    private PaymentResponse processP2PPayment(VenmoPaymentRequest request) throws Exception {
        String recipientVenmoId = (String) request.getMetadata().get("recipientVenmoId");
        
        String url = baseUrl + "/v1/payments";
        
        Map<String, Object> paymentRequest = Map.of(
            "user_id", recipientVenmoId,
            "amount", request.getAmount().toString(),
            "note", request.getNote(),
            "audience", request.getAudience().name().toLowerCase(),
            "funding_source_id", request.getMetadata().getOrDefault("fundingSourceId", "")
        );
        
        HttpHeaders headers = createAuthenticatedHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(paymentRequest, headers);
        
        ResponseEntity<VenmoPaymentResponse> response = restTemplate.postForEntity(
                url, entity, VenmoPaymentResponse.class);
        
        if (response.getStatusCode() == HttpStatus.OK) {
            return mapToVenmoPaymentResponse(response.getBody());
        } else {
            throw new PaymentProcessingException("P2P payment failed: " + response.getStatusCode());
        }
    }

    private PaymentResponse processMerchantPayment(VenmoPaymentRequest request) throws Exception {
        String merchantVenmoId = (String) request.getMetadata().get("merchantVenmoId");
        
        String url = baseUrl + "/v1/payments/merchant";
        
        Map<String, Object> merchantRequest = Map.of(
            "merchant_id", merchantVenmoId,
            "amount", request.getAmount().toString(),
            "note", request.getNote(),
            "reference", request.getReference(),
            "return_url", appBaseUrl + "/payment/success",
            "cancel_url", appBaseUrl + "/payment/cancel"
        );
        
        HttpHeaders headers = createAuthenticatedHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(merchantRequest, headers);
        
        ResponseEntity<VenmoPaymentResponse> response = restTemplate.postForEntity(
                url, entity, VenmoPaymentResponse.class);
        
        if (response.getStatusCode() == HttpStatus.OK) {
            VenmoPaymentResponse paymentResponse = response.getBody();
            
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
                            "status", paymentResponse.getStatus(),
                            "audience", request.getAudience().name()
                    ))
                    .build();
        } else {
            throw new PaymentProcessingException("Merchant payment failed: " + response.getStatusCode());
        }
    }

    private PaymentResponse processChargePayment(VenmoPaymentRequest request) throws Exception {
        String url = baseUrl + "/v1/charges";
        
        Map<String, Object> chargeRequest = Map.of(
            "amount", request.getAmount().toString(),
            "currency", request.getCurrency(),
            "note", request.getNote(),
            "user_id", request.getUserId(),
            "funding_source_id", request.getMetadata().getOrDefault("fundingSourceId", "")
        );
        
        HttpHeaders headers = createAuthenticatedHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(chargeRequest, headers);
        
        ResponseEntity<VenmoPaymentResponse> response = restTemplate.postForEntity(
                url, entity, VenmoPaymentResponse.class);
        
        if (response.getStatusCode() == HttpStatus.OK) {
            return mapToVenmoPaymentResponse(response.getBody());
        } else {
            throw new PaymentProcessingException("Charge payment failed: " + response.getStatusCode());
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
            String url = baseUrl + "/v1/oauth/access_token";
            
            Map<String, String> tokenRequest = Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "grant_type", "client_credentials",
                "scope", "make_payments access_profile access_email access_phone access_balance"
            );
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(tokenRequest, headers);
            
            ResponseEntity<VenmoTokenResponse> response = restTemplate.postForEntity(
                    url, entity, VenmoTokenResponse.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                VenmoTokenResponse tokenResponse = response.getBody();
                this.accessToken = tokenResponse.getAccessToken();
                this.tokenExpiresAt = LocalDateTime.now().plusSeconds(tokenResponse.getExpiresIn() - 300);
                
                log.debug("Venmo access token refreshed, expires at: {}", tokenExpiresAt);
            } else {
                throw new PaymentProcessingException("Failed to refresh access token: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Failed to refresh Venmo access token: {}", e.getMessage());
            throw new PaymentProcessingException("Failed to refresh access token: " + e.getMessage());
        }
    }

    private HttpHeaders createAuthenticatedHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        headers.set("User-Agent", "Waqiti/1.0");
        return headers;
    }

    private boolean pingVenmoAPI() {
        try {
            String url = baseUrl + "/v1/me";
            HttpHeaders headers = createAuthenticatedHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);
            
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            return false;
        }
    }

    private PaymentResponse mapToVenmoPaymentResponse(VenmoPaymentResponse venmoResponse) {
        return PaymentResponse.builder()
                .transactionId(venmoResponse.getPaymentId())
                .status(mapPaymentStatus(venmoResponse.getStatus()))
                .amount(new BigDecimal(venmoResponse.getAmount()))
                .currency("USD") // Venmo only supports USD
                .providerTransactionId(venmoResponse.getPaymentId())
                .providerResponse(venmoResponse.toString())
                .processedAt(LocalDateTime.parse(venmoResponse.getCreatedAt()))
                .metadata(Map.of(
                        "status", venmoResponse.getStatus(),
                        "note", venmoResponse.getNote(),
                        "audience", venmoResponse.getAudience()
                ))
                .build();
    }

    private Transaction.Status mapPaymentStatus(String venmoStatus) {
        return switch (venmoStatus) {
            case "settled" -> Transaction.Status.COMPLETED;
            case "pending" -> Transaction.Status.PENDING;
            case "submitted" -> Transaction.Status.PROCESSING;
            case "failed" -> Transaction.Status.FAILED;
            case "cancelled" -> Transaction.Status.CANCELLED;
            default -> Transaction.Status.PENDING;
        };
    }

    private Transaction.Status mapRefundStatus(String refundStatus) {
        return switch (refundStatus) {
            case "settled" -> Transaction.Status.REFUNDED;
            case "pending" -> Transaction.Status.PROCESSING;
            case "failed" -> Transaction.Status.FAILED;
            case "cancelled" -> Transaction.Status.CANCELLED;
            default -> Transaction.Status.PROCESSING;
        };
    }

    // Enums and DTOs

    public enum VenmoAudience {
        PUBLIC, FRIENDS, PRIVATE
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VenmoPaymentRequest {
        private BigDecimal amount;
        private String currency;
        private String note;
        private String userId;
        private VenmoAudience audience;
        private String reference;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VenmoSocialPaymentRequest {
        private BigDecimal amount;
        private String recipientVenmoId;
        private String note;
        private VenmoAudience audience;
        private String emoji;
        private String fundingSourceId;
        private String pin;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VenmoSocialPaymentResult {
        private String paymentId;
        private String status;
        private BigDecimal amount;
        private String recipientVenmoId;
        private String note;
        private VenmoAudience audience;
        private String feedStoryId;
        private LocalDateTime createdAt;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VenmoPaymentRequestRequest {
        private BigDecimal amount;
        private String fromVenmoId;
        private String note;
        private VenmoAudience audience;
        private String reminderFrequency;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VenmoPaymentRequestResult {
        private String requestId;
        private String status;
        private BigDecimal amount;
        private String fromVenmoId;
        private String note;
        private VenmoAudience audience;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VenmoUserProfile {
        private String userId;
        private String username;
        private String displayName;
        private String profilePictureUrl;
        private boolean isActive;
        private LocalDateTime joinedDate;
    }

    // Response DTOs
    @lombok.Data
    private static class VenmoTokenResponse {
        private String accessToken;
        private String tokenType;
        private long expiresIn;
        private String scope;
        private String refreshToken;
    }

    @lombok.Data
    private static class VenmoPaymentResponse {
        private String paymentId;
        private String status;
        private String amount;
        private String note;
        private String audience;
        private String approvalUrl;
        private String createdAt;
    }

    @lombok.Data
    private static class VenmoRefundResponse {
        private String refundId;
        private String status;
        private String amount;
        private String currency;
        private String createdAt;
    }

    @lombok.Data
    private static class VenmoSocialPaymentResponse {
        private String paymentId;
        private String status;
        private String feedStoryId;
        private String createdAt;
    }

    @lombok.Data
    private static class VenmoPaymentRequestResponse {
        private String requestId;
        private String status;
        private String createdAt;
        private String expiresAt;
    }

    @lombok.Data
    private static class VenmoUserProfileResponse {
        private String id;
        private String username;
        private String displayName;
        private String profilePictureUrl;
        private boolean active;
        private String joinedDate;
    }
}