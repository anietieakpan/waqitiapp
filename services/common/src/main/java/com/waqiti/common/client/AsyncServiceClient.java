package com.waqiti.common.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Async service client with circuit breaker, retry, and caching
 */
@Component
@Slf4j
public class AsyncServiceClient {
    
    private final WebClient.Builder webClientBuilder;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final ExecutorService executorService;
    private final CacheManager cacheManager;
    
    public AsyncServiceClient(WebClient.Builder webClientBuilder, CacheManager cacheManager) {
        this.webClientBuilder = webClientBuilder;
        this.cacheManager = cacheManager;
        
        // Configure circuit breaker
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofMillis(10000))
            .permittedNumberOfCallsInHalfOpenState(3)
            .slidingWindowSize(10)
            .recordExceptions(Exception.class)
            .build();
        
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        
        // Configure retry
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .retryExceptions(Exception.class)
            .build();
        
        this.retryRegistry = RetryRegistry.of(retryConfig);
        
        // Configure thread pool
        this.executorService = new ThreadPoolExecutor(
            10, 50,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("async-client-" + counter++);
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
    
    /**
     * Async GET request with circuit breaker and retry
     */
    public <T> CompletableFuture<T> getAsync(String serviceName, String url, 
                                            Class<T> responseType, 
                                            Map<String, String> headers) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(serviceName);
        Retry retry = retryRegistry.retry(serviceName);
        
        WebClient webClient = webClientBuilder
            .baseUrl(url)
            .defaultHeaders(httpHeaders -> {
                if (headers != null) {
                    headers.forEach(httpHeaders::add);
                }
            })
            .build();
        
        Supplier<Mono<T>> supplier = () -> webClient
            .get()
            .retrieve()
            .bodyToMono(responseType)
            .timeout(Duration.ofSeconds(5));
        
        return supplier.get()
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .transformDeferred(RetryOperator.of(retry))
            .toFuture();
    }
    
    /**
     * Async POST request with circuit breaker and retry
     */
    public <T, R> CompletableFuture<R> postAsync(String serviceName, String url,
                                                 T requestBody,
                                                 Class<R> responseType,
                                                 Map<String, String> headers) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(serviceName);
        Retry retry = retryRegistry.retry(serviceName);
        
        WebClient webClient = webClientBuilder
            .baseUrl(url)
            .defaultHeaders(httpHeaders -> {
                httpHeaders.add("Content-Type", "application/json");
                if (headers != null) {
                    headers.forEach(httpHeaders::add);
                }
            })
            .build();
        
        return webClient
            .post()
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(responseType)
            .timeout(Duration.ofSeconds(5))
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .transformDeferred(RetryOperator.of(retry))
            .subscribeOn(Schedulers.boundedElastic())
            .toFuture();
    }
    
    /**
     * Batch GET requests with parallel execution
     */
    public <T> CompletableFuture<List<T>> batchGetAsync(String serviceName,
                                                        List<String> urls,
                                                        Class<T> responseType,
                                                        Map<String, String> headers) {
        List<CompletableFuture<T>> futures = urls.stream()
            .map(url -> getAsync(serviceName, url, responseType, headers))
            .collect(Collectors.toList());
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()));
    }
    
    /**
     * Cached async GET request
     */
    @Cacheable(value = "service-responses", key = "#url", unless = "#result == null")
    public <T> CompletableFuture<T> getCachedAsync(String serviceName, String url,
                                                   Class<T> responseType,
                                                   Map<String, String> headers) {
        // Check cache first
        var cache = cacheManager.getCache("service-responses");
        if (cache != null) {
            var cached = cache.get(url, responseType);
            if (cached != null) {
                return CompletableFuture.completedFuture(cached);
            }
        }
        
        // If not in cache, fetch async
        return getAsync(serviceName, url, responseType, headers);
    }
    
    /**
     * Health check for a service
     */
    public CompletableFuture<Boolean> healthCheck(String serviceName, String healthUrl) {
        return getAsync(serviceName, healthUrl, String.class, null)
            .thenApply(response -> true)
            .exceptionally(ex -> {
                log.warn("Health check failed for service: {}", serviceName, ex);
                return false;
            });
    }
    
    /**
     * Execute with timeout and fallback
     */
    public <T> CompletableFuture<T> executeWithFallback(Supplier<CompletableFuture<T>> primary,
                                                       Supplier<T> fallback,
                                                       Duration timeout) {
        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
            .timeoutDuration(timeout)
            .build();
        
        TimeLimiter timeLimiter = TimeLimiter.of(timeLimiterConfig);
        
        ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);
        
        return timeLimiter.executeCompletionStage(
            scheduledExecutor,
            primary
        ).toCompletableFuture()
        .exceptionally(throwable -> {
            log.warn("Primary execution failed, using fallback", throwable);
            return fallback.get();
        });
    }
    
    /**
     * Parallel execution with aggregation
     */
    public <T, R> CompletableFuture<R> parallelAggregate(List<Supplier<CompletableFuture<T>>> tasks,
                                                         Function<List<T>, R> aggregator) {
        List<CompletableFuture<T>> futures = tasks.stream()
            .map(task -> CompletableFuture.supplyAsync(
                () -> task.get().join(), executorService))
            .collect(Collectors.toList());
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                List<T> results = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
                return aggregator.apply(results);
            });
    }
    
    /**
     * Shutdown cleanup
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // No need for helper classes as we use the official resilience4j reactor operators
}