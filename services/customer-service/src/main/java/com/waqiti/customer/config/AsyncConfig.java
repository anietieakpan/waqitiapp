package com.waqiti.customer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async Execution Configuration for Customer Service.
 * Configures thread pool for @Async methods with custom error handling,
 * rejection policy, and thread naming.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    @Value("${async.executor.core-pool-size:5}")
    private int corePoolSize;

    @Value("${async.executor.max-pool-size:20}")
    private int maxPoolSize;

    @Value("${async.executor.queue-capacity:100}")
    private int queueCapacity;

    @Value("${async.executor.thread-name-prefix:customer-async-}")
    private String threadNamePrefix;

    @Value("${async.executor.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    /**
     * Configures the default task executor for @Async methods.
     * Uses ThreadPoolTaskExecutor with custom sizing and rejection policy.
     *
     * @return Configured TaskExecutor
     */
    @Bean(name = "taskExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(rejectedExecutionHandler());
        executor.initialize();

        log.info("Async task executor configured: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
            corePoolSize, maxPoolSize, queueCapacity);

        return executor;
    }

    /**
     * Configures a separate executor for long-running tasks.
     * Has a larger pool size and queue capacity for background processing.
     *
     * @return Configured TaskExecutor for long-running tasks
     */
    @Bean(name = "longRunningTaskExecutor")
    public Executor longRunningTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("customer-long-running-");
        executor.setKeepAliveSeconds(120);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setRejectedExecutionHandler(rejectedExecutionHandler());
        executor.initialize();

        log.info("Long-running task executor configured: corePoolSize=3, maxPoolSize=10");
        return executor;
    }

    /**
     * Configures a separate executor for event publishing.
     * Optimized for high-throughput, low-latency event processing.
     *
     * @return Configured TaskExecutor for event publishing
     */
    @Bean(name = "eventPublishingExecutor")
    public Executor eventPublishingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(15);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("customer-event-");
        executor.setKeepAliveSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(rejectedExecutionHandler());
        executor.initialize();

        log.info("Event publishing executor configured: corePoolSize=8, maxPoolSize=15");
        return executor;
    }

    /**
     * Configures custom rejected execution handler.
     * Logs rejected tasks and uses caller-runs policy as fallback.
     *
     * @return RejectedExecutionHandler
     */
    private RejectedExecutionHandler rejectedExecutionHandler() {
        return (runnable, executor) -> {
            log.warn("Task rejected by executor: activeCount={}, poolSize={}, queueSize={}",
                executor.getActiveCount(),
                executor.getPoolSize(),
                executor.getQueue().size());

            // Use caller-runs policy - run the task in the calling thread
            if (!executor.isShutdown()) {
                log.info("Executing rejected task in caller thread");
                runnable.run();
            } else {
                log.error("Executor is shutdown, task cannot be executed");
            }
        };
    }

    /**
     * Configures exception handler for uncaught async exceptions.
     * Logs exceptions that occur in @Async methods.
     *
     * @return AsyncUncaughtExceptionHandler
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            log.error("Uncaught exception in async method: method={}, params={}, exception={}",
                method.getName(),
                params,
                throwable.getMessage(),
                throwable);
        };
    }
}
