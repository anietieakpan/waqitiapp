package com.waqiti.common.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * HTTP request/response logging interceptor.
 * 
 * Provides comprehensive logging of outbound HTTP requests and responses
 * for debugging, monitoring, and audit purposes.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Slf4j
public class LoggingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {
    
    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        
        long startTime = System.currentTimeMillis();
        
        log.debug("HTTP Request: {} {} - Headers: {}", 
                request.getMethod(), request.getURI(), request.getHeaders());
        
        try {
            ClientHttpResponse response = execution.execute(request, body);
            
            long duration = System.currentTimeMillis() - startTime;
            
            log.debug("HTTP Response: {} {} - Status: {} {} - Duration: {}ms", 
                    request.getMethod(), request.getURI(), 
                    response.getRawStatusCode(), response.getStatusText(), duration);
            
            return response;
            
        } catch (IOException e) {
            long duration = System.currentTimeMillis() - startTime;
            
            log.error("HTTP Request failed: {} {} - Duration: {}ms - Error: {}", 
                    request.getMethod(), request.getURI(), duration, e.getMessage());
            
            throw e;
        }
    }
}