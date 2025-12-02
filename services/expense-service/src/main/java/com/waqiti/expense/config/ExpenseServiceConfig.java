package com.waqiti.expense.config;

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
 * Comprehensive configuration for Expense Service
 * Includes expense management, receipt processing, categorization, and analytics
 */
@Slf4j
@Configuration
@EnableCaching
@EnableAsync
@EnableScheduling
public class ExpenseServiceConfig {

    @Value("${expense.service.async.core-pool-size:8}")
    private int asyncCorePoolSize;

    @Value("${expense.service.async.max-pool-size:32}")
    private int asyncMaxPoolSize;

    @Value("${expense.service.async.queue-capacity:200}")
    private int asyncQueueCapacity;

    @Value("${expense.service.http.connection-timeout:15000}")
    private int httpConnectionTimeout;

    @Value("${expense.service.http.read-timeout:45000}")
    private int httpReadTimeout;

    @Value("${expense.service.cache.ttl.expense-categories:7200}")
    private long expenseCategoriesCacheTtl;

    @Value("${expense.service.cache.ttl.expense-analytics:1800}")
    private long expenseAnalyticsCacheTtl;

    @Value("${expense.service.receipt.processing.enabled:true}")
    private boolean receiptProcessingEnabled;

    @Value("${expense.service.ml.categorization.enabled:true}")
    private boolean mlCategorizationEnabled;

