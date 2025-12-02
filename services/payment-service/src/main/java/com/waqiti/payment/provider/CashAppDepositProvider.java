package com.waqiti.payment.provider;

import com.waqiti.payment.dto.CashDepositRequest;
import com.waqiti.payment.dto.CashDepositResponse;
import com.waqiti.payment.dto.CashDepositLocation;
import com.waqiti.payment.dto.CashDepositStatus;
import com.waqiti.payment.exception.CashDepositException;
import com.waqiti.common.resilience.annotations.CircuitBreaker;
import com.waqiti.common.resilience.annotations.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CashAppDepositProvider implements CashDepositProvider {

    private final RestTemplate restTemplate;
    
    @Value("${cashapp.cash-deposit.api.base-url}")
    private String cashAppApiBaseUrl;
    
    @Value("${cashapp.cash-deposit.api.app-id}")
    private String appId;
    
    @Value("${cashapp.cash-deposit.api.app-secret}")
    private String appSecret;
    
    @Value("${cashapp.cash-deposit.max-amount:999}")
    private BigDecimal maxDepositAmount;
    
    @Value("${cashapp.cash-deposit.min-amount:1}")
    private BigDecimal minDepositAmount;
    
    @Value("${cashapp.cash-deposit.fee-flat:0}")
    private BigDecimal flatFee;

    @Override
    @CircuitBreaker(name = "cashapp-cash-deposit")
    @Retry(name = "cashapp-cash-deposit")
    public CashDepositResponse createCashDeposit(CashDepositRequest request) {
        validateDepositRequest(request);
        
        try {
            // Get API access token
            String accessToken = getAccessToken();
            
            // Create CashApp cash deposit order
            CashAppDepositOrder order = CashAppDepositOrder.builder()
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .recipientCashTag(request.getRecipientIdentifier())
                .senderName(request.getSenderName())
                .senderPhone(request.getSenderPhone())
                .purpose("DIGITAL_WALLET_FUNDING")
                .externalId(generateExternalId(request))
                .notificationEmail(request.getNotificationEmail())
                .build();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Cash-App-Version", "2024-03-15");
            
            HttpEntity<CashAppDepositOrder> requestEntity = new HttpEntity<>(order, headers);
            
            ResponseEntity<CashAppDepositResponse> response = restTemplate.postForEntity(
                cashAppApiBaseUrl + "/v2/cash-deposits",
                requestEntity,
                CashAppDepositResponse.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new CashDepositException("Failed to create CashApp cash deposit order");
            }
            
            CashAppDepositResponse cashAppResponse = response.getBody();
            
            return CashDepositResponse.builder()
                .transactionId(cashAppResponse.getDepositId())
                .status(CashDepositStatus.PENDING)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .fee(flatFee)
                .totalAmount(request.getAmount().add(flatFee))
                .provider("CASHAPP")
                .confirmationCode(cashAppResponse.getBarcodeValue())
                .qrCode(cashAppResponse.getQrCodeData())
                .instructions(generateInstructions(cashAppResponse))
                .validUntil(Instant.now().plusSeconds(86400)) // 24 hours validity
                .locations(mapCashAppLocations(cashAppResponse.getPartnerStores()))
                .build();
                
        } catch (Exception e) {
            log.error("Failed to create CashApp cash deposit for amount {}", request.getAmount(), e);
            throw new CashDepositException("CashApp cash deposit creation failed", e);
        }
    }

    @Override
    @CircuitBreaker(name = "cashapp-cash-deposit")
    public CashDepositStatus checkDepositStatus(String transactionId) {
        try {
            String accessToken = getAccessToken();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.set("X-Cash-App-Version", "2024-03-15");
            
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
            
            ResponseEntity<CashAppStatusResponse> response = restTemplate.exchange(
                cashAppApiBaseUrl + "/v2/cash-deposits/" + transactionId,
                HttpMethod.GET,
                requestEntity,
                CashAppStatusResponse.class
            );
            
            if (response.getBody() == null) {
                return CashDepositStatus.UNKNOWN;
            }
            
            return mapCashAppStatus(response.getBody().getStatus());
            
        } catch (Exception e) {
            log.error("Failed to check CashApp deposit status for transaction {}", transactionId, e);
            return CashDepositStatus.UNKNOWN;
        }
    }

    @Override
    @CircuitBreaker(name = "cashapp-cash-deposit")
    public List<CashDepositLocation> getAvailableLocations(String zipCode, String country) {
        if (!"US".equals(country)) {
            return Collections.emptyList(); // CashApp only available in US
        }
        
        try {
            String accessToken = getAccessToken();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.set("X-Cash-App-Version", "2024-03-15");
            
            String url = String.format("%s/v2/cash-deposits/locations?zip=%s&radius=15", 
                cashAppApiBaseUrl, zipCode);
            
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
            
            ResponseEntity<CashAppLocationResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                requestEntity,
                CashAppLocationResponse.class
            );
            
            if (response.getBody() == null || response.getBody().getStores() == null) {
                return Collections.emptyList();
            }
            
            return response.getBody().getStores().stream()
                .map(this::mapCashAppLocation)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Failed to get CashApp locations for zipcode {}", zipCode, e);
            return Collections.emptyList();
        }
    }

    @Override
    public String getProviderName() {
        return "CASHAPP";
    }

    @Override
    public boolean isAvailable() {
        try {
            String accessToken = getAccessToken();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.set("X-Cash-App-Version", "2024-03-15");
            
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                cashAppApiBaseUrl + "/v2/health",
                HttpMethod.GET,
                requestEntity,
                Map.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            log.warn("CashApp cash deposit service is not available", e);
            return false;
        }
    }

    @Override
    public BigDecimal getMaxAmount() {
        return maxDepositAmount;
    }

    @Override
    public BigDecimal getMinAmount() {
        return minDepositAmount;
    }

    @Override
    public Set<String> getSupportedCountries() {
        return Set.of("US"); // CashApp only available in US
    }

    private String getAccessToken() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, String> authRequest = Map.of(
                "app_id", appId,
                "app_secret", appSecret,
                "grant_type", "client_credentials",
                "scope", "cash_deposits"
            );
            
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(authRequest, headers);
            
            ResponseEntity<CashAppTokenResponse> response = restTemplate.postForEntity(
                cashAppApiBaseUrl + "/v2/oauth/token",
                requestEntity,
                CashAppTokenResponse.class
            );
            
            if (response.getBody() == null) {
                throw new CashDepositException("Failed to obtain CashApp access token");
            }
            
            return response.getBody().getAccessToken();
            
        } catch (Exception e) {
            log.error("Failed to get CashApp access token", e);
            throw new CashDepositException("CashApp authentication failed", e);
        }
    }

    private void validateDepositRequest(CashDepositRequest request) {
        if (request.getAmount().compareTo(minDepositAmount) < 0) {
            throw new IllegalArgumentException("Amount below minimum: " + minDepositAmount);
        }
        
        if (request.getAmount().compareTo(maxDepositAmount) > 0) {
            throw new IllegalArgumentException("Amount exceeds maximum: " + maxDepositAmount);
        }
        
        if (!"US".equals(request.getCountry())) {
            throw new IllegalArgumentException("CashApp cash deposits only available in US");
        }
        
        if (request.getRecipientIdentifier() == null || !isValidCashTag(request.getRecipientIdentifier())) {
            throw new IllegalArgumentException("Valid CashApp $cashtag required (e.g., $username)");
        }
    }

    private boolean isValidCashTag(String cashTag) {
        return cashTag != null && 
               cashTag.startsWith("$") && 
               cashTag.length() > 1 && 
               cashTag.length() <= 21 &&
               cashTag.substring(1).matches("[a-zA-Z0-9_]+");
    }

    private String generateExternalId(CashDepositRequest request) {
        return "WAQITI_CA_" + System.currentTimeMillis() + "_" + 
               UUID.randomUUID().toString().substring(0, 8);
    }

    private List<String> generateInstructions(CashAppDepositResponse response) {
        return List.of(
            "1. Visit any participating retail store (CVS, Walgreens, Dollar General, etc.)",
            "2. Show the barcode or QR code to the cashier",
            "3. Tell the cashier you want to add cash to CashApp",
            "4. Present a valid government-issued photo ID",
            "5. Give cash amount to the cashier: $" + response.getAmount(),
            "6. Funds will be available in your CashApp and transferred to your wallet within 10 minutes",
            "7. You may be charged a store fee by the retailer (typically $1-4)"
        );
    }

    private List<CashDepositLocation> mapCashAppLocations(List<CashAppStore> stores) {
        return stores.stream()
            .map(this::mapCashAppLocation)
            .collect(Collectors.toList());
    }

    private CashDepositLocation mapCashAppLocation(CashAppStore store) {
        return CashDepositLocation.builder()
            .id(store.getStoreId())
            .name(store.getStoreName())
            .address(store.getAddress())
            .city(store.getCity())
            .state(store.getState())
            .zipCode(store.getZipCode())
            .country("US")
            .phone(store.getPhone())
            .hours(store.getHours())
            .distance(store.getDistance())
            .acceptsCash(true)
            .additionalInfo(Map.of(
                "chain", store.getChain(),
                "maxCashAmount", "$999",
                "storeFee", store.getStoreFee()
            ))
            .build();
    }

    private CashDepositStatus mapCashAppStatus(String cashAppStatus) {
        switch (cashAppStatus.toUpperCase()) {
            case "PENDING":
            case "CREATED":
                return CashDepositStatus.PENDING;
            case "PROCESSING":
            case "CONFIRMED":
                return CashDepositStatus.IN_PROGRESS;
            case "COMPLETED":
            case "SETTLED":
                return CashDepositStatus.COMPLETED;
            case "FAILED":
            case "REJECTED":
                return CashDepositStatus.FAILED;
            case "EXPIRED":
                return CashDepositStatus.EXPIRED;
            case "CANCELLED":
                return CashDepositStatus.CANCELLED;
            default:
                return CashDepositStatus.UNKNOWN;
        }
    }

    // CashApp-specific DTOs
    private static class CashAppDepositOrder {
        private BigDecimal amount;
        private String currency;
        private String recipientCashTag;
        private String senderName;
        private String senderPhone;
        private String purpose;
        private String externalId;
        private String notificationEmail;
        
        public static CashAppDepositOrderBuilder builder() {
            return new CashAppDepositOrderBuilder();
        }
        
        public static class CashAppDepositOrderBuilder {
            private CashAppDepositOrder order = new CashAppDepositOrder();
            
            public CashAppDepositOrderBuilder amount(BigDecimal amount) {
                order.amount = amount;
                return this;
            }
            
            public CashAppDepositOrderBuilder currency(String currency) {
                order.currency = currency;
                return this;
            }
            
            public CashAppDepositOrderBuilder recipientCashTag(String cashTag) {
                order.recipientCashTag = cashTag;
                return this;
            }
            
            public CashAppDepositOrderBuilder senderName(String name) {
                order.senderName = name;
                return this;
            }
            
            public CashAppDepositOrderBuilder senderPhone(String phone) {
                order.senderPhone = phone;
                return this;
            }
            
            public CashAppDepositOrderBuilder purpose(String purpose) {
                order.purpose = purpose;
                return this;
            }
            
            public CashAppDepositOrderBuilder externalId(String externalId) {
                order.externalId = externalId;
                return this;
            }
            
            public CashAppDepositOrderBuilder notificationEmail(String email) {
                order.notificationEmail = email;
                return this;
            }
            
            public CashAppDepositOrder build() {
                return order;
            }
        }
        
        // Getters
        public BigDecimal getAmount() { return amount; }
        public String getCurrency() { return currency; }
        public String getRecipientCashTag() { return recipientCashTag; }
        public String getSenderName() { return senderName; }
        public String getSenderPhone() { return senderPhone; }
        public String getPurpose() { return purpose; }
        public String getExternalId() { return externalId; }
        public String getNotificationEmail() { return notificationEmail; }
    }

    private static class CashAppDepositResponse {
        private String depositId;
        private String barcodeValue;
        private String qrCodeData;
        private BigDecimal amount;
        private String status;
        private List<CashAppStore> partnerStores;
        private String instructions;
        
        // Getters
        public String getDepositId() { return depositId; }
        public String getBarcodeValue() { return barcodeValue; }
        public String getQrCodeData() { return qrCodeData; }
        public BigDecimal getAmount() { return amount; }
        public String getStatus() { return status; }
        public List<CashAppStore> getPartnerStores() { return partnerStores; }
        public String getInstructions() { return instructions; }
    }

    private static class CashAppStatusResponse {
        private String depositId;
        private String status;
        private BigDecimal amount;
        private String currency;
        private Instant completedAt;
        private String failureReason;
        
        // Getters
        public String getDepositId() { return depositId; }
        public String getStatus() { return status; }
        public BigDecimal getAmount() { return amount; }
        public String getCurrency() { return currency; }
        public Instant getCompletedAt() { return completedAt; }
        public String getFailureReason() { return failureReason; }
    }

    private static class CashAppStore {
        private String storeId;
        private String storeName;
        private String chain;
        private String address;
        private String city;
        private String state;
        private String zipCode;
        private String phone;
        private Map<String, String> hours;
        private Double distance;
        private String storeFee;
        
        // Getters
        public String getStoreId() { return storeId; }
        public String getStoreName() { return storeName; }
        public String getChain() { return chain; }
        public String getAddress() { return address; }
        public String getCity() { return city; }
        public String getState() { return state; }
        public String getZipCode() { return zipCode; }
        public String getPhone() { return phone; }
        public Map<String, String> getHours() { return hours; }
        public Double getDistance() { return distance; }
        public String getStoreFee() { return storeFee; }
    }

    private static class CashAppTokenResponse {
        private String accessToken;
        private String tokenType;
        private Integer expiresIn;
        private String scope;
        
        // Getters
        public String getAccessToken() { return accessToken; }
        public String getTokenType() { return tokenType; }
        public Integer getExpiresIn() { return expiresIn; }
        public String getScope() { return scope; }
    }

    private static class CashAppLocationResponse {
        private List<CashAppStore> stores;
        private Integer totalCount;
        private String searchRadius;
        
        // Getters
        public List<CashAppStore> getStores() { return stores; }
        public Integer getTotalCount() { return totalCount; }
        public String getSearchRadius() { return searchRadius; }
    }
}