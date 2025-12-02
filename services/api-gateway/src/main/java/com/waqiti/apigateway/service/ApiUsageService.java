package com.waqiti.apigateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * API Usage Service
 * Tracks API usage and metrics
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiUsageService {

    /**
     * Record API usage
     */
    public void recordUsage(String apiKey, String endpoint, String method, int statusCode, long responseTime) {
        log.debug("Recording API usage: apiKey={}, endpoint={}, method={}, status={}, time={}ms",
                apiKey, endpoint, method, statusCode, responseTime);
        // Implementation would persist usage data
    }
}
