/**
 * Telecom Gateway Service
 * Integration with telecom providers for SMS and USSD services
 */
package com.waqiti.smsbanking.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelecomGatewayService {
    
    private final RestTemplate restTemplate;
    
    @Value("${telecom.gateway.sms.endpoint:http://localhost:8080/sms}")
    private String smsGatewayEndpoint;
    
    @Value("${telecom.gateway.ussd.endpoint:http://localhost:8080/ussd}")
    private String ussdGatewayEndpoint;
    
    @Value("${telecom.gateway.api.key:demo-key}")
    private String apiKey;
    
    @Value("${telecom.gateway.sender.id:WAQITI}")
    private String senderId;
    
    @Value("${telecom.gateway.ussd.code:*123#}")
    private String ussdCode;
    
    public SmsGatewayResponse sendSms(String phoneNumber, String message) {
        try {
            log.info("Sending SMS to {}: {}", phoneNumber, message);
            
            Map<String, Object> request = new HashMap<>();
            request.put("recipient", phoneNumber);
            request.put("message", message);
            request.put("sender", senderId);
            request.put("reference", generateReference());
            request.put("timestamp", LocalDateTime.now().toString());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("X-Gateway-Version", "1.0");
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                smsGatewayEndpoint + "/send",
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> responseBody = response.getBody();
                return SmsGatewayResponse.builder()
                    .success(true)
                    .messageId((String) responseBody.get("messageId"))
                    .status((String) responseBody.get("status"))
                    .reference((String) request.get("reference"))
                    .build();
            } else {
                log.error("SMS gateway returned error status: {}", response.getStatusCode());
                return createFailedSmsResponse("Gateway error: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Error sending SMS to {}: {}", phoneNumber, e.getMessage(), e);
            return createFailedSmsResponse("Network error: " + e.getMessage());
        }
    }
    
    public SmsGatewayResponse sendBulkSms(String[] phoneNumbers, String message) {
        try {
            log.info("Sending bulk SMS to {} recipients", phoneNumbers.length);
            
            Map<String, Object> request = new HashMap<>();
            request.put("recipients", phoneNumbers);
            request.put("message", message);
            request.put("sender", senderId);
            request.put("reference", generateReference());
            request.put("timestamp", LocalDateTime.now().toString());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                smsGatewayEndpoint + "/bulk",
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> responseBody = response.getBody();
                return SmsGatewayResponse.builder()
                    .success(true)
                    .messageId((String) responseBody.get("batchId"))
                    .status("BATCH_SUBMITTED")
                    .reference((String) request.get("reference"))
                    .build();
            } else {
                return createFailedSmsResponse("Bulk SMS gateway error");
            }
            
        } catch (Exception e) {
            log.error("Error sending bulk SMS: {}", e.getMessage(), e);
            return createFailedSmsResponse("Bulk SMS network error");
        }
    }
    
    public UssdGatewayResponse sendUssdResponse(String sessionId, String phoneNumber, String message, boolean endSession) {
        try {
            log.info("Sending USSD response to session {}: {}", sessionId, message);
            
            Map<String, Object> request = new HashMap<>();
            request.put("sessionId", sessionId);
            request.put("phoneNumber", phoneNumber);
            request.put("message", message);
            request.put("action", endSession ? "END" : "CONTINUE");
            request.put("ussdCode", ussdCode);
            request.put("timestamp", LocalDateTime.now().toString());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                ussdGatewayEndpoint + "/response",
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return UssdGatewayResponse.builder()
                    .success(true)
                    .sessionId(sessionId)
                    .status("SENT")
                    .build();
            } else {
                log.error("USSD gateway returned error status: {}", response.getStatusCode());
                return createFailedUssdResponse("USSD gateway error");
            }
            
        } catch (Exception e) {
            log.error("Error sending USSD response for session {}: {}", sessionId, e.getMessage(), e);
            return createFailedUssdResponse("USSD network error");
        }
    }
    
    public boolean testConnection() {
        try {
            // Test SMS gateway
            ResponseEntity<String> smsTest = restTemplate.exchange(
                smsGatewayEndpoint + "/health",
                HttpMethod.GET,
                new HttpEntity<>(createAuthHeaders()),
                String.class
            );
            
            // Test USSD gateway
            ResponseEntity<String> ussdTest = restTemplate.exchange(
                ussdGatewayEndpoint + "/health",
                HttpMethod.GET,
                new HttpEntity<>(createAuthHeaders()),
                String.class
            );
            
            boolean smsOk = smsTest.getStatusCode() == HttpStatus.OK;
            boolean ussdOk = ussdTest.getStatusCode() == HttpStatus.OK;
            
            log.info("Gateway connection test - SMS: {}, USSD: {}", smsOk, ussdOk);
            
            return smsOk && ussdOk;
            
        } catch (Exception e) {
            log.error("Gateway connection test failed: {}", e.getMessage());
            return false;
        }
    }
    
    public GatewayStatus getGatewayStatus() {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("timestamp", LocalDateTime.now().toString());
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, createAuthHeaders());
            
            ResponseEntity<Map> response = restTemplate.exchange(
                smsGatewayEndpoint + "/status",
                HttpMethod.GET,
                entity,
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> responseBody = response.getBody();
                return GatewayStatus.builder()
                    .smsGatewayUp((Boolean) responseBody.get("smsGatewayUp"))
                    .ussdGatewayUp((Boolean) responseBody.get("ussdGatewayUp"))
                    .messageQueueDepth((Integer) responseBody.get("queueDepth"))
                    .lastHeartbeat(LocalDateTime.parse((String) responseBody.get("lastHeartbeat")))
                    .build();
            }
            
        } catch (Exception e) {
            log.error("Error getting gateway status: {}", e.getMessage());
        }
        
        return GatewayStatus.builder()
            .smsGatewayUp(false)
            .ussdGatewayUp(false)
            .messageQueueDepth(0)
            .lastHeartbeat(LocalDateTime.now())
            .build();
    }
    
    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
    
    private String generateReference() {
        return "WQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    private SmsGatewayResponse createFailedSmsResponse(String error) {
        return SmsGatewayResponse.builder()
            .success(false)
            .status("FAILED")
            .errorMessage(error)
            .build();
    }
    
    private UssdGatewayResponse createFailedUssdResponse(String error) {
        return UssdGatewayResponse.builder()
            .success(false)
            .status("FAILED")
            .errorMessage(error)
            .build();
    }
    
    // Response DTOs
    public static class SmsGatewayResponse {
        private boolean success;
        private String messageId;
        private String status;
        private String reference;
        private String errorMessage;
        
        public static SmsGatewayResponseBuilder builder() {
            return new SmsGatewayResponseBuilder();
        }
        
        public static class SmsGatewayResponseBuilder {
            private boolean success;
            private String messageId;
            private String status;
            private String reference;
            private String errorMessage;
            
            public SmsGatewayResponseBuilder success(boolean success) {
                this.success = success;
                return this;
            }
            
            public SmsGatewayResponseBuilder messageId(String messageId) {
                this.messageId = messageId;
                return this;
            }
            
            public SmsGatewayResponseBuilder status(String status) {
                this.status = status;
                return this;
            }
            
            public SmsGatewayResponseBuilder reference(String reference) {
                this.reference = reference;
                return this;
            }
            
            public SmsGatewayResponseBuilder errorMessage(String errorMessage) {
                this.errorMessage = errorMessage;
                return this;
            }
            
            public SmsGatewayResponse build() {
                SmsGatewayResponse response = new SmsGatewayResponse();
                response.success = this.success;
                response.messageId = this.messageId;
                response.status = this.status;
                response.reference = this.reference;
                response.errorMessage = this.errorMessage;
                return response;
            }
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessageId() { return messageId; }
        public String getStatus() { return status; }
        public String getReference() { return reference; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    public static class UssdGatewayResponse {
        private boolean success;
        private String sessionId;
        private String status;
        private String errorMessage;
        
        public static UssdGatewayResponseBuilder builder() {
            return new UssdGatewayResponseBuilder();
        }
        
        public static class UssdGatewayResponseBuilder {
            private boolean success;
            private String sessionId;
            private String status;
            private String errorMessage;
            
            public UssdGatewayResponseBuilder success(boolean success) {
                this.success = success;
                return this;
            }
            
            public UssdGatewayResponseBuilder sessionId(String sessionId) {
                this.sessionId = sessionId;
                return this;
            }
            
            public UssdGatewayResponseBuilder status(String status) {
                this.status = status;
                return this;
            }
            
            public UssdGatewayResponseBuilder errorMessage(String errorMessage) {
                this.errorMessage = errorMessage;
                return this;
            }
            
            public UssdGatewayResponse build() {
                UssdGatewayResponse response = new UssdGatewayResponse();
                response.success = this.success;
                response.sessionId = this.sessionId;
                response.status = this.status;
                response.errorMessage = this.errorMessage;
                return response;
            }
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getSessionId() { return sessionId; }
        public String getStatus() { return status; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    public static class GatewayStatus {
        private boolean smsGatewayUp;
        private boolean ussdGatewayUp;
        private int messageQueueDepth;
        private LocalDateTime lastHeartbeat;
        
        public static GatewayStatusBuilder builder() {
            return new GatewayStatusBuilder();
        }
        
        public static class GatewayStatusBuilder {
            private boolean smsGatewayUp;
            private boolean ussdGatewayUp;
            private int messageQueueDepth;
            private LocalDateTime lastHeartbeat;
            
            public GatewayStatusBuilder smsGatewayUp(boolean smsGatewayUp) {
                this.smsGatewayUp = smsGatewayUp;
                return this;
            }
            
            public GatewayStatusBuilder ussdGatewayUp(boolean ussdGatewayUp) {
                this.ussdGatewayUp = ussdGatewayUp;
                return this;
            }
            
            public GatewayStatusBuilder messageQueueDepth(int messageQueueDepth) {
                this.messageQueueDepth = messageQueueDepth;
                return this;
            }
            
            public GatewayStatusBuilder lastHeartbeat(LocalDateTime lastHeartbeat) {
                this.lastHeartbeat = lastHeartbeat;
                return this;
            }
            
            public GatewayStatus build() {
                GatewayStatus status = new GatewayStatus();
                status.smsGatewayUp = this.smsGatewayUp;
                status.ussdGatewayUp = this.ussdGatewayUp;
                status.messageQueueDepth = this.messageQueueDepth;
                status.lastHeartbeat = this.lastHeartbeat;
                return status;
            }
        }
        
        // Getters
        public boolean isSmsGatewayUp() { return smsGatewayUp; }
        public boolean isUssdGatewayUp() { return ussdGatewayUp; }
        public int getMessageQueueDepth() { return messageQueueDepth; }
        public LocalDateTime getLastHeartbeat() { return lastHeartbeat; }
    }
}