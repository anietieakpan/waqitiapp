package com.waqiti.common.async;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Non-Blocking Helper Utilities
 *
 * Provides utility methods to convert blocking operations to non-blocking reactive streams.
 * This is critical for maintaining high throughput and preventing thread pool exhaustion.
 *
 * ANTI-PATTERNS TO AVOID:
 * ❌ future.get() - Blocks thread
 * ❌ Mono.fromCallable(() -> blockingCall()) - Still blocks
 * ❌ CompletableFuture.join() - Blocks thread
 *
 * CORRECT PATTERNS:
 * ✅ Mono.fromFuture(asyncCall()) - Non-blocking
 * ✅ Mono.fromCallable(blockingCall).subscribeOn(Schedulers.boundedElastic()) - Offloads to elastic pool
 * ✅ Use reactive clients (WebClient, R2DBC, etc.)
 *
 * Performance Impact:
 * - Blocking: ~200 concurrent requests max (thread pool limit)
 * - Non-blocking: ~10,000+ concurrent requests (event loop)
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-09
 */
@UtilityClass
@Slf4j
public class NonBlockingHelper {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Convert CompletableFuture to non-blocking Mono
     *
     * BEFORE (Blocking):
     * <pre>
     * CompletableFuture&lt;Result&gt; future = externalService.callAsync();
     * Result result = future.get(); // ❌ BLOCKS THREAD!
     * </pre>
     *
     * AFTER (Non-blocking):
     * <pre>
     * Mono&lt;Result&gt; mono = NonBlockingHelper.fromFuture(
     *     () -> externalService.callAsync()
     * );
     * </pre>
     *
     * @param futureSupplier Supplier of CompletableFuture
     * @param <T> Result type
     * @return Non-blocking Mono
     */
    public static <T> Mono<T> fromFuture(Supplier<CompletableFuture<T>> futureSupplier) {
        return fromFuture(futureSupplier, DEFAULT_TIMEOUT);
    }

    /**
     * Convert CompletableFuture to non-blocking Mono with timeout
     *
     * @param futureSupplier Supplier of CompletableFuture
     * @param timeout Timeout duration
     * @param <T> Result type
     * @return Non-blocking Mono
     */
    public static <T> Mono<T> fromFuture(
            Supplier<CompletableFuture<T>> futureSupplier,
            Duration timeout) {

        return Mono.defer(() -> {
            try {
                CompletableFuture<T> future = futureSupplier.get();
                return Mono.fromFuture(future);
            } catch (Exception e) {
                return Mono.error(e);
            }
        })
        .timeout(timeout)
        .onErrorResume(TimeoutException.class, e -> {
            log.error("Operation timed out after {}", timeout);
            return Mono.error(new AsyncOperationTimeoutException(
                "Operation timed out after " + timeout, e));
        })
        .doOnError(e -> log.error("Async operation failed", e));
    }

    /**
     * Offload blocking operation to bounded elastic scheduler
     *
     * Use this for I/O-bound blocking operations (database, file system, etc.)
     *
     * BEFORE (Blocks event loop):
     * <pre>
     * Mono&lt;Result&gt; mono = Mono.fromCallable(() -> {
     *     return blockingDatabaseCall(); // ❌ BLOCKS EVENT LOOP
     * });
     * </pre>
     *
     * AFTER (Offloaded to elastic pool):
     * <pre>
     * Mono&lt;Result&gt; mono = NonBlockingHelper.offloadBlocking(
     *     () -> blockingDatabaseCall()
     * );
     * </pre>
     *
     * @param blockingOperation Blocking operation
     * @param <T> Result type
     * @return Non-blocking Mono
     */
    public static <T> Mono<T> offloadBlocking(Supplier<T> blockingOperation) {
        return offloadBlocking(blockingOperation, DEFAULT_TIMEOUT);
    }

    /**
     * Offload blocking operation with timeout
     *
     * @param blockingOperation Blocking operation
     * @param timeout Timeout duration
     * @param <T> Result type
     * @return Non-blocking Mono
     */
    public static <T> Mono<T> offloadBlocking(
            Supplier<T> blockingOperation,
            Duration timeout) {

        return Mono.fromCallable(blockingOperation::get)
            .subscribeOn(Schedulers.boundedElastic()) // Offload to elastic pool
            .timeout(timeout)
            .onErrorResume(TimeoutException.class, e -> {
                log.error("Blocking operation timed out after {}", timeout);
                return Mono.error(new AsyncOperationTimeoutException(
                    "Blocking operation timed out after " + timeout, e));
            })
            .doOnError(e -> log.error("Blocking operation failed", e));
    }

