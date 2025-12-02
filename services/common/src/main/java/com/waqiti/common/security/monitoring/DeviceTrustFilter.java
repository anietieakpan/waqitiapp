package com.waqiti.common.security.monitoring;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Device trust and fingerprinting filter for enhanced security
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceTrustFilter implements Filter {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    private static final String DEVICE_TRUST_PREFIX = "device:trust:";
    private static final String SUSPICIOUS_DEVICE_PREFIX = "device:suspicious:";
    private static final Duration DEVICE_TRUST_DURATION = Duration.ofDays(30);
    private static final Duration SUSPICIOUS_DEVICE_BLOCK_DURATION = Duration.ofHours(24);
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        try {
            String deviceFingerprint = generateDeviceFingerprint(httpRequest);
            String clientIp = getClientIpAddress(httpRequest);
            
            // Check if device is suspicious
            if (isSuspiciousDevice(deviceFingerprint, clientIp)) {
                log.warn("Blocking request from suspicious device: {} from IP: {}", deviceFingerprint, clientIp);
                sendSuspiciousDeviceResponse(httpResponse);
                return;
            }
            
            // Add device fingerprint to request for downstream services
            httpRequest.setAttribute("device_fingerprint", deviceFingerprint);
            httpRequest.setAttribute("device_trust_level", getDeviceTrustLevel(deviceFingerprint));
            
            chain.doFilter(request, response);
            
            // Update device trust after successful request
            updateDeviceTrust(deviceFingerprint, httpRequest);
            
        } catch (Exception e) {
            log.error("Error in device trust filter", e);
            // Allow request to proceed on error
            chain.doFilter(request, response);
        }
    }
    
    private String generateDeviceFingerprint(HttpServletRequest request) {
        StringBuilder fingerprint = new StringBuilder();
        
        // User Agent
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null) {
            fingerprint.append("ua:").append(userAgent).append("|");
        }
        
        // Accept headers
        String accept = request.getHeader("Accept");
        if (accept != null) {
            fingerprint.append("accept:").append(accept).append("|");
        }
        
        String acceptLanguage = request.getHeader("Accept-Language");
        if (acceptLanguage != null) {
            fingerprint.append("lang:").append(acceptLanguage).append("|");
        }
        
        String acceptEncoding = request.getHeader("Accept-Encoding");
        if (acceptEncoding != null) {
            fingerprint.append("enc:").append(acceptEncoding).append("|");
        }
        
        // Connection type
        String connection = request.getHeader("Connection");
        if (connection != null) {
            fingerprint.append("conn:").append(connection).append("|");
        }
        
        // DNT header
        String dnt = request.getHeader("DNT");
        if (dnt != null) {
            fingerprint.append("dnt:").append(dnt).append("|");
        }
        
        // Generate hash of the fingerprint
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(fingerprint.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16); // First 16 chars
        } catch (Exception e) {
            log.warn("Failed to generate device fingerprint hash", e);
            return "unknown";
        }
    }
    
    private boolean isSuspiciousDevice(String deviceFingerprint, String clientIp) {
        try {
            String suspiciousKey = SUSPICIOUS_DEVICE_PREFIX + deviceFingerprint;
            return Boolean.TRUE.equals(redisTemplate.hasKey(suspiciousKey));
        } catch (Exception e) {
            log.warn("Failed to check suspicious device status", e);
            return false; // Fail open
        }
    }
    
    private String getDeviceTrustLevel(String deviceFingerprint) {
        try {
            String trustKey = DEVICE_TRUST_PREFIX + deviceFingerprint;
            String trustData = redisTemplate.opsForValue().get(trustKey);
            
            if (trustData == null) {
                return "UNKNOWN";
            }
            
            // Parse trust data (format: "level:timestamp:count")
            String[] parts = trustData.split(":");
            if (parts.length >= 1) {
                return parts[0];
            }
            
            return "UNKNOWN";
        } catch (Exception e) {
            log.warn("Failed to get device trust level", e);
            return "UNKNOWN";
        }
    }
    
    private void updateDeviceTrust(String deviceFingerprint, HttpServletRequest request) {
        try {
            String trustKey = DEVICE_TRUST_PREFIX + deviceFingerprint;
            String currentTrust = redisTemplate.opsForValue().get(trustKey);
            
            if (currentTrust == null) {
                // New device
                String trustData = "NEW:" + System.currentTimeMillis() + ":1";
                redisTemplate.opsForValue().set(trustKey, trustData, DEVICE_TRUST_DURATION);
                log.debug("New device registered: {}", deviceFingerprint);
            } else {
                // Update existing device trust
                String[] parts = currentTrust.split(":");
                if (parts.length >= 3) {
                    String level = parts[0];
                    int count = Integer.parseInt(parts[2]) + 1;
                    
                    // Upgrade trust level based on usage
                    if (count >= 10 && "NEW".equals(level)) {
                        level = "TRUSTED";
                    } else if (count >= 50 && "TRUSTED".equals(level)) {
                        level = "HIGHLY_TRUSTED";
                    }
                    
                    String updatedTrust = level + ":" + System.currentTimeMillis() + ":" + count;
                    redisTemplate.opsForValue().set(trustKey, updatedTrust, DEVICE_TRUST_DURATION);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to update device trust", e);
        }
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    private void sendSuspiciousDeviceResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");
        
        String jsonResponse = """
            {
                "error": "device_blocked",
                "message": "Access denied. This device has been flagged as suspicious.",
                "status": 403,
                "code": "SUSPICIOUS_DEVICE"
            }
            """;
        
        response.getWriter().write(jsonResponse);
    }
    
    /**
     * Mark a device as suspicious (to be called by security monitoring services)
     */
    public void markDeviceAsSuspicious(String deviceFingerprint, String reason) {
        try {
            String suspiciousKey = SUSPICIOUS_DEVICE_PREFIX + deviceFingerprint;
            String suspiciousData = reason + ":" + System.currentTimeMillis();
            redisTemplate.opsForValue().set(suspiciousKey, suspiciousData, SUSPICIOUS_DEVICE_BLOCK_DURATION);
            log.warn("Device marked as suspicious: {} - Reason: {}", deviceFingerprint, reason);
        } catch (Exception e) {
            log.error("Failed to mark device as suspicious", e);
        }
    }
}