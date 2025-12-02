package com.waqiti.chaos.config;

import de.codecentric.chaos.monkey.configuration.ChaosMonkeySettings;
import de.codecentric.chaos.monkey.configuration.WatcherProperties;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChaosConfig {
    
    @Value("${toxiproxy.host:localhost}")
    private String toxiproxyHost;
    
    @Value("${toxiproxy.port:8474}")
    private int toxiproxyPort;
    
    @Bean
    public ToxiproxyClient toxiproxyClient() {
        return new ToxiproxyClient(toxiproxyHost, toxiproxyPort);
    }
    
    @Bean
    public KubernetesClient kubernetesClient() {
        return new DefaultKubernetesClient();
    }
    
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return CircuitBreakerRegistry.ofDefaults();
    }
    
    @Bean
    public RetryRegistry retryRegistry() {
        return RetryRegistry.ofDefaults();
    }
    
    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        return RateLimiterRegistry.ofDefaults();
    }
    
    @Bean
    public BulkheadRegistry bulkheadRegistry() {
        return BulkheadRegistry.ofDefaults();
    }
    
    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        return TimeLimiterRegistry.ofDefaults();
    }
    
    @Bean
    public ChaosMonkeySettings chaosMonkeySettings() {
        ChaosMonkeySettings settings = new ChaosMonkeySettings();
        
        // Configure Chaos Monkey
        settings.setEnabled(true);
        
        // Configure watchers
        WatcherProperties watchers = new WatcherProperties();
        watchers.setController(true);
        watchers.setService(true);
        watchers.setRepository(true);
        watchers.setComponent(true);
        settings.setWatcherProperties(watchers);
        
        return settings;
    }
}