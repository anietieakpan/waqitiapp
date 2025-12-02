package com.waqiti.common.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {
    
    public void logError(String errorId, Exception ex, HttpServletRequest request) {
        log.error("Error [{}] occurred at {}: {}", errorId, request.getRequestURI(), ex.getMessage());
        // Implementation would save to audit log database
    }
    
    public void logSecurityIncident(String errorId, Exception ex, HttpServletRequest request) {
        log.error("Security incident [{}] at {}: {}", errorId, request.getRequestURI(), ex.getMessage());
        // Implementation would save to security audit log and trigger alerts
    }
}