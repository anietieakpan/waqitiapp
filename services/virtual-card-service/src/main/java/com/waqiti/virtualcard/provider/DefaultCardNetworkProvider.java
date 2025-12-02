package com.waqiti.virtualcard.provider;

import com.waqiti.virtualcard.dto.*;
import com.waqiti.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default implementation of CardNetworkProvider
 * Provides integration with card networks for virtual card operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultCardNetworkProvider implements CardNetworkProvider {
    
    private final RestTemplate restTemplate;
    
    @Value("${card-network.api.base-url:https://api.cardnetwork.com}")
    private String baseUrl;
    
    @Value("${card-network.api.key}")
    private String apiKey;
    
    @Value("${card-network.api.secret}")
    private String apiSecret;
    
    @Value("${card-network.enabled:true}")
    private boolean enabled;
    
    @Value("${card-network.timeout:30000}")
    private int timeoutMs;
    
    @Value("${card-network.provider:DEFAULT}")
    private String providerName;
    
    private static final List<String> SUPPORTED_NETWORKS = Arrays.asList(
        "VISA", "MASTERCARD", "AMERICAN_EXPRESS", "DISCOVER"
    );
    
    @Override
    public CardNetworkRegistration registerCard(CardNetworkRequest request) {
        log.info("Registering card with network provider: {}", providerName);
        
        if (!isAvailable()) {
            throw new BusinessException("Card network provider is not available");
        }
        
        try {
            // Prepare request headers
            HttpHeaders headers = createHeaders();
            
            // Create registration payload
            Map<String, Object> payload = Map.of(
                "card_number", request.getCardNumber(),
                "cvv", request.getCvv(),
                "expiry", request.getExpiry(),
                "cardholder_name", request.getCardholderName(),
                "billing_address", request.getBillingAddress() != null ? request.getBillingAddress() : "",
                "card_type", request.getCardType() != null ? request.getCardType() : "VIRTUAL",
                "three_ds_enabled", request.isThreeDSecureEnabled(),
                "metadata", request.getMetadata() != null ? request.getMetadata() : Map.of()
            );
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            
            // Make API call
            ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/cards/register", 
                entity, 
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                return CardNetworkRegistration.builder()
                    .token((String) responseBody.get("token"))
                    .status((String) responseBody.get("status"))
                    .registrationId((String) responseBody.get("registration_id"))
                    .networkId((String) responseBody.get("network_id"))
                    .issuerBin((String) responseBody.get("issuer_bin"))
                    .processorId((String) responseBody.get("processor_id"))
                    .successful(true)
                    .registeredAt(LocalDateTime.now())
                    .networkBrand((String) responseBody.get("network_brand"))
                    .cardProductId((String) responseBody.get("card_product_id"))
                    .build();
            } else {
                throw new BusinessException("Failed to register card with network");
            }
            
        } catch (Exception e) {
            log.error("Failed to register card with network", e);
            return CardNetworkRegistration.failure("REGISTRATION_FAILED", e.getMessage());
        }
    }
    
    @Override
    public void updateCardStatus(String networkToken, String status) {
        log.info("Updating card status in network: token={}, status={}", maskToken(networkToken), status);
        
        if (!isAvailable()) {
            throw new BusinessException("Card network provider is not available");
        }
        
        try {
            HttpHeaders headers = createHeaders();
            
            Map<String, Object> payload = Map.of(
                "token", networkToken,
                "status", status,
                "updated_at", LocalDateTime.now().toString()
            );
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            
            restTemplate.exchange(
                baseUrl + "/cards/status", 
                HttpMethod.PUT, 
                entity, 
                Void.class
            );
            
        } catch (Exception e) {
            log.error("Failed to update card status in network", e);
            throw new BusinessException("Failed to update card status: " + e.getMessage());
        }
    }
    
    @Override
    public void deregisterCard(String networkToken) {
        log.info("Deregistering card from network: token={}", maskToken(networkToken));
        
        if (!isAvailable()) {
            log.warn("Card network provider is not available, skipping deregistration");
            return;
        }
        
        try {
            HttpHeaders headers = createHeaders();
            
            restTemplate.exchange(
                baseUrl + "/cards/" + networkToken, 
                HttpMethod.DELETE, 
                new HttpEntity<>(headers), 
                Void.class
            );
            
        } catch (Exception e) {
            log.error("Failed to deregister card from network", e);
            // Don't throw exception for deregistration failures
        }
    }
    
    @Override
    public CardNetworkResponse processTransaction(CardNetworkTransactionRequest request) {
        log.debug("Processing transaction through network: token={}, amount={}", 
                 maskToken(request.getToken()), request.getAmount());
        
        if (!isAvailable()) {
            return CardNetworkResponse.error("NETWORK_UNAVAILABLE", "Card network provider is not available");
        }
        
        try {
            HttpHeaders headers = createHeaders();
            
            // Create transaction payload
            Map<String, Object> payload = Map.of(
                "token", request.getToken(),
                "amount", request.getAmount(),
                "currency", request.getCurrency(),
                "merchant", createMerchantPayload(request.getMerchant()),
                "mcc", request.getMcc() != null ? request.getMcc() : "",
                "terminal_id", request.getTerminalId() != null ? request.getTerminalId() : "",
                "transaction_id", request.getTransactionId() != null ? request.getTransactionId() : UUID.randomUUID().toString(),
                "card_present", request.isCardPresent(),
                "cardholder_present", request.isCardholderPresent(),
                "is_international", request.isInternational(),
                "is_online", request.isOnline(),
                "is_contactless", request.isContactless(),
                "is_recurring", request.isRecurring(),
                "three_d_secure", request.isThreeDSecure(),
                "timestamp", LocalDateTime.now().toString()
            );
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            
            // Make transaction request
            ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/transactions/authorize", 
                entity, 
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseTransactionResponse(response.getBody());
            } else {
                return CardNetworkResponse.error("TRANSACTION_FAILED", "Transaction processing failed");
            }
            
        } catch (Exception e) {
            log.error("Failed to process transaction through network", e);
            return CardNetworkResponse.error("PROCESSING_ERROR", e.getMessage());
        }
    }
    
    @Override
    public void updateCVV(String networkToken, String newCvv) {
        log.info("Updating CVV for card in network: token={}", maskToken(networkToken));
        
        if (!isAvailable()) {
            throw new BusinessException("Card network provider is not available");
        }
        
        try {
            HttpHeaders headers = createHeaders();
            
            Map<String, Object> payload = Map.of(
                "token", networkToken,
                "cvv", newCvv,
                "updated_at", LocalDateTime.now().toString()
            );
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            
            restTemplate.exchange(
                baseUrl + "/cards/cvv", 
                HttpMethod.PUT, 
                entity, 
                Void.class
            );
            
        } catch (Exception e) {
            log.error("Failed to update CVV in network", e);
            throw new BusinessException("Failed to update CVV: " + e.getMessage());
        }
    }
    
    @Override
    public void update3DSecure(String networkToken, boolean enabled) {
        log.info("Updating 3D Secure for card in network: token={}, enabled={}", 
                maskToken(networkToken), enabled);
        
        if (!isAvailable()) {
            throw new BusinessException("Card network provider is not available");
        }
        
        try {
            HttpHeaders headers = createHeaders();
            
            Map<String, Object> payload = Map.of(
                "token", networkToken,
                "three_ds_enabled", enabled,
                "updated_at", LocalDateTime.now().toString()
            );
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            
            restTemplate.exchange(
                baseUrl + "/cards/3ds", 
                HttpMethod.PUT, 
                entity, 
                Void.class
            );
            
        } catch (Exception e) {
            log.error("Failed to update 3D Secure in network", e);
            throw new BusinessException("Failed to update 3D Secure: " + e.getMessage());
        }
    }
    
    @Override
    public boolean isAvailable() {
        return enabled && apiKey != null && !apiKey.isEmpty();
    }
    
    @Override
    public String getProviderName() {
        return providerName;
    }
    
    @Override
    public List<String> getSupportedNetworks() {
        return SUPPORTED_NETWORKS;
    }
    
    // Helper methods
    
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("X-API-Secret", apiSecret);
        headers.set("X-Provider", providerName);
        return headers;
    }
    
    private Map<String, Object> createMerchantPayload(CardNetworkTransactionRequest.MerchantInfo merchant) {
        if (merchant == null) {
            return Map.of();
        }
        
        return Map.of(
            "name", merchant.getName() != null ? merchant.getName() : "",
            "id", merchant.getId() != null ? merchant.getId() : "",
            "category", merchant.getCategory() != null ? merchant.getCategory() : "",
            "mcc", merchant.getMcc() != null ? merchant.getMcc() : "",
            "country", merchant.getCountry() != null ? merchant.getCountry() : "",
            "city", merchant.getCity() != null ? merchant.getCity() : "",
            "postal_code", merchant.getPostalCode() != null ? merchant.getPostalCode() : "",
            "address", merchant.getAddress() != null ? merchant.getAddress() : ""
        );
    }
    
    private CardNetworkResponse parseTransactionResponse(Map<String, Object> responseBody) {
        boolean approved = Boolean.TRUE.equals(responseBody.get("approved"));
        String responseCode = (String) responseBody.get("response_code");
        
        CardNetworkResponse.CardNetworkResponseBuilder builder = CardNetworkResponse.builder()
            .transactionId((String) responseBody.get("transaction_id"))
            .authorizationCode((String) responseBody.get("authorization_code"))
            .responseCode(responseCode)
            .responseMessage((String) responseBody.get("response_message"))
            .approved(approved)
            .networkReferenceId((String) responseBody.get("network_reference_id"))
            .retrievalReferenceNumber((String) responseBody.get("retrieval_reference_number"))
            .transactionTimestamp(LocalDateTime.now())
            .responseTimestamp(LocalDateTime.now());
        
        // Parse amounts
        if (responseBody.get("amount") != null) {
            builder.amount(new BigDecimal(responseBody.get("amount").toString()));
        }
        if (responseBody.get("authorized_amount") != null) {
            builder.authorizedAmount(new BigDecimal(responseBody.get("authorized_amount").toString()));
        }
        
        // Parse fees
        if (responseBody.get("interchange_fee") != null) {
            builder.interchangeFee(new BigDecimal(responseBody.get("interchange_fee").toString()));
        }
        if (responseBody.get("network_fee") != null) {
            builder.networkFee(new BigDecimal(responseBody.get("network_fee").toString()));
        }
        
        // Handle decline reason
        if (!approved) {
            builder.declineReason((String) responseBody.get("decline_reason"));
        }
        
        // Parse risk scores
        if (responseBody.get("risk_score") != null) {
            builder.riskScore(Integer.valueOf(responseBody.get("risk_score").toString()));
        }
        
        return builder.build();
    }
    
    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "****";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }
}