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
public class VenmoDepositProvider implements CashDepositProvider {

    private final RestTemplate restTemplate;
    
    @Value("${venmo.cash-deposit.api.base-url}")
    private String venmoApiBaseUrl;
    
    @Value("${venmo.cash-deposit.api.client-id}")
    private String clientId;
    
    @Value("${venmo.cash-deposit.api.client-secret}")
    private String clientSecret;
    
    @Value("${venmo.cash-deposit.max-amount:500}")
    private BigDecimal maxDepositAmount;
    
    @Value("${venmo.cash-deposit.min-amount:5}")
    private BigDecimal minDepositAmount;
    
    @Value("${venmo.cash-deposit.fee-percentage:2.9}")
    private BigDecimal feePercentage;

    @Override
    @CircuitBreaker(name = "venmo-cash-deposit")
    @Retry(name = "venmo-cash-deposit")
    public CashDepositResponse createCashDeposit(CashDepositRequest request) {
        validateDepositRequest(request);
        
        try {
            // Get OAuth access token
            String accessToken = getAccessToken();
            
            // Create Venmo cash deposit order
            VenmoCashDepositOrder order = VenmoCashDepositOrder.builder()
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .recipientUsername(request.getRecipientIdentifier())
                .senderName(request.getSenderName())
                .senderPhone(request.getSenderPhone())
                .note("Waqiti wallet funding")
                .externalReference(generateExternalReference(request))
                .build();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Venmo-Version", "2024-01-01");
            
            HttpEntity<VenmoCashDepositOrder> requestEntity = new HttpEntity<>(order, headers);
            
            ResponseEntity<VenmoCashDepositResponse> response = restTemplate.postForEntity(
                venmoApiBaseUrl + "/api/v1/cash-deposits",
                requestEntity,
                VenmoCashDepositResponse.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new CashDepositException("Failed to create Venmo cash deposit order");
            }
            
            VenmoCashDepositResponse venmoResponse = response.getBody();
            
            // Calculate fees (Venmo has lower fees for cash deposits)
            BigDecimal depositFee = request.getAmount()
                .multiply(feePercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal totalAmount = request.getAmount().add(depositFee);
            
            return CashDepositResponse.builder()
                .transactionId(venmoResponse.getDepositId())
                .status(CashDepositStatus.PENDING)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .fee(depositFee)
                .totalAmount(totalAmount)
                .provider("VENMO")
                .confirmationCode(venmoResponse.getVerificationCode())
                .qrCode(venmoResponse.getQrCodeData())
                .instructions(generateInstructions(venmoResponse))
                .validUntil(Instant.now().plusSeconds(1800)) // 30 minutes validity
                .locations(mapVenmoLocations(venmoResponse.getRetailPartners()))
                .build();
                
        } catch (Exception e) {
            log.error("Failed to create Venmo cash deposit for amount {}", request.getAmount(), e);
            throw new CashDepositException("Venmo cash deposit creation failed", e);
        }
    }

    @Override
    @CircuitBreaker(name = "venmo-cash-deposit")
    public CashDepositStatus checkDepositStatus(String transactionId) {
        try {
            String accessToken = getAccessToken();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.set("Venmo-Version", "2024-01-01");
            
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
            
            ResponseEntity<VenmoDepositStatusResponse> response = restTemplate.exchange(
                venmoApiBaseUrl + "/api/v1/cash-deposits/" + transactionId,
                HttpMethod.GET,
                requestEntity,
                VenmoDepositStatusResponse.class
            );
            
            if (response.getBody() == null) {
                return CashDepositStatus.UNKNOWN;
            }
            
            return mapVenmoStatus(response.getBody().getStatus());
            
        } catch (Exception e) {
            log.error("Failed to check Venmo deposit status for transaction {}", transactionId, e);
            return CashDepositStatus.UNKNOWN;
        }
    }

    @Override
    @CircuitBreaker(name = "venmo-cash-deposit")
    public List<CashDepositLocation> getAvailableLocations(String zipCode, String country) {
        if (!"US".equals(country)) {
            return Collections.emptyList(); // Venmo only available in US
        }
        
        try {
            String accessToken = getAccessToken();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.set("Venmo-Version", "2024-01-01");
            
            String url = String.format("%s/api/v1/cash-deposits/locations?postal_code=%s&radius_miles=10", 
                venmoApiBaseUrl, zipCode);
            
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
            
            ResponseEntity<VenmoLocationResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                requestEntity,
                VenmoLocationResponse.class
            );
            
            if (response.getBody() == null || response.getBody().getPartners() == null) {
                return Collections.emptyList();
            }
            
            return response.getBody().getPartners().stream()
                .map(this::mapVenmoLocation)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Failed to get Venmo locations for zipcode {}", zipCode, e);
            return Collections.emptyList();
        }
    }

