package com.waqiti.common.tracing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.*;

/**
 * Custom async executor that propagates trace context to async operations
 * Ensures distributed tracing works correctly with @Async methods
 */
@Slf4j
public class TracingAsyncExecutor extends ThreadPoolTaskExecutor {

    private final DistributedTracingService distributedTracingService;
    private final OpenTelemetryTracingService openTelemetryService;
    
    public TracingAsyncExecutor(int corePoolSize, int maxPoolSize, int queueCapacity,
                                DistributedTracingService distributedTracingService,
                                OpenTelemetryTracingService openTelemetryService) {
        this.distributedTracingService = distributedTracingService;
        this.openTelemetryService = openTelemetryService;
        
        setCorePoolSize(corePoolSize);
        setMaxPoolSize(maxPoolSize);
        setQueueCapacity(queueCapacity);
        setThreadNamePrefix("tracing-async-");
        setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        setWaitForTasksToCompleteOnShutdown(true);
        setAwaitTerminationSeconds(60);
        
        initialize();
        
        log.info("Initialized TracingAsyncExecutor with core pool size: {}, max pool size: {}, queue capacity: {}",
            corePoolSize, maxPoolSize, queueCapacity);
    }
    
    @Override
    public void execute(Runnable task) {
        super.execute(wrapWithTraceContext(task));
    }
    
    @Override
    public Future<?> submit(Runnable task) {
        return super.submit(wrapWithTraceContext(task));
    }
    
    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return super.submit(wrapWithTraceContext(task));
    }
    
    @Override
    public CompletableFuture<Void> submitCompletable(Runnable task) {
        return CompletableFuture.runAsync(wrapWithTraceContext(task), this);
    }
    
    public <T> CompletableFuture<T> submitCompletable(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return wrapWithTraceContext(task).call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, this);
    }
    
    /**
     * Wrap Runnable with trace context propagation
     */
    private Runnable wrapWithTraceContext(Runnable task) {
        // Capture current context
        CorrelationContext.CorrelationContextData contextData = CorrelationContext.copyContext();
        String traceId = distributedTracingService != null ? distributedTracingService.getCurrentTraceId() : null;
        String spanId = distributedTracingService != null ? distributedTracingService.getCurrentSpanId() : null;
        
        return () -> {
            // Restore context in async thread
            CorrelationContext.restoreContext(contextData);
            
            String taskName = task.getClass().getSimpleName();
            DistributedTracingService.TraceContext traceContext = null;
            
            try {
                // Start child trace for async operation
                if (distributedTracingService != null && contextData != null) {
                    traceContext = distributedTracingService.startChildTrace("async_" + taskName);
                    distributedTracingService.addTag("async.task", taskName);
                    distributedTracingService.addTag("async.thread", Thread.currentThread().getName());
                    distributedTracingService.addTag("async.parent_trace", traceId);
                    distributedTracingService.addTag("async.parent_span", spanId);
                }
                
                log.debug("Executing async task: {} with trace context", taskName);
                
                // Execute the actual task
                task.run();
                
                log.debug("Completed async task: {}", taskName);
                
            } catch (Exception e) {
                log.error("Error in async task: {}", taskName, e);
                
                if (distributedTracingService != null) {
                    distributedTracingService.recordError(e);
                }
                
                throw e;
                
            } finally {
                // Finish trace and clear context
                if (traceContext != null) {
                    distributedTracingService.finishTrace(traceContext);
                }
                
                CorrelationContext.clear();
            }
        };
    }
    
    /**
     * Wrap Callable with trace context propagation
     */
    private <T> Callable<T> wrapWithTraceContext(Callable<T> task) {
        // Capture current context
        CorrelationContext.CorrelationContextData contextData = CorrelationContext.copyContext();
        String traceId = distributedTracingService != null ? distributedTracingService.getCurrentTraceId() : null;
        String spanId = distributedTracingService != null ? distributedTracingService.getCurrentSpanId() : null;
        
        return () -> {
            // Restore context in async thread
            CorrelationContext.restoreContext(contextData);
            
            String taskName = task.getClass().getSimpleName();
            DistributedTracingService.TraceContext traceContext = null;
            
            try {
                // Start child trace for async operation
                if (distributedTracingService != null && contextData != null) {
                    traceContext = distributedTracingService.startChildTrace("async_callable_" + taskName);
                    distributedTracingService.addTag("async.task", taskName);
                    distributedTracingService.addTag("async.thread", Thread.currentThread().getName());
                    distributedTracingService.addTag("async.parent_trace", traceId);
                    distributedTracingService.addTag("async.parent_span", spanId);
                }
                
                log.debug("Executing async callable task: {} with trace context", taskName);
                
                // Execute the actual task
                T result = task.call();
                
                log.debug("Completed async callable task: {}", taskName);
                
                return result;
                
            } catch (Exception e) {
                log.error("Error in async callable task: {}", taskName, e);
                
                if (distributedTracingService != null) {
                    distributedTracingService.recordError(e);
                }
                
                throw e;
                
            } finally {
                // Finish trace and clear context
                if (traceContext != null) {
                    distributedTracingService.finishTrace(traceContext);
                }
                
                CorrelationContext.clear();
            }
        };
    }
    
    /**
     * Get executor statistics
     */
    public ExecutorStatistics getStatistics() {
        ThreadPoolExecutor executor = getThreadPoolExecutor();
        
        return ExecutorStatistics.builder()
            .activeCount(executor.getActiveCount())
            .completedTaskCount(executor.getCompletedTaskCount())
            .taskCount(executor.getTaskCount())
            .queueSize(executor.getQueue().size())
            .remainingCapacity(executor.getQueue().remainingCapacity())
            .corePoolSize(executor.getCorePoolSize())
            .maximumPoolSize(executor.getMaximumPoolSize())
            .largestPoolSize(executor.getLargestPoolSize())
            .build();
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ExecutorStatistics {
        private int activeCount;
        private long completedTaskCount;
        private long taskCount;
        private int queueSize;
        private int remainingCapacity;
        private int corePoolSize;
        private int maximumPoolSize;
        private int largestPoolSize;
    }
}