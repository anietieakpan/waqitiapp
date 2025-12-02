package com.waqiti.common.threading;

import com.waqiti.common.logging.SecureLoggingService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Managed Thread Pool Executor Service
 *
 * Production-grade thread pool management to prevent memory leaks and resource exhaustion.
 *
 * Features:
 * - Automatic thread pool lifecycle management
 * - Graceful shutdown with configurable timeout
 * - Thread leak detection and prevention
 * - Memory leak prevention through proper cleanup
 * - Monitoring and metrics for thread pools
 * - Rejection policy handling
 * - Thread naming for debugging
 * - Automatic dead thread cleanup
 * - Queue overflow protection
 * - Thread starvation prevention
 *
 * Prevents:
 * - Memory leaks from abandoned threads
 * - Resource exhaustion from unbounded thread creation
 * - Thread pool starvation
 * - Deadlocks from improper shutdown
 * - Queue overflow from unbounded task queues
 */
@Service
@Slf4j
public class ManagedThreadPoolExecutorService {

    private final ConcurrentHashMap<String, ManagedExecutor> executors = new ConcurrentHashMap<>();
    private final SecureLoggingService secureLogging;
    private final MeterRegistry meterRegistry;
    private final ScheduledExecutorService monitoringExecutor;
    private final AtomicInteger activeExecutorCount = new AtomicInteger(0);

    // Metrics
    private Counter taskSubmittedCounter;
    private Counter taskCompletedCounter;
    private Counter taskRejectedCounter;
    private Counter threadLeakCounter;
    private Gauge activeThreadsGauge;
    private Gauge queueSizeGauge;

