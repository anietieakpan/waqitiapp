package com.waqiti.payment.controller;

import com.waqiti.payment.integration.adyen.AdyenWebhookHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/v1/webhooks/adyen")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Adyen Webhooks", description = "Adyen webhook endpoints")
public class AdyenWebhookController {

    private final AdyenWebhookHandler webhookHandler;
    
    @Value("${adyen.webhook.max-payload-size:1048576}")
    private int maxPayloadSize;
    
    @Value("${adyen.webhook.rate-limit.max-requests:100}")
    private int maxRequestsPerMinute;
    
    private final ConcurrentHashMap<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> requestWindows = new ConcurrentHashMap<>();
    
    @PostMapping
    @PreAuthorize("permitAll()") // SECURITY: Webhook authentication via HMAC signature validation, not JWT
    @Operation(summary = "Handle Adyen webhook", description = "Process incoming Adyen webhook notifications")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Adyen-HMACSignature", required = true) String signature,
            HttpServletRequest request) {
        
        String clientIp = getClientIp(request);
        log.info("SECURITY: Adyen webhook received from IP: {}", clientIp);
        
        try {
            if (payload.length() > maxPayloadSize) {
                log.error("SECURITY VIOLATION: Adyen webhook payload too large: {} bytes from IP: {}", 
                    payload.length(), clientIp);
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body("[not accepted]");
            }
            
            if (!checkRateLimit(clientIp)) {
                log.error("SECURITY VIOLATION: Adyen webhook rate limit exceeded from IP: {}", clientIp);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("[not accepted]");
            }
            
            if (!isValidJsonPayload(payload)) {
                log.error("SECURITY VIOLATION: Invalid JSON payload from Adyen webhook IP: {}", clientIp);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("[not accepted]");
            }
            
            boolean processed = webhookHandler.handleWebhook(payload, signature);
            
            if (processed) {
                log.info("AUDIT: Adyen webhook processed successfully from IP: {}", clientIp);
                return ResponseEntity.ok("[accepted]");
            } else {
                log.warn("Adyen webhook not processed - possibly invalid signature from IP: {}", clientIp);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("[not accepted]");
            }
            
        } catch (SecurityException e) {
            log.error("SECURITY VIOLATION: Invalid Adyen webhook signature from IP: {}, error: {}", 
                clientIp, e.getMessage());
            auditSecurityViolation(clientIp, "INVALID_SIGNATURE", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("[not accepted]");
            
        } catch (Exception e) {
            log.error("Error processing Adyen webhook from IP: {}", clientIp, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("[not accepted]");
        }
    }
    
    @GetMapping("/health")
    @Operation(summary = "Adyen webhook health check")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Adyen webhook endpoint is healthy");
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        String xRealIp = request.getHeader("X-Real-IP");
        
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    private boolean checkRateLimit(String clientIp) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = requestWindows.get(clientIp);
        
        if (windowStart == null || windowStart.isBefore(now.minusMinutes(1))) {
            requestWindows.put(clientIp, now);
            requestCounts.put(clientIp, new AtomicInteger(1));
            return true;
        }
        
        AtomicInteger count = requestCounts.get(clientIp);
        if (count == null) {
            requestCounts.put(clientIp, new AtomicInteger(1));
            return true;
        }
        
        return count.incrementAndGet() <= maxRequestsPerMinute;
    }
    
    private boolean isValidJsonPayload(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = payload.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) || 
               (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }
    
    private void auditSecurityViolation(String clientIp, String violationType, String details) {
        log.error("SECURITY AUDIT: Adyen Webhook Violation - Type: {}, IP: {}, Details: {}, Timestamp: {}", 
            violationType, clientIp, details, LocalDateTime.now());
    }
}