    /**
     * Primary ObjectMapper for JSON serialization
     */
    @Bean
    @Primary
    public ObjectMapper expenseObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.findAndRegisterModules();
        // Configure for expense-specific serialization needs
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * Redis template configuration for expense caching
     */
    @Bean
    public RedisTemplate<String, Object> expenseRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serializer for values
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(expenseObjectMapper()));
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(expenseObjectMapper()));
        
        template.setEnableTransactionSupport(true);
        template.afterPropertiesSet();
        
        return template;
    }

    /**
     * Cache manager for expense operations
     */
    @Bean
    public CacheManager expenseCacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        cacheManager.setAllowNullValues(false);
        // Pre-create caches for expense operations
        cacheManager.setCacheNames(java.util.Arrays.asList(
            "expense-categories", 
            "expense-analytics", 
            "expense-budgets",
            "expense-reports"
        ));
        return cacheManager;
    }

    /**
     * Async task executor for expense operations
     */
    @Bean(name = "expenseTaskExecutor")
    public Executor expenseTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(asyncCorePoolSize);
        executor.setMaxPoolSize(asyncMaxPoolSize);
        executor.setQueueCapacity(asyncQueueCapacity);
        executor.setThreadNamePrefix("Expense-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(90);
        executor.initialize();
        return executor;
    }

    /**
     * Receipt processing task executor (separate pool for heavy operations)
     */
    @Bean(name = "receiptProcessingExecutor")
    public Executor receiptProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("Receipt-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        return executor;
    }

    /**
     * HTTP client factory for external expense services
     */
    @Bean
    public ClientHttpRequestFactory expenseHttpRequestFactory() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(httpConnectionTimeout);
        factory.setReadTimeout(httpReadTimeout);
        factory.setConnectionRequestTimeout(httpConnectionTimeout);
        return factory;
    }

    /**
     * RestTemplate for external expense API calls
     */
    @Bean(name = "expenseRestTemplate")
    public RestTemplate expenseRestTemplate(ClientHttpRequestFactory expenseHttpRequestFactory) {
        RestTemplate restTemplate = new RestTemplate(expenseHttpRequestFactory);
        
        // Add error handler
        restTemplate.setErrorHandler(new ExpenseServiceResponseErrorHandler());
        
        // Add interceptors for logging and authentication
        restTemplate.getInterceptors().add(new ExpenseServiceLoggingInterceptor());
        restTemplate.getInterceptors().add(new ExpenseServiceAuthInterceptor());
        
        return restTemplate;
    }

    /**
     * Validator for expense request validation
     */
    @Bean
    public Validator expenseValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        return factory.getValidator();
    }

    /**
     * Expense categories cache
     */
    @Bean
    public ExpenseCategoriesCache expenseCategoriesCache(
            RedisTemplate<String, Object> expenseRedisTemplate) {
        return new ExpenseCategoriesCache(
            expenseRedisTemplate, 
            Duration.ofSeconds(expenseCategoriesCacheTtl)
        );
    }

    /**
     * Expense analytics cache
     */
    @Bean
    public ExpenseAnalyticsCache expenseAnalyticsCache(
            RedisTemplate<String, Object> expenseRedisTemplate) {
        return new ExpenseAnalyticsCache(
            expenseRedisTemplate, 
            Duration.ofSeconds(expenseAnalyticsCacheTtl)
        );
    }

    /**
     * Receipt processing service
     */
    @Bean
    public ReceiptProcessingService receiptProcessingService(
            ResilientServiceExecutor resilientExecutor) {
        return new ReceiptProcessingService(resilientExecutor, receiptProcessingEnabled);
    }

    /**
     * ML expense categorization service
     */
    @Bean
    public MLExpenseCategorizationService mlExpenseCategorizationService(
            ResilientServiceExecutor resilientExecutor,
            ExpenseCategoriesCache categoriesCache) {
        return new MLExpenseCategorizationService(
            resilientExecutor, 
            categoriesCache, 
            mlCategorizationEnabled
        );
    }

    /**
     * Expense analytics service
     */
    @Bean
    public ExpenseAnalyticsService expenseAnalyticsService(
            ExpenseAnalyticsCache analyticsCache,
            ResilientServiceExecutor resilientExecutor) {
        return new ExpenseAnalyticsService(analyticsCache, resilientExecutor);
    }

    /**
     * Expense budget service
     */
    @Bean
    public ExpenseBudgetService expenseBudgetService(
            RedisTemplate<String, Object> expenseRedisTemplate,
            ResilientServiceExecutor resilientExecutor) {
        return new ExpenseBudgetService(expenseRedisTemplate, resilientExecutor);
    }

    /**
     * Expense report service
     */
    @Bean
    public ExpenseReportService expenseReportService(
            ExpenseAnalyticsService analyticsService,
            ExpenseCategoriesCache categoriesCache,
            ResilientServiceExecutor resilientExecutor) {
        return new ExpenseReportService(analyticsService, categoriesCache, resilientExecutor);
    }

    /**
     * Expense notification service
     */
    @Bean
    public ExpenseNotificationService expenseNotificationService(
            ResilientServiceExecutor resilientExecutor) {
        return new ExpenseNotificationService(resilientExecutor);
    }

    /**
     * Expense service metrics
     */
    @Bean
    public ExpenseServiceMetrics expenseServiceMetrics() {
        return new ExpenseServiceMetrics();
    }

    /**
     * Expense service health indicator
     */
    @Bean
    public ExpenseServiceHealthIndicator expenseServiceHealthIndicator(
            ExpenseCategoriesCache categoriesCache,
            ReceiptProcessingService receiptProcessingService,
            ResilientServiceExecutor resilientExecutor) {
        return new ExpenseServiceHealthIndicator(
            categoriesCache, 
            receiptProcessingService, 
            resilientExecutor
        );
    }

    /**
     * Expense audit service
     */
    @Bean
    public ExpenseAuditService expenseAuditService(
            RedisTemplate<String, Object> expenseRedisTemplate) {
        return new ExpenseAuditService(expenseRedisTemplate);
    }

    // Inner classes for expense service components
    
    /**
     * Response error handler for expense service HTTP calls
     */
    public static class ExpenseServiceResponseErrorHandler 
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
            
            log.error("Expense service HTTP error: {} - {}", statusCode, statusText);
            
            if (statusCode.is4xxClientError()) {
                throw new ExpenseServiceClientException("Client error: " + statusText);
            } else if (statusCode.is5xxServerError()) {
                throw new ExpenseServiceServerException("Server error: " + statusText);
            }
        }
    }

    /**
     * Logging interceptor for expense service requests
     */
    public static class ExpenseServiceLoggingInterceptor 
            implements org.springframework.http.client.ClientHttpRequestInterceptor {
        
        @Override
        public org.springframework.http.client.ClientHttpResponse intercept(
                org.springframework.http.HttpRequest request,
                byte[] body,
                org.springframework.http.client.ClientHttpRequestExecution execution) 
                throws java.io.IOException {
            
            long startTime = System.currentTimeMillis();
            log.debug("Expense service request: {} {}", request.getMethod(), request.getURI());
            
            org.springframework.http.client.ClientHttpResponse response = execution.execute(request, body);
            
            long endTime = System.currentTimeMillis();
            log.debug("Expense service response: {} in {}ms", 
                     response.getStatusCode(), (endTime - startTime));
            
            return response;
        }
    }

    /**
     * Authentication interceptor for expense service requests
     */
    public static class ExpenseServiceAuthInterceptor 
            implements org.springframework.http.client.ClientHttpRequestInterceptor {
        
        @Value("${expense.service.api.key:}")
        private String apiKey;
        
        @Override
        public org.springframework.http.client.ClientHttpResponse intercept(
                org.springframework.http.HttpRequest request,
                byte[] body,
                org.springframework.http.client.ClientHttpRequestExecution execution) 
                throws java.io.IOException {
            
            if (apiKey != null && !apiKey.isEmpty()) {
                request.getHeaders().set("Authorization", "Bearer " + apiKey);
            }
            
            request.getHeaders().set("User-Agent", "Waqiti-Expense-Service/1.0");
            request.getHeaders().set("Accept", "application/json");
            request.getHeaders().set("Content-Type", "application/json");
            
            return execution.execute(request, body);
        }
    }

    // Placeholder classes for expense service components
    // These would be implemented as separate files in a real application
    
    public static class ExpenseCategoriesCache {
        public ExpenseCategoriesCache(RedisTemplate<String, Object> redisTemplate, Duration ttl) {
            // Implementation would go here
        }
    }
    
    public static class ExpenseAnalyticsCache {
        public ExpenseAnalyticsCache(RedisTemplate<String, Object> redisTemplate, Duration ttl) {
            // Implementation would go here
        }
    }
    
    public static class ReceiptProcessingService {
        public ReceiptProcessingService(ResilientServiceExecutor resilientExecutor, boolean enabled) {
            // Implementation would go here
        }
    }
    
    public static class MLExpenseCategorizationService {
        public MLExpenseCategorizationService(ResilientServiceExecutor resilientExecutor, 
                                             ExpenseCategoriesCache categoriesCache, 
                                             boolean enabled) {
            // Implementation would go here
        }
    }
    
    public static class ExpenseAnalyticsService {
        public ExpenseAnalyticsService(ExpenseAnalyticsCache analyticsCache, 
                                      ResilientServiceExecutor resilientExecutor) {
            // Implementation would go here
        }
    }
    
    public static class ExpenseBudgetService {
        public ExpenseBudgetService(RedisTemplate<String, Object> redisTemplate, 
                                   ResilientServiceExecutor resilientExecutor) {
            // Implementation would go here
        }
    }
    
    public static class ExpenseReportService {
        public ExpenseReportService(ExpenseAnalyticsService analyticsService,
                                   ExpenseCategoriesCache categoriesCache,
                                   ResilientServiceExecutor resilientExecutor) {
            // Implementation would go here
        }
    }
    
    public static class ExpenseNotificationService {
        public ExpenseNotificationService(ResilientServiceExecutor resilientExecutor) {
            // Implementation would go here
        }
    }
    
    public static class ExpenseServiceMetrics {
        // Metrics implementation would go here
    }
    
    public static class ExpenseServiceHealthIndicator {
        public ExpenseServiceHealthIndicator(ExpenseCategoriesCache categoriesCache,
                                            ReceiptProcessingService receiptProcessingService,
                                            ResilientServiceExecutor resilientExecutor) {
            // Health check implementation would go here
        }
    }
    
    public static class ExpenseAuditService {
        public ExpenseAuditService(RedisTemplate<String, Object> redisTemplate) {
            // Audit implementation would go here
        }
    }

    // Exception classes
    public static class ExpenseServiceClientException extends RuntimeException {
        public ExpenseServiceClientException(String message) {
            super(message);
        }
    }

    public static class ExpenseServiceServerException extends RuntimeException {
        public ExpenseServiceServerException(String message) {
            super(message);
        }
    }
}