package com.waqiti.common.concurrent;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Thread-safe utilities for concurrent operations
 * Provides safe patterns for common concurrency scenarios
 */
@Slf4j
public class ConcurrencyUtils {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    
    /**
     * Executes an action with a lock
     */
    public static <T> T withLock(Lock lock, Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Executes an action with a lock (void return)
     */
    public static void withLockVoid(Lock lock, Runnable action) {
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Attempts to execute an action with a lock and timeout
     */
    public static <T> Optional<T> tryWithLock(Lock lock, Duration timeout, Supplier<T> action) {
        try {
            if (lock.tryLock(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                try {
                    return Optional.ofNullable(action.get());
                } finally {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock acquisition interrupted", e);
        }
        return Optional.empty();
    }

    /**
     * Executes read action with read-write lock
     */
    public static <T> T withReadLock(ReadWriteLock rwLock, Supplier<T> action) {
        Lock readLock = rwLock.readLock();
        readLock.lock();
        try {
            return action.get();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Executes write action with read-write lock
     */
    public static <T> T withWriteLock(ReadWriteLock rwLock, Supplier<T> action) {
        Lock writeLock = rwLock.writeLock();
        writeLock.lock();
        try {
            return action.get();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Thread-safe lazy initialization with double-checked locking
     */
    public static <T> T lazyInit(AtomicReference<T> ref, Supplier<T> supplier) {
        T value = ref.get();
        if (value == null) {
            synchronized (ref) {
                value = ref.get();
                if (value == null) {
                    value = supplier.get();
                    ref.set(value);
                }
            }
        }
        return value;
    }

    /**
     * Executes tasks in parallel with timeout
     */
    public static <T> List<T> executeParallel(
            Collection<Callable<T>> tasks,
            ExecutorService executor,
            Duration timeout) throws InterruptedException, TimeoutException {
        
        List<Future<T>> futures = executor.invokeAll(tasks, timeout.toMillis(), TimeUnit.MILLISECONDS);
        
        return futures.stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (InterruptedException | ExecutionException | CancellationException e) {
                        log.warn("Task execution failed", e);
                        return null;
                    }
                })
                .filter(result -> result != null)
                .collect(Collectors.toList());
    }

    /**
     * Retries an operation with exponential backoff
     */
    public static <T> T retryWithBackoff(
            Supplier<T> operation,
            int maxAttempts,
            Duration initialDelay,
            double backoffMultiplier) {
        
        Duration delay = initialDelay;
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    log.debug("Attempt {} failed, retrying after {} ms", attempt, delay.toMillis());
                    try {
                        Thread.sleep(delay.toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                    delay = Duration.ofMillis((long) (delay.toMillis() * backoffMultiplier));
                }
            }
        }
        
        throw new RuntimeException("All retry attempts failed", lastException);
    }

    /**
     * Thread-safe cache with TTL
     */
    public static class ThreadSafeCache<K, V> {
        private final Map<K, CacheEntry<V>> cache = new ConcurrentHashMap<>();
        private final Duration ttl;
        private final ScheduledExecutorService cleanupExecutor;
        
        public ThreadSafeCache(Duration ttl) {
            this.ttl = ttl;
            this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "cache-cleanup");
                t.setDaemon(true);
                return t;
            });
            
            // Schedule periodic cleanup
            cleanupExecutor.scheduleAtFixedRate(
                this::cleanup,
                ttl.toMillis(),
                ttl.toMillis() / 2,
                TimeUnit.MILLISECONDS
            );
        }
        
        public V get(K key) {
            CacheEntry<V> entry = cache.get(key);
            if (entry != null && !entry.isExpired()) {
                return entry.value;
            }
            cache.remove(key);
            return null;
        }
        
        public void put(K key, V value) {
            cache.put(key, new CacheEntry<>(value, System.currentTimeMillis() + ttl.toMillis()));
        }
        
        public V computeIfAbsent(K key, Function<K, V> mappingFunction) {
            CacheEntry<V> entry = cache.get(key);
            if (entry != null && !entry.isExpired()) {
                return entry.value;
            }
            
            V value = mappingFunction.apply(key);
            if (value != null) {
                put(key, value);
            }
            return value;
        }
        
        private void cleanup() {
            long now = System.currentTimeMillis();
            cache.entrySet().removeIf(entry -> entry.getValue().expiryTime < now);
        }
        
        public void shutdown() {
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cleanupExecutor.shutdownNow();
            }
        }
        
        private static class CacheEntry<V> {
            final V value;
            final long expiryTime;
            
            CacheEntry(V value, long expiryTime) {
                this.value = value;
                this.expiryTime = expiryTime;
            }
            
            boolean isExpired() {
                return System.currentTimeMillis() > expiryTime;
            }
        }
    }

    /**
     * Rate limiter implementation
     */
    public static class RateLimiter {
        private final Semaphore semaphore;
        private final int maxPermits;
        private final Duration refillPeriod;
        private final ScheduledExecutorService scheduler;
        private final AtomicBoolean shutdown = new AtomicBoolean(false);
        
        public RateLimiter(int maxPermits, Duration refillPeriod) {
            this.maxPermits = maxPermits;
            this.refillPeriod = refillPeriod;
            this.semaphore = new Semaphore(maxPermits);
            this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "rate-limiter");
                t.setDaemon(true);
                return t;
            });
            
            // Schedule periodic refill
            scheduler.scheduleAtFixedRate(
                this::refill,
                refillPeriod.toMillis(),
                refillPeriod.toMillis(),
                TimeUnit.MILLISECONDS
            );
        }
        
        public boolean tryAcquire() {
            return semaphore.tryAcquire();
        }
        
        public boolean tryAcquire(Duration timeout) throws InterruptedException {
            return semaphore.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        
        private void refill() {
            if (!shutdown.get()) {
                int permitsToAdd = maxPermits - semaphore.availablePermits();
                if (permitsToAdd > 0) {
                    semaphore.release(permitsToAdd);
                }
            }
        }
        
        public void shutdown() {
            if (shutdown.compareAndSet(false, true)) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    scheduler.shutdownNow();
                }
            }
        }
    }

    /**
     * Thread-safe counter
     */
    public static class ThreadSafeCounter {
        private final AtomicInteger count = new AtomicInteger(0);
        private final int maxValue;
        
        public ThreadSafeCounter(int maxValue) {
            this.maxValue = maxValue;
        }
        
        public int increment() {
            return count.updateAndGet(current -> 
                current >= maxValue ? 0 : current + 1
            );
        }
        
        public int decrement() {
            return count.updateAndGet(current -> 
                current <= 0 ? maxValue : current - 1
            );
        }
        
        public int get() {
            return count.get();
        }
        
        public void reset() {
            count.set(0);
        }
    }

    /**
     * Bounded executor that rejects tasks when queue is full
     */
    public static ExecutorService createBoundedExecutor(
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity,
            String threadNamePrefix) {
        
        return new ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(queueCapacity),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(0);
                
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, threadNamePrefix + "-" + counter.incrementAndGet());
                    t.setDaemon(false);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * Safely shuts down multiple executors
     */
    public static void shutdownExecutors(Duration timeout, ExecutorService... executors) {
        for (ExecutorService executor : executors) {
            if (executor != null) {
                executor.shutdown();
            }
        }
        
        for (ExecutorService executor : executors) {
            if (executor != null) {
                try {
                    if (!executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    executor.shutdownNow();
                }
            }
        }
    }
}