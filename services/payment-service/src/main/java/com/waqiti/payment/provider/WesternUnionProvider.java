package com.waqiti.payment.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.soap.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Western Union cash deposit provider implementation
 * Integrates with Western Union Business Solutions APIs
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WesternUnionProvider implements CashDepositProvider {
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${westernunion.api.url:https://api.westernunion.com/ws/v1}")
    private String apiUrl;
    
    @Value("${westernunion.api.partner-id}")
    private String partnerId;
    
    @Value("${westernunion.api.partner-key}")
    private String partnerKey;
    
    @Value("${westernunion.api.agent-id}")
    private String agentId;
    
    @Value("${westernunion.api.timeout:30000}")
    private int timeoutMs;
    
    @Value("${westernunion.cache.locations.ttl:3600}")
    private int locationCacheTtlSeconds;
    
    @Value("${westernunion.api.environment:production}")
    private String environment;
    
    // Session cache for WU APIs
    private final Map<String, SessionInfo> sessionCache = new ConcurrentHashMap<>();
    
    @Override
    public String getProviderName() {
        return "Western Union";
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // Check WU API status endpoint
            String statusUrl = apiUrl + "/system/status";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-WU-Partner-Id", partnerId);
            headers.set("X-WU-Timestamp", ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT));
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                statusUrl,
                HttpMethod.GET,
                entity,
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> status = response.getBody();
                return status != null && "OPERATIONAL".equals(status.get("systemStatus"));
            }
            
            return false;
        } catch (Exception e) {
            log.warn("Western Union API health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public BigDecimal getMinimumAmount() {
        return new BigDecimal("10.00");
    }
    
    @Override
    public BigDecimal getMaximumAmount() {
        return new BigDecimal("5000.00"); // WU limit with ID verification
    }
    
    @Override
    public BigDecimal calculateFee(BigDecimal amount) {
        try {
            // Call WU fee calculation service
            String sessionId = getSessionId();
            
            SOAPMessage soapRequest = createSOAPRequest("GetFeeQuote");
            SOAPBody body = soapRequest.getSOAPBody();
            
            SOAPElement feeQuoteRequest = body.addChildElement("FeeQuoteRequest", "wu");
            feeQuoteRequest.addChildElement("SessionId").addTextNode(sessionId);
            feeQuoteRequest.addChildElement("Amount").addTextNode(amount.toString());
            feeQuoteRequest.addChildElement("Currency").addTextNode("USD");
            feeQuoteRequest.addChildElement("ServiceType").addTextNode("CASH_DEPOSIT");
            feeQuoteRequest.addChildElement("DeliveryOption").addTextNode("IMMEDIATE");
            
            SOAPMessage soapResponse = callSOAPService(soapRequest, "GetFeeQuote");
            
            NodeList feeNodes = soapResponse.getSOAPBody().getElementsByTagName("TotalFee");
            if (feeNodes.getLength() > 0) {
                String feeAmount = feeNodes.item(0).getTextContent();
                return new BigDecimal(feeAmount);
            }
            
        } catch (Exception e) {
            log.error("Failed to calculate Western Union fee via API", e);
        }
        
        // Fallback to tiered fee structure
        if (amount.compareTo(new BigDecimal("50")) <= 0) {
            return new BigDecimal("4.00");
        } else if (amount.compareTo(new BigDecimal("100")) <= 0) {
            return new BigDecimal("5.00");
        } else if (amount.compareTo(new BigDecimal("300")) <= 0) {
            return new BigDecimal("6.50");
        } else if (amount.compareTo(new BigDecimal("500")) <= 0) {
            return new BigDecimal("8.00");
        } else if (amount.compareTo(new BigDecimal("1000")) <= 0) {
            return new BigDecimal("10.00");
        } else if (amount.compareTo(new BigDecimal("2500")) <= 0) {
            return new BigDecimal("12.50");
        } else {
            return new BigDecimal("15.00");
        }
    }
    
    @Override
    @Cacheable(value = "westernunion-locations", key = "#latitude + '-' + #longitude + '-' + #radiusMiles")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public List<CashDepositLocation> getNearbyLocations(double latitude, double longitude, int radiusMiles) {
        try {
            String sessionId = getSessionId();
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-WU-Session-Id", sessionId);
            headers.set("X-WU-Partner-Id", partnerId);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> request = new HashMap<>();
            request.put("latitude", latitude);
            request.put("longitude", longitude);
            request.put("radius", radiusMiles);
            request.put("radiusUnit", "MILES");
            request.put("serviceTypes", Arrays.asList("CASH_DEPOSIT", "QUICK_CASH"));
            request.put("maxResults", 25);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<LocationSearchResponse> response = restTemplate.exchange(
                apiUrl + "/locations/search",
                HttpMethod.POST,
                entity,
                LocationSearchResponse.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody().getLocations().stream()
                    .map(this::convertToLocation)
                    .collect(Collectors.toList());
            }
            
        } catch (Exception e) {
            log.error("Failed to fetch Western Union locations", e);
        }
        
        return new ArrayList<>();
    }
    
    @Override
    public CashDepositReference generateReference(String userId, BigDecimal amount) {
        try {
            String sessionId = getSessionId();
            
            // Create MTCN (Money Transfer Control Number)
            SOAPMessage soapRequest = createSOAPRequest("CreateTransaction");
            SOAPBody body = soapRequest.getSOAPBody();
            
            SOAPElement transactionRequest = body.addChildElement("TransactionRequest", "wu");
            transactionRequest.addChildElement("SessionId").addTextNode(sessionId);
            transactionRequest.addChildElement("AgentId").addTextNode(agentId);
            transactionRequest.addChildElement("TransactionType").addTextNode("CASH_DEPOSIT");
            
            SOAPElement sender = transactionRequest.addChildElement("Sender");
            sender.addChildElement("UserId").addTextNode(hashUserId(userId));
            
            SOAPElement transaction = transactionRequest.addChildElement("Transaction");
            transaction.addChildElement("Amount").addTextNode(amount.toString());
            transaction.addChildElement("Currency").addTextNode("USD");
            transaction.addChildElement("Purpose").addTextNode("WALLET_FUNDING");
            
            SOAPMessage soapResponse = callSOAPService(soapRequest, "CreateTransaction");
            
            NodeList mtcnNodes = soapResponse.getSOAPBody().getElementsByTagName("MTCN");
            if (mtcnNodes.getLength() > 0) {
                String mtcn = mtcnNodes.item(0).getTextContent();
                
                // Get expiry from response
                NodeList expiryNodes = soapResponse.getSOAPBody().getElementsByTagName("ExpiryDateTime");
                LocalDateTime expiry = expiryNodes.getLength() > 0 
                    ? LocalDateTime.parse(expiryNodes.item(0).getTextContent())
                    : LocalDateTime.now().plusDays(10);
                
                return new CashDepositReference(
                    mtcn,
                    generateBarcode(mtcn, amount),
                    generateQRCode(mtcn, amount, userId),
                    amount,
                    calculateFee(amount),
                    expiry
                );
            }
            
        } catch (Exception e) {
            log.error("Failed to generate Western Union reference", e);
        }
        
        // Fallback reference generation
        String reference = "WU" + System.currentTimeMillis() + generateChecksum(userId + amount);
        
        return new CashDepositReference(
            reference,
            generateBarcode(reference, amount),
            generateQRCode(reference, amount, userId),
            amount,
            calculateFee(amount),
            LocalDateTime.now().plusDays(10)
        );
    }
    
    @Override
    public boolean validateReference(String reference) {
        if (reference == null || (!reference.startsWith("WU") && reference.length() != 10)) {
            return false;
        }
        
        try {
            String sessionId = getSessionId();
            
            SOAPMessage soapRequest = createSOAPRequest("ValidateMTCN");
            SOAPBody body = soapRequest.getSOAPBody();
            
            SOAPElement validateRequest = body.addChildElement("ValidateRequest", "wu");
            validateRequest.addChildElement("SessionId").addTextNode(sessionId);
            validateRequest.addChildElement("MTCN").addTextNode(reference);
            
            SOAPMessage soapResponse = callSOAPService(soapRequest, "ValidateMTCN");
            
            NodeList validNodes = soapResponse.getSOAPBody().getElementsByTagName("IsValid");
            if (validNodes.getLength() > 0) {
                return "true".equalsIgnoreCase(validNodes.item(0).getTextContent());
            }
            
        } catch (Exception e) {
            log.error("Failed to validate Western Union reference: " + reference, e);
        }
        
        // Offline validation
        return reference.matches("(WU[0-9]+.*)|([0-9]{10})");
    }
    
    /**
     * Get or create WU session
     */
    private String getSessionId() throws Exception {
        SessionInfo cached = sessionCache.get("wu_session");
        
        if (cached != null && cached.isValid()) {
            return cached.getSessionId();
        }
        
        // Create new session
        SOAPMessage soapRequest = createSOAPRequest("CreateSession");
        SOAPBody body = soapRequest.getSOAPBody();
        
        SOAPElement sessionRequest = body.addChildElement("SessionRequest", "wu");
        sessionRequest.addChildElement("PartnerId").addTextNode(partnerId);
        sessionRequest.addChildElement("Timestamp").addTextNode(
            ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT)
        );
        
        // Generate signature
        String signature = generateSignature(partnerId + System.currentTimeMillis());
        sessionRequest.addChildElement("Signature").addTextNode(signature);
        
        SOAPMessage soapResponse = callSOAPService(soapRequest, "CreateSession");
        
        NodeList sessionNodes = soapResponse.getSOAPBody().getElementsByTagName("SessionId");
        if (sessionNodes.getLength() > 0) {
            String sessionId = sessionNodes.item(0).getTextContent();
            SessionInfo sessionInfo = new SessionInfo(
                sessionId,
                System.currentTimeMillis() + (30 * 60 * 1000) // 30 minutes
            );
            
            sessionCache.put("wu_session", sessionInfo);
            return sessionId;
        }
        
        throw new RuntimeException("Failed to create Western Union session");
    }
    
    /**
     * Create SOAP request
     */
    private SOAPMessage createSOAPRequest(String action) throws Exception {
        MessageFactory messageFactory = MessageFactory.newInstance();
        SOAPMessage soapMessage = messageFactory.createMessage();
        
        SOAPPart soapPart = soapMessage.getSOAPPart();
        SOAPEnvelope envelope = soapPart.getEnvelope();
        envelope.addNamespaceDeclaration("wu", "http://www.westernunion.com/schema/xrsi");
        
        // Add WS-Security header
        SOAPHeader header = envelope.getHeader();
        SOAPElement security = header.addChildElement("Security", "wsse",
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd");
        
        SOAPElement usernameToken = security.addChildElement("UsernameToken");
        usernameToken.addChildElement("Username").addTextNode(partnerId);
        usernameToken.addChildElement("Password").addTextNode(partnerKey);
        
        MimeHeaders headers = soapMessage.getMimeHeaders();
        headers.addHeader("SOAPAction", "http://www.westernunion.com/" + action);
        
        soapMessage.saveChanges();
        return soapMessage;
    }
    
    /**
     * Call SOAP service
     */
    private SOAPMessage callSOAPService(SOAPMessage request, String action) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        request.writeTo(out);
        String requestXml = new String(out.toByteArray());
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        headers.set("SOAPAction", "http://www.westernunion.com/" + action);
        
        HttpEntity<String> entity = new HttpEntity<>(requestXml, headers);
        
        ResponseEntity<String> response = restTemplate.exchange(
            apiUrl + "/services/" + action,
            HttpMethod.POST,
            entity,
            String.class
        );
        
        MessageFactory messageFactory = MessageFactory.newInstance();
        return messageFactory.createMessage(null, 
            new ByteArrayInputStream(response.getBody().getBytes()));
    }
    
    /**
     * Convert WU location to internal model
     */
    private CashDepositLocation convertToLocation(WULocation wuLocation) {
        return new CashDepositLocation(
            wuLocation.getAgentId(),
            wuLocation.getName(),
            wuLocation.getAddress(),
            wuLocation.getCity(),
            wuLocation.getState(),
            wuLocation.getPostalCode(),
            wuLocation.getLatitude(),
            wuLocation.getLongitude(),
            wuLocation.getRetailBrand(),
            wuLocation.getServices(),
            formatHours(wuLocation.getBusinessHours())
        );
    }
    
    /**
     * Format business hours
     */
    private String formatHours(Map<String, String> hours) {
        if (hours == null || hours.isEmpty()) {
            return "Hours vary";
        }
        
        return hours.entrySet().stream()
            .map(e -> e.getKey() + ": " + e.getValue())
            .collect(Collectors.joining(", "));
    }
    
    /**
     * Generate barcode data
     */
    private String generateBarcode(String reference, BigDecimal amount) {
        try {
            // Code 39 format for Western Union
            String data = String.format("*%s*%s*USD*", reference, amount.setScale(2, RoundingMode.HALF_UP));
            
            // Calculate check character
            int checksum = 0;
            for (char c : data.toCharArray()) {
                checksum += c;
            }
            checksum = checksum % 43;
            
            String checkChar = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%".substring(checksum, checksum + 1);
            
            return data + checkChar;
        } catch (Exception e) {
            log.error("Failed to generate barcode", e);
            return "BARCODE:" + reference;
        }
    }
    
    /**
     * Generate QR code data
     */
    private String generateQRCode(String reference, BigDecimal amount, String userId) {
        try {
            Map<String, Object> qrData = new HashMap<>();
            qrData.put("provider", "WesternUnion");
            qrData.put("mtcn", reference);
            qrData.put("amount", amount);
            qrData.put("currency", "USD");
            qrData.put("userId", hashUserId(userId));
            qrData.put("timestamp", System.currentTimeMillis());
            qrData.put("environment", environment);
            
            String json = objectMapper.writeValueAsString(qrData);
            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Failed to generate QR code", e);
            return "QR:" + reference;
        }
    }
    
    /**
     * Generate signature for API calls
     */
    private String generateSignature(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(partnerKey.getBytes(), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate signature", e);
        }
    }
    
    /**
     * Generate checksum for reference
     */
    private String generateChecksum(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash).substring(0, 6);
        } catch (Exception e) {
            return String.valueOf(data.hashCode() & 0xFFFFF);
        }
    }
    
    /**
     * Hash user ID for privacy
     */
    private String hashUserId(String userId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(partnerKey.getBytes(), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(userId.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash).substring(0, 16);
        } catch (Exception e) {
            return UUID.nameUUIDFromBytes(userId.getBytes()).toString().substring(0, 16);
        }
    }
    
    // Inner classes for API responses
    
    private static class SessionInfo {
        private final String sessionId;
        private final long expiryTime;
        
        public SessionInfo(String sessionId, long expiryTime) {
            this.sessionId = sessionId;
            this.expiryTime = expiryTime;
        }
        
        public String getSessionId() {
            return sessionId;
        }
        
        public boolean isValid() {
            return System.currentTimeMillis() < expiryTime - 60000; // 1 minute buffer
        }
    }
    
    private static class LocationSearchResponse {
        private List<WULocation> locations;
        private int totalResults;
        
        public List<WULocation> getLocations() { 
            return locations != null ? locations : new ArrayList<>(); 
        }
        public void setLocations(List<WULocation> locations) { this.locations = locations; }
        public int getTotalResults() { return totalResults; }
        public void setTotalResults(int totalResults) { this.totalResults = totalResults; }
    }
    
    private static class WULocation {
        private String agentId;
        private String name;
        private String address;
        private String city;
        private String state;
        private String postalCode;
        private String country;
        private double latitude;
        private double longitude;
        private String retailBrand;
        private List<String> services;
        private Map<String, String> businessHours;
        
        // Getters and setters
        public String getAgentId() { return agentId; }
        public void setAgentId(String agentId) { this.agentId = agentId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public String getPostalCode() { return postalCode; }
        public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country; }
        public double getLatitude() { return latitude; }
        public void setLatitude(double latitude) { this.latitude = latitude; }
        public double getLongitude() { return longitude; }
        public void setLongitude(double longitude) { this.longitude = longitude; }
        public String getRetailBrand() { return retailBrand; }
        public void setRetailBrand(String retailBrand) { this.retailBrand = retailBrand; }
        public List<String> getServices() { return services != null ? services : new ArrayList<>(); }
        public void setServices(List<String> services) { this.services = services; }
        public Map<String, String> getBusinessHours() { return businessHours; }
        public void setBusinessHours(Map<String, String> businessHours) { this.businessHours = businessHours; }
    }
}