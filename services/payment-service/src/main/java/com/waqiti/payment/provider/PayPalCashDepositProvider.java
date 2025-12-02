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
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayPalCashDepositProvider implements CashDepositProvider {

    private final RestTemplate restTemplate;
    
    @Value("${paypal.cash-deposit.api.base-url}")
    private String paypalApiBaseUrl;
    
    @Value("${paypal.cash-deposit.api.client-id}")
    private String clientId;
    
    @Value("${paypal.cash-deposit.api.client-secret}")
    private String clientSecret;
    
    @Value("${paypal.cash-deposit.max-amount:2500}")
    private BigDecimal maxDepositAmount;
    
    @Value("${paypal.cash-deposit.min-amount:10}")
    private BigDecimal minDepositAmount;
    
    @Value("${paypal.cash-deposit.fee-percentage:3.5}")
    private BigDecimal feePercentage;

    @Override
    @CircuitBreaker(name = "paypal-cash-deposit")
    @Retry(name = "paypal-cash-deposit")
    public CashDepositResponse createCashDeposit(CashDepositRequest request) {
        validateDepositRequest(request);
        
        try {
            // Get OAuth access token
            String accessToken = getAccessToken();
            
            // Create cash deposit order with PayPal
            PayPalCashDepositOrder order = PayPalCashDepositOrder.builder()
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .recipientEmail(request.getRecipientIdentifier())
                .senderName(request.getSenderName())
                .senderPhone(request.getSenderPhone())
                .purpose("WALLET_FUNDING")
                .referenceId(generateReferenceId(request))
                .build();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<PayPalCashDepositOrder> requestEntity = new HttpEntity<>(order, headers);
            
            ResponseEntity<PayPalCashDepositResponse> response = restTemplate.postForEntity(
                paypalApiBaseUrl + "/v1/cash-deposit/orders",
                requestEntity,
                PayPalCashDepositResponse.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new CashDepositException("Failed to create PayPal cash deposit order");
            }
            
            PayPalCashDepositResponse paypalResponse = response.getBody();
            
            // Calculate fees
            BigDecimal depositFee = request.getAmount()
                .multiply(feePercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal totalAmount = request.getAmount().add(depositFee);
            
            return CashDepositResponse.builder()
                .transactionId(paypalResponse.getOrderId())
                .status(CashDepositStatus.PENDING)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .fee(depositFee)
                .totalAmount(totalAmount)
                .provider("PAYPAL")
                .confirmationCode(paypalResponse.getConfirmationCode())
                .qrCode(paypalResponse.getQrCodeData())
                .instructions(generateInstructions(paypalResponse))
                .validUntil(Instant.now().plusSeconds(3600)) // 1 hour validity
                .locations(mapPayPalLocations(paypalResponse.getAcceptingLocations()))
                .build();
                
        } catch (Exception e) {
            log.error("Failed to create PayPal cash deposit for amount {}", request.getAmount(), e);
            throw new CashDepositException("PayPal cash deposit creation failed", e);
        }
    }

    @Override
    @CircuitBreaker(name = "paypal-cash-deposit")
    public CashDepositStatus checkDepositStatus(String transactionId) {
        try {
            String accessToken = getAccessToken();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
            
            ResponseEntity<PayPalDepositStatusResponse> response = restTemplate.exchange(
                paypalApiBaseUrl + "/v1/cash-deposit/orders/" + transactionId,
                HttpMethod.GET,
                requestEntity,
                PayPalDepositStatusResponse.class
            );
            
            if (response.getBody() == null) {
                return CashDepositStatus.UNKNOWN;
            }
            
            return mapPayPalStatus(response.getBody().getStatus());
            
        } catch (Exception e) {
            log.error("Failed to check PayPal deposit status for transaction {}", transactionId, e);
            return CashDepositStatus.UNKNOWN;
        }
    }

    @Override
    @CircuitBreaker(name = "paypal-cash-deposit")
    public List<CashDepositLocation> getAvailableLocations(String zipCode, String country) {
        try {
            String accessToken = getAccessToken();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            
            String url = String.format("%s/v1/cash-deposit/locations?zipcode=%s&country=%s&radius=25", 
                paypalApiBaseUrl, zipCode, country);
            
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
            
            ResponseEntity<PayPalLocationResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                requestEntity,
                PayPalLocationResponse.class
            );
            
            if (response.getBody() == null || response.getBody().getLocations() == null) {
                return Collections.emptyList();
            }
            
            return response.getBody().getLocations().stream()
                .map(this::mapPayPalLocation)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Failed to get PayPal locations for zipcode {}", zipCode, e);
            return Collections.emptyList();
        }
    }

    @Override
    public String getProviderName() {
        return "PAYPAL";
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, String> healthCheck = Map.of("status", "check");
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(healthCheck, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                paypalApiBaseUrl + "/v1/health",
                requestEntity,
                Map.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            log.warn("PayPal cash deposit service is not available", e);
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
        return Set.of("US", "CA", "GB", "AU", "DE", "FR", "IT", "ES", "NL", "BE");
    }

    private String getAccessToken() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(clientId, clientSecret);
            
            String body = "grant_type=client_credentials&scope=cash-deposit";
            HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);
            
            ResponseEntity<PayPalTokenResponse> response = restTemplate.postForEntity(
                paypalApiBaseUrl + "/v1/oauth2/token",
                requestEntity,
                PayPalTokenResponse.class
            );
            
            if (response.getBody() == null) {
                throw new CashDepositException("Failed to obtain PayPal access token");
            }
            
            return response.getBody().getAccessToken();
            
        } catch (Exception e) {
            log.error("Failed to get PayPal access token", e);
            throw new CashDepositException("PayPal authentication failed", e);
        }
    }

    private void validateDepositRequest(CashDepositRequest request) {
        if (request.getAmount().compareTo(minDepositAmount) < 0) {
            throw new IllegalArgumentException("Amount below minimum: " + minDepositAmount);
        }
        
        if (request.getAmount().compareTo(maxDepositAmount) > 0) {
            throw new IllegalArgumentException("Amount exceeds maximum: " + maxDepositAmount);
        }
        
        if (!getSupportedCountries().contains(request.getCountry())) {
            throw new IllegalArgumentException("PayPal cash deposits not available in: " + request.getCountry());
        }
        
        if (request.getRecipientIdentifier() == null || !isValidEmail(request.getRecipientIdentifier())) {
            throw new IllegalArgumentException("Valid PayPal email address required");
        }
    }

    private boolean isValidEmail(String email) {
        return email != null && email.contains("@") && email.contains(".");
    }

    private String generateReferenceId(CashDepositRequest request) {
        return "WAQITI_PAYPAL_" + System.currentTimeMillis() + "_" + 
               request.getAmount().toString().replace(".", "");
    }

    private List<String> generateInstructions(PayPalCashDepositResponse response) {
        return List.of(
            "1. Visit any participating PayPal cash deposit location",
            "2. Show the QR code or provide confirmation code: " + response.getConfirmationCode(),
            "3. Present a valid government-issued photo ID",
            "4. Give cash amount to the cashier: $" + response.getAmount(),
            "5. Funds will be available in your wallet within 30 minutes",
            "6. Keep your receipt for your records"
        );
    }

    private List<CashDepositLocation> mapPayPalLocations(List<PayPalLocation> paypalLocations) {
        return paypalLocations.stream()
            .map(location -> CashDepositLocation.builder()
                .id(location.getLocationId())
                .name(location.getBusinessName())
                .address(location.getAddress())
                .city(location.getCity())
                .state(location.getState())
                .zipCode(location.getZipCode())
                .country(location.getCountry())
                .phone(location.getPhone())
                .hours(location.getBusinessHours())
                .distance(location.getDistanceMiles())
                .acceptsCash(true)
                .build())
            .collect(Collectors.toList());
    }

    private CashDepositLocation mapPayPalLocation(PayPalLocation location) {
        return CashDepositLocation.builder()
            .id(location.getLocationId())
            .name(location.getBusinessName())
            .address(location.getAddress())
            .city(location.getCity())
            .state(location.getState())
            .zipCode(location.getZipCode())
            .country(location.getCountry())
            .phone(location.getPhone())
            .hours(location.getBusinessHours())
            .distance(location.getDistanceMiles())
            .acceptsCash(true)
            .build();
    }

    private CashDepositStatus mapPayPalStatus(String paypalStatus) {
        switch (paypalStatus.toUpperCase()) {
            case "PENDING":
            case "CREATED":
                return CashDepositStatus.PENDING;
            case "IN_PROGRESS":
            case "PROCESSING":
                return CashDepositStatus.IN_PROGRESS;
            case "COMPLETED":
            case "SUCCESS":
                return CashDepositStatus.COMPLETED;
            case "FAILED":
            case "DECLINED":
                return CashDepositStatus.FAILED;
            case "EXPIRED":
                return CashDepositStatus.EXPIRED;
            case "CANCELLED":
                return CashDepositStatus.CANCELLED;
            default:
                return CashDepositStatus.UNKNOWN;
        }
    }

    // PayPal-specific DTOs
    private static class PayPalCashDepositOrder {
        private BigDecimal amount;
        private String currency;
        private String recipientEmail;
        private String senderName;
        private String senderPhone;
        private String purpose;
        private String referenceId;
        
        public static PayPalCashDepositOrderBuilder builder() {
            return new PayPalCashDepositOrderBuilder();
        }
        
        // Builder implementation
        public static class PayPalCashDepositOrderBuilder {
            private PayPalCashDepositOrder order = new PayPalCashDepositOrder();
            
            public PayPalCashDepositOrderBuilder amount(BigDecimal amount) {
                order.amount = amount;
                return this;
            }
            
            public PayPalCashDepositOrderBuilder currency(String currency) {
                order.currency = currency;
                return this;
            }
            
            public PayPalCashDepositOrderBuilder recipientEmail(String email) {
                order.recipientEmail = email;
                return this;
            }
            
            public PayPalCashDepositOrderBuilder senderName(String name) {
                order.senderName = name;
                return this;
            }
            
            public PayPalCashDepositOrderBuilder senderPhone(String phone) {
                order.senderPhone = phone;
                return this;
            }
            
            public PayPalCashDepositOrderBuilder purpose(String purpose) {
                order.purpose = purpose;
                return this;
            }
            
            public PayPalCashDepositOrderBuilder referenceId(String referenceId) {
                order.referenceId = referenceId;
                return this;
            }
            
            public PayPalCashDepositOrder build() {
                return order;
            }
        }
        
        // Getters
        public BigDecimal getAmount() { return amount; }
        public String getCurrency() { return currency; }
        public String getRecipientEmail() { return recipientEmail; }
        public String getSenderName() { return senderName; }
        public String getSenderPhone() { return senderPhone; }
        public String getPurpose() { return purpose; }
        public String getReferenceId() { return referenceId; }
    }

    private static class PayPalCashDepositResponse {
        private String orderId;
        private String confirmationCode;
        private String qrCodeData;
        private BigDecimal amount;
        private String status;
        private List<PayPalLocation> acceptingLocations;
        
        // Getters
        public String getOrderId() { return orderId; }
        public String getConfirmationCode() { return confirmationCode; }
        public String getQrCodeData() { return qrCodeData; }
        public BigDecimal getAmount() { return amount; }
        public String getStatus() { return status; }
        public List<PayPalLocation> getAcceptingLocations() { return acceptingLocations; }
    }

    private static class PayPalDepositStatusResponse {
        private String orderId;
        private String status;
        private BigDecimal amount;
        private String currency;
        private Instant completedAt;
        
        // Getters
        public String getOrderId() { return orderId; }
        public String getStatus() { return status; }
        public BigDecimal getAmount() { return amount; }
        public String getCurrency() { return currency; }
        public Instant getCompletedAt() { return completedAt; }
    }

    private static class PayPalLocation {
        private String locationId;
        private String businessName;
        private String address;
        private String city;
        private String state;
        private String zipCode;
        private String country;
        private String phone;
        private Map<String, String> businessHours;
        private Double distanceMiles;
        
        // Getters
        public String getLocationId() { return locationId; }
        public String getBusinessName() { return businessName; }
        public String getAddress() { return address; }
        public String getCity() { return city; }
        public String getState() { return state; }
        public String getZipCode() { return zipCode; }
        public String getCountry() { return country; }
        public String getPhone() { return phone; }
        public Map<String, String> getBusinessHours() { return businessHours; }
        public Double getDistanceMiles() { return distanceMiles; }
    }

    private static class PayPalTokenResponse {
        private String accessToken;
        private String tokenType;
        private Integer expiresIn;
        
        // Getters
        public String getAccessToken() { return accessToken; }
        public String getTokenType() { return tokenType; }
        public Integer getExpiresIn() { return expiresIn; }
    }

    private static class PayPalLocationResponse {
        private List<PayPalLocation> locations;
        private Integer totalCount;
        
        // Getters
        public List<PayPalLocation> getLocations() { return locations; }
        public Integer getTotalCount() { return totalCount; }
    }
}