package com.waqiti.bankintegration.strategy.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.bankintegration.domain.PaymentProvider;
import com.waqiti.bankintegration.dto.*;
import com.waqiti.bankintegration.exception.PaymentProcessingException;
import com.waqiti.bankintegration.strategy.PaymentStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ACH Payment Strategy Implementation
 * 
 * Handles ACH (Automated Clearing House) payments which are commonly used
 * for bank transfers, direct deposits, and electronic bill payments.
 * 
 * ACH transfers are typically:
 * - Lower cost than wire transfers
 * - Take 1-3 business days to settle
 * - Have daily and monthly limits
 * - Require account verification
 */
@Component
public class ACHPaymentStrategy implements PaymentStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(ACHPaymentStrategy.class);
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public ACHPaymentStrategy(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @CircuitBreaker(name = "ach-payment", fallbackMethod = "fallbackProcessPayment")
    @Retry(name = "ach-payment")
    public PaymentResponse processPayment(PaymentProvider provider, PaymentRequest request) {
        logger.info("Processing ACH payment through provider: {} for request: {}", 
                   provider.getProviderCode(), request.getRequestId());
        
        try {
            // Validate ACH-specific requirements
            validateACHRequest(request);
            
            // Prepare ACH-specific payload
            Map<String, Object> achPayload = buildACHPayload(provider, request);
            
            // Send request to ACH provider
            HttpHeaders headers = buildHeaders(provider);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(achPayload, headers);
            
            String url = provider.getApiBaseUrl() + "/ach/transfers";
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, Map.class);
            
            // Process provider response
            return processACHResponse(response, request);
            
        } catch (Exception e) {
            logger.error("ACH payment processing failed for request: {}", request.getRequestId(), e);
            throw new PaymentProcessingException("ACH payment failed", e);
        }
    }

    @Override
    @CircuitBreaker(name = "ach-refund", fallbackMethod = "fallbackProcessRefund")
    @Retry(name = "ach-refund")
    public RefundResponse processRefund(PaymentProvider provider, RefundRequest request) {
        logger.info("Processing ACH refund through provider: {} for transaction: {}", 
                   provider.getProviderCode(), request.getOriginalTransactionId());
        
        try {
            Map<String, Object> refundPayload = buildACHRefundPayload(provider, request);
            
            HttpHeaders headers = buildHeaders(provider);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(refundPayload, headers);
            
            String url = provider.getApiBaseUrl() + "/ach/refunds";
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, Map.class);
            
            return processACHRefundResponse(response, request);
            
        } catch (Exception e) {
            logger.error("ACH refund processing failed for transaction: {}", 
                        request.getOriginalTransactionId(), e);
            throw new PaymentProcessingException("ACH refund failed", e);
        }
    }

    @Override
    public PaymentResponse checkPaymentStatus(PaymentProvider provider, String transactionId) {
        logger.info("Checking ACH payment status for transaction: {} with provider: {}", 
                   transactionId, provider.getProviderCode());
        
        try {
            HttpHeaders headers = buildHeaders(provider);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            String url = provider.getApiBaseUrl() + "/ach/transfers/" + transactionId;
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Map.class);
            
            return processACHStatusResponse(response, transactionId);
            
        } catch (Exception e) {
            logger.error("Failed to check ACH payment status for transaction: {}", transactionId, e);
            throw new PaymentProcessingException("Status check failed", e);
        }
    }

    @Override
    public boolean canHandle(PaymentProvider provider, PaymentRequest request) {
        // Check if this is an ACH-compatible request
        if (!"USD".equals(request.getCurrency())) {
            return false; // ACH typically only supports USD
        }
        
        if (request.getAmount().compareTo(new BigDecimal("0.01")) < 0) {
            return false; // Minimum amount check
        }
        
        if (request.getAmount().compareTo(new BigDecimal("1000000")) > 0) {
            return false; // Maximum amount check for standard ACH
        }
        
        // Check if provider supports ACH features
        return provider.supportsFeature("transfers") || provider.supportsFeature("ach_transfer");
    }

    @Override
    public boolean isProviderHealthy(PaymentProvider provider) {
        try {
            HttpHeaders headers = buildHeaders(provider);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            String url = provider.getApiBaseUrl() + "/health";
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Map.class);
            
            return response.getStatusCode() == HttpStatus.OK;
            
        } catch (Exception e) {
            logger.warn("Health check failed for ACH provider: {}", provider.getProviderCode(), e);
            return false;
        }
    }

    @Override
    public PaymentResponse cancelPayment(PaymentProvider provider, String transactionId) {
        logger.info("Cancelling ACH payment: {} with provider: {}", transactionId, provider.getProviderCode());
        
        try {
            HttpHeaders headers = buildHeaders(provider);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            String url = provider.getApiBaseUrl() + "/ach/transfers/" + transactionId + "/cancel";
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, Map.class);
            
            return processACHCancellationResponse(response, transactionId);
            
        } catch (Exception e) {
            logger.error("Failed to cancel ACH payment: {}", transactionId, e);
            throw new PaymentProcessingException("Cancellation failed", e);
        }
    }

    // Private helper methods

    private void validateACHRequest(PaymentRequest request) {
        if (request.getFromAccount() == null || request.getFromAccount().getRoutingNumber() == null) {
            throw new IllegalArgumentException("ACH requires valid source routing number");
        }
        
        if (request.getToAccount() == null || request.getToAccount().getRoutingNumber() == null) {
            throw new IllegalArgumentException("ACH requires valid destination routing number");
        }
        
        if (request.getFromAccount().getAccountNumber() == null) {
            throw new IllegalArgumentException("ACH requires valid source account number");
        }
        
        if (request.getToAccount().getAccountNumber() == null) {
            throw new IllegalArgumentException("ACH requires valid destination account number");
        }
    }

    private Map<String, Object> buildACHPayload(PaymentProvider provider, PaymentRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_id", request.getRequestId());
        payload.put("amount", request.getAmount());
        payload.put("currency", request.getCurrency());
        payload.put("description", request.getDescription());
        payload.put("reference", request.getReference());
        
        // Source account details
        Map<String, Object> sourceAccount = new HashMap<>();
        sourceAccount.put("routing_number", request.getFromAccount().getRoutingNumber());
        sourceAccount.put("account_number", request.getFromAccount().getAccountNumber());
        sourceAccount.put("account_type", request.getFromAccount().getAccountType());
        sourceAccount.put("account_holder_name", request.getFromAccount().getAccountHolderName());
        payload.put("source_account", sourceAccount);
        
        // Destination account details
        Map<String, Object> destinationAccount = new HashMap<>();
        destinationAccount.put("routing_number", request.getToAccount().getRoutingNumber());
        destinationAccount.put("account_number", request.getToAccount().getAccountNumber());
        destinationAccount.put("account_type", request.getToAccount().getAccountType());
        destinationAccount.put("account_holder_name", request.getToAccount().getAccountHolderName());
        payload.put("destination_account", destinationAccount);
        
        // ACH-specific fields
        payload.put("sec_code", "WEB"); // Standard Entry Class Code
        payload.put("effective_date", LocalDateTime.now().plusDays(1).toString()); // Next business day
        payload.put("same_day", request.isExpedited()); // Same-day ACH if supported
        
        return payload;
    }

    private Map<String, Object> buildACHRefundPayload(PaymentProvider provider, RefundRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("original_transaction_id", request.getOriginalTransactionId());
        payload.put("refund_amount", request.getAmount());
        payload.put("reason", request.getReason());
        payload.put("reference", request.getRequestId());
        
        return payload;
    }

    private HttpHeaders buildHeaders(PaymentProvider provider) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Add provider-specific authentication
        String config = provider.getConfiguration();
        if (config != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> configMap = objectMapper.readValue(config, Map.class);
                
                String apiKey = (String) configMap.get("api_key");
                if (apiKey != null) {
                    headers.set("Authorization", "Bearer " + apiKey);
                }
                
                String clientId = (String) configMap.get("client_id");
                if (clientId != null) {
                    headers.set("X-Client-ID", clientId);
                }
                
            } catch (Exception e) {
                logger.warn("Failed to parse provider configuration", e);
            }
        }
        
        headers.set("X-Request-ID", UUID.randomUUID().toString());
        headers.set("User-Agent", "Waqiti-Platform/1.0");
        
        return headers;
    }

    private PaymentResponse processACHResponse(ResponseEntity<Map> response, PaymentRequest request) {
        Map<String, Object> body = response.getBody();
        if (body == null) {
            body = new HashMap<>();
        }
        
        PaymentResponse paymentResponse = new PaymentResponse();
        paymentResponse.setRequestId(request.getRequestId());
        paymentResponse.setTimestamp(LocalDateTime.now());
        
        if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED) {
            paymentResponse.setSuccess(true);
            paymentResponse.setTransactionId((String) body.get("transaction_id"));
            paymentResponse.setProviderTransactionId((String) body.get("provider_transaction_id"));
            paymentResponse.setStatus((String) body.get("status"));
            paymentResponse.setMessage("ACH transfer initiated successfully");
            
            // ACH transfers are typically pending initially
            if ("pending".equalsIgnoreCase(paymentResponse.getStatus())) {
                paymentResponse.setEstimatedSettlement(LocalDateTime.now().plusDays(2));
            }
            
        } else {
            paymentResponse.setSuccess(false);
            paymentResponse.setErrorCode((String) body.get("error_code"));
            paymentResponse.setErrorMessage((String) body.get("error_message"));
        }
        
        return paymentResponse;
    }

    private RefundResponse processACHRefundResponse(ResponseEntity<Map> response, RefundRequest request) {
        Map<String, Object> body = response.getBody();
        if (body == null) {
            body = new HashMap<>();
        }
        
        RefundResponse refundResponse = new RefundResponse();
        refundResponse.setOriginalTransactionId(request.getOriginalTransactionId());
        refundResponse.setTimestamp(LocalDateTime.now());
        
        if (response.getStatusCode() == HttpStatus.OK) {
            refundResponse.setSuccess(true);
            refundResponse.setRefundTransactionId((String) body.get("refund_transaction_id"));
            refundResponse.setStatus((String) body.get("status"));
            refundResponse.setMessage("ACH refund initiated successfully");
        } else {
            refundResponse.setSuccess(false);
            refundResponse.setErrorCode((String) body.get("error_code"));
            refundResponse.setErrorMessage((String) body.get("error_message"));
        }
        
        return refundResponse;
    }

    private PaymentResponse processACHStatusResponse(ResponseEntity<Map> response, String transactionId) {
        Map<String, Object> body = response.getBody();
        if (body == null) {
            body = new HashMap<>();
        }
        
        PaymentResponse statusResponse = new PaymentResponse();
        statusResponse.setTransactionId(transactionId);
        statusResponse.setTimestamp(LocalDateTime.now());
        statusResponse.setSuccess(true);
        statusResponse.setStatus((String) body.get("status"));
        statusResponse.setMessage((String) body.get("status_description"));
        
        // Parse settlement information if available
        String settlementDate = (String) body.get("settlement_date");
        if (settlementDate != null) {
            statusResponse.setEstimatedSettlement(LocalDateTime.parse(settlementDate));
        }
        
        return statusResponse;
    }

    private PaymentResponse processACHCancellationResponse(ResponseEntity<Map> response, String transactionId) {
        Map<String, Object> body = response.getBody();
        if (body == null) {
            body = new HashMap<>();
        }
        
        PaymentResponse cancellationResponse = new PaymentResponse();
        cancellationResponse.setTransactionId(transactionId);
        cancellationResponse.setTimestamp(LocalDateTime.now());
        
        if (response.getStatusCode() == HttpStatus.OK) {
            cancellationResponse.setSuccess(true);
            cancellationResponse.setStatus("cancelled");
            cancellationResponse.setMessage("ACH transfer cancelled successfully");
        } else {
            cancellationResponse.setSuccess(false);
            cancellationResponse.setErrorCode((String) body.get("error_code"));
            cancellationResponse.setErrorMessage((String) body.get("error_message"));
        }
        
        return cancellationResponse;
    }

    // Fallback methods for circuit breaker

    public PaymentResponse fallbackProcessPayment(PaymentProvider provider, PaymentRequest request, Exception ex) {
        logger.warn("ACH payment fallback triggered for request: {}", request.getRequestId());
        
        PaymentResponse response = new PaymentResponse();
        response.setRequestId(request.getRequestId());
        response.setSuccess(false);
        response.setErrorCode("PROVIDER_UNAVAILABLE");
        response.setErrorMessage("ACH provider temporarily unavailable");
        response.setTimestamp(LocalDateTime.now());
        
        return response;
    }

    public RefundResponse fallbackProcessRefund(PaymentProvider provider, RefundRequest request, Exception ex) {
        logger.warn("ACH refund fallback triggered for transaction: {}", request.getOriginalTransactionId());
        
        RefundResponse response = new RefundResponse();
        response.setOriginalTransactionId(request.getOriginalTransactionId());
        response.setSuccess(false);
        response.setErrorCode("PROVIDER_UNAVAILABLE");
        response.setErrorMessage("ACH provider temporarily unavailable for refunds");
        response.setTimestamp(LocalDateTime.now());
        
        return response;
    }
}