    @Override
    public String getProviderName() {
        return "VENMO";
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Venmo-Version", "2024-01-01");
            
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                venmoApiBaseUrl + "/api/v1/status",
                HttpMethod.GET,
                requestEntity,
                Map.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            log.warn("Venmo cash deposit service is not available", e);
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
        return Set.of("US"); // Venmo only available in US
    }

    private String getAccessToken() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, String> authRequest = Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "grant_type", "client_credentials",
                "scope", "cash_deposits write"
            );
            
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(authRequest, headers);
            
            ResponseEntity<VenmoTokenResponse> response = restTemplate.postForEntity(
                venmoApiBaseUrl + "/api/oauth/access_token",
                requestEntity,
                VenmoTokenResponse.class
            );
            
            if (response.getBody() == null) {
                throw new CashDepositException("Failed to obtain Venmo access token");
            }
            
            return response.getBody().getAccessToken();
            
        } catch (Exception e) {
            log.error("Failed to get Venmo access token", e);
            throw new CashDepositException("Venmo authentication failed", e);
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
            throw new IllegalArgumentException("Venmo cash deposits only available in US");
        }
        
        if (request.getRecipientIdentifier() == null || !isValidVenmoUsername(request.getRecipientIdentifier())) {
            throw new IllegalArgumentException("Valid Venmo username required (no @ symbol)");
        }
    }

    private boolean isValidVenmoUsername(String username) {
        return username != null && 
               !username.contains("@") && 
               username.length() >= 5 && 
               username.length() <= 30 &&
               username.matches("[a-zA-Z0-9_-]+");
    }

    private String generateExternalReference(CashDepositRequest request) {
        return "WAQITI_VENMO_" + System.currentTimeMillis() + "_" + 
               UUID.randomUUID().toString().substring(0, 8);
    }

    private List<String> generateInstructions(VenmoCashDepositResponse response) {
        return List.of(
            "1. Visit any participating Venmo retail partner",
            "2. Show the QR code or barcode to the cashier",
            "3. Tell the cashier you want to add cash to Venmo",
            "4. Present a valid government-issued photo ID",
            "5. Give cash amount to the cashier: $" + response.getAmount(),
            "6. Funds will be available in your Venmo and transferred to your wallet within 30 minutes",
            "7. Store may charge a small convenience fee (typically $1-3)",
            "8. Save your receipt and verification code: " + response.getVerificationCode()
        );
    }

    private List<CashDepositLocation> mapVenmoLocations(List<VenmoRetailPartner> partners) {
        return partners.stream()
            .map(this::mapVenmoLocation)
            .collect(Collectors.toList());
    }

    private CashDepositLocation mapVenmoLocation(VenmoRetailPartner partner) {
        return CashDepositLocation.builder()
            .id(partner.getPartnerId())
            .name(partner.getBusinessName())
            .address(partner.getAddress())
            .city(partner.getCity())
            .state(partner.getState())
            .zipCode(partner.getZipCode())
            .country("US")
            .phone(partner.getPhone())
            .hours(partner.getOperatingHours())
            .distance(partner.getDistanceMiles())
            .acceptsCash(true)
            .additionalInfo(Map.of(
                "partnerType", partner.getPartnerType(),
                "maxCashAmount", "$500",
                "convenienceFee", partner.getConvenienceFee(),
                "acceptsDebitCard", String.valueOf(partner.isAcceptsDebitCard())
            ))
            .build();
    }

    private CashDepositStatus mapVenmoStatus(String venmoStatus) {
        switch (venmoStatus.toUpperCase()) {
            case "PENDING":
            case "INITIATED":
                return CashDepositStatus.PENDING;
            case "PROCESSING":
            case "DEPOSITING":
                return CashDepositStatus.IN_PROGRESS;
            case "COMPLETED":
            case "DEPOSITED":
                return CashDepositStatus.COMPLETED;
            case "FAILED":
            case "DECLINED":
            case "INSUFFICIENT_FUNDS":
                return CashDepositStatus.FAILED;
            case "EXPIRED":
                return CashDepositStatus.EXPIRED;
            case "CANCELLED":
            case "REVERTED":
                return CashDepositStatus.CANCELLED;
            default:
                return CashDepositStatus.UNKNOWN;
        }
    }

    // Venmo-specific DTOs
    private static class VenmoCashDepositOrder {
        private BigDecimal amount;
        private String currency;
        private String recipientUsername;
        private String senderName;
        private String senderPhone;
        private String note;
        private String externalReference;
        
        public static VenmoCashDepositOrderBuilder builder() {
            return new VenmoCashDepositOrderBuilder();
        }
        
        public static class VenmoCashDepositOrderBuilder {
            private VenmoCashDepositOrder order = new VenmoCashDepositOrder();
            
            public VenmoCashDepositOrderBuilder amount(BigDecimal amount) {
                order.amount = amount;
                return this;
            }
            
            public VenmoCashDepositOrderBuilder currency(String currency) {
                order.currency = currency;
                return this;
            }
            
            public VenmoCashDepositOrderBuilder recipientUsername(String username) {
                order.recipientUsername = username;
                return this;
            }
            
            public VenmoCashDepositOrderBuilder senderName(String name) {
                order.senderName = name;
                return this;
            }
            
            public VenmoCashDepositOrderBuilder senderPhone(String phone) {
                order.senderPhone = phone;
                return this;
            }
            
            public VenmoCashDepositOrderBuilder note(String note) {
                order.note = note;
                return this;
            }
            
            public VenmoCashDepositOrderBuilder externalReference(String reference) {
                order.externalReference = reference;
                return this;
            }
            
            public VenmoCashDepositOrder build() {
                return order;
            }
        }
        
        // Getters
        public BigDecimal getAmount() { return amount; }
        public String getCurrency() { return currency; }
        public String getRecipientUsername() { return recipientUsername; }
        public String getSenderName() { return senderName; }
        public String getSenderPhone() { return senderPhone; }
        public String getNote() { return note; }
        public String getExternalReference() { return externalReference; }
    }

    private static class VenmoCashDepositResponse {
        private String depositId;
        private String verificationCode;
        private String qrCodeData;
        private BigDecimal amount;
        private String status;
        private List<VenmoRetailPartner> retailPartners;
        private String depositInstructions;
        
        // Getters
        public String getDepositId() { return depositId; }
        public String getVerificationCode() { return verificationCode; }
        public String getQrCodeData() { return qrCodeData; }
        public BigDecimal getAmount() { return amount; }
        public String getStatus() { return status; }
        public List<VenmoRetailPartner> getRetailPartners() { return retailPartners; }
        public String getDepositInstructions() { return depositInstructions; }
    }

    private static class VenmoDepositStatusResponse {
        private String depositId;
        private String status;
        private BigDecimal amount;
        private String currency;
        private Instant processedAt;
        private String errorMessage;
        
        // Getters
        public String getDepositId() { return depositId; }
        public String getStatus() { return status; }
        public BigDecimal getAmount() { return amount; }
        public String getCurrency() { return currency; }
        public Instant getProcessedAt() { return processedAt; }
        public String getErrorMessage() { return errorMessage; }
    }

    private static class VenmoRetailPartner {
        private String partnerId;
        private String businessName;
        private String partnerType;
        private String address;
        private String city;
        private String state;
        private String zipCode;
        private String phone;
        private Map<String, String> operatingHours;
        private Double distanceMiles;
        private String convenienceFee;
        private boolean acceptsDebitCard;
        
        // Getters
        public String getPartnerId() { return partnerId; }
        public String getBusinessName() { return businessName; }
        public String getPartnerType() { return partnerType; }
        public String getAddress() { return address; }
        public String getCity() { return city; }
        public String getState() { return state; }
        public String getZipCode() { return zipCode; }
        public String getPhone() { return phone; }
        public Map<String, String> getOperatingHours() { return operatingHours; }
        public Double getDistanceMiles() { return distanceMiles; }
        public String getConvenienceFee() { return convenienceFee; }
        public boolean isAcceptsDebitCard() { return acceptsDebitCard; }
    }

    private static class VenmoTokenResponse {
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

    private static class VenmoLocationResponse {
        private List<VenmoRetailPartner> partners;
        private Integer count;
        private String searchArea;
        
        // Getters
        public List<VenmoRetailPartner> getPartners() { return partners; }
        public Integer getCount() { return count; }
        public String getSearchArea() { return searchArea; }
    }
}