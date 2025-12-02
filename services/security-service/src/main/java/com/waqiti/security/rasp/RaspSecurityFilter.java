package com.waqiti.security.rasp;

import com.waqiti.security.rasp.analyzer.ThreatAnalyzer;
import com.waqiti.security.rasp.detector.AttackDetector;
import com.waqiti.security.rasp.model.SecurityEvent;
import com.waqiti.security.rasp.model.ThreatLevel;
import com.waqiti.security.rasp.response.SecurityResponse;
import com.waqiti.security.rasp.service.SecurityEventService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * RASP Security Filter - Runtime Application Self-Protection
 * Monitors and protects against attacks in real-time
 */
@Component
@Order(1)
@Slf4j
@RequiredArgsConstructor
public class RaspSecurityFilter implements Filter {

    private final List<AttackDetector> attackDetectors;
    private final ThreatAnalyzer threatAnalyzer;
    private final SecurityResponse securityResponse;
    private final SecurityEventService securityEventService;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Create request wrapper for detailed analysis
        RaspRequestWrapper requestWrapper = new RaspRequestWrapper(httpRequest);
        
        try {
            // Pre-request security analysis
            SecurityEvent preEvent = analyzeRequest(requestWrapper);
            
            if (preEvent != null && shouldBlockRequest(preEvent)) {
                handleSecurityThreat(preEvent, httpResponse);
                return;
            }
            
            // Continue with request processing
            chain.doFilter(requestWrapper, response);
            
            // Post-request analysis (if needed)
            analyzeResponse(requestWrapper, httpResponse);
            
        } catch (Exception e) {
            log.error("RASP Filter error: ", e);
            // Log security event for unexpected errors
            SecurityEvent errorEvent = SecurityEvent.builder()
                    .timestamp(LocalDateTime.now())
                    .requestId(generateRequestId(httpRequest))
                    .clientIp(getClientIp(httpRequest))
                    .userAgent(httpRequest.getHeader("User-Agent"))
                    .uri(httpRequest.getRequestURI())
                    .method(httpRequest.getMethod())
                    .threatType("SYSTEM_ERROR")
                    .threatLevel(ThreatLevel.LOW)
                    .description("Unexpected error in RASP filter: " + e.getMessage())
                    .build();
            securityEventService.logSecurityEvent(errorEvent);
            
            // Continue with normal processing
            chain.doFilter(request, response);
        }
    }

    private SecurityEvent analyzeRequest(RaspRequestWrapper request) {
        String requestId = generateRequestId(request);
        String clientIp = getClientIp(request);
        
        // Run all attack detectors
        for (AttackDetector detector : attackDetectors) {
            try {
                SecurityEvent threat = detector.detectThreat(request);
                if (threat != null) {
                    // Enhance with request metadata
                    threat.setRequestId(requestId);
                    threat.setClientIp(clientIp);
                    threat.setUserAgent(request.getHeader("User-Agent"));
                    threat.setUri(request.getRequestURI());
                    threat.setMethod(request.getMethod());
                    threat.setTimestamp(LocalDateTime.now());
                    
                    // Analyze threat severity
                    ThreatLevel level = threatAnalyzer.analyzeThreat(threat, request);
                    threat.setThreatLevel(level);
                    
                    log.warn("Security threat detected: {} - {} - {}", 
                            threat.getThreatType(), level, threat.getDescription());
                    
                    return threat;
                }
            } catch (Exception e) {
                log.error("Error in attack detector {}: ", detector.getClass().getSimpleName(), e);
            }
        }
        
        return null;
    }

    private void analyzeResponse(RaspRequestWrapper request, HttpServletResponse response) {
        // Post-request analysis for data exfiltration, information disclosure, etc.
        if (response.getStatus() >= 500) {
            SecurityEvent event = SecurityEvent.builder()
                    .timestamp(LocalDateTime.now())
                    .requestId(generateRequestId(request))
                    .clientIp(getClientIp(request))
                    .uri(request.getRequestURI())
                    .method(request.getMethod())
                    .threatType("SERVER_ERROR")
                    .threatLevel(ThreatLevel.MEDIUM)
                    .description("Server error response: " + response.getStatus())
                    .build();
            securityEventService.logSecurityEvent(event);
        }
    }

    private boolean shouldBlockRequest(SecurityEvent event) {
        return event.getThreatLevel() == ThreatLevel.CRITICAL || 
               event.getThreatLevel() == ThreatLevel.HIGH;
    }

    private void handleSecurityThreat(SecurityEvent event, HttpServletResponse response) 
            throws IOException {
        // Log the security event
        securityEventService.logSecurityEvent(event);
        
        // Execute security response
        securityResponse.handleThreat(event, response);
        
        log.error("Blocked security threat: {} from IP: {} - {}", 
                event.getThreatType(), event.getClientIp(), event.getDescription());
    }

    private String generateRequestId(HttpServletRequest request) {
        String existingId = request.getHeader("X-Request-ID");
        return existingId != null ? existingId : 
                "REQ-" + System.currentTimeMillis() + "-" + 
                Integer.toHexString(request.hashCode());
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