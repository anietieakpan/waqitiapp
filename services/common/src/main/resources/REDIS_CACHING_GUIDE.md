# Redis Caching Strategy and Implementation Guide

## Overview
This guide outlines the comprehensive Redis caching strategy implemented across all Waqiti microservices, including cache warming, performance optimization, and best practices.

## Architecture

### Cache Layers
1. **L1 Cache**: Application-level in-memory cache (minimal)
2. **L2 Cache**: Redis distributed cache (primary)
3. **L3 Cache**: Database with optimized queries

### Cache Types by Service

#### User Service
- **users**: User profiles and basic information (30 min TTL)
- **userPreferences**: User settings and preferences (2 hours TTL)
- **authTokens**: Authentication tokens and sessions (15 min TTL)
- **permissions**: User roles and permissions (30 min TTL)

#### Payment Service
- **exchangeRates**: FX rates for currency conversion (5 min TTL)
- **paymentMethods**: Payment method configurations (1 hour TTL)
- **merchantConfigs**: Merchant-specific settings (30 min TTL)
- **fraudRules**: Fraud detection rules (15 min TTL)

#### Global/System-wide
- **countries**: Country reference data (6 hours TTL)
- **currencies**: Currency reference data (6 hours TTL)
- **featureFlags**: Feature toggles (10 min TTL)
- **systemConfig**: System-wide configuration (1 hour TTL)

## Cache Warming Strategy

### Startup Warming
Cache warming occurs automatically on application startup:

```yaml
cache:
  warmup:
    enabled: true
    on-startup: true
    batch-size: 100
```

#### Warming Priorities
1. **Priority 0** (Highest): Global system data
2. **Priority 1**: User data and authentication
3. **Priority 2**: Payment and transaction data
4. **Priority 3**: Analytics and reporting data

### Runtime Warming
- **Scheduled refresh**: Every 30 minutes
- **Demand-based warming**: Based on access patterns
- **Event-driven warming**: On configuration changes

## Implementation Examples

### Basic Caching
```java
@Service
public class UserService {
    
    @Cacheable(value = "users", key = "#userId")
    public User getUserById(String userId) {
        return userRepository.findById(userId);
    }
    
    @CacheEvict(value = "users", key = "#user.id")
    public User updateUser(User user) {
        return userRepository.save(user);
    }
}
```

### Advanced Caching with Conditional Logic
```java
@Service
public class PaymentService {
    
    @Cacheable(value = "exchangeRates", 
               key = "#sourceCurrency + '-' + #targetCurrency",
               condition = "#amount > 100", // Only cache for amounts > 100
               unless = "#result.rate == null") // Don't cache null rates
    public ExchangeRate getExchangeRate(String sourceCurrency, String targetCurrency, BigDecimal amount) {
        return exchangeRateProvider.getRate(sourceCurrency, targetCurrency);
    }
}
```

### Cache Warming Implementation
```java
@Component
public class UserCacheWarmer implements CacheWarmupService.CacheWarmer {
    
    @Override
    public void warmup() {
        // Get active users from analytics
        List<String> activeUsers = getActiveUserIds();
        
        // Warm cache in batches
        for (String userId : activeUsers) {
            userService.getUserById(userId); // Populates cache
            Thread.sleep(10); // Avoid overwhelming DB
        }
    }
    
    @Override
    public int getPriority() {
        return 1; // High priority
    }
}
```

## Configuration by Environment

### Development
```yaml
cache:
  redis:
    default-ttl: PT5M  # Short TTL for testing
  warmup:
    enabled: false     # Disable warming in dev
```

### Staging
```yaml
cache:
  redis:
    default-ttl: PT30M # Medium TTL
  warmup:
    enabled: true
    batch-size: 50     # Smaller batches
```

### Production
```yaml
cache:
  redis:
    default-ttl: PT1H  # Full TTL
  warmup:
    enabled: true
    batch-size: 100    # Full batch size
```

## Performance Optimization

### Key Design Patterns

#### 1. Hierarchical Keys
```
service:cache_type:identifier

Examples:
user-service:users:12345
payment-service:rates:USD-EUR
global:countries:US
```

