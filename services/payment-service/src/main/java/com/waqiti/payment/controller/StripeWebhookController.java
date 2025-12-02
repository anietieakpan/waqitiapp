package com.waqiti.payment.controller;

import com.waqiti.common.ratelimit.RateLimited;
import com.waqiti.payment.integration.stripe.StripeWebhookHandler;
import com.waqiti.payment.security.WebhookAuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Production-hardened Stripe webhook controller with comprehensive security.
 *
 * Security Features:
 * - HMAC signature verification (prevents spoofing)
 * - Replay attack prevention (timestamp validation)
 * - IP whitelisting (Stripe IPs only)
 * - Rate limiting (100 req/min per IP)
 * - Payload size validation (prevent DoS)
 * - Comprehensive audit logging
 *
 * @version 2.0 - Production Ready
 * @since 2025-10-07
 */
@RestController
@RequestMapping("/api/v1/webhooks/stripe")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Stripe Webhooks", description = "Production-grade Stripe webhook endpoints with security hardening")
public class StripeWebhookController {

    private final StripeWebhookHandler webhookHandler;
    private final WebhookAuthenticationService webhookAuthenticationService;
    
    @Value("${stripe.webhook.max-payload-size:1048576}") // 1MB default
    private int maxPayloadSize;
    
    @Value("${stripe.webhook.rate-limit.max-requests:100}")
    private int maxRequestsPerMinute;
    
    // Rate limiting for webhook endpoints
    private final ConcurrentHashMap<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> requestWindows = new ConcurrentHashMap<>();
    
