package com.waqiti.virtualcard.service;

import com.waqiti.virtualcard.dto.OrderCardRequest;
import com.waqiti.virtualcard.dto.FraudCheckResult;
import com.waqiti.common.security.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for fraud detection and prevention
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionService {
    
    private final SecurityContext securityContext;
    
    @Value("${fraud-detection.enabled:true}")
    private boolean fraudDetectionEnabled;
    
    @Value("${fraud-detection.max-orders-per-day:3}")
    private int maxOrdersPerDay;
    
    @Value("${fraud-detection.max-orders-per-month:10}")
    private int maxOrdersPerMonth;
    
    @Value("${fraud-detection.suspicious-countries:}")
    private Set<String> suspiciousCountries;
    
    // In-memory cache for demonstration (use Redis in production)
    private final Map<String, List<Instant>> userOrderHistory = new ConcurrentHashMap<>();
    private final Set<String> blockedIpAddresses = ConcurrentHashMap.newKeySet();
    private final Set<String> blockedUserIds = ConcurrentHashMap.newKeySet();
    
    /**
     * Performs fraud check for card order
     */
    public FraudCheckResult checkCardOrder(String userId, OrderCardRequest request) {
        log.debug("Performing fraud check for user: {}", userId);
        
        if (!fraudDetectionEnabled) {
            return FraudCheckResult.builder()
                .blocked(false)
                .riskLevel("LOW")
                .reason("Fraud detection disabled")
                .build();
        }
        
        try {
            // Check if user is blocked
            if (isUserBlocked(userId)) {
                return createBlockedResult("User is blocked for suspicious activity", "HIGH");
            }
            
            // Check IP address
            String ipAddress = securityContext.getClientIpAddress();
            if (isIpAddressBlocked(ipAddress)) {
                return createBlockedResult("IP address is blocked", "HIGH");
            }
            
            // Check order velocity (too many orders in short time)
            if (isOrderVelocityExceeded(userId)) {
                return createBlockedResult("Too many orders in short period", "HIGH");
            }
            
            // Check suspicious shipping address
            if (isSuspiciousShippingAddress(request)) {
                return createBlockedResult("Suspicious shipping address", "MEDIUM");
            }
            
            // Check for duplicate orders
            if (isDuplicateOrder(userId, request)) {
                return createBlockedResult("Duplicate order detected", "MEDIUM");
            }
            
            // Check device fingerprint (if available)
            String deviceId = securityContext.getDeviceId();
            if (isSuspiciousDevice(deviceId)) {
                return createBlockedResult("Suspicious device detected", "MEDIUM");
            }
            
            // Check user behavior patterns
            String riskLevel = calculateRiskLevel(userId, request);
            
            // All checks passed
            recordOrderAttempt(userId);
            
            return FraudCheckResult.builder()
                .blocked(false)
                .riskLevel(riskLevel)
                .reason("All fraud checks passed")
                .requiresAdditionalVerification("HIGH".equals(riskLevel))
                .build();
                
        } catch (Exception e) {
            log.error("Fraud detection check failed", e);
            
            // In case of error, allow the order but flag for manual review
            return FraudCheckResult.builder()
                .blocked(false)
                .riskLevel("MEDIUM")
                .reason("Fraud check service error - manual review required")
                .requiresAdditionalVerification(true)
                .build();
        }
    }
    
    /**
     * Reports suspicious activity
     */
    public void reportSuspiciousActivity(String userId, String activityType, String details) {
        log.warn("Suspicious activity reported - User: {}, Type: {}, Details: {}", 
                userId, activityType, details);
        
        // In production, this would:
        // 1. Store in fraud database
        // 2. Update user risk score
        // 3. Trigger alerts to fraud team
        // 4. Consider temporary blocks
    }
    
    /**
     * Blocks a user temporarily or permanently
     */
    public void blockUser(String userId, String reason, boolean permanent) {
        log.warn("Blocking user {} - Reason: {}, Permanent: {}", userId, reason, permanent);
        
        blockedUserIds.add(userId);
        
        // In production:
        // 1. Store in database with expiry time if temporary
        // 2. Notify user (if appropriate)
        // 3. Trigger fraud team alerts
        // 4. Log security event
    }
    
    /**
     * Blocks an IP address
     */
    public void blockIpAddress(String ipAddress, String reason) {
        log.warn("Blocking IP address {} - Reason: {}", ipAddress, reason);
        
        blockedIpAddresses.add(ipAddress);
        
        // In production:
        // 1. Store in database/cache with expiry
        // 2. Consider IP range blocking
        // 3. Integrate with WAF/firewall
    }
    
    private boolean isUserBlocked(String userId) {
        return blockedUserIds.contains(userId);
    }
    
    private boolean isIpAddressBlocked(String ipAddress) {
        if (ipAddress == null) return false;
        return blockedIpAddresses.contains(ipAddress);
    }
    
    private boolean isOrderVelocityExceeded(String userId) {
        List<Instant> orderTimes = userOrderHistory.getOrDefault(userId, new ArrayList<>());
        
        Instant now = Instant.now();
        Instant oneDayAgo = now.minus(1, ChronoUnit.DAYS);
        Instant oneMonthAgo = now.minus(30, ChronoUnit.DAYS);
        
        // Count orders in last 24 hours
        long ordersToday = orderTimes.stream()
            .filter(time -> time.isAfter(oneDayAgo))
            .count();
        
        if (ordersToday >= maxOrdersPerDay) {
            log.warn("User {} exceeded daily order limit: {}", userId, ordersToday);
            return true;
        }
        
        // Count orders in last 30 days
        long ordersThisMonth = orderTimes.stream()
            .filter(time -> time.isAfter(oneMonthAgo))
            .count();
        
        if (ordersThisMonth >= maxOrdersPerMonth) {
            log.warn("User {} exceeded monthly order limit: {}", userId, ordersThisMonth);
            return true;
        }
        
        return false;
    }
    
    private boolean isSuspiciousShippingAddress(OrderCardRequest request) {
        if (request.getShippingAddress() == null) return false;
        
        String country = request.getShippingAddress().getCountry();
        if (suspiciousCountries.contains(country)) {
            log.info("Order to suspicious country: {}", country);
            return true;
        }
        
        // Check for suspicious address patterns
        String address = request.getShippingAddress().getAddressLine1().toLowerCase();
        
        // Suspicious keywords
        List<String> suspiciousKeywords = Arrays.asList(
            "drop box", "mail forwarding", "reshipping", "package forwarding"
        );
        
        for (String keyword : suspiciousKeywords) {
            if (address.contains(keyword)) {
                log.info("Suspicious address keyword detected: {}", keyword);
                return true;
            }
        }
        
        return false;
    }
    
    private boolean isDuplicateOrder(String userId, OrderCardRequest request) {
        // Simple duplicate detection - in production would be more sophisticated
        // Check if same user, same address within last hour
        
        List<Instant> orderTimes = userOrderHistory.getOrDefault(userId, new ArrayList<>());
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        
        boolean recentOrder = orderTimes.stream()
            .anyMatch(time -> time.isAfter(oneHourAgo));
        
        if (recentOrder) {
            log.info("Potential duplicate order detected for user: {}", userId);
            return true;
        }
        
        return false;
    }
    
    private boolean isSuspiciousDevice(String deviceId) {
        if (deviceId == null) return false;
        
        // Simple device-based checks
        // In production would check device fingerprinting, known bad devices, etc.
        
        return false; // Placeholder
    }
    
    private String calculateRiskLevel(String userId, OrderCardRequest request) {
        int riskScore = 0;
        
        // Increase risk for new users
        List<Instant> orderHistory = userOrderHistory.get(userId);
        if (orderHistory == null || orderHistory.isEmpty()) {
            riskScore += 10; // New user
        }
        
        // Increase risk for international shipping
        if (request.isInternationalShipping()) {
            riskScore += 5;
        }
        
        // Increase risk for rush delivery
        if (request.isRushDelivery()) {
            riskScore += 5;
        }
        
        // Increase risk for unusual hours
        int hour = java.time.LocalTime.now().getHour();
        if (hour < 6 || hour > 22) {
            riskScore += 3;
        }
        
        // Calculate final risk level
        if (riskScore >= 20) return "HIGH";
        if (riskScore >= 10) return "MEDIUM";
        return "LOW";
    }
    
    private void recordOrderAttempt(String userId) {
        userOrderHistory.computeIfAbsent(userId, k -> new ArrayList<>())
                       .add(Instant.now());
        
        // Clean up old entries (keep last 100 per user)
        List<Instant> orders = userOrderHistory.get(userId);
        if (orders.size() > 100) {
            orders.subList(0, orders.size() - 100).clear();
        }
    }
    
    private FraudCheckResult createBlockedResult(String reason, String riskLevel) {
        return FraudCheckResult.builder()
            .blocked(true)
            .riskLevel(riskLevel)
            .reason(reason)
            .requiresAdditionalVerification(true)
            .build();
    }
}