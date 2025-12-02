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
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Security monitoring filter for detecting and tracking security threats
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityMonitoringFilter implements Filter {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final SecurityEventPublisher securityEventPublisher;
    
    // Attack patterns to monitor
    private static final List<String> SQL_INJECTION_PATTERNS = Arrays.asList(
        "union", "select", "insert", "delete", "update", "drop", "exec", "script",
        "javascript:", "vbscript:", "onload=", "onerror=", "onmouseover="
    );
    
    private static final List<String> XSS_PATTERNS = Arrays.asList(
        "<script", "</script>", "javascript:", "vbscript:", "onload", "onerror",
        "alert(", "document.cookie", "document.write"
    );
    
    private static final List<String> PATH_TRAVERSAL_PATTERNS = Arrays.asList(
        "../", "..\\", "%2e%2e%2f", "%2e%2e\\", "....//", "....\\\\",
        "/etc/passwd", "/etc/shadow", "\\windows\\system32"
    );
    
    private static final String SECURITY_INCIDENT_PREFIX = "security:incident:";
    private static final String THREAT_SCORE_PREFIX = "security:threat_score:";
    private static final Duration INCIDENT_TTL = Duration.ofHours(24);
    
    private final AtomicLong securityIncidentCounter = new AtomicLong(0);
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        try {
            String clientIp = getClientIpAddress(httpRequest);
            
            // Analyze request for security threats
            SecurityThreat threat = analyzeRequest(httpRequest);
            
            if (threat.getThreatLevel() > 0) {
                handleSecurityThreat(httpRequest, threat, clientIp);
            }
            
            chain.doFilter(request, response);
            
        } catch (Exception e) {
            log.error("Error in security monitoring filter", e);
            // Allow request to proceed on error
            chain.doFilter(request, response);
        }
    }
    
    private SecurityThreat analyzeRequest(HttpServletRequest request) {
        SecurityThreat threat = new SecurityThreat();
        
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String userAgent = request.getHeader("User-Agent");
        
        // Check for SQL injection patterns
        int sqlInjectionScore = 0;
        if (uri != null) {
            sqlInjectionScore += countPatternMatches(uri.toLowerCase(), SQL_INJECTION_PATTERNS) * 10;
        }
        if (queryString != null) {
            sqlInjectionScore += countPatternMatches(queryString.toLowerCase(), SQL_INJECTION_PATTERNS) * 15;
        }
        
        // Check for XSS patterns
        int xssScore = 0;
        if (uri != null) {
            xssScore += countPatternMatches(uri.toLowerCase(), XSS_PATTERNS) * 10;
        }
        if (queryString != null) {
            xssScore += countPatternMatches(queryString.toLowerCase(), XSS_PATTERNS) * 15;
        }
        
        // Check for path traversal patterns
        int pathTraversalScore = 0;
        if (uri != null) {
            pathTraversalScore += countPatternMatches(uri.toLowerCase(), PATH_TRAVERSAL_PATTERNS) * 20;
        }
        
        // Check for suspicious user agents
        int userAgentScore = 0;
        if (userAgent != null) {
            String lowerUserAgent = userAgent.toLowerCase();
            if (lowerUserAgent.contains("bot") || lowerUserAgent.contains("crawler") || 
                lowerUserAgent.contains("scanner") || lowerUserAgent.length() < 10) {
                userAgentScore = 5;
            }
        }
        
        // Calculate total threat level
        int totalScore = sqlInjectionScore + xssScore + pathTraversalScore + userAgentScore;
        
        threat.setThreatLevel(totalScore);
        threat.setSqlInjectionScore(sqlInjectionScore);
        threat.setXssScore(xssScore);
        threat.setPathTraversalScore(pathTraversalScore);
        threat.setUserAgentScore(userAgentScore);
        
        return threat;
    }
    
    private int countPatternMatches(String input, List<String> patterns) {
        int count = 0;
        for (String pattern : patterns) {
            if (input.contains(pattern)) {
                count++;
            }
        }
        return count;
    }
    
    private void handleSecurityThreat(HttpServletRequest request, SecurityThreat threat, String clientIp) {
        try {
            long incidentId = securityIncidentCounter.incrementAndGet();
            
            // Store security incident
            String incidentKey = SECURITY_INCIDENT_PREFIX + incidentId;
            String incidentData = createIncidentData(request, threat, clientIp);
            redisTemplate.opsForValue().set(incidentKey, incidentData, INCIDENT_TTL);
            
            // Update threat score for IP
            updateThreatScore(clientIp, threat.getThreatLevel());
            
            // Publish security event for real-time monitoring
            securityEventPublisher.publishSecurityEvent(
                "SECURITY_THREAT_DETECTED",
                clientIp,
                threat,
                request.getRequestURI()
            );
            
            log.warn("Security threat detected - IP: {}, Threat Level: {}, URI: {}, " +
                     "SQL Injection: {}, XSS: {}, Path Traversal: {}, User Agent: {}",
                clientIp, threat.getThreatLevel(), request.getRequestURI(),
                threat.getSqlInjectionScore(), threat.getXssScore(),
                threat.getPathTraversalScore(), threat.getUserAgentScore());
            
        } catch (Exception e) {
            log.error("Failed to handle security threat", e);
        }
    }
    
    private String createIncidentData(HttpServletRequest request, SecurityThreat threat, String clientIp) {
        return String.format(
            "{\"timestamp\":\"%d\",\"ip\":\"%s\",\"uri\":\"%s\",\"method\":\"%s\"," +
            "\"user_agent\":\"%s\",\"threat_level\":%d,\"sql_injection\":%d,\"xss\":%d," +
            "\"path_traversal\":%d,\"user_agent_score\":%d}",
            System.currentTimeMillis(),
            clientIp,
            request.getRequestURI(),
            request.getMethod(),
            request.getHeader("User-Agent"),
            threat.getThreatLevel(),
            threat.getSqlInjectionScore(),
            threat.getXssScore(),
            threat.getPathTraversalScore(),
            threat.getUserAgentScore()
        );
    }
    
    private void updateThreatScore(String clientIp, int threatLevel) {
        try {
            String threatKey = THREAT_SCORE_PREFIX + clientIp;
            String currentScore = redisTemplate.opsForValue().get(threatKey);
            
            int newScore = threatLevel;
            if (currentScore != null) {
                newScore += Integer.parseInt(currentScore);
            }
            
            redisTemplate.opsForValue().set(threatKey, String.valueOf(newScore), INCIDENT_TTL);
            
            // Alert if threat score is too high
            if (newScore > 100) {
                securityEventPublisher.publishHighThreatAlert(clientIp, newScore);
            }
            
        } catch (Exception e) {
            log.warn("Failed to update threat score for IP: {}", clientIp, e);
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
    
    /**
     * Get current threat score for an IP address
     */
    public int getThreatScore(String clientIp) {
        try {
            String threatKey = THREAT_SCORE_PREFIX + clientIp;
            String score = redisTemplate.opsForValue().get(threatKey);
            return score != null ? Integer.parseInt(score) : 0;
        } catch (Exception e) {
            log.warn("Failed to get threat score for IP: {}", clientIp, e);
            return 0;
        }
    }
    
    /**
     * Data class for security threat analysis
     */
    public static class SecurityThreat {
        private int threatLevel = 0;
        private int sqlInjectionScore = 0;
        private int xssScore = 0;
        private int pathTraversalScore = 0;
        private int userAgentScore = 0;
        
        // Getters and setters
        public int getThreatLevel() { return threatLevel; }
        public void setThreatLevel(int threatLevel) { this.threatLevel = threatLevel; }
        
        public int getSqlInjectionScore() { return sqlInjectionScore; }
        public void setSqlInjectionScore(int sqlInjectionScore) { this.sqlInjectionScore = sqlInjectionScore; }
        
        public int getXssScore() { return xssScore; }
        public void setXssScore(int xssScore) { this.xssScore = xssScore; }
        
        public int getPathTraversalScore() { return pathTraversalScore; }
        public void setPathTraversalScore(int pathTraversalScore) { this.pathTraversalScore = pathTraversalScore; }
        
        public int getUserAgentScore() { return userAgentScore; }
        public void setUserAgentScore(int userAgentScore) { this.userAgentScore = userAgentScore; }
    }
}