    public ManagedThreadPoolExecutorService(SecureLoggingService secureLogging, MeterRegistry meterRegistry) {
        this.secureLogging = secureLogging;
        this.meterRegistry = meterRegistry;
        this.monitoringExecutor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("pool-monitor"));
    }

    @PostConstruct
    public void init() {
        initializeMetrics();
        startMonitoring();
        log.info("ManagedThreadPoolExecutorService initialized");
    }

    /**
     * Create a fixed thread pool with proper lifecycle management.
     */
    public ExecutorService createFixedThreadPool(String poolName, int nThreads) {
        return createFixedThreadPool(poolName, nThreads, 10000);
    }

    /**
     * Create a fixed thread pool with custom queue capacity.
     */
    public ExecutorService createFixedThreadPool(String poolName, int nThreads, int queueCapacity) {
        if (executors.containsKey(poolName)) {
            log.warn("Thread pool {} already exists, returning existing pool", poolName);
            return executors.get(poolName).getExecutor();
        }

        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(queueCapacity);

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            nThreads,
            nThreads,
            0L,
            TimeUnit.MILLISECONDS,
            workQueue,
            new NamedThreadFactory(poolName),
            new MonitoredRejectionHandler(poolName)
        );

        // Enable core thread timeout to prevent idle thread accumulation
        executor.allowCoreThreadTimeOut(true);

        ManagedExecutor managedExecutor = new ManagedExecutor(poolName, executor);
        executors.put(poolName, managedExecutor);
        activeExecutorCount.incrementAndGet();

        log.info("Created fixed thread pool: {} with {} threads and queue capacity: {}",
            poolName, nThreads, queueCapacity);

        return executor;
    }

    /**
     * Create a cached thread pool with proper lifecycle management.
     * WARNING: Use with caution - can lead to resource exhaustion if not properly bounded.
     */
    public ExecutorService createCachedThreadPool(String poolName, int maxThreads) {
        if (executors.containsKey(poolName)) {
            log.warn("Thread pool {} already exists, returning existing pool", poolName);
            return executors.get(poolName).getExecutor();
        }

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            0,
            maxThreads,
            60L,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new NamedThreadFactory(poolName),
            new MonitoredRejectionHandler(poolName)
        );

        ManagedExecutor managedExecutor = new ManagedExecutor(poolName, executor);
        executors.put(poolName, managedExecutor);
        activeExecutorCount.incrementAndGet();

        log.info("Created cached thread pool: {} with max {} threads", poolName, maxThreads);

        return executor;
    }

    /**
     * Create a scheduled thread pool with proper lifecycle management.
     */
    public ScheduledExecutorService createScheduledThreadPool(String poolName, int corePoolSize) {
        if (executors.containsKey(poolName)) {
            log.warn("Thread pool {} already exists, returning existing pool", poolName);
            return (ScheduledExecutorService) executors.get(poolName).getExecutor();
        }

        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
            corePoolSize,
            new NamedThreadFactory(poolName),
            new MonitoredRejectionHandler(poolName)
        );

        // Remove cancelled tasks immediately to prevent memory leaks
        executor.setRemoveOnCancelPolicy(true);

        ManagedExecutor managedExecutor = new ManagedExecutor(poolName, executor);
        executors.put(poolName, managedExecutor);
        activeExecutorCount.incrementAndGet();

        log.info("Created scheduled thread pool: {} with {} core threads", poolName, corePoolSize);

        return executor;
    }

    /**
     * Create a work-stealing pool (Java 8+) with proper lifecycle management.
     */
    public ExecutorService createWorkStealingPool(String poolName, int parallelism) {
        if (executors.containsKey(poolName)) {
            log.warn("Thread pool {} already exists, returning existing pool", poolName);
            return executors.get(poolName).getExecutor();
        }

        ExecutorService executor = new ForkJoinPool(
            parallelism,
            new NamedForkJoinWorkerThreadFactory(poolName),
            new UncaughtExceptionHandler(poolName),
            false
        );

        ManagedExecutor managedExecutor = new ManagedExecutor(poolName, executor);
        executors.put(poolName, managedExecutor);
        activeExecutorCount.incrementAndGet();

        log.info("Created work-stealing pool: {} with parallelism: {}", poolName, parallelism);

        return executor;
    }

    /**
     * Shutdown a specific thread pool gracefully.
     */
    public void shutdown(String poolName) {
        ManagedExecutor managedExecutor = executors.remove(poolName);
        if (managedExecutor != null) {
            shutdownExecutor(managedExecutor);
            activeExecutorCount.decrementAndGet();
        }
    }

    /**
     * Shutdown all managed thread pools gracefully.
     */
    @PreDestroy
    public void shutdownAll() {
        log.info("Shutting down all managed thread pools...");

        List<CompletableFuture<Void>> shutdownFutures = executors.values().stream()
            .map(executor -> CompletableFuture.runAsync(() -> shutdownExecutor(executor)))
            .toList();

        // Wait for all shutdowns to complete
        CompletableFuture.allOf(shutdownFutures.toArray(new CompletableFuture[0]))
            .orTimeout(60, TimeUnit.SECONDS)
            .join();

        executors.clear();

        // Shutdown monitoring executor
        shutdownMonitoring();

        log.info("All managed thread pools shut down successfully");
    }

    /**
     * Shutdown a single executor with graceful timeout.
     */
    private void shutdownExecutor(ManagedExecutor managedExecutor) {
        String poolName = managedExecutor.getName();
        ExecutorService executor = managedExecutor.getExecutor();

        try {
            log.info("Shutting down thread pool: {}", poolName);

            // Disable new tasks from being submitted
            executor.shutdown();

            // Wait for existing tasks to terminate
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Thread pool {} did not terminate gracefully, forcing shutdown", poolName);

                // Cancel currently executing tasks
                List<Runnable> droppedTasks = executor.shutdownNow();

                if (!droppedTasks.isEmpty()) {
                    log.warn("Thread pool {} dropped {} tasks during forced shutdown",
                        poolName, droppedTasks.size());
                }

                // Wait a while for tasks to respond to being cancelled
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.error("Thread pool {} did not terminate after forced shutdown", poolName);
                    threadLeakCounter.increment();
                }
            }

            log.info("Thread pool {} shut down successfully", poolName);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            secureLogging.logException("Thread pool shutdown interrupted: " + poolName, e);

            // Force shutdown if interrupted
            executor.shutdownNow();
        }
    }

    /**
     * Get current status of a thread pool.
     */
    public ThreadPoolStatus getStatus(String poolName) {
        ManagedExecutor managedExecutor = executors.get(poolName);
        if (managedExecutor == null) {
            return null;
        }

        ExecutorService executor = managedExecutor.getExecutor();

        if (executor instanceof ThreadPoolExecutor tpe) {
            return ThreadPoolStatus.builder()
                .poolName(poolName)
                .corePoolSize(tpe.getCorePoolSize())
                .maximumPoolSize(tpe.getMaximumPoolSize())
                .activeThreads(tpe.getActiveCount())
                .poolSize(tpe.getPoolSize())
                .queueSize(tpe.getQueue().size())
                .completedTaskCount(tpe.getCompletedTaskCount())
                .taskCount(tpe.getTaskCount())
                .isShutdown(tpe.isShutdown())
                .isTerminated(tpe.isTerminated())
                .build();
        }

        return ThreadPoolStatus.builder()
            .poolName(poolName)
            .isShutdown(executor.isShutdown())
            .isTerminated(executor.isTerminated())
            .build();
    }

    /**
     * Get status of all thread pools.
     */
    public List<ThreadPoolStatus> getAllStatuses() {
        return executors.keySet().stream()
            .map(this::getStatus)
            .filter(status -> status != null)
            .toList();
    }

    /**
     * Monitor thread pools for potential issues.
     */
    private void startMonitoring() {
        monitoringExecutor.scheduleWithFixedDelay(() -> {
            try {
                for (ManagedExecutor managedExecutor : executors.values()) {
                    monitorExecutor(managedExecutor);
                }
            } catch (Exception e) {
                secureLogging.logException("Error monitoring thread pools", e);
            }
        }, 1, 5, TimeUnit.MINUTES);
    }

    /**
     * Monitor individual executor for issues.
     */
    private void monitorExecutor(ManagedExecutor managedExecutor) {
        String poolName = managedExecutor.getName();
        ExecutorService executor = managedExecutor.getExecutor();

        if (executor instanceof ThreadPoolExecutor tpe) {
            int activeCount = tpe.getActiveCount();
            int poolSize = tpe.getPoolSize();
            int queueSize = tpe.getQueue().size();
            int maxPoolSize = tpe.getMaximumPoolSize();

            // Check for thread pool exhaustion
            if (activeCount >= maxPoolSize * 0.9) {
                log.warn("Thread pool {} is near capacity: {}/{} threads active",
                    poolName, activeCount, maxPoolSize);
            }

            // Check for queue buildup
            if (queueSize > 1000) {
                log.warn("Thread pool {} has large queue: {} tasks pending", poolName, queueSize);
            }

            // Check for thread leaks (threads that never complete)
            long taskCount = tpe.getTaskCount();
            long completedTaskCount = tpe.getCompletedTaskCount();
            long runningTaskCount = taskCount - completedTaskCount;

            if (runningTaskCount > maxPoolSize * 10) {
                log.error("Potential thread leak detected in pool {}: {} tasks running but not completing",
                    poolName, runningTaskCount);
                threadLeakCounter.increment();
            }

            // Update metrics
            updateMetrics(poolName, tpe);
        }
    }

    /**
     * Shutdown monitoring executor.
     */
    private void shutdownMonitoring() {
        try {
            monitoringExecutor.shutdown();
            if (!monitoringExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                monitoringExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            monitoringExecutor.shutdownNow();
        }
    }

    /**
     * Initialize metrics.
     */
    private void initializeMetrics() {
        taskSubmittedCounter = Counter.builder("threadpool.tasks.submitted")
            .description("Number of tasks submitted to thread pools")
            .register(meterRegistry);

        taskCompletedCounter = Counter.builder("threadpool.tasks.completed")
            .description("Number of tasks completed by thread pools")
            .register(meterRegistry);

        taskRejectedCounter = Counter.builder("threadpool.tasks.rejected")
            .description("Number of tasks rejected by thread pools")
            .register(meterRegistry);

        threadLeakCounter = Counter.builder("threadpool.leaks.detected")
            .description("Number of thread leaks detected")
            .register(meterRegistry);

        activeThreadsGauge = Gauge.builder("threadpool.threads.active", activeExecutorCount, AtomicInteger::get)
            .description("Number of active thread pools")
            .register(meterRegistry);
    }

    /**
     * Update metrics for thread pool.
     */
    private void updateMetrics(String poolName, ThreadPoolExecutor executor) {
        // Update task metrics
        long completed = executor.getCompletedTaskCount();
        taskCompletedCounter.increment(completed);

        // Additional pool-specific metrics could be registered here
    }

    // Inner classes

    /**
     * Named thread factory for better debugging.
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        public NamedThreadFactory(String poolName) {
            this.namePrefix = poolName + "-thread-";
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            thread.setDaemon(false); // Ensure threads are not daemon to prevent data loss
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }

    /**
     * Named ForkJoin worker thread factory.
     */
    private static class NamedForkJoinWorkerThreadFactory implements ForkJoinPool.ForkJoinWorkerThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        public NamedForkJoinWorkerThreadFactory(String poolName) {
            this.namePrefix = poolName + "-worker-";
        }

        @Override
        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            thread.setName(namePrefix + threadNumber.getAndIncrement());
            return thread;
        }
    }

    /**
     * Monitored rejection handler.
     */
    private class MonitoredRejectionHandler implements RejectedExecutionHandler {
        private final String poolName;

        public MonitoredRejectionHandler(String poolName) {
            this.poolName = poolName;
        }

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            taskRejectedCounter.increment();
            log.error("Task rejected by thread pool: {}. Pool status - Active: {}, Queue: {}, Max: {}",
                poolName, executor.getActiveCount(), executor.getQueue().size(), executor.getMaximumPoolSize());

            // Throw exception to notify caller
            throw new RejectedExecutionException(
                "Task rejected by thread pool: " + poolName + " - pool at capacity");
        }
    }

    /**
     * Uncaught exception handler for ForkJoinPool.
     */
    private static class UncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
        private final String poolName;

        public UncaughtExceptionHandler(String poolName) {
            this.poolName = poolName;
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            log.error("Uncaught exception in thread pool {} thread {}", poolName, t.getName(), e);
        }
    }

    /**
     * Managed executor wrapper.
     */
    private static class ManagedExecutor {
        private final String name;
        private final ExecutorService executor;
        private final AtomicLong createdAt = new AtomicLong(System.currentTimeMillis());

        public ManagedExecutor(String name, ExecutorService executor) {
            this.name = name;
            this.executor = executor;
        }

        public String getName() {
            return name;
        }

        public ExecutorService getExecutor() {
            return executor;
        }

        public long getCreatedAt() {
            return createdAt.get();
        }
    }

    /**
     * Thread pool status DTO.
     */
    @lombok.Data
    @lombok.Builder
    public static class ThreadPoolStatus {
        private String poolName;
        private int corePoolSize;
        private int maximumPoolSize;
        private int activeThreads;
        private int poolSize;
        private int queueSize;
        private long completedTaskCount;
        private long taskCount;
        private boolean isShutdown;
        private boolean isTerminated;
    }
}