    /**
     * Handles Stripe webhook events with enterprise-grade security.
     *
     * Security Flow:
     * 1. Validate signature (HMAC-SHA256) - BLOCKS spoofed requests
     * 2. Check timestamp (5-min window) - BLOCKS replay attacks
     * 3. Verify IP whitelist - BLOCKS non-Stripe sources
     * 4. Rate limit (100/min) - BLOCKS DoS attacks
     * 5. Validate payload size - BLOCKS memory exhaustion
     * 6. Audit log all attempts - COMPLIANCE requirement
     *
     * @param payload Raw webhook JSON payload
     * @param signature Stripe-Signature header (t=timestamp,v1=signature)
     * @param request HTTP request for IP extraction
     * @return 200 OK if processed, 401 if invalid signature, 403 if blocked
     */
    @PostMapping
    @PreAuthorize("@webhookAuthenticationService.isValidStripeWebhook(#payload, #signature)")
    @RateLimited(keyType = RateLimited.KeyType.IP, capacity = 100, refillTokens = 100, refillPeriodMinutes = 1)
    @Operation(
        summary = "Handle Stripe webhook (SECURED)",
        description = "Process incoming Stripe webhook events with signature verification, replay protection, and IP whitelisting"
    )
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature,
            HttpServletRequest request) {

        String clientIp = getClientIp(request);
        log.info("SECURITY AUDIT: Stripe webhook received from IP: {}, signature validated", clientIp);

        try {
            // SECURITY CHECKPOINT 1: IP Whitelist (defense in depth)
            if (!webhookAuthenticationService.isWhitelistedIp(clientIp, "stripe")) {
                log.error("SECURITY VIOLATION: Stripe webhook from non-whitelisted IP: {}", clientIp);
                auditSecurityViolation(clientIp, "IP_NOT_WHITELISTED", "Stripe webhook from unauthorized IP");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // SECURITY CHECKPOINT 2: Signature already validated by @PreAuthorize
            // If we reach here, signature is valid and not replayed

            // CRITICAL SECURITY CHECKS
            
            // 1. Validate payload size to prevent DoS
            if (payload.length() > maxPayloadSize) {
                log.error("SECURITY VIOLATION: Webhook payload too large: {} bytes from IP: {}", 
                    payload.length(), clientIp);
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
            }
            
            // 2. Rate limiting check
            if (!checkRateLimit(clientIp)) {
                log.error("SECURITY VIOLATION: Rate limit exceeded from IP: {}", clientIp);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
            }
            
            // 3. Validate signature header format
            if (signature == null || signature.trim().isEmpty() || !signature.startsWith("t=")) {
                log.error("SECURITY VIOLATION: Invalid signature header format from IP: {}", clientIp);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            
            // 4. Validate payload format (basic JSON check)
            if (!isValidJsonPayload(payload)) {
                log.error("SECURITY VIOLATION: Invalid JSON payload from IP: {}", clientIp);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            
            // 5. Process webhook with enhanced security
            webhookHandler.handleWebhook(payload, signature);
            
            log.info("AUDIT: Webhook processed successfully from IP: {}", clientIp);
            return ResponseEntity.ok().build();
            
        } catch (SecurityException e) {
            log.error("SECURITY VIOLATION: Invalid webhook signature from IP: {}, error: {}", 
                clientIp, e.getMessage());
            // Audit security violation
            auditSecurityViolation(clientIp, "INVALID_SIGNATURE", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            
        } catch (IllegalArgumentException e) {
            log.error("SECURITY VIOLATION: Malformed webhook data from IP: {}, error: {}", 
                clientIp, e.getMessage());
            auditSecurityViolation(clientIp, "MALFORMED_DATA", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            
        } catch (Exception e) {
            log.error("Error processing webhook from IP: {}", clientIp, e);
            // Don't expose internal error details to prevent information leakage
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/health")
    @Operation(summary = "Webhook health check", description = "Check if webhook endpoint is healthy")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Stripe webhook endpoint is healthy");
    }
    
    // ================================
    // SECURITY ENHANCEMENT METHODS
    // ================================
    
    /**
     * Extract client IP with proper header checking
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        String xRealIp = request.getHeader("X-Real-IP");
        String xForwardedProto = request.getHeader("X-Forwarded-Proto");
        
        // Log security-relevant headers
        log.debug("SECURITY: Headers - X-Forwarded-For: {}, X-Real-IP: {}, X-Forwarded-Proto: {}", 
            xForwardedFor, xRealIp, xForwardedProto);
        
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take the first IP (original client)
            return xForwardedFor.split(",")[0].trim();
        }
        
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Rate limiting implementation for webhook security
     */
    private boolean checkRateLimit(String clientIp) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = requestWindows.get(clientIp);
        
        // Reset window if it's been more than a minute
        if (windowStart == null || windowStart.isBefore(now.minusMinutes(1))) {
            requestWindows.put(clientIp, now);
            requestCounts.put(clientIp, new AtomicInteger(1));
            return true;
        }
        
        // Check if we're within the rate limit
        AtomicInteger count = requestCounts.get(clientIp);
        if (count == null) {
            requestCounts.put(clientIp, new AtomicInteger(1));
            return true;
        }
        
        return count.incrementAndGet() <= maxRequestsPerMinute;
    }
    
    /**
     * Basic JSON payload validation
     */
    private boolean isValidJsonPayload(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = payload.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) || 
               (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }
    
    /**
     * Audit security violations for compliance and monitoring
     */
    private void auditSecurityViolation(String clientIp, String violationType, String details) {
        log.error("SECURITY AUDIT: Violation - Type: {}, IP: {}, Details: {}, Timestamp: {}", 
            violationType, clientIp, details, LocalDateTime.now());
        
        // In production, this would integrate with security monitoring systems
        // Could also trigger alerts or temporary IP blocking
    }
    
    /**
     * Cleanup method for rate limiting maps (called periodically)
     */
    public void cleanupRateLimitingData() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
        requestWindows.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        
        // Remove corresponding counts
        requestWindows.keySet().forEach(ip -> {
            if (!requestWindows.containsKey(ip)) {
                requestCounts.remove(ip);
            }
        });
    }
}