#### 2. Batch Operations
```java
// Batch get multiple cache entries
List<String> keys = users.stream()
    .map(user -> "users:" + user.getId())
    .collect(Collectors.toList());
    
List<Object> cachedUsers = redisTemplate.opsForValue().multiGet(keys);
```

#### 3. Pipeline Operations
```java
// Use pipeline for multiple operations
redisTemplate.executePipelined(new RedisCallback<Object>() {
    public Object doInRedis(RedisConnection connection) {
        for (User user : users) {
            connection.set(
                ("users:" + user.getId()).getBytes(),
                serialize(user)
            );
        }
        return null;
    }
});
```

### Memory Optimization

#### Data Compression
```java
@Configuration
public class RedisConfig {
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        
        // Use compressed JSON serialization
        GenericJackson2JsonRedisSerializer serializer = 
            new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(serializer);
        
        return template;
    }
}
```

#### Selective Caching
```java
// Only cache expensive operations
@Cacheable(value = "reports", condition = "#complexity > 5")
public Report generateReport(ReportRequest request) {
    // Only cache complex reports
}
```

## Monitoring and Metrics

### Key Metrics
- **Hit Rate**: Target > 80% for critical caches
- **Miss Rate**: Should be < 20%
- **Eviction Rate**: Should be minimal
- **Memory Usage**: Monitor Redis memory consumption
- **Response Time**: Cache operations should be < 5ms

### Alerting Thresholds
```yaml
alers:
  cache_hit_rate_low:
    threshold: 70%
    severity: warning
    
  cache_memory_high:
    threshold: 80%
    severity: critical
    
  cache_response_time_high:
    threshold: 10ms
    severity: warning
```

### Prometheus Metrics
```java
@Component
public class CacheMetrics {
    
    private final Counter cacheHits = Counter.builder("cache_hits_total")
        .tag("cache", "")
        .register(Metrics.globalRegistry);
        
    private final Timer cacheOperationTime = Timer.builder("cache_operation_duration")
        .register(Metrics.globalRegistry);
}
```

## Best Practices

### Cache Key Design
1. **Use consistent naming**: `service:type:identifier`
2. **Avoid special characters**: Use alphanumeric and colons/hyphens only
3. **Keep keys short**: Long keys consume more memory
4. **Use versioning**: Include version in key for schema changes

### TTL Management
1. **Set appropriate TTLs**: Based on data volatility
2. **Use different TTLs**: For different types of data
3. **Avoid eternal caches**: Always set TTL
4. **Consider cache warming**: For frequently accessed data

### Error Handling
1. **Graceful degradation**: Application should work without cache
2. **Log cache errors**: But don't fail operations
3. **Circuit breaker**: For cache operations
4. **Fallback mechanism**: Direct database access

### Security Considerations
1. **Encrypt sensitive data**: Before caching
2. **Use Redis AUTH**: In production
3. **Network security**: Use TLS for Redis connections
4. **Access control**: Limit Redis access to authorized services

## Troubleshooting

### Common Issues

#### Cache Miss Rate Too High
```bash
# Check cache configuration
redis-cli INFO stats

# Monitor key expiration
redis-cli MONITOR

# Check TTL settings
redis-cli TTL cache_key
```

**Solutions:**
- Increase TTL for stable data
- Implement cache warming
- Check key generation logic

#### Memory Usage Too High
```bash
# Check memory usage
redis-cli INFO memory

# Analyze key distribution
redis-cli --bigkeys

# Check expiration policy
redis-cli CONFIG GET maxmemory-policy
```

**Solutions:**
- Implement LRU eviction
- Reduce TTL for less critical data
- Use data compression
- Consider cache partitioning

#### Slow Cache Operations
```bash
# Check slow queries
redis-cli SLOWLOG GET 10

# Monitor latency
redis-cli --latency

# Check connection pool
redis-cli INFO clients
```

**Solutions:**
- Optimize key structure
- Use pipeline operations
- Increase connection pool size
- Consider Redis clustering

## Cache Invalidation Strategies

### Time-based Invalidation
```java
@Scheduled(fixedRate = 300000) // 5 minutes
public void refreshCriticalCaches() {
    cacheManager.getCache("exchangeRates").clear();
    // Trigger cache warming
    exchangeRateService.warmupRates();
}
```

