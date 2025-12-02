package com.waqiti.common.fraud.notification;

import com.waqiti.common.fraud.model.FraudAlert;
import com.waqiti.common.fraud.model.AlertLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter service to integrate fraud alerts with the notification service
 * Uses REST API calls to communicate with the notification microservice
 */
@Slf4j
@Service
public class FraudNotificationAdapter {

    private final RestTemplate restTemplate;
    
    @Value("${notification.service.url:http://notification-service:8080}")
    private String notificationServiceUrl;
    
    @Value("${notification.fraud.template.alert:FRAUD_ALERT}")
    private String fraudAlertTemplate;
    
    @Value("${notification.fraud.template.verification:TRANSACTION_VERIFICATION}")
    private String verificationTemplate;
    
    @Value("${notification.fraud.template.security:SECURITY_ALERT}")
    private String securityAlertTemplate;

    public FraudNotificationAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Send fraud alert notification
     */
    public void sendFraudAlert(FraudAlert alert) {
        try {
            Map<String, Object> request = buildFraudAlertRequest(alert);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            String url = notificationServiceUrl + "/api/v1/notifications/send";
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Fraud alert notification sent successfully for alert: {}", alert.getId());
            } else {
                log.error("Failed to send fraud alert notification. Status: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error sending fraud alert notification for alert: {}", alert.getId(), e);
        }
    }

    /**
     * Send transaction verification notification
     */
    public void sendTransactionVerification(String userId, String transactionId, 
                                           BigDecimal amount, String currency,
                                           String merchantName, String verificationCode) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("userId", userId);
            request.put("templateCode", verificationTemplate);
            request.put("types", List.of("EMAIL", "SMS"));
            
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("transactionId", transactionId);
            parameters.put("amount", amount.toString());
            parameters.put("currency", currency);
            parameters.put("merchantName", merchantName);
            parameters.put("verificationCode", verificationCode);
            parameters.put("timestamp", LocalDateTime.now().toString());
            
            request.put("parameters", parameters);
            request.put("priority", "HIGH");
            
            sendNotification(request);
            log.info("Transaction verification sent for transaction: {}", transactionId);
            
        } catch (Exception e) {
            log.error("Error sending transaction verification for transaction: {}", transactionId, e);
        }
    }

    /**
     * Send security notification
     */
    public void sendSecurityNotification(String userId, String eventType, 
                                        String eventDescription, String ipAddress,
                                        String location, boolean urgent) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("userId", userId);
            request.put("templateCode", securityAlertTemplate);
            request.put("types", urgent ? List.of("EMAIL", "SMS", "PUSH") : List.of("EMAIL"));
            
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("eventType", eventType);
            parameters.put("eventDescription", eventDescription);
            parameters.put("ipAddress", ipAddress);
            parameters.put("location", location);
            parameters.put("timestamp", LocalDateTime.now().toString());
            parameters.put("urgent", urgent);
            
            request.put("parameters", parameters);
            request.put("priority", urgent ? "URGENT" : "NORMAL");
            
            sendNotification(request);
            log.info("Security notification sent for user: {}, event: {}", userId, eventType);
            
        } catch (Exception e) {
            log.error("Error sending security notification for user: {}", userId, e);
        }
    }

    /**
     * Build fraud alert notification request
     */
    private Map<String, Object> buildFraudAlertRequest(FraudAlert alert) {
        Map<String, Object> request = new HashMap<>();
        request.put("userId", alert.getUserId());
        request.put("templateCode", fraudAlertTemplate);
        
        // Determine notification types based on severity
        List<String> notificationTypes = determineNotificationTypes(alert.getSeverity());
        request.put("types", notificationTypes);
        
        // Build template parameters
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("alertId", alert.getId());
        parameters.put("alertType", alert.getAlertType().toString());
        parameters.put("severity", alert.getSeverity().toString());
        parameters.put("title", alert.getTitle());
        parameters.put("description", alert.getDescription());
        parameters.put("transactionId", alert.getTransactionId());
        parameters.put("amount", alert.getAmount() != null ? alert.getAmount().toString() : "N/A");
        parameters.put("currency", alert.getCurrency() != null ? alert.getCurrency() : "");
        parameters.put("fraudScore", alert.getFraudScore() != null ? alert.getFraudScore().toString() : "N/A");
        parameters.put("riskLevel", alert.getRiskLevel() != null ? alert.getRiskLevel().toString() : "");
        parameters.put("detectionMethod", alert.getDetectionMethod());
        parameters.put("timestamp", alert.getCreatedAt().toString());
        parameters.put("actionRequired", alert.getSeverity() == AlertLevel.CRITICAL);
        
        request.put("parameters", parameters);
        request.put("referenceId", alert.getId());
        request.put("priority", mapAlertPriority(alert.getSeverity()));
        
        return request;
    }

    /**
     * Determine notification types based on alert severity
     */
    private List<String> determineNotificationTypes(AlertLevel severity) {
        return switch (severity) {
            case CRITICAL -> List.of("EMAIL", "SMS", "PUSH", "IN_APP");
            case HIGH -> List.of("EMAIL", "SMS", "PUSH");
            case MEDIUM -> List.of("EMAIL", "PUSH");
            case LOW -> List.of("EMAIL", "IN_APP");
            case INFO -> List.of("IN_APP");
        };
    }

    /**
     * Map alert severity to notification priority
     */
    private String mapAlertPriority(AlertLevel severity) {
        return switch (severity) {
            case CRITICAL, HIGH -> "URGENT";
            case MEDIUM -> "HIGH";
            case LOW -> "NORMAL";
            case INFO -> "LOW";
        };
    }

    /**
     * Send notification to notification service
     */
    private void sendNotification(Map<String, Object> request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        
        String url = notificationServiceUrl + "/api/v1/notifications/send";
        restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
    }

    /**
     * Send batch fraud alerts
     */
    public void sendBatchFraudAlerts(List<FraudAlert> alerts) {
        if (alerts == null || alerts.isEmpty()) {
            return;
        }
        
        try {
            List<Map<String, Object>> batchRequests = alerts.stream()
                .map(this::buildFraudAlertRequest)
                .toList();
            
            Map<String, Object> batchRequest = new HashMap<>();
            batchRequest.put("notifications", batchRequests);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(batchRequest, headers);
            
            String url = notificationServiceUrl + "/api/v1/notifications/batch";
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Batch fraud alerts sent successfully. Count: {}", alerts.size());
            } else {
                log.error("Failed to send batch fraud alerts. Status: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error sending batch fraud alerts", e);
        }
    }

    /**
     * Check if notification service is available
     */
    public boolean isNotificationServiceAvailable() {
        try {
            String url = notificationServiceUrl + "/actuator/health";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Notification service health check failed", e);
            return false;
        }
    }
}