package com.waqiti.currency.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.waqiti.common.resilience.ResilientServiceExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * Comprehensive configuration for Currency Service
 * Includes caching, async processing, external service clients, and validation
 */
@Slf4j
@Configuration
@EnableCaching
@EnableAsync
@EnableScheduling
public class CurrencyServiceConfig {

    @Value("${currency.service.async.core-pool-size:10}")
    private int asyncCorePoolSize;

    @Value("${currency.service.async.max-pool-size:50}")
    private int asyncMaxPoolSize;

    @Value("${currency.service.async.queue-capacity:100}")
    private int asyncQueueCapacity;

    @Value("${currency.service.http.connection-timeout:10000}")
    private int httpConnectionTimeout;

    @Value("${currency.service.http.read-timeout:30000}")
    private int httpReadTimeout;

    @Value("${currency.service.cache.ttl.exchange-rates:300}")
    private long exchangeRateCacheTtl;

    @Value("${currency.service.cache.ttl.currency-info:3600}")
    private long currencyInfoCacheTtl;

    /**
     * Primary ObjectMapper for JSON serialization
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.findAndRegisterModules();
        return mapper;
    }

    /**
     * Redis template configuration for caching exchange rates
     */
    @Bean
    public RedisTemplate<String, Object> currencyRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serializer for values
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper()));
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper()));
        
        template.setEnableTransactionSupport(true);
        template.afterPropertiesSet();
        
        return template;
    }

    /**
     * Cache manager for in-memory caching
     */
    @Bean
    public CacheManager currencyCacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        cacheManager.setAllowNullValues(false);
        return cacheManager;
    }

    /**
     * Async task executor for currency operations
     */
    @Bean(name = "currencyTaskExecutor")
    public Executor currencyTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(asyncCorePoolSize);
        executor.setMaxPoolSize(asyncMaxPoolSize);
        executor.setQueueCapacity(asyncQueueCapacity);
        executor.setThreadNamePrefix("Currency-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * HTTP client factory for external currency services
     */
    @Bean
    public ClientHttpRequestFactory currencyHttpRequestFactory() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(httpConnectionTimeout);
        factory.setReadTimeout(httpReadTimeout);
        factory.setConnectionRequestTimeout(httpConnectionTimeout);
        return factory;
    }

    /**
     * RestTemplate for external currency API calls
     */
    @Bean(name = "currencyRestTemplate")
    public RestTemplate currencyRestTemplate(ClientHttpRequestFactory currencyHttpRequestFactory) {
        RestTemplate restTemplate = new RestTemplate(currencyHttpRequestFactory);
        
        // Add error handler
        restTemplate.setErrorHandler(new CurrencyServiceResponseErrorHandler());
        
        // Add interceptors for logging and authentication
        restTemplate.getInterceptors().add(new CurrencyServiceLoggingInterceptor());
        restTemplate.getInterceptors().add(new CurrencyServiceAuthInterceptor());
        
        return restTemplate;
    }

    /**
     * Validator for request validation
     */
    @Bean
    public Validator currencyValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        return factory.getValidator();
    }

    /**
     * Currency exchange rate cache configuration
     */
    @Bean
    public CurrencyExchangeRateCache currencyExchangeRateCache(
            RedisTemplate<String, Object> currencyRedisTemplate,
            ResilientServiceExecutor resilientExecutor) {
        return new CurrencyExchangeRateCache(
            currencyRedisTemplate, 
            resilientExecutor, 
            Duration.ofSeconds(exchangeRateCacheTtl)
        );
    }

    /**
     * Currency information cache
     */
    @Bean
    public CurrencyInfoCache currencyInfoCache(
            RedisTemplate<String, Object> currencyRedisTemplate) {
        return new CurrencyInfoCache(
            currencyRedisTemplate, 
            Duration.ofSeconds(currencyInfoCacheTtl)
        );
    }

    /**
     * External currency provider client
     */
    @Bean
    public ExternalCurrencyProviderClient externalCurrencyProviderClient(
            RestTemplate currencyRestTemplate,
            ResilientServiceExecutor resilientExecutor) {
        return new ExternalCurrencyProviderClient(currencyRestTemplate, resilientExecutor);
    }

    /**
     * Currency conversion service
     */
    @Bean
    public CurrencyConversionService currencyConversionService(
            CurrencyExchangeRateCache exchangeRateCache,
            CurrencyInfoCache currencyInfoCache,
            ExternalCurrencyProviderClient providerClient,
            ResilientServiceExecutor resilientExecutor) {
        return new CurrencyConversionService(
            exchangeRateCache, 
            currencyInfoCache, 
            providerClient, 
            resilientExecutor
        );
    }

    /**
     * Currency rate updater scheduler
     */
    @Bean
    public CurrencyRateUpdaterService currencyRateUpdaterService(
            CurrencyExchangeRateCache exchangeRateCache,
            ExternalCurrencyProviderClient providerClient) {
        return new CurrencyRateUpdaterService(exchangeRateCache, providerClient);
    }

    /**
     * Currency service metrics
     */
    @Bean
    public CurrencyServiceMetrics currencyServiceMetrics() {
        return new CurrencyServiceMetrics();
    }

    /**
     * Currency service health indicator
     */
    @Bean
    public CurrencyServiceHealthIndicator currencyServiceHealthIndicator(
            CurrencyExchangeRateCache exchangeRateCache,
            ExternalCurrencyProviderClient providerClient,
            ResilientServiceExecutor resilientExecutor) {
        return new CurrencyServiceHealthIndicator(exchangeRateCache, providerClient, resilientExecutor);
    }

    // Inner classes for currency service components
    
    /**
     * Response error handler for currency service HTTP calls
     */
    public static class CurrencyServiceResponseErrorHandler 
            implements org.springframework.web.client.ResponseErrorHandler {
        
        @Override
        public boolean hasError(org.springframework.http.client.ClientHttpResponse response) 
                throws java.io.IOException {
            return response.getStatusCode().isError();
        }
        
        @Override
        public void handleError(org.springframework.http.client.ClientHttpResponse response) 
                throws java.io.IOException {
            String statusText = response.getStatusText();
            org.springframework.http.HttpStatus statusCode = response.getStatusCode();
            
            log.error("Currency service HTTP error: {} - {}", statusCode, statusText);
            
            if (statusCode.is4xxClientError()) {
                throw new CurrencyServiceClientException("Client error: " + statusText);
            } else if (statusCode.is5xxServerError()) {
                throw new CurrencyServiceServerException("Server error: " + statusText);
            }
        }
    }

    /**
     * Logging interceptor for currency service requests
     */
    public static class CurrencyServiceLoggingInterceptor 
            implements org.springframework.http.client.ClientHttpRequestInterceptor {
        
        @Override
        public org.springframework.http.client.ClientHttpResponse intercept(
                org.springframework.http.HttpRequest request,
                byte[] body,
                org.springframework.http.client.ClientHttpRequestExecution execution) 
                throws java.io.IOException {
            
            long startTime = System.currentTimeMillis();
            log.debug("Currency service request: {} {}", request.getMethod(), request.getURI());
            
            org.springframework.http.client.ClientHttpResponse response = execution.execute(request, body);
            
            long endTime = System.currentTimeMillis();
            log.debug("Currency service response: {} in {}ms", 
                     response.getStatusCode(), (endTime - startTime));
            
            return response;
        }
    }

    /**
     * Authentication interceptor for currency service requests
     */
    public static class CurrencyServiceAuthInterceptor 
            implements org.springframework.http.client.ClientHttpRequestInterceptor {
        
        @Value("${currency.service.api.key:}")
        private String apiKey;
        
        @Override
        public org.springframework.http.client.ClientHttpResponse intercept(
                org.springframework.http.HttpRequest request,
                byte[] body,
                org.springframework.http.client.ClientHttpRequestExecution execution) 
                throws java.io.IOException {
            
            if (apiKey != null && !apiKey.isEmpty()) {
                request.getHeaders().set("X-API-Key", apiKey);
            }
            
            request.getHeaders().set("User-Agent", "Waqiti-Currency-Service/1.0");
            request.getHeaders().set("Accept", "application/json");
            
            return execution.execute(request, body);
        }
    }

    // Placeholder classes for currency service components
    // These would be implemented as separate files in a real application
    
    public static class CurrencyExchangeRateCache {
        public CurrencyExchangeRateCache(RedisTemplate<String, Object> redisTemplate, 
                                        ResilientServiceExecutor resilientExecutor, 
                                        Duration ttl) {
            // Implementation would go here
        }
    }
    
    public static class CurrencyInfoCache {
        public CurrencyInfoCache(RedisTemplate<String, Object> redisTemplate, Duration ttl) {
            // Implementation would go here
        }
    }
    
    public static class ExternalCurrencyProviderClient {
        public ExternalCurrencyProviderClient(RestTemplate restTemplate, 
                                             ResilientServiceExecutor resilientExecutor) {
            // Implementation would go here
        }
    }
    
    public static class CurrencyConversionService {
        public CurrencyConversionService(CurrencyExchangeRateCache exchangeRateCache,
                                        CurrencyInfoCache currencyInfoCache,
                                        ExternalCurrencyProviderClient providerClient,
                                        ResilientServiceExecutor resilientExecutor) {
            // Implementation would go here
        }
    }
    
    public static class CurrencyRateUpdaterService {
        public CurrencyRateUpdaterService(CurrencyExchangeRateCache exchangeRateCache,
                                         ExternalCurrencyProviderClient providerClient) {
            // Implementation would go here
        }
    }
    
    public static class CurrencyServiceMetrics {
        // Metrics implementation would go here
    }
    
    public static class CurrencyServiceHealthIndicator {
        public CurrencyServiceHealthIndicator(CurrencyExchangeRateCache exchangeRateCache,
                                             ExternalCurrencyProviderClient providerClient,
                                             ResilientServiceExecutor resilientExecutor) {
            // Health check implementation would go here
        }
    }

    // Exception classes
    public static class CurrencyServiceClientException extends RuntimeException {
        public CurrencyServiceClientException(String message) {
            super(message);
        }
    }

    public static class CurrencyServiceServerException extends RuntimeException {
        public CurrencyServiceServerException(String message) {
            super(message);
        }
    }
}