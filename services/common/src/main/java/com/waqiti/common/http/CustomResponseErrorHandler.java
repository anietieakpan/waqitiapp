package com.waqiti.common.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.io.IOException;

/**
 * Custom response error handler for RestTemplate.
 * 
 * Provides enhanced error handling with detailed logging,
 * custom exception mapping, and resilience patterns.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Slf4j
public class CustomResponseErrorHandler extends DefaultResponseErrorHandler {
    
    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
        HttpStatus statusCode = HttpStatus.resolve(response.getRawStatusCode());
        return (statusCode != null && (statusCode.is4xxClientError() || statusCode.is5xxServerError()));
    }
    
    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        HttpStatus statusCode = HttpStatus.resolve(response.getRawStatusCode());
        String statusText = response.getStatusText();
        
        if (statusCode != null) {
            if (statusCode.is4xxClientError()) {
                log.warn("Client error response: {} {} - {}", 
                        response.getRawStatusCode(), statusText, response.getHeaders());
                
                switch (statusCode) {
                    case UNAUTHORIZED:
                        throw new HttpClientErrorException(statusCode, "Authentication required");
                    case FORBIDDEN:
                        throw new HttpClientErrorException(statusCode, "Access forbidden");
                    case NOT_FOUND:
                        throw new HttpClientErrorException(statusCode, "Resource not found");
                    case TOO_MANY_REQUESTS:
                        throw new HttpClientErrorException(statusCode, "Rate limit exceeded");
                    default:
                        throw new HttpClientErrorException(statusCode, statusText);
                }
            } else if (statusCode.is5xxServerError()) {
                log.error("Server error response: {} {} - {}", 
                        response.getRawStatusCode(), statusText, response.getHeaders());
                
                switch (statusCode) {
                    case INTERNAL_SERVER_ERROR:
                        throw new HttpServerErrorException(statusCode, "Internal server error");
                    case BAD_GATEWAY:
                        throw new HttpServerErrorException(statusCode, "Bad gateway");
                    case SERVICE_UNAVAILABLE:
                        throw new HttpServerErrorException(statusCode, "Service unavailable");
                    case GATEWAY_TIMEOUT:
                        throw new HttpServerErrorException(statusCode, "Gateway timeout");
                    default:
                        throw new HttpServerErrorException(statusCode, statusText);
                }
            }
        }
        
        // Fallback to default handling
        super.handleError(response);
    }
}