### Event-based Invalidation
```java
@EventListener
public void handleUserUpdate(UserUpdatedEvent event) {
    cacheManager.getCache("users").evict(event.getUserId());
    cacheManager.getCache("userPreferences").evict(event.getUserId());
}
```

### Conditional Invalidation
```java
@CacheEvict(value = "reports", 
            condition = "#result.isSignificantChange()")
public Report updateReport(Report report) {
    return reportRepository.save(report);
}
```

## Testing Cache Implementation

### Unit Tests
```java
@ExtendWith(SpringExtension.class)
@EnableCaching
class CacheTest {
    
    @MockBean
    private UserRepository userRepository;
    
    @Test
    void shouldCacheUserOnFirstCall() {
        // Given
        User user = new User("123", "John");
        when(userRepository.findById("123")).thenReturn(user);
        
        // When - First call
        userService.getUserById("123");
        
        // Then - Should hit database
        verify(userRepository, times(1)).findById("123");
        
        // When - Second call
        userService.getUserById("123");
        
        // Then - Should use cache, no additional DB call
        verify(userRepository, times(1)).findById("123");
    }
}
```

### Integration Tests
```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.cache.type=redis",
    "spring.redis.host=localhost",
    "spring.redis.port=6379"
})
class CacheIntegrationTest {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Test
    void shouldWarmCacheOnStartup() {
        // Verify cache warming worked
        assertThat(redisTemplate.hasKey("global:countries:US")).isTrue();
        assertThat(redisTemplate.hasKey("global:currencies:USD")).isTrue();
    }
}
```

## Deployment Considerations

### Redis Configuration
```redis
# redis.conf for production
maxmemory 4gb
maxmemory-policy allkeys-lru
save 900 1
save 300 10
save 60 10000
appendonly yes
appendfsync everysec
```

### Kubernetes Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: redis-cache
spec:
  replicas: 1
  selector:
    matchLabels:
      app: redis-cache
  template:
    metadata:
      labels:
        app: redis-cache
    spec:
      containers:
      - name: redis
        image: redis:7-alpine
        ports:
        - containerPort: 6379
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "4Gi"
            cpu: "1000m"
        volumeMounts:
        - name: redis-data
          mountPath: /data
      volumes:
      - name: redis-data
        persistentVolumeClaim:
          claimName: redis-pvc
```

## Cache Eviction Strategies

### Event-Driven Eviction
Cache eviction automatically triggered by Kafka events:

```java
@KafkaListener(topics = "user.updated")
public void handleUserUpdated(String userId) {
    evictUserRelatedCaches(userId);
}
```

### Scheduled Eviction
- **Every 15 minutes**: Stale analytics and expired sessions
- **Every 10 minutes**: Low hit rate caches (< 30%)
- **Every 5 minutes**: Memory pressure checks (> 80% usage)

### Memory-Pressure Eviction
Automatic eviction when Redis memory usage exceeds 80%:
1. Evict analytics and reporting caches first
2. Evict audit logs and system logs
3. Preserve user and transaction caches

## Testing Framework

### Performance Testing
```java
@Service
@ConditionalOnProperty(name = "cache.testing.enabled", havingValue = "true")
public class RedisCacheTestService {
    
    public void testCacheWarmingPerformance() {
        // Test cache warming with 1000 keys in batches
    }
    
    public void testCachePerformanceUnderLoad() {
        // Test 10,000 operations across 10 concurrent threads
    }
}
```

### Enable Testing (Development Only)
```yaml
cache:
  testing:
    enabled: true
```

## Production Deployment

### Redis Cluster Configuration
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: redis-cluster
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: redis
        image: redis:7-alpine
        command: ["redis-server", "/etc/redis/redis.conf", "--cluster-enabled", "yes"]
```

### Monitoring Setup
```yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      cache.enabled: true
```

This comprehensive Redis caching strategy provides:
- **High Performance**: Sub-5ms cache operations
- **Scalability**: Handles high-volume requests with clustering
- **Reliability**: Graceful degradation when cache is unavailable
- **Monitoring**: Complete visibility into cache performance
- **Flexibility**: Easy to tune per service requirements
- **Automated Management**: Event-driven and scheduled eviction
- **Testing Framework**: Performance and load testing capabilities