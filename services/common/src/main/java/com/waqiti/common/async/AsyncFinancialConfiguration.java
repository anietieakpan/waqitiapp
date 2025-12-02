package com.waqiti.common.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * CRITICAL ASYNC CONFIGURATION: Thread pool configuration for financial operations
 * PRODUCTION-READY: Optimized thread pools for different types of financial operations
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncFinancialConfiguration {

    @Value("${waqiti.async.financial.core-pool-size:5}")
    private int financialCorePoolSize;

    @Value("${waqiti.async.financial.max-pool-size:20}")
    private int financialMaxPoolSize;

    @Value("${waqiti.async.financial.queue-capacity:100}")
    private int financialQueueCapacity;

    @Value("${waqiti.async.batch.core-pool-size:2}")
    private int batchCorePoolSize;

    @Value("${waqiti.async.batch.max-pool-size:8}")
    private int batchMaxPoolSize;

    @Value("${waqiti.async.batch.queue-capacity:50}")
    private int batchQueueCapacity;

    @Value("${waqiti.async.compliance.core-pool-size:3}")
    private int complianceCorePoolSize;

    @Value("${waqiti.async.compliance.max-pool-size:10}")
    private int complianceMaxPoolSize;

    @Value("${waqiti.async.compliance.queue-capacity:75}")
    private int complianceQueueCapacity;

    /**
     * CRITICAL: Dedicated thread pool for financial operations (payments, transfers)
     * High priority, limited concurrency to prevent resource exhaustion
     */
    @Bean(name = "financialTaskExecutor")
    public AsyncTaskExecutor financialTaskExecutor() {
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(financialCorePoolSize);
        executor.setMaxPoolSize(financialMaxPoolSize);
        executor.setQueueCapacity(financialQueueCapacity);
        executor.setThreadNamePrefix("FinancialAsync-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // Set thread priority to high for financial operations
        executor.setThreadPriority(Thread.MAX_PRIORITY - 1);
        
        executor.initialize();
        
        log.info("ASYNC_CONFIG: Initialized financial task executor - Core: {}, Max: {}, Queue: {}", 
                financialCorePoolSize, financialMaxPoolSize, financialQueueCapacity);
        
        return executor;
    }

    /**
     * CRITICAL: Dedicated thread pool for batch operations
     * Lower priority, higher concurrency for bulk processing
     */
    @Bean(name = "batchTaskExecutor")
    public AsyncTaskExecutor batchTaskExecutor() {
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(batchCorePoolSize);
        executor.setMaxPoolSize(batchMaxPoolSize);
        executor.setQueueCapacity(batchQueueCapacity);
        executor.setThreadNamePrefix("BatchAsync-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(300); // 5 minutes for batch operations
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // Normal priority for batch operations
        executor.setThreadPriority(Thread.NORM_PRIORITY);
        
        executor.initialize();
        
        log.info("ASYNC_CONFIG: Initialized batch task executor - Core: {}, Max: {}, Queue: {}", 
                batchCorePoolSize, batchMaxPoolSize, batchQueueCapacity);
        
        return executor;
    }

    /**
     * CRITICAL: Dedicated thread pool for compliance checks
     * Medium priority, moderate concurrency for regulatory operations
     */
    @Bean(name = "complianceTaskExecutor")
    public AsyncTaskExecutor complianceTaskExecutor() {
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(complianceCorePoolSize);
        executor.setMaxPoolSize(complianceMaxPoolSize);
        executor.setQueueCapacity(complianceQueueCapacity);
        executor.setThreadNamePrefix("ComplianceAsync-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120); // 2 minutes for compliance
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // High priority for compliance operations
        executor.setThreadPriority(Thread.MAX_PRIORITY - 2);
        
        executor.initialize();
        
        log.info("ASYNC_CONFIG: Initialized compliance task executor - Core: {}, Max: {}, Queue: {}", 
                complianceCorePoolSize, complianceMaxPoolSize, complianceQueueCapacity);
        
        return executor;
    }

    /**
     * CRITICAL: General async task executor for non-financial operations
     * Standard priority and concurrency
     */
    @Bean(name = "generalTaskExecutor")
    public AsyncTaskExecutor generalTaskExecutor() {
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(15);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("GeneralAsync-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // Normal priority for general operations
        executor.setThreadPriority(Thread.NORM_PRIORITY);
        
        executor.initialize();
        
        log.info("ASYNC_CONFIG: Initialized general task executor - Core: 5, Max: 15, Queue: 200");
        
        return executor;
    }

    /**
     * CRITICAL: High-priority async executor for critical system operations
     * Used for urgent financial operations and system health checks
     */
    @Bean(name = "criticalTaskExecutor")
    public AsyncTaskExecutor criticalTaskExecutor() {
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("CriticalAsync-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(90);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy()); // Fail fast for critical operations
        
        // Maximum priority for critical operations
        executor.setThreadPriority(Thread.MAX_PRIORITY);
        
        executor.initialize();
        
        log.info("ASYNC_CONFIG: Initialized critical task executor - Core: 3, Max: 6, Queue: 25");
        
        return executor;
    }
}