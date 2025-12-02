package com.waqiti.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Device Fingerprinting Filter
 * Generates and validates device fingerprints for enhanced security
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeviceFingerprintingFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, Object> redisTemplate;
    
    @Value("${security.device-fingerprinting.enabled:true}")
    private boolean enabled;
    
    @Value("${security.device-fingerprinting.max-devices:5}")
    private int maxDevicesPerUser;
    
    @Value("${security.device-fingerprinting.trust-duration-days:30}")
    private int trustDurationDays;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
        
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            DeviceFingerprint fingerprint = generateFingerprint(request);
            boolean isTrusted = validateDevice(userId, fingerprint, request);
            
            // Add device info to request context
            request.setAttribute("device.fingerprint", fingerprint.getHash());
            request.setAttribute("device.trusted", isTrusted);
            
            if (!isTrusted && isHighRiskOperation(request)) {
                handleUntrustedDevice(userId, fingerprint, response);
                return;
            }
            
            // Update device last seen
            updateDeviceActivity(userId, fingerprint);
            
            filterChain.doFilter(request, response);
            
        } catch (Exception e) {
            log.error("Device fingerprinting failed for user {}", userId, e);
            // Continue with request but mark as unverified
            request.setAttribute("device.verified", false);
            filterChain.doFilter(request, response);
        }
    }

    private DeviceFingerprint generateFingerprint(HttpServletRequest request) {
        DeviceFingerprint.DeviceFingerprintBuilder builder = DeviceFingerprint.builder();
        
        // User Agent
        String userAgent = request.getHeader("User-Agent");
        builder.userAgent(userAgent != null ? userAgent : "unknown");
        
        // Accept headers
        builder.acceptLanguage(request.getHeader("Accept-Language"))
               .acceptEncoding(request.getHeader("Accept-Encoding"))
               .accept(request.getHeader("Accept"));
        
        // Client hints (modern browsers)
        builder.platformVersion(request.getHeader("Sec-CH-UA-Platform-Version"))
               .platform(request.getHeader("Sec-CH-UA-Platform"))
               .mobile(request.getHeader("Sec-CH-UA-Mobile"))
               .brands(request.getHeader("Sec-CH-UA"));
        
        // Custom headers from client
        builder.screenResolution(request.getHeader("X-Screen-Resolution"))
               .timezone(request.getHeader("X-Timezone"))
               .colorDepth(request.getHeader("X-Color-Depth"))
               .pixelRatio(request.getHeader("X-Pixel-Ratio"))
               .touchSupport(request.getHeader("X-Touch-Support"))
               .canvas(request.getHeader("X-Canvas-Fingerprint"))
               .webgl(request.getHeader("X-WebGL-Fingerprint"))
               .fonts(request.getHeader("X-Fonts-Hash") != null ? 
                      java.util.Arrays.asList(request.getHeader("X-Fonts-Hash").split(",")) : 
                      java.util.Collections.emptyList());
        
        DeviceFingerprint fingerprint = builder.build();
        fingerprint.setHash(calculateHash(fingerprint));
        fingerprint.setTimestamp(Instant.now());
        
        return fingerprint;
    }

    private String calculateHash(DeviceFingerprint fingerprint) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            // Combine stable attributes for hash
            StringBuilder data = new StringBuilder();
            data.append(fingerprint.getUserAgent());
            data.append(fingerprint.getPlatform());
            data.append(fingerprint.getScreenResolution());
            data.append(fingerprint.getTimezone());
            data.append(fingerprint.getColorDepth());
            data.append(fingerprint.getCanvas());
            data.append(fingerprint.getWebgl());
            data.append(fingerprint.getFonts());
            
            byte[] hash = digest.digest(data.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
            
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to calculate device fingerprint hash", e);
        }
    }

    private boolean validateDevice(String userId, DeviceFingerprint fingerprint, HttpServletRequest request) {
        String devicesKey = "user:devices:" + userId;
        String deviceKey = "device:" + fingerprint.getHash();
        
        // Check if device is already trusted
        Boolean isTrusted = redisTemplate.opsForSet().isMember(devicesKey, fingerprint.getHash());
        if (Boolean.TRUE.equals(isTrusted)) {
            return true;
        }
        
        // Check device limit
        Long deviceCount = redisTemplate.opsForSet().size(devicesKey);
        if (deviceCount != null && deviceCount >= maxDevicesPerUser) {
            log.warn("User {} has reached maximum device limit of {}", userId, maxDevicesPerUser);
            return false;
        }
        
        // New device - check if it should be trusted
        String clientIp = getClientIp(request);
        boolean shouldTrust = evaluateNewDevice(userId, fingerprint, clientIp);
        
        if (shouldTrust) {
            // Add to trusted devices
            redisTemplate.opsForSet().add(devicesKey, fingerprint.getHash());
            redisTemplate.expire(devicesKey, Duration.ofDays(trustDurationDays));
            
            // Store device details
            redisTemplate.opsForHash().putAll(deviceKey, fingerprint.toMap());
            redisTemplate.expire(deviceKey, Duration.ofDays(trustDurationDays));
            
            log.info("New device trusted for user {}: {}", userId, fingerprint.getHash());
        }
        
        return shouldTrust;
    }

    private boolean evaluateNewDevice(String userId, DeviceFingerprint fingerprint, String clientIp) {
        // Check if user is in device enrollment mode
        String enrollmentKey = "device:enrollment:" + userId;
        Boolean inEnrollment = redisTemplate.hasKey(enrollmentKey);
        
        if (Boolean.TRUE.equals(inEnrollment)) {
            // User recently authenticated with MFA or similar
            return true;
        }
        
        // Check if from known IP
        String knownIpsKey = "user:ips:" + userId;
        Boolean isKnownIp = redisTemplate.opsForSet().isMember(knownIpsKey, clientIp);
        
        // More lenient for known IPs
        return Boolean.TRUE.equals(isKnownIp);
    }

    private void updateDeviceActivity(String userId, DeviceFingerprint fingerprint) {
        String activityKey = "device:activity:" + fingerprint.getHash();
        
        redisTemplate.opsForHash().put(activityKey, "lastSeen", Instant.now().toString());
        redisTemplate.opsForHash().put(activityKey, "userId", userId);
        redisTemplate.opsForHash().increment(activityKey, "useCount", 1);
        
        redisTemplate.expire(activityKey, Duration.ofDays(trustDurationDays));
    }

    private boolean isHighRiskOperation(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        // Define high-risk operations
        return (path.contains("/payments") && "POST".equals(method)) ||
               (path.contains("/transfers") && "POST".equals(method)) ||
               (path.contains("/users") && ("PUT".equals(method) || "DELETE".equals(method))) ||
               path.contains("/settings/security") ||
               path.contains("/cards") ||
               path.contains("/beneficiaries");
    }

    private void handleUntrustedDevice(String userId, DeviceFingerprint fingerprint, 
                                     HttpServletResponse response) throws IOException {
        log.warn("Untrusted device {} attempted high-risk operation for user {}", 
                fingerprint.getHash(), userId);
        
        // Set enrollment mode for device trust after additional auth
        String enrollmentKey = "device:enrollment:" + userId;
        redisTemplate.opsForValue().set(enrollmentKey, fingerprint.getHash(), 5, TimeUnit.MINUTES);
        
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Device verification required\",\"code\":\"DEVICE_NOT_TRUSTED\"}");
    }

    private String getClientIp(HttpServletRequest request) {
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
}