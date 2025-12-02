package com.waqiti.payment.controller;

import com.waqiti.payment.integration.square.SquareWebhookHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/v1/webhooks/square")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Square Webhooks", description = "Square webhook endpoints")
public class SquareWebhookController {

    private final SquareWebhookHandler webhookHandler;
    
    @Value("${square.webhook.max-payload-size:1048576}")
    private int maxPayloadSize;
    
    @Value("${square.webhook.rate-limit.max-requests:100}")
    private int maxRequestsPerMinute;
    
    private final ConcurrentHashMap<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> requestWindows = new ConcurrentHashMap<>();
    
    @PostMapping
    @Operation(summary = "Handle Square webhook", description = "Process incoming Square webhook events")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Square-Signature") String signature,
            @RequestHeader(value = "X-Square-Hmacsha256-Signature", required = false) String hmacSignature,
            HttpServletRequest request) {
        
        String clientIp = getClientIp(request);
        log.info("SECURITY: Square webhook received from IP: {}", clientIp);
        
        try {
            if (payload.length() > maxPayloadSize) {
                log.error("SECURITY VIOLATION: Square webhook payload too large: {} bytes from IP: {}", 
                    payload.length(), clientIp);
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
            }
            
            if (!checkRateLimit(clientIp)) {
                log.error("SECURITY VIOLATION: Square webhook rate limit exceeded from IP: {}", clientIp);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
            }
            
            if (signature == null || signature.trim().isEmpty()) {
                log.error("SECURITY VIOLATION: Missing Square signature from IP: {}", clientIp);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            if (!isValidJsonPayload(payload)) {
                log.error("SECURITY VIOLATION: Invalid JSON payload from Square webhook IP: {}", clientIp);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            
            webhookHandler.handleWebhook(payload, signature, hmacSignature);
            
            log.info("AUDIT: Square webhook processed successfully from IP: {}", clientIp);
            return ResponseEntity.ok().build();
            
        } catch (SecurityException e) {
            log.error("SECURITY VIOLATION: Invalid Square webhook signature from IP: {}, error: {}", 
                clientIp, e.getMessage());
            auditSecurityViolation(clientIp, "INVALID_SIGNATURE", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            
        } catch (Exception e) {
            log.error("Error processing Square webhook from IP: {}", clientIp, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/health")
    @Operation(summary = "Square webhook health check")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Square webhook endpoint is healthy");
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
        log.error("SECURITY AUDIT: Square Webhook Violation - Type: {}, IP: {}, Details: {}, Timestamp: {}", 
            violationType, clientIp, details, LocalDateTime.now());
    }
}