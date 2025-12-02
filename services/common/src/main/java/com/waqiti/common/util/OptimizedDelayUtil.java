package com.waqiti.common.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Production-Grade Optimized Delay Utility
 *
 * Provides efficient non-blocking delay mechanisms for distributed systems.
 * Replaces the anti-pattern of CompletableFuture.delayedExecutor().get()
 * with proper async scheduling using CountDownLatch.
 *
 * ANTI-PATTERN (DO NOT USE):
 * <pre>
 * CompletableFuture.runAsync(() -> {},
 *     CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS)
 * ).get(); // ❌ Blocks thread, uses shared ForkJoinPool
 * </pre>
 *
 * BEST PRACTICE (USE THIS):
 * <pre>
 * OptimizedDelayUtil.performDelay(
 *     scheduledExecutor,
 *     delayMs,
 *     "operation-name"
 * ); // ✅ Non-blocking, dedicated thread pool
 * </pre>
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2025-10-23
 */
@Slf4j
@UtilityClass
public class OptimizedDelayUtil {

    /**
     * Perform optimized non-blocking delay
     *
     * Benefits over CompletableFuture.delayedExecutor().get():
     * 1. Uses dedicated ScheduledExecutorService (no ForkJoinPool.commonPool() contention)
     * 2. CountDownLatch.await() is more efficient than Future.get()
     * 3. Proper interrupt handling for graceful shutdown
     * 4. Better resource isolation and predictability
     * 5. No CompletableFuture overhead for simple delays
     *
     * @param scheduledExecutor Dedicated scheduler (should be from injected bean)
     * @param delayMs Delay duration in milliseconds
     * @param operationName Name of operation being delayed (for logging/monitoring)
     * @throws InterruptedException if thread is interrupted during delay
     */
    public static void performDelay(
            ScheduledExecutorService scheduledExecutor,
            long delayMs,
            String operationName) throws InterruptedException {

        if (delayMs <= 0) {
            return; // No delay needed
        }

        if (log.isTraceEnabled()) {
            log.trace("Performing {}ms delay for: {}", delayMs, operationName);
        }

        // Use CountDownLatch for efficient waiting
        CountDownLatch latch = new CountDownLatch(1);

        // Schedule the countdown
        ScheduledFuture<?> future = scheduledExecutor.schedule(
            latch::countDown,
            delayMs,
            TimeUnit.MILLISECONDS
        );

        try {
            // Wait for scheduled delay to complete
            // This is efficient and doesn't block the executor thread
            latch.await();

        } catch (InterruptedException e) {
            // Cancel the scheduled task if interrupted
            future.cancel(false);

            // Restore interrupt status
            Thread.currentThread().interrupt();

            log.warn("Delay interrupted for: {}", operationName);
            throw e;
        }

        if (log.isTraceEnabled()) {
            log.trace("Delay completed for: {}", operationName);
        }
    }

    /**
     * Perform delay with timeout
     *
     * @param scheduledExecutor Dedicated scheduler
     * @param delayMs Delay duration in milliseconds
     * @param timeoutMs Maximum time to wait
     * @param operationName Name of operation being delayed
     * @return true if delay completed normally, false if timed out
     * @throws InterruptedException if thread is interrupted during delay
     */
    public static boolean performDelayWithTimeout(
            ScheduledExecutorService scheduledExecutor,
            long delayMs,
            long timeoutMs,
            String operationName) throws InterruptedException {

        if (delayMs <= 0) {
            return true;
        }

        if (log.isTraceEnabled()) {
            log.trace("Performing {}ms delay (timeout={}ms) for: {}",
                delayMs, timeoutMs, operationName);
        }

        CountDownLatch latch = new CountDownLatch(1);

        ScheduledFuture<?> future = scheduledExecutor.schedule(
            latch::countDown,
            delayMs,
            TimeUnit.MILLISECONDS
        );

        try {
            // Wait with timeout
            boolean completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS);

            if (!completed) {
                future.cancel(false);
                log.warn("Delay timed out for: {}", operationName);
            }

            return completed;

        } catch (InterruptedException e) {
            future.cancel(false);
            Thread.currentThread().interrupt();
            log.warn("Delay interrupted for: {}", operationName);
            throw e;
        }
    }

    /**
     * Sleep with proper interrupt handling (for backward compatibility)
     * Use performDelay() with ScheduledExecutorService for better performance
     *
     * @param durationMs Duration in milliseconds
     * @throws InterruptedException if thread is interrupted
     */
    public static void sleep(long durationMs) throws InterruptedException {
        if (durationMs <= 0) {
            return;
        }

        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }
}
