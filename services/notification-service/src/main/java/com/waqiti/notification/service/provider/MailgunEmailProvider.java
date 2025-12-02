package com.waqiti.notification.service.provider;

import com.waqiti.notification.model.EmailMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.Base64Utils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Mailgun email provider implementation
 */
@Component
@Slf4j
public class MailgunEmailProvider implements EmailProvider {
    
    @Value("${mailgun.api.key:}")
    private String apiKey;
    
    @Value("${mailgun.domain:}")
    private String domain;
    
    @Value("${mailgun.base.url:https://api.mailgun.net/v3}")
    private String baseUrl;
    
    @Value("${mailgun.enabled:false}")
    private boolean enabled;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private boolean initialized = false;
    
    @PostConstruct
    public void init() {
        if (enabled && apiKey != null && !apiKey.isEmpty()) {
            try {
                initialize(Map.of(
                    "apiKey", apiKey,
                    "domain", domain,
                    "baseUrl", baseUrl
                ));
                log.info("Mailgun email provider initialized successfully");
            } catch (Exception e) {
                log.error("Failed to initialize Mailgun provider", e);
            }
        } else {
            log.info("Mailgun provider disabled or not configured");
        }
    }
    
    @Override
    public boolean send(EmailMessage message) {
        if (!enabled || !initialized) {
            log.warn("Mailgun provider not enabled or initialized");
            return false;
        }
        
        try {
            log.debug("Sending email {} via Mailgun to {}", 
                message.getMessageId(), message.getRecipientEmail());
            
            String url = baseUrl + "/" + domain + "/messages";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Authorization", "Basic " + Base64Utils.encodeToString(("api:" + apiKey).getBytes()));
            
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("from", message.getSenderEmail() != null ? message.getSenderEmail() : "noreply@" + domain);
            body.add("to", message.getRecipientEmail());
            body.add("subject", message.getSubject());
            
            if (message.getHtmlBody() != null && !message.getHtmlBody().isEmpty()) {
                body.add("html", message.getHtmlBody());
            }
            
            if (message.getTextBody() != null && !message.getTextBody().isEmpty()) {
                body.add("text", message.getTextBody());
            }
            
            // Add tracking
            body.add("o:tracking", "true");
            body.add("o:tracking-clicks", "true");
            body.add("o:tracking-opens", "true");
            
            // Add custom variables
            body.add("v:message_id", message.getMessageId());
            if (message.getCategory() != null) {
                body.add("v:category", message.getCategory());
            }
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody != null && responseBody.containsKey("id")) {
                    log.info("Email {} sent successfully via Mailgun. ID: {}", 
                        message.getMessageId(), responseBody.get("id"));
                    return true;
                }
            }
            
            log.warn("Unexpected response from Mailgun: {}", response.getStatusCode());
            return false;
            
        } catch (Exception e) {
            log.error("Failed to send email {} via Mailgun: {}", 
                message.getMessageId(), e.getMessage());
            return false;
        }
    }
    
    @Override
    public String getProviderName() {
        return "Mailgun";
    }
    
    @Override
    public boolean isHealthy() {
        if (!enabled || !initialized) {
            return false;
        }
        
        try {
            // Check domain info to verify connectivity
            String url = baseUrl + "/" + domain;
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + Base64Utils.encodeToString(("api:" + apiKey).getBytes()));
            
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            
            return response.getStatusCode() == HttpStatus.OK;
            
        } catch (Exception e) {
            log.error("Health check failed for Mailgun", e);
            return false;
        }
    }
    
    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("provider", "Mailgun");
        status.put("enabled", enabled);
        status.put("initialized", initialized);
        status.put("domain", domain);
        status.put("healthy", isHealthy());
        
        if (initialized) {
            try {
                // Get domain stats
                String statsUrl = baseUrl + "/" + domain + "/stats/total";
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Basic " + Base64Utils.encodeToString(("api:" + apiKey).getBytes()));
                
                HttpEntity<Void> request = new HttpEntity<>(headers);
                ResponseEntity<Map> response = restTemplate.exchange(statsUrl, HttpMethod.GET, request, Map.class);
                
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    Map<String, Object> stats = response.getBody();
                    status.put("stats", stats);
                }
                
            } catch (Exception e) {
                log.warn("Failed to get Mailgun stats: {}", e.getMessage());
            }
        }
        
        return status;
    }
    
    @Override
    public void initialize(Map<String, Object> config) {
        try {
            if (!enabled) {
                log.info("Mailgun provider disabled, skipping initialization");
                return;
            }
            
            String configApiKey = (String) config.get("apiKey");
            String configDomain = (String) config.get("domain");
            String configBaseUrl = (String) config.get("baseUrl");
            
            if (configApiKey == null || configApiKey.isEmpty()) {
                throw new IllegalArgumentException("Mailgun API key is required");
            }
            
            if (configDomain == null || configDomain.isEmpty()) {
                throw new IllegalArgumentException("Mailgun domain is required");
            }
            
            this.apiKey = configApiKey;
            this.domain = configDomain;
            this.baseUrl = configBaseUrl != null ? configBaseUrl : this.baseUrl;
            
            // Test the configuration
            if (isHealthy()) {
                initialized = true;
                log.info("Mailgun provider initialized for domain: {}", domain);
            } else {
                throw new RuntimeException("Mailgun health check failed");
            }
            
        } catch (Exception e) {
            log.error("Failed to initialize Mailgun provider", e);
            initialized = false;
            throw new RuntimeException("Mailgun initialization failed", e);
        }
    }
    
    @Override
    public boolean supportsBulkSending() {
        return true;
    }
    
    @Override
    public int sendBulk(java.util.List<EmailMessage> messages) {
        if (!enabled || !initialized) {
            log.warn("Mailgun provider not enabled for bulk sending");
            return 0;
        }
        
        log.info("Sending bulk emails via Mailgun: {} messages", messages.size());
        
        int successCount = 0;
        
        // Mailgun doesn't have a native bulk API like SendGrid
        // Send individually but with batch optimization
        for (EmailMessage message : messages) {
            if (send(message)) {
                successCount++;
            }
            
            // Small delay to respect rate limits
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        log.info("Mailgun bulk send completed: {}/{} successful", successCount, messages.size());
        return successCount;
    }
}