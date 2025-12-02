package com.waqiti.common.coordination;

import com.waqiti.common.locking.DistributedLock;
import com.waqiti.common.locking.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Distributed Coordination Service
 * 
 * Provides distributed coordination primitives for microservices:
 * - Leader election
 * - Distributed barriers
 * - Distributed semaphores
 * - Distributed queues
 * - Service coordination
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedCoordinationService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final DistributedLockService lockService;
    
    // Leader election state
    private final Map<String, LeaderElectionContext> leaderContexts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    
    // Constants
    private static final String LEADER_KEY_PREFIX = "leader:";
    private static final String BARRIER_KEY_PREFIX = "barrier:";
    private static final String SEMAPHORE_KEY_PREFIX = "semaphore:";
    private static final String QUEUE_KEY_PREFIX = "queue:";
    private static final String COUNTER_KEY_PREFIX = "counter:";
    
    /**
     * Leader Election - Elect a leader for a service group
     */
    public CompletableFuture<Boolean> electLeader(String serviceName, String instanceId, Duration leaseDuration) {
        return CompletableFuture.supplyAsync(() -> {
            String leaderKey = LEADER_KEY_PREFIX + serviceName;
            
            try {
                // Try to become leader
                Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(leaderKey, instanceId, leaseDuration);
                
                if (Boolean.TRUE.equals(acquired)) {
                    log.info("Instance {} became leader for service {}", instanceId, serviceName);
                    
                    // Setup leader context
                    LeaderElectionContext context = new LeaderElectionContext(
                        serviceName, instanceId, leaseDuration
                    );
                    leaderContexts.put(serviceName, context);
                    
                    // Schedule lease renewal
                    scheduleLeadershipRenewal(context);
                    
                    return true;
                } else {
                    log.debug("Instance {} failed to become leader for service {}", instanceId, serviceName);
                    return false;
                }
                
            } catch (Exception e) {
                log.error("Error during leader election for service {}", serviceName, e);
                return false;
            }
        });
    }
    
    /**
     * Check if current instance is the leader
     */
    public boolean isLeader(String serviceName, String instanceId) {
        String leaderKey = LEADER_KEY_PREFIX + serviceName;
        String currentLeader = redisTemplate.opsForValue().get(leaderKey);
        return instanceId.equals(currentLeader);
    }
    
    /**
     * Get current leader for a service
     */
    public Optional<String> getLeader(String serviceName) {
        String leaderKey = LEADER_KEY_PREFIX + serviceName;
        return Optional.ofNullable(redisTemplate.opsForValue().get(leaderKey));
    }
    
    /**
     * Resign from leadership
     */
    public void resignLeadership(String serviceName, String instanceId) {
        String leaderKey = LEADER_KEY_PREFIX + serviceName;
        
        // Only delete if we are the current leader
        String currentLeader = redisTemplate.opsForValue().get(leaderKey);
        if (instanceId.equals(currentLeader)) {
            redisTemplate.delete(leaderKey);
            log.info("Instance {} resigned leadership for service {}", instanceId, serviceName);
        }
        
        // Clean up context
        LeaderElectionContext context = leaderContexts.remove(serviceName);
        if (context != null) {
            context.cancelRenewal();
        }
    }
    
    /**
     * Distributed Barrier - Wait for multiple services to reach a synchronization point
     */
    public CompletableFuture<Boolean> waitAtBarrier(String barrierName, int expectedCount, Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            String barrierKey = BARRIER_KEY_PREFIX + barrierName;
            String countKey = barrierKey + ":count";
            String readyKey = barrierKey + ":ready";
            
            try {
                // Increment barrier count
                Long currentCount = redisTemplate.opsForValue().increment(countKey);
                
                if (currentCount == null) {
                    return false;
                }
                
                // Set expiration on first arrival
                if (currentCount == 1) {
                    redisTemplate.expire(countKey, timeout);
                }
                
                // Check if all expected parties have arrived
                if (currentCount >= expectedCount) {
                    // Signal barrier is ready
                    redisTemplate.opsForValue().set(readyKey, "true", timeout);
                    log.info("Barrier {} reached with {} parties", barrierName, currentCount);
                    return true;
                }
                
                // Wait for barrier to be ready
                Instant deadline = Instant.now().plus(timeout);
                while (Instant.now().isBefore(deadline)) {
                    if (Boolean.TRUE.toString().equals(redisTemplate.opsForValue().get(readyKey))) {
                        return true;
                    }
                    
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
                
                log.warn("Barrier {} timed out waiting for {} parties", barrierName, expectedCount);
                return false;
                
            } catch (Exception e) {
                log.error("Error at barrier {}", barrierName, e);
                return false;
            }
        });
    }
    
    /**
     * Distributed Semaphore - Control access to limited resources
     */
    public boolean acquireSemaphore(String semaphoreName, int permits, Duration timeout) {
        String semaphoreKey = SEMAPHORE_KEY_PREFIX + semaphoreName;
        String permitKey = semaphoreKey + ":permits";
        
        Instant deadline = Instant.now().plus(timeout);
        
        while (Instant.now().isBefore(deadline)) {
            try {
                // Use Lua script for atomic check-and-decrement
                String script = 
                    "local current = redis.call('get', KEYS[1]) " +
                    "if current == false then " +
                    "   redis.call('set', KEYS[1], ARGV[1]) " +
                    "   return ARGV[1] " +
                    "end " +
                    "current = tonumber(current) " +
                    "if current >= tonumber(ARGV[2]) then " +
                    "   redis.call('decrby', KEYS[1], ARGV[2]) " +
                    "   return current - tonumber(ARGV[2]) " +
                    "else " +
                    "   return -1 " +
                    "end";
                
                DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
                redisScript.setScriptText(script);
                redisScript.setResultType(Long.class);
                
                Long result = redisTemplate.execute(
                    redisScript,
                    Collections.singletonList(permitKey),
                    String.valueOf(permits * 10), // Initial permits if not exists
                    String.valueOf(permits)
                );
                
                if (result != null && result >= 0) {
                    log.debug("Acquired {} permits for semaphore {}", permits, semaphoreName);
                    return true;
                }
                
                // Wait before retry
                Thread.sleep(50);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                log.error("Error acquiring semaphore {}", semaphoreName, e);
                return false;
            }
        }
        
        return false;
    }
    
    /**
     * Release semaphore permits
     */
    public void releaseSemaphore(String semaphoreName, int permits) {
        String semaphoreKey = SEMAPHORE_KEY_PREFIX + semaphoreName;
        String permitKey = semaphoreKey + ":permits";
        
        try {
            redisTemplate.opsForValue().increment(permitKey, permits);
            log.debug("Released {} permits for semaphore {}", permits, semaphoreName);
        } catch (Exception e) {
            log.error("Error releasing semaphore {}", semaphoreName, e);
        }
    }
    
    /**
     * Distributed Queue - Add item to distributed queue
     */
    public void enqueue(String queueName, String item) {
        String queueKey = QUEUE_KEY_PREFIX + queueName;
        redisTemplate.opsForList().rightPush(queueKey, item);
        log.debug("Added item to queue {}", queueName);
    }
    
    /**
     * Dequeue item from distributed queue
     */
    public Optional<String> dequeue(String queueName, Duration timeout) {
        String queueKey = QUEUE_KEY_PREFIX + queueName;
        
        try {
            String item = redisTemplate.opsForList()
                .leftPop(queueKey, timeout.toMillis(), TimeUnit.MILLISECONDS);
            
            if (item != null) {
                log.debug("Dequeued item from queue {}", queueName);
            }
            
            return Optional.ofNullable(item);
            
        } catch (Exception e) {
            log.error("Error dequeuing from queue {}", queueName, e);
            return Optional.empty();
        }
    }
    
    /**
     * Get queue size
     */
    public long getQueueSize(String queueName) {
        String queueKey = QUEUE_KEY_PREFIX + queueName;
        Long size = redisTemplate.opsForList().size(queueKey);
        return size != null ? size : 0;
    }
    
    /**
     * Distributed Counter - Atomic counter operations
     */
    public long incrementCounter(String counterName) {
        return incrementCounter(counterName, 1);
    }
    
    public long incrementCounter(String counterName, long delta) {
        String counterKey = COUNTER_KEY_PREFIX + counterName;
        Long value = redisTemplate.opsForValue().increment(counterKey, delta);
        return value != null ? value : 0;
    }
    
    public long getCounter(String counterName) {
        String counterKey = COUNTER_KEY_PREFIX + counterName;
        String value = redisTemplate.opsForValue().get(counterKey);
        
        try {
            return value != null ? Long.parseLong(value) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    public void resetCounter(String counterName) {
        String counterKey = COUNTER_KEY_PREFIX + counterName;
        redisTemplate.delete(counterKey);
    }
    
    /**
     * Distributed Configuration - Share configuration across services
     */
    public void setConfiguration(String configKey, Map<String, String> config) {
        String key = "config:" + configKey;
        redisTemplate.opsForHash().putAll(key, config);
        
        // Publish configuration change event
        publishConfigurationChange(configKey);
    }
    
    public Map<Object, Object> getConfiguration(String configKey) {
        String key = "config:" + configKey;
        return redisTemplate.opsForHash().entries(key);
    }
    
    public Optional<String> getConfigurationValue(String configKey, String field) {
        String key = "config:" + configKey;
        Object value = redisTemplate.opsForHash().get(key, field);
        return Optional.ofNullable(value != null ? value.toString() : null);
    }
    
    /**
     * Service Registry - Register and discover services
     */
    public void registerService(String serviceName, String instanceId, Map<String, String> metadata) {
        String registryKey = "registry:" + serviceName;
        String instanceKey = instanceId + ":" + System.currentTimeMillis();
        
        // Store service metadata
        redisTemplate.opsForHash().put(registryKey, instanceKey, 
            serializeMetadata(metadata));
        
        // Set TTL for automatic cleanup
        redisTemplate.expire(registryKey, Duration.ofMinutes(5));
        
        log.info("Registered service instance: {} for service: {}", instanceId, serviceName);
    }
    
    public List<ServiceInstance> discoverService(String serviceName) {
        String registryKey = "registry:" + serviceName;
        Map<Object, Object> instances = redisTemplate.opsForHash().entries(registryKey);
        
        List<ServiceInstance> serviceInstances = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : instances.entrySet()) {
            String instanceKey = entry.getKey().toString();
            String[] parts = instanceKey.split(":");
            if (parts.length >= 2) {
                ServiceInstance instance = new ServiceInstance();
                instance.setInstanceId(parts[0]);
                instance.setRegistrationTime(Long.parseLong(parts[1]));
                instance.setMetadata(deserializeMetadata(entry.getValue().toString()));
                serviceInstances.add(instance);
            }
        }
        
        return serviceInstances;
    }
    
    /**
     * Distributed Task Coordination - Coordinate task execution across services
     */
    public CompletableFuture<Boolean> coordinateTask(String taskId, Supplier<Boolean> task, Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            String lockKey = "task:" + taskId;
            
            // Acquire lock for task execution
            DistributedLock lock = lockService.acquireLock(lockKey, timeout, timeout);
            if (lock == null) {
                log.warn("Could not acquire lock for task: {}", taskId);
                return false;
            }
            
            try (lock) {
                // Execute task
                boolean result = task.get();
                
                if (result) {
                    // Mark task as completed
                    String completionKey = "task:completed:" + taskId;
                    redisTemplate.opsForValue().set(completionKey, "true", Duration.ofHours(24));
                    log.info("Task {} completed successfully", taskId);
                } else {
                    log.warn("Task {} failed", taskId);
                }
                
                return result;
                
            } catch (Exception e) {
                log.error("Error executing task: {}", taskId, e);
                return false;
            }
        });
    }
    
    /**
     * Check if task has been completed
     */
    public boolean isTaskCompleted(String taskId) {
        String completionKey = "task:completed:" + taskId;
        return Boolean.TRUE.toString().equals(redisTemplate.opsForValue().get(completionKey));
    }
    
    /**
     * Rate Limiting - Distributed rate limiting for APIs
     */
    public boolean allowRequest(String identifier, int limit, Duration window) {
        String key = "ratelimit:" + identifier;
        long windowStart = System.currentTimeMillis() - window.toMillis();
        
        // Remove old entries
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
        
        // Count current requests in window
        Long count = redisTemplate.opsForZSet().count(key, windowStart, System.currentTimeMillis());
        
        if (count != null && count < limit) {
            // Add current request
            redisTemplate.opsForZSet().add(key, UUID.randomUUID().toString(), System.currentTimeMillis());
            redisTemplate.expire(key, window);
            return true;
        }
        
        return false;
    }
    
    /**
     * Get remaining rate limit
     */
    public int getRemainingLimit(String identifier, int limit, Duration window) {
        String key = "ratelimit:" + identifier;
        long windowStart = System.currentTimeMillis() - window.toMillis();
        
        Long count = redisTemplate.opsForZSet().count(key, windowStart, System.currentTimeMillis());
        return Math.max(0, limit - (count != null ? count.intValue() : 0));
    }
    
    // Private helper methods
    
    private void scheduleLeadershipRenewal(LeaderElectionContext context) {
        long renewalInterval = context.leaseDuration.toMillis() / 2;
        
        context.renewalFuture = scheduler.scheduleAtFixedRate(() -> {
            try {
                String leaderKey = LEADER_KEY_PREFIX + context.serviceName;
                String currentLeader = redisTemplate.opsForValue().get(leaderKey);
                
                if (context.instanceId.equals(currentLeader)) {
                    // Renew lease
                    redisTemplate.expire(leaderKey, context.leaseDuration);
                    log.debug("Renewed leadership for service: {}", context.serviceName);
                } else {
                    // Lost leadership
                    log.warn("Lost leadership for service: {}", context.serviceName);
                    context.cancelRenewal();
                    leaderContexts.remove(context.serviceName);
                }
                
            } catch (Exception e) {
                log.error("Error renewing leadership for service: {}", context.serviceName, e);
            }
        }, renewalInterval, renewalInterval, TimeUnit.MILLISECONDS);
    }
    
    private void publishConfigurationChange(String configKey) {
        String channel = "config:change:" + configKey;
        redisTemplate.convertAndSend(channel, "changed");
    }
    
    private String serializeMetadata(Map<String, String> metadata) {
        // Simple serialization - in production use proper JSON serialization
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }
    
    private Map<String, String> deserializeMetadata(String data) {
        Map<String, String> metadata = new HashMap<>();
        if (data != null && !data.isEmpty()) {
            String[] pairs = data.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split("=");
                if (kv.length == 2) {
                    metadata.put(kv[0], kv[1]);
                }
            }
        }
        return metadata;
    }
    
    // Cleanup scheduled tasks on shutdown
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Clean up all leader contexts
        leaderContexts.values().forEach(LeaderElectionContext::cancelRenewal);
        leaderContexts.clear();
    }
    
    // Inner classes
    
    private static class LeaderElectionContext {
        final String serviceName;
        final String instanceId;
        final Duration leaseDuration;
        ScheduledFuture<?> renewalFuture;
        
        LeaderElectionContext(String serviceName, String instanceId, Duration leaseDuration) {
            this.serviceName = serviceName;
            this.instanceId = instanceId;
            this.leaseDuration = leaseDuration;
        }
        
        void cancelRenewal() {
            if (renewalFuture != null && !renewalFuture.isDone()) {
                renewalFuture.cancel(false);
            }
        }
    }
    
    public static class ServiceInstance {
        private String instanceId;
        private long registrationTime;
        private Map<String, String> metadata;
        
        // Getters and setters
        public String getInstanceId() { return instanceId; }
        public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
        
        public long getRegistrationTime() { return registrationTime; }
        public void setRegistrationTime(long registrationTime) { this.registrationTime = registrationTime; }
        
        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    }
    
    // Distributed consensus using Raft-like algorithm (simplified)
    public static class ConsensusManager {
        private final RedisTemplate<String, String> redisTemplate;
        private final String nodeId;
        private final AtomicLong currentTerm = new AtomicLong(0);
        private final AtomicBoolean isLeader = new AtomicBoolean(false);
        
        public ConsensusManager(RedisTemplate<String, String> redisTemplate, String nodeId) {
            this.redisTemplate = redisTemplate;
            this.nodeId = nodeId;
        }
        
        public boolean proposeValue(String key, String value, Duration timeout) {
            if (!isLeader.get()) {
                return false;
            }
            
            // Simplified consensus - in production use proper Raft implementation
            String proposalKey = "consensus:proposal:" + key;
            String voteKey = "consensus:votes:" + key;
            
            // Create proposal
            Map<String, String> proposal = new HashMap<>();
            proposal.put("value", value);
            proposal.put("term", String.valueOf(currentTerm.get()));
            proposal.put("proposer", nodeId);
            
            redisTemplate.opsForHash().putAll(proposalKey, proposal);
            redisTemplate.expire(proposalKey, timeout);
            
            // Vote for own proposal
            redisTemplate.opsForSet().add(voteKey, nodeId);
            redisTemplate.expire(voteKey, timeout);
            
            // Wait for majority
            Instant deadline = Instant.now().plus(timeout);
            while (Instant.now().isBefore(deadline)) {
                Long votes = redisTemplate.opsForSet().size(voteKey);
                if (votes != null && votes > 1) { // Simplified - just need more than 1 vote
                    // Commit value
                    redisTemplate.opsForValue().set("consensus:committed:" + key, value, Duration.ofHours(24));
                    return true;
                }
                
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            
            return false;
        }
    }
}