    /**
     * Execute multiple futures in parallel and collect results
     *
     * BEFORE (Sequential):
     * <pre>
     * Result1 r1 = future1.get(); // Waits
     * Result2 r2 = future2.get(); // Waits
     * Result3 r3 = future3.get(); // Waits
     * Total: 300ms
     * </pre>
     *
     * AFTER (Parallel):
     * <pre>
     * Mono&lt;List&lt;Result&gt;&gt; results = NonBlockingHelper.parallelFutures(
     *     List.of(future1, future2, future3)
     * );
     * Total: 100ms (slowest operation)
     * </pre>
     *
     * @param futures List of CompletableFutures
     * @param <T> Result type
     * @return Mono containing list of results
     */
    public static <T> Mono<List<T>> parallelFutures(List<CompletableFuture<T>> futures) {
        return Flux.fromIterable(futures)
            .flatMap(future -> Mono.fromFuture(future)
                .onErrorResume(e -> {
                    log.error("Future failed in parallel execution", e);
                    return Mono.empty(); // Skip failed futures
                })
            )
            .collectList()
            .timeout(DEFAULT_TIMEOUT)
            .onErrorResume(TimeoutException.class, e -> {
                log.error("Parallel futures timed out");
                return Mono.error(new AsyncOperationTimeoutException(
                    "Parallel operations timed out", e));
            });
    }

    /**
     * Execute multiple blocking operations in parallel
     *
     * @param operations List of blocking operations
     * @param <T> Result type
     * @return Mono containing list of results
     */
    public static <T> Mono<List<T>> parallelBlocking(List<Supplier<T>> operations) {
        return Flux.fromIterable(operations)
            .flatMap(op -> offloadBlocking(op)
                .onErrorResume(e -> {
                    log.error("Operation failed in parallel execution", e);
                    return Mono.empty(); // Skip failed operations
                })
            )
            .collectList();
    }

    /**
     * Convert blocking call with fallback
     *
     * @param operation Blocking operation
     * @param fallback Fallback value supplier
     * @param <T> Result type
     * @return Non-blocking Mono
     */
    public static <T> Mono<T> withFallback(
            Supplier<T> operation,
            Supplier<T> fallback) {

        return offloadBlocking(operation)
            .onErrorResume(e -> {
                log.warn("Operation failed, using fallback: {}", e.getMessage());
                return offloadBlocking(fallback);
            });
    }

    /**
     * Retry blocking operation with exponential backoff
     *
     * @param operation Blocking operation
     * @param maxAttempts Maximum retry attempts
     * @param <T> Result type
     * @return Non-blocking Mono
     */
    public static <T> Mono<T> withRetry(
            Supplier<T> operation,
            int maxAttempts) {

        return offloadBlocking(operation)
            .retryWhen(reactor.util.retry.Retry.backoff(maxAttempts, Duration.ofMillis(100))
                .maxBackoff(Duration.ofSeconds(5))
                .doBeforeRetry(signal ->
                    log.warn("Retrying operation, attempt: {}", signal.totalRetries() + 1)
                )
            );
    }

    /**
     * Check if current thread is in reactive context
     *
     * @return true if in reactive non-blocking context
     */
    public static boolean isReactiveContext() {
        Thread currentThread = Thread.currentThread();
        String threadName = currentThread.getName();

        // Check if we're on a non-blocking thread
        return threadName.contains("reactor-") ||
               threadName.contains("nio-") ||
               threadName.contains("netty-");
    }

    /**
     * Warn if blocking operation is called in reactive context
     *
     * Use this in development to detect blocking operations
     */
    public static void warnIfBlocking() {
        if (isReactiveContext()) {
            log.warn("⚠️ BLOCKING OPERATION IN REACTIVE CONTEXT - Thread: {}",
                Thread.currentThread().getName(),
                new Exception("Stack trace"));
        }
    }

    /**
     * Custom exception for async operation timeouts
     */
    public static class AsyncOperationTimeoutException extends RuntimeException {
        public AsyncOperationTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
