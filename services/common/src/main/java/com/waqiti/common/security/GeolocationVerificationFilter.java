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
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Geolocation Verification Filter
 * Validates user location and detects impossible travel scenarios
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeolocationVerificationFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, Object> redisTemplate;
    private final GeoIPService geoIPService;
    
    @Value("${security.geolocation.enabled:true}")
    private boolean enabled;
    
    @Value("${security.geolocation.max-speed-kmh:900}")
    private int maxSpeedKmh; // Commercial flight speed
    
    @Value("${security.geolocation.trusted-countries:US,CA,GB,AU,NZ}")
    private String[] trustedCountries;
    
    @Value("${security.geolocation.blocked-countries:}")
    private String[] blockedCountries;

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
            String clientIp = getClientIp(request);
            GeoLocation location = geoIPService.getLocation(clientIp);
            
            // Check blocked countries
            if (isBlockedCountry(location.getCountryCode())) {
                handleBlockedLocation(userId, location, response);
                return;
            }
            
            // Check impossible travel
            if (isImpossibleTravel(userId, location)) {
                handleImpossibleTravel(userId, location, response);
                return;
            }
            
            // Add location info to request
            request.setAttribute("user.location.country", location.getCountryCode());
            request.setAttribute("user.location.city", location.getCity());
            request.setAttribute("user.location.trusted", isTrustedCountry(location.getCountryCode()));
            
            // Update location history
            updateLocationHistory(userId, location);
            
            filterChain.doFilter(request, response);
            
        } catch (Exception e) {
            log.error("Geolocation verification failed for user {}", userId, e);
            // Continue but mark as unverified
            request.setAttribute("location.verified", false);
            filterChain.doFilter(request, response);
        }
    }

    private boolean isBlockedCountry(String countryCode) {
        if (blockedCountries == null || blockedCountries.length == 0) {
            return false;
        }
        
        for (String blocked : blockedCountries) {
            if (blocked.equalsIgnoreCase(countryCode)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTrustedCountry(String countryCode) {
        if (trustedCountries == null || trustedCountries.length == 0) {
            return true; // All countries trusted if not configured
        }
        
        for (String trusted : trustedCountries) {
            if (trusted.equalsIgnoreCase(countryCode)) {
                return true;
            }
        }
        return false;
    }

    private boolean isImpossibleTravel(String userId, GeoLocation newLocation) {
        String lastLocationKey = "user:last-location:" + userId;
        LocationRecord lastRecord = (LocationRecord) redisTemplate.opsForValue().get(lastLocationKey);
        
        if (lastRecord == null) {
            return false; // First location
        }
        
        // Calculate time difference
        long timeDiffSeconds = Duration.between(lastRecord.getTimestamp(), Instant.now()).getSeconds();
        if (timeDiffSeconds > 86400) { // More than 24 hours
            return false; // Enough time for any travel
        }
        
        // Calculate distance
        double distanceKm = calculateDistance(
            lastRecord.getLatitude(), lastRecord.getLongitude(),
            newLocation.getLatitude(), newLocation.getLongitude()
        );
        
        // Calculate required speed
        double requiredSpeedKmh = (distanceKm / timeDiffSeconds) * 3600;
        
        if (requiredSpeedKmh > maxSpeedKmh) {
            log.warn("Impossible travel detected for user {}: {} km in {} seconds (requires {} km/h)",
                    userId, distanceKm, timeDiffSeconds, requiredSpeedKmh);
            return true;
        }
        
        return false;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Haversine formula for great-circle distance
        double R = 6371; // Earth's radius in kilometers
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private void updateLocationHistory(String userId, GeoLocation location) {
        // Store last location
        String lastLocationKey = "user:last-location:" + userId;
        LocationRecord record = new LocationRecord(
            location.getLatitude(),
            location.getLongitude(),
            location.getCountryCode(),
            location.getCity(),
            Instant.now()
        );
        redisTemplate.opsForValue().set(lastLocationKey, record, 30, TimeUnit.DAYS);
        
        // Add to location history
        String historyKey = "user:location-history:" + userId;
        redisTemplate.opsForList().leftPush(historyKey, record);
        redisTemplate.opsForList().trim(historyKey, 0, 99); // Keep last 100 locations
        redisTemplate.expire(historyKey, Duration.ofDays(90));
        
        // Update country set
        String countriesKey = "user:countries:" + userId;
        redisTemplate.opsForSet().add(countriesKey, location.getCountryCode());
        redisTemplate.expire(countriesKey, Duration.ofDays(180));
    }

    private void handleBlockedLocation(String userId, GeoLocation location, 
                                     HttpServletResponse response) throws IOException {
        log.warn("User {} attempted access from blocked country: {}", userId, location.getCountryCode());
        
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Access denied from this location\",\"code\":\"BLOCKED_COUNTRY\"}");
    }

    private void handleImpossibleTravel(String userId, GeoLocation location,
                                      HttpServletResponse response) throws IOException {
        log.warn("Impossible travel detected for user {} to {}, {}", 
                userId, location.getCity(), location.getCountryCode());
        
        // Trigger security alert
        String alertKey = "security:impossible-travel:" + userId;
        redisTemplate.opsForValue().set(alertKey, location, 1, TimeUnit.HOURS);
        
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Security verification required\",\"code\":\"IMPOSSIBLE_TRAVEL\"}");
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

