package com.waqiti.common.client;

import com.waqiti.common.api.StandardApiResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Base Service Client
 * 
 * Provides standardized HTTP communication between microservices
 * with circuit breaker, retry, and timeout patterns
 */
@Slf4j
@RequiredArgsConstructor
public abstract class ServiceClient {

    protected final RestTemplate restTemplate;
    protected final String baseUrl;
    protected final String serviceName;

    /**
     * GET request with circuit breaker and retry - for List responses
     */
    @CircuitBreaker(name = "service-client", fallbackMethod = "getListFallback")
    @Retry(name = "service-client")
    @TimeLimiter(name = "service-client")
    protected <T> CompletableFuture<ServiceResponse<List<T>>> getList(
            String endpoint, 
            ParameterizedTypeReference<StandardApiResponse<List<T>>> responseType,
            Map<String, Object> queryParams) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = buildUrl(endpoint, queryParams);
                
                HttpHeaders headers = createHeaders();
                HttpEntity<?> entity = new HttpEntity<>(headers);
                
                log.debug("GET request to: {} for service: {}", url, serviceName);
                
                ResponseEntity<StandardApiResponse<List<T>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    responseType
                );
                
                return handleListResponse(response);
                
            } catch (Exception e) {
                log.error("GET request failed for endpoint: {} in service: {}", endpoint, serviceName, e);
                return handleError(e);
            }
        });
    }

    /**
     * GET request with circuit breaker and retry
     */
    @CircuitBreaker(name = "service-client", fallbackMethod = "getFallback")
    @Retry(name = "service-client")
    @TimeLimiter(name = "service-client")
    protected <T> CompletableFuture<ServiceResponse<T>> get(
            String endpoint, 
            Class<T> responseType, 
            Map<String, Object> queryParams) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = buildUrl(endpoint, queryParams);
                
                HttpHeaders headers = createHeaders();
                HttpEntity<?> entity = new HttpEntity<>(headers);
                
                log.debug("GET request to: {} for service: {}", url, serviceName);
                
                ResponseEntity<StandardApiResponse<T>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<StandardApiResponse<T>>() {}
                );
                
                return handleResponse(response);
                
            } catch (Exception e) {
                log.error("GET request failed for endpoint: {} in service: {}", endpoint, serviceName, e);
                return handleError(e);
            }
        });
    }

    /**
     * POST request with circuit breaker and retry
     */
    @CircuitBreaker(name = "service-client", fallbackMethod = "postFallback")
    @Retry(name = "service-client")
    @TimeLimiter(name = "service-client")
    protected <T, R> CompletableFuture<ServiceResponse<R>> post(
            String endpoint, 
            T requestBody, 
            Class<R> responseType) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = buildUrl(endpoint, null);
                
                HttpHeaders headers = createHeaders();
                HttpEntity<T> entity = new HttpEntity<>(requestBody, headers);
                
                log.debug("POST request to: {} for service: {}", url, serviceName);
                
                ResponseEntity<StandardApiResponse<R>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<StandardApiResponse<R>>() {}
                );
                
                return handleResponse(response);
                
            } catch (Exception e) {
                log.error("POST request failed for endpoint: {} in service: {}", endpoint, serviceName, e);
                return handleError(e);
            }
        });
    }

    /**
     * PUT request with circuit breaker and retry
     */
    @CircuitBreaker(name = "service-client", fallbackMethod = "putFallback")
    @Retry(name = "service-client")
    @TimeLimiter(name = "service-client")
    protected <T, R> CompletableFuture<ServiceResponse<R>> put(
            String endpoint, 
            T requestBody, 
            Class<R> responseType) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = buildUrl(endpoint, null);
                
                HttpHeaders headers = createHeaders();
                HttpEntity<T> entity = new HttpEntity<>(requestBody, headers);
                
                log.debug("PUT request to: {} for service: {}", url, serviceName);
                
                ResponseEntity<StandardApiResponse<R>> response = restTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    entity,
                    new ParameterizedTypeReference<StandardApiResponse<R>>() {}
                );
                
                return handleResponse(response);
                
            } catch (Exception e) {
                log.error("PUT request failed for endpoint: {} in service: {}", endpoint, serviceName, e);
                return handleError(e);
            }
        });
    }

    /**
     * DELETE request with circuit breaker and retry
     */
    @CircuitBreaker(name = "service-client", fallbackMethod = "deleteFallback")
    @Retry(name = "service-client")
    @TimeLimiter(name = "service-client")
    protected <T> CompletableFuture<ServiceResponse<T>> delete(
            String endpoint, 
            Class<T> responseType) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = buildUrl(endpoint, null);
                
                HttpHeaders headers = createHeaders();
                HttpEntity<?> entity = new HttpEntity<>(headers);
                
                log.debug("DELETE request to: {} for service: {}", url, serviceName);
                
                ResponseEntity<StandardApiResponse<T>> response = restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    entity,
                    new ParameterizedTypeReference<StandardApiResponse<T>>() {}
                );
                
                return handleResponse(response);
                
            } catch (Exception e) {
                log.error("DELETE request failed for endpoint: {} in service: {}", endpoint, serviceName, e);
                return handleError(e);
            }
        });
    }

    /**
     * Build URL with query parameters
     */
    private String buildUrl(String endpoint, Map<String, Object> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + endpoint);
        
        if (queryParams != null) {
            queryParams.forEach(builder::queryParam);
        }
        
        return builder.toUriString();
    }

    /**
     * Create standard headers
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Service-Name", serviceName);
        headers.add("X-Request-ID", java.util.UUID.randomUUID().toString());
        
        // Add correlation ID if present in current context
        String correlationId = getCurrentCorrelationId();
        if (correlationId != null) {
            headers.add("X-Correlation-ID", correlationId);
        }
        
        // Add authentication headers
        String authToken = getCurrentAuthToken();
        if (authToken != null) {
            headers.add("Authorization", "Bearer " + authToken);
        }
        
        return headers;
    }

    /**
     * Handle successful List response
     */
    private <T> ServiceResponse<List<T>> handleListResponse(ResponseEntity<StandardApiResponse<List<T>>> response) {
        StandardApiResponse<List<T>> apiResponse = response.getBody();
        
        if (apiResponse != null && apiResponse.isSuccess()) {
            return ServiceResponse.success(apiResponse.getData(), apiResponse.getMessage());
        } else if (apiResponse != null && apiResponse.hasError()) {
            return ServiceResponse.error(
                apiResponse.getError().getCode(), 
                apiResponse.getError().getMessage()
            );
        } else {
            return ServiceResponse.error("UNKNOWN_ERROR", "Unknown error occurred");
        }
    }

    /**
     * Handle successful response
     */
    private <T> ServiceResponse<T> handleResponse(ResponseEntity<StandardApiResponse<T>> response) {
        StandardApiResponse<T> apiResponse = response.getBody();
        
        if (apiResponse != null && apiResponse.isSuccess()) {
            return ServiceResponse.success(apiResponse.getData(), apiResponse.getMessage());
        } else if (apiResponse != null && apiResponse.hasError()) {
            return ServiceResponse.error(
                apiResponse.getError().getCode(), 
                apiResponse.getError().getMessage()
            );
        } else {
            return ServiceResponse.error("UNKNOWN_ERROR", "Unknown error occurred");
        }
    }

    /**
     * Handle error response
     */
    private <T> ServiceResponse<T> handleError(Exception e) {
        if (e instanceof HttpClientErrorException clientError) {
            return ServiceResponse.error(
                "CLIENT_ERROR_" + clientError.getStatusCode().value(),
                clientError.getMessage()
            );
        } else if (e instanceof HttpServerErrorException serverError) {
            return ServiceResponse.error(
                "SERVER_ERROR_" + serverError.getStatusCode().value(),
                serverError.getMessage()
            );
        } else if (e instanceof ResourceAccessException) {
            return ServiceResponse.error("CONNECTION_ERROR", "Service unavailable");
        } else {
            return ServiceResponse.error("UNKNOWN_ERROR", e.getMessage());
        }
    }

    // Fallback methods for circuit breaker

    protected <T> CompletableFuture<ServiceResponse<List<T>>> getListFallback(
            String endpoint, 
            ParameterizedTypeReference<StandardApiResponse<List<T>>> responseType,
            Map<String, Object> queryParams, 
            Exception ex) {
        
        log.warn("Circuit breaker fallback for GET List {} in service {}: {}", 
            endpoint, serviceName, ex.getMessage());
        
        return CompletableFuture.completedFuture(
            ServiceResponse.error("SERVICE_UNAVAILABLE", 
                serviceName + " service is currently unavailable")
        );
    }

    protected <T> CompletableFuture<ServiceResponse<T>> getFallback(
            String endpoint, 
            Class<T> responseType, 
            Map<String, Object> queryParams, 
            Exception ex) {
        
        log.warn("Circuit breaker fallback for GET {} in service {}: {}", 
            endpoint, serviceName, ex.getMessage());
        
        return CompletableFuture.completedFuture(
            ServiceResponse.error("SERVICE_UNAVAILABLE", 
                serviceName + " service is currently unavailable")
        );
    }

    protected <T, R> CompletableFuture<ServiceResponse<R>> postFallback(
            String endpoint, 
            T requestBody, 
            Class<R> responseType, 
            Exception ex) {
        
        log.warn("Circuit breaker fallback for POST {} in service {}: {}", 
            endpoint, serviceName, ex.getMessage());
        
        return CompletableFuture.completedFuture(
            ServiceResponse.error("SERVICE_UNAVAILABLE", 
                serviceName + " service is currently unavailable")
        );
    }

    protected <T, R> CompletableFuture<ServiceResponse<R>> putFallback(
            String endpoint, 
            T requestBody, 
            Class<R> responseType, 
            Exception ex) {
        
        log.warn("Circuit breaker fallback for PUT {} in service {}: {}", 
            endpoint, serviceName, ex.getMessage());
        
        return CompletableFuture.completedFuture(
            ServiceResponse.error("SERVICE_UNAVAILABLE", 
                serviceName + " service is currently unavailable")
        );
    }

    protected <T> CompletableFuture<ServiceResponse<T>> deleteFallback(
            String endpoint, 
            Class<T> responseType, 
            Exception ex) {
        
        log.warn("Circuit breaker fallback for DELETE {} in service {}: {}", 
            endpoint, serviceName, ex.getMessage());
        
        return CompletableFuture.completedFuture(
            ServiceResponse.error("SERVICE_UNAVAILABLE", 
                serviceName + " service is currently unavailable")
        );
    }

    // Abstract methods to be implemented by subclasses

    /**
     * Get current correlation ID from request context
     */
    protected abstract String getCurrentCorrelationId();

    /**
     * Get current authentication token from request context
     */
    protected abstract String getCurrentAuthToken();

    /**
     * Service Response wrapper
     */
    public static class ServiceResponse<T> {
        private final boolean success;
        private final T data;
        private final String message;
        private final String errorCode;
        private final String errorMessage;

        private ServiceResponse(boolean success, T data, String message, String errorCode, String errorMessage) {
            this.success = success;
            this.data = data;
            this.message = message;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public static <T> ServiceResponse<T> success(T data) {
            return new ServiceResponse<>(true, data, null, null, null);
        }

        public static <T> ServiceResponse<T> success(T data, String message) {
            return new ServiceResponse<>(true, data, message, null, null);
        }

        public static <T> ServiceResponse<T> error(String errorCode, String errorMessage) {
            return new ServiceResponse<>(false, null, null, errorCode, errorMessage);
        }

        // Getters
        public boolean isSuccess() { return success; }
        public T getData() { return data; }
        public String getMessage() { return message; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
        public boolean hasError() { return !success; }
    }
}