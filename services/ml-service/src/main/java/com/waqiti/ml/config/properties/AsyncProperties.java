package com.waqiti.ml.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Async executor configuration properties for ML service
 * Provides comprehensive configuration for thread pools and async processing
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "async")
public class AsyncProperties {
    
    /**
     * Core pool size for async executor
     * This is the number of threads to keep in the pool, even if they are idle
     */
    private int corePoolSize = 5;
    
    /**
     * Maximum pool size for async executor
     * This is the maximum number of threads to allow in the pool
     */
    private int maxPoolSize = 20;
    
    /**
     * Queue capacity for async executor
     * The capacity of the BlockingQueue before new threads are created
     */
    private int queueCapacity = 1000;
    
    /**
     * Thread name prefix for async executor threads
     * Helps identify threads in logs and monitoring
     */
    private String threadNamePrefix = "ml-async-";
    
    /**
     * Keep alive time in seconds for idle threads
     * Threads idle for this duration will be terminated if pool size > core size
     */
    private int keepAliveSeconds = 60;
    
    /**
     * Allow core thread timeout
     * If true, core threads can timeout and be terminated
     */
    private boolean allowCoreThreadTimeout = false;
    
    /**
     * Wait for tasks to complete on shutdown
     * If true, executor will wait for tasks to complete before shutdown
     */
    private boolean waitForTasksToCompleteOnShutdown = true;
    
    /**
     * Await termination seconds
     * Maximum time to wait for tasks to complete on shutdown
     */
    private int awaitTerminationSeconds = 60;
    
    /**
     * Thread priority for async threads
     * Priority from Thread.MIN_PRIORITY (1) to Thread.MAX_PRIORITY (10)
     */
    private int threadPriority = Thread.NORM_PRIORITY;
    
    /**
     * Daemon threads
     * If true, threads will be daemon threads
     */
    private boolean daemon = false;
    
    /**
     * Task decorator bean name
     * Bean name of TaskDecorator to apply to all tasks
     */
    private String taskDecoratorBeanName;
    
    /**
     * Rejection policy
     * Policy for handling tasks that cannot be executed
     */
    private RejectionPolicy rejectionPolicy = RejectionPolicy.CALLER_RUNS;
    
    /**
     * Thread group name
     * Name of the ThreadGroup for all threads
     */
    private String threadGroupName = "ml-async-group";
    
    /**
     * Pre-start all core threads
     * If true, all core threads will be started immediately
     */
    private boolean preStartAllCoreThreads = false;
    
    /**
     * Task timeout in milliseconds
     * Maximum time allowed for a single task execution
     */
    private long taskTimeoutMs = 30000; // 30 seconds
    
    /**
     * Enable metrics collection
     * If true, detailed metrics will be collected for async operations
     */
    private boolean metricsEnabled = true;
    
    /**
     * Enable MDC (Mapped Diagnostic Context) propagation
     * If true, MDC context will be propagated to async threads
     */
    private boolean mdcPropagationEnabled = true;
    
    /**
     * Rejection policy enum
     */
    public enum RejectionPolicy {
        /**
         * Runs the rejected task in the calling thread
         */
        CALLER_RUNS,
        
        /**
         * Aborts the task and throws RejectedExecutionException
         */
        ABORT,
        
        /**
         * Discards the task silently
         */
        DISCARD,
        
        /**
         * Discards the oldest unhandled task and retries
         */
        DISCARD_OLDEST
    }
    
    /**
     * Validates the configuration
     */
    public void validate() {
        if (corePoolSize < 0) {
            throw new IllegalArgumentException("Core pool size must be non-negative");
        }
        if (maxPoolSize <= 0) {
            throw new IllegalArgumentException("Max pool size must be positive");
        }
        if (maxPoolSize < corePoolSize) {
            throw new IllegalArgumentException("Max pool size must be >= core pool size");
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException("Queue capacity must be non-negative");
        }
        if (keepAliveSeconds < 0) {
            throw new IllegalArgumentException("Keep alive seconds must be non-negative");
        }
        if (awaitTerminationSeconds < 0) {
            throw new IllegalArgumentException("Await termination seconds must be non-negative");
        }
        if (threadPriority < Thread.MIN_PRIORITY || threadPriority > Thread.MAX_PRIORITY) {
            throw new IllegalArgumentException("Thread priority must be between " + 
                Thread.MIN_PRIORITY + " and " + Thread.MAX_PRIORITY);
        }
        if (taskTimeoutMs < 0) {
            throw new IllegalArgumentException("Task timeout must be non-negative");
        }
    }
    
    /**
     * Gets the total capacity of the executor
     * @return sum of max pool size and queue capacity
     */
    public int getTotalCapacity() {
        return maxPoolSize + queueCapacity;
    }
    
    /**
     * Checks if the configuration is for a fixed thread pool
     * @return true if core pool size equals max pool size
     */
    public boolean isFixedThreadPool() {
        return corePoolSize == maxPoolSize;
    }
    
    /**
     * Gets optimal pool size based on available processors
     * @return recommended pool size
     */
    public int getOptimalPoolSize() {
        int processors = Runtime.getRuntime().availableProcessors();
        return Math.max(corePoolSize, Math.min(processors * 2, maxPoolSize));
    }
}